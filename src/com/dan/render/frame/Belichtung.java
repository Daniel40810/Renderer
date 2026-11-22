package com.dan.render.frame;

/**
 * Die Belichtung (Werkbuch HalligHooge Teil A, 8.1 und 12.7): Augenanpassung aus einem Histogramm der Leuchtdichte des
 * HDR-Bilds, nach dem Verfahren von {@code exposure.EyeAdaptation} (Mittel von log₂ L zwischen den Perzentilen 40 und
 * 92, Zeit exponentiell, ins Dunkle 3 s, ins Helle 0,8 s [G]), aber absolut: Das HDR-Bild trägt L · Vorbelichtung, die
 * Belichtung E bringt das Mittel der Szene auf den Bezug, und E ist die Vorbelichtung des nächsten Bilds.
 * So bleibt auch die Sonne (1,6 · 10⁹ cd/m²) oder eine Nacht im Halbfloat.
 * <ul>
 * <li><b>Gemessen wird auf Gelände und Wasser</b> (F49 A, 12.7 Nachlauf P6): Das Histogramm kommt doppelt, einmal über
 * die Bildpunkte mit Tiefe (nicht der Himmel) und einmal über alle. Tragen die Bildpunkte mit Tiefe weniger als
 * {@link #anteilBoden} der gezählten, gilt das ganze Bild wie vorher ({@link #waehle}).</li>
 * <li><b>Sehen bei Nacht</b> (F50 A) nach Krawczyk, Myszkowski und Seidel (2005): Der Schlüssel
 * α(Ȳ) = 1,03 − 2 / (2 + log₁₀(Ȳ + 1)) bestimmt bei Krawczyk die Helligkeit des Mittels <i>auf dem Bildschirm</i>,
 * α / (1 + α). Hier gilt dasselbe Verhältnis zum Tag ({@link #bezugY}) für das Mittel nach der Filmkurve
 * ({@link #bild}); der Bezug ist der Wert vor der Kurve, der es trifft ({@link #bezug(double)}). Dazu das Sehen mit den
 * Stäbchen je Bildpunkt in der Ausgabe ({@link #staebchen}).</li>
 * </ul>
 */
public final class Belichtung {
    /** Bereich des Histogramms (log₂ des vorbelichteten Werts) und Zahl der Fächer, wie {@code histogramm.comp}. */
    public static final double LOG_MIN = -14, LOG_MAX = 4;
    public static final int FAECHER = 64;
    /**
     * Der Puffer des Histogramms ({@code histogramm.comp}): Fächer über die Bildpunkte mit Tiefe, Fächer über alle, dann
     * die Zahl der gezählten Bildpunkte mit Tiefe und aller (ohne Gewicht).
     */
    public static final int PUFFER = 2 * FAECHER + 2;
    /** Stäbchen nach Krawczyk u. a. (2005), Gl. 7 und 15: σ(Y) = K / (K + Y) mit K in cd/m², Farbe der Nacht. */
    public static final double STAEBCHEN_K = 0.04;
    public static final double[] NACHTFARBE = {1.05, 0.97, 1.27};
    /** Bezug bei {@link #bezugY}, Perzentile, Zeitkonstanten (s), Grenzen von log₂ E [G]. */
    public double bezug = 0.12, unten = 40, oben = 92, tauDunkel = 3, tauHell = 0.8, evMin = -40, evMax = 20;
    /** Leuchtdichte der Szene (cd/m²), bei der der Bezug {@link #bezug} gilt (F50 A) [G]. */
    public double bezugY = 1000;
    /** Sehen bei Nacht (F50 A): Bezug nach Krawczyk und Stäbchen in der Ausgabe; aus: Bezug fest wie eine Kamera. */
    public boolean nachtsehen = true;
    /** Messen auf Gelände und Wasser (F49 A); aus: über das ganze Bild. */
    public boolean nurBoden = true;
    /**
     * Die Kurve auf den Bildschirm für ein Grau (linear vor der Kurve → linear auf dem Bildschirm), steigend; der
     * {@link Rahmen} setzt die Filmkurve. Ohne Kurve gilt der Bildschirm gleich der Szene.
     */
    public java.util.function.DoubleUnaryOperator bild = x -> x;
    /** Mindestanteil der Bildpunkte mit Tiefe an den gezählten, damit nur sie gelten (F49 A) [G]. */
    public double anteilBoden = 0.10;
    /** Korrektur in Blenden obenauf (von der App), 0 ohne. */
    public double blenden = 0;

    private double ev = 0, ziel = 0, mittel = Double.NaN, letzterAnteil = Double.NaN;
    private boolean sofort = true, amBoden;

    /** Die Belichtung E (linear); zugleich die Vorbelichtung des nächsten Bilds. */
    public double faktor() { return Math.pow(2, ev + blenden); }
    public double ev() { return ev; }
    public double ziel() { return ziel; }
    /** Mittlere Leuchtdichte der Szene (cd/m²) der letzten Messung. */
    public double mittel() { return mittel; }
    /** Galt bei der letzten Wahl nur der Boden (F49 A)? */
    public boolean amBoden() { return amBoden; }
    /** Anteil der Bildpunkte mit Tiefe an den gezählten bei der letzten Wahl. */
    public double anteil() { return letzterAnteil; }
    /** Die nächste Messung gilt sofort (Schnitt, Standbild, Prüfung). */
    public void sofort() { sofort = true; }

