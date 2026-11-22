package com.dan.physics.hydro.demo;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.dan.core.pruef.Attest.f;

/**
 * Das Status-Bild von P4 (Werkbuch HalligHooge Teil A, 12.5, Plan P4d), {@code docs/status/P4_Hydro.png}: G3 um Hooge
 * und G4 bei Hoch- und Niedrigwasser am Bezugspegel (Tiefe, benetzter Anteil, Strömung, Deiche), die Pegel gegen die
 * Vorgabe, die Fälle 2–5 gegen ihre Lösungen, dazu Bilanz und Zeiten. Die Fälle legen ihre Kurven hier ab.
 */
final class StatusBild {
    private StatusBild() { }

    /** Fall 2: x (km), η der Rechnung (NaN trocken), η der Lösung, Grund (m). */
    static double[] thackerX, thackerEta, thackerSoll, thackerGrund;
    /** Fall 3: [0] Ritter, [1] Stoker: x hinter dem Damm (m), Tiefe, Lösung. */
    static final double[][] dammX = new double[2][], dammH = new double[2][], dammSoll = new double[2][];
    /** Fall 4: Abstand von der Südwand (km), Amplitude der Rechnung, Lösung (m). */
    static double[] kelvinY, kelvinA, kelvinSoll;
    /** Fall 5: Zeit (h), η am geschlossenen Ende, lineare Lösung (m). */
    static double[] kanalT, kanalEta, kanalSoll;

    static final int W = 2000, KOPF = 64, KARTE = 900, KURVE = 430, FUSS = 96;
    static final Color GRUND = new Color(24, 26, 30), LINIE = new Color(60, 64, 70), TEXT = new Color(230, 232, 236), BLASS = new Color(150, 156, 166);
    static final Color RECHNUNG = new Color(64, 200, 255), LOESUNG = new Color(255, 190, 60), DEICH = new Color(200, 40, 40);

