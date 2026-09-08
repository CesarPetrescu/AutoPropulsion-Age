# Continuous tests and JAR releases

[Latest installable JAR](https://github.com/CesarPetrescu/AutoPropulsion-Age/releases/latest/download/autopropulsion-age-latest.jar) · [Releases](https://github.com/CesarPetrescu/AutoPropulsion-Age/releases/latest) · [Actions runs](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions)

The **Test and release** workflow runs on every branch push, pull request and manual dispatch. All checks must pass before it publishes a JAR. A failed, cancelled or skipped required job blocks publication. GitHub's manual dispatch button is available once the workflow is on the repository's default branch; branch pushes run it immediately.

| Required job | Fresh evidence |
|---|---|
| Workflow and release gate tests | actionlint, shell validation and 28 positive/negative gate tests, including missing results, partial coverage, test classes in the JAR, changed bytes and wrong commit provenance |
| Build, simulation and dedicated server | Java 21 build; at least 74 JUnit cases with no skips; at least 41 native GameTests, including 294 combustion and 12 electric drivetrain cases, oriented collision and crash/spin recovery; simulation benchmark report; installable JAR inspection |
| Native client (ui) | Loaded Mods-list logo plus 156 workshop page/scale cases, widget bounds/overlaps/labels, scaled mouse navigation and screenshots |
| Resources, audio and geometry | Regeneration of 472 resources preserves JSON semantics, PNG pixels and exact audio/mesh bytes; all 48 mono OGG assets decoded; 294 hardware geometry identities; 49 engine envelopes; 49 layouts at five hood positions; 20 body-coverage rays and 48 tire/body steering/travel poses |
| Native client (electric) | Four vehicle variants and chargers rendered; actual GUI charge-target packets, charging, cable interlocks, READY, electric driving and braking |
| ElectricalAge compatibility | Pinned companion build and tests; 42 native GameTests including real cable/MNA power, energy accounting, overvoltage recovery and unload; combined-mod client charging and driving |
| Native client (mechanics) | Actual GUI/network coolant diagnosis, targeted repair, fluid refill, timed verification, jack/tire service, failed sender, driving, active audio channels and cleanup |
| Native client (handling) | Real RWD/FWD/AWD preset, differential and center-split controls; native steering keys, handbrake drift, service braking, forward relaunch aligned to the rendered heading, airborne contact loss and spring landing |
| Native client (matrix) | All 49 engine/induction layouts and 42 hardware choices through native GUI buttons and packets, plus garage, driving, paint, tuning and parked rev test |
| Two native clients and dedicated server | Owner-only service, worn-part removal, real item drop/pickup, installation into a second owner's car and synchronization to both clients |
| Required checks | Stable aggregate check: every job above must return `success` |

The native clients run in Xvfb with Mesa software rendering and OpenAL Soft's null output backend. They create isolated test worlds without desktop input or Minecraft accounts. The network fixture binds only to loopback and accepts the Minecraft EULA for its disposable test server. Logs, native screenshots, JUnit XML/HTML and fresh JSON reports are uploaded even when tests fail. Missing PASS markers cannot be replaced by a successful Gradle exit code. Timeouts bound every job and the multiplayer launcher terminates its own processes on failure.

These checks exercise implemented behavior. They do not establish subjective audio quality, physical speaker output, performance on a player's GPU, compatibility with other modpacks, remote network latency, or completion of the original future-work specification. The benchmark reports pure simulation time without imposing a misleading shared-runner performance threshold.

## Release policy

Automatic publication is limited to pushes to the repository variable **RELEASE_BRANCH**, currently `main` (also the workflow's fallback). Other branches and pull requests test and produce downloadable Actions artifacts but cannot publish. When development moves to another branch, change that variable deliberately. Feature branches are reviewed and integrated separately; their successful runs do not publish.

Each successful release has a unique tag `ci-<version>-<run-id>-<attempt>` pointing to the exact tested commit. Tags are never moved. The publisher downloads the same run's build artifact, verifies commit provenance and SHA-256 values, attaches fresh test evidence, creates a draft release with all assets, then publishes it and marks it Latest. A superseded branch commit cannot replace Latest. A failed CI run leaves the previous working release available.

Release assets:

- `autopropulsion-age-latest.jar`: permanent download filename for the newest successful build.
- `autopropulsion-age-<version>.jar`: identical versioned copy. Install only one JAR.
- `SHA256SUMS.txt`: checksums for both JARs, provenance and test evidence.
- `build-info.json`: exact commit, CI run URL, Minecraft/NeoForge/Java versions and JAR hashes.
- `test-evidence.zip`: logs, fresh reports and screenshots from the successful run.

These are clearly labeled **tested alpha** releases. They use GitHub's ordinary Latest release pointer so the permanent JAR URL works; Latest does not mean stable or feature complete. The old committed `downloads/` JAR remains a historical snapshot and is not the automatic release source.

All external Actions are pinned to full commit SHAs. Gradle validates its wrapper and distribution checksum. Test jobs have read-only repository permissions, and checkout does not retain credentials. Only the trusted branch's publishing job has `contents: write`; it does not check out or build project code. Pull-request jobs cannot publish. `Required checks` is the check to select if branch protection is enabled; this workflow does not itself configure branch protection.

## Local reproduction

Use Python 3.13, Java 21, ffmpeg and Blender on PATH. Linux native runs also need Xvfb, Mesa, Xauth and OpenAL. On Windows use `gradlew.bat` and run the Python launcher directly; it starts hidden processes.

```bash
python -m unittest discover -s tools/ci/tests -v
python tools/ci/documentation.py
./gradlew -PwithGameTests runClientUi
go run github.com/rhysd/actionlint/cmd/actionlint@v1.7.12
./gradlew --no-daemon -PwithGameTests build :sim:test --rerun runGameTestServer
python -m pip install -r tools/ci/requirements.txt
python tools/ci/resources.py
python tools/verify_powertrain_mesh.py
python tools/verify_engine_fit.py
blender --background --factory-startup --python-exit-code 1 --python tools/validate_engine_geometry.py
blender --background --factory-startup --python-exit-code 1 --python tools/validate_body_geometry.py
LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null xvfb-run -a ./gradlew -PwithGameTests runClientHandling
LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null xvfb-run -a ./gradlew -PwithGameTests runClientMechanics
LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null xvfb-run -a ./gradlew -PwithGameTests runClientSmoke
./gradlew -PwithGameTests prepareMultiplayerHarness
LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null xvfb-run -a python tools/run_multiplayer_test.py
```

Use an empty multiplayer output directory (`--output <path>`) each time. Release gates live in `tools/ci/checks.py`; the workflow shows how to validate each run's logs. Do not reuse old result files as proof of a new run.

## Reproducible Minecraft metadata

The build uses an [unmodified, checksum-verified Minecraft 1.21.1 metadata snapshot](../gradle/minecraft-metadata/README.md) from the successful local build. This removes dependence on NeoForge's live Mojang Meta generator, which returned HTTP 502 in two CI attempts. Dependency versions and all nine platform variants are unchanged; game and tool binaries still resolve through the normal repositories. This does not skip or weaken any test or release requirement.
