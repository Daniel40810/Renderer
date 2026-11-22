package com.dan.physics.atmos;

/**
 * Stoffwerte der feuchten Luft und die Brechzahl nach Ciddor (Werkbuch HalligHooge Teil A, 7.5 und 12.11, P10a).
 * <p>
 * <b>Brechzahl:</b> Ciddor (1996, Applied Optics 35, 1566), in der Fassung der Dokumentation des NIST (Stone und
 * Zimmerman, Engineering Metrology Toolbox, Anhang A-III, Gleichungen A21–A41; am 18.11.2026 dort nachgelesen):
 * Brechung der trockenen Normluft und des Wasserdampfs je für sich, gewichtet mit den Dichten der beiden Anteile aus
 * der Zustandsgleichung des BIPM mit dem Kompressionsfaktor Z. Gültig für 0,3–1,7 µm.
 * <p>
 * <b>Sättigungsdampfdruck</b> über Wasser nach IAPWS-IF97 (dieselbe Formel, die das NIST nimmt; Konstanten K1–K10 aus
 * Tabelle A1 dort), auch unter 0 °C (unterkühltes Wasser [G]; Eis rechnet atmos nicht).
 * <p>
 * Kennt nur java.* (Schichtenvertrag, Werkbuch 4).
 */
public final class Luft {
    private Luft() { }

    /** Normschwere (m/s²), von-Kármán-Konstante, Stefan-Boltzmann-Konstante (W/(m² K⁴)). */
    public static final double G = 9.80665, KAPPA = 0.40, SIGMA = 5.670374419e-8;
    /** Gaskonstante (J/(mol K)) und Molmasse des Wasserdampfs (kg/mol), wie bei Ciddor. */
    public static final double R = 8.314472, MV = 0.018015;
    /** Molmasse trockener Luft bei 400 µmol/mol CO₂ (kg/mol), ihre Gaskonstante (J/(kg K)), Verhältnis der Molmassen. */
    public static final double MA = 0.0289635, RD = R / MA, EPS = MV / MA;
    /** Wärmekapazität trockener Luft bei festem Druck (J/(kg K)) und das trockenadiabatische Gefälle g/c_p (K/m). */
    public static final double CP = 1004.7, GAMMA = G / CP;
    /** Wasser: Dichte (kg/m³), Wärmekapazität (J/(kg K)); Zähigkeit der Luft (m²/s, 10 °C). */
    public static final double RHO_W = 1000, C_W = 4186, NU = 1.42e-5;

    private static final double K1 = 1.16705214528e3, K2 = -7.24213167032e5, K3 = -1.70738469401e1, K4 = 1.20208247025e4, K5 = -3.23255503223e6,
            K6 = 1.49151086135e1, K7 = -4.82326573616e3, K8 = 4.05113405421e5, K9 = -2.38555575678e-1, K10 = 6.50175348448e2;

    /** Sättigungsdampfdruck über Wasser (Pa) bei der Temperatur T (K), IAPWS-IF97. */
    public static double saettigung(double T) {
        double o = T + K9 / (T - K10);
        double a = o * o + K1 * o + K2, b = K3 * o * o + K4 * o + K5, c = K6 * o * o + K7 * o + K8;
        double x = 2 * c / (-b + Math.sqrt(b * b - 4 * a * c));
        double x2 = x * x;
        return 1e6 * x2 * x2;
    }

    /** Verdampfungswärme des Wassers (J/kg) bei T (K), linear um 0 °C. */
    public static double verdampfung(double T) { return 2.501e6 - 2361 * (T - 273.15); }

    /** Spezifische Feuchte (kg/kg) aus Dampfdruck e und Druck p (Pa). */
    public static double feuchte(double e, double p) { return EPS * e / (p - (1 - EPS) * e); }

    /** Dampfdruck (Pa) aus spezifischer Feuchte q (kg/kg) und Druck p (Pa). */
    public static double dampfdruck(double q, double p) { return q * p / (EPS + (1 - EPS) * q); }

    /** Spezifische Feuchte der gesättigten Luft (kg/kg) bei T (K) und p (Pa). */
    public static double feuchteSatt(double T, double p) { return feuchte(saettigung(T), p); }

    /** Ableitung von {@link #feuchteSatt} nach T (1/K), nach Clausius-Clapeyron; nur für die Schrittweite von Newton. */
    public static double feuchteSattNachT(double T, double p) {
        double e = saettigung(T), de = verdampfung(T) * e / (R / MV * T * T), n = p - (1 - EPS) * e;
        return EPS * p * de / (n * n);
    }

    /** Dichte der feuchten Luft (kg/m³) aus T (K), q (kg/kg), p (Pa). */
    public static double dichte(double T, double q, double p) { return p / (RD * T * (1 + (1 / EPS - 1) * q)); }

    /** Anreicherungsfaktor f(p, t) des Dampfdrucks in Luft (NIST A16, A17); t in °C. */
    public static double anreicherung(double p, double tC) { return 1.00062 + 3.14e-8 * p + 5.60e-7 * tC * tC; }

    /** Molanteil des Wasserdampfs aus der relativen Feuchte (0–1) nach NIST A19. */
    public static double molanteil(double rf, double T, double p) { return rf * anreicherung(p, T - 273.15) * saettigung(T) / p; }

    /** Brechzahl der Luft nach Ciddor (1996): 1 + {@link #brechung}. */
    public static double brechzahl(double lambda, double T, double p, double xv, double co2) { return 1 + brechung(lambda, T, p, xv, co2); }

    /**
     * Brechung n − 1 der Luft nach Ciddor (1996); ohne die Eins, damit kleine Unterschiede zwischen zwei Höhen ihre
     * Stellen behalten.
     *
     * @param lambda Wellenlänge im Vakuum (µm), 0,3–1,7
     * @param T      Temperatur (K)
     * @param p      Druck (Pa)
     * @param xv     Molanteil des Wasserdampfs (Dampfdruck / Druck)
     * @param co2    Kohlendioxid (µmol/mol)
     */
    public static double brechung(double lambda, double T, double p, double xv, double co2) {
        double s = 1 / (lambda * lambda), t = T - 273.15;
        double ras = 1e-8 * (5792105 / (238.0185 - s) + 167917 / (57.362 - s));                            // A32
        double rvs = 1.022e-8 * (295.235 + 2.6422 * s - 0.03238 * s * s + 0.004028 * s * s * s);          // A33
        double ma = 0.0289635 + 1.2011e-8 * (co2 - 400);                                                  // A34
        double raxs = ras * (1 + 5.34e-7 * (co2 - 450));                                                  // A35
        double pt = p / T;
        double zm = 1 - pt * (1.58123e-6 - 2.9331e-8 * t + 1.1043e-10 * t * t + (5.707e-6 - 2.051e-8 * t) * xv + (1.9898e-4 - 2.376e-6 * t) * xv * xv)
                + pt * pt * (1.83e-11 - 0.765e-8 * xv * xv);                                              // A37
        double rhoAxs = 101325 * ma / (0.9995922115 * R * 288.15);                                         // A38
        double rhoV = xv * p * MV / (zm * R * T), rhoA = (1 - xv) * p * ma / (zm * R * T);                // A39, A40
        return rhoA / rhoAxs * raxs + rhoV / 0.00985938 * rvs;                                             // A41
    }

    /** Brechung n − 1 aus den Größen des Wetters: spezifische Feuchte q statt des Molanteils. */
    public static double brechungFeucht(double lambda, double T, double p, double q, double co2) {
        return brechung(lambda, T, p, dampfdruck(q, p) / p, co2);
    }
}
