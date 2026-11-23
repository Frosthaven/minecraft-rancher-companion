package dev.frosthaven.ranchercompanion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frosthaven.ranchercompanion.look.EnchantMath;
import org.junit.jupiter.api.Test;

/** The numbers of barding's enchantments, which follow the game's own. */
class BardingEnchantsTest {
    @Test
    void protectionsAddUpByDamageKindAndStopAtEightyPercent() {
        assertEquals(0.16f, EnchantMath.guard(4, 0, 0, 0, false, false, false), 1e-6);
        assertEquals(0.32f, EnchantMath.guard(0, 0, 4, 0, false, true, false), 1e-6, "Blast Protection IV against a blast");
        assertEquals(0f, EnchantMath.guard(0, 0, 4, 0, false, false, false), 1e-6, "and against nothing else");
        assertEquals(0.48f, EnchantMath.guard(4, 4, 0, 0, true, false, false), 1e-6);
        assertEquals(0.8f, EnchantMath.guard(4, 4, 4, 4, true, true, true), 1e-6);
    }

    @Test
    void sharpnessSmiteAndBaneAddTheGamesDamage() {
        assertEquals(3f, EnchantMath.bonus(5, 0, 0, false, false), 1e-6);
        assertEquals(1f, EnchantMath.bonus(1, 0, 0, false, false), 1e-6);
        assertEquals(12.5f, EnchantMath.bonus(0, 5, 0, true, false), 1e-6);
        assertEquals(0f, EnchantMath.bonus(0, 5, 5, false, false), 1e-6, "Smite and Bane only on their own kind");
        assertEquals(0.75f, EnchantMath.sweep(3), 1e-6);
        assertEquals(0f, EnchantMath.sweep(0), 1e-6);
    }
}
