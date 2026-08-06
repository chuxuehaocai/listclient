package dev.naominet.listclient.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.naominet.listclient.manager.FileManager;
import dev.naominet.listclient.manager.ModuleManager;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.ui.theme.Icons;
import dev.naominet.listclient.ui.theme.M3;
import dev.naominet.listclient.ui.theme.MonetTheme;
import dev.naominet.listclient.ui.theme.Ripple;
import dev.naominet.listclient.utils.AnimationUtils;
import dev.naominet.listclient.utils.Lang;
import dev.naominet.listclient.utils.font.TTFFontRenderer;
import dev.naominet.listclient.value.Mode;
import dev.naominet.listclient.value.Numbers;
import dev.naominet.listclient.value.Option;
import dev.naominet.listclient.value.Value;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * ClickGUI – module configuration screen, Material 3 throughout.
 * <p>
 * Layout: an M3 navigation rail (one destination per non-empty
 * {@link Category}, Material icon + label, secondary-container pill for the
 * active one), a search field and client-language toggle in the header, and a
 * scrollable list of module cards. Cards carry a Material icon, name, keybind
 * chip and an M3 switch; cards with settings expand to rows – switches for
 * {@link Option}, a slider for {@link Numbers}, choice chips for {@link Mode}.
 * <p>
 * Motion: the panel scale-fades in on open (M3 emphasized-decelerate feel),
 * card expansion animates its height under a scissor, category switches slide
 * the content in, and switches/hover layers all interpolate. All strings come
 * from {@link Lang} – the client's own i18n, independent of Minecraft's
 * language.
 * <p>
 * Immediate-mode like the other screens: click zones are rebuilt every frame
 * and clipped to the scrolled viewport so hidden rows can't steal clicks.
 * Config saves when the screen closes.
 */
