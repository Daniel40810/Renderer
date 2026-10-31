package com.dan.firework.demo;

import com.dan.firework.Firework;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

/**
 * Ein Bild des Feuerwerks auf dem Prozessor (seit Timmendorf Phase 11), für die Vorführung und Statusbilder ohne
 * Grafikkarte: Nachthimmel, Wasser mit Spiegelung, Rauch im Licht der Bomben, Sterne als Lichtpunkte mit Schein,
 * Funkenschweife und auf Wunsch die Langzeitbelichtung (die Spuren der letzten Sekunden mit ihrer Lichtstärke).
 * Gerechnet wird in Beleuchtungsstärke je Bildpunkt (lx), daraus Leuchtdichte, Belichtung und eine Filmkurve – wie
 * {@code gl/FireworkGl} auf der Grafikkarte.
 */
public final class FireworkPicture {
    public final int w, h;
    /** Auge (m) und Ziel; Öffnungswinkel senkrecht (Grad); Wasserspiegel (m). */
    public double ex = 0, ey = 3, ez = 600, tx = 0, ty = 105, tz = 0, fovDeg = 34, water = 0;
    /** Belichtung (Leuchtdichte in cd/m² × Belichtung = 1 ist Weiß), Langzeitbelichtung (s, 0 = aus). */
    public double exposure = 0.004, longExposure = 0;
    /** Anteil, den das Wasser spiegelt, und wie weit die Wellen die Spiegelung senkrecht verziehen (Grad). */
    public double reflect = 0.12, ripple = 1.6;
    private final float[] r, g, b;
    private double[] fw, rt, up;
    private double scale, pixSr;

    public FireworkPicture(int w, int h) {
        this.w = w;
        this.h = h;
        r = new float[w * h];
        g = new float[w * h];
        b = new float[w * h];
    }

    private void camera() {
        double fx = tx - ex, fy = ty - ey, fz = tz - ez, l = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fw = new double[]{fx / l, fy / l, fz / l};
        double rx = -fw[2], rz = fw[0], rl = Math.hypot(rx, rz);
        rt = new double[]{rx / rl, 0, rz / rl};
        up = new double[]{rt[1] * fw[2] - rt[2] * fw[1], rt[2] * fw[0] - rt[0] * fw[2], rt[0] * fw[1] - rt[1] * fw[0]};
        if (up[1] < 0) { up = new double[]{-up[0], -up[1], -up[2]}; rt = new double[]{-rt[0], -rt[1], -rt[2]}; }
        scale = h / 2.0 / Math.tan(Math.toRadians(fovDeg) / 2);
        double px = 1 / scale;
        pixSr = px * px;
    }

    /** Bildpunkt (x, y) und Entfernung eines Orts; null hinter dem Auge. */
    double[] project(double x, double y, double z) {
        double dx = x - ex, dy = y - ey, dz = z - ez;
        double c = dx * fw[0] + dy * fw[1] + dz * fw[2];
        if (c < 0.5) return null;
        double sx = dx * rt[0] + dy * rt[1] + dz * rt[2], sy = dx * up[0] + dy * up[1] + dz * up[2];
        return new double[]{w / 2.0 + sx / c * scale, h / 2.0 - sy / c * scale, Math.sqrt(dx * dx + dy * dy + dz * dz)};
    }

