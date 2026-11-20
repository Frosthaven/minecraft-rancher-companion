package dev.frosthaven.ranchercompanion.look;

/** Per-stage dimensions in pixels (16 per block), the design page's DIMS table. */
public final class Dims {
    public float[] body, thigh, shank, foot, toe, neck, neckA, head, muzzle, jaw, eye, horn, ear, wingArm, wingFin, wingPanel, plumeCarn, plumeHerb, sickleSeg;
    public float[][] tail;
    public float hipX, hipZ, hipSink, thighA, shankA, talon, headTilt, crestH, fang, spineLen = 1, tailUp, saddleD, saddleZ, shadow;
    /** the whole model's scale in the world (decided 2026-11-20: Fledgling and Mature at 75% of the page's pixel sizes),
     *  and the hitbox in blocks at that scale. The pixel sizes and the loops stay the page's; everything in the world
     *  (hitbox, shadow, particles, travel speeds, room to stand) follows the scale. */
    public float scale = 1, hitW, hitH;
    public int crest, sickleN;
    public boolean rump, hook, hornParts, wingFlank, legDetail, saddle;

    private static float[] f(float... v) { return v; }

    private static final Dims S1 = new Dims(), S2 = new Dims(), S3 = new Dims();

    static {
        Dims d = S1;
        d.body = f(5, 4, 6); d.hipX = 1.6f; d.hipZ = 0.2f; d.hipSink = 1.3f;
        d.thigh = f(1.4f, 1.3f, 1.4f); d.thighA = 15; d.shank = f(1.1f, 1.3f, 1.1f); d.shankA = 30; d.foot = f(1.4f, 0.6f, 1.6f); d.toe = f(0.45f, 0.45f, 1.3f); d.talon = 0.6f;
        d.neck = f(2.4f, 1.6f, 1.4f, 2.4f); d.neckA = f(40, -22); d.head = f(4.4f, 3.1f, 3.8f); d.headTilt = 4; d.muzzle = f(3, 1.7f, 3); d.jaw = f(2.7f, 0.7f, 2.5f);
        d.eye = f(2.1f, 1.6f); d.horn = f(0.6f, 2.4f); d.ear = f(0.4f, 1.4f, 1.6f); d.crest = 1; d.crestH = 1.6f;
        d.wingArm = f(0.7f, 2.2f); d.wingFin = f(0.7f, 2.4f); d.wingPanel = f(0.4f, 1.6f, 2.4f);
        d.tail = new float[][]{f(1.6f, 1.6f, 3)}; d.tailUp = 18; d.plumeCarn = f(0.5f, 1.5f, 1.6f, 1); d.plumeHerb = f(0.6f, 1.6f, 2.8f, 4); d.shadow = 0.28f;
        d.hitW = 0.5f; d.hitH = 0.55f;

        d = S2;
        d.body = f(8, 6.8f, 14); d.rump = true; d.hipX = 3.4f; d.hipZ = -1.5f; d.hipSink = 2;
        d.thigh = f(3.5f, 4.2f, 3.5f); d.thighA = 20; d.shank = f(2.2f, 4.2f, 2.2f); d.shankA = 36; d.foot = f(2.4f, 1, 3); d.toe = f(0.7f, 0.7f, 2.4f); d.talon = 1.1f;
        d.neck = f(4.2f, 4, 3.2f, 4.2f); d.neckA = f(42, -24); d.head = f(6.6f, 4.6f, 5.6f); d.headTilt = 12; d.muzzle = f(4.6f, 2.5f, 3.4f); d.jaw = f(4.2f, 1.1f, 2.9f); d.fang = 0.45f;
        d.eye = f(2.7f, 2); d.horn = f(0.9f, 3.8f); d.ear = f(0.5f, 2, 2.4f); d.crest = 2; d.crestH = 3;
        d.wingArm = f(1.4f, 6); d.wingFin = f(1, 8.5f); d.wingPanel = f(1.0f, 7.5f, 13); d.wingFlank = true;
        d.tail = new float[][]{f(3, 3, 7), f(2.2f, 2.2f, 6)}; d.tailUp = 14; d.plumeCarn = f(0.7f, 2.6f, 3, 1); d.plumeHerb = f(0.9f, 3, 7, 6);
        d.sickleSeg = f(3.5f, 3.5f, 3); d.sickleN = 3; d.shadow = 0.62f;
        d.scale = 0.75f; d.hitW = 0.75f; d.hitH = 0.975f;  /* was 1.0 x 1.3 at full size */

        d = S3;
        d.body = f(11, 9.5f, 24); d.rump = true; d.hipX = 5; d.hipZ = -3.5f; d.hipSink = 2.3f;
        d.thigh = f(4, 8.5f, 4); d.thighA = 25; d.shank = f(2, 8.5f, 2); d.shankA = 40; d.foot = f(3.4f, 1.6f, 4.5f); d.toe = f(1, 1, 4.5f); d.talon = 1.6f;
        d.neck = f(6, 6, 5, 6); d.neckA = f(42, -24); d.head = f(8.2f, 5.6f, 7); d.headTilt = 20; d.muzzle = f(5.6f, 3, 5.4f); d.jaw = f(5.2f, 1.4f, 4.8f); d.hook = true; d.fang = 0.8f;
        d.eye = f(3.3f, 2.5f); d.horn = f(1.5f, 8); d.hornParts = true; d.spineLen = 2; d.ear = f(0.6f, 2.6f, 3.2f); d.crest = 3; d.crestH = 4.5f;
        d.wingArm = f(1.8f, 10); d.wingFin = f(1.3f, 14); d.wingPanel = f(1.0f, 12, 22); d.wingFlank = true; d.legDetail = true;
        d.tail = new float[][]{f(4, 4, 9), f(3, 3, 8), f(2.2f, 2.2f, 7)}; d.tailUp = 10; d.plumeCarn = f(0.8f, 3.4f, 4, 2); d.plumeHerb = f(1.1f, 4, 11, 7);
        d.sickleSeg = f(5, 5, 4); d.sickleN = 3; d.saddle = true; d.saddleD = 9.5f; d.saddleZ = 1;  /* 4 px back (2026-11-20): the page moved it so a player-size rider clears the head */ d.shadow = 0.95f;
        d.scale = 0.75f; d.hitW = 1.125f; d.hitH = 1.725f;  /* was 1.5 x 2.3 */
    }

    public static Dims of(int stage) { return stage <= 1 ? S1 : stage == 2 ? S2 : S3; }

    /** Standing hip height above the ground, pixels. */
    public float hipY() {
        return (float) (thigh[1] * Math.cos(Math.toRadians(thighA)) + shank[1] * Math.cos(Math.toRadians(shankA)) + foot[1]);
    }

    public float bodyY() { return hipY() + body[1] / 2 - hipSink; }
}
