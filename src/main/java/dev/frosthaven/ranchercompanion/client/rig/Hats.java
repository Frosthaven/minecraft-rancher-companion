package dev.frosthaven.ranchercompanion.client.rig;

/**
 * The design page's hats, one to one: each builds its cubes in an 8 pixel wide hat space that the page scales to the
 * head (hwS / 8). The rig has no node scale, so every position and every rounded size is scaled here instead.
 * Index order is {@link dev.frosthaven.ranchercompanion.look.Gear#HATS}.
 */
final class Hats {
    private Hats() {}

    private static final int BLACK = 0x1b1b1f, RED = 0xb8352b, GOLD = 0xe2b63a, STRAW = 0xd9c372, WHITE = 0xf2f2ee, IRON = 0x8e9297,
        IRON2 = 0xb9bec3, BLUE = 0x3d4f9e, GREEN = 0x4e8a3c, PINK = 0xe39cc0, PURPLE = 0x6d3f9c, YELLOW = 0xe8d84a,
        BROWN = 0x6b4a2b, BROWN2 = 0x4a3119, CAN = 0xc8ccd0, LAMP = 0xfff2a8, BONE = Rig.BONE,
        /* the aviator's leather and goggles, the satellite's board and tape */
        BRASS = 0xd9ad4e,
        LEATHER = 0x7a4a2a, LEATHER2 = 0x5e3a1a, FLEECE = 0xe8dcc0, STRAP = 0x2b2e35, GLASS = 0x9ad0e0, BOARD = 0x2f7a3a, CHIP = 0x1b1b1f, TAPE = 0xd8d2c0, LED = 0x7dff7a;

    private static double rad(double d) { return Math.toRadians(d); }
    private static float sn(double v) { return v < 0.35 ? 0 : Math.max(1, Math.round((float) v)); }

    /** cubes and groups in hat space, scaled by k into the head */
    private record H(Rig r, double k) {
        Node g(Node p, double x, double y, double z) { return r.grp(p, x * k, y * k, z * k); }
        void c(Node p, double w, double h, double d, double x, double y, double z, int color) { c(p, w, h, d, x, y, z, color, 0); }
        void c(Node p, double w, double h, double d, double x, double y, double z, int color, int kind) {
            p.boxes.add(new Node.Box((float) (sn(w) * k), (float) (sn(h) * k), (float) (sn(d) * k), (float) (x * k), (float) (y * k), (float) (z * k), color, 0, 0, 0, kind, 0));
        }
    }

