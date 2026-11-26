package dev.frosthaven.ranchercompanion.client;

import static dev.frosthaven.ranchercompanion.client.SelfTest.cmd;
import static dev.frosthaven.ranchercompanion.client.SelfTest.mc;
import static dev.frosthaven.ranchercompanion.client.SelfTest.quiet;
import static dev.frosthaven.ranchercompanion.client.SelfTest.step;
import static dev.frosthaven.ranchercompanion.client.SelfTest.useOn;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen;
import dev.frosthaven.ranchercompanion.client.screen.HatcheryScreen;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.CreativeEggItem;
import dev.frosthaven.ranchercompanion.item.DrekkiData;
import dev.frosthaven.ranchercompanion.look.Astral;
import dev.frosthaven.ranchercompanion.net.HatcheryView.Row;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.ranch.Roster;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * The hatchery's Roster and Breeding tabs in the real game (with -Drancher_companion.selftest.breed=true; spec, Breeding:
 * Pairing): seven Mature drekki and a Fledgling at the hatchery, one of the Matures resting; the picker lists only the
 * six fit ones and scrolls; an unowned coat (Rose pure) previews as the "?" badge, a muted one always shows; the preview for a same-line and a mixed pairing with the trait odds on hover; Pair starts a day of
 * breeding ("Breeding" in the roster, no check out), the bar half full half a day on, Cancel frees both; Pair again and
 * a day later the egg is in the nest; the roster's "Resting 3d" and the refused check out; the bred egg's tooltip; a day later "Resting 2d"; the hatch and the child's
 * sleeping traits. Each screen holds a few seconds for the watcher, with Dark Mode Everywhere off and then on when the
 * game has it.
 */
final class SelfTestBreed {
    private SelfTestBreed() {}

    private static final String[] NAMES = {"Ashford", "Bramble", "Cinder", "Dune", "Pip", "Ember", "Fern", "Gale"};
    private static final String[] FIVE = {"hearty", "night_watch", "open_road", "downstream", "duck_cover"};

    private static HatcheryScreen hs() { return mc().screen instanceof HatcheryScreen s ? s : null; }

    private static Row named(String n) {
        HatcheryScreen s = hs();
        if (s == null) return null;
        for (Row r : s.view().rows()) if (r.name().getString().equals(n)) return r;
        return null;
    }

    /** Dark Mode Everywhere's shade (0 off, 1.. its dark shades), when the game has it */
    private static void dme(int i) {
        try {
            Class<?> c = Class.forName("com.buuz135.darkmodeeverywhere.ClientProxy");
            List<?> values = (List<?>) c.getField("SHADER_VALUES").get(null);
            if (values == null || values.isEmpty()) return;
            c.getField("SELECTED_SHADER_VALUE").set(null, values.get(Math.min(i, values.size() - 1)));
            RancherCompanion.LOG.info("[selftest] dark mode shade {}", i);
        } catch (ReflectiveOperationException | LinkageError e) {
            RancherCompanion.LOG.info("[selftest] no Dark Mode Everywhere: {}", e.toString());
        }
    }

    /** picks the roster row of this name */
    private static void row(String n) {
        for (int i = 0; i < hs().view().rows().size(); i++) if (hs().view().rows().get(i).name().getString().equals(n)) hs().press("row" + i);
    }

    private static void hold(String n) { quiet(n + "_hold", 100, () -> {}); }

