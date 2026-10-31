package com.dan.dune;

import java.util.Locale;

/**
 * Das Feld des Flugsands über einem Rechteck, das in Windrichtung liegt (seit Sylt Phase 8). Je Zeile in Windrichtung:
 * <ol>
 * <li>Boden h aus {@link Dune#ground}, darüber die Hülle des Windschattens (vom Kamm mit {@link Dune#shadowDeg} abwärts);</li>
 * <li>Störung der Schubspannung durch den Boden nach Kroy, Sauermann und Herrmann (2002): τ̂ = A·(ℋ[h'] + B·h') mit der
 * Hilbert-Transformation ℋ der Steigung der Hülle, u* = u*₀·√(1 + τ̂) – Beschleunigung über dem Kamm, etwas luvwärts
 * davon, Bremsung am Fuß; im Windschatten fliegt nichts;</li>
 * <li>Schwelle am Ort mit Feuchte, Bewuchs und Hang ({@link Dune#slopeFactor}), Sättigungsfluss nach Bagnold;</li>
 * <li>der tatsächliche Fluss läuft dem Sättigungsfluss mit der Sättigungslänge ℓ nach: dq/ds = (q_sat − q)/ℓ (Sauermann
 * u. a. 2001), exakt je Zelle integriert. Daraus die Ablagerung D = −dq/ds in kg/(m²·s): wo der Wind den Sand nicht mehr
 * tragen kann (nasser Strand, Strandhafer, Windschatten) lagert er sich ab, wo er auffrischt, nimmt er ihn auf.</li>
 * </ol>
 * Der Sand bleibt erhalten: je Zeile ist die Summe der Ablagerung gleich dem Fluss hinein minus dem Fluss hinaus
 * ({@link #massBalance}). Wind gilt im ganzen Feld aus einer Richtung (die in der Mitte), seine Stärke je Ort.
 */
public final class FluxField {
    public final Dune dune;
    /** Mitte (x, z), Länge in Windrichtung, Breite quer, Zelle, alle in m; Zahl der Zellen längs und quer. */
    public final double cx, cz, length, width, cell;
    public final int ns, nn;
    /** Wohin der Wind weht (Einheitsvektor in x, z) und woher (Grad von Nord). */
    public final double tx, tz, fromDeg;
    /** Je Zelle (Zeile quer j, Spalte längs i → j·ns + i): Boden, Hülle, u*, Schwelle, Sättigungsfluss, Fluss, Ablagerung, Feuchte, Bewuchs. */
    public final float[] h, env, ustar, thr, qsat, q, dep, moist, cover;
    /** Fluss am Rand hinein als Anteil der Sättigung (0: Sand fängt am Rand an zu fliegen, etwa an der Wasserlinie). */
    public double inflow;
    /** Zeit der letzten Rechnung in ms. */
    public double ms;
    private double[] hilbert;

    public FluxField(Dune d, double cx, double cz, double length, double width, double cell) {
        this.dune = d;
        this.cx = cx;
        this.cz = cz;
        this.length = length;
        this.width = width;
        this.cell = cell;
        ns = Math.max(4, (int) Math.round(length / cell));
        nn = Math.max(1, (int) Math.round(width / cell));
        fromDeg = d.windFromAt(cx, cz);
        double fr = Math.toRadians(fromDeg);
        tx = -Math.sin(fr);
        tz = Math.cos(fr);
        int n = ns * nn;
        h = new float[n]; env = new float[n]; ustar = new float[n]; thr = new float[n]; qsat = new float[n];
        q = new float[n]; dep = new float[n]; moist = new float[n]; cover = new float[n];
    }

    /** Weltpunkt (x, z) der Zellmitte i (längs), j (quer). */
    public double[] world(double i, double j) {
        double s = (i + 0.5) * cell - length / 2, n = (j + 0.5) * cell - width / 2;
        return new double[]{cx + s * tx - n * tz, cz + s * tz + n * tx};
    }

