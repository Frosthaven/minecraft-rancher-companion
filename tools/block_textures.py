"""Block textures for the hatchery, the trough and the decor blocks, painted the way the design page paints them.

Every painter here is a port of the page's (Blocks tab): the barding-style material painter (`bardTex`), the decor block
painters (`dmat`) and the trough's diet emblems, with the page's seeded random (`mulberry`), so a texture in the game is the
texture on the page. Leather is painted grey and tinted in the game (its dye), with untinted overlays for what is steel.

Run: uv run --with pillow python -I tools/block_textures.py
Writes PNGs to src/main/resources/assets/rancher_companion/textures/block/.
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/rancher_companion/textures/block"

# the style palette (design page, Blocks tab, Style): shadow to highlight
STEEL = ["#2b2e35", "#41454e", "#5d636e", "#8d939e"]
WOOD = ["#2a1e16", "#3b2a1f", "#523b2a"]
STRAW = ["#7e6430", "#b0903f", "#d4b665"]
FEATHER = ["#3a3344", "#6a5d7a", "#d9cfbd"]
# leather is painted in grey and tinted by the dye: these greys keep the page's crease / base / puff steps (see LEATHER_BASE)
LEATHER_GREY = ["#8c8c8c", "#d0d0d0", "#ffffff"]


def mulberry(seed):
    """The page's mulberry32: a function returning floats in [0, 1)."""
    state = [seed & 0xFFFFFFFF]

    def imul(a, b):
        return ((a & 0xFFFFFFFF) * (b & 0xFFFFFFFF)) & 0xFFFFFFFF

    def nxt():
        s = (state[0] + 0x6D2B79F5) & 0xFFFFFFFF
        state[0] = s
        t = imul(s ^ (s >> 15), 1 | s)
        t = ((t + imul(t ^ (t >> 7), 61 | t)) & 0xFFFFFFFF) ^ t
        return ((t ^ (t >> 14)) & 0xFFFFFFFF) / 4294967296

    return nxt


def rgb(h):
    n = int(h[1:], 16)
    return (n >> 16) & 255, (n >> 8) & 255, n & 255


def jit(h, j):
    r, g, b = rgb(h)
    return tuple(max(0, min(255, round(c + j))) for c in (r, g, b)) + (255,)


def bard(hex_, kind):
    """The page's bardTex: a 16 px material surface in one color."""
    rnd = mulberry(int(hex_[1:], 16) ^ (len(kind) * 7919))
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            n = rnd() - 0.5
            if kind == "wood":
                j = (-16 if y % 4 == 0 else 0) + (6 if y % 4 == 2 else 0) + n * 8 + (-10 if (x * 3 + y * 5) % 11 == 0 else 0)
            elif kind == "leather":
                j = n * 10 + (-10 if rnd() < 0.06 else 0)
            elif kind == "straw":
                j = n * 10 + (18 if (x * 7 + (y >> 2) * 3) % 5 == 0 else 0) + (-10 if x % 3 == 0 else 0)
            elif kind == "feather":
                j = (-12 if (x + y) % 3 == 0 else 4) + n * 6 + (18 if x in (7, 8) else 0)
            elif kind == "plain":
                j = n * 20 + (-14 if rnd() < 0.1 else 0)
            else:  # iron: brushed metal with a highlight band and glints
                j = n * 6 + (18 if y in (2, 3) else 0) + (-12 if y == 13 else 0) + (24 if rnd() < 0.05 else 0)
            im.putpixel((x, y), jit(hex_, j))
    return im


def decor(kind, leather=LEATHER_GREY):
    """The page's dmat painters for the decor blocks."""
    rnd = mulberry(7919 * len(kind) + ord(kind[0]) * 31)
    W, S, L, T = WOOD, STEEL, leather, STRAW

    def planks(x, y):
        b, r = y >> 2, y & 3
        jx = [11, 4, 13, 7][b]
        if r == 1 and x in (jx - 2, jx + 2):
            return S[3], 0
        return (W[0] if r == 3 or x == jx else W[2] if r == 0 else W[0] if rnd() < 0.12 else W[1]), 10

    def beamSide(x, y):
        if y in (1, 14):
            return (S[3], 0) if x in (3, 8, 12) else (S[2] if y == 1 else S[1], 6)
        if y in (0, 2, 13, 15):
            return S[0], 6
        return (W[0] if x % 5 == 0 or rnd() < 0.08 else W[2] if x % 5 == 2 else W[1]), 10

    def beamEnd(x, y):
        if x in (1, 14) and y in (1, 14):
            return S[3], 0
        d = max(abs(x - 7.5), abs(y - 7.5))
        if d > 6.5:
            return S[1], 6
        ring = int(d) % 3
        return [W[0], W[1], W[2]][ring], 8

    def beamSlabSide(x, y):
        yy = y & 7
        if x in (1, 14):
            return (S[3], 0) if yy in (2, 5) else (S[2] if x == 1 else S[1], 6)
        if x in (0, 2, 13, 15):
            return S[0], 6
        return (W[0] if y % 5 == 0 or rnd() < 0.08 else W[2] if y % 5 == 2 else W[1]), 10

    def beamSlabEnd(x, y):
        yy = y & 7
        if x in (1, 14) and yy in (1, 6):
            return S[3], 0
        if x in (0, 15) or yy in (0, 7):
            return S[1], 6
        ring = int(max(abs(x - 7.5) - 4, abs(yy - 3.5))) % 3
        return [W[0], W[1], W[2]][ring], 8

    def tufted(x, y):
        u, v = (x - y) & 7, (x + y + 2) & 7
        if not u and not v:
            return L[1], 0  # under the stud (the stud is an overlay)
        if not u or not v:
            return L[0], 6
        return (L[2] if 3 <= u <= 5 and 3 <= v <= 5 else L[1]), 8

    def shingles(x, y):
        row, sy = y >> 2, y & 3
        u = x + (row & 1) * 2
        sx, t = u & 3, (((u >> 2) & 3) * 7 + row * 13) % 5
        if sy == 3 or sx == 0:
            return S[0], 6
        if sy == 0 and sx == 1:
            return S[2], 4
        if sy == 0 and sx == 2 and t == 2:
            return S[3], 0
        return ("#4b3f57" if t == 0 else "#4f545e" if t == 1 else S[1]), 8

    def bedding(x, y):
        return (T[2] if (x + y * 3) % 7 == 0 or rnd() < 0.25 else T[0] if rnd() < 0.12 else T[1]), 12

    paint = dict(planks=planks, beamSide=beamSide, beamEnd=beamEnd, beamSlabSide=beamSlabSide, beamSlabEnd=beamSlabEnd,
                 tufted=tufted, shingles=shingles, bedding=bedding)[kind]
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            h, a = paint(x, y)
            im.putpixel((x, y), jit(h, (rnd() - 0.5) * a))
    return im


