package com.dan.gertsch;

/**
 * Ein Gelände unter dem Dickicht (seit LituyaBay1958 W10): Höhen auf einem Gitter, bilinear zwischen den Punkten, am
 * Rand gehalten. Ohne Gelände ist der Boden die Ebene y = 0 wie in den Phasen 1 bis 5. Auf der Grafikkarte liest
 * {@code boden.vert} dieselben Höhen (Textur R32F, {@code texelFetch}), der Boden wird dort ein Gitter mit derselben
 * Auflösung; {@link Malweise} schneidet den Strahl mit {@link #strahl}.
 * <p>
 * Punkt (i, j) liegt bei x = x0 + i · zelle, z = z0 + j · zelle; Zeilen nach z ({@code h[j · nx + i]}). Meter, y nach oben.
 */
public final class Gelaende {
    public final double x0, z0, zelle;
    public final int nx, nz;
    public final float[] h;
    private final float min, max;
    /**
     * Wasserspiegel (seit LituyaBay1958 W10b, m; NaN = kein Wasser): wo der Grund tiefer liegt, ist der Boden die
     * Wasserfläche in dieser Höhe. Sie spiegelt den Himmel nach Fresnel (unter Bäumen nach der Blattfläche darüber) und
     * zeigt darunter {@link #wasserFarbe}; Bäume spiegelt sie nicht.
     */
    public float wasser = Float.NaN;
    /** Albedo des Wassers mit dem, was darunter liegt (linear) [G: Gletschermilch dunkel graugrün]. */
    public float[] wasserFarbe = {0.018f, 0.030f, 0.030f};

    /** Mit Wasserspiegel in dieser Höhe (gibt sich selbst zurück). */
    public Gelaende mitWasser(float hoehe) { wasser = hoehe; return this; }

    /** Steht bei (x, z) Wasser über dem Grund? */
    public boolean nass(double x, double z) { return !Float.isNaN(wasser) && hoehe(x, z) < wasser; }

    /** Höhe der Oberfläche: der Grund oder darüber das Wasser. */
    public float oberflaeche(double x, double z) { float g = hoehe(x, z); return !Float.isNaN(wasser) && g < wasser ? wasser : g; }

    /**
     * Anteil des Himmelslichts, den das Wasser zum Auge spiegelt: Fresnel nach Schlick (n = 1,33, R₀ = 0,02) aus dem
     * Kosinus zwischen Blick und Senkrechter. Gleich in {@code boden.frag}.
     */
    public static double fresnel(double cosBlick) {
        double c = Math.max(0, Math.min(1, cosBlick)), m = 1 - c;
        return 0.02 + 0.98 * m * m * m * m * m;
    }

    public Gelaende(double x0, double z0, double zelle, int nx, int nz, float[] h) {
        if (nx < 2 || nz < 2) throw new IllegalArgumentException("gertsch: Gelände braucht mindestens 2 × 2 Punkte");
        if (h.length != nx * nz) throw new IllegalArgumentException("gertsch: Gelände mit " + h.length + " Höhen statt " + nx * nz);
        this.x0 = x0; this.z0 = z0; this.zelle = zelle; this.nx = nx; this.nz = nz; this.h = h;
        float a = Float.MAX_VALUE, b = -Float.MAX_VALUE;
        for (float v : h) { a = Math.min(a, v); b = Math.max(b, v); }
        min = a;
        max = b;
    }

