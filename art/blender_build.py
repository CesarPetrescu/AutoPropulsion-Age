"""Run: blender --background --factory-startup --python art/blender_build.py
Creates editable .blend files, GLBs and rendered previews from the runtime meshes.
Compatible with Blender 4.0+; no add-ons or external assets are required.
"""
from __future__ import annotations
import json, math, sys
from pathlib import Path
import bpy
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'art/generated'; OUT.mkdir(parents=True,exist_ok=True)
MODELS=ROOT/'mod/src/main/resources/assets/autopropulsion/models/vehicle'

def clear():
    bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
    for data in list(bpy.data.materials): bpy.data.materials.remove(data)

def material(color):
    name='rgba_'+'_'.join(str(round(v,3)) for v in color)
    found=bpy.data.materials.get(name)
    if found: return found
    mat=bpy.data.materials.new(name); mat.diffuse_color=tuple(color); mat.use_nodes=True
    shader=mat.node_tree.nodes.get('Principled BSDF'); shader.inputs['Base Color'].default_value=tuple(color)
    shader.inputs['Roughness'].default_value=.36
    shader.inputs['Metallic'].default_value=.45 if sum(color[:3])>1.3 else .12
    return mat

def import_mesh(doc,offset=(0,0,0),scale=1):
    collection=bpy.data.collections.new(doc['name']); bpy.context.scene.collection.children.link(collection)
    for src in doc['objects']:
        verts=[(v[0]*scale+offset[0],-v[2]*scale+offset[1],v[1]*scale+offset[2]) for v in src['vertices']]
        data=bpy.data.meshes.new(src['name']); data.from_pydata(verts,[],src['faces']); data.update()
        obj=bpy.data.objects.new(src['name'],data); collection.objects.link(obj)
        obj.data.materials.append(material(src['color']))
        obj['apa_group']=src.get('group','body'); obj['apa_pivot_metres']=src.get('pivot',[0,0,0])
        obj['apa_source_units']='metres'; obj['apa_source_model']=doc['name']
        # Keep the export geometry identical to the game; bevels/subdivision are not baked behind its back.
    for name,p in doc.get('locators',{}).items():
        obj=bpy.data.objects.new('locator:'+name,None); obj.empty_display_type='ARROWS'; obj.empty_display_size=.12
        obj.location=(p[0]*scale+offset[0],-p[2]*scale+offset[1],p[1]*scale+offset[2]); collection.objects.link(obj)
    return collection

def point(obj,at): obj.rotation_euler=(Vector(at)-obj.location).to_track_quat('-Z','Y').to_euler()
def studio(at,camera_location,size,resolution):
    scene=bpy.context.scene
    try: scene.render.engine='BLENDER_EEVEE_NEXT'
    except TypeError: scene.render.engine='BLENDER_EEVEE'
    scene.render.resolution_x,scene.render.resolution_y=resolution; scene.render.resolution_percentage=100
    scene.render.image_settings.file_format='PNG'; scene.world.color=(.12,.12,.12)
    scene.render.film_transparent=False
    bpy.ops.object.camera_add(location=camera_location); camera=bpy.context.object; camera.name='Studio camera'; point(camera,at)
    camera.data.type='ORTHO'; camera.data.ortho_scale=size; scene.camera=camera
    for name,loc,energy,light_size in [('Key',(camera_location[0],camera_location[1],camera_location[2]+5),1800,8),('Fill',(-6,1,8),1100,6),('Rim',(4,6,7),1400,5)]:
        data=bpy.data.lights.new(name,'AREA'); data.energy=energy; data.shape='DISK'; data.size=light_size
        obj=bpy.data.objects.new(name,data); scene.collection.objects.link(obj); obj.location=loc; point(obj,at)
    bpy.ops.mesh.primitive_plane_add(size=200,location=(at[0],at[1],-.015)); ground=bpy.context.object
    ground.name='Studio floor (not game asset)'; ground.data.materials.append(material([.11,.13,.16,1]))
    scene.view_settings.view_transform='Standard'

def export(name):
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/f'{name}.blend'))
    # Export only asset meshes and locators, not studio lighting, camera or floor.
    bpy.ops.object.select_all(action='DESELECT')
    for obj in bpy.context.scene.objects:
        if 'apa_source_model' in obj or obj.name.startswith('locator:'): obj.select_set(True)
    bpy.ops.export_scene.gltf(filepath=str(OUT/f'{name}.glb'),export_format='GLB',use_selection=True,export_apply=False)
    bpy.context.scene.render.filepath=str(OUT/f'{name}.png'); bpy.ops.render.render(write_still=True)

def main():
    if not (MODELS/'hatch_01.json').exists(): raise SystemExit('Run python3 art/generate_assets.py first')
    clear(); import_mesh(json.loads((MODELS/'hatch_01.json').read_text()))
    studio((0,0,.7),(6,-7,4.7),6.3,(1600,1100)); export('hatch')
    clear(); files=sorted((MODELS/'parts').glob('*.json')); columns=10
    for i,path in enumerate(files):
        doc=json.loads(path.read_text()); bounds=[v for obj in doc['objects'] for v in obj['vertices']]
        maximum=max(max(v[j] for v in bounds)-min(v[j] for v in bounds) for j in range(3))
        scale=min(1,1.2/max(maximum,.001)); offset=((i%columns)*1.65,(i//columns)*1.70,0)
        import_mesh(doc,offset,scale)
        bpy.ops.object.text_add(location=(offset[0]-.65,offset[1]-.70,.01),rotation=(0,0,0))
        text=bpy.context.object; text.name='label:'+doc['name']; text.data.body=doc['name'].replace('_',' ')
        text.data.size=.10; text.data.materials.append(material([.86,.88,.90,1]))
    rows=math.ceil(len(files)/columns); target=((columns-1)*.825,(rows-1)*.85,0)
    studio(target,(target[0]+10,target[1]-15,24),max(columns*1.65,rows*1.70)*1.15,(2400,2000)); export('catalog')
    report={'blender_version':bpy.app.version_string,'part_meshes':len(files),'files':[p.name for p in sorted(OUT.iterdir()) if p.is_file()]}
    (OUT/'blender-report.json').write_text(json.dumps(report,indent=2)+'\n')
    print('BLENDER_ASSETS_COMPLETE '+json.dumps(report))

if __name__=='__main__': main()
