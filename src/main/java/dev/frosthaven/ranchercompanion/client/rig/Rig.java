package dev.frosthaven.ranchercompanion.client.rig;

import dev.frosthaven.ranchercompanion.look.Astral;
import dev.frosthaven.ranchercompanion.look.Diet;
import dev.frosthaven.ranchercompanion.look.Dims;
import dev.frosthaven.ranchercompanion.look.Palette;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The drekki rig, a straight port of the design page's buildCreature: the same groups, cubes and base angles, so the
 * page's animations drive it unchanged. Built in three.js space (y up, z forward, 16 px per block).
 */
public final class Rig {
    public static final int TONGUE = 0xa85a5a, BONE = 0xe3dcc6, EBONY = 0x2b2622, DARK = 0x2a2a2c, SADDLE = 0x7a4a2a, SADDLE_D = 0x4b2c17, IRON = 0x9a9a9a;

    /** What a build needs: the look of one drekki. */
    /** trim: the lighter trim color, or -1 for a lighter shade of the fur (the page's o.trim). */
    /** hat: Gear.HATS index (0 none); barding: Gear.BARDING tier (0 none, stage 3 only); dye: an RGB barding dye or -1. */
    /** sheenHex and sheen: the line's glow color and strength (0 for no sheen); see: a see-through line (Bakeneko). */
    public record Look(int stage, int fur, int trim, int legTint, int astral, Diet diet, int horn, boolean spread, int hat, int barding, int dye, int sheenHex, float sheen, boolean see,
                       int dye2, int bag, int bagDye, int bagDye2) {
        /** no barding trim dye and no bag */
        public Look(int stage, int fur, int trim, int legTint, int astral, Diet diet, int horn, boolean spread, int hat, int barding, int dye, int sheenHex, float sheen, boolean see) {
            this(stage, fur, trim, legTint, astral, diet, horn, spread, hat, barding, dye, sheenHex, sheen, see, -1, 0, -1, -1);
        }
    }

    public static final class Leg {
        public final Node thigh, knee, ankle;
        public final int s;
        public final float t0, k0, a0;
        Leg(Node thigh, Node knee, Node ankle, int s) { this.thigh = thigh; this.knee = knee; this.ankle = ankle; this.s = s; t0 = thigh.rx; k0 = knee.rx; a0 = ankle.rx; }
    }

    public final Look look;
    public final Dims D;
    public final boolean carn;
    public final Node root, body, neck1, neck2, head, jaw, tail;
    public final List<Leg> legs = new ArrayList<>();
    public final List<Node> wings = new ArrayList<>(), tailSegs = new ArrayList<>(), lids = new ArrayList<>(), all = new ArrayList<>();
    public final float[] wingS;
    public final Vector3f mouthLocal;
    /** the propeller beanie's propeller, spun by the idle loop; null for every other hat */
    public Node hatSpin;
    /** the fur shades the sheen lights, as the page's SHEEN_COLORS: fur, trim and the two dark shades; empty without a sheen */
    public final java.util.Set<Integer> sheenColors = new java.util.HashSet<>();
    public final float bodyY, hipY, tailBase, neck1Base, neckBase, headBase, crouchPx, th, sh, fh;
    private int cubeCount;
    private final int HORN;

    private static double rad(double d) { return Math.toRadians(d); }
    private static float sn(double v) { return v < 0.35 ? 0 : Math.max(1, Math.round((float) v)); }

    Node grp(Node parent, double x, double y, double z) { return grp(parent, x, y, z, 0); }
    Node grp(Node parent, double x, double y, double z, double rx) { Node n = new Node(parent, x, y, z, rx); all.add(n); return n; }

    void cube(Node p, double w, double h, double d, double x, double y, double z, int color) { cube(p, w, h, d, x, y, z, color, 0, 0, 0); }
    void cube(Node p, double w, double h, double d, double x, double y, double z, int color, double rx, double ry, double rz) {
        cubeCount++;
        p.boxes.add(new Node.Box(sn(w), sn(h), sn(d), (float) x, (float) y, (float) z, color, (float) rx, (float) ry, (float) rz, 0, 0));
    }

