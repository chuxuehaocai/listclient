package dev.naominet.listclient.eventBus.events;

import dev.naominet.listclient.eventBus.Event;
import net.minecraft.network.protocol.Packet;

public class EventPacket extends Event {
    private final Packet<?> packet;
    private final boolean incoming;

    /** Backwards-compatible outgoing event constructor. */
    public EventPacket(Packet<?> message) {
        this(message, false);
    }

    public EventPacket(Packet<?> message, boolean incoming) {
        this.packet = message;
        this.incoming = incoming;
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public boolean isIncoming() {
        return incoming;
    }

    public boolean isOutgoing() {
        return !incoming;
    }
}
