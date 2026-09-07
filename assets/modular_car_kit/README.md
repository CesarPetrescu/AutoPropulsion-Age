# Spark Motors modular Blender kit

A first low-poly authoring kit covering all 43 categories in the approved model checklist. Built through Blender MCP in Blender 5.1.2. The earlier square.blend and car.blend remain separate reference files.

## Open the kit

Open `sparkmotors_modular.blend`. Use Blender's Scene dropdown:

| Scene | Purpose |
|---|---|
| SM_01_Assembled | Editable stock sedan, with engine, cabin and mechanical parts installed. |
| SM_02_Engine_exploded | Exploded inline-four inspection display. |
| SM_03_Upgrade_catalog | Labeled overview of 59 optional assemblies and corner variants. |
| SM_04_Workshop | Lift, dyno, fuel pump, machining bench, paint booth and service items. |
| SM_05_Service_view | Independent open-panel cutaway; roof omitted to expose the interior. |
| SM_06_Interchange | Stock geometry batched into one mesh per modeled removable part. |

Frame **1** is closed; frame **50** opens all four doors, hood and trunk; frame **100** closes them. Wheels have separate suspension, steering and spin transforms. Engine internals are static removable geometry, not an operating mechanism animation.

## What is included

**471 named part/assembly roots:** 282 stock, 175 upgrade, 14 workshop. These counts include repeated per-cylinder and per-corner components plus assembly containers; they are not 471 unique designs. All 43 checklist categories have representative geometry. Material tiers can share models. Tiny fasteners are sometimes grouped into their parent part.

The primary car is a 4.5 m sedan with a 2.65 m wheelbase, 1.66 m track and 1.9 m body width excluding mirrors. Authoring axes are **+X right, -Y front, +Z up**. The detailed reference drivetrain is a longitudinal inline-four, manual five-speed and rear differential. V6, flat-four and 1/2/3/4-rotor fitting assemblies share the engine-bay envelope and output interface. Alternate engine internals and transmissions are simplified visual representations; shared accessories and routing still need configuration-specific integration.

Optional models are hidden at their installation coordinates in `SM_Optional_upgrades`; the catalog scene uses separate display copies. Do not enable all mutually exclusive options together. Match `mount_locator`, `replaces` and `exclusive_group` properties, or use the provided Blender helper for exact replacement IDs. The spoiler follows the trunk hinge. Turbo and supercharger are alternative configurations. A complete turbo installation also needs its replacement header, intercooler, pipes, wastegate and BOV.

## Files

- `parts_manifest.json`: IDs, parent relationships, mounting origins, category, paint flags and panel hinge metadata.
- `fit_report.json`: 22 passing checks covering selected clearances, sampled door motion, sampled front wheel steering/travel, engine interfaces and fixture dimensions.
- `stock_car.glb`: stock interchange model with materials, mounting hierarchy, and one opening/closing animation with six channels.
- `export_report.json`: 281 meshes, 68,400 triangles including hidden internals, valid GLB container and zero measured root-position difference from the authoring scene.
- `assembled.png`, `service.png`, `engine_exploded.png`, `upgrade_catalog.png`, `workshop.png`: rendered inspection images.
- `scripts/`: generator, fit/export helpers and rebuild entry point. Source is also stored as Blender text blocks.

## Fit and readiness

The fit checks are selected geometric checks, not exhaustive collision detection across every part combination or manufacturing CAD tolerances. Front tire sweeps sample -28 to +28 degrees and -0.05 to +0.05 m vertical travel. The stock engine has at least 10 mm modeled clearance beneath the hood. Pistons, liners, brakes, fuel tank, radiator/intercooler and driveshaft tunnel have explicit clearance checks. The rebuild check folder records a fresh-process validation run.

This package is modeling work. It does not implement the NeoForge mod, physics, recipes, sound, tuning UI or networking. The GLB is an interchange artifact, not the spec's `.bbmodel`/internal Minecraft JSON. It has basic projected UVs and material colors; production texture atlases, paint masks, livery UVs and LODs are still to be authored. Runtime material batching, internal-part culling and in-game scale/animation tests remain necessary.

## Rebuild

Use a new Blender file, then run `scripts/rebuild_modular_kit.py` through Blender Python/MCP. Existing kit scenes are detected to prevent duplicate IDs. Output defaults to this kit folder; set the environment variable `SPARKMOTORS_OUTPUT_DIR` to another directory to keep the delivered files intact. The saved `.blend` is the authoritative visual deliverable; presentation framing can differ slightly on rebuild.

```sh
blender --background --factory-startup --python scripts/rebuild_modular_kit.py
```

For the part previews, open the saved kit in a background Blender process:

```sh
blender --background sparkmotors_modular.blend --python scripts/render_part_gallery.py
```

The [complete gallery](GALLERY.md) includes a separate preview for every manifest entry. `gallery_index.json` maps model IDs to previews and measured bounds. Each image is framed independently, so image size does not indicate physical size. Assembly images include their same-state child parts. Workbench previews use opaque material colors to make shapes readable; the five overview renders use the authored surface materials.

## Checklist coverage

| # | Category | Part/assembly roots |
|---|---|---:|
| 1 | Chassis | 1 |
| 2 | Opening panels | 6 |
| 3 | Exterior panels | 8 |
| 4 | Glass | 6 |
| 5 | Exterior fittings | 7 |
| 6 | Lighting | 8 |
| 7 | Body upgrades | 7 |
| 8 | Seats | 5 |
| 9 | Dashboard | 5 |
| 10 | Driver controls | 6 |
| 11 | Interior trim | 7 |
| 12 | Safety upgrades | 3 |
| 13 | Wheels | 16 |
| 14 | Wheel mounting | 8 |
| 15 | Brakes | 17 |
| 16 | Suspension | 14 |
| 17 | Steering mechanism | 1 |
| 18 | Engine blocks | 10 |
| 19 | Engine top | 10 |
| 20 | Rotating internals | 27 |
| 21 | Bearings and small internals | 40 |
| 22 | Valvetrain | 71 |
| 23 | Gaskets | 1 |
| 24 | Timing system | 3 |
| 25 | Air intake | 4 |
| 26 | Fuel delivery | 8 |
| 27 | Ignition and electrical | 12 |
| 28 | Lubrication | 4 |
| 29 | Cooling | 6 |
| 30 | Exhaust | 4 |
| 31 | Turbo system | 6 |
| 32 | Supercharger | 1 |
| 33 | Nitrous | 1 |
| 34 | Water-meth | 1 |
| 35 | Clutch and flywheel | 3 |
| 36 | Transmission | 11 |
| 37 | Driveline | 10 |
| 38 | Rotary engines | 94 |
| 39 | ECU and tuning | 5 |
| 40 | Garage equipment | 4 |
| 41 | Testing and servicing | 5 |
| 42 | Fluid containers | 4 |
| 43 | Damage variant | 1 |

After reopening, run `scripts/load_inspection_helpers.py` to restore the optional-part helper namespace. This loader does not change model visibility. `activate_variant` handles exact replacement IDs; multi-part turbo or engine configurations require selecting their companion parts.
