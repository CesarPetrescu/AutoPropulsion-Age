# ADR 0003: preserve concurrent main-branch art without changing the tested runtime

Date: 2026-09-07.

## Context

While the NeoForge vehicle foundation was being tested, a separate authoring delivery reached `main` as commit `56fe6e7250f4baa00bb297ed3cf91c189265f6bd` (Add modular Blender car kit and complete model preview gallery). PR #2 then had overlapping README, gitignore and gitattributes changes. The earlier assumption that main only held its original short README was no longer true.

The new art delivery contains the Spark Motors modular sedan kit, early studies, downloads, original requirements and packaging/preview helpers. Its guide describes 471 named roots across 43 categories and a 68,400-triangle stock GLB. Those counts and its supplied fit/rebuild results describe that independent authoring kit; they are not measurements from the H1 simulation/runtime test run.

## Alternatives

| Approach | Consequence |
| --- | --- |
| Keep only this PR's files | Discards concurrent user work; rejected |
| Immediately replace the runtime H1 with the new GLB | Invalidates the completed renderer/seat/collision verification and requires a real conversion/integration pass; rejected for this maintenance step |
| Preserve both, merge main into the feature branch, document the boundary | Retains all work and keeps the verified runtime unchanged; selected |

## Decision and preservation

Create a normal two-parent merge commit on `feat/neoforge-1.21.1-vehicle-foundation`, with the current feature tip and `56fe6e7` as parents. Do not update main, force-push or merge the PR.

Preserve `assets/early_studies`, `assets/modular_car_kit`, `downloads`, `docs/modular-car-requirements.md`, `tools/optimize_previews.py` and `tools/package_models.py` using their exact existing Git tree/blob objects. Preserve the full original main README at root as `ART_GALLERY_2026-09-07.md`, using the same blob SHA, so its gallery content and root-relative links remain intact. Label it an historical asset-only snapshot from the current README.

Resolve `.gitattributes` by retaining the union of source and binary rules. Resolve `.gitignore` by retaining both build/runtime exclusions and art backup/progress exclusions; explicitly permit selected permanent logs under `docs/evidence/`.

## Test boundary

The independently tested implementation remains `4ed59ca2f03e29e342c60692a63cd85ecc8c9a4c`. This preservation merge does not change `mod/`, `sim/`, the dyno Java source, runtime models, authoring H1 generator or build scripts. Existing evidence remains attributed to its actual source commit. PR CI reruns are separate checks of the combined repository.

The modular sedan kit is not yet loaded by Minecraft. Integrating it requires conversion to the runtime mesh/locator model, an explicit identifier mapping, attention to triangle/draw-call budgets, passenger/collision dimensions and a new actual client/server visual test. Do not count its assembly roots as new installable mod definitions. Do not claim its Blender 5.1.2 rebuild was performed by the H1 Blender 4.0.2 CI job.
