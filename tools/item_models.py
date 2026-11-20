"""Writes the mod's 3D block-shaped models and their textures (spec, Accessory: bags; Carriers).

Run from anywhere: `uv run --with pillow python -I tools/item_models.py`.

- The bags: block/drekki_satchel and block/drekki_saddlebag, used by the placed bags (plain and Sophisticated) and as the
  bags' item models. Two bags side by side with a strap over both; tint 0 is the leather (the main dye), 1 the flaps and
  strap (the trim dye), 2 the metal (the tier's color).
- The barding: item/barding_fledgling and item/barding_mature, a heap of the pieces (breastplate, bands, on a Mature a
  face and a knee plate), each tier's item a child with its own plate pattern (tools/item_icons_barding.py MATERIALS).
- The tombstone: block/tombstone, placed where a drekki died and as its item model.
- The carriers: item/small_drekki_carrier and item/large_drekki_carrier, 3D in the hand and the inventory (not
  placeable): a crate with leather sides, a grated door, feet and a handle; the small one framed in wood, the large one
  in iron with rivets, as their old icons were.
"""
import importlib.util
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/rancher_companion"


def el(frm, to, tex, tint=None, skip=()):
    """a cube; skip names faces that rest on another cube (never seen, so never drawn)"""
    face = {"texture": "#" + tex}
    if tint is not None:
        face["tintindex"] = tint
    return {"from": frm, "to": to, "faces": {d: dict(face) for d in ("north", "south", "east", "west", "up", "down") if d not in skip}}


def display(scale):
    return {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 2, 0], "scale": [scale * 0.625] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.4] * 3},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [scale * 0.6] * 3},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45] * 3},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.5] * 3},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.5] * 3},
    }


def bag(big):
    """the design page's bags: leather bags, flaps over the top front, a clasp (and on a saddlebag a strip) in metal"""
    bags, h = ([([1, 0, 4], [7, 9, 12]), ([9, 0, 4], [15, 9, 12])], 9) if big else ([([2, 0, 5], [7, 6, 11]), ([9, 0, 5], [14, 6, 11])], 6)
    els = []
    for f, t in bags:
        x0, x1, z0, z1 = f[0], t[0], f[2], t[2]
        cx = (x0 + x1) / 2
        els.append(el(f, t, "leather", 0))
        els.append(el([x0 - 0.2, h - 1, z0 - 0.2], [x1 + 0.2, h + 0.8, z0 + (z1 - z0) * 0.55], "trim", 1))
        els.append(el([cx - 0.5, h - 1.6, z0 - 0.6], [cx + 0.5, h - 0.4, z0 - 0.2], "metal", 2))
        if big:
            els.append(el([x0 + 0.6, h + 0.8, z0 + 0.2], [x1 - 0.6, h + 1.2, z0 + 1.0], "metal", 2))
    zc = (bags[0][0][2] + bags[0][1][2]) / 2
    els.append(el([bags[0][0][0], h + 0.6, zc + 0.5], [bags[1][1][0], h + 1.4, zc + 1.5], "trim", 1))
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": "rancher_companion:block/bag_leather", "leather": "rancher_companion:block/bag_leather",
                         "trim": "rancher_companion:block/bag_trim", "metal": "rancher_companion:block/bag_metal"},
            "elements": els, "display": display(0.95 if big else 1.15)}


