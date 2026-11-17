package dev.frosthaven.ranchercompanion.client;

import dev.frosthaven.ranchercompanion.client.rig.Baked;
import dev.frosthaven.ranchercompanion.client.rig.Node;
import dev.frosthaven.ranchercompanion.client.rig.Rig;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.look.Anim;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The design page's loops, one method each, driving the rig by loop time in seconds. */
public final class DrekkiAnimations {
    private DrekkiAnimations() {}

    private static final double TAU = 6.283;
    private static double rad(double d) { return Math.toRadians(d); }
    private static double loop(double t, double T) { return Anim.loop(t, T); }
    private static double ease(double u) { return u <= 0 ? 0 : u >= 1 ? 1 : 0.5 - 0.5 * Math.cos(u * Math.PI); }
    private static double sin(double a) { return Math.sin(a); }
    private static double cos(double a) { return Math.cos(a); }

    public static void pose(Baked b, DrekkiEntity e, double t) {
        Rig P = b.rig; P.reset();
        int stage = e.stage(); double k = Anim.stageK(stage);
        idle(P, t);
        switch (e.anim()) {
            case WALK -> walk(P, t);
            case RUN -> run(P, t);
            case DASH -> dash(P, t);
            case GLIDE -> glide(P, t, k);
            case JUMP -> jump(P, t, k);
            case FALL -> fall(P, t, k);
            case SLEEP -> sleep(P, t);
            case FEED -> feed(P, t, k, stage);
            case BITE -> bite(P, t, k);
            case POUNCE -> pounce(P, t, k);
            case BALL -> ball(P, t);
            case BREATH -> breath(P, t);
            case GUARD -> guard(P, t);
            case REJUV -> rejuv(P, t);
            default -> {}
        }
        if (e.petTicks() > 0) pet(P, t, e.petTicks());
        b.apply();
    }

    /** petted: the head dips into the hand and nuzzles, the tail wags, eased in and out over the pet's ticks */
    static void pet(Rig P, double t, int left) {
        double u = 1 - left / (double) DrekkiEntity.PET_TICKS, env = Math.sin(Math.PI * Math.min(1, u * 1.15));
        P.neck2.rx += (float) (0.35 * env); P.head.rx += (float) (0.3 * env);
        P.head.rz = (float) (sin(t * 9) * 0.12 * env);
        P.tail.ry += (float) (sin(t * 14) * 0.45 * env);
    }

    static void idle(Rig P, double t) {
        P.neck1.y = (float) (P.neck1.py + sin(t * 2.1) * 0.18);  /* breathing lifts the neck, not the body, so the feet stay planted */
        P.neck2.rx = (float) (P.neckBase + sin(t * 1.3) * 0.03);
        P.head.rx = (float) (P.headBase + sin(t * 1.3) * 0.05); P.head.ry = (float) (sin(t * 0.7) * 0.08);
        P.tail.ry = (float) (sin(t * 1.7) * 0.12);
        for (int i = 0; i < P.tailSegs.size(); i++) P.tailSegs.get(i).ry = (float) (sin(t * 1.7 - 0.6 * (i + 1)) * 0.1);
        for (int i = 0; i < P.wings.size(); i++) { Node w = P.wings.get(i); w.rx = (float) (w.bx + sin(t * 0.9 + i) * 0.03); }
        if (P.hatSpin != null) P.hatSpin.ry = (float) (t * 9);
    }

    static void walk(Rig P, double t) {
        double w = t * 1.4 * TAU; P.gait(w, 0.55, P.bodyY * 0.04);
        P.neck2.rx += (float) (sin(w * 2) * 0.04); P.head.rx -= (float) (sin(w * 2) * 0.06); P.mouth(0); P.flare(0);
    }

    static void run(Rig P, double t) {
        double w = t * 2.4 * TAU; P.gait(w, 1.0, P.bodyY * 0.11); P.body.rx += 0.14f;
        P.neck2.rx = (float) (P.neckBase - 0.3 + sin(w * 2) * 0.08); P.head.rx = (float) (P.headBase + 0.35 - sin(w * 2) * 0.1);
        P.tail.rx = P.tailBase * 0.35f; P.tail.ry = (float) (sin(w) * 0.06); for (Node s : P.tailSegs) s.rx = 0.02f;
        P.mouth(0.2); P.flare(0.15 + sin(w) * 0.08);
    }

