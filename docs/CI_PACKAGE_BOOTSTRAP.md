# CI package bootstrap

## Incident and scope

[Main run #115](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/runs/34382769818/job/102571439376)
failed during `apt-get update`: Google's unrelated Chrome repository served an index
whose SHA-256 did not match its signed release metadata. The companion compilation,
circuit GameTests and combined client check passed; package setup still correctly
left the job red. This change repairs the bootstrap, not either mod's simulation.

## Installation policy

All workflows that install Ubuntu packages now share `tools/ci/install_packages.py`.
The native clients and ElectricalAge job also share
`.github/actions/setup-native-runtime/action.yml`, including a real Xvfb/Mesa probe.
The assets profile installs Blender and FFmpeg using the same package policy.

* Pin the operating system to `ubuntu-24.04`; reject an unexpected distribution or
  release instead of silently mixing repositories. Both amd64 and arm64 source
  definitions exist; this repository's CI currently exercises amd64.
* Generate a temporary deb822 source list using official Ubuntu archives, including
  updates/backports/security and `Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg`.
* Give **both update and install** the same private sources directory, package lists,
  binary cache and download cache. Existing Chrome/Microsoft/PPA entries and cached
  indexes are not consulted. `/etc/apt` and its original repositories are not deleted
  or edited, and no global APT configuration is changed.
* Fail updates on **any** error, including transient errors that APT might otherwise
  treat as a warning. Retry failed APT commands at most three times, with bounded
  acquisition retries, connection/lock/command timeouts and backoff. Persistent
  errors, signals and command timeouts remain fatal.
* Keep signature, package hash, TLS and metadata-expiry checks enabled. Do not use
  `trusted=yes`, `--allow-unauthenticated`, `--ignore-missing`, `|| true`, or
  `continue-on-error` to manufacture success.
* Record the selected sources, per-attempt logs, installed package versions and a
  JSON result. Pre-create the report directory as the runner user before invoking
  the installer with sudo, so the build directory does not become root-owned.

The ElectricalAge client now requires **both** a successful build and a successful
native setup. Evidence still uploads after failures. The required-checks gate and
main-only release policy remain unchanged. Historical audit workflows obtain the
bootstrap from the workflow revision in a separate sparse checkout, without
changing the historical production code under test.

## Verification

```sh
python -m unittest discover -s tools/ci/tests -p test_install_packages.py -v
mkdir -p build/ci/apt-smoke
sudo -n python3 tools/ci/apt_smoke.py --report build/ci/apt-smoke
```

The unit suite injects update/install failures, persistent errors, timeouts, missing
signing keys, incorrect host releases, and incomplete installed-package state. It
also checks workflow wiring and preserves the strict release gate.

The real APT test generates a temporary signing key and two localhost repositories.
The broad-source negative control receives a **validly signed Release but corrupted
Packages index** and must fail with `Hash Sum mismatch`. The same production APT
scope options exclude that unrelated source and successfully download a real test
`.deb`. Corrupting the package itself must still fail verification. No global source
is modified, no test package is installed, and the private signing key is deleted
with its temporary directory. Logs contain no private signing-key material.

The APT tests run in the required workflow-test job. The existing full build,
Minecraft clients/server, rendering, model, multiplayer and ELN jobs exercise live
Ubuntu installation and the resulting tools. A green CI run is evidence for that
exact revision, not a guarantee that Ubuntu or the network can never be unavailable.

References: [APT CLI and error mode](https://manpages.ubuntu.com/manpages/noble/man8/apt-get.8.html),
[APT configuration and source/list scopes](https://manpages.ubuntu.com/manpages/noble/man5/apt.conf.5.html).
