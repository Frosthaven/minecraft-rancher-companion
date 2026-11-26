"""Screen art for the hatchery and the trough (spec, Style: Screens), drawn as pixel art in the mod's palette.

The layout follows Create's own screens (frogport, stock keeper, factory gauge): a colored header band with the title,
a body of dithered material, sunk wells framed by a bevel, and tab-shaped buttons. Here the band is the style's
leather, the body dark oak, the frame worn steel, the wells dark leather, and the text thread.

Run: uv run --with pillow python -I tools/screen_textures.py
Writes assets/rancher_companion/textures/gui/{hatchery,hatchery_breed,hatchery_sprites,trough,tombstone,drekki,drekki_gear,drekki_sprites}.png (256x256 sheets). The Java screens use the same
numbers (HatcheryScreen, TroughScreen): change both together.
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/rancher_companion/textures/gui"

STEEL = ["2b2e35", "41454e", "5d636e", "8d939e"]
LEATHER = ["2e1a33", "4a2a52", "64396e", "8a5a92"]
WOOD = ["2a1e16", "3b2a1f", "523b2a"]
STRAW = ["7e6430", "b0903f", "d4b665"]
WELL = ["170e1b", "221428", "2e1a33"]
GLOW = "7ff0dc"


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


class Sheet:
    def __init__(self):
        self.im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
        self.px = self.im.load()

    def put(self, x, y, c):
        if 0 <= x < 256 and 0 <= y < 256:
            self.px[x, y] = rgb(c)

    def rect(self, x0, y0, w, h, c):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.put(x, y, c)

    def wood(self, x0, y0, w, h):
        """dark oak planks: boards 6 px high with a dark seam, staggered butt joints, and sparse light grain streaks"""
        for y in range(y0, y0 + h):
            board, row = (y - y0) // 6, (y - y0) % 6
            for x in range(x0, x0 + w):
                c = WOOD[1]
                if row == 5 or (x - x0 + board * 29) % 47 == 0:
                    c = WOOD[0]
                elif row in (1, 3) and ((x - x0) * 3 + board * 17) % 23 < 4:
                    c = WOOD[2]
                self.put(x, y, c)

    def leather(self, x0, y0, w, h):
        """the style's leather: base with a checker dither of the crease shade, a stitch line 2 px in"""
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = LEATHER[1] if (x * 3 + y * 5) % 7 else LEATHER[0]
                self.put(x, y, c)
        for x in range(x0 + 3, x0 + w - 3):
            if x % 3 != 0:
                self.put(x, y0 + 2, LEATHER[3])
                self.put(x, y0 + h - 3, LEATHER[3])
        for x in range(x0, x0 + w):
            self.put(x, y0, LEATHER[2])
            self.put(x, y0 + h - 1, LEATHER[0])

    def frame(self, x0, y0, w, h, rivets=True):
        """a worn steel frame 3 px wide, lit from the top left, riveted at the corners"""
        for i, (a, b) in enumerate(((STEEL[3], STEEL[0]), (STEEL[2], STEEL[1]), (STEEL[1], STEEL[1]))):
            for x in range(x0 + i, x0 + w - i):
                self.put(x, y0 + i, a if i < 2 else STEEL[1])
                self.put(x, y0 + h - 1 - i, b)
            for y in range(y0 + i, y0 + h - i):
                self.put(x0 + i, y, a if i < 2 else STEEL[1])
                self.put(x0 + w - 1 - i, y, b)
        if rivets:
            for cx, cy in ((x0 + 1, y0 + 1), (x0 + w - 3, y0 + 1), (x0 + 1, y0 + h - 3), (x0 + w - 3, y0 + h - 3)):
                self.put(cx, cy, STEEL[3]); self.put(cx + 1, cy, STEEL[2]); self.put(cx, cy + 1, STEEL[2]); self.put(cx + 1, cy + 1, STEEL[0])

    def well(self, x0, y0, w, h):
        """a sunk well: dark rim on top and left, light rim on bottom and right (the inverse of a raised panel)"""
        self.rect(x0, y0, w, h, WELL[1])
        for x in range(x0, x0 + w):
            self.put(x, y0, WELL[0]); self.put(x, y0 + h - 1, LEATHER[2])
        for y in range(y0, y0 + h):
            self.put(x0, y, WELL[0]); self.put(x0 + w - 1, y, LEATHER[2])
        for y in range(y0 + 1, y0 + h - 1):  # a faint dither so a large well is not flat
            for x in range(x0 + 1, x0 + w - 1):
                if (x + y * 2) % 9 == 0:
                    self.put(x, y, WELL[2])

    def slot(self, x0, y0):
        """an 18 px item slot: a sunk dark square, vanilla's size"""
        self.rect(x0, y0, 18, 18, WELL[0])
        for x in range(x0, x0 + 18):
            self.put(x, y0, "0e0811"); self.put(x, y0 + 17, LEATHER[2])
        for y in range(y0, y0 + 18):
            self.put(x0, y, "0e0811"); self.put(x0 + 17, y, LEATHER[2])
        self.rect(x0 + 1, y0 + 1, 16, 16, WELL[1])

    def straw(self, x0, y0, w, h):
        """a heap of straw: short strands in the three straw shades"""
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                n = (x * 5 + y * 3 + (x * y) % 7) % 6
                self.put(x, y, STRAW[n % 3] if n < 5 else WOOD[2])

    def button(self, x0, y0, w, h, face, top, side):
        """a raised button: the face color, a highlight on top and left, a dark base, a steel outline"""
        self.rect(x0, y0, w, h, STEEL[0])
        self.rect(x0 + 1, y0 + 1, w - 2, h - 2, face)
        for x in range(x0 + 1, x0 + w - 1):
            self.put(x, y0 + 1, top); self.put(x, y0 + h - 2, side)
        for y in range(y0 + 1, y0 + h - 1):
            self.put(x0 + 1, y, top); self.put(x0 + w - 2, y, side)

    def save(self, name):
        OUT.mkdir(parents=True, exist_ok=True)
        self.im.save(OUT / f"{name}.png")


