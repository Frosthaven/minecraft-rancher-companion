"""Schematics for the Ponder scenes (compat/ponder/DrekkiPonder): a stable around a hatchery, and three trough scenes
(a line of 10 with an 11th apart, the four diets, feeding). Layer y 0 is the base plate. The scenes reveal parts of each
schematic step by step, so the positions here and in DrekkiPonder must agree.

Run: uv run --with nbtlib python -I tools/ponder_scenes.py
Writes assets/rancher_companion/ponder/{hatchery/stable,trough/line,trough/diets,trough/feeding}.nbt, and the scenes'
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


# ---- hatchery/stable: 11 x 8 x 11, the hatchery at (5, 1, 5) ----
def stable():
    s = Schematic(11, 8, 11)
    s.set(5, 1, 5, f"{NS}:hatchery", facing="south", color=0)
    for x, z in ((4, 4), (6, 4), (4, 6), (6, 6)):           # corner posts: fence posts, so the stable stays open
        for y in range(1, 5):
            s.set(x, y, z, f"{NS}:rancher_fence", east=False, west=False, north=False, south=False, waterlogged=False)
    for x in range(4, 7):                                    # the 3x3 roof, 4 blocks up
        for z in range(4, 7):
            s.set(x, 5, z, f"{NS}:rancher_shingles_slab", type="bottom", waterlogged=False)
    s.set(3, 1, 3, "minecraft:lantern", hanging=False, waterlogged=False)
    for x in (5, 6):                                         # bedding 3 south of the hatchery
        s.set(x, 1, 8, f"{NS}:straw_bedding")
    for x in range(7, 11):                                   # a dirt path running east from the bedding
        s.set(x, 0, 9, "minecraft:dirt_path")
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
