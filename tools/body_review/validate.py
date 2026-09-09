"""Read-only checks for the committed body meshes; never rewrites an asset to pass.
These numerical checks complement, not replace, native screenshot review.
"""
from __future__ import annotations
import hashlib,json,math,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/body_styles'))
from mesh_io import read,bounds


def validate() -> dict:
    base=ROOT/'src/main/resources/assets/sparkmotors/models/entity'
    manifest=json.loads((ROOT/'assets/body_styles/profiles.json').read_text())
    bodies=manifest['bodies'];assert len(bodies)==5
    assert {b['id'] for b in bodies}=={'hatchback','sports_car','suv','van','touring_sedan'}
    canonical=read(base/'sedan.mesh.gz');records=[];fit_cases=0
    for b in bodies:
        path=base/'bodies'/(b['id']+'.mesh.gz');chunks=read(path)
        assert hashlib.sha256(path.read_bytes()).hexdigest()==b['sha256'],b['id']+' checksum'
        assert len(chunks)==b['bodyChunks'] and sum(len(c['vertices'])//3 for c in chunks)==b['bodyTriangles']
        names={c['name'] for c in chunks}
        assert {'roof','hood','windshield','floor_pan_-1','floor_pan_1','front_seat_-1','front_seat_1','dashboard','steering_wheel','instrument_cluster'}<=names,b['id']+' missing coachwork'
        assert any(c['name'].startswith('door_front_') and c['hinge']==1 for c in chunks)
        assert any(c['name'].startswith('door_front_') and c['hinge']==2 for c in chunks)
        if b['id']=='van':
            assert {7,8,9,10}<={c['hinge'] for c in chunks},'Van sliders and barn doors'
            assert 'rear_bench' not in names
        elif b['id']=='sports_car':
            assert not any(c['name'].startswith('door_rear_') for c in chunks)
            assert 'rear_bench' not in names
        else:assert {3,4}<={c['hinge'] for c in chunks},b['id']+' rear doors'
        if b['id'] in ('hatchback','suv'):
            assert any(c['name']=='rear_glass' and c['hinge']==6 for c in chunks),'Glass must follow liftgate'
        floors=[c for c in chunks if c['name'].startswith('floor_pan_')]
        # Traction pack top is 0.39 m, including its actual cooling fins it reaches 0.40 m.
        assert min(v[1] for c in floors for v in c['vertices'])>.4005,b['id']+' floor intersects pack fins'
        low,high=bounds(chunks)
        assert max(abs(low[0]),abs(high[0]))<b['halfWidth']+.17,b['id']+' lateral query envelope'
        assert high[1]<b['roof']+.15,b['id']+' vertical query envelope'
        assert max(abs(low[2]),abs(high[2]))<max(b['nose'],b['tail'])+.10,b['id']+' longitudinal query envelope'
        interior=[c for c in chunks if c['category'] in (8,9,10,11,39)]
        assert max(v[1] for c in interior for v in c['vertices'])<b['roof']-.045,b['id']+' cabin part above roof'
        assert max(abs(v[0]) for c in interior for v in c['vertices'])<b['halfWidth'],b['id']+' cabin part outside doors'
        for c in chunks:
            assert 0<=c['hinge']<=10 and 0<=c['kind']<=3
            assert len(c['vertices'])%3==0
            for v in c['vertices']:
                assert all(math.isfinite(x) for x in v[:6]),b['id']+' non-finite geometry'
                assert .98<sum(x*x for x in v[3:6])<1.02,b['id']+' invalid normal '+c['name']
        # The same complete canonical mechanical chunks are used by every new body. Evaluate
        # family/induction masks individually; fixed front mounts must stay below the bonnet.
        for family in range(7):
            for induction in range(7):
                components=[c for c in canonical if c['group']==0 and c['family']&(1<<family) and c['induction']&(1<<induction)]
                assert components,(family,induction)
                lo,hi=bounds(components)
                assert max(abs(lo[0]),abs(hi[0]))<.778,b['id']+' engine bay side '+str((family,induction,lo,hi))
                assert hi[1]<max(b['belt']+.015,1.057)-.002,b['id']+' engine through hood '+str((family,induction,hi))
                assert hi[2]<b['nose']-.025,b['id']+' engine through front fascia'
                fit_cases+=1
        records.append({'id':b['id'],'triangles':b['bodyTriangles'],'parts':len(chunks),'packFloorClearanceM':round(min(v[1] for c in floors for v in c['vertices'])-.40,6),'bounds':[low,high]})
    result={'schema':1,'passed':True,'bodies':records,'combustionFamilyInductionFitCases':fit_cases,'scope':'Finite geometry, silhouettes/panels, cabin envelopes, shared combustion bay masks and traction-pack floor clearance. Not a visual screenshot approval or a proof of every triangle intersection.'}
    return result


if __name__=='__main__':
    result=validate();print(json.dumps(result,indent=2))
    if '--report' in sys.argv:
        path=ROOT/'build/reports/body-styles/geometry.json';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(result,indent=2)+'\n')
    print('BODY_GEOMETRY_PASS 5 bodies, 245 combustion fit combinations')
