package dev.frosthaven.ranchercompanion.client.screen;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.HatcheryMenu;
import dev.frosthaven.ranchercompanion.client.DrekkiRenderer;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.net.HatcheryAction;
import dev.frosthaven.ranchercompanion.net.HatcheryView;
import dev.frosthaven.ranchercompanion.net.HatcheryView.Row;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The hatchery screen (spec, Checkout: Roster on the hatchery screen; Breeding: Pairing), two tabs over the player's
 * inventory. Roster: the nest (the egg, its incubation on hover, Hatch once Ready), the viewer's drekki with a model
 * portrait, the name and where each is (or how long it rests), then Check out, Check in and Release (with a confirm
 * step). Breeding: {@link BreedTab}. The tab parts are drawn in screen coordinates in {@link #renderBg}; tooltips after
 * the slots.
 */
public final class HatcheryScreen extends AbstractContainerScreen<HatcheryMenu> {
    static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "textures/gui/hatchery.png"),
        TEX_BREED = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "textures/gui/hatchery_breed.png"),
        SPR = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "textures/gui/hatchery_sprites.png"),
        TABS = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "textures/gui/drekki_sprites.png");
    static final int ROWS = 4, ROW_H = 21, LIST_X = 79, LIST_Y = 34, LIST_W = 138;
    static final int BAR_Y = 124, OUT_X = 10, IN_X = 86, REL_X = 162, BW = 60, HATCH_X = 12, HATCH_Y = 97, HATCH_W = 56;
    static final int TAB_W = 52, TAB_H = 12, TAB1 = 122, TAB2 = 174;  /* wider than the drekki screen's 40: "Breeding" needs 44 */
    static final int REST = 0xFFE0B860, BREEDING = 0xFFF09AC8;

    HatcheryView view;
    private int scroll;
    private UUID selected;
    private boolean confirming;
    private final Map<String, DrekkiEntity> models = new HashMap<>();
    final BreedTab breed = new BreedTab(this);
    /** what the mouse is over this frame, set while drawing, shown after the slots */
    List<Component> tip;

    public HatcheryScreen(HatcheryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.view = menu.view;
        imageWidth = HatcheryMenu.W;
        imageHeight = HatcheryMenu.H;
        inventoryLabelY = 10000;  /* the steel panel needs no label */
        titleLabelY = 10000;      /* drawn by renderTitle, clipped before the tabs */
    }

    /** a fresh view from the server: refresh this screen when it shows the same hatchery */
    public static void show(HatcheryView v) {
        if (Minecraft.getInstance().screen instanceof HatcheryScreen s && s.view.pos().equals(v.pos())) s.update(v);
    }

    private void update(HatcheryView v) {
        view = v;
        confirming = false;
        if (selected != null && row(selected) == null) selected = null;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, v.rows().size() - ROWS)));
        breed.update();
    }

    Row row(UUID id) {
        if (id == null) return null;
        for (Row r : view.rows()) if (r.id().equals(id)) return r;
        return null;
    }

    @Override
    protected void init() {
        super.init();
        if (selected == null) for (Row r : view.rows()) if (r.where() == HatcheryView.HERE) { selected = r.id(); break; }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    boolean breeding() { return menu.breeding; }

    private void tab(boolean toBreeding) {
        if (menu.breeding == toBreeding) return;
        menu.breeding = toBreeding;
        confirming = false;
        breed.picking = 0;
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, toBreeding ? 1 : 0);
        click();
    }

    // ---- what each Roster button may do now, and why not ----

    private Component checkOutWhy(Row r) {
        if (r == null) return Component.translatable("screen.rancher_companion.pick");
        if (r.rest() > 0) return Component.translatable("screen.rancher_companion.resting_why", r.rest());
        if (r.breeding()) return Component.translatable("screen.rancher_companion.breeding_why");
        return switch (r.where()) {
            case HatcheryView.WITH_YOU, HatcheryView.CARRIED -> Component.translatable("screen.rancher_companion.already_out");
            case HatcheryView.ELSEWHERE -> Component.translatable("screen.rancher_companion.lives_at", r.place());
            case HatcheryView.TOMB -> Component.translatable("screen.rancher_companion.ghost_stays");
            default -> null;
        };
    }

    private Row out() {
        for (Row r : view.rows()) if (r.where() == HatcheryView.WITH_YOU || r.where() == HatcheryView.CARRIED) return r;
        return null;
    }

    /** the selected row when it is employed: Check in recalls that one (spec, Go here: Employed) */
    private Row employed() {
        Row sel = row(selected);
        return sel != null && sel.where() == HatcheryView.EMPLOYED ? sel : null;
    }

    private Component checkInWhy() {
        if (out() == null && employed() == null) return Component.translatable("screen.rancher_companion.none_out");
        if (view.here() >= 20) return Component.translatable("screen.rancher_companion.full");
        return null;
    }

    private Component releaseWhy(Row r) {
        if (r == null) return Component.translatable("screen.rancher_companion.pick");
        if (r.where() == HatcheryView.ELSEWHERE) return Component.translatable("screen.rancher_companion.lives_at", r.place());
        return null;
    }

    // ---- drawing ----

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        g.blit(breeding() ? TEX_BREED : TEX, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int cx = confirming ? -1 : mx, cy = confirming ? -1 : my;
        header(g, cx, cy);
        if (breeding()) breed.render(g, cx, cy);
        else {
            nest(g, cx, cy);
            roster(g, cx, cy);
            Row sel = row(selected);
            bar(g, cx, cy, OUT_X, Component.translatable("screen.rancher_companion.check_out"), checkOutWhy(sel), false);
            bar(g, cx, cy, IN_X, Component.translatable("screen.rancher_companion.check_in"), checkInWhy(), false);
            bar(g, cx, cy, REL_X, Component.translatable("screen.rancher_companion.release"), releaseWhy(sel), true);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (testMouse != null) { mx = testMouse[0]; my = testMouse[1]; }
        tip = null;
        super.render(g, mx, my, pt);
        if (confirming) confirm(g, mx, my, row(selected));
        else if (tip != null) Art.tip(g, font, tip, false, mx, my);
        else renderTooltip(g, mx, my);
    }

    /** the title on the left, clipped before the tabs; a named one says what it is in the dimmer thread (spec, Checkout:
     *  Names). No coordinates: they do not matter to a player (Shane, 2026-11-24) */
    private void header(GuiGraphics g, int mx, int my) {
        int room = TAB1 - 8 - 6;
        g.drawString(font, font.plainSubstrByWidth(title.getString(), room), leftPos + 8, topPos + 6, Art.THREAD, false);
        Component kind = Component.translatable("block.rancher_companion.hatchery");
        if (view.named() && font.width(title) + 6 + font.width(kind) <= room)
            g.drawString(font, kind, leftPos + 8 + font.width(title) + 6, topPos + 6, Art.DIM, false);
        tabButton(g, TAB1, false, mx, my);
        tabButton(g, TAB2, true, mx, my);
    }

    private void tabButton(GuiGraphics g, int x, boolean isBreeding, int mx, int my) {
        boolean on = breeding() == isBreeding, hover = !on && Art.in(mx, my, leftPos + x, topPos + 4, TAB_W, TAB_H);
        int v = on ? TAB_H : hover ? TAB_H * 2 : 0, tx = leftPos + x, ty = topPos + 4;  /* the 40 px sprite widened: its left part, its middle again, its right edge */
        g.blit(TABS, tx, ty, 0, v, 37, TAB_H);
        g.blit(TABS, tx + 37, ty, 3, v, TAB_W - 40, TAB_H);
        g.blit(TABS, tx + TAB_W - 3, ty, 37, v, 3, TAB_H);
        Component l = Component.translatable("screen.rancher_companion.tab." + (isBreeding ? "breeding" : "roster"));
        g.drawString(font, l, leftPos + x + (TAB_W - font.width(l)) / 2, topPos + 6, on ? Art.THREAD : Art.DIM, false);
    }

    void bar(GuiGraphics g, int mx, int my, int x, Component label, Component why, boolean danger) {
        int bx = leftPos + x, by = topPos + BAR_Y;
        boolean hover = Art.in(mx, my, bx, by, BW, Art.BTN_H);
        Art.Btn st = why != null ? Art.Btn.DISABLED : danger && hover ? Art.Btn.DANGER : hover ? Art.Btn.HOVER : Art.Btn.NORMAL;
        Art.button(g, SPR, font, bx, by, BW, st, label);
        if (hover && why != null) tip = List.of(why);
    }

    private void nest(GuiGraphics g, int mx, int my) {
        int ex = leftPos + 32, ey = topPos + 40;
        if (view.egg().isEmpty()) {
            Component t = Component.translatable("screen.rancher_companion.empty_nest");  /* "Empty Nest" only (Shane, 2026-11-26) */
            g.drawString(font, t, leftPos + 40 - font.width(t) / 2, topPos + 79, Art.DIM, false);
            return;
        }
        g.renderItem(view.egg(), ex, ey);
        if (view.ready()) {
            Component r = Component.translatable("screen.rancher_companion.ready");
            g.drawString(font, r, leftPos + 40 - font.width(r) / 2, topPos + 69, Art.GLOW, false);
            boolean bad = !view.problems().isEmpty();
            boolean hover = Art.in(mx, my, leftPos + HATCH_X, topPos + HATCH_Y, HATCH_W, Art.BTN_H);
            Art.button(g, SPR, font, leftPos + HATCH_X, topPos + HATCH_Y, HATCH_W, bad ? Art.Btn.DISABLED : hover ? Art.Btn.HOVER : Art.Btn.NORMAL,
                Component.translatable("screen.rancher_companion.hatch"));
            if (bad) {
                Component why = Component.translatable("message.rancher_companion.hatch." + view.problems().get(0));
                List<net.minecraft.util.FormattedCharSequence> lines = font.split(why, 54);
                for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), leftPos + 13, topPos + 79 + i * 9, Art.WARN, false);
                if (hover) tip = view.problems().stream().map(p -> (Component) Component.translatable("message.rancher_companion.hatch." + p)).toList();
            }
        }
        if (Art.in(mx, my, ex, ey, 16, 16)) tip = getTooltipFromContainerItem(view.egg());
    }

    private void roster(GuiGraphics g, int mx, int my) {
        g.drawString(font, Component.translatable("screen.rancher_companion.your_drekki"), leftPos + 81, topPos + 25, Art.THREAD, false);
        Component here = Component.translatable("screen.rancher_companion.here_count", view.here(), 20);
        g.drawString(font, here, leftPos + 214 - font.width(here), topPos + 25, Art.DIM, false);
        List<Row> rows = view.rows();
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("screen.rancher_companion.no_drekki"), leftPos + 84, topPos + LIST_Y + 6, Art.DIM, false);
            return;
        }
        for (int i = 0; i < ROWS && i + scroll < rows.size(); i++) {
            Row r = rows.get(i + scroll);
            int x = leftPos + LIST_X, y = topPos + LIST_Y + i * ROW_H;
            rowBox(g, x, y, LIST_W, r.id().equals(selected), Art.in(mx, my, x, y, LIST_W, ROW_H - 1));
            if (!confirming) portrait(g, r, x + 1, y);  /* models draw above the GUI; none under the confirm box */
            g.drawString(font, r.name(), x + 22, y + 2, Art.THREAD, false);
            g.drawString(font, where(r), x + 22, y + 11, r.breeding() ? BREEDING : r.rest() > 0 ? REST : r.where() == HatcheryView.HERE ? Art.GLOW : Art.DIM, false);
        }
        knob(g, leftPos + 217, topPos + LIST_Y, rows.size(), scroll);
    }

    /** a roster row's highlight: picked, or under the mouse (dimmer) */
    void rowBox(GuiGraphics g, int x, int y, int w, boolean sel, boolean hover) {
        if (!sel && !hover) return;
        g.blit(SPR, x, y, 0, 220, w - 3, ROW_H - 2);
        g.blit(SPR, x + w - 3, y, 137, 220, 3, ROW_H - 2);
        g.blit(SPR, x, y + ROW_H - 2, 0, 243, w, 1);
        if (!sel) g.fill(x + 1, y + 1, x + w - 1, y + ROW_H - 2, 0x66170e1b);
    }

    /** the scroll knob, when the list is longer than its four rows */
    void knob(GuiGraphics g, int x, int y, int size, int at) {
        if (size <= ROWS) return;
        int track = ROWS * ROW_H - 15;
        g.blit(SPR, x, y + track * at / (size - ROWS), 144, 220, 6, 15);
    }

    /** the roster column: breeding, or how long it rests after a pairing, else where it is */
    private Component where(Row r) {
        if (r.breeding()) return Component.translatable("screen.rancher_companion.where.breeding");
        if (r.rest() > 0) return Component.translatable("screen.rancher_companion.where.resting", r.rest());
        return switch (r.where()) {
            case HatcheryView.HERE -> Component.translatable("screen.rancher_companion.where.here");
            case HatcheryView.WITH_YOU -> Component.translatable("screen.rancher_companion.where.with_you");
            case HatcheryView.CARRIED -> Component.translatable("screen.rancher_companion.where.carried");
            case HatcheryView.EMPLOYED -> r.place().getString().isEmpty() ? Component.translatable("screen.rancher_companion.where.employed") : r.place();  /* its job's title */
            case HatcheryView.ELSEWHERE -> Component.translatable("screen.rancher_companion.where.at", r.place());
            case HatcheryView.TOMB -> Component.translatable("screen.rancher_companion.where.tomb");
            default -> Component.translatable("screen.rancher_companion.where.none");
        };
    }

    /** a row's model, turned three quarters, in a 20 x 20 box */
    void portrait(GuiGraphics g, Row r, int x, int y) {
        float k = r.stage() == 1 ? 26 : r.stage() == 2 ? 17 : 11;  /* stage 2 and 3 models are at 75% (Dims.scale) */
        model(g, r.id().toString(), r.stage(), r.line(), r.horn(), r.hat(), r.ghost(), x, y, 20, 20, k);
    }

    /** a drekki model of this look, kept per key, in a w x h box */
    void model(GuiGraphics g, String key, int stage, int line, int horn, int hat, int x, int y, int w, int h, float k) {
        model(g, key, stage, line, horn, hat, false, x, y, w, h, k);
    }

    void model(GuiGraphics g, String key, int stage, int line, int horn, int hat, boolean ghost, int x, int y, int w, int h, float k) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        DrekkiEntity d = models.computeIfAbsent(key, id -> RancherCompanion.DREKKI.get().create(mc.level));
        if (d == null) return;
        if (d.stage() != stage || d.line() != line || d.horn() != horn) d.setLook(stage, line, 0, 0, horn);
        if (d.hat() != hat) d.setGear(hat, 0, -1);
        if (d.ghost() != ghost) d.setGhostView(ghost);
        d.tickCount = (int) (mc.level.getGameTime() % 100000);
        DrekkiRenderer.inScreen = true;
        try {
            InventoryScreen.renderEntityInInventoryFollowsAngle(g, x, y, x + w, y + h, (int) k, 0.0625f, 0.6f, -0.2f, d);
        } finally { DrekkiRenderer.inScreen = false; }
    }

    private void confirm(GuiGraphics g, int mx, int my, Row sel) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);  /* over the slots' items */
        g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xC0100a12);
        int px = leftPos + 26, py = topPos + 50, pw = imageWidth - 52, ph = 70;
        g.fill(px, py, px + pw, py + ph, 0xFF221428);
        g.renderOutline(px, py, pw, ph, 0xFF5D636E);
        Component name = sel == null ? Component.empty() : sel.name();
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.translatable("screen.rancher_companion.release_confirm", name), pw - 12);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), px + 6, py + 6 + i * 10, Art.THREAD, false);
        boolean hy = Art.in(mx, my, px + 8, py + ph - 22, BW, Art.BTN_H), hn = Art.in(mx, my, px + pw - 8 - BW, py + ph - 22, BW, Art.BTN_H);
        Art.button(g, SPR, font, px + 8, py + ph - 22, BW, hy ? Art.Btn.DANGER : Art.Btn.NORMAL, Component.translatable("screen.rancher_companion.release"));
        Art.button(g, SPR, font, px + pw - 8 - BW, py + ph - 22, BW, hn ? Art.Btn.HOVER : Art.Btn.NORMAL, Component.translatable("screen.rancher_companion.keep"));
        g.pose().popPose();
    }

    // ---- for the self test (client/SelfTest): press a control by name, as a click on it would ----

    public HatcheryView view() { return view; }

    /** the self test's mouse (a cursor moved by the program does not reach a headless window): screen coordinates, or null */
    public static int[] testMouse;

    /** the names the open parent picker lists, empty when it is closed */
    public List<String> pickerNames() { return breed.picking == 0 || breed.picking == 3 ? List.of() : breed.choiceNames(); }

    /** the open picker's scroll (the parent list, or the Heirloom trait list) */
    public int pickScroll() { return breed.pickScroll(); }

    /** the Heirloom trait picker is open */
    public boolean keepPickerOpen() { return breed.picking == 3; }

    /** parent 0 (A) or 1 (B)'s name, "" when not picked */
    public String parentName(int i) { Row r = row(breed.parent(i)); return r == null ? "" : r.name().getString(); }

    /** the egg slot's center, in GUI pixels */
    public int[] eggCenter() { return new int[]{leftPos + 40, topPos + 48}; }

    public boolean press(String what) {
        int[] at = switch (what) {
            case "out" -> new int[]{leftPos + OUT_X + 5, topPos + BAR_Y + 5};
            case "in" -> new int[]{leftPos + IN_X + 5, topPos + BAR_Y + 5};
            case "release" -> new int[]{leftPos + REL_X + 5, topPos + BAR_Y + 5};
            case "pair", "cancel" -> new int[]{leftPos + BreedTab.PAIR_X + 5, topPos + BAR_Y + 5};
            case "confirm" -> new int[]{leftPos + 26 + 10, topPos + 50 + 70 - 20};
            case "hatch" -> new int[]{leftPos + HATCH_X + 5, topPos + HATCH_Y + 5};
            case "tab_roster" -> new int[]{leftPos + TAB1 + 5, topPos + 6};
            case "tab_breeding" -> new int[]{leftPos + TAB2 + 5, topPos + 6};
            case "parent0" -> new int[]{leftPos + BreedTab.A_X + 20, topPos + BreedTab.P_Y + 20};
            case "parent1" -> new int[]{leftPos + BreedTab.B_X + 20, topPos + BreedTab.P_Y + 20};
            case "gear" -> new int[]{leftPos + BreedTab.GEAR_X + 5, topPos + BreedTab.GEAR_Y + 5};
            default -> what.startsWith("row") ? new int[]{leftPos + LIST_X + 30, topPos + LIST_Y + Integer.parseInt(what.substring(3)) * ROW_H + 5}
                : what.startsWith("trait") ? new int[]{leftPos + BreedTab.PICK_X + 20, topPos + BreedTab.TRAIT_LIST_Y + Integer.parseInt(what.substring(5)) * BreedTab.TRAIT_H + 3}
                : what.startsWith("pick") ? new int[]{leftPos + BreedTab.PICK_X + 30, topPos + LIST_Y + Integer.parseInt(what.substring(4)) * ROW_H + 5} : null;
        };
        return at != null && mouseClicked(at[0], at[1], 0);
    }

    /** the spot of a named control, for hovering (screen coordinates) */
    public int[] spot(String what) {
        return switch (what) {
            case "kept" -> new int[]{leftPos + BreedTab.C_X + 20, topPos + BreedTab.TRAITS_Y + 14};
            case "eyes" -> new int[]{leftPos + BreedTab.C_X + 40, topPos + 87 + 3};
            case "pair" -> new int[]{leftPos + BreedTab.PAIR_X + 5, topPos + BAR_Y + 5};
            default -> null;
        };
    }

    public List<Component> shownTip() { return tip; }

    /** whether the child's preview showed its coat (false: the "?" badge) */
    public boolean previewKnown() { return breed.lastKnown; }

    /** the eyes the preview showed: "1/2" (one of), "1+2" (one eye each) */
    public String previewEyes() { return breed.lastEyes; }

    /** the Heirloom Tonic's trait picked in the screen, or null */
    public String keepTrait() { return breed.keep; }

    /** the odds the preview's traits tooltip shows, by trait id */
    public java.util.Map<String, Double> previewOdds() { return breed.lastOdds; }

    /** how far the breeding is, 0 to 1, or -1 when none */
    public float breedProgress() { return view.breeding() ? breed.progress() : -1; }

    // ---- input ----

    void send(Ranch.Action a, UUID id, UUID other) { send(a, id, other, ""); }

    void send(Ranch.Action a, UUID id, UUID other, String trait) {
        PacketDistributor.sendToServer(new HatcheryAction(view.pos(), a.ordinal(), id == null ? HatcheryAction.NONE : id, other == null ? HatcheryAction.NONE : other, trait == null ? "" : trait));
        click();
    }

    static void click() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        Row sel = row(selected);
        if (confirming) {
            int px = leftPos + 26, py = topPos + 50, pw = imageWidth - 52, ph = 70;
            if (Art.in(mx, my, px + 8, py + ph - 22, BW, Art.BTN_H) && sel != null) send(Ranch.Action.RELEASE, sel.id(), null);
            if (Art.in(mx, my, px + 8, py + ph - 22, BW, Art.BTN_H) || Art.in(mx, my, px + pw - 8 - BW, py + ph - 22, BW, Art.BTN_H)) confirming = false;
            return true;
        }
        if (Art.in(mx, my, leftPos + TAB1, topPos + 4, TAB_W, TAB_H)) { tab(false); return true; }
        if (Art.in(mx, my, leftPos + TAB2, topPos + 4, TAB_W, TAB_H)) { tab(true); return true; }
        if (breeding()) return breed.click(mx, my) || super.mouseClicked(mx, my, button);
        for (int i = 0; i < ROWS && i + scroll < view.rows().size(); i++)
            if (Art.in(mx, my, leftPos + LIST_X, topPos + LIST_Y + i * ROW_H, LIST_W, ROW_H - 1)) { selected = view.rows().get(i + scroll).id(); return true; }
        int by = topPos + BAR_Y;
        if (Art.in(mx, my, leftPos + OUT_X, by, BW, Art.BTN_H) && checkOutWhy(sel) == null) { send(Ranch.Action.CHECK_OUT, sel.id(), null); return true; }
        if (Art.in(mx, my, leftPos + IN_X, by, BW, Art.BTN_H) && checkInWhy() == null) { Row job = employed(); send(Ranch.Action.CHECK_IN, job == null ? null : job.id(), null); return true; }
        if (Art.in(mx, my, leftPos + REL_X, by, BW, Art.BTN_H) && releaseWhy(sel) == null) { confirming = true; return true; }
        if (view.ready() && view.problems().isEmpty() && Art.in(mx, my, leftPos + HATCH_X, topPos + HATCH_Y, HATCH_W, Art.BTN_H)) { send(Ranch.Action.HATCH, null, null); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (breeding()) return breed.scroll(dy) || super.mouseScrolled(mx, my, dx, dy);
        if (!Art.in(mx, my, leftPos + LIST_X, topPos + LIST_Y, LIST_W, ROWS * ROW_H)) return super.mouseScrolled(mx, my, dx, dy);
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(dy), Math.max(0, view.rows().size() - ROWS)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (confirming) { confirming = false; return true; }
            if (breed.picking != 0) { breed.picking = 0; return true; }
        }
        return super.keyPressed(key, scan, mods);
    }
}
