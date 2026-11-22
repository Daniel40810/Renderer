package com.dan.core.gitter;

import com.dan.core.feld.Feld;
import com.dan.core.feld.Ort;

import java.util.Arrays;

/**
 * Umrechnung von Zellfeldern zwischen zwei Gittern (Werkbuch HalligHooge Teil A, 5.4): Schachteln gleicher Lage und
 * Gitter verschiedener Lage (geographisch ↔ UTM). Mengen bleiben erhalten, Zustände werden bilinear übertragen.
 * <ul>
 * <li>{@link #menge}: Werte sind Mengen je Zelle (m³, kg). Jede Quellzelle wird in {@code teilung²} Teile zerlegt;
 * jedes Teil geht mit seinem Anteil in die Zielzelle, in der seine Mitte liegt. Die Summe bleibt genau erhalten,
 * soweit die Quelle im Ziel liegt ({@link #verlust} nennt den Rest).</li>
 * <li>{@link #dichte}: Werte sind Dichten (m, kg/m²); erhalten bleibt das Integral über die Fläche.</li>
 * <li>{@link #zustand}: bilinear zwischen den Mitten der Quellzellen; am Rand der Quelle wird der Randwert gehalten.
 * Zielzellen außerhalb der Quelle bleiben unverändert ({@link #innen}).</li>
 * </ul>
 * Bei Schachteln gleicher Lage ist {@code teilung} = 1 (fein nach grob) oder das Verhältnis der Zellen (grob nach
 * fein) genau. Vektoren (u, v) werden nicht gedreht: Zwischen geographischem Gitter und UTM dreht das Modul sie selbst
 * um die Meridiankonvergenz ({@code raum.Utm.faktoren}).
 */
public final class Umrechnung {
    public final Gitter von, nach;
    /** Anteil der Quellmenge (bei gleichmäßiger Belegung), der außerhalb des Ziels liegt. */
    public final double verlust;
    private final int[] mStart, mZiel;
    private final double[] mAnteil;
    private final int[] zIndex;
    private final double[] zGewicht;
    /** Je Zielzelle: liegt ihre Mitte in der Quelle? */
    public final boolean[] innen;

    /** Baut die Gewichte. {@code teilung}: Teile je Quellzelle und Richtung für {@link #menge}. */
    public static Umrechnung bauen(Gitter von, Gitter nach, int teilung) { return new Umrechnung(von, nach, teilung); }

    /** Baut die Gewichte mit der kleinsten Teilung, die bei Schachteln genau ist (sonst 4). */
    public static Umrechnung bauen(Gitter von, Gitter nach) {
        int k = nach.passtIn(von);
        return new Umrechnung(von, nach, k > 0 ? k : von.passtIn(nach) > 0 ? 1 : 4);
    }

    private Umrechnung(Gitter von, Gitter nach, int teilung) {
        this.von = von;
        this.nach = nach;
        int n = von.zellen(), t2 = teilung * teilung;
        mStart = new int[n + 1];
        int[] ziel = new int[Math.min(n, 1 << 20) * 2];
        double[] anteil = new double[ziel.length];
        int[] lokal = new int[t2];
        int z = 0;
        double draussen = 0;
        for (int j = 0; j < von.ny; j++)
            for (int i = 0; i < von.nx; i++) {
                int m = 0;
                for (int v = 0; v < teilung; v++)
                    for (int u = 0; u < teilung; u++) {
                        double[] p = nach.von(von, von.x0 + (i + (u + 0.5) / teilung) * von.dx, von.y0 + (j + (v + 0.5) / teilung) * von.dy);
                        int a = nach.i(p[0]), b = nach.j(p[1]);
                        if (a < 0 || b < 0 || a >= nach.nx || b >= nach.ny) continue;
                        lokal[m++] = nach.index(a, b);
                    }
                draussen += (t2 - m) / (double) t2;
                Arrays.sort(lokal, 0, m);
                mStart[von.index(i, j)] = z;
                for (int k = 0; k < m; ) {
                    int e = k;
                    while (e < m && lokal[e] == lokal[k]) e++;
                    if (z == ziel.length) {
                        ziel = Arrays.copyOf(ziel, z * 2);
                        anteil = Arrays.copyOf(anteil, z * 2);
                    }
                    ziel[z] = lokal[k];
                    anteil[z++] = (e - k) / (double) t2;
                    k = e;
                }
            }
        mStart[n] = z;
        mZiel = Arrays.copyOf(ziel, z);
        mAnteil = Arrays.copyOf(anteil, z);
        verlust = draussen / n;

        int nz = nach.zellen();
        zIndex = new int[4 * nz];
        zGewicht = new double[4 * nz];
        innen = new boolean[nz];
        for (int j = 0; j < nach.ny; j++)
            for (int i = 0; i < nach.nx; i++) {
                int c = nach.index(i, j);
                double[] p = von.von(nach, nach.x(i), nach.y(j));
                innen[c] = von.enthaelt(p[0], p[1]);
                if (!innen[c]) continue;
                double fx = (p[0] - von.x0) / von.dx - 0.5, fy = (p[1] - von.y0) / von.dy - 0.5;
                int a = (int) Math.floor(fx), b = (int) Math.floor(fy);
                double wx = fx - a, wy = fy - b;
                int a0 = Math.max(0, Math.min(von.nx - 1, a)), a1 = Math.max(0, Math.min(von.nx - 1, a + 1));
                int b0 = Math.max(0, Math.min(von.ny - 1, b)), b1 = Math.max(0, Math.min(von.ny - 1, b + 1));
                zIndex[4 * c] = von.index(a0, b0);
                zIndex[4 * c + 1] = von.index(a1, b0);
                zIndex[4 * c + 2] = von.index(a0, b1);
                zIndex[4 * c + 3] = von.index(a1, b1);
                zGewicht[4 * c] = (1 - wx) * (1 - wy);
                zGewicht[4 * c + 1] = wx * (1 - wy);
                zGewicht[4 * c + 2] = (1 - wx) * wy;
                zGewicht[4 * c + 3] = wx * wy;
            }
    }

