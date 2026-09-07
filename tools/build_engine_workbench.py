"""Blender MCP: create an editable, derived inspection .blend with 21 open-hood scenes.
The game's committed APA2 mesh is the source for this snapshot. Shared mesh data
keeps this small. Authoring rebuilds still use the original kit + runtime exporter.
"""
import bpy,gzip,struct,math,json
from pathlib import Path
from mathutils import Vector,Matrix
repo=Path(__file__).resolve().parent.parent
prefix='APA_INSPECT_'
# Only clean previously generated inspection data in the current Blender file.
for scene in list(bpy.data.scenes):
    if scene.name.startswith(prefix):bpy.data.scenes.remove(scene)
for ob in list(bpy.data.objects):
    if ob.name.startswith(prefix):bpy.data.objects.remove(ob,do_unlink=True)
for mesh in list(bpy.data.meshes):
    if mesh.name.startswith(prefix) and mesh.users==0:bpy.data.meshes.remove(mesh)
def xyz(v):return (v[0],-v[2],v[1])
def linear(x):return x/12.92 if x<=.04045 else ((x+.055)/1.055)**2.4
materials={}
def material(color):
    if color not in materials:
        rgb=[linear(((color>>s)&255)/255) for s in [16,8,0]]
        m=bpy.data.materials.get(prefix+str(color)) or bpy.data.materials.new(prefix+str(color));m.diffuse_color=(*rgb,1);m.use_nodes=True
        shader=m.node_tree.nodes.get('Principled BSDF');shader.inputs['Base Color'].default_value=(*rgb,1);shader.inputs['Roughness'].default_value=.4
        materials[color]=m
    return materials[color]
chunks=[]
with gzip.open(repo/'src/main/resources/assets/sparkmotors/models/entity/sedan.mesh.gz','rb') as f:
    magic,count=struct.unpack('>ii',f.read(8));assert magic==0x41504132
    for _ in range(count):
        n=struct.unpack('>H',f.read(2))[0];name=f.read(n).decode()
        cat,group,variant,hinge,px,py,pz,angle,family,slot,tier,induction,kind,n=struct.unpack('>iiii4f6i',f.read(56))
        raw=[struct.unpack('>6fI',f.read(28)) for _ in range(n)]
        data=bpy.data.meshes.new(prefix+name);data.from_pydata([xyz(v) for v in raw],[],[(i,i+1,i+2) for i in range(0,n,3)])
        colors={};indices=[]
        for i in range(0,n,3):
            color=0xBF333B if kind==1 else raw[i][6]&0xFFFFFF
            if color not in colors:colors[color]=len(colors);data.materials.append(material(color))
            indices.append(colors[color])
        for polygon,index in zip(data.polygons,indices):polygon.material_index=index
        if kind==2:
            for m in data.materials:m.node_tree.nodes.get('Principled BSDF').inputs['Transmission Weight'].default_value=.85
        chunks.append(dict(name=name,mesh=data,group=group,variant=variant,hinge=hinge,pivot=xyz((px,py,pz)),angle=angle,family=family,slot=slot,tier=tier,induction=induction))
