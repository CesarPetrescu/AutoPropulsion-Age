# Verification report: 2026-09-07

## Result and exact scope

**The vehicle foundation builds and passes the executed local simulation, dedicated-server and graphical client/server checks. It is an alpha, not the complete modular-car specification.**

Verified implementation commit: `4ed59ca2f03e29e342c60692a63cd85ecc8c9a4c`.

Primary [CI verification run 34147283568](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/runs/34147283568) completed successfully. Its code includes the corrected Blender runtime geometry, transactional simulation fixes, corrected garage render order, lowered rider position and stricter screenshot harness. Later documentation/evidence commits do not change that implementation.

| Executed check | Environment | Result |
| --- | --- | --- |
| `bash scripts/test-sim.sh` | Local Debian 13, OpenJDK 21.0.11 | **35 baseline + 2 safety checks passed; 0 failed** |
| Same simulation suites | CI Ubuntu 24.04, Temurin Java 21 | **35 + 2 passed** |
| Content validation | Local and CI | **140 definitions, 24 distinct recipe signatures, generated assets passed** |
| Gradle build | CI, Gradle 9.2.1 | **PASS**, including Java compilation and simulation suites |
| Packaged-JAR inspection | CI | **JAR_VALIDATION_PASS** |
| Minecraft dedicated GameTests | CI, Minecraft 1.21.1 / NeoForge 21.1.249 | **All 8 required tests passed** |
| Default graphical client | CI, real client connected to real loopback dedicated server | **PASS; 5 fresh 1280x720 screenshots** |
| Minimal graphical client | Same server, next sequential client | **PASS; 5 fresh 1280x720 screenshots** |
| Blender generation and finalization | CI Blender 4.0.2 / CPU Cycles | **PASS; editable scenes, GLB, mesh and previews produced** |
| Exported hood-clearance check | Local JSON geometry inspection | **0.03 m; 7 mesh groups; 4,232 triangles** |
| Full local Minecraft launch | Local container | **BLOCKED by DNS resolution, not passed** |
| Local Blender installation/run | Local container | **BLOCKED by unavailable package/network route, not passed** |

The twelve Java files compiled in the local simulation/tools test were compared byte-for-byte with the successful CI source artifact. They matched. The fast local run therefore tested the actual committed simulation, not a rewritten demonstration.

## Graphical runtime measurements

| Profile | HUD / engine detail | Peak observed speed | Peak observed RPM | Final stopped speed |
| --- | --- | --- | --- | --- |
| `default` | On / on | 28.079319 m/s, approximately 101.1 km/h | 5970.031 | 0.0 m/s |
| `minimal` | Off / off | 27.749472 m/s, approximately 99.9 km/h | 6048.677 | 0.0 m/s |

These are short smoke-run observations, **not** a top-speed benchmark, a measured 0-100 time, a performance comparison between profiles or a deterministic networking claim. The client/server timeline can vary. Each profile had to mount the vehicle, open the garage through the packet path, click the dyno tab, survive invalid NaN input without losing the session, accelerate, shift and brake. PASS also required the car to remain above the test floor and the passenger eye/head allowance to stay below the roof threshold.

The runner required a zero process exit, a PASS sentinel, the actual seven-group mesh-loaded log, and five new PNGs with the expected dimensions. It removes old sentinels/screenshots before each profile. The ten resulting frames were opened and visually inspected. Header validation alone is not visual approval.

Permanent copies live under [dated runtime evidence](evidence/2026-09-07/runtime/). In particular: [garage](evidence/2026-09-07/runtime/screenshots/autopropulsion-default-02-garage.png), [analytical dyno](evidence/2026-09-07/runtime/screenshots/autopropulsion-default-03-dyno.png), and [driving HUD](evidence/2026-09-07/runtime/screenshots/autopropulsion-default-04-driving-hud.png). These are actual Minecraft captures, not Blender renders or generated illustrations.

## Local-versus-CI decision

The local container had Java 21, javac, Xvfb and FFmpeg. A clone attempt failed with `Could not resolve host: github.com`; the Blender install exited 100 with `Unable to locate package blender`; the corrected Gradle wrapper could not resolve `services.gradle.org`.

