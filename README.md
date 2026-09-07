# AutoPropulsion Age

**Build a car from parts. Understand what makes it run. Find out which component fails first.**

A modular vehicle mod in development for **Minecraft Java Edition 1.21.1**, **NeoForge 21.1.x** and **Java 21**. The project separates a deterministic, Minecraft-free vehicle simulation from its game integration and original Blender asset pipeline.

> **Development alpha, not the complete design specification.** This branch contains the first playable foundation. Eighteen part definitions have prototype gameplay; 118 additional component-family items are explicitly labelled **model studies**, not functioning upgrades. Read the status table before assuming a planned system is implemented.

## What is in this milestone?

| Area | Available in the foundation | Deliberate limitation |
|---|---|---|
| Vehicle | Original Hatch 01, driver seat, forward/reverse gears, steering, braking, clutch input | One driver; assisted clutch launch; no passenger-seat entities |
| Simulation | Fixed-step JVM core, torque curves, turbo lag, fuel use, coolant/oil temperatures, structural wear | Calibrated lumped model, not combustion chemistry or full rigid-body dynamics |
| Parts | Tag-based nested slots, compatibility reasons, weakest-link limits, replace/remove transactions | Only 18 definitions installable; no per-cylinder inventory or independent per-part health yet |
| Engines | Reference 2.0 L inline-four; prototype two-rotor definition; stock/race cam curves | Rotary audio, oil metering, seal-specific wear and complete engine assembly are roadmap work |
| Workshop | Garage lift and dyno-console blocks; live diagnostics; part-removal page; ECU limiter adjustment | Install parts by right-clicking the stopped car; no drag-and-drop inventory tree |
| Dyno | Server-calculated WOT torque/power sweep displayed as a graph; matching standalone CLI | Not a measured, strapped-down roller test |
| Networking | Server-owned simulation, driver/owner validation, finite/range checks, one input accepted per tick, input watchdog | Vanilla tracked state and pose interpolation; prediction and optimized delta packets deferred |
| Persistence | Owner, installed definitions, fuel, temperatures, health and odometer; missing definitions preserved | Loaded cars are intentionally parked with the engine off |
| Collision | Full-length conservative rotating-body AABB, vanilla swept movement, four ground-material probes | Probes select grip, not a complete spring/damper suspension solver |
| Rendering | Original low-poly body/interior/engine bay; animated wheels/hood; workshop block models | Prototype materials; opaque glass; no replaceable body panels, paint or livery system |
| Assets | 119 runtime meshes, generated inventory renders, editable Blender scenes and GLB export pipeline | Families share some small accessory geometry; not manufacturer-accurate or final art |
| Audio | None in this milestone | No engine loops, horn or sampled audio banks yet |

## Target and toolchain

| Component | Pinned version |
|---|---|
| Minecraft | **1.21.1** only |
| Loader | **NeoForge 21.1.249**; metadata permits compatible 21.1.x versions at or above this pin |
| Java | **21** |
| Gradle wrapper | **9.2.1** |
| ModDevGradle | **2.0.146** |
| Parchment | **1.21.1 / 2024.11.17** |
| Mod id | `autopropulsion` |
| Namespace/package | `com.photonspark.autopropulsion` |

The current implementation is Java-first. Kotlin, Forge, Fabric, Bedrock and other Minecraft versions are **not** supported by this build. NeoForge is required on both client and dedicated server. No Valkyrien Skies, Create, Electrical Age or Kotlin runtime is required.

## Build and run

Install a Java 21 JDK and Python 3, then use the included wrapper:

```bash
./gradlew :sim:test :sim:selfTest :mod:build
./gradlew :mod:runClient
```

On Windows, use `gradlew.bat`. The mod JAR is written to `mod/build/libs/`. Python regenerates original assets during the build; Blender is required only to rebuild authoring scenes and preview renders, not to compile or play the mod.

For a dedicated development server, review the Minecraft EULA and create `mod/run/server/eula.txt` with `eula=true`, then run:

```bash
./gradlew :mod:runServer
```

