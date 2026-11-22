package com.dan.lauf;

import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;

import java.util.List;

/**
 * Das Bild am Bus, live (P6e, Werkbuch HalligHooge Teil A, 12.7; 5.5): ein Modul {@code render}, das nur liest und
 * nicht getaktet wird. Es liest {@code water.eta}, {@code water.h} und {@code water.wetAge} auf den Gittern des Wassers
 * und {@code air.u10}, {@code air.v10} auf einem davon, und gibt die gehaltenen Stände als {@link Bild.Quelle} weiter.
 * Gelesen wird je Stand genau zu dessen Zeit (der Bus kopiert dann ohne zu mitteln); wird ein Stand verdrängt, sagt
 * {@link #stand} nein.
 */
public final class BildModul implements Modul, Bild.Quelle {
    public static final String VERSION = "0.2";
    private final List<Gitter> gitter;
    private final Gitter wind;
    private Bus bus;
    private Feld[] fe, fh, fa;
    private Feld fu, fv;
    /** Seit 0.2 (P9c): das Gitter des Sediments im Lauf (null: ohne) und ob hydro mit dem bewegten Boden rechnet. */
    private final Gitter sed;
    private final boolean bewegt;
    private Feld fc, fr, fm, fz;

    /** @param wind Gitter für den Wind (null: ohne Wind) */
    public BildModul(List<Gitter> gitter, Gitter wind) { this(gitter, wind, null, false); }

    /** @param sediment Gitter des Sediments (null: ohne): Das Bild liest dazu {@code sed.c}, {@code bed.ripple}, {@code bed.mud}, {@code bed.dz}; {@code bewegt}: hydro rechnet mit {@code bed.dz}. */
    public BildModul(List<Gitter> gitter, Gitter wind, Gitter sediment, boolean bewegt) {
        this.gitter = List.copyOf(gitter);
        this.wind = wind;
        this.sed = sediment;
        this.bewegt = bewegt;
    }

    @Override public String name() { return "render"; }

    @Override public String version() { return VERSION; }

    @Override public void anmelden(Anmeldung a) {
        a.ohneTakt();
        for (Gitter g : gitter) a.liest("water.eta", "m NHN", Ort.ZELLE, g).liest("water.h", "m", Ort.ZELLE, g).liest("water.wetAge", "s", Ort.ZELLE, g);
        if (wind != null) a.liest("air.u10", "m/s", Ort.ZELLE, wind).liest("air.v10", "m/s", Ort.ZELLE, wind);
        if (sed != null) a.liest("sed.c", "kg/m³", Ort.ZELLE, sed).liest("bed.ripple", "m, m, °", Ort.ZELLE, sed).liest("bed.mud", "–", Ort.ZELLE, sed).liest("bed.dz", "m", Ort.ZELLE, sed);
    }

    @Override public void beginn(double t0, Bus bus) { verbinde(bus); }

    @Override public void schritt(double t0, double t1, Bus bus) { }

    /** Nach dem Verdrahten (ein Modul ohne Takt bekommt kein {@link #beginn}). */
    synchronized void verbinde(Bus b) {
        if (bus == b) return;
        bus = b;
        int n = gitter.size();
        fe = new Feld[n];
        fh = new Feld[n];
        fa = new Feld[n];
        for (int k = 0; k < n; k++) {
            Gitter g = gitter.get(k);
            fe[k] = b.lesefeld(this, "water.eta", g);
            fh[k] = b.lesefeld(this, "water.h", g);
            fa[k] = b.lesefeld(this, "water.wetAge", g);
        }
        if (wind != null) {
            fu = b.lesefeld(this, "air.u10", wind);
            fv = b.lesefeld(this, "air.v10", wind);
        }
        if (sed != null) {
            fc = b.lesefeld(this, "sed.c", sed);
            fr = b.lesefeld(this, "bed.ripple", sed);
            fm = b.lesefeld(this, "bed.mud", sed);
            fz = b.lesefeld(this, "bed.dz", sed);
        }
    }

