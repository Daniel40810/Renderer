package com.dan.debris.demo;

import com.dan.debris.Carpet;
import com.dan.debris.Debris;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;

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
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vorführung von {@link Debris} und {@link Carpet} (seit LituyaBay1958 Phase 12): eine Bucht von 3 × 2 km mit einem
 * Wirbel (Rankine, bis 1,5 m/s) und Südwestwind 8 m/s. Am Nordufer liegen 400 Stämme, ein paar mit Krone, ein paar
 * Eisbrocken; sie treiben, stoßen, verkeilen sich, laufen auf Grund. Links eine halbe Stunde als einzelne Stücke, rechts
 * derselbe Wald als Holzteppich über drei Tage (Nordostwind vom Ufer weg, der Wirbel abgeflaut). Fenster mit Zeitregler; {@code --bild datei.png [t]}.
 */
public final class DebrisDemo {
    private DebrisDemo() { }

    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(140, 156, 170), ACC = new Color(240, 200, 110);
    static final double T_END = 1800, W = 3000, H = 2000, DAYS = 3;

    /** Tiefe: Becken bis 60 m, Ufer elliptisch mit einer Bucht im Osten; NaN an Land. */
    static double depth(double x, double z) {
        double ex = (x - 1500) / 1350, ez = (z - 1000) / 850, r = Math.sqrt(ex * ex + ez * ez) - 0.08 * Math.sin(5 * Math.atan2(ez, ex));
        if (r >= 1) return Double.NaN;
        return Math.max(0.05, 60 * (1 - r * r));
    }

    static double[] current(double x, double z, double scale) {
        double dx = x - 1500, dz = z - 1000, r = Math.hypot(dx, dz), rc = 600, v = r < rc ? 1.5 * r / rc : 1.5 * rc / r;
        if (r < 1e-6 || Double.isNaN(depth(x, z))) return new double[2];
        return new double[]{-v * dz / r * scale, v * dx / r * scale};
    }

    static Debris.Water water(double scale) {
        return new Debris.Water() {
            public double depth(double x, double z) { return DebrisDemo.depth(x, z); }

            public double[] current(double x, double z) { return DebrisDemo.current(x, z, scale); }
        };
    }

    /** Südwestwind 8 m/s für die Stücke, Nordostwind 6 m/s (vom Ufer weg) für den Teppich; z zeigt nach Norden. */
    static final Debris.Wind WIND = (x, z, t) -> new double[]{8 * Math.sqrt(0.5), 8 * Math.sqrt(0.5)}, NE = (x, z, t) -> new double[]{-6 * Math.sqrt(0.5), -6 * Math.sqrt(0.5)};

    public static Debris debris() {
        Debris d = new Debris(water(1));
        d.name = "Bucht mit Wirbel";
        d.maxSub = 0.2; // ruhiges Wasser: der Widerstand ist halb implizit, Stöße als Impulse [G]
        d.wind = WIND;
        d.scatter(360, 900, 1650, 2100, 1760, Debris.Kind.TRUNK, 1958);
        d.scatter(30, 900, 1600, 2100, 1700, Debris.Kind.CROWN, 7);
        d.scatter(10, 1300, 1500, 1700, 1600, Debris.Kind.ICE, 11);
        return d;
    }

    /** Augenblick: je Stück {x1, z1, x2, z2, Zustand, Grau}, Zählung, Teppich. */
    public record Frame(double t, float[] logs, int[] census, float[] cover, float[] stranded, double carpetT) { }

    public static final class Film {
        public final Debris debris = debris();
        public final Carpet carpet;
        public final List<Frame> frames = new ArrayList<>();

        public Film(double tEnd) {
            carpet = new Carpet(0, 0, 75, 50, 40, water(0.2));
            carpet.wind = NE;
            carpet.like(debris, debris.logs().get(0));
            Debris copy = debris();
            copy.toCarpet(carpet, l -> true);
            frames.add(frame());
            int n = 30;
            for (int k = 1; k <= n; k++) {
                debris.run(tEnd * k / n);
                carpet.run(DAYS * 86400 * k / n);
                frames.add(frame());
            }
        }

        Frame frame() {
            List<Debris.Log> ls = debris.logs();
            float[] a = new float[ls.size() * 6];
            for (int i = 0; i < ls.size(); i++) {
                Debris.Log l = ls.get(i);
                double[] e = l.ends();
                a[6 * i] = (float) e[0]; a[6 * i + 1] = (float) e[1]; a[6 * i + 2] = (float) e[2]; a[6 * i + 3] = (float) e[3];
                a[6 * i + 4] = l.state.ordinal() + (l.kind == Debris.Kind.ICE ? 10 : l.kind == Debris.Kind.CROWN ? 20 : 0);
                a[6 * i + 5] = (float) l.diameter;
            }
            float[][] c = carpet.snapshot();
            return new Frame(debris.time(), a, debris.census(), c[0], c[1], carpet.time());
        }

