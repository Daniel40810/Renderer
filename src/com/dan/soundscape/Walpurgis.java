package com.dan.soundscape;

/**
 * Seit Brocken Phase 15: die Stimme der Walpurgisnacht (die Sage, kein Messwert; wie die Glocken aus dem Watt). Stärke
 * 0..1 ({@code Soundscape.walpurgis}):
 * <ul>
 * <li><b>Rufender Wind</b>: drei Rufer kreisen langsam in 120 bis 260 m um das Ohr; jeder ruft 2 bis 5 s lang ein
 * „huuu-oooh-aah“: Rauschen durch einen Kammfilter (das gibt eine Tonhöhe von 150 bis 300 Hz, die steigt und fällt) und
 * durch die Formanten der Vokale u, o, a. Am Ohr 44 + 12 · Stärke + 0,5 · Wind (m/s, höchstens 25) dB.</li>
 * <li><b>Ferne Trommeln</b> aus {@code walpurgisFromDeg} in 900 m: Folgen von vier bis acht Takten, dann eine Pause.</li>
 * <li><b>Gelächter</b> (im Mittel alle 14 / Stärke s, 40 bis 250 m weit, über dem Boden) und <b>Kreischen</b> (alle
 * 35 / Stärke s, 80 bis 400 m, hoch in der Luft).</li>
 * </ul>
 * Teil „Stimmen“.
 */
final class Walpurgis {
    static final int CALLERS = 3;
    private final double[] ang = new double[CALLERS], angV = new double[CALLERS], dist = new double[CALLERS], f0 = new double[CALLERS],
            len = new double[CALLERS], t = new double[CALLERS], wait = new double[CALLERS], peak = new double[CALLERS];
    private final float[][] comb = new float[CALLERS][4096];
    private final int[] idx = new int[CALLERS];
    private final Dsp.Biquad[][] form = new Dsp.Biquad[CALLERS][2];
    private final Dsp.Biquad[] air = new Dsp.Biquad[CALLERS];
    private final Dsp.Pink[] pink = new Dsp.Pink[CALLERS];
    private Dsp.Rng rng;
    private int builtSr;
    private double drumNext = 2, drumLeft, laughNext = 3, shriekNext = 8;
    /** Gerufene Stimmen (für Prüfungen): Rufe des Winds, Takte, Lachen, Kreischen. */
    long calls, bars, laughs, shrieks;

    private void build(int sr, long seed) {
        rng = new Dsp.Rng(seed * 7717 + 30_4);
        for (int k = 0; k < CALLERS; k++) {
            pink[k] = new Dsp.Pink(seed * 29 + k);
            form[k][0] = new Dsp.Biquad();
            form[k][1] = new Dsp.Biquad();
            air[k] = new Dsp.Biquad().lowpass(5000, 0.6, sr);
            ang[k] = Dsp.TAU * (k + rng.uniform() * 0.6) / CALLERS;
            angV[k] = (rng.uniform() < 0.5 ? -1 : 1) * (0.03 + 0.06 * rng.uniform());
            dist[k] = 120 + 140 * rng.uniform();
            wait[k] = 0.5 + 3 * rng.uniform() + 2 * k;
            t[k] = -1;
        }
        builtSr = sr;
    }

    /** Stimmen dieses Stücks einplanen (Trommeln, Lachen, Kreischen; sie laufen über die Warteschlange der Ereignisse). */
    void schedule(Soundscape s, double now, double dt, double amount, double ex, double ey, double ez, double fromDeg, int sr, long seed) {
        if (builtSr != sr) build(sr, seed);
        double a = Math.max(0.01, amount);
        if (now >= drumNext) {
            if (drumLeft <= 0) drumLeft = 4 + (int) (rng.uniform() * 5);
            double bpm = 100 + 16 * rng.uniform(), dir = Math.toRadians(fromDeg + 8 * rng.gauss()), d = 900;
            s.event(Soundscape.Kind.TROMMEL, ex + d * Math.sin(dir), ey - 120, ez - d * Math.cos(dir), Math.min(1, 0.6 + 0.4 * a), bpm);
            bars++;
            drumLeft--;
            drumNext = now + 8 * 60 / bpm + (drumLeft <= 0 ? 6 + 9 * rng.uniform() : 0);
        }
        if (now >= laughNext) {
            double dir = Dsp.TAU * rng.uniform(), d = 40 + 210 * rng.uniform();
            s.event(Soundscape.Kind.LACHEN, ex + d * Math.sin(dir), ey + 5 + 35 * rng.uniform(), ez - d * Math.cos(dir), 0.6 + 0.4 * rng.uniform());
            laughs++;
            laughNext = now + rng.exp(a / 14);
        }
        if (now >= shriekNext) {
            double dir = Dsp.TAU * rng.uniform(), d = 80 + 320 * rng.uniform();
            s.event(Soundscape.Kind.KREISCHEN, ex + d * Math.sin(dir), ey + 20 + 60 * rng.uniform(), ez - d * Math.cos(dir), 0.6 + 0.4 * rng.uniform());
            shrieks++;
            shriekNext = now + rng.exp(a / 35);
        }
    }

