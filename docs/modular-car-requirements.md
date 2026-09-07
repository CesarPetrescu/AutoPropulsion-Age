# Modular Car Mod — Requirements, Architecture & Handoff Plan

Status: pre-implementation spec. Date: 2026-09-07.
Working name: `sparkmotors` (mod id `sparkmotors`, package `com.photonspark.sparkmotors`). Rename freely.

---

## 0. Decisions (made here, revisit only with a reason)

| # | Decision | Rationale |
|---|---|---|
| D1 | **NeoForge 21.1.x on Minecraft 1.21.1, Java 21, Kotlin + Java, ModDevGradle, Mojmap+Parchment.** Same toolchain as the Eln 1.21.1 port. | 1.21.1 is still the largest modded target by mod count; 26.x is where the loader is moving (26.1 released Mar 2026, 26.2 in Jun 2026, no obfuscation from 26.1). Porting later is cheap if the sim is MC-free (D2). If you'd rather skip a port entirely, start on 26.2 now — everything below is loader-version-agnostic except renderer/registry glue. |
| D2 | **Simulation is a pure-JVM Gradle subproject (`sim/`) with zero Minecraft dependencies.** Deterministic, fixed-step, unit-tested, drives a CLI dyno. The mod wraps it. | Lets you develop and test engine/drivetrain physics without launching MC; makes the future 26.x port trivial; makes a standalone tuner/dyno tool possible. |
| D3 | **Custom vehicle physics (raycast-wheel entity), not Valkyrien Skies.** | VS2 on NeoForge 1.21.1 exists only as an unofficial port with no shader support and open build issues; official 1.21+ support is still an open feature request. Don't take a hard dependency on it. Cars don't need general rigid-body block physics anyway. |
| D4 | **Every part is data (datapack JSON via codecs); stats are a declarative modifier stack.** No part-specific Java. | 80+ engine parts × families × tiers is only tractable if adding a part means adding a JSON file + model. Also gives addon authors a surface. |
| D5 | **Two distinct stat classes: *production* (how much torque the engine makes) and *limits* (how much the hardware survives).** Exceeding a limit damages the part that set it. | This is what your list actually encodes: "affects power" = production, "affects maxtorque"/"maxrpm" = limit. It's the mechanic that makes internals matter (a stock-rod engine with a big turbo grenades itself). |
| D6 | **Server-authoritative sim at 20 TPS with 4 substeps (80 Hz), client renders interpolated state; driver-side prediction deferred to a later phase.** | Correctness and multiplayer first; prediction is an optimization with real complexity. |
| D7 | **Units: SI internally (Nm, kW, rad/s, kg, m, K, bar gauge for boost). Display layer converts.** 1 block = 1 m; vehicles built at true scale (~4.2–5 m long). | Avoids the fudge-factor mess; formulas below are real ones. |
| D8 | **Scope v1 = cars only.** No roads, traffic, trailers, aircraft, boats. Motorcycles/trucks are chassis types that can come later. | Keep the surface finite. |

---

## 1. Goals / Non-goals

**Goals**
- Player picks a chassis, then installs/removes every part down to piston rings, and the car's behaviour, sound, durability and diagnostics change accordingly.
- Engine families: inline/V piston engines (any cylinder count/layout the block defines) and Wankel rotary (1–4 rotors), with the internals lists you gave, plus the parts those imply (turbo, supercharger, wastegate, BOV, injectors, clutch, flywheel…).
- Full drivetrain: clutch, transmission types, final drive, differentials, drive layout.
- Wheels/tires/brakes/suspension/steering as installable parts with real trade-offs.
- ECU as a physical, flashable part with maps; OBD-II port with DTCs and live data; dyno block; tuning laptop.
- Damage, wear, thermal and fluid systems that create maintenance loops.
- Body customization: doors, hood, trunk, panels, bumpers, spoilers, lights, paint/livery, interior.
- Multiplayer-correct: dedicated server, multiple passengers, many vehicles.
- Data-driven content so packs/addons can add parts without Java.

**Non-goals (v1)**
- General block physics, vehicle-on-vehicle collisions beyond AABB pushing.
- AI traffic, road generation, GPS.
- Realistic combustion chemistry; we want *plausible curves and correct trade-offs*, not a CFD.
- Bedrock, Fabric.

---

## 2. Architecture

```
sparkmotors/
├── sim/                      pure Kotlin/Java, no MC. Gradle lib.
│   ├── stat/                 StatId, Modifier, StatSheet, Evaluator
│   ├── part/                 PartDef, SlotDef, PartTree, Compatibility
│   ├── engine/               EngineModel (piston, rotary), Induction, Fuel, Thermal, Damage
│   ├── drivetrain/           Clutch, Gearbox variants, Diff, Driveline
│   ├── chassis/              WheelModel (tire, brake, suspension), VehicleBody, Aero
│   ├── ecu/                  EcuModel, Maps, Limiters, TC/ABS/LaunchControl, DTC, ObdFrame
│   ├── audio/                AudioState (pure numbers the client audio layer consumes)
│   ├── Vehicle.kt            composes the above; step(dt, inputs) -> state
│   └── test/                 dyno tests, golden curves, determinism tests
├── mod/                      NeoForge mod
│   ├── registry/             datapack registries + codecs for every *Def
│   ├── entity/               VehicleEntity, SeatEntity, collision, chunk-loading
│   ├── item/                 PartItem (generic, def-driven), EcuItem, ObdDongle, TunerLaptop, Fluids
│   ├── block/                GarageLift, Dyno, FuelPump, PartBench, PaintBooth
│   ├── menu/                 container menus + screens (garage tree, tuner, OBD, dyno)
│   ├── net/                  input packet, state packet, tune packets
│   ├── client/render/        model loader, VehicleRenderer, part composition, animations
│   ├── client/audio/         EngineSoundInstance, event sounds
│   └── compat/               JEI/EMI, Curios (optional), Create fluids (optional)
├── tools/                    CLI: dyno (sim only), model converter (bbmodel→internal), part validator
└── data/                     datagen sources + hand-written datapack JSON
```

