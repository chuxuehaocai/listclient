package dev.naominet.listclient.module.render;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventRender2D;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.ui.theme.M3;
import dev.naominet.listclient.utils.Lang;
import dev.naominet.listclient.utils.font.TTFFontRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class InventoryDisplay  extends Module {

    private static final int HEADER_H = 12;

    private final TTFFontRenderer headerFont = M3.label();

    public InventoryDisplay() {
        super("InventoryDisplay", Category.Render);
    }

    @EventTarget
    public void onRender2d(EventRender2D e) {
        GuiGraphicsExtractor extractor = e.getExtractor();


        int boxWidth = 170;
        int boxHeight = 70;

        setXYWH(getX(), getY(), boxWidth, boxHeight);

        int x = (int) getX();
        int y = (int) getY();

        // Panel: surface container, medium shape; translucent for game visibility.
        M3.shadow(extractor, x, y, boxWidth, boxHeight, M3.SHAPE_M);
        M3.roundRect(extractor, x, y + HEADER_H, boxWidth, boxHeight - HEADER_H,
                M3.SHAPE_M, M3.withAlpha(M3.SURFACE_CONTAINER, 0xE6), false, false, true, true);

        // Header strip one container step above the panel.
        M3.roundRect(extractor, x, y, boxWidth, HEADER_H,
                M3.SHAPE_M, M3.withAlpha(M3.SURFACE_CONTAINER_HIGH, 0xE6), true, true, false, false);
        headerFont.drawString(extractor, Lang.tr("hud.inventory"), x + 4,
                y + (HEADER_H - headerFont.lineHeight()) / 2f, M3.ON_SURFACE);

        // Items rendering
        List<ItemStack> mainStacks = mc.player.getInventory().getNonEquipmentItems();

        int startX = x + 4;
        int startY = y + HEADER_H + 2;
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
            if (!item.isEmpty()) {
                extractor.item(mc.player, item, drawX, drawY, i + 1);
                extractor.itemDecorations(mc.font, item, drawX, drawY);
            }
        }
    }

}