def carrier(large):
    """a crate: leather sides inside a frame (wood, or iron with rivets), a grated door on the front, feet, a handle"""
    x0, x1, y1, z0, z1 = (1, 15, 12, 2, 14) if large else (3, 13, 9, 4, 12)
    frame = "iron" if large else "wood"
    els = [el([x0 + 0.5, 1, z0 + 0.5], [x1 - 0.5, y1 - 0.5, z1 - 0.5], "leather")]                       # the body
    for x in (x0, x1 - 1):                                                                                  # corner posts
        for z in (z0, z1 - 1):
            els.append(el([x, 1, z], [x + 1, y1, z + 1], frame))
            els.append(el([x, 0, z], [x + 1, 1, z + 1], "handle"))                                         # feet
    els.append(el([x0, y1 - 1, z0], [x1, y1, z1], frame))                                                  # the top
    els.append(el([x0, 1, z0], [x1, 2, z1], frame))                                                        # the base
    gx0, gx1 = x0 + 2, x1 - 2                                                                              # the door's grate
    els.append(el([gx0, 2, z0 - 0.4], [gx1, 3, z0 + 0.1], frame))
    els.append(el([gx0, y1 - 2, z0 - 0.4], [gx1, y1 - 1, z0 + 0.1], frame))
    x = gx0 + 1
    while x < gx1 - 0.5:
        els.append(el([x, 3, z0 - 0.3], [x + 0.6, y1 - 2, z0 + 0.05], "grate"))
        x += 2
    if large:
        for x in (x0 + 0.2, x1 - 0.8):                                                                     # rivets
            for y in (2.5, y1 - 2.5):
                els.append(el([x, y, z0 - 0.3], [x + 0.6, y + 0.6, z0 + 0.1], "rivet"))
    hx0, hx1, hz = (x0 + x1) / 2 - 3, (x0 + x1) / 2 + 3, (z0 + z1) / 2                                     # the handle
    els.append(el([hx0, y1, hz - 0.5], [hx0 + 1, y1 + 2, hz + 0.5], "handle"))
    els.append(el([hx1 - 1, y1, hz - 0.5], [hx1, y1 + 2, hz + 0.5], "handle"))
    els.append(el([hx0, y1 + 2, hz - 0.5], [hx1, y1 + 3, hz + 0.5], "handle"))
    t = {k: f"rancher_companion:block/carrier_{k}" for k in ("leather", "wood", "iron", "grate", "rivet", "handle")}
    return {"parent": "minecraft:block/block", "textures": {"particle": t[frame], **t}, "elements": els,
            "display": display(0.9 if large else 1.1)}


def tombstone():
    """a drekki's headstone (spec, Tombstone), facing north: a plinth, the stone with a stepped round top, a drekki's
    horned head carved on its face (a dark inlay 0.25 px proud of it, so no face shares the stone's plane), a name
    line, and a small amethyst-purple crystal at its foot"""
    els = [
        el([1, 0, 4], [15, 2, 12], "plinth"),
        el([3, 2, 6.5], [13, 12, 9.5], "stone", skip=("down",)),
        el([4, 12, 6.5], [12, 14, 9.5], "stone", skip=("down",)),
        el([5.5, 14, 6.5], [10.5, 15, 9.5], "stone", skip=("down",)),
        # the carving: head, snout, two swept horns, the name line
        el([6.5, 7, 6.25], [9.5, 9.5, 6.5], "carve", skip=("south",)),
        el([7.25, 6, 6.25], [8.75, 7, 6.5], "carve", skip=("south",)),
        el([5.75, 9.5, 6.25], [6.75, 11.5, 6.5], "carve", skip=("south",)),
        el([9.25, 9.5, 6.25], [10.25, 11.5, 6.5], "carve", skip=("south",)),
        el([5, 3.5, 6.25], [11, 4.5, 6.5], "carve", skip=("south",)),
        # the crystal at the foot, front right
        el([11.5, 2, 4.75], [12.75, 4.5, 6], "crystal", skip=("down",)),
        el([12.75, 2, 5.25], [13.5, 3.25, 6], "crystal", skip=("down",)),
    ]
    t = {k: f"rancher_companion:block/tomb_{k}" for k in ("plinth", "stone", "carve", "crystal")}
    return {"parent": "minecraft:block/block", "textures": {"particle": t["stone"], **t}, "elements": els, "display": display(1.0)}


def rot(e, axis, angle, origin):
    """turns one cube (Minecraft allows 22.5 degree steps up to 45)"""
    e["rotation"] = {"angle": angle, "axis": axis, "origin": origin}
    return e


