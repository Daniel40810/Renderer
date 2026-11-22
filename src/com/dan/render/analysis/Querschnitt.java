package com.dan.render.analysis;

import com.dan.core.gitter.Gitter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

/**
 * Ein Querschnitt durch das Wasser (seit P11c3, Werkbuch HalligHooge Teil A, 12.12): eine Strecke von A nach B auf
 * einem Gitter der Strömung.
 * <ul>
 * <li><b>Durchfluss genau:</b> Die Strecke wird als Treppe über die Ecken der Zellen gelegt (A und B auf die nächste Ecke,
 * je Schritt eine Kante nach Ost, West, Nord oder Süd, so nah an der Geraden wie möglich). Der Fluss durch die Treppe ist
 * die Summe von {@code water.q} über die Kanten, die sie bildet, mal deren Länge – derselbe Fluss, den hydro rechnet.
 * Positiv zählt, was von A nach B gesehen nach links fließt.</li>
 * <li><b>Profil:</b> auf der Geraden je halbe Zelle Grund (von außen, etwa das Gelände), Wasserstand, Geschwindigkeit
 * quer zur Strecke (nach links positiv) und Trübung; bilinear zwischen den Mitten der Zellen, nur wo nass.</li>
 * <li><b>Bilanz:</b> aus der Reihe Q(t) das Volumen, das in einem Zeitfenster nach links (Flut, wenn links das Becken
 * liegt) und nach rechts floss.</li>
 * </ul>
 * Reines Java ohne Karte.
 */
public final class Querschnitt {
    public final Gitter gitter;
    /** Die Enden auf den Ecken des Gitters (UTM). */
    public final double ostA, nordA, ostB, nordB;
    /** Länge der Geraden (m). */
    public final double laenge;
    /** Die Kanten der Treppe (Index in {@code water.q}) und je Kante Länge mal Vorzeichen (m). */
    public final int[] kante;
    public final double[] gewicht;

    private Querschnitt(Gitter g, int ia, int ja, int ib, int jb) {
        gitter = g;
        ostA = g.x0 + ia * g.dx;
        nordA = g.y0 + ja * g.dy;
        ostB = g.x0 + ib * g.dx;
        nordB = g.y0 + jb * g.dy;
        laenge = Math.hypot(ostB - ostA, nordB - nordA);
        int nx = g.nx, kantenX = (nx + 1) * g.ny;
        List<int[]> k = new ArrayList<>();
        List<Double> w = new ArrayList<>();
        int i = ia, j = ja;
        double dx = ib - ia, dy = jb - ja;
        while (i != ib || j != jb) {
            // der nächste Schritt: der, dessen Endpunkt der Geraden (in Zellen) am nächsten liegt
            int si = Integer.signum(ib - i), sj = Integer.signum(jb - j);
            double ax = Double.POSITIVE_INFINITY, ay = Double.POSITIVE_INFINITY;
            if (si != 0) ax = Math.abs(dy * (i + si - ia) - dx * (j - ja));
            if (sj != 0) ay = Math.abs(dy * (i - ia) - dx * (j + sj - ja));
            if (ax <= ay) {
                // Schritt nach Ost oder West entlang der Ecken: die Kante zwischen den Zellen (·, j − 1) und (·, j) in y
                int spalte = si > 0 ? i : i - 1;
                k.add(new int[] {kantenX + spalte + nx * j});
                w.add(si > 0 ? g.dx : -g.dx);                    // nach Ost: links ist Nord (+q in y)
                i += si;
            } else {
                // Schritt nach Nord oder Süd: die Kante zwischen den Zellen (i − 1, ·) und (i, ·) in x
                int zeile = sj > 0 ? j : j - 1;
                k.add(new int[] {i + (nx + 1) * zeile});
                w.add(sj > 0 ? -g.dy : g.dy);                    // nach Nord: links ist West (−q in x)
                j += sj;
            }
        }
        kante = new int[k.size()];
        gewicht = new double[k.size()];
        for (int n = 0; n < kante.length; n++) {
            kante[n] = k.get(n)[0];
            gewicht[n] = w.get(n);
        }
    }

    /** Ein Querschnitt von A nach B (UTM); die Enden rücken auf die nächste Ecke im Gitter. */
    public static Querschnitt von(Gitter g, double ostA, double nordA, double ostB, double nordB) {
        int ia = ecke((ostA - g.x0) / g.dx, g.nx), ja = ecke((nordA - g.y0) / g.dy, g.ny);
        int ib = ecke((ostB - g.x0) / g.dx, g.nx), jb = ecke((nordB - g.y0) / g.dy, g.ny);
        if (ia == ib && ja == jb) throw new IllegalArgumentException("Querschnitt: A und B fallen auf dieselbe Ecke");
        return new Querschnitt(g, ia, ja, ib, jb);
    }

