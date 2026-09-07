"""Build the GitHub README and the portable kit gallery from the rendered index."""
import html
import json
import re
from collections import Counter
from pathlib import Path

KIT = Path(__file__).resolve().parent.parent
REPO = KIT.parent.parent
data = json.loads((KIT / 'gallery_index.json').read_text(encoding='utf8'))
parts = data['parts']
categories = {
    int(number): title for number, title in re.findall(
        r'^\| (\d+) \| ([^|]+?) \| \d+ \|$',
        (KIT / 'README.md').read_text(encoding='utf8'), re.MULTILINE)
}
assert len(categories) == 43
assert len(parts) == 471
assert all((KIT / p['preview']).is_file() for p in parts)

def gallery(prefix):
    lines = [
        '## Every model', '',
        '**471 named parts and assemblies across 43 categories.** Expand a category to see every model, then click an image for its full 512 px preview. Repeated cylinder and wheel components are included; these are not 471 unique designs.', '',
        'Previews are scaled independently. Assembly previews include their same-state child parts; glass is opaque in these shape previews. The manifest and gallery index retain exact IDs, mounting positions, hierarchy and dimensions.', '',
        '| Category | Models | Category | Models |',
        '|---|---:|---|---:|',
    ]
    counts = Counter(p['category'] for p in parts)
    nums = list(categories)
    for i in range(0, len(nums), 2):
        cells = []
        for number in nums[i:i+2]:
            cells.extend([f'[{number:02d} · {categories[number]}](#category-{number:02d})', str(counts[number])])
        if len(cells) == 2:
            cells += ['', '']
        lines.append('| ' + ' | '.join(cells) + ' |')
    lines.append('')
    for category, title in categories.items():
        group = [p for p in parts if p['category'] == category]
        lines += [f'<a id="category-{category:02d}"></a>', '<details>',
                  f'<summary><strong>{category:02d} · {html.escape(title)} — {len(group)} models</strong></summary>', '',
                  '<table>']
        for start in range(0, len(group), 3):
            lines.append('<tr>')
            for part in group[start:start+3]:
                source = prefix + part['preview']
                identifier = html.escape(part['id'])
                name = html.escape(part['id'].split(':', 1)[1].replace('_', ' ').title())
                components = part['preview_mesh_components_including_children']
                lines.append(f'<td align="center" width="33%"><a href="{source}"><img src="{source}" width="220" alt="{identifier}" title="{identifier}"></a><br><strong>{name}</strong><br><sub>{part["state"]} · {components} mesh components</sub></td>')
            lines.append('</tr>')
        lines += ['</table>', '', '</details>', '']
    return '\n'.join(lines)

if (REPO / 'assets' / 'modular_car_kit').resolve() == KIT:
    readme = REPO / 'README.md'
    overview = readme.read_text(encoding='utf8').split('## Every model', 1)[0]
    readme.write_text(overview + gallery('assets/modular_car_kit/'), encoding='utf8')
(KIT / 'GALLERY.md').write_text(
    '# Spark Motors model gallery\n\n[Authoring guide](README.md) · [Manifest](parts_manifest.json) · [Gallery index](gallery_index.json)\n\n' + gallery(''), encoding='utf8')
print(f'Generated README and portable gallery for {len(parts)} model entries.')
