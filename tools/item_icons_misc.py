"""Carrier, tonic and egg icon art: one 16x16 grid and palette each."""

B = "................"


def _pad(rows, top):
    """Places the drawn rows at `top` in a 16-row icon."""
    return [B] * top + rows + [B] * (16 - top - len(rows))


# A potion bottle: cork (C), glass (g, G), liquid (L light, D dark, w shine) and sparkles (s, liquid colored when none).
_BOTTLE = _pad([
    "......oooo......",
    "......oCCo......",
    "......ogGo......",
    "......ogGo......",
    ".....ogggGo.....",
    "....ogggggGo....",
    "...oLLLLLLLLo...",
    "...oLwLLsLLLo...",
    "...oLLLLLLLLo...",
    "...oLLsLLLDDo...",
    "...oLLLLLDDDo...",
    "....oDDDDDDo....",
    ".....oooooo.....",
], 1)


def _tonic(light, dark, sparkle=None):
    return _BOTTLE, {"o": "30303a", "C": "a07040", "g": "e8f0f4", "G": "ffffff", "L": light, "D": dark,
                     "w": "ffffff", "s": sparkle or light}


# The Potion of Horizons and the two potions brewed on the way to it (spec, Traits: caps): the tonics' bottle filling up.
# Stopper (S, s), liquid rows 1 to 6 by letter, the tonics' shine (w) and sparkle (k).
def _brew(stopper, liquid):
    rows = _pad([
        "......oooo......",
        "......o" + stopper + "o......",
        "......ogGo......",
        "......ogGo......",
        ".....ogggGo.....",
        "....ogggggGo....",
    ] + liquid + [".....oooooo....."], 1)
    return rows, {"o": "30303a", "C": "a07040", "g": "e8f0f4", "G": "ffffff", "w": "ffffff", "k": "fff4b0",
                  "e": "7df5a8", "E": "17c95c", "d": "0a8a3a", "P": "b48ad0", "Q": "8a62a8", "h": "e6fff4",
                  "T": "2a9a86", "D": "125e52"}


_DISTANCE = _brew("CC", [
    "...oTTTTTTTTo...",
    "...oTwTTkTTTo...",
    "...oTTTTTTTTo...",
    "...oTTkTTTDDo...",
    "...oTTTTTDDDo...",
    "....oDDDDDDo....",
])
_TWILIGHT = _brew("CC", [
    "...oPPPPPPPPo...",
    "...oPwPPkPPQo...",
    "...oPPPPPPQQo...",
    "...oTTkTTTDDo...",
    "...oTTTTTDDDo...",
    "....oDDDDDDo....",
])
_HORIZONS = _brew("eE", [
    "...oPPPPPPPPo...",
    "...oPwPPkPPQo...",
    "...ohhhhhhhho...",
    "...oTTkTTTDDo...",
    "...oTTTTTDDDo...",
    "....oeEEEEdo....",
])

# The Drekki Lotus (spec, Tombstone: the way back): its seeds (soul-dark husks with a cyan glint) and its blossom (layered
# violet petals, pale tips, a gold heart). The four crop stages are in tools/lotus_textures.py.
_LOTUS_SEEDS = (_pad([
    ".....oo.........",
    "....oSSo........",
    "....oSHSo...oo..",
    ".....oSSo..oSSo.",
    "......oo..oSHSo.",
    "...........oSSo.",
    "..oo........oo..",
    ".oSSo...........",
    ".oSHSo..........",
    "..oSSo..........",
    "...oo...........",
], 3), {"o": "1a1420", "S": "3e2f4a", "H": "5fe0e0"})

_LOTUS_BLOSSOM = (_pad([
    ".......oo.......",
    "......oPPo......",
    "..oo..oPLPo.oo..",
    ".oPPo.oPLPooPPo.",
    ".oPLPooPLPoPLPo.",
    "..oPLPoPPPoPLo..",
    "..oPPLPoGoPLPPo.",
    ".ooPPPPGYGPPPPoo",
    "oPPDDPPGGGPDDPPo",
    ".oPDDDPPPPPDDDo.",
    "..ooDDDDDDDDoo..",
    "....oooooooo....",
], 2), {"o": "2a1240", "P": "9b4ff0", "L": "d9b8ff", "D": "5a1fb0", "G": "f2c230", "Y": "fff1b0"})

ICONS = {
    "drekki_lotus_seeds": _LOTUS_SEEDS,
    "drekki_lotus_blossom": _LOTUS_BLOSSOM,
    "potion_of_distance": _DISTANCE,
    "potion_of_twilight": _TWILIGHT,
    "potion_of_horizons": _HORIZONS,
    # nothing: the tinted overlay layer of a potion drawn with one of the three icons above
    "potion_no_overlay": ([B] * 16, {}),
    # the carriers are 3D models now (tools/item_models.py)
    "bloodline_tonic": _tonic("d03838", "902020"),
    "heirloom_tonic": _tonic("e8b830", "b08018"),
    "clean_slate_tonic": _tonic("eef4f8", "c0ccd8"),
    "starlight_tonic": _tonic("9a6ae0", "6a40b0", "fff4b0"),
    "purity_tonic": _tonic("50d8e8", "2898b0"),
    "rarity_tonic": _tonic("e050b0", "a02880", "ffffff"),
    # the Drekki Whistle: a carved bone (b, B shade, w shine) with an iron band (i, I) on a string loop (s)
    "drekki_whistle": ([
        "................",
        "..........sss...",
        ".........s...s..",
        ".........s....s.",
        "........os....s.",
        ".......obbo..s..",
        "......obwbBos...",
        ".....obwbBBo....",
        "....oiibBko.....",
        "...obIibBo......",
        "..obwbIBo.......",
        "..obbBBo........",
        "...oBBo.........",
        "....oo..........",
        "................",
        "................",
    ], {"o": "30303a", "b": "e8e0c8", "B": "b8ac8c", "w": "ffffff", "i": "c8ccd4", "I": "8d939e", "k": "30303a", "s": "c9a45a"}),
    "intriguing_egg": (_pad([
        "......oooo......",
        ".....ohheeo.....",
        "....ohheeeeo....",
        "....oheeeseo....",
        "...oeeeeeeeeo...",
        "...oeseeeteEo...",
        "...oeeeeeeeEo...",
        "...oeteesseEo...",
        "...oeeeeeeeEo...",
        "....oeeseeEo....",
        ".....oeeEEo.....",
        "......oooo......",
    ], 2), {"o": "6a5a40", "e": "f0e6cc", "E": "d4c6a4", "s": "3a9a8a", "t": "8a5a3a", "h": "ffffff"}),
}