    /** stride phase of the dash: the run's rate plus the burst, integrated over the loops so far */
    public static double dashPhase(double t) {
        double f = loop(t, 3.4), lobe = 1.8 * 3.4 * 0.45 / Math.PI, loops = Math.floor(t / 3.4);
        return 2.4 * t + lobe * (2 * loops + (f < 0.45 ? 1 - cos(f * Math.PI / 0.45) : 2));
    }

    static void dash(Rig P, double t) {
        double f = loop(t, 3.4), d = f < 0.45 ? sin(f / 0.45 * Math.PI) : 0, w = dashPhase(t) * TAU;
        P.gait(w, 1.0 + 0.25 * d, P.bodyY * (0.11 - 0.05 * d)); P.body.y -= (float) (d * P.crouchPx * 0.35); P.body.rx += (float) (0.14 + 0.12 * d);
        P.neck2.rx = (float) (P.neckBase - 0.3 - 0.25 * d + sin(w * 2) * 0.08 * (1 - d)); P.head.rx = (float) (P.headBase + 0.35 + 0.3 * d - sin(w * 2) * 0.1 * (1 - d));
        P.tail.rx = (float) (P.tailBase * (0.35 - 0.3 * d)); P.tail.ry = (float) (sin(w) * 0.06); for (Node s : P.tailSegs) s.rx = 0.02f;
        P.mouth(0.2); P.flare(0.15 - 0.15 * d + sin(w) * 0.08 * (1 - d));
    }

    static void glide(Rig P, double t, double k) {
        double H = 1.3 + 0.9 * k;
        P.lift(H + sin(t * 0.8) * 0.1 * k); P.legPose(0.9, 0.6, 0.4); P.body.rx = (float) (0.08 + sin(t * 0.8) * 0.03);
        P.neck2.rx = P.neckBase - 0.4f; P.head.rx = P.headBase + 0.45f;
        P.tail.rx = 0.04f; P.tail.ry = (float) (sin(t * 1.1) * 0.06);
        for (int i = 0; i < P.tailSegs.size(); i++) { Node s = P.tailSegs.get(i); s.rx = 0.02f; s.ry = (float) (sin(t * 1.1 - 0.5 * i) * 0.05); }
        P.mouth(0); P.flare(0.1 + sin(t * 1.6) * 0.07); P.body.y = P.bodyY;
    }

    static final double[] STAND = {0, 0, 0}, J_REACH = {-0.2, 0.3, -0.35}, J_CROUCH = {-0.25, 0.9, -0.65}, F_REACH = {-0.25, 0.35, -0.45}, F_CROUCH = {-0.3, 1.2, -0.9};  /* squashes fold the hock with the thigh forward, so the foot keeps its spot */

    static void jump(Rig P, double t, double k) {
        double H = 1.25 * k, f = loop(t, 2.2), y = 0, crouch = 0, fl = 0, pitch = 0; P.legPose(0, 0, 0);
        if (f >= 0.1 && f < 0.42) { double u = (f - 0.1) / 0.32, push = sin(Math.min(1, u / 0.07) * Math.PI), reach = ease((u - 0.78) / 0.22), back = ease((u - 0.03) / 0.12) * (1 - reach);
            y = H * 4 * u * (1 - u);
            P.legPose(0.1 * push + 0.45 * back + J_REACH[0] * reach, -0.35 * push + 0.6 * back + J_REACH[1] * reach, 0.3 * push + 0.3 * back + J_REACH[2] * reach);
            fl = 0.2 + sin(Math.min(1, u / 0.6) * Math.PI) * 0.4; pitch = (-0.12 * (1 - u) + 0.05 * u) * ease(u / 0.12); }
        else if (f >= 0.42 && f < 0.56) { double u = (f - 0.42) / 0.14, c = sin(u * Math.PI);
            P.legMix(u < 0.5 ? J_REACH : STAND, J_CROUCH, c); crouch = c * 1.4; pitch = 0.05 * (1 - Math.min(1, u / 0.3)); fl = 0.2 * (1 - Math.min(1, u / 0.3)); }
        P.lift(y); P.body.y -= (float) (crouch * P.crouchPx); P.body.rx = (float) pitch; P.mouth(0); P.flare(fl); P.tail.rx = (float) (P.tailBase - (y / H) * 0.3);
    }

