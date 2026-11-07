package com.dan.precip;

/**
 * Physik des Regentropfens: Größenverteilung, Fallgeschwindigkeit, Form, Schwingung, Luftwiderstand, Kennzahlen des
 * Einschlags und die Wellen, die er auf dem Wasser macht. Alles in SI-Einheiten, Durchmesser D in mm, wo es so üblich
 * ist (Verteilungen, Fallgeschwindigkeit), sonst in m. Dieselben Formeln stehen Zeile für Zeile in
 * {@code gl/precip_common.glsl}.
 * <p>
 * Quellen: Marshall und Palmer (1948), Joss, Thams und Waldvogel (1968) für die Verteilungen; Gunn und Kinzer (1949) als
 * Messung der Fallgeschwindigkeit, Brandes, Zhang und Vivekanandan (2002) als Formel, Foote und du Toit (1969) für dünne
 * Luft; Beard und Chuang (1987) für die Form; Rayleigh (1879) für die Schwingung; Mundo, Sommerfeld und Tropea (1995) für
 * das Spritzen auf festem Grund; Engel (1966), Rein (1993), Pumphrey und Elmore (1990) für den Einschlag in tiefes
 * Wasser; Lamb (1932) für Kapillar- und Schwerewellen.
 */
public final class Drops {
    private Drops() {}

    /** Schwerebeschleunigung (m/s²), Dichte von Wasser (kg/m³), Oberflächenspannung (N/m), Zähigkeit von Wasser (Pa·s), kinematische Zähigkeit (m²/s). */
    public static final double G = 9.81, RHO_W = 998.2, SIGMA = 0.0728, MU_W = 1.002e-3, NU_W = MU_W / RHO_W;
    /** Luft am Boden (kg/m³) und ihre Zähigkeit (Pa·s). */
    public static final double RHO_AIR0 = 1.225, MU_AIR = 1.81e-5;

    // ------------------------------------------------------------------ Größenverteilung

    /**
     * Exponentialverteilungen N(D) = N0 · e^(−ΛD) in m⁻³ mm⁻¹ mit Λ = a · R^b mm⁻¹ (R in mm/h). N0 ist der Wert aus der
     * Literatur; {@link #n0(double)} gleicht ihn so ab, dass die Verteilung genau die Regenmenge R ergibt.
     */
    public enum Dsd {
        /** Landregen, Marshall und Palmer (1948). */
        LANDREGEN("Landregen", 8000, 4.1, -0.21),
        /** Niesel, Joss u. a. (1968): viele kleine Tropfen. */
        NIESEL("Niesel", 30000, 5.7, -0.21),
        /** Schauer und Gewitter, Joss u. a. (1968): wenige große Tropfen. */
        SCHAUER("Schauer", 1400, 3.0, -0.21);

        public final String label;
        public final double n0Literature, a, b;

        Dsd(String label, double n0, double a, double b) { this.label = label; this.n0Literature = n0; this.a = a; this.b = b; }

        /** Λ in mm⁻¹ bei R mm/h. */
        public double lambda(double rate) { return a * Math.pow(Math.max(0.01, rate), b); }

        /** N0 (m⁻³ mm⁻¹), mit dem die Verteilung bei Λ(R) genau R mm/h ergibt. */
        public double n0(double rate) {
            if (rate <= 0) return 0;
            double unit = rateIntegral(1, lambda(rate));
            return rate / unit;
        }
    }

    /** Regenmenge in mm/h einer Verteilung N0 · e^(−ΛD): 6π·10⁻⁴ ∫ N(D) D³ v(D) dD (D in mm, v in m/s). */
    public static double rateIntegral(double n0, double lambda) {
        double sum = 0, dd = 0.005;
        for (double d = dd / 2; d < 8; d += dd) sum += n0 * Math.exp(-lambda * d) * d * d * d * terminal(d) * dd;
        return 6 * Math.PI * 1e-4 * sum;
    }

    /** Tropfen je m³ mit D zwischen d0 und d1 (mm). */
    public static double numberDensity(double n0, double lambda, double d0, double d1) {
        return n0 / lambda * (Math.exp(-lambda * d0) - Math.exp(-lambda * d1));
    }

    /** Einschläge je m² und s auf eine waagrechte Fläche von Tropfen zwischen d0 und d1 (mm): ∫ N v dD. */
    public static double impacts(double n0, double lambda, double d0, double d1) {
        double sum = 0, dd = 0.005;
        for (double d = d0 + dd / 2; d < d1; d += dd) sum += n0 * Math.exp(-lambda * d) * terminal(d) * dd;
        return sum;
    }

    /** Kleinste Größe (mm), über der n Tropfen je m³ schweben (die Dichte der gezeichneten Tropfen). */
    public static double dminFor(double n0, double lambda, double perM3, double dmax) {
        if (perM3 <= 0 || n0 <= 0) return dmax;
        double e = perM3 * lambda / n0 + Math.exp(-lambda * dmax);
        return e >= 1 ? 0 : -Math.log(e) / lambda;
    }

