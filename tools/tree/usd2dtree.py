#!/usr/bin/env python3
"""USD-Baum aus Unreal (Procedural Vegetation Editor, Nanite Assembly) -> .dtree fuer com.dan.tree.

Liest eine Variante (z. B. Tree_Black_Alder_01_C.usd) samt referenzierter Ast-Bibliothek
(Tree_Black_Alder_Branches.usd im selben Ordner) und der *_DynamicWind.json und schreibt
eine kompakte Datei, die com.dan.tree.TreeAsset liest.

Inhalt:
  - Skelett (Eltern, Lage der Gelenke)
  - Radius je Gelenk (fuer die groeberen Stufen baut com.dan.tree Roehren aus dem Skelett)
  - Stamm und Aeste im Original, je Punkt Lage, Normale, zwei Gelenke
  - Zweig-Prototypen: Holz als Knotengraph (Lage, Radius, Eltern), Blaetter als Sechseck
    (Stiel, Mitte, Normale, Rand)
  - Zweig-Instanzen: Matrix, Prototyp, zwei Gelenke

Aufruf:
  pip install usd-core scipy numpy
  python3 usd2dtree.py <variante.usd> <ziel.dtree> [--name Name]

Format: GZIP, darin Big Endian (java.io.DataInputStream), siehe TreeAsset.read.
"""
import argparse
import gzip
import json
import os
import sys

import numpy as np
from pxr import Usd, UsdGeom, UsdShade, UsdSkel
from scipy.spatial import cKDTree

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dtree import MAGIC, VERSION, Out, compact, triangulate, vertex_normals, write_mesh, write_tail

RING = 6
TWIG_CELL = 0.03      # Rastermass in m fuer den Knotengraphen der Zweige
MIN_RADIUS = 0.0015


# ---------------------------------------------------------------- Netze

def seg_dist(p, a, b):
    """Abstand der Punkte p von der Strecke a-b."""
    ab = b - a
    l2 = float(np.dot(ab, ab))
    if l2 < 1e-12:
        return np.linalg.norm(p - a, axis=1)
    t = np.clip((p - a) @ ab / l2, 0, 1)
    return np.linalg.norm(p - (a + t[:, None] * ab), axis=1)


def joint_radii(pts, ji, jw, parents, jpos):
    """Radius je Gelenk: Median des Abstands der Punkte, die vor allem an ihm haengen, zur Strecke Eltern-Gelenk."""
    dom = ji[np.arange(len(ji)), np.argmax(jw, 1)]
    r = np.zeros(len(jpos))
    for j in range(len(jpos)):
        sel = pts[dom == j]
        if len(sel) == 0:
            continue
        if parents[j] >= 0:
            r[j] = np.median(seg_dist(sel, jpos[parents[j]], jpos[j]))
        else:
            # Wurzel: waagerechter Abstand zur Senkrechten durch das Gelenk
            r[j] = np.median(np.hypot(sel[:, 0] - jpos[j, 0], sel[:, 2] - jpos[j, 2]))
    # Luecken von den Eltern erben (an der Wurzel vom dicksten Kind), Kinder nie dicker als die Eltern
    if r[0] <= 0 and len(r) > 1:
        r[0] = r[1:][parents[1:] == 0].max(initial=0.1) * 1.1
    for j in range(len(r)):
        if r[j] <= 0:
            r[j] = r[parents[j]] * 0.8 if parents[j] >= 0 else 0.1
    for j in range(len(r)):
        if parents[j] >= 0:
            r[j] = min(r[j], r[parents[j]] * 1.05)
    return np.maximum(r, MIN_RADIUS)


