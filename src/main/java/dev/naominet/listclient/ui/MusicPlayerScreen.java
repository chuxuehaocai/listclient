package dev.naominet.listclient.ui;

import dev.naominet.listclient.module.render.MusicPlayer;
import dev.naominet.listclient.module.render.MusicPlayer.Page;
import dev.naominet.listclient.ncmApi.NcmBanner;
import dev.naominet.listclient.ncmApi.NcmPlaylist;
import dev.naominet.listclient.ncmApi.NcmSong;
import dev.naominet.listclient.utils.AnimationUtils;
import dev.naominet.listclient.utils.RenderUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen (in-game overlay) UI for {@link MusicPlayer}.
 * All session/playback state lives on the module; this class only draws and
 * forwards input.
 */
public class MusicPlayerScreen extends Screen {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 260;
    private static final int SIDEBAR_W = 64;
    private static final int HEADER_H = 16;
    private static final int PLAYER_H = 40;
    private static final int PAD = 6;

    private static final Color BG = new Color(18, 28, 38, 225);
    private static final Color BG_HEADER = new Color(22, 36, 48, 240);
    private static final Color BG_SIDEBAR = new Color(16, 28, 38, 240);
    private static final Color BG_PLAYER = new Color(20, 32, 44, 245);
    private static final Color BG_CARD = new Color(30, 48, 64, 215);
    private static final Color BG_CARD_HOVER = new Color(40, 70, 92, 235);
    /** Material Light Blue 400 */
    private static final Color ACCENT = MusicPlayer.ACCENT;
    private static final Color ACCENT_DIM = MusicPlayer.ACCENT_DIM;
    private static final Color TEXT = new Color(232, 245, 252);
    private static final Color TEXT_DIM = new Color(144, 164, 174);
    private static final Color TEXT_MUTED = new Color(96, 125, 139);
    private static final Color PROGRESS_BG = new Color(50, 70, 85, 200);
    private static final Color DIVIDER = new Color(41, 182, 246, 30);

    private final MusicPlayer mp;
    private final List<ClickZone> clickZones = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int mouseX;
    private int mouseY;

    /** Dragging the floating panel by its header. */
    private boolean draggingPanel;
    private int dragOffX;
    private int dragOffY;

