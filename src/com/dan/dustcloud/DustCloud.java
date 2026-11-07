package com.dan.dustcloud;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Eine Staubwolke als Schwerestrom über Gelände und Wasser (seit LituyaBay1958 Phase 12, Werkbuch 5.4, 9.2). Die Wolke
 * ist eine Schicht über dem Grund mit Mächtigkeit h, Geschwindigkeit u und je Kornklasse k dem Staubvolumen je Fläche
 * q<sub>k</sub> = h c<sub>k</sub>. Sie ist schwerer als die Luft um g′ = g R c (R = (ρ<sub>s</sub> − ρ<sub>a</sub>)/ρ<sub>a</sub>),
 * fließt also hangab und breitet sich in der Ebene aus wie Wasser unter Luft:
 * <pre>
 *   ∂h/∂t   + ∇·(h u)                    = E |u| + Quellen               (Luft wird eingemischt)
 *   ∂hu/∂t  + ∇·(h u u + ½ g R Q h)      = −g R Q ∇b − C<sub>d</sub>|u|u + h (W − u)/τ<sub>w</sub>
 *   ∂q<sub>k</sub>/∂t + ∇·(q<sub>k</sub> u)              = −w<sub>k</sub> q<sub>k</sub>/h            (Q = Σ q<sub>k</sub>, g′h = g R Q)
 * </pre>
 * Flüsse nach HLL (erste Ordnung, stoßerfassend und ohne negative Mächtigkeit), Grund als Quelle in der Zelle. Die Front
 * bekommt keine eigene Bedingung: Ihre Nase (1 % der Anfangshöhe) läuft in der Schleuse wie das Kastenmodell nach Huppert
 * und Simpson mit Fr 1,19 bis √2 (Benjamin), der Kopf dahinter ist flacher als im Versuch [G]. Einmischung nach
 * Parker (1987) E = 0,075 / √(1 + 718 Ri<sup>2,4</sup>), Ri = g′h/|u|². Sinken je Klasse nach Ferguson und Church (2004),
 * für kleine Körner Stokes. Der Wind {@code W} nimmt die Wolke mit der Zeit τ<sub>w</sub> = τ<sub>0</sub> (1 + Ri<sub>w</sub>) mit
 * (Ri<sub>w</sub> = g′h / |W − u|²) [G]: eine dichte Wolke kriecht am Grund, eine verdünnte zieht mit dem Wind. Abgesetzter Staub wird je Zelle gezählt,
 * getrennt nach Land und Wasser; was über den Rand zieht, auch. Für Licht und Sicht: optische Dicke und Sichtweite.
 * <p>
 * Reines Java, parallel über die Zeilen, zwei Läufe gleich. Das Package kennt keine App und kein anderes Package.
 */
public final class DustCloud {
    /** Grund (m über dem Bezug; über Wasser der Spiegel), NaN: außerhalb (Wand). */
    @FunctionalInterface public interface Ground {
        double height(double x, double z);

        /** Ob an diesem Ort Wasser ist (abgesetzter Staub zählt dann als „ins Wasser“). */
        default boolean water(double x, double z) { return false; }
    }

    /** Wind in Höhe der Wolke {u Ost, w Süd} (m/s). */
    @FunctionalInterface public interface Wind { double[] at(double x, double z, double t); }

    /** Eine Kornklasse: Durchmesser (m) und Anteil am Staub (die Anteile einer Quelle summieren sich zu 1). */
    public record Grain(double d, double share) { }

    /** Eine Quelle: Kreis (x, z, r), Zeit t0 bis t1, Staub (m³ Feststoff je s), Wolke (m³ je s), Geschwindigkeit. */
    public record Source(double x, double z, double r, double t0, double t1, double dust, double cloud, double u, double w) { }

    public final int nx, nz, px;
    public final double dx, x0, z0;
    public final Grain[] grains;
    public double gravity = 9.81, rhoSolid = 2650, rhoAir = 1.2, nuAir = 1.5e-5;
    /** Reibung am Grund C<sub>d</sub> [G], Zeit des Windes τ<sub>0</sub> (s) [G]. */
    public double drag = 0.004, windTime = 20, cfl = 0.4;
    public boolean entrain = true;
    public Wind wind = (x, z, t) -> new double[2];
    public String name = "Staubwolke";

