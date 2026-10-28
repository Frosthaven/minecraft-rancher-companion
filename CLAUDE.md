# minecraft-rancher-companion

A standalone NeoForge mod for Minecraft 1.21.1. It must run in plain NeoForge with only its own declared
dependencies; the Haven pack (`../minecraft-haven-pack`) may ship it later, but nothing here may need a Haven mod.

**State:** new repo, local git only. There is no remote: do not create a GitHub repo, add a remote or push until the
user says so. The mod's features are not written down yet: ask the user, do not invent them. Record each decision here.

## Stack

Copy the build from `../minecraft-haven-arachnowhat` (a small standalone mod that builds on this machine), then strip
its specifics (Iron's/GeckoLib deps, the harness flag, its name):

- `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/wrapper/`, `gradlew`, `gradlew.bat`, `.gitattributes`,
  `src/main/templates/META-INF/neoforge.mods.toml` (expanded from `gradle.properties` by `generateModMetadata`).
- ModDevGradle `net.neoforged.moddev` 2.0.148, Gradle 9.2.1 wrapper, Parchment 2024.11.17.
- Java 21 toolchain (`java.toolchain.languageVersion = 21`). The machine's own `java` is 27; the foojay plugin in
  `settings.gradle` downloads JDK 21 for Gradle. Mojang ships Java 21 to players.
- `neo_version=21.1.262`: the Haven pack's NeoForge (its floor, see the pack's `policy.toml`). Building against a newer
  NeoForge than the pack's breaks the mod there.
- Suggested ids (confirm with the user before the first build): `mod_id=rancher_companion`,
  `mod_group_id=dev.frosthaven.ranchercompanion`, `archivesName = 'rancher-companion'`, `mod_license=MIT`.
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

## Release (later, when the user makes the GitHub repo)

Copy `../minecraft-haven-arachnowhat/.github/workflows/release.yml`: every push to main publishes the jar as the rolling
`latest` release and an immutable `v<mod_version>-<sha7>` release; the Haven pack pins one by hash
(`just bump-mod <slug>` in the pack repo). Bump `mod_version` in `gradle.properties` for each release.

## Commits

Author `Frosthaven <shane@thedragon.dev>`, no `Co-Authored-By` trailer. Imperative summary of what changed and why.
