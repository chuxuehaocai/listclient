package dev.naominet.listclient.module.movement;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.manager.ModuleManager;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.module.world.Scaffold;
import dev.naominet.listclient.utils.MoveUtils;

public class Sprint extends Module {
    public Sprint() {
        super("Sprint", Category.Movement);
    }

    @EventTarget
    public void onPlayerMotionUpdate(EventPlayerMotionPreUpdate event) {
        // Yield to Scaffold Down — the sprint key acts as descent modifier
        Scaffold scaffold = ModuleManager.instance.getModuleByClazz(Scaffold.class);
        if (scaffold != null && scaffold.isEnable() && scaffold.isDownEnabled()) {
            return;
        }
        if (MoveUtils.isMoving())
            mc.options.keySprint.setDown(true);
    }
}

