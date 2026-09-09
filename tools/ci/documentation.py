"""Validate README links, every model preview, and the canonical packaged logo without rewriting assets."""
import json
import re
import tomllib
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit
from PIL import Image

REPO = Path(__file__).resolve().parents[2]


class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links, self.images = [], []
        self.details = 0
    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        self.links += [attrs[k] for k in ('src', 'href') if k in attrs]
        if tag == 'img':
            assert attrs.get('alt'), 'Images need descriptive alt text'
            self.images.append(attrs['src'])
        if tag == 'details':
            self.details += 1


def validate():
    checked = 0
    for name in ('README.md', 'DEVELOPMENT.md', 'docs/PLAYING.md', 'docs/CI.md', 'docs/BRANDING.md', 'docs/BODY_AND_COLLISION.md', 'docs/CONFIGURED_POWERTRAINS.md', 'docs/BODY_STYLES.md'):
        path = REPO / name
        text = path.read_text(encoding='utf-8')
        parser = Links()
        parser.feed(text)
        for reference in parser.links + re.findall(r'\]\(([^)]+)\)', text):
            if reference.startswith(('https://', 'http://', '#')):
                continue
            target = path.parent / unquote(urlsplit(reference).path)
            assert target.exists(), f'{name}: missing local target {reference}'
            checked += 1
        assert text.count('<details>') == text.count('</details>'), f'{name}: unbalanced galleries'
        if name == 'README.md':
            images = set(parser.images + re.findall(r'!\[[^\]]*\]\(([^)]+)\)', text))
            for prefix, count in (('assets/modular_car_kit/previews/',471),('docs/engine-gallery/',49),('docs/hardware-gallery/',42)):
                actual = {p for p in images if p.startswith(prefix)}
                assert len(actual) == count, f'{prefix}: {len(actual)} of {count} previews'
            assert 'src/main/resources/autopropulsion-age.png' in images
            assert 'docs/body-review/stock-front.png' in images, 'Missing Classic Sedan preview'
            for body in ('hatchback','sports_car','suv','van','touring_sedan'):
                for kind in ('blender','native'):
                    assert f'docs/body-styles/{kind}/{body}-front.png' in images, f'Missing visible {body} {kind} preview'
            for marker in ('ENGINE', 'HARDWARE'):
                assert text.count(f'<!-- {marker}-GALLERY-START -->') == 1
                assert text.count(f'<!-- {marker}-GALLERY-END -->') == 1
    metadata = tomllib.loads((REPO / 'src/main/resources/META-INF/neoforge.mods.toml').read_text())
    mod = next(m for m in metadata['mods'] if m['modId'] == 'sparkmotors')
    logo = REPO / 'src/main/resources' / mod['logoFile']
    with Image.open(logo) as image:
        image.verify()
    with Image.open(logo) as image:
        assert image.width == image.height and 64 <= image.width <= 2048
        assert image.mode == 'RGBA'
        assert image.getextrema()[3] == (0, 255), 'Logo must retain real transparent and opaque pixels'
    report = {'local_links_checked': checked, 'source_previews': 471, 'engine_previews': 49, 'hardware_previews': 42, 'logo': mod['logoFile']}
    output = REPO / 'build/ci/resources/documentation.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
    return report


if __name__ == '__main__':
    validate()
