"""Refresh the 0.3 guide and README while preserving both generated galleries and original model kit."""
from pathlib import Path
import json
repo=Path(__file__).resolve().parent.parent
catalog=json.loads((repo/'docs/powertrain-catalog.json').read_text())['slots']
readme=repo/'README.md';old=readme.read_text(encoding='utf8')
galleries=''
for name in ['ENGINE','HARDWARE']:
    start=f'<!-- {name}-GALLERY-START -->';end=f'<!-- {name}-GALLERY-END -->'
    if start in old:galleries+=old[old.index(start):old.index(end)+len(end)]+'\n\n'
tail=old[old.index('## Editable model kit'):].replace('autopropulsion-age-0.2.0-alpha.jar','autopropulsion-age-0.3.0-alpha.jar').replace('21 open-hood configurations.','49 open-hood layouts plus 42 isolated hardware scenes.')
intro='''# AutoPropulsion Age

**Playable alpha 0.3.0 — Minecraft Java 1.21.1 · NeoForge 21.1.249 · Java 21.**

Drive a modular sedan and build its engine under an opening hood. Choose **42 hardware items across ten engine slots**, with **seven engine families and seven induction configurations**. Parts change the actual geometry and the simulated response: turbo lag, blower drive load, flywheel inertia, fuel capacity, cams/ports, cooling and oil systems all matter.

**[Download the playable mod JAR](https://github.com/CesarPetrescu/AutoPropulsion-Age/raw/refs/heads/main/downloads/autopropulsion-age-0.3.0-alpha.jar)** · [Installation and controls](docs/PLAYING.md) · [Engine workshop](docs/ENGINE-WORKSHOP.md) · [Build and tests](docs/DEVELOPMENT.md) · [Checksums](downloads/SHA256SUMS.txt)

Replace an older AutoPropulsion JAR in your NeoForge instance's `mods` folder. Take a **Sedan Crate** from the **AutoPropulsion Age** Creative tab and place it on open, flat ground. Right-click, press **R** to start, then **W** to drive. **A/D** steer, **S** brakes, **Z** selects reverse while stopped, **G** opens the garage and **Shift** exits. Hold **C** to disengage the clutch; **C + W** free-revs the engine. Existing 0.1/0.2 cars and engine items migrate their installed components.

## Build the engine

Open **G → Engine → Open hood** while parked with the engine off. Browse the ten service slots with **More parts**, **Previous parts**, or the mouse wheel. Arrows select a named part; **Fit** installs it. Hover the button for its effects and requirements. Whole-engine stock/sport grades remain available alongside the individual hardware choices.

![Expanded workshop with a large-turbo inline-four](docs/screenshots/engine-i4-large-turbo.png)

| Engine slot | Hardware choices |
|---|---|
'''
for slot in catalog:intro+='| '+slot['title']+' | '+' / '.join(o['label'] for o in slot['options'])+' |\n'
intro+='''
The induction slot also supports natural aspiration. Street turbo, centrifugal, Roots and twin-screw kits require high-flow fuel and forged/billet internals. Large/twin turbos require return/race fuel, billet internals, upgraded cooling and ignition. High-compression internals are for naturally aspirated builds. Survival swaps consume the incoming item and return the old one; engines retain their parts, coolant/oil temperatures and wear through swaps, crafting and saves.

![Four-rotor twin-screw build under the hood](docs/screenshots/hood-rotor4-twin-screw.png)

## Test and tune

The crank and flywheel store angular momentum. A slipping automatic clutch couples engine speed to the wheels; hold C to disengage it. Turbo shaft speed and manifold pressure build over time, and a blow-off valve vents pressure on throttle lift. Blowers consume crankshaft power. Fuel capacity limits power and can produce a lean mixture under boost; lean running and excessive coolant/oil heat wear the engine. Oil hardware changes pressure and heat rejection. Cams/ports and exhausts trade low-end torque for high-RPM flow.

The **Live** page shows RPM, throttle, turbo speed, boost, AFR, coolant/oil temperatures, oil pressure, shaft torque, blower load and engine condition. Start the engine with its hood open and press **Rev test / 2s** to watch a timed test while the car remains parked. **Rebuild engine** restores wear for twelve iron ingots.

![Live diagnostics during a native engine rev test](docs/screenshots/powertrain-live.png)

The **Tuner** sets rev limiter, final drive and boost target. Hardware limits still apply. The graph estimates warm, healthy, steady-state output; the running engine additionally responds to lag, throttle, temperature and wear.

![Boost target and calculated power curve](docs/screenshots/powertrain-tuner.png)

## Drive and customize the car

The alpha includes automatic gears, reverse, collision handling, fuel, repairs, engine sound, eight paint colors, stock/sport car assemblies, opening doors/hood/trunk, one driving seat, seventy recipes and persistent ownership/configurations.

![Driving HUD with boost, AFR and oil temperature](docs/screenshots/alpha-driving.png)

![Garage with live car preview](docs/screenshots/alpha-garage.png)

![Paint customization](docs/screenshots/alpha-paint.png)

![Opening panels and car controls](docs/screenshots/alpha-car-controls.png)

## Verification and scope

The reproducible checks cover **1,835,008 populated hardware combinations**, **7,308 continuous driving/braking cases**, **98 native in-world engine layouts**, all **42 hardware item recipes and inventory transactions**, and all **49 rendered family/induction layouts**. Every hardware choice selects distinct vertex geometry in each engine family (**294 checks**). Blender clearance and selected triangle-intersection checks pass for all 49 layouts through five hood positions. See the exact results and test counts in the [verification record](docs/verification.json).

This is a playable alpha with calibrated gameplay physics. Engine families use shared reference torque curves with family scaling; combustion cycles, per-cylinder damage, full suspension force simulation, compound charging, nitrous, individual body-panel choices and liveries remain future work. Stock/sport still describes the six large car assemblies. The 42 new choices are engine hardware. [Detailed scope](docs/ENGINE-WORKSHOP.md).

'''
readme.write_text(intro+galleries+tail,encoding='utf8')
guide='''# Engine workshop — 0.3.0-alpha

The engine workshop has ten slots with 42 named hardware items, plus removal/natural aspiration. It supports Inline-4, V6, flat-four and one- through four-rotor engines. Each family keeps the original stock/sport base-engine grades. The seven induction layouts fit all seven families.

## Install and inspect

1. Park, switch off with R, open G → Engine, and open the hood fully.
2. Choose a family with the top arrows, then Fit stock or Fit sport. Survival needs that engine item.
3. Browse parts with More parts, Previous parts or scrolling. Each row shows the installed part. Its arrows browse choices; the center Fit button installs the selected choice. Hover for behavior and requirements.
4. Drag the engine preview to orbit. Inspect internals hides covers in the preview without changing inventory.
5. Fit supporting parts before a boost kit. The server rejects incompatible installations without consuming the item. Removal is allowed, but an incomplete engine cannot start.
6. Start it from Live and use Rev test / 2s with the hood open. This disengages the clutch and holds the brakes, then automatically releases throttle. The engine can also free-rev while driving with C + W.

Work requires the owner/operator, proximity, a parked car, an engine that is off and a fully open hood. Inventory transactions return the exact old item once. Whole engines retain all installed parts, coolant/oil temperatures and wear through item serialization, swaps, engine conversions and crafted sedan crates. Version 0.1/0.2 saves migrate the original six slots; the four new supporting slots start with OEM parts. Loading parks the car and resets RPM/boost; temperatures and engine condition persist.

## Hardware behavior

| Slot / option | Behavior |
|---|---|
'''
for slot in catalog:
    for o in slot['options']:guide+='| '+slot['title']+' / **'+o['label']+'** | '+o['description']+' |\n'
