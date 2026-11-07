package com.dan.shutter;

/**
 * Die Fehler des Objektivs in der Kino-Stufe (seit LituyaBay1958 W9c Runde 2): sphärische Aberration und Längsfarbfehler.
 * <p>
 * Ein Teilbild sieht die Szene von einem Ort der Pupille aus; die Projektion ist so geschert, dass die Schärfenebene steht
 * ({@link SubFrames#shearProjection}). Eine andere Schärfenebene für dieses Teilbild ändert die Scherung, und eine Änderung
 * der Scherung verschiebt das ganze Bild um denselben Betrag, für jede Tiefe (x_ndc = −P₀₀ x/z − P₀₂). Das ist der Schlüssel:
 * <ul>
 * <li><b>Längsfarbfehler.</b> Rot und Blau bündeln in dz_R, dz_B hinter (oder vor) Grün. Am Sensor ist ein Strahl der Farbe c
 * durch den Pupillenort mit Halbmesser r_p noch um dz_c · r_p / v neben dem grünen (v die Bildweite): Das Teilbild ist in
 * dieser Farbe um −dz_c · (D/2) / v · (u, v) verschoben (u, v der Ort in der Pupille, |·| ≤ 1). Genau die dünne Linse für jede
 * Tiefe.</li>
 * <li><b>Sphärische Aberration.</b> Echte Strahlen durch die Zone ρ landen in der Bildebene um TA(ρ) neben dem
 * Bildpunkt (TA aus der Strahlrechnung des Objektivs, etwa {@code com.dan.lens}: positiv auf der Seite des Pupillenorts).
 * Das Teilbild ist darum um −TA(ρ) in Richtung (u, v) verschoben. Scharfgestellt wird auf den kleinsten Fleck, nicht paraxial:
 * TA_best(ρ) = TA(ρ) − α ρ mit dem α, das den mittleren Fleck ∫ (TA − αρ)² 2ρ dρ klein macht (α = 4 ∫ TA ρ² dρ).</li>
 * </ul>
 * Zusammen kommen der Ring oder die helle Mitte im Unschärfekreis (vor und hinter der Schärfe verschieden), das Glühen bei
 * offener Blende in der Schärfe und die Farbsäume vor und hinter der Schärfe (purpur und grün) aus der Pupille selbst, ohne
 * Tiefe: auch für Gischt, Nebel und Spiegelbilder. Grenzen: TA gilt für einen Punkt auf der Achse in Unendlich (die Aberration
 * im Feld, Koma und Astigmatismus, fehlen; die Abhängigkeit vom Abstand ist bei d ≫ f klein); der Farbfehler der sphärischen
 * Aberration (Gauß-Fehler) fehlt.
 */
public final class Aberration {
    /** TA(ρ) in Grün (mm) an ρ = i / (n − 1), i = 0 … n − 1 (TA(0) = 0). */
    public final double[] transverseMm;
    /** Längsfarbfehler: Schnittweite Rot und Blau minus Grün (mm). */
    public final double dzR, dzB;
    /** Eintrittspupille (Durchmesser, mm) und Bildweite (mm). */
    public final double pupilMm, imageMm;
    /** Auf den kleinsten Fleck scharfgestellt (true, wie ein Objektiv eingestellt wird) oder paraxial. */
    public boolean bestFocus = true;
    /** Schalter: sphärische Aberration, Längsfarbfehler. */
    public boolean spherical = true, chromatic = true;

    public Aberration(double[] transverseMm, double dzR, double dzB, double pupilMm, double imageMm) {
        this.transverseMm = transverseMm == null ? new double[]{0, 0} : transverseMm.clone();
        this.dzR = dzR;
        this.dzB = dzB;
        this.pupilMm = pupilMm;
        this.imageMm = imageMm;
    }

    /** TA in Grün an ρ (0 … 1, linear zwischen den Stützstellen; darüber gehalten). */
    public double ta(double rho) {
        double[] t = transverseMm;
        int n = t.length;
        double x = Math.max(0, Math.min(1, rho)) * (n - 1);
        int i = Math.min(n - 2, (int) Math.floor(x));
        double f = x - i;
        return t[i] + (t[i + 1] - t[i]) * f;
    }

    /** α des kleinsten Flecks (mm je Einheit ρ): 4 ∫₀¹ TA(ρ) ρ² dρ. */
    public double bestAlpha() {
        int n = 4000;
        double s = 0;
        for (int k = 0; k < n; k++) {
            double r = (k + 0.5) / n;
            s += ta(r) * r * r / n;
        }
        return 4 * s;
    }

