#!/usr/bin/env python3
"""Static content validation. --assets additionally requires actual Blender-generated outputs."""
import json,sys
from pathlib import Path
R=Path(__file__).resolve().parents[1];A=R/'mod/src/main/resources/assets/autopropulsion';D=R/'mod/src/main/resources/data/autopropulsion'
parts=json.loads((R/'assets/catalog.json').read_text())['parts'];errors=[]
def check(ok,why):
 if not ok:errors.append(why)
check(len(parts)==140,'unexpected catalogue size');check(sum(p['functional'] for p in parts)==16,'unexpected functional count')
check(len(set(p['id'] for p in parts))==len(parts),'duplicate part id');check(sorted(p['model_index'] for p in parts)==list(range(1,len(parts)+1)),'model index continuity')
lang=json.loads((A/'lang/en_us.json').read_text());sigs={}
for p in R.glob('mod/src/main/resources/**/*.json'):
 try:json.loads(p.read_text())
 except Exception as e:errors.append(f'{p}: {e}')
for p in parts:
 check('part.autopropulsion.'+p['id'] in lang,'missing translation '+p['id'])
 check(json.loads((D/f'autopropulsion/parts/{p["id"]}.json').read_text())==p,'generated definition stale '+p['id'])
 if '--assets' in sys.argv:
  file=A/('models/'+p['model'].split(':',1)[1]+'.json')
  check(file.exists(),'missing item model '+str(file))
  if file.exists():
   model=json.loads(file.read_text());obj=A/model['model'].split(':',1)[1];check(obj.exists(),'missing OBJ '+str(obj))
for p in (D/'recipe').glob('*.json'):
 r=json.loads(p.read_text());sig=json.dumps(sorted(x['item'] for x in r['ingredients'])) if 'ingredients' in r else json.dumps([r['pattern'],r['key']],sort_keys=True)
 check(sig not in sigs,f'recipe collision {p.name} / {sigs.get(sig)}');sigs[sig]=p.name
if '--assets' in sys.argv:
 for p in ['assets/source/h1-hatchback.blend','assets/source/component-catalogue.blend','assets/source/h1-hatchback.glb','assets/manifest.json','docs/images/h1-hatchback.png','docs/images/component-catalogue.png','mod/src/main/resources/assets/autopropulsion/sounds/engine_loop.ogg']:
  check((R/p).is_file(),'missing asset '+p)
 mesh=json.loads((A/'models/vehicle/hatchback.mesh.json').read_text());check(bool(mesh.get('groups')),'empty vehicle mesh')
print('\n'.join(errors) if errors else f'VALIDATION_PASS {len(parts)} parts, {len(sigs)} unambiguous recipes'+(' and generated assets' if '--assets' in sys.argv else ' (asset execution not checked)'))
sys.exit(bool(errors))
