package com.dan.surf;

/**
 * Der Schaumteppich (seit Sylt Phase 6): wo Brecher und Walzen Schaum hinterlassen, je Zelle längs des Querschnitts
 * (x) und längs des Kamms (Reihe). Zwei Anteile [G nach Fotos und Monahan 1971: dichter Schaum zerfällt in Sekunden,
 * dünne Schlieren halten sich eine halbe bis eine Minute]: <b>dicht</b> (frisch, zerfällt mit {@link #tauDense}) und
 * das <b>Nachleuchten</b>, die Schlieren, in die er zerfällt ({@link #tauResidual}). Der Schaum treibt mit der
 * Strömung zur Küste ({@link #drift}).
 */
public final class FoamField {
    public final double x0, x1, cell;
    public final int nx, rows;
    public final float[] dense, residual;
    /** Zerfall (s), Anteil des zerfallenden dichten Schaums, der zu Schlieren wird, Drift zur Küste (m/s). */
    public double tauDense = 6, tauResidual = 40, toResidual = 0.7, drift = 0.25;

    public FoamField(double x0, double x1, double cell, int rows) {
        this.x0 = x0;
        this.x1 = x1;
        this.cell = cell;
        this.rows = rows;
        this.nx = (int) Math.ceil((x1 - x0) / cell) + 1;
        dense = new float[nx * rows];
        residual = new float[nx * rows];
    }

    /** Schaum eintragen: Reihe r, von xa bis xb, Stärke 0..1 (die stärkste gewinnt). */
    public void deposit(int r, double xa, double xb, double v) {
        if (r < 0 || r >= rows) return;
        int a = Math.max(0, (int) Math.floor((Math.min(xa, xb) - x0) / cell)), b = Math.min(nx - 1, (int) Math.ceil((Math.max(xa, xb) - x0) / cell));
        for (int i = a; i <= b; i++) {
            int q = r * nx + i;
            if (v > dense[q]) dense[q] = (float) v;
        }
    }

    /** Zeit vergeht: dichter Schaum zerfällt zu Schlieren, Schlieren vergehen, alles treibt zur Küste. */
    public void step(double dt) {
        double fd = Math.exp(-dt / tauDense), fr = Math.exp(-dt / tauResidual);
        double shift = drift * dt / cell;
        for (int r = 0; r < rows; r++) {
            int o = r * nx;
            for (int i = nx - 1; i >= 0; i--) {
                // Drift: halbe Lagrange-Verschiebung aus der Zelle seewärts
                double src = i - shift;
                int s0 = (int) Math.floor(src);
                double f = src - s0;
                double d = s0 >= 0 && s0 + 1 < nx ? dense[o + s0] * (1 - f) + dense[o + s0 + 1] * f : (s0 >= 0 && s0 < nx ? dense[o + s0] : 0);
                double rr = s0 >= 0 && s0 + 1 < nx ? residual[o + s0] * (1 - f) + residual[o + s0 + 1] * f : (s0 >= 0 && s0 < nx ? residual[o + s0] : 0);
                double lost = d * (1 - fd);
                dense[o + i] = (float) (d * fd);
                residual[o + i] = (float) Math.min(1, rr * fr + toResidual * lost);
            }
        }
    }

    /** Schaum am Ort (x, Reihe als Gleitkommazahl): dicht + Schlieren (0..1). */
    public double at(double x, double row) {
        double u = (x - x0) / cell;
        if (u < 0 || u > nx - 1) return 0;
        int i = (int) Math.floor(u), r = (int) Math.max(0, Math.min(rows - 1, Math.round(row)));
        double f = u - i;
        int i1 = Math.min(nx - 1, i + 1), q = r * nx;
        double d = dense[q + i] * (1 - f) + dense[q + i1] * f, s = residual[q + i] * (1 - f) + residual[q + i1] * f;
        return Math.min(1, d + s);
    }

    /** Dichter Anteil allein. */
    public double denseAt(double x, int r) {
        int i = (int) Math.round((x - x0) / cell);
        return i < 0 || i >= nx || r < 0 || r >= rows ? 0 : dense[r * nx + i];
    }

    /** Schlieren allein. */
    public double residualAt(double x, int r) {
        int i = (int) Math.round((x - x0) / cell);
        return i < 0 || i >= nx || r < 0 || r >= rows ? 0 : residual[r * nx + i];
    }

    /** Anteil der Fläche mit Schaum über 0,3. */
    public double coverage() {
        int n = 0;
        for (int q = 0; q < dense.length; q++) if (dense[q] + residual[q] > 0.3) n++;
        return (double) n / dense.length;
    }
}
