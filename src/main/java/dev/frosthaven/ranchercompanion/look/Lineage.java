package dev.frosthaven.ranchercompanion.look;

import java.util.List;

/**
 * One fur look: a bloodline at a purity tier (indexes 0..35, family * 4 + tier), or a rare line (36..55). New rare lines go at the end so saved drekki keep
 * their index.
 * Values come from the design page's bloodline tables.
 */
public record Lineage(int index, String name, int fur, int trim, int sheenHex, float sheen, int astral, List<Integer> particles, Sparkle sparkle) {
    /**
     * The page's sparkle() config for a rare line, in pixels at stage 3: count, spawn box (half width, bottom, height,
     * half length), rise, wander and burst speeds per second, life in seconds, cube size. blend: colors mix between
     * the first two (the page's c1, c2); else one is picked from the list (the page's palette). opacity and mode (fade, smoke,
     * burst, ember, twinkle, glitch: how each cube's size moves over its life) are the page's; tall stretches a quarter of
     * them three high, bars stretches a third of them three or six wide.
     */
    public record Sparkle(int n, float[] box, float rise, float wander, float burst, float life, float size, boolean blend, float opacity, String mode, boolean tall, boolean bars) {}

    public static final int FAMILIES = 9, TIERS = 4, BLOODLINES = FAMILIES * TIERS, SPECIALS = 23, COUNT = BLOODLINES + SPECIALS;
    public static final String[] TIER_NAMES = {"muted", "clear", "vivid", "pure"};
    private static final float[] TIER_SHEEN = {0f, 0f, 0f, 0f};  /* only rare and ultra rare lines glow */
    /** name, muted egg color, pure color, optional sheen color (else the pure fur itself). */
    private static final String[][] FAMILY = {
        {"Moss", "#7d8a5c", "#3cc45c"}, {"Slate", "#6b7a8c", "#2f6fe0"}, {"Rose", "#b58a88", "#d4262e"},
        {"Sand", "#c9b48a", "#f2c230"}, {"Ash", "#8c8a85", "#e9ebee", "#b9c0c9"}, {"Clay", "#a8725a", "#e8622a"},
        {"Lavender", "#9a8fa8", "#9b4ff0"}, {"Teal", "#6f8f8c", "#22c2b0"}, {"Soot", "#4b4e53", "#141418", "#7c86a8"}};
    private static Sparkle fx(int n, float bx, float by, float bh, float bz, float rise, float wander, float burst, float life, float size, boolean blend, float opacity, String mode, boolean tall, boolean bars) {
        return new Sparkle(n, new float[]{bx, by, bh, bz}, rise, wander, burst, life, size, blend, opacity, mode, tall, bars);
    }
    /** name, fur, sheen color, sheen (set so every line adds the same light: 0.06 / the glint's linear luminance), astral, particle colors, sparkle, optional trim color (else a lighter shade of the fur). The astral is a display default (showcase, page cards): a real child inherits its parents'. */
    private static final Object[][] SPECIAL = {
        {"Echo", "#021416", "#2fe0e8", 0.1f, Astral.LUNAR, new String[]{"#0f4a52", "#2fbac2"}, fx(48, 8, 1, 26, 13, 4, 1.8f, 0, 2.8f, 4, true, 0.6f, "smoke", false, false), "#2fe0e8"},
        {"Glowberry", "#1fbe66", "#5dff9f", 0.08f, Astral.SOLAR, new String[]{"#ffb43c", "#ffe08a"}, fx(30, 10, 2, 30, 15, 0, 0, 5, 1.0f, 3.4f, true, 1f, "burst", false, false), "#25db77"},
        {"Drowned", "#12204f", "#2d55e0", 0.49f, Astral.LUNAR, new String[]{"#5d7cff", "#ffffff"}, fx(34, 10, 6, 32, 16, -1.2f, 0.2f, 0, 3.2f, 2, true, 0.95f, "twinkle", true, false), "#192c6c"},
        {"Basalt", "#3a3b3f", "#ff8a2a", 0.15f, Astral.UNDERWORLD, new String[]{"#ff7a1a", "#ffd34a"}, fx(36, 8, 0, 24, 13, 9, 1.2f, 0, 1.4f, 2.4f, true, 1f, "ember", false, false), "#ff6a1a"},
        {"Pharaoh", "#ffc428", "#fff1b0", 0.07f, Astral.SOLAR, new String[]{"#fff3a0", "#ffb02a"}, fx(30, 9, 2, 30, 15, 3, 0.8f, 0, 1.6f, 2.6f, true, 1f, "ember", false, false), "#ffce4c"},
        {"Bosun", "#0f6f7a", "#36d6e0", 0.11f, Astral.AERO, new String[]{"#bff5ff", "#ffffff"}, fx(28, 9, 0, 28, 14, 4, 0.6f, 0, 1.8f, 2, true, 0.8f, "fade", false, false), "#138c9a"},
        {"Amethyst", "#5a1fb0", "#d9a8ff", 0.12f, Astral.DEEP, new String[]{"#e8d4ff", "#ffffff"}, fx(30, 9, 3, 30, 15, 0.8f, 0.4f, 0, 3, 2, true, 0.95f, "twinkle", false, false), "#6a24ce"},
        {"Purpur", "#eadff3", "#ffffff", 0.06f, Astral.AERO, new String[]{"#ff9ad5", "#9ad5ff", "#b8ffb0", "#fff3a0", "#d9b8ff"}, fx(30, 10, 2, 30, 15, 1.5f, 1, 0, 1.6f, 2.2f, false, 1f, "fade", false, false), "#a531ff"},
        {"Bakeneko", "#eceae4", "#bfe8ff", 0.08f, Astral.AERO, new String[]{"#e8f4ff", "#ffffff"}, fx(30, 8, 1, 26, 13, 2.5f, 1.5f, 0, 3, 3.6f, true, 0.5f, "smoke", false, false), "#3a3a3e"},
        {"Glitch", "#120a14", "#ff06b5", 0.24f, Astral.END, new String[]{"#ff06b5", "#ff2a2a", "#ff06b5"}, fx(26, 9, 2, 30, 15, 0, 0, 0, 0.5f, 3.2f, false, 1f, "glitch", false, true), "#ff06b5"},
        {"Weeping", "#2a0408", "#ff6a3a", 0.19f, Astral.LUNAR, new String[]{"#ff2a2a", "#ffb347"}, fx(34, 8, 0, 24, 13, 7, 1.2f, 0, 1.5f, 2.4f, true, 1f, "ember", false, false), "#ff3a22"},
        {"Railwright", "#b9c0c9", "#ffffff", 0.06f, Astral.SOLAR, new String[]{"#e08a4a", "#d9dde3", "#ffffff"}, fx(26, 9, 2, 28, 14, -2, 0.5f, 0, 1.4f, 1.8f, false, 1f, "fade", false, false), "#cdd2d9"},
        {"Idol", "#e8731e", "#ffc27a", 0.1f, Astral.SOLAR, new String[]{"#5f9e3a", "#8cc24a", "#3f7a2a"}, fx(26, 9, 2, 28, 14, -0.8f, 1, 0, 2.4f, 1.8f, false, 1f, "fade", false, false), "#eb873e"},
        {"Ruin", "#111015", "#3a3046", 1.97f, Astral.LUNAR, new String[]{"#ff3a1a", "#ffa02a"}, fx(24, 9, 2, 28, 14, 4, 0.6f, 0, 1.4f, 1.6f, true, 1f, "ember", false, false), "#1e1c22"},
        {"Sakura", "#f27aaa", "#ffd1e3", 0.08f, Astral.SOLAR, new String[]{"#ff8fbf", "#ffd1e3"}, fx(30, 10, 4, 30, 15, -1.2f, 1.2f, 0, 2.8f, 2, true, 1f, "fade", false, false), "#f59bbf"},
        {"Prismarine", "#0e3b38", "#7fe0cc", 0.1f, Astral.AERO, new String[]{"#7fd6c2", "#4fa89a", "#bfeee2"}, fx(28, 9, 2, 28, 14, 1, 0.6f, 0, 2, 2, false, 1f, "twinkle", false, false), "#2affd5"},
        {"Brew", "#8fd42a", "#c8ff6a", 0.07f, Astral.LUNAR, new String[]{"#c8ff6a", "#ffffff"}, fx(26, 9, 2, 28, 14, 3, 0.5f, 0, 1.2f, 1.8f, true, 0.9f, "fade", false, false), "#9fdb47"},
        {"Summit", "#8cc8f0", "#ffffff", 0.06f, Astral.AERO, new String[]{"#ffffff", "#e4f3ff"}, fx(26, 9, 2, 28, 14, 0.6f, 1.4f, 0, 3.2f, 3.6f, true, 0.5f, "smoke", false, false), "#acd7f4"},
        {"Prospector", "#c0392b", "#ff8a6a", 0.15f, Astral.SOLAR, new String[]{"#3a78b8", "#e0b040", "#3fa88a", "#c44a6a", "#f2e6d0"}, fx(28, 9, 2, 28, 14, -2.5f, 0.5f, 0, 1.2f, 1.4f, false, 1f, "fade", false, false), "#d34a3b"},
        {"Vault", "#763418", "#ff8a3a", 0.15f, Astral.SOLAR, new String[]{"#e08a4a", "#ffd34a", "#5fa88a"}, fx(26, 9, 2, 28, 14, -2.5f, 0.5f, 0, 1.2f, 1.2f, false, 1f, "fade", false, false), "#22c483"},
        {"Tome", "#1f3f9e", "#c46bff", 0.2f, Astral.LUNAR, new String[]{"#e8ddff", "#c46bff", "#8fb0ff"}, fx(24, 9, 2, 28, 14, 2, 0.8f, 0, 2.2f, 2, true, 1f, "twinkle", false, false), "#c46bff"},
        {"Deep", "#0a1850", "#2f7bff", 0.27f, Astral.DEEP, new String[]{"#2f7bff", "#7fb0ff", "#1a3fa8"}, fx(24, 9, 2, 28, 14, 0.6f, 0.5f, 0, 3, 2, true, 0.9f, "twinkle", false, false), "#2f7bff"},
        {"Bastion", "#1c1a1e", "#ffb020", 0.11f, Astral.UNDERWORLD, new String[]{"#ffd84a", "#ffb020", "#fff1a0"}, fx(26, 9, 2, 28, 14, -2, 0.5f, 0, 1.4f, 1.6f, false, 1f, "fade", false, false), "#ffc21a"}};

