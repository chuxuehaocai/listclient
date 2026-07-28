package dev.naominet.listclient.module.world;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPostUpdate;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.utils.BlockUtil;
import dev.naominet.listclient.utils.ClientUtils;
import dev.naominet.listclient.value.Option;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public class Scaffold extends Module {
    private static final Set<Block> BLACKLIST = Set.of(
            Blocks.ENCHANTING_TABLE, Blocks.CHEST, Blocks.ENDER_CHEST,
            Blocks.TRAPPED_CHEST, Blocks.ANVIL, Blocks.SAND, Blocks.COBWEB,
            Blocks.TORCH, Blocks.CRAFTING_TABLE, Blocks.FURNACE,
            Blocks.LILY_PAD, Blocks.DISPENSER, Blocks.STONE_PRESSURE_PLATE,
            Blocks.OAK_PRESSURE_PLATE, Blocks.NOTE_BLOCK, Blocks.DROPPER,
            Blocks.TNT, Blocks.REDSTONE_TORCH, Blocks.PLAYER_HEAD,
            Blocks.BAMBOO
    );

    public final Option keepY = new Option("KeepY", true);
    public final Option down = new Option("Down", false);

    private int startY;
    private int startSlot = -1;
    private boolean disablePending;
    private boolean notifiedNoBlocks;
    private PendingPlacement pendingPlacement;
    // Rate-limit to one placement per game tick; sendPosition can fire multiple times per tick.
    private int lastPlacedTick = -1;

    public Scaffold() {
        super("Scaffold", Category.World);
        addValues(keepY, down);
    }

    @Override
    public void onEnable() {
        BlockUtil.reset();
        pendingPlacement = null;
        disablePending = false;
        notifiedNoBlocks = false;
        startSlot = -1;
        lastPlacedTick = -1;

        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            scheduleDisable();
            return;
        }

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (selectedSlot >= 0 && selectedSlot < 9) {
            startSlot = selectedSlot;
        }
        startY = Mth.floor(mc.player.getY());
        mc.options.keySprint.setDown(false);

        if (findBlock() == -1) {
            notifyNoBlocks();
            scheduleDisable();
        }
    }

    @Override
    public void onDisable() {
        pendingPlacement = null;
        BlockUtil.reset();
        disablePending = false;
        notifiedNoBlocks = false;
        lastPlacedTick = -1;

        if (mc.player != null && startSlot >= 0 && startSlot < 9) {
            mc.player.getInventory().setSelectedSlot(startSlot);
        }
        startSlot = -1;
    }

    @EventTarget
    public void onPre(EventPlayerMotionPreUpdate event) {
        pendingPlacement = null;
        if (disablePending || mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }

        if (keepY.getValue() && mc.player.onGround()) {
            startY = Mth.floor(mc.player.getY());
        }

        int slot = findBlock();
        if (slot == -1) {
            notifyNoBlocks();
            BlockUtil.reset();
            scheduleDisable();
            return;
        }
        mc.player.getInventory().setSelectedSlot(slot);

        boolean descending = down.getValue() && mc.options.keySprint.isDown();
        if (descending) {
            mc.player.setSprinting(false);
        }

        int baseY = keepY.getValue() ? startY : Mth.floor(mc.player.getY());
        BlockPos target = new BlockPos(
                Mth.floor(mc.player.getX()),
                baseY - (descending ? 2 : 1),
                Mth.floor(mc.player.getZ())
        );
        // Keep the server-side head rotation aimed at the bridge position even
        // between placements, so it does not snap back after each placed block.
        float[] bridgeRotations = rotationsTo(new Vec3(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5
        ));
        event.setYaw(bridgeRotations[0]);
        event.setPitch(bridgeRotations[1]);

        // Do not let the support search fan out around an already filled target.
        // Without this guard, standing still makes Scaffold place a cluster nearby.
        if (!mc.level.getBlockState(target).canBeReplaced()) {
            return;
        }
        // Only place at the exact block below the player. The breadth-first helper
        // is useful for finding support, but using its alternate placement nodes
        // here creates a platform/cluster instead of a one-block bridge.
        BlockUtil.BlockData blockData = BlockUtil.findDirectSupport(mc.level, target);
        if (blockData == null) {
            return;
        }

        Vec3 hitLocation = faceLocation(blockData.position(), blockData.facing());
        BlockHitResult hitResult = new BlockHitResult(
                hitLocation, blockData.facing(), blockData.position(), false
        );
        pendingPlacement = new PendingPlacement(blockData, hitResult, slot);
    }

    @EventTarget
    public void onPost(EventPlayerMotionPostUpdate event) {
        PendingPlacement placement = pendingPlacement;
        pendingPlacement = null;
        if (placement == null || disablePending
                || mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }

        // At most one placement per game tick; sendPosition can fire multiple times per tick.
        int tick = mc.player.tickCount;
        if (tick == lastPlacedTick) {
            return;
        }

        ItemStack stack = mc.player.getInventory().getItem(placement.slot());
        if (!isValidBlock(stack)
                || !mc.level.getBlockState(placement.blockData().placedPosition()).canBeReplaced()
                || mc.level.getBlockState(placement.blockData().position()).canBeReplaced()) {
            return;
        }

        mc.player.getInventory().setSelectedSlot(placement.slot());
        stack = mc.player.getMainHandItem();
        if (!isValidBlock(stack) || !mc.player.mayUseItemAt(
                placement.blockData().position(), placement.blockData().facing(), stack)) {
            return;
        }

        InteractionResult result = mc.gameMode.useItemOn(
                mc.player, InteractionHand.MAIN_HAND, placement.hitResult()
        );
        if (result.consumesAction()) {
            lastPlacedTick = tick;
            BlockUtil.markPlaced(placement.blockData().placedPosition());
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    public int findBlock() {
        if (mc.player == null) {
            return -1;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (isValidBlock(mc.player.getInventory().getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    public boolean isDownEnabled() {
        return down.getValue();
    }

    private boolean isValidBlock(ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() > 0
                && stack.getItem() instanceof BlockItem blockItem
                && !BLACKLIST.contains(blockItem.getBlock());
    }

    private Vec3 faceLocation(BlockPos position, Direction face) {
        double x = position.getX() + 0.5;
        double y = position.getY() + 0.5;
        double z = position.getZ() + 0.5;
        return switch (face) {
            case WEST -> new Vec3(position.getX(), y, z);
            case EAST -> new Vec3(position.getX() + 1.0, y, z);
            case DOWN -> new Vec3(x, position.getY(), z);
            case UP -> new Vec3(x, position.getY() + 1.0, z);
            case NORTH -> new Vec3(x, y, position.getZ());
            case SOUTH -> new Vec3(x, y, position.getZ() + 1.0);
        };
    }

    private float[] rotationsTo(Vec3 target) {
        Vec3 eye = mc.player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[]{Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0F, 90.0F)};
    }

    private void notifyNoBlocks() {
        if (!notifiedNoBlocks) {
            notifiedNoBlocks = true;
            ClientUtils.sendMessage("Scaffold: no blocks in the hotbar");
        }
    }

    private void scheduleDisable() {
        if (disablePending) {
            return;
        }
        disablePending = true;
        mc.execute(() -> {
            if (isEnable() && disablePending) {
                setEnable(false);
            }
        });
    }

    private record PendingPlacement(BlockUtil.BlockData blockData,
                                    BlockHitResult hitResult, int slot) {
    }
}
