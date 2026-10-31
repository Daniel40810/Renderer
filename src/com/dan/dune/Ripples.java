package com.dan.dune;

import java.util.Locale;

/**
 * Windrippel (seit Sylt Phase 8) auf den Zellen eines {@link FluxField}: quer zum Wind, flacher Luv- und steiler
 * Leehang, sie wachsen, wo Sand fliegt, und wandern mit dem Kriechen der Körner.
 * <ul>
 * <li><b>Wellenlänge:</b> λ = λ₀·(1 + a·(u* : u*t − 1)), an der Schwelle {@link Dune#rippleLambda0}, mit dem Wind länger
 * (Andreotti u. a. 2006: linear in u*); sie passt sich mit der Zeit der Bildung an.</li>
 * <li><b>Höhe:</b> wächst mit {@link Dune#rippleFormTime} gegen H = λ/{@link Dune#rippleIndex}; über
 * u* : u*t = {@link Dune#rippleFlatBed} ebnet der Wind sie ein (ebene Sohle). Ohne Flug bleiben sie stehen (alte Rippel), im
 * Nassen und im Windschatten verwischen sie langsam.</li>
 * <li><b>Wandern:</b> c = r·q / (ρ_b·H) mit dem Anteil r ({@link Dune#rippleReptation}) des Flusses, der als Kriechen über
 * die Rippel geht.</li>
 * </ul>
 * Gibt je Zelle λ, H und c für den Shader; das Profil (Luv lang, Lee kurz) zeichnet {@code gl/dune.frag}. Die Phase der
 * Kämme rechnet der Shader mit einer Wellenlänge für das ganze Feld ({@link #drawLambda}, das Mittel, wo Rippel liegen):
 * mit der Wellenlänge je Zelle liefen die Kämme benachbarter Zellen über 100 m Weg um viele Wellenlängen auseinander.
 */
public final class Ripples {
    public final FluxField field;
    /** Je Zelle wie im Feld: Wellenlänge (m), Höhe (m), Geschwindigkeit (m/s). */
    public final float[] lambda, height, speed;
    /** Weg, den die Rippel an der Stelle des größten Flusses seit dem Anfang gewandert sind (m), und die Zeit (s). */
    public double travelled;
    public double t;

    public Ripples(FluxField f) {
        field = f;
        int n = f.q.length;
        lambda = new float[n];
        height = new float[n];
        speed = new float[n];
        java.util.Arrays.fill(lambda, (float) f.dune.rippleLambda0);
    }

    /** Wellenlänge, die zu u* und Schwelle gehört (m). */
    public static double lambdaFor(Dune d, double ustar, double threshold) {
        double r = threshold > 0 ? Math.max(1, ustar / threshold) : 1;
        return d.rippleLambda0 * (1 + d.rippleGrowth * (r - 1));
    }

    /** Fertig ausgebildete Rippel wie nach langer Zeit bei diesem Wind (für Standbilder und Prüfung). */
    public Ripples settle() {
        for (int k = 0; k < 40; k++) step(field.dune.rippleFormTime / 8);
        return this;
    }

    /** Ein Schritt um dt Sekunden. */
    public void step(double dt) {
        t += dt;
        Dune d = field.dune;
        FluxField f = field;
        double grow = 1 - Math.exp(-dt / d.rippleFormTime), cMax = 0;
        for (int k = 0; k < lambda.length; k++) {
            double us = f.ustar[k], ut = f.thr[k], qq = f.q[k];
            boolean wet = f.moist[k] > 0.04, shadow = f.h[k] < f.env[k] - 0.02;
            if (qq > 1e-6 && us > ut) {
                double r = us / ut, target = lambdaFor(d, us, ut);
                double heq = target / d.rippleIndex;
                // über rippleFlatBed eingeebnet, weich
                double flat = Math.max(0, Math.min(1, (r - d.rippleFlatBed + 0.5) / 1.0));
                heq *= 1 - flat;
                lambda[k] += (float) ((target - lambda[k]) * grow);
                height[k] += (float) ((heq - height[k]) * grow);
                // Kriechen über die Rippel; bei kleiner Höhe zählt die halbe Gleichgewichtshöhe (sonst rasen junge Rippel)
                double c = d.rippleReptation * qq / (d.bulkDensity * Math.max(height[k], 0.5 * lambda[k] / d.rippleIndex));
                speed[k] = (float) Math.min(c, 0.002);
                cMax = Math.max(cMax, speed[k]);
            } else {
                speed[k] = 0;
                if (wet || shadow) height[k] *= (float) Math.exp(-dt / (d.rippleFormTime * 6));
            }
        }
        travelled += cMax * dt;
    }

    /** Wellenlänge zum Zeichnen: Mittel über die Zellen mit Rippeln, nach Höhe gewichtet (m). */
    public double drawLambda() {
        double s = 0, w = 0;
        for (int k = 0; k < lambda.length; k++) { s += lambda[k] * height[k]; w += height[k]; }
        return w > 0 ? s / w : field.dune.rippleLambda0;
    }

    /** Wellenlänge, Höhe und Geschwindigkeit am Ort, {λ, H, c}, aus der nächsten Zelle. */
    public double[] at(double x, double z) {
        double[] l = field.local(x, z);
        int i = (int) Math.round(Math.max(0, Math.min(field.ns - 1, l[0]))), j = (int) Math.round(Math.max(0, Math.min(field.nn - 1, l[1])));
        int k = j * field.ns + i;
        return new double[]{lambda[k], height[k], speed[k]};
    }

    /** „Rippel: λ 8–14 cm, H bis 7 mm, wandern bis 3 cm/min“ */
    public String describe() {
        double lmin = 1e9, lmax = 0, hmax = 0, cmax = 0;
        for (int k = 0; k < lambda.length; k++) {
            if (height[k] > 1e-3) { lmin = Math.min(lmin, lambda[k]); lmax = Math.max(lmax, lambda[k]); }
            hmax = Math.max(hmax, height[k]);
            cmax = Math.max(cmax, speed[k]);
        }
        if (lmax == 0) return "Rippel: keine";
        return String.format(Locale.GERMAN, "Rippel: λ %.0f–%.0f cm, H bis %.0f mm, wandern bis %.1f cm/min", lmin * 100, lmax * 100, hmax * 1000, cmax * 6000);
    }
}
