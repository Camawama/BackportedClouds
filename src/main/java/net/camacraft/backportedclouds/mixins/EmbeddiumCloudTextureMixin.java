package net.camacraft.backportedclouds.mixins;

import net.camacraft.backportedclouds.CloudTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Embeddium's cloud renderer shapes its clouds from the cloud texture it reads in {@code createCloudEdges}
 * ({@code ResourceManager.getResource(minecraft:textures/environment/clouds.png)}): it reads the one {@link CloudTexture}
 * picks, this mod's own copy where vanilla's old texture is on top of the packs. By name, so the mixin is skipped without
 * Embeddium; optional.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.immediate.CloudRenderer", remap = false)
public abstract class EmbeddiumCloudTextureMixin {
    @ModifyArg(method = "createCloudEdges", require = 0, remap = false,
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/server/packs/resources/ResourceManager;getResource(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;"))
    private static ResourceLocation backported$cloudTexture(ResourceLocation location) {
        return CloudTexture.forEmbeddium(location);
    }

    /** The same by the method's runtime (SRG) name, in case the refmap has no entry for a target in a pseudo mixin. */
    @ModifyArg(method = "createCloudEdges", require = 0, remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/server/packs/resources/ResourceManager;m_213713_(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;"))
    private static ResourceLocation backported$cloudTextureSrg(ResourceLocation location) {
        return CloudTexture.forEmbeddium(location);
    }
}
