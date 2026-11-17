package dev.frosthaven.ranchercompanion.look;

/** How far an egg is, by ticks in a hatchery (spec, Pup release): one stage per in-game day, Ready after 3 days. */
public final class Incubation {
    private Incubation() {}

    public static final long DAY = 24000, READY = 3 * DAY;
    /** tooltip keys by stage: warm, noises, active, ready */
    public static final String[] KEYS = {"warm", "noises", "active", "ready"};

    /** 0 up to 1 day, 1 up to 2 days, 2 up to 3 days, 3 after */
    public static int stage(long ticks) {
        if (ticks > READY) return 3;
        return ticks <= DAY ? 0 : ticks <= 2 * DAY ? 1 : 2;
    }

    public static boolean ready(long ticks) { return ticks > READY; }

    /** the creative egg for day n (1..4): the first tick of that stage */
    public static long creativeDay(int n) { return (n - 1) * DAY + 1; }
}
