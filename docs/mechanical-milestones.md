# Connected mechanical system — implementation evidence

Work started from `2bbe5ea` on `mechanical-components`. The supplied audit examined `83b91cd`; some independent crank, clutch and turbo concerns predated the existing 0.3 implementation. The existing simulation, garage, mod IDs and ownership protocol were extended. No downloadable handoff file was attached; the pasted specification is the reference. Tests below were performed on 8 September 2026 in this implementation run.

## Milestone status

| Milestone | Outcome and evidence |
|---|---|
| M0: baseline | Complete. Fresh 22 JUnit / 18 GameTests and the hidden native quick client passed before edits. Confirmed combined braking, absent-brake fallback, airborne braking and sound-generator ownership defects. |
| M1: persistence | Complete. Immutable, versioned NeoForge component data carries UUID, specification, wear, damage, faults, reserve and temperature. Used parts survive service, storage, network codecs, crafting, engine/crate conversion and saves. Fresh M1: 22 JUnit / 22 GameTests. |
| M2: playable repair | Complete. Localized front impact can damage a hose; leaking coolant, timed pressure diagnosis, targeted replacement, separate conserved refill and repeat verification work in the native client and server. Independent tire/brake corners, contact-dependent braking and distinct handbrake work. Fresh M2: 28 JUnit / 24 GameTests and the native repair workflow. |
| M3: mechanical depth | Core milestone implemented. Starting, oil pressure/supply, charging, clutch/shift state, drive-path failure and independent wheel behavior connect to installed parts. Fresh M3: 35 JUnit / 25 GameTests, including all 98 native family/grade/induction driving layouts. Deeper cylinder/chamber internals remain grouped assemblies. |
| M4: audio | Integrated and mechanically tested; listening acceptance pending. 48 real, original synthesized mono OGG assets with explicit family/load/exhaust/induction/road/fault mixing. Fresh audio tests and native active-channel/cleanup checks passed. No model listening tool is available and user feedback has not arrived. |
| M5: cockpit/workshop | Baseline complete. Sender-aware cockpit needles and warnings, optional performance display, odometer/trip, orbit/pan/zoom/focus/underside, physical jack/access, selected components and independently animated wheels/fan. Native screenshots reviewed; the mini-needle depth defect was found and fixed. Larger body/latch/Expert workflows remain outside this implemented baseline. |
| M6: integration | Complete for the recorded scope. Fresh 43 JUnit / 26 server GameTests, the full 49-layout / 42-hardware native matrix, final repair-and-drive / rebuild UI / saved-temperature regressions, and a real two-client trading test passed. Audio regeneration, targeted Blender geometry and scoped performance measurements passed. Listening, remote latency and fleet rendering remain unverified. |

## Architecture and actual coverage

`MechanicalState` is the immutable persistent authority, shared by vehicle and item codecs. `ComponentSlot` gives each repeated corner its own identity. Fluid amount, operating temperature, wear, structural damage and discrete faults are distinct. Vehicle schema 4 migrates older aggregate engine condition once; typed item schema 1 retains missing mounts instead of creating replacement parts. Existing registry IDs remain unchanged and protocol 4 requires matching client/server versions.

`CircuitPhysics`, `MechanicalCapabilities`, `WheelDynamics`, `EnginePhysics` and `TransmissionPhysics` consume the installed state. A running engine can have no torque path or failed brakes; a stopped engine does not prevent rolling. Coolant mass, circulation, radiator/fan behavior and heat balance produce temperature. Oil quantity/supply produces pressure; starvation wears internals and compressors. Electrical demand and alternator output change battery charge. Individual contact, tire pressure/tread, brake heat/condition, suspension and links affect motion. Impacts allocate one bounded localized budget and use a cooldown to avoid substep repetition.

`VehicleAudio` produces simultaneous layers from actual family, RPM, load, fitted muffler/compressor and corner contact/fault state. `CarAudio` owns the positional voices and their lifecycle. Synthesized assets are original game audio, not engine recordings. The [listening reel](mechanical-audio-preview.mp3) and [timeline](audio-preview-timeline.json) are available for review. All 48 assets decode as mono Vorbis with finite, unclipped decoded peaks. Resource regeneration preserves authored sound definitions and hashes.

The normal cockpit uses sender-aware `InstrumentReadings`; failed senders do not report perfect values. The garage Live panel is explicitly assisted telemetry. The multimeter, fluid inspection, timed pressure tester, tire gauge, oil-pressure tool and grouped compression test provide specific evidence. Fault-history clearing is separate from repair. Used parts stay used, and partial 1 L fluid bottles retain the remainder.

The Blender master was preserved. Reproducible derived scripts split hoses, springs/dampers, fan blades/shrouds and induction plumbing and add service geometry and cockpit gauges. The current runtime union contains 734 batches / 268,980 triangles; alternatives are mutually exclusive. All 49 fit envelopes and five-position hood sweeps passed the targeted clearance/intersection checks, and all 294 hardware geometry identities passed. These checks do not establish clearance for every possible pair of all hardware alternatives. `assets/engine_workshop.blend` contains the matching 91 editable derived scenes.

## Twenty acceptance scenarios from the pasted requirements

The pasted handoff described acceptance examples but did not include its separate numbered attachment. These twenty traceable scenarios cover that supplied scope; they are not a claim to have read an unavailable file.