Do not use the disposable CI server configuration for a public server. It deliberately binds to loopback and disables authentication for development clients. Normal multiplayer installations should keep online authentication enabled.

## First drive

Create a **new Creative test world**. Open the AutoPropulsion Age creative tab and take a **Hatch 01 Assembly Kit**, **Garage Wrench**, **Diagnostic Laptop**, and desired installable parts. Place the kit in a clear area at least five blocks long and three wide. The kit creates a preassembled reference car, rather than demanding the entire future assembly progression.

Right-click the car to enter. Press **E** to start, **R** to select first gear, then **W** to accelerate. **S** brakes, **A/D** steer, **R/F** change gear, **Left Control** disengages the clutch, and **Space** applies the handbrake. **Left Shift** dismounts. **K** toggles the hood while driving. Vanilla **F5** changes camera mode. All custom keys can be rebound.

With the engine stopped, right-click the owned car with an installable part to replace its preferred slot; the replaced item is returned in Survival. Use the laptop or wrench to open diagnostics, the calculated dyno graph and the parts page. Shift-right-click toggles the hood. A Fuel Can adds 10 L and is consumed outside Creative. To remove a parent assembly, remove its children first. A standalone ECU permits limiter adjustment while the engine is off; stock hardware still constrains the permitted RPM.

Operators can use `/apa spawn` and `/apa status`. These are development conveniences, not an economy or vehicle-ownership administration system.

## Simulation and asset tools

```bash
# Reference engine CSV
./gradlew :tools:dyno
# Race cam or rotary comparison
./gradlew :tools:dyno -PdynoArgs="--engine ref_i4_2.0 --race-cam"
./gradlew :tools:dyno -PdynoArgs="--engine ref_rotary_2"
# Straight-line reference acceleration, not an in-game benchmark
./gradlew :tools:dyno -PdynoArgs="--drive"
# Original runtime meshes, icons, data and validation
python3 art/generate_assets.py
python3 tools/validate_assets.py
# Editable .blend scenes, .glb exports and preview images
blender --background --factory-startup --python-exit-code 1 --python art/run_blender.py
```

Blender outputs are under `art/generated/` and are provided as CI artifacts rather than committing large binary revisions. The runtime meshes and generated part data are versioned. See [the asset pipeline](docs/ASSET_PIPELINE.md) before editing generated files.

## Tests and evidence

Two GitHub Actions workflows separate JVM/asset checks from Minecraft integration checks. The mod tester builds the JAR, starts a dedicated GameTest server, and provides an actual client/server smoke harness with screenshots. A workflow definition is not proof that every test passed: consult [the test report](docs/TEST_REPORT.md), the exact commit's workflow results, and attached logs.

The reference JVM run measured **179.642 Nm peak torque**, **109.020 kW peak power**, and **8.15 s to 100 km/h** under its documented simplified acceleration model. These are simulation calibration results, not claims about a real car or an in-game performance match.

## Project map

```text
sim/       Pure Java simulation, stat/part rules and deterministic tests
mod/       NeoForge registries, vehicle, payloads, persistence, menus and rendering
tools/     CSV dyno, asset validator and actual Minecraft smoke-test supervisor
art/       Original procedural geometry, part catalogue and Blender authoring pipeline
docs/      Requirements, architecture, formulas, limitations and developer handoff
.github/   Build/test workflows and contribution templates
```

Start with [Development](docs/DEVELOPMENT.md), [Architecture](docs/ARCHITECTURE.md), [Requirements](docs/REQUIREMENTS.md), and [Roadmap](docs/ROADMAP.md). Content authors should read [Parts](docs/PARTS.md), [Asset catalogue](docs/ASSET_CATALOG.md) and [Add-on guide](docs/ADDON_GUIDE.md). Operators and testers should read [Configuration](docs/CONFIGURATION.md), [Testing](docs/TESTING.md) and [Security](SECURITY.md).

## Licensing

A project-wide open-source license has **not** been selected. See [LICENSE.md](LICENSE.md). Public source visibility is not a claim that the project or Minecraft assets are freely redistributable under an open-source license. The generated car and part studies use original procedural geometry and no downloaded manufacturer models.
