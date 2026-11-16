package dev.frosthaven.ranchercompanion.look;

/**
 * Every loop the design page shows, with the first stage that can do it and how fast the drekki travels while
 * it plays (blocks per second at stage 3; scaled by the stage). Pure data, so the stage gate is unit tested.
 */
public enum Anim {
    IDLE("idle", 1, 0), WALK("walk", 1, 1.1), RUN("run", 1, 3.4), JUMP("jump", 1, 0), FALL("fall", 1, 0),
    GLIDE("glide", 3, 4.2), DASH("dash", 1, -1), SLEEP("sleep", 1, 0), FEED("feed", 1, 0),
    BITE("bite", 2, 0), POUNCE("pounce", 3, 0), BALL("ball", 2, 0), BREATH("breath", 3, 0),
    GUARD("guard", 3, 0), REJUV("rejuv", 3, 0);

    public final String key;
    public final int minStage;
    /** blocks per second at stage 3; 0 stays put; -1 is the dash, whose speed follows its loop. */
    public final double speed;

    Anim(String key, int minStage, double speed) { this.key = key; this.minStage = minStage; this.speed = speed; }

    public boolean availableAt(int stage) { return stage >= minStage; }

    public static Anim of(int i) { return values()[Math.floorMod(i, values().length)]; }

    /** Scene scale per stage: distances, lanes and particles follow the drekki's size. */
    public static double stageK(int stage) { return stage == 1 ? 0.42 : stage == 2 ? 0.7 : 1.0; }

    /** Speed in blocks per second at a stage and loop time; the dash bursts out of the run and eases back. */
    public double speedAt(int stage, double t) {
        double k = stageK(stage);
        if (speed >= 0) return speed * k;
        double f = loop(t, 3.4), d = f < 0.45 ? Math.sin(f / 0.45 * Math.PI) : 0;
        return (3.4 + 4.5 * d) * k;
    }

    /** the feed loop: seconds of walking before the trough, the walk's distance, and the mouth's reach at full lean (measured on the page) */
    public static final double FEED_W = 2.0;
    private static final double[] FEED_R = {0.41, 0.97, 1.58};
    public static double feedWalk(double k) { return 1.1 * k * (FEED_W - 0.15); }
    public static double feedReach(int stage) { return FEED_R[Math.max(1, Math.min(3, stage)) - 1]; }
    /** the pounce's leap, blocks forward of the start at stage 3 */
    public static final double POUNCE_LEAP = 4.2;

    public static double loop(double t, double period) {
        double m = t % period;
        return (m < 0 ? m + period : m) / period;
    }
}