def wood_graph(wp, wt, cell):
    """Holz eines Zweigs -> Baum aus Knoten (Raster-Schwerpunkte), Wurzel nahe (0,0,0)."""
    q = np.floor(wp / cell).astype(np.int64)
    _, cid = np.unique(q, axis=0, return_inverse=True)
    cid = cid.reshape(-1)
    n = cid.max() + 1
    cnt = np.bincount(cid, minlength=n).astype(np.float64)
    cen = np.stack([np.bincount(cid, wp[:, k], n) for k in range(3)], 1) / cnt[:, None]
    e = np.concatenate([wt[:, [0, 1]], wt[:, [1, 2]], wt[:, [2, 0]]])
    e = cid[e]
    e = e[e[:, 0] != e[:, 1]]
    e = np.unique(np.sort(e, 1), axis=0)
    adj = [[] for _ in range(n)]
    for a, b in e:
        adj[a].append(b)
        adj[b].append(a)
    parent = -np.ones(n, dtype=np.int64)
    seen = np.zeros(n, dtype=bool)
    root = int(np.argmin(np.linalg.norm(cen, axis=1)))
    starts = [(root, -1)]
    while True:
        for s0, p0 in starts:
            seen[s0] = True
            parent[s0] = p0
            queue = [s0]
            while queue:
                nxt = []
                for a in queue:
                    for b in adj[a]:
                        if not seen[b]:
                            seen[b] = True
                            parent[b] = a
                            nxt.append(b)
                queue = nxt
        if seen.all():
            break
        # abgetrennte Stuecke (Knospen, Zapfen) an den naechsten Knoten haengen
        rest = np.where(~seen)[0]
        tree = cKDTree(cen[seen])
        idx_seen = np.where(seen)[0]
        d, k = tree.query(cen[rest])
        i = int(np.argmin(d))
        starts = [(int(rest[i]), int(idx_seen[k[i]]))]
    # Reihenfolge: Eltern vor Kindern
    order = []
    stack = [root]
    kids = [[] for _ in range(n)]
    for c in range(n):
        if parent[c] >= 0:
            kids[parent[c]].append(c)
    while stack:
        a = stack.pop()
        order.append(a)
        stack.extend(kids[a])
    remap = -np.ones(n, dtype=np.int64)
    remap[order] = np.arange(n)
    pos = cen[order]
    par = np.array([remap[parent[c]] if parent[c] >= 0 else -1 for c in order])
    # Radius: mittlerer Abstand der Punkte zur Achse durch den Knoten
    rad = np.zeros(n)
    for new, c in enumerate(order):
        sel = wp[cid == c]
        d = pos[new] - pos[par[new]] if par[new] >= 0 else np.array([0, 1.0, 0])
        ln = np.linalg.norm(d)
        d = d / ln if ln > 1e-9 else np.array([0, 1.0, 0])
        off = sel - pos[new]
        perp = off - np.outer(off @ d, d)
        rad[new] = np.mean(np.linalg.norm(perp, axis=1)) if len(sel) else MIN_RADIUS
    rad = np.clip(rad, MIN_RADIUS, cell * 0.5)
    return pos, rad, par


def one_mesh(mprim):
    """Ein Netz: Punkte, Normalen je Punkt, Dreiecke, Blatt je Dreieck (Teilmenge „TwoSided“)."""
    mm, tp, tc, tix = mesh_data(mprim)
    ttris, towner = triangulate(tc, tix)
    leaf_faces = set()
    for sub in UsdGeom.Subset.GetAllGeomSubsets(mm):
        if 'TwoSided' in sub.GetPrim().GetName():
            leaf_faces.update(sub.GetIndicesAttr().Get())
    is_leaf = np.array([f in leaf_faces for f in towner], dtype=bool)
    nrm_attr = mm.GetNormalsAttr().Get()
    if nrm_attr is not None and mm.GetNormalsInterpolation() == 'vertex' and len(nrm_attr) == len(tp):
        tn = np.array(nrm_attr, dtype=np.float64)
    else:
        tn = vertex_normals(tp, ttris)
    return tp, tn, ttris, is_leaf


def proto_geometry(stage, proto, mpu):
    """
    Ein Zweig-Prototyp. Schwarzerle und Greasewood: ein Netz (Holz, Blätter als „TwoSided“). Seit Timmendorf Phase 19
    auch zweistufig wie die Rotbuche: das Holz als eigenes Netz neben einem inneren PointInstancer, der Blätter und
    Knospen setzt (die Netze darunter werden mit ihren Matrizen eingesammelt und als Blatt gezählt, wo sie „TwoSided“ sind).
    """
    parts = []
    inner = [p for p in Usd.PrimRange(proto) if p.GetTypeName() == 'PointInstancer']
    inside = set()
    for ip in inner:
        for q in Usd.PrimRange(ip):
            inside.add(str(q.GetPath()))
    own = [p for p in Usd.PrimRange(proto) if p.GetTypeName() == 'Mesh' and str(p.GetPath()) not in inside]
    for m in own:
        parts.append(one_mesh(m))
    for ip in inner:
        pi = UsdGeom.PointInstancer(ip)
        targets = pi.GetPrototypesRel().GetTargets()
        mats = pi.ComputeInstanceTransformsAtTime(Usd.TimeCode.Default(), Usd.TimeCode.Default())
        pidx = np.array(pi.GetProtoIndicesAttr().Get())
        geo = []
        for t in targets:
            ms = [p for p in Usd.PrimRange(stage.GetPrimAtPath(t)) if p.GetTypeName() == 'Mesh']
            geo.append([one_mesh(m) for m in ms])
        for k, mat in enumerate(mats):
            M = np.array([[mat[r][c] for c in range(4)] for r in range(4)])
            R = M[:3, :3]
            for (tp, tn, tt, lf) in geo[int(pidx[k])]:
                p2 = tp @ R + M[3, :3]
                n2 = tn @ np.linalg.inv(R).T
                n2 /= np.maximum(np.linalg.norm(n2, axis=1, keepdims=True), 1e-12)
                parts.append((p2, n2, tt, lf))
    if not parts:
        raise ValueError('Prototyp ohne Netz: ' + str(proto.GetPath()))
    P, N, T, L, off = [], [], [], [], 0
    for (tp, tn, tt, lf) in parts:
        P.append(tp); N.append(tn); T.append(tt + off); L.append(lf); off += len(tp)
    return np.concatenate(P) * mpu, np.concatenate(N), np.concatenate(T), np.concatenate(L)


