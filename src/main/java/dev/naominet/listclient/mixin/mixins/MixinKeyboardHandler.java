package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.eventBus.EventManager;
import dev.naominet.listclient.eventBus.events.EventKey;
import dev.naominet.listclient.eventBus.events.EventPacket;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {
    @Inject(
            at = @At("HEAD"),
            method = "keyPress"
    )
    public void onKeyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
        if(action == 1) {
            EventKey ek = new EventKey(event.key());
            EventManager.instance.call(ek);
        }
    }
}
