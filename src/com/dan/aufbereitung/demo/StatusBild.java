package com.dan.aufbereitung.demo;

import com.dan.aufbereitung.Schliessen;
import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
import com.dan.core.raum.Utm;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Das Status-Bild von P3 (Werkbuch HalligHooge Teil A, 12.4, Prüfstein 16): sechs Felder – die Gitterkette über G0,
 * G1 und G2 mit den geschlossenen Flächen, G3 mit Deichkanten und Japsand, Hooge im Stapel mit Deichkanten nach Krone
 * und den Sielen, die Naht am Hallighafen.
 */
final class StatusBild {
    private StatusBild() { }

    static final int F = 800;

    static void zeichne(GebietCheck.Lage c, Path datei) throws IOException {
        BufferedImage bild = new BufferedImage(3 * F, 2 * F, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bild.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(24, 26, 30));
        g.fillRect(0, 0, 3 * F, 2 * F);
        Gebiet.Bauten bw = c.gebiet.bauwerke("G3");
        // jedes Feld nur in seinem Rahmen (sonst zeichnet die Vergrößerung von Hooge Warftringe in die Nachbarfelder)
        g.setClip(0, 0, F, F);
        feldG0(c, g, 0, 0);
        g.setClip(F, 0, F, F);
        feldG1(c, g, F, 0);
        g.setClip(2 * F, 0, F, F);
        feldG2(c, g, 2 * F, 0);
        g.setClip(0, F, F, F);
        feldG3(c, g, bw, 0, F);
        g.setClip(F, F, F, F);
        feldHooge(c, g, bw, F, F);
        g.setClip(2 * F, F, F, F);
        feldNaht(c, g, 2 * F, F);
        g.setClip(null);
        g.setColor(new Color(60, 64, 70));
        for (int i = 1; i < 3; i++) g.fillRect(i * F - 1, 0, 2, 2 * F);
        g.fillRect(0, F - 1, 3 * F, 2);
        g.dispose();
        Files.createDirectories(datei.toAbsolutePath().getParent());
        ImageIO.write(bild, "png", datei.toFile());
    }

    // ------------------------------------------------------------------ Farben

    /** Höhe (m NHN) nach Farbe: Tiefe blau, Watt hell, Land grün bis braun. */
    static int farbe(double z) {
        if (Double.isNaN(z)) return 0x202020;
        double[][] t = {{-30, 8, 20, 60}, {-10, 20, 60, 130}, {-3, 50, 120, 190}, {-1, 110, 170, 215}, {0, 175, 205, 220}, {0.5, 214, 205, 160}, {1.5, 160, 195, 115},
                {3, 95, 150, 70}, {6, 150, 120, 80}, {12, 225, 225, 225}};
        if (z <= t[0][0]) return rgb(t[0][1], t[0][2], t[0][3]);
        for (int i = 1; i < t.length; i++)
            if (z <= t[i][0]) {
                double a = (z - t[i - 1][0]) / (t[i][0] - t[i - 1][0]);
                return rgb(t[i - 1][1] + a * (t[i][1] - t[i - 1][1]), t[i - 1][2] + a * (t[i][2] - t[i - 1][2]), t[i - 1][3] + a * (t[i][3] - t[i - 1][3]));
            }
        return rgb(t[t.length - 1][1], t[t.length - 1][2], t[t.length - 1][3]);
    }

    static int rgb(double r, double g, double b) { return (int) Math.max(0, Math.min(255, r)) << 16 | (int) Math.max(0, Math.min(255, g)) << 8 | (int) Math.max(0, Math.min(255, b)); }

