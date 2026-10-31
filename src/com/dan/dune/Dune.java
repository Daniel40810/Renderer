package com.dan.dune;

/**
 * Flugsand und Dünen (Sylt, Werkbuch Abschnitt 5): Sand, der vom Wind getragen wird, wo er liegen bleibt und wie Dünen
 * wandern. <b>Stand: Sylt Phase 9 (dune II).</b> Je Ort: Schubspannungsgeschwindigkeit u* aus dem Wind
 * (logarithmisches Profil), die Schwelle nach Bagnold (1941), ihre Erhöhung durch Feuchte nach Belly (1964), durch
 * Bewuchs nach der Aufteilung der Schubspannung von Raupach (1992) und am Hang nach Dyer (1986), und der Massenfluss.
 * Darauf bauen {@link FluxField} (Fluss mit Sättigungslänge, Beschleunigung über Kämmen, Windschatten, Ablagerung),
 * {@link Grains} (Saltation als Körner mit Sandfahnen und Kammfahnen) und {@link Ripples} (Windrippel). Seit Phase 9
 * das Höhenfeld des losen Sands mit Strandhafer und Zäunen ({@link SandBed}) und die Dünenwanderung im Zeitraffer
 * ({@link Werner}, {@link TimeLapse}) (F11 A, F12 A).
 * <p>
 * Der Kern kennt nur Java 21. Wind, Feuchte, Bewuchs und Boden kommen über die eigenen kleinen Schnittstellen
 * {@link Wind}, {@link Moisture}, {@link Cover} und {@link Ground} (Paketvertrag Punkt 4); die App reicht dort
 * {@code wind}, {@code mudflat}, {@code rain}, {@code reed} und das Gelände hindurch.
 */
public final class Dune {
    /** Qualitätsstufen, eigene Kopie im Package (Paketvertrag Punkt 7). */
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    /** Wind in {@link #refHeight} über Grund: Stärke in m/s und Richtung, aus der er kommt (Grad von Nord). */
    public interface Wind { double speed(double x, double z); double fromDeg(double x, double z); }

    /** Feuchte des Sands an der Oberfläche als Massenanteil (0,02 = 2 %). */
    @FunctionalInterface
    public interface Moisture { double moisture(double x, double z); }

    /** Bewuchs als Rauigkeitsdichte λ (Stirnfläche der Halme je Bodenfläche), 0 = kahler Sand. */
    @FunctionalInterface
    public interface Cover { double lambda(double x, double z); }

    /** Höhe des Bodens über der Bezugshöhe, in m. */
    @FunctionalInterface
    public interface Ground { double height(double x, double z); }

    /** Ereignis nach außen: an diesem Ort fliegt Sand (Fluss in kg/(m·s)). */
    @FunctionalInterface
    public interface Saltation { void saltation(double x, double z, double flux); }

    /** Ereignis nach außen: hier wächst (onDeposit, z. B. Strandhafer wird übersandet) oder schwindet (onScour) der Sand, Rate in m/s. */
    @FunctionalInterface
    public interface Deposit { void deposit(double x, double z, double rate); }

    public static final double G = 9.81, KARMAN = 0.41;

    /** Körner im Bild je Stufe (für {@link Grains}). */
    public int grainBudget() {
        return switch (quality) { case LOW -> 4000; case MEDIUM -> 10000; case HIGH -> 20000; case ULTRA -> 50000; };
    }

