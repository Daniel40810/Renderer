package com.dan.debris;

import java.util.Locale;

/**
 * Der Holzteppich als Feld (seit LituyaBay1958 Phase 12, Werkbuch 5.5): je Zelle der bedeckte Anteil φ der
 * Wasserfläche, für weit draußen und für Tage, wenn einzelne Stämme zu teuer wären. Er treibt mit der Strömung plus
 * α/(1 + α) des Windes (α wie ein Stamm, {@link Debris#windage}) und staut sich, wo er nicht weiter kann: Keine Zelle
 * nimmt mehr auf als φ<sub>max</sub> (dichte Packung liegender Stämme [G 0,6]), der Rest bleibt stehen, wo er ist. Wo
 * das Wasser flacher ist als der Tiefgang oder Land beginnt, strandet, was hineintreibt (eigenes Feld, bleibt liegen).
 * Upwind, Teilschritte nach CFL; die Fläche bleibt genau.
 */
public final class Carpet {
    public final int nx, nz;
    public final double dx, x0, z0;
    /** Höchste Bedeckung, Tiefgang (m), Windanteil α. */
    public double maxCover = 0.6, draft = 0.25, windage = 0.035;
    public Debris.Water water;
    public Debris.Wind wind = (x, z, t) -> new double[2];
    final double[] cover, stranded;
    double time;

    public Carpet(double x0, double z0, int nx, int nz, double dx, Debris.Water water) {
        this.nx = nx;
        this.nz = nz;
        this.dx = dx;
        this.x0 = x0;
        this.z0 = z0;
        this.water = water;
        cover = new double[nx * nz];
        stranded = new double[nx * nz];
    }

    /** Windanteil und Tiefgang wie das Stück l. */
    public Carpet like(Debris d, Debris.Log l) { windage = d.windage(l); draft = l.draft(d.rhoWater); return this; }

    int cell(double x, double z) {
        int i = (int) Math.floor((x - x0) / dx), j = (int) Math.floor((z - z0) / dx);
        return i < 0 || j < 0 || i >= nx || j >= nz ? -1 : j * nx + i;
    }

    double cx(int i) { return x0 + (i + 0.5) * dx; }

    double cz(int j) { return z0 + (j + 0.5) * dx; }

    /** Fläche (m²) an einem Ort hinzufügen. */
    public void addArea(double x, double z, double area) {
        int c = cell(x, z);
        if (c < 0) return;
        if (floats(c % nx, c / nx)) cover[c] += area / (dx * dx); else stranded[c] += area / (dx * dx);
    }

