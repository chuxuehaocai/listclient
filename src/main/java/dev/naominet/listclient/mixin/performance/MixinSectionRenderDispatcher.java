package dev.naominet.listclient.mixin.performance;

import dev.naominet.listclient.module.render.PerfBoost;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PerfBoost chunk-rebuild budget: while the module is active with a budget,
 * at most {@code PerfBoost.chunkBudget} section recompiles are scheduled per
 * rendered frame. Further rebuilds are skipped for that frame and re-queued by
 * the section's own dirty flag the next frame, so nothing is lost – the queue
 * simply drains more slowly (the classic vanilla "rebuild storm" under heavy
 * block changes / fast movement is damped).
 * <p>
 * Skipping a rebuild is always safe: the section keeps its last compiled mesh
 * until the next frame. The shared per-frame counter lives in
 * {@link PerfBoost} (reset by {@code MixinLevelRendererPerfReset}).
 */
@Mixin(SectionRenderDispatcher.class)
public class MixinSectionRenderDispatcher {

    @Inject(method = "schedule(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection$SectionTask;)V",
            at = @At("HEAD"), cancellable = true)
    private void perfBoostThrottleRebuilds(SectionRenderDispatcher.RenderSection.SectionTask task, CallbackInfo ci) {
        int budget = PerfBoost.chunkBudget();
        if (budget <= 0) return; // disabled
        // Only throttles recompiles (mesh rebuilds), not first-time compiles.
        if (!task.isRecompile()) return;
        if (PerfBoost.perfScheduledRebuilds >= budget) {
            ci.cancel();
        } else {
            PerfBoost.perfScheduledRebuilds++;
        }
    }
}
