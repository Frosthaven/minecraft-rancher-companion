package dev.frosthaven.ranchercompanion.client.screen;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.entity.DrekkiMenu;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.trait.Tips;
import dev.frosthaven.ranchercompanion.look.Astral;
import dev.frosthaven.ranchercompanion.look.Diet;
import dev.frosthaven.ranchercompanion.trait.Trait;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/**
 * The drekki screen (spec, Drekki screen; design page, UI tab), drawn from tools/screen_textures.py's drekki sheets: the
 * name over two tabs (Details, Traits), the hat slot, the drekki's own model (it turns to follow the mouse),
 * then Details (hearts, food, bloodline, astral, diet, mood with its buffs) or Traits (its slots by stage, each with its
 * tooltip), the stance buttons at the foot from stage 2, its pack from stage 2, and the player's inventory below.
 */
public final class DrekkiScreen extends AbstractContainerScreen<DrekkiMenu> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "textures/gui/drekki.png"),
        TEX_GEAR = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "textures/gui/drekki_gear.png"),
        SPR = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "textures/gui/drekki_sprites.png");
    private static final ResourceLocation HEART = ResourceLocation.withDefaultNamespace("hud/heart/full"),
        HALF = ResourceLocation.withDefaultNamespace("hud/heart/half"), EMPTY = ResourceLocation.withDefaultNamespace("hud/heart/container"),
        FOOD = ResourceLocation.withDefaultNamespace("hud/food_full"), FOOD_HALF = ResourceLocation.withDefaultNamespace("hud/food_half"),
        FOOD_EMPTY = ResourceLocation.withDefaultNamespace("hud/food_empty");
    private static final net.minecraft.world.item.ItemStack GHOST_HAT = dev.frosthaven.ranchercompanion.item.DrekkiItems.hatStack(5);
    /** the faint items in the empty barding and accessory slots: what goes there */
    private net.minecraft.world.item.ItemStack ghostBarding, ghostBag;
    /** drekki_sprites (tools/screen_textures.py: SX, TAB_*, STANCE_*, ICON_Y, BUFF_Y) */
    private static final int SX = 0, TAB_W = 40, TAB_H = 12, STANCE = 14, STANCE_Y = 40, ICON_Y = 56, BUFF_Y = 66;
    /** the wells: model 28..80 x 22..122, info 83..214; text from 86, 125 wide; the tabs at the header's right */
    private static final int INFO_X = 86, INFO_W = 125, WELL_TOP = 22, WELL_BOTTOM = 122, TAB1 = 134, TAB2 = 174;
    private static final int[] BAND_COLOR = {0xFFC04050, 0xFFF0B040, 0xFFC9A45A, 0xFF6AA83E, 0xFF7FF0DC};

    private boolean traitsTab;
    /** where this frame drew each part, in screen coordinates (the self test hovers and clicks them) */
    private final java.util.Map<String, int[]> spots = new java.util.HashMap<>();

    public int[] spot(String key) { return spots.get(key); }

    /** the self test's mouse (a cursor moved by the program does not reach a headless window): screen coordinates, or null */
    public static int[] testMouse;

    private void spot(String key, int x, int y, int w, int h) { spots.put(key, new int[]{leftPos + x + w / 2, topPos + y + h / 2}); }
    /** what the mouse is over this frame, set while drawing, shown after */
    private List<Component> tip;

    public DrekkiScreen(DrekkiMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        traitsTab = menu.traitsTab;
        imageWidth = 220;
        imageHeight = 220;
        inventoryLabelY = 10000;  /* the steel panel needs no label */
    }

    private ResourceLocation tex() { return menu.gear ? TEX_GEAR : TEX; }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        g.blit(tex(), leftPos, topPos, 0, 0, imageWidth, imageHeight);
        /* an empty equipment slot shows what goes there, dim: the item, then the slot's own fill over it at 70% */
        DrekkiEntity d = menu.drekki;
        ghost(g, 0, GHOST_HAT, DrekkiMenu.HAT_Y);
        if (menu.gear && d != null) {
            if (ghostBarding == null) {
                ghostBarding = new net.minecraft.world.item.ItemStack((d.stage() == 2 ? dev.frosthaven.ranchercompanion.item.DrekkiItems.FLEDGLING_BARDING
                    : dev.frosthaven.ranchercompanion.item.DrekkiItems.BARDING).get(dev.frosthaven.ranchercompanion.item.Barding.IRON).get());
                ghostBag = new net.minecraft.world.item.ItemStack(dev.frosthaven.ranchercompanion.item.DrekkiItems.BAGS.get(d.stage() == 2 ? 0 : 1).get());
            }
            ghost(g, 1, ghostBarding, DrekkiMenu.BARDING_Y);
            ghost(g, 2, ghostBag, DrekkiMenu.ACCESSORY_Y);
        }
        if (d == null) return;
        int k = d.stage() == 1 ? 42 : d.stage() == 2 ? 35 : 21;  /* the frame as before: the model is at 75% from stage 2 (Dims.scale) */
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, leftPos + 29, topPos + 23, leftPos + 79, topPos + 121, k, 0.0625f, mx, my, d);
    }

    private void ghost(GuiGraphics g, int slot, net.minecraft.world.item.ItemStack item, int sy) {
        if (!menu.slots.get(slot).getItem().isEmpty()) return;
        int x = leftPos + DrekkiMenu.HAT_X, y = topPos + sy;
        g.renderFakeItem(item, x, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        g.fill(x, y, x + 16, y + 16, 0xB3221428);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        DrekkiEntity d = menu.drekki;
        tip = null;
        spots.clear();
        if (menu.gear) spot("accessory", DrekkiMenu.HAT_X, DrekkiMenu.ACCESSORY_Y, 16, 16);  /* the self test right-clicks the bag here */
        int rx = mx - leftPos, ry = my - topPos;
        g.drawString(font, title, 8, 6, Art.THREAD, false);
        if (d == null) return;
        /* the name only (2026-11-20): no stage, no ghost tag */
        tab(g, TAB1, false, rx, ry);
        tab(g, TAB2, true, rx, ry);
        spot("tab_details", TAB1, 4, TAB_W, TAB_H);
        spot("tab_traits", TAB2, 4, TAB_W, TAB_H);
        if (traitsTab) traits(g, d, rx, ry); else details(g, d, rx, ry);
        if (d.stage() >= 2) stances(g, d, rx, ry);
    }

    private void tab(GuiGraphics g, int x, boolean traits, int rx, int ry) {
        boolean on = traitsTab == traits, hover = !on && Art.in(rx, ry, x, 4, TAB_W, TAB_H);
        g.blit(SPR, x, 4, SX, on ? TAB_H : hover ? TAB_H * 2 : 0, TAB_W, TAB_H);
        Component l = Component.translatable("screen.rancher_companion.drekki." + (traits ? "traits" : "details"));
        g.drawString(font, l, x + (TAB_W - font.width(l)) / 2, 6, on ? Art.THREAD : Art.DIM, false);
    }

    /** health, food, bloodline, astral, diet, and mood with its buffs */
    private void details(GuiGraphics g, DrekkiEntity d, int rx, int ry) {
        int x = INFO_X, y = 26;
        int hearts = (int) Math.ceil(d.getMaxHealth() / 2), full = (int) d.getHealth() / 2;
        boolean half = ((int) Math.ceil(d.getHealth())) % 2 == 1;
        int perRow = hearts > 20 ? 13 : 10;  /* vanilla's ten to a row; a Mature's 25 take 13 so they stay two rows over the text */
        for (int i = 0; i < hearts; i++) {  /* vanilla's HUD: 8 px apart */
            int hx = x + (i % perRow) * 8, hy = y + (i / perRow) * 9;
            g.blitSprite(EMPTY, hx, hy, 9, 9);
            if (i < full) g.blitSprite(HEART, hx, hy, 9, 9);
            else if (i == full && half) g.blitSprite(HALF, hx, hy, 9, 9);
        }
        y += ((hearts + perRow - 1) / perRow) * 9 + 1;
        float food = Life.shownFood(d.food());
        for (int i = 0; i < Life.FOOD_MAX; i++) {
            int fx = x + i * 8;
            g.blitSprite(FOOD_EMPTY, fx, y, 9, 9);
            if (food >= i + 1) g.blitSprite(FOOD, fx, y, 9, 9);
            else if (food >= i + 0.5f) g.blitSprite(FOOD_HALF, fx, y, 9, 9);
        }
        if (Art.in(rx, ry, x, y, 81, 9)) tip = List.of(Component.translatable("screen.rancher_companion.drekki.food", food % 1 == 0 ? String.valueOf((int) food) : String.valueOf(food), Life.FOOD_MAX));
        int ty = y + 12;
        Component diet = d.stage() == 1 ? Component.translatable("screen.rancher_companion.drekki.diet_open")
            : Component.translatable("screen.rancher_companion.drekki.diet", Diet.of(d.diet()).label);
        ty = text(g, Component.literal(d.lineage().name()), ty, Art.THREAD) + 2;
        /* the astral name in its eye color (Dormant's brown is too dark on the panel: dim instead), its tooltip on hover,
           the same lines as the chat message when it settles */
        int a = d.astral();
        Component astral = Component.translatable("screen.rancher_companion.drekki.astral",
            Component.literal(Astral.NAMES[a]).withColor(a == Astral.DORMANT ? Art.DIM : 0xFF000000 | Astral.iris(a)));
        spot("astral", x, ty - 1, INFO_W, 10);
        if (Art.in(rx, ry, x, ty - 1, INFO_W, 10)) tip = dev.frosthaven.ranchercompanion.trait.Tips.astral(a);
        ty = text(g, astral, ty, Art.THREAD) + 2;
        ty = text(g, diet, ty, Art.THREAD) + 2;
        Life.Band band = d.band();
        Component bandName = Component.translatable("mood.rancher_companion." + band.name().toLowerCase());
        Component mood = Component.translatable("screen.rancher_companion.drekki.mood", "");
        g.drawString(font, mood, x, ty, Art.THREAD, false);
        int bx = x + font.width(mood);
        g.drawString(font, bandName, bx, ty, BAND_COLOR[band.ordinal()], false);
        if (Art.in(rx, ry, x, ty - 1, font.width(mood) + font.width(bandName), 10)) tip = moodTip(d, band);
        spot("mood", x, ty - 1, font.width(mood) + font.width(bandName), 10);
        /* buffs after the band: Recently pet, Kinship */
        int ix = bx + font.width(bandName) + 3;
        if (d.petLeft() > 0) {
            g.blit(SPR, ix, ty - 1, SX, BUFF_Y, 9, 9);
            spot("pet", ix, ty - 1, 9, 9);
            if (Art.in(rx, ry, ix, ty - 1, 9, 9)) tip = List.of(Component.translatable("screen.rancher_companion.drekki.pet").withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.translatable("screen.rancher_companion.drekki.pet.desc").withStyle(ChatFormatting.GRAY),
                Component.translatable("screen.rancher_companion.drekki.pet.duration", Tips.time(Life.PET_MILLIS / 50)).withStyle(ChatFormatting.GOLD),
                Component.translatable("screen.rancher_companion.drekki.pet.ends_in", Tips.time(d.petLeft() * 20L)).withStyle(ChatFormatting.RED));
            ix += 10;
        }
        if (d.kin() > 0) {
            g.blit(SPR, ix, ty - 1, SX + 10, BUFF_Y, 9, 9);
            if (Art.in(rx, ry, ix, ty - 1, 9, 9)) tip = List.of(Component.translatable("screen.rancher_companion.drekki.kin").withStyle(ChatFormatting.GOLD),
                Component.translatable("screen.rancher_companion.drekki.kin.desc", d.kin(), Math.round(Life.kinBonus(d.kin()))).withStyle(ChatFormatting.GRAY));
        }
    }

    /** the band's name, effects and reasons (spec, Mood: the hover tooltip) */
    private List<Component> moodTip(DrekkiEntity d, Life.Band band) {
        List<Component> t = new ArrayList<>();
        t.add(Component.translatable("mood.rancher_companion." + band.name().toLowerCase()).withStyle(s -> s.withColor(BAND_COLOR[band.ordinal()])));
        t.add(Component.translatable("mood.rancher_companion." + band.name().toLowerCase() + ".desc").withStyle(ChatFormatting.GRAY));
        if (d.food() <= 0) t.add(Component.translatable("screen.rancher_companion.drekki.reason.hungry").withStyle(ChatFormatting.RED));
        var owner = d.ownerPlayer();
        if (owner != null && owner.distanceToSqr(d) > 30 * 30) t.add(Component.translatable("screen.rancher_companion.drekki.reason.far").withStyle(ChatFormatting.RED));
        if (d.petLeft() > 0) t.add(Component.translatable("screen.rancher_companion.drekki.reason.pet").withStyle(ChatFormatting.GREEN));
        if (d.kin() > 0) t.add(Component.translatable("screen.rancher_companion.drekki.reason.kin", Math.round(Life.kinBonus(d.kin()))).withStyle(ChatFormatting.GREEN));
        return t;
    }

    /** the offer's yes and no buttons: 9 x 9, at the right of its row */
    private static final int ANSWER = 9, YES_X = INFO_X + INFO_W - 2 * ANSWER - 3, NO_X = INFO_X + INFO_W - ANSWER - 1;

    /** the row the offer sits in (the first free slot), or -1 with none */
    private int offerRow(DrekkiEntity d) {
        if (d.ghost() || !d.traitView().contains("offer")) return -1;
        int n = d.traitView().getList("traits", Tag.TAG_STRING).size();
        return n < Trait.cap(d.stage()) ? 26 + n * 10 : -1;
    }

    /** its trait slots by stage: learned ones in green with their tooltip, the offer in gold with yes and no, free ones dim */
    private void traits(GuiGraphics g, DrekkiEntity d, int rx, int ry) {
        List<String> held = new ArrayList<>();
        for (Tag s : d.traitView().getList("traits", Tag.TAG_STRING)) held.add(s.getAsString());
        int y = 26, slots = Math.max(Trait.cap(d.stage()), held.size()), offerY = offerRow(d);
        for (int i = 0; i < slots; i++) {
            if (y == offerY) { offer(g, d, d.traitView().getString("offer"), y, rx, ry); y += 10; continue; }
            if (i < held.size()) {
                Component name = Component.translatable("trait.rancher_companion." + held.get(i));
                long readyIn = Math.max(0, d.traitView().getCompound("ready").getLong(held.get(i)) - d.level().getGameTime());
                g.fill(INFO_X, y + 2, INFO_X + 4, y + 6, 0xFF6AA83E);
                g.drawString(font, name, INFO_X + 7, y, readyIn > 0 ? 0xFF6F9A73 : 0xFF9FE3A6, false);
                if (readyIn > 0) hourglass(g, INFO_X + 7 + font.width(name) + 3, y);  /* recharging: dimmer name and an hourglass after it */
                int h = 10;
                spot("trait" + i, INFO_X, y - 1, INFO_W, h);
                if (Art.in(rx, ry, INFO_X, y - 1, INFO_W, h)) tip = dev.frosthaven.ranchercompanion.trait.Tips.trait(held.get(i), readyIn, d.ghost());
            } else {
                g.fill(INFO_X, y + 2, INFO_X + 4, y + 6, 0xFF3B2A44);
                g.drawString(font, Component.translatable("screen.rancher_companion.drekki.free_slot"), INFO_X + 7, y, Art.DIM, false);
            }
            y += 10;
        }
    }

    /** the trait it wants to learn: a gold name with its tooltip, then yes (learn it) and no (pass) */
    private void offer(GuiGraphics g, DrekkiEntity d, String id, int y, int rx, int ry) {
        Component name = Component.translatable("trait.rancher_companion." + id);
        g.fill(INFO_X, y + 2, INFO_X + 4, y + 6, 0xFFE0A84A);
        g.drawString(font, font.plainSubstrByWidth(name.getString(), YES_X - INFO_X - 9), INFO_X + 7, y, 0xFFF0C860, false);
        spot("offer", INFO_X, y - 1, YES_X - INFO_X - 2, 10);
        if (Art.in(rx, ry, INFO_X, y - 1, YES_X - INFO_X - 2, 10)) {
            List<Component> t = new ArrayList<>(dev.frosthaven.ranchercompanion.trait.Tips.trait(id, 0, false));
            t.add(Component.translatable("screen.rancher_companion.drekki.offer").withStyle(ChatFormatting.GOLD));
            tip = t;
        }
        answer(g, YES_X, y - 1, true, rx, ry);
        answer(g, NO_X, y - 1, false, rx, ry);
    }

    /** a 9 x 9 button: a check on green (learn) or a cross on red (pass), lighter on hover */
    private void answer(GuiGraphics g, int x, int y, boolean yes, int rx, int ry) {
        boolean hover = Art.in(rx, ry, x, y, ANSWER, ANSWER);
        spot(yes ? "offer_yes" : "offer_no", x, y, ANSWER, ANSWER);
        int edge = yes ? 0xFF2F5A30 : 0xFF6A2630, face = yes ? (hover ? 0xFF5FA85C : 0xFF467F44) : (hover ? 0xFFC0505A : 0xFF8E3A44), mark = 0xFFF4F0E0;
        g.fill(x, y, x + ANSWER, y + ANSWER, edge);
        g.fill(x + 1, y + 1, x + ANSWER - 1, y + ANSWER - 1, face);
        if (yes) {  /* a check: down-right two, then up-right four */
            for (int i = 0; i < 2; i++) g.fill(x + 2 + i, y + 4 + i, x + 3 + i, y + 5 + i, mark);
            for (int i = 0; i < 4; i++) g.fill(x + 4 + i, y + 5 - i, x + 5 + i, y + 6 - i, mark);
        } else for (int i = 0; i < 5; i++) {  /* a cross */
            g.fill(x + 2 + i, y + 2 + i, x + 3 + i, y + 3 + i, mark);
            g.fill(x + 6 - i, y + 2 + i, x + 7 - i, y + 3 + i, mark);
        }
        if (hover) tip = List.of(Component.translatable("screen.rancher_companion.drekki.offer_" + (yes ? "yes" : "no")).withStyle(yes ? ChatFormatting.GREEN : ChatFormatting.RED),
            Component.translatable("screen.rancher_companion.drekki.offer_" + (yes ? "yes" : "no") + ".desc").withStyle(ChatFormatting.GRAY));
    }

    /** a 5 x 8 gold hourglass: the trait is recharging */
    private static void hourglass(GuiGraphics g, int x, int y) {
        int c = 0xFFE0A84A, s = 0xFFB87A2A;
        g.fill(x, y, x + 5, y + 1, c); g.fill(x, y + 7, x + 5, y + 8, c);
        g.fill(x + 1, y + 1, x + 4, y + 2, s); g.fill(x + 2, y + 2, x + 3, y + 3, s);
        g.fill(x + 2, y + 5, x + 3, y + 6, s); g.fill(x + 1, y + 6, x + 4, y + 7, s);
        g.fill(x + 2, y + 3, x + 3, y + 5, c);
    }

    /** the three stance buttons at the foot of the info well, whichever tab is open (spec, Combat; design page, UI tab) */
    private void stances(GuiGraphics g, DrekkiEntity d, int rx, int ry) {
        int y = WELL_BOTTOM - STANCE - 2;
        for (Life.Stance s : Life.Stance.values()) {
            int x = INFO_X + s.ordinal() * (STANCE + 2);
            boolean on = d.stance() == s, over = Art.in(rx, ry, x, y, STANCE, STANCE), hover = over && !d.ghost();
            spot("stance" + s.ordinal(), x, y, STANCE, STANCE);
            g.blit(SPR, x, y, SX + (on ? 1 : hover ? 2 : 0) * STANCE, STANCE_Y, STANCE, STANCE);
            g.blit(SPR, x + 3, y + 3, SX + s.ordinal() * 8, ICON_Y, 8, 8);
            if (over) tip = d.ghost() ? List.of(Component.translatable("stance.rancher_companion." + s.name().toLowerCase()).withStyle(ChatFormatting.WHITE),
                    Component.translatable("screen.rancher_companion.drekki.ghost_stance").withStyle(ChatFormatting.GRAY))
                : List.of(Component.translatable("stance.rancher_companion." + s.name().toLowerCase()).withStyle(ChatFormatting.WHITE),
                    Component.translatable("stance.rancher_companion." + s.name().toLowerCase() + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }

    /** a line wrapped inside the info well, as vanilla wraps text: 10 px a row; returns the next y */
    private int text(GuiGraphics g, Component line, int y, int color) {
        for (FormattedCharSequence row : font.split(line, INFO_W)) { g.drawString(font, row, INFO_X, y, color, false); y += 10; }
        return y;
    }

    /** Sophisticated's Open Backpack key over the bag it wears opens that bag (spec, Accessory: bags) */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (menu.gear && menu.drekki != null && hoveredSlot != null && hoveredSlot.index == 2 && hoveredSlot.hasItem() && menu.getCarried().isEmpty()
            && dev.frosthaven.ranchercompanion.item.Bags.sophisticated()
            && dev.frosthaven.ranchercompanion.compat.sophisticated.client.SophKeys.isOpenKey(keyCode, scanCode)) {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, DrekkiMenu.BAG);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double rx = mx - leftPos, ry = my - topPos;
        if (button == 0 && Art.in(rx, ry, TAB1, 4, TAB_W, TAB_H)) { traitsTab = false; click(); return true; }
        if (button == 0 && Art.in(rx, ry, TAB2, 4, TAB_W, TAB_H)) { traitsTab = true; click(); return true; }
        DrekkiEntity d = menu.drekki;
        /* right-click the bag it wears to open it, as a placed shulker box opens (spec, Accessory: bags); a left click still takes it */
        if (button == 1 && menu.gear && d != null && menu.getCarried().isEmpty() && menu.slots.get(2).hasItem()
            && Art.in(rx, ry, DrekkiMenu.HAT_X, DrekkiMenu.ACCESSORY_Y, 16, 16)) {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, DrekkiMenu.BAG);
            click();
            return true;
        }
        int offerY = d == null || !traitsTab ? -1 : offerRow(d);
        if (button == 0 && offerY >= 0) for (int b = 0; b < 2; b++)
            if (Art.in(rx, ry, b == 0 ? YES_X : NO_X, offerY - 1, ANSWER, ANSWER)) {
                if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, b == 0 ? DrekkiMenu.LEARN : DrekkiMenu.PASS);
                click();
                return true;
            }
        if (button == 0 && d != null && d.stage() >= 2 && !d.ghost()) for (Life.Stance s : Life.Stance.values()) {
            int x = INFO_X + s.ordinal() * (STANCE + 2), y = WELL_BOTTOM - STANCE - 2;
            if (Art.in(rx, ry, x, y, STANCE, STANCE)) {
                if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, s.ordinal());
                click();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void click() {
        if (minecraft != null) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (testMouse != null) { mx = testMouse[0]; my = testMouse[1]; }
        super.render(g, mx, my, pt);
        if (tip != null && menu.getCarried().isEmpty()) Art.tip(g, font, tip, true, mx, my);
        else renderTooltip(g, mx, my);
    }
}