    final double[] b, h, hu, hv;
    final double[][] q;
    final boolean[] water;
    final double[] depLand, depWater;
    final double[] settle;
    final List<Source> sources = new ArrayList<>();
    double time, injected, outflow, cut;
    long steps;
    double busyMs;

    /** Gitter nx × nz Zellen zu dx (m), linke untere Ecke (x0, z0); Kornklassen mit Anteilen. */
    public DustCloud(double x0, double z0, int nx, int nz, double dx, Grain... grains) {
        this.nx = nx;
        this.nz = nz;
        this.dx = dx;
        this.x0 = x0;
        this.z0 = z0;
        this.grains = grains.clone();
        px = nx + 2;
        int n = px * (nz + 2);
        b = new double[n]; h = new double[n]; hu = new double[n]; hv = new double[n];
        q = new double[grains.length][n];
        water = new boolean[n];
        depLand = new double[n];
        depWater = new double[n];
        settle = new double[grains.length];
    }

    /** Lituya [G]: Gesteinsstaub eines Felssturzes, 2 / 10 / 50 / 200 µm mit 10 / 30 / 40 / 20 %. */
    public static Grain[] rockDust() { return new Grain[]{new Grain(2e-6, 0.1), new Grain(10e-6, 0.3), new Grain(50e-6, 0.4), new Grain(200e-6, 0.2)}; }

    public int id(int i, int j) { return (j + 1) * px + i + 1; }

    public double cx(int i) { return x0 + (i + 0.5) * dx; }

    public double cz(int j) { return z0 + (j + 0.5) * dx; }

    /** Grund einlesen (samt Randzellen). */
    public DustCloud build(Ground g) {
        for (int j = -1; j <= nz; j++)
            for (int i = -1; i <= nx; i++) {
                int c = id(i, j);
                b[c] = g.height(cx(i), cz(j));
                water[c] = !Double.isNaN(b[c]) && g.water(cx(i), cz(j));
            }
        for (int k = 0; k < grains.length; k++) settle[k] = settling(grains[k].d);
        return this;
    }

    public DustCloud source(Source s) { sources.add(s); return this; }

    /**
     * Sinkgeschwindigkeit (m/s) eines Korns mit Durchmesser d in Luft nach Ferguson und Church (2004), glatte Kugel
     * (C<sub>1</sub> 18, C<sub>2</sub> 0,4): w = R g d² / (C<sub>1</sub> ν + √(0,75 C<sub>2</sub> R g d³)); für kleine Körner Stokes.
     */
    public double settling(double d) {
        double r = (rhoSolid - rhoAir) / rhoAir;
        return r * gravity * d * d / (18 * nuAir + Math.sqrt(0.75 * 0.4 * r * gravity * d * d * d));
    }

    /** Stokes: w = (ρ<sub>s</sub> − ρ<sub>a</sub>) g d² / (18 η), η = ρ<sub>a</sub> ν. */
    public double stokes(double d) { return (rhoSolid - rhoAir) * gravity * d * d / (18 * rhoAir * nuAir); }

    double R() { return (rhoSolid - rhoAir) / rhoAir; }

    boolean solid(int c) { return Double.isNaN(b[c]); }

    double Q(int c) { double s = 0; for (double[] a : q) s += a[c]; return s; }

