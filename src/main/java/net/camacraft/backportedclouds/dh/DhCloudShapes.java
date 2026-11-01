package net.camacraft.backportedclouds.dh;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.Optional;

/**
 * Distant Horizons' own clouds (its "generic rendering" clouds over the LODs) in the cloud texture the game draws with
 * (1.0.7, "Distant Horizons clouds use the cloud texture"; the owner, 2026-10-28). DH builds its clouds from a 64 by 64
 * map of its own ({@code assets/distanthorizons/textures/clouds.png}, read from its jar with the class loader, so neither
 * this mod's texture nor a resource pack reached it): a white pixel is a 128-block cloud cell, the map repeating every
 * 8192 blocks. Here the map is made from the cloud texture the clouds are drawn with ({@link net.camacraft.backportedclouds.CloudTexture}:
 * this mod's 1.21.11 texture, or a resource pack's), a 64 by 64 cell being a cloud where enough of its 4 by 4 pixels
 * are: DH's own map is a thinned copy of the old texture that keeps two thirds of its cloud (13.6% of DH's cells against
 * 20.4% of the old texture's pixels), so the threshold is the one that keeps the same share of the texture in use (10 of
 * 16 pixels for the 1.21.11 texture: 790 cells). A texture that isn't a square multiple of 64 is left to DH. Read when
 * DH builds a level's clouds.
 */
public final class DhCloudShapes {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation CLOUDS = new ResourceLocation("textures/environment/clouds.png");
    private static final int SIZE = 64;
    /** The share of its texture's cloud DH's own map keeps (559 of 4096 cells against 13,356 of 65,536 pixels). */
    private static final double DH_KEEPS = (559.0 / 4096.0) / (13356.0 / 65536.0);
    private static volatile String lastResult = "none";
    private static boolean stackLogged;

    private DhCloudShapes() {
    }

    /** The cloud map for DH from the game's cloud texture, or null to let DH read its own. */
    @Nullable
    public static boolean[][] fromGameTexture() {
        try {
            if (!stackLogged) {
                // every pack's copy of the texture, bottom to top: which one the clouds are drawn with is the last
                stackLogged = true;
                StringBuilder stack = new StringBuilder();
                for (Resource r : Minecraft.getInstance().getResourceManager().getResourceStack(CLOUDS)) {
                    if (stack.length() > 0) stack.append(", ");
                    stack.append(r.sourcePackId());
                }
                LOGGER.info("Backported Clouds: the cloud texture's packs, bottom to top: [{}] (on {})", stack, Thread.currentThread().getName());
            }
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(net.camacraft.backportedclouds.CloudTexture.location());
            if (resource.isEmpty()) {
                lastResult = "no cloud texture";
                return null;
            }
            try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                int w = image.getWidth(), h = image.getHeight();
                if (w != h || w < SIZE || w % SIZE != 0) {
                    lastResult = "texture " + w + "x" + h + " left to DH";
                    return null;
                }
                int step = w / SIZE, per = step * step;
                // how many of each cell's pixels are cloud
                int[][] counts = new int[SIZE][SIZE];
                long opaque = 0;
                for (int x = 0; x < SIZE; x++) {
                    for (int y = 0; y < SIZE; y++) {
                        int n = 0;
                        for (int i = 0; i < step; i++) {
                            for (int j = 0; j < step; j++) {
                                // NativeImage is ABGR: the alpha is the top byte
                                if ((image.getPixelRGBA(x * step + i, y * step + j) >>> 24) >= 128) n++;
                            }
                        }
                        counts[x][y] = n;
                        opaque += n;
                    }
                }
                // DH's own map keeps two thirds of its texture's cloud share (13.6% of cells against 20.4% of pixels): the
                // threshold of cloud pixels a cell needs is the one whose share of cloud cells comes nearest that
                double target = DH_KEEPS * opaque / per;
                int threshold = per, best = Integer.MAX_VALUE;
                for (int t = 1; t <= per; t++) {
                    int cells = 0;
                    for (int[] column : counts) for (int n : column) if (n >= t) cells++;
                    int off = (int) Math.abs(cells - target);
                    if (off < best) {
                        best = off;
                        threshold = t;
                    }
                }
                boolean[][] map = new boolean[SIZE][SIZE];
                int clouds = 0;
                for (int x = 0; x < SIZE; x++) {
                    for (int y = 0; y < SIZE; y++) {
                        map[x][y] = counts[x][y] >= threshold;
                        if (map[x][y]) clouds++;
                    }
                }
                lastResult = clouds + " of " + (SIZE * SIZE) + " cells (" + threshold + " of " + per + " pixels a cell) from " + resource.get().sourcePackId();
                LOGGER.info("Backported Clouds: Distant Horizons' clouds take the game's cloud texture ({})", lastResult);
                return map;
            }
        } catch (Exception e) {
            lastResult = "failed: " + e;
            LOGGER.warn("Backported Clouds: the cloud texture couldn't be read for Distant Horizons' clouds ({}); DH keeps its own", e.toString());
            return null;
        }
    }

    /** What the last map was made of (the harness reads it). */
    public static String lastResult() {
        return lastResult;
    }
}
