package dev.naominet.listclient.module.world;

import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.value.Numbers;

public class Timer extends Module {
    public static Numbers timerSpeed = new Numbers("Speed", 1d, 0d, 10d, 0.1d);
    public Timer() {
        super("Timer", Category.World);
        addValues(timerSpeed);
    }
}
