package dev.naominet.listclient.ui.hud;

import dev.naominet.listclient.ui.theme.M3;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/** Material 3 replacement for the vanilla survival hotbar. */
public final class MaterialHotbarRenderer {
    private static float selectionX = Float.NaN;

    public static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        int selected = player.getInventory().getSelectedSlot();
        int slotsWidth = 180;
        int height = 20;
        int x = (g.guiWidth() - slotsWidth) / 2;
        int y = g.guiHeight() - 22;

        M3.shadowSoft(g, x, y, slotsWidth, height, M3.SHAPE_L);
        M3.roundRect(g, x, y, slotsWidth, height, M3.SHAPE_L,
                M3.withAlpha(M3.SURFACE_CONTAINER, 200));

        float targetSelectionX = x + selected * 20f + 1f;
        if (Float.isNaN(selectionX) || Math.abs(selectionX - targetSelectionX) > slotsWidth) {
            selectionX = targetSelectionX;
        } else {
            selectionX += (targetSelectionX - selectionX) * 0.35f;
            if (Math.abs(selectionX - targetSelectionX) < 0.1f) selectionX = targetSelectionX;
        }
        M3.roundRect(g, Math.round(selectionX), y + 1, 18, height - 2, M3.SHAPE_M,
                M3.SECONDARY_CONTAINER);

        int seed = 1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            renderSlot(g, deltaTracker, player, stack,
                    x + slot * 20 + 2, y + 2, seed++);
        }

        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) {
            boolean left = player.getMainArm().getOpposite() == HumanoidArm.LEFT;
            int offhandX = left ? x - 24 : x + slotsWidth + 8;
            M3.roundRect(g, offhandX - 3, y, 22, height, M3.SHAPE_M,
                    M3.withAlpha(M3.SURFACE_CONTAINER_HIGH, 0xEE));
            renderSlot(g, deltaTracker, player, offhand, offhandX, y + 2, seed);
        }

        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            float attack = player.getAttackStrengthScale(0f);
            if (attack < 1f) {
                M3.linearProgress(g, x + 4, y + height - 3, slotsWidth - 8, 2, attack);
            }
        }
    }

    private static void renderSlot(GuiGraphicsExtractor g, DeltaTracker deltaTracker,
                                   LocalPlayer player, ItemStack stack, int x, int y, int seed) {
        if (stack.isEmpty()) return;
        float pop = stack.getPopTime() - deltaTracker.getGameTimeDeltaPartialTick(false);
        if (pop > 0f) {
            float scale = 1f + pop / 5f;
            g.pose().pushMatrix();
            g.pose().translate(x + 8f, y + 12f);
            g.pose().scale(1f / scale, (scale + 1f) / 2f);
            g.pose().translate(-(x + 8f), -(y + 12f));
        }
        g.item(player, stack, x, y, seed);
        if (pop > 0f) g.pose().popMatrix();
        g.itemDecorations(Minecraft.getInstance().font, stack, x, y);
    }

    private MaterialHotbarRenderer() {
    }
}
