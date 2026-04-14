package net.camacraft.backportedclouds.mixins;

import net.camacraft.backportedclouds.Config;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {

    @Unique
    private int backported__transientCloudDistance;

    public VideoSettingsScreenMixin(Screen parent, Options options, Component title) {
        super(parent, options, title);
    }

    @Unique
    private boolean backported__isCompatMode() {
        return net.minecraftforge.fml.ModList.get().isLoaded("embeddium") || net.minecraftforge.fml.ModList.get().isLoaded("rubidium") || net.minecraftforge.fml.ModList.get().isLoaded("oculus");
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void backported__onInitHead(CallbackInfo ci) {
        if (!backported__isCompatMode()) {
            this.backported__transientCloudDistance = Config.COMMON.cloudRenderDistance.get();
        }
    }

    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/OptionsList;addSmall([Lnet/minecraft/client/OptionInstance;)V"))
    private OptionInstance<?>[] backported__modifyOptions(OptionInstance<?>[] originalOptions) {
        if (backported__isCompatMode()) {
            return originalOptions;
        }

        List<OptionInstance<?>> optionsList = new ArrayList<>(Arrays.asList(originalOptions));
        int cloudStatusIndex = -1;

        for (int i = 0; i < optionsList.size(); i++) {
            if (optionsList.get(i) == this.options.cloudStatus()) {
                cloudStatusIndex = i;
                break;
            }
        }

        if (cloudStatusIndex != -1) {
            OptionInstance<Integer> cloudRenderDistanceOption = new OptionInstance<>(
                    "options.backportedclouds.cloudRenderDistance",
                    OptionInstance.cachedConstantTooltip(Component.translatable("options.backportedclouds.cloudRenderDistance.tooltip")),
                    (caption, value) -> Options.genericValueLabel(caption, Component.translatable("options.chunks", value)),
                    new OptionInstance.IntRange(2, 128),
                    this.backported__transientCloudDistance,
                    (value) -> this.backported__transientCloudDistance = value
            );

            // Calculate the index for the start of the next row
            int insertIndex = cloudStatusIndex - (cloudStatusIndex % 2) + 2;

            // Add the new option on the left and a null on the right for an empty slot
            optionsList.add(insertIndex, cloudRenderDistanceOption);
            optionsList.add(insertIndex + 1, null);
        }

        return optionsList.toArray(new OptionInstance[0]);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void backported__onRemoved(CallbackInfo ci) {
        if (!backported__isCompatMode()) {
            if (Config.COMMON.cloudRenderDistance.get() != this.backported__transientCloudDistance) {
                Config.COMMON.cloudRenderDistance.set(this.backported__transientCloudDistance);
            }
        }
    }
}
