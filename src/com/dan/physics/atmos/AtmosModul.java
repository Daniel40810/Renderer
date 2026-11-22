package com.dan.physics.atmos;

import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;

/**
 * atmos als Modul am Bus (Werkbuch HalligHooge Teil A, 7.5 und 12.11, P10b): {@link Atmos} auf einem Gitter von hydro
 * (G3) mit den Säulen der Luft auf einem zweiten Gitter, {@link #teiler}-mal gröber (G3s, 200 m).
 * <ul>
 * <li><b>Liest</b> im Intervall, also zum Ende seines Schritts: {@code water.h}, {@code water.wetAge}, {@code water.eta}
 * auf dem Gitter der Zellen; {@code air.u10}, {@code air.v10}, {@code air.p}, {@code air.t2}, {@code air.q2},
 * {@code air.sw}, {@code air.lw}, {@code water.sst} auf dem Gitter der Säulen (der Bus rechnet vom Raster von ERA5
 * bilinear um); seit 0.3 mit {@link #mitRegen} dazu {@code air.rain}, soweit er {@link #regenAb} erreicht.</li>
 * <li><b>Schreibt</b> {@code surf.wet} und {@code surf.T} auf dem Gitter der Zellen, {@code air.n} (n − 1 in
 * {@link Profil#STUFEN} Höhen) auf dem Gitter der Säulen, alles in float.</li>
 * <li><b>Wasser:</b> Eine Zelle trägt Wasser, wenn hydro sie nass nennt ({@code water.wetAge} ≥ 0) und
 * {@code water.h} wenigstens {@link Atmos#hNass} ist. Teilnasse Zellen zählen damit ganz als Wasser [G].</li>
 * <li><b>Grundwasser</b> (F92 A): Der Wasserstand nebenan ist das Mittel von {@code water.eta} über die Zellen mit
 * tiefem Wasser im Gitter [G]; die Höhe der Zelle darüber kommt aus {@link #grund} (Zellmittel, m NHN). Wie lange
 * eine Zelle beim Beginn schon trocken liegt, sagt {@code water.wetAge}; daraus folgt das Grundwasser am Anfang.</li>
 * <li><b>Beginn:</b> Boden und Oberfläche mit der Temperatur der Luft in 2 m, Wasser mit der des Meers; trockene
 * Zellen mit der Nässe ihres Gleichgewichts. Der Lauf rechnet einen Tag vor ({@code lauf.Nachlauf}).</li>
 * </ul>
 * Kennt den Kern und kein anderes Modul (Schichtenvertrag, Werkbuch 4).
 */
public final class AtmosModul implements Modul {
    public static final String VERSION = "0.3";

    public final Gitter gitter, saeulen;
    public final int teiler;
    public final Atmos atmos;
    /** Kopplungsintervall (s) [G]. */
    public double kopplung = 300;
    /** Zellmittel des Geländes (m NHN) auf dem Gitter der Zellen; null: Das Grundwasser sinkt ohne Grenze. */
    public float[] grund;
    /** Wasserstand nebenan zum letzten Schritt (m NHN); NaN, solange keine Zelle tiefes Wasser trägt. */
    public double pegel = Double.NaN;
    /** Schritte seit dem Beginn. */
    public long schritte;
    /** Rechenzeit von {@link Atmos#schritt} (s). */
    public double sekunden;

    /** Seit 0.3 (P10c, F97 A), vor dem Anmelden: den Regen lesen ({@code air.rain}); ohne ihn rechnet atmos wie 0.2. */
    public boolean mitRegen = true;
    /** Regen unter dieser Rate (kg/(m² s); 0,1 mm je Stunde [G]) zählt nicht: Spuren, die die Reanalyse an vielen Stunden meldet, hielten den Sand sonst dauernd feucht. */
    public double regenAb = 0.1 / 3600;
    /** Regen seit dem Beginn im Mittel der Säulen (kg/m² = mm). */
    public double regenSumme;

    private Feld fH, fAlter, fEta, fU, fV, fP, fT, fQ, fSw, fLw, fSst, fRegen;

