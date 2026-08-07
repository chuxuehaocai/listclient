package dev.naominet.listclient.mixin.performance;

import dev.naominet.listclient.module.render.PerfBoost;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PerfBoost clouds toggle: with the "off" mode the cloud renderer is skipped
 * entirely – one of the cheapest big wins in the vanilla renderer (the cloud
 * mesh rebuild is per-frame CPU work on the render thread). Rendering state is
 * untouched; the sky just shows no clouds, matching the vanilla "off" option.
 */
@Mixin(CloudRenderer.class)
public class MixinCloudRenderer {

    @Inject(method = "render(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            at = @At("HEAD"), cancellable = true)
    private void perfBoostSkipClouds(int i, CloudStatus cloudStatus, float f, int j,
                                     Vec3 vec3, long l, float g, CallbackInfo ci) {
        if (PerfBoost.cloudsOff()) {
            ci.cancel();
        }
    }
}
