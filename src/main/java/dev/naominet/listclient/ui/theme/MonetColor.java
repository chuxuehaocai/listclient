package dev.naominet.listclient.ui.theme;

/**
 * Monet / Material You color science: seed extraction from album art and a
 * tonal-palette generator that turns one seed color into a full M3 dark scheme.
 * <p>
 * This is an HSL approximation of Google's HCT tonal palettes – not bit-exact
 * to the reference library, but it produces the same perceptual behaviour: a
 * fixed hue per palette, a chroma that mostly survives across tones, and
 * neutral surfaces subtly tinted toward the seed hue. Good enough for a mod UI
 * and dependency-free.
 */
public final class MonetColor {

    /* ================================================================== */
    /*  seed extraction                                                   */
    /* ================================================================== */

    /**
     * Pick a vibrant seed color from ARGB pixels (row-major). Buckets pixels
     * into a coarse hue/quantized histogram weighted by chroma and a mid-tone
     * preference, then returns the most representative vibrant bucket's average
     * color. Falls back to the overall average, then to the default seed, if the
     * image is flat/greyscale/transparent.
     */
    public static int seedFromPixels(int[] px, int w, int h) {
        if (px == null || px.length == 0) return M3.DEFAULT_SEED;

        // 24 hue buckets; accumulate chroma-weighted sums for a vibrant pick,
        // and a separate plain average as the greyscale fallback.
        double[] wr = new double[24], wg = new double[24], wb = new double[24], ww = new double[24];
        double ar = 0, ag = 0, ab = 0, an = 0;

        int stepX = Math.max(1, w / 48);
        int stepY = Math.max(1, h / 48);
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int p = px[y * w + x];
                int a = (p >>> 24) & 0xFF;
                if (a < 128) continue;
                int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                ar += r; ag += g; ab += b; an++;

                float[] hsl = rgbToHsl(r, g, b);
                float hue = hsl[0], sat = hsl[1], light = hsl[2];
                // Reject near-greyscale and extreme tones (they carry no hue).
                if (sat < 0.15f || light < 0.12f || light > 0.92f) continue;
                // Weight by saturation and a bell around mid-light (vibrancy).
                double midBias = 1.0 - Math.abs(light - 0.5) * 1.6;
                double weight = sat * Math.max(0.05, midBias);
                int bucket = Math.min(23, (int) (hue / 360f * 24f));
                wr[bucket] += r * weight;
                wg[bucket] += g * weight;
                wb[bucket] += b * weight;
                ww[bucket] += weight;
            }
        }

        int best = -1;
        double bestW = 0;
        for (int i = 0; i < 24; i++) {
            if (ww[i] > bestW) {
                bestW = ww[i];
                best = i;
            }
        }
        if (best >= 0 && bestW > 0.5) {
            int r = clamp255(wr[best] / ww[best]);
            int g = clamp255(wg[best] / ww[best]);
            int b = clamp255(wb[best] / ww[best]);
            // Boost chroma a touch so muted covers still tint the UI clearly.
            return boostChroma(0xFF000000 | (r << 16) | (g << 8) | b, 1.25f);
        }
        if (an > 0) {
            return 0xFF000000 | (clamp255(ar / an) << 16) | (clamp255(ag / an) << 8) | clamp255(ab / an);
        }
        return M3.DEFAULT_SEED;
    }

    private static int boostChroma(int argb, float mul) {
        float[] hsl = rgbToHsl((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
        return hslToArgb(hsl[0], Math.min(1f, hsl[1] * mul), hsl[2]);
    }

    /* ================================================================== */
    /*  tonal scheme                                                      */
    /* ================================================================== */

    /**
     * Build a full M3 dark scheme from a seed color and write it into the
     * {@code out} array in the fixed order consumed by {@link M3#applyScheme}.
     */
    public static void darkScheme(int seed, int[] out) {
        float[] hsl = rgbToHsl((seed >> 16) & 0xFF, (seed >> 8) & 0xFF, seed & 0xFF);
        float h = hsl[0];
        // Per-palette chroma (HSL-sat approximations of HCT chroma).
        float cPrimary = clampf(hsl[1] * 0.95f, 0.35f, 0.85f);
        float cSecondary = 0.22f;
        float cTertiary = 0.30f;
        float cNeutral = 0.06f;
        float cNeutralVar = 0.11f;
        float ht = (h + 60f) % 360f; // tertiary hue shift

        int i = 0;
        out[i++] = tone(h, cPrimary, 80);        // PRIMARY
        out[i++] = tone(h, cPrimary, 20);        // ON_PRIMARY
        out[i++] = tone(h, cPrimary, 30);        // PRIMARY_CONTAINER
        out[i++] = tone(h, cPrimary, 90);        // ON_PRIMARY_CONTAINER

        out[i++] = tone(h, cSecondary, 80);      // SECONDARY
        out[i++] = tone(h, cSecondary, 20);      // ON_SECONDARY
        out[i++] = tone(h, cSecondary, 30);      // SECONDARY_CONTAINER
        out[i++] = tone(h, cSecondary, 90);      // ON_SECONDARY_CONTAINER

        out[i++] = tone(ht, cTertiary, 80);      // TERTIARY
        out[i++] = tone(ht, cTertiary, 20);      // ON_TERTIARY

        out[i++] = tone(h, cNeutral, 6);         // SURFACE
        out[i++] = tone(h, cNeutral, 4);         // SURFACE_CONTAINER_LOWEST
        out[i++] = tone(h, cNeutral, 10);        // SURFACE_CONTAINER_LOW
        out[i++] = tone(h, cNeutral, 12);        // SURFACE_CONTAINER
        out[i++] = tone(h, cNeutral, 17);        // SURFACE_CONTAINER_HIGH
        out[i++] = tone(h, cNeutral, 22);        // SURFACE_CONTAINER_HIGHEST
        out[i++] = tone(h, cNeutral, 90);        // ON_SURFACE
        out[i++] = tone(h, cNeutralVar, 80);     // ON_SURFACE_VARIANT

        out[i++] = tone(h, cNeutralVar, 60);     // OUTLINE
        out[i++] = tone(h, cNeutralVar, 30);     // OUTLINE_VARIANT

        out[i++] = tone(h, cNeutral, 90);        // INVERSE_SURFACE
        out[i] = tone(h, cNeutral, 20);          // INVERSE_ON_SURFACE
    }

    /** Number of scheme slots {@link #darkScheme} writes. */
    public static final int SCHEME_SIZE = 22;

    /** A tone: hue (deg), chroma (0..1 as HSL sat), tone (0..100 as lightness). */
    private static int tone(float hue, float chroma, int toneL) {
        return hslToArgb(hue, chroma, toneL / 100f);
    }

    /* ================================================================== */
    /*  color space helpers                                               */
    /* ================================================================== */

    /** @return {hueDeg 0..360, sat 0..1, light 0..1} */
    public static float[] rgbToHsl(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float l = (max + min) / 2f;
        float hue = 0, sat = 0;
        float d = max - min;
        if (d > 1e-4f) {
            sat = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == rf) hue = (gf - bf) / d + (gf < bf ? 6f : 0f);
            else if (max == gf) hue = (bf - rf) / d + 2f;
            else hue = (rf - gf) / d + 4f;
            hue *= 60f;
        }
        return new float[]{hue, sat, l};
    }

    public static int hslToArgb(float hue, float sat, float light) {
        hue = ((hue % 360f) + 360f) % 360f;
        sat = clampf(sat, 0f, 1f);
        light = clampf(light, 0f, 1f);
        float c = (1f - Math.abs(2f * light - 1f)) * sat;
        float hp = hue / 60f;
        float xx = c * (1f - Math.abs(hp % 2f - 1f));
        float r = 0, g = 0, b = 0;
        if (hp < 1) { r = c; g = xx; }
        else if (hp < 2) { r = xx; g = c; }
        else if (hp < 3) { g = c; b = xx; }
        else if (hp < 4) { g = xx; b = c; }
        else if (hp < 5) { r = xx; b = c; }
        else { r = c; b = xx; }
        float m = light - c / 2f;
        return 0xFF000000
                | (clamp255((r + m) * 255f) << 16)
                | (clamp255((g + m) * 255f) << 8)
                | clamp255((b + m) * 255f);
    }

    /** Blend two ARGB colors (RGB channels), full alpha out. */
    public static int lerp(int a, int b, float t) {
        t = clampf(t, 0f, 1f);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000
                | ((ar + Math.round((br - ar) * t)) << 16)
                | ((ag + Math.round((bg - ag) * t)) << 8)
                | (ab + Math.round((bb - ab) * t));
    }

    private static int clamp255(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    private static float clampf(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private MonetColor() {
    }
}
