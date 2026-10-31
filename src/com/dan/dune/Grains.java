package com.dan.dune;

import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Saltation als Körner (seit Sylt Phase 8): Sand springt in flachen Bögen knapp über dem Boden, zieht in Fahnen über den
 * Strand und weht als Kammfahne über die Dünen. Jedes Korn im Bild steht für ein Gewicht an Sand ({@link #weight}, kg),
 * so gewählt, dass um das Auge ungefähr {@link #budget} Körner fliegen.
 * <ul>
 * <li><b>Absprung:</b> je Fläche und Zeit E = q / (m·L), q der Fluss aus dem {@link FluxField} mal dem Faktor der Böen
 * ({@link Dune#gust}: nahe der Schwelle fliegt Sand nur in den Fahnen), L die mittlere Sprungweite bei diesem u*
 * (aus denselben Bahnen gerechnet). So trägt der Strom der Körner genau den Fluss q.</li>
 * <li><b>Flug:</b> senkrecht im Mittel {@link Dune#launch}·u* (exponentiell verteilt), unter {@link Dune#launchDeg} zum
 * Boden; Schwere und Widerstand im logarithmischen Wind, Widerstandsbeiwert nach Schiller und Naumann. Das Korn landet,
 * wo es den Boden trifft – hinter einem Kamm erst weit im Windschatten (Kammfahne).</li>
 * <li><b>Schweb:</b> ein Anteil {@link Dune#finesFraction} feiner Körner fällt langsam und wird von der Turbulenz
 * (K = κ·u*·y) gehoben; über beschleunigten Kämmen steigt er als Fahne auf.</li>
 * </ul>
 * Gibt die Körner als Felder (Ort, Geschwindigkeit, Art) für {@code gl/DuneGl}.
 */
public final class Grains {
    /** Höchstzahl der Körner im Bild und Halbmesser um das Auge, in dem sie entstehen (m). */
    public int budget = 20000;
    public double radius = 40;
    /** Kilogramm Sand je Korn im Bild (wird nachgeführt). */
    public double weight = 1e-4;

    public final Dune dune;
    private final SplittableRandom rnd;
    private int cap;
    /** Ort, Geschwindigkeit; Art 0 = Saltation, 1 = Schweb; Alter in s. */
    public float[] x, y, z, vx, vy, vz, age;
    public byte[] kind;
    public int count;
    /** Für die Prüfung: Masse, die eine Ebene quer zum Wind bei s = gateS durchquert hat (kg je m Breite), und die Höhen dabei. */
    public double gateS = Double.NaN, gateN, gateHalf = 1e9, gateMass, gateHeightMass, gateTime;
    /** Zählt die gelandeten Körner. */
    public long landed;
    private double t, ms;

    public Grains(Dune d) {
        dune = d;
        budget = d.grainBudget();
        rnd = new SplittableRandom(d.seed * 7919 + 13);
        ensure(budget);
    }

    private void ensure(int n) {
        if (cap >= n) return;
        int c = Math.max(n, cap * 2);
        x = grow(x, c); y = grow(y, c); z = grow(z, c); vx = grow(vx, c); vy = grow(vy, c); vz = grow(vz, c); age = grow(age, c);
        byte[] k = new byte[c];
        if (kind != null) System.arraycopy(kind, 0, k, 0, count);
        kind = k;
        cap = c;
    }

    private float[] grow(float[] a, int c) {
        float[] b = new float[c];
        if (a != null) System.arraycopy(a, 0, b, 0, count);
        return b;
    }

    // ------------------------------------------------------------------ Bahn eines Korns

    /** Widerstand: dv/dt = k·|U−v|·(U−v), k = 3ρ·C_D/(4ρs·d), C_D = 24/Re·(1 + 0,15 Re^0,687). */
    double drag(double rel, double d) {
        double re = Math.max(1e-6, rel * d / 1.5e-5), cd = 24 / re * (1 + 0.15 * Math.pow(re, 0.687));
        return 0.75 * dune.rhoAir * cd / (dune.rhoSand * d);
    }

    /**
     * Mittlere Sprungweite bei u* über ebenem Boden (m), aus Bahnen mit der verteilten Absprunggeschwindigkeit gerechnet
     * (Gauß-Laguerre über die Exponentialverteilung). Auch die mittlere Höhe des Flusses (m) in {@code out[1]}.
     */
    public double[] hop(double ustar) {
        double[] xs = {0.2635603197, 1.4134030591, 3.5964257710, 7.0858100059, 12.6408008443};
        double[] ws = {0.5217556106, 0.3986668111, 0.0759424497, 0.0036117587, 0.0000233700};
        double len = 0, hgt = 0;
        for (int k = 0; k < xs.length; k++) {
            double[] r = flight(ustar, dune.launch * ustar * xs[k], dune.grain);
            len += ws[k] * r[0];
            hgt += ws[k] * r[1] * r[0];
        }
        return new double[]{len, len > 0 ? hgt / len : 0};
    }

    /** Eine Bahn über ebenem Boden: {Sprungweite, mittlere Höhe}. */
    double[] flight(double ustar, double w0, double d) {
        double a = Math.toRadians(dune.launchDeg), u = w0 / Math.tan(a), w = w0, px = 0, py = 1e-3, dt = 2e-4, area = 0;
        for (int it = 0; it < 200000; it++) {
            double ua = dune.windAt(ustar, Math.max(py, dune.z0 * 1.01)), ru = ua - u, rw = -w, rel = Math.hypot(ru, rw), k = drag(rel, d) * rel;
            u += k * ru * dt;
            w += (k * rw - Dune.G) * dt;
            px += u * dt;
            py += w * dt;
            area += py * u * dt;
            if (py <= 0) break;
        }
        return new double[]{px, px > 0 ? area / px : 0};
    }

    private double[] hopCache;
    private double hopStep = 0.02;

    /** Sprungweite aus einer Tafel über u* (0…2 m/s). */
    double hopLength(double ustar) {
        if (hopCache == null) {
            hopCache = new double[101];
            for (int i = 0; i < hopCache.length; i++) hopCache[i] = Math.max(1e-3, hop(Math.max(0.05, i * hopStep))[0]);
        }
        double f = Math.max(0, Math.min(hopCache.length - 1.001, ustar / hopStep));
        int i = (int) f;
        return hopCache[i] + (hopCache[i + 1] - hopCache[i]) * (f - i);
    }

    // ------------------------------------------------------------------ Schritt

    /**
     * Ein Schritt: fliegende Körner bewegen, gelandete entfernen, neue um das Auge (ex, ez) aus dem Feld abspringen
     * lassen. Zeit t läuft mit.
     */
    public void step(double dt, FluxField f, double ex, double ez) {
        long t0 = System.nanoTime();
        t += dt;
        Dune d = dune;
        // 1 bewegen
        double fineD = d.finesGrain;
        int w = 0;
        for (int k = 0; k < count; k++) {
            double px = x[k], py = y[k], pz = z[k], u = vx[k], v = vy[k], ww = vz[k];
            double us = f.ustarAt(px, pz);
            if (Double.isNaN(us)) continue;
            double gy = d.ground.height(px, pz), hgt = py - gy;
            double ua = d.windAt(us, Math.max(hgt, d.z0 * 1.01));
            double ux = ua * f.tx - u, uy = -v, uz = ua * f.tz - ww, rel = Math.sqrt(ux * ux + uy * uy + uz * uz);
            double kd = drag(rel, kind[k] == 1 ? fineD : d.grain) * rel;
            // halbimplizit: stabil auch für feine Körner
            double fac = dt * kd / (1 + dt * kd);
            u += ux * fac;
            ww += uz * fac;
            v += uy * fac - Dune.G * dt;
            double nx = px + u * dt, ny = py + v * dt, nz = pz + ww * dt;
            if (kind[k] == 1) {
                // Turbulenz: Zufallsweg mit K = κ·u*·y (und Drift dK/dy)
                double kk = Dune.KARMAN * us * Math.max(0.02, hgt);
                ny += Dune.KARMAN * us * dt + Math.sqrt(2 * kk * dt) * gauss();
            }
            if (!Double.isNaN(gateS)) {
                double s0 = (px - f.cx) * f.tx + (pz - f.cz) * f.tz, s1 = (nx - f.cx) * f.tx + (nz - f.cz) * f.tz;
                double nq = -(px - f.cx) * f.tz + (pz - f.cz) * f.tx;
                if (s0 < gateS && s1 >= gateS && Math.abs(nq - gateN) < gateHalf) { gateMass += weight; gateHeightMass += weight * hgt; }
            }
            double g1 = d.ground.height(nx, nz);
            age[k] += (float) dt;
            if (ny <= g1 || age[k] > 30) {
                if (ny <= g1) landed++;
                continue;
            }
            x[w] = (float) nx; y[w] = (float) ny; z[w] = (float) nz; vx[w] = (float) u; vy[w] = (float) v; vz[w] = (float) ww;
            age[w] = age[k]; kind[w] = kind[k];
            w++;
        }
        count = w;
        if (!Double.isNaN(gateS)) gateTime += dt;
        // 2 abspringen: Masse je Fläche und Zeit E = q·g / L im Kreis um das Auge, abgetastet auf Zellen
        double r = radius, cellA = Math.max(f.cell, 1.0), want = 0;
        int nc = (int) Math.ceil(2 * r / cellA);
        double meanFlight = 0, mass = 0;
        double[][] spots = new double[nc * nc][];
        int ns = 0;
        for (int a = 0; a < nc; a++)
            for (int b = 0; b < nc; b++) {
                double sx = ex - r + (a + rnd.nextDouble()) * cellA, sz = ez - r + (b + rnd.nextDouble()) * cellA;
                if (Math.hypot(sx - ex, sz - ez) > r) continue;
                double qq = f.fluxAt(sx, sz);
                if (Double.isNaN(qq) || qq <= 0) continue;
                double us = f.ustarAt(sx, sz);
                double gust = d.gust(sx, sz, t, us), ut = f.thresholdAt(sx, sz);
                // Böen: Fluss mit u*·Faktor gegen den Fluss ohne, nahe der Schwelle an und aus
                double q0 = d.flux(us, ut), qg = d.flux(us * gust, ut), g = q0 > 0 ? qg / q0 : 0;
                double e = qq * g / hopLength(us) * cellA * cellA;   // kg/s aus dieser Zelle
                if (e <= 0) continue;
                spots[ns++] = new double[]{sx, sz, e, us};
                want += e;
                meanFlight += e * 2 * d.launch * us / Dune.G;
                mass += e;
            }
        // Gewicht so führen, dass die Zahl im Bild beim Budget liegt: N ≈ (Masse je s / Gewicht)·Flugzeit
        if (mass > 0) {
            double tf = meanFlight / mass * 1.3, target = want * tf / Math.max(1, budget * 0.8);
            if (Double.isNaN(gateS)) weight = weight * 0.9 + 0.1 * Math.max(target, 1e-12);
        }
        double fines = d.finesFraction;
        for (int k = 0; k < ns; k++) {
            double[] s = spots[k];
            double expect = s[2] * dt / weight;
            int n = (int) expect;
            if (rnd.nextDouble() < expect - n) n++;
            for (int m = 0; m < n && count < budget * 2; m++) {
                ensure(count + 1);
                double us = s[3], w0 = d.launch * us * -Math.log(1 - rnd.nextDouble()), a = Math.toRadians(d.launchDeg);
                double u0 = w0 / Math.tan(a), side = (rnd.nextDouble() - 0.5) * 0.3 * u0;
                int c = count++;
                x[c] = (float) (s[0] + (rnd.nextDouble() - 0.5) * cellA);
                z[c] = (float) (s[1] + (rnd.nextDouble() - 0.5) * cellA);
                y[c] = (float) (d.ground.height(x[c], z[c]) + 1e-3);
                vx[c] = (float) (u0 * f.tx - side * f.tz);
                vz[c] = (float) (u0 * f.tz + side * f.tx);
                vy[c] = (float) w0;
                age[c] = 0;
                kind[c] = (byte) (rnd.nextDouble() < fines ? 1 : 0);
            }
        }
        ms = (System.nanoTime() - t0) / 1e6;
    }

    /**
     * Staub (seit Sylt Phase 11/12, F15 A): n Körner der Feinfraktion (Schweb) springen aus einem Punkt, etwa wo ein
     * Stück Kliff zerfällt; sie steigen mit bis zu {@code speed} m/s auf und treiben dann mit dem Wind wie der Schweb aus den
     * Fahnen (Turbulenz, Widerstand, Absinken). Gibt die Zahl der neuen Körner zurück (höchstens bis zum doppelten Budget).
     */
    public int burst(double px, double py, double pz, int n, double speed) {
        int k = 0;
        for (int m = 0; m < n && count < budget * 2; m++) {
            ensure(count + 1);
            int c = count++;
            double a = rnd.nextDouble() * 2 * Math.PI, s = speed * (0.3 + 0.7 * rnd.nextDouble());
            x[c] = (float) (px + (rnd.nextDouble() - 0.5));
            z[c] = (float) (pz + (rnd.nextDouble() - 0.5));
            y[c] = (float) (py + rnd.nextDouble() * 0.5);
            vx[c] = (float) (Math.cos(a) * s * 0.6);
            vz[c] = (float) (Math.sin(a) * s * 0.6);
            vy[c] = (float) (s * (0.5 + 0.5 * rnd.nextDouble()));
            age[c] = 0;
            kind[c] = 1;
            k++;
        }
        return k;
    }

    private double gauss() {
        double u = Math.max(1e-12, rnd.nextDouble()), v = rnd.nextDouble();
        return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
    }

    /** Fluss, den die Körner durch die Ebene bei {@link #gateS} getragen haben, kg/(m·s), je m Breite {@code breite}. */
    public double gateFlux(double breite) { return gateTime > 0 ? gateMass / gateTime / breite : 0; }

    /** Mittlere Höhe des Flusses durch die Ebene (m). */
    public double gateHeight() { return gateMass > 0 ? gateHeightMass / gateMass : 0; }

    /** Höchstes Schwebkorn über dem Boden (m). */
    public double finesTop() {
        double m = 0;
        for (int k = 0; k < count; k++) if (kind[k] == 1) m = Math.max(m, y[k] - dune.ground.height(x[k], z[k]));
        return m;
    }

    /** „Saltation: 18 400 Körner zu 0,12 g, Schweb 540 (bis 1,8 m), 2,1 ms“ */
    public String describe() {
        int f = 0;
        for (int k = 0; k < count; k++) if (kind[k] == 1) f++;
        return String.format(Locale.GERMAN, "Saltation: %d Körner zu %.2g g, Schweb %d (bis %.1f m), %.1f ms", count, weight * 1000, f, finesTop(), ms);
    }
}
