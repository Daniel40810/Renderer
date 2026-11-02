package com.dan.impact;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein gerader Kanal mit ebenem Grund, der eine Welle über {@link Impact.Far} übernimmt: Flachwassergleichungen in 1D,
 * finite Volumen mit HLL-Flüssen (stoßerfassend), am linken Rand Wasserstand und Durchfluss aus dem Nahfeld, rechts
 * offen. Das ist der Empfänger, mit dem {@code ImpactCheck} die Übergabe prüft, bevor {@code wave} in Phase 11 die drei
 * Gitter mit HLL bekommt. Ort x zählt vom Rand des Nahfelds.
 */
public final class Channel implements Impact.Far {
    public final double depth, dx, gravity = 9.81;
    public final int cells;
    /**
     * Nur den Wasserstand vom Rand nehmen und den Durchfluss aus der rechtslaufenden Welle setzen,
     * u = 2(√(g(h + η)) − √(g h)): für einen Rand, an dem die Wasserwand noch überkippt und der gemessene Durchfluss
     * Strahlen enthält, die das Flachwasser nicht kennt.
     */
    public boolean fromEta;
    final double[] hh, q;
    double time, inflowVolume;
    final List<double[]> bc = new ArrayList<>();
    final List<double[]> gauges = new ArrayList<>();
    final List<List<double[]>> series = new ArrayList<>();

    public Channel(double length, double dx, double depth) {
        this.dx = dx;
        this.depth = depth;
        cells = (int) Math.ceil(length / dx);
        hh = new double[cells];
        q = new double[cells];
        java.util.Arrays.fill(hh, depth);
    }

    /** Pegel im Kanal bei x (vom Rand): {x, höchstes η, Zeit}. */
    public synchronized int gauge(double x) { gauges.add(new double[]{x, Double.NEGATIVE_INFINITY, Double.NaN}); series.add(new ArrayList<>()); return gauges.size() - 1; }

    public synchronized double[] gaugeMax(int g) { return gauges.get(g).clone(); }

    public synchronized List<double[]> gaugeSeries(int g) { return new ArrayList<>(series.get(g)); }

    @Override public synchronized void at(double t, int cell, double x, double z, double eta, double qx, double qz) {
        bc.add(new double[]{t, eta, qx});
        advanceTo(t);
    }

    double[] boundary(double t) {
        if (bc.isEmpty()) return new double[]{0, 0};
        if (bc.size() == 1 || t <= bc.get(0)[0]) return new double[]{bc.get(0)[1], bc.get(0)[2]};
        double[] a = bc.get(bc.size() - 2), b = bc.get(bc.size() - 1);
        if (t >= b[0]) return new double[]{b[1], b[2]};
        double f = (t - a[0]) / (b[0] - a[0]);
        return new double[]{a[1] + f * (b[1] - a[1]), a[2] + f * (b[2] - a[2])};
    }

    /** Rechnet bis t mit dem zuletzt gemeldeten Rand. */
    public synchronized void advanceTo(double t) {
        double[] fh = new double[cells + 1], fq = new double[cells + 1];
        while (time < t - 1e-12) {
            double smax = 1e-9;
            for (int i = 0; i < cells; i++) if (hh[i] > 1e-6) smax = Math.max(smax, Math.abs(q[i] / hh[i]) + Math.sqrt(gravity * hh[i]));
            double dt = Math.min(t - time, 0.45 * dx / smax);
            double[] b = boundary(time + 0.5 * dt);
            double hl = Math.max(0, depth + b[0]), ql = hl > 1e-6 ? b[1] : 0;
            if (fromEta) ql = 2 * (Math.sqrt(gravity * hl) - Math.sqrt(gravity * depth)) * hl;
            for (int f = 0; f <= cells; f++) {
                double hL = f == 0 ? hl : hh[f - 1], qL = f == 0 ? ql : q[f - 1];
                double hR = f == cells ? hh[cells - 1] : hh[f], qR = f == cells ? q[cells - 1] : q[f];
                double[] fl = hll(hL, qL, hR, qR);
                fh[f] = fl[0];
                fq[f] = fl[1];
            }
            for (int i = 0; i < cells; i++) {
                hh[i] -= dt / dx * (fh[i + 1] - fh[i]);
                q[i] -= dt / dx * (fq[i + 1] - fq[i]);
                if (hh[i] < 1e-6) { hh[i] = 0; q[i] = 0; }
            }
            inflowVolume += fh[0] * dt;
            time += dt;
            for (int g = 0; g < gauges.size(); g++) {
                double[] gg = gauges.get(g);
                int c = Math.min(cells - 1, (int) (gg[0] / dx));
                double e = hh[c] - depth;
                if (e > gg[1]) { gg[1] = e; gg[2] = time; }
                List<double[]> s = series.get(g);
                if (s.isEmpty() || time - s.get(s.size() - 1)[0] >= 0.05) s.add(new double[]{time, e});
            }
        }
    }

    double[] hll(double hL, double qL, double hR, double qR) {
        double uL = hL > 1e-6 ? qL / hL : 0, uR = hR > 1e-6 ? qR / hR : 0, cL = Math.sqrt(gravity * hL), cR = Math.sqrt(gravity * hR);
        double sL = Math.min(uL - cL, uR - cR), sR = Math.max(uL + cL, uR + cR);
        double fLh = qL, fLq = qL * uL + 0.5 * gravity * hL * hL, fRh = qR, fRq = qR * uR + 0.5 * gravity * hR * hR;
        if (sL >= 0) return new double[]{fLh, fLq};
        if (sR <= 0) return new double[]{fRh, fRq};
        return new double[]{(sR * fLh - sL * fRh + sL * sR * (hR - hL)) / (sR - sL), (sR * fLq - sL * fRq + sL * sR * (qR - qL)) / (sR - sL)};
    }

    public double time() { return time; }

    /** Volumen über dem Ruhespiegel im Kanal (m² je Meter). */
    public synchronized double volume() { double s = 0; for (double v : hh) s += v - depth; return s * dx; }

    /** Durch den linken Rand hereingekommen (m² je Meter). */
    public double inflowVolume() { return inflowVolume; }

    public synchronized double etaAt(double x) { int c = Math.max(0, Math.min(cells - 1, (int) (x / dx))); return hh[c] - depth; }
}