    static void fall(Rig P, double t, double k) {
        double H = 3.2 * k, f = loop(t, 2.6), y = 0, crouch = 0; boolean air = f < 0.36; double a = air ? 1 : Math.max(0, 1 - (f - 0.36) / 0.08); P.legPose(0, 0, 0);  /* a: how much of the airborne pose is left */
        if (air) { double u = f / 0.36; y = H * (1 - u * u); P.legPose(F_REACH[0], F_REACH[1], F_REACH[2]); }
        else if (f < 0.52) { double u = (f - 0.36) / 0.16, c = sin(u * Math.PI); P.legMix(u < 0.5 ? F_REACH : STAND, F_CROUCH, c); crouch = c * 2.0; P.head.rx += (float) (0.2 * c); }
        P.head.rx += (float) (0.35 * a); P.neck2.rx -= (float) (0.1 * a);
        P.lift(y); P.body.y -= (float) (crouch * P.crouchPx); P.body.rx = (float) (0.12 * a); P.mouth(0.25 * a); P.flare((0.35 + sin(t * 22) * 0.15) * a); P.tail.rx = (float) (P.tailBase + 0.5 * a);
    }

    static void sleep(Rig P, double t) {
        double ground = P.crouchPx / 0.4 / 2 + 0.5;
        P.neck1.orderYXZ = P.neck2.orderYXZ = P.head.orderYXZ = true;
        double T = loop(t, 8) * 8, s = ease((T - 0.6) / 1.8), legs = ease(s / 0.7), neck = ease((s - 0.25) / 0.75), tail = ease((s - 0.15) / 0.75), br = sin(t * 1.4) * s;
        P.body.y = (float) (P.bodyY + (ground - P.bodyY) * legs + br * 0.3); P.legPose(2.0 * legs, -4.6 * legs, 2.6 * legs);
        P.neck1.rx = (float) (P.neck1Base + 1.4 * neck); P.neck1.ry = (float) (-0.3 * neck); P.neck2.rx = (float) (P.neckBase + 0.8 * neck); P.neck2.ry = (float) (-0.3 * neck);
        /* level the head along the neck's line, 30 degrees clockwise, blended from the standing pitch as it lies down */
        Quaternionf q = Baked.quat(P.body, new Quaternionf()).mul(Baked.quat(P.neck1, new Quaternionf())).mul(Baked.quat(P.neck2, new Quaternionf()));
        Vector3f v = q.transform(new Vector3f(0, 1, 0)); double h = Math.atan2(v.x, v.z) - rad(30);
        Quaternionf level = new Quaternionf(q).invert().mul(new Quaternionf().rotationY((float) h));
        P.head.quat = level.slerp(new Quaternionf().rotationX(P.headBase), (float) (1 - neck));
        P.tail.rx = (float) (P.tailBase + (-0.1 - P.tailBase) * tail); P.tail.ry = (float) (1.5 * tail);
        for (Node sg : P.tailSegs) { sg.rx = (float) (rad(4) * (1 - tail)); sg.ry = (float) (0.5 * tail); }
        P.mouth(0.04 * s); P.flare(-0.12 * s + br * 0.02); for (Node l : P.lids) l.visible = s > 0.92;
    }

    static final double[][] FEED_A = {{0.1, 0.15, -0.15}, {0.35, 0.5, -0.6}, {0.6, 0.75, -1.0}};

    static void feed(Rig P, double t, double k, int stage) {
        double[] A = FEED_A[stage - 1]; double W = Anim.FEED_W, v = 1.1 * k, T = loop(t, W + 4.2) * (W + 4.2);
        if (T < W) { double d = Math.max(0, T - (W - 0.3)), a = 1 - d / 0.3, w = T * 1.4 * TAU; P.forward(v * (Math.min(T, W - 0.3) + d - d * d / 0.6));
            P.gait(w, 0.55 * a, P.bodyY * 0.04 * a); P.neck2.rx += (float) (sin(w * 2) * 0.04 * a); P.head.rx -= (float) (sin(w * 2) * 0.06 * a); P.mouth(0); P.flare(0); return; }
        P.forward(Anim.feedWalk(k)); double f = (T - W) / 4.2, e = 0, open = 0, rock = 0, swallow = 0;
        if (f < 0.14) e = sin(f / 0.14 * Math.PI / 2);
        else if (f < 0.48) { e = 1; open = 0.25 * Math.abs(sin(t * 14)); P.head.ry = (float) (sin(t * 9) * 0.06); rock = 1; }
        else if (f < 0.6) { double u = (f - 0.48) / 0.12; e = 1 - 0.75 * sin(u * Math.PI / 2); }
        else if (f < 0.86) { e = 0.25; open = 0.3 * Math.abs(sin(t * 10)); }
        else { double u = (f - 0.86) / 0.14; e = 0.25 * (1 - u); swallow = sin(u * Math.PI); }
        P.legPose(-0.15 * e, 0.35 * e, -0.15 * e); P.body.rot(0.12 * e + sin(t * 7) * 0.012 * rock, sin(t * 2.5) * 0.03 * (1 - rock), sin(t * 7) * 0.015 * rock);
        P.body.y += (float) (-e * P.crouchPx * 0.3 + swallow * 0.5); P.body.x = (float) (sin(t * 7) * 0.35 * rock);
        P.neck1.rx = (float) (P.neck1Base + A[0] * e); P.neck2.rx = (float) (P.neckBase + A[1] * e); P.head.rx = (float) (P.headBase + A[2] * e + 0.25 * e);
        P.mouth(open + (e > 0.9 ? 0.15 : 0)); P.flare(0.08 * e + sin(t * 7) * 0.01 * rock); P.tail.ry = (float) (sin(t * 3) * 0.35);
    }

