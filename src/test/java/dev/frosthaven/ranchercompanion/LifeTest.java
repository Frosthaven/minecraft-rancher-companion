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
        assertEquals(Band.UNEASY, Life.band(Life.effective(0, false, 9), false, 0));
        assertEquals(Band.MISERABLE, Life.band(Life.effective(0, false, 8), false, 0));
        assertEquals(Life.effective(0, false, 9), Life.effective(0, false, 30), 1e-9);  /* capped at nine */
    }

    @Test
    void thrivingNeedsPettingAndFood() {
        assertEquals(Band.HAPPY, Life.band(100, false, 10));
        assertEquals(Band.HAPPY, Life.band(100, true, 5));
        assertEquals(Band.THRIVING, Life.band(Life.effective(80, true, 0), true, 6));
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
}
