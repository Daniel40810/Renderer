package com.dan.core.szene.spektrum;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Die Spektren der gemeinsamen Szene (P7a, Werkbuch HalligHooge Teil A, 12.8, F53 A), 360–830 nm in 1 nm, aus
 * {@code spektrum.txt} (Quellen dort und im Werkbuch, 14):
 * <ul>
 * <li>Normspektralwerte CIE 1931 (2°) als Tabelle; XYZ → lineares sRGB mit der Matrix aus den Primärvalenzen von
 * IEC 61966-2-1 und dem Weißpunkt D65 aus der Tabelle (XYZ 0,95047; 1; 1,08883 nach ASTM E308; IEC rundet auf
 * x 0,3127, y 0,3290) in double ({@link #XYZ_RGB}).</li>
 * <li>Sonne außerhalb der Luft in 1 AE nach ASTM G173-03 (W/(m² nm)); Lichtausbeute K_m = 683 lm/W.</li>
 * <li>Rayleigh-Streuung der Luft am Boden nach Bodhaine u. a. (1999, Gl. 30: optische Dicke bei 1013,25 hPa, 45°, 0 m),
 * verteilt auf die Skalenhöhe der Szene ({@link #rayleigh}).</li>
 * <li>Ozon: Querschnitt nach Serdyuchenko u. a. (2014) in 10 nm (Bruneton 2017), linear.</li>
 * <li>Reines Wasser: Absorption nach Pope und Fry (1997), außerhalb 380–727,5 nm nach IOCCG (2018).</li>
 * <li>RGB → Spektrum für Reflexionen nach Mallett und Yuksel (2019): drei Basisspektren, deren Summe mit den
 * RGB-Gewichten unter D65 genau das RGB zurückgibt. Weil die Basis hier auf 1 nm gelegt ist, wird sie mit der Matrix
 * {@link #KORREKTUR} nachgestellt; so ist die Rückrechnung bis auf Rundung genau (Prüfstein 4).</li>
 * </ul>
 */
public final class Spektrum {
    /** Kürzeste und längste Wellenlänge (nm), Schritt 1 nm. */
    public static final int MIN = 360, MAX = 830, N = MAX - MIN + 1;
    /** Lichtausbeute (lm/W). */
    public static final double KM = 683.0;
    /** Wellenlängen der drei Kanäle des Echtzeitbilds (nm) [G], wie {@code Atmosphere} und {@code Wasseroptik}. */
    public static final double[] KANAELE = {680, 550, 440};

    static final double[] X = new double[N], Y = new double[N], Z = new double[N], D65 = new double[N], SONNE = new double[N],
            AW = new double[N], O3 = new double[N];
    static final double[][] BASIS = new double[3][N];

    /** XYZ → lineares sRGB (Zeilen R, G, B), aus den Primärvalenzen und D65 (XYZ 0,95047; 1; 1,08883). */
    public static final double[][] XYZ_RGB;
    /** Korrektur der Basis nach Mallett und Yuksel: Gewichte c' = KORREKTUR · c. */
    public static final double[][] KORREKTUR;
    /** Weiß von D65 in XYZ (Y = 1) nach der Tabelle; Faktor, der D65 auf Y = 1 bringt. */
    static final double D65_Y;

    static {
        try (InputStream in = Spektrum.class.getResourceAsStream("spektrum.txt")) {
            if (in == null) throw new IOException("spektrum.txt fehlt im Klassenpfad");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String s;
            int i = 0;
            while ((s = r.readLine()) != null) {
                s = s.strip();
                if (s.isEmpty() || s.startsWith("#")) continue;
                String[] t = s.split("\\s+");
                if (Integer.parseInt(t[0]) != MIN + i) throw new IOException("spektrum.txt: Zeile für " + t[0] + " nm statt " + (MIN + i));
                X[i] = Double.parseDouble(t[1]);
                Y[i] = Double.parseDouble(t[2]);
                Z[i] = Double.parseDouble(t[3]);
                D65[i] = Double.parseDouble(t[4]);
                SONNE[i] = Double.parseDouble(t[5]);
                AW[i] = Double.parseDouble(t[6]);
                O3[i] = Double.parseDouble(t[7]);
                for (int k = 0; k < 3; k++) BASIS[k][i] = Double.parseDouble(t[8 + k]);
                i++;
            }
            if (i != N) throw new IOException("spektrum.txt: " + i + " Zeilen statt " + N);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        XYZ_RGB = invers(rgbNachXyz(0.64, 0.33, 0.30, 0.60, 0.15, 0.06, 0.95047 / (0.95047 + 1 + 1.08883), 1 / (0.95047 + 1 + 1.08883)));
        double y = 0;
        for (int i = 0; i < N; i++) y += D65[i] * Y[i];
        D65_Y = 1 / y;
        // Mallett und Yuksel: RGB jeder Basis unter D65 (Y von D65 = 1) mit dieser Tabelle; Korrektur = Inverse
        double[][] m = new double[3][3];
        for (int k = 0; k < 3; k++) {
            double[] xyz = new double[3];
            for (int i = 0; i < N; i++) {
                double e = BASIS[k][i] * D65[i] * D65_Y;
                xyz[0] += e * X[i];
                xyz[1] += e * Y[i];
                xyz[2] += e * Z[i];
            }
            double[] rgb = mal(XYZ_RGB, xyz);
            for (int c = 0; c < 3; c++) m[c][k] = rgb[c];
        }
        KORREKTUR = invers(m);
    }

    private Spektrum() { }

    /** Index der Tabelle und Rest für λ (nm); außerhalb auf den Rand gehalten. */
    static double lerp(double[] t, double l) {
        double u = l - MIN;
        if (u <= 0) return t[0];
        if (u >= N - 1) return t[N - 1];
        int i = (int) u;
        double f = u - i;
        return t[i] + f * (t[i + 1] - t[i]);
    }

    /** x̄, ȳ, z̄ bei λ (nm), linear zwischen den Werten der Tabelle. */
    public static double[] xyz(double l) { return new double[] {lerp(X, l), lerp(Y, l), lerp(Z, l)}; }

    public static double xBar(double l) { return lerp(X, l); }

    public static double yBar(double l) { return lerp(Y, l); }

    public static double zBar(double l) { return lerp(Z, l); }

    /** Sonne außerhalb der Luft in 1 AE (W/(m² nm)). */
    public static double sonne(double l) { return lerp(SONNE, l); }

    /** Beleuchtungsstärke der Sonne außerhalb der Luft in 1 AE (lx): K_m ∫ E ȳ dλ. */
    public static double sonneLux() {
        double s = 0;
        for (int i = 0; i < N; i++) s += SONNE[i] * Y[i];
        return KM * s;
    }

    /** D65 relativ, so dass Y = 1 (∫ D65 ȳ dλ = 1 in 1-nm-Schritten). */
    public static double d65(double l) { return lerp(D65, l) * D65_Y; }

    /** Absorption reinen Wassers (1/m). */
    public static double wasser(double l) { return lerp(AW, l); }

    /** Querschnitt des Ozons (m²). */
    public static double ozon(double l) { return lerp(O3, l); }

    /** Ozon: größte Teilchendichte (1/m³) für 300 DU in einer Schicht von 15 km wie Bruneton (2017) und Hillaire (2020). */
    public static final double OZON_DICHTE = 300 * 2.687e20 / 15000;

    /** Optische Dicke der Rayleigh-Streuung senkrecht durch die Luft nach Bodhaine u. a. (1999), Gl. 30 (λ in nm). */
    public static double rayleighTau(double l) {
        double u = l / 1000, a = 1 / (u * u), b = u * u;
        return 0.0021520 * (1.0455996 - 341.29061 * a - 0.90230850 * b) / (1 + 0.0027059889 * a - 85.968563 * b);
    }

    /** Rayleigh-Streuung am Boden (1/m) für eine Luft mit der Skalenhöhe H (m): τ / H. */
    public static double rayleigh(double l, double skalenhoehe) { return rayleighTau(l) / skalenhoehe; }

    /** Basisspektrum k (0 R, 1 G, 2 B) bei λ, ohne Korrektur. */
    public static double basis(int k, double l) { return lerp(BASIS[k], l); }

    /** Gewichte der Basis für ein lineares sRGB (mit {@link #KORREKTUR}). */
    public static double[] gewichte(double[] rgb) { return mal(KORREKTUR, rgb); }

    /** Reflexion bei λ zu den Gewichten aus {@link #gewichte}. */
    public static double reflexion(double[] gewichte, double l) {
        double u = l - MIN;
        if (u <= 0) return gewichte[0] * BASIS[0][0] + gewichte[1] * BASIS[1][0] + gewichte[2] * BASIS[2][0];
        if (u >= N - 1) return gewichte[0] * BASIS[0][N - 1] + gewichte[1] * BASIS[1][N - 1] + gewichte[2] * BASIS[2][N - 1];
        int i = (int) u;
        double f = u - i, s = 0;
        for (int k = 0; k < 3; k++) s += gewichte[k] * (BASIS[k][i] + f * (BASIS[k][i + 1] - BASIS[k][i]));
        return s;
    }

    /** Spektrum (Werte je 1 nm ab {@link #MIN}) nach XYZ: Σ s ȳ … in 1-nm-Schritten, ohne K_m. */
    public static double[] nachXyz(double[] s) {
        double[] r = new double[3];
        for (int i = 0; i < N; i++) {
            r[0] += s[i] * X[i];
            r[1] += s[i] * Y[i];
            r[2] += s[i] * Z[i];
        }
        return r;
    }

    /** XYZ → lineares sRGB. */
    public static double[] nachRgb(double[] xyz) { return mal(XYZ_RGB, xyz); }

    /** Wellenlänge (nm) des Index i der Tabelle. */
    public static double lambda(int i) { return MIN + i; }

    // ------------------------------------------------------------------ Matrizen

    static double[] mal(double[][] m, double[] v) {
        return new double[] {m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2], m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
                m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]};
    }

    static double[][] invers(double[][] m) {
        double a = m[0][0], b = m[0][1], c = m[0][2], d = m[1][0], e = m[1][1], f = m[1][2], g = m[2][0], h = m[2][1], k = m[2][2];
        double det = a * (e * k - f * h) - b * (d * k - f * g) + c * (d * h - e * g);
        return new double[][] {
                {(e * k - f * h) / det, (c * h - b * k) / det, (b * f - c * e) / det},
                {(f * g - d * k) / det, (a * k - c * g) / det, (c * d - a * f) / det},
                {(d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det}};
    }

    /** RGB → XYZ aus den Normfarbwertanteilen der Primärvalenzen und des Weißpunkts (Weiß hat Y = 1). */
    static double[][] rgbNachXyz(double xr, double yr, double xg, double yg, double xb, double yb, double xw, double yw) {
        double[][] p = {{xr / yr, xg / yg, xb / yb}, {1, 1, 1}, {(1 - xr - yr) / yr, (1 - xg - yg) / yg, (1 - xb - yb) / yb}};
        double[] w = {xw / yw, 1, (1 - xw - yw) / yw};
        double[] s = mal(invers(p), w);
        double[][] m = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) m[i][j] = p[i][j] * s[j];
        return m;
    }

    /** Für Prüfungen: die Zeilen der Tabelle als Liste {λ, x̄, ȳ, z̄}. */
    public static List<double[]> tabelle() {
        List<double[]> l = new ArrayList<>();
        for (int i = 0; i < N; i++) l.add(new double[] {MIN + i, X[i], Y[i], Z[i]});
        return l;
    }
}
