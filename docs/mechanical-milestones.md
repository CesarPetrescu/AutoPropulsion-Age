# Connected mechanical system

Work starts from `2bbe5ea`, on branch `mechanical-components`. The supplied audit examined `83b91cd`; its independent crank, flywheel, clutch and turbo-spool concerns partly predate 0.3. The existing simulation, garage, registry IDs and ownership protocol are being extended. No separate downloadable handoff was attached; the pasted specification is the implementation reference.

## M0 — reproduced baseline

Fresh runs on 8 September 2026: 22 simulation JUnit tests and all 18 required dedicated-server GameTests passed. The hidden native `runClientSmokeQuick` also passed in 3m11s, with real garage buttons/packets, driving, paint, tuning, seven I4 induction layouts and all 42 hardware choices. These are results from this run, not historical repository claims. Test logs are local under `.codex-reference/mechanics-m0-*`.

Confirmed source defects: service/handbrake input collapsed; no contact requirement on braking; absent brakes use the stock branch; broad assembly drive gate damps rolling and prevents otherwise runnable engines; generator overwrites authored sound definitions. The 471 Blender roots are visual geometry, not 471 independently simulated components. Workshop scenery is not automatically functional equipment.

## M1 — persistent component foundation

Immutable component instances have UUID identity, item specification, wear, structural damage, fault flags, stored pressure/charge and temperature. Stable paths distinguish all four wheel corners. NeoForge's registered `sparkmotors:mechanical_state` data component supplies persistent and network codecs. Vehicle data version 4 migrates old aggregate engine damage into the internal assembly once; existing version 1–3 part layouts remain supported.

Single components, generic assemblies, whole engines, engine family conversions, used-part upgrades and crafted car crates carry their component contents. Empty stored mounts stay empty. Engine bundles carry remaining oil/coolant rather than refilling on installation. The garage's Service view exposes inventory-backed removal/installation, hood access, a jack, orbit/pan/zoom and underside viewing. Deeper behavior follows in M2/M3; listing a mount is not evidence that its failure is implemented.

Fresh M1 checks: 22 simulation tests and 22 server GameTests passed, including four new tests for typed item/network/save round trips, used wheel-assembly crafting, engine/crate conversion and ownership/access. The new screen was subsequently verified in the M2 native client run.

## Next milestone

M2: coolant circuit causality, pressure testing, conserved refill transactions, localized collision damage, independent tire/brake behavior and a complete playable repair scenario. M3–M6 remain unfinished.

## M3 � connected powertrain, oil and electrical systems

The starter now physically spins the crank before combustion catches; a flat battery, failed starting circuit or seized assembly can prevent cranking. Ignition/fuel/compression faults can permit cranking without sustained running. Engine, transmission, rolling and braking capabilities are independent. Ancillary cooling/oil/exhaust removal no longer makes the combustion assembly disappear; running without them has the corresponding fluid/heat consequences.

Oil quantity, pump/filter/feed restriction and temperature produce pressure. Low-pressure running wears the actual engine and compressor; changing a turbo cannot repair its supply. Battery charge changes with starter/accessory demand and alternator/belt output. Clutch condition/heat changes torque capacity, gearbox/differential/shaft faults interrupt drive, shifts take finite time, and steering links, damping, bearings and corner travel influence motion. Engine families now have differing torque-curve shapes as gameplay calibrations. Rotary diagnostic vocabulary uses chambers, seals and ports.

Fresh checks: 35 simulation tests and all 25 server GameTests pass, including all 98 family/grade/induction layouts. The layout test now allows the starter to finish before its existing acceleration interval, and legacy test fixtures explicitly discard generated mechanical data before assigning legacy aggregate fields. The new server test reproduces a flat battery, missing oil pump, measured pressure loss, internal wear, targeted replacement and retained damage. The compression tool reports a grouped assembly estimate; individual cylinder/chamber internals are not separately simulated. Native client regression is running before M4.
