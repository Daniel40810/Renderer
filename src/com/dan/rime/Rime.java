package com.dan.rime;

import java.util.Locale;

/**
 * Raueis und Raureif (neu für Brocken, F9 A). Eis, das sich an Objekten ansetzt: Raueis aus unterkühlten
 * Nebeltröpfchen, das gegen den Wind als Fahne wächst, Raureif aus klaren Nächten (fein, ohne Richtung) und Klareis aus
 * Eisregen. Das Package kennt keine Fichte und keinen Mast: das Wetter am Objekt kommt als {@link Lage}, das Objekt als
 * {@link Ansatz} (Durchmesser, Dicke, Richtung, Art, Masse je Meter).
 * <p>
 * Wachstum nach Makkonen (2000): dM/dt = α1·α2·α3·w·v·A je Meter des Objekts, mit dem Fangkoeffizienten α1 nach der
 * empirischen Anpassung von Finstad, Lozowski und Gates (1988) an die Trägheit der Tröpfchen (Stokes-Zahl K und
 * Langmuir-Parameter φ), α2 = 1 (trockene Tröpfchen haften), α3 aus der Wärmebilanz genähert: bei starkem Frost friert
 * alles an (trockenes Wachstum, Raueis), nahe 0 °C läuft ein Teil ab (nasses Wachstum, Klareis) [G]. Die Dichte des
 * Raueises nach Macklin (1962): ρ = 110·R^0,76. Dazu Raureif in klaren Nächten, Tauen und Abfall. Seit Brocken Phase
 * 10 geprüft (Hülle seit Phase 1).
 */
public final class Rime {
    /** Qualitätsstufen, eigene Kopie im Package (Paketvertrag Punkt 7): längster Rechenschritt (s). */
    public enum Quality {
        LOW(600), MEDIUM(300), HIGH(120), ULTRA(60);
        public final double maxStep;
        Quality(double maxStep) { this.maxStep = maxStep; }
    }

    /** Art des Eises. */
    public enum Art { KEINS, RAUEIS, RAUREIF, KLAREIS }

    /** Eigene kleine Schnittstelle: Eis fällt ab (für den Klang ab Phase 15). kg je Meter des Objekts. */
    @FunctionalInterface
    public interface Abfall { void fiel(Ansatz a, double kgJeMeter); }

    /** Das Wetter am Objekt. */
    public static final class Lage {
        /** Lufttemperatur (°C), Flüssigwasser im Nebel (g/m³), Wind (m/s), Richtung, aus der er kommt (Grad). */
        public double tempC = -5, lwc = 0.3, wind = 10, fromDeg = 270;
        /** Mittlerer Durchmesser der Tröpfchen (µm): Nebel 10–20, Nieselregen über 100 (dann Klareis). */
        public double tropfenUm = 15;
        /** Sonne auf dem Objekt (W/m²) und relative Feuchte (1 = gesättigt über Eis). */
        public double sonne = 0, feuchte = 1;
        @Override public String toString() {
            return String.format(Locale.GERMAN, "%.1f °C, LWC %.2f g/m³, Wind %.1f m/s aus %03.0f°, Tropfen %.0f µm, Sonne %.0f W/m²", tempC, lwc, wind, fromDeg, tropfenUm, sonne);
        }
    }

    /** Der Ansatz an einem Objekt (Stab, Zweig, Mast, Geländer) je Meter Länge. */
    public static final class Ansatz {
        /** Durchmesser des Objekts (m). */
        public final double durchmesser;
        /** Dicke des Eises (m), Richtung, in die die Fahne zeigt (Grad, gegen den Wind), Masse (kg/m), Dichte (kg/m³). */
        public double dicke, richtung = Double.NaN, masse, dichte;
        public Art art = Art.KEINS;
        /**
         * Seit Brocken Phase 10: raue Oberfläche (Fels, Rinde, Fichtenzweige, Masten mit Kanten): fangen wie Zylinder von
         * höchstens {@link #RAU} m Durchmesser [G], denn auf der Luvseite wachsen die Federn des Eises als viele kleine
         * Fänger. Ein glatter Zylinder (false, Vorgabe) fängt nach seinem Durchmesser.
         */
        public boolean rau;
        public Ansatz(double durchmesser) { this.durchmesser = durchmesser; }
        public Ansatz(double durchmesser, boolean rau) { this.durchmesser = durchmesser; this.rau = rau; }
        @Override public String toString() {
            return art == Art.KEINS ? "kein Eis" : String.format(Locale.GERMAN, "%s %.1f mm%s, %.3f kg/m, %.0f kg/m³", art, dicke * 1000,
                    Double.isNaN(richtung) ? "" : String.format(Locale.GERMAN, " nach %03.0f°", richtung), masse, dichte);
        }
    }

