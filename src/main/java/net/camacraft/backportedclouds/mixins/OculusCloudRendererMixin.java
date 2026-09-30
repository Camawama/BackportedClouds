package net.camacraft.backportedclouds.mixins;

import net.camacraft.backportedclouds.OculusCompat;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * While a shader pack is active, Oculus cancels Embeddium's cloud renderer and draws the
 * clouds with its own copy, {@code renderIris}, merged into Embeddium's CloudRenderer by
 * Oculus's MixinCloudRenderer, so the pack's gbuffers_clouds program shades them. That copy
 * sizes the clouds from the vanilla render distance and never fires Embeddium's cloud
 * distance event, so without this the cloud distance setting does nothing under shaders.
 * Both targets are optional: without Embeddium the class is absent ({@link Pseudo}), and
 * without Oculus the method is absent ({@code require = 0}).
 *
 * <p>The priority must stay above Oculus's (the default, 1000). Mixin only lets an injector
 * into a method another mixin merged when the injecting mixin's priority is strictly higher,
 * and otherwise throws while the class loads, whether or not a shader pack is on: the game
 * crashes the first time clouds render.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.immediate.CloudRenderer", priority = 1500)
public abstract class OculusCloudRendererMixin {
    @Redirect(method = "renderIris", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getEffectiveRenderDistance()I", remap = true), remap = false, require = 0)
    private int backported$shaderCloudDistance(Options options) {
        return OculusCompat.shaderCloudRenderDistance(options.getEffectiveRenderDistance());
    }
}
