"""Deterministic clearance and framing pass on generated editable Blender scenes.
Run after build_assets.py. Updates source scene, GLB, runtime mesh and reviewed previews together.
"""
import bpy
import json
from pathlib import Path
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / 'assets/source'
IMAGES = ROOT / 'docs/images'
MESH = ROOT / 'mod/src/main/resources/assets/autopropulsion/models/vehicle/hatchback.mesh.json'


def corners(objects):
    return [o.matrix_world @ Vector(c) for o in objects if o.type == 'MESH' for c in o.bound_box]


def preview(objects, target, location, width, height, filename):
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = 96
    scene.cycles.use_denoising = False
    for layer in scene.view_layers:
        layer.cycles.use_denoising = False
    scene.render.resolution_x = width
    scene.render.resolution_y = height
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'PNG'
    scene.view_settings.view_transform = 'AgX'
    target = Vector(target)
    bpy.ops.object.camera_add(location=location)
    camera = bpy.context.object
    camera.rotation_euler = (target-camera.location).to_track_quat('-Z', 'Y').to_euler()
    camera.data.type = 'ORTHO'
    camera.data.ortho_scale = 1
    scene.camera = camera
    bpy.context.view_layer.update()
    bounds = corners(objects)
    # Fit actual projected geometry, not a guessed scale that clips edge components.
    for _ in range(100):
        projected = [world_to_camera_view(scene, camera, p) for p in bounds]
        if all(.055 <= p.x <= .945 and .055 <= p.y <= .945 and p.z > 0 for p in projected):
            break
        camera.data.ortho_scale *= 1.1
    else:
        raise RuntimeError('Unable to frame all asset bounds')
    for offset, power, size in [((3,-5,8),900,7),((-5,2,5),600,5),((4,6,6),1100,5)]:
        bpy.ops.object.light_add(type='AREA', location=target+Vector(offset))
        light=bpy.context.object
        light.data.energy=power
        light.data.shape='DISK'
        light.data.size=size
        light.rotation_euler=(target-light.location).to_track_quat('-Z','Y').to_euler()
    scene.world.use_nodes=True
    scene.world.node_tree.nodes['Background'].inputs['Color'].default_value=(.16,.19,.23,1)
    scene.world.node_tree.nodes['Background'].inputs['Strength'].default_value=.35
    bottom=min(v.z for v in bounds)
    bpy.ops.mesh.primitive_plane_add(size=100, location=(target.x,target.y,bottom-.015))
    floor=bpy.context.object
    material=bpy.data.materials.new('preview_floor')
    material.diffuse_color=(.06,.075,.09,1)
    material.use_nodes=True
    material.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value=(.06,.075,.09,1)
    material.node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value=.8
    floor.data.materials.append(material)
    scene.render.filepath=str(IMAGES/filename)
    bpy.ops.render.render(write_still=True)
    return {'all_mesh_bounds_inside_margin':True,'margin':.055,'ortho_scale':camera.data.ortho_scale}


bpy.ops.wm.open_mainfile(filepath=str(SRC/'h1-hatchback.blend'))
objects=[o for o in bpy.context.scene.objects if o.type=='MESH']
engine=[o for o in objects if o.get('group')=='enginebay']
hood=[o for o in objects if o.get('group')=='hood']
if not engine or not hood:
    raise RuntimeError('Required enginebay/hood groups absent')
hood_bottom=min(v.z for v in corners(hood))
engine_top=max(v.z for v in corners(engine))
delta=min(0.0,hood_bottom-.03-engine_top)
for o in engine:
    o.location.z+=delta
bpy.context.view_layer.update()
if max(v.z for v in corners(engine)) > hood_bottom-.029:
    raise RuntimeError('Engine intersects closed hood after clearance pass')
payload=json.loads(MESH.read_text())
for group in payload['groups']:
    if group['name']=='enginebay':
        for face in group['faces']:
            for vertex in face['v']:
                vertex[1]=round(vertex[1]+delta,5)
MESH.write_text(json.dumps(payload,separators=(',',':'))+'\n')
bpy.ops.wm.save_as_mainfile(filepath=str(SRC/'h1-hatchback.blend'),compress=True)
bpy.ops.object.select_all(action='DESELECT')
for o in objects:
    o.select_set(True)
bpy.ops.export_scene.gltf(filepath=str(SRC/'h1-hatchback.glb'),export_format='GLB',use_selection=True)
car_review=preview(objects,(0,0,.65),(6,-7,4.4),1200,760,'h1-hatchback.png')

bpy.ops.wm.open_mainfile(filepath=str(SRC/'component-catalogue.blend'))
objects=[o for o in bpy.context.scene.objects if o.type=='MESH']
bounds=corners(objects)
lo=Vector([min(v[i] for v in bounds) for i in range(3)])
hi=Vector([max(v[i] for v in bounds) for i in range(3)])
center=(lo+hi)/2
catalog_review=preview(objects,center,center+Vector((8,-12,25)),1800,1300,'component-catalogue.png')
manifest_path=ROOT/'assets/manifest.json'
manifest=json.loads(manifest_path.read_text())
manifest['preview_render']={'engine':'CYCLES','device':'CPU','samples':96,'denoising':False,'view_transform':'AgX'}
manifest['quality_pass']={'script':'assets/blender/finalize_assets.py','closed_hood_clearance_m':.03,'engine_vertical_adjustment_m':round(delta,5),'car_framing':car_review,'catalogue_framing':catalog_review}
manifest_path.write_text(json.dumps(manifest,indent=2)+'\n')
print('AUTOPROPULSION_VISUAL_GEOMETRY_PASS',json.dumps(manifest['quality_pass']))