    public Rig(Look o) {
        this.look = o;
        int stage = o.stage;
        HORN = (o.diet.ebony && stage > 1) ? EBONY : BONE;  /* diet locks at the start of stage 2, so stage 1 horns are always ivory */
        D = Dims.of(stage);
        int fur = o.fur, furL = o.trim >= 0 ? o.trim : Palette.trim(fur), furD = Palette.shade(fur, -.3), furD2 = Palette.shade(fur, -.45), foot = Palette.legTint(o.legTint, fur);
        if (o.sheen > 0) { sheenColors.add(fur); sheenColors.add(furL); sheenColors.add(furD); sheenColors.add(furD2); }
        int mem = o.trim >= 0 ? o.trim : furD2;  /* membrane panels: a line's own trim when it has one (rare and ultra rare lines), else the darkest fur shade */
        carn = o.diet.carnivore;
        int leg = carn ? mem : furL, bone = o.trim >= 0 ? fur : carn ? furD : furL;  /* wing arm: body color for a line with its own trim, so the trim panels stand apart */  /* shank in the wing's main color, foot in the biome tint */
        float bw = D.body[0], bh = D.body[1], bd = D.body[2], tw = D.thigh[0]; th = D.thigh[1]; float td = D.thigh[2];
        float sw = D.shank[0]; sh = D.shank[1]; float sd = D.shank[2], fw = D.foot[0]; fh = D.foot[1]; float fd = D.foot[2];
        hipY = D.hipY(); bodyY = D.bodyY();
        root = grp(null, 0, 0, 0);
        body = grp(root, 0, bodyY, 0);
        if (D.rump) { cube(body, bw, bh, bd * 0.5, 0, 0, bd * 0.25, fur); cube(body, bw - 2, bh - 1.6, bd * 0.5 + 0.6, 0, 0.6, -bd * 0.25, fur);
            cube(body, bw - 1.5, 1.2, bd * 0.5 - 1, 0, -bh / 2 + 0.2, bd * 0.25, furL); }
        else { cube(body, bw, bh, bd, 0, 0, 0, fur); cube(body, bw - 1.5, 1.2, bd - 3, 0, -bh / 2 + 0.2, 0.5, furL); }
        cube(body, bw - 2, bh * 0.5, 1.6, 0, -bh * 0.15, bd / 2 - 0.1, D.rump ? foot : furL);  /* chest patch: foot tint from stage 2 */

        float ow = D.toe[0], oh = D.toe[1], od = D.toe[2];
        for (int s : new int[]{-1, 1}) {
            Node hip = grp(body, s * D.hipX, -bh / 2 + D.hipSink, D.hipZ);
            Node thigh = grp(hip, 0, 0, 0, -rad(D.thighA));
            Node knee = grp(thigh, 0, -th, 0, rad(D.thighA + D.shankA));
            Node ankle = grp(knee, 0, -sh, 0, -rad(D.shankA));
            legs.add(new Leg(thigh, knee, ankle, s));
            if (D.legDetail) {
                cube(thigh, tw + 1, th * 0.62, td + 1, 0, -th * 0.3, 0.2, fur); cube(thigh, tw, th * 0.55, td, 0, -th * 0.72, 0, fur); cube(thigh, tw * 0.6, th * 0.4, 1, 0, -th * 0.35, -td / 2 - 0.4, furL);
                cube(knee, sw + 0.8, 1.8, sd + 0.8, 0, 0.1, 0, leg); cube(knee, sw + 0.3, sh * 0.5, sd + 0.3, 0, -sh * 0.25, 0, leg); cube(knee, sw, sh * 0.55, sd, 0, -sh * 0.72, 0, leg);
                cube(ankle, sw + 0.6, 1.6, sd + 0.6, 0, 0.1, 0, leg);
                cube(ankle, fw, fh, fd, 0, -fh / 2, fd / 2 - sd / 2, foot);
                cube(ankle, fw * 0.65, fh * 0.85, 1.6, 0, -fh / 2 + 0.1, -sd / 2 - 0.5, foot);
                for (int tx = -1; tx <= 1; tx++) { Node tg = grp(ankle, tx * (fw / 2 - ow / 2 + 0.1), -fh, fd - sd / 2 - 0.4); tg.ry = (float) (tx * rad(16));
                    cube(tg, ow, oh, od * 0.55, 0, oh / 2, od * 0.275, foot); cube(tg, ow * 0.85, oh * 0.8, od * 0.5, 0, oh * 0.4, od * 0.8 - 0.1, foot);
                    if (carn) cube(tg, ow * 0.6, oh * 0.6, D.talon, 0, oh * 0.3, od * 1.05 + D.talon / 2 - 0.3, HORN); }
                Node hx = grp(ankle, 0, -fh, -sd / 2 - 1.2); cube(hx, ow * 0.8, oh * 0.8, od * 0.45, 0, oh * 0.4, -od * 0.22, foot);
                if (carn) cube(hx, ow * 0.5, oh * 0.5, D.talon * 0.7, 0, oh * 0.3, -od * 0.45 - D.talon * 0.35 + 0.2, HORN);
            } else {
                cube(thigh, tw, th + 0.6, td, 0, -th / 2, 0, fur);
                cube(knee, sw, sh + 0.6, sd, 0, -sh / 2, 0, leg);
                cube(ankle, fw, fh, fd, 0, -fh / 2, fd / 2 - sd / 2, foot);
                for (int tx = -1; tx <= 1; tx++) { double x = tx * (fw / 2 - ow / 2 + 0.15), z = fd - sd / 2 + od / 2 - 0.3;
                    cube(ankle, ow, oh, od, x, -fh + oh / 2, z, foot);
                    if (carn) cube(ankle, ow * 0.7, oh * 0.7, D.talon, x, -fh + oh * 0.35, z + od / 2 + D.talon / 2 - 0.2, HORN); }
            }
        }

        /* two-segment neck: lower leans forward, upper comes back toward vertical */
        float nw = D.neck[0], nh1 = D.neck[1], nh2 = D.neck[2], nd = D.neck[3], a1 = D.neckA[0], a2 = D.neckA[1];
        neck1 = grp(body, 0, bh / 2 - 1.5, bd / 2 - nd / 2 - 0.5, rad(a1)); neck1Base = neck1.rx;
        cube(neck1, nw, nh1 + 1, nd, 0, nh1 / 2, 0, fur); cube(neck1, nw - 1.4, nh1, 0.8, 0, nh1 / 2, nd / 2 - 0.2, furL);
        neck2 = grp(neck1, 0, nh1, 0, rad(a2)); neckBase = neck2.rx;
        cube(neck2, nw - 0.6, nh2 + 1, nd - 0.6, 0, nh2 / 2, 0, fur); cube(neck2, nw - 1.8, nh2, 0.8, 0, nh2 / 2, nd / 2 - 0.5, furL);
        head = grp(neck2, 0, nh2, 0, -rad(a1 + a2) + rad(D.headTilt)); headBase = head.rx;

        float hw = D.head[0], hh = D.head[1], hd = D.head[2], mw = D.muzzle[0], mh = D.muzzle[1], md = D.muzzle[2], jw = D.jaw[0], jh = D.jaw[1], jd = D.jaw[2];
        cube(head, hw, hh, hd, 0, hh / 2 - 1, hd / 2 - 2, fur);
        float mTop = hh - 1 - 1.1f, mY = mTop - mh / 2, mZ = hd - 2 + md / 2 - 0.8f;
        mouthLocal = new Vector3f(0, mY - mh / 2, mZ + md / 2);
        cube(head, mw, mh, md, 0, mY, mZ, fur);
        cube(head, 1.2, 0.5, md * 0.8, 0, mTop + 0.1, mZ - 0.2, furD);
        jaw = grp(head, 0, mY - mh / 2 + 0.25, hd - 2 - 0.8);  /* hinged at the back so the mouth opens; a tongue shows inside */
        cube(jaw, jw, jh, jd, 0, -jh / 2, jd / 2, furL); cube(jaw, jw - 1.2, 0.4, jd - 1.4, 0, 0.1, jd / 2 - 0.4, TONGUE);
        if (D.hook) { float jawBot = mY - mh / 2 - jh + 0.25f, hookH = mY - jawBot + 0.4f;
            cube(head, mw * 0.4, hookH, 1, 0, mY - hookH / 2, mZ + md / 2 - 0.3, fur); }
        for (int s : new int[]{-1, 1}) cube(head, 0.7, 0.5, 0.7, s * (mw / 2 - 0.6), mTop, mZ + md / 2 + 0.1, DARK);
        cube(head, mw + 1, 0.5, md - 1, 0, mY - mh / 2, mZ - 0.3, DARK);
        int mlw = Math.round(mw + 1);
        if (carn && D.fang > 0) for (int s : new int[]{-1, 1}) cube(head, 0.6, mh * D.fang, 0.6, s * (mlw / 2.0 + 0.3), mY - mh / 2 - mh * D.fang * 0.25, mZ + md / 2 - 1.1, BONE);
        float ew = D.eye[0], eh = D.eye[1], eyeY = hh * 0.5f - 1, eyeZ = hd * 0.58f - 2; int hwS = Math.round(hw);
        /* the page's eye is a plane on the head's side; here it is a one pixel thick box half sunk into the head, whole pixels, so only
           its outer face shows (a flat box draws both faces on one plane and they fight). The lid is the same box a little further out */
        for (int s : new int[]{-1, 1}) {
            head.boxes.add(new Node.Box(1, sn(eh), sn(ew), s * (hwS / 2f), eyeY, eyeZ, Astral.iris(o.astral), 0, 0, 0, 1, s));
            Node lid = grp(head, s * (hwS / 2f + 0.35f), eyeY, eyeZ); lid.boxes.add(new Node.Box(1, sn(eh), sn(ew), 0, 0, 0, furD, 0, 0, 0, 2, s)); lids.add(lid);
            cube(head, 1, 0.8, ew + 0.6, s * (hwS / 2f + 0.5f), eyeY + eh / 2 + 0.5, eyeZ, furD2);
        }
        for (int s : new int[]{-1, 1}) { if (D.hornParts) hornsMulti(head, o.horn, s, D.horn[0], D.horn[1], hw, hh, foot); else horns(head, o.horn, s, D.horn[0], D.horn[1], hw, hh); }
        float rw = D.ear[0], rh = D.ear[1], rd = D.ear[2];
        for (int s : new int[]{-1, 1}) {
            Node e = grp(head, s * (hw / 2 + 0.2), hh * 0.6 - 1, -1); e.rot(-rad(20), s * rad(-35), 0); cube(e, rw, rh, rd, 0, 0, -rd / 2, furD);
            Node e2 = grp(head, s * (hw / 2 + 0.1), hh * 0.28 - 1, -1.4); e2.rot(-rad(5), s * rad(-55), 0); cube(e2, rw, rh * 0.7, rd * 0.7, 0, 0, -rd * 0.35, furD);
            Node e3 = grp(head, s * (hw / 2 - 0.6), hh - 1.2, -1.6); e3.rot(-rad(40), s * rad(-20), 0); cube(e3, rw, rh * 0.55, rd * 0.55, 0, 0, -rd * 0.28, furD);
        }
        if (o.hat > 0) { /* a hat covers the crest; horns poke through */ }
        else if (D.rump && !carn) { /* herbivore head crest: light plumes rising from the skull and arching back, dark tips */
            for (int i = 0; i < D.crest; i++) { Node r = grp(head, 0, hh - 1.3, hd / 2 - 2 - i * 2.2 + 0.4); r.rot(rad(58 - i * 8), 0, 0);
                float L1 = D.crestH * 1.1f * (1 - i * 0.12f), L2 = D.crestH * 0.9f * (1 - i * 0.12f), bh2 = D.crestH * 0.55f;
                cube(r, 1, bh2, L1, 0, 0, -L1 / 2, furL); Node tip = grp(r, 0, 0, -L1, -rad(42)); cube(tip, 1, bh2 * 0.8, L2, 0, 0, -L2 / 2, furD); } }
        else for (int i = 0; i < D.crest; i++) { float ch = D.crestH * (1 - i * 0.18f); cube(head, 0.8, ch, 2.2, 0, hh - 1 + ch / 2 - 0.6, hd / 2 - 2 - i * 2.2 + 0.4, furD2, -rad(38), 0, 0); }

        if (o.hat > 0) hatSpin = Hats.build(this, grp(head, 0, hh - 1, hd / 2 - 2), o.hat, hwS / 8.0);

        /* dorsal line: dark fins on carnivores, feather tufts on herbivores from stage 2 */
        float sf = D.spineLen, kk = bh / 11;
        spine(neck1, 0, nh1 * 0.35, -nd / 2 + 0.2, kk * 0.8, sf, furL, furD, furD2); spine(neck1, 0, nh1 * 0.75, -nd / 2 + 0.2, kk * 0.8, sf, furL, furD, furD2);
        BagRig.Layout bagAt = o.bag > 0 && stage >= dev.frosthaven.ranchercompanion.look.Gear.BAG_STAGE[o.bag] ? BagRig.layout(D, o.bag) : null;  /* no spines under the bags, as under the saddle */
        for (double z = bd / 2 - 2.5; z > -bd / 2 + 2; z -= 3.4 * kk) { if (D.saddle && z < D.saddleZ + D.saddleD / 2 + 1 && z > D.saddleZ - D.saddleD / 2 - 1) continue;
            if (bagAt != null && z < bagAt.z() + bagAt.d() / 2 + 1 && z > bagAt.z() - bagAt.d() / 2 - 1) continue; spine(body, 0, bh / 2 + 0.5 * kk, z, kk, sf, furL, furD, furD2); }

        /* wings */
        float pt = D.wingPanel[0], ph2 = D.wingPanel[1], pl = D.wingPanel[2];
        for (int s : new int[]{-1, 1}) {
            if (o.spread) spreadWing(body, s, fur, furD, bone, mem, furL);
            else if (D.wingFlank) {
                double tilt = rad(18);
                Node wg = grp(body, s * (bw / 2 - 0.5), bh / 2 - 0.5, bd / 2 - 2); wg.rot(rad(6), -s * rad(6), s * tilt); wings.add(wg);
                if (carn) {
                    float aw = D.wingArm[0], aL = D.wingArm[1], fw2 = D.wingFin[0], fL = D.wingFin[1] * 1.45f;
                    /* a line with its own trim: the arm runs along the panels' top edge, centered on them and wider, so it overhangs both faces */
                    boolean t = o.trim >= 0; double ax = t ? s * 0.45 : 0, aW = t ? Math.max(aw * 1.3, pt + 1) : aw, fW = t ? Math.max(fw2 * 1.3, pt + 0.8) : fw2, aH = t ? aw * 1.3 : aw, fH = t ? fw2 * 1.3 : fw2, tp = t ? 0 : 1;  /* tp 0: panels top out at the arm's middle and start at the joints, so no panel pokes past the arm */
                    Node arm = grp(wg, 0, 0, 0, rad(8)); cube(arm, aW, aH, aL, ax, 0, -aL / 2, bone);
                    cube(arm, pt, ph2, aL + tp, s * 0.45, tp * aw / 2 - ph2 / 2, tp * 0.5 - aL / 2, mem);
                    Node elbow = grp(arm, 0, 0, -aL, rad(30)); cube(elbow, fW, fH, fL, ax, 0, -fL / 2, bone);
                    cube(elbow, pt, ph2 * 0.75, fL * 0.88, s * 0.45, tp * fw2 / 2 - ph2 * 0.375, -fL * 0.44, mem);
                    Node mid = grp(arm, 0, 0, -aL, -rad(25)); float mL = aL * 0.5f + fL * 0.55f; cube(mid, pt, ph2 * 0.9, mL, s * 0.55, tp * aw / 2 - ph2 * 0.45, tp * 2 - mL / 2, mem);
                } else featherWing(wg, s, pt, ph2, D.plumeHerb[1], fur, furD, furL, bone, o.trim >= 0);
            } else if (carn) {
                Node sh2 = grp(body, s * (bw / 2 + 0.2), bh / 2 - 1, bd / 2 - 3); sh2.rot(-rad(40), 0, -s * rad(22)); wings.add(sh2);
                cube(sh2, D.wingArm[0], D.wingArm[1], D.wingArm[0], s * 0.2, D.wingArm[1] / 2, 0, bone);
                Node elbow = grp(sh2, s * 0.2, D.wingArm[1], 0, rad(60)); cube(elbow, D.wingFin[0], D.wingFin[1], D.wingFin[0], 0, -D.wingFin[1] / 2, 0, bone);
                Node pg = grp(body, s * (bw / 2 + 0.2), bh / 2 - 0.2, bd / 2 - 3); pg.rz = (float) (-s * rad(22)); cube(pg, pt, ph2, pl, s * (pt / 2 + 0.3), -ph2 / 2, 0.3 - pl / 2, mem);
            } else {
                /* hatchling: layered covert rows and a small fan of primaries */
                Node wg = grp(body, s * (bw / 2 + 0.2), bh / 2 - 0.2, bd / 2 - 3); wg.rz = (float) (-s * rad(18)); wings.add(wg);
                float rh2 = ph2 * 0.42f; int[] rowCol = {furD, fur, furL};
                for (int i = 0; i < 3; i++) { float rl = pl * (0.7f + i * 0.15f); cube(wg, pt + 0.2, rh2, rl, s * (pt / 2 + 0.3), -rh2 / 2 - i * rh2 * 0.75, 0.3 - rl / 2 - i * 0.6, rowCol[i]); }
                for (int i = 0; i < 3; i++) { Node f = grp(wg, s * (pt / 2 + 0.3), -rh2 * 2.1, 0.9 - pl); f.rot(rad(6 + i * 9), s * rad(-5 * i), 0);
                    float L = D.wingFin[1] * (1 - i * 0.12f); cube(f, 0.5, rh2 * 0.8, L, 0, 0, -L / 2, i == 2 ? furL : fur); cube(f, 0.55, rh2 * 0.8, L * 0.3, 0, 0, -L * 0.85, furD); }
            }
        }
        wingS = new float[wings.size()];
        for (int i = 0; i < wings.size(); i++) wingS[i] = Math.signum(wings.get(i).x) == 0 ? 1 : Math.signum(wings.get(i).x);

        tail = grp(body, 0, -1, -bd / 2 + 1, rad(D.tailUp)); tailBase = tail.rx;
        Node cur = tail;
        for (int i = 0; i < D.tail.length; i++) { float a = D.tail[i][0], b = D.tail[i][1], c = D.tail[i][2];
            cube(cur, a, b, c, 0, 0, -c / 2 + 0.5, fur);
            if (D.rump && i == D.tail.length - 1) cube(cur, a + 0.6, b + 0.6, 2, 0, 0, -c + 1.8, foot);  /* trim band near the tip, foot tint */
            if (D.rump && !carn) for (double ry : new double[]{0, -0.45, 0.45}) tuft(cur, ry * 1.6, b / 2 - 0.2 - (ry != 0 ? 0.4 : 0), -c / 2 + 1.4, b * (ry != 0 ? 0.9 : 1.2), c * 0.6 * (ry != 0 ? 0.85 : 1), ry, furL, furD);
            else { float finH = b * 0.75f; cube(cur, 0.6, finH, c * 0.5, 0, b / 2 + finH / 2 - 0.3, -c / 2 + 0.5, furD2, -rad(10), 0, 0); }
            Node nx = grp(cur, 0, 0, -c + 0.5, rad(4)); tailSegs.add(nx); cur = nx; }
        float[] plume = carn ? D.plumeCarn : D.plumeHerb; float pw = plume[0], ph = plume[1], pd = plume[2]; int n = (int) plume[3];
        double spread = n > 1 ? rad(carn ? 50 : 100) / (n - 1) : 0; float lc = D.tail[D.tail.length - 1][2];
        for (int s : new int[]{-1, 1}) { Node fin = grp(cur, s * 0.3, 0, 0.3); fin.ry = (float) (s * rad(-18)); cube(fin, lc * 0.7, 0.5, lc, s * lc * 0.35, 0, -lc * 0.5, furD2); }
        if (carn || D.sickleSeg == null) {
            for (int i = 0; i < n; i++) { Node f = grp(cur, 0, 0.3, 0); f.rot(rad(38), (i - (n - 1) / 2.0) * spread, 0);
                cube(f, pw, ph, pd, 0, ph * 0.1, -pd / 2, furL); cube(f, pw, ph * 0.5, pd * 0.45, 0, ph * 0.1, -pd * 1.2 + 0.2, furD); }
        } else {
            float[] seg = D.sickleSeg; double[] bends = {0, -35, -40};
            for (int i = 0; i < D.sickleN; i++) for (int s : new int[]{-1, 1}) {
                float k = 1 - i * 0.22f; Node r = grp(cur, s * (0.3 + i * 0.5), 0.3, 0.2 - i * 0.5); r.rot(rad(62 - i * 8), -s * rad(8 + i * 10), 0);
                Node g = r; for (int j = 0; j < seg.length; j++) { Node sg = grp(g, 0, 0, 0, rad(bends[j])); float len = seg[j] * k; cube(sg, 0.5, ph * (1 - j * 0.22), len, 0, 0, -len / 2, j == 2 ? furD : furL); g = grp(sg, 0, 0, -len); }
            }
        }

        if (D.saddle) {
            float top = bh / 2, s2 = bw - 3, sdp = D.saddleD, sz = D.saddleZ;
            cube(body, s2, 1.6, sdp, 0, top + 0.6, sz, SADDLE);
            cube(body, s2 + 1, 2.2, 1.6, 0, top + 1.8, sz - sdp / 2 + 0.8, SADDLE_D);
            cube(body, s2 * 0.55, 1.6, 1.6, 0, top + 1.6, sz + sdp / 2 - 0.8, SADDLE_D);
            if (!D.wingFlank) cube(body, bw + 0.6, bh + 0.6, 1.6, 0, 0, sz, SADDLE_D);
            for (int s : new int[]{-1, 1}) {
                if (D.wingFlank) { float yb = bh / 2 - 0.5f - D.wingPanel[1] * 0.95f - 1.4f; cube(body, 0.3, top + 0.3 - yb, 1.2, s * (bw / 2 + 0.2), (top + 0.3 + yb) / 2, sz + 0.5, SADDLE_D); cube(body, 1.6, 1.8, 2.6, s * (bw / 2 + 0.3), yb - 0.6, sz + 0.5, IRON); }
                else { cube(body, 0.7, 5.5, 1.4, s * (bw / 2 + 0.35), top - 2.5, sz + 0.5, SADDLE_D); cube(body, 1.6, 1.8, 2.6, s * (bw / 2 + 0.6), top - 5.8, sz + 0.5, IRON); }
            }
        }
        if (o.barding > 0 && stage >= 2) BardingRig.build(this, o.barding, o.dye, o.dye2);
        if (bagAt != null) BagRig.build(this, o.bag, o.bagDye, o.bagDye2);
        crouchPx = bh * 0.4f;
        for (Node nd0 : all) nd0.bake();
    }

