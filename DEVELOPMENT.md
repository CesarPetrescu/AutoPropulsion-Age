# Windows development guide

This is the day-to-day guide for AutoPropulsion Age on **Minecraft Java 1.21.1, NeoForge 21.1.249 and Java 21**. Start commands in the repository folder containing `gradlew.bat`, not its parent.

## Tools we use

| Tool | Purpose | Needed for a normal Java build? |
|---|---|---|
| Git; optional GitHub CLI (`gh`) | Changes, branches, pushes, Actions results and releases | Git for source checkout |
| Eclipse Temurin / another Java 21 JDK | Compile the mod and run Minecraft | Yes |
| Included Gradle 9.2.1 wrapper | Downloads pinned build tooling and Minecraft development dependencies | Yes; no global Gradle installation |
| IntelliJ IDEA or VS Code with Java support | Edit Java and attach a debugger | Optional |
| Minecraft development client launched by Gradle | Real rendering, garage, driving and integrated server tests | For client work |
| Blender with its connected MCP service | Edit source models using Blender's Python API without desktop input | Only for model work |
| Blender background CLI | Repeatable exports, geometry validation and renders in a separate process | Only for model work |
| Python 3.13, Pillow and NumPy | Resource generation, catalogs, image galleries and CI validation | Only for assets / release validation |
| ffmpeg / ffprobe | Generate and decode the original mono OGG layers | Only for audio work |
| Go with actionlint | Validate GitHub Actions and embedded shell commands | Only for workflow edits |
| GitHub Actions | Full Linux build, simulation, native client/server, assets and gated JAR release | Runs remotely |

Normal builds use committed game assets. You do **not** need Blender, Python or a manually installed Minecraft instance just to compile the JAR. The runtime mod uses custom Java simulation; it does not require PhysX, Blender or Python on players' computers.

## First checkout and build

Back up player worlds before testing a new alpha. Keep personal worlds outside the disposable development `run/` folder. Inspect the current branch and uncommitted changes before pulling or generating assets.

```powershell
git clone https://github.com/CesarPetrescu/AutoPropulsion-Age.git
Set-Location AutoPropulsion-Age
git status --short
java -version
.\gradlew.bat --version
.\gradlew.bat build
```

Development is integrated on `main`. Feature branches run the same required checks; automatic releases come from successful pushes to `main`. On an existing checkout, preserve local edits and review incoming changes; do not reset or force-push to get a clean build.

The main output is `build/libs/autopropulsion-age-0.5.1-alpha.jar`. The version comes from `gradle.properties`. The JAR under `sim/build/libs/` is a development library, **not** another mod to install. Put exactly one main JAR into a separate NeoForge 1.21.1 instance's `mods` folder for installation testing.

