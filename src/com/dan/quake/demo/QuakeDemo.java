package com.dan.quake.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.quake.Basin;
import com.dan.quake.Quake;
import com.dan.quake.Rumble;
import com.dan.quake.Slope;

import javax.imageio.ImageIO;
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
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

/**
 * Vorführung von {@code com.dan.quake} (seit LituyaBay1958 Phase 8): das Beben vom 9. Juli 1958 an der
 * Fairweather-Störung ({@link Quake#fairweather1958()}). Links die Karte mit dem Feld der größten Beschleunigung nach
 * BSSA14, der Störung (gebrochener Teil rot), der laufenden Bruchfront und den P- und S-Fronten vom Herd; rechts die
 * Zeitreihen an fünf Orten (Ost, Süd, oben), der Newmark-Block am Hang über dem Gilbert Inlet und die Seiche der Bucht.
 * Fenster mit Zeitregler; {@code --bild datei.png [t]} schreibt den Bogen zur Zeit t (s), {@code --wav datei.wav} das
 * Grollen am Gilbert Inlet.
 */
public final class QuakeDemo {
    private QuakeDemo() { }

    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(140, 156, 170), ACC = new Color(240, 200, 110);
    /** Orte: Name, x, z (m, um den Ursprung von LituyaBay1958). */
    static final Object[][] ORTE = {
            {"Gilbert Inlet", 2903.5, -3385.5}, {"Anchorage Cove", -6747.4, 2063.2}, {"Yakutat", -119122.0, -107460.0},
            {"Elfin Cove", 69033.0, 51826.0}, {"Gustavus", 104928.0, 28038.0}};
    static final double X0 = -125_000, X1 = 110_000, Z0 = -165_000, Z1 = 150_000, TMAX = 120;

    /** Hang über dem Gilbert Inlet: 40°, Schiefer φ' 35°, Platte 92 m, FS in Ruhe 1,15 [G], hangabwärts nach Südwest. */
    public static Slope gilbert() {
        Slope s = new Slope();
        s.name = "NE-Wand des Gilbert Inlet";
        s.alpha = 40; s.phi = 35; s.depth = 92; s.gamma = 27; s.downslopeAzimuth = 225;
        s.cohesion = s.cohesionFor(1.15);
        return s;
    }