public class ClickGuiScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 200;
    private static final int HEADER_H = 24;
    private static final int RAIL_W = 40;
    private static final int PAD = 6;
    private static final int CARD_H = 22;
    private static final int ROW_H = 16;
    private static final int CONTENT_FOOTER_H = 16;

    /** Panel position persists across reopens. */
    private static int sPanelX;
    private static int sPanelY;
    private static boolean sPlaced;

    private final TTFFontRenderer titleFont = M3.title();
    private final TTFFontRenderer bodyFont = M3.body();
    private final TTFFontRenderer smallFont = M3.labelSmall();

    private final List<Category> categories = new ArrayList<>();
    private final Set<Module> expanded = new HashSet<>();
    private final Map<Object, Float> anim = new HashMap<>();
    private final long openedAt = Util.getMillis();

    private Category activeCategory;
    private String query = "";
    private boolean searchFocused;
    private Module binding;

    private float scroll;
    private float scrollTarget;
    private int contentHeight;
    private float slideT = 1f;

    private int mouseX;
    private int mouseY;
    private boolean draggingPanel;
    private int dragOffX;
    private int dragOffY;

    /* animation-map keys that must not collide with each other */
    private record ExpandKey(Module m) {
    }

    private record HoverKey(Object o) {
    }

    /* click zones */
    private record Zone(int x, int y, int w, int h, Runnable click, IntConsumer drag) {
    }

    private final List<Zone> zones = new ArrayList<>();
    private IntConsumer activeDrag;
    private int zClipY0 = Integer.MIN_VALUE;
    private int zClipY1 = Integer.MAX_VALUE;

    public ClickGuiScreen() {
        super(Component.literal("ClickGUI"));
        for (Category c : Category.values()) {
            if (ModuleManager.instance.getModules().stream().anyMatch(m -> m.getCategory() == c)) {
                categories.add(c);
            }
        }
        activeCategory = categories.isEmpty() ? Category.Render : categories.get(0);
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
        if (!sPlaced) {
            sPlaced = true;
            sPanelX = Math.max(8, (this.width - PANEL_W) / 2);
            sPanelY = Math.max(8, (this.height - PANEL_H) / 2);
        } else {
            sPanelX = clamp(sPanelX, 0, Math.max(0, this.width - PANEL_W));
            sPanelY = clamp(sPanelY, 0, Math.max(0, this.height - PANEL_H));
        }
    }

    /* ================================================================== */
    /*  render                                                            */
    /* ================================================================== */

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        MonetTheme.update();
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        zones.clear();

        // Frosted glass: blur the world/panorama behind the panel, then scrim.
        M3.blurBehind(g);
        extractTransparentBackground(g);

        int x = sPanelX;
        int y = sPanelY;

        // Open animation: scale in around the panel center (M3 emphasized
        // decelerate: 250ms cubic ease-out). Zones use unscaled coords; the
        // window is too short for that to be perceivable.
        float open = AnimationUtils.easeOutCubic((Util.getMillis() - openedAt) / 250f);
        boolean scaled = open < 0.995f;
        if (scaled) {
            float s = 0.92f + 0.08f * open;
            float cx = x + PANEL_W / 2f;
            float cy = y + PANEL_H / 2f;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(s, s);
            g.pose().translate(-cx, -cy);
        }
        // Per-frame scroll animation (tick() is only 20Hz – too steppy).
        scroll = AnimationUtils.easeExp(scroll, scrollTarget, 12f);
        slideT = animTo("slide", 1f, 5f);

        M3.shadow(g, x, y, PANEL_W, PANEL_H, M3.SHAPE_L);
        M3.roundRect(g, x, y, PANEL_W, PANEL_H, M3.SHAPE_L, M3.SURFACE_CONTAINER_LOW);
        M3.roundRect(g, x, y, PANEL_W, HEADER_H, M3.SHAPE_L, M3.SURFACE_CONTAINER,
                true, true, false, false);
        M3.roundRect(g, x, y + HEADER_H, RAIL_W, PANEL_H - HEADER_H, M3.SHAPE_L, M3.SURFACE_CONTAINER,
                false, false, true, false);
        M3.divider(g, x, y + HEADER_H - 1, PANEL_W);
        fill(g, x + RAIL_W, y + HEADER_H, 1, PANEL_H - HEADER_H, M3.OUTLINE_VARIANT);

        drawHeader(g, x, y);
        drawRail(g, x, y);

        int cx0 = x + RAIL_W + 1;
        int cy0 = y + HEADER_H;
        int cw = PANEL_W - RAIL_W - 1;
        int ch = PANEL_H - HEADER_H - CONTENT_FOOTER_H;
        g.enableScissor(cx0, cy0, cx0 + cw, cy0 + ch);
        zClipY0 = cy0;
        zClipY1 = cy0 + ch;
        try {
            drawContent(g, cx0, cy0, cw, ch);
        } finally {
            zClipY0 = Integer.MIN_VALUE;
            zClipY1 = Integer.MAX_VALUE;
            g.disableScissor();
        }

        // Hint INSIDE the panel: at small GUI sizes the panel sits flush with
        // the screen bottom, so anything below it would be invisible.
        String hint = binding != null ? Lang.tr("gui.hint_binding") : Lang.tr("gui.hint_close");
        float hw = smallFont.width(hint);
        int hx = (int) (x + PANEL_W - hw - 10);
        int hy = y + PANEL_H - 13;
        M3.roundRect(g, hx - 4, hy - 1, (int) hw + 8, 12, M3.pill(12),
                M3.withAlpha(M3.SURFACE_CONTAINER, 0xD8));
        smallFont.drawString(g, hint, hx, hy, binding != null ? M3.PRIMARY : M3.ON_SURFACE_VARIANT);

        if (scaled) {
            g.pose().popMatrix();
        }
    }

    private void drawHeader(GuiGraphicsExtractor g, int x, int y) {
        Icons.drawCentered(g, Icons.TUNE, 10, x + 13, y + HEADER_H / 2f, M3.ON_SURFACE);
        titleFont.drawString(g, Lang.tr("gui.title"), x + 24,
                y + (HEADER_H - titleFont.lineHeight()) / 2f, M3.ON_SURFACE);

        // Search: outlined field, OUTLINE at rest / PRIMARY when focused.
        int sw = 100;
        int sh = 14;
        int sx = x + PANEL_W - sw - 44;
        int sy = y + (HEADER_H - sh) / 2;
        M3.outlinedRoundRect(g, sx, sy, sw, sh, M3.pill(sh),
                M3.SURFACE_CONTAINER_LOW, searchFocused ? M3.PRIMARY : M3.OUTLINE);
        Icons.drawCentered(g, Icons.SEARCH, 8, sx + 9, sy + sh / 2f, M3.ON_SURFACE_VARIANT);
        String shown = query.isEmpty() && !searchFocused
                ? Lang.tr("gui.search")
                : query + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        smallFont.drawString(g, shown, sx + 17, sy + (sh - smallFont.lineHeight()) / 2f,
                query.isEmpty() && !searchFocused ? M3.ON_SURFACE_VARIANT : M3.ON_SURFACE);
        addZone(sx, sy, sw, sh, () -> searchFocused = true, null);

        // Client-language toggle (independent of Minecraft's language).
        iconButton(g, x + PANEL_W - 38, y + (HEADER_H - 14) / 2, Icons.TRANSLATE,
                Lang::cycle);
        // Close.
        iconButton(g, x + PANEL_W - 19, y + (HEADER_H - 14) / 2, Icons.CLOSE, this::onClose);

        // Everything left of the search field drags the panel.
        int dragW = Math.max(10, sx - x - 4);
        addZone(x, y, dragW, HEADER_H, () -> {
            draggingPanel = true;
            dragOffX = mouseX - sPanelX;
            dragOffY = mouseY - sPanelY;
        }, null);
    }

    /** Standard M3 icon button: bare icon, animated state-layer circle on hover. */
    private void iconButton(GuiGraphicsExtractor g, int x, int y, String icon, Runnable action) {
        boolean hover = isOver(x, y, 14, 14);
        float t = animTo(new HoverKey(icon), hover ? 1f : 0f, 12f);
        if (t > 0.01f) {
            M3.roundRect(g, x, y, 14, 14, M3.pill(14),
                    M3.stateLayer(M3.ON_SURFACE, (int) (M3.STATE_HOVER * t)));
        }
        Ripple.draw(g, "ib:" + icon, x, y, 14, 14, M3.ON_SURFACE);
        Icons.drawCentered(g, icon, 9, x + 7, y + 7,
                M3.lerp(M3.ON_SURFACE_VARIANT, M3.ON_SURFACE, t));
        addZone(x, y, 14, 14, () -> {
            Ripple.press("ib:" + icon, mouseX, mouseY);
            action.run();
        }, null);
    }

    private void drawRail(GuiGraphicsExtractor g, int x, int y) {
        boolean searching = !query.isEmpty();
        int iy = y + HEADER_H + 6;
        for (Category c : categories) {
            boolean active = !searching && c == activeCategory;
            boolean hover = isOver(x + 3, iy, RAIL_W - 6, 26);
            float t = animTo(c, active ? 1f : 0f, 10f);

            if (t > 0.01f) {
                M3.roundRect(g, x + 5, iy, RAIL_W - 10, 16, M3.pill(16),
                        M3.fade(M3.SECONDARY_CONTAINER, t));
            }
            if (hover) {
                // hover layer applies to the active destination too, per M3
                M3.roundRect(g, x + 5, iy, RAIL_W - 10, 16, M3.pill(16),
                        M3.stateLayer(t > 0.5f ? M3.ON_SECONDARY_CONTAINER : M3.ON_SURFACE,
                                M3.STATE_HOVER));
            }
            Ripple.draw(g, c, x + 5, iy, RAIL_W - 10, 16, M3.ON_SECONDARY_CONTAINER);
            int iconColor = M3.lerp(M3.ON_SURFACE_VARIANT, M3.ON_SECONDARY_CONTAINER, t);
            Icons.drawCentered(g, categoryIcon(c), 10, x + RAIL_W / 2f, iy + 8, iconColor);
            smallFont.drawCenteredString(g, categoryLabel(c), x + RAIL_W / 2f, iy + 17,
                    active ? M3.ON_SURFACE : M3.ON_SURFACE_VARIANT);

            Category cat = c;
            addZone(x + 3, iy, RAIL_W - 6, 26, () -> {
                Ripple.press(cat, mouseX, mouseY);
                if (activeCategory != cat || !query.isEmpty()) {
                    activeCategory = cat;
                    query = "";
                    scrollTarget = 0;
                    anim.put("slide", 0f); // restart the content slide-in
                }
            }, null);
            // 28px pitch: six destinations must fit PANEL_H=200 minus header.
            iy += 28;
        }
    }

    private void drawContent(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        List<Module> shown = new ArrayList<>();
        for (Module m : ModuleManager.instance.getModules()) {
            if (!query.isEmpty()) {
                if (m.getName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                    shown.add(m);
                }
            } else if (m.getCategory() == activeCategory) {
                shown.add(m);
            }
        }

        // Category-switch slide-in (cursor offset, so click zones follow).
        int slideX = (int) ((1f - AnimationUtils.easeOutCubic(slideT)) * 24);

        int cursor = y + PAD - (int) scroll;
        int cardX = x + PAD + slideX;
        int cardW = w - PAD * 2 - 3; // room for the scrollbar

        for (Module m : shown) {
            cursor = drawModuleCard(g, m, cardX, cursor, cardW, y, y + h);
            cursor += 4;
        }
        if (shown.isEmpty()) {
            bodyFont.drawCenteredString(g, Lang.tr("gui.no_match"), x + w / 2f, y + 30,
                    M3.ON_SURFACE_VARIANT);
        }

        contentHeight = cursor + (int) scroll - y + PAD;
        int viewH = h;
        scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, contentHeight - viewH)));
        if (contentHeight > viewH) {
            float ratio = viewH / (float) contentHeight;
            int barH = Math.max(12, (int) (viewH * ratio));
            int barY = y + 2 + (int) ((viewH - 4 - barH) * (scroll / Math.max(1f, contentHeight - viewH)));
            M3.roundRect(g, x + w - 4, barY, 2, barH, 1, M3.ON_SURFACE_VARIANT);
        }
    }

    /** Draws one module card (expansion animated); returns the next y. */
    private int drawModuleCard(GuiGraphicsExtractor g, Module m, int x, int y, int w,
                               int clipTop, int clipBottom) {
        List<Value<?>> values = visibleValues(m);
        boolean open = expanded.contains(m) && !values.isEmpty();
        float expT = animTo(new ExpandKey(m), open ? 1f : 0f, 8f);
        int fullExtra = valuesHeight(values) + 4;
        int extra = Math.round(easeOut(expT) * fullExtra);
        int cardH = CARD_H + (values.isEmpty() ? 0 : extra);

        if (y + cardH > clipTop && y < clipBottom) {
            boolean hover = isOver(x, y, w, CARD_H);
            float ht = animTo(new HoverKey(m), hover ? 1f : 0f, 12f);
            int bg = M3.layered(M3.SURFACE_CONTAINER_HIGH, M3.ON_SURFACE, (int) (M3.STATE_HOVER * ht));
            M3.roundRect(g, x, y, w, cardH, M3.SHAPE_M, bg);
            Ripple.draw(g, m, x, y, w, CARD_H, M3.ON_SURFACE);

            // Card-body zone FIRST: the hit test is last-added-wins, so the
            // chip/switch/chevron zones added below must come after it.
            if (!values.isEmpty()) {
                addZone(x, y, w, CARD_H, () -> {
                    Ripple.press(m, mouseX, mouseY);
                    toggleExpanded(m);
                }, null);
            }

            // leading icon + name
            Icons.drawCentered(g, moduleIcon(m), 10, x + 13, y + CARD_H / 2f,
                    m.isEnable() ? M3.PRIMARY : M3.ON_SURFACE_VARIANT);
            bodyFont.drawString(g, m.getName(), x + 24,
                    y + (CARD_H - bodyFont.lineHeight()) / 2f, M3.ON_SURFACE);

            drawBindChip(g, m, x, y, w);
            drawSwitch(g, x + w - 34, y + (CARD_H - 12) / 2, m, m.isEnable(),
                    () -> {
                        Ripple.press(m, mouseX, mouseY);
                        m.setEnable(!m.isEnable());
                    });

            if (!values.isEmpty()) {
                int ex = x + w - 58;
                int ey = y + (CARD_H - 12) / 2;
                Object chevronKey = new HoverKey(m.getName() + ":expand:ripple");
                Ripple.draw(g, chevronKey, ex, ey, 12, 12, M3.ON_SURFACE_VARIANT);
                Icons.drawCentered(g, open ? Icons.EXPAND_LESS : Icons.EXPAND_MORE, 9,
                        ex + 6, ey + 6, M3.ON_SURFACE_VARIANT);
                addZone(ex, ey, 12, 12, () -> {
                    Ripple.press(chevronKey, mouseX, mouseY);
                    toggleExpanded(m);
                }, null);
            }

            if (extra > 2) {
                // Animated reveal: clip the settings rows to the growing card.
                g.enableScissor(x, y + CARD_H, x + w, y + CARD_H + extra);
                int zc0 = zClipY0;
                int zc1 = zClipY1;
                zClipY0 = Math.max(zClipY0, y + CARD_H);
                zClipY1 = Math.min(zClipY1, y + CARD_H + (expT > 0.9f ? extra : 0));
                try {
                    int vy = y + CARD_H;
                    M3.divider(g, x + 8, vy, w - 16);
                    vy += 3;
                    for (Value<?> v : values) {
                        vy = drawValueRow(g, v, x + 10, vy, w - 20);
                    }
                } finally {
                    zClipY0 = zc0;
                    zClipY1 = zc1;
                    g.disableScissor();
                }
            }
        }
        return y + cardH;
    }

    /**
     * Keybind chip: keyboard icon + key name; while listening it turns
     * primary-container with a pulsing primary ring. Clicking anywhere else
     * cancels listening.
     */
    private void drawBindChip(GuiGraphicsExtractor g, Module m, int x, int y, int w) {
        boolean listening = binding == m;
        String keyLabel = listening ? Lang.tr("gui.bind_listening") : keyName(m.getKeyCode());
        int chipW = Math.max((int) smallFont.width(keyLabel) + 22, 34);
        int chipX = x + w - 66 - chipW;
        int chipY = y + (CARD_H - 13) / 2;
        boolean hover = isOver(chipX, chipY, chipW, 13);
        float ht = animTo(new HoverKey(m.getName() + ":bind"), hover ? 1f : 0f, 12f);

        int bg;
        int fg;
        if (listening) {
            bg = M3.PRIMARY_CONTAINER;
            fg = M3.ON_PRIMARY_CONTAINER;
            // pulsing primary ring while waiting for a key
            int pulse = (int) (90 + 80 * Math.sin(Util.getMillis() / 180.0));
            M3.roundRect(g, chipX - 2, chipY - 2, chipW + 4, 17, M3.pill(17),
                    M3.withAlpha(M3.PRIMARY, pulse));
        } else {
            bg = M3.layered(M3.SURFACE_CONTAINER_HIGHEST, M3.ON_SURFACE, (int) (M3.STATE_HOVER * ht));
            fg = M3.ON_SURFACE_VARIANT;
        }
        M3.roundRect(g, chipX, chipY, chipW, 13, M3.pill(13), bg);
        Object rippleKey = new HoverKey(m.getName() + ":bind:ripple");
        Ripple.draw(g, rippleKey, chipX, chipY, chipW, 13, fg);
        Icons.drawCentered(g, Icons.KEYBOARD, 8, chipX + 9, chipY + 6.5f, fg);
        smallFont.drawString(g, keyLabel, chipX + 16,
                chipY + (13 - smallFont.lineHeight()) / 2f, fg);
        addZone(chipX, chipY, chipW, 13, () -> {
            Ripple.press(rippleKey, mouseX, mouseY);
            binding = listening ? null : m;
        }, null);
    }

    private int drawValueRow(GuiGraphicsExtractor g, Value<?> v, int x, int y, int w) {
        float labelY = y + (ROW_H - smallFont.lineHeight()) / 2f;
        smallFont.drawString(g, v.getName(), x + 2, labelY, M3.ON_SURFACE_VARIANT);

        if (v instanceof Option opt) {
            drawSwitch(g, x + w - 24, y + (ROW_H - 12) / 2, opt, opt.getValue(),
                    () -> opt.setValue(!opt.getValue()));
            return y + ROW_H;
        }
        if (v instanceof Numbers num) {
            String valText = num.isInteger()
                    ? String.valueOf(num.intValue())
                    : String.format(Locale.ROOT, "%.2f", num.getValue());
            // Fixed-width value column: the track geometry must not depend on
            // the value text, or it would shift mid-drag under the cursor.
            int valueCol = 26;
            smallFont.drawString(g, valText, x + w - smallFont.width(valText) - 2, labelY, M3.ON_SURFACE);

            int trackX = x + 70;
            int trackW = w - 70 - valueCol - 8;
            int trackY = y + ROW_H / 2 - 1;
            if (trackW > 20 && num.getMaximum() > num.getMinimum()) {
                float ratio = (float) ((num.getValue() - num.getMinimum())
                        / (num.getMaximum() - num.getMinimum()));
                fill(g, trackX, trackY, trackW, 2, M3.SURFACE_CONTAINER_HIGHEST);
                int filled = (int) (trackW * ratio);
                if (filled > 0) fill(g, trackX, trackY, filled, 2, M3.PRIMARY);
                M3.roundRect(g, trackX + filled - 1, trackY - 3, 3, 8, 1, M3.PRIMARY);

                int fx = trackX;
                int fw = trackW;
                addZone(trackX - 2, y, trackW + 4, ROW_H,
                        () -> dragSlider(num, fx, fw, mouseX),
                        mx -> dragSlider(num, fx, fw, mx));
            }
            return y + ROW_H;
        }
        if (v instanceof Mode mode) {
            addZone(x, y, w, ROW_H, () -> cycleMode(mode), null);
            int cx = x + w;
            for (int i = mode.getModes().length - 1; i >= 0; i--) {
                String option = mode.getModes()[i];
                boolean sel = mode.isCurrentMode(option);
                int chipW = (int) smallFont.width(option) + (sel ? 16 : 12);
                cx -= chipW + 3;
                int chipY = y + (ROW_H - 13) / 2;
                boolean hover = isOver(cx, chipY, chipW, 13);
                int bg = sel ? M3.SECONDARY_CONTAINER
                        : hover
                        ? M3.layered(M3.SURFACE_CONTAINER_HIGHEST, M3.ON_SURFACE, M3.STATE_HOVER)
                        : M3.SURFACE_CONTAINER_HIGHEST;
                M3.roundRect(g, cx, chipY, chipW, 13, M3.SHAPE_S, bg);
                Object rippleKey = new HoverKey(v.getName() + ":mode:" + option);
                Ripple.draw(g, rippleKey, cx, chipY, chipW, 13,
                        sel ? M3.ON_SECONDARY_CONTAINER : M3.ON_SURFACE);
                if (sel) {
                    Icons.drawCentered(g, Icons.CHECK, 7, cx + 8, chipY + 6.5f, M3.ON_SECONDARY_CONTAINER);
                }
                smallFont.drawString(g, option, cx + (sel ? 14 : 6),
                        chipY + (13 - smallFont.lineHeight()) / 2f,
                        sel ? M3.ON_SECONDARY_CONTAINER : M3.ON_SURFACE_VARIANT);
                String opt = option;
                addZone(cx, chipY, chipW, 13, () -> {
                    Ripple.press(rippleKey, mouseX, mouseY);
                    mode.setMode(opt);
                }, null);
            }
            return y + ROW_H;
        }
        return y + ROW_H;
    }

    private void cycleMode(Mode mode) {
        String[] modes = mode.getModes();
        if (modes.length == 0) {
            return;
        }
        for (int i = 0; i < modes.length; i++) {
            if (mode.isCurrentMode(modes[i])) {
                mode.setMode(modes[(i + 1) % modes.length]);
                return;
            }
        }
        mode.setMode(modes[0]);
    }

    /** M3 switch: 24×12 pill track, animated thumb; container-role change on toggle. */
    private void drawSwitch(GuiGraphicsExtractor g, int x, int y, Object key,
                            boolean checked, Runnable toggle) {
        float t = animTo(key, checked ? 1f : 0f, 10f);
        int track = M3.lerp(M3.SURFACE_CONTAINER_HIGHEST, M3.PRIMARY, t);
        int ring = M3.lerp(M3.OUTLINE, M3.PRIMARY, t);
        M3.outlinedRoundRect(g, x, y, 24, 12, M3.pill(12), track, ring);
        Object rippleKey = new HoverKey(key.toString() + ":switch:ripple");
        Ripple.draw(g, rippleKey, x - 2, y - 2, 28, 16,
                checked ? M3.ON_PRIMARY : M3.ON_SURFACE);
        int thumbSize = Math.round(6 + 2 * t);
        float thumbX = x + 3 + (24 - 6 - thumbSize / 2f - 3) * t;
        float thumbY = y + 6 - thumbSize / 2f;
        // M3 switch hover: a state-layer halo around the thumb.
        boolean hover = isOver(x - 2, y - 2, 28, 16);
        float ht = animTo(new HoverKey(key), hover ? 1f : 0f, 12f);
        if (ht > 0.01f) {
            int halo = 14;
            M3.roundRect(g, Math.round(thumbX + thumbSize / 2f - halo / 2f),
                    Math.round(thumbY + thumbSize / 2f - halo / 2f), halo, halo, M3.pill(halo),
                    M3.stateLayer(checked ? M3.PRIMARY : M3.ON_SURFACE, (int) (M3.STATE_HOVER * ht)));
        }
        int thumb = M3.lerp(M3.OUTLINE, M3.ON_PRIMARY, t);
        M3.roundRect(g, Math.round(thumbX), Math.round(thumbY), thumbSize, thumbSize,
                M3.pill(thumbSize), thumb);
        addZone(x - 2, y - 2, 28, 16, () -> {
            Ripple.press(rippleKey, mouseX, mouseY);
            toggle.run();
        }, null);
    }

    /* ================================================================== */
    /*  behavior                                                          */
    /* ================================================================== */

    private void toggleExpanded(Module m) {
        if (expanded.contains(m)) expanded.remove(m);
        else expanded.add(m);
    }

    private void dragSlider(Numbers num, int trackX, int trackW, int mx) {
        double ratio = Math.max(0, Math.min(1, (mx - trackX) / (double) trackW));
        double raw = num.getMinimum() + ratio * (num.getMaximum() - num.getMinimum());
        double inc = num.getIncrement();
        if (inc > 0) {
            raw = num.getMinimum() + Math.round((raw - num.getMinimum()) / inc) * inc;
        }
        num.setValue(raw);
    }

    private List<Value<?>> visibleValues(Module m) {
        List<Value<?>> out = new ArrayList<>();
        for (Value<?> v : m.getValues()) {
            if (v.isVisitable()) out.add(v);
        }
        return out;
    }

    private int valuesHeight(List<Value<?>> values) {
        return values.size() * ROW_H + 3;
    }

    private static String categoryLabel(Category c) {
        return switch (c) {
            case Combat -> Lang.tr("cat.combat");
            case Movement -> Lang.tr("cat.movement");
            case Render -> Lang.tr("cat.render");
            case World -> Lang.tr("cat.world");
            case Player -> Lang.tr("cat.player");
            case Misc -> Lang.tr("cat.misc");
        };
    }

    private static String categoryIcon(Category c) {
        return switch (c) {
            case Combat -> Icons.COMBAT;
            case Movement -> Icons.MOVEMENT;
            case Render -> Icons.RENDER;
            case World -> Icons.WORLD;
            case Player -> Icons.PLAYER;
            case Misc -> Icons.MISC;
        };
    }

    private static String moduleIcon(Module m) {
        return switch (m.getName()) {
            case "Sprint" -> Icons.SPEED;
            case "Speed" -> Icons.BOLT;
            case "Fly" -> Icons.MOVEMENT;
            case "Interface" -> Icons.WIDGETS;
            case "InventoryDisplay" -> Icons.INVENTORY;
            case "MusicPlayer" -> Icons.MUSIC_NOTE;
            case "ClickGUI" -> Icons.TUNE;
            case "NoCommands" -> Icons.CHAT;
            default -> Icons.VISIBILITY;
        };
    }

    private static String keyName(int keyCode) {
        if (keyCode == InputConstants.UNKNOWN.getValue()) return Lang.tr("gui.bind_none");
        String name = InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString();
        return name.length() > 10 ? name.substring(0, 10) : name;
    }

    /* ================================================================== */
    /*  input                                                             */
    /* ================================================================== */

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        if (Util.getMillis() - openedAt < 250) {
            return true;
        }
        if (binding != null) {
            // Clicking anywhere cancels key listening.
            binding = null;
            return true;
        }
        if (mx < sPanelX || mx > sPanelX + PANEL_W || my < sPanelY || my > sPanelY + PANEL_H) {
            onClose();
            return true;
        }
        searchFocused = false;
        for (int i = zones.size() - 1; i >= 0; i--) {
            Zone z = zones.get(i);
            if (mx >= z.x && mx <= z.x + z.w && my >= z.y && my <= z.y + z.h) {
                if (z.drag != null) {
                    activeDrag = z.drag;
                }
                if (z.click != null) {
                    z.click.run();
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingPanel = false;
        activeDrag = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingPanel) {
            sPanelX = clamp((int) event.x() - dragOffX, 0, Math.max(0, this.width - PANEL_W));
            sPanelY = clamp((int) event.y() - dragOffY, 0, Math.max(0, this.height - PANEL_H));
            return true;
        }
        if (activeDrag != null) {
            activeDrag.accept((int) event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= sPanelX + RAIL_W && mx <= sPanelX + PANEL_W
                && my >= sPanelY + HEADER_H && my <= sPanelY + PANEL_H - CONTENT_FOOTER_H) {
            scrollTarget = Math.max(0, scrollTarget + (float) (-scrollY * 20));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (binding != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                binding.setKeyCode(InputConstants.UNKNOWN.getValue());
            } else {
                binding.setKeyCode(key);
            }
            binding = null;
            return true;
        }

        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
            return true;
        }

        // RSHIFT toggles the GUI closed, but only after a short grace period –
        // the opening key press (and its GLFW repeats while held) must not
        // instantly close the screen it just opened.
        if (key == GLFW.GLFW_KEY_ESCAPE
                || (key == GLFW.GLFW_KEY_RIGHT_SHIFT && Util.getMillis() - openedAt > 300)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchFocused && event.isAllowedChatCharacter()) {
            query += event.codepointAsString();
            scrollTarget = 0;
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        FileManager.instance.save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(null);
        }
    }

    /* ================================================================== */
    /*  helpers                                                           */
    /* ================================================================== */

    /** Non-linear (exponential decelerate) animation toward target. */
    private float animTo(Object key, float target, float speedPerSec) {
        float now = anim.getOrDefault(key, target);
        float next = AnimationUtils.easeExp(now, target, speedPerSec);
        anim.put(key, next);
        return next;
    }

    private void addZone(int x, int y, int w, int h, Runnable click, IntConsumer drag) {
        int y0 = Math.max(y, zClipY0);
        int y1 = Math.min(y + h, zClipY1);
        if (y1 <= y0) return;
        zones.add(new Zone(x, y0, w, y1 - y0, click, drag));
    }

    private boolean isOver(int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, argb);
    }

    private static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
