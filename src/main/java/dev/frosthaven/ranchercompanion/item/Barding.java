package dev.frosthaven.ranchercompanion.item;

/** The seven barding tiers, in display order, with each plate's undyed color (0xRRGGBB). */
public enum Barding {
    WOOD(0xa2834f), LEATHER(0x8b5a2b), STONE(0x8a8a8a), IRON(0xd6d9dc), GOLD(0xf5c542), DIAMOND(0x4fe0d8), NETHERITE(0x4a4347);

    /** The plate color when the barding has no dye. */
    public final int plate;

    Barding(int plate) {
        this.plate = plate;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT) + "_barding";
    }
}
