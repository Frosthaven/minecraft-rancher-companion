# minecraft-rancher-companion: Drekki Rancher

A standalone NeoForge mod for Minecraft 1.21.1, display name "Drekki Rancher" (mod id `rancher_companion`, kept so
saved worlds keep their drekki). It must run in plain NeoForge with only its own declared
dependencies; the Haven pack (`../minecraft-haven-pack`) may ship it later, but nothing here may need a Haven mod.

**State:** public on GitHub (`Frosthaven/minecraft-rancher-companion`), in the Haven pack since 0.2.0 (the teaser:
blocks, trough, trader egg; see `spec.md` "Teaser release"). Push to `main` only when the user asks. The design is in `spec.md` (every decision, open questions at the end) and on the design page listed in
`artifacts.md`; keep both in step with any change. Ask the user before inventing a feature.

## Layout (`src/main/java/dev/frosthaven/ranchercompanion`)

- `RancherCompanion`: the entity type, attributes, commands, showcase hooks; hands off to `client/` only on the client.
- `look/`: pure data, no Minecraft types, unit tested. `Dims` (per-stage pixel dimensions), `Lineage` (nine bloodlines
  at four tiers plus twenty-three rare and ultra rare lines: fur, trim, sheen, eye and particle colors), `Astral`, `Diet`, `Anim` (every loop, the
  first stage that can do it, its travel speed), `Palette` (color math), `Gear` (hats, barding tiers and dyes).
- `entity/DrekkiEntity`: synched look (stage, lineage, diet, astral, horn, hat, barding, dye) and a pinned loop; stage sets the hitbox.
- `item/`: `DrekkiItems` registers every item (barding, carriers, hats, tonics, the egg) in display order (`ALL`) plus the
  creative tab; recipes are JSON under `data/rancher_companion/recipe`; barding is dyeable (`#minecraft:dyeable`).
  Item icons come from `tools/item_icons.py` (`uv run --with pillow python -I tools/item_icons.py`).
  Showcase mobs are NoAi; the travelling loops move them down a lane on the server and cut back.
- `client/rig/`: the design page's rig, ported one to one. `Rig` builds the same groups and cubes in the page's space
  (y up, z forward, pixels) with the page's pose helpers (`gait`, `legPose`, `legMix`, `mouth`, `flare`, `lift`,
  `forward`); `RigBake` packs every cube into a generated texture painted in its color with the page's fur grain
  (one draw per drekki, no texture files), bakes vanilla model parts, and paints the eye sheet, a glow texture (eyes, lamps) and a sheen texture drawn additively (`RenderType.eyes`), as the page's emissive;
  `Baked` copies node poses onto the parts each frame (page space to vanilla is a half turn about x: flip y and z,
  flip a rotation's axis the same way, then ZYX Euler). `Hats` and `BardingRig` are the page's hats and barding plates
  (no node scale here, so hats scale their own cubes); `BardingTex` paints each barding material's texture.
- `client/DrekkiAnimations`: the page's loops verbatim, by loop time in seconds. Change a loop on the page first,
  then port the numbers here. `DrekkiParticles` does the page's particle effects per client tick as `DrekkiCube`, the page's unlit see-through cube with its size modes (its own particle render type, position and color only).
- `DrekkiSounds` + `assets/rancher_companion/sounds.json`: the drekki's sound events, over the game's own files
  (trident pierce, bone meal, azalea leaves, armor and chain equip, shield block, panda nose breath, eating) or the
  mod's files below. `client/DrekkiAudio` is the page's sound scores, fired per client
  tick on the same loop phases, at the page's levels (`GAIN` is each rendered file's level before normalizing) and stage
  pitches; the wind and the breath's hiss are looping tickable sounds.
- `assets/rancher_companion/sounds/drekki/*.ogg`: the design page's synthesized cues (steps, whooshes, growls, calls,
  snore, breath bursts, wind and hiss loops), rendered offline from the page's own Web Audio code (Node with
  `node-web-audio-api`, then trimmed, loop-crossfaded, normalized). The page embeds the same files, so the page and the
  game sound alike. To change one, change the synthesizer on the page, re-render, and replace the file in both.
  Game sounds the engine cannot play as the page does (no filters, pitch clamped to 0.5..2, volume to 1) are baked
  there too by `tools/bake_sounds.py <page.html> <out>` from the takes the page embeds: the fox bite per stage (speed,
  tone, jaws aligned), the amethyst chime and a copy two octaves up, the fireball, ignite and explosion an octave up.
  It prints the gains and bite peaks for `SYN_GAIN`/`SAMPLE_PEAK` (page) and `GAIN`/`PEAK` (`DrekkiSounds`). The page
  caps each sound at the game's full volume and scores on 20 ticks a second, so it hears what the game plays.
