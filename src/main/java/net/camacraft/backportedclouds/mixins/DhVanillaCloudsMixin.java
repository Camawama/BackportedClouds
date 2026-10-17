package net.camacraft.backportedclouds.mixins;

import com.mojang.logging.LogUtils;
import net.camacraft.backportedclouds.Config;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the game's Clouds video setting as the player set it. Distant Horizons (3.3.x) switches it OFF every time
 * its renderer starts ("Disabling vanilla clouds... This is done to prevent vanilla clouds from rendering on top of
 * Distant Horizons LODs", {@code LodRenderer} with its {@code overrideVanillaGraphicsSettings} option on). This mod
 * hides the clouds behind nearer LODs itself ({@link net.camacraft.backportedclouds.dh.DhCloudDepth}), so the
 * override is not needed; DH's other overrides (chunk fade-in, Fabulous graphics) are left to it. By name, so the
 * mixin is skipped without DH; config "Keep the Clouds setting with Distant Horizons".
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftClientWrapper_forge", remap = false)
public class DhVanillaCloudsMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean backported$said;

    @Inject(method = "disableVanillaClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void backported$keepCloudsSetting(CallbackInfo ci) {
        if (!Config.CLIENT.keepCloudsSettingWithLods.get()) return;
        if (!backported$said) {
            backported$said = true;
            LOGGER.info("Backported Clouds keeps the Clouds video setting: Distant Horizons' vanilla-clouds override is cancelled (the clouds are drawn behind its LODs instead).");
        }
        ci.cancel();
    }
}
