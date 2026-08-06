package dev.naominet.listclient.module.player;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPacket;
import dev.naominet.listclient.extension.ConnectionExtension;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.value.Mode;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class NoRotate extends Module {
    public Mode mode = new Mode("Mode", new String[]{"Ignore", "Silent"}, "Ignore");
    public NoRotate() {
        super("NoRotate", Category.Player);
        addValues(mode);
    }

    @EventTarget
    public void onPacket(EventPacket e){
        if(e.getPacket() instanceof ClientboundMoveEntityPacket.PosRot posRot){
            if(mode.getValue().equals("Ignore")){
                posRot.yRot = (byte) mc.player.getYRot();
                posRot.xRot = (byte) mc.player.getXRot();
            }

            if(mode.isCurrentMode("Silent")){
                ((ConnectionExtension) mc.getConnection()).sendPacketNoEvent(
                        new ServerboundMovePlayerPacket.PosRot(
                                mc.player.getX(),
                                mc.player.getY(),
                                mc.player.getZ(),
                                posRot.getYRot(),
                                posRot.getXRot(),
                                posRot.isOnGround(),
                                mc.player.horizontalCollision
                        )
                );
                posRot.yRot = (byte) mc.player.getYRot();
                posRot.xRot = (byte) mc.player.getXRot();
            }
        }
    }
}
