"""Generate item icons, recipe JSON, language strings and a synthesized engine loop.
Run with Python + Pillow; ffmpeg is required for the OGG conversion.
"""
import json, math, random, struct, subprocess, wave
from pathlib import Path
from PIL import Image
repo=Path(__file__).resolve().parent.parent
res=repo/'src/main/resources';assets=res/'assets/sparkmotors';data=res/'data/sparkmotors'
def js(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n')
icons={'sedan_crate':'../../assembled.png','garage_wrench':'head_bolt_0.158_4.png','fuel_can':'jerry_can.png','garage_controller':'garage_lift.png',
'stock_engine':'i4_engine.png','sport_engine':'i4_engine.png','stock_transmission':'manual_5speed.png','sport_transmission':'sequential_gearbox.png',
'stock_wheels':'rim_fr.png','sport_wheels':'sport_rim_fr.png','stock_brakes':'brake_disc_fr.png','sport_brakes':'brake_caliper_fr.png',
'stock_suspension':'coilover_fr.png','sport_suspension':'sport_coilover_fr.png','stock_body':'front_bumper.png','sport_body':'sport_front_bumper.png'}
families={'v6':'v6_engine','flat4':'flat4_engine',**{f'rotor{i}':f'rotary_{i}rotor' for i in range(1,5)}}
service={'intake':'intake_manifold','fuel_system':'fuel_rail','ignition':'coil_-1.5575','cooling':'radiator','internals':'crankshaft_i4'}
for family,model in families.items():
    for grade in ['stock','sport']:icons[f'{grade}_{family}_engine']=model+'.png'
for slot,model in service.items():
    for grade in ['stock','performance']:icons[f'{grade}_{slot}']=model+'.png'
icons.update(turbo_kit='turbocharger.png',supercharger_kit='centrifugal_supercharger.png')
catalog=json.loads((repo/'docs/powertrain-catalog.json').read_text())
fallback={'intake':'intake_manifold','fuel_system':'fuel_rail','ignition':'coil_-1.5575','cooling':'radiator','internals':'crankshaft_i4','exhaust':'exhaust_header','flywheel':'flywheel','headwork':'camshaft_-1','oil_system':'oil_pan','induction':'turbocharger'}
for slot in catalog['slots']:
    for option in slot['options']:icons[option['item']]=fallback[slot['id']]+'.png'
preview=repo/'assets/modular_car_kit/previews'
for name,image in icons.items():
    path=repo/'assets/modular_car_kit/assembled.png' if name=='sedan_crate' else preview/image
    hardware=repo/'docs/hardware-gallery'/f'{name}.png'
    if hardware.exists():path=hardware
    im=Image.open(path).convert('RGBA');im.thumbnail((128,128))
    dest=assets/'textures/item'/f'{name}.png';dest.parent.mkdir(parents=True,exist_ok=True)
    # Shape icons use transparent backgrounds; the car crate keeps its overview thumbnail.
    if name!='sedan_crate':
        pixels=im.load()
        bg=im.getpixel((0,0))
        for y in range(im.height):
            for x in range(im.width):
                p=pixels[x,y]
                if sum(abs(p[i]-bg[i]) for i in range(3))<25:pixels[x,y]=(*p[:3],0)
    im.save(dest,optimize=True)
    js(assets/'models/item'/f'{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'sparkmotors:item/{name}'}})
white=assets/'textures/entity/white.png';white.parent.mkdir(parents=True,exist_ok=True);Image.new('RGBA',(2,2),'white').save(white)
js(assets/'blockstates/garage_controller.json',{'variants':{'':{'model':'sparkmotors:block/garage_controller'}}})
js(assets/'models/block/garage_controller.json',{'parent':'minecraft:block/cube','textures':{'particle':'minecraft:block/iron_block','down':'minecraft:block/iron_block','up':'minecraft:block/smithing_table_top','north':'minecraft:block/blast_furnace_front','south':'minecraft:block/iron_block','east':'minecraft:block/iron_block','west':'minecraft:block/iron_block'}})
js(data/'loot_table/blocks/garage_controller.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'sparkmotors:garage_controller'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
lang={'entity.sparkmotors.sedan':'Modular Sedan','block.sparkmotors.garage_controller':'Garage Controller','key.categories.sparkmotors':'AutoPropulsion Age',
      'key.sparkmotors.ignition':'Start / stop engine','key.sparkmotors.garage':'Open garage','key.sparkmotors.lights':'Headlights','key.sparkmotors.panels':'Open / close panels','key.sparkmotors.reverse':'Toggle forward / reverse','key.sparkmotors.horn':'Horn','subtitles.sparkmotors.engine':'Car engine running'}
for name in icons:lang['item.sparkmotors.'+name]=name.replace('_',' ').title()
lang['item.sparkmotors.sedan_crate']='Sedan Crate';lang['item.sparkmotors.fuel_can']='Fuel Can (10 L)'
for family,title in {'i4':'Inline-4','v6':'V6','flat4':'Flat-four',**{f'rotor{i}':f'{i}-Rotor' for i in range(1,5)}}.items():
    for grade in ['stock','sport']:
        key=grade+'_engine' if family=='i4' else grade+'_'+family+'_engine'
        lang['item.sparkmotors.'+key]=grade.title()+' '+title+' Engine'
lang['item.sparkmotors.performance_internals']='Forged Internals / Seal Kit'
lang['item.sparkmotors.performance_fuel_system']='High-flow Fuel System'
lang['item.sparkmotors.performance_cooling']='Heavy-duty Cooling Kit'
lang['item.sparkmotors.turbo_kit']='Complete Turbo Kit'
lang['item.sparkmotors.supercharger_kit']='Complete Supercharger Kit'
lang['key.sparkmotors.clutch']='Disengage clutch (hold to free-rev)'
for slot in catalog['slots']:
    for option in slot['options']:lang['item.sparkmotors.'+option['item']]=option['label']
js(assets/'lang/en_us.json',lang)
def recipe(name,pattern,keys,count=1):
    js(data/'recipe'/f'{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':{k:{'item':v} for k,v in keys.items()},'result':{'id':'sparkmotors:'+name,'count':count}})
recipe('sedan_crate',['III','EWT','III'],{'I':'minecraft:iron_block','E':'sparkmotors:stock_engine','W':'sparkmotors:stock_wheels','T':'sparkmotors:stock_transmission'})
recipe('garage_wrench',[' I ',' II','I  '],{'I':'minecraft:iron_ingot'})
recipe('garage_controller',['III','RCR','III'],{'I':'minecraft:iron_ingot','R':'minecraft:redstone','C':'minecraft:crafting_table'})
recipe('fuel_can',[' I ','ICI',' I '],{'I':'minecraft:iron_nugget','C':'minecraft:coal'})
centers={'engine':'minecraft:piston','transmission':'minecraft:iron_block','wheels':'minecraft:coal_block','brakes':'minecraft:copper_ingot','suspension':'minecraft:string','body':'minecraft:glass'}
for slot,center in centers.items():
    recipe('stock_'+slot,['III','ICI','III'],{'I':'minecraft:iron_ingot','C':center})
    recipe('sport_'+slot,['GRG','RSR','GRG'],{'G':'minecraft:gold_ingot','R':'minecraft:redstone','S':'sparkmotors:stock_'+slot})
family_centers={'v6':'piston','flat4':'quartz','rotor1':'copper_block','rotor2':'gold_block','rotor3':'diamond','rotor4':'netherite_ingot'}
for family,center in family_centers.items():
    recipe('stock_'+family+'_engine',['III','CEC','III'],{'I':'minecraft:iron_ingot','C':'minecraft:'+center,'E':'sparkmotors:stock_engine'})
    recipe('sport_'+family+'_engine',['GRG','RSR','GRG'],{'G':'minecraft:gold_ingot','R':'minecraft:redstone','S':'sparkmotors:stock_'+family+'_engine'})
for slot,center in {'intake':'hopper','fuel_system':'bucket','ignition':'redstone_torch','cooling':'water_bucket','internals':'iron_pickaxe'}.items():
    recipe('stock_'+slot,['III','ICI','III'],{'I':'minecraft:iron_ingot','C':'minecraft:'+center})
    recipe('performance_'+slot,['GRG','RSR','GRG'],{'G':'minecraft:gold_ingot','R':'minecraft:redstone','S':'sparkmotors:stock_'+slot})
for name,center in [('turbo_kit','diamond'),('supercharger_kit','gold_block')]:
    recipe(name,['IRI','PCP','IRI'],{'I':'minecraft:iron_ingot','R':'minecraft:redstone','P':'minecraft:piston','C':'minecraft:'+center})
for slot in catalog['slots']:
    for option in slot['options']:
        name=option['item'];v=option['value']
        if (data/'recipe'/f'{name}.json').exists():continue
        # Distinct recipes, including choices 3/4, preserve all old registry IDs.
        center=['iron_block','gold_block','diamond','netherite_ingot','copper_block','amethyst_block'][v-1]
        ingredient={'exhaust':'hopper','flywheel':'iron_ingot','headwork':'piston','oil_system':'bucket'}.get(slot['id'],'redstone')
        recipe(name,['ICI','PSP','IRI'],{'I':'minecraft:iron_ingot','C':'minecraft:'+center,'P':'minecraft:'+ingredient,'S':('sparkmotors:'+slot['options'][0]['item']) if v>1 else 'minecraft:iron_block','R':'minecraft:redstone'})
for path in (data/'recipe').glob('*.json'):
    value=json.loads(path.read_text());name=value['result']['id'].split(':')[1]
    if name=='sedan_crate' or any(v.get('item','').startswith('sparkmotors:') for v in value.get('key',{}).values()):value['type']='sparkmotors:engine_crafting';js(path,value)
# A clean one-second periodic four-cylinder-style pulse loop. Original synthesized audio.
sound=assets/'sounds';sound.mkdir(parents=True,exist_ok=True)
wav=repo/'.codex-reference/engine_loop.wav';rate=44100
wav.parent.mkdir(parents=True,exist_ok=True)
with wave.open(str(wav),'wb') as f:
    f.setnchannels(1);f.setsampwidth(2);f.setframerate(rate)
    frames=[]
    for i in range(rate):
        t=i/rate
        base=sum(math.sin(2*math.pi*40*k*t)/k**1.25 for k in range(1,13))
        texture=.06*math.sin(2*math.pi*727*t)+.035*math.sin(2*math.pi*1201*t)
        sample=math.tanh(base*1.3)*.34+texture
        frames.append(struct.pack('<h',int(sample*22000)))
    f.writeframes(b''.join(frames))
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','5',str(sound/'engine_loop.ogg')],check=True)
# This generator owns only its legacy entry. Authored layer definitions survive regeneration.
sound_manifest=assets/'sounds.json'
sound_entries=json.loads(sound_manifest.read_text()) if sound_manifest.exists() else {}
sound_entries.setdefault('engine_loop',{'subtitle':'subtitles.sparkmotors.engine','sounds':[{'name':'sparkmotors:engine_loop','stream':False}]})
js(sound_manifest,sound_entries)
import runpy
runpy.run_path(str(repo/'tools/generate_mechanical_resources.py'),run_name='__main__')
print(f'Generated {len(icons)} icons and recipes, block resources, translations and engine_loop.ogg')
