package dev.frosthaven.ranchercompanion.client.rig;

import dev.frosthaven.ranchercompanion.look.Dims;
import dev.frosthaven.ranchercompanion.look.Gear;

/**
 * The design page's barding plates on a stage 3 rig, one to one: breastplate, neck bands and throat plates, face and
 * brow plates, greaves and sabatons, tail rings. Each plate's box kind is {@link #KIND} plus the tier, so the bake paints
 * it with that material's texture. Every plate has its own whole size and a part-pixel offset from its neighbours, so
 * no two faces share a plane.
 */
final class BardingRig {
    private BardingRig() {}

    /** box kind of a barding plate: KIND + tier (1 wood .. 7 netherite) */
    static final int KIND = 10;

    private static int R(double v) { return Math.round((float) v); }

    static void build(Rig r, int tier, int dye) {
        Dims D = r.D; int kind = KIND + tier, pl = Gear.plate(tier, dye), rim = Gear.rim(tier, dye);
        float bw = D.body[0], bh = D.body[1], bd = D.body[2];
        float nw = D.neck[0], nh1 = D.neck[1], nh2 = D.neck[2], nd = D.neck[3];
        float hw = D.head[0], hh = D.head[1], hd = D.head[2], mw = D.muzzle[0], md = D.muzzle[2];
        float sw = D.shank[0], sh = D.shank[1], sd = D.shank[2], fw = D.foot[0], fh = D.foot[1], fd = D.foot[2];
        float mTop = hh - 1 - 1.1f, mZ = hd - 2 + md / 2 - 0.8f;

        /* chest: a breastplate standing just in front of the chest patch, a rim along its top, a side plate on each side
           kept in front of the wing roots */
        int pw = R(bw) + 1, ph = R(bh * 0.7); double py = -bh * 0.05, pz = bd / 2 + 0.8;
        plate(r, r.body, pw, ph, 1, 0, py, pz, pl, kind); plate(r, r.body, pw + 2, 1, 2, 0, py + ph / 2.0 - 0.3, pz + 0.1, rim, kind);
        for (int s : new int[]{-1, 1}) plate(r, r.body, 1, R(bh * 0.6), 2, s * (pw / 2.0 + 0.2), py, bd / 2 - 0.4, pl, kind);

        /* neck: two bands on each segment, deep enough to stand clear of the trim stripe, and a throat plate narrower
           than the stripe */
        Object[][] segs = {{r.neck1, R(nw), R(nd), nh1}, {r.neck2, R(nw - 0.6), R(nd - 0.6), nh2}};
        for (Object[] sg : segs) { Node g = (Node) sg[0]; int w = (int) sg[1], d = (int) sg[2]; float h = (float) sg[3];
            for (double f : new double[]{0.3, 0.75}) plate(r, g, w + 1, 1, d + 2, 0, h * f, 0, pl, kind);
            plate(r, g, w - 2, R(h) - 1, 1, 0, h / 2, (d + 2) / 2.0 + 0.2, rim, kind); }

        /* head: a face plate that tops out above the head's top, a rim set back from the snout's front, and a brow plate
           that reaches over the face plate's back edge */
        int fpd = R(md * 0.85); double fz = mZ - 0.2, top = hh / 2 - 1 + R(hh) / 2.0;
        plate(r, r.head, R(mw) + 1, 2, fpd, 0, top - 0.7, fz, pl, kind); plate(r, r.head, R(mw) + 2, 3, 1, 0, top - 0.9, fz + fpd / 2.0 - 0.9, rim, kind);
        plate(r, r.head, R(hw) - 2, 1, R(hd * 0.45) + 1, 0, top + 0.3, hd / 2 - 0.8, pl, kind);

        /* legs: a greave down the front of each shank and a sabaton over the front half of the foot, from just inside the
           ankle to just before the toes and talons */
        double z0 = R(sd + 0.6) / 2.0 - 0.5, z1 = fd - sd / 2 - 0.5, ftop = -fh / 2 + R(fh) / 2.0;
        for (Rig.Leg L : r.legs) {
            plate(r, L.knee, R(sw) + 1, R(sh) - 2, 1, 0, -sh / 2 - 0.3, R(sd) / 2.0 + 0.8, pl, kind);
            plate(r, L.knee, R(sw) + 2, 1, 2, 0, -1.6, R(sd) / 2.0 + 0.7, rim, kind);
            plate(r, L.ankle, R(fw) + 1, 2, R(z1 - z0), 0, ftop - 0.4, (z0 + z1) / 2, pl, kind);
        }

        /* tail: a ring around each segment but the tip, which carries its own band */
        for (int i = 0; i < D.tail.length - 1; i++) { Node g = i == 0 ? r.tail : r.tailSegs.get(i - 1); float a = D.tail[i][0], b = D.tail[i][1], c = D.tail[i][2];
            plate(r, g, R(a) + 1, R(b) + 1, 2, 0, 0, -c / 2 + 0.5, pl, kind); }
    }

    private static void plate(Rig r, Node p, double w, double h, double d, double x, double y, double z, int color, int kind) {
        p.boxes.add(new Node.Box(Math.max(1, R(w)), Math.max(1, R(h)), Math.max(1, R(d)), (float) x, (float) y, (float) z, color, 0, 0, 0, kind, 0));
    }
}
