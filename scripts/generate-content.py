#!/usr/bin/env python3
"""Generate workstation resources, recipes, audio source and stable-index catalogue code.
Blender-generated item models are not overwritten here.
"""
from pathlib import Path
import json,struct,zlib,wave,math
R=Path(__file__).resolve().parents[1];A=R/'mod/src/main/resources/assets/autopropulsion';D=R/'mod/src/main/resources/data/autopropulsion'
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,indent=2)+'\n')
def png(p,c,size=16):
 def ch(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
 p.parent.mkdir(parents=True,exist_ok=True);rows=b''.join(b'\0'+bytes(c)*size for _ in range(size));p.write_bytes(b'\x89PNG\r\n\x1a\n'+ch(b'IHDR',struct.pack('>2I5B',size,size,8,2,0,0,0))+ch(b'IDAT',zlib.compress(rows))+ch(b'IEND',b''))
colors={'paint':(184,36,22),'dark':(7,10,12),'rubber':(11,13,15),'metal':(143,156,163),'alloy':(199,207,212),'glass':(14,43,54),'red':(179,8,6),'amber':(255,110,11),'white':(235,250,255),'blue':(15,76,120),'green':(25,115,74),'bronze':(143,89,33),'cloth':(18,23,30)}
for name,c in colors.items():png(A/f'textures/block/palette/{name}.png',c)
png(A/'textures/entity/white.png',(255,255,255));png(A/'textures/item/part.png',colors['metal'])
for name,color in [('vehicle_blueprint','blue'),('mechanic_wrench','alloy'),('jerry_can','red'),('empty_jerry_can','metal')]:
 png(A/f'textures/item/{name}.png',colors[color]);write(A/f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'autopropulsion:item/{name}'}})