    public static void main(String[] a) throws Exception {
        Quake q = Quake.fairweather1958();
        for (int i = 0; i < a.length; i++)
            if (a[i].equals("--wav")) {
                Quake.Record r = q.record(2903.5, -3385.5);
                try (FileOutputStream o = new FileOutputStream(a[i + 1])) { Rumble.wav(new Rumble().pcm(r, 0, TMAX, 22050), 22050, o); }
                System.out.println("Grollen: " + a[i + 1]);
                return;
            }
        if (a.length > 1 && a[0].equals("--bild")) {
            double t = a.length > 2 ? Double.parseDouble(a[2]) : 30;
            BufferedImage im = new BufferedImage(1800, 1100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            paint(g, q, 1800, 1100, t);
            g.dispose();
            ImageIO.write(im, "png", new File(a[1]));
            System.out.println(text(q));
            System.out.println("Bild: " + a[1]);
            return;
        }
        SwingUtilities.invokeLater(() -> open(q));
    }

    /** Die Zahlen des Bogens als Text (für Werkzeuge der Szenen). */
    public static String text(Quake q) {
        StringBuilder b = new StringBuilder(String.format(Locale.GERMAN, "Beben: Mw %.1f, Bruch %.0f km (WELLS94), Herd %.0f km tief, Bruchgeschwindigkeit %.1f km/s%n",
                q.magnitude, (q.fault.rupture(q.magnitude)[1] - q.fault.rupture(q.magnitude)[0]) / 1000, q.fault.hdepth / 1000, q.fault.vr / 1000));
        for (Object[] o : ORTE) {
            double x = (double) o[1], z = (double) o[2];
            Quake.Record r = q.record(x, z);
            double[] d = r.significantDuration();
            b.append(String.format(Locale.GERMAN, "  %-15s Rjb %5.1f km  P %5.1f s  S %5.1f s  PGA %.2f g  Arias %.2f m/s  D5–95 %3.0f s (%.0f bis %.0f s)%n", o[0], q.rjbKm(x, z), q.tP(x, z), q.tS(x, z),
                    q.pga(x, z), r.arias(), d[0], d[1], d[2]));
        }
        Slope s = gilbert();
        Slope.Result n = s.newmark(q.record(2903.5, -3385.5));
        b.append(String.format(Locale.GERMAN, "  Newmark %s: FS %.2f (c' %.0f kPa), a_c %.2f m/s² (%.3f g): Verschiebung %.2f m, versagt nach %.0f s (Zeugen: 60 bis 150 s); JIBSON93 %.2f m%n",
                s.name, s.fs(), s.cohesion, s.ac(), s.ac() / 9.81, n.total(), n.failTime(), Slope.jibson93(q.record(2903.5, -3385.5).arias(), s.ac() / 9.81)));
        Basin bs = new Basin();
        b.append(String.format(Locale.GERMAN, "  Seiche der Bucht (Merian): T1 %.0f s (%.1f min), Ausschlag am Kopf durch das Beben %.1f mm", bs.period(1), bs.period(1) / 60,
                bs.peak(q.record(2903.5, -3385.5)) * 1000));
        return b.toString();
    }

    // ------------------------------------------------------------------ Zeichnen

    static BufferedImage feld;
    static Quake feldFuer;

    /** Der ganze Bogen (Karte, Zeitreihen, Newmark, Seiche) zur Zeit t; die Szenen nehmen ihn für ihre Bilder. */
    public static void paint(Graphics2D g, Quake q, int w, int h, double t) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        int mh = h - 90, mw = (int) (mh * (X1 - X0) / (Z1 - Z0));
        karte(g, q, 10, 40, mw, mh, t);
        int rx = mw + 30, rw = w - rx - 10;
        int sh = (mh - 2 * 150 - 20) / ORTE.length;
        for (int i = 0; i < ORTE.length; i++) seismogramm(g, q, ORTE[i], rx, 40 + i * sh, rw, sh - 6, t);
        newmark(g, q, rx, 40 + ORTE.length * sh + 4, rw, 150, t);
        seiche(g, q, rx, 40 + ORTE.length * sh + 164, rw, 140);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.drawString(String.format(Locale.GERMAN, "com.dan.quake · Fairweather-Störung 1958: Mw %.1f, t = %.0f s nach dem ersten Stoß", q.magnitude, t), 12, 26);
        g.setColor(DIM);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString("Karte: größte Beschleunigung nach BSSA14 (Fels, Vs30 760 m/s), Störung aus Qfaults 2020, gebrochener Teil rot (WELLS94, ¾ nach Nordwesten [G]), Bruchfront gelb, P weiß, S orange vom Herd (58,23° N, 136,71° W, 10 km).", 12, h - 40);
        g.drawString("Rechts: synthetische Zeitreihen (Teilflächen alle 2 km, gefiltertes Rauschen, Spitze = BSSA14) Ost / Süd / oben; Newmark-Block an der NE-Wand des Gilbert Inlet (FS 1,15 [G]); Seiche der Bucht (Merian, geschlossen).", 12, h - 20);
    }

