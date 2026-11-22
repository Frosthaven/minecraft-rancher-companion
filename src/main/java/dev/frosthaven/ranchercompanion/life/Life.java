package dev.frosthaven.ranchercompanion.life;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * A drekki's life as numbers (spec, Growth release): travel, the astral and diet counts, biome time, food, mood, the
 * petting buff, traits and their learning progress, ghost and stance. Pure: no Minecraft types, so the rules are unit
 * tested; {@code Vitals} applies them to the entity and {@code LifeTag} saves them.
 */
public final class Life {
    public static final int DAY = 24000, FOOD_MAX = 10, MOOD_START = 50, KIN_MAX = 9;
    public static final double KIN_FULL = 25;
    /** Recently pet lasts an hour of real time (spec, Mood) */
    public static final long PET_MILLIS = 3_600_000L;

    /** ticks out in the world, checked out and out of a carrier: the growth clock */
    public long travel;
    /** stage 1 travel ticks by astral place (Astral indexes; 0 unused) until the lock */
    public final long[] astral = new long[7];
    /** meals by Diet index while the diet is open */
    public final int[] meals = new int[4];
    /** stage 2 travel ticks by biome category id, for the later lock-in */
    public final Map<String, Long> biome = new HashMap<>();
    public double food = FOOD_MAX, mood = MOOD_START;
    /** the world's game time this was last brought up to date; NEVER before its first update */
    public static final long NEVER = Long.MIN_VALUE;
    public long stamp = NEVER;
    /** Recently pet until this epoch millisecond */
    public long petUntil;
    public final List<String> traits = new ArrayList<>();
    /** counted activities started, by trait id */
    public final Map<String, Integer> progress = new HashMap<>();
    /** the trait it wants to learn, waiting for its owner's yes or no on the drekki screen, or null (one at a time) */
    public String offer;
    public boolean ghost;
    /** trait cooldowns and timers: the game time each is ready again, by trait id (saved) */
    public final Map<String, Long> cooldowns = new HashMap<>();
    /** health owed by regeneration, paid a whole point at a time (not saved) */
    public double regen;
    public Stance stance = Stance.REACTIVE;

    public enum Stance { AGGRESSIVE, REACTIVE, PASSIVE }

    public enum Band {
        MISERABLE(0), UNEASY(25), CONTENT(50), HAPPY(75), THRIVING(85);
        public final int from;
        Band(int from) { this.from = from; }
    }

    // ---- growth ----

    /** the travel ticks a stage lasts */
    public static long stageTicks(double days) { return Math.round(days * DAY); }

    /** the stage this much travel reaches, capped (spec: this release stops at stage 2) */
    public static int stageFor(long travel, double days, int cap) {
        long per = Math.max(1, stageTicks(days));
        return (int) Math.min(cap, 1 + travel / per);
    }

    /** the largest count wins, a tie settled at random among the tied (rnd: n to a random index below n); null when nothing counted */
    public static Integer pick(long[] counts, int from, IntUnaryOperator rnd) {
        long best = 0; List<Integer> tied = new ArrayList<>();
        for (int i = from; i < counts.length; i++) {
            if (counts[i] > best) { best = counts[i]; tied.clear(); tied.add(i); }
            else if (counts[i] == best && best > 0) tied.add(i);
        }
        return tied.isEmpty() ? null : tied.get(rnd.applyAsInt(tied.size()));
    }

    public static Integer pick(int[] counts, IntUnaryOperator rnd) {
        long[] l = new long[counts.length];
        for (int i = 0; i < counts.length; i++) l[i] = counts[i];
        return pick(l, 0, rnd);
    }

    // ---- hunger ----

    /** shanks lost over this many ticks at a rate per day */
    public static double drain(long ticks, double perDay) { return Math.max(0, ticks) * perDay / DAY; }

