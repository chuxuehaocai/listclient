package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.manager.ModuleManager;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.module.world.Timer;
import net.minecraft.client.DeltaTracker;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DeltaTracker.Timer.class)
public class MixinDeltaTrackerTimer {
    @Shadow
    private float deltaTicks;

    @Inject(method = "advanceGameTime(J)I",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/DeltaTracker$Timer;lastMs:J",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0))
    public void onBeginRenderTick(long currentMs, CallbackInfoReturnable<Integer> cir)
    {
        if(ModuleManager.instance.getModuleByClazz(Timer.class).isEnable())
            deltaTicks *= Timer.timerSpeed.getValue();
        else
            deltaTicks *= 1f;
    }
}