    static void bite(Rig P, double t, double k) {
        double f = loop(t, 1.6); boolean on = f > 0.1 && f < 0.34; double u = on ? (f - 0.1) / 0.24 : 0, c = u < 0.35 ? sin(u / 0.35 * Math.PI / 2) : cos((u - 0.35) / 0.65 * Math.PI / 2);
        P.forward(c * 0.2 * k); P.head.rx += (float) (c * 0.35); P.mouth(u < 0.5 ? c * 0.7 : c * 0.12); P.flare(c * 0.2); P.tail.ry += (float) (c * 0.35); P.body.rx = (float) (c * 0.1);
        Rig.Leg far = P.legs.get(0), near = P.legs.get(1);
        near.thigh.rx = (float) (near.t0 - 0.45 * c); near.knee.rx = (float) (near.k0 + 0.25 * c); near.ankle.rx = (float) (near.a0 - 0.1 * c);
        far.thigh.rx = (float) (far.t0 + 0.2 * c); far.knee.rx = (float) (far.k0 - 0.15 * c); far.ankle.rx = (float) (far.a0 + 0.1 * c);
    }

    /** the pounce: off the ground at once, a flat leap with the jaws open, and one bite as it lands on the target */
    static void pounce(Rig P, double t, double k) {
        double f = loop(t, 2.4), x = 0, y = 0, crouch = 0, pitch = 0, open = 0, fl = 0, x1 = Anim.POUNCE_LEAP * k; P.legPose(0, 0, 0);
        if (f < 0.04) { double u = f / 0.04, cc = sin(u * Math.PI / 2); crouch = cc * 1.7; P.legPose(-0.3 * cc, 1.0 * cc, -0.7 * cc); open = 0.3 * cc; P.head.rx -= (float) (0.15 * cc * 1.7); fl = -0.1 * cc * 1.7; }
        else if (f < 0.18) { double u = (f - 0.04) / 0.14, arc = sin(u * Math.PI), ext = Math.min(1, u / 0.25); x = x1 * u; y = arc * 1.2 * k; pitch = cos(u * Math.PI) * 0.4; crouch = (1 - ext) * 1.7;
            P.legPose(-0.3 * (1 - ext) - 0.6 * ext + arc * 0.9, 1.0 * (1 - ext) + 0.2 * ext + arc * 0.6, -0.7 * (1 - ext) - 0.2 * arc); open = 0.75; fl = arc * 0.6; }
        else if (f < 0.3) { double u = (f - 0.18) / 0.12, c = sin(u * Math.PI); x = x1; crouch = c * 0.6; P.legPose(-0.12 * c, 0.4 * c, -0.28 * c); pitch = -0.2 * (1 - u);
            /* the bite as it lands: the body barely dips, the neck reaches forward with the head level, the jaws gape, slam shut at u 0.47 and hold */
            double gape = u < 0.4 ? 0.8 + 0.2 * (u / 0.4) : u < 0.47 ? 1 - (u - 0.4) / 0.07 : 0, reach = u < 0.47 ? u / 0.47 : Math.max(0, 1 - (u - 0.47) / 0.53);
            open = gape * 0.95; P.neck2.rx += (float) (0.18 * reach); P.head.rx -= (float) (0.12 * reach + 0.1 * gape); }
        else x = x1;
        P.forward(x); P.lift(y); P.body.rx = (float) -pitch; P.body.y -= (float) (crouch * P.crouchPx); P.mouth(open); P.flare(fl);
    }

