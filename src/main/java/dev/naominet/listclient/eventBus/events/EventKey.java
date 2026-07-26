package dev.naominet.listclient.eventBus.events;

import dev.naominet.listclient.eventBus.Event;

public class EventKey extends Event {
    private int keyCode;
    public EventKey(int keyCode) {
        this.keyCode = keyCode;
    }

    public int getKeyCode() {
        return keyCode;
    }
}