    /** RMS des Flecks in der Bildebene (mm): ∫ (TA − αρ)² 2ρ dρ, α = 0 (paraxial) oder bestAlpha. */
    public double rmsSpotMm(boolean best) {
        double a = best ? bestAlpha() : 0;
        int n = 4000;
        double s = 0;
        for (int k = 0; k < n; k++) {
            double r = (k + 0.5) / n, e = ta(r) - a * r;
            s += e * e * 2 * r / n;
        }
        return Math.sqrt(s);
    }

    private double alpha = Double.NaN;

    /**
     * Versatz des Teilbilds am Ort (u, v) der Pupille (Fläche π, v mit der Stauchung squeeze) in der Farbe ch (0 Rot, 1 Grün,
     * 2 Blau): mm im aufrechten Bild (x rechts, y oben), in out[0], out[1].
     */
    public void shiftMm(double u, double v, double squeeze, int ch, double[] out) {
        double q = Math.max(1e-6, squeeze);
        double rho = Math.hypot(u, v / q);
        double k = 0;
        if (spherical && rho > 1e-9) {
            if (Double.isNaN(alpha)) alpha = bestAlpha();
            double t = ta(rho) - (bestFocus ? alpha * rho : 0);
            k += t / rho;
        }
        if (chromatic && ch != 1) k += (ch == 0 ? dzR : dzB) * 0.5 * pupilMm / Math.max(1e-6, imageMm);
        out[0] = -k * u;
        out[1] = -k * v;
    }

    /**
     * Für die Lichter der Kino-Stufe ({@code splat.vert}): TA_best (ohne den Längsfarbfehler) in Bildpunkten zu pitchMm an
     * ρ = k/16 · rhoTop, k = 0 … 16 (rhoTop: Umkreis der Blende im Maß der Pupille, ≥ 1 bei Lamellen). Mit dem Vorzeichen
     * so, dass der Ort p bei X(ρ)/ρ · p landet, X = S ρ − dieser Wert.
     */
    public double[] taBestPx(double pitchMm, double rhoTop) {
        double[] o = new double[17];
        if (!spherical) return o;
        if (Double.isNaN(alpha)) alpha = bestAlpha();
        for (int k = 0; k <= 16; k++) {
            double r = k / 16.0 * rhoTop;
            o[k] = (ta(r) - (bestFocus ? alpha * r : 0)) / pitchMm;
        }
        return o;
    }

    /** Längsfarbfehler der Farbe ch als Bildpunkte je Einheit der Pupille (S_c = S − dieser Wert); Grün 0. */
    public double chromaticPx(int ch, double pitchMm) {
        if (!chromatic || ch == 1) return 0;
        return (ch == 0 ? dzR : dzB) * 0.5 * pupilMm / Math.max(1e-6, imageMm) / pitchMm;
    }

    /** Wie {@link #shiftMm}, alle drei Farben in uv eines Bilds aus width × height Bildpunkten zu pitchMm: rx, ry, gx, gy, bx, by. */
    public double[] shiftUv(double u, double v, double squeeze, double pitchMm, int width, int height) {
        double[] o = new double[6], t = new double[2];
        for (int c = 0; c < 3; c++) {
            shiftMm(u, v, squeeze, c, t);
            o[2 * c] = t[0] / (pitchMm * width);
            o[2 * c + 1] = t[1] / (pitchMm * height);
        }
        return o;
    }

    /**
     * Für Prüfungen: wo landet ein Punkt auf der Achse im Abstand d (m) bei Schärfe s (m), Brennweite f (mm), für den Ort (u, v)
     * der Pupille in Farbe ch? mm im aufrechten Bild relativ zum Bild des Mittelstrahls: der Versatz der Scherung (dünne Linse)
     * plus {@link #shiftMm}.
     */
    public double[] landingMm(double dM, double sM, double fMm, double u, double v, double squeeze, int ch) {
        double e = 0.5 * pupilMm;   // mm
        // Scherung: x_ndc + P00 · e · (1/s − 1/d) → mm: f · e · (1/s − 1/d) (e, s, d in denselben Einheiten)
        double k = fMm * (1 / (sM * 1000) - 1 / (dM * 1000));
        double[] t = new double[2];
        shiftMm(u, v, squeeze, ch, t);
        return new double[]{k * e * u + t[0], k * e * v + t[1]};
    }
}
