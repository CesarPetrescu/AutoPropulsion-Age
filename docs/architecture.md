# Architecture: implemented foundation

This document describes the code in this branch, not every system in the original pre-implementation specification.

## Boundaries

`sim` has no Minecraft dependency. `EngineSpec` and `Curve` describe the reference engine; `EngineModel` returns torque, power and fuel demand; `VehicleSimulation` advances speed, RPM, gear, fluids, condition and odometer. `tools` exposes the same analytical engine sampler as a CLI.

`mod` owns registry and world concerns. `VehicleEntity` validates inputs and management actions, applies the simulation's longitudinal speed through Minecraft collision movement, reconciles the resulting speed, persists state, and synchronizes telemetry. Client-only rendering and screens live under `client/`. The installable mod JAR embeds the `sim` output.

## Server tick and trust boundary

1. The driver sends bounded throttle/brake/steer/flag inputs.
2. The server rejects non-finite floats, unknown flags and non-driver inputs. An entity accepts at most two input packets per server tick. Management actions also check ownership, distance and an action cooldown.
3. A 50 ms tick advances the pure simulation with the configured substeps, normally four. Inputs older than five ticks or vehicles without a controller fall back to braking/parking.
4. Four contact raycasts estimate surface grip. Vanilla AABB movement resolves world contact. This is not a spring/damper suspension simulation or a rigid-body solver.
5. The server reconciles collision-limited speed and synchronizes RPM, speed, fuel, temperature, condition, boost, steering, gear and ignition state. The client interpolates entity movement.

The normal driver key handler requires an active window and no open screen. Opening a screen sends parked input. Developer smoke automation is explicitly gated by JVM properties and bypasses physical keyboard input, but sends control payloads through the real client-to-server networking path.

## Assembly and data

The current vehicle maintains **seven flat functional slots**: block, rods, camshaft, induction, radiator, tires and ECU. `PartCatalog` reloads a complete validated server-side JSON snapshot and requires all starter definitions. It rejects malformed statistics and caps the catalogue at 512 definitions.

The independent `PartTree` supports nested exposed slots and tag compatibility, but is not yet wired into the in-game garage. Its replacement/removal operations must preserve dependencies. `StatResolver` separates production modifiers from weakest structural limits; the current vehicle adapter uses a smaller direct resolver instead of the full modifier pipeline.

Metadata such as `mass_kg` is not yet summed into the vehicle dynamics. The reference mass remains 1180 kg. A billet block's higher limit does not help while a weaker installed rod still limits the engine. A standalone ECU does not implement arbitrary fuel/ignition maps.

## Persistence and transactional safety

`DataVersionAP=1` marks the entity save format. Owner, installed slots, fuel, thermal state, condition, odometer and diagnostic information are persisted. Reload intentionally parks the vehicle with ignition off rather than restoring a moving car into a possibly unloaded scene.

`VehicleSimulation.restore` validates every numeric field before mutating any live field. This was added after a local regression demonstrated that invalid RPM could otherwise leave a newly assigned speed behind. `PartTree` similarly checks the prospective tag set before a replacement can break another installed part's requirements.

## Rendering and audio

The Blender export supplies a compact vehicle mesh with named body, engine-bay, hood and wheel groups, local pivots, triangle normals and palette colours. The renderer feeds those triangles into a vanilla entity render type, with degenerate fourth vertices for its quad interface. Wheel steering/spin is visual; there is no tyre deformation, glass transmission or articulated door system yet. A resource reload clears the mesh cache.

Component items use NeoForge OBJ/MTL models selected through stable custom-model-data indices. The authoring source remains editable `.blend`; no Blender process runs inside Minecraft.

The audio prototype is a single original synthetic loop, not a recorded engine-family sample bank. Null OpenAL output in CI validates startup only, not perceived sound quality.

## Numerical model and limits

Power uses `kW = torqueNm * rpm * 2*pi / 60000`. Fuel demand converts kW to W before division by efficiency and fuel energy in J/kg. RPM-dependent reference curves, boost lag, clutch torque, aerodynamic drag, rolling resistance, cooling and bounded damage provide a tuning foundation, not a high-fidelity engine simulator.

Do not infer per-wheel suspension, ABS/TC, full load transfer, rotor simulation, oil-pressure simulation or individual-part damage from the presence of their catalogue models. See [Roadmap](roadmap.md) for those requirements.
