"""Require complete, current-run close-up evidence; this is not aesthetic approval."""
import json
import re
import struct
from pathlib import Path

BODIES = ('classic_sedan', 'hatchback', 'sports_car', 'suv', 'van', 'touring_sedan')
POWERTRAINS = ('combustion', 'electric_800')
VIEWS = ('closed-front-left', 'closed-front-right', 'closed-rear-left', 'closed-rear-right',
         'left-seams', 'right-seams', 'inside-forward', 'inside-rear', 'inside-roof',
         'floor-joints', 'rear-roof-join', 'open-front', 'open-rear-left', 'open-rear-right',
         'open-interior', 'underbody', 'night-rear')


def expected_images():
    return {f'seams-{body}-{power}-{view}.png' for body in BODIES for power in POWERTRAINS for view in VIEWS}


def validate(log, root):
    from checks import require
    root = Path(root)
    text = (root / 'shell-smoke-result.txt').read_text()
    data = json.loads((root / 'shell-smoke-result.json').read_text())
    require(text.startswith('PASS ') and data['result'].startswith('PASS '), 'Missing shell PASS')
    require('SHELL_NATIVE_RESULT PASS ' in log and 'SHELL_NATIVE_RESULT FAIL' not in log,
            'Failed or missing native shell run')
    expected_cases = {(body, power) for body in BODIES for power in POWERTRAINS}
    require(len(data['cases']) == 12 and
            {(case['body'], case['powertrain']) for case in data['cases']} == expected_cases,
            'Incomplete or duplicate shell cases')
    require(all(case['views'] == len(VIEWS) for case in data['cases']), 'Incomplete case view count')
    expected = expected_images()
    require(len(data['screenshots']) == len(expected) and set(data['screenshots']) == expected,
            'Incomplete or duplicate close-up frames')
    require(set(re.findall(r'SHELL_SCREENSHOT ([\w.-]+)', log)) == expected,
            'Missing native framebuffer capture markers')
    for name in sorted(expected):
        image = (root / 'screenshots' / name).read_bytes()
        require(len(image) > 1000 and image[:8] == b'\x89PNG\r\n\x1a\n' and
                struct.unpack('>II', image[16:24]) == (1440, 900), 'Missing/invalid shell frame: ' + name)
    return {'body_powertrain_cases': 12, 'views_per_case': 17, 'native_screenshots': 204,
            'visual_approval': False}
