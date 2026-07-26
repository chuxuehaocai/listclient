package dev.naominet.listclient.eventBus.events;

import dev.naominet.listclient.eventBus.Event;
import net.minecraft.network.protocol.Packet;

public class EventPacket extends Event {
    private final Packet<?> packet;

    public EventPacket(Packet<?> message) {
        this.packet = message;
    }

    public Packet<?> getPacket() {
        return packet;
    }
}
