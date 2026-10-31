package com.dan.fog.demo;

import com.dan.fog.TerrainFog;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Nebel über Gelände ohne Grafikkarte (seit Brocken Phase 6): Schnitte durch einen Testberg (Kuppe 1 140 m, ein Tal
 * im Osten) für vier Lagen – Nebelmeer am Morgen, dasselbe zwei und vier Stunden später (es löst sich von unten auf),
 * Talnebel in der Kaltluft einer Nacht, Stau mit Westwind (der Gürtel am Luvhang). Grau: Dichte des Nebels, weiß der
 * Rand des Geländes.
 * <pre>TerrainFogDemo [--bild datei.png]</pre>
 */
public final class TerrainFogDemo {
    private TerrainFogDemo() { }

    /** Testberg: Kuppe im Westen, Tal bei x = 3 500, Vorland 300 m. */
    public static double ground(double x, double z) {
        double hill = 840 * Math.exp(-(x * x + z * z) / (2 * 1800.0 * 1800));
        double ridge = 360 * Math.exp(-((x - 6000) * (x - 6000)) / (2 * 1200.0 * 1200));
        return 300 + hill + ridge;
    }

    /** Kaltluft im Tal zwischen Kuppe und Rücken (m). */
    public static double cold(double x, double z) {
        double g = ground(x, z), rim = Math.min(ground(1500, z), ground(5200, z));
        return x > 1500 && x < 5200 ? Math.max(0, rim - g) * 0.6 : 0;
    }

    /** Westwind 12 m/s, am Luvhang steigt die Luft mit dem Gefälle. */
    public static double[] wind(double x, double y, double z) {
        double e = 1;
        double dgdx = (ground(x + e, z) - ground(x - e, z)) / (2 * e);
        return new double[]{12, 12 * dgdx * Math.exp(-Math.max(0, y - ground(x, z)) / 300), 0};
    }

    public static TerrainFog lage(int k) {
        TerrainFog f = new TerrainFog();
        f.ground = TerrainFogDemo::ground;
        f.cold = TerrainFogDemo::cold;
        f.wind = TerrainFogDemo::wind;
        f.mapRect = new double[]{-8000, -8000, 16_000};
        f.wisps = 0.25;
        switch (k) {
            case 0, 1, 2 -> {
                f.seaBaseTarget = 450;
                f.seaTopTarget = 850;
                f.settle();
                f.heating = 0.4;
                for (int i = 0; i < k * 2 * 60; i++) f.step(60);
            }
            case 3 -> { f.valleyFill = 1.0; f.settle(); }
            default -> { f.capBase = 980; f.settle(); }
        }
        return f;
    }

    static final String[] NAMEN = {"Nebelmeer 450–850 m am Morgen", "zwei Stunden Sonne: es löst sich von unten", "vier Stunden: fort",
            "Talnebel in der Kaltluft", "Stau ab 980 m, Westwind 12 m/s"};

    public static BufferedImage bild(int w) {
        int ph = w * 3 / 10, n = NAMEN.length;
        BufferedImage im = new BufferedImage(w, ph * n, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double x0 = -7000, x1 = 8000, y0 = 250, y1 = 1450;
        for (int k = 0; k < n; k++) {
            TerrainFog f = lage(k);
            int oy = k * ph;
            for (int py = 0; py < ph; py++)
                for (int px = 0; px < w; px++) {
                    double x = x0 + (px + 0.5) / w * (x1 - x0), y = y1 - (py + 0.5) / ph * (y1 - y0);
                    double gr = ground(x, 0);
                    int c;
                    if (y < gr) c = 0x2E3B2C;
                    else {
                        double d = f.density(x, y, 0) * 120;
                        double sky = 0.55 + 0.25 * (py / (double) ph);
                        double v = Math.min(1, d);
                        int r = (int) (255 * (sky * 0.55 * (1 - v) + 0.92 * v)), gg = (int) (255 * (sky * 0.68 * (1 - v) + 0.93 * v)), b = (int) (255 * (sky * 0.9 * (1 - v) + 0.95 * v));
                        c = (r << 16) | (gg << 8) | b;
                    }
                    im.setRGB(px, oy + py, c);
                }
            Path2D p = new Path2D.Double();
            for (int px = 0; px <= w; px++) {
                double x = x0 + px / (double) w * (x1 - x0);
                double py = (y1 - ground(x, 0)) / (y1 - y0) * ph + oy;
                if (px == 0) p.moveTo(px, py); else p.lineTo(px, py);
            }
            g.setColor(new Color(235, 238, 236));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(p);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(11, w / 70)));
            g.setColor(new Color(20, 24, 26));
            g.drawString(NAMEN[k] + " · " + f.describe(), 10, oy + 18);
        }
        g.dispose();
        return im;
    }

    public static void main(String[] a) throws Exception {
        String out = "terrainfog.png";
        for (int i = 0; i < a.length; i++) if (a[i].equals("--bild") && i + 1 < a.length) out = a[++i];
        File f = new File(out);
        ImageIO.write(bild(1200), "png", f);
        System.out.println(String.format(Locale.GERMAN, "Bild: %s", f.getPath()));
    }
}
