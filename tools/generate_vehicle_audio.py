#!/usr/bin/env python3
"""Generate original, deterministic mono vehicle sounds. Requires Python 3 and ffmpeg.
No recordings or third-party audio are used. These are stylized game sounds, not
manufacturer recordings. All loops are periodic; files are Ogg Vorbis, 22050 Hz.
"""
from pathlib import Path
import array, hashlib, json, math, random, shutil, subprocess, tempfile, wave

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/sparkmotors'
OUT=ASSETS/'sounds/vehicle'
RATE=22050
FAMILIES={'i4':2,'v6':3,'flat4':2,'rotor1':1,'rotor2':2,'rotor3':3,'rotor4':4}
LOOPS=('turbo','supercharger','tire_roll','tire_skid','brake_squeal','brake_grind','damage_rattle','intake')
SHOTS=('starter','engine_stop','shift','bov','impact','tire_burst','engine_failure','part_break')
TAU=2*math.pi

def engine(family,units,tone,band):
    rpm=1800 if band=='low' else 4200
    freq=units*rpm/60
    slope={'stock':2.2,'sport':1.5,'open':1.12}[tone]
    result=[]
    for i in range(RATE):
        t=i/RATE
        phase=TAU*freq*t
        v=sum(math.sin(k*phase + (0.12*k if family.startswith('rotor') else 0))/k**slope for k in range(1,13))
        if family=='flat4':v*=.77+.23*math.sin(phase/2)
        if family.startswith('rotor'):v*=.83+.17*math.cos(TAU*int(units*10)*t)
        if family=='v6':v+=.1*math.sin(phase*1.5)
        result.append(math.tanh(v*1.4))
    return result

def effect(name):
    shot=name in SHOTS
    n=int(RATE*(.75 if shot else 1))
    rng=random.Random('sparkmotors-vehicle-audio-v1:'+name)
    raw=[rng.uniform(-1,1) for _ in range(n)]
    # Circular smoothing keeps the loop boundary continuous in the same sense as its interior.
    noise=[sum(raw[(i-k)%n] for k in range(5))/5 for i in range(n)]
    values=[]
    for i in range(n):
        t=i/RATE
        s=lambda f:math.sin(TAU*f*t)
        z=noise[i]
        if name=='turbo':v=.50*s(1100)+.22*s(2200)+.2*z
        elif name=='supercharger':v=.65*s(380)+.3*s(760)+.12*s(1520)
        elif name=='tire_roll':v=z*.75+.08*s(90)
        elif name=='tire_skid':v=.6*math.sin(TAU*780*t+(20/9)*math.sin(TAU*9*t))+.6*z
        elif name=='brake_squeal':v=.6*s(1680)+.2*s(2310)+.1*z
        elif name=='brake_grind':v=z*(.65+.35*s(60))+.25*s(310)
        elif name=='damage_rattle':v=(.5*s(181)+.25*s(509)+z)*(.2+.8*max(0,s(17))**6)
        elif name=='intake':v=.6*z+.22*s(240)+.1*s(480)
        elif name=='starter':v=(.4*s(95)+.3*s(190)+z*.3)*min(1,t*25)*math.exp(-t*2)
        elif name=='engine_stop':v=(s(60)+z*.15)*math.exp(-t*8)
        elif name=='shift':v=(z+.3*s(420))*math.exp(-t*28)
        elif name=='bov':v=z*min(1,t*100)*math.exp(-t*9)
        elif name=='impact':v=(z+s(74)*.5+s(317)*.3)*math.exp(-t*12)
        elif name=='tire_burst':v=(z+s(130)*.2)*math.exp(-t*22)
        elif name=='engine_failure':v=(z+.4*s(177)+.2*s(411))*math.exp(-t*8)
        else:v=(z+.4*s(601)+.3*s(1333))*math.exp(-t*12)
        if shot:v*=min(1,t*500, max(0,(n-i)/300))
        values.append(v)
    return values

def write(name,values,tmp,manifest):
    rms=math.sqrt(sum(v*v for v in values)/len(values))
    gain=min(.79/max(abs(v) for v in values),(.19 if name in SHOTS else .14)/max(rms,1e-9))
    pcm=array.array('h',(max(-32767,min(32767,round(v*gain*32767))) for v in values))
    import sys
    if sys.byteorder!='little':pcm.byteswap()
    wav=tmp/'source.wav'
    with wave.open(str(wav),'wb') as f:
        f.setnchannels(1);f.setsampwidth(2);f.setframerate(RATE);f.writeframes(pcm.tobytes())
    path=OUT/(name+'.ogg')
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(wav),'-map_metadata','-1','-c:a','libvorbis','-q:a','4',str(path)],check=True)
    manifest[name]={'samples':len(values),'sample_rate':RATE,'channels':1,'pcm_sha256':hashlib.sha256(pcm.tobytes()).hexdigest(),
        'rms':round(rms*gain,4),'peak':round(max(abs(v) for v in values)*gain,4),'loop':name not in SHOTS}

def main():
    if not shutil.which('ffmpeg'):raise SystemExit('ffmpeg is required. Install it before running this generator.')
    OUT.mkdir(parents=True,exist_ok=True)
    definitions=json.loads((ASSETS/'sounds.json').read_text()) if (ASSETS/'sounds.json').exists() else {}
    lang=json.loads((ASSETS/'lang/en_us.json').read_text())
    lang.update({"item.sparkmotors.stock_exhaust":"Stock Exhaust / Muffler","item.sparkmotors.sport_exhaust":"Sport Exhaust / Muffler"})
    manifest={}
    with tempfile.TemporaryDirectory() as temp:
        tmp=Path(temp)
        for family,units in FAMILIES.items():
            for tone in ('stock','sport','open'):
                for band in ('low','high'):
                    name=f'engine_{family}_{tone}_{band}';write(name,engine(family,units,tone,band),tmp,manifest)
        for name in LOOPS+SHOTS:write(name,effect(name),tmp,manifest)
    for name in manifest:
        subtitle='subtitles.sparkmotors.vehicle.'+name
        definitions[name]={'subtitle':subtitle,'sounds':[{'name':'sparkmotors:vehicle/'+name,'stream':False,'attenuation_distance':32}]}
        lang[subtitle]='Car engine running' if name.startswith('engine_') and name!='engine_failure' and name!='engine_stop' else name.replace('_',' ').capitalize()
    (ASSETS/'sounds.json').write_text(json.dumps(definitions,indent=2)+'\n')
    (ASSETS/'lang/en_us.json').write_text(json.dumps(lang,indent=2)+'\n')
    (OUT/'manifest.json').write_text(json.dumps({'generator':'generate_vehicle_audio.py v1','license':'CC0-1.0','sounds':manifest},indent=2)+'\n')
    print(f'Generated and registered {len(manifest)} original mono sounds')
if __name__=='__main__':main()
