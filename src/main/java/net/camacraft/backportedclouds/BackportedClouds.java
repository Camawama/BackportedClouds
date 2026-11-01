package net.camacraft.backportedclouds;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BackportedClouds.MODID)
public class BackportedClouds {
    public static final String MODID = "backportedclouds";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Boolean sodiumCloudsActive;

    public BackportedClouds(FMLJavaModLoadingContext context) {
        context.getModEventBus().addListener(this::onClientSetup);
        context.getModEventBus().addListener(CloudFade::registerShaders);
        context.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        // which cloud texture is in use is worked out again after each resource reload (CloudTexture)
        context.getModEventBus().addListener((net.minecraftforge.client.event.RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> CloudTexture.invalidate()));
        // the planet below's record lasts one frame
        MinecraftForge.EVENT_BUS.addListener((TickEvent.RenderTickEvent e) -> {
            if (e.phase == TickEvent.Phase.START) PlanetBelow.newFrame();
        });
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        net.camacraft.backportedclouds.dh.DhCloudDepth.init();
        if (ModList.get().isLoaded("embeddium")) {
            try {
                EmbeddiumCompat.register();
            } catch (NoClassDefFoundError e) {
                LOGGER.warn("Embeddium is installed but its cloud API is unavailable; extended cloud distance will not apply", e);
            }
        } else if (sodiumCloudsActive()) {
            LOGGER.warn("A Sodium-based renderer without the Embeddium cloud API is installed; Backported Clouds cannot extend its cloud distance and will stand down");
        }
    }

    /**
     * True when a Sodium-derived renderer is installed, meaning it replaces vanilla
     * cloud rendering and this mod must not touch the vanilla cloud path.
     */
    public static boolean sodiumCloudsActive() {
        if (sodiumCloudsActive == null) {
            ModList mods = ModList.get();
            sodiumCloudsActive = mods.isLoaded("embeddium") || mods.isLoaded("rubidium") || mods.isLoaded("oculus");
        }
        return sodiumCloudsActive;
    }

    /**
     * True when whichever renderer owns the vanilla cloud path applies the configured
     * distance: this mod's own renderer, or Embeddium via its cloud API. False for
     * Sodium ports without that API, where the distance cannot be extended.
     */
    public static boolean cloudDistanceExtended() {
        return !sodiumCloudsActive() || ModList.get().isLoaded("embeddium");
    }
}
