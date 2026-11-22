package com.dan.lauf.demo;

import com.dan.core.gitter.Gitter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

/** Status-Bild {@code P5_Tide.png} von {@link TideCheck} (Werkbuch HalligHooge Teil A, 12.6, P5d). */
final class TideBild {
    /** M2 über G0: Amplitude (m) und Phase (Grad) des Laufs und von EOT20; Amphidromien als {Zelle Lauf, Zelle EOT20}. */
    record Tide(Gitter g, double[] modell, double[] modellG, double[] eot, double[] eotG, List<double[]> amphidromien) {
        Tide(Gitter g, double[] modell, double[] modellG, double[] eot, double[] eotG) { this(g, modell, modellG, eot, eotG, new ArrayList<>()); }
    }

    /** Hooge am höchsten Stand des Hafens: Tiefe (m) und η je Zelle des Fensters von G3, der Ring. */
    record Hooge(Gitter g, float[] tiefe, double[] eta, Instant zeit, boolean[] ring) { }

    static final class Daten {
        String titel = "P5d – Tide über G0 → G1 → G2 (TideCheck)";
        Tide tide;
        Hooge hooge;
        /** Je Pegel {Zeiten (s seit 1970), Lauf, astronomische Tide ohne Mittel, Beobachtung}. */
        final Map<String, double[][]> pegelTide = new LinkedHashMap<>();
        /** Je Pegel {Zeiten, Lauf, Beobachtung (oder null)}. */
        final Map<String, double[][]> pegelSturm = new LinkedHashMap<>();
    }

    static final int B = 1800, H = 1120;
    static final Color HINTERGRUND = new Color(250, 250, 247), TEXT = new Color(40, 40, 40), GITTER = new Color(215, 215, 210);
    static final Color[] FARBEN = {new Color(31, 119, 180), new Color(214, 39, 40), new Color(44, 160, 44), new Color(148, 103, 189), new Color(140, 86, 75)};

