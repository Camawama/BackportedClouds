package net.camacraft.backportedclouds;

import net.minecraft.network.chat.Component;

public class EmbeddiumCompat {
    public static void register() {
        try {
            org.embeddedt.embeddium.api.render.clouds.ModifyCloudRenderingEvent.BUS.addListener(e -> e.setCloudRenderDistance(Config.CLIENT.cloudRenderDistance.get()));

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
