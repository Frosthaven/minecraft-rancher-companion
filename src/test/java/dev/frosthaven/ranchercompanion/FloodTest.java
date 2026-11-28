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

    /** a fence line at z = 2 from x -10 to 10, with a gap at x = gap (none when gap is out of that range) */
    private static Set<String> fenceLine(int gap) {
        Set<String> f = new HashSet<>();
        for (int x = -10; x <= 10; x++) if (x != gap) f.add(x + ",2");
        return f;
    }

    @Test
    void thePenStopsAtAFence() {
        /* bedding (an anchor) at 0,0,3 behind the fence, within 3 of the hatchery: neither it nor the ground past the
           fence is in the pen (the area once went through the fence) */
        Set<Long> pen = Flood.anchored(flat(fenceLine(99)), 0, 0, 0, 50, 6, 3, p -> Flood.x(p) == 0 && Flood.z(p) == 3);
        assertTrue(pen.contains(Flood.pack(0, 0, 1)), "this side of the fence");
        assertFalse(pen.contains(Flood.pack(1, 0, 3)), "within 3 of the hatchery, but behind the fence");
        assertFalse(pen.contains(Flood.pack(0, 0, 3)), "the bedding behind the fence");
    }

    @Test
    void aWayRoundTheFenceLetsTheAnchorCount() {
        /* a gap at x = 3: the walk goes round, steps onto the bedding, and the bedding opens its own 3 blocks */
        Set<Long> pen = Flood.anchored(flat(fenceLine(3)), 0, 0, 0, 50, 6, 3, p -> Flood.x(p) == 0 && Flood.z(p) == 3);
        assertTrue(pen.contains(Flood.pack(3, 0, 2)), "the gap");
        assertTrue(pen.contains(Flood.pack(0, 0, 3)), "the bedding, reached round the fence");
        assertTrue(pen.contains(Flood.pack(0, 0, 6)), "3 past the bedding");
        assertFalse(pen.contains(Flood.pack(0, 0, 7)), "but no further");
    }

    @Test
    void anchorsChainOffTheEdgeAndDown() {
        /* ground at y 0 for x >= -6, at y -1 west of it; bedding at x -3 and -6: the area runs 3 past the last (x -9) */
        Flood.Grid g = (x, y, z) -> x >= -6 ? y == 0 : y == -1;
        Set<Long> pen = Flood.anchored(g, 0, 0, 0, 50, 9, 3, p -> Flood.z(p) == 0 && (Flood.x(p) == -3 || Flood.x(p) == -6) && Flood.y(p) == 0);
        int west = 0;
        for (long p : pen) west = Math.max(west, -Flood.x(p));
        assertEquals(9, west);
    }
}
