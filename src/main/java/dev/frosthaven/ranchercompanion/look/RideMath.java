package dev.frosthaven.ranchercompanion.look;

/** The numbers of a ridden Mature (spec, Stage 3: riding), pure so they are unit tested. Speeds in blocks a tick. */
public final class RideMath {
    private RideMath() {}

    /** its walking speed under a rider, as the game's movement speed attribute (an average horse is 0.225) */
    public static final double SPEED = 0.2;
    /** the jump: about 2 blocks */
    public static final double JUMP = 0.62;
    /** Four Winds: a camel's dash (2.0 along, 0.6 up) at twice the speed, and its 55 ticks between dashes */
    public static final double DASH = 4.0, DASH_UP = 0.6;
    public static final int DASH_COOLDOWN = 55;
    /** Leap of Faith: the slow fall's speed, from the 3 blocks a plain fall may be */
    public static final double SLOW_FALL = 0.18, SAFE_FALL = 3;
    /** Wings of Hope: a wingbeat every BEAT ticks pushes for PUSH of them, THRUST each; the climb eases out toward CLIMB */
    public static final int BEAT = 12, PUSH = 5;
    public static final double THRUST = 0.30, CLIMB = 0.45;
    /** a liquid this deep at its feet takes the rider off (a puddle does not) */
    public static final double DEEP = 0.9;
    /** the server forgets a fall while it sinks slower than this: a landing under wings or a slow fall is no fall */
    public static final double FALLING = 0.3;

    /** the push of one tick of flight: along the rider's up direction on the beat's downstroke, the vertical part eased
     *  as the climb builds (Mekanism's Vector mode, in beats); null between downstrokes */
    public static double[] thrust(int beatTick, double upX, double upY, double upZ, double vy) {
        if (Math.floorMod(beatTick, BEAT) >= PUSH) return null;
        double ease = upY > 0 ? Math.max(0, Math.min(1, 1 - vy / CLIMB)) : 1;
        return new double[]{upX * THRUST, upY * THRUST * ease, upZ * THRUST};
    }

    /** a liquid walker stays on top when it steps on or lands from 3 blocks or less; from higher it plunges in */
    public static boolean staysOnTop(float fallDistance) { return fallDistance <= SAFE_FALL; }
}
