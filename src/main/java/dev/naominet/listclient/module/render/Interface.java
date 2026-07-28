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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

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

    /** M3 type scale – every HUD string goes through TTF. */
    private final TTFFontRenderer brandFont = M3.title();
    private final TTFFontRenderer listFont = M3.label();

    public Interface() {
        super("Interface", Category.Render);
        setEnable(true);
    }

    @EventTarget
    public void onRender2D(EventRender2D e) {
        GuiGraphicsExtractor extractor = e.getExtractor();

        // Drive the Monet color morph from the HUD so the whole client recolors
        // live as the album seed changes, even with no screen open.
        dev.naominet.listclient.ui.theme.MonetTheme.update();

        String buildLabel = "4.0 · DEV";
        int padX = 5;
        int padY = 2;
        int gap = 3;
        float brandW = brandFont.width("List");
        float buildW = M3.labelSmall().width(buildLabel);
        int wmW = (int) Math.ceil(padX * 2 + brandW + gap + buildW);
        int wmH = (int) Math.ceil(Math.max(brandFont.lineHeight(), M3.labelSmall().lineHeight())) + padY * 2;
        int wmX = (int) getX();
        int wmY = (int) getY();
        M3.shadowSoft(extractor, wmX, wmY, wmW, wmH, M3.pill(wmH));
        M3.roundRect(extractor, wmX, wmY, wmW, wmH, M3.pill(wmH),
                M3.withAlpha(M3.SURFACE_CONTAINER_HIGH, 0xE8));
        float brandY = wmY + (wmH - brandFont.lineHeight()) / 2f;
        float suffixY = wmY + (wmH - M3.labelSmall().lineHeight()) / 2f;
        float brandEnd = brandFont.drawString(extractor, "List", wmX + padX, brandY, M3.PRIMARY);
        M3.labelSmall().drawString(extractor, buildLabel, brandEnd + gap, suffixY, M3.ON_SURFACE_VARIANT);
        setXYWH(wmX, wmY, wmW, wmH);

        RenderUtils.drawTexture(extractor, "cute/cute.png", "cute",
                mc.getWindow().getGuiScaledWidth() - 85, mc.getWindow().getGuiScaledHeight() - 75, 80, 75);

        // ---- MusicPlayer widget (parasitic host) ----
        renderMusicWidget(extractor);

        int startX = mc.getWindow().getGuiScaledWidth();

        if (mc.player != null) {
            if (sortedModuleListNeedUpdata) {
                sortedModuleList = (ArrayList<Module>) ModuleManager.instance.getModules().clone();
                sortedModuleList.sort((o1, o2) -> Float.compare(
                        renderedWidth(o2), renderedWidth(o1)));
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

        // Dense M3 one-line rows: tonal container, primary name, variant suffix.
        int lh = (int) Math.ceil(listFont.lineHeight());
        int rowH = Math.max(9, lh);
        int band = rowH;
        int textPad = 3;
        if (sortedModuleList != null) {
            for (Module m : sortedModuleList) {
                // MusicPlayer is an entry keybind, not a HUD array item.
                if (m instanceof MusicPlayer) continue;

                String suffix = m.getSuffix() == null ? "" : m.getSuffix();
                float nameW = listFont.width(m.getName());
                float suffixGap = suffix.isEmpty() ? 0f : listFont.width(" ");
                float suffixW = suffix.isEmpty() ? 0f : listFont.width(suffix);
                int rowW = (int) Math.ceil(nameW + suffixGap + suffixW) + textPad * 2;

                // animX: horizontal slide (0 = fully in, rowW+8 = off-screen).
                // animY: 0..1 vertical reveal used to collapse hidden rows.
                float xTarget = m.isEnable() ? 0f : rowW + 8f;
                float yTarget = m.isEnable() ? 1f : 0f;
                m.setAnimX(AnimationUtils.easeExp(m.getAnimX(), xTarget, 12f));
                m.setAnimY(AnimationUtils.easeExp(m.getAnimY(), yTarget, 12f));

                float reveal = m.getAnimY();
                if (reveal < 0.02f) {
                    continue; // fully collapsed – contributes no height
                }
                int bandThis = Math.max(1, Math.round(band * reveal));

                int rowX = (int) (startX - rowW + m.getAnimX());
                int ry = (int) yOffset;

                extractor.enableScissor(Math.max(0, rowX - 1), ry, startX, ry + bandThis);
                try {
                    M3.roundRect(extractor, rowX, ry, rowW, rowH, M3.SHAPE_XS,
                            M3.withAlpha(M3.SURFACE_CONTAINER_HIGH, 0xEC), true, false, true, false);

                    float textY = ry + (rowH - listFont.lineHeight()) / 2f;
                    float nameX = rowX + textPad;
                    listFont.drawString(extractor, m.getName(), nameX, textY, M3.PRIMARY);
                    if (!suffix.isEmpty()) {
                        listFont.drawString(extractor, suffix, nameX + nameW + suffixGap,
                                textY, M3.ON_SURFACE_VARIANT);
                    }
                } finally {
                    extractor.disableScissor();
                }

                yOffset += bandThis;
            }
        }
    }

    private float renderedWidth(Module module) {
        float width = listFont.width(module.getName());
        String suffix = module.getSuffix();
        return suffix == null || suffix.isEmpty() ? width : width + 1f + listFont.width(suffix);
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
