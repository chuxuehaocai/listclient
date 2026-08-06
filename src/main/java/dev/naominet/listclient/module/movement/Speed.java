package dev.naominet.listclient.module.movement;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.utils.MoveUtils;
import dev.naominet.listclient.value.Mode;
import dev.naominet.listclient.value.Numbers;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public class Speed extends Module {
    public final Mode mode = new Mode("Mode", new String[]{"Vanilla", "LowHop Vanilla", "NCP"}, "Vanilla");

    public Speed() {
        super("Speed", Category.Movement);
        addValues(mode);
    }

    @EventTarget
    public void onPlayerMotion(EventPlayerMotionPreUpdate event) {
        setSuffix(mode.getValue());
        if(mode.isCurrentMode("Vanilla")) {
            if (!MoveUtils.isMoving()) {
                return;
            }
            if (mc.player.onGround()) {
                mc.player.jumpFromGround();
            }
            MoveUtils.strafe(1f);
        }

        if(mode.isCurrentMode("LowHop Vanilla")) {
            if (!MoveUtils.isMoving()) {
                return;
            }
            if (mc.player.onGround() && !mc.options.keyJump.isDown()) {
                Vec3 deltaMovement = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(deltaMovement.x, 0.2f, deltaMovement.z);
            }
            if(mc.options.keyJump.isDown() && mc.player.onGround()){
                mc.player.jumpFromGround();
            }
            MoveUtils.strafe(1f);
        }

        if(mode.isCurrentMode("NCP")){
            if (!MoveUtils.isMoving()) {
                return;
            }
            int speedEffectBoost = mc.player.hasEffect(MobEffects.SPEED)?mc.player.getEffect(MobEffects.SPEED).getAmplifier():0;
            float speed = (float) (0.159999999 * speedEffectBoost + 0.281);
            if (mc.player.onGround()) {
                mc.player.jumpFromGround();
            }
            MoveUtils.strafe(speed);
        }
    }
}