    public long seed = 1;
    public Quality quality = Quality.HIGH;
    /** Korngröße d in m (Dünensand 0,25–0,35 mm), Kornrohdichte und Luftdichte in kg/m³ [belegt: Quarz 2 650]. */
    public double grain = 0.30e-3, rhoSand = 2650, rhoAir = 1.225;
    /** Bagnolds Beiwert A der Schwelle (0,1 für Fluid-Schwelle bei Korn über 0,1 mm) [belegt]. */
    public double bagnoldA = 0.1;
    /** Bagnolds Beiwert C des Flusses: 1,5 gleichkörnig, 1,8 Dünensand, 2,8 gemischt [belegt]; Bezugskorn D = 0,25 mm. */
    public double bagnoldC = 1.8;
    /** Rauigkeitslänge des Sands in m und Höhe, in der der Wind gilt [G]. */
    public double z0 = 1e-4, refHeight = 10;
    /** Raupach (1992): β = C_R/C_S, σ = Grund- zu Stirnfläche, m für ungleichmäßige Verteilung [G nach Raupach u. a. 1993]. */
    public double raupachBeta = 90, raupachSigma = 1, raupachM = 0.5;
    /** Der Wind als offener Wert, wenn kein {@link #wind} gesetzt ist. */
    public double windSpeed = 8, windFromDeg = 270;

    /** Sättigungslänge ℓ = c·(ρs/ρ)·d mit c = 2,2 (Andreotti, Claudin und Pouliquen 2010) [belegt]. */
    public double satFactor = 2.2;
    /** Schüttdichte des abgelegten Sands in kg/m³ (Porosität 0,4) [belegt]; Schüttwinkel in Grad [belegt: 32–34°]. */
    public double bulkDensity = 1600, reposeDeg = 34;
    /** Störung der Schubspannung über Hügeln nach Kroy, Sauermann und Herrmann (2002): τ̂ = A·(ℋ[h'] + B·h') [G nach Kroy u. a.]. */
    public double kroyA = 3.0, kroyB = 0.2;
    /** Neigung der Hülle des Windschattens hinter Kämmen: 14° wie im Plattenmodell (Werner 1995: 15°) [belegt]. */
    public double shadowDeg = 14;
    /** Absprung der Körner: senkrechte Geschwindigkeit im Mittel launch·u*, Winkel zum Boden in Grad [G nach Namikas 2003, Nalpanis u. a. 1993]. */
    public double launch = 1.2, launchDeg = 40;
    /** Feinkorn (Schweb) als Anteil der Absprünge und seine Korngröße in m [G]: macht die Kammfahnen über den Dünen. */
    public double finesFraction = 0.03, finesGrain = 0.10e-3;
    /** Böen als Sandfahnen (streamers): Stärke σ von u*, Länge in Windrichtung und Breite quer in m [G nach Baas und Sherman 2005]. */
    public double gustiness = 0.22, streamerLength = 3.0, streamerWidth = 0.45;
    /**
     * Windrippel: Wellenlänge an der Schwelle in m, Zunahme mit u* : u*t − 1 (Andreotti u. a. 2006: linear), Rippelindex
     * λ/H, Zeit bis zur vollen Höhe in s, Anteil des Flusses, der als Kriechen die Rippel trägt, und das Verhältnis
     * u* : u*t, über dem die Rippel eingeebnet werden [G nach Bagnold 1941, Sharp 1963].
     */
    public double rippleLambda0 = 0.075, rippleGrowth = 1.0, rippleIndex = 18, rippleFormTime = 300, rippleReptation = 0.25, rippleFlatBed = 3.5;
    /** Rate, ab der {@link #onDeposit} und {@link #onScour} melden, in m/s (2e-6 m/s ≈ 7 mm/h). */
    public double eventRate = 2e-6;

    public Wind wind;
    public Moisture moisture = (x, z) -> 0;
    public Cover cover = (x, z) -> 0;
    public Ground ground = (x, z) -> 0;
    public Saltation onSaltation;
    public Deposit onDeposit, onScour;

    // ------------------------------------------------------------------ Presets (Punkt 5)
    public static Dune calm() { return beaufort(1); }
    public static Dune breeze() { return beaufort(4); }
    public static Dune gale() { return beaufort(8); }
    public static Dune storm() { return beaufort(10); }

    public static Dune beaufort(int bft) {
        Dune d = new Dune();
        d.windSpeed = BFT_MID[Math.max(0, Math.min(12, bft))];
        return d;
    }