- `client/DrekkiModels` caches one baked model per look; `DrekkiRenderer` picks it and draws locked eyes full bright.
- `showcase/`: `Layout` (where the showcase puts every row, stand-in and camera shot; shared by both sides),
  `Showcase` (spawns every group, `/drekki showcase`; with the showcase flag it runs on login and walks the camera;
  group C is a Ruin in each hat at every stage, a Ruin adult in each barding tier and a wall of framed items; the feed
  loops eat from a `trough` block shaped like the page's feeder; `night_*` shots are taken at midnight to show the sheen),
  `ShowcaseBuild` (with `-Drancher_companion.buildworld=true` the dedicated server spawns everything, saves and stops),
  `client/ShowcaseClient` (takes a screenshot after each teleport, then closes the game).

## Showcase (screenshots of everything, no hands)

`uv run --with nbtlib python -I tools/showcase.py` builds `run/saves/showcase` (a flat world the dedicated server
generates once), writes `run/options.txt` (no pause on focus loss, far entities, 1600x900), then runs the
`showcase` run config (`./gradlew runShowcase`: `--quickPlaySingleplayer showcase` with
`-Drancher_companion.showcase=true`). The mod spawns group A (every lineage at every stage, one row per family, tiers
along the row) and group B (every loop at every stage that can do it, with a hay feeder and armor-stand stand-ins),
puts the player in spectator, teleports through `Layout.shots()` and takes `run/screenshots/showcase_<shot>.png`
after each; the client then stops the game. The tool copies them to `docs/showcase/`. The showcase flag also writes
every generated texture sheet to `run/debug_textures/<stage>_<line>_<diet>_<astral>_<horn>.png` for inspection. Needs the desktop session
(`WAYLAND_DISPLAY=wayland-1 DISPLAY=:0 XDG_RUNTIME_DIR=/run/user/1000` from an agent shell); about four minutes.
Gradle needs Java 21 to run: the tool points `JAVA_HOME` at the toolchain JDK Gradle downloaded if it is unset.

`tools/showcase.py world spawn` builds the same world headless (no window, no screenshots): the dedicated server
opens `run/saves/showcase` with `./gradlew runShowcaseWorld`, spawns every group, saves and stops. Use it to hand
the world to another machine.

## Stack

The build is copied from `../minecraft-haven-arachnowhat` with its specifics stripped:

- `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/wrapper/`, `gradlew`, `gradlew.bat`, `.gitattributes`,
  `src/main/templates/META-INF/neoforge.mods.toml` (expanded from `gradle.properties` by `generateModMetadata`).
- ModDevGradle `net.neoforged.moddev` 2.0.148, Gradle 9.2.1 wrapper, Parchment 2024.11.17.
- Java 21 toolchain (`java.toolchain.languageVersion = 21`). The machine's own `java` is 27; the foojay plugin in
  `settings.gradle` downloads JDK 21 for Gradle. Mojang ships Java 21 to players.
- `neo_version=21.1.262`: the Haven pack's NeoForge (its floor, see the pack's `policy.toml`). Building against a newer
  NeoForge than the pack's breaks the mod there.
- Ids: `mod_id=rancher_companion`, `mod_group_id=dev.frosthaven.ranchercompanion`, `archivesName = 'drekki-rancher'`,
  `mod_license=MIT` (the suggested ids, used for the first build without confirmation; rename if the user wants).
- Add the `runs` block (as `../minecraft-haven-consolidated/build.gradle` has) plus a GameTest server:

  ```groovy
  runs {
      client { client() }
      server { server(); programArgument '--nogui' }
      gameTestServer { type = "gameTestServer" }
  }
  ```
- JUnit 5 for pure logic, as `../minecraft-haven-compat/build.gradle` sets it up (`junit-bom`, `junit-jupiter`,
  `junit-platform-launcher`).

## Addons: integration with other mods

Integrations with other mods are optional and stay that way, as in arachnowhat's `compat/irons`:

- Compile against the other mod with `compileOnly` only. Modrinth's maven (`maven.modrinth:<slug>:<version id>`) takes
  a **version id**: a version number alone can pick the Fabric build. Put the exact version in a comment.
- Declare it in `neoforge.mods.toml` as `type="optional"`, with `ordering` only when load order matters.
- Keep every class that touches the other mod in `compat/<mod>/`, reached only after `ModList.get().isLoaded("<id>")`.
  A class in the main path that imports it crashes plain NeoForge.
- Mixins into another mod's classes: `@Pseudo`, `require = 0`, in a separate mixin config when there are many.
- To test with the other mod in the dev runs, add it as `localRuntime` (on the dev classpath, not published).
- Test both with the other mod and without it. The run without it is the one that finds the crash.

## Testing, fastest loop first

1. **Unit tests** (`./gradlew test`): put decisions in pure classes without Minecraft types and test those. Tests that
   matter only: no tests of plain text or of design values.
2. **GameTests** (`./gradlew runGameTestServer`): in-world checks for server logic (entities, blocks, AI, saved
   data). Methods are `@GameTest` in a `@GameTestHolder(MOD_ID)` class with structure templates in
   `src/main/resources/data/<mod_id>/structure/`. The task exits non-zero when one fails, so it is a real gate.
