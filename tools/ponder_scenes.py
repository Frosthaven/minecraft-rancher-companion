"""Schematics for the Ponder scenes (compat/ponder/DrekkiPonder): a stable around a hatchery, and three trough scenes
(a line of 10 with an 11th apart, the four diets, feeding). Layer y 0 is the base plate. The scenes reveal parts of each
schematic step by step, so the positions here and in DrekkiPonder must agree.

Run: uv run --with nbtlib python -I tools/ponder_scenes.py
Writes assets/rancher_companion/ponder/{hatchery/stable,trough/line,trough/diets,trough/feeding,tombstone/rebirth,whistle/commands,footprints/trail}.nbt, and the scenes'
texts into en_us.json: Ponder shows only translated text, keyed <mod>.ponder.<scene>.header and .text_<n> in the order
the scene registers them, so they are read from DrekkiPonder.java's scene.title(...) and .text("...") calls in order.
"""
import json
import re
from pathlib import Path

import nbtlib
from nbtlib.tag import Byte, Compound, Int, List, String

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/rancher_companion/ponder"
NS = "rancher_companion"
DATA_VERSION = 3955  # 1.21.1


class Schematic:
    def __init__(self, w, h, d, floor="minecraft:grass_block"):
        self.size = (w, h, d)
        self.blocks = {}
        for x in range(w):
            for z in range(d):
                self.set(x, 0, z, floor)

    def set(self, x, y, z, name, **props):
        self.blocks[(x, y, z)] = (name, tuple(sorted((k, str(v).lower()) for k, v in props.items())))

    def save(self, path):
        palette, index, blocks = [], {}, []
        for pos, state in sorted(self.blocks.items()):
            if state not in index:
                index[state] = len(palette)
                entry = {"Name": String(state[0])}
                if state[1]:
                    entry["Properties"] = Compound({k: String(v) for k, v in state[1]})
                palette.append(Compound(entry))
            blocks.append(Compound({"pos": List[Int]([Int(c) for c in pos]), "state": Int(index[state])}))
        root = Compound({"DataVersion": Int(DATA_VERSION), "size": List[Int]([Int(c) for c in self.size]),
                         "palette": List[Compound](palette), "blocks": List[Compound](blocks), "entities": List[Compound]([])})
        out = OUT / f"{path}.nbt"
        out.parent.mkdir(parents=True, exist_ok=True)
        nbtlib.File(root, gzipped=True).save(out)


def trough(s, x, y, z, facing="north", left=False, right=False, diet=0):
    s.set(x, y, z, f"{NS}:trough", facing=facing, left=left, right=right, diet=diet, color=0)


# ---- hatchery/stable: 11 x 8 x 11, the design page's decor booth (Blocks tab) turned to open toward the viewer ----
# A 5x5 booth over x 3..7, z 3..7: plank floor, a ceiling of slabs 3 above the floor (beam slab rows at z 3, 5, 7,
# shingle slabs between), back (z 7) and side (x 7) walls of a plank course under two of tufted leather, a fence front
# on x 3, fence corner posts 3 high, the hatchery in the back corner (6, 1, 6), a trough in the other back corner
# (4, 1, 6), bedding on the rest of the inside, a lantern under the ceiling. The front (z 3) stays open toward the
# viewer, so the bedding and the path in front of it are in plain view.
HATCH = (6, 1, 6)


def fence(s, x, y, z, **sides):
    s.set(x, y, z, f"{NS}:rancher_fence", **{d: sides.get(d, False) for d in ("north", "east", "south", "west")}, waterlogged=False)


def stable():
    s = Schematic(11, 8, 11)
    for x in range(3, 8):
        for z in range(3, 8):
            s.set(x, 0, z, f"{NS}:rancher_planks")
            if z % 2:
                s.set(x, 4, z, f"{NS}:rancher_beam_slab", axis="x", type="bottom", waterlogged=False)
            else:
                s.set(x, 4, z, f"{NS}:rancher_shingles_slab", type="bottom", waterlogged=False)
    for n in range(4, 7):
        for x, z in ((n, 7), (7, n)):                       # back wall and side wall
            s.set(x, 1, z, f"{NS}:rancher_planks")
            s.set(x, 2, z, f"{NS}:tufted_leather")
            s.set(x, 3, z, f"{NS}:tufted_leather")
        fence(s, 3, 1, n, north=True, south=True)          # the fence front on the west side
    for y in range(1, 4):                                    # corner posts
        fence(s, 3, y, 3, south=y == 1)
        fence(s, 7, y, 3, south=True)
        fence(s, 3, y, 7, east=True, north=y == 1)
        fence(s, 7, y, 7, west=True, north=True)
    s.set(*HATCH, f"{NS}:hatchery", facing="north", color=0)
    trough(s, 4, 1, 6, facing="north")
    for x in range(4, 7):
        for z in range(4, 7):
            if (x, z) not in ((6, 6), (4, 6)):
                s.set(x, 1, z, f"{NS}:straw_bedding")
    s.set(5, 3, 4, "minecraft:lantern", hanging=True, waterlogged=False)
    s.save("hatchery/stable")


