package com.dan.wind;

import java.util.stream.IntStream;

/**
 * Der massenkonsistente Kern (seit Brocken Phase 4, F6 A): ein Startfeld aus Profil, Rauigkeit, Lee, Hangwind und
 * Kaltluft wird so wenig wie möglich verändert, dass in keiner Zelle Luft entsteht oder verschwindet (Variationsansatz
 * nach Sherman 1978, MATHEW; wie WindNinja). Gerechnet wird mit einem Lagrange-Multiplikator λ je Zelle:
 * <pre>
 *   u = u0 + a ∂λ/∂x,   w = w0 + a ∂λ/∂z (waagerecht),   Ω = Ω0 + b ∂λ/∂ζ (durch die Schichtflächen)
 *   Σ Flüsse durch die Seiten jeder Zelle = 0   →   Σ k_f (λ_P − λ_N) = Div0_P
 * </pre>
 * Das Gitter folgt dem Gelände mit fester Obergrenze (σ-Koordinaten): Über einer Kuppe ist die Säule dünner, die Luft
 * muss schneller werden oder hinauf; das Verhältnis b/a entscheidet, ob sie über den Berg geht (neutral, labil) oder um
 * ihn herum (stabil, Inversion). Am Grund geht nichts hindurch (Ω = 0), oben und an den Seiten ist der Rand offen (λ = 0).
 * <p>
 * Lösen: vorkonditionierte konjugierte Gradienten, der Vorkonditionierer löst jede Säule senkrecht genau (Thomas), weil
 * die dünnen unteren Schichten senkrecht am stärksten koppeln; Zeilen parallel auf allen Kernen. Massenerhaltung ist im
 * Gitter exakt bis auf den Rest des Lösers.
 */
final class MassSolver {
    private MassSolver() { }

