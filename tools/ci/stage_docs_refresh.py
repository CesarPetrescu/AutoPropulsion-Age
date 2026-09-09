from pathlib import Path

README_GALLERY = '''### Current Minecraft body gallery — 0.9.1 repaired shells

These are **fresh native Minecraft captures** from repaired-shell source `e382dd61`, the exact body-model revision merged by PR #7. They replace the older pre-repair gallery that showed disconnected rear quarters and cargo panels. [Full repaired-shell CI evidence (204 close-up frames)](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/runs/34353228991).

| Classic Sedan | Hatchback | Sports Coupe |
|---|---|---|
| ![Classic Sedan](docs/body-review/stock-front.png) | ![Hatchback repaired shell](docs/body-styles/native/hatchback-front.png) | ![Sports Coupe repaired shell](docs/body-styles/native/sports_car-front.png) |
| Utility SUV | Panel Van | Touring Sedan |
| ![Utility SUV repaired shell](docs/body-styles/native/suv-front.png) | ![Panel Van repaired shell](docs/body-styles/native/van-front.png) | ![Touring Sedan repaired shell](docs/body-styles/native/touring_sedan-front.png) |

### Rear-shell checks

| Hatchback | Sports Coupe | Utility SUV | Panel Van | Touring Sedan |
|---|---|---|---|---|
| ![Hatchback repaired rear](docs/body-styles/native/hatchback-rear.png) | ![Sports Coupe repaired rear](docs/body-styles/native/sports_car-rear.png) | ![SUV repaired rear](docs/body-styles/native/suv-rear.png) | ![Panel Van repaired rear](docs/body-styles/native/van-rear.png) | ![Touring Sedan repaired rear](docs/body-styles/native/touring_sedan-rear.png) |

'''

BODY_GALLERY = '''## Full body gallery

The images below are **fresh native Minecraft captures from repaired-shell source `e382dd61f9f4c836596c5b1315fdfd69ba53236e`**, verified in [CI run 34353228991](https://github.com/CesarPetrescu/AutoPropulsion-Age/actions/runs/34353228991). They supersede the older `876c0bf6` body gallery. The shell harness also captured open-panel, underbody, interior, seam and night views; those remain in the CI evidence archive rather than bloating the README.

### Classic Sedan

![Classic front](body-review/stock-front.png)

### Hatchback

| Front | Rear |
|---|---|
| ![Hatchback front](body-styles/native/hatchback-front.png) | ![Hatchback rear](body-styles/native/hatchback-rear.png) |

### Sports Coupe

| Front | Rear |
|---|---|
| ![Sports Coupe front](body-styles/native/sports_car-front.png) | ![Sports Coupe rear](body-styles/native/sports_car-rear.png) |

### Utility SUV

| Front | Rear |
|---|---|
| ![Utility SUV front](body-styles/native/suv-front.png) | ![Utility SUV rear](body-styles/native/suv-rear.png) |

### Panel Van

| Front | Rear |
|---|---|
| ![Panel Van front](body-styles/native/van-front.png) | ![Panel Van rear](body-styles/native/van-rear.png) |

### Touring Sedan

| Front | Rear |
|---|---|
| ![Touring Sedan front](body-styles/native/touring_sedan-front.png) | ![Touring Sedan rear](body-styles/native/touring_sedan-rear.png) |
'''

p = Path('README.md')
s = p.read_text()
old = '**Playable alpha · 0.9.0 source.**'
assert s.count(old) == 1, s.count(old)
s = s.replace(old, '**Playable alpha · 0.9.1-alpha source.**')
start = s.index('### Blender previews')
end = s.index('In Creative, select the named vehicle crate from the mod tab.')
s = s[:start] + README_GALLERY + s[end:]
s = s.replace(
    'The installable output is `build/libs/autopropulsion-age-0.6.1-alpha.jar`.',
    'The installable output is `build/libs/autopropulsion-age-0.9.1-alpha.jar`.',
)
p.write_text(s)

p = Path('docs/BODY_STYLES.md')
s = p.read_text()
old = 'Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21. Included in 0.9.0.'
assert s.count(old) == 1, s.count(old)
s = s.replace(old, 'Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21. Included in 0.9.1-alpha.')
start = s.index('## Full body gallery')
p.write_text(s[:start] + BODY_GALLERY)

assert 'Playable alpha · 0.9.1-alpha source' in Path('README.md').read_text()
assert 'docs/body-styles/blender/' not in Path('README.md').read_text()
assert 'Included in 0.9.1-alpha' in Path('docs/BODY_STYLES.md').read_text()
print('DOCS_TEXT_REFRESH_PASS')
