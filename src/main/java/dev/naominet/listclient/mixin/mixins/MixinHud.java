package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.eventBus.EventManager;
import dev.naominet.listclient.eventBus.events.EventRender2D;
import dev.naominet.listclient.ui.hud.MaterialHotbarRenderer;
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
    @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
    private void listclient$extractMaterialHotbar(final GuiGraphicsExtractor graphics,
                                                  final DeltaTracker deltaTracker,
                                                  CallbackInfo ci) {
        ci.cancel();
        MaterialHotbarRenderer.render(graphics, deltaTracker);
    }

    @Inject(
            at = @At("RETURN"),
            method = "extractRenderState")
    private void extractRenderState(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker, CallbackInfo ci) {
        ClientUtils.runTasks();
        EventManager.instance.call(new EventRender2D(graphics, deltaTracker));
    }
}
