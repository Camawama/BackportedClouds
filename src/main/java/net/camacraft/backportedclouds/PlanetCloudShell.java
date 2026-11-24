package net.camacraft.backportedclouds;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.nio.FloatBuffer;

/**
 * A cloud layer on VS Genesis' planet below (1.0.7, "Clouds on the VS Genesis planet"; the owner, 2026-10-28: "It looked really
 * nice when the clouds rendered on the vs earth, but ... the clouds were changing the texture size with the depth the higher
 * I got ... they should keep a consistent texture resolution as I get higher").
 *
 * <p>Genesis' Earth texture has no clouds. What the owner saw on the planet were the world's own clouds, 192 blocks up and
 * drawn through the planet's outline, so as the camera rose they shrank against the planet (the world's cloud layer
 * recedes at the camera's speed, the planet at a sixteenth of it). Here the clouds are part of the planet: a shell a hair
 * above the planet's cube, drawn right after Genesis draws the planet (in its sky pass), each face showing a fixed window of
 * the cloud texture the clouds use ({@link CloudTexture}), so they scale with the planet at any height and never against
 * its continents. A texel is as big on the planet as the world's 12-block cloud cell looks from the height where the two
 * layers hand over ({@link #texelsAcrossAFace}: from the render distance, the world's height, the cloud height and the
 * planet's size, the way Genesis places its planet; 61 across a face at 8 chunks), so the cloud size stays the same through
 * the handover, which is kept to that height ({@link PlanetBelow#handover}): the world's clouds fade out
 * ({@code planet_below_fade}) as these fade in. They drift as the world's clouds do and are lit as Genesis lights the planet
 * (its own {@code LightDirection}: bright on the day side, dark on the night side). Only where the world has clouds and they
 * are switched on, never under a shader pack.
 *
 * <p>A face that looks up or down takes the world's own cloud mapping: the texture's width along east, its height along
 * south, centred on the world cloud over the point it is lined up with ({@link #anchorX}), so at the handover's height the
 * planet's clouds are the world's clouds, the same pattern at the same size in the same place, and nothing changes as one
 * layer gives way to the other. While the world's clouds hand over the point follows the camera; once they have, it stays,
 * so flying sideways high up the clouds keep to the planet; coming back down it slides back into line at
 * {@link #ALIGN_SPEED}. The other faces show a fixed window of the texture each.
 *
 * <p>Clouds are drawn only on planets of the overworld's kind ({@link PlanetBelow#bodyIsOverworldKind}). A planet seen from
 * afar gets the whole texture ({@link #afterBody}, {@link PlanetCloudWrap}) rather than this layer's windows.
 */
public final class PlanetCloudShell {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The shell's size against the planet's cube: just above its surface. */
    private static final float SHELL = 1.006F;
    /** Quads along each side of a face: the light's terminator is drawn smooth across a face. */
    private static final int SPLIT = 8;
    /** The world's clouds' opacity. */
    private static final float OPACITY = 0.85F;
    /** The light a cloud keeps on the night side: the planet's own ambient tenth would make white clouds the brightest thing there. */
    private static final float NIGHT_LIGHT = 0.035F;
    /** Genesis' planet shader's ambient light, which also decides how much of the sky's colour it lays over the ground. */
    private static final float GENESIS_AMBIENT = 0.1F;
    /** The wrap's shell against the cube (a planet seen from afar), and the shadows' just over the ground under it. */
    private static final float WRAP_SHELL = 1.012F, SHADOW_SHELL = 1.002F;
    /** How dark a cloud's shadow is at its darkest, and how far it lies from the cloud, in half sizes of a face, with the sun low. */
    private static final float SHADOW_DARK = 0.42F, SHADOW_SHIFT = 0.05F;
    /** A planet seen under this tangent of its half size is left out: a few pixels at most. */
    private static final double TOO_SMALL = 0.004;
    /** The faces: normal, then the two axes along the face. */
    static final float[][][] FACES = {
            {{0, 1, 0}, {1, 0, 0}, {0, 0, 1}},
            {{0, -1, 0}, {1, 0, 0}, {0, 0, -1}},
            {{1, 0, 0}, {0, 0, 1}, {0, 1, 0}},
            {{-1, 0, 0}, {0, 0, -1}, {0, 1, 0}},
            {{0, 0, 1}, {0, 1, 0}, {1, 0, 0}},
            {{0, 0, -1}, {0, -1, 0}, {1, 0, 0}},
    };
    /** Each face's window into the 256-texel cloud texture, so the faces don't repeat one another. */
    private static final int[][] WINDOWS = {{0, 0}, {64, 0}, {128, 0}, {192, 0}, {0, 128}, {128, 128}};
    /** Faces turned more upright than this (their normal's height in the drawn axes) take the world's own cloud mapping. */
    private static final float LEVEL_FACE = 0.7F;
    /** How fast the pattern on the planet slides back into line with the world's clouds, in blocks a second. */
    private static final double ALIGN_SPEED = 36.0;
    /** The world point the planet's cloud pattern is lined up with (the camera's, while the world's clouds hand over). */
    private static double anchorX = Double.NaN, anchorZ = Double.NaN;
    private static Object anchorLevel;
    private static long lastNanos;
    private static java.lang.invoke.MethodHandle shaderSupplier;
    private static boolean shaderLooked;
    private static int draws, wrapDraws;
    private static String lastDraw = "none", lastWrap = "none";

