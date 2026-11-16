package dev.frosthaven.ranchercompanion.look;

/**
 * What a drekki can wear, as the design page lists it: hats (index 0 is none), barding tiers (index 0 is none) with
 * their plate and rim colors, and the game's 16 dyes at their leather-armor tint. Pure data, no Minecraft types.
 */
public final class Gear {
    private Gear() {}

    /** The page's HATS order; index 0 is no hat. */
    public static final String[] HATS = {"None", "Top hat", "Drinking hat", "Viking helmet", "Wizard hat", "Straw hat", "Crown",
        "Party cone", "Bucket", "Cowboy hat", "Chef toque", "Miner helmet", "Pirate tricorne", "Propeller beanie", "Flower crown", "Jester cap"};

    /** The page's BARDING order by armor; index 0 is none. Material names pick the texture pattern. */
    public static final String[] BARDING = {"none", "wood", "leather", "stone", "iron", "gold", "diamond", "netherite"};
    public static final int[] BARDING_PLATE = {0, 0xa2834f, 0x8b5a2b, 0x8a8a8a, 0xd6d9dc, 0xf5c542, 0x4fe0d8, 0x4a4347};
    public static final int[] BARDING_RIM = {0, 0x6b5232, 0x5e3a1a, 0x5a5a5a, 0x8e9398, 0xb8892a, 0x2a9c96, 0x2a2628};
    public static final int[] BARDING_ARMOR = {0, 2, 3, 4, 5, 7, 11, 13};

    /** The 16 dyes in the game's order (white first), at their leather-armor tint. */
    public static final int[] DYES = {0xf9fffe, 0xf9801d, 0xc74ebd, 0x3ab3da, 0xfed83d, 0x80c71f, 0xf38baa, 0x474f52,
        0x9d9d97, 0x169c9c, 0x8932b8, 0x3c44aa, 0x835432, 0x5e7c16, 0xb02e26, 0x1d1d21};

    /** Plate and rim colors for a barding tier, dyed or not: a dye (an RGB color, or -1 for none) becomes the plate color. */
    public static int plate(int tier, int dye) { return dye >= 0 ? dye : BARDING_PLATE[tier]; }
    public static int rim(int tier, int dye) { return dye >= 0 ? Palette.shade(dye, -.3) : BARDING_RIM[tier]; }
}
