package com.dan.shutter.demo;

import com.dan.shutter.Pupil;
import com.dan.shutter.Shutter;
import com.dan.shutter.SubFrames;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Demo von com.dan.shutter ohne Grafikkarte: dieselbe Rechnung wie die Kino-Stufe, auf dem Prozessor, an drei Tafeln.
 * <ol>
 * <li><b>Das Wagenrad</b>: ein Rad mit acht Speichen, 2,9 Umdrehungen je Sekunde, gefilmt mit 24 B/s und 180°: sechs Bilder
 * hintereinander. Die rote Speiche wandert vorwärts, das Muster der Speichen scheinbar langsam rückwärts (−0,1 U/s); jede
 * Speiche ist um ihren Weg in 1/48 s verwischt.</li>
 * <li><b>Der Verschluss</b>: ein Schwenk über senkrechte Pfähle mit 1/1000 s, 1/48 s (180°) und 1/24 s (360°): die Länge
 * der Unschärfe, und im Öffnungsgang die leichte Neigung (die Kante läuft quer über das Fenster).</li>
 * <li><b>Die Pupille</b>: ferne Lichter über das Bild eines 24 mm f/1,4 (zum Rand schneiden die Fassungen die Kreise zum
 * Katzenauge) und Lichter in sechs Abständen vor und hinter der Schärfe eines 85 mm f/1,4, durch eine Blende mit 7 geraden
 * Lamellen, 1 024 Teilbilder: die Kreise haben die Form der Blende.</li>
 * </ol>
 * {@code --bild datei.png}; ohne Argument {@code shutter_demo.png}.
 */
