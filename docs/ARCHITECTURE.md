# Architecture

## Dependency boundary

`sim` is a Java 21 library with no Minecraft, NeoForge, rendering or filesystem dependencies in its model classes. `tools` depends on `sim`; `mod` depends on `sim`. The packaged mod JAR includes the simulation classes. A downstream Minecraft port should replace the adapter layer rather than make the physics depend on loader APIs.

The original handoff suggested Kotlin + Java and a Blockbench converter. This implementation deliberately starts Java-first and uses Blender because the repository owner explicitly requested Blender authoring. There is no KotlinForForge requirement. The stable project namespace is `autopropulsion`, replacing the freely renameable working name `sparkmotors` from the supplied spec.

## Server dataflow

1. A client sends entity ID, throttle, brake, steering, clutch, gear and a small flag bitfield.
2. `InputPayload` rejects non-finite values, out-of-range controls, unsupported flags and non-driver packets. `VehicleEntity` additionally checks owner/operator authorization and accepts at most one packet per game tick.
3. At each server tick, the adapter samples four ground probes, supplies a grip coefficient, and steps the pure model through 1–8 fixed substeps (four by default, 80 Hz).
4. The adapter turns longitudinal speed/yaw into movement, applies a conservative full-length AABB and vanilla swept collision, then constrains simulated speed after a collision.
5. Vanilla entity pose synchronization and tracked data carry visible state. The client interpolates poses and draws original indexed meshes. The prototype does not implement the spec's optimized custom state-delta protocol or driver prediction.

No client-supplied position, power, damage, fuel quantity or ownership field is trusted. No-input timeout is ten server ticks. A disconnected or unmounted driver leaves the car in neutral with the handbrake applied, although a running engine may still idle and consume fuel until stopped or unloaded.

## Parts and stat resolution

An assembly is a sorted map of slot paths backed by a tree of exposed slots. Parent parts expose child slots. Compatibility uses categories and ancestor tags; failures are typed strings suitable for UI display. Installation is capped at 128 parts and eight path levels. Replacing/removing a parent while descendants remain is rejected.

Production statistics use `SET -> ADD -> MUL -> MIN -> MAX`. Multiple SET values are deterministic by sorted slot order and generate a conflict warning. Structural limits are a separate minimum-over-parts reduction with the source slot retained. This prevents a forged part's ordinary SET modifier from silently erasing the limit imposed by weaker hardware. Curve modifiers and arbitrary conditions from the design document remain future work.

Installing a part builds and validates a candidate assembly before consuming the held item. Resolution runs on assembly/tune change, not for every simulation substep. Installed part changes alter the simplified engine specification and selected grip/cooling scalars. Runtime engine health is currently one aggregate value with bottleneck attribution, not a separate state object per internal component.

## Persistence

`ApaSchema=1` stores owner, installed IDs by slot path, requested ECU limiter, hood state, fuel, coolant/oil temperatures, engine health and odometer. Vanilla entity data stores position and orientation. Saved unknown part IDs are retained and reported instead of being silently replaced; the engine cannot run with unavailable parts.

On reload, speed, boost, RPM and selected gear are deliberately reset and the engine is stopped. This is a safety policy for parked/unloaded cars, not full moving-vehicle continuity. Schema migrations, recovery tooling, vehicle titles and an off-entity storage database are not implemented.

## Rendering and menus

Runtime model JSON contains triangles/quads, vertex coordinates, colors, groups, pivots and named locators, in metres. `MeshRenderer` uses the same source meshes as Blender. Wheels steer/spin around pivots; the hood hinges independently; engine-bay detail is distance/hood gated. Body panels do not yet come from the installed part tree. Glass is intentionally opaque in this prototype.

The workshop uses a server menu with bounded button IDs, a frozen slot-path list for each session, and server-provided telemetry/curve samples. Freezing paths prevents an earlier removal from moving later button indices onto a different part. Item installation remains an in-world interaction. The dyno page is explicitly a calculated WOT sweep, not the future strapped roller/load simulation.

## Deliberate missing architecture

There are no `SeatEntity` passengers, chunk tickets, collision damage to other entities, Create fluid capability, livery uploader, external network service, audio manager, Kotlin runtime, Valkyrien Skies integration or compatibility modules. Add those through small explicit interfaces and separate tests, not empty classes advertised as features.
