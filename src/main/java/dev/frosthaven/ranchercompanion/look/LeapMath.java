package dev.frosthaven.ranchercompanion.look;

/**
 * A leap that lands where it is aimed (the Pounce). The game moves a mob each tick by its velocity, then scales the
 * velocity: sideways by the ground's friction on a tick that began on the ground and by 0.91 in the air; down by
 * gravity 0.08, then 0.98. So a launch speed for a given distance, height and time in the air follows from the sums.
 */
public final class LeapMath {
    private LeapMath() {}

    public static final double AIR = 0.91, GRAVITY = 0.08, DRAG = 0.98;

    /** blocks covered for a launch speed of 1 a tick: one tick off the ground, ticks - 1 in the air, then the slide to a stop */
    public static double reach(int ticks, double ground) {
        double v = 1, x = 0;
        for (int i = 0; i < ticks; i++) { x += v; v *= i == 0 ? ground : AIR; }
        for (int i = 0; i < 20 && v > 0.003; i++) { x += v; v *= ground; }
        return x;
    }

    /** the height after ticks in the air for an upward launch speed */
    public static double rise(double up, int ticks) {
        double y = 0;
        for (int i = 0; i < ticks; i++) { y += up; up = (up - GRAVITY) * DRAG; }
        return y;
    }

    /** {sideways, up} launch speeds to land dist ahead and dy higher after ticks; ground: the block's slipperiness x 0.91 */
    public static double[] launch(double dist, double dy, int ticks, double ground) {
        double y0 = rise(0, ticks), y1 = rise(1, ticks);  /* the height is linear in the launch speed */
        return new double[]{dist / reach(ticks, ground), (dy - y0) / (y1 - y0)};
    }
}