    // ------------------------------------------------------------------ Sediment (seit 0.2, P9c: live mit Sediment)

    @Override public Gitter sedimentGitter() { return sed; }

    @Override public double[] sedimentZeiten() { return bus == null || sed == null ? new double[0] : bus.staende("sed.c", sed); }

    @Override public double[] bodenZeiten() { return bus == null || sed == null ? new double[0] : bus.staende("bed.dz", sed); }

    @Override public boolean bodenBewegt() { return bewegt; }

    @Override public synchronized boolean sediment(double ts, float[] c) {
        if (bus == null || sed == null) return false;
        double[] z = bus.staende("sed.c", sed);
        if (z.length == 0 || ts < z[0] || ts > z[z.length - 1]) return false;
        if (!genau(bus.lies(this, "sed.c", ts, fc), ts, 1e-9 * Math.max(1, Math.abs(ts)))) return false;
        kopiere(fc, c);
        return true;
    }

    @Override public synchronized boolean boden(double ts, float[] rippel, float[] schlick, float[] dz) {
        if (bus == null || sed == null) return false;
        double[] z = bus.staende("bed.dz", sed);
        if (z.length == 0 || ts < z[0] || ts > z[z.length - 1]) return false;
        double tol = 1e-9 * Math.max(1, Math.abs(ts));
        if (!genau(bus.lies(this, "bed.dz", ts, fz), ts, tol) || !genau(bus.lies(this, "bed.ripple", ts, fr), ts, tol) || !genau(bus.lies(this, "bed.mud", ts, fm), ts, tol)) return false;
        if (rippel != null) kopiere(fr, rippel);
        if (schlick != null) kopiere(fm, schlick);
        if (dz != null) kopiere(fz, dz);
        return true;
    }

    @Override public String herkunft() { return "Bus"; }

    @Override public List<Gitter> gitter() { return gitter; }

    @Override public Gitter windGitter() { return wind; }

    @Override public double[] zeiten() {
        Bus b = bus;
        return b == null || gitter.isEmpty() ? new double[0] : b.staende("water.eta", gitter.get(gitter.size() - 1));
    }

    @Override public synchronized boolean stand(double ts, int k, float[] eta, float[] h, float[] wetAge) {
        if (bus == null) return false;
        double[] z = bus.staende("water.eta", gitter.get(k));
        if (z.length == 0 || ts < z[0] || ts > z[z.length - 1]) return false;      // verdrängt: nicht außerhalb lesen
        double tol = 1e-9 * Math.max(1, Math.abs(ts));
        if (!genau(bus.lies(this, "water.eta", ts, fe[k]), ts, tol) || !genau(bus.lies(this, "water.h", ts, fh[k]), ts, tol)
                || !genau(bus.lies(this, "water.wetAge", ts, fa[k]), ts, tol)) return false;
        kopiere(fe[k], eta);
        kopiere(fh[k], h);
        kopiere(fa[k], wetAge);
        // ein Stand, der zwischen dem Lesen der drei Felder verdrängt wurde, hätte eine andere Zeit
        return fe[k].zeit == fh[k].zeit && fh[k].zeit == fa[k].zeit;
    }

    @Override public synchronized boolean wind(double ts, float[] u, float[] v) {
        if (bus == null || fu == null) return false;
        double[] z = bus.staende("air.u10");
        if (z.length == 0) return false;
        double t = Math.max(z[0], Math.min(z[z.length - 1], ts));       // der Antrieb rechnet voraus; außerhalb nicht lesen
        bus.lies(this, "air.u10", t, fu);
        bus.lies(this, "air.v10", t, fv);
        kopiere(fu, u);
        kopiere(fv, v);
        return true;
    }

    private static boolean genau(double zeit, double ts, double tol) { return Math.abs(zeit - ts) <= tol; }

    private static void kopiere(Feld f, float[] ziel) {
        if (f.f != null) System.arraycopy(f.f, 0, ziel, 0, ziel.length);
        else for (int i = 0; i < ziel.length; i++) ziel[i] = (float) f.d[i];
    }
}
