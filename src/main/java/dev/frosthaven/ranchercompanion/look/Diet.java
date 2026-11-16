package dev.frosthaven.ranchercompanion.look;

/** The four diets: index, family and whether the horns turn ebony (the magical diets). */
public enum Diet {
    RAW("Raw", true, false), COOKED("Cooked", true, true), SEED("Seed", false, false), ROOT("Root", false, true);

    public final String label;
    public final boolean carnivore, ebony;

    Diet(String label, boolean carnivore, boolean ebony) { this.label = label; this.carnivore = carnivore; this.ebony = ebony; }

    public static Diet of(int i) { return values()[Math.floorMod(i, 4)]; }
}