    /** Ein Gelände aus einer Höhenfunktion über das Rechteck (x0, z0) … (x1, z1) mit Punkten im Abstand zelle. */
    public static Gelaende aus(double x0, double z0, double x1, double z1, double zelle, java.util.function.DoubleBinaryOperator hoehe) {
        int nx = Math.max(2, (int) Math.ceil((x1 - x0) / zelle) + 1), nz = Math.max(2, (int) Math.ceil((z1 - z0) / zelle) + 1);
        float[] h = new float[nx * nz];
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) h[j * nx + i] = (float) hoehe.applyAsDouble(x0 + i * zelle, z0 + j * zelle);
        return new Gelaende(x0, z0, zelle, nx, nz, h);
    }

    public float min() { return min; }

    public float max() { return max; }

    /** Höhe bei (x, z), bilinear, am Rand gehalten (in float wie der Shader). */
    public float hoehe(double x, double z) {
        float fx = (float) ((x - x0) / zelle), fz = (float) ((z - z0) / zelle);
        fx = Math.max(0, Math.min(nx - 1, fx));
        fz = Math.max(0, Math.min(nz - 1, fz));
        int i = Math.min(nx - 2, (int) fx), j = Math.min(nz - 2, (int) fz);
        float tx = fx - i, tz = fz - j;
        float a = h[j * nx + i], b = h[j * nx + i + 1], c = h[(j + 1) * nx + i], d = h[(j + 1) * nx + i + 1];
        float ab = a + (b - a) * tx, cd = c + (d - c) * tx;
        return ab + (cd - ab) * tz;
    }

    /** Normale bei (x, z) aus zentralen Differenzen über eine Zelle (Einheitsvektor, nach oben) in out. */
    public double[] normale(double x, double z, double[] out) {
        double e = zelle;
        double dx = (hoehe(x + e, z) - hoehe(x - e, z)) / (2 * e), dz = (hoehe(x, z + e) - hoehe(x, z - e)) / (2 * e);
        double l = Math.sqrt(dx * dx + 1 + dz * dz);
        out[0] = -dx / l; out[1] = 1 / l; out[2] = -dz / l;
        return out;
    }

    /**
     * Schnitt des Strahls auge + t · r (t ≥ 0) mit dem Gelände über seinem Rechteck: Schritte von einer halben Zelle, dann
     * Bisektion; NaN, wenn der Strahl das Gelände nicht trifft. Höchstens bis t = weit.
     */
    public double strahl(double[] auge, double[] r, double weit) {
        double schritt = 0.5 * zelle, t = 0;
        // erst bis an die Höhe des Geländes heran (von oben kommend), dann in Schritten
        if (auge[1] > max && r[1] < 0) t = Math.max(0, (auge[1] - max) / -r[1] - schritt);
        double vorher = auge[1] + r[1] * t - hoehe(auge[0] + r[0] * t, auge[2] + r[2] * t);
        if (vorher < 0) return t;   // das Auge steht unter dem Gelände
        double xe = x0 + (nx - 1) * zelle, ze = z0 + (nz - 1) * zelle;
        while (t < weit) {
            double t1 = t + schritt, x = auge[0] + r[0] * t1, z = auge[2] + r[2] * t1, y = auge[1] + r[1] * t1;
            if ((r[1] >= 0 && y > max) || ((x < x0 || x > xe || z < z0 || z > ze) && !innen(auge[0] + r[0] * t, auge[2] + r[2] * t, x0, z0, xe, ze)
                    && weg(x, z, r, x0, z0, xe, ze))) return Double.NaN;
            double d = y - hoehe(x, z);
            if (d <= 0) {
                double a = t, b = t1;
                for (int k = 0; k < 24; k++) {
                    double m = 0.5 * (a + b);
                    if (auge[1] + r[1] * m - hoehe(auge[0] + r[0] * m, auge[2] + r[2] * m) > 0) a = m; else b = m;
                }
                return 0.5 * (a + b);
            }
            t = t1;
            vorher = d;
        }
        return Double.NaN;
    }

    /** Wie {@link #strahl}, aber gegen die Oberfläche: das Wasser, wenn der Strahl es vor dem Grund trifft (seit W10b). */
    public double strahlOberflaeche(double[] auge, double[] r, double weit) {
        double t = strahl(auge, r, weit);
        if (Float.isNaN(wasser) || auge[1] <= wasser || r[1] >= 0) return t;
        double tw = (auge[1] - wasser) / -r[1];
        if (tw > weit) return t;
        double x = auge[0] + r[0] * tw, z = auge[2] + r[2] * tw;
        boolean drin = x >= x0 && x <= x0 + (nx - 1) * zelle && z >= z0 && z <= z0 + (nz - 1) * zelle;
        return drin && (Double.isNaN(t) || tw < t) ? tw : t;
    }

    private static boolean innen(double x, double z, double x0, double z0, double x1, double z1) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }

    /** Läuft der Strahl vom Punkt (x, z) aus weiter vom Rechteck weg (draußen und nicht darauf zu)? */
    private static boolean weg(double x, double z, double[] r, double x0, double z0, double x1, double z1) {
        return (x < x0 && r[0] <= 0) || (x > x1 && r[0] >= 0) || (z < z0 && r[2] <= 0) || (z > z1 && r[2] >= 0);
    }
}
