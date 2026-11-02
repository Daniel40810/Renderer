package com.dan.wave;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Eine Welle in einer ganzen Bucht (seit LituyaBay1958 Phase 11, Werkbuch 6.1, 9.4): Flachwassergleichungen mit
 * Stoßerfassung auf geschachtelten Gittern ({@link BayGrid}: HLL, zweite Ordnung, hydrostatische Rekonstruktion,
 * nass und trocken). Für Lituya grob 16 m über den Kern, mittel 4 m, fein 2 m im Inlet; jedes feinere Gitter liegt in
 * ganzen Zellen des gröberen.
 * <ul>
 * <li><b>Schachtelung:</b> das feinere Gitter rechnet in Unterschritten (Verhältnis der Zellen); seine Randzellen kommen
 * aus dem gröberen (nur nasse Zellen), im Ort bilinear und in der Zeit linear; danach bekommt das gröbere unter dem
 * feineren dessen Mittel (halb trocken: Grund passend zum Stand der nassen Zellen), und die Zellen vor der Grenze bekommen
 * den Unterschied der Flüsse (Abgleich): Das Volumen bleibt über alle Gitter genau, außer an Trockenfronten, die über eine
 * Gittergrenze laufen ({@link #clipped()}).</li>
 * <li><b>Ränder:</b> Wand, offen, Einlass ({@link Inlet}: Wasserstand und Durchfluss je Randzelle, etwa aus
 * {@code Impact.Far} – die App verdrahtet, das Package kennt {@code impact} nicht); seit Phase 13 auch als Linie im
 * Inneren ({@link #line}).</li>
 * <li><b>Abfragen</b> wie {@link Wave}: Pegel, höchster Stand und Auflauf je Ort, Höhe, Geschwindigkeit, Volumen,
 * Brechfronten für {@code spray}.</li>
 * </ul>
 * Das bisherige {@link Wave} bleibt unverändert (Rungholt, Glacier, Edersee); dieser Löser steht daneben (Paketvertrag
 * 14). Reines Java, parallel und wiederholbar; derselbe Löser in float auf der Grafikkarte: {@code wave.gl.BayGpu} (seit
 * Phase 11b), der Prozessor baut die Bucht und fragt danach ab.
 */
public final class Bay {
    /** Höhe des Grundes (m über dem Bezug); NaN: Fels. */
    @FunctionalInterface public interface Bed { double height(double x, double z); }

    /** Anfangszustand: Auslenkung über dem Spiegel, Durchfluss {qx, qz} (m²/s). */
    public interface Init {
        double eta(double x, double z);

        default double[] q(double x, double z) { return null; }
    }

    /** Einlass an einer Seite: {Wasserstand über dem Bezug, Durchfluss je Meter ins Gitter (m²/s)} zur Zeit t am Ort. */
    @FunctionalInterface public interface Inlet { double[] at(double t, double x, double z); }

    public static final int W = BayGrid.W, E = BayGrid.E, S = BayGrid.S, N = BayGrid.N;

    final List<BayGrid> grids = new ArrayList<>();
    final List<int[]> place = new ArrayList<>(); // je feineres Gitter: {i0, j0, ni, nj, r} in Zellen des gröberen
    public String name = "Bucht";
    public double level = 0, cfl = 0.3, gravity = 9.81, manning = 0.03, minSpeed = 1;
    double time;
    long steps;
    double busyMs;
    boolean built;
    final List<Gauge> gauges = new ArrayList<>();
    double nextSample = 0;
    public double sampleDt = 0.05;
    double volume0;

    /** Das gröbste Gitter: Ursprung, Zellen, Zellgröße. */
    public Bay(double x0, double z0, int nx, int nz, double dx) { grids.add(new BayGrid(nx, nz, dx, x0, z0)); }

    /**
     * Ein feineres Gitter im zuletzt angelegten: Ursprung auf dessen Zellen (i0, j0), Größe ni × nj seiner Zellen, Verhältnis
     * r (2 oder 4); mindestens drei Zellen Abstand zum Rand.
     */
    public Bay nest(int i0, int j0, int ni, int nj, int r) {
        BayGrid p = grids.get(grids.size() - 1);
        if (i0 < 3 || j0 < 3 || i0 + ni > p.nx - 3 || j0 + nj > p.nz - 3) throw new IllegalArgumentException("feineres Gitter zu nah am Rand");
        BayGrid c = new BayGrid(ni * r, nj * r, p.dx / r, p.x0 + i0 * p.dx, p.z0 + j0 * p.dx);
        for (int s = 0; s < 4; s++) c.edge[s] = BayGrid.Edge.NEST;
        grids.add(c);
        place.add(new int[]{i0, j0, ni, nj, r});
        return this;
    }

    /** Ein feineres Gitter nach Weltkoordinaten (auf Zellen des gröberen gerundet). */
    public Bay nest(double xa, double za, double xb, double zb, int r) {
        BayGrid p = grids.get(grids.size() - 1);
        int i0 = (int) Math.floor((xa - p.x0) / p.dx), j0 = (int) Math.floor((za - p.z0) / p.dx);
        int i1 = (int) Math.ceil((xb - p.x0) / p.dx), j1 = (int) Math.ceil((zb - p.z0) / p.dx);
        return nest(i0, j0, i1 - i0, j1 - j0, r);
    }

    /** Art einer Seite des gröbsten Gitters. */
    public Bay edge(int side, BayGrid.Edge e) { grids.get(0).edge[side] = e; return this; }

    /** Einlass an einer Seite des gröbsten Gitters. */
    public Bay inlet(int side, Inlet in) { BayGrid g = grids.get(0); g.edge[side] = BayGrid.Edge.INLET; g.inlet[side] = in; return this; }

    /**
     * Ein Einlass im Inneren (seit LituyaBay1958 Phase 13): entlang der Strecke (xa, za)–(xb, zb) werden in jedem Gitter
     * die Zellen, deren Mitte näher als ¾ Zelle an der Strecke liegt und die am Anfang unter Wasser sind, zu jeder Stufe
     * auf den Wasserstand und den Durchfluss aus {@code in} gesetzt, der Durchfluss in Richtung (nx, nz) (wird normiert).
     * Der Durchfluss gilt für die tiefste Stelle der Linie; flachere Zellen bekommen dieselbe Geschwindigkeit
     * q / (Tiefe + η), also weniger Durchfluss (sonst rasten dünne Schichten am Ufer davon). So kann ein Nahfeld, das
     * anderswo gerechnet ist (etwa {@code impact} im Schnitt), mitten in der Bucht übergeben werden; was hinter der Linie
     * liegt, macht der Aufrufer über den Grund zu Fels. Gibt {@code in} null zurück, ist die Linie zu dieser Zeit frei
     * (gewöhnliche Zellen). Vor {@link #build} aufrufen. Das Volumen, das über die Linie kommt
     * oder geht, zählt {@link #volume} mit, aber nicht {@link #inflowVolume}.
     */
    public Bay line(double xa, double za, double xb, double zb, double nx, double nz, Inlet in) {
        double l = Math.hypot(nx, nz);
        lines.add(new double[]{xa, za, xb, zb, nx / l, nz / l});
        lineInlets.add(in);
        return this;
    }

    final List<double[]> lines = new ArrayList<>();
    final List<Inlet> lineInlets = new ArrayList<>();
    /** je Gitter: Zellen der Linien, ihre Mitte und welche Linie. */
    int[][] forced;
    double[][] forcedXZ;
    int[][] forcedLine;
    /** je Linie die größte Tiefe unter dem Spiegel: der Durchfluss des Einlasses gilt für sie (Geschwindigkeit q / (Tiefe + η)). */
    double[] lineDepth;

    /** Durchfluss in einer Zelle der Tiefe hh aus dem Einlass (η über Bezug, q für die tiefste Stelle der Linie). */
    double lineQ(int li, double eta, double q, double hh) {
        double ref = lineDepth[li] + eta - level;
        return ref > 1e-3 ? q / ref * hh : 0;
    }

    void findForced() {
        lineDepth = new double[lines.size()];
        forced = new int[grids.size()][];
        forcedXZ = new double[grids.size()][];
        forcedLine = new int[grids.size()][];
        for (int k = 0; k < grids.size(); k++) {
            BayGrid g = grids.get(k);
            List<int[]> cells = new ArrayList<>();
            for (int li = 0; li < lines.size(); li++) {
                double[] L = lines.get(li);
                double ex = L[2] - L[0], ez = L[3] - L[1], len2 = ex * ex + ez * ez;
                for (int j = 0; j < g.nz; j++)
                    for (int i = 0; i < g.nx; i++) {
                        double x = g.cx(i), z = g.cz(j), t = len2 <= 0 ? 0 : Math.max(0, Math.min(1, ((x - L[0]) * ex + (z - L[1]) * ez) / len2));
                        double d = Math.hypot(x - L[0] - t * ex, z - L[1] - t * ez);
                        int c = g.id(i, j);
                        if (d < 0.75 * g.dx && !g.solid(c) && g.b[c] < level) cells.add(new int[]{c, li, i, j});
                    }
            }
            forced[k] = new int[cells.size()];
            forcedLine[k] = new int[cells.size()];
            forcedXZ[k] = new double[2 * cells.size()];
            for (int c = 0; c < cells.size(); c++) {
                int[] e = cells.get(c);
                lineDepth[e[1]] = Math.max(lineDepth[e[1]], level - g.b[e[0]]);
                forced[k][c] = e[0];
                forcedLine[k][c] = e[1];
                forcedXZ[k][2 * c] = g.cx(e[2]);
                forcedXZ[k][2 * c + 1] = g.cz(e[3]);
            }
        }
    }

    /** Zellen der Linien in Gitter k (Indizes wie {@link BayGrid#id}); leer ohne Linien. Für {@code wave.gl.BayGpu}. */
    public int[] forcedCells(int k) { return forced == null ? new int[0] : forced[k].clone(); }

    /** Werte der Linien in Gitter k zur Zeit t: je Zelle Wasserstand (1e30: frei), Durchfluss x, Durchfluss z. Für {@code wave.gl.BayGpu}. */
    public void forcedValues(int k, double t, float[] out) {
        for (int c = 0; c < forced[k].length; c++) {
            double[] L = lines.get(forcedLine[k][c]);
            double[] v = lineInlets.get(forcedLine[k][c]).at(t, forcedXZ[k][2 * c], forcedXZ[k][2 * c + 1]);
            if (v == null) { out[3 * c] = 1e30f; out[3 * c + 1] = out[3 * c + 2] = 0; continue; }
            double hh = Math.max(0, v[0] - grids.get(k).b[forced[k][c]]), qq = lineQ(forcedLine[k][c], v[0], v[1], hh);
            out[3 * c] = (float) v[0];
            out[3 * c + 1] = (float) (qq * L[4]);
            out[3 * c + 2] = (float) (qq * L[5]);
        }
    }

    void force(int k, double t) {
        if (forced == null || forced[k].length == 0) return;
        BayGrid g = grids.get(k);
        for (int c = 0; c < forced[k].length; c++) {
            int cc = forced[k][c];
            double[] L = lines.get(forcedLine[k][c]);
            double[] v = lineInlets.get(forcedLine[k][c]).at(t, forcedXZ[k][2 * c], forcedXZ[k][2 * c + 1]);
            if (v == null) continue;
            double hh = Math.max(0, v[0] - g.b[cc]), qq = lineQ(forcedLine[k][c], v[0], v[1], hh);
            g.h[cc] = hh;
            g.hu[cc] = hh > g.hVel ? qq * L[4] : 0;
            g.hv[cc] = hh > g.hVel ? qq * L[5] : 0;
        }
    }

    public List<BayGrid> grids() { return Collections.unmodifiableList(grids); }

    /** Grund, Spiegel und Anfangszustand für alle Gitter; das gröbere bekommt unter dem feineren dessen Mittel. */
    public synchronized Bay build(Bed bed, double level, Init init) {
        this.level = level;
        for (BayGrid g : grids) { g.gravity = gravity; g.manning = manning; g.bed(bed); }
        for (int k = grids.size() - 1; k >= 1; k--) averageBed(k);
        for (BayGrid g : grids) g.fill(level, init);
        for (int k = grids.size() - 1; k >= 1; k--) restrict(k);
        findForced();
        volume0 = volume();
        built = true;
        return this;
    }

    void averageBed(int k) {
        BayGrid p = grids.get(k - 1), c = grids.get(k);
        int[] pl = place.get(k - 1);
        int r = pl[4];
        for (int pj = 0; pj < pl[3]; pj++)
            for (int pi = 0; pi < pl[2]; pi++) {
                double s = 0;
                int m = 0;
                for (int b = 0; b < r; b++) for (int a = 0; a < r; a++) { double v = c.b[c.id(pi * r + a, pj * r + b)]; if (!Double.isNaN(v)) { s += v; m++; } }
                p.b[p.id(pl[0] + pi, pl[1] + pj)] = m == 0 ? Double.NaN : s / m;
            }
    }

    /** Das gröbere Gitter bekommt unter dem feineren dessen Mittel (Volumen und Impuls). */
    void restrict(int k) {
        BayGrid p = grids.get(k - 1), c = grids.get(k);
        int[] pl = place.get(k - 1);
        int r = pl[4];
        double w = 1.0 / (r * r);
        for (int pj = 0; pj < pl[3]; pj++)
            for (int pi = 0; pi < pl[2]; pi++) {
                double sh = 0, su = 0, sv = 0, se = 0, sb = 0;
                int wet = 0, rock = 0;
                for (int b = 0; b < r; b++)
                    for (int a = 0; a < r; a++) {
                        int cc = c.id(pi * r + a, pj * r + b);
                        if (c.solid(cc)) { rock++; continue; }
                        sh += c.h[cc]; su += c.hu[cc]; sv += c.hv[cc]; sb += c.b[cc];
                        if (c.h[cc] > c.hDry) { se += c.h[cc] + c.b[cc]; wet++; }
                    }
                int pc = p.id(pl[0] + pi, pl[1] + pj);
                if (p.solid(pc)) continue;
                p.h[pc] = sh * w; p.hu[pc] = su * w; p.hv[pc] = sv * w;
                // halb trocken: Grund so, dass der Stand dem der nassen feinen Zellen gleicht (sonst sähe das gröbere
                // Gitter eine Stufe, die es nicht gibt); ganz nass oder ganz trocken: Mittel des Grundes
                int open = r * r - rock;
                if (wet > 0 && wet < open) p.b[pc] = se / wet - p.h[pc];
                else if (open > 0) p.b[pc] = sb / open;
            }
    }

    // ------------------------------------------------------------------ Rechnung

    /** Ein Schritt des gröbsten Gitters (s); die feineren rechnen darin ihre Unterschritte. */
    public synchronized double step() {
        if (!built) throw new IllegalStateException("build() fehlt");
        long t0 = System.nanoTime();
        double dt = Double.POSITIVE_INFINITY, mul = 1;
        for (int k = 0; k < grids.size(); k++) {
            BayGrid g = grids.get(k);
            if (k > 0) mul *= place.get(k - 1)[4];
            double s = Math.max(minSpeed, g.maxSpeed(k + 1 < grids.size() ? place.get(k) : null));
            dt = Math.min(dt, cfl * g.dx / s * mul);
        }
        advance(0, time, dt);
        time += dt;
        steps++;
        busyMs += (System.nanoTime() - t0) / 1e6;
        if (time + 1e-12 >= nextSample) { sample(); nextSample += sampleDt; }
        return dt;
    }

    public void run(double tEnd) { while (time < tEnd) step(); }

    void advance(int k, double t, double dt) {
        BayGrid g = grids.get(k);
        if (k == 0) g.rk2(t, dt, tt -> { g.fillEdges(tt); force(0, tt); });
        else {
            BayGrid p = grids.get(k - 1);
            double tp0 = parentT0[k - 1], tp1 = parentT1[k - 1];
            g.rk2(t, dt, tt -> { ghostsFromParent(k, p, g, (tt - tp0) / (tp1 - tp0)); force(k, tt); });
            accumulate(k, dt);
        }
        if (k + 1 < grids.size()) {
            int r = place.get(k)[4];
            if (parentT0 == null) { parentT0 = new double[grids.size()]; parentT1 = new double[grids.size()]; }
            parentT0[k] = t;
            parentT1[k] = t + dt;
            resetAccumulators(k + 1);
            for (int s = 0; s < r; s++) advance(k + 1, t + s * dt / r, dt / r);
            if (refluxFirst) { reflux(k + 1, dt); restrict(k + 1); } else { restrict(k + 1); reflux(k + 1, dt); }
        }
    }

    double[] parentT0, parentT1;
    double refluxClip;
    /**
     * Reihenfolge Abgleich/Rückgabe und ob ein Fehlbetrag an einer Trockenfront den feinen Randzellen genommen wird. Die
     * Rückgabe hält das Volumen genau, macht die Fjordbucht der Demo aber nach gut 30 s instabil; Vorgabe daher aus: der
     * Fehlbetrag wird abgeschnitten und in {@link #clipped()} gezählt (Fjordbucht: 1,4e-5 nach 50 s).
     */
    boolean refluxFirst = false, giveBackOn = false;

    /** Randzellen des feineren Gitters aus dem gröberen (alt h0, neu h) mit dem Zeitgewicht w. */
    void ghostsFromParent(int k, BayGrid p, BayGrid c, double w) {
        for (int j = -BayGrid.G; j < c.nz + BayGrid.G; j++)
            for (int i = -BayGrid.G; i < c.nx + BayGrid.G; i++) {
                if (i >= 0 && i < c.nx && j >= 0 && j < c.nz) continue;
                int cc = c.id(i, j);
                if (c.solid(cc)) { c.h[cc] = c.hu[cc] = c.hv[cc] = 0; continue; }
                double x = c.cx(i), z = c.cz(j);
                double fi = (x - p.x0) / p.dx - 0.5, fj = (z - p.z0) / p.dx - 0.5;
                int i0 = (int) Math.floor(fi), j0 = (int) Math.floor(fj);
                double ax = fi - i0, az = fj - j0;
                // nur nasse Zellen des gröberen Gitters: trockene hätten als Stand ihren Grund und höben den Rand an
                double eo = 0, en = 0, wo = 0, wn = 0, su = 0, sv = 0, sw = 0;
                for (int b = 0; b < 2; b++)
                    for (int a = 0; a < 2; a++) {
                        int pc = p.id(i0 + a, j0 + b);
                        if (p.solid(pc)) continue;
                        double wt = (a == 0 ? 1 - ax : ax) * (b == 0 ? 1 - az : az);
                        if (wt <= 0) continue;
                        if (p.h0[pc] > p.hDry) { eo += wt * (p.h0[pc] + p.b[pc]); wo += wt; }
                        if (p.h[pc] > p.hDry) { en += wt * (p.h[pc] + p.b[pc]); wn += wt; }
                        su += wt * ((1 - w) * p.hu0[pc] + w * p.hu[pc]);
                        sv += wt * ((1 - w) * p.hv0[pc] + w * p.hv[pc]);
                        sw += wt;
                    }
                if (wo <= 0 && wn <= 0) { c.h[cc] = c.hu[cc] = c.hv[cc] = 0; continue; }
                double se, ee0 = wo > 0 ? eo / wo : Double.NaN, ee1 = wn > 0 ? en / wn : Double.NaN;
                if (Double.isNaN(ee0)) se = ee1; else if (Double.isNaN(ee1)) se = ee0; else se = (1 - w) * ee0 + w * ee1;
                sw = 1;
                double hh = Math.max(0, se - c.b[cc]);
                c.h[cc] = hh;
                c.hu[cc] = hh > c.hVel ? su : 0;
                c.hv[cc] = hh > c.hVel ? sv : 0;
            }
    }

    // Abgleich der Flüsse: je feineres Gitter und Seite die Volumina ins feinere über die Unterschritte
    final List<double[][]> acc = new ArrayList<>();

    void resetAccumulators(int k) {
        while (acc.size() < grids.size()) acc.add(null);
        BayGrid c = grids.get(k);
        acc.set(k, new double[][]{new double[c.nz], new double[c.nz], new double[c.nx], new double[c.nx]});
    }

    void accumulate(int k, double dt) {
        BayGrid c = grids.get(k);
        double[][] a = acc.get(k);
        for (int j = 0; j < c.nz; j++) { a[W][j] += c.mfx[j * (c.nx + 1)] * dt * c.dx; a[E][j] -= c.mfx[j * (c.nx + 1) + c.nx] * dt * c.dx; }
        for (int i = 0; i < c.nx; i++) { a[S][i] += c.mfz[i] * dt * c.dx; a[N][i] -= c.mfz[c.nz * c.nx + i] * dt * c.dx; }
    }

    /**
     * Die Zellen des gröberen Gitters vor der Grenze verloren, was das feinere wirklich aufnahm, nicht was sie selbst
     * rechneten. Reicht ihr Wasser dafür nicht (eine Trockenfront an der Grenze), wird abgeschnitten und gezählt (oder, mit
     * {@code giveBackOn}, den feinen Randzellen dieser Kante genommen).
     */
    void reflux(int k, double dtp) {
        BayGrid p = grids.get(k - 1), c = grids.get(k);
        int[] pl = place.get(k - 1);
        int i0 = pl[0], j0 = pl[1], ni = pl[2], nj = pl[3], r = pl[4];
        double[][] a = acc.get(k);
        double area = p.dx * p.dx;
        for (int pj = 0; pj < nj; pj++) {
            double vcW = 0, vcE = 0;
            for (int s = 0; s < r; s++) { vcW += a[W][pj * r + s]; vcE += a[E][pj * r + s]; }
            int fW = (j0 + pj) * (p.nx + 1) + i0, fE = (j0 + pj) * (p.nx + 1) + i0 + ni;
            double vpW = p.mfx[fW] * dtp * p.dx, vpE = -p.mfx[fE] * dtp * p.dx;
            giveBack(c, W, pj * r, r, adjust(p, p.id(i0 - 1, j0 + pj), (vpW - vcW) / area));
            giveBack(c, E, pj * r, r, adjust(p, p.id(i0 + ni, j0 + pj), (vpE - vcE) / area));
        }
        for (int pi = 0; pi < ni; pi++) {
            double vcS = 0, vcN = 0;
            for (int s = 0; s < r; s++) { vcS += a[S][pi * r + s]; vcN += a[N][pi * r + s]; }
            int fS = j0 * p.nx + i0 + pi, fN = (j0 + nj) * p.nx + i0 + pi;
            double vpS = p.mfz[fS] * dtp * p.dx, vpN = -p.mfz[fN] * dtp * p.dx;
            giveBack(c, S, pi * r, r, adjust(p, p.id(i0 + pi, j0 - 1), (vpS - vcS) / area));
            giveBack(c, N, pi * r, r, adjust(p, p.id(i0 + pi, j0 + nj), (vpN - vcN) / area));
        }
    }

    /** Ändert die Tiefe einer Zelle des gröberen Gitters; gibt den Fehlbetrag (m³) zurück, wenn sie dafür zu wenig hat. */
    double adjust(BayGrid p, int c, double dh) {
        if (p.solid(c)) return 0;
        double nh = p.h[c] + dh, deficit = 0;
        if (nh < 0) { deficit = -nh * p.dx * p.dx; nh = 0; }
        if (nh <= p.hDry) { p.hu[c] = p.hv[c] = 0; }
        p.h[c] = nh;
        return deficit;
    }

    /** Nimmt einen Fehlbetrag (m³) aus den feinen Randzellen einer Kante (Zellen from … from + n − 1), anteilig nach Tiefe. */
    void giveBack(BayGrid c, int side, int from, int n, double vol) {
        if (vol <= 0) return;
        if (!giveBackOn) { refluxClip += vol; return; }
        double sum = 0;
        for (int s = 0; s < n; s++) sum += c.h[edgeCell(c, side, from + s)];
        double area = c.dx * c.dx, take = Math.min(vol, sum * area);
        if (sum > 0)
            for (int s = 0; s < n; s++) {
                int cc = edgeCell(c, side, from + s);
                double hh = c.h[cc], nh = hh - take / area * hh / sum;
                if (nh <= c.hDry) { c.hu[cc] = c.hv[cc] = 0; }
                else { c.hu[cc] *= nh / hh; c.hv[cc] *= nh / hh; }
                c.h[cc] = Math.max(0, nh);
            }
        refluxClip += vol - take;
    }

    static int edgeCell(BayGrid c, int side, int k) {
        return side == W ? c.id(0, k) : side == E ? c.id(c.nx - 1, k) : side == S ? c.id(k, 0) : c.id(k, c.nz - 1);
    }

    // ------------------------------------------------------------------ Abfragen

    /** Das feinste Gitter am Ort (oder null). */
    public BayGrid gridAt(double x, double z) {
        for (int k = grids.size() - 1; k >= 0; k--) if (grids.get(k).contains(x, z)) return grids.get(k);
        return null;
    }

    /** Wasserstand über dem Spiegel (NaN: trocken oder Fels). */
    public synchronized double heightAt(double x, double z) {
        BayGrid g = gridAt(x, z);
        if (g == null) return Double.NaN;
        int i = g.cellI(x), j = g.cellJ(z), c = g.id(i, j);
        return g.solid(c) || g.h[c] <= 0.01 ? Double.NaN : g.h[c] + g.b[c] - level;
    }

    public synchronized double depthAt(double x, double z) { BayGrid g = gridAt(x, z); return g == null ? Double.NaN : g.depth(g.cellI(x), g.cellJ(z)); }

    public synchronized double[] velocityAt(double x, double z) { BayGrid g = gridAt(x, z); return g == null ? new double[2] : g.velocity(g.cellI(x), g.cellJ(z)); }

    /** Höchster Stand bisher am Ort über dem Spiegel (NaN: nie nass). */
    public synchronized double maxAt(double x, double z) { BayGrid g = gridAt(x, z); if (g == null) return Double.NaN; double e = g.etaMax[g.id(g.cellI(x), g.cellJ(z))]; return e - level; }

    /** Höchster Auflauf über dem Spiegel in Zellen, die am Anfang trocken waren, im Rechteck (alle Gitter, das feinste zählt). */
    public synchronized double runup(double xa, double za, double xb, double zb) {
        double best = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < grids.size(); k++) {
            BayGrid g = grids.get(k);
            for (int j = 0; j < g.nz; j++)
                for (int i = 0; i < g.nx; i++) {
                    double x = g.cx(i), z = g.cz(j);
                    if (x < xa || x > xb || z < za || z > zb) continue;
                    if (k + 1 < grids.size() && grids.get(k + 1).contains(x, z)) continue;
                    int c = g.id(i, j);
                    if (g.dryAtStart[c] && !Double.isNaN(g.etaMax[c])) best = Math.max(best, g.etaMax[c] - level);
                }
        }
        return best;
    }

    /** Volumen über alle Gitter (m³; das gröbere nur außerhalb des feineren). */
    public synchronized double volume() {
        double v = 0;
        for (int k = 0; k < grids.size(); k++) {
            BayGrid g = grids.get(k);
            int[] pl = k + 1 < grids.size() ? place.get(k) : null;
            double sk = 0;
            for (int j = 0; j < g.nz; j++)
                for (int i = 0; i < g.nx; i++) {
                    if (pl != null && i >= pl[0] && i < pl[0] + pl[2] && j >= pl[1] && j < pl[1] + pl[3]) continue;
                    int c = g.id(i, j);
                    if (!g.solid(c)) sk += g.h[c];
                }
            v += sk * g.dx * g.dx;
        }
        return v;
    }

    public double initialVolume() { return volume0; }

    /** Über Einlässe hereingekommen (m³). */
    public double inflowVolume() { return grids.get(0).inflowVolume; }

    /** Abgeschnittene negative Tiefen (m³), sollte null sein. */
    public double clipped() { double s = refluxClip; for (BayGrid g : grids) s += g.clipped; return s; }

    public double time() { return time; }

    /** Lage des feineren Gitters k + 1 im Gitter k: {i0, j0, ni, nj, r} (Kopie; für {@code wave.gl.BayGpu}). */
    public int[] placement(int k) { return place.get(k).clone(); }

    /** Ob {@link #build} gelaufen ist. */
    public boolean built() { return built; }

    /**
     * Übernimmt den Stand nach einer Rechnung auf der Grafikkarte ({@code wave.gl.BayGpu}, die Felder der Gitter sind
     * schon geschrieben): Zeit, Schritte, Rechenzeit und wann die Pegel das nächste Mal ablesen.
     */
    public synchronized void takeOver(double time, long steps, double busyMs, double nextSample) {
        this.time = time;
        this.steps = steps;
        this.busyMs = busyMs;
        this.nextSample = nextSample;
    }

    /** Wann die Pegel das nächste Mal ablesen (s). */
    public double nextSample() { return nextSample; }

    /** Für {@code wave.gl.BayGpu}: je Pegel ein Stand über dem Spiegel zur Zeit t (NaN: trocken), Reihenfolge wie {@link #gauges}. */
    public void record(double t, double[] eta) {
        for (int k = 0; k < gauges.size(); k++) {
            Gauge g = gauges.get(k);
            synchronized (g) {
                g.series.add(new double[]{t, eta[k]});
                if (!Double.isNaN(eta[k]) && eta[k] > g.max) { g.max = eta[k]; g.tMax = t; }
            }
        }
    }

    public long steps() { return steps; }

    public double busyMs() { return busyMs; }

    /**
     * Brechende Fronten für {@code spray}: Zellen des feinsten Gitters je Ort, deren Wasserspiegel über eine Zelle
     * stärker als {@code slope} steigt und in Laufrichtung fällt; je Treffer {x, z, Höhe über dem Spiegel}.
     */
    public synchronized List<double[]> bores(double slope) {
        List<double[]> out = new ArrayList<>();
        for (int k = 0; k < grids.size(); k++) {
            BayGrid g = grids.get(k);
            BayGrid f = k + 1 < grids.size() ? grids.get(k + 1) : null;
            for (int j = 1; j < g.nz - 1; j++)
                for (int i = 1; i < g.nx - 1; i++) {
                    if (f != null && f.contains(g.cx(i), g.cz(j))) continue;
                    int c = g.id(i, j);
                    if (g.h[c] <= 0.05) continue;
                    double ex = (g.h[c + 1] + g.b[c + 1] - g.h[c - 1] - g.b[c - 1]) / (2 * g.dx), ez = (g.h[c + g.px] + g.b[c + g.px] - g.h[c - g.px] - g.b[c - g.px]) / (2 * g.dx);
                    if (Double.isNaN(ex) || Double.isNaN(ez)) continue;
                    double u = g.vel(g.hu[c], g.h[c]), v = g.vel(g.hv[c], g.h[c]);
                    if (Math.hypot(ex, ez) > slope && ex * u + ez * v < 0) out.add(new double[]{g.cx(i), g.cz(j), g.h[c] + g.b[c] - level});
                }
        }
        return out;
    }

    // ------------------------------------------------------------------ Pegel

    public static final class Gauge {
        public final String name;
        public final double x, z;
        final List<double[]> series = new ArrayList<>();
        double max = Double.NEGATIVE_INFINITY, tMax = Double.NaN;

        Gauge(String name, double x, double z) { this.name = name; this.x = x; this.z = z; }

        public synchronized double max() { return max; }

        public synchronized double timeOfMax() { return tMax; }

        public synchronized List<double[]> series() { return new ArrayList<>(series); }

        public synchronized double firstAbove(double a) { for (double[] s : series) if (s[1] > a) return s[0]; return Double.NaN; }
    }

    public synchronized Gauge gauge(String name, double x, double z) { Gauge g = new Gauge(name, x, z); gauges.add(g); return g; }

    public List<Gauge> gauges() { return Collections.unmodifiableList(gauges); }

    void sample() {
        for (Gauge g : gauges) {
            double e = heightAt(g.x, g.z);
            synchronized (g) {
                g.series.add(new double[]{time, e});
                if (!Double.isNaN(e) && e > g.max) { g.max = e; g.tMax = time; }
            }
        }
    }

    public String describe() {
        StringBuilder b = new StringBuilder(String.format(Locale.GERMAN, "%s: %d Gitter", name, grids.size()));
        for (BayGrid g : grids) b.append(String.format(Locale.GERMAN, " · %d × %d zu %.0f m", g.nx, g.nz, g.dx));
        return b.toString();
    }
}
