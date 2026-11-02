package com.dan.forest;

import java.util.Arrays;

/**
 * Die Kultur eines großen Walds (seit LituyaBay1958 Phase 6): wo jeder Baum steht, aus Dichte, Startwert und Zustand,
 * ohne ihn zu speichern. Ein Gitter mit {@link #spacing} teilt die Welt in Plätze (i, j); jeder Platz hat höchstens einen
 * Baum, ausgewürfelt aus einem ganzzahligen Hash (PCG) des Platzes und des Startwerts. Auf der Grafikkarte rechnet
 * {@code gl/forestgpu_cull.comp} dieselbe Kultur je Bild für alle Plätze in Reichweite; diese Klasse ist ihr Spiegel auf
 * dem Prozessor (gleiche Hashes, gleiche Rechnung in float), für Prüfung, Statistik und Abfragen der App.
 * <p>
 * Dichte und Höhe kommen als Gitter ({@link Grid}) über ein Rechteck; abgetastet wird bilinear zwischen den Zellmitten,
 * auf der Grafikkarte von Hand mit {@code texelFetch}, damit beide Seiten gleich rechnen. Die Dichte liegt wie auf der
 * Grafikkarte in 8 Bit (0..255).
 * <p>
 * Typ eines Baums: {@code Zustand · Arten · Varianten + Art · Varianten + Variante}; die Formen je Zustand baut
 * {@code gl.ForestGpu} aus {@link Species#damaged}.
 */
public final class ForestCulture {
    /** Ein Rastergitter über das Quadrat (x0, z0) … (x0 + size, z0 + size), n × n Werte je Schicht, Zeilen nach z. */
    public static final class Grid {
        public final double x0, z0, size;
        public final int n, layers;
        public final float[] v;

        public Grid(double x0, double z0, double size, int n, int layers, float[] v) {
            if (v.length != n * n * layers) throw new IllegalArgumentException("Gitter: " + v.length + " Werte statt " + n * n * layers);
            this.x0 = x0; this.z0 = z0; this.size = size; this.n = n; this.layers = layers; this.v = v;
        }

        /** Dichte je Art an den Zellmitten, auf 8 Bit gerundet wie in der Textur der Grafikkarte. */
        public static Grid density(ForestField.Density d, int species, double x0, double z0, double size, int n) {
            float[] v = new float[n * n * species];
            for (int s = 0; s < species; s++)
                for (int j = 0; j < n; j++)
                    for (int i = 0; i < n; i++) {
                        double x = x0 + (i + 0.5) * size / n, z = z0 + (j + 0.5) * size / n;
                        v[(s * n + j) * n + i] = Math.round(Math.max(0, Math.min(1, d.at(x, z, s))) * 255) / 255f;
                    }
            return new Grid(x0, z0, size, n, species, v);
        }

        /** Bodenhöhe an den Zellmitten (float wie R32F). */
        public static Grid heights(Ground g, double x0, double z0, double size, int n) {
            float[] v = new float[n * n];
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) v[j * n + i] = g.height(x0 + (i + 0.5) * size / n, z0 + (j + 0.5) * size / n);
            return new Grid(x0, z0, size, n, 1, v);
        }

        /** Bilinear zwischen den Zellmitten, am Rand gehalten; in float wie der Shader. */
        public float sample(int layer, float x, float z) {
            float fx = (x - (float) x0) / (float) size * n - 0.5f, fz = (z - (float) z0) / (float) size * n - 0.5f;
            float flx = (float) Math.floor(fx), flz = (float) Math.floor(fz);
            float tx = fx - flx, tz = fz - flz;
            int ix = (int) flx, iz = (int) flz;
            int x0i = clamp(ix, n), x1i = clamp(ix + 1, n), z0i = clamp(iz, n), z1i = clamp(iz + 1, n);
            int o = layer * n * n;
            float a = v[o + z0i * n + x0i], b = v[o + z0i * n + x1i], c = v[o + z1i * n + x0i], d = v[o + z1i * n + x1i];
            float ab = a + (b - a) * tx, cd = c + (d - c) * tx;
            return ab + (cd - ab) * tz;
        }

