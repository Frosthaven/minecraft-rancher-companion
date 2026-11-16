"""Showcase: a flat singleplayer world "showcase" in run/saves with every lineage at every stage and every loop at
every stage that can do it, screenshotted by the mod itself.

Steps (each can be run alone):
  world    a fresh run/saves/showcase (spectator, noon, no mobs) from a flat world the dedicated server generates once
  options  run/options.txt: no pause on focus loss, 16 chunks, far entities, 1600x900 window
  spawn    headless instead of launch: the dedicated server opens run/saves/showcase, spawns every group, saves and
           stops (./gradlew runShowcaseWorld); no window, no screenshots. Use it to hand the world to another machine
  launch   ./gradlew runShowcase: straight into the world; the mod spawns the groups, walks the camera through the
           shots and closes the game; screenshots land in run/screenshots and are copied to docs/showcase
Run: uv run --with nbtlib python -I tools/showcase.py [world|options|spawn|launch]   (default: world, options, launch)
Needs a desktop for the launch step (the game opens a window). Portable: paths come from the repo, nothing per machine.
"""
import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
RUN = REPO / "run"
WORLD = RUN / "world"
SAVE = RUN / "saves" / "showcase"
OUT = REPO / "docs" / "showcase"
JDK = Path.home() / ".gradle/jdks"
GRADLEW = str(REPO / ("gradlew.bat" if os.name == "nt" else "gradlew"))


def env():
    """Gradle itself needs Java 21; use the toolchain JDK Gradle downloaded if JAVA_HOME is not set."""
    e = dict(os.environ)
    if "JAVA_HOME" not in e and JDK.exists():
        for d in sorted(JDK.iterdir()):
            if d.is_dir() and "21" in d.name and (d / "bin").exists():
                e["JAVA_HOME"] = str(d)
                break
    return e


def gradle(task, log, wait=True):
    f = open(log, "w")
    p = subprocess.Popen([GRADLEW, task, "--no-daemon", "-q"], cwd=REPO, stdout=f, stderr=subprocess.STDOUT, env=env())
    return p.wait() if wait else p


def game_pids():
    """Java processes whose working folder is run/: the game, never anything else."""
    out = []
    for pid in os.listdir("/proc"):
        if not pid.isdigit():
            continue
        try:
            if Path(f"/proc/{pid}/cwd").resolve() == RUN.resolve():
                out.append(int(pid))
        except (OSError, RuntimeError):
            pass
    return out


def kill_game():
    for pid in game_pids():
        try:
            os.kill(pid, 15)
        except OSError:
            pass


def world():
    """A fresh run/saves/showcase from the server's flat world, generated once."""
    import nbtlib
    from nbtlib.tag import Byte, Compound, Int, Long, String
    if SAVE.exists():
        shutil.rmtree(SAVE)
    if (WORLD / "level.dat").exists():
        print("world: reusing the generated flat world")
    else:
        generate()
    SAVE.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(WORLD, SAVE)
    f = nbtlib.load(SAVE / "level.dat")
    d = f["Data"]
    d["LevelName"] = String("showcase")
    d["GameType"] = Int(3)
    d["allowCommands"] = Byte(1)
    d["Difficulty"] = Byte(0)
    d["raining"], d["thundering"] = Byte(0), Byte(0)
    d["Time"], d["DayTime"] = Long(6000), Long(6000)
    d["SpawnX"], d["SpawnY"], d["SpawnZ"] = Int(12), Int(-59), Int(20)
    rules = d.setdefault("GameRules", Compound())
    for k, v in (("doDaylightCycle", "false"), ("doWeatherCycle", "false"), ("doMobSpawning", "false"), ("doTraderSpawning", "false")):
        rules[k] = String(v)
    f.save(SAVE / "level.dat")
    print("world: run/saves/showcase rebuilt")


