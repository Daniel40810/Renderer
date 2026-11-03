package com.dan.bear.demo;

import com.dan.bear.BearBody;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Ein kleiner Software-Rasterer für Demo und Bilder ohne Grafikkarte: Bären aus {@link BearBody} mit Tiefenpuffer,
 * Licht von der Sonne und vom Himmel, Fell wie {@code gl/bear.frag} in grob (Rücken dunkler, Schnauze heller, Beine
 * dunkel, nass dunkler). Für die Abnahme des Körpers, nicht für die Szene.
 */
public final class Soft {
    final int w, h;
    final int[] rgb;
    final float[] depth;
    /** Kamera: Auge, Ziel, Blickwinkel senkrecht (Grad). */
    double[] eye = {4, 1.6, 5}, at = {0, 0.6, 0};
    double fovDeg = 32;
    double[] sun = norm(new double[]{-0.5, 0.75, 0.45});
    private double[] fx, fy, fz;
    private double focal;
    static final float[] MESH = BearBody.mesh();

    public Soft(int w, int h) {
        this.w = w;
        this.h = h;
        rgb = new int[w * h];
        depth = new float[w * h];
        clear(0xFF9DB4C6, 0xFF5B6A58);
    }

    /** Himmel oben, Grund unten (Horizont in der Mitte, grob). */
    public void clear(int sky, int ground) {
        Arrays.fill(depth, Float.MAX_VALUE);
        for (int y = 0; y < h; y++) {
            double t = y / (double) h;
            int c = t < 0.5 ? mix(0xFFCFDCE6, sky, t * 2) : mix(ground, 0xFF3C4636, (t - 0.5) * 2);
            Arrays.fill(rgb, y * w, (y + 1) * w, c);
        }
    }

    public void look(double ex, double ey, double ez, double ax, double ay, double az) {
        eye = new double[]{ex, ey, ez};
        at = new double[]{ax, ay, az};
    }

    private void setup() {
        fz = norm(new double[]{at[0] - eye[0], at[1] - eye[1], at[2] - eye[2]});
        fx = norm(cross(fz, new double[]{0, 1, 0}));
        fy = cross(fx, fz);
        focal = h / 2.0 / Math.tan(Math.toRadians(fovDeg / 2));
    }

