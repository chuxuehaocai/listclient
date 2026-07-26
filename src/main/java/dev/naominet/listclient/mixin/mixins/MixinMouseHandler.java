package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.utils.MouseData;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {
    @Inject(
            at = @At("HEAD"),
            method = "onButton"
    )
    private void onButton(long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        MouseData.mouseAction = action;
        MouseData.mouseKey = rawButtonInfo.button();
    }
}
