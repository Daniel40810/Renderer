package com.dan.lamp.demo;

import com.dan.lamp.Lamp;
import com.dan.lamp.Lamps;
import com.dan.lamp.Wet;
import com.dan.lamp.Windows;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Eine Promenade bei Nacht auf der CPU (für Standbilder ohne Grafikkarte): links Häuser mit Fensterlicht nach Uhrzeit,
 * davor nasses oder trockenes Pflaster unter Laternen, rechts das Meer, alles in Leuchtdichte (cd/m²) gerechnet und
 * erst am Ende belichtet. Dieselben Formeln wie {@code gl/lamp.glsl}.
 */
public final class LampPicture {
    public final int w, h;
    /** Auge, Blickrichtung (Azimut, Neigung in Grad), Bildwinkel (Grad, senkrecht). */
    public double ex = 0.5, ey = 1.7, ez = 6, heading = -8, pitch = -2, fovDeg = 50;
    public double exposure = 0.22, hour = 21, wetness = 1, wind = 3;
    /** Hausfront (x), Promenade bis (x, Kante zum Meer). */
    public double facadeX = -7, seaX = 7;
    public long seed = 1;
    public final Lamps lamps = new Lamps();

    public LampPicture(int w, int h) {
        this.w = w;
        this.h = h;
        lamps.row(-5.5, 2, -5.5, -400, 0, 22, 4.0, Lamp.Type.LED_4000K, Lamp.Distribution.ROUND, 3200, 1);
        lamps.row(seaX - 0.6, -9, seaX - 0.6, -400, 0, 22, 1.0, Lamp.Type.LED_4000K, Lamp.Distribution.DOWN, 450, -1);
        lamps.add(new Lamp(30, 4.5, -160, Lamp.Type.HPS, Lamp.Distribution.ROUND, 9000));      // Laterne am Ende der Seebrücke
    }

    public BufferedImage render() {
        lamps.step(0, 0, hour);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double[] fw = dir(heading, pitch), right = norm(cross(fw, new double[]{0, 1, 0})), up = cross(right, fw);
        double t = Math.tan(Math.toRadians(fovDeg) / 2), asp = (double) w / h;
        double[] eye = {ex, ey, ez};
        float[][] L = new float[3][w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double sx = (2 * (x + 0.5) / w - 1) * t * asp, sy = (1 - 2 * (y + 0.5) / h) * t;
                double[] d = norm(new double[]{fw[0] + right[0] * sx + up[0] * sy, fw[1] + right[1] * sx + up[1] * sy, fw[2] + right[2] * sx + up[2] * sy});
                double[] c = shade(eye, d);
                int i = y * w + x;
                L[0][i] = (float) c[0]; L[1][i] = (float) c[1]; L[2][i] = (float) c[2];
            }
        // Leuchtenköpfe: Gauß, der genau I/d² trägt
        double pxPerRad = h / (2 * t);
        for (Lamp l : lamps.all()) {
            double vx = l.x - ex, vy = l.y - ey, vz = l.z - ez, dd = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double cx = dot(new double[]{vx, vy, vz}, fw);
            if (cx <= 0.5) continue;
            double px = (dot(new double[]{vx, vy, vz}, right) / cx / (t * asp) + 1) * w / 2, py = (1 - dot(new double[]{vx, vy, vz}, up) / cx / t) * h / 2;
            double I = l.candela(-vx / dd, -vy / dd, -vz / dd);
            if (I <= 0) continue;
            double sigma = Math.max(Math.sqrt(l.area / Math.PI) / dd * pxPerRad, 0.8);
            double eLux = I / (dd * dd), lumPerPx = eLux * pxPerRad * pxPerRad / (2 * Math.PI * sigma * sigma);
            double[] col = l.rgb();
            int r = (int) Math.ceil(3 * sigma);
            for (int yy = (int) py - r; yy <= (int) py + r; yy++)
                for (int xx = (int) px - r; xx <= (int) px + r; xx++) {
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                    double q = ((xx + 0.5 - px) * (xx + 0.5 - px) + (yy + 0.5 - py) * (yy + 0.5 - py)) / (sigma * sigma);
                    if (q > 9) continue;
                    double v = lumPerPx * Math.exp(-0.5 * q);
                    int i = yy * w + xx;
                    L[0][i] += (float) (v * col[0]); L[1][i] += (float) (v * col[1]); L[2][i] += (float) (v * col[2]);
                }
        }
        for (int i = 0; i < w * h; i++) im.setRGB(i % w, i / w, (tone(L[0][i] * exposure) << 16) | (tone(L[1][i] * exposure) << 8) | tone(L[2][i] * exposure));
        return im;
    }

    /** Leuchtdichte (cd/m², lineares RGB) längs des Blicks d. */
    double[] shade(double[] e, double[] d) {
        double tf = d[0] < -1e-6 ? (facadeX - e[0]) / d[0] : Double.MAX_VALUE, tg = d[1] < -1e-6 ? -e[1] / d[1] : Double.MAX_VALUE;
        if (tf < tg) {
            double y = e[1] + d[1] * tf, z = e[2] + d[2] * tf;
            if (y < 13) return facade(new double[]{facadeX, y, z}, roofline(z));
        }
        if (tg < Double.MAX_VALUE) {
            double[] p = {e[0] + d[0] * tg, 0, e[2] + d[2] * tg};
            return p[0] < seaX ? ground(p, e) : sea(p, e, d);
        }
        return sky(d);
    }

