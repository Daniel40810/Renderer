package com.dan.surf.demo;

import com.dan.fframe.FFrame;
import com.dan.surf.Surf;
import com.dan.surf.Swash;
import com.dan.surf.WaveTrain;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
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
import java.util.Locale;

/**
 * Auflauf und Rücklauf ohne Grafikkarte (seit Sylt Phase 7, F8 A): drei Strände nebeneinander – flach 1:100 (Riff
 * davor, Schwallbrecher), Strand 1:20 (Sturzbrecher), Strand 1:30 wie am Roten Kliff –, je 24 Querprofile mit
 * {@link Swash} am {@link WaveTrain}. Oben der Querschnitt (Grund, Wasser jetzt, höchster Stand der letzten Minute,
 * R_2% gemessen und nach Stockdon), in der Mitte der Strand von oben (Wasser, glänzender Film, nasser und trockener
 * Sand, Blasen, Spülsaum mit Tang und Schill), unten die Höhe der Spitze über der Zeit mit den gemeldeten
 * Auflaufspitzen.
 * <p>
 * {@code SurfSwash} öffnet ein Fenster; {@code SurfSwash --bild datei.png [sekunden]} rechnet die Zeit (Vorgabe 600 s)
 * und schreibt ein Bild.
 */
public final class SurfSwash extends JPanel {
    /** Ein Strand. */
    static final class Beach {
        final String name;
        final WaveTrain train;
        final Swash swash;
        final double slope, flat, depth0, xShore;
        final int row;
        /** Spur der Spitze in Reihe row (Höhe über Ruhewasser), 120 s zu je 0,1 s, und die Spitzen. */
        final double[] trace = new double[1200], traceT = new double[1200];
        final double[] peakT = new double[400], peakZ = new double[400];
        int traceN, peaks;
        double lastSample, tipHigh = Double.NEGATIVE_INFINITY;
        /** Höchster Wasserstand je Zelle in Reihe row (fallend über eine Minute). */
        final double[] maxEta;

        Beach(String name, double slope, double flat, double depth0, double tp, long seed) {
            this.name = name;
            this.slope = slope;
            this.flat = flat;
            this.depth0 = depth0;
            xShore = flat + depth0 / slope;
            double xEnd = xShore + Math.min(2.0 / slope, 120);
            train = new WaveTrain(24, 0, xEnd, 40);
            train.profile = x -> x < flat ? -depth0 : -depth0 + (x - flat) * slope;
            train.hs = 1;
            train.tp = tp;
            train.seed = seed;
            double xa = flat + Math.max(0, depth0 - 1.6) / slope;
            swash = new Swash(train, xa, xEnd, 0.5);
            row = 12;
            maxEta = new double[swash.nx];
            java.util.Arrays.fill(maxEta, Double.NaN);
            swash.onSwash = (x, y, z, kind, s) -> {
                if (kind == Swash.Kind.RUNUP && Math.abs(y - train.rowY(row)) < 1e-6 && peaks < peakT.length) {
                    peakT[peaks] = swash.time();
                    peakZ[peaks] = s;
                    peaks++;
                }
            };
        }

        void step(double dt) {
            train.step(dt);
            swash.step(dt, train);
            double t = swash.time();
            // die Spitze ist oft nur Bruchteile einer Sekunde oben: je Probe das Höchste seit der letzten
            tipHigh = Math.max(tipHigh, swash.bed[swash.cellAt(swash.tipX(row))] - train.level);
            if (t - lastSample >= 0.1) {
                lastSample = t;
                double z = tipHigh;
                tipHigh = Double.NEGATIVE_INFINITY;
                if (traceN == trace.length) { System.arraycopy(trace, 1, trace, 0, trace.length - 1); System.arraycopy(traceT, 1, traceT, 0, traceT.length - 1); traceN--; }
                traceT[traceN] = t;
                trace[traceN++] = z;
                while (peaks > 0 && peakT[0] < t - 120) {
                    System.arraycopy(peakT, 1, peakT, 0, peaks - 1);
                    System.arraycopy(peakZ, 1, peakZ, 0, peaks - 1);
                    peaks--;
                }
            }
            double f = Math.exp(-dt / 60);
            for (int i = 0; i < swash.nx; i++) {
                double e = swash.surface(row, swash.x(i));
                double m = Double.isNaN(maxEta[i]) ? Double.NEGATIVE_INFINITY : train.level + (maxEta[i] - train.level) * f;
                maxEta[i] = Double.isNaN(e) ? (Double.isInfinite(m) ? Double.NaN : Math.max(m, swash.bed[i])) : Math.max(e, m);
            }
        }

