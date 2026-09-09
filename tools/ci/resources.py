"""Rebuild committed game resources and fail on semantic/pixel/audio drift."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
ROOT = REPO / 'src/main/resources'


def snapshot():
    result = {}
    for path in ROOT.rglob('*'):
        if not path.is_file():
            continue
        if path.suffix == '.json':
            value = json.dumps(json.loads(path.read_text(encoding='utf-8')), sort_keys=True).encode()
        elif path.suffix == '.png':
            # Compare pixels: PNG container/zlib metadata can differ across OSes.
            with Image.open(path) as image:
                value = str(image.size).encode() + image.convert('RGBA').tobytes()
        else:
            value = path.read_bytes()
        result[str(path.relative_to(ROOT))] = hashlib.sha256(value).hexdigest()
    return result


if __name__ == '__main__':
    # Child generators must use the same UTF-8 resource encoding on Windows and Linux.
    os.environ['PYTHONUTF8'] = '1'
    before = snapshot()
    subprocess.run([sys.executable, 'tools/verify_mechanical_audio.py'], cwd=REPO, check=True)
    subprocess.run([sys.executable, 'tools/generate_electric_resources.py'], cwd=REPO, check=True)
    subprocess.run([sys.executable, 'tools/body_styles/generate_resources.py'], cwd=REPO, check=True)
    after = snapshot()
    changed = sorted(k for k in before.keys() | after.keys() if before.get(k) != after.get(k))
    output = REPO / 'build/ci/resources'
    output.mkdir(parents=True, exist_ok=True)
    (output / 'regeneration.json').write_text(json.dumps({'files': len(before), 'changed': changed}, indent=2) + '\n')
    shutil.copyfile(REPO / 'docs/audio-validation.json', output / 'audio-validation.json')
    if changed:
        raise ValueError('Resource regeneration drift:\n' + '\n'.join(changed))
    print(f'PASS: {len(before)} resources preserved across regeneration (JSON semantics, PNG pixels, exact audio/mesh bytes)')