    static Path schreibe(Daten d, Path datei) throws Exception {
        BufferedImage bild = new BufferedImage(B, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bild.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(HINTERGRUND);
        g.fillRect(0, 0, B, H);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.drawString(d.titel, 30, 38);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        if (d.tide != null) {
            karte(g, d.tide, false, 30, 70, 560, 560, "M2 im Lauf (Amplitude, Phase alle 30°)");
            karte(g, d.tide, true, 620, 70, 560, 560, "M2 aus EOT20");
        }
        if (d.hooge != null) hooge(g, d.hooge, 1210, 70, 560, 560);
        int y = 730;
        if (!d.pegelTide.isEmpty()) {
            kurven(g, d.pegelTide, true, 30, y, 820, 290, "Tide allein: Lauf (Linie) gegen astronomische Tide der Beobachtung (gestrichelt), ohne Mittel, erste 3 Tage");
        }
        if (!d.pegelSturm.isEmpty()) {
            kurven(g, d.pegelSturm, false, 930, y, 800, 290, "Sturmflut: Lauf (Linie) gegen Beobachtung (gestrichelt), m NHN");
        }
        g.dispose();
        Files.createDirectories(datei.toAbsolutePath().getParent());
        ImageIO.write(bild, "png", datei.toFile());
        return datei;
    }

    // ------------------------------------------------------------------ M2 über der Nordsee

    static final double LON0 = -5, LON1 = 13, LAT0 = 49, LAT1 = 62;

    static void karte(Graphics2D g, Tide t, boolean eot, int x0, int y0, int b, int h, String titel) {
        Gitter gi = t.g;
        double[] a = eot ? t.eot : t.modell, ph = eot ? t.eotG : t.modellG;
        BufferedImage im = new BufferedImage(b, h, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < h; py++)
            for (int px = 0; px < b; px++) {
                double lon = LON0 + (LON1 - LON0) * (px + 0.5) / b, lat = LAT1 - (LAT1 - LAT0) * (py + 0.5) / h;
                int i = gi.i(lon), j = gi.j(lat);
                Color c = new Color(205, 200, 185);
                if (i >= 0 && j >= 0 && i < gi.nx && j < gi.ny) {
                    double v = a[gi.index(i, j)];
                    if (!Double.isNaN(v)) c = farbe(Math.min(1, v / 2.0));
                }
                im.setRGB(px, py, c.getRGB());
            }
        g.drawImage(im, x0, y0, null);
        // Phasenlinien alle 30° (Zellen, an denen die Phase eine Schwelle quert)
        g.setColor(new Color(20, 20, 20, 150));
        for (int j = 0; j < gi.ny - 1; j++)
            for (int i = 0; i < gi.nx - 1; i++) {
                int c = gi.index(i, j), r = gi.index(i + 1, j), o = gi.index(i, j + 1);
                if (Double.isNaN(a[c]) || Double.isNaN(a[r]) || Double.isNaN(a[o])) continue;
                double lon = gi.x(i), lat = gi.y(j);
                if (lon < LON0 || lon > LON1 || lat < LAT0 || lat > LAT1) continue;
                if (quert(ph[c], ph[r]) || quert(ph[c], ph[o])) {
                    int px = (int) (x0 + (lon - LON0) / (LON1 - LON0) * b), py = (int) (y0 + (LAT1 - lat) / (LAT1 - LAT0) * h);
                    g.fillRect(px, py, 2, 2);
                }
            }
        // Amphidromien: Kreis Lauf, Kreuz EOT20
        g.setStroke(new BasicStroke(2.5f));
        for (double[] am : t.amphidromien) {
            for (int k = 0; k < 2; k++) {
                int c = (int) am[k];
                int px = (int) (x0 + (gi.x(c % gi.nx) - LON0) / (LON1 - LON0) * b), py = (int) (y0 + (LAT1 - gi.y(c / gi.nx)) / (LAT1 - LAT0) * h);
                g.setColor(k == 0 ? Color.WHITE : Color.BLACK);
                if (k == 0) g.drawOval(px - 9, py - 9, 18, 18);
                else { g.drawLine(px - 7, py - 7, px + 7, py + 7); g.drawLine(px - 7, py + 7, px + 7, py - 7); }
            }
        }
        g.setStroke(new BasicStroke(1));
        g.setColor(TEXT);
        g.drawRect(x0, y0, b, h);
        g.drawString(titel, x0, y0 - 8);
        legende(g, x0, y0 + h + 10, b);
    }

    static boolean quert(double a, double b) {
        for (int s = 0; s < 360; s += 30) {
            double da = ((a - s) % 360 + 360) % 360, db = ((b - s) % 360 + 360) % 360;
            if (Math.abs(da - db) > 180) continue;
            if ((da < 15 || da > 345) != (db < 15 || db > 345) && Math.abs(TideCheck.winkel(a - b)) < 60) return true;
        }
        return false;
    }

    static void legende(Graphics2D g, int x0, int y0, int b) {
        for (int px = 0; px < b; px++) {
            g.setColor(farbe((double) px / b));
            g.drawLine(x0 + px, y0, x0 + px, y0 + 10);
        }
        g.setColor(TEXT);
        g.drawString("0 m", x0, y0 + 26);
        g.drawString("2 m", x0 + b - 28, y0 + 26);
        g.drawString("○ Lauf   × EOT20", x0 + b / 2 - 60, y0 + 26);
    }

    static Color farbe(double v) {
        v = Math.max(0, Math.min(1, v));
        float[][] st = {{0.05f, 0.10f, 0.45f}, {0.10f, 0.45f, 0.80f}, {0.35f, 0.80f, 0.75f}, {0.95f, 0.85f, 0.35f}, {0.80f, 0.20f, 0.15f}};
        double p = v * (st.length - 1);
        int k = Math.min(st.length - 2, (int) p);
        double w = p - k;
        return new Color((float) (st[k][0] + w * (st[k + 1][0] - st[k][0])), (float) (st[k][1] + w * (st[k + 1][1] - st[k][1])), (float) (st[k][2] + w * (st[k + 1][2] - st[k][2])));
    }

    // ------------------------------------------------------------------ Hooge

    static void hooge(Graphics2D g, Hooge ho, int x0, int y0, int b, int h) {
        Gitter gi = ho.g;
        double s = Math.min((double) b / gi.nx, (double) h / gi.ny);
        int bw = (int) (gi.nx * s), bh = (int) (gi.ny * s);
        BufferedImage im = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < bh; py++)
            for (int px = 0; px < bw; px++) {
                int i = (int) (px / s), j = gi.ny - 1 - (int) (py / s);
                int c = gi.index(i, j);
                double t = ho.tiefe[c];
                Color f = t < 0.01 ? new Color(150, 170, 110) : wasser(Math.min(1, t / 4.0));
                if (ho.ring != null && ho.ring[c] && t < 0.01) f = new Color(120, 150, 90);
                im.setRGB(px, py, f.getRGB());
            }
        g.drawImage(im, x0, y0, null);
        g.setColor(TEXT);
        g.drawRect(x0, y0, bw, bh);
        g.drawString("Hooge (G3, 20 m) am höchsten Stand des Hafens, " + TideCheck.uhr(ho.zeit), x0, y0 - 8);
        for (int px = 0; px < 200; px++) {
            g.setColor(wasser(px / 200.0));
            g.drawLine(x0 + px, y0 + bh + 10, x0 + px, y0 + bh + 20);
        }
        g.setColor(TEXT);
        g.drawString("Tiefe 0 … 4 m", x0 + 210, y0 + bh + 21);
        g.drawString("grün: trocken (dunkler im Ring Hooge)", x0, y0 + bh + 42);
    }

