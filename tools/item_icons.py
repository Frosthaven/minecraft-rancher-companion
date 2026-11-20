"""Paints the mod's 16x16 item icons into src/main/resources/assets/rancher_companion/textures/item/.

Run from anywhere: `uv run --with pillow python -I tools/item_icons.py`.

Each icon is a 16-row grid of palette letters ('.' is clear). Barding, bags, carriers and the tombstone are 3D models
(tools/item_models.py); the barding plates' material patterns stay in item_icons_barding.py.
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


hat_art, misc_art = (load_art(n) for n in ("item_icons_hats", "item_icons_misc"))
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


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (rows, palette) in {**HATS, **ICONS}.items():
        paint(rows, palette).save(OUT / f"{name}.png")
    print(f"wrote {len(HATS) + len(ICONS)} icons to {OUT}")


if __name__ == "__main__":
    main()