    /** Zellkoordinaten (i, j, als Kommazahlen) des Weltpunkts. */
    public double[] local(double x, double z) {
        double dx = x - cx, dz = z - cz, s = dx * tx + dz * tz, n = -dx * tz + dz * tx;
        return new double[]{(s + length / 2) / cell - 0.5, (n + width / 2) / cell - 0.5};
    }

    /** Rechnet das ganze Feld neu (Wind, Feuchte, Bewuchs und Boden über die Schnittstellen von {@link Dune}). */
    public FluxField compute() {
        long t0 = System.nanoTime();
        Dune d = dune;
        double ls = d.saturationLength(), decay = Math.exp(-cell / ls), shadow = Math.tan(Math.toRadians(d.shadowDeg)), utDry = d.thresholdDry();
        if (hilbert == null) {
            hilbert = new double[ns];
            for (int k = 1; k < ns; k++) hilbert[k] = 1 / (Math.PI * k);   // ℋ[f]_i = Σ f_j / (π (i − j)), Abstände in Zellen
        }
        double[] slope = new double[ns];
        for (int j = 0; j < nn; j++) {
            int row = j * ns;
            for (int i = 0; i < ns; i++) {
                double[] p = world(i, j);
                h[row + i] = (float) d.ground.height(p[0], p[1]);
                moist[row + i] = (float) d.moisture.moisture(p[0], p[1]);
                cover[row + i] = (float) d.cover.lambda(p[0], p[1]);
            }
            // Hülle des Windschattens: von jedem Kamm mit der Neigung des Schattens abwärts
            double e = h[row];
            for (int i = 0; i < ns; i++) {
                e = Math.max(h[row + i], e - shadow * cell);
                env[row + i] = (float) e;
            }
            for (int i = 0; i < ns; i++) {
                int a = Math.max(0, i - 1), b = Math.min(ns - 1, i + 1);
                slope[i] = (env[row + b] - env[row + a]) / ((b - a) * cell);
            }
            double qPrev = Double.NaN;
            for (int i = 0; i < ns; i++) {
                int c = row + i;
                double hil = 0;
                for (int k = 0; k < ns; k++) if (k != i) hil += slope[k] * (i > k ? hilbert[i - k] : -hilbert[k - i]);
                double tau = d.kroyA * (hil + d.kroyB * slope[i]);
                tau = Math.max(-1, Math.min(2.5, tau));
                double[] p = world(i, j);
                double u0 = d.ustarAt(p[0], p[1]);
                boolean inShadow = h[c] < env[c] - 0.02;
                double us = inShadow ? 0 : u0 * Math.sqrt(1 + tau);
                double hs = (h[Math.min(ns - 1, i + 1) + row] - h[Math.max(0, i - 1) + row]) / ((Math.min(ns - 1, i + 1) - Math.max(0, i - 1)) * cell);
                double ut = utDry * d.wetFactor(moist[c]) * d.coverFactor(cover[c]) * d.slopeFactor(hs);
                double qs = d.flux(us, ut);
                ustar[c] = (float) us;
                thr[c] = (float) Math.min(ut, 99);   // gebunden: als 99 m/s (bilinear ohne Unendlich)
                qsat[c] = (float) qs;
                double qIn = i == 0 ? inflow * qs : qPrev;
                double qq = qs + (qIn - qs) * decay;
                q[c] = (float) qq;
                dep[c] = (float) ((qIn - qq) / cell);
                qPrev = qq;
            }
        }
        if (d.onDeposit != null || d.onScour != null) {
            for (int j = 0; j < nn; j++)
                for (int i = 0; i < ns; i++) {
                    double r = dep[j * ns + i] / d.bulkDensity;
                    double[] p = world(i, j);
                    if (r > d.eventRate && d.onDeposit != null) d.onDeposit.deposit(p[0], p[1], r);
                    else if (r < -d.eventRate && d.onScour != null) d.onScour.deposit(p[0], p[1], r);
                }
        }
        ms = (System.nanoTime() - t0) / 1e6;
        return this;
    }

