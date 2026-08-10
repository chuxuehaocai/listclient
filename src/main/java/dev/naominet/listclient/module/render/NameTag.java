package dev.naominet.listclient.module.render;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPacket;
import dev.naominet.listclient.eventBus.events.EventRender2D;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.ui.theme.M3;
import dev.naominet.listclient.utils.font.TTFFontRenderer;
import dev.naominet.listclient.value.Numbers;
import dev.naominet.listclient.value.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class NameTag extends Module {
    private static final int COLOR_PADDING = 0x78000000;
    private static final int COLOR_LIGHT_GRAY = 0xFFAAAAAA;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_RED = 0xFFFF5555;
    private static final int COLOR_GOLD = 0xFFFFD700;

    /** 服务器计分板 "belowHealth"/"health" 目标下各玩家的分数（Netty 线程写入）。 */
    public static final Map<String, AtomicInteger> scoreboardHealthMap = new ConcurrentHashMap<>();

    private final Numbers scaleSetting = new Numbers("Scale", 0.3, 0.1, 1.0, 0.01);
    private final Numbers distanceSetting = new Numbers("Max Distance", 64.0, 8.0, 256.0, 1.0);
    private final Option showHealthSetting = new Option("Invisibles", false);
    private final Option showArmorSetting = new Option("Show Artifacts", true);

    private final TTFFontRenderer mainFont = TTFFontRenderer.get(14);
    private final TTFFontRenderer iconFont = TTFFontRenderer.icon(14);

    private final Map<Entity, Vec2f> entityPositions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> itemCheckTimestamps = new HashMap<>();

    // 物品提醒跟踪（仅渲染线程访问）。
    private final Map<UUID, Set<Item>> alertedItems = new ConcurrentHashMap<>();
    private final Map<Entity, Set<ItemStack>> entityItems = new ConcurrentHashMap<>();

    private final String iconDistance = "";
    private final String iconName = "";
    private final String iconHealth = "";

    public NameTag() {
        super("NameTag", Category.Render);
        addValues(scaleSetting, distanceSetting, showHealthSetting, showArmorSetting);
    }

    @Override
    public void onEnable() {
        entityPositions.clear();
        alertedItems.clear();
        entityItems.clear();
    }

    @Override
    public void onDisable() {
        onEnable();
    }

    /* ================================================================== */
    /*  render                                                            */
    /* ================================================================== */

    @EventTarget
    public void onRender2D(EventRender2D event) {
        if (mc.level == null || mc.player == null) {
            entityPositions.clear();
            return;
        }
        GuiGraphicsExtractor g = event.getExtractor();
        float partialTick = event.getTracker().getGameTimeDeltaPartialTick(false);
        float scale = scaleSetting.floatValue();

        // 1) 收集可见玩家并投影到 GUI 坐标
        entityPositions.clear();
        List<ItemRenderData> deferredItems = new ArrayList<>();
        double rangeSq = distanceSetting.getValue() * distanceSetting.getValue();
        Set<Entity> seen = new HashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || !entity.isAlive()) {
                continue;
            }
            if (entity.isInvisible() && !showHealthSetting.getValue()) {
                continue;
            }
            if (entity.distanceToSqr(mc.player) > rangeSq) {
                continue;
            }
            if (!(entity instanceof Player)) {
                continue;
            }
            if (entity.getName().getString().startsWith("CIT-")) {
                continue;
            }
            Vec2f screen = projectToGui(entity, partialTick);
            if (screen == null) {
                continue;
            }
            entityPositions.put(entity, new Vec2f(screen.x(), screen.y() - 2.0f));
            seen.add(entity);
        }
        entityItems.keySet().removeIf(e -> !seen.contains(e));

        // 2) 每 250ms 检查一次玩家手里的高亮物品
        long now = System.currentTimeMillis();
        for (Entity entity : entityPositions.keySet()) {
            if (!(entity instanceof Player player) || !showArmorSetting.getValue()) {
                continue;
            }
            UUID uuid = player.getUUID();
            Long last = itemCheckTimestamps.get(uuid);
            if (last != null && now - last < 250L) {
                continue;
            }
            trackEntityItem(player, player.getMainHandItem());
            trackEntityItem(player, player.getOffhandItem());
            itemCheckTimestamps.put(uuid, now);
        }

        // 3) 绘制名牌（圆角矩形 + 文字），物品图标延迟到主管线
        for (Map.Entry<Entity, Vec2f> entry : entityPositions.entrySet()) {
            if (!(entry.getKey() instanceof AbstractClientPlayer player)) {
                continue;
            }
            drawNameTag(g, player, entry.getValue(), scale, deferredItems);
        }

        // 4) 物品图标走主 GUI 批量管线（无法塞进名牌的缩放矩阵）
        int seed = 1;
        for (ItemRenderData data : deferredItems) {
            g.pose().pushMatrix();
            g.pose().translate(data.position.x(), data.position.y());
            g.pose().scale(scale, scale);
            g.item(data.itemStack(), -1, -4, seed++);
            g.itemDecorations(mc.font, data.itemStack(), -3, 0);
            g.pose().popMatrix();
        }
    }

    private void drawNameTag(GuiGraphicsExtractor g, AbstractClientPlayer player, Vec2f screenPos,
                             float scale, List<ItemRenderData> deferredItems) {
        int padding = 4;
        int gap = 4;
        float corner = 6.0f;

        float ascent = mainFont.lineHeight() * 0.7f;
        float rowHeight = mainFont.lineHeight() + padding * 2;

        String displayName = player.getDisplayName().getString();
        String healthText = String.valueOf(Math.round(player.getHealth()));
        String absorbText = Math.round(player.getAbsorptionAmount()) > 0
                ? String.valueOf(Math.round(player.getAbsorptionAmount())) : "";
        String distanceText = (int) mc.player.distanceTo(player) + "m";

        Set<ItemStack> alertItems = showArmorSetting.getValue()
                ? entityItems.getOrDefault(player, Set.of()) : Set.of();

        float distIconW = iconFont.width(iconDistance);
        float distTextW = mainFont.width(distanceText);
        float distBoxW = distIconW + 2.0f + distTextW + padding * 2;
        float nameIconW = iconFont.width(iconName);
        float displayW = mainFont.width(displayName);
        float nameBoxW = nameIconW + 6.0f + displayW + padding * 2;
        float healthIconW = iconFont.width(iconHealth);
        float healthTextW = mainFont.width(healthText);
        float healthBoxW = healthIconW + 2.0f + healthTextW + padding * 2;
        float absorbBoxW = absorbText.isEmpty() ? 0.0f : healthIconW + 2.0f
                + mainFont.width(absorbText) + padding * 2;

        int boxCount = 2;
        if (!healthText.isEmpty()) boxCount++;
        if (!absorbText.isEmpty()) boxCount++;
        boxCount += alertItems.size();

        float itemTotalW = rowHeight * alertItems.size()
                + (alertItems.size() > 0 ? gap * (alertItems.size() - 1) : 0);
        float fullWidth = distBoxW + nameBoxW + healthBoxW + absorbBoxW + itemTotalW
                + gap * (boxCount > 1 ? boxCount - 1 : 0);
        float originX = -fullWidth / 2.0f;
        float originY = -rowHeight;

        g.pose().pushMatrix();
        g.pose().translate(screenPos.x(), screenPos.y());
        g.pose().scale(scale, scale);

        int oy = Math.round(originY);
        float textBaseline = originY + padding + 1;

        // 距离箱
        int bx = Math.round(originX);
        M3.roundRect(g, bx, oy, Math.round(distBoxW), Math.round(rowHeight), Math.round(corner), COLOR_PADDING);
        iconFont.drawString(g, iconDistance, bx + padding, textBaseline, COLOR_LIGHT_GRAY);
        mainFont.drawString(g, distanceText, bx + padding + distIconW + 2.0f, textBaseline - 2, COLOR_LIGHT_GRAY);

        // 名字箱
        int nx = Math.round(originX + distBoxW + gap);
        M3.roundRect(g, nx, oy, Math.round(nameBoxW + 2.0f), Math.round(rowHeight), Math.round(corner), COLOR_PADDING);
        iconFont.drawString(g, iconName, nx + padding, textBaseline, COLOR_WHITE);
        mainFont.drawString(g, displayName, nx + padding + nameIconW + 6.0f, textBaseline - 2, COLOR_WHITE);

        // 生命值箱
        int hx = Math.round(originX + distBoxW + gap + nameBoxW + gap);
        M3.roundRect(g, hx, oy, Math.round(healthBoxW), Math.round(rowHeight), Math.round(corner), COLOR_PADDING);
        iconFont.drawString(g, iconHealth, hx + padding, textBaseline, COLOR_RED);
        mainFont.drawString(g, healthText, hx + padding + healthIconW + 2.5f, textBaseline - 2, COLOR_WHITE);

        // 吸收值箱（有吸收时才画）
        if (!absorbText.isEmpty()) {
            int ax = Math.round(originX + distBoxW + gap + nameBoxW + gap + healthBoxW + gap);
            M3.roundRect(g, ax, oy, Math.round(absorbBoxW), Math.round(rowHeight), Math.round(corner), COLOR_PADDING);
            iconFont.drawString(g, iconHealth, ax + padding, textBaseline, COLOR_GOLD);
            mainFont.drawString(g, absorbText, ax + padding + healthIconW + 2.5f, textBaseline - 2, COLOR_WHITE);
        }

        // 高亮物品箱（占位矩形，图标延迟绘制）
        float itemCursor = originX + distBoxW + gap + nameBoxW + gap + healthBoxW
                + (absorbText.isEmpty() ? 0 : gap + absorbBoxW) + gap;
        if (!alertItems.isEmpty()) {
            int seed = 1;
            for (ItemStack item : alertItems) {
                if (alertedItems.getOrDefault(player.getUUID(), Set.of()).contains(item.getItem())) {
                    continue;
                }
                int ix = Math.round(itemCursor);
                M3.roundRect(g, ix, oy, Math.round(rowHeight), Math.round(rowHeight), Math.round(corner), COLOR_PADDING);
                float itemX = screenPos.x() + (itemCursor + rowHeight / 2.0f - 5.0f) * scale;
                float itemY = screenPos.y() + (originY + rowHeight / 2.0f - 5.0f) * scale;
                deferredItems.add(new ItemRenderData(item, new Vec2f(itemX, itemY), seed++));
                itemCursor += rowHeight + gap;
            }
        }
        g.pose().popMatrix();
    }

    /* ================================================================== */
    /*  projection                                                        */
    /* ================================================================== */

    /**
     * 把实体头顶点投影到 GUI 坐标。用 {@code GameRenderer.projectPointToScreen}
     * 得到 NDC（包含相机旋转、fov、视野晃动），再手动映射回 GUI 像素。
     */
    private Vec2f projectToGui(Entity entity, float partialTick) {
        double x = Mth.lerp(partialTick, entity.xo, entity.getX());
        double y = Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getBbHeight() + 0.5;
        double z = Mth.lerp(partialTick, entity.zo, entity.getZ());

        Vec3 ndc = mc.gameRenderer.projectPointToScreen(new Vec3(x, y, z));
        // projectPointToScreen 只返回 xyz/w；用投影矩阵补出 w 以拒绝镜头后的点。
        Matrix4f vp = mc.gameRenderer.mainCamera().getViewRotationProjectionMatrix(new Matrix4f());
        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        Vector4f clip = vp.transform(new Vector4f(
                (float) (x - camPos.x), (float) (y - camPos.y), (float) (z - camPos.z), 1.0f));
        if (clip.w <= 0.0f) {
            return null;
        }
        float ndcX = (float) ndc.x;
        float ndcY = (float) ndc.y;
        if (Float.isNaN(ndcX) || Float.isNaN(ndcY) || ndcX < -1.2f || ndcX > 1.2f
                || ndcY < -1.2f || ndcY > 1.2f) {
            return null;
        }
        float guiWidth = mc.getWindow().getGuiScaledWidth();
        float guiHeight = mc.getWindow().getGuiScaledHeight();
        return new Vec2f((1.0f + ndcX) * guiWidth / 2.0f, (1.0f - ndcY) * guiHeight / 2.0f);
    }

    /* ================================================================== */
    /*  packet                                                            */
    /* ================================================================== */

    @EventTarget
    public void onPacket(EventPacket event) {
        if (!(event.getPacket() instanceof ClientboundSetScorePacket packet)) {
            return;
        }
        if (mc.level == null || mc.player == null) {
            return;
        }
        String objective = packet.objectiveName();
        if (!"belowHealth".equals(objective) && !"health".equals(objective)) {
            return;
        }
        if (packet.owner().equals(mc.player.getGameProfile().name())) {
            return;
        }
        scoreboardHealthMap.computeIfAbsent(packet.owner(), ignored -> new AtomicInteger()).set(packet.score());
    }

    /* ================================================================== */
    /*  item alerts                                                       */
    /* ================================================================== */

    private boolean isNewItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item == Items.ENCHANTED_GOLDEN_APPLE || item == Items.END_CRYSTAL) {
            return true;
        }
        if (item == Items.SLIME_BALL || item == Items.STICK) {
            return enchantLevel(stack, Enchantments.KNOCKBACK) > 1;
        }
        if (item instanceof BowItem) {
            return enchantLevel(stack, Enchantments.PUNCH) > 2 || enchantLevel(stack, Enchantments.POWER) > 3;
        }
        return false;
    }

    /** 26.2：附魔键是 ResourceKey，从附魔注册表解析 Holder 再查等级。 */
    private static int enchantLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return 0;
        }
        Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        return EnchantmentHelper.getItemEnchantmentLevel(registry.getOrThrow(key), stack);
    }

    private void trackEntityItem(Player player, ItemStack stack) {
        if (!isNewItem(stack)) {
            return;
        }
        Set<ItemStack> stacks = entityItems.computeIfAbsent(player, ignored -> new HashSet<>());
        if (stacks.stream().noneMatch(existing -> ItemStack.matches(existing, stack))) {
            stacks.add(stack);
        }
    }

    private record Vec2f(float x, float y) {
    }

    private record ItemRenderData(ItemStack itemStack, Vec2f position, int seed) {
    }
}
