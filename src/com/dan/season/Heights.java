package com.dan.season;

import java.util.Locale;
import java.util.function.DoubleUnaryOperator;

/**
 * Seit Brocken Phase 11: das Jahr in Höhenstufen. Je Höhe ein {@link Year} mit dem Klima dieser Höhe
 * ({@link Climate#atHeight}: kälter mit der Temperaturabnahme, nasser nach oben) und demselben Startwert, also denselben
 * nassen und trockenen Tagen und denselben warmen und kalten Wochen – nur oben kälter. Dazu verspäten sich die Pflanzen
 * nach oben ({@link Year#plantDelay}). Abfragen zwischen den Stufen werden linear verbunden.
 * <p>
 * Je Bild bekommt {@link #step} das Wetter des Augenblicks: die Temperatur als Funktion der Höhe (etwa aus einer
 * Wetterregie mit Schichtung) und den Niederschlag in der Bezugshöhe, der nach oben wie im Klima zunimmt.
 */
public final class Heights {
    /** Höhen der Stufen (m, aufsteigend) und die Bezugshöhe des Klimas. */
    public final double[] h;
    public final double refHeight;
    /** Temperaturabnahme (K je km), Zunahme des Niederschlags (Anteil je km), Verspätung der Pflanzen (Tage je 100 m). */
    public final double lapse, precipPerKm, delayPer100;
    public final Year[] years;
    private Year.State[] states;

    public Heights(Climate atRef, double refHeight, double[] heights, double lapse, double precipPerKm, double delayPer100) {
        if (heights.length < 2) throw new IllegalArgumentException("season: mindestens zwei Höhen");
        this.h = heights.clone();
        this.refHeight = refHeight;
        this.lapse = lapse;
        this.precipPerKm = precipPerKm;
        this.delayPer100 = delayPer100;
        years = new Year[h.length];
        states = new Year.State[h.length];
        for (int i = 0; i < h.length; i++) {
            years[i] = new Year(atRef.atHeight(h[i] - refHeight, lapse, precipPerKm));
            years[i].plantDelay = delayPer100 * (h[i] - refHeight) / 100;
        }
    }

    /** Stufen von a bis b (m) im Abstand step. */
    public static double[] range(double a, double b, double step) {
        int n = (int) Math.round((b - a) / step) + 1;
        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = a + i * step;
        return r;
    }

    /** Startwert und Verschiebung der Temperatur für alle Stufen. */
    public void seed(long seed, double shift) { for (Year y : years) { y.seed = seed; y.shift = shift; } }

    /** Wind von außen für alle Stufen (Kronen, Reif). */
    public void wind(Wind w, double[] anchor) { for (Year y : years) { y.wind = w; y.windAnchor = anchor; } }

    /** Alle Stufen zum Datum vorrechnen. */
    public synchronized void settle(int year, int doy, double hour) {
        for (int i = 0; i < h.length; i++) states[i] = years[i].settle(year, doy, hour);
    }

    /** Niederschlag in der Höhe z (mm/h) aus dem Niederschlag in der Bezugshöhe. */
    public double precipAt(double precipRef, double z) {
        return precipRef * Math.max(0.2, 1 + precipPerKm * (z - refHeight) / 1000);
    }

    /**
     * Ein Schritt mit dem Wetter des Augenblicks: Temperatur je Höhe (°C), Niederschlag in der Bezugshöhe (mm/h),
     * Sonnenhöhe, Bedeckung, Wind. Springt das Datum, rechnet jede Stufe selbst vor.
     */
    public synchronized void step(double dtS, int year, int doy, double hour, DoubleUnaryOperator airAt, double precipRef, double sunAlt, double oktas, double windMs) {
        for (int i = 0; i < h.length; i++)
            states[i] = years[i].step(dtS, year, doy, hour, airAt.applyAsDouble(h[i]), precipAt(precipRef, h[i]), sunAlt, oktas, windMs);
    }

    /** Die Stände aller Stufen (nach settle oder step; sonst null). */
    public synchronized Year.State[] states() { return states.clone(); }

    /** Ein Wert des Stands in der Höhe z, zwischen den Stufen linear; unter und über den Stufen der Rand. */
    public synchronized double at(double z, java.util.function.ToDoubleFunction<Year.State> f) {
        if (states[0] == null) return 0;
        if (z <= h[0]) return f.applyAsDouble(states[0]);
        for (int i = 1; i < h.length; i++)
            if (z <= h[i]) {
                double t = (z - h[i - 1]) / (h[i] - h[i - 1]);
                return f.applyAsDouble(states[i - 1]) * (1 - t) + f.applyAsDouble(states[i]) * t;
            }
        return f.applyAsDouble(states[h.length - 1]);
    }

    public double snowDepth(double z) { return at(z, Year.State::snowDepth); }
    public double snowCover(double z) { return at(z, Year.State::snowCover); }
    public double treeSnow(double z) { return at(z, Year.State::treeSnow); }
    public double snowWet(double z) { return at(z, Year.State::snowWet); }
    public double frost(double z) { return at(z, Year.State::frost); }

    /** Schneehöhe aller Stufen (m) für einen Shader. */
    public synchronized float[] depths() {
        float[] r = new float[h.length];
        for (int i = 0; i < h.length; i++) r[i] = states[i] == null ? 0 : (float) states[i].snowDepth();
        return r;
    }

    /** Die unterste Höhe mit geschlossener Decke (Bedeckung über 0,5; NaN = keine). */
    public synchronized double snowLine() {
        for (int i = 0; i < h.length; i++) if (states[i] != null && states[i].snowCover() > 0.5) {
            if (i == 0) return h[0];
            double a = states[i - 1].snowCover(), b = states[i].snowCover();
            return h[i - 1] + (h[i] - h[i - 1]) * (0.5 - a) / Math.max(1e-6, b - a);
        }
        return Double.NaN;
    }

    /** „Schnee: 600 m 12 cm, 900 m 45 cm, 1 150 m 80 cm · Schneegrenze 640 m“ */
    public String describe(double... at) {
        StringBuilder b = new StringBuilder("Schnee:");
        for (int i = 0; i < at.length; i++)
            b.append(i > 0 ? "," : "").append(String.format(Locale.GERMAN, " %,.0f m %.0f cm", at[i], 100 * snowDepth(at[i])));
        double l = snowLine();
        b.append(Double.isNaN(l) ? " · keine geschlossene Decke" : String.format(Locale.GERMAN, " · Schneegrenze %,.0f m", l));
        return b.toString();
    }
}
