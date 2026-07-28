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

    @Inject(
            at = @At("HEAD"),
            method = "sendPosition",
            cancellable = true)
    public void sendPosition(CallbackInfo ci) {
        LocalPlayer localPlayer = (LocalPlayer)(Object)this;
        oldYaw = localPlayer.getYRot();
        oldPitch = localPlayer.getXRot();
        x = localPlayer.getX();
        y = localPlayer.getY();
        z = localPlayer.getZ();
        EventPlayerMotionPreUpdate event = new EventPlayerMotionPreUpdate(
                localPlayer.getX(),
                localPlayer.getY(),
                localPlayer.getZ(),
                localPlayer.getYRot(),
                localPlayer.getXRot(),
                localPlayer.onGround()
        );

        EventManager.instance.call(event);
        RotationHandler.update(localPlayer, oldYaw, oldPitch, event.getYaw(), event.getPitch());

        localPlayer.setYRot(event.getYaw());
        localPlayer.setXRot(event.getPitch());
        localPlayer.setPos(event.getX(), event.getY(), event.getZ());

        if(event.isCancelled()) ci.cancel();
    }

    @Inject(
            at = @At("RETURN"),
            method = "sendPosition",
            cancellable = true
    )
    public void hookPost(CallbackInfo ci) {
        LocalPlayer localPlayer = (LocalPlayer)(Object)this;

        localPlayer.setYRot(oldYaw);
        localPlayer.setXRot(oldPitch);
        localPlayer.setPos(x, y, z);

        EventPlayerMotionPostUpdate eventPlayerMotionPostUpdate = new EventPlayerMotionPostUpdate();
        EventManager.instance.call(eventPlayerMotionPostUpdate);

        if(eventPlayerMotionPostUpdate.isCancelled()) ci.cancel();
    }
}
