package dev.naominet.listclient.module.player;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPreTick;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.utils.ItemUtil;
import dev.naominet.listclient.utils.TimerUtils;
import dev.naominet.listclient.value.Numbers;
import dev.naominet.listclient.value.Option;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Equips the highest-scoring armor available in the player's inventory. */
public class AutoArmor extends Module {
    private final Numbers delay = new Numbers("Delay", 150, 100, 500, 50);
    private final Option onlyInventory = new Option("Only Inv", false);
    private final TimerUtils timer = new TimerUtils();

    public AutoArmor() {
        super("AutoArmor", Category.Player);
        addValues(delay, onlyInventory);
    }

    @EventTarget
    public void onPreTick(EventPreTick event) {
        if (mc.player == null || mc.level == null || mc.gui.screen() == null) {
            return;
        }
        if ((onlyInventory.getValue() && !(mc.gui.screen() instanceof InventoryScreen)) ) {
            return;
        }
        if (!(mc.gui.screen() instanceof InventoryScreen screen) || !timer.hasReached(delay.getValue())) {
            return;
        }

        for (EquipmentSlot equipmentSlot : new EquipmentSlot[]{
                EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
            int inventorySlot = findBetterArmor(equipmentSlot);
            if (inventorySlot == -1) {
                continue;
            }

            Slot slot = screen.getMenu().getSlot(toMenuSlot(inventorySlot));
            screen.slotClicked(slot, slot.index, 0, ContainerInput.QUICK_MOVE);
            timer.reset();
            return;
        }
    }

    private int findBetterArmor(EquipmentSlot equipmentSlot) {
        float equippedScore = ItemUtil.getEquippedArmorScore(equipmentSlot);
        float bestScore = equippedScore;
        int bestSlot = -1;

        for (int i = 0; i < mc.player.getInventory().getNonEquipmentItems().size(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!usesSlot(stack, equipmentSlot)) {
                continue;
            }
            float score = ItemUtil.getArmorScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private boolean usesSlot(ItemStack stack, EquipmentSlot equipmentSlot) {
        var equippable = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == equipmentSlot;
    }

    /** InventoryMenu slots: main inventory 9-35, then hotbar 36-44. */
    private int toMenuSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }
}
