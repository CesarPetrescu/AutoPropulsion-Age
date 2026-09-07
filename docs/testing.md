# Testing and evidence

This page distinguishes executed checks from implementation and planned coverage. Exact outcomes and source hashes are in [the dated report](test-report-2026-09-07.md). Never label a skipped or blocked check as a pass.

## Layers

| Layer | Command / implementation | What it establishes |
| --- | --- | --- |
| Offline simulation | `bash scripts/test-sim.sh` | 35 baseline checks plus two transactional-safety regressions on actual Java source |
| Gradle simulation | `:sim:regressionTest :sim:safetyTest` | Same executable suites through the project build |
| Content | `python3 scripts/validate-content.py --assets` | JSON/catalogue/index/recipe consistency and required generated-file presence |
| Packaging | `./gradlew build` then `python3 scripts/check-jar.py` | Compilation and expected embedded classes/resources in a distributable mod JAR |
| Dedicated GameTests | `./gradlew :mod:runGameTestServer` | Eight world checks covering catalogue, assembly, persistence, item identity and guarded mutations |
| Graphical network smoke | `ACCEPT_MINECRAFT_EULA=true python3 scripts/test-runtime.py` | Real developer client connects to a loopback dedicated server and exercises control/garage payloads and both GUI tabs |
| Manual acceptance | Checklist below | Behaviour and environments not covered by the automated harness |

GameTests use a disposable directory and require Minecraft EULA acceptance before creating `mod/run/gametest/eula.txt`. CI explicitly provisions this isolated world. Do not use an existing gameplay world as a test fixture.

## Actual configuration coverage

The pure-simulation suite checks substep counts **1, 2, 4, 8 and 16**, a 4-vs-8 convergence bound, zero fuel consumption, zero damage, configuration limits, 100 repeat runs and a 20,000-tick seeded bounded-state run. It does not iterate every allowed substep or multiplier combination.

The runtime runner uses server defaults (4 substeps, fuel 1, damage 1, guests disabled) and two sequential client profiles: `default` with HUD/detail enabled and `minimal` with them disabled. Both use low-graphics Xvfb/Mesa software rendering. This is not simultaneous two-player testing, every GPU/graphics setting or a full modpack compatibility matrix.

## Smoke assertions and captures

The client waits for a mounted player with no loading screen before advancing its test timeline. It must open the garage, click the analytical dyno tab, send invalid NaN input without losing the session, start the engine, shift and accelerate, then brake. The final sentinel requires a garage visit, maximum speed above 5 m/s, RPM above 1500, stopped speed below 0.5 m/s, the car above the test floor and a passenger eye/head allowance below the roof threshold.

The Python runner requires a zero exit and PASS sentinel, the actual mesh-loaded message, and five **fresh** 1280x720 PNG captures per profile: showroom, garage, dyno, driving/HUD and braking. Missing/empty images, wrong dimensions or invalid PNG headers fail the test. Old sentinels and profile screenshots are deleted before each launch. PNG header validation does not establish that the GUI is legible; the frames must also be opened and reviewed.

The last braking screenshot is taken during deceleration, before the final stopped-speed assertion. A nonzero speed visible in that screenshot does not contradict the subsequent zero-speed result. Startup and scripted payloads do not prove all physical key bindings work. Audio output is deliberately null: no listening/quality test is implied.

## Evidence locations and publication

`build/reports/` holds environment, build, GameTest and runtime logs. `build/reports/runtime/result.json` holds profile outcomes; `screenshots/` holds frames. `.local-test/reports/` and `sim/build/reports/` hold simulation results. CI uploads source and verification artifacts even on failure, normally for 14 days.

Selected verified output is copied unchanged to `docs/evidence/2026-09-07/`, including screenshot hashes and artifact identity. The explicit evidence-publication workflow checks the source commit and successful result before committing only to the foundation feature branch. Normal build/runtime/asset verification remains read-only. No workflow merges the PR.

A source-set development launch still needs a clean installed-JAR acceptance run before a release is called ready.

## Required manual acceptance before release

Test an installed JAR in a clean client and dedicated-server installation with licensed clients and online-mode authentication. Use at least two simultaneous clients; verify driver/passenger control separation and observer synchronization. Exercise ownership rejection, refuelling/replacement in Survival, actual crafting, resource reload, disconnect/reconnect, world restart, chunk unload and dimensions. Check slopes, slabs, stairs, walls, water, falls, braking on ice and chunk boundaries.

Inspect the garage at small/large GUI scales, every item view, passenger limb/interior alignment, first/third-person cameras, wheel animation and HUD toggles. Listen on real audio hardware and test a hardware GPU. Measure idle/active vehicle tick cost and collision performance. Record exact modpack versions for compatibility claims; no third-party compatibility is implied by these smoke tests.
