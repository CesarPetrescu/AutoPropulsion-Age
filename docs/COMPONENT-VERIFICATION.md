# Component systems: verified 0.3.0-alpha build

## Exact build

- Minecraft Java 1.21.1; NeoForge 21.1.249; Java 21.
- Feature branch: `feat/component-damage-and-vehicle-audio`.
- Tested commit: `09df0722b1fb983ea05346d09266ec3ad76db568`.
- Successful workflow: https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/runs/34171855673
- Artifact `vehicle-validation`: `build/libs/autopropulsion-age-0.3.0-alpha.jar`.
- JAR size: 2,225,348 bytes.
- SHA-256: `989448d1f892a4586c3aece1ebf7ec86fa3d866312650a567b49f5e58e23ec25a3b9`.

This verification record and removal of a temporary transport file do not change
any Java code, game resource, or build input compared with the tested commit.
The historical 0.2.0 JAR linked by the main README does not include this feature.

## Completed checks

The downloaded CI reports were inspected, not inferred from the presence of a
workflow file:

| Check | Result |
|---|---|
| `ConditionAudioTest` | 8 tests; 0 failures/errors/skips |
| `SimulationTest` | 13 tests; 0 failures/errors/skips |
| `DamageRegressionTest` | 5 tests; 0 failures/errors/skips |
| `VehicleAudioTest` | 2 tests; 0 failures/errors/skips |
| `VehicleConditionTest` | 5 tests; 0 failures/errors/skips |
| Unit-test total | 33 passing |
| Dedicated-server GameTests | 20 passing; no required tests failed |
| Native Minecraft client smoke | PASS |
| Audio resource validation | 58 mono Ogg Vorbis vehicle assets validated |
| Model/resource validation | Reported PASS for vehicle model, component resources and recipes |

The native client result records driving, garage, paint and tuner interactions,
all 21 family/induction layouts through the native UI, and component inspection
controls: repair requests, body cutaway, condition overlay, orbit and zoom.
The workflow preserves actual Minecraft screenshots, logs, the JAR, unit-test
XML/HTML reports and the source snapshot. The driving and damage-inspection
screenshots were also visually checked after download.

Artifacts from that run are `vehicle-source`, `vehicle-validation` and
`vehicle-client-smoke`. GitHub artifact retention is limited; keep a local copy
of the JAR and source that you test.

## Playing this build

Back up the world. Replace the old AutoPropulsion Age JAR rather than loading
both versions. Client and dedicated server must use the same version because
the network protocol changed to version 3.

Open **G -> Inspect** for component condition, wear, impact damage and per-part
repairs. Park and stop the engine before servicing. Engine repairs require the
hood fully open. In **Garage**, the new exhaust slot offers stock/sport mufflers
or removal for an open pipe. Drag the preview to orbit and scroll over it to
zoom. Scroll over the component list to move through its pages.

See [component damage, audio and inspection](COMPONENT-DAMAGE-AUDIO.md) for the
mechanics, inventory/save migration rules, sound layers and rebuild commands.

## Scope of verification

This is a tested alpha, not a claim that every modpack or multiplayer topology
has been tested. Separate-machine multiplayer, long survival sessions and
human listening on representative speakers/headphones remain playtest work.
The 58 sounds are original procedural synthesis, not recordings of real cars.
The damage and traction models are gameplay calibrations, not soft-body crash
physics, independent per-wheel force simulation, or a fluid-leak model.
