"""Blender MCP: triangle-intersection checks on the exact APA2 runtime geometry.
Mechanical mating surfaces inside the core are intentionally not checked against
each other. External non-mating hardware and hood articulation are checked.
"""
import gzip,struct,json,math
from pathlib import Path
from mathutils import Vector,Matrix
from mathutils.bvhtree import BVHTree
repo=Path(__file__).resolve().parent.parent
chunks=[]
with gzip.open(repo/'src/main/resources/assets/sparkmotors/models/entity/sedan.mesh.gz','rb') as f:
    magic,count=struct.unpack('>ii',f.read(8));assert magic==0x41504132
    for _ in range(count):
        n=struct.unpack('>H',f.read(2))[0];name=f.read(n).decode()
        cat,group,variant,hinge,px,py,pz,angle,family,slot,tier,induction,kind,n=struct.unpack('>iiii4f6i',f.read(56))
        vertices=[struct.unpack('>6fI',f.read(28))[:3] for _ in range(n)]
        chunks.append(dict(name=name,group=group,hinge=hinge,pivot=(px,py,pz),angle=angle,family=family,slot=slot,tier=tier,induction=induction,vertices=vertices))
def bvh(parts,amount=None):
    vs=[];faces=[]
    for c in parts:
        transform=Matrix.Identity(4)
        if amount is not None:
            p=Vector(c['pivot']);transform=Matrix.Translation(p)@Matrix.Rotation(math.radians(c['angle'])*amount,4,'X')@Matrix.Translation(-p)
        offset=len(vs);vs.extend([transform@Vector(v) for v in c['vertices']]);faces.extend((offset+i,offset+i+1,offset+i+2) for i in range(0,len(c['vertices']),3))
    return BVHTree.FromPolygons(vs,faces,all_triangles=True,epsilon=0)
hood=[c for c in chunks if c['hinge']==5];hoods=[bvh(hood,a) for a in [0,.25,.5,.75,1]]
fixed={name:bvh([c for c in chunks if c['name']==name or name=='radiator' and c['name'].startswith('hardware_cooling_')]) for name in ['battery','radiator','coolant_reservoir']}
checks=[]
for family in range(7):
    for induction in range(7):
        engine=[c for c in chunks if c['group']==0 and c['family']&(1<<family) and c['induction']&(1<<induction)]
        allparts=bvh(engine);hood_hits=[len(allparts.overlap(h)) for h in hoods]
        plumbing=[c for c in engine if c['name'].startswith('boost_plumbing_')]
        interference={name:len(bvh(plumbing).overlap(tree)) if plumbing else 0 for name,tree in fixed.items()}
        compressor_names=['turbocharger','supercharger','large_turbo','twin_turbo','roots_blower','twin_screw']
        cores=[c for c in engine if not c['name'].startswith(('hardware_','service_','turbo_header_','boost_plumbing_')) and c['name'] not in compressor_names+['battery','radiator','radiator_fan','coolant_reservoir']]
        compressors=[c for c in engine if c['name'] in compressor_names]
        compressor_hits=len(bvh(cores).overlap(bvh(compressors))) if compressors else 0
        passed=not any(hood_hits) and not any(interference.values()) and compressor_hits==0
        checks.append(dict(family=family,induction=induction,hood_intersections=hood_hits,boost_vs_fixed=interference,compressor_vs_core=compressor_hits,passed=passed))
report={'scope':'Exact runtime triangle intersections: all 49 layouts × 5 hood positions with the union of all 42 hardware choices, compressors versus core, boost plumbing versus battery/all radiator variants/reservoir. Intended internal mating surfaces are excluded.',
        'layouts':49,'hood_positions':[0,.25,.5,.75,1],'passed':sum(c['passed'] for c in checks),'failed':sum(not c['passed'] for c in checks),'checks':checks}
(repo/'docs/engine-intersections.json').write_text(json.dumps(report,indent=2)+'\n')
result={k:v for k,v in report.items() if k!='checks'}
result['failures']=[c for c in checks if not c['passed']]