    /**
     * Sylter Dünensand: Mittel- bis Feinsand um 0,3 mm (Dünen und oberer Strand der Westküste 0,25–0,35 mm), Westwind,
     * Wind im Mittel 7 m/s [G nach den Korngrößen der Sylter Strandaufspülungen].
     */
    public static Dune sylt() { Dune d = new Dune(); d.grain = 0.30e-3; d.windFromDeg = 270; d.windSpeed = 7; return d; }

    static final double[] BFT_MID = {0.1, 0.9, 2.5, 4.4, 6.7, 9.4, 12.3, 15.5, 19.0, 22.6, 26.5, 30.6, 35.0};

    // ------------------------------------------------------------------ Physik am Ort

    /** u* aus dem Wind in Höhe z über Sand mit z0: u* = κ·U / ln(z/z0). */
    public double ustarFromWind(double u) { return KARMAN * u / Math.log(refHeight / z0); }

    /** Wind in Bezugshöhe, der zu u* gehört (Umkehrung). */
    public double windFromUstar(double us) { return us / KARMAN * Math.log(refHeight / z0); }

    /** Fluid-Schwelle nach Bagnold (1941) für trockenen, kahlen Sand: u*t = A·√((ρs − ρ)/ρ · g·d). */
    public double thresholdDry() { return bagnoldA * Math.sqrt((rhoSand - rhoAir) / rhoAir * G * grain); }

    /** Faktor der Feuchte nach Belly (1964): 1,8 + 0,6·log10(W in %), nie unter 1. */
    /**
     * Seit Sylt Phase 9a: Nässe, ab der der Sand gebunden ist und gar nicht fliegt (Massenanteil, 6 %): Belly gilt für
     * feuchten Sand bis wenige Prozent; nasser Sand am Strand, nach Regen oder unter Wasser hebt kein Wind ab
     * (McKenna Neuman und Nickling 1989: über 4–6 % kein Transport mehr) [G].
     */
    public double boundMoisture = 0.06;

    /** Faktor der Feuchte für dieses Objekt: Belly, über {@link #boundMoisture} unendlich (gebunden). */
    public double wetFactor(double w) { return w >= boundMoisture - 1e-6 ? Double.POSITIVE_INFINITY : moistureFactor(w); }

    public static double moistureFactor(double w) {
        double pct = w * 100;
        return pct <= 0 ? 1 : Math.max(1, 1.8 + 0.6 * Math.log10(pct));
    }

    /** Faktor des Bewuchses nach Raupach (1992): u*t,veg / u*t = √((1 − mσλ)(1 + mβλ)). */
    public double coverFactor(double lambda) {
        double l = Math.max(0, Math.min(lambda, 0.99 / (raupachM * raupachSigma)));
        return Math.sqrt((1 - raupachM * raupachSigma * l) * (1 + raupachM * raupachBeta * l));
    }

    /** Schwelle am Ort mit Feuchte und Bewuchs (ohne Hang), in m/s. */
    public double thresholdAt(double x, double z) {
        return thresholdDry() * wetFactor(moisture.moisture(x, z)) * coverFactor(cover.lambda(x, z));
    }

    /**
     * Faktor des Hangs nach Dyer (1986): u*t(θ)/u*t = √(cos θ + sin θ / tan φ), θ die Neigung in Windrichtung (bergauf
     * positiv), φ der Schüttwinkel; bergab am Schüttwinkel 0.
     */
    public double slopeFactor(double slope) {
        double th = Math.atan(slope), v = Math.cos(th) + Math.sin(th) / Math.tan(Math.toRadians(reposeDeg));
        return Math.sqrt(Math.max(0, v));
    }

    /** Sättigungslänge in m: ℓ = c·(ρs/ρ)·d (bei 0,3 mm rund 1,4 m). */
    public double saturationLength() { return satFactor * rhoSand / rhoAir * grain; }

