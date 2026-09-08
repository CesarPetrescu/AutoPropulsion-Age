"""Fail-closed CI gates. Read fresh outputs; never use the curated docs as test results."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tomllib
import xml.etree.ElementTree as ET
import zipfile

REPO = Path(__file__).resolve().parents[2]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def junit(directory, minimum=52):
    files = sorted(directory.glob('TEST-*.xml'))
    require(files, 'Missing JUnit XML reports')
    cases = [case for file in files for case in ET.parse(file).iter('testcase')]
    require(len(cases) >= minimum, f'Expected at least {minimum} JUnit tests, got {len(cases)}')
    require(all(not list(case) or all(c.tag not in {'failure', 'error', 'skipped'} for c in case)
                for case in cases), 'JUnit failures, errors or skipped tests')
    for file in files:
        root = ET.parse(file).getroot()
        require(all(int(root.get(key, 0)) == 0 for key in ('failures', 'errors', 'skipped')),
                f'Unsuccessful JUnit suite: {file}')
    return {'junit_passed': len(cases), 'suites': len(files)}


def server(log, minimum=31):
    counts = re.findall(r'All (\d+) required tests passed', log)
    require(counts and int(counts[-1]) >= minimum, 'Missing complete dedicated GameTest PASS')
    require(not re.search(r'\d+ required tests failed|GameTest.*FAILED', log, re.I), 'Dedicated GameTest failure')
    require('DRIVETRAIN_SERVER_MATRIX_PASS 294' in log, 'Missing complete drivetrain/engine driving matrix')
    return {'dedicated_gametests_passed': int(counts[-1]), 'drivetrain_engine_cases': 294}


def client(log, result, mode):
    require(result.startswith('PASS:'), f'Client result is not PASS: {result[:200]}')
    require('ALPHA_CLIENT_SMOKE PASS:' in log and 'ALPHA_CLIENT_SMOKE FAILED' not in log,
            'Missing native client PASS or explicit failure present')
    if mode == 'ui':
        cases = set(re.findall(r'WORKSHOP_UI_CASE_PASS (\d+ [\w-]+)', log))
        require(len(cases) == 132 and 'WORKSHOP_UI_PASS 132' in log, 'Incomplete workshop page/scale coverage')
        require('MOD_LOGO_CLIENT_PASS' in log, 'Missing native loaded logo check')
        return {'workshop_page_scale_cases': 132, 'native_mod_logo': True, 'scaled_navigation': True}
    if mode == 'mechanics':
        for marker in ('MECHANICS_CLIENT_PASS', 'AUDIO_CHANNELS_AND_CLEANUP_PASS', 'INSTRUMENT_SENDER_PASS'):
            require(marker in log, f'Missing {marker}')
        return {'native_mechanics_passed': True, 'audio_channels_and_cleanup': True, 'sender_test': True}
    if mode == 'handling':
        for marker in ('HANDLING_CLIENT_PASS', 'AIRBORNE_LANDING_PASS'):
            require(marker in log, f'Missing {marker}')
        layouts = set(re.findall(r'DRIVE_LAYOUT_PASS (RWD|FWD|AWD)\b', log))
        require(layouts == {'RWD', 'FWD', 'AWD'}, 'Incomplete native drivetrain coverage')
        return {'native_drive_layouts_passed': sorted(layouts), 'airborne_landing': True}
    layouts = set(re.findall(r'ENGINE_LAYOUT_PASS ([\w-]+)', log))
    hardware = set(re.findall(r'HARDWARE_UI_PASS ([\w-]+)', log))
    require(len(layouts) == 49 and len(hardware) == 42,
            f'Incomplete native matrix: {len(layouts)}/49 layouts, {len(hardware)}/42 hardware choices')
    return {'native_layouts_passed': sorted(layouts), 'native_hardware_passed': sorted(hardware)}


def multiplayer(directory):
    result = json.loads((directory / 'result.json').read_text())
    require(result.get('passed') is True and result.get('clients') == 2, 'Dedicated multiplayer scenario failed')
    expected = {'ownership rejection', 'live entity synchronization', 'native item drop and pickup',
                'used part reinstallation in another owned car'}
    require(set(result.get('checks', [])) == expected, 'Multiplayer coverage is incomplete')
    require(not list(directory.glob('*.failed')), 'A native network client failed')
    for name in ('A', 'B'):
        require((directory / f'client-{name}.pass').read_text().startswith('PASS:'), f'Missing client {name} PASS')
        require(f'MULTIPLAYER_CLIENT_{name}_PASS' in (directory / f'runMultiClient{name}.log').read_text(errors='replace'),
                f'Missing actual network client {name} log PASS')
    return result


def inspect_jar(path):
    with zipfile.ZipFile(path) as archive:
        require(archive.testzip() is None, 'Corrupt JAR entry')
        names = set(archive.namelist())
        require(not any('/gametest/' in n or n.endswith('/structure/test_track.nbt') for n in names),
                'Development harness/test track leaked into release JAR')
        for name in ('META-INF/neoforge.mods.toml', 'com/photonspark/sparkmotors/entity/CarEntity.class',
                     'com/photonspark/sparkmotors/sim/MechanicalState.class',
                     'assets/sparkmotors/models/entity/sedan.mesh.gz', 'assets/sparkmotors/sounds.json'):
            require(name in names, f'Missing runtime entry: {name}')
        metadata = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode())
        mod = next(m for m in metadata['mods'] if m['modId'] == 'sparkmotors')
        require('${' not in mod['version'], 'Unexpanded mod version')
        logo = mod.get('logoFile', '')
        require(logo in names and logo.endswith('.png'), 'Missing declared mod logo in JAR')
        require(archive.read(logo).startswith(bytes.fromhex('89504e470d0a1a0a')), 'Packaged logo is not a PNG')
        sounds = json.loads(archive.read('assets/sparkmotors/sounds.json'))
        for event in sounds.values():
            for sound in event['sounds']:
                name = sound if isinstance(sound, str) else sound['name']
                namespace, name = name.split(':', 1) if ':' in name else ('minecraft', name)
                if namespace == 'sparkmotors':
                    require(f'assets/{namespace}/sounds/{name}.ogg' in names, f'Missing packaged sound: {name}')
        audio = [n for n in names if n.startswith('assets/sparkmotors/sounds/mechanical/') and n.endswith('.ogg')]
        require(len(audio) == 48, f'Expected 48 mechanical sound assets, got {len(audio)}')
        return mod['version']


def package(jar, output, sha, run_url):
    require(re.fullmatch(r'[0-9a-f]{40}', sha), 'Build provenance needs a full commit SHA')
    version = inspect_jar(jar)
    output.mkdir(parents=True, exist_ok=True)
    require(not list(output.iterdir()), 'Refusing to mix release artifacts with earlier output')
    for name in (f'autopropulsion-age-{version}.jar', 'autopropulsion-age-latest.jar'):
        shutil.copyfile(jar, output / name)
    info = {'version': version, 'commit': sha, 'run_url': run_url,
            'minecraft': '1.21.1', 'neoforge': '21.1.249', 'java': 21,
            'channel': 'tested alpha', 'jars': {p.name: digest(p) for p in sorted(output.glob('*.jar'))}}
    (output / 'build-info.json').write_text(json.dumps(info, indent=2) + '\n')
    (output / 'SHA256SUMS.txt').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in sorted(output.iterdir())))
    return info


def verify_package(directory, sha):
    info = json.loads((directory / 'build-info.json').read_text())
    require(info['commit'] == sha, 'Release artifact belongs to a different commit')
    checksums = {}
    for line in (directory / 'SHA256SUMS.txt').read_text().splitlines():
        expected, name = line.split('  ', 1)
        require(Path(name).name == name and name not in checksums, 'Unsafe/duplicate checksum filename')
        require(digest(directory / name) == expected, f'Checksum mismatch: {name}')
        checksums[name] = expected
    expected_files = set(info['jars']) | {'build-info.json'}
    require(set(checksums) == expected_files, 'Missing/extra checksummed release assets')
    require(set(p.name for p in directory.iterdir()) == expected_files | {'SHA256SUMS.txt'}, 'Unexpected release files')
    require(set(info['jars']) == {f"autopropulsion-age-{info['version']}.jar", 'autopropulsion-age-latest.jar'}, 'Missing installable JAR/alias')
    for name, expected in info['jars'].items():
        require(digest(directory / name) == expected, f'Build-info hash mismatch: {name}')
        require(inspect_jar(directory / name) == info['version'], 'JAR/version mismatch')
    require(len(set(info['jars'].values())) == 1, 'Latest alias differs from the versioned JAR')
    return info


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['junit', 'server', 'mechanics', 'matrix', 'handling', 'ui', 'multiplayer', 'package', 'verify-package'])
    parser.add_argument('--log', type=Path)
    parser.add_argument('--directory', type=Path)
    parser.add_argument('--result', type=Path, default=REPO / 'run/alpha-smoke-result.txt')
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--sha', default=os.environ.get('GITHUB_SHA', ''))
    parser.add_argument('--run-url', default='local')
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    if args.mode == 'junit':
        report = junit(args.directory or REPO / 'sim/build/test-results/test')
    elif args.mode == 'server':
        report = server(args.log.read_text(errors='replace'))
    elif args.mode in ('mechanics', 'matrix', 'handling', 'ui'):
        report = client(args.log.read_text(errors='replace'), args.result.read_text(), args.mode)
    elif args.mode == 'multiplayer':
        report = multiplayer(args.directory)
    elif args.mode == 'package':
        report = package(args.jar, args.directory, args.sha, args.run_url)
    else:
        report = verify_package(args.directory, args.sha)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
