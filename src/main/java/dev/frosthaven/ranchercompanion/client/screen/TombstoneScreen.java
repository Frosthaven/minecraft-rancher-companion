package dev.frosthaven.ranchercompanion.client.screen;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity;
import dev.frosthaven.ranchercompanion.block.TombstoneMenu;
import dev.frosthaven.ranchercompanion.ranch.Rebirth;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * A tombstone's screen (spec, Tombstone; the way back): the gear in its well and the date under it. With a Drekki Lotus
 * Blossom in a slot, a cyan meter fills over one Minecraft day (a few pixels from the start); once it is full, "Reclaim
 * fallen drekki" takes its place. Only the drekki's owner can reclaim it (others see why on hover); anyone may take the gear.
 */
public final class TombstoneScreen extends AbstractContainerScreen<TombstoneMenu> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "textures/gui/tombstone.png"),
        SPR = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "textures/gui/hatchery_sprites.png");
    /** the free wood under the slots runs from about 52 to 104 (Shane, 2026-11-26): the message field is centered there, and
     *  "Died ..." sits on its line when there is no field; one status row under it holds the meter, its lines and Reclaim */
    static final int FIELD_Y = 71, FIELD_H = 14, DATE_Y = FIELD_Y + 4, STATUS_Y = 87;
    static final int CYAN = 0xFF40E0E8, BAR_X = 28, BAR_Y = STATUS_Y + 2, BAR_W = 120, BTN_X = 28, BTN_Y = STATUS_Y - 1, BTN_W = 120;
    /** the self test's mouse (a cursor moved by the program does not reach a headless window): screen coordinates, or null */
    public static int[] testMouse;
    private List<Component> tip;

    public TombstoneScreen(TombstoneMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = TombstoneMenu.H;
        inventoryLabelY = 10000;  /* the steel panel needs no label */
    }

    /** a decorative tombstone's message field, under the slots (Shane, 2026-11-26); null on one with a record */
    private net.minecraft.client.gui.components.EditBox message;
    private String sent;

    @Override
    protected void init() {
        super.init();
        TombstoneBlockEntity b = be();
        if (b == null || b.showsRecord()) return;
        message = new net.minecraft.client.gui.components.EditBox(font, leftPos + 28, topPos + FIELD_Y, 120, FIELD_H, Component.translatable("screen.rancher_companion.tombstone.message"));
        message.setMaxLength(TombstoneBlockEntity.MESSAGE_MAX);
        message.setValue(b.message());
        message.setHint(Component.translatable("screen.rancher_companion.tombstone.message").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        sent = b.message();
        addRenderableWidget(message);
    }

    /** sends the message when it changed (Enter, or the screen closing) */
    public void sendMessage() {
        if (message == null || message.getValue().equals(sent)) return;
        sent = message.getValue();
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.frosthaven.ranchercompanion.net.TombMessage(menu.pos, sent));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (message != null && message.isFocused()) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) { sendMessage(); message.setFocused(false); return true; }
            if (key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return message.keyPressed(key, scan, mods) || true;  /* typing: no inventory key */
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        sendMessage();
        super.removed();
    }

    /** for the self test: types into the field */
    public void typeMessage(String text) { if (message != null) message.setValue(text); }

    private TombstoneBlockEntity be() { return menu.level.getBlockEntity(menu.pos) instanceof TombstoneBlockEntity b ? b : null; }

    /** the meter, 0 to 1, on the client's day time from the start the tombstone sent */
    public float progress() {
        TombstoneBlockEntity b = be();
        var level = Minecraft.getInstance().level;
        return b == null || level == null ? 0 : Rebirth.progress(b, level.getDayTime());
    }

    /** the blossom's day is done: Reclaim shows */
    public boolean ready() { TombstoneBlockEntity b = be(); return b != null && b.showsRecord() && b.reviveStart() != 0 && progress() >= 1; }

    /** a blossom in, its candle not lit yet: "The candle is ready to be lit." */
    public boolean unlit() {
        TombstoneBlockEntity b = be();
        if (b == null || !b.showsRecord() || b.reviveStart() != 0) return false;
        for (int i = 0; i < TombstoneBlockEntity.SLOTS; i++) if (menu.getSlot(i).getItem().is(dev.frosthaven.ranchercompanion.item.DrekkiItems.LOTUS_BLOSSOM.get())) return true;
        return false;
    }

    /** the meter shows: a blossom's day running */
    public boolean running() { TombstoneBlockEntity b = be(); return b != null && b.showsRecord() && b.reviveStart() != 0 && progress() < 1; }

    /** why Reclaim does nothing (client side: what it knows; the server checks again), or null */
    public Component buttonWhy() { return menu.owner ? null : Component.translatable("message.rancher_companion.tomb.not_owner"); }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        g.blit(TEX, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (running()) {  /* the blossom's day: a cyan meter on the wood, a few pixels from the start so it reads as a meter */
            int x = leftPos + BAR_X, y = topPos + BAR_Y;
            g.fill(x, y, x + BAR_W, y + 6, 0xFF170E1B);
            g.renderOutline(x - 1, y - 1, BAR_W + 2, 8, 0xFF5D636E);
            g.fill(x + 1, y + 1, x + 1 + Math.max(3, Math.round((BAR_W - 2) * progress())), y + 5, CYAN);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, Art.THREAD, false);
        TombstoneBlockEntity b = be();
        if (b != null && !b.date().isEmpty()) {
            Component d = Component.translatable("screen.rancher_companion.tombstone.date", b.date());
            g.drawString(font, d, (imageWidth - font.width(d)) / 2, DATE_Y, Art.DIM, false);
        }
        if (unlit()) {  /* a blossom in, the candle not lit yet (Shane, 2026-11-26); off soul soil, it says so instead */
            Component c = Component.translatable(b.onSoulSoil() ? "screen.rancher_companion.tombstone.unlit" : "screen.rancher_companion.tombstone.soul_soil");
            g.drawString(font, c, (imageWidth - font.width(c)) / 2, STATUS_Y + 2, Art.THREAD, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (testMouse != null) { mx = testMouse[0]; my = testMouse[1]; }
        tip = null;
        super.render(g, mx, my, pt);
        if (ready()) {
            Component why = buttonWhy();
            int x = leftPos + BTN_X, y = topPos + BTN_Y;
            boolean hover = Art.in(mx, my, x, y, BTN_W, Art.BTN_H);
            Art.button(g, SPR, font, x, y, BTN_W, why != null ? Art.Btn.DISABLED : hover ? Art.Btn.HOVER : Art.Btn.NORMAL,
                Component.translatable("screen.rancher_companion.tombstone.reclaim"));
            if (hover && why != null) tip = List.of(why);
        }
        if (tip != null) Art.tip(g, font, tip, false, mx, my);
        else renderTooltip(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        TombstoneBlockEntity b = be();
        if (button == 0 && ready() && Art.in(mx, my, leftPos + BTN_X, topPos + BTN_Y, BTN_W, Art.BTN_H)) {
            if (buttonWhy() == null && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1);
                HatcheryScreen.click();
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    // ---- for the self test ----

    public int[] buttonSpot() { return new int[]{leftPos + BTN_X + 10, topPos + BTN_Y + 5}; }

    public List<Component> shownTip() { return tip; }
}
