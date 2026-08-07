package dev.naominet.listclient.module.player;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPreTick;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.utils.TimerUtils;
import dev.naominet.listclient.value.Numbers;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

public class ChestStealer extends Module {
    public Numbers delay = new Numbers("Delay", 50, 5, 800, 1);
    private final TimerUtils timer = new TimerUtils();
    public ChestStealer() {
        super("ChestStealer", Category.Player);
        addValues(delay);
    }

    @EventTarget
    public void onPreTick(EventPreTick event){
        if(mc.player == null) return;

        if(!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) return;

        if(screen instanceof InventoryScreen) return;

        if(screen.getMenu() instanceof ChestMenu chestMenu) {
            for (int i = 0; i < chestMenu.slots.size(); i++) {
                Slot slot = chestMenu.getSlot(i);

                if (slot.container != mc.player.getInventory() && !slot.getItem().isEmpty() && timer.hasReached(delay.intValue())) {
                    //mc.interactionManager.clickSlot(chestMenu.containerId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    screen.slotClicked(slot, i, 0, ContainerInput.QUICK_MOVE);
                    timer.reset();
                }
            }

            if (isChestEmpty(screen) || isPlayerInventoryFull()) {
                mc.player.closeContainer();
            }
        }
    }

    private boolean isChestEmpty(AbstractContainerScreen<?> handler) {
        for (Slot slot : handler.getMenu().slots) {
            if (mc.player != null && slot.container != mc.player.getInventory() && !slot.getItem().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean isPlayerInventoryFull() {
        return mc.player != null && mc.player.getInventory().getFreeSlot() == -1;
    }
}
