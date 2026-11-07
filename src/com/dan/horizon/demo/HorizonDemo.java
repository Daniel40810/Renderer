package com.dan.horizon.demo;

import com.dan.horizon.Aerial;
import com.dan.horizon.Airmass;
import com.dan.horizon.Horizon;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Vorführung von {@code com.dan.horizon} ohne Grafikkarte: eine Kette an einer Bucht in der Abenddämmerung, vom Boot aus
 * gesehen. Der Bergschatten kommt aus der Horizontkarte des Kerns (dieselbe Rechnung wie {@code gl/horizon.comp}), das
 * Sonnenlicht am Punkt aus {@link Airmass} (die Gipfel glühen rot, wenn die Bucht schon im Schatten liegt), der Dunst aus
 * {@link Aerial} in zwei Schichten. Mit dem Regler sinkt die Sonne von 6° bis −2°, und das Alpenglühen steigt die Hänge
 * hinauf.
 * <p>
 * {@code HorizonDemo --bild datei.png [Sonnenhöhe°] [Breite]} schreibt ein Standbild ohne Fenster (Vorgabe 0,8°, 1280).
 */
public final class HorizonDemo {
    static final double EDGE = 48000, AZ = 300;   // Gelände 48 km, Sonne im Westnordwesten
    static final int N = 768;

    final Horizon hz = new Horizon();
    final Airmass air = new Airmass();
    final Aerial aerial = new Aerial();
    final Horizon.Grid ground;
    float[] map;
    double mapAz = Double.NaN;

    HorizonDemo() {
        ground = Horizon.Grid.sample(HorizonDemo::terrain, -EDGE / 2, -EDGE / 2, EDGE, N, 0);
    }

    /** Eine Kette im Norden (−z) mit Graten und Karen, davor eine Bucht; Höhe in m. */
    static double terrain(double x, double z) {
        double ridge = Math.exp(-Math.pow((z + 9000) / 5200, 2)) * 3600;
        double peaks = 0;
        double[][] P = {{-6000, -11000, 1500, 2600}, {3500, -9500, 1100, 1900}, {11000, -12500, 1700, 2300}, {-14000, -8000, 900, 1400}};
        for (double[] p : P) peaks += p[3] * Math.exp(-(Math.pow(x - p[0], 2) + Math.pow(z - p[1], 2)) / (2 * p[2] * p[2]));
        double f = 0, a = 1, s = 1 / 2600.0;
        for (int o = 0; o < 6; o++) { f += a * (noise(x * s + o * 17.1, z * s - o * 9.7) - 0.5); a *= 0.5; s *= 2.03; }
        double h = (ridge + peaks) * (0.75 + 0.5 * f) + f * 260 - 180 + z * -0.012;
        return h;
    }

    static double hash(int i, int j) {
        long k = i * 73856093L ^ j * 19349663L;
        k = (k ^ (k >>> 13)) * 0x5bd1e995L;
        return ((k ^ (k >>> 15)) & 0xffffff) / (double) 0xffffff;
    }

    static double noise(double x, double z) {
        int i = (int) Math.floor(x), j = (int) Math.floor(z);
        double u = x - i, v = z - j;
        u = u * u * (3 - 2 * u);
        v = v * v * (3 - 2 * v);
        return (1 - v) * ((1 - u) * hash(i, j) + u * hash(i + 1, j)) + v * ((1 - u) * hash(i, j + 1) + u * hash(i + 1, j + 1));
    }

    /** Das Bild bei der Sonnenhöhe elDeg. */
    BufferedImage render(int w, int h, double elDeg) {
        if (!(mapAz == AZ)) { map = hz.map(ground, ground, null, AZ); mapAz = AZ; }
        double el = Math.toRadians(elDeg), az = Math.toRadians(AZ);
        double[] sun = {Math.sin(az) * Math.cos(el), Math.sin(el), -Math.cos(az) * Math.cos(el)};
        double sinEl = Math.sin(el);
        // Licht: Sonne außerhalb der Luft, Himmel nach der Sonnenhöhe (einfach), Dunst 45 km
        double bright = Math.pow(10, -1.0 + 1.0 * clamp((elDeg + 0) / 10, -1.3, 1));   // Dämmerung dunkler
        double[] skyZen = {0.10 * bright, 0.17 * bright, 0.36 * bright};
        double[] skyHor = {0.55 * bright, 0.42 * bright, 0.38 * bright};
        double fog = 45000;
        double[] eye = {0, 2, 9000};
        double yaw = Math.toRadians(-6), pitch = Math.toRadians(3.5), fov = Math.toRadians(42);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double tanY = Math.tan(fov / 2), tanX = tanY * w / h;
        IntStream.range(0, h).parallel().forEach(py -> {
            for (int px = 0; px < w; px++) {
                double sx = (2 * (px + 0.5) / w - 1) * tanX, sy = (1 - 2 * (py + 0.5) / h) * tanY;
                double[] d = norm(new double[]{sx, sy, -1});
                d = rotX(d, pitch);
                d = rotY(d, yaw);
                double[] c = trace(eye, d, sun, sinEl, skyZen, skyHor, fog, bright, 0);
                img.setRGB(px, py, rgb(c));
            }
        });
        return img;
    }

