package com.dan.impact;

import java.util.Arrays;
import java.util.Locale;

/**
 * Zufluss an der Uferlinie je Meter Breite: Mächtigkeit s(t) normal zum Hang (m) und Geschwindigkeit v(t) längs des
 * Hangs (m/s), stückweise linear über Stützstellen. Außerhalb der Stützstellen null. Das ist die Schnittstelle, über die
 * die App einen Strom (etwa {@code Landslide.Entry} je Schritt) an das Nahfeld gibt.
 */
public final class Inflow {
    private final double[] t, s, v;
    public final String name;

    public Inflow(String name, double[] t, double[] s, double[] v) {
        if (t.length < 2 || t.length != s.length || t.length != v.length) throw new IllegalArgumentException("Stützstellen");
        for (int i = 1; i < t.length; i++) if (!(t[i] > t[i - 1])) throw new IllegalArgumentException("Zeiten steigen nicht");
        this.name = name;
        this.t = t.clone();
        this.s = s.clone();
        this.v = v.clone();
    }

    /**
     * Ein Profil wie im Versuch: Volumen je Meter Breite {@code volume} (m²), Geschwindigkeit {@code speed} (m/s), Länge
     * des Körpers {@code length} (m), Gipfel {@code peak} (m) nach dem Anteil {@code peakAt} der Dauer; davor wächst die
     * Mächtigkeit wie (τ/τ_p)^k, danach fällt sie linear auf null. k folgt aus dem Volumen.
     */
    public static Inflow profile(String name, double volume, double speed, double length, double peak, double peakAt) {
        double dur = length / speed, mean = volume / length;
        // mittlere Mächtigkeit: peak (τ_p/(k+1) + (1 − τ_p)/2)
        double k = peakAt / (mean / peak - (1 - peakAt) / 2) - 1;
        if (!(k > 0.05)) throw new IllegalArgumentException("Volumen passt nicht zu Gipfel und Länge");
        int m = 24;
        double[] t = new double[m + 2], s = new double[m + 2], v = new double[m + 2];
        for (int i = 0; i <= m; i++) { double f = (double) i / m; t[i] = f * peakAt * dur; s[i] = peak * Math.pow(f, k); v[i] = speed; }
        t[m + 1] = dur; s[m + 1] = 0; v[m + 1] = speed;
        return new Inflow(name, t, s, v);
    }

    /**
     * Gilbert Inlet im Versuch FRITZ01 (Modell 1:675, auf Natur): 98,5 · 10³ t je Meter bei der Schüttdichte 1,61 t/m³
     * (61 180 m²), 110 m/s, 748 m lang, also 6,8 s; Gipfel 134 m [FRITZ01], nach 85 % der Dauer, danach schneller Abfall
     * [G: „wächst auf 134 m und fällt schnell ab“].
     */
    public static Inflow fritz01() { return profile("FRITZ01 (Modell 1:675)", 98.5e6 / 1610, 110, 748, 134, 0.85); }

    public double start() { return t[0]; }

    public double end() { return t[t.length - 1]; }

    public double thickness(double time) { return at(s, time); }

    public double speed(double time) { return at(v, time); }

    private double at(double[] y, double time) {
        if (!(time >= t[0] && time <= t[t.length - 1])) return 0;
        int k = Arrays.binarySearch(t, time);
        if (k >= 0) return y[k];
        k = -k - 2;
        double f = (time - t[k]) / (t[k + 1] - t[k]);
        return y[k] + f * (y[k + 1] - y[k]);
    }

    /** Volumen je Meter Breite ∫ s v dt (m²), mit der Trapezregel auf 2 000 Stützen. */
    public double volume() {
        int n = 2000;
        double dt = (end() - start()) / n, sum = 0;
        for (int i = 0; i <= n; i++) {
            double tt = start() + i * dt, w = i == 0 || i == n ? 0.5 : 1;
            sum += w * thickness(tt) * speed(tt);
        }
        return sum * dt;
    }

    /** Größte Mächtigkeit (m). */
    public double peak() { double m = 0; for (double x : s) m = Math.max(m, x); return m; }

    /** Mittlere Geschwindigkeit nach Rate (m/s). */
    public double meanSpeed() {
        int n = 2000;
        double dt = (end() - start()) / n, a = 0, b = 0;
        for (int i = 0; i <= n; i++) {
            double tt = start() + i * dt, r = thickness(tt) * speed(tt);
            a += r * speed(tt);
            b += r;
        }
        return b > 0 ? a / b : 0;
    }

    /** Zeit, in der 90 % des Volumens durchgehen (5 % bis 95 %), s. */
    public double duration90() {
        int n = 4000;
        double dt = (end() - start()) / n, tot = volume(), acc = 0, t5 = Double.NaN, t95 = Double.NaN;
        for (int i = 0; i < n; i++) {
            double tt = start() + (i + 0.5) * dt;
            acc += thickness(tt) * speed(tt) * dt;
            if (Double.isNaN(t5) && acc >= 0.05 * tot) t5 = tt;
            if (Double.isNaN(t95) && acc >= 0.95 * tot) t95 = tt;
        }
        return t95 - t5;
    }

    /** Stützstellen (Kopien): Zeit, Mächtigkeit, Geschwindigkeit. */
    public double[][] points() { return new double[][]{t.clone(), s.clone(), v.clone()}; }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "%s: %.0f m² je Meter in %.1f s (90 %% in %.1f s), bis %.0f m mächtig, %.0f m/s", name, volume(), end() - start(),
                duration90(), peak(), meanSpeed());
    }
}
