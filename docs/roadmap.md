# Feature status and implementation roadmap

The original modular-car specification describes the destination. This repository currently implements a smaller alpha foundation, not P0 through P9 in full. Implementation status and test status are separate; see [Testing](testing.md).

## Present in code

| Area | Current implementation | Important gap |
| --- | --- | --- |
| Engine | Reference 2.0 L I4, stock/race curves, boost, limits, fuel and cooling | No complete combustion, ignition/injection or oil-pressure model |
| Driving | Five forward gears, neutral/reverse, clutch, brake, handbrake, steering | No full differential, automatic, DCT or CVT models |
| World integration | Server-authoritative entity, ownership, two passenger positions, AABB movement, surface rays | No independently sprung wheels or validated two-client latency tolerance |
| Assembly | Seven installed slots; 16 installable definitions | Nested `PartTree` is a sim utility, not the in-game assembly UI |
| Workshop | Garage telemetry, boost controls and analytical dyno estimate | No drag-and-drop tree, ECU flashing, datalogging or measured rollers |
| Assets | 140 catalogue models; editable car/component Blender scenes | Category proxies and reused geometry are not final individually detailed parts |
| Damage | Engine-wide condition, collision damage and basic diagnostics | No per-part health/wear, hydrolock, fluid leaks or accurate DTC attribution |
| Persistence | Owner, slots, fluids, state and odometer; safe parking on reload | No stable migration guarantee or vehicle-title storage system |

## Deliberate deviations from the source specification

The target remains NeoForge 21.1.x / Minecraft 1.21.1 / Java 21. Java-only modules avoid adding Kotlin runtime/build dependencies before the gameplay loop is validated. Blender replaces the initially proposed Blockbench-first workflow at the owner's explicit request. Both are authoring choices, not claims that the simulation or gameplay requirements were waived.

Four surface rays currently estimate grip rather than solving suspension. The flat assembly, analytical garage graph, single synthetic audio loop and global engine condition are disclosed intermediate implementations. The current formula model keeps SI conversions explicit but is not a literal implementation of every formula in the specification.

## Next acceptance gates

### 1. Reliable driving and multiplayer

Test a clean installed JAR on client and dedicated server, two simultaneous licensed clients, chunk unload/reload, passenger dismount, collisions, slopes/slabs/stairs, ice and low-grip surfaces. Compare straight-line acceleration to the pure simulation. Measure synchronization error instead of claiming it is below 0.25 m without data. Add explicit entity-count and tick-time budgets.

### 2. True modular assembly

Wire the nested tree and declarative modifiers into codecs/datapack registries and a server-backed container menu. Add validated install/remove/adjust transactions, dynamic compatibility reasons and per-part condition. Maintain stable model indices or migrate away from built-in index assumptions. Add an invalid-packet test for every mutation path.

### 3. Engine and maintenance depth

Implement the piston internals list, production versus survival limits, forced-induction hardware, fuel-flow limits, oil pressure/volume, cooling volume, per-part failure attribution, nitrous, water-meth and VTEC. Use reproducible dyno pulls to show weak rods fail before upgraded rods under the same load; do not treat changing a single global health scalar as completion.

### 4. ECU, OBD and measured dyno

Add ECU tiers, tune data components, validated fuel/ignition/boost maps, flashing preconditions, diagnostic subscriptions and datalog export. The physical dyno must measure loaded pulls and agree with the independent model within a declared tolerance. Rename or label estimates clearly until then.

### 5. Rotary and drivetrain/chassis depth

Implement rotor/eccentric-shaft/housing/seal models, port-dependent behaviour and oil metering. Then add gearbox variants, differential/drive-layout models, tyre temperatures/pressure/wear, brake fade, suspension and alignment. Each system needs a distinguishing quantitative test before being marked implemented.

### 6. Art, body systems, audio and release

Replace proxies, improve silhouettes/UVs/materials, add locator-driven body parts and animations, paint, resource-pack liveries and proper engine sound banks. Validate resource reload, renderer performance, hardware GPU output and audio continuity. Resolve licensing, document addon APIs, publish tested artifacts and write a migration policy before a public release.

Deferred/non-goals remain AI traffic, roads-as-a-world-system, boats, aircraft, general block physics, Bedrock and Fabric. Road/asphalt crafting in the current prototype is not an AI traffic system.
