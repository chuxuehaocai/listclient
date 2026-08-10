package dev.naominet.listclient.module.world;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPostUpdate;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.value.Option;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * AutoTools — 挖掘时自动切换到对当前方块破坏速度最快的工具，
 * 松手后自动切回之前的槽位（可静默）。
 */
public class AutoTools extends Module {
    private final Option checkSword = new Option("Check Sword", true);
    private final Option switchBack = new Option("Switch Back", true);
    private final Option silent = new Option("Silent", true);

    private int previousSlot = -1;

    public AutoTools() {
        super("AutoTools", Category.World);
        addValues(checkSword, switchBack, silent);
    }

    @EventTarget
    public void onPre(EventPlayerMotionPreUpdate event) {
        if (mc.player == null || mc.gameMode == null) {
            return;
        }
        // 每 tick 发送位置包之前检查一次：正在挖掘则换成最佳工具。
        if (mc.gameMode.isDestroying()) {
            int bestSlot;
            if (checkSword.getValue() && mc.player.getMainHandItem().is(ItemTags.SWORDS)) {
                return;
            }
            BlockHitResult blockHit;
            if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK
                    && (bestSlot = this.getBestTool((blockHit = (BlockHitResult) mc.hitResult).getBlockPos())) != -1
                    && bestSlot != mc.player.getInventory().getSelectedSlot()) {
                this.previousSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(bestSlot);
            }
        } else if (this.previousSlot != -1 && this.switchBack.getValue()) {
            // 停止挖掘：立即切回之前的槽位。
            this.switchBackToPrevious();
        }
    }

    @EventTarget
    public void onPost(EventPlayerMotionPostUpdate event) {
        if (mc.player == null) {
            return;
        }
        // Silent 模式：位置包发出后补发换槽包，让服务端看到的是原槽位。
        // 客户端本地槽位已经切回，渲染与交互都不受影响。
        if (this.switchBack.getValue() && this.silent.getValue() && this.previousSlot != -1) {
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundSetCarriedItemPacket(this.previousSlot));
            }
            this.previousSlot = -1;
        }
    }

    private void switchBackToPrevious() {
        mc.player.getInventory().setSelectedSlot(this.previousSlot);
        this.previousSlot = -1;
    }

    private int getBestTool(BlockPos blockPos) {
        BlockState blockState = mc.level.getBlockState(blockPos);
        Block block = blockState.getBlock();
        int bestSlot = 0;
        float bestSpeed = 1.0f;
        for (int i = 0; i < 9; ++i) {
            int efficiencyLevel;
            ItemStack itemStack = mc.player.getInventory().getItem(i);
            // 跳过武器、空槽位、空气以及（非蜘蛛网时的）剑。
            if (itemStack.isEmpty() || blockState.isAir()
                    || itemStack.is(ItemTags.SWORDS) && !(block instanceof WebBlock)) {
                continue;
            }
            float destroySpeed = itemStack.getDestroySpeed(blockState);
            // 26.2 中挖掘速度不包含效率附魔的加成，按 1.9 公式手动补上。
            if (destroySpeed > 1.0f && !(block instanceof DropExperienceBlock) && !(block instanceof RedStoneOreBlock)
                    && (efficiencyLevel = getEfficiencyLevel(itemStack)) > 0) {
                destroySpeed += (float) (efficiencyLevel * efficiencyLevel + 1);
            }
            if (!(destroySpeed > bestSpeed)) {
                continue;
            }
            bestSlot = i;
            bestSpeed = destroySpeed;
        }
        if (bestSpeed > 1.0f) {
            return bestSlot;
        }
        return -1;
    }

    private int getEfficiencyLevel(ItemStack stack) {
        // 26.2：附魔键是 ResourceKey，需要从附魔注册表解析出 Holder 才能查等级。
        Registry<net.minecraft.world.item.enchantment.Enchantment> registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        return EnchantmentHelper.getItemEnchantmentLevel(registry.getOrThrow(Enchantments.EFFICIENCY), stack);
    }
}
