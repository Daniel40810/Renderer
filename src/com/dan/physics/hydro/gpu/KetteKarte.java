package com.dan.physics.hydro.gpu;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import com.dan.physics.hydro.Hydro;
import com.dan.physics.hydro.Kette;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Geschachtelte Gitter auf der Karte (Werkbuch HalligHooge Teil A, 12.5, Stand P4c): dasselbe Verfahren wie
 * {@link Kette}, je grobem Schritt ein Befehlspuffer. Die Lage prüft, die Ränder des feinen Gitters setzt und die
 * groben Kanten unter dem feinen sperrt eine {@code Kette} auf dem Prozessor; gerechnet wird mit den Kernels von
 * {@link HydroKarte} (Masse und Impuls getrennt), dazu {@code geist} (Rand des feinen aus den Zellen außen),
 * {@code aussengrenze} (seit P4d: Flüsse hinein ziehen aus den Zellen außen höchstens deren Volumen),
 * {@code aussen} (die Zellen außen nach jeder Masse des feinen), {@code flaeche} (fein: benetzte Fläche und
 * Durchfluss), {@code restriktion} (grob unter fein) und {@code grobu} (Fluss und u der gesperrten groben Kanten).
 * <p>
 * Abweichung vom Prozessor: Die Zahl der Unterschritte jeder Ebene steht am Anfang des groben Schritts fest (aus Δt nach
 * CFL der Ebene zu diesem Zeitpunkt); bei zwei Ebenen ist das dasselbe.
 */
public final class KetteKarte implements AutoCloseable {
    private static final int GRUPPE = 64;

    public final Kette kette;
    public final HydroKarte[] ebene;
    private final Geraet g;
    private final int n;
    // je Ebene k ≥ 1 (das Paar k − 1, k)
    private final Puffer[] werte, kopplung, gewicht, zelleO, liste, rein;
    private final int[] nAussen;
    private final Kernel kGeist, kAussen, kAussengrenze, kFlaeche, kRestriktion, kGrobu;
    /** Schritte je Ebene. */
    public final long[] schritte;
    private boolean eigenesGeraet;

    public KetteKarte(Geraet g, Konto konto, Hydro... h) {
        this.g = g;
        kette = new Kette(h);
        n = h.length;
        ebene = new HydroKarte[n];
        for (int k = 0; k < n; k++) ebene[k] = new HydroKarte(g, konto, h[k], k == 0 ? 2 : 1);
        werte = new Puffer[n];
        kopplung = new Puffer[n];
        gewicht = new Puffer[n];
        zelleO = new Puffer[n];
        liste = new Puffer[n];
        rein = new Puffer[n];
        nAussen = new int[n];
        schritte = new long[n];
        for (int k = 1; k < n; k++) {
            einrichten(k, konto);
            final int kk = k;
            ebene[k].nachBegrenzen = (ab, dt) -> aussenBegrenzen(ab, kk, dt);
        }
        Kernel.Bindung P = Kernel.Bindung.PUFFER;
        kGeist = Kernel.lade(g, HydroKarte.PFAD + "geist.comp", 0, null, P, P, P, P, P, P, P);
        kAussen = Kernel.lade(g, HydroKarte.PFAD + "aussen.comp", 8, null, bindungen(27));
        kAussengrenze = Kernel.lade(g, HydroKarte.PFAD + "aussengrenze.comp", 8, null, bindungen(28));
        kFlaeche = Kernel.lade(g, HydroKarte.PFAD + "flaeche.comp", 8, null, bindungen(20));
        kRestriktion = Kernel.lade(g, HydroKarte.PFAD + "restriktion.comp", 8, null, bindungen(23));
        kGrobu = Kernel.lade(g, HydroKarte.PFAD + "grobu.comp", 8, null, bindungen(22));
    }

    /** Eine Kette mit eigenem Gerät (für Prüfungen); schließt das Gerät mit. */
    public static KetteKarte mitGeraet(Hydro... h) {
        Geraet g = new Geraet();
        KetteKarte k = new KetteKarte(g, g.konto("hydro", 8L << 30), h);
        k.eigenesGeraet = true;
        return k;
    }

    private static Kernel.Bindung[] bindungen(int n) {
        Kernel.Bindung[] b = new Kernel.Bindung[n];
        Arrays.fill(b, Kernel.Bindung.PUFFER);
        return b;
    }