    private void spine(Node parent, double x, double y, double z, double k, double sf, int furL, int furD, int furD2) {
        if (D.rump && !carn) tuft(parent, x, y - 0.3, z + 0.8, 1.1 * k, 2.4 * k * sf, 0, furL, furD);
        else cube(parent, 0.6, 1.5 * k, 1.6 * k * sf, x, y, z, furD2, -rad(10), 0, 0);
    }

    private void tuft(Node parent, double x, double y, double z, double h, double L, double ry, int furL, int furD) {
        Node r = grp(parent, x, y, z); r.rot(rad(22), ry, 0); cube(r, 1, h, L, 0, h * 0.3, -L / 2, furL);
        Node t = grp(r, 0, 0, -L, -rad(26)); cube(t, 1, h * 0.8, L * 0.6, 0, h * 0.3, -L * 0.3, furD);
    }

    private void horns(Node head, int style, int s, float T, float L, float hw, float hh) {
        float topY = hh - 1, backZ = -0.8f;
        if (style == 0) { cube(head, T, L, T, s * (hw / 2 - T / 2 - 0.4), topY + L / 2 - 1, backZ, HORN, -rad(50), 0, s * rad(10)); }
        else if (style == 1) { Node g = grp(head, s * (hw / 2 + T / 2 - 0.1), topY - 0.4, -0.6);
            cube(g, T, T, L * 0.5, 0, 0, -L * 0.25, HORN); cube(g, T, L * 0.45, T, 0, -L * 0.225, -L * 0.5, HORN); cube(g, T * 0.9, T * 0.9, L * 0.4, 0, -L * 0.45, -L * 0.3, HORN); }
        else if (style == 2) { Node g = grp(head, s * (hw / 2 - T / 2 - 0.4), topY - 1, backZ, -rad(40)); cube(g, T, L * 0.85, T, 0, L * 0.42, 0, HORN);
            Node t1 = grp(g, 0, L * 0.38, 0); t1.rot(rad(25), 0, s * rad(40)); cube(t1, T * 0.8, L * 0.45, T * 0.8, 0, L * 0.22, 0, HORN);
            Node t2 = grp(g, 0, L * 0.7, 0); t2.rot(rad(35), 0, s * rad(-12)); cube(t2, T * 0.8, L * 0.4, T * 0.8, 0, L * 0.2, 0, HORN); }
        else if (style == 3) { Node g = grp(head, s * (hw / 2 - 0.2), hh * 0.72 - 1, -0.5); g.rz = (float) (s * rad(-62)); cube(g, T, L * 0.5, T, 0, L * 0.25, 0, HORN);
            Node g2 = grp(g, 0, L * 0.48, 0); g2.rot(rad(45), 0, s * rad(35)); cube(g2, T * 0.9, L * 0.5, T * 0.9, 0, L * 0.25, 0, HORN); }
        else { for (double[] q : new double[][]{{0.36, 0.9, 20}, {0.13, 0.72, 38}}) cube(head, T * 0.9, L * q[1], T * 0.9, s * hw * q[0], topY - 1.2 + L * q[1] * 0.4, -1.5, HORN, -rad(55), 0, s * rad(q[2])); }
    }