    public MusicPlayerScreen(MusicPlayer mp) {
        super(Component.literal("MusicPlayer"));
        this.mp = mp;
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
        mp.tickBanner();
        mp.tickSearchDebounce();
        mp.homeScroll = AnimationUtils.animationNew(mp.homeScroll, mp.homeScrollTarget, 6f, 0.4f);
        mp.listScroll = AnimationUtils.animationNew(mp.listScroll, mp.listScrollTarget, 6f, 0.4f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        clickZones.clear();

        // Dim the world behind the panel a bit.
        extractTransparentBackground(g);

        int x = panelX;
        int y = panelY;

        RenderUtils.drawShadow(g, x, y, PANEL_W, PANEL_H);

        fill(g, x, y, PANEL_W, PANEL_H, BG);
        fill(g, x, y, PANEL_W, HEADER_H, BG_HEADER);
        fill(g, x, y + HEADER_H, SIDEBAR_W, PANEL_H - HEADER_H - PLAYER_H, BG_SIDEBAR);
        fill(g, x, y + PANEL_H - PLAYER_H, PANEL_W, PLAYER_H, BG_PLAYER);
        fill(g, x, y + HEADER_H - 1, PANEL_W, 1, DIVIDER);
        fill(g, x + SIDEBAR_W, y + HEADER_H, 1, PANEL_H - HEADER_H - PLAYER_H, DIVIDER);
        fill(g, x, y + PANEL_H - PLAYER_H, PANEL_W, 1, DIVIDER);

        drawHeader(g, x, y);
        drawSidebar(g, x, y);

        // Clip page body so scrolled rows/cards cannot paint over header / player / sidebar.
        int bodyX = x + SIDEBAR_W + 1;
        int bodyY = y + HEADER_H;
        int bodyW = PANEL_W - SIDEBAR_W - 1;
        int bodyH = PANEL_H - HEADER_H - PLAYER_H;
        g.enableScissor(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH);
        try {
            drawPage(g, x, y);
        } finally {
            g.disableScissor();
        }

        // Chrome above the clipped body.
        drawPlayerBar(g, x, y);

        if (mp.errorText != null && !mp.errorText.isEmpty()) {
            String err = MusicPlayer.ellipsize(mp.errorText, 40);
            g.text(font, err, bodyX + 4, bodyY + 2, new Color(255, 120, 120).getRGB());
        }

        // Close hint
        String hint = "ESC 关闭界面";
        g.text(font, hint, x + PANEL_W - font.width(hint) - 6, y + PANEL_H + 4, TEXT_MUTED.getRGB());
    }

    /* ================================================================== */
    /*  sections                                                          */
    /* ================================================================== */

    private void drawHeader(GuiGraphicsExtractor g, int x, int y) {
        g.text(font, "MusicPlayer", x + 6, y + 4, TEXT.getRGB());

        // circular user avatar only (not album art)
        int avatarSize = 12;
        int avatarX = x + PANEL_W - avatarSize - 6;
        int avatarY = y + (HEADER_H - avatarSize) / 2;
        if (mp.user != null && mp.user.loggedIn) {
            Identifier avatar = mp.ensureImage(mp.user.avatarUrl, "avatar_" + mp.user.userId, true, true);
            if (avatar != null) {
                RenderUtils.drawCircularTexture(g, avatar, avatarX, avatarY, avatarSize);
            } else {
                fill(g, avatarX, avatarY, avatarSize, avatarSize, ACCENT_DIM);
            }
        }

        String right;
        if (mp.user != null && mp.user.loggedIn) {
            right = mp.user.displayName();
            if (mp.user.vipType > 0) right += " VIP";
        } else {
            right = "未登录";
        }
        int rw = font.width(right);
        int rightTextX = avatarX - rw - 4;
        g.text(font, right, rightTextX, y + 4,
                mp.user != null && mp.user.loggedIn ? ACCENT.getRGB() : TEXT_DIM.getRGB());
        addClick(rightTextX - 2, y, rw + avatarSize + 10, HEADER_H, "header_user");

        if (mp.statusText != null && !mp.statusText.isEmpty()) {
            g.text(font, MusicPlayer.ellipsize(mp.statusText, 18), x + 80, y + 4, TEXT_MUTED.getRGB());
        }

        // whole header is a drag handle (except the user label which has its own click)
        addClick(x, y, Math.max(10, rightTextX - x - 4), HEADER_H, "drag_header");
    }

    private void drawSidebar(GuiGraphicsExtractor g, int x, int y) {
        int top = y + HEADER_H + 4;
        Page[] items = {Page.HOME, Page.SEARCH, Page.MINE, Page.FM, Page.LOGIN};
        for (int i = 0; i < items.length; i++) {
            Page p = items[i];
            int iy = top + i * 22;
            boolean active = mp.page == p || (mp.page == Page.PLAYLIST && p == Page.HOME);
            if (active) {
                fill(g, x + 2, iy - 2, SIDEBAR_W - 4, 18, MusicPlayer.ACCENT_SOFT);
                fill(g, x + 2, iy - 2, 2, 18, ACCENT);
            }
            int color = active ? ACCENT.getRGB() : TEXT_DIM.getRGB();
            int tw = font.width(p.label);
            g.text(font, p.label, x + (SIDEBAR_W - tw) / 2, iy + 2, color);
            addClick(x, iy - 2, SIDEBAR_W, 18, "nav:" + p.name());
        }
    }

    private void drawPage(GuiGraphicsExtractor g, int x, int y) {
        int px = x + SIDEBAR_W + 1;
        int py = y + HEADER_H;
        int pw = PANEL_W - SIDEBAR_W - 1;
        int ph = PANEL_H - HEADER_H - PLAYER_H;
        // Nested clip keeps section internals honest even if outer scissor is adjusted later.
        g.enableScissor(px, py, px + pw, py + ph);
        try {
            switch (mp.page) {
                case HOME -> drawHome(g, px, py, pw, ph);
                case SEARCH -> drawSearch(g, px, py, pw, ph);
                case MINE -> drawMine(g, px, py, pw, ph);
                case FM -> drawFm(g, px, py, pw, ph);
                case PLAYLIST -> drawPlaylistDetail(g, px, py, pw, ph);
                case LOGIN -> drawLogin(g, px, py, pw, ph);
            }
        } finally {
            g.disableScissor();
        }
    }

    /* ---- home ---- */

    private void drawHome(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int contentTop = y + 4;
        int scroll = (int) mp.homeScroll;

        int bannerH = 54;
        int bannerY = contentTop - scroll;
        if (bannerY + bannerH > y && bannerY < y + h) {
            fill(g, x + PAD, Math.max(bannerY, y), w - PAD * 2,
                    Math.min(bannerH, y + h - Math.max(bannerY, y)), BG_CARD);
            if (!mp.banners.isEmpty()) {
                NcmBanner b = mp.banners.get(Math.floorMod(mp.bannerIndex, mp.banners.size()));
                Identifier img = mp.ensureImage(b.imageUrl, "banner_" + mp.bannerIndex);
                if (img != null && bannerY >= y - 10) {
                    blit(g, img, x + PAD, bannerY, w - PAD * 2, bannerH);
                }
                String title = MusicPlayer.ellipsize(
                        b.title == null || b.title.isEmpty() ? b.typeTitle : b.title, 28);
                g.text(font, title, x + PAD + 4, bannerY + bannerH - 12, TEXT.getRGB());
                int dots = Math.min(mp.banners.size(), 8);
                for (int i = 0; i < dots; i++) {
                    int dx = x + w / 2 - dots * 4 + i * 8;
                    fill(g, dx, bannerY + 4, 4, 4,
                            i == Math.floorMod(mp.bannerIndex, mp.banners.size()) ? ACCENT : TEXT_MUTED);
                }
                addClick(x + PAD, bannerY, w - PAD * 2, bannerH, "banner:" + mp.bannerIndex);
            } else {
                g.text(font, mp.homeLoaded ? "暂无 Banner" : "加载首页中…",
                        x + PAD + 8, bannerY + 20, TEXT_DIM.getRGB());
            }
        }

        int cursor = contentTop + bannerH + 8 - scroll;

        if (mp.user != null && mp.user.loggedIn) {
            cursor = drawSectionTitle(g, x, y, w, h, cursor, "每日推荐");
            cursor = drawSongRows(g, x, y, w, h, cursor, mp.dailySongs, "daily", 4);
        }

        cursor = drawSectionTitle(g, x, y, w, h, cursor, "推荐歌单");
        cursor = drawPlaylistCards(g, x, y, w, h, cursor, mp.homePlaylists, "homepl");

        cursor = drawSectionTitle(g, x, y, w, h, cursor, "推荐新歌");
        cursor = drawSongRows(g, x, y, w, h, cursor, mp.newSongs, "newsong", 4);

        int contentH = cursor + scroll - contentTop + 12;
        int viewH = h - 8;
        mp.homeScrollTarget = MusicPlayer.clamp(mp.homeScrollTarget, 0, Math.max(0, contentH - viewH));

        if (contentH > viewH) {
            float ratio = viewH / (float) contentH;
            int barH = Math.max(12, (int) (viewH * ratio));
            int barY = y + 4 + (int) ((h - 8 - barH)
                    * (mp.homeScroll / Math.max(1f, contentH - viewH)));
            fill(g, x + w - 3, barY, 2, barH, ACCENT_DIM);
        }

        if (!mp.homeLoaded && !mp.busy) {
            g.text(font, "加载中…", x + w / 2 - 16, y + h / 2, TEXT_DIM.getRGB());
        }
    }

    private int drawSectionTitle(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                 int cursor, String title) {
        if (cursor + 12 > y && cursor < y + h) {
            g.text(font, title, x + PAD, cursor, TEXT.getRGB());
            fill(g, x + PAD + font.width(title) + 4, cursor + 5,
                    w - PAD * 2 - font.width(title) - 4, 1, DIVIDER);
        }
        return cursor + 14;
    }

    private int drawSongRows(GuiGraphicsExtractor g, int x, int y, int w, int h,
                             int cursor, List<NcmSong> songs, String prefix, int max) {
        if (songs == null || songs.isEmpty()) {
            if (cursor + 12 > y && cursor < y + h) {
                g.text(font, "暂无数据", x + PAD + 4, cursor, TEXT_MUTED.getRGB());
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
                if (hover) fill(g, x + PAD, rowY - 1, w - PAD * 2, rowH, BG_CARD_HOVER);
                g.text(font, String.valueOf(i + 1), x + PAD + 2, rowY + 2, TEXT_MUTED.getRGB());
                Identifier cover = mp.ensureImage(s.coverUrl, prefix + "_c_" + s.id);
                if (cover != null) blit(g, cover, x + PAD + 14, rowY, 14, 14);
                else fill(g, x + PAD + 14, rowY, 14, 14, BG_CARD);
                g.text(font, MusicPlayer.ellipsize(s.name, 22), x + PAD + 32, rowY + 2, TEXT.getRGB());
                String artist = MusicPlayer.ellipsize(s.artists, 16);
                g.text(font, artist, x + w - PAD - font.width(artist) - 28, rowY + 2, TEXT_DIM.getRGB());
                g.text(font, s.durationText(), x + w - PAD - 24, rowY + 2, TEXT_MUTED.getRGB());
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
                g.text(font, "暂无歌单", x + PAD + 4, cursor, TEXT_MUTED.getRGB());
            }
            return cursor + 16;
        }
        int cardW = 70;
        int cardH = 86;
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
                fill(g, cx, cy, cardW, cardH, hover ? BG_CARD_HOVER : BG_CARD);
                Identifier cover = mp.ensureImage(pl.coverUrl, prefix + "_pl_" + pl.id);
                if (cover != null) blit(g, cover, cx + 3, cy + 3, cardW - 6, cardW - 6);
                else fill(g, cx + 3, cy + 3, cardW - 6, cardW - 6, new Color(40, 55, 70));
                g.text(font, MusicPlayer.ellipsize(pl.name, 8), cx + 3, cy + cardW - 2, TEXT.getRGB());
                g.text(font, pl.shortPlayCount() + " 播", cx + 3, cy + cardW + 8, TEXT_MUTED.getRGB());
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
        fill(g, x + PAD, boxY, w - PAD * 2 - 36, boxH, BG_CARD);
        if (mp.searchFocused) {
            fill(g, x + PAD, boxY, w - PAD * 2 - 36, 1, ACCENT);
            fill(g, x + PAD, boxY + boxH - 1, w - PAD * 2 - 36, 1, ACCENT);
            fill(g, x + PAD, boxY, 1, boxH, ACCENT);
            fill(g, x + PAD + w - PAD * 2 - 37, boxY, 1, boxH, ACCENT);
        }
        String shown = mp.searchQuery.isEmpty() && !mp.searchFocused
                ? "输入关键字搜索歌曲…"
                : mp.searchQuery + (mp.searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        g.text(font, MusicPlayer.ellipsize(shown, 34), x + PAD + 4, boxY + 4,
                mp.searchQuery.isEmpty() && !mp.searchFocused ? TEXT_MUTED.getRGB() : TEXT.getRGB());
        addClick(x + PAD, boxY, w - PAD * 2 - 36, boxH, "search_box");

        int btnW = 32;
        fill(g, x + w - PAD - btnW, boxY, btnW, boxH, ACCENT);
        g.text(font, "搜索", x + w - PAD - btnW + 4, boxY + 4, Color.WHITE.getRGB());
        addClick(x + w - PAD - btnW, boxY, btnW, boxH, "search_go");

        int listY = boxY + boxH + 6;
        if (mp.searchLoading) {
            g.text(font, "搜索中…", x + PAD, listY, TEXT_DIM.getRGB());
            return;
        }
        if (mp.searchResults.isEmpty()) {
            g.text(font, mp.searchQuery.isEmpty() ? "试试搜索你喜欢的歌" : "没有结果",
                    x + PAD, listY, TEXT_MUTED.getRGB());
            return;
        }
        int rowH = 16;
        int maxRows = Math.max(1, (h - (listY - y) - 4) / rowH);
        for (int i = 0; i < Math.min(maxRows, mp.searchResults.size()); i++) {
            NcmSong s = mp.searchResults.get(i);
            int ry = listY + i * rowH;
            boolean hover = isMouseOver(x + PAD, ry, w - PAD * 2, rowH);
            if (hover) fill(g, x + PAD, ry, w - PAD * 2, rowH, BG_CARD_HOVER);
            g.text(font, String.valueOf(i + 1), x + PAD + 2, ry + 3, TEXT_MUTED.getRGB());
            Identifier cover = mp.ensureImage(s.coverUrl, "search_c_" + s.id);
            if (cover != null) blit(g, cover, x + PAD + 14, ry + 1, 12, 12);
            g.text(font, MusicPlayer.ellipsize(s.name, 20), x + PAD + 30, ry + 3, TEXT.getRGB());
            g.text(font, MusicPlayer.ellipsize(s.artists, 14), x + w / 2, ry + 3, TEXT_DIM.getRGB());
            g.text(font, s.durationText(), x + w - PAD - 24, ry + 3, TEXT_MUTED.getRGB());
            addClick(x + PAD, ry, w - PAD * 2, rowH, "play_song:search:" + i);
        }
    }

    /* ---- mine ---- */

    private void drawMine(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (mp.user == null || !mp.user.loggedIn) {
            g.text(font, "登录后查看我的歌单", x + PAD + 8, y + 24, TEXT_DIM.getRGB());
            fill(g, x + PAD + 8, y + 44, 72, 16, ACCENT);
            g.text(font, "去扫码登录", x + PAD + 14, y + 48, Color.WHITE.getRGB());
            addClick(x + PAD + 8, y + 44, 72, 16, "nav:LOGIN");
            return;
        }

        fill(g, x + PAD, y + 6, w - PAD * 2, 36, BG_CARD);
        Identifier avatar = mp.ensureImage(mp.user.avatarUrl, "avatar_" + mp.user.userId, true, true);
        if (avatar != null) {
            RenderUtils.drawCircularTexture(g, avatar, x + PAD + 4, y + 10, 28);
        } else {
            fill(g, x + PAD + 4, y + 10, 28, 28, new Color(40, 55, 70));
        }
        g.text(font, mp.user.displayName(), x + PAD + 38, y + 12, TEXT.getRGB());
        String sub = "Lv." + mp.user.level + (mp.user.vipType > 0 ? "  ·  VIP" : "")
                + "  ·  UID " + mp.user.userId;
        g.text(font, sub, x + PAD + 38, y + 24, TEXT_MUTED.getRGB());

        fill(g, x + w - PAD - 40, y + 14, 36, 14, new Color(60, 40, 40));
        g.text(font, "退出", x + w - PAD - 32, y + 17, new Color(255, 160, 160).getRGB());
        addClick(x + w - PAD - 40, y + 14, 36, 14, "logout");

        int cursor = y + 50;
        g.text(font, "我的歌单", x + PAD, cursor, TEXT.getRGB());
        cursor += 14;

        if (!mp.mineLoaded) {
            g.text(font, "加载中…", x + PAD, cursor, TEXT_DIM.getRGB());
            return;
        }
        if (mp.myPlaylists.isEmpty()) {
            g.text(font, "暂无歌单", x + PAD, cursor, TEXT_MUTED.getRGB());
            return;
        }

        int rowH = 18;
        int maxRows = Math.max(1, (y + h - cursor - 4) / rowH);
        for (int i = 0; i < Math.min(maxRows, mp.myPlaylists.size()); i++) {
            NcmPlaylist pl = mp.myPlaylists.get(i);
            int ry = cursor + i * rowH;
            boolean hover = isMouseOver(x + PAD, ry, w - PAD * 2, rowH);
            if (hover) fill(g, x + PAD, ry, w - PAD * 2, rowH, BG_CARD_HOVER);
            Identifier cover = mp.ensureImage(pl.coverUrl, "mine_pl_" + pl.id);
            if (cover != null) blit(g, cover, x + PAD + 2, ry + 1, 14, 14);
            else fill(g, x + PAD + 2, ry + 1, 14, 14, BG_CARD);
            g.text(font, MusicPlayer.ellipsize(pl.name, 22), x + PAD + 20, ry + 4, TEXT.getRGB());
            String meta = pl.trackCount + " 首";
            g.text(font, meta, x + w - PAD - font.width(meta) - 4, ry + 4, TEXT_MUTED.getRGB());
            addClick(x + PAD, ry, w - PAD * 2, rowH, "open_pl:mine:" + i);
        }
    }

    /* ---- fm ---- */

    private void drawFm(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (mp.user == null || !mp.user.loggedIn) {
            g.text(font, "私人 FM 需要登录", x + PAD + 8, y + 30, TEXT_DIM.getRGB());
            fill(g, x + PAD + 8, y + 50, 72, 16, ACCENT);
            g.text(font, "去扫码登录", x + PAD + 14, y + 54, Color.WHITE.getRGB());
            addClick(x + PAD + 8, y + 50, 72, 16, "nav:LOGIN");
            return;
        }

        g.text(font, "私人 FM", x + PAD, y + 8, TEXT.getRGB());
        g.text(font, "根据你的口味推荐", x + PAD, y + 20, TEXT_MUTED.getRGB());

        fill(g, x + PAD, y + 36, 60, 16, ACCENT);
        g.text(font, "刷新一批", x + PAD + 8, y + 40, Color.WHITE.getRGB());
        addClick(x + PAD, y + 36, 60, 16, "fm_refresh");

        if (mp.fmQueue.isEmpty()) {
            g.text(font, "点击刷新获取歌曲", x + PAD, y + 64, TEXT_DIM.getRGB());
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
            if (active) fill(g, x + PAD, ry, w - PAD * 2, rowH, MusicPlayer.ACCENT_SOFT);
            else if (hover) fill(g, x + PAD, ry, w - PAD * 2, rowH, BG_CARD_HOVER);
            g.text(font, MusicPlayer.ellipsize(s.titleLine(), 36), x + PAD + 4, ry + 3,
                    active ? ACCENT.getRGB() : TEXT.getRGB());
            addClick(x + PAD, ry, w - PAD * 2, rowH, "play_song:fm:" + i);
        }
    }

    /* ---- playlist detail ---- */

    private void drawPlaylistDetail(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        fill(g, x + PAD, y + 4, 28, 12, BG_CARD);
        g.text(font, "< 返回", x + PAD + 2, y + 6, TEXT_DIM.getRGB());
        addClick(x + PAD, y + 4, 28, 12, "pl_back");

        String title = mp.currentPlaylist == null ? "歌单" : mp.currentPlaylist.name;
        g.text(font, MusicPlayer.ellipsize(title, 24), x + PAD + 34, y + 6, TEXT.getRGB());

        if (mp.currentPlaylist != null) {
            Identifier cover = mp.ensureImage(mp.currentPlaylist.coverUrl, "pldetail_" + mp.currentPlaylist.id);
            if (cover != null) blit(g, cover, x + w - PAD - 40, y + 4, 36, 36);
        }

        int listY = y + 44;
        if (mp.playlistTracks.isEmpty()) {
            g.text(font, mp.busy ? "加载歌曲中…" : "空歌单", x + PAD, listY, TEXT_DIM.getRGB());
            return;
        }

        fill(g, x + PAD, y + 22, 52, 14, ACCENT);
        g.text(font, "播放全部", x + PAD + 4, y + 25, Color.WHITE.getRGB());
        addClick(x + PAD, y + 22, 52, 14, "pl_play_all");

        if (mp.playlistHasMore) {
            fill(g, x + PAD + 58, y + 22, 52, 14, BG_CARD);
            g.text(font, mp.busy ? "加载中" : "加载更多", x + PAD + 62, y + 25, TEXT.getRGB());
            addClick(x + PAD + 58, y + 22, 52, 14, "pl_load_more");
        }

        int rowH = 14;
        int scroll = (int) mp.listScroll;
        int viewH = y + h - listY - 2;
        int contentH = mp.playlistTracks.size() * rowH;
        mp.listScrollTarget = MusicPlayer.clamp(mp.listScrollTarget, 0, Math.max(0, contentH - viewH));

        // Only touch rows near the viewport (covers deferred until visible).
        int first = Math.max(0, scroll / rowH - 1);
        int last = Math.min(mp.playlistTracks.size() - 1, (scroll + viewH) / rowH + 1);
        for (int i = first; i <= last; i++) {
            int ry = listY + i * rowH - scroll;
            if (ry + rowH < listY || ry > y + h) continue;
            NcmSong s = mp.playlistTracks.get(i);
            boolean active = mp.currentSong != null && mp.currentSong.id == s.id;
            boolean hover = isMouseOver(x + PAD, ry, w - PAD * 2, rowH);
            if (active) fill(g, x + PAD, ry, w - PAD * 2, rowH, MusicPlayer.ACCENT_SOFT);
            else if (hover) fill(g, x + PAD, ry, w - PAD * 2, rowH, BG_CARD_HOVER);
            g.text(font, String.format("%02d", i + 1), x + PAD + 2, ry + 2, TEXT_MUTED.getRGB());
            g.text(font, MusicPlayer.ellipsize(s.name, 20), x + PAD + 22, ry + 2,
                    active ? ACCENT.getRGB() : TEXT.getRGB());
            g.text(font, MusicPlayer.ellipsize(s.artists, 12), x + w / 2 + 10, ry + 2, TEXT_DIM.getRGB());
            g.text(font, s.durationText(), x + w - PAD - 22, ry + 2, TEXT_MUTED.getRGB());
            addClick(x + PAD, ry, w - PAD * 2, rowH, "play_song:pl:" + i);
        }
    }

    /* ---- qr login ---- */

    private void drawLogin(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.text(font, "扫码登录网易云", x + PAD, y + 6, TEXT.getRGB());
        g.text(font, "打开网易云 App -> 左侧栏 -> 扫一扫", x + PAD, y + 18, TEXT_MUTED.getRGB());

        int qrSize = 110;
        int qx = x + (w - qrSize) / 2;
        int qy = y + 34;

        fill(g, qx - 4, qy - 4, qrSize + 8, qrSize + 8, Color.WHITE);
        if (mp.qrTexture != null && !mp.qrLoading) {
            blit(g, mp.qrTexture, qx, qy, qrSize, qrSize);
        } else {
            fill(g, qx, qy, qrSize, qrSize, new Color(240, 240, 240));
            String msg = mp.qrLoading ? "生成中…" : "无二维码";
            g.text(font, msg, qx + qrSize / 2 - font.width(msg) / 2,
                    qy + qrSize / 2 - 4, TEXT_MUTED.getRGB());
        }

        if (mp.qrCode == 802) {
            fill(g, qx, qy, qrSize, qrSize, new Color(0, 0, 0, 140));
            String t = "待确认";
            g.text(font, t, qx + qrSize / 2 - font.width(t) / 2,
                    qy + qrSize / 2 - 4, Color.WHITE.getRGB());
            if (mp.qrNicknameHint != null && !mp.qrNicknameHint.isEmpty()) {
                String n = MusicPlayer.ellipsize(mp.qrNicknameHint, 12);
                g.text(font, n, qx + qrSize / 2 - font.width(n) / 2,
                        qy + qrSize / 2 + 8, new Color(200, 255, 200).getRGB());
            }
        } else if (mp.qrCode == 800) {
            fill(g, qx, qy, qrSize, qrSize, new Color(0, 0, 0, 160));
            String t = "已过期";
            g.text(font, t, qx + qrSize / 2 - font.width(t) / 2,
                    qy + qrSize / 2 - 10, Color.WHITE.getRGB());
            String t2 = "点击刷新";
            g.text(font, t2, qx + qrSize / 2 - font.width(t2) / 2,
                    qy + qrSize / 2 + 4, ACCENT.getRGB());
        }

        addClick(qx - 4, qy - 4, qrSize + 8, qrSize + 8, "qr_refresh");

        int statusColor = switch (mp.qrCode) {
            case 803 -> new Color(80, 200, 120).getRGB();
            case 802 -> new Color(255, 200, 80).getRGB();
            case 801 -> TEXT_DIM.getRGB();
            case 800 -> ACCENT.getRGB();
            default -> TEXT_MUTED.getRGB();
        };
        String stShow = MusicPlayer.ellipsize(mp.qrStatusText == null ? "" : mp.qrStatusText, 34);
        g.text(font, stShow, x + w / 2 - font.width(stShow) / 2, qy + qrSize + 12, statusColor);

        g.text(font, "状态: 800过期  801等待  802待确认  803成功",
                x + PAD, qy + qrSize + 28, TEXT_MUTED.getRGB());

        int by = y + h - 22;
        fill(g, x + PAD, by, 52, 14, BG_CARD);
        g.text(font, "刷新二维码", x + PAD + 2, by + 3, TEXT.getRGB());
        addClick(x + PAD, by, 52, 14, "qr_refresh");

        if (mp.user != null && mp.user.loggedIn) {
            fill(g, x + PAD + 60, by, 52, 14, ACCENT);
            g.text(font, "进入首页", x + PAD + 68, by + 3, Color.WHITE.getRGB());
            addClick(x + PAD + 60, by, 52, 14, "nav:HOME");
        }
    }

    /* ---- player bar ---- */

    private void drawPlayerBar(GuiGraphicsExtractor g, int x, int y) {
        int px = x;
        int py = y + PANEL_H - PLAYER_H;

        // Album cover stays square; only avatars use the circle mask.
        int coverSize = 28;
        int cx = px + 6;
        int cy = py + (PLAYER_H - coverSize) / 2;
        Identifier cover = mp.currentSong == null ? null
                : mp.ensureImage(mp.currentSong.coverUrl, "now_cover_" + mp.currentSong.id, false, true);
        if (cover != null) {
            blit(g, cover, cx, cy, coverSize, coverSize);
        } else {
            fill(g, cx, cy, coverSize, coverSize, BG_CARD);
        }

        int textX = cx + coverSize + 6;
        if (mp.currentSong != null) {
            g.text(font, MusicPlayer.ellipsize(mp.currentSong.name, 16), textX, py + 6, TEXT.getRGB());
            String sub = mp.currentLyricLine();
            if (sub == null || sub.isEmpty()) sub = mp.currentSong.artists;
            g.pose().pushMatrix();
            g.pose().scale(0.8f, 0.8f);
            g.text(font, MusicPlayer.ellipsize(sub, 18), (int)(textX / 0.8f), (int)((py + 18) / 0.8f), TEXT_DIM.getRGB());
            g.pose().popMatrix();
        } else {
            g.text(font, "未在播放", textX, py + 10, TEXT_MUTED.getRGB());
        }

        int midX = px + PANEL_W / 2;
        int btnY = py + 6;
        drawBtn(g, midX - 40, btnY, 14, 12, "<<", "prev");
        drawBtn(g, midX - 12, btnY, 18, 12, mp.audio.isPlaying() ? "||" : ">", "toggle");
        drawBtn(g, midX + 18, btnY, 14, 12, ">>", "next");

        int barX = px + 110;
        int barW = PANEL_W - 200;
        int barY = py + 28;
        int barH = 4;
        fill(g, barX, barY, barW, barH, PROGRESS_BG);
        float prog = mp.audio.progress();
        int filled = (int) (barW * prog);
        if (filled > 0) fill(g, barX, barY, filled, barH, ACCENT);
        fill(g, barX + Math.max(0, filled - 1), barY - 2, 4, barH + 4, Color.WHITE);
        addClick(barX - 2, barY - 4, barW + 4, barH + 8, "seek");

        String tLeft = MusicPlayer.formatMs(mp.audio.positionMs());
        String tRight = mp.currentSong == null ? "0:00"
                : MusicPlayer.formatMs(Math.max(mp.audio.getDurationMs(), mp.currentSong.durationMs));
        g.text(font, tLeft, barX - font.width(tLeft) - 4, barY - 2, TEXT_MUTED.getRGB());
        g.text(font, tRight, barX + barW + 4, barY - 2, TEXT_MUTED.getRGB());

        int vx = px + PANEL_W - 70;
        g.text(font, "VOL", vx - 18, py + 8, TEXT_MUTED.getRGB());
        fill(g, vx, py + 12, 50, 3, PROGRESS_BG);
        fill(g, vx, py + 12, (int) (50 * mp.volume), 3, TEXT_DIM);
        addClick(vx - 2, py + 8, 54, 12, "volume");

        int mx = px + PANEL_W - 60;
        g.text(font, mp.repeatOne ? "[单曲]" : (mp.shuffle ? "[随机]" : "[顺序]"),
                mx, py + 22, TEXT_MUTED.getRGB());
        addClick(mx, py + 20, 40, 12, "mode");

        if (mp.audio.isLoading()) {
            g.text(font, "缓冲…", midX - 12, py + 22, TEXT_DIM.getRGB());
        }
    }

    private void drawBtn(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, String id) {
        boolean hover = isMouseOver(x, y, w, h);
        fill(g, x, y, w, h, hover ? BG_CARD_HOVER : BG_CARD);
        int tw = font.width(label);
        g.text(font, label, x + (w - tw) / 2, y + 2, TEXT.getRGB());
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
            int barX = panelX + 110;
            int barW = PANEL_W - 200;
            mp.seekByRatio((mx - barX) / (float) barW);
            return true;
        }
        if ("volume".equals(hit.id)) {
            int vx = panelX + PANEL_W - 70;
            mp.setVolumeByRatio((mx - vx) / 50f);
            return true;
        }

        mp.dispatchAction(hit.id, mx, panelX);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingPanel = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingPanel) {
            panelX = clamp((int) event.x() - dragOffX, 0, Math.max(0, this.width - PANEL_W));
            panelY = clamp((int) event.y() - dragOffY, 0, Math.max(0, this.height - PANEL_H));
            return true;
        }
        // scrub while dragging on seek / volume
        int mx = (int) event.x();
        int my = (int) event.y();
        ClickZone hit = hitTest(mx, my);
        if (hit != null) {
            if ("seek".equals(hit.id)) {
                int barX = panelX + 110;
                int barW = PANEL_W - 200;
                mp.seekByRatio((mx - barX) / (float) barW);
                return true;
            }
            if ("volume".equals(hit.id)) {
                int vx = panelX + PANEL_W - 70;
                mp.setVolumeByRatio((mx - vx) / 50f);
                return true;
            }
        }
        return super.mouseDragged(event, dx, dy);
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

    private void addClick(int x, int y, int w, int h, String id) {
        clickZones.add(new ClickZone(x, y, w, h, id));
    }

    private void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, Color c) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, c.getRGB());
    }

    private void blit(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h) {
        RenderUtils.drawTexture(g, id, x, y, w, h);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
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
