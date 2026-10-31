package com.dan.dune;

import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Dünenwanderung im Zeitraffer nach dem Plattenmodell von Werner (1995), mit Bewuchs nach Baas (2002) und Nield und
 * Baas (2008) (seit Sylt Phase 9, F12 A). Auf einem {@link SandBed}:
 * <ol>
 * <li>Eine zufällige Zelle wird gewählt. Liegt dort Sand, nicht im Windschatten (Hülle unter {@link #shadowDeg}), nicht
 * nass und nicht vom Strandhafer oder einem Zaun gehalten, hebt der Wind eine Platte ab.</li>
 * <li>Die Platte springt {@link #hop} Zellen in Windrichtung. Sie bleibt liegen mit der Wahrscheinlichkeit
 * {@link #pSand} auf Sand, {@link #pBare} auf festem Grund, {@link #pWet} auf nassem Sand, 1 im Windschatten; Strandhafer
 * und Zäune fangen zusätzlich. Sonst springt sie weiter.</li>
 * <li>Wo ein Hang steiler als der Schüttwinkel wird, rutschen Platten ab (Lawinen auf der Rutschfläche).</li>
 * </ol>
 * Der Sand bleibt erhalten: Platten verlassen das Feld nur über den Rand in Windrichtung ({@link SandBed#outflow}) und
 * kommen nur von der Luvseite herein ({@link #supply}, {@link SandBed#inflow}). Quer zum Wind ist das Feld geschlossen
 * zum Ring. Mit {@link #periodic} ist es auch in Windrichtung ein Ring (für die wandernde Düne in der Prüfung).
 * <p>
 * Zeit: Je Zelle hebt der Wind im Mittel r = q / (ρ_b·h·D) Platten je Sekunde ab, q der Fluss nach Bagnold beim Wind von
 * {@link Dune}, h die Plattendicke, D = hop·a/pSand der mittlere Weg; der Wind weht so stark den Anteil {@link #duty} der
 * Zeit [G]. So wandert eine Düne der Höhe H um c = q·duty/(ρ_b·H).
 */
public final class Werner {
    public final SandBed bed;
    private final SplittableRandom rnd;
    /** Sprungweite in Zellen, Liegenbleiben auf Sand, festem Grund, nassem Sand [G nach Werner 1995: L = 1…5, p 0,6 und 0,4]. */
    public int hop = 4;
    public double pSand = 0.6, pBare = 0.4, pWet = 0.5;
    /** Hülle des Windschattens (Grad, Werner: 15°). */
    public double shadowDeg = 15;
    /** Strandhafer: hält Sand fest (Anteil weniger Abheben) und fängt ihn (Anteil mehr Liegenbleiben) bei voller Bedeckung [G nach Nield und Baas 2008]. */
    public double vegHold = 0.9, vegTrap = 0.8;
    /** Nässe, ab der der Sand nicht abhebt (Massenanteil). */
    public double wetLimit = 0.04;
    /** Wie viel Sand von Luv hereinweht, als Anteil des Sättigungsflusses (0: nichts, 1: gesättigt vom Strand davor). */
    public double supply = 1;
    /** Anteil der Zeit, in der der Wind so weht (der Rest liegt unter der Schwelle) [G]. */
    public double duty = 0.08;
    /** In Windrichtung als Ring (ohne Zu- und Abfluss). */
    public boolean periodic;
    /**
     * Die Tide räumt den nassen Strand: Bei jedem Wachsen des Strandhafers setzt sie den Sand in nassen Zellen und am
     * Strand, den das Meer hält ({@link SandBed#sea}), auf den Anfang zurück; was sie nimmt, zählt als hinaus, was sie
     * bringt, als herein (Sand bleibt in der Bilanz).
     */
    public boolean tide = true;
    private int[] wetStart;
    /** Ereignisse (Abheben, das stattfand), Rechenzeit des letzten Laufs (ms). */
    public long moves;
    public double ms;

    private final float[] env;
    private final boolean[] dirtyRow;
    private final double qRef, rate;
    private int stepsSinceGrow;
    private double yearsSinceGrow;

    public Werner(SandBed b) {
        bed = b;
        rnd = new SplittableRandom(b.dune.seed * 31 + 5);
        env = new float[b.nx * b.ny];
        dirtyRow = new boolean[b.ny];
        java.util.Arrays.fill(dirtyRow, true);
        Dune d = b.dune;
        qRef = d.flux(d.ustarAt(b.cx, b.cz), d.thresholdDry());
        double dist = hop * b.cell / pSand;
        rate = qRef / (d.bulkDensity * b.slab * dist);
        wetStart = b.sand.clone();
    }

    /** Platten, um die zwei Nachbarn höchstens auseinander liegen dürfen (Schüttwinkel). */
    int reposeSlabs() { return (int) Math.floor(Math.tan(Math.toRadians(bed.dune.reposeDeg)) * bed.cell / bed.slab); }

    /** Abheben je Zelle und Sekunde bei diesem Wind (1/s), mit duty. */
    public double rate() { return rate * duty; }

    /** Sättigungsfluss bei diesem Wind, kg/(m·s). */
    public double qRef() { return qRef; }

    /** Zeitraffer um so viele Jahre. Gibt die Zahl der Schritte zurück. */
    public long advanceYears(double years) {
        long t0 = System.nanoTime();
        double perYear = rate() * 3.15576e7 * (bed.nx + (periodic ? 0 : hop)) * bed.ny;
        long steps = (long) Math.round(perYear * years);
        double yearsPerStep = steps > 0 ? years / steps : 0;
        for (long s = 0; s < steps; s++) {
            step();
            yearsSinceGrow += yearsPerStep;
            if (++stepsSinceGrow >= bed.nx * bed.ny) growNow();
        }
        if (steps == 0) yearsSinceGrow += years;
        growNow();
        ms = (System.nanoTime() - t0) / 1e6;
        return steps;
    }

    private void growNow() {
        if (tide && !periodic) {
            if (wetStart == null) wetStart = bed.sand.clone();
            for (int k = 0; k < bed.sand.length; k++)
                if ((bed.moist[k] > wetLimit || bed.sea[k] != 0) && bed.sand[k] != wetStart[k]) {
                    int d = bed.sand[k] - wetStart[k];
                    if (d > 0) bed.outflow += d; else bed.inflow -= d;
                    bed.sand[k] = wetStart[k];
                    dirtyRow[k / bed.nx] = true;
                }
        }
        bed.grow(yearsSinceGrow);
        bed.years += yearsSinceGrow;
        yearsSinceGrow = 0;
        stepsSinceGrow = 0;
    }

    /** Ein Schritt: Zelle wählen, gegebenenfalls Platte abheben und springen lassen. */
    public void step() {
        int nx = bed.nx, ny = bed.ny;
        int extra = periodic ? 0 : hop;
        int pick = rnd.nextInt((nx + extra) * ny);
        int j = pick / (nx + extra), i = pick % (nx + extra) - extra;
        if (i < 0) {
            // von Luv herein: so viel, wie ein gesättigter Strand davor abgibt
            if (rnd.nextDouble() >= supply) return;
            bed.inflow++;
            moves++;
            fly(i, j);
            return;
        }
        int k = j * nx + i;
        if (bed.sand[k] == 0) return;
        if (shadow(i, j)) return;
        if (bed.moist[k] > wetLimit) return;
        double hold = vegHold * bed.veg[k] * bed.exposed(k);
        if (bed.fenceHeight(k) > 0) hold = Math.max(hold, 1 - bed.fencePor[k]);
        if (hold > 0 && rnd.nextDouble() < hold) return;
        bed.sand[k]--;
        dirtyRow[j] = true;
        avalancheInto(i, j);
        moves++;
        fly(i, j);
    }

    /** Die Platte springt von (i, j) in Windrichtung, bis sie liegen bleibt oder das Feld verlässt. */
    private void fly(int i, int j) {
        int nx = bed.nx;
        for (int guard = 0; guard < 10000; guard++) {
            i += hop;
            if (i >= nx) {
                if (!periodic) { bed.outflow++; return; }
                i -= nx;
            }
            int k = j * nx + i;
            double p;
            if (shadow(i, j)) p = 1;
            else {
                if (bed.moist[k] > wetLimit) p = pWet;
                else p = bed.sand[k] > 0 ? pSand : pBare;
                double trap = vegTrap * bed.veg[k] * bed.exposed(k);
                p += (1 - p) * trap;
                double fz = fenceLee(i, j);
                p += (1 - p) * fz;
            }
            if (rnd.nextDouble() < p) {
                bed.sand[k]++;
                dirtyRow[j] = true;
                avalancheFrom(i, j);
                return;
            }
        }
    }

    /** Wirkung eines Zauns in Luv (0…1): in der Zaunzelle und im Lee bis zur zehnfachen Höhe, abnehmend. */
    double fenceLee(int i, int j) {
        double best = 0;
        int reach = (int) Math.ceil(12 / bed.cell);
        for (int d = 0; d <= reach && i - d >= 0; d++) {
            int k = j * bed.nx + i - d;
            double h = bed.fenceHeight(k);
            if (h <= 0) continue;
            double x = d * bed.cell / (10 * h);
            if (x < 1) best = Math.max(best, (1 - bed.fencePor[k]) * (1 - x));
        }
        return best;
    }

    /** Liegt die Zelle im Windschatten? (Hülle der Zeile, bei Bedarf neu.) */
    boolean shadow(int i, int j) {
        if (dirtyRow[j]) envelope(j);
        int k = j * bed.nx + i;
        return bed.surface(k) < env[k] - 1e-4;
    }

    private void envelope(int j) {
        int nx = bed.nx, row = j * nx;
        double drop = Math.tan(Math.toRadians(shadowDeg)) * bed.cell;
        double e = periodic ? maxSurface(row) : bed.surface(row);
        // im Ring zweimal herum, damit der Schatten über den Rand reicht
        for (int pass = 0; pass < (periodic ? 2 : 1); pass++)
            for (int i = 0; i < nx; i++) {
                e = Math.max(bed.surface(row + i), e - drop);
                env[row + i] = (float) e;
            }
        dirtyRow[j] = false;
    }

    private double maxSurface(int row) {
        double m = -1e9;
        for (int i = 0; i < bed.nx; i++) m = Math.max(m, bed.surface(row + i));
        return m - 1e3;   // Anfang tief, der zweite Umlauf trägt den Schatten über den Rand
    }

    // ------------------------------------------------------------------ Lawinen

    private int idx(int i, int j) {
        if (j < 0) j += bed.ny; else if (j >= bed.ny) j -= bed.ny;
        if (i < 0 || i >= bed.nx) {
            if (!periodic) return -1;
            i = (i + bed.nx) % bed.nx;
        }
        return j * bed.nx + i;
    }

    /** Nach dem Ablegen: ist (i, j) zu hoch über einem Nachbarn, rutscht die Platte zum tiefsten weiter. */
    private void avalancheFrom(int i, int j) {
        int lim = reposeSlabs();
        double thr = lim * bed.slab + 1e-6;
        for (int guard = 0; guard < 1000; guard++) {
            int k = idx(i, j);
            double s = bed.surface(k), low = s;
            int bi = 0, bj = 0;
            int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] o : nb) {
                int q = idx(i + o[0], j + o[1]);
                if (q < 0) continue;
                double t = bed.surface(q);
                if (t < low) { low = t; bi = o[0]; bj = o[1]; }
            }
            if (s - low <= thr) return;
            bed.sand[k]--;
            int ni = i + bi, nj = j + bj;
            if (periodic) ni = (ni + bed.nx) % bed.nx;
            nj = (nj + bed.ny) % bed.ny;
            bed.sand[nj * bed.nx + ni]++;
            dirtyRow[j] = true;
            dirtyRow[nj] = true;
            i = ni;
            j = nj;
        }
    }

    /** Nach dem Abheben: ist ein Nachbar zu hoch über (i, j), rutscht eine Platte herein (und so fort bergauf). */
    private void avalancheInto(int i, int j) {
        int lim = reposeSlabs();
        double thr = lim * bed.slab + 1e-6;
        for (int guard = 0; guard < 1000; guard++) {
            int k = idx(i, j);
            double s = bed.surface(k), high = s;
            int bi = 0, bj = 0;
            int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] o : nb) {
                int q = idx(i + o[0], j + o[1]);
                if (q < 0 || bed.sand[q] == 0) continue;
                double t = bed.surface(q);
                if (t > high) { high = t; bi = o[0]; bj = o[1]; }
            }
            if (high - s <= thr) return;
            int ni = i + bi, nj = j + bj;
            if (periodic) ni = (ni + bed.nx) % bed.nx;
            nj = (nj + bed.ny) % bed.ny;
            bed.sand[nj * bed.nx + ni]--;
            bed.sand[k]++;
            dirtyRow[j] = true;
            dirtyRow[nj] = true;
            i = ni;
            j = nj;
        }
    }

    /** Einmal über das ganze Feld abrutschen lassen, bis kein Hang steiler als der Schüttwinkel ist (nach dem Anlegen). */
    public void relax() {
        for (int pass = 0; pass < 50; pass++) {
            boolean moved = false;
            for (int j = 0; j < bed.ny; j++)
                for (int i = 0; i < bed.nx; i++) {
                    int k = j * bed.nx + i, before = bed.sand[k];
                    if (before > 0) avalancheFrom(i, j);
                    if (bed.sand[k] != before) moved = true;
                }
            if (!moved) break;
        }
        java.util.Arrays.fill(dirtyRow, true);
    }

    /** „Werner: q 0,021 kg/(m·s) 15 % der Zeit, 236 Abhebungen je Zelle und Jahr, 1,9 Mio. Schritte in 840 ms“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Werner: q %.3f kg/(m·s) %.0f %% der Zeit, %.0f Abhebungen je Zelle und Jahr, Schüttwinkel %.0f°, %,d bewegt (%.0f ms)",
                qRef, duty * 100, rate() * 3.15576e7, Math.toDegrees(Math.atan(reposeSlabs() * bed.slab / bed.cell)), moves, ms);
    }
}
