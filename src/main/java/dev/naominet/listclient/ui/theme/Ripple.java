package dev.naominet.listclient.ui.theme;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Material 3 touch-ripple effect for the immediate-mode UI.
 * <p>
 * A button records a ripple on click via {@link #press}, keyed by a stable
 * identity (its label / index), and every frame calls {@link #draw} inside its
 * own bounds. The ripple is an anti-aliased circle that expands from the click
 * point to cover the component while its alpha fades, clipped to the
 * component's rectangle by a GL scissor. State is time-based (no per-frame
 * bookkeeping by the caller) and self-expiring.
 */
public final class Ripple {

    private static final long DURATION = 380L;

    private static final class R {
        final float px, py;
        final long start;
        R(float px, float py, long start) {
            this.px = px;
            this.py = py;
            this.start = start;
        }
    }

    private static final Map<Object, R> ACTIVE = new HashMap<>();

    /** Record a ripple originating at (clickX, clickY) for this component. */
    public static void press(Object key, float clickX, float clickY) {
        sweep();
        ACTIVE.put(key, new R(clickX, clickY, Util.getMillis()));
    }

    /**
     * Draw (and expire) the ripple for {@code key} inside the component rect,
     * clipped to it. {@code onColor} is the component's on-color; the ripple
     * uses it at a fading state-layer alpha.
     */
    public static void draw(GuiGraphicsExtractor g, Object key, int x, int y, int w, int h, int onColor) {
        draw(g, key, x, y, w, h, Math.min(w, h) / 2, onColor);
    }

    /** Draw a ripple with a rounded-rectangle clip matching the component. */
    public static void draw(GuiGraphicsExtractor g, Object key, int x, int y, int w, int h,
                            int radius, int onColor) {
        R r = ACTIVE.get(key);
        if (r == null) return;
        float t = (Util.getMillis() - r.start) / (float) DURATION;
        if (t >= 1f) {
            ACTIVE.remove(key);
            return;
        }
        float ease = 1f - (1f - t) * (1f - t);          // radius: decelerate
        // Max radius = distance from origin to the farthest corner.
        float maxR = (float) Math.hypot(Math.max(r.px - x, x + w - r.px),
                Math.max(r.py - y, y + h - r.py)) + 2f;
        float rad = ease * maxR;
        int alpha = (int) (M3.STATE_PRESSED * (1f - t)); // fade out
        if (alpha <= 0) return;
        int col = (onColor & 0x00FFFFFF) | (alpha << 24);

        g.enableScissor(x, y, x + w, y + h);
        try {
            int d = Math.max(1, Math.round(rad * 2));
            M3.roundRect(g, Math.round(r.px - rad), Math.round(r.py - rad), d, d, d / 2, col);
        } finally {
            g.disableScissor();
        }
    }

    /** Drop expired ripples (optional housekeeping; draw() already self-expires). */
    public static void sweep() {
        long now = Util.getMillis();
        Iterator<Map.Entry<Object, R>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().start >= DURATION) it.remove();
        }
    }

    private Ripple() {
    }
}
