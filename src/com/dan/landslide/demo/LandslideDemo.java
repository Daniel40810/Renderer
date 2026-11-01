package com.dan.landslide.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.landslide.Landslide;
import com.dan.landslide.Presets;

import javax.imageio.ImageIO;
import javax.swing.JComboBox;
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
 * Vorführung von {@code com.dan.landslide} (seit LituyaBay1958 Phase 9): der Bergsturz in den Gilbert Inlet
 * ({@link Presets#gilbertInlet1958}), dazu Vajont 1963 und Spirit Lake 1980. Links die Draufsicht mit der Mächtigkeit
 * über dem schattierten Gelände, Wasser blau, Uferlinie hell; rechts oben der Schnitt längs der Mitte mit Gleitbahn,
 * Wasserspiegel und Oberfläche des Stroms (gefärbt nach Geschwindigkeit), rechts unten der Zufluss über die Uferlinie
 * s(t): Rate, Geschwindigkeit, Mächtigkeit – die Schnittstelle zur Welle. Fenster mit Zeitregler und Wahl des Falls;
 * {@code --bild datei.png [t] [preset] [LOW|MEDIUM|HIGH]} schreibt den Bogen zur Zeit t (s).
 */
public final class LandslideDemo {
    private LandslideDemo() { }

    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(140, 156, 170), ACC = new Color(240, 200, 110),
            WATER = new Color(40, 90, 140), SHORE = new Color(170, 210, 235);
    static final String[] PRESETS = {"gilbertInlet1958", "vajont1963", "spiritLake1980"};

    /** Ein Augenblick: Zeit, Mächtigkeit und Geschwindigkeit je Zelle, Kennzahlen. */
    public record Frame(double t, float[] h, float[] v, String status) {
        public static Frame of(Landslide s) {
            int n = s.nx * s.nz;
            float[] h = new float[n], v = new float[n];
            for (int c = 0; c < n; c++) { h[c] = (float) s.thickness(c); v[c] = (float) s.speed(c); }
            return new Frame(s.time(), h, v, s.status());
        }
    }

    public static Landslide preset(String name, Landslide.Quality q) {
        return switch (name) {
            case "vajont1963" -> Presets.vajont1963(q);
            case "spiritLake1980" -> Presets.spiritLake1980(q);
            default -> Presets.gilbertInlet1958(q);
        };
    }

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("--bild")) {
            double t = a.length > 2 ? Double.parseDouble(a[2]) : 18;
            String p = a.length > 3 ? a[3] : PRESETS[0];
            Landslide.Quality q = a.length > 4 ? Landslide.Quality.valueOf(a[4]) : Landslide.Quality.MEDIUM;
            Landslide s = preset(p, q);
            Landslide all = preset(p, q);
            all.runToRest(120);
            s.run(t);
            BufferedImage im = new BufferedImage(1800, 1100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            paint(g, s, Frame.of(s), all.entries(), all.entrySummary(), text(all), 1800, 1100);
            g.dispose();
            ImageIO.write(im, "png", new File(a[1]));
            System.out.println(text(all));
            System.out.println("Bild: " + a[1] + " (t = " + fmt("%.1f", s.time()) + " s)");
            return;
        }
        SwingUtilities.invokeLater(LandslideDemo::open);
    }

    /** Die Kennzahlen eines fertig gerechneten Stroms als Text. */
    public static String text(Landslide s) {
        StringBuilder b = new StringBuilder();
        b.append(fmt("%s (%s): %s, Gitter %d × %d zu %.0f m%n", s.name, s.source, s.material, s.nx, s.nz, s.dx));
        double[] e = s.entrySummary(), f = s.fahrboeschung(0.5);
        if (!s.entries().isEmpty())
            b.append(fmt("  Zufluss über die Uferlinie: %.2e m³ von %.0f bis %.0f s (%.1f s für 90 %%), Geschwindigkeit %.0f m/s (nach Rate), höchstens %.0f m/s;%n"
                    + "  Mächtigkeit am Ufer bis %.0f m senkrecht, %.0f m normal zum Hang; Breite bis %.0f m; Rate bis %.2e m³/s%n",
                    e[8], e[0], e[1], e[2], e[3], e[4], e[5], e[6], e[7], e[9]));
        b.append(fmt("  Fahrböschung %.1f° (H %.0f m, L %.0f m), SCHEIDEGGER73 für %.2e m³: %.1f°; Volumen %.6e m³ + %.1e m³ über den Rand%n",
                f[0], f[1], f[2], s.volume() + s.lost(), Landslide.scheidegger(s.volume() + s.lost()), s.volume(), s.lost()));
        b.append(fmt("  zur Ruhe nach %.0f s (%d Schritte)", s.time(), s.steps()));
        return b.toString();
    }

    static String fmt(String f, Object... o) { return String.format(Locale.GERMAN, f, o); }

    // ------------------------------------------------------------------ Bogen

    /**
     * Malt den Bogen: geom gibt Gitter und Gelände, fr den Augenblick, entries und sum den Zufluss des ganzen Laufs.
     */
    public static void paint(Graphics2D g, Landslide geom, Frame fr, List<Landslide.Entry> entries, double[] sum, int w, int h) { paint(g, geom, fr, entries, sum, null, w, h); }

    /** Wie oben, mit Kennzahlen (Zeilen) unter der Draufsicht. */
    public static void paint(Graphics2D g, Landslide geom, Frame fr, List<Landslide.Entry> entries, double[] sum, String notes, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        int lw = (int) (w * 0.56);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.setColor(INK);
        g.drawString(geom.name + " · " + fmt("t = %.1f s", fr.t()), 20, 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(DIM);
        g.drawString(fr.status(), 20, 52);
        int dh = draufsicht(g, geom, fr, 20, 66, lw - 30, h - 260);
        if (notes != null) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.setColor(INK);
            int ly = 66 + dh + 44;
            for (String line : notes.split("\n")) {
                for (String part : wrap(line.strip(), 130)) { g.drawString(part, 20, ly); ly += 17; }
                if (ly > h - 90) break;
            }
        }
        legende(g, 20, h - 70, lw - 30);
        schnitt(g, geom, fr, lw + 10, 40, w - lw - 30, (h - 80) / 2 - 10);
        zufluss(g, entries, sum, fr.t(), lw + 10, 40 + (h - 80) / 2 + 20, w - lw - 30, (h - 80) / 2 - 30);
    }

    static Color thicknessColor(double hh) {
        double t = Math.min(1, Math.sqrt(hh / 120));
        // dunkelrot → orange → hellgelb
        int r = (int) (120 + 135 * Math.min(1, t * 1.6)), gg = (int) (30 + 200 * Math.max(0, t - 0.25) / 0.75), bb = (int) (20 + 160 * Math.max(0, t - 0.7) / 0.3);
        return new Color(Math.min(255, r), Math.min(255, gg), Math.min(255, bb));
    }

    static Color speedColor(double v) {
        double t = Math.min(1, v / 120);
        float hue = (float) (0.66 - 0.66 * t);
        return Color.getHSBColor(hue, 0.75f, 0.95f);
    }

    static List<String> wrap(String s, int n) {
        List<String> out = new ArrayList<>();
        while (s.length() > n) {
            int k = s.lastIndexOf(' ', n);
            if (k <= 0) k = n;
            out.add(s.substring(0, k));
            s = "   " + s.substring(k).strip();
        }
        out.add(s);
        return out;
    }

    static int draufsicht(Graphics2D g, Landslide s, Frame fr, int x, int y, int mw, int mh) {
        double ex = s.nx * s.dx, ez = s.nz * s.dx, sc = Math.min(mw / ex, mh / ez);
        int iw = (int) (ex * sc), ih = (int) (ez * sc);
        BufferedImage im = new BufferedImage(s.nx, s.nz, BufferedImage.TYPE_INT_RGB);
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int c = 0; c < s.nx * s.nz; c++) { lo = Math.min(lo, s.bed(c)); hi = Math.max(hi, s.bed(c)); }
        for (int j = 0; j < s.nz; j++) for (int i = 0; i < s.nx; i++) {
            int c = s.index(i, j);
            double b = s.bed(c);
            double gx = i < s.nx - 1 ? s.bed(c + 1) - b : b - s.bed(c - 1), gz = j < s.nz - 1 ? s.bed(c + s.nx) - b : b - s.bed(c - s.nx);
            double shade = 0.55 + 0.45 * Math.max(-1, Math.min(1, (-gx - gz) / (2 * s.dx)));
            double lev = (b - lo) / Math.max(1, hi - lo);
            int base = (int) (60 + 90 * lev);
            Color col = new Color(clamp(base * shade), clamp(base * shade * 0.97), clamp(base * shade * 0.9));
            if (s.underWater(c)) col = mix(WATER, col, 0.25);
            float hh = fr.h()[c];
            if (hh > 0.05) col = mix(thicknessColor(hh), col, Math.min(1, hh / 3));
            im.setRGB(i, j, col.getRGB());
        }
        g.drawImage(im, x, y, iw, ih, null);
        // Uferlinie
        g.setColor(SHORE);
        for (int j = 0; j < s.nz; j++) for (int i = 0; i < s.nx - 1; i++) {
            int c = s.index(i, j);
            if (s.underWater(c) != s.underWater(c + 1)) g.fillRect(x + (int) ((i + 1) * s.dx * sc), y + (int) (j * s.dx * sc), 2, Math.max(1, (int) (s.dx * sc)));
        }
        // Abrisskante
        double[] cr = s.crownPoint();
        if (!Double.isNaN(cr[0])) {
            g.setColor(ACC);
            int px = x + (int) ((cr[0] - s.x0) * sc), pz = y + (int) ((cr[1] - s.z0) * sc);
            g.drawOval(px - 5, pz - 5, 10, 10);
            g.drawString(fmt("Abrisskante %.0f m", cr[2]), px + 8, pz - 6);
        }
        g.setColor(DIM);
        g.drawRect(x, y, iw, ih);
        // Maßstab 500 m
        int bar = (int) (500 * sc);
        g.setColor(INK);
        g.fillRect(x + 10, y + ih - 16, bar, 3);
        g.drawString("500 m", x + 14 + bar, y + ih - 10);
        g.drawString("Draufsicht: Mächtigkeit (m) über der Gleitbahn; Schnitt längs z = 0", x, y + ih + 18);
        return ih;
    }

    static void legende(Graphics2D g, int x, int y, int w) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int n = 7;
        double[] hs = {1, 5, 10, 25, 50, 92, 134};
        for (int k = 0; k < n; k++) {
            g.setColor(thicknessColor(hs[k]));
            g.fillRect(x + k * 70, y, 60, 12);
            g.setColor(DIM);
            g.drawString(fmt("%.0f m", hs[k]), x + k * 70, y + 26);
        }
        int sx = x + n * 70 + 30;
        for (int k = 0; k <= 6; k++) {
            g.setColor(speedColor(k * 20));
            g.fillRect(sx + k * 46, y, 40, 12);
            g.setColor(DIM);
            g.drawString(fmt("%d", k * 20), sx + k * 46, y + 26);
        }
        g.drawString("m/s (Schnitt)", sx + 7 * 46, y + 10);
    }

    static void schnitt(Graphics2D g, Landslide s, Frame fr, int x, int y, int w, int h) {
        int j = Math.min(s.nz - 1, Math.max(0, (int) ((0 - s.z0) / s.dx)));
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int i = 0; i < s.nx; i++) {
            int c = s.index(i, j);
            lo = Math.min(lo, s.bed(c));
            hi = Math.max(hi, s.bed(c) + Math.max(s.maxThickness(c), fr.h()[c]));
        }
        hi += 40;
        lo -= 20;
        double ex = s.nx * s.dx, sc = Math.min(w / ex, (h - 30) / (hi - lo));
        int top = y + 20;
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString("Schnitt längs der Mitte (unverzerrt; Höhen in m)", x, y + 10);
        // Wasser
        if (!Double.isNaN(s.waterLevel)) {
            int wy = top + (int) ((hi - s.waterLevel) * sc);
            Path2D wp = new Path2D.Double();
            boolean on = false;
            for (int i = 0; i < s.nx; i++) {
                int c = s.index(i, j);
                double px = x + (i + 0.5) * s.dx * sc;
                if (s.bed(c) < s.waterLevel) {
                    double by = top + (hi - s.bed(c)) * sc;
                    if (!on) { wp.moveTo(px, wy); on = true; }
                    wp.lineTo(px, by);
                }
            }
            for (int i = s.nx - 1; i >= 0; i--) if (s.bed(s.index(i, j)) < s.waterLevel) wp.lineTo(x + (i + 0.5) * s.dx * sc, wy);
            g.setColor(new Color(WATER.getRed(), WATER.getGreen(), WATER.getBlue(), 150));
            g.fill(wp);
        }
        // Gleitbahn
        Path2D bp = new Path2D.Double();
        for (int i = 0; i < s.nx; i++) {
            double px = x + (i + 0.5) * s.dx * sc, py = top + (hi - s.bed(s.index(i, j))) * sc;
            if (i == 0) bp.moveTo(px, py); else bp.lineTo(px, py);
        }
        g.setColor(DIM);
        g.setStroke(new BasicStroke(2f));
        g.draw(bp);
        // Strom: Säulen nach Geschwindigkeit
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < s.nx; i++) {
            int c = s.index(i, j);
            float hh = fr.h()[c];
            if (hh < 0.2) continue;
            double px = x + i * s.dx * sc, by = top + (hi - s.bed(c)) * sc, ty = top + (hi - s.bed(c) - hh) * sc;
            g.setColor(speedColor(fr.v()[c]));
            g.fillRect((int) px, (int) ty, Math.max(1, (int) Math.ceil(s.dx * sc)), Math.max(1, (int) (by - ty)));
        }
        // Höhen
        g.setColor(DIM);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        for (double v = Math.ceil(lo / 200) * 200; v <= hi; v += 200) {
            int py = top + (int) ((hi - v) * sc);
            g.drawString(fmt("%.0f", v), x + w - 40, py + 4);
        }
    }

    static void zufluss(Graphics2D g, List<Landslide.Entry> es, double[] sum, double t, int x, int y, int w, int h) {
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString("Zufluss über die Uferlinie s(t) – an die Welle (Phase 11)", x, y);
        if (es.isEmpty()) {
            g.setColor(DIM);
            g.drawString("kein Wasser", x, y + 30);
            return;
        }
        int top = y + 14, ph = h - 120;
        double t0 = Math.max(0, sum[0] - 5), t1 = Math.min(es.get(es.size() - 1).t(), sum[1] + 10);
        if (!(t1 > t0)) { t0 = 0; t1 = es.get(es.size() - 1).t(); }
        double rmax = Math.max(1, sum[9]), vmax = 140, hmax = Math.max(40, sum[5] * 1.1);
        g.setColor(new Color(30, 42, 54));
        g.fillRect(x, top, w, ph);
        Path2D pr = new Path2D.Double(), pv = new Path2D.Double(), ph2 = new Path2D.Double();
        boolean first = true;
        int stride = Math.max(1, es.size() / 1500);
        for (int k = 0; k < es.size(); k += stride) {
            Landslide.Entry e = es.get(k);
            if (e.t() < t0 || e.t() > t1) continue;
            double px = x + (e.t() - t0) / (t1 - t0) * w;
            double yr = top + ph - Math.max(0, e.rate()) / rmax * ph, yv = top + ph - e.speed() / vmax * ph, yh = top + ph - e.thickness() / hmax * ph;
            if (first) { pr.moveTo(px, yr); pv.moveTo(px, yv); ph2.moveTo(px, yh); first = false; }
            else { pr.lineTo(px, yr); pv.lineTo(px, yv); ph2.lineTo(px, yh); }
        }
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(110, 190, 240)); g.draw(pr);
        g.setColor(ACC); g.draw(pv);
        g.setColor(new Color(230, 120, 90)); g.draw(ph2);
        g.setStroke(new BasicStroke(1f));
        // FRITZ01: 96–110 m/s
        g.setColor(new Color(240, 200, 110, 60));
        int y110 = top + ph - (int) (110 / vmax * ph), y96 = top + ph - (int) (96 / vmax * ph);
        g.fillRect(x, y110, w, y96 - y110);
        if (t >= t0 && t <= t1) {
            int px = x + (int) ((t - t0) / (t1 - t0) * w);
            g.setColor(INK);
            g.drawLine(px, top, px, top + ph);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(DIM);
        g.drawString(fmt("%.0f s", t0), x, top + ph + 14);
        g.drawString(fmt("%.0f s", t1), x + w - 30, top + ph + 14);
        int ly = top + ph + 34;
        g.setColor(new Color(110, 190, 240)); g.drawString(fmt("Rate bis %.2e m³/s", sum[9]), x, ly);
        g.setColor(ACC); g.drawString(fmt("Geschwindigkeit (0–140 m/s; Band 96–110 m/s FRITZ01): %.0f m/s nach Rate", sum[3]), x + 190, ly);
        g.setColor(new Color(230, 120, 90)); g.drawString(fmt("Mächtigkeit senkrecht bis %.0f m (Achse 0–%.0f m)", sum[5], hmax), x, ly + 18);
        g.setColor(INK);
        g.drawString(fmt("%.2e m³ im Wasser, 90 %% in %.1f s (%.0f–%.0f s); Breite bis %.0f m", sum[8], sum[2], sum[0], sum[1], sum[7]), x, ly + 40);
        g.setColor(DIM);
        g.drawString("FRITZ01 (Versuch im Maßstab 1:675): Aufprall 96–110 m/s, Mächtigkeit bis 134 m, Dauer 6,8 s", x, ly + 58);
    }

    static int clamp(double v) { return (int) Math.max(0, Math.min(255, v)); }

    static Color mix(Color a, Color b, double t) {
        return new Color(clamp(a.getRed() * t + b.getRed() * (1 - t)), clamp(a.getGreen() * t + b.getGreen() * (1 - t)), clamp(a.getBlue() * t + b.getBlue() * (1 - t)));
    }

    // ------------------------------------------------------------------ Fenster

    /** Rechnet einen Fall durch und hält jede Sekunde einen Augenblick fest. */
    static final class Film {
        final Landslide s;
        final List<Frame> frames = new ArrayList<>();
        double[] sum;
        String text;

        Film(String preset, Landslide.Quality q) {
            s = preset(preset, q);
            frames.add(Frame.of(s));
            double next = 1;
            while (s.time() < 120 && !s.settled()) {
                s.step();
                if (s.time() >= next) { frames.add(Frame.of(s)); next += 1; }
            }
            frames.add(Frame.of(s));
            sum = s.entrySummary();
            text = text(s);
        }

        Frame at(double t) {
            Frame best = frames.get(0);
            for (Frame f : frames) if (f.t() <= t + 1e-6) best = f;
            return best;
        }
    }

    static void open() {
        Film[] film = {new Film(PRESETS[0], Landslide.Quality.LOW)};
        double[] time = {18};
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics gr) {
                Film f = film[0];
                LandslideDemo.paint((Graphics2D) gr, f.s, f.at(time[0]), f.s.entries(), f.sum, f.text, getWidth(), getHeight());
            }
        };
        view.setPreferredSize(new Dimension(1700, 1000));
        FFrame f = new FFrame("Bergsturz · com.dan.landslide · gilbertInlet1958()");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(view, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(BG);
        JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT));
        west.setBackground(BG);
        JComboBox<String> wahl = new JComboBox<>(PRESETS);
        JComboBox<Landslide.Quality> qual = new JComboBox<>(Landslide.Quality.values());
        JLabel l = new JLabel("  Zeit (s)  ");
        l.setForeground(INK);
        west.add(wahl);
        west.add(qual);
        west.add(l);
        south.add(west, BorderLayout.WEST);
        FSlider s = new FSlider(0, 120, 18);
        s.addChangeListener(e -> { time[0] = s.getValue(); view.repaint(); });
        Runnable neu = () -> {
            String p = (String) wahl.getSelectedItem();
            Landslide.Quality q = (Landslide.Quality) qual.getSelectedItem();
            new Thread(() -> {
                Film fm = new Film(p, q);
                SwingUtilities.invokeLater(() -> { film[0] = fm; f.setTitle("Bergsturz · com.dan.landslide · " + p + "()"); view.repaint(); });
            }, "landslide").start();
        };
        wahl.addActionListener(e -> neu.run());
        qual.addActionListener(e -> neu.run());
        south.add(s, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(1760, 1100);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}
