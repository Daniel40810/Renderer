package com.dan.understory;

import java.util.Arrays;
import java.util.Random;

/**
 * Netz einer Pflanze oder eines Stücks Totholz, prozedural aus Art, Variante (Startwert) und Stufe (0 nah, 1 fern). Der
 * Fuß liegt bei (0, 0, 0), y nach oben; Bartflechten hängen vom Ankerpunkt (0, 0, 0) nach unten. Je Ecke 11 Werte:
 * Lage 3, Normale 3, Farbe 3 (linear), Höhe im Netz 0..1 (für den Wind; Flechte: 0 am Anker, 1 an der Spitze), Laub
 * (1 Blatt oder Wedel, beidseitig und durchscheinend; 0 Holz, Moos, Streu).
 */
public final class PlantMesh {
    public static final int STRIDE = 11;

    public final Kind kind;
    public final int variant, lod;
    public float[] v = new float[STRIDE * 256];
    public int[] tri = new int[3 * 512];
    public int nv, nt;
    /** Größe: Durchmesser (Moos, Streu), Länge (Farnwedel, Totholz, Flechte) oder Höhe (Teufelskeule, Strauch) in m. */
    public float size;
    /** Höhe über dem Fuß (m) und Radius um die Achse (m), für Sichtprüfung und Stufen. */
    public float height, radius;

    private PlantMesh(Kind kind, int variant, int lod) { this.kind = kind; this.variant = variant; this.lod = lod; }

    /** Baut das Netz; gleiche Werte ergeben dasselbe Netz. */
    public static PlantMesh build(Kind k, int variant, int lod) {
        PlantMesh m = new PlantMesh(k, variant, lod);
        Random r = new Random(k.ordinal() * 7919L + variant * 104729L + 17);
        m.size = (float) (k.sizeMin + (k.sizeMax - k.sizeMin) * (0.25 + 0.5 * r.nextDouble()));
        switch (k) {
            case MOSS -> m.moss(r);
            case FERN -> m.fern(r);
            case DEVILS_CLUB -> m.devilsClub(r);
            case BERRY -> m.berry(r);
            case DEADWOOD -> m.deadwood(r);
            case LITTER -> m.litter(r);
            case LICHEN -> m.lichen(r);
        }
        m.v = Arrays.copyOf(m.v, m.nv * STRIDE);
        m.tri = Arrays.copyOf(m.tri, m.nt * 3);
        float hi = 0, lo = 0, rad = 0;
        for (int i = 0; i < m.nv; i++) {
            hi = Math.max(hi, m.v[i * STRIDE + 1]);
            lo = Math.min(lo, m.v[i * STRIDE + 1]);
            rad = Math.max(rad, (float) Math.hypot(m.v[i * STRIDE], m.v[i * STRIDE + 2]));
        }
        m.height = k == Kind.LICHEN ? -lo : hi;
        m.radius = rad;
        return m;
    }

    // ------------------------------------------------------------------ Bausteine

