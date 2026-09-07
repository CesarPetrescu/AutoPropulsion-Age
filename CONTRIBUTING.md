# Contributing

Read README.md, docs/DEVELOPMENT.md, docs/ARCHITECTURE.md and docs/ROADMAP.md before changing behavior. The supported target is Minecraft 1.21.1 / NeoForge / Java 21. Keep pull requests focused and do not merge a port into the main target without a separate decision.

Keep simulation classes Minecraft-free. Add parts as validated data and keep gameplay promises honest: a model-study item must remain `implemented: false` until its behavior exists. Regenerate resources after editing `art/catalog.py` or `art/geometry.py`. Preserve named pivots and metre-scale coordinates.

Every simulation change needs a regression test; server behavior needs dedicated-server evidence; render/UI changes need actual screenshots. Include the exact tested commit, commands, environment, failed cases and known limitations. Never remove a failing test merely to produce a green check; justify changes to tolerances with the model and units.

Do not include credentials, private worlds, downloaded Minecraft binaries, third-party manufacturer models or unlicensed audio. A repository-wide license has not yet been selected; discuss contribution/distribution terms with the owner before accepting external contributions under an assumed license.
