package dev.naominet.listclient.utils.font;

import java.awt.Font;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

/**
 * Color-emoji source for {@link TTFFontRenderer}.
 * <p>
 * AWT on this JDK shapes emoji correctly (ZWJ ligatures, skin tones, flags all
 * come out of {@code Font.layoutGlyphVector} as the right glyph ids) but it
 * rasterizes COLR fonts as monochrome outlines. So this class parses the COLR
 * v0 layer records and CPAL palette straight out of the font file; the renderer
 * then fills each layer's outline with its palette color. The AWT {@link Font}
 * is created from the <em>same file</em> so glyph ids line up with the parsed
 * tables.
 * <p>
 * Windows 11's Segoe UI Emoji is a COLR v1 font but still carries the full v0
 * base/layer record set, which is all we read. If no COLR/CPAL data is found
 * the font stays usable and emoji simply render monochrome.
 */
public final class ColrEmojiFont {
    public final Font font;

    private ByteBuffer data;
    private int numBase;
    private int baseRecOff;  // absolute file offset of BaseGlyphRecords
    private int layerRecOff; // absolute file offset of LayerRecords
    private int colorsOff;   // absolute file offset of CPAL color records
    private int palette0;    // first color index of palette 0

    /** Tries {@code List/emoji.ttf} (user override) then the system emoji font. */
    public static ColrEmojiFont load() {
        String winDir = System.getenv("WINDIR");
        String[] candidates = {
                "List/emoji.ttf",
                winDir == null ? null : winDir + "\\Fonts\\seguiemj.ttf",
        };
        for (String path : candidates) {
            if (path == null) continue;
            File file = new File(path);
            if (!file.isFile()) continue;
            try {
                return new ColrEmojiFont(file);
            } catch (Exception ignored) {
                // unreadable / not a TTF – try the next candidate
            }
        }
        return null;
    }

    private ColrEmojiFont(File file) throws Exception {
        font = Font.createFont(Font.TRUETYPE_FONT, file);
        try {
            parse(ByteBuffer.wrap(Files.readAllBytes(file.toPath())).order(ByteOrder.BIG_ENDIAN));
        } catch (Exception e) {
            data = null; // keep the font, lose the color
        }
    }

    private void parse(ByteBuffer buf) {
        int numTables = buf.getShort(4) & 0xFFFF;
        int colr = -1;
        int cpal = -1;
        for (int i = 0; i < numTables; i++) {
            int rec = 12 + i * 16;
            int tag = buf.getInt(rec);
            if (tag == 0x434F4C52) colr = buf.getInt(rec + 8); // 'COLR'
            if (tag == 0x4350414C) cpal = buf.getInt(rec + 8); // 'CPAL'
        }
        if (colr < 0 || cpal < 0) {
            return;
        }
        // The v0 header layout is shared by v1 tables.
        numBase = buf.getShort(colr + 2) & 0xFFFF;
        baseRecOff = colr + buf.getInt(colr + 4);
        layerRecOff = colr + buf.getInt(colr + 8);
        colorsOff = cpal + buf.getInt(cpal + 8);
        palette0 = buf.getShort(cpal + 12) & 0xFFFF;
        if (numBase > 0) {
            data = buf;
        }
    }

    public boolean hasColr() {
        return data != null;
    }

    /**
     * COLR v0 layers of a base glyph, bottom-to-top, as packed pairs
     * {@code [layerGlyphId, argb, layerGlyphId, argb, ...]}; {@code null} if the
     * glyph has no color layers. Palette index 0xFFFF (foreground) maps to white.
     */
    public int[] layers(int glyphId) {
        if (data == null) return null;
        int lo = 0;
        int hi = numBase - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int rec = baseRecOff + mid * 6;
            int base = data.getShort(rec) & 0xFFFF;
            if (base < glyphId) {
                lo = mid + 1;
            } else if (base > glyphId) {
                hi = mid - 1;
            } else {
                int first = data.getShort(rec + 2) & 0xFFFF;
                int count = data.getShort(rec + 4) & 0xFFFF;
                int[] out = new int[count * 2];
                for (int i = 0; i < count; i++) {
                    int lr = layerRecOff + (first + i) * 4;
                    out[i * 2] = data.getShort(lr) & 0xFFFF;
                    int palIdx = data.getShort(lr + 2) & 0xFFFF;
                    out[i * 2 + 1] = palIdx == 0xFFFF ? 0xFFFFFFFF : readColor(palIdx);
                }
                return out;
            }
        }
        return null;
    }

    private int readColor(int index) {
        int off = colorsOff + (palette0 + index) * 4; // stored as BGRA
        int b = data.get(off) & 0xFF;
        int g = data.get(off + 1) & 0xFF;
        int r = data.get(off + 2) & 0xFF;
        int a = data.get(off + 3) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
