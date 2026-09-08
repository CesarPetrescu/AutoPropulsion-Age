# Playable alpha: development and verification

## Build

Requires a Java 21 JDK. The wrapper downloads Gradle 9.2.1, NeoForge 21.1.249 and ModDevGradle 2.0.146 dependencies.

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

On Linux/macOS use `./gradlew`. The installable artifact is `build/libs/autopropulsion-age-0.4.0-alpha.jar`. Its simulation classes are bundled; `sim/build/libs` is not a second mod to install.

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
| `src/main/.../client/` | Native Minecraft garage/HUD, mesh renderer, wheel/panel animation and layered component-driven audio and cockpit instruments |
| `src/main/resources/` | Mod metadata, 122 items/recipes, controller block, language, mesh, icons and sound |
| `src/gametest/` | Development-only dedicated GameTests, test track and client integration harness |
| `tools/export_runtime_mesh.py` / `export_engine_runtime.py` | Blender export of the car, seven engine cores and their fitted service/induction hardware |
| `tools/validate_engine_geometry.py` | Exact runtime triangle checks for the hood sweep and selected non-mating engine hardware |
| `tools/build_engine_workbench.py` | Editable derived Blender inspection scenes for 49 engine layouts and 42 hardware models |
| `tools/build_engine_gallery.py` | Native screenshot gallery, preview crops and overview |
| `tools/generate_game_resources.py` | Rebuild icons, recipe JSON, translations and the original synthesized engine OGG |

The server advances four 12.5 ms simulation steps per game tick. Input packets identify the car, keys and steering; only its controlling passenger can provide input. The client receives authoritative entity state and interpolates transforms. GUI actions validate ownership, distance, stopped/engine state, slots and inventory on the server. Installed assembly changes consume and return real stacks in Survival.

## Assets

Open `assets/modular_car_kit/sparkmotors_modular.blend` in Blender, then run `tools/export_runtime_mesh.py` through Blender's Python API/MCP. The exporter expects the assembled scene and manifest. It converts Blender `(x,y,z)` to car-local Minecraft `(x,z,-y)` at one meter per block, retaining hinge pivots and wheel centers. The exporter adds the rear valance, scuttle and family-specific engine adapters without changing the original authoring project. `assets/engine_workshop.blend` is an editable derived snapshot of the runtime geometry with 49 open-hood scenes and 42 isolated part scenes. Rebuild it with `tools/build_engine_workbench.py`; primary authoring rebuilds use the original kit plus the exporter.

The committed APA2 mesh contains **734 material/part batches and 268,980 triangles across all alternatives**, compressed to **3,237,722 bytes**. Most alternatives are mutually exclusive, and the in-world engine bay is culled when the hood is closed. Each selected hardware choice has distinct vertex positions, verified for every family. Runtime metadata selects family, service slot/variant and compressor. Body paint is tinted at draw time. Mesh data is cached on resource reload. See [runtime-assets.json](runtime-assets.json).

Run `tools/build_engine_workbench.py` through Blender MCP to create `assets/engine_workshop.blend` with 91 editable scenes. `tools/render_powertrain_hardware.py` renders the 42 isolated models. `tools/verify_powertrain_mesh.py` checks all 294 family/hardware geometry identities. `tools/build_hardware_gallery.py` creates the README hardware gallery; `tools/build_engine_gallery.py` uses the native Minecraft captures. The primary authoring source is the original kit plus `tools/build_powertrain_hardware.py` and `tools/export_engine_runtime.py`.

Resource generation needs Python with Pillow and `ffmpeg` on PATH. The already-generated files are committed, so normal builds need neither Blender, Python nor ffmpeg. The original model ZIP remains the separately delivered authoring kit; its existing checksum is preserved.

## Verified on 2026-09-08