3. **Dev runs** (`./gradlew runClient`, `./gradlew runServer`): vanilla, this mod and its `localRuntime` mods, in
   `run/` (git-ignored; `run/eula.txt` needs `eula=true` once). The client can join the dev server at
   `localhost:25565` (offline mode: set `online-mode=false` in `run/server.properties`). Java changes need a restart;
   resources reload with F3+T, data with `/reload`.
4. **In the Haven pack** (before the mod goes into the pack, and for any interaction with pack mods):
   - Never touch the player instances "Haven" or "Haven Snapshot". Copy one to a throwaway Prism instance
     (arachnowhat's `tools/harness.py` copies "Haven Snapshot" to `dev`) and put `build/libs/<jar>` in its `mods/`.
   - Launch: `prismlauncher -l "<instance>" -w "<world>"` (single player) or `-s localhost:25565` (a server). From an
     agent shell, set the desktop session first: `WAYLAND_DISPLAY=wayland-1 DISPLAY=:0 XDG_RUNTIME_DIR=/run/user/1000`.
   - Dedicated-server join test (catches registry and network sync bugs single player hides):
     `../minecraft-haven-consolidated/tools/join_test.py --extra-mod <jar>`; see its CLAUDE.md "Testing a
     dedicated-server join".
   - Pass = the client's `logs/latest.log` has no `Mixin apply ... failed`, no `Loading errors`, no `FATAL`, no crash
     report, and (for a server) the server logs `<player> joined the game`. Stay in the world a minute.
   - Look at the game: `grim <file>.png` takes a screenshot; read the PNG.

### Rules for the test machine (learned the hard way)

- Never send desktop input with xdotool or ydotool: it triggers a COSMIC remote-control permission prompt.
- Close Prism fully (`pkill -x prismlauncher`) before editing `instance.cfg` or `mmc-pack.json`: a running Prism
  writes its own copy back. Quotes in `instance.cfg` are Qt-escaped (`\"$INST_JAVA\"`).
- Close test games by working folder (`/proc/<pid>/cwd` under the test instance), never by a text pattern (a pattern
  once killed the agent's own shell; leftover games once held 46 GB).
- Single player pauses without window focus: set `pauseOnLostFocus:false` in the test instance's `options.txt`.
- A test script must stop when no game starts within a few minutes (a Prism dialog is open): screenshot it and report.
- Tell the user one line before each long step and the result after it. No silent multi-minute runs.
- Tooling must be portable (other developers on Windows, macOS, Linux): no paths from this PC in committed files.
  Machine settings come from environment variables or a git-ignored `local.toml`; the Prism folder defaults per OS
  (Linux `~/.local/share/PrismLauncher`, macOS `~/Library/Application Support/PrismLauncher`, Windows
  `%APPDATA%/PrismLauncher`).

### The user's Mac (Prism instance "Haven", NeoForge 21.1.262)

Reached over Tailscale SSH as `frosthaven@frosthaven-mbp14`; the first connection from a machine prints a
`login.tailscale.com` link the user must open, after which connections just work for a while. The Mac's login shell
is nushell: run remote commands as `ssh host /bin/bash -s <<'EOF' ... EOF`, and stream files with
`tar -cf - . | ssh host "/bin/bash -c 'tar -C DEST -xf -'"`, because `rsync` over that login shell breaks and nushell
rejects bash flags. The Prism folder is `~/Library/Application Support/PrismLauncher`. The same rule as here: never
touch "Haven"; copy it to `instances/dev` (rsync on the Mac, excluding saves, screenshots, logs, crash-reports,
journeymap/data, backups; `sed -i '' 's/^name=.*/name=dev/' instance.cfg`), put the jar in `dev/minecraft/mods/`
and worlds in `dev/minecraft/saves/`. Prism must be closed while the folder is created or it will not see it.

The showcase world was first delivered there on 2026-10-29 as `saves/showcase` ("Drekki showcase"): the world from
`tools/showcase.py` after a run, which holds every drekki and prop, with `level.dat`'s `Player` moved to
(12.5, -56, 13.5) facing north in creative flight, so the player loads in facing the Moss row; the loop rows start
240 blocks further north.
On 2026-11-05 it was replaced with the headless build (`tools/showcase.py world spawn`, `GameType` set to creative)
and the jar became `drekki-rancher-0.1.0.jar`. New rows: hats at z 360, barding at z 380, the item wall at z 400.

## Release

`.github/workflows/release.yml` (from arachnowhat): every push to main publishes the jar as the rolling
`latest` release and an immutable `v<mod_version>-<sha7>` release; the Haven pack pins one by hash
(`just bump-mod drekki-rancher` in the pack repo). Bump `mod_version` in `gradle.properties` for each release.

## Commits

Author `Frosthaven <shane@thedragon.dev>`, no `Co-Authored-By` trailer. Imperative summary of what changed and why.
