package com.dan.core.demo;

import com.dan.core.astro.Gestirne;
import com.dan.core.astro.Partialtide;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Subgrid;
import com.dan.core.pruef.Harmonik;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Status-Bild des Kerns ohne Fenster (Werkbuch HalligHooge Teil A, Phase P1): vier Tafeln aus denselben Klassen, die
 * {@link CoreCheck} prüft.
 * <ol>
 * <li>Gleichgewichtstide aus Sonne und Mond auf dem Schelfgitter G0, mit den Gebieten G1 bis G3,</li>
 * <li>das Prüfstück Watt mit dem Rechengitter und den Kanten, die bei η = −1,0 m Wasser führen,</li>
 * <li>Speicherkurve V(η) einer Zelle am Priel: feines Gelände, Tabelle mit 12 Stufen, Zelle mit mittlerer Höhe,</li>
 * <li>Gleichgewichtstide am Äquator über 30 Tage und die Amplituden der Partialtiden gegen Cartwright.</li>
 * </ol>
 * Aufruf: {@code java com.dan.core.demo.CoreBild [datei.png]}; Vorgabe {@code docs/status/P1_Core.png}.
 */
public final class CoreBild {
    private static final Color GRUND = new Color(0x14181D), TAFEL = new Color(0x1B2128), TEXT = new Color(0xD8DEE6), MATT = new Color(0x7F8B99), LINIE = new Color(0x39424D),
            WASSER = new Color(0x3FA7D6), SAND = new Color(0xD9B26F), ROT = new Color(0xE0645C), GRUEN = new Color(0x6BC48A);

    public static void main(String[] args) throws Exception {
        File ziel = new File(args.length > 0 ? args[0] : "docs/status/P1_Core.png");
        int w = 1800, h = 1160, rand = 24, tw = (w - 3 * rand) / 2, th = (h - 3 * rand - 40) / 2;
        BufferedImage bild = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bild.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(GRUND);
        g.fillRect(0, 0, w, h);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString("com.dan.core – Status P1: Raum, Zeit, Gitter mit Subgrid, Prüfrahmen", rand, 34);
        int y0 = 40 + rand;
        schelf(tafel(g, rand, y0, tw, th, "Gleichgewichtstide aus Sonne und Mond auf G0 (20.03.2026 12:00 UTC, mit Faktor 0,69) und die Gitterkette"), tw, th);
        watt(tafel(g, 2 * rand + tw, y0, tw, th, "Prüfstück Watt, 24 × 16 Zellen zu 20 m über 1-m-Gelände: Kanten mit Leitwert bei η = −1,0 m"), tw, th);
        kurve(tafel(g, rand, y0 + th + rand, tw, th, "Speicherkurve V(η) einer Zelle am Priel: Gelände, Tabelle mit 12 Stufen, mittlere Höhe"), tw, th);
        tiden(tafel(g, 2 * rand + tw, y0 + th + rand, tw, th, "Gleichgewichtstide am Äquator (30 Tage) und Amplituden aus 369 Tagen gegen Cartwright"), tw, th);
        g.dispose();
        if (ziel.getParentFile() != null) ziel.getParentFile().mkdirs();
        ImageIO.write(bild, "png", ziel);
        System.out.println("Status-Bild: " + ziel.getAbsolutePath());
    }

    /** Zeichnet den Rahmen einer Tafel und gibt eine Fläche zurück, deren Ursprung links oben in der Tafel liegt. */
    private static Graphics2D tafel(Graphics2D g, int x, int y, int w, int h, String titel) {
        g.setColor(TAFEL);
        g.fillRoundRect(x, y, w, h, 14, 14);
        Graphics2D t = (Graphics2D) g.create(x, y, w, h);
        t.setColor(TEXT);
        t.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        t.drawString(titel, 14, 24);
        return t;
    }

    private static Color farbe(double v, Color a, Color b) {
        v = Math.max(0, Math.min(1, v));
        return new Color((int) (a.getRed() + v * (b.getRed() - a.getRed())), (int) (a.getGreen() + v * (b.getGreen() - a.getGreen())), (int) (a.getBlue() + v * (b.getBlue() - a.getBlue())));
    }

