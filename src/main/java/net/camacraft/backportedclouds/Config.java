package net.camacraft.backportedclouds;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Config {
    public static final ClientConfig CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        final Pair<ClientConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT = specPair.getLeft();
        CLIENT_SPEC = specPair.getRight();
    }

    public static class ClientConfig {
        public final ForgeConfigSpec.DoubleValue cloudDistanceMultiplier;
        public final ForgeConfigSpec.BooleanValue extendFrustum;
        public final ForgeConfigSpec.IntValue cloudRenderDistance;
        public final ForgeConfigSpec.BooleanValue extendShaderPackClouds;
        public final ForgeConfigSpec.BooleanValue keepCloudsAboveGenesisCeiling;
        public final ForgeConfigSpec.BooleanValue hideCloudsBehindLods;
        public final ForgeConfigSpec.BooleanValue keepCloudsSettingWithLods;

        // Key names are kept as-is for compatibility with existing config files.
        public ClientConfig(ForgeConfigSpec.Builder builder) {
            this.cloudDistanceMultiplier = builder
                    .comment("Multiplier applied to fog end and the projection far plane while clouds are visible, so distant clouds are not hidden.")
                    .defineInRange("Cloud distance multiplier", 4.0, 1.0, 16.0);
            this.extendFrustum = builder
                    .comment("Extend the projection far plane while clouds are visible so they are not clipped at long distances.")
                    .define("Extend frustum", true);
            this.cloudRenderDistance = builder
                    .comment("How far away clouds are rendered, in chunks.")
                    .defineInRange("Cloud render distance", 128, 2, 128);
            this.extendShaderPackClouds = builder
                    .comment("Apply the cloud render distance while an Oculus shader pack is active. Oculus shortens the view range with shader packs, so the distance is capped to keep clouds inside it, and many packs fade out or replace vanilla clouds themselves. Turn off to use Oculus's default cloud distance with shader packs.")
                    .define("Extend clouds with shader packs", true);
            this.keepCloudsAboveGenesisCeiling = builder
                    .comment("VS Genesis hides the clouds once you are above y 500 in its planet dimensions (the overworld included), so they vanish all at once when you fly up past it. On: the clouds stay in view from above, at any height. Off: Genesis decides. Clouds still go where a planet's air is too thin for them.")
                    .define("Keep clouds above the VS Genesis ceiling", true);
            this.hideCloudsBehindLods = builder
                    .comment("With Distant Horizons: clouds are hidden behind LOD terrain that is nearer than them (DH keeps its LODs' depth to itself, so the clouds were drawn on top of distant mountains). Costs one full-screen pass and two depth copies a frame. Not used with an Oculus shader pack.")
                    .define("Hide clouds behind Distant Horizons LODs", true);
            this.keepCloudsSettingWithLods = builder
                    .comment("With Distant Horizons: keep the game's Clouds video setting as you set it. DH turns it off (\"Disabling vanilla",
                            "clouds... to prevent vanilla clouds from rendering on top of Distant Horizons LODs\") every time its renderer",
                            "starts, with its 'overrideVanillaGraphicsSettings' option on; this mod draws its clouds behind the LODs",
                            "itself (the option above), so that is no longer needed. DH's other overrides (chunk fade-in, Fabulous",
                            "graphics) are left to it.")
                    .define("Keep the Clouds setting with Distant Horizons", true);
        }
    }
}
