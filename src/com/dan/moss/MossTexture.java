package com.dan.moss;

import java.util.Random;

/**
 * Eine kachelbare Moostextur (seit LituyaBay1958 W6 Runde 7): Polster aus einzelnen Trieben mit Blättchen, von oben
 * gesehen, in einem Tiefenpuffer gezeichnet (höhere Triebe decken tiefere). Ergebnis je Bildpunkt: Albedo (linear),
 * Höhe, Verdeckung (Höhlen zwischen den Trieben, Grund des Polsters) und Normale. Alles läuft über den Rand der Kachel
 * hinaus und wieder hinein (nahtlos). Gerechnet in doppelter Auflösung und 2 × 2 gemittelt (glatte Kanten).
 * <p>
 * Gestaltet [G] nach Fotos ({@link MossLook}): Triebe mit Blättchen links und rechts im Wechsel, zur Spitze kleiner und
 * heller, aus dem Polster aufragend; große Buckel aus kachelbarem Rauschen.
 */
public final class MossTexture {
    /** Kante in Bildpunkten. */
    public final int n;
    /** Albedo linear, je Bildpunkt RGB. */
    public final float[] albedo;
    /** Höhe 0..1 (über {@link #heightRange} m), Verdeckung 0..1, Normale x, y (z = sqrt(1 − x² − y²)). */
    public final float[] height, ao, normal;
    /** Spanne der Höhe (m) und Kante der Kachel (m). */
    public float heightRange, tile;
    /** Mittel der Albedo und der Albedo mal Verdeckung (die Farbe aus der Ferne). */
    public final float[] meanAlbedo = new float[3], meanSeen = new float[3];
    /** Anteil der Bildpunkte, die ein Trieb deckt (sonst Grund). */
    public float coverage;

    private MossTexture(int n) {
        this.n = n;
        albedo = new float[3 * n * n];
        height = new float[n * n];
        ao = new float[n * n];
        normal = new float[2 * n * n];
    }

