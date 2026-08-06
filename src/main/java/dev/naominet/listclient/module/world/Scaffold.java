package dev.naominet.listclient.module.world;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPostUpdate;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.utils.BlockUtil;
import dev.naominet.listclient.utils.ClientUtils;
import dev.naominet.listclient.utils.MoveUtils;
import dev.naominet.listclient.utils.RotationHandler;
import dev.naominet.listclient.utils.RotationUtil;
import dev.naominet.listclient.value.Numbers;
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

/**
 * Grim-safe Scaffold (OpenZen-style):
 * <ul>
 *   <li>GCD-stepped silent rotations toward a stable face hit vec.</li>
 *   <li>Place only when the sent look ray hits that face (RotationPlace).</li>
 *   <li>Place after flying so post-flying raytrace matches.</li>
 *   <li>No per-tick random hit points (those prevent GCD convergence).</li>
 * </ul>
 */
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

    public final Option keepY = new Option("KeepY", false);
    public final Option down = new Option("Down", false);
    public final Option eagle = new Option("Eagle", true);
    public final Option rayTrace = new Option("RayTrace", true);
    public final Numbers rotationSpeed = new Numbers("RotationSpeed", 120.0, 30.0, 180.0, 1.0);
    public final Numbers placeRange = new Numbers("PlaceRange", 4.5, 2.0, 4.5, 0.1);

    private int startY;
    private int startSlot = -1;
    private boolean disablePending;
    private boolean notifiedNoBlocks;
    private PendingPlacement pendingPlacement;
    private int lastPlacedTick = -1;
    private float lastYawDelta;
    private float lastPitchDelta;
    private boolean ownedShift;

    public Scaffold() {
        super("Scaffold", Category.World);
        addValues(keepY, down, eagle, rayTrace, rotationSpeed, placeRange);
    }

    @Override
    public void onEnable() {
        BlockUtil.reset();
        pendingPlacement = null;
        disablePending = false;
        notifiedNoBlocks = false;
        startSlot = -1;
        lastPlacedTick = -1;
        lastYawDelta = 0.0F;
        lastPitchDelta = 0.0F;
        ownedShift = false;

        if (!hasActiveClientWorld()) {
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
        releaseShift();

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

        if (eagle.getValue() && mc.player.onGround() && MoveUtils.isMoving() && isOnBlockEdge(0.3F)) {
            holdShift();
        } else {
            releaseShift();
        }

        int baseY = keepY.getValue() ? startY : Mth.floor(mc.player.getY());
        BlockPos under = new BlockPos(
                Mth.floor(mc.player.getX()),
                baseY - (descending ? 2 : 1),
                Mth.floor(mc.player.getZ())
        );

        float fromYaw = RotationHandler.isInitialized()
                ? RotationHandler.getSentYaw()
                : mc.player.getYRot();
        float fromPitch = RotationHandler.isInitialized()
                ? RotationHandler.getSentPitch()
                : mc.player.getXRot();

        BlockUtil.BlockData blockData = null;
        if (mc.level.getBlockState(under).canBeReplaced()) {
            blockData = BlockUtil.findDirectSupport(mc.level, under);
            if (blockData == null) {
                blockData = BlockUtil.findSupport(mc.level, under);
            }
            if (blockData != null && !under.equals(blockData.placedPosition())) {
                blockData = null;
            }
        }

        float targetYaw;
        float targetPitch;
        Vec3 hitLocation = null;
        if (blockData != null) {
            hitLocation = RotationUtil.faceHitVec(blockData.position(), blockData.facing());
            float[] placeRots = RotationUtil.rotationTo(mc.player.getEyePosition(1.0F), hitLocation);
            targetYaw = placeRots[0];
            targetPitch = placeRots[1];
        } else {
            // Hold a bridge-ish look under the feet so the head does not snap
            // back to the camera between placements.
            float[] hold = RotationUtil.rotationTo(mc.player.getEyePosition(1.0F), new Vec3(
                    under.getX() + 0.5, under.getY() + 0.5, under.getZ() + 0.5));
            targetYaw = hold[0];
            targetPitch = hold[1];
        }

        float maxStep = rotationSpeed.floatValue();
        if (!MoveUtils.isMoving()) {
            maxStep = 180.0F;
        }
        float[] stepped = RotationHandler.stepToward(fromYaw, fromPitch, targetYaw, targetPitch, maxStep);
        // Anti-dupe jitter is optional and must NOT push the look off the block.
        // Try the jittered look first; fall back to the plain GCD step if it misses.
        float[] jittered = RotationHandler.breakDuplicateDelta(
                stepped[0], stepped[1], fromYaw, fromPitch, lastYawDelta, lastPitchDelta);

        float sendYaw = stepped[0];
        float sendPitch = stepped[1];
        boolean canPlace = false;
        if (blockData != null && hitLocation != null) {
            if (!rayTrace.getValue()) {
                sendYaw = jittered[0];
                sendPitch = jittered[1];
                canPlace = true;
            } else if (RotationUtil.canRayTraceBlock(
                    jittered[0], jittered[1],
                    blockData.position(), blockData.facing(),
                    placeRange.getValue())) {
                sendYaw = jittered[0];
                sendPitch = jittered[1];
                canPlace = true;
            } else if (RotationUtil.canRayTraceBlock(
                    stepped[0], stepped[1],
                    blockData.position(), blockData.facing(),
                    placeRange.getValue())) {
                sendYaw = stepped[0];
                sendPitch = stepped[1];
                canPlace = true;
            }
            // else: keep holding the stepped look; do not place this tick
        } else {
            sendYaw = jittered[0];
            sendPitch = jittered[1];
        }

        lastYawDelta = Math.abs(Mth.wrapDegrees(sendYaw - fromYaw));
        lastPitchDelta = Math.abs(sendPitch - fromPitch);
        event.setYaw(sendYaw);
        event.setPitch(sendPitch);

        if (canPlace && blockData != null && hitLocation != null) {
            BlockHitResult hitResult = new BlockHitResult(
                    hitLocation, blockData.facing(), blockData.position(), false
            );
            pendingPlacement = new PendingPlacement(blockData, hitResult, slot);
        }
    }

    @EventTarget
    public void onPost(EventPlayerMotionPostUpdate event) {
        PendingPlacement placement = pendingPlacement;
        pendingPlacement = null;
        if (placement == null || disablePending
                || mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }

        int tick = mc.player.tickCount;
        if (tick == lastPlacedTick) {
            return;
        }

        float yaw = RotationHandler.getSentYaw();
        float pitch = RotationHandler.getSentPitch();

        // Re-validate with the rotation that actually went out.
        BlockHitResult liveHit = RotationUtil.rayTraceBlock(yaw, pitch, placeRange.getValue());
        if (rayTrace.getValue()) {
            if (liveHit == null
                    || !liveHit.getBlockPos().equals(placement.blockData().position())
                    || liveHit.getDirection() != placement.blockData().facing()) {
                return;
            }
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

        BlockHitResult toUse = liveHit != null ? liveHit : placement.hitResult();
        InteractionResult result = mc.gameMode.useItemOn(
                mc.player, InteractionHand.MAIN_HAND, toUse
        );
        if (result.consumesAction()) {
            lastPlacedTick = tick;
            BlockUtil.markPlaced(placement.blockData().placedPosition());
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else {
            BlockUtil.reset();
        }
    }

    private boolean isOnBlockEdge(float inflate) {
        if (mc.level == null || mc.player == null || !mc.player.onGround()) {
            return false;
        }
        // True when the shrunk-under box has no collisions → standing on a ledge.
        return !mc.level.getBlockCollisions(
                mc.player,
                mc.player.getBoundingBox().move(0.0, -0.5, 0.0).inflate(-inflate, 0.0, -inflate)
        ).iterator().hasNext();
    }

    private void holdShift() {
        if (!ownedShift) {
            ownedShift = true;
            mc.options.keyShift.setDown(true);
        }
    }

    private void releaseShift() {
        if (ownedShift) {
            ownedShift = false;
            mc.options.keyShift.setDown(false);
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

    public int getAvailableBlockCount() {
        if (mc.player == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (isValidBlock(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public boolean isDownEnabled() {
        return down.getValue();
    }

    public static boolean isValidBlock(ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() > 0
                && stack.getItem() instanceof BlockItem blockItem
                && !BLACKLIST.contains(blockItem.getBlock());
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
        if (mc == null) {
            return;
        }
        mc.execute(() -> {
            if (isEnable() && disablePending) {
                setEnable(false);
            }
        });
    }

    private boolean hasActiveClientWorld() {
        return mc != null && mc.player != null && mc.level != null && mc.gameMode != null;
    }

    private record PendingPlacement(BlockUtil.BlockData blockData,
                                    BlockHitResult hitResult, int slot) {
    }
}
