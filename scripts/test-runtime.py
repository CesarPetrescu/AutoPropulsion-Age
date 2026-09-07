#!/usr/bin/env python3
"""Real NeoForge development server and Xvfb clients. Loopback only, explicit EULA acceptance."""
import os,subprocess,time,signal,json,shutil,sys
from pathlib import Path
R=Path(__file__).resolve().parents[1];REPORT=R/'build/reports/runtime';REPORT.mkdir(parents=True,exist_ok=True)
if os.environ.get('ACCEPT_MINECRAFT_EULA')!='true':sys.exit('Set ACCEPT_MINECRAFT_EULA=true only after accepting https://aka.ms/MinecraftEULA for this isolated test.')
def stop(p):
 if p.poll() is None:
  try:p.stdin.write('stop\n');p.stdin.flush();p.wait(timeout=35)
  except (AttributeError,BrokenPipeError,subprocess.TimeoutExpired):
   os.killpg(p.pid,signal.SIGTERM)
   try:p.wait(timeout=15)
   except subprocess.TimeoutExpired:os.killpg(p.pid,signal.SIGKILL)
def prepare():
 server=R/'mod/run/server-smoke';server.mkdir(parents=True,exist_ok=True)
 (server/'eula.txt').write_text('eula=true\n')
 (server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25565\nonline-mode=false\nview-distance=5\nsimulation-distance=5\nmax-players=4\nspawn-protection=0\ngamemode=creative\ndifficulty=peaceful\nlevel-type=minecraft:flat\ngenerate-structures=false\nallow-flight=true\nmax-tick-time=120000\n')
 d=server/'defaultconfigs';d.mkdir(exist_ok=True)
 (d/'autopropulsion-server.toml').write_text('simulationSubsteps=4\nfuelConsumptionMultiplier=1.0\ndamageMultiplier=1.0\nallowGuestDriving=false\n')
 return server
results=[]
try:
 prepare()
 with (REPORT/'server-console.log').open('w') as log:
  server=subprocess.Popen(['./gradlew',':mod:runServerSmoke','--no-daemon','--console=plain','-Dorg.gradle.jvmargs=-Xmx1G'],cwd=R,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,text=True,start_new_session=True)
  try:
   deadline=time.monotonic()+900
   while True:
    text=(REPORT/'server-console.log').read_text(errors='replace')
    if 'Done (' in text:break
    if server.poll() is not None:raise RuntimeError('dedicated server exited before readiness')
    if time.monotonic()>deadline:raise TimeoutError('dedicated server readiness timeout')
    time.sleep(3)
   for profile in ['default','minimal']:
    run=R/'mod/run/client-smoke';run.mkdir(parents=True,exist_ok=True)
    (run/'smoke-result.txt').unlink(missing_ok=True)
    (run/'options.txt').write_text('renderDistance:5\nsimulationDistance:5\nmaxFps:30\nguiScale:2\nenableVsync:false\nfullscreen:false\ngraphicsMode:0\nparticles:2\nentityShadows:false\n')
    cmd=['xvfb-run','-a','-s','-screen 0 1280x720x24','./gradlew',':mod:runClientSmoke','--no-daemon','--console=plain','-Dorg.gradle.jvmargs=-Xmx1G',f'-PsmokeConfig={profile}']
    env=os.environ|{'LIBGL_ALWAYS_SOFTWARE':'1','ALSOFT_DRIVERS':'null'}
    with (REPORT/f'client-{profile}-console.log').open('w') as out:
     p=subprocess.Popen(cmd,cwd=R,env=env,stdout=out,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,text=True,start_new_session=True)
     try:rc=p.wait(timeout=900)
     except subprocess.TimeoutExpired:stop(p);raise TimeoutError(f'client {profile} timeout')
    result=(run/'smoke-result.txt').read_text().strip() if (run/'smoke-result.txt').exists() else 'FAIL no client sentinel'
    shots=REPORT/'screenshots';shots.mkdir(exist_ok=True)
    for image in (run/'screenshots').glob(f'autopropulsion-{profile}-*.png'):shutil.copy2(image,shots/image.name)
    results.append({'profile':profile,'exit_code':rc,'result':result,'passed':rc==0 and result.startswith('PASS ')})
    if not results[-1]['passed']:raise RuntimeError(f'client {profile}: {result}, exit {rc}')
  finally:stop(server)
except Exception as e:
 results.append({'passed':False,'error':str(e)});print('RUNTIME_FAILURE:',e);sys.exitcode=1
finally:
 (REPORT/'result.json').write_text(json.dumps({'schema':1,'tests':results,'passed':bool(results) and all(r.get('passed') for r in results)},indent=2)+'\n')
 print(json.dumps(results,indent=2))
sys.exit(getattr(sys,'exitcode',0))
