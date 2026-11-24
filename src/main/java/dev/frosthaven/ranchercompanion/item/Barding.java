package dev.frosthaven.ranchercompanion.item;

/**
 * The seven barding tiers, in display order, with each plate's undyed color (0xRRGGBB) and durability by stage (spec,
 * Barding: it soaks up damage into its durability, as wolf armor does; iron on a Fledgling matches a wolf in wolf armor).
 */
public enum Barding {
    WOOD(0xa2834f, 24, 34), LEATHER(0x8b5a2b, 32, 45), STONE(0x8a8a8a, 48, 67), IRON(0xd6d9dc, 74, 104),
    GOLD(0xf5c542, 90, 126), DIAMOND(0x4fe0d8, 120, 169), NETHERITE(0x4a4347, 150, 211);

    /** The plate color when the barding has no dye. */
    public final int plate;
    public final int fledglingDurability, matureDurability;

    Barding(int plate, int fledgling, int mature) {
        this.plate = plate;
        this.fledglingDurability = fledgling;
        this.matureDurability = mature;
    }

    /** the Mature barding keeps the original ids; the Fledgling's are prefixed */
    public String id(int stage) {
        String base = name().toLowerCase(java.util.Locale.ROOT) + "_barding";
        return stage == 2 ? "fledgling_" + base : base;
    }

    public int durability(int stage) { return stage == 2 ? fledglingDurability : matureDurability; }

    /** Armor points while worn, as wolf armor has beside its soak (Shane, 2026-11-24: "if wolves get armor points drekki
     *  should too"): by durability against wolf armor's (64 durability, 11 armor), rounded, 3 at least, 20 at most; no
     *  toughness. Fledgling 4, 6, 8, 13, 15, 20, 20; Mature 6, 8, 12, 18, 20, 20, 20. */
    public static int armor(int durability) { return Math.max(3, Math.min(20, Math.round(durability * 11f / 64))); }

    /** the Gear.BARDING index (0 is none) */
    public int gearIndex() { return ordinal() + 1; }
}
