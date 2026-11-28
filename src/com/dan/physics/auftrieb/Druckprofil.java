package com.dan.physics.auftrieb;

/**
 * Die Druckhöhe in der Tiefe aus Summen je Band an der Oberfläche (Seegang Werkbuch, S7a; Plan Datei 86, 3.3).
 * <p>
 * Die Karte rechnet je Punkt und Frequenzband b an der Oberfläche S0_b = Σ a cos φ und S1_b = Σ a (k − k_b) cos φ (k_b:
 * mittlere Wellenzahl des Bands nach der Energie). Das Abklingen A(k, z) = cosh(k (z + h)) / cosh(k h) ist in k glatt;
 * in erster Ordnung um k_b gilt A(k) ≈ A(k_b) (1 + D (k − k_b)) mit D = ∂ ln A / ∂k = (z + h) tanh(k_b (z + h)) − h
 * tanh(k_b h). Damit
 * <pre>
 *   ψ(z) ≈ Σ_b A(k_b, z) · (S0_b + D_b(z) · S1_b)
 * </pre>
 * Der Fehler ist zweiter Ordnung in der Breite des Bands. Entscheidung S7a (nach Ermessen, Datei 87): statt der Druckhöhe in
 * drei Tiefen (Plan 3.3) gibt die Karte zwei Summen je Band; zwischen festen Tiefen zu interpolieren war bei kurzen Wellen
 * um mehrere Prozent ungenau.
 */
public final class Druckprofil {
    private Druckprofil() { }

    /** ψ in der Tiefe z (−h ≤ z ≤ 0) bei der Tiefe h aus nb Bändern. */
    public static double psi(double z, double h, int nb, double[] kb, double[] s0, double[] s1) {
        double zz = Math.max(-h, Math.min(0, z)), p = 0;
        for (int b = 0; b < nb; b++) {
            if (kb[b] <= 0) { p += s0[b]; continue; }
            double a = Welle.abklingen(kb[b], zz, h);
            double d = (zz + h) * Math.tanh(kb[b] * (zz + h)) - h * Math.tanh(kb[b] * h);
            p += a * (s0[b] + d * s1[b]);
        }
        return p;
    }

    /** Dasselbe nur nullter Ordnung (ohne S1), zum Vergleich. */
    public static double psi0(double z, double h, int nb, double[] kb, double[] s0) {
        double zz = Math.max(-h, Math.min(0, z)), p = 0;
        for (int b = 0; b < nb; b++) p += (kb[b] <= 0 ? 1 : Welle.abklingen(kb[b], zz, h)) * s0[b];
        return p;
    }
}
