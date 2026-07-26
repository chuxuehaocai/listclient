package dev.naominet.listclient.utils;

import net.minecraft.client.Minecraft;

public class AnimationUtils {
    /**
     * Non-linear, frame-rate-independent easing: exponential approach toward
     * the target (fast start, smooth deceleration – the M3 "decelerate" feel).
     * {@code speedPerSec} ≈ how many times per second the remaining distance
     * shrinks by ~63%; 6-8 feels calm, 12+ feels snappy.
     */
    public static float easeExp(float now, float target, float speedPerSec) {
        float dt = 1f / Math.max((float) Minecraft.getInstance().getFps(), 5f);
        float k = 1f - (float) Math.exp(-speedPerSec * dt);
        float next = now + (target - now) * k;
        return Math.abs(target - next) < 0.002f ? target : next;
    }

    /** Cubic ease-out for time-based (0..1) animations. */
    public static float easeOutCubic(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    public static float animation(float animation, float target, float speedTarget) {
        float dif = (target - animation) / Math.max((float) Minecraft.getInstance().getFps(), 5) * 15;

        if (dif > 0) {
            dif = Math.max(speedTarget, dif);
            dif = Math.min(target - animation, dif);
        } else if (dif < 0) {
            dif = Math.min(-speedTarget, dif);
            dif = Math.max(target - animation, dif);
        }
        return animation + dif;
    }

    public static float animationNew(float now, float end, float multiplier, float min) {
        float betterspeedinfps = 120.0f / Minecraft.getInstance().getFps();
        float speed = Math.max((Math.abs(now - end) / multiplier), min) * betterspeedinfps;
        if (now < end) {
            if (now + speed > end) {
                now = end;
            } else {
                now += speed;
            }
        } else if (now > end) {
            if (now - speed < end) {
                now = end;
            } else {
                now -= speed;
            }
        }
        return now;
    }

    public static float animationNew(float now, float start, float end, float multiplier, float min) {
        float betterspeedinfps = 120.0f / Minecraft.getInstance().getFps();
        if ((now < start && now < end) || (now > start && now > end)) {
            now = start;
        }
        float speed = Math.max((Math.abs(start - now) / multiplier), min) * betterspeedinfps;
        if (now < end) {
            if (now + speed > end) {
                now = end;
            } else {
                now += speed;
            }
        } else if (now > end) {
            if (now - speed < end) {
                now = end;
            } else {
                now -= speed;
            }
        }
        return now;
    }
}
