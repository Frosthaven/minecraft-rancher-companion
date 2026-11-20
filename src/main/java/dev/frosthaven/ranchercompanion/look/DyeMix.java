package dev.frosthaven.ranchercompanion.look;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-color dyeing (spec, Look: Dyeing), as Sophisticated Backpacks dyes a backpack: in the crafting grid only the
 * column counts. Dyes left of the item color its main part, dyes right of it its trim, dyes in its column both. Each side
 * blends as vanilla blends leather: the average of the dyes and the side's current color (one sample, however it was
 * made; an undyed side does not count), brightened back to the average of their brightest channels. Pure: colors are
 * 0xRRGGBB ints, -1 for undyed.
 */
public final class DyeMix {
    private DyeMix() {}

    /** the two dye lists of a grid: main (columns left of and at the item) and trim (at and right of it) */
    public record Split(List<Integer> main, List<Integer> trim) {}

    /** dyes as {column, color}; itemColumn is the dyed item's column */
    public static Split split(List<int[]> dyes, int itemColumn) {
        List<Integer> main = new ArrayList<>(), trim = new ArrayList<>();
        for (int[] d : dyes) {
            if (d[0] <= itemColumn) main.add(d[1]);
            if (d[0] >= itemColumn) trim.add(d[1]);
        }
        return new Split(main, trim);
    }

    /** a side's new color: current (-1 undyed) blended with the dyes; unchanged when there are none */
    public static int mix(int current, List<Integer> dyes) {
        if (dyes.isEmpty()) return current;
        int r = 0, g = 0, b = 0, bright = 0, n = 0;
        if (current >= 0) {
            int cr = current >> 16 & 255, cg = current >> 8 & 255, cb = current & 255;
            bright += Math.max(cr, Math.max(cg, cb)); r += cr; g += cg; b += cb; n++;
        }
        for (int c : dyes) {
            int dr = c >> 16 & 255, dg = c >> 8 & 255, db = c & 255;
            bright += Math.max(dr, Math.max(dg, db)); r += dr; g += dg; b += db; n++;
        }
        int ar = r / n, ag = g / n, ab = b / n;
        float avgBright = (float) bright / n, top = Math.max(ar, Math.max(ag, ab));
        ar = (int) (ar * avgBright / top); ag = (int) (ag * avgBright / top); ab = (int) (ab * avgBright / top);
        return ar << 16 | ag << 8 | ab;
    }
}