    /**
     * @param gitter Gitter der Zellen (UTM); nx und ny teilbar durch {@code teiler}
     * @param teiler Zellen je Säule in jeder Richtung
     */
    /**
     * Seit P13b (F153 A, A9): {@code surf.z0} lesen; wo es positiv ist, gilt es als Rauheit des Landes ({@code Atmos.z0}),
     * sonst bleibt 1 mm (Sand). Die Salzwiese rechnet so mit Gras statt mit Sand.
     */
    public boolean rauheit;
    private Feld fZ0;

    public AtmosModul(Gitter gitter, int teiler) {
        if (gitter.lage != Gitter.Lage.UTM || gitter.nx % teiler != 0 || gitter.ny % teiler != 0)
            throw new IllegalArgumentException("atmos: Gitter in UTM mit nx, ny teilbar durch " + teiler + ", nicht " + gitter);
        this.gitter = gitter;
        this.teiler = teiler;
        saeulen = Gitter.utm(gitter.name + "s", gitter.zone, gitter.x0, gitter.y0, gitter.dx * teiler, gitter.nx / teiler, gitter.ny / teiler);
        atmos = new Atmos(gitter.nx, gitter.ny, teiler);
        atmos.mitEntwaesserung = true;
    }

    @Override public String name() { return "atmos"; }

    @Override public String version() { return VERSION + " (Atmos " + Atmos.VERSION + ")"; }

    @Override public void anmelden(Anmeldung a) {
        a.kopplung(kopplung);
        a.liestImIntervall("water.h", "m", Ort.ZELLE, gitter).liestImIntervall("water.wetAge", "s", Ort.ZELLE, gitter).liestImIntervall("water.eta", "m NHN", Ort.ZELLE, gitter);
        a.liestImIntervall("air.u10", "m/s", Ort.ZELLE, saeulen).liestImIntervall("air.v10", "m/s", Ort.ZELLE, saeulen).liestImIntervall("air.p", "Pa", Ort.ZELLE, saeulen);
        a.liestImIntervall("air.t2", "K", Ort.ZELLE, saeulen).liestImIntervall("air.q2", "kg/kg", Ort.ZELLE, saeulen);
        a.liestImIntervall("air.sw", "W/m²", Ort.ZELLE, saeulen).liestImIntervall("air.lw", "W/m²", Ort.ZELLE, saeulen).liestImIntervall("water.sst", "K", Ort.ZELLE, saeulen);
        if (mitRegen) a.liestImIntervall("air.rain", "kg/(m² s)", Ort.ZELLE, saeulen);
        if (rauheit) a.liest("surf.z0", "m", Ort.ZELLE, gitter);
        a.schreibt("surf.wet", "–", Ort.ZELLE, gitter, Feld.Zahl.FLOAT).schreibt("surf.T", "K", Ort.ZELLE, gitter, Feld.Zahl.FLOAT);
        a.schreibt("air.n", "–", Ort.SAEULE, saeulen, Feld.Zahl.FLOAT);
    }

