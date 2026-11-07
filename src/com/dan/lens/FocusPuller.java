package com.dan.lens;

/**
 * Der Schärfezieher: Er führt den Fokus einem Ziel nach wie ein Assistent am Schärfering. Gerechnet wird in Dioptrien
 * (1 / Abstand), weil die Skala eines Objektivs dort fast gleichmäßig ist und ein Sprung von 2 auf 4 m so weit ist wie
 * von 20 auf ∞ ... beinahe: nah ändert sich die Schärfe schnell, fern langsam, wie am echten Ring. Die Bewegung ist eine
 * kritisch gedämpfte Feder (kein Überschwingen) mit der Zeit {@link #timeS} bis rund 98 % und höchstens
 * {@link #maxDioptersPerS} Dioptrien je Sekunde; sie ist exakt gelöst (x(t) = x_Ziel + (c1 + c2·t)·e^(−ωt)) und damit
 * für jede Schrittweite stabil.
 */
public final class FocusPuller {
    /** Zeit bis rund 98 % (s). 0 = sofort. */
    public volatile double timeS = 0.9;
    /** Höchste Geschwindigkeit am Ring (Dioptrien je s) [G]. */
    public volatile double maxDioptersPerS = 4;
    /** Nächster und fernster Fokus (m). */
    public volatile double nearM = 0.45, farM = 1e5;

    private double x = 0.1, v;   // Dioptrien, Dioptrien je s

    /** Fokus jetzt (m). */
    public double focusM() { return 1 / Math.max(1 / farM, x); }

    public void snap(double targetM) { x = clampD(targetM); v = 0; }

    /** Um dt Sekunden dem Ziel (m) nach; gibt den Fokus (m) zurück. */
    public double step(double dt, double targetM) {
        double xt = clampD(targetM);
        if (timeS <= 0 || dt <= 0) { if (timeS <= 0) { x = xt; v = 0; } return focusM(); }
        double w = 5.8 / timeS;
        double c1 = x - xt, c2 = v + w * c1;
        double e = Math.exp(-w * dt);
        double nx = xt + (c1 + c2 * dt) * e;
        double nv = (c2 - w * (c1 + c2 * dt)) * e;
        double lim = maxDioptersPerS;
        if (lim > 0 && Math.abs(nv) > lim) {
            nv = Math.signum(nv) * lim;
            nx = x + nv * dt;
            if ((x - xt) * (nx - xt) < 0) { nx = xt; nv = 0; }
        }
        x = nx;
        v = nv;
        return focusM();
    }

    private double clampD(double m) {
        double d = 1 / Math.max(nearM, Math.min(farM, m));
        return Math.max(1 / farM, Math.min(1 / nearM, d));
    }
}
