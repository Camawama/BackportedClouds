package net.camacraft.backportedclouds;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * The horizon fade of 1.21.6 and later ("Fade toward the horizon"): each cloud's opacity falls in a straight line with
 * its distance from the camera, from full overhead to nothing at the cloud fog distance, with no tint toward the fog
 * colour; the cloud fog distance is the cloud range (vanilla caps it at 2048 blocks, which is 128 chunks). Vanilla's
 * cloud shader does it as {@code color.a *= 1 - linear_fog_value(distance, 0, FogCloudsEnd)}. Until 1.21.5 vanilla
 * blended the far clouds toward the fog colour instead, and 1.20.1 fogged them out at the render distance.
 *
 * <p>Done in this mod's own cloud shaders, one per cloud renderer: {@code backportedclouds:clouds} for the vanilla
 * path (the mesh with texture, colour and normal) and {@code backportedclouds:clouds_sodium} for Embeddium's renderer
 * (position and colour), both reading the fade from the standard fog uniforms: the renderer sets the cloud pass's fog
 * to start 0 and end the cloud fog distance ({@link #applyFog}), or leaves its own shorter fog when the camera is in
 * water, lava or powder snow, the player is blinded or in darkness, or the place is foggy (a foggy biome, the dragon's
 * and wither's world fog), where vanilla shortens the cloud fog the same way.
 *
 * <p>For other mods: {@link #style()} says which fade the cloud pass is drawn with ({@code 1}: this one; {@code 0}:
 * the renderer's own), so a mod that swaps the cloud shader for a copy of its own (Camafy's storm clouds) can fade the
 * same way; the fog uniforms carry the distance.
 *
 * <p>Off under an Oculus shader pack: the pack's own cloud program draws the clouds then.
 *
 * @since 1.0.5
 */
public final class CloudFade {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float CELL_BLOCKS = 12.0F;
    /** Embeddium meshes {@code 2 * distance + 9} cells of 12 blocks round the camera (at least 32), a square. */
    private static final int EMBEDDIUM_EXTRA_CELLS = 9;

    @Nullable
    private static ShaderInstance vanilla, sodium;

    private CloudFade() {}

    /** The fade is on and the clouds are drawn by a renderer it applies to (not a shader pack's). */
    public static boolean active() {
        return Config.CLIENT.horizonFade.get() && !OculusCompat.shaderPackInUse();
    }

    /** The fade the cloud pass is drawn with: 1 this mod's (1.21.6+), 0 the renderer's own. Read by Camafy's storm clouds. */
    public static int style() {
        return active() ? 1 : 0;
    }

    /** The cloud range in blocks: how far clouds are meshed, the far plane's least reach, and the cloud fog distance. */
    public static int rangeBlocks() {
        return Config.CLIENT.cloudRenderDistance.get() * 16;
    }

    /** The cloud fog distance in blocks: a cloud this far off is gone. Vanilla: the cloud range, at most 2048. */
    public static float end() {
        return rangeBlocks();
    }

    /** The distance (in Embeddium's render-distance terms) whose square of cells covers the cloud range. */
    public static int embeddiumDistance() {
        int cells = Mth.ceil(rangeBlocks() / CELL_BLOCKS);
        return Math.max(2, Mth.positiveCeilDiv(cells - EMBEDDIUM_EXTRA_CELLS, 2));
    }

    /**
     * Whether the clouds get the plain cloud fog this frame (start 0, end the cloud fog distance), or the renderer's own
     * shorter one: in water, lava or powder snow, blinded or in darkness, in a foggy biome or the world fog of a boss.
     */
    public static boolean plainAir(@Nullable ClientLevel level, @Nullable Entity viewer) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        if (level == null || camera.getFluidInCamera() != FogType.NONE) return false;
        if (viewer instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) return false;
        Vec3 pos = camera.getPosition();
        if (level.effects().isFoggyAt(Mth.floor(pos.x), Mth.floor(pos.z))) return false;
        return !mc.gui.getBossOverlay().shouldCreateWorldFog();
    }

    /** Sets the cloud pass's fog for the fade in plain air (the renderer has just set its own); nothing otherwise. */
    public static void applyFog(@Nullable ClientLevel level, @Nullable Entity viewer) {
        if (!active() || !plainAir(level, viewer)) return;
        RenderSystem.setShaderFogStart(0.0F);
        RenderSystem.setShaderFogEnd(end());
        if (!fogLogged) {
            fogLogged = true;
            LOGGER.info("Backported Clouds: the fade toward the horizon sets the cloud fog (0 to {} blocks)", end());
        }
    }

    private static boolean fogLogged, vanillaLogged, sodiumLogged;

    /**
     * The shader the vanilla-path clouds are drawn with: this mod's in place of vanilla's
     * {@code position_tex_color_normal}; another mod's copy of it (Camafy's storm clouds) stays, it reads {@link #style()}.
     */
    public static ShaderInstance vanillaShader(ShaderInstance current) {
        if (!active() || vanilla == null) return current;
        if (!"position_tex_color_normal".equals(current.getName())) return current;
        if (!vanillaLogged) {
            vanillaLogged = true;
            LOGGER.info("Backported Clouds: the fade toward the horizon draws the vanilla-path clouds with its own shader");
        }
        return vanilla;
    }

    /** This mod's shader for Embeddium's cloud renderer (position and colour), or null while it isn't loaded. */
    @Nullable
    public static ShaderInstance sodiumShader() {
        if (!active() || sodium == null) return null;
        if (!sodiumLogged) {
            sodiumLogged = true;
            LOGGER.info("Backported Clouds: the fade toward the horizon draws Embeddium's clouds with its own shader");
        }
        return sodium;
    }

    static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(BackportedClouds.MODID, "clouds"),
                    DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL), s -> vanilla = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(BackportedClouds.MODID, "clouds_sodium"),
                    DefaultVertexFormat.POSITION_COLOR), s -> sodium = s);
        } catch (IOException | RuntimeException e) {
            vanilla = sodium = null;
            LOGGER.warn("Backported Clouds: the cloud fade shaders didn't load; clouds keep the renderer's own fog", e);
        }
    }
}
