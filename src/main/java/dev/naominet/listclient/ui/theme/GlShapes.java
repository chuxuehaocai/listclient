package dev.naominet.listclient.ui.theme;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;

/**
 * Pure-GL anti-aliased rounded rectangles and drop shadows.
 * <p>
 * No textures and no images: shapes are tessellated into quads for the
 * standard {@code RenderPipelines.GUI} (position-color) pipeline and submitted
 * as a custom {@link GuiElementRenderState}, so they batch and z-order exactly
 * like vanilla {@code fill()} calls and honor the current pose and scissor.
 * Anti-aliasing comes from geometry: the solid interior stops half a pixel
 * inside the true outline and a one-pixel "feather" ring runs to half a pixel
 * outside it with the outer vertices at alpha&nbsp;0 – the GPU's per-fragment
 * color interpolation produces the coverage ramp. Shadows are the same
 * outline extruded outward in bands whose vertex alphas follow a quadratic
 * falloff.
 * <p>
 * The outline is described per-corner by a center and radius; offsetting the
 * shape by {@code d} just adds {@code d} to each corner radius, which is the
 * exact Minkowski offset of a rounded rectangle (square corners naturally
 * grow a radius-{@code d} arc – exactly what a soft shadow should do).
 * <p>
 * Requires the access widener entries for {@code guiRenderState} /
 * {@code scissorStack}. Render-thread only, like all GUI drawing.
 */
final class GlShapes {

    /** Arc segments per 90° corner; radii here stay below ~16px. */
    private static final int SEGS = 6;
    /** Ring vertex count: 4 corners × (SEGS + 1) points each. */
    private static final int RING = 4 * (SEGS + 1);
    private static final float FEATHER = 0.5f;

    /**
     * Filled AA rounded rect. Corner flags select which corners are rounded;
     * square corners stay perfectly sharp (their feather collapses to the
     * corner point sideways, keeping straight edges crisp).
     */
    static void roundRect(GuiGraphicsExtractor g, float x, float y, float w, float h,
                          float radius, int argb, boolean tl, boolean tr, boolean bl, boolean br) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        float r = Math.min(radius, Math.min(w, h) / 2f);
        float[] cx = corners(x, y, w, h, r, tl, tr, bl, br);

        float[] inner = ring(cx, -FEATHER);
        float[] outer = ring(cx, FEATHER);