    /** the elemental ball: the feet stay planted; the body rocks on its hips with the thighs turning against it, the neck rears and thrusts */
    static void ball(Rig P, double t) {
        double f = loop(t, 2.8), rear = f < 0.14 ? sin(f / 0.14 * Math.PI) : 0; boolean fire = f >= 0.14 && f < 0.5; double u = fire ? (f - 0.14) / 0.36 : 0;
        double thrust = fire ? sin(Math.min(1, u * 4) * Math.PI) : 0, bp = -0.12 * rear + 0.07 * thrust;
        P.head.rx -= (float) (rear * 0.35 + 0.1 * thrust); P.neck2.rx -= (float) (rear * 0.25); P.mouth(rear * 0.7 + (fire ? 0.5 * (1 - u) : 0));
        P.legPose(-bp, 0, 0); P.body.rx = (float) bp; P.flare(rear * 0.45 + thrust * 0.3 + (fire ? 0.15 * (1 - u) : 0));
        P.tail.rx = (float) (P.tailBase + 0.3 * rear - 0.15 * thrust); P.tail.ry = (float) (sin(t * 4) * 0.12 * (rear + thrust * 0.5)); for (Node sg : P.tailSegs) sg.rx = (float) (rad(4) + 0.1 * rear);
        P.neck1.rx = (float) (P.neck1Base - 0.22 * rear + 0.18 * thrust);
    }

    static void breath(Rig P, double t) {
        double f = loop(t, 3.8), wind = f < 0.1 ? f / 0.1 : 0; boolean on = f >= 0.1 && f < 0.62; double fade = f >= 0.62 && f < 0.72 ? 1 - (f - 0.62) / 0.1 : 0, a = on ? 1 : fade + wind * 0.6, lean = on ? 1 : fade + wind;
        P.neck1.rx = (float) (P.neck1Base + 0.55 * lean); P.neck2.rx = (float) (P.neckBase + 0.75 * lean); P.head.rx = (float) (P.headBase - 1.05 * lean + (on ? 0.1 : 0));
        P.mouth(on ? 0.75 : 0.55 * a); P.legPose(-0.2 * a, 0.3 * a, -0.1 * a); P.flare(0.35 * a); P.tail.ry = (float) (sin(t * 5) * 0.25 * a); P.body.x = (float) (on ? sin(t * 40) * 0.15 : 0); P.body.y -= (float) (lean * P.crouchPx * 0.25);
        P.tail.rx = (float) (P.tailBase + 0.6 * lean); for (Node sg : P.tailSegs) sg.rx = (float) (rad(4) + 0.15 * lean);
    }

    static void guard(Rig P, double t) {
        double f = loop(t, 4.4), stomp = f < 0.1 ? f / 0.1 : 0, hold = f >= 0.1 && f < 0.66 ? 1 : (f >= 0.66 && f < 0.76 ? 1 - (f - 0.66) / 0.1 : 0); Rig.Leg L = P.legs.get(0); P.legPose(0, 0, 0);
        if (stomp > 0) { double u = sin(stomp * Math.PI); L.thigh.rx = (float) (L.t0 - u * 0.7); L.knee.rx = (float) (L.k0 + u * 0.9); L.ankle.rx = (float) (L.a0 - u * 0.3); }
        P.body.y -= (float) ((stomp > 0.8 ? sin((stomp - 0.8) / 0.2 * Math.PI) : 0) * P.crouchPx * 0.5); P.flare(0.6 * hold + 0.3 * stomp); P.head.rx -= (float) (0.25 * hold); P.mouth(0); P.tail.ry += (float) (sin(t * 4) * 0.1 * hold);
    }

    static void rejuv(Rig P, double t) {
        double f = loop(t, 4.2), rise = f < 0.12 ? sin(f / 0.12 * Math.PI / 2) : f < 0.62 ? 1 : f < 0.74 ? 1 - (f - 0.62) / 0.12 : 0;
        P.legPose(0.3 * rise, 0.45 * rise, -0.2 * rise); P.body.rx = (float) (-0.3 * rise); P.body.y -= (float) (rise * P.crouchPx * 0.3); P.neck2.rx -= (float) (0.2 * rise); P.head.rx += (float) (0.1 * rise);
        P.mouth(0.35 * rise); P.flare(0.55 * rise + sin(t * 3) * 0.05 * rise); P.tail.rx = (float) (P.tailBase - 0.25 * rise);
    }
}
