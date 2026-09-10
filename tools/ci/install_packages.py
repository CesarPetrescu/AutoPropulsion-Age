#!/usr/bin/env python3
"""CI-only Ubuntu package bootstrap, isolated from runner-added repositories.

Run with sudo on an Ubuntu 24.04 runner. Both update and install use the same
private sources, lists and caches; /etc/apt and global lists are never modified.
APT signatures, hashes and expiry checks remain mandatory. Requires Python 3.10+.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import platform
import shlex
import subprocess
import tempfile
import time

KEYRING = Path('/usr/share/keyrings/ubuntu-archive-keyring.gpg')
PROFILES = {
    'native': ('xvfb', 'xauth', 'libgl1-mesa-dri', 'libglx-mesa0',
               'mesa-utils', 'libopenal1', 'libasound2t64'),
    'assets': ('ffmpeg', 'blender'),
}


def ubuntu_sources(codename: str, architecture: str) -> str:
    """Use only official Ubuntu archives and their distribution signing key."""
    if codename != 'noble':
        raise ValueError(f'Expected Ubuntu 24.04 (noble), got {codename!r}')
    if architecture == 'amd64':
        archive = 'https://archive.ubuntu.com/ubuntu'
        security = 'https://security.ubuntu.com/ubuntu'
    elif architecture == 'arm64':
        archive = security = 'https://ports.ubuntu.com/ubuntu-ports'
    else:
        raise ValueError(f'Unsupported Ubuntu runner architecture: {architecture!r}')
    return '\n\n'.join(
        f'Types: deb\nURIs: {uri}\nSuites: {suites}\n'
        f'Components: main restricted universe multiverse\n'
        f'Architectures: {architecture}\nSigned-By: {KEYRING}'
        for uri, suites in ((archive, 'noble noble-updates noble-backports'),
                            (security, 'noble-security'))
    ) + '\n'


def apt_options(root: Path) -> list[str]:
    """The source AND index/cache scopes must match for update and install."""
    paths = {
        'Dir::Etc::sourcelist': root / 'ubuntu.sources',
        'Dir::Etc::sourceparts': root / 'sources.list.d',
        'Dir::State::lists': root / 'lists',
        'Dir::Cache::archives': root / 'archives',
        'Dir::Cache::pkgcache': root / 'pkgcache.bin',
        'Dir::Cache::srcpkgcache': root / 'srcpkgcache.bin',
    }
    values = paths | {
        'APT::Update::Error-Mode': 'any',
        'Acquire::Retries': '3',
        'Acquire::http::Timeout': '30',
        'Acquire::https::Timeout': '30',
        'DPkg::Lock::Timeout': '120',
        'Acquire::Languages': 'none',
    }
    return [item for key, value in values.items() for item in ('-o', f'{key}={value}')]


def prepare(root: Path, sources: str) -> None:
    # _apt needs traversal permission even when the parent is a root-owned mktemp.
    root.chmod(0o755)
    for name in ('sources.list.d', 'lists/partial', 'archives/partial'):
        (root / name).mkdir(parents=True, exist_ok=True)
    (root / 'ubuntu.sources').write_text(sources)
    (root / 'ubuntu.sources').chmod(0o644)


def retry(command: list[str], report: Path, name: str) -> int:
    """Retry failed APT commands, but never swallow a persistent error."""
    for attempt in range(1, 4):
        print(f'{name}, attempt {attempt}/3: {shlex.join(command)}', flush=True)
        # Timeouts propagate immediately: do not start a second dpkg transaction
        # while an interrupted transaction might still be shutting down.
        result = subprocess.run(command, text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, timeout=600,
                                env=os.environ | {'DEBIAN_FRONTEND': 'noninteractive'})
        text = result.stdout or ''
        (report / f'{name}-{attempt}.log').write_text(text)
        print(text, end='', flush=True)
        if result.returncode == 0:
            return attempt
        if attempt == 3 or result.returncode < 0:
            raise subprocess.CalledProcessError(result.returncode, command, output=text)
        time.sleep(5 * attempt)
    raise AssertionError('Unreachable retry state')


def install(profile: str, report: Path) -> None:
    packages = PROFILES[profile]
    release = platform.freedesktop_os_release()
    if release.get('ID') != 'ubuntu':
        raise ValueError('This bootstrap is for Ubuntu runners, not arbitrary Linux hosts')
    architecture = subprocess.check_output(['dpkg', '--print-architecture'], text=True).strip()
    sources = ubuntu_sources(release.get('VERSION_CODENAME', ''), architecture)
    if not KEYRING.is_file() or KEYRING.stat().st_size == 0:
        raise ValueError(f'Required Ubuntu signing keyring missing: {KEYRING}')
    report.mkdir(parents=True, exist_ok=True)
    (report / 'ubuntu.sources').write_text(sources)
    outcome = {'schema': 1, 'passed': False, 'profile': profile,
               'packages': packages, 'architecture': architecture,
               'codename': 'noble', 'source_scope': 'isolated-official-ubuntu'}
    try:
        # A fresh scope prevents stale Chrome/Microsoft/PPA indexes from being
        # candidates even after update has been restricted to Ubuntu sources.
        with tempfile.TemporaryDirectory(prefix='apa-ci-apt-', dir='/tmp') as folder:
            root = Path(folder)
            prepare(root, sources)
            base = ['apt-get', *apt_options(root)]
            outcome['update_attempts'] = retry([*base, 'update', '-q'], report, 'update')
            outcome['install_attempts'] = retry(
                [*base, 'install', '-y', '--no-install-recommends', *packages], report, 'install')
            versions = subprocess.check_output(
                ['dpkg-query', '-W', '-f=${Package}\t${Version}\t${db:Status-Abbrev}\n', *packages],
                text=True)
            (report / 'packages.tsv').write_text(versions)
            rows = [line.split('\t') for line in versions.splitlines()]
            if len(rows) != len(packages) or any(len(row) != 3 or row[2] != 'ii ' for row in rows):
                raise RuntimeError('APT completed but not every requested package is installed')
            outcome['passed'] = True
    except Exception as error:
        outcome['error'] = str(error)
        raise
    finally:
        (report / 'result.json').write_text(json.dumps(outcome, indent=2) + '\n')
    print('APT_BOOTSTRAP_PASS ' + profile, flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('profile', choices=PROFILES)
    parser.add_argument('--report', type=Path, default=Path('build/ci/apt'))
    args = parser.parse_args()
    if os.geteuid() != 0:
        parser.error('Run with sudo -n python3; package installation needs root')
    install(args.profile, args.report.resolve())


if __name__ == '__main__':
    main()
