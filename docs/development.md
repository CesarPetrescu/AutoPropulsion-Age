# Development

## Toolchain

Use a Java 21 JDK and the committed Gradle 9.2.1 wrapper. Minecraft 1.21.1, NeoForge 21.1.249, ModDevGradle 2.0.146 and the Parchment mapping version are explicit in `gradle.properties`, `build.gradle` and `mod/build.gradle`. Do not substitute a newer Minecraft version to make a failing build pass.

Official references: [NeoForge 1.21.1 setup](https://docs.neoforged.net/docs/1.21.1/gettingstarted/) and [Gradle release checksums](https://gradle.org/release-checksums/).

```bash
java -version
./gradlew --version
./gradlew build
./gradlew :mod:runClient
```

The wrapper JAR and distribution checksums are verified in CI. Do not remove verification when a download fails. Follow redirects when retrieving a checksum and validate that the response is exactly 64 hexadecimal characters, not an HTML page.

## Fast offline simulation loop

```bash
bash scripts/test-sim.sh
```

This compiles the real `sim`, `tools` and regression source with `javac --release 21`. It runs 35 baseline checks and two transactional-safety regressions, then writes `.local-test/reports/reference-dyno.csv`. No JUnit, Gradle or Minecraft download is necessary after obtaining the source.

Gradle invokes these same main-method suites as `:sim:regressionTest` and `:sim:safetyTest`. The standard JUnit-discovery task is intentionally disabled for `sim`, with dependencies on both real suites. `test`, `check` and `build` still propagate any suite failure.

```bash
./gradlew :tools:run --args='--engine ref_i4_2.0'
./gradlew :tools:run --args='--engine ref_i4_2.0 --race-cam --boost 0.8'
```

CSV goes to stdout; the peak summary goes to stderr. The stock reference produced 187.73 Nm and 118.07 kW in the recorded local test. This is a calibration result, not a measurement of a real engine.

## Minecraft development server

Read and accept the [Minecraft EULA](https://aka.ms/MinecraftEULA) before running a server. For an isolated development server, create `mod/run/server/eula.txt` containing `eula=true` only after acceptance, then run:

```bash
./gradlew :mod:runServer
```

Do not reuse a production server directory. `runClient` and `runServer` use development launch configuration, not a production login test. For a production multiplayer acceptance test, use licensed clients, online-mode authentication, a clean NeoForge installation and the built mod JAR on both sides.

The automated smoke runner is separate:

```bash
ACCEPT_MINECRAFT_EULA=true python3 scripts/test-runtime.py
```

It starts a real dedicated development server bound to **127.0.0.1**, deliberately uses offline-mode for isolated development clients, and launches graphical clients under Xvfb. Never copy its offline-mode settings to a public server. It writes only ignored `mod/run/` directories and shuts its processes down.

## Configuration

Server settings are NeoForge per-world server configuration. Client settings are in the client config directory. Existing server worlds can override `defaultconfigs`, so change the world's actual `serverconfig` or start a fresh disposable test world when evaluating a profile.

| Key | Default | Accepted range / meaning |
| --- | --- | --- |
| `simulationSubsteps` | 4 | 1..16 substeps per 20 TPS tick |
| `fuelConsumptionMultiplier` | 1.0 | 0..10; zero disables consumption |
| `damageMultiplier` | 1.0 | 0..10; zero disables engine/collision damage |
| `allowGuestDriving` | false | Guest driving only; not part/tune management |
| `showHud` | true | Client driver HUD |
| `detailedModels` | true | Client engine-bay geometry |

There is no livery-upload, advanced sound-bank, chunk-ticket or per-player vehicle-limit configuration yet. Those belong to the future specification, not the current config file.

## Source changes and generated files

Change authoritative source first. Keep catalogue indices stable. Run content generation and validation after catalogue edits, and Blender after geometry edits. Commit generated runtime resources together with their generator changes. Ordinary code changes must not rerender Blender or require the authoring tools.

Do not commit Minecraft binaries, worlds, account tokens, `.gradle`, `build`, or `run` directories. A branch that builds with development source sets still needs the packaged-JAR check and a clean installed-mod acceptance run before release.
