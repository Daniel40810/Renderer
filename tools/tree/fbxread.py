"""Kleiner Leser für binäres FBX (Version 7.x), nur was usd2dtree/fbx2dtree braucht: Knotenbaum, Meshes,
Modelle mit Lcl-Transformation, Materialien, Texturen, Verbindungen, Achsen und Einheit.
"""
import re
import struct
import zlib

import numpy as np

_ARR = {'f': ('<f4', 4), 'd': ('<f8', 8), 'l': ('<i8', 8), 'i': ('<i4', 4), 'b': ('<i1', 1)}


class Node:
    __slots__ = ('name', 'props', 'children')

    def __init__(self, name, props, children):
        self.name, self.props, self.children = name, props, children

    def find(self, name):
        for c in self.children:
            if c.name == name:
                return c
        return None

    def all(self, name):
        return [c for c in self.children if c.name == name]

    def __repr__(self):
        return f'Node({self.name}, {len(self.props)} props, {len(self.children)} children)'


def _props(b, o, n):
    out = []
    for _ in range(n):
        t = chr(b[o]); o += 1
        if t == 'Y': out.append(struct.unpack_from('<h', b, o)[0]); o += 2
        elif t == 'C': out.append(bool(b[o])); o += 1
        elif t == 'I': out.append(struct.unpack_from('<i', b, o)[0]); o += 4
        elif t == 'F': out.append(struct.unpack_from('<f', b, o)[0]); o += 4
        elif t == 'D': out.append(struct.unpack_from('<d', b, o)[0]); o += 8
        elif t == 'L': out.append(struct.unpack_from('<q', b, o)[0]); o += 8
        elif t in 'SR':
            ln = struct.unpack_from('<I', b, o)[0]; o += 4
            raw = b[o:o + ln]; o += ln
            out.append(raw.decode('utf-8', 'replace') if t == 'S' else raw)
        elif t in _ARR:
            ln, enc, cl = struct.unpack_from('<III', b, o); o += 12
            raw = b[o:o + cl]; o += cl
            if enc == 1:
                raw = zlib.decompress(raw)
            dt, sz = _ARR[t]
            out.append(np.frombuffer(raw, dtype=dt, count=ln).copy())
        else:
            raise ValueError(f'FBX: unbekannter Typ {t!r} bei {o}')
    return out, o


def _node(b, o, wide):
    if wide:
        end, n, plen = struct.unpack_from('<QQQ', b, o); o += 24
    else:
        end, n, plen = struct.unpack_from('<III', b, o); o += 12
    nl = b[o]; o += 1
    if end == 0:
        return None, o
    name = b[o:o + nl].decode('ascii', 'replace'); o += nl
    props, o2 = _props(b, o, n)
    o = o + plen
    children = []
    while o < end:
        c, o = _node(b, o, wide)
        if c is None:
            break
        children.append(c)
    return Node(name, props, children), end


_TOK = re.compile(r'''\s*(?:(;[^\n]*)|("(?:[^"\\]|\\.)*")|(\*\d+)|([-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?)'''
                  r'''|([A-Za-z_][\w|]*)\s*:|([A-Za-z_][\w|]*)|([{},]))''')


def _ascii(text):
    """ASCII-FBX in denselben Knotenbaum wie das binäre Format."""
    toks = []
    for m in _TOK.finditer(text):
        if m.group(1):
            continue
        if m.group(2):
            toks.append(('v', m.group(2)[1:-1]))
        elif m.group(3):
            toks.append(('arr', int(m.group(3)[1:])))
        elif m.group(4):
            t = m.group(4)
            toks.append(('v', float(t) if any(c in t for c in '.eE') else int(t)))
        elif m.group(5):
            toks.append(('name', m.group(5)))
        elif m.group(6):
            toks.append(('v', m.group(6)))
        else:
            toks.append((m.group(7), None))
    pos = 0

    def node(name):
        nonlocal pos
        props, children = [], []
        arr = False
        while pos < len(toks):
            k, v = toks[pos]
            if k == 'v':
                props.append(v); pos += 1
            elif k == 'arr':
                arr = True; pos += 1
            elif k == ',':
                pos += 1
            else:
                break
        if pos < len(toks) and toks[pos][0] == '{':
            pos += 1
            while pos < len(toks) and toks[pos][0] != '}':
                k, v = toks[pos]
                if k == 'name':
                    pos += 1
                    children.append(node(v))
                else:
                    pos += 1
            pos += 1
        if arr:
            vals = children[0].props if children else []
            isint = all(isinstance(x, int) for x in vals)
            props = [np.array(vals, dtype=np.int64 if isint else np.float64)]
            children = []
        return Node(name, props, children)

    top = []
    while pos < len(toks):
        k, v = toks[pos]
        pos += 1
        if k == 'name':
            top.append(node(v))
    return top