If Java is not 21, set the JDK for the current PowerShell session, adapting the path to your installation:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat --stop
.\gradlew.bat --version
```

Do not change Minecraft, NeoForge, mod IDs or protocol versions casually. Current network protocol **6** requires matching client/server builds. Vehicle save schema **5** adds drivetrain routing and migrates older cars to the RWD road preset; mechanical components retain their separate typed schema.

## Daily edit, run and debug loop

1. Run `git status --short`; identify which files belong to your task.
2. Edit the smallest connected part of the existing simulation, entity, garage or asset pipeline.
3. Run the relevant fast tests; then run native integration tests when behavior crosses the Minecraft boundary.
4. Inspect the actual game for visual and handling work. Check fresh logs, not an older report committed under `docs/`.
5. Build the main JAR, inspect the diff, commit and push to the intended branch. The full CI gate publishes the new JAR only after every job passes.

```powershell
.\gradlew.bat :sim:test
.\gradlew.bat runClient
# Optional debugger: Minecraft waits for a Java debugger on localhost:5005.
.\gradlew.bat runClient --debug-jvm
```

Import the repository as a Gradle project using Java 21. Open the existing project rather than generating a second Minecraft mod. The Gradle client stores development saves, screenshots, configuration and logs under `run/`. To start the client again, close the previous one first; do not launch competing Gradle builds against the same output directory.

For the game's basic loop: create a Creative flat world, take a Sedan Crate from the AutoPropulsion Age tab, place it on open ground, right-click to enter, then **R** starts the engine. **W** applies throttle, **S** is the service brake, **A/D** steer, **Space** is the rear handbrake, **C** disengages the clutch, **Z** selects reverse while stopped, **G** opens the garage, and **Shift** exits. **C + W** free-revs without applying drive torque. Controls can be rebound in Minecraft.

## Handling and drivetrain work

Use **G → Drive** for actual installed routing and wheel contact/slip readings. Stop fully, switch off, exit, and raise the car with the service jack before a layout or differential conversion. Survival needs a service jack and **eight iron ingots per conversion**. The conversion retains every installed component's identity, wear, damage and faults. A repeated current preset is a no-op. A missing or broken gearbox, shaft or differential must be repaired first. Lower the jack before starting.

| Road preset | Driven axles | Axle differential | Behavior |
|---|---|---|---|
| RWD | Rear 100% | Limited slip | Rear-wheel power slip and handbrake rotation; timely lift/countersteer helps recovery |
| FWD | Front 100% | Open | Front tires share steering and propulsion grip; excessive throttle can widen a turn |
| AWD | Front 40% / rear 60% | Limited slip | More contact patches transmit torque, with finite grip and extra driveline loss |

Choose open, limited-slip or locked axle differentials independently of the preset. AWD center torque split adjusts from **20% to 80% front**, in ten-point steps, while parked with the engine off; it is a free adjustment rather than a hardware conversion. Locked differentials use strong bounded coupling and scrub in turns; they are not an exact rigid constraint. Preset selection also restores that preset's differential and split.

On a wide pad, first learn a stock naturally aspirated car at moderate speed. Apply steering progressively. A short Space pull while turning starts rear slip; release it and countersteer promptly. Holding it can spin the car. More power or less road friction breaks traction sooner. Ice, loose surfaces, tire pressure, wear, corner damage and brake condition change the result. AWD has no immunity to sliding. Normal service braking remains separate from the rear handbrake.

### Simulation architecture

- `DriveConfig` persists chassis layout, axle differential and AWD split. Existing service assemblies supply actual clutch, gearbox, shaft and differential capability; the layout is not another healing upgrade tier.
- `VehicleDynamics` integrates body forward/lateral velocity and yaw from tire forces at up to 80 Hz. World momentum does not rotate magically with the car body.
- `WheelDynamics` computes each wheel's contact velocity, steering angle, normal-load approximation, longitudinal/lateral slip, combined friction limit, brake torque and wheel rotation. Longitudinal and cornering forces share a finite contact budget. Loss of contact removes road forces, but a driven or braked wheel can still change rotation in the air.
- `SuspensionPhysics` supplies unilateral spring/damper heave forces: a spring can support the chassis, never pull it down toward a missing road. Gravity acts when rays lose contact.
- `CarEntity` raycasts each wheel in every substep, samples its road surface, applies Minecraft collision movement, and synchronizes authoritative state. Glancing collisions retain the unblocked velocity component.
- Instruments and road audio use total horizontal speed, individual wheel rotation/contact and slip. Wheel wear, temperature and odometer distance include lateral travel. A sliding chassis does not silently become stationary because its forward component is small.
- Physics uses SI units. Body axes are forward, left and positive yaw-left. Minecraft movement transforms these into world coordinates; keyboard A maps to positive left steering.

The combined-slip/load approach follows standard tire-model concepts; see the [PhysX vehicle overview](https://nvidia-omniverse.github.io/PhysX/physx/5.1.1/docs/Vehicles.html) for background. This is an independent bounded Java implementation, not that library or a calibrated commercial tire model.

Current limits: planar yaw/side-slip plus vertical heave; pitch/roll are visual responses. There is no full six-degree-of-freedom rollover or soft-body crash solver. Collision shapes remain Minecraft AABBs, differentials and weight transfer are approximations, and vehicle mass is a fixed baseline with layout-specific balance. Chassis layout conversion does not generate a new set of visible front driveshaft models. Treat these as alpha handling presets, not replicas of measured real vehicles.

## Blender and MCP workflow

Primary editable source: `assets/modular_car_kit/sparkmotors_modular.blend`, together with `parts_manifest.json`. The exporter expects scene **SM_01_Assembled**, named part roots, their `part_id` properties and parent relationships. Preserve identity, material names, wheel centers, hinge pivots and common engine-bay coordinates. Blender meters become Minecraft blocks; export maps `(x,y,z)` to `(x,z,-y)`.

The connected Blender MCP tools let the assistant inspect the active file, scenes and objects, edit through Blender's Python API, and execute export scripts **without mouse/keyboard automation**. Before changes, query the active file and dirty state, inspect the target scene, and save deliberate authoring changes. Do not load another file over unsaved user edits. Use ordinary Blender UI when *you* want to model manually; automation should use MCP or a background process.

For an MCP script execution, use an absolute path and `runpy` so the exporter receives its correct `__file__`:

```python
import runpy
result = runpy.run_path(
    r"C:\path\to\AutoPropulsion-Age\tools\export_runtime_mesh.py"
)["result"]
```

`export_runtime_mesh.py` delegates to `export_engine_runtime.py`, which combines the kit with `build_powertrain_hardware.py` and `build_service_geometry.py`. Those helper scripts rely on the exporter's context; do not launch them as standalone Python programs. The exporter temporarily builds derived geometry and writes the runtime mesh/metadata. It does not save over the original authoring file.

For independent reproducible work, use Blender's background CLI. This workstation has Blender at `T:\Blender\blender.exe`; change the variable elsewhere:

```powershell
$blenderExe = 'T:\Blender\blender.exe'
& $blenderExe --background assets/modular_car_kit/sparkmotors_modular.blend --python-exit-code 1 --python tools/export_runtime_mesh.py
python tools/verify_powertrain_mesh.py
python tools/verify_engine_fit.py
& $blenderExe --background --factory-startup --python-exit-code 1 --python tools/validate_engine_geometry.py
```

The committed runtime mesh is APA2 at `src/main/resources/assets/sparkmotors/models/entity/sedan.mesh.gz`. After export, review geometry/metadata diffs and run the native client to verify selection, pivots, hood clearance, wheel animation and resource reload. A Blender render is not proof that Minecraft renders the same configuration.

`assets/engine_workshop.blend` is a **derived inspection snapshot** with 49 family/induction scenes and 42 isolated hardware scenes. Rebuild it from the runtime mesh using `tools/build_engine_workbench.py` in a separate background Blender process. Do not make it the only copy of new authoring edits: the next rebuild replaces generated inspection data.

```powershell
& $blenderExe --background --factory-startup --python-exit-code 1 --python tools/build_engine_workbench.py
New-Item -ItemType Directory -Force .codex-reference | Out-Null
& $blenderExe --background assets/engine_workshop.blend --python-exit-code 1 --python tools/render_powertrain_hardware.py
python tools/build_hardware_gallery.py
python tools/build_engine_gallery.py
```

The hardware gallery uses Blender renders; the engine gallery uses native Minecraft captures. Keep those sources clearly labeled. The model kit's hundreds of named roots are not all independently simulated service parts.

## Python, resources and sound

Use a project virtual environment; `.venv/` is ignored. Activation is optional, avoiding PowerShell execution-policy changes:

```powershell
py -3.13 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r tools/ci/requirements.txt
.\.venv\Scripts\python.exe tools/generate_game_resources.py
.\.venv\Scripts\python.exe tools/verify_mechanical_audio.py
ffmpeg -version
ffprobe -version
```

Where examples say `python`, use the virtual-environment executable or your selected Python on PATH. Blender scripts use Blender's embedded Python. Do not install `bpy` into the resource virtual environment.

`generate_game_resources.py` owns generated recipes, icons and base resources while preserving authored sound definitions/translations. `generate_mechanical_audio.py` owns the original synthesized mechanical layers. Intentional changes to authored audio must survive resource regeneration. Positional assets are mono OGG; several alternatives in one sound event are not an RPM-layer mixer.

Use `tools/ci/resources.py` to snapshot, regenerate and compare all committed resource semantics/pixels/bytes. It does not approve intentional asset changes for you: generate intended changes first, review them, then run the preservation check on that result. Native audio-channel/cleanup tests establish runtime behavior; listen to acceleration, coast, load changes, compressor transitions and faults for audible quality. OpenAL null-output CI cannot perform subjective listening.

## Tests without taking over the computer

The automated clients hide their own GLFW window and drive native game controls internally. They do not move the desktop cursor, send OS keyboard input or require a Minecraft account. Keep `runClient` for a visible manual playtest and use the harness tasks for background verification. Background rendering can still consume CPU/GPU.

```powershell
.\gradlew.bat :sim:test
.\gradlew.bat -PwithGameTests runGameTestServer
.\gradlew.bat -PwithGameTests runClientHandling
.\gradlew.bat -PwithGameTests runClientMechanics
.\gradlew.bat -PwithGameTests runClientSmoke
.\gradlew.bat -PwithGameTests prepareMultiplayerHarness
python tools/run_multiplayer_test.py --timeout 720
.\gradlew.bat :sim:mechanicsBenchmark
```

| Suite | What it must prove |
|---|---|
| Simulation JUnit | At least 52 tests: existing mechanics plus traction/differentials, finite combined force, steering symmetry, brief handbrake/countersteer, unsupported suspension and world momentum in air |
| Simulation matrices | Existing 7,308 hardware cases plus 294 layout × family × grade × induction drive/brake cases |
| Dedicated GameTests | At least 30 tests, including 294 actual in-world drive/brake builds, conversion transactions, permissions, condition retention, save migration and spring landing |
| Native handling client | RWD/FWD/AWD garage buttons, differential/split packets, real key mapping steering/drift/braking, contact loss and landing; screenshots |
| Native mechanics client | Complete coolant diagnosis/repair/refill/verification, service access, sender behavior, audio channels and cleanup |
| Full native client matrix | 49 family/induction selections and 42 hardware choices, native GUI and rendering checks |
| Two-client dedicated fixture | Ownership, synchronized state, worn-item removal, actual drop/pickup and installation in another owner's car |
| Resource/geometry checks | 424 resources preserved, 48 mono sounds decoded, 294 hardware identities, 49 envelopes and five-position hood checks |

Do not substitute `runClientSmokeQuick` for the full release matrix. The quick task covers seven I4 layouts and all 42 hardware choices, useful while iterating on the UI.

Fresh reports are under `sim/build/test-results/`, `sim/build/reports/`, `build/ci/`, `run/gametest/logs/`, `run/logs/` and `run/screenshots/`. The native client also writes `run/alpha-smoke-result.txt`. Require explicit PASS markers and matching current logs; Gradle success alone or an older screenshot is insufficient. The CI gates reject incomplete coverage. See [CI.md](docs/CI.md) for evidence and publication details.

A reproducible logged Windows run:

```powershell
New-Item -ItemType Directory -Force build/ci | Out-Null
.\gradlew.bat -PwithGameTests runClientHandling *> build/ci/handling.log
if ($LASTEXITCODE -ne 0) { throw 'Minecraft handling run failed; read build/ci/handling.log' }
python tools/ci/checks.py handling --log build/ci/handling.log --report build/ci/handling.json
```

### Optional workaround for this Windows host

If Java fails during socket initialization with `sun.nio.ch.PipeImpl` / Unix-domain socket `Invalid argument`, this workstation can select Java's built-in TCP fallback with a **process-local** property pointing at a nonexistent directory:

```powershell
$env:JDK_JAVA_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$((Get-Location).Path.Replace('\','/'))/.codex-reference/no-unix-sockets"
.\gradlew.bat --stop
.\gradlew.bat :sim:test
# Remove the workaround when no longer needed:
Remove-Item Env:JDK_JAVA_OPTIONS
```

Do not create the `no-unix-sockets` directory. This is not a mod dependency and is deliberately absent from Gradle, CI and global Java settings.

## Commit, CI and get the JAR

```powershell
git diff --check
git status --short
# Stage the reviewed source/assets/docs belonging to the change.
git add <reviewed-paths>
git commit -m "Describe the implemented behavior"
git push origin mechanical-components
gh run list --branch mechanical-components --limit 5
gh run view <run-id>
gh release view --repo CesarPetrescu/AutoPropulsion-Age
```

Every push/PR runs all required jobs. Only the configured release branch may publish after they all pass. The publisher attaches the **same tested artifact**, commit provenance, SHA-256 checksums and fresh test evidence to an immutable build tag. Failed or superseded runs leave the previous Latest release intact. This workflow does not merge branches or enable branch protection.

- [Latest tested JAR](https://github.com/CesarPetrescu/AutoPropulsion-Age/releases/latest/download/autopropulsion-age-latest.jar)
- [Release page and test evidence](https://github.com/CesarPetrescu/AutoPropulsion-Age/releases/latest)
- [Actions](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions)

For gameplay detail see [PLAYING.md](docs/PLAYING.md). For the original engine/resource architecture and historical verification see [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md); use fresh CI artifacts for the current commit's results.

## Workshop sizing, branding and regression tests

`WorkshopScreen` fits a minimum 760 × 460 logical canvas inside the current GUI viewport. It scales rendering, tooltips, scissor regions and mouse/drag/scroll input together, without changing the player's global GUI option. Garage, Service and Drive share this layout boundary. Page controls remain separated from selected-part details and footers. The driving HUD caps its physical scale independently.

Run `.\gradlew.bat -PwithGameTests runClientUi` for the native UI suite. It checks all six garage tabs, engine pages, every component-list page, muffler operations, fluid/tests and Drive: 132 page/scale combinations across 1024 × 600, 1440 × 900 and 1920 × 1080, using explicit and Auto GUI scales. It checks widget bounds, overlapping controls, label widths and actual scaled mouse navigation, and captures screenshots under `run/screenshots/ui-*.png`. Inspect the images as well as the PASS marker: widget bounds alone do not validate text clipping or presentation. The same run opens NeoForge's Mods screen and checks the loaded logo texture.

The canonical [mod logo](src/main/resources/autopropulsion-age.png) is also used by the README. [Branding provenance](docs/BRANDING.md) records its generation prompt. Run `python tools/ci/documentation.py` to validate the full galleries and local links without rebuilding the model ZIP.

Steering uses positive driver-left simulation axes; Minecraft yaw increases to the right. Keep both the yaw sign and lateral/world velocity conversion consistent. Native handling checks assert the world-space direction, not just a positive simulation yaw reading. Forward/reverse is driver-selected while stopped, never inferred from signed road speed. Clutch slip is crank/input-shaft speed difference; torque capacity, dissipated heat and installed clutch condition determine the mechanical result. The Live page displays synchronized measurements, not a decorative gauge.
