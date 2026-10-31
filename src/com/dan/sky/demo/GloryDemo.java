package com.dan.sky.demo;

import com.dan.sky.Droplets;
import com.dan.sky.Halo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Seit Brocken Phase 7: Glorie, Nebelbogen und Halos ohne Grafikkarte, aus denselben Tabellen, die ein Nebel als
 * Phasenfunktion nimmt ({@link Droplets}). {@code GloryDemo --bild datei.png [breite]} schreibt ein Blatt:
 * <ol>
 * <li>oben vier Glorien, je 14° × 14° um den Gegenpunkt der Sonne: Tröpfchen von 5, 10 und 20 µm (schmal verteilt,
 * σ 5 %) und 10 µm breit verteilt (σ 25 %); gestrichelt der erste rote Ring nach Laven (24° / r), golden der aus der
 * Tabelle;</li>
 * <li>unten links der Blick ±50° um den Gegenpunkt (10 µm): Glorie und Nebelbogen;</li>
 * <li>unten rechts die Helligkeit über dem Abstand vom Gegenpunkt (0° bis 60°, logarithmisch) für 5, 10 und 20 µm und
 * die Halo-Winkel (Ring, Nebensonnen über der Sonnenhöhe).</li>
 * </ol>
 * Nur JDK (Java2D).
 */
public final class GloryDemo {
    private GloryDemo() { }

    public static void main(String[] a) throws Exception {
        String out = "Glorie.png";
        int w = 1600;
        for (int i = 0; i < a.length; i++) {
            if (a[i].equals("--bild") && i + 1 < a.length) out = a[++i];
            else if (a[i].matches("\\d+")) w = Integer.parseInt(a[i]);
        }
        BufferedImage im = sheet(w);
        File f = new File(out);
        if (f.getAbsoluteFile().getParentFile() != null) f.getAbsoluteFile().getParentFile().mkdirs();
        javax.imageio.ImageIO.write(im, "png", f);
        System.out.println("Bild: " + f.getPath());
    }

    static final Color BG = new Color(15, 19, 21), INK = new Color(232, 234, 233), MUTED = new Color(147, 156, 159), NEBEL = new Color(159, 192, 206),
            GOLD = new Color(230, 185, 94);

