package dev.naominet.listclient.ui;

import dev.naominet.listclient.module.render.MusicPlayer;
import dev.naominet.listclient.module.render.MusicPlayer.Page;
import dev.naominet.listclient.ncmApi.NcmPlaylist;
import dev.naominet.listclient.ncmApi.NcmSong;
import dev.naominet.listclient.ui.theme.Icons;
import dev.naominet.listclient.ui.theme.M3;
import dev.naominet.listclient.ui.theme.MonetTheme;
import dev.naominet.listclient.ui.theme.Ripple;
import dev.naominet.listclient.utils.AnimationUtils;
import dev.naominet.listclient.utils.Lang;
import dev.naominet.listclient.utils.RenderUtils;
import dev.naominet.listclient.utils.font.TTFFontRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen (in-game overlay) UI for {@link MusicPlayer}.
 * All session/playback state lives on the module; this class only draws and
 * forwards input. Styled with the client's Material 3 theme ({@link M3}):
 * tonal surface-container ladder for depth, state layers for hover, and the
 * MiSans TTF type scale for every string.
 */
public class MusicPlayerScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 216;
    private static final int SIDEBAR_W = 54;
    private static final int HEADER_H = 16;
    private static final int PLAYER_H = 34;
    private static final int PAD = 6;
    private static final float LYRIC_ROW_PITCH = 22f;
    private static final int SEEK_X = 100;
    private static final int SEEK_W = PANEL_W - 180;
    private static final int VOLUME_X = PANEL_W - 60;
    private static final int VOLUME_W = 40;

    /** M3 type scale (MiSans) – every string on this screen goes through TTF. */
    private final TTFFontRenderer titleFont = M3.title();
    private final TTFFontRenderer bodyFont = M3.body();
    private final TTFFontRenderer lyricFont = TTFFontRenderer.medium(18);
    private final TTFFontRenderer labelFont = M3.label();
    private final TTFFontRenderer smallFont = M3.labelSmall();

    private final MusicPlayer mp;
    private final List<ClickZone> clickZones = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int mouseX;
    private int mouseY;

    /** Dragging the floating panel by its header. */
    private boolean draggingPanel;
    private TrackDrag trackDrag = TrackDrag.NONE;
    private int dragOffX;
    private int dragOffY;

    private enum TrackDrag {
        NONE, SEEK, VOLUME
    }

    /** Open animation: panel scales in from 92% over ~250ms after construction. */
    private final long openedAt = Util.getMillis();

    /** Page-switch slide: body content eases in from the right on page change. */
    private Page lastPage;
    private float slideT = 1f;

    public MusicPlayerScreen(MusicPlayer mp) {
        super(Component.literal("MusicPlayer"));
        this.mp = mp;
        this.lastPage = mp.page;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    protected void init() {
        // Center the panel the first time; keep position across reopen if already set.
        if (panelX == 0 && panelY == 0) {
            panelX = Math.max(8, (this.width - PANEL_W) / 2);
            panelY = Math.max(8, (this.height - PANEL_H) / 2);
        } else {
            panelX = clamp(panelX, 0, Math.max(0, this.width - PANEL_W));
            panelY = clamp(panelY, 0, Math.max(0, this.height - PANEL_H));
        }
    }

    @Override
    public void tick() {
        mp.tickQrPoll();
        mp.tickSearchDebounce();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Render thread, once per frame, BEFORE any M3 color is read: ease the
        // Monet seed toward the album-derived target and re-apply the scheme so
        // the whole panel morphs color while the screen is open.
        MonetTheme.update();
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        clickZones.clear();

        // Frosted glass: blur the world behind the panel, then dim it a bit.
        M3.blurBehind(g);
        extractTransparentBackground(g);

        int x = panelX;
        int y = panelY;

        // Scroll animates per FRAME (not per 20Hz tick): easeExp derives its
        // step from the real frame rate, so this is both smooth and correctly
        // timed. Exponential ease – fast start, smooth deceleration.
        mp.homeScroll = AnimationUtils.easeExp(mp.homeScroll, mp.homeScrollTarget, 12f);
        mp.listScroll = AnimationUtils.easeExp(mp.listScroll, mp.listScrollTarget, 12f);
        updateLyricMotion();

        // Page-switch slide: restart the ease whenever the page changes.
        if (mp.page != lastPage) {
            slideT = 0f;
            lastPage = mp.page;
        }
        slideT = AnimationUtils.easeExp(slideT, 1f, 5f);

        // Open animation: scale the whole panel in from 92% around its center
        // for the first ~250ms. Click zones stay unscaled – the window is tiny.
        float open = AnimationUtils.easeOutCubic((Util.getMillis() - openedAt) / 250f);
        boolean opening = open < 0.995f;
        if (opening) {
            float cx = x + PANEL_W / 2f;
            float cy = y + PANEL_H / 2f;
            float s = 0.92f + 0.08f * open;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(s, s);
            g.pose().translate(-cx, -cy);
        }
        try {
            // Floating surface over gameplay (busy background) – soft shadow, then a
            // rounded SURFACE_CONTAINER_LOW silhouette; header / sidebar / player bar
            // sit one container step above it (tonal elevation).
            M3.shadow(g, x, y, PANEL_W, PANEL_H, M3.SHAPE_L);
            M3.roundRect(g, x, y, PANEL_W, PANEL_H, M3.SHAPE_L, M3.SURFACE_CONTAINER_LOW);

            // Header rounded on top only, player bar on the bottom only.
            M3.roundRect(g, x, y, PANEL_W, HEADER_H, M3.SHAPE_L, M3.SURFACE_CONTAINER,
                    true, true, false, false);

            // Sidebar lives entirely between the rounded corners – a flat fill is safe.
            fill(g, x, y + HEADER_H, SIDEBAR_W, PANEL_H - HEADER_H - PLAYER_H, M3.SURFACE_CONTAINER);

            M3.roundRect(g, x, y + PANEL_H - PLAYER_H, PANEL_W, PLAYER_H, M3.SHAPE_L, M3.SURFACE_CONTAINER,
                    false, false, true, true);

            M3.divider(g, x, y + HEADER_H - 1, PANEL_W);
            fill(g, x + SIDEBAR_W, y + HEADER_H, 1, PANEL_H - HEADER_H - PLAYER_H, M3.OUTLINE_VARIANT);
            M3.divider(g, x, y + PANEL_H - PLAYER_H, PANEL_W);

            drawHeader(g, x, y);
            drawSidebar(g, x, y);

            // Clip page body so scrolled rows/cards cannot paint over header / player / sidebar.
            int bodyX = x + SIDEBAR_W + 1;
            int bodyY = y + HEADER_H;
            int bodyW = PANEL_W - SIDEBAR_W - 1;
            int bodyH = PANEL_H - HEADER_H - PLAYER_H;
            g.enableScissor(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH);
            // Click zones must clip like the pixels do, or scroll-hidden rows keep
            // stealing clicks from the header / player bar (hitTest is last-wins).
            pushZoneClip(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH);
            try {
                drawPage(g, x, y);
            } finally {
                popZoneClip();
                g.disableScissor();
            }

            // Chrome above the clipped body.
            drawPlayerBar(g, x, y);

            if (mp.errorText != null && !mp.errorText.isEmpty()) {
                String err = MusicPlayer.ellipsize(mp.errorText, 30);
                smallFont.drawString(g, err, bodyX + 4, bodyY + 2, M3.ERROR);
            }

            // Close hint – INSIDE the panel (bottom of the sidebar, above the
            // player bar); anything below the panel could be off-screen.
            String hint = Lang.tr("music.esc_close");
            smallFont.drawCenteredString(g, hint, x + SIDEBAR_W / 2f,
                    y + PANEL_H - PLAYER_H - 11, M3.ON_SURFACE_VARIANT);
        } finally {
            if (opening) {
                g.pose().popMatrix();
            }
        }
    }

    /* ================================================================== */
    /*  sections                                                          */
    /* ================================================================== */

    private void drawHeader(GuiGraphicsExtractor g, int x, int y) {
        titleFont.drawString(g, "MusicPlayer", x + 6,
                y + (HEADER_H - titleFont.lineHeight()) / 2f, M3.ON_SURFACE);

        // circular user avatar only (not album art)
        int avatarSize = 12;
        int avatarX = x + PANEL_W - avatarSize - 6;
        int avatarY = y + (HEADER_H - avatarSize) / 2;
        if (mp.user != null && mp.user.loggedIn) {
            Identifier avatar = mp.ensureImage(mp.user.avatarUrl, "avatar_" + mp.user.userId, true, true);
            if (avatar != null) {
                RenderUtils.drawCircularTexture(g, avatar, avatarX, avatarY, avatarSize);
            } else {
                M3.roundRect(g, avatarX, avatarY, avatarSize, avatarSize,
                        M3.pill(avatarSize), M3.SURFACE_CONTAINER_HIGHEST);
            }
        }

        String right;
        if (mp.user != null && mp.user.loggedIn) {
            right = mp.user.displayName();
            if (mp.user.vipType > 0) right += " VIP";
        } else {
            right = Lang.tr("music.not_logged_in");
        }
        int rw = (int) labelFont.width(right);
        int rightTextX = avatarX - rw - 4;
        boolean userHover = isMouseOver(rightTextX - 2, y, rw + avatarSize + 10, HEADER_H);
        int userColor = mp.user != null && mp.user.loggedIn
                ? M3.PRIMARY
                : (userHover ? M3.ON_SURFACE : M3.ON_SURFACE_VARIANT);
        int userX = rightTextX - 2;
        int userW = rw + avatarSize + 10;
        Ripple.draw(g, "header_user", userX, y, userW, HEADER_H, M3.ON_SURFACE);
        labelFont.drawString(g, right, rightTextX, y + (HEADER_H - labelFont.lineHeight()) / 2f, userColor);
        addClick(userX, y, userW, HEADER_H, "header_user");

        if (mp.statusText != null && !mp.statusText.isEmpty()) {
            smallFont.drawString(g, MusicPlayer.ellipsize(mp.statusText, 14), x + 74,
                    y + (HEADER_H - smallFont.lineHeight()) / 2f, M3.ON_SURFACE_VARIANT);
        }

        // whole header is a drag handle (except the user label which has its own click)
        addClick(x, y, Math.max(10, rightTextX - x - 4), HEADER_H, "drag_header");
    }

    private void drawSidebar(GuiGraphicsExtractor g, int x, int y) {
        int top = y + HEADER_H + 4;
        Page[] items = {Page.HOME, Page.SEARCH, Page.MINE, Page.FM, Page.LYRICS, Page.LOGIN};
        for (int i = 0; i < items.length; i++) {
            Page p = items[i];
            int iy = top + i * 22;
            boolean active = mp.page == p || (mp.page == Page.PLAYLIST && p == Page.HOME);
            boolean hover = isMouseOver(x, iy - 2, SIDEBAR_W, 18);
            if (active) {
                // M3 nav item: secondary-container pill, on-secondary-container label.
                M3.roundRect(g, x + 2, iy - 2, SIDEBAR_W - 4, 18, M3.pill(18), M3.SECONDARY_CONTAINER);
            } else if (hover) {
                M3.roundRect(g, x + 2, iy - 2, SIDEBAR_W - 4, 18, M3.pill(18),
                        M3.stateLayer(M3.ON_SURFACE, M3.STATE_HOVER));
            }
            Ripple.draw(g, "nav:" + p.name(), x + 2, iy - 2, SIDEBAR_W - 4, 18,
                    active ? M3.ON_SECONDARY_CONTAINER : M3.ON_SURFACE);
            int color = active ? M3.ON_SECONDARY_CONTAINER : M3.ON_SURFACE_VARIANT;
            String icon = navIcon(p);
            int iconSize = 9;
            float iconW = Icons.width(icon, iconSize);
            float gap = 3f;
            String label = p.label();
            float groupW = iconW + gap + labelFont.width(label);
            float startX = x + (SIDEBAR_W - groupW) / 2f;
            Icons.drawCentered(g, icon, iconSize, startX + iconW / 2f, iy - 2 + 9f, color);
            labelFont.drawString(g, label, startX + iconW + gap,
                    iy - 2 + (18 - labelFont.lineHeight()) / 2f, color);
            addClick(x, iy - 2, SIDEBAR_W, 18, "nav:" + p.name());
        }
    }

    /** Material icon for each sidebar destination. */
    private static String navIcon(Page p) {
        return switch (p) {
            case HOME -> Icons.HOME;
            case SEARCH -> Icons.SEARCH;
            case MINE -> Icons.PERSON;
            case FM -> Icons.RADIO;
            case LYRICS -> Icons.QUEUE_MUSIC;
            case LOGIN -> Icons.QR_CODE;
            default -> Icons.MUSIC_NOTE;
        };
    }

    private void drawPage(GuiGraphicsExtractor g, int x, int y) {
        int px = x + SIDEBAR_W + 1;
        int py = y + HEADER_H;
        int pw = PANEL_W - SIDEBAR_W - 1;
        int ph = PANEL_H - HEADER_H - PLAYER_H;
        // Page-switch slide: body content eases in from 14px right. Only the x
        // handed to the page bodies moves – the scissor and zone clip stay put,
        // so overflow keeps clipping and clicks stay honest.
        int sx = px + (int) ((1f - AnimationUtils.easeOutCubic(slideT)) * 24f);
        // Nested clip keeps section internals honest even if outer scissor is adjusted later.
        g.enableScissor(px, py, px + pw, py + ph);
        try {
            switch (mp.page) {
                case HOME -> drawHome(g, sx, py, pw, ph);
                case SEARCH -> drawSearch(g, sx, py, pw, ph);
                case MINE -> drawMine(g, sx, py, pw, ph);
                case FM -> drawFm(g, sx, py, pw, ph);
                case PLAYLIST -> drawPlaylistDetail(g, sx, py, pw, ph);
                case LYRICS -> drawLyrics(g, px, py, pw, ph);
                case LOGIN -> drawLogin(g, sx, py, pw, ph);
            }
        } finally {
            g.disableScissor();
        }
    }

    private void updateLyricMotion() {
        int active = mp.currentLyricIndex();
        if (active < 0) return;
        long now = Util.getMillis();
        if (!mp.lyricsFollowPlayback && now - mp.lyricsManualAt > 3500L) {
            mp.lyricsFollowPlayback = true;
        }
        float target = active * LYRIC_ROW_PITCH;
        if (!mp.lyricsFollowPlayback) {
            mp.lyricMotionAt = now;
            return;
        }
        float dt = mp.lyricMotionAt == 0L ? 1f / 60f
                : Math.min(0.05f, Math.max(0.001f, (now - mp.lyricMotionAt) / 1000f));
        mp.lyricMotionAt = now;
        float k = 1f - (float) Math.exp(-10f * dt);
        mp.lyricScroll += (target - mp.lyricScroll) * k;
        mp.lyricScrollVelocity = 0f;
        if (Math.abs(target - mp.lyricScroll) < 0.02f) mp.lyricScroll = target;
    }

    private void drawLyrics(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        M3.lyricBackground(g, x, y, w, h, 0, mp.currentSong != null);
        int backX = x + 7;
        int backY = y + 6;
        boolean backHover = isMouseOver(backX, backY, 34, 14);
        if (backHover) {
            M3.roundRect(g, backX, backY, 34, 14, M3.pill(14),
                    M3.stateLayer(M3.ON_SURFACE, M3.STATE_HOVER));
        }
        Ripple.draw(g, "lyrics_back", backX, backY, 34, 14, M3.ON_SURFACE);
        Icons.drawCentered(g, Icons.ARROW_BACK, 8, backX + 8, backY + 7, M3.ON_SURFACE);
        smallFont.drawString(g, Lang.tr("music.back"), backX + 14,
                backY + (14 - smallFont.lineHeight()) / 2f, M3.ON_SURFACE);
        addClick(backX, backY, 34, 14, "lyrics_back");
        int active = mp.currentLyricIndex();
        if (mp.currentSong == null) {
            bodyFont.drawCenteredString(g, Lang.tr("music.not_playing"), x + w / 2f,
                    y + h / 2f - bodyFont.lineHeight() / 2f, M3.ON_SURFACE_VARIANT);
            return;
        }
        if (mp.lyricsLoading) {
            bodyFont.drawCenteredString(g, Lang.tr("music.lyrics_loading"), x + w / 2f,
                    y + h / 2f - bodyFont.lineHeight() / 2f, M3.ON_SURFACE_VARIANT);
            return;
        }
        if (mp.lyrics == null || mp.lyrics.isEmpty()) {
            bodyFont.drawCenteredString(g, mp.lyricsError.isEmpty() ? Lang.tr("music.lyrics_empty") : mp.lyricsError,
                    x + w / 2f, y + h / 2f - bodyFont.lineHeight() / 2f, M3.ON_SURFACE_VARIANT);
            return;
        }

        float centerY = y + h * 0.46f;
        float visualIndex = mp.lyricScroll / LYRIC_ROW_PITCH;
        for (int i = 0; i < mp.lyrics.size(); i++) {
            float lineY = centerY + i * LYRIC_ROW_PITCH - mp.lyricScroll;
            if (lineY < y - 24 || lineY > y + h + 12) continue;
            float distance = Math.abs(i - visualIndex);
            float focus = active < 0 ? 0f : Math.max(0f, 1f - distance);
            float ambient = Math.max(0f, 1f - distance / 4f);
            float scale = (0.92f + focus * 0.16f + ambient * 0.03f) * 0.5f;
            int alpha = Math.max(32, (int) (130 * ambient + 125 * focus));
            int color = M3.lerp(M3.ON_SURFACE_VARIANT, M3.ON_SURFACE, focus);
            String text = trimToWidth(lyricFont, mp.lyrics.get(i).text,
                    Math.round((w - 28) / scale));
            float textX = x + 12 + (1f - ambient) * 5f;
            int rowY = (int) lineY - 9;
            String lyricKey = "lyric:" + i;
            if (isMouseOver(x + 6, rowY, w - 12, 18)) {
                M3.roundRect(g, x + 6, rowY, w - 12, 18, M3.SHAPE_S,
                        M3.stateLayer(M3.ON_SURFACE, M3.STATE_HOVER));
            }
            Ripple.draw(g, lyricKey, x + 6, rowY, w - 12, 18, M3.SHAPE_S, M3.ON_SURFACE);
            g.pose().pushMatrix();
            g.pose().translate(textX, lineY);
            g.pose().scale(scale, scale);
            lyricFont.drawString(g, text, 0, -lyricFont.lineHeight() / 2f, M3.withAlpha(color, alpha));
            g.pose().popMatrix();
            addClick(x + 6, rowY, w - 12, 18, lyricKey);
        }

        int fadeH = 18;
        for (int i = 0; i < fadeH; i++) {
            int a = (int) (130f * (1f - i / (float) fadeH));
            fill(g, x, y + i, w, 1, M3.withAlpha(M3.SURFACE_CONTAINER_LOWEST, a));
            fill(g, x, y + h - 1 - i, w, 1, M3.withAlpha(M3.SURFACE_CONTAINER_LOWEST, a));
        }
    }

    /* ---- home ---- */

    private void drawHome(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int contentTop = y + 4;
        int scroll = (int) mp.homeScroll;
        int cursor = contentTop - scroll;

        if (mp.user != null && mp.user.loggedIn) {
            cursor = drawSectionTitle(g, x, y, w, h, cursor, Lang.tr("music.daily"));
            cursor = drawSongRows(g, x, y, w, h, cursor, mp.dailySongs, "daily", 4);
        }

        cursor = drawSectionTitle(g, x, y, w, h, cursor, Lang.tr("music.rec_playlists"));
        cursor = drawPlaylistCards(g, x, y, w, h, cursor, mp.homePlaylists, "homepl");

        cursor = drawSectionTitle(g, x, y, w, h, cursor, Lang.tr("music.rec_songs"));
        cursor = drawSongRows(g, x, y, w, h, cursor, mp.newSongs, "newsong", 4);

        int contentH = cursor + scroll - contentTop + 12;
        int viewH = h - 8;
        mp.homeScrollTarget = MusicPlayer.clamp(mp.homeScrollTarget, 0, Math.max(0, contentH - viewH));

        if (contentH > viewH) {
            float ratio = viewH / (float) contentH;
            int barH = Math.max(12, (int) (viewH * ratio));
            int barY = y + 4 + (int) ((h - 8 - barH)
                    * (mp.homeScroll / Math.max(1f, contentH - viewH)));
            fill(g, x + w - 3, barY, 2, barH, M3.ON_SURFACE_VARIANT);
        }

        if (!mp.homeLoaded && !mp.busy) {
            bodyFont.drawCenteredString(g, Lang.tr("music.loading"), x + w / 2f, y + h / 2f, M3.ON_SURFACE_VARIANT);
        }
    }

    private int drawSectionTitle(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                 int cursor, String title) {
        if (cursor + 12 > y && cursor < y + h) {
            titleFont.drawString(g, title, x + PAD, cursor, M3.ON_SURFACE);
            int titleW = (int) titleFont.width(title);
            M3.divider(g, x + PAD + titleW + 4, cursor + (int) (titleFont.lineHeight() / 2f),
                    w - PAD * 2 - titleW - 4);
        }
        return cursor + 14;
    }

    private int drawSongRows(GuiGraphicsExtractor g, int x, int y, int w, int h,
                             int cursor, List<NcmSong> songs, String prefix, int max) {
        if (songs == null || songs.isEmpty()) {
            if (cursor + 12 > y && cursor < y + h) {
                smallFont.drawString(g, Lang.tr("music.no_data"), x + PAD + 4, cursor, M3.ON_SURFACE_VARIANT);
            }
            return cursor + 16;
        }
        int shown = Math.min(max, songs.size());
        for (int i = 0; i < shown; i++) {
            NcmSong s = songs.get(i);
            int rowY = cursor;
            int rowH = 16;
            // Only draw + fetch covers for rows that intersect the clipped viewport.
            if (rowY + rowH > y && rowY < y + h) {
                boolean hover = isMouseOver(x + PAD, rowY, w - PAD * 2, rowH);
                if (hover) {
                    M3.roundRect(g, x + PAD, rowY - 1, w - PAD * 2, rowH, M3.SHAPE_XS,
                            M3.stateLayer(M3.ON_SURFACE, M3.STATE_HOVER));
                }
                Ripple.draw(g, "play_song:" + prefix + ":" + i,
                        x + PAD, rowY - 1, w - PAD * 2, rowH, M3.SHAPE_XS, M3.ON_SURFACE);
                smallFont.drawString(g, String.valueOf(i + 1), x + PAD + 2,
                        rowY + (rowH - smallFont.lineHeight()) / 2f, M3.ON_SURFACE_VARIANT);
                Identifier cover = mp.ensureImage(s.coverUrl, prefix + "_c_" + s.id);
                if (cover != null) blit(g, cover, x + PAD + 14, rowY, 14, 14);
                else fill(g, x + PAD + 14, rowY, 14, 14, M3.SURFACE_CONTAINER_HIGH);
                bodyFont.drawString(g, MusicPlayer.ellipsize(s.name, 16), x + PAD + 32,
                        rowY + (rowH - bodyFont.lineHeight()) / 2f, M3.ON_SURFACE);
                String artist = MusicPlayer.ellipsize(s.artists, 12);
                smallFont.drawString(g, artist, x + w - PAD - smallFont.width(artist) - 28,
                        rowY + (rowH - smallFont.lineHeight()) / 2f, M3.ON_SURFACE_VARIANT);
                smallFont.drawString(g, s.durationText(), x + w - PAD - 24,
                        rowY + (rowH - smallFont.lineHeight()) / 2f, M3.ON_SURFACE_VARIANT);
                addClick(x + PAD, rowY - 1, w - PAD * 2, rowH, "play_song:" + prefix + ":" + i);
            }
            cursor += rowH + 1;
        }
        return cursor + 6;
    }

    private int drawPlaylistCards(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                  int cursor, List<NcmPlaylist> lists, String prefix) {
        if (lists == null || lists.isEmpty()) {
            if (cursor + 12 > y && cursor < y + h) {
                smallFont.drawString(g, Lang.tr("music.no_playlists"), x + PAD + 4, cursor, M3.ON_SURFACE_VARIANT);
            }
            return cursor + 16;
        }
        int cardW = 62;
        int cardH = 80; // cover (cardW-6) + name + count lines
        int gap = 6;
        int perRow = Math.max(1, (w - PAD * 2 + gap) / (cardW + gap));
        // One row of cards is enough for the home panel; avoids stampeding cover downloads.
        int max = Math.min(lists.size(), perRow);
        int rowStart = cursor;
        for (int i = 0; i < max; i++) {
            int col = i % perRow;
            int row = i / perRow;
            int cx = x + PAD + col * (cardW + gap);
            int cy = rowStart + row * (cardH + gap);
            if (cy + cardH > y && cy < y + h) {
                NcmPlaylist pl = lists.get(i);
                boolean hover = isMouseOver(cx, cy, cardW, cardH);
                int bg = hover
                        ? M3.layered(M3.SURFACE_CONTAINER_HIGH, M3.ON_SURFACE, M3.STATE_HOVER)
                        : M3.SURFACE_CONTAINER_HIGH;
                M3.roundRect(g, cx, cy, cardW, cardH, M3.SHAPE_M, bg);
                Ripple.draw(g, "open_pl:" + prefix + ":" + i,
                        cx, cy, cardW, cardH, M3.SHAPE_M, M3.ON_SURFACE);
                Identifier cover = mp.ensureImage(pl.coverUrl, prefix + "_pl_" + pl.id);
                if (cover != null) blit(g, cover, cx + 3, cy + 3, cardW - 6, cardW - 6);
                else fill(g, cx + 3, cy + 3, cardW - 6, cardW - 6, M3.SURFACE_CONTAINER_HIGHEST);
                labelFont.drawString(g, MusicPlayer.ellipsize(pl.name, 7), cx + 3,
                        cy + cardW - 3, M3.ON_SURFACE);
                smallFont.drawString(g, Lang.tr("music.plays_short", pl.shortPlayCount()), cx + 3,
                        cy + cardW + 8, M3.ON_SURFACE_VARIANT);
                addClick(cx, cy, cardW, cardH, "open_pl:" + prefix + ":" + i);
            }
        }
        int rows = Math.max(1, (max + perRow - 1) / perRow);
        return rowStart + rows * (cardH + gap) + 4;
    }

    /* ---- search ---- */

    private void drawSearch(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int boxY = y + 6;
        int boxH = 16;
        int boxW = w - PAD * 2 - 36;
        // Outlined text field: OUTLINE at rest, PRIMARY outline when focused.
        M3.outlinedRoundRect(g, x + PAD, boxY, boxW, boxH, M3.SHAPE_S,
                M3.SURFACE_CONTAINER_LOW, mp.searchFocused ? M3.PRIMARY : M3.OUTLINE);
        String shown = mp.searchQuery.isEmpty() && !mp.searchFocused
                ? Lang.tr("music.search_placeholder")
                : mp.searchQuery + (mp.searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        bodyFont.drawString(g, MusicPlayer.ellipsize(shown, 28), x + PAD + 4,
                boxY + (boxH - bodyFont.lineHeight()) / 2f,
                mp.searchQuery.isEmpty() && !mp.searchFocused ? M3.ON_SURFACE_VARIANT : M3.ON_SURFACE);
        addClick(x + PAD, boxY, boxW, boxH, "search_box");

        int btnW = 32;
        button(g, x + w - PAD - btnW, boxY, btnW, boxH, Lang.tr("music.search_go"), M3.PRIMARY, M3.ON_PRIMARY, "search_go");

        int listY = boxY + boxH + 6;
        if (mp.searchLoading) {
            smallFont.drawString(g, Lang.tr("music.searching"), x + PAD, listY, M3.ON_SURFACE_VARIANT);
            return;
        }
        if (mp.searchResults.isEmpty()) {
            smallFont.drawString(g, mp.searchQuery.isEmpty()
                            ? Lang.tr("music.search_hint") : Lang.tr("music.search_empty"),
                    x + PAD, listY, M3.ON_SURFACE_VARIANT);
            return;
        }
        int rowH = 16;
        int maxRows = Math.max(1, (h - (listY - y) - 4) / rowH);
        for (int i = 0; i < Math.min(maxRows, mp.searchResults.size()); i++) {
            NcmSong s = mp.searchResults.get(i);
            int ry = listY + i * rowH;
            boolean hover = isMouseOver(x + PAD, ry, w - PAD * 2, rowH);
            if (hover) {
                M3.roundRect(g, x + PAD, ry, w - PAD * 2, rowH, M3.SHAPE_XS,
                        M3.stateLayer(M3.ON_SURFACE, M3.STATE_HOVER));
            }
            Ripple.draw(g, "play_song:search:" + i, x + PAD, ry, w - PAD * 2, rowH,
                    M3.SHAPE_XS, M3.ON_SURFACE);
            smallFont.drawString(g, String.valueOf(i + 1), x + PAD + 2,
                    ry + (rowH - smallFont.lineHeight()) / 2f, M3.ON_SURFACE_VARIANT);
            Identifier cover = mp.ensureImage(s.coverUrl, "search_c_" + s.id);
            if (cover != null) blit(g, cover, x + PAD + 14, ry + 1, 12, 12);
            bodyFont.drawString(g, MusicPlayer.ellipsize(s.name, 14), x + PAD + 30,
                    ry + (rowH - bodyFont.lineHeight()) / 2f, M3.ON_SURFACE);
            smallFont.drawString(g, MusicPlayer.ellipsize(s.artists, 12), x + w / 2,
                    ry + (rowH - smallFont.lineHeight()) / 2f, M3.ON_SURFACE_VARIANT);
            smallFont.drawString(g, s.durationText(), x + w - PAD - 24,
                    ry + (rowH - smallFont.lineHeight()) / 2f, M3.ON_SURFACE_VARIANT);
            addClick(x + PAD, ry, w - PAD * 2, rowH, "play_song:search:" + i);
        }
    }

    /* ---- mine ---- */

    private void drawMine(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (mp.user == null || !mp.user.loggedIn) {
            bodyFont.drawString(g, Lang.tr("music.login_for_mine"), x + PAD + 8, y + 24, M3.ON_SURFACE_VARIANT);
            button(g, x + PAD + 8, y + 44, 72, 16, Lang.tr("music.go_login"), M3.PRIMARY, M3.ON_PRIMARY, "nav:LOGIN");
            return;
        }

        M3.roundRect(g, x + PAD, y + 6, w - PAD * 2, 36, M3.SHAPE_M, M3.SURFACE_CONTAINER_HIGH);
        Identifier avatar = mp.ensureImage(mp.user.avatarUrl, "avatar_" + mp.user.userId, true, true);
        if (avatar != null) {
            RenderUtils.drawCircularTexture(g, avatar, x + PAD + 4, y + 10, 28);
        } else {
            M3.roundRect(g, x + PAD + 4, y + 10, 28, 28, M3.pill(28), M3.SURFACE_CONTAINER_HIGHEST);
        }
        bodyFont.drawString(g, mp.user.displayName(), x + PAD + 38, y + 10, M3.ON_SURFACE);
        String sub = "Lv." + mp.user.level + (mp.user.vipType > 0 ? "  ·  VIP" : "")
                + "  ·  UID " + mp.user.userId;
        smallFont.drawString(g, sub, x + PAD + 38, y + 23, M3.ON_SURFACE_VARIANT);

        // Destructive button: error container pair.
        button(g, x + w - PAD - 40, y + 14, 36, 14, Lang.tr("music.logout"),
                M3.ERROR_CONTAINER, M3.ON_ERROR_CONTAINER, "logout");

        int cursor = y + 50;
        titleFont.drawString(g, Lang.tr("music.my_playlists"), x + PAD, cursor, M3.ON_SURFACE);
        cursor += 14;

        if (!mp.mineLoaded) {
            smallFont.drawString(g, Lang.tr("music.loading"), x + PAD, cursor, M3.ON_SURFACE_VARIANT);
            return;
        }
        if (mp.myPlaylists.isEmpty()) {
            smallFont.drawString(g, Lang.tr("music.no_playlists"), x + PAD, cursor, M3.ON_SURFACE_VARIANT);
            return;
        }

        int rowH = 18;
        int maxRows = Math.max(1, (y + h - cursor - 4) / rowH);
        for (int i = 0; i < Math.min(maxRows, mp.myPlaylists.size()); i++) {
            NcmPlaylist pl = mp.myPlaylists.get(i);
            int ry = cursor + i * rowH;
            boolean hover = isMouseOver(x + PAD, ry, w - PAD * 2, rowH);
            if (hover) {
                M3.roundRect(g, x + PAD, ry, w - PAD * 2, rowH, M3.SHAPE_XS,
                        M3.stateLayer(M3.ON_SURFACE, M3.STATE_HOVER));
            }
            Ripple.draw(g, "open_pl:mine:" + i, x + PAD, ry, w - PAD * 2, rowH,
                    M3.SHAPE_XS, M3.ON_SURFACE);
            Identifier cover = mp.ensureImage(pl.coverUrl, "mine_pl_" + pl.id);
            if (cover != null) blit(g, cover, x + PAD + 2, ry + 1, 14, 14);
            else fill(g, x + PAD + 2, ry + 1, 14, 14, M3.SURFACE_CONTAINER_HIGH);
            bodyFont.drawString(g, MusicPlayer.ellipsize(pl.name, 18), x + PAD + 20,
                    ry + (rowH - bodyFont.lineHeight()) / 2f, M3.ON_SURFACE);
            String meta = Lang.tr("music.songs_count", pl.trackCount);
            smallFont.drawString(g, meta, x + w - PAD - smallFont.width(meta) - 4,
                    ry + (rowH - smallFont.lineHeight()) / 2f, M3.ON_SURFACE_VARIANT);
            addClick(x + PAD, ry, w - PAD * 2, rowH, "open_pl:mine:" + i);
        }
    }

    /* ---- fm ---- */

    private void drawFm(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (mp.user == null || !mp.user.loggedIn) {
            bodyFont.drawString(g, Lang.tr("music.fm_needs_login"), x + PAD + 8, y + 30, M3.ON_SURFACE_VARIANT);
            button(g, x + PAD + 8, y + 50, 72, 16, Lang.tr("music.go_login"), M3.PRIMARY, M3.ON_PRIMARY, "nav:LOGIN");
            return;
        }

        titleFont.drawString(g, Lang.tr("music.page.fm"), x + PAD, y + 6, M3.ON_SURFACE);
        smallFont.drawString(g, Lang.tr("music.fm_desc"), x + PAD, y + 22, M3.ON_SURFACE_VARIANT);

        button(g, x + PAD, y + 36, 60, 16, Lang.tr("music.fm_refresh"),
                M3.SECONDARY_CONTAINER, M3.ON_SECONDARY_CONTAINER, "fm_refresh");

        if (mp.fmQueue.isEmpty()) {
            smallFont.drawString(g, Lang.tr("music.fm_hint"), x + PAD, y + 64, M3.ON_SURFACE_VARIANT);
            return;
        }

        int listY = y + 60;
        int rowH = 16;
        int maxRows = Math.max(1, (y + h - listY - 4) / rowH);
        for (int i = 0; i < Math.min(maxRows, mp.fmQueue.size()); i++) {
            NcmSong s = mp.fmQueue.get(i);
            int ry = listY + i * rowH;
            boolean active = mp.currentSong != null && mp.currentSong.id == s.id;
            boolean hover = isMouseOver(x + PAD, ry, w - PAD * 2, rowH);
            if (active) {
                M3.roundRect(g, x + PAD, ry, w - PAD * 2, rowH, M3.pill(rowH), M3.SECONDARY_CONTAINER);
            } else if (hover) {
                // State layer conforms to the component's shape (same pill as active).
                M3.roundRect(g, x + PAD, ry, w - PAD * 2, rowH, M3.pill(rowH),
                        M3.stateLayer(M3.ON_SURFACE, M3.STATE_HOVER));
            }
            Ripple.draw(g, "play_song:fm:" + i, x + PAD, ry, w - PAD * 2, rowH,
                    M3.pill(rowH), active ? M3.ON_SECONDARY_CONTAINER : M3.ON_SURFACE);
            bodyFont.drawString(g, MusicPlayer.ellipsize(s.titleLine(), 28), x + PAD + 4,
                    ry + (rowH - bodyFont.lineHeight()) / 2f,
                    active ? M3.ON_SECONDARY_CONTAINER : M3.ON_SURFACE);
            addClick(x + PAD, ry, w - PAD * 2, rowH, "play_song:fm:" + i);
        }
    }

    /* ---- playlist detail ---- */

    private void drawPlaylistDetail(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        // Text button: no container, primary label, hover state layer in primary.
        if (isMouseOver(x + PAD, y + 4, 28, 12)) {
            M3.roundRect(g, x + PAD, y + 4, 28, 12, M3.pill(12),
                    M3.stateLayer(M3.PRIMARY, M3.STATE_HOVER));
        }
        Ripple.draw(g, "pl_back", x + PAD, y + 4, 28, 12, M3.PRIMARY);
        Icons.draw(g, Icons.ARROW_BACK, 8, x + PAD + 2, y + 4 + (12 - 8) / 2f, M3.PRIMARY);
        smallFont.drawString(g, Lang.tr("music.back"), x + PAD + 2 + Icons.width(Icons.ARROW_BACK, 8) + 2,
                y + 4 + (12 - smallFont.lineHeight()) / 2f, M3.PRIMARY);
        addClick(x + PAD, y + 4, 28, 12, "pl_back");

        String title = mp.currentPlaylist == null ? Lang.tr("music.page.playlist") : mp.currentPlaylist.name;
        titleFont.drawString(g, MusicPlayer.ellipsize(title, 18), x + PAD + 34, y + 4, M3.ON_SURFACE);

        if (mp.currentPlaylist != null) {
            Identifier cover = mp.ensureImage(mp.currentPlaylist.coverUrl, "pldetail_" + mp.currentPlaylist.id);
            if (cover != null) blit(g, cover, x + w - PAD - 40, y + 4, 36, 36);
        }

        int listY = y + 44;
        if (mp.playlistTracks.isEmpty()) {
            smallFont.drawString(g, mp.playlistLoading ? Lang.tr("music.playlist_loading") : Lang.tr("music.playlist_empty"),
                    x + PAD, listY, M3.ON_SURFACE_VARIANT);
            return;
        }

        button(g, x + PAD, y + 22, 52, 14, Lang.tr("music.play_all"), M3.PRIMARY, M3.ON_PRIMARY, "pl_play_all");
        if (mp.playlistHasMore) {
            button(g, x + PAD + 58, y + 22, 52, 14,
                    mp.playlistLoading ? Lang.tr("music.loading_short") : Lang.tr("music.load_more"),
                    M3.SECONDARY_CONTAINER, M3.ON_SECONDARY_CONTAINER, "pl_load_more");
        }

        int rowH = 14;
        int scroll = (int) mp.listScroll;
        int viewH = y + h - listY - 2;
        int contentH = mp.playlistTracks.size() * rowH;
        mp.listScrollTarget = MusicPlayer.clamp(mp.listScrollTarget, 0, Math.max(0, contentH - viewH));

        // Rows clip (pixels AND click zones) at listY so partially scrolled rows
        // can't overdraw or steal clicks from the buttons above the list.
        g.enableScissor(x, listY, x + w, y + h);
        raiseZoneClipTop(listY);
        try {
            // Keep one buffered row around the viewport, but trigger prefetch only
            // from rows that are actually visible.
            int firstVisible = Math.max(0, scroll / rowH);
            int lastVisible = Math.min(mp.playlistTracks.size() - 1,
                    Math.max(firstVisible, (scroll + Math.max(0, viewH - 1)) / rowH));
            int first = Math.max(0, firstVisible - 1);
            int last = Math.min(mp.playlistTracks.size() - 1, lastVisible + 1);
            mp.observePlaylistVisibleLastIndex(lastVisible);
            for (int i = first; i <= last; i++) {
                int ry = listY + i * rowH - scroll;
                if (ry + rowH < listY || ry > y + h) continue;
                NcmSong s = mp.playlistTracks.get(i);
                boolean active = mp.currentSong != null && mp.currentSong.id == s.id;
                boolean hover = isMouseOver(x + PAD, ry, w - PAD * 2, rowH);
                if (active) {
                    M3.roundRect(g, x + PAD, ry, w - PAD * 2, rowH, M3.pill(rowH), M3.SECONDARY_CONTAINER);
                } else if (hover) {
                    M3.roundRect(g, x + PAD, ry, w - PAD * 2, rowH, M3.pill(rowH),
                            M3.stateLayer(M3.ON_SURFACE, M3.STATE_HOVER));
                }
                Ripple.draw(g, "play_song:pl:" + i, x + PAD, ry, w - PAD * 2, rowH,
                        M3.pill(rowH), active ? M3.ON_SECONDARY_CONTAINER : M3.ON_SURFACE);
                // Tonal pairing: every label on the secondary-container pill
                // uses its on-color, not just the song name.
                int metaColor = active ? M3.ON_SECONDARY_CONTAINER : M3.ON_SURFACE_VARIANT;
                smallFont.drawString(g, String.format("%02d", i + 1), x + PAD + 2,
                        ry + (rowH - smallFont.lineHeight()) / 2f, metaColor);
                bodyFont.drawString(g, MusicPlayer.ellipsize(s.name, 14), x + PAD + 22,
                        ry + (rowH - bodyFont.lineHeight()) / 2f,
                        active ? M3.ON_SECONDARY_CONTAINER : M3.ON_SURFACE);
                smallFont.drawString(g, MusicPlayer.ellipsize(s.artists, 12), x + w / 2 + 10,
                        ry + (rowH - smallFont.lineHeight()) / 2f, metaColor);
                smallFont.drawString(g, s.durationText(), x + w - PAD - 22,
                        ry + (rowH - smallFont.lineHeight()) / 2f, metaColor);
                addClick(x + PAD, ry, w - PAD * 2, rowH, "play_song:pl:" + i);
            }
        } finally {
            restoreZoneClipTop();
            g.disableScissor();
        }
    }

    /* ---- qr login ---- */

    private void drawLogin(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        titleFont.drawString(g, Lang.tr("music.qr_title"), x + PAD, y + 3, M3.ON_SURFACE);
        smallFont.drawString(g, Lang.tr("music.qr_desc"), x + PAD, y + 14,
                M3.ON_SURFACE_VARIANT);

        int qrSize = 92;
        int qx = x + (w - qrSize) / 2;
        int qy = y + 28;

        // Functional white backing plate – QR codes need it, keep it white.
        fill(g, qx - 4, qy - 4, qrSize + 8, qrSize + 8, 0xFFFFFFFF);
        if (mp.qrTexture != null && !mp.qrLoading) {
            blit(g, mp.qrTexture, qx, qy, qrSize, qrSize);
        } else {
            fill(g, qx, qy, qrSize, qrSize, 0xFFF0F0F0);
            String msg = mp.qrLoading ? Lang.tr("music.qr_generating") : Lang.tr("music.qr_none");
            bodyFont.drawCenteredString(g, msg, qx + qrSize / 2f,
                    qy + qrSize / 2f - bodyFont.lineHeight() / 2f, M3.INVERSE_ON_SURFACE);
        }

        if (mp.qrCode == 802) {
            fill(g, qx, qy, qrSize, qrSize, M3.withAlpha(M3.SCRIM, 140));
            bodyFont.drawCenteredString(g, Lang.tr("music.qr_waiting_confirm"), qx + qrSize / 2f,
                    qy + qrSize / 2f - bodyFont.lineHeight() / 2f, M3.ON_SURFACE);
            if (mp.qrNicknameHint != null && !mp.qrNicknameHint.isEmpty()) {
                String n = MusicPlayer.ellipsize(mp.qrNicknameHint, 12);
                smallFont.drawCenteredString(g, n, qx + qrSize / 2f,
                        qy + qrSize / 2f + 8, M3.PRIMARY);
            }
        } else if (mp.qrCode == 800) {
            fill(g, qx, qy, qrSize, qrSize, M3.withAlpha(M3.SCRIM, 160));
            bodyFont.drawCenteredString(g, Lang.tr("music.qr_expired"), qx + qrSize / 2f,
                    qy + qrSize / 2f - 12, M3.ON_SURFACE);
            smallFont.drawCenteredString(g, Lang.tr("music.qr_click_refresh"), qx + qrSize / 2f,
                    qy + qrSize / 2f + 4, M3.PRIMARY);
        }

        addClick(qx - 4, qy - 4, qrSize + 8, qrSize + 8, "qr_refresh");

        int statusColor = switch (mp.qrCode) {
            case 803 -> M3.PRIMARY;              // success
            case 802 -> M3.ON_SURFACE_VARIANT;   // waiting for confirm
            case 801 -> M3.ON_SURFACE_VARIANT;   // waiting for scan
            case 800 -> M3.ERROR;                // expired
            default -> M3.ON_SURFACE_VARIANT;
        };
        String stShow = MusicPlayer.ellipsize(mp.qrStatusText == null ? "" : mp.qrStatusText, 30);
        smallFont.drawCenteredString(g, stShow, x + w / 2f, qy + qrSize + 8, statusColor);

        smallFont.drawString(g, Lang.tr("music.qr_legend"),
                x + PAD, qy + qrSize + 18, M3.ON_SURFACE_VARIANT);

        int by = y + h - 18;
        button(g, x + PAD, by, 52, 14, Lang.tr("music.qr_refresh"),
                M3.SECONDARY_CONTAINER, M3.ON_SECONDARY_CONTAINER, "qr_refresh");

        if (mp.user != null && mp.user.loggedIn) {
            button(g, x + PAD + 60, by, 52, 14, Lang.tr("music.qr_enter"), M3.PRIMARY, M3.ON_PRIMARY, "nav:HOME");
        }
    }

    /* ---- player bar ---- */

    private void drawPlayerBar(GuiGraphicsExtractor g, int x, int y) {
        int px = x;
        int py = y + PANEL_H - PLAYER_H;

        // Album cover stays square; only avatars use the circle mask.
        int coverSize = 24;
        int cx = px + 5;
        int cy = py + (PLAYER_H - coverSize) / 2;
        Identifier cover = mp.currentSong == null ? null
                : mp.ensureImage(mp.currentSong.coverUrl, "now_cover_" + mp.currentSong.id, false, true);
        if (cover != null) {
            blit(g, cover, cx, cy, coverSize, coverSize);
        } else {
            fill(g, cx, cy, coverSize, coverSize, M3.SURFACE_CONTAINER_HIGH);
        }

        int textX = cx + coverSize + 5;
        int midX = px + PANEL_W / 2;
        int barX = px + SEEK_X;
        if (mp.currentSong != null) {
            // Pixel budget: the title must stop before the prev button (midX-40).
            String name = trimToWidth(titleFont, mp.currentSong.name, midX - 40 - 4 - textX);
            titleFont.drawString(g, name, textX, py + 4, M3.ON_SURFACE);
            String sub = mp.currentLyricLine();
            if (sub == null || sub.isEmpty()) sub = mp.currentSong.artists;
            // Pixel budget, not char count: the lyric must stop before the
            // elapsed-time label that right-aligns to barX - 4.
            float subMax = barX - 4 - smallFont.width("00:00") - 6 - textX;
            sub = trimToWidth(smallFont, sub, subMax);
            smallFont.drawString(g, sub, textX, py + 17, M3.ON_SURFACE_VARIANT);
        } else {
            bodyFont.drawString(g, Lang.tr("music.not_playing"), textX,
                    py + (PLAYER_H - bodyFont.lineHeight()) / 2f, M3.ON_SURFACE_VARIANT);
        }

        int btnY = py + 4;
        drawBtn(g, midX - 40, btnY, 14, 12, Icons.SKIP_PREVIOUS, "prev");
        drawBtn(g, midX - 12, btnY, 18, 12, mp.audio.isPlaying() ? Icons.PAUSE : Icons.PLAY_ARROW, "toggle");
        drawBtn(g, midX + 18, btnY, 14, 12, Icons.SKIP_NEXT, "next");

        int barW = SEEK_W;
        int barY = py + 24;
        int barH = isMouseOver(barX - 2, barY - 6, barW + 4, 14)
                || trackDrag == TrackDrag.SEEK ? 4 : 3;
        float prog = mp.audio.progress();
        M3.linearProgress(g, barX, barY, barW, barH, prog);
        if (trackDrag == TrackDrag.SEEK || isMouseOver(barX - 2, barY - 6, barW + 4, 14)) {
            int thumbX = barX + Math.round(barW * Math.max(0f, Math.min(1f, prog)));
            M3.roundRect(g, thumbX - 2, barY - 2, 4, barH + 4,
                    M3.pill(barH + 4), M3.PRIMARY);
        }
        addClick(barX - 2, barY - 7, barW + 4, 18, "seek");

        String tLeft = MusicPlayer.formatMs(mp.audio.positionMs());
        String tRight = mp.currentSong == null ? "0:00"
                : MusicPlayer.formatMs(Math.max(mp.audio.getDurationMs(), mp.currentSong.durationMs));
        smallFont.drawString(g, tLeft, barX - smallFont.width(tLeft) - 4, barY - 3,
                M3.ON_SURFACE_VARIANT);
        smallFont.drawString(g, tRight, barX + barW + 4, barY - 3, M3.ON_SURFACE_VARIANT);

        int vx = px + VOLUME_X;
        Icons.draw(g, Icons.VOLUME_UP, 8, vx - 16, py + 6, M3.ON_SURFACE_VARIANT);
        int volumeY = py + 10;
        int volumeH = isMouseOver(vx - 2, py + 3, VOLUME_W + 4, 15)
                || trackDrag == TrackDrag.VOLUME ? 4 : 3;
        M3.linearProgress(g, vx, volumeY, VOLUME_W, volumeH, mp.volume);
        if (trackDrag == TrackDrag.VOLUME || isMouseOver(vx - 2, py + 3, VOLUME_W + 4, 15)) {
            int thumbX = vx + Math.round(VOLUME_W * Math.max(0f, Math.min(1f, mp.volume)));
            M3.roundRect(g, thumbX - 2, volumeY - 2, 4, volumeH + 4,
                    M3.pill(volumeH + 4), M3.PRIMARY);
        }
        addClick(vx - 2, py + 3, VOLUME_W + 4, 15, "volume");

        int mx = px + PANEL_W - 48;
        String modeIcon = mp.repeatOne ? Icons.REPEAT_ONE : (mp.shuffle ? Icons.SHUFFLE : Icons.REPEAT);
        Ripple.draw(g, "mode", mx, py + 18, 24, 12, M3.ON_SURFACE);
        Icons.draw(g, modeIcon, 9, mx, py + 19,
                isMouseOver(mx, py + 18, 24, 12) ? M3.ON_SURFACE : M3.ON_SURFACE_VARIANT);
        addClick(mx, py + 18, 24, 12, "mode");

        if (mp.audio.isLoading()) {
            smallFont.drawString(g, Lang.tr("music.buffering"), midX + 36, py + 6, M3.ON_SURFACE_VARIANT);
        }
    }

    /** Transport button: tonal pill (secondary container + on-secondary-container). */
    private void drawBtn(GuiGraphicsExtractor g, int x, int y, int w, int h, String icon, String id) {
        boolean hover = isMouseOver(x, y, w, h);
        int bg = hover
                ? M3.layered(M3.SECONDARY_CONTAINER, M3.ON_SECONDARY_CONTAINER, M3.STATE_HOVER)
                : M3.SECONDARY_CONTAINER;
        M3.roundRect(g, x, y, w, h, M3.pill(h), bg);
        Ripple.draw(g, id, x, y, w, h, M3.ON_SECONDARY_CONTAINER);
        Icons.drawCentered(g, icon, 9, x + w / 2f, y + h / 2f, M3.ON_SECONDARY_CONTAINER);
        addClick(x, y, w, h, id);
    }

    /** Standard M3 button: pill container + label, with an 8% hover state layer. */
    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label,
                        int container, int onColor, String id) {
        boolean hover = isMouseOver(x, y, w, h);
        int bg = hover ? M3.layered(container, onColor, M3.STATE_HOVER) : container;
        M3.roundRect(g, x, y, w, h, M3.pill(h), bg);
        Ripple.draw(g, id, x, y, w, h, onColor);
        labelFont.drawCenteredString(g, label, x + w / 2f,
                y + (h - labelFont.lineHeight()) / 2f, onColor);
        addClick(x, y, w, h, id);
    }

    /* ================================================================== */
    /*  input                                                             */
    /* ================================================================== */

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();
        int button = event.button();

        if (button != 0 && button != 1) {
            return super.mouseClicked(event, doubleClick);
        }

        // Outside panel → close screen (module stays on)
        if (!isInsidePanel(mx, my)) {
            onClose();
            return true;
        }

        ClickZone hit = hitTest(mx, my);
        if (hit == null) {
            if (mp.page != Page.SEARCH) mp.searchFocused = false;
            return true;
        }

        if ("drag_header".equals(hit.id)) {
            draggingPanel = true;
            dragOffX = mx - panelX;
            dragOffY = my - panelY;
            return true;
        }
        if ("seek".equals(hit.id)) {
            if (button == 0) {
                trackDrag = TrackDrag.SEEK;
                updateSeek(mx);
            }
            return true;
        }
        if ("volume".equals(hit.id)) {
            if (button == 0) {
                trackDrag = TrackDrag.VOLUME;
                updateVolume(mx);
            }
            return true;
        }

        if (isButtonAction(hit.id) || hit.id.startsWith("play_song:")
                || hit.id.startsWith("open_pl:") || hit.id.startsWith("lyric:")) {
            Ripple.press(hit.id, mx, my);
        }
        mp.dispatchAction(hit.id, mx, panelX);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            draggingPanel = false;
            trackDrag = TrackDrag.NONE;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        int mx = (int) event.x();
        if (trackDrag == TrackDrag.SEEK) {
            updateSeek(mx);
            return true;
        }
        if (trackDrag == TrackDrag.VOLUME) {
            updateVolume(mx);
            return true;
        }
        if (draggingPanel) {
            panelX = clamp(mx - dragOffX, 0, Math.max(0, this.width - PANEL_W));
            panelY = clamp((int) event.y() - dragOffY, 0, Math.max(0, this.height - PANEL_H));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    private void updateSeek(int mouseX) {
        mp.seekByRatio((mouseX - (panelX + SEEK_X)) / (float) SEEK_W);
    }

    private void updateVolume(int mouseX) {
        mp.setVolumeByRatio((mouseX - (panelX + VOLUME_X)) / (float) VOLUME_W);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!isInsidePanel((int) mx, (int) my)) {
            return false;
        }
        float delta = (float) (-scrollY * 24);
        if (mp.page == Page.HOME) {
            mp.homeScrollTarget = Math.max(0, mp.homeScrollTarget + delta);
            return true;
        }
        if (mp.page == Page.PLAYLIST) {
            mp.listScrollTarget = Math.max(0, mp.listScrollTarget + delta);
            return true;
        }
        if (mp.page == Page.LYRICS) {
            mp.lyricsFollowPlayback = false;
            mp.lyricsManualAt = Util.getMillis();
            float maxScroll = Math.max(0f, (mp.lyrics.size() - 1) * LYRIC_ROW_PITCH);
            mp.lyricScroll = Math.max(0f, Math.min(maxScroll, mp.lyricScroll + delta));
            mp.lyricScrollVelocity = 0f;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }

        if (mp.searchFocused && mp.page == Page.SEARCH) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!mp.searchQuery.isEmpty()) {
                    mp.searchQuery = mp.searchQuery.substring(0, mp.searchQuery.length() - 1);
                    mp.onSearchTyped();
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                mp.runSearch(mp.searchQuery);
                return true;
            }
            if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL) {
                // allow ctrl combos to fall through? swallow for now
            }
            // Let charTyped handle printable chars; don't close on other keys.
            if (key != GLFW.GLFW_KEY_LEFT_SHIFT && key != GLFW.GLFW_KEY_RIGHT_SHIFT
                    && key != GLFW.GLFW_KEY_LEFT_ALT && key != GLFW.GLFW_KEY_RIGHT_ALT) {
                return true;
            }
        }

        // Global hotkeys while screen is open
        if (key == GLFW.GLFW_KEY_SPACE) {
            mp.audio.toggle();
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            mp.playRelative(-1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            mp.playRelative(1);
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (mp.searchFocused && mp.page == Page.SEARCH) {
            if (event.isAllowedChatCharacter()) {
                mp.searchQuery += event.codepointAsString();
                mp.onSearchTyped();
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        // Leave the module enabled so playback + mini HUD continue.
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(null);
        }
    }

    /* ================================================================== */
    /*  helpers                                                           */
    /* ================================================================== */

    private static boolean isButtonAction(String id) {
        return id != null && (id.startsWith("nav:")
                || id.equals("header_user") || id.equals("qr_refresh") || id.equals("logout")
                || id.equals("toggle") || id.equals("prev") || id.equals("next") || id.equals("mode")
                || id.equals("search_go") || id.equals("fm_refresh") || id.equals("pl_back")
                || id.equals("pl_play_all") || id.equals("pl_load_more") || id.equals("lyrics_back"));
    }

    private boolean isInsidePanel(int mx, int my) {
        return mx >= panelX && mx <= panelX + PANEL_W && my >= panelY && my <= panelY + PANEL_H;
    }

    private ClickZone hitTest(int mx, int my) {
        for (int i = clickZones.size() - 1; i >= 0; i--) {
            ClickZone z = clickZones.get(i);
            if (mx >= z.x && mx <= z.x + z.w && my >= z.y && my <= z.y + z.h) {
                return z;
            }
        }
        return null;
    }

    private boolean isMouseOver(int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    /** Active click-zone clip; mirrors the scissor so hidden content can't be hit. */
    private int zClipX0, zClipY0, zClipX1, zClipY1;
    private boolean zClipOn;
    private int zClipSavedY0;

    private void pushZoneClip(int x0, int y0, int x1, int y1) {
        zClipX0 = x0;
        zClipY0 = y0;
        zClipX1 = x1;
        zClipY1 = y1;
        zClipOn = true;
    }

    private void popZoneClip() {
        zClipOn = false;
    }

    /** Narrows only the top edge (list areas below in-body buttons). */
    private void raiseZoneClipTop(int y0) {
        zClipSavedY0 = zClipY0;
        zClipY0 = Math.max(zClipY0, y0);
    }

    private void restoreZoneClipTop() {
        zClipY0 = zClipSavedY0;
    }

    private void addClick(int x, int y, int w, int h, String id) {
        if (zClipOn) {
            int nx0 = Math.max(x, zClipX0);
            int ny0 = Math.max(y, zClipY0);
            int nx1 = Math.min(x + w, zClipX1);
            int ny1 = Math.min(y + h, zClipY1);
            if (nx1 <= nx0 || ny1 <= ny0) return;
            clickZones.add(new ClickZone(nx0, ny0, nx1 - nx0, ny1 - ny0, id));
            return;
        }
        clickZones.add(new ClickZone(x, y, w, h, id));
    }

    private void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, argb);
    }

    private void blit(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h) {
        RenderUtils.drawTexture(g, id, x, y, w, h);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Trims with a … suffix until the string fits {@code maxW} GUI px. */
    private static String trimToWidth(TTFFontRenderer font, String s, float maxW) {
        if (s == null || s.isEmpty() || font.width(s) <= maxW) return s == null ? "" : s;
        while (s.length() > 1 && font.width(s + "…") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    private static final class ClickZone {
        final int x, y, w, h;
        final String id;

        ClickZone(int x, int y, int w, int h, String id) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.id = id;
        }
    }
}
