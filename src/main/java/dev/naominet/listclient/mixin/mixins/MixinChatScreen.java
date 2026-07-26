package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.manager.ModuleManager;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.module.render.MusicPlayer;
import dev.naominet.listclient.utils.MouseData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class MixinChatScreen {
    @Inject(method = "mouseClicked", at = @At("RETURN"))
    public void mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        for (Module m : ModuleManager.instance.getModules()) {
            // MusicPlayer widget is hosted by Interface and must receive clicks
            // even when the MusicPlayer module itself is disabled (entry-only).
            if (m instanceof MusicPlayer || m.isEnable()) {
                m.mouseClick((int) event.x(), (int) event.y(), MouseData.mouseKey);
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        for (Module m : ModuleManager.instance.getModules()) {
            if (m instanceof MusicPlayer || m.isEnable()) {
                m.doDrag(mouseX, mouseY);
            }
        }
    }
}
