package com.dan.wave;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Ein Gitter des Lösers {@link Bay} (seit LituyaBay1958 Phase 11, Werkbuch 6.1, 9.4): Flachwassergleichungen in
 * Flussform, finite Volumen mit den Werten in der Zellmitte (h, hu, hv), Flüsse nach HLL (stoßerfassend), zweite Ordnung
 * mit MUSCL (minmod) und dem Zeitschritt nach Heun. Der Grund geht über die hydrostatische Rekonstruktion nach Audusse
 * (2004) ein: Ein ruhender See über steilem Grund bleibt genau in Ruhe, Ufer werden ohne Sonderfälle nass und trocken,
 * die Wassertiefe bleibt nicht negativ. Reibung nach Manning halb implizit. Zwei Lagen Randzellen je Seite; jede Seite ist
 * Wand, offen, Einlass oder Rand zu einem gröberen Gitter. NaN als Grund ist fester Fels (Wand).
 * <p>
 * Parallel über die Zeilen, jede Zelle und jede Kante wird für sich gerechnet: zwei Läufe sind gleich.
 */
public final class BayGrid {
    /** Art einer Seite. */
    public enum Edge { WALL, OPEN, INLET, NEST }

    public static final int W = 0, E = 1, S = 2, N = 3, G = 2;

    public final int nx, nz, px;
    public final double dx, x0, z0;
    public double gravity = 9.81, manning = 0.03, hDry = 1e-6, hVel = 1e-3, frictionDepth = 1e9;
    /**
     * Höchste Geschwindigkeit (m/s) nach jeder Stufe [G, seit LituyaBay1958 Phase 13a]: dünne Filme hoch am Hang können in
     * float (Grafikkarte) durch Auslöschung in η − b Geschwindigkeiten von 10⁵ m/s bekommen und den Lauf sprengen. 150 m/s
     * liegt weit über allem Wirklichen (Felssturz 110 m/s, Welle im Inlet 50–100 m/s).
     */
    public double maxVelocity = 150;
    final Edge[] edge = {Edge.WALL, Edge.WALL, Edge.WALL, Edge.WALL};
    final Bay.Inlet[] inlet = new Bay.Inlet[4];

    // Zustand (mit Randzellen)
    final double[] b, h, hu, hv;
    // Arbeit
    final double[] h0, hu0, hv0, dh, dhu, dhv;
    final double[] sH, sE, sU, sV, tH, tE, tU, tV; // Steigungen in x (s) und z (t)
    final double[] fx, fz;      // Flüsse je Kante, 3 Komponenten
    final double[] pxL, pxR, pzL, pzR; // Druckkorrekturen nach Audusse je Kante (links/unten, rechts/oben)
    /** Massenfluss je Kante, gemittelt über die beiden Stufen (für den Abgleich an Gittergrenzen). */
    final double[] mfx, mfz;
    /** Höchster Stand je Zelle (NaN: nie nass) und ob die Zelle am Anfang trocken war. */
    final double[] etaMax;
    final boolean[] dryAtStart;
    double clipped, inflowVolume;
    long busyNs, stepCount;

    public BayGrid(int nx, int nz, double dx, double x0, double z0) {
        this.nx = nx;
        this.nz = nz;
        this.dx = dx;
        this.x0 = x0;
        this.z0 = z0;
        px = nx + 2 * G;
        int n = px * (nz + 2 * G);
        b = new double[n]; h = new double[n]; hu = new double[n]; hv = new double[n];
        h0 = new double[n]; hu0 = new double[n]; hv0 = new double[n];
        dh = new double[n]; dhu = new double[n]; dhv = new double[n];
        sH = new double[n]; sE = new double[n]; sU = new double[n]; sV = new double[n];
        tH = new double[n]; tE = new double[n]; tU = new double[n]; tV = new double[n];
        fx = new double[3 * (nx + 1) * nz]; fz = new double[3 * nx * (nz + 1)];
        pxL = new double[(nx + 1) * nz]; pxR = new double[(nx + 1) * nz]; pzL = new double[nx * (nz + 1)]; pzR = new double[nx * (nz + 1)];
        mfx = new double[(nx + 1) * nz]; mfz = new double[nx * (nz + 1)];
        etaMax = new double[n];
        Arrays.fill(etaMax, Double.NaN);
        dryAtStart = new boolean[n];
    }

