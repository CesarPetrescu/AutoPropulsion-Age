"""Verify that all 42 catalog choices select distinct actual vertex geometry in all seven families."""
import gzip,struct,hashlib,json,os
from pathlib import Path
repo=Path(__file__).resolve().parent.parent
catalog=json.loads((repo/'docs/powertrain-catalog.json').read_text());chunks=[]
with gzip.open(repo/'src/main/resources/assets/sparkmotors/models/entity/sedan.mesh.gz','rb') as f:
    magic,count=struct.unpack('>ii',f.read(8));assert magic==0x41504132
    for _ in range(count):
        n=struct.unpack('>H',f.read(2))[0];name=f.read(n).decode();cat,group,variant,hinge,px,py,pz,angle,family,slot,tier,induction,kind,n=struct.unpack('>iiii4f6i',f.read(56))
        data=f.read(n*28);assert len(data)==n*28
        geometry=b''.join(data[i:i+12] for i in range(0,len(data),28))
        chunks.append(dict(name=name,group=group,family=family,slot=slot,tier=tier,induction=induction,geometry=geometry,vertices=n))
checks=[]
for family in range(7):
    for slot in catalog['slots']:
        seen=set()
        for option in slot['options']:
            mode=option['value'] if slot['index']==5 else 0
            visible=[c for c in chunks if c['group']==0 and c['family']&(1<<family) and c['slot']==slot['index'] and c['tier'] in [0,option['value']] and c['induction']&(1<<mode)]
            assert any(c['tier']==option['value'] for c in visible),(family,option['item'],'missing selected variant geometry')
            fingerprint=hashlib.sha256(b''.join(c['geometry'] for c in visible)).hexdigest()
            assert fingerprint not in seen,(family,option['item'],'recolor-only option');seen.add(fingerprint)
            checks.append({'family':family,'item':option['item'],'triangles':sum(c['vertices']//3 for c in visible),'position_sha256':fingerprint})
report={'scope':'Every catalog choice selects nonempty, distinct vertex positions in every engine family. Hashes exclude names, normals and colors. Geometry fit and intersection tests are separate.','checks':len(checks),'passed':len(checks),'hardware_choices':42,'families':7,'details':checks}
output=Path(os.environ.get('APA_VALIDATION_OUTPUT',str(repo/'docs')));output.mkdir(parents=True,exist_ok=True)
(output/'hardware-mesh-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print(f"Passed {len(checks)} geometry identity checks")
