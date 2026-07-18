package net.camacraft.backportedclouds.mixins;

import net.camacraft.backportedclouds.BackportedClouds;
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
    private int backported$transientCloudDistance;

    public VideoSettingsScreenMixin(Screen parent, Options options, Component title) {
        super(parent, options, title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void backported$onInitHead(CallbackInfo ci) {
        if (!BackportedClouds.sodiumCloudsActive()) {
            this.backported$transientCloudDistance = Config.CLIENT.cloudRenderDistance.get();
        }
    }

    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/OptionsList;addSmall([Lnet/minecraft/client/OptionInstance;)V"))
    private OptionInstance<?>[] backported$modifyOptions(OptionInstance<?>[] originalOptions) {
        if (BackportedClouds.sodiumCloudsActive()) {
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

        OptionInstance<Integer> cloudRenderDistanceOption = new OptionInstance<>(
                "options.backportedclouds.cloudRenderDistance",
                OptionInstance.cachedConstantTooltip(Component.translatable("options.backportedclouds.cloudRenderDistance.tooltip")),
                (caption, value) -> Options.genericValueLabel(caption, Component.translatable("options.chunks", value)),
                new OptionInstance.IntRange(2, 128),
                this.backported$transientCloudDistance,
                (value) -> this.backported$transientCloudDistance = value
        );

        // OptionsList.addSmall lays the array out in rows of two, so the option is inserted
        // at a row boundary with a null right-hand slot. If another mod left the array
        // odd-length or removed the clouds option, fall back to appending at the end.
        if (cloudStatusIndex != -1 && optionsList.size() % 2 == 0) {
            int insertIndex = Math.min(cloudStatusIndex - (cloudStatusIndex % 2) + 2, optionsList.size());
            optionsList.add(insertIndex, cloudRenderDistanceOption);
            optionsList.add(insertIndex + 1, null);
        } else {
            optionsList.add(cloudRenderDistanceOption);
        }

        return optionsList.toArray(new OptionInstance[0]);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void backported$onRemoved(CallbackInfo ci) {
        if (!BackportedClouds.sodiumCloudsActive()) {
            if (Config.CLIENT.cloudRenderDistance.get() != this.backported$transientCloudDistance) {
                Config.CLIENT.cloudRenderDistance.set(this.backported$transientCloudDistance);
            }
        }
    }
}