def studs():
    """The tufted leather's steel studs, an untinted overlay where the creases cross."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if not ((x - y) & 7) and not ((x + y + 2) & 7):
                im.putpixel((x, y), rgb(STEEL[3]) + (255,))
    return im


# the trough's diet emblems, 5x5 (design page, Blocks tab, Functional): read left to right from outside
EMBLEMS = {
    "raw": ([".FFF.", "FPPPF", "FPDPF", ".FPPF", "..FF."], {"F": "#f2d6c8", "P": "#e08a84", "D": "#b85a5a"}),
    "cooked": ([".BB..", "BbBB.", "BBBB.", "..BW.", "...WW"], {"B": "#8a5228", "b": "#b5743a", "W": "#e3dcc6"}),
    "seed": ([".S...", "...S.", "S.s..", "..S.S", ".s..."], {"S": "#7ac84a", "s": "#4f8a34"}),
    "root": (["...GG", "..OG.", ".OO..", "Oo...", "O...."], {"O": "#e07a2a", "o": "#b85a1a", "G": "#5e9a3a"}),
}


def plaque(diet):
    """A 6 x 6 dark steel plaque at texture pixels 5..10, the diet's emblem in its upper-left 5 x 5."""
    im = bard(STEEL[0], "iron")
    if diet:
        rows, pal = EMBLEMS[diet]
        for r, row in enumerate(rows):
            for c, ch in enumerate(row):
                if ch in pal:
                    im.putpixel((5 + c, 5 + r), rgb(pal[ch]) + (255,))
    return im


def footprint():
    """Drekki Footprints: clear but for two pressed paw prints, the right foot a stride ahead of the left. The texture's top
    is the way the prints walk (the block's facing). A print: a three-lobed heel pad, three toes and a claw tip on each."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # deep, pressed, rim; at half their first opacity (Shane, 2026-11-27: 50% translucent; the model is translucent)
    EARTH = [(42, 30, 22, 108), (66, 48, 34, 95), (92, 70, 50, 60)]
    PRINT = [  # 7 wide, 8 tall: c claw, t toe, r pad rim, p heel pad
        "c..c..c",
        "t..t..t",
        "tt.t.tt",
        ".t.t.t.",
        "..rrr..",
        ".rpppr.",
        ".ppppp.",
        "..ppp..",
    ]
    def put(ox, oy):
        for r, row in enumerate(PRINT):
            for c, ch in enumerate(row):
                if ch == ".":
                    continue
                col = {"c": EARTH[0], "t": EARTH[1], "p": EARTH[0], "r": EARTH[2]}[ch]
                im.putpixel((ox + c, oy + r), col)
    put(1, 7)   # left foot, behind
    put(8, 0)   # right foot, a stride ahead
    return im


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    out = {
        # decor
        "rancher_planks": decor("planks"),
        "rancher_beam_side": decor("beamSide"),
        "rancher_beam_end": decor("beamEnd"),
        "rancher_beam_slab_side": decor("beamSlabSide"),
        "rancher_beam_slab_end": decor("beamSlabEnd"),
        "tufted_leather": decor("tufted"),
        "tufted_leather_studs": studs(),
        "rancher_shingles": decor("shingles"),
        "straw_bedding": decor("bedding"),
        "drekki_footprints": footprint(),
        # the style's materials, for the hatchery and the trough
        "style_wood": bard(WOOD[1], "wood"),
        "style_steel": bard(STEEL[1], "iron"),
        "style_steel_dark": bard(STEEL[0], "iron"),
        "style_rivet": bard(STEEL[3], "plain"),
        "style_leather": bard(LEATHER_GREY[1], "leather"),
        "style_leather_light": bard(LEATHER_GREY[2], "leather"),
        "style_straw": bard(STRAW[1], "straw"),
        "style_straw_light": bard(STRAW[2], "straw"),
        "style_greens": bard("#4f8a34", "plain"),
        "style_greens_light": bard("#6aa83e", "plain"),
        "style_feather_dark": bard(FEATHER[0], "feather"),
        "style_feather": bard(FEATHER[1], "feather"),
        "style_feather_light": bard(FEATHER[2], "feather"),
        "trough_plaque": plaque(None),
    }
    for d in EMBLEMS:
        out["trough_plaque_" + d] = plaque(d)
    for name, im in out.items():
        im.save(OUT / f"{name}.png")
    print(f"{len(out)} textures in {OUT}")


if __name__ == "__main__":
    main()