    /** Index der Zelle (i, j), Randzellen ab −2. */
    public int id(int i, int j) { return (j + G) * px + i + G; }

    public double cx(int i) { return x0 + (i + 0.5) * dx; }

    public double cz(int j) { return z0 + (j + 0.5) * dx; }

    public boolean contains(double x, double z) { return x >= x0 && z >= z0 && x < x0 + nx * dx && z < z0 + nz * dx; }

    public Edge edge(int side) { return edge[side]; }

    /** Grund für alle Zellen samt Rand (Höhe über dem Bezug; NaN: Fels). */
    void bed(Bay.Bed bed) {
        for (int j = -G; j < nz + G; j++) for (int i = -G; i < nx + G; i++) b[id(i, j)] = bed.height(cx(i), cz(j));
    }

    /** Wasser bis zum Spiegel {@code level} (mit Auslenkung und Geschwindigkeit aus init). */
    void fill(double level, Bay.Init init) {
        for (int j = -G; j < nz + G; j++)
            for (int i = -G; i < nx + G; i++) {
                int c = id(i, j);
                double bz = b[c];
                if (Double.isNaN(bz)) { h[c] = hu[c] = hv[c] = 0; dryAtStart[c] = false; continue; }
                double e = level + (init == null ? 0 : init.eta(cx(i), cz(j)));
                h[c] = Math.max(0, e - bz);
                dryAtStart[c] = level - bz <= 0;
                double[] q = init == null ? null : init.q(cx(i), cz(j));
                hu[c] = h[c] > 0 && q != null ? q[0] : 0;
                hv[c] = h[c] > 0 && q != null ? q[1] : 0;
            }
    }

    boolean solid(int c) { return Double.isNaN(b[c]); }

    // ------------------------------------------------------------------ Ränder