    static void steps(BlockPos hatch) {
        quiet("breed_setup", 60, () -> {
            SelfTest.close();
            cmd("tp @s " + (hatch.getX() - 2.5) + " " + hatch.getY() + " " + (hatch.getZ() + 0.5) + " -90 30");  /* within reach of the hatchery */
            var server = mc().getSingleplayerServer();
            java.util.UUID me = mc().player.getUUID();
            if (server != null) server.execute(() -> {
                var p = server.getPlayerList().getPlayer(me);
                Roster r = Ranch.roster(server);
                Object[][] kin = {{8 + 1, 2, FIVE}, {8 + 2, 2, FIVE}, {32 + 3, 0, new String[]{"duck_cover"}}, {12, 1, new String[]{}}, {0, 3, new String[]{}}, {8 + 2, 2, new String[]{"hearty"}}, {0, 1, new String[]{}}, {16, 0, new String[]{}}};
                for (int i = 0; i < kin.length; i++) {
                    Ranch.putEgg(p, hatch, CreativeEggItem.egg(4));
                    Ranch.hatch(p, hatch);
                    var mine = r.of(me);
                    Roster.Entry e = mine.get(mine.size() - 1);
                    DrekkiEntity d = Ranch.liveOf(e.id);
                    if (d == null) { RancherCompanion.LOG.warn("[selftest] no live drekki for {}", NAMES[i]); continue; }
                    d.setCustomName(Component.literal(NAMES[i]));
                    d.setLook(i == 4 ? 2 : 3, (Integer) kin[i][0], 1, i == 2 ? Astral.LUNAR : Astral.SOLAR, (Integer) kin[i][1]);
                    d.applyStageHealth(true);
                    d.life.traits.clear();
                    d.life.traits.addAll(List.of((String[]) kin[i][2]));
                    d.syncLife();
                    Ranch.touch(d);
                    if (i == 3) r.rest(e, Roster.now(server));  /* Dune paired yesterday */
                }
                p.closeContainer();
            });
        });
        quiet("breed_close", 10, () -> { SelfTest.close(); dme(0); });
        step("breed_roster", 30, () -> useOn(hatch, Direction.NORTH), () -> hs() != null && named("Dune") != null && named("Dune").rest() == 3);
        hold("breed_roster");
        step("breed_tab", 15, () -> hs().press("tab_breeding"), () -> hs() != null && hs().getMenu().breeding);
        hold("breed_tab");
        step("breed_picker_lists_only_fit", 15, () -> hs().press("parent0"),
            () -> hs() != null && hs().pickerNames().equals(List.of("Ashford", "Bramble", "Cinder", "Ember", "Fern", "Gale")));
        hold("breed_picker");
        /* six to pick from, four rows: the list scrolls (the knob moves down) */
        step("breed_picker_scrolls", 15, () -> { hs().mouseScrolled(hs().getGuiLeft() + 160, hs().getGuiTop() + 60, 0, -1); hs().mouseScrolled(hs().getGuiLeft() + 160, hs().getGuiTop() + 60, 0, -1); },
            () -> hs().pickScroll() == 2);
        hold("breed_picker_scrolled");
        step("breed_picker_scroll_back", 15, () -> { hs().mouseScrolled(hs().getGuiLeft() + 160, hs().getGuiTop() + 60, 0, 1); hs().mouseScrolled(hs().getGuiLeft() + 160, hs().getGuiTop() + 60, 0, 1); },
            () -> hs().pickScroll() == 0);
        step("breed_pick_a", 15, () -> hs().press("pick0"), () -> hs().parentName(0).equals("Ashford"));
        step("breed_pick_b", 15, () -> { hs().press("parent1"); hs().press("pick0"); }, () -> hs().parentName(1).equals("Bramble"));
        hold("breed_preview_same");
        /* no Traits line (the odds are known); the odds still follow the pair's mood (the average of both bands; kinship can
           lift them above Content), here for a trait both hold */
        step("breed_mood_odds", 5, () -> {}, () -> {
            double p = 1 / 3.0 + dev.frosthaven.ranchercompanion.life.Breeding.moodScore(named("Ashford").band(), named("Bramble").band());
            RancherCompanion.LOG.info("[selftest] moods {} {}: a shared trait at {}", named("Ashford").band(), named("Bramble").band(), hs().previewOdds().get("hearty"));
            return named("Ashford").band() >= 2 && Math.abs(hs().previewOdds().getOrDefault("hearty", 0.0) - (1 - (1 - p) * (1 - p))) < 1e-6;
        });
        step("breed_mixed", 15, () -> { HatcheryScreen.testMouse = new int[]{2, 2}; hs().press("parent1"); hs().press("pick1"); },
            () -> hs().parentName(1).equals("Cinder"));
        hold("breed_preview_mixed");
        quiet("breed_dark", 10, () -> dme(1));
        hold("breed_preview_mixed_dark");
        quiet("breed_light", 10, () -> dme(0));
        step("breed_mixed_muted_shows", 5, () -> {}, () -> hs().previewKnown());
        /* Ashford (Solar) and Cinder (Lunar): one of the two; with the Starlight Tonic in the slot, one eye of each */
        step("breed_eyes_one_of", 5, () -> {}, () -> hs().previewEyes().equals("1/2"));
        quiet("breed_tonic_give", 10, () -> SelfTest.hotbar(0, "rancher_companion:starlight_tonic"));
        step("breed_tonic_in_slot", 20, () -> SelfTest.shiftClick(28),
            () -> !hs().getMenu().tonic().isEmpty() && hs().previewEyes().equals("1+2"));
        step("breed_tonic_tooltip", 15, () -> HatcheryScreen.testMouse = hs().spot("eyes"),
            () -> hs().shownTip() != null && hs().shownTip().stream().anyMatch(c -> c.getString().equals("Solar + Lunar")));
        hold("breed_tonic_pair");
        step("breed_tonic_out", 20, () -> { HatcheryScreen.testMouse = new int[]{2, 2}; SelfTest.shiftClick(0); },
            () -> hs().getMenu().tonic().isEmpty() && hs().previewEyes().equals("1/2"));
        /* the Heirloom Tonic: pick a trait (Ashford's Night Watch); its odds read 100% */
        quiet("breed_heirloom_give", 10, () -> SelfTest.hotbar(1, "rancher_companion:heirloom_tonic"));
        step("breed_heirloom_in_slot", 20, () -> SelfTest.shiftClick(29), () -> !hs().getMenu().tonic().isEmpty() && hs().keepTrait() == null);
        hold("breed_heirloom_keep_line");
        step("breed_heirloom_gear", 15, () -> hs().press("gear"), () -> hs().keepPickerOpen());
        hold("breed_heirloom_picker");
        step("breed_heirloom_picked", 15, () -> hs().press("trait1"),
            () -> "night_watch".equals(hs().keepTrait()) && hs().previewOdds().getOrDefault("night_watch", 0.0) == 1.0);
        step("breed_heirloom_kept_tooltip", 15, () -> HatcheryScreen.testMouse = hs().spot("kept"),
            () -> hs().shownTip() != null && hs().shownTip().stream().anyMatch(c -> c.getString().equals("Night Watch")));
        hold("breed_heirloom_kept");
        step("breed_heirloom_out_resets", 20, () -> { HatcheryScreen.testMouse = new int[]{2, 2}; SelfTest.shiftClick(0); },
            () -> hs().getMenu().tonic().isEmpty() && hs().keepTrait() == null && !hs().keepPickerOpen());
        /* Bramble and Ember, both Rose vivid: a Rose pure child, a coat this player never had: the "?" badge */
        step("breed_unknown_coat", 15, () -> { hs().press("parent0"); hs().press("pick2"); hs().press("parent1"); hs().press("pick1"); },
            () -> hs().parentName(0).equals("Ember") && hs().parentName(1).equals("Bramble") && !hs().previewKnown());
        hold("breed_unknown_coat");
        step("breed_back_to_same", 15, () -> { hs().press("parent0"); hs().press("pick0"); },
            () -> hs().parentName(0).equals("Ashford") && hs().parentName(1).equals("Bramble") && hs().previewKnown());
        step("breed_pair_starts_breeding", 30, () -> hs().press("pair"),
            () -> hs() != null && hs().view().breeding() && named("Ashford").breeding() && named("Bramble").breeding() && !hs().view().egg().has(DrekkiData.BRED.get()));
        hold("breed_breeding");
        step("breed_roster_breeding", 15, () -> hs().press("tab_roster"), () -> !hs().getMenu().breeding);
        hold("breed_roster_breeding");
        step("breed_breeding_no_checkout", 30, () -> { row("Ashford"); hs().press("out"); },
            () -> named("Ashford") != null && named("Ashford").where() == dev.frosthaven.ranchercompanion.net.HatcheryView.HERE);
        quiet("breed_half_day", 30, () -> { SelfTest.close(); cmd("time add 12000"); });
        step("breed_half_way", 30, () -> { useOn(hatch, Direction.NORTH); },
            () -> hs() != null && hs().view().breeding());
        step("breed_bar_half", 15, () -> hs().press("tab_breeding"),
            () -> hs().getMenu().breeding && hs().breedProgress() > 0.45f && hs().breedProgress() < 0.55f);
        hold("breed_bar_half");
        step("breed_cancel", 30, () -> hs().press("cancel"),
            () -> !hs().view().breeding() && named("Ashford").fit() && named("Bramble").fit() && named("Ashford").rest() == 0);
        hold("breed_cancelled");
        step("breed_pair_again", 30, () -> { hs().press("parent0"); hs().press("pick0"); hs().press("parent1"); hs().press("pick0"); hs().press("pair"); },
            () -> hs().view().breeding() && named("Ashford").breeding());
        /* the hatchery from outside while they breed: magenta swirls rise from its base */
        quiet("breed_particles_look", 20, () -> { SelfTest.close(); mc().options.hideGui = true;
            cmd("tp @s " + (hatch.getX() - 2.5) + " " + (hatch.getY() + 0.2) + " " + (hatch.getZ() + 0.5) + " facing " + (hatch.getX() + 0.5) + " " + (hatch.getY() + 0.4) + " " + (hatch.getZ() + 0.5)); });
        step("breed_particles", 40, () -> {}, () -> mc().level.getBlockEntity(hatch) instanceof dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity be && be.breeding());
        hold("breed_particles");
        quiet("breed_particles_done", 10, () -> { mc().options.hideGui = false;
            cmd("tp @s " + (hatch.getX() - 2.5) + " " + hatch.getY() + " " + (hatch.getZ() + 0.5) + " -90 30"); });
        quiet("breed_full_day", 30, () -> { SelfTest.close(); cmd("time add 24100"); });
        step("breed_egg_laid", 60, () -> useOn(hatch, Direction.NORTH),
            () -> hs() != null && !hs().view().breeding() && hs().view().egg().has(DrekkiData.BRED.get()) && named("Ashford").rest() == 3 && named("Bramble").rest() == 3);
        hold("breed_egg_laid");
        step("breed_roster_resting", 15, () -> hs().press("tab_roster"), () -> !hs().getMenu().breeding);
        quiet("breed_roster_dark", 10, () -> dme(1));
        hold("breed_roster_resting_dark");
        quiet("breed_roster_light", 10, () -> dme(0));
        step("breed_resting_no_checkout", 30, () -> { row("Ashford"); hs().press("out"); },
            () -> named("Ashford") != null && named("Ashford").where() == dev.frosthaven.ranchercompanion.net.HatcheryView.HERE);
        step("breed_egg_tooltip", 15, () -> HatcheryScreen.testMouse = hs().eggCenter(),
            () -> hs().shownTip() != null && hs().shownTip().stream().anyMatch(c -> c.getString().contains("Bred from Ashford and Bramble")));
        hold("breed_egg_tooltip");
        quiet("breed_day", 30, () -> { HatcheryScreen.testMouse = null; SelfTest.close(); cmd("time add 24000"); });
        step("breed_rest_counts_down", 30, () -> useOn(hatch, Direction.NORTH), () -> named("Ashford") != null && named("Ashford").rest() == 2);
        hold("breed_rest_2d");
        quiet("breed_days", 30, () -> { SelfTest.close(); cmd("time add 48500"); });
        quiet("breed_known", 5, () -> { known.clear(); var srv = mc().getSingleplayerServer(); if (srv != null) for (var e : Ranch.roster(srv).of(mc().player.getUUID())) known.add(e.id); });
        step("breed_hatch", 40, () -> useOn(hatch, Direction.UP), () -> hs() != null && hs().view().rows().size() == 10);
        quiet("breed_hatch_close", 15, () -> SelfTest.close());
        step("breed_child_screen", 30, () -> {
            DrekkiEntity c = child();
            if (c == null) return;
            cmd("tp @s " + (c.getX() + 1.2) + " " + c.getY() + " " + (c.getZ() + 1.2));
            mc().gameMode.interact(mc().player, c, net.minecraft.world.InteractionHand.MAIN_HAND);
        }, () -> mc().screen instanceof DrekkiScreen);
        step("breed_child_traits", 20, () -> {
            DrekkiScreen d = (DrekkiScreen) mc().screen;
            int[] t = d.spot("tab_traits");
            if (t != null) d.mouseClicked(t[0], t[1], 0);
            DrekkiScreen.testMouse = new int[]{2, 2};
        }, () -> {
            DrekkiEntity c = serverChild();
            if (c == null) return false;
            RancherCompanion.LOG.info("[selftest] child: {} horn {} astral {} traits {}", c.lineage().name(), c.horn(), c.astral(), c.life.traits);
            /* Ashford (Rose clear) and Bramble (Rose vivid), horn 2 both, both Solar, the same five traits: Rose vivid, horn 2, Solar eyes,
               every inherited trait kept */
            return c.astral() == Astral.SOLAR && c.astral2() == 0 && c.stage() == 1 && c.line() == 2 * 4 + 2 && c.horn() == 2
                && mc().screen instanceof DrekkiScreen ds && ds.getMenu().drekki == child();
        });
        hold("breed_child_traits");
        twoEyes(hatch);
        quiet("breed_end", 10, () -> { DrekkiScreen.testMouse = null; SelfTest.close(); });
    }

