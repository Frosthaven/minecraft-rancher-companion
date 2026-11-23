package dev.frosthaven.ranchercompanion;

import static org.junit.jupiter.api.Assertions.*;

import dev.frosthaven.ranchercompanion.look.RideMath;
import org.junit.jupiter.api.Test;

class RideMathTest {
    /** one flight tick as the game moves a mount in the air: the push, then gravity and drag */
    private static double climb(double vy, int tick, double upY) {
        double[] p = RideMath.thrust(tick, 0, upY, 0, vy);
        if (p != null) vy += p[1];
        return (vy - 0.08) * 0.98;
    }

    @Test
    void wingsClimbWhenTheRiderLooksLevelAndNeverRunAway() {
        double vy = 0, y = 0, top = 0;
        for (int i = 0; i < 400; i++) { vy = climb(vy, i, 1); y += vy; top = Math.max(top, vy); }
        assertTrue(y > 20, "20 s of flight climbs: " + y);
        assertTrue(top <= RideMath.CLIMB + RideMath.THRUST, "the climb eases out: " + top);
    }

    @Test
    void theWingsPushOnTheBeatOnlySoItCannotHover() {
        int pushes = 0;
        for (int i = 0; i < RideMath.BEAT; i++) if (RideMath.thrust(i, 0, 1, 0, 0) != null) pushes++;
        assertEquals(RideMath.PUSH, pushes);
        /* between beats it sinks: its height is never held still */
        double vy = 0, lo = 1, hi = -1;
        for (int i = 0; i < 240; i++) { vy = climb(vy, i, 1); if (i > 120) { lo = Math.min(lo, vy); hi = Math.max(hi, vy); } }
        assertTrue(hi - lo > 0.1, "it rises and sinks with each beat: " + lo + " to " + hi);
    }

    @Test
    void lookingDownTurnsThePushForward() {
        double[] level = RideMath.thrust(0, 0, 1, 0, 0), down = RideMath.thrust(0, 0, 0.5, 0.866, 0);
        assertEquals(0, level[2], 1e-9);
        assertTrue(down[2] > down[1], "more forward than up");
    }

    @Test
    void aLiquidWalkerStaysOnTopFromThreeBlocksOrLess() {
        assertTrue(RideMath.staysOnTop(0) && RideMath.staysOnTop(3));
        assertFalse(RideMath.staysOnTop(3.5f));
    }
}
