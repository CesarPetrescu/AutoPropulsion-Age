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
modes=['natural','turbo','supercharger'];crop=(38,204,546,642)
font=ImageFont.load_default(size=22)
sheet=Image.new('RGB',(1200,7*345+60),'#101b25');draw=ImageDraw.Draw(sheet)
draw.text((20,15),'AutoPropulsion Age / all 21 engine layouts',font=font,fill='#42d2c6')
records=[]
for row,family in enumerate(families):
    for col,mode in enumerate(modes):
        name=f'{family}-{mode}'
        for prefix in ['engine','hood']:shutil.copy2(source/f'{prefix}-{name}.png',dest/f'{prefix}-{name}.png')
        im=Image.open(source/f'engine-{name}.png');assert im.size==(1440,900)
        preview=im.crop(crop);preview.save(gallery/f'{name}.png',optimize=True)
        preview.thumbnail((390,300));x=col*400+(400-preview.width)//2;y=60+row*345
        sheet.paste(preview,(x,y));draw.text((col*400+12,y+305),titles[row]+' / '+mode,font=font,fill='#e7f0f4')
        records.append({'family':family,'induction':mode,'preview':'engine-gallery/'+name+'.png','screen':'screenshots/engine-'+name+'.png','in_world':'screenshots/hood-'+name+'.png'})
    shutil.copy2(source/f'engine-{family}-internals.png',dest/f'engine-{family}-internals.png')
for p in source.glob('alpha-*.png'):shutil.copy2(p,dest/p.name)
shutil.copy2(source/'engine-open-hood.png',dest/'engine-open-hood.png')
sheet.save(gallery/'all-configurations.png',optimize=True)
(repo/'docs/engine-gallery.json').write_text(json.dumps({'source':'Native Minecraft 1.21.1 screenshots','preview_crop_xyxy':crop,'configurations':records},indent=2)+'\n')

readme=repo/'README.md';text=readme.read_text(encoding='utf8')
start='<!-- ENGINE-GALLERY-START -->';end='<!-- ENGINE-GALLERY-END -->'
lines=[start,'## Engine configuration gallery','','Native game previews. Click an engine to see it installed under the open hood. [Full workshop instructions](docs/ENGINE-WORKSHOP.md).','','| Engine | Natural | Turbo | Supercharger |','|---|---|---|---|']
for family,title in zip(families,titles):
    cells=[title]+[f'[![{title} {mode}](docs/engine-gallery/{family}-{mode}.png)](docs/screenshots/hood-{family}-{mode}.png)' for mode in modes]
    lines.append('| '+' | '.join(cells)+' |')
lines.extend(['',end,''])
block='\n'.join(lines)
if start in text:text=text[:text.index(start)]+block+text[text.index(end)+len(end):]
else:text=text.replace('## Editable model kit',block+'\n## Editable model kit')
readme.write_text(text,encoding='utf8')
print('Created 21 preview crops, full UI/world captures, seven internal views, and configuration overview.')
