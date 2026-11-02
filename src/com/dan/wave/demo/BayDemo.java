package com.dan.wave.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.wave.Bay;
import com.dan.wave.BayGrid;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vorführung von {@link Bay} (seit LituyaBay1958 Phase 11): links eine gerechnete Fjordbucht mit T-Kopf in drei
 * geschachtelten Gittern (20, 5, 2,5 m), eine Welle aus dem nördlichen Arm (Hügel 100 m, wie nach einem Felssturz) läuft
 * über die Gittergrenzen bis zum Eingang; rechts oben der Laborfall Lituya im Schnitt (Messwelle FRITZ01 als Einlass,
 * Auflauf am 45°-Gegenhang), rechts unten Pegel und Rechenzeit je Gitter. Fenster mit Zeitregler;
 * {@code --bild datei.png [t]} schreibt den Bogen zur Zeit t (s).
 */
public final class BayDemo {
    private BayDemo() { }

    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(140, 156, 170), ACC = new Color(240, 200, 110);
    static final double T_END = 60;

    /** Grund der Fjordbucht: Stamm 5,2 km von West nach Ost, T-Kopf von Süd nach Nord, Hänge 45° bis 800 m. */
    public static double fjord(double x, double z) {
        double d1 = x < 5200 ? Math.abs(z - 1600) : Math.hypot(x - 5200, z - 1600), w1 = 600;
        double d2 = Math.abs(x - 5550) + Math.max(0, Math.max(600 - z, z - 2600)), w2 = 350;
        double e1 = d1 <= w1 ? -150 + 150 * (d1 / w1) * (d1 / w1) : (d1 - w1);
        double e2 = d2 <= w2 ? -150 + 150 * (d2 / w2) * (d2 / w2) : (d2 - w2);
        return Math.min(800, Math.min(e1, e2));
    }

    /** Die Bucht mit drei Gittern und einem Hügel von 100 m im nördlichen Arm. */
    public static Bay fjordBay() {
        Bay b = new Bay(0, 0, 310, 160, 20);
        b.name = "Fjordbucht (gerechnet)";
        b.nest(5000.0, 1800.0, 6000.0, 2800.0, 4).nest(5300.0, 2300.0, 5800.0, 2700.0, 2);
        b.edge(Bay.W, BayGrid.Edge.OPEN);
        b.build(BayDemo::fjord, 0, new Bay.Init() {
            public double eta(double x, double z) { return 100 * Math.exp(-((x - 5550) * (x - 5550) + (z - 2450) * (z - 2450)) / (2 * 90 * 90.0)); }
        });
        b.gauge("Arm", 5550, 2200);
        b.gauge("Stamm", 4000, 1600);
        b.gauge("Eingang", 600, 1600);
        return b;
    }

    /** Der Laborfall im Schnitt: Messwelle FRITZ01 (152 m auf 122 m bei 885 m nach 16 s) als Einlass, Gegenhang 45° ab 1 220 m. */
    public static Bay laborfall() {
        double h = 122, hh = 152, c = Math.sqrt(9.81 * (h + hh)), k = Math.sqrt(3 * hh / (4 * h * h * h)), dx = 2, xa = 885;
        int nx = (int) Math.ceil((1342 + 700 - xa) / dx);
        Bay s = new Bay(xa, 0, nx, 3, dx);
        s.name = "Laborfall Lituya im Schnitt";
        s.inlet(Bay.W, (t, x, z) -> { double ch = 1 / Math.cosh(k * c * (t - 16)); double e = hh * ch * ch; return new double[]{e, c * e}; });
        s.build((x, z) -> x < 1220 ? -h : Math.min(650, -h + (x - 1220)), 0, null);
        s.gauge("1 100 m", 1100, 3);
        return s;
    }

    /** Ein Augenblick: Wasserstand je Gitter (NaN: trocken), Schnitt des Laborfalls. */
    public record Frame(double t, List<float[]> eta, float[] section, float[] sectionMax) { }

    /** Rechnet beide Fälle und hält alle 2 s einen Augenblick fest. */
    public static final class Film {
        public final Bay bay = fjordBay(), lab = laborfall();
        public final List<Frame> frames = new ArrayList<>();

        public Film(double tEnd) {
            frames.add(frame());
            for (double t = 2; t <= tEnd + 1e-9; t += 2) { bay.run(t); lab.run(t); frames.add(frame()); }
        }

