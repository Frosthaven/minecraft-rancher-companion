package dev.frosthaven.ranchercompanion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frosthaven.ranchercompanion.life.Breeding;
import dev.frosthaven.ranchercompanion.life.Life;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The first breeding pass (spec, Breeding): fur tiers, horns, trait odds and the 5 kept, and the eyes. */
class BreedingTest {
    private static Breeding.Rng rng(long seed) {
        Random r = new Random(seed);
        return new Breeding.Rng() {
            public int below(int n) { return r.nextInt(n); }
            public double unit() { return r.nextDouble(); }
        };
    }

    private static int line(int family, int tier) { return family * Breeding.TIERS + tier; }

    @Test
    void theSameBloodlineClimbsOneTierAndStopsAtPure() {
        Breeding.Rng r = rng(1);
        assertEquals(line(2, 1), Breeding.child(line(2, 0), 0, List.of(), line(2, 0), 0, List.of(), r).line());
        assertEquals(line(2, 2), Breeding.child(line(2, 1), 0, List.of(), line(2, 3), 0, List.of(), r).line(), "from the lower parent");
        assertEquals(line(2, 3), Breeding.child(line(2, 3), 0, List.of(), line(2, 3), 0, List.of(), r).line());
    }

    @Test
    void mixedBloodlinesGiveAnyBloodlineOneTierDownNeverBelowMuted() {
        Breeding.Rng r = rng(2);
        Set<Integer> families = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            Breeding.Child c = Breeding.child(line(0, 3), 0, List.of(), line(5, 2), 0, List.of(), r);
            assertEquals(1, Breeding.tier(c.line()), "one below the lower parent's vivid");
            families.add(Breeding.family(c.line()));
            assertEquals(0, Breeding.tier(Breeding.child(line(0, 0), 0, List.of(), line(5, 1), 0, List.of(), r).line()), "muted is the floor");
        }
        assertEquals(Breeding.FAMILIES, families.size(), "any of the nine");
    }

    @Test
    void hornsFollowTheBloodline() {
        Breeding.Rng r = rng(3);
        Set<Integer> mixed = new HashSet<>(), two = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            assertEquals(4, Breeding.child(line(1, 0), 4, List.of(), line(1, 2), 4, List.of(), r).horn());
            two.add(Breeding.child(line(1, 0), 1, List.of(), line(1, 2), 3, List.of(), r).horn());
            mixed.add(Breeding.child(line(1, 0), 1, List.of(), line(6, 2), 1, List.of(), r).horn());
        }
        assertEquals(Set.of(1, 3), two);
        assertEquals(Breeding.HORNS, mixed.size());
    }

    @Test
    void traitsPassAtAThirdOrFiveNinthsWhenBothHoldThem() {
        Breeding.Rng r = rng(4);
        int one = 0, both = 0, n = 20000;
        for (int i = 0; i < n; i++) {
            List<String> t = Breeding.child(0, 0, List.of("a", "b"), 0, 0, List.of("b"), r).traits();
            if (t.contains("a")) one++;
            if (t.contains("b")) both++;
        }
        assertEquals(1 / 3.0, one / (double) n, 0.015);
        assertEquals(5 / 9.0, both / (double) n, 0.015);
    }

    @Test
    void atMostFiveAreKept() {
        Breeding.Rng r = rng(5);
        List<String> a = List.of("a", "b", "c", "d", "e", "f"), b = List.of("a", "b", "c", "d", "e", "f", "g");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            List<String> t = Breeding.child(0, 0, a, 0, 0, b, r).traits();
            assertTrue(t.size() <= Breeding.KEEP);
            assertEquals(t.size(), new HashSet<>(t).size(), "no trait twice");
            seen.addAll(t);
        }
        assertEquals(7, seen.size(), "which ones are kept is random");
    }

    private static java.util.Set<String> seen(int[] a, int[] b, boolean twin, long seed) {
        Breeding.Rng r = rng(seed);
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (int i = 0; i < 400; i++) {
            int[] e = Breeding.eyes(a, b, twin, r);
            out.add(e.length == 1 ? String.valueOf(e[0]) : Math.min(e[0], e[1]) + "+" + Math.max(e[0], e[1]));
        }
        return out;
    }

    @Test
    void eyesFollowTheParentsAstrals() {
        int[] solar = {1}, lunar = {2}, solarLunar = {1, 2}, lunarSolar = {2, 1}, aeroDeep = {3, 5};
        assertEquals(Set.of("1"), seen(solar, solar, false, 7), "the same astral: guaranteed");
        assertEquals(Set.of("1", "2"), seen(solar, lunar, false, 8), "different: one of the two at random");
        assertEquals(Set.of("1+2"), seen(solar, lunar, true, 9), "the Starlight Tonic: one eye of each");
        assertEquals(Set.of("1"), seen(solar, solar, true, 10), "the tonic on the same astral changes nothing");
        assertEquals(Set.of("1+2"), seen(solarLunar, lunarSolar, false, 11), "two-eyed parents with the same pair: that pair, no tonic");
        assertEquals(Set.of("1+2", "1+3", "1+5", "2+3", "2+5", "3+5"), seen(solarLunar, aeroDeep, false, 12),
            "two-eyed parents: a two-eyed child, two different astrals from all of theirs");
        assertEquals(Set.of("1+2", "1+3", "2+3"), seen(solarLunar, new int[]{2, 3}, false, 16), "a shared astral counts once: never two of one color");
        assertEquals(Set.of("1", "2", "3"), seen(solarLunar, new int[]{3}, false, 17), "one two-eyed parent: one astral, any of theirs");
        assertEquals(Set.of("1+3", "1+5", "2+3", "2+5"), seen(solarLunar, aeroDeep, true, 13), "different pairs with the tonic: one from each parent");
        Breeding.Rng r = rng(14);
        boolean left = false, right = false;
        for (int i = 0; i < 200; i++) { int[] e = Breeding.eyes(solar, lunar, true, r); if (e[0] == 1) left = true; else right = true; }
        assertTrue(left && right, "which eye gets which color is random");
    }

    @Test
    void thePairsAverageMoodMovesTheOddsAndHeirloomAlwaysPasses() {
        assertEquals(-0.10, Breeding.moodBonus(0), 1e-9);
        assertEquals(-0.05, Breeding.moodBonus(1), 1e-9);
        assertEquals(0.05, Breeding.moodBonus(2), 1e-9);
        assertEquals(0.10, Breeding.moodBonus(3), 1e-9);
        assertEquals(0.15, Breeding.moodBonus(4), 1e-9);
        var odds = Breeding.odds(List.of("a", "b"), List.of("b", "c"), 0, 4, "c");
        double p = 1 / 3.0 + (-0.10 + 0.15) / 2;  /* Miserable and Thriving: the average, +2.5 points */
        assertEquals(p, odds.get("a"), 1e-9, "one parent's trait, at the pair's mood score");
        assertEquals(1 - (1 - p) * (1 - p), odds.get("b"), 1e-9, "held by both: either roll");
        assertEquals(1.0, odds.get("c"), 1e-9, "the Heirloom trait");
        Breeding.Rng r = rng(15);
        List<String> six = List.of("a", "b", "c", "d", "e", "f");
        for (int i = 0; i < 500; i++) {
            var c = Breeding.child(new Breeding.Parent(0, 0, six, new int[]{1}, 4), new Breeding.Parent(0, 0, six, new int[]{1}, 4), false, "f", r);
            assertTrue(c.traits().contains("f") && c.traits().size() <= Breeding.KEEP, "kept past the cut of 5 too");
        }
    }

    @Test
    void aGhostParentGivesABakenekoWithItsLegendaryTrait() {
        int was = Breeding.bakeneko;
        Breeding.bakeneko = 44;
        try {
            Breeding.Rng r = rng(16);
            List<String> five = List.of("a", "b", "c", "d", "e");
            for (int i = 0; i < 300; i++) {
                var c = Breeding.child(new Breeding.Parent(line(2, 3), 1, five, new int[]{1}, 2, true), new Breeding.Parent(line(5, 0), 1, five, new int[]{1}, 2), false, null, r);
                assertEquals(44, c.line(), "a ghost parent: always a Bakeneko");
                assertEquals("spirit_veil", c.traits().get(0), "its legendary trait first");
                assertTrue(c.traits().size() <= Breeding.KEEP, "still up to 5 in all");
            }
            for (int i = 0; i < 100; i++) {
                var c = Breeding.child(new Breeding.Parent(44, 2, List.of(), new int[]{1}, 2), new Breeding.Parent(44, 2, List.of(), new int[]{1}, 2), false, null, r);
                assertEquals(44, c.line(), "two Bakeneko: a Bakeneko");
                assertEquals(List.of("spirit_veil"), c.traits(), "with its legendary trait");
                assertEquals(2, c.horn(), "their shared horn");
                var m = Breeding.child(new Breeding.Parent(44, 2, List.of("spirit_veil"), new int[]{1}, 2), new Breeding.Parent(line(3, 1), 2, List.of(), new int[]{1}, 2), false, null, r);
                assertTrue(m.line() < Breeding.BLOODLINES, "a Bakeneko and a living bloodline drekki: a bloodline (mixed)");
            }
        } finally { Breeding.bakeneko = was; }
    }

    /** a ghost has no mood (-1): only the living parent's mood counts (Shane, 2026-11-26) */
    @Test
    void aGhostParentAddsNoMood() {
        assertEquals(Breeding.moodBonus(4), Breeding.moodScore(-1, 4), 1e-9);
        assertEquals(Breeding.moodBonus(0), Breeding.moodScore(0, -1), 1e-9);
        assertEquals(0, Breeding.moodScore(-1, -1), 1e-9);
        assertEquals((Breeding.moodBonus(4) + Breeding.moodBonus(0)) / 2, Breeding.moodScore(4, 0), 1e-9);
    }
}
