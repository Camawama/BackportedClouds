package net.camacraft.backportedclouds;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import org.slf4j.Logger;

/**
 * Reads the Aether's client config reflectively so this mod can respect its
 * "disable_clouds" option without compiling against the Aether.
 */
public final class AetherCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static ForgeConfigSpec.ConfigValue<?> disableClouds;
    private static boolean lookupFailed;

    private AetherCompat() {
    }

    public static boolean cloudsDisabled() {
        if (disableClouds == null && !lookupFailed) {
            try {
                Class<?> configClass = Class.forName("com.aetherteam.aether.AetherConfig");
                Object client = configClass.getField("CLIENT").get(null);
                disableClouds = (ForgeConfigSpec.ConfigValue<?>) client.getClass().getField("disable_clouds").get(client);
            } catch (ReflectiveOperationException | ClassCastException e) {
                lookupFailed = true;
                LOGGER.warn("Could not read the Aether's disable_clouds config option; assuming Aether clouds are enabled", e);
            }
        }
        return disableClouds != null && Boolean.TRUE.equals(disableClouds.get());
    }
}
