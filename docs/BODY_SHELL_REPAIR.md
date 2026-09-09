# Body-shell repair: model delivery, not just a workspace workflow

This change is based on main `cf74a3d8b599929e090ebd1076c229b9f2c12b67`.
PR #6 contained only `.github/workflows/shell-workspace.yml`. Merging it therefore
could not ship the locally repaired models. This follow-up contains all five
`.blend` files, all five `.mesh.gz` files, their manifest, renderer changes and tests.
The owner will merge this PR; no automatic merge or pre-merge release is requested.

## Per-body repair

| Body | Defect | Repair |
|---|---|---|
| Hatchback | Large upright rear plates, mismatched rear glass/hatch and open cabin seams | Profile-following rear quarters, one coordinated liftgate/glazing assembly and continuous roof/floor returns |
| Sports Coupe | Open bonnet/shoulder sheets and disconnected low-floor/interior edges | Enclosed panel surfaces, common aperture coordinates and battery-safe floor/tunnel joins |
| Utility SUV | Rear plates above the roof slope and poorly seated roof rails | Reprofiled upright rear quarters/liftgate with roof-side returns and real rail mounting feet |
| Panel Van | Gap between sliding cargo panel and rear fixed quarter, mismatched roof/header, rear plate embedded in bumper | Continuous cargo upper side, extended roof, rear posts/header, solid barn doors, moving center seal and externally seated registration plate |
| Touring Sedan | Open bonnet/rear-deck shoulders and mismatched cabin interfaces | Thickened closed panels, common roof/window corners and floor/door returns |
| Classic Sedan | Reference regression only | Original geometry and rendering path retained |

## Rendering and mechanical boundaries

The old opaque renderer already used no-culling; the reported holes were not fixed
by disabling culling. New authored shells have outer faces, inner faces and capped
boundaries, with outward winding on each connected component. The game uses culled
render types only for named closed surfaces in `shell-surfaces.json`. Legacy and
shared mechanical rendering remains unchanged. New enclosed rear wheelhouses replace
the mismatched old rear visual housings; wheel/axle simulation and mounts do not change.

All five bodies retain the existing combustion, hybrid and electric powertrains.
This repair does not alter engine torque, battery capacity, part condition or charge.
Model version is 0.9.1-alpha; network protocol remains 11 because this repair adds no
packet or entity-data fields. Use matching server/client versions.

## Reproduction

Blender 4.2.3 is used to regenerate the editable body assets and review renders:

```sh
blender --background --threads 4 --python-exit-code 1 \
  --python tools/body_styles/build_models.py
python tools/body_styles/generate_resources.py
python -m unittest discover -s tools/body_review/tests -v
python tools/body_review/topology.py --report build/reports/body-styles/topology.json
python tools/body_review/validate.py --report
for body in hatchback sports_car suv van touring_sedan; do
  blender --background "assets/body_styles/$body.blend" --threads 2 \
    --python-exit-code 1 --python tools/body_review/export_blend.py -- \
    --output "/tmp/$body.mesh.gz" --check
done
./gradlew --no-daemon -PwithGameTests build :sim:test runGameTestServer
mkdir -p build/ci
LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null xvfb-run -a -s '-screen 0 1600x1000x24' \
  ./gradlew --no-daemon -PwithGameTests runClientShellSmoke 2>&1 | tee build/ci/client.log
python tools/ci/checks.py shell --log build/ci/client.log --report build/ci/shell.json
```

Normal PR/main CI captures the client output and blocks the required checks if any
body/shell job fails. A PR run does not publish a release. After the owner merges,
the existing main workflow builds 0.9.1-alpha and publishes the JAR only after all
required jobs pass. A successful merge alone is not a successful release.

## Evidence and limits

The independent geometry gate checks exported triangles, not Blender's object graph:
closed edges, winding, normals, degenerate faces, signed volume, rear-profile limits,
matching door/glass pivots and front-face-only visibility rays through the reported
seams in both directions. A nearest-surface ray also verifies that the registration
plate is visible from behind rather than hidden inside the bumper. Negative controls
deliberately remove/reverse/duplicate faces, leave gaps and obscure the plate to
demonstrate that the checker fails those cases.

`ShellSeamClientSmoke` targets 12 cases (six bodies x combustion/electric_800) and 204
close-up frames (17 viewpoints per case), including interiors, floor/roof seams,
closed/open panels, raised underbody and night views. The original 25-case body and
five-powertrain harness remains part of CI. These are actual Minecraft captures;
Blender preview images are labelled separately. A complete screenshot manifest is
not aesthetic approval and does not prove every arbitrary triangle intersection.
The old README gallery predates these repairs; consult the current PR's CI artifacts
for corrected native frames rather than treating old gallery images as new evidence.

The recovered source was regenerated locally for this delivery: 10 topology negative
controls, 2,687 geometry/seam assertions across 748 checked shell chunks, 245
engine-family/induction envelope combinations and 41 CI-checker unit tests passed.
Local Gradle dependency download failed DNS before compilation. Minecraft/Gradle
results must therefore come from the linked CI run, not be inferred from local mesh
checks. The final PR description records completed results and their exact source.
