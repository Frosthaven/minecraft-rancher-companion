package dev.frosthaven.ranchercompanion.client.screen;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity;
import dev.frosthaven.ranchercompanion.block.TombstoneMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** A tombstone's screen (spec, Tombstone): the gear in its well, the date under it, the player's inventory below. */
public final class TombstoneScreen extends AbstractContainerScreen<TombstoneMenu> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "textures/gui/tombstone.png");

    public TombstoneScreen(TombstoneMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 1000;  /* the steel panel needs no label */
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        g.blit(TEX, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, Art.THREAD, false);
        if (menu.level.getBlockEntity(menu.pos) instanceof TombstoneBlockEntity be && !be.date().isEmpty()) {
            Component d = Component.translatable("screen.rancher_companion.tombstone.date", be.date());
            g.drawString(font, d, (imageWidth - font.width(d)) / 2, 61, Art.DIM, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
