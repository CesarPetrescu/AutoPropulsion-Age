#!/usr/bin/env python3
"""Real APT isolation and checksum tests using disposable signed localhost repos.

Never changes /etc/apt, installs a package, or disables authentication. A private
signing key exists only inside the temporary fixture and is deleted afterwards.
"""
from __future__ import annotations
import argparse
import email.utils
import functools
import hashlib
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import subprocess
import tempfile
import threading
import time

from install_packages import apt_options, prepare


def checked(command: list[str]) -> str:
    return subprocess.check_output(command, stderr=subprocess.STDOUT, text=True, timeout=90)


def exercise(report: Path) -> None:
    report.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='apa-apt-fixture-', dir='/tmp') as folder:
        root = Path(folder); root.chmod(0o755)
        home = root / 'gnupg'; home.mkdir(mode=0o700)
        gpg = ['gpg', '--homedir', str(home), '--batch', '--pinentry-mode', 'loopback', '--passphrase', '']
        checked([*gpg, '--quick-generate-key', 'APT CI fixture <apt@example.invalid>', 'rsa2048', 'sign', '1d'])
        key = root / 'fixture.gpg'
        key.write_bytes(subprocess.check_output([*gpg, '--export'], stderr=subprocess.DEVNULL))
        package = root / 'package'; (package / 'DEBIAN').mkdir(parents=True)
        (package / 'DEBIAN/control').write_text(
            'Package: apa-ci-source-isolation-probe\nVersion: 1.0\nArchitecture: all\n'
            'Maintainer: CI <apt@example.invalid>\nDescription: Disposable APT download-only fixture\n')
        good = root / 'good'; (good / 'pool').mkdir(parents=True)
        deb = good / 'pool/probe.deb'
        checked(['dpkg-deb', '--build', '--root-owner-group', str(package), str(deb)])
        payload = deb.read_bytes()
        index = (
            'Package: apa-ci-source-isolation-probe\nVersion: 1.0\nArchitecture: all\n'
            'Maintainer: CI <apt@example.invalid>\nDescription: Disposable download-only fixture\n'
            f'Filename: pool/probe.deb\nSize: {len(payload)}\n'
            f'SHA256: {hashlib.sha256(payload).hexdigest()}\n\n').encode()
        architecture = checked(['dpkg', '--print-architecture']).strip()
        index_path = f'main/binary-{architecture}/Packages'
        for name in ('good', 'broken'):
            dist = root / name / 'dists/noble'
            (dist / index_path).parent.mkdir(parents=True)
            (dist / index_path).write_bytes(index)
            release = (
                'Origin: APA disposable CI fixture\nLabel: APA CI\nSuite: noble\nCodename: noble\n'
                f'Date: {email.utils.formatdate(time.time(), usegmt=True)}\n'
                f'Valid-Until: {email.utils.formatdate(time.time() + 86400, usegmt=True)}\n'
                f'Architectures: {architecture}\nComponents: main\nSHA256:\n'
                f' {hashlib.sha256(index).hexdigest()} {len(index)} {index_path}\n')
            (dist / 'Release').write_text(release)
            checked([*gpg, '--yes', '--digest-algo', 'SHA256', '--clearsign',
                     '--output', str(dist / 'InRelease'), str(dist / 'Release')])
        # Signed Release is valid, but downloaded Packages differs at identical length.
        (root / 'broken/dists/noble' / index_path).write_bytes(index.replace(b'Version: 1.0', b'Version: 9.0'))
        requests: list[str] = []
        class Handler(SimpleHTTPRequestHandler):
            def log_message(self, *args):
                pass
            def do_GET(self):
                requests.append(self.path)
                return super().do_GET()
        server = ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(Handler, directory=str(root)))
        thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
        try:
            def source(name: str) -> str:
                return (f'Types: deb\nURIs: http://127.0.0.1:{server.server_port}/{name}\n'
                        f'Suites: noble\nComponents: main\nArchitectures: {architecture}\nSigned-By: {key}\n')
            def scope(name: str) -> Path:
                path = root / name; path.mkdir(); prepare(path, source('good')); return path
            def run(command: list[str], label: str, success: bool) -> str:
                result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                        text=True, timeout=90)
                (report / f'{label}.log').write_text(result.stdout)
                if (result.returncode == 0) != success:
                    raise AssertionError(f'{label}: exit={result.returncode}\n{result.stdout}')
                if not success and 'Hash Sum mismatch' not in result.stdout:
                    raise AssertionError(f'{label} did not reproduce a checksum failure:\n{result.stdout}')
                print(f'APT_REAL_CASE_PASS {label} exit={result.returncode}', flush=True)
                return result.stdout
            # Ordinary sources + an unrelated signed-but-corrupt repo fail just as in run #115.
            broad = scope('unscoped')
            (broad / 'sources.list.d/unrelated.sources').write_text(source('broken'))
            run(['apt-get', *apt_options(broad), 'update', '-q'], 'unrelated-index-mismatch', False)
            if not any('/broken/' in path for path in requests):
                raise AssertionError('Negative control never contacted the broken repository')
            requests.clear()
            # Same production isolation options, now with a clean private sourceparts directory.
            private = scope('isolated')
            base = ['apt-get', *apt_options(private)]
            run([*base, 'update', '-q'], 'isolated-update', True)
            run([*base, 'install', '-y', '--download-only', '--no-install-recommends',
                 'apa-ci-source-isolation-probe'], 'isolated-download', True)
            downloaded = list((private / 'archives').glob('*.deb'))
            if len(downloaded) != 1 or downloaded[0].read_bytes() != payload:
                raise AssertionError('The real package was not downloaded and verified')
            if any('/broken/' in path for path in requests):
                raise AssertionError('Isolated APT still contacted an unrelated source')
            # Keeping a successful update must NOT let a tampered binary package be accepted.
            deb.write_bytes(payload[:-1] + bytes([payload[-1] ^ 1]))
            corrupt = scope('corrupt-download'); base = ['apt-get', *apt_options(corrupt)]
            run([*base, 'update', '-q'], 'intact-index-before-corrupt-package', True)
            run([*base, 'install', '-y', '--download-only', '--no-install-recommends',
                 'apa-ci-source-isolation-probe'], 'corrupt-package-rejected', False)
        finally:
            server.shutdown(); server.server_close(); thread.join(timeout=5)
            subprocess.run(['gpgconf', '--homedir', str(home), '--kill', 'all'], check=False)
    (report / 'result.json').write_text(json.dumps({
        'passed': True, 'cases': 5, 'apt': checked(['apt-get', '--version']).splitlines()[0],
        'scope': 'real APT + signed localhost fixtures; no package installed, no global sources changed'
    }, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, default=Path('build/ci/apt-smoke'))
    args = parser.parse_args()
    if os.geteuid() != 0:
        parser.error('Run with sudo for APT download-only lock handling')
    exercise(args.report.resolve())