    static Color wasser(double v) {
        return new Color((int) (200 - 170 * v), (int) (225 - 140 * v), (int) (245 - 90 * v));
    }

    // ------------------------------------------------------------------ Kurven

    static void kurven(Graphics2D g, Map<String, double[][]> m, boolean ersteTage, int x0, int y0, int b, int h, String titel) {
        double t0 = Double.MAX_VALUE, t1 = -Double.MAX_VALUE, lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double[][] r : m.values()) {
            double a = r[0][0], e = ersteTage ? Math.min(r[0][r[0].length - 1], a + 3 * 86400) : r[0][r[0].length - 1];
            t0 = Math.min(t0, a);
            t1 = Math.max(t1, e);
        }
        for (double[][] r : m.values())
            for (int s = 1; s < r.length; s++) {
                if (r[s] == null || ersteTage && s == 3) continue;
                for (int k = 0; k < r[0].length; k++) if (r[0][k] <= t1 && !Double.isNaN(r[s][k])) { lo = Math.min(lo, r[s][k]); hi = Math.max(hi, r[s][k]); }
            }
        lo = Math.floor(lo * 2) / 2;
        hi = Math.ceil(hi * 2) / 2;
        g.setColor(TEXT);
        g.drawString(titel, x0, y0 - 8);
        g.setColor(GITTER);
        for (double v = lo; v <= hi + 1e-9; v += 0.5) {
            int py = (int) (y0 + h - (v - lo) / (hi - lo) * h);
            g.drawLine(x0, py, x0 + b, py);
            g.setColor(TEXT);
            g.drawString(String.format(Locale.GERMAN, "%+.1f", v), x0 - 2 + b + 4, py + 5);
            g.setColor(GITTER);
        }
        for (double t = Math.ceil(t0 / 86400) * 86400; t <= t1; t += 86400) {
            int px = (int) (x0 + (t - t0) / (t1 - t0) * b);
            g.drawLine(px, y0, px, y0 + h);
            g.setColor(TEXT);
            g.drawString(Instant.ofEpochSecond((long) t).toString().substring(5, 10), px + 3, y0 + h + 16);
            g.setColor(GITTER);
        }
        g.setColor(TEXT);
        g.drawRect(x0, y0, b, h);
        int f = 0, ly = y0 + h + 36, lx = x0;
        for (Map.Entry<String, double[][]> e : m.entrySet()) {
            Color c = FARBEN[f++ % FARBEN.length];
            double[][] r = e.getValue();
            for (int s = 1; s <= 2; s++) {
                if (r[s] == null) continue;
                g.setColor(c);
                g.setStroke(s == 1 ? new BasicStroke(1.8f) : new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[] {6, 4}, 0));
                double[] y = ersteTage || s == 1 ? r[s] : r[2];
                Path2D p = new Path2D.Double();
                boolean an = false;
                for (int k = 0; k < r[0].length; k++) {
                    if (r[0][k] > t1 || Double.isNaN(y[k])) { an = false; continue; }
                    double px = x0 + (r[0][k] - t0) / (t1 - t0) * b, py = y0 + h - (y[k] - lo) / (hi - lo) * h;
                    if (an) p.lineTo(px, py);
                    else p.moveTo(px, py);
                    an = true;
                }
                g.draw(p);
            }
            g.setStroke(new BasicStroke(1));
            g.setColor(c);
            int bt = 18 + g.getFontMetrics().stringWidth(e.getKey());
            if (lx > x0 && lx + bt > x0 + b) { lx = x0; ly += 22; }
            g.fillRect(lx, ly - 10, 14, 10);
            g.setColor(TEXT);
            g.drawString(e.getKey(), lx + 18, ly);
            lx += bt + 24;
        }
    }

    private TideBild() { }
}
