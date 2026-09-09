# Generated car-system matrix testing

AutoPropulsion Age has several independent configuration dimensions: body style, combustion/electric/hybrid powertrain, drivetrain layout, differential, AWD split, engine family, engine grade, induction and service hardware. The generated matrix suite combines those dimensions automatically instead of relying only on hand-written examples.

The goal is not to claim that every continuous physical state can be exhaustively simulated. The suite uses exhaustive enumeration where the state space is finite and cheap, full cross-products for the main categorical vehicle choices, and deterministic stateful fuzzing for action ordering and damaged/serviced states.

## Coverage layers

| Layer | Coverage | What it proves |
|---|---:|---|
| Engine hardware compatibility | **1,835,008 exhaustive configurations** | Every populated supporting-part combination and induction choice is classified ready/blocked and produces finite reference torque when valid. |
| Targeted engine dynamics | **7,308+ drive/brake cases** | Every hardware option appears in compatible induction baselines across all engine families, grades and tune bounds. |
| Generated composed simulation | **15,984 full-cross-product cases** | Every body × powertrain × drivetrain layout × differential combination is crossed with every relevant engine family × grade × induction choice and advanced through the same simulation classes used by `CarEntity`. |
| AWD routing | **All integer splits 20–80 × all differentials × all powertrains** | `DriveConfig` round-trip and fresh torque-path routing remain valid across the full allowed AWD split range. |
| Native Minecraft lifecycle | **270 cases** | Every body × powertrain × layout × differential combination is constructed as a real `CarEntity`, mounted, started, steered, driven, braked, checked for energy use, and save/loaded before and after driving. AWD cases rotate through 20/40/60/80 splits. |
| Stateful fuzz | **1,000 deterministic sequences × 32 actions** | Randomized-but-reproducible drive/brake/reverse, wear/damage, remove/refit, fluid and state-roundtrip action orderings preserve finite state and part identity. |
| macOS Apple Silicon | Same composed simulation + fuzz suite | Java/Gradle/simulation behavior is checked independently on the standard `macos-15` ARM64 runner. |

The expected counts and deterministic fuzz seed live in [`testspec/car-matrix.json`](../testspec/car-matrix.json). CI treats missing or smaller coverage as a failure rather than silently accepting a partial report.

## What is actually crossed

The generated simulation suite reads the production enums directly, so adding a new enum value automatically expands the matrix and makes the hard expected count fail until the policy/report is reviewed. For engine-equipped vehicles it crosses:

```text
BodyStyle
× Powertrain
× DriveConfig.Layout
× DriveConfig.Differential
× EngineFamily
× engine grade
× induction
```

Pure battery-electric vehicles omit meaningless combustion-engine dimensions but still cross every body, drivetrain layout and differential.

The low-level service-hardware space is larger. That space remains exhaustively checked by `EngineConfigurationTest` for compatibility, while the dynamic suite intentionally uses representative compatible hardware builds instead of trying to drive all 1.8 million hardware configurations in Minecraft.

## Native lifecycle

`GeneratedCarMatrixGameTests` runs each selected vehicle through one connected scenario rather than isolated assertions:

```text
construct real CarEntity
  -> configure body + powertrain + drivetrain
  -> verify fresh mechanical torque path
  -> save/load and compare configuration + part identities
  -> mount real server player
  -> ignition / EV READY
  -> accelerate + steer
  -> brake to near-stop
  -> require world displacement and energy/fuel use
  -> save/load again
  -> require used mechanical state and stored energy to persist
```

Each successful native case emits a stable line such as:

```text
GENERATED_CAR_MATRIX_CASE_PASS case=42 body=hatchback powertrain=hybrid layout=AWD diff=LIMITED_SLIP split=60
```

The final complete-suite marker is:

```text
GENERATED_CAR_MATRIX_SERVER_PASS 270
```

The report merger refuses to pass unless all case IDs `0..269` are present exactly once and all body/powertrain/layout/differential tuples are unique.

## Deterministic fuzzing

`GeneratedCarMatrixTest` uses seed `424242`. A failure includes the seed, sequence number, action number and generated vehicle case ID. Current actions include:

- throttle, steering and occasional reverse;
- normal and hand braking;
- gradual wear/damage of a real installed component;
- remove and refit of a component with UUID identity preservation;
- fluid-level changes within legal bounds;
- current-version mechanical-state round trips.

This is deliberately deterministic in CI. A failure can therefore be replayed from the same commit instead of disappearing on rerun.

## Reports

`tools/matrix/report.py` merges fresh evidence from the simulation tests and dedicated GameTest log. It writes:

```text
build/ci/car-matrix/report/matrix-report.json
build/ci/car-matrix/report/matrix-report.md
build/ci/car-matrix/report/matrix-report.html
```

The JSON contains all 270 native cases plus distribution counts. Markdown is appended to the GitHub Actions job summary and the full directory is uploaded as an Actions artifact.

The merger is fail-closed. It rejects missing evidence, incomplete exhaustive counts, a dynamic engine matrix below its expected minimum, duplicate native configurations, missing case IDs, or a missing final native PASS marker. Unit tests under `tools/ci/tests/test_matrix_report.py` exercise those failure paths.

## Run locally

Pure generated composition + fuzz only:

```bash
./gradlew --no-daemon :sim:test --tests 'com.photonspark.sparkmotors.sim.GeneratedCarMatrixTest'
```

All simulation tests and real dedicated GameTests:

```bash
mkdir -p build/ci/car-matrix
./gradlew --no-daemon --max-workers=2 -PwithGameTests :sim:test --rerun runGameTestServer \
  2>&1 | tee build/ci/car-matrix/server.log
```

Build the combined report:

```bash
python tools/matrix/report.py \
  --server-log build/ci/car-matrix/server.log \
  --output-dir build/ci/car-matrix/report
```

Report-tool regression tests:

```bash
python -m unittest tools.ci.tests.test_matrix_report -v
```

## CI

`.github/workflows/car-matrix.yml` runs two independent jobs on pull requests, `main`, and manual dispatch:

1. **Linux native** — complete simulation suite plus the real Minecraft GameTest matrix, followed by fail-closed report generation.
2. **macOS M1** — requires an ARM64 `macos-15` host and runs the composed simulation/fuzz suite under Java 21.

The existing main CI also runs `:sim:test` and `runGameTestServer`, so the new core matrix assertions become part of the normal release gate even if the dedicated reporting workflow itself is changed later.

## Extending the matrix

When adding another vehicle feature, prefer one of three integrations:

1. **Finite categorical choice:** add it to the generated cross-product or an exhaustive finite sweep.
2. **Continuous numeric setting:** exhaust the legal integer range when cheap, otherwise use boundaries plus deterministic samples.
3. **State/action interaction:** add an invariant and an action to the deterministic fuzzer, then add a native lifecycle assertion if Minecraft world behavior matters.

Do not add a large generated dimension merely to increase a case count. Each dimension should have an independent invariant or interaction that can detect a real defect.