    /** Das Bild zur jetzigen Zeit des Feuerwerks. */
    public BufferedImage render(Firework f) {
        camera();
        java.util.Arrays.fill(r, 0);
        java.util.Arrays.fill(g, 0);
        java.util.Arrays.fill(b, 0);
        double[] hz = project(ex + fw[0] * 5000, water, ez + fw[2] * 5000);
        int horizon = hz == null ? h : (int) Math.round(hz[1]);
        List<Firework.Light> lights = f.lights();
        // Rauch: Scheiben, beleuchtet von den Bomben (Albedo 0,6, gleichmäßig gestreut), vor dem Himmel
        float[] sr = new float[w * h], sg = new float[w * h], sb = new float[w * h], sa = new float[w * h];
        List<Firework.Smoke> sm = new java.util.ArrayList<>(f.smoke());
        sm.sort((p, q) -> Double.compare(dist(q), dist(p)));
        for (Firework.Smoke s : sm) {
            double[] p = project(s.x, s.y, s.z);
            if (p == null) continue;
            double rad = s.radius / p[2] * scale, op = Math.min(0.9, s.opacity());
            if (rad < 0.3 || op < 0.003) continue;
            double[] e = new double[3];
            for (Firework.Light l : lights) {
                double dx = l.x() - s.x, dy = l.y() - s.y, dz = l.z() - s.z, d2 = Math.max(dx * dx + dy * dy + dz * dz, s.radius * s.radius);
                e[0] += l.r() / d2; e[1] += l.g() / d2; e[2] += l.b() / d2;
            }
            double k = 0.6 / Math.PI;
            double lr = e[0] * k + 0.0006, lg = e[1] * k + 0.0007, lb = e[2] * k + 0.001;
            int x0 = (int) Math.max(0, p[0] - rad), x1 = (int) Math.min(w - 1, p[0] + rad), y0 = (int) Math.max(0, p[1] - rad), y1 = (int) Math.min(h - 1, p[1] + rad);
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++) {
                    double q = ((x - p[0]) * (x - p[0]) + (y - p[1]) * (y - p[1])) / (rad * rad);
                    if (q >= 1) continue;
                    double a = op * (1 - q) * (1 - q);
                    int i = y * w + x;
                    sr[i] = (float) (sr[i] * (1 - a) + lr * a);
                    sg[i] = (float) (sg[i] * (1 - a) + lg * a);
                    sb[i] = (float) (sb[i] * (1 - a) + lb * a);
                    sa[i] = (float) (sa[i] * (1 - a) + a);
                }
        }
        // Sterne, Schweife, Langzeitbelichtung; dazu im Wasser gespiegelt
        Random jit = new Random(5);
        float[] pt = new float[4];
        for (Firework.Star st : f.stars()) {
            double[] c = st.salt.rgb();
            double cd = st.candela();
            if (cd > 0) splat(st.x, st.y, st.z, cd, c, 1.0, jit, horizon);
            int n = st.trailCount;
            double keep = longExposure > 0 ? longExposure : 0.45;
            int m = (int) Math.min(n, Math.ceil(keep / Firework.TRAIL_STEP));
            for (int i = 1; i < m; i++) {
                st.trailPoint(i - 1, pt);
                double ax = pt[0], ay = pt[1], az = pt[2], ac = pt[3];
                st.trailPoint(i, pt);
                double age = i * Firework.TRAIL_STEP, weight;
                if (longExposure > 0) weight = Firework.TRAIL_STEP / longExposure * 30;   // gleiche Helligkeit je Sekunde wie ein Bild
                else weight = st.sparks * 0.35 * Math.exp(-age / 0.18);                     // Funken kühlen ab
                if (weight <= 0 || (ac <= 0 && pt[3] <= 0)) continue;
                int sub = 4;
                for (int k = 0; k < sub; k++) {
                    double t = (k + 0.5) / sub;
                    double cdk = (ac * (1 - t) + pt[3] * t) * weight / sub;
                    double[] cc = longExposure > 0 || st.sparks < 0.5 ? c : mix(c, Firework.Salt.GOLD.rgb(), 0.6);
                    if (cdk > 0) splat(ax + (pt[0] - ax) * t, ay + (pt[1] - ay) * t, az + (pt[2] - az) * t, cdk, cc, 0.8, jit, horizon);
                }
            }
        }
        // Blitze und Lichter nicht noch einmal: die Sterne sind das Licht; der Blitz als großer, schwacher Schein
        for (Firework.Light l : lights) if (l.r() == l.g()) splat(l.x(), l.y(), l.z(), l.cd() * 0.02, new double[]{1, 1, 1}, 18, jit, horizon);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            double t = Math.max(0, Math.min(1, (double) (horizon - y) / Math.max(1, horizon)));
            double skyR = 0.0008 + 0.002 * (1 - t), skyG = 0.0012 + 0.0025 * (1 - t), skyB = 0.003 + 0.004 * (1 - t);   // cd/m², Nacht kurz nach der Dämmerung
            boolean sea = y > horizon;
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                double br = sea ? 0.0004 : skyR, bg = sea ? 0.0006 : skyG, bb = sea ? 0.0012 : skyB;
                if (sea) { double fres = 0.02 + 0.3 * Math.exp(-(y - horizon) / 25.0); br += skyR * fres * 8; bg += skyG * fres * 8; bb += skyB * fres * 8; }
                double a = sa[i];
                br = br * (1 - a) + sr[i];
                bg = bg * (1 - a) + sg[i];
                bb = bb * (1 - a) + sb[i];
                double lr = br + r[i] / pixSr, lg = bg + g[i] / pixSr, lb = bb + b[i] / pixSr;
                im.setRGB(x, y, (tone(lr * exposure) << 16) | (tone(lg * exposure) << 8) | tone(lb * exposure));
            }
        }
        return im;
    }

    private double dist(Firework.Smoke s) { return Math.sqrt((s.x - ex) * (s.x - ex) + (s.y - ey) * (s.y - ey) + (s.z - ez) * (s.z - ez)); }

    static double[] mix(double[] a, double[] b, double t) { return new double[]{a[0] * (1 - t) + b[0] * t, a[1] * (1 - t) + b[1] * t, a[2] * (1 - t) + b[2] * t}; }

    /**
     * Ein Lichtpunkt mit Lichtstärke cd in der Farbe c (Leuchtdichte 1): Beleuchtungsstärke cd/d² als Gaußfleck (σ in
     * Bildpunkten) und ein Schein von 3 % mit zehnfacher Breite (Streulicht im Auge); im Wasser gespiegelt.
     */
    void splat(double x, double y, double z, double cd, double[] c, double sigma, Random jit, int horizon) {
        double[] p = project(x, y, z);
        if (p != null) {
            double e = cd / (p[2] * p[2]);
            gauss(p[0], p[1], e * 0.97, c, sigma);
            gauss(p[0], p[1], e * 0.03, c, sigma * 10);
        }
        if (reflect <= 0 || y < water) return;
        double[] q = project(x, 2 * water - y, z);
        if (q == null || q[1] < horizon) return;
        double e = cd / (q[2] * q[2]) * reflect / 3, dy = Math.toRadians(ripple) * scale;
        for (int k = 0; k < 3; k++) gauss(q[0] + jit.nextGaussian() * 0.6, q[1] + jit.nextGaussian() * dy, e, c, sigma * 1.5);
    }

    private void gauss(double cx, double cy, double e, double[] c, double s) {
        int rad = (int) Math.ceil(3 * s);
        int x0 = (int) Math.floor(cx) - rad, y0 = (int) Math.floor(cy) - rad;
        double norm = 0;
        for (int y = y0; y <= y0 + 2 * rad + 1; y++)
            for (int x = x0; x <= x0 + 2 * rad + 1; x++) {
                double d2 = (x + 0.5 - cx) * (x + 0.5 - cx) + (y + 0.5 - cy) * (y + 0.5 - cy);
                norm += Math.exp(-d2 / (2 * s * s));
            }
        if (norm <= 0) return;
        for (int y = Math.max(0, y0); y <= Math.min(h - 1, y0 + 2 * rad + 1); y++)
            for (int x = Math.max(0, x0); x <= Math.min(w - 1, x0 + 2 * rad + 1); x++) {
                double d2 = (x + 0.5 - cx) * (x + 0.5 - cx) + (y + 0.5 - cy) * (y + 0.5 - cy);
                double k = e * Math.exp(-d2 / (2 * s * s)) / norm;
                int i = y * w + x;
                r[i] += (float) (k * c[0]);
                g[i] += (float) (k * c[1]);
                b[i] += (float) (k * c[2]);
            }
    }

    /** Filmkurve (ACES nach Narkowicz) und Gamma 2,2. */
    static int tone(double v) {
        v = Math.max(0, v);
        double t = (v * (2.51 * v + 0.03)) / (v * (2.43 * v + 0.59) + 0.14);
        return (int) Math.max(0, Math.min(255, Math.round(255 * Math.pow(Math.min(1, t), 1 / 2.2))));
    }
}
