# Contributing

Read [Development](docs/development.md), [Architecture](docs/architecture.md), [Roadmap](docs/roadmap.md) and [Testing](docs/testing.md) before changing the foundation. Keep Minecraft-independent logic in `sim` and client classes out of server initialization paths.

A change should include its regression, the command actually executed, the outcome and any remaining limitation. When fixing a defect, reproduce it first whenever practical. Keep authority, ownership, distance, finite-number validation and inventory transactions on the server. Never trade those checks for a smoother demo.

Run `bash scripts/test-sim.sh`, content validation and `./gradlew build`. World-facing changes also require GameTests and graphical client/server evidence. For assets, include editable sources, generated runtime models, provenance and reviewed images. Do not count catalogue-only entries as implemented gameplay.

Use a feature branch and PR; do not force-push shared work or silently merge. Keep registry identifiers, saved fields and model indices stable. Document migrations before changing them. Do not commit account data, Minecraft binaries, test worlds or build caches. The owner must approve licence changes; the current metadata remains All Rights Reserved.
