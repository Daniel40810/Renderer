package com.dan.render.frame;

import com.dan.render.frame.film.Curve;
import com.dan.render.frame.film.Film;

/**
 * Die Kurve von der Szene zum Bildschirm aus {@code film} (Werkbuch HalligHooge Teil A, 8.1 und 12.7: Vision3 auf
 * 2383, ohne Korn, Halation, Bildstand und Flackern [G]) in einer Form für die Karte.
 * <p>
 * {@link Film#develop} zerfällt in drei Teile: eine lineare Abbildung auf die Schichten des Negativs (Matrix mal
 * Belichtung), je Schicht eine Kurve von log₁₀ H auf die Nettodichte der Kopie (Negativ, Kopierlicht, Kopie, Verblassen)
 * und die Projektion (Farbstoffe gemischt, 10^−e, Verstärkung, Knie). Nur der mittlere Teil wird tabelliert, mit
 * {@link #N} Werten je Schicht über log₁₀ H von {@link #X0} bis {@link #X1}, linear dazwischen; Matrix und Projektion
 * rechnet der Shader genau. Unter X0 gilt der Wert bei X0 (das Negativ ist dort im Schleier), über X1 der bei X1.
 */
public final class Filmkurve {
    public static final int N = 2048;
    public static final double X0 = -7, X1 = 3;

    /** Tabelle: je Stelle drei Nettodichten (und 0), wie das Bild RGBA32F sie trägt. */
    public final float[] tabelle = new float[4 * N];
    /** Parameter für den Shader: Matrix (9), Faktor, Farbstoffe normiert (9), Verstärkung, X0, X1 – 24 floats. */
    public final float[] parameter = new float[24];
    private final double[] m = new double[9], cr = new double[9];
    private final double k, gain;

    public Filmkurve(Film f) {
        double[] mat = f.negative.matrix(), dye = f.print.dye(), b = f.printerOffsets(), o = f.grade.offsets(), keep = f.dyeKept();
        Curve[] neg = f.negative.curves(), pr = f.print.curves();
        k = Math.pow(2, f.exposureStops) / Film.GRAY;
        gain = f.displayGain;
        System.arraycopy(mat, 0, m, 0, 9);
        for (int i = 0; i < 3; i++) {
            double s = dye[3 * i] + dye[3 * i + 1] + dye[3 * i + 2];
            for (int j = 0; j < 3; j++) cr[3 * i + j] = dye[3 * i + j] / s;
        }
        for (int t = 0; t < N; t++) {
            double x = X0 + (X1 - X0) * t / (N - 1);
            for (int c = 0; c < 3; c++) tabelle[4 * t + c] = (float) ((pr[c].density(-neg[c].density(x) + b[c] + o[c]) - pr[c].dmin()) * keep[c]);
        }
        for (int i = 0; i < 9; i++) parameter[i] = (float) m[i];
        parameter[9] = (float) k;
        for (int i = 0; i < 9; i++) parameter[10 + i] = (float) cr[i];
        parameter[19] = (float) gain;
        parameter[20] = (float) X0;
        parameter[21] = (float) X1;
    }

    /** Dieselbe Rechnung wie der Shader, in double (mit der Tabelle): lineares sRGB der Szene → linear für den Bildschirm. */
    public double[] anwenden(double r, double g, double b) {
        double[] h = {(m[0] * r + m[1] * g + m[2] * b) * k, (m[3] * r + m[4] * g + m[5] * b) * k, (m[6] * r + m[7] * g + m[8] * b) * k}, dd = new double[3], out = new double[3];
        for (int c = 0; c < 3; c++) {
            double x = Math.log10(Math.max(1e-7, h[c])), u = (Math.min(X1, Math.max(X0, x)) - X0) / (X1 - X0) * (N - 1);
            int i = Math.min(N - 2, (int) Math.floor(u));
            double w = u - i;
            dd[c] = tabelle[4 * i + c] * (1 - w) + tabelle[4 * (i + 1) + c] * w;
        }
        for (int i = 0; i < 3; i++) out[i] = Film.knee(Math.pow(10, -(cr[3 * i] * dd[0] + cr[3 * i + 1] * dd[1] + cr[3 * i + 2] * dd[2])) * gain);
        return out;
    }
}
