package com.dan.render.reference;

/**
 * Gekrümmte Strahlen durch eine Luft mit der Brechzahl n(x) (P7b, Werkbuch 8.5): die Strahlgleichung
 * d/ds (n dr/ds) = ∇n, nach Runge-Kutta vierter Ordnung mit dem Zustand (r, T = n dr/ds) über die Bogenlänge s.
 * <p>
 * Im Bild der Referenz für Hooge steckt die Krümmung wie im Echtzeitbild in den Flächen (gesenkt um d² / 2 R_eff mit
 * k = 0,13); dass beides dasselbe trifft, prüft {@code ReferenceCheck} (Prüfstein 5). Die eigentliche Kimmung aus
 * {@code air.n} rechnet erst P10 mit dieser Klasse.
 */
public final class Strahlgleichung {
    /** Brechzahl und ihr Gradient am Ort. */
    public interface Brechzahl {
        double n(double[] r);

        double[] gradient(double[] r);
    }

    private final Brechzahl b;

    public Strahlgleichung(Brechzahl b) { this.b = b; }

    /** Zustand {x, y, z, Tx, Ty, Tz}. */
    public double[] start(double[] r, double[] d) {
        double n = b.n(r);
        double[] e = Vek.norm(d);
        return new double[] {r[0], r[1], r[2], n * e[0], n * e[1], n * e[2]};
    }

    private double[] ableitung(double[] z) {
        double[] r = {z[0], z[1], z[2]};
        double n = b.n(r);
        double[] g = b.gradient(r);
        return new double[] {z[3] / n, z[4] / n, z[5] / n, g[0], g[1], g[2]};
    }

    /** Ein Schritt der Länge h (RK4). */
    public double[] schritt(double[] z, double h) {
        double[] k1 = ableitung(z), z2 = new double[6], z3 = new double[6], z4 = new double[6];
        for (int i = 0; i < 6; i++) z2[i] = z[i] + 0.5 * h * k1[i];
        double[] k2 = ableitung(z2);
        for (int i = 0; i < 6; i++) z3[i] = z[i] + 0.5 * h * k2[i];
        double[] k3 = ableitung(z3);
        for (int i = 0; i < 6; i++) z4[i] = z[i] + h * k3[i];
        double[] k4 = ableitung(z4);
        double[] a = new double[6];
        for (int i = 0; i < 6; i++) a[i] = z[i] + h / 6 * (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i]);
        return a;
    }
}
