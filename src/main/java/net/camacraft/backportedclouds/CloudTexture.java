package net.camacraft.backportedclouds;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Which cloud texture the clouds are drawn and shaped with (1.0.7; "Use this mod's cloud texture over vanilla's"). This mod
 * puts its 1.21.11 texture at vanilla's path ({@code minecraft:textures/environment/clouds.png}), which wins as long as the
 * mods' resources sit above vanilla's in the pack order. In the owner's main pack they don't (2026-10-28: the texture's
 * packs, bottom to top, were Vanilla Backport, this mod, vanilla; its {@code options.txt} lists {@code vanilla} and no
 * {@code mod_resources}), so every cloud renderer read vanilla's old texture. Where vanilla's own file is the one on top,
 * the clouds take this mod's copy at {@code backportedclouds:textures/environment/clouds.png}; any other pack on top (a
 * resource pack, another mod) keeps vanilla's path, so a resource pack's clouds still win. Read by the vanilla cloud path,
 * Embeddium's cloud shapes and Distant Horizons' clouds; worked out again after each resource reload.
 */
public final class CloudTexture {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation VANILLA = new ResourceLocation("textures/environment/clouds.png");
    public static final ResourceLocation OWN = new ResourceLocation(BackportedClouds.MODID, "textures/environment/clouds.png");
    private static volatile ResourceLocation cached;
    private static String lastChoice = "none";

    private CloudTexture() {}

    /** After a resource reload: work the choice out again on the next ask. */
    public static void invalidate() {
        cached = null;
    }

    /** The texture the clouds are drawn and shaped with now. */
    public static ResourceLocation location() {
        ResourceLocation c = cached;
        if (c != null) return c;
        c = choose();
        cached = c;
        return c;
    }

    private static ResourceLocation choose() {
        boolean on;
        try {
            on = Config.CLIENT.ownTextureOverVanilla.get();
        } catch (IllegalStateException e) {
            // asked before the client config is loaded: the default
            on = true;
        }
        if (!on) return remember(VANILLA, "the option is off");
        try {
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            Optional<Resource> top = manager.getResource(VANILLA);
            if (top.isEmpty()) return remember(OWN, "no pack has the cloud texture");
            String pack = top.get().sourcePackId();
            if ("vanilla".equals(pack) && manager.getResource(OWN).isPresent()) {
                return remember(OWN, "vanilla's texture is on top of the packs");
            }
            return remember(VANILLA, pack + "'s texture is on top of the packs");
        } catch (RuntimeException e) {
            return remember(VANILLA, "the packs couldn't be read: " + e);
        }
    }

    private static ResourceLocation remember(ResourceLocation choice, String why) {
        String line = choice + " (" + why + ")";
        if (!line.equals(lastChoice)) {
            lastChoice = line;
            LOGGER.info("Backported Clouds: the clouds use {}", line);
        }
        return choice;
    }

    private static volatile String embeddiumRead = "never";

    /** Embeddium's cloud shapes read {@code location}: the chosen texture in vanilla's place (said in the log the first time and after a change). */
    public static ResourceLocation forEmbeddium(ResourceLocation location) {
        if (!VANILLA.equals(location)) return location;
        ResourceLocation chosen = location();
        String read = chosen.toString();
        if (!read.equals(embeddiumRead)) {
            embeddiumRead = read;
            LOGGER.info("Backported Clouds: Embeddium's cloud shapes are read from {}", read);
        }
        return chosen;
    }

    /** What Embeddium's cloud shapes were last read from (the harness reads it). */
    public static String embeddiumRead() {
        return embeddiumRead;
    }

    /** The last choice and why (the harness reads it). */
    public static String lastChoice() {
        return lastChoice;
    }
}