    int vert(float x, float y, float z, float nx, float ny, float nz, float r, float g, float b, float h, float leafy) {
        if ((nv + 1) * STRIDE > v.length) v = Arrays.copyOf(v, v.length * 2);
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-6f) { nx = 0; ny = 1; nz = 0; l = 1; }
        int o = nv * STRIDE;
        v[o] = x; v[o + 1] = y; v[o + 2] = z;
        v[o + 3] = nx / l; v[o + 4] = ny / l; v[o + 5] = nz / l;
        v[o + 6] = r; v[o + 7] = g; v[o + 8] = b;
        v[o + 9] = h; v[o + 10] = leafy;
        return nv++;
    }

    void tri(int a, int b, int c) {
        if ((nt + 1) * 3 > tri.length) tri = Arrays.copyOf(tri, tri.length * 2);
        tri[nt * 3] = a; tri[nt * 3 + 1] = b; tri[nt * 3 + 2] = c;
        nt++;
    }

    /** Dreieck mit eigener Normale (flach), Farbe c, Laub leafy, Höhen aus y / hmax. */
    void flat(float[] a, float[] b, float[] c, float[] col, float leafy, float hmax) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
        float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        if (ny < 0) { nx = -nx; ny = -ny; nz = -nz; }
        int i = vert(a[0], a[1], a[2], nx, ny, nz, col[0], col[1], col[2], h(a, hmax), leafy);
        int j = vert(b[0], b[1], b[2], nx, ny, nz, col[0], col[1], col[2], h(b, hmax), leafy);
        int k = vert(c[0], c[1], c[2], nx, ny, nz, col[0], col[1], col[2], h(c, hmax), leafy);
        tri(i, j, k);
    }

    private static float h(float[] p, float hmax) { return Math.max(0, Math.min(1, p[1] / Math.max(0.05f, hmax))); }

    /** Röhre entlang der Punkte p (je 3), Radien r, sides Seiten; Farbe col; Höhe für den Wind aus y / hmax. */
    void tube(float[][] p, float[] r, int sides, float[] col, float hmax) {
        int base = nv;
        for (int i = 0; i < p.length; i++) {
            float[] d = i < p.length - 1 ? sub(p[i + 1], p[i]) : sub(p[i], p[i - 1]);
            float[] u = perp(d), w = cross(d, u);
            norm(w);
            for (int s = 0; s < sides; s++) {
                double a = 2 * Math.PI * s / sides;
                float cx = (float) Math.cos(a), sx = (float) Math.sin(a);
                float nx = u[0] * cx + w[0] * sx, ny = u[1] * cx + w[1] * sx, nz = u[2] * cx + w[2] * sx;
                vert(p[i][0] + nx * r[i], p[i][1] + ny * r[i], p[i][2] + nz * r[i], nx, ny, nz, col[0], col[1], col[2], Math.max(0, Math.min(1, p[i][1] / hmax)), 0);
            }
        }
        for (int i = 0; i < p.length - 1; i++)
            for (int s = 0; s < sides; s++) {
                int a = base + i * sides + s, b = base + i * sides + (s + 1) % sides, c = a + sides, d = b + sides;
                tri(a, b, c);   // außen gegen den Uhrzeigersinn (Vorderseite nach außen)
                tri(b, d, c);
            }
    }

    static float[] sub(float[] a, float[] b) { return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static float[] cross(float[] a, float[] b) { return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static void norm(float[] a) { float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); if (l > 1e-9f) { a[0] /= l; a[1] /= l; a[2] /= l; } }
    static float[] perp(float[] d) {
        float[] u = Math.abs(d[1]) < 0.9f ? cross(d, new float[]{0, 1, 0}) : cross(d, new float[]{1, 0, 0});
        norm(u);
        return u;
    }
    static float[] tone(float[] c, Random r, float amount) {
        float k = 1 + (r.nextFloat() - 0.5f) * 2 * amount;
        return new float[]{c[0] * k, c[1] * k * (1 + (r.nextFloat() - 0.5f) * amount * 0.5f), c[2] * k};
    }

    // ------------------------------------------------------------------ Arten

    /** Moospolster: flaches, beuliges Kissen; oben gelblicher, am Rand dunkler. */
    void moss(Random r) {
        int rings = lod == 0 ? 7 : 3, seg = lod == 0 ? 18 : 8;
        float R = size / 2, H = size * (0.22f + 0.15f * r.nextFloat());
        float ph1 = r.nextFloat() * 6.28f, ph2 = r.nextFloat() * 6.28f;
        float[] top = {0.075f, 0.13f, 0.018f}, side = {0.035f, 0.07f, 0.014f};
        top = tone(top, r, 0.15f);
        int c = vert(0, H, 0, 0, 1, 0, top[0], top[1], top[2], 1, 0);
        int prev = -1;
        for (int i = 1; i <= rings; i++) {
            double th = 0.56 * Math.PI * i / rings;
            int start = nv;
            for (int s = 0; s < seg; s++) {
                double ph = 2 * Math.PI * s / seg;
                double bump = 1 + 0.16 * Math.sin(5 * ph + ph1) * Math.sin(3 * th + ph2) + 0.08 * Math.sin(9 * ph - 2 * th);
                float x = (float) (R * Math.sin(th) * Math.cos(ph) * bump), z = (float) (R * Math.sin(th) * Math.sin(ph) * bump), y = (float) (H * Math.cos(th) * bump);
                float t = (float) (th / (0.56 * Math.PI));
                vert(x, y, z, x / (R * R), y / (H * H), z / (R * R), top[0] + (side[0] - top[0]) * t, top[1] + (side[1] - top[1]) * t, top[2] + (side[2] - top[2]) * t,
                        Math.max(0, y / H), 0);
            }
            for (int s = 0; s < seg; s++) {
                int a = start + s, b = start + (s + 1) % seg;
                if (prev < 0) tri(c, b, a);
                else { int pa = prev + s, pb = prev + (s + 1) % seg; tri(pa, pb, a); tri(pb, b, a); }
            }
            prev = start;
        }
    }

    /** Farn: Trichter aus bogigen Wedeln, Fiedern als Dreiecke (Stufe 1: Wedel als Band). */
    void fern(Random r) {
        boolean sword = variant % 2 == 0;
        float[] col = sword ? new float[]{0.028f, 0.075f, 0.018f} : new float[]{0.06f, 0.15f, 0.028f};
        int fronds = lod == 0 ? 11 + r.nextInt(5) : 8;
        float L = size, hmax = L * 0.75f;
        for (int f = 0; f < fronds; f++) {
            double az = 2 * Math.PI * f / fronds + r.nextGaussian() * 0.2;
            double e0 = Math.toRadians(58 + 18 * r.nextDouble()), droop = 1.1 + 0.5 * r.nextDouble();
            float len = L * (0.75f + 0.25f * r.nextFloat());
            float dx = (float) Math.cos(az), dz = (float) Math.sin(az);
            float[] fc = tone(col, r, 0.12f);
            int n = lod == 0 ? 16 : 7;
            float[][] p = new float[n + 1][];
            for (int i = 0; i <= n; i++) {
                double s = i / (double) n, e = e0 - droop * s * s;
                double hd = 0, hy = 0;
                for (int k = 0; k < i; k++) { double ss = (k + 0.5) / n, ee = e0 - droop * ss * ss; hd += Math.cos(ee) * len / n; hy += Math.sin(ee) * len / n; }
                p[i] = new float[]{(float) (dx * hd), (float) hy, (float) (dz * hd)};
            }
            float sx = -dz, sz = dx;   // seitlich zum Wedel
            for (int i = 0; i < n; i++) {
                float s = (i + 0.5f) / n;
                float w = (float) (len * (sword ? 0.11 : 0.16) * Math.sin(Math.PI * (0.12 + 0.88 * s)) * (1 - 0.6 * s));
                float[] a = p[i], b = p[i + 1];
                if (lod == 0) {
                    for (int side = -1; side <= 1; side += 2) {
                        float[] mid = {(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2};
                        float[] tip = {mid[0] + side * sx * w + (b[0] - a[0]) * 0.8f, mid[1] - w * 0.15f, mid[2] + side * sz * w + (b[2] - a[2]) * 0.8f};
                        flat(a, b, tip, fc, 1, hmax);
                    }
                } else {
                    float[] a1 = {a[0] + sx * w, a[1], a[2] + sz * w}, a2 = {a[0] - sx * w, a[1], a[2] - sz * w};
                    float[] b1 = {b[0] + sx * w * 0.8f, b[1], b[2] + sz * w * 0.8f}, b2 = {b[0] - sx * w * 0.8f, b[1], b[2] - sz * w * 0.8f};
                    flat(a1, b1, a2, fc, 1, hmax);
                    flat(a2, b1, b2, fc, 1, hmax);
                }
            }
        }
    }

    /** Teufelskeule: einige schräge Stöcke, oben große gelappte Blätter, an einem eine rote Beerentraube. */
    void devilsClub(Random r) {
        int canes = 2 + r.nextInt(lod == 0 ? 4 : 2);
        float H = size;
        float[] stem = {0.16f, 0.12f, 0.07f}, leaf = {0.055f, 0.16f, 0.025f}, berry = {0.45f, 0.03f, 0.02f};
        for (int c = 0; c < canes; c++) {
            double az = r.nextDouble() * 2 * Math.PI, lean = Math.toRadians(8 + 28 * r.nextDouble());
            float hc = H * (0.6f + 0.4f * r.nextFloat());
            int n = lod == 0 ? 6 : 3;
            float[][] p = new float[n + 1][];
            float[] rad = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                double s = i / (double) n, l = lean * (0.4 + s);   // stärker geneigt nach oben, leicht S-förmig
                float hd = (float) (hc * s * Math.sin(l)), hy = (float) (hc * s * Math.cos(l * 0.8));
                p[i] = new float[]{(float) (Math.cos(az) * hd + 0.15 * Math.cos(az + 1) * (c - canes / 2.0)), hy, (float) (Math.sin(az) * hd + 0.15 * Math.sin(az + 1) * (c - canes / 2.0))};
                rad[i] = 0.022f * (1 - 0.4f * (float) s);
            }
            tube(p, rad, lod == 0 ? 5 : 3, stem, H);
            float[] top = p[n];
            int leaves = lod == 0 ? 3 + r.nextInt(4) : 2;
            for (int k = 0; k < leaves; k++) {
                double la = r.nextDouble() * 2 * Math.PI;
                float R = (float) (0.18 + 0.17 * r.nextDouble()) * H / 2;
                float cx = top[0] + (float) Math.cos(la) * R * 0.8f, cz = top[2] + (float) Math.sin(la) * R * 0.8f, cy = top[1] - 0.05f - 0.1f * r.nextFloat();
                float tilt = (float) (r.nextGaussian() * 0.15);
                int lobes = lod == 0 ? 28 : 10;
                float[] lc = tone(leaf, r, 0.15f);
                int center = vert(cx, cy, cz, tilt, 1, 0, lc[0], lc[1], lc[2], 1, 1);
                int first = nv;
                for (int s = 0; s < lobes; s++) {
                    double a = 2 * Math.PI * s / lobes;
                    double rr = R * (0.72 + 0.28 * Math.abs(Math.cos(3.5 * a))) * (1 + (lod == 0 ? 0.06 * ((s & 1) * 2 - 1) : 0));
                    float x = cx + (float) (Math.cos(a) * rr), z = cz + (float) (Math.sin(a) * rr);
                    float y = cy - (float) (rr * 0.25) + tilt * (x - cx);   // Rand hängt
                    vert(x, y, z, tilt, 1, 0, lc[0], lc[1], lc[2], 1, 1);
                }
                for (int s = 0; s < lobes; s++) tri(center, first + (s + 1) % lobes, first + s);
            }
            if (c == 0) {
                // Beerentraube: kleiner roter Kegel über der Mitte
                float bh = 0.16f, br = 0.05f;
                int tip = vert(top[0], top[1] + bh, top[2], 0, 1, 0, berry[0], berry[1], berry[2], 1, 0);
                int first = nv;
                for (int s = 0; s < 6; s++) {
                    double a = Math.PI / 3 * s;
                    vert(top[0] + (float) Math.cos(a) * br, top[1], top[2] + (float) Math.sin(a) * br, (float) Math.cos(a), 0.4f, (float) Math.sin(a), berry[0], berry[1], berry[2], 1, 0);
                }
                for (int s = 0; s < 6; s++) tri(tip, first + (s + 1) % 6, first + s);
            }
        }
    }

    /** Beerenstrauch: Zweige aus dem Grund, kleine Blätter, einige Beeren (blau oder orange je Variante). */
    void berry(Random r) {
        float H = size;
        boolean blue = variant % 2 == 0;
        float[] twig = {0.07f, 0.09f, 0.035f}, leaf = {0.07f, 0.165f, 0.032f}, fruit = blue ? new float[]{0.03f, 0.035f, 0.09f} : new float[]{0.55f, 0.20f, 0.03f};
        int twigs = lod == 0 ? 12 + r.nextInt(9) : 8;
        for (int t = 0; t < twigs; t++) {
            double az = r.nextDouble() * 2 * Math.PI, out = Math.toRadians(15 + 40 * r.nextDouble());
            float len = H * (0.6f + 0.4f * r.nextFloat());
            int n = 4;
            float[][] p = new float[n + 1][];
            float[] rad = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                double s = i / (double) n, o = out * (0.6 + 0.6 * s);
                p[i] = new float[]{(float) (Math.cos(az) * Math.sin(o) * len * s), (float) (Math.cos(o) * len * s), (float) (Math.sin(az) * Math.sin(o) * len * s)};
                rad[i] = 0.006f * (1 - 0.6f * (float) s);
            }
            if (lod == 0) tube(p, rad, 3, twig, H);
            int leaves = lod == 0 ? 9 : 4;
            float ls = (float) (lod == 0 ? 0.045 : 0.075) * (0.8f + 0.4f * r.nextFloat());
            for (int k = 0; k < leaves; k++) {
                float s = 0.3f + 0.7f * (k + r.nextFloat()) / leaves;
                int i = Math.min(n - 1, (int) (s * n));
                float u = s * n - i;
                float[] q = {p[i][0] + (p[i + 1][0] - p[i][0]) * u, p[i][1] + (p[i + 1][1] - p[i][1]) * u, p[i][2] + (p[i + 1][2] - p[i][2]) * u};
                double la = r.nextDouble() * 2 * Math.PI;
                float ax = (float) Math.cos(la) * ls, az2 = (float) Math.sin(la) * ls;
                float[] a = q, b = {q[0] + ax * 0.5f - az2 * 0.35f, q[1] + ls * 0.15f, q[2] + az2 * 0.5f + ax * 0.35f}, c = {q[0] + ax, q[1] + ls * 0.1f, q[2] + az2},
                        d = {q[0] + ax * 0.5f + az2 * 0.35f, q[1] + ls * 0.15f, q[2] + az2 * 0.5f - ax * 0.35f};
                float[] lc = tone(leaf, r, 0.15f);
                flat(a, b, c, lc, 1, H);
                flat(a, c, d, lc, 1, H);
            }
            if (lod == 0 && r.nextFloat() < 0.6f) {
                float[] q = p[n - 1];
                float br = blue ? 0.006f : 0.009f;
                int top = vert(q[0], q[1] + br, q[2], 0, 1, 0, fruit[0], fruit[1], fruit[2], 1, 0);
                int first = nv;
                for (int s = 0; s < 4; s++) {
                    double a = Math.PI / 2 * s;
                    vert(q[0] + (float) Math.cos(a) * br, q[1], q[2] + (float) Math.sin(a) * br, (float) Math.cos(a), 0, (float) Math.sin(a), fruit[0], fruit[1], fruit[2], 1, 0);
                }
                int bot = vert(q[0], q[1] - br, q[2], 0, -1, 0, fruit[0], fruit[1], fruit[2], 1, 0);
                for (int s = 0; s < 4; s++) { tri(top, first + (s + 1) % 4, first + s); tri(bot, first + s, first + (s + 1) % 4); }
            }
        }
    }

    /** Totholz: liegender Stamm entlang +x, oben bemoost, Stirnflächen morsch, Aststummel und junge Bäume darauf (Stufe 0). */
    void deadwood(Random r) {
        float L = size, R = 0.18f + 0.025f * L * (0.7f + 0.6f * r.nextFloat());
        int sides = lod == 0 ? 12 : 6, segs = lod == 0 ? 8 : 3;
        float[] bark = {0.085f, 0.07f, 0.055f}, moss = {0.05f, 0.11f, 0.02f}, wood = {0.22f, 0.12f, 0.07f};
        float sink = 0.15f * R;
        int base = nv;
        for (int i = 0; i <= segs; i++) {
            float s = i / (float) segs, x = s * L;
            float rr = R * (1 - 0.25f * s), yc = R - sink + (float) Math.sin(s * Math.PI) * 0.03f * L * 0.1f;
            for (int k = 0; k < sides; k++) {
                double a = 2 * Math.PI * k / sides;
                float ny = (float) Math.sin(a), nz = (float) Math.cos(a);
                float bump = lod == 0 ? 1 + 0.05f * (float) Math.sin(7 * a + 3 * s) : 1;
                float m = Math.max(0, Math.min(1, (ny - 0.1f) / 0.5f));   // oben Moos
                vert(x, yc + ny * rr * bump, nz * rr * bump, 0, ny, nz,
                        bark[0] + (moss[0] - bark[0]) * m, bark[1] + (moss[1] - bark[1]) * m, bark[2] + (moss[2] - bark[2]) * m, 0, 0);
            }
        }
        for (int i = 0; i < segs; i++)
            for (int k = 0; k < sides; k++) {
                int a = base + i * sides + k, b = base + i * sides + (k + 1) % sides, c = a + sides, d = b + sides;
                tri(a, c, b);   // Vorderseite nach außen
                tri(b, c, d);
            }
        for (int e = 0; e <= 1; e++) {
            float x = e * L, rr = R * (1 - 0.25f * e), yc = R - sink;
            int c = vert(x, yc, 0, e == 0 ? -1 : 1, 0, 0, wood[0], wood[1], wood[2], 0, 0);
            int first = nv;
            for (int k = 0; k < sides; k++) {
                double a = 2 * Math.PI * k / sides;
                vert(x, yc + (float) Math.sin(a) * rr, (float) Math.cos(a) * rr, e == 0 ? -1 : 1, 0, 0, wood[0], wood[1], wood[2], 0, 0);
            }
            for (int k = 0; k < sides; k++) if (e == 0) tri(c, first + k, first + (k + 1) % sides); else tri(c, first + (k + 1) % sides, first + k);
        }
        if (lod == 0) {
            int stubs = 3 + r.nextInt(4);
            for (int k = 0; k < stubs; k++) {
                float x = L * (0.2f + 0.7f * r.nextFloat());
                double a = r.nextDouble() * Math.PI;   // nur oben und seitlich
                float[] p0 = {x, R - sink + (float) Math.sin(a) * R * 0.9f, (float) Math.cos(a) * R * 0.9f};
                float len = 0.3f + 0.6f * r.nextFloat();
                float[] p1 = {p0[0] + 0.2f * len, p0[1] + (float) Math.sin(a) * len, p0[2] + (float) Math.cos(a) * len};
                tube(new float[][]{p0, p1}, new float[]{0.04f, 0.02f}, 4, bark, 1);
            }
            int seedlings = r.nextInt(4);
            float[] green = {0.025f, 0.07f, 0.025f};
            for (int k = 0; k < seedlings; k++) {
                float x = L * (0.15f + 0.7f * r.nextFloat()), y = 2 * R - sink, h = 0.4f + 0.9f * r.nextFloat(), rr = h * 0.3f;
                int tip = vert(x, y + h, 0, 0, 1, 0, green[0], green[1], green[2], 1, 1);
                int first = nv;
                for (int s = 0; s < 6; s++) {
                    double a = Math.PI / 3 * s;
                    vert(x + (float) Math.cos(a) * rr, y + h * 0.15f, (float) Math.sin(a) * rr, (float) Math.cos(a), 0.6f, (float) Math.sin(a), green[0], green[1], green[2], 0.2f, 1);
                }
                for (int s = 0; s < 6; s++) tri(tip, first + (s + 1) % 6, first + s);
            }
        }
    }

    /** Nadelstreu: ein flacher, unregelmäßiger Fleck knapp über dem Boden, braun bis rostrot. */
    void litter(Random r) {
        int n = lod == 0 ? 26 : 10;
        float R = size / 2;
        float[] col = tone(new float[]{0.10f, 0.055f, 0.025f}, r, 0.2f);
        float p1 = r.nextFloat() * 6.28f, p2 = r.nextFloat() * 6.28f;
        int c = vert(0, 0.025f, 0, 0, 1, 0, col[0] * 1.1f, col[1] * 1.1f, col[2], 0, 0);
        int first = nv;
        for (int s = 0; s < n; s++) {
            double a = 2 * Math.PI * s / n;
            float rr = R * (float) (0.75 + 0.18 * Math.sin(3 * a + p1) + 0.1 * Math.sin(7 * a + p2));
            vert((float) Math.cos(a) * rr, 0.012f, (float) Math.sin(a) * rr, 0, 1, 0, col[0] * 0.85f, col[1] * 0.85f, col[2] * 0.9f, 0, 0);
        }
        for (int s = 0; s < n; s++) tri(c, first + (s + 1) % n, first + s);
    }

    /** Bartflechte: ein Büschel dünner Bänder, die vom Anker (0, 0, 0) herabhängen und sich unten spreizen. */
    void lichen(Random r) {
        int strands = lod == 0 ? 24 : 8;
        float L = size;
        float[] col = {0.24f, 0.30f, 0.18f};
        for (int s = 0; s < strands; s++) {
            double az = r.nextDouble() * 2 * Math.PI;
            float len = L * (0.45f + 0.55f * r.nextFloat()), w = (lod == 0 ? 0.018f : 0.04f) * (0.7f + 0.6f * r.nextFloat());
            float ox = (float) Math.cos(az), oz = (float) Math.sin(az);
            float[] c = tone(col, r, 0.1f);
            int segs = 3;
            int prevA = -1, prevB = -1;
            for (int i = 0; i <= segs; i++) {
                float t = i / (float) segs, spread = (0.06f + 0.3f * t) * len * t;
                float x = ox * spread, z = oz * spread, y = -len * t;
                int a = vert(x - oz * w, y, z + ox * w, ox, 0, oz, c[0], c[1], c[2], t, 1);
                int b = vert(x + oz * w, y, z - ox * w, ox, 0, oz, c[0], c[1], c[2], t, 1);
                if (prevA >= 0) { tri(prevA, prevB, a); tri(prevB, b, a); }
                prevA = a;
                prevB = b;
            }
        }
    }

    @Override public String toString() { return kind.name + " Variante " + variant + " Stufe " + lod + ": " + nt + " Dreiecke, " + String.format(java.util.Locale.GERMAN, "%.2f m", size); }
}