def generate():
    RUN.mkdir(exist_ok=True)
    (RUN / "eula.txt").write_text("eula=true\n")
    layers = {"biome": "minecraft:plains", "lakes": False, "features": False,
              "layers": [{"block": "minecraft:bedrock", "height": 1}, {"block": "minecraft:dirt", "height": 2}, {"block": "minecraft:grass_block", "height": 1}]}
    (RUN / "server.properties").write_text(
        "level-type=minecraft\\:flat\nlevel-name=world\nonline-mode=false\ngamemode=creative\ndifficulty=peaceful\n"
        "spawn-monsters=false\nspawn-animals=false\nspawn-npcs=false\ngenerate-structures=false\nview-distance=4\nmax-tick-time=-1\n"
        f"generator-settings={json.dumps(layers).replace(':', '\\:').replace('=', '\\=')}\n")
    if WORLD.exists():
        shutil.rmtree(WORLD)
    print("world: generating with the dedicated server (a minute or two)")
    log = RUN / "showcase_server.log"
    p = gradle("runServer", log, wait=False)
    t0 = time.time()
    done = False
    while time.time() - t0 < 600 and p.poll() is None:
        time.sleep(3)
        if (WORLD / "level.dat").exists() and "Done (" in log.read_text(errors="replace"):
            done = True
            break
    kill_game()
    p.wait(timeout=120)
    if not done:
        sys.exit("world: the server did not finish; see run/showcase_server.log")


def options():
    RUN.mkdir(exist_ok=True)
    keep = {}
    p = RUN / "options.txt"
    if p.exists():
        for line in p.read_text().splitlines():
            if ":" in line:
                k, v = line.split(":", 1)
                keep[k] = v
    keep.update({"pauseOnLostFocus": "false", "renderDistance": "16", "entityDistanceScaling": "5.0", "particles": "0",
                 "onboardAccessibility": "false", "skipMultiplayerWarning": "true", "joinedFirstServer": "true",
                 "overrideWidth": "1600", "overrideHeight": "900", "guiScale": "2", "fov": "0.0", "narrator": "0", "lang": "en_us",
                 "tutorialStep": "none", "showSubtitles": "false"})
    p.write_text("".join(f"{k}:{v}\n" for k, v in keep.items()))
    print("options: written")


def spawn():
    """The dedicated server, pointed at the showcase save, spawns the groups and stops itself."""
    props = RUN / "server.properties"
    text = props.read_text()
    props.write_text("\n".join("level-name=saves/showcase" if l.startswith("level-name=") else l for l in text.splitlines()) + "\n")
    log = RUN / "showcase_world.log"
    print("spawn: the dedicated server builds the showcase into run/saves/showcase (a minute or two)")
    try:
        p = gradle("runShowcaseWorld", log, wait=False)
        try:
            p.wait(timeout=600)
        except subprocess.TimeoutExpired:
            kill_game()
            p.wait(timeout=120)
            sys.exit("spawn: the server did not stop on its own; see run/showcase_world.log")
    finally:
        props.write_text(text)
    out = log.read_text(errors="replace")
    if "showcase build: saved" not in out:
        sys.exit("spawn: the server did not save the showcase; see run/showcase_world.log")
    print("spawn: " + next(l for l in out.splitlines() if "showcase build: spawned" in l).split("showcase build: ")[-1])


def launch():
    shots = RUN / "screenshots"
    if shots.exists():
        for f in shots.glob("showcase_*.png"):
            f.unlink()
    print("launch: starting the game; it takes its own screenshots and closes itself (about four minutes)")
    log = RUN / "showcase_client.log"
    p = gradle("runShowcase", log, wait=False)
    t0 = time.time()
    while time.time() - t0 < 900 and p.poll() is None:
        time.sleep(5)
    timed_out = p.poll() is None
    if timed_out:
        kill_game()
        p.wait(timeout=120)
    got = sorted(shots.glob("showcase_*.png")) if shots.exists() else []
    if OUT.exists():
        for f in OUT.glob("*.png"):
            f.unlink()
    OUT.mkdir(parents=True, exist_ok=True)
    for f in got:
        shutil.copy(f, OUT / f.name.replace("showcase_", ""))
    print(f"launch: {len(got)} screenshots, copied to docs/showcase")
    if timed_out or not got:
        sys.exit("launch: the game did not finish on its own; see run/showcase_client.log and run/logs/latest.log")


if __name__ == "__main__":
    steps = sys.argv[1:] or ["world", "options", "launch"]
    for s in steps:
        {"world": world, "options": options, "spawn": spawn, "launch": launch}[s]()
