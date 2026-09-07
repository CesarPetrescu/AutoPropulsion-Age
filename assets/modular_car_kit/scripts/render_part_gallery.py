"""Render every manifest part in isolation without changing the authoring file.

blender --background sparkmotors_modular.blend --python scripts/render_part_gallery.py
Optional arguments after --: --kit-dir PATH --limit 5 --resume
Workbench previews use material colors; glass is opaque for shape inspection.
"""
import argparse
import bpy
import json
import re
import sys
import time
from pathlib import Path
from mathutils import Matrix, Vector

parser = argparse.ArgumentParser()
parser.add_argument('--kit-dir', type=Path, default=Path(__file__).resolve().parent.parent)
parser.add_argument('--limit', type=int)
parser.add_argument('--resume', action='store_true')
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
out = args.kit_dir.resolve()
gallery = out / 'previews'
gallery.mkdir(parents=True, exist_ok=True)
parts = json.loads((out / 'parts_manifest.json').read_text(encoding='utf8'))['parts']
if args.limit:
    parts = parts[:args.limit]

def descendants(root):
    for child in root.children:
        yield child
        yield from descendants(child)

def nearest_part(obj):
    while obj and 'part_id' not in obj:
        obj = obj.parent
    return obj

# Evaluate meshes in their original scene before separating the parts, so
# booleans retain the correct cutter positions and all modifiers are included.
cache = {}
source_geometry = {}
for scene_name in ['SM_01_Assembled', 'SM_04_Workshop']:
    source_scene = bpy.data.scenes[scene_name]
    bpy.context.window.scene = source_scene
    source_scene.frame_set(1)
    bpy.context.view_layer.update()
    depsgraph = bpy.context.evaluated_depsgraph_get()
    for item in parts:
        root = source_scene.objects.get(item['object'])
        if root is None:
            continue
        source_geometry[item['id']] = []
        for obj in descendants(root):
            owner = nearest_part(obj)
            if obj.type not in {'MESH', 'CURVE', 'FONT'} or owner.get('category', 0) <= 0:
                continue
            if owner.get('asset_state', 'stock') != item['state']:
                continue
            if obj.name not in cache:
                mesh = bpy.data.meshes.new_from_object(obj.evaluated_get(depsgraph), depsgraph=depsgraph)
                mesh.transform(obj.matrix_world)
                cache[obj.name] = mesh
            if cache[obj.name].vertices:
                source_geometry[item['id']].append(cache[obj.name])

scene = bpy.data.scenes.new('Part_Gallery_Temporary')
bpy.context.window.scene = scene
scene.render.engine = 'BLENDER_WORKBENCH'
scene.render.resolution_x = 512
scene.render.resolution_y = 512
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGB'
scene.render.image_settings.compression = 75
scene.render.film_transparent = False
scene.world = bpy.data.worlds.new('Gallery_Background')
scene.world.color = (0.055, 0.07, 0.085)
scene.display.render_aa = '32'
shading = scene.display.shading
shading.light = 'STUDIO'
shading.studio_light = 'paint.sl'
shading.color_type = 'MATERIAL'
shading.show_shadows = True
shading.show_cavity = True
shading.cavity_type = 'BOTH'
shading.curvature_ridge_factor = 1.3
shading.curvature_valley_factor = 1.1
shading.background_type = 'WORLD'
shading.background_color = (0.055, 0.07, 0.085)
scene.view_settings.view_transform = 'Standard'

camera_data = bpy.data.cameras.new('Gallery_Camera')
camera = bpy.data.objects.new('Gallery_Camera', camera_data)
scene.collection.objects.link(camera)
scene.camera = camera
camera_data.type = 'ORTHO'
camera_data.clip_start = 0.001
camera_data.clip_end = 100
camera.location = (3.6, -4.8, 3.3)
camera.rotation_euler = (-camera.location).to_track_quat('-Z', 'Y').to_euler()
bpy.context.view_layer.update()
rotation_inverse = camera.matrix_world.to_3x3().transposed()
index = []
started = time.time()

for number, item in enumerate(parts, 1):
    meshes = source_geometry.get(item['id'], [])
    if not meshes:
        raise RuntimeError('No preview geometry for ' + item['id'])
    points = [vertex.co.copy() for mesh in meshes for vertex in mesh.vertices]
    lo = Vector([min(v[axis] for v in points) for axis in range(3)])
    hi = Vector([max(v[axis] for v in points) for axis in range(3)])
    center = (lo + hi) / 2
    span = max(hi - lo)
    if span <= 0:
        raise RuntimeError('Degenerate model: ' + item['id'])
    filename = re.sub(r'[^A-Za-z0-9_.-]', '_', item['id'].split(':', 1)[1]) + '.png'
    filepath = gallery / filename
    projected = [rotation_inverse @ ((v - center) / span) for v in points]
    width = max(v.x for v in projected) - min(v.x for v in projected)
    height = max(v.y for v in projected) - min(v.y for v in projected)
    # Aim at the projected bounding-box center to keep asymmetric parts framed.
    projected_center = Vector(((max(v.x for v in projected) + min(v.x for v in projected))/2,
                               (max(v.y for v in projected) + min(v.y for v in projected))/2, 0))
    camera_shift = camera.matrix_world.to_3x3() @ projected_center
    camera.location = Vector((3.6, -4.8, 3.3)) + camera_shift
    camera_data.ortho_scale = max(width, height) * 1.18
    if not (args.resume and filepath.exists()):
        copies = []
        transform = Matrix.Scale(1 / span, 4) @ Matrix.Translation(-center)
        for mesh in meshes:
            obj = bpy.data.objects.new('Preview', mesh)
            scene.collection.objects.link(obj)
            obj.matrix_world = transform
            copies.append(obj)
        bpy.context.view_layer.update()
        scene.render.filepath = str(filepath)
        bpy.ops.render.render(write_still=True, scene=scene.name)
        for obj in copies:
            bpy.data.objects.remove(obj, do_unlink=True)
    index.append({**item, 'preview': 'previews/' + filename,
                  'preview_mesh_components_including_children': len(meshes),
                  'bounds_m': [round(n, 6) for n in hi - lo]})
    (out / 'gallery_progress.json').write_text(json.dumps({
        'completed': number, 'total': len(parts), 'last_id': item['id'],
        'elapsed_seconds': round(time.time() - started, 1)}, indent=2), encoding='utf8')

if len({entry['preview'] for entry in index}) != len(index):
    raise RuntimeError('Preview filename collision')
(out / 'gallery_index.json').write_text(json.dumps({
    'renderer': 'Blender 5.1 Workbench, studio material colors, 512 x 512',
    'note': 'Assembly previews include same-state child parts; each image is scaled independently. Glass is opaque.',
    'count': len(index), 'parts': index}, indent=2), encoding='utf8')
print('GALLERY_COMPLETE', len(index), flush=True)