**Dataflow per server tick**
1. Driver client → `C2SVehicleInput` (throttle, brake, steer, clutch, gear req, handbrake, nitrous, lights, horn, engine start/stop).
2. `VehicleEntity.tick()` → `sim.Vehicle.step(0.05s, inputs)` with 4 internal substeps.
3. Sim returns `VehicleState` (pose, velocity, wheel states, engine state, fluids, damage events, audio state, DTC events).
4. Entity applies pose, resolves block collisions (see §9), syncs `S2CVehicleState` (delta-compressed, ~10 Hz for non-drivers, 20 Hz for driver), fires sound/particle events.

**Stat evaluation** happens only when the part tree changes (install/remove/damage threshold crossed/ECU flash), producing an immutable `ResolvedVehicle` the sim reads. No per-tick modifier walks.

---

## 3. Stat pipeline

### 3.1 Modifier model
```
Modifier(stat: StatId, op: ADD | MUL | SET | MIN | MAX | CURVE_ADD | CURVE_MUL, value, condition?)
```
Evaluation order per stat: base (from the owning definition) → all `SET` (last wins, warn on conflict) → all `ADD` → all `MUL` (multiplied together) → `MIN`/`MAX` clamps. Curve ops operate on `(rpm → value)` tables with linear interpolation. Conditions are simple predicates on the resolved tree (`has_tag`, `slot_filled`, `family == rotary`, `boost > 0`) evaluated at resolve time, not runtime.

### 3.2 Engine stats (the ones your list maps to)

**Production**
- `ve_curve` — volumetric efficiency vs RPM (0–1.2). The single most important curve; cams, heads, valves, intake/exhaust, ports (rotary) shape it.
- `power_mult` — flat multiplier on torque production (filters, ignition, injection, header, throttle body…).
- `friction_torque(rpm)` — parasitic loss; alternator, oil pump, water pump, bearings add to it.
- `compression_ratio` — derived from `clearance_volume_cc` (head gasket thickness, piston dome/dish, head chamber) and displacement.
- `burn_efficiency` — fuel/injection/ignition quality → thermal efficiency scalar and knock margin.
- `boost_max_bar`, `spool_start_rpm`, `spool_full_rpm`, `boost_lag_tau_s`, `intercooler_eff` (0–1), `charge_temp_k`.
- `nitrous_kw` (flat power add while armed & injecting), `water_meth_knock_margin`.
- `vtec_rpm`, `vtec_ve_curve` — second VE curve switched in above threshold.

**Limits**
- `torque_limit_nm` — structural; **the resolved value is the MIN over all parts that declare it** (weakest link), and the sim records *which part* is the bottleneck for damage attribution.
- `rpm_limit` — MIN over valvetrain/rotating parts (valve springs, lifters, seals, rods, block, ECU hard cut).
- `boost_limit_bar` — head bolts/gasket/pistons.
- `thermal_limit_k`, `oil_pressure_min_bar`.

**State capacity / behaviour**
- `idle_rpm`, `idle_roughness` (0–1, drives RPM jitter amplitude + misfire probability + audio modulation), `health_max` per part, `oil_volume_l`, `coolant_volume_l`, `afterfire_chance`, `starter_torque_nm`, `starter_kw`, `alternator_kw`, `two_step: bool`, `launch_rpm`, slot additions.

### 3.3 Core formulas (sim/engine)

Displacement: `V_d = n_cyl · π/4 · bore² · stroke` (rotary: `V_d = n_rotors · chamber_cc · 2` for torque-equivalence; treat as 2× chamber volume per rotor per rev).

Compression ratio: `CR = (V_cyl + V_c) / V_c`, `V_c = chamber_cc(head) + gasket_cc(thickness × bore area) + piston_cc(dome negative / dish positive)`.

Otto efficiency: `η_th = 1 − CR^(1−γ)`, γ = 1.35 effective (not 1.4; gets closer to real BSFC trends). Rotary uses the same with a fixed ×0.85 factor.

Effective compression under boost: `CR_eff = CR · (1 + boost_bar)^(1/γ)` — compare against fuel `knock_cr` (octane). If `CR_eff > knock_cr + water_meth_margin + intercooler_margin` → knock events: ECU retards timing (power loss) if it has knock control, else piston/gasket damage.

Indicated torque: `T_ind(rpm) = k · V_d · VE(rpm) · ρ_charge/ρ_std · η_th · burn_eff · fuel_energy_factor`, where `ρ_charge = ρ_std · (1+boost) · (T_std / T_charge)`; `k` is a single global calibration constant fitted so a reference 2.0 L NA at CR 10:1, VE 0.9 makes ~180 Nm.

Brake torque: `T_b(rpm) = T_ind − T_friction(rpm) − T_accessories`, `T_friction = a + b·rpm + c·rpm²`.

Power: `P_kW = T_b · rpm · 2π / 60000`. (`hp = T_lbft · rpm / 5252` in the display layer.)

Engine speed integration: `ω̇ = (T_b·throttle_eff − T_load) / J_eff`, where `J_eff = J_engine + J_flywheel (+ reflected drivetrain inertia when clutch locked)`. Throttle response: first-order lag on the throttle plate (`tau` from throttle body/ITBs).

Turbo: `boost(t)` follows `boost_target(rpm) = boost_max · spool(rpm)`, `spool = clamp((rpm − spool_start)/(spool_full − spool_start), 0, 1)`, then filtered `ḃ = (target·throttle − b)/tau`. Wastegate = ECU/part-set `boost_target` ≤ `boost_max`. BOV on throttle close → dumps boost to 0 over 0.15 s + audio event; no BOV → compressor surge event (damage tick on turbo). Anti-lag (ECU feature) keeps `b` from decaying below `b·0.6` off throttle at cost of turbo health.

