# Development and handoff

## Start here

Target Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21. Use the committed Gradle 9.2.1 wrapper. Import the root Gradle project in IntelliJ IDEA or another Java-capable IDE; do not create independent projects for `sim` and `mod`. The current sources are Java, not Kotlin. Python 3 is required by the asset-generation task.

Run `./gradlew :sim:test :sim:selfTest :mod:build`. Then launch `./gradlew :mod:runClient` in a new test world. Dedicated-server launch is `./gradlew :mod:runServer` after explicit EULA acceptance. Never develop against the only copy of a valued world.

## Common commands

| Purpose | Command |
|---|---|
| Pure simulation unit suite | `./gradlew :sim:test :sim:selfTest` |
| Reference CSV | `./gradlew :tools:dyno` |
| Acceleration CSV | `./gradlew :tools:dyno -PdynoArgs="--drive"` |
| Build distributable JAR | `./gradlew :mod:build` |
| Client | `./gradlew :mod:runClient` |
| Dedicated server | `./gradlew :mod:runServer` |
| Dedicated GameTests | `./gradlew :mod:runGameTestServer` |
| Rebuild all runtime assets | `python3 art/generate_assets.py` |
| Validate geometry and data | `python3 tools/validate_assets.py` |
| Authoring scenes and previews | `blender --background --factory-startup --python-exit-code 1 --python art/run_blender.py` |
| Disposable visual client/server matrix | `MC_EULA=true python3 tools/mod_tester.py` |

For GameTests, create `mod/run/gametest/eula.txt` before the first run. Linux visual smoke tests require Xvfb, Mesa and ALSA libraries. The harness uses a disposable loopback-only offline development server and must not be repointed at a production server.

## Where to make a change

Engine equations and state integration belong in `sim/EngineModel` and `sim/VehicleModel`. Part compatibility and modifier ordering belong in `sim/Parts`; do not create a Java subclass for each part. Builtin content lives in `art/catalog.py`, which generates codec-readable datapack JSON. Original geometry lives in `art/geometry.py`. The same indexed meshes are drawn in Minecraft and imported by Blender.

`VehicleEntity` adapts the core to world collision, driver input, ownership, persistence and tracked state. Keep imports of `net.minecraft.client` exclusively under `mod/.../client/`. `InputPayload` accepts controls, not client-computed pose or engine state. `GarageMenu` owns server actions and sampled dyno values; `GarageScreen` only displays them and sends bounded button IDs.

## Next implementation tasks, in order

1. Replace the assisted drivetrain and bicycle steering approximation with an explicitly stable wheel/contact and suspension model. Add deterministic flat-ground, slope, airborne, braking, reverse, wall and slab scenarios first.
2. Add proper seat entities, safe dismount paths and two simultaneous-client convergence tests. Do not call the single-driver smoke run a multiplayer-load test.
3. Extend the garage into a real inventory-backed slot tree with atomic install/remove operations, per-part health, and server-revalidated compatibility. Add datapack synchronization and re-resolution on reload.
4. Expand engine systems using tests for the affected hardware: pump flow limits, AFR/ignition maps, knock, per-part damage, oil starvation, rotary metering and seal wear. Implement a feature before changing its catalogue entry to `implemented: true`.
5. Replace the calculated graph with a measured roller-load dyno, then add datalog export. Follow with body-panel composition and original audio.

These tasks correspond to the original phased plan, not a promise that the full specification already works. See ROADMAP.md for acceptance criteria.

## Definition of done for a contribution

The exact changed revision must build. Simulation changes need a regression test and justified tolerances; gameplay changes need dedicated-server coverage; render/UI changes need actual screenshots and a resource-reload check. Record OS, Java/NeoForge versions, commands, test failures and untested cases. Never mark a workflow green merely because a process started or an artifact directory exists.

Do not commit caches, downloaded Minecraft JARs, user worlds, tokens, or Blender backup files. Generated runtime resources can be committed after regeneration, but the procedural source must remain the authoritative edit point. Keep build/tool versions pinned and document version changes.
