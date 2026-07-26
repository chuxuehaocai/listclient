package dev.naominet.listclient.ui.theme;

import dev.naominet.listclient.utils.font.TTFFontRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Material Icons for the M3 UI, drawn from the bundled
 * {@code assets/listclient/font/material-icons.ttf} through
 * {@link TTFFontRenderer#icon}. Each constant is the icon's codepoint
 * (explicit escapes, verified against the font's .codepoints table); draw them
 * like text, or use the centering helpers here. Icon glyphs are designed on a
 * square em – width ≈ optical size.
 */
public final class Icons {

    /* navigation / chrome */
    public static final String SETTINGS = "";
    public static final String TUNE = "";
    public static final String SEARCH = "";
    public static final String CLOSE = "";
    public static final String ARROW_BACK = "";
    public static final String EXPAND_MORE = "";
    public static final String EXPAND_LESS = "";
    public static final String CHECK = "";
    public static final String INFO = "";
    public static final String TRANSLATE = "";
    public static final String ACCESSIBILITY = "";
    public static final String POWER = "";
    public static final String REFRESH = "";
    public static final String KEYBOARD = "";
    public static final String DRAG_INDICATOR = "";

    /* menu destinations */
    public static final String PLAY_ARROW = "";
    public static final String PUBLIC = "";
    public static final String CLOUD = "";
    public static final String HOME = "";
    public static final String PERSON = "";
    public static final String WIDGETS = "";

    /* music */
    public static final String MUSIC_NOTE = "";
    public static final String PAUSE = "";
    public static final String SKIP_NEXT = "";
    public static final String SKIP_PREVIOUS = "";
    public static final String VOLUME_UP = "";
    public static final String SHUFFLE = "";
    public static final String REPEAT = "";
    public static final String REPEAT_ONE = "";
    public static final String RADIO = "";
    public static final String QUEUE_MUSIC = "";
    public static final String QR_CODE = "";
    public static final String LOGOUT = "";

    /* module categories */
    public static final String COMBAT = "";      // sports_esports
    public static final String MOVEMENT = "";    // directions_run
    public static final String RENDER = "";      // palette
    public static final String WORLD = "";       // public
    public static final String PLAYER = "";      // person
    public static final String MISC = "";        // category
    public static final String INVENTORY = "";   // inventory_2
    public static final String SPEED = "";
    public static final String VISIBILITY = "";
    public static final String BOLT = "";
    public static final String CHAT = "";
    public static final String BLOCK = "";

    /** Icon centered in a box – the usual way icons sit in buttons/rails. */
    public static void drawCentered(GuiGraphicsExtractor g, String icon, int size,
                                    float centerX, float centerY, int argb) {
        TTFFontRenderer font = TTFFontRenderer.icon(size);
        font.drawString(g, icon, centerX - font.width(icon) / 2f,
                centerY - font.lineHeight() / 2f, argb);
    }

    /** Icon drawn top-left anchored, matching drawString conventions. */
    public static void draw(GuiGraphicsExtractor g, String icon, int size,
                            float x, float y, int argb) {
        TTFFontRenderer.icon(size).drawString(g, icon, x, y, argb);
    }

    public static float width(String icon, int size) {
        return TTFFontRenderer.icon(size).width(icon);
    }

    private Icons() {
    }
}
