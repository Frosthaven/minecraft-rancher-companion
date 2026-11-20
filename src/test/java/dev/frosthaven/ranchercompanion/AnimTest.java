package dev.frosthaven.ranchercompanion;

import dev.frosthaven.ranchercompanion.look.Anim;
import dev.frosthaven.ranchercompanion.look.Lineage;
import dev.frosthaven.ranchercompanion.look.Palette;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class AnimTest {
    @Test
    void stageOneHasNoAttacksAndStageTwoNoCooldowns() {
        EnumSet<Anim> s1 = EnumSet.noneOf(Anim.class), s2 = EnumSet.noneOf(Anim.class);
        for (Anim a : Anim.values()) { if (a.availableAt(1)) s1.add(a); if (a.availableAt(2)) s2.add(a); }
        assertFalse(s1.contains(Anim.BITE)); assertFalse(s1.contains(Anim.BALL)); assertFalse(s1.contains(Anim.GLIDE));
        assertTrue(s2.contains(Anim.BITE)); assertTrue(s2.contains(Anim.BALL));
        for (Anim a : EnumSet.of(Anim.POUNCE, Anim.BREATH, Anim.GUARD, Anim.REJUV, Anim.GLIDE)) {
            assertFalse(s2.contains(a), a + " is stage 3 only");
            assertTrue(a.availableAt(3));
        }
    }

    @Test
    void dashSpeedPeaksThenEasesBackToTheRun() {
        double run = Anim.RUN.speedAt(3, 0);
        assertEquals(run, Anim.DASH.speedAt(3, 0), 1e-9);
        assertEquals(run + 4.5 * Anim.worldK(3), Anim.DASH.speedAt(3, 3.4 * 0.225), 1e-6);  /* the burst scales with the stage like the run */
        assertEquals(run, Anim.DASH.speedAt(3, 3.0), 1e-9);
    }

    @Test
    void pureTierReachesThePureColorAndRareLinesFollow() {
        assertEquals(0x7d8a5c, Lineage.of(0).fur());          // Moss muted
        assertEquals(0x3cc45c, Lineage.of(3).fur());          // Moss pure
        assertEquals("Glowberry", Lineage.of(Lineage.BLOODLINES + 1).name());
        assertEquals(Lineage.COUNT, 59);
        assertEquals(0x000000, Palette.shade(0x808080, -1));
        assertEquals(0xffffff, Palette.shade(0x808080, 1));
    }
}