    static WindSolution solve(WindField f, double xMin, double zMin, double xMax, double zMax) {
        long t0 = System.nanoTime();
        double c = f.quality.cell;
        int nx = Math.max(4, (int) Math.round((xMax - xMin) / c)), nz = Math.max(4, (int) Math.round((zMax - zMin) / c)), n = f.quality.layers;
        int cols = nx * nz, cells = cols * n;
        // ------------------------------------------------ Spalten: Grund, Neigung, Rauigkeit, Lee, Kaltluft
        float[] zg = new float[cols];
        double zmax = -1e9;
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                double h = f.ground.height(xMin + (i + 0.5) * c, zMin + (j + 0.5) * c);
                zg[j * nx + i] = (float) h;
                zmax = Math.max(zmax, h);
            }
        double ztop = zmax + f.top;
        double[] D = new double[cols], sx = new double[cols], sz = new double[cols], z0 = new double[cols];
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int p = j * nx + i;
                D[p] = ztop - zg[p];
                int ia = Math.max(0, i - 1), ib = Math.min(nx - 1, i + 1), ja = Math.max(0, j - 1), jb = Math.min(nz - 1, j + 1);
                sx[p] = (zg[j * nx + ib] - zg[j * nx + ia]) / ((ib - ia) * c);
                sz[p] = (zg[jb * nx + i] - zg[ja * nx + i]) / ((jb - ja) * c);
                z0[p] = f.z0At(xMin + (i + 0.5) * c, zMin + (j + 0.5) * c);
            }
        double[] dir = f.toVector();
        float[] shelter = shelter(f, zg, nx, nz, c, xMin, zMin, dir);
        float[] cold = coldPool(f, zg, nx, nz, c);
        // ------------------------------------------------ Schichten (σ), erste Schicht über dem höchsten Grund f.first m
        double first = WindField.FIRST_LAYER, r = WindField.stretchFor(f.top, first, n);
        double[] sigE = new double[n + 1], sigC = new double[n];
        for (int k = 0; k <= n; k++) sigE[k] = Math.min(1, first * (Math.pow(r, k) - 1) / (r - 1) / f.top);
        sigE[n] = 1;
        for (int k = 0; k < n; k++) sigC[k] = (sigE[k] + sigE[k + 1]) / 2;
        // ------------------------------------------------ Startfeld (waagerecht, in der Mitte jeder Zelle)
        double[] u0 = new double[cells], v0 = new double[cells];
        double hm = 25;   // Höhe des Strahls im Hangwind (m) [G]
        final double slopeF = f.slopeFlow();
        IntStream.range(0, nz).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) {
                int p = j * nx + i;
                double sh = smooth(0.05, 0.35, shelter[p]), cd = cold[p];
                double gl = Math.hypot(sx[p], sz[p]), ux = gl > 1e-6 ? sx[p] / gl : 0, uz = gl > 1e-6 ? sz[p] / gl : 0;
                for (int k = 0; k < n; k++) {
                    double h = sigC[k] * D[p];
                    double s = f.profile(h, z0[p]);
                    s *= 1 - 0.7 * sh * Math.exp(-h / 120);                                // Lee: Windschatten hinter Kanten [G]
                    if (cd > 1) s *= 0.2 + 0.8 * smooth(0.5 * cd, 1.2 * cd, h);             // im Kaltluftsee fast still [G]
                    double a = dir[0] * s, b = dir[2] * s;
                    if (slopeF != 0 && gl > 1e-4) {
                        double m = slopeF * Math.min(1, gl / 0.15) * (h / hm) * Math.exp(1 - h / hm);
                        a += ux * m;
                        b += uz * m;
                    }
                    int q = (k * nz + j) * nx + i;
                    u0[q] = a;
                    v0[q] = b;
                }
            }
        });
        // ------------------------------------------------ Koeffizienten und Quellen
        double ra = 1, rb = ratio(f);
        float[] kE = new float[cells], kS = new float[cells], kU = new float[cells];
        double[] diag = new double[cells], rhs = new double[cells];
        double[] dsig = new double[n];
        for (int k = 0; k < n; k++) dsig[k] = sigE[k + 1] - sigE[k];
        // Flüsse des Startfelds: östliche, südliche, obere Fläche je Zelle
        double[] fE = new double[cells], fS = new double[cells], fU = new double[cells];
        IntStream.range(0, nz).parallel().forEach(j -> {
            for (int k = 0; k < n; k++)
                for (int i = 0; i < nx; i++) {
                    int p = j * nx + i, q = (k * nz + j) * nx + i;
                    if (i < nx - 1) {
                        double ax = c * dsig[k] * 0.5 * (D[p] + D[p + 1]);
                        fE[q] = 0.5 * (u0[q] + u0[q + 1]) * ax;
                        kE[q] = (float) (ra * ax / c);
                    } else {
                        double ax = c * dsig[k] * D[p];
                        fE[q] = u0[q] * ax;
                        kE[q] = (float) (ra * ax / c);   // zum Rand (λ = 0)
                    }
                    if (j < nz - 1) {
                        double az = c * dsig[k] * 0.5 * (D[p] + D[p + nx]);
                        fS[q] = 0.5 * (v0[q] + v0[q + nx]) * az;
                        kS[q] = (float) (ra * az / c);
                    } else {
                        double az = c * dsig[k] * D[p];
                        fS[q] = v0[q] * az;
                        kS[q] = (float) (ra * az / c);
                    }
                    if (k < n - 1) {
                        int qu = q + nx * nz;
                        double uf = 0.5 * (u0[q] + u0[qu]), vf = 0.5 * (v0[q] + v0[qu]);
                        double om = -(uf * sx[p] + vf * sz[p]) * (1 - sigE[k + 1]);
                        fU[q] = om * c * c;
                        kU[q] = (float) (rb * c * c / ((sigC[k + 1] - sigC[k]) * D[p]));
                    } else {
                        fU[q] = 0;   // σ = 1: die Obergrenze ist flach
                        kU[q] = (float) (rb * c * c / ((1 - sigC[k]) * D[p]));   // zum offenen Rand oben (λ = 0)
                    }
                }
        });
        // westliche, nördliche Ränder: Fluss des Startfelds hinein, offener Rand
        double[] kW0 = new double[n * nz], kN0 = new double[n * nx];
        IntStream.range(0, nz).parallel().forEach(j -> {
            for (int k = 0; k < n; k++)
                for (int i = 0; i < nx; i++) {
                    int p = j * nx + i, q = (k * nz + j) * nx + i;
                    double out = fE[q] + fS[q] + fU[q];
                    double in = 0, d = kE[q] + kS[q] + kU[q];
                    if (i > 0) { in += fE[q - 1]; d += kE[q - 1]; }
                    else { double ax = c * dsig[k] * D[p]; in += u0[q] * ax; d += ra * ax / c; }
                    if (j > 0) { in += fS[q - nx]; d += kS[q - nx]; }
                    else { double az = c * dsig[k] * D[p]; in += v0[q] * az; d += ra * az / c; }
                    if (k > 0) { in += fU[q - nx * nz]; d += kU[q - nx * nz]; }
                    rhs[q] = out - in;
                    diag[q] = d;
                }
        });
        double fs0 = 0;
        for (int q = 0; q < cells; q++) fs0 = Math.max(fs0, Math.abs(fE[q]) + Math.abs(fS[q]));
        final double fluxScale = fs0;
        // ------------------------------------------------ PCG
        double[] lam = new double[cells], res = rhs.clone(), z = new double[cells], pd = new double[cells], ap = new double[cells];
        Op op = new Op(nx, nz, n, kE, kS, kU, diag);
        op.precond(res, z);
        System.arraycopy(z, 0, pd, 0, cells);
        double rz = dot(res, z), r0 = Math.sqrt(dot(rhs, rhs)), rn = r0;
        int it = 0, maxIt = f.maxIterations;
        double tol = f.tolerance;
        while (it < maxIt && rn > tol * r0 && r0 > 0) {
            op.apply(pd, ap);
            double alpha = rz / dot(pd, ap);
            axpy(alpha, pd, lam);
            axpy(-alpha, ap, res);
            op.precond(res, z);
            double rz1 = dot(res, z);
            double beta = rz1 / rz;
            rz = rz1;
            xpby(z, beta, pd);
            rn = Math.sqrt(dot(res, res));
            it++;
        }
        // ------------------------------------------------ Feld aus λ: Flüsse, Geschwindigkeiten, Prüfung der Masse
        float[] u = new float[cells], v = new float[cells], w = new float[cells];
        double[] err = new double[nz], errSum = new double[nz];
        final double[] lamF = lam;
        IntStream.range(0, nz).parallel().forEach(j -> {
            double emax = 0, esum = 0;
            for (int k = 0; k < n; k++)
                for (int i = 0; i < nx; i++) {
                    int p = j * nx + i, q = (k * nz + j) * nx + i;
                    double lp = lamF[q];
                    double le = i < nx - 1 ? lamF[q + 1] : 0, lw = i > 0 ? lamF[q - 1] : 0;
                    double ls = j < nz - 1 ? lamF[q + nx] : 0, ln = j > 0 ? lamF[q - nx] : 0;
                    double lu = k < n - 1 ? lamF[q + nx * nz] : 0, ld = k > 0 ? lamF[q - nx * nz] : 0;
                    // Flüsse durch die sechs Seiten (Startfeld + Ausgleich)
                    double FE = fE[q] + kE[q] * (le - lp);
                    double FW = (i > 0 ? fE[q - 1] + kE[q - 1] * (lp - lw) : u0[q] * c * dsig[k] * D[p] + ra * c * dsig[k] * D[p] / c * (lp - 0));
                    double FS = fS[q] + kS[q] * (ls - lp);
                    double FN = (j > 0 ? fS[q - nx] + kS[q - nx] * (lp - ln) : v0[q] * c * dsig[k] * D[p] + ra * c * dsig[k] * D[p] / c * (lp - 0));
                    double FU = fU[q] + kU[q] * (lu - lp);
                    double FD = k > 0 ? fU[q - nx * nz] + kU[q - nx * nz] * (lp - ld) : 0;
                    double net = FE - FW + FS - FN + FU - FD;
                    double scale = Math.abs(FE) + Math.abs(FW) + Math.abs(FS) + Math.abs(FN) + Math.abs(FU) + Math.abs(FD);
                    double e = Math.abs(net) / Math.max(1e-9 * fluxScale, scale);
                    emax = Math.max(emax, e);
                    esum += e;
                    double ax = c * dsig[k] * D[p];
                    double uc = 0.5 * (FE + FW) / ax, vc = 0.5 * (FS + FN) / ax;
                    double oc = 0.5 * (FU + FD) / (c * c);
                    double wc = oc + (uc * sx[p] + vc * sz[p]) * (1 - sigC[k]);
                    u[q] = (float) uc;
                    v[q] = (float) wc;
                    w[q] = (float) vc;
                }
            err[j] = emax;
            errSum[j] = esum;
        });
        double emax = 0, esum = 0;
        for (int j = 0; j < nz; j++) { emax = Math.max(emax, err[j]); esum += errSum[j]; }
        double ms = (System.nanoTime() - t0) / 1e6;
        return new WindSolution(xMin, zMin, c, nx, nz, n, ztop, sigE, sigC, zg, shelter, cold, u, v, w, it, r0 > 0 ? rn / r0 : 0, emax, esum / cells, ms,
                f.speed, f.fromDeg, rb, slopeF, f.stability);
    }

    /**
     * Verhältnis b/a (senkrecht gegen waagerecht) nach der Schichtung [G]: labil leichter hinauf, stabil um den Berg
     * herum. Neutral so gewählt, dass die Beschleunigung über einer Kuppe die Faustformel von Walmsley, Taylor und Salmon
     * (1989) trifft (ΔS = 1,6 H/L am Gipfel).
     */
    static double ratio(WindField f) {
        if (!Double.isNaN(f.verticalRatio)) return f.verticalRatio;
        return switch (f.stability) { case LABIL -> 1.2; case NEUTRAL -> 0.7; case STABIL -> 0.35; case INVERSION -> 0.12; };
    }

    /**
     * Lee-Winkel nach Winstral und Marks (2002, „Sx“): der größte Winkel zu einer Kante luvwärts bis {@link WindField#shelterReach}
     * m; positiv heißt Windschatten.
     */
    static float[] shelter(WindField f, float[] zg, int nx, int nz, double c, double xMin, double zMin, double[] dir) {
        float[] s = new float[nx * nz];
        double reach = f.shelterReach, step = c * 0.5;
        IntStream.range(0, nz).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) {
                double x = xMin + (i + 0.5) * c, z = zMin + (j + 0.5) * c, h = zg[j * nx + i], best = -1;
                for (double d = step; d <= reach; d += step) {
                    double g = f.ground.height(x - dir[0] * d, z - dir[2] * d);
                    best = Math.max(best, Math.atan2(g - h, d));
                }
                s[j * nx + i] = (float) best;
            }
        });
        return s;
    }

    /**
     * Kaltluft (seit Brocken Phase 4): nachts bei stabiler Luft sammelt sie sich in Senken. Tiefe = Lage unter dem Mittel
     * des Geländes in {@link WindField#coldRadius} m (TPI), · 0,6, höchstens 200 m [G], nach {@link WindField#coldStrength}.
     */
    static float[] coldPool(WindField f, float[] zg, int nx, int nz, double c) {
        float[] out = new float[nx * nz];
        double k = f.coldStrength();
        if (k <= 0) return out;
        int r = Math.max(1, (int) Math.round(f.coldRadius / c));
        double[] a = new double[nx * nz], b = new double[nx * nz];
        for (int j = 0; j < nz; j++) {
            double s = 0;
            int cnt = 0;
            for (int i = -r; i <= r; i++) { int ii = WindSolution.clamp(i, 0, nx - 1); s += zg[j * nx + ii]; cnt++; }
            for (int i = 0; i < nx; i++) {
                a[j * nx + i] = s / cnt;
                int add = WindSolution.clamp(i + r + 1, 0, nx - 1), rem = WindSolution.clamp(i - r, 0, nx - 1);
                s += zg[j * nx + add] - zg[j * nx + rem];
            }
        }
        for (int i = 0; i < nx; i++) {
            double s = 0;
            int cnt = 0;
            for (int j = -r; j <= r; j++) { int jj = WindSolution.clamp(j, 0, nz - 1); s += a[jj * nx + i]; cnt++; }
            for (int j = 0; j < nz; j++) {
                b[j * nx + i] = s / cnt;
                int add = WindSolution.clamp(j + r + 1, 0, nz - 1), rem = WindSolution.clamp(j - r, 0, nz - 1);
                s += a[add * nx + i] - a[rem * nx + i];
            }
        }
        for (int p = 0; p < out.length; p++) out[p] = (float) (Math.max(0, Math.min(200, (b[p] - zg[p]) * 0.6)) * k);
        return out;
    }

    // ------------------------------------------------------------------ Lineare Algebra

    /** Der Operator A λ = diag λ − Σ k λ_N und der Vorkonditionierer (Säulen genau). */
    static final class Op {
        final int nx, nz, n, layer;
        final float[] kE, kS, kU;
        final double[] diag;

        Op(int nx, int nz, int n, float[] kE, float[] kS, float[] kU, double[] diag) {
            this.nx = nx;
            this.nz = nz;
            this.n = n;
            this.layer = nx * nz;
            this.kE = kE;
            this.kS = kS;
            this.kU = kU;
            this.diag = diag;
        }

        void apply(double[] x, double[] y) {
            IntStream.range(0, nz).parallel().forEach(j -> {
                for (int k = 0; k < n; k++)
                    for (int i = 0; i < nx; i++) {
                        int q = (k * nz + j) * nx + i;
                        double s = diag[q] * x[q];
                        if (i < nx - 1) s -= kE[q] * x[q + 1];
                        if (i > 0) s -= kE[q - 1] * x[q - 1];
                        if (j < nz - 1) s -= kS[q] * x[q + nx];
                        if (j > 0) s -= kS[q - nx] * x[q - nx];
                        if (k < n - 1) s -= kU[q] * x[q + layer];
                        if (k > 0) s -= kU[q - layer] * x[q - layer];
                        y[q] = s;
                    }
            });
        }

        /** z = T⁻¹ r je Säule (Thomas-Verfahren, die senkrechte Kopplung genau). */
        void precond(double[] r, double[] z) {
            IntStream.range(0, nz).parallel().forEach(j -> {
                double[] cp = new double[n], dp = new double[n];
                for (int i = 0; i < nx; i++) {
                    int base = j * nx + i;
                    // a_k = −kU[k−1], b_k = diag, c_k = −kU[k]
                    double b0 = diag[base];
                    cp[0] = -kU[base] / b0;
                    dp[0] = r[base] / b0;
                    for (int k = 1; k < n; k++) {
                        int q = base + k * layer;
                        double a = -kU[q - layer], cc = k < n - 1 ? -kU[q] : 0;
                        double m = diag[q] - a * cp[k - 1];
                        cp[k] = cc / m;
                        dp[k] = (r[q] - a * dp[k - 1]) / m;
                    }
                    z[base + (n - 1) * layer] = dp[n - 1];
                    for (int k = n - 2; k >= 0; k--) z[base + k * layer] = dp[k] - cp[k] * z[base + (k + 1) * layer];
                }
            });
        }
    }

    static double dot(double[] a, double[] b) {
        int parts = 64, len = a.length;
        return IntStream.range(0, parts).parallel().mapToDouble(p -> {
            int s = (int) ((long) len * p / parts), e = (int) ((long) len * (p + 1) / parts);
            double t = 0;
            for (int i = s; i < e; i++) t += a[i] * b[i];
            return t;
        }).sum();
    }

    static void axpy(double a, double[] x, double[] y) {
        int parts = 64, len = x.length;
        IntStream.range(0, parts).parallel().forEach(p -> {
            int s = (int) ((long) len * p / parts), e = (int) ((long) len * (p + 1) / parts);
            for (int i = s; i < e; i++) y[i] += a * x[i];
        });
    }

    /** p = z + beta · p */
    static void xpby(double[] z, double beta, double[] p) {
        int parts = 64, len = z.length;
        IntStream.range(0, parts).parallel().forEach(q -> {
            int s = (int) ((long) len * q / parts), e = (int) ((long) len * (q + 1) / parts);
            for (int i = s; i < e; i++) p[i] = z[i] + beta * p[i];
        });
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
