"""Contact sheet and README table for all actual Blender-rendered engine hardware."""
from pathlib import Path
import json,math
from PIL import Image,ImageDraw,ImageFont
repo=Path(__file__).resolve().parent.parent
catalog=json.loads((repo/'docs/powertrain-catalog.json').read_text())['slots']
out=repo/'docs/hardware-gallery'
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',17);titlefont=ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf',24)
height=90+sum(42+math.ceil(len(s['options'])/4)*238 for s in catalog)
sheet=Image.new('RGB',(1000,height),'#101b25');draw=ImageDraw.Draw(sheet)
draw.text((20,18),'AUTOPROPULSION / 42 HARDWARE CHOICES',font=titlefont,fill='#42d2c6')
draw.text((20,52),'Actual Blender models used by the playable 0.3 engine workshop',font=font,fill='#9fb1be')
y=90;lines=['<!-- HARDWARE-GALLERY-START -->','## Every new engine hardware model','','42 selectable items, with distinct geometry. These are Blender renders of the exact game meshes; click any image to inspect it. Parts are framed independently. The Engine tab adapts mounts and ports for all seven families.','']
for slot in catalog:
    draw.text((20,y),slot['title'],font=titlefont,fill='#e7f0f4');y+=42
    lines.extend(['### '+slot['title'],'','<table>'])
    for row in range(math.ceil(len(slot['options'])/4)):
        lines.append('<tr>')
        for col,opt in enumerate(slot['options'][row*4:(row+1)*4]):
            im=Image.open(out/(opt['item']+'.png')).convert('RGBA');assert im.size==(512,512);im.thumbnail((215,200))
            sheet.paste(im,(col*250+(250-im.width)//2,y),im)
            label=opt['label'];words=label.split();wrapped=[];line=''
            for word in words:
                if draw.textlength((line+' '+word).strip(),font=font)>230:wrapped.append(line);line=word
                else:line=(line+' '+word).strip()
            wrapped.append(line)
            for i,line in enumerate(wrapped):draw.text((col*250+10,y+200+i*19),line,font=font,fill='#e7f0f4')
            path='docs/hardware-gallery/'+opt['item']+'.png'
            lines.append(f'<td align="center"><a href="{path}"><img src="{path}" width="180" alt="{label}"></a><br>{label}</td>')
        lines.append('</tr>');y+=238
    lines.extend(['</table>',''])
sheet.save(out/'all-hardware.png',optimize=True)
lines.extend(['<!-- HARDWARE-GALLERY-END -->',''])
readme=repo/'README.md';text=readme.read_text(encoding='utf8');start='<!-- HARDWARE-GALLERY-START -->';end='<!-- HARDWARE-GALLERY-END -->';block='\n'.join(lines)
if start in text:text=text[:text.index(start)]+block+text[text.index(end)+len(end):]
else:text=text.replace('## Editable model kit',block+'\n## Editable model kit')
readme.write_text(text,encoding='utf8')
print('Created 42-image contact sheet and complete README hardware gallery')
