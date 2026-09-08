"""Build an evidence gallery from native Minecraft screenshots, with recorded crops.
Requires Pillow. Full screenshots remain beside the cropped preview images.
"""
from pathlib import Path
import json,shutil
from PIL import Image,ImageDraw,ImageFont

repo=Path(__file__).resolve().parent.parent
source=repo/'run/screenshots';dest=repo/'docs/screenshots';dest.mkdir(exist_ok=True)
gallery=repo/'docs/engine-gallery';gallery.mkdir(exist_ok=True)
families=['i4','v6','flat4','rotor1','rotor2','rotor3','rotor4']
titles=['Inline-4','V6','Flat-four','1 rotor','2 rotors','3 rotors','4 rotors']
modes=['natural','turbo','supercharger','large-turbo','twin-turbo','roots','twin-screw'];crop=(38,204,546,642)
font=ImageFont.load_default(size=16)
sheet=Image.new('RGB',(1680,7*245+60),'#101b25');draw=ImageDraw.Draw(sheet)
draw.text((20,15),'AutoPropulsion Age 0.4 / all 49 engine layouts',font=font,fill='#42d2c6')
records=[]
for row,family in enumerate(families):
    for col,mode in enumerate(modes):
        name=f'{family}-{mode}'
        for prefix in ['engine','hood']:shutil.copy2(source/f'{prefix}-{name}.png',dest/f'{prefix}-{name}.png')
        im=Image.open(source/f'engine-{name}.png');assert im.size==(1440,900)
        preview=im.crop(crop);preview.save(gallery/f'{name}.png',optimize=True)
        preview.thumbnail((230,200));x=col*240+(240-preview.width)//2;y=60+row*245
        sheet.paste(preview,(x,y));draw.text((col*240+8,y+207),titles[row]+' / '+mode,font=font,fill='#e7f0f4')
        records.append({'family':family,'induction':mode,'preview':'engine-gallery/'+name+'.png','screen':'screenshots/engine-'+name+'.png','in_world':'screenshots/hood-'+name+'.png'})
    shutil.copy2(source/f'engine-{family}-internals.png',dest/f'engine-{family}-internals.png')
for p in source.glob('alpha-*.png'):shutil.copy2(p,dest/p.name)
for p in source.glob('powertrain-*.png'):shutil.copy2(p,dest/p.name)
for p in source.glob('hardware-ui-*.png'):shutil.copy2(p,dest/p.name)
shutil.copy2(source/'engine-open-hood.png',dest/'engine-open-hood.png')
sheet.save(gallery/'all-configurations.png',optimize=True)
(repo/'docs/engine-gallery.json').write_text(json.dumps({'source':'Native Minecraft 1.21.1 screenshots','preview_crop_xyxy':crop,'configurations':records},indent=2)+'\n')

readme=repo/'README.md';text=readme.read_text(encoding='utf8')
start='<!-- ENGINE-GALLERY-START -->';end='<!-- ENGINE-GALLERY-END -->'
lines=[start,'## Engine configuration gallery','','Native game previews. Click an engine to see it installed under the open hood. [Full workshop instructions](docs/ENGINE-WORKSHOP.md).','']
for group in [modes[:3],modes[3:]]:
    lines.extend(['| Engine | '+' | '.join(m.replace('-',' ').title() for m in group)+' |','|'+'---|'*(len(group)+1)])
    for family,title in zip(families,titles):
        cells=[title]+[f'[![{title} {mode}](docs/engine-gallery/{family}-{mode}.png)](docs/screenshots/hood-{family}-{mode}.png)' for mode in group]
        lines.append('| '+' | '.join(cells)+' |')
    lines.append('')
lines.extend(['',end,''])
block='\n'.join(lines)
if start in text:text=text[:text.index(start)]+block+text[text.index(end)+len(end):]
else:text=text.replace('## Editable model kit',block+'\n## Editable model kit')
readme.write_text(text,encoding='utf8')
print('Created 49 preview crops, full UI/world captures, seven internal views, and configuration overview.')
