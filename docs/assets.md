# Asset pipeline and visual review

## Outputs and provenance

The original procedural generator is `assets/blender/build_assets.py`. It creates the H1 hatchback and 140 component exports from primitive-based category geometry. Reuse and proxy shapes are deliberate alpha shortcuts, not 140 individually hand-sculpted production models.

Editable sources are `assets/source/h1-hatchback.blend` and `assets/source/component-catalogue.blend`; the car also has a GLB preview export. Minecraft consumes `models/vehicle/hatchback.mesh.json` and NeoForge OBJ/MTL component models. The initial completed export has 4,232 car triangles. `assets/manifest.json` records counts, Blender version and source provenance.

`engine-loop.wav` is a one-second synthetic harmonic loop created by `scripts/generate-content.py`, encoded to OGG with FFmpeg. It is not a licensed recording of a particular vehicle.

## Rebuild

Use the tested Blender 4.0.2 authoring baseline, Python/NumPy and FFmpeg. Blender is not needed for `./gradlew build` when generated resources are present.

```bash
python3 scripts/generate-content.py
blender -b --python-exit-code 1 --python assets/blender/build_assets.py
ffmpeg -y -i assets/source/engine-loop.wav -c:a libvorbis -q:a 4 \
  mod/src/main/resources/assets/autopropulsion/sounds/engine_loop.ogg
python3 scripts/validate-content.py --assets
```

Do not omit `--python-exit-code 1`; a Blender script exception must fail automation. Commit generator changes together with their generated runtime resources. Do not commit timestamp-only `.blend1` backups. The runtime model must not rely on undocumented one-off edits to a preview.

## Conventions

Authoring uses metres. Export maps Blender `(x,y,z)` to Minecraft `(x,z,-y)`, with vehicle forward `+Z`. Named groups preserve wheel and body pivots; item geometry is normalized into a display volume. Palette textures are original flat colours. Current glass is opaque tinted geometry, not a validated translucent pass.

## Review observations from the first successful render

Both preview PNGs were opened and inspected after CI asset generation passed. The hatchback's engine cover protruded above its closed hood. The catalogue contact sheet cropped edge components and had over-bright lighting. These are visual defects despite successful file validation. Corrective work must update source, export and screenshot together and record the replacement images here.

Remaining art limitations include coarse silhouettes, shared category meshes, flat palette textures, opaque windows, no finished door/hood animation or livery pipeline, and a synthetic sound loop. No screenshot should be presented as final-production art or as a Minecraft frame unless it actually came from the Minecraft client.

## Acceptance checklist

Inspect all sides of the car, wheel pivots, closed-body clearance, player seating, item GUI/hand/ground views, resource reload and both detail profiles. Use a complete labelled contact sheet for coverage, but also inspect representative items at useful scale. A headless render is an authoring check; a game screenshot is required to check the runtime renderer.
