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
        }
    }
}
