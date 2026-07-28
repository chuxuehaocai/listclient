package dev.naominet.listclient.module.render;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventRender2D;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.module.combat.KillAura;
import dev.naominet.listclient.ui.theme.Icons;
import dev.naominet.listclient.ui.theme.M3;
import dev.naominet.listclient.utils.AnimationUtils;
import dev.naominet.listclient.utils.font.TTFFontRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Draggable Material 3 target card, based on the legacy TargetHUD layout. */
public class TargetHUD extends Module {
    private static final int WIDTH = 184;
    private static final int HEIGHT = 72;
    private static final int PORTRAIT = 36;

    private final TTFFontRenderer nameFont = M3.title();
    private final TTFFontRenderer bodyFont = M3.body();
    private final TTFFontRenderer labelFont = M3.labelSmall();

    private LivingEntity displayedTarget;
    private float visibility;
    private float displayedHealth;
    private float displayedAbsorption;
    private long visibilityAt;
    private long visibilityDuration;
    private float visibilityFrom;
    private float visibilityTo;

    public TargetHUD() {
        super("TargetHUD", Category.Render);
        setXYWH(8, 108, WIDTH, HEIGHT);
    }

    @EventTarget
    public void onRender2D(EventRender2D event) {
        if (mc.player == null || mc.level == null) {
            displayedTarget = null;
            visibility = 0f;
            return;
        }

        LivingEntity current = mc.gui.screen() instanceof ChatScreen ? mc.player : validTarget();
        if (current != null) {
            if (displayedTarget != current) {
                displayedTarget = current;
                displayedHealth = current.getHealth();
                displayedAbsorption = current.getAbsorptionAmount();
            }
        }

        updateVisibility(current != null);
        if (displayedTarget == null || visibility <= 0f) {
            if (visibility <= 0f) displayedTarget = null;
            return;
        }

        if (current != null) {
            displayedHealth = AnimationUtils.easeExp(displayedHealth,
                    Math.max(0f, current.getHealth()), 10f);
            displayedAbsorption = AnimationUtils.easeExp(displayedAbsorption,
                    Math.max(0f, current.getAbsorptionAmount()), 10f);
        }

        int x = (int) getX();
        int y = (int) getY();
        setXYWH(x, y, WIDTH, HEIGHT);
        GuiGraphicsExtractor g = event.getExtractor();
        float scale = 0.96f + visibility * 0.04f;
        g.pose().pushMatrix();
        g.pose().translate(x + WIDTH / 2f, y + HEIGHT / 2f);
        g.pose().scale(scale, scale);
        g.pose().translate(-(x + WIDTH / 2f), -(y + HEIGHT / 2f));
        drawCard(g, displayedTarget, x, y, visibility);
        g.pose().popMatrix();

        if (current == null && visibility <= 0f) {
            displayedTarget = null;
        }
    }

    private void updateVisibility(boolean shown) {
        float target = shown ? 1f : 0f;
        if (visibilityTo != target) {
            visibilityFrom = visibility;
            visibilityTo = target;
            visibilityAt = Util.getMillis();
            visibilityDuration = shown ? 250L : 200L;
        }
        if (visibility == target) {
            return;
        }
        float t = Mth.clamp((Util.getMillis() - visibilityAt) / (float) visibilityDuration, 0f, 1f);
        float eased = shown
                ? AnimationUtils.easeOutCubic(t)
                : 1f - (float) Math.pow(1f - t, 3f);
        visibility = Mth.lerp(eased, visibilityFrom, visibilityTo);
        if (t >= 1f) {
            visibility = target;
        }
    }

    private LivingEntity validTarget() {
        LivingEntity target = KillAura.target;
        return target != null && target.isAlive() && !target.isRemoved() ? target : null;
    }

