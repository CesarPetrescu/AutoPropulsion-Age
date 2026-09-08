"""Service item resources from the Java slot catalog. Preserves resources owned by other generators."""
from pathlib import Path
import json,re
from PIL import Image

repo=Path(__file__).resolve().parents[1]
assets=repo/'src/main/resources/assets/sparkmotors'
data=repo/'src/main/resources/data/sparkmotors/recipe'
source=(repo/'sim/src/main/java/com/photonspark/sparkmotors/sim/ComponentSlot.java').read_text()
catalog={}
for line in source.splitlines():
    fields=re.findall(r'"([^"]+)"',line)
    if 'add(all,' in line:
        system,*pairs=fields
        for key,title in zip(pairs[::2],pairs[1::2]):catalog[system+'_'+key]=title
    if 'corner(all,' in line:
        for key,title in zip(fields[::2],fields[1::2]):catalog['service_'+key]=title
    if 'repeated(all,' in line:
        system,*pairs=fields
        for key,title in zip(pairs[::2],pairs[1::2]):catalog[system+'_'+key]=title
catalog.update(sport_muffler='Sport Muffler',coolant_bottle='Coolant (1 L)',oil_bottle='Engine Oil (1 L)',brake_fluid_bottle='Brake Fluid (1 L)',service_jack='Service Jack',pressure_tester='Cooling Pressure Tester',multimeter='Multimeter',tire_gauge='Tire Pressure Gauge',oil_pressure_gauge='Mechanical Oil Pressure Gauge',compression_tester='Compression Tester',tire_pump='Hand Tire Pump')
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n')
langfile=assets/'lang/en_us.json';lang=json.loads(langfile.read_text())
preview=repo/'assets/modular_car_kit/previews'
fallback={'tire':'tire_fr','rim':'rim_fr','brake':'brake_disc_fr','caliper':'brake_caliper_fr','cooling':'radiator','oil':'oil_pan','electrical':'battery','exhaust':'exhaust_header','muffler':'muffler','induction':'turbocharger','driveline':'manual_5speed','jack':'floor_jack','spring':'coilover_fr','damper':'coilover_fr'}
# Minecraft mirrors shaped recipes horizontally. Reserve one canonical orientation per pattern.
patterns=[]
for code in range(256):
    cells=['N' if (code>>i)&1 else 'I' for i in range(8)];cells.insert(4,'C')
    rows=[''.join(cells[i:i+3]) for i in (0,3,6)]
    if ''.join(rows)<= ''.join(row[::-1] for row in rows):patterns.append(rows)
assert len(patterns)>=len(catalog)
for index,(item,title) in enumerate(catalog.items()):
    lang['item.sparkmotors.'+item]=title
    candidate=next((name for word,name in fallback.items() if word in item),'garage_lift')
    image=preview/(candidate+'.png')
    if not image.exists():image=preview/'radiator.png'
    im=Image.open(image).convert('RGBA');im.thumbnail((128,128));im.save(assets/'textures/item'/f'{item}.png',optimize=True)
    write(assets/'models/item'/f'{item}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'sparkmotors:item/{item}'}})
    # Distinct visible workshop patterns. No consumed used-part donor can silently heal in these recipes.
    pattern=patterns[index]
    cells=''.join(pattern)
    center='minecraft:redstone' if 'electrical' in item or item=='multimeter' else 'minecraft:copper_ingot'
    keys={'I':{'item':'minecraft:iron_ingot'},'N':{'item':'minecraft:iron_nugget'},'C':{'item':center}}
    if 'N' not in cells:keys.pop('N')
    if 'I' not in cells:keys.pop('I')
    if item.endswith('_bottle'):
        pattern=[' D ',' WB','   '];keys={'D':{'item':{'coolant_bottle':'minecraft:blue_dye','oil_bottle':'minecraft:coal','brake_fluid_bottle':'minecraft:redstone'}[item]},'W':{'item':'minecraft:water_bucket'},'B':{'item':'minecraft:glass_bottle'}}
    write(data/f'{item}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':keys,'result':{'id':'sparkmotors:'+item,'count':1}})
write(langfile,lang)
write(repo/'docs/service-catalog.json',catalog)
print(f'Generated {len(catalog)} service items, models, translations and distinct recipes')
