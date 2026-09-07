# Requirements baseline

**Basis:** the owner's supplied *Modular Car Mod — Requirements, Architecture & Handoff Plan*, dated 2026-09-07. This is a structured summary of that pre-implementation specification, not a claim that its features are complete. Preserve the distinction between requirements here and implementation status in README.md / ROADMAP.md.

## 0. Decisions

The requested target is NeoForge 21.1.x on Minecraft 1.21.1 with Java 21, ModDevGradle, Mojmap/Parchment, a Minecraft-free JVM simulation, custom vehicle physics, data-driven parts, production statistics separated from survival limits, server authority and SI-oriented units. Cars are the first scope; general block physics, AI traffic, roads, boats, aircraft, Fabric and Bedrock are not v1 goals.

Recorded implementation deviations: the first sources are Java rather than Kotlin + Java; the project namespace is `autopropulsion` rather than the provisional `sparkmotors`; Blender replaces the proposed Blockbench-only authoring workflow. The owner explicitly requested Blender. Full spring/damper raycast dynamics, prediction and compressed high-frequency state packets remain requirements, not completed implementation.

## 1. Goals and non-goals

A player chooses a chassis and installs/removes mechanical and body parts. Hardware must change production, durability, sound, temperature, handling and diagnostics rather than act as decorative tier labels. Inline/V/flat piston engines and one-to-four-rotor Wankel families are planned. Transmission, clutch, differential, wheel, tire, brake, suspension and steering choices must have understandable trade-offs.

ECUs are physical, flashable parts. The planned gameplay loop includes garage assembly, tuning, OBD diagnostics, dyno testing, damage, fluids and maintenance. Dedicated multiplayer and passenger correctness are explicit goals. The original plan calls for plausible curves and correct trade-offs rather than CFD or detailed combustion chemistry.

## 2. Architecture

Keep `sim/`, `mod/`, `tools/` and data/asset sources separable. Fixed-step simulation receives driver inputs and immutable resolved hardware, then returns state and events. World collision, registries, persistence, packets, menus, rendering and sounds belong in the mod adapter. Resolve modifiers on part/tune/health-threshold changes rather than walking every part on every substep.

## 3. Stat pipeline

Production includes VE curves, power multipliers, friction/accessory torque, compression, burn efficiency, boost/spool/lag, charge cooling, nitrous and valve-timing profiles. Limits include torque, RPM, boost, temperature, oil pressure and fuel flow. The weakest declaring component sets a structural limit and receives attributed damage. Capacity/behavior statistics include fluids, idle, starting, electrical accessories, roughness and exposed slots.

The original modifier vocabulary includes SET, ADD, MUL, MIN, MAX, CURVE_ADD and CURVE_MUL, with simple declarative conditions. The initial code implements the scalar operations and explicit structural minima. Curve operations, runtime-state-sensitive tuning, damage-scaled per-part modifiers and full fuel/knock systems must be added deliberately. See FORMULAS.md for implemented equations and the explicit correction of fuel-unit conversion.

## 4. Part slots

The chassis is the root of a nested slot tree. Compatibility is tag/family based, not a hardcoded list of named products. Chassis slots cover engine bay, gearbox, transfer case, fuel tank, battery, radiator, exhaust, four wheel/brake/suspension corners, steering, seats and body/interior components.

Piston internals cover crankshaft and bearings/caps/bolts, rods and bearings, pistons and pins/rings/nozzles, liners, heads, camshafts, timing, valves/lifters/springs/seals, gasket/bolts, induction, exhaust, fueling, ignition, ECU, oil, cooling, starter and alternator. Rotary internals replace head/valvetrain parts with eccentric shaft, rotors, housings, apex seals, stationary gears and intake/exhaust port choices, sharing relevant accessories.

The baseline provisionally favors set items for repeated internals, while retaining future per-cylinder damage. Turbo/supercharger compound charging is undecided; do not silently implement an incompatible combination. Two-step is specified as a module requiring ECU support, not necessarily a completely separate controller.

## 5. Vehicle and collision

The intended vehicle is a custom entity with per-chassis dimensions, raycast wheel contacts, suspension forces, swept world collision, limited low-speed step-up, safe passenger seats and persistence. The original plan permits conservative AABB world collision rather than complete rotating rigid-body vehicle collisions. Moving-driver chunk tickets, crash damage, hydrolock and vehicle-as-item storage are later requirements. AABB grip probes in the current prototype must not be described as finished suspension.

## 6. ECU, OBD and dyno

