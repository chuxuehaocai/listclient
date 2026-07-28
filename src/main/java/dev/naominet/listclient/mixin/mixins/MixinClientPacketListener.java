package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.eventBus.EventManager;
import dev.naominet.listclient.eventBus.events.EventVelocity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    @Inject(
            at = @At("HEAD"),
            method = "handleSetEntityMotion",
            cancellable = true
    )
    public void onHandleSetEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || packet.id() != mc.player.getId()) {
            return;
        }

        Vec3 dm = packet.movement();
        EventVelocity event = new EventVelocity(packet.id(), dm.x, dm.y, dm.z);
        EventManager.instance.call(event);

        if (event.isCancelled()) {
            ci.cancel();
            return;
        }

        if (event.getX() != dm.x || event.getY() != dm.y || event.getZ() != dm.z) {
            // Cancel vanilla and apply modified velocity directly to the entity
            ci.cancel();
            Entity entity = mc.level.getEntity(packet.id());
            if (entity != null) {
                entity.setDeltaMovement(event.getX(), event.getY(), event.getZ());
            }
        }
    }
}

