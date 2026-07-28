package dev.naominet.listclient.comamnd;

import dev.naominet.listclient.comamnd.commands.BindCommand;
import dev.naominet.listclient.comamnd.commands.ToggleCommand;
import dev.naominet.listclient.core.ListClient;
import dev.naominet.listclient.eventBus.EventManager;
import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPacket;
import dev.naominet.listclient.manager.ModuleManager;
import dev.naominet.listclient.module.misc.NoCommands;
import dev.naominet.listclient.ui.notification.NotificationManager;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;

import java.util.ArrayList;
import java.util.Arrays;

public class CommandManager {
    private ArrayList<Command> commands;
    private String prefix;
    public static final CommandManager instance = new CommandManager();

    public String getPrefix() {
        return prefix;
    }

    public void initialize(){
        this.commands = new ArrayList<>();
        this.prefix = ".";
        //add commands
        this.commands.add(new ToggleCommand());
        this.commands.add(new BindCommand());

        EventManager.instance.register(this);

    }

    public void processCommand(String s) {
        if (!s.startsWith(prefix)) return;

        String[] args = s.substring(prefix.length()).split(" ");
        String commandName = args[0];
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);

        commands.stream()
                .filter(cmd -> cmd.getName().equalsIgnoreCase(commandName)
                               || Arrays.stream(cmd.getOtherNames()).anyMatch(name -> name.equalsIgnoreCase(commandName)))
                .findFirst()
                .map(cmd -> {
                    cmd.run(commandArgs);
                    return true;
                })
                .orElseGet(() -> {
                    NotificationManager.instance.error("Command \"" + s + "\" not found.");
                    return false;
                });
    }

    @EventTarget
    public void onPacket(EventPacket e){
        Packet<?> packet = e.getPacket();
        if (packet instanceof ClientboundPlayerChatPacket) {
            if (ModuleManager.instance.getModuleByClazz(NoCommands.class).isEnable()) {
                return;
            }
            ClientboundPlayerChatPacket messagePacket = (ClientboundPlayerChatPacket) packet;
            if(messagePacket.body().content().startsWith(this.getPrefix())){
                e.setCancelled(true);
                this.processCommand(messagePacket.body().content());
            }
        }
    }

    public ArrayList<Command> getCommands() {
        return commands;
    }
}