def mesh_data(prim):
    m = UsdGeom.Mesh(prim)
    pts = np.array(m.GetPointsAttr().Get(), dtype=np.float64)
    counts = np.array(m.GetFaceVertexCountsAttr().Get())
    idx = np.array(m.GetFaceVertexIndicesAttr().Get())
    return m, pts, counts, idx


# ---------------------------------------------------------------- Blaetter

def hull2d(p):
    """Konvexe Huelle (Andrew), Indizes gegen den Uhrzeigersinn."""
    order = np.lexsort((p[:, 1], p[:, 0]))

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lower, upper = [], []
    for i in order:
        while len(lower) >= 2 and cross(p[lower[-2]], p[lower[-1]], p[i]) <= 0:
            lower.pop()
        lower.append(i)
    for i in order[::-1]:
        while len(upper) >= 2 and cross(p[upper[-2]], p[upper[-1]], p[i]) <= 0:
            upper.pop()
        upper.append(i)
    return lower[:-1] + upper[:-1]


def leaf_ring(lp, ln, base):
    """Ein Blatt (Punkte, Normalen, Stielansatz) -> Mitte, Normale, RING Randpunkte in 3D."""
    c = lp.mean(0)
    w, v = np.linalg.eigh(np.cov((lp - c).T))
    n = v[:, 0]
    if np.dot(n, ln.sum(0)) < 0:
        n = -n
    tip = lp[np.argmax(np.linalg.norm(lp - base, axis=1))]
    u = tip - base
    u -= n * np.dot(u, n)
    if np.linalg.norm(u) < 1e-9:
        u = v[:, 2]
    u /= np.linalg.norm(u)
    vv = np.cross(n, u)
    q = np.stack([(lp - c) @ u, (lp - c) @ vv], 1)
    h = hull2d(q)
    hq, hp = q[h], lp[h]
    ring = []
    for k in range(RING):
        a = 2 * np.pi * k / RING
        d = np.array([np.cos(a), np.sin(a)])
        best, bt = None, -1
        for i in range(len(h)):
            p0, p1 = hq[i], hq[(i + 1) % len(h)]
            e = p1 - p0
            den = d[0] * (-e[1]) - d[1] * (-e[0])
            if abs(den) < 1e-12:
                continue
            t = (p0[0] * (-e[1]) - p0[1] * (-e[0])) / den   # Abstand entlang d
            s = (d[0] * p0[1] - d[1] * p0[0]) / den          # Anteil entlang e
            if t > 0 and -1e-6 <= s <= 1 + 1e-6 and t > bt:
                bt, best = t, hp[i] + (hp[(i + 1) % len(h)] - hp[i]) * np.clip(s, 0, 1)
        if best is None:
            best = c + (u * np.cos(a) + vv * np.sin(a)) * 0.01
        ring.append(best)
    return c, n, np.array(ring)


def leaves_of(pts, nrm, tris_leaf, wood_pts):
    """Blattflaechen in Zusammenhangskomponenten zerlegen, je Blatt Stiel, Mitte, Normale, Rand."""
    par = np.arange(len(pts))

    def find(x):
        r = x
        while par[r] != r:
            r = par[r]
        while par[x] != r:
            par[x], x = r, par[x]
        return r

    for a, b, c in tris_leaf:
        ra, rb, rc = find(a), find(b), find(c)
        par[rb] = ra
        par[find(rc)] = ra
    verts = np.unique(tris_leaf)
    roots = np.array([find(v) for v in verts])
    tree = cKDTree(wood_pts) if len(wood_pts) else None
    out = []
    for r in np.unique(roots):
        vs = verts[roots == r]
        if len(vs) < 3:
            continue
        lp, ln = pts[vs], nrm[vs]
        if tree is not None:
            dist, _ = tree.query(lp)
            base = lp[np.argmin(dist)]
        else:
            base = lp[np.argmin(lp[:, 1])]
        c, n, ring = leaf_ring(lp, ln, base)
        out.append((base, c, n, ring))
    return out


