package dev.frosthaven.ranchercompanion.client.screen;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.entity.DrekkiMenu;
import dev.frosthaven.ranchercompanion.look.Astral;
import dev.frosthaven.ranchercompanion.look.Diet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The drekki screen (pup release): the hat slot, the drekki's own model (it turns to follow the mouse), its details and
 * health, and the player's inventory below; drawn from tools/screen_textures.py's drekki sheet.
 */
public final class DrekkiScreen extends AbstractContainerScreen<DrekkiMenu> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "textures/gui/drekki.png");
    private static final ResourceLocation HEART = ResourceLocation.withDefaultNamespace("hud/heart/full"),
        HALF = ResourceLocation.withDefaultNamespace("hud/heart/half"), EMPTY = ResourceLocation.withDefaultNamespace("hud/heart/container");
    /** the growth stages as players see them: never "stage 1, 2, 3" in game */
    private static final String[] STAGES = {"Pup", "Fledgling", "Mature"};

    private static final net.minecraft.world.item.ItemStack GHOST_HAT = dev.frosthaven.ranchercompanion.item.DrekkiItems.hatStack(5);

    public DrekkiScreen(DrekkiMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 196;
        inventoryLabelY = 10000;  /* the steel panel needs no label */
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        g.blit(TEX, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        /* an empty hat slot shows a dim straw hat, as on the design page: the item, then the slot's own fill over it at 70% */
        if (menu.slots.get(0).getItem().isEmpty()) {
            int x = leftPos + dev.frosthaven.ranchercompanion.entity.DrekkiMenu.HAT_X, y = topPos + dev.frosthaven.ranchercompanion.entity.DrekkiMenu.HAT_Y;
            g.renderFakeItem(GHOST_HAT, x, y);
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            g.fill(x, y, x + 16, y + 16, 0xB3221428);
            g.pose().popPose();
        }
        DrekkiEntity d = menu.drekki;
        if (d == null) return;
        float k = d.stage() == 1 ? 46 : d.stage() == 2 ? 26 : 16;
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, leftPos + 29, topPos + 23, leftPos + 87, topPos + 97, (int) k, 0.0625f, mx, my, d);
    }

    /** the header as the design page draws it: the name, then the growth stage in the dimmer thread; the details beside the
     *  model, health first. Only what a pup has today: no tabs, traits, stance, food or pack yet */
    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        DrekkiEntity d = menu.drekki;
        g.drawString(font, title, 8, 6, Art.THREAD, false);
        if (d != null) g.drawString(font, STAGES[d.stage() - 1], 8 + font.width(title) + 6, 6, Art.DIM, false);
        if (d == null) return;
        int x = 96, y = 26;
        /* health: vanilla's hearts, eight to a row */
        int hearts = (int) Math.ceil(d.getMaxHealth() / 2), full = (int) d.getHealth() / 2;
        boolean half = ((int) Math.ceil(d.getHealth())) % 2 == 1;
        for (int i = 0; i < hearts; i++) {
            int hx = x + (i % 8) * 9, hy = y + (i / 8) * 10;
            g.blitSprite(EMPTY, hx, hy, 9, 9);
            if (i < full) g.blitSprite(HEART, hx, hy, 9, 9);
            else if (i == full && half) g.blitSprite(HALF, hx, hy, 9, 9);
        }
        int ty = y + ((hearts + 7) / 8) * 10 + 4;
        Component diet = d.stage() == 1 ? Component.translatable("screen.rancher_companion.drekki.diet_open")
            : Component.translatable("screen.rancher_companion.drekki.diet", Diet.of(d.diet()).label);
        /* each line wraps inside the details well (92..170, text from 96), as vanilla wraps text: 10 px a wrapped row,
           12 between lines */
        for (Component line : new Component[]{Component.literal(d.lineage().name()),
                Component.translatable("screen.rancher_companion.drekki.astral", Astral.NAMES[d.astral()]), diet}) {
            for (var row : font.split(line, 71)) { g.drawString(font, row, x, ty, Art.THREAD, false); ty += 10; }
            ty += 2;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