    /** Randzellen der Seiten WALL, OPEN, INLET zur Zeit t (NEST füllt {@link Bay}). */
    void fillEdges(double t) {
        for (int side = 0; side < 4; side++) {
            Edge e = edge[side];
            if (e == Edge.NEST) continue;
            boolean xs = side == W || side == E;
            int m = xs ? nz : nx;
            for (int k = -G; k < m + G; k++) {
                for (int l = 1; l <= G; l++) {
                    int gi, gj, ii, jj;
                    if (side == W) { gi = -l; gj = k; ii = l - 1; jj = k; }
                    else if (side == E) { gi = nx - 1 + l; gj = k; ii = nx - l; jj = k; }
                    else if (side == S) { gi = k; gj = -l; ii = k; jj = l - 1; }
                    else { gi = k; gj = nz - 1 + l; ii = k; jj = nz - l; }
                    if (xs && (k < 0 || k >= nz) || !xs && (k < 0 || k >= nx)) {
                        // Ecken: von der Nachbarzelle innen
                        ii = Math.max(0, Math.min(nx - 1, ii));
                        jj = Math.max(0, Math.min(nz - 1, jj));
                    }
                    int g = id(gi, gj), c = id(ii, jj);
                    if (e == Edge.INLET && inlet[side] != null && !solid(g)) {
                        double[] v = inlet[side].at(t, cx(gi), cz(gj));
                        double hh = Math.max(0, v[0] - b[g]);
                        h[g] = hh;
                        double qn = hh > hVel ? v[1] : 0, sign = side == W || side == S ? 1 : -1;
                        if (xs) { hu[g] = sign * qn; hv[g] = 0; } else { hv[g] = sign * qn; hu[g] = 0; }
                        continue;
                    }
                    // Wand und offen: Grund und Tiefe der gespiegelten (bzw. nächsten) Zelle, so bleibt ein ruhender See in Ruhe
                    b[g] = b[c];
                    if (solid(g)) { h[g] = hu[g] = hv[g] = 0; continue; }
                    h[g] = h[c];
                    hu[g] = solid(c) ? 0 : hu[c];
                    hv[g] = solid(c) ? 0 : hv[c];
                    if (e == Edge.WALL) { if (xs) hu[g] = -hu[g]; else hv[g] = -hv[g]; }
                    if (h[g] <= hDry) { hu[g] = hv[g] = 0; }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Rechnung

    static double minmod(double a, double b) { return a * b <= 0 ? 0 : (Math.abs(a) < Math.abs(b) ? a : b); }

    double vel(double q, double hh) { return hh > hVel ? q / hh : 0; }

    /** Steigungen (minmod) für die Zellen −1 … n in x und z. */
    void slopes() {
        IntStream.range(-1, nz + 1).parallel().forEach(j -> {
            for (int i = -1; i <= nx; i++) {
                int c = id(i, j);
                if (solid(c)) { sH[c] = sE[c] = sU[c] = sV[c] = tH[c] = tE[c] = tU[c] = tV[c] = 0; continue; }
                int l = c - 1, r = c + 1, d = c - px, u = c + px;
                double hc = h[c], ec = hc + b[c], uc = vel(hu[c], hc), vc = vel(hv[c], hc);
                if (solid(l) || solid(r)) { sH[c] = sE[c] = sU[c] = sV[c] = 0; }
                else {
                    sH[c] = minmod(hc - h[l], h[r] - hc);
                    sE[c] = minmod(ec - h[l] - b[l], h[r] + b[r] - ec);
                    sU[c] = minmod(uc - vel(hu[l], h[l]), vel(hu[r], h[r]) - uc);
                    sV[c] = minmod(vc - vel(hv[l], h[l]), vel(hv[r], h[r]) - vc);
                }
                if (solid(d) || solid(u)) { tH[c] = tE[c] = tU[c] = tV[c] = 0; }
                else {
                    tH[c] = minmod(hc - h[d], h[u] - hc);
                    tE[c] = minmod(ec - h[d] - b[d], h[u] + b[u] - ec);
                    tU[c] = minmod(uc - vel(hu[d], h[d]), vel(hu[u], h[u]) - uc);
                    tV[c] = minmod(vc - vel(hv[d], h[d]), vel(hv[u], h[u]) - vc);
                }
                if (hc <= hVel) { sU[c] = sV[c] = tU[c] = tV[c] = 0; }
            }
        });
    }

    /**
     * Flüsse über eine Kante: Zustand links (hL, ηL, unL, utL) und rechts; schreibt Masse, Normal-, Tangentialimpuls nach
     * out[0..2] und die Druckkorrekturen nach out[3] (links) und out[4] (rechts).
     */
    void hll(double hL, double eL, double unL, double utL, double hR, double eR, double unR, double utR, double[] out) {
        double bL = eL - hL, bR = eR - hR, bf = Math.max(bL, bR);
        double hLs = Math.max(0, eL - bf), hRs = Math.max(0, eR - bf);
        double g = gravity;
        if (hLs <= hDry && hRs <= hDry) { out[0] = out[1] = out[2] = 0; }
        else {
            double cL = Math.sqrt(g * hLs), cR = Math.sqrt(g * hRs);
            double sl, sr;
            if (hLs <= hDry) { sl = unR - 2 * cR; sr = unR + cR; }
            else if (hRs <= hDry) { sl = unL - cL; sr = unL + 2 * cL; }
            else { sl = Math.min(unL - cL, unR - cR); sr = Math.max(unL + cL, unR + cR); }
            double qL = hLs * unL, qR = hRs * unR;
            double fL0 = qL, fL1 = qL * unL + 0.5 * g * hLs * hLs, fL2 = qL * utL;
            double fR0 = qR, fR1 = qR * unR + 0.5 * g * hRs * hRs, fR2 = qR * utR;
            if (sl >= 0) { out[0] = fL0; out[1] = fL1; out[2] = fL2; }
            else if (sr <= 0) { out[0] = fR0; out[1] = fR1; out[2] = fR2; }
            else {
                double inv = 1 / (sr - sl);
                out[0] = (sr * fL0 - sl * fR0 + sl * sr * (hRs - hLs)) * inv;
                out[1] = (sr * fL1 - sl * fR1 + sl * sr * (qR - qL)) * inv;
                out[2] = (sr * fL2 - sl * fR2 + sl * sr * (hRs * utR - hLs * utL)) * inv;
            }
        }
        out[3] = 0.5 * g * (hL * hL - hLs * hLs);
        out[4] = 0.5 * g * (hR * hR - hRs * hRs);
    }

    /** Änderungsraten dh, dhu, dhv aus dem jetzigen Zustand (Randzellen gefüllt). */
    void rates() {
        slopes();
        final double g = gravity;
        // Kanten in x
        IntStream.range(0, nz).parallel().forEach(j -> {
            double[] o = new double[5];
            for (int i = -1; i < nx; i++) {
                int a = id(i, j), c = a + 1, f = j * (nx + 1) + i + 1;
                boolean sa = solid(a), sc = solid(c);
                if (sa && sc) { fx[3 * f] = fx[3 * f + 1] = fx[3 * f + 2] = 0; pxL[f] = pxR[f] = 0; continue; }
                double hL = h[a] + 0.5 * sH[a], eL = h[a] + b[a] + 0.5 * sE[a], uL = vel(hu[a], h[a]) + 0.5 * sU[a], vL = vel(hv[a], h[a]) + 0.5 * sV[a];
                double hR = h[c] - 0.5 * sH[c], eR = h[c] + b[c] - 0.5 * sE[c], uR = vel(hu[c], h[c]) - 0.5 * sU[c], vR = vel(hv[c], h[c]) - 0.5 * sV[c];
                if (sc) { fx[3 * f] = 0; fx[3 * f + 1] = 0; fx[3 * f + 2] = 0; pxL[f] = 0.5 * g * hL * hL; pxR[f] = 0; continue; }
                if (sa) { fx[3 * f] = 0; fx[3 * f + 1] = 0; fx[3 * f + 2] = 0; pxL[f] = 0; pxR[f] = 0.5 * g * hR * hR; continue; }
                hll(hL, eL, uL, vL, hR, eR, uR, vR, o);
                fx[3 * f] = o[0]; fx[3 * f + 1] = o[1]; fx[3 * f + 2] = o[2];
                pxL[f] = o[1] + o[3];
                pxR[f] = o[1] + o[4];
            }
        });
        // Kanten in z (normal = v)
        IntStream.range(-1, nz).parallel().forEach(j -> {
            double[] o = new double[5];
            for (int i = 0; i < nx; i++) {
                int a = id(i, j), c = a + px, f = (j + 1) * nx + i;
                boolean sa = solid(a), sc = solid(c);
                if (sa && sc) { fz[3 * f] = fz[3 * f + 1] = fz[3 * f + 2] = 0; pzL[f] = pzR[f] = 0; continue; }
                double hL = h[a] + 0.5 * tH[a], eL = h[a] + b[a] + 0.5 * tE[a], vL = vel(hv[a], h[a]) + 0.5 * tV[a], uL = vel(hu[a], h[a]) + 0.5 * tU[a];
                double hR = h[c] - 0.5 * tH[c], eR = h[c] + b[c] - 0.5 * tE[c], vR = vel(hv[c], h[c]) - 0.5 * tV[c], uR = vel(hu[c], h[c]) - 0.5 * tU[c];
                if (sc) { fz[3 * f] = fz[3 * f + 1] = fz[3 * f + 2] = 0; pzL[f] = 0.5 * g * hL * hL; pzR[f] = 0; continue; }
                if (sa) { fz[3 * f] = fz[3 * f + 1] = fz[3 * f + 2] = 0; pzL[f] = 0; pzR[f] = 0.5 * g * hR * hR; continue; }
                hll(hL, eL, vL, uL, hR, eR, vR, uR, o);
                fz[3 * f] = o[0]; fz[3 * f + 1] = o[1]; fz[3 * f + 2] = o[2];
                pzL[f] = o[1] + o[3];
                pzR[f] = o[1] + o[4];
            }
        });
        // Zellen
        final double inv = 1 / dx;
        IntStream.range(0, nz).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                if (solid(c)) { dh[c] = dhu[c] = dhv[c] = 0; continue; }
                int fl = j * (nx + 1) + i, fr = fl + 1, fd = j * nx + i, fu = fd + nx;
                double mass = -(fx[3 * fr] - fx[3 * fl] + fz[3 * fu] - fz[3 * fd]) * inv;
                // Impuls in x: Normalfluss über x-Kanten (mit Druckkorrektur), Tangentialfluss über z-Kanten
                double mx = -(pxL[fr] - pxR[fl] + fz[3 * fu + 2] - fz[3 * fd + 2]) * inv;
                double mz = -(pzL[fu] - pzR[fd] + fx[3 * fr + 2] - fx[3 * fl + 2]) * inv;
                // zentrierter Quellterm aus den eigenen Randwerten (Audusse)
                double hW = h[c] - 0.5 * sH[c], hE = h[c] + 0.5 * sH[c], eW = h[c] + b[c] - 0.5 * sE[c], eE = h[c] + b[c] + 0.5 * sE[c];
                double hS = h[c] - 0.5 * tH[c], hN = h[c] + 0.5 * tH[c], eS = h[c] + b[c] - 0.5 * tE[c], eN = h[c] + b[c] + 0.5 * tE[c];
                mx += g * 0.5 * (hW + hE) * ((eW - hW) - (eE - hE)) * inv;
                mz += g * 0.5 * (hS + hN) * ((eS - hS) - (eN - hN)) * inv;
                dh[c] = mass;
                dhu[c] = mx;
                dhv[c] = mz;
            }
        });
    }

    /** Größte Wellengeschwindigkeit |u| + √(g h) (m/s). */
    double maxSpeed() { return maxSpeed(null); }

    /** Wie oben, ohne die Zellen unter dem feineren Gitter skip = {i0, j0, ni, nj} (die überschreibt es ohnehin). */
    double maxSpeed(int[] skip) {
        return IntStream.range(0, nz).parallel().mapToDouble(j -> {
            double m = 0;
            for (int i = 0; i < nx; i++) {
                if (skip != null && i >= skip[0] && i < skip[0] + skip[2] && j >= skip[1] && j < skip[1] + skip[3]) continue;
                int c = id(i, j);
                double hh = h[c];
                if (hh <= hDry) continue;
                double u = Math.abs(vel(hu[c], hh)), v = Math.abs(vel(hv[c], hh));
                m = Math.max(m, Math.max(u, v) + Math.sqrt(gravity * hh));
            }
            return m;
        }).max().orElse(0);
    }

    /**
     * Ein Schritt nach Heun. {@code ghosts} füllt die Randzellen zur Zeit t (Stufe 1) und t + dt (Stufe 2); die über die
     * beiden Stufen gemittelten Massenflüsse je Kante stehen danach in mfx, mfz.
     */
    void rk2(double t, double dt, java.util.function.DoubleConsumer ghosts) {
        long t0 = System.nanoTime();
        int n = h.length;
        System.arraycopy(h, 0, h0, 0, n);
        System.arraycopy(hu, 0, hu0, 0, n);
        System.arraycopy(hv, 0, hv0, 0, n);
        ghosts.accept(t);
        rates();
        for (int f = 0; f < mfx.length; f++) mfx[f] = 0.5 * fx[3 * f];
        for (int f = 0; f < mfz.length; f++) mfz[f] = 0.5 * fz[3 * f];
        apply(dt, 1);
        ghosts.accept(t + dt);
        rates();
        for (int f = 0; f < mfx.length; f++) mfx[f] += 0.5 * fx[3 * f];
        for (int f = 0; f < mfz.length; f++) mfz[f] += 0.5 * fz[3 * f];
        apply(dt, 2);
        friction(dt);
        track();
        // Zufluss über Einlass-Seiten
        for (int side = 0; side < 4; side++) if (edge[side] == Edge.INLET) inflowVolume += boundaryFlux(side) * dt;
        busyNs += System.nanoTime() - t0;
        stepCount++;
    }

    /** Rechenzeit (ms) und Schritte dieses Gitters bisher. */
    public double busyMs() { return busyNs / 1e6; }

    public long stepCount() { return stepCount; }

    // ------------------------------------------------------------------ für den Rechner auf der Grafikkarte (wave.gl)

    /** Grund je Zelle samt Rand (NaN: Fels), unmittelbar, nicht kopiert: für {@code wave.gl.BayGpu}. */
    public double[] bedCells() { return b; }

    /** Tiefe je Zelle samt Rand, unmittelbar. */
    public double[] depthCells() { return h; }

    /** Durchfluss in x je Zelle samt Rand, unmittelbar. */
    public double[] qxCells() { return hu; }

    /** Durchfluss in z je Zelle samt Rand, unmittelbar. */
    public double[] qzCells() { return hv; }

    /** Höchster Stand je Zelle (NaN: nie nass), unmittelbar. */
    public double[] etaMaxCells() { return etaMax; }

    /** Einlass einer Seite (null: keiner). */
    public Bay.Inlet inlet(int side) { return inlet[side]; }

    /** Was die Grafikkarte für dieses Gitter gerechnet hat: abgeschnittenes Volumen, Zufluss (m³), Schritte, Rechenzeit. */
    public void addGpu(double clippedVolume, double inflow, long steps, long ns) {
        clipped += clippedVolume;
        inflowVolume += inflow;
        stepCount += steps;
        busyNs += ns;
    }

    /** Stufe 1: U = U0 + dt L; Stufe 2: U = (U0 + U + dt L)/2. */
    void apply(double dt, int stage) {
        final double[] cl = new double[nz];
        IntStream.range(0, nz).parallel().forEach(j -> {
            double cut = 0;
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                if (solid(c)) continue;
                double nh, nu, nv;
                if (stage == 1) { nh = h[c] + dt * dh[c]; nu = hu[c] + dt * dhu[c]; nv = hv[c] + dt * dhv[c]; }
                else { nh = 0.5 * (h0[c] + h[c] + dt * dh[c]); nu = 0.5 * (hu0[c] + hu[c] + dt * dhu[c]); nv = 0.5 * (hv0[c] + hv[c] + dt * dhv[c]); }
                if (!(nh >= 0)) { cut -= Double.isNaN(nh) ? 0 : nh; nh = 0; }
                if (nh <= hDry || !(Math.abs(nu) < Double.MAX_VALUE) || !(Math.abs(nv) < Double.MAX_VALUE)) { nu = nv = 0; }
                double sp = Math.hypot(nu, nv), lim = maxVelocity * nh;
                if (sp > lim) { nu *= lim / sp; nv *= lim / sp; }
                h[c] = nh; hu[c] = nu; hv[c] = nv;
            }
            cl[j] = cut;
        });
        for (double v : cl) clipped += v * dx * dx;
    }

