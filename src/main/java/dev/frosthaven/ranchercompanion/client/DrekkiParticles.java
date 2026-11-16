package dev.frosthaven.ranchercompanion.client;

import dev.frosthaven.ranchercompanion.client.rig.Baked;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.look.Anim;
import dev.frosthaven.ranchercompanion.look.Lineage;
import dev.frosthaven.ranchercompanion.look.Palette;
import dev.frosthaven.ranchercompanion.showcase.Layout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

/**
 * The page's particle effects as its own unlit cubes ({@link DrekkiCube}), spawned per client tick from each loop's
 * phase, with the page's velocity, gravity, life, opacity and size envelope. The page's per-frame emitters run three
 * frames per tick (60 fps).
 * Coordinates are the page's, from the drekki's start: forward, up, side (the page's z).
 */
public final class DrekkiParticles {
    private DrekkiParticles() {}

    static final int MAGIC = 0xff3fd6, MAGIC_L = 0xffb0ee, HEAL = 0x7ef58a, STEEL = 0xc8ccd2, THORN = 0x4f545c, DUST = 0xb9b4a4, WIND = 0xc9d2db, HAY = 0xc9a63a, WHITE = 0xffffff, SNORE = 0x9aa3ad;
    private static final double[] PK = {0.3, 0.6, 1};  /* the rare lines' sparkle scale per stage */
    private static final Random R = new Random();

