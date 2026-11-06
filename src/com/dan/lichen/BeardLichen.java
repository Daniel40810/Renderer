package com.dan.lichen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Ein Büschel Bartflechte als Fäden (seit LituyaBay1958 W6 Runde 7): Hauptachsen vom Ansatz (0, 0, 0) aus, unter der
 * Schwere hängend oder abstehend, verworren, verzweigt, mit Seitenfasern. Maße in m, y nach oben (ein hängendes Büschel
 * reicht also ins Negative). Gestaltet [G] nach den Fotos in {@link LichenLook}.
 */
public final class BeardLichen {
    /** Ein Faden: Punkte (x, y, z), Dicke (m) und Farbe (linear) je Punkt, Stufe (0 Achse, 1 Zweig, 2 Faser). */
    public static final class Strand {
        public final float[] p, col;
        public final float[] r;
        public final int level;
        Strand(float[] p, float[] r, float[] col, int level) { this.p = p; this.r = r; this.col = col; this.level = level; }
        public int points() { return p.length / 3; }
    }

    public final LichenLook look;
    public final List<Strand> strands = new ArrayList<>();
    /** Umgebendes Kästchen: x von bis, y von bis, z von bis. */
    public final float[] box = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};

    private BeardLichen(LichenLook l) { look = l; }

    /** Ein Büschel nach look mit seed. */
    public static BeardLichen grow(LichenLook look, long seed) {
        BeardLichen b = new BeardLichen(look);
        Random r = new Random(seed);
        int axes = look.axesMin + r.nextInt(Math.max(1, look.axesMax - look.axesMin + 1));
        float jit = 1 + look.jitter * (2 * r.nextFloat() - 1);
        for (int a = 0; a < axes; a++) {
            float len = look.lengthMin + (look.lengthMax - look.lengthMin) * (float) Math.pow(r.nextFloat(), 1.6);
            double az = r.nextFloat() * 2 * Math.PI;
            // Anfangsrichtung: hängend nach unten (Schwere 1) oder schräg nach außen und oben (Schwere 0)
            float out = (0.35f + 0.5f * r.nextFloat()) * (0.4f + 0.6f * look.gravity);
            float dy = (float) (-look.gravity + (1 - look.gravity) * (0.5 + 0.8 * r.nextFloat()));
            float[] d = norm(new float[]{(float) Math.cos(az) * out, dy, (float) Math.sin(az) * out});
            b.axis(r, new float[]{(r.nextFloat() - 0.5f) * 0.006f, 0, (r.nextFloat() - 0.5f) * 0.006f}, d, len, look.thickness, 0, jit);
        }
        return b;
    }

    private void axis(Random r, float[] start, float[] dir, float len, float th, int level, float jit) {
        LichenLook L = look;
        float step = Math.min(0.004f, len / 24);
        int n = Math.max(3, (int) Math.ceil(len / step));
        float[] p = new float[3 * (n + 1)], rad = new float[n + 1], col = new float[3 * (n + 1)];
        float[] x = start.clone(), d = dir.clone();
        float pj = 1 + L.jitter * 0.5f * (2 * r.nextFloat() - 1);
        float nextFib = L.fibrilSpacing * r.nextFloat(), side = 1;
        float tw = (float) (r.nextFloat() * 2 * Math.PI);
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            p[3 * i] = x[0]; p[3 * i + 1] = x[1]; p[3 * i + 2] = x[2];
            rad[i] = th * (1 - 0.65f * t);
            shade(level == 0 ? 0.15f + 0.85f * t : 0.55f + 0.45f * t, jit * pj, col, 3 * i);
            include(x);
            if (i == n) break;
            // Seitenfasern (nicht an Fasern)
            if (level < 2) {
                nextFib -= step;
                while (nextFib <= 0) {
                    nextFib += L.fibrilSpacing * (0.6f + 0.8f * r.nextFloat());
                    float fl = L.fibrilMin + (L.fibrilMax - L.fibrilMin) * r.nextFloat() * (1 - 0.5f * t);
                    side = -side;
                    tw += 2.2f;
                    float[] perp = perpendicular(d, tw);
                    float ang = L.fibrilAngle * (0.8f + 0.4f * r.nextFloat());
                    float[] fd = norm(new float[]{d[0] * (float) Math.cos(ang) + perp[0] * (float) Math.sin(ang),
                            d[1] * (float) Math.cos(ang) + perp[1] * (float) Math.sin(ang), d[2] * (float) Math.cos(ang) + perp[2] * (float) Math.sin(ang)});
                    fibril(r, x.clone(), fd, fl, rad[i] * 0.45f, jit * pj);
                }
                // Verzweigung
                if (r.nextFloat() < L.branchPerM * step * (level == 0 ? 1 : 0.5f) && len * (1 - t) > 0.025f) {
                    float[] perp = perpendicular(d, (float) (r.nextFloat() * 2 * Math.PI));
                    float ba = L.branchAngle * (0.7f + 0.6f * r.nextFloat());
                    float[] bd = norm(new float[]{d[0] + perp[0] * (float) Math.tan(ba), d[1] + perp[1] * (float) Math.tan(ba), d[2] + perp[2] * (float) Math.tan(ba)});
                    axis(r, x.clone(), bd, len * (1 - t) * (0.45f + 0.5f * r.nextFloat()), rad[i] * 0.8f, 1, jit);
                }
            }
            // Richtung: Schwere zieht nach unten, Verworrenheit dreht zufällig
            float g = L.gravity * 0.08f;
            d[1] -= g;
            float k = L.tangle * step;
            d[0] += (r.nextFloat() - 0.5f) * 2 * k; d[1] += (r.nextFloat() - 0.5f) * k; d[2] += (r.nextFloat() - 0.5f) * 2 * k;
            d = norm(d);
            x[0] += d[0] * step; x[1] += d[1] * step; x[2] += d[2] * step;
        }
        strands.add(new Strand(p, rad, col, level));
    }

    private void fibril(Random r, float[] start, float[] d, float len, float th, float jit) {
        int n = 3;
        float[] p = new float[3 * (n + 1)], rad = new float[n + 1], col = new float[3 * (n + 1)];
        float[] x = start, dd = d.clone();
        for (int i = 0; i <= n; i++) {
            p[3 * i] = x[0]; p[3 * i + 1] = x[1]; p[3 * i + 2] = x[2];
            rad[i] = th * (1 - 0.7f * i / (float) n);
            shade(0.7f + 0.3f * i / n, jit, col, 3 * i);
            include(x);
            dd[1] -= look.gravity * 0.15f;
            float[] cu = perpendicular(dd, r.nextFloat() * 6.283f);
            dd[0] += cu[0] * 0.35f; dd[1] += cu[1] * 0.35f; dd[2] += cu[2] * 0.35f;
            dd = norm(dd);
            x = new float[]{x[0] + dd[0] * len / n, x[1] + dd[1] * len / n, x[2] + dd[2] * len / n};
        }
        strands.add(new Strand(p, rad, col, 2));
    }

    private void shade(float t, float j, float[] out, int o) {
        float[] a = t < 0.5f ? look.base : look.mid, b = t < 0.5f ? look.mid : look.tip;
        float u = t < 0.5f ? t * 2 : (t - 0.5f) * 2;
        for (int k = 0; k < 3; k++) out[o + k] = (a[k] + (b[k] - a[k]) * u) * j;
    }

    private void include(float[] x) {
        box[0] = Math.min(box[0], x[0]); box[1] = Math.max(box[1], x[0]);
        box[2] = Math.min(box[2], x[1]); box[3] = Math.max(box[3], x[1]);
        box[4] = Math.min(box[4], x[2]); box[5] = Math.max(box[5], x[2]);
    }

    static float[] norm(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1e-9f ? new float[]{0, -1, 0} : new float[]{v[0] / l, v[1] / l, v[2] / l};
    }

    /** Eine Richtung senkrecht zu d, um d gedreht um den Winkel a. */
    static float[] perpendicular(float[] d, float a) {
        float[] u = Math.abs(d[1]) < 0.9f ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        float[] e1 = norm(new float[]{d[1] * u[2] - d[2] * u[1], d[2] * u[0] - d[0] * u[2], d[0] * u[1] - d[1] * u[0]});
        float[] e2 = {d[1] * e1[2] - d[2] * e1[1], d[2] * e1[0] - d[0] * e1[2], d[0] * e1[1] - d[1] * e1[0]};
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[]{e1[0] * c + e2[0] * s, e1[1] * c + e2[1] * s, e1[2] * c + e2[2] * s};
    }

    /** Gesamtlänge aller Fäden (m), je Stufe. */
    public float[] lengths() {
        float[] L = new float[3];
        for (Strand s : strands)
            for (int i = 1; i < s.points(); i++) {
                float dx = s.p[3 * i] - s.p[3 * i - 3], dy = s.p[3 * i + 1] - s.p[3 * i - 2], dz = s.p[3 * i + 2] - s.p[3 * i - 1];
                L[s.level] += (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
        return L;
    }
}