    /** Eine Größe (mm) zwischen d0 und d1 aus der Verteilung für die Zufallszahl u ∈ [0, 1) (Umkehr der Verteilungsfunktion). */
    public static double sample(double lambda, double d0, double d1, double u) {
        double a = Math.exp(-lambda * d0), b = Math.exp(-lambda * d1);
        return -Math.log(a - u * (a - b)) / lambda;
    }

    /**
     * Eine Größe (mm) für einen Einschlag: Auf eine Fläche treffen die Tropfen nach N(D) v(D), nicht nach N(D) – die
     * großen fallen schneller und kommen öfter an. Gezogen aus N, behalten mit v(D) / v(d1) (genau, ohne Tafel); im
     * Mittel braucht ein Einschlag {@link #impactDraws} Züge. (Bis W7b Runde 4 zogen die Ringe nach N: zu wenige große.)
     */
    public static double sampleImpact(double lambda, double d0, double d1, java.util.random.RandomGenerator r) {
        double vMax = terminal(d1);
        while (true) {
            double d = sample(lambda, d0, d1, r.nextDouble());
            if (r.nextDouble() * vMax <= terminal(d)) return d;
        }
    }

    /** Züge aus N je m² und s für {@link #sampleImpact}: Dichte · v(d1); davon bleiben im Mittel {@link #impacts}. */
    public static double impactDraws(double n0, double lambda, double d0, double d1) {
        return numberDensity(n0, lambda, d0, d1) * terminal(d1);
    }

    /** Wasser in der Luft (g/m³): ρ_w π/6 ∫ N D³ dD. */
    public static double liquidWater(double n0, double lambda) {
        return RHO_W * 1e3 * Math.PI / 6 * n0 * 6 / Math.pow(lambda, 4) * 1e-9;
    }

    /** Radarreflektivität Z in mm⁶ m⁻³: ∫ N D⁶ dD = N0 · 720 / Λ⁷. */
    public static double reflectivity(double n0, double lambda) { return n0 * 720 / Math.pow(lambda, 7); }

    /** Auslöschung (1/m) aus der Verteilung: zweimal der geometrische Querschnitt (Extinktionsparadoxon), ∫ 2 · π/4 · D² N dD. */
    public static double extinction(double n0, double lambda) { return 2 * Math.PI / 4 * n0 * 2 / Math.pow(lambda, 3) * 1e-6; }

    // ------------------------------------------------------------------ Fall

    /**
     * Fallgeschwindigkeit (m/s) eines Tropfens von D mm in Luft am Boden: bis 0,6 mm die Messung von Gunn und Kinzer
     * (1949), linear zwischen ihren Punkten (unter 0,1 mm in Stokes übergehend), ab 0,8 mm die Formel von Brandes u. a.
     * (2002), dazwischen gemischt. Gegen Gunn und Kinzer auf rund 2 % genau.
     */
    public static double terminal(double d) {
        if (d <= 0) return 0;
        if (d >= 0.8) return brandes(d);
        double gk = measured(d);
        if (d <= 0.6) return gk;
        double t = (d - 0.6) / 0.2;
        return gk + (brandes(d) - gk) * t;
    }

    /** Gunn und Kinzer zwischen ihren Punkten (0,1 bis 0,8 mm); darunter Stokes, das bei 0,1 mm auf die Messung trifft. */
    static double measured(double d) {
        if (d <= 0.1) {
            double f = 1 - (1 - 0.27 / stokes(0.1)) * (d / 0.1) * (d / 0.1);
            return stokes(d) * f;
        }
        int i = Math.min(5, (int) ((d - 0.1) / 0.1));
        double a = GK_SMALL[i], b = GK_SMALL[i + 1], t = (d - 0.1) / 0.1 - i;
        return a + (b - a) * t;
    }

    /** Gunn und Kinzer bei 0,1 bis 0,7 mm in 0,1er-Schritten (0,7: zwischen 0,6 und 0,8 gemittelt), wie im Shader. */
    static final double[] GK_SMALL = {0.27, 0.72, 1.17, 1.62, 2.06, 2.47, 2.87};

    /** Stokes: g D² (ρ_w − ρ_a) / (18 μ_a), D in mm. */
    static double stokes(double d) { return G * (d * 1e-3) * (d * 1e-3) * (RHO_W - RHO_AIR0) / (18 * MU_AIR); }

    /** Brandes u. a. (2002), D in mm; über 5,9 mm (dem Scheitel der Formel) bleibt die Geschwindigkeit stehen. */
    static double brandes(double d) {
        double c = Math.min(d, 5.9);
        return -0.1021 + c * (4.932 + c * (-0.9551 + c * (0.07934 - 0.002362 * c)));
    }

    /** Fallgeschwindigkeit in Luft der Dichte rho (kg/m³): v · (ρ0/ρ)^0,4 (Foote und du Toit 1969). */
    public static double terminal(double d, double rhoAir) { return terminal(d) * Math.pow(RHO_AIR0 / Math.max(0.3, rhoAir), 0.4); }

    /** Luftdichte (kg/m³) in der Höhe h (m) nach der Standardatmosphäre (Skalenhöhe 8 400 m). */
    public static double airDensity(double h) { return RHO_AIR0 * Math.exp(-Math.max(0, h) / 8400); }

