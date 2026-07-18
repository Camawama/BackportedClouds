package net.camacraft.backportedclouds.mixins;

import net.camacraft.backportedclouds.Config;
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
     * precision.
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
        cir.setReturnValue(cir.getReturnValue() * Config.CLIENT.cloudDistanceMultiplier.get().floatValue());
    }
}