Planned ECU tiers are stock, chipped and standalone. Maps cover 16x16 fuel and ignition tables, boost by gear/RPM, limiters, idle, launch/two-step, TC, ABS, shift behavior, VTEC and fan thresholds. Flashing should require a stopped engine and sufficient battery; tuning files should be shareable items. Diagnostics should expose live signals and persistent, clearable DTCs. The specified roller dyno applies a controlled load and measures a sweep. The initial calculated graph is a stepping stone, not that full system.

## 7. Body customization

Removable doors, hood, trunk, bumpers, fenders, skirts, spoilers, splitters, lights, mirrors, dashboard, seats, cage, paint and liveries are required in the longer plan. Locators and hinges are part of the model contract. Paintable surfaces, emissive lights, translucent glass and first-person interiors need separate rendering treatment. User livery upload remains off/not implemented pending size limits, validation and moderation design.

## 8. Rendering

Parts should ultimately compose at named locators. Wheel spin/steer/compression, hood/door hinges, body roll and dashboard gauges should follow authoritative state with interpolation. Interior/engine LOD and texture batching matter as part count increases. The current procedural Blender pipeline supplies original geometry and locators, but the renderer still draws a fixed reference body rather than arbitrary installed body-part composition.

## 9. Audio

The plan calls for original/licensed engine-family on/off-load sample banks with RPM crossfades, turbo/BOV/afterfire/two-step events, tire and brake sound, starter, horn, doors and crashes. Budget audible sources and distance culling. No downloaded copyrighted engine sound library is authorized by the specification. Audio is not present in the foundation.

## 10. UI

A garage tree with compatible inventory installation, tuning screen, OBD live data and faults, measured dyno graph and paint booth are the intended interfaces. Driver HUD should expose speed, RPM, gear, boost, fuel, temperatures and warnings. Keybinds must avoid dismount/clutch conflicts. Current controls use Left Control for clutch and vanilla Left Shift for dismount, explicitly differing from the overlapping original suggestion.

## 11. Progression and fluids

The baseline proposes thin vanilla-material crafting and cast/forged/billet/titanium progression, without building a shop system. Gasoline grades, E85, methanol, later diesel, oil grades, coolant, nitrous and water-meth are planned. External fluid integration is optional. The first kit/bench/tool recipes and simple fuel can are not a complete survival progression.

## 12. Networking

All inventory, installation, tuning and ownership checks must occur on the server. Client packets must be bounded, finite and rate-limited. Driver/non-driver snapshot rates, quantization, subscriptions for OBD and explicit vehicle events are intended optimizations. The initial tracked-data implementation is simpler and must be profiled before large fleets are advertised.

## 13. Configuration

The requested long-term settings include damage/fuel multipliers, entity crash damage, chunk tickets, vehicle limits, livery restrictions, simulation substeps and client detail/audio/HUD settings. Only actually implemented options are registered now; see CONFIGURATION.md. An absent setting is not a no-op promise of a feature.

## 14. Add-ons

The intended public surface includes part/chassis/engine/fuel/tire/audio definitions, surface tags, events and capability interfaces. The current supported prototype is a codec-loaded part folder with referenced resources and existing scalar statistics. Additional registries, a stable Java API and fluid capability are deferred.

## 15. Phases and acceptance

Preserve P0 through P9: bootstrap simulation; driving in world; in-game parts; engine depth; ECU/OBD/dyno; rotary depth; drivetrain/chassis depth; body/rendering; audio; release polish. Each phase has testable acceptance, not just file-count targets. ROADMAP.md records what remains and which checks must run before advancing.

## 16. Risks and unresolved decisions

The original plan identifies asset volume, collision behavior, stiff numerical constraints, sound budgets, chunk/seat lifecycle and scope creep. Open choices include compound charging, repeated-part inventory representation, two-step placement, livery policy, progression, ECU simple mode and final vehicle scale. The foundation uses full-size metre-scale geometry and no upload feature; these choices do not close all future design questions.

## 17. Scaffold and first implementation

The original handoff prioritized stat evaluation, piston calibration and CLI dyno tests before game integration. The repository follows that dependency order. Small, functioning classes replace an empty skeleton of every eventual package. Avoid equating a directory or a model-study item with implemented gameplay.

## 18. Reference data

The original JSON shapes are design examples, not frozen schemas. The implemented part schema separates structural `limits` from production `modifiers`, explicitly includes `implemented`, and uses `preferred_slot`, category/tag compatibility and exposed child slots. ADDON_GUIDE.md gives the actual accepted format and paths. Invalid datapack reloads fail rather than quietly taking a partial catalogue.
