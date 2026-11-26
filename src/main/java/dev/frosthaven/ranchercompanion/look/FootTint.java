package dev.frosthaven.ranchercompanion.look;

/**
 * The foot tint a drekki is born with (spec, Biome affinity: the foot tint is set at birth): one of the design page's
 * biome tints for each biome category, in the order of life.Places.CATEGORIES. It colors the feet, from stage 2 the
 * chest patch and the tail ring, and on a Mature the base of each horn. A look only.
 */
public final class FootTint {
    private FootTint() {}

    public static final int PLAINS = 1;
    private static final int SNOW = 0xd6dde0, GRASS = 0x8a9a6a, DESERT = 0xc9b88a, FOREST = 0x5f7a55, JUNGLE = 0x4a7a5c, SWAMP = 0x6a7a66,
        MOUNTAINS = 0x8d9196, OCEAN = 0x6a8a9c, UNDERGROUND = 0x5f6268, NETHER = 0x9a5a54, END = 0xa99bb8;
    /** snow_ice, plains, dry, forest, taiga, jungle, wet, hills_mountains, water_coast, ocean, cave, nether_forest, nether_barren, end, end_exploration */
    private static final int[] TINTS = {SNOW, GRASS, DESERT, FOREST, FOREST, JUNGLE, SWAMP, MOUNTAINS, OCEAN, OCEAN, UNDERGROUND, NETHER, NETHER, END, END};

    /** dark charcoal, almost black: every drekki's feet, legs, chest patch, tail ring and horn bases (Shane, 2026-11-26:
     *  "always and forever for now"); the birth biome is still kept, for a later return of the biome tints */
    public static final int CHARCOAL = 0x26262a;

    public static int of(int category) { return CHARCOAL; }

    /** the biome's own tint (not drawn since 2026-11-26) */
    public static int biome(int category) { return TINTS[category < 0 || category >= TINTS.length ? PLAINS : category]; }
}
