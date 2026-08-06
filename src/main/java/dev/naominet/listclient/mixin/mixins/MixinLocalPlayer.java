package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.eventBus.EventManager;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPostUpdate;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.utils.RotationHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {
    @Unique
    float oldYaw, oldPitch;

    @Unique
    double x, y, z;

    @Unique
    boolean oldOnGround;

    @Inject(
            at = @At("HEAD"),
            method = "sendPosition",
            cancellable = true)
    public void sendPosition(CallbackInfo ci) {
        LocalPlayer localPlayer = (LocalPlayer) (Object) this;
        oldYaw = localPlayer.getYRot();
        oldPitch = localPlayer.getXRot();
        x = localPlayer.getX();
        y = localPlayer.getY();
        z = localPlayer.getZ();
        oldOnGround = localPlayer.onGround();

        EventPlayerMotionPreUpdate event = new EventPlayerMotionPreUpdate(
                localPlayer.getX(),
                localPlayer.getY(),
                localPlayer.getZ(),
                localPlayer.getYRot(),
                localPlayer.getXRot(),
                localPlayer.onGround()
        );

        EventManager.instance.call(event);

        // Modules already GCD-stepped. Only record — do not snap a second time
        // or the look ray used for Reach/RotationPlace will miss the packet.
        float sentYaw = event.getYaw();
        float sentPitch = event.getPitch();
        RotationHandler.update(localPlayer, oldYaw, oldPitch, sentYaw, sentPitch);

        localPlayer.setYRot(sentYaw);
        localPlayer.setXRot(sentPitch);
        localPlayer.setPos(event.getX(), event.getY(), event.getZ());
        localPlayer.setOnGround(event.isOnGround());

        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(
            at = @At("RETURN"),
            method = "sendPosition",
            cancellable = true
    )
    public void hookPost(CallbackInfo ci) {
        LocalPlayer localPlayer = (LocalPlayer) (Object) this;

        localPlayer.setYRot(oldYaw);
        localPlayer.setXRot(oldPitch);
        localPlayer.setPos(x, y, z);
        localPlayer.setOnGround(oldOnGround);

        // Post fires after the flying packet left, with the silent look still
        // tracked in RotationHandler — KillAura/Scaffold act on that look.
        EventPlayerMotionPostUpdate post = new EventPlayerMotionPostUpdate();
        EventManager.instance.call(post);

        if (post.isCancelled()) {
            ci.cancel();
        }
    }
}
