package com.dan.quake;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

/**
 * Das Grollen des Bebens als Schallspur (PCM, mono, −1 … 1) für {@code soundscape} [G]: Das Schütteln liegt unter
 * 20 Hz (Infraschall); hörbar gemacht wird seine Hülle (gleitender Effektivwert der Beschleunigung am Ort) auf einem
 * Rauschen im Bereich von {@link #fLow} bis {@link #fHigh} Hz, dazu Knacken (kurze Klicks, deren Rate mit dem Ruck
 * wächst) wie aus Fels und Wald. Lautstärke relativ zum stärksten Moment der Spur.
 */
public final class Rumble {
    public double fLow = 25, fHigh = 110, crackle = 0.25;
    public long seed = 7;

    /** PCM über die Dauer ab t0 (s nach dem ersten Stoß). */
    public float[] pcm(Quake.Record r, double t0, double seconds, int rate) {
        int n = (int) (seconds * rate);
        float[] out = new float[n];
        Random rnd = new Random(seed);
        double[] env = new double[n];
        double e = 0, k = 1 / (0.25 * rate), last = 0, peak = 1e-12;
        for (int i = 0; i < n; i++) {
            double[] a = r.at(t0 + i / (double) rate);
            double m = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
            e += (m * m - e) * k;
            env[i] = Math.sqrt(e);
            peak = Math.max(peak, env[i]);
        }
        double[] noise = new double[n];
        for (int i = 0; i < n; i++) noise[i] = rnd.nextGaussian();
        Quake.highPass(noise, 1.0 / rate, fLow);
        Quake.lowPass(noise, 1.0 / rate, fHigh);
        Quake.lowPass(noise, 1.0 / rate, fHigh);
        double np = 1e-12;
        for (double v : noise) np = Math.max(np, Math.abs(v));
        for (int i = 0; i < n; i++) {
            double s = noise[i] / np * env[i] / peak;
            double jerk = Math.abs(env[i] - last) * rate;
            last = env[i];
            if (rnd.nextDouble() < crackle * Math.min(1, jerk / peak) * 0.02) s += (rnd.nextBoolean() ? 1 : -1) * 0.6 * env[i] / peak;
            out[i] = (float) Math.max(-1, Math.min(1, s * 0.9));
        }
        return out;
    }

    /** Schreibt PCM als WAV (16 bit, mono). */
    public static void wav(float[] pcm, int rate, OutputStream o) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (float f : pcm) { int v = Math.round(f * 32767); b.write(v & 255); b.write((v >> 8) & 255); }
        byte[] d = b.toByteArray();
        o.write("RIFF".getBytes()); le(o, 36 + d.length, 4); o.write("WAVEfmt ".getBytes()); le(o, 16, 4); le(o, 1, 2); le(o, 1, 2);
        le(o, rate, 4); le(o, rate * 2, 4); le(o, 2, 2); le(o, 16, 2); o.write("data".getBytes()); le(o, d.length, 4); o.write(d);
    }

    static void le(OutputStream o, int v, int n) throws IOException { for (int i = 0; i < n; i++) o.write((v >> (8 * i)) & 255); }
}
