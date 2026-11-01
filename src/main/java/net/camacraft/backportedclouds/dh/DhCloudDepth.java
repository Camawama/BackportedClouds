package net.camacraft.backportedclouds.dh;

import net.camacraft.backportedclouds.Config;
import net.camacraft.backportedclouds.OculusCompat;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4f;

/**
 * Clouds behind Distant Horizons' LOD terrain. DH draws its LODs into a framebuffer of its own and only copies their
 * colour onto Minecraft's, so Minecraft's depth buffer is empty wherever the LODs are and the clouds, drawn later, came
 * out on top of LOD mountains that are nearer than them. Around the cloud pass this writes the LODs' depth (turned into
 * the clouds' projection) into the depth buffer, and puts Minecraft's own depth back afterwards, so the clouds are
 * hidden behind LODs that are nearer and stay in front of those that are farther, whichever renderer draws them
 * (this mod's, Embeddium's, Camafy's storm-cloud shaders).
 *
 * <p>Distant Horizons is optional: this class has no DH types; {@link DhCloudDepthImpl} has, and is only loaded when
 * DH is. Off with an Oculus shader pack (the pack and DH's Iris path draw clouds and LODs their own way) and with the
 * config option "Hide clouds behind Distant Horizons LODs".
 *
 * <p>With DH loaded it also makes sure the game's own target is bound before the clouds (1.0.5,
 * {@link DhCloudDepthImpl#ensureTarget}): DH's passes left the window's framebuffer bound there, and the clouds drawn
 * into it were overwritten by the game's target, so with DH there were no clouds at all.
 */
public final class DhCloudDepth {
    private DhCloudDepth() {}

    private static Boolean loaded;

    private static boolean loaded() {
        if (loaded == null) loaded = ModList.get().isLoaded("distanthorizons");
        return loaded;
    }

    /** Client setup: listens for DH's render parameters. */
    public static void init() {
        if (loaded()) DhCloudDepthImpl.init();
    }

    private static boolean active() {
        return loaded() && Config.CLIENT.hideCloudsBehindLods.get() && !OculusCompat.shaderPackInUse();
    }

    /** {@code renderLevel}, just before it draws the clouds with {@code projection}. */
    public static void beforeClouds(Matrix4f projection) {
        if (!loaded()) return;
        DhCloudDepthImpl.noteBinding();
        DhCloudDepthImpl.ensureTarget();
        if (active()) DhCloudDepthImpl.beforeClouds(projection);
    }

    /** For the pack harness: the framebuffer bound as the last cloud pass began, against the main target's. */
    public static String lastBinding() {
        return loaded() ? DhCloudDepthImpl.lastBinding : "no-dh";
    }

    /** {@code renderLevel}, just after the clouds. */
    public static void afterClouds() {
        if (loaded()) DhCloudDepthImpl.afterClouds();
    }

    /** For the pack harness: the last cloud pass's state ("off", "no-lods", "written", ...). */
    public static String lastPass() {
        return loaded() ? DhCloudDepthImpl.lastPass : "no-dh";
    }
}
