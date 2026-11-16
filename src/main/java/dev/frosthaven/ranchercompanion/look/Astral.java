package dev.frosthaven.ranchercompanion.look;

/** Eye colors by astral affinity, as the design page draws them. Index 0 is Dormant: matte, no glow. */
public final class Astral {
    private Astral() {}
    public static final int DORMANT = 0, SOLAR = 1, LUNAR = 2, AERO = 3, UNDERWORLD = 4, DEEP = 5, END = 6;
    public static final String[] NAMES = {"Dormant", "Solar", "Lunar", "Aero", "Underworld", "Deep", "End"};
    private static final int[] IRIS = {0x6e4b2c, 0xffb13b, 0x4ff0ff, 0xa3abb4, 0xff3a2e, 0x4ee35c, 0xb65cff};

    public static int iris(int astral) { return IRIS[Math.floorMod(astral, IRIS.length)]; }
    public static boolean glows(int astral) { return Math.floorMod(astral, IRIS.length) != DORMANT; }
}