    static int mische(int a, int b, double t) {
        return rgb(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t, ((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t, (a & 255) * (1 - t) + (b & 255) * t);
    }

    static int schatten(int c, double s) { return rgb(((c >> 16) & 255) * s, ((c >> 8) & 255) * s, (c & 255) * s); }

    static final int ZU = 0xd0207a;

    static Color kroneFarbe(double k) {
        double t = Math.max(0, Math.min(1, (k - 2.0) / 1.5));
        return new Color(mische(0xff3020, 0x40e0ff, t));
    }

    static void titel(Graphics2D g, int ox, int oy, String t, String u) {
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(ox, oy, F, 44);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString(t, ox + 12, oy + 22);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString(u, ox + 12, oy + 39);
    }

    static void text(Graphics2D g, String t, double x, double y, Color c) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(new Color(0, 0, 0, 170));
        g.drawString(t, (float) x + 1, (float) y + 1);
        g.setColor(c);
        g.drawString(t, (float) x, (float) y);
    }

    /** Abbildung eines Rechtecks (West, Süd, Ost, Nord) in ein Feld mit Rand; Seitenverhältnis bleibt (Faktor k für Grad). */
    record Karte(double w, double s, double o, double n, double k, int ox, int oy, double m, double rx, double ry) {
        static Karte von(double w, double s, double o, double n, double k, int ox, int oy) {
            int rand = 50;
            double m = Math.min((F - 20) / ((o - w) * k), (F - rand - 10) / (n - s));
            double rx = ox + (F - (o - w) * k * m) / 2, ry = oy + rand + (F - rand - (n - s) * m) / 2;
            return new Karte(w, s, o, n, k, ox, oy, m, rx, ry);
        }

        double x(double e) { return rx + (e - w) * k * m; }
        double y(double nn) { return ry + (n - nn) * m; }
        double e(double px) { return w + (px - rx) / (k * m); }
        double nn(double py) { return n - (py - ry) / m; }
        int breite() { return (int) Math.round((o - w) * k * m); }
        int hoehe() { return (int) Math.round((n - s) * m); }
    }

    /** Rahmen eines UTM-Gitters in einer Karte; mit {@code grad} in Länge und Breite. */
    static void rahmen(Graphics2D g, Karte k, Gitter gi, boolean grad, Color col, String name) {
        Path2D.Double p = new Path2D.Double();
        double[][] ecken = {{gi.x0, gi.y0}, {gi.x1(), gi.y0}, {gi.x1(), gi.y1()}, {gi.x0, gi.y1()}};
        boolean erst = true;
        for (int s = 0; s < 4; s++)
            for (int q = 0; q <= 20; q++) {
                double a = q / 20.0, x = ecken[s][0] + a * (ecken[(s + 1) % 4][0] - ecken[s][0]), y = ecken[s][1] + a * (ecken[(s + 1) % 4][1] - ecken[s][1]);
                double[] u = grad ? Utm.rueckwaerts(x, y, gi.zone) : new double[] {x, y};
                if (erst) { p.moveTo(k.x(u[0]), k.y(u[1])); erst = false; }
                else p.lineTo(k.x(u[0]), k.y(u[1]));
            }
        p.closePath();
        g.setColor(col);
        g.setStroke(new BasicStroke(2f));
        g.draw(p);
        double[] u = grad ? Utm.rueckwaerts(gi.x0, gi.y1(), gi.zone) : new double[] {gi.x0, gi.y1()};
        text(g, name, k.x(u[0]) + 4, k.y(u[1]) + 15, col);
    }

    // ------------------------------------------------------------------ Felder

    static void feldG0(GebietCheck.Lage c, Graphics2D g, int ox, int oy) throws IOException {
        Gitter g0 = c.g0;
        float[] z = c.gebiet.groundZ("G0"), nass = c.gebiet.nass("G0");
        Karte k = Karte.von(g0.x0, g0.y0, g0.x1(), g0.y1(), Math.cos(Math.toRadians(54)), ox, oy);
        BufferedImage b = new BufferedImage(k.breite(), k.hoehe(), BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < b.getHeight(); py++)
            for (int px = 0; px < b.getWidth(); px++) {
                int i = g0.i(k.e(k.rx + px + 0.5)), j = g0.j(k.nn(k.ry + py + 0.5));
                if (i < 0 || j < 0 || i >= g0.nx || j >= g0.ny) continue;
                int q = g0.index(i, j);
                b.setRGB(px, py, nass[q] <= 0 ? 0x6a6a62 : mische(0x6a6a62, farbe(Math.min(z[q], -0.5)), Math.min(1, nass[q])));
            }
        g.drawImage(b, (int) Math.round(k.rx), (int) Math.round(k.ry), null);
        rahmen(g, k, c.g1, true, new Color(255, 220, 60), "G1");
        rahmen(g, k, c.g2, true, new Color(255, 140, 40), "G2");
        rahmen(g, k, c.g3, true, new Color(255, 70, 70), "");
        titel(g, ox, oy, "G0 Schelf – Gitterkette", f("%d × %d Zellen 1/12° × 1/18°; Tiefe in Meeranteil, Land nach Natural Earth grau", g0.nx, g0.ny));
    }

    static void feldG1(GebietCheck.Lage c, Graphics2D g, int ox, int oy) throws IOException {
        Gitter g1 = c.g1;
        float[] z = c.gebiet.groundZ("G1");
        Karte k = Karte.von(g1.x0, g1.y0, g1.x1(), g1.y1(), 1, ox, oy);
        BufferedImage b = new BufferedImage(k.breite(), k.hoehe(), BufferedImage.TYPE_INT_RGB);
        Gelaende.Stufe.Leser la = c.anteil.leser();
        for (int py = 0; py < b.getHeight(); py++)
            for (int px = 0; px < b.getWidth(); px++) {
                double e = k.e(k.rx + px + 0.5), n = k.nn(k.ry + py + 0.5);
                int i = g1.i(e), j = g1.j(n);
                if (i < 0 || j < 0 || i >= g1.nx || j >= g1.ny) continue;
                float a = la.imPixel(e, n);
                int col = farbe(c.hundert.bilinear(e, n));
                b.setRGB(px, py, a >= 0.5f ? mische(col, ZU, 0.65) : col);
            }
        g.drawImage(b, (int) Math.round(k.rx), (int) Math.round(k.ry), null);
        rahmen(g, k, c.g2, false, new Color(255, 140, 40), "G2");
        rahmen(g, k, c.g3, false, new Color(255, 70, 70), "G3");
        titel(g, ox, oy, "G1 Deutsche Bucht – Stufe 100 m, geschlossen", f("%d × %d Zellen 1 km; magenta: Land hinter Deichen (F23), beim Backen +%.0f m", g1.nx, g1.ny, c.auf));
    }

    static void feldG2(GebietCheck.Lage c, Graphics2D g, int ox, int oy) throws IOException {
        Gitter g2 = c.g2;
        Karte k = Karte.von(g2.x0, g2.y0, g2.x1(), g2.y1(), 1, ox, oy);
        BufferedImage b = new BufferedImage(k.breite(), k.hoehe(), BufferedImage.TYPE_INT_RGB);
        IntStream.range(0, b.getHeight()).parallel().forEach(py -> {
            Gelaende.Stufe.Leser lm = c.maske.leser();
            for (int px = 0; px < b.getWidth(); px++) {
                double e = k.e(k.rx + px + 0.5), n = k.nn(k.ry + py + 0.5);
                if (!g2.enthaelt(e, n)) continue;
                int col = farbe(c.gel.hoehe(e, n, 10));
                synchronized (b) { b.setRGB(px, py, Schliessen.zu(lm, c.maske, e, n) ? mische(col, ZU, 0.6) : col); }
            }
        });
        g.drawImage(b, (int) Math.round(k.rx), (int) Math.round(k.ry), null);
        rahmen(g, k, c.g3, false, new Color(255, 70, 70), "G3");
        for (String key : c.rz.alle().keySet()) {
            if (!key.startsWith("zu.") && !key.startsWith("offen.")) continue;
            double[] p = c.rz.zahlen(key, 2);
            g.setColor(Color.WHITE);
            g.fillOval((int) k.x(p[0]) - 4, (int) k.y(p[1]) - 4, 8, 8);
            text(g, key, k.x(p[0]) + 6, k.y(p[1]) - 4, Color.WHITE);
        }
        titel(g, ox, oy, "G2 Nordfriesland – Stufe 10 m, geschlossen", f("%d × %d Zellen 200 m; magenta: Festland und eingedeichte Inseln (F24); Sonden aus dem Rezept", g2.nx, g2.ny));
    }

    static void feldG3(GebietCheck.Lage c, Graphics2D g, Gebiet.Bauten bw, int ox, int oy) throws IOException {
        Gitter g3 = c.g3;
        float[] z = c.gebiet.groundZ("G3");
        Karte k = Karte.von(g3.x0, g3.y0, g3.x1(), g3.y1(), 1, ox, oy);
        BufferedImage b = new BufferedImage(k.breite(), k.hoehe(), BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < b.getHeight(); py++)
            for (int px = 0; px < b.getWidth(); px++) {
                int i = g3.i(k.e(k.rx + px + 0.5)), j = g3.j(k.nn(k.ry + py + 0.5));
                if (i < 0 || j < 0 || i >= g3.nx || j >= g3.ny) continue;
                b.setRGB(px, py, farbe(z[g3.index(i, j)]));
            }
        g.drawImage(b, (int) Math.round(k.rx), (int) Math.round(k.ry), null);
        kanten(g, k, g3, bw, 1.5f);
        for (Gebiet.Bauten.Siel si : bw.siele()) {
            g.setColor(Color.MAGENTA);
            g.fillOval((int) k.x(si.ost()) - 4, (int) k.y(si.nord()) - 4, 8, 8);
            text(g, si.name(), k.x(si.ost()) + 5, k.y(si.nord()) - 4, new Color(255, 200, 255));
        }
        rahmen(g, k, c.g4, false, Color.WHITE, "G4");
        if (g3.enthaelt(465600, 6046300)) text(g, "Japsand", k.x(464900), k.y(6046300), new Color(255, 245, 200));
        titel(g, ox, oy, "G3 Szene Hooge – ground.z", f("%d × %d Zellen 20 m; Deichkanten nach Krone (rot 2,0 m, cyan 3,5 m); %d Siele magenta", g3.nx, g3.ny, bw.siele().size()));
    }

    static void kanten(Graphics2D g, Karte k, Gitter gi, Gebiet.Bauten bw, float breite) {
        g.setStroke(new BasicStroke(breite, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Gebiet.Bauten.Kante e : bw.kanten()) {
            double x0 = gi.x0 + e.i() * gi.dx, y0 = gi.y0 + e.j() * gi.dy;
            double x1 = e.querX() ? x0 : x0 + gi.dx, y1 = e.querX() ? y0 + gi.dy : y0;
            g.setColor(e.art().equals("siel") ? Color.MAGENTA : kroneFarbe(e.krone()));
            g.drawLine((int) k.x(x0), (int) k.y(y0), (int) k.x(x1), (int) k.y(y1));
        }
    }

    static void feldHooge(GebietCheck.Lage c, Graphics2D g, Gebiet.Bauten bw, int ox, int oy) throws IOException {
        // Rahmen um den Ring Hooge (Kanten mit Krone unter 4 m nahe der Sonde)
        double[] s = c.rz.hat("ring.Hooge") ? c.rz.zahlen("ring.Hooge", 2) : new double[] {0.5 * (c.g3.x0 + c.g3.x1()), 0.5 * (c.g3.y0 + c.g3.y1())};
        double w = s[0] - 2600, o = s[0] + 2600, sd = s[1] - 2100, n = s[1] + 2100;
        Karte k = Karte.von(w, sd, o, n, 1, ox, oy);
        bildStapel(c, g, k, 1);
        kanten(g, k, c.g3, bw, 3f);
        double tiefste = Double.MAX_VALUE;
        Gebiet.Bauten.Kante t = null;
        for (Gebiet.Bauten.Kante e : bw.kanten()) {
            double x = c.g3.x0 + e.i() * c.g3.dx, y = c.g3.y0 + e.j() * c.g3.dy;
            if (x < w || x > o || y < sd || y > n || !e.art().equals("deich")) continue;
            if (e.krone() < tiefste) { tiefste = e.krone(); t = e; }
        }
        if (t != null) {
            double x = c.g3.x0 + (t.i() + 0.5) * c.g3.dx, y = c.g3.y0 + (t.j() + 0.5) * c.g3.dy;
            g.setColor(Color.YELLOW);
            g.setStroke(new BasicStroke(2.5f));
            g.drawOval((int) k.x(x) - 12, (int) k.y(y) - 12, 24, 24);
            text(g, f("niedrigste Krone %.2f m", tiefste), k.x(x) + 14, k.y(y) + 5, Color.YELLOW);
        }
        int nr = 0;
        for (Gebiet.Bauten.Siel si : bw.siele()) {
            if (si.ost() < w || si.ost() > o || si.nord() < sd || si.nord() > n) continue;
            nr++;
            g.setColor(Color.MAGENTA);
            g.fillOval((int) k.x(si.ost()) - 6, (int) k.y(si.nord()) - 6, 12, 12);
            text(g, si.name() + f(" (Sohle %.2f m)", si.sohle()), k.x(si.ost()) + 8, k.y(si.nord()) - 6, Color.WHITE);
        }
        titel(g, ox, oy, "Hooge – Stapel 1 m, Deichkanten und Siele", f("%d Siele im Bild; Deichkanten von G3 nach Krone (rot 2,0 m, cyan 3,5 m), Siele magenta", nr));
    }

    static void feldNaht(GebietCheck.Lage c, Graphics2D g, int ox, int oy) throws IOException {
        double[] s = {470330, 6047650};
        for (String key : c.rz.alle().keySet()) if (key.equals("naht.bild")) s = c.rz.zahlen(key, 2);
        Karte k = Karte.von(s[0] - 350, s[1] - 300, s[0] + 350, s[1] + 300, 1, ox, oy);
        bildStapel(c, g, k, 0);
        // Rand der Landstufe (Land, Saum und Hof)
        Gelaende.Stufe l1 = c.gel.stufen()[0];
        Gelaende.Stufe.Leser l = l1.leser();
        g.setColor(new Color(255, 255, 255, 200));
        for (double y = k.s(); y < k.n(); y += 1)
            for (double x = k.w(); x < k.o(); x += 1) {
                boolean a = !Float.isNaN(l.imPixel(x, y)), b = !Float.isNaN(l.imPixel(x + 1, y)), d = !Float.isNaN(l.imPixel(x, y + 1));
                if (a != b || a != d) g.fillRect((int) k.x(x + 0.5), (int) k.y(y + 0.5), 2, 2);
            }
        titel(g, ox, oy, "Naht am Hallighafen – Stapel 1 m", "Land, Saum und Hof der 1-m-Stufe; weiß: ihr Rand, außerhalb TrilaWatt 10 m bilinear");
    }

    /** Der Stapel in einer Karte, schattiert; {@code art} 1: grobe Abtastung (Schritt eines Bildpunkts), 0: 1 m. */
    static void bildStapel(GebietCheck.Lage c, Graphics2D g, Karte k, int art) {
        int bw = k.breite(), bh = k.hoehe();
        double schritt = 1 / k.m();
        float[] z = new float[(bw + 2) * (bh + 2)];
        IntStream.range(0, bh + 2).parallel().forEach(py -> {
            for (int px = 0; px < bw + 2; px++) z[py * (bw + 2) + px] = c.gel.hoehe(k.e(k.rx + px - 0.5), k.nn(k.ry + py - 0.5), art == 1 ? schritt : 1);
        });
        BufferedImage b = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_RGB);
        double l0 = -0.5, l1 = -0.6, l2 = 0.62;                                      // Licht von Nordwest
        for (int py = 0; py < bh; py++)
            for (int px = 0; px < bw; px++) {
                int q = (py + 1) * (bw + 2) + px + 1;
                double dzx = (z[q + 1] - z[q - 1]) / (2 * schritt), dzy = (z[q - (bw + 2)] - z[q + (bw + 2)]) / (2 * schritt);
                double nx = -dzx * 3, ny = -dzy * 3, nz = 1, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                double lam = Math.max(0, (nx * l0 + ny * -l1 + nz * l2) / nl);
                double sh = Double.isNaN(lam) ? 1 : 0.55 + 0.6 * lam;
                b.setRGB(px, py, schatten(farbe(z[q]), Math.min(1.15, sh)));
            }
        g.drawImage(b, (int) Math.round(k.rx), (int) Math.round(k.ry), null);
    }

    static String f(String format, Object... w) { return String.format(Locale.GERMAN, format, w); }
}
