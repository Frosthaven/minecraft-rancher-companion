"""The Drekki Lotus's crop stages (spec, Tombstone: the way back). Stages 0 to 2 are 16x16 crop (cross) sprites; the
grown stage is a small 3D lotus (Shane, 2026-11-26: the flat bloom looked wrong in the world; no lily pads): thin stems
with pointed leaves on two crossed planes, one stem and a cup of pointed petals, a ring of four tilted out 45 degrees, a ring of four at 22.5, two crossed planes between them,
and a pale seed pod.

Run: uv run --with pillow python -I tools/lotus_textures.py
Writes textures/block/drekki_lotus_stage{0..2}.png, drekki_lotus_{petal,petals,stems,stem,pod}.png and
models/block/drekki_lotus_stage3.json.
"""
import json
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/rancher_companion"
OUT = ASSETS / "textures/block"
PAL = {"s": "3f6b62", "S": "2a4a44", "l": "4f8a6e", "L": "2f6b5a", "b": "5a1fb0", "B": "9b4ff0", "t": "d9b8ff", "g": "f2c230", "o": "2a1240"}
B = "................"


def pad(rows):
    return [B] * (16 - len(rows)) + rows


STAGES = [
    pad(["......s..s......", ".....sS..Ss.....", "......S..S......"]),
    pad(["....ll......ll..", "...lLLl....lLLl.", "....lLs..sLLl...", "......S..S......", ".....sS..Ss.....",
         "......S..S......", "......S..S......"]),
    pad([".......ob.......", "......obBo......", "......bBBb......", "......obbo......", ".......Ss.......",
         "..ll...S.....ll.", ".lLLl..S....lLLl", "..lLLs.S..sLLl..", ".....sS..Ss.....", "......S..S......",
         "......S..S......"]),
]


# the grown lotus's parts: one pointed petal (tip up), two side by side, a round pad with a notch, the stem, the pod
PETAL_PAL = {"o": "4a1690", "b": "6a2cc4", "B": "9b5ff0", "t": "c9a6ff", "T": "efe2ff"}
PETAL = ["................", ".......TT.......", "......tTTt......", "......tTTt......", ".....BtTTtB.....",
         ".....BtttTB.....", "....BBttttBB....", "....BBBttBBB....", "...oBBBttBBBo...", "...oBBBBBBBBo...",
         "...obBBBBBBbo...", "...obbBBBBbbo...", "....obbBBbbo....", "....obbbbbbo....", ".....obbbbo.....",
         "......oooo......"]
PETALS = ["................", "...TT......TT...", "..tTTt....tTTt..", "..tTTt....tTTt..", ".BtTTtB..BtTTtB.",
          ".BtttTB..BtttTB.", "BBttttBBBBttttBB", "BBBttBBBBBBttBBB", "oBBttBBooBBttBBo", "oBBBBBBooBBBBBBo",
          "obBBBBboobBBBBbo", "obbBBbboobbBBbbo", ".obbbbo..obbbbo.", ".obbbbo..obbbbo.", "..obbo....obbo..",
          "...oo......oo..."]
# the stems below the bloom (Shane, 2026-11-26: no lily pads): thin stems fanning up from one root, pointed leaves on top
STEMS_PAL = {"s": "2f6b5a", "S": "3f6b62", "l": "4f8a6e", "L": "2a4a44"}
STEMS = ["................", "................", "................", "...L........L...", "..LlL......LlL..",
         "..LlL..L...LlL..", "..LlL.LlL..LlL..", "...s..LlL...s...", "...s..LlL...s...", "...s...s....s...",
         "...s...s...s....", "....s..s..s.....", "....s..s..s.....", ".....s.S.s......", "......sSs.......",
         ".......S........"]
STEM = ["S" * 16 if y % 4 else "s" * 16 for y in range(16)]
POD_PAL = {"p": "c9d98a", "P": "a8bc66", "k": "6e7d3a"}
POD = ["".join("k" if (x % 5 == 2 and y % 5 == 2) else ("P" if (x + y) % 3 == 0 else "p") for x in range(16)) for y in range(16)]


