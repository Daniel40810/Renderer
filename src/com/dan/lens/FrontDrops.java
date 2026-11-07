package com.dan.lens;

import java.util.Locale;

/**
 * Tropfen auf der Frontlinse (seit LituyaBay1958 W9c Runde 2, Werkbuch W7e): was sie im Bild tun.
 * <p>
 * Die Frontlinse liegt nur wenige Zentimeter vor der Eintrittspupille (Doppel-Gauß 50 mm: 20 mm, Weitwinkel 12 mm, Tele
 * 15 mm). Ein Tropfen dort ist darum nie scharf im Bild. Jeder Bildpunkt sammelt sein Licht über ein Bündel, das die
 * Frontlinse in einem Kreis vom Halbmesser der Pupille trifft, um den Ort, an dem der Hauptstrahl durchgeht (z · tan θ vor
 * der Pupille). Liegt ein Teil c dieses Kreises unter Tropfen, so fehlt dem scharfen Bild dieser Anteil:
 * <ul>
 * <li>Ein Tropfen ist eine Kugelkappe mit dem Randwinkel θ_c (60° auf Glas). Senkrechtes Licht trifft innen auf seine
 * Oberfläche unter dem Winkel σ der Neigung; über dem Grenzwinkel arcsin(1/n) = 48,6° wird es ganz zurückgeworfen, darunter
 * gebrochen und um δ = arcsin(n sin σ) − σ abgelenkt, bis 41,4°. Der Anteil, der durchgeht, ist (sin σ_g / sin θ_c)² =
 * (0,75/0,866)² = 0,75; ein Viertel geht verloren (der dunkle Rand eines Tropfens).</li>
 * <li>Das Durchgehende wird nach {@link #phase} über bis zu 41° verteilt: als weicher Schleier aus dem Bild ringsum und, steht
 * die Sonne oder ein helles Licht in diesem Kegel, als leuchtender Fleck.</li>
 * </ul>
 * Was das Bild zeigt, hängt vom Objektiv ab: Beim Tele (Pupille 46 mm, Feld ±4°) sieht jeder Bildpunkt fast dieselben
 * Tropfen, der Schleier liegt gleichmäßig; beim Weitwinkel (Pupille 8 mm, Feld ±38°) wandert der Kreis über die Linse, jeder
 * Tropfen wird ein weicher Fleck in der Form der Blende, so groß wie (Pupille + Tropfen) / z im Winkel.
 * <p>
 * Der Kern rechnet nur Geometrie und Optik des Tropfens (Java 21); woher die Tropfen kommen (Regen, Gischt, Laufen, Wischen),
 * sagt der Aufrufer, etwa aus {@code com.dan.wetglass}. {@code gl/LensGl} legt das Ergebnis über das Bild.
 */
public final class FrontDrops {
    /** Brechzahl des Wassers, Randwinkel des Tropfens auf Glas (Grad). */
    public double n = 1.333, contactDeg = 60;
    /** Tropfen: Ort (mm von der Achse der Frontlinse; x zur rechten Seite der Kamera, wie im Bild, y oben) und Halbmesser (mm). */
    public double[] xMm = new double[0], yMm = new double[0], rMm = new double[0];
    public int count;
    /** Feintropfen und Film: zusätzlicher Anteil der Fläche unter Wasser, gleichmäßig (0 … 1). */
    public double mist;

    /** Anteil des Lichts, das durch einen Tropfen geht (der Rest wird zurückgeworfen): (sin σ_g / sin θ_c)², höchstens 1. */
    public double transmitted() {
        double sg = 1 / n, sc = Math.sin(Math.toRadians(contactDeg));
        return Math.min(1, (sg / sc) * (sg / sc));
    }

    /** Größte Ablenkung (rad): an der Neigung min(θ_c, σ_g). */
    public double maxDeflection() {
        double s = Math.min(Math.toRadians(contactDeg), Math.asin(1 / n) - 1e-9);
        return Math.asin(Math.min(1, n * Math.sin(s))) - s;
    }

