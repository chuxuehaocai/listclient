package dev.naominet.listclient.module.render;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventRender2D;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.module.combat.KillAura;
import dev.naominet.listclient.ui.theme.M3;
import dev.naominet.listclient.utils.AnimationUtils;
import dev.naominet.listclient.utils.Colors;
import dev.naominet.listclient.utils.RenderUtils;
import dev.naominet.listclient.utils.font.TTFFontRenderer;
import dev.naominet.listclient.value.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.awt.*;

public class TargetHUD extends Module {
    public Mode mode = new Mode("Mode", new String[]{"Hanabi", "Classic"}, "Classic");
    int defaultWidth = 125;
    double alphaSet = 1;
    private double healthBarWidth;
    private double healthBarWidth2;
    private double hudHeight;
    public TargetHUD() {
        super("TargetHUD", Category.Render);
        setXYWH(8, 108, 125, 40);
        addValues(mode);
    }

    boolean nulltarget = false;

    @EventTarget
    public void onRender2D(EventRender2D e){
        if(mode.isCurrentMode("Classic")) {
            if (KillAura.target == null && !(mc.gui.screen() instanceof ChatScreen)) {
                if (alphaSet > 0)
                    alphaSet -= 0.1f;
            }

            LivingEntity entity;
            if (mc.gui.screen() instanceof ChatScreen)
                entity = mc.player;
            else
                entity = KillAura.target;

            GuiGraphicsExtractor dc = e.getExtractor();
            setXYWH(getX(), getY(), defaultWidth, 40);

            // Clamp alpha between 0 and 1
            alphaSet = Math.clamp(alphaSet, 0, 1);

            Color backgroundColor = new Color(0, 0, 0, (int) (152 * alphaSet));
            Color textColor = new Color(255, 255, 255, (int) (200 * alphaSet));

            dc.fill((int) getX(), (int) getY(), (int) (getX() + getWidth()), (int) (getY() + getHeight()), backgroundColor.getRGB());

            if (entity != null) {
                RenderUtils.drawShadow(dc, (float) getX(), (float) getY(), (float) getWidth(), (float) getHeight());

                if (alphaSet < 1.0) alphaSet += 0.05;
                float healthRatio = entity.getHealth() / entity.getMaxHealth();
                int filledWidth = (int) (getWidth() * healthRatio);
                int barY = (int) (getY() + 38);
                dc.fill((int) getX(), barY, (int) (getX() + filledWidth), barY + 2, M3.PRIMARY);

                if (entity instanceof AbstractClientPlayer player) {
                    Identifier skin = player.getSkin().body().texturePath();
                    dc.blit(
                            RenderPipelines.GUI_TEXTURED,
                            skin,
                            (int) getX() + 3,
                            (int) getY() + 3,
                            8,
                            8,
                            32, 32,
                            8, 8,
                            64, 64
                    );
                    RenderUtils.drawShadow(dc, (int) getX() + 3, (int) getY() + 4, 32, 32);
                }

                // 名称绘制
                dc.text(mc.font, entity.getName(), (int) (getX() + 40), (int) (getY() + 16), textColor.getRGB(), true);
            } else {
                if (alphaSet > 0.01) alphaSet -= 0.01;
            }
        }

        if(mode.isCurrentMode("Hanabi")){
            //Distance TH code by Mymylesaws
            int blackcolor = new Color(0, 0, 0, 180).getRGB();
            int blackcolor2 = new Color(200, 200, 200, 160).getRGB();
            float scaledWidth = mc.getWindow().getGuiScaledWidth();
            float scaledHeight = mc.getWindow().getGuiScaledHeight();
            TTFFontRenderer font1 = TTFFontRenderer.get(8);

            nulltarget = false;
            LivingEntity target;
            if (mc.gui.screen() instanceof ChatScreen)
                target = mc.player;
            else
                target = KillAura.target;
            if(target == null) nulltarget = true;

            float x = (float) getX();
            float y = (float) getY();
            setXYWH(getX(), getY(), 140, 40);
            float health;
            double hpPercentage;
            Color hurt;
            int healthColor;
            String healthStr;
            if (nulltarget) {
                health = 0;
                hpPercentage = health / 20;
                hurt = Color.getHSBColor(300f / 360f, ((float) 0 / 10f) * 0.37f, 1f);
                healthStr = String.valueOf((float) 0 / 2.0f);
                healthColor = getHealthColor(0, 20).getRGB();
            } else {
                health = target.getHealth();
                hpPercentage = health / target.getMaxHealth();
                hurt = Color.getHSBColor(310f / 360f, ((float) target.hurtTime / 10f), 1f);
                healthStr = String.valueOf((float) (int) (target.getHealth()) / 2.0f);
                healthColor = getHealthColor(target.getHealth(), target.getMaxHealth()).getRGB();
            }
            hpPercentage = Math.clamp(hpPercentage, 0.0, 1.0);
            double hpWidth = 140.0 * hpPercentage;

            if (nulltarget) {
                this.healthBarWidth2 = RenderUtils.getAnimationStateSmooth(0, this.healthBarWidth2, 6f / Minecraft.getInstance().getFps());
                this.healthBarWidth = RenderUtils.getAnimationStateSmooth(0, this.healthBarWidth, 14f / Minecraft.getInstance().getFps());

                this.hudHeight = RenderUtils.getAnimationStateSmooth(0.0, this.hudHeight, 8f / Minecraft.getInstance().getFps());
            } else {
                this.healthBarWidth2 = AnimationUtils.moveUD((float) this.healthBarWidth2, (float) hpWidth, 6f / Minecraft.getInstance().getFps(), 3f / Minecraft.getInstance().getFps());
                this.healthBarWidth = RenderUtils.getAnimationStateSmooth(hpWidth, this.healthBarWidth, 14f / Minecraft.getInstance().getFps());

                this.hudHeight = RenderUtils.getAnimationStateSmooth(40.0, this.hudHeight, 8f / Minecraft.getInstance().getFps());
            }

            if (hudHeight == 0) {
                this.healthBarWidth2 = 140;
                this.healthBarWidth = 140;
            }

            //GL11.glEnable(3089);
            e.getExtractor().enableScissor((int) x, (int) ((double) y + 40 - hudHeight), (int) (x + 140), (int) (y + 40));
            e.getExtractor().fill((int) x, (int) y, (int) (x + 140.0f), (int) (y + 40.0f), blackcolor);
            e.getExtractor().fill((int) x, (int) (y + 37.0f), (int) ((x) + 140), (int) (y + 40f), new Color(0, 0, 0, 49).getRGB());

            e.getExtractor().fill((int) x, (int) (y + 37.0f), (int) (x + this.healthBarWidth2), (int) (y + 40.0f), new Color(255, 0, 213, 220).getRGB());
            RenderUtils.drawGradientSideways(e.getExtractor(), x - 1, y + 37.0f, (x + this.healthBarWidth - 1), y + 40.0f, new Color(0, 81, 179).getRGB(), healthColor);

            font1.drawStringWithShadow(e.getExtractor(), healthStr, x + 40.0f + 80.0f - font1.width(healthStr) / 2.0f + mc.font.width("\u2764") / 1.9f, y + 25.0f, blackcolor2);
            // Scale around the glyph's own anchor. Scaling the pose about the origin
            // while passing GUI coords (as done before) transforms the glyph quad but
            // not the scissor, and the cull bounds were computed from the unscaled
            // glyph box - so it would be clipped. Translate to the pivot, draw at (0,0).
            float heartX = x + 40.0f + 80.0f - font1.width(healthStr) / 2.0f - mc.font.width("\u2764") / 1.9f;
            float heartY = y + 27.0f;
            e.getExtractor().pose().pushMatrix();
            e.getExtractor().pose().translate(heartX, heartY);
            e.getExtractor().pose().scale(0.9f, 0.9f);
            e.getExtractor().text(mc.font, "\u2764", 0, 0, hurt.getRGB(), true);
            e.getExtractor().pose().popMatrix();
            TTFFontRenderer font2 = TTFFontRenderer.get(7);
            if (nulltarget) {
                font2.drawStringWithShadow(e.getExtractor(), "XYZ:" + 0 + " " + 0 + " " + 0 + " | " + "Hurt: " + (false), x + 37f, y + 15f, Colors.WHITE.c);
                font1.drawStringWithShadow(e.getExtractor(),"(No target)", x + 36.0f, y + 5.0f, Colors.WHITE.c);
            } else {
                font2.drawStringWithShadow(e.getExtractor(),"XYZ:" + (int) target.getX() + " " + (int) (int) target.getY()+ " " + (int) (int) target.getZ() + " | " + "Hurt: " + (target.hurtTime > 0), x + 37f, y + 15f, Colors.WHITE.c);

                if ((target instanceof Player)) {
                    font2.drawStringWithShadow(e.getExtractor(), "Block:" + " " + (((Player) target).isBlocking() ? "True" : "False"), x + 37f, y + 25f, Colors.WHITE.c);
                }

                font1.drawStringWithShadow(e.getExtractor(), target.getName().getString(), x + 36f, y + 4.0f, Colors.WHITE.c);

                if ((target instanceof AbstractClientPlayer player)) {
//                    GlStateManager.resetColor();
//                    mc.getTextureManager().bindTexture(((AbstractClientPlayer) target).getLocationSkin());
//
//                    GlStateManager.color(1, 1, 1);
//                    Gui.drawScaledCustomSizeModalRect((int) x + 3, (int) y + 3, 8.0F, 8.0F, 8, 8, 32, 32, 64, 64);
                    Identifier skin = player.getSkin().body().texturePath();
                    e.getExtractor().blit(
                            RenderPipelines.GUI_TEXTURED,
                            skin,
                            (int) x + 3, (int) y + 3, 8,
                            8,
                            32, 32,
                            8, 8,
                            64, 64                    );
                }
            }
            e.getExtractor().disableScissor();
        }
    }


