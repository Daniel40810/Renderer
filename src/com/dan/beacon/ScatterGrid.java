package com.dan.beacon;

/**
 * Die Streudichte um das Feuer als Kugelgitter: Azimut (gleichmäßig), Höhenwinkel (quadratisch, fein um die
 * Waagerechte, wo der Strahl liegt) und Abstand (logarithmisch von 0,5 m bis 12 km). So hat das Gitter nah am Feuer,
 * wo Gischt und Strahl am hellsten sind, Zellen von Dezimetern, in Kilometern Entfernung Zellen von Hunderten Metern.
 * <p>
 * {@link #update(Scatter, double, double, double, double)} füllt das Gitter in Portionen mit begrenzter Rechenzeit
 * nach und tauscht es erst, wenn es voll ist; das Bild sieht immer ein vollständiges Gitter. {@link #sample} liest
 * trilinear, genau wie der Shader ({@code sampler3D} mit LINEAR, Kanten wiederholt im Azimut, sonst geklemmt).
 */
public final class ScatterGrid {
    public final int nAz, nEl, nR;
    public final double maxElDeg, r0, r1;
    private float[] front, back;
    private int cursor, version;
    private double cx, cy, cz;
    private double fx, fy, fz;

    public ScatterGrid(int nAz, int nEl, int nR) { this(nAz, nEl, nR, 40, 0.5, 12_000); }

    public ScatterGrid(int nAz, int nEl, int nR, double maxElDeg, double r0, double r1) {
        this.nAz = nAz;
        this.nEl = nEl;
        this.nR = nR;
        this.maxElDeg = maxElDeg;
        this.r0 = r0;
        this.r1 = r1;
        front = new float[nAz * nEl * nR];
        back = new float[nAz * nEl * nR];
    }

    /** Zählt jede fertige Füllung hoch (zum Hochladen auf die Grafikkarte). */
    public int version() { return version; }

    /** Das fertige Gitter (nicht verändern): Index (r · nEl + e) · nAz + a. */
    public float[] data() { return front; }

    /** Mitte des fertigen Gitters (das Feuer). */
    public double[] center() { return new double[]{fx, fy, fz}; }

    /** Höhenwinkel in Grad zur Texturkoordinate v (0..1): quadratisch, v = ½ + ½ · sign · √(|el| / max). */
    public double elOf(double v) { double s = v * 2 - 1; return Math.signum(s) * s * s * maxElDeg; }

    public double vOf(double elDeg) {
        double e = Math.max(-1, Math.min(1, elDeg / maxElDeg));
        return 0.5 + 0.5 * Math.signum(e) * Math.sqrt(Math.abs(e));
    }

    public double rOf(double w) { return r0 * Math.pow(r1 / r0, w); }

    public double wOf(double r) { return Math.log(Math.max(r0, Math.min(r1, r)) / r0) / Math.log(r1 / r0); }

    /** Ort einer Zellmitte {x, y, z} um das Feuer an (lx, ly, lz). */
    double[] cell(int a, int e, int ri, double lx, double ly, double lz) {
        double az = Math.toRadians((a + 0.5) / nAz * 360), el = Math.toRadians(elOf((e + 0.5) / nEl)), r = rOf((ri + 0.5) / nR);
        double h = Math.cos(el) * r;
        return new double[]{lx + Math.sin(az) * h, ly + Math.sin(el) * r, lz - Math.cos(az) * h};
    }

    /**
     * Füllt höchstens maxMillis lang weiter (≤ 0: alles auf einmal). Gibt true zurück, wenn dabei eine Füllung fertig
     * wurde. Das Feuer steht an (lx, ly, lz); wechselt es den Ort, beginnt die Füllung neu.
     */
    public synchronized boolean update(Scatter s, double lx, double ly, double lz, double maxMillis) {
        if (cursor == 0 || lx != cx || ly != cy || lz != cz) { cursor = 0; cx = lx; cy = ly; cz = lz; }
        long end = maxMillis <= 0 ? Long.MAX_VALUE : System.nanoTime() + (long) (maxMillis * 1e6);
        int n = back.length;
        while (cursor < n) {
            int a = cursor % nAz, e = (cursor / nAz) % nEl, ri = cursor / (nAz * nEl);
            double[] p = cell(a, e, ri, lx, ly, lz);
            back[cursor] = (float) Math.max(0, s.sigma(p[0], p[1], p[2]));
            cursor++;
            if ((cursor & 63) == 0 && System.nanoTime() > end) return false;
        }
        float[] t = front;
        front = back;
        back = t;
        fx = cx;
        fy = cy;
        fz = cz;
        cursor = 0;
        version++;
        return true;
    }

    /** Alles neu und sofort (für Standbilder und Prüfungen). */
    public void fill(Scatter s, double lx, double ly, double lz) {
        synchronized (this) { cursor = 0; }
        update(s, lx, ly, lz, 0);
    }

    /** Streukoeffizient 1/m trilinear im fertigen Gitter für die Richtung w (Einheitsvektor vom Feuer) und Abstand r. */
    public double sample(double wx, double wy, double wz, double r) {
        double az = Math.toDegrees(Math.atan2(wx, -wz));
        if (az < 0) az += 360;
        double el = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, wy))));
        double u = az / 360 * nAz - 0.5, v = vOf(el) * nEl - 0.5, w = wOf(r) * nR - 0.5;
        return trilinear(front, u, v, w);
    }

    double trilinear(float[] g, double u, double v, double w) {
        int a0 = (int) Math.floor(u), e0 = (int) Math.floor(v), r0i = (int) Math.floor(w);
        double fu = u - a0, fv = v - e0, fw = w - r0i;
        double s = 0;
        for (int k = 0; k < 8; k++) {
            int a = a0 + (k & 1), e = e0 + ((k >> 1) & 1), ri = r0i + ((k >> 2) & 1);
            a = Math.floorMod(a, nAz);
            e = Math.max(0, Math.min(nEl - 1, e));
            ri = Math.max(0, Math.min(nR - 1, ri));
            double wt = ((k & 1) != 0 ? fu : 1 - fu) * (((k >> 1) & 1) != 0 ? fv : 1 - fv) * (((k >> 2) & 1) != 0 ? fw : 1 - fw);
            s += wt * g[(ri * nEl + e) * nAz + a];
        }
        return s;
    }
}
