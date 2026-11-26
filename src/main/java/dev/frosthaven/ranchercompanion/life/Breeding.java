package dev.frosthaven.ranchercompanion.life;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The child two drekki give (spec, Breeding; decided with Shane 2026-11-26 for the first breeding pass). Pure: no Minecraft
 * types, so the rules are unit tested.
 * <ul>
 * <li>Fur: the same bloodline gives that bloodline one tier purer (pure stays pure). Mixed bloodlines give a random one of
 *     the nine at one tier below the lower parent, never below muted. Only the 36 bloodline colors exist.</li>
 * <li>Horns: the same bloodline gives the parents' horn when they share it, else one of the two; mixed gives a random one.</li>
 * <li>Eyes (astral; Shane, 2026-11-26): each parent's astral set is its one astral, or the two of a two-eyed drekki. The
 *     same set on both parents gives the child that set (a two-eyed pair passes both). Otherwise the child takes one
 *     astral, one of the parents' at random; with the Starlight Tonic ({@code twin}) it takes one of each parent's, one
 *     per eye, when they differ. Lit from the hatch, never Dormant; which eye gets which color is random.</li>
 * <li>Traits (Shane, 2026-11-26): up to {@link #KEEP}. The child's line's legendary trait first, when it has one (Spirit
 *     Veil for a Bakeneko), then the Heirloom Tonic's trait; then the pool, a copy of each trait per parent that holds it,
 *     shuffled and rolled one by one at 1/3 plus the pair's mood score (the average of both parents': Miserable -10
 *     points, Uneasy -5, Content +5, Happy +10, Thriving +15) until the slots are full. A trait both hold is in the
 *     pool twice, so it has two rolls; holding it twice guarantees nothing.</li>
 * </ul>
 * Rare lines (Shane, 2026-11-26): a pairing with a ghost parent always gives a Bakeneko; two parents of the same rare
 * line give that line. A rare parent with any other counts as a pure parent of no bloodline (a mixed pairing).
 */
public final class Breeding {
    private Breeding() {}

    public static final int FAMILIES = 9, TIERS = 4, BLOODLINES = FAMILIES * TIERS, HORNS = 5, KEEP = 5;
    public static final double ONE = 1 / 3.0, BOTH = 1 - (2 / 3.0) * (2 / 3.0);

    /** the random numbers the rules draw: an int in [0, n) and a double in [0, 1) */
    public interface Rng {
        int below(int n);
        double unit();
    }

    /** astral: the first eye's (left), astral2: the second eye's, 0 when both eyes share the first */
    public record Child(int line, int horn, List<String> traits, int astral, int astral2) {}

    /** a drekki's astral set: one astral, or two for a two-eyed one */
    public static int[] set(int astral, int astral2) { return astral2 == 0 || astral2 == astral ? new int[]{astral} : new int[]{astral, astral2}; }

    private static boolean sameSet(int[] a, int[] b) {
        return a.length == b.length && (a.length == 1 ? a[0] == b[0] : (a[0] == b[0] && a[1] == b[1]) || (a[0] == b[1] && a[1] == b[0]));
    }

    /** the child's eyes: one astral, or two (one per eye, sides at random) */
    public static int[] eyes(int[] a, int[] b, boolean twin, Rng rng) {
        if (sameSet(a, b)) return a.length == 2 && rng.below(2) == 1 ? new int[]{a[1], a[0]} : a.clone();
        if (twin) {
            List<int[]> pairs = new ArrayList<>();
            for (int x : a) for (int y : b) if (x != y) pairs.add(new int[]{x, y});
            if (!pairs.isEmpty()) {
                int[] p = pairs.get(rng.below(pairs.size()));
                return rng.below(2) == 0 ? p : new int[]{p[1], p[0]};
            }
        }
        int x = a[rng.below(a.length)], y = b[rng.below(b.length)];
        return new int[]{rng.below(2) == 0 ? x : y};
    }

    /** the eyes a pairing may give, for the preview: each possible set once (one astral, or a pair in either order once) */
    public static List<int[]> possibleEyes(int[] a, int[] b, boolean twin) {
        List<int[]> out = new ArrayList<>();
        if (sameSet(a, b)) { out.add(a.clone()); return out; }
        if (twin) {
            for (int x : a) for (int y : b) if (x != y && out.stream().noneMatch(p -> sameSet(p, new int[]{x, y}))) out.add(new int[]{x, y});
            if (!out.isEmpty()) return out;
        }
        for (int x : a) if (out.stream().noneMatch(p -> p[0] == x)) out.add(new int[]{x});
        for (int y : b) if (out.stream().noneMatch(p -> p[0] == y)) out.add(new int[]{y});
        return out;
    }

    /** the bloodline of a line, or -1 for a rare line */
    public static int family(int line) { return line >= 0 && line < BLOODLINES ? line / TIERS : -1; }

    /** a line's purity tier; a rare line counts as pure */
    public static int tier(int line) { return line >= 0 && line < BLOODLINES ? line % TIERS : TIERS - 1; }

