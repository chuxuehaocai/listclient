package dev.naominet.listclient.value;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.util.function.Supplier;

public class Numbers extends Value<Double> {
    public Double min;
    public Double max;
    public Double inc;
    public boolean dragged;
    private boolean isInteger;

    public Numbers(String name, Number value, Number min, Number max, Number inc) {
        super(name, value.doubleValue(), () -> true);
        this.handleInteger(inc.doubleValue());
        this.min = min.doubleValue();
        this.max = max.doubleValue();
        this.inc = inc.doubleValue();
    }

    public Numbers(String name, Number value, Number min, Number max, Number inc, Supplier<Boolean> visitable) {
        super(name, value.doubleValue(), visitable);
        this.handleInteger(inc.doubleValue());
        this.min = min.doubleValue();
        this.max = max.doubleValue();
        this.inc = inc.doubleValue();
    }

    private void handleInteger(Double inc) {
        if (inc % 1 == 0.0) this.isInteger = true;
    }

    @Override
    public void setValue(Double value) {
        if (value > max) value = max;
        if (value < min) value = min;
        super.setValue(value);
    }

    public int intValue() {
        return this.getValue().intValue();
    }

    public float floatValue() {
        return this.getValue().floatValue();
    }

    public Double getMinimum() {
        return this.min;
    }

    public Double getMaximum() {
        return this.max;
    }

    public void setIncrement(Double inc) {
        this.inc = inc;
    }

    public Double getIncrement() {
        return this.inc;
    }

    public boolean isInteger() {
        return isInteger;
    }

    @Override
    public void write(MessageBufferPacker packer) throws IOException {
        packer.packInt(1); // NUM
        packer.packString(getName());
        packer.packDouble(getValue());
    }

    @Override
    public void read(MessageUnpacker unpacker) throws IOException {
        setValue(unpacker.unpackDouble());
    }
}

