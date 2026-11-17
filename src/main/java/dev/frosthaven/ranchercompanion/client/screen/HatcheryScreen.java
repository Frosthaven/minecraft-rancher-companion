package dev.frosthaven.ranchercompanion.client.screen;

import dev.frosthaven.ranchercompanion.RancherCompanion;
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
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The hatchery screen (spec, Checkout: Roster on the hatchery screen; Pup release: Screens). Left, the nest: the egg
 * (its incubation on hover) and Hatch once it is Ready. Right, the viewer's drekki with a model portrait, the name
 * (or bloodline) and where each is. Below, Check out, Check in and Release (with a confirm step). No inventory.
 */
public final class HatcheryScreen extends Screen {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "textures/gui/hatchery.png");
    private static final int W = 232, H = 170, ROWS = 4, ROW_H = 21, LIST_X = 79, LIST_Y = 43, LIST_W = 138;
    private static final int BAR_Y = 142, OUT_X = 10, IN_X = 86, REL_X = 162, BW = 60, HATCH_X = 12, HATCH_Y = 104, HATCH_W = 56;

    private HatcheryView view;
    private int left, top, scroll;
    private UUID selected;
    private boolean confirming;
    private final Map<UUID, DrekkiEntity> portraits = new HashMap<>();

    public HatcheryScreen(HatcheryView view) {
        super(view.title());
        this.view = view;
    }

    /** a fresh view from the server: refresh this screen when it shows the same hatchery, else open a new one */
    public static void show(HatcheryView v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof HatcheryScreen s && s.view.pos().equals(v.pos())) s.update(v);
        else mc.setScreen(new HatcheryScreen(v));
    }

    private void update(HatcheryView v) {
        view = v;
        confirming = false;
        if (selected != null && row(selected) == null) selected = null;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, v.rows().size() - ROWS)));
    }

    private Row row(UUID id) {
        for (Row r : view.rows()) if (r.id().equals(id)) return r;
        return null;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (selected == null) for (Row r : view.rows()) if (r.where() == HatcheryView.HERE) { selected = r.id(); break; }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---- what each button may do now, and why not ----

    private Component checkOutWhy(Row r) {
        if (r == null) return Component.translatable("screen.rancher_companion.pick");
        return switch (r.where()) {
            case HatcheryView.WITH_YOU, HatcheryView.CARRIED -> Component.translatable("screen.rancher_companion.already_out");
            case HatcheryView.ELSEWHERE -> Component.translatable("screen.rancher_companion.lives_at", r.place());
            default -> null;
        };
    }

    private Row out() {
        for (Row r : view.rows()) if (r.where() == HatcheryView.WITH_YOU || r.where() == HatcheryView.CARRIED) return r;
        return null;
    }

    private Component checkInWhy() {
        if (out() == null) return Component.translatable("screen.rancher_companion.none_out");
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
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        g.blit(TEX, left, top, 0, 0, W, H);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int cx = confirming ? -1 : mx, cy = confirming ? -1 : my;
        /* the title on the left, then what it is and where in the dimmer thread (spec, Checkout: Names) */
        g.drawString(font, title, left + 8, top + 8, Art.THREAD, false);
        BlockPos p = view.pos();
        Component sub = Component.literal((view.named() ? "Drekki Hatchery · " : "") + p.getX() + ", " + p.getY() + ", " + p.getZ());
        g.drawString(font, sub, left + 8 + font.width(title) + 6, top + 8, Art.DIM, false);
        nest(g, cx, cy);
        roster(g, cx, cy);
        Row sel = selected == null ? null : row(selected);
        bar(g, cx, cy, OUT_X, Component.translatable("screen.rancher_companion.check_out"), checkOutWhy(sel), false);
        bar(g, cx, cy, IN_X, Component.translatable("screen.rancher_companion.check_in"), checkInWhy(), false);
        bar(g, cx, cy, REL_X, Component.translatable("screen.rancher_companion.release"), releaseWhy(sel), true);
        if (confirming) confirm(g, mx, my, sel);
    }

    private void bar(GuiGraphics g, int mx, int my, int x, Component label, Component why, boolean danger) {
        int bx = left + x, by = top + BAR_Y;
        boolean hover = Art.in(mx, my, bx, by, BW, Art.BTN_H);
        Art.Btn st = why != null ? Art.Btn.DISABLED : danger && hover ? Art.Btn.DANGER : hover ? Art.Btn.HOVER : Art.Btn.NORMAL;
        Art.button(g, TEX, font, bx, by, BW, st, label);
        if (hover && why != null) g.renderTooltip(font, why, mx, my);
    }

    private void nest(GuiGraphics g, int mx, int my) {
        int ex = left + 32, ey = top + 47;
        if (view.egg().isEmpty()) {
            for (int i = 0; i < 3; i++) {  /* three short lines: the nest well is 54 px wide inside */
                Component t = Component.translatable("screen.rancher_companion.empty_nest." + i);
                g.drawString(font, t, left + 40 - font.width(t) / 2, top + 76 + i * 10, Art.DIM, false);
            }
            return;
        }
        g.renderItem(view.egg(), ex, ey);
        if (view.ready()) {
            Component r = Component.translatable("screen.rancher_companion.ready");
            g.drawString(font, r, left + 40 - font.width(r) / 2, top + 76, Art.GLOW, false);
            boolean bad = !view.problems().isEmpty();
            boolean hover = Art.in(mx, my, left + HATCH_X, top + HATCH_Y, HATCH_W, Art.BTN_H);
            Art.button(g, TEX, font, left + HATCH_X, top + HATCH_Y, HATCH_W, bad ? Art.Btn.DISABLED : hover ? Art.Btn.HOVER : Art.Btn.NORMAL,
                Component.translatable("screen.rancher_companion.hatch"));
            if (bad) {
                Component why = Component.translatable("message.rancher_companion.hatch." + view.problems().get(0));
                List<net.minecraft.util.FormattedCharSequence> lines = font.split(why, 54);
                for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), left + 13, top + 86 + i * 9, Art.WARN, false);
                if (hover) g.renderComponentTooltip(font, view.problems().stream()
                    .map(p -> (Component) Component.translatable("message.rancher_companion.hatch." + p)).toList(), mx, my);
            }
        }
        if (Art.in(mx, my, ex, ey, 16, 16)) g.renderTooltip(font, view.egg(), mx, my);
    }

    private void roster(GuiGraphics g, int mx, int my) {
        g.drawString(font, Component.translatable("screen.rancher_companion.your_drekki"), left + 81, top + 32, Art.THREAD, false);
        Component here = Component.translatable("screen.rancher_companion.here_count", view.here(), 20);
        g.drawString(font, here, left + 214 - font.width(here), top + 32, Art.DIM, false);
        List<Row> rows = view.rows();
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("screen.rancher_companion.no_drekki"), left + 84, top + LIST_Y + 6, Art.DIM, false);
            return;
        }
        for (int i = 0; i < ROWS && i + scroll < rows.size(); i++) {
            Row r = rows.get(i + scroll);
            int x = left + LIST_X, y = top + LIST_Y + i * ROW_H;
            boolean sel = r.id().equals(selected), hover = Art.in(mx, my, x, y, LIST_W, ROW_H - 1);
            if (sel || hover) {
                g.blit(TEX, x, y, 0, 220, LIST_W - 3, ROW_H - 2);
                g.blit(TEX, x + LIST_W - 3, y, 137, 220, 3, ROW_H - 2);
                g.blit(TEX, x, y + ROW_H - 2, 0, 243, LIST_W, 1);
                if (!sel) g.fill(x + 1, y + 1, x + LIST_W - 1, y + ROW_H - 2, 0x66170e1b);
            }
            if (!confirming) portrait(g, r, x + 1, y);  /* models draw above the GUI; none under the confirm box */
            g.drawString(font, r.name(), x + 22, y + 2, Art.THREAD, false);
            g.drawString(font, where(r), x + 22, y + 11, r.where() == HatcheryView.HERE ? Art.GLOW : Art.DIM, false);
        }
        if (rows.size() > ROWS) {
            int track = ROWS * ROW_H - 15, ky = top + LIST_Y + track * scroll / (rows.size() - ROWS);
            g.blit(TEX, left + 217, ky, 144, 220, 6, 15);
        }
    }

    private Component where(Row r) {
        return switch (r.where()) {
            case HatcheryView.HERE -> Component.translatable("screen.rancher_companion.where.here");
            case HatcheryView.WITH_YOU -> Component.translatable("screen.rancher_companion.where.with_you");
            case HatcheryView.CARRIED -> Component.translatable("screen.rancher_companion.where.carried");
            case HatcheryView.ELSEWHERE -> Component.translatable("screen.rancher_companion.where.at", r.place());
            default -> Component.translatable("screen.rancher_companion.where.none");
        };
    }

    /** the drekki's own model, turned three quarters, in a 20 x 20 box */
    private void portrait(GuiGraphics g, Row r, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        DrekkiEntity d = portraits.computeIfAbsent(r.id(), id -> {
            DrekkiEntity e = RancherCompanion.DREKKI.get().create(mc.level);
            if (e != null) { e.setLook(r.stage(), r.line(), 0, 0, r.horn()); e.setGear(r.hat(), 0, -1); }
            return e;
        });
        if (d == null) return;
        d.tickCount = (int) (mc.level.getGameTime() % 100000);
        float k = r.stage() == 1 ? 26 : r.stage() == 2 ? 13 : 8;
        InventoryScreen.renderEntityInInventoryFollowsAngle(g, x, y, x + 20, y + 20, (int) k, 0.0625f, 0.6f, -0.2f, d);
    }

    private void confirm(GuiGraphics g, int mx, int my, Row sel) {
        g.fill(left + 3, top + 3, left + W - 3, top + H - 3, 0xC0100a12);
        int px = left + 26, py = top + 50, pw = W - 52, ph = 70;
        g.fill(px, py, px + pw, py + ph, 0xFF221428);
        g.renderOutline(px, py, pw, ph, 0xFF5D636E);
        Component name = sel == null ? Component.empty() : sel.name();
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.translatable("screen.rancher_companion.release_confirm", name), pw - 12);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), px + 6, py + 6 + i * 10, Art.THREAD, false);
        boolean hy = Art.in(mx, my, px + 8, py + ph - 22, BW, Art.BTN_H), hn = Art.in(mx, my, px + pw - 8 - BW, py + ph - 22, BW, Art.BTN_H);
        Art.button(g, TEX, font, px + 8, py + ph - 22, BW, hy ? Art.Btn.DANGER : Art.Btn.NORMAL, Component.translatable("screen.rancher_companion.release"));
        Art.button(g, TEX, font, px + pw - 8 - BW, py + ph - 22, BW, hn ? Art.Btn.HOVER : Art.Btn.NORMAL, Component.translatable("screen.rancher_companion.keep"));
    }

    // ---- for the self test (client/SelfTest): press a control by name, as a click on it would ----

    public HatcheryView view() { return view; }

    /** the egg slot's center, in GUI pixels */
    public int[] eggCenter() { return new int[]{left + 40, top + 55}; }

    public boolean press(String what) {
        int[] at = switch (what) {
            case "out" -> new int[]{left + OUT_X + 5, top + BAR_Y + 5};
            case "in" -> new int[]{left + IN_X + 5, top + BAR_Y + 5};
            case "release" -> new int[]{left + REL_X + 5, top + BAR_Y + 5};
            case "confirm" -> new int[]{left + 26 + 10, top + 50 + 70 - 20};
            case "hatch" -> new int[]{left + HATCH_X + 5, top + HATCH_Y + 5};
            default -> what.startsWith("row") ? new int[]{left + LIST_X + 30, top + LIST_Y + Integer.parseInt(what.substring(3)) * ROW_H + 5} : null;
        };
        return at != null && mouseClicked(at[0], at[1], 0);
    }

    // ---- input ----

    private void send(Ranch.Action a, UUID id) {
        PacketDistributor.sendToServer(new HatcheryAction(view.pos(), a.ordinal(), id == null ? HatcheryAction.NONE : id));
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        Row sel = selected == null ? null : row(selected);
        if (confirming) {
            int px = left + 26, py = top + 50, pw = W - 52, ph = 70;
            if (Art.in(mx, my, px + 8, py + ph - 22, BW, Art.BTN_H) && sel != null) send(Ranch.Action.RELEASE, sel.id());
            if (Art.in(mx, my, px + 8, py + ph - 22, BW, Art.BTN_H) || Art.in(mx, my, px + pw - 8 - BW, py + ph - 22, BW, Art.BTN_H)) confirming = false;
            return true;
        }
        for (int i = 0; i < ROWS && i + scroll < view.rows().size(); i++)
            if (Art.in(mx, my, left + LIST_X, top + LIST_Y + i * ROW_H, LIST_W, ROW_H - 1)) { selected = view.rows().get(i + scroll).id(); return true; }
        int by = top + BAR_Y;
        if (Art.in(mx, my, left + OUT_X, by, BW, Art.BTN_H) && checkOutWhy(sel) == null) { send(Ranch.Action.CHECK_OUT, sel.id()); return true; }
        if (Art.in(mx, my, left + IN_X, by, BW, Art.BTN_H) && checkInWhy() == null) { send(Ranch.Action.CHECK_IN, null); return true; }
        if (Art.in(mx, my, left + REL_X, by, BW, Art.BTN_H) && releaseWhy(sel) == null) { confirming = true; return true; }
        if (view.ready() && view.problems().isEmpty() && Art.in(mx, my, left + HATCH_X, top + HATCH_Y, HATCH_W, Art.BTN_H)) { send(Ranch.Action.HATCH, null); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(dy), Math.max(0, view.rows().size() - ROWS)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (confirming && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { confirming = false; return true; }
        if (Minecraft.getInstance().options.keyInventory.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
