package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.core.ListClient;
import dev.naominet.listclient.eventBus.EventManager;
import dev.naominet.listclient.eventBus.events.EventPreTick;
import dev.naominet.listclient.eventBus.events.EventWorldUpdate;
import dev.naominet.listclient.manager.ModuleManager;
import dev.naominet.listclient.module.render.ESP;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.minecraft.client.Minecraft.checkModStatus;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(
            at = @At("RETURN"),
            method = "<init>"
    )
    private static void onInit(CallbackInfo ci) {
        ListClient.instance.start();
    }
    
    /**
     * @author openai
     * @reason 你妈死了
     */
    @Overwrite
    private String createTitle() {
        StringBuilder builder = new StringBuilder("Minecraft (mzk Modified)");
        if (checkModStatus().shouldReportAsModified()) {
            builder.append("*");
        }

        builder.append(" ");
        builder.append(SharedConstants.getCurrentVersion().name());
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.getConnection().isConnected()) {
            builder.append(" - ");
            ServerData server = Minecraft.getInstance().getCurrentServer();
            if (Minecraft.getInstance().getSingleplayerServer() != null && !Minecraft.getInstance().getSingleplayerServer().isPublished()) {
                builder.append(I18n.get("title.singleplayer", new Object[0]));
            } else if (server != null && server.isRealm()) {
                builder.append(I18n.get("title.multiplayer.realms", new Object[0]));
            } else if (Minecraft.getInstance().getSingleplayerServer() == null && (server == null || !server.isLan())) {
                builder.append(I18n.get("title.multiplayer.other", new Object[0]));
            } else {
                builder.append(I18n.get("title.multiplayer.lan", new Object[0]));
            }
        }

        return builder.toString();
    }

    @Inject(
            at = @At("HEAD"),
            method = "close"
    )
    public void onClose(CallbackInfo ci) {
        ListClient.instance.stop();
    }

    @Inject(
            at = @At("HEAD"),
            method = "shouldEntityAppearGlowing",
            cancellable = true
    )
    private void applyEspOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        ESP esp = ModuleManager.instance.getModuleByClazz(ESP.class);
        if (esp != null && esp.isEnable() && esp.shouldOutline(entity)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            at = @At("HEAD"),
            method = "runTick"
    )
    public void onPreTick(CallbackInfo ci) {
        EventManager.instance.call(new EventPreTick());
    }

    @Inject(
            method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V",
            at = @At("HEAD")
    )
    public void onWorldUpdate(CallbackInfo ci){
        EventManager.instance.call(new EventWorldUpdate());
    }
}
