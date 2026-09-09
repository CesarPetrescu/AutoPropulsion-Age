"""Deterministic item resources for body styles. Uses only Pillow plus the stable Java profile table."""
from pathlib import Path
import json,re
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[2]
RES=ROOT/'src/main/resources'
ASSET=RES/'assets/sparkmotors';DATA=RES/'data/sparkmotors'
source=(ROOT/'sim/src/main/java/com/photonspark/sparkmotors/sim/BodyStyle.java').read_text()
fields=('nose','tail','halfWidth','belt','roof','cabinFront','roofFront','roofRear','cabinRear','cabinY','cabinZ')
styles=[]
for enum,num,key,title,values in re.findall(r'^\s*([A-Z_]+)\((\d+),"([^"]+)","([^"]+)",([^)]*)\)',source,re.M):
    styles.append(dict(zip(fields,map(float,values.split(','))),id=key,title=title))
assert len(styles)==6
powertrains={'combustion':'Combustion','hybrid':'Parallel Hybrid','plug_in_hybrid':'Plug-in Hybrid','electric_400':'400 V Electric','electric_800':'800 V Electric'}
def crate(s,p):return ('sedan_crate' if p=='combustion' else p+'_crate') if s['id']=='classic_sedan' else s['id']+('' if p=='combustion' else '_'+p)+'_crate'
def save(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n')
lang=json.loads((ASSET/'lang/en_us.json').read_text())
markers=['glass_bottle','tripwire_hook','redstone','chest','oak_door','clock']
for s,marker in zip(styles,markers):
    key=s['id']+'_body_kit';lang['item.sparkmotors.'+key]=s['title']+' Coachwork Kit';lang['body.sparkmotors.'+s['id']]=s['title']
    save(DATA/'recipe'/f'{key}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':['III','IMI','III'],'key':{'I':{'item':'minecraft:iron_ingot'},'M':{'item':'minecraft:'+marker}},'result':{'id':'sparkmotors:'+key,'count':1}})
    for p,title in powertrains.items():
        cid=crate(s,p)
        save(DATA/'recipe'/f'body_conversion_{s["id"]}_{p}.json',{'type':'sparkmotors:body_crating','category':'misc','group':'sparkmotors_body_'+p,'ingredients':[{'tag':'sparkmotors:crates/'+p},{'item':'sparkmotors:'+key}],'result':{'id':'sparkmotors:'+cid,'count':1}})
        if s['id']=='classic_sedan':continue
        lang['item.sparkmotors.'+cid]=s['title']+' — '+title+' Crate'
    for p in ('kit',*powertrains):
        if s['id']=='classic_sedan' and p!='kit':continue
        key=s['id']+'_body_kit' if p=='kit' else crate(s,p)
        img=Image.new('RGBA',(32,32));d=ImageDraw.Draw(img)
        def point(z,y):return (round(16+z*5.35),round(25-y*7))
        outline=[(-s['tail'],.47),(-s['tail'],s['belt']), (s['cabinRear'],s['belt']),(s['roofRear'],s['roof']),(s['roofFront'],s['roof']),(s['cabinFront'],s['belt']),(s['nose'],s['belt']),(s['nose'],.47)]
        d.polygon([point(z,y) for z,y in outline],fill=(32,144,151),outline=(13,42,49))
        if s['id']!='van':
            d.polygon([point(z,y) for z,y in [(s['cabinRear']+.09,s['belt']+.08),(s['roofRear']+.08,s['roof']-.10),(s['roofFront']-.04,s['roof']-.10),(s['cabinFront']-.11,s['belt']+.08)]],fill=(179,217,224))
        else:d.polygon([point(z,y) for z,y in [(-.20,s['belt']+.08),(-.20,s['roof']-.10),(s['roofFront']-.04,s['roof']-.10),(s['cabinFront']-.11,s['belt']+.08)]],fill=(179,217,224))
        for z in (-1.30,1.35):
            x,y=point(z,.34);d.ellipse((x-2,y-2,x+2,y+2),fill=(19,25,30));d.point((x,y),fill=(176,192,197))
        if p=='kit':d.rectangle((2,3,29,8),fill=(41,52,65),outline=(126,172,183));d.line((5,5,25,5),fill=(173,201,210))
        else:
            badge=(239,156,59) if p=='combustion' else (114,207,163) if p in ('hybrid','plug_in_hybrid') else (106,187,242)
            d.rectangle((2,3,29,8),fill=(70,56,41),outline=(184,147,90));d.rectangle((23,3,28,8),fill=badge)
            if p!='combustion':d.line((26,3,24,6,27,6,25,9),fill=(245,248,241),width=1)
        path=ASSET/'textures/item'/f'{key}.png';path.parent.mkdir(parents=True,exist_ok=True);img.save(path,optimize=False)
        save(ASSET/'models/item'/f'{key}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'sparkmotors:item/'+key}})
for p in powertrains:save(DATA/'tags/item/crates'/f'{p}.json',{'replace':False,'values':['sparkmotors:'+crate(s,p) for s in styles]})
lang.update({
 'tooltip.sparkmotors.body_kit':'Right-click your parked, unoccupied vehicle on its service jack.',
 'tooltip.sparkmotors.body_kit_preserves':'Cosmetic only. The old shell is returned; installed parts and charge are preserved.',
 'message.sparkmotors.body_same':'This coachwork is already installed.',
 'message.sparkmotors.body_service':'Switch off, unplug, exit, and raise the parked vehicle on its service jack first.',
 'message.sparkmotors.body_obstructed':'The body is obstructed. Clear a space at least 6 × 6 blocks and 3 blocks high.',
 'message.sparkmotors.body_installed':'Installed %s coachwork; all powertrain and part state preserved.'
})
save(ASSET/'lang/en_us.json',lang)
print('BODY_RESOURCES_PASS: 6 coachwork kits, 25 new crates, 30 state-preserving conversion recipes, 5 type-safe tags')
