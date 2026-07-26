package dev.naominet.listclient.module.movement;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.utils.MoveUtils;
import net.minecraft.world.phys.Vec3;

public class Fly extends Module {
    public Fly() {
        super("Fly", Category.Movement);
    }

    @EventTarget
    public void onMovement(EventPlayerMotionPreUpdate e){
        setSuffix("Vanilla");
        Vec3 jiba = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(jiba.x, 0, jiba.z);
        if(mc.options.keyJump.isDown()){
            mc.player.setDeltaMovement(jiba.x, 0.6f, jiba.z);
        }
        if(mc.options.keyShift.isDown()){
            mc.player.setDeltaMovement(jiba.x, -0.6f, jiba.z);
        }
        if(MoveUtils.isMoving()){
            MoveUtils.strafe(1.8f);
        }
    }
}
