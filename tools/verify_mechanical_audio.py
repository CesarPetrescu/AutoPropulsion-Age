"""Decode actual assets; check mono, peaks, references and regeneration ownership."""
from pathlib import Path
import hashlib,json,subprocess,sys
import numpy as np

repo=Path(__file__).resolve().parents[1];assets=repo/'src/main/resources/assets/sparkmotors'
manifest=json.loads((repo/'docs/audio-manifest.json').read_text());sounds=json.loads((assets/'sounds.json').read_text())
hashes={};results={}
for name,expected in manifest.items():
    file=assets/'sounds/mechanical'/f'{name}.ogg';hashes[name]=hashlib.sha256(file.read_bytes()).hexdigest()
    probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-of','json',str(file)]))['streams'][0]
    assert probe['channels']==1 and probe['codec_name']=='vorbis',name
    decoded=subprocess.check_output(['ffmpeg','-v','error','-i',str(file),'-f','f32le','-acodec','pcm_f32le','-'])
    samples=np.frombuffer(decoded,dtype='<f4');assert len(samples)>4000 and np.isfinite(samples).all(),name
    peak=float(np.max(np.abs(samples)));assert peak<1.0,(name,peak)
    assert sounds[name]['sounds'][0]['name']=='sparkmotors:mechanical/'+name
    results[name]={'decoded_peak':peak,'rms':float(np.sqrt(np.mean(samples*samples))),'channels':probe['channels'],'sha256':hashes[name]}
subprocess.run([sys.executable,str(repo/'tools/generate_game_resources.py')],cwd=repo,check=True)
assert json.loads((assets/'sounds.json').read_text())==sounds,'Resource regeneration changed authored sound definitions'
for name,digest in hashes.items():assert hashlib.sha256((assets/'sounds/mechanical'/f'{name}.ogg').read_bytes()).hexdigest()==digest,name
result={'assets_checked':len(results),'resource_regeneration_preserves_authored_audio':True,'listening_status':'User listening review requested; no model listening tool is available.','assets':results}
(repo/'docs/audio-validation.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS:',len(results),'decoded mono assets; valid references; no clipped decoded peaks; authored assets and definitions survive regeneration')