# ---- trough/line: 13 x 3 x 6, ten troughs at z 3 from x 1 (facing north: left is east), an 11th at x 11 ----
def line():
    s = Schematic(13, 3, 6)
    for x in range(1, 11):
        trough(s, x, 1, 3, left=x < 10, right=x > 1)
    trough(s, 11, 1, 3)
    s.save("trough/line")


# ---- trough/diets: 9 x 3 x 5, four single troughs at x 1, 3, 5, 7 holding Raw, Cooked, Seed, Root ----
def diets():
    s = Schematic(9, 3, 5)
    for i, x in enumerate((1, 3, 5, 7)):
        trough(s, x, 1, 2, diet=i + 1)
    s.save("trough/diets")


# ---- trough/feeding: 9 x 3 x 7, a line of 5 at z 3 (x 2..6), a stone block walling off x 5's open side ----
def feeding():
    s = Schematic(9, 3, 7)
    for x in range(2, 7):
        trough(s, x, 1, 3, left=x < 6, right=x > 2, diet=3)
    s.set(5, 1, 2, "minecraft:stone")
    s.save("trough/feeding")


def rebirth():
    """the tombstone and its lotus: a grass plate (Shane, 2026-11-26), the tombstone at (3, 1, 3) facing west (toward Ponder's camera; Shane,
    2026-11-26) on soul soil, soul soil under the lotus in front of it at (2, 1, 3) (a lotus grows only in front of a
    tombstone or behind it)"""
    s = Schematic(7, 2, 7, floor="minecraft:grass_block")
    s.set(2, 0, 3, "minecraft:soul_soil")
    s.set(3, 0, 3, "minecraft:soul_soil")  # the tombstone on soul soil: its rebirth works only there
    s.set(3, 1, 3, f"{NS}:tombstone", facing="west")
    s.set(2, 1, 3, f"{NS}:drekki_lotus", age=0)
    s.save("tombstone/rebirth")


def whistle():
    """the whistle's commands and Create jobs: a grass plate, a plain Create seat at (6, 1, 5), and a seat at (6, 1, 1)
    beside a Stock Ticker at (7, 1, 1) (a job seat: the stock keeper's)"""
    s = Schematic(9, 2, 7, floor="minecraft:grass_block")
    s.set(6, 1, 5, "create:white_seat")
    s.set(6, 1, 1, "create:brown_seat")
    s.set(7, 1, 1, "create:stock_ticker")
    s.save("whistle/commands")


def trail():
    """footprint trails: a grass plate 13 x 9, the hatchery at (1, 1, 4); prints from (3, 1, 4) east to a fork at
    (7, 1, 4): one branch east along z 4 to its end at (11, 1, 4), one south along x 8 to (8, 1, 7) with Straw Bedding
    beside it at (10, 1, 7), a rest stop"""
    s = Schematic(13, 2, 9, floor="minecraft:grass_block")
    s.set(1, 1, 4, f"{NS}:hatchery")
    for x in range(3, 12):
        s.set(x, 1, 4, f"{NS}:drekki_footprints", facing="east")
    for z in range(5, 8):
        s.set(8, 1, z, f"{NS}:drekki_footprints", facing="south")
    s.set(10, 1, 7, f"{NS}:straw_bedding")
    s.save("footprints/trail")


def lang():
    src = (Path(__file__).resolve().parent.parent / "src/main/java/dev/frosthaven/ranchercompanion/compat/ponder/DrekkiPonder.java").read_text()
    p = Path(__file__).resolve().parent.parent / "src/main/resources/assets/rancher_companion/lang/en_us.json"
    data = json.loads(p.read_text())
    for k in [k for k in data if k.startswith(f"{NS}.ponder.")]:
        del data[k]
    scene, n = None, 0
    for m in re.finditer(r'scene\.title\("([a-z_]+)", "((?:[^"\\]|\\.)*)"\)|\.text\("((?:[^"\\]|\\.)*)"\)', src):
        if m.group(1):
            scene, n = m.group(1), 1
            data[f"{NS}.ponder.{scene}.header"] = m.group(2)
        else:
            data[f"{NS}.ponder.{scene}.text_{n}"] = m.group(3)
            n += 1
    p.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    lang()
    stable()
    line()
    diets()
    feeding()
    rebirth()
    whistle()
    trail()
