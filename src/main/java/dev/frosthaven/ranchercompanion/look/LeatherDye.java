package dev.frosthaven.ranchercompanion.look;

/**
 * Leather colors for the blocks (the hatchery's nest, the trough's rolls, Tufted Leather). Index 0 is undyed, the style's
 * purple; 1 to 16 are the dyes in DyeColor order. The leather textures are painted in grey (crease, base, puff at 0x8c,
 * 0xd0, 0xff, tools/block_textures.py), so the tint is the wanted base shade over the base grey: a dye darkens into the
 * same moody range as the purple, as on the design page (Blocks tab).
 */
public final class LeatherDye {
    private LeatherDye() {}

    public static final int COUNT = 17;
    /** DyeColor's ids, in order, for block names */
    public static final String[] NAMES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    /** the dyes' leather-armor colors (the design page's DYES), in DyeColor order */
    private static final int[] DYE = {0xf9fffe, 0xf9801d, 0xc74ebd, 0x3ab3da, 0xfed83d, 0x80c71f, 0xf38baa, 0x474f52,
        0x9d9d97, 0x169c9c, 0x8932b8, 0x3c44aa, 0x835432, 0x5e7c16, 0xb02e26, 0x1d1d21};
    private static final int UNDYED_BASE = 0x4a2a52;
    private static final double GREY_BASE = 0xd0 / 255.0;

    /** the base leather shade for an index: the style purple, or the dye darkened 38% (the page's leatherShades) */
    public static int base(int index) {
        return index <= 0 ? UNDYED_BASE : shade(DYE[Math.min(index, DYE.length) - 1], -0.38);
    }

    /** the tint for the grey leather textures, so their base grey lands on {@link #base} */
    public static int tint(int index) {
        int b = base(index);
        return lift(b >> 16) << 16 | lift(b >> 8) << 8 | lift(b);
    }

    private static int lift(int c) {
        return Math.min(255, (int) Math.round((c & 255) / GREY_BASE));
    }

    /** toward white (amt > 0) or black (amt < 0) by |amt|, as the page's shade() */
    static int shade(int rgb, double amt) {
        int t = amt > 0 ? 255 : 0;
        double a = Math.abs(amt);
        return mix((rgb >> 16) & 255, t, a) << 16 | mix((rgb >> 8) & 255, t, a) << 8 | mix(rgb & 255, t, a);
    }

    private static int mix(int c, int t, double a) {
        return (int) Math.round(c + (t - c) * a);
    }
}
