package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.core.ListClient;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
            method = "stop"
    )
    public void onStop(CallbackInfo ci) {
        ListClient.instance.stop();
    }
}