Supercharger: `boost = boost_max · (rpm/rpm_limit)` (roots/twin-screw linear; centrifugal quadratic) and adds parasitic `T_accessories += sc_drag(rpm)`.

Nitrous: while armed, `rpm > 3000`, throttle > 90%, bottle > 0: `+nitrous_kw` (converted to torque at current rpm), bottle depletes, `CR_eff` margin reduced by 1.0 (knock risk), heavy damage tick on `torque_limit` overshoot.

Damage: each substep compute `overshoot = max(0, T_b − torque_limit)/torque_limit`; `bottleneck_part.health −= overshoot² · k_dmg · dt`. Same shape for `rpm_limit` (valve float first: above `rpm_limit` VE ×0.6 with random misfires; above 1.08× → damage), `boost_limit`, `thermal_limit`, oil pressure. Part health thresholds: `<50%` → its contributions scale by `health/100` (a tired engine makes less power); `<10%` → part "failed": engine won't start / gearbox stuck / wheel locked depending on part class. Failed part sets a DTC.

Idle: PID on throttle toward `idle_rpm`; `idle_roughness` adds `sin(t·f)·A` jitter and misfire probability; rotary peripheral-port sets `idle_rpm_locked = true` (ECU can't change it) and roughness ≥ 0.6 (the "brap").

Fuel: consumption `kg/s = P_kW / (η_th · LHV_fuel) / 1000` with `LHV` per fuel; tank as fluid. Fuel type sets `knock_cr`, `LHV`, `power_mult`, `fouling_rate` (E85/methanol → injector size requirement; wrong injectors → lean → damage).

Thermal: single-node coolant temp and oil temp. `Q_in = P_kW · (1/η_th − 1) · 0.3`, `Q_out = k_rad · (T − T_amb) · (airspeed + fan)`. Coolant volume = thermal mass. Above `thermal_limit_k` → head gasket damage; coolant < 20% → runaway.

### 3.4 Drivetrain
- Clutch: torque capacity `T_cap` (stock/stage1/2/twin-plate); slipping when `|T_in| > T_cap·pedal`; slip generates heat and clutch wear. Flywheel mass sets `J_flywheel` (light = faster rev, harder launch).
- Gearbox types: `manual` (H-pattern, player shifts, clutch required unless ECU has flat-shift/auto-blip), `sequential` (no clutch needed, ignition cut on shift), `automatic` (torque converter model: stall rpm, lockup; shift map in ECU/TCU), `dct` (pre-selected next gear, 80 ms shifts), `cvt` (ratio tracks target rpm). Gear sets are parts: ratios array + `torque_limit`. Money-shift (downshift over `rpm_limit`) → immediate valvetrain damage.
- Final drive ratio part. Differentials per axle: `open`, `lsd(lock_pct, preload)`, `locked`, `torsen(bias)`, `welded`. Drive layout from chassis + transfer case part: FWD / RWD / AWD(split, center diff type).
- `η_driveline` 0.85–0.92 by layout.

### 3.5 Wheels / tires / brakes / suspension (per corner)
- Wheel = rim (diameter, width, offset, mass, bolt pattern tag, style=cosmetic) + tire (width, aspect, compound, load index, `grip_peak`, `grip_slide`, `wear_rate`, `temp_window`, pressure). Slip ratio & slip angle → simplified Pacejka `F = D·sin(C·atan(B·s))` with `D = μ(compound, temp, surface) · F_z`. Surface μ from block below (asphalt/concrete high, grass, dirt, sand, snow, ice tagged via block tag `sparkmotors:surface/*`).
- Brakes: pad compound (μ, fade temp), rotor size (heat capacity, `max_torque`), caliper pistons (bias); per-axle bias; ABS/handbrake as ECU/parts. Brake temp model → fade.
- Suspension: spring rate, damper (bump/rebound), ride height, camber, toe, anti-roll bar, travel; affects load transfer → grip and body roll (render). Coilovers = adjustable in garage UI.
- Steering: rack ratio, lock angle, power steering (affects turn speed at low speed only).
- Aero: `C_d·A` from body, downforce from spoiler/splitter parts, `F_drag = ½ρ v² C_dA`.

---

## 4. Part slot system

### 4.1 Definitions
```
SlotDef { id, accepts: [tag], required: bool, count: int, exposes: [locator], depends_on?: slotId }
PartDef {
  id, family_tags: [tag], slot_tags: [tag],   // what it is / where it fits
  compat: { requires_tags: [...], excludes_tags: [...], size_class?, bolt_pattern?, engine_family? },
  modifiers: [Modifier], adds_slots: [SlotDef], removes_slots: [slotId],
  health_max, mass_kg, model?, sound_bank?, tier, price?
}
```
A vehicle is a **tree**: `Chassis → slots → Part → slots → Part…`. `Compatibility.check(tree, slot, part)` returns a list of typed reasons (UI shows them). Compat is tag-based, never id-based, so addons interoperate.

### 4.2 Chassis-level slots
engine_bay (accepts `engine_block/*` with `size_class ≤ chassis.size_class`, `mount == chassis.mount`), transmission (must match engine `bellhousing` tag), transfer_case (AWD chassis only), fuel_tank, battery, radiator, exhaust_system (from header downstream), wheel_hub ×4 (bolt pattern), suspension ×4, brake ×4, steering, seats ×N, door ×N, hood, trunk, front_bumper, rear_bumper, fenders, side_skirts, spoiler, splitter, headlights, taillights, mirrors, interior_dash, roll_cage, body_paint, livery.

### 4.3 Engine block slots (piston family)
The block defines: `cylinders`, `layout` (I/V/flat), `bore`, `stroke`, `material` (iron/aluminium → mass, `torque_limit`, `thermal_limit`), `deck_height`, `bellhousing` tag, `size_class`, base `torque_limit`, `rpm_limit`, `health_max`, and exposes these slots:

| Slot | Accepts | Notes |
|---|---|---|
| crankshaft | crankshaft/* | exposes main_bearing, main_bearing_cap, main_bearing_bolts, harmonic_damper, flywheel |
| connecting_rod ×n | rod/* | exposes rod_bearing |
| piston ×n | piston/* | exposes wrist_pin, rings, cooling_nozzle |
| cylinder_liner ×n | liner/* | |
| cylinder_head (×2 for V/flat) | head/* | material, OHC/DOHC, exposes cam slots, valve slots, headgasket, headbolts, valve_seal, valve_springs, lifters, camshaft_gear, vtec (if head tag `vtec_capable`) |
| intake_manifold | intake_manifold/* | exposes throttle_valve, may `adds_slots: supercharger` |
| air_filter | filter/* | |
| forced_induction | turbo/* | exposes wastegate, boost_pipe, charge_pipe, intercooler, bov; excluded if supercharger installed (or allow compound — decide) |
| exhaust_header | header/* | chains to chassis exhaust_system |
| fuel_system | injection/* (carb, MPI, DI) | exposes fuel_pump, injectors; fuel type is tank fluid, not a part |
| ignition | ignition/* | exposes spark plugs (optional depth) |
| ecu | ecu/* | exposes obd_port (always), two_step, launch_control as ECU features not parts — **decision**: keep `two_step` as a part per your list, requires ECU tag `supports_two_step` |
| oil_system | oil_pan (adds volume), oil_pump, oil_filter, crankcase_ventilation | |
| cooling | water_pump, water_pipes, water_reservoir (adds volume), thermostat | |
| starter, alternator | | |
| nos_kit | nos/* | `adds_slots: nos_bottle` |
| water_meth | water_meth/* | |
| timing | timing/* (chain/belt/gear) | `torque_limit` + failure mode (belt snap = valvetrain destruction if interference) |

### 4.4 Rotary block slots
eccentric_shaft, rotor ×n, rotor_housing ×n, side_housing ×(n+1), apex_seal ×(3n), stationary_gear, intake_port (side/bridge/peripheral), exhaust_port, plus shared: intake_manifold, air_filter, forced_induction, header, fuel_system, ignition, ecu, oil_system (rotary adds oil metering pump: consumes engine oil by design), cooling, starter, alternator, nos, water_meth. No head/valvetrain/cam slots. `rpm_limit` comes from eccentric shaft + apex seals; `torque_limit` from housings/seals/stationary gear.

### 4.5 Part → stat map (your list, normalized)

Piston family:

| Part | Modifies |
|---|---|
| Air filter | power_mult (+0–4%), restriction affects VE top end |
| Alternator | alternator_kw, friction (bigger = more drag) |
| Engine block material | torque_limit, thermal_limit, mass |
| Turbo boost pipe / charge pipe | boost_lag_tau, boost_limit (silicone vs hard pipe), leaks when damaged (boost ×0.7) |
| Camshaft | ve_curve (CURVE_MUL: shifts peak, stock/street/race/drag profiles), idle_roughness, vtec profile |
| Rod bearing / main bearing | power_mult small (friction), torque_limit; oil-starvation failure point |
| Connecting rods | torque_limit, rpm_limit (weight), mass |
| Crankcase ventilation | torque_limit (blow-by tolerance), oil consumption |
| Main bearing cap / bolts | torque_limit |
| Crankshaft | torque_limit, rpm_limit, stroke override (stroker kits change V_d), power_mult |
| Cylinder head | ve_curve, OHC/DOHC (DOHC unlocks 4v-per-cyl slots, higher rpm_limit), chamber_cc → CR |
| Cylinder liner | torque_limit, boost_limit |
| Harmonic damper | torque_limit, idle_roughness −, rpm_limit |
| ECU | see §6 |
| Exhaust header / exhaust | ve_curve (scavenging), afterfire_chance, sound bank, power_mult |
| Fuel pump | max fuel flow (kg/s) — a **limit**: if demand > flow → lean → knock/damage |
| Fuel injection | burn_efficiency, power_mult, injector flow limit, DI unlocks +CR knock margin |
| Fuel type (tank fluid) | knock_cr, LHV, power_mult, fouling |
| Head bolts / head gasket | boost_limit, torque_limit; gasket thickness → CR |
| Head material | torque_limit, thermal_limit, mass |
| Ignition system | burn_efficiency, rpm_limit (coil saturation), misfire under boost if weak |
| Intercooler | intercooler_eff → charge_temp → density + knock margin |
| Oil filter / oil pump | oil_pressure curve, torque_limit (pump), power_mult small |
| Oil pan | oil_volume, baffled → no starvation under lateral g |
| Wrist pin / rings / cooling nozzle | torque_limit, boost_limit (nozzle → thermal), oil consumption (rings) |
| Piston | torque_limit, boost_limit, dome/dish cc → CR, mass → rpm_limit |
| Starter | starter_torque; weak starter + high CR = no start when cold |
| Throttle valve | power_mult, throttle tau (ITBs fastest), idle control quality |
| Timing type | torque_limit, failure mode |
| Two step | two_step flag (needs ECU tag) |
| Valve lifters / springs / seals | rpm_limit; seals → oil consumption, torque_limit |
| Intake/exhaust valves + count | ve_curve shape, torque_limit, power_mult; 4v/5v per cyl requires DOHC head |
| VTEC | vtec_rpm + second ve_curve; requires head tag |
| Camshaft gear | ve_curve phase shift (adjustable in garage: advance/retard) |
| Water reservoir / pipes | coolant_volume, thermal_limit |
| Water-meth injection | knock margin, charge_temp −, consumes tank fluid |
| NOS kit | adds bottle slot, nitrous_kw by jet size |
| Intake manifold | ve_curve, adds supercharger slot |

Added by implication (needed for the above to make sense): turbocharger (sizes: spool/boost_max/inertia), wastegate, BOV, supercharger (roots/twin-screw/centrifugal), injectors (flow), spark plugs (heat range; optional), flywheel (mass), clutch, gear set, final drive, differential, transfer case, radiator, thermostat, fan, battery, spark/coil packs, fuel tank size, oil (fluid grade → viscosity vs temp), coolant (fluid).

Rotary: as per your list; peripheral intake port → `idle_rpm_locked`, `idle_roughness SET 0.7`, big top-end VE; apex seals → torque_limit + boost_limit + are the primary wear item; side housing → torque_limit; stationary gear → torque_limit + rpm_limit; rotors (light/heavy) → rpm_limit/power_mult; rotor housing → power_mult + torque_limit + thermal_limit.

---

## 5. Vehicle entity, physics, collision

- `VehicleEntity` (custom `Entity`, not a `LivingEntity`) with per-chassis dimensions; passengers ride via child `SeatEntity`s (invisible, one per seat, positioned by locator) so vanilla passenger sync works and seats can be at arbitrary offsets.
- Body integrates position/yaw/pitch/roll from wheel forces. Wheels are **raycast suspension** points: ray down from corner locator, hit → spring/damper force; no hit → wheel in air. Keep the body as an OBB for rendering; for world collision, sweep the vehicle AABB against block collision shapes per substep (vanilla `Level.getBlockCollisions`) and resolve with a small "step-up" of ≤0.5 blocks (slabs/stairs) at low speed; higher obstacles = crash (velocity-based damage to body/frame, chassis `health`).
- Vehicle pushes/hurts entities in its path proportional to speed × mass (config toggles).
- Chunk loading: vehicle ticks a small ticket around itself while moving with a driver (config). Vehicles with no driver stop simulating engine (parked) and only re-sync on load.
- Persistence: entire part tree + per-part health/wear + fluids + ECU map + odometer + paint saved as NBT via codecs on the entity. **Vehicle-as-item**: a "car key/title" item holding a vehicle UUID references a persistent record so cars can be garaged/stored (Phase 6).
- Fall damage, water: drowning engine (hydrolock → severe damage) above hood height; simple buoyancy is out of scope.

---

## 6. ECU, tuning, OBD, dyno

### 6.1 ECU part
ECU is an item with a `DataComponent` holding a `Tune`. Tiers: `stock` (locked maps, mild limiter, no launch control, OBD read-only), `chipped` (maps editable within ±15%), `standalone` (full maps, all features). Features flags: `boost_control`, `launch_control`, `two_step_support`, `anti_lag`, `flat_shift`, `traction_control`, `knock_control`, `rev_limiter_soft/hard`, `closed_loop_fueling`, `map_switching` (N maps selectable from a dash switch), `datalogging`.

`Tune` contents:
- Fuel map: 16×16 (rpm × load) → target AFR (or injector duty for carb tiers).
- Ignition map: 16×16 → timing advance (deg). Over-advanced + high CR_eff → knock; retarded → power loss + EGT up.
- Boost target by gear (and optionally rpm) ≤ hardware `boost_limit`.
- Rev limiter (hard rpm, soft window), idle target (unless locked), launch rpm, two-step rpm, TC slip target (%), ABS on/off (needs ABS hardware part), speed limiter, shift map (auto/DCT), VTEC crossover (if hardware), fan-on temp.

Effects: AFR vs stoich for fuel type → power curve scalar (peak around 12.5:1 gasoline, lean = knock + heat, rich = fouling + less power); timing → power scalar with knock cliff. This gives a real tuning game: the optimal map depends on the hardware.

### 6.2 Tuner laptop item + OBD dongle
Right-click the OBD port on the car (or via garage GUI) with the dongle → opens tuner screen: read ECU identity/tier, read/clear DTCs, live data page (rpm, coolant, oil temp/pressure, boost, AFR, knock count, IAT, throttle, gear, wheel speeds, battery V), datalog to a file the player can export (client-side), flash a `Tune` (requires ECU tier ≥ chipped; flashing takes N seconds, engine must be off, battery must be > threshold or it bricks → DTC + ECU health hit). Tunes are also items (`tune_file` item) so they can be traded/shared and copied.

### 6.3 DTC list (initial)
`P0300` random misfire (idle_roughness/ignition/valve float), `P0325` knock sensor high, `P0234` overboost, `P0299` underboost (boost leak / turbo damage), `P0171/P0172` lean/rich, `P0217` overheat, `P0520` oil pressure low, `P0335` crank sensor (crank damage), `P0700` transmission fault, `C0035` wheel speed sensor (ABS), `B1000` battery low, `U0100` ECU comms lost (ECU failed), plus mod-specific `SM0xxx` for part failures with the slot path.

### 6.4 Dyno block
2-block-wide roller block. Drive the car on, strap in (interaction), run: sim runs a sweep from idle to limiter at WOT in a chosen gear with a load model; outputs torque/power curves rendered in the dyno screen (and the true peak values that the garage UI otherwise only estimates). Optionally "road dyno" mode = logs from a real pull via laptop.

---

## 7. Body, doors, panels, paint, interior

- Every exterior panel is a part with a model and a `paintable` flag; body kit parts replace stock ones. Doors/hood/trunk are parts with a `hinge` locator and open/close animation (players interact → toggle; entering the car auto-opens/closes with delay). Removed door = you can enter without opening, higher drag, no side impact protection (damage to occupant on crash).
- Paint: `body_paint` slot holds a paint part (color RGB, finish: gloss/matte/metallic/chrome) applied to `paintable` panels via tint. Livery: texture overlay layer (resource pack or player-uploaded PNG stored server-side, size-capped, admin-toggleable). Paint booth block for the UI.
- Lights: head/tail/indicator/fog/underglow parts, toggled via input packet; render with emissive layer; optional dynamic light via compat with a light mod (not core).
- Interior: dash (gauges variant: analog/digital), seats (bucket seats + harness = less crash damage), roll cage (chassis health +, weight +), steering wheel (cosmetic + lock ratio), shifter.
- Wheels: rim style cosmetic + stats; tire sidewall text via texture.

---

## 8. Rendering

- Model format: author in Blockbench, export `.bbmodel`; `tools/model-converter` turns it into the mod's internal JSON (cubes/meshes + named locators + animation clips). Loaded at resource reload via a `ModelResourceManager`; parts reference models by resource location. Locators are the attachment system (`locator:wheel_fl`, `locator:door_fl_hinge`, `locator:engine_mount`…). Chassis defines locators; parts attach to a locator on their parent.
- Renderer composes the tree: chassis → transform → part model → its child locators → child parts. Wheel: `steer_yaw`, `spin`, `suspension_offset`. Doors: hinge angle interpolated. Body roll/pitch from sim.
- Engine bay parts render only if hood is open/removed (or always, config) — LOD: skip internal parts beyond 24 m.
- Textures: base + paint mask + livery overlay + emissive. Use vanilla render types; one `RenderType` per texture atlas; batch parts by texture.
- Glass: translucent render pass. Interior: rendered for passengers in first person (camera inside).

---

## 9. Audio

- Engine sound = **sample bank per engine family/character** (i4, v6, v8 crossplane, flat-plane, rotary, diesel, boxer): N samples at fixed RPM points × {on-throttle, off-throttle}. Client-side `EngineSoundInstance` per vehicle in earshot: pick the two nearest RPM samples, crossfade by RPM, pitch-shift each within ±20% (MC/OpenAL pitch is clamped 0.5–2.0; keep per-sample bands narrow to avoid chipmunking), gain from load/throttle. Idle roughness → amplitude modulation at 2–6 Hz. Exhaust part chooses the bank variant (stock/sport/straight-pipe) and gain.
- Event sounds fired from sim events: turbo spool (looping, gain ∝ boost), BOV, wastegate flutter, backfire/afterfire (afterfire_chance × off-throttle deceleration), two-step bangs, tire squeal (slip-based loop), gear whine, starter crank, misfire pops, NOS hiss, brake squeal, horn, door open/close, collision.
- Volume category "Vehicles" in options.

---

## 10. UI

- **Garage lift block** (multi-block 3×2×5 footprint, or single block with a virtual area; start single-block): open GUI showing the part tree as a collapsible slot tree with compat highlighting, drag part items from inventory into slots; shows estimated peak power/torque/mass/weight distribution from `ResolvedVehicle`; shows part health; adjustable parts (cam gear advance, coilover height, tire pressure, brake bias, wheel alignment) expose sliders here. Uninstalling requires the lift; some quick parts (wheels, filter, ECU, tires) can be swapped in-world with a "jack" item.
- **Tuner screen** (§6.2), **Dyno screen** (§6.4), **Paint booth screen** (§7).
- **HUD** for driver: rpm (bar/dial), speed, gear, boost, fuel, temps, warning lights (check engine, oil, temp, battery, ABS, TC), indicators. Dash part chooses HUD style. Passenger sees minimal.
- Keybinds: throttle/brake (W/S), steer (A/D), clutch (LShift), shift up/down (R/F), handbrake (Space), horn (H), lights (L), NOS (N), engine start (E-hold), exit (Sneak-tap), camera cycle (F5 override), map switch (M).
- JEI/EMI recipe + part info tabs (compat module).

---

## 11. Items, blocks, crafting/progression (thin v1)

- Generic `PartItem` whose `PartDef` id is a data component → one item class for all parts. Icons: rendered from the part model (item model via `BlockEntityWithoutLevelRenderer`) or a 2D sprite per part; start with sprite category icons to avoid asset explosion.
- Fluids: gasoline 91/95/98, E85, methanol, diesel (later), engine oil (grades), coolant, nitrous (as item bottle), water-meth mix. Fuel pump block dispenses from a tank (NeoForge fluid API); jerry can item.
- Crafting: v1 = simple recipes from vanilla materials + a "machining" bench for internals (iron/steel tiers). Progression by material tier (cast → forged → billet → titanium). Economy hooks: `price` field on parts for shop mods. Don't build a shop.

---

## 12. Networking

- `C2SVehicleInput` every client tick from driver: bitfield + 3 floats (throttle, brake, steer). Server clamps and rate-limits (max 25/s, ignore if not driver).
- `S2CVehicleState`: position/rotation (vanilla entity sync handles base pose; send extras: roll, wheel angles/spin/compression ×4, rpm, gear, boost, throttle, lights bitfield, engine running). Driver at 20 Hz, others at 10 Hz, quantized (rpm u16, angles i8/i16). Use `EntityDataAccessor` only for rarely-changing state (part tree hash, lights); custom payloads for the hot path.
- `C2SGarageAction` (install/remove/adjust), `C2STuneFlash`, `S2CObdLive` (subscribed stream while tuner open), `S2CVehicleEvent` (sound/particle events, id-coded).
- All server-side validation: player must be in range of the lift, own the item, compat check re-run server-side. Tune values clamped to hardware limits server-side; malformed = reject + log.

---

## 13. Config

Server: damage multiplier, fuel consumption multiplier, entity damage on/off, chunk ticket on/off, max vehicles per player, livery uploads on/off + size cap, sim substeps.
Client: audio bank quality, render distance for interior/engine parts, HUD style overrides, keybinds.

---

## 14. Public API / addons

- All content via datapack registries: `sparkmotors:part`, `sparkmotors:chassis`, `sparkmotors:engine_family`, `sparkmotors:fuel`, `sparkmotors:tire_compound`, `sparkmotors:sound_bank`, `sparkmotors:surface` (block tag → μ). Addon = datapack + resource pack.
- Java API (small): `StatId` registration for custom stats, `Modifier` condition predicates, `VehicleEvent` bus (on install, on damage, on tick-post), `IVehicleAccess` capability for other mods (fuel from Create/Mekanism pipes, etc.).

---

## 15. Phased plan with falsifiable acceptance

Each phase ends with a check you can run; don't advance until it passes.

**P0 — Bootstrap (sim core)**
Repo, Gradle multi-project, `sim/` with `StatSheet`, `Modifier`, `PartTree`, `Compatibility`, piston `EngineModel`, CLI dyno.
Check: `./gradlew :tools:dyno --engine ref_i4_2.0` prints a torque/power curve; reference NA 2.0 L peaks 180±10 Nm / 115±10 kW at ~6000 rpm; same inputs → byte-identical output across 100 runs (determinism test); adding a `cam/race` part shifts peak torque rpm up ≥800 and drops torque below 2500 rpm ≥10%.

**P1 — Drives in world**
`VehicleEntity`, one chassis, one engine, manual 5-speed, clutch, open diff, 4 raycast wheels, basic tire model, block collision + step-up, seat entities, input/state packets, placeholder cube models, HUD rpm/gear/speed.
Check: 0–100 km/h on flat asphalt-tag surface within ±10% of the sim CLI's prediction for the same config; two clients on a dedicated server see the same car pose within 0.25 m at 30 m/s; car survives chunk unload/reload with its state.

**P2 — Part tree in game**
Datapack registries + codecs, generic `PartItem`, garage lift block + tree GUI, install/remove/adjust, `ResolvedVehicle` rebuild, part health persistence, JEI info.
Check: install a turbo via GUI → dyno CLI and in-game estimate agree; incompatible part shows reason and is rejected server-side even if the client packet is forged; 200 parts across 30 slot types load from JSON with a validator pass (`tools:validate`).

**P3 — Engine depth**
Whole piston part list, forced induction, fuel/injection/pump limits, NOS, water-meth, VTEC, thermal, oil, damage model with bottleneck attribution, failures, misfire/valve float, hydrolock.
Check: stock rods + 1.5 bar turbo → rod health hits 0 within N dyno pulls, DTC `SM0xxx` names the rod slot; forged rods survive; lean condition from undersized pump reproduces knock at WOT top end; overheating with coolant drained.

**P4 — ECU / OBD / dyno / tuner**
ECU tiers, `Tune` component, maps, limiters, launch/two-step, TC/ABS, anti-lag, flat-shift, dongle + laptop screens, DTCs, live data + datalog export, dyno block, tune-file items.
Check: retarding timing 8° drops peak power 5–9%; boost target above hardware limit is clamped server-side; flashing with dead battery bricks ECU and logs `U0100`; dyno screen curve matches CLI within 2%.

**P5 — Rotary**
Rotary block/parts/slot tree, port types, oil metering, apex seal wear, rotary sound bank.
Check: peripheral port locks idle and sets roughness; 13B-equivalent reference 2-rotor peaks ~ 200 Nm / 180 kW at ~8000 rpm on CLI; apex seals are the wear bottleneck under boost.

**P6 — Drivetrain & chassis depth**
Sequential/auto/DCT/CVT, LSD/locked/torsen, AWD transfer, clutch capacity/wear, flywheels; brake pads/rotors/fade/bias; suspension params & alignment; tire compounds/temp/wear/pressure; aero parts; surfaces.
Check: welded diff understeers measurably at low speed (steady-state radius test); brake fade after 10 consecutive 100→0 stops with street pads, not with race pads; AWD launch beats RWD launch on same power by a margin the sim reports.

**P7 — Body/customization/rendering**
Blockbench pipeline + converter, locator composition, doors/hood/trunk animation, panels/kits, paint booth, liveries, lights, interior/dash variants, item icons.
Check: model swap of a door via GUI renders correctly incl. hinge; paint applies only to `paintable` panels; livery upload cap enforced.

**P8 — Audio**
Sample banks, crossfade engine, event sounds, roughness modulation, per-exhaust variants.
Check: continuous RPM sweep 800→8000 has no audible sample seam >3 dB at crossfade points (measure RMS in a test harness); 20 vehicles in earshot stay under the sound source budget (cull by distance).

**P9 — Polish & release**
Config, vehicle-as-item/titles, chunk tickets, crash damage, performance pass (100 idle vehicles < 2 ms/tick server), addon docs, 26.x port assessment.

Suggested order if you want a playable milestone soonest: P0 → P1 → P2 → P3 → P4, then P7 before P5/P6/P8 because assets take the longest wall-clock and can run in parallel with sim work.

---

## 16. Risks & open decisions

Risks
- **Asset volume is the real cost.** 80+ parts × models is weeks of Blockbench time even with reuse. Mitigation: internals share a handful of generic meshes with tint/label variants; only visible bay parts get unique models; internals can be icon-only until P7.
- **Collision with a non-block-aligned rotating body** is where MC vehicle mods usually get janky. Keep the AABB conservative, cap speed-per-substep vs block size, and accept "sweep AABB + step-up" for v1.
- **Sim stability at 80 Hz** with stiff springs/tires: use semi-implicit Euler, clamp spring rates, and treat tire force with relaxation length. Test with the CLI at 80 Hz before anything touches MC.
- **Sound source limits** (OpenAL ~255 sources, vanilla budgets far fewer): one looping engine source + ≤2 event sources per audible vehicle, hard cull beyond 48 m.
- **Chunk/entity ticking edge cases**: vehicle straddling unloaded chunks, passengers dismounting on unload. Follow vanilla boat/minecart patterns.
- **Scope creep**: the part list already exceeds most commercial car sims. Ship P0–P4 with placeholder art before touching body customization.

Open decisions (need your call, don't block P0)
1. Compound charging (turbo + supercharger) allowed or mutually exclusive? (Spec assumes exclusive.)
2. Two-step as a part (your list) vs ECU feature only. Spec: part that requires ECU support.
3. Per-cylinder parts (n rods, n pistons) as n item stacks vs one "set" item. Spec leans **set item** (one slot, count implied) for inventory sanity; per-cylinder damage still tracked internally.
4. Livery upload: player PNG upload (moderation risk) vs resource-pack-only. Spec: both, server-toggle.
5. Progression: none (creative-ish/economy-mod driven) vs tiered crafting. Spec: thin tiers, `price` field for shops.
6. Fidelity of ECU maps: 16×16 tables (real-feeling, more UI work) vs 4–6 scalar sliders. Spec: tables, with a "simple mode" that generates the table from sliders.
7. Vehicle scale: true 1:1 (cars feel big in MC, 3-wide blocks) vs ~0.8×. Spec: 1:1; revisit after P1 feel test.

---

## 17. Repo scaffold (concrete)

```
settings.gradle.kts            includes sim, mod, tools
gradle/libs.versions.toml      neoforge 21.1.x, kotlin 2.x, kotlinforforge, parchment 2024.11.17-1.21.1 (verify latest), junit5, kotest
sim/
  build.gradle.kts             kotlin("jvm"), no MC deps, java 21 toolchain
  src/main/kotlin/…/sim/
    stat/StatId.kt, Modifier.kt, StatSheet.kt, Evaluator.kt
    part/PartDef.kt, SlotDef.kt, PartTree.kt, Compatibility.kt, Resolve.kt (→ ResolvedVehicle)
    engine/EngineSpec.kt, PistonEngine.kt, RotaryEngine.kt, Induction.kt, FuelSystem.kt, Thermal.kt, Oil.kt, Damage.kt, Idle.kt
    drivetrain/Clutch.kt, Gearbox.kt (+Manual/Seq/Auto/Dct/Cvt), Differential.kt, Driveline.kt
    chassis/Suspension.kt, Tire.kt, Brake.kt, Steering.kt, Aero.kt, Body.kt
    ecu/Ecu.kt, Tune.kt, Maps.kt, Limiters.kt, TractionControl.kt, Abs.kt, Dtc.kt, Obd.kt
    audio/AudioState.kt
    Vehicle.kt, VehicleInput.kt, VehicleState.kt, Events.kt
  src/test/kotlin/…             DynoTest, DeterminismTest, CompatTest, DamageTest, golden/*.csv
mod/
  build.gradle.kts             ModDevGradle, depends on :sim
  src/main/resources/META-INF/neoforge.mods.toml
  src/main/kotlin/…/SparkMotors.kt (entrypoint), registry/, entity/, item/, block/, menu/, net/, client/, compat/, data/ (datagen)
  src/main/resources/data/sparkmotors/{part,chassis,engine_family,fuel,tire_compound,sound_bank}/*.json
  src/main/resources/assets/sparkmotors/{models/vehicle,textures,sounds,lang}
tools/
  build.gradle.kts             depends on :sim; application plugin
  src/main/kotlin/…/dyno/Main.kt, validate/Main.kt, bbconvert/Main.kt
docs/
  parts.md (this table, kept current), formulas.md, addon-guide.md
```

First three files to write, in order: `sim/stat/Evaluator.kt` (+test), `sim/engine/PistonEngine.kt` (+DynoTest with the reference 2.0 L golden curve), `tools/dyno/Main.kt`. Nothing in `mod/` until those pass.

---

## 18. Reference JSON shapes

```jsonc
// data/sparkmotors/part/rod_forged_i4.json
{
  "family_tags": ["rod", "rod/forged"],
  "slot_tags": ["connecting_rod"],
  "compat": { "requires_tags": ["engine_family/piston", "cyl_count/4"] },
  "modifiers": [
    { "stat": "torque_limit_nm", "op": "SET", "value": 650 },
    { "stat": "rpm_limit",       "op": "SET", "value": 9000 },
    { "stat": "rotating_mass_kg","op": "ADD", "value": -0.4 }
  ],
  "health_max": 100, "mass_kg": 2.4, "tier": 3, "price": 1200,
  "model": "sparkmotors:vehicle/internal/rod_generic", "icon": "sparkmotors:item/rod_forged"
}
```
```jsonc
// data/sparkmotors/part/intake_manifold_sc_ready.json
{
  "family_tags": ["intake_manifold"],
  "slot_tags": ["intake_manifold"],
  "compat": { "requires_tags": ["engine_family/piston"] },
  "modifiers": [{ "stat": "ve_curve", "op": "CURVE_MUL", "curve": [[1000,0.97],[4000,1.0],[7000,1.06]] }],
  "adds_slots": [{ "id": "supercharger", "accepts": ["supercharger"], "required": false, "count": 1, "exposes": ["locator:sc_mount"] }],
  "removes_slots": [],
  "health_max": 100, "mass_kg": 6.0, "tier": 2
}
```
```jsonc
// data/sparkmotors/chassis/hatch_a.json
{
  "size_class": 2, "mount": "transverse", "drive_layouts": ["fwd"], "mass_kg": 980,
  "wheelbase_m": 2.5, "track_m": 1.48, "cd_a": 0.62, "seats": 4, "doors": 3,
  "locators": { "engine_mount": [0,0.4,1.2], "wheel_fl": [-0.74,0.3,1.25], /* … */ },
  "slots": [ /* SlotDef list per §4.2 */ ],
  "modifiers": [{ "stat": "body_health_max", "op": "SET", "value": 100 }],
  "model": "sparkmotors:vehicle/chassis/hatch_a"
}
```

`Tune` component (ECU item): `{ "ecu_tier": "standalone", "fuel": [[…16×16 AFR…]], "ign": [[…16×16 deg…]], "boost_by_gear": [0.8,1.0,1.2,1.2,1.2,1.2], "rev_hard": 8200, "rev_soft": 8000, "idle": 900, "launch": 4500, "two_step": 5000, "tc_slip": 0.08, "abs": true, "vtec_rpm": 5800, "map_index": 0 }`.