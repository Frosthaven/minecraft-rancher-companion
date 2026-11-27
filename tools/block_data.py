"""Blockstates, models, item models, loot tables, recipes, tags and names for the hatchery, the trough and the decor set.

The hatchery and trough models are the design page's (Blocks tab) cubes in 16 px block space, with the textures from
tools/block_textures.py; leather faces carry tintindex 0 (the dye, BlockClient). The trough is a multipart: the base, then
an end wall or a joint on each side of the line, the straw and greens when it holds food, and the plaques by diet.
No two same-facing faces share a plane (the design page's rule): bands and rivets stand proud, decals sit off the surface.

Run: python3 -I tools/block_data.py   (writes under src/main/resources, merges names into en_us.json)
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
NS = "rancher_companion"
A, D = ROOT / "assets" / NS, ROOT / "data"
DYES = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue",
        "brown", "green", "red", "black"]
DIETS = ["none", "raw", "cooked", "seed", "root"]
written = []


def put(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=1) + "\n")
    written.append(path)


def t(name):
    return f"{NS}:block/{name}"


# ---------- model elements ----------

def _shift(a, b):
    lo, hi = min(a, b), max(a, b)
    if lo < 0:
        a, b = a + 16, b + 16
    elif hi > 16:
        a, b = a - 16, b - 16
    return max(0, min(16, a)), max(0, min(16, b))


def uv(face, f, to):
    x1, y1, z1 = f
    x2, y2, z2 = to
    u = {"down": (x1, 16 - z2, x2, 16 - z1), "up": (x1, z1, x2, z2), "north": (16 - x2, 16 - y2, 16 - x1, 16 - y1),
         "south": (x1, 16 - y2, x2, 16 - y1), "west": (z1, 16 - y2, z2, 16 - y1), "east": (16 - z2, 16 - y2, 16 - z1, 16 - y1)}[face]
    a, c = _shift(u[0], u[2])
    b, d = _shift(u[1], u[3])
    return [round(a, 3), round(b, 3), round(c, 3), round(d, 3)]


def el(f, to, tex, tint=False, rot=None, faces=None):
    """a cube from f to to, every face with texture variable #tex (a flat cube keeps only its two big faces)"""
    sizes = [to[i] - f[i] for i in range(3)]
    if faces is None:
        faces = ["down", "up", "north", "south", "west", "east"]
        if sizes[1] == 0: faces = ["down", "up"]
        elif sizes[0] == 0: faces = ["west", "east"]
        elif sizes[2] == 0: faces = ["north", "south"]
    out = {"from": list(f), "to": list(to), "faces": {}}
    for face in faces:
        fc = {"uv": uv(face, f, to), "texture": "#" + tex}
        if tint:
            fc["tintindex"] = 0
        out["faces"][face] = fc
    if rot:
        out["rotation"] = rot
    return out


def model(elements, textures, parent="minecraft:block/block", render=None):
    m = {"parent": parent, "textures": textures, "elements": elements}
    if render:
        m["render_type"] = render
    return m


STYLE = {"wood": t("style_wood"), "steel": t("style_steel"), "steel_dark": t("style_steel_dark"), "rivet": t("style_rivet"),
         "leather": t("style_leather"), "leather_light": t("style_leather_light"), "straw": t("style_straw"),
         "straw_light": t("style_straw_light"), "greens": t("style_greens"), "greens_light": t("style_greens_light"),
         "feather": t("style_feather"), "feather_dark": t("style_feather_dark"), "feather_light": t("style_feather_light"),
         "particle": t("style_wood")}