    /** Bedeckung φ in einem Rechteck setzen. */
    public void fill(double xa, double za, double xb, double zb, double phi) {
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) if (cx(i) >= xa && cx(i) < xb && cz(j) >= za && cz(j) < zb) cover[j * nx + i] = phi;
    }

    boolean floats(int i, int j) {
        double d = water.depth(cx(i), cz(j));
        return !Double.isNaN(d) && d > draft;
    }

    /** Treiben bis tEnd. */
    public void run(double tEnd) {
        while (time < tEnd - 1e-9) {
            double[] u = new double[nx * nz], w = new double[nx * nz];
            boolean[] fl = new boolean[nx * nz];
            double vmax = 1e-6;
            double f = windage / (1 + windage);
            for (int j = 0; j < nz; j++)
                for (int i = 0; i < nx; i++) {
                    int c = j * nx + i;
                    fl[c] = floats(i, j);
                    if (!fl[c]) continue;
                    double[] cur = water.current(cx(i), cz(j)), wd = wind.at(cx(i), cz(j), time);
                    u[c] = cur[0] + f * wd[0];
                    w[c] = cur[1] + f * wd[1];
                    vmax = Math.max(vmax, Math.max(Math.abs(u[c]), Math.abs(w[c])));
                }
            double dt = Math.min(tEnd - time, 0.25 * dx / vmax);
            step(dt, u, w, fl);
            time += dt;
        }
    }

    void step(double dt, double[] u, double[] w, boolean[] fl) {
        int n = nx * nz;
        // gewünschte Flüsse über die Kanten (Anteil der Zellfläche), dann je Empfänger auf den freien Platz begrenzt
        double[] fx = new double[(nx + 1) * nz], fz = new double[nx * (nz + 1)];
        double[] in = new double[n];
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx - 1; i++) {
                int a = j * nx + i, b = a + 1;
                if (!fl[a] && !fl[b]) continue;
                double uf = 0.5 * ((fl[a] ? u[a] : 0) + (fl[b] ? u[b] : 0));
                double q = uf > 0 ? (fl[a] ? cover[a] * uf : 0) : (fl[b] ? cover[b] * uf : 0);
                fx[j * (nx + 1) + i + 1] = q * dt / dx;
                in[uf > 0 ? b : a] += Math.abs(q * dt / dx);
            }
        for (int j = 0; j < nz - 1; j++)
            for (int i = 0; i < nx; i++) {
                int a = j * nx + i, b = a + nx;
                if (!fl[a] && !fl[b]) continue;
                double wf = 0.5 * ((fl[a] ? w[a] : 0) + (fl[b] ? w[b] : 0));
                double q = wf > 0 ? (fl[a] ? cover[a] * wf : 0) : (fl[b] ? cover[b] * wf : 0);
                fz[(j + 1) * nx + i] = q * dt / dx;
                in[wf > 0 ? b : a] += Math.abs(q * dt / dx);
            }
        double[] scale = new double[n];
        for (int c = 0; c < n; c++) {
            double room = Math.max(0, maxCover - cover[c] - stranded[c]);
            scale[c] = in[c] <= room ? 1 : room / in[c];
        }
        double[] nc = cover.clone(), ns = stranded.clone();
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx - 1; i++) {
                double q = fx[j * (nx + 1) + i + 1];
                if (q == 0) continue;
                int a = j * nx + i, b = a + 1, from = q > 0 ? a : b, to = q > 0 ? b : a;
                double m = Math.abs(q) * scale[to];
                nc[from] -= m;
                if (fl[to]) nc[to] += m; else ns[to] += m;
            }
        for (int j = 0; j < nz - 1; j++)
            for (int i = 0; i < nx; i++) {
                double q = fz[(j + 1) * nx + i];
                if (q == 0) continue;
                int a = j * nx + i, b = a + nx, from = q > 0 ? a : b, to = q > 0 ? b : a;
                double m = Math.abs(q) * scale[to];
                nc[from] -= m;
                if (fl[to]) nc[to] += m; else ns[to] += m;
            }
        System.arraycopy(nc, 0, cover, 0, n);
        System.arraycopy(ns, 0, stranded, 0, n);
    }

    // ------------------------------------------------------------------ Abfragen

    public double time() { return time; }

    /** Bedeckung φ (schwimmend) und gestrandet am Ort. */
    public double[] at(double x, double z) { int c = cell(x, z); return c < 0 ? new double[2] : new double[]{cover[c], stranded[c]}; }

    /** Fläche (m²): schwimmend, gestrandet. */
    public double[] area() {
        double a = 0, s = 0;
        for (int c = 0; c < cover.length; c++) { a += cover[c]; s += stranded[c]; }
        return new double[]{a * dx * dx, s * dx * dx};
    }

    /** Schwerpunkt der schwimmenden Fläche {x, z}. */
    public double[] centroid() {
        double s = 0, sx = 0, sz = 0;
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) { double m = cover[j * nx + i]; s += m; sx += m * cx(i); sz += m * cz(j); }
        return s <= 0 ? new double[]{Double.NaN, Double.NaN} : new double[]{sx / s, sz / s};
    }

    /** Größte Bedeckung (schwimmend + gestrandet) einer Zelle. */
    public double maxCoverNow() {
        double m = 0;
        for (int c = 0; c < cover.length; c++) m = Math.max(m, cover[c] + stranded[c]);
        return m;
    }

    /** Feld für die Darstellung (Zeilen j): Bedeckung und gestrandet. */
    public float[][] snapshot() {
        float[] a = new float[cover.length], b = new float[cover.length];
        for (int c = 0; c < a.length; c++) { a[c] = (float) cover[c]; b[c] = (float) stranded[c]; }
        return new float[][]{a, b};
    }

    public String describe() {
        double[] a = area();
        return String.format(Locale.GERMAN, "Holzteppich %d × %d zu %.0f m: %.0f m² schwimmend, %.0f m² gestrandet, Windanteil %.1f %%", nx, nz, dx, a[0], a[1], 100 * windage / (1 + windage));
    }
}
