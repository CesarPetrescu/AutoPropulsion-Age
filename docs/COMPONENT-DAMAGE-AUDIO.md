# Component damage, audio and inspection (0.3.0-alpha feature preview)

This change targets Minecraft 1.21.1, NeoForge 21.1.249 and Java 21. Both client
and server must use this version: the network protocol is now version 3.
The existing README screenshots and checked-in 0.2.0 download are historical;
build this branch or use its validation artifact for the changes below.

## Playing

Open **G → Inspect**. The list shows each component's remaining condition,
wear, impact damage and individual repair cost. Green means healthy, amber
means degraded, red means failed. Missing parts are marked absent and cannot
be repaired. Use the arrows or scroll the list to reach all 26 components.

Drag the car preview to orbit horizontally and vertically. Scroll over the
preview to zoom. The Inspect tab offers a body cutaway and a condition overlay;
these are inspection-only views and do not remove inventory parts. The Engine
tab still has its dedicated internal cutaway. Orbit and zoom work on other tabs
as well. Damage also changes component tint in the world, distorts damaged body
panels in a bounded way, flattens badly damaged tire models, and emits smoke or
steam from damaged/hot running engines. This is not a soft-body crash solver.

The driving HUD adds temperature, boost and overall condition, plus the name
of a critically damaged component. The overall percentage is only a summary:
a single failed engine component can stop the engine even at a high average.
The Tuner curve is explicitly a healthy-build estimate, not a damaged live dyno.

## What is independent

There are **26 logical service components**, not 471 independently simulated
Blender meshes: frame; front/rear/left/right body; engine block; intake; fuel;
ignition; cooling; internals; induction; transmission; four tires; four brakes;
four suspension corners; exhaust/muffler. Piston rings, each rotor seal, valves,
individual fuel injectors and bearings remain grouped into service assemblies.

Each stores separate floating-point wear and impact damage, with remaining
condition `max(0, 100 - wear - damage)`. Wear advances in simulation time, not
wall-clock time or offline time. A parked, switched-off car does not wear.
Normal wear is slow; tire slip, braking, high loads, cold revving and overheating
accelerate the appropriate component's wear. Sport mechanical parts generally
wear more slowly; sport tires do not get a blanket durability advantage.
These are gameplay calibrations, not manufacturer durability data.

Front collisions primarily hurt front body, cooling and engine; rear collisions
hurt rear body and muffler; side collisions affect that side's body and wheels;
hard landings affect suspension, tires and transmission. Severity grows with
squared impact speed above a low-speed threshold, is bounded, and collision
sounds/damage have a short contact cooldown. Contact direction uses the car's
local coordinates and blocked movement; it is not a detailed contact manifold.

Engine block, internals, ignition and fuel health reduce available power;
critical failures prevent ignition. Transmission damage reduces delivered
power, cooling damage raises operating temperature, and a failed turbo/blower
loses its boost contribution. Tire damage reduces grip; brake damage extends
stopping distance; suspension damage reduces steering response and asymmetric
wheel/suspension damage adds pull. The existing longitudinal/bicycle vehicle
model remains: there is no independent per-wheel force solver, ABS simulation,
physical puncture pressure model, deformable chassis or fluid leakage system.

## Repair and inventory integrity

Park and switch off the engine before servicing. Open the hood fully before
engine repairs. The Inspect repair button affects **only its selected part**.
The Car tab's **Repair worst** affects only the worst installed component.

| Component | Iron ingots per full repair |
|---|---:|
| Frame / engine block | 8 |
| Engine internals | 6 |
| Transmission | 5 |
| Turbo / supercharger | 4 |
| Fuel, cooling, each suspension corner | 3 |
| Body section, intake, ignition, each brake, muffler | 2 |
| Each tire | 1 |

Creative servicing does not consume items. Survival checks available inventory
before changing anything. Ownership, distance, action-rate limits, parked state
and hood access are validated on the server, including forged repair requests.
Healthy or absent components cannot consume repair materials.

Removed items retain their component condition. A wheel set preserves all four
corners, and an engine preserves its block plus installed service conditions,
parts and temperature. A worn same-grade part can be replaced with a fresh one;
refitting the worn item does not heal it. Upgrade crafting and crafted car
crates preserve donor condition as well. Item tooltips disclose damaged parts.

