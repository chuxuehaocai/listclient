package dev.naominet.listclient.module.player;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;

public class Eagle extends Module {
    public Eagle() {
        super("Eagle", Category.Player);
    }

    public Block getBlockUnderPlayer(LocalPlayer player) {
        return getBlock(new BlockPos(player.getBlockX(), player.getBlockY()-1, player.getBlockZ()));
    }
    public Block getBlock(BlockPos pos) {
        if (mc.level != null) {
            return mc.level.getBlockState(pos).getBlock();
        }
        return null;
    }

    @EventTarget
    public void onUpdate(EventPlayerMotionPreUpdate event) {
        if (mc.player != null && getBlockUnderPlayer(mc.player) instanceof AirBlock) {
            if (mc.player.onGround()) {
                mc.options.keyShift.setDown(true);
            }
        }else if (mc.player != null && mc.player.onGround()) mc.options.keyShift.setDown(false);
    }

    @Override
    public void onDisable(){
        if (mc.player != null) {
            mc.options.keyShift.setDown(false);
        }
    }
}
