package com.dan.core.szene;

/**
 * „Seekrank“ messbar (Seegang Werkbuch, S7c, Datei 89, SF110 A): die senkrechte Beschleunigung am Auge, gewichtet mit W_f
 * nach ISO 2631-1:1997 (Anhang A), ergibt die Dosis der Bewegungskrankheit MSDV_z = (∫ a_w² dt)^½ (m/s^1,5). Nach Anhang D
 * erbrechen sich davon rund K_m · MSDV_z Prozent der Menschen (K_m ≈ 1/3, gemischt, nicht gewöhnt).
 * <p>
 * W_f = H_h · H_l · H_t · H_s mit f₁ = 0,08 Hz (Hochpass), f₂ = 0,63 Hz (Tiefpass), beide Butterworth zweiter Ordnung;
 * H_t = 1 / (1 + p/(Q₄ ω₄) + p²/ω₄²) mit f₄ = 0,25 Hz, Q₄ = 0,86 (f₃ unendlich); H_s = (1 + p/(Q₅ ω₅) + p²/ω₅²) /
 * (1 + p/(Q₆ ω₆) + p²/ω₆²) · (ω₅/ω₆)² mit f₅ = 0,0625 Hz, Q₅ = 0,80, f₆ = 0,10 Hz, Q₆ = 0,80. Je Glied ein Biquad über die
 * bilineare Abbildung bei der Abtastrate der Messung.
 */
public final class Seekrank {
    public static final String VERSION = "0.1";
    public static final double F1 = 0.08, F2 = 0.63, F4 = 0.25, Q4 = 0.86, F5 = 0.0625, Q5 = 0.80, F6 = 0.10, Q6 = 0.80, K_M = 1.0 / 3;

    private final double[][] bq;                     // je Glied b0, b1, b2, a1, a2 (a0 = 1)
    private final double[][] z;
    private final double dt;
    private double summe, zeit;

    /** Für Messungen im Abstand dt (s). */
    public Seekrank(double dt) {
        this.dt = dt;
        double w1 = 2 * Math.PI * F1, w2 = 2 * Math.PI * F2, w4 = 2 * Math.PI * F4, w5 = 2 * Math.PI * F5, w6 = 2 * Math.PI * F6, r2 = Math.sqrt(2);
        // je Glied (b2 p² + b1 p + b0) / (a2 p² + a1 p + a0)
        double[][] s = {
                {1, 0, 0, 1, r2 * w1, w1 * w1},                                   // Hochpass: p² / (p² + √2 ω₁ p + ω₁²)
                {0, 0, w2 * w2, 1, r2 * w2, w2 * w2},                             // Tiefpass
                {0, 0, w4 * w4, 1, w4 / Q4, w4 * w4},                             // Übergang
                {1, w5 / Q5, w5 * w5, 1, w6 / Q6, w6 * w6}};                      // Stufe: (p² + ω₅/Q₅ p + ω₅²) / (p² + ω₆/Q₆ p + ω₆²)
        bq = new double[4][];
        z = new double[4][2];
        double c = 2 / dt;
        for (int i = 0; i < 4; i++) {
            double b2 = s[i][0], b1 = s[i][1], b0 = s[i][2], a2 = s[i][3], a1 = s[i][4], a0 = s[i][5];
            double B0 = b2 * c * c + b1 * c + b0, B1 = 2 * (b0 - b2 * c * c), B2 = b2 * c * c - b1 * c + b0;
            double A0 = a2 * c * c + a1 * c + a0, A1 = 2 * (a0 - a2 * c * c), A2 = a2 * c * c - a1 * c + a0;
            bq[i] = new double[] {B0 / A0, B1 / A0, B2 / A0, A1 / A0, A2 / A0};
        }
    }

    /** Eine Messung der senkrechten Beschleunigung (m/s²); gibt den gewichteten Wert. */
    public double schritt(double a) {
        double x = a;
        for (int i = 0; i < 4; i++) {
            double[] q = bq[i], s = z[i];
            double y = q[0] * x + s[0];                                       // transponierte Form II
            s[0] = q[1] * x - q[3] * y + s[1];
            s[1] = q[2] * x - q[4] * y;
            x = y;
        }
        summe += x * x * dt;
        zeit += dt;
        return x;
    }

    /** MSDV_z bisher (m/s^1,5). */
    public double msdv() { return Math.sqrt(summe); }

    /** Gemessene Zeit (s). */
    public double zeit() { return zeit; }

    /** MSDV_z auf die Dauer T (s) hochgerechnet, wenn die Bewegung gleich bleibt (× √(T / Zeit)). */
    public double msdv(double dauer) { return zeit > 0 ? msdv() * Math.sqrt(dauer / zeit) : 0; }

    /** Anteil der Menschen, die sich erbrechen (Prozent), nach Anhang D: K_m · MSDV_z. */
    public static double erbrechen(double msdv) { return K_M * msdv; }

    /** Betrag von W_f bei der Frequenz f (Hz), analog (zur Prüfung). */
    public static double betrag(double f) {
        double w = 2 * Math.PI * f, w1 = 2 * Math.PI * F1, w2 = 2 * Math.PI * F2, w4 = 2 * Math.PI * F4, w5 = 2 * Math.PI * F5, w6 = 2 * Math.PI * F6, r2 = Math.sqrt(2);
        return bet(-w * w, 0, 1, 0, 0, w, r2 * w1, w1 * w1) * bet(0, 0, 0, w2 * w2, 0, w, r2 * w2, w2 * w2)
                * bet(0, 0, 0, w4 * w4, 0, w, w4 / Q4, w4 * w4) * bet2(w, w5 / Q5, w5 * w5, w6 / Q6, w6 * w6);
    }

    // |(b2 (iw)² + b1 iw + b0) / ((iw)² + a1 iw + a0)| mit b2 = 1 im ersten Fall (Zähler -w², 0)
    private static double bet(double zr, double zi, double b2, double b0, double b1, double w, double a1, double a0) {
        double nr = b2 == 1 ? zr : b0, ni = b2 == 1 ? zi : b1 * w;
        double dr = a0 - w * w, di = a1 * w;
        return Math.hypot(nr, ni) / Math.hypot(dr, di);
    }

    private static double bet2(double w, double b1, double b0, double a1, double a0) {
        return Math.hypot(b0 - w * w, b1 * w) / Math.hypot(a0 - w * w, a1 * w);
    }
}
