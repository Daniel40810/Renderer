package com.dan.longship;

import java.util.Arrays;

/**
 * Ein Netz aus Dreiecken in Schiffskoordinaten: x Steuerbord, y oben, z achteraus (wie {@code buoyancy}); je Ecke Lage,
 * Normale, Flächenkoordinate u, v (Meter) und Werkstoff ({@link #STRIDE} Zahlen). Eigene kleine Kopie, das Package
 * braucht kein anderes.
 */
public final class Mesh {
    public static final int STRIDE = 9;
    float[] v = new float[STRIDE * 256];
    int[] idx = new int[384];
    int nv, ni;

    public int vertices() { return nv; }
    public int triangles() { return ni / 3; }
    public float[] vertexData() { return Arrays.copyOf(v, nv * STRIDE); }
    public int[] indexData() { return Arrays.copyOf(idx, ni); }
    public void clear() { nv = 0; ni = 0; }

    public int vertex(double x, double y, double z, double nx, double ny, double nz, double u, double w, Material m) {
        if ((nv + 1) * STRIDE > v.length) v = Arrays.copyOf(v, v.length * 2);
        int o = nv * STRIDE;
        v[o] = (float) x; v[o + 1] = (float) y; v[o + 2] = (float) z;
        v[o + 3] = (float) nx; v[o + 4] = (float) ny; v[o + 5] = (float) nz;
        v[o + 6] = (float) u; v[o + 7] = (float) w; v[o + 8] = m.ordinal();
        return nv++;
    }

    public void tri(int a, int b, int c) {
        if (ni + 3 > idx.length) idx = Arrays.copyOf(idx, idx.length * 2);
        idx[ni++] = a; idx[ni++] = b; idx[ni++] = c;
    }

    /** Viereck p0 → p1 → p2 → p3, gegen den Uhrzeigersinn von außen, flach; uv je Ecke als {u0, v0, u1, v1, u2, v2, u3, v3}. */
    public void quad(double[] p0, double[] p1, double[] p2, double[] p3, Material m, double[] uv) {
        double[] n = normal(p0, p1, p3);
        if (len(n) < 1e-12) n = normal(p1, p2, p0);
        int a = vertex(p0[0], p0[1], p0[2], n[0], n[1], n[2], uv[0], uv[1], m);
        int b = vertex(p1[0], p1[1], p1[2], n[0], n[1], n[2], uv[2], uv[3], m);
        int c = vertex(p2[0], p2[1], p2[2], n[0], n[1], n[2], uv[4], uv[5], m);
        int d = vertex(p3[0], p3[1], p3[2], n[0], n[1], n[2], uv[6], uv[7], m);
        tri(a, b, c);
        tri(a, c, d);
    }

    /** Viereck mit u entlang p0 → p1 und v entlang p0 → p3 (Meter). */
    public void quad(double[] p0, double[] p1, double[] p2, double[] p3, Material m) {
        double lu = dist(p0, p1), lv = dist(p0, p3);
        quad(p0, p1, p2, p3, m, new double[]{0, 0, lu, 0, lu, lv, 0, lv});
    }

    /** Beidseitig: einmal so, einmal umgekehrt. */
    public void quad2(double[] p0, double[] p1, double[] p2, double[] p3, Material m) {
        quad(p0, p1, p2, p3, m);
        quad(p1, p0, p3, p2, m);
    }

    /** Balken von a nach b, sides Seiten, Halbmesser r (Faser entlang), mit Deckeln. */
    public void beam(double[] a, double[] b, double r, int sides, Material m) { beam(a, b, r, r, sides, m); }

