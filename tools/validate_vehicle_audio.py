#!/usr/bin/env python3
"""Validate shipped audio resources and decode every Vorbis stream. Requires ffmpeg/ffprobe."""
import array, concurrent.futures, json, math, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/sparkmotors'
manifest=json.loads((ROOT/'sounds/vehicle/manifest.json').read_text())['sounds']
events=json.loads((ROOT/'sounds.json').read_text());lang=json.loads((ROOT/'lang/en_us.json').read_text())
assert len(manifest)==58

def check(entry):
    name,declared=entry;p=ROOT/'sounds/vehicle'/f'{name}.ogg'
    assert name in events and events[name]['subtitle'] in lang,name+' registration/subtitle'
    assert events[name]['sounds'][0]['name']=='sparkmotors:vehicle/'+name,name+' path'
    info=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-of','json',str(p)]))['streams'][0]
    assert info['codec_name']=='vorbis' and info['channels']==1 and int(info['sample_rate'])==22050,name+' format'
    pcm=array.array('f');pcm.frombytes(subprocess.check_output(['ffmpeg','-v','error','-i',str(p),'-f','f32le','-acodec','pcm_f32le','-']))
    assert pcm and all(math.isfinite(x) for x in pcm),name+' empty/invalid samples'
    peak=max(abs(x) for x in pcm);rms=math.sqrt(sum(x*x for x in pcm)/len(pcm))
    assert .001<rms<.3 and peak<1,name+' silent/clipping'
    assert declared['peak']<=.8 and declared['channels']==1
    return {'event':name,'decoded_peak':round(peak,4),'decoded_rms':round(rms,4),'samples':len(pcm)}
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:results=list(pool.map(check,manifest.items()))
print(json.dumps({'passed':len(results),'mono_vorbis':True,'max_peak':max(r['decoded_peak'] for r in results),'sounds':results},indent=2))