    public static Lineage of(int index) {
        int i = Math.floorMod(index, COUNT);
        if (i < BLOODLINES) {
            int fam = i / TIERS, tier = i % TIERS;
            String[] f = FAMILY[fam];
            int fur = tierColor(fam, tier);
            int sheenHex = f.length > 3 ? Palette.hex(f[3]) : fur;
            return new Lineage(i, f[0] + " " + TIER_NAMES[tier], fur, -1, sheenHex, TIER_SHEEN[tier], fam % 6 + 1, List.of(), null);
        }
        Object[] s = SPECIAL[i - BLOODLINES];
        String[] pc = (String[]) s[5];
        Integer[] parts = new Integer[pc.length];
        for (int j = 0; j < pc.length; j++) parts[j] = Palette.hex(pc[j]);
        return new Lineage(i, (String) s[0], Palette.hex((String) s[1]), s.length > 7 ? Palette.hex((String) s[7]) : -1, Palette.hex((String) s[2]), (Float) s[3], (Integer) s[4], List.of(parts), (Sparkle) s[6]);
    }

    /** The egg color climbs toward the pure color one third per tier. */
    public static int tierColor(int fam, int tier) {
        return Palette.mix(Palette.hex(FAMILY[fam][1]), Palette.hex(FAMILY[fam][2]), tier / 3.0);
    }

    public static String familyName(int fam) { return FAMILY[fam][0]; }

    /** the Bakeneko line's index: every pairing with a ghost parent gives it (spec, Breeding) */
    public static final int BAKENEKO = indexOf("Bakeneko");

    private static int indexOf(String name) {
        for (int i = 0; i < SPECIAL.length; i++) if (SPECIAL[i][0].equals(name)) return BLOODLINES + i;
        throw new IllegalStateException(name);
    }

    /** Bakeneko is the one see-through line: a steady 35% see-through (its barding stays solid). */
    public boolean seeThrough() { return "Bakeneko".equals(name); }

    public boolean rare() { return index >= BLOODLINES; }
}