    /** Der rufende Wind: addiert in L, R; gibt die Energie zurück. */
    double render(float[] L, float[] R, int n, int sr, long seed, double amount, double windAtEar, double ex, double ez, double yaw) {
        if (builtSr != sr) build(sr, seed);
        double dt = n / (double) sr, a = Math.max(0, Math.min(1, amount));
        double lv = 44 + 12 * a + 0.5 * Math.min(25, Math.max(0, windAtEar));
        double e = 0;
        for (int k = 0; k < CALLERS; k++) {
            ang[k] += angV[k] * dt;
            if (t[k] < 0) {
                wait[k] -= dt * (0.4 + 0.6 * a);
                if (wait[k] > 0) continue;
                t[k] = 0;
                len[k] = 2 + 3 * rng.uniform();
                f0[k] = 150 + 110 * rng.uniform();
                peak[k] = 0.7 + 0.3 * rng.uniform();
                dist[k] = 120 + 140 * rng.uniform();
                calls++;
            }
            double u0 = t[k] / len[k];
            t[k] += dt;
            if (t[k] >= len[k]) { t[k] = -1; wait[k] = 3 + 7 * rng.uniform(); continue; }
            // Tonhöhe steigt und fällt, die Vokale gehen von u über o nach a und zurück nach o
            double f = f0[k] * (1 + 0.3 * Math.sin(Math.PI * u0) - 0.22 * u0);
            double v = Math.min(1, u0 * 3);
            double f1 = u0 < 0.6 ? 320 + (750 - 320) * Calls.smooth(u0 / 0.6) : 750 - 230 * Calls.smooth((u0 - 0.6) / 0.4);
            double f2 = u0 < 0.6 ? 800 + (1250 - 800) * Calls.smooth(u0 / 0.6) : 1250 - 350 * Calls.smooth((u0 - 0.6) / 0.4);
            form[k][0].bandpass(f1, 5, sr);
            form[k][1].bandpass(f2, 6, sr);
            double env = Math.pow(Math.sin(Math.PI * u0), 1.5) * peak[k] * (0.3 + 0.7 * v);
            double x = Math.sin(ang[k]) * dist[k], z = -Math.cos(ang[k]) * dist[k];
            float[] g = Soundscape.pan(ex + x, ez + z, ex, ez, yaw);
            float amp = (float) (Dsp.amp(lv) * env * 2.6);
            double d = sr / Math.max(60, f);
            float[] cb = comb[k];
            int m = cb.length;
            for (int i = 0; i < n; i++) {
                double rp = idx[k] - d;
                while (rp < 0) rp += m;
                int i0 = (int) rp;
                double fr = rp - i0;
                float delayed = (float) (cb[i0 % m] * (1 - fr) + cb[(i0 + 1) % m] * fr);
                float src = pink[k].next() * 0.3f + 0.93f * delayed;
                cb[idx[k]] = src;
                idx[k] = (idx[k] + 1) % m;
                float y = (form[k][0].process(src) * 1.0f + form[k][1].process(src) * 0.6f) * amp;
                y = air[k].process(y);
                L[i] += y * g[0];
                R[i] += y * g[1];
                e += 0.5 * y * y * (g[0] * g[0] + g[1] * g[1]);
            }
        }
        return e;
    }
}