def ring(x0, z0, size, y0, t, tex_, tint):
    """a neck band lying flat: four bars around a square hole"""
    x1, z1 = x0 + size, z0 + size
    return [el([x0, y0, z0], [x1, y0 + t, z0 + 1], tex_, tint), el([x0, y0, z1 - 1], [x1, y0 + t, z1], tex_, tint),
            el([x0, y0, z0 + 1], [x0 + 1, y0 + t, z1 - 1], tex_, tint), el([x1 - 1, y0, z0 + 1], [x1, y0 + t, z1 - 1], tex_, tint)]


def barding(mature):
    """a pile of barding (spec, Barding: icons): the chest piece lying flat, curved by a raised center ridge and two side
    flaps sloping down, its rim along the front edge, two leather straps with buckles across it; two neck bands rest on
    it, overlapping, each tilted 22.5 degrees (the smallest tilt a model allows) and hanging over its side. A Mature pile
    is bigger, with a face plate and a knee plate peeking out from under it. Plates take tint 0 (the main dye or the
    tier's plate color), rims tint 1 (the trim dye or the tier's rim color)."""
    k = 1.0 if mature else 0.82
    def S(v):
        return round(8 + (v - 8) * k, 3)
    els = [
        el([S(3), 0, S(3.5)], [S(13), 1.5, S(13)], "plate", 0),                                  # the chest piece
        el([S(7), 1.5, S(4)], [S(9), 2, S(12.5)], "plate", 0, skip=("down",)),                    # its center ridge
        rot(el([S(1), 0, S(4)], [S(3.25), 1, S(12.5)], "plate", 0), "z", -22.5, [S(3), 1.5, 8]),  # side flaps, sloping
        rot(el([S(12.75), 0, S(4)], [S(15), 1, S(12.5)], "plate", 0), "z", 22.5, [S(13), 1.5, 8]),
        el([S(2.5), 0, S(2.5)], [S(13.5), 2, S(3.75)], "rim", 1),                                 # the rim along the front
        el([S(4.5), 1.5, S(3.75)], [S(5.5), 1.75, S(13)], "strap", skip=("down",)),              # straps and buckles
        el([S(10.5), 1.5, S(3.75)], [S(11.5), 1.75, S(13)], "strap", skip=("down",)),
        el([S(4.25), 1.75, S(7.5)], [S(5.75), 2.25, S(8.75)], "buckle", skip=("down",)),
        el([S(10.25), 1.75, S(7.5)], [S(11.75), 2.25, S(8.75)], "buckle", skip=("down",)),
    ]
    # two neck bands on it, overlapping, each tilted over a side
    for x0, z0, size, y0, ang, edge in ((S(8), S(5), 7 * k, 2.25, 22.5, S(13)), (S(2.5), S(7), 6 * k, 3.5, -22.5, S(3))):
        o = [edge, y0, z0 + size / 2]
        band = ring(x0, z0, size, y0, 1, "plate", 0)
        band.append(el([x0 + 0.5, y0 + 1, z0 + 0.5], [x0 + size - 0.5, y0 + 1.4, z0 + 1.25], "rim", 1, skip=("down",)))
        els += [rot(e, "z", ang, o) for e in band]
    if mature:
        els += [rot(el([8.5, 0, 11.5], [14.5, 0.75, 15.5], "plate", 0), "y", 22.5, [11.5, 0, 13.5]),   # the face plate
                rot(el([9, 0.75, 14.25], [14, 1.25, 15], "rim", 1), "y", 22.5, [11.5, 0, 13.5]),
                rot(el([0.5, 0, 0.5], [4.5, 0.75, 4.5], "plate", 0), "y", -22.5, [2.5, 0, 2.5])]   # the knee plate
    return {"parent": "minecraft:block/block", "textures": {"particle": "#plate", "rim": "rancher_companion:item/barding_rim",
            "strap": "rancher_companion:item/barding_strap", "buckle": "rancher_companion:item/barding_buckle"},
            "elements": els, "display": low(display(1.1 if mature else 1.25))}


def low(d):
    """a low pile: raised a little to sit centered in the inventory, and turned to face out of an item frame"""
    d["gui"]["translation"] = [0, 4, 0]
    d["fixed"]["rotation"] = [90, 0, 0]  # an item frame looks at it from the front: laid back flat against the frame, top out
    d["fixed"]["translation"] = [0, 0, 0]
    return d


