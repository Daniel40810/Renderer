package com.dan.quake;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Ein Erdbeben (seit LituyaBay1958 Phase 8, Werkbuch 5.1 und 9.1): eine {@link Fault Störung} mit Herd und Magnitude,
 * daraus je Ort die Laufzeiten der P- und S-Welle, die Stärke nach {@link Bssa14} und eine synthetische Zeitreihe der
 * Bodenbeschleunigung in drei Richtungen (Ost, Süd, oben; m/s²) mit Arias-Intensität und Dauer.
 * <p>
 * <b>Zeitreihe</b> [G, nach dem Verfahren der Teilflächen]: Der Bruch läuft vom Herd über die Störung; jede Teilfläche
 * (alle {@link Quality#spacing} m) strahlt, wenn der Bruch sie erreicht, einen Schwall gefilterten Rauschens ab, der als
 * P-Welle (schwach, vor allem senkrecht) und als S-Welle (stark, waagerecht) zum Ort läuft, abgeschwächt mit 1/R. Die
 * Summe wird so skaliert, dass die größte waagerechte Beschleunigung dem Median von BSSA14 entspricht (Rjb zum
 * gebrochenen Teil, Vs30 des Orts). Damit dauert das Schütteln so lange, wie der Bruch an diesem Ort vorbeiläuft, und
 * am Ende einer langen Störung kommt es spät und lang. Gleicher {@link #seed}, gleiche Zeitreihe.
 * <p>
 * Zeit t in Sekunden nach dem ersten Stoß (Bruchbeginn am Herd); Lage x Ost, z Süd in Metern, wie in den Szenen.
 */
public final class Quake {
    /** Qualität: Abstand der Teilflächen (m) und Zeitschritt der Zeitreihe (s). */
    public enum Quality {
        LOW(5000, 0.02), MEDIUM(3000, 0.01), HIGH(2000, 0.01), ULTRA(1000, 0.005);
        public final double spacing, dt;
        Quality(double spacing, double dt) { this.spacing = spacing; this.dt = dt; }
    }

    public final Fault fault;
    public double magnitude = 7.8;
    /** Geschwindigkeiten der P- und S-Welle in der Kruste (m/s) [G, Werkbuch 9.1]. */
    public double vp = 6000, vs = 3500;
    /** Mittlere Scherwellengeschwindigkeit der oberen 30 m (m/s): 760 Fels (Bezug von BSSA14). */
    public double vs30 = 760;
    /** Tiefe, aus der die Teilflächen strahlen (m) [G]: Mitte der Bruchfläche. */
    public double sourceDepth = 8000;
    /** Verhältnis senkrecht zu waagerecht der S-Welle und Anteil der P-Welle [G]. */
    public double verticalRatio = 0.6, pShare = 0.25;
    /** Durchlass des Rauschens (Hz) [G]. */
    public double fLow = 0.15, fHigh = 12;
    public long seed = 1958;
    public Quality quality = Quality.HIGH;
    /** Streuung: ε in Standardabweichungen von BSSA14 (0 = Median). */
    public double epsilon = 0;

    public Quake(Fault fault, double magnitude) { this.fault = fault; this.magnitude = magnitude; }

    // ------------------------------------------------------------------ Presets

    /**
     * Das Beben vom 9. Juli 1958 an der Fairweather-Störung (Mw 7,8, ISC-GEM; Herd 58,23° N, 136,71° W, 10 km, ComCat
     * iscgem884702). Spur aus Qfaults 2020 (USGS, Abschnitte „historic“ der Fairweather-Störung, alle 10 km gemittelt),
     * in Metern um den Ursprung von LituyaBay1958 (UTM 8 N, E 352 000, N 6 503 000; x Ost, z Süd). Der Bruch läuft zu drei
     * Vierteln nach Nordwesten [G], seine Länge nach WELLS94 (167 km). Die App lädt die volle Spur aus
     * {@code daten/ereignis/stoerung.tsv}.
     */
    public static Quake fairweather1958() {
        double[] xz = {65436, 140209, 61663, 123679, 57228, 102899, 52682, 82179, 47339, 63960, 37812, 44434, 26972, 26348, 15121, 11208,
                3099, -3848, -7850, -20540, -17750, -37497, -26621, -57759, -36309, -75846, -46564, -92714, -56208, -109316, -65932, -127046,
                -76425, -141952, -93133, -155839};
        Fault f = new Fault(xz);
        f.hx = 399461 - 352000;
        f.hz = -(6455594 - 6503000);
        f.hdepth = 10_000;
        f.forward = 0.75;
        Quake q = new Quake(f, 7.8);
        q.seed = 1958;
        return q;
    }

    /** Ein Beben der Magnitude m an einer geraden Störung längs x durch den Ursprung; der Ort (0, abstandKm km) liegt quer dazu. */
    public static Quake magnitude(double m, double abstandKm) {
        double l = Math.max(20_000, Fault.wellsLength(m) * 1.5);
        Fault f = new Fault(new double[]{-l, 0, l, 0});
        f.hx = 0; f.hz = 0;
        Quake q = new Quake(f, m);
        q.seed = Math.round(m * 1000 + abstandKm);
        return q;
    }

    // ------------------------------------------------------------------ Laufzeiten und Stärke

    /** Ankunft der P-Welle vom Herd (s). */
    public double tP(double x, double z) { return fault.hypoDistance(x, z) / vp; }

    /** Ankunft der S-Welle vom Herd (s). */
    public double tS(double x, double z) { return fault.hypoDistance(x, z) / vs; }

    /** Joyner-Boore-Abstand zum gebrochenen Teil (km). */
    public double rjbKm(double x, double z) { return fault.rjb(x, z, magnitude) / 1000; }

    /** Median (mit ε) von BSSA14 am Ort: PGA, SA in g, PGV in cm/s. */
    public double intensity(Bssa14.Imt imt, double x, double z) {
        double r = rjbKm(x, z);
        return Math.exp(Bssa14.ln(imt, magnitude, r, vs30, fault.mechanism) + epsilon * Bssa14.sigma(imt, magnitude, r, vs30));
    }

    /** Größte waagerechte Beschleunigung (g). */
    public double pga(double x, double z) { return intensity(Bssa14.Imt.PGA, x, z); }

    // ------------------------------------------------------------------ Zeitreihe

    /** Eine Zeitreihe an einem Ort: ab t0 alle dt Sekunden Ost, Süd, oben (m/s²). */
    public static final class Record {
        public final double x, z, t0, dt;
        public final float[] east, south, up;
        public final double pgaTarget;
        private double arias = -1;

        Record(double x, double z, double t0, double dt, int n, double pgaTarget) {
            this.x = x; this.z = z; this.t0 = t0; this.dt = dt; this.pgaTarget = pgaTarget;
            east = new float[n]; south = new float[n]; up = new float[n];
        }

        /** Eine Zeitreihe von außen (Messung, Prüfung): Ost, Süd, oben in m/s², gleich lang. */
        public static Record of(double x, double z, double t0, double dt, float[] east, float[] south, float[] up) {
            Record r = new Record(x, z, t0, dt, east.length, Double.NaN);
            System.arraycopy(east, 0, r.east, 0, east.length);
            System.arraycopy(south, 0, r.south, 0, east.length);
            System.arraycopy(up, 0, r.up, 0, east.length);
            return r;
        }

        public int length() { return east.length; }

        public double tEnd() { return t0 + dt * (east.length - 1); }

        /** Beschleunigung zur Zeit t (linear zwischen den Schritten; 0 außerhalb). */
        public double[] at(double t) {
            double f = (t - t0) / dt;
            int i = (int) Math.floor(f);
            if (i < 0 || i + 1 >= east.length) return new double[3];
            double u = f - i;
            return new double[]{east[i] + (east[i + 1] - east[i]) * u, south[i] + (south[i + 1] - south[i]) * u, up[i] + (up[i + 1] - up[i]) * u};
        }

        /** Größte waagerechte Beschleunigung (m/s², größere der beiden Komponenten). */
        public double peakHorizontal() {
            double p = 0;
            for (int i = 0; i < east.length; i++) p = Math.max(p, Math.max(Math.abs(east[i]), Math.abs(south[i])));
            return p;
        }

        /** Arias-Intensität (m/s), Mittel der beiden waagerechten Komponenten: I_A = π/(2g) ∫ a² dt. */
        public double arias() {
            if (arias < 0) {
                double s = 0;
                for (int i = 0; i < east.length; i++) s += 0.5 * (east[i] * east[i] + south[i] * south[i]);
                arias = Math.PI / (2 * 9.81) * s * dt;
            }
            return arias;
        }

        /** Signifikante Dauer D5–95 (s): Zeit zwischen 5 % und 95 % der Arias-Intensität; dazu die beiden Zeiten. */
        public double[] significantDuration() {
            double total = 0;
            for (int i = 0; i < east.length; i++) total += east[i] * east[i] + south[i] * south[i];
            double s = 0, a = Double.NaN, b = Double.NaN;
            for (int i = 0; i < east.length; i++) {
                s += east[i] * east[i] + south[i] * south[i];
                if (Double.isNaN(a) && s >= 0.05 * total) a = t0 + i * dt;
                if (Double.isNaN(b) && s >= 0.95 * total) b = t0 + i * dt;
            }
            return new double[]{b - a, a, b};
        }

        /** Beschleunigung in eine waagerechte Richtung (Azimut in Grad von Nord im Uhrzeigersinn). */
        public double along(double t, double azimuthDeg) {
            double[] a = at(t);
            double r = Math.toRadians(azimuthDeg);
            return a[0] * Math.sin(r) - a[1] * Math.cos(r);   // Nord = −Süd
        }
    }

    private final Map<Long, Record> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Record> e) { return size() > 64; }
    };

    /** Die Zeitreihe am Ort (auf 100 m gerastert und gemerkt). */
    public synchronized Record record(double x, double z) {
        long key = (Math.round(x / 100) << 32) ^ (Math.round(z / 100) & 0xffffffffL) ^ ((long) quality.ordinal() << 60) ^ seed * 31;
        Record r = cache.get(key);
        if (r == null) { r = synthesize(Math.round(x / 100) * 100.0, Math.round(z / 100) * 100.0); cache.put(key, r); }
        return r;
    }

    /** Vergisst gemerkte Zeitreihen (nach Änderung der offenen Werte). */
    public synchronized void clear() { cache.clear(); }

    /** Beschleunigung am Ort zur Zeit t: Ost, Süd, oben (m/s²). */
    public double[] accelerationAt(double x, double z, double t) { return record(x, z).at(t); }

    /** Arias-Intensität am Ort (m/s). */
    public double arias(double x, double z) { return record(x, z).arias(); }

    Record synthesize(double x, double z) {
        double dt = quality.dt;
        double[] rup = fault.rupture(magnitude);
        double sh = fault.hypoS();
        int nSub = Math.max(1, (int) Math.ceil((rup[1] - rup[0]) / quality.spacing));
        double ds = (rup[1] - rup[0]) / nSub;
        double[] tr = new double[nSub], r = new double[nSub];
        double tMin = Double.MAX_VALUE, tMax = 0;
        for (int k = 0; k < nSub; k++) {
            double s = rup[0] + (k + 0.5) * ds;
            double[] p = fault.point(s);
            tr[k] = Math.abs(s - sh) / fault.vr;
            r[k] = Math.sqrt((p[0] - x) * (p[0] - x) + (p[1] - z) * (p[1] - z) + sourceDepth * sourceDepth);
            tMin = Math.min(tMin, tr[k] + r[k] / vp);
            tMax = Math.max(tMax, tr[k] + r[k] / vs + burst(ds, r[k]) * 4);
        }
        tMin = Math.min(tMin, tP(x, z));
        double t0 = Math.max(0, tMin - 2);
        int n = (int) Math.ceil((tMax + 20 - t0) / dt) + 1;
        double pga = pga(x, z) * 9.81;
        Record rec = new Record(x, z, t0, dt, n, pga);
        double[][] acc = new double[3][n];
        long site = Double.doubleToLongBits(x) * 31 + Double.doubleToLongBits(z);
        for (int k = 0; k < nSub; k++) {
            Random rnd = new Random(seed ^ site ^ (k * 0x9E3779B97F4A7C15L));
            double w = 1 / Math.max(r[k], 5000.0);
            double td = burst(ds, r[k]);
            add(acc, rnd, t0, dt, tr[k] + r[k] / vs, td, w, new double[]{1, 1, verticalRatio});
            add(acc, rnd, t0, dt, tr[k] + r[k] / vp, td * 0.6, w * pShare, new double[]{0.4, 0.4, 1});
        }
        for (int c = 0; c < 3; c++) {
            highPass(acc[c], dt, fLow);
            lowPass(acc[c], dt, Math.min(fHigh, 0.45 / dt));
        }
        double peak = 0;
        for (int i = 0; i < n; i++) peak = Math.max(peak, Math.max(Math.abs(acc[0][i]), Math.abs(acc[1][i])));
        double f = peak > 0 ? pga / peak : 0;
        for (int i = 0; i < n; i++) {
            rec.east[i] = (float) (acc[0][i] * f);
            rec.south[i] = (float) (acc[1][i] * f);
            rec.up[i] = (float) (acc[2][i] * f);
        }
        return rec;
    }

    /** Dauer eines Schwalls (s): Durchgang des Bruchs über die Teilfläche und Streuung auf dem Weg (0,05 s/km) [G]. */
    double burst(double ds, double r) { return ds / fault.vr + 0.05 * r / 1000; }

    /** Ein Schwall ab ta mit Hülle τ/τm · e^(1 − τ/τm), τm = 0,25 · Dauer, je Komponente eigenes Rauschen. */
    static void add(double[][] acc, Random rnd, double t0, double dt, double ta, double td, double w, double[] comp) {
        double tm = 0.25 * td;
        int i0 = (int) Math.floor((ta - t0) / dt), i1 = (int) Math.ceil((ta + 6 * td - t0) / dt);
        for (int i = Math.max(0, i0); i < Math.min(acc[0].length, i1); i++) {
            double tau = t0 + i * dt - ta;
            if (tau < 0) continue;
            double e = (tau / tm) * Math.exp(1 - tau / tm) * w;
            for (int c = 0; c < 3; c++) acc[c][i] += e * comp[c] * rnd.nextGaussian();
        }
    }

    /** Hochpass 2. Ordnung (Butterworth, bilinear). */
    static void highPass(double[] a, double dt, double fc) { biquad(a, dt, fc, true); biquad(a, dt, fc, true); }

    static void lowPass(double[] a, double dt, double fc) { biquad(a, dt, fc, false); }

    static void biquad(double[] a, double dt, double fc, boolean high) {
        double w = Math.tan(Math.PI * fc * dt), q = Math.sqrt(0.5), n = 1 / (1 + w / q + w * w);
        double b0, b1, b2;
        if (high) { b0 = n; b1 = -2 * n; b2 = n; } else { b0 = w * w * n; b1 = 2 * b0; b2 = b0; }
        double a1 = 2 * (w * w - 1) * n, a2 = (1 - w / q + w * w) * n;
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        for (int i = 0; i < a.length; i++) {
            double x0 = a[i], y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            a[i] = y0;
            x2 = x1; x1 = x0; y2 = y1; y1 = y0;
        }
    }

    // ------------------------------------------------------------------ Ereignisse

    /** Ereignis nach außen: ein Hang hat versagt (Newmark-Verschiebung über der Schwelle). */
    @FunctionalInterface
    public interface Failure { void failed(Slope slope, double t, double displacement); }

    /** Für das Platzhalterbild und die App: Stärke des Schüttelns am Ort zur Zeit t (0 … 1, |a| / g). */
    public double strength(double x, double z, double t) {
        double[] a = accelerationAt(x, z, t);
        return Math.min(1, Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]) / 9.81);
    }
}