def read(path):
    with open(path, 'rb') as f:
        b = f.read()
    if not b.startswith(b'Kaydara FBX Binary'):
        text = b.decode('utf-8', 'replace')
        m = re.search(r'FBXVersion:\s*(\d+)', text)
        return Node('', [int(m.group(1)) if m else 7000], _ascii(text))
    version = struct.unpack_from('<I', b, 23)[0]
    wide = version >= 7500
    o = 27
    top = []
    while o < len(b) - 13:
        n, o = _node(b, o, wide)
        if n is None:
            break
        top.append(n)
    return Node('', [version], top)


def props70(node):
    """Properties70 als dict Name -> Werte."""
    out = {}
    p = node.find('Properties70') if node else None
    if p:
        for c in p.all('P'):
            out[c.props[0]] = c.props[4:]
    return out


def _euler(rx, ry, rz):
    """FBX: Rotation XYZ in Grad, angewendet erst X, dann Y, dann Z (Spaltenvektoren)."""
    rx, ry, rz = np.radians([rx, ry, rz])
    cx, sx, cy, sy, cz, sz = np.cos(rx), np.sin(rx), np.cos(ry), np.sin(ry), np.cos(rz), np.sin(rz)
    X = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Y = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Z = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Z @ Y @ X


def model_matrix(model):
    """4x4 (Spaltenvektoren) aus Lcl Translation/Rotation/Scaling, PreRotation und GeometricTranslation."""
    p = props70(model)
    t = np.array(p.get('Lcl Translation', [0, 0, 0]), dtype=float)
    r = p.get('Lcl Rotation', [0, 0, 0])
    s = np.array(p.get('Lcl Scaling', [1, 1, 1]), dtype=float)
    pre = p.get('PreRotation', [0, 0, 0])
    m = np.eye(4)
    m[:3, :3] = _euler(*pre) @ _euler(*r) @ np.diag(s)
    m[:3, 3] = t
    g = np.eye(4)
    g[:3, 3] = p.get('GeometricTranslation', [0, 0, 0])
    g[:3, :3] = _euler(*p.get('GeometricRotation', [0, 0, 0])) @ np.diag(p.get('GeometricScaling', [1, 1, 1]))
    return m, g


def _layer(geo, name, data_key, index_key, npv, nverts, poly_of_pv, cols):
    el = geo.find(name)
    if el is None:
        return None
    mapping = el.find('MappingInformationType').props[0]
    ref = el.find('ReferenceInformationType').props[0]
    data = el.find(data_key).props[0].reshape(-1, cols)
    if ref in ('IndexToDirect', 'Index') and el.find(index_key) is not None:
        idx = el.find(index_key).props[0]
        data = data[np.where(idx < 0, 0, idx)]
    if mapping == 'ByPolygonVertex':
        return data
    if mapping in ('ByVertice', 'ByVertex'):
        return None, data          # je Punkt
    if mapping == 'ByPolygon':
        return data[poly_of_pv]
    if mapping == 'AllSame':
        return np.repeat(data[:1], npv, axis=0)
    return None


def _name(s):
    return s.split('\x00')[0].split('::')[-1]


