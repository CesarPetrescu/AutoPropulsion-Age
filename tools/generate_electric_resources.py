"""Reproducible native block models, recipes and icons. Does not alter the original sedan authoring kit."""
import json
from pathlib import Path
from PIL import Image, ImageDraw
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT/'src/main/resources'
ASSET = RES/'assets/sparkmotors'
DATA = RES/'data/sparkmotors'
def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2)+'\n')

def box(a,b,texture):
    return {'from':a,'to':b,'faces':{face:{'texture':'#'+texture} for face in ('north','south','east','west','up','down')}}

def recipe(name, ingredients):
    write(DATA/'recipe'/f'{name}.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':x} for x in ingredients], 'result':{'id':'sparkmotors:'+name,'count':1}})

lang=json.loads((ASSET/'lang/en_us.json').read_text())
types=[('hybrid','HEV',220,1.8),('plug_in_hybrid','PHEV',400,18),('electric_400','EV400',400,60),('electric_800','EV800',800,85)]
for index,(key,label,voltage,kwh) in enumerate(types):
    for suffix in ('crate','battery_pack'):
        name=key+'_'+suffix
        title={'hybrid':'Parallel Hybrid','plug_in_hybrid':'Plug-in Parallel Hybrid','electric_400':'400 V Electric','electric_800':'800 V Electric'}[key]
        lang['item.sparkmotors.'+name]=title+(' Sedan Crate' if suffix=='crate' else ' Traction Battery Pack')
        write(ASSET/'models/item'/f'{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'sparkmotors:item/'+name}})
        image=Image.new('RGBA',(64,64));draw=ImageDraw.Draw(image)
        draw.rounded_rectangle((4,14,60,55),radius=4,fill='#35434e',outline='#a7b6c1',width=3)
        draw.rectangle((10,19,54,24),fill=['#45bda8','#34a0c2','#39baa9','#ff903e'][index])
        if suffix=='crate':
            draw.polygon([(11,40),(18,32),(39,32),(48,39),(54,42),(54,47),(10,47)],fill='#aebac1')
            draw.rectangle((21,34,37,39),fill='#1f3445');draw.ellipse((15,43,25,53),fill='#131c23');draw.ellipse((42,43,52,53),fill='#131c23')
        else:
            for x in range(12,53,9):draw.rectangle((x,30,x+6,47),fill='#8195a0')
            draw.rectangle((23,10,39,15),fill='#ff903e')
        draw.text((7,57),label,fill='white')
        (ASSET/'textures/item').mkdir(parents=True,exist_ok=True);image.save(ASSET/'textures/item'/f'{name}.png')
    recipe(key+'_battery_pack',['minecraft:copper_block','minecraft:copper_block','minecraft:redstone_block','minecraft:iron_block','minecraft:quartz','minecraft:quartz',['minecraft:iron_ingot','minecraft:gold_ingot','minecraft:diamond','minecraft:netherite_scrap'][index]])
    write(DATA/'recipe'/f'{key}_crate.json',{'type':'sparkmotors:electric_crate_crafting','category':'misc','pattern':['CRQ',' B ',' P '],'key':{'C':{'item':'minecraft:copper_block'},'R':{'item':'minecraft:redstone_block'},'Q':{'item':'minecraft:quartz'},'B':{'item':'sparkmotors:sedan_crate'},'P':{'item':'sparkmotors:'+key+'_battery_pack'}},'result':{'id':'sparkmotors:'+key+'_crate','count':1}})
for index,(key,title,voltage) in enumerate([('lv','Workshop',48),('home','Wallbox',240),('rapid','Rapid DC',480),('ultra','Ultra DC',3200)]):
    name=key+'_charger';lang['block.sparkmotors.'+name]=f'{voltage} V {title} Vehicle Charger'
    textures={'case':'minecraft:block/iron_block','dark':'minecraft:block/gray_concrete','screen':'minecraft:block/cyan_concrete','cable':'minecraft:block/black_concrete','hv':'minecraft:block/orange_concrete'}
    if index<2:
        elements=[box([3,0,4],[13,12+index*3,12],'case'),box([4,5,3.7],[12,11+index*2,4],'dark'),box([5,7,3.5],[11,10+index*2,3.7],'screen'),box([13,2,6],[14,8,8],'cable'),box([12,8,6],[14,9,8],'hv')]
    else:
        elements=[box([1,0,2],[15,2,14],'dark'),box([3,2,4],[13,15,12],'case'),box([4,8,3.7],[12,14,4],'dark'),box([5,10,3.5],[11,13,3.7],'screen'),box([13,4,5],[14,12,7],'cable'),box([12,12,5],[14,13,7],'hv')]
        for y in range(3,8):elements.append(box([4,y,3.8],[12,y+.3,4],'dark'))
        if index==3:
            elements.extend([box([0,2,5],[3,15,13],'dark'),box([13,2,8],[16,15,13],'dark'),box([3,14.7,3.6],[13,15.3,4.2],'hv')])
    write(ASSET/'models/block'/f'{name}.json',{'textures':textures|{'particle':'minecraft:block/iron_block'},'elements':elements})
    write(ASSET/'blockstates'/f'{name}.json',{'variants':{'facing='+side:{'model':'sparkmotors:block/'+name,'y':angle} for side,angle in [('north',0),('east',90),('south',180),('west',270)]}})
    write(ASSET/'models/item'/f'{name}.json',{'parent':'sparkmotors:block/'+name})
    write(DATA/'loot_table/blocks'/f'{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'sparkmotors:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    recipe(name,['minecraft:iron_block','minecraft:copper_block','minecraft:redstone_block','minecraft:quartz']+(['sparkmotors:'+['lv','home','rapid'][index-1]+'_charger'] if index else []))
lang['item.sparkmotors.charging_cable']='Vehicle Charging Cable'
write(ASSET/'models/item/charging_cable.json',{'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/lead'}})
recipe('charging_cable',['minecraft:copper_ingot','minecraft:copper_ingot','minecraft:string','minecraft:dried_kelp'])
write(ASSET/'lang/en_us.json',lang)
write(RES/'data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':['sparkmotors:'+x+'_charger' for x in ['lv','home','rapid','ultra']]})
print('Generated 4 vehicle crates, 4 stateful pack icons, 4 native charger models, recipes and tags.')