    static void karte(Graphics2D g, Quake q, int ox, int oy, int mw, int mh, double t) {
        if (feld == null || feldFuer != q) {
            feldFuer = q;
            int fw = 235, fh = 315;
            feld = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_RGB);
            for (int j = 0; j < fh; j++)
                for (int i = 0; i < fw; i++) {
                    double x = X0 + (i + 0.5) * (X1 - X0) / fw, z = Z0 + (j + 0.5) * (Z1 - Z0) / fh;
                    feld.setRGB(i, j, farbe(q.pga(x, z)));
                }
        }
        g.drawImage(feld, ox, oy, mw, mh, null);
        java.awt.Shape clip = g.getClip();
        g.clipRect(ox, oy, mw, mh);
        double sc = mw / (X1 - X0);
        // Störung
        Path2D p = new Path2D.Double(), pr = new Path2D.Double();
        double[] rup = q.fault.rupture(q.magnitude);
        for (int k = 0; k < q.fault.x.length; k++) {
            double x = ox + (q.fault.x[k] - X0) * sc, y = oy + (q.fault.z[k] - Z0) * sc;
            if (k == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        for (int k = 0; k <= 200; k++) {
            double[] pt = q.fault.point(rup[0] + (rup[1] - rup[0]) * k / 200);
            double x = ox + (pt[0] - X0) * sc, y = oy + (pt[1] - Z0) * sc;
            if (k == 0) pr.moveTo(x, y); else pr.lineTo(x, y);
        }
        g.setStroke(new BasicStroke(2));
        g.setColor(new Color(200, 200, 200));
        g.draw(p);
        g.setStroke(new BasicStroke(3.5f));
        g.setColor(new Color(230, 60, 50));
        g.draw(pr);
        // Bruchfront
        double hs = q.fault.hypoS();
        g.setColor(new Color(255, 230, 80));
        for (double s : new double[]{hs - t * q.fault.vr, hs + t * q.fault.vr}) {
            if (s < rup[0] || s > rup[1]) continue;
            double[] pt = q.fault.point(s);
            g.fill(new Ellipse2D.Double(ox + (pt[0] - X0) * sc - 6, oy + (pt[1] - Z0) * sc - 6, 12, 12));
        }
        // Herd und Fronten
        double hx = ox + (q.fault.hx - X0) * sc, hy = oy + (q.fault.hz - Z0) * sc;
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(hx - 5, hy - 5, 10, 10));
        for (double[] c : new double[][]{{q.vp, 0}, {q.vs, 1}}) {
            double d = c[0] * t, r = d > q.fault.hdepth ? Math.sqrt(d * d - q.fault.hdepth * q.fault.hdepth) * sc : 0;
            if (r <= 0) continue;
            g.setColor(c[1] == 0 ? new Color(255, 255, 255, 200) : new Color(255, 150, 60, 230));
            g.setStroke(new BasicStroke(c[1] == 0 ? 1.5f : 2.5f));
            g.draw(new Ellipse2D.Double(hx - r, hy - r, 2 * r, 2 * r));
        }
        // Orte, die gerade schütteln
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (Object[] o : ORTE) {
            double x = (double) o[1], z = (double) o[2];
            double px = ox + (x - X0) * sc, py = oy + (z - Z0) * sc, st = q.strength(x, z, t);
            double rr = 4 + 30 * st;
            g.setColor(new Color(120, 220, 255, 90));
            g.fill(new Ellipse2D.Double(px - rr, py - rr, 2 * rr, 2 * rr));
            g.setColor(INK);
            g.fill(new Ellipse2D.Double(px - 3, py - 3, 6, 6));
            g.drawString((String) o[0], (int) px + 7, (int) py + (o[0].equals("Anchorage Cove") ? 14 : -4));
        }
        // Maßstab und Farbleiste
        g.setColor(INK);
        int sx = ox + 12, sy = oy + mh - 20;
        g.fillRect(sx, sy, (int) (50_000 * sc), 3);
        g.drawString("50 km", sx, sy - 4);
        for (int k = 0; k < 100; k++) {
            g.setColor(new Color(farbe(k / 100.0 * 0.8)));
            g.fillRect(ox + mw - 130 + k, oy + 10, 1, 12);
        }
        g.setColor(INK);
        g.drawString("PGA 0 … 0,8 g", ox + mw - 130, oy + 36);
        g.setColor(new Color(255, 255, 255, 60));
        g.drawRect(ox, oy, mw, mh);
        g.setClip(clip);
    }

    static int farbe(double pga) {
        double u = Math.max(0, Math.min(1, pga / 0.8));
        int[][] st = {{14, 22, 34}, {30, 70, 110}, {60, 150, 120}, {230, 200, 80}, {230, 90, 50}, {170, 30, 60}};
        double f = u * (st.length - 1);
        int i = Math.min(st.length - 2, (int) f);
        double v = f - i;
        int r = (int) (st[i][0] + (st[i + 1][0] - st[i][0]) * v), gg = (int) (st[i][1] + (st[i + 1][1] - st[i][1]) * v), b = (int) (st[i][2] + (st[i + 1][2] - st[i][2]) * v);
        return (r << 16) | (gg << 8) | b;
    }