    /** Ember made two-eyed with the widest pair (Underworld and Lunar), held still in the open: the camera on its left eye,
     *  then its right, the HUD hidden; then its screen's Details tab with both astrals fitting */
    private static void twoEyes(BlockPos hatch) {
        BlockPos spot = hatch.offset(-9, 0, -6);
        quiet("eyes_setup", 30, () -> {
            SelfTest.close();
            cmd("time set 6000");
            var srv = mc().getSingleplayerServer();
            java.util.UUID me = mc().player.getUUID();
            if (srv != null) srv.execute(() -> {
                for (var e : Ranch.roster(srv).of(me)) {
                    DrekkiEntity d = Ranch.liveOf(e.id);
                    if (d == null || !d.hasCustomName() || !d.getCustomName().getString().equals("Ember")) continue;
                    d.setLook(3, d.line(), d.diet(), Astral.UNDERWORLD, d.horn());
                    d.setAstral2(Astral.LUNAR);
                    d.applyStageHealth(true); d.setHealth(d.getMaxHealth());
                    d.setNoAi(true);
                    d.setSleepingLoop(false);
                    d.setAnim(dev.frosthaven.ranchercompanion.look.Anim.IDLE, 0);  /* standing still, as the showcase holds them */
                    d.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
                    d.setYHeadRot(0); d.setYBodyRot(0);
                    Ranch.touch(d);
                }
            });
        });
        quiet("eyes_hud_off", 10, () -> { mc().options.hideGui = true; cmd("gamemode spectator"); });  /* the camera holds still in the air */
        /* facing south (yaw 0): its left is east (+x), its right west; the camera level with its head, a little in front of it */
        step("eyes_left_side", 40, () -> headShot(1),
            () -> ember() != null && ember().astral() == Astral.UNDERWORLD && ember().astral2() == Astral.LUNAR && !ember().isSleepingLoop());
        hold("eyes_left_side");
        step("eyes_right_side", 40, () -> headShot(-1), () -> ember() != null);
        hold("eyes_right_side");
        quiet("eyes_hud_on", 10, () -> { mc().options.hideGui = false; cmd("gamemode creative"); });
        step("eyes_screen", 30, () -> { cmd("tp @s " + (spot.getX() + 2.5) + " " + spot.getY() + " " + (spot.getZ() + 0.5) + " 90 10");
                if (ember() != null) mc().gameMode.interact(mc().player, ember(), net.minecraft.world.InteractionHand.MAIN_HAND); },
            () -> mc().screen instanceof DrekkiScreen ds && ds.getMenu().drekki == ember());
        step("eyes_screen_astral", 15, () -> { DrekkiScreen d = (DrekkiScreen) mc().screen; DrekkiScreen.testMouse = d.spot("astral"); },
            () -> mc().screen instanceof DrekkiScreen d && d.shownTip() != null && d.shownTip().stream().anyMatch(c -> c.getString().equals("Lunar")) && d.shownTip().stream().anyMatch(c -> c.getString().equals("Underworld"))
                && d.shownTip().get(d.shownTip().size() - 1).getString().startsWith("At home"));
        hold("eyes_screen_astral");
        quiet("eyes_screen_plain", 5, () -> DrekkiScreen.testMouse = new int[]{2, 2});
        hold("eyes_screen_plain");
    }