    public static Color getHealthColor(float health, float maxHealth) {
        float[] fractions = new float[]{0.0f, 0.5f, 1.0f};
        Color[] colors = new Color[]{new Color(0, 81, 179), new Color(0, 153, 255), new Color(47, 154, 241)};
        float progress = health / maxHealth;
        return blendColors(fractions, colors, progress).brighter();
    }

    public static Color blendColors(float[] fractions, Color[] colors, float progress) {
        if (fractions.length == colors.length) {
            int[] indices = getFractionIndices(fractions, progress);
            float[] range = new float[]{fractions[indices[0]], fractions[indices[1]]};
            Color[] colorRange = new Color[]{colors[indices[0]], colors[indices[1]]};
            float max = range[1] - range[0];
            float value = progress - range[0];
            float weight = value / max;
            return blend(colorRange[0], colorRange[1], 1.0f - weight);
        }
        throw new IllegalArgumentException("Fractions and colours must have equal number of elements");
    }

    public static Color blend(Color color1, Color color2, double ratio) {
        float r = (float) ratio;
        float ir = 1.0f - r;
        float[] rgb1 = new float[3];
        float[] rgb2 = new float[3];
        color1.getColorComponents(rgb1);
        color2.getColorComponents(rgb2);
        return new Color(rgb1[0] * r + rgb2[0] * ir, rgb1[1] * r + rgb2[1] * ir, rgb1[2] * r + rgb2[2] * ir);
    }

    public static int[] getFractionIndices(float[] fractions, float progress) {
        int startPoint;
        int[] range = new int[2];
        for (startPoint = 0; startPoint < fractions.length && fractions[startPoint] <= progress; ++startPoint) {
        }
        if (startPoint >= fractions.length) {
            startPoint = fractions.length - 1;
        }
        range[0] = startPoint - 1;
        range[1] = startPoint;
        return range;
    }
}
