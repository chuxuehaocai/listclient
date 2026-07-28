package dev.naominet.listclient.comamnd.commands;

import dev.naominet.listclient.comamnd.Command;
import dev.naominet.listclient.manager.ModuleManager;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.ui.notification.NotificationManager;
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
                NotificationManager.instance.error("Module \"" + args[0] + "\" not found.");
            } else {
                int targetKey = KeyMap.getKeyCode(args[1].toUpperCase());
                m.setKeyCode(targetKey);
                NotificationManager.instance.success("Bound " + args[1].toUpperCase() + " to " + m.getName() + ".");
            }
        } else {
            NotificationManager.instance.warning("Please enter the correct content.");
        }
    }
}