Instead of claiming those launches passed, the source artifact was transferred through the connected GitHub artifact API. Simulation and content checks ran locally without network dependencies. CI supplied the complete Blender/NeoForge toolchain and graphical runtime. [ADR 0001](decisions/0001-testing.md) compares local-only, CI-only and hybrid execution and records why the hybrid route was chosen. [Local results](evidence/2026-09-07/local/results.json) and the [local simulation log](evidence/2026-09-07/local/simulation.log) preserve the outcomes.

## Failures found and corrected

| Observation | Correction and evidence |
| --- | --- |
| Blender failed with `Build without OpenImageDenoiser` | Explicit CPU preview rendering with denoising disabled; asset generation then completed |
| Wrapper expected an HTML page as its checksum | Pinned independently verified Gradle distribution checksum; integrity validation kept enabled |
| Gradle 9 expected JUnit tests, although executable regressions passed | Wired `test` and `check` to the real dependency-free regression and safety suites; disabled only irrelevant JUnit discovery |
| Invalid persisted RPM partially changed live speed | Validate the whole state before mutation; reproduced failing locally, then passed the permanent safety regression |
| A replacement removed a tag required by another part | Validate prospective assembly dependencies before replacement; reproduced failing locally, then passed the permanent safety regression |
| Engine cover intersected the closed hood; catalogue preview cropped edges | Reproducible Blender finalizer updates source and runtime geometry, verifies 3 cm clearance and fits all projected bounds within 5.5% margins |
| Garage labels blurred after being drawn | Corrected `Screen.render` / `renderBackground` order; both tabs are now legible in actual client captures |
| First showroom frame still showed Loading terrain | Wait for a render-ready mounted player before advancing the smoke timeline |
| Seated player head protruded through roof | Adjusted rider origin and added an explicit client roof-clearance assertion; rerendered frames checked |
| Flat-world settings and Linux narrator emitted avoidable errors | Added explicit generator layers and installed flite development/runtime libraries in CI |

Useful iteration runs: [initial denoiser failure](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/runs/34132653705), [asset success/checksum failure](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/runs/34144929311), [test-runner mismatch](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/runs/34145629769), [first green runtime with visually discovered defects](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/runs/34145960471), [corrected Blender asset publication](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/runs/34146458685), and [final green runtime](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/runs/34147283568).

The final dedicated smoke-server log and both graphical client logs contain no `ERROR` entries. Vanilla/dev-environment warnings remain, including command ambiguity, development resource URL schemes, missing vanilla goat-horn sounds and an emissive shader sampler warning. Deprecation warnings also remain for event-bus annotations. This is not a claim that every warning in the toolchain has been eliminated.

## Artifact identity

Built mod: `autopropulsion-age-neoforge-1.21.1-0.1.0-alpha.1.jar`.

```text
SHA-256 1445c2dcb70a26e0b268306070c74fccdc4e92bd36892413cbafc6c78f12caaf
```

The CI artifact `autopropulsion-verification-4` contains the JAR, logs, result JSON and screenshots. `autopropulsion-source-verify-4` contains the corresponding source snapshot. Workflow artifacts normally expire after 14 days; selected results, screenshot hashes and frames are copied into this repository so the decision record outlives that retention window. Minecraft binaries, private account data and build caches are not committed.

## What remains unverified or incomplete

The automated client launches use the developer source-set configuration. A **clean installed-JAR client/server acceptance run** is still required before release, even though the distributable JAR was compiled and inspected. CI uses offline-mode **only on 127.0.0.1**, software rendering and null audio. It does not establish licensed online-mode login, real-GPU/shader compatibility, sound quality or public-server security.

The two clients ran sequentially, not as simultaneous driver/passenger/observer sessions. The server used four simulation substeps and default multipliers. Pure-simulation coverage includes substeps 1, 2, 4, 8 and 16 plus disabled fuel/damage cases; this is not every configurable combination. Full world restart/chunk-unload scenarios, all terrain/collision cases, physical key use, all item views, GUI scales, passenger limb/interior alignment and first-person cameras remain on the manual checklist.

The code has 140 catalogue/model entries but only 16 installable definitions in seven flat slots. Full nested in-game assembly, per-wheel suspension, per-part wear, rotary operation, ECU map flashing, measured roller dyno, body customization and production audio/art remain future work. See [Roadmap](roadmap.md); do not treat passing smoke tests as completion of those features.
