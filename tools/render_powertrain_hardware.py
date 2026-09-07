"""Run via Blender MCP after build_engine_workbench.py. Render the exact 42 item models."""
import bpy,json,time
from pathlib import Path
repo=Path(__file__).resolve().parent.parent
out=repo/'docs/hardware-gallery';out.mkdir(parents=True,exist_ok=True)
rendered=[]
for s in bpy.data.scenes:
    if not s.name.startswith('APA_INSPECT_PART_'):continue
    bpy.context.window.scene=s
    s.render.filepath=str(out/(s['item_id']+'.png'))
    bpy.ops.render.render(write_still=True,scene=s.name)
    rendered.append(s['item_id'])
    (repo/'.codex-reference/hardware-render-progress.json').write_text(json.dumps({'completed':rendered}))
bpy.context.window.scene=bpy.data.scenes['APA_INSPECT_I4_TwinTurbo']
result={'rendered':len(rendered),'directory':str(out)}
