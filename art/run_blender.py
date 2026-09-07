"""Fail-closed Blender entrypoint with distribution-aware glTF Python dependencies."""
import sys
from pathlib import Path
# Ubuntu packages NumPy separately; its system package path may be absent in Blender.
try:
    import numpy
except ImportError:
    system_packages = Path('/usr/lib/python3/dist-packages')
    if system_packages.is_dir():
        sys.path.append(str(system_packages))
    import numpy
import bpy
import runpy
bpy.ops.preferences.addon_enable(module='io_scene_gltf2')
runpy.run_path(str(Path(__file__).with_name('blender_build.py')), run_name='__main__')
output = Path(__file__).resolve().parent / 'generated'
for name in ('hatch.blend', 'hatch.glb', 'hatch.png', 'catalog.blend', 'catalog.glb', 'catalog.png', 'blender-report.json'):
    path = output / name
    if not path.is_file() or path.stat().st_size < 64:
        raise RuntimeError('Missing or empty Blender deliverable: ' + name)
print('BLENDER_DELIVERABLES_VERIFIED')
