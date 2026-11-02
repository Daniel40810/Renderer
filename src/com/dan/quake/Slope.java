package com.dan.quake;

/**
 * Ein Hang unter dem Beben nach Newmark (NEWMARK65, Werkbuch 9.1): unendlicher Hang mit Gleitfläche unter dem Winkel
 * {@link #alpha}, Reibungswinkel {@link #phi}, Kohäsion {@link #cohesion} (kPa) der Klüfte, Wichte {@link #gamma}
 * (kN/m³) und Mächtigkeit {@link #depth} (m) der Platte. Sicherheitsfaktor
 * FS = (c' + γ z cos²α tan φ') / (γ z sin α cos α); kritische Beschleunigung a_c = (FS − 1) g sin α. Die Verschiebung
 * entsteht, solange die Beschleunigung hangabwärts (die Trägheit der Platte, wenn der Boden hangaufwärts beschleunigt)
 * a_c übersteigt, und läuft aus, bis die Gleitgeschwindigkeit wieder null ist (nur hangabwärts). Ab {@link #failAt}
 * Verschiebung gilt der Hang als versagt (Übergabe an {@code landslide}).
 * <p>
 * Seit LituyaBay1958 Phase 9 (Werkbuch 9.2) mit Entfestigung: die Kohäsion der Klüfte sinkt mit der Verschiebung D von
 * c' auf den Rest c'·{@link #residual} (c(D) = c_r + (c' − c_r)·e^(−D/{@link #softening})); damit sinkt a_c während
 * des Bebens. Fällt a_c auf null, gleitet die Platte von selbst weiter (Durchgehen), auch ohne Stoß: dann gilt der Hang
 * als versagt, ebenso ab {@link #failAt}. {@link #residual} = 1 (Vorgabe) ist der Hang ohne Entfestigung wie bisher.
 */
public final class Slope {
    public double alpha = 40, phi = 35, cohesion = 60, gamma = 27, depth = 92;
    /** Richtung hangabwärts (Grad von Nord im Uhrzeigersinn). */
    public double downslopeAzimuth = 225;
    /** Schwelle des Versagens (m) [Werkbuch 9.1: 10 cm]. */
    public double failAt = 0.10;
    public String name = "Hang";
    /** Entfestigung: Anteil der Kohäsion, der nach großer Verschiebung bleibt (1 = keine Entfestigung) [G]. */
    public double residual = 1;
    /** Entfestigung: Verschiebung (m), nach der der Abfall der Kohäsion auf 1/e gesunken ist [G]. */
    public double softening = 0.5;

    /** Kohäsion (kPa) nach der Verschiebung d (m). */
    public double cohesionAt(double d) {
        double cr = cohesion * residual;
        return cr + (cohesion - cr) * Math.exp(-Math.max(0, d) / Math.max(1e-9, softening));
    }

    /** Kritische Beschleunigung (m/s²) nach der Verschiebung d (m), mit Entfestigung. */
    public double acAt(double d) {
        double c = cohesion;
        cohesion = cohesionAt(d);
        double r = ac();
        cohesion = c;
        return r;
    }

    /** Sicherheitsfaktor in Ruhe. */
    public double fs() { return fs(0); }

    /** Sicherheitsfaktor pseudo-statisch mit waagerechtem Beiwert k (hangabwärts). */
    public double fs(double k) {
        double a = Math.toRadians(alpha), t = Math.tan(Math.toRadians(phi)), w = gamma * depth;
        double num = cohesion + w * (Math.cos(a) * Math.cos(a) - k * Math.sin(a) * Math.cos(a)) * t;
        double den = w * (Math.sin(a) * Math.cos(a) + k * Math.cos(a) * Math.cos(a));
        return num / den;
    }

    /** Kritische Beschleunigung (m/s²); ≤ 0 heißt: schon in Ruhe instabil. */
    public double ac() { return (fs() - 1) * 9.81 * Math.sin(Math.toRadians(alpha)); }

    /** Kohäsion (kPa), bei der der Hang den Sicherheitsfaktor fs hat. */
    public double cohesionFor(double fsWanted) {
        double a = Math.toRadians(alpha), t = Math.tan(Math.toRadians(phi)), w = gamma * depth;
        return fsWanted * w * Math.sin(a) * Math.cos(a) - w * Math.cos(a) * Math.cos(a) * t;
    }

    /**
     * Ergebnis: Verschiebung über der Zeit (m), Ende (m), Zeit des Versagens (NaN, wenn nicht); seit Phase 9 dazu die
     * kleinste kritische Beschleunigung (m/s², mit Entfestigung) und ob der Hang durchgegangen ist (a_c ≤ 0).
     */
    public record Result(double t0, double dt, double[] displacement, double total, double failTime, double acMin, boolean runaway) {
        public Result(double t0, double dt, double[] displacement, double total, double failTime) { this(t0, dt, displacement, total, failTime, Double.NaN, false); }
    }

    /** Newmark-Verschiebung in einer Zeitreihe (mit Entfestigung, wenn {@link #residual} &lt; 1). */
    public Result newmark(Quake.Record r) {
        double dt = r.dt, v = 0, d = 0, tf = Double.NaN, ac = ac(), acMin = ac;
        boolean soft = residual < 1, run = false;
        double[] out = new double[r.length()];
        double az = Math.toRadians(downslopeAzimuth), ex = Math.sin(az), sz = -Math.cos(az);
        for (int i = 0; i < r.length(); i++) {
            // Trägheit der Platte hangabwärts = − Bodenbeschleunigung in diese Richtung
            double a = -(r.east[i] * ex + r.south[i] * sz);
            if (soft) { ac = acAt(d); acMin = Math.min(acMin, ac); }
            if (v > 0 || a > ac) {
                v = Math.max(0, v + (a - ac) * dt);
                d += v * dt;
            }
            out[i] = d;
            if (soft && ac <= 0 && !run) run = true;
            if (Double.isNaN(tf) && (d >= failAt || run)) tf = r.t0 + i * dt;
        }
        return new Result(r.t0, dt, out, d, tf, acMin, run);
    }

    /** Newmark-Verschiebung für einen Rechteckimpuls der Höhe A (m/s²) und Dauer T (s): (A − a_c) A T² / (2 a_c). */
    public static double rectangularPulse(double a, double ac, double t) { return a <= ac ? 0 : (a - ac) * a * t * t / (2 * ac); }

    /** Faustformel JIBSON93: log10 D(cm) = 1,521 log10 I_A − 1,993 log10 a_c(g) − 1,546; gibt D in m. */
    public static double jibson93(double ariasMs, double acG) {
        return Math.pow(10, 1.521 * Math.log10(ariasMs) - 1.993 * Math.log10(acG) - 1.546) / 100;
    }
}
