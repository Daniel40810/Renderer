package com.dan.physics.hydro.gpu;

import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.physics.hydro.Einseitig;
import com.dan.physics.hydro.Hydro;
import com.dan.physics.hydro.Rechnung;

import java.util.List;

/**
 * Der Betrieb von {@link com.dan.physics.hydro.HydroModul} auf der Karte (Werkbuch HalligHooge Teil A, 12.6, P5c): je
 * Stufe eine {@link HydroKarte} (ein Gitter) oder eine {@link KetteKarte} (eine Kette). Der Antrieb geht einmal je
 * Kopplungsintervall auf die Karte ({@link #antrieb}), die Summen der Flüsse sammelt der Kernel {@code sammeln}.
 * Ein Gitter rechnet in gleichen Schritten nach der Grenze am Anfang des Intervalls, je Abschicken bis zu
 * {@link #ABSCHNITT} Schritte; eine Kette je Schritt der gröbsten Ebene nach {@link KetteKarte#dtKette}, der Rest auf
 * gleiche Schritte verteilt (wie der Prozessor).
 */
public final class KarteRechnung implements Rechnung {
    /** Schritte je Abschicken bei einem einzelnen Gitter (Größe des Randpuffers). */
    public static final int ABSCHNITT = 64;

    private final Geraet g;
    private final Konto konto;
    private final boolean eigenesGeraet;
    private List<Hydro[]> stufen;
    private HydroKarte[] einzeln;
    private KetteKarte[] ketten;
    private long zellschritte;

    /** Auf einem Gerät mit eigenem Konto. */
    public KarteRechnung(Geraet g, Konto konto) {
        this.g = g;
        this.konto = konto;
        eigenesGeraet = false;
    }

    /** Mit eigenem Gerät (schließt es mit). */
    public KarteRechnung() {
        g = new Geraet();
        konto = g.konto("hydro", 12L << 30);
        eigenesGeraet = true;
    }

    @Override public void einrichten(List<Hydro[]> stufen) {
        this.stufen = stufen;
        einzeln = new HydroKarte[stufen.size()];
        ketten = new KetteKarte[stufen.size()];
        for (int s = 0; s < stufen.size(); s++) {
            Hydro[] st = stufen.get(s);
            if (st.length == 1) {
                einzeln[s] = new HydroKarte(g, konto, st[0], ABSCHNITT);
                einzeln[s].antriebJeSchritt = false;
                einzeln[s].qSammeln();
            } else {
                ketten[s] = new KetteKarte(g, konto, st);
                for (HydroKarte e : ketten[s].ebene) {
                    e.antriebJeSchritt = false;
                    e.qSammeln();
                }
            }
        }
    }

    @Override public void bis(int s, double t1) {
        if (einzeln[s] != null) {
            HydroKarte k = einzeln[s];
            double rest = t1 - k.zeit;
            if (rest <= 1e-9) return;
            double dc = k.dtCfl();
            if (!(dc >= DT_MIN)) throw new IllegalStateException(String.format(java.util.Locale.ROOT, "hydro: der Schritt auf %s ist auf %.3g s gefallen, Zeit %.0f s", k.h.gitter.name, dc, k.zeit));
            int n = (int) Math.ceil(rest / dc - 1e-9);
            double dt = rest / Math.max(1, n);
            for (int m = 0; m < n; ) {
                int z = Math.min(ABSCHNITT, n - m);
                k.schritte(z, dt);
                m += z;
            }
            k.zeit = t1;
            zellschritte += (long) n * k.zellen;
            return;
        }
        KetteKarte k = ketten[s];
        long z0 = k.zellschritte();
        while (k.ebene[0].zeit < t1 - 1e-9) {
            double rest = t1 - k.ebene[0].zeit, d = k.dtKette(rest);
            double[] de = k.dtEbenen();
            for (int i = 0; i < de.length; i++) if (!(de[i] >= DT_MIN)) throw new IllegalStateException(instabil(k, i, de));
            int n = (int) Math.ceil(rest / d - 1e-9);
            k.schritt(n <= 1 ? rest : rest / n);
        }
        for (HydroKarte e : k.ebene) e.zeit = t1;
        zellschritte += k.zellschritte() - z0;
    }

    /** Unter dieser Grenze des Schritts (s) gilt ein Gitter der Kette als instabil (seit P9c): Der Lauf bricht mit einer Auskunft ab, statt Schritte ohne Ende zu rechnen. */
    public static final double DT_MIN = 0.02;

    /** Auskunft zum instabilen Gitter i der Kette: die Kante mit der größten Geschwindigkeit und ihre beiden Zellen. */
    private static String instabil(KetteKarte k, int i, double[] de) {
        k.herunter();
        com.dan.physics.hydro.Hydro h = k.ebene[i].h;
        int e = 0;
        for (int q = 0; q < h.kanten; q++) if (!(Math.abs(h.u[q]) <= Math.abs(h.u[e]))) e = q;
        int a = h.links(e), b = h.rechts(e);
        StringBuilder t = new StringBuilder(String.format(java.util.Locale.ROOT, "hydro: der Schritt auf %s ist auf %.3g s gefallen (Grenzen je Ebene %s), Zeit %.0f s. Größte Geschwindigkeit %.3g m/s an Kante %d",
                h.gitter.name, de[i], java.util.Arrays.toString(de), h.zeit, h.u[e], e));
        for (int c : new int[] {a, b})
            if (c >= 0) t.append(String.format(java.util.Locale.ROOT, "; Zelle %d, %d: η = %.3f m, Grund %.3f m, Volumen %.3g m³, Bodenänderung %+.3f m", c % h.nx, c / h.nx, h.eta[c], h.grund(c), h.vol[c], h.bodenDz(c)));
        return t.toString();
    }

    @Override public void herunter(int s) {
        if (einzeln[s] != null) einzeln[s].herunter();
        else ketten[s].herunter();
    }

    @Override public Einseitig.Stand stand(int s) { return einzeln[s] != null ? einzeln[s].standKarte() : ketten[s].ebene[0].standKarte(); }

    @Override public void antrieb(int s) {
        if (einzeln[s] != null) einzeln[s].antriebHoch();
        else for (HydroKarte e : ketten[s].ebene) e.antriebHoch();
    }

    @Override public void boden(int s) {
        if (einzeln[s] != null) einzeln[s].bodenHoch();
        else for (HydroKarte e : ketten[s].ebene) e.bodenHoch();
    }

    @Override public void summenNull(int s) {
        if (einzeln[s] != null) einzeln[s].summenNull();
        else for (HydroKarte e : ketten[s].ebene) e.summenNull();
    }

    @Override public double volumen(int s) { return einzeln[s] != null ? einzeln[s].h.volumen() : ketten[s].volumen(); }

    @Override public long zellschritte() { return zellschritte; }

    @Override public String geraet() { return g.name + " (" + g.treiber + ")"; }

    /** Die Karte der Stufe s, wenn sie ein einzelnes Gitter ist (für Prüfungen). */
    public HydroKarte einzeln(int s) { return einzeln[s]; }

    /** Die Kette der Stufe s, wenn sie eine ist (für Prüfungen). */
    public KetteKarte kette(int s) { return ketten[s]; }

    @Override public void close() {
        if (einzeln != null) for (HydroKarte k : einzeln) if (k != null) k.close();
        if (ketten != null) for (KetteKarte k : ketten) if (k != null) k.close();
        if (eigenesGeraet) g.close();
    }
}