    private void drawCard(GuiGraphicsExtractor g, LivingEntity target, int x, int y, float alpha) {
        int drawY = y - Math.round((1f - alpha) * 6f);
        int surface = M3.fade(M3.withAlpha(M3.SURFACE_CONTAINER_HIGH, 0xF0), alpha);
        int portraitSurface = M3.fade(M3.SURFACE_CONTAINER_HIGHEST, alpha);
        int onSurface = M3.fade(M3.ON_SURFACE, alpha);
        int onVariant = M3.fade(M3.ON_SURFACE_VARIANT, alpha);

        M3.shadowSoft(g, x, drawY, WIDTH, HEIGHT, M3.SHAPE_M, alpha);
        M3.roundRect(g, x, drawY, WIDTH, HEIGHT, M3.SHAPE_M, surface);
        M3.roundRect(g, x + 6, drawY + 6, PORTRAIT, PORTRAIT,
                M3.SHAPE_S, portraitSurface);
        drawPortrait(g, target, x + 6, drawY + 6, alpha);

        int contentX = x + 48;
        int contentW = WIDTH - 54;
        String name = fit(target.getName().getString(), contentW, nameFont);
        nameFont.drawString(g, name, contentX, drawY + 8, onSurface);

        float maxHealth = Math.max(1f, target.getMaxHealth());
        float health = Math.max(0f, displayedHealth);
        float healthRatio = Mth.clamp(health / maxHealth, 0f, 1f);
        float distance = mc.player == target ? 0f : mc.player.distanceTo(target);
        String healthText = String.format(Locale.ROOT, "%.1f / %.1f HP", health, maxHealth);
        String distanceText = String.format(Locale.ROOT, "%.1f m", distance);
        bodyFont.drawString(g, fit(healthText, contentW - 32, bodyFont),
                contentX, drawY + 21, onVariant);
        labelFont.drawString(g, distanceText,
                x + WIDTH - 6 - labelFont.width(distanceText), drawY + 21, onVariant);

        String posText = String.format(Locale.ROOT, "XYZ %d  %d  %d",
                Mth.floor(target.getX()), Mth.floor(target.getY()), Mth.floor(target.getZ()));
        labelFont.drawString(g, fit(posText, contentW, labelFont),
                contentX, drawY + 32, onVariant);
        drawEquipment(g, target, x + 6, drawY + 47, alpha);

        int barY = drawY + HEIGHT - 8;
        M3.roundRect(g, contentX, barY, contentW, 4, M3.pill(4),
                M3.fade(M3.SURFACE_CONTAINER_HIGHEST, alpha));
        int fill = Math.round(contentW * healthRatio);
        if (fill > 0) {
            int healthColor = M3.lerp(M3.ERROR, M3.PRIMARY,
                    Mth.clamp((healthRatio - 0.15f) / 0.45f, 0f, 1f));
            M3.roundRect(g, contentX, barY, fill, 4, M3.pill(4),
                    M3.fade(healthColor, alpha));
        }

        float absorptionRatio = Mth.clamp(displayedAbsorption / maxHealth, 0f, 1f);
        int absorptionWidth = Math.min(contentW - fill, Math.round(contentW * absorptionRatio));
        if (absorptionWidth > 0) {
            M3.roundRect(g, contentX + fill, barY, absorptionWidth, 4, M3.pill(4),
                    M3.fade(M3.TERTIARY, alpha));
        }
    }

    private void drawPortrait(GuiGraphicsExtractor g, LivingEntity target, int x, int y, float alpha) {
        if (target instanceof AbstractClientPlayer player) {
            PlayerFaceExtractor.extractRenderState(g, player.getSkin(),
                    x + 2, y + 2, PORTRAIT - 4, M3.fade(0xFFFFFFFF, alpha));
            return;
        }
        Icons.drawCentered(g, Icons.PERSON, 20,
                x + PORTRAIT / 2f, y + PORTRAIT / 2f, M3.fade(M3.ON_SURFACE_VARIANT, alpha));
    }

    private void drawEquipment(GuiGraphicsExtractor g, LivingEntity target,
                               int x, int y, float alpha) {
        ItemStack[] equipment = {
                target.getItemBySlot(EquipmentSlot.HEAD),
                target.getItemBySlot(EquipmentSlot.CHEST),
                target.getItemBySlot(EquipmentSlot.LEGS),
                target.getItemBySlot(EquipmentSlot.FEET),
                target.getMainHandItem(),
                target.getOffhandItem()
        };
        int slot = 14;
        int gap = 3;
        for (int i = 0; i < equipment.length; i++) {
            int slotX = x + i * (slot + gap);
            M3.roundRect(g, slotX, y, slot, slot, M3.SHAPE_XS,
                    M3.fade(M3.SURFACE_CONTAINER_HIGHEST, alpha));
            ItemStack stack = equipment[i];
            if (!stack.isEmpty()) {
                g.pose().pushMatrix();
                g.pose().translate(slotX + 1, y + 1);
                float itemScale = 0.75f * (0.85f + alpha * 0.15f);
                g.pose().translate(6f, 6f);
                g.pose().scale(itemScale, itemScale);
                g.pose().translate(-6f, -6f);
                g.item(target, stack, 0, 0, i + 1);
                g.pose().popMatrix();
            }
        }
        int armor = target.getArmorValue();
        String armorText = armor + " ARM";
        labelFont.drawString(g, armorText,
                x + WIDTH - 12 - labelFont.width(armorText),
                y + (slot - labelFont.lineHeight()) / 2f,
                M3.fade(M3.ON_SURFACE_VARIANT, alpha));
    }

    private static String fit(String text, int maxWidth, TTFFontRenderer font) {
        if (text == null || font.width(text) <= maxWidth) return text == null ? "" : text;
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + ellipsis) > maxWidth) {
            end -= Character.charCount(text.codePointBefore(end));
        }
        return text.substring(0, end) + ellipsis;
    }
}
