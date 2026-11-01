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
        public final ForgeConfigSpec.BooleanValue horizonFade;
        public final ForgeConfigSpec.BooleanValue cloudsOnGenesisPlanet;
        public final ForgeConfigSpec.BooleanValue dhCloudsUseGameTexture;
        public final ForgeConfigSpec.BooleanValue ownTextureOverVanilla;
        public final ForgeConfigSpec.BooleanValue planetClouds;
        public final ForgeConfigSpec.IntValue planetCloudTexels;

        // Key names are kept as-is for compatibility with existing config files.
        public ClientConfig(ForgeConfigSpec.Builder builder) {
            this.cloudDistanceMultiplier = builder
                    .comment("Without the fade toward the horizon (below): multiplier applied to the fog end and the projection far plane while clouds are visible, so distant clouds are not hidden.")
                    .defineInRange("Cloud distance multiplier", 4.0, 1.0, 16.0);
            this.extendFrustum = builder
                    .comment("Extend the projection far plane while clouds are visible so they are not clipped at long distances (with the fade: to the cloud render distance, as 1.21.2+ does).")
                    .define("Extend frustum", true);
            this.cloudRenderDistance = builder
                    .comment("How far away clouds are rendered, in chunks.")
                    .defineInRange("Cloud render distance", 128, 2, 128);
            this.horizonFade = builder
                    .comment("Clouds fade toward the horizon as in 1.21.6 and later: each cloud's opacity falls with its distance from you,",
                            "from full overhead to nothing at the cloud render distance, with no tint toward the fog colour. Off: the clouds",
                            "keep their renderer's own fog (vanilla's fogs them out at the render distance, Embeddium's holds them to the",
                            "cloud distance and tints them). Not under an Oculus shader pack, whose own cloud program draws them.")
                    .define("Fade toward the horizon", true);
            this.cloudsOnGenesisPlanet = builder
                    .comment("With VS Genesis: from a third of the world's height up Genesis draws the planet you stand on as a cube below you,",
                            "and the cloud layer, far below and up to the cloud range wide, was drawn past its edges. On: the clouds fade out",
                            "as the clouds on the planet fade in (\"Clouds on the VS Genesis planet\"), around the height where the two look the",
                            "same size (nothing within 32 blocks of you). Off: the clouds are drawn as they are. Nothing without Genesis.")
                    .define("Clouds fade into the VS Genesis planet", true);
            this.dhCloudsUseGameTexture = builder
                    .comment("With Distant Horizons: its own clouds over the LODs are made from the game's cloud texture (this mod's 1.21.11",
                            "texture, or a resource pack's) instead of the map DH ships, at DH's own density (a cloud cell where enough of a 4 by 4",
                            "block of the texture is cloud). DH builds its clouds when a world loads: rejoin to see a change of this option.")
                    .define("Distant Horizons clouds use the cloud texture", true);
            this.planetClouds = builder
                    .comment("With VS Genesis: a layer of clouds on the planet you see below you from high up, in this mod's cloud texture,",
                            "fixed to the planet (so it never changes size against the planet's continents), lit by the planet's own sun and",
                            "taking over from the world's clouds around the height where the two look the same size.")
                    .define("Clouds on the VS Genesis planet", true);
            this.planetCloudTexels = builder
                    .comment("How many cloud texels span a face of the planet below. 0: as many as make a texel on the planet look as big as",
                            "the world's cloud cell where the two hand over (61 at 8 chunks), so the clouds keep their size through it.")
                    .defineInRange("Cloud texels across a VS Genesis planet face", 0, 0, 256);
            this.ownTextureOverVanilla = builder
                    .comment("Draw the clouds with this mod's 1.21.11 texture even where the resource pack order puts vanilla's old texture on",
                            "top (a pack list in options.txt with 'vanilla' and no 'mod_resources' puts every mod's resources under vanilla's).",
                            "A resource pack's cloud texture still wins. Off: the clouds take whatever texture is on top.")
                    .define("Use this mod's cloud texture over vanilla's", true);
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
