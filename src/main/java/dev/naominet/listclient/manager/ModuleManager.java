package dev.naominet.listclient.manager;

import dev.naominet.listclient.file.ConfigWriter;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.module.misc.NoCommands;
import dev.naominet.listclient.module.movement.Fly;
import dev.naominet.listclient.module.movement.Speed;
import dev.naominet.listclient.module.movement.Sprint;
import dev.naominet.listclient.module.render.ClickGui;
import dev.naominet.listclient.module.render.Interface;
import dev.naominet.listclient.module.render.InventoryDisplay;
import dev.naominet.listclient.module.render.LyricDisplay;
import dev.naominet.listclient.module.render.MusicPlayer;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class ModuleManager implements ConfigWriter {
    public static ModuleManager instance = new ModuleManager();
    private final ArrayList<Module> modules = new ArrayList<>();
    private boolean initialized = false;

    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        // Register modules
        modules.add(new Sprint());
        modules.add(new Interface());
        modules.add(new NoCommands());
        modules.add(new Speed());
        modules.add(new Fly());
        modules.add(new InventoryDisplay());
        modules.add(new MusicPlayer());
        modules.add(new LyricDisplay());
        modules.add(new ClickGui());
    }

    public ArrayList<Module> getModules() {
        return modules;
    }

    public ArrayList<Module> getEnabledModuleList(){
        return (ArrayList<Module>) modules.stream().filter(Module::isEnable).collect(Collectors.toList());
    }

    public <T extends Module> T getModuleByClazz(Class<T> moduleClass) {
        return modules.stream()
                .filter(moduleClass::isInstance)
                .map(moduleClass::cast)
                .findFirst()
                .orElse(null);
    }

    public <T extends Module> T getModuleByName(String moduleName) {
        return modules.stream()
                .filter(module -> module.getName().equalsIgnoreCase(moduleName))
                .map(module -> (T) module)
                .findFirst()
                .orElse(null);
    }


    @Override
    public void write(MessageBufferPacker packer) throws IOException {
        packer.packInt(modules.size());
        for (Module module : modules) {
            packer.packString(module.getName());
            module.write(packer);
        }
    }

    @Override
    public void read(MessageUnpacker unpacker) throws IOException {
        int moduleCount = unpacker.unpackInt();
        for (int i = 0; i < moduleCount; i++) {
            String moduleName = unpacker.unpackString();
            Module module = getModuleByName(moduleName);
            if (module != null) {
                module.read(unpacker);
            } else {
                unpacker.unpackInt();
                unpacker.unpackBoolean();
                unpacker.unpackDouble();
                unpacker.unpackDouble();
                int valueSize = unpacker.unpackInt();
                for (int j = 0; j < valueSize; j++) {
                    int type = unpacker.unpackInt();
                    unpacker.unpackString();
                    switch (type) {
                        case 0:
                            unpacker.unpackBoolean();
                            break;
                        case 1:
                            unpacker.unpackDouble();
                            break;
                        case 2:
                            unpacker.unpackString();
                            break;
                    }
                }
            }
        }
    }
}
