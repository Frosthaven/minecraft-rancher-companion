package dev.frosthaven.ranchercompanion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.life.Life.Band;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LifeTest {
    @Test
    void nineKinKeepItFromMiserable() {
        assertEquals(Band.UNEASY, Life.band(Life.effective(0, 9), false, 0));
        assertEquals(Band.MISERABLE, Life.band(Life.effective(0, 8), false, 0));
        assertEquals(Life.effective(0, 9), Life.effective(0, 30), 1e-9);  /* capped at nine */
    }

    @Test
    void aPetLiftsItOneBand() {
        assertEquals(Band.UNEASY, Life.band(0, true, 0));
        assertEquals(Band.HAPPY, Life.band(50, true, 0));  /* the bottom of Content: the case +10 missed */
        assertEquals(Band.HAPPY, Life.band(100, false, 10));  /* never Thriving without a pet */
    }

    @Test
    void thrivingNeeds85APetAndFood() {
        assertEquals(Band.THRIVING, Life.band(85, true, 6));
        assertEquals(Band.HAPPY, Life.band(84, true, 10));
        assertEquals(Band.HAPPY, Life.band(100, true, 5));
    }

    @Test
    void stagesFollowTravelAndStopAtTheCap() {
        long day = Life.DAY;
        assertEquals(1, Life.stageFor(7 * day - 1, 7, 2));
        assertEquals(2, Life.stageFor(7 * day, 7, 2));
        assertEquals(2, Life.stageFor(100 * day, 7, 2));
        assertEquals(3, Life.stageFor(14 * day, 7, 3));
    }

    @Test
    void pickTakesTheLargestAndSettlesTiesAtRandom() {
        Random r = new Random(1);
        java.util.function.IntUnaryOperator rnd = r::nextInt;
        assertNull(Life.pick(new long[7], 1, rnd));
        assertEquals(4, Life.pick(new long[]{0, 5, 2, 0, 9, 0, 1}, 1, rnd));
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 64; i++) seen.add(Life.pick(new long[]{0, 3, 0, 3, 0, 0, 0}, 1, rnd));
        assertEquals(Set.of(1, 3), seen);
        assertTrue(Life.pick(new int[]{0, 0, 0, 0}, rnd) == null);
    }

    /** the shank bar shows an empty shank exactly when the drekki eats at a trough (a whole shank missing) */
    @Test
    void emptyShankMeansItEats() {
        for (int k = 0; k <= 1000; k++) {
            double food = k / 100.0;
            Life l = new Life();
            l.food = food;
            boolean emptyShown = Life.shownFood(food) <= Life.FOOD_MAX - 1;
            assertEquals(l.wantsFood(), emptyShown, "food " + food);
        }
        assertEquals(9.5f, Life.shownFood(9.18));
    }

    @Test
    void aFedDrekkiHealsAsAPlayerDoesAndPaysInFood() {
        Life l = new Life();
        assertEquals(0, l.fedHeal(79, 20));
        assertEquals(1, l.fedHeal(1, 20));           /* 4 s: one health */
        assertEquals(9.25, l.food, 1e-9);
        assertEquals(1, l.fedHeal(200, 20));         /* 9.25 pays for one more, then it is below 9 shanks */
        assertEquals(8.5, l.food, 1e-9);
        assertEquals(0, l.fedHeal(400, 20));
        l.food = 10;
        assertEquals(0, l.fedHeal(400, 0));          /* unhurt: nothing spent */
        assertEquals(10, l.food, 1e-9);
    }

    @Test
    void aFedDrekkiHealsFromItsOwnFoodAndPaysInFood() {
        Life l = new Life();
        assertEquals(0, l.fedHeal(79, 20));
        assertEquals(1, l.fedHeal(1, 20));           /* 4 s: one health */
        assertEquals(9.25, l.food, 1e-9);
        assertEquals(1, l.fedHeal(200, 20));         /* 9.25 pays for one more, then it is below 9 shanks */
        assertEquals(8.5, l.food, 1e-9);
        assertEquals(0, l.fedHeal(400, 20));
        l.food = 10;
        assertEquals(0, l.fedHeal(400, 0));          /* unhurt: nothing spent */
        assertEquals(10, l.food, 1e-9);
    }

    @Test
    void saturationIsSpentFirstAndHealsFast() {
        Life l = new Life();
        l.food = 8;
        l.eat(4, 6.4);                               /* a steak: the bar fills, the rest of the nutrition is lost */
        assertEquals(10, l.food, 1e-9);
        assertEquals(6.4, l.sat, 1e-9);
        l.spend(1);                                  /* hunger takes saturation, not shanks */
        assertEquals(10, l.food, 1e-9);
        assertEquals(5.4, l.sat, 1e-9);
        assertEquals(2, l.fedHeal(20, 20), 1e-6);    /* full with saturation: 1 health each half second */
        assertEquals(10, l.food, 1e-9);
        assertEquals(3.9, l.sat, 1e-9);
        l.food = 3;
        l.eat(1, 9);                                 /* saturation never passes the shanks */
        assertEquals(4, l.sat, 1e-9);
    }
}
