"""Compress the 512 px gallery PNGs for GitHub. Requires Pillow in normal Python.

Converts the RGB render output to an optimized 256-color PNG palette; preserves
pixel dimensions. The five full-size overview renders are left at full color.
"""
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageStat

kit = Path(__file__).resolve().parent.parent / 'assets' / 'modular_car_kit'
before = after = 0
worst_error = 0
paths = sorted((kit / 'previews').glob('*.png'))
for path in paths:
    before += path.stat().st_size
    with Image.open(path) as source:
        rgb = source.convert('RGB')
        indexed = rgb.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
        mean_error = sum(ImageStat.Stat(ImageChops.difference(rgb, indexed.convert('RGB'))).mean) / 3
        worst_error = max(worst_error, mean_error)
        indexed.save(path, format='PNG', optimize=True)
    after += path.stat().st_size
report = {'images': len(paths), 'dimensions_unchanged': True, 'palette_colors': 256,
          'bytes_before': before, 'bytes_after': after,
          'largest_mean_channel_error_out_of_255': round(worst_error, 4)}
(kit / 'preview_optimization.json').write_text(json.dumps(report, indent=2), encoding='utf8')
print(json.dumps(report, indent=2))
