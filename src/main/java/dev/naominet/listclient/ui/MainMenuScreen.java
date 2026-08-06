package dev.naominet.listclient.ui;

import dev.naominet.listclient.module.render.MusicPlayer;
import dev.naominet.listclient.ui.theme.Icons;
import dev.naominet.listclient.ui.theme.M3;
import dev.naominet.listclient.ui.theme.MonetTheme;
import dev.naominet.listclient.ui.theme.Ripple;
import dev.naominet.listclient.utils.AnimationUtils;
import dev.naominet.listclient.utils.Lang;
import dev.naominet.listclient.utils.RenderUtils;
import dev.naominet.listclient.utils.font.TTFFontRenderer;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Client main menu – replaces the vanilla {@link TitleScreen} (installed by
 * {@code MixinGui}; extends TitleScreen so {@code instanceof} checks and the
 * panorama spin keep working, but {@code TitleScreen#init} is never called).
 * <p>
 * Fourth generation, panel-first Material 3: a single elevated surface panel
 * over a frosted-blurred panorama, split into distinct M3 regions – a
 * primary-container hero band carrying the wordmark, a body of icon-led list
 * buttons (one filled primary CTA, the rest tonal), and a footer band of M3
 * icon buttons. No account/profile is shown here – accounts live in the
 * {@link AccountManagerScreen}. Strings via {@link Lang}, icons via
 * {@link Icons}, motion via a cubic scale-in + hover state layers.
 */
public class MainMenuScreen extends TitleScreen {

    private static final int BACKGROUND_WIDTH = 10080;
    private static final int BACKGROUND_HEIGHT = 5760;
    private static final String DEMO_LEVEL_ID = "Demo_World";

    private static final int PANEL_W = 176;
    private static final int HERO_H = 40;
    private static final int FOOTER_H = 26;
    private static final int BTN_H = 17;
    private static final int BTN_GAP = 3;
    private static final int PAD = 10;
    private static final int MARGIN = 20;

    private final TTFFontRenderer displayFont = M3.display();
    private final TTFFontRenderer titleFont = M3.title();
    private final TTFFontRenderer smallFont = M3.labelSmall();

    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> icons = new ArrayList<>();

    private long openedAt;
    private float fade;
    private int mouseX;
    private int mouseY;
    private int selected = -1;
    private String hint = "";

    public MainMenuScreen() {
        super(false);
    }

    /* ================================================================== */
    /*  setup                                                             */
    /* ================================================================== */

    @Override
    protected void init() {
        if (openedAt == 0L) {
            openedAt = Util.getMillis();
        }
        buildEntries();
    }

    private void buildEntries() {
        entries.clear();
        icons.clear();

        if (this.minecraft.isDemo()) {
            boolean demoWorldPresent = checkDemoWorldPresence();
            entries.add(new Entry("menu.play_demo", Icons.PLAY_ARROW, Kind.FILLED, this::playDemo,
                    () -> true, null));
            entries.add(new Entry("menu.reset_demo", Icons.REFRESH, Kind.TONAL, this::resetDemo,
                    () -> demoWorldPresent, "menu.no_demo_world"));
        } else {
            entries.add(new Entry("menu.play", Icons.PLAY_ARROW, Kind.FILLED,
                    () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this)), () -> true, null));

            Component reason = multiplayerDisabledReason();
            String reasonText = reason == null ? null : reason.getString();
            entries.add(new Entry("menu.multiplayer", Icons.PUBLIC, Kind.TONAL,
                    () -> this.minecraft.gui.setScreen(this.minecraft.options.skipMultiplayerWarning
                            ? new JoinMultiplayerScreen(this)
                            : new SafetyScreen(this)),
                    () -> reasonText == null, reasonText));
        }

        entries.add(new Entry("menu.accounts", Icons.PERSON, Kind.TONAL,
                () -> this.minecraft.gui.setScreen(new AccountManagerScreen(this)), () -> true, null));
        entries.add(new Entry("menu.options", Icons.SETTINGS, Kind.TONAL,
                () -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false)),
                () -> true, null));

        icons.add(new Entry("menu.music", Icons.MUSIC_NOTE, Kind.ICON, this::openMusic,
                () -> MusicPlayer.instance != null, "menu.no_music_module"));
        icons.add(new Entry("menu.language", Icons.TRANSLATE, Kind.ICON, Lang::cycle, () -> true, null));
        icons.add(new Entry("menu.quit", Icons.POWER, Kind.ICON, this.minecraft::stop, () -> true, null));
    }

    private void openMusic() {
        MusicPlayer mp = MusicPlayer.instance;
        if (mp != null) {
            mp.openScreen();
        }
    }

    /* ================================================================== */
    /*  render                                                            */
    /* ================================================================== */

    private int panelX, panelY, panelH;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        MonetTheme.update();
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.hint = "";

        if (entries.isEmpty()) {
            buildEntries();
        }

        fade = clamp01((Util.getMillis() - openedAt) / 420f);

        drawBackground(g);
        // Frosted glass: blur the background, then a light scrim for text contrast.
        //M3.blurBehind(g);
        g.fillGradient(0, 0, this.width, this.height,
                M3.withAlpha(M3.SCRIM, 0x40), M3.withAlpha(M3.SCRIM, 0x80));

        layout();

        // Scale-in around the panel center.
        float open = AnimationUtils.easeOutCubic((Util.getMillis() - openedAt) / 260f);
        boolean scaled = open < 0.995f;
        if (scaled) {
            float s = 0.94f + 0.06f * open;
            float cx = panelX + PANEL_W / 2f;
            float cy = panelY + panelH / 2f;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(s, s);
            g.pose().translate(-cx, -cy);
        }
        try {
            drawPanel(g);
        } finally {
            if (scaled) {
                g.pose().popMatrix();
            }
        }

        drawFooterLine(g);
        drawNowPlaying(g);
    }

    private void drawBackground(GuiGraphicsExtractor g) {
        // 原图尺寸
        final int IMAGE_WIDTH = 10080;
        final int IMAGE_HEIGHT = 5760;

        // 计算缩放比例，使图片完全覆盖屏幕（类似cover效果）
        float scaleX = (float) this.width / IMAGE_WIDTH;
        float scaleY = (float) this.height / IMAGE_HEIGHT;
        float scale = Math.max(scaleX, scaleY);

        // 计算实际绘制尺寸（按比例缩放后的尺寸）
        int drawWidth = Math.round(IMAGE_WIDTH * scale);
        int drawHeight = Math.round(IMAGE_HEIGHT * scale);

        // 计算偏移量，使图片居中显示
        int offsetX = (this.width - drawWidth) / 2;
        int offsetY = (this.height - drawHeight) / 2;

        // 绘制缩放后的图片
        RenderUtils.drawTexture(g, "gui/background.jpg", "bg",
                offsetX, offsetY, drawWidth, drawHeight);
    }

    private void layout() {
        int bodyH = PAD + entries.size() * BTN_H + (entries.size() - 1) * BTN_GAP + PAD;
        panelH = HERO_H + bodyH + FOOTER_H;
        panelX = clamp(this.width / 8, MARGIN, Math.max(MARGIN, this.width - PANEL_W - MARGIN));
        panelY = clamp((this.height - panelH) / 2, 12, Math.max(12, this.height - panelH - 16));
    }

    private void drawPanel(GuiGraphicsExtractor g) {
        int x = panelX;
        int y = panelY;

        M3.shadow(g, x, y, PANEL_W, panelH, M3.SHAPE_XL);
        M3.roundRect(g, x, y, PANEL_W, panelH, M3.SHAPE_XL, faded(M3.SURFACE_CONTAINER));

        drawHero(g, x, y);

        int by = y + HERO_H + PAD;
        int bx = x + PAD;
        int bw = PANEL_W - PAD * 2;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            e.x = bx;
            e.y = by + i * (BTN_H + BTN_GAP);
            e.w = bw;
            e.h = BTN_H;
            drawButton(g, e, i == selected);
        }

        drawFooterBand(g, x, y + panelH - FOOTER_H);

        if (!hint.isEmpty()) {
            String shown = hint.length() > 30 ? hint.substring(0, 29) + "…" : hint;
            smallFont.drawCenteredString(g, shown, x + PANEL_W / 2f, y + panelH + 4, faded(M3.ERROR));
        }
    }

    /** Primary-container hero band with the wordmark, rounded to match the panel top. */
    private void drawHero(GuiGraphicsExtractor g, int x, int y) {
        M3.roundRect(g, x, y, PANEL_W, HERO_H, M3.SHAPE_XL,
                faded(M3.PRIMARY_CONTAINER), true, true, false, false);

        float cx = x + PANEL_W / 2f;
        displayFont.drawCenteredString(g, "List", cx, y + 6, faded(M3.ON_PRIMARY_CONTAINER));
        int markW = (int) displayFont.width("List") + 26;
        int lw = Math.max(1, (int) (markW * easeOut(fade)));
        fill(g, (int) (cx - lw / 2f), y + 24, lw, 1, faded(M3.PRIMARY));
        smallFont.drawCenteredString(g, "CLIENT · BUILD 4.0", cx, y + 28,
                faded(M3.withAlpha(M3.ON_PRIMARY_CONTAINER, 0xC8)));
    }

    private void drawButton(GuiGraphicsExtractor g, Entry e, boolean keyboardFocus) {
        boolean enabled = e.enabled.getAsBoolean();
        boolean hot = enabled && (keyboardFocus || isOver(e.x, e.y, e.w, e.h));
        e.hover = AnimationUtils.animationNew(e.hover, hot ? 1f : 0f, 4f, 0.06f);
        float t = clamp01(e.hover);

        int container;
        int onColor;
        if (e.kind == Kind.FILLED) {
            container = M3.layered(M3.PRIMARY, M3.ON_PRIMARY, (int) (M3.STATE_HOVER * t));
            onColor = M3.ON_PRIMARY;
        } else {
            container = M3.layered(M3.SECONDARY_CONTAINER, M3.ON_SECONDARY_CONTAINER,
                    (int) (M3.STATE_HOVER * t));
            onColor = M3.ON_SECONDARY_CONTAINER;
        }
        if (!enabled) {
            container = M3.withAlpha(M3.ON_SURFACE, M3.DISABLED_CONTAINER);
            onColor = M3.withAlpha(M3.ON_SURFACE, M3.DISABLED_CONTENT);
        }

        M3.roundRect(g, e.x, e.y, e.w, e.h, M3.pill(e.h), faded(container));
        Ripple.draw(g, e, e.x, e.y, e.w, e.h, M3.pill(e.h), faded(onColor));
        if (keyboardFocus && enabled) {
            M3.focusRing(g, e.x, e.y, e.w, e.h, M3.pill(e.h));
        }

        String label = Lang.tr(e.labelKey);
        int iconSize = 9;
        float groupW = iconSize + 5 + titleFont.width(label);
        float gx = e.x + (e.w - groupW) / 2f;
        Icons.drawCentered(g, e.icon, iconSize, gx + iconSize / 2f, e.y + e.h / 2f, faded(onColor));
        titleFont.drawString(g, label, gx + iconSize + 5,
                e.y + (e.h - titleFont.lineHeight()) / 2f, faded(onColor));

        if (!enabled && e.disabledHintKey != null && isOver(e.x, e.y, e.w, e.h)) {
            hint = Lang.tr(e.disabledHintKey);
        }
    }

    /** Footer band of M3 icon buttons. */
    private void drawFooterBand(GuiGraphicsExtractor g, int x, int y) {
        M3.divider(g, x + PAD, y, PANEL_W - PAD * 2);
        int size = 16;
        int gap = 12;
        float total = icons.size() * size + (icons.size() - 1) * gap;
        float ix = x + PANEL_W / 2f - total / 2f;
        int iconY = y + (FOOTER_H - size) / 2;
        for (Entry e : icons) {
            e.x = (int) ix;
            e.y = iconY;
            e.w = size;
            e.h = size;

            boolean enabled = e.enabled.getAsBoolean();
            boolean hot = enabled && isOver(e.x, e.y, size, size);
            e.hover = AnimationUtils.animationNew(e.hover, hot ? 1f : 0f, 4f, 0.06f);
            float t = clamp01(e.hover);

            if (t > 0.01f) {
                M3.roundRect(g, e.x, e.y, size, size, M3.pill(size),
                        faded(M3.stateLayer(M3.ON_SURFACE, (int) (M3.STATE_HOVER * t))));
            }
            Ripple.draw(g, e, e.x, e.y, size, size, M3.pill(size), faded(M3.ON_SURFACE));
            int c = enabled ? M3.lerp(M3.ON_SURFACE_VARIANT, M3.ON_SURFACE, t)
                    : M3.withAlpha(M3.ON_SURFACE, M3.DISABLED_CONTENT);
            Icons.drawCentered(g, e.icon, 10, e.x + size / 2f, e.y + size / 2f, faded(c));
            if (!enabled && e.disabledHintKey != null && isOver(e.x, e.y, size, size)) {
                hint = Lang.tr(e.disabledHintKey);
            }
            ix += size + gap;
        }
    }

    /* ---- footer text + now playing ---- */

    private void drawFooterLine(GuiGraphicsExtractor g) {
        String version = "Minecraft " + SharedConstants.getCurrentVersion().name();
        if (this.minecraft.isDemo()) {
            version = version + " Demo";
        }
        if (Minecraft.checkModStatus().shouldReportAsModified()) {
            version = version + I18n.get("menu.modded");
        }
        smallFont.drawString(g, version, 4, this.height - 12, faded(M3.ON_SURFACE_VARIANT));

        String brand = "List Client · Build 4.0";
        smallFont.drawString(g, brand, this.width - smallFont.width(brand) - 4, this.height - 12,
                faded(M3.withAlpha(M3.ON_SURFACE_VARIANT, 0xB4)));
    }

    private void drawNowPlaying(GuiGraphicsExtractor g) {
        MusicPlayer mp = MusicPlayer.instance;
        if (mp == null || mp.currentSong == null) {
            nowPlayingW = 0;
            return;
        }
        int h = 28;
        int y = this.height - h - 20;
        int w = Math.min(180, this.width - (panelX + PANEL_W) - 24);
        if (w < 108) {
            nowPlayingW = 0;
            return;
        }
        int x = this.width - w - 14;

        nowPlayingX = x;
        nowPlayingY = y;
        nowPlayingW = w;
        nowPlayingH = h;

        boolean hot = isOver(x, y, w, h);
        nowPlayingHover = AnimationUtils.animationNew(nowPlayingHover, hot ? 1f : 0f, 4f, 0.06f);
        float t = clamp01(nowPlayingHover);

        int bg = M3.layered(M3.SURFACE_CONTAINER_HIGH, M3.ON_SURFACE, (int) (M3.STATE_HOVER * t));
        M3.shadow(g, x, y, w, h, M3.SHAPE_M);
        M3.roundRect(g, x, y, w, h, M3.SHAPE_M, faded(bg));
        Ripple.draw(g, "now-playing", x, y, w, h, M3.SHAPE_M, faded(M3.ON_SURFACE));

        var audio = mp.audio;
        Icons.drawCentered(g, audio.isPlaying() ? Icons.MUSIC_NOTE : Icons.PAUSE, 9,
                x + 11, y + h / 2f, faded(M3.PRIMARY));
        String state = Lang.tr(audio.isLoading() ? "np.buffering"
                : audio.isPlaying() ? "np.playing" : "np.paused");
        smallFont.drawString(g, state, x + 20, y + 3, faded(M3.ON_SURFACE_VARIANT));
        titleFont.drawString(g, MusicPlayer.ellipsize(mp.currentSong.name, 16), x + 20, y + 12,
                faded(M3.ON_SURFACE));

        int barY = y + h - 5;
        fill(g, x + 20, barY, w - 30, 2, faded(M3.SURFACE_CONTAINER_HIGHEST));
        int filled = (int) ((w - 30) * clamp01(audio.progress()));
        if (filled > 0) {
            fill(g, x + 20, barY, filled, 2, faded(M3.PRIMARY));
        }
    }

    private int nowPlayingX, nowPlayingY, nowPlayingW, nowPlayingH;
    private float nowPlayingHover;

    /* ================================================================== */
    /*  input                                                             */
    /* ================================================================== */

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        int mx = (int) event.x();
        int my = (int) event.y();

        for (Entry e : entries) {
            if (e.w > 0 && isInside(e, mx, my)) {
                activate(e);
                return true;
            }
        }
        for (Entry e : icons) {
            if (e.w > 0 && isInside(e, mx, my)) {
                activate(e);
                return true;
            }
        }
        if (nowPlayingW > 0 && mx >= nowPlayingX && mx <= nowPlayingX + nowPlayingW
                && my >= nowPlayingY && my <= nowPlayingY + nowPlayingH) {
            Ripple.press("now-playing", mx, my);
            openMusic();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void activate(Entry e) {
        if (!e.enabled.getAsBoolean()) {
            return;
        }
        float px = isOver(e.x, e.y, e.w, e.h) ? this.mouseX : e.x + e.w / 2f;
        float py = isOver(e.x, e.y, e.w, e.h) ? this.mouseY : e.y + e.h / 2f;
        Ripple.press(e, px, py);
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        e.action.run();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (entries.isEmpty()) {
            return super.keyPressed(event);
        }
        switch (key) {
            case GLFW.GLFW_KEY_DOWN -> {
                selected = step(selected, 1);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                selected = step(selected, -1);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                if (selected >= 0 && selected < entries.size()) {
                    activate(entries.get(selected));
                    return true;
                }
            }
            default -> {
            }
        }
        return super.keyPressed(event);
    }

    private int step(int from, int dir) {
        int size = entries.size();
        int at = from < 0 ? (dir > 0 ? -1 : 0) : from;
        for (int i = 0; i < size; i++) {
            at = Math.floorMod(at + dir, size);
            if (entries.get(at).enabled.getAsBoolean()) {
                return at;
            }
        }
        return from;
    }

    /* ================================================================== */
    /*  demo mode (mirrors vanilla TitleScreen)                           */
    /* ================================================================== */

    private void playDemo() {
        if (checkDemoWorldPresence()) {
            this.minecraft.createWorldOpenFlows()
                    .openWorld(DEMO_LEVEL_ID, () -> this.minecraft.gui.setScreen(this));
        } else {
            this.minecraft.createWorldOpenFlows().createFreshLevel(
                    DEMO_LEVEL_ID, MinecraftServer.DEMO_SETTINGS, WorldOptions.DEMO_OPTIONS,
                    WorldPresets::createNormalWorldDimensions, this);
        }
    }

    private void resetDemo() {
        LevelStorageSource levelSource = this.minecraft.getLevelSource();
        try (LevelStorageSource.LevelStorageAccess access = levelSource.createAccess(DEMO_LEVEL_ID)) {
            if (access.hasWorldData()) {
                this.minecraft.gui.setScreen(new ConfirmScreen(
                        this::confirmDemo,
                        Component.translatable("selectWorld.deleteQuestion"),
                        Component.translatable("selectWorld.deleteWarning", MinecraftServer.DEMO_SETTINGS.levelName()),
                        Component.translatable("selectWorld.deleteButton"),
                        CommonComponents.GUI_CANCEL));
            }
        } catch (IOException e) {
            SystemToast.onWorldAccessFailure(this.minecraft, DEMO_LEVEL_ID);
        }
    }

    private void confirmDemo(boolean result) {
        if (result) {
            try (LevelStorageSource.LevelStorageAccess access =
                         this.minecraft.getLevelSource().createAccess(DEMO_LEVEL_ID)) {
                access.deleteLevel();
            } catch (IOException e) {
                SystemToast.onWorldDeleteFailure(this.minecraft, DEMO_LEVEL_ID);
            }
        }
        this.minecraft.gui.setScreen(this);
    }

    private boolean checkDemoWorldPresence() {
        try (LevelStorageSource.LevelStorageAccess access =
                     this.minecraft.getLevelSource().createAccess(DEMO_LEVEL_ID)) {
            return access.hasWorldData();
        } catch (IOException e) {
            SystemToast.onWorldAccessFailure(this.minecraft, DEMO_LEVEL_ID);
            return false;
        }
    }

    private Component multiplayerDisabledReason() {
        if (this.minecraft.allowsMultiplayer()) {
            return null;
        }
        if (this.minecraft.isNameBanned()) {
            return Component.translatable("title.multiplayer.disabled.banned.name");
        }
        return Component.translatable("title.multiplayer.disabled");
    }

    /* ================================================================== */
    /*  helpers                                                           */
    /* ================================================================== */

    private boolean isOver(int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static boolean isInside(Entry e, int mx, int my) {
        return mx >= e.x && mx <= e.x + e.w && my >= e.y && my <= e.y + e.h;
    }

    private void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, argb);
    }

    private int faded(int argb) {
        if (fade >= 1f) return argb;
        int out = ARGB.multiplyAlpha(argb, fade);
        return (out >>> 24) == 0 ? (argb & 0x00FFFFFF) | 0x01000000 : out;
    }

    private static float easeOut(float t) {
        t = clamp01(t);
        return 1f - (1f - t) * (1f - t);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private enum Kind {
        FILLED, TONAL, ICON
    }

    /** A menu action: label/hint are i18n keys resolved at draw time. */
    private static final class Entry {
        final String labelKey;
        final String icon;
        final Kind kind;
        final Runnable action;
        final BooleanSupplier enabled;
        final String disabledHintKey;

        float hover;
        int x, y, w, h;

        Entry(String labelKey, String icon, Kind kind, Runnable action,
              BooleanSupplier enabled, String disabledHintKey) {
            this.labelKey = labelKey;
            this.icon = icon;
            this.kind = kind;
            this.action = action;
            this.enabled = enabled;
            this.disabledHintKey = disabledHintKey;
        }
    }
}
