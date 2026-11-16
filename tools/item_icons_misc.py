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


ICONS = {
    "small_drekki_carrier": (_pad([
        ".....hhhhhh.....",
        ".....h....h.....",
        "..oooooooooooo..",
        "..oSSSSSSSSSSo..",
        "..oSlwwwwwwlSo..",
        "..oSlwWwwWwlSo..",
        "..oSlwwwwwwlSo..",
        "..oSlwwWwwwlSo..",
        "..oSSSSSSSSSSo..",
        "..oooooooooooo..",
        "..oo........oo..",
    ], 2), {"o": "4a3018", "h": "6a4020", "S": "a07840", "l": "8b5a2b", "w": "e8e4dc", "W": "c8c4bc"}),
    "large_drekki_carrier": (_pad([
        "....hhhhhhhh....",
        "....h......h....",
        ".oooooooooooooo.",
        ".oIIIIIIIIIIIIo.",
        ".oIlrllllllrlIo.",
        ".oIllllllllllIo.",
        ".oIIIIIIIIIIIIo.",
        ".oIllllllllllIo.",
        ".oIlrllllllrlIo.",
        ".oIIIIIIIIIIIIo.",
        ".oooooooooooooo.",
        ".oo..........oo.",
    ], 1), {"o": "2c2c30", "h": "5a3a1e", "I": "c8ccd0", "l": "8b5a2b", "r": "f0f0f0"}),
    "bloodline_tonic": _tonic("d03838", "902020"),
    "heirloom_tonic": _tonic("e8b830", "b08018"),
    "clean_slate_tonic": _tonic("eef4f8", "c0ccd8"),
    "starlight_tonic": _tonic("9a6ae0", "6a40b0", "fff4b0"),
    "purity_tonic": _tonic("50d8e8", "2898b0"),
    "rarity_tonic": _tonic("e050b0", "a02880", "ffffff"),
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