    private PlanetCloudShell() {}

    /** Right after Genesis has drawn the planet below: its cloud layer, in the same place and turn, at the same fade. */
    public static void afterPlanet(ResourceLocation id, PoseStack pose, double x, double y, double z, double halfExtent, Object rotation, float planetAlpha) {
        try {
            if (planetAlpha <= 0.004F || halfExtent <= 0.0 || !(rotation instanceof Quaterniondc turn)) return;
            if (!Config.CLIENT.planetClouds.get() || OculusCompat.shaderPackInUse()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.options.getCloudsType() == CloudStatus.OFF || Float.isNaN(mc.level.effects().getCloudHeight())) return;
            if (!PlanetBelow.bodyIsOverworldKind(id)) return;
            draw(mc, pose, x, y, z, halfExtent, turn, planetAlpha);
        } catch (RuntimeException e) {
            LOGGER.warn("Backported Clouds: the clouds on VS Genesis' planet couldn't be drawn ({}); off for this session", e.toString());
            Config.CLIENT.planetClouds.set(false);
        }
    }

    /**
     * Right after Genesis has drawn a body that isn't the one under the camera, seen from space or from another world's
     * sky: on a planet of the overworld's kind ({@link PlanetBelow#bodyIsOverworldKind}), the whole cloud texture
     * ({@link PlanetCloudWrap}), lit as Genesis lights the planet (with the sky's colour over its night side, seen from a
     * world's sky: {@link #colourAt}), with the clouds' shadows on the ground under it. Drawn before Genesis' glow, which
     * lies over it.
     */
    public static void afterBody(ResourceLocation id, PoseStack pose, double x, double y, double z, double halfExtent, Object rotation, float alpha) {
        try {
            if (alpha <= 0.004F || halfExtent <= 0.0 || !(rotation instanceof Quaterniondc turn)) return;
            if (!Config.CLIENT.planetCloudsFromAfar.get() || OculusCompat.shaderPackInUse()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.options.getCloudsType() == CloudStatus.OFF) return;
            if (!PlanetBelow.bodyIsOverworldKind(id)) return;
            if (halfExtent / Math.max(1.0, Math.sqrt(x * x + y * y + z * z)) < TOO_SMALL) return;
            drawWrap(mc, id, pose, x, y, z, halfExtent, turn, alpha);
        } catch (RuntimeException e) {
            LOGGER.warn("Backported Clouds: the clouds on VS Genesis' planets couldn't be drawn ({}); off for this session", e.toString());
            Config.CLIENT.planetCloudsFromAfar.set(false);
        }
    }