    // ------------------------------------------------------------------ 1 Schelf

    private static void schelf(Graphics2D g, int w, int h) {
        Gitter g0 = CoreCheck.G0;
        Gestirne.Stand st = Gestirne.bei(Instant.parse("2026-03-20T12:00:00Z"));
        int x0 = 14, y0 = 40, pw = (int) ((h - 60) * 28.0 / 21 * 0.62), ph = h - 60;                 // Länge gestaucht wie bei 54° N
        double groesste = 0;
        double[] eta = new double[g0.zellen()];
        for (int j = 0; j < g0.ny; j++)
            for (int i = 0; i < g0.nx; i++) {
                eta[g0.index(i, j)] = Gestirne.LOVE * st.gleichgewicht(g0.x(i), g0.y(j));
                groesste = Math.max(groesste, Math.abs(eta[g0.index(i, j)]));
            }
        BufferedImage b = new BufferedImage(g0.nx, g0.ny, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < g0.ny; j++)
            for (int i = 0; i < g0.nx; i++) {
                double v = eta[g0.index(i, j)] / groesste;
                b.setRGB(i, g0.ny - 1 - j, (v >= 0 ? farbe(v, TAFEL, WASSER) : farbe(-v, TAFEL, SAND)).getRGB());
            }
        g.drawImage(b, x0, y0, pw, ph, null);
        g.setColor(LINIE);
        g.drawRect(x0, y0, pw, ph);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        Gitter[] kette = {CoreCheck.G1, CoreCheck.G2, CoreCheck.G3};
        Color[] farben = {TEXT, GRUEN, ROT};
        for (int k = 0; k < kette.length; k++) {
            Path2D p = new Path2D.Double();
            Gitter q = kette[k];
            for (int s = 0; s <= 80; s++) {
                double a = (s % 20) / 20.0, x = s < 20 ? q.x0 + a * (q.x1() - q.x0) : s < 40 ? q.x1() : s < 60 ? q.x1() - a * (q.x1() - q.x0) : q.x0,
                        y = s < 20 ? q.y0 : s < 40 ? q.y0 + a * (q.y1() - q.y0) : s < 60 ? q.y1() : q.y1() - a * (q.y1() - q.y0);
                double[] ll = q.grad(x, y);
                double px = x0 + (ll[0] - g0.x0) / (g0.x1() - g0.x0) * pw, py = y0 + (g0.y1() - ll[1]) / (g0.y1() - g0.y0) * ph;
                if (s == 0) p.moveTo(px, py); else p.lineTo(px, py);
            }
            g.setColor(farben[k]);
            g.setStroke(new BasicStroke(1.6f));
            g.draw(p);
        }
        int tx = x0 + pw + 18, ty = y0 + 14;
        g.setColor(TEXT);
        String[] zeilen = {g0.name + "  " + g0.nx + " × " + g0.ny + "  1/12° × 1/18°", CoreCheck.G1.name + "  " + CoreCheck.G1.nx + " × " + CoreCheck.G1.ny + "  1 km",
                CoreCheck.G2.name + "  " + CoreCheck.G2.nx + " × " + CoreCheck.G2.ny + "  200 m", CoreCheck.G3.name + "  " + CoreCheck.G3.nx + " × " + CoreCheck.G3.ny + "  20 m"};
        Color[] zf = {MATT, TEXT, GRUEN, ROT};
        for (int k = 0; k < zeilen.length; k++) {
            g.setColor(zf[k]);
            g.drawString(zeilen[k], tx, ty + 20 * k);
        }
        g.setColor(MATT);
        g.drawString(String.format(Locale.GERMAN, "Farbe: −%.2f m (Sand) bis +%.2f m (Blau)", groesste, groesste), tx, ty + 100);
        g.drawString(String.format(Locale.GERMAN, "Mond im Zenit über %.1f° O, %.1f° N", st.mondLaengeGrad(), Math.toDegrees(st.mondDek)), tx, ty + 120);
        g.drawString(String.format(Locale.GERMAN, "Sonne im Zenit über %.1f° O, %.1f° N", st.sonneLaengeGrad(), Math.toDegrees(st.sonneDek)), tx, ty + 140);
        g.drawString("15° W – 13° O, 43 – 64° N", tx, ty + 170);
    }

