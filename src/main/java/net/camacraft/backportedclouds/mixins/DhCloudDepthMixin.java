package net.camacraft.backportedclouds.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.camacraft.backportedclouds.dh.DhCloudDepth;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wraps both of {@code renderLevel}'s cloud calls (plain and Fabulous) so the clouds are hidden behind Distant
 * Horizons' LOD terrain where it is nearer ({@link DhCloudDepth}). Outside {@code renderClouds}, so it holds for
 * whatever draws the clouds in there: this mod, Embeddium, Camafy's storm-cloud shaders.
 */
@Mixin(LevelRenderer.class)
public class DhCloudDepthMixin {
    private static final String RENDER_CLOUDS = "Lnet/minecraft/client/renderer/LevelRenderer;renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FDDD)V";

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = RENDER_CLOUDS), require = 0)
    private void backported$lodDepthBeforeClouds(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline,
                                                 Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix,
                                                 CallbackInfo ci) {
        DhCloudDepth.beforeClouds(projectionMatrix);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = RENDER_CLOUDS, shift = At.Shift.AFTER), require = 0)
    private void backported$lodDepthAfterClouds(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline,
                                                Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix,
                                                CallbackInfo ci) {
        DhCloudDepth.afterClouds();
    }
}