    /** the camera beside Ember's head on one side (1 east, -1 west), facing it: its eye fills the middle of the view */
    private static void headShot(int side) {
        DrekkiEntity d = ember();
        if (d == null) return;
        /* measured from the last shots: the model's head sits about 1.2 below the eye height the hitbox gives, and further forward */
        var head = d.getEyePosition().add(0, -1.2, d.getBbWidth() * 0.45 + 0.3);
        double cx = head.x + side * 2.6, cz = head.z, cy = head.y - 1.62;
        cmd("tp @s " + cx + " " + cy + " " + cz + " facing " + head.x + " " + head.y + " " + head.z);
    }

    private static DrekkiEntity ember() {
        for (var e : mc().level.entitiesForRendering())
            if (e instanceof DrekkiEntity d && d.hasCustomName() && d.getCustomName().getString().equals("Ember") && mc().player.getUUID().equals(d.owner())) return d;
        return null;
    }

    /** the roster ids before the bred hatch: the hatchling is the one not among them */
    private static final java.util.Set<java.util.UUID> known = new java.util.HashSet<>();

    /** the hatchling as the integrated server has it (its roster entry is the one that was not there before the hatch) */
    private static DrekkiEntity serverChild() {
        var srv = mc().getSingleplayerServer();
        if (srv == null) return null;
        for (var e : Ranch.roster(srv).of(mc().player.getUUID())) if (!known.contains(e.id)) return Ranch.liveOf(e.id);
        return null;
    }

    /** the hatchling's client entity */
    private static DrekkiEntity child() {
        DrekkiEntity s = serverChild();
        if (s == null) return null;
        for (var e : mc().level.entitiesForRendering()) if (e instanceof DrekkiEntity d && d.getUUID().equals(s.getUUID())) return d;
        return null;
    }
}
