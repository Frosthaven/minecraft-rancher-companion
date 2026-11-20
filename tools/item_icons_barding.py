"""Barding art: a grayscale material pattern per tier, painted on the 3D barding's plates (tools/item_models.py).

Digits 1 (dark) to 5 (white) are gray levels; the game multiplies them by the dye or the tier's plate color.
"""




def _grid(fn):
    return ["".join(str(fn(x, y)) for x in range(16)) for y in range(16)]


def _wood(x, y):
    if y % 4 == 3:
        return 2  # plank seam
    if (x + y * 5) % 11 == 0:
        return 3  # grain fleck
    return 4


def _leather(x, y):
    if (x * 7 + y * 3) % 13 == 0:
        return 3
    return 4


def _stone(x, y):
    # Cobbles: cells of about 3x3 with darker mortar between them.
    if (x + (y // 3) * 2) % 4 == 0 or y % 3 == 0:
        return 2
    return 3 + ((x // 4 + y // 3) % 2)


def _brushed(highlight):
    def fn(x, y):
        if y in (6, 7) and highlight:
            return 5
        if (x - y) % 6 == 0:
            return 5 if highlight else 3
        return 4
    return fn


def _diamond(x, y):
    # Facets: diagonal bands that read as cut faces.
    return (3, 4, 5, 4)[((x + y) // 2) % 4] if (x - y) % 5 else 3


def _netherite(x, y):
    if (x * 3 + y * 5) % 9 == 0:
        return 5
    return 3 if (x + y) % 5 == 0 else 4


MATERIALS = {
    "wood": _grid(_wood),
    "leather": _grid(_leather),
    "stone": _grid(_stone),
    "iron": _grid(_brushed(False)),
    "gold": _grid(_brushed(True)),
    "diamond": _grid(_diamond),
    "netherite": _grid(_netherite),
}



