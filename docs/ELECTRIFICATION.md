# Electric and series-hybrid vehicles (0.4.0 alpha)

Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21. Client and server must use the same mod version (network protocol 4). Existing combustion sedans retain their original drivetrain; old saves do not silently convert into EVs. Back up a world before testing an alpha.

## Vehicles

| Crate | Architecture | Nominal pack | Usable design capacity | Motor peak | Generator | Plug charging |
|---|---|---:|---:|---:|---:|---|
| Series Hybrid | Engine-generator, electric traction | 220 V | 1.8 kWh | 80 kW / 220 Nm | 40 kW | No |
| Plug-in Series Hybrid | Engine-generator, electric traction | 400 V | 18 kWh | 120 kW / 280 Nm | 55 kW | 7.2 kW onboard / 30 kW DC |
| 400 V Electric | Battery-electric, fixed reduction | 400 V | 60 kWh | 160 kW / 310 Nm | None | 11 kW onboard / 120 kW DC |
| 800 V Electric | Battery-electric, fixed reduction | 800 V | 85 kWh | 240 kW / 380 Nm | None | 22 kW onboard / 250 kW DC |

These are fictional, calibrated vehicle specifications, not manufacturer data. Pack terminal voltage varies with state of charge, internal resistance, temperature, current and health. A nameplate charging limit is not a guarantee of delivered power. Pack current limits and charge taper also apply.

The existing angular sedan, doors, paint, hood, wheels and garage remain. Electric variants replace the engine bay with a motor, reduction housing, finned inverter, coolant header and orange HV cables. Battery enclosures and protection rails sit under the floor. Hybrids retain the fitted combustion engine and add a generator and electric axle. These authored runtime meshes do not overwrite the original Blender kit. Charger blocks use four distinct native Minecraft block models.

## Driving and service

Place the matching crate. Creative inventory crates start at 65% SOC for evaluation; a newly crafted survival battery is empty. A converted crate preserves the input battery's actual stored energy, temperature, health and throughput. Replacement battery recipes have different tier ingredients. The recipe book supplies exact crafting layouts.

Use **R** for READY, **W** for drive, **S** for blended braking and **Z** for reverse while stopped. The EV has a fixed 9.1:1 reduction, not an automatic combustion gearbox. Open **G → Electric** for battery SOC/voltage/current, thermal condition, motor/inverter temperature, regen and generator output. Positive pack power means discharge; negative means charging. Charging station input is a separate value because conversion and battery losses consume energy.

Hybrids are explicitly **series hybrids**, not parallel hybrids or a planetary power-split transmission. The engine drives a generator, never the wheels directly. AUTO starts/stops it with SOC hysteresis (HEV 45–65%, PHEV 18–30%). ELECTRIC ONLY forbids engine generation. CHARGE SUSTAIN targets a fixed 55–65% band; it is not a latch of the current SOC. The existing installed engine's simulated shaft output limits generator output, and generation consumes fuel. Missing/failed engines cannot generate. A damaged engine does not prevent battery-only travel while the pack can supply traction.

For pack service: park, switch READY off, unplug, open the hood fully, carry one matching traction pack and select **Replace battery pack** on Electric. The incoming pack is consumed and the old pack is returned, with both packs' condition and energy preserved. The service action is server-authoritative and subject to vehicle ownership and distance checks. The complete pack is the service unit in this alpha; individual cells, inverter swaps and contactor replacement are not implemented.

## ElectricalAge charging

The optional integration uses the public `mods.eln.api.v1.electrical.ElectricalIntegration` API, pinned and tested against companion commit `cb11383d2d8266b6876ad1b1629cadde0d2c1706`. Install ElectricalAge's 1.21.1 NeoForge JAR and **Kotlin for Forge 5.12.0** on both sides. No hard dependency is required to load AutoPropulsion by itself.

A charger registers a native grounded ELN circuit load at its block position. Connect an ELN power cable directly to the charger; no FE converter, exporter or imaginary FE-to-joule ratio is involved. The load has a real voltage, current and power demand in ELN's solver. Actual power is integrated at ELN's electrical timestep into a small 100 ms input buffer. Vehicle charging debits that finite buffer. Energy outside the voltage window or above buffer capacity is rejected/dissipated, not duplicated. Unloading/removing a charger unregisters its electrical process and graph node.