    public boolean hungry() { return food <= 0; }
    /** the food as the shank bar shows it: rounded up to half a shank, so an empty shank shows only once a whole shank
     *  is missing, when it eats at a trough ({@link #wantsFood}) */
    public static float shownFood(double food) { return (float) Math.min(FOOD_MAX, Math.ceil(food * 2 - 1e-9) / 2.0); }

    /** saturation in shanks, never above {@link #food}: hidden, spent before the shanks, as a player's */
    public double sat;

    /** a meal of this many shanks and this much saturation; what does not fit is lost */
    public void eat(double shanks, double saturation) {
        food = Math.min(FOOD_MAX, food + shanks);
        sat = Math.min(food, sat + saturation);
    }

    /** hunger spent: saturation first, then the shanks */
    public void spend(double shanks) {
        double s = Math.min(sat, shanks);
        sat -= s;
        food = Math.max(0, food - (shanks - s));
    }

    /** fed healing, as a player's on a 20 point bar (a shank is 2 points): at 9 shanks or more a hurt drekki heals
     *  1 health every 4 s; with a full bar and saturation, 1 every half second. Each health costs 0.75 of a shank
     *  (a player's 6 exhaustion), and the fast heal pays only from saturation */
    public static final double FED = 9, HEAL_COST = 0.75;
    public static final int HEAL_TICKS = 80, FAST_TICKS = 10;
    /** ticks toward the next fed heal (not saved) */
    public long fed;

    /** the health these ticks of being fed give a drekki missing this much; takes the food it costs */
    public float fedHeal(long ticks, float missing) {
        if (ghost || food < FED || missing <= 0) { fed = 0; return 0; }
        fed += ticks;
        float n = 0;
        while (n < missing && food >= FED) {
            boolean fast = food >= FOOD_MAX && sat > 0;
            int every = fast ? FAST_TICKS : HEAL_TICKS;
            if (fed < every) break;
            fed -= every;
            double cost = fast ? Math.min(sat, HEAL_COST) : HEAL_COST;
            spend(cost);
            n += (float) (cost / HEAL_COST);
        }
        return Math.min(n, missing);
    }

    /** room for a whole shank: it eats */
    public boolean wantsFood() { return food <= FOOD_MAX - 1; }

    // ---- mood ----

    public boolean petted(long nowMillis) { return nowMillis < petUntil; }

    /** Kinship: 25/9 for each other drekki of its hatchery, up to 9, so nine kin hold it at 25 (never Miserable) */
    public static double kinBonus(int kin) { return Math.min(KIN_MAX, Math.max(0, kin)) * KIN_FULL / KIN_MAX; }

    /** the mood its bands read: its own plus Kinship, 0 to 100 */
    public static double effective(double mood, int kin) {
        return Math.max(0, Math.min(100, mood + kinBonus(kin)));
    }

    /** the plain band, then Recently pet lifts it one band for the hour. Thriving is only ever a pet's lift, and only
     *  from 85 with above half food; a Happy drekki short of that stays Happy (spec, Mood) */
    public static Band band(double effective, boolean petted, double food) {
        Band plain = effective >= Band.HAPPY.from ? Band.HAPPY : effective >= Band.CONTENT.from ? Band.CONTENT
            : effective >= Band.UNEASY.from ? Band.UNEASY : Band.MISERABLE;
        if (!petted) return plain;
        if (plain != Band.HAPPY) return Band.values()[plain.ordinal() + 1];
        return effective >= Band.THRIVING.from && food > FOOD_MAX / 2.0 ? Band.THRIVING : Band.HAPPY;
    }

    /** a one-off activity's chance to teach in a band (spec, Traits and Mood) */
    public static double oneOffChance(Band b) {
        return switch (b) { case THRIVING -> 0.75; case HAPPY -> 0.5; case CONTENT -> 0.25; case UNEASY -> 0.125; case MISERABLE -> 0; };
    }

    /** each counted activity counts this many times in a band */
    public static int countedStep(Band b) { return b == Band.THRIVING ? 2 : 1; }

    public void addMood(double d) { mood = Math.max(0, Math.min(100, mood + d)); }
}
