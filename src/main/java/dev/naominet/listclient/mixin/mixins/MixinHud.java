package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.eventBus.EventManager;
import dev.naominet.listclient.eventBus.events.EventRender2D;
import dev.naominet.listclient.utils.ClientUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class MixinHud {
    @Inject(
            at = @At("RETURN"),
            method = "extractRenderState",
            cancellable = true)
    private void extractRenderState(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker, CallbackInfo ci) {
        ClientUtils.runTasks();
        EventRender2D eventRender2D = new EventRender2D(graphics, deltaTracker);

        EventManager.instance.call(eventRender2D);
        if(eventRender2D.isCancelled()) ci.cancel();
    }
}
