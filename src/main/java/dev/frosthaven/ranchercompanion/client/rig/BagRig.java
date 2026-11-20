package dev.frosthaven.ranchercompanion.client.rig;

import dev.frosthaven.ranchercompanion.look.Dims;
import dev.frosthaven.ranchercompanion.look.Gear;

/**
 * The design page's bags, one to one (spec, Accessory: bags): a pair side by side on top of the back, either side of the
 * spine, behind the saddle where there is one, a strap over both. Leather in the main dye, flaps and strap in the trim
 * dye, and on top at each flap's front edge the metal that shows the tier (never dyed): a clasp, and on a saddlebag a
 * strip along the edge. On top because the folded wings flare outward toward the back and cover the flanks.
 */
final class BagRig {
    private BagRig() {}

    /** the page's bagLayout: bag width, height and depth, and its center along the body */
    record Layout(boolean big, float w, float h, float d, float z) {}

    static Layout layout(Dims D, int kind) {
        float bw = D.body[0], bh = D.body[1], bd = D.body[2];
        boolean big = kind > 1;
        float d = Math.round(bd * (big ? 0.3f : 0.26f));
        float z = D.saddle ? D.saddleZ - D.saddleD / 2 - 0.4f - d / 2 : -bd * (big ? 0.2f : 0.15f);
        return new Layout(big, (float) Math.floor((bw - 2.6) / 2), Math.max(2, Math.round(bh * (big ? 0.45f : 0.35f))), d, z);
    }

    static void build(Rig r, int kind, int mainDye, int trimDye) {
        Dims D = r.D; Layout L = layout(D, kind);
        int main = Gear.bagMain(mainDye), trim = Gear.bagTrim(trimDye), metal = Gear.BAG_METAL[kind];
        float w = L.w(), h = L.h(), d = L.d(), z = L.z(), y0 = D.body[1] / 2 + 0.15f, fz = z + d * 0.475f;
        r.cube(r.body, 2 * w + 1.0, 0.5, 1.0, 0, y0 + h + 0.25, z - d * 0.25, trim);              /* the strap over both */
        for (int s : new int[]{-1, 1}) {
            double x = s * (w / 2 + 0.3);
            r.cube(r.body, w, h, d, x, y0 + h / 2, z, main);                                        /* the bag */
            r.cube(r.body, w + 0.3, 0.8, d * 0.55, x, y0 + h + 0.1, z + d * 0.2, trim);             /* its flap over the top front */
            if (L.big()) r.cube(r.body, w - 0.6, 0.4, 0.8, x, y0 + h + 0.7, fz - 0.5, metal);      /* the tier's strip along the flap's edge */
            r.cube(r.body, L.big() ? 1.4 : 1, 0.6, L.big() ? 1.4 : 1.2, x, y0 + h + 0.8, fz - 0.7, metal);  /* the clasp */
        }
    }
}
