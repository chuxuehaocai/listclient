package dev.naominet.listclient.file;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;

public interface ConfigWriter {
    void write(MessageBufferPacker packer) throws IOException;
    void read(MessageUnpacker unpacker) throws IOException;
}