    double[] trace(double[] e, double[] d, double[] sun, double sinEl, double[] zen, double[] hor, double fog, double bright, int depth) {
        double t = 5, prev = 0;
        boolean hit = false;
        double[] p = new double[3];
        for (int k = 0; k < 900 && t < 60000; k++) {
            p[0] = e[0] + d[0] * t; p[1] = e[1] + d[1] * t; p[2] = e[2] + d[2] * t;
            double g = Math.max(0, ground.contains(p[0], p[2]) ? ground.at(p[0], p[2]) : 0);
            if (p[1] < g) {
                // zurück zwischen prev und t halbieren
                double a = prev, b = t;
                for (int q = 0; q < 8; q++) {
                    double m = (a + b) / 2;
                    double gy = ground.contains(e[0] + d[0] * m, e[2] + d[2] * m) ? Math.max(0, ground.at(e[0] + d[0] * m, e[2] + d[2] * m)) : 0;
                    if (e[1] + d[1] * m < gy) b = m; else a = m;
                }
                t = b;
                p[0] = e[0] + d[0] * t; p[1] = e[1] + d[1] * t; p[2] = e[2] + d[2] * t;
                hit = true;
                break;
            }
            prev = t;
            t += Math.max(4, t * 0.006);
        }
        double[] skyC = sky(d, sun, zen, hor);
        if (!hit) return skyC;
        double g = ground.contains(p[0], p[2]) ? ground.at(p[0], p[2]) : 0;
        double[] c;
        if (g <= 0.5 && depth == 0) {
            // Wasser: die Szene gespiegelt (ein zweiter Strahl), dunkel
            double[] r = {d[0], -d[1], d[2]};
            double[] s = trace(new double[]{p[0], 0.05, p[2]}, r, sun, sinEl, zen, hor, fog, bright, 1);
            double fr = 0.02 + 0.98 * Math.pow(1 - Math.abs(d[1]), 5);
            c = new double[]{s[0] * fr + 0.01 * bright, s[1] * fr + 0.018 * bright, s[2] * fr + 0.02 * bright};
        } else if (g <= 0.5) {
            c = new double[]{0.01 * bright, 0.018 * bright, 0.02 * bright};
        } else {
            double ex = 30;
            double hx = ground.at(p[0] + ex, p[2]) - ground.at(p[0] - ex, p[2]), hzz = ground.at(p[0], p[2] + ex) - ground.at(p[0], p[2] - ex);
            double[] n = norm(new double[]{-hx, 2 * ex, -hzz});
            // Bergschatten aus der Horizontkarte (nächste Zelle)
            int i = (int) Math.min(N - 1, Math.max(0, (p[0] - ground.x0) / ground.cell())), j = (int) Math.min(N - 1, Math.max(0, (p[2] - ground.z0) / ground.cell()));
            double vis = hz.visibility(map[(j * N + i) * 2], map[(j * N + i) * 2 + 1], p[1], sinEl);
            double[] tr = air.transmittance(p[1], sinEl);
            double nd = Math.max(0, dot(n, sun));
            double snow = smooth(1700, 2300, p[1] + 300 * (noise(p[0] / 900, p[2] / 900) - 0.5)) * (1 - smooth(0.55, 0.8, 1 - n[1]));
            double[] alb = {mix(0.16, 0.85, snow), mix(0.15, 0.87, snow), mix(0.14, 0.92, snow)};
            double sicht = 0.55 + 0.45 * n[1];
            c = new double[3];
            for (int k = 0; k < 3; k++) {
                double sunL = 3.0 * tr[k] * vis * nd;
                double skyL = (zen[k] * 0.8 + hor[k] * 0.4) * sicht;
                c[k] = alb[k] * (sunL + skyL);
            }
        }
        // Dunst in zwei Schichten
        double f = aerial.fraction(t, e[1], d[1], fog);
        double[] haze = sky(norm(new double[]{d[0], 0.03, d[2]}), sun, zen, hor);
        for (int k = 0; k < 3; k++) c[k] = c[k] * (1 - f) + haze[k] * Math.min(1, 0.9) * f;
        return c;
    }

