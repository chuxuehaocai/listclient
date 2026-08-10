package dev.naominet.listclient.module.render;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPacket;
import dev.naominet.listclient.eventBus.events.EventPreTick;
import dev.naominet.listclient.eventBus.events.EventWorldChange;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChestESP extends Module {
    /** Chest open/close animation packets (b0 == EVENT_SET_OPEN_COUNT, b1 == 1 means opened). */
    private final List<BlockPos> openedChestPositions = new CopyOnWriteArrayList<>();
    private final List<AABB> renderBoundingBoxes = new CopyOnWriteArrayList<>();

    public ChestESP() {
        super("ChestESP", Category.Render);
    }

    @EventTarget
    public void onWorldChange(EventWorldChange event) {
        openedChestPositions.clear();
    }

    @EventTarget
    public void onPacket(EventPacket event) {
        var packet = event.getPacket();
        if (!(packet instanceof ClientboundBlockEventPacket blockEvent)) {
            return;
        }
        if ((blockEvent.getBlock() == Blocks.CHEST || blockEvent.getBlock() == Blocks.TRAPPED_CHEST)
                && blockEvent.getB0() == 1 && blockEvent.getB1() == 1) {
            openedChestPositions.add(blockEvent.getPos());
        }
    }

    @EventTarget
    public void onTick(EventPreTick event) {
        if (mc.level == null || mc.player == null || mc.levelRenderer == null) {
            return;
        }
        renderBoundingBoxes.clear();
        for (BlockEntity blockEntity : getLoadedBlockEntities()) {
            if (!(blockEntity instanceof ChestBlockEntity chest)) {
                continue;
            }
            AABB aabb = getChestAabb(chest);
            if (aabb != null) {
                renderBoundingBoxes.add(aabb);
            }
        }
        // Collect gizmos into our own collector, then hand them to the
        // renderer directly. Minecraft's per-tick collector (collectPerTickGizmos)
        // is only active inside runTick and cannot be relied on from an event
        // fired at its HEAD — Gizmos.cuboid would throw without a collector.
        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        for (AABB aabb : renderBoundingBoxes) {
            BlockPos pos = BlockPos.containing(aabb.minX, aabb.minY, aabb.minZ);
            int fill = openedChestPositions.contains(pos)
                    ? ARGB.color(64, 255, 0, 0)
                    : ARGB.color(64, 0, 255, 0);
            try (var ignored = Gizmos.withCollector(collector)) {
                Gizmos.cuboid(aabb, GizmoStyle.fill(fill)).setAlwaysOnTop();
            }
        }
        mc.levelRenderer.addMainThreadGizmos(collector.drainGizmos());
    }

    /**
     * AABB of a chest block. For double chests only the RIGHT half carries
     * its own box; LEFT halves are skipped and merged into the RIGHT one.
     */
    private AABB getChestAabb(ChestBlockEntity chestBlockEntity) {
        BlockState blockState = chestBlockEntity.getBlockState();
        if (!blockState.hasProperty(ChestBlock.TYPE)) {
            return null;
        }
        ChestType chestType = blockState.getValue(ChestBlock.TYPE);
        if (chestType == ChestType.LEFT) {
            return null;
        }
        BlockPos pos = chestBlockEntity.getBlockPos();
        AABB aabb = new AABB(pos);
        if (chestType != ChestType.SINGLE) {
            BlockPos connectedPos = pos.relative(ChestBlock.getConnectedDirection(blockState));
            BlockState connectedState = mc.level.getBlockState(connectedPos);
            if (!connectedState.isAir() && !connectedState.canBeReplaced()) {
                aabb = aabb.minmax(new AABB(connectedPos));
            }
        }
        return aabb;
    }

    private List<BlockEntity> getLoadedBlockEntities() {
        List<BlockEntity> result = new ArrayList<>();
        int radius = Math.max(2, mc.options.getEffectiveRenderDistance()) + 3;
        ChunkPos center = mc.player.chunkPosition();
        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                if (!mc.level.hasChunk(x, z)) {
                    continue;
                }
                LevelChunk chunk = mc.level.getChunk(x, z);
                result.addAll(chunk.getBlockEntities().values());
            }
        }
        return result;
    }
}
