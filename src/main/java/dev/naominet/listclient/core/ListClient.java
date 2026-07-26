package dev.naominet.listclient.core;

import dev.naominet.listclient.comamnd.CommandManager;
import dev.naominet.listclient.eventBus.EventManager;
import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventKey;
import dev.naominet.listclient.manager.FileManager;
import dev.naominet.listclient.manager.ModuleManager;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.ncmApi.NCMAPI;
import dev.naominet.listclient.utils.WavPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;

public class ListClient {
    public static ListClient instance = new ListClient();
    public NCMAPI ncmapi = new NCMAPI("https://music.naominet.dev");

    //Invoke when minecraft start
    public void start(){
        ModuleManager.instance.initialize();
        CommandManager.instance.initialize();
        EventManager.instance.register(this);

        WavPlayer.playWav("startup.wav", false);
    }

    //Invoke when minecraft stop
    public void stop(){
        FileManager.instance.save();
    }


    @EventTarget
    public void onKey(EventKey e){
        if (e.getKeyCode() == -1) {
            return;
        }
        for (Module m : ModuleManager.instance.getModules())
            if(e.getKeyCode() == m.getKeyCode() && !(Minecraft.getInstance().gui.screen() instanceof ChatScreen))
                m.setEnable(!m.isEnable());
    }

}
