package net.camacraft.backportedclouds.mixins;

import net.camacraft.backportedclouds.CloudFade;
import net.camacraft.backportedclouds.Config;
import net.camacraft.backportedclouds.OculusCompat;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    Minecraft minecraft;

    /**
     * Extends the projection far plane so distant clouds are not clipped. Only active
     * while there is something to see: a loaded level whose dimension has clouds, with
     * clouds enabled — so menus, the Nether, and clouds-off setups keep vanilla depth
     * precision. Also inactive while an Oculus shader pack is loaded: Oculus sets the far
     * plane itself without calling this method, so mods that still read it should get the
     * vanilla value, not an extension the real projection does not have. With the fade toward
     * the horizon the far plane reaches the cloud range at least, as 1.21.2+'s does
     * ({@code max(renderDistance * 4, cloudRange * 16)}); without, the multiplier applies.
     */
    @Inject(method = "getDepthFar", at = @At("RETURN"), cancellable = true)
    private void backported$extendDepthFar(CallbackInfoReturnable<Float> cir) {
        ClientLevel level = this.minecraft.level;
        if (level == null) {
            return;
        }
        if (this.minecraft.options.getCloudsType() == CloudStatus.OFF) {
            return;
        }
        if (Float.isNaN(level.effects().getCloudHeight())) {
            return;
        }
        if (!Config.CLIENT.extendFrustum.get()) {
            return;
        }
        if (OculusCompat.shaderPackInUse()) {
            return;
        }
        float far = cir.getReturnValue();
        if (CloudFade.active()) {
            cir.setReturnValue(Math.max(far, CloudFade.rangeBlocks()));
        } else {
            cir.setReturnValue(far * Config.CLIENT.cloudDistanceMultiplier.get().floatValue());
        }
    }
}
