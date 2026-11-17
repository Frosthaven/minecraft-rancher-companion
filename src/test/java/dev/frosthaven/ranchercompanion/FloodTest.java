package dev.frosthaven.ranchercompanion;

import static org.junit.jupiter.api.Assertions.*;

import dev.frosthaven.ranchercompanion.look.Incubation;
import dev.frosthaven.ranchercompanion.ranch.Flood;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FloodTest {
    /** flat ground at y 0; fences block a spot */
    private static Flood.Grid flat(Set<String> fences) {
        return (x, y, z) -> y == 0 && !fences.contains(x + "," + z);
    }

    @Test
    void packRoundTrip() {
        long p = Flood.pack(-123, -60, 4567);
        assertEquals(-123, Flood.x(p)); assertEquals(-60, Flood.y(p)); assertEquals(4567, Flood.z(p));
    }

    @Test
    void openFieldPlacesNextToTheHatchery() {
        long p = Flood.place(flat(Set.of()), 0, 0, 0, 3, s -> true);
        assertEquals(1, Math.max(Math.abs(Flood.x(p)), Math.abs(Flood.z(p))));
    }

    @Test
    void fencedPenComesBeforeTheNearerSpotOutside() {
        /* hatchery at 0,0 against a fence line at x = 1 (all z): every spot east of it is outside the pen */
        Set<String> fence = new HashSet<>();
        for (int z = -3; z <= 3; z++) fence.add("1," + z);
        /* only spots outside (x >= 2) and one inside far away (x = -3) fit */
        long p = Flood.place(flat(fence), 0, 0, 0, 3, s -> Flood.x(s) >= 2 || Flood.x(s) == -3);
        assertEquals(-3, Flood.x(p));
    }

    @Test
    void fallsBackOutsideWhenThePenIsFull() {
        Set<String> fence = new HashSet<>();
        for (int z = -3; z <= 3; z++) fence.add("1," + z);
        long p = Flood.place(flat(fence), 0, 0, 0, 3, s -> Flood.x(s) >= 2);
        assertEquals(2, Flood.x(p));
    }

    @Test
    void noRoomIsMinusOne() {
        assertEquals(-1, Flood.place(flat(Set.of()), 0, 0, 0, 3, s -> false));
    }

    @Test
    void incubationStages() {
        assertEquals(0, Incubation.stage(0));
        assertEquals(0, Incubation.stage(Incubation.DAY));
        assertEquals(1, Incubation.stage(Incubation.DAY + 1));
        assertEquals(2, Incubation.stage(Incubation.READY));
        assertEquals(3, Incubation.stage(Incubation.READY + 1));
        for (int d = 1; d <= 4; d++) assertEquals(d - 1, Incubation.stage(Incubation.creativeDay(d)));
    }
}
