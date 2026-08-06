package dev.naominet.listclient.utils;

import net.minecraft.network.protocol.Packet;

public class PacketSnapshot {
    private Packet<?> packet;
    private Long snapTime;
    public PacketSnapshot(Packet<?> packet, Long snapTime) {
        this.packet = packet;
        this.snapTime = snapTime;
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public Long getSnapTime() {
        return snapTime;
    }
}
