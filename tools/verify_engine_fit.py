"""Recompute all 49 engine envelope checks from the committed runtime mesh."""
import gzip
import json
from pathlib import Path
import struct
import numpy as np

repo = Path(__file__).resolve().parents[1]
families = ['i4_engine', 'v6_engine', 'flat4_engine'] + [f'rotary_{i}rotor' for i in range(1, 5)]
induction_names = ['natural', 'turbo', 'supercharger', 'large-turbo', 'twin-turbo', 'roots', 'twin-screw']
parts = json.loads((repo / 'assets/modular_car_kit/parts_manifest.json').read_text())['parts']
lookup = {p['id']: p for p in parts}
core_parts = set()
for part in parts:
    name = part['id'].split(':')[1]
    if part['state'] == 'workshop' or part['category'] in [25, 26, 27, 29, 30] or name.startswith(('intake_port', 'exhaust_port')):
        continue
    ancestor, names = part, [name]
    while ancestor.get('parent_part'):
        ancestor = lookup[ancestor['parent_part']]
        names.append(ancestor['id'].split(':')[1])
    if any(f in names for f in families):
        core_parts.add(name)

chunks = []
with gzip.open(repo / 'src/main/resources/assets/sparkmotors/models/entity/sedan.mesh.gz', 'rb') as file:
    magic, count = struct.unpack('>ii', file.read(8))
    assert magic == 0x41504132
    for _ in range(count):
        name = file.read(struct.unpack('>H', file.read(2))[0]).decode()
        cat, group, variant, hinge, px, py, pz, angle, family, slot, tier, induction, kind, n = struct.unpack('>iiii4f6i', file.read(56))
        assert n > 0 and n % 3 == 0
        data = file.read(n * 28)
        assert len(data) == n * 28
        vertices = np.frombuffer(data, dtype='>f4').reshape(n, 7)[:, :3].astype(float)
        assert np.isfinite(vertices).all()
        chunks.append(dict(name=name, group=group, family=family, induction=induction, vertices=vertices))
    assert not file.read(1), 'Trailing mesh data'

checks = []
for family in range(7):
    for induction in range(7):
        visible = [c for c in chunks if c['group'] == 0 and c['family'] & (1 << family) and c['induction'] & (1 << induction)]
        vertices = np.concatenate([c['vertices'] for c in visible])
        under_hood = vertices[(-2.105 < -vertices[:, 2]) & (-vertices[:, 2] < -.79) & (np.abs(vertices[:, 0]) < .865)]
        hood = float(np.min(1.028 + (-under_hood[:, 2] + 2.105) / 1.315 * .078 - .011 - under_hood[:, 1]))
        left, right = float(vertices[:, 0].min()), float(vertices[:, 0].max())
        core = np.concatenate([c['vertices'] for c in visible if c['name'] in core_parts])
        front, back = float(core[:, 2].max()), float(core[:, 2].min())
        checks.append(dict(family=families[family], induction=induction_names[induction],
            hood_clearance_m=round(hood, 6), side_clearance_m=round(min(left + .72, .72 - right), 6),
            core_to_radiator_m=round(1.881 - front, 6), core_to_firewall_m=round(back - .80, 6),
            triangles=len(vertices) // 3,
            passed=hood >= .005 and left >= -.72 and right <= .72 and front < 1.86 and back > .81))
report = dict(source='Committed APA2 mesh; all hardware alternatives included', configurations=len(checks),
              passed=sum(c['passed'] for c in checks), failed=sum(not c['passed'] for c in checks), checks=checks)
output = repo / 'build/ci/resources/engine-fit.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, indent=2) + '\n')
assert report['passed'] == 49 and report['failed'] == 0, 'Engine envelope failure; see ' + str(output)
print('PASS: all 49 runtime engine envelopes')
