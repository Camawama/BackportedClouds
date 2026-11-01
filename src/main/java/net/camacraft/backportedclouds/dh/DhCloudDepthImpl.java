package net.camacraft.backportedclouds.dh;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import java.nio.FloatBuffer;

/**
 * The Distant Horizons half of {@link DhCloudDepth}: only loaded when DH is. Before the clouds, one full-screen pass
 * reads DH's LOD depth texture, takes each LOD pixel back into view space with DH's projection, projects it with the
 * clouds' projection and writes that depth where it is nearer than what the depth buffer holds (colour untouched).
 * The depth buffer is saved first and put back after the clouds.
 */
final class DhCloudDepthImpl {
    private static final Logger LOGGER = LogUtils.getLogger();

    static volatile String lastPass = "none";

    /** DH's projection this frame, inverted; set by DH's before-render event on the render thread. */
    private static final Matrix4f DH_INVERSE_PROJECTION = new Matrix4f();
    private static boolean dhRendered;

    private static boolean failed;
    private static int program, vao;
    private static int uDepth, uInverse, uProjection, uScreen, uEmpty, uZeroToOne;
    private static TextureTarget backup;
    private static boolean restore;

    private DhCloudDepthImpl() {}

    static void init() {
        DhApiEventRegister.on(DhApiBeforeRenderEvent.class, new DhApiBeforeRenderEvent() {
            @Override
            public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
                if (event.value == null) return;
                if (toJoml(event.value.dhProjectionMatrix, DH_INVERSE_PROJECTION) && DH_INVERSE_PROJECTION.determinant() != 0) {
                    DH_INVERSE_PROJECTION.invert();
                    dhRendered = true;
                }
            }
        });
    }

    /**
     * DH's matrix into JOML. DhApiMat4f names its fields row first (m01 is row 0, column 1); a perspective
     * projection's -1 (or +1) sits at row 3, column 2, which tells the two layouts apart if that ever changes.
     */
    private static boolean toJoml(DhApiMat4f m, Matrix4f out) {
        if (m == null) return false;
        boolean rowFirst = Math.abs(Math.abs(m.m32) - 1.0f) < 1e-3f || Math.abs(m.m23) < 1e-3f;
        if (rowFirst) {
            out.set(m.m00, m.m10, m.m20, m.m30, m.m01, m.m11, m.m21, m.m31,
                    m.m02, m.m12, m.m22, m.m32, m.m03, m.m13, m.m23, m.m33);
        } else {
            out.set(m.m00, m.m01, m.m02, m.m03, m.m10, m.m11, m.m12, m.m13,
                    m.m20, m.m21, m.m22, m.m23, m.m30, m.m31, m.m32, m.m33);
        }
        return true;
    }

    /** The framebuffer bound as the clouds are about to be drawn, against the main target's (the harness reads it). */
    static volatile String lastBinding = "none";
    private static boolean reboundSaid;

    /**
     * Before the clouds: the game's own target bound, where vanilla expects it. With Distant Horizons the window's
     * framebuffer (0) was bound here instead (left by DH's passes earlier in the frame, with Fabulous graphics'
     * targets still alive after DH switched that setting off), so the clouds were drawn into the window and
     * overwritten when the game's target was blitted to it: no clouds at all with DH. Vanilla binds the clouds
     * target itself under Fabulous graphics; otherwise it assumes the main target is still bound.
     */
    static void ensureTarget() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        RenderTarget clouds = mc.levelRenderer.getCloudsTarget();
        RenderTarget expected = Minecraft.useShaderTransparency() && clouds != null ? clouds : main;
        int bound = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (bound == expected.frameBufferId) return;
        expected.bindWrite(true);
        if (!reboundSaid) {
            reboundSaid = true;
            LOGGER.info("Backported Clouds: framebuffer {} was bound before the clouds with Distant Horizons (the game's is {}); the clouds are drawn into the game's from now on",
                    bound, expected.frameBufferId);
        }
    }

    /** Just before the clouds, with or without the depth pass: which framebuffer the clouds will be drawn into. */
    static void noteBinding() {
        try {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            RenderTarget clouds = Minecraft.getInstance().levelRenderer.getCloudsTarget();
            int bound = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            lastBinding = "fbo=" + bound + " main=" + main.frameBufferId + " clouds=" + (clouds == null ? "none" : clouds.frameBufferId)
                    + (bound == main.frameBufferId ? " (main)" : clouds != null && bound == clouds.frameBufferId ? " (clouds target)" : " (NEITHER)");
        } catch (RuntimeException e) {
            lastBinding = "unreadable: " + e;
        }
    }

    static void beforeClouds(Matrix4f cloudProjection) {
        restore = false;
        if (failed) {
            lastPass = "failed";
            return;
        }
        if (!dhRendered) {
            lastPass = "no-lods";
            return;
        }
        int depthTexture;
        try {
            DhApiResult<Integer> texture = DhApi.Delayed.renderProxy == null ? null : DhApi.Delayed.renderProxy.getDhDepthTextureId();
            depthTexture = texture != null && texture.success && texture.payload != null ? texture.payload : 0;
        } catch (RuntimeException | LinkageError e) {
            depthTexture = 0;
        }
        if (depthTexture <= 0) {
            lastPass = "no-texture";
            return;
        }
        try {
            // errors left by earlier drawing aren't this pass's
            for (int i = 0; i < 16 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) ;
            if (program == 0) compile();
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            // The main target is saved and put back; Fabulous graphics draw the clouds into a target of their own
            // (cleared each frame), which keeps the LOD depth harmlessly.
            restore = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == main.frameBufferId;
            if (restore) {
                if (backup == null || backup.width != main.width || backup.height != main.height) {
                    if (backup != null) backup.destroyBuffers();
                    backup = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
                }
                if (main.isStencilEnabled() && !backup.isStencilEnabled()) backup.enableStencil();
                backup.copyDepthFrom(main);
                main.bindWrite(true);
            }
            draw(depthTexture, cloudProjection, main.width, main.height);
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException("GL error " + error + " in the LOD depth pass");
            }
            lastPass = "written";
        } catch (RuntimeException e) {
            failed = true;
            lastPass = "failed";
            LOGGER.error("Backported Clouds: hiding clouds behind Distant Horizons LODs failed; off for this session", e);
            afterClouds();
        }
    }

    static void afterClouds() {
        dhRendered = false;
        if (!restore) return;
        restore = false;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        main.copyDepthFrom(backup);
        main.bindWrite(true);
    }

    private static void draw(int depthTexture, Matrix4f cloudProjection, int width, int height) {
        GlStateManager._glUseProgram(program);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(depthTexture);
        GL20.glUniform1i(uDepth, 0);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            GL20.glUniformMatrix4fv(uInverse, false, DH_INVERSE_PROJECTION.get(buffer));
            GL20.glUniformMatrix4fv(uProjection, false, cloudProjection.get(buffer));
        }
        GL20.glUniform2f(uScreen, width, height);
        String direction = enumName("getDepthDirection");
        String range = enumName("getDepthRange");
        GL20.glUniform1f(uEmpty, "REVERSE_Z".equals(direction) ? 0.0f : 1.0f);
        GL20.glUniform1i(uZeroToOne, "ZERO_TO_POS_ONE".equals(range) ? 1 : 0);

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LESS);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.disableBlend();
        GlStateManager._glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GlStateManager._glBindVertexArray(0);

        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.bindTexture(0);
        // ShaderInstance remembers the last program it bound: clear that, or the cloud shader would skip its bind
        ShaderInstance any = GameRenderer.getPositionShader();
        if (any != null) any.clear();
        else GlStateManager._glUseProgram(0);
    }

    /** DH 3.3+'s depth direction or range (absent before: forward Z, -1..1), by name. */
    private static String enumName(String method) {
        try {
            Object value = DhApi.Delayed.renderProxy.getClass().getMethod(method).invoke(DhApi.Delayed.renderProxy);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return "";
        }
    }

    private static final String VERTEX = """
            #version 150
            // one triangle over the whole screen
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 150
            uniform sampler2D DhDepth;            // DH's LOD depth, drawn with DH's projection
            uniform mat4 DhInverseProjection;
            uniform mat4 CloudProjection;         // the projection the clouds are drawn with
            uniform vec2 ScreenSize;
            uniform float DhEmptyDepth;           // what DH's depth holds where it drew nothing (1, or 0 with reverse Z)
            uniform int DhZeroToOne;              // DH's depth range is 0..1 rather than -1..1
            out vec4 fragColor;
            void main() {
                vec2 uv = gl_FragCoord.xy / ScreenSize;
                float d = texture(DhDepth, uv).r;
                if (d == DhEmptyDepth) discard;
                vec4 view = DhInverseProjection * vec4(uv * 2.0 - 1.0, DhZeroToOne != 0 ? d : d * 2.0 - 1.0, 1.0);
                vec4 clip = CloudProjection * vec4(view.xyz / view.w, 1.0);
                float depth = clip.z / clip.w * 0.5 + 0.5;
                if (depth <= 0.0) discard;
                // an LOD past the far plane still hides the clouds that are past it
                gl_FragDepth = min(depth, 0.99999994);
                fragColor = vec4(0.0);
            }
            """;

    private static void compile() {
        int vertex = shader(GL20.GL_VERTEX_SHADER, VERTEX);
        int fragment = shader(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            program = 0;
            throw new IllegalStateException("LOD depth program link failed: " + log);
        }
        uDepth = GL20.glGetUniformLocation(program, "DhDepth");
        uInverse = GL20.glGetUniformLocation(program, "DhInverseProjection");
        uProjection = GL20.glGetUniformLocation(program, "CloudProjection");
        uScreen = GL20.glGetUniformLocation(program, "ScreenSize");
        uEmpty = GL20.glGetUniformLocation(program, "DhEmptyDepth");
        uZeroToOne = GL20.glGetUniformLocation(program, "DhZeroToOne");
        vao = GL30.glGenVertexArrays();
    }

    private static int shader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("LOD depth shader compile failed: " + log);
        }
        return shader;
    }
}