| Charger | Nominal ELN input | Maximum input current | Maximum input power | Conversion efficiency | Vehicle-side path |
|---|---:|---:|---:|---:|---|
| Workshop | 48 V | 20 A | 0.96 kW | 90% | Regulated onboard charger |
| Wallbox | 240 V | 30 A | 7.2 kW | 93% | Regulated onboard charger |
| Rapid DC | 480 V | 125 A | 60 kW | 95% | Regulated traction-pack DC |
| Ultra DC | 3,200 V | 100 A | 320 kW | 96% | Regulated traction-pack DC |

**Input voltage is not battery voltage.** The power converter supplies the selected pack's DC bus. The 3,200 V input is never applied directly to a 400/800 V battery. Values represent ELN's scalar circuit simulation; the integration does not simulate three-phase AC, RMS waveforms, CCS protocol or mains wiring regulations. The current port's standard generation is 480 V, large generation 3,200 V, and its storage includes 48 V. Legacy 50/200/800 V constants and hidden legacy cables still exist; they are not a description of all current equipment.

The input window is 80–110% of nominal. Wrong voltage, a dead source, supply droop, a disconnected cable, a hot battery and a full/target-SOC battery stop or restrict charging. Choose ELN conductors and transformer/source capacity for the actual current and power, not just a matching voltage label. For example the Rapid charger can demand 125 A; a thin cable may overheat in ELN's own wire thermal simulation. A higher tier cannot override a vehicle's onboard charger, DC limit, battery current limit, temperature limit or taper.

Pair deliberately: use a **Vehicle Charging Cable** on the charger, then on an owned, stationary EV/PHEV within six blocks. The link is dimension-checked and one charger serves one vehicle. READY is blocked while connected. Sneak-right-click the charger to unplug. Right-click it to read measured input voltage, power, accumulated input energy and status. Connections are deliberately not restored after a save/reload or endpoint unload; reconnect both endpoints explicitly. No forced chunk loading or proximity auto-charging is used.

The default target is 80%; the Electric page switches 80/100%. Charging tapers above 80%. Below 5 C the supplied electricity first powers a pack heater; cold cells are not given free charge. At 55 C charging cuts out. Empty packs cannot provide free launch torque. Regeneration decreases near full SOC, at low speed and under battery limits; friction braking remains available.

A clearly labelled **creative test supply** can be toggled by a creative player sneak-right-clicking an unpaired charger. It is unlimited fixture power, not an ElectricalAge network and not a survival energy source. Standalone native smoke tests use that fixture. Companion integration tests must use real ELN cables and a native circuit, not this fixture.

## Simulation boundary

The server advances traction at four 12.5 ms substeps per tick. Energy uses joules, power watts, time seconds and capacity kWh (`1 kWh = 3,600,000 J`), with no accelerated recharge multiplier. The battery is a lumped Thevenin model with SOC-dependent OCV, resistive sag/heat, current limits, temperature derating, charge taper and throughput-based aging. Motor/generator maps are calibrated approximations. Midpoint road work accounts for energy required to launch from rest; regenerative energy is bounded by actual braking work and pack acceptance. Conversion losses and fuel-backed generation are accounted separately.

Not implemented: per-cell electrochemistry, cell imbalance, thermal runaway/fire propagation, isolation testing, a 12 V accessory battery/DC-DC subsystem, physical contactor timing, parallel/power-split hybrids, vehicle-to-grid, an AC phase solver, licensed/OEM motor maps or EV-specific motor sound recordings. These limitations must not be presented as fully simulated systems.

## Reproduce verification

```sh
./gradlew build :sim:test
./gradlew -PwithGameTests runGameTestServer
./gradlew -PwithGameTests runClientElectricSmoke
./gradlew -PwithGameTests runClientSmokeQuick
# Optional companion tests: supply a compiled 1.21.1 ElectricalAge JAR.
./gradlew -PwithGameTests -PelnJar=/absolute/path/ElectricalAge.jar runGameTestServer
./gradlew -PwithGameTests -PelnJar=/absolute/path/ElectricalAge.jar runClientElectricSmoke
```

For headless Linux use Xvfb with software Mesa/OpenAL, as in the Actions workflow. The standalone invariant runner is also executable with javac and has no Minecraft dependencies. Check explicit native PASS files and the `ELN_CHARGER_CIRCUIT_PASS` marker, not only a Gradle exit status. Native captures belong in `run/screenshots`; an actual ElectricalAge companion run states `ELN=true` in its electric smoke result. Test sources, fixtures and test-world structures are excluded from the released mod JAR.

Verification results are recorded in the pull request and CI artifacts. Until the new feature run completes, baseline test results are not evidence that electrification passes.
