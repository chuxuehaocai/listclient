package dev.naominet.listclient.comamnd.commands;

import dev.naominet.listclient.comamnd.Command;
import dev.naominet.listclient.manager.ModuleManager;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.utils.ClientUtils;
import dev.naominet.listclient.utils.KeyMap;

public class BindCommand extends Command {

    public BindCommand() {
        super("bind", "Bind a module to a key.");
    }

    @Override
    public void run(String[] args) {
        if (args.length == 2) {
            Module m = ModuleManager.instance.getModuleByName(args[0]);

            if (m == null) {
                ClientUtils.sendMessage("\u00A77Module \"\u00a7c" + args[0] + "\u00a77\" not found.");
            } else {
                int targetKey = KeyMap.getKeyCode(args[1].toUpperCase());
                m.setKeyCode(targetKey);
                ClientUtils.sendMessage("\u00A77Finished to bind key \"\u00a7f" + args[1].toUpperCase() + "\u00a77\" to module \"\u00a7f" + m.getName() + "\u00a77\".");
            }
        } else {
            ClientUtils.sendMessage("\u00A77Please enter the correct content.");
        }
    }
}
