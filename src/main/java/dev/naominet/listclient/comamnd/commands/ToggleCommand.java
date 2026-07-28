package dev.naominet.listclient.comamnd.commands;


import dev.naominet.listclient.comamnd.Command;
import dev.naominet.listclient.manager.FileManager;
import dev.naominet.listclient.manager.ModuleManager;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.ui.notification.NotificationManager;

public class ToggleCommand extends Command {
    public ToggleCommand() {
        super("toggle", new String[]{"t"}, "Toggle module.");
    }

    public void run(String[] args) {
        if (args.length == 1) {
            Module m = ModuleManager.instance.getModuleByName(args[0]);
            if (m == null) {
                NotificationManager.instance.error("Module \"" + args[0] + "\" not found.");
            } else {
                m.setEnable(!m.isEnable());
                if (m.isEnable()) {
                    NotificationManager.instance.success("Enabled " + m.getName() + ".");
                } else {
                    NotificationManager.instance.info("Disabled " + m.getName() + ".");
                }
            }
            FileManager.instance.save();
        } else if (args.length==0) {
            NotificationManager.instance.warning("Please enter the module.");
        } else {
            NotificationManager.instance.warning("Please enter the correct content.");
        }
    }
}
