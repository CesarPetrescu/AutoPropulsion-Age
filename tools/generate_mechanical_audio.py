"""Original deterministic synthesis, mono positional OGG assets and a playable listening reel.

These are designed game sounds, not recordings of production engines. This script owns only
the events listed in audio-manifest.json; other authored sound definitions are retained.
"""
from pathlib import Path
import hashlib,json,math,subprocess,wave
import numpy as np

repo=Path(__file__).resolve().parents[1];assets=repo/'src/main/resources/assets/sparkmotors'
out=assets/'sounds/mechanical';out.mkdir(parents=True,exist_ok=True)
work=repo/'.codex-reference/audio';work.mkdir(parents=True,exist_ok=True)
rate=44100;frames={};report={}
families={'i4':(2,1.2,.05),'v6':(3,1.45,.02),'flat4':(2,1.0,.24),'rotor1':(1,.82,.18),'rotor2':(2,.86,.12),'rotor3':(3,.9,.08),'rotor4':(4,.95,.04)}
def export(name,signal,loop=True):
    signal=np.asarray(signal,dtype=np.float64);signal-=signal.mean();signal=signal/max(1,np.max(np.abs(signal)))*.82
    if not loop:signal*=np.minimum(np.arange(len(signal))/220,1)*np.minimum(np.arange(len(signal))[::-1]/1200,1)
    # Matching periodic synthesis and a short equal-power seam prevent loop clicks.
    if loop:
        n=300;seam=(signal[:n]+signal[-n:])/2;signal[:n]=signal[:n]*np.linspace(0,1,n)+seam*np.linspace(1,0,n);signal[-n:]=signal[-n:]*np.linspace(1,0,n)+seam*np.linspace(0,1,n)
        signal[-1]=signal[0]
    frames[name]=signal
    path=work/(name+'.wav')
    with wave.open(str(path),'wb') as f:f.setnchannels(1);f.setsampwidth(2);f.setframerate(rate);f.writeframes((signal*32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(path),'-c:a','libvorbis','-q:a','5',str(out/(name+'.ogg'))],check=True)
    report[name]={'channels':1,'rate':rate,'seconds':len(signal)/rate,'peak':float(np.max(np.abs(signal))),'rms':float(np.sqrt(np.mean(signal**2))),'loop':loop}
for family,(pulses,rolloff,uneven) in families.items():
    for band,rpm in [('idle',900),('low',3000),('high',5800),('coast',3000)]:
        t=np.arange(rate*3)/rate;fund=round(rpm/60*pulses*3)/3
        signal=np.zeros(len(t));phase=2*np.pi*fund*t+uneven*np.sin(2*np.pi*fund/2*t)
        for k in range(1,22):
            attenuation=k**(rolloff+(0.65 if band=='coast' else 0))
            color=1 if family.startswith('rotor') else (1 if k%2 else .65)
            signal+=color*np.sin(k*phase+.1*k*k)/attenuation
        if family=='flat4':signal*=.8+.2*np.cos(2*np.pi*fund/2*t)
        signal=np.tanh(signal*(.8 if band=='idle' else 1.2))
        export(family+'_'+band,signal)
