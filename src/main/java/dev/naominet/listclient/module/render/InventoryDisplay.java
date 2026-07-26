package dev.naominet.listclient.module.render;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventRender2D;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.utils.RenderUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.awt.*;
import java.util.List;

public class InventoryDisplay  extends Module {
    public InventoryDisplay() {
        super("InventoryDisplay", Category.Render);
    }

    @EventTarget
    public void onRender2d(EventRender2D e) {
        GuiGraphicsExtractor extractor = e.getExtractor();


        int boxWidth = 170;
        int boxHeight = 70;

        setXYWH(getX(), getY(), boxWidth, boxHeight);
        RenderUtils.drawShadow(extractor, (float) getX(), (float) getY(), boxWidth, boxHeight);

        // Background
        extractor.fill((int) getX(), (int) getY(), (int) (getX() + boxWidth), (int) (getY() + boxHeight), new Color(0, 0, 0, 115).getRGB());

        // Header
        extractor.fill((int) getX(), (int) getY(), (int) (getX() + boxWidth), (int) (getY() + 12), new Color(0, 0, 0, 150).getRGB());
        extractor.text(mc.font, "Inventory", (int) (getX() + 4), (int) (getY() + 2), -1);

        // Items rendering
        List<ItemStack> mainStacks = mc.player.getInventory().getNonEquipmentItems();

        int startX = (int) (getX() + 4);
        int startY = (int) (getY() + 14);
        int itemSize = 16;
        int spacing = 2;
        int itemsPerRow = 9;

        for (int i = 9; i < 36; i++) { // Skip hotbar (0-8), render inventory only
            ItemStack item = mainStacks.get(i);
            int index = i - 9;
            int row = index / itemsPerRow;
            int col = index % itemsPerRow;

            int drawX = startX + col * (itemSize + spacing);
            int drawY = startY + row * (itemSize + spacing);
            extractor.item(item, drawX, drawY);
            if (!item.isEmpty() && item.getCount() > 1) {
                String countText = String.valueOf(item.getCount());
                extractor.itemDecorations(mc.font, item, drawX, drawY, countText);
            }
        }
    }
}
