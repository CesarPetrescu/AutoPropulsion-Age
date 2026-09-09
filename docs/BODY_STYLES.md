# Selectable body styles

Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21. Included in 0.9.0. Use matching client/server versions (network protocol 11). The original Classic Sedan remains the default for older saves.

## 1. Design

Body identity is independent of powertrain and serviceable parts. These are five distinct low-poly coachworks on the existing **2.65 m wheelbase**, not five paint presets and not five new physics implementations.

| Body | Nominal shell length × width | Roof height in the model frame | Distinguishing features |
|---|---|---|---|
| Hatchback | 4.13 × 1.91 m | 1.61 m | Short rear overhang, four doors, lifting rear window/tailgate |
| Sports Coupe | 4.49 × 1.96 m | 1.39 m | Low roof, two longer doors, short cabin, separate boot, no rear bench |
| Utility SUV | 4.545 × 1.99 m | 1.88 m | Upright cabin, four doors, lifting tailgate, roof rails and protective trim |
| Panel Van | 4.755 × 2.02 m | 2.17 m | Tall enclosed cargo body, front doors, sliding side doors and rear barn doors |
| Touring Sedan | 4.73 × 1.95 m | 1.56 m | Longer rear deck, four doors, separate boot and revised cabin proportions |

Dimensions exclude mirrors, rails and optional trim. The touring model has a longer **body**, not a different wheelbase. A van body does not add simulated cargo capacity or passenger seats; an SUV body does not grant suspension travel or torque. Tire, engine, battery, drivetrain and suspension specifications remain the installed parts' specifications.

## 2. Obtain and install

The creative tab contains 25 new crates: each of the five bodies with combustion, hybrid, plug-in hybrid, 400 V electric and 800 V electric configurations. Their powertrains retain the existing configurable hardware.

In survival, craft a coachwork kit from eight iron ingots around its identifying center ingredient:

| Classic Sedan | Hatchback | Sports Coupe | SUV | Van | Touring Sedan |
|---|---|---|---|---|---|
| Glass bottle | Tripwire hook | Redstone | Chest | Oak door | Clock |

Combine any existing vehicle crate with the desired body kit in a crafting grid. The conversion is restricted to the **same powertrain type**. The complete donor component data is copied, including mechanical part identities/condition and persisted traction battery state. The old body kit is returned as the recipe remainder. No second vehicle is returned.

An already placed vehicle can also be re-bodied. Park it, switch it off, unplug it, leave its seat, and raise it using the existing service-jack operation. Right-click the vehicle with the desired kit. The server checks ownership, reach, service state and the larger body's clearance before consuming the kit. In survival the old kit is returned to the inventory or dropped if that inventory is full. Obstructed, occupied, moving, running, plugged-in, unauthorized and redundant swaps do not consume a kit.

Body swaps do not refuel, recharge, cool, repair or replace installed parts. Body identity survives save/load and normal client synchronization. Unknown or missing body IDs fall back to Classic Sedan. The network protocol is bumped to 11 because entity data registration changed: use matching mod versions on server and clients.

## 3. Parts and opening panels

All bodies reuse the existing engine families, induction kits, individual mechanical parts, drive layouts and electrified configurations. The engine bay and wheel mounts stay in the canonical mechanical coordinate frame. Interior trim is translated into each cabin; tail exhaust geometry is fitted behind the rear axle. Charging inlets and cable endpoints follow the selected body. Vehicle query/collision envelopes follow the shell rather than always using the original sedan dimensions.

Existing open-panel and service-jack operations work with the new bodies. The van uses separate sliding-door and barn-door animation metadata; hatchback/SUV glazing follows the liftgate. Opening doors remain visual/service panels, not independent rigid-body door collision simulations.

## Editable Blender sources

`assets/body_styles/` contains five `.blend` files and `profiles.json`. Each file has `Coachwork`, `Shared mechanical fit reference` and `Studio` collections. The reference collection is for fit inspection and is never exported as a duplicate engine or wheel assembly. Named objects retain `apa_metadata`, including part filters, material class and hinge/pivot information.

Rebuild all authored bodies with Blender 4.2.3:

```sh
blender --background --threads 4 --python-exit-code 1 \
  --python tools/body_styles/build_models.py
python3 tools/body_styles/generate_resources.py
```

For manual editing, open the relevant `.blend`, edit the `Coachwork` collection, preserve the metadata on serviceable/animated objects, then export:

```sh
blender --background assets/body_styles/hatchback.blend \
  --python-exit-code 1 --python tools/body_review/export_blend.py -- \
  --output /tmp/hatchback.mesh.gz
```

The exporter uses evaluated mesh copies, including object transforms/modifiers, and excludes the studio and mechanical references. `--check` compares an unmodified authored file against its committed runtime geometry and metadata. Edited geometry is expected to differ; omit `--check` for an intentional change, install the exported mesh, update its profile checksum/counts, and rerun validation. Model axes: Blender `-Y` is forward and `Z` is up; the runtime format uses `+Z` forward and `+Y` up.

## 3. Verify and iterate

