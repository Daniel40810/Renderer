package com.dan.buoyancy;

/**
 * Rumpf eines Segelboots (seit Timmendorf Phase 7, Werkbuch Abschnitt 6): Jolle mit Schwert, Kielboot oder Katamaran
 * als Streifenmodell wie {@link Floater}, aus Länge der Wasserlinie, Breite, Masse und Anfangsstabilität. Die
 * Wasserlinie ist eine Spindel (Breite ∝ √(1 − (2s/L)²)), der Tiefgang folgt aus der Masse; Tauchen und Stampfen kommen
 * aus den Säulen, das Rollen geschlossen aus der metazentrischen Höhe GM (W · GM · sin φ; beim Kat höchstens W · B/2,
 * dann fliegt ein Rumpf). Das krängende Moment des Segels kommt über
 * {@link #externalRoll}; so krängt der Rumpf im Seegang wie das Segel es verlangt.
 * <p>
 * Werte aus {@code sail} (Takelage) reicht die App herüber; das Package kennt {@code sail} nicht.
 */
public final class SailHull extends Floater {
    public enum Form { DINGHY, KEELBOAT, CATAMARAN }

    public final Form form;
    public final double lwl, beam;

    /**
     * @param lwl  Länge der Wasserlinie (m)
     * @param beam Breite der Wasserlinie (beim Kat: Abstand der Rumpfmitten) (m)
     * @param mass Masse mit Crew (kg)
     * @param gm   metazentrische Höhe (m)
     */
    public SailHull(String name, Form form, double lwl, double beam, double mass, double gm) {
        super(name, columns(form, lwl, beam, mass), 0, 0.28 * lwl, form == Form.CATAMARAN ? 0.45 * beam : 0.38 * beam);
        this.form = form;
        this.lwl = lwl;
        this.beam = beam;
        maxRoll = Math.toRadians(75);
        zetaRoll = form == Form.CATAMARAN ? 0.25 : 0.15;
        rollDrag = 2;
        setGm(gm);
    }

    /**
     * Säulen der Wasserlinie: 9 längs auf der Mittellinie (beim Kat je Rumpf eine Reihe), Tiefgang aus der Masse. Sie
     * tragen Tauchen und Stampfen; das Rollen rechnet {@link #integrate} geschlossen (Säulen eines flachen Jollenrumpfs
     * tauchten schon bei 10° auf und das Boot fiele um).
     */
    static double[][] columns(Form f, double lwl, double beam, double mass) {
        int ns = 9;
        java.util.List<double[]> c = new java.util.ArrayList<>();
        double ds = lwl / ns, area = 0, b = f == Form.CATAMARAN ? 0.3 : beam * 0.85;
        for (int i = 0; i < ns; i++) {
            double s = -lwl / 2 + (i + 0.5) * ds, w = Math.sqrt(Math.max(0.05, 1 - Math.pow(2 * s / lwl, 2)));
            int hulls = f == Form.CATAMARAN ? 2 : 1;
            for (int k = 0; k < hulls; k++) {
                double a = ds * b * w;
                c.add(new double[]{s, 0, a, 0, 0});
                area += a;
            }
        }
        double draft = mass / (RHO * area);
        double[][] out = c.toArray(new double[0][]);
        for (double[] r : out) { r[3] = -draft; r[4] = Math.max(0.3, draft * 1.5); }
        return out;
    }

    private double gmTarget;

    private void setGm(double gm) { gmTarget = gm; }

    /**
     * Rollen geschlossen: aufrichtendes Moment W · GM · sin φ (beim Kat höchstens W · B/2 · cos φ, dann fliegt ein Rumpf),
     * das krängende Moment {@link #externalRoll}, Dämpfung ζ der kritischen; Tauchen und Stampfen aus den Säulen.
     */
    @Override
    public void integrate(double dt) {
        double keep = externalRoll, w = mass() * G;
        int n = Math.max(1, (int) Math.ceil(dt / (1 / 120.0)));
        double h = dt / n, ir = mass() * Math.pow(form == Form.CATAMARAN ? 0.45 * beam : 0.38 * beam, 2) * (1 + addedInertia);
        double k = w * gmTarget, c = 2 * zetaRoll * Math.sqrt(k * ir);
        double r0 = roll, v0 = vRoll;
        // Säulen nur für Tauchen und Stampfen (Rollen hier)
        externalRoll = 0;
        super.integrate(dt);
        roll = r0;
        vRoll = v0;
        for (int i = 0; i < n; i++) {
            double rm = w * gmTarget * Math.sin(roll);
            if (form == Form.CATAMARAN) rm = Math.signum(roll) * Math.min(Math.abs(rm), w * beam / 2 * Math.cos(roll));
            double acc = (keep - rm - c * vRoll - rollDrag * ir * vRoll * Math.abs(vRoll) * 0.1) / ir;
            vRoll += acc * h;
            roll += vRoll * h;
        }
        roll = Math.max(-maxRoll, Math.min(maxRoll, roll));
        externalRoll = keep;
    }

    /** Die metazentrische Höhe, mit der der Rumpf rollt (m). */
    public double effectiveGm() { return gmTarget; }

    /** Rolllage im Gleichgewicht unter einem krängenden Moment M (Grad), Handrechnung: sin φ = M / (W · GM). */
    public double staticHeel(double moment) {
        return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, moment / (mass() * G * effectiveGm())))));
    }
}
