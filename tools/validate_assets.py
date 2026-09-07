#!/usr/bin/env python3
"""Fail closed on invalid geometry, content references, missing locators or unsafe budgets."""
from __future__ import annotations
import hashlib, json, math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]; RES=ROOT/'mod/src/main/resources'

def main():
    manifest=json.loads((ROOT/'art/assets-manifest.json').read_text()); checked=0; faces=0
    for record in manifest['meshes']:
        path=ROOT/record['path']; raw=path.read_bytes()
        assert hashlib.sha256(raw).hexdigest()==record['sha256'],f'stale mesh {path}'
        data=json.loads(raw); assert data['format']==1 and data['units']=='metres'
        assert data['objects'],f'empty mesh {path}'
        count=0
        for obj in data['objects']:
            assert len(obj['color'])==4 and all(math.isfinite(c) and 0<=c<=1 for c in obj['color'])
            assert obj['vertices'] and obj['faces']
            assert all(len(v)==3 and all(math.isfinite(c) and abs(c)<=50 for c in v) for v in obj['vertices'])
            assert all(3<=len(face)<=4 and all(isinstance(i,int) and 0<=i<len(obj['vertices']) for i in face) for face in obj['faces'])
            count+=len(obj['faces'])
        assert count<=20000,f'mesh budget exceeded: {path}'
        if data['name']=='hatch_01':
            for name in ['wheel_fl','wheel_fr','wheel_rl','wheel_rr','seat_driver','hood_hinge','engine_mount']:
                assert name in data['locators'],f'missing locator {name}'
            points=[v for o in data['objects'] for v in o['vertices']]
            length=max(v[2] for v in points)-min(v[2] for v in points)
            assert 4.0<=length<=4.7,f'wrong vehicle scale: {length}'
        checked+=1; faces+=count
    defs=list((RES/'data/autopropulsion/autopropulsion/parts').glob('*.json')); ids=set(); installed=0
    for path in defs:
        p=json.loads(path.read_text()); assert p['id'] not in ids; ids.add(p['id'])
        assert p['id']=='autopropulsion:'+path.stem
        model=RES/'assets/autopropulsion/models'/ (p['model'].split(':',1)[1]+'.json')
        assert model.is_file(),f'missing model for {p["id"]}'
        assert isinstance(p['implemented'],bool); installed+=p['implemented']
        assert all(math.isfinite(v) and v>0 for v in p['limits'].values())
        if not p['implemented']: assert p['modifiers']==[], 'model-only part falsely advertises effects'
    assert checked==manifest['mesh_count'] and len(ids)==manifest['part_definition_count'] and installed==manifest['installable_definition_count']
    for path in RES.rglob('*.json'): json.loads(path.read_text())
    report={'status':'passed','meshes':checked,'faces':faces,'part_definitions':len(ids),'installable_parts':installed}
    out=ROOT/'reports/local'; out.mkdir(parents=True,exist_ok=True)
    (out/'asset-validation.json').write_text(json.dumps(report,indent=2)+'\n')
    print('ASSET_VALIDATION_PASSED '+json.dumps(report))

if __name__=='__main__': main()