    /** Traufhöhe je Haus (12 m breit): 2 bis 4 Geschosse. */
    double roofline(double z) {
        int house = (int) Math.floor(-z / 12);
        return 3 * (2 + Math.floorMod(house * 7 + 3, 3)) + 0.6;
    }

    double[] facade(double[] p, double roof) {
        if (p[1] > roof) return sky(norm(new double[]{p[0] - ex, p[1] - ey, p[2] - ez}));
        int house = (int) Math.floor(-p[2] / 12);
        double u = Math.floorMod((long) Math.floor(-p[2] * 100), 1200) / 100.0, v = p[1];
        int col = (int) Math.floor(u / 3), floor = (int) Math.floor(v / 3);
        double fu = u - col * 3, fv = v - floor * 3;
        boolean shop = floor == 0 && house % 3 == 1;
        boolean glass = shop ? fu > 0.3 && fu < 2.7 && fv > 0.3 && fv < 2.6 : fu > 0.9 && fu < 2.1 && fv > 0.9 && fv < 2.4;
        double[] e = lamps.illuminance(p[0], p[1], p[2], 1, 0, 0);
        if (glass) {
            Windows.Use use = shop ? Windows.Use.SHOP : house % 2 == 0 ? Windows.Use.HOLIDAY : Windows.Use.HOME;
            int win = floor * 4 + col;
            if (Windows.lit(use, house, win, hour, seed)) {
                double[] r = Windows.radiance(use, house, win, seed);
                double sill = fv < (shop ? 0.5 : 1.1) ? 0.6 : 1;             // Fensterbank, Sprossen
                return new double[]{r[0] * sill, r[1] * sill, r[2] * sill};
            }
            return new double[]{e[0] * 0.02 + 0.0004, e[1] * 0.02 + 0.0005, e[2] * 0.02 + 0.0008};
        }
        double a = (house % 4 == 0 ? 0.55 : house % 4 == 1 ? 0.35 : house % 4 == 2 ? 0.45 : 0.28) / Math.PI;
        return new double[]{e[0] * a + 0.0003, e[1] * a + 0.0004, e[2] * a + 0.0006};
    }

    double[] ground(double[] p, double[] e) {
        double[] c = new double[3];
        boolean cobble = Math.floorMod((long) Math.floor(p[0] * 4), 2) == Math.floorMod((long) Math.floor(p[2] * 4), 2);
        Wet.Surface s = Wet.Surface.COBBLE;
        double wet = wetness * (cobble ? 1 : 0.93);
        List<Lamp> near = lamps.near(p[0], p[2]);
        for (Lamp l : near) {
            double v = Wet.luminance(l, p, e, s, wet);
            if (v <= 0) continue;
            double[] k = l.rgb();
            c[0] += v * k[0]; c[1] += v * k[1]; c[2] += v * k[2];
        }
        c[0] += 0.0003; c[1] += 0.0004; c[2] += 0.0007;
        return c;
    }

    double[] sea(double[] p, double[] e, double[] d) {
        double[] c = new double[3];
        for (Lamp l : lamps.near(p[0], p[2])) {
            double v = Wet.waterLuminance(l, p, e, wind);
            if (v <= 0) continue;
            double[] k = l.rgb();
            c[0] += v * k[0]; c[1] += v * k[1]; c[2] += v * k[2];
        }
        double[] sk = sky(new double[]{d[0], -d[1], d[2]});
        double f = Wet.fresnel(0.02, -d[1]);
        c[0] += sk[0] * f; c[1] += sk[1] * f; c[2] += sk[2] * f;
        return c;
    }

    /** Nachthimmel über der Stadt (cd/m²): Lichtglocke am Horizont, nach 22 Uhr dunkler [G]. */
    double[] sky(double[] d) {
        double up = Math.max(0, d[1]), glow = Math.exp(-up * 8), k = hour > 22 || hour < 5 ? 0.6 : 1;
        return new double[]{(0.0012 + 0.006 * glow) * k, (0.0016 + 0.005 * glow) * k, (0.004 + 0.004 * glow) * k};
    }

    static double[] dir(double headingDeg, double pitchDeg) {
        double h = Math.toRadians(headingDeg), p = Math.toRadians(pitchDeg);
        return new double[]{Math.sin(h) * Math.cos(p), Math.sin(p), -Math.cos(h) * Math.cos(p)};
    }

    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double[] norm(double[] a) { double l = Math.sqrt(dot(a, a)); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }

    /** ACES-Näherung (Narkowicz) und sRGB-Gamma. */
    static int tone(double v) {
        v = Math.max(0, v);
        double t = (v * (2.51 * v + 0.03)) / (v * (2.43 * v + 0.59) + 0.14);
        return (int) Math.max(0, Math.min(255, Math.round(255 * Math.pow(Math.min(1, t), 1 / 2.2))));
    }
}
