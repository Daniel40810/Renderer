package com.dan.impact.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.impact.Channel;
import com.dan.impact.Impact;
import com.dan.impact.Inflow;
import com.dan.impact.Presets;

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
 * Vorführung von {@code com.dan.impact} (seit LituyaBay1958 Phase 10): der Laborfall FRITZ01 im Schnitt durch den
 * Gilbert Inlet in Naturmaßen. Oben der Schnitt mit Grund, Ruhespiegel, Wasser (blau, heller mit der Geschwindigkeit),
 * Granulat (ocker) und Puffer des Zulaufs (braun), dazu die Pegel bei 600, 885 und 1 100 m; unten links die Pegel über die
 * Zeit mit den Werten aus FRITZ01 und dem Kanal hinter dem Rand bei 885 m ({@link Impact.Far}), in der Mitte der Zufluss
 * s(t) und die Kaverne, rechts die Energie: Gleitkörper gegen führende Welle (Werkbuch 9.3: 2–30 %). Fenster mit
 * Zeitregler und Qualität; {@code --bild datei.png [t] [LOW|MEDIUM|HIGH]} schreibt den Bogen zur Zeit t (s).
 */
public final class ImpactDemo {
    private ImpactDemo() { }

    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(140, 156, 170), ACC = new Color(240, 200, 110),
            ROCK = new Color(58, 62, 66), GRAIN = new Color(214, 160, 82), FEED = new Color(150, 96, 52), GAUGE = new Color(120, 200, 140);
    static final double X0 = -200, X1 = 2050, Y0 = -170, Y1 = 720, T_END = 30;

    /** Ein ganzer Lauf mit Augenblicken je 0,5 s. */
    public static final class Film {
        public final Impact s;
        public final Channel channel;
        public final int channelGauge;
        public final List<Impact.Frame> frames = new ArrayList<>();
        public final double seconds;

        public Film(Impact.Quality q, double tEnd) { this(Presets.gilbertInlet1958(q), tEnd); }

        /** Ein beliebiger Fall im Schnitt des Gilbert Inlet (Pegel wie im Preset), Rand bei 885 m an den Kanal. */
        public Film(Impact sim, double tEnd) {
            s = sim;
            channel = new Channel(3000, 1, Presets.DEPTH);
            channel.fromEta = true;
            channelGauge = channel.gauge(1100 - 885);
            s.far(885, channel);
            long t0 = System.nanoTime();
            frames.add(s.frame());
            for (double t = 0.5; t <= tEnd + 1e-9; t += 0.5) { s.run(t); frames.add(s.frame()); }
            seconds = (System.nanoTime() - t0) / 1e9;
        }

