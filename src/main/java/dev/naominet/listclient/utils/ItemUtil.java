package dev.naominet.listclient.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * ItemUtil — 物品评分工具（从 OpenZen 移植）。
 * <p>
 * 26.2 中 {@code SwordItem}/{@code PickaxeItem}/{@code AxeItem} 等类已删除：
 * 武器/工具用 {@code ItemTags} 判断，攻击伤害从
 * {@code ATTRIBUTE_MODIFIERS} 组件里的 {@code ATTACK_DAMAGE} 读取，
 * 盔甲用 {@code EQUIPPABLE} 组件 + 护甲值属性评分，挖掘速度用
 * {@code TOOL} 组件的 {@code getMiningSpeed}。附魔键一律经附魔注册表
 * 解析成 Holder 再查等级。
 */
public final class ItemUtil {
    private static final Minecraft mc = Minecraft.getInstance();

    private ItemUtil() {
    }

    /* ================================================================== */
    /*  slot lookup                                                        */
    /* ================================================================== */

    /** 返回 {@code item} 在背包中的槽位（0-35；-1 表示没找到）。 */
    public static int getSlot(Item item) {
        if (mc.player == null) {
            return -1;
        }
        for (int i = 0; i < mc.player.getInventory().getNonEquipmentItems().size(); i++) {
            if (mc.player.getInventory().getItem(i).getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    /** 返回 {@code stack} 在背包中的槽位（-1 表示没找到）。 */
    public static int getSlot(ItemStack stack) {
        if (mc.player == null) {
            return -1;
        }
        for (int i = 0; i < mc.player.getInventory().getNonEquipmentItems().size(); i++) {
            if (mc.player.getInventory().getItem(i) == stack) {
                return i;
            }
        }
        return -1;
    }

    /** 所有 36 格背包物品（不含装备位）。 */
    public static List<ItemStack> getAllItems() {
        if (mc.player == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(mc.player.getInventory().getNonEquipmentItems());
    }

    public static int countItem(Item item) {
        if (mc.player == null) {
            return 0;
        }
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() == item)
                .mapToInt(ItemStack::getCount).sum();
    }

    /* ================================================================== */
    /*  armor                                                              */
    /* ================================================================== */

    private static boolean isArmor(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.EQUIPPABLE);
    }

    private static EquipmentSlot armorSlot(ItemStack stack) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable == null ? null : equippable.slot();
    }

    /** 护甲分数：护甲值 + 盔甲韧性 + 保护附魔等级。 */
    public static float getArmorScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isArmor(stack)) {
            return 0.0f;
        }
        EquipmentSlot slot = armorSlot(stack);
        if (slot == null) {
            return 0.0f;
        }
        float score = 0.0f;
        score += attributeAmount(stack, Attributes.ARMOR, slot);
        score += attributeAmount(stack, Attributes.ARMOR_TOUGHNESS, slot);
        score += enchantLevel(stack, Enchantments.PROTECTION);
        return score;
    }

    /** 某个装备位（HEAD/CHEST/LEGS/FEET）当前穿着的护甲分数。 */
    public static float getEquippedArmorScore(EquipmentSlot slot) {
        if (mc.player == null) {
            return 0.0f;
        }
        ItemStack equipped = mc.player.getItemBySlot(slot);
        return getArmorScore(equipped);
    }

    /** 某个装备位在背包+装备中的最佳护甲分数。 */
    public static float getBestArmorScore(EquipmentSlot slot) {
        List<ItemStack> items = getAllItems();
        // 已穿着的也算上，避免"最佳就是身上这件"被当作没有更好的。
        if (mc.player != null) {
            items.add(mc.player.getItemBySlot(slot));
        }
        return items.stream()
                .filter(ItemUtil::isArmor)
                .filter(stack -> armorSlot(stack) == slot)
                .map(ItemUtil::getArmorScore)
                .max(Float::compareTo)
                .orElse(0.0f);
    }

    /* ================================================================== */
    /*  weapons                                                            */
    /* ================================================================== */

    /** 主手攻击伤害（含锋利附魔加成）。 */
    public static float getSwordDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0f;
        }
        float damage = (float) attributeAmount(stack, Attributes.ATTACK_DAMAGE);
        return damage + enchantLevel(stack, Enchantments.SHARPNESS) * 0.5f;
    }

    /** 斧头伤害：攻击伤害 + 锋利加成（26.2 斧与剑共用属性模型）。 */
    public static float getAxeDamage(ItemStack stack) {
        return getSwordDamage(stack);
    }

    public static ItemStack getBestSword() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.is(net.minecraft.tags.ItemTags.SWORDS))
                .max(Comparator.comparingDouble(ItemUtil::getSwordDamage))
                .orElse(null);
    }

    public static ItemStack getBestSharpAxe() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.is(net.minecraft.tags.ItemTags.AXES))
                .filter(ItemUtil::isLegitAxe)
                .max(Comparator.comparingDouble(ItemUtil::getAxeDamage))
                .orElse(null);
    }

    public static ItemStack getBestAxe() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.is(net.minecraft.tags.ItemTags.AXES))
                .max(Comparator.comparingDouble(ItemUtil::getSwordDamage))
                .orElse(null);
    }

    /** 锋利 8+ 的斧（服务器小游戏里的"合法斧"）。 */
    public static boolean isLegitAxe(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(net.minecraft.tags.ItemTags.AXES)) {
            return false;
        }
        int sharpness = enchantLevel(stack, Enchantments.SHARPNESS);
        return sharpness >= 8 && sharpness < 50;
    }

    /* ================================================================== */
    /*  tools                                                              */
    /* ================================================================== */

    /** 挖掘速度：TOOL 组件对常见方块的挖掘速度（含效率附魔换算）。 */
    public static float getDigSpeed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0f;
        }
        var tool = stack.get(net.minecraft.core.component.DataComponents.TOOL);
        if (tool == null) {
            return 0.0f;
        }
        float speed = 0.0f;
        if (stack.is(net.minecraft.tags.ItemTags.PICKAXES)) {
            speed = tool.getMiningSpeed(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        } else if (stack.is(net.minecraft.tags.ItemTags.AXES)) {
            speed = tool.getMiningSpeed(net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState());
        } else if (stack.is(net.minecraft.tags.ItemTags.SHOVELS)) {
            speed = tool.getMiningSpeed(net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
        } else {
            return 0.0f;
        }
        int efficiency = enchantLevel(stack, Enchantments.EFFICIENCY);
        if (efficiency > 0) {
            speed += efficiency * 0.0075f;
        }
        return speed;
    }

    public static ItemStack getBestPickaxe() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.is(net.minecraft.tags.ItemTags.PICKAXES))
                .max(Comparator.comparingDouble(ItemUtil::getDigSpeed))
                .orElse(null);
    }

    public static ItemStack getBestShovel() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.is(net.minecraft.tags.ItemTags.SHOVELS))
                .max(Comparator.comparingDouble(ItemUtil::getDigSpeed))
                .orElse(null);
    }

    /* ================================================================== */
    /*  ranged / projectiles                                               */
    /* ================================================================== */

    public static float getBowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BowItem)) {
            return 0.0f;
        }
        float score = 10.0f;
        score += enchantLevel(stack, Enchantments.PUNCH);
        score += enchantLevel(stack, Enchantments.INFINITY);
        score += enchantLevel(stack, Enchantments.FLAME);
        score += enchantLevel(stack, Enchantments.POWER) / 10.0f;
        return score + (float) stack.getDamageValue() / (float) Math.max(1, stack.getMaxDamage());
    }

    public static float getBowScoreAlt(ItemStack stack) {
        return getBowScore(stack);
    }

    public static float getCrossbowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof CrossbowItem)) {
            return 0.0f;
        }
        float score = 10.0f;
        score += enchantLevel(stack, Enchantments.QUICK_CHARGE);
        score += enchantLevel(stack, Enchantments.MULTISHOT);
        score += enchantLevel(stack, Enchantments.PIERCING);
        return score;
    }

    public static ItemStack getBestBow() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() instanceof BowItem)
                .max(Comparator.comparingDouble(ItemUtil::getBowScore))
                .orElse(null);
    }

    public static ItemStack getBestBowAlt() {
        return getBestBow();
    }

    public static ItemStack getBestCrossbow() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() instanceof CrossbowItem)
                .max(Comparator.comparingDouble(ItemUtil::getCrossbowScore))
                .orElse(null);
    }

    /** 最好的投掷物（蛋/雪球），按数量取最多。 */
    public static ItemStack getBestProjectile() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty()
                        && (stack.getItem() == Items.EGG || stack.getItem() == Items.SNOWBALL))
                .max(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getWorstProjectile() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty()
                        && (stack.getItem() == Items.EGG || stack.getItem() == Items.SNOWBALL))
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getArrowStack() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() == Items.ARROW)
                .findAny().orElse(null);
    }

    public static ItemStack getFishingRodStack() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() instanceof FishingRodItem)
                .findAny().orElse(null);
    }

    /* ================================================================== */
    /*  food / blocks                                                      */
    /* ================================================================== */

    public static ItemStack getBestFoodStack() {
        return getAllItems().stream()
                .filter(ItemUtil::isFood)
                .filter(stack -> stack.getItem() != Items.GOLDEN_APPLE
                        && stack.getItem() != Items.ENCHANTED_GOLDEN_APPLE)
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static int countFood() {
        return getAllItems().stream()
                .filter(ItemUtil::isFood)
                .filter(stack -> stack.getItem() != Items.GOLDEN_APPLE
                        && stack.getItem() != Items.ENCHANTED_GOLDEN_APPLE)
                .mapToInt(ItemStack::getCount).sum();
    }

    /** 26.2 用 FOOD 组件判断食物（Item.isEdible 已删）。 */
    private static boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && stack.has(net.minecraft.core.component.DataComponents.FOOD);
    }

    public static int countBlocks() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() instanceof BlockItem)
                .mapToInt(ItemStack::getCount).sum();
    }

    public static int countFishingRods() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() instanceof FishingRodItem)
                .mapToInt(ItemStack::getCount).sum();
    }

    /** 背包里最多的方块。 */
    public static ItemStack getBestBlock() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() instanceof BlockItem)
                .max(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    /** 背包里最少的方块。 */
    public static ItemStack getWorstBlock() {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() instanceof BlockItem)
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    /* ================================================================== */
    /*  misc predicates                                                    */
    /* ================================================================== */

    /**
     * 服务器小游戏里"值得保留"的物品：金苹果、末影水晶、击退粘液球/木棍、
     * 冲击/力量弓（从 OpenZen 的 isOtherCheat/isNewItem 简化合并）。
     */
    public static boolean isWeaponItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() == Items.GOLDEN_APPLE || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE
                || stack.getItem() == Items.END_CRYSTAL) {
            return true;
        }
        if (stack.getItem() == Items.SLIME_BALL || stack.getItem() == Items.STICK) {
            return enchantLevel(stack, Enchantments.KNOCKBACK) > 1;
        }
        if (stack.getItem() instanceof BowItem) {
            return enchantLevel(stack, Enchantments.PUNCH) > 2
                    || enchantLevel(stack, Enchantments.POWER) > 3;
        }
        return false;
    }

    /** 服务器特供物品（显示名含中文提示词）或普通可保留物品。 */
    public static boolean isUsable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        if (stack.getItem() instanceof PlayerHeadItem) {
            return false;
        }
        String displayName = stack.getDisplayName().getString();
        return !displayName.contains("点击") && !displayName.contains("使用")
                && !displayName.contains("传送") && !displayName.contains("再来");
    }

    public static boolean isGoodBow(ItemStack stack) {
        return getBowScore(stack) > 10.0f && isUsable(stack);
    }

    public static boolean isGoodBowAlt(ItemStack stack) {
        return isGoodBow(stack);
    }

    /* ================================================================== */
    /*  attribute / enchantment helpers                                    */
    /* ================================================================== */

    /** 从物品的 ATTRIBUTE_MODIFIERS 组件读取指定装备位的某个属性总加成。 */
    private static double attributeAmount(ItemStack stack, Holder<Attribute> attribute, EquipmentSlot slot) {
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) {
            return 0.0;
        }
        final double[] sum = {0.0};
        modifiers.forEach(slot, (attr, modifier) -> {
            if (attr == attribute) {
                sum[0] += modifier.amount();
            }
        });
        return sum[0];
    }

    /** 从物品的 ATTRIBUTE_MODIFIERS 组件读取某个属性的总加成（主手）。 */
    private static double attributeAmount(ItemStack stack, Holder<Attribute> attribute) {
        return attributeAmount(stack, attribute, EquipmentSlot.MAINHAND);
    }

    /** 26.2：附魔键是 ResourceKey，从附魔注册表解析 Holder 再查等级。 */
    public static int enchantLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack == null || stack.isEmpty() || mc.level == null) {
            return 0;
        }
        Registry<Enchantment> registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Optional<Holder.Reference<Enchantment>> holder = registry.get(key);
        return holder.map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
    }
}