def el(a,b,tex):return {'from':a,'to':b,'faces':{f:{'texture':'#'+tex} for f in ['up','down','north','south','east','west']}}
textures={'metal':'autopropulsion:block/palette/metal','paint':'autopropulsion:block/palette/paint','dark':'autopropulsion:block/palette/dark','particle':'autopropulsion:block/palette/metal'}
models={'garage_lift':[el([0,0,0],[16,2,16],'metal'),el([6,2,6],[10,15,10],'paint'),el([1,13,2],[15,15,14],'metal')],
'dyno':[el([0,0,0],[16,4,16],'dark'),el([1,4,2],[15,7,6],'metal'),el([1,4,10],[15,7,14],'metal')],
'parts_bench':[el([0,12,0],[16,15,16],'metal'),el([1,0,1],[3,12,3],'paint'),el([13,0,1],[15,12,3],'paint'),el([1,0,13],[3,12,15],'paint'),el([13,0,13],[15,12,15],'paint')]}
for name,elements in models.items():write(A/f'models/block/{name}.json',{'textures':textures,'elements':elements});write(A/f'models/item/{name}.json',{'parent':f'autopropulsion:block/{name}'});write(A/f'blockstates/{name}.json',{'variants':{'':{'model':f'autopropulsion:block/{name}'}}})
write(A/'models/block/asphalt.json',{'parent':'minecraft:block/cube_all','textures':{'all':'autopropulsion:block/palette/dark'}});write(A/'models/item/asphalt.json',{'parent':'autopropulsion:block/asphalt'});write(A/'blockstates/asphalt.json',{'variants':{'':{'model':'autopropulsion:block/asphalt'}}})
parts=sorted(json.loads((R/'assets/catalog.json').read_text())['parts'],key=lambda p:p['model_index'])
for part in parts:write(D/f'autopropulsion/parts/{part["id"]}.json',part)
functional=[p for p in parts if p['functional']];starters=['i4_block_cast','stock_connecting_rods','stock_camshaft','naturally_aspirated_intake','stock_radiator','street_tires','stock_ecu'];slots={p['id']:p['slot'] for p in parts}
js=lambda xs:','.join(json.dumps(x) for x in xs)
java='package com.photonspark.autopropulsion;\n/** Generated from explicit, stable model_index values in assets/catalog.json. */\npublic final class BuiltinCatalog {\n'
for key,values in [('IDS',[p['id'] for p in parts]),('FUNCTIONAL_IDS',[p['id'] for p in functional]),('STARTER_PARTS',starters)]:java+=f'    public static final String[] {key}={{{js(values)}}};\n'
java+=f'    public static final java.util.Set<String> FUNCTIONAL_SLOTS=java.util.Set.of({js(sorted(set(p["slot"] for p in functional)))});\n'
java+='    public static String slotOf(String id) {return switch(id) {\n'+''.join('        case '+json.dumps(s)+' -> '+json.dumps(slots[s])+';\n' for s in starters)+'        default -> throw new IllegalArgumentException("Unknown starter part: "+id);\n    };}\n    private BuiltinCatalog() {}\n}\n'
(R/'mod/src/main/java/com/photonspark/autopropulsion/BuiltinCatalog.java').write_text(java)
write(A/'models/item/part.json',{'parent':'minecraft:item/generated','textures':{'layer0':'autopropulsion:item/part'},'overrides':[{'predicate':{'custom_model_data':p['model_index']},'model':p['model']} for p in parts]})
def recipe(name,pattern,keys,result,count=1):write(D/f'recipe/{name}.json',{'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'item':v} for k,v in keys.items()},'result':{'id':result,'count':count}})
recipe('vehicle_blueprint',['III','GFG','IPI'],{'I':'minecraft:iron_ingot','G':'minecraft:glass','F':'minecraft:furnace','P':'minecraft:piston'},'autopropulsion:vehicle_blueprint')
recipe('mechanic_wrench',['I I',' I ',' S '],{'I':'minecraft:iron_ingot','S':'minecraft:stick'},'autopropulsion:mechanic_wrench')
recipe('empty_jerry_can',[' II','I I','III'],{'I':'minecraft:iron_ingot'},'autopropulsion:empty_jerry_can')
write(D/'recipe/jerry_can.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'autopropulsion:empty_jerry_can'},{'item':'minecraft:coal'},{'item':'minecraft:coal'}],'result':{'id':'autopropulsion:jerry_can','count':1}})
for name,core in [('garage_lift','piston'),('dyno','observer'),('parts_bench','crafting_table')]:recipe(name,['III','IPI','III'],{'I':'minecraft:iron_ingot','P':'minecraft:'+core},'autopropulsion:'+name)
recipe('asphalt',['GGG','GCG','GGG'],{'G':'minecraft:gravel','C':'minecraft:coal'},'autopropulsion:asphalt',8)
unique=['piston','sticky_piston','iron_block','diamond','clock','compass','furnace','blast_furnace','redstone_block','netherite_ingot','glass','blue_dye','leather','rabbit_hide','comparator','repeater']
for part,extra in zip(functional,unique,strict=True):
 write(D/f'recipe/part_{part["id"]}.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'minecraft:iron_ingot'},{'item':'minecraft:copper_ingot'},{'item':'minecraft:'+extra}],
 'result':{'id':'autopropulsion:part','count':1,'components':{'minecraft:custom_data':{'Part':'autopropulsion:'+part['id']},'minecraft:custom_model_data':part['model_index']}}})
p=R/'assets/source/engine-loop.wav';p.parent.mkdir(parents=True,exist_ok=True)
with wave.open(str(p),'wb') as f:
 f.setnchannels(1);f.setsampwidth(2);f.setframerate(22050);f.writeframes(b''.join(struct.pack('<h',int(6500*(.65*math.sin(math.tau*100*i/22050)+.24*math.sin(math.tau*200*i/22050)+.11*math.sin(math.tau*400*i/22050)))) for i in range(22050)))
print(f'CONTENT_GENERATED {len(parts)} parts, {len(functional)} functional')
for name in ['garage_lift','dyno','parts_bench','asphalt']:
 write(D/f'loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'autopropulsion:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
write(R/'mod/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':['autopropulsion:'+n for n in ['garage_lift','dyno','parts_bench','asphalt']]})
