# Pinned Minecraft 1.21.1 dependency metadata

This directory is a local Maven metadata repository for `net.neoforged:minecraft-dependencies:1.21.1`. It contains only dependency coordinates and variant constraints, with no Minecraft, NeoForge or library binaries.

The `.module` file is an unmodified snapshot from Gradle's successful-build cache, originally resolved through NeoForge's [Mojang Meta endpoint](https://maven.neoforged.net/mojang-meta/net/neoforged/minecraft-dependencies/1.21.1/minecraft-dependencies-1.21.1.module). That endpoint returned HTTP 502 across two GitHub Actions attempts and a local check on September 8, 2026. The snapshot preserves the dependency versions and platform variants used by the locally passing build.

- SHA-256: `211b1f95714cf1fb6f4a45612dd4bf731fb09795c30d4fb5f23c9fada6173332`
- SHA-1 (also the original Gradle cache directory identity): `42b2a5fcd956009dadebda955239079441ef0d52`

The root build verifies SHA-256 before resolution and redirects only ModDevGradle's `Mojang Meta` repository here. Minecraft libraries, NeoForge, mappings and tooling still come from their normal upstream repositories. All build, server, client, multiplayer and release gates remain required.

When upgrading Minecraft, review the official metadata for the new version and update the path and checksum together. Do not edit dependency versions inside this snapshot or restore a live metadata endpoint as an outage workaround without reviewing the resulting dependency graph.
