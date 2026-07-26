package dev.naominet.listclient.utils;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class KeyMap {
    //lwjgl3你贏了。
    private static final Map<String, Integer> keyMap = new HashMap<>();

    static {
        keyMap.put("A", GLFW.GLFW_KEY_A);
        keyMap.put("B", GLFW.GLFW_KEY_B);
        keyMap.put("C", GLFW.GLFW_KEY_C);
        keyMap.put("D", GLFW.GLFW_KEY_D);
        keyMap.put("E", GLFW.GLFW_KEY_E);
        keyMap.put("F", GLFW.GLFW_KEY_F);
        keyMap.put("G", GLFW.GLFW_KEY_G);
        keyMap.put("H", GLFW.GLFW_KEY_H);
        keyMap.put("I", GLFW.GLFW_KEY_I);
        keyMap.put("J", GLFW.GLFW_KEY_J);
        keyMap.put("K", GLFW.GLFW_KEY_K);
        keyMap.put("L", GLFW.GLFW_KEY_L);
        keyMap.put("M", GLFW.GLFW_KEY_M);
        keyMap.put("N", GLFW.GLFW_KEY_N);
        keyMap.put("O", GLFW.GLFW_KEY_O);
        keyMap.put("P", GLFW.GLFW_KEY_P);
        keyMap.put("Q", GLFW.GLFW_KEY_Q);
        keyMap.put("R", GLFW.GLFW_KEY_R);
        keyMap.put("S", GLFW.GLFW_KEY_S);
        keyMap.put("T", GLFW.GLFW_KEY_T);
        keyMap.put("U", GLFW.GLFW_KEY_U);
        keyMap.put("V", GLFW.GLFW_KEY_V);
        keyMap.put("W", GLFW.GLFW_KEY_W);
        keyMap.put("X", GLFW.GLFW_KEY_X);
        keyMap.put("Y", GLFW.GLFW_KEY_Y);
        keyMap.put("Z", GLFW.GLFW_KEY_Z);
        keyMap.put("SPACE", GLFW.GLFW_KEY_SPACE);
        keyMap.put("LEFT_SHIFT", GLFW.GLFW_KEY_LEFT_SHIFT);
        keyMap.put("RIGHT_SHIFT", GLFW.GLFW_KEY_RIGHT_SHIFT);
        keyMap.put("ESCAPE", GLFW.GLFW_KEY_ESCAPE);
        keyMap.put("ENTER", GLFW.GLFW_KEY_ENTER);
        keyMap.put("TAB", GLFW.GLFW_KEY_TAB);
        keyMap.put("BACKSPACE", GLFW.GLFW_KEY_BACKSPACE);
    }

    public static int getKeyCode(String name) {
        return keyMap.getOrDefault(name.toUpperCase(), -1);
    }


}