    /** Balken, der sich von ra nach rb verjüngt. */
    public void beam(double[] a, double[] b, double ra, double rb, int sides, Material m) {
        double[] d = sub(b, a);
        double l = len(d);
        if (l < 1e-9) return;
        double[] w = {d[0] / l, d[1] / l, d[2] / l};
        double[] up = Math.abs(w[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] e1 = norm(cross(w, up)), e2 = cross(e1, w);
        int base = nv;
        for (int i = 0; i <= sides; i++) {
            double t = 2 * Math.PI * i / sides + (sides == 4 ? Math.PI / 4 : 0), c = Math.cos(t), s = Math.sin(t);
            double[] n = {e1[0] * c + e2[0] * s, e1[1] * c + e2[1] * s, e1[2] * c + e2[2] * s};
            vertex(a[0] + n[0] * ra, a[1] + n[1] * ra, a[2] + n[2] * ra, n[0], n[1], n[2], 0, ra * t, m);
            vertex(b[0] + n[0] * rb, b[1] + n[1] * rb, b[2] + n[2] * rb, n[0], n[1], n[2], l, rb * t, m);
        }
        for (int i = 0; i < sides; i++) { int k = base + i * 2; tri(k, k + 3, k + 2); tri(k, k + 1, k + 3); }
        for (int end = 0; end < 2; end++) {
            double[] c0 = end == 0 ? a : b;
            double rr = end == 0 ? ra : rb, sg = end == 0 ? -1 : 1;
            int ci = vertex(c0[0], c0[1], c0[2], w[0] * sg, w[1] * sg, w[2] * sg, 0, 0, m);
            int first = nv;
            for (int i = 0; i < sides; i++) {
                double t = 2 * Math.PI * i / sides + (sides == 4 ? Math.PI / 4 : 0), c = Math.cos(t), s = Math.sin(t);
                vertex(c0[0] + (e1[0] * c + e2[0] * s) * rr, c0[1] + (e1[1] * c + e2[1] * s) * rr, c0[2] + (e1[2] * c + e2[2] * s) * rr, w[0] * sg, w[1] * sg, w[2] * sg, c * rr, s * rr, m);
            }
            for (int i = 0; i < sides; i++) { int i0 = first + i, i1 = first + (i + 1) % sides; if (end == 0) tri(ci, i0, i1); else tri(ci, i1, i0); }
        }
    }

    public void append(Mesh o) {
        int base = nv;
        if ((nv + o.nv) * STRIDE > v.length) v = Arrays.copyOf(v, Math.max(v.length * 2, (nv + o.nv) * STRIDE));
        System.arraycopy(o.v, 0, v, nv * STRIDE, o.nv * STRIDE);
        nv += o.nv;
        if (ni + o.ni > idx.length) idx = Arrays.copyOf(idx, Math.max(idx.length * 2, ni + o.ni));
        for (int i = 0; i < o.ni; i++) idx[ni + i] = o.idx[i] + base;
        ni += o.ni;
    }

    /** Ausdehnung {minX, minY, minZ, maxX, maxY, maxZ}. */
    public double[] bounds() {
        double[] b = {1e30, 1e30, 1e30, -1e30, -1e30, -1e30};
        for (int i = 0; i < nv; i++) for (int k = 0; k < 3; k++) { b[k] = Math.min(b[k], v[i * STRIDE + k]); b[k + 3] = Math.max(b[k + 3], v[i * STRIDE + k]); }
        return b;
    }

    /** Fläche aller Dreiecke eines Werkstoffs (m²). */
    public double area(Material m) {
        double s = 0;
        for (int t = 0; t < ni; t += 3) {
            if ((int) v[idx[t] * STRIDE + 8] != m.ordinal()) continue;
            s += len(cross(sub(pos(idx[t + 1]), pos(idx[t])), sub(pos(idx[t + 2]), pos(idx[t])))) / 2;
        }
        return s;
    }

    public double[] pos(int i) { return new double[]{v[i * STRIDE], v[i * STRIDE + 1], v[i * STRIDE + 2]}; }
    public double[] nrm(int i) { return new double[]{v[i * STRIDE + 3], v[i * STRIDE + 4], v[i * STRIDE + 5]}; }

    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double len(double[] a) { return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); }
    static double[] norm(double[] a) { double l = len(a); return l < 1e-12 ? new double[]{0, 1, 0} : new double[]{a[0] / l, a[1] / l, a[2] / l}; }
    static double dist(double[] a, double[] b) { return len(sub(a, b)); }
    public static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    public static double[] normal(double[] a, double[] b, double[] c) { return norm(cross(sub(b, a), sub(c, a))); }
}