public final class ShutterDemo {
    /** Teilbilder für die Tafel der Pupille. */
    static final int NB = 1024;
    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        String out = "shutter_demo.png";
        for (int i = 0; i < a.length - 1; i++) if (a[i].equals("--bild")) out = a[i + 1];
        int pw = 300, gap = 10;
        BufferedImage img = new BufferedImage(6 * pw + 7 * gap, 3 * (pw + 40) + 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(12, 18, 24));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        Shutter sh = new Shutter(24, 180);
        sh.rolling = false;
        // 1 Wagenrad
        g.setColor(new Color(230, 236, 240));
        g.drawString(String.format(Locale.GERMAN, "Wagenrad: 8 Speichen, 2,9 U/s, 24 B/s, 180° – sichtbar %.2f U/s (rückwärts), die rote Speiche läuft vorwärts",
                Shutter.apparentRevPerS(2.9, 8, 24)), gap, 24);
        for (int f = 0; f < 6; f++) g.drawImage(wheel(pw, sh, 2.9, f), gap + f * (pw + gap), 36, null);
        // 2 Verschluss
        int y2 = 36 + pw + 40;
        g.setColor(new Color(230, 236, 240));
        g.drawString("Schwenk über Pfähle (600 Bildpunkte/s): 1/1000 s · 1/48 s (180°) · 1/24 s (360°) · rechts 1/1000 s mit Öffnungsgang quer zur Bewegung, 6 000 Bildpunkte/s: die Pfähle neigen sich", gap, y2 - 12);
        double[][] cases = {{1000, 0, 600}, {48, 0, 600}, {24, 0, 600}, {1000, 1, 6000}};
        for (int k = 0; k < cases.length; k++) {
            Shutter s = new Shutter(24, Shutter.angleFor(1 / cases[k][0], 24));
            s.rolling = cases[k][1] > 0;
            s.sweepDeg = 90;   // die Kante läuft senkrecht über das Fenster
            s.gateWidthMm = 24.89;
            s.gateHeightMm = 24.89;   // quadratische Tafel
            g.drawImage(poles(pw, s, cases[k][2]), gap + k * (pw + gap), y2, null);
            g.setColor(new Color(160, 176, 186));
            g.drawString(s.describe(), gap + k * (pw + gap), y2 + pw + 16);
        }
        // 3 Pupille
        int y3 = y2 + pw + 60;
        g.setColor(new Color(230, 236, 240));
        g.drawString("Pupille, 7 gerade Lamellen, 1 024 Teilbilder – links: 24 mm f/1,4 auf 0,3 m, ferne Lichter, zum Rand das Katzenauge; rechts: 85 mm f/1,4 auf 2 m, Lichter in 0,8 · 1,2 · 2 · 4 · 12 m · ∞", gap, y3 - 12);
        g.drawImage(bokeh(2 * pw + gap, pw), gap, y3, null);
        g.drawImage(bokehRow(4 * pw + 3 * gap, pw), 3 * gap + 2 * pw, y3, null);
        g.dispose();
        File file = new File(out);
        if (file.getAbsoluteFile().getParentFile() != null) file.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", file);
        System.out.println("Bild: " + file.getPath() + " · " + sh.describe());
    }

    /** Ein Filmbild des Rads: 64 Augenblicke über die offene Zeit, Speichen als Streifen. */
    static BufferedImage wheel(int s, Shutter sh, double rev, int frame) {
        int n = 64;
        float[] acc = new float[s * s * 3];
        double t0 = frame / sh.fps;
        for (int k = 0; k < n; k++) {
            double t = t0 + (k + 0.5) / n * sh.openS(), rot = 2 * Math.PI * rev * t;
            for (int y = 0; y < s; y++)
                for (int x = 0; x < s; x++) {
                    double dx = x + 0.5 - s / 2.0, dy = y + 0.5 - s / 2.0, r = Math.hypot(dx, dy) / (s * 0.45);
                    double cr = 0.12, cg = 0.15, cb = 0.18;
                    if (r < 1.02 && r > 0.95) { cr = 0.8; cg = 0.78; cb = 0.7; }
                    else if (r < 0.95 && r > 0.08) {
                        double ang = Math.atan2(dy, dx) - rot, seg = 2 * Math.PI / 8;
                        double m = ang - seg * Math.floor(ang / seg + 0.5);
                        if (Math.abs(m) * r < 0.035) {
                            int spoke = Math.floorMod((int) Math.floor(ang / seg + 0.5), 8);
                            if (spoke == 0) { cr = 0.9; cg = 0.25; cb = 0.2; } else { cr = 0.85; cg = 0.83; cb = 0.75; }
                        }
                    } else if (r <= 0.08) { cr = 0.6; cg = 0.6; cb = 0.55; }
                    int i = (y * s + x) * 3;
                    acc[i] += cr; acc[i + 1] += cg; acc[i + 2] += cb;
                }
        }
        return image(acc, s, s, 1.0 / n);
    }

    /** Ein Schwenk über Pfähle, mit Öffnungsgang je Ort. */
    static BufferedImage poles(int s, Shutter sh, double pxPerS) {
        int n = 96;
        float[] acc = new float[s * s * 3];
        float[] wsum = new float[s * s];
        double[] r = sh.openRange();
        double w = r[1] - r[0] + sh.openS();
        for (int k = 0; k < n; k++) {
            double tau = r[0] + (k + 0.5) / n * w;
            for (int y = 0; y < s; y++)
                for (int x = 0; x < s; x++) {
                    double tx = (x + 0.5) / s - 0.5, ty = 0.5 - (y + 0.5) / s;
                    double a0 = tau - sh.openAt(tx, ty);
                    if (a0 < 0 || a0 >= sh.openS()) continue;
                    double xs = x + pxPerS * tau;
                    double m = xs - 50 * Math.floor(xs / 50);
                    double v = m < 8 ? 0.9 : (y > s * 0.8 ? 0.25 : 0.12);
                    int i = (y * s + x) * 3;
                    acc[i] += v * 0.95; acc[i + 1] += v * 0.9; acc[i + 2] += v * 0.8;
                    wsum[y * s + x]++;
                }
        }
        for (int i = 0; i < s * s; i++) for (int c = 0; c < 3; c++) acc[3 * i + c] /= Math.max(1, wsum[i]);
        return image(acc, s, s, 1);
    }

    /**
     * Ferne Lichter über das ganze Bild eines 24 mm f/1,4 (Halbdiagonale 26°), Schärfe auf 0,5 m: Kreise der Blende, zum Rand
     * von der vorderen Fassung zum Katzenauge beschnitten [G: Fassung ρ = 1,05, Versatz 2,2 je rad Feldwinkel].
     */
    static BufferedImage bokeh(int w, int h) {
        Pupil p = new Pupil(7, 0, 0, 1);
        SubFrames.Sample[] ss = SubFrames.plan(new Shutter(), p, NB, 0, true);
        float[] acc = new float[w * h * 3];
        double sFocus = 0.3, f = 0.024, dPup = f / 1.4;
        double halfDiag = Math.toRadians(26), pxPerTan = Math.hypot(w, h) / 2 / Math.tan(halfDiag);
        for (SubFrames.Sample s : ss) {
            double ex = s.u() * dPup / 2, ey = s.v() * dPup / 2;
            for (int j = 0; j < 5; j++)
                for (int i = 0; i < 9; i++) {
                    double fx = (i - 4) / 4.0 * 0.46 * w, fy = (j - 2) / 2.0 * 0.42 * h;   // Ort im Bild (px von der Mitte)
                    double rr = Math.hypot(fx, fy), th = Math.atan(rr / pxPerTan);
                    double cx = rr > 0 ? 2.2 * th * fx / rr : 0, cy = rr > 0 ? 2.2 * th * fy / rr : 0;
                    if (Math.hypot(s.u() - cx, s.v() - cy) > 1.05) continue;
                    double shift = SubFrames.imageShift(1, sFocus, 1e9) * pxPerTan;   // Bildpunkte je m Versatz
                    splat(acc, w, h, w / 2.0 + fx + ex * shift + s.jx() * 0.5, h / 2.0 - fy - ey * shift + s.jy() * 0.5, 0.95f, 0.8f, 0.55f);
                }
        }
        return image(acc, w, h, 1.0 / NB * 80);
    }

    /** 85 mm f/1,4, Schärfe auf 2 m, Lichter in 0,8 · 1,2 · 2 · 4 · 12 m und ∞: vor der Schärfe und dahinter wachsen die Kreise. */
    static BufferedImage bokehRow(int w, int h) {
        Pupil p = new Pupil(7, 0, 0, 1);
        SubFrames.Sample[] ss = SubFrames.plan(new Shutter(), p, NB, 0, true);
        float[] acc = new float[w * h * 3];
        double sFocus = 2, f = 0.085, dPup = f / 1.4;
        double pxPerTan = h / 2.0 / (12 / 85.0);
        double[] ds = {0.8, 1.2, 2, 4, 12, 1e9};
        for (SubFrames.Sample s : ss)
            for (int k = 0; k < ds.length; k++) {
                double ex = s.u() * dPup / 2, ey = s.v() * dPup / 2;
                double shift = SubFrames.imageShift(1, sFocus, ds[k]) * pxPerTan;
                double x = (k + 0.5) * w / ds.length + ex * shift + s.jx() * 0.5, y = h / 2.0 - ey * shift + s.jy() * 0.5;
                splat(acc, w, h, x, y, 0.95f, 0.8f, 0.55f);
            }
        return image(acc, w, h, 1.0 / NB * 80);
    }

    /** Ein Licht als kleine Scheibe (Halbmesser 2,2 Bildpunkte, weicher Rand): so groß wie eine ferne Laterne im Bild. */
    static void splat(float[] acc, int w, int h, double x, double y, float r, float g, float b) {
        double rad = 2.2;
        for (int yy = (int) Math.floor(y - rad - 1); yy <= (int) Math.ceil(y + rad + 1); yy++)
            for (int xx = (int) Math.floor(x - rad - 1); xx <= (int) Math.ceil(x + rad + 1); xx++) {
                if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                double d = Math.hypot(xx + 0.5 - x, yy + 0.5 - y), wt = Math.max(0, Math.min(1, rad + 0.5 - d)) / (Math.PI * rad * rad);
                if (wt <= 0) continue;
                int q = (yy * w + xx) * 3;
                acc[q] += r * wt; acc[q + 1] += g * wt; acc[q + 2] += b * wt;
            }
    }

    static BufferedImage image(float[] acc, int w, int h, double k) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int q = (y * w + x) * 3, rgb = 0;
                for (int c = 0; c < 3; c++) {
                    double v = acc[q + c] * k;
                    v = 1 - Math.exp(-v * 1.2);   // weiche Schulter
                    int b = (int) Math.round(255 * Math.pow(Math.max(0, Math.min(1, v)), 1 / 2.2));
                    rgb = rgb << 8 | b;
                }
                im.setRGB(x, y, rgb);
            }
        return im;
    }

    private ShutterDemo() { }
}
