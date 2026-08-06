package dev.naominet.listclient.utils;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class BlockUtil {
    private static final Direction[] FACES = {
            Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH, Direction.UP
    };
    private static final Set<Block> INTERACTABLES = Set.of(
            Blocks.CHEST, Blocks.ENDER_CHEST, Blocks.TRAPPED_CHEST,
            Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BLAST_FURNACE,
            Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL,
            Blocks.IRON_TRAPDOOR, Blocks.OAK_DOOR, Blocks.IRON_DOOR
    );

    private static BlockPos previousBlock;

    private BlockUtil() {
    }

    public static BlockData findSupport(ClientLevel level, BlockPos target) {
        if (previousBlock != null && (!isSupport(level, previousBlock)
                || previousBlock.distManhattan(target) > 4)) {
            previousBlock = null;
        }

        ArrayDeque<SearchNode> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(new SearchNode(target, 0));
        visited.add(target);

        while (!queue.isEmpty()) {
            SearchNode node = queue.removeFirst();
            BlockData support = findDirectSupportInternal(level, node.position());
            if (support != null) {
                return support;
            }

            if (node.depth() >= 2) {
                continue;
            }

            for (Direction face : FACES) {
                BlockPos next = node.position().relative(face);
                if (visited.add(next)) {
                    queue.addLast(new SearchNode(next, node.depth() + 1));
                }
            }
        }

        return null;
    }

    public static BlockData findDirectSupport(ClientLevel level, BlockPos placement) {
        if (previousBlock != null && !isSupport(level, previousBlock)) {
            previousBlock = null;
        }
        return findDirectSupportInternal(level, placement);
    }

    private static BlockData findDirectSupportInternal(ClientLevel level, BlockPos placement) {
        if (!level.getBlockState(placement).canBeReplaced()) {
            return null;
        }

        if (previousBlock != null) {
            for (Direction face : FACES) {
                if (placement.relative(face.getOpposite()).equals(previousBlock)) {
                    return new BlockData(face, previousBlock);
                }
            }
        }

        for (Direction face : FACES) {
            BlockPos support = placement.relative(face.getOpposite());
            if (isSupport(level, support)) {
                return new BlockData(face, support);
            }
        }
        return null;
    }

    private static boolean isSupport(ClientLevel level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        return !state.canBeReplaced() && !INTERACTABLES.contains(state.getBlock());
    }

    public static void markPlaced(BlockPos position) {
        previousBlock = position.immutable();
    }

    public static void reset() {
        previousBlock = null;
    }

    public record BlockData(Direction facing, BlockPos position) {
        public BlockPos placedPosition() {
            return position.relative(facing);
        }
    }

    private record SearchNode(BlockPos position, int depth) {
    }
}
