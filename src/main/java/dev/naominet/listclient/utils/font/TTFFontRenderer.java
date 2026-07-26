package dev.naominet.listclient.utils.font;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TTF text renderer with CJK and color-emoji support.
 * <p>
 * Text is segmented into emoji-aware clusters (ZWJ sequences, variation
 * selectors, skin tones, regional-indicator flags, keycaps), each cluster is
 * shaped by AWT ({@code Font.layoutGlyphVector}, which applies GSUB – so
 * 👨‍👩‍👧 really is one glyph) and rasterized once into a texture atlas:
 * <ul>
 *   <li>emoji glyphs with COLR layers are filled per-layer with their CPAL
 *       palette colors via {@link ColrEmojiFont} → real color emoji;</li>
 *   <li>everything else is stored as a white mask and tinted with the text
 *       color at draw time.</li>
 * </ul>
 * Font fallback per cluster: user font {@code List/font.ttf} if present, else
 * the bundled MiSans ({@code assets/listclient/font/}, full CJK), then
 * Microsoft YaHei / Dialog; emoji clusters go to the system emoji font.
 * MiSans Medium backs the {@link #medium} weight used by the M3 type scale.
 * <p>
 * Glyphs are rasterized at {@code size * 2} px and drawn under a 0.5× pose
 * scale, so text stays sharp at GUI scale 2. All methods (including
 * {@link #width}) may rasterize and must be called on the render thread –
 * matching how {@code mc.font} is used in this codebase.
 * <p>
 * Coordinates follow {@code GuiGraphicsExtractor.text}: {@code y} is the top
 * of the line, colors are ARGB (alpha 0 treated as opaque).
 */
public class TTFFontRenderer {
    private static final int OVERSAMPLE = 2;
    private static final int PAGE_SIZE = 512;

    private static final int FAMILY_REGULAR = 0;
    private static final int FAMILY_MEDIUM = 1;
    private static final int FAMILY_ICON = 2;

    private record Key(int size, int family) {
    }

    private static final Map<Key, TTFFontRenderer> SHARED = new HashMap<>();
    private static Font primaryBase;
    private static Font mediumBase;
    private static Font iconBase;
    private static Font[] fallbackBases;
    private static ColrEmojiFont emoji;
    private static boolean fontsLoaded;

    /** Shared regular-weight renderer for a font size in GUI units (mc.font is ~9). */
    public static TTFFontRenderer get(int guiSize) {
        return SHARED.computeIfAbsent(new Key(guiSize, FAMILY_REGULAR),
                k -> new TTFFontRenderer(k.size, k.family));
    }

    /** Shared medium-weight renderer (falls back to regular if MiSans-Medium is missing). */
    public static TTFFontRenderer medium(int guiSize) {
        return SHARED.computeIfAbsent(new Key(guiSize, FAMILY_MEDIUM),
                k -> new TTFFontRenderer(k.size, k.family));
    }

    /**
     * Shared Material Icons renderer: draw an icon by its codepoint string
     * (see {@code ui/theme/Icons}); {@code guiSize} is the icon's optical size.
     */
    public static TTFFontRenderer icon(int guiSize) {
        return SHARED.computeIfAbsent(new Key(guiSize, FAMILY_ICON),
                k -> new TTFFontRenderer(k.size, k.family));
    }

    private final String atlasPrefix;
    private final Font primary;
    private final Font[] fallbacks;
    private final Font emojiFont;
    private final FontRenderContext frc = new FontRenderContext(null,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    private final Map<String, Glyph> glyphs = new HashMap<>();
    private final List<Page> pages = new ArrayList<>();
    private final float ascent;
    private final float lineHeight;

    private TTFFontRenderer(int guiSize, int family) {
        loadFonts();
        this.atlasPrefix = "ttf-atlas-" + family + "-" + guiSize + "-";
        float raster = guiSize * OVERSAMPLE;
        Font base = switch (family) {
            case FAMILY_MEDIUM -> mediumBase != null ? mediumBase : primaryBase;
            case FAMILY_ICON -> iconBase != null ? iconBase : primaryBase;
            default -> primaryBase;
        };
        this.primary = base.deriveFont(raster);
        this.fallbacks = new Font[fallbackBases.length];
        for (int i = 0; i < fallbackBases.length; i++) {
            this.fallbacks[i] = fallbackBases[i].deriveFont(raster);
        }
        this.emojiFont = emoji == null ? null : emoji.font.deriveFont(raster);
        LineMetrics lm = this.primary.getLineMetrics("Al你好", frc);
        this.ascent = lm.getAscent();
        this.lineHeight = lm.getAscent() + lm.getDescent();
    }

    private static synchronized void loadFonts() {
        if (fontsLoaded) return;
        fontsLoaded = true;
        Font base = null;
        File custom = new File("List/font.ttf");
        if (custom.isFile()) {
            try {
                base = Font.createFont(Font.TRUETYPE_FONT, custom);
            } catch (Exception ignored) {
            }
        }
        if (base == null) {
            base = fromResources("misans-regular.ttf");
        }
        if (base == null) {
            base = pickInstalled("Microsoft YaHei UI", "Microsoft YaHei",
                    "PingFang SC", "Noto Sans CJK SC", "WenQuanYi Micro Hei");
        }
        primaryBase = base;
        mediumBase = fromResources("misans-medium.ttf");
        iconBase = fromResources("material-icons.ttf");
        // YaHei fills CJK when a custom Latin font is primary; Dialog is the
        // logical composite and catches whatever is left.
        fallbackBases = new Font[]{
                new Font("Microsoft YaHei", Font.PLAIN, 12),
                new Font(Font.DIALOG, Font.PLAIN, 12),
        };
        emoji = ColrEmojiFont.load();
    }

    private static Font fromResources(String name) {
        try (java.io.InputStream in = TTFFontRenderer.class
                .getResourceAsStream("/assets/listclient/font/" + name)) {
            if (in == null) return null;
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            return null;
        }
    }

    private static Font pickInstalled(String... families) {
        for (String family : families) {
            Font f = new Font(family, Font.PLAIN, 12);
            if (!f.getFamily(Locale.ENGLISH).equalsIgnoreCase(Font.DIALOG)) {
                return f;
            }
        }
        return new Font(Font.DIALOG, Font.PLAIN, 12);
    }

    /* ================================================================== */
    /*  public API                                                        */
    /* ================================================================== */

    public float lineHeight() {
        return lineHeight / OVERSAMPLE;
    }

    public float width(String text) {
        if (text == null || text.isEmpty()) return 0;
        float w = 0;
        int i = 0;
        int len = text.length();
        while (i < len) {
            int end = clusterEnd(text, i);
            Glyph g = glyphFor(text.substring(i, end));
            if (g != null) w += g.advance;
            i = end;
        }
        return w / OVERSAMPLE;
    }

    /** Draws {@code text} with its top-left at (x, y); returns the end x. */
    public float drawString(GuiGraphicsExtractor g, String text, float x, float y, int argb) {
        return draw(g, text, x, y, argb, false);
    }

    public float drawStringWithShadow(GuiGraphicsExtractor g, String text, float x, float y, int argb) {
        if ((argb & 0xFF000000) == 0) argb |= 0xFF000000;
        int shadow = ((argb & 0xFCFCFC) >> 2) | (argb & 0xFF000000);
        draw(g, text, x + 1, y + 1, shadow, true);
        return draw(g, text, x, y, argb, false);
    }

    public void drawCenteredString(GuiGraphicsExtractor g, String text, float centerX, float y, int argb) {
        drawString(g, text, centerX - width(text) / 2f, y, argb);
    }

    /* ================================================================== */
    /*  drawing                                                           */
    /* ================================================================== */

    private float draw(GuiGraphicsExtractor g, String text, float x, float y, int argb, boolean shadowPass) {
        if (text == null || text.isEmpty()) return x;
        if ((argb & 0xFF000000) == 0) argb |= 0xFF000000;

        float penX = x * OVERSAMPLE;
        float baseY = y * OVERSAMPLE + ascent;

        g.pose().pushMatrix();
        g.pose().scale(1f / OVERSAMPLE, 1f / OVERSAMPLE);
        int i = 0;
        int len = text.length();
        while (i < len) {
            int end = clusterEnd(text, i);
            String cluster = text.substring(i, end);
            i = end;
            if (cluster.codePointAt(0) < 0x20) continue;

            Glyph glyph = glyphFor(cluster);
            if (glyph == null) continue;
            // Color emoji keep their own colors (only the alpha of the text color
            // applies) and cast no shadow – a dark emoji silhouette looks broken.
            if (glyph.page != null && !(shadowPass && glyph.colored)) {
                glyph.page.flush();
                int tint = glyph.colored ? (0xFFFFFF | (argb & 0xFF000000)) : argb;
                g.blit(RenderPipelines.GUI_TEXTURED, glyph.page.id,
                        Math.round(penX + glyph.xOff), Math.round(baseY + glyph.yOff),
                        glyph.u, glyph.v, glyph.w, glyph.h, PAGE_SIZE, PAGE_SIZE, tint);
            }
            penX += glyph.advance;
        }
        g.pose().popMatrix();
        return penX / OVERSAMPLE;
    }

    /* ================================================================== */
    /*  cluster segmentation                                              */
    /* ================================================================== */

    /**
     * End index of the emoji-aware cluster starting at {@code start}: base
     * codepoint plus variation selectors, skin tones, keycap, tag characters,
     * ZWJ-joined continuations, or a regional-indicator pair.
     */
    private static int clusterEnd(String s, int start) {
        int len = s.length();
        int cp = s.codePointAt(start);
        int i = start + Character.charCount(cp);
        if (isRegionalIndicator(cp)) {
            if (i < len) {
                int next = s.codePointAt(i);
                if (isRegionalIndicator(next)) return i + Character.charCount(next);
            }
            return i;
        }
        while (i < len) {
            int n = s.codePointAt(i);
            if (n == 0xFE0E || n == 0xFE0F || (n >= 0x1F3FB && n <= 0x1F3FF)
                    || n == 0x20E3 || (n >= 0xE0020 && n <= 0xE007F)) {
                i += Character.charCount(n);
            } else if (n == 0x200D && i + 1 < len) {
                int after = i + 1;
                int b = s.codePointAt(after);
                i = after + Character.charCount(b);
            } else {
                break;
            }
        }
        return i;
    }

    private static boolean isRegionalIndicator(int cp) {
        return cp >= 0x1F1E6 && cp <= 0x1F1FF;
    }

    private static boolean isEmojiBase(int cp) {
        return (cp >= 0x1F000 && cp <= 0x1FBFF)
                || (cp >= 0x2600 && cp <= 0x27BF)
                || (cp >= 0x2B00 && cp <= 0x2BFF);
    }

    private static boolean looksEmoji(String cluster) {
        if (cluster.indexOf(0xFE0E) >= 0) return false; // explicit text presentation
        if (isEmojiBase(cluster.codePointAt(0))) return true;
        return cluster.indexOf(0xFE0F) >= 0 || cluster.indexOf(0x200D) >= 0
                || cluster.indexOf(0x20E3) >= 0;
    }

    /* ================================================================== */
    /*  rasterization                                                     */
    /* ================================================================== */

    private Glyph glyphFor(String cluster) {
        Glyph glyph = glyphs.get(cluster);
        if (glyph == null) {
            try {
                glyph = rasterize(cluster);
            } catch (Exception e) {
                glyph = new Glyph(); // never take down the render thread over one glyph
            }
            glyphs.put(cluster, glyph);
        }
        return glyph;
    }

    private Glyph rasterize(String cluster) {
        boolean emojiish = looksEmoji(cluster);
        Font font = pick(cluster.codePointAt(0), emojiish);
        char[] cs = cluster.toCharArray();
        GlyphVector gv = font.layoutGlyphVector(frc, cs, 0, cs.length, Font.LAYOUT_LEFT_TO_RIGHT);
        int n = gv.getNumGlyphs();

        Glyph glyph = new Glyph();
        glyph.advance = (float) gv.getGlyphPosition(n).getX();

        List<Shape> shapes = null;
        List<Integer> colors = null;
        Rectangle bounds;
        if (emojiish && font == emojiFont && emoji != null && emoji.hasColr()) {
            shapes = new ArrayList<>();
            colors = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int[] layers = emoji.layers(gv.getGlyphCode(i));
                Point2D pos = gv.getGlyphPosition(i);
                if (layers != null) {
                    glyph.colored = true;
                    for (int j = 0; j < layers.length; j += 2) {
                        GlyphVector lv = emojiFont.createGlyphVector(frc, new int[]{layers[j]});
                        shapes.add(lv.getGlyphOutline(0, (float) pos.getX(), (float) pos.getY()));
                        colors.add(layers[j + 1]);
                    }
                } else {
                    shapes.add(gv.getGlyphOutline(i));
                    colors.add(0xFFFFFFFF);
                }
            }
            bounds = null;
            for (Shape s : shapes) {
                Rectangle b = s.getBounds();
                if (b.isEmpty()) continue;
                bounds = bounds == null ? b : bounds.union(b);
            }
            if (bounds != null) bounds.grow(2, 2);
        } else {
            bounds = gv.getPixelBounds(frc, 0, 0);
            if (!bounds.isEmpty()) bounds.grow(1, 1);
        }
        if (bounds == null || bounds.isEmpty()) {
            return glyph; // whitespace – advance only
        }
        int w = Math.min(bounds.width, PAGE_SIZE - 2);
        int h = Math.min(bounds.height, PAGE_SIZE - 2);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.translate(-bounds.x, -bounds.y);
        if (shapes != null) {
            for (int i = 0; i < shapes.size(); i++) {
                g2.setColor(new Color(colors.get(i), true));
                g2.fill(shapes.get(i));
            }
        } else {
            g2.setColor(Color.WHITE);
            g2.drawGlyphVector(gv, 0, 0);
        }
        g2.dispose();

        place(glyph, img);
        glyph.xOff = bounds.x;
        glyph.yOff = bounds.y;
        return glyph;
    }

    private Font pick(int baseCp, boolean emojiish) {
        if (emojiish && emojiFont != null) return emojiFont;
        if (primary.canDisplay(baseCp)) return primary;
        for (Font f : fallbacks) {
            if (f.canDisplay(baseCp)) return f;
        }
        if (emojiFont != null && emojiFont.canDisplay(baseCp)) return emojiFont;
        return primary; // .notdef box
    }

    /* ================================================================== */
    /*  atlas                                                             */
    /* ================================================================== */

    private void place(Glyph glyph, BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        Page page = null;
        for (Page p : pages) {
            if (p.reserve(w, h)) {
                page = p;
                break;
            }
        }
        if (page == null) {
            page = new Page(atlasPrefix + pages.size());
            pages.add(page);
            page.reserve(w, h);
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                page.img.setPixel(page.takenX + x, page.takenY + y, img.getRGB(x, y));
            }
        }
        page.dirty = true;
        glyph.page = page;
        glyph.u = page.takenX;
        glyph.v = page.takenY;
        glyph.w = w;
        glyph.h = h;
    }

    private static final class Glyph {
        Page page;
        int u, v, w, h;
        float xOff, yOff, advance;
        boolean colored;
    }

    /** One 512×512 atlas page with a simple shelf packer. */
    private static final class Page {
        final NativeImage img;
        final DynamicTexture tex;
        final Identifier id;
        int cursorX = 1;
        int cursorY = 1;
        int rowH;
        int takenX, takenY; // result of the last successful reserve()
        boolean dirty;

        Page(String name) {
            img = new NativeImage(PAGE_SIZE, PAGE_SIZE, true);
            tex = new DynamicTexture(null, img);
            id = Identifier.fromNamespaceAndPath("listclient", name);
            Minecraft.getInstance().getTextureManager().register(id, tex);
        }

        boolean reserve(int w, int h) {
            if (cursorX + w + 1 > PAGE_SIZE) {
                cursorX = 1;
                cursorY += rowH + 1;
                rowH = 0;
            }
            if (cursorY + h + 1 > PAGE_SIZE) {
                return false;
            }
            takenX = cursorX;
            takenY = cursorY;
            cursorX += w + 1;
            rowH = Math.max(rowH, h);
            return true;
        }

        void flush() {
            if (dirty) {
                tex.upload();
                dirty = false;
            }
        }
    }
}
