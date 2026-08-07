package dev.naominet.listclient.mixin.performance;

import dev.naominet.listclient.module.render.PerfBoost;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * PerfBoost particle cap: when the module is active, no more than
 * {@code PerfBoost.maxParticles} living particles exist at once. Skipping the
 * add is purely cosmetic (a particle that never spawns) – game state is
 * untouched. Particles beyond the cap simply don't appear.
 * <p>
 * The live count is sampled at most every 100ms (a frame's worth of spawns
 * slips through the throttle, which is exactly what we want – no per-particle
 * bookkeeping in the hot add() path, just a cheap cached count).
 */
@Mixin(ParticleEngine.class)
public class MixinParticleEngine {

    @Shadow
    @Final
    private Map<ParticleRenderType, ParticleGroup<?>> particles;

    @Unique
    private static long perfLastSampleMs = Long.MIN_VALUE;

    @Unique
    private static int perfCachedCount;

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V",
            at = @At("HEAD"), cancellable = true)
    private void perfBoostCapParticles(Particle particle, CallbackInfo ci) {
        int cap = PerfBoost.particleCap();
        if (cap <= 0) return; // disabled

        long now = System.currentTimeMillis();
        if (now - perfLastSampleMs >= 100L) {
            perfLastSampleMs = now;
            int count = 0;
            for (ParticleGroup<?> group : particles.values()) {
                count += group.size();
            }
            perfCachedCount = count;
        }
        if (perfCachedCount >= cap) {
            ci.cancel();
        }
    }
}