    /** Gunn und Kinzer (1949): Durchmesser (mm) und gemessene Fallgeschwindigkeit (m/s), für die Prüfung. */
    public static final double[][] GUNN_KINZER = {
            {0.1, 0.27}, {0.2, 0.72}, {0.3, 1.17}, {0.4, 1.62}, {0.5, 2.06}, {0.6, 2.47}, {0.8, 3.27}, {1.0, 4.03}, {1.2, 4.64},
            {1.4, 5.17}, {1.6, 5.65}, {1.8, 6.09}, {2.0, 6.49}, {2.4, 7.18}, {2.8, 7.71}, {3.2, 8.14}, {3.6, 8.46}, {4.0, 8.73},
            {4.4, 8.92}, {4.8, 9.04}, {5.2, 9.12}, {5.6, 9.16}};

    /**
     * Beschleunigung durch den Luftwiderstand (m/s²) eines Tropfens mit der Fallgeschwindigkeit vt, der sich mit v
     * gegen die Luft w bewegt: −g · |v − w| · (v − w) / vt². In ruhender Luft fällt er so genau mit vt; den Böen folgt
     * ein kleiner Tropfen schnell, ein großer träge. Ergebnis in out (3 Werte).
     */
    public static void drag(double vx, double vy, double vz, double wx, double wy, double wz, double vt, double[] out) {
        double rx = vx - wx, ry = vy - wy, rz = vz - wz, r = Math.sqrt(rx * rx + ry * ry + rz * rz), k = -G * r / (vt * vt);
        out[0] = k * rx;
        out[1] = k * ry;
        out[2] = k * rz;
    }

    /** Zeit (s), in der ein Tropfen einer Böe folgt: τ = vt / g. */
    public static double relaxation(double d) { return terminal(d) / G; }

    // ------------------------------------------------------------------ Form und Schwingung

    /**
     * Achsenverhältnis (Höhe zu Breite) eines fallenden Tropfens nach Beard und Chuang (1987), D in mm:
     * 1,0048 + 0,0057 D − 2,628 D² + 3,682 D³ − 1,677 D⁴ (D in cm); höchstens 1.
     */
    public static double axisRatio(double d) {
        double c = Math.min(d, 8) * 0.1;
        return Math.min(1, 1.0048 + c * (0.0057 + c * (-2.628 + c * (3.682 - 1.677 * c))));
    }

    /** Grundton der Schwingung (Hz) eines Tropfens von D mm nach Rayleigh (n = 2): √(8σ / (ρ r³)) / 2π. */
    public static double oscillationHz(double d) {
        double r = d * 0.5e-3;
        return Math.sqrt(8 * SIGMA / (RHO_W * r * r * r)) / (2 * Math.PI);
    }

    /** Tropfen über diesem Durchmesser (mm) zerplatzen im Fall (Beutelzerfall); darüber zeichnet das Paket keinen. */
    public static final double BREAKUP = 6.5;

    // ------------------------------------------------------------------ Einschlag

    /** Weberzahl ρ v² D / σ (D in mm, v in m/s). */
    public static double weber(double d, double v) { return RHO_W * v * v * d * 1e-3 / SIGMA; }

    /** Reynoldszahl des Tropfens im Wasser ρ v D / μ. */
    public static double reynolds(double d, double v) { return RHO_W * v * d * 1e-3 / MU_W; }

    /** Ohnesorgezahl μ / √(ρ σ D). */
    public static double ohnesorge(double d) { return MU_W / Math.sqrt(RHO_W * SIGMA * d * 1e-3); }

    /** Spritzzahl K = Oh · Re^1,25 nach Mundo u. a. (1995): über {@link #SPLASH_K} spritzt der Tropfen auf festem Grund. */
    public static double splashK(double d, double v) { return ohnesorge(d) * Math.pow(reynolds(d, v), 1.25); }

    public static final double SPLASH_K = 57.7;

    // ------------------------------------------------------------------ Wellen

    /** Kreisfrequenz (1/s) einer Welle mit der Wellenzahl k (1/m) auf tiefem Wasser: ω² = g k + (σ/ρ) k³. */
    public static double omega(double k) { return Math.sqrt(G * k + SIGMA / RHO_W * k * k * k); }

    /** Phasengeschwindigkeit ω/k (m/s); am kleinsten (23 cm/s) bei λ = 2π √(σ / (ρ g)) = 1,7 cm. */
    public static double phaseSpeed(double k) { return omega(k) / k; }

    /** Gruppengeschwindigkeit dω/dk (m/s). */
    public static double groupSpeed(double k) {
        double s = SIGMA / RHO_W;
        return (G + 3 * s * k * k) / (2 * omega(k));
    }

    /** Wellenzahl (1/m) der langsamsten Welle: √(ρ g / σ). */
    public static double kMin() { return Math.sqrt(RHO_W * G / SIGMA); }

    /** Abklingrate (1/s) einer Welle durch die Zähigkeit: 2 ν k². */
    public static double viscousDecay(double k) { return 2 * NU_W * k * k; }
}
