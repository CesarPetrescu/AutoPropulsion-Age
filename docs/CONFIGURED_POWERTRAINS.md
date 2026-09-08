# Configured powertrains — 0.7.0

The same immutable `MechanicalState` drives torque routing, compression, heat, service inventory and renderer visibility. The car has 136 possible service paths; only the installed powertrain/family/layout paths are active. Catalog positions and old item IDs remain stable. This is a component simulation with calibrated values, not an OEM mechanical model.

## Driving and converting

Use **G → Drive**. Park, switch off, exit and raise the service jack. Hover a layout to see the required parts. Survival conversions consume eight iron ingots and the actual components for mounts introduced by the new layout. Removed components return to inventory with their identities and condition. Shared missing mounts remain missing; repair the current torque path first. The server stages the whole inventory transaction before applying it. Repeating the installed preset costs nothing; AWD 20–80% center-split tuning is free while stopped.

| Path | RWD | FWD | AWD |
|---|---|---|---|
| Combustion | Clutch, gearbox, propeller shaft, rear differential and rear CVs | Clutch, front transaxle/final drive and front CVs | Gearbox, transfer, propeller shaft, both differentials and all CVs |
| Electric / series hybrid | Rear motor, inverter, reduction, differential and CVs | Front motor, inverter, reduction, differential and CVs | Separate drive units on both axles; same total rated vehicle torque/power |

Open differentials lose axle drive with a broken output. Limited slip has bounded bias; locked diffs can retain the other connected output. Missing front EV components do not turn off the rear drive unit. Failed shared HV wiring/contactors interrupt both. The differential mode remains a vehicle-wide workshop setting. This does not model every planetary gear or individual differential tooth.

Service crafting patterns are unique even when mirrored horizontally, so the workbench resolves the intended component. Existing item IDs are retained.

## Actual engine repairs

**G → Service → Parts** lists applicable mounts and any stored legacy spares. Open the hood for internals. Select a part, use **Focus part** and **Cutaway view**, then remove/install it. Selected internal geometry remains visible through the cutaway. Removed items preserve serial, wear, damage, faults and temperature. Each cylinder has separate piston, rings, bearing and valves; each rotor has housing/rotor assembly, seals and bearing. Rotary engines use an eccentric shaft instead of piston/cam failures.

Use the compression tester with the engine stopped, hood open and a working starter supply. Each cylinder/rotor reports its own compression. Oil starvation wears actual bearings; severe starvation can seize them. A new bearing does not repair the oil pump/feed, refill oil, or clear history. Individual repairs restore the affected contribution. The existing internal rebuild consumes materials and repairs installed internal pieces; it does not create missing pieces or repair an external cause.

The Engine tab removes an internal kit as a bundle containing its actual child parts. Crafting an upgrade preserves those children and updates the kit specification. Converting a **used** whole engine into a different family preserves the original component instances as reusable spares; newly required family mounts can be empty and must be fitted. Fresh unversioned engines receive a complete family-appropriate set when first installed. Service access is currently grouped at the hood/jack level; an expert fastener/engine-stand procedure remains future work.

## Electric and hybrid service

Craft the appropriate vehicle crate; **G → Electric** shows the rated motor curve, pack state and each installed axle's motor/inverter temperature. **Service parts** opens the actual removable components. The battery EV radiator is serviced directly in the Parts view; open the hood, and carry the next radiator variant to use **Fit next radiator**. Raise the jack and switch READY off for drive-unit work; disconnect the charge cable before HV service.

Regen requires a working motor/inverter/output path, wheel contact, rotation and battery acceptance. Missing friction pads or brake fluid does not disable healthy regeneration. Airborne wheels do not recover energy; the handbrake excludes rear regen. Friction braking remains necessary for the final stop and when regeneration is unavailable.

Motor copper/conversion losses heat the individual motor and inverter; cooling uses coolant quantity and pump capability, with heat transferred into the radiator circuit. Thermal limits reduce that unit's output. The 12 V converter can recharge the accessory battery only when the traction system paid for available accessory power. Missing contactor/HV harness blocks READY and charging before the charger debits energy. Coolant, fuel, batteries and used parts are not refilled by storage or reinstallation.

The seven generator engines retain their individual crank torque curves. The series controller uses family-dependent governed RPM and bounded efficiency: I4 3,200; V6 2,600; flat-four 3,000; rotary 4,000–4,450 RPM targets. These are fictional calibration targets, not guaranteed measured operating RPM. Hybrids remain series hybrids: the engine supplies electricity, not a mechanical wheel connection. No parallel/power-split architecture is claimed.

## Migration and evidence

Network protocol **8**, vehicle save schema **7**, mechanical item schema **2**. Matching client/server builds are required. Version 1 components migrate once: old identities remain intact and new children receive deterministic identities with inherited condition. Removed version 2 slots are never filled by migration, item placement or reload. Inactive old parts remain recoverable spares. Vehicle conversion recipes manufacture only newly introduced topology from their ingredients and preserve shared missing/used mounts.

Required CI runs the simulation suite, actual dedicated GameTests, native UI/matrix/handling/electric/mechanics clients, two-client trading, ElectricalAge compatibility, the independent energy audit and regeneration/geometry checks. Downloaded releases include their fresh logs, screenshots, provenance and checksums. See [CI](CI.md) for the gate and [Windows development](../DEVELOPMENT.md) for commands. The QA branch's older failures are historical evidence, not a claim that the current build failed those same tests.

The geometry check uses exact exported triangles for all configured paths, outer body clearance, seven family bindings and hybrid engine/drive-unit clearance. Native renderer checks remove each detailed component and require its geometry to disappear across 105 type/layout/family combinations. Intended shaft/housing joints are excluded from non-mating intersection checks. Physics is rigid-body/contact-patch based; there is no soft-body deformation, full rollover or OEM acoustics/efficiency model. Listening quality, other modpacks and remote network conditions still need separate playtesting.

## Blender renders

These views show the exported runtime drive-unit geometry and the principal battery envelope, with the stock body. They are Blender inspections, not gameplay screenshots. The editable derived scene is [drivetrain_workshop.blend](../assets/drivetrain_workshop.blend). The original modular kit is preserved.

| Layout | Combustion | Battery electric | Plug-in series hybrid |
|---|---|---|---|
| RWD | ![RWD combustion](drivetrain-review/combustion-rwd.png) | ![RWD electric](drivetrain-review/electric-rwd.png) | ![RWD hybrid](drivetrain-review/series-hybrid-rwd.png) |
| FWD | ![FWD combustion](drivetrain-review/combustion-fwd.png) | ![FWD electric](drivetrain-review/electric-fwd.png) | ![FWD hybrid](drivetrain-review/series-hybrid-fwd.png) |
| AWD | ![AWD combustion](drivetrain-review/combustion-awd.png) | ![AWD electric](drivetrain-review/electric-awd.png) | ![AWD hybrid](drivetrain-review/series-hybrid-awd.png) |
