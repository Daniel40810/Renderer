package com.dan.route.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.route.TravelGrid;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
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
 * Vorführung des Laufzeitgitters (seit LituyaBay1958 Phase 7): eine gerechnete Fjordbucht, 14 × 6 km, mit einem T-Kopf,
 * einer Insel und einer flachen Schwelle am Eingang [G]. Die Front startet an einer 800 m breiten Linie im Kopf; gezeigt
 * werden die Isochronen alle 10 s (jede Minute kräftig), drei Strahlen zu „Booten“ und der Unterschied zwischen
 * linearer (c = √(g h)) und nichtlinearer Laufzeit (c = √(g (h + a)), a = 30 m). Mit Fenster läuft die Front mit einem
 * Regler; {@code --bild datei.png} schreibt den Bogen (oben 16, unten 32 Nachbarn).
 */
public final class IsochroneDemo {
    private IsochroneDemo() { }

    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(140, 156, 170), ACC = new Color(240, 200, 110);
    static final double CELL = 20;
    static final int W = 700, H = 300;
    static final double X0 = -7000, Z0 = -3000;

    /** Tiefe (m, positiv) der gerechneten Bucht an (x, z); ≤ 0 Land. */
    static double depth(double x, double z) {
        double half = 1100 + 300 * Math.sin(x / 1700.0);
        double d = half - Math.abs(z - 120 * Math.sin(x / 900.0));
        // T-Kopf: Querarm bei x = 5 500, quer über die ganze Breite
        if (x > 4800 && x < 6200) d = Math.max(d, 600 - Math.abs(x - 5500) + 2600 - Math.abs(z) - 300);
        if (x > 6200) d = Math.min(d, 6200 - x + 200);
        if (d <= 0) return 0;
        double h = Math.min(160, d * 0.35 + 5);
        // Schwelle am Eingang (wie die Moräne von Lituya, 10 m)
        if (x < -5200) h = Math.min(h, 10 + Math.max(0, -5800 - x) * 0.05);
        // Insel
        if (Math.hypot((x - 500) / 900, (z + 100) / 350) < 1) return 0;
        return h;
    }

    static TravelGrid grid(TravelGrid.Stencil s, double amplitude) {
        TravelGrid g = new TravelGrid(W, H, X0, Z0, CELL);
        for (int j = 0; j < H; j++)
            for (int i = 0; i < W; i++) g.speed[j * W + i] = (float) TravelGrid.waveSpeed(depth(g.cx(i), g.cz(j)), amplitude);
        g.stencil = s;
        g.solve(sources(g));
        return g;
    }

    /** Die Einschlaglinie: offene Zellen im Nordarm des T-Kopfs, 800 m entlang der Wand. */
    static List<TravelGrid.Source> sources(TravelGrid g) {
        List<TravelGrid.Source> s = new ArrayList<>();
        for (int j = 0; j < g.h; j++)
            for (int i = 0; i < g.w; i++) {
                double x = g.cx(i), z = g.cz(j);
                if (g.open(i, j) && Math.abs(x - 5500) < 400 && z < -1700 && (!g.open(i, j - 2) || z < -2400)) s.add(new TravelGrid.Source(i, j, 0));
            }
        return s;
    }

