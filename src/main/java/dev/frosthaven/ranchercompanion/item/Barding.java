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

    /** the Gear.BARDING index (0 is none) */
    public int gearIndex() { return ordinal() + 1; }
}
