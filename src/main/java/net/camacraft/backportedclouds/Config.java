package net.camacraft.backportedclouds;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.tuple.Pair;

@Mod.EventBusSubscriber(modid = BackportedClouds.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final CommonConfig COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        {
            final Pair<CommonConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(CommonConfig::new);
            COMMON = specPair.getLeft();
            COMMON_SPEC = specPair.getRight();
        }
    }


    public static class CommonConfig {
        public final ForgeConfigSpec.DoubleValue cloudDistanceMultiplier;
        public final ForgeConfigSpec.BooleanValue extendFrustum;
        public final ForgeConfigSpec.IntValue cloudRenderDistance;

        public CommonConfig(ForgeConfigSpec.Builder builder) {
            this.cloudDistanceMultiplier = builder.defineInRange("Cloud distance multiplier", 4.0, 0.1, Float.MAX_VALUE);
            this.extendFrustum = builder.define("Extend frustum", true);
            this.cloudRenderDistance = builder.defineInRange("Cloud render distance", 128, 2, 128);
        }
    }
}
