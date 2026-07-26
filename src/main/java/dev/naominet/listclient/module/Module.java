package dev.naominet.listclient.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.naominet.listclient.eventBus.EventManager;
import dev.naominet.listclient.file.ConfigWriter;
import dev.naominet.listclient.module.render.Interface;
import dev.naominet.listclient.utils.MouseData;
import dev.naominet.listclient.value.Value;
import net.minecraft.client.Minecraft;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class Module implements ConfigWriter {
    private final String name;
    private final Category category;
    private int keyCode = InputConstants.UNKNOWN.getValue();
    private float animX, animY;
    private boolean enable = false;
    private String suffix;
    private boolean dragging = false;
    public ArrayList<Value<?>> valuesList = new ArrayList<>();
    private double x = 1, y = 1, width, height;
    protected Minecraft mc = Minecraft.getInstance();
    static Module instance;

    public Module(String name, Category category) {
        this.name = name;
        this.category = category;
        instance = this;
    }

    public static Module getInstance() {
        return instance;
    }

    public ArrayList<Value<?>> getValues() {
        return valuesList;
    }

    public void addValues(Value<?>... values) {
        valuesList.addAll(Arrays.asList(values));
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public void setKeyCode(int keyCode) {
        this.keyCode = keyCode;
    }

    public float getAnimX() {
        return animX;
    }

    public void setAnimX(float animX) {
        this.animX = animX;
    }

    public float getAnimY() {
        return animY;
    }

    public void setAnimY(float animY) {
        this.animY = animY;
    }

    public boolean isEnable() {
        return enable;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
        Interface.sortedModuleListNeedUpdata = true;
        if (enable) {
            if (mc.isGameLoadFinished())
                onEnable();
            EventManager.instance.register(this);
        } else {
            if (mc.isGameLoadFinished())
                onDisable();
            EventManager.instance.unregister(this);
        }
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public double getHeight() {
        return height;
    }

    public double getWidth() {
        return width;
    }

    public double getY() {
        return y;
    }

    public double getX() {
        return x;
    }

    float offsetX, offsetY;

    public void mouseClick(int mouseX, int mouseY, int button) {
        if (isHovered(getX(), getY(), getX() + getWidth(), getY() + getHeight(), mouseX, mouseY) && isEnable()) {
            if (button == 0) {
                offsetX = (float) (mouseX - getX());
                offsetY = (float) (mouseY - getY());
                this.dragging = true;
            }
        }
    }

    public void doDrag(int mouseX, int mouseY) {
        if (this.dragging) {
            if (MouseData.mouseAction == 0) {
                this.dragging = false;
            }
            this.x = mouseX - offsetX;
            this.y = mouseY - offsetY;
        }
    }

    protected void setXYWH(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public static boolean isHovered(float x, float y, float x2, float y2, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x2 && mouseY >= y && mouseY <= y2;
    }

    public static boolean isHovered(double x, double y, double x2, double y2, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x2 && mouseY >= y && mouseY <= y2;
    }

    @Override
    public void write(MessageBufferPacker packer) throws IOException {
        packer.packInt(keyCode);
        packer.packBoolean(enable);
        packer.packDouble(x);
        packer.packDouble(y);
        packer.packInt(valuesList.size());
        for (Value<?> value : valuesList) {
            value.write(packer);
        }
    }

    @Override
    public void read(MessageUnpacker unpacker) throws IOException {
        setKeyCode(unpacker.unpackInt());
        setEnable(unpacker.unpackBoolean());
        this.x = unpacker.unpackDouble();
        this.y = unpacker.unpackDouble();
        int valueSize = unpacker.unpackInt();
        for (int i = 0; i < valueSize; i++) {
            int type = unpacker.unpackInt();
            String valueName = unpacker.unpackString();
            Value<?> value = null;
            for (Value<?> v : valuesList) {
                if (v.getName().equals(valueName)) {
                    value = v;
                    break;
                }
            }
            if (value != null) {
                switch (type) {
                    case 0, 1, 2:
                        value.read(unpacker);
                        break;
                }
            } else {
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
