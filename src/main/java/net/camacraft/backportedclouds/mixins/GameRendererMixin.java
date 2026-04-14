package net.camacraft.backportedclouds.mixins;

import net.camacraft.backportedclouds.Config;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getDepthFar", at = @At("RETURN"), cancellable = true)
    private void backported__extend_distance(CallbackInfoReturnable<Float> cir) {
        if (Config.COMMON.extendFrustum.get()) {
            cir.setReturnValue((float) (cir.getReturnValue() * Config.COMMON.cloudDistanceMultiplier.get()));
        }
    }
}
