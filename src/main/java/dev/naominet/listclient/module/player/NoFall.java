package dev.naominet.listclient.module.player;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.value.Mode;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class NoFall extends Module {
    public Mode mode = new Mode("Mode", new String[]{"On Ground Spoof"}, "On Ground Spoof");
    public NoFall() {
        super("NoFall", Category.Player);
        addValues(mode);
    }

    @EventTarget
    public void onUpdate(EventPlayerMotionPreUpdate e){
        setSuffix(mode.getValue());

        if(mode.isCurrentMode("On Ground Spoof")){
            if(mc.player.fallDistance > 2.5){
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.position(), true, mc.player.horizontalCollision));
            }
        }
    }
}
