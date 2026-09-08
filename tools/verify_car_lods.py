"""Validate sparse world LODs, exact visual topology and preserved body geometry without Blender."""
import gzip,hashlib,json,math,struct
from pathlib import Path
repo=Path(__file__).resolve().parent.parent
folder=repo/'src/main/resources/assets/sparkmotors/models/entity'
def read(path,magic):
    result=[]
    with gzip.open(path,'rb') as f:
        actual,count=struct.unpack('>ii',f.read(8));assert actual==magic
        for _ in range(count):
            name=f.read(struct.unpack('>H',f.read(2))[0]).decode();meta=struct.unpack('>iiii4f6i',f.read(56));n=meta[-1]
            assert n==-1 or n>0 and n%3==0
            result.append((name,meta,[struct.unpack('>6fI',f.read(28)) for _ in range(max(0,n))]))
        assert not f.read(1)
    return result
original=read(folder/'sedan.mesh.gz',0x41504132);manifest=json.loads((folder/'lod-manifest.json').read_text())
assert manifest['source_sha256']==hashlib.sha256((folder/'sedan.mesh.gz').read_bytes()).hexdigest(),'Regenerate LODs after editing the full mesh'
reports=[]
for entry in manifest['lods']:
    path=folder/f"sedan-lod{entry['level']}.mesh.gz";assert entry['sha256']==hashlib.sha256(path.read_bytes()).hexdigest()
    reduced=read(path,0x41504C31);assert len(reduced)==len(original)==manifest['chunks']
    changed=0;error=0;total=0
    for (name,meta,vertices),(base_name,base_meta,base_vertices) in zip(reduced,original):
        assert name==base_name and meta[:-1]==base_meta[:-1],name
        if meta[-1]==-1:total+=len(base_vertices)//3;continue
        assert meta[1] in [1,2,3,4] or name.startswith('fender_lip_'),f'Unexpected body/engine/control simplification: {name}'
        assert 0<len(vertices)<=len(base_vertices);changed+=1;total+=len(vertices)//3
        assert all(math.isfinite(value) for v in vertices for value in v[:6])
        assert all(.98<sum(x*x for x in v[3:6])<1.02 for v in vertices),f'Invalid normals: {name}'
        for axis in range(3):
            for bound in (min,max):
                deviation=abs(bound(v[axis] for v in vertices)-bound(v[axis] for v in base_vertices));error=max(error,deviation)
                assert deviation<(.025 if entry['level']==1 else .05),(name,axis,deviation)
    assert total==entry['all_variant_triangles_after'];assert changed>0
    reports.append({'level':entry['level'],'changed_chunks':changed,'max_bounds_deviation_m':error,'all_variant_triangles':total})
out=repo/'build/ci/resources';out.mkdir(parents=True,exist_ok=True)
(out/'car-lods.json').write_text(json.dumps({'passed':True,'chunks':len(original),'levels':reports},indent=2)+'\n')
print(json.dumps({'passed':True,'chunks':len(original),'levels':reports},indent=2))
