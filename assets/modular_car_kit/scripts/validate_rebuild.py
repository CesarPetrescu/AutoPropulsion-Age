"""Fresh-process rebuild check; writes only a result record into the delivered kit.

blender --background --factory-startup --python-exit-code 1 --python scripts/validate_rebuild.py
Generated models use a temporary directory that is cleaned up after the check.
"""
import bpy
import json
import os
import runpy
import struct
import tempfile
from pathlib import Path

base = Path(__file__).resolve().parent
with tempfile.TemporaryDirectory(prefix='sparkmotors_rebuild_') as temporary:
    os.environ['SPARKMOTORS_OUTPUT_DIR'] = temporary
    runpy.run_path(str(base / 'rebuild_modular_kit.py'), run_name='__main__')
    output = Path(temporary)
    fit = json.loads((output / 'fit_report.json').read_text(encoding='utf8'))
    parts = json.loads((output / 'parts_manifest.json').read_text(encoding='utf8'))['parts']
    export = json.loads((output / 'export_report.json').read_text(encoding='utf8'))
    glb = (output / 'stock_car.glb').read_bytes()
    magic, version, length = struct.unpack_from('<4sII', glb)
    assert (magic, version, length) == (b'glTF', 2, len(glb))
    json_length, chunk_type = struct.unpack_from('<II', glb, 12)
    assert chunk_type == 0x4e4f534a
    gltf = json.loads(glb[20:20+json_length])
    channels = sum(len(a['channels']) for a in gltf.get('animations', []))
    assert fit['failed'] == 0, fit
    assert len(parts) == len({p['id'] for p in parts}) == 471
    assert {p['category'] for p in parts} == set(range(1, 44))
    assert export['triangles'] == 68400
    assert channels == 6
    result = {
        'passed': True, 'blender_version': bpy.app.version_string,
        'parts': len(parts), 'fit_checks': 22,
        'export_triangles': export['triangles'], 'animation_channels': channels,
        'portable_output_override': True,
    }
    target = base.parent / 'rebuild_check'
    target.mkdir(exist_ok=True)
    (target / 'result.json').write_text(json.dumps(result, indent=2), encoding='utf8')
    print('REBUILD_CHECK_PASSED', json.dumps(result), flush=True)
