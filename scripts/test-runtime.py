#!/usr/bin/env python3
"""Real NeoForge development server and Xvfb clients; loopback only, explicit EULA acceptance."""
import json
import os
from pathlib import Path
import shutil
import signal
import struct
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / 'build/reports/runtime'
CAPTURES = ('01-showroom', '02-garage', '03-dyno', '04-driving-hud', '05-braking')


def stop(process):
    if process.poll() is not None:
        return
    try:
        process.stdin.write('stop\n')
        process.stdin.flush()
        process.wait(timeout=35)
    except (AttributeError, BrokenPipeError, subprocess.TimeoutExpired):
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=10)


def validate_capture(path):
    """Presence/header check, not a claim that image contents received visual approval."""
    if not path.is_file() or path.stat().st_size < 1000:
        raise RuntimeError(f'Missing or empty screenshot: {path.name}')
    with path.open('rb') as source:
        header = source.read(24)
    if header[:8] != b'\x89PNG\r\n\x1a\n' or header[12:16] != b'IHDR':
        raise RuntimeError(f'Invalid screenshot header: {path.name}')
    dimensions = struct.unpack('>II', header[16:24])
    if dimensions != (1280, 720):
        raise RuntimeError(f'Unexpected screenshot dimensions {dimensions}: {path.name}')


def prepare():
    server = ROOT / 'mod/run/server-smoke'
    server.mkdir(parents=True, exist_ok=True)
    (server / 'eula.txt').write_text('eula=true\n')
    flat = json.dumps({'biome': 'minecraft:plains', 'layers': [
        {'block': 'minecraft:bedrock', 'height': 1},
        {'block': 'minecraft:dirt', 'height': 2},
        {'block': 'minecraft:grass_block', 'height': 1}]}, separators=(',', ':'))
    (server / 'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=25565\nonline-mode=false\n'
        'view-distance=5\nsimulation-distance=5\nmax-players=4\nspawn-protection=0\n'
        'gamemode=creative\ndifficulty=peaceful\nlevel-type=minecraft:flat\n'
        'generator-settings=' + flat + '\ngenerate-structures=false\n'
        'allow-flight=true\nmax-tick-time=120000\n')
    defaults = server / 'defaultconfigs'
    defaults.mkdir(exist_ok=True)
    (defaults / 'autopropulsion-server.toml').write_text(
        'simulationSubsteps=4\nfuelConsumptionMultiplier=1.0\n'
        'damageMultiplier=1.0\nallowGuestDriving=false\n')


def main():
    if os.environ.get('ACCEPT_MINECRAFT_EULA') != 'true':
        sys.exit('Set ACCEPT_MINECRAFT_EULA=true only after accepting '
                 'https://aka.ms/MinecraftEULA for this isolated test.')
    REPORT.mkdir(parents=True, exist_ok=True)
    results = []
    code = 0
    try:
        prepare()
        server_log = REPORT / 'server-console.log'
        with server_log.open('w') as log:
            server = subprocess.Popen(
                ['./gradlew', ':mod:runServerSmoke', '--no-daemon', '--console=plain',
                 '-Dorg.gradle.jvmargs=-Xmx1G'], cwd=ROOT, stdout=log,
                stderr=subprocess.STDOUT, stdin=subprocess.PIPE, text=True, start_new_session=True)
            try:
                deadline = time.monotonic() + 900
                while 'Done (' not in server_log.read_text(errors='replace'):
                    if server.poll() is not None:
                        raise RuntimeError('Dedicated server exited before readiness')
                    if time.monotonic() > deadline:
                        raise TimeoutError('Dedicated server readiness timeout')
                    time.sleep(3)
                for profile in ('default', 'minimal'):
                    run = ROOT / 'mod/run/client-smoke'
                    run.mkdir(parents=True, exist_ok=True)
                    (run / 'smoke-result.txt').unlink(missing_ok=True)
                    for old in (run / 'screenshots').glob(f'autopropulsion-{profile}-*.png'):
                        old.unlink()
                    (run / 'options.txt').write_text(
                        'renderDistance:5\nsimulationDistance:5\nmaxFps:30\nguiScale:2\n'
                        'enableVsync:false\nfullscreen:false\ngraphicsMode:0\nparticles:2\n'
                        'entityShadows:false\ntutorialStep:none\n')
                    command = ['xvfb-run', '-a', '-s', '-screen 0 1280x720x24', './gradlew',
                               ':mod:runClientSmoke', '--no-daemon', '--console=plain',
                               '-Dorg.gradle.jvmargs=-Xmx1G', f'-PsmokeConfig={profile}']
                    environment = os.environ | {'LIBGL_ALWAYS_SOFTWARE': '1', 'ALSOFT_DRIVERS': 'null'}
                    client_log = REPORT / f'client-{profile}-console.log'
                    with client_log.open('w') as out:
                        process = subprocess.Popen(command, cwd=ROOT, env=environment, stdout=out,
                                                   stderr=subprocess.STDOUT, stdin=subprocess.PIPE,
                                                   text=True, start_new_session=True)
                        try:
                            return_code = process.wait(timeout=900)
                        except subprocess.TimeoutExpired:
                            stop(process)
                            raise TimeoutError(f'Client {profile} timeout')
                    sentinel = run / 'smoke-result.txt'
                    result = sentinel.read_text().strip() if sentinel.exists() else 'FAIL no client sentinel'
                    passed = return_code == 0 and result.startswith('PASS ')
                    entry = {'profile': profile, 'exit_code': return_code, 'result': result,
                             'passed': passed, 'screenshots': []}
                    results.append(entry)
                    if not passed:
                        raise RuntimeError(f'Client {profile}: {result}, exit {return_code}')
                    if 'Loaded Blender vehicle mesh: 7 groups' not in client_log.read_text(errors='replace'):
                        entry['passed'] = False
                        raise RuntimeError(f'Client {profile} did not load the vehicle mesh')
                    shots = REPORT / 'screenshots'
                    shots.mkdir(exist_ok=True)
                    for capture in CAPTURES:
                        image = run / 'screenshots' / f'autopropulsion-{profile}-{capture}.png'
                        try:
                            validate_capture(image)
                        except RuntimeError:
                            entry['passed'] = False
                            raise
                        shutil.copy2(image, shots / image.name)
                        entry['screenshots'].append(image.name)
            finally:
                stop(server)
    except Exception as error:
        results.append({'passed': False, 'error': str(error)})
        print('RUNTIME_FAILURE:', error)
        code = 1
    finally:
        (REPORT / 'result.json').write_text(json.dumps(
            {'schema': 2, 'tests': results, 'passed': bool(results) and all(r.get('passed') for r in results)},
            indent=2) + '\n')
        print(json.dumps(results, indent=2))
    return code


if __name__ == '__main__':
    sys.exit(main())
