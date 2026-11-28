package com.dan.physics.swash;

/**
 * Kennwerte des Bodens für den Auflauf (Seegang Werkbuch, S6a): Reibung aus der Rauheit, Durchlässigkeit aus dem Korn.
 * Alle Werte sind gestaltet [G] aus den genannten Formeln und Quellen; die Zuordnung zu den Klassen des Geländes macht die
 * Anwendung (seit S6c-3 {@code lauf.Bodenkarte}).
 */
public final class Boden {
    private Boden() { }

    /** Saugspannung an der Front (m) und wirksamer Porenanteil für Sand nach Rawls, Brakensiek und Miller (1983): ψ 4,95 cm, θ_e 0,417. */
    public static final double PSI_SAND = 0.0495, THETA_E_SAND = 0.417;
    /** Freier Porenanteil Δθ = θ_e (1 − S_e) mit einer Anfangsfüllung S_e von rund 0,3 [G: Sand am Ufer ist nie ganz trocken]. */
    public static final double DTHETA_SAND = 0.3;
    /** Korngröße der Grenze Sand/Schluff (m) und darunter die Durchlässigkeit von Schlick (m/s) [G: Ton und Schluff 1e-10 … 1e-8]. */
    public static final double D_SAND = 0.063e-3, K_SCHLICK = 1e-9;

    /**
     * Seit S6c-3 (Seegang Werkbuch 80, SF89 A): Böden, die nicht aus dem Korn folgen. Durchlässigkeit (m/s): Klei der
     * Deiche, Warften und Marsch {@link #K_KLEI} [G: Deckschicht im Deichbau k ≤ 1e-8 m/s, EAK 2002]; Schotter
     * {@link #K_SCHOTTER} [G]; dicht ({@link #K_DICHT}: Deckwerk, Asphalt, Beton, Klinker, Holz). Manning-Beiwert [G, nach
     * Chow 1959]: kurze Grasnarbe {@link #N_GRAS_KURZ} (Deich, Warft), höheres Gras {@link #N_GRAS} (Fenne, Salzwiese,
     * Grasweg), gesetzte Steine {@link #N_STEIN} (Deckwerk), glatt {@link #N_GLATT}, Schotter {@link #N_SCHOTTER}.
     */
    public static final double K_KLEI = 1e-8, K_SCHOTTER = 1e-3, K_DICHT = 0;
    public static final double N_GRAS_KURZ = 0.020, N_GRAS = 0.030, N_STEIN = 0.018, N_GLATT = 0.013, N_SCHOTTER = 0.025;

    /**
     * Manning-Beiwert aus der Rauheit k_s (m) nach Strickler in der Form n = k_s^(1/6) / (8,1 √g) (Henderson 1966) [G].
     * Sand mit k_s = 2,5 d50 (Nikuradse): d50 0,2 mm gibt n ≈ 0,011.
     */
    public static double manning(double ks) { return Math.pow(Math.max(ks, 1e-6), 1.0 / 6.0) / (8.1 * Math.sqrt(9.81)); }

    /** Manning-Beiwert für glatten Sand aus d50 (m), k_s = 2,5 d50. */
    public static double manningSand(double d50) { return manning(2.5 * d50); }

    /**
     * Durchlässigkeit (m/s) aus d50 (m): Sand nach Hazen, K = 0,01 m/s · (d10 / mm)² mit d10 = 0,6 d50 [G: gut sortierter
     * Strandsand]; unter der Sandgrenze bis halb so fein logarithmisch auf {@link #K_SCHLICK} [G], darunter Schlick.
     * d50 0,2 mm gibt 1,4e-4 m/s.
     */
    public static double durchlaessigkeit(double d50) {
        if (!(d50 > 0)) return 0;
        if (d50 >= 2 * D_SAND) return hazen(d50);
        if (d50 <= 0.5 * D_SAND) return K_SCHLICK;
        double a = Math.log(0.5 * D_SAND), z = Math.log(2 * D_SAND), w = (Math.log(d50) - a) / (z - a);
        return Math.exp(Math.log(K_SCHLICK) + w * (Math.log(hazen(2 * D_SAND)) - Math.log(K_SCHLICK)));
    }

    static double hazen(double d50) {
        double d10mm = 0.6 * d50 * 1e3;
        return 0.01 * d10mm * d10mm;
    }
}