    private Node hornChain(Node parent, double T, double L, int n, double bend, double taper, double twist, int baseCol) {
        Node g = parent; double t = T, segL = L / n;
        for (int i = 0; i < n; i++) { Node sg = grp(g, 0, 0, 0); sg.rot(rad(i > 0 ? bend : 0), 0, rad(i > 0 ? twist : 0)); cube(sg, t, segL + 0.3, t, 0, segL / 2, 0, i == 0 && baseCol != 0 ? baseCol : HORN); g = grp(sg, 0, segL, 0); t *= taper; }
        return g;
    }

    private void hornsMulti(Node head, int style, int s, float T, float L, float hw, float hh, int base) {
        float topY = hh - 1, backZ = -0.8f;
        if (style == 0) { Node g = grp(head, s * (hw / 2 - T / 2 - 0.4), topY - 0.5, backZ); g.rot(-rad(35), 0, s * rad(-14)); hornChain(g, T, L, 3, -14, 0.82, s * -5, base); }
        else if (style == 1) { Node g = grp(head, s * (hw / 2 + T / 2 - 0.1), topY - 0.6, backZ); g.rot(-rad(60), 0, s * rad(5)); hornChain(g, T, L * 1.7, 5, -40, 0.86, 0, base); }
        else if (style == 2) { Node g = grp(head, s * (hw / 2 - T / 2 - 0.4), topY - 0.8, backZ); g.rot(-rad(35), 0, s * rad(-14)); Node p = g; double t = T, segL = L * 0.9 / 3;
            for (int i = 0; i < 3; i++) { Node sg = grp(p, 0, 0, 0); sg.rot(rad(i > 0 ? -8 : 0), 0, 0); cube(sg, t, segL + 0.3, t, 0, segL / 2, 0, i == 0 ? base : HORN);
                Node tine = grp(sg, 0, segL * 0.8, 0); tine.rot(rad(25), 0, s * rad(i == 0 ? -40 : i == 1 ? 18 : -15)); hornChain(tine, t * 0.8, L * 0.45 * (1 - i * 0.15), 2, -12, 0.8, 0, 0); p = grp(sg, 0, segL, 0); t *= 0.85; } }
        else if (style == 3) { Node g = grp(head, s * (hw / 2 - 0.2), hh * 0.72 - 1, -0.5); g.rot(0, 0, s * rad(-65)); hornChain(g, T, L, 3, 28, 0.8, s * 22, base); }
        else { float r = hw / 2;
            for (double[] q : new double[][]{{26, 0.9}, {66, 0.68}}) { double a = rad(q[0]); Node g = grp(head, s * r * Math.sin(a), topY - 0.8 - (1 - Math.cos(a)) * hh * 0.5, -1.5); g.rot(-rad(22), 0, -s * a); hornChain(g, T * 0.9, L * q[1], 2, -8, 0.75, 0, base); }
            if (s > 0) { Node g = grp(head, 0, topY - 0.6, -1.5); g.rot(-rad(28), 0, 0); hornChain(g, T, L, 2, -8, 0.75, 0, base); } }
    }

