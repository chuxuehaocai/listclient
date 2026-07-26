package dev.naominet.listclient.manager;

import org.apache.commons.io.FileUtils;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

import java.io.File;
import java.io.IOException;

public class FileManager {

    private final File clientDir = new File("List");
    public final File scriptDir = new File("List/script");
    public static final FileManager instance = new FileManager();


    public FileManager(){
        if(!clientDir.exists()){
            clientDir.mkdir();
        }
        if(!scriptDir.exists()){
            scriptDir.mkdir();
        }
    }

    public void read(){

        File config = new File(clientDir,"config.dll");

        if(config.exists()){
            try {
                MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(FileUtils.readFileToByteArray(config));
                unpacker.unpackString();
                ModuleManager.instance.read(unpacker);
                unpacker.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void save(){
        File config = new File(clientDir,"config.dll");
        try {
            FileUtils.writeByteArrayToFile(config,getConfig());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getConfig(){
        MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
        try {
            // header
            packer.packString("MZ");
            ModuleManager.instance.write(packer);
            packer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return packer.toByteArray();
    }
}
