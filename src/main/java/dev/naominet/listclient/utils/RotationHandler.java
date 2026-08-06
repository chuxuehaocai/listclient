package dev.naominet.listclient.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Tracks the rotation that went out on the last flying packet and helps modules
 * produce GCD-legal steps toward a target.
 *
 * Important: {@link #update} records the rotation the mixin is about to send
 * and does <b>not</b> snap a second time. Modules call {@link #stepToward}
 * once so the look ray used for Reach / RotationPlace matches the packet.
 */
public final class RotationHandler {
    private static final Minecraft mc = Minecraft.getInstance();

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

    /**
     * Record the rotation that will be written into the outgoing flying packet.
     * {@code sentYaw/sentPitch} must already be final (GCD-stepped by the module).
     */
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
        currentPitch = Mth.clamp(sentPitch, -90.0F, 90.0F);
        clientYaw = cameraYaw;
        clientPitch = cameraPitch;
        initialized = true;
        yawRewritten = Float.compare(currentYaw, clientYaw) != 0;
        rotating = yawRewritten || Float.compare(currentPitch, clientPitch) != 0;
    }

    /**
     * One GCD-quantised step from the last sent rotation toward the target.
     * Caps the angular step so Aim checks do not see 180° single-tick snaps.
     */
    public static float[] stepToward(float targetYaw, float targetPitch, float maxStep) {
        float fromYaw = initialized ? currentYaw : (mc.player != null ? mc.player.getYRot() : targetYaw);
        float fromPitch = initialized ? currentPitch : (mc.player != null ? mc.player.getXRot() : targetPitch);
        return stepToward(fromYaw, fromPitch, targetYaw, targetPitch, maxStep);
    }

    public static float[] stepToward(float fromYaw, float fromPitch,
                                     float targetYaw, float targetPitch, float maxStep) {
        float yawDiff = Mth.wrapDegrees(targetYaw - fromYaw);
        float pitchDiff = targetPitch - fromPitch;
        float dist = (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
        float nextYaw;
        float nextPitch;
        if (dist <= maxStep || dist < 1.0E-4F) {
            nextYaw = fromYaw + yawDiff;
            nextPitch = fromPitch + pitchDiff;
        } else {
            float scale = maxStep / dist;
            nextYaw = fromYaw + yawDiff * scale;
            nextPitch = fromPitch + pitchDiff * scale;
        }
        nextPitch = Mth.clamp(nextPitch, -90.0F, 90.0F);
        return snapToGcd(nextYaw, nextPitch, fromYaw, fromPitch);
    }

    /**
     * Apply vanilla mouse GCD quantisation relative to {@code prevYaw/prevPitch}.
     */
    public static float[] snapToGcd(float yaw, float pitch, float prevYaw, float prevPitch) {
        float gcd = mouseGcd();
        if (gcd <= 1.0E-8F) {
            return new float[]{yaw, Mth.clamp(pitch, -90.0F, 90.0F)};
        }
        float yawDelta = Mth.wrapDegrees(yaw - prevYaw);
        float pitchDelta = pitch - prevPitch;
        // Round-to-nearest GCD step. Using % on floats is not stable under
        // re-application and can freeze or drift the look ray.
        float snappedYawDelta = Math.round(yawDelta / gcd) * gcd;
        float snappedPitchDelta = Math.round(pitchDelta / gcd) * gcd;
        return new float[]{
                prevYaw + snappedYawDelta,
                Mth.clamp(prevPitch + snappedPitchDelta, -90.0F, 90.0F)
        };
    }

    public static float mouseGcd() {
        if (mc.options == null) {
            return 0.15F;
        }
        double sensitivity = mc.options.sensitivity().get();
        float f = (float) (sensitivity * 0.6F + 0.2F);
        return f * f * f * 1.2F;
    }

    /**
     * Break identical consecutive deltas (OpenZen DuplicateRotPlace / Grim
     * AimDuplicateLook) with a sub-degree GCD-aligned nudge.
     */
    public static float[] breakDuplicateDelta(float yaw, float pitch,
                                              float prevYaw, float prevPitch,
                                              float lastYawDelta, float lastPitchDelta) {
        float yawDelta = Math.abs(Mth.wrapDegrees(yaw - prevYaw));
        float pitchDelta = Math.abs(pitch - prevPitch);
        float gcd = Math.max(mouseGcd(), 1.0E-3F);
        float outYaw = yaw;
        float outPitch = pitch;
        if (yawDelta > 2.0F && Math.abs(yawDelta - lastYawDelta) < 1.0E-3F) {
            outYaw = yaw + Math.copySign(gcd, Math.random() < 0.5 ? -1.0F : 1.0F);
        }
        if (pitchDelta > 2.0F && Math.abs(pitchDelta - lastPitchDelta) < 1.0E-3F) {
            outPitch = Mth.clamp(pitch + Math.copySign(gcd * 0.5F, Math.random() < 0.5 ? -1.0F : 1.0F),
                    -90.0F, 90.0F);
        }
        return new float[]{outYaw, outPitch};
    }

    public static boolean hasMoveFix(LocalPlayer localPlayer) {
        return player == localPlayer && initialized && rotating && yawRewritten;
    }

    public static boolean hasRenderRotation(LocalPlayer localPlayer) {
        return player == localPlayer && initialized && rotating;
    }

    public static boolean isRotating() {
        return initialized && rotating;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static float getCameraYaw() {
        return clientYaw;
    }

    public static float getCameraPitch() {
        return clientPitch;
    }

    public static float getMovementYaw() {
        return currentYaw;
    }

    public static float getSentYaw() {
        return currentYaw;
    }

    public static float getSentPitch() {
        return currentPitch;
    }

    public static float getPreviousSentYaw() {
        return previousYaw;
    }

    public static float getPreviousSentPitch() {
        return previousPitch;
    }

    public static float getRenderYaw(float partialTick) {
        return Mth.rotLerp(partialTick, previousYaw, currentYaw);
    }

    public static float getRenderPitch(float partialTick) {
        return Mth.lerp(partialTick, previousPitch, currentPitch);
    }

    public static Vec3 getSentLookVector() {
        // Vec3.directionFromRotation(rotX, rotY) = (pitch, yaw)
        return Vec3.directionFromRotation(currentPitch, currentYaw);
    }

    private static void reset(LocalPlayer localPlayer, float yaw, float pitch) {
        player = localPlayer;
        lastTick = Integer.MIN_VALUE;
        previousYaw = currentYaw = yaw;
        previousPitch = currentPitch = pitch;
        clientYaw = yaw;
        clientPitch = pitch;
        initialized = false;
        rotating = false;
        yawRewritten = false;
    }
}