Tests use Java 21.0.11, Minecraft 1.21.1 and NeoForge 21.1.249. [verification.json](verification.json) records exact results and the release checksum. The [workshop guide](ENGINE-WORKSHOP.md#reproducible-tests) explains the matrix coverage. Test sources are committed; worlds, build output, dependencies and local logs are ignored.

The pure-Java suite covers exhaustive populated hardware compatibility, migration encodings, all slot-option pairs, 7,308 stateful drive/brake cases, turbo lag/BOV, wastegate limits, supercharger shaft load, flywheel inertia, throttle response, mixture/wear, cooling, oil pressure, limiter, reverse, grip and deterministic output. Native server tests cover the inventory/persistence/driver integration and all 98 engine layouts. The full client harness covers all 49 family/induction layouts and all 42 hardware choices; `runClientSmokeQuick` repeats the seven I4 layouts, all 42 hardware choices and the final live rev/tuning flows.

The simulation catalog can be exported with `java -cp sim/build/classes/java/main com.photonspark.sparkmotors.sim.PartCatalog > docs/powertrain-catalog.json` after `:sim:classes`. Resource generation reads that catalog. Re-run `tools/generate_game_resources.py` after hardware renders to refresh unique item icons. Normal builds use committed generated resources and do not require Blender/Python/ffmpeg.

```powershell
.\gradlew.bat :sim:dyno --args="--family v6 --induction twin-turbo --part intake=3 --boost 1.1"
.\gradlew.bat :sim:dyno --args="--family i4 --induction large-turbo --transient"
.\gradlew.bat -PwithGameTests runClientSmokeQuick
```

The dyno shares the hardware curves and physics with the game. `--transient` outputs a ten-second stateful run: seven seconds of acceleration followed by throttle lift and braking. CSV includes RPM, road speed, boost, turbo speed, throttle, torque, AFR, oil readings, engine wear and blower load. Every CLI hardware selection is validated before the run.

Vehicle schema 4 adds typed mechanical state while retaining the ten three-bit hardware slots; legacy six two-bit slots migrate without changing old choices. Engine item schema 2 uses the same mapping. Protocol 4 requires matching client/server mod versions. Individual wear, damage, faults, quantities, pressure/charge and temperatures persist, while moving/rotating transient state resets on load. Simulation classes are included in the mod output and JAR; dedicated test/harness classes and the test track are excluded from releases.

The native screenshots are actual Minecraft captures. The isolated hardware images are Blender renders of the exact game geometry. These checks establish the implemented alpha behavior; other modpacks, separate-machine multiplayer, a full combustion solver and the remaining original specification are future work.

## Connected mechanics and M6 reproduction

GitHub Actions now runs these suites on Linux and gates downloadable JAR releases on every required result. See [CI coverage, artifacts and release policy](CI.md). The portable `python tools/run_multiplayer_test.py` launcher adds readiness checks, explicit pass validation, timeouts and guaranteed cleanup to the original PowerShell launch helper.

```powershell
.\gradlew.bat -PwithGameTests runClientMechanics
.\gradlew.bat :sim:mechanicsBenchmark
.\gradlew.bat -PwithGameTests prepareMultiplayerHarness
.\tools\run_multiplayer_test.ps1
python tools/verify_mechanical_audio.py
```

The multiplayer script starts a hidden dedicated server and two native clients on loopback port 25576. It writes the fixture server's EULA acceptance and settings in its isolated `run/multi-server` directory, uses a unique flat world and writes logs/result files under `.codex-reference/multiplayer-*`. It exports only development launch descriptors under ignored build output. A successful result requires both real clients to acknowledge synchronized state after Survival removal, native drop/pickup and installation into the second owner's car. The processes exit themselves. This tests actual networking on one machine, not remote latency or hostile modpacks.

`MechanicalState`/`PartInstance` are immutable and `MechanicalData` registers their versioned NeoForge data component and codecs. `CircuitPhysics`, `WheelDynamics` and `MechanicalCapabilities` feed the existing engine/transmission. The server updates fluids/wear at 20 Hz, advances dynamics in four substeps, and sends a full mechanical snapshot once per second and immediately after service. Scalar operating values and wheel states also synchronize. The native size test records serialized NBT; the simulation benchmark excludes Minecraft overhead. See [mechanics-performance.json](mechanics-performance.json) and [mechanics-network-size.json](mechanics-network-size.json).

`generate_game_resources.py` preserves existing authored sounds and translations; `generate_mechanical_audio.py` owns original synthesized layers. The validator decodes all 48 mono OGG files and proves regeneration preserves definitions/hashes. [Audio validation](audio-validation.json) is technical evidence, not listening acceptance.

Current tests, milestone coverage, approximations and the next unfinished step are recorded in [mechanical-milestones.md](mechanical-milestones.md). Do not count the original 471 model roots as individually simulated service parts.
