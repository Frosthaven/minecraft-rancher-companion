package dev.frosthaven.ranchercompanion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.look.Astral;
import dev.frosthaven.ranchercompanion.trait.Trait;
import org.junit.jupiter.api.Test;

/** The 0.5.0 trait rules: the caps, the update of drekki from the 3/6/9 caps, and who can learn what. */
class TraitRulesTest {
    private static Life old(int traits) {
        Life l = new Life();
        l.oldCaps = true;
        for (int i = 0; i < traits; i++) l.traits.add("t" + i);
        return l;
    }

    @Test
    void anOldDrekkiOverTheNewCapCountsAsHavingHadThePotionAndKeepsItsTraits() {
        Life pup = old(3), fledgling5 = old(5), fledgling6 = old(6);
        pup.settleSlots(Trait.cap(1, false));
        fledgling5.settleSlots(Trait.cap(2, false));
        fledgling6.settleSlots(Trait.cap(2, false));
        assertTrue(pup.horizons && fledgling5.horizons && fledgling6.horizons);
        assertEquals(6, fledgling6.traits.size());
        assertEquals(6, Trait.cap(3, true), "a Fledgling with 6 sits on the final cap once it matures");
    }

    @Test
    void anOldDrekkiAtOrUnderTheNewCapCanStillTakeThePotion() {
        Life pup = old(2), fledgling = old(4);
        pup.settleSlots(Trait.cap(1, false));
        fledgling.settleSlots(Trait.cap(2, false));
        assertFalse(pup.horizons || fledgling.horizons);
    }

    @Test
    void theUpdateRunsOncePerDrekki() {
        Life l = old(2);
        l.settleSlots(Trait.cap(1, false));
        l.traits.add("later");  /* learned after the update, with the potion's slot or not */
        l.traits.add("more");
        l.settleSlots(Trait.cap(1, false));
        assertFalse(l.horizons);
    }

    @Test
    void eachAffinityLearnsNineteenOrTwentyOfFortyAndOneCapstoneOfItsOwn() {
        int[] astrals = {Astral.SOLAR, Astral.LUNAR, Astral.AERO, Astral.UNDERWORLD, Astral.DEEP, Astral.END};
        for (int a : astrals) {
            int learnable = 0, capstones = 0;
            for (Trait t : Trait.values()) {
                if (!t.learnedBy(a)) continue;
                learnable++;
                if (Integer.bitCount(t.affinities) == 1) capstones++;
            }
            assertTrue(learnable == 19 || learnable == 20, Astral.NAMES[a] + " learns " + learnable);
            assertEquals(1, capstones, Astral.NAMES[a]);
        }
    }

    @Test
    void aDormantPupLearnsOnlyWhatEveryAffinityCan() {
        for (Trait t : Trait.values()) assertEquals(Integer.bitCount(t.affinities) == 6, t.learnedBy(Astral.DORMANT), t.id);
    }

    @Test
    void noSingleAffinityLearnsBothHalvesOfThermostatOrBothProvokeAndFade() {
        for (int a = Astral.SOLAR; a <= Astral.END; a++) {
            assertFalse(Trait.STAMP_OUT.learnedBy(a) && Trait.HUDDLE_UP.learnedBy(a), Astral.NAMES[a]);
            assertFalse(Trait.PROVOKE.learnedBy(a) && Trait.FADE.learnedBy(a), Astral.NAMES[a]);
            assertFalse(Trait.LAVA_WALKER.learnedBy(a) && Trait.OCEAN_WALKER.learnedBy(a), Astral.NAMES[a] + ": Surface Tension is for breeding");
        }
    }

    @Test
    void thereAreFortyTraitsAndEveryCapstoneIsEpic() {
        assertEquals(40, Trait.values().length);
        int epic = 0, rare = 0, uncommon = 0;
        for (Trait t : Trait.values()) {
            if (Integer.bitCount(t.affinities) == 1) assertEquals(Trait.Rarity.EPIC, t.rarity, t.id);
            switch (t.rarity) { case EPIC -> epic++; case RARE -> rare++; case UNCOMMON -> uncommon++; default -> throw new AssertionError(t.id + " is Legendary: that tier is for bred traits"); }
        }
        assertEquals(11, epic);
        assertEquals(10, rare);
        assertEquals(19, uncommon);
    }
}
