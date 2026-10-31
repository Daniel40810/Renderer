package com.dan.rail.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.rail.Track;
import com.dan.rail.Train;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Eigene kleine Vorführung (Paketvertrag Punkt 3 und 10): eine Spirale mit 1,5 Windungen von oben, Farbe nach der
 * Höhe, darauf ein Zug, der hinauffährt; Regler für den Zeitraffer. Seit Brocken Phase 12 liegt die Spirale auf einem
 * welligen Gelände: unten der Längsschnitt mit Gelände (grau), Gradiente (Farbe nach der Steigung, rot über 3,3 %), Bahnhof
 * (eben) und Bahnübergang. Ohne Fenster schreibt {@code RailDemo --bild datei.png} ein Standbild.
 */
public final class RailDemo {
    private RailDemo() { }

    /** Welliges Gelände um die Spirale [nur für die Vorführung]. */
    static double gelaende(double x, double z, double y) { return y + 9 * Math.sin(x / 140.0) * Math.cos(z / 170.0) + 5 * Math.sin((x + z) / 60.0); }

    static Track spirale() {
        double[][] ax = Track.spirale(0, 0, 900, 600, 1.5, 1000, 1125, 2000);
        Track roh = new Track("roh", ax, Track.METERSPUR, Track.Quality.HIGH);
        int n = (int) (roh.length() / 2) + 1;
        double[] g = new double[n];
        double[][] p = new double[n][];
        for (int i = 0; i < n; i++) {
            Track.Pose q = roh.at(i * 2.0);
            g[i] = gelaende(q.x(), q.z(), q.y());
            p[i] = new double[]{q.x(), 0, q.z()};
        }
        double len = (n - 1) * 2.0;
        double[] pr = Track.profile(g, 2, 0.033, 120, new double[][]{{len - 120, len}}, 0.004, 0.3);
        for (int i = 0; i < n; i++) p[i][1] = pr[i];
        Track t = new Track("Spirale", p, Track.METERSPUR, Track.Quality.HIGH);
        t.stations.add(new Track.Station("Gipfel", len - 60, 120));
        return t;
    }

    /** Der Längsschnitt: Gelände grau, Gradiente nach der Steigung gefärbt, Bahnhof und Übergang markiert. */
    static void laengsschnitt(Graphics2D g, Track tr, int x0, int y0, int w, int h) {
        g.setColor(new Color(24, 29, 32));
        g.fillRect(x0, y0, w, h);
        double y1 = 1000 - 15, y2 = 1125 + 15, sx = w / tr.length(), sy = h / (y2 - y1);
        g.setColor(new Color(90, 96, 98));
        for (double s = 0; s < tr.length(); s += 10) {
            Track.Pose a = tr.at(s);
            double gy = gelaende(a.x(), a.z(), 1000 + 125 * s / tr.length());
            g.drawLine((int) (x0 + s * sx), (int) (y0 + h - (gy - y1) * sy), (int) (x0 + s * sx), y0 + h);
        }
        g.setStroke(new BasicStroke(2f));
        for (double s = 0; s + 10 <= tr.length(); s += 10) {
            Track.Pose a = tr.at(s), b = tr.at(s + 10);
            double gr = Math.abs(b.y() - a.y()) / 10;
            g.setColor(gr > 0.0331 ? new Color(212, 87, 74) : Color.getHSBColor((float) (0.33 - 0.25 * gr / 0.033), 0.6f, 0.9f));
            g.drawLine((int) (x0 + s * sx), (int) (y0 + h - (a.y() - y1) * sy), (int) (x0 + (s + 10) * sx), (int) (y0 + h - (b.y() - y1) * sy));
        }
        g.setColor(new Color(230, 185, 94));
        for (Track.Station st : tr.stations) {
            g.fillRect((int) (x0 + st.s0() * sx), y0 + 4, Math.max(2, (int) (st.length() * sx)), 6);
            g.drawString(st.name(), (int) (x0 + st.s0() * sx) - 40, y0 + 22);
        }
        g.setColor(new Color(232, 234, 233));
        g.drawString(String.format(java.util.Locale.GERMAN, "Längsschnitt: größte Steigung %.2f %% (Grenze 3,3 %%), Gelände grau", tr.maxGradient(10) * 100), x0 + 8, y0 + h - 8);
    }

