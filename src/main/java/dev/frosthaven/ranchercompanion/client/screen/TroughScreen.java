package dev.frosthaven.ranchercompanion.client.screen;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.TroughMenu;
import dev.frosthaven.ranchercompanion.look.Diet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** The trough's screen: its line's food slots in the trough's well, the diet it holds beside the title, and the player's inventory below. */
public final class TroughScreen extends AbstractContainerScreen<TroughMenu> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "textures/gui/trough.png");

    public TroughScreen(TroughMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = TroughMenu.INV_Y - 11 + 1000;  /* the steel panel needs no label */
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        g.blit(TEX, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int n = menu.foodSlots();  /* a slot frame per trough of the line: the inventory's own frame, copied */
        for (int i = 0; i < n; i++) g.blit(TEX, leftPos + TroughMenu.slotX(i, n) - 1, topPos + TroughMenu.slotY(i, n) - 1, 7, 83, 18, 18);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, Art.THREAD, false);
        Diet d = menu.lineDiet();
        Component holds = d == null ? Component.translatable("screen.rancher_companion.trough.any")
            : Component.translatable("screen.rancher_companion.trough.holds", d.label);
        g.drawString(font, holds, imageWidth - 8 - font.width(holds), 6, d == null ? Art.DIM : Art.THREAD, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