    private static int ecke(double x, int n) { return (int) Math.max(0, Math.min(n, Math.round(x))); }

    /** Durchfluss (m³/s, nach links positiv) aus dem Fluss je Kante {@code q} (m²/s, positiv nach Ost und Nord). */
    public double durchfluss(float[] q) {
        double s = 0;
        for (int n = 0; n < kante.length; n++) s += q[kante[n]] * gewicht[n];
        return s;
    }

    /** Das Profil: je Punkt Abstand von A (m), Grund (m NHN), Wasserstand (NaN: trocken), Geschwindigkeit quer (m/s), Trübung (g/m³ oder NaN). */
    public record Profil(double[] s, double[] grund, double[] eta, double[] quer, double[] truebung) {
        public int punkte() { return s.length; }
    }

    /**
     * Das Profil zu einem Stand: Felder je Zelle (u, v in m/s nach Ost und Nord, η, h in m; Trübung darf null sein),
     * {@code grund} gibt die Höhe des Bodens am Ort (UTM). Trocken ist, wo h höchstens {@code hMin} ist.
     */
    public Profil profil(float[] u, float[] v, float[] eta, float[] h, float[] truebung, double hMin, DoubleBinaryOperator grund) {
        int n = (int) Math.max(2, Math.ceil(laenge / (0.5 * Math.min(gitter.dx, gitter.dy)))) + 1;
        double nxl = -(nordB - nordA) / laenge, nyl = (ostB - ostA) / laenge;   // nach links
        double[] s = new double[n], gr = new double[n], et = new double[n], qu = new double[n], tr = new double[n];
        for (int k = 0; k < n; k++) {
            double f = (double) k / (n - 1), o = ostA + f * (ostB - ostA), no = nordA + f * (nordB - nordA);
            s[k] = f * laenge;
            gr[k] = grund.applyAsDouble(o, no);
            double hh = bilinear(h, o, no);
            if (hh > hMin) {
                et[k] = bilinear(eta, o, no);
                qu[k] = (u == null ? 0 : bilinear(u, o, no) * nxl) + (v == null ? 0 : bilinear(v, o, no) * nyl);
                tr[k] = truebung == null ? Double.NaN : bilinear(truebung, o, no);
            } else {
                et[k] = qu[k] = Double.NaN;
                tr[k] = Double.NaN;
            }
        }
        return new Profil(s, gr, et, qu, tr);
    }

    /** Bilinear zwischen den Mitten der Zellen, am Rand festgehalten. */
    double bilinear(float[] f, double ost, double nord) {
        double x = (ost - gitter.x0) / gitter.dx - 0.5, y = (nord - gitter.y0) / gitter.dy - 0.5;
        x = Math.max(0, Math.min(gitter.nx - 1, x));
        y = Math.max(0, Math.min(gitter.ny - 1, y));
        int i0 = (int) Math.floor(x), j0 = (int) Math.floor(y), i1 = Math.min(i0 + 1, gitter.nx - 1), j1 = Math.min(j0 + 1, gitter.ny - 1);
        double tx = x - i0, ty = y - j0;
        int nx = gitter.nx;
        double a = f[i0 + nx * j0] + tx * (f[i1 + nx * j0] - f[i0 + nx * j0]);
        double b = f[i0 + nx * j1] + tx * (f[i1 + nx * j1] - f[i0 + nx * j1]);
        return a + ty * (b - a);
    }

    /** Volumen durch den Schnitt im Fenster: nach links, nach rechts (beide positiv, m³), dazu netto (links − rechts). */
    public record Bilanz(double links, double rechts, double netto, double von, double bis) { }

    /**
     * Die Bilanz aus der Reihe Q(t): {@code zeiten} sind die Enden der Intervalle (s), {@code q} das Mittel über das
     * Intervall bis dorthin (m³/s), wie {@code water.q} abgelegt ist. Gezählt werden die Intervalle, deren Ende in
     * (von, bis] liegt.
     */
    public static Bilanz bilanz(double[] zeiten, double[] q, double von, double bis) {
        double l = 0, r = 0;
        for (int k = 1; k < zeiten.length; k++) {
            if (zeiten[k] <= von || zeiten[k] > bis || Double.isNaN(q[k])) continue;
            double v = q[k] * (zeiten[k] - zeiten[k - 1]);
            if (v > 0) l += v;
            else r -= v;
        }
        return new Bilanz(l, r, l - r, von, bis);
    }
}
