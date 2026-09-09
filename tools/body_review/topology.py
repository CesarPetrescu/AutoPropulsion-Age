"""Independent mesh topology, oriented-ray seam and panel-group regression checks.

Reads exported geometry, never the Blender object graph. A capture is NOT a pass.
Use --baseline ROOT --report FILE to inspect an old checkout with the same tests.
"""
from __future__ import annotations
import argparse,json,sys
from collections import Counter
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/body_styles'))
from mesh_io import read


def topology(chunk):
    raw=np.array([v[:6] for v in chunk['vertices']],dtype=float)
    if len(raw)==0:return {'empty':True,'boundaryEdges':0,'nonManifoldEdges':0,'windingErrors':0,'normalErrors':0,'degenerateTriangles':0,'signedVolume':0}
    p=raw[:,:3].reshape(-1,3,3)
    normals=np.cross(p[:,1]-p[:,0],p[:,2]-p[:,0]);area=np.linalg.norm(normals,axis=1)
    safe=np.maximum(area,1e-30)
    ndot=(raw[:,3:].reshape(-1,3,3)*(normals/safe[:,None])[:,None,:]).sum(axis=2)
    edges=Counter();oriented=Counter()
    for tri in p:
        vertices=[tuple(np.round(v,6)) for v in tri]
        for a,b in zip(vertices,vertices[1:]+vertices[:1]):edges[tuple(sorted((a,b)))]+=1;oriented[(a,b)]+=1
    return {'boundaryEdges':sum(n==1 for n in edges.values()),'nonManifoldEdges':sum(n!=2 for n in edges.values()),
            'windingErrors':sum(oriented[(a,b)]!=oriented[(b,a)] for a,b in edges),
            'normalErrors':int((ndot<.999).sum()),'degenerateTriangles':int((area<2e-12).sum()),
            'signedVolume':float(np.einsum('ij,ij->i',p[:,0],np.cross(p[:,1],p[:,2])).sum()/6)}


class SurfaceRays:
    def __init__(self,chunks):
        self.p=np.array([v[:3] for c in chunks for v in c['vertices']],dtype=float).reshape(-1,3,3)
        self.e1=self.p[:,1]-self.p[:,0];self.e2=self.p[:,2]-self.p[:,0]
    def first_hit(self,origin,direction,limit=.25):
        d=np.asarray(direction,dtype=float);h=np.cross(d,self.e2);det=np.einsum('ij,ij->i',self.e1,h)
        # Positive determinant only: strict BACKFACE CULLING, including inside-to-outside rays.
        valid=det>1e-9;inv=np.zeros_like(det);inv[valid]=1/det[valid]
        s=np.asarray(origin)-self.p[:,0];u=inv*np.einsum('ij,ij->i',s,h)
        q=np.cross(s,self.e1);v=inv*np.einsum('j,ij->i',d,q);t=inv*np.einsum('ij,ij->i',self.e2,q)
        valid &= (u>=-1e-7)&(v>=-1e-7)&(u+v<=1+1e-7)&(t>1e-6)&(t<=limit)
        return float(t[valid].min()) if valid.any() else float('inf')
    def hit(self,origin,direction,limit=.25):
        return bool(np.isfinite(self.first_hit(origin,direction,limit)))


def rear_plate_visible(chunks):
    """The registration plate must be the first opaque surface from behind, not inside a bumper."""
    target=[c for c in chunks if c['name']=='rear_plate']
    if len(target)!=1:return False
    others=[c for c in chunks if c['name']!='rear_plate' and c['kind']!=2 and c['variant']<=1]
    points=np.array([v[:3] for v in target[0]['vertices']])
    center=(points.min(axis=0)+points.max(axis=0))*.5
    near=min(v[2] for c in [*target,*others] for v in c['vertices'])-.10
    origin=(float(center[0]),float(center[1]),near)
    plate=SurfaceRays(target).first_hit(origin,(0,0,1),10)
    obstruction=SurfaceRays(others).first_hit(origin,(0,0,1),10) if others else float('inf')
    return bool(np.isfinite(plate) and plate<=obstruction+1e-6)


def probes(b):
    """Sampling locations derive from the public body profile, not the authored frame list."""
    w,roof,belt,rf,rr,cf,cr=(b[k] for k in ('halfWidth','roof','belt','roofFront','roofRear','cabinFront','cabinRear'))
    taper=.055 if b['id']=='van' else .115
    def height(z):return belt+(roof-belt)*(cf-z)/(cf-rf) if z>rf else belt+(roof-belt)*(z-cr)/(rr-cr) if z<rr else roof
    for z in np.linspace(cr+.07,cf-.10,28):
        for label,y in [('belt',belt+.006),('upper',belt+(height(z)-belt)*.53),('roof_join',height(z)-.009)]:
            x=w-taper*(y-belt)/(roof-belt)
            for side in (-1,1):
                for inside in (True,False):
                    d=side if inside else -side
                    yield f'{label}-{z:.3f}-{side}-{inside}',(side*x-d*.105,y,z),(d,0,0),.21
    floor=max(.435,.43+b['cabinY'])
    for x in (-.225,.225):
        for z in (-.84,-.7,-.1,.5):yield f'tunnel-floor-{x}-{z}',(x,floor+.04,z),(0,-1,0),.10
    for x in (-.64,-.3,0,.3,.64):
        for z in (-1.015,-1.005,-.995):yield f'rear-floor-{x}-{z}',(x,max(floor,.56)+.04,z),(0,-1,0),.24
    # Occupants looking up near the windshield and rear roof edges must meet inner roof faces.
    for x in (-.5,0,.5):
        for z in (rf-.015,rr+.015,(rr+rf)/2):yield f'roof-inner-{x}-{z}',(x,roof-.085,z),(0,1,0),.15
    if b['id']=='van':
        for z in (-2.07,-1.99,-1.9,-1.80,-1.77):
            for y in (belt+.10,(belt+roof)/2,roof-.03):
                x=w-taper*(y-belt)/(roof-belt)
                for side in (-1,1):yield f'cargo-join-{z}-{y}-{side}',(side*(x-.105),y,z),(side,0,0),.21