    /** Wind in Höhe y über dem Sand bei u*, logarithmisch, in m/s (unter z0 null). */
    public double windAt(double ustar, double y) { return y <= z0 ? 0 : ustar / KARMAN * Math.log(y / z0); }

    /**
     * Böen als Sandfahnen: Faktor auf u* am Ort zur Zeit t, 1 + σ·n mit n aus gestrecktem Rauschen (lang in Windrichtung,
     * schmal quer), das mit dem Wind in 1 m Höhe zieht. Mittel 1.
     */
    public double gust(double x, double z, double t, double ustar) {
        if (gustiness <= 0) return 1;
        double fr = Math.toRadians(windFromAt(x, z)), tx = -Math.sin(fr), tz = Math.cos(fr);
        double s = x * tx + z * tz - windAt(ustar, 1.0) * 0.8 * t, n = -x * tz + z * tx;
        double a = Noise.value(s / streamerLength, n / streamerWidth, seed) * 0.65 + Noise.value(s / (streamerLength * 4), n / (streamerWidth * 6), seed + 7) * 0.35;
        return Math.max(0, 1 + gustiness * (a * 2 - 1) * 1.7);
    }

    public double windSpeedAt(double x, double z) { return wind != null ? wind.speed(x, z) : windSpeed; }
    public double windFromAt(double x, double z) { return wind != null ? wind.fromDeg(x, z) : windFromDeg; }

    /** u* am Ort, in m/s. */
    public double ustarAt(double x, double z) { return ustarFromWind(windSpeedAt(x, z)); }

    /**
     * Massenfluss in kg/(m·s) nach Bagnold mit Schwelle: q = C·√(d/D)·ρ/g·u*²·(u* − u*t) (Werkbuch 5.2; geht weit über
     * der Schwelle in Bagnolds u*³ über), 0 darunter.
     */
    public double flux(double ustar, double threshold) {
        if (ustar <= threshold) return 0;
        return bagnoldC * Math.sqrt(grain / 0.25e-3) * rhoAir / G * ustar * ustar * (ustar - threshold);
    }

    /** Massenfluss am Ort, in kg/(m·s), in Richtung des Winds. */
    public double fluxAt(double x, double z) { return flux(ustarAt(x, z), thresholdAt(x, z)); }

    /** Fluss am Ort zur Zeit t mit Böen (Sandfahnen), kg/(m·s): nahe der Schwelle fliegt Sand nur in den Fahnen. */
    public double fluxGusty(double x, double z, double t) {
        double us = ustarAt(x, z);
        return flux(us * gust(x, z, t, us), thresholdAt(x, z));
    }

    /** Sucht auf einem Raster die Stellen, an denen Sand fliegt, und meldet sie über {@link #onSaltation}. */
    public int scan(double x0, double z0, double size, int n) {
        int found = 0;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                double x = x0 + size * i / n, z = z0 + size * j / n, q = fluxAt(x, z);
                if (q > 0) {
                    found++;
                    if (onSaltation != null) onSaltation.saltation(x, z, q);
                }
            }
        return found;
    }
}

/** Wertrauschen 0…1, glatt, aus Gitterwerten nach Hash (paketeigen). */
final class Noise {
    private Noise() { }

    static double hash(long i, long j, long seed) {
        long h = i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL ^ seed * 0x165667B19E3779F9L;
        h ^= h >>> 31; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29; h *= 0x94D049BB133111EBL; h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }

    static double value(double x, double y, long seed) {
        double fx = Math.floor(x), fy = Math.floor(y);
        long i = (long) fx, j = (long) fy;
        double u = x - fx, v = y - fy;
        u = u * u * (3 - 2 * u);
        v = v * v * (3 - 2 * v);
        double a = hash(i, j, seed), b = hash(i + 1, j, seed), c = hash(i, j + 1, seed), d = hash(i + 1, j + 1, seed);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }
}
