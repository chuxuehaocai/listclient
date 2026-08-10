package dev.naominet.listclient.module.world;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.value.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;

public class Breaker extends Module {
    public Mode mode = new Mode("Mode", new String[]{"Single", "Range"}, "Range");
    private ArrayList<BlockPos> blocks = new ArrayList<>();
    int current;

    public Breaker() {
        super("Breaker", Category.World);
        addValues(mode);
    }

    @Override
    public void onDisable() {
        // Release whatever we were doing so the client does not keep mining.
        if (mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }
        mc.options.keyAttack.setDown(false);
        blocks.clear();
        current = 0;
    }

    @EventTarget
    public void onPreMotion(EventPlayerMotionPreUpdate e) {
        setSuffix(mode.getValue());
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }

        if (mode.isCurrentMode("Single")) {
            // Mine whatever the crosshair points at, legit-style.
            HitResult hit = mc.hitResult;
            mc.options.keyAttack.setDown(hit != null && hit.getType() == HitResult.Type.BLOCK);
            return;
        }

        // ---- Range mode ----
        if (blocks.isEmpty()) {
            initBlocksList();
        }
        if (blocks.isEmpty()) {
            return;
        }
        if (current >= blocks.size()) {
            // All targets mined — rescan around the (possibly moved) player.
            mc.gameMode.stopDestroyBlock();
            blocks.clear();
            current = 0;
            return;
        }

        BlockPos target = blocks.get(current);

        // Done: block is gone (server broke it and sent the update).
        if (mc.level.getBlockState(target).isAir()) {
            mc.gameMode.stopDestroyBlock();
            current++;
            return;
        }

        if (!mc.gameMode.isDestroying()) {
            // Begin mining. The server keeps accumulating progress from this
            // single START in ServerPlayerGameMode.tick(), so never re-send
            // START for the same block — it resets destroyProgressStart and
            // blocks harder than one tick would never break.
            if (!mc.gameMode.startDestroyBlock(target, Direction.DOWN)) {
                // Cannot mine (restricted / unreachable) — skip.
                current++;
            }
            return;
        }

        // Same target: continueDestroyBlock only updates the local progress
        // display without sending any packet (vanilla behavior while holding
        // attack). Re-sending packets here would reset server progress.
        if (!mc.gameMode.continueDestroyBlock(target, Direction.DOWN)) {
            mc.gameMode.stopDestroyBlock();
            current++;
        }
    }

    private void initBlocksList() {
        for (int i = 1; i < 4; i++) {
            Block b = mc.level.getBlockState(new BlockPos((int) mc.player.getX() + i, (int) mc.player.getY(), (int) mc.player.getZ())).getBlock();
            if (!(b instanceof AirBlock)) {
                blocks.add(new BlockPos((int) mc.player.getX() + i, (int) mc.player.getY(), (int) mc.player.getZ()));
            }

            b = mc.level.getBlockState(new BlockPos((int) mc.player.getX() - i, (int) mc.player.getY(), (int) mc.player.getZ())).getBlock();
            if (!(b instanceof AirBlock)) {
                blocks.add(new BlockPos((int) mc.player.getX() - i, (int) mc.player.getY(), (int) mc.player.getZ()));
            }

            b = mc.level.getBlockState(new BlockPos((int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ() + i)).getBlock();
            if (!(b instanceof AirBlock)) {
                blocks.add(new BlockPos((int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ() + i));
            }

            b = mc.level.getBlockState(new BlockPos((int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ() - i)).getBlock();
            if (!(b instanceof AirBlock)) {
                blocks.add(new BlockPos((int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ() - i));
            }
        }
        Block b = mc.level.getBlockState(new BlockPos((int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ())).getBlock();
        if (!(b instanceof AirBlock)) {
            blocks.add(new BlockPos((int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ()));
        }
    }
}
