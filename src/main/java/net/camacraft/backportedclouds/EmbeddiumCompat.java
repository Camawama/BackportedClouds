package net.camacraft.backportedclouds;

import net.minecraft.network.chat.Component;

public class EmbeddiumCompat {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static int lastDistance = -1;

    public static void register() {
        try {
            // with the fade: the distance whose square of cells covers the cloud range (Embeddium meshes 2d + 9 cells of 12
            // blocks round the camera; the configured value as is gave 2.5 times the range, faded out long before its edge)
            org.embeddedt.embeddium.api.render.clouds.ModifyCloudRenderingEvent.BUS.addListener(e -> {
                int distance = CloudFade.active() ? CloudFade.embeddiumDistance() : Config.CLIENT.cloudRenderDistance.get();
                if (distance != lastDistance) {
                    lastDistance = distance;
                    LOGGER.info("Backported Clouds: Embeddium's cloud distance set to {} (was {}; {} cells of 12 blocks round the camera)",
                            distance, e.getCloudRenderDistance(), Math.max(32, distance * 2 + 9));
                }
                e.setCloudRenderDistance(distance);
            });

            org.embeddedt.embeddium.api.OptionGroupConstructionEvent.BUS.addListener(e -> {
                if (e.getId().matches(org.embeddedt.embeddium.client.gui.options.StandardOptions.Group.DETAILS)) {
                    e.getOptions().add(me.jellysquid.mods.sodium.client.gui.options.OptionImpl.createBuilder(int.class, me.jellysquid.mods.sodium.client.gui.SodiumGameOptionPages.getVanillaOpts())
                            .setName(Component.literal("Cloud Distance"))
                            .setTooltip(Component.literal("How far away clouds are rendered (in chunks). With a shader pack active, the distance is capped to the pack's view range."))
                            .setControl(option -> new me.jellysquid.mods.sodium.client.gui.options.control.SliderControl(option, 2, 128, 1, me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter.translateVariable("options.chunks")))
                            .setBinding((opts, value) -> Config.CLIENT.cloudRenderDistance.set(value), opts -> Config.CLIENT.cloudRenderDistance.get())
                            .build());
                }
            });
        } catch (NoClassDefFoundError e) {
            // Embeddium build without this API; the caller logs the situation.
        }
    }
}
