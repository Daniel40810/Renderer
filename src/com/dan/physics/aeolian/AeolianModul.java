package com.dan.physics.aeolian;

import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Kornfeld;

/**
 * aeolian als Modul am Bus (Werkbuch HalligHooge Teil A, 7.6 und 12.11, P10c): {@link Aeolian} auf einem Gitter von
 * hydro (G3); den Wind liest es auf den Säulen der Luft ({@link #teiler}-mal gröber, wie atmos).
 * <ul>
 * <li><b>Liest</b> im Intervall, also zum Ende seines Schritts: {@code surf.wet}, {@code water.h}, {@code water.wetAge}
 * auf dem Gitter der Zellen; {@code air.u10}, {@code air.v10} auf dem Gitter der Säulen.</li>
 * <li><b>Schreibt</b> {@code bed.dz.aeolian} (m, Summe seit dem Beginn; der Kern bildet daraus {@code bed.dz}) und
 * {@code aeol.q} (kg/(m s), nach Ost und nach Nord), beides in float.</li>
 * <li><b>Wasser:</b> wie atmos – eine Zelle trägt Wasser, wenn hydro sie nass nennt ({@code water.wetAge} ≥ 0) und
 * {@code water.h} wenigstens {@link Aeolian#hNass} ist.</li>
 * <li><b>Boden</b> aus dem {@link Kornfeld} des Gebiets: Korn, Porenanteil, Schlick, fester Boden; ohne Kornfeld
 * überall loser Sand von 0,2 mm.</li>
 * <li><b>Beginn:</b> Höhe 0. Vor {@link #ab} rechnet das Modul nicht (Vorlauf von atmos).</li>
 * </ul>
 * Kennt den Kern und kein anderes Modul (Schichtenvertrag, Werkbuch 4).
 */
public final class AeolianModul implements Modul {
    public static final String VERSION = "0.1";

    public final Gitter gitter, saeulen;
    public final int teiler;
    public final Aeolian aeolian;
    /** Kopplungsintervall (s) [G]. */
    public double kopplung = 300;
    /** Gerechnet wird ab dieser Modellzeit (s); davor bleibt die Höhe 0. */
    public double ab = Double.NEGATIVE_INFINITY;
    /** Schritte seit dem Beginn, Rechenzeit von {@link Aeolian#schritt} (s). */
    public long schritte;
    public double sekunden;

    private Feld fWet, fH, fAlter, fU, fV, fVeg;
    /** Seit P13b (F153 A): {@code veg.cover} lesen ({@link Aeolian#deckung}). */
    public boolean vegetation;

    /**
     * @param gitter Gitter der Zellen (UTM); nx und ny teilbar durch {@code teiler}
     * @param teiler Zellen je Säule in jeder Richtung
     * @param korn   der Boden des Gebiets; null: überall loser Sand
     */
    public AeolianModul(Gitter gitter, int teiler, Kornfeld korn) {
        if (gitter.lage != Gitter.Lage.UTM || gitter.nx % teiler != 0 || gitter.ny % teiler != 0)
            throw new IllegalArgumentException("aeolian: Gitter in UTM mit nx, ny teilbar durch " + teiler + ", nicht " + gitter);
        this.gitter = gitter;
        this.teiler = teiler;
        saeulen = Gitter.utm(gitter.name + "s", gitter.zone, gitter.x0, gitter.y0, gitter.dx * teiler, gitter.nx / teiler, gitter.ny / teiler);
        aeolian = new Aeolian(gitter.nx, gitter.ny, gitter.dx, gitter.dy);
        if (korn != null) {
            double[] d = korn.auf(gitter, Kornfeld.D50), n = korn.auf(gitter, Kornfeld.POROSITAET), m = korn.auf(gitter, Kornfeld.SCHLICK), f = korn.auf(gitter, Kornfeld.FEST);
            for (int c = 0; c < aeolian.nc; c++) {
                aeolian.d50[c] = d[c] > 0 ? d[c] : 2e-4;
                aeolian.poros[c] = n[c] > 0 && n[c] < 1 ? n[c] : 0.4;
                aeolian.schlick[c] = m[c];
                aeolian.fest[c] = f[c];
            }
        }
    }