# ---- shared sprites (the same place on both sheets) ----
# buttons 60x16 at y 200: normal, hover, disabled, pressed-in (the selected confirm)
BTN_W, BTN_H, BTN_Y = 60, 16, 200


def sprites(s):
    s.button(0, BTN_Y, BTN_W, BTN_H, LEATHER[1], LEATHER[3], LEATHER[0])
    s.button(BTN_W, BTN_Y, BTN_W, BTN_H, LEATHER[2], "a87ab0", LEATHER[1])
    s.button(BTN_W * 2, BTN_Y, BTN_W, BTN_H, STEEL[1], STEEL[2], STEEL[0])
    s.button(BTN_W * 3, BTN_Y, BTN_W, BTN_H, "6e2a2a", "a04848", "3e1414")  # release: a dark red
    # a roster row highlight 140x24 at (0, 220), and the scroll knob 6x15 at (144, 220)
    for y in range(220, 244):
        for x in range(0, 140):
            edge = y in (220, 243) or x in (0, 139)
            s.put(x, y, GLOW if edge and (x + y) % 2 == 0 else LEATHER[2] if edge else (LEATHER[1] if (x + y) % 3 else LEATHER[2]))
    s.button(144, 220, 6, 15, STEEL[2], STEEL[3], STEEL[0])


# ---- the hatchery (spec, Checkout: Roster; Breeding: Pairing): 232 wide; the top part 144 high with two tabs (Roster,
# Breeding: one sheet each), the player's inventory below it, vanilla's 176 wide, centered (the drekki screen's part, so
# Dark Mode Everywhere shades its greys alike). HatcheryScreen and HatcheryMenu use the same numbers. ----
HW, HTOP = 232, 144


def inventory(s, W, top):
    """the player's inventory hanging from a top part's bottom edge: its own steel frame, vanilla's 176 wide, centered"""
    IX, H = (W - 176) // 2, top + 92
    py = top - 3
    s.rect(IX, py, 176, H - py, STEEL[1])
    s.frame(IX, py, 176, H - py, rivets=False)
    for x in range(IX + 3, IX + 173):     # the join: one steel rule across, no frame between the parts
        for y in range(py, py + 3):
            s.put(x, y, STEEL[1])
        s.put(x, py, STEEL[0]); s.put(x, py + 1, STEEL[2])
    for r in range(3):
        for c in range(9):
            s.slot(IX + 7 + c * 18, top + 8 + r * 18)
    for c in range(9):
        s.slot(IX + 7 + c * 18, top + 66)


