package dev.naominet.listclient.module.render;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventRender2D;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.module.combat.KillAura;
import dev.naominet.listclient.ui.theme.M3;
import dev.naominet.listclient.utils.RenderUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.awt.*;

public class TargetHUD extends Module {
    int defaultWidth = 125;
    double alphaSet = 1;
    public TargetHUD() {
        super("TargetHUD", Category.Render);
        setXYWH(8, 108, 125, 40);
    }

    @EventTarget
    public void onRender2D(EventRender2D e){

        if(KillAura.target == null && !(mc.gui.screen() instanceof ChatScreen)){
            if(alphaSet > 0)
                alphaSet -= 0.1f;
        }

        LivingEntity entity;
        if(mc.gui.screen() instanceof ChatScreen)
            entity = mc.player;
        else
            entity = KillAura.target;

        GuiGraphicsExtractor dc = e.getExtractor();
        setXYWH(getX(), getY(), defaultWidth, 40);

        // Clamp alpha between 0 and 1
        alphaSet = Math.clamp(alphaSet, 0, 1);

        Color backgroundColor = new Color(0, 0, 0, (int)(152 * alphaSet));
        Color textColor = new Color(255, 255, 255, (int)(200 * alphaSet));

        dc.fill((int) getX(), (int) getY(), (int) (getX() + getWidth()), (int) (getY() + getHeight()), backgroundColor.getRGB());

        if (entity != null) {
            RenderUtils.drawShadow(dc, (float) getX(), (float) getY(), (float) getWidth(), (float) getHeight());

            if (alphaSet < 1.0) alphaSet += 0.05;
            float healthRatio = entity.getHealth() / entity.getMaxHealth();
            int filledWidth = (int)(getWidth() * healthRatio);
            int barY = (int)(getY() + 38);
            dc.fill((int) getX(), barY, (int)(getX() + filledWidth), barY + 2, M3.PRIMARY);

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
            dc.text(mc.font, entity.getName(), (int)(getX() + 40), (int)(getY() + 16), textColor.getRGB(), true);
        } else {
            if (alphaSet > 0.01) alphaSet -= 0.01;

            //dc.fill((int) getX(), (int) getY(), (int)(getX() + getWidth()), (int)(getY() + getHeight()), backgroundColor.getRGB());
            //dc.drawText(mc.textRenderer, "No target :(", (int)(getX() + 40), (int)(getY() + 16), textColor.getRGB(), true);
        }

    }
}
