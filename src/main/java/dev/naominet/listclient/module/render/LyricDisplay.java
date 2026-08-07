package dev.naominet.listclient.module.render;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventRender2D;
import dev.naominet.listclient.manager.ModuleManager;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.ui.theme.M3;
import dev.naominet.listclient.utils.font.TTFFontRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draggable Apple Music-inspired now-playing lyric HUD. */
public class LyricDisplay extends Module {

    private static final int WIDTH = 220;
    private static final int HEIGHT = 54;
    private static final float LINE_GAP = 16f;
    private static final float TEXT_SCALE = 0.5f;

    private final TTFFontRenderer lyricFont = TTFFontRenderer.medium(18);
    private final TTFFontRenderer fallbackFont = TTFFontRenderer.get(16);
    private float visualIndex = -1f;
    private long motionAt;

    public LyricDisplay() {
        super("LyricDisplay", Category.Render);
        setXYWH(8, 48, WIDTH, HEIGHT);
    }

    @EventTarget
    public void onRender2D(EventRender2D event) {
        MusicPlayer player = ModuleManager.instance.getModuleByClazz(MusicPlayer.class);
        if (player == null) return;

        int x = (int) getX();
        int y = (int) getY();
        setXYWH(x, y, WIDTH, HEIGHT);
//        M3.shadowSoft(event.getExtractor(), x, y, WIDTH, HEIGHT, M3.SHAPE_M);
//        M3.lyricBackground(event.getExtractor(), x, y, WIDTH, HEIGHT,
//                player.currentSong != null);

        int active = player.currentLyricIndex();
        if (active < 0 || player.lyrics.isEmpty()) {
            visualIndex = -1f;
            motionAt = 0L;
            String fallback = player.currentSong == null ? "Music" : player.currentSong.name;
            fallback = fit(fallback, WIDTH - 22, fallbackFont, TEXT_SCALE);
            event.getExtractor().pose().pushMatrix();
            event.getExtractor().pose().translate(x + WIDTH / 2f, y + HEIGHT / 2f);
            event.getExtractor().pose().scale(TEXT_SCALE, TEXT_SCALE);
            fallbackFont.drawCenteredString(event.getExtractor(), fallback, 0,
                    -fallbackFont.lineHeight() / 2f, M3.ON_SURFACE_VARIANT);
            event.getExtractor().pose().popMatrix();
            return;
        }

        long now = net.minecraft.util.Util.getMillis();
        float dt = motionAt == 0L ? 1f / 60f
                : Math.clamp((now - motionAt) / 1000f, 0.001f, 0.05f);
        motionAt = now;
        if (visualIndex < 0f || Math.abs(active - visualIndex) > 2f) {
            visualIndex = active;
        } else {
            float k = 1f - (float) Math.exp(-11f * dt);
            visualIndex += (active - visualIndex) * k;
            if (Math.abs(active - visualIndex) < 0.002f) visualIndex = active;
        }

        float centerY = y + HEIGHT / 2f;
        int first = Math.max(0, active - 1);
        int last = Math.min(player.lyrics.size() - 1, active + 1);
        GuiGraphicsExtractor g = event.getExtractor();
        g.enableScissor(x, y, x + WIDTH, y + HEIGHT);
        try {
            for (int i = first; i <= last; i++) {
                float offset = i - visualIndex;
                float focus = Math.max(0f, 1f - Math.abs(offset));
                drawLine(g, player, i, x, centerY + offset * LINE_GAP, focus);
            }
        } finally {
            g.disableScissor();
        }
    }

    private void drawLine(GuiGraphicsExtractor g, MusicPlayer player, int index, int x,
                          float centerY, float focus) {
        float scale = (0.90f + focus * 0.10f) * TEXT_SCALE;
        String text = fit(player.lyrics.get(index).text, WIDTH - 22, lyricFont, scale);
        float alpha = 0.28f + focus * 0.72f;
        int color = M3.lerp(M3.ON_SURFACE_VARIANT, M3.ON_SURFACE, focus);
        g.pose().pushMatrix();
        g.pose().translate(x + WIDTH / 2f, centerY);
        g.pose().scale(scale, scale);
        lyricFont.drawCenteredString(g, text, 0, -lyricFont.lineHeight() / 2f, M3.fade(color, alpha));
        g.pose().popMatrix();
    }

    private static String fit(String text, int maxWidth, TTFFontRenderer font, float scale) {
        if (text == null) return "";
        float unscaledWidth = maxWidth / Math.max(0.01f, scale);
        if (font.width(text) <= unscaledWidth) return text;
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + ellipsis) > unscaledWidth) {
            int codePoint = text.codePointBefore(end);
            end -= Character.charCount(codePoint);
        }
        return text.substring(0, end) + ellipsis;
    }
}