    static Path schreibe(Path datei) throws IOException {
        HydroLauf.Ergebnis er = HydroLauf.lauf;
        int h = KOPF + 2 * KARTE + 2 * KURVE + FUSS;
        BufferedImage bild = new BufferedImage(W, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bild.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(GRUND);
        g.fillRect(0, 0, W, h);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        g.drawString("P4 – hydro auf der Karte: Lauf über G2 → G3 → G4 und Prüfung A", 24, 36);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        g.setColor(BLASS);
        g.drawString(f("Werkbuch HalligHooge Teil A, 12.5 (P4d) · %s · %s", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
                er == null ? "ohne Lauf" : er.geraet), 24, 56);
        int y = KOPF;
        if (er != null && er.hw != null) {
            g.setClip(0, y, W / 2, KARTE);
            feldG3(g, er, er.hw, 0, y, "Hochwasser");
            g.setClip(W / 2, y, W / 2, KARTE);
            feldG3(g, er, er.nw, W / 2, y, "Niedrigwasser");
            y += KARTE;
            g.setClip(0, y, W / 2, KARTE);
            feldG4(g, er, er.hw, 0, y, "Hochwasser");
            g.setClip(W / 2, y, W / 2, KARTE);
            feldG4(g, er, er.nw, W / 2, y, "Niedrigwasser");
            g.setClip(null);
            y += KARTE;
            g.setColor(LINIE);
            g.fillRect(W / 2 - 1, KOPF, 2, 2 * KARTE);
            g.fillRect(0, KOPF + KARTE - 1, W, 2);
            g.fillRect(0, y - 1, W, 2);
            pegel(g, er, 0, y);
        } else {
            g.setColor(BLASS);
            g.drawString("Kein Lauf (Fall 9 rechnet nur mit --karte --gebiet).", 40, y + 40);
            y += 2 * KARTE;
        }
        y += KURVE;
        g.setColor(LINIE);
        g.fillRect(0, y - 1, W, 2);
        faelle(g, 0, y);
        y += KURVE;
        g.setColor(LINIE);
        g.fillRect(0, y - 1, W, 2);
        fuss(g, er, y);
        g.dispose();
        Files.createDirectories(datei.toAbsolutePath().getParent());
        ImageIO.write(bild, "png", datei.toFile());
        return datei;
    }

    // ------------------------------------------------------------------ Farben

    /** Höhe (m NHN) nach Farbe für trockenes Land und Watt. */
    static int land(double z) {
        if (Double.isNaN(z)) return 0x202020;
        double[][] t = {{-3, 120, 128, 120}, {0, 168, 160, 128}, {1, 196, 186, 140}, {1.5, 150, 182, 110}, {3, 100, 150, 80}, {5, 140, 120, 90}, {10, 205, 205, 205}};
        return verlauf(t, z);
    }

    /** Wassertiefe (m) nach Farbe. */
    static int wasser(double d) {
        double[][] t = {{0, 170, 230, 240}, {0.3, 110, 200, 230}, {1, 50, 150, 215}, {3, 25, 95, 180}, {10, 12, 45, 120}, {30, 5, 15, 60}};
        return verlauf(t, d);
    }

    static int verlauf(double[][] t, double z) {
        if (z <= t[0][0]) return rgb(t[0][1], t[0][2], t[0][3]);
        for (int i = 1; i < t.length; i++)
            if (z <= t[i][0]) {
                double a = (z - t[i - 1][0]) / (t[i][0] - t[i - 1][0]);
                return rgb(t[i - 1][1] + a * (t[i][1] - t[i - 1][1]), t[i - 1][2] + a * (t[i][2] - t[i - 1][2]), t[i - 1][3] + a * (t[i][3] - t[i - 1][3]));
            }
        double[] e = t[t.length - 1];
        return rgb(e[1], e[2], e[3]);
    }

    static int mische(int a, int b, double t) {
        return rgb(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t, ((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t, (a & 255) * (1 - t) + (b & 255) * t);
    }

    static int rgb(double r, double g, double b) { return (int) Math.max(0, Math.min(255, r)) << 16 | (int) Math.max(0, Math.min(255, g)) << 8 | (int) Math.max(0, Math.min(255, b)); }

    /** Farbe einer Zelle: Land nach Grund, darüber Wasser nach Tiefe im Verhältnis des benetzten Anteils. */
    static int zelle(float anteil, float tiefe, float grund) {
        int l = land(grund);
        if (!(anteil > 0)) return l;
        return mische(l, wasser(tiefe), Math.min(1, 0.25 + 0.75 * anteil));
    }

    // ------------------------------------------------------------------ Karten

    static void titel(Graphics2D g, int x, int y, String a, String b) {
        Font fa = new Font(Font.SANS_SERIF, Font.BOLD, 18), fb = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        int breite = Math.max(g.getFontMetrics(fa).stringWidth(a), g.getFontMetrics(fb).stringWidth(b)) + 20;
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(x + 10, y + 10, breite, 52);
        g.setColor(TEXT);
        g.setFont(fa);
        g.drawString(a, x + 20, y + 32);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(BLASS);
        g.drawString(b, x + 20, y + 52);
    }

    /** Bild einer Fläche von Zellen (Zeile 0 im Süden) in ein Rechteck. */
    static void flaeche(Graphics2D g, int ni, int nj, float[] anteil, float[] tiefe, float[] grund, int x, int y, int b, int h) {
        BufferedImage im = new BufferedImage(ni, nj, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < nj; j++)
            for (int i = 0; i < ni; i++) {
                int c = i + ni * j;
                im.setRGB(i, nj - 1 - j, zelle(anteil[c], tiefe[c], grund[c]));
            }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, ni > b ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(im, x, y, b, h, null);
    }

    /** Pfeile der Strömung: jede s-te Zelle, Länge p Pixel je m/s. */
    static void pfeile(Graphics2D g, int ni, int nj, float[] u, float[] v, int x, int y, double px, int s, double p) {
        g.setColor(new Color(255, 255, 255, 190));
        g.setStroke(new BasicStroke(1.2f));
        for (int j = s / 2; j < nj; j += s)
            for (int i = s / 2; i < ni; i += s) {
                int c = i + ni * j;
                double sp = Math.hypot(u[c], v[c]);
                if (sp < 0.05) continue;
                double x0 = x + (i + 0.5) * px, y0 = y + (nj - j - 0.5) * px, dx = u[c] * p, dy = -v[c] * p;
                double l = Math.hypot(dx, dy), k = Math.min(6, 0.35 * l);
                double x1 = x0 + dx, y1 = y0 + dy, ax = dx / l, ay = dy / l;
                Path2D.Double pa = new Path2D.Double();
                pa.moveTo(x0, y0);
                pa.lineTo(x1, y1);
                pa.moveTo(x1, y1);
                pa.lineTo(x1 - k * (ax - 0.5 * ay), y1 - k * (ay + 0.5 * ax));
                pa.moveTo(x1, y1);
                pa.lineTo(x1 - k * (ax + 0.5 * ay), y1 - k * (ay - 0.5 * ax));
                g.draw(pa);
            }
    }

    static void feldG3(Graphics2D g, HydroLauf.Ergebnis er, HydroLauf.Stand s, int x, int y, String wann) {
        int[] w = er.fenster;
        int rand = 30, b = W / 2 - 2 * rand, hh = KARTE - 2 * rand;
        double px = Math.min((double) b / w[2], (double) hh / w[3]);
        int bx = (int) Math.round(w[2] * px), by = (int) Math.round(w[3] * px), ox = x + (W / 2 - bx) / 2, oy = y + (KARTE - by) / 2;
        flaeche(g, w[2], w[3], s.anteil3, s.tiefe3, er.grund3, ox, oy, bx, by);
        // Deichkanten im Ausschnitt
        g.setColor(DEICH);
        g.setStroke(new BasicStroke((float) Math.max(1.5, px * 0.6)));
        for (Gebiet.Bauten.Kante k : er.bauten.kanten()) {
            int i = k.i() - er.bauI0 - w[0], j = k.j() - er.bauJ0 - w[1];
            if (i < 0 || j < 0 || i > w[2] || j > w[3]) continue;
            double xa = ox + i * px, ya = oy + (w[3] - j) * px;
            if (k.querX()) g.drawLine((int) xa, (int) ya, (int) xa, (int) (ya - px));
            else g.drawLine((int) xa, (int) ya, (int) (xa + px), (int) ya);
        }
        pfeile(g, w[2], w[3], s.u3, s.v3, ox, oy, px, 8, 8 * px / 1.0);
        // Rahmen von G4
        Gitter g3 = er.g3, g4 = er.g4;
        double ix = (g4.x0 - g3.x0) / g3.dx - w[0], jy = (g4.y0 - g3.y0) / g3.dy - w[1], n = g4.nx * g4.dx / g3.dx;
        g.setColor(new Color(255, 230, 80));
        g.setStroke(new BasicStroke(2f));
        g.drawRect((int) (ox + ix * px), (int) (oy + (w[3] - jy - n) * px), (int) (n * px), (int) (n * px));
        g.drawString("G4", (int) (ox + ix * px) + 4, (int) (oy + (w[3] - jy - n) * px) - 6);
        // Pegel
        for (HydroLauf.Pegel p : er.pegel) {
            double pi = (p.soll.ost() - g3.x0) / g3.dx - w[0], pj = (p.soll.nord() - g3.y0) / g3.dy - w[1];
            if (pi < 0 || pj < 0 || pi > w[2] || pj > w[3]) continue;
            int qx = (int) (ox + pi * px), qy = (int) (oy + (w[3] - pj) * px);
            g.setColor(Color.MAGENTA);
            g.fillOval(qx - 5, qy - 5, 10, 10);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.drawString(p.soll.name(), qx + 8, qy - 6);
        }
        titel(g, x, y, f("G3 (20 m) bei %s, t = %.2f h", wann, s.t / 3600), f("Pegel %s %+.2f m NHN · %.0f × %.0f km · Pfeile: 1 m/s = %.0f m · Deichkanten rot",
                er.pegel.get(0).soll.name(), s.eta, w[2] * g3.dx / 1000, w[3] * g3.dx / 1000, 8 * g3.dx));
        legende(g, x + W / 2 - 250, y + KARTE - 70);
    }

    static void feldG4(Graphics2D g, HydroLauf.Ergebnis er, HydroLauf.Stand s, int x, int y, String wann) {
        int n = s.n4, rand = 30, b = Math.min(W / 2, KARTE) - 2 * rand;
        double px = (double) b / n;
        int ox = x + (W / 2 - b) / 2, oy = y + (KARTE - b) / 2;
        flaeche(g, n, n, s.anteil4, s.tiefe4, er.grund4, ox, oy, b, b);
        int st = Math.max(1, n / 40);
        pfeile(g, n, n, s.u4, s.v4, ox, oy, px, st, st * px);
        titel(g, x, y, f("G4 (2 m) bei %s, t = %.2f h", wann, s.t / 3600), f("%.0f × %.0f m, Südwestecke O %.0f, N %.0f · Pfeile: 1 m/s = %.0f m", n * er.g4.dx, n * er.g4.dx,
                er.g4.x0, er.g4.y0, st * er.g4.dx));
    }

    static void legende(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(x - 8, y - 22, 236, 64);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(TEXT);
        g.drawString("Tiefe über der benetzten Fläche", x, y - 6);
        double[] d = {0, 0.3, 1, 3, 10};
        for (int i = 0; i < 200; i++) {
            double t = Math.pow(10, -1.3 + 2.3 * i / 199.0);
            g.setColor(new Color(wasser(t)));
            g.fillRect(x + i, y, 1, 14);
        }
        g.setColor(BLASS);
        for (double v : d) {
            int xi = (int) Math.round((Math.log10(Math.max(v, 0.05)) + 1.3) / 2.3 * 199);
            g.drawString(v == 0 ? "0,05" : f("%.1f", v).replace(",0", ""), x + xi - 8, y + 30);
        }
    }

    // ------------------------------------------------------------------ Kurven

    record Reihe(double[] x, double[] y, Color farbe, boolean gestrichelt, String name) { }

    static void kurve(Graphics2D g, int x, int y, int b, int h, String titel, String xa, String ya, double[] band, Reihe... r) {
        int l = x + 62, o = y + 40, bb = b - 82, hh = h - 92;
        double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, y0 = Double.POSITIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY;
        for (Reihe q : r) {
            if (q.x == null) continue;
            for (int i = 0; i < q.x.length; i++) {
                if (!Double.isFinite(q.y[i]) || !Double.isFinite(q.x[i])) continue;
                x0 = Math.min(x0, q.x[i]);
                x1 = Math.max(x1, q.x[i]);
                y0 = Math.min(y0, q.y[i]);
                y1 = Math.max(y1, q.y[i]);
            }
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.setColor(TEXT);
        g.drawString(titel, x + 14, y + 24);
        if (!(x1 > x0)) {
            g.setColor(BLASS);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.drawString("nicht gerechnet", l + 10, o + 30);
            return;
        }
        double m = 0.06 * Math.max(y1 - y0, 1e-9);
        y0 -= m;
        y1 += m;
        final double X0 = x0, X1 = x1, Y0 = y0, Y1 = y1;
        java.util.function.DoubleUnaryOperator sx = v -> l + (v - X0) / (X1 - X0) * bb, sy = v -> o + hh - (v - Y0) / (Y1 - Y0) * hh;
        if (band != null) {
            g.setColor(new Color(255, 255, 255, 18));
            g.fillRect((int) sx.applyAsDouble(Math.max(band[0], X0)), o, (int) (sx.applyAsDouble(Math.min(band[1], X1)) - sx.applyAsDouble(Math.max(band[0], X0))), hh);
        }
        g.setColor(LINIE);
        g.drawRect(l, o, bb, hh);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        for (int k = 0; k <= 4; k++) {
            double vx = X0 + k * (X1 - X0) / 4, vy = Y0 + k * (Y1 - Y0) / 4;
            g.setColor(LINIE);
            g.drawLine((int) sx.applyAsDouble(vx), o, (int) sx.applyAsDouble(vx), o + hh);
            g.drawLine(l, (int) sy.applyAsDouble(vy), l + bb, (int) sy.applyAsDouble(vy));
            g.setColor(BLASS);
            g.drawString(zahl(vx), (int) sx.applyAsDouble(vx) - 12, o + hh + 16);
            g.drawString(zahl(vy), x + 8, (int) sy.applyAsDouble(vy) + 4);
        }
        g.drawString(xa, l + bb / 2 - 30, o + hh + 34);
        g.drawString(ya, x + 8, o - 6);
        Stroke st = g.getStroke();
        int lx = l + 10;
        for (Reihe q : r) {
            if (q.x == null) continue;
            g.setColor(q.farbe);
            g.setStroke(q.gestrichelt ? new BasicStroke(1.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[] {7, 5}, 0) : new BasicStroke(2f));
            Path2D.Double pa = new Path2D.Double();
            boolean an = false;
            for (int i = 0; i < q.x.length; i++) {
                if (!Double.isFinite(q.y[i])) { an = false; continue; }
                double px = sx.applyAsDouble(q.x[i]), py = sy.applyAsDouble(q.y[i]);
                if (an) pa.lineTo(px, py);
                else pa.moveTo(px, py);
                an = true;
            }
            g.draw(pa);
            g.setStroke(st);
            g.fillRect(lx, o + 10, 16, 3);
            g.drawString(q.name, lx + 20, o + 15);
            lx += 30 + g.getFontMetrics().stringWidth(q.name);
        }
    }

    static String zahl(double v) {
        double a = Math.abs(v);
        return a >= 100 ? f("%.0f", v) : a >= 10 ? f("%.1f", v) : f("%.2f", v);
    }

    /** Die Pegel gegen die Vorgabe (ohne Anlauf), der Anlauf hell hinterlegt. */
    static void pegel(Graphics2D g, HydroLauf.Ergebnis er, int x, int y) {
        int n = Math.max(1, er.pegel.size()), b = W / n;
        for (int q = 0; q < er.pegel.size(); q++) {
            HydroLauf.Pegel p = er.pegel.get(q);
            double[] t = new double[p.n], e = new double[p.n], s = new double[p.n];
            for (int k = 0; k < p.n; k++) {
                t[k] = p.t[k] / 3600;
                e[k] = p.eta[k];
                s[k] = p.soll.mittel() + p.soll.amplitude() * Math.cos(er.omega * (p.t[k] + er.versatz) - Math.toRadians(p.soll.phase()));
            }
            kurve(g, x + q * b, y, b, KURVE, f("Pegel %s (%s)", p.soll.name(), p.gitter), "t (h)", "η (m NHN)", new double[] {0, er.anlauf / 3600},
                    new Reihe(t, e, RECHNUNG, false, "Lauf"), new Reihe(t, s, LOESUNG, true, "Vorgabe M2"));
        }
    }

    /** Die Fälle 2–5 gegen ihre Lösungen. */
    static void faelle(Graphics2D g, int x, int y) {
        int b = W / 4;
        kurve(g, x, y, b, KURVE, "Fall 2: Thacker, η nach 3 Perioden", "x (km)", "η (m)", null, new Reihe(thackerX, thackerGrund, BLASS, false, "Grund"),
                new Reihe(thackerX, thackerSoll, LOESUNG, true, "Lösung"), new Reihe(thackerX, thackerEta, RECHNUNG, false, "Rechnung"));
        kurve(g, x + b, y, b, KURVE, "Fall 3: Dammbruch, t = 40 s", "x hinter dem Damm (m)", "Tiefe (m)", null, new Reihe(dammX[0], dammSoll[0], LOESUNG, true, "Ritter"),
                new Reihe(dammX[0], dammH[0], RECHNUNG, false, "Rechnung"), new Reihe(dammX[1], dammSoll[1], new Color(255, 120, 80), true, "Stoker"),
                new Reihe(dammX[1], dammH[1], new Color(120, 230, 160), false, "Rechnung"));
        kurve(g, x + 2 * b, y, b, KURVE, "Fall 4: Kelvin-Welle, Amplitude quer", "y (km)", "Amplitude (m)", null, new Reihe(kelvinY, kelvinSoll, LOESUNG, true, "exp(−y/R)"),
                new Reihe(kelvinY, kelvinA, RECHNUNG, false, "Rechnung"));
        kurve(g, x + 3 * b, y, b, KURVE, "Fall 5: Kanal mit Reibung, geschlossenes Ende", "t (h)", "η (m)", null, new Reihe(kanalT, kanalSoll, LOESUNG, true, "linear"),
                new Reihe(kanalT, kanalEta, RECHNUNG, false, "Rechnung"));
    }

    static void fuss(Graphics2D g, HydroLauf.Ergebnis er, int y) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.setColor(TEXT);
        if (er == null) {
            g.drawString("Fall 9 und 14: ohne Lauf.", 24, y + 30);
            return;
        }
        g.drawString(f("Fall 9: %s, %.2f h, η₀ = %+.3f m, Anlauf %.0f h (hell hinterlegt). Bilanz %.1e (Grenze 1e-5, Abgeschnittenes als Fehler).", er.name, er.dauer / 3600, er.eta0,
                er.anlauf / 3600, Math.abs(er.rest) / er.bezug), 24, y + 30);
        g.setColor(BLASS);
        g.drawString(f("Schritte G2 %d · G3 %d · G4 %d; %.1f ns je Zellschritt, Zeitraffer %.1f (Wanduhr %.1f min); G4 gebacken in %.2f s.", er.schritte[0], er.schritte[1],
                er.schritte[2], 1e9 * er.rechnen / Math.max(1, er.zellschritte), er.dauer / er.wand, er.wand / 60, er.backen), 24, y + 56);
    }
}