        // interior fan + feather ring
        int quads = RING + RING;
        float[] xy = new float[quads * 8];
        int[] col = new int[quads * 4];
        int p = 0;
        float ccx = x + w / 2f;
        float ccy = y + h / 2f;
        int clear = argb & 0x00FFFFFF;
        for (int i = 0; i < RING; i++) {
            int j = (i + 1) % RING;
            // Fan triangle (as a degenerate quad), all solid. The ring runs
            // clockwise on screen, so the fan emits (C, j, i) to come out
            // counterclockwise like vanilla fill quads – the GUI pipeline
            // backface-culls the other winding.
            p = quad(xy, col, p,
                    ccx, ccy, argb,
                    inner[j * 2], inner[j * 2 + 1], argb,
                    inner[i * 2], inner[i * 2 + 1], argb,
                    inner[i * 2], inner[i * 2 + 1], argb);
            // Feather band, (in_i, in_j, out_j, out_i) – shoelace-negative like
            // vanilla fill quads. Outer edge fades to zero coverage.
            p = quad(xy, col, p,
                    inner[i * 2], inner[i * 2 + 1], argb,
                    inner[j * 2], inner[j * 2 + 1], argb,
                    outer[j * 2], outer[j * 2 + 1], clear,
                    outer[i * 2], outer[i * 2 + 1], clear);
        }
        submit(g, xy, col, x - 1, y - 1, x + w + 1, y + h + 1);
    }

    /**
     * Soft drop shadow for a rounded rect of the given geometry. The shadow
     * hugs the outline from just under its edge out to {@code spread} px,
     * with a quadratic alpha falloff, shifted down by {@code offsetY}.
     * Draw it BEFORE the surface – the surface covers the interior.
     */
    static void shadow(GuiGraphicsExtractor g, float x, float y, float w, float h,
                       float radius, float spread, float offsetY, int argb) {
        if (w <= 0 || h <= 0 || spread <= 0 || (argb >>> 24) == 0) return;
        float r = Math.min(radius, Math.min(w, h) / 2f);
        float[] cx = corners(x, y + offsetY, w, h, r, true, true, true, true);

        int bands = 4;
        int baseA = argb >>> 24;
        int rgb = argb & 0x00FFFFFF;

        // The innermost ring starts deeper than the downward offset, so the
        // surface drawn on top always covers the shadow's inner boundary –
        // otherwise a bright seam shows between the card edge and the shadow.
        float d0 = -(FEATHER + Math.abs(offsetY) + 1f);
        float[][] rings = new float[bands + 1][];
        int[] alphas = new int[bands + 1];
        for (int b = 0; b <= bands; b++) {
            float t = b / (float) bands;
            rings[b] = ring(cx, d0 + t * (spread - d0));
            float fall = (1f - t) * (1f - t);
            alphas[b] = Math.round(baseA * fall);
        }

        float[] xy = new float[bands * RING * 8];
        int[] col = new int[bands * RING * 4];
        int p = 0;
        for (int b = 0; b < bands; b++) {
            float[] in = rings[b];
            float[] out = rings[b + 1];
            int ca = (alphas[b] << 24) | rgb;
            int cb = (alphas[b + 1] << 24) | rgb;
            for (int i = 0; i < RING; i++) {
                int j = (i + 1) % RING;
                // Same shoelace-negative band winding as the feather ring.
                p = quad(xy, col, p,
                        in[i * 2], in[i * 2 + 1], ca,
                        in[j * 2], in[j * 2 + 1], ca,
                        out[j * 2], out[j * 2 + 1], cb,
                        out[i * 2], out[i * 2 + 1], cb);
            }
        }
        submit(g, xy, col, x - spread - 1, y - spread - 1 + offsetY,
                x + w + spread + 1, y + h + spread + 1 + offsetY);
    }

    /**
     * Anti-aliased ring following the rounded-rect outline: solid between
     * {@code offset} and {@code offset + thickness} outside the shape edge,
     * with half-pixel feather on both sides. Used for focus indicators.
     */
    static void ring(GuiGraphicsExtractor g, float x, float y, float w, float h,
                     float radius, float offset, float thickness, int argb) {
        if (w <= 0 || h <= 0 || thickness <= 0 || (argb >>> 24) == 0) return;
        float r = Math.min(radius, Math.min(w, h) / 2f);
        float[] cx = corners(x, y, w, h, r, true, true, true, true);

        float[] d = {offset - FEATHER, offset, offset + thickness, offset + thickness + FEATHER};
        int[] alpha = {0, argb >>> 24, argb >>> 24, 0};
        int rgb = argb & 0x00FFFFFF;

        float[] xy = new float[3 * RING * 8];
        int[] col = new int[3 * RING * 4];
        int p = 0;
        for (int b = 0; b < 3; b++) {
            float[] in = ring(cx, d[b]);
            float[] out = ring(cx, d[b + 1]);
            int ca = (alpha[b] << 24) | rgb;
            int cb = (alpha[b + 1] << 24) | rgb;
            for (int i = 0; i < RING; i++) {
                int j = (i + 1) % RING;
                p = quad(xy, col, p,
                        in[i * 2], in[i * 2 + 1], ca,
                        in[j * 2], in[j * 2 + 1], ca,
                        out[j * 2], out[j * 2 + 1], cb,
                        out[i * 2], out[i * 2 + 1], cb);
            }
        }
        float pad = offset + thickness + 1;
        submit(g, xy, col, x - pad, y - pad, x + w + pad, y + h + pad);
    }

    /** Soft elliptical field built from GPU-interpolated alpha rings. */
    static void radialGlow(GuiGraphicsExtractor g, float x, float y, float w, float h, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        int segments = 32;
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        float rx = w / 2f;
        float ry = h / 2f;
        int clear = argb & 0x00FFFFFF;
        float[] xy = new float[segments * 8];
        int[] col = new int[segments * 4];
        int p = 0;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            float x0 = cx + (float) Math.cos(a0) * rx;
            float y0 = cy + (float) Math.sin(a0) * ry;
            float x1 = cx + (float) Math.cos(a1) * rx;
            float y1 = cy + (float) Math.sin(a1) * ry;
            p = quad(xy, col, p,
                    cx, cy, argb,
                    x1, y1, clear,
                    x0, y0, clear,
                    x0, y0, clear);
        }
        submit(g, xy, col, x, y, x + w, y + h);
    }

    /**
     * Filled, anti-aliased sine band clipped horizontally to a determinate
     * progress boundary. The center line oscillates while the thickness stays
     * constant, producing the M3 expressive wavy progress treatment.
     */
    static void wavyBand(GuiGraphicsExtractor g, float x, float y, float w, float h,
                         float progress, float phase, int argb) {
        float filled = w * Math.max(0f, Math.min(1f, progress));
        if (filled <= 0f || h <= 0f || (argb >>> 24) == 0) return;

        float amplitude = Math.min(1.05f, Math.max(0.35f, h * 0.16f));
        float half = Math.max(0.6f, h * 0.16f);
        float wavelength = Math.max(10f, h * 3f);
        int segments = Math.max(2, (int) Math.ceil(filled / 1.5f));
        int capSegments = 6;
        float[] xy = new float[(segments * 3 + capSegments) * 8];
        int[] col = new int[(segments * 3 + capSegments) * 4];
        int clear = argb & 0x00FFFFFF;
        int p = 0;

        for (int i = 0; i < segments; i++) {
            float x0 = filled * i / segments;
            float x1 = filled * (i + 1) / segments;
            float c0 = y + h / 2f + (float) Math.sin((x0 / wavelength) * Math.PI * 2f + phase) * amplitude;
            float c1 = y + h / 2f + (float) Math.sin((x1 / wavelength) * Math.PI * 2f + phase) * amplitude;
            float top0 = c0 - half;
            float top1 = c1 - half;
            float bottom0 = c0 + half;
            float bottom1 = c1 + half;

            p = quad(xy, col, p,
                    x + x0, top0, argb,
                    x + x1, top1, argb,
                    x + x1, top1 - FEATHER, clear,
                    x + x0, top0 - FEATHER, clear);
            p = quad(xy, col, p,
                    x + x0, bottom0, argb,
                    x + x1, bottom1, argb,
                    x + x1, top1, argb,
                    x + x0, top0, argb);
            p = quad(xy, col, p,
                    x + x0, bottom0 + FEATHER, clear,
                    x + x1, bottom1 + FEATHER, clear,
                    x + x1, bottom1, argb,
                    x + x0, bottom0, argb);
        }

        float endCenter = y + h / 2f
                + (float) Math.sin((filled / wavelength) * Math.PI * 2f + phase) * amplitude;
        for (int i = 0; i < capSegments; i++) {
            double a0 = -Math.PI / 2.0 + Math.PI * i / capSegments;
            double a1 = -Math.PI / 2.0 + Math.PI * (i + 1) / capSegments;
            p = quad(xy, col, p,
                    x + filled, endCenter, argb,
                    x + filled + (float) Math.cos(a1) * half,
                    endCenter + (float) Math.sin(a1) * half, argb,
                    x + filled + (float) Math.cos(a0) * half,
                    endCenter + (float) Math.sin(a0) * half, argb,
                    x + filled, endCenter, argb);
        }
        submit(g, xy, col, x - 1, y - 1, x + filled + half + 1, y + h + 1);
    }

    /* ================================================================== */
    /*  geometry                                                          */
    /* ================================================================== */

    /**
     * Corner descriptors, clockwise from top-left: {centerX, centerY, radius,
     * startAngleDeg} × 4. A square corner is radius 0 at the rect corner.
     */
    private static float[] corners(float x, float y, float w, float h, float r,
                                   boolean tl, boolean tr, boolean bl, boolean br) {
        float rtl = tl ? r : 0, rtr = tr ? r : 0, rbl = bl ? r : 0, rbr = br ? r : 0;
        return new float[]{
                x + rtl, y + rtl, rtl, 180f,          // top-left: 180° → 270°
                x + w - rtr, y + rtr, rtr, 270f,      // top-right: 270° → 360°
                x + w - rbr, y + h - rbr, rbr, 0f,    // bottom-right: 0° → 90°
                x + rbl, y + h - rbl, rbl, 90f,       // bottom-left: 90° → 180°
        };
    }

    /** Outline ring offset outward by {@code d} (negative = inward). */
    private static float[] ring(float[] corners, float d) {
        float[] pts = new float[RING * 2];
        int p = 0;
        for (int c = 0; c < 4; c++) {
            float cx = corners[c * 4];
            float cy = corners[c * 4 + 1];
            float cr = Math.max(0f, corners[c * 4 + 2] + d);
            float a0 = corners[c * 4 + 3];
            for (int s = 0; s <= SEGS; s++) {
                double ang = Math.toRadians(a0 + 90.0 * s / SEGS);
                pts[p++] = cx + (float) Math.cos(ang) * cr;
                pts[p++] = cy + (float) Math.sin(ang) * cr;
            }
        }
        return pts;
    }

    /**
     * Vertices are stored exactly as given – callers are responsible for
     * counterclockwise-on-screen winding (vanilla fill order), since the GUI
     * pipeline backface-culls.
     */
    private static int quad(float[] xy, int[] col, int p,
                            float x1, float y1, int c1, float x2, float y2, int c2,
                            float x3, float y3, int c3, float x4, float y4, int c4) {
        int v = p * 4;
        int o = p * 8;
        xy[o] = x1; xy[o + 1] = y1; col[v] = c1;
        xy[o + 2] = x2; xy[o + 3] = y2; col[v + 1] = c2;
        xy[o + 4] = x3; xy[o + 5] = y3; col[v + 2] = c3;
        xy[o + 6] = x4; xy[o + 7] = y4; col[v + 3] = c4;
        return p + 1;
    }

    /* ================================================================== */
    /*  submission                                                        */
    /* ================================================================== */

    private static void submit(GuiGraphicsExtractor g, float[] xy, int[] col,
                               float minX, float minY, float maxX, float maxY) {
        ScreenRectangle scissor = g.scissorStack.peek();
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        ScreenRectangle bounds = new ScreenRectangle(
                (int) Math.floor(minX), (int) Math.floor(minY),
                (int) Math.ceil(maxX - minX), (int) Math.ceil(maxY - minY))
                .transformMaxBounds(pose);
        if (scissor != null) {
            bounds = scissor.intersection(bounds);
            if (bounds == null) return; // fully clipped
        }
        g.guiRenderState.addGuiElement(new Mesh(
                RenderPipelines.GUI, TextureSetup.noTexture(), pose, xy, col, scissor, bounds));
    }

    private record Mesh(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose,
                        float[] xy, int[] color, ScreenRectangle scissorArea, ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer vc) {
            for (int i = 0; i < color.length; i++) {
                vc.addVertexWith2DPose(pose, xy[i * 2], xy[i * 2 + 1]).setColor(color[i]);
            }
        }
    }

    private GlShapes() {
    }
}