    private void einrichten(int k, Konto konto) {
        Hydro gr = ebene[k - 1].h, f = ebene[k].h;
        int[] l = kette.lage(k);
        int r = l[0], i0 = l[1], j0 = l[2], ni = l[3], nj = l[4], nRandF = ebene[k].nRand;
        int seiten = 0;
        for (int s = 0; s < 4; s++) if (f.rand[s] == Hydro.Rand.WASSERSTAND) seiten |= 1 << s;
        // Randkanten der Kette auf die Randindizes des feinen (West j, Ost j, Süd i, Nord i)
        int[] rk = ebene[k].randKanten(), index = new int[f.kanten];
        Arrays.fill(index, -1);
        for (int q = 0; q < nRandF; q++) index[rk[q]] = q;
        int[] kk = kette.randKanten(k), au = kette.aussen(k);
        double[] gw = kette.randGewichte(k);
        int[] kop = new int[4 * nRandF];
        float[] gew = new float[nRandF];
        List<List<Integer>> eintraege = new ArrayList<>();
        for (int o = 0; o < au.length; o++) eintraege.add(new ArrayList<>());
        for (int q = 0; q < kk.length / 4; q++) {
            int rr = index[kk[4 * q]];
            System.arraycopy(kk, 4 * q, kop, 4 * rr, 4);
            gew[rr] = (float) gw[q];
            eintraege.get(kk[4 * q + 2]).add(2 * rr);
            if (kk[4 * q + 3] >= 0) eintraege.get(kk[4 * q + 3]).add(2 * rr + 1);
        }
        int summe = 0;
        for (List<Integer> e : eintraege) summe += e.size();
        int[] li = new int[au.length + 1 + summe];
        int p = au.length + 1;
        for (int o = 0; o < au.length; o++) {
            li[o] = p - au.length - 1;
            for (int x : eintraege.get(o)) li[p++] = x;
        }
        li[au.length] = p - au.length - 1;
        nAussen[k] = au.length;
        int[] w = {gr.nx, gr.ny, f.nx, f.ny, r, i0, j0, ni, nj, nRandF, seiten, au.length};
        werte[k] = Puffer.speicher(g, konto, "Schachtel Werte " + k, 4L * w.length);
        werte[k].schreibe(w);
        kopplung[k] = Puffer.speicher(g, konto, "Schachtel Kopplung " + k, 4L * kop.length);
        kopplung[k].schreibe(kop);
        gewicht[k] = Puffer.speicher(g, konto, "Schachtel Gewicht " + k, 4L * gew.length);
        gewicht[k].schreibe(gew);
        zelleO[k] = Puffer.speicher(g, konto, "Schachtel Zellen außen " + k, 4L * Math.max(1, au.length));
        zelleO[k].schreibe(au.length > 0 ? au : new int[1]);
        liste[k] = Puffer.speicher(g, konto, "Schachtel Liste " + k, 4L * li.length);
        liste[k].schreibe(li);
        rein[k] = Puffer.speicher(g, konto, "Schachtel hinein " + k, 4L * Math.max(1, au.length));
    }

    // ------------------------------------------------------------------ Lauf

    /** Ein Schritt der gröbsten Ebene mit Δt (s), die feineren in Unterschritten; wartet, bis er fertig ist. */
    public void schritt(double dt) {
        double[] dtf = vorab;
        vorab = null;
        if (dtf == null) {
            dtf = new double[n];
            for (int k = 1; k < n; k++) dtf[k] = ebene[k].dtCfl();
        }
        HydroKarte g0 = ebene[0];
        g0.randFuellen(2, 0.5 * dt);                                  // halbe Schritte zu Δt / 4: Masse ab 0 und 2, Impuls 4
        for (HydroKarte e : ebene) if (e.antriebJeSchritt) e.antriebHoch();
        Ablauf ab = new Ablauf(g);
        masse(ab, 0, dt, dtf);
        g0.impulsAufnehmen(ab, dt, 2);
        g.rechnen.einmal(c -> ab.aufnehmen(c, null));
        g0.zeit += dt;
        schritte[0]++;
    }

    /** Läuft bis {@code bis}, Schritte der gröbsten Ebene nach CFL (höchstens dtMax), abgeglichen wie {@link Kette#abgleich}. Gibt die Zahl der Schritte zurück. */
    public int lauf(double bis, double dtMax) {
        int z = 0;
        while (ebene[0].zeit < bis - 1e-9) {
            schritt(dtKette(Math.min(dtMax, bis - ebene[0].zeit)));
            z++;
        }
        return z;
    }

