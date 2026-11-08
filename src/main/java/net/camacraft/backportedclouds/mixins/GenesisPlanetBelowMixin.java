package net.camacraft.backportedclouds.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.camacraft.backportedclouds.PlanetBelow;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Reads VS Genesis' planet below the camera off its planet renderer for {@link PlanetBelow}: which body each
 * {@code invoke} is for (the one the camera stands on is the planet below) and, when it is drawn, where and how large.
 * Nothing of Genesis is compiled against ({@link Pseudo}, by name, its types coerced); every injection is optional.
 */
@Pseudo
@Mixin(targets = "shipwrights.genesis.space.renderer.PlanetRenderer", remap = false)
public abstract class GenesisPlanetBelowMixin {
    @Inject(method = "invoke", at = @At("HEAD"), require = 0)
    private void backported$bodyBegins(RenderLevelStageEvent event, @Coerce Object celestial, @Coerce Object vantage, CallbackInfo ci) {
        PlanetBelow.invokeStarts(event, celestial, vantage);
    }

    @Inject(method = "invoke", at = @At("RETURN"), require = 0)
    private void backported$bodyEnds(CallbackInfo ci) {
        PlanetBelow.invokeEnds();
    }

    /** After Genesis has drawn a body and before its eclipse shadows, under its glow, so a moon's shadow falls on the clouds too: the clouds on the planet below, or those of a planet seen from afar ({@link net.camacraft.backportedclouds.PlanetCloudShell}). */
    @Inject(method = "renderPlanetAt", at = @At(value = "INVOKE", remap = false,
            target = "Lshipwrights/genesis/space/renderer/PlanetRenderer;renderShadows(Ljava/util/List;Lcom/mojang/blaze3d/vertex/PoseStack;DDDDLorg/joml/Quaterniondc;)V"), require = 0)
    private void backported$cloudsOnThePlanet(ResourceLocation id, List<?> shadows, PoseStack poseStack, double x, double y, double z,
                                              double halfExtent, @Coerce Object rotation, float alpha, CallbackInfo ci) {
        if (PlanetBelow.drawingBelow()) net.camacraft.backportedclouds.PlanetCloudShell.afterPlanet(id, poseStack, x, y, z, halfExtent, rotation, alpha);
        else net.camacraft.backportedclouds.PlanetCloudShell.afterBody(id, poseStack, x, y, z, halfExtent, rotation, alpha);
    }

    @Inject(method = "renderPlanetAt", at = @At("HEAD"), require = 0)
    private void backported$bodyDrawn(ResourceLocation id, List<?> shadows, PoseStack poseStack, double x, double y, double z,
                                      double halfExtent, @Coerce Object rotation, float alpha, CallbackInfo ci) {
        PlanetBelow.drawn(x, y, z, halfExtent, alpha);
    }
}
