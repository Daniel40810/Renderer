package com.dan.tree;

/**
 * Laub über das Jahr: Austrieb, Sommergrün, Verfärbung, Laubfall. Jedes Blatt hat eine Zufallszahl
 * {@code rand} in 0..1: Blätter mit kleinem {@code rand} färben sich zuerst und fallen zuerst,
 * im Frühjahr treiben Blätter mit kleinem {@code rand} zuerst aus. Tage im Jahr 0..364, Nordhalbkugel.
 */
public final class TreeSeason {

    public int leafOut = 110;
    /** Tage vom ersten bis zum letzten Blatt beim Austrieb */
    public int flushDays = 18;
    /** Tage, in denen das junge Laub zum Sommergrün nachdunkelt */
    public int matureDays = 45;
    public int colorStart = 290, colorFull = 312, bare = 322;
    public boolean evergreen;
    /** Laub bleibt das ganze Jahr, färbt sich aber (Gras): im Winter {@link #winter}, ab dem Austrieb wieder frisch */
    public boolean persistent;
    /** Farbe im Winter bei {@link #persistent} (null: letzte Herbstfarbe) */
    public float[] winter;

    /**
     * Bezug für Laubtexturen (mittlere Farbe der Textur); null: {@link #summer}. Seit Timmendorf Phase 6: eine herbstliche
     * Textur bekommt so im Sommer ihr Grün (Tönung = Sommerfarbe / Bezug), im Herbst ihre eigene Farbe.
     */
    public float[] reference;

    public float[] spring = {0.10f, 0.26f, 0.05f};
    public float[] summer = {0.03f, 0.095f, 0.025f};
    /** Herbstfarben vom frühen zum späten Blatt */
    public float[][] autumn = {{0.30f, 0.16f, 0.03f}, {0.40f, 0.12f, 0.02f}};

    /** Stand an einem Tag. */
    public static final class Look {
        /** sichtbar sind Blätter mit lo ≤ rand < hi */
        public float lo, hi;
        /** Farbe für rand = 0 und rand = 1, linear */
        public final float[] colorLo = new float[3], colorHi = new float[3];
        public int day;
        public float snow;
        /** Sommergrün: Bezug für Laubtexturen (Tönung = Farbe / Bezug) */
        public final float[] reference = new float[3];

        public boolean visible(float rand) { return rand >= lo && rand < hi; }

        public void color(float rand, float[] out) {
            for (int k = 0; k < 3; k++) out[k] = colorLo[k] + (colorHi[k] - colorLo[k]) * rand;
        }

        public boolean bare() { return lo >= hi; }
    }

    public Look look(int day, float snow) {
        day = Math.floorMod(day, 365);
        Look l = new Look();
        l.day = day;
        l.snow = Math.max(0, Math.min(1, snow));
        set(l.reference, reference != null ? reference : summer);
        if (evergreen) {
            l.lo = 0;
            l.hi = 1;
            set(l.colorLo, summer);
            set(l.colorHi, summer);
            return l;
        }
        if (persistent) {
            l.lo = 0;
            l.hi = 1;
            float[] base = new float[3], dry = winter != null ? winter : autumnAt(1);
            // Winter (nach bare und vor leafOut) dürr; Austrieb vom Dürren zum Frühlingsgrün, dann wie sonst
            float green = smooth(leafOut, leafOut + flushDays, day);
            mix(dry, spring, green, base);
            float mature = smooth(leafOut + flushDays, leafOut + flushDays + matureDays, day);
            float[] tmp = new float[3];
            mix(base, summer, mature, tmp);
            float p = smooth(colorStart, colorFull, day), q = smooth(colorFull, bare, day);
            float pLo = Math.min(1, p * 1.6f), pHi = Math.max(0, p * 1.6f - 0.6f);
            float[] lo = new float[3], hi = new float[3];
            mix(tmp, autumnAt(0), pLo, lo);
            mix(tmp, autumnAt(1), pHi, hi);
            mix(lo, dry, q, l.colorLo);
            mix(hi, dry, q, l.colorHi);
            if (day >= bare || day < leafOut) { set(l.colorLo, dry); set(l.colorHi, dry); }
            return l;
        }
        float flush = smooth(leafOut, leafOut + flushDays, day);
        float drop = smooth(colorFull, bare, day);
        l.lo = drop;
        l.hi = day < colorStart ? flush : 1;
        if (day >= bare || day < leafOut) { l.lo = 1; l.hi = 0; }

        // Grundfarbe vor dem Herbst: hell austreiben, zum Sommer nachdunkeln
        float[] base = new float[3];
        float mature = smooth(leafOut, leafOut + flushDays + matureDays, day);
        mix(spring, summer, mature, base);
        // Herbst: frühe Blätter (rand 0) sind weiter als späte (rand 1)
        float p = smooth(colorStart, colorFull, day);
        float pLo = Math.min(1, p * 1.6f), pHi = Math.max(0, p * 1.6f - 0.6f);
        mix(base, autumnAt(0), pLo, l.colorLo);
        mix(base, autumnAt(1), pHi, l.colorHi);
        return l;
    }

    private float[] autumnAt(float t) {
        if (autumn == null || autumn.length == 0) return summer;
        if (autumn.length == 1) return autumn[0];
        float f = t * (autumn.length - 1);
        int i = Math.min((int) f, autumn.length - 2);
        float[] out = new float[3];
        mix(autumn[i], autumn[i + 1], f - i, out);
        return out;
    }

    static float smooth(float a, float b, float x) {
        if (b <= a) return x >= b ? 1 : 0;
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static void mix(float[] a, float[] b, float t, float[] out) {
        for (int k = 0; k < 3; k++) out[k] = a[k] + (b[k] - a[k]) * t;
    }

    private static void set(float[] d, float[] s) { System.arraycopy(s, 0, d, 0, 3); }
}
