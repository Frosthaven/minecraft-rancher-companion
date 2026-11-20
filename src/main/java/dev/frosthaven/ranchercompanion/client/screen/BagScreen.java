package dev.frosthaven.ranchercompanion.client.screen;

import dev.frosthaven.ranchercompanion.entity.BagMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * A drekki's bag (spec, Accessory: bags), drawn as vanilla draws a chest: the header, one row strip per 9 slots (up to
 * 7, past the 6 the chest sheet holds), then the player's inventory. A last part row shows only its slots.
 */
public final class BagScreen extends AbstractContainerScreen<BagMenu> {
    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public BagScreen(BagMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageHeight = 114 + menu.rows * 18;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        g.blit(CHEST, x, y, 0, 0, imageWidth, 17);
        int slots = menu.slots.size() - 36;
        for (int r = 0; r < menu.rows; r++) {
            int inRow = Math.min(9, slots - r * 9);
            g.blit(CHEST, x, y + 17 + r * 18, 0, 17, 7, 18);                                  /* the left edge */
            g.blit(CHEST, x + 7, y + 17 + r * 18, 7, 17, inRow * 18, 18);                      /* its slots */
            if (inRow < 9) g.fill(x + 7 + inRow * 18, y + 17 + r * 18, x + 169, y + 35 + r * 18, 0xFFC6C6C6);  /* no slots: the plain panel */
            g.blit(CHEST, x + 169, y + 17 + r * 18, 169, 17, 7, 18);                          /* the right edge */
        }
        g.blit(CHEST, x, y + 17 + menu.rows * 18, 0, 126, imageWidth, 96);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