    private static void drawWrap(Minecraft mc, ResourceLocation id, PoseStack pose, double x, double y, double z, double halfExtent, Quaterniondc turn, float bodyAlpha) {
        ResourceLocation texture = PlanetCloudWrap.texture(mc, id, mc.level.getGameTime() + (double) mc.getFrameTime());
        if (texture == null) return;
        // how thick each cloud is, is in the cells' alpha
        float alpha = Mth.clamp(bodyAlpha, 0F, 1F);
        float[] light = lightDirection();
        float[] sky = skyColour();
        Quaternionf q = new Quaternionf(turn);
        Matrix4f m = new Matrix4f(pose.last().pose()).translate((float) x, (float) y, (float) z).rotate(q);
        Vector3d centre = new Vector3d(x, y, z);
        // the way to the sun in the planet's own axes, and the clouds' shadows when there is a sun to cast them
        Vector3f sun = light == null ? null : new Quaternionf(q).conjugate().transform(new Vector3f(-light[0], -light[1], -light[2]));
        boolean shadows = sun != null && Config.CLIENT.planetCloudShadows.get();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Vector3f n = new Vector3f(), a = new Vector3f(), b = new Vector3f(), p = new Vector3f(), dir = new Vector3f();
        float[] rgb = new float[3];
        int faces = 0;
        float h = (float) (halfExtent * WRAP_SHELL), low = (float) (halfExtent * SHADOW_SHELL);
        for (int f = 0; f < FACES.length; f++) {
            n.set(FACES[f][0][0], FACES[f][0][1], FACES[f][0][2]);
            a.set(FACES[f][1][0], FACES[f][1][1], FACES[f][1][2]);
            b.set(FACES[f][2][0], FACES[f][2][1], FACES[f][2][2]);
            Vector3f nw = q.transform(new Vector3f(n));
            Vector3d faceCentre = new Vector3d(nw.x, nw.y, nw.z).mul(halfExtent).add(centre);
            if (faceCentre.x * nw.x + faceCentre.y * nw.y + faceCentre.z * nw.z >= 0.0) continue;
            faces++;
            // a point is shaded by the cloud sunward of it
            float shiftA = sun == null ? 0F : a.dot(sun) * SHADOW_SHIFT, shiftB = sun == null ? 0F : b.dot(sun) * SHADOW_SHIFT;
            for (int pass = shadows ? 0 : 1; pass < 2; pass++) {
                boolean shadow = pass == 0;
                for (int i = 0; i < SPLIT; i++) {
                    for (int j = 0; j < SPLIT; j++) {
                        float s0 = -1F + 2F * i / SPLIT, s1 = -1F + 2F * (i + 1) / SPLIT;
                        float t0 = -1F + 2F * j / SPLIT, t1 = -1F + 2F * (j + 1) / SPLIT;
                        float[][] st = {{s0, t0}, {s1, t0}, {s1, t1}, {s0, t1}};
                        for (float[] c : st) {
                            p.set(n).add(a.x * c[0], a.y * c[0], a.z * c[0]).add(b.x * c[1], b.y * c[1], b.z * c[1]);
                            float size = shadow ? low : h;
                            float u = PlanetCloudWrap.atlasU(f, shadow ? Mth.clamp(c[0] + shiftA, -1F, 1F) : c[0]);
                            float v = PlanetCloudWrap.atlasV(f, shadow ? Mth.clamp(c[1] + shiftB, -1F, 1F) : c[1]);
                            float opacity = alpha;
                            if (shadow) {
                                dir.set(p).normalize();
                                rgb[0] = rgb[1] = rgb[2] = 0F;
                                opacity = alpha * SHADOW_DARK * Mth.clamp(dir.dot(sun) * 4F, 0F, 1F);
                            } else {
                                colourAt(q, light, sky, p, dir, rgb);
                            }
                            buffer.vertex(m, p.x * size, p.y * size, p.z * size).uv(u, v).color(rgb[0], rgb[1], rgb[2], opacity).endVertex();
                        }
                    }
                }
            }
        }
        if (faces > 0) BufferUploader.drawWithShader(buffer.end());
        else buffer.end().release();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        wrapDraws++;
        lastWrap = String.format(java.util.Locale.ROOT, "%s, %d faces, alpha %.2f, shadows %s, sky %s, %s", id, faces, alpha, shadows,
                sky == null ? "none" : String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f", sky[0], sky[1], sky[2]), PlanetCloudWrap.describe());
    }

    private static void draw(Minecraft mc, PoseStack pose, double x, double y, double z, double halfExtent, Quaterniondc turn, float planetAlpha) {
        float h = (float) (halfExtent * SHELL);
        int texels = texelsAcrossAFace(mc, halfExtent);
        float partial = mc.getFrameTime();
        // the world's clouds drift 0.03 blocks a tick, a texel being 12 blocks
        double drift = ((double) mc.level.getGameTime() + partial) * 0.03D / 12.0D;
        float alpha = Mth.clamp(OPACITY * PlanetBelow.handover(planetAlpha), 0F, 1F);
        if (alpha <= 0.004F) return;
        float[] light = lightDirection();
        float[] sky = skyColour();
        float handover = PlanetBelow.handover(planetAlpha);
        // the world point the pattern is lined up with: the camera's while the world's clouds hand over, then left where it is
        net.minecraft.world.phys.Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        long now = System.nanoTime();
        double dt = lastNanos == 0L ? 0.0 : Mth.clamp((now - lastNanos) / 1.0e9, 0.0, 0.25);
        lastNanos = now;
        if (Double.isNaN(anchorX) || anchorLevel != mc.level) {
            anchorX = cam.x;
            anchorZ = cam.z;
            anchorLevel = mc.level;
        } else if (handover < 0.999F) {
            double dx = cam.x - anchorX, dz = cam.z - anchorZ, d = Math.sqrt(dx * dx + dz * dz), step = ALIGN_SPEED * dt;
            if (d <= step || d > 3072.0) {
                anchorX = cam.x;
                anchorZ = cam.z;
            } else {
                anchorX += dx / d * step;
                anchorZ += dz / d * step;
            }
        }
        // the world cloud texel over the anchor (BackportedClouds' own mapping: x drifts 0.03 a tick, z sits 0.33 texel on)
        // the drift the world's clouds are drawn with: Embeddium's by the level renderer's own tick count, this mod's by game time
        double driftTicks = BackportedClouds.sodiumCloudsActive()
                ? ((net.camacraft.backportedclouds.mixins.LevelRendererTicksAccessor) mc.levelRenderer).backported$ticks()
                : (double) mc.level.getGameTime();
        double driftBlocks = (driftTicks + partial) * 0.03D;
        double texX = (anchorX + driftBlocks) / 12.0, texZ = anchorZ / 12.0 + 0.33;
        float baseU = (float) (texX / 256.0 - Math.floor(texX / 256.0)), baseV = (float) (texZ / 256.0 - Math.floor(texZ / 256.0));
        float perTexel = 2F * h / texels;
        Quaternionf q = new Quaternionf(turn);
        Matrix4f m = new Matrix4f(pose.last().pose()).translate((float) x, (float) y, (float) z).rotate(q);
        Vector3d centre = new Vector3d(x, y, z);

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, CloudTexture.location());
        RenderSystem.enableBlend();
        // the colour only: the framebuffer's alpha stays as the sky left it (Fabulous graphics composite by it)
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Vector3f n = new Vector3f(), a = new Vector3f(), b = new Vector3f(), p = new Vector3f(), dir = new Vector3f();
        float[] rgb = new float[3];
        int faces = 0;
        for (int f = 0; f < FACES.length; f++) {
            n.set(FACES[f][0][0], FACES[f][0][1], FACES[f][0][2]);
            a.set(FACES[f][1][0], FACES[f][1][1], FACES[f][1][2]);
            b.set(FACES[f][2][0], FACES[f][2][1], FACES[f][2][2]);
            // only the faces turned towards the camera (it stands at the origin of these axes)
            Vector3f nw = q.transform(new Vector3f(n));
            Vector3d faceCentre = new Vector3d(nw.x, nw.y, nw.z).mul(h).add(centre);
            if (faceCentre.x * nw.x + faceCentre.y * nw.y + faceCentre.z * nw.z >= 0.0) continue;
            faces++;
            boolean level = Math.abs(nw.y) > LEVEL_FACE;
            float u0 = WINDOWS[f][0] / 256F + (float) (drift / 256.0 % 1.0), v0 = WINDOWS[f][1] / 256F;
            float span = texels / 256F;
            for (int i = 0; i < SPLIT; i++) {
                for (int j = 0; j < SPLIT; j++) {
                    float s0 = -1F + 2F * i / SPLIT, s1 = -1F + 2F * (i + 1) / SPLIT;
                    float t0 = -1F + 2F * j / SPLIT, t1 = -1F + 2F * (j + 1) / SPLIT;
                    float[][] st = {{s0, t0}, {s1, t0}, {s1, t1}, {s0, t1}};
                    for (float[] c : st) {
                        float u, v;
                        p.set(n).add(a.x * c[0], a.y * c[0], a.z * c[0]).add(b.x * c[1], b.y * c[1], b.z * c[1]);
                        if (level) {
                            // where the corner is from the camera, in the world's axes: the world's cloud mapping over it
                            q.transform(dir.set(p).mul(h));
                            u = baseU + (float) ((dir.x + x) / perTexel) / 256F;
                            v = baseV + (float) ((dir.z + z) / perTexel) / 256F;
                        } else {
                            u = u0 + (c[0] + 1F) * 0.5F * span;
                            v = v0 + (c[1] + 1F) * 0.5F * span;
                        }
                        corner(buffer, m, q, light, sky, p, dir, rgb, h, u, v, alpha);
                    }
                }
            }
        }
        if (faces > 0) BufferUploader.drawWithShader(buffer.end());
        else buffer.end().release();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        draws++;
        lastDraw = String.format(java.util.Locale.ROOT, "%d faces, alpha %.2f, %d texels a face, lit %s, sky %s", faces, alpha, texels, light != null,
                sky == null ? "none" : String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f", sky[0], sky[1], sky[2]));
    }

    /**
     * Cloud texels across a face of the planet: the option's value, or by default the count at which a texel on the planet looks
     * as big as the world's 12-block cloud cell from the height where the two layers hand over. Genesis draws the planet's
     * centre {@code camY / 16 + renderDistance + 32} blocks below the camera and fades it in from {@code renderDistance +
     * maxBuildHeight / 3} to {@code renderDistance + maxBuildHeight}; the handover's middle is at the planet's fade-in
     * {@link PlanetBelow#HANDOVER_MIDDLE}. Nothing in it depends on where the camera is.
     */
    public static int texelsAcrossAFace(Minecraft mc, double halfExtent) {
        int set = Config.CLIENT.planetCloudTexels.get();
        if (set > 0) return set;
        if (mc.level == null || halfExtent <= 0.0) return 64;
        double rd = mc.gameRenderer.getRenderDistance();
        double top = mc.level.getMaxBuildHeight();
        double lo = rd + top / 3.0, hi = rd + top;
        double y = lo + PlanetBelow.HANDOVER_MIDDLE * (hi - lo);
        double cloudY = mc.level.effects().getCloudHeight();
        double toClouds = Math.max(16.0, y - (Double.isNaN(cloudY) ? 192.0 : cloudY));
        double toShell = Math.max(1.0, y / 16.0 + rd + 32.0 - halfExtent * SHELL);
        double texel = 12.0 * toShell / toClouds;
        return Mth.clamp((int) Math.round(2.0 * halfExtent * SHELL / texel), 8, 256);
    }

    /** One corner {@code p} of a face (on the unit cube): placed on the shell, its texel, and its colour as Genesis colours the planet there. */
    private static void corner(BufferBuilder buffer, Matrix4f m, Quaternionf q, float[] light, float[] sky, Vector3f p, Vector3f dir, float[] rgb,
                               float h, float u, float v, float alpha) {
        colourAt(q, light, sky, p, dir, rgb);
        buffer.vertex(m, p.x * h, p.y * h, p.z * h)
                .uv(u, v)
                .color(rgb[0], rgb[1], rgb[2], alpha)
                .endVertex();
    }

    /**
     * A white cloud's colour at the point {@code p} of the cube, as Genesis' planet shader colours the ground there: lit by
     * dot(n, -light) through the normal of a sphere, eased, with a cloud's own night light for the planet's ambient tenth;
     * and, seen from a world's sky, the sky's colour where that light falls short ({@code SkyColor * (1 - lighting)}, with
     * Genesis' own lighting), so a body's night side in a day sky is the sky's colour under its clouds as well as under
     * its ground (1.0.9). Without it the clouds there were nearly black over a sky-blue planet. In space and on the planet
     * below Genesis sends no sky colour, and nothing changes.
     */
    private static void colourAt(Quaternionf q, float[] light, float[] sky, Vector3f p, Vector3f dir, float[] rgb) {
        if (light == null) {
            rgb[0] = rgb[1] = rgb[2] = 1F;
            return;
        }
        q.transform(dir.set(p).normalize());
        float lit = Math.max(0F, -(dir.x * light[0] + dir.y * light[1] + dir.z * light[2]));
        float eased = 1F - 1F / (lit * lit * 5F + 1F);
        float shade = Mth.clamp(NIGHT_LIGHT + eased, 0F, 1F);
        float skyShare = sky == null ? 0F : 1F - Mth.clamp(GENESIS_AMBIENT + eased, 0F, 1F);
        for (int c = 0; c < 3; c++) rgb[c] = Mth.clamp(shade + (sky == null ? 0F : sky[c] * skyShare), 0F, 1F);
    }

    /** Genesis' planet shader's light direction this frame (from the star to the body, in the axes the body is drawn in), or null. */
    private static float[] lightDirection() {
        float[] l = uniform("LightDirection");
        if (l == null) return null;
        float len = Mth.sqrt(l[0] * l[0] + l[1] * l[1] + l[2] * l[2]);
        return len < 1e-6F ? null : new float[]{l[0] / len, l[1] / len, l[2] / len};
    }

    /**
     * The sky colour Genesis' planet shader lays over the body it has just drawn where the sun's light falls short: the
     * world's sky colour for a body seen from a world's sky; null in space and for the planet below, where it sends none.
     */
    private static float[] skyColour() {
        float[] c = uniform("SkyColor");
        if (c == null || c[0] + c[1] + c[2] <= 1e-4F) return null;
        return new float[]{Mth.clamp(c[0], 0F, 1F), Mth.clamp(c[1], 0F, 1F), Mth.clamp(c[2], 0F, 1F)};
    }

    /** A three-float uniform of Genesis' planet shader as it was set for the body just drawn, or null. */
    private static float[] uniform(String name) {
        if (!shaderLooked) {
            shaderLooked = true;
            try {
                Object holder = Class.forName("shipwrights.genesis.client.ShaderRegistry").getField("PLANET_TEXTURED_SHADER").get(null);
                Object supplier = holder.getClass().getMethod("getInstance").invoke(holder);
                shaderSupplier = java.lang.invoke.MethodHandles.constant(Object.class, supplier);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOGGER.warn("Backported Clouds: VS Genesis' planet shader couldn't be read ({}); the clouds on its planet are lit evenly", e.toString());
            }
        }
        if (shaderSupplier == null) return null;
        try {
            Object shader = ((java.util.function.Supplier<?>) shaderSupplier.invoke()).get();
            if (!(shader instanceof ShaderInstance instance)) return null;
            com.mojang.blaze3d.shaders.Uniform u = instance.getUniform(name);
            if (u == null) return null;
            FloatBuffer fb = u.getFloatBuffer();
            return new float[]{fb.get(0), fb.get(1), fb.get(2)};
        } catch (Throwable t) {
            return null;
        }
    }

    /** How many times the layer was drawn since the game started (the harness reads it). */
    public static int draws() {
        return draws;
    }

    /** What the last draw was (the harness reads it). */
    public static String lastDraw() {
        return lastDraw;
    }

    /** How many times a planet's whole wrap was drawn since the game started (the harness reads it). */
    public static int wrapDraws() {
        return wrapDraws;
    }

    /** What the last wrap was (the harness reads it). */
    public static String lastWrap() {
        return lastWrap;
    }
}