Older cars with only a global Health value migrate that damage conservatively
across installed components; wrecks do not become healthy. Named, versioned NBT
keys permit new parts without reinterpreting old ordinal data. Existing
assembly bit positions are preserved; exhaust was appended as the seventh slot.
Old cars receive a stock muffler, while a new-version intentionally removed
muffler stays removed when reloaded. Back up worlds before testing the alpha;
downgrading to 0.2.0 is not a supported condition-preserving migration.

## Sounds

There are **58 original, procedurally synthesized mono Ogg Vorbis assets**:
42 combustion loops (seven families × stock/sport/open exhaust × low/high RPM)
and 16 supporting loops/events. No game recordings or manufacturer recordings
were reused. This is a stylized first audio pass, not a claim of recorded-engine
fidelity or final listening polish.

All seven existing families are covered: inline-four, V6, flat-four and one to
four rotors. Two RPM bands crossfade and pitch smoothly; throttle changes load
gain. Sport engines and performance intake change the intake layer. The new
Garage exhaust slot accepts stock/sport mufflers or removal for an open pipe.
Muffler choice changes waveform harmonic content as well as gain, independently
of engine grade. A nearly failed muffler sounds like an open exhaust.

Turbo and supercharger are exclusive layers. Turbo noise follows boost;
throttle lift under boost triggers a cooldown-limited blow-off sound. The
supercharger follows RPM/load and never produces a turbo blow-off event. Loss
of induction condition reduces the audible contribution along with boost.
There is no turbo shaft-speed or compressor-map simulation.

Tire roll continues while coasting with the engine off. Skid follows estimated
traction saturation or handbrake use; rolling/skid/brake sounds require ground
contact. Worn brakes squeal and severely damaged brakes grind. Other layers
include intake, damage rattle, starter, shutdown, gear shifts, impacts, tire
bursts, engine failure and part breakage. Break events trigger on transition to
failure, not continuously while a component remains failed.

The client manages at most six nearby cars and eight loops per car. It stops
sources when cars disappear or the world changes and retries interrupted audio
sources after a grace interval on resource/audio reload. The common/server
code does not load client audio classes. All events have subtitles.

## Rebuild and verification

```sh
# Java 21, Python 3, ffmpeg/ffprobe required for asset regeneration/validation.
python3 tools/generate_vehicle_audio.py
python3 tools/validate_vehicle_audio.py
./gradlew build -PwithGameTests
./gradlew runGameTestServer -PwithGameTests
./gradlew runClientSmoke -PwithGameTests
```

Normal `./gradlew build` uses the committed OGG files, so players/builders do not
need ffmpeg unless they regenerate or validate audio. The generator has stable
PCM synthesis and a manifest of PCM hashes, levels, durations and format.
Vorbis container bytes need not be identical across ffmpeg versions.

The dependency-free local smoke gate can also be run without Minecraft:

```sh
mkdir -p /tmp/vehicle-check
javac -d /tmp/vehicle-check $(find sim/src/main/java -name '*.java') tools/VehicleSystemSelfTest.java
java -cp /tmp/vehicle-check VehicleSystemSelfTest
```

Local result at implementation time: **654,200 assertions**, **13,608 audio
states**, **1,092 damaged-drivetrain scenarios**. All 58 streams decoded as mono
Vorbis at 22,050 Hz; the highest decoded sample peak was approximately 0.820,
with no clipping detected. This validates resource integrity, not subjective
audio quality.

Additional automated gates are JUnit unit/matrix tests, dedicated-server
GameTests for persistence, repairs, ownership, swaps, crafting and registrations,
and the native client smoke harness for actual GUI/network/render/audio-source
integration. The client harness records screenshots and a PASS/FAILED text file.
See the PR's run artifacts for results on the final commit. Do not infer a passed
in-game test merely from the presence of the harness or this document.

Remaining playtesting: listening and gain tuning on real speakers/headphones,
separate-machine multiplayer, modpack interoperability, long-session save/reload
and audio-device switching, and vehicle handling/damage balance.