    /** Eine Wolke in einem Kreis setzen: Mächtigkeit h (m), Staubanteil c (Volumen), Geschwindigkeit (u, w). */
    public DustCloud put(double x, double z, double r, double hh, double c, double u, double w) {
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int cc = id(i, j);
                if (solid(cc) || Math.hypot(cx(i) - x, cz(j) - z) > r) continue;
                h[cc] += hh;
                hu[cc] += hh * u;
                hv[cc] += hh * w;
                for (int k = 0; k < grains.length; k++) q[k][cc] += hh * c * grains[k].share;
                injected += hh * c * dx * dx;
            }
        return this;
    }

    /**
     * Sofort in die Zelle am Ort: Staub (m³ Feststoff, nach den Anteilen der Körner) in Wolke (m³) mit Geschwindigkeit
     * (u, w) – für Quellen, die von außen je Schritt kommen (etwa ein Felssturz). Gibt false, wenn der Ort außerhalb liegt.
     */
    public synchronized boolean inject(double x, double z, double dust, double cloud, double u, double w) {
        int c = cell(x, z);
        if (c < 0 || solid(c)) return false;
        double a = dx * dx, dh = cloud / a;
        h[c] += dh;
        hu[c] += dh * u;
        hv[c] += dh * w;
        for (int k = 0; k < grains.length; k++) q[k][c] += dust / a * grains[k].share;
        injected += dust;
        return true;
    }

    // ------------------------------------------------------------------ Rechnung

    static final double H_DRY = 1e-3, H_VEL = 0.05;

    /** Ein Schritt nach CFL; gibt dt zurück. */
    public synchronized double step() {
        long t0 = System.nanoTime();
        double s = 0.5;
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                if (h[c] <= H_DRY) continue;
                double u = vel(hu[c], h[c]), v = vel(hv[c], h[c]);
                s = Math.max(s, Math.max(Math.abs(u), Math.abs(v)) + Math.sqrt(gravity * R() * Q(c)));
            }
        double dt = Math.min(cfl * dx / s, windTime / 4);
        step(dt);
        busyMs += (System.nanoTime() - t0) / 1e6;
        return dt;
    }

    public void run(double tEnd) { while (time < tEnd - 1e-12) step(); }

    double vel(double m, double hh) { return hh > H_VEL ? m / hh : 0; }

    /** Ein Schritt mit festem dt. */
    public synchronized void step(double dt) {
        int K = grains.length, nv = 3 + K, n = h.length;
        edges();
        double[] fx = new double[(nx + 1) * nz * nv], fz = new double[nx * (nz + 1) * nv];
        IntStream.range(0, nz).parallel().forEach(j -> {
            double[] o = new double[nv];
            for (int i = -1; i < nx; i++) { flux(id(i, j), id(i + 1, j), true, o); System.arraycopy(o, 0, fx, (j * (nx + 1) + i + 1) * nv, nv); }
        });
        IntStream.range(-1, nz).parallel().forEach(j -> {
            double[] o = new double[nv];
            for (int i = 0; i < nx; i++) { flux(id(i, j), id(i, j + 1), false, o); System.arraycopy(o, 0, fz, ((j + 1) * nx + i) * nv, nv); }
        });
        // Ausfluss über den Rand (Staub)
        double out = 0;
        for (int j = 0; j < nz; j++) {
            int fw = (j * (nx + 1)) * nv, fe = (j * (nx + 1) + nx) * nv;
            for (int k = 0; k < K; k++) out += (-fx[fw + 3 + k] + fx[fe + 3 + k]) * dt * dx;
        }
        for (int i = 0; i < nx; i++) {
            int fs = i * nv, fn = (nz * nx + i) * nv;
            for (int k = 0; k < K; k++) out += (-fz[fs + 3 + k] + fz[fn + 3 + k]) * dt * dx;
        }
        outflow += out;
        double gR = gravity * R(), inv = dt / dx;
        double[] nh = h.clone(), nhu = hu.clone(), nhv = hv.clone();
        double[][] nq = new double[K][];
        for (int k = 0; k < K; k++) nq[k] = q[k].clone();
        double[] cutRow = new double[nz];
        IntStream.range(0, nz).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                if (solid(c)) continue;
                int l = (j * (nx + 1) + i) * nv, r = l + nv, d = (j * nx + i) * nv, u = d + nx * nv;
                nh[c] = h[c] - inv * (fx[r] - fx[l] + fz[u] - fz[d]);
                nhu[c] = hu[c] - inv * (fx[r + 1] - fx[l + 1] + fz[u + 2] - fz[d + 2]);
                nhv[c] = hv[c] - inv * (fx[r + 2] - fx[l + 2] + fz[u + 1] - fz[d + 1]);
                for (int k = 0; k < K; k++) nq[k][c] = q[k][c] - inv * (fx[r + 3 + k] - fx[l + 3 + k] + fz[u + 3 + k] - fz[d + 3 + k]);
                // Grund: −g R Q ∇b in der Zelle (an Wänden einseitig)
                double Qc = Q(c);
                if (Qc > 0) {
                    double gx = slope(c, 1), gz = slope(c, px);
                    nhu[c] -= dt * gR * Qc * gx;
                    nhv[c] -= dt * gR * Qc * gz;
                }
                double neg = 0;
                if (nh[c] < 0) { neg -= nh[c]; nh[c] = 0; }
                for (int k = 0; k < K; k++) if (nq[k][c] < 0) { cutRow[j] -= nq[k][c] * dx * dx; nq[k][c] = 0; }
            }
        });
        for (double v : cutRow) cut += v;
        System.arraycopy(nh, 0, h, 0, n);
        System.arraycopy(nhu, 0, hu, 0, n);
        System.arraycopy(nhv, 0, hv, 0, n);
        for (int k = 0; k < K; k++) System.arraycopy(nq[k], 0, q[k], 0, n);
        sources(dt);
        local(dt);
        time += dt;
        steps++;
    }

    double slope(int c, int s) {
        double l = b[c - s], r = b[c + s];
        if (Double.isNaN(l) && Double.isNaN(r)) return 0;
        if (Double.isNaN(l)) return (r - b[c]) / dx;
        if (Double.isNaN(r)) return (b[c] - l) / dx;
        return (r - l) / (2 * dx);
    }

    /** Randzellen: offen nur hinaus (wie die Nachbarzelle innen, ohne Zustrom über den Rand); Wand (Grund NaN) hat keinen Zustand. */
    void edges() {
        for (int j = -1; j <= nz; j++)
            for (int i = -1; i <= nx; i++) {
                if (i >= 0 && i < nx && j >= 0 && j < nz) continue;
                int g = id(i, j), c = id(Math.max(0, Math.min(nx - 1, i)), Math.max(0, Math.min(nz - 1, j)));
                if (solid(g) || solid(c)) { h[g] = hu[g] = hv[g] = 0; for (double[] a : q) a[g] = 0; continue; }
                h[g] = h[c]; hu[g] = hu[c]; hv[g] = hv[c];
                if (i < 0 && hu[g] > 0 || i >= nx && hu[g] < 0) hu[g] = 0;
                if (j < 0 && hv[g] > 0 || j >= nz && hv[g] < 0) hv[g] = 0;
                for (double[] a : q) a[g] = a[c];
            }
    }

    /** HLL über die Kante a | c (x: a links; z: a unten). o: {h, Normal-, Tangentialimpuls, q₀ …}. */
    void flux(int a, int c, boolean xs, double[] o) {
        Arrays.fill(o, 0);
        boolean sa = solid(a), sc = solid(c);
        if (sa && sc) return;
        double gR = gravity * R();
        if (sa || sc) {
            // Wand: nur Druck auf die Wand
            int w = sa ? c : a;
            double p = 0.5 * gR * Q(w) * h[w];
            o[1] = p;
            return;
        }
        double hL = h[a], hR = h[c];
        if (hL <= H_DRY && hR <= H_DRY) return;
        double uL = vel(xs ? hu[a] : hv[a], hL), vL = vel(xs ? hv[a] : hu[a], hL);
        double uR = vel(xs ? hu[c] : hv[c], hR), vR = vel(xs ? hv[c] : hu[c], hR);
        double QL = Q(a), QR = Q(c);
        double cL = Math.sqrt(gR * QL), cR = Math.sqrt(gR * QR), sl, sr;
        if (hL <= H_DRY) { sl = uR - 2 * cR; sr = uR + cR; }
        else if (hR <= H_DRY) { sl = uL - cL; sr = uL + 2 * cL; }
        else { sl = Math.min(uL - cL, uR - cR); sr = Math.max(uL + cL, uR + cR); }
        int K = grains.length;
        double[] FL = new double[3 + K], FR = new double[3 + K], UL = new double[3 + K], UR = new double[3 + K];
        UL[0] = hL; UL[1] = hL * uL; UL[2] = hL * vL;
        UR[0] = hR; UR[1] = hR * uR; UR[2] = hR * vR;
        FL[0] = hL * uL; FL[1] = hL * uL * uL + 0.5 * gR * QL * hL; FL[2] = hL * uL * vL;
        FR[0] = hR * uR; FR[1] = hR * uR * uR + 0.5 * gR * QR * hR; FR[2] = hR * uR * vR;
        for (int k = 0; k < K; k++) { UL[3 + k] = q[k][a]; UR[3 + k] = q[k][c]; FL[3 + k] = q[k][a] * uL; FR[3 + k] = q[k][c] * uR; }
        if (sl >= 0) System.arraycopy(FL, 0, o, 0, o.length);
        else if (sr <= 0) System.arraycopy(FR, 0, o, 0, o.length);
        else {
            double inv = 1 / (sr - sl);
            for (int m = 0; m < o.length; m++) o[m] = (sr * FL[m] - sl * FR[m] + sl * sr * (UR[m] - UL[m])) * inv;
        }
    }

    void sources(double dt) {
        for (Source s : sources) {
            if (time + dt <= s.t0() || time >= s.t1()) continue;
            double f = (Math.min(time + dt, s.t1()) - Math.max(time, s.t0()));
            List<Integer> cells = new ArrayList<>();
            for (int j = 0; j < nz; j++)
                for (int i = 0; i < nx; i++) {
                    int c = id(i, j);
                    if (!solid(c) && Math.hypot(cx(i) - s.x(), cz(j) - s.z()) <= Math.max(s.r(), 0.5 * dx)) cells.add(c);
                }
            if (cells.isEmpty()) continue;
            double area = cells.size() * dx * dx, dh = s.cloud() * f / area, dq = s.dust() * f / area;
            for (int c : cells) {
                h[c] += dh;
                hu[c] += dh * s.u();
                hv[c] += dh * s.w();
                for (int k = 0; k < grains.length; k++) q[k][c] += dq * grains[k].share;
            }
            injected += s.dust() * f;
        }
    }

    /** In der Zelle: Sinken, Reibung, Wind, Einmischung (alles halb implizit). */
    void local(double dt) {
        int K = grains.length;
        double gR = gravity * R();
        double[] cutRow = new double[nz];
        IntStream.range(0, nz).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                if (solid(c)) continue;
                double hh = h[c];
                if (hh <= H_DRY) {
                    // zu dünn: der Staub fällt aus
                    for (int k = 0; k < K; k++) { settleOut(c, q[k][c]); q[k][c] = 0; }
                    hu[c] = hv[c] = 0;
                    continue;
                }
                for (int k = 0; k < K; k++) {
                    double before = q[k][c], after = before / (1 + dt * settle[k] / hh);
                    q[k][c] = after;
                    settleOut(c, before - after);
                }
                double u = vel(hu[c], hh), v = vel(hv[c], hh), sp = Math.hypot(u, v), Qc = Q(c);
                double ri = sp > 1e-9 ? gR * Qc / (sp * sp) : 1e9;
                // Reibung am Grund
                if (hh > H_VEL) { double k = 1 + dt * drag * sp / hh; u /= k; v /= k; }
                // Wind
                double[] w = wind.at(cx(i), cz(j), time);
                double rel = (w[0] - u) * (w[0] - u) + (w[1] - v) * (w[1] - v);
                double tw = windTime * (1 + Math.min(gR * Qc / Math.max(rel, 1e-6), 1e6)), a = dt / tw;
                u = (u + a * w[0]) / (1 + a);
                v = (v + a * w[1]) / (1 + a);
                // Einmischung: die Luft bringt den Impuls des Windes mit
                if (entrain && sp > 0) {
                    double e = 0.075 / Math.sqrt(1 + 718 * Math.pow(ri, 2.4));
                    double nh = hh + dt * e * sp;
                    u = (hh * u + (nh - hh) * w[0]) / nh;
                    v = (hh * v + (nh - hh) * w[1]) / nh;
                    hh = nh;
                }
                h[c] = hh;
                hu[c] = hh * u;
                hv[c] = hh * v;
            }
        });
    }

    void settleOut(int c, double dq) {
        if (water[c]) depWater[c] += dq; else depLand[c] += dq;
    }

    // ------------------------------------------------------------------ Abfragen

    public double time() { return time; }

    public long steps() { return steps; }

    public double busyMs() { return busyMs; }

    int cell(double x, double z) {
        int i = (int) Math.floor((x - x0) / dx), j = (int) Math.floor((z - z0) / dx);
        return i < 0 || j < 0 || i >= nx || j >= nz ? -1 : id(i, j);
    }

    /** Grund unter der Zelle (m über dem Bezug; über Wasser der Spiegel), NaN außerhalb (für die Darstellung in gl/). */
    public double ground(double x, double z) { int c = cell(x, z); return c < 0 ? Double.NaN : b[c]; }

    /** Mächtigkeit der Wolke (m), 0 außerhalb. */
    public double thickness(double x, double z) { int c = cell(x, z); return c < 0 ? 0 : h[c]; }

    /** Oberkante der Wolke (m über dem Bezug), NaN ohne Wolke. */
    public double top(double x, double z) { int c = cell(x, z); return c < 0 || h[c] <= H_DRY ? Double.NaN : b[c] + h[c]; }

    /** Staubanteil (Volumen) in der Wolke. */
    public double concentration(double x, double z) { int c = cell(x, z); return c < 0 || h[c] <= H_DRY ? 0 : Q(c) / h[c]; }

    /** Geschwindigkeit {u, w} (m/s). */
    public double[] velocity(double x, double z) { int c = cell(x, z); return c < 0 ? new double[2] : new double[]{vel(hu[c], h[c]), vel(hv[c], h[c])}; }

    /** Optische Dicke senkrecht durch die Wolke: Σ 1,5 Q<sub>ext</sub> q<sub>k</sub> / d<sub>k</sub> mit Q<sub>ext</sub> = 2 (große Körner). */
    public double opticalDepth(double x, double z) {
        int c = cell(x, z);
        if (c < 0) return 0;
        double s = 0;
        for (int k = 0; k < grains.length; k++) s += 3 * q[k][c] / grains[k].d;
        return s;
    }

    /** Sichtweite in der Wolke (m) nach Koschmieder 3,912/β, β = Σ 3 c<sub>k</sub>/d<sub>k</sub>; unendlich ohne Wolke. */
    public double visibility(double x, double z) {
        int c = cell(x, z);
        if (c < 0 || h[c] <= H_DRY) return Double.POSITIVE_INFINITY;
        double beta = 0;
        for (int k = 0; k < grains.length; k++) beta += 3 * q[k][c] / h[c] / grains[k].d;
        return beta <= 0 ? Double.POSITIVE_INFINITY : 3.912 / beta;
    }

    /** Abgesetzter Staub (m³ Feststoff je m²) an Land und im Wasser. */
    public double deposit(double x, double z) { int c = cell(x, z); return c < 0 ? 0 : depLand[c] + depWater[c]; }

    /** Staub (m³ Feststoff): in der Luft, abgesetzt an Land, im Wasser, über den Rand, abgeschnitten, eingebracht. */
    public double[] budget() {
        double air = 0, land = 0, wat = 0;
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                air += Q(c);
                land += depLand[c];
                wat += depWater[c];
            }
        double a = dx * dx;
        return new double[]{air * a, land * a, wat * a, outflow, cut, injected};
    }

    /** Wolkenvolumen (m³) und Fläche mit Wolke über hMin (m²). */
    public double[] extent(double hMin) {
        double v = 0, a = 0;
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) { int c = id(i, j); v += h[c]; if (h[c] > hMin) a++; }
        return new double[]{v * dx * dx, a * dx * dx};
    }

    /** Front: am weitesten vom Punkt (x, z) entfernte Zelle mit h über hMin, in Richtung (ex, ez) gemessen (m). */
    public double front(double x, double z, double ex, double ez, double hMin) {
        double best = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                if (h[c] > hMin) best = Math.max(best, (cx(i) - x) * ex + (cz(j) - z) * ez);
            }
        return best;
    }

    /** Schwerpunkt des Staubs in der Luft {x, z}. */
    public double[] centroid() {
        double s = 0, sx = 0, sz = 0;
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) { double m = Q(id(i, j)); s += m; sx += m * cx(i); sz += m * cz(j); }
        return s <= 0 ? new double[]{Double.NaN, Double.NaN} : new double[]{sx / s, sz / s};
    }

    /** Gitterwerte für die Darstellung (Zeilen j, Spalten i): Mächtigkeit (m), optische Dicke, Ablagerung (m³/m²). */
    public float[][] snapshot() {
        float[] th = new float[nx * nz], od = new float[nx * nz], dp = new float[nx * nz];
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                th[j * nx + i] = (float) h[c];
                od[j * nx + i] = (float) opticalDepth(cx(i), cz(j));
                dp[j * nx + i] = (float) (depLand[c] + depWater[c]);
            }
        return new float[][]{th, od, dp};
    }

    public List<Source> sources() { return Collections.unmodifiableList(sources); }

    public String describe() {
        StringBuilder s = new StringBuilder(String.format(Locale.GERMAN, "%s: %d × %d zu %.0f m, Körner", name, nx, nz, dx));
        for (Grain g : grains) s.append(String.format(Locale.GERMAN, " %.0f µm (%.0f %%, %.3g m/s)", g.d * 1e6, g.share * 100, settling(g.d)));
        return s.toString();
    }
}