def hatchery():
    """the page's buildHatchery: a dark oak chest (12 px), dark steel corner posts, two riveted steel bands, a leather pad
    and padded rim (tinted), a bed of straw heaped and spilling over the rim, three feathers"""
    e = [el((2, 0, 2), (14, 8, 14), "wood")]
    for x in (0, 13):
        for z in (0, 13):
            e.append(el((x, 0, z), (x + 3, 9, z + 3), "steel_dark"))
    for y in (1, 6):
        e.append(el((1.5, y, 1.5), (14.5, y + 1, 14.5), "steel"))
        for p in (-3, 0, 3):
            e.append(el((7.5 + p, y, 14.5), (8.5 + p, y + 1, 15.5), "rivet"))
            e.append(el((7.5 + p, y, 0.5), (8.5 + p, y + 1, 1.5), "rivet"))
            e.append(el((14.5, y, 7.5 + p), (15.5, y + 1, 8.5 + p), "rivet"))
            e.append(el((0.5, y, 7.5 + p), (1.5, y + 1, 8.5 + p), "rivet"))
    e.append(el((2, 8, 2), (14, 9, 14), "leather", tint=True))
    e += [el((2, 9, 12), (14, 11, 14), "leather_light", tint=True), el((2, 9, 2), (14, 11, 4), "leather_light", tint=True),
          el((12, 9, 4), (14, 11, 12), "leather_light", tint=True), el((2, 9, 4), (4, 11, 12), "leather_light", tint=True)]
    e.append(el((4, 9, 4), (12, 11, 12), "straw"))
    for x, z in ((-3.5, 1), (3.5, -1.5), (1, 3.5), (-1.5, -3.5), (3.5, 2.5), (-3.5, -2)):  # clumps, apart: no tops share a plane
        e.append(el((7 + x, 11, 7 + z), (9 + x, 12, 9 + z), "straw_light"))
    e += [el((3.5, 11, 12), (6.5, 12, 14), "straw"), el((12, 11, 2.5), (14, 12, 5.5), "straw"),  # tufts across the rim
          el((6, 9, 14), (10, 11, 15), "straw_light"), el((3.5, 9, 1), (6.5, 11, 2), "straw_light"),  # spilling over the sides
          el((14, 9, 7.5), (15, 11, 10.5), "straw_light"), el((1, 9, 5), (2, 11, 9), "straw_light")]
    e += [el((3, 11.5, 2), (5, 11.5, 9), "feather_dark", rot={"origin": [4, 11.5, 4], "axis": "x", "angle": -45}),
          el((5, 11.5, 3), (6.5, 11.5, 8), "feather_light", rot={"origin": [5.5, 11.5, 4], "axis": "x", "angle": -22.5}),
          el((11, 11.5, 2), (13, 11.5, 8), "feather", rot={"origin": [12, 11.5, 4], "axis": "x", "angle": -45})]
    return model(e, STYLE, render="minecraft:cutout")


# ---------- the trough: local model opens toward -x (west); the line runs along z ----------

def trough_parts():
    P = {}
    P["base"] = [el((2, 0, 2), (14, 2, 14), "wood"), el((14, 0, 2), (16, 14, 14), "wood"),
                 el((0, 12, 2), (2, 14, 14), "wood"), el((0, 2, 2), (2, 5, 14), "wood")]
    P["base"] += [el((x, 0, z), (x + 2, 14, z + 2), "steel_dark") for x in (0, 14) for z in (0, 14)]
    P["base"] += [el((-0.5, 14, 0), (2.5, 16, 16), "leather_light", tint=True), el((13.5, 14, 0), (16.5, 16, 16), "leather_light", tint=True)]
    for y in (2, 13):
        P["base"] += [el((-1, y, 0), (0, y + 1, 16), "steel"), el((16, y, 0), (17, y + 1, 16), "steel")]
        P["base"] += [el((17, y, 7.5 + p), (18, y + 1, 8.5 + p), "rivet") for p in (-4, 0, 4)]
    for side, z0, z1, zs, zr in (("north", 0, 2, (-1, 0), (-2, -1)), ("south", 14, 16, (16, 17), (17, 18))):
        end = [el((2, 0, z0), (14, 14, z1), "wood"), el((2.5, 14, z0 - 0.5 if side == "north" else z0 - 0.5), (13.5, 16, z1 + 0.5), "leather_light", tint=True)]
        for y in (2, 13):
            end.append(el((-1, y, zs[0]), (17, y + 1, zs[1]), "steel"))
            end += [el((7.5 + p, y, zr[0]), (8.5 + p, y + 1, zr[1]), "rivet") for p in (-4, 0, 4)]
        P["end_" + side] = end
        P["join_" + side] = [el((2, 0, z0), (14, 2, z1), "wood")]
        P["straw_" + side] = [el((2, 2, z0), (14, 7, z1), "straw")]
    P["straw"] = [el((2, 2, 2), (14, 7, 14), "straw")]
    P["greens"] = [el((3, 7, 3), (13, 8, 13), "greens")] + [el((x, 8, z), (x + 3, 9, z + 3), "greens_light") for x, z in ((3, 3), (10, 10), (10, 3))]
    for d in DIETS:  # plaques: back (+x), and an end plaque on each unjoined end
        P["plaque_back_" + d] = [el((16, 5, 5), (17, 11, 11), "plaque", faces=["east", "up", "down", "north", "south"])]
        P["plaque_north_" + d] = [el((5, 5, -1), (11, 11, 0), "plaque", faces=["north", "up", "down", "east", "west"])]
        P["plaque_south_" + d] = [el((5, 5, 16), (11, 11, 17), "plaque", faces=["south", "up", "down", "east", "west"])]
    return P


