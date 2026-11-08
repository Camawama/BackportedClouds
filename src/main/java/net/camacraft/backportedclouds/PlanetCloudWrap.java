package net.camacraft.backportedclouds;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The whole cloud texture wrapped round a VS Genesis planet, for {@link PlanetCloudShell} to lay on it when the planet is
 * seen from afar (1.0.8). A planet is a cube that Genesis lights as a sphere, and the texture a tile: a square that tiles
 * can't cover a ball without a tear or a pinch, so the pinch is put where it does no harm. The direction of each point on
 * the cube is taken to the texture by Mercator's projection: once round the equator along the texture's width, its full
 * height up to about 85 degrees either side, with the clouds' shapes kept (it is conformal), and the clouds thin out
 * before the poles ({@link #FADE_FROM}, {@link #FADE_TO}), where every longitude meets.
 *
 * <p>Taken directly that projection shears the texture's pixels near a face's corners, against the square pixels of the
 * planet under it. So the wrap is baked: each face gets {@link #CELLS} square cells along each side (the planet's picture
 * shows 256; the 64 here are a little coarser than the 61 cells a face the planet below shows at 8 chunks, on purpose:
 * near the same size of cloud), and a cell is cloud where most of the texture it covers is. The same pattern, square
 * pixels lined up with the cube. Baked on the render thread from a table of where each cell's samples fall, about a
 * third of a million texture reads, when the drift has moved on by a quarter of a texel ({@link #BAKE_TICKS}) or the
 * storms changed; only for bodies being drawn.
 *
 * <p>Storms ({@link #storms}, sent by Space Events, Camafy's weather cells on the overworld's face) are baked into the same
 * cells: inside a storm the texture's clouds thicken towards its middle and wind into it in arms, in three steps of
 * thickness from a plain cloud at its rim to a solid heart. The cells are white; their alpha is the thickness.
 */
public final class PlanetCloudWrap {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Cells along a side of a face. */
    public static final int CELLS = 64;
    private static final int WIDTH = CELLS * 3, HEIGHT = CELLS * 2;
    /** Samples along a side of a cell. */
    private static final int SAMPLES = 3;
    /** Between these distances from the texture's middle row (1 is its full height) the clouds thin out: about 78 and 84 degrees of latitude. */
    private static final float FADE_FROM = 0.36F, FADE_TO = 0.46F;
    /** A quarter of a texel of drift: the world's clouds drift 0.03 blocks a tick, a texel being 12 blocks. */
    private static final int BAKE_TICKS = 100;
    /** {@code PlanetCloudShell.FACES}' -Z face, where Genesis lays out a world's ground. */
    private static final int GROUND_FACE = 5;
    /** How many values describe a storm in {@link #storms}. */
    public static final int STORM_STRIDE = 5;
    /** How strongly a storm's middle thickens the clouds: the soft texture's threshold at full strength, against 0.5 outside. */
    private static final float STORM_THRESHOLD = 0.06F;
    /**
     * How much of what lies under it a cloud hides, in the cells' alpha. From above a cloud's brightness is its thickness: a
     * thin one lets the planet show through and a storm's heart is the whitest thing on the planet. A storm is never grey
     * here, that is its shaded base seen from under it. The shell's shadows take the alpha too, so thick cloud throws the
     * darker shadow.
     */
    private static final float PLAIN_THICKNESS = 0.8F, THUNDER_HEART = 1F, RAIN_HEART = 0.92F;
    /** Texture texels to a half size of a face, inside a storm. */
    private static final float STORM_TEXELS = 34F;
    /** How many arms wind into a storm, and how much they add to the clouds' thickness. */
    private static final int STORM_ARMS = 3;
    private static final float ARM_WEIGHT = 0.24F;
    /** A storm's thickness comes in steps, as a picture of this size would have it: its rim a plain cloud, then half way, its heart thickest. */
    private static final float MIDDLE_FROM = 0.3F, HEART_FROM = 0.6F;

    private static boolean[] mask;
    private static float[] soft;
    private static int maskWidth, maskHeight;
    private static float[] tableU, tableV, tableFade;
    private static int generation;
    private static boolean failed;
    private static final Map<ResourceLocation, Layer> LAYERS = new HashMap<>();
    private static final Map<ResourceLocation, float[]> STORMS = new HashMap<>();
    private static int stormVersion;
    private static long lastBakeNanos, bakes;

    private static final class Layer {
        final DynamicTexture texture = new DynamicTexture(WIDTH, HEIGHT, false);
        final ResourceLocation location;
        final ResourceLocation body;
        final float seedU, seedV;
        long bakedStep = Long.MIN_VALUE;
        int bakedGeneration = -1, bakedStorms = -1;

        Layer(ResourceLocation body) {
            this.body = body;
            this.location = new ResourceLocation(BackportedClouds.MODID, "planet_clouds/" + body.getNamespace() + "/" + body.getPath());
            // Earth keeps the texture as it is; every other world its own part of it
            boolean earth = "minecraft".equals(body.getNamespace()) && "overworld".equals(body.getPath());
            int hash = body.hashCode();
            this.seedU = earth ? 0F : (hash & 0xFFFF) / 65536F;
            this.seedV = earth ? 0F : ((hash >>> 16) & 0xFFFF) / 65536F;
        }
    }

    private PlanetCloudWrap() {}

    /** After a resource reload: the texture is read again and every layer baked again. */
    public static void invalidate() {
        mask = null;
        soft = null;
        failed = false;
    }

    /** {@code body}'s wrap baked for this moment, as a texture of six faces, three across and two down; null if the cloud texture can't be read. */
    static ResourceLocation texture(Minecraft mc, ResourceLocation body, double ticks) {
        if (mask == null && !read(mc)) return null;
        if (tableU == null) buildTable();
        Layer layer = LAYERS.get(body);
        if (layer == null) {
            layer = new Layer(body);
            mc.getTextureManager().register(layer.location, layer.texture);
            LAYERS.put(body, layer);
        }
        long step = (long) Math.floor(ticks / BAKE_TICKS);
        if (layer.bakedStep != step || layer.bakedGeneration != generation || layer.bakedStorms != stormVersion) {
            bake(layer, step * BAKE_TICKS);
            layer.texture.upload();
            layer.bakedStep = step;
            layer.bakedGeneration = generation;
            layer.bakedStorms = stormVersion;
        }
        return layer.location;
    }

    /** Which face of the shell's atlas {@code face} is, across and down. */
    static float atlasU(int face, float s) {
        return (face % 3 + (s + 1F) * 0.5F) / 3F;
    }

    static float atlasV(int face, float t) {
        return (face / 3 + (t + 1F) * 0.5F) / 2F;
    }

    /**
     * The storms on a body's ground now, replacing what was there: for each {@link #STORM_STRIDE} values, its middle
     * along the -Z face's X and Y in half sizes of the face, its radius likewise, its strength from 0 to 1, and 1 for a
     * thunderstorm or 0. Space Events calls this by reflection.
     */
    public static void storms(ResourceLocation body, float[] storms) {
        float[] old = STORMS.get(body);
        if (old == null && (storms == null || storms.length == 0)) return;
        if (old != null && Arrays.equals(old, storms)) return;
        if (storms == null || storms.length == 0) STORMS.remove(body);
        else STORMS.put(body, storms.clone());
        stormVersion++;
    }

    /** No storms anywhere (Space Events, leaving space). */
    public static void clearStorms() {
        if (STORMS.isEmpty()) return;
        STORMS.clear();
        stormVersion++;
    }

    private static boolean read(Minecraft mc) {
        if (failed) return false;
        try (InputStream in = mc.getResourceManager().getResourceOrThrow(CloudTexture.location()).open(); NativeImage image = NativeImage.read(in)) {
            int w = image.getWidth(), h = image.getHeight();
            boolean[] m = new boolean[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) m[y * w + x] = (image.getPixelRGBA(x, y) >>> 24) > 127;
            }
            mask = m;
            maskWidth = w;
            maskHeight = h;
            soft = blurred(m, w, h);
            generation++;
            return true;
        } catch (IOException | RuntimeException e) {
            failed = true;
            LOGGER.warn("Backported Clouds: the cloud texture couldn't be read for the planets ({})", e.toString());
            return false;
        }
    }

    /** The mask with its edges spread out, tiling: what a storm thresholds to thicken the clouds. */
    private static float[] blurred(boolean[] m, int w, int h) {
        float[] a = new float[w * h], b = new float[w * h];
        for (int i = 0; i < a.length; i++) a[i] = m[i] ? 1F : 0F;
        for (int pass = 0; pass < 4; pass++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    b[y * w + x] = (a[y * w + x] + a[y * w + Math.floorMod(x - 1, w)] + a[y * w + Math.floorMod(x + 1, w)]
                            + a[Math.floorMod(y - 1, h) * w + x] + a[Math.floorMod(y + 1, h) * w + x]) / 5F;
                }
            }
            float[] t = a;
            a = b;
            b = t;
        }
        return a;
    }

    /** Where each cell's samples fall in the texture, once: from the cell's direction on the cube to Mercator's u and v. */
    private static void buildTable() {
        int count = CELLS * CELLS * 6, per = SAMPLES * SAMPLES;
        float[] u = new float[count * per], v = new float[count * per], fade = new float[count];
        int at = 0, cell = 0;
        for (float[][] f : PlanetCloudShell.FACES) {
            float[] n = f[0], a = f[1], b = f[2];
            for (int j = 0; j < CELLS; j++) {
                for (int i = 0; i < CELLS; i++) {
                    float kept = 0F;
                    for (int q = 0; q < SAMPLES; q++) {
                        for (int p = 0; p < SAMPLES; p++) {
                            float s = -1F + 2F * (i + (p + 0.5F) / SAMPLES) / CELLS, t = -1F + 2F * (j + (q + 0.5F) / SAMPLES) / CELLS;
                            double x = n[0] + a[0] * s + b[0] * t, y = n[1] + a[1] * s + b[1] * t, z = n[2] + a[2] * s + b[2] * t;
                            double length = Math.sqrt(x * x + y * y + z * z);
                            double up = Mth.clamp(y / length, -0.999999, 0.999999);
                            float row = (float) (0.5 - 0.5 * Math.log((1.0 + up) / (1.0 - up)) / (2.0 * Math.PI));
                            u[at] = (float) (Math.atan2(x, z) / (2.0 * Math.PI) + 0.5);
                            v[at] = row;
                            at++;
                            float e = Mth.clamp((Math.abs(row - 0.5F) - FADE_FROM) / (FADE_TO - FADE_FROM), 0F, 1F);
                            kept += 1F - e * e * (3F - 2F * e);
                        }
                    }
                    fade[cell++] = kept / per;
                }
            }
        }
        tableU = u;
        tableV = v;
        tableFade = fade;
    }

    private static void bake(Layer layer, double ticks) {
        long start = System.nanoTime();
        NativeImage image = layer.texture.getPixels();
        if (image == null) return;
        float drift = (float) ((ticks * 0.03 / 12.0 / 256.0 + layer.seedU) % 1.0);
        float[] storms = STORMS.get(layer.body);
        int per = SAMPLES * SAMPLES, at = 0, cell = 0;
        for (int f = 0; f < 6; f++) {
            int x0 = f % 3 * CELLS, y0 = f / 3 * CELLS;
            boolean ground = f == GROUND_FACE && storms != null;
            for (int j = 0; j < CELLS; j++) {
                for (int i = 0; i < CELLS; i++) {
                    int cover = 0;
                    for (int k = 0; k < per; k++, at++) {
                        if (cloudAt(tableU[at] + drift, tableV[at] + layer.seedV)) cover++;
                    }
                    boolean cloud = (float) cover / per * tableFade[cell++] > 0.5F;
                    float thickness = PLAIN_THICKNESS;
                    if (ground) {
                        // the -Z face: x along its b axis, y against its a axis
                        float s = -1F + 2F * (i + 0.5F) / CELLS, t = -1F + 2F * (j + 0.5F) / CELLS;
                        float over = stormOver(storms, t, -s, ticks);
                        if (over >= 0F) {
                            cloud = true;
                            thickness = over;
                        } else if (over < -1.5F) {
                            cloud = false;
                        }
                    }
                    image.setPixelRGBA(x0 + i, y0 + j, (cloud ? (int) (thickness * 255F) << 24 : 0) | 0xFFFFFF);
                }
            }
        }
        lastBakeNanos = System.nanoTime() - start;
        bakes++;
    }

    /** At the -Z face's point {@code x, y}: the thickness of a storm's cloud there, or -1 for no storm there, or -2 for a clear gap inside one. */
    private static float stormOver(float[] storms, float x, float y, double ticks) {
        float result = -1F;
        for (int i = 0; i + STORM_STRIDE <= storms.length; i += STORM_STRIDE) {
            float dx = x - storms[i], dy = y - storms[i + 1], r = storms[i + 2];
            if (r <= 0F) continue;
            float d = Mth.sqrt(dx * dx + dy * dy) / r;
            if (d >= 1F) continue;
            float strength = storms[i + 3] * (1F - d * d);
            // wound in towards the middle, and turning slowly
            float turn = 2.6F * (1F - d) + (float) (ticks * 0.0006 % (2.0 * Math.PI));
            float c = Mth.cos(turn), n = Mth.sin(turn);
            float sx = (dx * c - dy * n) * STORM_TEXELS + storms[i] * 37F, sy = (dx * n + dy * c) * STORM_TEXELS + storms[i + 1] * 37F;
            float arm = Mth.sin(STORM_ARMS * (float) Mth.atan2(dy, dx) + 7F * d + (float) (ticks * 0.0006 % (2.0 * Math.PI)));
            boolean thick = softAt(sx, sy) + ARM_WEIGHT * arm > Mth.lerp(strength, 0.5F, STORM_THRESHOLD);
            if (thick) {
                float heart = storms[i + 4] > 0.5F ? THUNDER_HEART : RAIN_HEART;
                float thickness = strength >= HEART_FROM ? heart : strength >= MIDDLE_FROM ? (PLAIN_THICKNESS + heart) * 0.5F : PLAIN_THICKNESS;
                result = Math.max(result, thickness);
            } else if (result < 0F) {
                result = -2F;
            }
        }
        return result;
    }

    private static boolean cloudAt(float u, float v) {
        int x = (int) ((u - Mth.floor(u)) * maskWidth), y = (int) ((v - Mth.floor(v)) * maskHeight);
        return mask[Math.min(y, maskHeight - 1) * maskWidth + Math.min(x, maskWidth - 1)];
    }

    private static float softAt(float x, float y) {
        return soft[Math.floorMod((int) y, maskHeight) * maskWidth + Math.floorMod((int) x, maskWidth)];
    }

    /** What has been baked (the harness reads it). */
    public static String describe() {
        return String.format(Locale.ROOT, "%d bakes, last %.2f ms, %d bodies, %d with storms", bakes, lastBakeNanos / 1.0e6, LAYERS.size(), STORMS.size());
    }
}
