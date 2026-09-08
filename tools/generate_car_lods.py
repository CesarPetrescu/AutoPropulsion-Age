"""Blender CLI: derive world LODs from the canonical mesh; full workshop geometry stays untouched.
Only dense wheel/suspension/drivetrain parts and fender lips are simplified. Bodies,
glass, lamps, controls, gauges, engine cores and all visibility/hinge metadata stay exact.
"""
import bpy,bmesh,gzip,struct,json,hashlib
from pathlib import Path
repo=Path(__file__).resolve().parent.parent
folder=repo/'src/main/resources/assets/sparkmotors/models/entity'
source=(folder/'sedan.mesh.gz').read_bytes();chunks=[]
with gzip.open(folder/'sedan.mesh.gz','rb') as f:
    magic,count=struct.unpack('>ii',f.read(8));assert magic==0x41504132
    for _ in range(count):
        name=f.read(struct.unpack('>H',f.read(2))[0]).decode();meta=struct.unpack('>iiii4f6i',f.read(56));n=meta[-1]
        vertices=[struct.unpack('>6fI',f.read(28)) for _ in range(n)];chunks.append((name,meta,vertices))
report={'source_sha256':hashlib.sha256(source).hexdigest(),'chunks':len(chunks),'lods':[]}
for level,ratio in [(1,.28),(2,.10)]:
    result=[];before=after=0
    for name,meta,vertices in chunks:
        output=vertices;before+=len(vertices)//3
        if len(vertices)>540 and (meta[1] in [1,2,3,4] or name.startswith('fender_lip_')):
            mesh=bpy.data.meshes.new('APA_LOD');mesh.from_pydata([v[:3] for v in vertices],[],[(i,i+1,i+2) for i in range(0,len(vertices),3)])
            colors=sorted({v[6] for v in vertices});materials=[]
            for color in colors:
                material=bpy.data.materials.new(str(color));mesh.materials.append(material);materials.append(material)
            for poly,i in zip(mesh.polygons,range(0,len(vertices),3)):poly.material_index=colors.index(vertices[i][6])
            bm=bmesh.new();bm.from_mesh(mesh);bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=.000001);bm.to_mesh(mesh);bm.free()
            ob=bpy.data.objects.new('APA_LOD',mesh);bpy.context.scene.collection.objects.link(ob)
            modifier=ob.modifiers.new('World silhouette','DECIMATE');modifier.ratio=max(ratio,48/max(1,len(mesh.polygons)));modifier.use_collapse_triangulate=True
            # Preserve the actual wheel/part envelope. Aggressive collapse can remove a
            # narrow extremity; increase detail for that chunk rather than shrinking it.
            for requested in [modifier.ratio,.55,.8,1]:
                modifier.ratio=max(modifier.ratio,requested);bpy.context.view_layer.update()
                evaluated=ob.evaluated_get(bpy.context.evaluated_depsgraph_get());reduced=evaluated.to_mesh();reduced.calc_loop_triangles()
                output=[]
                for triangle in reduced.loop_triangles:
                    for i in triangle.vertices:output.append((*reduced.vertices[i].co,*triangle.normal,colors[triangle.material_index]))
                deviation=max(abs(bound(v[axis] for v in output)-bound(v[axis] for v in vertices)) for axis in range(3) for bound in (min,max))
                evaluated.to_mesh_clear()
                if deviation<.008:break
            if deviation>=.008:output=vertices
            assert output and len(output)<=len(vertices),(name,len(output),len(vertices))
            bpy.data.objects.remove(ob,do_unlink=True);bpy.data.meshes.remove(mesh)
            for material in materials:bpy.data.materials.remove(material)
        after+=len(output)//3;result.append((name,meta,output,output is vertices))
    path=folder/f'sedan-lod{level}.mesh.gz'
    with path.open('wb') as raw:
        with gzip.GzipFile(filename='',fileobj=raw,mode='wb',mtime=0,compresslevel=9) as f:
            f.write(struct.pack('>ii',0x41504C31,len(result)))
            for name,meta,vertices,unchanged in result:
                encoded=name.encode();f.write(struct.pack('>H',len(encoded))+encoded);f.write(struct.pack('>iiii4f6i',*meta[:-1],-1 if unchanged else len(vertices)))
                if not unchanged:
                    for v in vertices:f.write(struct.pack('>6fI',*v))
    report['lods'].append({'level':level,'ratio':ratio,'all_variant_triangles_before':before,'all_variant_triangles_after':after,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
(folder/'lod-manifest.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
