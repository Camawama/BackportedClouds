package net.camacraft.backportedclouds;

import net.minecraft.network.chat.Component;

public class EmbeddiumCompat {
    public static void register() {
        try {
            org.embeddedt.embeddium.api.render.clouds.ModifyCloudRenderingEvent.BUS.addListener(e -> e.setCloudRenderDistance(Config.COMMON.cloudRenderDistance.get()));

            org.embeddedt.embeddium.api.OptionGroupConstructionEvent.BUS.addListener(e -> {
                if (e.getId().matches(org.embeddedt.embeddium.client.gui.options.StandardOptions.Group.DETAILS)) {
                    e.getOptions().add(me.jellysquid.mods.sodium.client.gui.options.OptionImpl.createBuilder(int.class, me.jellysquid.mods.sodium.client.gui.SodiumGameOptionPages.getVanillaOpts())
                            .setName(Component.literal("Cloud Distance"))
                            .setTooltip(Component.literal("How far away clouds are rendered (in chunks)."))
                            .setControl(option -> new me.jellysquid.mods.sodium.client.gui.options.control.SliderControl(option, 2, 128, 1, me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter.translateVariable("options.chunks")))
                            .setBinding((opts, value) -> Config.COMMON.cloudRenderDistance.set(value), opts -> Config.COMMON.cloudRenderDistance.get())
                            .build());
                }
            });
        } catch (NoClassDefFoundError e) {
            // Ignored, embeddium is not present
        }
    }
}
