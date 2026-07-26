package dev.naominet.listclient.ui.theme;

import dev.naominet.listclient.utils.AnimationUtils;

/**
 * Monet theme controller: owns the current seed color and animates smooth
 * transitions between seeds (e.g. when the playing song changes). Any thread
 * may {@link #requestSeed}; {@link #update()} must run on the render thread
 * once per frame – call it at the top of every screen render and the HUD.
 */
public final class MonetTheme {

    private static volatile int targetSeed = M3.DEFAULT_SEED;
    private static int displayed = M3.DEFAULT_SEED;
    private static int lastApplied = ~M3.DEFAULT_SEED; // force first apply

    /** Request a new seed (any thread). Ignored if identical to the target. */
    public static void requestSeed(int argb) {
        targetSeed = 0xFF000000 | (argb & 0x00FFFFFF);
    }

    /** Reset to the built-in Monet Light Blue (e.g. playback stopped). */
    public static void reset() {
        targetSeed = M3.DEFAULT_SEED;
    }

    public static int targetSeed() {
        return targetSeed;
    }

    /**
     * Eases the displayed seed toward the target and re-applies the M3 scheme
     * only when it actually moved. Cheap on steady state (one int compare).
     */
    public static void update() {
        int t = targetSeed;
        if (displayed != t) {
            displayed = MonetColor.lerp(displayed, t, easeStep());
            // Snap when very close so we stop churning the scheme.
            if (near(displayed, t)) {
                displayed = t;
            }
        }
        if (displayed != lastApplied) {
            M3.applyScheme(displayed);
            lastApplied = displayed;
        }
    }

    private static float easeStep() {
        // Frame-rate independent approach (~250ms morph); reuse the shared curve.
        return AnimationUtils.easeExp(0f, 1f, 10f);
    }

    private static boolean near(int a, int b) {
        return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) <= 1
                && Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)) <= 1
                && Math.abs((a & 0xFF) - (b & 0xFF)) <= 1;
    }

    private MonetTheme() {
    }
}
