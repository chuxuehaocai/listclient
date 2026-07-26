package dev.naominet.listclient.utils;

import net.minecraft.client.Minecraft;

public class AnimationUtils {
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
