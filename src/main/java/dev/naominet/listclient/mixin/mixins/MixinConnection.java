package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.eventBus.EventManager;
import dev.naominet.listclient.eventBus.events.EventPacket;
import dev.naominet.listclient.extension.ConnectionExtension;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinConnection implements ConnectionExtension {
    @Shadow
    private Channel channel;

    @Inject(
            at = @At("HEAD"),
            method = "doSendPacket",
            cancellable = true
    )
    public void sendPacketHook(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        EventPacket ep = new EventPacket(packet, false);
        EventManager.instance.call(ep);

        if(ep.isCancelled()) ci.cancel();
    }

    @Inject(
            at = @At("HEAD"),
            method = "channelRead0*",
            cancellable = true
    )
    public void receivePacketHook(final ChannelHandlerContext ctx, final Packet<?> packet, CallbackInfo ci) {
        EventPacket ep = new EventPacket(packet, true);
        EventManager.instance.call(ep);

        if(ep.isCancelled()) ci.cancel();
    }

    @Override
    public void sendPacketNoEvent(Packet<?> packet) {
        this.channel.writeAndFlush(packet, this.channel.voidPromise());
    }
}
