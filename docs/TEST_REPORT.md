# Verification report — foundation

Date: 2026-09-07. This document records observed results, not the full roadmap acceptance. Exact subsequent run results remain authoritative in GitHub Actions.

## Verified so far

| Check | Observed result | Evidence |
|---|---|---|
| Local Java 21 standalone checks | 145 checks passed | Executed `SimulationChecks` using javac/java without Minecraft dependencies |
| Reference torque | 179.642 Nm at 4,200 RPM | Actual JVM calibration output |
| Reference peak power | 109.020 kW | Actual JVM calibration output |
| Reference CLI acceleration | 8.15 s, 0–100 km/h | Actual `Dyno --drive` stderr and CSV; not an in-game measurement |
| JUnit | Four groups, no failures | Core workflow and XML reports |
| Generated data/geometry | 119 meshes, 136 definitions, 18 installable | Asset validator report |
| NeoForge compilation | Passed for server milestone `6148ff4` | Minecraft workflow run 34144566389 |
| Physical dedicated GameTest server | All four required tests passed | Run 34144566389, Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21.0.12 |

## Failures found and fixed or under verification

The first Gradle run evaluated `mod` before `sim` and failed on `sourceSets`; explicit project evaluation fixed it. A braking regression initially allowed only five seconds to stop from the 60 m/s speed cap; the corrected physical test permits eight seconds without changing braking strength. The immutable-view assertion now tests the precise operation rather than accidentally treating the whole test group as an expected exception.

An initial Blender run saved a scene but had no enabled glTF export operator. Blender returned zero despite a script error. The entrypoint now enables the exporter, uses `--python-exit-code 1`, and checks all output files. This correctly exposed a second distribution issue: the exporter requires NumPy. Complete authoring-bundle validation and rendered client results must be recorded after successful reruns, not inferred from those earlier green steps.

## Not established by these results

Full P1–P9 acceptance; measured in-game versus CLI acceleration agreement; simultaneous multi-client convergence; full suspension; arbitrary config combinations; Windows/macOS/native GPU behavior; shaders or third-party modpacks; fleet performance; complete survival progression; sound quality; every part's gameplay. Model studies are not included in the installable count.

The source, mod JAR, test logs, CSVs, `.blend`/GLB outputs and screenshots should be delivered from successful runs and labelled by commit. Do not present an early server-only JAR as the final client-tested artifact.
