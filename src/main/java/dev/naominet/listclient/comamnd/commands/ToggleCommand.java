package dev.naominet.listclient.comamnd.commands;


import dev.naominet.listclient.comamnd.Command;
import dev.naominet.listclient.manager.FileManager;
import dev.naominet.listclient.manager.ModuleManager;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.utils.ClientUtils;

public class ToggleCommand extends Command {
    public ToggleCommand() {
        super("toggle", new String[]{"t"}, "Toggle module.");
    }

    public void run(String[] args) {
        if (args.length == 1) {
            Module m = ModuleManager.instance.getModuleByName(args[0]);
            if (m == null) {
                ClientUtils.sendMessage("\u00A77Module \"\u00a7c" + args[0] + "\u00a77\" not found.");
            } else {
                m.setEnable(!m.isEnable());
                ClientUtils.sendMessage("\u00A77Module \"\u00A7f" + m.getName() + "\u00A77\" " + (m.isEnable() ? "enabled" : "disabled") + ".");
            }
            FileManager.instance.save();
        } else if (args.length==0) {
            ClientUtils.sendMessage("\u00A77Please enter the module.");
        } else {
            ClientUtils.sendMessage("\u00A77Please enter the correct content.");
        }
    }
}
