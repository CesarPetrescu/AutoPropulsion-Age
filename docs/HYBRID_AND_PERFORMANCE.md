# Hybrid control and car performance — 0.8.0

Hybrids now have a parallel mechanical path: the engine drives the selected wheels through a clutch, five-speed gearbox, final drive, differentials and CV shafts. RWD/AWD also need the shaft; AWD needs the transfer case. Electric motors add torque at those same contact patches. The generator loads the crank to charge the pack when output is available. All three contributions use the same installed parts and one wheel integration.

## Choosing a mode

Park, press **R** to switch READY off, then **G → Electric → Hybrid mode**. It cycles **Auto → Electric Only → Charge Sustain**. Switch READY on to drive. Use matching client/server builds; the current charger update is 0.8.1 (network protocol 10).

| Mode | Engine use | Propulsion reserve | Charging band |
|---|---|---|---|
| Auto, HEV | Acceleration, high speed or low charge | 35% | Engine charge request below 45%; running controller targets 55% |
| Auto, PHEV | Acceleration, high speed or low charge | 12% | Engine charge request below 22%; running controller targets 32% |
| Charge Sustain | Same engine drive, higher charge target | 45% | Starts below 55%; running controller targets 65% |
| Electric Only | Engine stays off | 4% | Braking regeneration and compatible external charger |

These percentages refer to the pack's current usable capacity, including wear. Electric assistance tapers over the eight percentage points above the reserve. It tapers out between 50.4 and 79.2 km/h when the engine is available in Auto/Sustain. At highway speeds the engine carries propulsion; the pack still pays for auxiliaries. Engine start/stop uses different on/off thresholds to avoid switching on every tick. The engine starts above 57.6 km/h and remains on down to 43.2 km/h, or for high throttle / low charge.

Low-speed starts can use electric torque; at depleted reserve the clutch permits an engine-powered launch. The clutch can slip and heat, gears shift with the existing interruption, and an engine that cannot supply enough torque may slow down. There is no invented battery refill or forced engine RPM based solely on road speed.

Charging requests are bounded to 8 kW HEV / 12 kW PHEV, taper near the charge target, and respect the actual pack's acceptance, temperature and installed generator condition. Launch and active acceleration suspend charging. At settled highway cruise, even while holding **W**, the controller can divert up to 10% of positive crank torque or use more when the clutch leaves output spare. This adds real crank load and can reduce available wheel power/top speed. Parked charging governs the installed engine's own family RPM target. It finishes within half a percentage point of the nominal target so auxiliaries cannot hold it indefinitely at the end of the taper. Generator output consumes fuel and crank work; rejected energy is accounted for rather than stored twice. Braking regeneration still requires wheel contact and respects the fitted motor, inverter, reduction gear and differential.

The HUD shows **ENGINE / ELECTRIC**, fuel, charge and signed battery power: negative battery kW means charging. **V** adds engine RPM and generator output for hybrids. **G → Electric** shows the reserve and measured clutch torque without adding another large HUD panel.

## Failures and limits

The mechanical and electric paths fail independently: a missing clutch interrupts engine drive but allows electric propulsion; a missing motor can leave the engine drive working. Shared differentials/CVs and control power remain necessary. A failed generator cannot charge the pack. A missing HV harness, contactor or essential 12 V control supply blocks READY. Fuel exhaustion and engine faults permit electric fallback only while the pack can supply it above reserve.

The reserve protects propulsion, not an indefinite supply for accessories. Leaving READY on without fuel or a working charger can eventually drain a battery. Cold, damaged or overheated packs may reject charging or restrict power. These are fictional calibrated parallel hybrids, not replicas of a manufacturer's ECU, planetary power-split transmission or cell chemistry.

Mechanical schema 3 migrates old v2 series hybrids once. Previously absent clutch/gearbox/shaft/transfer mounts inherit the installed generator's wear, damage, faults and deterministic derived identities. Existing mounts, fluids and IDs are retained. A missing generator supplies no mounts. Once migrated, removing those mounts and reloading does not recreate them. Keep world backups when updating mods.

## What caused the measured simulation cost

Before this change each powered electric/hybrid physics step ran a 28-iteration search through the full four-wheel solver, followed by another final solve: up to 2,320 chassis solves per car per second. That happened even when the requested torque was already within the battery's limits. Motor/inverter temperature updates also copied the entire installed-part map repeatedly within each step.

The new path tries full requested torque once and reuses its feasible result. Only constrained requests use bisection, bounded to 14 refinements (less than 0.007% torque resolution); it always keeps a result within the actual electrical budget. Axle thermal changes now share one part-map update. Rendering caches the selected mesh chunks until configuration/component state changes, and each server tick avoids one redundant set of wheel-ground queries.

World rendering now selects medium detail nearby and a lighter mesh beyond 16 metres. Only dense wheels, suspension, drivetrain hardware and fender lips are simplified. Body panels, glass, instruments, controls and engine cores stay exact. Workshop previews retain all full-resolution geometry. Sparse LOD resources share unchanged geometry arrays instead of duplicating the whole kit in memory; both new compressed meshes total about 0.84 MB. Their source hashes, chunk ordering, part masks, normals and envelopes are checked in CI. Native tests remove and reinstall components in all configurations at both detail levels to verify cache invalidation.

The reproducible `:sim:powertrainBenchmark` measures 20 cars, four substeps per tick, 200 warm-up ticks and 600 measured ticks for each powertrain, both parked and driving. It includes component circuits/wear, but **excludes Minecraft entity/world collision, networking, audio, renderer and GPU costs**. Allocation is measured with Java's per-thread allocated-byte counter. See [this run's evidence](qa/2026-09-08-parallel-hybrid.md) for results and limits.

On Windows, after the Java setup in [DEVELOPMENT](../DEVELOPMENT.md):

```powershell
.\gradlew.bat --no-daemon --max-workers=2 :sim:test :sim:powertrainBenchmark
.\gradlew.bat --no-daemon --max-workers=2 -PwithGameTests runGameTestServer
.\gradlew.bat --no-daemon --max-workers=2 -PwithGameTests runClientGraphics
```

The graphics fixture logs `CAR_RENDER_PROFILE`: CPU time submitting the car's visible vertices, excluding GPU execution and the rest of Minecraft. The mesh contains alternatives for many builds; its total triangle count is **not** the number displayed on each car. Detailed visible geometry, collisions in busy worlds, other mods, many loaded cars and resource packs can still cost time. This optimization is not a promise of a particular FPS on every modpack.
