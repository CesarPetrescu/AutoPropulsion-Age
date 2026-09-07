# Engine workshop — 0.2.0-alpha

Open the hood, change the engine and its service assemblies, inspect the internals, and drive the resulting build. Every engine below has a stock and sport grade, with natural aspiration, turbo or supercharger configurations.

## Using it

1. Stop the car and switch off the engine with **R**.
2. Open the garage with **G**, then select **Engine**. Using a wrench or Shift + right-click on the front of the car also opens this page.
3. Press **Open hood** and let the animation finish. This opens just the hood; the doors and trunk stay independent. The Car tab also has a hood button.
4. Use the arrow buttons to choose a family, then **Fit stock** or **Fit sport**. Survival requires the matching engine in your inventory.
5. Install/remove parts in the six service rows. Scroll if your GUI scale shows fewer rows. Drag the preview to orbit it; **Inspect internals** hides covers in the preview without removing items.
6. For a turbo or supercharger, install **high-flow fuel** and **forged internals** first. Then select **Turbo** or **Blower**. Heavy-duty cooling is useful for sustained load.
7. Close the hood when ready, start the engine and drive. The Tuner graph uses the selected engine and installed components.

Work is rejected while moving, while the engine is running, before the hood finishes opening, without the required inventory, or for an unauthorized/distant player. A missing required service assembly prevents starting. Turbo and supercharger occupy one exclusive slot, so they cannot be installed together.

## Engine families

| Engine | Stock item ID | Induction modes |
|---|---|---|
| Inline-4 | `sparkmotors:stock_engine` | Natural / turbo / supercharger |
| V6 | `sparkmotors:stock_v6_engine` | Natural / turbo / supercharger |
| Flat-four | `sparkmotors:stock_flat4_engine` | Natural / turbo / supercharger |
| 1 rotor | `sparkmotors:stock_rotor1_engine` | Natural / turbo / supercharger |
| 2 rotors | `sparkmotors:stock_rotor2_engine` | Natural / turbo / supercharger |
| 3 rotors | `sparkmotors:stock_rotor3_engine` | Natural / turbo / supercharger |
| 4 rotors | `sparkmotors:stock_rotor4_engine` | Natural / turbo / supercharger |

Replace `stock_` with `sport_` for sport engines. All engines and kits are in the Creative tab. In Survival, a whole-engine swap consumes the incoming engine and returns the outgoing one **with its installed components and temperature preserved**. Crafting an upgraded engine, converting its family or crafting a sedan crate also preserves the donor engine's components. Newly crafted components have their default stock state.

## Service assemblies

| Slot | Choices | Effect |
|---|---|---|
| Intake | Missing / stock / performance | Required to run; performance increases torque by 8% |
| Fuel system | Missing / stock / high flow | Required to run; high flow is required for boost |
| Ignition | Missing / stock / performance | Required to run; performance increases torque by 3% |
| Cooling | Missing / stock / heavy duty | Required to run; heavy duty lowers operating temperature and speeds cooling toward that target |
| Internals | Missing / stock / forged | Required to run; forged is required for boost; the kit represents piston internals or rotor/seal hardware for the selected family |
| Induction | Natural / turbo / supercharger | Exclusive complete kits, including their pipes, intercooler and fittings |

The turbo reaches a modeled 0.65 bar boost above its spool range. The supercharger supplies boost lower in the rev range and has a modeled parasitic torque cost. Fuel use varies with family and boost. Temperatures rise under load, affect output above 110 °C, and shut the engine down at 130 °C; restart requires cooling below 125 °C. These are **gameplay calibrations**, not measurements of real production engines.

Engine component upgrades use fitted shared geometry with material changes. The seven engine cores and the two compressor assemblies have distinct geometry. Accessory adapters are generated for each core's port height and width. The internals inspection is an assembly view, with simplified V6/flat-four internals; it is not a complete mechanical disassembly or combustion simulation.

## Recipes

| Item | Recipe |
|---|---|
| Stock service kit | Eight iron ingots around: hopper (intake), bucket (fuel), redstone torch (ignition), water bucket (cooling), iron pickaxe (internals) |
| Performance service kit | Corresponding stock kit in center, four gold corners, four redstone edges |
| Stock alternate engine | Six iron ingots in top/bottom rows; middle row: family material, stock I4 engine, family material |
| Family material | V6: piston; flat-four: quartz; 1 rotor: copper block; 2 rotors: gold block; 3 rotors: diamond; 4 rotors: netherite ingot |
| Sport engine | Matching stock engine in center, four gold corners, four redstone edges |
| Turbo / supercharger kit | Iron in four corners, redstone top/bottom-center, pistons middle-left/right; center: diamond / gold block |

## What was tested

| Coverage | Result |
|---|---|
| All service states | 10,206 family/grade/component states checked: 672 ready builds and 9,534 incomplete or incompatible states correctly blocked |
| Driving matrix | 672 ready engine builds × 32 stock/sport car configurations × 3 tune cases = **64,512 acceleration/braking cases** |
| Engine behavior | Torque/output limits, boost differences, upgrade effects, reverse, ice/dirt/road grip, airborne behavior, overheating derating and cooling |
| Native server | All **42 family/grade/induction layouts** drive and brake on a Minecraft track; inventory swaps, serialized engine items, all 13 engine conversion recipes, sedan-crate placement, ownership, hood timing and old-save migration are exercised |
| Native client | All **21 family/induction layouts** installed with actual GUI buttons and synchronized over packets; renderer checks confirm one engine family and one induction layout at a time |
| Geometry | All 21 layouts pass closed-hood/envelope checks, plus exact triangle-intersection checks at five hood positions and against selected non-mating hardware |

The matrix reports describe their tested scope. Internal mating surfaces, every possible pair of parts, and every continuous tune value are not exhaustively collision-checked. Separate-machine multiplayer, other modpacks, long-term wear, manual clutch operation and a full per-piston/valve/rotor parts tree remain future work.

[Configuration gallery](../README.md#engine-configuration-gallery) · [Fit matrix](engine-fit-matrix.json) · [Triangle checks](engine-intersections.json) · [Test record](verification.json)
