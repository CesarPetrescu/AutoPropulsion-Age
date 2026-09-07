# Restore inspection helpers after reopening the saved kit. Does not alter models.
import bpy
from pathlib import Path
out=Path(bpy.data.filepath).parent
sc=bpy.data.scenes['SM_01_Assembled']
parts=[o for o in sc.objects if 'part_id' in o and o.name.startswith('SM_')]
parts += [o for o in bpy.data.scenes['SM_04_Workshop'].objects if 'part_id' in o and o.name.startswith('SM_')]
ns={'SC':sc,'PARTS':parts,'OUT':out}
path=out/'scripts'/'finish_modular_kit.py'
exec(compile(path.read_text(encoding='utf8'),str(path),'exec'),ns)
bpy.app.driver_namespace['SMKIT']=ns
# Example AFTER loading: bpy.app.driver_namespace['SMKIT']['activate_variant']('sport_rim_fl')