    /** Schritte der feineren Ebenen aus {@link #dtKette}, für den nächsten {@link #schritt} (dann nicht noch einmal gerechnet). */
    private double[] vorab;

    /**
     * Schritt der gröbsten Ebene nach CFL, höchstens {@code hoechstens}, abgeglichen auf die feineren wie
     * {@link Kette#abgleich}; die Schritte der feineren gelten für den folgenden {@link #schritt}.
     */
    /** Die Grenzen des Schritts je Ebene aus dem letzten {@link #dtKette} (s; null vor dem ersten), für Prüfungen und die Wache in {@code KarteRechnung}. */
    public double[] dtEbenen() { return dtLetzt == null ? null : dtLetzt.clone(); }

    private double[] dtLetzt;

    public double dtKette(double hoechstens) {
        double[] dt = new double[n];
        long[] z = new long[n];
        for (int k = 0; k < n; k++) {
            dt[k] = ebene[k].dtCfl();
            z[k] = ebene[k].zellen;
        }
        vorab = dt.clone();
        vorab[0] = 0;
        dtLetzt = dt.clone();
        return Kette.abgleich(dt, z, hoechstens);
    }

    private Mittel[] alle() {
        List<Mittel> m = new ArrayList<>();
        for (HydroKarte e : ebene) m.addAll(Arrays.asList(e.alle()));
        for (Puffer[] p : new Puffer[][] {werte, kopplung, gewicht, zelleO, liste, rein})
            for (Puffer x : p) if (x != null) m.add(x);
        return m.toArray(new Mittel[0]);
    }

