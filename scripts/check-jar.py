#!/usr/bin/env python3
from pathlib import Path
import zipfile
root=Path(__file__).resolve().parents[1]
jars=[p for p in (root/'mod/build/libs').glob('*.jar') if not p.name.endswith('-sources.jar')]
assert len(jars)==1,f'Expected one installable mod JAR, got {jars}'
with zipfile.ZipFile(jars[0]) as f:
 names=set(f.namelist())
 for path in ['META-INF/neoforge.mods.toml','com/photonspark/autopropulsion/VehicleEntity.class','com/photonspark/autopropulsion/sim/VehicleSimulation.class','assets/autopropulsion/models/vehicle/hatchback.mesh.json','assets/autopropulsion/sounds/engine_loop.ogg']:
  assert path in names,f'Missing runtime entry {path}'
 assert not any(p.endswith('.blend') for p in names),'Authoring scenes must not ship in mod JAR'
print('JAR_VALIDATION_PASS',jars[0].name)
