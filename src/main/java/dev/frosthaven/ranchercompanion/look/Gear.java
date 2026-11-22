package dev.frosthaven.ranchercompanion.look;

/**
 * What a drekki can wear, as the design page lists it: hats (index 0 is none), barding tiers (index 0 is none) with
 * their plate and rim colors, and the game's 16 dyes at their leather-armor tint. Pure data, no Minecraft types.
 */
public final class Gear {
    private Gear() {}

    /** The page's HATS order; index 0 is no hat. */
    public static final String[] HATS = {"None", "Top hat", "Drinking hat", "Viking helmet", "Wizard hat", "Straw hat", "Crown",
        "Party cone", "Bucket", "Cowboy hat", "Chef toque", "Miner helmet", "Pirate tricorne", "Propeller beanie", "Flower crown", "Jester cap",
        "Conductor's cap", "Stock keeper's cap", "Aviator cap", "Satellite hat"};

    /** The page's BARDING order by armor; index 0 is none. Material names pick the texture pattern. */
    public static final String[] BARDING = {"none", "wood", "leather", "stone", "iron", "gold", "diamond", "netherite"};
    public static final int[] BARDING_PLATE = {0, 0xa2834f, 0x8b5a2b, 0x8a8a8a, 0xd6d9dc, 0xf5c542, 0x4fe0d8, 0x4a4347};
    public static final int[] BARDING_RIM = {0, 0x6b5232, 0x5e3a1a, 0x5a5a5a, 0x8e9398, 0xb8892a, 0x2a9c96, 0x2a2628};
    public static final int[] BARDING_ARMOR = {0, 2, 3, 4, 5, 7, 11, 13};

    /** The 16 dyes in the game's order (white first), at their leather-armor tint. */
    public static final int[] DYES = {0xf9fffe, 0xf9801d, 0xc74ebd, 0x3ab3da, 0xfed83d, 0x80c71f, 0xf38baa, 0x474f52,
        0x9d9d97, 0x169c9c, 0x8932b8, 0x3c44aa, 0x835432, 0x5e7c16, 0xb02e26, 0x1d1d21};

    /** Plate and rim colors for a barding tier: each side's own dye (an RGB color, or -1 for none), else the tier's color. */
    public static int plate(int tier, int dye) { return dye >= 0 ? dye : BARDING_PLATE[tier]; }
    public static int rim(int tier, int trimDye) { return trimDye >= 0 ? trimDye : BARDING_RIM[tier]; }

    /** The page's BAGS order (spec, Accessory: bags); index 0 is none. Slots, the first stage that wears it, and the
     *  metal (clasps, tier strips) that shows the tier and never takes dye. */
    public static final String[] BAGS = {"none", "satchel", "saddlebag", "iron_saddlebag", "gold_saddlebag", "diamond_saddlebag", "netherite_saddlebag"};
    public static final int[] BAG_SLOTS = {0, 9, 27, 36, 45, 54, 63};
    public static final int[] BAG_STAGE = {0, 2, 3, 3, 3, 3, 3};
    public static final int[] BAG_METAL = {0, 0xc9a63a, 0xb8743a, 0xd6d9dc, 0xf5c542, 0x4fe0d8, 0x4a4347};
    /** upgrade slots with Sophisticated Backpacks installed. Both counts stay under Sophisticated's own backpack of the
     *  same tier (satchel: leather, saddlebag: copper, then iron to netherite; its defaults 27+1, 45+1, 54+2, 81+3,
     *  108+5, 120+7), so a bag never outdoes the backpack it sits beside */
    public static final int[] BAG_UPGRADES = {0, 1, 1, 2, 3, 4, 6};
    /** the bags' undyed leather: main (the bags) and trim (flaps and strap) */
    public static final int BAG_LEATHER = 0x8b5a2b, BAG_LEATHER_TRIM = 0x5e3a1a;

    public static int bagMain(int dye) { return dye >= 0 ? dye : BAG_LEATHER; }
    public static int bagTrim(int dye) { return dye >= 0 ? dye : BAG_LEATHER_TRIM; }
}