        Frame frame() {
            List<float[]> e = new ArrayList<>();
            for (BayGrid g : bay.grids()) {
                float[] a = new float[g.nx * g.nz];
                for (int j = 0; j < g.nz; j++) for (int i = 0; i < g.nx; i++) a[j * g.nx + i] = (float) (g.depth(i, j) > 0.05 ? g.eta(i, j) : Double.NaN);
                e.add(a);
            }
            BayGrid l = lab.grids().get(0);
            float[] s = new float[l.nx], m = new float[l.nx];
            for (int i = 0; i < l.nx; i++) { s[i] = (float) (l.depth(i, 1) > 0.05 ? l.eta(i, 1) : Double.NaN); m[i] = (float) lab.maxAt(l.cx(i), l.cz(1)); }
            return new Frame(bay.time(), e, s, m);
        }

        public Frame at(double t) { Frame best = frames.get(0); for (Frame f : frames) if (f.t() <= t + 1e-6) best = f; return best; }
    }

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("--bild")) {
            double t = a.length > 2 ? Double.parseDouble(a[2]) : 20;
            Film f = new Film(Math.max(t, 50));
            BufferedImage im = new BufferedImage(1800, 1100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            paint(g, f, t, 1800, 1100);
            g.dispose();
            ImageIO.write(im, "png", new File(a[1]));
            System.out.println(text(f));
            System.out.println("Bild: " + a[1] + " (t = " + fmt("%.0f", f.at(t).t()) + " s)");
            return;
        }
        SwingUtilities.invokeLater(BayDemo::open);
    }

    public static String text(Film f) {
        StringBuilder b = new StringBuilder(f.bay.describe()).append('\n');
        for (Bay.Gauge g : f.bay.gauges()) b.append(fmt("  Pegel %s: höchstens %.1f m nach %.1f s%n", g.name, g.max(), g.timeOfMax()));
        int k = 0;
        for (BayGrid g : f.bay.grids()) b.append(fmt("  Gitter %d (%.1f m, %d × %d): %d Schritte, %.1f s Rechnung%n", k++, g.dx, g.nx, g.nz, g.stepCount(), g.busyMs() / 1000));
        b.append(fmt("  Volumen %.12f des Anfangs, %.0f s Welle in %.1f s Rechnung%n", f.bay.volume() / f.bay.initialVolume(), f.bay.time(), f.bay.busyMs() / 1000));
        b.append(fmt("%s: Auflauf %.0f m (FRITZ01 526 m), Pegel 1 100 m %.0f m nach %.1f s", f.lab.name, f.lab.runup(1220, -1, 1e9, 1e9), f.lab.gauges().get(0).max(), f.lab.gauges().get(0).timeOfMax()));
        return b.toString();
    }

    static String fmt(String f, Object... o) { return String.format(Locale.GERMAN, f, o); }

    // ------------------------------------------------------------------ Bogen

    public static void paint(Graphics2D g, Film f, double t, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        Frame fr = f.at(t);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString("Bucht · com.dan.wave.Bay · " + fmt("t = %.0f s", fr.t()), 20, 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(DIM);
        g.drawString(f.bay.describe() + " · HLL, zweite Ordnung, nass und trocken, Unterschritte mit Abgleich der Flüsse", 20, 52);
        int lw = (int) (w * 0.58);
        plan(g, f, fr, 20, 66, lw - 30, h - 86);
        section(g, f, fr, lw, 66, w - lw - 20, (h - 86) / 2 - 10);
        gauges(g, f, fr.t(), lw, 66 + (h - 86) / 2 + 10, w - lw - 20, (h - 86) / 2 - 10);
    }

    static Color waterColor(double e) {
        double v = Math.max(-1, Math.min(1, e / 40));
        if (v >= 0) return new Color((int) (40 + 200 * v), (int) (90 + 140 * v), (int) (150 + 100 * v));
        return new Color(20, (int) (60 + 30 * (1 + v)), (int) (110 + 40 * (1 + v)));
    }

    static void plan(Graphics2D g, Film f, Frame fr, int x, int y, int w, int h) {
        BayGrid g0 = f.bay.grids().get(0);
        double sc = Math.min(w / (g0.nx * g0.dx), h / (g0.nz * g0.dx));
        int pw = (int) (g0.nx * g0.dx * sc), ph = (int) (g0.nz * g0.dx * sc);
        BufferedImage im = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < ph; py++)
            for (int px = 0; px < pw; px++) {
                double wx = g0.x0 + (px + 0.5) / sc, wz = g0.z0 + (ph - py - 0.5) / sc;
                double e = Double.NaN;
                for (int k = f.bay.grids().size() - 1; k >= 0; k--) {
                    BayGrid gk = f.bay.grids().get(k);
                    if (!gk.contains(wx, wz)) continue;
                    e = fr.eta().get(k)[gk.cellJ(wz) * gk.nx + gk.cellI(wx)];
                    break;
                }
                int rgb;
                if (!Double.isNaN(e)) rgb = waterColor(e - f.bay.level).getRGB();
                else {
                    double z = fjord(wx, wz), s = (fjord(wx + 20, wz) - fjord(wx - 20, wz)) / 40, gray = Math.max(30, Math.min(200, 90 + 0.12 * z - 60 * s));
                    rgb = new Color((int) gray, (int) (gray * 0.97), (int) (gray * 0.9)).getRGB();
                }
                im.setRGB(px, py, rgb);
            }
        g.drawImage(im, x, y, null);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        Color[] cs = {INK, ACC, new Color(240, 120, 100)};
        for (int k = 0; k < f.bay.grids().size(); k++) {
            BayGrid gk = f.bay.grids().get(k);
            int rx = x + (int) ((gk.x0 - g0.x0) * sc), ry = y + ph - (int) ((gk.z0 - g0.z0 + gk.nz * gk.dx) * sc);
            g.setColor(cs[k % 3]);
            g.drawRect(rx, ry, (int) (gk.nx * gk.dx * sc), (int) (gk.nz * gk.dx * sc));
            g.drawString(fmt("%.1f m", gk.dx), rx + 4, ry - 4);
        }
        for (Bay.Gauge gg : f.bay.gauges()) {
            int gx = x + (int) ((gg.x - g0.x0) * sc), gy = y + ph - (int) ((gg.z - g0.z0) * sc);
            g.setColor(ACC);
            g.fillOval(gx - 4, gy - 4, 8, 8);
            g.drawString(gg.name, gx + 6, gy + 4);
        }
        g.setColor(DIM);
        g.drawString("Wasserstand: dunkel Tal, hell Kamm (±40 m); Land schattiert; Rahmen: Gitter", x, y + ph + 18);
    }

    static void section(Graphics2D g, Film f, Frame fr, int x, int y, int w, int h) {
        g.setColor(new Color(24, 32, 42));
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString("Laborfall Lituya im Schnitt: Messwelle FRITZ01 → Gegenhang 45°", x + 12, y + 22);
        BayGrid l = f.lab.grids().get(0);
        double xa = l.x0, xb = l.x0 + l.nx * l.dx, za = -140, zb = 700;
        int px0 = x + 50, py0 = y + 36, pw = w - 70, ph = h - 80;
        java.util.function.DoubleUnaryOperator sx = v -> px0 + (v - xa) / (xb - xa) * pw, sy = v -> py0 + (zb - v) / (zb - za) * ph;
        Path2D.Double rock = new Path2D.Double();
        rock.moveTo(sx.applyAsDouble(xa), sy.applyAsDouble(za));
        for (int i = 0; i < l.nx; i++) rock.lineTo(sx.applyAsDouble(l.cx(i)), sy.applyAsDouble(l.bedAt(i, 1)));
        rock.lineTo(sx.applyAsDouble(xb), sy.applyAsDouble(za));
        rock.closePath();
        g.setColor(new Color(58, 62, 66));
        g.fill(rock);
        Path2D.Double wat = new Path2D.Double(), mx = new Path2D.Double();
        boolean on = false, onm = false;
        for (int i = 0; i < l.nx; i++) {
            double e = fr.section()[i], m = fr.sectionMax()[i];
            if (!Double.isNaN(e)) { if (!on) wat.moveTo(sx.applyAsDouble(l.cx(i)), sy.applyAsDouble(e)); else wat.lineTo(sx.applyAsDouble(l.cx(i)), sy.applyAsDouble(e)); on = true; } else on = false;
            if (!Double.isNaN(m)) { if (!onm) mx.moveTo(sx.applyAsDouble(l.cx(i)), sy.applyAsDouble(m)); else mx.lineTo(sx.applyAsDouble(l.cx(i)), sy.applyAsDouble(m)); onm = true; } else onm = false;
        }
        g.setColor(new Color(110, 190, 240));
        g.setStroke(new BasicStroke(2f));
        g.draw(wat);
        g.setColor(new Color(240, 200, 110, 160));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[]{5, 4}, 0));
        g.draw(mx);
        g.setStroke(new BasicStroke(1f));
        int ry = (int) sy.applyAsDouble(526);
        g.setColor(INK);
        g.drawLine(px0, ry, px0 + pw, ry);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        double r = f.lab.runup(1220, -1, 1e9, 1e9);
        g.drawString(fmt("FRITZ01 526 m · gerechnet bis jetzt %.0f m (gestrichelt: höchster Stand)", r), px0 + 6, ry - 6);
        g.setColor(DIM);
        for (int m = 0; m <= 600; m += 200) { int yy = (int) sy.applyAsDouble(m); g.drawString(m + " m", x + 8, yy + 4); }
        g.drawString(fmt("Einlass bei 885 m: Einzelwelle 152 m auf 122 m, Kamm nach 16 s; 2 m Zellen; t = %.0f s", fr.t()), px0, y + h - 14);
    }

    static void gauges(Graphics2D g, Film f, double t, int x, int y, int w, int h) {
        g.setColor(new Color(24, 32, 42));
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString("Pegel in der Bucht und Rechenzeit je Gitter", x + 12, y + 22);
        int px0 = x + 50, py0 = y + 36, pw = w - 70, ph = h / 2 - 30;
        double e0 = -40, e1 = 80;
        g.setColor(new Color(50, 62, 76));
        g.drawRect(px0, py0, pw, ph);
        Color[] cs = {new Color(110, 190, 240), ACC, new Color(230, 120, 90)};
        int k = 0;
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (Bay.Gauge gg : f.bay.gauges()) {
            Path2D.Double p = new Path2D.Double();
            boolean first = true;
            for (double[] s : gg.series()) {
                if (Double.isNaN(s[1]) || s[0] > T_END) { first = true; continue; }
                double xx = px0 + s[0] / T_END * pw, yy = py0 + (e1 - Math.max(e0, Math.min(e1, s[1]))) / (e1 - e0) * ph;
                if (first) p.moveTo(xx, yy); else p.lineTo(xx, yy);
                first = false;
            }
            g.setColor(cs[k]);
            g.draw(p);
            g.drawString(fmt("%s bis %.1f m (%.1f s)", gg.name, gg.max(), gg.timeOfMax()), px0 + k * (pw / 3), py0 + ph + 16);
            k++;
        }
        int tx = px0 + (int) (Math.min(T_END, t) / T_END * pw);
        g.setColor(new Color(255, 255, 255, 70));
        g.drawLine(tx, py0, tx, py0 + ph);
        g.setColor(INK);
        int ly = py0 + ph + 44;
        int gi = 0;
        for (BayGrid gr : f.bay.grids()) {
            g.drawString(fmt("Gitter %d: %.1f m, %d × %d Zellen, %d Schritte, %.1f s Rechnung", gi++, gr.dx, gr.nx, gr.nz, gr.stepCount(), gr.busyMs() / 1000), px0, ly);
            ly += 18;
        }
        g.drawString(fmt("Volumen %.12f des Anfangs · %.0f s Welle in %.1f s Rechnung (Prozessor; Grafikkarte mit Phase 11b)", f.bay.volume() / f.bay.initialVolume(), f.bay.time(), f.bay.busyMs() / 1000), px0, ly + 6);
    }

    // ------------------------------------------------------------------ Fenster

    static void open() {
        Film[] film = {null};
        double[] time = {20};
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics gr) {
                if (film[0] == null) { gr.setColor(BG); gr.fillRect(0, 0, getWidth(), getHeight()); gr.setColor(INK); gr.drawString("rechnet …", 40, 40); return; }
                BayDemo.paint((Graphics2D) gr, film[0], time[0], getWidth(), getHeight());
            }
        };
        view.setPreferredSize(new Dimension(1700, 1000));
        FFrame f = new FFrame("Bucht · com.dan.wave.Bay");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(view, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(BG);
        JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT));
        west.setBackground(BG);
        JLabel l = new JLabel("  Zeit (s)  ");
        l.setForeground(INK);
        west.add(l);
        south.add(west, BorderLayout.WEST);
        FSlider s = new FSlider(0, (int) T_END, 20);
        s.addChangeListener(e -> { time[0] = s.getValue(); view.repaint(); });
        south.add(s, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(1760, 1100);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Thread(() -> { Film fm = new Film(T_END); SwingUtilities.invokeLater(() -> { film[0] = fm; view.repaint(); }); }, "bay").start();
    }
}
