#!/usr/bin/env python3
"""Launch an isolated dedicated server and three actual Minecraft clients under Xvfb.
Requires Java 21, an initialized Gradle wrapper, Xvfb/Mesa, and MC_EULA=true.
Never points at an existing world. No Minecraft binaries are included in artifacts.
"""
from __future__ import annotations
import json, os, re, signal, subprocess, time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'reports/minecraft'; OUT.mkdir(parents=True,exist_ok=True)
SERVER=ROOT/'mod/run/server'

def text(path): return path.read_text(errors='replace') if path.exists() else ''
def terminate(process):
    if process.poll() is None:
        os.killpg(process.pid,signal.SIGTERM)
        try: process.wait(timeout=20)
        except subprocess.TimeoutExpired: os.killpg(process.pid,signal.SIGKILL); process.wait()

def main():
    if os.environ.get('MC_EULA')!='true': raise SystemExit('Set MC_EULA=true after reviewing the Minecraft EULA to run these disposable tests.')
    SERVER.mkdir(parents=True,exist_ok=True)
    marker=SERVER/'.apa-disposable-test-world'
    if (SERVER/'world').exists() and not marker.exists(): raise SystemExit('Refusing to overwrite an existing development/server world.')
    marker.touch()
    (SERVER/'eula.txt').write_text('eula=true\n')
    properties={'server-ip':'127.0.0.1','server-port':'25565','online-mode':'false','enforce-secure-profile':'false','level-name':'apa-smoke-world','level-type':'minecraft:flat','generate-structures':'false','gamemode':'creative','difficulty':'peaceful','spawn-protection':'0','view-distance':'5','simulation-distance':'5','max-players':'4','allow-flight':'true','sync-chunk-writes':'false','max-tick-time':'120000'}
    (SERVER/'server.properties').write_text(''.join(k+'='+v+'\n' for k,v in properties.items()))
    result={'status':'running','profiles':[],'server_bound_to':'127.0.0.1','authentication':'offline mode only in disposable loopback-bound CI server'}
    env=dict(os.environ,LIBGL_ALWAYS_SOFTWARE='1',ALSOFT_DRIVERS='null')
    server_log=OUT/'dedicated-server.log'
    with server_log.open('w') as output:
        server=subprocess.Popen(['./gradlew','--project-cache-dir','.gradle-smoke-server','-Dapa.serverSmoke=true',':mod:runServer','--stacktrace'],cwd=ROOT,env=env,stdout=output,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,text=True,start_new_session=True)
        try:
            deadline=time.monotonic()+600
            while 'Done (' not in text(server_log):
                if server.poll() is not None: raise RuntimeError('Dedicated server exited before readiness; see dedicated-server.log')
                if time.monotonic()>deadline: raise TimeoutError('Dedicated server did not become ready')
                time.sleep(.5)
            for profile in ('fast','fancy','fabulous'):
                path=OUT/f'client-{profile}.log'
                with path.open('w') as log:
                    client=subprocess.Popen(['xvfb-run','-a','./gradlew','--project-cache-dir','.gradle-smoke-client','-Dapa.clientSmoke=true',f'-Dapa.profile={profile}',f'-Dapa.username=APA_{profile}',':mod:runClient','--stacktrace'],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
                    try: code=client.wait(timeout=600)
                    except subprocess.TimeoutExpired: terminate(client); raise TimeoutError(f'{profile}: client did not finish')
                contents=text(path)
                distance=re.search(r'APA_CLIENT_DRIVE_DISTANCE_METRES=([0-9.]+)',contents)
                screenshots=sorted((ROOT/'mod/run/client/screenshots').glob(f'apa-{profile}-*.png'))
                passed=code==0 and 'APA_CLIENT_SMOKE_COMPLETE' in contents and 'APA_CLIENT_SMOKE_FAILED' not in contents and distance is not None and float(distance.group(1))>5 and len(screenshots)>=6
                result['profiles'].append({'profile':profile,'passed':passed,'exit_code':code,'distance_m':float(distance.group(1)) if distance else None,'screenshots':[str(p.relative_to(ROOT)) for p in screenshots]})
                (OUT/'test-report.json').write_text(json.dumps(result,indent=2)+'\n')
                if not passed: raise RuntimeError(f'{profile}: actual world/GUI smoke assertions failed; inspect {path.name}')
            result['status']='passed'
        except Exception as error:
            result['status']='failed'; result['error']=str(error); raise
        finally:
            if server.poll() is None and server.stdin:
                try: server.stdin.write('save-all\nstop\n'); server.stdin.flush(); server.wait(timeout=30)
                except (BrokenPipeError,subprocess.TimeoutExpired): terminate(server)
            (OUT/'test-report.json').write_text(json.dumps(result,indent=2)+'\n')
    print('MINECRAFT_CLIENT_SERVER_TESTS_PASSED '+json.dumps(result))

if __name__=='__main__': main()
