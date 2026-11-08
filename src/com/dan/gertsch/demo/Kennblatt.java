package com.dan.gertsch.demo;

import com.dan.gertsch.Lab;
import com.dan.gertsch.Messung;
import com.dan.gertsch.Vorlage;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * Zeichnet die Kennwerte einer {@link Vorlage} auf eine Tafel von {@link #BREITE} × {@link #HOEHE}: Tonstufen,
 * Helligkeit, Fleckgrößen, Richtung und Farbe. Mit einem Soll (Phase 2: die Vorlage, an der ein gemaltes Bild gemessen
 * wird) zeichnet sie dessen Werte dazu: Anteile als zweiter Balken, Verteilungen gestrichelt, Zahlen in Klammern.
 * Enthält keine Bildpunkte der Vorlage, nur die gemessenen Zahlen.
 */
final class Kennblatt {
    static final int BREITE = 400, HOEHE = 664;
    static final Color GRUND = new Color(12, 18, 24), TAFEL = new Color(18, 26, 34), TEXT = new Color(230, 236, 240),
            LEISE = new Color(140, 156, 166), LINIE = new Color(52, 64, 76), HELL = new Color(232, 168, 80), DUNKEL = new Color(96, 164, 214);
    static final Stroke GESTRICHELT = new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 4f, new float[]{4f, 3f}, 0f);

    private Kennblatt() { }

    static void zeichnen(Graphics2D g, Vorlage v, int x0, int y0) { zeichnen(g, v, x0, y0, null); }

    static void zeichnen(Graphics2D g, Vorlage v, int x0, int y0, Vorlage soll) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(TAFEL);
        g.fillRoundRect(x0, y0, BREITE, HOEHE, 14, 14);
        int x = x0 + 16, w = BREITE - 32, y = y0 + 28;
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        g.drawString(v.titel.isEmpty() ? v.name : v.titel, x, y);
        if (soll != null) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.setColor(LEISE);
            String s = "Soll: " + (soll.titel.isEmpty() ? soll.name : soll.titel);
            g.drawString(s, x + w - g.getFontMetrics().stringWidth(s), y);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.setColor(LEISE);
        y += 18;
        g.drawString(String.format(Locale.GERMAN, "%s%d × %d · gemessen auf %d · %s · %d Stufen", v.quelle.isEmpty() ? "" : v.quelle + " · ",
                v.breite, v.hoehe, v.arbeitsbreite, v.qualitaet, v.stufen), x, y);

        // Tonstufen: gleich breite Felder mit L*, darunter die Anteile (und die des Solls)
        y += 22;
        ueberschrift(g, "Tonstufen", x, y);
        y += 8;
        int k = v.palette.size();
        for (int i = 0; i < k; i++) {
            int fx = x + i * w / k, fw = (i + 1) * w / k - i * w / k;
            g.setColor(new Color(v.palette.rgb(i)));
            g.fillRect(fx, y, fw, 34);
            g.setColor(v.palette.lab[i][0] > 55 ? new Color(20, 20, 20) : new Color(235, 235, 235));
            g.drawString(String.format(Locale.GERMAN, "L %.0f", v.palette.lab[i][0]), fx + 5, y + 14);
            g.drawString(String.format(Locale.GERMAN, "%.0f %%", 100 * v.palette.anteil[i]), fx + 5, y + 28);
        }
        y += 38;
        anteile(g, v, x, y, w, 10);
        if (soll != null) anteile(g, soll, x, y + 12, w, 5);

        // Helligkeit
        y += 34;
        ueberschrift(g, "Helligkeit L*", x, y);
        y += 8;
        int hh = 64;
        double max = 0;
        for (double a : v.helligkeit) max = Math.max(max, a);
        if (soll != null) for (double a : soll.helligkeit) max = Math.max(max, a);
        for (int i = 0; i < 10; i++) {
            int bh = (int) Math.round(hh * v.helligkeit[i] / Math.max(1e-9, max));
            g.setColor(new Color(Lab.zuRgb(5 + 10 * i, 0, 0)));
            g.fillRect(x + i * w / 10 + 1, y + hh - bh, w / 10 - 2, bh);
            if (soll != null) {
                int sh = (int) Math.round(hh * soll.helligkeit[i] / Math.max(1e-9, max));
                g.setColor(LEISE);
                g.drawRect(x + i * w / 10 + 1, y + hh - sh, w / 10 - 3, sh);
            }
        }
        g.setColor(LINIE);
        g.drawLine(x, y + hh, x + w, y + hh);
        g.setColor(HELL);
        Stroke alt = g.getStroke();
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 4f, new float[]{3f, 3f}, 0f));
        int gx = x + (int) Math.round(w * new Messung().grundL / 100);
        g.drawLine(gx, y - 2, gx, y + hh);
        g.setStroke(alt);
        y += hh + 16;
        g.setColor(LEISE);
        g.drawString(String.format(Locale.GERMAN, "Grund (L* < 20) %.0f %%%s   ·   Median %.0f%s", 100 * v.grund, soll(soll, "%.0f %%", soll == null ? 0 : 100 * soll.grund),
                v.l50, soll(soll, "%.0f", soll == null ? 0 : soll.l50)), x, y);

        // Flecken: Summenverteilungen über den Durchmesser (logarithmisch, 0,1 % bis 10 % der Breite)
        y += 30;
        ueberschrift(g, "Fleckgrößen (Durchmesser, Anteil der Bildbreite)", x, y);
        y += 10;
        int ph = 112;
        double lo = Math.log10(0.001), hi = Math.log10(0.1);
        g.setColor(LINIE);
        g.drawRect(x, y, w, ph);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        for (double t : new double[]{0.001, 0.003, 0.01, 0.03, 0.1}) {
            int tx = x + (int) Math.round(w * (Math.log10(t) - lo) / (hi - lo));
            g.setColor(LINIE);
            g.drawLine(tx, y, tx, y + ph);
            g.setColor(LEISE);
            String s = String.format(Locale.GERMAN, t < 0.01 ? "%.1f %%" : "%.0f %%", 100 * t);
            g.drawString(s, Math.min(x + w - g.getFontMetrics().stringWidth(s), Math.max(x, tx - g.getFontMetrics().stringWidth(s) / 2)), y + ph + 13);
        }
        g.setColor(LINIE);
        g.drawLine(x, y + ph / 2, x + w, y + ph / 2);
        if (soll != null) {
            kurve(g, soll.durchmesser, soll.dunkel, DUNKEL, x, y, w, ph, lo, hi, GESTRICHELT);
            kurve(g, soll.durchmesser, soll.hell, HELL, x, y, w, ph, lo, hi, GESTRICHELT);
            marke(g, soll.dunkelD50, DUNKEL, x, y, w, ph, lo, hi, false);
            marke(g, soll.hellD50, HELL, x, y, w, ph, lo, hi, false);
        }
        kurve(g, v.durchmesser, v.dunkel, DUNKEL, x, y, w, ph, lo, hi, new BasicStroke(2f));
        kurve(g, v.durchmesser, v.hell, HELL, x, y, w, ph, lo, hi, new BasicStroke(2f));
        marke(g, v.dunkelD50, DUNKEL, x, y, w, ph, lo, hi, true);
        marke(g, v.hellD50, HELL, x, y, w, ph, lo, hi, true);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        y += ph + 32;
        g.setColor(HELL);
        g.drawString(String.format(Locale.GERMAN, "hell %.2f %%%s", 100 * v.hellD50, soll(soll, "%.2f", soll == null ? 0 : 100 * soll.hellD50)), x, y);
        g.setColor(DUNKEL);
        g.drawString(String.format(Locale.GERMAN, "dunkel %.2f %%%s", 100 * v.dunkelD50, soll(soll, "%.2f", soll == null ? 0 : 100 * soll.dunkelD50)), x + w / 2 + 20, y);

        // Richtung: Rose der gerichteten Energie, jede Richtung in beide Seiten gezeichnet; das Soll als Umriss
        y += 30;
        ueberschrift(g, "Richtung der Linien", x, y);
        y += 10;
        int rr = 62, cx = x + rr + 4, cy = y + rr + 4;
        g.setColor(LINIE);
        g.drawOval(cx - rr, cy - rr, 2 * rr, 2 * rr);
        g.drawLine(cx - rr, cy, cx + rr, cy);
        g.drawLine(cx, cy - rr, cx, cy + rr);
        double rmax = 0;
        for (double a : v.richtung) rmax = Math.max(rmax, a);
        if (soll != null) for (double a : soll.richtung) rmax = Math.max(rmax, a);
        for (int i = 0; i < 12; i++) {
            double len = rr * Math.sqrt(v.richtung[i] / Math.max(1e-9, rmax));
            for (int s = 0; s < 2; s++) {
                g.setColor(new Color(Color.HSBtoRGB((float) (i / 12.0), 0.55f, 0.95f)));
                g.fill(new Arc2D.Double(cx - len, cy - len, 2 * len, 2 * len, i * 15 + s * 180, 15, Arc2D.PIE));
            }
        }
        if (soll != null) {
            g.setColor(TEXT);
            g.setStroke(new BasicStroke(1.2f));
            for (int i = 0; i < 12; i++) {
                double len = rr * Math.sqrt(soll.richtung[i] / Math.max(1e-9, rmax));
                for (int s = 0; s < 2; s++) g.draw(new Arc2D.Double(cx - len, cy - len, 2 * len, 2 * len, i * 15 + s * 180, 15, Arc2D.PIE));
            }
            g.setStroke(alt);
        }
        int tx = cx + rr + 24, ty = y + 26;
        g.setColor(TEXT);
        g.drawString(String.format(Locale.GERMAN, "senkrecht (60°–120°)  %.0f %%%s", 100 * v.senkrecht, soll(soll, "%.0f %%", soll == null ? 0 : 100 * soll.senkrecht)), tx, ty);
        g.drawString(String.format(Locale.GERMAN, "Kohärenz  %.2f%s", v.kohaerenz, soll(soll, "%.2f", soll == null ? 0 : soll.kohaerenz)), tx, ty + 20);
        g.drawString(String.format(Locale.GERMAN, "Hauptrichtung  %.0f°%s", v.hauptrichtung, soll(soll, "%.0f°", soll == null ? 0 : soll.hauptrichtung)), tx, ty + 40);
        g.setColor(LEISE);
        g.drawString(soll != null ? "Umriss: Soll" : "0 = nur Flecken, 1 = nur Linien", tx, ty + 60);

        // Farbe
        y += 2 * rr + 30;
        ueberschrift(g, "Farbe", x, y);
        y += 8;
        double h = Math.toRadians(v.farbton);
        g.setColor(new Color(Lab.zuRgb(55, v.buntheit * Math.cos(h), v.buntheit * Math.sin(h))));
        g.fillRect(x, y, 34, 22);
        g.setColor(new Color(Lab.zuRgb(v.akzentLab[0], v.akzentLab[1], v.akzentLab[2])));
        if (v.akzent > 0) g.fillRect(x + w / 2 + 20, y, 34, 22);
        g.setColor(TEXT);
        g.drawString(String.format(Locale.GERMAN, "Farbton %.0f°%s", v.farbton, soll(soll, "%.0f°", soll == null ? 0 : soll.farbton)), x + 42, y + 15);
        g.drawString(String.format(Locale.GERMAN, "Akzente %.1f %%%s", 100 * v.akzent, soll(soll, "%.1f %%", soll == null ? 0 : 100 * soll.akzent)), x + w / 2 + 62, y + 15);
    }

    /** „ (Soll …)“ oder nichts. */
    static String soll(Vorlage soll, String fmt, double wert) {
        return soll == null ? "" : " (" + String.format(Locale.GERMAN, fmt, wert) + ")";
    }

    static void anteile(Graphics2D g, Vorlage v, int x, int y, int w, int hoehe) {
        double ax = x;
        for (int i = 0; i < v.palette.size(); i++) {
            double aw = v.palette.anteil[i] * w;
            g.setColor(new Color(v.palette.rgb(i)));
            g.fillRect((int) Math.round(ax), y, (int) Math.round(ax + aw) - (int) Math.round(ax), hoehe);
            ax += aw;
        }
        g.setColor(LINIE);
        g.drawRect(x, y, w, hoehe);
    }

    static void ueberschrift(Graphics2D g, String s, int x, int y) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.setColor(TEXT);
        g.drawString(s, x, y);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
    }

    static void kurve(Graphics2D g, double[] d, double[] f, Color c, int x, int y, int w, int h, double lo, double hi, Stroke strich) {
        if (d.length == 0) return;
        Path2D.Double p = new Path2D.Double();
        for (int i = 0; i < d.length; i++) {
            double px = x + w * (Math.log10(Math.max(1e-6, d[i])) - lo) / (hi - lo), py = y + h * (1 - f[i]);
            px = Math.max(x, Math.min(x + w, px));
            if (i == 0) p.moveTo(px, y + h);
            p.lineTo(px, py);
        }
        Stroke alt = g.getStroke();
        g.setColor(c);
        g.setStroke(strich);
        g.draw(p);
        g.setStroke(alt);
    }

    static void marke(Graphics2D g, double d50, Color c, int x, int y, int w, int h, double lo, double hi, boolean voll) {
        if (d50 <= 0) return;
        int px = (int) Math.round(x + w * (Math.log10(d50) - lo) / (hi - lo));
        g.setColor(c);
        if (voll) g.fillOval(px - 4, y + h / 2 - 4, 8, 8);
        else g.drawOval(px - 5, y + h / 2 - 5, 10, 10);
    }

    /** Alle Vorlagen nebeneinander, drei je Zeile. */
    static BufferedImage tafel(Vorlage[] vs) {
        int sp = 3, rand = 20, zeilen = (vs.length + sp - 1) / sp;
        BufferedImage im = new BufferedImage(rand + sp * (BREITE + rand), rand + zeilen * (HOEHE + rand), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(GRUND);
        g.fillRect(0, 0, im.getWidth(), im.getHeight());
        for (int i = 0; i < vs.length; i++) zeichnen(g, vs[i], rand + (i % sp) * (BREITE + rand), rand + (i / sp) * (HOEHE + rand));
        g.dispose();
        return im;
    }
}
