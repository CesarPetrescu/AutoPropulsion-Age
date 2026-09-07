# Playable alpha: development and verification

## Build

Requires a Java 21 JDK. The wrapper downloads Gradle 9.2.1, NeoForge 21.1.249 and ModDevGradle 2.0.146 dependencies.

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

On Linux/macOS use `./gradlew`. The installable artifact is `build/libs/autopropulsion-age-0.2.0-alpha.jar`. Its simulation classes are bundled; `sim/build/libs` is not a second mod to install.

```powershell
.\gradlew.bat :sim:test
.\gradlew.bat :sim:dyno
.\gradlew.bat :sim:dyno --args="--family rotor4 --induction turbo --sport"
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
| `src/main/resources/` | Mod metadata, 40 items/recipes, controller block, language, mesh, icons and sound |
| `src/gametest/` | Development-only dedicated GameTests, test track and client integration harness |
| `tools/export_runtime_mesh.py` / `export_engine_runtime.py` | Blender export of the car, seven engine cores and their fitted service/induction hardware |
| `tools/validate_engine_geometry.py` | Exact runtime triangle checks for the hood sweep and selected non-mating engine hardware |
| `tools/build_engine_workbench.py` | Editable derived Blender inspection scenes for all 21 engine layouts |
| `tools/build_engine_gallery.py` | Native screenshot gallery, preview crops and overview |
| `tools/generate_game_resources.py` | Rebuild icons, recipe JSON, translations and the original synthesized engine OGG |

The server advances four 12.5 ms simulation steps per game tick. Input packets identify the car, keys and steering; only its controlling passenger can provide input. The client receives authoritative entity state and interpolates transforms. GUI actions validate ownership, distance, stopped/engine state, slots and inventory on the server. Installed assembly changes consume and return real stacks in Survival.

## Assets

Open `assets/modular_car_kit/sparkmotors_modular.blend` in Blender, then run `tools/export_runtime_mesh.py` through Blender's Python API/MCP. The exporter expects the assembled scene and manifest. It converts Blender `(x,y,z)` to car-local Minecraft `(x,z,-y)` at one meter per block, retaining hinge pivots and wheel centers. The exporter adds the rear valance, scuttle and family-specific engine adapters without changing the original authoring project. `assets/engine_workshop.blend` is an editable derived snapshot of the runtime geometry with 21 open-hood scenes. Rebuild it with `tools/build_engine_workbench.py`; primary authoring rebuilds use the original kit plus the exporter.

The committed mesh contains **460 material/part batches and 106,572 triangles across all included alternatives**, compressed to **1,443,154 bytes**. Those totals include mutually exclusive options; they are not a per-frame visible triangle count. Engine internals are retained for the inspection view; tiny enclosed gearbox internals remain omitted. The APA2 visibility metadata selects a single family, service-part state and induction kit. Moving fans and drive pulleys use synchronized RPM. Colors use material values and a runtime body tint; the mesh is loaded once on resource reload. See [runtime-assets.json](runtime-assets.json).

Resource generation needs Python with Pillow and `ffmpeg` on PATH. The already-generated files are committed, so normal builds need neither Blender, Python nor ffmpeg. The original model ZIP remains the separately delivered authoring kit; its existing checksum is preserved.

## Verified on 2026-09-08

Tested with Java 21.0.11, Minecraft 1.21.1 and NeoForge 21.1.249 on Windows.

| Check | Result / evidence |
|---|---|
| Pure simulation | **13 JUnit tests passed**, including 10,206 engine states and 64,512 ready engine/car/tune acceleration and braking cases; torque, boost, fuel, cooling, grip, reverse and deterministic output checks |
| Dedicated Minecraft server | **13 required GameTests passed**: the original car tests plus hood animation/access, compatibility/ownership, all 42 family/grade/induction driving layouts, service inventory transactions, serialized engine round trips, 13 engine crafting conversions, donor preservation in a placed sedan crate and old-save migration |
| Native Minecraft client | **PASS**: world boot, car render, actual Garage/Paint/Tuner button presses, part/paint/tune synchronization through real packets, engine start, driving, braking and fuel use; all 21 family/induction layouts installed through actual Engine-page buttons and checked for exclusive mesh visibility |
| Visual inspection | Garage, Paint, Tuner, Car controls, driving/cockpit, all 21 engine layouts and seven internal views captured at 1440 × 900; gallery inspected |
| Blender geometry | **21/21 layouts pass** the envelope/clearance matrix and exact selected triangle checks, including five hood positions per layout |
| Release build | `clean build` packages the runtime and simulation; development test classes and track are excluded |

The [screenshots](screenshots) are native Minecraft captures from the integration harness. They are not Blender renders or UI mockups. Test sources are committed so these checks can be rerun. Local worlds, logs, Gradle output and dependency caches are ignored by Git.

These checks establish a playable alpha. They do not establish a full mechanical simulation, compatibility with other mods, or separate-machine multiplayer performance. The full original specification is preserved in [modular-car-requirements.md](modular-car-requirements.md).

The [engine workshop guide](ENGINE-WORKSHOP.md) explains the implemented component model and its limits. [engine-state-matrix.json](engine-state-matrix.json) and [engine-driving-matrix.json](engine-driving-matrix.json) summarize the exhaustive discrete-state checks.