    public static boolean sameLine(int a, int b) { return family(a) >= 0 && family(a) == family(b); }

    /** the child's tier: one up for the same bloodline (capped at pure), one down for mixed (floored at muted) */
    public static int childTier(int a, int b) {
        int low = Math.min(tier(a), tier(b));
        return sameLine(a, b) ? Math.min(TIERS - 1, low + 1) : Math.max(0, low - 1);
    }

    /** a parent's mood band (Life.Band ordinal: Miserable 0 .. Thriving 4; -1 none) as points on its traits' chance */
    public static double moodBonus(int band) {
        return band < 0 ? 0 : band == 0 ? -0.10 : band == 1 ? -0.05 : 0.05 * (band - 1);
    }

    /** one parent of a pairing: its look, traits, astral set and mood band */
    public record Parent(int line, int horn, List<String> traits, int[] eyes, int band, boolean ghost) {
        public Parent(int line, int horn, List<String> traits, int[] eyes, int band) { this(line, horn, traits, eyes, band, false); }
    }

    /** the Bakeneko line: a pairing with a ghost parent always gives it (Shane, 2026-11-26); set by the game (Lineage) */
    public static int bakeneko = -1;

    /** a line's legendary trait, guaranteed to its children; null for none */
    public static String legendary(int line) { return line >= 0 && line == bakeneko ? "spirit_veil" : null; }

    /** each trait either parent holds, with its chance to pass, in the parents' order (no mood) */
    public static Map<String, Double> odds(List<String> a, List<String> b) { return odds(a, b, -1, -1, null); }

    /** the pair's mood score: the average of both parents' bonuses; a parent with no mood (a ghost, -1) leaves the other's alone */
    public static double moodScore(int bandA, int bandB) {
        if (bandA < 0) return moodBonus(bandB);
        if (bandB < 0) return moodBonus(bandA);
        return (moodBonus(bandA) + moodBonus(bandB)) / 2;
    }

    /** each trait either parent holds, with its chance to pass by the pair's mood score; the kept one (Heirloom) at 1 */
    public static Map<String, Double> odds(List<String> a, List<String> b, int bandA, int bandB, String keep) {
        double p = clamp(ONE + moodScore(bandA, bandB));
        Map<String, Double> out = new LinkedHashMap<>();
        for (String t : a) out.put(t, b.contains(t) ? 1 - (1 - p) * (1 - p) : p);
        for (String t : b) out.putIfAbsent(t, p);
        if (keep != null && out.containsKey(keep)) out.put(keep, 1.0);
        return out;
    }

    private static double clamp(double p) { return Math.max(0, Math.min(1, p)); }

    /** two Solar parents of no mood, no tonic (the tests that are about fur and traits) */
    public static Child child(int lineA, int hornA, List<String> traitsA, int lineB, int hornB, List<String> traitsB, Rng rng) {
        return child(new Parent(lineA, hornA, traitsA, new int[]{1}, -1), new Parent(lineB, hornB, traitsB, new int[]{1}, -1), false, null, rng);
    }

    /** twin: the Starlight Tonic; keep: the Heirloom Tonic's trait, or null */
    public static Child child(Parent pa, Parent pb, boolean twin, String keep, Rng rng) {
        int lineA = pa.line(), lineB = pb.line(), hornA = pa.horn(), hornB = pb.horn();
        int tier = childTier(lineA, lineB);
        int line, horn;
        if ((pa.ghost() || pb.ghost()) && bakeneko >= 0) {  /* a ghost parent: always a Bakeneko, horns at random */
            line = bakeneko;
            horn = rng.below(HORNS);
        } else if (lineA == lineB && family(lineA) < 0) {  /* two of the same rare line: that line (Shane, 2026-11-26) */
            line = lineA;
            horn = hornA == hornB ? hornA : rng.below(2) == 0 ? hornA : hornB;
        } else if (sameLine(lineA, lineB)) {
            line = family(lineA) * TIERS + tier;
            horn = hornA == hornB ? hornA : rng.below(2) == 0 ? hornA : hornB;
        } else {
            line = rng.below(FAMILIES) * TIERS + tier;
            horn = rng.below(HORNS);
        }
        List<String> passed = new ArrayList<>();
        String leg = legendary(line);
        if (leg != null) passed.add(leg);
        if (keep != null && !passed.contains(keep) && (pa.traits().contains(keep) || pb.traits().contains(keep))) passed.add(keep);
        List<String> pool = new ArrayList<>(pa.traits());
        pool.addAll(pb.traits());
        for (int i = pool.size() - 1; i > 0; i--) { int j = rng.below(i + 1); String x = pool.get(i); pool.set(i, pool.get(j)); pool.set(j, x); }
        double p = clamp(ONE + moodScore(pa.band(), pb.band()));
        for (String t : pool) {
            if (passed.size() >= KEEP) break;
            if (!passed.contains(t) && rng.unit() < p) passed.add(t);
        }
        int[] e = eyes(pa.eyes(), pb.eyes(), twin, rng);
        return new Child(line, horn, passed, e[0], e.length > 1 ? e[1] : 0);
    }
}