    /** one drekki's frame for a tick: where it stands, which way it faces, how fast it travels */
    private record Ctx(DrekkiEntity e, double x, double y, double z, double[] F, double[] Rt, double travel) {}

    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        for (Entity en : mc.level.entitiesForRendering()) if (en instanceof DrekkiEntity d && d.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) < 90 * 90) emit(d);
    }

    private static double rnd(double a, double b) { return a + R.nextDouble() * (b - a); }

    /** the page's fx.emit: position and velocity in the drekki's frame, life in seconds, cube size in blocks, gravity. age pre-runs it for a frame emitted earlier in the tick */
    private static void spark(Ctx c, double fw, double up, double sd, double vf, double vu, double vs, double life, double size, int rgb, double g, double age) {
        cube(c, fw, up, sd, vf, vu, vs, life, size, rgb, g, age, 0.95, DrekkiCube.Mode.SHRINK, 1, 1);  /* the loop emitter: 95% opaque, shrinking to 30% */
    }

    /** one of the page's cubes, with its opacity, size envelope and stretch (w along the drekki's side, h up) */
    private static void cube(Ctx c, double fw, double up, double sd, double vf, double vu, double vs, double life, double size, int rgb, double g, double age,
                             double opacity, DrekkiCube.Mode mode, double w, double h) {
        Minecraft mc = Minecraft.getInstance(); ParticleStatus ps = mc.options.particles().get();
        if (ps == ParticleStatus.MINIMAL || ps == ParticleStatus.DECREASED && R.nextInt(3) == 0) return;
        double[] F = c.F, Rt = c.Rt; vf += c.travel;
        double[] v = {F[0] * vf + Rt[0] * vs, vu, F[2] * vf + Rt[2] * vs};
        double x = c.x + F[0] * fw + Rt[0] * sd, y = c.y + up, z = c.z + F[2] * fw + Rt[2] * sd;
        mc.particleEngine.add(new DrekkiCube(mc.level, x, y, z, v, g, c.y, life, size, rgb, opacity, mode, w, h, Rt, F).preRun(age));
    }

    /** the page's burst(): n cubes out of a point, spread speed sp, gravity g */
    private static void burst(Ctx c, double fw, double up, double sd, int n, int rgb, double sp, double g, double k) {
        for (int i = 0; i < n; i++) { double a = R.nextDouble() * 6.283, b = rnd(-0.4, 1.2), v = rnd(0.4, 1) * sp * k;
            spark(c, fw, up, sd, Math.cos(a) * Math.cos(b) * v, Math.sin(b) * v, Math.sin(a) * Math.cos(b) * v, rnd(0.45, 0.8), rnd(0.09, 0.16) * (0.5 + 0.5 * k), rgb, g, 0); }
    }

    private static boolean cross(double lastF, double f, double at) { return lastF < at && f >= at; }

    private static void emit(DrekkiEntity e) {
        Anim a = e.anim(); int stage = e.stage(); double k = Anim.stageK(stage), t = e.time(0);
        float yaw = e.yBodyRot;
        Ctx c = new Ctx(e, e.getX(), e.getY(), e.getZ(), Layout.forward(yaw), Layout.right(yaw), e.lane() > 0 && a.speed != 0 ? a.speedAt(stage, t) : 0);
        sparkle(c, e.lineage(), stage, t);
        double lastF = e.lastF; Vector3f m = a == Anim.SLEEP || a == Anim.FEED || a == Anim.BREATH ? mouth(e) : null;  /* posed once a tick */
        for (int fr = 0; fr < 3; fr++) {
            double age = (2 - fr) / 60.0, tf = t - age;
            switch (a) {
                case RUN -> { if (R.nextDouble() < 0.6) spark(c, rnd(-0.4, 0.1) * k, 0.05, rnd(-0.2, 0.2) * k, -rnd(1, 2) * k, rnd(0.2, 0.6) * k, 0, 0.5, 0.07 * (0.5 + 0.5 * k), DUST, 2, age); }
                case DASH -> { double f = Anim.loop(tf, 3.4), d = f < 0.45 ? Math.sin(f / 0.45 * Math.PI) : 0;
                    if (R.nextDouble() < 0.6 + 0.4 * d) spark(c, rnd(-0.4, 0.1) * k, 0.05, rnd(-0.2, 0.2) * k, -rnd(1, 2.5) * k, rnd(0.2, 0.6) * k, 0, 0.5, 0.07 * (0.5 + 0.5 * k), DUST, 2, age);
                    if (d > 0.3) for (int i = 0; i < 2; i++) spark(c, 4.4, rnd(0.2, 1.6) * k, rnd(-1.4, 1.4) * k, -9, 0, 0, 0.9, 0.05, WIND, 0, age); }
                case GLIDE -> { double H = 1.3 + 0.9 * k; for (int i = 0; i < 2; i++) spark(c, 4.4, H + rnd(-1, 1) * k, rnd(-1.6, 1.6) * k, -7, 0, 0, 1.1, 0.05, WIND, 0, age); }
                case SLEEP -> { double s = ease((Anim.loop(tf, 8) * 8 - 0.6) / 1.8);
                    if (s > 0.99 && R.nextDouble() < 0.012) { spark(c, m.z, m.y + 0.1 * k, -m.x, 0.12, 0.45, 0, 2.0, 0.09 * (0.5 + 0.5 * k), SNORE, 0, age); } }
                case FEED -> { double W = Anim.FEED_W, T = Anim.loop(tf, W + 4.2) * (W + 4.2), f = (T - W) / 4.2;
                    if (T >= W && f >= 0.6 && f < 0.72 && R.nextDouble() < 0.035) { spark(c, m.z, m.y - 0.05, -m.x + rnd(-0.1, 0.1) * k, rnd(0.1, 0.3) * k, 0, 0, 0.5, 0.04 * (0.5 + 0.5 * k), HAY, 6, age); } }
                case BALL -> { double f = Anim.loop(tf, 2.8); if (f >= 0.14 && f < 0.5) { double u = (f - 0.14) / 0.36, tx = Layout.props(a, k)[0][0]; Vector3f rm = restMouth(e);
                    double bf = rm.z + (tx - rm.z) * u, by = rm.y + (1.2 * k - rm.y) * u + Math.sin(u * Math.PI) * 0.35 * k;
                    if (fr == 2) spark(c, bf, by, 0, 0, 0, 0, 0.1, 0.34 * k, MAGIC, 0, 0);  /* the ball itself: the page's 0.34 cube, one tick at a time */
                    if (R.nextDouble() < 0.85) spark(c, bf, by, 0, rnd(-.3, .3), rnd(-.3, .3), rnd(-.3, .3), 0.45, 0.11 * k, R.nextDouble() < 0.6 ? MAGIC : MAGIC_L, 0, age); } }
                case BREATH -> { double f = Anim.loop(tf, 3.8); if (f >= 0.1 && f < 0.62) {
                    /* the cone is filled along its whole length each frame: born at a random distance out from the mouth, inside the cone's radius there, drifting on outward */
                    for (int i = 0; i < 14; i++) { double d = rnd(0.15, 4) * k, r = (0.08 + d * 0.3) * Math.sqrt(R.nextDouble()), a2 = rnd(0, 6.283), vy = Math.sin(a2) * r, vz = Math.cos(a2) * r;
                        spark(c, m.z + d, m.y + vy - d * 0.05, -m.x + vz, 2.2 * k, vy * 0.4, vz * 0.4, rnd(0.25, 0.4), rnd(0.12, 0.2) * (0.6 + 0.4 * k) * (0.7 + d * 0.15), R.nextDouble() < 0.5 ? MAGIC : MAGIC_L, 0, age); } } }
                case GUARD -> { double f = Anim.loop(tf, 4.4); if (f < 0.1 || f >= 0.76) break; double w = tf * 4.5;
                    for (double[] b : Layout.props(a, k)) { double x = b[0], z = b[1], r = 0.75, h = 0.95;  /* the players only: the drekki does not take the buff */
                        for (int i = 0; i < 2; i++) { double a2 = w + i * Math.PI + rnd(0, 0.5); spark(c, x + Math.cos(a2) * r, h + Math.sin(tf * 3 + a2) * 0.3, z + Math.sin(a2) * r, -Math.sin(a2) * 1.4, 0, Math.cos(a2) * 1.4, 0.32, 0.13, R.nextDouble() < 0.5 ? STEEL : WHITE, 0, age); }
                        if (R.nextDouble() < 0.3) { double a2 = rnd(0, 6.283); spark(c, x + Math.cos(a2) * r * 0.6, rnd(0.3, h * 1.5), z + Math.sin(a2) * r * 0.6, Math.cos(a2) * 2.4, 0.2, Math.sin(a2) * 2.4, 0.2, 0.1, THORN, 0, age); } } }
                case REJUV -> { double f = Anim.loop(tf, 4.2); if (f <= 0.12 || f >= 0.62) break; double[][] p = Layout.props(a, k);
                    double[][] bodies = {{p[0][0], p[0][1], 1}, {p[1][0], p[1][1], 1}, {0, 0, 1.6 * k}};
                    for (double[] b : bodies) if (R.nextDouble() < 0.7) { double a2 = rnd(0, 6.283), r = rnd(0.2, 0.55) * b[2];
                        spark(c, b[0] + Math.cos(a2) * r, rnd(0.1, 0.5) * b[2], b[1] + Math.sin(a2) * r, 0, rnd(0.9, 1.4), 0, 0.9, 0.12, R.nextDouble() < 0.7 ? HEAL : WHITE, 0, age); } }
                default -> {}
            }
        }
        switch (a) {  /* the bursts, once as the loop crosses their moment */
            case JUMP -> { double f = Anim.loop(t, 2.2); if (cross(lastF, f, 0.42)) burst(c, 0, 0.08, 0, 12, DUST, 2.2, 5, k); e.lastF = f; }
            case FALL -> { double f = Anim.loop(t, 2.6); if (cross(lastF, f, 0.36)) burst(c, 0, 0.08, 0, 20, DUST, 3, 5, k); e.lastF = f; }
            case BITE -> { double f = Anim.loop(t, 1.6), dx = Layout.props(a, k)[0][0];
                if (cross(lastF, f, 0.18)) { burst(c, dx, 1.25 * k, 0, 10, WHITE, 2.2, 6, k); burst(c, dx, 1.25 * k, 0, 6, HAY, 2.2, 6, k); } e.lastF = f; }
            case POUNCE -> { double f = Anim.loop(t, 2.4), dx = Layout.props(a, k)[0][0];
                if (cross(lastF, f, 0.18)) { burst(c, dx, 1.25 * k, 0, 18, WHITE, 3, 6, k); burst(c, dx, 1.25 * k, 0, 10, HAY, 3, 6, k); burst(c, Anim.POUNCE_LEAP * k + 0.3 * k, 0.1, 0, 16, DUST, 2.6, 5, k); } e.lastF = f; }
            case BALL -> { double f = Anim.loop(t, 2.8), tx = Layout.props(a, k)[0][0];
                if (cross(lastF, f, 0.5)) { burst(c, tx, 1.2 * k, 0, 16, MAGIC, 2.6, 4, k); burst(c, tx, 1.2 * k, 0, 8, MAGIC_L, 2, 4, k); } e.lastF = f; }
            case GUARD -> { double f = Anim.loop(t, 4.4);
                if (cross(lastF, f, 0.1)) { ring(c, STEEL); burst(c, 0, 0.3 * k, 0, 10, DUST, 2, 5, k); } e.lastF = f; }
            case REJUV -> { double f = Anim.loop(t, 4.2); if (cross(lastF, f, 0.1)) ring(c, HEAL); e.lastF = f; }
            default -> {}
        }
    }

    /** the ring that runs out from under the drekki */
    private static void ring(Ctx c, int rgb) {
        for (int i = 0; i < 44; i++) { double a2 = i / 44.0 * 6.283; spark(c, 0, 0.08, 0, Math.cos(a2) * 5, 0.15, Math.sin(a2) * 5, 0.75, 0.12, rgb, 0, 0); }
    }

    private static final java.util.Map<String, DrekkiCube.Mode> MODES = java.util.Map.of("fade", DrekkiCube.Mode.FADE, "smoke", DrekkiCube.Mode.SMOKE,
        "burst", DrekkiCube.Mode.BURST, "ember", DrekkiCube.Mode.EMBER, "twinkle", DrekkiCube.Mode.TWINKLE, "glitch", DrekkiCube.Mode.GLITCH);

    /** the page's sparkle() for the rare lines: cubes born in a box around the body, n of them alive at once, each living the config's life */
    private static void sparkle(Ctx c, Lineage L, int stage, double t) {
        Lineage.Sparkle s = L.sparkle(); if (s == null) return;
        double pk = PK[Math.max(1, Math.min(3, stage)) - 1], px = pk / 16, rate = s.n() / s.life() / 20;  /* spawns per tick */
        int n = (int) rate + (R.nextDouble() < rate - (int) rate ? 1 : 0); if (n == 0) return;
        Baked b = DrekkiModels.get(c.e).baked; DrekkiAnimations.pose(b, c.e, t);
        double lift = b.rig.root.y / 16.0, fwd = b.rig.root.z / 16.0;  /* the page parents the cubes to the root: they ride its lift and forward offset */
        float[] box = s.box(); List<Integer> col = L.particles();
        for (int i = 0; i < n; i++) {
            double x = (R.nextDouble() * 2 - 1) * box[0] * px, y = (box[1] + R.nextDouble() * box[2]) * px, z = (R.nextDouble() * 2 - 1) * box[3] * px, bu = s.burst() * px, wn = s.wander() * px;
            double vx = (R.nextDouble() - 0.5) * wn + Math.signum(x) * bu, vy = s.rise() * px * (0.6 + R.nextDouble() * 0.8) + bu * (R.nextDouble() - 0.3), vz = (R.nextDouble() - 0.5) * wn + Math.signum(z) * bu * 0.6;
            int rgb = s.blend() ? Palette.mix(col.get(0), col.get(1), R.nextDouble()) : col.get(R.nextInt(col.size()));
            double w = s.bars() && R.nextDouble() < 0.3 ? (R.nextDouble() < 0.3 ? 6 : 3) : 1, h = s.tall() && R.nextDouble() < 0.25 ? 3 : 1;
            cube(c, fwd + z, lift + y, -x, vz, vy, -vx, s.life(), s.size() * px, rgb, 0, R.nextDouble() * 0.05, s.opacity(), MODES.get(s.mode()), w, h);
        }
    }

    private static double ease(double u) { return u <= 0 ? 0 : u >= 1 ? 1 : 0.5 - 0.5 * Math.cos(u * Math.PI); }

    /** the mouth at this tick from the posed rig, blocks: x right, y up, z forward */
    private static Vector3f mouth(DrekkiEntity e) {
        Baked b = DrekkiModels.get(e).baked;
        DrekkiAnimations.pose(b, e, e.time(0));
        return b.point(b.rig.head, b.rig.mouthLocal, new Vector3f());
    }

    /** the mouth at rest as a forward/up offset: x unused, y up, z forward */
    private static Vector3f restMouth(DrekkiEntity e) { return DrekkiModels.get(e).baked.mouthRest; }
}