    /** herbivore wing (stages 2 and 3), on the same frame as the carnivore's, in the light fur shade */
    private void featherWing(Node wg, int s, float pt, float ph2, float ph, int fur, int furD, int furL, int bone, boolean own) {  /* own trim: a thick arm that overhangs the panels and the covert band on both faces */
        float aw = D.wingArm[0], aL = D.wingArm[1], fL = D.wingFin[1], back = 2;
        Node arm = grp(wg, 0, 0, 0, rad(8)); cube(arm, own ? Math.max(aw * 1.3, pt + 1.2) : aw, own ? aw * 1.3 : aw, aL - back, own ? s * 0.6 : 0, 0, -back - (aL - back) / 2, bone);
        cube(arm, pt, ph2, aL + 1 - back, s * 0.45, (own ? 0 : aw / 2) - ph2 / 2, -back - (aL + 1 - back) / 2 + 0.5, furL);
        cube(arm, pt + 0.4, ph2 * 0.25, aL + 1 - back, s * 0.6, (own ? 0 : aw / 2) - ph2 * 0.125, -back - (aL + 1 - back) / 2 + 0.5, fur);
        Node elbow = grp(arm, 0, 0, -aL, rad(30)); cube(elbow, own ? Math.max(aw * 1.3, pt + 0.8) : aw, own ? aw * 1.3 : aw, fL, own ? s * 0.45 : 0, 0, -fL / 2, bone); cube(elbow, aw + 0.2, aw * 0.8, fL * 0.35, 0, 0, -fL * 0.85, furD);
        cube(elbow, pt, ph2 * 0.6, fL * 0.8, s * 0.45, (own ? 0 : aw / 2) - ph2 * 0.3, -fL * 0.4, furL);
        float[] seg = D.sickleSeg; double[] bends = {0, 35, 40}; int na = 6, nf = 3;
        List<double[]> roots = new ArrayList<>(); List<Node> rootG = new ArrayList<>();
        for (int i = 0; i < na; i++) { double t = i / (double) (na - 1); rootG.add(arm); roots.add(new double[]{-0.5 - i * (aL - 1) / (na - 1), 0.8 + 0.1 * i, -88 + 30 * t, 0.5 + 0.5 * t}); }
        for (int i = 0; i < nf; i++) { rootG.add(elbow); roots.add(new double[]{-1 - i * (fL * 0.7) / (nf - 1), 1.25 - i * 0.1, -58, 1}); }
        for (int i = 0; i < roots.size(); i++) { double[] r = roots.get(i); Node root = grp(rootG.get(i), s * (0.6 + 0.05 * (i % 3)), -aw / 2, r[0]); root.rot(rad(r[2]), -s * rad(10 + 4 * (i % 3)), 0);
            Node g = root; for (int j = 0; j < seg.length; j++) { Node sg = grp(g, 0, 0, 0, rad(bends[j] * r[3])); double len = seg[j] * r[1]; cube(sg, 1, ph * 1.2 * (1 - j * 0.2), len, 0, 0, -len / 2, j == 2 ? furD : furL); g = grp(sg, 0, 0, -len); } }
    }

