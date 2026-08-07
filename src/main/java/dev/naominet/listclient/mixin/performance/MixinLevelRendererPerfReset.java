package dev.naominet.listclient.mixin.performance;

import dev.naominet.listclient.module.render.PerfBoost;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-frame reset of the chunk-rebuild budget counter shared with
 * {@link MixinSectionRenderDispatcher} (the counter itself lives in
 * {@link PerfBoost} – a plain class – so no cross-mixin static coupling).
 * Runs at the very start of the world render on the render thread; the
 * section scheduler runs on worker threads, but the counter is only read /
 * written on the render thread's own scheduling path, so the static is safe
 * in practice.
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRendererPerfReset {

    /** Reset the per-frame rebuild budget at the start of the frame render. */
    @Inject(method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            at = @At("HEAD"))
    private void perfBoostResetFrameBudget(CallbackInfo ci) {
        PerfBoost.resetFrameBudget();
    }
}
