package com.dan.cliff;

/**
 * Rauschen für Fels, Zeile für Zeile wie {@code gl/cliff.glsl}: ein ganzzahliger Hash (pcg3d nach Jarzynski und
 * Olano 2020) ohne Vorzeichen, darauf Wertrauschen in 3D mit quintischer Glättung. Java und GLSL rechnen mit denselben
 * 32-Bit-Ganzzahlen, darum liefern Prozessor und Grafikkarte dieselben Gitterwerte; Unterschiede kommen nur aus der
 * Gleitkommarechnung (float auf der Grafikkarte). Seit der Nacharbeit zu Phase 5 liegen die Gitterwerte als 3D-Textur
 * 64³ auf der Grafikkarte: ein Abruf je Rauschwert (die Filterung der Grafikkarte rundet die Gewichte, darum weicht
 * sie um Bruchteile eines Prozents ab).
 * <p>
 * Alles ist 3D: der Fels wird nicht auf Ebenen projiziert, darum ziehen senkrechte Wände keine Streifen.
 */
public final class CNoise {
    private CNoise() { }

    /** pcg3d: drei 32-Bit-Werte (ohne Vorzeichen) zu einem. */
    public static int hash(int x, int y, int z) {
        x = x * 1664525 + 1013904223;
        y = y * 1664525 + 1013904223;
        z = z * 1664525 + 1013904223;
        x += y * z; y += z * x; z += x * y;
        x ^= x >>> 16; y ^= y >>> 16; z ^= z >>> 16;
        x += y * z; y += z * x; z += x * y;
        return x ^ y ^ z;
    }

    /** Hash als Zahl 0..1. */
    public static double unit(int h) { return (h >>> 8) * (1.0 / 16777216.0); }

    static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }

    /** Periode des Gitters (Zellen je Achse): so groß wie die 3D-Textur auf der Grafikkarte. */
    public static final int PERIOD = 64;

    /** Gitterwert 0..1 an der Zelle (i, j, k) modulo {@link #PERIOD}, in 16 Bit wie die Textur (R16). */
    public static double lattice(int i, int j, int k) {
        return (hash(i & (PERIOD - 1), j & (PERIOD - 1), k & (PERIOD - 1)) >>> 16) / 65535.0;
    }

    /** Die Gitterwerte als Textur: PERIOD³ Werte, x läuft am schnellsten (16 Bit ohne Vorzeichen). */
    public static short[] latticeTexture() {
        short[] t = new short[PERIOD * PERIOD * PERIOD];
        for (int k = 0; k < PERIOD; k++)
            for (int j = 0; j < PERIOD; j++)
                for (int i = 0; i < PERIOD; i++) t[(k * PERIOD + j) * PERIOD + i] = (short) (hash(i, j, k) >>> 16);
        return t;
    }

    /**
     * Wertrauschen 0..1 an (x, y, z), Gitterabstand 1, Startwert seed. Das Gitter wiederholt sich nach {@link #PERIOD}
     * Zellen; der Startwert verschiebt es um ganze Zellen. Die Grafikkarte holt denselben Wert mit einem Abruf aus der
     * 3D-Textur (trilinear an den geglätteten Stellen), statt acht Hashes zu rechnen.
     */
    public static double value(double x, double y, double z, int seed) {
        x += (seed * 37) & (PERIOD - 1);
        y += (seed * 59) & (PERIOD - 1);
        z += (seed * 1013) & (PERIOD - 1);
        double fx = Math.floor(x), fy = Math.floor(y), fz = Math.floor(z);
        int ix = (int) fx, iy = (int) fy, iz = (int) fz;
        double tx = fade(x - fx), ty = fade(y - fy), tz = fade(z - fz);
        double c000 = lattice(ix, iy, iz), c100 = lattice(ix + 1, iy, iz), c010 = lattice(ix, iy + 1, iz), c110 = lattice(ix + 1, iy + 1, iz);
        double c001 = lattice(ix, iy, iz + 1), c101 = lattice(ix + 1, iy, iz + 1), c011 = lattice(ix, iy + 1, iz + 1), c111 = lattice(ix + 1, iy + 1, iz + 1);
        double a = lerp(lerp(c000, c100, tx), lerp(c010, c110, tx), ty);
        double b = lerp(lerp(c001, c101, tx), lerp(c011, c111, tx), ty);
        return lerp(a, b, tz);
    }

    /** Wertrauschen in 1D (für Bänder entlang der Schieferung). */
    public static double value1(double x, int seed) {
        double fx = Math.floor(x);
        int ix = (int) fx;
        double t = fade(x - fx);
        return lerp(unit(hash(ix, seed, 7)), unit(hash(ix + 1, seed, 7)), t);
    }

    static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    /** Gewicht eines Rauschanteils mit Wellenlänge lambda bei footprint m je Bildpunkt: 1 fein genug, 0 flimmert. */
    public static double band(double lambda, double footprint) {
        return 1 - Cliff.smooth(0.5 * lambda, 1.5 * lambda, 2 * footprint);
    }
}
