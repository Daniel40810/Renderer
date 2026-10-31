#!/usr/bin/env python3
"""glTF-Gelände oder -Objekt (statisches Netz mit einer Farbtextur) -> .dtree für com.dan.tree / com.dan.grass.

Liest .gltf mit .bin (Dreiecke, POSITION, NORMAL, TEXCOORD_0, Knoten-Matrizen) und schreibt das Netz als
"Stamm" ohne Laub mit der Farbtextur als Rinde. Für Gelände (etwa Fotogrammetrie) wird die Achse mit der
kleinsten Ausdehnung zur Höhe, die Mitte auf (0, 0) gelegt und die mittlere Höhe auf 0.

Aufruf:
  pip install numpy pillow
  python3 gltf2dtree.py scene.gltf ziel.dtree --name "Name" --prefix art [--texture-size 2048] [--terrain]
"""
import argparse
import gzip
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dtree import MAGIC, VERSION, Out, write_mesh, write_tail  # noqa: E402
from fbx2dtree import srgb_to_linear  # noqa: E402

Image.MAX_IMAGE_PIXELS = None
_TYPES = {5126: np.float32, 5125: np.uint32, 5123: np.uint16, 5121: np.uint8}
_COUNT = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4}


def accessor(g, buf, i):
    a = g['accessors'][i]
    v = g['bufferViews'][a['bufferView']]
    dt = np.dtype(_TYPES[a['componentType']])
    n = _COUNT[a['type']]
    off = v.get('byteOffset', 0) + a.get('byteOffset', 0)
    stride = v.get('byteStride', 0)
    if stride and stride != dt.itemsize * n:
        raw = np.frombuffer(buf, dtype=np.uint8, count=stride * a['count'], offset=off).reshape(-1, stride)
        return raw[:, :dt.itemsize * n].copy().view(dt).reshape(a['count'], n)
    return np.frombuffer(buf, dtype=dt, count=a['count'] * n, offset=off).reshape(a['count'], n).copy()


def node_matrix(n):
    if 'matrix' in n:
        return np.array(n['matrix'], dtype=np.float64).reshape(4, 4).T     # glTF: Spalten
    m = np.eye(4)
    if 'scale' in n:
        m = np.diag(list(n['scale']) + [1]) @ m
    if 'rotation' in n:
        x, y, z, w = n['rotation']
        r = np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                      [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                      [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])
        rm = np.eye(4)
        rm[:3, :3] = r
        m = rm @ m
    if 'translation' in n:
        t = np.eye(4)
        t[:3, 3] = n['translation']
        m = t @ m
    return m


def convert(src, dst, name, prefix, tex_size, terrain):
    with open(src) as f:
        g = json.load(f)
    base = os.path.dirname(os.path.abspath(src))
    buf = open(os.path.join(base, g['buffers'][0]['uri']), 'rb').read()
    P, N, T, I = [], [], [], []
    count = 0

    def walk(ni, parent):
        nonlocal count
        n = g['nodes'][ni]
        m = parent @ node_matrix(n)
        if 'mesh' in n:
            for prim in g['meshes'][n['mesh']]['primitives']:
                at = prim['attributes']
                p = accessor(g, buf, at['POSITION']).astype(np.float64)
                nr = accessor(g, buf, at['NORMAL']).astype(np.float64) if 'NORMAL' in at else np.zeros_like(p)
                uv = accessor(g, buf, at['TEXCOORD_0']).astype(np.float64) if 'TEXCOORD_0' in at else np.zeros((len(p), 2))
                idx = accessor(g, buf, prim['indices']).reshape(-1, 3).astype(np.int64)
                P.append((m[:3, :3] @ p.T).T + m[:3, 3])
                N.append((np.linalg.inv(m[:3, :3]).T @ nr.T).T)
                T.append(uv)                 # glTF: v nach unten, wie die Bildzeilen
                I.append(idx + count)
                count += len(p)
        for c in n.get('children', []):
            walk(c, m)

    scene = g['scenes'][g.get('scene', 0)]
    for ni in scene['nodes']:
        walk(ni, np.eye(4))
    p, n, t, tri = np.concatenate(P), np.concatenate(N), np.concatenate(T), np.concatenate(I)
    n /= np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-9)
    if terrain:
        up = int(np.argmin(p.max(0) - p.min(0)))
        if up != 1:                          # kleinste Ausdehnung nach y drehen (rechtshändig)
            perm = {0: [1, 0, 2], 2: [0, 2, 1]}[up]
            p, n = p[:, perm], n[:, perm]
            p[:, 2] *= -1
            n[:, 2] *= -1
            tri = tri[:, [0, 2, 1]]
        if np.mean(n[:, 1]) < 0:             # Oberseite nach oben
            p[:, 1] *= -1
            n[:, 1] *= -1
            p[:, 2] *= -1
            n[:, 2] *= -1
        p[:, 0] -= (p[:, 0].max() + p[:, 0].min()) / 2
        p[:, 2] -= (p[:, 2].max() + p[:, 2].min()) / 2
        p[:, 1] -= np.median(p[:, 1])

    mat = g['materials'][0] if g.get('materials') else {}
    tex_i = mat.get('pbrMetallicRoughness', {}).get('baseColorTexture', {}).get('index')
    bark_name, bark_mean = '', [0.2, 0.18, 0.12]
    if tex_i is not None:
        img = Image.open(os.path.join(base, g['images'][g['textures'][tex_i]['source']]['uri'])).convert('RGB')
        img = img.resize((min(tex_size, img.width), min(tex_size, img.height)), Image.LANCZOS)
        bark_name = f'{prefix}_ground.jpg'
        img.save(os.path.join(os.path.dirname(os.path.abspath(dst)), bark_name), quality=88)
        bark_mean = srgb_to_linear(np.asarray(img).reshape(-1, 3) / 255.0).mean(0)

    o = Out()
    o.i(MAGIC)
    o.i(VERSION)
    o.utf(name)
    o.utf(os.path.basename(src))
    o.f(0.0)
    o.z(True)                                # Bodendecker
    o.fa(bark_mean)
    o.fa([0.06, 0.12, 0.03])
    o.i(1)
    o.ia([-1])
    o.fa([0.0, 0.0, 0.0])
    o.fa([0.1])
    write_mesh(o, p, n, tri, np.zeros((len(p), 2), dtype=np.int64), np.tile([1.0, 0.0], (len(p), 1)))
    o.i(0)
    o.i(0)
    write_tail(o, trunk_uv=t, foliage=None, bark_texture=bark_name, leaf_texture='')
    with open(dst, 'wb') as raw, gzip.GzipFile(filename='', fileobj=raw, mode='wb', compresslevel=9, mtime=0) as fh:
        fh.write(bytes(o.b))
    ext = p.max(0) - p.min(0)
    print(f'{os.path.basename(dst)}: {len(tri)} Dreiecke, {ext[0]:.1f} × {ext[2]:.1f} m, Höhenunterschied {ext[1]:.1f} m, '
          f'Textur {bark_name or "-"}, {os.path.getsize(dst) / 1e6:.2f} MB')


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('src')
    ap.add_argument('dst')
    ap.add_argument('--name', default=None)
    ap.add_argument('--prefix', default=None)
    ap.add_argument('--texture-size', type=int, default=2048)
    ap.add_argument('--terrain', action='store_true', help='Gelände: flachste Achse nach oben, Mitte und Höhe auf 0')
    a = ap.parse_args()
    base = os.path.splitext(os.path.basename(a.dst))[0]
    convert(a.src, a.dst, a.name or base, a.prefix or base, a.texture_size, a.terrain)


if __name__ == '__main__':
    main()