def paint(rows, pal, name):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                h = pal[ch]
                im.putpixel((x, y), tuple(int(h[k:k + 2], 16) for k in (0, 2, 4)) + (255,))
    im.save(OUT / f"{name}.png")


def face(tex, *dirs):
    return {d: {"uv": [0, 0, 16, 16], "texture": f"#{tex}"} for d in dirs}


def plane(frm, to, tex, rot=None):
    """a flat part, both sides drawn (zero thick, as vanilla's cross)"""
    dirs = ("north", "south") if frm[2] == to[2] else ("east", "west") if frm[0] == to[0] else ("up", "down")
    e = {"from": frm, "to": to, "faces": face(tex, *dirs)}
    if rot:
        e["rotation"] = rot
    return e


def petal_ring(c, half, y0, h, w, angle, tex):
    """four petals around a square of half size `half`, each tilted out by `angle` at its foot"""
    a, b = c - w / 2, c + w / 2
    lo, hi = c - half, c + half
    return [
        plane([a, y0, lo], [b, y0 + h, lo], tex, {"axis": "x", "angle": -angle, "origin": [c, y0, lo]}),   # north, tips to -z
        plane([a, y0, hi], [b, y0 + h, hi], tex, {"axis": "x", "angle": angle, "origin": [c, y0, hi]}),     # south, to +z
        plane([lo, y0, a], [lo, y0 + h, b], tex, {"axis": "z", "angle": angle, "origin": [lo, y0, c]}),     # west, to -x
        plane([hi, y0, a], [hi, y0 + h, b], tex, {"axis": "z", "angle": -angle, "origin": [hi, y0, c]}),    # east, to +x
    ]


def grown():
    els = [
        plane([3, 0, 8], [13, 6.5, 8], "stems", {"axis": "y", "angle": 45, "origin": [8, 8, 8]}),
        plane([3, 0, 8], [13, 6.5, 8], "stems", {"axis": "y", "angle": -45, "origin": [8, 8, 8]}),
        {"from": [7.5, 0, 7.5], "to": [8.5, 6, 8.5], "faces": face("stem", "north", "south", "east", "west")},
        *petal_ring(8, 2.5, 6.25, 5, 4, 45, "petal"),
        plane([4.5, 6.5, 8], [11.5, 11, 8], "petals", {"axis": "y", "angle": 45, "origin": [8, 8, 8]}),
        plane([4.5, 6.5, 8], [11.5, 11, 8], "petals", {"axis": "y", "angle": -45, "origin": [8, 8, 8]}),
        *petal_ring(8, 1.5, 6.5, 4.5, 3, 22.5, "petal"),
        {"from": [6.75, 6.5, 6.75], "to": [9.25, 8, 9.25], "faces": face("pod", "north", "south", "east", "west", "up")},
    ]
    t = {k: f"rancher_companion:block/drekki_lotus_{k}" for k in ("petal", "petals", "stems", "stem", "pod")}
    model = {"ambientocclusion": False, "textures": {"particle": t["petal"], **t}, "elements": els,
             "render_type": "minecraft:cutout"}
    (ASSETS / "models/block/drekki_lotus_stage3.json").write_text(json.dumps(model, indent=2) + "\n")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for i, rows in enumerate(STAGES):
        paint(rows, PAL, f"drekki_lotus_stage{i}")
    paint(PETAL, PETAL_PAL, "drekki_lotus_petal")
    paint(PETALS, PETAL_PAL, "drekki_lotus_petals")
    paint(STEMS, STEMS_PAL, "drekki_lotus_stems")
    (OUT / "drekki_lotus_pad.png").unlink(missing_ok=True)
    paint(STEM, {"s": "2a4a44", "S": "3f6b62"}, "drekki_lotus_stem")
    paint(POD, POD_PAL, "drekki_lotus_pod")
    (OUT / "drekki_lotus_stage3.png").unlink(missing_ok=True)
    grown()


if __name__ == "__main__":
    main()
