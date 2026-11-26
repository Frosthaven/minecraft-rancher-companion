package dev.frosthaven.ranchercompanion.test;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.DrekkiBlocks;
import dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.CreativeEggItem;
import dev.frosthaven.ranchercompanion.item.DrekkiData;
import dev.frosthaven.ranchercompanion.life.Breeding;
import dev.frosthaven.ranchercompanion.look.Astral;
import dev.frosthaven.ranchercompanion.look.Incubation;
import dev.frosthaven.ranchercompanion.ranch.Pairing;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.ranch.Roster;
import dev.frosthaven.ranchercompanion.trait.Trait;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pairing at the hatchery (spec, Breeding: Pairing): two rested Mature drekki that live there breed for a day (both
 * "breeding": no check out, the nest kept), then a bred egg is in the nest and both rest 3 days (no second pairing, no
 * check out); cancel ends it with no egg and no rest; a full nest refuses; the bred egg hatches their child with Dormant
 * eyes and every inherited trait, past its cap too. The day passes by moving the breeding's start back (the world's
 * clock is shared with the other tests).
 */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class PairingTests {
    private PairingTests() {}

    private static final BlockPos H = new BlockPos(6, 1, 6);

    private static BlockPos stable(GameTestHelper t) {
        for (int x = 0; x < 13; x++) for (int z = 0; z < 13; z++) t.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        t.setBlock(H, DrekkiBlocks.HATCHERY.get());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) t.setBlock(H.offset(dx, 4, dz), Blocks.OAK_PLANKS);
        t.setBlock(H.offset(-3, 0, -3), Blocks.TORCH);
        return t.absolutePos(H);
    }

    private static ServerPlayer player(GameTestHelper t) {
        @SuppressWarnings("removal") ServerPlayer p = t.makeMockServerPlayerInLevel();
        p.moveTo(t.absoluteVec(H.offset(2, 0, 2).getCenter()));
        return p;
    }

    /** hatches a drekki and makes it a Mature of this line and horn with these traits */
    private static Roster.Entry mature(GameTestHelper t, ServerPlayer p, BlockPos h, int line, int horn, String... traits) {
        Roster r = Ranch.roster(t.getLevel().getServer());
        int before = r.of(p.getUUID()).size();
        Ranch.putEgg(p, h, CreativeEggItem.egg(4));
        Ranch.hatch(p, h);
        List<Roster.Entry> mine = r.of(p.getUUID());
        if (mine.size() != before + 1) throw new GameTestAssertException("the hatch failed: " + Ranch.hatchProblems(t.getLevel(), h));
        Roster.Entry e = mine.get(mine.size() - 1);
        DrekkiEntity d = Ranch.liveOf(e.id);
        d.setLook(3, line, 1, Astral.SOLAR, horn);
        d.life.traits.clear();
        d.life.traits.addAll(List.of(traits));
        Ranch.touch(d);
        return e;
    }

    private static HatcheryBlockEntity be(GameTestHelper t, BlockPos h) { return (HatcheryBlockEntity) t.getLevel().getBlockEntity(h); }

    /** a breeding's day is out: its start and the parents' clock times a day back (as if the day had passed), then the
     *  hatchery's check */
    private static void dayPasses(GameTestHelper t, BlockPos h, Roster.Entry a, Roster.Entry b) { timePasses(t, h, a, b, Pairing.DAY); }

    private static void timePasses(GameTestHelper t, BlockPos h, Roster.Entry a, Roster.Entry b, long ticks) {
        HatcheryBlockEntity be = be(t, h);
        be.startBreeding(be.breedA(), be.breedB(), be.breedStart() - ticks);
        for (Roster.Entry e : new Roster.Entry[]{a, b}) { e.breedUntil -= ticks; e.restUntil -= ticks; }
        Pairing.tick(t.getLevel(), h, be);
    }

    private static long now(GameTestHelper t) { return Roster.now(t.getLevel().getServer()); }

    @GameTest(template = "floor")
    public static void pairingLaysABredEggAndBothRest(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        Roster.Entry a = mature(t, p, h, 2 * Breeding.TIERS + 1, 3, Trait.HEARTY.id, Trait.NIGHT_WATCH.id);
        Roster.Entry b = mature(t, p, h, 2 * Breeding.TIERS + 2, 3, Trait.HEARTY.id);
        Ranch.act(p, h, Ranch.Action.PAIR, a.id, b.id);
        t.assertTrue(be(t, h).breeding() && !be(t, h).hasEgg() && a.breeding(now(t)) && b.breeding(now(t)), "pairing starts a day of breeding, no egg yet");
        Ranch.act(p, h, Ranch.Action.CHECK_OUT, a.id);
        t.assertTrue(a.where == Roster.Where.HOME, "a breeding drekki is not checked out");
        Ranch.putEgg(p, h, CreativeEggItem.egg(1));
        t.assertFalse(be(t, h).hasEgg(), "the nest is kept for the breeding egg");
        Pairing.tick(t.getLevel(), h, be(t, h));
        t.assertTrue(be(t, h).breeding() && !be(t, h).hasEgg(), "not before the day is out");
        dayPasses(t, h, a, b);
        t.assertTrue(!be(t, h).breeding() && !a.breeding(now(t)) && !b.breeding(now(t)), "the day is out: breeding ends");
        var egg = be(t, h).egg();
        t.assertTrue(!egg.isEmpty() && egg.has(DrekkiData.BRED.get()), "a bred egg is in the nest");
        var bred = egg.get(DrekkiData.BRED.get());
        t.assertTrue(bred.getInt("Line") == 2 * Breeding.TIERS + 2, "the same bloodline: one tier up from the lower parent (clear to vivid)");
        t.assertTrue(bred.getInt("Horn") == 3, "the horn both parents have");
        long now = Roster.now(t.getLevel().getServer());
        t.assertTrue(a.restDays(now) == 3 && b.restDays(now) == 3, "both parents rest 3 days");

        Ranch.act(p, h, Ranch.Action.CHECK_OUT, a.id);
        t.assertTrue(a.where == Roster.Where.HOME, "a resting drekki is not checked out");

        be(t, h).takeEgg();
        Ranch.act(p, h, Ranch.Action.PAIR, a.id, b.id);
        t.assertFalse(be(t, h).breeding(), "resting parents do not pair again");

        a.restUntil = 0; b.restUntil = 0;  /* the rest is over */
        Ranch.act(p, h, Ranch.Action.CHECK_OUT, a.id);
        t.assertTrue(a.where == Roster.Where.OUT, "rested, it can be checked out");
        Ranch.act(p, h, Ranch.Action.CHECK_IN, null);
        for (Roster.Entry e : Ranch.roster(t.getLevel().getServer()).of(p.getUUID())) Ranch.act(p, h, Ranch.Action.RELEASE, e.id);
        t.succeed();
    }

    /** the hatchery was unloaded when the day ran out: the parents' status follows the clock all the same, and the egg
     *  laid when it loads is as far along as the time since the end */
    @GameTest(template = "floor")
    public static void aBreedingThatEndedUnloadedLaysAnEggAlreadyIncubating(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        Roster.Entry a = mature(t, p, h, 0, 0);
        Roster.Entry b = mature(t, p, h, 1, 0);
        Ranch.act(p, h, Ranch.Action.PAIR, a.id, b.id);
        HatcheryBlockEntity be = be(t, h);
        be.startBreeding(be.breedA(), be.breedB(), be.breedStart() - Pairing.DAY - 30000);
        for (Roster.Entry e : new Roster.Entry[]{a, b}) { e.breedUntil -= Pairing.DAY + 30000; e.restUntil -= Pairing.DAY + 30000; }
        long now = now(t);
        t.assertTrue(!a.breeding(now) && a.restDays(now) == 2 && be.breeding() && !be.hasEgg(),
            "before the hatchery's next check: the roster already reads the clock (rest 2d left), the egg is not laid yet");
        Pairing.tick(t.getLevel(), h, be);
        Long inc = be.egg().get(DrekkiData.INCUBATION.get());
        t.assertTrue(be.egg().has(DrekkiData.BRED.get()) && inc != null && inc >= 30000 && be.incubation() >= 30000,
            "laid on load, already 30000 ticks along (" + inc + ")");
        for (Roster.Entry e : Ranch.roster(t.getLevel().getServer()).of(p.getUUID())) Ranch.act(p, h, Ranch.Action.RELEASE, e.id);
        t.succeed();
    }

    /** the child's preview shows only coats the owner has had: a coat stays recorded after its drekki is released */
    @GameTest(template = "floor")
    public static void aCoatOnceOwnedStaysKnown(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        Roster r = Ranch.roster(t.getLevel().getServer());
        int pure = 5 * Breeding.TIERS + 3;
        t.assertTrue((r.coats(p.getUUID()) >> pure & 1) == 0, "a coat never owned is not known");
        Roster.Entry a = mature(t, p, h, pure, 0);
        t.assertTrue((r.coats(p.getUUID()) >> pure & 1) != 0, "owned: known");
        Ranch.act(p, h, Ranch.Action.RELEASE, a.id);
        t.assertTrue(r.get(a.id) == null && (r.coats(p.getUUID()) >> pure & 1) != 0, "released: still known");
        t.succeed();
    }

    /** the eggs a pairing lays: Solar and Lunar parents with the Starlight Tonic give one eye of each; two-eyed parents
     *  with the same pair give that pair without it; a cancel gives the tonic back */
    @GameTest(template = "floor")
    public static void eyesFollowTheParentsAndTheStarlightTonic(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        Roster.Entry a = mature(t, p, h, 0, 0);
        Roster.Entry b = mature(t, p, h, 4, 0);
        DrekkiEntity db = Ranch.liveOf(b.id);
        db.setLook(3, 4, 1, Astral.LUNAR, 0);
        Ranch.touch(db);
        var tonic = new net.minecraft.world.item.ItemStack(dev.frosthaven.ranchercompanion.item.DrekkiItems.TONICS.get(3).get());
        Ranch.act(p, h, Ranch.Action.PAIR, a.id, b.id);
        HatcheryBlockEntity be = be(t, h);
        be.startBreeding(be.breedA(), be.breedB(), be.breedStart(), tonic);  /* as if the tonic was in the screen's slot */
        Ranch.act(p, h, Ranch.Action.CANCEL_PAIR, null);
        t.assertTrue(p.getInventory().contains(tonic), "a cancel gives the tonic back");
        Ranch.act(p, h, Ranch.Action.PAIR, a.id, b.id);
        be.startBreeding(be.breedA(), be.breedB(), be.breedStart(), tonic);
        dayPasses(t, h, a, b);
        var bred = be.egg().get(DrekkiData.BRED.get());
        if (bred == null) throw new GameTestAssertException("no bred egg");
        int e1 = bred.getInt("Astral"), e2 = bred.getInt("Astral2");
        t.assertTrue(java.util.Set.of(e1, e2).equals(java.util.Set.of(Astral.SOLAR, Astral.LUNAR)), "the tonic: one eye of each (" + e1 + ", " + e2 + ")");

        /* two-eyed parents, the same pair (sides swapped): that pair, no tonic */
        be.takeEgg();
        for (Roster.Entry e : new Roster.Entry[]{a, b}) e.restUntil = 0;
        DrekkiEntity da = Ranch.liveOf(a.id);
        da.setLook(3, 0, 1, Astral.SOLAR, 0); da.setAstral2(Astral.LUNAR); Ranch.touch(da);
        db.setLook(3, 4, 1, Astral.LUNAR, 0); db.setAstral2(Astral.SOLAR); Ranch.touch(db);
        Ranch.act(p, h, Ranch.Action.PAIR, a.id, b.id);
        dayPasses(t, h, a, b);
        bred = be.egg().get(DrekkiData.BRED.get());
        if (bred == null) throw new GameTestAssertException("no second bred egg");
        t.assertTrue(java.util.Set.of(bred.getInt("Astral"), bred.getInt("Astral2")).equals(java.util.Set.of(Astral.SOLAR, Astral.LUNAR)),
            "the same pair on both parents: the child has it");
        t.assertTrue(da.hasAstral(Astral.LUNAR) && da.hasAstral(Astral.SOLAR) && !da.hasAstral(Astral.END), "a two-eyed drekki has both astrals");
        for (Roster.Entry e : Ranch.roster(t.getLevel().getServer()).of(p.getUUID())) Ranch.act(p, h, Ranch.Action.RELEASE, e.id);
        t.succeed();
    }

    /** the Heirloom Tonic's trait is on every egg, whatever the rolls (six tries) */
    @GameTest(template = "floor")
    public static void theHeirloomTraitAlwaysPasses(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        Roster.Entry a = mature(t, p, h, 0, 0, Trait.HEARTY.id);
        Roster.Entry b = mature(t, p, h, 1, 0, Trait.NIGHT_WATCH.id);
        var tonic = new net.minecraft.world.item.ItemStack(dev.frosthaven.ranchercompanion.item.DrekkiItems.TONICS.get(1).get());
        HatcheryBlockEntity be = be(t, h);
        for (int i = 0; i < 6; i++) {
            for (Roster.Entry e : new Roster.Entry[]{a, b}) e.restUntil = 0;
            Ranch.act(p, h, Ranch.Action.PAIR, a.id, b.id);
            be.startBreeding(be.breedA(), be.breedB(), be.breedStart(), tonic, Trait.NIGHT_WATCH.id);
            dayPasses(t, h, a, b);
            var bred = be.egg().get(DrekkiData.BRED.get());
            if (bred == null) throw new GameTestAssertException("no bred egg");
            t.assertTrue(bred.getList("Traits", 8).stream().anyMatch(x -> x.getAsString().equals(Trait.NIGHT_WATCH.id)), "the kept trait is on egg " + i);
            be.takeEgg();
        }
        for (Roster.Entry e : Ranch.roster(t.getLevel().getServer()).of(p.getUUID())) Ranch.act(p, h, Ranch.Action.RELEASE, e.id);
        t.succeed();
    }

    @GameTest(template = "floor")
    public static void cancellingEndsBreedingWithNoEggAndNoRest(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        Roster.Entry a = mature(t, p, h, 0, 0);
        Roster.Entry b = mature(t, p, h, 1, 0);
        Ranch.act(p, h, Ranch.Action.PAIR, a.id, b.id);
        t.assertTrue(be(t, h).breeding(), "breeding");
        Ranch.act(p, h, Ranch.Action.CANCEL_PAIR, null);
        long now = Roster.now(t.getLevel().getServer());
        t.assertTrue(!be(t, h).breeding() && !be(t, h).hasEgg() && !a.breeding(now) && !b.breeding(now) && a.restDays(now) == 0 && b.restDays(now) == 0,
            "cancelled: no egg, no rest, both free");
        Ranch.act(p, h, Ranch.Action.CHECK_OUT, a.id);
        t.assertTrue(a.where == Roster.Where.OUT, "free to check out again");
        Ranch.act(p, h, Ranch.Action.CHECK_IN, null);
        for (Roster.Entry e : Ranch.roster(t.getLevel().getServer()).of(p.getUUID())) Ranch.act(p, h, Ranch.Action.RELEASE, e.id);
        t.succeed();
    }

    @GameTest(template = "floor")
    public static void aFullNestOrNoMatureRefusesPairing(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        Roster.Entry a = mature(t, p, h, 0, 0);
        Roster.Entry b = mature(t, p, h, 4, 1);
        Ranch.putEgg(p, h, CreativeEggItem.egg(1));
        Ranch.act(p, h, Ranch.Action.PAIR, a.id, b.id);
        t.assertTrue(!be(t, h).breeding() && !a.breeding(now(t)), "an egg in the nest: no pairing");
        be(t, h).takeEgg();
        Ranch.liveOf(b.id).setLook(2, 4, 1, Astral.SOLAR, 1);
        Ranch.touch(Ranch.liveOf(b.id));
        Ranch.act(p, h, Ranch.Action.PAIR, a.id, b.id);
        t.assertFalse(be(t, h).breeding(), "a Fledgling does not pair");
        Ranch.act(p, h, Ranch.Action.PAIR, a.id, a.id);
        t.assertFalse(be(t, h).breeding(), "a drekki does not pair with itself");
        for (Roster.Entry e : Ranch.roster(t.getLevel().getServer()).of(p.getUUID())) Ranch.act(p, h, Ranch.Action.RELEASE, e.id);
        t.succeed();
    }

    @GameTest(template = "floor")
    public static void theBredEggHatchesTheirChild(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        String[] five = {Trait.HEARTY.id, Trait.NIGHT_WATCH.id, Trait.OPEN_ROAD.id, Trait.DOWNSTREAM.id, Trait.DUCK_COVER.id};
        Roster.Entry a = mature(t, p, h, 3, 2, five);
        Roster.Entry b = mature(t, p, h, 7 * Breeding.TIERS + 1, 4, five);
        Ranch.act(p, h, Ranch.Action.PAIR, a.id, b.id);
        dayPasses(t, h, a, b);
        var bred = be(t, h).egg().get(DrekkiData.BRED.get());
        if (bred == null) throw new GameTestAssertException("no bred egg");
        t.assertTrue(Breeding.tier(bred.getInt("Line")) == 0, "mixed bloodlines: one below the lower parent's clear, so muted");
        /* the egg is Ready at once for the test; a real one incubates 3 days */
        var egg = be(t, h).takeEgg();
        egg.set(DrekkiData.INCUBATION.get(), Incubation.READY + 1);
        be(t, h).putEgg(egg);
        Roster r = Ranch.roster(t.getLevel().getServer());
        int before = r.of(p.getUUID()).size();
        Ranch.hatch(p, h);
        List<Roster.Entry> mine = r.of(p.getUUID());
        t.assertTrue(mine.size() == before + 1, "the child hatched");
        DrekkiEntity c = Ranch.liveOf(mine.get(mine.size() - 1).id);
        t.assertTrue(c.stage() == 1 && c.astral() == Astral.SOLAR && c.astral2() == 0, "a pup with its parents' shared astral, eyes lit");
        t.assertTrue(c.line() == bred.getInt("Line") && c.horn() == bred.getInt("Horn"), "the egg's line and horn");
        int inherited = bred.getList("Traits", 8).size();
        t.assertTrue(c.life.traits.size() == inherited, "every inherited trait, past the pup's cap of 2 too (" + c.life.traits + ")");
        for (Roster.Entry e : r.of(p.getUUID())) Ranch.act(p, h, Ranch.Action.RELEASE, e.id);
        t.succeed();
    }
}
