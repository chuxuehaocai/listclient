package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.manager.ModuleManager;
import dev.naominet.listclient.module.render.FullBright;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public class MixinLightmapRenderStateExtractor {
    @Inject(method = "extract", at = @At("RETURN"))
    private void applyFullBright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        FullBright fullBright = ModuleManager.instance.getModuleByClazz(FullBright.class);
        if (fullBright != null && fullBright.isEnable()) {
            state.nightVisionEffectIntensity = 1.0F;
        }
    }
}
