# Parallel hybrid and lag investigation — 2026-09-08

This run started from `ad3d527640c4eed1d5ebd086a8e4103a9d7d6566` on main. Measurements below were taken on the Windows development machine with Java 21.0.11, Minecraft 1.21.1 and NeoForge 21.1.249. The modified build is 0.8.0-alpha. These are fresh results from this task, not inherited audit claims.

## Simulation measurements

The same `PowertrainBenchmark` source was compiled against the unmodified simulation classes for the baseline, then run against the changed classes. Each entry is a 20-car AWD batch, four physics substeps, 200 warm-up ticks and 600 measured ticks. The workload mixes throttle, coasting, braking and small steering inputs. Hybrids now follow a different physical trajectory because the mechanical engine path exists. This comparison measures the task workload, not an isolated instruction benchmark or FPS.

| Driving fleet | Mean before → after (ms/tick, 20 cars) | Allocated bytes per car tick before → after |
|---|---:|---:|
| Combustion | 0.8845 → 0.8201 | 144,009 → 144,222 |
| Hybrid | 4.2304 → 1.6448 | 534,989 → 232,077 |
| Plug-in hybrid | 4.0972 → 1.3046 | 529,638 → 201,831 |
| Electric 400 V | 3.6417 → 0.6677 | 435,494 → 112,879 |
| Electric 800 V | 3.6211 → 0.6640 | 435,494 → 112,879 |

The benchmark excludes Minecraft world/collision queries, networking, audio and rendering. Desktop load varies; the final comparison was rerun after the Blender export/render work ended. Full parked/driving samples and p95 timings are retained in [before](parallel-hybrid/performance-before.json) and [after](parallel-hybrid/performance-after.json). The reduction in allocation explains a plausible reduction in GC pressure; no GC pause-duration claim is made.

## Native rendering measurement

The graphics fixture rendered the same car scene across Fabulous startup and native Video Settings transitions to Fast, Fancy, Fabulous and Fast. CPU vertex-submission measurements:

| Mesh selection | Samples | Mean CPU ms/car draw | p95 ms | Mean visible triangles |
|---|---:|---:|---:|---:|
| Full world geometry, visibility cache already enabled | 265 | 11.8448 | 17.8604 | 55,004 |
| World LOD | 269 | 5.8990 | 9.0333 | 34,274 |

This measures the car renderer's CPU submission, not GPU execution, total frame time or end-user FPS. Both tests ran on the same desktop, with background dedicated-server tests. Workshop previews retain full detail; the total kit contains alternatives, so its 320,168 triangles are not rendered on every car. [Raw render measurements](parallel-hybrid/render-profile.json).

## Verification

Final local simulation run: **98 JUnit tests passed, no skipped tests**, including eleven parallel-hybrid tests. The local dedicated run passed **47 GameTests**, the 294 combustion driving cases and 12 electrified cases. That dedicated run preceded the final long-duration generator-governor and engine-failure fallback adjustments; the final simulation run covers those adjustments, and the release's CI reruns the dedicated suite on its exact commit. Local native UI passed **192 cases** and **105 configured geometries / 1,865 part removals**, including removal/reinstallation at both world LODs. Handling, electric charging and all five graphics-mode cases also passed; graphics was rerun after introducing LODs. Package/release gate tests passed **29 cases**.

The simulation tests cover all seven engine families and FWD/RWD/AWD hybrid launches at reserve, highway engine drive without motor propulsion, independent clutch/motor failures, prolonged demand, charge taper and acceleration priority, failed/cold/full-pack charging rejection, total launch/brake energy bounds, charge-sustain limits, Electric Only and condition-preserving v2 migration.

The sustained-charge test found and fixed a governor defect: always limiting generator load to 90% of spare torque lets crank speed drift toward zero-load equilibrium and generation disappear. Generator load now balances available torque, while maximum pedal / low-RPM launch takes priority. Charge completion uses a half-percentage-point tolerance; otherwise auxiliaries and an asymptotic taper can keep the engine on forever just below the nominal target.

A failed-ignition highway scenario also verifies that the high-speed assistance cutoff only applies with an available running engine. A faulted engine leaves the working electric torque path available above reserve, and motor assistance bridges cranking instead of being cut merely because the ECU requested an engine start.

Native Minecraft checks include actual hybrid entity ticking at depleted reserve, 12 electrified driving layouts, the 294 combustion/family/layout cases, configuration save/load, inventory/charging transactions, steering/drift recovery, 192 UI cases, five graphics cases and full/workshop/LOD part visibility. CI reruns these and adds two-client trading, compatibility and resource gates before publishing a JAR. The immutable release includes logs and provenance for its own commit.

Blender passed 278 targeted drivetrain checks, including each hybrid pack's clearance from its mechanical torque path. Sparse LOD checks covered 854 chunk identities per level; the largest measured bounding-envelope deviation was 4.94 mm nearby and 6.37 mm far away. This is an envelope/topology check, not a proof of every possible surface intersection.

The independent boundary audit passed seven suites / 22,171 assertions, including seeded pack-energy, charge-interlock, preheat and passive-chassis checks. Resource regeneration preserved all authored sound definitions and decoded 48 mono sound assets. This task did not conduct a new subjective listening test.

## Remaining limits

This is a calibrated component simulation, not a manufacturer's hybrid ECU, soft-body solver or exact efficiency map. High-speed assistance cutoff and reserve percentages are explicit game calibrations. Accessories can still consume reserve over time, and a broken/cold charging path cannot be wished into working. Rendering several detailed nearby cars still costs CPU time; modpacks, network conditions and collision-heavy scenes require separate profiling. There is no blanket claim that every source of lag is fixed.
