# Original asset pipeline

## Sources and outputs

`art/geometry.py` defines reusable low-poly primitives and original component/chassis meshes. `art/catalog.py` defines component-family studies and functioning prototype part data. `art/generate_assets.py` writes deterministic runtime mesh JSON, depth-rendered inventory PNGs, item/block models, language strings, recipes, tags, part JSON, an asset manifest and generated catalogues.

`art/blender_build.py` imports those exact indexed meshes into editable named Blender collections, attaches materials and locator empties, and creates authoring scenes, GLB exports and studio previews. `art/run_blender.py` enables the bundled glTF exporter and verifies all expected files. Both the game and Blender therefore share a geometry source; one is not an unrelated promotional render of a different vehicle.

## Rebuild

```bash
python3 art/generate_assets.py
python3 tools/validate_assets.py
blender --background --factory-startup --python-exit-code 1 --python art/run_blender.py
```

On a headless Linux host, prefix the Blender command with `xvfb-run -a` and set `LIBGL_ALWAYS_SOFTWARE=1` when no GPU is available. Install Blender's Python dependencies, including NumPy for the glTF exporter; distribution packages can separate these from Blender itself. The tested distribution Blender is 4.0.2. A missing exporter or failed render must produce a nonzero exit code.

Expected outputs: `art/generated/hatch.blend`, `hatch.glb`, `hatch.png`, `catalog.blend`, `catalog.glb`, `catalog.png`, and `blender-report.json`. Scenes and GLBs contain original mesh data; GLB selection excludes studio cameras, lights and floor. The catalogue scene includes labelled family collections. Use the `.blend` collections to inspect individual parts; the file is not a single fused mesh.

## Model contract

Format version 1 uses metres and Minecraft-local axes X right, Y up, Z forward. Blender import maps these to X, -Z, Y. Each object has a unique descriptive name, vertices, triangle/quad indices, RGBA color, animation group and pivot. Locators are named positions. The hatch includes wheel_fl/fr/rl/rr, driver/passenger seat, hood hinge and engine mount locators.

Wheel groups are transformed around their own pivot; front wheel groups additionally steer. Hood rotation is independent. Engine-bay geometry is detail gated. Generated geometry currently uses flat colors; opaque glass avoids pretending an unfinished transparency pass is complete.

## Editing rules

Change the procedural source and regenerate; direct runtime JSON edits will be overwritten. To make Blender the primary manual authoring surface in a later phase, first add a tested Blender-to-runtime exporter preserving locators, groups, pivots, units and winding. The current pipeline imports into Blender and exports GLB; it does not round-trip arbitrary manual Blender edits back into the Minecraft format.

The family studies are original prototype geometry, not final art for every possible cylinder count, material tier or body kit. Small accessories intentionally share a family-level housing. A generated model does not make a gameplay system functional. `implemented: false` parts are marked as model studies and rejected by the server's installation path.

## Validation

The validator checks finite coordinates/colors, mesh version/units, face/index validity, geometry budgets, reference hashes, required hatch locators, vehicle scale and part-to-model references. It parses every generated JSON file and checks catalogue counts. CI additionally requires Blender output files and actual client screenshots. Neither mesh validation nor a successful export alone proves the model looks correct in-game.

No downloaded meshes, logos, manufacturer badges or third-party textures are included. No font binaries are shipped with the model bundle.
