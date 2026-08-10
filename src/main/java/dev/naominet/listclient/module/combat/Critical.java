package dev.naominet.listclient.module.combat;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.utils.ClientUtils;
import dev.naominet.listclient.value.Mode;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.Random;

public class Critical extends Module {
    public Mode mode = new Mode("Mode", new String[]{"No Ground", "NCP Packet", "Vanilla Packet"}, "Vanilla Packet");
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
                mc.player.crit(KillAura.target);
            }

            if (mode.isCurrentMode("NCP Packet")) {
                mc.getConnection().send(
                        new ServerboundMovePlayerPacket.Pos(
                                mc.player.getX(),
                                mc.player.getY() + 0.11,
                                mc.player.getZ(),
                                false,
                                false
                        )
                );
                mc.getConnection().send(
                        new ServerboundMovePlayerPacket.Pos(
                                mc.player.getX(),
                                mc.player.getY() + 0.1100013579,
                                mc.player.getZ(),
                                false,
                                false
                        )
                );
                mc.getConnection().send(
                        new ServerboundMovePlayerPacket.Pos(
                                mc.player.getX(),
                                mc.player.getY() + 0.0000013579,
                                mc.player.getZ(),
                                false,
                                false
                        )
                );
                mc.player.crit(KillAura.target);
                ClientUtils.sendClientChatMsg("Crit "+new Random().nextInt(1000));
            }

            if (mode.isCurrentMode("Vanilla Packet")) {
                mc.getConnection().send(
                        new ServerboundMovePlayerPacket.Pos(
                                mc.player.getX(),
                                mc.player.getY() + 0.2,
                                mc.player.getZ(),
                                false,
                                false
                        )
                );
                mc.getConnection().send(
                        new ServerboundMovePlayerPacket.Pos(
                                mc.player.getX(),
                                mc.player.getY() + 0.01,
                                mc.player.getZ(),
                                false,
                                false
                        )
                );
                mc.player.crit(KillAura.target);
                ClientUtils.sendClientChatMsg("Crit "+new Random().nextInt(1000));
            }
        }
    }
}
