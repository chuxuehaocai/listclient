package dev.naominet.listclient.module.world;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPacket;
import dev.naominet.listclient.eventBus.events.EventPreTick;
import dev.naominet.listclient.eventBus.events.EventWorldChange;
import dev.naominet.listclient.extension.ConnectionExtension;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.ui.notification.NotificationManager;
import dev.naominet.listclient.ui.notification.NotificationType;
import dev.naominet.listclient.utils.PacketSnapshot;
import dev.naominet.listclient.value.Mode;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class Disabler extends Module {
    public Mode mode = new Mode("Mode", new String[]{"CubeCraft Ping Spoof"}, "CubeCraft Ping Spoof");
    public CopyOnWriteArrayList<PacketSnapshot> cachedPacketList = new CopyOnWriteArrayList<>();

    public Disabler() {
        super("Disabler", Category.World);
        addValues(mode);
    }

    public void onEnable(){
        cachedPacketList.clear();
        if(mc.hasSingleplayerServer()){
            NotificationManager.instance.show(NotificationType.ERROR, "Disabler can't be enabled in singleplayer.", 1000);
            setEnable(false);
        }
    }

    @EventTarget
    public void onTick(EventPreTick e){
        setSuffix(mode.getValue());

        if(mode.isCurrentMode("CubeCraft Ping Spoof")){
            if(mc.player != null) {
                long currentTime = System.currentTimeMillis();
                long delay = 0L;
                if (mc.player.tickCount < 150) delay = 5000L;
                if (mc.player.tickCount < 300 && mc.player.tickCount > 150) delay = 10000L;
                if (mc.player.tickCount > 300) delay = 20000L;

                for (PacketSnapshot packetSnapshot : cachedPacketList) {
                    if(currentTime - packetSnapshot.getSnapTime() >= delay){
                        ((ConnectionExtension) Objects.requireNonNull(mc.getConnection())).sendPacketNoEvent(packetSnapshot.getPacket());
                    }
                }
            }
        }
    }

    @EventTarget
    public void onPacket(EventPacket e){
        if(mode.isCurrentMode("CubeCraft Ping Spoof")){
            if(
                    e.getPacket() instanceof ClientboundKeepAlivePacket ||
                    e.getPacket() instanceof ClientboundPingPacket ||
                    e.getPacket() instanceof ClientboundPlayerPositionPacket
            ){
                cachedPacketList.add(new PacketSnapshot(e.getPacket(), System.currentTimeMillis()));
                e.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onWorldChange(EventWorldChange e){
        cachedPacketList.clear();
    }
}