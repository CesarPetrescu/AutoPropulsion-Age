# Playing AutoPropulsion Age 0.3.0-alpha

This alpha lets you place, drive and customize one sedan. It includes six garage tabs, 42 engine hardware choices in ten slots, stateful engine/boost physics, a driving HUD, seventy recipes, fuel, repairs and saved configurations. The larger design in [the original requirements](modular-car-requirements.md) remains a roadmap.

## Install

1. Install **Minecraft Java Edition 1.21.1** with **NeoForge 21.1.249** and **Java 21**. This is the tested combination.
2. Download [autopropulsion-age-0.3.0-alpha.jar](https://github.com/CesarPetrescu/AutoPropulsion-Age/raw/refs/heads/main/downloads/autopropulsion-age-0.3.0-alpha.jar).
3. Put the JAR in that Minecraft instance's `mods` folder and launch its NeoForge profile. No Blender, GLB importer, separate simulation library or other mod is required.
4. Replace the previous AutoPropulsion JAR when upgrading; keep only one version installed. Existing 0.1/0.2 cars and traded engine items migrate their original installed parts. The four new slots receive OEM parts.
5. For a dedicated server, put the same JAR in the server's `mods` folder and on each player's client.

The separate `modular-car-kit.zip` contains editable artwork. Install the **JAR** to play. If you use a launcher with multiple instances, use the selected instance's folder.

## First drive

Start a Creative world, open the **AutoPropulsion Age** creative tab and take a **Sedan Crate**. Alternatively, with cheats enabled:

```mcfunction
/give @s sparkmotors:sedan_crate
```

Use the crate on a wide, flat surface with room for a 4.5-block car. It places a complete stock sedan with 40 L of fuel. Right-click the car with an empty hand to enter, press **R** to start the engine and hold **W** to drive. Use **F5** for a view behind the car. **S** brakes; stop, press **Z**, then hold **W** to reverse. **Left Shift** exits.

## Controls

| Input | Action |
|---|---|
| Right-click car, empty hand | Enter the driver's seat |
| W / S | Accelerator / brake |
| A / D | Steer left / right |
| Space | Handbrake |
| Hold C / C + W | Disengage clutch / free-rev the engine |
| Z, while stopped | Switch forward / reverse |
| R | Start / stop engine |
| G, while driving or looking at car | Open garage |
| Shift + right-click, or use Garage Wrench | Open garage from outside |
| H | Toggle lamps |
| O, while stopped | Open / close doors, hood and trunk |
| B | Horn |
| Left Shift | Exit |
| F5 | Cycle normal Minecraft camera views |

The custom keys can be changed under **Options → Controls → AutoPropulsion Age**. The HUD displays km/h, gear, RPM, fuel, boost, AFR, oil temperature and engine status. Opening a screen applies the handbrake. Gears shift automatically; look around with the mouse while seated.

## Garage

Stop and switch off the engine before changing parts, paint or tuning, refuelling or repairing. Open the garage with G, a wrench, or by right-clicking a **Garage Controller** near your car. The controller searches within eight blocks. Owners and server operators can use the car; another player cannot take it or modify it.

| Tab | Working features |
|---|---|
| Garage | Live 3D preview and Stock / Sport / Remove buttons for engine, transmission, wheels, brakes, suspension and body kit |
| Paint | Eight paint colors with an immediate preview after the server accepts the change |
| Tuner | 4,000–7,000 RPM limiter, 2.8–4.8 final drive, 0.2–1.4 bar boost target and calculated steady-state power curve |
| Car | Ignition, lamps, independent hood, opening panels, fuel, condition and repair controls |
| Engine | Seven engine families, stock/sport base grades, ten service slots, 42 named hardware choices, seven induction layouts and internals inspection |
| Live | RPM, throttle, turbo speed, boost, AFR, coolant/oil temperatures, oil pressure, torque, blower load, engine wear, timed rev test and engine rebuild |

For engine work, open the **Engine** tab and press **Open hood** first. See the [engine workshop guide](ENGINE-WORKSHOP.md) for every family, kit and configuration.

In the Engine tab, each row's arrows browse named hardware and the middle Fit button installs it. More parts / Previous parts and scrolling reveal the other slots. For large or twin turbos, install return/race fuel, billet internals and upgraded ignition/cooling first. Other boost kits require high-flow fuel and forged/billet internals. High-compression internals require natural aspiration. On Live, start the engine and use **Rev test / 2s** with the hood open to watch diagnostics while the brakes are held and the clutch disengaged. Rebuild engine consumes twelve iron ingots to restore engine wear.

In Survival, installing consumes one assembly from your inventory and returns the previous assembly. Removing a required driving assembly prevents the engine starting. Creative changes are free. Scroll the Garage list if a large GUI scale leaves only a few rows visible.

Sport parts have gameplay effects: engine torque +38%, shorter transmission gearing, more tire grip, stronger brakes, quicker steering with sport suspension, and slightly lower drag with the sport body kit. Wheels, suspension and body kits have alternate geometry. The stock/sport grade within each engine family and brake appearances use recolors; the sport transmission uses the existing casing in this alpha.

Paint consumes one dye of any color. Refuelling consumes one **Fuel Can** for up to 10 L, with a 50 L tank limit. Repair consumes four iron ingots and restores condition to 100%. Creative mode bypasses these costs. High-speed wall impacts damage the car; zero condition disables the engine until repaired.

Configuration, engine family/components, owner, paint, fuel, car condition, engine wear, coolant/oil temperatures, tune, hood and lamp state save with the entity. Removed engines and engine crafting retain their parts and condition. Reloaded cars start parked with the engine off. The all-panels control opens all six together, and the hood also has an independent control. There is one usable seat.

## Survival crafting

Use a crafting table. These are deliberately simple alpha recipes.

| Item | Recipe |
|---|---|
| Each stock assembly | Eight iron ingots around its center ingredient below |
| Stock engine / transmission | Center: piston / iron block |
| Stock wheels / brakes | Center: coal block / copper ingot |
| Stock suspension / body | Center: string / glass |
| Sport assembly | Matching stock assembly in center; gold ingots in four corners; redstone in the four remaining edge cells |
| Sedan Crate | Top and bottom rows: iron blocks. Middle row: stock engine, stock wheels, stock transmission |
| Fuel Can | Coal in center; iron nuggets directly above, below, left and right |
| Garage Controller | Top and bottom rows: iron ingots. Middle row: redstone, crafting table, redstone |
| Garage Wrench | Iron ingots at top-center, middle-center, middle-right, bottom-left |

The crate includes stock brakes, suspension and body. Exact recipe JSON is in [the resource folder](../src/main/resources/data/sparkmotors/recipe).

## Alpha limits and next work

- Driving integrates crank/flywheel inertia and clutch torque, with automatic gearing, simplified grip/drag and Minecraft block collisions. Four wheel probes align the body with the road; independent spring-force suspension and tire heat/wear remain future work. Engine hardware uses calibrated gameplay curves and simplified fuel/thermal/wear models.
- The bounding box conservatively encloses the rotated car. Tight corners and diagonal gaps need more clearance than the body shape; this is best played on broad roads. Full-block curbs are obstacles.
- Lamps glow on the model; they do not cast dynamic beams into the world. The horn uses a vanilla sound and the engine uses an original synthesized loop.
- The garage power graph is calculated from the engine model. A physical dyno, lifts, tire machines, drag races, a full individual-component engine parts tree and additional passengers are future work.
- The runtime includes seven engine families, 42 hardware choices and seven induction configurations. The full 471-entry original Blender kit also includes workshop and driveline assets that remain authoring assets. Individual body-panel customization, texture atlases, liveries, LODs and large-fleet performance work remain.
- Single-player client/server traffic and a dedicated GameTest server are tested. Separate-machine multiplayer latency, other modpacks and long-running worlds still need playtesting.

See [verification and development instructions](DEVELOPMENT.md) for the tests run on this build.
