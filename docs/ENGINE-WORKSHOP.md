# Engine workshop — 0.3.0-alpha

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
| Intake / **Airbox** | Quiet airbox; balanced low-speed response. |
| Intake / **Cold-air** | Larger cone filter and smooth intake tube. |
| Intake / **Individual throttles** | Separate throttle butterflies; sharper response and high-RPM flow. |
| Intake / **Ram plenum** | Long runners and a large plenum; stronger mid-range. |
| Fuel system / **Port injection** | Injector capacity: 220 Nm per reference engine. Undersized injectors run lean under load. |
| Fuel system / **High-flow rail** | Injector capacity: 360 Nm per reference engine. Undersized injectors run lean under load. |
| Fuel system / **Return fuel rail** | Injector capacity: 520 Nm per reference engine. Undersized injectors run lean under load. |
| Fuel system / **Race injection** | Injector capacity: 760 Nm per reference engine. Undersized injectors run lean under load. |
| Ignition / **Coil pack** | Standard coil and plug energy. |
| Ignition / **Performance coils** | Higher-energy coil bank for boost. |
| Ignition / **CDI ignition** | Capacitor discharge with stronger high-RPM spark. |
| Ignition / **Multi-spark** | Multiple spark drivers and a separate control module. |
| Cooling / **OEM radiator** | Standard radiator and mechanical fan. |
| Cooling / **Aluminium radiator** | Thicker aluminium core; improved heat rejection. |
| Cooling / **Dual electric fans** | Two electric fans improve cooling while stationary. |
| Cooling / **Race radiator** | Large finned core and high-output fans for sustained boost. |
| Rotating assembly / **Cast assembly** | Cast rods / OEM seals; natural aspiration only. |
| Rotating assembly / **Forged assembly** | Forged rods / reinforced seals; street boost. |
| Rotating assembly / **High compression** | High compression / high-compression rotors; natural aspiration only. |
| Rotating assembly / **Billet assembly** | Billet crank and rods / race shaft and seals; high boost. |
| Forced induction / **Street turbo** | 0.65 bar; early spool. Requires high-flow fuel and forged internals. |
| Forced induction / **Centrifugal blower** | 0.65 bar; boost rises with RPM; belt drive consumes shaft power. |
| Forced induction / **Large turbo** | 1.40 bar; later spool and more lag. Requires race support parts. |
| Forced induction / **Twin turbos** | 1.15 bar; two smaller turbos. Requires race support parts. |
| Forced induction / **Roots blower** | 0.80 bar; immediate low-RPM boost with substantial belt load. |
| Forced induction / **Twin-screw blower** | 1.10 bar; positive-displacement boost with better compressor efficiency. |
| Exhaust / **Cast manifold** | Compact cast log and quiet muffler. |
| Exhaust / **4-2-1 headers** | Paired runners improve low and medium RPM torque. |
| Exhaust / **Equal-length tubes** | Individual equal-length pipes improve high RPM flow. |
| Exhaust / **Race collector** | Large collector: strongest high RPM flow, less low-end torque. |
| Flywheel / **OEM flywheel** | Flywheel inertia: 0.22 kg m2. Lower inertia revs faster and stores less launch energy. |
| Flywheel / **Light steel** | Flywheel inertia: 0.13 kg m2. Lower inertia revs faster and stores less launch energy. |
| Flywheel / **Aluminium flywheel** | Flywheel inertia: 0.065 kg m2. Lower inertia revs faster and stores less launch energy. |
| Flywheel / **Billet flywheel** | Flywheel inertia: 0.09 kg m2. Lower inertia revs faster and stores less launch energy. |
| Cams / rotary ports / **OEM cams / side ports** | Broad stock torque curve. |
| Cams / rotary ports / **Street cams / ports** | Mild cams / street ports; balanced road use. |
| Cams / rotary ports / **Race cams / bridge** | Long-duration cams / bridge ports; trades low-end torque for top-end. |
| Cams / rotary ports / **High-lift / peripheral** | High lift / peripheral ports; pronounced high-RPM power band. |
| Oil system / **Wet sump** | Standard pump and sump. |
| Oil system / **Baffled sump** | Baffles and higher-pressure pump. |
| Oil system / **Oil cooler** | External oil cooler and braided feed lines. |
| Oil system / **Dry sump** | Scavenge pump, external oil tank and cooler; strongest pressure support. |

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
