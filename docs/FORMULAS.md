# Implemented formulas and calibration

These equations describe the current source, not an assertion that every formula in the original handoff was implemented. Distances are metres, mass kilograms, torque Nm, temperature kelvin and time seconds. The adapter treats one Minecraft block as one metre. Boost values are bar gauge; state RPM is converted where angular units are needed.

## Engine production

The current calibrated indicated torque is:

```text
eta = 1 - compression_ratio^(-0.35)
eta_rotary = 0.85 * eta
charge_temperature_K = 293.15 + 32 * boost_bar
charge_density_ratio = (1 + boost_bar) * 293.15 / charge_temperature_K
T_indicated = 198 * displacement_L * VE(rpm) * eta * density_ratio * power_multiplier
T_brake = max(0, T_indicated - 10 - 0.0018 * rpm)
P_kW = T_brake * rpm * 2*pi / 60000
```

The `198` coefficient is a gameplay/reference calibration, not a universal physical constant. VE is an immutable endpoint-clamped piecewise-linear table. A race cam shifts the reference curve by 1,000 RPM and applies an additional low-speed penalty below 2,500 RPM. Rotary displacement is explicitly torque-equivalent in this prototype; it must not be confused with a manufacturer's advertised swept displacement.

Reference 2.0 L naturally aspirated results from the actual JVM suite: **179.642 Nm at 4,200 RPM** and **109.020 kW peak power**. Tolerances are 170–190 Nm and 105–125 kW, matching the supplied P0 calibration band. Reference CLI acceleration is 8.15 s to 100 km/h with scripted shifts, road grip 1, 1,080 kg, a 3.9 final drive and the assisted takeoff model. This is not an in-game or real-vehicle validation.

## Fuel: explicit unit correction

The supplied pre-implementation spec mixed kW and fuel lower-heating-value units in its fuel expression. The implementation explicitly uses **43,000,000 J/kg** and **0.745 kg/L**:

```text
fuel_L_per_s = idle_allowance_L_per_s
             + max(P_kW, 0) * 1000 / (eta * LHV_J_per_kg * density_kg_per_L)
idle_allowance_L_per_s = 0.00018
```

The factor 1,000 converts kW to W; it is not division by 1,000 when LHV is in J/kg. A unit regression test prevents a million-fold scaling error. The idle allowance is a model choice, not a fuel-economy prediction.

## Drivetrain and body

Forward ratios are 3.25, 1.95, 1.34, 1.03 and 0.82; reverse is -3.1. Driveline efficiency is 0.89, wheel radius 0.32 m, wheelbase 2.52 m and mass 1,080 kg.

```text
wheel_force = engine_torque * gear_ratio * 3.9 * 0.89 * clutch_coupling / wheel_radius
traction_limit = mass * 9.81 * surface_mu * 0.63
aero_force = 0.5 * 1.225 * 0.62 * speed * abs(speed)
yaw_rate = speed / wheelbase * tan(steer * 0.49) / (1 + speed^2/625)
```

The final yaw attenuation and 0.63 driven-load factor are prototype simplifications. Launch assistance uses a coupled RPM target instead of solving a stiff clutch/shaft constraint. These choices must be replaced or calibrated before claiming realistic handling. Clutch input 0 means engaged and 1 means disengaged; the spec's ambiguous pedal convention is made explicit here.

Braking approaches zero speed without numerically crossing through it into reverse. Reverse selection is explicit. The current speed caps are -18 and +60 m/s. Four world probes choose surface grip; they are not a suspension spring solver.

## Heat and damage

Heat input combines load-related rejected energy with a 1,700 W running baseline. Coolant capacity is approximated as seven litres of water at 4,180 J/(kg K); cooling scales with radiator coefficient, temperature difference, and speed. Oil temperature relaxes toward coolant temperature plus a running offset. There is no oil-pressure or fluid-network solver.

Structural damage is proportional to squared fractional torque overshoot, with separate squared over-rev and temperature terms. The current damage target is aggregate engine health, with a DTC naming the weakest-limit slot. Health at or below 10 stops the engine. A zero damage multiplier suppresses wear, but does not allow an already failed or empty engine to start.

## Numerical guarantees and exclusions

Java 21 strict floating-point semantics plus StrictMath where needed produce repeatable results for identical inputs in the tested runtime. The suite repeats a trajectory 100 times and compares snapshots exactly. This does not promise bit-identical output across every CPU/JVM release. The 1/2/4/8-substep tests check bounded convergence, not equality across different timesteps. ECU AFR/ignition maps, knock, detailed tire slip and individual mechanical failure propagation are not implemented by these equations.
