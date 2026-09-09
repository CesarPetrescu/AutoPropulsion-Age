# Glass and charger visibility repair

Based on main `b83ba89e67c73f130df9b35dcb6a2c901e076e5e` (0.9.1-alpha).
This change does not regenerate or replace car Blender meshes, change batteries, or alter electrical power simulation.

## Corrected diagnosis

The previous explanation that closed geometry inherently should not be back-face culled was too broad. A closed pane has outward-facing surfaces visible from either side; its internal back face need not be drawn twice. The repaired five-body glass passes the topology/winding checks. Topology correctness alone does not guarantee correct transparency.

`CarMesh` interleaved glass, seals, body panels and cabin geometry through the shared `MultiBufferSource`. Changing render types can flush the shared buffer. A translucent pane could therefore write depth before a later opaque interior part or a separate entity behind it. That later draw fails its depth test, producing missing interiors and apparent see-through surfaces. Just using a no-cull render type does not address that ordering.

The chargers use complete vanilla JSON cuboids, but the Ultra tier exposed a full-block shape and all tiers retained occluding block properties. The model only covers part of the supporting block; occluding its whole upper face leaves a terrain hole around the base. A collision proxy must not be used as evidence that a model fills its block cell. This is different from deliberately placing a spectator camera inside a solid cuboid, whose backside is normally culled.

## Changes

- World glass is accumulated independently of opaque car parts and other entities, globally sorted, and flushed at NeoForge's `AFTER_PARTICLES` stage. The Fabulous particles target retains depth for the transparency compositor; GUI glass uses the main target with color-only writes after opaque GUI/model buffers.
- Opaque body culling is unchanged. New closed panes retain front-face selection; thin legacy panes remain two-sided. Closed and legacy world panes share one sort batch rather than independently sorted material buckets. GUI previews are separately flushed and do not leak into the world pass.
- Charger properties explicitly disable neighbor occlusion. Its occlusion shape is empty; outline and collision shapes are cached unions of its actual JSON cuboids, rotated into all four horizontal orientations.

## Reproduction and validation

```sh
./gradlew --no-daemon -PwithGameTests build :sim:test runGameTestServer
LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null xvfb-run -a -s '-screen 0 1440x900x24' \
  ./gradlew --no-daemon -PwithGameTests -PvisibilityGraphics=fancy runClientVisibility
# Repeat with -PvisibilityGraphics=fabulous (selected before world loading).
```

Two native server tests check 96 neighbor-face cases and 16 model/shape rotations against the actual packaged JSON, including collision and selection shape equality and six faces per cuboid.

The native client checks six bodies × combustion/PHEV/800 V BEV × six camera views, plus four chargers × four rotations × two base views: **140 screenshots per profile**. There are 72 through-glass pixel probes using a small lime-concrete vanilla block-display witness. This is a real separately rendered entity, not a substitute car, fake screenshot or mock renderer. Remaining views show the unmodified car interior and grazing two-window sightlines. The baseline negative control restores the old `CarMesh` and `ChargerBlock` while keeping exactly the same harness; it must actually lose witness pixels. A baseline failure is expected only in that job, not ignored on repaired profiles.

Evidence: `evidence-visibility-fancy`, `evidence-visibility-fabulous`, `evidence-visibility-baseline`, and the normal PR CI `evidence-build-server`/other regression artifacts. Each contains the tested source ID, logs, JSON results, and native PNG captures. Results and sampled visual review will be recorded in the PR after execution, not inferred from file counts.

## Boundaries

Fast/Fancy/Fabulous GPU paths need native coverage; shader packs, arbitrary third-party renderers, nested translucent effects and every GPU/driver combination are not universally certified. Camera clipping *inside a solid charger* is not a promised gameplay view. These changes do not alter the earlier screenshot gallery's provenance or claim every missing interior report has the same cause.