    void friction(double dt) {
        final double g = gravity, n2 = manning * manning;
        if (n2 <= 0) return;
        IntStream.range(0, nz).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                double hh = h[c];
                if (hh <= hVel || hh > frictionDepth) continue;
                double u = hu[c] / hh, v = hv[c] / hh, s = Math.sqrt(u * u + v * v);
                double k = 1 + dt * g * n2 * s / Math.pow(hh, 4.0 / 3.0);
                hu[c] /= k;
                hv[c] /= k;
            }
        });
    }

    void track() {
        IntStream.range(0, nz).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                if (h[c] > 0.01) { double e = h[c] + b[c]; if (!(etaMax[c] >= e)) etaMax[c] = e; }
            }
        });
    }

    /** Massenfluss (m³/s) durch eine Seite ins Gitter hinein, aus den gemittelten Flüssen des letzten Schritts. */
    double boundaryFlux(int side) {
        double s = 0;
        if (side == W) for (int j = 0; j < nz; j++) s += mfx[j * (nx + 1)];
        else if (side == E) for (int j = 0; j < nz; j++) s -= mfx[j * (nx + 1) + nx];
        else if (side == S) for (int i = 0; i < nx; i++) s += mfz[i];
        else for (int i = 0; i < nx; i++) s -= mfz[nz * nx + i];
        return s * dx;
    }

    // ------------------------------------------------------------------ Abfragen

    public double volume(double level) {
        double s = 0;
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) { int c = id(i, j); if (!solid(c)) s += h[c]; }
        return s * dx * dx;
    }

    public double depth(int i, int j) { return h[id(i, j)]; }

    public double bedAt(int i, int j) { return b[id(i, j)]; }

    public double eta(int i, int j) { int c = id(i, j); return solid(c) ? Double.NaN : h[c] + b[c]; }

    public double[] velocity(int i, int j) { int c = id(i, j); return new double[]{vel(hu[c], h[c]), vel(hv[c], h[c])}; }

    public int cellI(double x) { return (int) Math.floor((x - x0) / dx); }

    public int cellJ(double z) { return (int) Math.floor((z - z0) / dx); }
}
