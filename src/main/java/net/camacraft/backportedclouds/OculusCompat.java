package net.camacraft.backportedclouds;

import com.mojang.logging.LogUtils;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Reads Oculus (the Forge port of Iris) through its public API reflectively, so this mod
 * neither compiles against nor requires it.
 *
 * <p>While a shader pack is active Oculus changes two things this mod relies on: it draws
 * clouds with its own copy of Embeddium's cloud renderer, which never fires Embeddium's
 * cloud distance event, and it sets the projection far plane itself (render distance +
 * 1024 blocks, matching OptiFine) without calling {@code GameRenderer#getDepthFar}.
 */
public final class OculusCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Oculus's far plane while a shader pack is loaded is the render distance plus this many blocks.
    private static final int SHADER_FAR_PLANE_EXTRA_BLOCKS = 1024;
    // Headroom for the camera sitting far below or above the cloud layer.
    private static final int VERTICAL_MARGIN_BLOCKS = 320;
    private static final int BLOCKS_PER_CLOUD_CELL = 12;

    @Nullable
    private static MethodHandle shaderPackInUse;
    private static boolean lookupDone;

    private OculusCompat() {
    }

    /**
     * True while Oculus renders the world through a shader pack. False when Oculus is not
     * installed, no pack is selected, or the API cannot be read.
     */
    public static boolean shaderPackInUse() {
        if (!lookupDone) {
            lookupDone = true;
            if (ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris")) {
                try {
                    Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    Object instance = api.getMethod("getInstance").invoke(null);
                    shaderPackInUse = MethodHandles.publicLookup()
                            .findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class))
                            .bindTo(instance);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    LOGGER.warn("Oculus is installed but its API could not be read; assuming no shader pack is in use", e);
                }
            }
        }
        if (shaderPackInUse == null) {
            return false;
        }
        try {
            return (boolean) shaderPackInUse.invokeExact();
        } catch (Throwable t) {
            shaderPackInUse = null;
            LOGGER.warn("Could not ask Oculus whether a shader pack is in use; assuming none from now on", t);
            return false;
        }
    }

    /**
     * The distance, in chunks, that Oculus's shader-pack cloud renderer should use in place
     * of the render distance it reads. Matches what Embeddium's cloud event gives Embeddium's
     * own renderer, except that it is capped so the whole cloud mesh stays inside Oculus's far
     * plane: clouds past it would be cut off along a hard line. The cap never goes below the
     * distance Oculus would use on its own.
     */
    public static int shaderCloudRenderDistance(int renderDistanceChunks) {
        if (!Config.CLIENT.extendShaderPackClouds.get()) {
            return renderDistanceChunks;
        }
        double farPlane = renderDistanceChunks * 16.0D + SHADER_FAR_PLANE_EXTRA_BLOCKS;
        // A mesh corner sits at (r, h, r) from the camera, so keep sqrt(2r^2 + h^2) within the far plane.
        double maxRadiusBlocks = Math.sqrt((farPlane * farPlane - (double) (VERTICAL_MARGIN_BLOCKS * VERTICAL_MARGIN_BLOCKS)) / 2.0D);
        // Oculus meshes (2 * distance + 9) + 4 cells around the camera's cell, which adds up to one more cell.
        int maxCells = Mth.floor(maxRadiusBlocks / BLOCKS_PER_CLOUD_CELL) - 5;
        int cap = Math.max(renderDistanceChunks, (maxCells - 9) / 2);
        return Math.min(Config.CLIENT.cloudRenderDistance.get(), cap);
    }
}