def hatchery_top(s):
    s.wood(0, 0, HW, HTOP)
    s.leather(3, 3, HW - 6, 14)           # header band: the title and the tabs
    s.frame(0, 0, HW, HTOP)
    for x in range(3, HW - 3):            # a steel rule under the band
        s.put(x, 17, STEEL[0]); s.put(x, 18, STEEL[2])


def hatchery():
    s = Sheet()
    hatchery_top(s)
    # the nest well, left: an egg slot on a bed of straw
    s.well(9, 22, 62, 98)
    s.straw(27, 49, 26, 14)               # a bed of straw a little wider than the egg's slot (Shane, 2026-11-26)
    s.slot(31, 39)
    # the roster well, right: four rows of 21 px and a scroll track
    s.well(77, 22, 146, 98)
    for y in range(24, 118):
        s.put(219, y, WELL[0]); s.put(220, y, WELL[0])
    inventory(s, HW, HTOP)
    s.save("hatchery")


def hatchery_breed():
    s = Sheet()
    hatchery_top(s)
    s.well(9, 22, 54, 72)                 # the two parents, side by side
    s.well(67, 22, 54, 72)
    s.slot(9, 100)                        # the tonic
    s.well(125, 22, 98, 98)               # the child's preview, or the parent picker (its scroll track drawn while it scrolls)
    inventory(s, HW, HTOP)
    s.save("hatchery_breed")


# the child preview's "?" (a coat its owner never had): 28 x 28 at QMARK on hatchery_sprites, the screens' own parts: a sunk
# well in a worn steel frame and the question mark in the glow teal of unknown and magic states, with a dark drop shadow
QMARK = (156, 220)
Q_GLYPH = ['..GGGGGG..',
           '.GHHHHHHG.',
           'GHH....GHG',
           'GG.....GHG',
           '.......GHG',
           '......GHG.',
           '.....GHG..',
           '....GHG...',
           '....GHG...',
           '....GGG...',
           '..........',
           '....GHG...',
           '....GGG...']


def qmark(s, x0, y0):
    s.rect(x0, y0, 28, 28, STEEL[1])                     # the steel frame, lit from the top left
    for i in range(28):
        s.put(x0 + i, y0, STEEL[3]); s.put(x0, y0 + i, STEEL[3])
        s.put(x0 + i, y0 + 27, STEEL[0]); s.put(x0 + 27, y0 + i, STEEL[0])
    s.put(x0 + 27, y0, STEEL[2]); s.put(x0, y0 + 27, STEEL[2])
    s.well(x0 + 2, y0 + 2, 24, 24)                       # the sunk well
    gx, gy = x0 + 9, y0 + 7
    pal = {'G': "3f9e92", 'H': GLOW}
    for y, row in enumerate(Q_GLYPH):                    # the shadow, one down and right
        for x, ch in enumerate(row):
            if ch != '.':
                s.put(gx + x + 1, gy + y + 1, WELL[0])
    for y, row in enumerate(Q_GLYPH):
        for x, ch in enumerate(row):
            if ch != '.':
                s.put(gx + x, gy + y, pal[ch])


def hatchery_sprites():
    s = Sheet()
    sprites(s)
    qmark(s, *QMARK)
    s.save("hatchery_sprites")


# ---- the trough: 176 x 166, vanilla's container size, the player's inventory below ----
def trough():
    s = Sheet()
    W, H = 176, 166
    s.wood(0, 0, W, H)
    s.leather(3, 3, W - 6, 14)
    s.frame(0, 0, W, H)
    for x in range(3, W - 3):
        s.put(x, 17, STEEL[0]); s.put(x, 18, STEEL[2])
    s.well(36, 22, 5 * 18 + 14, 46)      # the food slots' trough: the screen draws a slot per trough of the line
    # the player's inventory: a steel panel with slot wells (vanilla layout, 3 rows + hotbar)
    s.rect(3, 73, W - 6, 90, STEEL[1])
    for x in range(3, W - 3):
        s.put(x, 73, STEEL[0]); s.put(x, 74, STEEL[2])
    for r in range(3):
        for c in range(9):
            s.slot(7 + c * 18, 83 + r * 18)
    for c in range(9):
        s.slot(7 + c * 18, 141)
    sprites(s)
    s.save("trough")