# ---------------------------------------------------------------- Schreiben

def diffuse(stage, name_part, default):
    for p in stage.Traverse():
        if p.GetTypeName() == 'Shader' and name_part in p.GetName():
            v = UsdShade.Shader(p).GetInput('diffuseColor')
            if v and v.Get() is not None:
                return list(v.Get())
    return default


def convert(src, dst, name):
    stage = Usd.Stage.Open(src)
    mpu = UsdGeom.GetStageMetersPerUnit(stage) or 1.0
    if UsdGeom.GetStageUpAxis(stage) != 'Y':
        sys.exit('nur Y oben wird unterstuetzt')

    skel_prims = [p for p in stage.Traverse() if p.GetTypeName() == 'Skeleton']
    inst_prim = [p for p in stage.Traverse() if p.GetTypeName() == 'PointInstancer'][0]
    trunk_prim = [p for p in stage.Traverse() if p.GetTypeName() == 'Mesh'
                  and not p.GetPath().HasPrefix(inst_prim.GetPath())][0]
    skel_prim = [p for p in skel_prims if not p.GetPath().HasPrefix(inst_prim.GetPath())][0]

    # Skelett
    skel = UsdSkel.Skeleton(skel_prim)
    jnames = list(skel.GetJointsAttr().Get())
    parents = np.array(UsdSkel.Topology(jnames).GetParentIndices())
    bind = skel.GetBindTransformsAttr().Get()
    jpos = np.array([[m[3][0], m[3][1], m[3][2]] for m in bind]) * mpu
    # Platzhalter: Gelenke (nicht die Wurzel) im Ursprung ohne Lage, etwa beim Greasewood je Zweig eines;
    # sie bekommen die Lage ihres Elternteils, Zweige an ihnen werden unten an das nächste echte Gelenk gebunden
    phantom = np.linalg.norm(jpos, axis=1) < 1e-5
    phantom[0] = False
    for j in range(len(jpos)):
        if phantom[j] and parents[j] >= 0:
            jpos[j] = jpos[parents[j]]
    by_leaf = {n.split('/')[-1]: k for k, n in enumerate(jnames)}
    by_path = {n: k for k, n in enumerate(jnames)}

    def jidx(n):
        return by_path.get(n, by_leaf.get(str(n).split('/')[-1], 0))

    # Stamm und Aeste
    m, pts, counts, idx = mesh_data(trunk_prim)
    pts *= mpu
    tris, owner = triangulate(counts, idx)
    bapi = UsdSkel.BindingAPI(trunk_prim)
    mj = [jidx(n) for n in bapi.GetJointsAttr().Get()] if bapi.GetJointsAttr().Get() else list(range(len(jnames)))
    ji = np.array(bapi.GetJointIndicesPrimvar().Get()).reshape(len(pts), -1)
    jw = np.array(bapi.GetJointWeightsPrimvar().Get()).reshape(len(pts), -1)
    ji = np.array(mj)[ji]
    order = np.argsort(-jw, axis=1)[:, :2]
    ji = np.take_along_axis(ji, order, 1)
    jw = np.take_along_axis(jw, order, 1)
    if ji.shape[1] == 1:
        ji = np.concatenate([ji, ji], 1)
        jw = np.concatenate([jw, np.zeros_like(jw)], 1)
    s = jw.sum(1, keepdims=True)
    s[s == 0] = 1
    jw = jw / s

    radii = joint_radii(pts, ji, jw, parents, jpos)
    trunk_nrm = vertex_normals(pts, tris)

    # Zweig-Prototypen
    pi = UsdGeom.PointInstancer(inst_prim)
    proto_paths = pi.GetPrototypesRel().GetTargets()
    protos = []
    for pp in proto_paths:
        tp, tn, ttris, is_leaf = proto_geometry(stage, stage.GetPrimAtPath(pp), mpu)
        wood = ttris[~is_leaf]
        wp, wt, _ = compact(tp, wood)
        graph = wood_graph(wp, wt, TWIG_CELL)
        leaves = leaves_of(tp, tn, ttris[is_leaf], wp)
        length = float(np.max(np.linalg.norm(tp, axis=1)))
        protos.append((pp.name, length, graph, leaves))

    # Instanzen
    mats = pi.ComputeInstanceTransformsAtTime(Usd.TimeCode.Default(), Usd.TimeCode.Default())
    pidx = np.array(pi.GetProtoIndicesAttr().Get())
    ipv = UsdGeom.PrimvarsAPI(inst_prim)
    bj = ipv.GetPrimvar('unreal:naniteAssembly:bindJoints')
    bw = ipv.GetPrimvar('unreal:naniteAssembly:bindJointWeights')
    if bj and bj.Get() is not None:
        es = bj.GetElementSize()
        bjv = np.array([jidx(n) for n in bj.Get()]).reshape(-1, es)
        bwv = np.array(bw.Get(), dtype=np.float64).reshape(-1, es)
    else:
        tree = cKDTree(jpos)
        _, near = tree.query(np.array([[mm_[3][0], mm_[3][1], mm_[3][2]] for mm_ in mats]) * mpu)
        bjv = np.stack([near, near], 1)
        bwv = np.stack([np.ones(len(near)), np.zeros(len(near))], 1)
    if bjv.shape[1] == 1:
        bjv = np.concatenate([bjv, bjv], 1)
        bwv = np.concatenate([bwv, np.zeros_like(bwv)], 1)
    if phantom.any():
        real = np.where(~phantom)[0]
        tree = cKDTree(jpos[real])
        ipos = np.array([[mm_[3][0], mm_[3][1], mm_[3][2]] for mm_ in mats]) * mpu
        bad = phantom[bjv]
        if bad.any():
            _, near = tree.query(ipos[bad.any(1)])
            rows = np.where(bad.any(1))[0]
            for r, nj in zip(rows, real[near]):
                bjv[r][bad[r]] = nj

    # Wind aus Unreal
    wind = {'GustAttenuation': 0.0, 'bIsGroundCover': False}
    wj = os.path.splitext(src)[0] + '_DynamicWind.json'
    if os.path.exists(wj):
        with open(wj) as fh:
            wind.update(json.load(fh))

    # Schreiben
    o = Out()
    o.i(MAGIC)
    o.i(VERSION)
    o.utf(name)
    o.utf(os.path.basename(src))
    o.f(wind.get('GustAttenuation', 0.0))
    o.z(wind.get('bIsGroundCover', False))
    o.fa(diffuse(stage, 'Tileable', [0.14, 0.07, 0.03]))
    o.fa(diffuse(stage, 'TwoSided', [0.09, 0.15, 0.02]))

    o.i(len(jnames))
    o.ia(parents)
    o.fa(jpos.reshape(-1))
    o.fa(radii)

    write_mesh(o, pts, trunk_nrm, tris, ji, jw)

    o.i(len(protos))
    for pname, length, (gpos, grad, gpar), leaves in protos:
        o.utf(pname)
        o.f(length)
        o.i(len(gpos))
        o.ia(gpar)
        o.fa(gpos.reshape(-1))
        o.fa(grad)
        o.i(RING)
        o.i(len(leaves))
        for base, c, n, ring in leaves:
            o.fa(base)
            o.fa(c)
            o.fa(n)
            o.fa(ring.reshape(-1))

    o.i(len(pidx))
    for k in range(len(pidx)):
        mm_ = mats[k]
        o.h(pidx[k])
        # 3 x 4, Zeilen: Spalten der linearen Abbildung, dann Verschiebung (USD: Zeilenvektoren)
        o.fa([mm_[r][c] * (mpu if r == 3 else 1.0) for r in range(4) for c in range(3)])
        o.ha(bjv[k, :2])
        o.ua(np.clip(np.round(bwv[k, :2] / max(bwv[k, :2].sum(), 1e-9) * 255), 0, 255))

    write_tail(o)
    with open(dst, 'wb') as raw, gzip.GzipFile(filename='', fileobj=raw, mode='wb', compresslevel=9, mtime=0) as fh:
        fh.write(bytes(o.b))       # ohne Zeitstempel: gleiche Eingabe, gleiche Datei

    print(f'{os.path.basename(dst)}: Gelenke {len(jnames)}, Stamm {len(tris)} Dreiecke, '
          f'Zweige {[(p[0], len(p[2][0]), len(p[3])) for p in protos]} (Knoten, Blaetter), '
          f'Instanzen {len(pidx)}, {os.path.getsize(dst) / 1e6:.2f} MB')


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('src')
    ap.add_argument('dst')
    ap.add_argument('--name', default=None)
    a = ap.parse_args()
    convert(a.src, a.dst, a.name or os.path.splitext(os.path.basename(a.src))[0])


if __name__ == '__main__':
    main()