    static void seismogramm(Graphics2D g, Quake q, Object[] o, int x, int y, int w, int h, double t) {
        double ox = (double) o[1], oz = (double) o[2];
        Quake.Record r = q.record(ox, oz);
        g.setColor(new Color(20, 28, 38));
        g.fillRect(x, y, w, h);
        double peak = r.peakHorizontal();
        Color[] c = {new Color(120, 200, 255), new Color(255, 170, 90), new Color(180, 230, 140)};
        float[][] k = {r.east, r.south, r.up};
        for (int comp = 0; comp < 3; comp++) {
            double yc = y + h * (comp + 0.5) / 3, amp = h / 6.5 / Math.max(1e-9, peak);
            Path2D p = new Path2D.Double();
            boolean first = true;
            for (int px = 0; px < w; px++) {
                double tt = TMAX * px / w;
                double mn = 0, mx = 0;
                for (int s = 0; s < 4; s++) {
                    double v = r.at(tt + TMAX / w * s / 4)[comp == 0 ? 0 : comp == 1 ? 1 : 2];
                    mn = Math.min(mn, v); mx = Math.max(mx, v);
                }
                if (first) { p.moveTo(x + px, yc - mx * amp); first = false; } else p.lineTo(x + px, yc - mx * amp);
                p.lineTo(x + px, yc - mn * amp);
            }
            g.setColor(c[comp]);
            g.setStroke(new BasicStroke(1f));
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.draw(p);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        int tp = x + (int) (q.tP(ox, oz) / TMAX * w), ts = x + (int) (q.tS(ox, oz) / TMAX * w), tc = x + (int) (t / TMAX * w);
        g.setColor(new Color(255, 255, 255, 110));
        g.drawLine(tp, y, tp, y + h);
        g.setColor(new Color(255, 150, 60, 160));
        g.drawLine(ts, y, ts, y + h);
        g.setColor(ACC);
        g.setStroke(new BasicStroke(2));
        g.drawLine(tc, y, tc, y + h);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString((String) o[0], x + 6, y + 14);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.drawString(String.format(Locale.GERMAN, "PGA %.2f g · Arias %.2f m/s · P %.0f s · S %.0f s · Rjb %.0f km", peak / 9.81, r.arias(), q.tP(ox, oz), q.tS(ox, oz), q.rjbKm(ox, oz)), x + 140, y + 14);
        g.setColor(DIM);
        for (int s = 0; s <= TMAX; s += 20) g.drawString(String.valueOf(s), x + (int) (s / TMAX * w) + 2, y + h - 3);
    }

    static void newmark(Graphics2D g, Quake q, int x, int y, int w, int h, double t) {
        Slope s = gilbert();
        Quake.Record r = q.record(2903.5, -3385.5);
        Slope.Result n = s.newmark(r);
        g.setColor(new Color(20, 28, 38));
        g.fillRect(x, y, w, h);
        double max = Math.max(0.2, n.total() * 1.1);
        Path2D p = new Path2D.Double();
        for (int px = 0; px < w; px++) {
            double tt = TMAX * px / w;
            int i = (int) Math.round((tt - n.t0()) / n.dt());
            double d = i < 0 ? 0 : n.displacement()[Math.min(n.displacement().length - 1, i)];
            double py = y + h - 8 - d / max * (h - 30);
            if (px == 0) p.moveTo(x, py); else p.lineTo(x + px, py);
        }
        g.setColor(new Color(255, 120, 90));
        g.setStroke(new BasicStroke(2));
        g.draw(p);
        double fy = y + h - 8 - s.failAt / max * (h - 30);
        g.setColor(new Color(255, 255, 255, 90));
        g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[]{4, 4}, 0));
        g.drawLine(x, (int) fy, x + w, (int) fy);
        g.setColor(new Color(120, 200, 120, 60));
        g.fillRect(x + (int) (60 / TMAX * w), y, (int) (90 / TMAX * w), h);
        g.setColor(ACC);
        g.setStroke(new BasicStroke(2));
        int tc = x + (int) (t / TMAX * w);
        g.drawLine(tc, y, tc, y + h);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString("Newmark-Block: " + s.name, x + 6, y + 14);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.drawString(String.format(Locale.GERMAN, "α 40°, φ' 35°, Platte 92 m, FS %.2f, a_c %.3f g · Verschiebung %.2f m · versagt (10 cm) nach %.0f s · grün: Sturz nach Zeugen 60 … 150 s",
                s.fs(), s.ac() / 9.81, n.total(), n.failTime()), x + 6, y + 30);
    }

    static void seiche(Graphics2D g, Quake q, int x, int y, int w, int h) {
        Basin b = new Basin();
        Quake.Record r = q.record(2903.5, -3385.5);
        double[] eta = b.response(r);
        double mx = 1e-9;
        for (double e : eta) mx = Math.max(mx, Math.abs(e));
        g.setColor(new Color(20, 28, 38));
        g.fillRect(x, y, w, h);
        Path2D p = new Path2D.Double();
        for (int i = 0; i < eta.length; i++) {
            double px = x + (r.t0 + i * r.dt) / r.tEnd() * w, py = y + h / 2.0 - eta[i] / mx * (h / 2.0 - 22);
            if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
        }
        g.setColor(new Color(120, 200, 255));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(p);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString("Seiche der Bucht, angeregt vom Beben (ohne Welle)", x + 6, y + 14);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.drawString(String.format(Locale.GERMAN, "Merian geschlossen, L 12 km, h̄ 130 m: T1 %.0f s (%.1f min) · Ausschlag am Kopf bis %.1f mm in den ersten %.0f s: schwach, wie erwartet (9.1)",
                b.period(1), b.period(1) / 60, mx * 1000, r.tEnd()), x + 6, y + 30);
    }

    // ------------------------------------------------------------------ Fenster

    static void open(Quake q) {
        double[] time = {30};
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics gr) { QuakeDemo.paint((Graphics2D) gr, q, getWidth(), getHeight(), time[0]); }
        };
        view.setPreferredSize(new Dimension(1700, 1000));
        FFrame f = new FFrame("Erdbeben · com.dan.quake · fairweather1958()");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(view, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(BG);
        JLabel l = new JLabel("  Zeit (s)  ");
        l.setForeground(INK);
        south.add(l, BorderLayout.WEST);
        FSlider s = new FSlider(0, (int) TMAX, 30);
        s.addChangeListener(e -> { time[0] = s.getValue(); view.repaint(); });
        south.add(s, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(1760, 1100);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}
