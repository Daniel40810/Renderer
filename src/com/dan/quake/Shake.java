package com.dan.quake;

/**
 * Zittern einer Kamera (oder eines Bootes, eines Baums) aus der Bodenbewegung an ihrem Ort: Verschiebung aus der
 * zweifach integrierten Beschleunigung mit einem nachgiebigen Hochpass (Zeitkonstante {@link #tau} s, damit nichts
 * wegläuft), dazu kleine Drehungen aus der Geschwindigkeit. {@link #gain} skaliert für die Darstellung (1 = wie der
 * Boden). Ergebnis je Zeit: dx (Ost), dy (oben), dz (Süd) in m, Nicken und Rollen in Grad.
 */
public final class Shake {
    public double gain = 1, tau = 2, degPerMs = 1.5;
    private final Quake.Record r;
    private final float[] dx, dy, dz, vx, vz;

    public Shake(Quake q, double x, double z) { this(q.record(x, z)); }

    public Shake(Quake.Record r) {
        this.r = r;
        int n = r.length();
        dx = new float[n]; dy = new float[n]; dz = new float[n]; vx = new float[n]; vz = new float[n];
        double[] v = new double[3], d = new double[3];
        double k = r.dt / tau;
        for (int i = 0; i < n; i++) {
            double[] a = {r.east[i], r.up[i], r.south[i]};
            for (int c = 0; c < 3; c++) {
                v[c] += (a[c] - k / r.dt * v[c] * 0.5) * r.dt;
                d[c] += (v[c] - k / r.dt * d[c] * 0.5) * r.dt;
            }
            dx[i] = (float) d[0]; dy[i] = (float) d[1]; dz[i] = (float) d[2]; vx[i] = (float) v[0]; vz[i] = (float) v[2];
        }
    }

    /** dx, dy, dz (m), Nicken, Rollen (Grad) zur Zeit t. */
    public double[] at(double t) {
        double f = (t - r.t0) / r.dt;
        int i = (int) Math.floor(f);
        if (i < 0 || i >= dx.length) return new double[5];
        return new double[]{dx[i] * gain, dy[i] * gain, dz[i] * gain, vz[i] * degPerMs * gain, vx[i] * degPerMs * gain};
    }
}
