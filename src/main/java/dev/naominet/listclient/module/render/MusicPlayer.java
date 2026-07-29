package dev.naominet.listclient.module.render;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.naominet.listclient.core.ListClient;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.value.Option;
import dev.naominet.listclient.ncmApi.NCMAPI;
import dev.naominet.listclient.ncmApi.NcmBanner;
import dev.naominet.listclient.ncmApi.NcmLyricLine;
import dev.naominet.listclient.ncmApi.NcmPlaylist;
import dev.naominet.listclient.ncmApi.NcmSong;
import dev.naominet.listclient.ncmApi.NcmUser;
import dev.naominet.listclient.ui.MusicPlayerScreen;
import dev.naominet.listclient.ui.theme.Icons;
import dev.naominet.listclient.ui.theme.M3;
import dev.naominet.listclient.ui.theme.MonetColor;
import dev.naominet.listclient.ui.theme.MonetTheme;
import dev.naominet.listclient.ui.theme.Ripple;
import dev.naominet.listclient.utils.DynamicImageUtils;
import dev.naominet.listclient.utils.Lang;
import dev.naominet.listclient.utils.MouseData;
import dev.naominet.listclient.utils.NcmAudioPlayer;
import dev.naominet.listclient.utils.Pair;
import dev.naominet.listclient.utils.RenderUtils;
import dev.naominet.listclient.utils.font.TTFFontRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * NetEase Cloud Music entry module.
 * <p>
 * Holds session / playback state and the draggable mini-widget bounds
 * ({@link #setXYWH}). Enabling (default key M) only opens
 * {@link MusicPlayerScreen}. The mini player HUD is drawn by
 * {@link Interface}, not by this module's render hook.
 * <p>
 * Audio goes through the process-wide {@link NcmAudioPlayer#INSTANCE}
 * so only one track can ever play.
 */
public class MusicPlayer extends Module {

    public static MusicPlayer instance;

    /** Material Light Blue 400 */
    public static final Color ACCENT = new Color(0x29, 0xB6, 0xF6);
    public static final Color ACCENT_DARK = new Color(0x02, 0x88, 0xD1);
    public static final Color ACCENT_DIM = new Color(0x29, 0xB6, 0xF6, 120);
    public static final Color ACCENT_SOFT = new Color(0x29, 0xB6, 0xF6, 40);

    public static final int WIDGET_W = 196;
    public static final int WIDGET_H = 36;

    public enum Page {
        HOME("music.page.home"),
        SEARCH("music.page.search"),
        MINE("music.page.mine"),
        FM("music.page.fm"),
        PLAYLIST("music.page.playlist"),
        LYRICS("music.page.lyrics"),
        LOGIN("music.page.login");

        /** Translation key – resolve via {@link #label()} at draw time. */
        public final String key;

        Page(String key) {
            this.key = key;
        }

        /** Translated label; resolved on every call so language switches apply live. */
        public String label() {
            return Lang.tr(key);
        }
    }

    /* ---- shared audio / session ---- */
    /** Always the process-wide singleton – never construct another player. */
    public final NcmAudioPlayer audio = NcmAudioPlayer.INSTANCE;
    public final Map<String, Identifier> imageCache = new HashMap<>();
    public final Map<String, Boolean> imageLoading = new HashMap<>();
    public final Map<String, Boolean> imageFailed = new HashMap<>();

    public NcmUser user = NcmUser.empty();
    public String statusText = "";
    public String errorText = "";
    public boolean busy;

    public Page page = Page.HOME;
    public Page returnPage = Page.HOME;

    /* home */
    public List<NcmBanner> banners = new CopyOnWriteArrayList<>();
    public List<NcmPlaylist> homePlaylists = new CopyOnWriteArrayList<>();
    public List<NcmSong> dailySongs = new CopyOnWriteArrayList<>();
    public List<NcmSong> newSongs = new CopyOnWriteArrayList<>();
    public int bannerIndex;
    public long bannerLastSwitchMs;
    public boolean homeLoaded;
    public float homeScroll;
    public float homeScrollTarget;

    /* mine */
    public List<NcmPlaylist> myPlaylists = new CopyOnWriteArrayList<>();
    public boolean mineLoaded;

    /* playlist detail */
    public NcmPlaylist currentPlaylist;
    public List<NcmSong> playlistTracks = new CopyOnWriteArrayList<>();
    public float listScroll;
    public float listScrollTarget;
    /** Server offset for the next page; advances by the raw response size. */
    public int playlistTrackOffset;
    public boolean playlistHasMore;
    public boolean playlistLoading;
    public static final int PLAYLIST_PAGE_SIZE = 100;
    public static final int PLAYLIST_PREFETCH_DISTANCE = 20;
    private int playlistInFlightOffset = -1;
    private int playlistVisibleLastIndex = -1;
    private boolean playlistAutoLoadBlocked;
    private final AtomicLong playlistGeneration = new AtomicLong();
    private final Set<Long> playlistTrackIds = new HashSet<>();

    /* search */
    public String searchQuery = "";
    public List<NcmSong> searchResults = new CopyOnWriteArrayList<>();
    public boolean searchLoading;
    public long lastSearchTypeMs;
    public String pendingSearch = "";
    public boolean searchFocused;

    /* fm */
    public List<NcmSong> fmQueue = new CopyOnWriteArrayList<>();
    public int fmIndex;

    /* player */
    public NcmSong currentSong;
    public final List<NcmSong> playQueue = new CopyOnWriteArrayList<>();
    public int queueIndex = -1;
    public List<NcmLyricLine> lyrics = new CopyOnWriteArrayList<>();
    public boolean lyricsLoading;
    public String lyricsError = "";
    public float lyricScroll;
    public float lyricScrollVelocity;
    public boolean lyricsFollowPlayback = true;
    public long lyricsManualAt;
    public long lyricMotionAt;
    public Page lyricsReturnPage = Page.HOME;
    public boolean shuffle;
    public boolean repeatOne;
    public float volume = 0.85f;

    /* qr login */
    public Identifier qrTexture;
    public String qrUniKey = "";
    public String qrStatusText = Lang.tr("music.status.qr_idle");
    public int qrCode = -1;
    public long lastQrPollMs;
    public long lastQrRefreshMs;
    public boolean qrPolling;
    public boolean qrLoading;
    public String qrNicknameHint = "";

    private boolean sessionStarted;
    private final AtomicLong playbackRequest = new AtomicLong();
    private volatile Thread playbackUrlThread;
    private volatile Thread playbackDetailThread;
    private volatile Thread playbackLyricsThread;

    public MusicPlayer() {
        super("MusicPlayer", Category.Render);
        instance = this;
        setKeyCode(InputConstants.KEY_M);
        // Default mini-widget anchor (Interface hosts the actual drawing).
        setXYWH(4, 120, WIDGET_W, WIDGET_H);
        audio.setVolume(volume);
        // Re-bind end callback every construction; singleton audio outlives modules.
        audio.setOnEnded(() -> {
            if (mc != null) mc.execute(this::onTrackEnded);
        });
    }

    public NCMAPI api() {
        return ListClient.instance.ncmapi;
    }

    /* ================================================================== */
    /*  module lifecycle – entry to the Screen only                       */
    /* ================================================================== */

    @Override
    public void onEnable() {
        setSuffix("Screen");
        // Defer to the render thread: setEnable can arrive from the netty
        // thread (.toggle command intercepts outgoing packets in
        // Connection.doSendPacket) and setScreen off-thread crashes the game.
        // Entry-only: release the enable latch afterwards so the module list
        // doesn't treat MusicPlayer as a permanent HUD owner.
        mc.execute(() -> {
            ensureSession();
            openScreen();
            if (isEnable()) {
                setEnable(false);
            }
        });
    }

    @Override
    public void onDisable() {
        searchFocused = false;
        // Do NOT close the screen or stop audio – widget/Interface owns those.
        setSuffix(null);
    }

    public void openScreen() {
        if (mc == null) return;
        Screen current = mc.gui.screen();
        if (current instanceof MusicPlayerScreen) {
            return;
        }
        ensureSession();
        if (page == Page.LOGIN && !api().hasCookie()) {
            beginQrLogin(qrTexture == null);
        }
        mc.gui.setScreen(new MusicPlayerScreen(this));
    }

    public void closeScreenIfOpen() {
        if (mc == null) return;
        if (mc.gui.screen() instanceof MusicPlayerScreen) {
            mc.gui.setScreen(null);
        }
    }

    public void ensureSession() {
        if (sessionStarted) {
            return;
        }
        sessionStarted = true;
        statusText = Lang.tr("music.status.ready");
        if (api().hasCookie()) {
            refreshUserAndHome();
        } else {
            page = Page.LOGIN;
        }
    }

    public boolean isScreenOpen() {
        return mc != null && mc.gui.screen() instanceof MusicPlayerScreen;
    }

    /**
     * Mini player widget – called by {@link Interface}. Honours {@link #getX()}/{@link #getY()}
     * so the control is draggable via {@link #mouseClick}/{@link #doDrag}.
     */
    public void renderWidget(GuiGraphicsExtractor g) {
        // Render thread, once per frame: ease the Monet seed + re-apply the M3
        // scheme so the mini player animates its color morph even with the full
        // screen closed. Cheap on steady state.
        MonetTheme.update();
        ensureSession();
        tickQrPoll();
        tickBanner();
        tickSearchDebounce();

        int w = WIDGET_W;
        int h = WIDGET_H;
        setXYWH(getX(), getY(), w, h);
        int x = (int) getX();
        int y = (int) getY();

        // Shared fluid surface with the lyric player, tinted by the current album.
        M3.shadow(g, x, y, w, h, M3.SHAPE_M);
        M3.lyricBackground(g, x, y, w, h, currentSong != null);

        // cover = square album art; fall back to circular user avatar only when idle
        int artSize = 28;
        int ax = x + 6;
        int ay = y + (h - artSize) / 2;
        Identifier art = null;
        if (currentSong != null) {
            art = ensureImage(currentSong.coverUrl, "now_cover_" + currentSong.id, false, true);
            if (art != null) {
                RenderUtils.drawTexture(g, art, ax, ay, artSize, artSize);
            }
        }
        if (art == null && user != null && user.loggedIn) {
            art = ensureImage(user.avatarUrl, "avatar_" + user.userId, true, true);
            if (art != null) {
                RenderUtils.drawCircularTexture(g, art, ax, ay, artSize);
            }
        }
        if (art == null) {
            M3.roundRect(g, ax, ay, artSize, artSize, M3.SHAPE_XS, M3.SURFACE_CONTAINER_HIGH);
        }

        TTFFontRenderer titleFont = M3.label();
        TTFFontRenderer subFont = M3.labelSmall();
        int textX = ax + artSize + 6;
        if (currentSong != null) {
            titleFont.drawString(g, ellipsize(currentSong.name, 14), textX, y + 4, M3.ON_SURFACE);
            String sub = currentSong.artists == null ? "" : currentSong.artists;
            subFont.drawString(g, ellipsize(sub, 16), textX, y + 15, M3.ON_SURFACE_VARIANT);
            setSuffix(ellipsize(currentSong.name, 12));
        } else if (user != null && user.loggedIn) {
            titleFont.drawString(g, ellipsize(user.displayName(), 14), textX, y + 4, M3.ON_SURFACE);
            subFont.drawString(g, Lang.tr("widget.open_hint"), textX, y + 15, M3.ON_SURFACE_VARIANT);
            setSuffix(user.displayName());
        } else {
            titleFont.drawString(g, "MusicPlayer", textX, y + 4, M3.ON_SURFACE);
            subFont.drawString(g, Lang.tr("widget.idle_hint"), textX, y + 15, M3.ON_SURFACE_VARIANT);
            setSuffix("Idle");
        }

        // transport hint buttons (visual only; click handled in mouseClick)
        int bx = x + w - 52;
        int by = y + 8;
        drawMiniBtn(g, bx, by, audio.isPlaying() ? Icons.PAUSE : Icons.PLAY_ARROW,
                audio.isPlaying(), "widget-toggle");
        drawMiniBtn(g, bx + 18, by, Icons.SKIP_NEXT, false, "widget-next");

        // Compact display-only M3 determinate progress indicator.
        float prog = currentSong == null ? 0f : audio.progress();
        int barX = textX;
        int barW = w - (textX - x) - 58;
        int barY = y + h - 5;
        M3.linearProgress(g, barX, barY, barW, 2, prog);
    }

    /**
     * Small toggle pill (visual only; hit zones live in {@link #mouseClick}).
     * Selection is a container ROLE change per M3 (primary when active), not a
     * state layer – state layers are reserved for transient interaction.
     */
    private void drawMiniBtn(GuiGraphicsExtractor g, int x, int y, String icon, boolean active, String key) {
        int bg = active ? M3.PRIMARY : M3.SECONDARY_CONTAINER;
        int fg = active ? M3.ON_PRIMARY : M3.ON_SECONDARY_CONTAINER;
        M3.roundRect(g, x, y, 16, 12, M3.pill(12), bg);
        Ripple.draw(g, key, x, y, 16, 12, fg);
        Icons.drawCentered(g, icon, 8, x + 8f, y + 6f, fg);
    }

    /* widget interaction – independent of module enable flag (Interface hosts it) */
    private boolean widgetDragging;
    private float widgetOffX, widgetOffY;
    private int pressX, pressY;
    private boolean pressOpenedButtons;

    /** Drag + click handling for the Interface-hosted widget. */
    @Override
    public void mouseClick(int mouseX, int mouseY, int button) {
        if (!isHovered(getX(), getY(), getX() + getWidth(), getY() + getHeight(), mouseX, mouseY)) {
            return;
        }
        if (button == 0) {
            int x = (int) getX();
            int y = (int) getY();
            int w = (int) getWidth();
            int bx = x + w - 52;
            int by = y + 8;
            pressOpenedButtons = false;
            if (isHovered(bx, by, bx + 16, by + 12, mouseX, mouseY)) {
                Ripple.press("widget-toggle", mouseX, mouseY);
                audio.toggle();
                pressOpenedButtons = true;
                return;
            }
            if (isHovered(bx + 18, by, bx + 34, by + 12, mouseX, mouseY)) {
                Ripple.press("widget-next", mouseX, mouseY);
                playRelative(1);
                pressOpenedButtons = true;
                return;
            }
            widgetDragging = true;
            widgetOffX = (float) (mouseX - getX());
            widgetOffY = (float) (mouseY - getY());
            pressX = mouseX;
            pressY = mouseY;
        } else if (button == 1) {
            openScreen();
        }
    }

    @Override
    public void doDrag(int mouseX, int mouseY) {
        if (!widgetDragging) {
            return;
        }
        if (MouseData.mouseAction == 0) {
            widgetDragging = false;
            // click without meaningful drag → open full player
            if (!pressOpenedButtons
                    && Math.abs(mouseX - pressX) < 4
                    && Math.abs(mouseY - pressY) < 4) {
                openScreen();
            }
            return;
        }
        setXYWH(mouseX - widgetOffX, mouseY - widgetOffY, WIDGET_W, WIDGET_H);
    }

    /* ================================================================== */
    /*  navigation / actions (used by screen)                             */
    /* ================================================================== */

    public void switchTo(Page target) {
        if (target == Page.LYRICS) {
            if (page != Page.LYRICS) lyricsReturnPage = page == Page.PLAYLIST ? Page.HOME : page;
            page = Page.LYRICS;
            searchFocused = false;
            lyricsFollowPlayback = true;
            return;
        }
        if (target == Page.LOGIN) {
            page = Page.LOGIN;
            beginQrLogin(qrTexture == null);
            searchFocused = false;
            return;
        }
        if (target == Page.MINE) {
            page = Page.MINE;
            if (user != null && user.loggedIn && !mineLoaded) {
                loadMine();
            }
            searchFocused = false;
            return;
        }
        if (target == Page.HOME) {
            page = Page.HOME;
            if (!homeLoaded) loadHome();
            searchFocused = false;
            return;
        }
        if (target == Page.FM) {
            page = Page.FM;
            if (fmQueue.isEmpty() && user != null && user.loggedIn) loadFm();
            searchFocused = false;
            return;
        }
        if (target == Page.SEARCH) {
            page = Page.SEARCH;
            searchFocused = true;
            return;
        }
        page = target;
        searchFocused = false;
    }

    public void cycleMode() {
        if (!shuffle && !repeatOne) {
            shuffle = true;
        } else if (shuffle) {
            shuffle = false;
            repeatOne = true;
        } else {
            repeatOne = false;
        }
        statusText = repeatOne
                ? Lang.tr("music.status.repeat_one")
                : (shuffle ? Lang.tr("music.status.shuffle") : Lang.tr("music.status.sequential"));
    }

    public void dispatchAction(String id, int mouseX, int panelX) {
        if (id == null) return;
        errorText = "";

        if (id.startsWith("nav:")) {
            switchTo(Page.valueOf(id.substring(4)));
            return;
        }
        switch (id) {
            case "header_user" -> switchTo(user != null && user.loggedIn ? Page.MINE : Page.LOGIN);
            case "qr_refresh" -> beginQrLogin(true);
            case "logout" -> doLogout();
            case "toggle" -> audio.toggle();
            case "prev" -> playRelative(-1);
            case "next" -> playRelative(1);
            case "mode" -> cycleMode();
            case "lyrics_back" -> switchTo(lyricsReturnPage == Page.LYRICS ? Page.HOME : lyricsReturnPage);
            case "seek" -> {
                // panel-relative: bar starts at panelX + 110, width PANEL_W-200 (screen passes absolute)
                // Screen computes ratio itself for seek/volume when needed; keep simple absolute math here.
            }
            case "search_box" -> searchFocused = true;
            case "search_go" -> {
                searchFocused = false;
                runSearch(searchQuery);
            }
            case "fm_refresh" -> loadFm();
            case "pl_back" -> switchTo(returnPage == Page.PLAYLIST ? Page.HOME : returnPage);
            case "pl_play_all" -> {
                if (!playlistTracks.isEmpty()) {
                    playQueue.clear();
                    playQueue.addAll(playlistTracks);
                    queueIndex = 0;
                    playIndex(0);
                }
            }
            case "pl_load_more" -> loadMorePlaylistTracks();
            case "home_scroll_up" -> homeScrollTarget = Math.max(0, homeScrollTarget - 40);
            case "home_scroll_down" -> homeScrollTarget += 40;
            case "list_scroll_up" -> listScrollTarget = Math.max(0, listScrollTarget - 40);
            case "list_scroll_down" -> listScrollTarget += 40;
            default -> {
                if (id.startsWith("play_song:")) {
                    handlePlaySongClick(id.substring("play_song:".length()));
                } else if (id.startsWith("open_pl:")) {
                    handleOpenPlaylist(id.substring("open_pl:".length()));
                } else if (id.startsWith("lyric:")) {
                    seekToLyric(Integer.parseInt(id.substring("lyric:".length())));
                } else if (id.startsWith("banner:")) {
                    handleBannerClick();
                }
            }
        }
    }

    public void seekByRatio(float ratio) {
        audio.seekRatio(ratio);
    }

    public void setVolumeByRatio(float ratio) {
        volume = clamp(ratio, 0f, 1f);
        audio.setVolume(volume);
    }

    private void handlePlaySongClick(String rest) {
        int colon = rest.lastIndexOf(':');
        if (colon < 0) return;
        String prefix = rest.substring(0, colon);
        int index;
        try {
            index = Integer.parseInt(rest.substring(colon + 1));
        } catch (NumberFormatException e) {
            return;
        }
        List<NcmSong> source = switch (prefix) {
            case "daily" -> dailySongs;
            case "newsong" -> newSongs;
            case "search" -> searchResults;
            case "fm" -> fmQueue;
            case "pl" -> playlistTracks;
            default -> null;
        };
        if (source == null || index < 0 || index >= source.size()) return;

        playQueue.clear();
        playQueue.addAll(source);
        queueIndex = index;
        if ("fm".equals(prefix)) fmIndex = index;
        playIndex(index);
    }

    private void handleOpenPlaylist(String rest) {
        int colon = rest.lastIndexOf(':');
        if (colon < 0) return;
        String prefix = rest.substring(0, colon);
        int index;
        try {
            index = Integer.parseInt(rest.substring(colon + 1));
        } catch (NumberFormatException e) {
            return;
        }
        List<NcmPlaylist> source = switch (prefix) {
            case "homepl" -> homePlaylists;
            case "mine" -> myPlaylists;
            default -> null;
        };
        if (source == null || index < 0 || index >= source.size()) return;
        openPlaylist(source.get(index));
    }

    private void handleBannerClick() {
        if (banners.isEmpty()) return;
        NcmBanner b = banners.get(Math.floorMod(bannerIndex, banners.size()));
        if (b.targetId > 0 && (b.targetType == 1 || b.targetType == 0)) {
            NcmSong song = new NcmSong();
            song.id = b.targetId;
            song.name = b.title == null || b.title.isEmpty() ? Lang.tr("music.status.banner_song") : b.title;
            playQueue.clear();
            playQueue.add(song);
            queueIndex = 0;
            playIndex(0);
        } else if (b.targetId > 0 && b.targetType == 1000) {
            NcmPlaylist pl = new NcmPlaylist();
            pl.id = b.targetId;
            pl.name = b.title;
            pl.coverUrl = b.imageUrl;
            openPlaylist(pl);
        } else {
            statusText = "Banner: " + (b.typeTitle == null ? "" : b.typeTitle);
        }
    }

    /* ================================================================== */
    /*  data loading                                                      */
    /* ================================================================== */

    public void refreshUserAndHome() {
        busy = true;
        statusText = Lang.tr("music.status.checking_login");
        api().async(() -> {
            NcmUser u = api().fetchLoginStatus();
            if (u.loggedIn && u.userId > 0) {
                try {
                    NcmUser detail = api().fetchUserDetail(u.userId);
                    if (detail.loggedIn) {
                        u.level = detail.level;
                        if (u.avatarUrl == null || u.avatarUrl.isEmpty()) u.avatarUrl = detail.avatarUrl;
                        if (u.signature == null || u.signature.isEmpty()) u.signature = detail.signature;
                    }
                } catch (Exception ignored) {
                }
            }
            return u;
        }, u -> {
            this.user = u;
            busy = false;
            if (u.loggedIn) {
                statusText = Lang.tr("music.status.hello", u.displayName());
                qrPolling = false;
                if (page == Page.LOGIN) {
                    page = Page.HOME;
                }
                loadHome();
                loadMine();
            } else {
                statusText = Lang.tr("music.status.login_expired");
                api().clearCookie();
                page = Page.LOGIN;
                beginQrLogin(false);
            }
        }, ex -> {
            busy = false;
            errorText = Lang.tr("music.status.check_failed", ex.getMessage());
            page = Page.LOGIN;
            beginQrLogin(false);
        });
    }

    public void loadHome() {
        busy = true;
        statusText = Lang.tr("music.loading_home");
        api().async(api()::getHomePage, data -> {
            banners = new CopyOnWriteArrayList<>(data.banners);
            // Already trimmed server-side; don't merge extra playlist sources on first paint.
            homePlaylists = new CopyOnWriteArrayList<>(data.personalizedPlaylists);
            dailySongs = new CopyOnWriteArrayList<>(data.dailySongs);
            newSongs = new CopyOnWriteArrayList<>(data.newSongs);
            homeLoaded = true;
            busy = false;
            bannerIndex = 0;
            bannerLastSwitchMs = System.currentTimeMillis();
            statusText = Lang.tr("music.status.home_updated");
        }, ex -> {
            busy = false;
            homeLoaded = true;
            errorText = Lang.tr("music.status.home_failed", ex.getMessage());
        });
    }

    public void loadMine() {
        if (user == null || !user.loggedIn) return;
        // Keep the first page small – covers are only fetched for visible rows.
        api().async(() -> api().getUserPlaylists(user.userId, 12), list -> {
            myPlaylists = new CopyOnWriteArrayList<>(list);
            mineLoaded = true;
        }, ex -> errorText = Lang.tr("music.status.playlists_failed", ex.getMessage()));
    }

    public void loadFm() {
        if (user == null || !user.loggedIn) {
            errorText = Lang.tr("music.fm_needs_login");
            return;
        }
        statusText = Lang.tr("music.status.fm_loading");
        api().async(api()::getPersonalFm, list -> {
            fmQueue = new CopyOnWriteArrayList<>(list);
            fmIndex = 0;
            statusText = "FM " + Lang.tr("music.songs_count", list.size());
            if (!list.isEmpty() && (currentSong == null || !audio.isPlaying())) {
                playQueue.clear();
                playQueue.addAll(list);
                queueIndex = 0;
                playIndex(0);
            }
        }, ex -> errorText = Lang.tr("music.status.fm_failed", ex.getMessage()));
    }

    public void openPlaylist(NcmPlaylist pl) {
        playlistGeneration.incrementAndGet();
        currentPlaylist = pl;
        playlistTracks = new CopyOnWriteArrayList<>();
        playlistTrackIds.clear();
        playlistTrackOffset = 0;
        playlistInFlightOffset = -1;
        playlistVisibleLastIndex = -1;
        playlistHasMore = true;
        playlistLoading = false;
        playlistAutoLoadBlocked = false;
        returnPage = page == Page.PLAYLIST ? Page.HOME : page;
        page = Page.PLAYLIST;
        listScroll = listScrollTarget = 0;
        requestNextPlaylistPage();
    }

    public void loadMorePlaylistTracks() {
        playlistAutoLoadBlocked = false;
        requestNextPlaylistPage();
    }

    public void observePlaylistVisibleLastIndex(int lastVisibleIndex) {
        if (page != Page.PLAYLIST || lastVisibleIndex < 0) return;
        playlistVisibleLastIndex = Math.max(playlistVisibleLastIndex, lastVisibleIndex);
        pumpPlaylistPrefetch();
    }

    private void pumpPlaylistPrefetch() {
        if (currentPlaylist == null || playlistLoading || !playlistHasMore
                || playlistAutoLoadBlocked || playlistTrackOffset <= 0) return;
        int triggerIndex = playlistTrackOffset - PLAYLIST_PREFETCH_DISTANCE - 1;
        if (playlistVisibleLastIndex >= triggerIndex) {
            requestNextPlaylistPage();
        }
    }

    private void requestNextPlaylistPage() {
        if (currentPlaylist == null || playlistLoading || !playlistHasMore) return;
        final long generation = playlistGeneration.get();
        final long pid = currentPlaylist.id;
        final int offset = playlistTrackOffset;
        playlistLoading = true;
        playlistInFlightOffset = offset;
        statusText = offset == 0
                ? Lang.tr("music.status.loading_playlist")
                : Lang.tr("music.status.loading_more");

        api().async(() -> api().getPlaylistTracks(pid, PLAYLIST_PAGE_SIZE, offset), tracks -> {
            if (!isCurrentPlaylistRequest(generation, pid, offset)) return;
            int rawPageSize = tracks.size();
            for (NcmSong song : tracks) {
                if (song == null) continue;
                if (song.id <= 0 || playlistTrackIds.add(song.id)) {
                    playlistTracks.add(song);
                }
            }
            int nextOffset = offset + rawPageSize;
            playlistTrackOffset = nextOffset;
            boolean belowKnownTotal = currentPlaylist.trackCount <= 0
                    || nextOffset < currentPlaylist.trackCount;
            playlistHasMore = rawPageSize >= PLAYLIST_PAGE_SIZE && belowKnownTotal;
            playlistLoading = false;
            playlistInFlightOffset = -1;
            playlistAutoLoadBlocked = false;
            statusText = currentPlaylist.name + " · "
                    + Lang.tr("music.songs_count", playlistTracks.size() + (playlistHasMore ? "+" : ""));
            pumpPlaylistPrefetch();
        }, ex -> {
            if (!isCurrentPlaylistRequest(generation, pid, offset)) return;
            playlistLoading = false;
            playlistInFlightOffset = -1;
            playlistAutoLoadBlocked = true;
            errorText = Lang.tr("music.status.playlist_failed", ex.getMessage());
        });
    }

    private boolean isCurrentPlaylistRequest(long generation, long playlistId, int offset) {
        return playlistGeneration.get() == generation
                && currentPlaylist != null
                && currentPlaylist.id == playlistId
                && playlistLoading
                && playlistInFlightOffset == offset;
    }

    private void resetPlaylistPaging() {
        playlistGeneration.incrementAndGet();
        playlistLoading = false;
        playlistInFlightOffset = -1;
        playlistVisibleLastIndex = -1;
        playlistAutoLoadBlocked = false;
        currentPlaylist = null;
        playlistTracks = new CopyOnWriteArrayList<>();
        playlistTrackIds.clear();
        playlistTrackOffset = 0;
        playlistHasMore = false;
    }

    public void runSearch(String q) {
        if (q == null || q.trim().isEmpty()) {
            searchResults = new CopyOnWriteArrayList<>();
            return;
        }
        searchLoading = true;
        statusText = Lang.tr("music.status.searching_for", q);
        api().async(() -> api().searchSongs(q.trim(), 15), list -> {
            searchResults = new CopyOnWriteArrayList<>(list);
            searchLoading = false;
            statusText = Lang.tr("music.status.search_found", list.size());
        }, ex -> {
            searchLoading = false;
            errorText = Lang.tr("music.status.search_failed", ex.getMessage());
        });
    }

    public void doLogout() {
        resetPlaylistPaging();
        statusText = Lang.tr("music.status.logging_out");
        api().async(() -> {
            api().logout();
            return null;
        }, ignored -> {
            user = NcmUser.empty();
            mineLoaded = false;
            myPlaylists = new CopyOnWriteArrayList<>();
            dailySongs = new CopyOnWriteArrayList<>();
            homeLoaded = false;
            statusText = Lang.tr("music.status.logged_out");
            page = Page.LOGIN;
            beginQrLogin(true);
        }, ex -> {
            api().clearCookie();
            user = NcmUser.empty();
            page = Page.LOGIN;
            beginQrLogin(true);
        });
    }

    /* ================================================================== */
    /*  QR login                                                          */
    /* ================================================================== */

    public void beginQrLogin(boolean force) {
        if (qrLoading) return;
        long now = System.currentTimeMillis();
        if (!force && qrTexture != null && now - lastQrRefreshMs < 3_000) {
            qrPolling = true;
            return;
        }
        qrLoading = true;
        qrPolling = false;
        qrCode = -1;
        qrNicknameHint = "";
        qrStatusText = Lang.tr("music.status.qr_fetching");
        statusText = Lang.tr("music.status.qr_fetching");

        api().async(() -> api().getLoginQrCode(), pair -> {
            qrTexture = pair.getFirst();
            qrUniKey = pair.getSecond();
            qrLoading = false;
            qrPolling = true;
            lastQrRefreshMs = System.currentTimeMillis();
            lastQrPollMs = 0;
            qrCode = 801;
            qrStatusText = Lang.tr("music.status.qr_waiting");
            statusText = Lang.tr("music.status.qr_scan_prompt");
        }, ex -> {
            qrLoading = false;
            qrPolling = false;
            qrStatusText = Lang.tr("music.status.qr_fetch_failed", ex.getMessage());
            errorText = qrStatusText;
        });
    }

    public void tickQrPoll() {
        if (!qrPolling) return;
        if (page != Page.LOGIN) return;
        if (qrUniKey == null || qrUniKey.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastQrPollMs < 1500) return;
        lastQrPollMs = now;

        final String key = qrUniKey;
        api().async(() -> api().qrCodeStateCheck(key), pair -> {
            if (!key.equals(qrUniKey)) return;
            int code = pair.getFirst();
            String msg = pair.getSecond() == null ? "" : pair.getSecond();
            qrCode = code;
            switch (code) {
                case 800 -> {
                    qrStatusText = Lang.tr("music.status.qr_expired_refresh");
                    qrPolling = false;
                }
                case 801 -> qrStatusText = Lang.tr("music.status.qr_waiting");
                case 802 -> {
                    qrStatusText = msg.isEmpty() ? Lang.tr("music.status.qr_scanned_confirm") : msg;
                    qrNicknameHint = msg;
                }
                case 803 -> {
                    qrStatusText = Lang.tr("music.status.login_success");
                    qrPolling = false;
                    statusText = Lang.tr("music.status.login_loading_user");
                    onQrLoginSuccess();
                }
                default -> qrStatusText = Lang.tr("music.status.qr_state", code)
                        + (msg.isEmpty() ? "" : (" · " + msg));
            }
        }, ex -> qrStatusText = Lang.tr("music.status.qr_poll_error", ex.getMessage()));
    }

    private void onQrLoginSuccess() {
        api().async(() -> {
            NcmUser u = api().fetchLoginStatus();
            if (!u.loggedIn) {
                u = api().fetchAccount();
            }
            if (u.loggedIn && u.userId > 0) {
                try {
                    NcmUser detail = api().fetchUserDetail(u.userId);
                    if (detail.loggedIn) {
                        u.level = detail.level;
                        if (u.avatarUrl == null || u.avatarUrl.isEmpty()) u.avatarUrl = detail.avatarUrl;
                    }
                } catch (Exception ignored) {
                }
            }
            return u;
        }, u -> {
            this.user = u;
            if (u.loggedIn) {
                statusText = Lang.tr("music.status.welcome", u.displayName());
                page = Page.HOME;
                homeLoaded = false;
                mineLoaded = false;
                loadHome();
                loadMine();
            } else {
                errorText = Lang.tr("music.status.profile_failed");
            }
        }, ex -> errorText = Lang.tr("music.status.profile_error", ex.getMessage()));
    }

    /* ================================================================== */
    /*  playback                                                          */
    /* ================================================================== */

    public void playIndex(int index) {
        if (playQueue.isEmpty() || index < 0 || index >= playQueue.size()) return;

        long request = playbackRequest.incrementAndGet();
        interruptPlaybackRequests();
        queueIndex = index;
        NcmSong song = playQueue.get(index);
        currentSong = song;
        // If the song already carries a cover URL (e.g. from a playlist batch load),
        // extract the Monet seed immediately so the theme updates without waiting for
        // the detail-enrichment thread (which early-returns when metadata is complete).
        if (song.coverUrl != null && !song.coverUrl.isEmpty()) {
            extractSeedAsync(song.coverUrl, request);
        }
        lyrics = new CopyOnWriteArrayList<>();
        lyricsLoading = true;
        lyricsError = "";
        lyricScroll = 0f;
        lyricScrollVelocity = 0f;
        lyricMotionAt = 0L;
        lyricsFollowPlayback = true;
        statusText = Lang.tr("music.status.fetching_url");
        errorText = "";
        final long id = song.id;

        playbackUrlThread = startPlaybackThread("url", request, () -> {
            String url = api().getMusicURL(String.valueOf(id));
            if (!playbackRequestCurrent(request)) return;
            mc.execute(() -> {
                if (!playbackRequestCurrent(request)) return;
                if (url == null || url.isEmpty()) {
                    errorText = Lang.tr("music.status.no_play_url");
                    statusText = Lang.tr("music.status.cannot_play");
                    return;
                }
                song.playUrl = url;
                statusText = Lang.tr("music.status.playing", song.name);
                audio.playUrl(url, song.durationMs);
            });
        }, ex -> {
            errorText = Lang.tr("music.status.play_failed", ex.getMessage());
            statusText = Lang.tr("music.status.cannot_play");
        });

        playbackDetailThread = startPlaybackThread("detail", request, () -> {
            if (song.coverUrl != null && !song.coverUrl.isEmpty() && song.durationMs > 0) return;
            NcmSong full = api().getSong(id);
            if (!playbackRequestCurrent(request)) return;
            mc.execute(() -> {
                if (!playbackRequestCurrent(request)) return;
                full.playUrl = song.playUrl;
                currentSong = full;
                if (index < playQueue.size()) playQueue.set(index, full);
                extractSeedAsync(full.coverUrl, request);
            });
        }, ex -> {
            // Queue metadata is sufficient for playback; detail is enrichment only.
        });

        playbackLyricsThread = startPlaybackThread("lyrics", request, () -> {
            List<NcmLyricLine> loaded = api().getLyrics(id);
            if (!playbackRequestCurrent(request)) return;
            mc.execute(() -> {
                if (!playbackRequestCurrent(request)) return;
                lyrics = new CopyOnWriteArrayList<>(loaded);
                lyricsLoading = false;
                lyricsError = lyrics.isEmpty() ? Lang.tr("music.lyrics_empty") : "";
            });
        }, ex -> {
            lyricsLoading = false;
            lyricsError = Lang.tr("music.lyrics_error");
        });
    }

    private Thread startPlaybackThread(String purpose, long request,
                                       InterruptibleWork work, java.util.function.Consumer<Exception> onError) {
        Thread thread = new Thread(() -> {
            try {
                work.run();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                if (!playbackRequestCurrent(request)) return;
                mc.execute(() -> {
                    if (playbackRequestCurrent(request)) onError.accept(ex);
                });
            }
        }, "ncm-playback-" + purpose + "-" + request);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private boolean playbackRequestCurrent(long request) {
        return !Thread.currentThread().isInterrupted() && playbackRequest.get() == request;
    }

    private void interruptPlaybackRequests() {
        interrupt(playbackUrlThread);
        interrupt(playbackDetailThread);
        interrupt(playbackLyricsThread);
    }

    private static void interrupt(Thread thread) {
        if (thread != null) thread.interrupt();
    }

    @FunctionalInterface
    private interface InterruptibleWork {
        void run() throws Exception;
    }

    /**
     * Extract a Monet seed color from the now-playing album cover off-thread and
     * hand it to {@link MonetTheme}. No cover ⇒ no-op (keeps the last seed). Any
     * failure (download, decode, empty image) is swallowed – a bad cover must
     * never crash or spam the theme.
     */
    private void extractSeedAsync(String coverUrl, long request) {
        if (coverUrl == null || coverUrl.isEmpty()) {
            return;
        }
        api().async(() -> {
            byte[] b = api().fetchCoverBytes(coverUrl);
            if (b == null || b.length == 0) {
                return null;
            }
            try (NativeImage img = DynamicImageUtils.decodeBytes(b)) {
                int w = img.getWidth();
                int h = img.getHeight();
                if (w <= 0 || h <= 0) {
                    return null;
                }
                return MonetColor.seedFromPixels(img.getPixels(), w, h);
            }
        }, seed -> {
            if (seed != null && playbackRequest.get() == request) {
                MonetTheme.requestSeed(seed);
            }
        }, ex -> {
        });
    }

    public void playRelative(int delta) {
        if (playQueue.isEmpty()) return;
        if (repeatOne && delta == 1 && audio.progress() > 0.95f) {
            audio.seekRatio(0f);
            audio.resume();
            return;
        }
        int next;
        if (shuffle) {
            if (playQueue.size() == 1) {
                next = queueIndex;
            } else {
                next = queueIndex;
                int guard = 0;
                while (next == queueIndex && guard++ < 10) {
                    next = (int) (Math.random() * playQueue.size());
                }
            }
        } else {
            next = queueIndex + delta;
            if (next < 0) next = playQueue.size() - 1;
            if (next >= playQueue.size()) next = 0;
        }
        playIndex(next);
    }

    private void onTrackEnded() {
        if (repeatOne) {
            audio.seekRatio(0f);
            audio.resume();
            return;
        }
        if (page == Page.FM || (currentSong != null && fmQueue.contains(currentSong))) {
            if (queueIndex >= playQueue.size() - 1) {
                loadFm();
                return;
            }
        }
        playRelative(1);
    }

    /* ================================================================== */
    /*  ticks / images                                                    */
    /* ================================================================== */

    public void tickBanner() {
        if (banners == null || banners.size() <= 1) return;
        long now = System.currentTimeMillis();
        if (now - bannerLastSwitchMs > 5000) {
            bannerIndex = (bannerIndex + 1) % banners.size();
            bannerLastSwitchMs = now;
        }
    }

    public void tickSearchDebounce() {
        if (pendingSearch == null) return;
        if (System.currentTimeMillis() - lastSearchTypeMs < 550) return;
        String q = pendingSearch;
        pendingSearch = null;
        if (page == Page.SEARCH) {
            runSearch(q);
        }
    }

    public void onSearchTyped() {
        pendingSearch = searchQuery;
        lastSearchTypeMs = System.currentTimeMillis();
    }

    /**
     * Covers are loaded lazily when a row/card paints. Downloads go through a
     * FIFO queue with optional priority so the now-playing cover / avatar are
     * never starved by a wall of list thumbnails.
     */
    private void prefetchCovers() {
        // no bulk prefetch
    }

    private static final int MAX_IN_FLIGHT_IMAGES = 3;

    private static final class ImageJob {
        final String url;
        final String key;
        final String cacheKey;
        final boolean circular;
        final boolean priority;

        ImageJob(String url, String key, String cacheKey, boolean circular, boolean priority) {
            this.url = url;
            this.key = key;
            this.cacheKey = cacheKey;
            this.circular = circular;
            this.priority = priority;
        }
    }

    /** Pending downloads: priority jobs are drained first, then FIFO normals. */
    private final java.util.ArrayDeque<ImageJob> imageQueueHigh = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<ImageJob> imageQueueNorm = new java.util.ArrayDeque<>();
    private int imagesInFlight = 0;

    public Identifier ensureImage(String url, String key) {
        return ensureImage(url, key, false, false);
    }

    public Identifier ensureImage(String url, String key, boolean circular) {
        return ensureImage(url, key, circular, false);
    }

    /**
     * @param circular only for user avatars – album covers must stay square
     * @param priority true for now-playing cover / header avatar (jump the queue)
     */
    public Identifier ensureImage(String url, String key, boolean circular, boolean priority) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String cacheKey = circular ? ("c:" + key) : key;
        Identifier cached = imageCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        if (Boolean.TRUE.equals(imageFailed.get(cacheKey))) {
            return null;
        }
        if (imageLoading.containsKey(cacheKey)) {
            pumpImageQueue();
            return null;
        }

        // Already queued?
        if (!isQueued(cacheKey)) {
            ImageJob job = new ImageJob(url, key, cacheKey, circular, priority);
            if (priority) {
                imageQueueHigh.addLast(job);
            } else {
                imageQueueNorm.addLast(job);
            }
            imageLoading.put(cacheKey, true); // reserved – prevents duplicate enqueue
        }
        pumpImageQueue();
        return null;
    }

    private boolean isQueued(String cacheKey) {
        for (ImageJob j : imageQueueHigh) {
            if (j.cacheKey.equals(cacheKey)) return true;
        }
        for (ImageJob j : imageQueueNorm) {
            if (j.cacheKey.equals(cacheKey)) return true;
        }
        return false;
    }

    private void pumpImageQueue() {
        while (imagesInFlight < MAX_IN_FLIGHT_IMAGES) {
            ImageJob job = imageQueueHigh.pollFirst();
            if (job == null) job = imageQueueNorm.pollFirst();
            if (job == null) return;
            // Skip if another path already filled the cache.
            if (imageCache.containsKey(job.cacheKey)) {
                imageLoading.remove(job.cacheKey);
                continue;
            }
            startImageJob(job);
        }
    }

    private void startImageJob(ImageJob job) {
        imagesInFlight++;
        imageLoading.put(job.cacheKey, true);
        api().async(() -> api().downloadImage(job.url, job.key, job.circular), id -> {
            if (id != null) {
                imageCache.put(job.cacheKey, id);
            } else {
                imageFailed.put(job.cacheKey, true);
            }
            imageLoading.remove(job.cacheKey);
            imagesInFlight = Math.max(0, imagesInFlight - 1);
            pumpImageQueue();
        }, ex -> {
            imageFailed.put(job.cacheKey, true);
            imageLoading.remove(job.cacheKey);
            imagesInFlight = Math.max(0, imagesInFlight - 1);
            pumpImageQueue();
        });
    }

    /** Stop all music (used if needed externally). */
    public void stopPlayback() {
        audio.stop();
        currentSong = null;
        // Nothing playing → drop the album-derived seed, ease back to Light Blue.
        MonetTheme.reset();
    }

    public int currentLyricIndex() {
        if (lyrics == null || lyrics.isEmpty()) return -1;
        long pos = audio.positionMs();
        int lo = 0;
        int hi = lyrics.size() - 1;
        int result = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lyrics.get(mid).timeMs <= pos) {
                result = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return result;
    }

    public void seekToLyric(int index) {
        if (lyrics == null || index < 0 || index >= lyrics.size()) return;
        audio.seekMs(lyrics.get(index).timeMs);
        lyricsFollowPlayback = true;
        lyricScrollVelocity = 0f;
    }

    public String currentLyricLine() {
        int index = currentLyricIndex();
        return index < 0 ? "" : lyrics.get(index).text;
    }

    public static String ellipsize(String s, int maxChars) {
        if (s == null) return "";
        if (s.length() <= maxChars) return s;
        if (maxChars <= 1) return "...";
        return s.substring(0, maxChars - 1) + "...";
    }

    public static String formatMs(long ms) {
        long total = Math.max(0, ms / 1000);
        long m = total / 60;
        long s = total % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    public static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
