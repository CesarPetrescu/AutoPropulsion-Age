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

overview = '''# AutoPropulsion Age

A modular vehicle simulation mod targeting **Minecraft 1.21.1**, **NeoForge 21.1.x**, and **Java 21**.

The first Blender asset kit is available below: an assembled sedan, removable mechanical parts, engine and transmission variants, upgrades, and garage equipment. **Current delivery: 3D authoring assets. The playable mod and simulation are not implemented yet.**

The models currently use the working asset namespace `sparkmotors`. No affiliation with Mojang or Microsoft.

## Download and inspect

| File | Contents |
|---|---|
| [Complete model kit ZIP](downloads/modular-car-kit.zip) | Blender source, GLB, all previews, scripts, manifests, fit report and gallery. |
| [Blender project](assets/modular_car_kit/sparkmotors_modular.blend) | Editable stock car, exploded engine, upgrade catalog, workshop, service cutaway and interchange scenes. |
| [Stock car GLB](assets/modular_car_kit/stock_car.glb) | Materials, part hierarchy and six opening-panel animation channels. |
| [Authoring and rebuild guide](assets/modular_car_kit/README.md) | Scene controls, measurements, variant handling and rebuild commands. |
| [Parts manifest](assets/modular_car_kit/parts_manifest.json) / [gallery index](assets/modular_car_kit/gallery_index.json) | All 471 model IDs and their preview paths, bounds and mounting metadata. |
| [Requirements and architecture](docs/modular-car-requirements.md) | Original supplied design and planned game systems. |
| [Early studies](assets/early_studies) | Original square and simple car Blender files, plus the initial car render. |

## Assembled car

![Assembled modular sedan](assets/modular_car_kit/assembled.png)

## Service cutaway

![Open-panel service view showing the cabin and engine bay](assets/modular_car_kit/service.png)

## Exploded engine

![Exploded inline-four engine and removable internals](assets/modular_car_kit/engine_exploded.png)

## Upgrade catalog

![Engine, transmission, wheel and performance upgrade assemblies](assets/modular_car_kit/upgrade_catalog.png)

## Workshop equipment

![Garage equipment and service items](assets/modular_car_kit/workshop.png)

## Fit and development status

- **22 selected geometric checks pass**; a fresh Blender rebuild reproduced 471 part roots and a 68,400-triangle stock export. See the [fit report](assets/modular_car_kit/fit_report.json), [export report](assets/modular_car_kit/export_report.json) and [rebuild record](assets/modular_car_kit/rebuild_check/result.json).
- The sedan is 4.5 m long, 1.9 m wide excluding mirrors, with a 2.65 m wheelbase. Authoring axes: +X right, -Y front, +Z up. Frame 1 closes the panels, frame 50 opens them, and frame 100 closes them.
- Alternate engines and transmissions are fitting models with simplified internals. Shared accessories and routing need configuration-specific integration. Options are mutually exclusive where indicated; the checks do not exhaust every collision or combination.
- Materials and basic projected UVs are included. Texture atlases, livery UVs, LODs, Minecraft model conversion and runtime integration remain to be built. The GLB is an interchange file, not an installable mod.

'''

if (REPO / 'assets' / 'modular_car_kit').resolve() == KIT:
    (REPO / 'README.md').write_text(overview + gallery('assets/modular_car_kit/'), encoding='utf8')
(KIT / 'GALLERY.md').write_text(
    '# Spark Motors model gallery\n\n[Authoring guide](README.md) · [Manifest](parts_manifest.json) · [Gallery index](gallery_index.json)\n\n' + gallery(''), encoding='utf8')
print(f'Generated README and portable gallery for {len(parts)} model entries.')
