package net.camacraft.backportedclouds.mixins;

import net.camacraft.backportedclouds.Config;
import net.camacraft.backportedclouds.dh.DhCloudShapes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Distant Horizons' clouds take the game's cloud texture ({@link DhCloudShapes}; config "Distant Horizons clouds use the
 * cloud texture"): DH's {@code CloudRenderHandler.getCloudsFromTexture} reads a 64 by 64 map from its own jar. By name,
 * so the mixin is skipped without DH; nothing of DH is compiled against here.
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.renderer.CloudRenderHandler", remap = false)
public abstract class DhCloudTextureMixin {
    @Inject(method = "getCloudsFromTexture", at = @At("HEAD"), cancellable = true, require = 0)
    private static void backported$gameCloudTexture(CallbackInfoReturnable<boolean[][]> cir) {
        if (!Config.CLIENT.dhCloudsUseGameTexture.get()) return;
        boolean[][] map = DhCloudShapes.fromGameTexture();
        if (map != null) cir.setReturnValue(map);
    }
}