    /** Ebener Grund bei y (ein Gitter, damit die Füße zu sehen sind). */
    public void ground(double y, double half, int color) {
        setup();
        int n = 24;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                double x0 = at[0] - half + 2 * half * i / n, x1 = at[0] - half + 2 * half * (i + 1) / n;
                double z0 = at[2] - half + 2 * half * j / n, z1 = at[2] - half + 2 * half * (j + 1) / n;
                int c = ((i + j) & 1) == 0 ? color : mix(color, 0xFF000000, 0.08);
                double[] a = {x0, y, z0}, b = {x1, y, z0}, cc = {x1, y, z1}, d = {x0, y, z1};
                tri(a, b, cc, c, c, c);
                tri(a, cc, d, c, c, c);
            }
    }

    /**
     * Ein Bär: Hüfte bei (x, y, z), Länge L, Richtung h (rad, 0 = Norden = −z, im Uhrzeigersinn), Haltung p, Fell 0..1,
     * Nässe 0..1.
     */
    public void bear(double x, double y, double z, double L, double hd, float[] p, double tone, double wet) {
        setup();
        double[] R = {Math.cos(hd), 0, Math.sin(hd)}, F = {Math.sin(hd), 0, -Math.cos(hd)};
        int F9 = BearBody.FLOATS;
        double[][] P = new double[3][];
        int[] C = new int[3];
        for (int i = 0; i + 3 * F9 <= MESH.length; i += 3 * F9) {
            for (int k = 0; k < 3; k++) {
                int o = i + k * F9;
                double[] d = BearBody.deform(MESH[o], MESH[o + 1], MESH[o + 2], MESH[o + 3], MESH[o + 4], MESH[o + 5], MESH[o + 6], (int) MESH[o + 7], MESH[o + 8], p);
                double wx = x + (R[0] * d[0] + F[0] * d[2]) * L, wy = y + d[1] * L, wz = z + (R[2] * d[0] + F[2] * d[2]) * L;
                double[] n = {R[0] * d[3] + F[0] * d[5], d[4], R[2] * d[3] + F[2] * d[5]};
                P[k] = new double[]{wx, wy, wz};
                C[k] = shade(n, MESH[o + 6], (int) MESH[o + 7], MESH[o + 8], MESH[o + 1], tone, wet, MESH[o + 0]);
            }
            tri(P[0], P[1], P[2], C[0], C[1], C[2]);
        }
    }

    /** Fell grob wie bear.frag. */
    int shade(double[] n, double s, int part, double ang, double oy, double tone, double wet, double ox) {
        n = norm(n);
        double[] base = furColor(s, part, ang, tone);
        double k = mixd(1.0, 0.55, wet);
        double ns = Math.max(0, dot(n, sun)), wrap = Math.max(0, (dot(n, sun) + 0.3) / 1.3) * 0.25, up = n[1] * 0.5 + 0.5;
        double l = ns * 0.85 + wrap + 0.30 * up + 0.08;
        return rgb(base[0] * k * l, base[1] * k * l, base[2] * k * l);
    }

    /** Fellfarbe (linear, 0..1) an einer Stelle: Teil, Stelle s, Winkel um die Achse, Fellton des Tiers. */
    public static double[] furColor(double s, int part, double ang, double tone) {
        double[] dark = {mixd(0.045, 0.12, tone), mixd(0.028, 0.075, tone), mixd(0.016, 0.040, tone)};
        double[] light = {mixd(0.16, 0.42, tone), mixd(0.10, 0.28, tone), mixd(0.05, 0.14, tone)};
        double top = Math.cos(ang);
        double[] c;
        if (part == BearBody.BODY) {
            double t = smooth(-0.6, 0.8, top) * 0.55 + 0.25;
            c = lerp(dark, light, t);
            // Schnauze heller, Nase schwarz
            if (s < 0.09) c = lerp(c, new double[]{light[0] * 1.15, light[1] * 1.05, light[2] * 0.95}, smooth(0.09, 0.03, s) * 0.8);
            if (s < 0.012) c = new double[]{0.02, 0.018, 0.016};
            // Buckel und Rücken mit hellen Spitzen (grizzled)
            if (s > 0.25 && s < 0.5 && top > 0.4) c = lerp(c, light, 0.25);
        } else if (part == BearBody.EAR) {
            c = lerp(dark, light, 0.35);
        } else {
            int seg = (part - BearBody.LEG0) % 3;
            c = lerp(dark, light, seg == 0 ? 0.2 : 0.05);
            if (seg == 2 && ang > 0.5) c = new double[]{0.05, 0.045, 0.04};
        }
        return c;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private void tri(double[] a, double[] b, double[] c, int ca, int cb, int cc) {
        double[] pa = project(a), pb = project(b), pc = project(c);
        if (pa == null || pb == null || pc == null) return;
        int minx = (int) Math.max(0, Math.floor(Math.min(pa[0], Math.min(pb[0], pc[0])))), maxx = (int) Math.min(w - 1, Math.ceil(Math.max(pa[0], Math.max(pb[0], pc[0]))));
        int miny = (int) Math.max(0, Math.floor(Math.min(pa[1], Math.min(pb[1], pc[1])))), maxy = (int) Math.min(h - 1, Math.ceil(Math.max(pa[1], Math.max(pb[1], pc[1]))));
        double area = (pb[0] - pa[0]) * (pc[1] - pa[1]) - (pc[0] - pa[0]) * (pb[1] - pa[1]);
        if (Math.abs(area) < 1e-9) return;
        for (int y = miny; y <= maxy; y++)
            for (int x = minx; x <= maxx; x++) {
                double px = x + 0.5, py = y + 0.5;
                double w0 = ((pb[0] - px) * (pc[1] - py) - (pc[0] - px) * (pb[1] - py)) / area;
                double w1 = ((pc[0] - px) * (pa[1] - py) - (pa[0] - px) * (pc[1] - py)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float d = (float) (w0 * pa[2] + w1 * pb[2] + w2 * pc[2]);
                int i = y * w + x;
                if (d >= depth[i]) continue;
                depth[i] = d;
                rgb[i] = bary(ca, cb, cc, w0, w1, w2);
            }
    }

    private double[] project(double[] p) {
        double dx = p[0] - eye[0], dy = p[1] - eye[1], dz = p[2] - eye[2];
        double z = dx * fz[0] + dy * fz[1] + dz * fz[2];
        if (z < 0.05) return null;
        double x = dx * fx[0] + dy * fx[1] + dz * fx[2], y = dx * fy[0] + dy * fy[1] + dz * fy[2];
        return new double[]{w / 2.0 + x / z * focal, h / 2.0 - y / z * focal, z};
    }

    public BufferedImage image() {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        im.setRGB(0, 0, w, h, rgb, 0, w);
        return im;
    }

    // ------------------------------------------------------------------ Hilfen
    static int rgb(double r, double g, double b) {
        return 0xFF000000 | (gam(r) << 16) | (gam(g) << 8) | gam(b);
    }

    static int gam(double v) { return (int) Math.round(255 * Math.pow(Math.max(0, Math.min(1, v)), 1 / 2.2)); }

    static int bary(int a, int b, int c, double u, double v, double t) {
        int r = (int) (((a >> 16) & 255) * u + ((b >> 16) & 255) * v + ((c >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * u + ((b >> 8) & 255) * v + ((c >> 8) & 255) * t);
        int bl = (int) ((a & 255) * u + (b & 255) * v + (c & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static double mixd(double a, double b, double t) { return a + (b - a) * t; }

    static double[] lerp(double[] a, double[] b, double t) { return new double[]{mixd(a[0], b[0], t), mixd(a[1], b[1], t), mixd(a[2], b[2], t)}; }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }

    static double[] norm(double[] a) { double l = Math.sqrt(dot(a, a)); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
}
