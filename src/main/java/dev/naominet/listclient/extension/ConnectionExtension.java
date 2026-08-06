package dev.naominet.listclient.extension;

import net.minecraft.network.protocol.Packet;

public interface ConnectionExtension {
    void sendPacketNoEvent(Packet<?> packet);
}
