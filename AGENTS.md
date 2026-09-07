# Implementation agent handoff

Target Minecraft 1.21.1, NeoForge 21.1.249 and Java 21. Preserve the MC-free `sim` boundary. Read `docs/architecture.md`, `docs/roadmap.md`, `docs/testing.md` and the decision records before implementation.

Do not mark the complete modular-car specification done. There are 140 model/catalogue entries but only 16 installable definitions in seven in-game slots. A flat assembly is not the nested-tree milestone; grip raycasts are not suspension; the garage curve is an estimate, not a measured roller pull.

Use `bash scripts/test-sim.sh` for offline regressions, `./gradlew build` for the artifact, GameTests for world assertions and `scripts/test-runtime.py` for actual graphical networking smoke checks. Inspect real output images and logs. Never replace a failed assertion with a weaker assertion merely to obtain a green run. Distinguish local, CI, skipped and manual checks in the dated report.

Make changes on the requested feature branch, preserve unrelated branches, and open/update a PR rather than merging without instruction. No production server, public port, secret or account login is needed for the isolated loopback harness. Regenerate assets from source; do not hide one-off runtime edits. Keep signed artifact URLs and authentication data out of committed documentation.
