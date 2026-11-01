package net.camacraft.backportedclouds.mixins;

import net.camacraft.backportedclouds.CloudFade;
import net.camacraft.backportedclouds.PlanetBelow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The horizon fade ({@link CloudFade}) on Embeddium's cloud renderer: for its {@code render} (not Oculus's
 * {@code renderIris}, which a shader pack's program draws) the renderer's {@code clouds} shader is swapped for this
 * mod's copy with the fade, put back after, and the cloud pass's fog is set to the fade's right after Embeddium has set
 * its own. A mod that swaps the shader at the draw (Camafy's storm clouds wrap the draws) sees this mod's and reads
 * {@link CloudFade#style()}. Absent without Embeddium ({@link Pseudo}); every injection is optional.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.immediate.CloudRenderer", remap = false)
public abstract class EmbeddiumCloudFadeMixin {
    @Shadow
    private ShaderInstance shader;

    @Unique
    private ShaderInstance backported$ownShader;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void backported$fadeShader(CallbackInfo ci) {
        ShaderInstance fade = CloudFade.sodiumShader();
        if (fade != null && this.shader != null) {
            this.backported$ownShader = this.shader;
            this.shader = fade;
            PlanetBelow.apply(fade);
        }
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void backported$ownShaderBack(CallbackInfo ci) {
        if (this.backported$ownShader != null) {
            this.shader = this.backported$ownShader;
            this.backported$ownShader = null;
        }
    }

    /** Right after Embeddium set its clouds' fog (its first setShaderFogStart; the second puts the frame's back). */
    @Inject(method = "render", require = 0, at = @At(value = "INVOKE", ordinal = 0, shift = At.Shift.AFTER,
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFogStart(F)V"))
    private void backported$fadeFog(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        CloudFade.applyFog(mc.level, mc.getCameraEntity());
    }
}