| # | Scenario | Evidence / limit |
|---|---|---|
| 1 | Worn part removed, serialized and refitted without healing | Native `wornHoseRoundTripsItemNetworkSaveAndAnotherCar`; actual two-client trade |
| 2 | Used assembly crafting and car/engine conversion preserve state | Native wheel-assembly, donor engine and crate tests |
| 3 | Older saves migrate once with distinct corner identities | Native migration, save round trips and verified resumed ticks retaining hot brake/clutch/tire state |
| 4 | Non-owner, out-of-range and invalid work cannot consume/duplicate parts | Native server tests; two actual clients verify owner rejection |
| 5 | Front impact creates a localized coolant fault once | Mechanical physics plus native pressure workflow's repeated-impact check |
| 6 | Leak causes loss, overheating and retained permanent damage | `MechanicalPhysicsTest` circuit simulation; actual server/client leak diagnosis |
| 7 | Hose replacement stops its leak without filling or healing internals | Native timed repair workflow and pure circuit tests |
| 8 | Partial refill conserves quantity and verification holds pressure | Native fluid remainder and ten-second repeat test |
| 9 | Four tire identities, pressures and used corner swaps | Native corner-transfer test; wheel simulation tests |
| 10 | Service brake, rear handbrake and missing brakes behave differently | `MechanicalPhysicsTest`, including asymmetric front hydraulics |
| 11 | Airborne brakes slow the wheel, not the chassis | `MechanicalPhysicsTest` airborne case |
| 12 | Starter/flat battery/ignition faults determine cranking and running | Powertrain JUnit and native electrical/oil test |
| 13 | Clutch slip, finite shifting and failed drivetrain permit appropriate coasting/revving | `MechanicalPowertrainTest` |
| 14 | Oil-supply fault damages a replacement compressor until its cause is repaired | `MechanicalPowertrainTest`; native pump replacement retains wear |
| 15 | Alternator/belt control charge independently of internal damage | `MechanicalPowertrainTest` |
| 16 | Muffler-only swap changes exhaust mix without giving free power | Powertrain + audio JUnit tests |
| 17 | Turbo release requires hardware, pressure and a real transition; blowers use drive state | Powertrain + audio JUnit tests |
| 18 | Road audio works engine-off, requires contact, and voices clean up | Audio JUnit plus native active-channel and cleanup checks; listening quality pending |
| 19 | Cockpit failed sender, actual service access and visible part removal work | Instrument JUnit + native client screenshots/actions, physical jack and focused hose |
| 20 | Regeneration, native integration and performance are recorded | 48 decoded assets/hash check; two-client report; final verification record; scoped benchmark |

## Limits and next unfinished work

Subjective listening acceptance is the next unfinished gate. There is no fabricated claim to have listened. Separate-machine/network-latency tests, other modpacks, prolonged survival play and many-car rendering remain untested. The simulation benchmark excludes Minecraft world/collision/render/network costs; the measured healthy boosted-car NBT snapshot is 5,623 bytes at one periodic snapshot per second, with additional live scalar and wheel packets. Worn/hot states can serialize larger snapshots. This is not a measured total bandwidth or fleet capacity.

This baseline does not implement soft-body deformation, individual thermodynamic cylinders/chambers, Expert fasteners, engine-stand procedures, a comprehensive independent glass/latch/interior parts system, projected road lighting or LODs. Several service nodes share assembly geometry. Torque curves and compression tests are calibrated gameplay models. Expanding these should continue from the same persistent state, not add a parallel health model.

## Changed files

| Area | Main files |
|---|---|
| Persistent mechanics and simulation | [sim sources](../sim/src/main/java/com/photonspark/sparkmotors/sim/): `PartInstance`, `ComponentSlot`, `MechanicalState`, `CircuitPhysics`, `MechanicalCapabilities`, `WheelDynamics`, `TransmissionPhysics`, `VehicleDynamics`, `EnginePhysics`, `EngineBuild`, `InstrumentReadings`, `VehicleAudio` |
| World behavior, service and synchronization | [CarEntity.java](../src/main/java/com/photonspark/sparkmotors/entity/CarEntity.java), [CarPackets.java](../src/main/java/com/photonspark/sparkmotors/net/CarPackets.java) |
| Item/crafting state | [item sources](../src/main/java/com/photonspark/sparkmotors/item/): `MechanicalData`, `EngineItem`, `EngineCraftingRecipe`, `CarCrateItem`; registry integration in `AutoPropulsionAge` |
| Client presentation | [client sources](../src/main/java/com/photonspark/sparkmotors/client/): `CarAudio`, `CarClient`, `CarMesh`, `CockpitInstruments`, `GarageScreen`, `ServiceScreen`; old single-loop controller removed |
| Native and pure tests | [GameTests and client harnesses](../src/gametest/java/com/photonspark/sparkmotors/gametest/), [JUnit tests](../sim/src/test/java/com/photonspark/sparkmotors/sim/), `tools/run_multiplayer_test.ps1`, `MechanicsBenchmark` |
| Blender and resources | `tools/build_service_geometry.py`, runtime/workbench exporters, `assets/engine_workshop.blend`, packaged mesh, service recipes/icons/translations, 48 mono OGGs and explicit sound definitions |
| Evidence and delivery | `docs/verification.json`, mechanical/audio/geometry reports, native screenshot galleries, guides, `downloads/autopropulsion-age-0.4.0-alpha.jar`, `downloads/SHA256SUMS.txt` |
