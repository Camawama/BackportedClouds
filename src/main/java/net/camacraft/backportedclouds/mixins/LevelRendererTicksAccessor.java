package net.camacraft.backportedclouds.mixins;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The level renderer's own tick count, by which Embeddium's cloud renderer drifts its clouds ({@code (ticks + partial) *
 * 0.03} blocks): the clouds on VS Genesis' planet drift by the same count so they stay in line with the world's.
 */
@Mixin(LevelRenderer.class)
public interface LevelRendererTicksAccessor {
    @Accessor("ticks")
    int backported$ticks();
}
