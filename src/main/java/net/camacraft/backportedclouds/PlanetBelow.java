package net.camacraft.backportedclouds;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VS Genesis' planet below the camera, for the clouds (1.0.6; "Clouds fade into the VS Genesis planet" since 1.0.7). From a
 * third of the world's height over the render distance Genesis draws the planet the camera stands on as a textured cube
 * below the camera ({@code camY / 16 + renderDistance + 32} blocks down), fading it in from
 * {@code renderDistance + maxBuildHeight / 3} to {@code renderDistance + maxBuildHeight}. The cloud layer, far below and up
 * to the cloud range wide, was drawn past the cube's edges (the owner, 2026-10-28); 1.0.6 cut the clouds to the cube's
 * outline, which left full-size clouds over a small planet, covering more of it the lower one flew ("the clouds change
 * how much of the texture is visible"). Since 1.0.7 the clouds fade out with the planet's fade-in, by distance (nothing
 * within 32 blocks, all of it from 96), as Space Events fades the far world into the planet: the planet's own painted
 * clouds take their place.
 *
 * <p>The planet is read from Genesis' own planet renderer ({@code GenesisPlanetBelowMixin}, by name, nothing of
 * Genesis compiled against): when its {@code invoke} begins for the body the camera stands on, the cube is worked out
 * as Genesis does (a fallback for a frame in which it is not drawn, e.g. culled outside the view), and when the body is
 * really drawn its exact centre, half extent and alpha are taken from the draw. The record lasts the frame
 * ({@link #newFrame} at the start of each render tick). {@link #current} is for the cloud shaders and other mods' copies
 * of them (Camafy's storm clouds read it by reflection): the centre from the camera in world axes, the half extent and
 * the alpha, all 0 without a planet, with the option off or without Genesis.
 */
public final class PlanetBelow {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ON_CELESTIAL = "shipwrights.genesis.space.VantagePoint$OnCelestial";
    private static final Map<Class<?>, Method> CELESTIAL_OF = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> SIZE_OF = new ConcurrentHashMap<>();
    private static final float[] NONE = new float[5];
    private static long frame = 1, recordedFrame;
    private static boolean inBelowInvoke, failed, said;
    private static float cx, cy, cz, half, alpha;

    private PlanetBelow() {}

    /** A new frame: the last frame's record no longer counts. */
    public static void newFrame() {
        frame++;
    }

    /** Genesis' planet renderer begins a body: the one the camera stands on is the planet below. */
    public static void invokeStarts(RenderLevelStageEvent event, Object celestial, Object vantage) {
        inBelowInvoke = false;
        if (failed || celestial == null || vantage == null || !vantage.getClass().getName().equals(ON_CELESTIAL)) return;
        try {
            Method celestialOf = CELESTIAL_OF.computeIfAbsent(vantage.getClass(), c -> method(c, "celestial"));
            Object standingOn = celestialOf.invoke(vantage);
            if (standingOn == null || !standingOn.equals(celestial)) return;
            inBelowInvoke = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            double camY = event.getCamera().getPosition().y;
            float rd = mc.gameRenderer.getRenderDistance();
            int h = mc.level.getMaxBuildHeight();
            float lo = rd + h / 3f, hi = rd + h;
            Method sizeOf = SIZE_OF.computeIfAbsent(celestial.getClass(), c -> method(c, "getActualSize"));
            double size = ((Number) sizeOf.invoke(celestial)).doubleValue();
            // Genesis' own placement, in case the draw is left out this frame
            cx = 0f;
            cy = (float) -(camY / 16.0 + rd + 32.0);
            cz = 0f;
            half = (float) (size / 2.0);
            alpha = Mth.clamp(((float) camY - lo) / (hi - lo), 0f, 1f);
            recordedFrame = frame;
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            inBelowInvoke = false;
            LOGGER.warn("Backported Clouds: VS Genesis' planet below couldn't be read ({}); the clouds no longer fade into it", e.toString());
        }
    }

    private static Method method(Class<?> c, String name) {
        try {
            Method m = c.getMethod(name);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(c.getName() + " has no " + name + "()", e);
        }
    }

    /** Whether Genesis is drawing the planet below right now (its {@code invoke} for the body the camera stands on). */
    public static boolean drawingBelow() {
        return inBelowInvoke;
    }

    /** Genesis' planet renderer is done with the body. */
    public static void invokeEnds() {
        inBelowInvoke = false;
    }

    /** The body is really drawn: where (from the camera, world axes), how large and how faded. */
    public static void drawn(double x, double y, double z, double halfExtent, float a) {
        if (!inBelowInvoke) return;
        cx = (float) x;
        cy = (float) y;
        cz = (float) z;
        half = (float) halfExtent;
        alpha = Mth.clamp(a, 0f, 1f);
        recordedFrame = frame;
        if (!said) {
            said = true;
            LOGGER.info("Backported Clouds: the clouds fade into VS Genesis' planet below ({})", describe());
        }
    }

    /** The planet's fade-in at which the world's clouds have half handed over to the planet's own (see {@link #handover}). */
    public static final float HANDOVER_MIDDLE = 0.5f;
    /** Half the width of the handover, in the planet's fade-in. */
    public static final float HANDOVER_HALF_WIDTH = 0.15f;

    /**
     * How far the world's clouds have handed over to the clouds on the planet ({@link PlanetCloudShell}) at the planet's
     * fade-in {@code a}: none below 0.35, all of it from 0.65, eased between. The handover is kept short, around the height
     * at which the two cloud layers look the same size, because only there do they match.
     */
    public static float handover(float a) {
        float t = Mth.clamp((a - (HANDOVER_MIDDLE - HANDOVER_HALF_WIDTH)) / (2f * HANDOVER_HALF_WIDTH), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * This frame's planet below for the cloud shaders: {x, y, z} its centre from the camera in world axes, the half extent
     * and how far the world's clouds have handed over to the planet's ({@link #handover}, 0 to 1); all 0 when there is none,
     * with the option off or under a shader pack. Camafy's storm-cloud copies read the last value as the clouds' fade.
     */
    public static float[] current() {
        if (recordedFrame != frame || alpha <= 0f || half <= 0f) return NONE;
        if (!Config.CLIENT.cloudsOnGenesisPlanet.get() || OculusCompat.shaderPackInUse()) return NONE;
        return new float[]{cx, cy, cz, half, handover(alpha)};
    }

    /** The planet's own fade-in this frame (Genesis' alpha), 0 without a planet below (the harness reads it). */
    public static float planetAlpha() {
        return recordedFrame == frame ? alpha : 0f;
    }

    /** The planet below as the cloud shaders see it (the harness reads it). */
    public static String describe() {
        float[] p = current();
        return p[4] <= 0f && planetAlpha() <= 0f ? "none" : String.format(Locale.ROOT, "centre %.1f %.1f %.1f half %.1f planet %.2f handover %.2f",
                p[0], p[1], p[2], p[3], planetAlpha(), p[4]);
    }

    /** Sets this frame's planet on a cloud shader ({@code PlanetBelow}, {@code PlanetBelowAlpha}); nothing on a shader without them. */
    public static void apply(ShaderInstance shader) {
        float[] p = current();
        shader.safeGetUniform("PlanetBelow").set(p[0], p[1], p[2], p[3]);
        shader.safeGetUniform("PlanetBelowAlpha").set(p[4]);
    }
}
