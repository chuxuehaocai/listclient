package dev.naominet.listclient.mixin.performance;

import dev.naominet.listclient.module.render.PerfBoost;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PerfBoost entity-distance culling: entities beyond the configured radius
 * stop rendering even when inside the frustum. This is the single biggest
 * win on dense servers (entity rendering + state extraction per frame is the
 * render thread's hottest loop). Purely visual – gameplay state untouched.
 */
@Mixin(Entity.class)
public class MixinEntityPerf {

    @Inject(method = "shouldRenderAtSqrDistance(D)Z",
            at = @At("RETURN"), cancellable = true)
    private void perfBoostCullByDistance(double sqrDistance, CallbackInfoReturnable<Boolean> cir) {
        double limit = PerfBoost.entityCullDistanceSqr();
        if (limit <= 0d) return; // disabled
        if (cir.getReturnValueZ() && sqrDistance > limit) {
            cir.setReturnValue(false);
        }
    }
}
