# Configuration

Only settings backed by working code are registered. The larger specification's chunk tickets, livery upload, crash damage and vehicle quotas are not silently exposed as no-op switches.

## Server/world configuration

NeoForge creates `autopropulsion-server.toml` under the world's `serverconfig/` directory. Defaults can be distributed using the usual NeoForge default-config workflow. Server values control the authoritative simulation.

| Key | Default | Bounds | Effect |
|---|---|---|---|
| `simulationSubsteps` | 4 | 1–8 | Fixed substeps per 20 Hz tick; default 80 Hz |
| `damageMultiplier` | 1.0 | 0–10 | Aggregate structural/thermal wear; zero disables new wear |
| `fuelConsumptionMultiplier` | 1.0 | 0–10 | Fuel consumed while running; zero does not let an already-empty tank start |

The JVM suite exercises 1, 2, 4 and 8 substeps and selected damage/fuel edge cases. This is not an exhaustive Cartesian product of every possible value or modpack combination.

## Client configuration

NeoForge creates `config/autopropulsion-client.toml` on the client.

| Key | Default | Bounds | Effect |
|---|---|---|---|
| `driverHud` | true | boolean | Driver speed/RPM/fuel/temperature overlay |
| `alwaysRenderEngine` | false | boolean | Include engine-bay objects when hood is shut |
| `engineDetailDistance` | 24 | 4–96 metres | Distance cutoff for engine detail |

Controls are standard rebindable Minecraft key mappings. Graphics modes, render distance, GUI scale and camera are vanilla settings. The automated smoke harness requests Fast, Fancy and Fabulous in separate actual client launches; consult TEST_REPORT.md for the runs that really completed.

## Development-only switches

`-Dapa.serverSmoke=true` creates a disposable test track and owned vehicle when a player logs in. **Never enable it on an existing or production world.** `-Dapa.clientSmoke=true` enables scripted driving/screenshots and normal client termination. `-Dapa.profile=fast|fancy|fabulous` selects its graphics preset, and `-Dapa.smokeHost=127.0.0.1:25565` selects the private development connection. These are test JVM properties, not user-facing gameplay options.