guide+='''
All nine supporting slots are required. Induction is exclusive: natural, street turbo, centrifugal, large turbo, twin turbo, Roots or twin-screw. Large/twin kits require fuel option 3/4, internal option 4 and cooling/ignition options 2–4. Other boost kits require fuel 2–4 and internal 2/4. Internal option 3 (high compression) is naturally aspirated only. Parts marked cams/ports use cam geometry for piston engines and port geometry for rotaries.

The named assemblies are selectable kits. They are not separate inventory items for every piston, valve or rotor seal. Internals inspection remains simplified, especially on V6 and flat-four cores. Some small upgrades add machining/bearing/fastener geometry to the common core; large visual changes come from engine family, intake, compressor and oil/cooling hardware. [Every hardware render](../README.md#every-new-engine-hardware-model).

## Engine physics and diagnostics

Simulation advances four 12.5 ms steps per Minecraft tick. It integrates crank angular velocity from shaft torque minus clutch load divided by engine/flywheel inertia. A two-inertia clutch model transfers torque with finite capacity; the gearbox shifts automatically. Throttle position has a hardware-dependent response time. The turbo rotor state and manifold pressure persist across steps, with different spool thresholds/time constants by size. Wastegate target caps pressure; throttle lift vents the manifold while shaft speed decays more slowly. Centrifugal boost rises with RPM; Roots and twin-screw units produce low-RPM boost and consume shaft power.

Injector capacity limits fuel delivery, raises AFR when insufficient, and reduces available torque. Sustained lean boosted operation and excessive oil/coolant heat reduce engine condition. Below 5% the engine stops and cannot restart. Rebuild engine requires twelve iron ingots, with the engine off and hood open; it restores wear without cooling it. Coolant output derates above 110 C, shutdown occurs at 130 C and restarting requires below 125 C. Oil variants change oil cooling and pump pressure. Oil pressure is a computed diagnostic; oil volume, leaks and lateral-G starvation are not yet simulated.

Live displays RPM, throttle/turbo speed, boost/target, AFR, coolant/oil temperatures, oil pressure, shaft torque, blower drive power and engine condition. The driving HUD shows boost, AFR and oil temperature. Tuner accepts limiter 4000–7000 RPM, final drive 2.8–4.8 and boost target 0.2–1.4 bar. A compressor's own pressure ceiling still applies. Apply tune saves limiter/final drive; Apply boost saves the pressure target. The estimated graph includes injector capacity and blower load, assuming steady boost, a healthy engine and 90 C coolant. It does not predict the transient lag or wear of a running engine.

These are deterministic gameplay models with calibrated values, not measurements of production engines or a thermodynamic combustion solver. Families scale the reference I4 curve. Individual cylinder/valve damage, manual gear selection, compound charging, nitrous, E85 maps, individual body panels and detailed suspension forces remain future work.

## Items and recipes

All seventy mod items are in the AutoPropulsion Age Creative tab. The original stock/performance item registry IDs remain valid. Each of the 42 engine hardware options has its own shaped survival recipe. Recipe JSON is under `src/main/resources/data/sparkmotors/recipe/`; [the compiled catalog](powertrain-catalog.json) maps names, slot numbers, item IDs and effects. Existing engine conversion recipes keep donor custom data. The standalone `sim` JAR is not a second Minecraft mod.

## Reproducible tests

All 4^9 supporting-part combinations × 7 induction choices are checked for compatibility and finite steady output: **1,835,008 builds**, **692,224 compatible** and **1,142,784 correctly blocked**. Missing-slot rejection and all ordered slot/option pairs are checked separately. The dynamic matrix exercises every option in each compatible induction baseline, all seven families, two grades and three tune bounds: **174 distinct baseline builds and 7,308 drive/brake cases**. This is targeted dynamic coverage, not a claim to drive all 1.8 million builds.

Dedicated Minecraft tests exercise all 98 family/grade/induction driving layouts, all 42 hardware recipes/transactions, 98 serialized-engine round trips, engine crafting/crates, migration, ownership, malformed requests, thermal/wear preservation, clutch free revs and the parked rev timer. The client harness drives actual GUI buttons and packets, checks exclusive compressor visibility, and captures every family/induction layout and every hardware choice. Blender checks all 49 layout envelopes, five hood positions, compressor/core and boost-plumbing/fixed-hardware intersections. Internal mating surfaces and all possible pairwise hardware collisions are outside that targeted geometric check.

[Verification record](verification.json) · [Build instructions](DEVELOPMENT.md) · [Hardware geometry identities](hardware-mesh-validation.json) · [Clearance report](engine-fit-matrix.json) · [Intersection report](engine-intersections.json)
'''
(repo/'docs/ENGINE-WORKSHOP.md').write_text(guide,encoding='utf8')
dev=repo/'docs/DEVELOPMENT.md';text=dev.read_text(encoding='utf8')
text=text.replace('0.2.0-alpha','0.3.0-alpha').replace('40 items/recipes','70 items/recipes').replace('all 21 engine layouts','49 engine layouts and 42 hardware models').replace('with 21 open-hood scenes','with 49 open-hood scenes and 42 isolated part scenes')
start=text.index('The committed ');end=text.index('\n\nResource generation',start)
text=text[:start]+'''The committed APA2 mesh contains **668 material/part batches and 266,436 triangles across all alternatives**, compressed to **3,206,115 bytes**. Most alternatives are mutually exclusive, and the in-world engine bay is culled when the hood is closed. Each selected hardware choice has distinct vertex positions, verified for every family. Runtime metadata selects family, service slot/variant and compressor. Body paint is tinted at draw time. Mesh data is cached on resource reload. See [runtime-assets.json](runtime-assets.json).

Run `tools/build_engine_workbench.py` through Blender MCP to create `assets/engine_workshop.blend` with 91 editable scenes. `tools/render_powertrain_hardware.py` renders the 42 isolated models. `tools/verify_powertrain_mesh.py` checks all 294 family/hardware geometry identities. `tools/build_hardware_gallery.py` creates the README hardware gallery; `tools/build_engine_gallery.py` uses the native Minecraft captures. The primary authoring source is the original kit plus `tools/build_powertrain_hardware.py` and `tools/export_engine_runtime.py`.''' + text[end:]
start=text.index('## Verified on')
text=text[:start]+'''## Verified on 2026-09-08

Tests use Java 21.0.11, Minecraft 1.21.1 and NeoForge 21.1.249. [verification.json](verification.json) records exact results and the release checksum. The [workshop guide](ENGINE-WORKSHOP.md#reproducible-tests) explains the matrix coverage. Test sources are committed; worlds, build output, dependencies and local logs are ignored.

The pure-Java suite covers exhaustive populated hardware compatibility, migration encodings, all slot-option pairs, 7,308 stateful drive/brake cases, turbo lag/BOV, wastegate limits, supercharger shaft load, flywheel inertia, throttle response, mixture/wear, cooling, oil pressure, limiter, reverse, grip and deterministic output. Native server tests cover the inventory/persistence/driver integration and all 98 engine layouts. The full client harness covers all 49 family/induction layouts and all 42 hardware choices; `runClientSmokeQuick` repeats the seven I4 layouts, all 42 hardware choices and the final live rev/tuning flows.

The simulation catalog can be exported with `java -cp sim/build/classes/java/main com.photonspark.sparkmotors.sim.PartCatalog > docs/powertrain-catalog.json` after `:sim:classes`. Resource generation reads that catalog. Re-run `tools/generate_game_resources.py` after hardware renders to refresh unique item icons. Normal builds use committed generated resources and do not require Blender/Python/ffmpeg.

```powershell
.\\gradlew.bat :sim:dyno --args="--family v6 --induction twin-turbo --part intake=3 --boost 1.1"
.\\gradlew.bat :sim:dyno --args="--family i4 --induction large-turbo --transient"
.\\gradlew.bat -PwithGameTests runClientSmokeQuick
```

The dyno shares the hardware curves and physics with the game. `--transient` outputs a ten-second stateful run: seven seconds of acceleration followed by throttle lift and braking. CSV includes RPM, road speed, boost, turbo speed, throttle, torque, AFR, oil readings, engine wear and blower load. Every CLI hardware selection is validated before the run.

State schema 3 uses ten three-bit slots; legacy six two-bit slots migrate without changing old choices. Engine item schema 2 uses the same mapping. Protocol 3 requires matching client/server mod versions. Engine wear and temperatures persist, while moving/rotating transient state resets on load. Simulation classes are included in the mod output and JAR; dedicated test/harness classes and the test track are excluded from releases.

The native screenshots are actual Minecraft captures. The isolated hardware images are Blender renders of the exact game geometry. These checks establish the implemented alpha behavior; other modpacks, separate-machine multiplayer, a full combustion solver and the remaining original specification are future work.
'''
dev.write_text(text,encoding='utf8')
print('Updated README, engine workshop and developer guide for 0.3')
