package com.dan.gertsch.demo;

import com.dan.gertsch.Druck;
import com.dan.gertsch.Lab;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.Locale;

/**
 * Zeichnet die Kennwerte eines {@link Druck} auf eine Tafel von {@link #BREITE} × {@link #HOEHE} (wie das
 * {@link Kennblatt}): Papier und Farbe, Ton, Punkte, Wolken, Korn. Mit einem Soll (der gemessene Holzschnitt) dessen
 * Werte dazu: Kurven gestrichelt, Zahlen in Klammern. Enthält keine Bildpunkte der Vorlage, nur Zahlen.
 */
final class Druckblatt {
    static final int BREITE = Kennblatt.BREITE, HOEHE = Kennblatt.HOEHE;
    static final Color WAAGERECHT = new Color(232, 168, 80), SENKRECHT = new Color(96, 164, 214);

    private Druckblatt() { }

    static void zeichnen(Graphics2D g, Druck d, int x0, int y0, Druck soll) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Kennblatt.TAFEL);
        g.fillRoundRect(x0, y0, BREITE, HOEHE, 14, 14);
        int x = x0 + 16, w = BREITE - 32, y = y0 + 28;
        g.setColor(Kennblatt.TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        g.drawString(d.titel.isEmpty() ? d.name : d.titel, x, y);
        if (soll != null) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.setColor(Kennblatt.LEISE);
            String s = "Soll: " + (soll.titel.isEmpty() ? soll.name : soll.titel);
            g.drawString(s, x + w - g.getFontMetrics().stringWidth(s), y);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.setColor(Kennblatt.LEISE);
        y += 18;
        g.drawString(String.format(Locale.GERMAN, "%s%d × %d · Messung für %d px Breite", d.quelle.isEmpty() ? "" : d.quelle + " · ", d.breite, d.hoehe, Druck.BREITE), x, y);

        // Papier und Farbe
        y += 24;
        Kennblatt.ueberschrift(g, "Papier und Farbe", x, y);
        y += 8;
        feld(g, d.papier, soll == null ? null : soll.papier, "Papier", x, y, w / 2 - 6);
        feld(g, d.farbe, soll == null ? null : soll.farbe, "Farbe", x + w / 2 + 6, y, w / 2 - 6);

        // Ton: Anteil Papier über den Anteil der Fläche (Wurzel-Achse, damit das Dunkle sichtbar bleibt)
        y += 56;
        Kennblatt.ueberschrift(g, "Ton: Anteil Papier über die Fläche", x, y);
        y += 10;
        int ph = 96;
        g.setColor(Kennblatt.LINIE);
        g.drawRect(x, y, w, ph);
        double top = 0.8;
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        for (double t : new double[]{0.02, 0.1, 0.3, 0.6}) {
            int ty = y + ph - (int) Math.round(ph * Math.sqrt(t / top));
            g.setColor(Kennblatt.LINIE);
            g.drawLine(x, ty, x + w, ty);
            g.setColor(Kennblatt.LEISE);
            g.drawString(String.format(Locale.GERMAN, "%.0f %%", 100 * t), x + 3, ty - 2);
        }
        if (soll != null) tonKurve(g, soll.ton, x, y, w, ph, top, Kennblatt.GESTRICHELT, Kennblatt.LEISE);
        tonKurve(g, d.ton, x, y, w, ph, top, new BasicStroke(2f), Kennblatt.HELL);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        y += ph + 16;
        g.setColor(Kennblatt.TEXT);
        g.drawString(String.format(Locale.GERMAN, "Mittel %.1f %%%s · Median %.1f %%%s · 95 %% %.0f %%%s", 100 * d.tonMittel, k(soll, "%.1f", soll == null ? 0 : 100 * soll.tonMittel),
                100 * d.ton[10], k(soll, "%.1f", soll == null ? 0 : 100 * soll.ton[10]), 100 * d.ton[19], k(soll, "%.0f", soll == null ? 0 : 100 * soll.ton[19])), x, y);

        // Punkte: Größe 12-fach (gefüllt: Ist, Umriss: Soll), Zahlen
        y += 26;
        Kennblatt.ueberschrift(g, "Gestochene Punkte (bei " + Druck.BREITE + " px Breite)", x, y);
        y += 10;
        double mass = 12;
        int cx = x + 34, cy = y + 30;
        double di = d.punkt * Druck.BREITE * mass;
        g.setColor(new Color(Lab.zuRgb(d.papier[0], d.papier[1], d.papier[2])));
        g.fill(new Ellipse2D.Double(cx - di / 2, cy - di / 2, di, di));
        if (soll != null) {
            double ds = soll.punkt * Druck.BREITE * mass;
            Stroke alt = g.getStroke();
            g.setStroke(Kennblatt.GESTRICHELT);
            g.setColor(Kennblatt.TEXT);
            g.draw(new Ellipse2D.Double(cx - ds / 2, cy - ds / 2, ds, ds));
            g.setStroke(alt);
        }
        int tx = x + 84;
        g.setColor(Kennblatt.TEXT);
        g.drawString(String.format(Locale.GERMAN, "Durchmesser %.2f px%s", d.punkt * Druck.BREITE, k(soll, "%.2f", soll == null ? 0 : soll.punkt * Druck.BREITE)), tx, y + 14);
        g.drawString(String.format(Locale.GERMAN, "nächster Punkt %.1f px%s", d.abstand * Druck.BREITE, k(soll, "%.1f", soll == null ? 0 : soll.abstand * Druck.BREITE)), tx, y + 32);
        g.drawString(String.format(Locale.GERMAN, "Ordnung (Clark–Evans) R %.2f%s", d.ordnung, k(soll, "%.2f", soll == null ? 0 : soll.ordnung)), tx, y + 50);
        g.setColor(Kennblatt.LEISE);
        g.drawString("12-fach; R 1 = Zufall, Blue Noise 1,5 bis 1,8", tx, y + 66);

        // Wolken: Autokorrelation über den Abstand (logarithmisch)
        y += 92;
        Kennblatt.ueberschrift(g, "Wolken im Farbauftrag: Autokorrelation", x, y);
        y += 10;
        ph = 104;
        g.setColor(Kennblatt.LINIE);
        g.drawRect(x, y, w, ph);
        double lo = Math.log10(0.015), hi = Math.log10(0.4);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        for (double l : Druck.LAG) {
            int lx = x + (int) Math.round(w * (Math.log10(l) - lo) / (hi - lo));
            g.setColor(Kennblatt.LINIE);
            g.drawLine(lx, y, lx, y + ph);
            g.setColor(Kennblatt.LEISE);
            String s = String.format(Locale.GERMAN, "%.0f %%", 100 * l);
            g.drawString(s, lx - g.getFontMetrics().stringWidth(s) / 2, y + ph + 12);
        }
        int y0l = y + ph / 2;
        g.setColor(Kennblatt.LINIE);
        g.drawLine(x, y0l, x + w, y0l);
        if (soll != null) {
            acf(g, soll.wolkenX, x, y, w, ph, lo, hi, Kennblatt.GESTRICHELT, WAAGERECHT);
            acf(g, soll.wolkenY, x, y, w, ph, lo, hi, Kennblatt.GESTRICHELT, SENKRECHT);
        }
        acf(g, d.wolkenX, x, y, w, ph, lo, hi, new BasicStroke(2f), WAAGERECHT);
        acf(g, d.wolkenY, x, y, w, ph, lo, hi, new BasicStroke(2f), SENKRECHT);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        y += ph + 30;
        g.setColor(WAAGERECHT);
        g.drawString("waagerecht", x, y);
        g.setColor(SENKRECHT);
        g.drawString("senkrecht", x + 80, y);
        g.setColor(Kennblatt.TEXT);
        g.drawString(String.format(Locale.GERMAN, "Streuung L* %.1f%s", d.wolken, k(soll, "%.1f", soll == null ? 0 : soll.wolken)), x + 160, y);
        g.setColor(Kennblatt.LEISE);
        y += 16;
        g.drawString("Abstand als Anteil der Breite; 1 = gleich, 0 = unabhängig", x, y);

        // Korn
        y += 28;
        Kennblatt.ueberschrift(g, "Korn", x, y);
        y += 18;
        g.setColor(Kennblatt.TEXT);
        g.drawString(String.format(Locale.GERMAN, "Streuung L* je Bildpunkt in der Farbe %.2f%s", d.korn, k(soll, "%.2f", soll == null ? 0 : soll.korn)), x, y);
    }

    static void feld(Graphics2D g, double[] lab, double[] soll, String name, int x, int y, int w) {
        g.setColor(new Color(Lab.zuRgb(lab[0], lab[1], lab[2])));
        g.fillRect(x, y, 40, 36);
        if (soll != null) {
            g.setColor(new Color(Lab.zuRgb(soll[0], soll[1], soll[2])));
            g.fillRect(x + 40, y, 14, 36);
        }
        g.setColor(Kennblatt.TEXT);
        g.drawString(name, x + 62, y + 13);
        g.setColor(Kennblatt.LEISE);
        g.drawString(String.format(Locale.GERMAN, "L%.0f %+.0f %+.0f%s", lab[0], lab[1], lab[2],
                soll == null ? "" : String.format(Locale.GERMAN, " · ΔE %.1f", Lab.deltaE(lab, soll))), x + 62, y + 29);
    }

    static void tonKurve(Graphics2D g, double[] ton, int x, int y, int w, int h, double top, Stroke strich, Color c) {
        Path2D.Double p = new Path2D.Double();
        for (int i = 0; i < ton.length; i++) {
            double px = x + w * i / (ton.length - 1.0), py = y + h - h * Math.sqrt(Math.max(0, Math.min(top, ton[i])) / top);
            if (i == 0) p.moveTo(px, py);
            else p.lineTo(px, py);
        }
        Stroke alt = g.getStroke();
        g.setStroke(strich);
        g.setColor(c);
        g.draw(p);
        g.setStroke(alt);
    }

    static void acf(Graphics2D g, double[] a, int x, int y, int w, int h, double lo, double hi, Stroke strich, Color c) {
        Path2D.Double p = new Path2D.Double();
        for (int i = 0; i < a.length; i++) {
            double px = x + w * (Math.log10(Druck.LAG[i]) - lo) / (hi - lo), py = y + h / 2.0 - h / 2.0 * Math.max(-1, Math.min(1, a[i]));
            if (i == 0) p.moveTo(px, py);
            else p.lineTo(px, py);
        }
        Stroke alt = g.getStroke();
        g.setStroke(strich);
        g.setColor(c);
        g.draw(p);
        g.setStroke(alt);
    }

    /** „ (Soll …)“ oder nichts. */
    static String k(Druck soll, String fmt, double wert) { return soll == null ? "" : " (" + String.format(Locale.GERMAN, fmt, wert) + ")"; }
}