def plaque_tex(d):
    return {"plaque": t("trough_plaque" if d == "none" else "trough_plaque_" + d)}


def plaque_uv_fix(elements):
    """the emblem sits at texture pixels 5..10: map each plaque face's 6 x 6 onto it"""
    for e in elements:
        for face, fc in e["faces"].items():
            if fc["texture"] == "#plaque":
                fc["uv"] = [5, 5, 11, 11] if face in ("east", "north", "south", "west") and _big(e, face) else [5, 5, 6, 11]
    return elements


def _big(e, face):
    f, to = e["from"], e["to"]
    w = {"east": (to[2] - f[2]), "west": (to[2] - f[2]), "north": (to[0] - f[0]), "south": (to[0] - f[0])}[face]
    return w >= 6


def trough():
    parts = trough_parts()
    for name, els in parts.items():
        tex = dict(STYLE)
        if name.startswith("plaque_"):
            d = name.rsplit("_", 1)[1]
            tex.update(plaque_tex(d))
            els = plaque_uv_fix(els)
        put(A / f"models/block/trough/{name}.json", model(els, tex))
    rot = {"west": 0, "north": 90, "east": 180, "south": 270}
    mp = []

    def add(part, when):
        for f, y in rot.items():
            w = {"facing": f, **when}
            apply = {"model": f"{NS}:block/trough/{part}"}
            if y:
                apply["y"] = y
            mp.append({"when": w, "apply": apply})

    add("base", {})
    for d_i, d in enumerate(DIETS):
        add("plaque_back_" + d, {"diet": str(d_i)})
        add("plaque_north_" + d, {"left": "false", "diet": str(d_i)})
        add("plaque_south_" + d, {"right": "false", "diet": str(d_i)})
    add("end_north", {"left": "false"})
    add("end_south", {"right": "false"})
    add("join_north", {"left": "true"})
    add("join_south", {"right": "true"})
    add("straw", {"diet": "1|2|3|4"})
    add("straw_north", {"left": "true", "diet": "1|2|3|4"})
    add("straw_south", {"right": "true", "diet": "1|2|3|4"})
    add("greens", {"diet": "3|4"})
    put(A / "blockstates/trough.json", {"multipart": mp})
    # the item: a whole single trough with blank plaques
    whole = parts["base"] + parts["end_north"] + parts["end_south"] + plaque_uv_fix(
        [dict(e) for e in parts["plaque_back_none"] + parts["plaque_north_none"] + parts["plaque_south_none"]])
    tex = dict(STYLE)
    tex.update(plaque_tex("none"))
    put(A / "models/block/trough_item.json", model(whole, tex))
    put(A / "models/item/trough.json", {"parent": f"{NS}:block/trough_item"})


# ---------- decor ----------

def cube_all(name, tex):
    put(A / f"models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": t(tex)}})


def slab(name, full_model, tex):
    for suffix, parent in (("", "slab"), ("_top", "slab_top")):
        put(A / f"models/block/{name}{suffix}.json", {"parent": f"minecraft:block/{parent}", "textures": {"bottom": t(tex), "top": t(tex), "side": t(tex)}})
    put(A / f"blockstates/{name}.json", {"variants": {"type=bottom": {"model": f"{NS}:block/{name}"}, "type=top": {"model": f"{NS}:block/{name}_top"},
                                                         "type=double": {"model": full_model}}})


