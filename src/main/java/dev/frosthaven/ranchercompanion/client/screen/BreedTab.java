package dev.frosthaven.ranchercompanion.client.screen;

import dev.frosthaven.ranchercompanion.life.Breeding;
import dev.frosthaven.ranchercompanion.look.Lineage;
import dev.frosthaven.ranchercompanion.net.HatcheryView.Row;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.trait.Trait;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The hatchery's Breeding tab (spec, Breeding: Pairing; first pass decided with Shane 2026-11-26): two parent wells side
 * by side, each picked from the Mature drekki that live here and are not resting (a click on a well lists them in the
 * right well; unfit ones are not listed), the tonic slot under them, and the right well's preview of the child by
 * {@link Breeding}'s rules: its fur (a mixed pairing cycles through every bloodline it may take; a coat its owner never
 * had shows a "?" badge and no name, muted coats always show), its line and tier, its
 * horns and the eyes it may get (icons, names on hover); with the Heirloom Tonic, "Keep:" and the kept trait. The trait
 * odds are not shown (they are known). Pair needs both
 * parents, an empty nest and a hatchery under its cap. Breeding then takes one Minecraft day: the parents stay in their
 * wells, a bar under the preview fills, and Cancel takes Pair's place.
 */
final class BreedTab {
    static final int A_X = 9, B_X = 67, P_Y = 22, P_W = 54, P_H = 72, C_X = 125, C_W = 98, PICK_X = 127, PICK_W = 90, PAIR_X = 162;
    static final int TRAITS_Y = 98, KEEP_Y = 108, TRAIT_LIST_Y = 35, TRAIT_H = 9, TRAIT_ROWS = 9;
    /** the gear after the tonic slot (its settings), then the "Tonic (optional)" words */
    static final int GEAR_X = 29, GEAR_Y = 101, GEAR_W = 14, TEXT_X = 46;
    /** the tonic in the slot last frame: a different one (or none) resets the settings */
    private net.minecraft.world.item.Item lastTonic;
    private int traitScroll;
    /** the Heirloom Tonic's trait to keep, picked in the screen; null for none */
    String keep;
    /** the odds the preview showed last (the self test reads them) */
    Map<String, Double> lastOdds = Map.of();
    /** the "?" badge on hatchery_sprites (tools/screen_textures.py: QMARK) */
    static final int UNKNOWN_U = 156, UNKNOWN_V = 220;
    /** the preview drawn last: its coat known (the self test reads it) */
    boolean lastKnown;
    /** the eyes the preview drew last: astral numbers, "/" between choices, "+" in a pair (the self test reads it) */
    String lastEyes = "";

    /** a coat the preview may show: one the viewer has had, or a muted one (open to everyone) */
    private boolean known(int line) { return Breeding.tier(line) == 0 && line < Breeding.BLOODLINES || (line >= 0 && line < 64 && (s.view.coats() >> line & 1) != 0); }

    private final HatcheryScreen s;
    UUID a, b;
    /** 0: the preview shows; 1 or 2: the right well lists the drekki to pick for parent A or B; 3: the parents' traits, to
     *  pick the Heirloom Tonic's */
    int picking;
    private int scroll;

    BreedTab(HatcheryScreen s) { this.s = s; }

    private List<Row> fit() {
        List<Row> out = new ArrayList<>();
        for (Row r : s.view.rows()) if (r.fit()) out.add(r);
        return out;
    }

    /** the list a picker shows: the fit drekki, without the other parent */
    private List<Row> choices() {
        UUID other = picking == 1 ? b : a;
        List<Row> out = fit();
        out.removeIf(r -> r.id().equals(other));
        return out;
    }