def analyze(root):
    manifest=json.loads((root/'assets/body_styles/profiles.json').read_text());reports=[]
    base=root/'src/main/resources/assets/sparkmotors/models/entity/bodies'
    for b in manifest['bodies']:
        chunks=read(base/(b['id']+'.mesh.gz'));solids=set(b.get('solidParts',[]))
        candidates=[c for c in chunks if c['category']<=6 or c['name'].startswith('door_card_')]
        checks=[]
        required={'roof','hood','windshield','bonnet_shoulder_-1','bonnet_shoulder_1','door_front_left','door_front_right'}
        if b['id'] in ('hatchback','suv'):required|={'rear_glass','trunk_lid'}
        if b['id']=='van':required|={'cargo_upper_left','cargo_upper_right','barn_door_-1','barn_door_1'}
        names={c['name'] for c in candidates}
        for n in required:checks.append({'test':'required-solid:'+n,'passed':n in names})
        counts={'testedParts':0,'boundaryEdges':0,'nonManifoldEdges':0,'windingErrors':0,'normalErrors':0,'degenerateTriangles':0}
        for c in candidates:
            result=topology(c);counts['testedParts']+=1
            for k in counts:
                if k!='testedParts':counts[k]+=result[k]
            passed=not any(result[k] for k in ('boundaryEdges','nonManifoldEdges','windingErrors','normalErrors','degenerateTriangles')) and result['signedVolume']>1e-11
            checks.append({'test':'solid:'+c['name']+':'+str(c['variant']),'passed':passed,**result})
        rays=SurfaceRays([c for c in candidates if c['variant']<=1])
        for label,origin,direction,limit in probes(b):checks.append({'test':'seam:'+label,'passed':rays.hit(origin,direction,limit),'origin':list(map(float,origin)),'direction':direction})
        if b['id'] in ('hatchback','suv','van'):
            rr,cr,roof,belt=(b[k] for k in ('roofRear','cabinRear','roof','belt'))
            for c in candidates:
                if not c['name'].startswith(('rear_fixed_','hatch_side_','cargo_rear_quarter_')):continue
                excess=max((y-(roof if z>=rr else belt if z<=cr else belt+(roof-belt)*(z-cr)/(rr-cr)) for x,y,z,*_ in c['vertices']),default=0)
                checks.append({'test':'no-rear-sail:'+c['name'],'passed':excess<=.045,'excessM':excess})
        # A moving window/frame/card cannot remain behind when its door opens.
        for c in candidates:
            if not c['name'].startswith('door_front_'):continue
            side=c['name'].rsplit('_',1)[1]
            related=[d for d in chunks if d['name'] in ('window_'+side+'_front','window_'+side+'_front_frame','door_card_'+side+'_front')]
            same=lambda d:all(d[k]==c[k] for k in ('hinge','px','py','pz','angle'))
            checks.append({'test':'door-assembly-pivot:'+side,'passed':len(related)==3 and all(same(d) for d in related)})
        checks.append({'test':'rear-registration-plate-not-occluded','passed':rear_plate_visible(candidates)})
        if solids:
            required_manifest=all(c['name'] in solids for c in candidates)
            checks.append({'test':'every-body-surface-declared-closed','passed':required_manifest})
        reports.append({'body':b['id'],**counts,'checks':len(checks),'failures':[r for r in checks if not r['passed']], 'passed':all(r['passed'] for r in checks)})
    return {'schema':1,'passed':all(b['passed'] for b in reports),'bodies':reports,'scope':'Exported solid topology, winding, normals, explicit rear-profile limits, rigid panel pivots and front-face-only seam rays. Not arbitrary full-scene intersection or universal visual certification.'}


def main():
    p=argparse.ArgumentParser();p.add_argument('--baseline',type=Path);p.add_argument('--report',type=Path,default=ROOT/'build/reports/body-styles/topology.json')
    args=p.parse_args();result=analyze(args.baseline or ROOT);args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(result,indent=2)+'\n')
    for body in result['bodies']:
        print(body['body'],body['testedParts'],'parts',body['checks'],'checks',len(body['failures']),'failures')
        for failure in body['failures'][:16]:print(' ',failure['test'])
    if not result['passed']:raise SystemExit(1)
    print('BODY_SHELL_TOPOLOGY_PASS')
if __name__=='__main__':main()
