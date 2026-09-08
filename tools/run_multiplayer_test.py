"""Run the real dedicated server and two native clients, with bounded lifetime and cleanup.

First run ./gradlew -PwithGameTests prepareMultiplayerHarness. On Linux run this
script inside xvfb-run; no desktop input, external Minecraft accounts or public port are used.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
from ci.checks import multiplayer

REPO = Path(__file__).resolve().parents[1]


def java_quote(value):
    return '"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"'


def run(output, timeout):
    output = output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    if list(output.iterdir()):
        raise ValueError('Use an empty output directory; previous PASS files must not satisfy a new run')
    launches = json.loads((REPO / 'build/multiplayer-launches.json').read_text())
    server = Path(launches['runMultiServer']['workingDirectory'])
    server.mkdir(parents=True, exist_ok=True)
    (server / 'eula.txt').write_text('eula=true\n')
    (server / 'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=25576\nonline-mode=false\nview-distance=4\n'
        'simulation-distance=4\nspawn-protection=0\ngamemode=survival\ndifficulty=peaceful\n'
        f'level-type=minecraft:flat\nlevel-name=mechanical-network-{time.time_ns()}\nmax-tick-time=120000\n')
    processes, logs = {}, []
    deadline = time.monotonic() + timeout

    def start(name):
        spec = launches[name]
        Path(spec['workingDirectory']).mkdir(parents=True, exist_ok=True)
        # Java @argfiles cannot nest. Merge ModDev's VM and program files with its
        # argument providers (including FML mod folders), using Java's own quoting.
        args = [Path(spec['vmArgs']).read_text(), *map(java_quote, spec['jvmArguments']),
                java_quote('-Dsparkmotors.multiRoot=' + str(output)), '-Xmx2G', '-classpath',
                java_quote(spec['classpath']), Path(spec['programArgs']).read_text()]
        argfile = output / f'{name}.args'
        argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
        env = os.environ.copy()
        if spec.get('modClasses'):
            env['MOD_CLASSES'] = spec['modClasses']
        else:
            env.pop('MOD_CLASSES', None)
        log = (output / f'{name}.log').open('w', encoding='utf-8')
        logs.append(log)
        processes[name] = subprocess.Popen([spec['java'], '@' + str(argfile)], cwd=spec['workingDirectory'],
            env=env, stdout=log, stderr=subprocess.STDOUT,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        print(f'Started {name}: pid={processes[name].pid}', flush=True)

    def check_time_and_failure():
        if time.monotonic() > deadline:
            raise TimeoutError(f'Multiplayer harness exceeded {timeout}s; inspect {output}')
        failures = list(output.glob('*.failed'))
        if failures:
            raise RuntimeError('\n'.join(p.read_text() for p in failures))
        result = output / 'result.json'
        if result.exists() and json.loads(result.read_text()).get('passed') is not True:
            raise RuntimeError(result.read_text())

    try:
        start('runMultiServer')
        while not (output / 'ready.txt').exists():
            check_time_and_failure()
            if processes['runMultiServer'].poll() is not None:
                raise RuntimeError('Dedicated server exited before readiness')
            time.sleep(1)
        start('runMultiClientA')
        start('runMultiClientB')
        while any(p.poll() is None for p in processes.values()):
            check_time_and_failure()
            for name, process in processes.items():
                if process.poll() is not None:
                    marker = 'result.json' if name == 'runMultiServer' else f'client-{name[-1]}.pass'
                    if process.returncode != 0 or not (output / marker).exists():
                        raise RuntimeError(f'{name} exited {process.returncode} without successful completion')
            time.sleep(1)
        if any(p.returncode != 0 for p in processes.values()):
            raise RuntimeError('A native process failed during shutdown')
        print(json.dumps(multiplayer(output), indent=2), flush=True)
    finally:
        for process in processes.values():
            if process.poll() is None:
                process.terminate()
        for process in processes.values():
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=15)
        for log in logs:
            log.close()
        (output / 'processes.json').write_text(json.dumps({n: {'pid': p.pid, 'exit': p.returncode}
                                                        for n, p in processes.items()}, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=REPO / 'build/ci/multiplayer')
    parser.add_argument('--timeout', type=int, default=720)
    args = parser.parse_args()
    run(args.output, args.timeout)