    /** open wings for flight: the arm runs out from the shoulder, the finger continues from the elbow and sweeps back, the surface lies flat */
    private void spreadWing(Node body, int s, int fur, int furD, int bone, int mem, int furL) {
        float bw = D.body[0], bh = D.body[1], bd = D.body[2], aw = D.wingArm[0], aL = D.wingArm[1] * 1.3f, fw2 = D.wingFin[0], fL = D.wingFin[1] * 1.35f, chord = bd * 0.55f;
        int col = bone;
        Node wg = grp(body, s * (bw / 2 - 1), bh / 2 - 0.6, bd / 2 - Math.min(5, bd * 0.2)); wg.rot(0, -s * rad(12), 0); wings.add(wg);
        cube(wg, aL, aw, aw, s * aL / 2, 0, 0, col);
        Node elbow = grp(wg, s * aL, 0, 0); elbow.ry = (float) (s * rad(28)); cube(elbow, fL, fw2, fw2, s * fL / 2, 0, 0, col);
        if (carn) {
            cube(wg, aL + 1, 0.6, chord, s * aL / 2, -aw / 2 + 0.1, -chord / 2 + 1.5, mem);
            cube(elbow, fL * 0.6, 0.6, chord * 0.75, s * fL * 0.3, -fw2 / 2 + 0.1, -chord * 0.375 + 1, mem);
            cube(elbow, fL * 0.42, 0.6, chord * 0.42, s * fL * 0.79, -fw2 / 2 + 0.1, -chord * 0.21 + 0.8, mem);
        } else {
            float fw = Math.max(0.6f, aL * 0.16f);
            for (int i = 0; i < 4; i++) row(wg, s * (aL * 0.2 + aL * 0.8 * i / 3), chord * (0.6 + 0.1 * i), i, s, fw, aw, furL, furD);
            for (int i = 0; i < 4; i++) row(elbow, s * (fL * 0.15 + fL * 0.85 * i / 3), chord * (1.0 - 0.14 * i), i + 4, s, fw, aw, furL, furD);
            cube(wg, aL, 0.6, chord * 0.35, s * aL / 2, -aw / 2 + 0.35, -chord * 0.17 + 0.6, fur);
        }
    }

