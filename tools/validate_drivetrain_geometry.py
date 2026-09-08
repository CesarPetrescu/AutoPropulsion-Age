"""Exact exported service geometry: every configured torque path, body clearance and family binding.
Intended shaft/housing joints are not tested against their mating parts.
"""
import gzip,struct,json,os
from pathlib import Path
from mathutils.bvhtree import BVHTree
repo=Path(__file__).resolve().parent.parent
chunks=[]
with gzip.open(repo/'src/main/resources/assets/sparkmotors/models/entity/sedan.mesh.gz','rb') as f:
    magic,count=struct.unpack('>ii',f.read(8));assert magic==0x41504132
    for _ in range(count):
        name=f.read(struct.unpack('>H',f.read(2))[0]).decode()
        cat,group,variant,hinge,px,py,pz,angle,family,slot,tier,induction,kind,n=struct.unpack('>iiii4f6i',f.read(56))
        vs=[struct.unpack('>6fI',f.read(28))[:3] for _ in range(n)]
        chunks.append(dict(name=name,cat=cat,group=group,variant=variant,family=family,slot=slot,tier=tier,induction=induction,kind=kind,vs=vs))
def tree(parts):
    vs=[]
    for p in parts:vs.extend(p['vs'])
    return BVHTree.FromPolygons(vs,[(i,i+1,i+2) for i in range(0,len(vs),3)],all_triangles=True,epsilon=0)
bindings=json.loads((repo/'src/main/resources/assets/sparkmotors/models/entity/mechanical-models.json').read_text())
checks=[]
for family in range(7):
    expected={'internal.eccentric'} if family>=3 else {'internal.crank','internal.timing'}
    for n in range(1,(family-2 if family>=3 else 6 if family==1 else 4)+1):
        expected.update(f'{"rotor" if family>=3 else "cylinder"}.{n}.{suffix}' for suffix in (['housing','seals','bearing'] if family>=3 else ['piston','rings','bearing','valves']))
    present={bindings[c['name']] for c in chunks if c['name'] in bindings and c['family']&(1<<family)}
    checks.append(dict(kind='family_service_geometry',family=family,missing=sorted(expected-present),passed=expected<=present))
body=tree([c for c in chunks if c['kind']==1 and c['cat'] in [1,2,3,7] and c['variant']<=1])
for type_ in [1,2,4]:
    for layout in [1,2,4]:
        parts=[c for c in chunks if c['name'].startswith('pt|') and int(c['name'].split('|')[1])&type_ and int(c['name'].split('|')[2])&layout]
        keys={c['name'].split('|')[3] for c in parts}
        front=layout in [2,4];rear=layout in [1,4]
        expected={'driveline.front_differential'} if front else set()
        if rear:expected.add('driveline.differential')
        for axle,enabled in [('front',front),('rear',rear)]:
            if enabled:
                expected.update('driveline.cv_'+('f' if axle=='front' else 'r')+s for s in ['l','r'])
                if type_!=1:expected.update('traction.'+unit+'_'+axle for unit in ['motor','inverter','reduction'])
        if type_ in [1,4]:
            expected.update(['driveline.clutch','driveline.gearbox'])
            if rear:expected.add('driveline.shaft')
            if layout==4:expected.add('driveline.transfer')
        if type_!=1:
            expected.update(['traction.hv_cable','traction.contactor','traction.dc_dc'])
            if type_==4:expected.add('traction.generator')
        checks.append(dict(kind='configured_path',type=type_,layout=layout,missing=sorted(expected-keys),unexpected=sorted(keys-expected),passed=keys==expected))
        for c in parts:
            if 'hv_cable' in c['name']:continue # Routed penetrations / connectors are intentional.
            hits=len(tree([c]).overlap(body))
            checks.append(dict(kind='outer_body_clearance',type=type_,layout=layout,part=c['name'],intersections=hits,passed=hits==0))
        if type_==4:
            # Conservative envelope includes the split pack's mounting flanges / upper fins.
            # Check the PHEV envelope (larger than HEV) against every rigid drivetrain mount.
            for left,right in [(-.67,-.22),(.22,.67)]:
                v=[(x,y,z) for x,y,z in [(left,.25,-.85),(right,.25,-.85),(right,.40,-.85),(left,.40,-.85),(left,.25,.65),(right,.25,.65),(right,.40,.65),(left,.40,.65)]]
                faces=[(0,3,2,1),(4,5,6,7),(0,4,7,3),(1,2,6,5),(3,7,6,2),(0,1,5,4)]
                pack=BVHTree.FromPolygons(v,faces,all_triangles=False,epsilon=0)
                for unit in parts:
                    if 'hv_cable' in unit['name']:continue
                    hits=len(tree([unit]).overlap(pack))
                    contained=any(left<x<right and .25<y<.40 and -.85<z<.65 for x,y,z in unit['vs'])
                    checks.append(dict(kind='hybrid_pack_clearance',layout=layout,part=unit['name'],side=left,intersections=hits,passed=hits==0 and not contained))
            units=[c for c in parts if any(k in c['name'] for k in ['traction.motor','traction.inverter','traction.generator'])]
            for family in range(7):
                core=[c for c in chunks if c['group']==0 and c['family']&(1<<family) and c['induction']&1 and c['tier']<=1 and not c['name'].startswith(('hardware_','service_','coolant_')) and c['cat'] in [18,19,20,21,22,23,24,28]]
                for unit in units:
                    hits=len(tree([unit]).overlap(tree(core)))
                    checks.append(dict(kind='hybrid_core_clearance',family=family,layout=layout,part=unit['name'],intersections=hits,passed=hits==0))
report=dict(scope=__doc__,checks=checks,passed=all(c['passed'] for c in checks))
out=Path(os.environ.get('APA_VALIDATION_OUTPUT',str(repo/'docs')));out.mkdir(parents=True,exist_ok=True)
(out/'drivetrain-geometry-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(dict(passed=report['passed'],checks=len(checks),failures=[c for c in checks if not c['passed']]),indent=2))
assert report['passed'],'Configured drivetrain geometry regression'
