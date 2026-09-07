"""Run in a NEW Blender file. Authoring only; does not install a Minecraft mod."""
import bpy
from pathlib import Path
base=Path(__file__).parent
ns={'__name__':'sparkmotors_rebuild'}
for filename in ['build_modular_kit.py','finish_modular_kit.py']:
    path=base/filename
    ns['__file__']=str(path.resolve())
    exec(compile(path.read_text(encoding='utf8'),str(path),'exec'),ns)
bpy.app.driver_namespace['SMKIT']=ns
ns['build_stock'](); ns['refine_stock'](); ns['upgrades'](); ns['alternate_engines'](); ns['workshop']()
ns['extra_variants_and_fit'](); ns['studio_scene'](ns['SC']); ns['inspection_scenes'](); ns['bake_display_modifiers']()
report=ns['validate_kit']()
if report['failed']:raise RuntimeError('Fit validation failed; inspect fit_report.json')
ns['manifest'](); ns['export_stock']()
for path in base.glob('*.py'):
    text=bpy.data.texts.get(path.name) or bpy.data.texts.new(path.name)
    text.clear(); text.write(path.read_text(encoding='utf8'))
bpy.context.window.scene=ns['SC']; bpy.context.scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(ns['OUT']/'sparkmotors_modular.blend'))
