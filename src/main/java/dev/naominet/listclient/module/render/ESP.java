package dev.naominet.listclient.module.render;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPreTick;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class ESP extends Module {
    public ESP() {
        super("ESP", Category.Render);
        setSuffix("Glowing Effect");
    }

    @EventTarget
    public void onTick(EventPreTick e){
        if(mc.isGameLoadFinished() && mc.level != null) {
            for (AbstractClientPlayer entity : mc.level.players()){
                if(entity == mc.player) return;

                if(entity.getActiveEffects().contains(new MobEffectInstance(MobEffects.INVISIBILITY))) return;//不显示隐身.

                entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1337));
            }
        }
    }
}
