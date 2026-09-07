# Testing and evidence

This page distinguishes executed checks from implementation and planned coverage. Detailed run outcomes are recorded in [the dated report](test-report-2026-09-07.md). Never label a skipped/blocked check as a pass.

## Layers

| Layer | Command / implementation | What it establishes |
| --- | --- | --- |
| Offline simulation | `bash scripts/test-sim.sh` | 35 baseline checks plus two transactional-safety regressions on actual Java source |
| Gradle simulation | `:sim:regressionTest :sim:safetyTest` | Same executable suites through the project build |
| Content | `python3 scripts/validate-content.py --assets` | JSON/catalogue/index/recipe consistency and required generated-file presence |
| Packaging | `./gradlew build` then `python3 scripts/check-jar.py` | Compilation and expected embedded classes/resources in a distributable mod JAR |
| Dedicated GameTests | `./gradlew :mod:runGameTestServer` | Registered catalogue, assembly, persistence, item identity and guarded mutations inside Minecraft |
| Graphical network smoke | `ACCEPT_MINECRAFT_EULA=true python3 scripts/test-runtime.py` | A real development client connects to a loopback dedicated server and uses the control/garage payload path |
| Human/manual acceptance | Checklist below | Behaviour and environments not covered by the automated harness |

GameTests use a disposable directory and require Minecraft EULA acceptance before creating `mod/run/gametest/eula.txt`. CI explicitly provisions this isolated test world. Do not use an existing gameplay world as a test fixture.

## Actual configuration coverage

The pure-simulation suite checks substep counts **1, 2, 4, 8 and 16**, a 4-vs-8 convergence bound, zero fuel consumption, zero damage, configuration limits, 100 repeat runs and a 20,000-tick seeded bounded-state run. It does not iterate every allowed substep or every multiplier combination.

The runtime runner currently uses server defaults (4 substeps, fuel 1, damage 1, guests disabled) and two sequential client profiles: `default` with HUD/detail enabled and `minimal` with them disabled. Both use a low-graphics Xvfb/Mesa software-rendering environment. This is not simultaneous two-player testing, every GPU, every graphics setting, or a full modpack compatibility matrix.

## Smoke assertions

The developer client must mount the test vehicle, receive/open the garage screen, send an invalid NaN input without losing the session, start the engine, shift and accelerate, then brake. The final sentinel requires a garage visit, maximum speed above 5 m/s, maximum RPM above 1500, stopped speed below 0.5 m/s and the car remaining above the test-floor threshold. The Python runner requires both a zero process exit and a PASS sentinel.

Screenshots are evidence for visual review, not substitutes for those assertions. Startup and screenshots also do not prove that every physical key binding behaves correctly. The sound driver is deliberately null: no listening/quality test is implied.

## Evidence locations

`build/reports/` holds environment, build, GameTest and runtime logs. `build/reports/runtime/result.json` holds profile outcomes; its `screenshots/` subdirectory holds captured frames. `.local-test/reports/` and `sim/build/reports/` hold simulation output. CI uploads source and verification artifacts even on failure, normally retained for 14 days. The committed dated report preserves relevant outcomes after artifact expiration.

Normal verification runs from the event checkout with read-only permissions. A build of source-set development launches must still be followed by a clean installed-JAR test before a release is called ready.

## Required manual acceptance before release

Test an installed JAR in a clean client and dedicated-server installation with licensed clients and online-mode authentication. Use at least two simultaneous clients; verify driver/passenger control separation and observer synchronization. Exercise ownership rejection, refuelling and replacement in Survival, actual crafting, resource reload, disconnect/reconnect, world restart, chunk unload and dimensions. Check slopes, slabs, stairs, walls, water, falls, braking on ice and driving at chunk boundaries.

Inspect the garage at small/large GUI scales, every item view, seated player positioning, first/third-person cameras, wheel animation and HUD toggles. Listen to the engine loop on real audio hardware and test a hardware GPU. Measure idle/active vehicle tick cost and collision performance. Record exact modpack versions for compatibility claims; no third-party compatibility is implied by the present smoke tests.