for name in ['intake','exhaust_stock','exhaust_sport','exhaust_open','turbo','centrifugal','roots','twin_screw','boost_leak','road','gravel','tire_slip','brake_grind','bearing','knock','starter','release','latch','impact','horn']:
    rng=np.random.default_rng(int.from_bytes(hashlib.sha256(name.encode()).digest()[:4],'little'));loop=name not in ['release','latch','impact','horn'];seconds=3 if loop else .65 if name=='release' else .25 if name=='latch' else .8
    t=np.arange(int(rate*seconds))/rate;noise=rng.normal(0,.25,len(t));smooth=np.convolve(noise,np.ones(15)/15,mode='same')
    if name.startswith('exhaust'):
        bright={'exhaust_stock':2.0,'exhaust_sport':1.35,'exhaust_open':.85}[name];signal=sum(np.sin(2*np.pi*100*k*t)/k**bright for k in range(1,18))*.5+smooth
    elif name in ['turbo','centrifugal','roots','twin_screw']:
        hz={'turbo':1600,'centrifugal':1150,'roots':350,'twin_screw':720}[name];signal=.38*np.sin(2*np.pi*hz*t)+.18*np.sin(2*np.pi*hz*2*t)+.1*np.sin(2*np.pi*hz*3*t)+smooth*.2
        if name=='roots':signal+=.2*np.sin(2*np.pi*100*t)
    elif name=='tire_slip':signal=.3*np.sin(2*np.pi*850*t+.8*np.sin(2*np.pi*17*t))+smooth
    elif name=='brake_grind':signal=noise*(.6+.4*np.sin(2*np.pi*24*t))+np.sin(2*np.pi*270*t)*.15
    elif name=='bearing':signal=.35*np.sin(2*np.pi*180*t)*(.7+.3*np.sin(2*np.pi*9*t))+smooth
    elif name=='knock':signal=np.sin(2*np.pi*1250*t)*np.exp(-((t*30)%1)*12)+smooth*.2
    elif name=='starter':signal=.3*np.sin(2*np.pi*300*t)+.15*np.sin(2*np.pi*600*t)+smooth;signal*=.75+.25*np.sin(2*np.pi*7*t)
    elif name=='release':signal=noise*np.exp(-t*7)+.16*np.sin(2*np.pi*(1800*t-650*t*t))*np.exp(-t*11)
    elif name in ['latch','impact']:signal=(noise+sum(np.sin(2*np.pi*f*t) for f in [173,431,973])*.15)*np.exp(-t*(30 if name=='latch' else 8))
    elif name=='horn':signal=.35*np.sin(2*np.pi*350*t)+.35*np.sin(2*np.pi*440*t)
    elif name=='gravel':signal=smooth+noise*(rng.random(len(t))>.998)*2
    else:signal=smooth if name=='road' else noise*.6+smooth*.5
    export(name,signal,loop)
manifest={name:{'subtitle':'subtitles.sparkmotors.'+name,'sounds':[{'name':'sparkmotors:mechanical/'+name,'stream':False}]} for name in frames}
path=assets/'sounds.json';sounds=json.loads(path.read_text());sounds.update(manifest);path.write_text(json.dumps(sounds,indent=2)+'\n')
(repo/'docs/audio-manifest.json').write_text(json.dumps(report,indent=2)+'\n')
langpath=assets/'lang/en_us.json';lang=json.loads(langpath.read_text());lang.update({'subtitles.sparkmotors.'+name:name.replace('_',' ').capitalize() for name in frames});langpath.write_text(json.dumps(lang,indent=2)+'\n')
# A listening reel of the actual source assets, with the family names/timeline documented alongside it.
clips=[];timeline=[];offset=0
for family in families:
    sig=frames[family+'_low'][:rate*2]*.32+frames['exhaust_stock'][:rate*2]*.12
    fade=np.minimum(np.arange(len(sig))/2200,1)*np.minimum(np.arange(len(sig))[::-1]/2200,1);clips.append(sig*fade);clips.append(np.zeros(rate//4));timeline.append({'at':offset,'label':family+' at 3000 RPM'});offset+=2.25
for name in ['exhaust_stock','exhaust_sport','exhaust_open','turbo','centrifugal','roots','twin_screw','release']:
    sig=frames[name][:rate]*.3;clips.append(sig);clips.append(np.zeros(rate//4));timeline.append({'at':offset,'label':name});offset+=len(sig)/rate+.25
reel=np.concatenate(clips)
with wave.open(str(work/'preview.wav'),'wb') as f:f.setnchannels(1);f.setsampwidth(2);f.setframerate(rate);f.writeframes((reel*32767).astype('<i2').tobytes())
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(work/'preview.wav'),'-c:a','libvorbis','-q:a','5',str(repo/'docs/mechanical-audio-preview.ogg')],check=True)
(repo/'docs/audio-preview-timeline.json').write_text(json.dumps(timeline,indent=2)+'\n')
print(f'Wrote {len(frames)} mono sound assets and {offset:.1f}s listening reel; max asset peak {max(v["peak"] for v in report.values()):.3f}')
