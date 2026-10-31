package com.dan.dune;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Das Höhenfeld des losen Sands (seit Sylt Phase 9): ein Raster in Windrichtung wie {@link FluxField}, je Zelle der
 * feste Grund (aus {@link Dune#ground} beim Anlegen) und darauf der lose Sand als ganze Zahl von Platten
 * ({@link #slab} m dick). Dazu je Zelle der Strandhafer (Bedeckung V 0…1 und die Übersandung B, die er noch nicht
 * durchwachsen hat) und die Sandfangzäune. Das Feld ist selbst ein {@link Dune.Ground} (Oberfläche) und ein
 * {@link Dune.Cover} (λ des Strandhafers), so dass {@link FluxField} und {@link Ripples} auf ihm rechnen.
 * <p>
 * Den Sand bewegt {@link Werner} (Plattenmodell im Zeitraffer), den Strandhafer {@link #grow} nach der Übersandung:
 * <ul>
 * <li>Strandhafer (Ammophila arenaria) wächst mit dem Sand: Übersandung bis etwa 1 m im Jahr regt ihn an, er schiebt
 * seine Triebe bis zu {@link #marramGrowth} m im Jahr nach oben (Maun 1998) [G]. Ohne Übersandung lässt er langsam nach
 * (vergreist).</li>
 * <li>Mehr Sand, als er durchwachsen kann: Liegt die Übersandung über seiner Höhe, stirbt er in etwa einem Jahr ab;
 * Abtrag legt die Wurzeln frei und schwächt ihn.</li>
 * <li>Er breitet sich mit Ausläufern aus (bis {@link #marramSpread} m im Jahr), wo Sand kommt.</li>
 * </ul>
 */
public final class SandBed implements Dune.Ground, Dune.Cover {
    /** Ein Sandfangzaun aus Busch (Reisig): von (x0, z0) bis (x1, z1), Höhe über dem Sand beim Setzen (m), Durchlässigkeit 0…1. */
    public record Fence(double x0, double z0, double x1, double z1, double height, double porosity) { }

    public final Dune dune;
    public final double cx, cz, length, width, cell, slab, tx, tz, fromDeg;
    public final int nx, ny;
    /** Fester Grund (m) und loser Sand (Platten) je Zelle (Zeile j quer, Spalte i in Windrichtung → j·nx + i). */
    public final float[] base;
    public final int[] sand;
    /** Strandhafer: Bedeckung 0…1, Übersandung, die er noch durchwachsen muss (m), Sand beim letzten Wachsen (Platten). */
    public final float[] veg, burial;
    /**
     * Strand, den das Meer hält (1) oder nicht (0): Dort setzen Brandung und Tide den Sand bei {@link Werner#tide} auf
     * den Anfang zurück – was der Wind abträgt, bringt die See wieder (auf Sylt auch die Sandvorspülungen) [G].
     */
    public final byte[] sea;
    /** Nässe des Sands je Zelle (aus {@link Dune#moisture}, {@link #refreshMoisture}). */
    public final float[] moist;
    private final int[] lastSand;
    private final float[] trend;
    /** Oberkante der Zäune je Zelle (m, NaN: kein Zaun) und ihre Durchlässigkeit. */
    public final float[] fenceTop, fencePor;
    public final List<Fence> fences = new ArrayList<>();
    /** Höhe des Strandhafers ausgewachsen (m), Wachstum nach oben (m/Jahr), Ausbreitung (m/Jahr), λ bei voller Bedeckung [G]. */
    public double marramHeight = 0.8, marramGrowth = 1.0, marramSpread = 0.6, marramLambda = 0.10;
    /** Zähler: Platten herein und hinaus (über die Ränder), seit dem Anlegen. Jahre im Zeitraffer. */
    public long inflow, outflow;
    public double years;

    /** Ein Feld in Windrichtung (wie {@link FluxField}); der Grund aus {@link Dune#ground}, der lose Sand aus {@code top} − Grund. */
    public SandBed(Dune d, double cx, double cz, double length, double width, double cell, double slab, Dune.Ground fixed, Dune.Ground top) {
        dune = d;
        this.cx = cx;
        this.cz = cz;
        this.length = length;
        this.width = width;
        this.cell = cell;
        this.slab = slab;
        nx = Math.max(4, (int) Math.round(length / cell));
        ny = Math.max(1, (int) Math.round(width / cell));
        fromDeg = d.windFromAt(cx, cz);
        double fr = Math.toRadians(fromDeg);
        tx = -Math.sin(fr);
        tz = Math.cos(fr);
        int n = nx * ny;
        base = new float[n];
        sand = new int[n];
        veg = new float[n];
        burial = new float[n];
        lastSand = new int[n];
        trend = new float[n];
        fenceTop = new float[n];
        fencePor = new float[n];
        moist = new float[n];
        sea = new byte[n];
        java.util.Arrays.fill(fenceTop, Float.NaN);
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                double[] p = world(i, j);
                int k = j * nx + i;
                double g = fixed.height(p[0], p[1]), t = top.height(p[0], p[1]);
                base[k] = (float) g;
                sand[k] = (int) Math.max(0, Math.round((t - g) / slab));
                lastSand[k] = sand[k];
            }
        refreshMoisture();
    }

    /** Nässe je Zelle neu aus {@link Dune#moisture} (nach Regen, Tide). */
    public void refreshMoisture() {
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                double[] p = world(i, j);
                moist[j * nx + i] = (float) dune.moisture.moisture(p[0], p[1]);
            }
    }

    /** Weltpunkt der Zellmitte. */
    public double[] world(double i, double j) {
        double s = (i + 0.5) * cell - length / 2, n = (j + 0.5) * cell - width / 2;
        return new double[]{cx + s * tx - n * tz, cz + s * tz + n * tx};
    }

    /** Zellkoordinaten (Kommazahlen) des Weltpunkts. */
    public double[] local(double x, double z) {
        double dx = x - cx, dz = z - cz, s = dx * tx + dz * tz, n = -dx * tz + dz * tx;
        return new double[]{(s + length / 2) / cell - 0.5, (n + width / 2) / cell - 0.5};
    }

    /** Oberfläche der Zelle (m). */
    public double surface(int k) { return base[k] + sand[k] * slab; }

    /** Oberfläche am Ort, bilinear (außerhalb am Rand gehalten). */
    @Override public double height(double x, double z) {
        double[] l = local(x, z);
        double u = Math.max(0, Math.min(nx - 1, l[0])), v = Math.max(0, Math.min(ny - 1, l[1]));
        int i = Math.min(nx - 2, (int) u), j = Math.min(Math.max(0, ny - 2), (int) v), j1 = Math.min(ny - 1, j + 1);
        double fu = u - i, fv = ny == 1 ? 0 : v - j;
        double a = surface(j * nx + i) * (1 - fu) + surface(j * nx + i + 1) * fu;
        double b = surface(j1 * nx + i) * (1 - fu) + surface(j1 * nx + i + 1) * fu;
        return a * (1 - fv) + b * fv;
    }

    /** Wie hoch der Strandhafer aus dem Sand ragt, als Anteil seiner Höhe (0 … 1). */
    public double exposed(int k) { return Math.max(0, Math.min(1, 1 - burial[k] / marramHeight)); }

    /** λ des Strandhafers am Ort (nächste Zelle) für {@link Dune#cover}. */
    @Override public double lambda(double x, double z) {
        int k = nearest(x, z);
        return k < 0 ? 0 : marramLambda * veg[k] * exposed(k);
    }

    /** Übersandung am Ort, die der Strandhafer noch nicht durchwachsen hat (m), für die Halme in {@code reed}. */
    public double burialAt(double x, double z) {
        int k = nearest(x, z);
        return k < 0 ? 0 : burial[k];
    }

    /** Bedeckung mit Strandhafer am Ort (0…1). */
    public double vegAt(double x, double z) {
        int k = nearest(x, z);
        return k < 0 ? 0 : veg[k];
    }

    /** Nächste Zelle oder −1. */
    public int nearest(double x, double z) {
        double[] l = local(x, z);
        int i = (int) Math.round(l[0]), j = (int) Math.round(l[1]);
        if (i < 0 || j < 0 || i >= nx || j >= ny) return -1;
        return j * nx + i;
    }

    /** Strandhafer pflanzen: Bedeckung v (0…1), wo {@code where} > 0 ist (λ von {@code where} geteilt durch {@link #marramLambda}). */
    public void plant(Dune.Cover where, double v) {
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                double[] p = world(i, j);
                double w = where.lambda(p[0], p[1]);
                if (w > 0) veg[j * nx + i] = (float) Math.min(1, Math.max(veg[j * nx + i], v * Math.min(1, 2 * w / marramLambda)));
            }
    }

    /** Den Strand festlegen, den das Meer hält: wo {@code where} > 0 ist. */
    public void sea(Dune.Cover where) {
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                double[] p = world(i, j);
                sea[j * nx + i] = (byte) (where.lambda(p[0], p[1]) > 0 ? 1 : 0);
            }
    }

    /** Einen Zaun setzen: Oberkante je Zelle auf der Linie (Sand dort + Höhe). */
    public void fence(Fence f) {
        fences.add(f);
        double len = Math.hypot(f.x1() - f.x0(), f.z1() - f.z0());
        int steps = (int) Math.ceil(len / (cell * 0.5));
        for (int s = 0; s <= steps; s++) {
            double t = steps == 0 ? 0 : s / (double) steps;
            int k = nearest(f.x0() + (f.x1() - f.x0()) * t, f.z0() + (f.z1() - f.z0()) * t);
            if (k < 0) continue;
            fenceTop[k] = (float) (surface(k) + f.height());
            fencePor[k] = (float) f.porosity();
        }
    }

    /** Wirksame Höhe des Zauns in der Zelle (m über dem Sand; 0 wenn eingesandet oder keiner). */
    public double fenceHeight(int k) {
        float t = fenceTop[k];
        return Float.isNaN(t) ? 0 : Math.max(0, t - surface(k));
    }

    /** Sand im Feld (Platten). */
    public long total() {
        long s = 0;
        for (int v : sand) s += v;
        return s;
    }

    /** Sandbilanz: Sand jetzt − (Sand am Anfang + herein − hinaus), in Platten; 0, wenn nichts verloren geht. */
    public long balance(long start) { return total() - (start + inflow - outflow); }

    /**
     * Strandhafer wachsen lassen über dt Jahre nach der Übersandung seit dem letzten Aufruf: Übersandung durchwachsen,
     * Anregung durch Sand, Vergreisen ohne Sand, Absterben unter zu viel Sand, Schwächung durch Abtrag, Ausbreitung.
     */
    public void grow(double dt) {
        if (dt <= 0) return;
        float[] spreadFrom = veg.clone();
        double reach = marramSpread * dt / cell;
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int k = j * nx + i;
                double dz = (sand[k] - lastSand[k]) * slab, rate = dz / dt;   // m/Jahr
                lastSand[k] = sand[k];
                double v = veg[k], b = burial[k];
                // Ausbreitung: Ausläufer in Zellen, wo Sand kommt
                if (rate > 0.02 && v < 0.9) {
                    double nb = 0;
                    if (i > 0) nb = Math.max(nb, spreadFrom[k - 1]);
                    if (i < nx - 1) nb = Math.max(nb, spreadFrom[k + 1]);
                    if (j > 0) nb = Math.max(nb, spreadFrom[k - nx]);
                    if (j < ny - 1) nb = Math.max(nb, spreadFrom[k + nx]);
                    if (nb > 0.3) v = Math.max(v, Math.min(nb, v + 0.5 * nb * Math.min(1, reach)));
                }
                if (v <= 0.001) { veg[k] = 0; burial[k] = 0; continue; }
                // Übersandung folgt dem Sand (Abtrag legt frei), die Raten geglättet über ein Vierteljahr
                double tr = trend[k] + (rate - trend[k]) * Math.min(1, dt / 0.25);
                trend[k] = (float) tr;
                b = Math.max(0, b + dz);
                // nach oben durchwachsen, mit Sand kräftiger (Anregung bis 1 m/Jahr)
                double stim = tr > 0 ? Math.min(1, tr / 0.3) : 0;
                b = Math.max(0, b - marramGrowth * (0.4 + 0.6 * stim) * dt * Math.min(1, 0.4 + v));
                double before = b;
                if (b > marramHeight) v -= dt * 1.2 * v;                      // ganz begraben: stirbt in etwa einem Jahr
                else if (tr < -0.1 && b == 0) v -= dt * Math.min(1, -tr) * 0.8; // Abtrag legt die Wurzeln frei
                else v += dt * (stim > 0 ? 0.6 * stim * (1 - v) : -0.05 * v);  // Anregung durch Sand, sonst vergreist er
                v = Math.max(0, Math.min(1, v));
                if (dune.onDeposit != null && dz > 0 && before > 0.5 * marramHeight) {
                    double[] p = world(i, j);
                    dune.onDeposit.deposit(p[0], p[1], tr / 3.15576e7);
                }
                if (dune.onScour != null && tr < -0.1 && b == 0) {
                    double[] p = world(i, j);
                    dune.onScour.deposit(p[0], p[1], tr / 3.15576e7);
                }
                veg[k] = (float) v;
                burial[k] = (float) b;
            }
    }

    /** Größte Neigung zwischen Nachbarzellen (Grad), nur wo loser Sand liegt. */
    public double maxSlopeDeg() {
        double m = 0;
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int k = j * nx + i;
                if (sand[k] == 0) continue;
                if (i < nx - 1 && sand[k + 1] > 0) m = Math.max(m, Math.abs(surface(k) - surface(k + 1)));
                if (j < ny - 1 && sand[k + nx] > 0) m = Math.max(m, Math.abs(surface(k) - surface(k + nx)));
            }
        return Math.toDegrees(Math.atan(m / cell));
    }

    /** „Sandbett 160 × 80 m zu 1 m, Platten 5 cm: 41 200 m³ loser Sand, Strandhafer auf 820 m², 2 Zäune, 3,4 Jahre“ */
    public String describe() {
        double area = 0;
        for (float v : veg) area += v;
        return String.format(Locale.GERMAN, "Sandbett %.0f × %.0f m zu %.1f m, Platten %.0f cm: %,.0f m³ loser Sand, Strandhafer auf %,.0f m², %d Zäune, %.1f Jahre",
                length, width, cell, slab * 100, total() * slab * cell * cell, area * cell * cell, fences.size(), years);
    }
}
