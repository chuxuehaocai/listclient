package dev.naominet.listclient.module.combat;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.value.Mode;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class Critical extends Module {
    public Mode mode = new Mode("Mode", new String[]{"No Ground", "Packet"}, "Packet");
    public Critical(){
        super("Critical", Category.Combat);
        addValues(mode);
    }

    @EventTarget
    public void onEvent(EventPlayerMotionPreUpdate e){
        setSuffix(mode.getValue());
        if(KillAura.target != null && mc.player.onGround()) {
            if (mode.isCurrentMode("No Ground")) {
                e.setOnGround(false);
            }

            if (mode.isCurrentMode("Packet")) {
                mc.getConnection().send(
                        new ServerboundMovePlayerPacket.Pos(
                                mc.player.getX(),
                                mc.player.getY() + 0.12,
                                mc.player.getZ(),
                                false,
                                false
                        )
                );
                mc.getConnection().send(
                        new ServerboundMovePlayerPacket.Pos(
                                mc.player.getX(),
                                mc.player.getY() + 0.22,
                                mc.player.getZ(),
                                false,
                                false
                        )
                );
                mc.getConnection().send(
                        new ServerboundMovePlayerPacket.Pos(
                                mc.player.getX(),
                                mc.player.getY() + 0.0021,
                                mc.player.getZ(),
                                false,
                                false
                        )
                );
            }
        }
    }
}