    static final double[][] BOATS = {{-2600, 300}, {-5600, -100}, {1800, 600}};
    static final String[] BOAT_NAMES = {"Ankerplatz", "Eingang", "hinter der Insel"};

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("--bild")) {
            BufferedImage im = sheet();
            javax.imageio.ImageIO.write(im, "png", new File(a[1]));
            System.out.println("Bild: " + a[1]);
            return;
        }
        SwingUtilities.invokeLater(IsochroneDemo::open);
    }

    static BufferedImage sheet() {
        int pw = 1400, ph = 600;
        BufferedImage im = new BufferedImage(pw, 2 * ph + 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, im.getWidth(), im.getHeight());
        TravelGrid.Stencil[] st = {TravelGrid.Stencil.N16, TravelGrid.Stencil.N32};
        for (int k = 0; k < 2; k++) {
            long t0 = System.nanoTime();
            TravelGrid lin = grid(st[k], 0), non = grid(st[k], 30);
            double ms = (System.nanoTime() - t0) / 2e6;
            g.translate(0, k * ph);
            paint(g, lin, non, pw, ph, Double.POSITIVE_INFINITY, String.format(Locale.GERMAN, "%d Nachbarn (Gitterfehler höchstens %.2f %%), %,d Zellen offen, %.0f ms je Lauf",
                    st[k].size(), st[k].error() * 100, lin.openCount(), ms));
            g.translate(0, -k * ph);
        }
        g.setColor(DIM);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString("com.dan.route · TravelGrid: Laufzeit nach Fermat als kürzester Weg über das Gitter (20 m). Isochronen alle 10 s, jede Minute kräftig; gestrichelt nichtlinear (a = 30 m). Linien zu den Booten: Strahlen.", 12, 2 * ph + 36);
        g.dispose();
        return im;
    }

    /** Zeichnet Tiefe, Isochronen (bis time), Strahlen und Zeiten; lin linear, non nichtlinear. */
    static void paint(Graphics2D g, TravelGrid lin, TravelGrid non, int pw, int ph, double time, String title) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double sc = Math.min((pw - 20) / (W * CELL), (ph - 70) / (H * CELL));
        int ox = 10, oy = 40;
        BufferedImage map = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < H; j++)
            for (int i = 0; i < W; i++) {
                double d = depth(lin.cx(i), lin.cz(j));
                int rgb;
                if (d <= 0) rgb = 0x2a2f2a;
                else {
                    double t = Math.min(1, d / 160);
                    rgb = ((int) (30 - 20 * t) << 16) | ((int) (70 - 30 * t) << 8) | (int) (95 - 30 * t);
                }
                map.setRGB(i, j, rgb);
            }
        g.drawImage(map, ox, oy, (int) (W * CELL * sc), (int) (H * CELL * sc), null);
        for (int pass = 0; pass < 2; pass++) {
            TravelGrid tg = pass == 0 ? lin : non;
            for (int t = 10; t <= 600 && t <= time; t += 10) {
                boolean minute = t % 60 == 0;
                g.setColor(pass == 0 ? (minute ? ACC : new Color(240, 200, 110, 110)) : new Color(120, 210, 255, minute ? 230 : 90));
                g.setStroke(pass == 0 ? new BasicStroke(minute ? 1.8f : 0.8f) : new BasicStroke(minute ? 1.4f : 0.7f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[]{5, 4}, 0));
                for (float[] l : tg.isochrones(t)) {
                    Path2D p = new Path2D.Double();
                    for (int k = 0; k < l.length; k += 2) {
                        double x = ox + (l[k] - X0) * sc, y = oy + (l[k + 1] - Z0) * sc;
                        if (k == 0) p.moveTo(x, y); else p.lineTo(x, y);
                    }
                    g.draw(p);
                }
            }
        }
        // laufende Front (Fenster)
        if (Double.isFinite(time)) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.5f));
            for (float[] l : lin.isochrones(time)) {
                Path2D p = new Path2D.Double();
                for (int k = 0; k < l.length; k += 2) { double x = ox + (l[k] - X0) * sc, y = oy + (l[k + 1] - Z0) * sc; if (k == 0) p.moveTo(x, y); else p.lineTo(x, y); }
                g.draw(p);
            }
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int b = 0; b < BOATS.length; b++) {
            int i = lin.cellI(BOATS[b][0]), j = lin.cellJ(BOATS[b][1]);
            float[] r = lin.ray(i, j);
            g.setColor(new Color(255, 120, 90));
            g.setStroke(new BasicStroke(1.3f));
            Path2D p = new Path2D.Double();
            for (int k = 0; k < r.length; k += 3) { double x = ox + (r[k] - X0) * sc, y = oy + (r[k + 1] - Z0) * sc; if (k == 0) p.moveTo(x, y); else p.lineTo(x, y); }
            g.draw(p);
            double bx = ox + (BOATS[b][0] - X0) * sc, by = oy + (BOATS[b][1] - Z0) * sc;
            g.fillOval((int) bx - 4, (int) by - 4, 8, 8);
            g.setColor(INK);
            g.drawString(String.format(Locale.GERMAN, "%s: %.0f s linear, %.0f s nichtlinear", BOAT_NAMES[b], lin.arrivalAt(BOATS[b][0], BOATS[b][1]), non.arrivalAt(BOATS[b][0], BOATS[b][1])),
                    (int) bx + 8, (int) by + (b == 1 ? 18 : -6));
        }
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.drawString(title, 12, 24);
    }

    private static void open() {
        TravelGrid lin = grid(TravelGrid.Stencil.N16, 0), non = grid(TravelGrid.Stencil.N16, 30);
        double[] time = {60};
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics gr) {
                Graphics2D g = (Graphics2D) gr;
                g.setColor(BG);
                g.fillRect(0, 0, getWidth(), getHeight());
                IsochroneDemo.paint(g, lin, non, getWidth(), getHeight(), time[0], String.format(Locale.GERMAN, "Front bei %.0f s (16 Nachbarn)", time[0]));
            }
        };
        view.setPreferredSize(new Dimension(1400, 640));
        FFrame f = new FFrame("Laufzeit auf dem Gitter · com.dan.route.TravelGrid");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(view, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(BG);
        JLabel l = new JLabel("  Zeit");
        l.setForeground(INK);
        south.add(l, BorderLayout.WEST);
        FSlider s = new FSlider(0, 600, 60);
        s.addChangeListener(e -> { time[0] = s.getValue(); view.repaint(); });
        south.add(s, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(1440, 760);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}
