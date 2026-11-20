package dev.frosthaven.ranchercompanion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frosthaven.ranchercompanion.look.DyeMix;
import java.util.List;
import org.junit.jupiter.api.Test;

class DyeMixTest {
    static final int RED = 0xb02e26, BLUE = 0x3c44aa;

    @Test
    void onlyTheColumnCounts() {
        /* the item in the middle column: left is main, right is trim, its own column (above, below) both */
        var s = DyeMix.split(List.of(new int[]{0, RED}, new int[]{2, BLUE}, new int[]{1, 0x123456}), 1);
        assertEquals(List.of(RED, 0x123456), s.main());
        assertEquals(List.of(BLUE, 0x123456), s.trim());
    }

    @Test
    void firstDyeIsPureAndRedyeingBlendsHalfway() {
        assertEquals(RED, DyeMix.mix(-1, List.of(RED)));
        int once = DyeMix.mix(RED, List.of(BLUE)), twice = DyeMix.mix(once, List.of(BLUE));
        int r1 = once >> 16 & 255, r2 = twice >> 16 & 255;
        assertEquals(true, r1 < (RED >> 16 & 255) && r2 < r1, "each blue step moves away from red: " + Integer.toHexString(once) + " " + Integer.toHexString(twice));
        assertEquals(-1, DyeMix.mix(-1, List.of()));
        assertEquals(RED, DyeMix.mix(RED, List.of()));
    }
}
