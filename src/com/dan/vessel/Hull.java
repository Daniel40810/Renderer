package com.dan.vessel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Form eines Holzkutters der 1950er (Troller, Seiner, Gillnetter) aus wenigen Maßen, und daraus die Proben für den
 * Starrkörper ({@link Boat}). Achsenkreuz des Rumpfs: s nach vorn (m, 0 mittschiffs), y nach oben (m, 0 auf der
 * Unterkante des Kiels), q nach Steuerbord (m).
 * <ul>
 * <li><b>Wasserlinienriss:</b> voll mittschiffs, nach vorn spitz (Steven), achtern bis auf den Spiegel ({@link #transom})
 * eingezogen;</li>
 * <li><b>Spant:</b> runder Boden, die halbe Breite wächst mit (y / Kimmhöhe)<sup>0,4</sup> bis zur vollen Breite;</li>
 * <li><b>Kiel:</b> gerade, vorn im Vorfuß bis zum Steven angehoben;</li>
 * <li><b>Deck:</b> Seitenhöhe mittschiffs, Sprung nach vorn und achtern;</li>
 * <li><b>Steuerhaus:</b> ein Kasten auf dem Deck (trägt zu einem Teil mit, die Fenster brechen [G]).</li>
 * </ul>
 * Die Proben sind Würfel eines Rasters im Inneren (Mitte im Rumpf); ihre Summe unter der Konstruktionswasserlinie ist die
 * Verdrängung, aus der {@link #massDesign()} die Masse macht, wenn keine gegeben ist.
 */
public final class Hull {
    public final String name;
    /** Länge über alles, Breite, Seitenhöhe mittschiffs, Konstruktionstiefgang (m). */
    public final double loa, beam, depth, draft;
    /** Sprung vorn und achtern (m über der Seitenhöhe mittschiffs), Breite des Spiegels (Anteil der Breite). */
    public double sheerFore = 0.55, sheerAft = 0.2, transom = 0.62;
    /** Höhe der Kimm als Anteil des Tiefgangs, Form des Spants (Hochzahl: 0,4 rund und voll, 1 ein V), Beginn des Vorfußes (Anteil der halben Länge nach vorn). */
    public double bilge = 1.0, deadrise = 1.0, forefoot = 0.45;
    /** Kiel unter dem Rumpf: Höhe als Anteil des Tiefgangs, Breite (m) [G]. */
    public double keelDepth = 0.42, keelWidth = 0.32;
    /** Steuerhaus: von s0 bis s1 (m), Breite, Höhe über Deck (m); trägt mit diesem Anteil, wenn es eintaucht [G]. */
    public double houseS0, houseS1, houseWidth, houseHeight = 2.0, houseBuoyancy = 0.3;
    /** Mast (s, Höhe über Deck), Trollstangen (Länge, m). */
    public double mastS, mastHeight = 7.5, poleLength = 11;
    /** Raster der Proben: Zellen über die Länge (Breite und Höhe folgen). */
    public int cellsLong = 40;

    // Proben (Mitten im Achsenkreuz des Rumpfs), Volumen, Art
    double[] ps, py, pq;
    double cell;
    boolean[] surface, house, deckTop;
    int n;

    public Hull(String name, double loa, double beam, double depth, double draft) {
        this.name = name;
        this.loa = loa;
        this.beam = beam;
        this.depth = depth;
        this.draft = draft;
        houseS0 = -0.05 * loa;
        houseS1 = 0.18 * loa;
        houseWidth = 0.62 * beam;
        mastS = 0.25 * loa;
    }

    // ------------------------------------------------------------------ Form

    /** Anteil der halben Breite im Wasserlinienriss bei s (0 … 1). */
    public double plan(double s) {
        double x = 2 * s / loa;
        if (x > 1 || x < -1) return 0;
        if (x > 0.12) { double t = (x - 0.12) / 0.88; return Math.sqrt(Math.max(0, 1 - t * t)) * (1 - 0.1 * t); }
        if (x < -0.25) { double t = (-x - 0.25) / 0.75; return 1 - (1 - transom) * t * t; }
        return 1;
    }

    /** Höhe der Unterkante des Kiels bei s (m): gerade, im Vorfuß bis zum Steven angehoben. */
    public double keel(double s) {
        double x = 2 * s / loa;
        if (x <= forefoot) return 0;
        double t = Math.min(1, (x - forefoot) / (1 - forefoot));
        return draft * 1.05 * t * t;
    }

    /** Höhe der Deckskante bei s (m über der Kielunterkante). */
    public double deck(double s) {
        double x = 2 * s / loa;
        return depth + sheerFore * Math.max(0, x) * Math.max(0, x) + sheerAft * Math.max(0, -x) * Math.max(0, -x);
    }

    /** Halbe Breite des Rumpfs bei s in der Höhe y (m); 0 außerhalb. */
    public double halfBreadth(double s, double y) {
        double k = keel(s);
        if (y < k || y > deck(s)) return 0;
        double kd = keelDepth * draft * Math.max(0, 1 - Math.max(0, 2 * s / loa - forefoot) / (1 - forefoot));
        double y0 = k + kd;                                   // Unterkante des Rumpfs über dem Kiel
        double keelHalf = Math.abs(2 * s / loa) < 0.97 ? keelWidth / 2 : 0;   // der Kiel läuft durch bis zum Deck (Kielschwein)
        double canoe = Math.max(0.05, draft - y0);
        double yb = Math.max(0.05, bilge * canoe);
        double sec = y <= y0 ? 0 : Math.min(1, Math.pow((y - y0) / yb, deadrise));
        double flare = 1 + 0.04 * Math.max(0, (y - draft) / Math.max(0.1, depth - draft));
        return Math.max(keelHalf, 0.5 * beam * plan(s) * sec * flare);
    }

    /** Ob (s, y, q) im Steuerhaus liegt. */
    public boolean inHouse(double s, double y, double q) {
        double d = deck(0.5 * (houseS0 + houseS1));
        return s >= houseS0 && s <= houseS1 && Math.abs(q) <= houseWidth / 2 && y > d && y <= d + houseHeight;
    }

    // ------------------------------------------------------------------ Proben

    /** Legt die Proben an (einmal; wird bei Bedarf von {@link Boat} gerufen). */
    public synchronized void build() {
        if (ps != null) return;
        cell = loa / cellsLong;
        double top = deck(-loa / 2) + houseHeight + 0.5;
        top = Math.max(top, deck(loa / 2));
        int nx = cellsLong, nq = Math.max(4, (int) Math.ceil(beam * 1.1 / cell)), ny = (int) Math.ceil((Math.max(deck(loa / 2), deck(0) + houseHeight)) / cell) + 1;
        List<double[]> l = new ArrayList<>();
        boolean[][][] in = new boolean[nx][ny][nq];
        double q0 = -nq * cell / 2;
        for (int i = 0; i < nx; i++)
            for (int j = 0; j < ny; j++)
                for (int k = 0; k < nq; k++) {
                    double s = -loa / 2 + (i + 0.5) * cell, y = (j + 0.5) * cell, q = q0 + (k + 0.5) * cell;
                    in[i][j][k] = Math.abs(q) < halfBreadth(s, y) || inHouse(s, y, q);
                }
        for (int i = 0; i < nx; i++)
            for (int j = 0; j < ny; j++)
                for (int k = 0; k < nq; k++) {
                    if (!in[i][j][k]) continue;
                    double s = -loa / 2 + (i + 0.5) * cell, y = (j + 0.5) * cell, q = q0 + (k + 0.5) * cell;
                    boolean surf = i == 0 || i == nx - 1 || j == 0 || j == ny - 1 || k == 0 || k == nq - 1
                            || !in[i - 1][j][k] || !in[i + 1][j][k] || !in[i][j - 1][k] || !in[i][j + 1][k] || !in[i][j][k - 1] || !in[i][j][k + 1];
                    boolean h = inHouse(s, y, q) && !(Math.abs(q) < halfBreadth(s, y));
                    boolean dt = !h && (j == ny - 1 || !in[i][j + 1][k] || inHouse(s, y + cell, q)) && y + cell / 2 >= deck(s) - cell;
                    l.add(new double[]{s, y, q, surf ? 1 : 0, h ? 1 : 0, dt ? 1 : 0});
                }
        n = l.size();
        ps = new double[n]; py = new double[n]; pq = new double[n];
        surface = new boolean[n]; house = new boolean[n]; deckTop = new boolean[n];
        for (int p = 0; p < n; p++) {
            double[] r = l.get(p);
            ps[p] = r[0]; py[p] = r[1]; pq[p] = r[2];
            surface[p] = r[3] > 0; house[p] = r[4] > 0; deckTop[p] = r[5] > 0;
        }
    }

    public int probes() { build(); return n; }

    public double cell() { build(); return cell; }

    /** Volumen des Rumpfs unter der Höhe y über dem Kiel (m³, ohne Steuerhaus), aufrecht. */
    public double volumeBelow(double y) {
        build();
        double v = 0, c3 = cell * cell * cell;
        for (int p = 0; p < n; p++) if (!house[p]) v += c3 * frac(y - (py[p] - cell / 2), cell);
        return v;
    }

    /** Volumen des ganzen Rumpfs bis zum Deck (m³). */
    public double volume() { return volumeBelow(1e9); }

    /** Masse, mit der der Rumpf im Seewasser (1025 kg/m³) auf dem Konstruktionstiefgang schwimmt (kg). */
    public double massDesign() { return 1025 * volumeBelow(draft); }

    /** Längslage des Auftriebs auf dem Konstruktionstiefgang (m). */
    public double lcb() {
        build();
        double v = 0, m = 0, c3 = cell * cell * cell;
        for (int p = 0; p < n; p++) {
            if (house[p]) continue;
            double f = c3 * frac(draft - (py[p] - cell / 2), cell);
            v += f;
            m += f * ps[p];
        }
        return v > 0 ? m / v : 0;
    }

    /** Fläche der Wasserlinie auf dem Tiefgang (m², aus der Form). */
    public double waterplane() {
        double a = 0, ds = loa / 400;
        for (double s = -loa / 2 + ds / 2; s < loa / 2; s += ds) a += 2 * halfBreadth(s, draft) * ds;
        return a;
    }

    /** Querträgheitsmoment der Wasserlinie (m⁴) für BM = I / V. */
    public double waterplaneInertia() {
        double a = 0, ds = loa / 400;
        for (double s = -loa / 2 + ds / 2; s < loa / 2; s += ds) { double b = halfBreadth(s, draft); a += 2.0 / 3 * b * b * b * ds; }
        return a;
    }

    /** Höhe des Formschwerpunkts unter dem Tiefgang (KB, m). */
    public double kb() {
        build();
        double v = 0, m = 0, c3 = cell * cell * cell;
        for (int p = 0; p < n; p++) {
            if (house[p]) continue;
            double f = c3 * frac(draft - (py[p] - cell / 2), cell);
            v += f;
            m += f * Math.min(py[p], draft);
        }
        return v > 0 ? m / v : 0;
    }

    static double frac(double d, double c) { return d <= 0 ? 0 : d >= c ? 1 : d / c; }

    /** Völligkeit auf dem Tiefgang: V / (L B T). */
    public double block() { return volumeBelow(draft) / (loa * beam * draft); }

    /** Klüse am Bug (s, y, q), wo die Ankerkette ausläuft. */
    public double[] fairlead() { double s = 0.46 * loa; return new double[]{s, deck(s) + 0.1, 0}; }

    /** Schraube (s, y, q). */
    public double[] propeller() { return new double[]{-0.44 * loa, 0.35 * draft, 0}; }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "%s: %.1f × %.2f m, Seitenhöhe %.2f m, Tiefgang %.2f m", name, loa, beam, depth, draft);
    }
}