    static BufferedImage render(Track tr, Train t, int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(15, 19, 21));
        g.fillRect(0, 0, w, h);
        int ph = h / 4;
        laengsschnitt(g, tr, 0, h - ph, w, ph);
        h -= ph;
        double sc = Math.min(w, h) / 2100.0, cx = w / 2.0, cy = h / 2.0;
        g.setStroke(new BasicStroke(3f));
        Track.Pose prev = tr.at(0);
        for (double s = 10; s <= tr.length(); s += 10) {
            Track.Pose p = tr.at(s);
            float k = (float) ((p.y() - 1000) / 125);
            g.setColor(new Color(0.42f + 0.5f * k, 0.6f + 0.1f * k, 0.47f + 0.2f * k));
            g.drawLine((int) (cx + prev.x() * sc), (int) (cy + prev.z() * sc), (int) (cx + p.x() * sc), (int) (cy + p.z() * sc));
            prev = p;
        }
        // der Zug: von der Spitze bis zum Ende
        Path2D z = new Path2D.Double();
        Track.Pose a = tr.at(t.s - t.laenge);
        z.moveTo(cx + a.x() * sc, cy + a.z() * sc);
        for (double s = t.s - t.laenge; s <= t.s; s += 2) { Track.Pose q = tr.at(s); z.lineTo(cx + q.x() * sc, cy + q.z() * sc); }
        g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(212, 87, 74));
        g.draw(z);
        g.setColor(new Color(232, 234, 233));
        g.drawString(tr.describe(), 12, 20);
        Track.Pose sp = tr.at(t.s);
        g.drawString(String.format(java.util.Locale.GERMAN, "%s · Höhe %.0f m · Steigung %.1f %% · Bogen %.0f m", t, sp.y(), sp.gradient() * 100, sp.radius()), 12, 38);
        g.drawString(String.format(java.util.Locale.GERMAN, "Last %.0f %% · %.1f Auspuffschläge je s · %d Wagen, %.0f t · %.1f min unterwegs", t.last() * 100, t.schlaege(), t.wagen(), t.masse, t.zeit() / 60), 12, 56);
        g.drawString("N ↑", w - 40, 20);
        g.dispose();
        return im;
    }

    /** Ein Brockenzug mit einem Halt in der Mitte der Spirale (seit Brocken Phase 13). */
    static Train zug(Track tr) {
        Train t = Train.brockenzug("Brockenzug");
        t.halte.add(new Train.Halt(tr.length() / 2, 60, "Mitte"));
        return t;
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            Track tr = spirale();
            Train t = zug(tr);
            for (int i = 0; i < 1200; i++) t.step(tr, 0.5);
            javax.imageio.ImageIO.write(render(tr, t, 900, 1000), "png", new File(args[1]));
            System.out.println("Bild: " + args[1]);
            return;
        }
        SwingUtilities.invokeLater(RailDemo::open);
    }

    private static void open() {
        Track tr = spirale();
        Train[] t = {zug(tr)};
        FFrame f = new FFrame("Rail-Package · Demo");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(new Color(15, 19, 21));
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g) { g.drawImage(render(tr, t[0], Math.max(1, getWidth()), Math.max(1, getHeight())), 0, 0, null); }
        };
        view.setPreferredSize(new Dimension(900, 1000));
        FSlider raffer = new FSlider(1, 30, 10);
        JLabel lbl = new JLabel("Zeitraffer 10-fach");
        lbl.setForeground(new Color(232, 234, 233));
        raffer.addChangeListener(e -> lbl.setText("Zeitraffer " + raffer.getValue() + "-fach"));
        JPanel south = new JPanel(new BorderLayout(10, 0));
        south.setBackground(new Color(15, 19, 21));
        south.add(lbl, BorderLayout.WEST);
        south.add(raffer, BorderLayout.CENTER);
        root.add(view, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(960, 1110);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Timer(40, e -> {
            t[0].step(tr, 0.04 * raffer.getValue());
            if (!t[0].faehrt) t[0] = zug(tr);
            view.repaint();
        }).start();
    }
}
