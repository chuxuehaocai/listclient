package dev.naominet.listclient.value;



import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.util.function.Supplier;

public class Option extends Value<Boolean> {

    public Option(String name, Boolean enabled) {
        super(name, enabled, () -> true);
    }

    public Option(String name, Boolean enabled, Supplier<Boolean> visitable) {
        super(name, enabled, visitable);
    }

    @Override
    public Boolean getValue() {
        return this.isVisitable() && super.getValue();
    }

    @Override
    public void write(MessageBufferPacker packer) throws IOException {
        packer.packInt(0); // BOOL
        packer.packString(getName());
        packer.packBoolean(getValue());
    }

    @Override
    public void read(MessageUnpacker unpacker) throws IOException {
        setValue(unpacker.unpackBoolean());
    }
}
