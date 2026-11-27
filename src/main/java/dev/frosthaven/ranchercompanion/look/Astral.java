package dev.frosthaven.ranchercompanion.look;

/** Eye colors by astral affinity, as the design page draws them. Index 0 is Dormant: matte, no glow. */
public final class Astral {
    private Astral() {}
    public static final int DORMANT = 0, SOLAR = 1, LUNAR = 2, AERO = 3, UNDERWORLD = 4, DEEP = 5, END = 6;

    /** Lunar, Underworld and Deep eyes are nocturnal: they sleep by day and are about at night (Shane, 2026-11-27) */
    public static boolean nocturnal(int a) { return a == LUNAR || a == UNDERWORLD || a == DEEP; }

    /** time to sleep in this level for a drekki whose first eye is this astral: night, or day for a nocturnal one;
     *  never in a level without day and night (the Nether, the End) */
    public static boolean restTime(int a, net.minecraft.world.level.Level level) { return nocturnal(a) ? level.isDay() : level.isNight(); }
    public static final String[] NAMES = {"Dormant", "Solar", "Lunar", "Aero", "Underworld", "Deep", "End"};
    private static final int[] IRIS = {0x6e4b2c, 0xffb13b, 0x4ff0ff, 0xa3abb4, 0xff3a2e, 0x4ee35c, 0xb65cff};

    public static int iris(int astral) { return IRIS[Math.floorMod(astral, IRIS.length)]; }
    public static boolean glows(int astral) { return Math.floorMod(astral, IRIS.length) != DORMANT; }
}