    /** Rechnet die Kachel (n Bildpunkte je Kante, Zweierpotenz empfohlen) für look mit seed. */
    public static MossTexture bake(MossLook look, int n, long seed) {
        int N = 2 * n;
        float px = look.tile / N;   // m je Bildpunkt beim Rechnen
        Random r = new Random(seed);
        float[] noise = tileNoise(N, new int[]{2, 4, 8}, new float[]{0.55f, 0.3f, 0.15f}, r);
        float amp = look.cushion * 1.4f * look.shootMax;
        float[] z = new float[N * N], col = new float[3 * N * N];
        boolean[] hit = new boolean[N * N];
        for (int i = 0; i < N * N; i++) z[i] = noise[i] * amp - 0.6f * look.shootMax;   // Grund unter den Trieben
        int shoots = Math.max(1, Math.round(look.shootsPerM2 * look.tile * look.tile));
        // Polster: Kuppeln mit Halbmesser R, die Triebe stehen in ihnen und zeigen nach außen; dazwischen Lücken [G]
        java.util.List<float[]> clumps = new java.util.ArrayList<>();
        double area = 0, want = look.clumpCover * 1.35 * look.tile * look.tile;
        while (area < want) {
            float R = look.clumpMin + r.nextFloat() * (look.clumpMax - look.clumpMin);
            clumps.add(new float[]{r.nextFloat() * N, r.nextFloat() * N, R / px, look.clumpHeight * (0.6f + 0.6f * r.nextFloat()) * R / look.clumpMax});
            area += Math.PI * R * R;
        }
        float[] cum = new float[clumps.size()];
        float sumA = 0;
        for (int k = 0; k < cum.length; k++) { float R = clumps.get(k)[2]; sumA += R * R; cum[k] = sumA; }
        float[] c = new float[3];
        for (int s = 0; s < shoots; s++) {
            // ein Drittel als Unterschicht überall (keine kahlen Lücken), der Rest in den Polstern
            boolean unter = r.nextFloat() < 0.33f;
            float x, y, a, dome, rr = 0;
            if (unter) {
                x = r.nextFloat() * N; y = r.nextFloat() * N;
                a = (float) (r.nextFloat() * 2 * Math.PI);
                dome = -0.35f * look.clumpHeight;
            } else {
                float pick = r.nextFloat() * sumA;
                int ci = java.util.Arrays.binarySearch(cum, pick);
                if (ci < 0) ci = -ci - 1;
                float[] cl = clumps.get(Math.min(ci, cum.length - 1));
                rr = (float) Math.sqrt(r.nextFloat());
                float phi = (float) (r.nextFloat() * 2 * Math.PI);
                x = cl[0] + (float) Math.cos(phi) * rr * cl[2]; y = cl[1] + (float) Math.sin(phi) * rr * cl[2];
                a = phi + (r.nextFloat() - 0.5f) * 3.2f;
                dome = cl[3] * (1 - rr * rr);
            }
            float bend = (r.nextFloat() - 0.5f) * 2.2f;
            float L = (look.shootMin + r.nextFloat() * (look.shootMax - look.shootMin)) / px;
            int ix = Math.floorMod((int) x, N), iy = Math.floorMod((int) y, N);
            float z0 = noise[iy * N + ix] * amp + dome - 0.45f * look.shootMax * r.nextFloat();
            float rise = (0.35f + 0.45f * r.nextFloat()) * L * px;   // die Spitze ragt auf (m)
            float j = 1 + look.jitter * (r.nextFloat() * 2 - 1), hue = look.jitter * (r.nextFloat() * 2 - 1);
            float stemW = Math.max(0.7f, 0.35f * look.leaf / px);
            float leafL = 2.0f * look.leaf / px, leafW = look.leaf / px;
            // Stängel
            float cx = x, cy = y, dirA = a;
            int steps = Math.max(2, (int) (L * 2));
            float[] sx = new float[steps + 1], sy = new float[steps + 1], sa = new float[steps + 1];
            for (int k = 0; k <= steps; k++) {
                float t = k / (float) steps;
                sx[k] = cx; sy[k] = cy; sa[k] = dirA;
                shade(look, t * 0.85f, j, hue, c);
                disc(N, cx, cy, stemW * (1 - 0.5f * t), z0 + rise * t, c, z, col, hit);
                cx += (float) Math.cos(dirA) * L / steps;
                cy += (float) Math.sin(dirA) * L / steps;
                dirA += bend / steps;
            }
            // Blättchen im Wechsel, zur Spitze kleiner und heller; die Spitze selbst ein gestrecktes Blättchen
            int m = look.leavesPerShoot;
            for (int k = 0; k < m; k++) {
                float t = (k + 1f) / (m + 1f);
                int si = Math.min(steps, Math.round(t * steps));
                float side = (k & 1) == 0 ? 1 : -1;
                float la = sa[si] + side * (0.75f + 0.25f * r.nextFloat());
                float sc = 1 - 0.55f * t;
                shade(look, t, j, hue, c);
                ellipse(N, sx[si], sy[si], la, leafL * sc, leafW * sc, z0 + rise * t + 0.15f * leafW * px, rise * 0.25f / Math.max(1, leafL), c, z, col, hit);
            }
            shade(look, 1, j, hue, c);
            ellipse(N, sx[steps], sy[steps], sa[steps], leafL * 0.55f, leafW * 0.5f, z0 + rise + 0.2f * leafW * px, 0, c, z, col, hit);
        }
        // Grund zwischen den Trieben: dunkel, braungrün
        for (int i = 0; i < N * N; i++)
            if (!hit[i]) { col[3 * i] = look.base[0] * 0.55f; col[3 * i + 1] = look.base[1] * 0.5f; col[3 * i + 2] = look.base[2] * 0.6f; }
        // auf n herunter (2 × 2)
        MossTexture t = new MossTexture(n);
        t.tile = look.tile;
        float[] zz = new float[n * n];
        int covered = 0;
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                int o = y * n + x;
                for (int dy = 0; dy < 2; dy++)
                    for (int dx = 0; dx < 2; dx++) {
                        int q = (2 * y + dy) * N + 2 * x + dx;
                        for (int k = 0; k < 3; k++) t.albedo[3 * o + k] += 0.25f * col[3 * q + k];
                        zz[o] += 0.25f * z[q];
                        if (hit[q]) covered++;
                    }
            }
        t.coverage = covered / (float) (N * N);
        // Verdeckung: Höhle gegen die Umgebung (zwei Halbmesser) und Tiefe im Polster
        float[] b1 = blur(zz, n, Math.max(1, Math.round(look.shootMax / (2 * look.tile / n)))), b2 = blur(zz, n, Math.max(2, Math.round(2 * look.shootMax / (look.tile / n))));
        float zmin = Float.MAX_VALUE, zmax = -Float.MAX_VALUE;
        for (float v : zz) { zmin = Math.min(zmin, v); zmax = Math.max(zmax, v); }
        t.heightRange = zmax - zmin;
        float cav = 0.35f * look.shootMax;
        for (int i = 0; i < n * n; i++) {
            float h = (zz[i] - zmin) / Math.max(1e-9f, t.heightRange);
            t.height[i] = h;
            float hole = Math.max(0, b1[i] - zz[i]) + 0.5f * Math.max(0, b2[i] - zz[i]);
            float a = 1 - Math.min(0.7f, hole / cav);
            a *= 0.5f + 0.5f * (float) Math.pow(h, 0.7);
            t.ao[i] = Math.max(0.12f, a);
        }
        // Normale aus der Höhe (m je m), nahtlos
        float pxn = look.tile / n;
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                float gx = (zz[y * n + (x + 1) % n] - zz[y * n + (x + n - 1) % n]) / (2 * pxn);
                float gy = (zz[((y + 1) % n) * n + x] - zz[((y + n - 1) % n) * n + x]) / (2 * pxn);
                float l = (float) Math.sqrt(gx * gx + gy * gy + 1);
                t.normal[2 * (y * n + x)] = -gx / l;
                t.normal[2 * (y * n + x) + 1] = -gy / l;
            }
        for (int i = 0; i < n * n; i++)
            for (int k = 0; k < 3; k++) { t.meanAlbedo[k] += t.albedo[3 * i + k] / (n * n); t.meanSeen[k] += t.albedo[3 * i + k] * t.ao[i] / (n * n); }
        return t;
    }

    /** Farbe entlang des Triebs: t 0 Grund, 0,5 Mitte, 1 Spitze; j Helligkeit, hue Verschiebung zu Gelb (+) oder Blau (−). */
    static void shade(MossLook m, float t, float j, float hue, float[] out) {
        float[] a = t < 0.5f ? m.base : m.mid, b = t < 0.5f ? m.mid : m.tip;
        float u = t < 0.5f ? t * 2 : (t - 0.5f) * 2;
        u = u * u * (3 - 2 * u);
        for (int k = 0; k < 3; k++) out[k] = (a[k] + (b[k] - a[k]) * u) * j;
        out[0] *= 1 + 0.6f * hue;
        out[2] *= 1 - 0.8f * hue;
    }

    private static void disc(int N, float cx, float cy, float rad, float zv, float[] c, float[] z, float[] col, boolean[] hit) {
        int x0 = (int) Math.floor(cx - rad), x1 = (int) Math.ceil(cx + rad), y0 = (int) Math.floor(cy - rad), y1 = (int) Math.ceil(cy + rad);
        float r2 = rad * rad;
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                if (dx * dx + dy * dy > r2) continue;
                put(N, x, y, zv, 1, c, z, col, hit);
            }
    }

    /** Blättchen: Ellipse von (sx, sy) aus in Richtung a, Länge len, Breite wid; Höhe steigt um tilt je Bildpunkt. */
    private static void ellipse(int N, float sx, float sy, float a, float len, float wid, float zv, float tilt, float[] c, float[] z, float[] col, boolean[] hit) {
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        float mx = sx + ca * len * 0.5f, my = sy + sa * len * 0.5f, hl = len * 0.5f, hw = Math.max(0.6f, wid * 0.5f);
        int R = (int) Math.ceil(hl + 1);
        for (int y = (int) Math.floor(my - R); y <= (int) Math.ceil(my + R); y++)
            for (int x = (int) Math.floor(mx - R); x <= (int) Math.ceil(mx + R); x++) {
                float dx = x + 0.5f - mx, dy = y + 0.5f - my;
                float u = (dx * ca + dy * sa) / hl, v = (-dx * sa + dy * ca) / hw;
                float d = u * u + v * v;
                if (d > 1) continue;
                // Rand des Blättchens etwas heller (dünner, durchscheinend), die Mitte (Rippe) etwas dunkler
                float edge = 0.92f + 0.16f * Math.abs(v);
                put(N, x, y, zv + tilt * (u + 1) * hl * 0.5f + (1 - d) * 0.0001f, edge, c, z, col, hit);
            }
    }

    private static void put(int N, int x, int y, float zv, float f, float[] c, float[] z, float[] col, boolean[] hit) {
        int i = Math.floorMod(y, N) * N + Math.floorMod(x, N);
        if (hit[i] && z[i] >= zv) return;
        z[i] = zv;
        hit[i] = true;
        col[3 * i] = c[0] * f; col[3 * i + 1] = c[1] * f; col[3 * i + 2] = c[2] * f;
    }

    /** Kachelbares Wertrauschen 0..1: je Periode ein Gitter mit Zufallswerten, weich (Hermite) verbunden. */
    static float[] tileNoise(int N, int[] periods, float[] weights, Random r) {
        float[] out = new float[N * N];
        float wsum = 0;
        for (float w : weights) wsum += w;
        for (int pi = 0; pi < periods.length; pi++) {
            int P = periods[pi];
            float[] g = new float[P * P];
            for (int i = 0; i < g.length; i++) g[i] = r.nextFloat();
            for (int y = 0; y < N; y++) {
                float fy = y * P / (float) N;
                int y0 = (int) fy;
                float ty = fy - y0;
                ty = ty * ty * (3 - 2 * ty);
                for (int x = 0; x < N; x++) {
                    float fx = x * P / (float) N;
                    int x0 = (int) fx;
                    float tx = fx - x0;
                    tx = tx * tx * (3 - 2 * tx);
                    float a = g[(y0 % P) * P + x0 % P], b = g[(y0 % P) * P + (x0 + 1) % P], cc = g[((y0 + 1) % P) * P + x0 % P], d = g[((y0 + 1) % P) * P + (x0 + 1) % P];
                    out[y * N + x] += weights[pi] / wsum * ((a + (b - a) * tx) + ((cc + (d - cc) * tx) - (a + (b - a) * tx)) * ty);
                }
            }
        }
        return out;
    }

    /** Kastenfilter mit Halbmesser rad, nahtlos (zweimal: fast Gauß). */
    static float[] blur(float[] a, int n, int rad) {
        float[] b = a.clone(), t = new float[n * n];
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++) {
                    float s = 0;
                    for (int k = -rad; k <= rad; k++) s += b[y * n + Math.floorMod(x + k, n)];
                    t[y * n + x] = s / (2 * rad + 1);
                }
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++) {
                    float s = 0;
                    for (int k = -rad; k <= rad; k++) s += t[Math.floorMod(y + k, n) * n + x];
                    b[y * n + x] = s / (2 * rad + 1);
                }
        }
        return b;
    }

    /** Leuchtdichte der gesehenen Farbe (Albedo · Verdeckung) je Bildpunkt. */
    public float[] seenLuma() {
        float[] y = new float[n * n];
        for (int i = 0; i < n * n; i++) y[i] = (0.2126f * albedo[3 * i] + 0.7152f * albedo[3 * i + 1] + 0.0722f * albedo[3 * i + 2]) * ao[i];
        return y;
    }
}
