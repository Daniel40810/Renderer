package com.dan.lauf;

import com.dan.core.astro.Gestirne;
import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Subgrid;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Der Wasserstand an einem Ort über alle Stände einer {@link Bild.Quelle} (Werkbuch HalligHooge Teil B, B.3): die
 * Pegelkurve für eine Zeitleiste, dazu Hoch- und Niedrigwasser und die Zeiten, in denen das Wasser über einer Höhe
 * steht (etwa über der Krone eines Deichrings: Landunter). Für ein Archiv einmal gelesen ({@link #aus}), für einen Lauf
 * neben dem Bild mit jedem neuen Stand ergänzt ({@link #ergaenze}).
 * <p>
 * <b>Ort.</b> Es gilt die Zelle mit dem tiefsten Grund im Umkreis, auf dem feinsten Gitter der Quelle, das den Ort
 * enthält – dieselbe Regel wie in {@code BildCheck} (dort für G3 und den Pegel Hooge), damit der Pegel auch bei
 * Niedrigwasser nass bleibt. Der Grund kommt aus der Subgrid-Packung des Gebiets; fehlt sie, gilt die Zelle am Ort.
 * <p>
 * <b>Marken.</b> Ein Hoch- oder Niedrigwasser ist ein Extrem, vor und nach dem der Pegel um mindestens
 * {@link #ABSTAND} m [G] zurückgeht; Ränder der Reihe und Stufen von float16 zählen so nicht. Trockene Stände zählen
 * nicht mit.
 */
public final class Pegelreihe {
    /** Kleinster Rückgang vor und nach einem Extrem (m) [G], wie in BildCheck. */
    public static final double ABSTAND = 0.1;

    public enum Art { HOCHWASSER, NIEDRIGWASSER }

    /** Ein Hoch- oder Niedrigwasser: Zeit (s seit Start) und Pegel (m NHN). */
    public record Marke(Art art, double t, double pegel) { }

    public final String name;
    public final double ost, nord;
    /** Start der Quelle (UTC); die Zeiten zählen von hier. */
    public final Instant start;
    /** Gitter und Zelle des Pegels (Index in diesem Gitter), Grund der Zelle (m NHN; NaN ohne Packung). */
    public final Gitter gitter;
    public final int zelle;
    public final double grund;
    /** Index des Gitters in der Quelle (öffentlich seit W5b, für Prüfungen). */
    public final int k;
    private final double lon, lat;
    private double[] t = new double[0], pegel = new double[0], sonne = new double[0];
    private boolean[] nass = new boolean[0];
    private int n;
    private final float[] wert = new float[3];

    private record Ort(int zelle, double grund) { }

    private static final Map<String, Ort> ORTE = new ConcurrentHashMap<>();

    private Pegelreihe(String name, double ost, double nord, Instant start, Gitter gitter, int k, Ort o) {
        this.name = name;
        this.ost = ost;
        this.nord = nord;
        this.start = start;
        this.gitter = gitter;
        this.k = k;
        zelle = o.zelle;
        grund = o.grund;
        double[] ll = gitter.grad(ost, nord);
        lon = ll[0];
        lat = ll[1];
    }

    /**
     * Legt die Reihe an und liest alle Stände, die die Quelle hält.
     *
     * @param start   Start der Quelle (UTC)
     * @param ge      Gebiet für den Grund (null: die Zelle am Ort gilt)
     * @param umkreis m um den Ort, in dem die tiefste Zelle gilt
     */
    public static Pegelreihe aus(Bild.Quelle q, Instant start, Gebiet ge, String name, double ost, double nord, double umkreis) {
        List<Gitter> gs = q.gitter();
        int k = -1;
        for (int i = 0; i < gs.size(); i++) {
            Gitter g = gs.get(i);
            if (g.lage == Gitter.Lage.UTM && g.enthaelt(ost, nord) && (k < 0 || g.dx < gs.get(k).dx)) k = i;
        }
        if (k < 0) throw new IllegalArgumentException("Pegelreihe: kein Gitter der Quelle enthält den Ort O " + ost + ", N " + nord);
        Gitter g = gs.get(k);
        String schluessel = g.name + " " + g.x0 + " " + g.y0 + " " + g.nx + " " + g.ny + " " + ost + " " + nord + " " + umkreis + " " + (ge != null);
        Ort o = ORTE.computeIfAbsent(schluessel, s -> ort(g, ge, ost, nord, umkreis));
        Pegelreihe p = new Pegelreihe(name, ost, nord, start, g, k, o);
        p.ergaenze(q);
        return p;
    }

    /** Die Zelle mit dem tiefsten Grund im Umkreis (wie BildCheck.tiefste); ohne Packung die Zelle am Ort. */
    private static Ort ort(Gitter g, Gebiet ge, double ost, double nord, double r) {
        int best = Math.max(0, Math.min(g.nx - 1, g.i(ost))) + g.nx * Math.max(0, Math.min(g.ny - 1, g.j(nord)));
        if (ge == null) return new Ort(best, Double.NaN);
        Subgrid.Packung p;
        try {
            Gitter voll = ge.gitter(g.name);
            Subgrid.Packung pv = ge.packung(g.name);
            int i0 = (int) Math.round((g.x0 - voll.x0) / voll.dx), j0 = (int) Math.round((g.y0 - voll.y0) / voll.dy);
            p = i0 == 0 && j0 == 0 && g.nx == voll.nx && g.ny == voll.ny ? pv : pv.fenster(voll, i0, j0, g.nx, g.ny);
        } catch (Exception e) {
            return new Ort(best, Double.NaN);
        }
        int n = (int) Math.ceil(r / g.dx), ci = best % g.nx, cj = best / g.nx;
        for (int j = Math.max(0, cj - n); j <= Math.min(g.ny - 1, cj + n); j++)
            for (int i = Math.max(0, ci - n); i <= Math.min(g.nx - 1, ci + n); i++) {
                double x = g.x(i) - ost, y = g.y(j) - nord;
                if (x * x + y * y > r * r) continue;
                int c = i + g.nx * j;
                if (grund(p, c) < grund(p, best)) best = c;
            }
        return new Ort(best, grund(p, best));
    }

    private static double grund(Subgrid.Packung p, int c) { return p.basis()[c] + Float.float16ToFloat(p.tabelle()[c * 3 * p.stufen()]); }

    /** Nimmt die Stände der Quelle dazu, die jünger sind als der letzte der Reihe; gibt ihre Zahl. */
    public synchronized int ergaenze(Bild.Quelle q) {
        double[] z = q.zeiten();
        int neu = 0;
        for (double ts : z) {
            if (n > 0 && ts <= t[n - 1]) continue;
            // seit W5b (F216 A): nur die Zelle (ein Archiv liest dann je Stand drei Werte statt drei Felder)
            if (!q.standZelle(ts, k, zelle, wert)) continue;
            if (n == t.length) {
                int m = Math.max(64, 2 * n);
                t = Arrays.copyOf(t, m);
                pegel = Arrays.copyOf(pegel, m);
                sonne = Arrays.copyOf(sonne, m);
                nass = Arrays.copyOf(nass, m);
            }
            t[n] = ts;
            pegel[n] = wert[0];
            nass[n] = wert[2] > 0 || wert[2] == 0 && wert[1] > 0;
            sonne[n] = Gestirne.bei(start.plusMillis(Math.round(ts * 1000))).sonne(lon, lat).hoeheGrad();
            n++;
            neu++;
        }
        return neu;
    }

    /** Seit W5: ob die Zelle zu jedem Stand nass war (für {@link Sonde}). */
    public synchronized boolean[] nassAlle() { return Arrays.copyOf(nass, n); }

    /** Zahl der Stände. */
    public synchronized int laenge() { return n; }

    /** Zeiten der Stände (s seit Start). */
    public synchronized double[] zeiten() { return Arrays.copyOf(t, n); }

    /** Pegel je Stand (m NHN); trocken: NaN. */
    public synchronized double[] pegel() {
        double[] p = Arrays.copyOf(pegel, n);
        for (int i = 0; i < n; i++) if (!nass[i]) p[i] = Double.NaN;
        return p;
    }

    /** Höhe der Sonne über der Kimm je Stand (Grad), für Tag und Nacht in der Zeitleiste. */
    public synchronized double[] sonne() { return Arrays.copyOf(sonne, n); }

    /** Pegel zur Zeit ts, linear zwischen den Ständen (m NHN); NaN außerhalb der Reihe oder trocken. */
    public synchronized double bei(double ts) {
        if (n == 0 || ts < t[0] || ts > t[n - 1]) return Double.NaN;
        int i = Arrays.binarySearch(t, 0, n, ts);
        if (i >= 0) return nass[i] ? pegel[i] : Double.NaN;
        i = -i - 2;
        if (!nass[i] || !nass[i + 1]) return Double.NaN;
        double w = (ts - t[i]) / (t[i + 1] - t[i]);
        return pegel[i] + w * (pegel[i + 1] - pegel[i]);
    }

    /** Hoch- und Niedrigwasser in der Folge der Zeit. */
    public synchronized List<Marke> marken() {
        List<Marke> m = new ArrayList<>();
        int iMax = -1, iMin = -1, erster = -1;
        // 0: noch offen, +1: zuletzt ein Niedrigwasser (es steigt), −1: zuletzt ein Hochwasser (es fällt)
        int lage = 0;
        for (int i = 0; i < n; i++) {
            if (!nass[i]) continue;
            if (erster < 0) erster = i;
            if (iMax < 0 || pegel[i] > pegel[iMax]) iMax = i;
            if (iMin < 0 || pegel[i] < pegel[iMin]) iMin = i;
            if (lage >= 0 && pegel[i] < pegel[iMax] - ABSTAND) {
                // ein Hochwasser gilt nur, wenn der Pegel auch davor um den Abstand tiefer lag (nicht der Rand der Reihe)
                if (lage > 0 || iMax != erster && pegel[iMax] - tiefsterVor(iMax) >= ABSTAND) m.add(new Marke(Art.HOCHWASSER, t[iMax], pegel[iMax]));
                lage = -1;
                iMin = i;
            } else if (lage <= 0 && pegel[i] > pegel[iMin] + ABSTAND) {
                if (lage < 0 || iMin != erster && hoechsterVor(iMin) - pegel[iMin] >= ABSTAND) m.add(new Marke(Art.NIEDRIGWASSER, t[iMin], pegel[iMin]));
                lage = 1;
                iMax = i;
            }
        }
        return m;
    }

    private double tiefsterVor(int i) {
        double v = Double.MAX_VALUE;
        for (int j = 0; j < i; j++) if (nass[j]) v = Math.min(v, pegel[j]);
        return v;
    }

    private double hoechsterVor(int i) {
        double v = -Double.MAX_VALUE;
        for (int j = 0; j < i; j++) if (nass[j]) v = Math.max(v, pegel[j]);
        return v;
    }

    /** Die Zeiten, in denen der Pegel über einer Höhe steht: je Abschnitt Beginn und Ende (s seit Start). */
    public synchronized List<double[]> ueber(double hoehe) {
        List<double[]> r = new ArrayList<>();
        double a = Double.NaN;
        for (int i = 0; i < n; i++) {
            boolean ja = nass[i] && pegel[i] > hoehe;
            if (ja && Double.isNaN(a)) a = t[i];
            if (!ja && !Double.isNaN(a)) {
                r.add(new double[] {a, t[i - 1]});
                a = Double.NaN;
            }
        }
        if (!Double.isNaN(a)) r.add(new double[] {a, t[n - 1]});
        return r;
    }

    /** Der höchste Stand der Reihe (null: keine nassen Stände). */
    public synchronized Marke hoechster() {
        int b = -1;
        for (int i = 0; i < n; i++) if (nass[i] && (b < 0 || pegel[i] > pegel[b])) b = i;
        return b < 0 ? null : new Marke(Art.HOCHWASSER, t[b], pegel[b]);
    }

    @Override public synchronized String toString() {
        return String.format(java.util.Locale.GERMAN, "Pegel %s: Zelle %d, %d von %s, Grund %.2f m NHN, %d Stände", name, zelle % gitter.nx, zelle / gitter.nx, gitter.name, grund, n);
    }
}