def meshes(root):
    """Liste von dicts: name, pts (Punkte), pv (Punkt je Polygon-Ecke), poly (Start je Polygon), nrm, uv (je Ecke),
    mat (Material-Index je Polygon), materials (Namen), textures (Material -> Dateinamen), matrix (4x4 Welt)."""
    objs = root.find('Objects')
    conns = root.find('Connections')
    by_id = {}
    for c in objs.children:
        if c.props and isinstance(c.props[0], int):
            by_id[c.props[0]] = c
    parent = {}
    children = {}
    for c in conns.all('C'):
        kind, a, b = c.props[0], c.props[1], c.props[2]
        parent.setdefault(a, []).append(b)
        children.setdefault(b, []).append((a, c.props[3] if len(c.props) > 3 else None))

    def world(mid):
        m = np.eye(4)
        cur = mid
        while cur in by_id and by_id[cur].name == 'Model':
            lm, _ = model_matrix(by_id[cur])
            m = lm @ m
            ps = [p for p in parent.get(cur, []) if p in by_id and by_id[p].name == 'Model']
            if not ps:
                break
            cur = ps[0]
        return m

    def textures_of(mat_id):
        out = []
        for a, _ in children.get(mat_id, []):
            n = by_id.get(a)
            if n is not None and n.name == 'Texture':
                fn = n.find('RelativeFilename') or n.find('FileName')
                out.append(fn.props[0] if fn else '')
        return out

    result = []
    for gid, g in by_id.items():
        if g.name != 'Geometry' or len(g.props) < 3 or g.props[2] != 'Mesh':
            continue
        if g.find('Vertices') is None or g.find('PolygonVertexIndex') is None:
            continue
        pts = g.find('Vertices').props[0].reshape(-1, 3)
        pvi = g.find('PolygonVertexIndex').props[0]
        ends = pvi < 0
        pv = np.where(ends, ~pvi, pvi)
        starts = np.concatenate([[0], np.where(ends)[0][:-1] + 1])
        poly_of_pv = np.cumsum(np.concatenate([[0], ends[:-1].astype(int)]))
        npv = len(pv)
        nr = _layer(g, 'LayerElementNormal', 'Normals', 'NormalsIndex', npv, len(pts), poly_of_pv, 3)
        if isinstance(nr, tuple):
            nr = nr[1][pv]
        uv = _layer(g, 'LayerElementUV', 'UV', 'UVIndex', npv, len(pts), poly_of_pv, 2)
        if isinstance(uv, tuple):
            uv = uv[1][pv]
        mat = np.zeros(len(starts), dtype=int)
        me = g.find('LayerElementMaterial')
        if me is not None:
            m = me.find('Materials').props[0]
            if me.find('MappingInformationType').props[0] == 'ByPolygon' and len(m) == len(starts):
                mat = m.astype(int)
            else:
                mat[:] = int(m[0]) if len(m) else 0
        for mid in parent.get(gid, []):
            model = by_id.get(mid)
            if model is None or model.name != 'Model':
                continue
            mats = [a for a, _ in children.get(mid, []) if a in by_id and by_id[a].name == 'Material']
            names = [_name(by_id[a].props[1]) for a in mats]
            _, geom = model_matrix(model)
            result.append(dict(name=_name(model.props[1]), pts=pts, pv=pv, poly=starts, nrm=nr, uv=uv, mat=mat,
                               materials=names, textures=[textures_of(a) for a in mats],
                               matrix=world(mid) @ geom))
    return result


def axes(root):
    """(Matrix 3x3 von FBX nach y oben, rechtshändig; Meter je Einheit)."""
    p = props70(root.find('GlobalSettings'))
    up, ups = int(p.get('UpAxis', [1])[0]), int(p.get('UpAxisSign', [1])[0])
    fr, frs = int(p.get('FrontAxis', [2])[0]), int(p.get('FrontAxisSign', [1])[0])
    co, cos = int(p.get('CoordAxis', [0])[0]), int(p.get('CoordAxisSign', [1])[0])
    unit = float(p.get('UnitScaleFactor', [1.0])[0]) / 100.0    # cm je Einheit -> m
    m = np.zeros((3, 3))
    m[0, co] = cos          # x
    m[1, up] = ups          # y oben
    m[2, fr] = frs          # z
    if np.linalg.det(m) < 0:
        m[2] *= -1
    return m, unit
