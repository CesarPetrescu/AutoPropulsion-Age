# Connected mechanical system

Work starts from `2bbe5ea`, on branch `mechanical-components`. The supplied audit examined `83b91cd`; its independent crank, flywheel, clutch and turbo-spool concerns partly predate 0.3. The existing simulation, garage, registry IDs and ownership protocol are being extended. No separate downloadable handoff was attached; the pasted specification is the implementation reference.

## M0 — reproduced baseline

Fresh runs on 8 September 2026: 22 simulation JUnit tests and all 18 required dedicated-server GameTests passed. The hidden native `runClientSmokeQuick` also passed in 3m11s, with real garage buttons/packets, driving, paint, tuning, seven I4 induction layouts and all 42 hardware choices. These are results from this run, not historical repository claims. Test logs are local under `.codex-reference/mechanics-m0-*`.

Confirmed source defects: service/handbrake input collapsed; no contact requirement on braking; absent brakes use the stock branch; broad assembly drive gate damps rolling and prevents otherwise runnable engines; generator overwrites authored sound definitions. The 471 Blender roots are visual geometry, not 471 independently simulated components. Workshop scenery is not automatically functional equipment.

## M1 — persistent component foundation

Immutable component instances have UUID identity, item specification, wear, structural damage, fault flags, stored pressure/charge and temperature. Stable paths distinguish all four wheel corners. NeoForge's registered `sparkmotors:mechanical_state` data component supplies persistent and network codecs. Vehicle data version 4 migrates old aggregate engine damage into the internal assembly once; existing version 1–3 part layouts remain supported.

Single components, generic assemblies, whole engines, engine family conversions, used-part upgrades and crafted car crates carry their component contents. Empty stored mounts stay empty. Engine bundles carry remaining oil/coolant rather than refilling on installation. The garage's Service view exposes inventory-backed removal/installation, hood access, a jack, orbit/pan/zoom and underside viewing. Deeper behavior follows in M2/M3; listing a mount is not evidence that its failure is implemented.

Fresh M1 checks: 22 simulation tests and 22 server GameTests passed, including four new tests for typed item/network/save round trips, used wheel-assembly crafting, engine/crate conversion and ownership/access. Client verification of the new Service screen is pending the complete M2 workflow.

## Next milestone

M2: coolant circuit causality, pressure testing, conserved refill transactions, localized collision damage, independent tire/brake behavior and a complete playable repair scenario. M3–M6 remain unfinished.