        public Impact.Frame at(double t) {
            Impact.Frame best = frames.get(0);
            for (Impact.Frame f : frames) if (f.t() <= t + 1e-6) best = f;
            return best;
        }
    }

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("--bild")) {
            double t = a.length > 2 ? Double.parseDouble(a[2]) : 8;
            Impact.Quality q = a.length > 3 ? Impact.Quality.valueOf(a[3]) : Impact.Quality.LOW;
            Film f = new Film(q, Math.max(t, 24));
            BufferedImage im = new BufferedImage(1800, 1100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            paint(g, f, t, 1800, 1100);
            g.dispose();
            ImageIO.write(im, "png", new File(a[1]));
            System.out.println(text(f));
            System.out.println("Bild: " + a[1] + " (t = " + fmt("%.1f", f.at(t).t()) + " s)");
            return;
        }
        SwingUtilities.invokeLater(ImpactDemo::open);
    }

    /** Die Kennzahlen eines Laufs. */
    public static String text(Film f) {
        Impact s = f.s;
        StringBuilder b = new StringBuilder();
        b.append(fmt("%s (%s): %s, Partikel %.0f m%n", s.name, s.source, s.grain, s.dx));
        b.append(fmt("  Zufluss %s%n", s.inflow()));
        for (Impact.Gauge g : s.gauges()) b.append(fmt("  Pegel %s: höchstens %.0f m nach %.1f s%n", g.name, g.max(), g.timeOfMax()));
        double[] cm = f.channel.gaugeMax(f.channelGauge);
        b.append(fmt("  Kanal ab 885 m: bei 1 100 m %.0f m nach %.1f s; übergeben %.0f m² je Meter%n", cm[1], cm[2], f.channel.inflowVolume()));
        double[] cav = ImpactCheck.maxOf(s.cavernSeries());
        double ew = ImpactCheck.energyAt(s.energySeries(), s.gauges().get(1).timeOfMax());
        b.append(fmt("  Kaverne bis %.0f m² nach %.1f s; Energie der führenden Welle bei 885 m %.2e J/m = %.1f %% von ½ m v² = %.2e J/m%n", cav[1], cav[0], ew,
                100 * ew / s.releasedEnergy(), s.releasedEnergy()));
        b.append(fmt("  Auflauf am Gegenhang bis %.0f m; %d Schritte in %.1f s", s.runup(), s.steps(), f.seconds));
        return b.toString();
    }

    static String fmt(String f, Object... o) { return String.format(Locale.GERMAN, f, o); }

    // ------------------------------------------------------------------ Bogen

    public static void paint(Graphics2D g, Film f, double t, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        Impact.Frame fr = f.at(t);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.setColor(INK);
        g.drawString(f.s.name + " · " + fmt("t = %.1f s nach dem ersten Eintauchen", fr.t()), 20, 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(DIM);
        g.drawString(f.s.source + " · " + f.s.grain + " · Partikel " + fmt("%.0f m", f.s.dx), 20, 52);
        int sh = (int) (h * 0.56);
        schnitt(g, f, fr, 20, 66, w - 40, sh - 76);
        int by = sh + 10, bh = h - by - 20, bw = (w - 80) / 3;
        pegel(g, f, fr.t(), 20, by, bw, bh);
        zufluss(g, f, fr.t(), 40 + bw, by, bw, bh);
        energie(g, f, 60 + 2 * bw, by, bw, bh);
    }

    static void schnitt(Graphics2D g, Film f, Impact.Frame fr, int x, int y, int w, int h) {
        double sx = w / (X1 - X0), sy = h / (Y1 - Y0), sc = Math.min(sx, sy);
        int ox = x, oy = y;
        java.util.function.DoubleUnaryOperator px = v -> ox + (v - X0) * sc, py = v -> oy + (Y1 - v) * sc;
        double[][] b = f.s.bedLine();
        Path2D.Double rock = new Path2D.Double();
        rock.moveTo(px.applyAsDouble(b[0][0]), py.applyAsDouble(Y0));
        for (int i = 0; i < b[0].length; i++) rock.lineTo(px.applyAsDouble(b[0][i]), py.applyAsDouble(b[1][i]));
        rock.lineTo(px.applyAsDouble(b[0][b[0].length - 1]), py.applyAsDouble(Y0));
        rock.closePath();
        g.setColor(ROCK);
        g.fill(rock);
        g.setColor(new Color(60, 80, 100));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{6, 6}, 0));
        g.drawLine((int) px.applyAsDouble(X0), (int) py.applyAsDouble(0), (int) px.applyAsDouble(X1), (int) py.applyAsDouble(0));
        g.setStroke(new BasicStroke(1f));
        int d = Math.max(2, (int) Math.round(f.s.dx * sc));
        for (int i = 0; i < fr.x().length; i++) {
            byte k = fr.kind()[i];
            Color c;
            if (k == Impact.WATER) {
                double v = Math.min(1, fr.speed()[i] / 60);
                c = new Color((int) (40 + 150 * v), (int) (100 + 130 * v), (int) (190 + 60 * v));
            } else c = k == Impact.GRAIN ? GRAIN : FEED;
            g.setColor(c);
            g.fillRect((int) px.applyAsDouble(fr.x()[i]) - d / 2, (int) py.applyAsDouble(fr.y()[i]) - d / 2, d, d);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (Impact.Gauge gg : f.s.gauges()) {
            int gx = (int) px.applyAsDouble(gg.x);
            g.setColor(GAUGE);
            g.drawLine(gx, (int) py.applyAsDouble(Y1), gx, (int) py.applyAsDouble(-Presets.DEPTH));
            g.drawString(gg.name, gx + 4, (int) py.applyAsDouble(Y1) + 14);
        }
        g.setColor(DIM);
        for (int m = 0; m <= 600; m += 200) {
            int yy = (int) py.applyAsDouble(m);
            g.drawLine(ox, yy, ox + 6, yy);
            g.drawString(m + " m", ox + 10, yy + 4);
        }
        g.setColor(INK);
        g.drawString("Ruhespiegel 0 m, Tiefe 122 m, Hänge 45°; Wasser heller mit der Geschwindigkeit (bis 60 m/s), Granulat ocker, Zulauf braun", ox + 10, (int) py.applyAsDouble(Y0) - 6);
    }

    static void frame(Graphics2D g, int x, int y, int w, int h, String title) {
        g.setColor(new Color(24, 32, 42));
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(title, x + 12, y + 22);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    }

    static void pegel(Graphics2D g, Film f, double t, int x, int y, int w, int h) {
        frame(g, x, y, w, h, "Pegel η(t) über dem Ruhespiegel");
        int px0 = x + 44, py0 = y + 34, pw = w - 60, ph = h - 90;
        double tm = 26, em = 400, e0 = -150;
        g.setColor(new Color(50, 62, 76));
        for (int e = -100; e <= 400; e += 100) { int yy = py0 + (int) ((em - e) / (em - e0) * ph); g.drawLine(px0, yy, px0 + pw, yy); g.setColor(DIM); g.drawString(e + "", x + 8, yy + 4); g.setColor(new Color(50, 62, 76)); }
        for (int s = 0; s <= 25; s += 5) { int xx = px0 + (int) (s / tm * pw); g.drawLine(xx, py0, xx, py0 + ph); g.setColor(DIM); g.drawString(s + " s", xx - 8, py0 + ph + 14); g.setColor(new Color(50, 62, 76)); }
        Color[] cs = {new Color(110, 190, 240), ACC, new Color(230, 120, 90)};
        int k = 0;
        for (Impact.Gauge gg : f.s.gauges()) {
            g.setColor(cs[k % 3]);
            line(g, gg.series(), px0, py0, pw, ph, tm, e0, em, null);
            k++;
        }
        g.setColor(new Color(230, 120, 90));
        line(g, f.channel.gaugeSeries(f.channelGauge), px0, py0, pw, ph, tm, e0, em, new float[]{5, 4});
        // FRITZ01
        g.setColor(INK);
        int fx = px0 + (int) (16 / tm * pw), fy = py0 + (int) ((em - 152) / (em - e0) * ph);
        g.fillOval(fx - 5, fy - 5, 10, 10);
        g.drawString("FRITZ01: 152 m bei 885 m nach 16 s", fx + 8, fy - 6);
        int fx2 = px0 + (int) (11.1 / tm * pw), fy2 = py0 + (int) ((em - 200) / (em - e0) * ph);
        g.drawOval(fx2 - 5, fy2 - 5, 10, 10);
        g.drawString("über 200 m bei 600 m nach 11,1 s", fx2 + 8, fy2 + 16);
        int tx = px0 + (int) (Math.min(tm, t) / tm * pw);
        g.setColor(new Color(255, 255, 255, 70));
        g.drawLine(tx, py0, tx, py0 + ph);
        g.setColor(DIM);
        int ly = y + h - 44;
        k = 0;
        for (Impact.Gauge gg : f.s.gauges()) { g.setColor(cs[k]); g.drawString(fmt("%s bis %.0f m (%.1f s)", gg.name, gg.max(), gg.timeOfMax()), x + 12 + k * (w / 3), ly); k++; }
        double[] cm = f.channel.gaugeMax(f.channelGauge);
        g.setColor(new Color(230, 120, 90));
        g.drawString(fmt("gestrichelt: Kanal ab dem Rand bei 885 m (Impact.Far, HLL, Wasserstand) bei 1 100 m: %.0f m (%.1f s)", cm[1], cm[2]), x + 12, ly + 18);
    }

    static void line(Graphics2D g, List<double[]> s, int px0, int py0, int pw, int ph, double tm, double e0, double em, float[] dash) {
        Path2D.Double p = new Path2D.Double();
        boolean first = true;
        for (double[] e : s) {
            if (Double.isNaN(e[1]) || e[0] > tm) { first = true; continue; }
            double xx = px0 + e[0] / tm * pw, yy = py0 + (em - Math.max(e0, Math.min(em, e[1]))) / (em - e0) * ph;
            if (first) p.moveTo(xx, yy); else p.lineTo(xx, yy);
            first = false;
        }
        g.setStroke(dash == null ? new BasicStroke(1.6f) : new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, dash, 0));
        g.draw(p);
        g.setStroke(new BasicStroke(1f));
    }

    static void zufluss(Graphics2D g, Film f, double t, int x, int y, int w, int h) {
        frame(g, x, y, w, h, "Zufluss an der Uferlinie und Kaverne");
        Inflow in = f.s.inflow();
        int px0 = x + 44, py0 = y + 34, pw = w - 60, ph = (h - 110) / 2;
        double tm = 26;
        g.setColor(new Color(50, 62, 76));
        g.drawRect(px0, py0, pw, ph);
        Path2D.Double p = new Path2D.Double();
        for (int i = 0; i <= 400; i++) {
            double tt = i * tm / 400, s = in.thickness(tt);
            double xx = px0 + tt / tm * pw, yy = py0 + ph - s / 160 * ph;
            if (i == 0) p.moveTo(xx, yy); else p.lineTo(xx, yy);
        }
        g.setColor(GRAIN);
        g.setStroke(new BasicStroke(1.6f));
        g.draw(p);
        g.setStroke(new BasicStroke(1f));
        g.setColor(DIM);
        g.drawString(fmt("Mächtigkeit s(t) normal zum Hang (0–160 m), bis %.0f m, %.0f m/s", in.peak(), in.meanSpeed()), px0, py0 + ph + 14);
        int qy = py0 + ph + 30;
        g.setColor(new Color(50, 62, 76));
        g.drawRect(px0, qy, pw, ph);
        double cmax = 1;
        for (double[] c : f.s.cavernSeries()) cmax = Math.max(cmax, c[1]);
        Path2D.Double c = new Path2D.Double();
        boolean first = true;
        for (double[] e : f.s.cavernSeries()) {
            if (e[0] > tm) break;
            double xx = px0 + e[0] / tm * pw, yy = qy + ph - e[1] / cmax * ph;
            if (first) c.moveTo(xx, yy); else c.lineTo(xx, yy);
            first = false;
        }
        g.setColor(new Color(200, 210, 230));
        g.draw(c);
        double[] cv = ImpactCheck.maxOf(f.s.cavernSeries());
        g.setColor(DIM);
        g.drawString(fmt("Kaverne: Luft unter dem Ruhespiegel unter Wasser oder Granulat, bis %.0f m² je Meter nach %.1f s", cv[1], cv[0]), px0, qy + ph + 14);
        int tx = px0 + (int) (Math.min(tm, t) / tm * pw);
        g.setColor(new Color(255, 255, 255, 70));
        g.drawLine(tx, py0, tx, qy + ph);
        g.setColor(INK);
        g.drawString(fmt("%.0f m² je Meter (FRITZ01: 98,5 · 10³ t bei 1,61 t/m³), durch die Uferlinie %.0f m²", in.volume(), f.s.releasedVolume()), x + 12, y + h - 14);
    }

    static void energie(Graphics2D g, Film f, int x, int y, int w, int h) {
        frame(g, x, y, w, h, "Energie (Ideenspeicher: Energiebilanz als Fenster)");
        double es = f.s.releasedEnergy(), ew = ImpactCheck.energyAt(f.s.energySeries(), f.s.gauges().get(1).timeOfMax());
        int bx = x + 20, bw = w - 40, by = y + 50;
        g.setColor(GRAIN);
        g.fillRect(bx, by, bw, 26);
        g.setColor(BG);
        g.drawString(fmt("Gleitkörper ½ m v² = %.2e J/m", es), bx + 8, by + 18);
        int ww = (int) Math.max(2, bw * ew / Math.max(1, es));
        g.setColor(new Color(110, 190, 240));
        g.fillRect(bx, by + 40, ww, 26);
        g.setColor(INK);
        g.drawString(fmt("führende Welle bei 885 m ρ g ∫η² dx = %.2e J/m: %.1f %%", ew, 100 * ew / Math.max(1, es)), bx, by + 86);
        g.setColor(new Color(255, 255, 255, 60));
        g.fillRect(bx + (int) (0.02 * bw), by + 36, (int) (0.28 * bw), 4);
        g.setColor(DIM);
        g.drawString("Band 2–30 % (Werkbuch 9.3, Literatur), gerechnet aus H = 162 m: 7 %", bx, by + 104);
        g.setColor(INK);
        int ly = by + 140;
        for (String line : text(f).split("\n")) {
            for (String part : wrap(line.strip(), 70)) { g.drawString(part, bx, ly); ly += 16; }
            if (ly > y + h - 10) break;
        }
    }

    static List<String> wrap(String s, int n) {
        List<String> out = new ArrayList<>();
        while (s.length() > n) {
            int k = s.lastIndexOf(' ', n);
            if (k <= 0) k = n;
            out.add(s.substring(0, k));
            s = "  " + s.substring(k).strip();
        }
        out.add(s);
        return out;
    }

    // ------------------------------------------------------------------ Fenster

    static void open() {
        Film[] film = {null};
        double[] time = {8};
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics gr) {
                if (film[0] == null) {
                    gr.setColor(BG);
                    gr.fillRect(0, 0, getWidth(), getHeight());
                    gr.setColor(INK);
                    gr.drawString("rechnet …", 40, 40);
                    return;
                }
                ImpactDemo.paint((Graphics2D) gr, film[0], time[0], getWidth(), getHeight());
            }
        };
        view.setPreferredSize(new Dimension(1700, 1000));
        FFrame f = new FFrame("Eintauchen · com.dan.impact · gilbertInlet1958()");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(view, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(BG);
        JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT));
        west.setBackground(BG);
        JComboBox<Impact.Quality> qual = new JComboBox<>(Impact.Quality.values());
        JLabel l = new JLabel("  Zeit (s)  ");
        l.setForeground(INK);
        west.add(qual);
        west.add(l);
        south.add(west, BorderLayout.WEST);
        FSlider s = new FSlider(0, (int) T_END, 8);
        s.addChangeListener(e -> { time[0] = s.getValue(); view.repaint(); });
        Runnable neu = () -> {
            Impact.Quality q = (Impact.Quality) qual.getSelectedItem();
            film[0] = null;
            view.repaint();
            new Thread(() -> {
                Film fm = new Film(q, T_END);
                SwingUtilities.invokeLater(() -> { film[0] = fm; view.repaint(); });
            }, "impact").start();
        };
        qual.addActionListener(e -> neu.run());
        south.add(s, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(1760, 1100);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        neu.run();
    }
}
