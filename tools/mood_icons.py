"""Paints the mood icons a drekki shows over its head (design page, UI tab: 12x12, read by color and shape at a
distance) into src/main/resources/assets/rancher_companion/textures/entity/mood/.

Run from anywhere: `uv run --with pillow python -I tools/mood_icons.py`. The grids and palette are the page's
MOOD_ICON and MOOD_PAL, copied one to one.
"""
from pathlib import Path

from PIL import Image

ICONS = {
    "uneasy": ['.KKKKKKKKKK.', 'KAAAAAAAAAAK', 'KAAAAAAAAAAK', 'KAAKKAAKKAAK', 'KAAKKAAKKAAK', 'KAAAAAAAAAAK',
               'KAAAKKKKAAAK', 'KAAKAAAAKAAK', 'KAAAAAAAAAAK', '.KKKKKKKKKK.', '....KKK.....', '....KK......'],
    "miserable": ['.KKKKKKKKKK.', 'KMMMMMMMMMMK', 'KMMMWWMMMMMK', 'KMMWWWWWWMMK', 'KMWWWWWWWWMK', 'KMGGGGGGGGMK',
                  'KMMBMMBMMBMK', 'KMBMMBMMBMMK', 'KMMMMMMMMMMK', '.KKKKKKKKKK.', '....KKK.....', '....KK......'],
}
PALETTE = {'K': '#15161a', 'A': '#f0b040', 'M': '#7a2030', 'W': '#ece8e0', 'G': '#a8a4a0', 'B': '#6ac0ff'}

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/rancher_companion/textures/entity/mood"


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, rows in ICONS.items():
        img = Image.new("RGBA", (12, 12), (0, 0, 0, 0))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in PALETTE:
                    h = PALETTE[ch]
                    img.putpixel((x, y), (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255))
        img.save(OUT / f"{name}.png")
        print("wrote", OUT / f"{name}.png")


if __name__ == "__main__":
    main()