    double[] sky(double[] d, double[] sun, double[] zen, double[] hor) {
        double up = Math.max(0, d[1]);
        double hw = Math.pow(1 - up, 4);
        double toward = clamp(dot(norm2(d), norm2(sun)) * 0.5 + 0.5, 0, 1);
        double[] c = new double[3];
        double glow = Math.pow(toward, 5) * Math.pow(1 - up, 10) * (1 - smooth(-4, 8, Math.toDegrees(Math.asin(sun[1]))) * 0.4);
        double[] gc = {1.0, 0.45, 0.16};
        for (int k = 0; k < 3; k++) c[k] = zen[k] * (1 - hw) + hor[k] * hw + gc[k] * glow * (zen[1] / 0.17) * 1.6;
        // Erdschatten gegenüber der Sonne: blaugrau, darüber der Venusgürtel
        double anti = Math.pow(clamp(-dot(norm2(d), norm2(sun)), 0, 1), 3);
        double belt = smooth(0.0, 0.05, d[1]) * (1 - smooth(0.05, 0.16, d[1])) * anti;
        double shadow = (1 - smooth(0.0, 0.06, d[1])) * anti;
        c[0] += belt * zen[1] * 0.9; c[1] += belt * zen[1] * 0.5; c[2] += belt * zen[1] * 0.55;
        for (int k = 0; k < 3; k++) c[k] *= 1 - 0.35 * shadow;
        return c;
    }

    static int rgb(double[] c) {
        int r = tone(c[0]), g = tone(c[1]), b = tone(c[2]);
        return (r << 16) | (g << 8) | b;
    }

    static int tone(double x) {
        x *= 2.2;
        double a = 2.51, b = 0.03, cc = 2.43, d = 0.59, e = 0.14;
        double y = clamp((x * (a * x + b)) / (x * (cc * x + d) + e), 0, 1);
        return (int) Math.round(Math.pow(y, 1 / 2.2) * 255);
    }

    static double[] rotX(double[] v, double a) { double c = Math.cos(a), s = Math.sin(a); return new double[]{v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c}; }
    static double[] rotY(double[] v, double a) { double c = Math.cos(a), s = Math.sin(a); return new double[]{v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c}; }
    static double[] norm(double[] v) { double l = Math.sqrt(dot(v, v)); return new double[]{v[0] / l, v[1] / l, v[2] / l}; }
    static double[] norm2(double[] v) { double l = Math.hypot(v[0], v[2]) + 1e-9; return new double[]{v[0] / l, 0, v[2] / l}; }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double clamp(double x, double a, double b) { return Math.max(a, Math.min(b, x)); }
    static double mix(double a, double b, double t) { return a + (b - a) * t; }
    static double smooth(double a, double b, double x) { double t = clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); }

    /** Fläche für das Bild im Fenster. */
    static final class Bild extends JPanel {
        private BufferedImage img;
        Bild() { setPreferredSize(new java.awt.Dimension(1280, 720)); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (img != null) g.drawImage(img, 0, 0, getWidth(), getHeight(), null);
        }
        void set(BufferedImage i) { img = i; repaint(); }
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.GERMANY);
        HorizonDemo demo = new HorizonDemo();
        if (args.length >= 2 && args[0].equals("--bild")) {
            double el = args.length >= 3 ? Double.parseDouble(args[2].replace(',', '.')) : 0.8;
            int w = args.length >= 4 ? Integer.parseInt(args[3]) : 1280;
            long t0 = System.nanoTime();
            BufferedImage img = demo.render(w, w * 9 / 16, el);
            ImageIO.write(img, "png", new File(args[1]));
            System.out.printf("HorizonDemo: %s, Sonne %.1f°, %.1f s%n", args[1], el, (System.nanoTime() - t0) / 1e9);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("com.dan.horizon – Alpenglühen");
            Bild bild = new Bild();
            JLabel info = new JLabel(" Sonne 0,8°");
            JSlider s = new JSlider(-20, 60, 8);
            Runnable go = () -> {
                double el = s.getValue() / 10.0;
                info.setText(String.format(Locale.GERMANY, " Sonne %.1f° – rechnet …", el));
                new SwingWorker<BufferedImage, Void>() {
                    @Override protected BufferedImage doInBackground() { return demo.render(960, 540, el); }
                    @Override protected void done() {
                        try {
                            BufferedImage i = get();
                            bild.set(i);
                            info.setText(String.format(Locale.GERMANY, " Sonne %.1f°", el));
                        } catch (Exception ex) { info.setText(" " + ex); }
                    }
                }.execute();
            };
            s.addChangeListener(e -> { if (!s.getValueIsAdjusting()) go.run(); });
            JPanel unten = new JPanel(new BorderLayout());
            unten.add(s, BorderLayout.CENTER);
            unten.add(info, BorderLayout.EAST);
            f.add(bild, BorderLayout.CENTER);
            f.add(unten, BorderLayout.SOUTH);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            go.run();
        });
    }
}