# ---- the tombstone (spec, Tombstone; the way back): the trough's frame, the gear's well (hat, barding, bag, saddle; a
# Drekki Lotus Blossom goes in a free one) and the date under it, room for the blossom's meter or Reclaim (drawn by the
# screen), then the player's inventory. TombstoneMenu and TombstoneScreen use the same numbers. 176 x 200 ----
TOMB_TOP = 108


def tombstone():
    s = Sheet()
    W = 176
    s.wood(0, 0, W, TOMB_TOP)
    s.leather(3, 3, W - 6, 14)
    s.frame(0, 0, W, TOMB_TOP)
    for x in range(3, W - 3):
        s.put(x, 17, STEEL[0]); s.put(x, 18, STEEL[2])
    s.well(46, 24, 4 * 18 + 14, 32)      # the gear's well (TombstoneMenu SLOT_X 53 = 52 + 1)
    for i in range(4):
        s.slot(52 + i * 18, 30)
    # the blossom's meter and Reclaim are drawn by the screen, on the wood, only while they apply
    inventory(s, W, TOMB_TOP)
    s.save("tombstone")


# ---- the drekki (spec, Drekki screen): the hat slot, the model, the details or traits, the stance at the foot of the
# details (stage 2 on), the barding and accessory slots under the hat (stage 2 on), the player's inventory below, centered. Two sheets: drekki
# (stage 1, 220 x 220) and drekki_gear (stage 2 on, its barding and accessory slots); the tabs, buttons and icons on drekki_sprites. The top is wider than
# a container (Minecraft's font needs the room for trait names); the inventory keeps vanilla's 176, centered.
# DrekkiMenu and DrekkiScreen use the same numbers.
STANCE_ICONS = {  # the design page's STANCE_ICON grids, 8 x 8
    "aggressive": (['......SS', '.....SWS', '....SWS.', '.B.SWS..', '..BWS...', '..HB....', '.H..B...', 'H.......'],
                   {'S': '8d939e', 'W': 'e0e4ea', 'B': '7a4a2a', 'H': '523b2a'}),
    "reactive": (['.KKKKKK.', 'KSSSSSSK', 'KSRRRRSK', 'KSRSSRSK', 'KSRSSRSK', '.KSRRSK.', '..KSSK..', '...KK...'],
                 {'K': '2b2e35', 'S': '8d939e', 'R': 'b8352b'}),
    "passive": (['P.......', 'PWWWW...', 'PWWWWW..', 'PWWWW...', 'P.......', 'P.......', 'P.......', 'P.......'],
                {'P': '7a4a2a', 'W': 'ece6d8'}),
}
MOVE_ICONS = {  # 8 x 8, after the stance icons: Stay (a paw planted), Follow (footsteps going on), Recall from job
    "stay": (['.P.PP.P.', '.P.PP.P.', '........', '..PPPP..', '.PPPPPP.', '.PPPPPP.', '..PPPP..', 'KKKKKKKK'],
             {'P': 'e0d2b0', 'K': '523b2a'}),
    "follow": (['........', 'W..W....', '.W..W...', '..W..W..', '..W..W..', '.W..W...', 'W..W....', '........'],
               {'W': 'e0e4ea'}),
    "recall": (['..W.....', '.WW.....', 'WWWWWW..', '.WW...W.', '..W...W.', '......W.', '..WWWW..', '........'],
               {'W': 'e0e4ea'}),  # Recall from job: an arrow turning back (0.4.13)
}
BUFF_ICONS = {  # 9 x 9: Recently pet (a heart with a hand's warmth), Kinship (two drekki heads side by side), Soulflame (a cyan soul flame)
    "pet": (['.RR...RR.', 'RPPR.RPPR', 'RPWPRPPPR', 'RPPPPPPPR', '.RPPPPPR.', '..RPPPR..', '...RPR...', '....R....', '.........'],
            {'R': '6e1e2e', 'P': 'e0607a', 'W': 'ffd0d8'}),
    "kin": (['.........', '.HH...HH.', 'HAAH.HAAH', 'HAEH.HAEH', 'HAAAHAAAH', '.HAAHAAH.', '..HH.HH..', '.........', '.........'],
            {'H': '2b2e35', 'A': 'c9a45a', 'E': '7ff0dc'}),
    "soulflame": (['....F....', '...FC....', '...FCF...', '..FCCF.F.', '..FCWCFF.', '.FCWWCCF.', '.FCWWWCF.', '..FCCCF..', '...FFF...'],
                  {'F': '1e6e78', 'C': '40e0e8', 'W': 'd8ffff'}),
}
# drekki_sprites (64 x 80):
TAB_W, TAB_H, SX = 40, 12, 0                    # tabs: normal y 0, selected y 12, hover y 24
STANCE_BTN, STANCE_Y = 14, 40                   # buttons at y 40: normal, (unused: selected is a green border in code), hover; icons 8x8 at y 56: three stances, Stay, Follow, Recall from job
ICON_Y, BUFF_Y = 56, 66                         # buff icons 9x9 at y 66: Recently pet, Kinship


