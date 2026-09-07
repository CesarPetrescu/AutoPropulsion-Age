"""Fail-closed Blender entrypoint; the distribution-bundled glTF exporter must be enabled."""
import bpy
import runpy
from pathlib import Path
bpy.ops.preferences.addon_enable(module='io_scene_gltf2')
runpy.run_path(str(Path(__file__).with_name('blender_build.py')), run_name='__main__')
output = Path(__file__).resolve().parent / 'generated'
for name in ('hatch.blend', 'hatch.glb', 'hatch.png', 'catalog.blend', 'catalog.glb', 'catalog.png', 'blender-report.json'):
    path = output / name
    if not path.is_file() or path.stat().st_size < 64:
        raise RuntimeError('Missing or empty Blender deliverable: ' + name)
print('BLENDER_DELIVERABLES_VERIFIED')