    // ------------------------------------------------------------------ 2 Watt

    private static void watt(Graphics2D g, int w, int h) {
        Gitter q = Probe.G;
        int f = Probe.FEIN, nx = q.nx * f, ny = q.ny * f;
        double[] z = Probe.watt(2026);
        Subgrid s = Subgrid.bauen(q, f, Subgrid.aus(z, nx, ny));
        double eta = -1.0;
        BufferedImage b = new BufferedImage(nx, ny, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                double v = z[i + nx * j];
                Color c = v < eta ? farbe((eta - v) / 1.2, new Color(0x2B6F93), new Color(0x123A52)) : v < 1 ? farbe((v + 1) / 2, new Color(0x5B5140), SAND) : farbe((v - 1) / 3.5, new Color(0x6E8F4E),
                        new Color(0xC9D7A0));
                b.setRGB(i, ny - 1 - j, c.getRGB());
            }
        int x0 = 14, y0 = 40, ph = h - 60, pw = Math.min(w - 28, ph * nx / ny);
        ph = pw * ny / nx;
        g.drawImage(b, x0, y0, pw, ph, null);
        double m = pw / (double) nx;
        g.setColor(new Color(255, 255, 255, 34));
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= q.nx; i++) g.drawLine((int) (x0 + i * f * m), y0, (int) (x0 + i * f * m), y0 + ph);
        for (int j = 0; j <= q.ny; j++) g.drawLine(x0, (int) (y0 + j * f * m), x0 + pw, (int) (y0 + j * f * m));
        g.setColor(new Color(0x7FE3FF));
        g.setStroke(new BasicStroke(3f));
        int nass = 0;
        for (int j = 0; j < q.ny; j++)
            for (int i = 0; i <= q.nx; i++)
                if (s.leitwert(s.kanteX(i, j), eta) > 0) {
                    g.drawLine((int) (x0 + i * f * m), (int) (y0 + ph - (j + 1) * f * m), (int) (x0 + i * f * m), (int) (y0 + ph - j * f * m));
                    nass++;
                }
        for (int j = 0; j <= q.ny; j++)
            for (int i = 0; i < q.nx; i++)
                if (s.leitwert(s.kanteY(i, j), eta) > 0) {
                    g.drawLine((int) (x0 + i * f * m), (int) (y0 + ph - j * f * m), (int) (x0 + (i + 1) * f * m), (int) (y0 + ph - j * f * m));
                    nass++;
                }
        g.setColor(MATT);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString(nass + " Kanten führen Wasser (hell); keine Zelle liegt im Mittel unter −1,0 m: Ohne Subgrid wäre der Priel trocken.", x0, y0 + ph + 16);
    }

    // ------------------------------------------------------------------ 3 Speicherkurve

    private static void kurve(Graphics2D g, int w, int h) {
        Gitter q = Probe.G;
        int f = Probe.FEIN, nx = q.nx * f;
        double[] z = Probe.watt(2026);
        Subgrid s = Subgrid.bauen(q, f, Subgrid.aus(z, nx, q.ny * f));
        int zelle = -1;                                                                    // die Zelle mit der größten Spanne am Priel
        double spanne = 0;
        for (int c = 0; c < q.zellen(); c++) if (s.hoechste(c) < 0.6 && s.hoechste(c) - s.grund(c) > spanne) { spanne = s.hoechste(c) - s.grund(c); zelle = c; }
        int ci = zelle % q.nx, cj = zelle / q.nx, x0 = 70, y0 = 46, pw = w - 110, ph = h - 100;
        double e0 = s.grund(zelle) - 0.2, e1 = s.hoechste(zelle) + 0.5, vmax = s.volumen(zelle, e1), mittel = e1 - Probe.volumenFein(z, ci, cj, e1 + 50) / s.zellflaeche + 50;
        achsen(g, x0, y0, pw, ph, e0, e1, 0, vmax, "η in m NHN", "V in m³");
        Path2D fein = new Path2D.Double(), grob = new Path2D.Double();
        for (int k = 0; k <= 400; k++) {
            double eta = e0 + (e1 - e0) * k / 400, px = x0 + pw * k / 400.0;
            double pf = y0 + ph - ph * Probe.volumenFein(z, ci, cj, eta) / vmax, pg = y0 + ph - ph * Math.max(0, eta - mittel) * s.zellflaeche / vmax;
            if (k == 0) { fein.moveTo(px, pf); grob.moveTo(px, pg); } else { fein.lineTo(px, pf); grob.lineTo(px, pg); }
        }
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[] {6, 5}, 0));
        g.setColor(ROT);
        g.draw(grob);
        g.setStroke(new BasicStroke(2.4f));
        g.setColor(WASSER);
        g.draw(fein);
        g.setColor(SAND);
        g.setStroke(new BasicStroke(1.2f));
        Path2D tab = new Path2D.Double();
        for (int l = 0; l < s.stufen; l++) {
            double px = x0 + pw * (s.stufe[zelle * s.stufen + l] - e0) / (e1 - e0), py = y0 + ph - ph * s.volumen[zelle * s.stufen + l] / vmax;
            if (l == 0) tab.moveTo(px, py); else tab.lineTo(px, py);
            g.fillOval((int) px - 4, (int) py - 4, 8, 8);
        }
        g.draw(tab);
        double groesste = 0;
        for (int k = 0; k <= 2000; k++) {
            double eta = e0 + (e1 - e0) * k / 2000;
            groesste = Math.max(groesste, Math.abs(s.volumen(zelle, eta) - Probe.volumenFein(z, ci, cj, eta)));
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        legende(g, x0 + 16, y0 + 8, WASSER, "feines Gelände, 400 Pixel");
        legende(g, x0 + 16, y0 + 28, SAND, "Tabelle, 12 Stufen");
        legende(g, x0 + 16, y0 + 48, ROT, String.format(Locale.GERMAN, "eine Höhe je Zelle (Mittel %.2f m)", mittel));
        g.setColor(MATT);
        g.drawString(String.format(Locale.GERMAN, "Zelle (%d, %d): Grund %.2f m, höchstes Pixel %.2f m; größter Abstand Tabelle – Gelände %.2f m³ = %.2f mm mittlere Tiefe", ci, cj, s.grund(zelle),
                s.hoechste(zelle), groesste, groesste / s.zellflaeche * 1000), x0, y0 + ph + 40);
    }

    // ------------------------------------------------------------------ 4 Tiden

    private static void tiden(Graphics2D g, int w, int h) {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        int n = 369 * 24;
        double[] y = new double[n];
        for (int k = 0; k < n; k++) y[k] = Gestirne.bei(start.plusSeconds(3600L * k)).gleichgewicht(0, 0);
        List<Partialtide> tiden = List.of(Partialtide.M2, Partialtide.S2, Partialtide.N2, Partialtide.K2, Partialtide.K1, Partialtide.O1, Partialtide.P1, Partialtide.Q1, Partialtide.MF,
                Partialtide.MM, Partialtide.SSA, Partialtide.ZWEI_N2, Partialtide.J1, Partialtide.NU2, Partialtide.MU2);
        Harmonik.Ergebnis e = Harmonik.analyse(start, 3600, y, tiden, 1);
        int x0 = 70, y0 = 46, pw = w - 110, ph = (h - 130) / 2;
        achsen(g, x0, y0, pw, ph, 0, 30, -0.3, 0.5, "Tage seit 01.01.2026", "m");
        Path2D p = new Path2D.Double(), r = new Path2D.Double();
        for (int k = 0; k <= 30 * 24; k++) {
            double px = x0 + pw * k / (30.0 * 24), py = y0 + ph - ph * (y[k] + 0.3) / 0.8, pr = y0 + ph - ph * (y[k] - e.wert(start.plusSeconds(3600L * k)) + 0.3) / 0.8;
            if (k == 0) { p.moveTo(px, py); r.moveTo(px, pr); } else { p.lineTo(px, py); r.lineTo(px, pr); }
        }
        g.setStroke(new BasicStroke(1.4f));
        g.setColor(WASSER);
        g.draw(p);
        g.setColor(ROT);
        g.draw(r);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        legende(g, x0 + 16, y0 + 8, WASSER, "aus den Ephemeriden");
        legende(g, x0 + 190, y0 + 8, ROT, String.format(Locale.GERMAN, "Rest nach 15 Partialtiden (%.1f mm)", e.restRms() * 1000));
        int by = y0 + ph + 58, bh = h - by - 40;
        String[] namen = {"M2", "S2", "N2", "K2", "NU2", "MU2", "2N2", "MF", "MM", "SSA"};
        achsen(g, x0, by, pw, bh, 0, namen.length, 0, 0.26, "", "m");
        double bw = pw / (double) namen.length;
        for (int k = 0; k < namen.length; k++) {
            Harmonik.Tide t = e.tide(namen[k]);
            double soll = t.tide().gleichgewicht * (t.tide().art() == 0 ? 0.5 : 1);                   // langperiodisch am Äquator: Faktor ½
            int hs = (int) (bh * soll / 0.26), hi = (int) (bh * t.amplitude() / 0.26);
            g.setColor(MATT);
            g.fillRect((int) (x0 + k * bw + bw * 0.18), by + bh - hs, (int) (bw * 0.3), hs);
            g.setColor(WASSER);
            g.fillRect((int) (x0 + k * bw + bw * 0.52), by + bh - hi, (int) (bw * 0.3), hi);
            g.setColor(TEXT);
            g.drawString(namen[k], (int) (x0 + k * bw + bw * 0.34), by + bh + 16);
        }
        legende(g, x0 + pw - 330, by + 8, MATT, "Cartwright");
        legende(g, x0 + pw - 220, by + 8, WASSER, "aus Sonne und Mond gerechnet");
    }

    private static void legende(Graphics2D g, int x, int y, Color c, String text) {
        g.setColor(c);
        g.fillRect(x, y, 18, 4);
        g.setColor(TEXT);
        g.drawString(text, x + 26, y + 7);
    }

    private static void achsen(Graphics2D g, int x0, int y0, int pw, int ph, double xa, double xb, double ya, double yb, String xn, String yn) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setStroke(new BasicStroke(1f));
        for (int k = 0; k <= 4; k++) {
            int py = y0 + ph - ph * k / 4, px = x0 + pw * k / 4;
            g.setColor(LINIE);
            g.drawLine(x0, py, x0 + pw, py);
            g.setColor(MATT);
            String ty = String.format(Locale.GERMAN, Math.abs(yb - ya) > 20 ? "%.0f" : "%.2f", ya + (yb - ya) * k / 4);
            g.drawString(ty, x0 - 8 - g.getFontMetrics().stringWidth(ty), py + 4);
            if (!xn.isEmpty()) {
                String tx = String.format(Locale.GERMAN, Math.abs(xb - xa) > 20 ? "%.0f" : "%.2f", xa + (xb - xa) * k / 4);
                g.drawString(tx, px - g.getFontMetrics().stringWidth(tx) / 2, y0 + ph + 16);
            }
        }
        g.setColor(MATT);
        g.drawRect(x0, y0, pw, ph);
        g.drawString(yn, x0 - 56, y0 - 10);
        if (!xn.isEmpty()) g.drawString(xn, x0 + pw - g.getFontMetrics().stringWidth(xn), y0 + ph + 32);
    }

    private CoreBild() { }
}
