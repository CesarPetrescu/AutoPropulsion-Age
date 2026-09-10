# Which CI result matters, and which JAR should I install?

[![Main push CI](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/workflows/ci.yml/badge.svg?branch=main&event=push)](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/workflows/ci.yml?query=branch%3Amain+event%3Apush)

[Current main push runs](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/workflows/ci.yml?query=branch%3Amain+event%3Apush) · [Main source](https://github.com/CesarPetrescu/AutoPropulsion-Age/tree/main) · [Published release and checksums](https://github.com/CesarPetrescu/AutoPropulsion-Age/releases/latest)

## Two red jobs do not necessarily mean two defects

**Required checks is an aggregate release gate, not another Minecraft test.**
For example, a failed graphics-dependency install makes the ElectricalAge
compatibility job fail. Required checks then fails because that required job
failed. The second red check is the consequence of the first; inspect the
upstream job's actual failed step rather than changing the release gate.

The gate now writes a visible Actions job summary listing the required groups,
their actual results, run number, attempt, event, ref and exact tested checkout.
Matrix rows are aggregate groups: expand the client or visibility jobs to find
the individual failing mode. Failure, cancellation, skips, missing groups,
malformed JSON and missing results block publication. No previous green run is
substituted and no checks are disabled.

A PASS summary says only that the required groups passed for **that checkout**.
It is not confirmation that the publishing job has succeeded or that every
Minecraft configuration has been tested.

## Compare run IDs and commits before rerunning or making another PR

| Page or label | Meaning | Action |
|---|---|---|
| Old run linked from a message | Result for the commit recorded on that run | Compare its SHA with main; do not assume it represents the current code. |
| Current main run, still running | Verification has not finished | Wait. The older published release can remain Latest in the meantime. |
| Current main run, failed | There is a current blocker | Read the upstream failed step and preserve its logs. Fix that revision in a PR. |
| Current main run, all tests passed | Required testing completed | Also inspect Publish tested alpha JAR; a green gate alone is not a release. |
| Pull-request run, all tests passed | Candidate is ready for review within tested scope | It does not publish a release. The owner decides whether to merge. |
| Published release | A built, tested artifact with provenance | Verify its commit/build-info and install only one JAR alias. |

A PR checkout normally uses a synthetic merge commit (`refs/pull/N/merge`),
which may differ from its branch's head SHA. The summary labels `GITHUB_SHA` as
**Tested checkout**, not as the feature-branch head. On a main push it identifies
the commit built from main.

Re-running a historical workflow uses its **original `GITHUB_SHA` and
`GITHUB_REF`**. It does not pick up newly merged workflow fixes. Do not repeatedly
rerun a superseded failure, delete its history, or disable Required checks to
make the page green. Navigate to the current main push run instead.

## Example: the Chrome repository incident

These links are a **historical diagnosis**, not a hard-coded claim that today's
main is green:

- [Run #115 / failed compatibility job](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/runs/34382769818/job/102571439376): Google's unrelated Chrome APT index had a hash mismatch during dependency installation. Required checks then blocked publication.
- [PR #11](https://github.com/CesarPetrescu/AutoPropulsion-Age/pull/11): isolated official signed Ubuntu package sources for update and install, plus native-runtime setup checks. The APT fix remains in place; this reporting change does not replace it.
- [Post-merge main run #117](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/runs/34437469804): successful at `d83a27278da008a57931a7979dea87f4438e1006`, including compatibility, Required checks and publication.

Read [CI_PACKAGE_BOOTSTRAP.md](CI_PACKAGE_BOOTSTRAP.md) for the installer,
negative controls and remaining network-failure boundaries. No new PR can
retroactively change run #115's source or result.

## Verify the download, not just its version string

Multiple tested builds may share a version such as `0.9.1-alpha`. In the release,
compare the commit in `build-info.json` with the main build you intend to test.
Verify the JAR using `SHA256SUMS.txt`. The versioned JAR and
`autopropulsion-age-latest.jar` are identical aliases; install **one, not both**.
Back up your world and use the same build on client and server.

The Latest release may intentionally lag main after a pending or failed build.
A green badge, a merged PR, and a familiar JAR filename are not interchangeable
proof of an installed build. Shader/modpack/GPU and manual playtesting limits
still apply.

## Test the gate locally

```sh
python3 -m unittest discover -s tools/ci/tests -p test_required_checks.py -v
```

The tests include each required group's failure/cancellation/skip states,
missing/malformed context, duplicate result keys, extra failing dependencies,
summary writing and real CLI exit codes. They require no API token or network.

GitHub references: [status badge branch/event filters](https://docs.github.com/en/actions/how-tos/monitor-workflows/add-a-status-badge),
[workflow reruns](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs),
[job summaries](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-commands#adding-a-job-summary).
