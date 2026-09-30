package net.camacraft.backportedclouds.mixins;

import net.camacraft.backportedclouds.AetherCompat;
import net.camacraft.backportedclouds.BackportedClouds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Aether's dimension effects render their own clouds with a verbatim copy of the
 * vanilla renderer, so they are capped at the vanilla distance and bypass this mod.
 * Aether cloud color and drift only differ from vanilla during rain, which the Aether
 * dimension does not have, so handing the clouds back to the vanilla path (extended by
 * {@link LevelRendererMixin}, or by Embeddium via its cloud API) is visually identical
 * while gaining the configured render distance.
 */
@Pseudo
@Mixin(targets = "com.aetherteam.aether.client.renderer.level.AetherSkyRenderEffects")
public abstract class AetherSkyRenderEffectsMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, remap = false)
    private void backported$deferToExtendedClouds(CallbackInfoReturnable<Boolean> cir) {
        if (!BackportedClouds.cloudDistanceExtended()) {
            return; // No renderer with extended distance is active; leave the Aether's clouds alone.
        }
        // true = "handled, render nothing" (respects the Aether's disable_clouds option);
        // false = fall through to the vanilla cloud path, which renders at the extended distance.
        cir.setReturnValue(AetherCompat.cloudsDisabled());
    }
}