    /** builds hat {@code hat} on a group at the head's crown; returns the propeller's group for the spin, or null */
    static Node build(Rig r, Node hatRoot, int hat, double k) {
        H q = new H(r, k); Node h = hatRoot;
        switch (hat) {
            case 1 -> { q.c(h, 12, 1, 11, 0, 0.5, 0, BLACK); q.c(h, 8, 8, 7, 0, 4.5, 0, BLACK); q.c(h, 8.6, 1.5, 7.6, 0, 1.6, 0, RED); }
            case 2 -> { q.c(h, 9, 3, 8, 0, 1.5, 0, RED); q.c(h, 6, 0.8, 4, 0, 0.5, 5.5, RED);
                for (int s : new int[]{-1, 1}) { q.c(h, 3, 5, 3, s * 6.5, 2.5, 0, CAN); q.c(h, 3.4, 2, 3.4, s * 6.5, 2.5, 0, RED); q.c(h, 0.6, 2, 0.6, s * 6.5, 6, 0, WHITE);
                    Node st = q.g(h, s * 6.5, 7, 0); st.rot(rad(40), 0, -s * rad(12)); q.c(st, 0.6, 0.6, 9, 0, 0, 4.5, WHITE); } }
            case 3 -> { q.c(h, 9, 4, 8, 0, 2, 0, IRON); q.c(h, 9.6, 1.2, 8.6, 0, 0.6, 0, BROWN); q.c(h, 1.2, 4, 0.6, 0, -1.5, 4.3, IRON);
                for (int s : new int[]{-1, 1}) { Node g = q.g(h, s * 4.8, 3, 0); g.rz = (float) (-s * rad(40)); q.c(g, 1.4, 5, 1.4, 0, 2.5, 0, BONE);
                    Node t = q.g(g, 0, 5, 0); t.rz = (float) (s * rad(50)); q.c(t, 1, 3, 1, 0, 1.5, 0, BONE); } }
            case 4 -> { q.c(h, 13, 0.8, 13, 0, 0.4, 0, BLUE); q.c(h, 7.4, 1.2, 7.4, 0, 1.4, 0, GOLD);
                double[][] tiers = {{7, 2.3}, {5.5, 5}, {4, 8}, {2.5, 11}, {1.2, 13.5}};
                for (int i = 0; i < tiers.length; i++) q.c(h, tiers[i][0], 3, tiers[i][0], 0, tiers[i][1], -i * 0.6, BLUE);
                q.c(h, 1.2, 1.2, 0.5, 0, 6, 2.3, GOLD); }
            case 5 -> { q.c(h, 14, 0.8, 14, 0, 0.4, 0, STRAW); q.c(h, 8, 3, 8, 0, 2, 0, STRAW); q.c(h, 8.6, 1, 8.6, 0, 1.3, 0, RED); }
            case 6 -> { q.c(h, 9, 2, 8, 0, 1, 0, GOLD);
                for (double[] p : new double[][]{{0, 3.6}, {0, -3.6}, {4.2, 0}, {-4.2, 0}, {3.4, 2.8}, {-3.4, 2.8}, {3.4, -2.8}, {-3.4, -2.8}}) q.c(h, 1.2, 2, 1.2, p[0], 2.9, p[1], GOLD);
                q.c(h, 1.2, 1.2, 0.5, 0, 1, 4.1, RED); }
            case 7 -> { Node g = q.g(h, 0, 0, 0); g.rz = (float) rad(12);
                double[] w = {6, 4.8, 3.6, 2.4, 1.2}; int[] c = {RED, YELLOW, BLUE, RED, YELLOW};
                for (int i = 0; i < w.length; i++) q.c(g, w[i], 2.5, w[i], 0, 1.25 + i * 2.5, 0, c[i]);
                q.c(g, 2, 2, 2, 0, 13.5, 0, WHITE); }
            case 8 -> { q.c(h, 8, 7, 8, 0, 3.5, 0, IRON); q.c(h, 8.8, 1, 8.8, 0, 0.5, 0, IRON2);
                for (int s : new int[]{-1, 1}) q.c(h, 0.6, 3, 0.6, s * 4.3, 8.2, 0, IRON2); q.c(h, 9.2, 0.6, 0.6, 0, 9.6, 0, IRON2); }
            case 9 -> { q.c(h, 13, 0.8, 11, 0, 0.4, 0, BROWN);
                for (int s : new int[]{-1, 1}) { Node g = q.g(h, s * 6.2, 0.4, 0); g.rz = (float) (s * rad(28)); q.c(g, 2.4, 0.8, 10, s * 1.2, 0, 0, BROWN); }
                q.c(h, 7, 5, 7, 0, 3, 0, BROWN); q.c(h, 3, 1, 5.4, 0, 5.6, 0, BROWN2); q.c(h, 7.6, 1, 7.6, 0, 1.3, 0, BLACK); }
            case 10 -> { q.c(h, 7, 3, 7, 0, 1.5, 0, WHITE); q.c(h, 9, 5, 9, 0, 5.5, 0, WHITE); q.c(h, 8, 2, 8, 0, 8.6, 0.6, WHITE); }
            case 11 -> { q.c(h, 9, 4, 8, 0, 2, 0, YELLOW); q.c(h, 10, 0.8, 5, 0, 0.4, 3, YELLOW); q.c(h, 2, 2, 1.5, 0, 2.5, 4.5, IRON); q.c(h, 1.4, 1.4, 0.5, 0, 2.5, 5.4, LAMP, 3); }
            case 12 -> { q.c(h, 8, 3, 7, 0, 1.5, 0, BLACK); Node f = q.g(h, 0, 2.2, 3.8); f.rx = (float) -rad(35); q.c(f, 10, 0.8, 3.5, 0, 0, 0, BLACK); q.c(f, 1.6, 1.6, 0.5, 0, 0.9, 0, BONE);
                for (int s : new int[]{-1, 1}) { Node g = q.g(h, s * 4.2, 2.2, 0); g.rz = (float) (s * rad(35)); q.c(g, 3.5, 0.8, 9, 0, 0, 0, BLACK); } }
            case 13 -> { for (int s : new int[]{-1, 1}) q.c(h, 4.5, 3.5, 8, s * 2.25, 1.75, 0, s > 0 ? RED : BLUE); q.c(h, 6, 0.8, 3.5, 0, 0.5, 5.5, RED);
                q.c(h, 0.6, 2, 0.6, 0, 4.5, 0, IRON); Node pr = q.g(h, 0, 5.8, 0); q.c(pr, 8, 0.5, 1.4, 0, 0, 0, YELLOW); q.c(pr, 1.4, 0.5, 8, 0, 0, 0, YELLOW); return pr; }
            case 14 -> { q.c(h, 9, 1, 8, 0, 0.5, 0, GREEN);
                double[][] fl = {{0, 4.2, PINK}, {0, -4.2, WHITE}, {4.5, 0, YELLOW}, {-4.5, 0, PINK}, {3.4, 3, WHITE}, {-3.4, 3, YELLOW}, {3.4, -3, PINK}, {-3.4, -3, WHITE}};
                for (double[] p : fl) q.c(h, 1.6, 1.6, 1.6, p[0], 1.3, p[1], (int) p[2]); }
            case 15 -> { q.c(h, 8, 2.5, 7, 0, 1.25, 0, PURPLE);
                double[][] tails = {{0, rad(45), RED}, {0, -rad(45), YELLOW}, {-rad(55), 0, PURPLE}};
                for (double[] p : tails) { Node g = q.g(h, 0, 2, 0); g.rot(p[0], 0, p[1]); q.c(g, 2, 6, 2, 0, 3, 0, (int) p[2]); q.c(g, 1.4, 1.4, 1.4, 0, 6.5, 0, BONE); } }
            case 16, 17 -> { /* conductor's cap, stock keeper's cap: Create's own models (compat/create CreateHats), no cubes */ }
            case 18 -> { q.c(h, 9, 4, 9, 0, 2, 0, LEATHER); q.c(h, 1, 1, 8, 0, 4.5, 0, LEATHER2);  /* aviator cap: leather, ear flaps, a short visor, goggles on the brow */
                for (int s : new int[]{-1, 1}) { q.c(h, 1, 5, 4, s * 4.9, -0.5, -2.8, LEATHER); q.c(h, 1, 1, 4, s * 4.9, -3.5, -2.8, FLEECE);  /* behind the eyes */ }
                Node f = q.g(h, 0, 0.5, 4.6); f.rx = (float) rad(15); q.c(f, 8, 1, 2, 0, 0, 1, LEATHER2);
                q.c(h, 10, 1, 10, 0, 2.6, 0, STRAP);
                for (int s : new int[]{-1, 1}) { q.c(h, 3, 3, 1, s * 2, 2.6, 5, BRASS); q.c(h, 2, 2, 1, s * 2, 2.6, 5.3, GLASS); } }
            case 19 -> { q.c(h, 9, 3, 9, 0, 1.5, 0, IRON); q.c(h, 7, 2, 7, 0, 4, 0, IRON); q.c(h, 4, 1, 4, 0, 5.5, 0, IRON2);  /* satellite hat: a metal dome */
                q.c(h, 1, 9, 1, 3.5, 7.5, -1, IRON2);  /* the antenna on one side, a dish and its feed on top */
                Node d = q.g(h, 3.5, 12, -1); d.rz = (float) -rad(30); q.c(d, 4, 1, 4, 0, 0, 0, WHITE); q.c(d, 1, 2, 1, 0, 1.2, 0, RED);
                q.c(h, 1, 3, 4, -5, 2, 0.5, BOARD);  /* a circuit board taped to the other side, its chip, a lit LED and a wire */
                q.c(h, 1, 1, 1, -5.6, 2.6, 1.2, CHIP); q.c(h, 1, 1, 1, -5.6, 1.2, -0.6, LED, 3);
                q.c(h, 2, 1, 5, -5, 3.6, 0.5, TAPE); q.c(h, 2, 1, 5, -5, 0.4, 0.5, TAPE); q.c(h, 1, 1, 3, -4.6, 3.8, -2.5, RED); }
            default -> {}
        }
        return null;
    }
}
