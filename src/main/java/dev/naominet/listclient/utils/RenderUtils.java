package dev.naominet.listclient.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.awt.*;

public class RenderUtils {
    public static void drawSomeShitText(GuiGraphicsExtractor drawContext, String theShits, int x, int y) {
        char[] shitChar = theShits.toCharArray();
        int widthOffset = 0;
        for (int i = 0; i < shitChar.length; i++) {
            //講狗屎字符對象轉換為一個字符串
            //傻逼mojang
            String str = String.valueOf(shitChar[i]);
            if (str.equals("[") || str.equals("]"))
                drawContext.text(Minecraft.getInstance().font, str, x + widthOffset, y, new Color(113, 113, 113).getRGB());
            else
                drawContext.text(Minecraft.getInstance().font, str, x + widthOffset, y, -1);
            //最後記得加上offset - k
            widthOffset += Minecraft.getInstance().font.width(str);
            //不知道這步有沒有必要但是還是加一下手動釋放點內存
            str = null;
        }
    }


    public static void drawTexture(GuiGraphicsExtractor dc, String texturePath, String textureId, int x, int y, int width, int height) {
        Identifier identifier = DynamicImageUtils.registerAsDynamicImageFromClientResources(texturePath, textureId);
        dc.blit(RenderPipelines.GUI_TEXTURED, identifier, x, y, 0, 0, width, height, width, height);
    }

    public static void drawTexture(GuiGraphicsExtractor dc, Identifier identifier, int x, int y, int width, int height) {
        dc.blit(RenderPipelines.GUI_TEXTURED, identifier, x, y, 0, 0, width, height, width, height);
    }

    /**
     * Draw a (pre-masked circular) texture. Prefer generating the circle mask
     * once via {@link DynamicImageUtils#registerCircularDynamicImage}.
     */
    public static void drawCircularTexture(GuiGraphicsExtractor dc, Identifier identifier,
                                           int x, int y, int size) {
        if (identifier == null) return;
        dc.blit(RenderPipelines.GUI_TEXTURED, identifier, x, y, 0, 0, size, size, size, size);
    }

    private static Minecraft mc = Minecraft.getInstance();
    private static Identifier shitty = null;

    public static void drawShadow(GuiGraphicsExtractor dc, float x, float y, float width, float height) {
        drawTexturedRect(dc, x - 9, y - 9, 9, 9, "paneltopleft");
        drawTexturedRect(dc, x - 9, y + height, 9, 9, "panelbottomleft");
        drawTexturedRect(dc, x + width, y + height, 9, 9, "panelbottomright");
        drawTexturedRect(dc, x + width, y - 9, 9, 9, "paneltopright");
        drawTexturedRect(dc, x - 9, y, 9, height, "panelleft");
        drawTexturedRect(dc, x + width, y, 9, height, "panelright");
        drawTexturedRect(dc, x, y - 9, width, 9, "paneltop");
        drawTexturedRect(dc, x, y + height, width, 9, "panelbottom");
    }

    public static void drawTexturedRect(GuiGraphicsExtractor dc, float x, float y, float width, float height, String image) {
        shitty = DynamicImageUtils.registerAsDynamicImageFromClientResources("shadow/" + image + ".png", image);
        drawModalRectWithCustomSizedTexture(dc, x, y, 0, 0, width, height, width, height);
    }

    public static void drawModalRectWithCustomSizedTexture(GuiGraphicsExtractor dc, float x, float y, float u, float v, float width, float height, float textureWidth, float textureHeight) {
        dc.blit(RenderPipelines.GUI_TEXTURED, shitty, (int) x, (int) y, u, v, (int) width, (int) height, (int) textureWidth, (int) textureHeight);
    }

    public static boolean isHovered(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX > x && mouseX < x + width && mouseY > y && mouseY < y + height;
    }
}
