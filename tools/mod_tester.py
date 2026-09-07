#!/usr/bin/env python3
"""Run a loopback-only dedicated server and actual Fast/Fancy/Fabulous Minecraft clients.
Requires Java 21, Xvfb/Mesa and MC_EULA=true. Uses a marked disposable development world.
"""
from __future__ import annotations
import json, os, re, signal, subprocess, time
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'reports/minecraft'
SERVER = ROOT / 'mod/run/server'
CLIENT = ROOT / 'mod/run/client'

def text(path):
    return path.read_text(errors='replace') if path.exists() else ''

def terminate(process):
    if process.poll() is None:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()

def main():
    if os.environ.get('MC_EULA') != 'true':
        raise SystemExit('Set MC_EULA=true after reviewing the Minecraft EULA to run disposable tests.')
    OUT.mkdir(parents=True, exist_ok=True)
    SERVER.mkdir(parents=True, exist_ok=True)
    CLIENT.mkdir(parents=True, exist_ok=True)
    marker = SERVER / '.apa-disposable-test-world'
    if ((SERVER / 'world').exists() or (SERVER / 'apa-smoke-world').exists()) and not marker.exists():
        raise SystemExit('Refusing to use an existing unmarked server world.')
    marker.touch()
    (SERVER / 'eula.txt').write_text('eula=true\n')
    properties = {
        'server-ip': '127.0.0.1', 'server-port': '25565', 'online-mode': 'false',
        'enforce-secure-profile': 'false', 'level-name': 'apa-smoke-world', 'level-type': 'minecraft:flat',
        'generator-settings': json.dumps({'biome': 'minecraft:plains', 'layers': [
            {'block': 'minecraft:bedrock', 'height': 1}, {'block': 'minecraft:dirt', 'height': 2},
            {'block': 'minecraft:grass_block', 'height': 1}]}),
        'generate-structures': 'false', 'gamemode': 'creative', 'difficulty': 'peaceful',
        'spawn-protection': '0', 'view-distance': '5', 'simulation-distance': '5', 'max-players': '4',
        'allow-flight': 'true', 'sync-chunk-writes': 'false', 'max-tick-time': '120000'
    }
    (SERVER / 'server.properties').write_text(''.join(k + '=' + v + '\n' for k, v in properties.items()))
    # Seed only the first-run test client. Never overwrite a developer's existing options.
    if not (CLIENT / 'options.txt').exists():
        (CLIENT / 'options.txt').write_text('onboardAccessibility:false\nskipMultiplayerWarning:true\nrenderDistance:5\nsimulationDistance:5\nguiScale:2\nnarrator:0\n')
    result = {'status': 'running', 'profiles': [], 'server_bound_to': '127.0.0.1',
              'authentication': 'offline mode only in disposable loopback-bound CI server'}
    env = dict(os.environ, LIBGL_ALWAYS_SOFTWARE='1', ALSOFT_DRIVERS='null')
    server_log = OUT / 'dedicated-server.log'
    with server_log.open('w') as output:
        server = subprocess.Popen(['./gradlew', '--project-cache-dir', '.gradle-smoke-server',
            '-Dapa.serverSmoke=true', ':mod:runServer', '--stacktrace'], cwd=ROOT, env=env,
            stdout=output, stderr=subprocess.STDOUT, stdin=subprocess.PIPE, text=True, start_new_session=True)
        try:
            deadline = time.monotonic() + 600
            while 'Done (' not in text(server_log):
                if server.poll() is not None:
                    raise RuntimeError('Dedicated server exited before readiness; inspect dedicated-server.log')
                if time.monotonic() > deadline:
                    raise TimeoutError('Dedicated server did not become ready')
                time.sleep(.5)
            print('APA_TESTER_SERVER_READY', flush=True)
            for profile in ('fast', 'fancy', 'fabulous'):
                print('APA_TESTER_CLIENT_START=' + profile, flush=True)
                for old in (CLIENT / 'screenshots').glob(f'apa-{profile}-*.png'):
                    old.unlink()
                path = OUT / f'client-{profile}.log'
                with path.open('w') as log:
                    client = subprocess.Popen(['xvfb-run', '-a', './gradlew', '--project-cache-dir',
                        '.gradle-smoke-client', '-Dapa.clientSmoke=true', f'-Dapa.profile={profile}',
                        f'-Dapa.username=APA_{profile}', ':mod:runClient', '--stacktrace'], cwd=ROOT,
                        env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
                    try:
                        code = client.wait(timeout=600)
                    except subprocess.TimeoutExpired:
                        terminate(client)
                        raise TimeoutError(f'{profile}: client did not finish')
                contents = text(path)
                distance = re.search(r'APA_CLIENT_DRIVE_DISTANCE_METRES=([0-9.]+)', contents)
                expected = ['parked', 'driving-hud', 'hood', 'diagnostics', 'dyno', 'parts']
                screenshots = [CLIENT / 'screenshots' / f'apa-{profile}-{name}.png' for name in expected]
                passed = (code == 0 and 'APA_CLIENT_SMOKE_COMPLETE' in contents
                    and 'APA_CLIENT_SMOKE_FAILED' not in contents and distance is not None
                    and float(distance.group(1)) > 5 and all(p.is_file() for p in screenshots))
                entry = {'profile': profile, 'passed': passed, 'exit_code': code,
                    'distance_m': float(distance.group(1)) if distance else None,
                    'screenshots': [str(p.relative_to(ROOT)) for p in screenshots if p.exists()]}
                result['profiles'].append(entry)
                print('APA_TESTER_CLIENT_RESULT=' + json.dumps(entry), flush=True)
                (OUT / 'test-report.json').write_text(json.dumps(result, indent=2) + '\n')
                if not passed:
                    raise RuntimeError(f'{profile}: actual world/GUI smoke assertions failed; inspect {path.name}')
            result['status'] = 'passed'
        except Exception as error:
            result['status'] = 'failed'
            result['error'] = str(error)
            raise
        finally:
            if server.poll() is None and server.stdin:
                try:
                    server.stdin.write('save-all\nstop\n')
                    server.stdin.flush()
                    server.wait(timeout=30)
                except (BrokenPipeError, subprocess.TimeoutExpired):
                    terminate(server)
            (OUT / 'test-report.json').write_text(json.dumps(result, indent=2) + '\n')
    print('MINECRAFT_CLIENT_SERVER_TESTS_PASSED ' + json.dumps(result), flush=True)

if __name__ == '__main__':
    main()
