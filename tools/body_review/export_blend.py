"""Export the Coachwork collection of an editable body .blend into its APA2 runtime mesh.

blender --background assets/body_styles/hatchback.blend --python tools/body_review/export_blend.py -- --output /tmp/hatchback.mesh.gz --check
The shared mechanical reference and studio are never exported. Authoring modifiers and object
transforms are applied to evaluated copies, so editing does not mutate the source scene.
"""
from __future__ import annotations
import argparse,json,math,sys
from collections import Counter
from pathlib import Path
import bpy
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/body_styles'))
from mesh_io import read,write


def runtime(v):return (float(v[0]),float(v[2]),float(-v[1]))
def srgb(v):return max(0,min(255,round(255*(12.92*v if v<=.0031308 else 1.055*v**(1/2.4)-.055))))
def color(material):
    r,g,b=map(srgb,material.diffuse_color[:3]);return 0xFF000000|r<<16|g<<8|b


def export() -> tuple[dict,list[dict]]:
    profile=json.loads(bpy.context.scene['body_profile'])
    assert profile['id'] in ('hatchback','sports_car','suv','van','touring_sedan')
    collection=bpy.context.scene.collection.children.get('Coachwork');assert collection is not None
    chunks=[];deps=bpy.context.evaluated_depsgraph_get()
    for ob in collection.all_objects:
        if ob.type!='MESH' or ob.get('apa_reference_only',False):continue
        if 'apa_metadata' not in ob:raise ValueError('Mesh lacks service/export metadata: '+ob.name)
        md=json.loads(ob['apa_metadata'])
        evaluated=ob.evaluated_get(deps);mesh=bpy.data.meshes.new_from_object(evaluated,depsgraph=deps)
        try:
            mesh.transform(ob.matrix_world);mesh.calc_loop_triangles();vertices=[]
            for tri in mesh.loop_triangles:
                if tri.area<1e-14:continue
                if tri.material_index>=len(mesh.materials) or mesh.materials[tri.material_index] is None:
                    raise ValueError('Missing material: '+ob.name)
                rgba=color(mesh.materials[tri.material_index]);n=runtime(tri.normal)
                for i in tri.vertices:
                    xyz=runtime(mesh.vertices[i].co)
                    if not all(math.isfinite(v) for v in (*xyz,*n)):raise ValueError('Non-finite edited geometry: '+ob.name)
                    vertices.append((*xyz,*n,rgba))
            if vertices:chunks.append(dict(md,vertices=vertices))
        finally:bpy.data.meshes.remove(mesh)
    assert chunks,'Empty coachwork collection'
    return profile,chunks


def fingerprint(chunks):
    # Vertex/triangle order may change when a .blend is reloaded. Compare actual positions and
    # per-face colors, plus all filtering/hinge metadata, rather than gzip byte order.
    result=Counter()
    for c in chunks:
        metadata=tuple((k,round(v,5) if isinstance(v,float) else v) for k,v in sorted(c.items()) if k!='vertices')
        for start in range(0,len(c['vertices']),3):
            triangle=tuple(sorted(tuple(round(v,5) for v in x[:3])+(x[6],) for x in c['vertices'][start:start+3]))
            result[(metadata,triangle)]+=1
    return result


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,required=True);parser.add_argument('--check',action='store_true')
    args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
    profile,chunks=export();write(args.output,chunks)
    if args.check:
        original=read(ROOT/'src/main/resources/assets/sparkmotors/models/entity/bodies'/(profile['id']+'.mesh.gz'))
        expected=fingerprint(original);actual=fingerprint(read(args.output))
        if expected!=actual:
            missing=sum((expected-actual).values());extra=sum((actual-expected).values())
            raise AssertionError(f'Editable Blender roundtrip mismatch: {profile["id"]}, missing={missing}, extra={extra}')
        print('BODY_BLENDER_ROUNDTRIP_PASS '+profile['id'],flush=True)
    print('BODY_EDIT_EXPORT '+str(args.output),flush=True)

if __name__=='__main__':main()