    /** Das Blatt in der Breite w. */
    public static BufferedImage sheet(int w) {
        int pad = Math.max(12, w / 80), disc = (w - pad * 5) / 4, h = pad * 4 + disc + 40 + (int) (disc * 1.6);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        Font small = new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(11, w / 110)), big = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(13, w / 85));
        double[][] cases = {{5, 0.05}, {10, 0.05}, {20, 0.05}, {10, 0.25}};
        for (int k = 0; k < 4; k++) {
            Droplets.Table t = Droplets.table(cases[k][0], cases[k][1]);
            int x0 = pad + k * (disc + pad), y0 = pad + 30;
            g.drawImage(field(t, disc, 7.0), x0, y0, null);
            double px = disc / 14.0, cx = x0 + disc / 2.0, cy = y0 + disc / 2.0;
            double laven = 24 / cases[k][0];
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{5, 5}, 0));
            g.setColor(new Color(255, 255, 255, 170));
            g.draw(new Ellipse2D.Double(cx - laven * px, cy - laven * px, 2 * laven * px, 2 * laven * px));
            g.setStroke(new BasicStroke(1.4f));
            g.setColor(GOLD);
            double ring = t.gloryRingDeg();
            if (!Double.isNaN(ring)) g.draw(new Ellipse2D.Double(cx - ring * px, cy - ring * px, 2 * ring * px, 2 * ring * px));
            g.setColor(INK);
            g.setFont(big);
            g.drawString(String.format(Locale.GERMAN, "r = %.0f µm, σ %.0f %%", cases[k][0], cases[k][1] * 100), x0, y0 - 10);
            g.setFont(small);
            g.setColor(MUTED);
            g.drawString(String.format(Locale.GERMAN, "erster roter Ring %.2f° (Laven 24°/r: %.2f°)", ring, laven), x0, y0 + disc + 18);
        }
        int y1 = pad * 3 + 30 + disc + 30, hh = h - y1 - pad;
        int wl = hh - 24;
        Droplets.Table t10 = Droplets.table(10, 0.08);
        g.drawImage(field(t10, wl, 50.0), pad, y1, null);
        g.setColor(INK);
        g.setFont(big);
        g.drawString("±50° um den Gegenpunkt, 10 µm: Glorie und Nebelbogen", pad, y1 - 10);
        g.setFont(small);
        g.setColor(MUTED);
        g.drawString(String.format(Locale.GERMAN, "Nebelbogen bei %.1f°, Asymmetrie g %.2f", t10.fogbowDeg(), t10.asymmetry()), pad, y1 + wl + 17);
        plot(g, pad * 2 + wl, y1, w - pad * 3 - wl, hh, small, big);
        g.dispose();
        return im;
    }

    /** Ein Feld von ±half Grad um den Gegenpunkt, Farbe aus der Tabelle, belichtet auf den Wert bei 10° (oder 60°). */
    static BufferedImage field(Droplets.Table t, int n, double half) {
        BufferedImage im = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        double ref = t.luminance(180 - Math.min(10, half * 0.8));
        double k = 0.45 / Math.max(1e-6, ref);
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5 - n / 2.0) / n * 2 * half, dy = (y + 0.5 - n / 2.0) / n * 2 * half;
                double d = Math.hypot(dx, dy), th = 180 - d;
                int rgb = 0;
                for (int c = 0; c < 3; c++) {
                    double v = 1 - Math.exp(-t.at(c, th) * k * 1.6);
                    v = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
                    rgb = (rgb << 8) | Math.max(0, Math.min(255, (int) Math.round(v * 255)));
                }
                im.setRGB(x, y, rgb);
            }
        return im;
    }

    static void plot(Graphics2D g, int x0, int y0, int w, int h, Font small, Font big) {
        g.setColor(new Color(22, 27, 30));
        g.fillRect(x0, y0, w, h);
        g.setColor(INK);
        g.setFont(big);
        g.drawString("Helligkeit über dem Abstand vom Gegenpunkt (log)", x0, y0 - 10);
        double lo = Math.log10(0.01), hi = Math.log10(2);
        g.setFont(small);
        g.setColor(new Color(40, 48, 52));
        for (int d = 0; d <= 60; d += 10) {
            int x = x0 + (int) (d / 60.0 * w);
            g.drawLine(x, y0, x, y0 + h);
            g.setColor(MUTED);
            g.drawString(d + "°", x + 3, y0 + h - 4);
            g.setColor(new Color(40, 48, 52));
        }
        double[] rs = {5, 10, 20};
        Color[] cs = {new Color(168, 137, 192), NEBEL, GOLD};
        for (int k = 0; k < 3; k++) {
            Droplets.Table t = Droplets.table(rs[k], 0.08);
            g.setColor(cs[k]);
            g.setStroke(new BasicStroke(1.6f));
            int px = -1, py = -1;
            for (int i = 0; i <= w; i++) {
                double d = i / (double) w * 60, v = Math.log10(Math.max(1e-3, t.luminance(180 - d)));
                int x = x0 + i, y = y0 + (int) ((hi - v) / (hi - lo) * h);
                if (px >= 0) g.drawLine(px, py, x, y);
                px = x;
                py = y;
            }
            g.drawString(String.format(Locale.GERMAN, "%.0f µm", rs[k]), x0 + w - 60, y0 + 18 + k * 16);
        }
        g.setColor(MUTED);
        int ty = y0 + h + 20;
        g.drawString(String.format(Locale.GERMAN, "Halos (Eis): 22°-Ring rot %.2f°, blau %.2f° · Nebensonnen im Azimut: Sonne 0° %.1f°, 20° %.1f°, 40° %.1f°",
                Halo.ringDeg(Halo.RED), Halo.ringDeg(Halo.BLUE), Halo.parhelionDeg(0, Halo.RED), Halo.parhelionDeg(20, Halo.RED), Halo.parhelionDeg(40, Halo.RED)), x0, Math.min(ty, y0 + h - 22));
    }
}
