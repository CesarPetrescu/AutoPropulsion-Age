# ADR 0002: visual output is part of acceptance

Date: 2026-09-07. Evidence: [green simulation/server/client run](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/runs/34145960471) and [asset correction run](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/runs/34146458685).

## Observation

The first successful client/server run passed its driving assertions but visual inspection found three defects: custom garage labels were blurred while buttons were sharp, the first default-profile screenshot still showed Loading terrain, and the passenger head protruded above the roof. Therefore a PASS sentinel alone is insufficient visual evidence.

## Garage decision

Minecraft 1.21.1's `Screen.render` calls `renderBackground` before drawing widgets; this is visible in the [NeoForge source patch](https://github.com/neoforged/NeoForge/blob/1.21.1/patches/net/minecraft/client/gui/screens/Screen.java.patch). Our old override drew background and text and then called `super.render`, invoking the blur again over the new text.

Move custom panel/text rendering into `renderBackground`, call the inherited background there once, and let inherited `render` draw the widgets afterward. Do not disable the user's blur option to hide a draw-order defect. The follow-up screenshots must establish that both garage tabs remain legible.

## Capture and seating decisions

Wait for the loading screen to clear and the player to be mounted before advancing the smoke timeline. Exercise the dyno tab through its mouse-click handler. Require five fresh 1280x720 PNG captures per profile plus the actual vehicle mesh-loaded message; reject stale sentinels, missing images and invalid PNG headers. Header checks are not semantic screenshot review.

Lower the H1 rider origin from -0.18 m to -0.45 m relative to the vehicle base. Keep an explicit eye-height/roof-clearance check in the client smoke assertion and inspect the resulting third-person frames. This does not replace first-person-camera or every player-model compatibility testing.

## Blender decisions and verified result

The original preview showed engine parts above the hood and cropped catalogue edges. A reproducible finalization pass adjusts both the editable source and runtime vehicle mesh, not just the beauty render. It lowered the engine-bay group 0.18 m to leave 0.03 m of clearance under the hood, regenerated the GLB and used projected mesh bounds to fit all geometry within a 5.5% frame margin. CPU Cycles uses 96 samples, AgX and no optional denoiser.

The corrected car and complete component preview were downloaded and opened for visual inspection. The clipping/cropping defects are corrected. Coarse body panels, large panel gaps, category-level part reuse, opaque glass and flat-colour runtime materials remain alpha-art limitations, not final production assets.

## Environment hygiene

The first successful smoke log also exposed a missing flat-world layers definition and missing Linux narration library. Give the isolated server explicit flat-world generator settings and install Ubuntu's `flite1-dev` package in CI. Continue using null OpenAL output; successful narration-library initialization is not an audio quality test. Provide an empty server.properties before GameTests to avoid an avoidable first-start missing-file error.

Normal validation remains read-only. The explicitly named asset-maintenance workflow can publish generated files only to the foundation feature branch, never main, and fails rather than force-pushing if another commit moved that branch. The dated test report records which follow-up checks actually passed.
