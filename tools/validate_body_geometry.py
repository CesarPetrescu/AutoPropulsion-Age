"""Blender CI: inspect runtime surface coverage and tire/body clearance over suspension travel.

This checks the exported triangles, not an independently mocked body model.
"""
import gzip,struct,json,os,math
from pathlib import Path
from mathutils import Vector,Matrix
from mathutils.bvhtree import BVHTree
repo=Path(__file__).resolve().parent.parent
chunks=[]
with gzip.open(repo/'src/main/resources/assets/sparkmotors/models/entity/sedan.mesh.gz','rb') as f:
    magic,count=struct.unpack('>ii',f.read(8));assert magic==0x41504132
    for _ in range(count):
        name=f.read(struct.unpack('>H',f.read(2))[0]).decode()
        cat,group,variant,hinge,px,py,pz,angle,family,slot,tier,induction,kind,n=struct.unpack('>iiii4f6i',f.read(56))
        vs=[struct.unpack('>6fI',f.read(28))[:3] for _ in range(n)]
        chunks.append(dict(name=name,cat=cat,group=group,variant=variant,hinge=hinge,pivot=(px,py,pz),vs=vs,kind=kind))
def tree(parts,transform=None):
    vs=[];fs=[]
    for part in parts:
        offset=len(vs);vs.extend([transform@Vector(v) if transform else v for v in part['vs']]);fs.extend((offset+i,offset+i+1,offset+i+2) for i in range(0,len(part['vs']),3))
    return BVHTree.FromPolygons(vs,fs,all_triangles=True,epsilon=0)
coverage=[];clearances=[]
for grade in [1,2]:
    body=[c for c in chunks if c['group'] in [-1,5] and c['kind']!=2 and (c['group']!=5 or c['variant'] in [0,grade]) and c['name']!='service_jack']
    bvh=tree(body)
    for side in [-1,1]:
        for x in [.32,.37]:
            hit=bvh.ray_cast(Vector((side*x,.92,2.5)),Vector((0,0,-1)),.5)[0]
            coverage.append(dict(grade=grade,surface='front_fascia',sample=side*x,passed=hit is not None))
        for z in [-.7,0,.6]:
            hit=bvh.ray_cast(Vector((side*.835,.10,z)),Vector((0,1,0)),.5)[0]
            coverage.append(dict(grade=grade,surface='floor_sill',sample=[side,z],passed=hit is not None))
    for corner,(x,z) in {'fl':(-.83,1.35),'fr':(.83,1.35),'rl':(-.83,-1.30),'rr':(.83,-1.30)}.items():
        tires=[c for c in chunks if c['name']==('tire_' if grade==1 else 'slick_tire_')+corner]
        # Outer painted skin/doors must never cut the tire. Mounts and inner suspension mating
        # surfaces are tested separately by the native articulated rig, not counted as bodywork.
        skin_parts=[c for c in body if c['kind']==1 and c['cat'] in [1,2,3,7]]
        skin=tree(skin_parts)
        for travel in [-.14,0,.07]:
            for steer in ([-.55,0,.55] if corner[0]=='f' else [0]):
                center=Vector((x,.34,z));transform=Matrix.Translation((0,travel,0))@Matrix.Translation(center)@Matrix.Rotation(-steer,4,'Y')@Matrix.Translation(-center)
                hits=len(tree(tires,transform).overlap(skin))
                parts=[c['name'] for c in skin_parts if tree(tires,transform).overlap(tree([c]))] if hits else []
                clearances.append(dict(grade=grade,corner=corner,travel=travel,steer=steer,intersections=hits,parts=parts,passed=hits==0))
out=Path(os.environ.get('APA_VALIDATION_OUTPUT',str(repo/'docs')));out.mkdir(parents=True,exist_ok=True)
report=dict(scope='Exact runtime stock/sport surface coverage and 48 tire/body poses; outer painted skin excludes intentional internal mount joints.',coverage=coverage,clearances=clearances,passed=all(c['passed'] for c in coverage+clearances))
(out/'body-geometry.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(dict(passed=report['passed'],coverage=len(coverage),poses=len(clearances),failures=[c for c in coverage+clearances if not c['passed']]),indent=2))
assert report['passed'],'Body geometry regression; see body-geometry.json'
