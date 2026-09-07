# Agent development contract

Target only Minecraft 1.21.1, NeoForge 21.1.x and Java 21 unless the owner explicitly requests a separate port. Read README.md and docs/DEVELOPMENT.md before editing. Keep `sim` free of Minecraft imports. Do not implement a part with a dedicated subclass when data can express it.

Treat docs/REQUIREMENTS.md as requested future behavior and README.md / docs/TEST_REPORT.md as current status. Do not label an asset, empty class, TODO or untested branch as a completed feature. Never invent test results, screenshots, benchmarks or human reviewer approval.

Run simulation checks before gameplay changes. Build the actual mod and run dedicated GameTests. Render/UI changes require a client launch and image inspection. Check process exit, completion markers and outputs; use shell pipefail and Blender --python-exit-code 1. Preserve failed-run evidence and explain fixes.

Only publish original or properly licensed assets. Never commit secrets, downloaded game binaries, user worlds or font files. Never merge main, publish a release, change repository licensing, or enable smoke-world code on user infrastructure without appropriate owner authorization. Keep changes on a feature branch and use a PR with truthful scope and test evidence.