    /** Größter Durchmesser (m), mit dem eine raue Oberfläche fängt [G]. */
    public static final double RAU = 0.03;

    /** Dichte von Eis, Wasser und Luft (kg/m³), Zähigkeit der Luft (Pa·s, bei rund −5 °C). */
    public static final double RHO_EIS = 917, RHO_WASSER = 1000, RHO_LUFT = 1.3, MU_LUFT = 1.7e-5;

    /** Startwert des Zufalls (wann eine Fahne abbricht). */
    public long seed = 1;
    public Quality quality = Quality.MEDIUM;
    /** Raureif in klaren Nächten (m/s Zuwachs) [G: 0,2 mm/h]; Tauen je Kelvin über 0 °C und je W/m² Sonne (m/s) [G]. */
    public double raureifRate = 0.2e-3 / 3600, tauJeK = 0.6e-3 / 3600, tauJeW = 0.004e-3 / 3600;
    /** Eis fällt ab, wenn es taut und dicker als das ist (m). */
    public double abfallAb = 0.01;
    /** Wird gerufen, wenn Eis abfällt; null = niemand hört zu. */
    public Abfall onAbfall;

    // ------------------------------------------------------------------ Presets (Punkt 5)
    /** Raueislage: Nebel bei −6 °C und Wind 12 m/s aus West [G]. */
    public static Lage raueislage() { Lage l = new Lage(); l.tempC = -6; l.lwc = 0.3; l.wind = 12; l.fromDeg = 260; return l; }
    /** Klare Frostnacht: −8 °C, kein Nebel, kaum Wind (Raureif). */
    public static Lage klareNacht() { Lage l = new Lage(); l.tempC = -8; l.lwc = 0; l.wind = 1; return l; }
    /** Eisregen: −1 °C, Tropfen 500 µm, viel Wasser (Klareis). */
    public static Lage eisregen() { Lage l = new Lage(); l.tempC = -1; l.lwc = 0.5; l.wind = 6; l.tropfenUm = 500; return l; }
    /** Tauwetter mit Sonne: +3 °C, 400 W/m². */
    public static Lage tauwetter() { Lage l = new Lage(); l.tempC = 3; l.lwc = 0; l.wind = 4; l.sonne = 400; return l; }

    // ------------------------------------------------------------------ Wachstum

    /**
     * Stokes-Zahl der Tröpfchen am Zylinder: K = ρw·d²·v / (9·μ·D) mit dem Durchmesser d der Tröpfchen (mittlerer
     * Volumendurchmesser) und D des Objekts.
     */
    public static double stokes(double tropfenUm, double wind, double durchmesser) {
        double d = tropfenUm * 1e-6;
        return RHO_WASSER * d * d * Math.max(0, wind) / (9 * MU_LUFT * Math.max(1e-4, durchmesser));
    }

    /**
     * Fangkoeffizient α1 (0..1) nach Finstad, Lozowski und Gates (1988), wie bei Makkonen (2000):
     * α1 = A − 0,028 − C·(B − 0,0454) mit A = 1,066·K^−0,00616·e^(−1,103·K^−0,688), B = 3,641·K^−0,498·e^(−1,497·K^−0,694),
     * C = 0,00637·(φ − 100)^0,381, φ = Re²/K, Re = ρa·v·d/μ. Gilt ab K = 0,17; darunter fängt der Zylinder praktisch
     * nichts. Dünne Zweige fangen viel besser als dicke Stämme, große Tröpfchen besser als kleine, Wind fängt mehr.
     */
    public static double alpha1(double tropfenUm, double wind, double durchmesser) {
        double k = stokes(tropfenUm, wind, durchmesser);
        if (k < 0.17) return 0;
        double re = RHO_LUFT * Math.max(0, wind) * tropfenUm * 1e-6 / MU_LUFT, phi = re * re / k;
        double a = 1.066 * Math.pow(k, -0.00616) * Math.exp(-1.103 * Math.pow(k, -0.688));
        double b = 3.641 * Math.pow(k, -0.498) * Math.exp(-1.497 * Math.pow(k, -0.694));
        double c = 0.00637 * Math.pow(Math.max(0, phi - 100), 0.381);
        return Math.max(0, Math.min(1, a - 0.028 - c * (b - 0.0454)));
    }

