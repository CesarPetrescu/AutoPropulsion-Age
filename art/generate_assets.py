#!/usr/bin/env python3
"""Rebuild original runtime meshes, genuine mesh-rendered inventory icons and builtin data.
Standard-library only. Run from any directory. Blender consumes these same meshes.
"""
from __future__ import annotations
import gzip, hashlib, json, math, struct, sys, zlib
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from geometry import Mesh, component, hatch
from catalog import FAMILIES, definitions
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'mod/src/main/resources'
ASSETS=RES/'assets/autopropulsion'
DATA=RES/'data/autopropulsion'

def write_json(path, value, pretty=False):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,sort_keys=True,indent=2 if pretty else None,separators=None if pretty else (',',':'))+'\n',encoding='utf-8')

def png(path, width, height, rgba):
    def chunk(kind,data): return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    raw=b''.join(b'\x00'+bytes(rgba[y*width*4:(y+1)*width*4]) for y in range(height))
    encoded=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
    path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(encoded)

def icon(mesh: Mesh, path: Path, size=48):
    """Orthographic triangle rasterizer with a depth buffer. Not placeholder category squares."""
    faces=[]; projected=[]
    def project(v):
        x,y,z=v
        return ((x-z)*.7071, y*.8165-(x+z)*.4082, (x+y+z)*.57735)
    for obj in mesh.objects:
        coords=[project(v) for v in obj['vertices']]; projected.extend(coords)
        for face in obj['faces']:
            if len(face)<3: continue
            va,vb,vc=[obj['vertices'][i] for i in face[:3]]
            u=[vb[i]-va[i] for i in range(3)]; v=[vc[i]-va[i] for i in range(3)]
            n=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
            length=math.sqrt(sum(c*c for c in n)) or 1
            light=.48+.52*abs((n[0]*.35+n[1]*.8+n[2]*.48)/length)
            rgb=[int(min(255,max(0,c*light*255))) for c in obj['color'][:3]]+[255]
            for i in range(1,len(face)-1): faces.append(([coords[face[0]],coords[face[i]],coords[face[i+1]]],rgb))
    lo=[min(p[i] for p in projected) for i in (0,1)]; hi=[max(p[i] for p in projected) for i in (0,1)]
    scale=(size-6)/max(hi[0]-lo[0],hi[1]-lo[1],.001); cx=(lo[0]+hi[0])/2; cy=(lo[1]+hi[1])/2
    rgba=bytearray(size*size*4); depth=[-float('inf')]*(size*size)
    for triangle,color in faces:
        p=[((a-cx)*scale+size/2,size/2-(b-cy)*scale,c) for a,b,c in triangle]
        (x0,y0,z0),(x1,y1,z1),(x2,y2,z2)=p
        den=(y1-y2)*(x0-x2)+(x2-x1)*(y0-y2)
        if abs(den)<1e-9: continue
        for y in range(max(0,int(min(q[1] for q in p))), min(size-1,int(max(q[1] for q in p))+1)+1):
            for x in range(max(0,int(min(q[0] for q in p))), min(size-1,int(max(q[0] for q in p))+1)+1):
                a=((y1-y2)*(x+.5-x2)+(x2-x1)*(y+.5-y2))/den
                b=((y2-y0)*(x+.5-x2)+(x0-x2)*(y+.5-y2))/den; c=1-a-b
                if min(a,b,c)<-1e-6: continue
                z=a*z0+b*z1+c*z2; idx=y*size+x
                if z>depth[idx]: depth[idx]=z; rgba[idx*4:idx*4+4]=bytes(color)
    png(path,size,size,rgba)

def nbt_structure():
    def text(s):
        encoded=s.encode(); return struct.pack('>H',len(encoded))+encoded
    def named(tag,name,body): return bytes([tag])+text(name)+body
    content=named(3,'DataVersion',struct.pack('>i',3955))
    content+=named(9,'size',b'\x03'+struct.pack('>i',3)+struct.pack('>iii',16,8,16))
    content+=named(9,'palette',b'\x0a'+struct.pack('>i',1)+named(8,'Name',text('minecraft:air'))+b'\x00')
    content+=named(9,'blocks',b'\x0a'+struct.pack('>i',0))
    content+=named(9,'entities',b'\x0a'+struct.pack('>i',0))
    path=DATA/'structure/empty.nbt'; path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(gzip.compress(b'\x0a\x00\x00'+content+b'\x00',mtime=0))

