package dev.naominet.listclient.ui;

import com.mojang.authlib.GameProfile;
import dev.naominet.listclient.auth.MicrosoftAuth;
import dev.naominet.listclient.manager.AccountManager;
import dev.naominet.listclient.manager.AccountManager.Account;
import dev.naominet.listclient.ui.theme.Icons;
import dev.naominet.listclient.ui.theme.M3;
import dev.naominet.listclient.ui.theme.MonetTheme;
import dev.naominet.listclient.ui.theme.Ripple;
import dev.naominet.listclient.utils.AnimationUtils;
import dev.naominet.listclient.utils.Lang;
import dev.naominet.listclient.utils.font.TTFFontRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Account manager – Material 3 dialog-style panel over the parent screen.
 * <p>
 * Lists the stored accounts (offline and Microsoft) as cards with a type
 * chip, switches the running session on click via
 * {@link AccountManager#switchTo}, adds offline accounts through an outlined
 * text field + filled button and Microsoft accounts through the OAuth
 * device-code flow ({@link MicrosoftAuth}) with an in-panel user-code
 * overlay. Same immediate-mode patterns, i18n and motion (scale-in open,
 * non-linear hovers) as the rest of the client UI.
 */
public class AccountManagerScreen extends Screen {

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 232;
    private static final int HEADER_H = 24;
    private static final int PAD = 8;
    private static final int ROW_H = 26;
    private static final int BTN_H = 16;

    private final Screen parent;
    private final TTFFontRenderer titleFont = M3.title();
    private final TTFFontRenderer bodyFont = M3.body();
    private final TTFFontRenderer smallFont = M3.labelSmall();

    private final Map<Object, Float> anim = new HashMap<>();
    private final Map<String, Supplier<PlayerSkin>> skins = new HashMap<>();
    private final long openedAt = Util.getMillis();

    private String input = "";
    private boolean inputFocused;
    private String error = "";
    private float scroll;
    private float scrollTarget;

    // Microsoft device-code flow state (all mutated on the render thread).
    private MicrosoftAuth msAuth;
    private boolean msPending;
    private String msUserCode;
    private String msVerifyUri;
    private long copiedAt;

    private int mouseX;
    private int mouseY;
    private int panelX;
    private int panelY;

    private record Zone(int x, int y, int w, int h, Runnable click) {
    }

    private final List<Zone> zones = new ArrayList<>();
    private int zClipY0 = Integer.MIN_VALUE;
    private int zClipY1 = Integer.MAX_VALUE;

    public AccountManagerScreen(Screen parent) {
        super(Component.literal("Accounts"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = Math.max(8, (this.height - PANEL_H) / 2);
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

        // Per-frame scroll animation (tick() is only 20Hz – too steppy).
        scroll = AnimationUtils.easeExp(scroll, scrollTarget, 12f);

        if (parent != null) {
            parent.extractRenderState(g, -1, -1, delta);
        } else {
            extractTransparentBackground(g);
        }
        // Frosted backdrop: blur whatever is behind, then a scrim on top.
        M3.blurBehind(g);
        g.fill(0, 0, this.width, this.height, M3.withAlpha(M3.SCRIM, 0x66));

        float open = AnimationUtils.easeOutCubic((Util.getMillis() - openedAt) / 250f);
        boolean scaled = open < 0.995f;
        if (scaled) {
            float s = 0.92f + 0.08f * open;
            float cx = panelX + PANEL_W / 2f;
            float cy = panelY + PANEL_H / 2f;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(s, s);
            g.pose().translate(-cx, -cy);
        }

        int x = panelX;
        int y = panelY;
        M3.shadow(g, x, y, PANEL_W, PANEL_H, M3.SHAPE_XL);
        M3.roundRect(g, x, y, PANEL_W, PANEL_H, M3.SHAPE_XL, M3.SURFACE_CONTAINER);

        // header
        Icons.drawCentered(g, Icons.PERSON, 10, x + 14, y + HEADER_H / 2f, M3.ON_SURFACE);
        titleFont.drawString(g, Lang.tr("acct.title"), x + 24,
                y + (HEADER_H - titleFont.lineHeight()) / 2f, M3.ON_SURFACE);
        iconButton(g, x + PANEL_W - 19, y + (HEADER_H - 14) / 2, Icons.CLOSE, "close", this::onClose);
        M3.divider(g, x + PAD, y + HEADER_H - 1, PANEL_W - PAD * 2);

        // account list (scrollable)
        int listY = y + HEADER_H + 3;
        int listH = PANEL_H - HEADER_H - 3 - 30 - PAD - (BTN_H + 4);
        g.enableScissor(x, listY, x + PANEL_W, listY + listH);
        zClipY0 = listY;
        zClipY1 = listY + listH;
        try {
            drawAccounts(g, x, listY, listH);
        } finally {
            zClipY0 = Integer.MIN_VALUE;
            zClipY1 = Integer.MAX_VALUE;
            g.disableScissor();
        }

        drawMsButton(g, x, listY + listH + 2);
        drawAddRow(g, x, y + PANEL_H - 30 - PAD + 4);

        if (!error.isEmpty()) {
            smallFont.drawString(g, error, x + PAD + 2, y + PANEL_H - 11, M3.ERROR);
        }

        if (msPending) {
            drawMsOverlay(g, x, y);
        }

        if (scaled) {
            g.pose().popMatrix();
        }
    }

    private void drawAccounts(GuiGraphicsExtractor g, int x, int listY, int listH) {
        List<Account> accounts = AccountManager.instance.getAccounts();
        String current = AccountManager.instance.currentName();

        int cursor = listY + 2 - (int) scroll;
        int cardX = x + PAD;
        int cardW = PANEL_W - PAD * 2;

        if (accounts.isEmpty()) {
            bodyFont.drawCenteredString(g, Lang.tr("acct.empty"), x + PANEL_W / 2f,
                    listY + 26, M3.ON_SURFACE_VARIANT);
        }

        for (Account account : accounts) {
            boolean active = account.name().equals(current);
            boolean ms = account.microsoft();
            String key = account.type() + ":" + account.uuid();
            boolean hover = isOver(cardX, cursor, cardW, ROW_H - 3)
                    && mouseY >= zClipY0 && mouseY <= zClipY1;
            float ht = animTo("h:" + key, hover ? 1f : 0f, 12f);

            int bg = active ? M3.SECONDARY_CONTAINER
                    : M3.layered(M3.SURFACE_CONTAINER_HIGH, M3.ON_SURFACE, (int) (M3.STATE_HOVER * ht));
            M3.roundRect(g, cardX, cursor, cardW, ROW_H - 3, M3.SHAPE_M, bg);

            int onColor = active ? M3.ON_SECONDARY_CONTAINER : M3.ON_SURFACE;
            Ripple.draw(g, account, cardX, cursor, cardW, ROW_H - 3, onColor);
            PlayerSkin skin = skinFor(account).get();
            if (skin != null) {
                PlayerFaceExtractor.extractRenderState(g, skin, cardX + 5, cursor + (ROW_H - 3 - 14) / 2, 14);
            }
            bodyFont.drawString(g, account.name(), cardX + 25, cursor + 3f, onColor);

            // type chip: icon + label after the name
            int chipColor = active ? M3.ON_SECONDARY_CONTAINER
                    : (ms ? M3.PRIMARY : M3.ON_SURFACE_VARIANT);
            String typeIcon = ms ? Icons.CLOUD : Icons.PERSON;
            Icons.draw(g, typeIcon, 7, cardX + 25, cursor + 13, chipColor);
            smallFont.drawString(g, Lang.tr(ms ? "acct.type_ms" : "acct.type_offline"),
                    cardX + 25 + 9, cursor + 13f, chipColor);

            if (active) {
                Icons.drawCentered(g, Icons.CHECK, 9, cardX + cardW - 34, cursor + (ROW_H - 3) / 2f,
                        M3.ON_SECONDARY_CONTAINER);
                smallFont.drawString(g, Lang.tr("acct.current"),
                        cardX + cardW - 28, cursor + (ROW_H - 3 - smallFont.lineHeight()) / 2f,
                        M3.ON_SECONDARY_CONTAINER);
            } else {
                Account a = account;
                addZone(cardX, cursor, cardW - 22, ROW_H - 3, () -> {
                    Ripple.press(a, mouseX, mouseY);
                    AccountManager.instance.switchTo(a);
                    error = "";
                });
            }

            // delete icon button
            int dx = cardX + cardW - 17;
            int dy = cursor + (ROW_H - 3 - 12) / 2;
            boolean dHover = isOver(dx, dy, 12, 12) && mouseY >= zClipY0 && mouseY <= zClipY1;
            Object deleteKey = "delete:" + account.uuid();
            Ripple.draw(g, deleteKey, dx - 2, dy - 2, 16, 16,
                    active ? M3.ON_SECONDARY_CONTAINER : M3.ON_SURFACE_VARIANT);
            Icons.drawCentered(g, Icons.CLOSE, 8, dx + 6, dy + 6,
                    dHover ? M3.ERROR : (active ? M3.ON_SECONDARY_CONTAINER : M3.ON_SURFACE_VARIANT));
            Account a = account;
            addZone(dx - 2, dy - 2, 16, 16, () -> {
                Ripple.press(deleteKey, mouseX, mouseY);
                AccountManager.instance.remove(a);
            });

            cursor += ROW_H;
        }

        int contentH = accounts.size() * ROW_H + 4;
        scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, contentH - listH)));
    }

    /** Full-width filled "Microsoft Login" button above the offline add row. */
    private void drawMsButton(GuiGraphicsExtractor g, int x, int y) {
        int bx = x + PAD;
        int bw = PANEL_W - PAD * 2;
        boolean hover = !msPending && isOver(bx, y, bw, BTN_H);
        float ht = animTo("msbtn", hover ? 1f : 0f, 12f);
        int bg = M3.layered(M3.PRIMARY, M3.ON_PRIMARY, (int) (M3.STATE_HOVER * ht));
        M3.roundRect(g, bx, y, bw, BTN_H, M3.pill(BTN_H), bg);
        Ripple.draw(g, "msbtn", bx, y, bw, BTN_H, M3.ON_PRIMARY);

        String label = Lang.tr("acct.ms_login");
        float labelW = smallFont.width(label);
        float cx = bx + bw / 2f;
        Icons.drawCentered(g, Icons.CLOUD, 9, cx - labelW / 2f - 7, y + BTN_H / 2f, M3.ON_PRIMARY);
        smallFont.drawString(g, label, cx - labelW / 2f + 2,
                y + (BTN_H - smallFont.lineHeight()) / 2f, M3.ON_PRIMARY);
        addZone(bx, y, bw, BTN_H, () -> {
            Ripple.press("msbtn", mouseX, mouseY);
            startMsLogin();
        });
    }

    private void drawAddRow(GuiGraphicsExtractor g, int x, int y) {
        int fieldW = PANEL_W - PAD * 2 - 50;
        int fieldH = 16;
        int fx = x + PAD;
        M3.outlinedRoundRect(g, fx, y, fieldW, fieldH, M3.SHAPE_S,
                M3.SURFACE_CONTAINER_LOW, inputFocused ? M3.PRIMARY : M3.OUTLINE);
        String shown = input.isEmpty() && !inputFocused
                ? Lang.tr("acct.add_placeholder")
                : input + (inputFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        smallFont.drawString(g, shown, fx + 5, y + (fieldH - smallFont.lineHeight()) / 2f,
                input.isEmpty() && !inputFocused ? M3.ON_SURFACE_VARIANT : M3.ON_SURFACE);
        addZone(fx, y, fieldW, fieldH, () -> inputFocused = true);

        int bx = fx + fieldW + 6;
        int bw = PANEL_W - PAD * 2 - fieldW - 6;
        boolean hover = isOver(bx, y, bw, fieldH);
        float ht = animTo("addbtn", hover ? 1f : 0f, 12f);
        int bg = M3.layered(M3.PRIMARY, M3.ON_PRIMARY, (int) (M3.STATE_HOVER * ht));
        M3.roundRect(g, bx, y, bw, fieldH, M3.pill(fieldH), bg);
        Ripple.draw(g, "addbtn", bx, y, bw, fieldH, M3.ON_PRIMARY);
        smallFont.drawCenteredString(g, Lang.tr("acct.add"), bx + bw / 2f,
                y + (fieldH - smallFont.lineHeight()) / 2f, M3.ON_PRIMARY);
        addZone(bx, y, bw, fieldH, () -> {
            Ripple.press("addbtn", mouseX, mouseY);
            submitAdd();
        });
    }

    /** In-panel overlay while the device-code flow waits for the browser. */
    private void drawMsOverlay(GuiGraphicsExtractor g, int x, int y) {
        // Scrim over the whole panel; the zone swallows clicks under the card.
        M3.roundRect(g, x, y, PANEL_W, PANEL_H, M3.SHAPE_XL, M3.withAlpha(M3.SCRIM, 0xA0));
        addZone(x, y, PANEL_W, PANEL_H, () -> {
        });

        int cw = 196;
        int ch = 92;
        int cx = x + (PANEL_W - cw) / 2;
        int cy = y + (PANEL_H - ch) / 2;
        M3.shadow(g, cx, cy, cw, ch, M3.SHAPE_L);
        M3.roundRect(g, cx, cy, cw, ch, M3.SHAPE_L, M3.SURFACE_CONTAINER_HIGHEST);

        iconButton(g, cx + cw - 18, cy + 4, Icons.CLOSE, "mscancel", this::cancelMsLogin);

        float mid = cx + cw / 2f;
        smallFont.drawCenteredString(g, Lang.tr("acct.ms_pending"), mid, cy + 8, M3.ON_SURFACE_VARIANT);

        if (msUserCode == null) {
            // Still fetching the device code.
            int dots = 1 + (int) (System.currentTimeMillis() / 400) % 3;
            titleFont.drawCenteredString(g, "···".substring(0, dots),
                    mid, cy + 24, M3.ON_SURFACE_VARIANT);
            return;
        }

        titleFont.drawCenteredString(g, msUserCode, mid, cy + 21, M3.PRIMARY);
        smallFont.drawCenteredString(g, Lang.tr("acct.ms_enter_code"), mid, cy + 38, M3.ON_SURFACE);
        if (msVerifyUri != null) {
            smallFont.drawCenteredString(g, msVerifyUri, mid, cy + 50, M3.ON_SURFACE_VARIANT);
        }

        // copy button (tonal)
        boolean copied = Util.getMillis() - copiedAt < 1500;
        String label = Lang.tr(copied ? "acct.copied" : "acct.copy");
        int bw = (int) smallFont.width(label) + 20;
        int bx = (int) (mid - bw / 2f);
        int by = cy + ch - BTN_H - 8;
        boolean hover = isOver(bx, by, bw, BTN_H);
        float ht = animTo("mscopy", hover ? 1f : 0f, 12f);
        int bg = M3.layered(M3.SECONDARY_CONTAINER, M3.ON_SECONDARY_CONTAINER, (int) (M3.STATE_HOVER * ht));
        M3.roundRect(g, bx, by, bw, BTN_H, M3.pill(BTN_H), bg);
        Ripple.draw(g, "mscopy", bx, by, bw, BTN_H, M3.ON_SECONDARY_CONTAINER);
        smallFont.drawCenteredString(g, label, mid,
                by + (BTN_H - smallFont.lineHeight()) / 2f, M3.ON_SECONDARY_CONTAINER);
        addZone(bx, by, bw, BTN_H, () -> {
            Ripple.press("mscopy", mouseX, mouseY);
            if (msUserCode != null) {
                MicrosoftAuth.copyToClipboard(msUserCode);
                copiedAt = Util.getMillis();
            }
        });
    }

    /** Standard M3 icon button. */
    private void iconButton(GuiGraphicsExtractor g, int x, int y, String icon, String key, Runnable action) {
        boolean hover = isOver(x, y, 14, 14);
        float t = animTo("ib:" + key, hover ? 1f : 0f, 12f);
        if (t > 0.01f) {
            M3.roundRect(g, x, y, 14, 14, M3.pill(14),
                    M3.stateLayer(M3.ON_SURFACE, (int) (M3.STATE_HOVER * t)));
        }
        Object rippleKey = "ib:" + key;
        Ripple.draw(g, rippleKey, x, y, 14, 14, M3.ON_SURFACE);
        Icons.drawCentered(g, icon, 9, x + 7, y + 7,
                M3.lerp(M3.ON_SURFACE_VARIANT, M3.ON_SURFACE, t));
        addZone(x, y, 14, 14, () -> {
            Ripple.press(rippleKey, mouseX, mouseY);
            action.run();
        });
    }

    /* ================================================================== */
    /*  behavior                                                          */
    /* ================================================================== */

    private void submitAdd() {
        String name = input.trim();
        if (!AccountManager.instance.isValidName(name)) {
            error = Lang.tr("acct.invalid");
            return;
        }
        if (AccountManager.instance.addOffline(name)) {
            input = "";
            error = "";
        } else {
            error = Lang.tr("acct.duplicate");
        }
    }

    private void startMsLogin() {
        if (msPending) return;
        msPending = true;
        msUserCode = null;
        msVerifyUri = null;
        error = "";
        msAuth = new MicrosoftAuth();
        msAuth.start(
                code -> {
                    msUserCode = code.userCode();
                    msVerifyUri = code.verificationUri();
                },
                result -> {
                    AccountManager.instance.addMicrosoft(
                            result.uuid(), result.name(), result.mcAccessToken(), result.msRefreshToken());
                    msPending = false;
                    msUserCode = null;
                    error = "";
                },
                message -> {
                    msPending = false;
                    msUserCode = null;
                    error = Lang.tr("acct.ms_failed", message);
                });
    }

    private void cancelMsLogin() {
        if (msAuth != null) {
            msAuth.cancel();
            msAuth = null;
        }
        msPending = false;
        msUserCode = null;
    }

    private Supplier<PlayerSkin> skinFor(Account account) {
        return skins.computeIfAbsent(account.uuid(), k ->
                this.minecraft.getSkinManager().createLookup(
                        new GameProfile(account.uuidObject(), account.name()), false));
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
        if (mx < panelX || mx > panelX + PANEL_W || my < panelY || my > panelY + PANEL_H) {
            onClose();
            return true;
        }
        inputFocused = false;
        for (int i = zones.size() - 1; i >= 0; i--) {
            Zone z = zones.get(i);
            if (mx >= z.x && mx <= z.x + z.w && my >= z.y && my <= z.y + z.h) {
                z.click.run();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scrollTarget = Math.max(0, scrollTarget + (float) (-scrollY * 18));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (msPending) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                cancelMsLogin();
                return true;
            }
            return true;
        }
        if (inputFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                submitAdd();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                inputFocused = false;
                return true;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (inputFocused && !msPending) {
            String s = event.codepointAsString();
            if (s.matches("\\w") && input.length() < 16) {
                input += s;
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        cancelMsLogin();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }

    /* ================================================================== */
    /*  helpers                                                           */
    /* ================================================================== */

    private float animTo(Object key, float target, float speedPerSec) {
        float now = anim.getOrDefault(key, target);
        float next = AnimationUtils.easeExp(now, target, speedPerSec);
        anim.put(key, next);
        return next;
    }

    private void addZone(int x, int y, int w, int h, Runnable click) {
        int y0 = Math.max(y, zClipY0);
        int y1 = Math.min(y + h, zClipY1);
        if (y1 <= y0) return;
        zones.add(new Zone(x, y0, w, y1 - y0, click));
    }

    private boolean isOver(int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