    private void row(Node g, double x, double L, int i, int s, float fw, float aw, int furL, int furD) {
        Node f = grp(g, x, -aw / 2 + 0.2, 0.5); f.ry = (float) (-s * rad(6 + i * 4)); cube(f, fw, 0.5, L, 0, 0, -L / 2, furL); cube(f, fw + 0.1, 0.55, L * 0.3, 0, 0, -L * 0.85, furD);
    }

    /* ---- pose helpers, the page's P.* functions. Angles are radians added to the built pose ---- */

    public void reset() { for (Node n : all) n.reset(); for (Node l : lids) l.visible = false; }

    /** two-beat stride on reverse-jointed legs; w is the stride phase in radians */
    public void gait(double w, double amp, double bob) {
        for (Leg L : legs) { double c = w + (L.s > 0 ? 0 : Math.PI), sw = Math.sin(c), lift = Math.max(0, Math.cos(c)), stance = Math.max(0, -Math.cos(c)), push = Math.max(0, -sw) * stance;
            L.thigh.rx = (float) (L.t0 - sw * amp); L.knee.rx = (float) (L.k0 + lift * amp * 1.6 + stance * amp * 0.22); L.ankle.rx = (float) (L.a0 - lift * amp * 0.5 + push * amp * 0.45); }
        body.y = (float) (bodyY + Math.abs(Math.sin(w)) * bob); body.x = (float) (Math.sin(w) * bob * 0.4); body.rot(Math.sin(2 * w) * 0.025, Math.sin(w) * 0.05, Math.sin(w) * 0.03);
        tail.ry = (float) (-Math.sin(w) * 0.12); for (int i = 0; i < tailSegs.size(); i++) tailSegs.get(i).ry = (float) (-Math.sin(w - 0.6 * (i + 1)) * 0.1);
    }

    public void legPose(double th, double kn, double an) {
        for (Leg L : legs) { L.thigh.rx = (float) (L.t0 + th); L.knee.rx = (float) (L.k0 + kn); L.ankle.rx = (float) (L.a0 + an); }
        body.rot(0, 0, 0); body.x = 0;
    }

    public void legMix(double[] a, double[] b, double u) { legPose(a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u); }

    public void mouth(double open) { jaw.rx = (float) open; }

    /** folded wings swing out from the body; spread wings rock up */
    public void flare(double k) { for (int i = 0; i < wings.size(); i++) { Node w = wings.get(i); w.rz = (float) (w.bz + wingS[i] * k); } }

    /** root height in blocks */
    public void lift(double y) { root.y = (float) (y * 16); }

    /** root forward offset in blocks (the page's root.position.x once it faces +x) */
    public void forward(double x) { root.z = (float) (x * 16); }
}