        private static int clamp(int i, int n) { return i < 0 ? 0 : i >= n ? n - 1 : i; }
    }

    /** Ein ausgewürfelter Baum. */
    public static final class Tree {
        public int i, j, species, variant, state, type;
        public float x, y, z, yaw, scale, tint, phase;
    }

    public final int species, variants;
    public final long seed;
    /** Abstand der Plätze (m). */
    public final double spacing;
    /** Wie in {@link ForestField}: feste Drehung je Art (NaN = zufällig) mit Streuung, Größenfaktor. */
    public final double[] yaw, yawSpread, sizeFactor;
    public Grid density;
    /** Bodenhöhe; null = 0. */
    public Grid height;
    /** Zustände; null = alle stehen. */
    public TreeStates states;

    public ForestCulture(int species, int variants, double spacing, long seed) {
        if (species < 1 || species > 16) throw new IllegalArgumentException("1 bis 16 Arten");
        this.species = species;
        this.variants = Math.max(1, variants);
        this.spacing = spacing;
        this.seed = seed;
        yaw = new double[species];
        Arrays.fill(yaw, Double.NaN);
        yawSpread = new double[species];
        sizeFactor = new double[species];
        Arrays.fill(sizeFactor, 1);
    }

    /** Anzahl der Typen (Zustand × Art × Variante, ohne „fort“). */
    public int types() { return 4 * species * variants; }

    public int seed32() { return (int) (seed ^ (seed >>> 32)); }

    // ------------------------------------------------------------------ Hash (gleich in forestgpu_cull.comp)

    /** PCG-Hash (O'Neill), 32 Bit. */
    public static int pcg(int v) {
        int s = v * 747796405 + (int) 2891336453L;
        int w = ((s >>> ((s >>> 28) + 4)) ^ s) * 277803737;
        return (w >>> 22) ^ w;
    }

    public static int slotHash(int i, int j, int seed) { return pcg(i * 1597334677 ^ j * (int) 3812015801L ^ seed); }

    /** Die k-te Zufallszahl 0..1 (24 Bit) eines Platzes. */
    public static float u01(int h, int k) { return (pcg(h + k * (int) 0x9E3779B9L) >>> 8) * 0x1.0p-24f; }

    /** Drehung, mit der ein entlang +x liegendes Modell nach dem Kompasswinkel deg zeigt (wie {@link ForestField#yawToward}). */
    public static float yawToward(float deg) {
        float a = (float) Math.toRadians(deg);
        return (float) Math.atan2(-Math.cos(a), Math.sin(a));
    }

    // ------------------------------------------------------------------ ein Platz

    private final float[] d = new float[16];

    /** Würfelt den Baum des Platzes (i, j) aus; false, wenn dort keiner steht (Dichte, Zufall oder „fort“). */
    public boolean at(int i, int j, Tree out) {
        int h = slotHash(i, j, seed32());
        float sp = (float) spacing;
        float x = (i + 0.1f + 0.8f * u01(h, 1)) * sp, z = (j + 0.1f + 0.8f * u01(h, 2)) * sp;
        float roll = u01(h, 3);
        int st = states == null ? 0 : states.state(i, j);
        if (st == TreeStates.GONE) return false;
        int gr = states == null ? 0 : states.growth(i, j);
        int s;
        if (gr != 0 && (gr & 15) - 1 < species) s = (gr & 15) - 1;    // Nachwuchs (seit Phase 14): Art vorgegeben, immer ein Baum
        else {
            gr = 0;
            float sum = 0;
            for (int k = 0; k < species; k++) {
                float v = density == null ? (k == 0 ? 1 : 0) : density.sample(k, x, z);
                d[k] = v < 0 ? 0 : v > 1 ? 1 : v;
                sum += d[k];
            }
            if (sum <= 1e-6f) return false;
            float m = Math.min(1, sum);
            if (roll >= m) return false;
            float pick = roll / m * sum;
            s = 0;
            while (s < species - 1 && pick > d[s]) { pick -= d[s]; s++; }
        }
        int v = Math.min(variants - 1, (int) (u01(h, 7) * variants));
        float scale = (0.62f + 0.45f * u01(h, 4)) * (float) sizeFactor[s] * (gr != 0 ? TreeStates.growthSize(gr) : 1f);
        float rot = u01(h, 5) * 6.2831853f;
        float yw = Double.isNaN(yaw[s]) ? rot : (float) yaw[s] + (rot / 3.14159265f - 1) * (float) yawSpread[s];
        if (st == TreeStates.FELLED) yw = yawToward((float) states.fallDeg(i, j)) + (rot / 3.14159265f - 1) * 0.15f;
        float gy = height == null ? 0 : height.sample(0, x, z);
        out.i = i; out.j = j; out.species = s; out.variant = v; out.state = st;
        out.type = (st * species + s) * variants + v;
        out.x = x; out.z = z;
        out.y = st == TreeStates.FELLED ? gy : gy - 0.2f * scale;
        out.yaw = yw; out.scale = scale; out.tint = u01(h, 6); out.phase = u01(h, 8) * 6.2831853f;
        return true;
    }

    /** Zählt die Bäume im Rechteck je Typ (Länge {@link #types()}); für Statistik und Prüfung. */
    public long[] count(double x0, double z0, double x1, double z1) {
        long[] n = new long[types()];
        Tree t = new Tree();
        int a = (int) Math.floor(x0 / spacing), b = (int) Math.floor(z0 / spacing), c = (int) Math.floor(x1 / spacing), e = (int) Math.floor(z1 / spacing);
        for (int j = b; j <= e; j++)
            for (int i = a; i <= c; i++)
                if (at(i, j, t) && t.x >= x0 && t.x < x1 && t.z >= z0 && t.z < z1) n[t.type]++;
        return n;
    }
}
