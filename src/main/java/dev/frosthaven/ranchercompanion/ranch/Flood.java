package dev.frosthaven.ranchercompanion.ranch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks out from a hatchery over the spots a drekki can stand on, one step at a time (spec, Placement): a step goes to a
 * side neighbor one block up, level or one block down, and only when both spots can be stood on, so a fence, wall or
 * closed gate stops it. No Minecraft types: the world comes in through {@link Grid}, so it is unit tested.
 */
public final class Flood {
    private Flood() {}

    /** the world as the walk sees it; a spot is the block a drekki's feet are in */
    public interface Grid {
        /** solid ground under it (top at most one block high, so not a fence or wall), and nothing to collide with in it and above it */
        boolean stand(int x, int y, int z);
        /** room to step up into this spot from below (the block over the lower spot's head is clear) */
        default boolean headroom(int x, int y, int z) { return true; }
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int CLIMB = 1;

    public static long pack(int x, int y, int z) { return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF); }
    public static int x(long p) { return (int) (p >> 38); }
    public static int y(long p) { return (int) (p << 52 >> 52); }
    public static int z(long p) { return (int) (p << 26 >> 38); }

    /**
     * Every spot within {@code radius} blocks of (hx, hz) (square, the hatchery's own column left out) and within
     * {@code up} blocks up or down of hy that the walk reaches from the hatchery's sides, nearest walk first.
     * {@code inside} (may be null) limits which spots the walk may enter.
     */
    public static List<long[]> walk(Grid g, int hx, int hy, int hz, int radius, int up, java.util.function.LongPredicate inside) {
        List<long[]> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<long[]> q = new ArrayDeque<>();
        for (int[] s : SIDES) for (int dy = CLIMB; dy >= -CLIMB; dy--) {  /* from the hatchery's own spot, as a drekki on its sides */
            int x = hx + s[0], y = hy + dy, z = hz + s[1];
            if (g.stand(x, y, z) && (inside == null || inside.test(pack(x, y, z))) && seen.add(pack(x, y, z))) { q.add(new long[]{pack(x, y, z), 1}); break; }
        }
        while (!q.isEmpty()) {
            long[] cur = q.poll();
            out.add(cur);
            int x = x(cur[0]), y = y(cur[0]), z = z(cur[0]);
            for (int[] s : SIDES) for (int dy = CLIMB; dy >= -CLIMB; dy--) {
                int nx = x + s[0], ny = y + dy, nz = z + s[1];
                if (Math.max(Math.abs(nx - hx), Math.abs(nz - hz)) > radius || Math.abs(ny - hy) > up || (nx == hx && nz == hz)) continue;
                long p = pack(nx, ny, nz);
                if (seen.contains(p) || !g.stand(nx, ny, nz) || (inside != null && !inside.test(p))) continue;
                if (dy > 0 && !g.headroom(x, y + 1, z)) continue;
                if (dy < 0 && !g.headroom(nx, ny + 1, nz)) continue;
                seen.add(p);
                q.add(new long[]{p, cur[1] + 1});
                break;  /* one spot per column step: the highest one it can reach */
            }
        }
        return out;
    }

    /**
     * Where a drekki appears (spec, Placement): the first spot that fits on the walk, nearest walk first; then, when none
     * does, the spots the walk did not reach, ring by ring. -1 when nothing within the radius fits.
     */
    public static long place(Grid g, int hx, int hy, int hz, int radius, java.util.function.LongPredicate fits) {
        List<long[]> walked = walk(g, hx, hy, hz, radius, radius, null);
        Set<Long> reached = new HashSet<>();
        for (long[] w : walked) { reached.add(w[0]); if (fits.test(w[0])) return w[0]; }
        for (int r = 1; r <= radius; r++)
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
                    long p = pack(hx + dx, hy + dy, hz + dz);
                    if (!reached.contains(p) && g.stand(hx + dx, hy + dy, hz + dz) && fits.test(p)) return p;
                }
            }
        return -1;
    }
}
