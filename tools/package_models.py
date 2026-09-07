"""Package the model kit, then verify local README links and preview coverage."""
import hashlib
import json
import re
import zipfile
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote

repo = Path(__file__).resolve().parent.parent
kit = repo / 'assets' / 'modular_car_kit'
downloads = repo / 'downloads'
downloads.mkdir(exist_ok=True)
archive = downloads / 'modular-car-kit.zip'

files = [p for p in kit.iterdir() if p.is_file() and p.suffix in {'.blend', '.glb', '.md', '.json', '.png'}
         and p.name != 'gallery_progress.json']
files += list((kit / 'scripts').glob('*.py'))
files += list((kit / 'previews').glob('*.png'))
files += [kit / 'rebuild_check' / 'result.json']
with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    for path in sorted(files):
        z.write(path, path.relative_to(kit).as_posix())
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert len([n for n in z.namelist() if n.startswith('previews/')]) == 471
digest = hashlib.sha256(archive.read_bytes()).hexdigest()
(downloads / 'SHA256SUMS.txt').write_text(f'{digest}  {archive.name}\n', encoding='utf8')

class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
        self.images = []
        self.details = 0
    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        self.links += [attrs[k] for k in ['src', 'href'] if k in attrs]
        if tag == 'img':
            self.images.append(attrs['src'])
        if tag == 'details':
            self.details += 1

checked = 0
for doc in [repo / 'README.md', kit / 'GALLERY.md', kit / 'README.md']:
    text = doc.read_text(encoding='utf8')
    parser = Links()
    parser.feed(text)
    references = parser.links + re.findall(r'\]\(([^)]+)\)', text)
    for reference in references:
        if reference.startswith(('https://', 'http://', '#')):
            continue
        path = doc.parent / unquote(reference.split('#', 1)[0])
        assert path.exists(), (str(doc), reference)
        checked += 1
    if doc.name in {'README.md', 'GALLERY.md'} and doc != kit / 'README.md':
        assert len(parser.images) == 471
        assert parser.details == 43

manifest = json.loads((kit / 'parts_manifest.json').read_text(encoding='utf8'))['parts']
gallery = json.loads((kit / 'gallery_index.json').read_text(encoding='utf8'))['parts']
assert {p['id'] for p in manifest} == {p['id'] for p in gallery}
assert all((kit / p['preview']).is_file() for p in gallery)
print(json.dumps({'archive': archive.relative_to(repo).as_posix(),
                  'archive_files': len(files), 'bytes': archive.stat().st_size,
                  'sha256': digest, 'local_links_checked': checked,
                  'preview_models': len(gallery)}, indent=2))
