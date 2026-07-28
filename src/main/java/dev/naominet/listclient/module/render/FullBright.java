package dev.naominet.listclient.module.render;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.eventBus.events.EventPreTick;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class FullBright extends Module {
    public FullBright() {
        super("FullBright", Category.Render);
    }

    @EventTarget
    public void onShits(EventPreTick e){
        if(mc.player != null)
            mc.player.getActiveEffects().add(new MobEffectInstance(MobEffects.NIGHT_VISION, 1));
    }


}