        public Frame at(double t) { return frames.get((int) Math.max(0, Math.min(frames.size() - 1, Math.round(t / (T_END / 30))))); }
    }

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("--bild")) {
            double t = a.length > 2 ? Double.parseDouble(a[2]) : T_END;
            Film f = new Film(T_END);
            BufferedImage im = new BufferedImage(1800, 900, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            paint(g, f, t, 1800, 900);
            g.dispose();
            ImageIO.write(im, "png", new File(a[1]));
            System.out.println(text(f));
            System.out.println("Bild: " + a[1] + " (t = " + fmt("%.0f", f.at(t).t()) + " s)");
            return;
        }
        SwingUtilities.invokeLater(DebrisDemo::open);
    }

    public static String text(Film f) {
        StringBuilder b = new StringBuilder(f.debris.describe()).append('\n');
        for (int k = 0; k < f.frames.size(); k += 6) {
            Frame fr = f.frames.get(k);
            b.append(fmt("  t %5.0f s: %d schwimmen, %d auf Grund, %d an Land; Teppich nach %.1f Tagen%n", fr.t(), fr.census()[0], fr.census()[1], fr.census()[2], fr.carpetT() / 86400));
        }
        b.append(fmt("  %d Teilschritte, %d Berührungen, %.1f s Rechnung%n", f.debris.steps(), f.debris.contacts(), f.debris.busyMs() / 1000));
        b.append("  ").append(f.carpet.describe());
        return b.toString();
    }

    static String fmt(String f, Object... o) { return String.format(Locale.GERMAN, f, o); }

    static BufferedImage basin(int nx, int nz) {
        BufferedImage im = new BufferedImage(nx, nz, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                double x = (i + 0.5) * W / nx, z = (j + 0.5) * H / nz, d = depth(x, z);
                Color c = Double.isNaN(d) ? new Color(58, 72, 52) : new Color((int) (40 - 25 * d / 60), (int) (90 - 40 * d / 60), (int) (110 - 20 * d / 60));
                im.setRGB(i, nz - 1 - j, c.getRGB());
            }
        return im;
    }

    public static void paint(Graphics2D g, Film f, double t, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        Frame fr = f.at(t);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString("Treibgut · com.dan.debris · " + fmt("t = %.0f s", fr.t()), 20, 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(DIM);
        g.drawString(fmt("%d Stücke · Wirbel bis 1,5 m/s · Südwestwind 8 m/s · %d schwimmen, %d auf Grund, %d an Land", fr.logs().length / 6, fr.census()[0], fr.census()[1], fr.census()[2]), 20, 52);
        int pw = (w - 60) / 2, ph = (int) (pw * H / W), px = 20, py = 70;
        BufferedImage bas = basin(300, 200);
        g.drawImage(bas, px, py, pw, ph, null);
        double s = pw / W;
        float[] a = fr.logs();
        for (int i = 0; i < a.length / 6; i++) {
            int code = (int) a[6 * i + 4], st = code % 10;
            if (st == 3) continue;
            Color c = code >= 20 ? new Color(120, 170, 90) : code >= 10 ? new Color(220, 240, 250) : st == 0 ? new Color(205, 180, 140) : st == 1 ? ACC : new Color(200, 120, 90);
            g.setColor(c);
            g.setStroke(new BasicStroke((float) Math.max(1.2, a[6 * i + 5] * s * 3), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(px + a[6 * i] * s, py + ph - a[6 * i + 1] * s, px + a[6 * i + 2] * s, py + ph - a[6 * i + 3] * s));
        }
        g.setStroke(new BasicStroke(1f));
        g.setColor(DIM);
        g.drawRect(px, py, pw, ph);
        g.drawString("Stücke: Stamm (hell), mit Krone (grün), Eis (weiß), auf Grund (gelb), an Land (rot); Wasser dunkler mit der Tiefe", px, py + ph + 18);
        // Teppich
        int qx = px + pw + 20;
        g.drawImage(bas, qx, py, pw, ph, null);
        BufferedImage cov = new BufferedImage(75, 50, BufferedImage.TYPE_INT_ARGB);
        for (int j = 0; j < 50; j++)
            for (int i = 0; i < 75; i++) {
                double c = fr.cover()[j * 75 + i], sd = fr.stranded()[j * 75 + i];
                int al = (int) Math.min(255, 255 * Math.min(1, (c + sd) / 0.3));
                Color col = sd > c ? new Color(200, 120, 90, al) : new Color(205, 180, 140, al);
                cov.setRGB(i, 49 - j, col.getRGB());
            }
        g.drawImage(cov, qx, py, pw, ph, null);
        g.setColor(DIM);
        g.drawRect(qx, py, pw, ph);
        g.drawString(fmt("Derselbe Wald als Holzteppich nach %.1f Tagen, Nordostwind 6 m/s, Wirbel auf 20 %%: schwimmend hell, gestrandet rot", fr.carpetT() / 86400), qx, py + ph + 18);
        g.drawString("Windanteil " + fmt("%.1f %%", 100 * f.carpet.windage / (1 + f.carpet.windage)) + " wie ein Stamm 0,5 m; höchstens 60 % Bedeckung, der Rest staut sich", qx, py + ph + 36);
    }

    static void open() {
        Film[] film = {null};
        double[] time = {T_END / 2};
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics gr) {
                if (film[0] == null) { gr.setColor(BG); gr.fillRect(0, 0, getWidth(), getHeight()); gr.setColor(INK); gr.drawString("rechnet …", 40, 40); return; }
                DebrisDemo.paint((Graphics2D) gr, film[0], time[0], getWidth(), getHeight());
            }
        };
        view.setPreferredSize(new Dimension(1700, 820));
        FFrame f = new FFrame("Treibgut · com.dan.debris");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(view, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(BG);
        JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT));
        west.setBackground(BG);
        JLabel l = new JLabel("  Zeit (s; rechts Tage)  ");
        l.setForeground(INK);
        west.add(l);
        south.add(west, BorderLayout.WEST);
        FSlider s = new FSlider(0, (int) T_END, (int) (T_END / 2));
        s.addChangeListener(e -> { time[0] = s.getValue(); view.repaint(); });
        south.add(s, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(1760, 920);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Thread(() -> { Film fm = new Film(T_END); SwingUtilities.invokeLater(() -> { film[0] = fm; view.repaint(); }); }, "treibgut").start();
    }
}
