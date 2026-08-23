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
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Removes unwanted inventory items while the player's inventory screen is open. */
public class InvCleaner extends Module {
    private final Option keepTools = new Option("Best Tools", true);
    private final Option keepArmor = new Option("Best Armor", true);
    private final Option keepBow = new Option("Best Bow", true);
    private final Option keepBucket = new Option("Keep Buckets", false);
    private final Option keepArrow = new Option("Keep Arrows", true);
    private final Option autoToggle = new Option("Auto Toggle", false);
    private final Numbers delay = new Numbers("Delay", 80, 10, 500, 10);
    private final TimerUtils timer = new TimerUtils();

    private int currentSlot;

    public InvCleaner() {
        super("InvCleaner", Category.Player);
        addValues(keepTools, keepArmor, keepArrow, keepBow, keepBucket, autoToggle, delay);
    }

    @Override
    public void onEnable() {
        currentSlot = 0;
        timer.reset();
    }

    @EventTarget
    public void onPreTick(EventPreTick event) {
        if (mc.player == null || !(mc.gui.screen() instanceof InventoryScreen screen)) {
            return;
        }
        if (currentSlot >= mc.player.getInventory().getNonEquipmentItems().size()) {
            currentSlot = 0;
            if (autoToggle.getValue()) {
                setEnable(false);
            }
            return;
        }
        if (!timer.hasReached(delay.getValue())) {
            return;
        }

        ItemStack stack = mc.player.getInventory().getItem(currentSlot);
        if (shouldDiscard(stack)) {
            Slot slot = screen.getMenu().getSlot(toMenuSlot(currentSlot));
            screen.slotClicked(slot, slot.index, 1, ContainerInput.THROW);
        }
        currentSlot++;
        timer.reset();
    }

    private boolean shouldDiscard(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (isJunk(item)) {
            return true;
        }
        if (item == Items.FLINT || item == Items.COMPASS) {
            return ItemUtil.countItem(item) > 1;
        }
        if (!keepBucket.getValue() && (item == Items.BUCKET || item == Items.WATER_BUCKET
                || item == Items.LAVA_BUCKET || item == Items.MILK_BUCKET)) {
            return true;
        }
        if (item == Items.ARROW && !keepArrow.getValue()) {
            return true;
        }
        if (stack.is(ItemTags.SWORDS)) {
            return !keepTools.getValue() || hasBetterSword(stack);
        }
        if (isTool(stack)) {
            return !keepTools.getValue() || hasBetterTool(stack);
        }
        if (isArmor(stack)) {
            return !keepArmor.getValue() || hasBetterArmor(stack);
        }
        if (item instanceof BowItem) {
            return !keepBow.getValue() || hasBetterBow(stack);
        }
        return stack.has(DataComponents.POTION_CONTENTS);
    }

    private boolean isJunk(Item item) {
        return item == Items.STICK || item == Items.EGG || item == Items.BONE || item == Items.BOWL
                || item == Items.GLASS_BOTTLE || item == Items.STRING || item == Items.FEATHER
                || item == Items.FISHING_ROD || item == Items.SNOWBALL || item == Items.EXPERIENCE_BOTTLE;
    }

    private boolean hasBetterSword(ItemStack stack) {
        float score = ItemUtil.getSwordDamage(stack);
        return ItemUtil.getAllItems().stream()
                .filter(other -> other != stack && other.is(ItemTags.SWORDS))
                .anyMatch(other -> ItemUtil.getSwordDamage(other) > score);
    }

    private boolean hasBetterTool(ItemStack stack) {
        float score = ItemUtil.getDigSpeed(stack);
        return ItemUtil.getAllItems().stream()
                .filter(other -> other != stack && sameToolType(stack, other))
                .anyMatch(other -> ItemUtil.getDigSpeed(other) > score);
    }

    private boolean hasBetterArmor(ItemStack stack) {
        EquipmentSlot slot = stack.get(DataComponents.EQUIPPABLE).slot();
        float score = ItemUtil.getArmorScore(stack);
        return ItemUtil.getAllItems().stream()
                .filter(other -> other != stack && isArmor(other))
                .filter(other -> other.get(DataComponents.EQUIPPABLE).slot() == slot)
                .anyMatch(other -> ItemUtil.getArmorScore(other) > score);
    }

    private boolean hasBetterBow(ItemStack stack) {
        float score = ItemUtil.getBowScore(stack);
        return ItemUtil.getAllItems().stream()
                .filter(other -> other != stack && other.getItem() instanceof BowItem)
                .anyMatch(other -> ItemUtil.getBowScore(other) > score);
    }

    private boolean isTool(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES);
    }

    private boolean sameToolType(ItemStack first, ItemStack second) {
        return first.is(ItemTags.PICKAXES) && second.is(ItemTags.PICKAXES)
                || first.is(ItemTags.AXES) && second.is(ItemTags.AXES)
                || first.is(ItemTags.SHOVELS) && second.is(ItemTags.SHOVELS)
                || first.is(ItemTags.HOES) && second.is(ItemTags.HOES);
    }

    private boolean isArmor(ItemStack stack) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && switch (equippable.slot()) {
            case HEAD, CHEST, LEGS, FEET -> true;
            default -> false;
        };
    }

    /** InventoryMenu slots: main inventory 9-35, then hotbar 36-44. */
    private int toMenuSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }
}
