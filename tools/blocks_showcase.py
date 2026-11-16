"""Block showcase: screenshots of every block, the trough's rules, the creative tab and the trader's egg, taken by the mod.

Steps:
  world    a fresh run-blocks/saves/blocks: the flat world tools/showcase.py generates (run/world), creative, noon, no mobs
  options  run-blocks/options.txt: no pause on focus loss, 1600x900, GUI scale 3
  launch   ./gradlew runBlockShowcase: straight into the world; the player builds everything through the real placement and
           use code, the camera takes the pictures and the game closes; they are copied to docs/blocks
Run: uv run --with nbtlib python -I tools/blocks_showcase.py [world|options|launch]   (default: all three)
Needs a display for the launch step. To keep it off the desktop, run it inside a headless compositor (see CLAUDE.md).
"""
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import showcase  # noqa: E402  (its world generation and Gradle helpers)

REPO = showcase.REPO
RUN = REPO / "run-blocks"
SAVE = RUN / "saves" / "blocks"
OUT = REPO / "docs" / "blocks"


def world():
    import nbtlib
    from nbtlib.tag import Byte, Compound, Int, Long, String
    if not (showcase.WORLD / "level.dat").exists():
        showcase.generate()
    if SAVE.exists():
        shutil.rmtree(SAVE)
    SAVE.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(showcase.WORLD, SAVE)
    f = nbtlib.load(SAVE / "level.dat")
    d = f["Data"]
    d["LevelName"] = String("blocks")
    d["GameType"] = Int(1)
    d["allowCommands"] = Byte(1)
    d["Difficulty"] = Byte(0)
    d["raining"], d["thundering"] = Byte(0), Byte(0)
    d["Time"], d["DayTime"] = Long(6000), Long(6000)
    rules = d.setdefault("GameRules", Compound())
    for k, v in (("doDaylightCycle", "false"), ("doWeatherCycle", "false"), ("doMobSpawning", "false"), ("doTraderSpawning", "false")):
        rules[k] = String(v)
    f.save(SAVE / "level.dat")
    print("world: run-blocks/saves/blocks rebuilt")


def options():
    RUN.mkdir(exist_ok=True)
    keep = {"pauseOnLostFocus": "false", "renderDistance": "8", "particles": "0", "onboardAccessibility": "false",
            "skipMultiplayerWarning": "true", "joinedFirstServer": "true", "overrideWidth": "1600", "overrideHeight": "900",
            "guiScale": "3", "fov": "0.0", "narrator": "0", "lang": "en_us", "tutorialStep": "none", "showSubtitles": "false"}
    (RUN / "options.txt").write_text("".join(f"{k}:{v}\n" for k, v in keep.items()))
    print("options: written")


def launch():
    shots = RUN / "screenshots"
    if shots.exists():
        for f in shots.glob("blocks_*.png"):
            f.unlink()
    print("launch: the game builds everything, takes its screenshots and closes itself (a few minutes)")
    log = RUN / "blocks_client.log"
    with open(log, "w") as out:
        code = subprocess.call([showcase.GRADLEW, "runBlockShowcase", "--no-daemon", "-q"], cwd=REPO, stdout=out,
                               stderr=subprocess.STDOUT, env=showcase.env(), timeout=1200)
    got = sorted(shots.glob("blocks_*.png")) if shots.exists() else []
    if not got:
        sys.exit(f"launch: no screenshots (exit {code}); see run-blocks/blocks_client.log")
    OUT.mkdir(parents=True, exist_ok=True)
    for f in got:
        shutil.copy2(f, OUT / f.name)
    print(f"launch: {len(got)} screenshots in docs/blocks")


if __name__ == "__main__":
    steps = sys.argv[1:] or ["world", "options", "launch"]
    for s in steps:
        {"world": world, "options": options, "launch": launch}[s]()
