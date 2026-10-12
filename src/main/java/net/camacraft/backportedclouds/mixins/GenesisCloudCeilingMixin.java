package net.camacraft.backportedclouds.mixins;

import net.camacraft.backportedclouds.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * VS Genesis hides the clouds of its planet dimensions (the overworld included) once the camera is above y 500:
 * its dimension effects' {@code renderClouds} then answers "drawn" and draws nothing, so flying up past 500 the
 * clouds below vanished at once (with vanilla's clouds Genesis fades them out from y 320 to 400 first, a fade
 * Embeddium's and this mod's renderers set their own colour over). With "Keep clouds above the VS Genesis
 * ceiling" that test sees the camera at y 500 at most, so the clouds stay in view from above; Genesis' other test,
 * no clouds where a planet's air is thin, is untouched. Without Genesis this does nothing.
 */
@Pseudo
@Mixin(targets = "shipwrights.genesis.client.PlanetDimensionEffects", remap = false)
public abstract class GenesisCloudCeilingMixin {
    /** Genesis' own ceiling: clouds are hidden above it. */
    private static final double backported$GENESIS_CEILING = 500.0D;

    @ModifyVariable(method = "renderClouds", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private double backported$keepCloudsInView(double cameraY) {
        if (!Config.CLIENT.keepCloudsAboveGenesisCeiling.get()) {
            return cameraY;
        }
        return Math.min(cameraY, backported$GENESIS_CEILING - 1.0D);
    }
}
