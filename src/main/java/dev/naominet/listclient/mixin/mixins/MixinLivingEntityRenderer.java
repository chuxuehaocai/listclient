package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.utils.RotationHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("RETURN")
    )
    private void renderSilentRotation(LivingEntity entity, LivingEntityRenderState state,
                                      float partialTick, CallbackInfo ci) {
        if (entity != Minecraft.getInstance().player || !RotationHandler.hasRenderRotation(Minecraft.getInstance().player)) {
            return;
        }

        float yaw = RotationHandler.getRenderYaw(partialTick);
        state.bodyRot = yaw;
        state.yRot = Mth.wrapDegrees(yaw - state.bodyRot);
        state.xRot = Mth.clamp(RotationHandler.getRenderPitch(partialTick), -90.0F, 90.0F);
    }
}
