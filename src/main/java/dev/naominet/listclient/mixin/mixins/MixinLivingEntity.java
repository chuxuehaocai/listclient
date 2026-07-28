package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.utils.RotationHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {
    @Redirect(
            method = "jumpFromGround",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F")
    )
    private float useSilentJumpYaw(LivingEntity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (entity == player && RotationHandler.hasMoveFix(player)) {
            return RotationHandler.getCameraYaw();
        }
        return entity.getYRot();
    }
}
