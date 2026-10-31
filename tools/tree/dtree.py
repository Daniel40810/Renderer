"""Gemeinsame Bausteine für das .dtree-Format (com.dan.tree.TreeAsset): Schreiber und kleine Netz-Helfer.

Version 2 = Version 1 + Anhang: UVs des Stamms, Laubkarten mit UVs (FBX-Bäume), Namen der Texturen.
"""
import struct

import numpy as np

MAGIC = 0x44545245  # 'DTRE'
VERSION = 2


# ---------------------------------------------------------------- Netze

def triangulate(counts, idx):
    """Polygone als Faecher in Dreiecke; liefert (Dreiecke, Flaeche je Dreieck)."""
    tris, owner = [], []
    o = 0
    for f, n in enumerate(counts):
        for k in range(1, n - 1):
            tris.append((idx[o], idx[o + k], idx[o + k + 1]))
            owner.append(f)
        o += n
    return np.array(tris, dtype=np.int64), np.array(owner, dtype=np.int64)


def vertex_normals(pts, tris):
    n = np.zeros_like(pts)
    fn = np.cross(pts[tris[:, 1]] - pts[tris[:, 0]], pts[tris[:, 2]] - pts[tris[:, 0]])
    for k in range(3):
        np.add.at(n, tris[:, k], fn)
    ln = np.linalg.norm(n, axis=1, keepdims=True)
    ln[ln == 0] = 1
    return n / ln


def compact(pts, tris):
    """Nur benutzte Punkte behalten."""
    used = np.unique(tris)
    remap = -np.ones(len(pts), dtype=np.int64)
    remap[used] = np.arange(len(used))
    return pts[used], remap[tris], used



# ---------------------------------------------------------------- Schreiben

class Out:
    def __init__(self):
        self.b = bytearray()

    def i(self, v): self.b += struct.pack('>i', int(v))
    def h(self, v): self.b += struct.pack('>h', int(v))
    def f(self, v): self.b += struct.pack('>f', float(v))
    def z(self, v): self.b += struct.pack('>?', bool(v))

    def utf(self, s):
        e = s.encode('utf-8')
        self.b += struct.pack('>H', len(e)) + e

    def fa(self, a): self.b += np.asarray(a, dtype='>f4').tobytes()
    def ia(self, a): self.b += np.asarray(a, dtype='>i4').tobytes()
    def ha(self, a): self.b += np.asarray(a, dtype='>i2').tobytes()
    def ba(self, a): self.b += np.asarray(a, dtype='i1').tobytes()
    def ua(self, a): self.b += np.asarray(a, dtype='u1').tobytes()


def write_mesh(o, pts, nrm, tris, joints, weights):
    """Ein Netz: Punkte (float), Normalen (byte), zwei Gelenke und Gewichte je Punkt, Dreiecke."""
    o.i(len(pts))
    o.fa(pts.reshape(-1))
    o.ba(np.clip(np.round(nrm * 127), -127, 127).reshape(-1))
    o.ha(joints.reshape(-1))
    o.ua(np.clip(np.round(weights * 255), 0, 255).reshape(-1))
    o.i(len(tris))
    o.ia(tris.reshape(-1))



def write_tail(o, trunk_uv=None, foliage=None, bark_texture='', leaf_texture=''):
    """Anhang der Version 2. foliage: (Punkte, Normalen, UVs, Dreiecke) oder None."""
    if trunk_uv is None:
        o.i(0)
    else:
        o.i(1)
        o.fa(np.asarray(trunk_uv).reshape(-1))
    if foliage is None:
        o.i(0)
    else:
        pts, nrm, uv, tris = foliage
        o.i(len(pts))
        o.fa(pts.reshape(-1))
        o.ba(np.clip(np.round(nrm * 127), -127, 127).reshape(-1))
        o.fa(uv.reshape(-1))
        o.i(len(tris))
        o.ia(tris.reshape(-1))
    o.utf(bark_texture)
    o.utf(leaf_texture)