        double stockdon() { return Swash.stockdon(train.hs / Surf.shoaling(train.tp, depth0), train.tp, slope); }
    }

    final Beach[] beaches = {
            new Beach("flach 1:100 · Riff", 1 / 100.0, 0, 3.0, 8, 3),
            new Beach("Strand 1:20", 1 / 20.0, 30, 3.0, 8, 5),
            new Beach("Strand 1:30 · wie am Kliff", 1 / 30.0, 20, 3.0, 8, 9)};

    SurfSwash() {
        setPreferredSize(new Dimension(1800, 1050));
        setBackground(BG);
    }

    void step(double dt) { for (Beach b : beaches) b.step(dt); }

    static final Color BG = new Color(14, 20, 22), INK = new Color(233, 236, 234), MUTED = new Color(147, 160, 162),
            SEA = new Color(125, 189, 179), SAND = new Color(216, 195, 154), KLIFF = new Color(224, 127, 72), GOLD = new Color(232, 178, 92);

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        draw(g, getWidth(), getHeight());
    }

    void draw(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        int cw = w / beaches.length;
        for (int k = 0; k < beaches.length; k++) panel(g, beaches[k], k * cw + 14, 10, cw - 28, h - 20);
    }

    /** Ein Strand: Querschnitt, Draufsicht, Spur. */
    void panel(Graphics2D g, Beach b, int x0, int y0, int w, int h) {
        Swash s = b.swash;
        WaveTrain t = b.train;
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.drawString(b.name, x0, y0 + 16);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(MUTED);
        double r2 = s.r2(), st = b.stockdon();
        g.drawString(String.format(Locale.GERMAN, "H_s %.1f m · T_p %.0f s · %d Spitzen · R_2%% %.2f m · Stockdon %.2f m · %d Stöße", t.hs, t.tp, s.runups, r2, st, s.collisions), x0, y0 + 34);
        // Ausschnitt längs: vom Rand bis über den höchsten Auflauf
        final double xa = s.boundaryX() - 2, xe = Math.min(s.x(s.nx - 1), b.xShore + Math.max(1.2, 1.6 * Math.max(0.3, Double.isNaN(r2) ? 0.5 : r2)) / b.slope);
        // --- Querschnitt
        int qy = y0 + 46, qh = (int) (h * 0.30);
        double zLo = -Math.min(b.depth0, 1.6) - 0.1, zHi = 1.6;
        java.util.function.DoubleUnaryOperator px = x -> x0 + (x - xa) / (xe - xa) * w;
        java.util.function.DoubleUnaryOperator pz = z -> qy + qh - (z - zLo) / (zHi - zLo) * qh;
        g.setColor(new Color(20, 30, 33));
        g.fillRect(x0, qy, w, qh);
        int r = b.row;
        Path2D water = new Path2D.Double(), sand = new Path2D.Double(), max = new Path2D.Double();
        boolean on = false, mOn = false;
        sand.moveTo(px.applyAsDouble(xa), qy + qh);
        for (int i = 0; i < s.nx; i++) {
            double x = s.x(i);
            if (x < xa || x > xe) continue;
            sand.lineTo(px.applyAsDouble(x), pz.applyAsDouble(s.bed[i]));
            double e = s.surface(r, x);
            if (!Double.isNaN(e)) { if (on) water.lineTo(px.applyAsDouble(x), pz.applyAsDouble(e)); else water.moveTo(px.applyAsDouble(x), pz.applyAsDouble(e)); on = true; } else on = false;
            double m = b.maxEta[i];
            if (!Double.isNaN(m) && m > s.bed[i] + 0.005) { if (mOn) max.lineTo(px.applyAsDouble(x), pz.applyAsDouble(m)); else max.moveTo(px.applyAsDouble(x), pz.applyAsDouble(m)); mOn = true; } else mOn = false;
        }
        sand.lineTo(px.applyAsDouble(xe), qy + qh);
        sand.closePath();
        // Sand nach Nässe einfärben: Streifen je Zelle
        for (int i = 0; i < s.nx; i++) {
            double x = s.x(i);
            if (x < xa || x > xe) continue;
            int k = r * s.nx + i;
            Color c = sandColor(s.moist[k], s.d[k] > 0.002 ? 1 : s.film[k], 0, s.wrack[k]);
            int xx = (int) px.applyAsDouble(x - s.cell / 2), ww = Math.max(1, (int) Math.ceil(s.cell / (xe - xa) * w)) + 1;
            int yy = (int) pz.applyAsDouble(s.bed[i]);
            g.setColor(c);
            g.fillRect(xx, yy, ww, qy + qh - yy);
        }
        g.setColor(new Color(60, 140, 150, 210));
        g.setStroke(new BasicStroke(2.2f));
        g.draw(water);
        g.setColor(new Color(255, 255, 255, 110));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[]{4, 4}, 0));
        g.draw(max);
        // Linien: Ruhewasser, R_2% gemessen, Stockdon
        hline(g, x0, w, pz.applyAsDouble(t.level), MUTED, "Ruhewasser");
        if (!Double.isNaN(r2)) hline(g, x0, w, pz.applyAsDouble(t.level + r2), SEA, String.format(Locale.GERMAN, "R_2%% %.2f m", r2));
        hline(g, x0, w, pz.applyAsDouble(t.level + st), GOLD, String.format(Locale.GERMAN, "Stockdon %.2f m", st));
        hline(g, x0, w, pz.applyAsDouble(s.groundwater()), new Color(120, 160, 220), "Grundwasser");
        g.setColor(MUTED);
        g.drawString("Querschnitt (Reihe " + r + "), gestrichelt: höchster Stand der letzten Minute", x0 + 4, qy + qh - 6);
        // --- Draufsicht
        int ty = qy + qh + 14, th = (int) (h * 0.36);
        BufferedImage top = new BufferedImage(w, th, BufferedImage.TYPE_INT_RGB);
        for (int yy = 0; yy < th; yy++) {
            // Reihen: die nächste (Ränder der Zungen bleiben scharf), ein wenig verwischt längs
            int rr = Math.min(s.rows - 1, (int) ((yy + 0.5) / th * s.rows + 0.35 * Math.sin(yy * 0.37)));
            for (int xx = 0; xx < w; xx++) {
                double x = xa + (xx + 0.5) / w * (xe - xa);
                int i = s.cellAt(x), k = rr * s.nx + i;
                Color c;
                double d = s.d[k];
                if (d > 0.002 && i >= s.cellAt(s.boundaryX())) {
                    double f = s.foam[k];
                    double a = Math.min(1, d / 0.6);
                    Color wc = mix(new Color(150, 170, 150), new Color(30, 90, 95), a);
                    c = mix(wc, new Color(236, 240, 236), Math.min(1, f));
                } else if (i < s.cellAt(s.boundaryX())) {
                    c = new Color(30, 80, 88);
                } else {
                    c = sandColor(s.moist[k], s.film[k], s.bubbles[k], s.wrack[k]);
                    // Schill und Tang als Punkte, Blasen als helle Punkte
                    int hsh = (xx * 73856093) ^ (yy * 19349663);
                    if (s.wrack[k] > 0.2 && (hsh & 7) == 0) c = (hsh & 8) == 0 ? new Color(40, 36, 20) : new Color(225, 220, 205);
                    if (s.bubbles[k] > 0.15 && ((hsh >> 4) & 15) < s.bubbles[k] * 6) c = new Color(245, 248, 250);
                }
                top.setRGB(xx, yy, c.getRGB());
            }
        }
        g.drawImage(top, x0, ty, null);
        g.setColor(INK);
        g.drawString("von oben: " + s.rows + " Querprofile, je 0,5 m · See links, Strand rechts", x0 + 4, ty + th - 6);
        // --- Spur der Spitze
        int sy = ty + th + 14, sh = h - (sy - y0) - 4;
        g.setColor(new Color(20, 30, 33));
        g.fillRect(x0, sy, w, sh);
        double zMin = -0.4, zMax = Math.max(1.0, 1.25 * Math.max(st, Double.isNaN(r2) ? 0 : r2));
        java.util.function.DoubleUnaryOperator sz = z -> sy + sh - (z - zMin) / (zMax - zMin) * sh;
        hline(g, x0, w, sz.applyAsDouble(0), MUTED, "");
        hline(g, x0, w, sz.applyAsDouble(st), GOLD, "");
        if (!Double.isNaN(r2)) hline(g, x0, w, sz.applyAsDouble(r2), SEA, "");
        Path2D tr = new Path2D.Double();
        double tEnd = s.time();
        for (int i = 0; i < b.traceN; i++) {
            double tt = b.traceT[i];
            if (tt < tEnd - 120) continue;
            double xx = x0 + (1 - (tEnd - tt) / 120) * w, yy = sz.applyAsDouble(b.trace[i]);
            if (tr.getCurrentPoint() == null) tr.moveTo(xx, yy); else tr.lineTo(xx, yy);
        }
        g.setColor(INK);
        g.setStroke(new BasicStroke(1.4f));
        g.draw(tr);
        g.setColor(KLIFF);
        for (int i = 0; i < b.peaks; i++) {
            double xx = x0 + (1 - (tEnd - b.peakT[i]) / 120) * w, yy = sz.applyAsDouble(b.peakZ[i]);
            g.fillOval((int) xx - 3, (int) yy - 3, 6, 6);
        }
        g.setColor(MUTED);
        g.drawString("Höhe der Spitze über Ruhewasser, letzte 2 Minuten · Punkte: gemeldete Auflaufspitzen", x0 + 4, sy + 14);
    }

    static void hline(Graphics2D g, int x0, int w, double y, Color c, String label) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{6, 4}, 0));
        g.drawLine(x0, (int) y, x0 + w, (int) y);
        if (!label.isEmpty()) g.drawString(label, x0 + w - g.getFontMetrics().stringWidth(label) - 4, (int) y - 3);
    }

    /** Sand nach Nässe, Film, Blasen, Spülsaum. */
    static Color sandColor(double moist, double film, double bubbles, double wrack) {
        Color c = mix(SAND, new Color(122, 104, 74), Math.max(0, Math.min(1, (moist - 0.05) / 0.85)));
        c = mix(c, new Color(170, 190, 200), Math.min(1, film) * 0.45);
        return c;
    }

    static Color mix(Color a, Color b, double f) {
        f = Math.max(0, Math.min(1, f));
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * f), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * f), (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * f));
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            double secs = args.length > 2 ? Double.parseDouble(args[2]) : 600;
            SurfSwash p = new SurfSwash();
            for (int i = 0; i < 60 * 60; i++) p.step(1 / 60.0);
            for (Beach b : p.beaches) b.swash.resetStatistics();
            for (int i = 0; i < (int) (secs * 60); i++) p.step(1 / 60.0);
            BufferedImage img = new BufferedImage(1800, 1050, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            p.draw(g, 1800, 1050);
            g.dispose();
            javax.imageio.ImageIO.write(img, "png", new File(args[1]));
            for (Beach b : p.beaches) System.out.println(b.name + ": " + b.swash.describe(b.stockdon()));
            System.out.println("Bild: " + args[1]);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            SurfSwash p = new SurfSwash();
            FFrame f = new FFrame("Surf · Auflauf und Rücklauf");
            JPanel root = f.getComponentPane();
            root.setLayout(new BorderLayout());
            root.add(p, BorderLayout.CENTER);
            f.setSize(1800, 1050);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            long[] last = {System.nanoTime()};
            new Timer(16, e -> {
                long now = System.nanoTime();
                double dt = Math.min(0.05, (now - last[0]) / 1e9);
                last[0] = now;
                p.step(dt);
                p.repaint();
            }).start();
        });
    }
}