    /**
     * Anfrierwirkungsgrad α3 (0..1) [G, genähert]: unter −3 °C friert alles, was trifft (trockenes Wachstum); bis 0 °C sinkt
     * er auf 0,4, je mehr Wasser je Sekunde ankommt, desto früher (die Wärme der Erstarrung wird nicht schnell genug
     * abgeführt, der Rest läuft ab und gefriert als Klareis).
     */
    public static double alpha3(double tempC, double flussKgM2s) {
        if (tempC <= -3) return 1;
        double t = Math.max(0, Math.min(1, -tempC / 3)), nass = Math.min(1, flussKgM2s / 0.003);
        return Math.max(0.4, Math.min(1, 1 - (1 - t) * 0.6 * (0.4 + 0.6 * nass)));
    }

    /** Masse je Meter und Sekunde (kg/(m·s)), die am Ansatz a in der Lage l anwächst (0 ohne Frost oder Nebel). */
    public static double rate(Ansatz a, Lage l) {
        if (l.tempC >= 0 || l.lwc <= 0 || l.wind <= 0.3) return 0;
        double dEff = a.durchmesser + 2 * a.dicke;
        double fluss = alpha1(l.tropfenUm, l.wind, a.rau ? Math.min(dEff, RAU) : dEff) * l.lwc * 1e-3 * l.wind;   // kg/(m²·s) auf die Luvseite
        return fluss * alpha3(l.tempC, fluss) * dEff;
    }

    /** Wie schnell die Fahne an einem Objekt mit Durchmesser d anfangs wächst (mm/h), für Anzeige und Prüfung. */
    public static double mmJeStunde(double durchmesser, Lage l) { return mmJeStunde(durchmesser, false, l); }

    /** Wie {@link #mmJeStunde(double, Lage)}, für eine raue (true) oder glatte Oberfläche. */
    public static double mmJeStunde(double durchmesser, boolean rau, Lage l) {
        Ansatz a = new Ansatz(durchmesser, rau);
        double m = rate(a, l) * 3600, rho = l.tropfenUm >= 100 ? RHO_EIS * 0.98 : macklin(l.tropfenUm, l.wind, l.tempC);
        return dickeAus(m, rho, durchmesser) * 1000;
    }

    /**
     * Dichte des Raueises nach Macklin (1962): ρ = 110·R^0,76 mit R = −d·v / (2·T), d in µm, v in m/s, T in °C. Die Formel
     * gilt für R von etwa 1 bis 10; darüber wird R auf 10 gehalten (rund 630 kg/m³), darunter gilt mindestens 100 kg/m³.
     */
    public static double macklin(double tropfenUm, double wind, double tempC) {
        double t = Math.min(-0.5, tempC);
        double r = Math.min(10, -(tropfenUm * Math.max(0.1, wind)) / (2 * t));
        return Math.max(100, Math.min(RHO_EIS, 110 * Math.pow(r, 0.76)));
    }

    /** Rechnet den Ansatz um dt Sekunden weiter (in Schritten bis {@link Quality#maxStep}). */
    public void step(Ansatz a, Lage l, double dt) {
        while (dt > 1e-9) {
            double h = Math.min(dt, quality.maxStep);
            once(a, l, h);
            dt -= h;
        }
    }

