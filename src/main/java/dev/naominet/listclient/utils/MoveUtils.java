package dev.naominet.listclient.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class MoveUtils {
    private static Minecraft mc = Minecraft.getInstance();

    public static boolean isMoving() {
        return mc.player != null && (mc.player.input.getMoveVector().y != 0F || mc.player.input.getMoveVector().x != 0F);
    }
    public static float getSpeed() {
        return (float) Math.sqrt(mc.player.getDeltaMovement().x * mc.player.getDeltaMovement().x + mc.player.getDeltaMovement().z * mc.player.getDeltaMovement().z);
    }
    public static void strafe() {
        strafe(getSpeed());
    }
    public static double getDirection() {
        float rotationYaw = mc.player.getYRot();

        if(mc.player.input.getMoveVector().y < 0F)
            rotationYaw += 180F;

        float forward = 1F;
        if(mc.player.input.getMoveVector().y < 0F)
            forward = -0.5F;
        else if(mc.player.input.getMoveVector().y > 0F)
            forward = 0.5F;

        if(mc.player.input.getMoveVector().x > 0F)
            rotationYaw -= 90F * forward;

        if(mc.player.input.getMoveVector().x < 0F)
            rotationYaw += 90F * forward;

        return Math.toRadians(rotationYaw);
    }
    public static void strafe(final float speed) {
        if(!isMoving())
            return;

        final double yaw = getDirection();
        Vec3 shit = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(-Math.sin(yaw) * speed, shit.y, Math.cos(yaw) * speed);
    }
}