    /**
     * Verteilung der Ablenkung (je Steradiant) auf nb Ringe zu maxDeflection/nb: p_k, so dass Σ p_k · ΔΩ_k = transmitted().
     * Licht fällt senkrecht auf die Kappe (Fläche ∝ sin σ cos σ dσ); kleine Neigungen lenken wenig ab (δ ≈ (n − 1) σ).
     */
    public double[] phase(int nb) {
        double dmax = maxDeflection(), sMax = Math.min(Math.toRadians(contactDeg), Math.asin(1 / n));
        double sc = Math.sin(Math.toRadians(contactDeg));
        double[] e = new double[nb];
        int steps = 20000;
        for (int k = 0; k < steps; k++) {
            double s0 = sMax * k / steps, s1 = sMax * (k + 1) / steps, sm = 0.5 * (s0 + s1);
            // Anteil der Kappe (Grundfläche π (R sin θ_c)²) zwischen s0 und s1: (sin² s1 − sin² s0) / sin² θ_c
            double frac = (Math.sin(s1) * Math.sin(s1) - Math.sin(s0) * Math.sin(s0)) / (sc * sc);
            double d = Math.asin(Math.min(1, n * Math.sin(sm))) - sm;
            int b = (int) Math.min(nb - 1, Math.floor(d / dmax * nb));
            e[b] += frac;
        }
        double[] p = new double[nb];
        for (int b = 0; b < nb; b++) {
            double d0 = dmax * b / nb, d1 = dmax * (b + 1) / nb;
            double dOmega = 2 * Math.PI * (Math.cos(d0) - Math.cos(d1));
            p[b] = e[b] / dOmega;
        }
        return p;
    }

    /** Fläche der Schnittmenge zweier Kreise (Halbmesser r1, r2, Abstand d). */
    public static double overlap(double r1, double r2, double d) {
        if (d >= r1 + r2) return 0;
        double rs = Math.min(r1, r2), rb = Math.max(r1, r2);
        if (d <= rb - rs) return Math.PI * rs * rs;
        double a = r1 * r1 * Math.acos(Math.max(-1, Math.min(1, (d * d + r1 * r1 - r2 * r2) / (2 * d * r1))));
        double b = r2 * r2 * Math.acos(Math.max(-1, Math.min(1, (d * d + r2 * r2 - r1 * r1) / (2 * d * r2))));
        double c = 0.5 * Math.sqrt(Math.max(0, (-d + r1 + r2) * (d + r1 - r2) * (d - r1 + r2) * (d + r1 + r2)));
        return a + b - c;
    }

    /**
     * Anteil c des Bündels unter Tropfen für einen Bildpunkt, dessen Hauptstrahl die Frontlinse bei (hx, hy) mm trifft;
     * Bündel als Kreis vom Halbmesser rb (mm). Die Tropfen decken sich nicht (das Wasser ist eine Schicht): Summe, höchstens 1,
     * dazu der Film: 1 − (1 − Summe)(1 − mist).
     */
    public double coverAt(double hx, double hy, double rb) {
        double a = Math.PI * rb * rb, s = 0;
        for (int i = 0; i < count; i++) {
            double d = Math.hypot(xMm[i] - hx, yMm[i] - hy);
            if (d < rb + rMm[i]) s += overlap(rb, rMm[i], d);
        }
        double c = Math.min(1, s / a);
        return 1 - (1 - c) * (1 - Math.max(0, Math.min(1, mist)));
    }

    /**
     * Der Anteil c über das Bild als Gitter gw × gh (Zeilen von unten, wie eine Textur): Bildpunkt (u, v) ∈ [0, 1]² sieht unter
     * tan θ = ((u − ½) · Breite, (v − ½) · Höhe) / f (mm auf dem Sensor, ohne Verzeichnung), sein Hauptstrahl trifft die
     * Frontlinse zMm vor der Pupille bei z · tan θ, das Bündel hat den Halbmesser der Pupille pupilRadiusMm (für ferne Dinge).
     */
    public float[] coverage(int gw, int gh, double sensorWmm, double sensorHmm, double focalMm, double zMm, double pupilRadiusMm) {
        float[] g = new float[gw * gh];
        for (int j = 0; j < gh; j++)
            for (int i = 0; i < gw; i++) {
                double u = (i + 0.5) / gw - 0.5, v = (j + 0.5) / gh - 0.5;
                double tx = u * sensorWmm / focalMm, ty = v * sensorHmm / focalMm;
                g[j * gw + i] = (float) coverAt(zMm * tx, zMm * ty, pupilRadiusMm);
            }
        return g;
    }

    /** Wie viel Fläche die Tropfen auf einem Kreis vom Halbmesser r (mm) um die Achse decken (Anteil). */
    public double wetFraction(double r) { return coverAt(0, 0, r); }

    /** „12 Tropfen (0,4 bis 1,8 mm), Film 3 %“ */
    public String describe() {
        double lo = Double.MAX_VALUE, hi = 0;
        for (int i = 0; i < count; i++) { lo = Math.min(lo, rMm[i]); hi = Math.max(hi, rMm[i]); }
        return count == 0 ? String.format(Locale.GERMAN, "keine Tropfen, Film %.0f %%", 100 * mist)
                : String.format(Locale.GERMAN, "%d Tropfen (%.1f bis %.1f mm), Film %.0f %%", count, lo, hi, 100 * mist);
    }

    /** Setzt die Tropfen (Arrays werden übernommen). */
    public void set(double[] x, double[] y, double[] r, int n) {
        xMm = x;
        yMm = y;
        rMm = r;
        count = n;
    }
}
