"""Run in the delivered Blender file via MCP. Export evaluated geometry for Minecraft.
The compact gzip stream uses big-endian numbers for Java DataInputStream.
Axes: Blender (x,y,z) -> Minecraft local (x,z,-y), meters.
"""
import bpy, gzip, struct, math, json
from pathlib import Path
from mathutils import Vector

repo=Path(__file__).resolve().parent.parent
output=repo/'src/main/resources/assets/sparkmotors/models/entity'
output.mkdir(parents=True,exist_ok=True)
manifest=json.loads((repo/'assets/modular_car_kit/parts_manifest.json').read_text())['parts']
lookup={p['id']:p for p in manifest}
scene=bpy.data.scenes['SM_01_Assembled'];bpy.context.window.scene=scene;scene.frame_set(1);bpy.context.view_layer.update()
# Close the rear valance for the in-game body shell. This temporary export mesh
# fills the gap behind the lamps without changing the editable authoring file.
verts=[(x,y,z) for x,y,z in [(-.88,2.09,.815),(.88,2.09,.815),(.88,2.14,.815),(-.88,2.14,.815),(-.88,2.09,1.027),(.88,2.09,1.027),(.88,2.14,1.027),(-.88,2.14,1.027)]]
rear_mesh=bpy.data.meshes.new('Runtime rear valance')
rear_mesh.from_pydata(verts,[],[(0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)])
rear_mesh.materials.append(bpy.data.materials['SM_paint'])
rear=bpy.data.objects.new('Runtime rear valance',rear_mesh);scene.collection.objects.link(rear)
rear.parent=bpy.data.objects['SM_rear_bumper'];rear.matrix_parent_inverse=rear.parent.matrix_world.inverted()
bpy.context.view_layer.update()
deps=bpy.context.evaluated_depsgraph_get()
hinges=['door_front_left','door_front_right','door_rear_left','door_rear_right','hood','trunk_lid']
chunks=[];triangles=0

def owner(ob):
    while ob and 'part_id' not in ob: ob=ob.parent
    return ob
def ancestors(p):
    result=[p]
    while p.get('parent_part'):
        p=lookup[p['parent_part']];result.append(p)
    return result
def vector(v):return (v[0],v[2],-v[1])
def color(c):
    return round(max(0,min(1,12.92*c if c<=.0031308 else 1.055*c**(1/2.4)-.055))*255)

for part in manifest:
    if part['state']=='workshop':continue
    name=part['id'].split(':')[1];cat=part['category'];variant=0;group=-1
    chain=ancestors(part);names=[p['id'].split(':')[1] for p in chain]
    engine='i4_engine' in names
    if engine:group=0
    if 'manual_5speed' in names:group=1
    if cat==13:group=2
    if cat==15:group=3
    if cat==16:group=4
    body=name in ['front_bumper','side_skirt_-1','side_skirt_1','front_fender_-1','front_fender_1']
    if body:group=5;variant=1
    if part['state']=='upgrade':
        if name.startswith(('sport_rim_','slick_tire_')):group=2;variant=2
        elif name.startswith('sport_coilover_'):group=4;variant=2
        elif name in ['rear_spoiler','front_splitter','sport_front_bumper','widebody_fender_-1','widebody_fender_1','sport_skirt_-1','sport_skirt_1']:group=5;variant=2
        else:continue
    elif name.startswith(('rim_','tire_','coilover_')):variant=1
    # Tiny enclosed engine internals have authored models; do not ship/render
    # them in this assembled-car alpha. Keep visible outer engine components.
    if cat in [20,21,22,23]:continue
    if name.startswith('gear_set_') or name=='gearbox_shafts':continue
    hinge=next((hinges.index(n)+1 for n in names if n in hinges),0)
    hinge_part=lookup['sparkmotors:'+hinges[hinge-1]] if hinge else None
    pivot=vector(hinge_part['mount_world_m']) if hinge_part else (0,0,0)
    angle=hinge_part['hinge']['open_degrees'] if hinge_part else 0
    root=bpy.data.objects[part['object']]
    for ob in scene.objects:
        if ob.type not in {'MESH','CURVE','FONT'} or owner(ob)!=root:continue
        ev=ob.evaluated_get(deps);me=bpy.data.meshes.new_from_object(ev,depsgraph=deps)
        me.transform(ob.matrix_world);me.calc_loop_triangles()
        batches={}
        for tri in me.loop_triangles:
            mat=me.materials[tri.material_index] if len(me.materials)>tri.material_index else None
            mn=mat.name if mat else ''
            kind=1 if mn=='SM_paint' else 2 if mn=='SM_glass' else 3 if mn in ['SM_white','SM_red','SM_amber','SM_screen'] and cat==6 else 0
            rgb=tuple(color(c) for c in mat.diffuse_color[:3]) if mat else (170,180,190)
            rgba=(255<<24)|(rgb[0]<<16)|(rgb[1]<<8)|rgb[2]
            normal=vector(tri.normal)
            for vi in tri.vertices:batches.setdefault(kind,[]).append((*vector(me.vertices[vi].co),*normal,rgba))
            triangles+=1
        for kind,vertices in batches.items():
            chunks.append((name,cat,group,variant,hinge,*pivot,angle,kind,vertices))
        bpy.data.meshes.remove(me)

bpy.data.objects.remove(rear,do_unlink=True);bpy.data.meshes.remove(rear_mesh)
# Merge primitive components sharing a part and material role into one chunk.
# Keeps transforms/visibility modular while reducing per-frame object overhead.
merged={}
for chunk in chunks:
    key=tuple(chunk[:-1]);merged.setdefault(key,[]).extend(chunk[-1])
chunks=[(*key,vertices) for key,vertices in merged.items()]
with gzip.open(output/'sedan.mesh.gz','wb',compresslevel=9) as f:
    f.write(struct.pack('>ii',0x41504131,len(chunks)))
    for name,cat,group,variant,hinge,px,py,pz,angle,kind,verts in chunks:
        encoded=name.encode();f.write(struct.pack('>H',len(encoded)));f.write(encoded)
        f.write(struct.pack('>iiii4fii',cat,group,variant,hinge,px,py,pz,angle,kind,len(verts)))
        for v in verts:f.write(struct.pack('>6fI',*v))
report={'format':'APA1 gzip, big endian','source':'assets/modular_car_kit/sparkmotors_modular.blend','chunks':len(chunks),'triangles':triangles,
        'runtime_file':'src/main/resources/assets/sparkmotors/models/entity/sedan.mesh.gz','bytes':(output/'sedan.mesh.gz').stat().st_size,
        'scope':'Stock visible components and selected sport assemblies; tiny enclosed internals omitted. Export adds a rear valance closure behind the lamps.'}
(repo/'docs/runtime-assets.json').write_text(json.dumps(report,indent=2))
result=report
