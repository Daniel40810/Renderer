package com.dan.exposure;

/**
 * Augenanpassung (seit LituyaBay1958 W6 Runde 6): die Belichtung folgt langsam der Helligkeit im Bild, wie Auge oder
 * Kamera. Eingang ist ein Histogramm der Leuchtdichte des HDR-Bilds in log2 (aus {@code gl/LuminanceGl} oder von der App),
 * Ausgang eine Korrektur in Blenden (EV), die die App auf ihre feste Belichtung multipliziert: Faktor 2^{@link #ev()}.
 * <p>
 * <b>Messung.</b> Mittel der log2-Leuchtdichte über die Bildpunkte zwischen den Perzentilen {@link #lowPercent} und
 * {@link #highPercent} (die dunkelsten Schatten und die hellsten Lichter, etwa Himmel zwischen Kronen, zählen nicht).
 * <br><b>Ziel.</b> ev* = Stärke · log2(Bezug / Mittel), begrenzt auf −{@link #maxDown} … +{@link #maxUp}. Mit
 * {@link #strength} &lt; 1 passt sich das Bild nur teilweise an: ein dunkler Wald bleibt dunkler als eine Lichtung, wie es
 * auch das Auge sieht (vollständige Anpassung gäbe jedem Bild dieselbe Helligkeit).
 * <br><b>Zeit.</b> Exponentiell, ins Dunkle langsamer als ins Helle (Dunkeladaptation der Zapfen in Sekunden, Pupille
 * und Helladaptation in Bruchteilen davon) [G]: {@link #tauDark} 3 s, {@link #tauBright} 0,8 s.
 * <br><b>Wirkung.</b> {@link #weight} 0..1 von der App, etwa 1 am Boden und 0 im Flug (dort bleibt die Belichtung fest).
 * <br>Bei einem Schnitt oder Sprung {@link #snap()}: die nächste Messung gilt sofort (Standbilder, Proben).
 */
public final class EyeAdaptation {
    /** Bereich des Histogramms in log2 der Leuchtdichte und Zahl der Fächer (wie gl/LuminanceGl). */
    public double logMin = -14, logMax = 4;
    public int bins = 64;
    /** Bezug (Mittel, bei dem nichts korrigiert wird), Stärke, Grenzen (Blenden), Zeitkonstanten (s) [G]. */
    public double key = 0.12, strength = 0.6, maxUp = 2, maxDown = 0.5, tauDark = 3, tauBright = 0.8;
    public double lowPercent = 40, highPercent = 92;
    /** Wirkung 0..1, von der App je Bild. */
    public double weight = 1;

    private double ev, target, mean = Double.NaN;
    private boolean snap = true;

    /** Korrektur in Blenden, schon mit {@link #weight}. */
    public double ev() { return ev * weight; }
    /** Faktor auf die Belichtung. */
    public double factor() { return Math.pow(2, ev()); }
    /** Ziel ohne Zeit und ohne {@link #weight}. */
    public double target() { return target; }
    /** Letztes gemessenes Mittel der Leuchtdichte (linear), NaN vor der ersten Messung. */
    public double mean() { return mean; }
    /** Nächste Messung sofort übernehmen. */
    public void snap() { snap = true; }

    /**
     * Mittel (linear) der Leuchtdichte aus einem Histogramm (counts je Fach, Fach i deckt log2 von logMin + i·Δ bis
     * logMin + (i+1)·Δ) zwischen den Perzentilen; NaN ohne Zählung.
     */
    public double meanFromHistogram(long[] counts) {
        long total = 0;
        for (long c : counts) total += c;
        if (total <= 0) return Double.NaN;
        double lo = total * lowPercent / 100.0, hi = total * highPercent / 100.0, d = (logMax - logMin) / counts.length;
        double acc = 0, w = 0, run = 0;
        for (int i = 0; i < counts.length; i++) {
            double a = run, b = run + counts[i];
            run = b;
            double take = Math.max(0, Math.min(b, hi) - Math.max(a, lo));
            if (take <= 0) continue;
            acc += take * (logMin + (i + 0.5) * d);
            w += take;
        }
        return w > 0 ? Math.pow(2, acc / w) : Double.NaN;
    }

    /** Ziel in Blenden für ein Mittel (linear). */
    public double targetFor(double meanLum) {
        if (!(meanLum > 0)) return 0;
        double t = strength * (Math.log(key / meanLum) / Math.log(2));
        return Math.max(-maxDown, Math.min(maxUp, t));
    }

    /** Eine Messung (Histogramm, null = keine neue) und dt Sekunden seit dem letzten Aufruf. */
    public void update(long[] counts, double dt) {
        if (counts != null) {
            double m = meanFromHistogram(counts);
            if (!Double.isNaN(m)) {
                mean = m;
                target = targetFor(m);
                if (snap) { ev = target; snap = false; return; }
            }
        }
        step(dt);
    }

    /** Nur die Zeit: ev läuft auf das Ziel zu. */
    public void step(double dt) {
        if (!(dt > 0)) return;
        double tau = target > ev ? tauDark : tauBright;   // heller stellen heißt: das Bild war dunkel (Dunkeladaptation)
        ev = target + (ev - target) * Math.exp(-dt / Math.max(1e-3, tau));
    }

    @Override public String toString() {
        return String.format(java.util.Locale.GERMAN, "Augenanpassung %+.2f Blenden (Ziel %+.2f, Mittel %.3g, Wirkung %.0f %%)", ev(), target, mean, weight * 100);
    }
}