def barding_materials():
    """the tiers' 16x16 grayscale plate patterns (tools/item_icons_barding.py), whole squares for the 3D plates"""
    spec = importlib.util.spec_from_file_location("item_icons_barding", Path(__file__).resolve().parent / "item_icons_barding.py")
    art = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(art)
    gray = {"o": (0x3c,) * 3, "1": (0x70,) * 3, "2": (0x9a,) * 3, "3": (0xc4,) * 3, "4": (0xe2,) * 3, "5": (0xff,) * 3}
    for tier, rows in art.MATERIALS.items():
        im = Image.new("RGBA", (16, 16))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                im.putpixel((x, y), gray.get(ch, (0xc4,) * 3) + (255,))
        im.save(ROOT / "textures/item" / f"barding_{tier}_plate.png")
    return list(art.MATERIALS)


def tex(name, rgb, spread, seed, grain=True, folder="block"):
    """a 16x16 texture: a color with a little noise, and a grain line every few rows"""
    r = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = r.randint(-spread, spread) + (-10 if grain and (x * 7 + y * 3) % 13 == 0 else 0)
            im.putpixel((x, y), tuple(max(0, min(255, c + k)) for c in rgb) + (255,))
    im.save(ROOT / "textures" / folder / f"{name}.png")


def main():
    (ROOT / "textures/block").mkdir(parents=True, exist_ok=True)
    for name, big in (("drekki_satchel", False), ("drekki_saddlebag", True)):
        json.dump(bag(big), open(ROOT / f"models/block/{name}.json", "w"), indent=2)
    for name, large in (("small_drekki_carrier", False), ("large_drekki_carrier", True)):
        json.dump(carrier(large), open(ROOT / f"models/item/{name}.json", "w"), indent=2)
    json.dump(tombstone(), open(ROOT / "models/block/tombstone.json", "w"), indent=2)
    for mature in (False, True):
        json.dump(barding(mature), open(ROOT / f"models/item/barding_{'mature' if mature else 'fledgling'}.json", "w"), indent=2)
    for tier in barding_materials():
        for stage, prefix in (("mature", ""), ("fledgling", "fledgling_")):
            json.dump({"parent": f"rancher_companion:item/barding_{stage}", "textures": {"plate": f"rancher_companion:item/barding_{tier}_plate"}},
                      open(ROOT / f"models/item/{prefix}{tier}_barding.json", "w"), indent=2)
    tex("barding_rim", (0xe8, 0xe8, 0xe8), 6, 14, grain=False, folder="item")
    tex("barding_strap", (0x5a, 0x3a, 0x1e), 8, 15, folder="item")
    tex("barding_buckle", (0xd8, 0xb0, 0x40), 10, 16, grain=False, folder="item")
    tex("tomb_plinth", (0x41, 0x45, 0x4e), 7, 10)
    tex("tomb_stone", (0x6a, 0x6f, 0x7a), 9, 11)
    tex("tomb_carve", (0x2b, 0x2e, 0x35), 4, 12, grain=False)
    tex("tomb_crystal", (0xb0, 0x5c, 0xd0), 14, 13, grain=False)
    tex("bag_leather", (225, 225, 225), 10, 1)
    tex("bag_trim", (225, 225, 225), 8, 2)
    tex("bag_metal", (235, 235, 235), 6, 3, grain=False)
    tex("carrier_leather", (0x8b, 0x5a, 0x2b), 8, 4)
    tex("carrier_wood", (0xa0, 0x78, 0x40), 8, 5)
    tex("carrier_iron", (0xc8, 0xcc, 0xd0), 6, 6, grain=False)
    tex("carrier_grate", (0xe8, 0xe4, 0xdc), 5, 7, grain=False)
    tex("carrier_rivet", (0xf0, 0xf0, 0xf0), 3, 8, grain=False)
    tex("carrier_handle", (0x6a, 0x40, 0x20), 8, 9)
    print("wrote the bag, carrier, barding and tombstone models")


if __name__ == "__main__":
    main()