    /** Überträgt Mengen je Zelle; die Summe bleibt erhalten. Das Ziel wird vorher auf null gesetzt. */
    public void menge(Feld quelle, Feld ziel) { verteile(quelle, ziel, false); }

    /** Überträgt Dichten; das Integral über die Fläche bleibt erhalten. Das Ziel wird vorher auf null gesetzt. */
    public void dichte(Feld quelle, Feld ziel) { verteile(quelle, ziel, true); }

    private void verteile(Feld quelle, Feld ziel, boolean dichte) {
        pruefe(quelle, ziel);
        int k = quelle.komponenten * quelle.schichten;
        ziel.fuellen(0);
        for (int j = 0; j < von.ny; j++) {
            double fq = dichte ? von.flaeche(j) : 1;
            for (int i = 0; i < von.nx; i++) {
                int c = von.index(i, j);
                for (int m = mStart[c]; m < mStart[c + 1]; m++) {
                    int t = mZiel[m];
                    double w = mAnteil[m] * fq / (dichte ? nach.flaeche(t / nach.nx) : 1);
                    for (int q = 0; q < k; q++) ziel.setz(t * k + q, ziel.hol(t * k + q) + w * quelle.hol(c * k + q));
                }
            }
        }
        ziel.zeit = quelle.zeit;
    }

    /** Überträgt Zustände bilinear; Zielzellen außerhalb der Quelle bleiben, wie sie sind. */
    public void zustand(Feld quelle, Feld ziel) {
        pruefe(quelle, ziel);
        int k = quelle.komponenten * quelle.schichten, n = nach.zellen();
        for (int c = 0; c < n; c++) {
            if (!innen[c]) continue;
            for (int q = 0; q < k; q++) {
                double s = 0;
                for (int e = 0; e < 4; e++) s += zGewicht[4 * c + e] * quelle.hol(zIndex[4 * c + e] * k + q);
                ziel.setz(c * k + q, s);
            }
        }
        ziel.zeit = quelle.zeit;
    }

    private void pruefe(Feld quelle, Feld ziel) {
        if (quelle.eintrag.ort() != Ort.ZELLE && quelle.eintrag.ort() != Ort.SAEULE || ziel.eintrag.ort() != quelle.eintrag.ort())
            throw new IllegalArgumentException("Umrechnung nur für Zellfelder: " + quelle.eintrag.name() + " → " + ziel.eintrag.name());
        if (!von.gleich(quelle.gitter) || !nach.gleich(ziel.gitter))
            throw new IllegalArgumentException("Umrechnung " + von.name + " → " + nach.name + " passt nicht zu " + quelle + " → " + ziel);
        if (quelle.komponenten != ziel.komponenten || quelle.schichten != ziel.schichten)
            throw new IllegalArgumentException("Umrechnung: Komponenten oder Schichten verschieden: " + quelle + " → " + ziel);
    }
}
