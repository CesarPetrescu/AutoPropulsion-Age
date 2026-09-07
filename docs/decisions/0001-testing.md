# ADR 0001: execution environments and evidence-driven build decisions

Date: 2026-09-07. Scope: `feat/neoforge-1.21.1-vehicle-foundation`.

## Context and alternatives

| Approach | Benefit | Limitation observed or expected |
| --- | --- | --- |
| Local-only | Fast edits and direct inspection; offline simulation can run with javac | This assistant container could not resolve GitHub/Gradle hosts and had no installable Blender package |
| CI-only | Clean Ubuntu runner can fetch the complete NeoForge/Blender toolchain | Slower iteration; software-rendered client output is not representative of a player's GPU |
| Hybrid | Local deterministic/safety checks plus real server/client and asset execution in CI | Requires explicit evidence provenance; a CI run is not a local gameplay test |

**Decision: hybrid testing.** The local route was actually attempted, not assumed unavailable. Record blocked work separately from failed tests. Use CI for dependencies and graphical/dedicated-server execution; retrieve its source and evidence artifacts for independent local validation and visual review.

## Local observations

The container was Debian 13 with OpenJDK 21.0.11, `javac`, Xvfb and FFmpeg. `git clone` failed with `Could not resolve host: github.com`. The Blender package install exited 100 with `Unable to locate package blender`. Launching the corrected Gradle wrapper failed with `UnknownHostException: services.gradle.org`.

The CI source artifact was then downloaded through the connected GitHub artifact API and extracted locally. `bash scripts/test-sim.sh` passed all **35 baseline regressions**. Two additional defect probes were reproduced locally before fixes, then both passed after fixes. Local full Minecraft and Blender execution remains **blocked**, not passed.

## Blender: remove an optional denoising dependency

The initial CI runner installed Blender 4.0.2 successfully but failed with `Build without OpenImageDenoiser`. Geometry export was not the blocker; the preview render requested a component absent from that distribution build.

Decision: use CPU Cycles, explicit scene/view-layer denoising off and a fixed sampling configuration. Keep NumPy installed for the GLB exporter. Do not remove preview generation, swallow render exceptions or claim generation passed without output files. Run `34144929311` subsequently completed asset generation and committed the editable scenes and exported models. This authoring decision does not change the Minecraft renderer.

The initial visual inspection also found engine geometry above the hood and an over-cropped component contact sheet. A successful process exit therefore remains separate from visual acceptance; asset review and corrective work are recorded in [Assets](../assets.md).

## Gradle: keep integrity verification

Bootstrap fetched the distribution checksum URL without following redirects, writing `<html>` from an HTTP 301 response into `gradle-wrapper.properties`. The wrapper correctly refused the download.

Decision: pin the Gradle 9.2.1 binary checksum to `72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f`, independently checked against [Gradle's official checksum page](https://gradle.org/release-checksums/). Verify the wrapper JAR against its official checksum as well. Never bypass verification to turn the pipeline green.

## Tests: executable suites, not empty JUnit discovery

The baseline simulation uses dependency-free Java main-method suites so the same source can be tested in a network-restricted environment. On Gradle 9, its standard `Test` task failed after the real regression suite passed because no JUnit tests were discoverable.

Decision: keep the executable suites and explicitly connect both `test` and `check` to `regressionTest` and `safetyTest`; disable only the irrelevant JUnit-discovery task. Nonzero suite exit codes still fail the build. CI logs must show the actual test counts. This is a runner correction, not removal of a failing gameplay assertion.

## Transactional fixes driven by local failures

The first probe showed invalid saved RPM could modify live speed before throwing. The second showed replacing a provider part could remove a tag required by another installed component. Both probes failed on the original code and passed after validate-before-mutate changes. Permanent `SafetyTests` reproduce these cases.

## CI publication policy

Ordinary verification uses read-only repository permissions, checks the event checkout and never commits to `main`. Generated runtime resources are checked in so ordinary builds do not need Blender. One-time bootstrap asset publication used a feature-branch bot commit; ongoing regeneration and any explicit maintenance publication must be identified separately from normal verification. No workflow may force-push or silently merge a PR.

## Interpretation limits

A unit-test pass is not a successful Minecraft launch. A GameTest pass is not a rendered client test. A screenshot is not exhaustive gameplay validation. A software-rendered development client does not prove online-mode authentication, hardware rendering, shader compatibility, multiplayer latency or all configurations. The evidence matrix in [Testing](../testing.md) is the authority for what actually ran.
