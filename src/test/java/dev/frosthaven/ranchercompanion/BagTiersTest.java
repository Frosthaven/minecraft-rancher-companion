package dev.frosthaven.ranchercompanion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frosthaven.ranchercompanion.look.Gear;
import org.junit.jupiter.api.Test;

class BagTiersTest {
    /** Sophisticated Backpacks' defaults by tier (leather, copper, iron, gold, diamond, netherite): slots, upgrade slots */
    static final int[] BACKPACK_SLOTS = {27, 45, 54, 81, 108, 120}, BACKPACK_UPGRADES = {1, 1, 2, 3, 5, 7};

    @Test
    void everyBagStaysUnderTheBackpackOfItsTier() {
        for (int k = 1; k < Gear.BAGS.length; k++) {
            assertTrue(Gear.BAG_SLOTS[k] < BACKPACK_SLOTS[k - 1], Gear.BAGS[k] + " slots");
            assertTrue(Gear.BAG_UPGRADES[k] <= BACKPACK_UPGRADES[k - 1], Gear.BAGS[k] + " upgrade slots");
            if (k > 1) assertTrue(Gear.BAG_SLOTS[k] > Gear.BAG_SLOTS[k - 1], "each tier holds more");
        }
    }
}
