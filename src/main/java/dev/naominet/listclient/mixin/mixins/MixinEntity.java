package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.utils.RotationHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class MixinEntity {
    @Inject(method = "moveRelative", at = @At("HEAD"), cancellable = true)
    private void overwriteMoveRelative(float speed, Vec3 movement, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        LocalPlayer player = Minecraft.getInstance().player;
        if (entity != player || !RotationHandler.hasMoveFix(player)) {
            return;
        }

        Vec3 result = applyRotation(movement, speed, RotationHandler.getMovementYaw());
        entity.setDeltaMovement(entity.getDeltaMovement().add(result));
        ci.cancel();
    }

    private static Vec3 applyRotation(Vec3 movement, float speed, float yaw) {
        double lengthSq = movement.lengthSqr();
        if (lengthSq < 1.0e-7) {
            return Vec3.ZERO;
        }

        Vec3 normalized = (lengthSq > 1.0 ? movement.normalize() : movement).scale(speed);
        double yawRadians = yaw * (Math.PI / 180.0);
        float sinYaw = net.minecraft.util.Mth.sin(yawRadians);
        float cosYaw = net.minecraft.util.Mth.cos(yawRadians);
        return new Vec3(
                normalized.x * cosYaw - normalized.z * sinYaw,
                normalized.y,
                normalized.z * cosYaw + normalized.x * sinYaw
        );
    }
}