names=['I4','V6','Flat4','1Rotor','2Rotor','3Rotor','4Rotor'];modes=['Natural','Turbo','Supercharger','LargeTurbo','TwinTurbo','Roots','TwinScrew'];scenes=[]
for family in range(7):
    for induction in range(7):
        s=bpy.data.scenes.new(prefix+names[family]+'_'+modes[induction]);s.unit_settings.system='METRIC';s.unit_settings.scale_length=1
        s.render.engine='BLENDER_EEVEE';s.render.resolution_x=1200;s.render.resolution_y=900;s.render.resolution_percentage=100
        for c in chunks:
            if c['group']>0 and c['variant']>1:continue
            if not c['family']&(1<<family) or not c['induction']&(1<<induction):continue
            if c['slot']==5 and (induction==0 or c['tier'] not in [0,induction]):continue
            choices=[2,3,2,3,4,induction,3,2,2,3]
            if c['slot']>=0 and c['tier']>0 and c['tier']!=choices[c['slot']]:continue
            ob=bpy.data.objects.new(prefix+c['name'],c['mesh']);s.collection.objects.link(ob)
            ob['runtime_part']=c['name'];ob['service_slot']=c['slot']
            if c['hinge']==5:
                p=Vector(c['pivot']);ob.matrix_world=Matrix.Translation(p)@Matrix.Rotation(math.radians(c['angle']),4,'X')@Matrix.Translation(-p)
        camera_data=bpy.data.cameras.new(prefix+'camera');camera=bpy.data.objects.new(prefix+'camera',camera_data);s.collection.objects.link(camera)
        camera.location=(4,-5,4);camera.rotation_euler=(Vector((0,-.7,.75))-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.type='ORTHO';camera.data.ortho_scale=6;s.camera=camera
        world=bpy.data.worlds.new(prefix+'world');world.use_nodes=True;world.node_tree.nodes['Background'].inputs[0].default_value=(.10,.13,.18,1);world.node_tree.nodes['Background'].inputs[1].default_value=.6;s.world=world
        for loc,power,size in [((2,-4,6),1200,5),((-4,-1,3),800,4),((1,4,4),1000,3)]:
            ld=bpy.data.lights.new(prefix+'area','AREA');ld.energy=power;ld.shape='DISK';ld.size=size;lo=bpy.data.objects.new(prefix+'area',ld);s.collection.objects.link(lo);lo.location=loc;lo.rotation_euler=(Vector((0,-.7,.8))-lo.location).to_track_quat('-Z','Y').to_euler()
        scenes.append(s)
# Isolated hardware scenes share the exact runtime meshes. These also supply unique item icons.
catalog=json.loads((repo/'docs/powertrain-catalog.json').read_text())
for slot in catalog['slots']:
    for option in slot['options']:
        s=bpy.data.scenes.new(prefix+'PART_'+option['item']);s.render.engine='BLENDER_EEVEE';s.render.resolution_x=512;s.render.resolution_y=512;s.render.resolution_percentage=100
        s.render.image_settings.file_format='PNG';s.render.film_transparent=True;s.world=scenes[0].world
        vs=[]
        for c in chunks:
            if c['group']!=0 or c['slot']!=slot['index'] or not c['family']&1 or c['tier'] not in [0,option['value']]:continue
            if slot['index']!=5 and not c['induction']&1:continue
            if slot['index']==5 and not c['induction']&(1<<option['value']):continue
            ob=bpy.data.objects.new(prefix+'PART_'+c['name'],c['mesh']);s.collection.objects.link(ob);vs.extend(v.co for v in c['mesh'].vertices)
        lo=Vector(tuple(min(v[i] for v in vs) for i in range(3)));hi=Vector(tuple(max(v[i] for v in vs) for i in range(3)));center=(lo+hi)/2
        cd=bpy.data.cameras.new(prefix+'part camera');camera=bpy.data.objects.new(prefix+'part camera',cd);s.collection.objects.link(camera);camera.location=center+Vector((2,-3,2.2));camera.rotation_euler=(center-camera.location).to_track_quat('-Z','Y').to_euler();cd.type='ORTHO';cd.ortho_scale=max(hi-lo)*1.65;s.camera=camera
        for offset,power in [((1,-2,3),220),((-2,-1,1),120),((1,2,2),160)]:
            ld=bpy.data.lights.new(prefix+'part area','AREA');ld.energy=power;ld.size=2;ob=bpy.data.objects.new(prefix+'part area',ld);s.collection.objects.link(ob);ob.location=center+Vector(offset);ob.rotation_euler=(center-ob.location).to_track_quat('-Z','Y').to_euler()
        s['item_id']=option['item'];s['part_label']=option['label'];scenes.append(s)
out=repo/'assets/engine_workshop.blend'
bpy.data.libraries.write(str(out),set(scenes),fake_user=True,compress=True)
bpy.context.window.scene=scenes[1]
result={'file':str(out),'scenes':len(scenes),'bytes':out.stat().st_size,'scope':'49 assembled open-hood layouts and 42 isolated hardware scenes; editable snapshots of the runtime geometry.'}
