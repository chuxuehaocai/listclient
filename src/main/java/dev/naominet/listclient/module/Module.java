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
    /** Content size in GUI pixels. */
    private double width, height;
    /**
     * HUD anchor as a fraction of the current GUI scaled size (0..1 typical).
     * Source of truth for persistence so window resizes keep the same relative spot.
     */
    private double relX, relY;
    /** Working absolute GUI pixels, re-derived from {@link #relX}/{@link #relY} on resize. */
    private double absX = 1, absY = 1;
    private boolean hasRel;
    /** Screen size {@link #absX}/{@link #absY} were last derived against. */
    private int posScreenW, posScreenH;
    /**
     * Set by {@link dev.naominet.listclient.manager.FileManager} while loading:
     * {@code true} for the relative-position format ("MZr"), {@code false} for
     * legacy absolute-pixel configs ("MZ").
     */
    public static boolean positionFormatRelative = true;
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
        if (!java.util.Objects.equals(this.suffix, suffix)) {
            this.suffix = suffix;
            Interface.sortedModuleListNeedUpdata = true;
        }
    }

    public double getHeight() {
        return height;
    }

    public double getWidth() {
        return width;
    }

    public double getY() {
        ensureRelative();
        syncAbsFromRel();
        return absY;
    }

    public double getX() {
        ensureRelative();
        syncAbsFromRel();
        return absX;
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
            setPosition(mouseX - offsetX, mouseY - offsetY);
        }
    }

    protected void setXYWH(double x, double y, double width, double height) {
        // Only rewrite the relative anchor when the absolute position actually
        // moves. HUD modules commonly call setXYWH((int) getX(), …) every frame
        // to refresh size; treating that as a move would quantize relX/relY
        // toward zero over time.
        if (Math.abs(x - getX()) > 0.01 || Math.abs(y - getY()) > 0.01) {
            setPosition(x, y);
        }
        this.width = width;
        this.height = height;
    }

    /** Store an absolute GUI-pixel position as a fraction of the current screen. */
    protected void setPosition(double x, double y) {
        this.absX = x;
        this.absY = y;
        int sw = screenW();
        int sh = screenH();
        this.posScreenW = sw;
        this.posScreenH = sh;
        if (sw > 0 && sh > 0) {
            this.relX = x / sw;
            this.relY = y / sh;
            this.hasRel = true;
        }
    }

    /**
     * Convert a pure-absolute position (constructor default or legacy config
     * loaded before the window existed) into a relative fraction the first
     * time a real screen size is available.
     */
    private void ensureRelative() {
        if (hasRel) {
            return;
        }
        int sw = screenW();
        int sh = screenH();
        if (sw <= 0 || sh <= 0) {
            return;
        }
        relX = absX / sw;
        relY = absY / sh;
        posScreenW = sw;
        posScreenH = sh;
        hasRel = true;
    }

    /**
     * Recompute absolute pixels from the relative fraction when the GUI scale
     * size has changed (window resize / GUI scale change).
     */
    private void syncAbsFromRel() {
        if (!hasRel) {
            return;
        }
        int sw = screenW();
        int sh = screenH();
        if (sw <= 0 || sh <= 0) {
            return;
        }
        if (sw == posScreenW && sh == posScreenH) {
            return;
        }
        absX = relX * sw;
        absY = relY * sh;
        posScreenW = sw;
        posScreenH = sh;
    }

    private int screenW() {
        try {
            return mc.getWindow().getGuiScaledWidth();
        } catch (Exception e) {
            return 0;
        }
    }

    private int screenH() {
        try {
            return mc.getWindow().getGuiScaledHeight();
        } catch (Exception e) {
            return 0;
        }
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
        // Always write relative fractions. If we only ever held absolute coords
        // (e.g. never rendered before first save), convert now.
        if (!hasRel) {
            setPosition(absX, absY);
        }
        packer.packDouble(relX);
        packer.packDouble(relY);
        packer.packInt(valuesList.size());
        for (Value<?> value : valuesList) {
            value.write(packer);
        }
    }

    @Override
    public void read(MessageUnpacker unpacker) throws IOException {
        setKeyCode(unpacker.unpackInt());
        setEnable(unpacker.unpackBoolean());
        double px = unpacker.unpackDouble();
        double py = unpacker.unpackDouble();
        if (positionFormatRelative) {
            this.relX = px;
            this.relY = py;
            this.hasRel = true;
            // Force re-derive on next getX/getY once the window reports a size.
            this.posScreenW = 0;
            this.posScreenH = 0;
            this.absX = px;
            this.absY = py;
        } else {
            // Legacy absolute-pixel config: convert to relative against the
            // current screen (or keep absolute until the window is ready).
            this.hasRel = false;
            setPosition(px, py);
        }
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
