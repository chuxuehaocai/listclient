package dev.naominet.listclient.module.movement;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.utils.MoveUtils;

public class Speed extends Module {
    public Speed() {
        super("Speed", Category.Movement);
    }

    @EventTarget
    public void onPlayerMotion(EventPlayerMotionPreUpdate event) {
        setSuffix("Vanilla");
        if(MoveUtils.isMoving()) {
            if(mc.player.onGround()){
                mc.player.jumpFromGround();
            }

            MoveUtils.strafe(0.8f);
        }
    }
}
