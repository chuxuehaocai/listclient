package dev.naominet.listclient.utils;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class RotationHandler {
    private static LocalPlayer player;
    private static int lastTick = Integer.MIN_VALUE;
    private static float previousYaw;
    private static float previousPitch;
    private static float currentYaw;
    private static float currentPitch;
    private static float clientYaw;
    private static float clientPitch;
    private static boolean initialized;
    private static boolean rotating;
    private static boolean yawRewritten;

    private RotationHandler() {
    }

    public static void update(LocalPlayer localPlayer, float cameraYaw, float cameraPitch,
                              float sentYaw, float sentPitch) {
        if (player != localPlayer || localPlayer.tickCount < lastTick) {
            reset(localPlayer, cameraYaw, cameraPitch);
        }

        if (localPlayer.tickCount != lastTick) {
            previousYaw = initialized ? currentYaw : cameraYaw;
            previousPitch = initialized ? currentPitch : cameraPitch;
            lastTick = localPlayer.tickCount;
        }

        currentYaw = sentYaw;
        currentPitch = sentPitch;
        clientYaw = cameraYaw;
        clientPitch = cameraPitch;
        initialized = true;
        yawRewritten = Float.compare(sentYaw, clientYaw) != 0;
        rotating = yawRewritten || Float.compare(sentPitch, clientPitch) != 0;
    }

    public static boolean hasMoveFix(LocalPlayer localPlayer) {
        return player == localPlayer && initialized && rotating && yawRewritten;
    }

    public static boolean hasRenderRotation(LocalPlayer localPlayer) {
        return player == localPlayer && initialized && rotating;
    }

    public static Vec3 applyMoveFix(Vec3 movement) {
        if (movement.x == 0.0 && movement.z == 0.0) {
            return movement;
        }

        Vec3 intendedDirection = rotateHorizontal(movement.x, movement.z, clientYaw).normalize();
        double bestDot = -Double.MAX_VALUE;
        double bestStrafe = 0.0;
        double bestForward = 0.0;

        for (int forward = -1; forward <= 1; forward++) {
            for (int strafe = -1; strafe <= 1; strafe++) {
                if (forward == 0 && strafe == 0) {
                    continue;
                }

                Vec3 candidateDirection = rotateHorizontal(strafe, forward, currentYaw).normalize();
                double dot = intendedDirection.dot(candidateDirection);
                if (dot > bestDot) {
                    bestDot = dot;
                    bestStrafe = strafe;
                    bestForward = forward;
                }
            }
        }

        return new Vec3(bestStrafe, movement.y, bestForward);
    }

    private static Vec3 rotateHorizontal(double strafe, double forward, float yaw) {
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return new Vec3(strafe * cos - forward * sin, 0.0, forward * cos + strafe * sin);
    }

    public static float getCameraYaw() {
        return clientYaw;
    }

    public static float getMovementYaw() {
        return currentYaw;
    }

    public static float getRenderYaw(float partialTick) {
        return Mth.rotLerp(partialTick, previousYaw, currentYaw);
    }

    public static float getRenderPitch(float partialTick) {
        return Mth.lerp(partialTick, previousPitch, currentPitch);
    }

    private static void reset(LocalPlayer localPlayer, float yaw, float pitch) {
        player = localPlayer;
        lastTick = Integer.MIN_VALUE;
        previousYaw = currentYaw = yaw;
        previousPitch = currentPitch = pitch;
        initialized = false;
        rotating = false;
        yawRewritten = false;
    }
}
