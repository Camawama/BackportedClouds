package net.camacraft.backportedclouds;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(BackportedClouds.MODID)
public class BackportedClouds {
    public static final String MODID = "backportedclouds";

    public BackportedClouds(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::onClientSetup);
        MinecraftForge.EVENT_BUS.register(this);
        context.registerConfig(ModConfig.Type.CLIENT, Config.COMMON_SPEC);
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("embeddium")) {
            try {
                EmbeddiumCompat.register();
            } catch (NoClassDefFoundError e) {
                // Ignore if not found
            }
        }
    }
}
