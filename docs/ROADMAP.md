# Roadmap and acceptance gates

The full project is larger than the initial alpha. A feature is complete only after its behavior and failure cases are tested. This document preserves the supplied P0–P9 ordering while making the foundation's shortcuts explicit.

| Phase | Foundation status | Remaining acceptance gate |
|---|---|---|
| P0 — Bootstrap | Deterministic Java simulation, scalar stats, weakest limits, compatibility and CLI dyno implemented | Extend curve modifiers/conditions; retain 100-run determinism, calibration band and race-cam regression |
| P1 — Drives in world | Single-driver vehicle, controls, simple drivetrain, ground probes, swept collision, persistence and HUD | True stable wheel suspension; measured in-game/CLI acceleration agreement; simultaneous two-client pose error under 0.25 m at 30 m/s; chunk/seat lifecycle |
| P2 — Part tree | Codec-loaded parts, candidate-assembly validation, in-world install, workshop removal | Inventory-backed tree GUI; independent part health; reload synchronization; 200 working definitions across 30 slot types, not model-only entries |
| P3 — Engine depth | Simplified boost, heat, fuel and aggregate structural wear | Full internal dependencies, flow limits, AFR, knock, oil pressure/starvation, NOS, water-meth, timing failure and hydrolock; reproducible damage attribution tests |
| P4 — ECU / OBD / dyno | Live telemetry, limited faults, standalone-ECU limiter, calculated graph | 16x16 maps, ECU tiers/flash lifecycle, persistent DTC clearing, logging/tune files, measured roller sweeps, hardware clamp/invalid-flash tests |
| P5 — Rotary | Prototype torque family and installable reference definition; component models | Oil metering, port-dependent idle/audio, seal wear, calibrated rotary reference and boost failure test |
| P6 — Drivetrain / chassis | Five forward ratios + reverse, assisted clutch, simple grip/brake/drag | Sequential/automatic/DCT/CVT, real differentials/AWD, clutch heat, brake fade, suspension/alignment, tire state and load-transfer tests |
| P7 — Body / rendering | Original hatch, model catalogue, wheel/hood animation, workshop renders | Actual installed-part composition; doors/trunk; paint/livery; translucent glass; light layers; per-part visual swaps and resource reload validation |
| P8 — Audio | Not started | Original/licensed sample banks; crossfade tests; event sounds; audible-source budget under 20 nearby vehicles |
| P9 — Polish / release | Build/test workflows and development documentation | Performance profiles, storage/titles, migration, server limits, crash tuning, release packaging, addon API and compatibility matrix |

## Closest next milestone

Finish the real P1 wheel/contact solver and add two-client movement/persistence coverage before multiplying chassis or marking model-only parts installable. The current scripted driving checks are a smoke test of packet-to-world integration, not a replacement for those physics acceptance tests.

## Release blockers

No stable release should be advertised while the full assembly dependency model, safe multi-seat lifecycle, inventory-backed garage, independent part wear, measured dyno and representative collision tests are absent. Audio, survival progression, licensing choice, Windows/native-GPU verification and addon stability also need an explicit release decision. An alpha may be distributed for testing only when its README and release notes retain these limits.
