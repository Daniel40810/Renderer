package com.dan.physics.hydro;

import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;
import com.dan.core.raum.Utm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * hydro als Modul am Bus (Werkbuch HalligHooge Teil A, 7.1, 7.2 und 12.6, P5c; F27 A): Ein Modul rechnet alle Gitter
 * der Kette, G0 bis G4.
 * <ul>
 * <li><b>Stufen.</b> Stufe 0 ist ein Gitter mit offenem Rand aus {@code tide.bc} (G0, geographisch). Jede weitere
 * Stufe bekommt ihren Rand einseitig aus der gröbsten Ebene der vorigen ({@link Einseitig}): ein einzelnes Gitter (G1)
 * oder eine Kette, beidseitig geschachtelt (G2 ↔ G3 ↔ G4, {@link Kette}). Gerechnet wird je Kopplungsintervall Stufe
 * für Stufe bis zu seinem Ende, in gleichen Schritten ({@link Rechnung#bis}).</li>
 * <li><b>Liest</b> im Intervall ({@link Anmeldung#liestImIntervall}): {@code tide.bc} auf G0 (zwischen den Ständen am
 * Anfang und Ende des Intervalls linear, in jedem Schritt); {@code tide.eq} auf den Gittern aus {@link #eq} und, mit
 * Antrieb, {@code air.u10}, {@code air.v10}, {@code air.p} auf allen Gittern – diese drei zur Mitte des Intervalls
 * (das Integral über das Intervall ist damit in zweiter Ordnung genau). Wind auf Gittern in UTM wird um die
 * Meridiankonvergenz in Gitterrichtung gedreht. Mit {@link #schub} (seit 0.3, P5e, F34) statt des Windes
 * {@code air.taux}, {@code air.tauy} (N/m²), ebenso gedreht: Der Schub kommt dann vom Antrieb und geht als Kraft an der
 * Oberfläche ({@code Hydro.kraftX}, {@code kraftY}) in den Impuls; die Formel nach Wu in {@code Hydro} ruht.</li>
 * <li><b>Seegang (seit 0.4, P8c).</b> Auf den Gittern aus {@link #seegang} liest hydro {@code waves.force} (N/m², zwei
 * Komponenten in Gitterachsen, aus dem Gefälle der Strahlungsspannung) im Intervall, zur Mitte des Intervalls, und rechnet
 * sie zur Kraft an der Oberfläche ({@code Hydro.kraftX}, {@code kraftY}) dazu: beim Windschub vom Bus zum Schub, sonst
 * allein (der Wind geht dann nach Wu über {@code windX/Y}). Die Kraft wächst ab {@link #seegangAb} über
 * {@link #seegangAnlauf} linear von 0 auf 1 [G]. Die Gitter des Seegangs liegen in UTM wie G2 und G3; gedreht wird nichts.</li>
 * <li><b>Boden und Rauheit (seit 0.5, P9c, F81 A).</b> Auf den Gittern aus {@link #boden} liest hydro zu Beginn des
 * Intervalls {@code bed.dz} und verschiebt damit die Tabellen ({@code Hydro.boden}: Volumen bleibt, η geht mit); mit
 * {@link #rauheit} dazu {@code surf.z0} und rechnet daraus Manning-n nach Strickler (n = k_s^(1/6) / 26 mit k_s = 30 z0
 * [G], wenigstens {@link #nMin}; z0 ≤ 0: die Rauheit der Packung). Auf den Gittern aus {@link #spirale} schreibt es
 * {@code water.spiral} = h · |u| · κ (m/s) mit der Krümmung κ der Stromlinien (1/m, positiv: der Strom dreht nach links),
 * im Gleichgewicht, ohne Anpassungsweg [G]; |κ| höchstens 1 / (2 Zellweiten).</li>
 * <li><b>Schreibt</b> auf jedem Gitter am Ende des Intervalls, in float: {@code water.eta} (m NHN), {@code water.h}
 * (V / benetzte Fläche, m), {@code water.u}, {@code water.v} (in der Zelle, in Richtung der Gitterachsen),
 * {@code water.q} (Fluss je Kante als Zeitmittel über das Intervall, m²/s; unter einem feineren Gitter 0) und
 * {@code water.wetAge} (s: in einer nassen Zelle, wie lange sie nass ist, in einer trockenen, negativ, wie lange sie
 * trocken liegt; gezählt in Intervallen, ein Wechsel in der Mitte). Nass ist eine Zelle ab {@link #hNass} mittlerer
 * Tiefe über die ganze Zelle [G].</li>
 * </ul>
 * <li><b>Stufe 0 von außen (seit 0.6, P11c, F110 A).</b> Mit {@link #vorgabe0} rechnet hydro die Stufe 0 nicht: Ihr Stand
 * kommt zu Beginn und am Ende jedes Intervalls von außen (etwa aus einem Archiv) und geht einseitig an Stufe 1; Stufe 0
 * liest dann weder {@code tide.bc} noch den Antrieb und schreibt nichts. So rechnet ein Nachlauf G3 allein mit dem Rand
 * aus einem abgelegten G2. Mit {@link #bodenquelle} kommt {@code bed.dz} ebenso von außen statt vom Bus.</li>
 * <li><b>Aufschlag am Rand der innersten Stufe (seit 0.7, Steuerhaus Y2, Werkbuch HalligHooge 153).</b> Ein Ziel
 * {@link #aufschlagZiel} (m, aus jedem Faden setzbar) hebt den Wasserstand am Rand der letzten Stufe (im Live-Lauf G2,
 * rund 35–45 km um Hooge); hydro fährt es in der Modellzeit mit {@link #aufschlagRampe} an, in jedem Intervall linear
 * ({@link Einseitig#zusatz(double, double)}). Ohne Ziel bleibt alles bitgleich. Der Rand von G0 liegt im Atlantik; ein
 * Aufschlag dort wäre erst nach rund einem Tag an Hooge.</li>
 * </ul>
 * An einer Randzelle ohne Wasser ({@code tide.bc} NaN) ist die Geisterzelle trocken auf {@link #land} (wie das
 * geschlossene Land). Kennt den Kern und kein anderes Modul (Schichtenvertrag, Werkbuch 4); wo gerechnet wird, sagt
 * die {@link Rechnung}.
 */
public final class HydroModul implements Modul {
    public static final String VERSION = "0.7";
    private static final String[] AUS = {"water.eta", "water.h", "water.u", "water.v", "water.wetAge"};
    private static final String[] EINHEIT = {"m NHN", "m", "m/s", "m/s", "s"};

    /** Je Stufe die Gitter, gröbstes zuerst. */
    public final List<Hydro[]> stufen;
    /** Alle Gitter in der Folge der Stufen. */
    public final List<Hydro> gitter = new ArrayList<>();
    public final Rechnung rechnung;
    /** Kopplungsintervall (s) [G]. */
    public final double kopplung;
    /** Liest Wind und Luftdruck ({@code air.*}). */
    public final boolean antrieb;
    /** Liest mit dem Antrieb den Windschub ({@code air.taux}, {@code air.tauy}) statt des Windes (P5e, F34). */
    public final boolean schub;
    /** Namen der Gitter, die {@code tide.eq} lesen (G0, G1). */
    public final Set<String> eq;
    /** Mittlere Tiefe über die Zelle (m), ab der sie für {@code water.wetAge} nass ist [G]. */
    public double hNass = 1e-4;
    /** Höhe einer trockenen Geisterzelle am Rand ohne Wasser (m NHN), wie das geschlossene Land [G]. */
    public double land = 10;
    /** Seit 0.7 (Y2): Ziel des Aufschlags am Rand der letzten Stufe (m); aus jedem Faden setzbar. */
    public volatile double aufschlagZiel = 0;
    /** Seit 0.7: wie schnell der Aufschlag dem Ziel folgt (m je Stunde Modellzeit) [G]. */
    public double aufschlagRampe = 1;
    private volatile double aufschlag;
    private boolean aufschlagGesetzt;
    private final Einseitig[] einseitig;
    /** Je Gitter: cos und sin der Meridiankonvergenz (null bei geographischer Lage). */
    private final double[][] kos, sin;
    /** Je Gitter: nass am Ende des letzten Intervalls und das Alter (s). */
    private final boolean[][] nass;
    private final double[][] alter;
    private Feld bcA, bcB;
    private double tA, tB;
    private final Feld[][] luft;
    private final Feld[] gleich;
    /** Volumen der Stufen am Anfang (m³), für die Bilanz. */
    public double[] volumenAnfang;
    /** Namen der Gitter, die {@code waves.force} lesen (P8c); leer ohne Rückwirkung des Seegangs. */
    public final Set<String> seegang;
    /** Modellzeit (s), ab der der Seegang rechnet, und die Dauer (s), über die seine Kraft auf 1 wächst [G]. */
    public double seegangAb, seegangAnlauf = 3600;
    /** Je Gitter das Lesefeld {@code waves.force} (null ohne Rückwirkung). */
    private final Feld[] kraftSee;
    /** Seit 0.5 (P9c), vor dem Anmelden zu setzen: Namen der Gitter, auf denen hydro {@code bed.dz} liest und den Boden verschiebt. */
    public Set<String> boden = Set.of();
    /** Seit 0.5: auf den Gittern aus {@link #boden} auch {@code surf.z0} lesen und die Rauheit der Kanten daraus rechnen. */
    public boolean rauheit;
    /**
     * Seit P13b (F153 A): Gitter, auf denen hydro {@code surf.z0} liest, auch ohne Boden aus {@link #boden} (die
     * Salzwiese aus {@code physics.vegetation}); Rechnung wie bei {@link #rauheit}.
     */
    public Set<String> rauheitGitter = Set.of();
    /** Kleinstes Manning-n aus {@code surf.z0} (s/m^(1/3)) [G]. */
    public double nMin = 0.015;
    /** Seit 0.5: Namen der Gitter (in UTM), auf denen hydro {@code water.spiral} schreibt. */
    public Set<String> spirale = Set.of();
    /** Zellen, deren Boden sich geändert hat, summiert über die Intervalle (Auskunft). */
    public long bodenZellen;
    private final Feld[] fBoden, fZ0;

    /** Stand einer Stufe von außen (seit 0.6): zur Modellzeit t, für die einseitige Kopplung in Stufe 1. */
    public interface Vorgabe { Einseitig.Stand stand(double t); }

    /** Boden von außen (seit 0.6): {@code bed.dz} (m) je Zelle zur Modellzeit t; false, wenn es keinen gibt. */
    public interface Bodenquelle { boolean dz(double t, double[] ziel); }

    /** Seit 0.6, vor dem Anmelden: Stufe 0 kommt von außen und wird nicht gerechnet (null: wie bisher aus {@code tide.bc}). */
    public Vorgabe vorgabe0;
    /** Seit 0.6, vor dem Anmelden: {@code bed.dz} für die Gitter aus {@link #boden} von außen statt vom Bus. */
    public Bodenquelle bodenquelle;

    /** Ist das Gitter Stufe 0 und kommt von außen? */
    private boolean aussen(Hydro h) { return vorgabe0 != null && h == gitter.get(0); }

    /**
     * @param stufen   je Stufe die Gitter, gröbstes zuerst; Stufe 0 genau eines (der offene Rand). Die Zellen sind
     *                 gefüllt (ruhender See), die Bauwerke gesetzt.
     * @param rechnung Prozessor oder Karte
     * @param kopplung Kopplungsintervall (s)
     * @param antrieb  mit Wind und Luftdruck
     * @param eq       Namen der Gitter mit Gleichgewichtstide
     */
    public HydroModul(List<Hydro[]> stufen, Rechnung rechnung, double kopplung, boolean antrieb, Set<String> eq) {
        this(stufen, rechnung, kopplung, antrieb, false, eq);
    }

    /** Wie oben; {@code schub}: mit dem Antrieb den Windschub vom Bus statt des Windes (P5e, F34). */
    public HydroModul(List<Hydro[]> stufen, Rechnung rechnung, double kopplung, boolean antrieb, boolean schub, Set<String> eq) {
        this(stufen, rechnung, kopplung, antrieb, schub, eq, Set.of());
    }

    /** Wie oben; {@code seegang}: Namen der Gitter, die {@code waves.force} lesen (P8c, Rückwirkung des Seegangs). */
    public HydroModul(List<Hydro[]> stufen, Rechnung rechnung, double kopplung, boolean antrieb, boolean schub, Set<String> eq, Set<String> seegang) {
        if (stufen.isEmpty() || stufen.get(0).length != 1) throw new IllegalArgumentException("hydro: Stufe 0 ist genau ein Gitter mit offenem Rand");
        this.stufen = List.copyOf(stufen);
        this.rechnung = rechnung;
        this.kopplung = kopplung;
        this.antrieb = antrieb;
        this.schub = antrieb && schub;
        this.eq = Set.copyOf(eq);
        this.seegang = Set.copyOf(seegang);
        for (Hydro[] st : stufen) for (Hydro h : st) gitter.add(h);
        int n = gitter.size();
        kos = new double[n][];
        sin = new double[n][];
        nass = new boolean[n][];
        alter = new double[n][];
        luft = new Feld[n][];
        gleich = new Feld[n];
        kraftSee = new Feld[n];
        fBoden = new Feld[n];
        fZ0 = new Feld[n];
        for (int k = 0; k < n; k++) {
            Hydro h = gitter.get(k);
            h.qSumme = new double[h.kanten];
            h.qBrutto = new double[h.kanten];
            nass[k] = new boolean[h.zellen];
            alter[k] = new double[h.zellen];
            if (antrieb) {
                if (this.schub) {
                    h.kraftX = new double[h.zellen];
                    h.kraftY = new double[h.zellen];
                } else {
                    h.windX = new double[h.zellen];
                    h.windY = new double[h.zellen];
                }
                h.druck = new double[h.zellen];
                if (h.gitter.lage == Gitter.Lage.UTM) konvergenz(k, h.gitter);
            }
            if (eq.contains(h.gitter.name)) h.gleich = new double[h.zellen];
            // die Kraft des Seegangs: ohne Schub vom Bus eigene Felder (vor rechnung.einrichten, damit die Karte sie kennt)
            if (this.seegang.contains(h.gitter.name) && h.kraftX == null) {
                h.kraftX = new double[h.zellen];
                h.kraftY = new double[h.zellen];
            }
        }
        // Rand von Stufe 0 aus tide.bc, die anderen einseitig aus der vorigen
        Hydro g0 = gitter.get(0);
        for (int s = 0; s < 4; s++) {
            final int seite = s;
            g0.rand[s] = Hydro.Rand.WASSERSTAND;
            g0.randAbstand[s] = 1;
            g0.randwert[s] = (t, x, y) -> randwert(seite, x, y, t, false);
            g0.randBoden[s] = (t, x, y) -> randwert(seite, x, y, t, true);
        }
        einseitig = new Einseitig[stufen.size()];
        for (int s = 1; s < stufen.size(); s++) einseitig[s] = new Einseitig(stufen.get(s - 1)[0], stufen.get(s)[0]);
        rechnung.einrichten(this.stufen);
    }

    private void konvergenz(int k, Gitter g) {
        kos[k] = new double[g.zellen()];
        sin[k] = new double[g.zellen()];
        IntStream.range(0, g.ny).parallel().forEach(j -> {
            for (int i = 0; i < g.nx; i++) {
                double[] ll = g.grad(g.x(i), g.y(j));
                double gam = Math.toRadians(Utm.faktoren(ll[0], ll[1], g.zone)[0]);
                kos[k][g.index(i, j)] = Math.cos(gam);
                sin[k][g.index(i, j)] = Math.sin(gam);
            }
        });
    }

    /** Wert am Rand von Stufe 0: linear zwischen den Ständen von {@code tide.bc}; ohne Wasser trocken auf {@link #land}. */
    private double randwert(int seite, double x, double y, double t, boolean boden) {
        Gitter g = gitter.get(0).gitter;
        boolean quer = seite == Hydro.WEST || seite == Hydro.OST;
        int k = quer ? (int) Math.floor((y - g.y0) / g.dy) : (int) Math.floor((x - g.x0) / g.dx);
        k = Math.max(0, Math.min((quer ? g.ny : g.nx) - 1, k));
        int s = switch (seite) {
            case Hydro.WEST -> Gitter.RAND_WEST;
            case Hydro.OST -> Gitter.RAND_OST;
            case Hydro.SUED -> Gitter.RAND_SUED;
            default -> Gitter.RAND_NORD;
        };
        int r = g.rand(s, k);
        double a = bcA.hol(2 * r), b = bcB.hol(2 * r);
        if (Double.isNaN(a) || Double.isNaN(b)) return land;
        if (boden) return -1e30;
        double w = tB > tA ? Math.max(0, Math.min(1, (t - tA) / (tB - tA))) : 1;
        return a + w * (b - a);
    }

    @Override public String name() { return "hydro"; }

    @Override public String version() { return VERSION + " (Hydro " + Hydro.VERSION + ")"; }

    @Override public void anmelden(Anmeldung a) {
        a.kopplung(kopplung);
        if (vorgabe0 == null) a.liestImIntervall("tide.bc", "m, m²/s", Ort.RAND, gitter.get(0).gitter);
        for (Hydro h : gitter) {
            Gitter g = h.gitter;
            if (aussen(h)) continue;                                      // Stufe 0 von außen: liest und schreibt nichts
            if (h.gleich != null) a.liestImIntervall("tide.eq", "m", Ort.ZELLE, g);
            if (schub) a.liestImIntervall("air.taux", "N/m²", Ort.ZELLE, g).liestImIntervall("air.tauy", "N/m²", Ort.ZELLE, g).liestImIntervall("air.p", "Pa", Ort.ZELLE, g);
            else if (antrieb) a.liestImIntervall("air.u10", "m/s", Ort.ZELLE, g).liestImIntervall("air.v10", "m/s", Ort.ZELLE, g).liestImIntervall("air.p", "Pa", Ort.ZELLE, g);
            if (seegang.contains(g.name)) a.liestImIntervall("waves.force", "N/m²", Ort.ZELLE, g);
            if (boden.contains(g.name) && bodenquelle == null) a.liest("bed.dz", "m", Ort.ZELLE, g);
            if (liestZ0(g.name)) a.liest("surf.z0", "m", Ort.ZELLE, g);
            if (spirale.contains(g.name)) a.schreibt("water.spiral", "m/s", Ort.ZELLE, g, Feld.Zahl.FLOAT);
            for (int q = 0; q < AUS.length; q++) a.schreibt(AUS[q], EINHEIT[q], Ort.ZELLE, g, Feld.Zahl.FLOAT);
            a.schreibt("water.q", "m²/s", Ort.KANTE, g, Feld.Zahl.FLOAT);
        }
    }

    @Override public void beginn(double t0, Bus bus) {
        if (vorgabe0 == null) {
            bcA = bus.lesefeld(this, "tide.bc");
            bcB = bus.lesefeld(this, "tide.bc");
            bus.lies(this, "tide.bc", t0, bcA);
            bus.lies(this, "tide.bc", t0, bcB);
        }
        tA = tB = t0;
        for (int k = 0; k < gitter.size(); k++) {
            Gitter g = gitter.get(k).gitter;
            if (aussen(gitter.get(k))) continue;
            if (schub) luft[k] = new Feld[] {bus.lesefeld(this, "air.taux", g), bus.lesefeld(this, "air.tauy", g), bus.lesefeld(this, "air.p", g)};
            else if (antrieb) luft[k] = new Feld[] {bus.lesefeld(this, "air.u10", g), bus.lesefeld(this, "air.v10", g), bus.lesefeld(this, "air.p", g)};
            if (gitter.get(k).gleich != null) gleich[k] = bus.lesefeld(this, "tide.eq", g);
            if (seegang.contains(g.name)) kraftSee[k] = bus.lesefeld(this, "waves.force", g);
            if (boden.contains(g.name) && bodenquelle == null) fBoden[k] = bus.lesefeld(this, "bed.dz", g);
            if (liestZ0(g.name)) fZ0[k] = bus.lesefeld(this, "surf.z0", g);
        }
        if (bodenquelle != null) bodenLesen(bus, t0);
        antriebLesen(bus, t0);
        for (int s = 1; s < stufen.size(); s++) einseitig[s].stand(t0, s == 1 && vorgabe0 != null ? vorgabe0.stand(t0) : rechnung.stand(s - 1));
        volumenAnfang = new double[stufen.size()];
        for (int s = 0; s < stufen.size(); s++) {
            rechnung.herunter(s);
            volumenAnfang[s] = rechnung.volumen(s);
        }
        for (int k = 0; k < gitter.size(); k++) {
            Hydro h = gitter.get(k);
            for (int c = 0; c < h.zellen; c++) nass[k][c] = h.vol[c] > hNass * h.flaeche(c);
        }
        lege(bus, t0, 0);
    }

    /** Seit 0.7: der wirksame Aufschlag am Rand der letzten Stufe (m), am Ende des letzten Intervalls. */
    public double aufschlag() { return aufschlag; }

    /** Seit 0.7: ein Wert, der dem Ziel mit höchstens {@code rate} je Sekunde über {@code dt} Sekunden folgt. */
    public static double rampe(double ist, double ziel, double rate, double dt) {
        double d = ziel - ist, m = Math.max(0, rate * dt);
        return Math.abs(d) <= m ? ziel : ist + Math.copySign(m, d);
    }

    /** Seit 0.7: Aufschlag für das Intervall [t0, t1] an den Rand der letzten Stufe (erst, wenn je ein Ziel ≠ 0 kam). */
    private void aufschlagStellen(double t0, double t1) {
        double ziel = aufschlagZiel;
        if (stufen.size() < 2 || !aufschlagGesetzt && ziel == 0) return;
        Einseitig e = einseitig[stufen.size() - 1];
        if (!aufschlagGesetzt) {
            e.zusatz(t0, aufschlag);
            aufschlagGesetzt = true;
        }
        aufschlag = rampe(aufschlag, ziel, aufschlagRampe / 3600, t1 - t0);
        e.zusatz(t1, aufschlag);
    }

    @Override public void schritt(double t0, double t1, Bus bus) {
        aufschlagStellen(t0, t1);
        // Rand von Stufe 0: Stände am Anfang und am Ende des Intervalls
        if (vorgabe0 == null) {
            bus.lies(this, "tide.bc", t0, bcA);
            bus.lies(this, "tide.bc", t1, bcB);
        }
        tA = t0;
        tB = t1;
        bodenLesen(bus, t0);
        antriebLesen(bus, 0.5 * (t0 + t1));
        if (vorgabe0 != null && stufen.size() > 1) einseitig[1].stand(t1, vorgabe0.stand(t1));
        for (int s = 0; s < stufen.size(); s++) {
            if (s == 0 && vorgabe0 != null) continue;                     // Stufe 0 von außen
            rechnung.summenNull(s);
            rechnung.bis(s, t1);
            if (s + 1 < stufen.size() && !(s == 0 && vorgabe0 != null)) einseitig[s + 1].stand(t1, rechnung.stand(s));
        }
        for (int s = 0; s < stufen.size(); s++) rechnung.herunter(s);
        lege(bus, t1, t1 - t0);
    }

    /** Liest hydro auf diesem Gitter {@code surf.z0}? Mit Boden vom Bus und {@link #rauheit}, oder über {@link #rauheitGitter}. */
    private boolean liestZ0(String g) { return rauheit && boden.contains(g) && bodenquelle == null || rauheitGitter.contains(g); }

    /** Boden und Rauheit vom Bus zur Zeit t (Beginn des Intervalls) in die Gitter aus {@link #boden}; die Rechnung erfährt es je Stufe. */
    private void bodenLesen(Bus bus, double t) {
        int k = 0;
        for (int s = 0; s < stufen.size(); s++) {
            boolean neu = false;
            for (Hydro h : stufen.get(s)) {
                if (bodenquelle != null && boden.contains(h.gitter.name)) {
                    double[] dz = new double[h.zellen];
                    if (bodenquelle.dz(t, dz)) {
                        int n = h.boden(dz);
                        bodenZellen += n;
                        neu |= n > 0;
                    }
                } else if (fBoden[k] != null) {
                    Feld f = fBoden[k];
                    bus.lies(this, "bed.dz", t, f);
                    double[] dz = new double[h.zellen];
                    for (int c = 0; c < h.zellen; c++) dz[c] = f.hol(c);
                    int n = h.boden(dz);
                    bodenZellen += n;
                    neu |= n > 0;
                }
                if (fZ0[k] != null) {
                    Feld f = fZ0[k];
                    bus.lies(this, "surf.z0", t, f);
                    double[] nn = new double[h.zellen];
                    for (int c = 0; c < h.zellen; c++) {
                        double z0 = f.hol(c);
                        nn[c] = z0 > 0 ? Math.max(nMin, manning(z0)) : 0;
                    }
                    neu |= h.rauheit(nn) > 0;
                }
                k++;
            }
            if (neu) rechnung.boden(s);
        }
    }

    /** Manning-n (s/m^(1/3)) zur Rauheitslänge z0 (m) nach Strickler: n = k_s^(1/6) / 26 mit k_s = 30 z0 [G]. */
    public static double manning(double z0) { return Math.pow(30 * z0, 1.0 / 6) / 26; }

    /** Antrieb und Gleichgewichtstide zur Zeit t in die Gitter, Wind in UTM gedreht. */
    private void antriebLesen(Bus bus, double t) {
        for (int k = 0; k < gitter.size(); k++) {
            Hydro h = gitter.get(k);
            if (aussen(h)) continue;
            if (antrieb) {
                Feld[] l = luft[k];
                for (Feld f : l) bus.lies(this, f.eintrag.name(), t, f);
                double[] ue = l[0].d, vn = l[1].d, p = l[2].d, co = kos[k], si = sin[k];
                double[] zx = schub ? h.kraftX : h.windX, zy = schub ? h.kraftY : h.windY;      // Schub (N/m²) oder Wind (m/s), gleich gedreht
                if (co == null) {
                    System.arraycopy(ue, 0, zx, 0, h.zellen);
                    System.arraycopy(vn, 0, zy, 0, h.zellen);
                } else IntStream.range(0, h.zellen).parallel().forEach(c -> {
                    zx[c] = ue[c] * co[c] - vn[c] * si[c];
                    zy[c] = vn[c] * co[c] + ue[c] * si[c];
                });
                System.arraycopy(p, 0, h.druck, 0, h.zellen);
            }
            if (h.gleich != null) {
                bus.lies(this, "tide.eq", t, gleich[k]);
                System.arraycopy(gleich[k].d, 0, h.gleich, 0, h.zellen);
            }
            if (kraftSee[k] != null) {
                // Seegang (P8c): zur Kraft an der Oberfläche dazu; ohne Schub vom Bus ist sie die Kraft allein
                Feld f = kraftSee[k];
                bus.lies(this, "waves.force", t, f);
                double an = seegangAnlauf > 0 ? Math.max(0, Math.min(1, (t - seegangAb) / seegangAnlauf)) : t >= seegangAb ? 1 : 0;
                boolean dazu = schub;
                IntStream.range(0, h.zellen).parallel().forEach(c -> {
                    double fx = f.hol(2 * c), fy = f.hol(2 * c + 1);
                    if (!Double.isFinite(fx)) fx = 0;
                    if (!Double.isFinite(fy)) fy = 0;
                    h.kraftX[c] = (dazu ? h.kraftX[c] : 0) + an * fx;
                    h.kraftY[c] = (dazu ? h.kraftY[c] : 0) + an * fy;
                });
            }
        }
        for (int s = 0; s < stufen.size(); s++) if (!(s == 0 && vorgabe0 != null)) rechnung.antrieb(s);
    }

    /** Legt water.* aller Gitter zur Zeit t ab; {@code tau}: Länge des Intervalls (0 am Anfang). */
    private void lege(Bus bus, double t, double tau) {
        for (int k = 0; k < gitter.size(); k++) {
            Hydro h = gitter.get(k);
            if (aussen(h)) continue;
            Gitter g = h.gitter;
            Feld eta = bus.schreibfeld(this, "water.eta", g), tiefe = bus.schreibfeld(this, "water.h", g), fu = bus.schreibfeld(this, "water.u", g),
                    fv = bus.schreibfeld(this, "water.v", g), fa = bus.schreibfeld(this, "water.wetAge", g), fq = bus.schreibfeld(this, "water.q", g);
            final boolean[] na = nass[k];
            final double[] al = alter[k];
            IntStream.range(0, h.zellen).parallel().forEach(c -> {
                eta.f[c] = (float) h.eta[c];
                double a = h.vol[c] > 0 ? h.benetzt(c, h.eta[c]) : 0;
                tiefe.f[c] = a > 0 ? (float) (h.vol[c] / a) : 0f;
                double[] uv = h.geschwindigkeit(c);
                fu.f[c] = (float) uv[0];
                fv.f[c] = (float) uv[1];
                boolean jetzt = h.vol[c] > hNass * h.flaeche(c);
                if (tau > 0) {
                    if (jetzt) al[c] = na[c] ? al[c] + tau : 0.5 * tau;
                    else al[c] = na[c] ? -0.5 * tau : al[c] - tau;
                }
                na[c] = jetzt;
                fa.f[c] = (float) al[c];
            });
            IntStream.range(0, h.kanten).parallel().forEach(e -> fq.f[e] = tau > 0 ? (float) (h.qSumme[e] / (tau * h.laenge(e))) : 0f);
            for (String n : new String[] {"water.eta", "water.h", "water.u", "water.v", "water.wetAge", "water.q"}) bus.lege(this, n, g, t);
            if (spirale.contains(g.name)) {
                Feld sp = bus.schreibfeld(this, "water.spiral", g);
                spirale(tiefe.f, fu.f, fv.f, g.nx, g.ny, h.dx, h.dy, sp.f);
                bus.lege(this, "water.spiral", g, t);
            }
        }
    }

    /**
     * {@code water.spiral} (m/s) = h · |u| · κ aus Tiefe und Geschwindigkeit je Zelle: κ = [u² ∂v/∂x − v² ∂u/∂y +
     * u v (∂v/∂y − ∂u/∂x)] / |u|³ (Krümmung der Stromlinie, positiv: der Strom dreht nach links), aus mittigen Differenzen
     * über nasse Nachbarn, |κ| höchstens 1 / (2 Zellweiten) [G]; unter 0,05 m/s und am Rand des Gitters 0.
     */
    public static void spirale(float[] tiefe, float[] u, float[] v, int nx, int ny, double dx, double dy, float[] ziel) {
        double grenze = 1 / (2 * Math.min(dx, dy));
        IntStream.range(0, nx * ny).parallel().forEach(c -> {
            int i = c % nx, j = c / nx;
            float w = 0;
            double uc = u[c], vc = v[c], uu = Math.hypot(uc, vc);
            if (uu > 0.05 && tiefe[c] > 0 && i > 0 && i < nx - 1 && j > 0 && j < ny - 1 && tiefe[c - 1] > 0 && tiefe[c + 1] > 0 && tiefe[c - nx] > 0 && tiefe[c + nx] > 0) {
                double vx = (v[c + 1] - v[c - 1]) / (2 * dx), ux = (u[c + 1] - u[c - 1]) / (2 * dx), uy = (u[c + nx] - u[c - nx]) / (2 * dy), vy = (v[c + nx] - v[c - nx]) / (2 * dy);
                double kr = (uc * uc * vx - vc * vc * uy + uc * vc * (vy - ux)) / (uu * uu * uu);
                kr = Math.max(-grenze, Math.min(grenze, kr));
                w = (float) (tiefe[c] * uu * kr);
            }
            ziel[c] = w;
        });
    }

    // ------------------------------------------------------------------ für Prüfungen

    /** Bilanz der Stufe s nach dem letzten Intervall: {V_Anfang, V, Zufluss, Abfluss, abgeschnitten} (m³). */
    public double[] bilanz(int s) {
        Hydro[] st = stufen.get(s);
        double ab = 0;
        for (Hydro h : st) ab += h.abgeschnitten;
        return new double[] {volumenAnfang[s], rechnung.volumen(s), st[0].zufluss, st[0].abfluss, ab};
    }

    /** Die einseitige Kopplung in die Stufe s ≥ 1. */
    public Einseitig einseitig(int s) { return einseitig[s]; }
}