    private void once(Ansatz a, Lage l, double dt) {
        if (l.tempC < 0 && l.lwc > 0 && l.wind > 0.3) {
            // Querschnitt, der fängt: an der Fahne ihre Breite (D + 0,3 · Dicke, sie wächst gegen den Wind, nicht rundum),
            // am Klareis der Durchmesser mit dem Eis rundum
            double fahne = a.durchmesser + BREITER * a.dicke, rund = a.durchmesser + 2 * a.dicke;
            double f0 = alpha1(l.tropfenUm, l.wind, a.rau ? Math.min(fahne, RAU) : fahne) * l.lwc * 1e-3 * l.wind;
            double a3 = alpha3(l.tempC, f0);
            boolean klar = l.tropfenUm >= 100 || a3 < 0.8;   // Eisregen oder nasses Wachstum
            double dEff = klar ? rund : fahne;
            double fluss = klar ? alpha1(l.tropfenUm, l.wind, a.rau ? Math.min(rund, RAU) : rund) * l.lwc * 1e-3 * l.wind : f0;
            double rate = fluss * a3 * dEff;                  // kg/(m·s), α2 = 1
            double rho = klar ? RHO_EIS * 0.98 : macklin(l.tropfenUm, l.wind, l.tempC);
            a.masse += rate * dt;
            // die Fahne wächst gegen den Wind und wird dabei breiter (Breite = D + 0,3 · Dicke [G]):
            // Masse = ρ · Dicke · (D + 0,3 · Dicke), nach der Dicke gelöst
            a.dichte = a.dichte <= 0 ? rho : (a.dichte * 0.95 + rho * 0.05);
            // Raueis als Fahne gegen den Wind, Klareis als Ring rundum (ρ·π·t·(D + t) = Masse)
            a.dicke = klar && a.art != Art.RAUEIS ? ringAus(a.masse, a.dichte, a.durchmesser) : dickeAus(a.masse, a.dichte, a.durchmesser);
            if (a.art != Art.RAUEIS || !klar) a.art = klar ? Art.KLAREIS : Art.RAUEIS;
            a.richtung = klar ? Double.NaN : l.fromDeg;
        } else if (l.tempC < 0 && l.lwc <= 0 && l.feuchte >= 1 && l.wind < 3) {
            a.dicke += raureifRate * dt;
            a.dichte = 100;
            a.masse = a.dicke * a.dichte * Math.PI * (a.durchmesser + a.dicke);
            if (a.art == Art.KEINS) a.art = Art.RAUREIF;
        } else if (l.tempC > 0 || l.sonne > 300) {
            double tau = (Math.max(0, l.tempC) * tauJeK + l.sonne * tauJeW) * dt;
            double vorher = a.dicke;
            a.dicke = Math.max(0, a.dicke - tau);
            if (vorher > abfallAb && a.art != Art.RAUREIF && noise(vorher) < dt / 900) {
                // eine Fahne bricht ab (im Mittel nach einer Viertelstunde Tauwetter)
                double kg = a.masse;
                a.dicke = 0;
                if (onAbfall != null) onAbfall.fiel(a, kg);
            }
            a.masse = a.art == Art.KLAREIS ? Math.max(100, a.dichte) * Math.PI * a.dicke * (a.durchmesser + a.dicke)
                    : Math.max(100, a.dichte) * a.dicke * (a.durchmesser + BREITER * a.dicke);
            if (a.dicke <= 0) { a.art = Art.KEINS; a.masse = 0; a.richtung = Double.NaN; }
        }
    }

    /** Wie viel breiter die Fahne mit ihrer Dicke wird [G]. */
    public static final double BREITER = 0.3;

    /** Dicke (m) einer Fahne mit Masse m (kg/m) und Dichte ρ an einem Objekt mit Durchmesser d: ρ·t·(d + 0,3·t) = m. */
    public static double dickeAus(double m, double rho, double d) {
        double k = BREITER, q = Math.max(0, m) / Math.max(1, rho);
        return (-d + Math.sqrt(d * d + 4 * k * q)) / (2 * k);
    }

    /** Dicke (m) eines Rings aus Eis rundum (Klareis) mit Masse m (kg/m), Dichte ρ, Durchmesser d: ρ·π·t·(d + t) = m. */
    public static double ringAus(double m, double rho, double d) {
        double q = Math.max(0, m) / (Math.max(1, rho) * Math.PI);
        return (-d + Math.sqrt(d * d + 4 * q)) / 2;
    }

    /**
     * Last des Eises an einem Baum (kg) [G]: Masse je Meter am Zweig mal Länge der Zweige (m) in der Krone, die im Wind
     * steht (bei einer Fichte von 10 m rund 400 m, bei einer Krüppelfichte von 4 m rund 60 m).
     */
    public static double baumLast(Ansatz zweig, double zweigMeter) { return Math.max(0, zweig.masse) * Math.max(0, zweigMeter); }

    /** Wert 0..1 aus seed und Zustand (reproduzierbar). */
    double noise(double v) {
        long h = seed * 0x9E3779B97F4A7C15L + Double.doubleToLongBits(v) * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }
}