    private double sample(float[] a, double x, double z) {
        double[] l = local(x, z);
        if (l[0] < -0.5001 || l[1] < -0.5001 || l[0] > ns - 0.4999 || l[1] > nn - 0.4999) return Double.NaN;
        double u = Math.max(0, Math.min(ns - 1, l[0])), v = Math.max(0, Math.min(nn - 1, l[1]));
        int i = Math.min(ns - 2, (int) u), j = Math.min(Math.max(0, nn - 2), (int) v);
        double fu = u - i, fv = nn == 1 ? 0 : v - j;
        int j1 = Math.min(nn - 1, j + 1);
        double a0 = a[j * ns + i] * (1 - fu) + a[j * ns + i + 1] * fu, a1 = a[j1 * ns + i] * (1 - fu) + a[j1 * ns + i + 1] * fu;
        return a0 * (1 - fv) + a1 * fv;
    }

    /** Fluss am Ort in kg/(m·s) (außerhalb: NaN). */
    public double fluxAt(double x, double z) { return sample(q, x, z); }

    /** Sättigungsfluss am Ort in kg/(m·s). */
    public double saturatedAt(double x, double z) { return sample(qsat, x, z); }

    /** u* am Ort mit Beschleunigung über dem Boden, m/s. */
    public double ustarAt(double x, double z) { return sample(ustar, x, z); }

    /** Schwelle am Ort mit Feuchte, Bewuchs und Hang, m/s. */
    public double thresholdAt(double x, double z) { return sample(thr, x, z); }

    /** Ablagerung am Ort in kg/(m²·s) (negativ: der Wind nimmt Sand auf). */
    public double depositAt(double x, double z) { return sample(dep, x, z); }

    /** Ablagerung als Höhenänderung in m/s (Schüttdichte {@link Dune#bulkDensity}). */
    public double depositRateAt(double x, double z) { return depositAt(x, z) / dune.bulkDensity; }

    /** Liegt der Ort im Windschatten hinter einem Kamm? */
    public boolean shadowAt(double x, double z) {
        double[] l = local(x, z);
        int i = (int) Math.round(l[0]), j = (int) Math.round(l[1]);
        if (i < 0 || j < 0 || i >= ns || j >= nn) return false;
        return h[j * ns + i] < env[j * ns + i] - 0.02;
    }

    /**
     * Sandbilanz je Zeile, größte Abweichung in kg/(m·s): Σ D·Δs − (q hinein − q hinaus). Null bis auf Rundung (float).
     */
    public double massBalance() {
        double worst = 0;
        for (int j = 0; j < nn; j++) {
            int row = j * ns;
            double sum = 0;
            for (int i = 0; i < ns; i++) sum += dep[row + i] * cell;
            double qIn = inflow * qsat[row], qOut = q[row + ns - 1];
            worst = Math.max(worst, Math.abs(sum - (qIn - qOut)) / Math.max(1e-6, Math.abs(qIn) + Math.abs(qOut) + 1e-3));
        }
        return worst;
    }

    /** Größter Fluss im Feld, kg/(m·s). */
    public double maxFlux() {
        double m = 0;
        for (float v : q) m = Math.max(m, v);
        return m;
    }

    /** „Flugsand 160 × 60 m zu 0,5 m aus 270°: u* bis 0,52 m/s, q bis 0,031 kg/(m·s), ℓ 1,4 m, 18 % fliegt (4,1 ms)“ */
    public String describe() {
        double um = 0;
        int fly = 0;
        for (int k = 0; k < q.length; k++) {
            um = Math.max(um, ustar[k]);
            if (q[k] > 1e-5) fly++;
        }
        return String.format(Locale.GERMAN, "Flugsand %.0f × %.0f m zu %.2f m aus %03.0f°: u* bis %.2f m/s, q bis %.3f kg/(m·s), ℓ %.1f m, %d %% fliegt (%.1f ms)",
                length, width, cell, fromDeg, um, maxFlux(), dune.saturationLength(), Math.round(100.0 * fly / q.length), ms);
    }
}
