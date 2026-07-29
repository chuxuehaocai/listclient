package dev.naominet.listclient.module.render;

import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;

public class ESP extends Module {
    public ESP() {
        super("ESP", Category.Render);
        setSuffix("Player Outline");
    }

    public boolean shouldOutline(Entity entity) {
        return mc.player != null
                && entity instanceof AbstractClientPlayer player
                && player != mc.player
                && !player.hasEffect(MobEffects.INVISIBILITY);
    }
}
