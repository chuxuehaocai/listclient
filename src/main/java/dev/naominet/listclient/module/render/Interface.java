package dev.naominet.listclient.module.render;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventRender2D;
import dev.naominet.listclient.manager.ModuleManager;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.ui.MusicPlayerScreen;
import dev.naominet.listclient.utils.AnimationUtils;
import dev.naominet.listclient.utils.RenderUtils;
import dev.naominet.listclient.value.Option;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.awt.*;
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

    public Interface() {
        super("Interface", Category.Render);
        setEnable(true);
        addValues(rainbow);
    }

    @EventTarget
    public void onRender2D(EventRender2D e) {
        GuiGraphicsExtractor extractor = e.getExtractor();
        int rainbowTick = 0;

        setSuffix("Classic Experience");
        RenderUtils.drawSomeShitText(extractor, "List [Build 4.0] [Development Build]", (int) getX(), (int) getY());
        setXYWH(getX(), getY(), mc.font.width("List [Build 4.0] [Development Build]"), mc.font.lineHeight);

        RenderUtils.drawTexture(extractor, "cute/cute.png", "cute",
                mc.getWindow().getGuiScaledWidth() - 85, mc.getWindow().getGuiScaledHeight() - 75, 80, 75);

        // ---- MusicPlayer widget (parasitic host) ----
        renderMusicWidget(extractor);

        int startX = mc.getWindow().getGuiScaledWidth();

        if (mc.player != null) {
            if (sortedModuleListNeedUpdata) {
                sortedModuleList = (ArrayList<Module>) ModuleManager.instance.getModules().clone();
                sortedModuleList.sort((o1, o2) -> mc.font.width(o2.getSuffix() == null ? o2.getName() : o2.getName() + " " + o2.getSuffix())
                        - mc.font.width(o1.getSuffix() == null ? o1.getName() : o1.getName() + " " + o1.getSuffix()));
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

        //render
        if (sortedModuleList != null) {
            for (Module m : sortedModuleList) {
                // MusicPlayer is an entry keybind, not a HUD array item.
                if (m instanceof MusicPlayer) continue;

                String suffix = m.getSuffix() == null ? "" : " " + m.getSuffix();
                int target = mc.font.width(m.getName() + suffix);

                if (!m.isEnable()) {
                    m.setAnimX(AnimationUtils.animationNew(m.getAnimX(), 0, target, 10, 0.1f));
                } else {
                    m.setAnimX(AnimationUtils.animationNew(m.getAnimX(), 0, 10, 0.1f));
                }

                float yNeedPlusOffset = (mc.font.lineHeight + 2) * (target - m.getAnimX()) / target;

                int width = mc.font.width(m.getName() + suffix);
                int moduleX = startX - width + (int) m.getAnimX();
                int suffixX = startX - mc.font.width(suffix) + (int) m.getAnimX();
                mainColor = rainbow.getValue()
                        ? new Color(Color.HSBtoRGB((float) ((double) this.mc.player.tickCount / 50.0
                        + Math.sin((double) rainbowTick / 50.0 * 1.6)) % 1.0f, 0.5f, 1.0f)).getRGB()
                        : MusicPlayer.ACCENT.getRGB();
                if (++rainbowTick > 50) {
                    rainbowTick = 0;
                }

                if (yNeedPlusOffset != 0) {
                    extractor.fill(moduleX - 2, (int) (yOffset - 1), moduleX + width,
                            (int) (yOffset - 1 + yNeedPlusOffset), new Color(0, 0, 0, 84).getRGB());
                    extractor.text(mc.font, m.getName(), moduleX, (int) yOffset, mainColor);
                    extractor.text(mc.font, suffix, suffixX, (int) yOffset, new Color(114, 114, 114).getRGB());
                    yOffset += yNeedPlusOffset;
                }
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
