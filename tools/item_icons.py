"""Paints the mod's 16x16 item icons into src/main/resources/assets/rancher_companion/textures/item/.

Run from anywhere: `uv run --with pillow python -I tools/item_icons.py`.

Each icon is a 16-row grid of palette letters ('.' is clear). Barding plates are grayscale (layer0, tinted in game by
the dye or the tier's plate color); their straps and buckles are a separate untinted layer (`<id>_straps`).
"""
import importlib.util
from pathlib import Path

from PIL import Image


def load_art(name):
    """Loads a sibling art module by path (`python -I` leaves the script folder off sys.path)."""
    spec = importlib.util.spec_from_file_location(name, Path(__file__).resolve().parent / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


barding_art, hat_art, misc_art = (load_art(n) for n in ("item_icons_barding", "item_icons_hats", "item_icons_misc"))
BARDING_SHAPE, BARDING_STRAPS, MATERIALS = barding_art.SHAPE, barding_art.STRAPS, barding_art.MATERIALS
HATS, ICONS = hat_art.HATS, misc_art.ICONS

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/rancher_companion/textures/item"


def rgba(hex_color):
    if len(hex_color) == 6:
        hex_color += "ff"
    return tuple(int(hex_color[i:i + 2], 16) for i in range(0, 8, 2))


def paint(rows, palette):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), rows
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), rgba(palette[ch]))
    return img


def barding_plate(material):
    """The plate silhouette, its inside replaced by the material's 16x16 grayscale pattern."""
    rows = []
    for y, row in enumerate(BARDING_SHAPE):
        rows.append("".join(material[y][x] if ch == "#" else ch for x, ch in enumerate(row)))
    return rows


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    gray = {"o": "3c3c3c", "1": "707070", "2": "9a9a9a", "3": "c4c4c4", "4": "e2e2e2", "5": "ffffff"}
    for tier, material in MATERIALS.items():
        paint(barding_plate(material), gray).save(OUT / f"{tier}_barding.png")
        paint(BARDING_STRAPS, {"s": "5a3a1e", "t": "7a5030", "b": "d8b040"}).save(OUT / f"{tier}_barding_straps.png")
    for name, (rows, palette) in {**HATS, **ICONS}.items():
        paint(rows, palette).save(OUT / f"{name}.png")
    print(f"wrote {len(MATERIALS) * 2 + len(HATS) + len(ICONS)} icons to {OUT}")


if __name__ == "__main__":
    main()