    @Override public String name() { return "aeolian"; }

    @Override public String version() { return VERSION + " (Aeolian " + Aeolian.VERSION + ")"; }

    @Override public void anmelden(Anmeldung a) {
        a.kopplung(kopplung);
        a.liestImIntervall("surf.wet", "–", Ort.ZELLE, gitter).liestImIntervall("water.h", "m", Ort.ZELLE, gitter).liestImIntervall("water.wetAge", "s", Ort.ZELLE, gitter);
        a.liestImIntervall("air.u10", "m/s", Ort.ZELLE, saeulen).liestImIntervall("air.v10", "m/s", Ort.ZELLE, saeulen);
        if (vegetation) a.liest("veg.cover", "–", Ort.ZELLE, gitter);
        a.schreibt("bed.dz.aeolian", "m", Ort.ZELLE, gitter, Feld.Zahl.FLOAT).schreibt("aeol.q", "kg/(m s)", Ort.ZELLE, gitter, Feld.Zahl.FLOAT);
    }

    private void eingang(Bus bus, double t) {
        if (fWet == null) {
            fWet = bus.lesefeld(this, "surf.wet", gitter); fH = bus.lesefeld(this, "water.h", gitter); fAlter = bus.lesefeld(this, "water.wetAge", gitter);
            fU = bus.lesefeld(this, "air.u10", saeulen); fV = bus.lesefeld(this, "air.v10", saeulen);
        }
        for (Feld f : new Feld[] {fWet, fH, fAlter, fU, fV}) bus.lies(this, f.eintrag.name(), t, f);
        Aeolian ae = aeolian;
        if (vegetation) {
            if (fVeg == null) fVeg = bus.lesefeld(this, "veg.cover", gitter);
            bus.lies(this, "veg.cover", t, fVeg);
            if (ae.deckung == null) ae.deckung = new double[ae.nc];
            for (int c = 0; c < ae.nc; c++) { double d = fVeg.hol(c); ae.deckung[c] = Double.isFinite(d) ? Math.max(0, Math.min(1, d)) : 0; }
        }
        for (int j = 0; j < ae.ny; j++)
            for (int i = 0; i < ae.nx; i++) {
                int c = i + ae.nx * j, s = i / teiler + saeulen.nx * (j / teiler);
                double h = fAlter.hol(c) >= 0 ? fH.hol(c) : 0;
                ae.h[c] = h >= ae.hNass ? h : 0;
                ae.wet[c] = fWet.hol(c);
                ae.u[c] = fU.hol(s);
                ae.v[c] = fV.hol(s);
            }
    }

    @Override public void beginn(double t0, Bus bus) {
        aeolian.anfang();
        schritte = 0;
        sekunden = 0;
        lege(bus, t0);
    }

    @Override public void schritt(double t0, double t1, Bus bus) {
        if (t0 >= ab - 1e-9 * Math.max(1, Math.abs(ab))) {
            eingang(bus, t1);
            long n0 = System.nanoTime();
            aeolian.schritt(t1 - t0);
            sekunden += (System.nanoTime() - n0) * 1e-9;
            schritte++;
        }
        lege(bus, t1);
    }

    private void lege(Bus bus, double t) {
        Aeolian ae = aeolian;
        Feld z = bus.schreibfeld(this, "bed.dz.aeolian"), q = bus.schreibfeld(this, "aeol.q");
        for (int c = 0; c < ae.nc; c++) {
            z.f[c] = (float) ae.dz[c];
            q.f[2 * c] = (float) ae.qx[c];
            q.f[2 * c + 1] = (float) ae.qy[c];
        }
        bus.lege(this, "bed.dz.aeolian", t);
        bus.lege(this, "aeol.q", t);
    }
}
