# Playable alpha: development and verification

## Build

Requires a Java 21 JDK. The wrapper downloads Gradle 9.2.1, NeoForge 21.1.249 and ModDevGradle 2.0.146 dependencies.

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

On Linux/macOS use `./gradlew`. The installable artifact is `build/libs/autopropulsion-age-0.1.0-alpha.jar`. Its simulation classes are bundled; `sim/build/libs` is not a second mod to install.

```powershell
.\gradlew.bat :sim:test
.\gradlew.bat :sim:dyno
.\gradlew.bat -PwithGameTests runGameTestServer
.\gradlew.bat -PwithGameTests runClientSmoke
```

The client smoke harness creates its own Creative flat world under the development `run` directory, hides its GLFW window without using desktop input, operates native GUI buttons, saves screenshots, writes `run/alpha-smoke-result.txt`, and exits. It requires a graphics/audio-capable desktop environment. The dedicated tests use `run/gametest`. Neither harness is packaged in the release JAR. Check the explicit test PASS result and game log as well as Gradle's exit code.

An unusual Windows host can report `sun.nio.ch.PipeImpl` / Unix-domain socket `Invalid argument` during Java startup. On the workstation used for this build, a process-local `-Djdk.net.unixdomain.tmpdir=<nonexistent-directory>` selected Java's built-in TCP fallback. This is a host workaround, not a mod requirement; it is intentionally absent from project configuration.

## Code map

| Location | Responsibility |
|---|---|
| `sim/` | Pure Java engine curve, automatic gearing, longitudinal dynamics, steering, fuel and assembly configuration; JUnit tests and CSV dyno CLI |
| `src/main/.../entity/CarEntity.java` | Server simulation, four wheel contacts, collision movement, ownership, inventory transactions, safe dismount and NBT persistence |
| `src/main/.../net/CarPackets.java` | Driver input, validated garage actions and screen-open payloads |
| `src/main/.../client/` | Native Minecraft garage/HUD, mesh renderer, wheel/panel animation and looping engine sound |
| `src/main/resources/` | Mod metadata, 16 items/recipes, controller block, language, mesh, icons and sound |
| `src/gametest/` | Development-only dedicated GameTests, test track and client integration harness |
| `tools/export_runtime_mesh.py` | Blender export of stock/selected sport geometry to the compact runtime mesh |
| `tools/generate_game_resources.py` | Rebuild icons, recipe JSON, translations and the original synthesized engine OGG |

The server advances four 12.5 ms simulation steps per game tick. Input packets identify the car, keys and steering; only its controlling passenger can provide input. The client receives authoritative entity state and interpolates transforms. GUI actions validate ownership, distance, stopped/engine state, slots and inventory on the server. Installed assembly changes consume and return real stacks in Survival.

## Assets

Open `assets/modular_car_kit/sparkmotors_modular.blend` in Blender, then run `tools/export_runtime_mesh.py` through Blender's Python API/MCP. The exporter expects the assembled scene and manifest. It converts Blender `(x,y,z)` to car-local Minecraft `(x,z,-y)` at one meter per block, retaining hinge pivots and wheel centers. A temporary rear valance closes the runtime shell behind the lamps without modifying the authoring project.

The committed mesh contains **189 material/part batches and 65,520 triangles across all included alternatives**, compressed to **948,300 bytes**. Those totals include mutually exclusive options; they are not a per-frame visible triangle count. Tiny enclosed engine and gearbox internals are omitted. Colors use material values and a runtime body tint; the mesh is loaded once on resource reload. See [runtime-assets.json](runtime-assets.json).

Resource generation needs Python with Pillow and `ffmpeg` on PATH. The already-generated files are committed, so normal builds need neither Blender, Python nor ffmpeg. The original model ZIP remains the separately delivered authoring kit; its existing checksum is preserved.

## Verified on 2026-09-07

Tested with Java 21.0.11, Minecraft 1.21.1 and NeoForge 21.1.249 on Windows.

| Check | Result / evidence |
|---|---|
| Pure simulation | **8 JUnit tests passed**: reference torque, acceleration/fuel/gears, sport-engine effect, reverse/braking, missing assemblies/empty fuel, finite deterministic output, slot encoding, sport-suspension steering effect |
| Dedicated Minecraft server | **5 required GameTests passed**: persistence, real inventory replacement and ownership, fuel/tune/service validation, driving/braking, solid-wall collision with damage |
| Native Minecraft client | **PASS**: world boot, car render, actual Garage/Paint/Tuner button presses, part/paint/tune synchronization through real packets, engine start, driving, braking and fuel use |
| Visual inspection | Garage, Paint, Tuner, Car controls, open panels, driving HUD and first-person cockpit captured and reviewed at 1440 × 900 |
| Release build | `clean build` packages the runtime and simulation; development test classes and track are excluded |

The [screenshots](screenshots) are native Minecraft captures from the integration harness. They are not Blender renders or UI mockups. Test sources are committed so these checks can be rerun. Local worlds, logs, Gradle output and dependency caches are ignored by Git.

These checks establish a playable alpha. They do not establish a full mechanical simulation, compatibility with other mods, or separate-machine multiplayer performance. The full original specification is preserved in [modular-car-requirements.md](modular-car-requirements.md).
