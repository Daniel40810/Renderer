package com.dan.lamp;

/**
 * Spiegelung der Lichter auf nassem Pflaster und auf dem Wasser (seit Timmendorf Phase 11, F18 A): Mikrofacetten nach
 * GGX (Walter u. a. 2007) mit höhenkorrelierter Smith-Abschattung (Heitz 2014) und Fresnel nach Schlick. Die langen
 * senkrechten Lichtstreifen auf nasser Straße kommen von selbst heraus: bei flachem Blick ist die Keule der Spiegelung
 * in Blickrichtung lang und quer schmal.
 * <p>
 * Nässe macht Flächen dunkler (Wasser füllt die Poren, Lekner und Dorf 1988) und glatter; ein Wasserfilm hat F0 = 0,02.
 * Rauigkeit des Meeres aus dem Wind nach Cox und Munk (1954): mittlere Neigungsquadrat 0,003 + 0,00512·U.
 */
public final class Wet {
    private Wet() { }

    /** Flächen mit trockener und nasser Albedo und Rauigkeit (GGX α) [G]. */
    public enum Surface {
        ASPHALT(0.12, 0.05, 0.55, 0.10), COBBLE(0.25, 0.13, 0.45, 0.08), FLAGSTONE(0.30, 0.17, 0.40, 0.06), PLANKS(0.30, 0.18, 0.50, 0.12), SAND(0.35, 0.20, 0.80, 0.35);
        public final double dryAlbedo, wetAlbedo, dryAlpha, wetAlpha;
        Surface(double da, double wa, double dr, double wr) { dryAlbedo = da; wetAlbedo = wa; dryAlpha = dr; wetAlpha = wr; }

        /** Albedo bei Nässe 0..1. */
        public double albedo(double wetness) { return dryAlbedo + (wetAlbedo - dryAlbedo) * clamp(wetness); }

        /** Rauigkeit bei Nässe 0..1: erst füllen sich die Poren, dann glättet der Film (Pfützen ab 0,8). */
        public double alpha(double wetness) {
            double w = clamp(wetness), a = dryAlpha + (wetAlpha - dryAlpha) * Math.sqrt(w);
            return w > 0.8 ? a + (0.02 - a) * (w - 0.8) / 0.2 : a;
        }

        /** Anteil der Spiegelung (0 trocken, 1 nass). */
        public double specular(double wetness) { return 0.15 + 0.85 * clamp(wetness); }
    }

    static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    /** GGX-α der Meeresoberfläche aus der Windgeschwindigkeit (m/s) in 10 m Höhe, Cox und Munk. */
    public static double seaAlpha(double windSpeed) { return Math.sqrt(0.003 + 0.00512 * Math.max(0, windSpeed)); }

    /** Fresnel nach Schlick. */
    public static double fresnel(double f0, double cos) {
        double m = 1 - Math.max(0, Math.min(1, cos));
        return f0 + (1 - f0) * m * m * m * m * m;
    }

    /** GGX-Normalverteilung D(n·h). */
    public static double ggx(double nh, double a) {
        double a2 = a * a, d = nh * nh * (a2 - 1) + 1;
        return a2 / (Math.PI * d * d);
    }

    /** Höhenkorrelierte Smith-Sichtbarkeit V = G / (4 n·l n·v). */
    public static double visibility(double nl, double nv, double a) {
        double a2 = a * a;
        double gv = nl * Math.sqrt(nv * nv * (1 - a2) + a2), gl = nv * Math.sqrt(nl * nl * (1 - a2) + a2);
        return 0.5 / Math.max(1e-9, gv + gl);
    }

    /** Spiegelnde BRDF (1/sr) für Normale n, Richtung zum Licht l, Richtung zum Auge v (Einheitsvektoren). */
    public static double brdf(double[] n, double[] l, double[] v, double a, double f0) {
        double nl = dot(n, l), nv = dot(n, v);
        if (nl <= 0 || nv <= 0) return 0;
        double hx = l[0] + v[0], hy = l[1] + v[1], hz = l[2] + v[2], hl = Math.sqrt(hx * hx + hy * hy + hz * hz);
        hx /= hl; hy /= hl; hz /= hl;
        double nh = n[0] * hx + n[1] * hy + n[2] * hz, vh = v[0] * hx + v[1] * hy + v[2] * hz;
        return ggx(nh, a) * visibility(nl, nv, a) * fresnel(f0, vh);
    }

    /**
     * Leuchtdichte (cd/m², Helligkeit) einer waagerechten nassen Fläche am Ort p, gesehen vom Auge e, durch die Leuchte l:
     * spiegelnd plus diffus.
     */
    public static double luminance(Lamp lamp, double[] p, double[] eye, Surface s, double wetness) {
        double lx = lamp.x - p[0], ly = lamp.y - p[1], lz = lamp.z - p[2], d2 = lx * lx + ly * ly + lz * lz, d = Math.sqrt(d2);
        double[] l = {lx / d, ly / d, lz / d};
        double vx = eye[0] - p[0], vy = eye[1] - p[1], vz = eye[2] - p[2], dv = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double[] v = {vx / dv, vy / dv, vz / dv}, n = {0, 1, 0};
        double e = lamp.illuminance(p[0], p[1], p[2], 0, 1, 0);
        if (e <= 0) return 0;
        return e * (s.albedo(wetness) / Math.PI + s.specular(wetness) * brdf(n, l, v, s.alpha(wetness), 0.02));
    }

    /** Dasselbe auf dem Wasser (dunkel, nur Spiegelung; α aus dem Wind). */
    public static double waterLuminance(Lamp lamp, double[] p, double[] eye, double windSpeed) {
        double lx = lamp.x - p[0], ly = lamp.y - p[1], lz = lamp.z - p[2], d = Math.sqrt(lx * lx + ly * ly + lz * lz);
        double vx = eye[0] - p[0], vy = eye[1] - p[1], vz = eye[2] - p[2], dv = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double e = lamp.illuminance(p[0], p[1], p[2], 0, 1, 0);
        if (e <= 0) return 0;
        return e * brdf(new double[]{0, 1, 0}, new double[]{lx / d, ly / d, lz / d}, new double[]{vx / dv, vy / dv, vz / dv}, seaAlpha(windSpeed), 0.02);
    }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
}
