package dev.naominet.listclient.utils;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

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




    public static double getAnimationStateSmooth(double target, double current, double speed) {
        boolean larger = target > current;
        if (speed < 0.0) {
            speed = 0.0;
        } else if (speed > 1.0) {
            speed = 1.0;
        }
        if (target == current) {
            return target;
        }
        double dif = Math.max(target, current) - Math.min(target, current);
        double factor = dif * speed;
        if (factor < 0.1) {
            factor = 0.1;
        }
        if (larger) {
            if (current + factor > target) {
                current = target;
            } else {
                current += factor;
            }
        } else {
            if (current - factor < target) {
                current = target;
            } else {
                current -= factor;
            }
        }
        return current;
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

    public static void drawGradientSideways(GuiGraphicsExtractor e, double left, double top,
                                            double right, double bottom, int col1, int col2) {
        if (right <= left || bottom <= top) return;

        // One quad with per-vertex colors; the GUI pipeline interpolates RGB + alpha
        // across the fragment, which is exactly what the legacy GL shade-model quad did.
        // Winding is shoelace-negative (vanilla fill order) because the GUI pipeline
        // backface-culls the other orientation.
        float l = (float) left, t = (float) top, r = (float) right, b = (float) bottom;
        float[] xy = {
                l, t, l, b, r, b, r, t
        };
        int[] col = {
                col1, col1, col2, col2
        };

        ScreenRectangle scissor = e.scissorStack.peek();
        Matrix3x2f pose = new Matrix3x2f(e.pose());
        ScreenRectangle bounds = new ScreenRectangle(
                (int) Math.floor(l), (int) Math.floor(t),
                (int) Math.ceil(r - l), (int) Math.ceil(b - t))
                .transformMaxBounds(pose);
        if (scissor != null) {
            bounds = scissor.intersection(bounds);
            if (bounds == null) return; // fully clipped
        }
        e.guiRenderState.addGuiElement(new GradientQuad(
                RenderPipelines.GUI, TextureSetup.noTexture(), pose, xy, col, scissor, bounds));
    }

    /**
     * Position-color quad for {@link #drawGradientSideways}: same batchable
     * {@link GuiElementRenderState} shape as the {@code GlShapes} meshes, so it
     * honors the current pose and scissor and z-orders with vanilla fills.
     */
    private record GradientQuad(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose,
                                float[] xy, int[] color, ScreenRectangle scissorArea, ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer vc) {
            for (int i = 0; i < color.length; i++) {
                vc.addVertexWith2DPose(pose, xy[i * 2], xy[i * 2 + 1]).setColor(color[i]);
            }
        }
    }
}