```sh
python3 tools/body_review/validate.py --report
./gradlew --no-daemon -PwithGameTests build :sim:test
./gradlew --no-daemon -PwithGameTests runGameTestServer
LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null \
  xvfb-run -a -s '-screen 0 1440x900x24' \
  ./gradlew --no-daemon -PwithGameTests runClientBodySmoke
```

The body GameTests cover stable identities, old-save fallback, 150 crate conversions, paid installation/blocked swaps, and 30 body/powertrain driving/braking cases. The native client harness targets 25 new-body/powertrain cases and 125 screenshots: front, rear, open panels, underbody, plug-in charging, garage previews and one driven BEV per new body. Its charging supply is explicitly a creative fixture, not new proof of ElectricalAge network compatibility.

Geometry checks cover the cabin envelope, battery-fin floor clearance and all seven combustion-family/seven induction masks for each new body. These are numerical envelope checks, **not a proof of every triangle intersection**. PNG presence, dimensions and successful native gameplay do not by themselves establish visual approval. The evidence manifest records the exact tested commit and keeps visual approval separate.

The refinement corrects the coupe floor's clearance over the unchanged traction-pack cooling fins, moves its low interior mounts consistently, and closes the bonnet-to-fascia gap. Review the regenerated images before treating the assets as visually final. Real GPU/driver combinations, every first-person camera/avatar configuration, very large fleets and all optional modpacks are outside this body's focused verification campaign.

## Full body gallery

The native images below are unedited captures from tested source `876c0bf6`, the asset revision included in this feature. [Source run and full 125-frame evidence](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/runs/34306618116). The normal release CI now reruns the body matrix on each exact candidate commit.

### Classic Sedan

![Classic front](body-review/stock-front.png)

![Classic underbody](body-review/stock-under.png)

### Hatchback

| Blender front | Blender rear | Blender side |
|---|---|---|
| ![Hatchback front](body-styles/blender/hatchback-front.png) | ![Hatchback rear](body-styles/blender/hatchback-rear.png) | ![Hatchback side](body-styles/blender/hatchback-side.png) |

| Minecraft front | Minecraft rear |
|---|---|
| ![Hatchback front](body-styles/native/hatchback-front.png) | ![Hatchback rear](body-styles/native/hatchback-rear.png) |
| Open panels | Underbody |
| ![Hatchback open](body-styles/native/hatchback-open.png) | ![Hatchback underbody](body-styles/native/hatchback-underbody.png) |

### Sports Coupe

| Blender front | Blender rear | Blender side |
|---|---|---|
| ![Sports Coupe front](body-styles/blender/sports_car-front.png) | ![Sports Coupe rear](body-styles/blender/sports_car-rear.png) | ![Sports Coupe side](body-styles/blender/sports_car-side.png) |

| Minecraft front | Minecraft rear |
|---|---|
| ![Sports Coupe front](body-styles/native/sports_car-front.png) | ![Sports Coupe rear](body-styles/native/sports_car-rear.png) |
| Open panels | Underbody |
| ![Sports Coupe open](body-styles/native/sports_car-open.png) | ![Sports Coupe underbody](body-styles/native/sports_car-underbody.png) |

### Utility SUV

| Blender front | Blender rear | Blender side |
|---|---|---|
| ![Utility SUV front](body-styles/blender/suv-front.png) | ![Utility SUV rear](body-styles/blender/suv-rear.png) | ![Utility SUV side](body-styles/blender/suv-side.png) |

| Minecraft front | Minecraft rear |
|---|---|
| ![Utility SUV front](body-styles/native/suv-front.png) | ![Utility SUV rear](body-styles/native/suv-rear.png) |
| Open panels | Underbody |
| ![Utility SUV open](body-styles/native/suv-open.png) | ![Utility SUV underbody](body-styles/native/suv-underbody.png) |

### Panel Van

| Blender front | Blender rear | Blender side |
|---|---|---|
| ![Panel Van front](body-styles/blender/van-front.png) | ![Panel Van rear](body-styles/blender/van-rear.png) | ![Panel Van side](body-styles/blender/van-side.png) |

| Minecraft front | Minecraft rear |
|---|---|
| ![Panel Van front](body-styles/native/van-front.png) | ![Panel Van rear](body-styles/native/van-rear.png) |
| Open panels | Underbody |
| ![Panel Van open](body-styles/native/van-open.png) | ![Panel Van underbody](body-styles/native/van-underbody.png) |

### Touring Sedan

| Blender front | Blender rear | Blender side |
|---|---|---|
| ![Touring Sedan front](body-styles/blender/touring_sedan-front.png) | ![Touring Sedan rear](body-styles/blender/touring_sedan-rear.png) | ![Touring Sedan side](body-styles/blender/touring_sedan-side.png) |

| Minecraft front | Minecraft rear |
|---|---|
| ![Touring Sedan front](body-styles/native/touring_sedan-front.png) | ![Touring Sedan rear](body-styles/native/touring_sedan-rear.png) |
| Open panels | Underbody |
| ![Touring Sedan open](body-styles/native/touring_sedan-open.png) | ![Touring Sedan underbody](body-styles/native/touring_sedan-underbody.png) |
