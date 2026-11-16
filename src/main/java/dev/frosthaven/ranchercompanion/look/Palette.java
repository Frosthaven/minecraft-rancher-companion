package dev.frosthaven.ranchercompanion.look;

/** Color math shared with the design page: packed 0xRRGGBB ints. */
public final class Palette {
    private Palette() {}

    public static int hex(String h) {
        return Integer.parseInt(h.startsWith("#") ? h.substring(1) : h, 16);
    }

    public static int r(int c) { return (c >> 16) & 255; }
    public static int g(int c) { return (c >> 8) & 255; }
    public static int b(int c) { return c & 255; }

    public static int rgb(double r, double g, double b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    /** Toward white for a positive amount, toward black for a negative one. */
    public static int shade(int c, double amt) {
        int t = amt > 0 ? 255 : 0;
        double a = Math.abs(amt);
        return rgb(r(c) + (t - r(c)) * a, g(c) + (t - g(c)) * a, b(c) + (t - b(c)) * a);
    }

    /** The trim a fur gets when its line names none: a lighter shade, eased off on dark furs so a near-black body keeps a dark trim. */
    public static int trim(int c) {
        double l = (Math.max(r(c), Math.max(g(c), b(c))) + Math.min(r(c), Math.min(g(c), b(c)))) / 510.0;
        return shade(c, 0.26 * Math.min(1, l / 0.35));
    }

    /** a at k = 0, b at k = 1. */
    public static int mix(int a, int b, double k) {
        return rgb(r(a) + (r(b) - r(a)) * k, g(a) + (g(b) - g(a)) * k, b(a) + (b(b) - b(a)) * k);
    }

    /** A biome tint blended toward the fur so the feet sit with the body. */
    public static int legTint(int tint, int fur) {
        return mix(tint, fur, 0.3);
    }

    /** Additive sheen: the design page lights the fur with an emissive color scaled by the tier's sheen. */
    public static int sheen(int c, int sheenHex, double amt) {
        return rgb(r(c) + r(sheenHex) * amt, g(c) + g(sheenHex) * amt, b(c) + b(sheenHex) * amt);
    }
}
