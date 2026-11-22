package com.dan.physics.waves;

import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;
import com.dan.core.raum.Utm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Der Seegang als Modul am Bus (Werkbuch HalligHooge Teil A, 7.3 und 12.9, P8b): Modul {@code waves}: Es
 * liest Wasser und Wind und schreibt die Kennwerte; seit P8c liest hydro {@code waves.force} im Intervall (Rückwirkung), der Takt rechnet den Seegang dafür ein Intervall voraus.
 * <ul>
 * <li><b>Gitter.</b> Eine Kette eigener Gitter in UTM, gröbstes zuerst (nach 7.3 G2w über G2 in 500 m, G3w über dem,
 * was hydro als G3 rechnet, in 100 m). Das gröbste bekommt den Rand aus ERA5 ({@link Randspektrum}), jedes weitere
 * einseitig das Spektrum des vorigen an den Mitten seiner Geisterzellen (bilinear über die nassen Zellen), am
 * Anfang und Ende des Intervalls und dazwischen linear.</li>
 * <li><b>Takt.</b> Kopplung {@link #kopplung} (5.6: 1 200 s [G]); darin Schritte zu höchstens {@link #dtQuellen}
 * (die Quellen), die Ausbreitung in Teilschritten nach Courant ({@link Waves#schritt}). Vor {@link #ab} (Modellzeit)
 * rechnet es nicht und legt null ab: Der Seegang braucht keinen Anlauf über Tage wie der Stau.</li>
 * <li><b>Liest</b> am Anfang des Intervalls {@code water.h}, {@code water.u}, {@code water.v} (Gitterachsen; der Bus
 * rechnet bilinear vom Gitter von hydro um) und ebenso {@code air.u10}, {@code air.v10}, mit
 * {@link #schub} dazu {@code air.taux}, {@code air.tauy}: Dann ist u* = √(|τ| / ρ_L) (W1) statt nach Wu. Wind und
 * Schub werden um die Meridiankonvergenz ins Gitter gedreht, wie in hydro. Der Wind wird nicht im Intervall gelesen:
 * Sonst triebe der Takt den Antrieb um ein ganzes Intervall des Seegangs voraus, weiter, als die Leitung von
 * {@code air.p} für hydro Stände hält.</li>
 * <li><b>Schreibt</b> am Ende des Intervalls je Gitter (float): {@code waves.hs} (m), {@code waves.tp} (s, 0 ohne
 * Seegang), {@code waves.dir} (Grad, woher die Wellen kommen, von Gitternord im Uhrzeigersinn), {@code waves.ubot},
 * {@code waves.mss}, {@code waves.diss} (Schaumkronen und Brechen, W/m²) und {@code waves.force} (zwei Komponenten in
 * Gitterachsen, N/m²).</li>
 * </ul>
 * Wo gerechnet wird, sagt der {@link Rechner}: {@link Prozessor} (die Referenz) oder die Karte
 * ({@code gpu.WavesRechner}). Kennt den Kern und kein anderes Modul.
 */
public final class WavesModul implements Modul {
    public static final String VERSION = "0.1";
    private static final String[] AUS = {"waves.hs", "waves.tp", "waves.dir", "waves.ubot", "waves.mss", "waves.diss"};
    private static final String[] EINHEIT = {"m", "s", "°", "m/s", "–", "W/m²"};
    /** Dichte der Luft für u* aus dem Schub (kg/m³), wie in hydro und antrieb. */
    public static final double RHO_L = 1.25;

    /** Wo gerechnet wird. */
    public interface Rechner extends AutoCloseable {
        /** Einmal je Gitter, nachdem {@link Waves#vorbereiten()} lief. */
        void einrichten(Waves w);
        /** Nach jedem {@link Waves#vorbereiten()}: Tiefe, Strömung, Wind und u* neu. */
        void felder(Waves w);
        /** Das Randfeld ({@link Waves#randAusFeld}); das Feld gehört danach dem Rechner. */
        void rand(Waves w, double[] feld);
        /** Ein Schritt wie {@link Waves#schritt}. */
        void schritt(Waves w, double dt);
        /** Spektrum und {@link Waves#diss} nach {@code w}. */
        void herunter(Waves w);
        String geraet();
        /** Stoppuhr des Rechners je Gitter (P13d): {Ausbreitung s, Quellen s, Teilschritte, Schritte}; null: keine. */
        default double[] stoppuhr(Waves w) { return null; }
        @Override default void close() { }
    }

    /** Die Referenz auf dem Prozessor. */
    public static final class Prozessor implements Rechner {
        @Override public void einrichten(Waves w) { }
        @Override public void felder(Waves w) { }
        @Override public void rand(Waves w, double[] feld) { w.randAusFeld(feld); }
        @Override public void schritt(Waves w, double dt) { w.schritt(dt); }
        @Override public void herunter(Waves w) { }
        @Override public String geraet() { return "Prozessor (" + Runtime.getRuntime().availableProcessors() + " Fäden, double)"; }
    }

    public final List<Gitter> gitter;
    public final List<Waves> waves = new ArrayList<>();
    public final Rechner rechner;
    private final Randspektrum rand0;
    private final Instant start;
    /** Kopplungsintervall (s) [G, 5.6]. */
    public final double kopplung;
    /** Längster Schritt der Quellen (s) [G]. */
    public double dtQuellen = 60;
    /** Liest den Windschub für u* (W1). */
    public final boolean schub;
    /** Ab dieser Modellzeit (s) rechnet der Seegang; davor legt er null ab. */
    public double ab;
    /** Rechenzeit in {@link #schritt} (s) und gerechnete Intervalle. */
    public double sekunden;
    public long intervalle;
    /** Teile der Stoppuhr (P13d, A33), je Gitter Sekunden in {@link #stoppuhr}. */
    public static final String[] TEILE = {"lesen", "Rand", "Schritt", "herunter", "Schachtel", "ablegen"};
    /** Sekunden je Gitter und Teil ({@link #TEILE}) seit dem Bau; vor {@link #ab} zählt nur lesen und ablegen. */
    public double[][] stoppuhr;
    private final double[][] kos, sin;
    private Feld[][] lese;
    private double[][] randA, randB;

    /**
     * @param gitter    die Gitter des Seegangs in UTM, gröbstes zuerst; jedes weitere ganz im vorigen
     * @param seegang   Seegang aus ERA5 ({@code antrieb/seegang.txt}) für den Rand des gröbsten
     * @param start     Szenenzeit 0
     */
    public WavesModul(List<Gitter> gitter, int nf, int nd, com.dan.core.gitter.Wetterfeld seegang, Instant start, Rechner rechner, double kopplung, boolean schub) {
        if (gitter.isEmpty()) throw new IllegalArgumentException("waves: kein Gitter");
        this.gitter = List.copyOf(gitter);
        this.rechner = rechner;
        this.kopplung = kopplung;
        this.schub = schub;
        this.start = start;
        int n = gitter.size();
        kos = new double[n][];
        sin = new double[n][];
        for (int k = 0; k < n; k++) {
            Gitter g = gitter.get(k);
            if (g.lage != Gitter.Lage.UTM) throw new IllegalArgumentException("waves: Gitter " + g.name + " ist nicht in UTM");
            Waves w = new Waves(g.nx, g.ny, g.dx, g.dy, nf, 0.04, 0.6, nd);
            w.vorbereiten();
            waves.add(w);
            kos[k] = new double[g.zellen()];
            sin[k] = new double[g.zellen()];
            final int kk = k;
            IntStream.range(0, g.ny).parallel().forEach(j -> {
                for (int i = 0; i < g.nx; i++) {
                    double[] ll = g.grad(g.x(i), g.y(j));
                    double gam = Math.toRadians(Utm.faktoren(ll[0], ll[1], g.zone)[0]);
                    kos[kk][g.index(i, j)] = Math.cos(gam);
                    sin[kk][g.index(i, j)] = Math.sin(gam);
                }
            });
            if (k > 0 && !innen(gitter.get(k - 1), g))
                throw new IllegalArgumentException("waves: " + g.name + " liegt nicht im Gitter " + gitter.get(k - 1).name);
        }
        rand0 = new Randspektrum(seegang, gitter.get(0), waves.get(0));
        stoppuhr = new double[n][TEILE.length];
        randA = new double[n][];
        randB = new double[n][];
        for (Waves w : waves) rechner.einrichten(w);
    }

    private static boolean innen(Gitter grob, Gitter fein) {
        return fein.x0 >= grob.x0 - 1e-6 && fein.y0 >= grob.y0 - 1e-6 && fein.x1() <= grob.x1() + 1e-6 && fein.y1() <= grob.y1() + 1e-6;
    }

    /** Der Rand des gröbsten Gitters (für die Prüfung). */
    public Randspektrum rand() { return rand0; }

    @Override public String name() { return "waves"; }

    @Override public String version() { return VERSION + " (Waves " + Waves.VERSION + ", " + rechner.geraet() + ")"; }

    @Override public void anmelden(Anmeldung a) {
        a.kopplung(kopplung);
        for (Gitter g : gitter) {
            a.liest("water.h", "m", Ort.ZELLE, g).liest("water.u", "m/s", Ort.ZELLE, g).liest("water.v", "m/s", Ort.ZELLE, g);
            a.liest("air.u10", "m/s", Ort.ZELLE, g).liest("air.v10", "m/s", Ort.ZELLE, g);
            if (schub) a.liest("air.taux", "N/m²", Ort.ZELLE, g).liest("air.tauy", "N/m²", Ort.ZELLE, g);
            for (int q = 0; q < AUS.length; q++) a.schreibt(AUS[q], EINHEIT[q], Ort.ZELLE, g, Feld.Zahl.FLOAT);
            a.schreibt("waves.force", "N/m²", Ort.ZELLE, g, Feld.Zahl.FLOAT);
        }
    }

    @Override public void beginn(double t0, Bus bus) {
        lese = new Feld[gitter.size()][];
        for (int k = 0; k < gitter.size(); k++) {
            Gitter g = gitter.get(k);
            List<Feld> l = new ArrayList<>(List.of(bus.lesefeld(this, "water.h", g), bus.lesefeld(this, "water.u", g), bus.lesefeld(this, "water.v", g),
                    bus.lesefeld(this, "air.u10", g), bus.lesefeld(this, "air.v10", g)));
            if (schub) { l.add(bus.lesefeld(this, "air.taux", g)); l.add(bus.lesefeld(this, "air.tauy", g)); }
            lese[k] = l.toArray(new Feld[0]);
        }
        // Liest hydro die Kraft des Seegangs im Intervall (P8c), beginnt der Seegang vor hydro (Takt.beginn): Dann liegt
        // noch kein Wasser auf dem Bus. Der Anfangsstand ist ohnehin leer; gelesen wird mit dem ersten Schritt.
        if (bus.staende("water.h").length > 0) lesen(bus, t0, t0);
        lege(bus, t0);
    }

    @Override public void schritt(double t0, double t1, Bus bus) {
        long n0 = System.nanoTime();
        lesen(bus, t0, t0);
        if (t1 > ab + 1e-9) {
            int n = Math.max(1, (int) Math.ceil((t1 - t0) / dtQuellen - 1e-9));
            double dt = (t1 - t0) / n;
            for (int k = 0; k < waves.size(); k++) {
                Waves w = waves.get(k);
                double[] su = stoppuhr[k];
                long u = System.nanoTime();
                if (k + 1 < waves.size()) randA[k + 1] = schachtel(k, k + 1);            // Rand des feineren zur Zeit t0
                u = uhr(su, 4, u);
                for (int s = 0; s < n; s++) {
                    double tm = t0 + (s + 0.5) * dt;
                    if (k == 0) rechner.rand(w, rand0.feld(zeitpunkt(tm)));
                    else {
                        double a = (s + 0.5) / n;
                        double[] f = new double[randA[k].length];
                        for (int i = 0; i < f.length; i++) f[i] = (1 - a) * randA[k][i] + a * randB[k][i];
                        rechner.rand(w, f);
                    }
                    u = uhr(su, 1, u);
                    rechner.schritt(w, dt);
                    u = uhr(su, 2, u);
                }
                rechner.herunter(w);
                u = uhr(su, 3, u);
                if (k + 1 < waves.size()) randB[k + 1] = schachtel(k, k + 1);            // und zur Zeit t1
                uhr(su, 4, u);
            }
            intervalle++;
        }
        long u = System.nanoTime();
        lege(bus, t1);
        long e = System.nanoTime();
        for (int k = 0; k < waves.size(); k++) stoppuhr[k][5] += (e - u) * 1e-9 / waves.size();
        sekunden += (e - n0) * 1e-9;
    }

    /** Bucht die Zeit seit u auf den Teil q und gibt die jetzige Zeit zurück. */
    private static long uhr(double[] su, int q, long u) {
        long j = System.nanoTime();
        su[q] += (j - u) * 1e-9;
        return j;
    }

    private Instant zeitpunkt(double t) { return start.plusNanos(Math.round(t * 1e9)); }

    /** Wasser zur Zeit tw und Wind zur Zeit tl in die Gitter; Vorbereiten auf dem Prozessor und dem Rechner. */
    private void lesen(Bus bus, double tw, double tl) {
        for (int k = 0; k < gitter.size(); k++) {
            long u = System.nanoTime();
            Feld[] f = lese[k];
            for (int q = 0; q < f.length; q++) bus.lies(this, f[q].eintrag.name(), q < 3 ? tw : tl, f[q]);
            Waves w = waves.get(k);
            double[] co = kos[k], si = sin[k];
            if (schub && w.uStern == null) w.uStern = new double[w.nc];
            IntStream.range(0, w.nc).parallel().forEach(c -> {
                double h = f[0].hol(c);
                w.tiefe[c] = Double.isFinite(h) ? h : 0;
                w.u[c] = f[1].hol(c);
                w.v[c] = f[2].hol(c);
                if (!Double.isFinite(w.u[c]) || w.tiefe[c] < w.minTiefe) w.u[c] = 0;
                if (!Double.isFinite(w.v[c]) || w.tiefe[c] < w.minTiefe) w.v[c] = 0;
                double ue = f[3].hol(c), vn = f[4].hol(c);
                w.windU[c] = ue * co[c] - vn * si[c];
                w.windV[c] = vn * co[c] + ue * si[c];
                if (schub) w.uStern[c] = Math.sqrt(Math.hypot(f[5].hol(c), f[6].hol(c)) / RHO_L);
            });
            w.vorbereiten();
            rechner.felder(w);
            if (stoppuhr != null) stoppuhr[k][0] += (System.nanoTime() - u) * 1e-9;
        }
    }

    /**
     * Die Stoppuhr als Text (P13d, A33): je Gitter Sekunden je Teil, dazu aus dem Rechner Ausbreitung und Quellen, die
     * Teilschritte der Ausbreitung je Schritt und die Zellen.
     */
    public String stoppuhrText() {
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < gitter.size(); k++) {
            Gitter g = gitter.get(k);
            Waves w = waves.get(k);
            double[] su = stoppuhr[k], r = rechner.stoppuhr(w);
            double summe = 0;
            for (double x : su) summe += x;
            b.append(k > 0 ? "; " : "").append(String.format(java.util.Locale.ROOT, "%s (%d × %d, %.0f m): %.1f s", g.name, g.nx, g.ny, g.dx, summe));
            for (int q = 0; q < TEILE.length; q++) b.append(String.format(java.util.Locale.ROOT, ", %s %.1f", TEILE[q], su[q]));
            if (r != null) b.append(String.format(java.util.Locale.ROOT, " (davon Ausbreitung %.1f, Quellen %.1f; %.1f Teilschritte je Schritt über %.0f Schritte)",
                    r[0], r[1], r[2] / Math.max(1, r[3]), r[3]));
        }
        return b.toString();
    }

    /** Summe der Stoppuhr über alle Gitter und Teile (s). */
    public double stoppuhrSumme() {
        double s = 0;
        for (double[] su : stoppuhr) for (double x : su) s += x;
        return s;
    }

    /**
     * Das Randfeld des Gitters {@code fein} aus dem Spektrum von {@code grob}: an der Mitte jeder Geisterzelle bilinear
     * über die vier Zellen von {@code grob} um sie, nur die nassen, Gewichte neu auf 1; ohne nasse Zelle 0.
     */
    private double[] schachtel(int grob, int fein) {
        Gitter gg = gitter.get(grob), gf = gitter.get(fein);
        Waves wg = waves.get(grob), wf = waves.get(fein);
        int nfd = wg.nf * wg.nd, rz = gf.randZellen();
        double[] feld = new double[rz * nfd];
        IntStream.range(0, rz).parallel().forEach(r -> {
            double[] p = gf.geist(r);
            double fi = (p[0] - gg.x0) / gg.dx - 0.5, fj = (p[1] - gg.y0) / gg.dy - 0.5;
            int i0 = (int) Math.floor(fi), j0 = (int) Math.floor(fj);
            double a = fi - i0, b = fj - j0, summe = 0;
            int[] zelle = new int[4];
            double[] gew = new double[4];
            int z = 0;
            for (int dj = 0; dj < 2; dj++)
                for (int di = 0; di < 2; di++) {
                    int i = Math.max(0, Math.min(gg.nx - 1, i0 + di)), j = Math.max(0, Math.min(gg.ny - 1, j0 + dj));
                    int c = j * gg.nx + i;
                    if (!wg.nass(c)) continue;
                    double g = (di == 0 ? 1 - a : a) * (dj == 0 ? 1 - b : b);
                    zelle[z] = c;
                    gew[z++] = g;
                    summe += g;
                }
            if (summe <= 0) return;
            for (int k = 0; k < z; k++) {
                double g = gew[k] / summe;
                int o = zelle[k] * nfd;
                for (int n = 0; n < nfd; n++) feld[r * nfd + n] += g * wg.e[o + n];
            }
        });
        if (wf.nf != wg.nf || wf.nd != wg.nd) throw new IllegalStateException("waves: Gitter mit verschiedenen Stützstellen");
        return feld;
    }

    /** Legt die Kennwerte aller Gitter zur Zeit t ab. */
    private void lege(Bus bus, double t) {
        for (int k = 0; k < gitter.size(); k++) {
            Gitter g = gitter.get(k);
            Waves w = waves.get(k);
            Feld[] f = new Feld[AUS.length];
            for (int q = 0; q < AUS.length; q++) f[q] = bus.schreibfeld(this, AUS[q], g);
            Feld fk = bus.schreibfeld(this, "waves.force", g);
            double[][] kr = w.kraft();
            IntStream.range(0, w.nc).parallel().forEach(c -> {
                boolean da = w.nass(c) && w.m0(c) > 0;
                f[0].f[c] = da ? (float) w.hs(c) : 0f;
                double tp = da ? w.tp(c) : 0;
                f[1].f[c] = Double.isFinite(tp) ? (float) tp : 0f;
                double r = da ? w.richtung(c) : Double.NaN;
                f[2].f[c] = Double.isFinite(r) ? (float) (((270 - r) % 360 + 360) % 360) : 0f;
                f[3].f[c] = da ? (float) w.ubot(c) : 0f;
                f[4].f[c] = da ? (float) w.mss(c) : 0f;
                f[5].f[c] = da ? (float) w.diss[c] : 0f;
                fk.f[2 * c] = da ? (float) kr[0][c] : 0f;
                fk.f[2 * c + 1] = da ? (float) kr[1][c] : 0f;
            });
            for (String n : AUS) bus.lege(this, n, g, t);
            bus.lege(this, "waves.force", g, t);
        }
    }

    /** Werte des Spektrums, die nicht endlich oder negativ sind, über alle Gitter (Prüfstein 6 von P8b). */
    public long unheil() {
        long n = 0;
        for (Waves w : waves) for (double v : w.e) if (!(v >= 0) || Double.isInfinite(v)) n++;
        return n;
    }

    /** Größte Wellenhöhe je Gitter (m), für Protokolle. */
    public double[] hsMax() {
        double[] m = new double[waves.size()];
        for (int k = 0; k < m.length; k++) {
            Waves w = waves.get(k);
            for (int c = 0; c < w.nc; c++) if (w.nass(c)) m[k] = Math.max(m[k], w.hs(c));
        }
        return m;
    }
}
