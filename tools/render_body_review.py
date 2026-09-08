"""Blender: render exact runtime stock/sport body geometry without desktop input.

APA_BODY_OUTPUT chooses an evidence directory. Source scenes are never modified.
"""
import bpy, gzip, struct, math, os
from pathlib import Path
from mathutils import Vector, Matrix

repo=Path(__file__).resolve().parent.parent
out=Path(os.environ.get('APA_BODY_OUTPUT',str(repo/'docs/body-review')))
out.mkdir(parents=True,exist_ok=True)
prefix='APA_BODY_REVIEW_'
for scene in list(bpy.data.scenes):
    if scene.name.startswith(prefix):bpy.data.scenes.remove(scene)
for ob in list(bpy.data.objects):
    if ob.name.startswith(prefix):bpy.data.objects.remove(ob,do_unlink=True)
def linear(v):return v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4
materials={}
def material(color,kind):
    key=(color,kind)
    if key not in materials:
        m=bpy.data.materials.new(prefix+str(key));m.use_nodes=True
        rgb=(.035,.34,.39) if kind==1 else tuple(linear((color>>s&255)/255) for s in [16,8,0])
        bs=m.node_tree.nodes.get('Principled BSDF');bs.inputs['Base Color'].default_value=(*rgb,1)
        bs.inputs['Roughness'].default_value=.3 if kind==1 else .48
        if kind==2:bs.inputs['Transmission Weight'].default_value=.9;bs.inputs['Roughness'].default_value=.12
        materials[key]=m
    return materials[key]
chunks=[]
with gzip.open(repo/'src/main/resources/assets/sparkmotors/models/entity/sedan.mesh.gz','rb') as f:
    magic,count=struct.unpack('>ii',f.read(8));assert magic==0x41504132
    for _ in range(count):
        name=f.read(struct.unpack('>H',f.read(2))[0]).decode()
        cat,group,variant,hinge,px,py,pz,angle,family,slot,tier,induction,kind,n=struct.unpack('>iiii4f6i',f.read(56))
        vs=[struct.unpack('>6fI',f.read(28)) for _ in range(n)]
        if name.startswith('pt|'):
            _,types,layouts,_=name.split('|',3)
            if not int(types)&1 or not int(layouts)&1:continue
        elif group==1:continue
        if name=='service_jack' or not family&1 or not induction&1 or slot>=0 and tier>1:continue
        chunks.append((name,cat,group,variant,kind,vs))
for grade in [1,2]:
    s=bpy.data.scenes.new(prefix+str(grade));s.render.engine='BLENDER_EEVEE';s.render.resolution_x=1440;s.render.resolution_y=1000;s.render.resolution_percentage=100
    s.render.image_settings.file_format='PNG';s.view_settings.view_transform='AgX'
    for name,cat,group,variant,kind,vs in chunks:
        if group>=0 and variant and variant!=(grade if group in [2,4,5] else 1):continue
        data=bpy.data.meshes.new(prefix+name)
        data.from_pydata([(v[0],-v[2],v[1]) for v in vs],[],[(i,i+1,i+2) for i in range(0,len(vs),3)])
        colors={}
        for poly,i in zip(data.polygons,range(0,len(vs),3)):
            color=vs[i][6]
            if color not in colors:colors[color]=len(colors);data.materials.append(material(color,kind))
            poly.material_index=colors[color]
        ob=bpy.data.objects.new(prefix+name,data);s.collection.objects.link(ob)
    w=bpy.data.worlds.new(prefix+'world');w.use_nodes=True;w.node_tree.nodes['Background'].inputs[0].default_value=(.13,.16,.21,1);w.node_tree.nodes['Background'].inputs[1].default_value=.5;s.world=w
    for loc,power in [((2,-4,5),1500),((-4,-1,3),1100),((1,4,3),1500),((0,0,-3),600)]:
        ld=bpy.data.lights.new(prefix+'light','AREA');ld.energy=power;ld.size=5
        ob=bpy.data.objects.new(prefix+'light',ld);s.collection.objects.link(ob);ob.location=loc;ob.rotation_euler=(Vector((0,0,.6))-ob.location).to_track_quat('-Z','Y').to_euler()
    cd=bpy.data.cameras.new(prefix+'camera');cam=bpy.data.objects.new(prefix+'camera',cd);s.collection.objects.link(cam);s.camera=cam;cd.type='ORTHO';cd.ortho_scale=5.8
    for view,loc in [('front',(4,-6,2.6)),('rear',(-4,6,2.5)),('side',(6,0,1.0)),('under',(3,-4,-4))]:
        cam.location=loc;cam.rotation_euler=(Vector((0,0,.65))-cam.location).to_track_quat('-Z','Y').to_euler()
        s.render.filepath=str(out/f'{"stock" if grade==1 else "sport"}-{view}.png')
        bpy.ops.render.render(write_still=True,scene=s.name)
if not os.environ.get('APA_BODY_OUTPUT'):
    bpy.data.libraries.write(str(repo/'assets/body_workshop.blend'),{s for s in bpy.data.scenes if s.name.startswith(prefix)},fake_user=True,compress=True)
result={'directory':str(out),'renders':8}