def pixels(s, x0, y0, rows, pal):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                s.put(x0 + x, y0 + y, pal[ch])


def drekki_sprites(s):
    s.button(SX, 0, TAB_W, TAB_H, LEATHER[1], LEATHER[3], LEATHER[0])
    s.button(SX, TAB_H, TAB_W, TAB_H, LEATHER[2], "a87ab0", "2e1a33")
    s.button(SX, TAB_H * 2, TAB_W, TAB_H, LEATHER[2], LEATHER[3], LEATHER[1])
    for i, (face, top, side) in enumerate([(LEATHER[1], LEATHER[3], LEATHER[0]), (LEATHER[2], "a87ab0", "2e1a33"), (LEATHER[2], LEATHER[3], LEATHER[1])]):
        s.button(SX + i * STANCE_BTN, STANCE_Y, STANCE_BTN, STANCE_BTN, face, top, side)
    for i, (rows, pal) in enumerate(STANCE_ICONS.values()):
        pixels(s, SX + i * 8, ICON_Y, rows, pal)
    for i, (rows, pal) in enumerate(MOVE_ICONS.values()):
        pixels(s, SX + (3 + i) * 8, ICON_Y, rows, pal)
    for i, (rows, pal) in enumerate(BUFF_ICONS.values()):
        pixels(s, SX + i * 10, BUFF_Y, rows, pal)


def drekki(gear, saddle=False):
    """two parts, as Sophisticated Backpacks steps its screen: the drekki's part on top, 220 wide (hat, from stage 2 the
    barding and accessory slots under it, model, info), and the player's inventory below it, vanilla's 176 wide,
    centered; clear beside it. A drekki carries no pack: its storage is the bag in its accessory slot (spec, Accessory)"""
    s = Sheet()
    W, IX = 220, 22                       # IX: where the 176-wide inventory part starts
    top = 128                             # the top part's height; the inventory part hangs from its bottom edge
    H = top + 92
    s.wood(0, 0, W, top)
    s.leather(3, 3, W - 6, 14)
    s.frame(0, 0, W, top)
    for x in range(3, W - 3):
        s.put(x, 17, STEEL[0]); s.put(x, 18, STEEL[2])
    s.slot(7, 26)                         # the hat
    s.well(28, 22, 52, 100)               # the model
    s.well(83, 22, 131, 100)              # the details or traits, the stance at its foot
    if gear:
        s.slot(7, 46)                     # the barding
        s.slot(7, 66)                     # the accessory
    if saddle:
        s.slot(7, 86)                     # a Mature's saddle
    # the inventory part: its own steel frame, joined to the top part's bottom edge (the frames overlap there)
    py = top - 3
    s.rect(IX, py, 176, H - py, STEEL[1])
    s.frame(IX, py, 176, H - py, rivets=False)
    for x in range(IX + 3, IX + 173):     # the join: one steel rule across, no frame between the parts
        for y in range(py, py + 3):
            s.put(x, y, STEEL[1])
        s.put(x, py, STEEL[0]); s.put(x, py + 1, STEEL[2])
    for r in range(3):
        for c in range(9):
            s.slot(IX + 7 + c * 18, top + 8 + r * 18)
    for c in range(9):
        s.slot(IX + 7 + c * 18, top + 66)
    s.save("drekki_saddle" if saddle else "drekki_gear" if gear else "drekki")


def drekki_sheet_sprites():
    s = Sheet()
    drekki_sprites(s)
    s.save("drekki_sprites")


if __name__ == "__main__":
    hatchery()
    hatchery_breed()
    hatchery_sprites()
    trough()
    tombstone()
    drekki(False)
    drekki(True)
    drekki(True, True)
    drekki_sheet_sprites()
