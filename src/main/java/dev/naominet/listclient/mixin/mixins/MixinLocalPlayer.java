package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.eventBus.EventManager;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {
    @Inject(
            at = @At("HEAD"),
            method = "sendPosition",
            cancellable = true)
    public void sendPosition(CallbackInfo ci) {
        LocalPlayer localPlayer = (LocalPlayer)(Object)this;
        EventPlayerMotionPreUpdate event = new EventPlayerMotionPreUpdate(
                localPlayer.getX(),
                localPlayer.getY(),
                localPlayer.getZ(),
                localPlayer.getYRot(),
                localPlayer.getXRot(),
                localPlayer.onGround()
        );

        EventManager.instance.call(event);

        if(event.isCancelled()) ci.cancel();
    }
}