    /** a new view: a parent that is no longer fit (it paired and rests, it left) is unpicked */
    void update() {
        if (s.view.breeding()) { a = s.view.breedA(); b = s.view.breedB(); picking = 0; return; }
        Row ra = s.row(a), rb = s.row(b);
        if (ra == null || !ra.fit()) a = null;
        if (rb == null || !rb.fit()) b = null;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, choices().size() - HatcheryScreen.ROWS)));
    }

    private Component pairWhy() {
        if (!s.view.pairBlock().isEmpty()) return Component.translatable("message.rancher_companion." + s.view.pairBlock());
        if (fit().size() < 2) return Component.translatable("screen.rancher_companion.breed.need_two");
        if (a == null || b == null) return Component.translatable("screen.rancher_companion.breed.pick_two");
        if (heirloom() && !union().contains(keep)) return Component.translatable("message.rancher_companion.pair.keep");
        return null;
    }

    /** how far the breeding is, 0 to 1, counted on the client's day time from the view's start */
    float progress() {
        var level = Minecraft.getInstance().level;
        long now = level == null ? s.view.breedStart() : level.getDayTime();
        return Math.max(0, Math.min(1, (now - s.view.breedStart()) / (float) dev.frosthaven.ranchercompanion.ranch.Pairing.DAY));
    }

    void render(GuiGraphics g, int mx, int my) {
        Font font = Minecraft.getInstance().font;
        if (s.view.breeding() && (a == null || !a.equals(s.view.breedA()))) update();
        int L = s.getGuiLeft(), T = s.getGuiTop();
        parent(g, font, s.row(a), A_X, picking == 1, mx, my);
        parent(g, font, s.row(b), B_X, picking == 2, mx, my);
        tonicChanged();
        gearButton(g, mx, my);
        g.drawString(font, Component.translatable("screen.rancher_companion.breed.tonic"), L + TEXT_X, T + 101, Art.THREAD, false);
        g.drawString(font, Component.translatable("screen.rancher_companion.breed.optional"), L + TEXT_X, T + 111, Art.DIM, false);
        if (s.getMenu().tonic().isEmpty() && Art.in(mx, my, L + 9, T + 100, 18, 18)) s.tip = List.of(Component.translatable("screen.rancher_companion.breed.no_tonics"));
        if (picking == 3) traitPicker(g, font, mx, my); else if (picking != 0) picker(g, font, mx, my); else preview(g, font, mx, my);
        if (s.view.breeding()) { breedingBar(g, font, mx, my); return; }
        Component why = pairWhy();
        if (why != null) {
            var lines = font.split(why, PAIR_X - 14);
            for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), L + 10, T + HatcheryScreen.BAR_Y + (lines.size() > 1 ? i * 9 : 4), Art.WARN, false);
        }
        s.bar(g, mx, my, PAIR_X, Component.translatable("screen.rancher_companion.breed.pair"), why, false);
    }

    /** a breeding in progress: the bar on the wood beside Cancel, no text (Shane, 2026-11-26); a few pixels filled from the
     *  start, so it reads as a meter at once */
    private void breedingBar(GuiGraphics g, Font font, int mx, int my) {
        int L = s.getGuiLeft(), T = s.getGuiTop(), x = L + 10, y = T + HatcheryScreen.BAR_Y + 5, w = PAIR_X - 18, h = 6;
        float p = progress();
        g.fill(x, y, x + w, y + h, 0xFF170E1B);
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xFF5D636E);
        g.fill(x + 1, y + 1, x + 1 + Math.max(3, Math.round((w - 2) * p)), y + h - 1, HatcheryScreen.BREEDING);
        s.bar(g, mx, my, PAIR_X, Component.translatable("screen.rancher_companion.breed.cancel"), null, true);
    }

    private void parent(GuiGraphics g, Font font, Row r, int x, boolean active, int mx, int my) {
        int L = s.getGuiLeft(), T = s.getGuiTop(), cx = L + x + P_W / 2;
        boolean hover = Art.in(mx, my, L + x, T + P_Y, P_W, P_H);
        if (active || hover) g.renderOutline(L + x, T + P_Y, P_W, P_H, active ? Art.GLOW : 0xFF8A5A92);
        if (r == null) {
            for (int i = 0; i < 2; i++) {
                Component t = Component.translatable("screen.rancher_companion.breed.pick." + i);
                g.drawString(font, t, cx - font.width(t) / 2, T + P_Y + 26 + i * 10, Art.DIM, false);
            }
            return;
        }
        s.model(g, r.id() + "/big", r.stage(), r.line(), r.horn(), r.hat(), r.ghost(), L + x + 2, T + P_Y + 2, P_W - 4, 52, 16);
        String name = font.plainSubstrByWidth(r.name().getString(), P_W - 4);
        g.drawString(font, name, cx - font.width(name) / 2, T + P_Y + 56, Art.THREAD, false);
        if (hover) s.tip = s.view.breeding() ? List.of(r.name(), Component.literal(Lineage.of(r.line()).name()).withStyle(ChatFormatting.GRAY))
            : List.of(r.name(), Component.literal(Lineage.of(r.line()).name()).withStyle(ChatFormatting.GRAY),
                Component.translatable("screen.rancher_companion.breed.change").withStyle(ChatFormatting.DARK_GRAY));
    }

    private void picker(GuiGraphics g, Font font, int mx, int my) {
        int L = s.getGuiLeft(), T = s.getGuiTop();
        g.drawString(font, Component.translatable("screen.rancher_companion.breed.picking"), L + C_X + 4, T + 25, Art.THREAD, false);
        List<Row> rows = choices();
        if (rows.isEmpty()) {
            var lines = font.split(Component.translatable("screen.rancher_companion.breed.none_fit"), C_W - 10);
            for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), L + C_X + 5, T + HatcheryScreen.LIST_Y + 4 + i * 10, Art.DIM, false);
            return;
        }
        for (int i = 0; i < HatcheryScreen.ROWS && i + scroll < rows.size(); i++) {
            Row r = rows.get(i + scroll);
            int x = L + PICK_X, y = T + HatcheryScreen.LIST_Y + i * HatcheryScreen.ROW_H;
            s.rowBox(g, x, y, PICK_W, false, Art.in(mx, my, x, y, PICK_W, HatcheryScreen.ROW_H - 1));
            s.portrait(g, r, x + 1, y);
            g.drawString(font, font.plainSubstrByWidth(r.name().getString(), PICK_W - 24), x + 22, y + 2, Art.THREAD, false);
            g.drawString(font, font.plainSubstrByWidth(Lineage.of(r.line()).name(), PICK_W - 24), x + 22, y + 11, Art.DIM, false);
        }
        if (rows.size() > HatcheryScreen.ROWS) {
            g.fill(L + 219, T + 24, L + 221, T + 118, 0xFF170E1B);
            s.knob(g, L + 217, T + HatcheryScreen.LIST_Y, rows.size(), scroll);
        }
    }

    private void preview(GuiGraphics g, Font font, int mx, int my) {
        int L = s.getGuiLeft(), T = s.getGuiTop(), x = L + C_X + 4;
        Row ra = s.row(a), rb = s.row(b);
        g.drawString(font, Component.translatable("screen.rancher_companion.breed.child"), x, T + 25, Art.THREAD, false);
        if (ra == null || rb == null) {
            var lines = font.split(Component.translatable(fit().size() < 2 ? "screen.rancher_companion.breed.need_two" : "screen.rancher_companion.breed.pick_two"), C_W - 10);
            for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x + 1, T + 40 + i * 10, Art.DIM, false);
            return;
        }
        boolean ghostPair = ra.ghost() || rb.ghost();  /* always a Bakeneko */
        boolean sameRare = !ghostPair && ra.line() == rb.line() && Breeding.family(ra.line()) < 0;  /* two of a rare line: that line */
        boolean same = ghostPair || sameRare || Breeding.sameLine(ra.line(), rb.line());
        int tier = Breeding.childTier(ra.line(), rb.line());
        long t = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime() / 20;  /* a mixed pairing: every bloodline in turn, a second each */
        int line = ghostPair ? dev.frosthaven.ranchercompanion.look.Lineage.BAKENEKO : sameRare ? ra.line() : same ? Breeding.family(ra.line()) * Breeding.TIERS + tier : (int) (t % Breeding.FAMILIES) * Breeding.TIERS + tier;
        int horn = ghostPair ? (int) (t % Breeding.HORNS) : ra.horn() == rb.horn() && same ? ra.horn() : same ? (t % 2 == 0 ? ra.horn() : rb.horn()) : (int) (t % Breeding.HORNS);
        boolean seen = known(line);
        if (seen) s.model(g, "child", 1, line, horn, 0, L + C_X + 34, T + 34, 30, 30, 34);
        else g.blit(HatcheryScreen.SPR, L + C_X + 35, T + 35, UNKNOWN_U, UNKNOWN_V, 28, 28);  /* a coat its owner never had: the "?" badge */
        lastKnown = seen;
        Component name = !same ? Component.translatable("screen.rancher_companion.breed.any_line", Lineage.TIER_NAMES[tier])
            : seen ? Component.literal(Lineage.of(line).name()) : Component.translatable("screen.rancher_companion.breed.unknown_coat");
        g.drawString(font, name, x, T + 67, seen || !same ? Art.THREAD : Art.DIM, false);
        String horns = ghostPair ? "random" : same ? ra.horn() == rb.horn() ? "same" : "either" : "random";
        g.drawString(font, Component.translatable("screen.rancher_companion.breed.horns." + horns), x, T + 77, Art.DIM, false);
        eyesLine(g, font, ra, rb, x, T + 87, mx, my);
        lastOdds = Breeding.odds(ra.traits(), rb.traits(), ra.band(), rb.band(), heirloom() ? keep : null);  /* not shown (Shane, 2026-11-26: the odds are known); the self test reads them */
        keepLine(g, font, x, T + TRAITS_Y, mx, my);
    }

    /** "Eyes:" and each astral set the child may get, as eye icons: "/" between choices, a pair as one split eye; the names
     *  on hover. With the Starlight Tonic in the slot (or in this breeding), pairs */
    private void eyesLine(GuiGraphics g, Font font, Row ra, Row rb, int x, int y, int mx, int my) {
        boolean twin = s.view.breeding() ? s.view.breedTwin() : dev.frosthaven.ranchercompanion.ranch.Pairing.starlight(s.getMenu().tonic());
        List<int[]> sets = Breeding.possibleEyes(Breeding.set(ra.astral(), ra.astral2()), Breeding.set(rb.astral(), rb.astral2()), twin);
        Component label = Component.translatable("screen.rancher_companion.breed.eyes");
        g.drawString(font, label, x, y, Art.DIM, false);
        int ex = x + font.width(label) + 2, start = ex;
        lastEyes = sets.stream().map(e -> e.length == 1 ? String.valueOf(e[0]) : e[0] + "+" + e[1]).collect(java.util.stream.Collectors.joining("/"));
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable(sets.size() > 1 ? "screen.rancher_companion.breed.eyes_tip" : "screen.rancher_companion.breed.eyes_tip1").withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < sets.size(); i++) {
            if (i > 0) { g.drawString(font, "/", ex, y, Art.DIM, false); ex += font.width("/") + 1; }
            int[] e = sets.get(i);
            Art.eye(g, ex, y, e[0], e.length > 1 ? e[1] : 0);  /* a pair: one split eye, as the drekki screen draws it */
            ex += Art.EYE_W + 2;
            MutableName name = new MutableName();
            for (int k : e) name.add(k);
            tip.add(name.component());
        }
        if (Art.in(mx, my, start - 2, y - 1, ex - start + 2, 10)) s.tip = tip;
    }

    /** "Solar" or "Solar + Lunar" in the eye colors */
    private static final class MutableName {
        private final net.minecraft.network.chat.MutableComponent c = Component.empty();
        private int n;
        void add(int astral) {
            if (n++ > 0) c.append(Component.literal(" + ").withStyle(ChatFormatting.GRAY));
            c.append(Component.literal(dev.frosthaven.ranchercompanion.look.Astral.NAMES[astral]).withColor(dev.frosthaven.ranchercompanion.look.Astral.iris(astral)));
        }
        Component component() { return c; }
    }

    private boolean heirloom() {
        return s.view.breeding() ? false : dev.frosthaven.ranchercompanion.ranch.Pairing.heirloom(s.getMenu().tonic());
    }

    /** every trait the two parents hold, without repeats */
    private List<String> union() {
        Row ra = s.row(a), rb = s.row(b);
        List<String> out = new ArrayList<>();
        if (ra != null) out.addAll(ra.traits());
        if (rb != null) for (String t : rb.traits()) if (!out.contains(t)) out.add(t);
        return out;
    }

    /** with the Heirloom Tonic in the slot: "Keep:", and on the next line the picked trait with its own tooltip (the gear picks it) */
    /** the tonic was taken out or swapped: its settings reset (the kept trait, an open trait list) */
    private void tonicChanged() {
        var now = s.getMenu().tonic().isEmpty() ? null : s.getMenu().tonic().getItem();
        if (now != lastTonic) { keep = null; if (picking == 3) picking = 0; traitScroll = 0; }
        lastTonic = now;
    }

    /** why the gear (the active tonic's settings) is off, or null. Each tonic brings its own settings (Shane, 2026-11-26: the
     *  gear serves the other tonics in their own ways too); so far only the Heirloom Tonic has some, its trait to keep,
     *  which needs both parents picked. A new tonic's settings go here and in the gear's click */
    private Component gearWhy() {
        if (s.view.breeding()) return Component.translatable("screen.rancher_companion.breed.gear_busy");
        if (s.getMenu().tonic().isEmpty()) return Component.translatable("screen.rancher_companion.breed.gear_none");
        if (!heirloom()) return Component.translatable("screen.rancher_companion.breed.gear_nothing");
        if (a == null || b == null) return Component.translatable("screen.rancher_companion.breed.pick_two");
        return null;
    }

    private void gearButton(GuiGraphics g, int mx, int my) {
        int x = s.getGuiLeft() + GEAR_X, y = s.getGuiTop() + GEAR_Y;
        Component why = gearWhy();
        boolean hover = Art.in(mx, my, x, y, GEAR_W, Art.BTN_H);
        Art.button(g, HatcheryScreen.SPR, Minecraft.getInstance().font, x, y, GEAR_W, why != null ? Art.Btn.DISABLED : hover || picking == 3 ? Art.Btn.HOVER : Art.Btn.NORMAL, Component.empty());
        Art.gear(g, x + 3, y + 4, why != null ? 0xFF8D939E : Art.THREAD);
        if (hover) s.tip = List.of(why != null ? why : Component.translatable("screen.rancher_companion.breed.gear"));
    }

    private void keepLine(GuiGraphics g, Font font, int x, int y, int mx, int my) {
        if (!heirloom()) return;
        boolean set = union().contains(keep);
        g.drawString(font, Component.translatable("screen.rancher_companion.breed.keep"), x, y, Art.THREAD, false);
        String name = set ? Component.translatable("trait.rancher_companion." + keep).getString() : Component.translatable("screen.rancher_companion.breed.keep_pick").getString();
        g.drawString(font, font.plainSubstrByWidth(name, C_W - 8), x, y + 10, set ? 0xFF000000 | Trait.color(keep) : Art.WARN, false);
        if (Art.in(mx, my, x - 2, y + 9, C_W - 4, 10))
            s.tip = set ? dev.frosthaven.ranchercompanion.trait.Tips.trait(keep, 0, false) : List.of(Component.translatable("screen.rancher_companion.breed.keep_tip").withStyle(ChatFormatting.GRAY));
    }

    /** the parents' traits, one a row, to pick the one the Heirloom Tonic keeps */
    private void traitPicker(GuiGraphics g, Font font, int mx, int my) {
        int L = s.getGuiLeft(), T = s.getGuiTop();
        g.drawString(font, Component.translatable("screen.rancher_companion.breed.keep_title"), L + C_X + 4, T + 25, Art.THREAD, false);
        List<String> ts = union();
        for (int i = 0; i < TRAIT_ROWS && i + traitScroll < ts.size(); i++) {
            String id = ts.get(i + traitScroll);
            int y = T + TRAIT_LIST_Y + i * TRAIT_H;
            boolean hover = Art.in(mx, my, L + PICK_X, y - 1, PICK_W, TRAIT_H);
            if (hover || id.equals(keep)) g.fill(L + PICK_X, y - 1, L + PICK_X + PICK_W, y + TRAIT_H - 1, id.equals(keep) ? 0x664A2A52 : 0x44FFFFFF);
            String n = Component.translatable("trait.rancher_companion." + id).getString();
            g.drawString(font, font.plainSubstrByWidth(n, PICK_W - 4), L + PICK_X + 2, y, 0xFF000000 | Trait.color(id), false);
        }
        if (ts.size() > TRAIT_ROWS) {  /* the scroll track and knob, as the roster's */
            g.fill(L + 219, T + 24, L + 221, T + 118, 0xFF170E1B);
            int track = TRAIT_ROWS * TRAIT_H - 15;
            g.blit(HatcheryScreen.SPR, L + 217, T + TRAIT_LIST_Y - 1 + track * traitScroll / (ts.size() - TRAIT_ROWS), 144, 220, 6, 15);
        }
    }

    boolean click(double mx, double my) {
        int L = s.getGuiLeft(), T = s.getGuiTop();
        if (s.view.breeding()) {  /* the parents are set while they breed; only Cancel */
            if (Art.in(mx, my, L + PAIR_X, T + HatcheryScreen.BAR_Y, HatcheryScreen.BW, Art.BTN_H)) { s.send(Ranch.Action.CANCEL_PAIR, null, null); return true; }
            return false;
        }
        if (Art.in(mx, my, L + A_X, T + P_Y, P_W, P_H)) { picking = picking == 1 ? 0 : 1; scroll = 0; HatcheryScreen.click(); return true; }
        if (Art.in(mx, my, L + B_X, T + P_Y, P_W, P_H)) { picking = picking == 2 ? 0 : 2; scroll = 0; HatcheryScreen.click(); return true; }
        if (picking == 3) {
            List<String> ts = union();
            for (int i = 0; i < TRAIT_ROWS && i + traitScroll < ts.size(); i++)
                if (Art.in(mx, my, L + PICK_X, T + TRAIT_LIST_Y + i * TRAIT_H - 1, PICK_W, TRAIT_H)) { keep = ts.get(i + traitScroll); picking = 0; HatcheryScreen.click(); return true; }
            if (!Art.in(mx, my, L + GEAR_X, T + GEAR_Y, GEAR_W, Art.BTN_H)) { picking = 0; return true; }
        }
        if (Art.in(mx, my, L + GEAR_X, T + GEAR_Y, GEAR_W, Art.BTN_H) && gearWhy() == null) { picking = picking == 3 ? 0 : 3; traitScroll = 0; HatcheryScreen.click(); return true; }
        if (picking != 0) {
            List<Row> rows = choices();
            for (int i = 0; i < HatcheryScreen.ROWS && i + scroll < rows.size(); i++)
                if (Art.in(mx, my, L + PICK_X, T + HatcheryScreen.LIST_Y + i * HatcheryScreen.ROW_H, PICK_W, HatcheryScreen.ROW_H - 1)) {
                    UUID id = rows.get(i + scroll).id();
                    if (picking == 1) a = id; else b = id;
                    picking = 0;
                    HatcheryScreen.click();
                    return true;
                }
            if (Art.in(mx, my, L + C_X, T + P_Y, C_W, 98)) return true;
        }
        if (Art.in(mx, my, L + PAIR_X, T + HatcheryScreen.BAR_Y, HatcheryScreen.BW, Art.BTN_H) && pairWhy() == null) {
            s.send(Ranch.Action.PAIR, a, b, heirloom() ? keep : "");
            return true;
        }
        return false;
    }

    boolean scroll(double dy) {
        if (picking == 3) { traitScroll = Math.max(0, Math.min(traitScroll - (int) Math.signum(dy), Math.max(0, union().size() - TRAIT_ROWS))); return true; }
        if (picking == 0) return false;
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(dy), Math.max(0, choices().size() - HatcheryScreen.ROWS)));
        return true;
    }

    // ---- for the self test ----

    UUID parent(int i) { return i == 0 ? a : b; }

    int pickScroll() { return picking == 3 ? traitScroll : scroll; }

    List<String> choiceNames() { return choices().stream().map(r -> r.name().getString()).toList(); }
}
