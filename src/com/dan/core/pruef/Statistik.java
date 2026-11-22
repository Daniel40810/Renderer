package com.dan.core.pruef;

import com.dan.core.feld.Feld;

import java.util.Arrays;

/**
 * Kennzahlen für den Vergleich von Modell und Messung und von zwei Feldern (Werkbuch HalligHooge Teil A, 5.9).
 * Paare, in denen ein Wert NaN ist, zählen nicht.
 */
public final class Statistik {
    /** Wurzel des mittleren Fehlerquadrats. */
    public static double rmse(double[] modell, double[] messung) {
        double s = 0;
        int n = 0;
        for (int i = 0; i < modell.length; i++) {
            double d = modell[i] - messung[i];
            if (Double.isNaN(d)) continue;
            s += d * d;
            n++;
        }
        return Math.sqrt(s / n);
    }

    /** Mittlere Abweichung Modell − Messung. */
    public static double bias(double[] modell, double[] messung) {
        double s = 0;
        int n = 0;
        for (int i = 0; i < modell.length; i++) {
            double d = modell[i] - messung[i];
            if (Double.isNaN(d)) continue;
            s += d;
            n++;
        }
        return s / n;
    }

    /** Skill nach Willmott (1981): 1 − Σ(M − O)² / Σ(|M − Ō| + |O − Ō|)²; 1 ist vollkommen, 0 ohne Wert. */
    public static double willmott(double[] modell, double[] messung) {
        double mittel = 0;
        int n = 0;
        for (int i = 0; i < modell.length; i++) {
            if (Double.isNaN(modell[i] - messung[i])) continue;
            mittel += messung[i];
            n++;
        }
        mittel /= n;
        double z = 0, ne = 0;
        for (int i = 0; i < modell.length; i++) {
            double d = modell[i] - messung[i];
            if (Double.isNaN(d)) continue;
            z += d * d;
            double q = Math.abs(modell[i] - mittel) + Math.abs(messung[i] - mittel);
            ne += q * q;
        }
        return ne == 0 ? (z == 0 ? 1 : 0) : 1 - z / ne;
    }

    /** Korrelation nach Pearson. */
    public static double korrelation(double[] a, double[] b) {
        double ma = 0, mb = 0;
        int n = 0;
        for (int i = 0; i < a.length; i++) {
            if (Double.isNaN(a[i] - b[i])) continue;
            ma += a[i];
            mb += b[i];
            n++;
        }
        ma /= n;
        mb /= n;
        double sab = 0, saa = 0, sbb = 0;
        for (int i = 0; i < a.length; i++) {
            if (Double.isNaN(a[i] - b[i])) continue;
            sab += (a[i] - ma) * (b[i] - mb);
            saa += (a[i] - ma) * (a[i] - ma);
            sbb += (b[i] - mb) * (b[i] - mb);
        }
        return sab / Math.sqrt(saa * sbb);
    }

    /**
     * Vergleich zweier Felder Wert für Wert.
     *
     * @param groesste        größte Abweichung und ihr Index
     * @param rms             Wurzel des mittleren Abweichungsquadrats
     * @param median          Median der Beträge
     * @param p999            99,9-%-Wert der Beträge
     * @param innerhalb       Anteil der Werte mit Abweichung ≤ Grenze
     * @param schritte        größte Abweichung in float-Schritten (ulp des größeren Betrags), wie in P0
     * @param nan             Werte, die in genau einem Feld NaN sind, und Werte, die in beiden NaN sind
     */
    public record Vergleich(double groesste, int wo, double rms, double median, double p999, double innerhalb, double schritte, int nan, int beideNan, int werte) {
        public boolean gleich(double grenze) { return nan == 0 && groesste <= grenze; }
    }

    /** Vergleicht zwei Felder gleicher Länge; {@code grenze} bestimmt nur den Anteil {@code innerhalb}. */
    public static Vergleich vergleiche(Feld a, Feld b, double grenze) {
        if (a.laenge != b.laenge) throw new IllegalArgumentException("Vergleich: " + a + " gegen " + b);
        int n = a.laenge, nan = 0, beide = 0, m = 0, wo = -1, in = 0;
        double[] d = new double[n];
        double max = 0, q = 0, schritte = 0;
        for (int i = 0; i < n; i++) {
            double x = a.hol(i), y = b.hol(i);
            if (Double.isNaN(x) || Double.isNaN(y)) {
                if (Double.isNaN(x) && Double.isNaN(y)) beide++; else nan++;
                continue;
            }
            double e = Math.abs(x - y);
            d[m++] = e;
            q += e * e;
            if (e <= grenze) in++;
            if (e > max) { max = e; wo = i; }
            schritte = Math.max(schritte, e / Math.ulp((float) Math.max(Math.abs(x), Math.abs(y))));
        }
        Arrays.sort(d, 0, m);
        return new Vergleich(max, wo, m == 0 ? 0 : Math.sqrt(q / m), m == 0 ? 0 : d[m / 2], m == 0 ? 0 : d[(int) Math.min(m - 1, Math.floor(0.999 * m))],
                m == 0 ? 1 : in / (double) m, schritte, nan, beide, m);
    }

    private Statistik() { }
}
