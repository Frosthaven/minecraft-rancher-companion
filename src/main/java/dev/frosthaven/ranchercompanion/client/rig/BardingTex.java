package dev.frosthaven.ranchercompanion.client.rig;

import dev.frosthaven.ranchercompanion.look.Gear;
import dev.frosthaven.ranchercompanion.look.Palette;

/**
 * The design page's barding textures (bardTex): each tier's own surface, wood grain, cobble, hide, brushed metal,
 * facets, scratched netherite, painted in the plate's color, so a dye changes the color and keeps the material.
 * Same seed and same random draws as the page.
 */
final class BardingTex {
    private BardingTex() {}

    static int[] tile(int color, int tier) {
        String kind = Gear.BARDING[tier];
        int[] t = new int[256], seed = {color ^ (kind.length() * 7919)};
        int r = Palette.r(color), g = Palette.g(color), b = Palette.b(color);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double n = RigBake.rnd(seed) - 0.5, j;
            switch (kind) {
                case "wood" -> j = (y % 4 == 0 ? -16 : 0) + (y % 4 == 2 ? 6 : 0) + n * 8 + ((x * 3 + y * 5) % 11 == 0 ? -10 : 0);  /* plank seams every four rows */
                case "stone" -> j = n * 26 + (RigBake.rnd(seed) < 0.14 ? -26 : 0) + ((((x >> 2) + (y >> 2)) % 2) != 0 ? 6 : -4);  /* cobble with dark cracks */
                case "leather" -> j = n * 10 + (RigBake.rnd(seed) < 0.06 ? -10 : 0);  /* soft hide */
                case "diamond" -> j = (((x + y) % 8) < 4 ? 12 : -8) + n * 6 + (RigBake.rnd(seed) < 0.04 ? 34 : 0);  /* facets with sparkles */
                case "netherite" -> j = n * 8 + (RigBake.rnd(seed) < 0.09 ? 14 : 0) + (y % 5 == 0 ? -6 : 0);  /* dark, fine scratches */
                default -> j = n * 6 + (y == 2 || y == 3 ? 18 : 0) + (y == 13 ? -12 : 0) + (RigBake.rnd(seed) < 0.05 ? 24 : 0);  /* iron, gold: brushed metal */
            }
            t[y * 16 + x] = RigBake.abgr(Palette.rgb(r + j, g + j, b + j));
        }
        return t;
    }
}
