# Testing and the mod tester

## Evidence levels

A source review, a successful compilation, a simulation unit test, a dedicated GameTest and an actual client play/render test prove different things. Record them separately. A generated screenshot must be opened and inspected; its existence alone does not prove correct UI or geometry.

## JVM checks

`./gradlew :sim:test :sim:selfTest` runs four JUnit groups and the dependency-free assertion harness. The harness currently reports 145 checks, including 100 repeated deterministic trajectories. It covers reference torque/power calibration, race-cam curve effects, fuel/power units, bad-input rejection, forward/reverse/braking, clutch/neutral behavior, temperature/fuel movement, damage suppression, weak versus forged limits, state serialization values, part compatibility and immutable views.

A JVM-only fallback is useful on a host that cannot download mod dependencies:

```bash
mkdir -p /tmp/apa-classes
javac -d /tmp/apa-classes $(find sim/src/main/java tools/src/main/java -name '*.java') \
  sim/src/test/java/com/photonspark/autopropulsion/sim/SimulationChecks.java
java -cp /tmp/apa-classes com.photonspark.autopropulsion.sim.SimulationChecks
```

Do not include `SimulationTest.java` in that fallback without the JUnit dependency.

## Dedicated Minecraft GameTests

Create the disposable GameTest directory/EULA file, then run `./gradlew :mod:runGameTestServer`. The current four tests assert that the catalogue/registries load on a physical dedicated server, default assemblies have required parts and stock limits, NBT retains fuel/parts while parking cars, model studies cannot install, and the collision box covers the full-size body.

A successful run must contain `All 4 required tests passed`, not merely `Started game test server`. CI uses shell `pipefail` so a failing Gradle command cannot be hidden by `tee`. Empty/no-test runs are failures. Runtime GameTest entrypoints are enabled by NeoForge's development namespace setting; they are not an always-running production test loop.

## Actual client/server visual smoke tests

`tools/mod_tester.py` creates a loopback-bound offline development server and launches three actual NeoForge clients under Xvfb/Mesa. It refuses a known ordinary development world directory, uses its own test-world name, and writes logs plus `reports/minecraft/test-report.json`.

Each profile must connect, mount an owner-controlled car, start/accelerate using the real input packet path, move more than five metres according to synchronized world state, open the workshop menu and save at least six screenshots. The screenshots cover parked vehicle, driving HUD, hood, diagnostics, calculated dyno and parts. Fast, Fancy and Fabulous are separate launches. A zero process exit without the completion marker, a reported failure, missing movement or missing screenshots fails the harness.

This is not a two-simultaneous-player convergence test, latency emulator, manual driving-feel assessment, production-GPU benchmark or every possible configuration. Software OpenGL may expose different performance and driver issues than native hardware. Keep those limits visible in reports.

## Blender and data checks

The asset validator checks the entire generated model/data catalogue. Blender runs with `--python-exit-code 1`; a Python exception must fail CI. The entrypoint verifies expected `.blend`, `.glb`, preview and report files. On Debian/Ubuntu, enable `io_scene_gltf2` and install its NumPy dependency. Check the model images after downloading artifacts.

## Manual regression checklist for the next developer

Use a fresh world and test: placement near walls/slabs/stairs; forward/reverse braking; airborne/ice/water behavior; driver dismount/death/disconnect; chunk unload/server restart; two players observing the same car; owner versus non-owner access; malicious NaN/gear/action packets; removing required parts; parent removal while children remain; reload with a missing definition; rapid menu clicks; inventory-full replacement; resource reload; GUI scales and narrow windows; native GPU Fast/Fancy/Fabulous; 20 and 100 vehicles.

Only convert a checklist item to a reported pass after performing it. Performance claims need captured timings and hardware details. Sound checks cannot pass before audio exists.
