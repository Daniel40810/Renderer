package com.dan.physics.tide;

import com.dan.core.astro.Gestirne;
import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Tidenfeld;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Der Antrieb der Gezeiten als Modul am Bus (Werkbuch HalligHooge Teil A, 7.2 und 12.6, P5c; F27 A): hydro rechnet
 * alle Gitter, tide liefert, was die Gezeiten antreibt.
 * <ul>
 * <li><b>{@code tide.bc}</b> am Rand von G0 (Ort {@link Ort#RAND}, Folge nach {@link Gitter#randZelle}): Wasserstand
 * {@code mittel + r(t) · Σ f A cos(V + u − G) − (p − p₀) / (ρ g)} an der Mitte der Geisterzelle, aus den Partialtiden
 * von EOT20 ({@link Tidenfeld}, bilinear über nasse Punkte, sonst der nächste im {@link #umkreis}) mit Knotenfaktoren,
 * dazu das umgekehrte Barometer aus {@code air.p} (in der Randzelle, eine halbe Zelle innen), wenn der Antrieb läuft
 * ({@link #druck}). Durchfluss 0. An einer Randzelle ohne Wasser (Land nach dem Meeranteil von G0) NaN: Dort ist kein
 * offener Rand.</li>
 * <li><b>{@code tide.eq}</b> auf G0 und G1 (Zellmitte): {@code faktor · r(t) · (η_eq(t) − η̄_eq(φ))}, η_eq aus Sonne
 * und Mond ({@link Gestirne.Stand#gleichgewicht}, Grad 2 und 3), mal (1 + k₂ − h₂) ≈ 0,69. η̄_eq ist das Mittel über
 * ein Jahr ab dem Start (stündlich, je Breite): der ständige Anteil. Er ist im Rand (EOT20) nicht enthalten und gäbe
 * im Inneren eine feste Neigung von rund 7 cm über G0; mit ihm fällt auch die 18,6-jährige Tide weg, die EOT20 ebenso
 * wenig führt.</li>
 * <li><b>Anlauf:</b> r(t) = ½ (1 − cos(π t / Anlauf)) [G]; am Anfang steht ein ruhender See bei {@link #mittel}. Das
 * Barometer fährt mit dem Antrieb an ({@code physics.antrieb}), nicht hier.</li>
 * </ul>
 * Kennt den Kern und kein anderes Modul (Schichtenvertrag, Werkbuch 4).
 */
public final class Tide implements Modul {
    public static final String VERSION = "0.1";

    public final Tidenfeld eot;
    /** Szenenzeit 0 (UTC). */
    public final Instant start;
    /** Das Gitter mit dem offenen Rand (G0). */
    public final Gitter rand;
    /** Die Gitter mit der Gleichgewichtstide (G0, G1). */
    public final List<Gitter> eq;
    /** Kopplungsintervall (s) [G]; hydro liest im Intervall linear dazwischen. */
    public double kopplung = 300;
    /** Anlauf (s) [G]. */
    public double anlauf = 3 * 3600;
    /** Mittelwasser am offenen Rand (m NHN) [G]; P5d misst es an den Pegeln. */
    public double mittel = 0;
    /** Faktor der festen Erde (1 + k₂ − h₂). */
    public double faktor = Gestirne.LOVE;
    /** Dichte des Wassers (kg/m³), Schwere (m/s²) und Bezugsdruck (Pa) für das umgekehrte Barometer. */
    public double rhoW = 1025, g = 9.81, p0 = 101_325;
    /** Punkte von EOT20, in denen der nächste nasse gilt, wo die vier umgebenden Land sind [G] (wie {@code eot20.umkreis}). */
    public final int umkreis;
    /** Liest {@code air.p} für das umgekehrte Barometer am Rand (nur, wenn der Antrieb im Lauf ist). */
    public final boolean druck;

    /** Je Randzelle die Konstanten {re, im} je Tide; null an einer Randzelle ohne Wasser oder ohne Werte. */
    private final double[][][] konst;
    /** Zahl der offenen Randzellen, davon mit Werten bilinear, vom nächsten Punkt, ohne. */
    public final int offen, bilinear, naechster, ohne;
    /** Je Gitter mit Gleichgewichtstide: Länge und Breite der Zellmitten (Grad). */
    private final double[][] lon, lat;
    /** Der ständige Anteil η̄_eq über der Breite: ab {@link #breite0} in Schritten von {@link #dBreite} (m). */
    private final double[] staendig;
    private final double breite0, dBreite = 0.1;
    private Feld p;

    /**
     * @param eot     EOT20 ausgeschnitten ({@code tide/eot20.txt})
     * @param start   Szenenzeit 0
     * @param rand    G0
     * @param nass    Meeranteil je Zelle von G0 ({@code nass.f32}); eine Randzelle ohne Meer ist kein offener Rand
     * @param umkreis Punkte von EOT20 für den nächsten nassen
     * @param druck   umgekehrtes Barometer aus {@code air.p}
     * @param eq      Gitter mit Gleichgewichtstide
     */
    public Tide(Tidenfeld eot, Instant start, Gitter rand, float[] nass, int umkreis, boolean druck, Gitter... eq) {
        this.eot = eot;
        this.start = start;
        this.rand = rand;
        this.umkreis = umkreis;
        this.druck = druck;
        this.eq = List.of(eq);
        int n = rand.randZellen(), o = 0, b = 0, q = 0, x = 0;
        konst = new double[n][][];
        for (int r = 0; r < n; r++) {
            int[] z = rand.randZelle(r);
            if (!(nass[rand.index(z[0], z[1])] > 0)) continue;
            o++;
            double[] p = rand.geist(r), ll = rand.grad(p[0], p[1]);
            var w = eot.gewichte(ll[0], ll[1], umkreis);
            if (w == null) { x++; continue; }
            if (w.naechster()) q++;
            else b++;
            konst[r] = eot.konstanten(ll[0], ll[1], umkreis);
        }
        offen = o;
        bilinear = b;
        naechster = q;
        ohne = x;
        // Länge und Breite der Zellmitten
        lon = new double[eq.length][];
        lat = new double[eq.length][];
        double bMin = 90, bMax = -90;
        for (int k = 0; k < eq.length; k++) {
            Gitter gi = eq[k];
            lon[k] = new double[gi.zellen()];
            lat[k] = new double[gi.zellen()];
            final int kk = k;
            IntStream.range(0, gi.ny).parallel().forEach(j -> {
                for (int i = 0; i < gi.nx; i++) {
                    double[] ll = gi.grad(gi.x(i), gi.y(j));
                    lon[kk][gi.index(i, j)] = ll[0];
                    lat[kk][gi.index(i, j)] = ll[1];
                }
            });
            for (double v : lat[k]) {
                bMin = Math.min(bMin, v);
                bMax = Math.max(bMax, v);
            }
        }
        breite0 = Math.floor(bMin / dBreite) * dBreite - dBreite;
        int nb = eq.length == 0 ? 0 : (int) Math.ceil((bMax - breite0) / dBreite) + 2;
        staendig = staendig(start, breite0, dBreite, nb);
    }

    /**
     * Der ständige Anteil der Gleichgewichtstide je Breite: Mittel von η_eq über 365 Tage ab {@code start}, stündlich,
     * an den Längen 0°, 90°, 180°, 270° (ohne den Faktor der festen Erde).
     */
    static double[] staendig(Instant start, double breite0, double dBreite, int n) {
        double[] s = new double[n];
        if (n == 0) return s;
        int stunden = 365 * 24;
        double[] teil = IntStream.range(0, stunden).parallel().mapToObj(h -> {
            Gestirne.Stand st = Gestirne.bei(start.plusSeconds(3600L * h));
            double[] r = new double[n];
            for (int k = 0; k < n; k++)
                for (int l = 0; l < 4; l++) r[k] += st.gleichgewicht(90.0 * l, breite0 + k * dBreite) / 4;
            return r;
        }).reduce((a, b) -> {
            double[] c = new double[n];
            for (int k = 0; k < n; k++) c[k] = a[k] + b[k];
            return c;
        }).orElse(new double[n]);
        for (int k = 0; k < n; k++) s[k] = teil[k] / stunden;
        return s;
    }

    /** Der ständige Anteil η̄_eq (m, ohne Faktor) an einer Breite, linear zwischen den Stützstellen. */
    public double staendig(double breite) {
        double f = (breite - breite0) / dBreite;
        int k = Math.max(0, Math.min(staendig.length - 2, (int) Math.floor(f)));
        double w = f - k;
        return (1 - w) * staendig[k] + w * staendig[k + 1];
    }

    /** Anteil zur Zeit t beim Anlauf. */
    public static double anteil(double t, double anlauf) { return anlauf <= 0 || t >= anlauf ? 1 : t <= 0 ? 0 : 0.5 * (1 - Math.cos(Math.PI * t / anlauf)); }

    /** Der Zeitpunkt zur Modellzeit t (s). */
    public Instant zeit(double t) {
        long s = (long) Math.floor(t);
        return start.plusSeconds(s).plusNanos(Math.round((t - s) * 1e9));
    }

    /** Die Konstanten der Randzelle r ({re, im} je Tide) oder null. */
    public double[][] konstanten(int r) { return konst[r]; }

    @Override public String name() { return "tide"; }

    @Override public String version() { return VERSION; }

    @Override public void anmelden(Anmeldung a) {
        a.kopplung(kopplung).schreibt("tide.bc", "m, m²/s", Ort.RAND, rand, Feld.Zahl.DOUBLE);
        for (Gitter gi : eq) a.schreibt("tide.eq", "m", Ort.ZELLE, gi, Feld.Zahl.DOUBLE);
        if (druck) a.liestImIntervall("air.p", "Pa", Ort.ZELLE, rand);
    }

    @Override public void beginn(double t0, Bus bus) {
        if (druck) p = bus.lesefeld(this, "air.p");
        lege(bus, t0);
    }

    @Override public void schritt(double t0, double t1, Bus bus) { lege(bus, t1); }

    private void lege(Bus bus, double t) {
        double r = anteil(t, anlauf);
        Instant z = zeit(t);
        // Rand von G0
        double[][] fk = eot.faktoren(z);
        if (druck) bus.lies(this, "air.p", t, p);
        Feld bc = bus.schreibfeld(this, "tide.bc");
        for (int q = 0; q < konst.length; q++) {
            double eta = Double.NaN;
            if (konst[q] != null) {
                eta = mittel + r * Tidenfeld.eta(konst[q], fk);
                if (druck) {
                    int[] c = rand.randZelle(q);
                    eta -= (p.hol(rand.index(c[0], c[1])) - p0) / (rhoW * g);
                }
            }
            bc.d[2 * q] = eta;
            bc.d[2 * q + 1] = 0;
        }
        bus.lege(this, "tide.bc", t);
        // Gleichgewichtstide
        if (eq.isEmpty()) return;
        Gestirne.Stand st = Gestirne.bei(z);
        for (int k = 0; k < eq.size(); k++) {
            Gitter gi = eq.get(k);
            Feld f = bus.schreibfeld(this, "tide.eq", gi);
            double[] lo = lon[k], la = lat[k];
            double fr = faktor * r;
            IntStream.range(0, gi.zellen()).parallel().forEach(c -> f.d[c] = fr * (st.gleichgewicht(lo[c], la[c]) - staendig(la[c])));
            bus.lege(this, "tide.eq", gi, t);
        }
    }

    /** Die Gleichgewichtstide (m), wie sie tide schreibt, an einem Ort zur Modellzeit t (für Prüfungen). */
    public double gleichgewicht(double t, double lonGrad, double latGrad) {
        return faktor * anteil(t, anlauf) * (Gestirne.bei(zeit(t)).gleichgewicht(lonGrad, latGrad) - staendig(latGrad));
    }

    /** Länge und Breite der Zellmitten des k-ten Gitters mit Gleichgewichtstide (nicht kopiert). */
    public double[][] orte(int k) { return new double[][] {lon[k], lat[k]}; }

    /** Für das Protokoll: wie viele Randzellen offen sind und woher ihre Werte kommen. */
    @Override public String toString() {
        List<String> g = new ArrayList<>();
        for (Gitter x : eq) g.add(x.name);
        return String.format(java.util.Locale.ROOT, "tide %s: Rand %s mit %d offenen Randzellen (%d bilinear, %d vom nächsten Punkt, %d ohne Werte), Gleichgewichtstide auf %s, Anlauf %.1f h, Mittel %+.3f m%s",
                VERSION, rand.name, offen, bilinear, naechster, ohne, g, anlauf / 3600, mittel, druck ? ", umgekehrtes Barometer" : "");
    }

}