def stairs(name, tex):
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        put(A / f"models/block/{name}{suffix}.json", {"parent": f"minecraft:block/{parent}", "textures": {"bottom": t(tex), "top": t(tex), "side": t(tex)}})
    base = {"east": 0, "south": 90, "west": 180, "north": 270}
    v = {}
    for f, y0 in base.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                m = name + ("_inner" if shape.startswith("inner") else "_outer" if shape.startswith("outer") else "")
                y = y0
                if half == "bottom" and shape.endswith("left"):
                    y -= 90
                if half == "top" and shape.endswith("right"):
                    y += 90
                entry = {"model": f"{NS}:block/{m}"}
                if half == "top":
                    entry["x"] = 180
                if y % 360:
                    entry["y"] = y % 360
                if half == "top" or y % 360 or shape != "straight":
                    entry["uvlock"] = True
                v[f"facing={f},half={half},shape={shape}"] = entry
    put(A / f"blockstates/{name}.json", {"variants": v})


def decor():
    cube_all("rancher_planks", "rancher_planks")
    put(A / "blockstates/rancher_planks.json", {"variants": {"": {"model": f"{NS}:block/rancher_planks"}}})
    slab("rancher_planks_slab", f"{NS}:block/rancher_planks", "rancher_planks")
    stairs("rancher_planks_stairs", "rancher_planks")

    put(A / "models/block/rancher_beam.json", {"parent": "minecraft:block/cube_column", "textures": {"end": t("rancher_beam_end"), "side": t("rancher_beam_side")}})
    put(A / "models/block/rancher_beam_horizontal.json", {"parent": "minecraft:block/cube_column_horizontal", "textures": {"end": t("rancher_beam_end"), "side": t("rancher_beam_side")}})
    put(A / "blockstates/rancher_beam.json", {"variants": {"axis=y": {"model": f"{NS}:block/rancher_beam"},
                                                             "axis=z": {"model": f"{NS}:block/rancher_beam_horizontal", "x": 90},
                                                             "axis=x": {"model": f"{NS}:block/rancher_beam_horizontal", "x": 90, "y": 90}}})
    # the beam slab lies along x: its long faces carry the bands at their ends, the x faces the half-height end grain
    tex = {"side": t("rancher_beam_slab_side"), "end": t("rancher_beam_slab_end"), "particle": t("rancher_beam_slab_side")}

    def beam_half(y0, y1):
        return {"from": [0, y0, 0], "to": [16, y1, 16], "faces": {
            "down": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "down"} if y0 == 0 else {"uv": [0, 0, 16, 16], "texture": "#side"},
            "up": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "up"} if y1 == 16 else {"uv": [0, 0, 16, 16], "texture": "#side"},
            "north": {"uv": [0, 16 - y1, 16, 16 - y0], "texture": "#side", "cullface": "north"},
            "south": {"uv": [0, 16 - y1, 16, 16 - y0], "texture": "#side", "cullface": "south"},
            "west": {"uv": [0, 16 - y1, 16, 16 - y0], "texture": "#end", "cullface": "west"},
            "east": {"uv": [0, 16 - y1, 16, 16 - y0], "texture": "#end", "cullface": "east"}}}
    put(A / "models/block/rancher_beam_slab.json", {"parent": "minecraft:block/block", "textures": tex, "elements": [beam_half(0, 8)]})
    put(A / "models/block/rancher_beam_slab_top.json", {"parent": "minecraft:block/block", "textures": tex, "elements": [beam_half(8, 16)]})
    put(A / "models/block/rancher_beam_slab_double.json", {"parent": "minecraft:block/block", "textures": tex, "elements": [beam_half(0, 8), beam_half(8, 16)]})
    v = {}
    for axis, y in (("x", 0), ("z", 90)):
        for typ, m in (("bottom", "rancher_beam_slab"), ("top", "rancher_beam_slab_top"), ("double", "rancher_beam_slab_double")):
            e = {"model": f"{NS}:block/{m}"}
            if y:
                e["y"] = y
            v[f"axis={axis},type={typ}"] = e
    put(A / "blockstates/rancher_beam_slab.json", {"variants": v})

    cube_all("rancher_shingles", "rancher_shingles")
    put(A / "blockstates/rancher_shingles.json", {"variants": {"": {"model": f"{NS}:block/rancher_shingles"}}})
    slab("rancher_shingles_slab", f"{NS}:block/rancher_shingles", "rancher_shingles")
    stairs("rancher_shingles_stairs", "rancher_shingles")

    put(A / "models/block/straw_bedding.json", {"parent": "minecraft:block/carpet", "textures": {"wool": t("straw_bedding")}})
    put(A / "blockstates/straw_bedding.json", {"variants": {"": {"model": f"{NS}:block/straw_bedding"}}})

    # Drekki Footprints: a 1 px plate, translucent (clear but for the prints, which are half see-through), turned to its facing (the texture's top walks north)
    put(A / "models/block/drekki_footprints.json", {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
        "textures": {"prints": t("drekki_footprints"), "particle": t("drekki_footprints")},
        "elements": [{"from": [0, 0, 0], "to": [16, 0.25, 16], "faces": {
            "up": {"uv": [0, 0, 16, 16], "texture": "#prints"},
            "down": {"uv": [0, 0, 16, 16], "texture": "#prints", "cullface": "down"}}}]})
    put(A / "blockstates/drekki_footprints.json", {"variants": {
        f"facing={f}": ({"model": f"{NS}:block/drekki_footprints", "y": y} if y else {"model": f"{NS}:block/drekki_footprints"})
        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})

    # tufted leather: one grey texture tinted by the block's dye, the steel studs an untinted overlay
    ltex = {"leather": t("tufted_leather"), "studs": t("tufted_leather_studs"), "particle": t("tufted_leather")}

    def leather_box(y0, y1):
        base = el((0, y0, 0), (16, y1, 16), "leather", tint=True)
        over = el((0, y0, 0), (16, y1, 16), "studs")
        for face in ("down", "up", "north", "south", "west", "east"):
            if (face == "down" and y0 == 0) or (face == "up" and y1 == 16) or face not in ("down", "up"):
                base["faces"][face]["cullface"] = face
                over["faces"][face]["cullface"] = face
        return [base, over]
    put(A / "models/block/tufted_leather.json", model(leather_box(0, 16), ltex, render="minecraft:cutout"))
    put(A / "models/block/tufted_leather_slab.json", model(leather_box(0, 8), ltex, render="minecraft:cutout"))
    put(A / "models/block/tufted_leather_slab_top.json", model(leather_box(8, 16), ltex, render="minecraft:cutout"))
    for i in range(17):
        name = "tufted_leather" if i == 0 else f"{DYES[i - 1]}_tufted_leather"
        put(A / f"blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/tufted_leather"}}})
        put(A / f"blockstates/{name}_slab.json", {"variants": {"type=bottom": {"model": f"{NS}:block/tufted_leather_slab"},
                                                                "type=top": {"model": f"{NS}:block/tufted_leather_slab_top"},
                                                                "type=double": {"model": f"{NS}:block/tufted_leather"}}})
        put(A / f"models/item/{name}.json", {"parent": f"{NS}:block/tufted_leather"})
        put(A / f"models/item/{name}_slab.json", {"parent": f"{NS}:block/tufted_leather_slab"})

    # the fence: a beam post under a dark steel cap, plank rails (2 x 3) at 5..8 and 11..14 toward each neighbor
    ftex = {"post": t("rancher_beam_side"), "cap": t("style_steel_dark"), "rail": t("rancher_planks"), "particle": t("rancher_planks")}
    post = [el((6, 0, 6), (10, 15, 10), "post"), el((5.5, 14, 5.5), (10.5, 16, 10.5), "cap")]
    side = [el((7, 5, 0), (9, 8, 9), "rail"), el((7, 11, 0), (9, 14, 9), "rail")]
    put(A / "models/block/rancher_fence_post.json", model(post, ftex, parent="minecraft:block/block"))
    put(A / "models/block/rancher_fence_side.json", model(side, ftex, parent="minecraft:block/block"))
    inv = [el((6, 0, 2), (10, 15, 6), "post"), el((5.5, 14, 1.5), (10.5, 16, 6.5), "cap"),
           el((6, 0, 10), (10, 15, 14), "post"), el((5.5, 14, 9.5), (10.5, 16, 14.5), "cap"),
           el((7, 5, 0), (9, 8, 16), "rail"), el((7, 11, 0), (9, 14, 16), "rail")]
    put(A / "models/block/rancher_fence_inventory.json", {"parent": "minecraft:block/fence_inventory", "textures": ftex, "elements": inv})
    mp = [{"apply": {"model": f"{NS}:block/rancher_fence_post"}}]
    for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        a = {"model": f"{NS}:block/rancher_fence_side", "uvlock": True}
        if y:
            a["y"] = y
        mp.append({"when": {f: "true"}, "apply": a})
    put(A / "blockstates/rancher_fence.json", {"multipart": mp})
    put(A / "models/item/rancher_fence.json", {"parent": f"{NS}:block/rancher_fence_inventory"})

    for suffix, parent in (("", "template_fence_gate"), ("_open", "template_fence_gate_open"), ("_wall", "template_fence_gate_wall"),
                           ("_wall_open", "template_fence_gate_wall_open")):
        put(A / f"models/block/rancher_fence_gate{suffix}.json", {"parent": f"minecraft:block/{parent}", "textures": {"texture": t("rancher_planks")}})
    v = {}
    for f, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
        for wall in ("false", "true"):
            for op in ("false", "true"):
                m = "rancher_fence_gate" + ("_wall" if wall == "true" else "") + ("_open" if op == "true" else "")
                e = {"model": f"{NS}:block/{m}", "uvlock": True}
                if y:
                    e["y"] = y
                v[f"facing={f},in_wall={wall},open={op}"] = e
    put(A / "blockstates/rancher_fence_gate.json", {"variants": v})

    simple_items = {"rancher_planks": "rancher_planks", "rancher_planks_slab": "rancher_planks_slab", "rancher_planks_stairs": "rancher_planks_stairs",
                    "rancher_beam": "rancher_beam", "rancher_beam_slab": "rancher_beam_slab", "rancher_shingles": "rancher_shingles",
                    "rancher_shingles_slab": "rancher_shingles_slab", "rancher_shingles_stairs": "rancher_shingles_stairs",
                    "rancher_fence_gate": "rancher_fence_gate", "hatchery": "hatchery"}
    for item, m in simple_items.items():
        put(A / f"models/item/{item}.json", {"parent": f"{NS}:block/{m}"})
    put(A / "models/item/straw_bedding.json", {"parent": f"{NS}:block/straw_bedding"})
    put(A / "models/item/drekki_footprints.json", {"parent": "minecraft:item/generated", "textures": {"layer0": t("drekki_footprints")}})