    /** Schlüssel nach Krawczyk u. a. (2005), Gl. 11: α(Ȳ) = 1,03 − 2 / (2 + log₁₀(Ȳ + 1)), Ȳ in cd/m². */
    public static double schluessel(double y) { return 1.03 - 2 / (2 + Math.log10(y + 1)); }

    /** Helligkeit des Mittels auf dem Bildschirm nach Krawczyk u. a. (2005): α / (1 + α), Reinhards L / (1 + L). */
    public static double imBild(double y) { double a = schluessel(y); return a / (1 + a); }

    /**
     * Der Bezug für eine Szene mit der mittleren Leuchtdichte {@code y} (cd/m²): fest, oder (F50 A) der Wert vor der
     * Kurve, dessen Bild sich zum Bild von {@link #bezug} verhält wie imBild(y) zu imBild({@link #bezugY}).
     */
    public double bezug(double y) {
        if (!nachtsehen || y == bezugY) return bezug;
        double ziel = bild.applyAsDouble(bezug) * imBild(y) / imBild(bezugY), lo = bezug * 1e-6, hi = bezug * 1e6;
        for (int i = 0; i < 80; i++) {
            double m = Math.sqrt(lo * hi);
            if (bild.applyAsDouble(m) < ziel) lo = m; else hi = m;
        }
        return Math.sqrt(lo * hi);
    }

    /** Anteil der Stäbchen σ(Y) = K / (K + Y) bei der Leuchtdichte Y (cd/m²), Krawczyk u. a. (2005), Gl. 7. */
    public static double sigma(double y) { return STAEBCHEN_K / (STAEBCHEN_K + Math.max(0, y)); }

    /**
     * Das Sehen mit den Stäbchen wie {@code ausgabe.frag}: {@code rgb} (beliebig skaliert, linear) geht um σ(Y) in ein
     * Grau gleicher Leuchtdichte mit der Farbe der Nacht über; {@code y} ist die Leuchtdichte des Bildpunkts in cd/m².
     * Vor der Filmkurve, im linearen Licht [G].
     */
    public static double[] staebchen(double r, double g, double b, double y) {
        double s = sigma(y), l = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return new double[] {r * (1 - s) + NACHTFARBE[0] * l * s, g * (1 - s) + NACHTFARBE[1] * l * s, b * (1 - s) + NACHTFARBE[2] * l * s};
    }

    /**
     * Wählt aus dem Puffer des Histogramms ({@link #PUFFER} Werte) die Fächer, die gelten (F49 A): die über die
     * Bildpunkte mit Tiefe, wenn {@link #nurBoden} und diese mindestens {@link #anteilBoden} der gezählten tragen, sonst
     * die über alle.
     */
    public long[] waehle(long[] puffer) {
        long nBoden = puffer[2 * FAECHER], nAlle = puffer[2 * FAECHER + 1];
        letzterAnteil = nAlle > 0 ? (double) nBoden / nAlle : Double.NaN;
        amBoden = nurBoden && nBoden > 0 && nBoden >= anteilBoden * nAlle;
        long[] f = new long[FAECHER];
        System.arraycopy(puffer, amBoden ? 0 : FAECHER, f, 0, FAECHER);
        return f;
    }

    /** Mittel (linear, vorbelichtet) aus einem Histogramm zwischen den Perzentilen; NaN ohne Zählung. */
    public double mittelAus(long[] faecher) {
        long n = 0;
        for (long c : faecher) n += c;
        if (n <= 0) return Double.NaN;
        double lo = n * unten / 100.0, hi = n * oben / 100.0, d = (LOG_MAX - LOG_MIN) / faecher.length, acc = 0, w = 0, lauf = 0;
        for (int i = 0; i < faecher.length; i++) {
            double a = lauf, b = lauf + faecher[i];
            lauf = b;
            double nimm = Math.max(0, Math.min(b, hi) - Math.max(a, lo));
            if (nimm <= 0) continue;
            acc += nimm * (LOG_MIN + (i + 0.5) * d);
            w += nimm;
        }
        return w > 0 ? Math.pow(2, acc / w) : Double.NaN;
    }

    /**
     * Eine Messung: die gewählten Fächer eines Bilds ({@link #waehle}), das mit {@code vorbelichtung} gezeichnet war
     * (null: keine neue Messung), und dt Sekunden seit dem letzten Aufruf.
     */
    public void messung(long[] faecher, double vorbelichtung, double dt) {
        if (faecher != null) {
            double m = mittelAus(faecher);
            if (!Double.isNaN(m)) {
                mittel = m / vorbelichtung;
                ziel = Math.max(evMin, Math.min(evMax, Math.log(bezug(mittel) / mittel) / Math.log(2)));
                if (sofort) {
                    ev = ziel;
                    sofort = false;
                    return;
                }
            }
        }
        schritt(dt);
    }

    /** Nur die Zeit: ev läuft exponentiell auf das Ziel zu. */
    public void schritt(double dt) {
        if (!(dt > 0)) return;
        double tau = ziel > ev ? tauDunkel : tauHell;
        ev = ziel + (ev - ziel) * Math.exp(-dt / Math.max(1e-3, tau));
    }

    /** Setzt die Belichtung fest (Prüfung, Fotomodus). */
    public void setze(double ev) {
        this.ev = ziel = ev;
        sofort = false;
    }

    @Override public String toString() {
        return String.format(java.util.Locale.GERMAN, "Belichtung %+.2f Blenden (Ziel %+.2f, Mittel der Szene %.3g cd/m², %s)", ev + blenden, ziel, mittel,
                amBoden ? "gemessen auf Gelände und Wasser" : "gemessen über das ganze Bild");
    }
}