    private static ByteBuffer push(float dt, int m) { return ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()).putFloat(0, dt).putInt(4, m); }

    private static Object[] mit(Object[] a, Object... b) {
        Object[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    /** Masse der Ebene k über τ mit allem darunter (wie {@code Kette.masse}); Ränder der Ebene 0 ab halbem Schritt 0 und 2. */
    private void masse(Ablauf ab, int k, double tau, double[] dtf) {
        HydroKarte hk = ebene[k];
        if (k + 1 == n) {
            hk.masseAufnehmen(ab, tau, 0);
            aussen(ab, k, tau);
            return;
        }
        hk.masseAufnehmen(ab, 0.5 * tau, 0);
        aussen(ab, k, 0.5 * tau);
        geist(ab, k + 1);
        verlet(ab, k + 1, tau, dtf);
        hk.masseAufnehmen(ab, 0.5 * tau, k == 0 ? 2 : 0);
        aussen(ab, k, 0.5 * tau);
        geist(ab, k + 1);                                             // die Zellen außen haben sich geändert (P4d: sonst Durchfluss am Rand aus dem alten Stand)
        grobUnterFein(ab, k + 1);
    }

    /** Die Ebene k ≥ 1 über τ in m Unterschritten nach Verlet. */
    private void verlet(Ablauf ab, int k, double tau, double[] dtf) {
        HydroKarte f = ebene[k];
        int m = Math.max(1, (int) Math.ceil(tau / dtf[k] - 1e-9));
        double h = tau / m, t0 = f.zeit;
        f.impulsAufnehmen(ab, 0.5 * h, 0);
        for (int s = 0; s < m; s++) {
            masse(ab, k, h, dtf);
            f.impulsAufnehmen(ab, s == m - 1 ? 0.5 * h : h, 0);
            schritte[k]++;
        }
        f.zeit = t0 + tau;
    }

    /**
     * In einer Masse der Ebene k ≥ 1 nach dem Begrenzen: Flüsse über den Rand hinein ziehen aus den Zellen außen
     * höchstens deren Volumen (wie {@code Kette.aussenBegrenzen}, seit P4d).
     */
    private void aussenBegrenzen(Ablauf ab, int k, double tau) {
        HydroKarte g0 = ebene[k - 1], f = ebene[k];
        Mittel[] a = alle(), keine = new Mittel[0];
        Object[] b = mit(g0.belegung(g0.paritaet), werte[k], kopplung[k], gewicht[k], zelleO[k], liste[k], f.fluss, rein[k], f.belegung(f.paritaet)[5]);
        ab.rechne("aussen Anteil", kAussengrenze, kAussengrenze.satz(b), push((float) tau, 0), Kernel.gruppen(Math.max(1, nAussen[k]), GRUPPE), 1, 1, keine, a);
        ab.rechne("aussen Grenze", kAussengrenze, kAussengrenze.satz(b), push((float) tau, 1), Kernel.gruppen(Math.max(1, f.nRand), GRUPPE), 1, 1, keine, a);
    }

    /** Nach einer Masse der Ebene k ≥ 1: die Zellen außen der Ebene k − 1 nachziehen, dann der Rand der Ebene k neu. */
    private void aussen(Ablauf ab, int k, double tau) {
        if (k == 0) return;
        HydroKarte g0 = ebene[k - 1], f = ebene[k];
        Mittel[] a = alle(), keine = new Mittel[0];
        int gr = Kernel.gruppen(Math.max(1, nAussen[k]), GRUPPE);
        Object[] b = mit(g0.belegung(g0.paritaet), werte[k], kopplung[k], gewicht[k], zelleO[k], liste[k], f.fluss, rein[k]);
        ab.rechne("aussen Summe", kAussen, kAussen.satz(b), push((float) tau, 0), gr, 1, 1, keine, a);
        ab.rechne("aussen", kAussen, kAussen.satz(b), push((float) tau, 1), gr, 1, 1, keine, a);
        geist(ab, k);
    }

    /** Rand der Ebene k ≥ 1 aus den Zellen außen. */
    private void geist(Ablauf ab, int k) {
        HydroKarte g0 = ebene[k - 1], f = ebene[k];
        ab.rechne("geist", kGeist, kGeist.satz(werte[k], g0.eta, g0.vol, kopplung[k], gewicht[k], zelleO[k], f.rand), null,
                Kernel.gruppen(f.nRand, GRUPPE), 1, 1, new Mittel[0], alle());
    }

    /** Grob unter fein, Fluss und u der gesperrten groben Kanten. */
    private void grobUnterFein(Ablauf ab, int k) {
        HydroKarte g0 = ebene[k - 1], f = ebene[k];
        Mittel[] a = alle(), keine = new Mittel[0];
        int[] l = kette.lage(k);
        ab.rechne("flaeche", kFlaeche, kFlaeche.satz(f.belegung(f.paritaet)), push(0f, 2), Kernel.gruppen(Math.max(f.zellen, f.kanten), GRUPPE), 1, 1, keine, a);
        ab.rechne("restriktion", kRestriktion, kRestriktion.satz(mit(g0.belegung(g0.paritaet), werte[k], f.eta, f.faktor)), push(0f, 0),
                Kernel.gruppen(l[3] * l[4], GRUPPE), 1, 1, keine, a);
        int nk = (l[3] + 1) * l[4] + l[3] * (l[4] + 1);
        ab.rechne("grobu", kGrobu, kGrobu.satz(mit(g0.belegung(g0.paritaet), werte[k], f.q)), push(0f, 0), Kernel.gruppen(nk, GRUPPE), 1, 1, keine, a);
    }

    // ------------------------------------------------------------------ Bestand

    /** Holt den Stand aller Ebenen in ihre {@code Hydro}. */
    public void herunter() { for (HydroKarte e : ebene) e.herunter(); }

    /** Zellschritte aller Ebenen bisher: Zellen mal Schritte je Ebene. */
    public long zellschritte() {
        long z = 0;
        for (int k = 0; k < n; k++) z += (long) ebene[k].zellen * schritte[k];
        return z;
    }

    /** Volumen aller Ebenen (m³) nach {@link #herunter()}: je Ebene die Zellen, die nicht unter der nächsten liegen. */
    public double volumen() { return kette.volumen(); }

    /** Abgeschnittenes aller Ebenen (m³) nach {@link #herunter()}. */
    public double abgeschnitten() {
        double s = 0;
        for (HydroKarte e : ebene) s += e.h.abgeschnitten;
        return s;
    }

    @Override public void close() {
        g.ruhe();
        for (Kernel k : new Kernel[] {kGeist, kAussen, kAussengrenze, kFlaeche, kRestriktion, kGrobu}) k.close();
        for (Puffer[] p : new Puffer[][] {werte, kopplung, gewicht, zelleO, liste, rein})
            for (Puffer x : p) if (x != null) x.close();
        for (HydroKarte e : ebene) e.close();
        if (eigenesGeraet) g.close();
    }
}
