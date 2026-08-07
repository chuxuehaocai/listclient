package dev.naominet.listclient.module.render;

import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.value.Mode;
import dev.naominet.listclient.value.Numbers;

/**
 * PerfBoost – client-side performance tuning for the vanilla renderer.
 * <p>
 * A single switch for a bundle of non-destructive, server-independent
 * optimizations applied through the {@code mixin/performance} mixins. Every
 * optimization is gated behind {@link #isEnable()}, so toggling the module
 * off restores vanilla behavior exactly. The knob sizes are exposed as
 * module values:
 * <ul>
 *   <li>{@link #renderDistance} – extra squared-distance culling for entities
 *       beyond the normal frustum check (0 = vanilla).</li>
 *   <li>{@link #maxParticles} – hard cap on living particles per frame
 *       (0 = vanilla).</li>
 *   <li>{@link #cloudMode} – "vanilla" or "off" (skips cloud rendering).</li>
 *   <li>{@link #chunkBudget} – maximum section rebuilds compiled per frame
 *       (0 = vanilla).</li>
 * </ul>
 * All limits are best-effort: the mixins only skip work, never alter game
 * state, so nothing can desync or crash on server logic.
 */
public class PerfBoost extends Module {

    /** Extra squared-distance culling radius (blocks², 0 = disabled). */
    public static final Numbers renderDistance = new Numbers("Render Distance", 100d, 0d, 400d, 25d);

    /** Hard cap on living particles per frame (0 = vanilla). */
    public static final Numbers maxParticles = new Numbers("Max Particles", 700d, 0d, 3000d, 100d);

    /** Cloud rendering: "vanilla" or "off". */
    public static final Mode cloudMode = new Mode("Clouds", new String[]{"vanilla", "off"}, "vanilla");

    /** Section rebuilds compiled per frame (0 = vanilla). */
    public static final Numbers chunkBudget = new Numbers("Chunk Budget", 0d, 0d, 8d, 1d);

    public PerfBoost() {
        super("PerfBoost", Category.Render);
        addValues(renderDistance, maxParticles, cloudMode, chunkBudget);
    }

    /* ---- static queries the mixins call (cheap, no locking) ---- */

    private static PerfBoost instance;

    /**
     * Shared per-frame chunk-rebuild counter. Written by the render-thread
     * scheduling path only (the mixin injects {@code SectionRenderDispatcher.schedule}
     * which runs on the render thread), reset once per frame by
     * {@link #resetFrameBudget()} from LevelRenderer.render. Lives here (a
     * plain class) rather than in a mixin so multiple mixins can share it
     * without cross-mixin static coupling.
     */
    public static int perfScheduledRebuilds;

    /** Reset the per-frame rebuild counter; called at the start of each world render. */
    public static void resetFrameBudget() {
        perfScheduledRebuilds = 0;
    }

    public static PerfBoost instance() {
        return instance;
    }

    public static boolean active() {
        return instance != null && instance.isEnable();
    }

    /** Entity squared-distance culling radius (0 = vanilla). */
    public static double entityCullDistanceSqr() {
        double v = renderDistance.getValue();
        return v <= 0d ? 0d : v * v;
    }

    /** Particle cap (0 = vanilla). */
    public static int particleCap() {
        return (int) Math.max(0d, maxParticles.getValue());
    }

    /** Chunk rebuild budget (0 = vanilla). */
    public static int chunkBudget() {
        return (int) Math.max(0d, chunkBudget.getValue());
    }

    public static boolean cloudsOff() {
        return active() && cloudMode.isCurrentMode("off");
    }

    @Override
    public void onEnable() {
        instance = this;
    }

    @Override
    public void onDisable() {
        if (instance == this) {
            instance = null;
        }
    }
}
