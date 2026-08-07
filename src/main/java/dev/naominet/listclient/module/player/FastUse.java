package dev.naominet.listclient.module.player;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class FastUse extends Module {
    public FastUse() {
        super("FastUse", Category.Player);
        setSuffix("C03");
    }

    @EventTarget
    public void onUpdate(EventPlayerMotionPreUpdate e){
        //by send 10 c03
        if(mc.player.getUseItem().has(DataComponents.CONSUMABLE)){
            if(mc.player.isUsingItem()){
                for (int i = 0; i < 9; i++){
                    mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(mc.player.onGround(), mc.player.horizontalCollision));
                }
            }
        }
    }
}
