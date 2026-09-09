"""Shared, deterministic APA2 mesh IO for body authoring and validation (standard library)."""
from __future__ import annotations
import gzip
import io
import struct
from pathlib import Path

MAGIC = 0x41504132
HEADER = struct.Struct('>iiii4f6i')
VERTEX = struct.Struct('>6fI')
FIELDS = ('category', 'group', 'variant', 'hinge', 'px', 'py', 'pz', 'angle', 'family', 'slot', 'tier', 'induction', 'kind', 'count')


def read(path: Path) -> list[dict]:
    with gzip.open(path, 'rb') as stream:
        data = stream.read(512 * 1024 * 1024 + 1)
    if len(data) > 512 * 1024 * 1024:
        raise ValueError('Mesh exceeds size limit')
    f = io.BytesIO(data)
    if struct.unpack('>I', f.read(4))[0] != MAGIC:
        raise ValueError('Expected APA2 mesh')
    count = struct.unpack('>i', f.read(4))[0]
    if not 0 <= count <= 10000:
        raise ValueError('Invalid chunk count')
    result = []
    for _ in range(count):
        length = struct.unpack('>H', f.read(2))[0]
        name = f.read(length).decode('utf-8')
        c = dict(zip(FIELDS, HEADER.unpack(f.read(HEADER.size))))
        n = c.pop('count')
        if not 0 <= n <= 2000000 or n % 3:
            raise ValueError(f'Invalid vertices: {name}')
        c['name'] = name
        c['vertices'] = list(VERTEX.iter_unpack(f.read(n * VERTEX.size)))
        if len(c['vertices']) != n:
            raise ValueError(f'Truncated mesh: {name}')
        result.append(c)
    if f.read(1):
        raise ValueError('Unexpected trailing mesh bytes')
    return result


def write(path: Path, chunks: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('wb') as raw, gzip.GzipFile(filename='', fileobj=raw, mode='wb', compresslevel=9, mtime=0) as out:
        out.write(struct.pack('>Ii', MAGIC, len(chunks)))
        for c in chunks:
            name = c['name'].encode('utf-8')
            out.write(struct.pack('>H', len(name)))
            out.write(name)
            out.write(HEADER.pack(*(c[k] if k != 'count' else len(c['vertices']) for k in FIELDS)))
            for vertex in c['vertices']:
                out.write(VERTEX.pack(*vertex))


def bounds(chunks: list[dict]) -> list[list[float]]:
    points = [v[:3] for c in chunks for v in c['vertices']]
    if not points:
        return [[0., 0., 0.], [0., 0., 0.]]
    return [[min(p[i] for p in points) for i in range(3)], [max(p[i] for p in points) for i in range(3)]]


if __name__ == '__main__':
    import json
    root = Path(__file__).resolve().parents[2]
    chunks = read(root / 'src/main/resources/assets/sparkmotors/models/entity/sedan.mesh.gz')
    for category in sorted({c['category'] for c in chunks}):
        selected = [c for c in chunks if c['category'] == category]
        print(json.dumps({'category': category, 'groups': sorted({c['group'] for c in selected}), 'bounds': bounds(selected), 'names': sorted({c['name'] for c in selected})}))