def main():
    catalogue=definitions(); meshes={name:component(name) for name in FAMILIES}; meshes['hatch_01']=hatch()
    mesh_files=[]
    for name,mesh in sorted(meshes.items()):
        model_path=ASSETS/('models/vehicle/hatch_01.json' if name=='hatch_01' else f'models/vehicle/parts/{name}.json')
        write_json(model_path,mesh.document()); mesh_files.append(model_path)
        icon(mesh,ASSETS/f'textures/item/parts/{name}.png')
        write_json(ASSETS/f'models/item/parts/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'autopropulsion:item/parts/{name}'}})
    overrides=[]; manifest_lines=[]; language={
        'itemGroup.autopropulsion':'AutoPropulsion Age',
        'entity.autopropulsion.hatch':'APA Hatch 01',
        'item.autopropulsion.chassis_kit':'Hatch 01 Assembly Kit',
        'item.autopropulsion.part':'Vehicle Part',
        'item.autopropulsion.wrench':'Garage Wrench',
        'item.autopropulsion.laptop':'Diagnostic Laptop',
        'item.autopropulsion.fuel_can':'Fuel Can (10 L)',
        'block.autopropulsion.garage_lift':'Garage Lift',
        'block.autopropulsion.dyno':'Engine Dyno Console',
        'key.categories.autopropulsion':'AutoPropulsion Age',
        'key.autopropulsion.start':'Start / stop engine',
        'key.autopropulsion.up':'Shift up',
        'key.autopropulsion.down':'Shift down',
        'key.autopropulsion.clutch':'Clutch',
        'key.autopropulsion.hood':'Toggle hood',
    }
    for index,part in enumerate(catalogue,1):
        name=part['id'].split(':',1)[1]; mesh_name=part['model'].rsplit('/',1)[1]
        write_json(DATA/f'autopropulsion/parts/{name}.json',part,True)
        overrides.append({'predicate':{'custom_model_data':index},'model':f'autopropulsion:item/parts/{mesh_name}'})
        manifest_lines.append('\t'.join([part['id'],str(index),str(part['implemented']).lower(),part['preferred_slot']]))
        language['part.autopropulsion.'+name]=name.replace('model_','').replace('_',' ').title()+(' [Model study]' if not part['implemented'] else '')
    write_json(ASSETS/'models/item/part.json',{'parent':'minecraft:item/generated','textures':{'layer0':'autopropulsion:item/parts/piston'},'overrides':overrides})
    (RES/'autopropulsion-builtin-parts.tsv').write_text('\n'.join(manifest_lines)+'\n',encoding='utf-8')
    for item,model in {'chassis_kit':'hatch_01','wrench':'connecting_rod','laptop':'laptop','fuel_can':'jerry_can','garage_lift':'garage_lift','dyno':'dyno_rollers'}.items():
        write_json(ASSETS/f'models/item/{item}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'autopropulsion:item/parts/{model}'}})
    png(ASSETS/'textures/white.png',1,1,[255,255,255,255])
    for block in ('garage_lift','dyno'):
        write_json(ASSETS/f'blockstates/{block}.json',{'variants':{'':{'model':'autopropulsion:block/workshop_base'}}})
        write_json(DATA/f'loot_table/blocks/{block}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'autopropulsion:'+block}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    write_json(ASSETS/'models/block/workshop_base.json',{'parent':'minecraft:block/block','textures':{'particle':'minecraft:block/iron_block'}})
    write_json(ASSETS/'lang/en_us.json',language,True)
    write_json(DATA/'tags/block/surface/asphalt.json',{'replace':False,'values':['minecraft:gray_concrete','minecraft:black_concrete','minecraft:stone','minecraft:smooth_stone']},True)
    write_json(RES/'data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':['autopropulsion:garage_lift','autopropulsion:dyno']})
    recipes={
        'chassis_kit':(['IPI','IRI','ILI'],{'I':'minecraft:iron_block','P':'minecraft:piston','R':'minecraft:redstone_block','L':'minecraft:leather'}),
        'garage_lift':(['I I','IPI','IRI'],{'I':'minecraft:iron_ingot','P':'minecraft:piston','R':'minecraft:redstone'}),
        'dyno':(['III','PRP','III'],{'I':'minecraft:iron_ingot','P':'minecraft:piston','R':'minecraft:redstone'}),
        'wrench':([' I ',' II','I  '],{'I':'minecraft:iron_ingot'}),
        'laptop':(['GGG','IRI','III'],{'G':'minecraft:glass','I':'minecraft:iron_ingot','R':'minecraft:redstone'}),
    }
    for name,(pattern,key) in recipes.items():
        write_json(DATA/f'recipe/{name}.json',{'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'item':v} for k,v in key.items()},'result':{'id':'autopropulsion:'+name,'count':1}},True)
    nbt_structure()
    records=[{'path':str(p.relative_to(ROOT)), 'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in mesh_files]
    write_json(ROOT/'art/assets-manifest.json',{'schema_version':1,'quality':'original procedural prototype','mesh_count':len(meshes),'part_definition_count':len(catalogue),'installable_definition_count':sum(p['implemented'] for p in catalogue),'meshes':records},True)
    docs=ROOT/'docs'; docs.mkdir(exist_ok=True)
    lines=['# Asset catalogue','','Generated from `art/catalog.py`; do not hand-edit. All entries are original low-poly prototype geometry, not final production art. A model does not imply its gameplay system exists.','','| Family | Runtime mesh | Authoring |','|---|---|---|']
    lines += [f'| {name} | `assets/autopropulsion/models/vehicle/parts/{name}.json` | Editable object collection in generated `catalog.blend` |' for name in FAMILIES]
    lines += ['','The hatch body, wheels, hood, interior and engine bay live in `hatch.blend`. `art/blender_build.py` imports the exact runtime geometry, adds materials/locators and exports GLB files. Small accessories intentionally reuse family-level housings; this is not a catalogue of individually engineered or manufacturer-accurate parts.']
    (docs/'ASSET_CATALOG.md').write_text('\n'.join(lines)+'\n')
    lines=['# Parts and implementation status','','Generated from the builtin data source. Model-only study items are explicitly rejected by server-side installation, rather than accepted with no effect.','','| Part | Slot | Gameplay |','|---|---|---|']
    lines += [f"| `{p['id']}` | `{p['preferred_slot']}` | {'Installable prototype' if p['implemented'] else 'Model study only; not installable'} |" for p in catalogue]
    (docs/'PARTS.md').write_text('\n'.join(lines)+'\n')
    print(f'GENERATED meshes={len(meshes)} definitions={len(catalogue)} installable={sum(p["implemented"] for p in catalogue)}')

if __name__=='__main__': main()
