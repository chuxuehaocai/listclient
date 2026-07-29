package dev.naominet.listclient.module.combat;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.eventBus.events.EventVelocity;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.value.Mode;
import dev.naominet.listclient.value.Numbers;
import net.minecraft.world.phys.Vec3;

public class Velocity extends Module {
    public final Mode mode = new Mode("Mode", new String[]{"Hypixel", "Custom", "Prediction", "CubeCraft"}, "Custom");
    public final Numbers h = new Numbers("H", 0.0, 0.0, 100.0, 1.0);
    public final Numbers v = new Numbers("V", 0.0, 0.0, 100.0, 1.0);

    private boolean handlingVelocity;
    private int velocityTicks;

    public Velocity() {
        super("Velocity", Category.Combat);
        addValues(mode, h, v);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @EventTarget
    public void onVelocity(EventVelocity event) {
        if (mc.player == null || event.getEntityId() != mc.player.getId()) {
            return;
        }

        if (mode.isCurrentMode("Custom")) {
            event.setX(event.getX() * (h.intValue() / 100.0));
            event.setY(event.getY() * (v.intValue() / 100.0));
            event.setZ(event.getZ() * (h.intValue() / 100.0));
        } else if (mode.isCurrentMode("Hypixel")) {
            Vec3 current = mc.player.getDeltaMovement();
            event.setX(current.x);
            event.setZ(current.z);
        } else if (mode.isCurrentMode("Prediction")) {
            // Simplified: cancel the velocity packet entirely
            event.setCancelled(true);
        } else if (mode.isCurrentMode("CubeCraft")) {
            // Let the velocity apply, then reverse+halve after 2 ticks
            reset();
            handlingVelocity = true;
        }
    }

    @EventTarget
    public void onMotionPre(EventPlayerMotionPreUpdate event) {
        setSuffix(mode.getValue());
        if (!mode.isCurrentMode("CubeCraft") || !handlingVelocity || mc.player == null) {
            return;
        }
        Vec3 vel = mc.player.getDeltaMovement();
        if (vel.lengthSqr() == 0.0) {
            reset();
            return;
        }
        velocityTicks++;
        if (velocityTicks >= 2) {
            mc.player.setDeltaMovement(vel.x * -0.5, vel.y, vel.z * -0.5);
            reset();
        }
    }

    private void reset() {
        handlingVelocity = false;
        velocityTicks = 0;
    }
}
