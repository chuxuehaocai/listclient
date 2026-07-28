package dev.naominet.listclient.module.player;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;

import java.util.Random;

public class Derp extends Module {
    public Derp(){
        super("Derp", Category.Player);
    }

    @EventTarget
    public void onEvent(EventPlayerMotionPreUpdate e){
        Random r = new Random();
        e.setYaw(r.nextInt(360));
        e.setPitch(r.nextInt(360));
    }
}