# ---------- data: loot, recipes, tags ----------

def all_blocks():
    names = ["hatchery", "trough", "rancher_planks", "rancher_planks_slab", "rancher_planks_stairs", "rancher_beam", "rancher_beam_slab",
             "rancher_shingles", "rancher_shingles_slab", "rancher_shingles_stairs", "straw_bedding", "rancher_fence", "rancher_fence_gate", "drekki_footprints"]
    names += ["tufted_leather"] + [f"{d}_tufted_leather" for d in DYES]
    names += ["tufted_leather_slab"] + [f"{d}_tufted_leather_slab" for d in DYES]
    return names


def loot():
    for name in all_blocks():
        entry = {"type": "minecraft:item", "name": f"{NS}:{name}"}
        if name.endswith("_slab"):
            entry["functions"] = [{"function": "minecraft:set_count", "count": 2, "add": False, "conditions": [
                {"condition": "minecraft:block_state_property", "block": f"{NS}:{name}", "properties": {"type": "double"}}]},
                {"function": "minecraft:explosion_decay"}]
        if name in ("hatchery", "trough"):  # the dye travels with the block
            entry["functions"] = [{"function": "minecraft:copy_state", "block": f"{NS}:{name}", "properties": ["color"]}]
        if name == "hatchery":  # an anvil name stays with the block (spec, Checkout: Names)
            entry["functions"].append({"function": "minecraft:copy_components", "source": "block_entity", "include": ["minecraft:custom_name"]})
        put(D / NS / f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [
            {"rolls": 1, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"{NS}:blocks/{name}"})


def shaped(name, pattern, key, result, count=1, cat="building", when=None):
    k = {c: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for c, v in key.items()}
    r = {"type": "minecraft:crafting_shaped", "category": cat, "pattern": pattern, "key": k,
         "result": {"id": f"{NS}:{result}", "count": count}}
    if when:
        r = {"neoforge:conditions": when, **r}
    put(D / NS / f"recipe/{name}.json", r)


FD = [{"type": "neoforge:mod_loaded", "modid": "farmersdelight"}]


def shapeless(name, ingredients, result, count=1, group=None, components=None):
    r = {"type": "minecraft:crafting_shapeless", "category": "building",
         "ingredients": [({"tag": v[1:]} if v.startswith("#") else {"item": v}) for v in ingredients],
         "result": {"id": f"{NS}:{result}", "count": count}}
    if components:
        r["result"]["components"] = components
    if group:
        r["group"] = group
    put(D / NS / f"recipe/{name}.json", r)


def cutting(name, ingredient, result, count):
    put(D / NS / f"recipe/{name}.json", {"type": "minecraft:stonecutting", "ingredient": {"item": ingredient}, "result": {"id": f"{NS}:{result}", "count": count}})


def recipes():
    N = "minecraft:iron_nugget"
    shaped("hatchery", ["FHF", "LPL", "IPI"], {"F": "minecraft:feather", "H": "minecraft:hay_block", "L": "minecraft:leather",
                                                 "P": "#minecraft:planks", "I": "minecraft:iron_ingot"}, "hatchery", cat="misc")
    shaped("trough", ["L L", "P P", "NPN"], {"L": "minecraft:leather", "P": "#minecraft:planks", "N": N}, "trough", cat="misc")
    shaped("rancher_planks", ["PPP", "PNP", "PPP"], {"P": "#minecraft:planks", "N": N}, "rancher_planks", 8)
    shaped("rancher_planks_slab", ["PPP"], {"P": f"{NS}:rancher_planks"}, "rancher_planks_slab", 6)
    shaped("rancher_planks_stairs", ["P  ", "PP ", "PPP"], {"P": f"{NS}:rancher_planks"}, "rancher_planks_stairs", 4)
    shaped("rancher_beam", ["N", "L", "N"], {"N": N, "L": "#minecraft:logs"}, "rancher_beam", 2)
    shaped("rancher_beam_slab", ["BBB"], {"B": f"{NS}:rancher_beam"}, "rancher_beam_slab", 6)
    shaped("rancher_shingles", ["SSS", "SNS", "SSS"], {"S": "minecraft:cobbled_deepslate", "N": N}, "rancher_shingles", 8)
    shaped("rancher_shingles_slab", ["SSS"], {"S": f"{NS}:rancher_shingles"}, "rancher_shingles_slab", 6)
    shaped("rancher_shingles_stairs", ["S  ", "SS ", "SSS"], {"S": f"{NS}:rancher_shingles"}, "rancher_shingles_stairs", 4)
    cutting("rancher_shingles_slab_from_stonecutting", f"{NS}:rancher_shingles", "rancher_shingles_slab", 2)
    cutting("rancher_shingles_stairs_from_stonecutting", f"{NS}:rancher_shingles", "rancher_shingles_stairs", 1)
    # Farmer's Delight's straw when it is loaded (two side by side: its rope is two stacked), and wheat always
    shaped("straw_bedding", ["SS"], {"S": "farmersdelight:straw"}, "straw_bedding", 3, when=FD)
    shaped("straw_bedding_from_wheat", ["WW"], {"W": "minecraft:wheat"}, "straw_bedding", 3)
    # dirt and a feather: no other two-item recipe in the Haven pack (checked 2026-11-27)
    shapeless("drekki_footprints", ["minecraft:dirt", "minecraft:feather"], "drekki_footprints", 16)
    shaped("rancher_fence", ["PSP", "PSP"], {"P": f"{NS}:rancher_planks", "S": "minecraft:stick"}, "rancher_fence", 3)
    shaped("rancher_fence_gate", ["SPS", "SPS"], {"P": f"{NS}:rancher_planks", "S": "minecraft:stick"}, "rancher_fence_gate")
    shaped("tufted_leather", ["LNL", "NWN", "LNL"], {"L": "minecraft:leather", "N": N, "W": "#minecraft:wool"}, "tufted_leather", 4)
    for i in range(17):
        name = "tufted_leather" if i == 0 else f"{DYES[i - 1]}_tufted_leather"
        shaped(f"{name}_slab", ["LLL"], {"L": f"{NS}:{name}"}, f"{name}_slab", 6)
        if i:  # like wool: any tufted leather and a dye (a fan's bulk dyeing reads the same recipes)
            shapeless(f"dye_{name}", [f"#{NS}:tufted_leather", f"minecraft:{DYES[i - 1]}_dye"], name, group="tufted_leather_dye")
            shapeless(f"dye_{name}_slab", [f"#{NS}:tufted_leather_slab", f"minecraft:{DYES[i - 1]}_dye"], f"{name}_slab", group="tufted_leather_slab_dye")
    for block in ("hatchery", "trough"):  # a hatchery or trough and a dye: the same block with that leather (its color state)
        for i, d in enumerate(DYES, 1):
            shapeless(f"dye_{block}_{d}", [f"{NS}:{block}", f"minecraft:{d}_dye"], block, group=f"{block}_dye",
                      components={"minecraft:block_state": {"color": str(i)}})


def tags():
    def tag(kind, path, values):
        put(D / path[0] / f"tags/{kind}/{path[1]}.json", {"values": [f"{NS}:{v}" for v in values]})
    axe = ["hatchery", "trough", "rancher_planks", "rancher_planks_slab", "rancher_planks_stairs", "rancher_beam", "rancher_beam_slab",
           "rancher_fence", "rancher_fence_gate"]
    tag("block", ("minecraft", "mineable/axe"), axe)
    tag("block", ("minecraft", "mineable/pickaxe"), ["rancher_shingles", "rancher_shingles_slab", "rancher_shingles_stairs", "tombstone"])
    tag("block", ("minecraft", "mineable/hoe"), ["straw_bedding"])
    slabs_wood, stairs_wood = ["rancher_planks_slab", "rancher_beam_slab"], ["rancher_planks_stairs"]
    leather = ["tufted_leather"] + [f"{d}_tufted_leather" for d in DYES]
    for kind in ("block", "item"):
        tag(kind, ("minecraft", "wooden_slabs"), slabs_wood)
        tag(kind, ("minecraft", "slabs"), slabs_wood + ["rancher_shingles_slab"] + [l + "_slab" for l in leather])
        tag(kind, ("minecraft", "wooden_stairs"), stairs_wood)
        tag(kind, ("minecraft", "stairs"), stairs_wood + ["rancher_shingles_stairs"])
        tag(kind, ("minecraft", "wooden_fences"), ["rancher_fence"])
        tag(kind, ("minecraft", "fences"), ["rancher_fence"])
        tag(kind, ("minecraft", "fence_gates"), ["rancher_fence_gate"])
    tag("item", (NS, "tufted_leather"), leather)
    tag("item", (NS, "tufted_leather_slab"), [l + "_slab" for l in leather])


def names():
    p = A / "lang/en_us.json"
    lang = json.loads(p.read_text())
    title = lambda s: " ".join(w.capitalize() for w in s.split("_"))
    lang.update({
        "block.rancher_companion.hatchery": "Drekki Hatchery",
        "block.rancher_companion.trough": "Drekki Trough",
        "block.rancher_companion.trough.holds": "This trough holds %s food",
        "block.rancher_companion.trough.full": "This trough is full",
        "block.rancher_companion.rancher_planks": "Rancher Planks",
        "block.rancher_companion.rancher_planks_slab": "Rancher Plank Slab",
        "block.rancher_companion.rancher_planks_stairs": "Rancher Plank Stairs",
        "block.rancher_companion.rancher_beam": "Rancher Beam",
        "block.rancher_companion.rancher_beam_slab": "Rancher Beam Slab",
        "block.rancher_companion.rancher_shingles": "Rancher Shingles",
        "block.rancher_companion.rancher_shingles_slab": "Rancher Shingle Slab",
        "block.rancher_companion.rancher_shingles_stairs": "Rancher Shingle Stairs",
        "block.rancher_companion.straw_bedding": "Straw Bedding",
        "block.rancher_companion.drekki_footprints": "Drekki Footprints",
        "block.rancher_companion.rancher_fence": "Rancher Fence",
        "block.rancher_companion.rancher_fence_gate": "Rancher Fence Gate",
        "block.rancher_companion.tufted_leather": "Tufted Leather",
        "block.rancher_companion.tufted_leather_slab": "Tufted Leather Slab",
    })
    for d in DYES:
        lang[f"block.rancher_companion.{d}_tufted_leather"] = f"{title(d)} Tufted Leather"
        lang[f"block.rancher_companion.{d}_tufted_leather_slab"] = f"{title(d)} Tufted Leather Slab"
    p.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")


def main():
    put(A / "models/block/hatchery.json", hatchery())
    put(A / "blockstates/hatchery.json", {"variants": {f"facing={f}": ({"model": f"{NS}:block/hatchery", "y": y} if y else {"model": f"{NS}:block/hatchery"})
                                                       for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    old = A / "models/block/trough.json"
    if old.exists():
        old.unlink()  # the old single-model trough; the multipart replaces it
    trough()
    decor()
    loot()
    recipes()
    tags()
    names()
    print(f"{len(written)} files written")


if __name__ == "__main__":
    main()
