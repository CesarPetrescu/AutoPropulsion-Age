"""Require the complete native body matrix and every expected captured view."""
import json
import re
import struct
from pathlib import Path

BODIES=('hatchback','sports_car','suv','van','touring_sedan')
POWERTRAINS=('combustion','hybrid','plug_in_hybrid','electric_400','electric_800')

def expected_images():
    images=set()
    for body in BODIES:
        for power in POWERTRAINS:
            views=['front','rear','open','underbody']
            if power=='combustion':views.append('garage')
            if power in ('plug_in_hybrid','electric_400','electric_800'):views.append('charging')
            if power=='electric_400':views.append('driving')
            images.update(f'body-{body}-{power}-{view}.png' for view in views)
    return images

def validate(log,root):
    from checks import require
    root=Path(root);result=(root/'body-smoke-result.txt').read_text();data=json.loads((root/'body-smoke-result.json').read_text())
    require(result.startswith('PASS ') and data['result'].startswith('PASS '),'Missing body PASS')
    require('BODY_NATIVE_RESULT PASS ' in log and 'BODY_NATIVE_RESULT FAIL' not in log,'Failed native body run')
    expected={f'body-{body}-{power}' for body in BODIES for power in POWERTRAINS}
    require(len(data['cases'])==25 and set(data['cases'])==expected,'Incomplete/duplicate body cases')
    require(set(re.findall(r'BODY_NATIVE_CASE_PASS ([\w-]+)',log))==expected,'Missing actual body case markers')
    require(set(re.findall(r'BODY_NATIVE_DRIVE_PASS ([\w-]+)',log))==set(BODIES),'Missing body driving checks')
    require(len(data['screenshots'])==125 and set(data['screenshots'])==expected_images(),'Incomplete/duplicate captured views')
    for name in data['screenshots']:
        image=(root/'screenshots'/name).read_bytes()
        require(len(image)>1000 and image[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',image[16:24])==(1440,900),'Missing/invalid screenshot: '+name)
    return {'body_powertrain_cases':25,'body_driving_cases':5,'native_screenshots':125}