    /** Liest den Eingang zur Zeit t in {@link #atmos}. */
    private void eingang(Bus bus, double t) {
        if (fH == null) {
            fH = bus.lesefeld(this, "water.h", gitter); fAlter = bus.lesefeld(this, "water.wetAge", gitter); fEta = bus.lesefeld(this, "water.eta", gitter);
            fU = bus.lesefeld(this, "air.u10", saeulen); fV = bus.lesefeld(this, "air.v10", saeulen); fP = bus.lesefeld(this, "air.p", saeulen);
            fT = bus.lesefeld(this, "air.t2", saeulen); fQ = bus.lesefeld(this, "air.q2", saeulen);
            fSw = bus.lesefeld(this, "air.sw", saeulen); fLw = bus.lesefeld(this, "air.lw", saeulen); fSst = bus.lesefeld(this, "water.sst", saeulen);
            if (mitRegen) fRegen = bus.lesefeld(this, "air.rain", saeulen);
        }
        if (mitRegen) bus.lies(this, "air.rain", t, fRegen);
        for (Feld f : new Feld[] {fH, fAlter, fEta, fU, fV, fP, fT, fQ, fSw, fLw, fSst}) bus.lies(this, f.eintrag.name(), t, f);
        Atmos at = atmos;
        if (rauheit) {
            if (fZ0 == null) fZ0 = bus.lesefeld(this, "surf.z0", gitter);
            bus.lies(this, "surf.z0", t, fZ0);
            for (int c = 0; c < at.nc; c++) { double z = fZ0.hol(c); at.z0[c] = z > 0 && Double.isFinite(z) ? z : 1e-3; }
        }
        double summe = 0;
        long tief = 0;
        for (int c = 0; c < at.nc; c++) {
            double h = fAlter.hol(c) >= 0 ? fH.hol(c) : 0;
            if (!(h >= at.hNass)) h = 0;
            at.h[c] = h;
            if (h >= at.hTief) {
                summe += fEta.hol(c);
                tief++;
            }
        }
        if (tief > 0) pegel = summe / tief;
        if (grund != null && !Double.isNaN(pegel)) for (int c = 0; c < at.nc; c++) at.ueber[c] = Math.max(0, grund[c] - pegel);
        for (int s = 0; s < at.ns; s++) {
            at.wind[s] = Math.hypot(fU.hol(s), fV.hol(s));
            at.t2[s] = fT.hol(s);
            at.q2[s] = fQ.hol(s);
            at.druck[s] = fP.hol(s);
            at.sonne[s] = Math.max(0, fSw.hol(s));
            at.gegen[s] = fLw.hol(s);
            at.tMeer[s] = fSst.hol(s);
            at.regen[s] = mitRegen && fRegen.hol(s) >= regenAb ? fRegen.hol(s) : 0;
        }
    }

    @Override public void beginn(double t0, Bus bus) {
        eingang(bus, t0);
        Atmos at = atmos;
        at.anfang(at.t2[0]);
        for (int j = 0; j < at.ny; j++)
            for (int i = 0; i < at.nx; i++) {
                int c = i + at.nx * j, s = at.saeule(i, j);
                for (int k = 0; k < Atmos.SCHICHTEN; k++) at.tb[Atmos.SCHICHTEN * c + k] = at.t2[s];
                at.tw[c] = at.tMeer[s];
                if (at.h[c] > 0) {
                    at.ts[c] = at.tMeer[s];
                } else {
                    at.ts[c] = at.t2[s];
                    at.trocken[c] = Math.max(0, -fAlter.hol(c));
                    at.grundwasser[c] = Math.max(0, Math.min(at.ueber[c], at.sinken * at.trocken[c]));
                    at.wet[c] = at.gleichgewicht(at.grundwasser[c]);
                }
            }
        at.profile();
        lege(bus, t0);
    }

    @Override public void schritt(double t0, double t1, Bus bus) {
        eingang(bus, t1);
        long n0 = System.nanoTime();
        atmos.schritt(t1 - t0);
        double r = 0;
        for (int s = 0; s < atmos.ns; s++) r += atmos.regen[s];
        regenSumme += r / atmos.ns * (t1 - t0);
        sekunden += (System.nanoTime() - n0) * 1e-9;
        schritte++;
        lege(bus, t1);
    }

    private void lege(Bus bus, double t) {
        Atmos at = atmos;
        Feld w = bus.schreibfeld(this, "surf.wet"), ts = bus.schreibfeld(this, "surf.T"), n = bus.schreibfeld(this, "air.n");
        for (int c = 0; c < at.nc; c++) {
            w.f[c] = (float) at.wet[c];
            ts.f[c] = (float) at.ts[c];
        }
        for (int s = 0; s < at.ns; s++) {
            double[] r = at.profile[s].r;
            for (int k = 0; k < Profil.STUFEN; k++) n.f[s * Profil.STUFEN + k] = (float) r[k];
        }
        bus.lege(this, "surf.wet", t);
        bus.lege(this, "surf.T", t);
        bus.lege(this, "air.n", t);
    }
}
