package dev.naominet.listclient.module.render;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventRender2D;
import dev.naominet.listclient.manager.ModuleManager;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.ui.MusicPlayerScreen;
import dev.naominet.listclient.ui.theme.M3;
import dev.naominet.listclient.utils.AnimationUtils;
import dev.naominet.listclient.utils.RenderUtils;
import dev.naominet.listclient.utils.font.TTFFontRenderer;
import dev.naominet.listclient.value.Option;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Map;

/**
 * Core HUD. Also hosts the MusicPlayer mini-widget (drag/position owned by
 * {@link MusicPlayer#setXYWH}) so the MusicPlayer module itself only acts as
 * a keybind entry into {@link MusicPlayerScreen}.
 */
public class Interface extends Module {
    public static ArrayList<Module> sortedModuleList;
    public static boolean sortedModuleListNeedUpdata = true;
    float yPlus = 2;
    private Option rainbow = new Option("Rainbow", true);
    private int mainColor = -1;

    /** M3 type scale – every HUD string goes through TTF. */
    private final TTFFontRenderer brandFont = M3.title();
    private final TTFFontRenderer listFont = M3.label();

    public Interface() {
        super("Interface", Category.Render);
        setEnable(true);
        addValues(rainbow);
    }

    @EventTarget
    public void onRender2D(EventRender2D e) {
        GuiGraphicsExtractor extractor = e.getExtractor();
        int rainbowTick = 0;

        // Drive the Monet color morph from the HUD so the whole client recolors
        // live as the album seed changes, even with no screen open.
        dev.naominet.listclient.ui.theme.MonetTheme.update();

        setSuffix("Classic Experience");
        // Watermark chip: on-colors need their container – a bare on-surface
        // string washes out over bright scenes (sky, snow).
        float wmW = brandFont.width("List [Build 4.0] [Development Build]");
        float wmH = brandFont.lineHeight();
        M3.roundRect(extractor, (int) getX() - 4, (int) getY() - 2, (int) wmW + 8, (int) wmH + 4,
                M3.SHAPE_S, M3.withAlpha(M3.SURFACE_CONTAINER, 0xB4));
        float brandEnd = brandFont.drawString(extractor, "List ",
                (float) getX(), (float) getY(), M3.ON_SURFACE);
        brandFont.drawString(extractor, "[Build 4.0] [Development Build]",
                brandEnd, (float) getY(), M3.ON_SURFACE_VARIANT);
        setXYWH(getX(), getY(), wmW, wmH);

        RenderUtils.drawTexture(extractor, "cute/cute.png", "cute",
                mc.getWindow().getGuiScaledWidth() - 85, mc.getWindow().getGuiScaledHeight() - 75, 80, 75);

        // ---- MusicPlayer widget (parasitic host) ----
        renderMusicWidget(extractor);

        int startX = mc.getWindow().getGuiScaledWidth();

        if (mc.player != null) {
            if (sortedModuleListNeedUpdata) {
                sortedModuleList = (ArrayList<Module>) ModuleManager.instance.getModules().clone();
                sortedModuleList.sort((o1, o2) -> Float.compare(
                        listFont.width(o2.getSuffix() == null ? o2.getName() : o2.getName() + " " + o2.getSuffix()),
                        listFont.width(o1.getSuffix() == null ? o1.getName() : o1.getName() + " " + o1.getSuffix())));
                sortedModuleListNeedUpdata = false;
            }

            if (mc.player.getActiveEffects().isEmpty()) {
                yPlus = AnimationUtils.animationNew(yPlus, 2, 8, 0.1f);
            } else {
                boolean isGood = true;
                for (Map.Entry<Holder<MobEffect>, MobEffectInstance> entry : mc.player.getActiveEffectsMap().entrySet()) {
                    Holder<MobEffect> registryEntry = entry.getKey();
                    MobEffect effect = registryEntry.value();
                    if (!effect.isBeneficial()) {
                        isGood = false;
                        break;
                    }
                }
                yPlus = AnimationUtils.animationNew(yPlus, isGood ? 28 : 54, 8, 0.1f);
            }
        }

        float yOffset = yPlus;

        // Module chips: fixed row height (no more width-coupled height jank that
        // left gaps), each chip slides in from the right via animX and collapses
        // vertically via animY. Every chip is drawn inside its OWN GL scissor
        // band so its soft shadow can't bleed onto — and stack darkly with —
        // the chip below it.
        int lh = (int) Math.ceil(listFont.lineHeight());
        int chipH = lh + 4;      // chip content height
        int band = chipH + 4;    // row pitch: 4px gap holds each chip's shadow
        int dot = 3;             // leading M3 accent indicator
        int textPad = 8 + dot;   // left padding: gap + dot + gap
        if (sortedModuleList != null) {
            for (Module m : sortedModuleList) {
                // MusicPlayer is an entry keybind, not a HUD array item.
                if (m instanceof MusicPlayer) continue;

                String suffix = m.getSuffix() == null ? "" : " " + m.getSuffix();
                float textW = listFont.width(m.getName() + suffix);
                int chipW = (int) textW + textPad + 6;

                // animX: horizontal slide (0 = fully in, chipW+8 = off the right edge).
                // animY: 0..1 vertical reveal used to collapse hidden rows.
                float xTarget = m.isEnable() ? 0f : chipW + 8f;
                float yTarget = m.isEnable() ? 1f : 0f;
                m.setAnimX(AnimationUtils.easeExp(m.getAnimX(), xTarget, 12f));
                m.setAnimY(AnimationUtils.easeExp(m.getAnimY(), yTarget, 12f));

                float reveal = m.getAnimY();
                if (reveal < 0.02f) {
                    continue; // fully collapsed – contributes no height
                }
                int bandThis = Math.max(1, Math.round(band * reveal));

                int chipX = (int) (startX - chipW + m.getAnimX());
                int ry = (int) yOffset;

                mainColor = rainbow.getValue()
                        ? new Color(Color.HSBtoRGB((float) ((double) this.mc.player.tickCount / 50.0
                        + Math.sin((double) rainbowTick / 50.0 * 1.6)) % 1.0f, 0.5f, 1.0f)).getRGB()
                        : M3.PRIMARY;
                if (++rainbowTick > 50) {
                    rainbowTick = 0;
                }

                // Clip everything for this chip to its band; the shadow's downward
                // spread stops at the gap and never touches the next chip.
                extractor.enableScissor(chipX - 6, ry, startX + 2, ry + bandThis);
                // M3 chip: pill silhouette (left corners; right runs off-screen),
                // tonal surface-container fill, soft contained shadow.
                M3.shadowSoft(extractor, chipX, ry, chipW, chipH, M3.pill(chipH));
                M3.roundRect(extractor, chipX, ry, chipW, chipH, M3.pill(chipH),
                        M3.withAlpha(M3.SURFACE_CONTAINER, 0xE6), true, false, true, false);
                // Leading accent dot (Monet primary).
                int dotY = ry + (chipH - dot) / 2;
                M3.roundRect(extractor, chipX + 6, dotY, dot, dot, M3.pill(dot), M3.PRIMARY);
                float textY = ry + (chipH - listFont.lineHeight()) / 2f;
                listFont.drawString(extractor, m.getName(), chipX + textPad, textY, mainColor);
                listFont.drawString(extractor, suffix,
                        chipX + textPad + listFont.width(m.getName()), textY, M3.ON_SURFACE_VARIANT);
                extractor.disableScissor();

                yOffset += bandThis;
            }
        }
    }

    private void renderMusicWidget(GuiGraphicsExtractor g) {
        // Hide the floating widget while the full player screen is open.
        if (mc.gui.screen() instanceof MusicPlayerScreen) {
            return;
        }
        MusicPlayer mp = ModuleManager.instance.getModuleByClazz(MusicPlayer.class);
        if (mp == null) {
            mp = MusicPlayer.instance;
        }
        if (mp == null) {
            return;
        }
        mp.renderWidget(g);
    }
}
