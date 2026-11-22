package com.dan.render.reference;

/**
 * Ein Hüllkörperbaum über achsparallele Kästen (seit P12c3): gebaut durch Teilen in der Mitte der längsten Achse der
 * Mittelpunkte (bei einer leeren Seite am Median), Blätter mit höchstens {@link #BLATT} Teilen. Ein Strahl besucht die
 * Knoten nah zuerst und fragt für jeden Teil {@link Teil#schneide}; der kürzeste Weg kürzt die Suche.
 */
public final class Huellbaum {
    /** Höchstzahl der Teile je Blatt. */
    public static final int BLATT = 4;

    /** Ein Teil: Schnitt mit dem Strahl bis tMax; gibt den Weg oder +∞. */
    public interface Teil {
        double schneide(int i, double tMax);
    }

    /** Knoten: Kasten (6 double), links/erster, Zahl (0: innerer Knoten, rechts = links + 1 … gespeichert). */
    final double[] kasten;
    final int[] knoten;     // je Knoten: [a, b] – innen: a = linker Knoten, b = −(rechter Knoten) − 1; Blatt: a = erster Teil, b = Zahl
    /** Reihenfolge der Teile. */
    public final int[] teile;
    public final int zahl;
    int knotenZahl;

    /** lo, hi: je Teil drei Werte (x, y, z). */
    public Huellbaum(double[] lo, double[] hi) {
        int n = lo.length / 3;
        this.zahl = n;
        teile = new int[n];
        for (int i = 0; i < n; i++) teile[i] = i;
        int max = Math.max(1, 2 * n);
        kasten = new double[6 * max];
        knoten = new int[2 * max];
        double[] mitte = new double[3 * n];
        for (int i = 0; i < 3 * n; i++) mitte[i] = 0.5 * (lo[i] + hi[i]);
        if (n == 0) {
            knotenZahl = 1;
            for (int a = 0; a < 3; a++) {
                kasten[a] = Double.POSITIVE_INFINITY;
                kasten[3 + a] = Double.NEGATIVE_INFINITY;
            }
            knoten[0] = 0;
            knoten[1] = 0;
            return;
        }
        knotenZahl = 1;
        baue(0, 0, n, lo, hi, mitte, 0);
    }

    private void baue(int k, int von, int bis, double[] lo, double[] hi, double[] mitte, int tiefe) {
        double[] b = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double[] c = b.clone();
        for (int q = von; q < bis; q++) {
            int i = teile[q];
            for (int a = 0; a < 3; a++) {
                b[a] = Math.min(b[a], lo[3 * i + a]);
                b[3 + a] = Math.max(b[3 + a], hi[3 * i + a]);
                c[a] = Math.min(c[a], mitte[3 * i + a]);
                c[3 + a] = Math.max(c[3 + a], mitte[3 * i + a]);
            }
        }
        System.arraycopy(b, 0, kasten, 6 * k, 6);
        int n = bis - von;
        if (n <= BLATT || tiefe > 60) {
            knoten[2 * k] = von;
            knoten[2 * k + 1] = n;
            return;
        }
        int achse = 0;
        double best = -1;
        for (int a = 0; a < 3; a++)
            if (c[3 + a] - c[a] > best) {
                best = c[3 + a] - c[a];
                achse = a;
            }
        int m;
        if (!(best > 0)) m = (von + bis) >>> 1;
        else {
            double grenze = 0.5 * (c[achse] + c[3 + achse]);
            int i = von, j = bis - 1;
            while (i <= j) {
                if (mitte[3 * teile[i] + achse] < grenze) i++;
                else {
                    int t = teile[i];
                    teile[i] = teile[j];
                    teile[j] = t;
                    j--;
                }
            }
            m = i;
            if (m == von || m == bis) m = (von + bis) >>> 1;
        }
        int links = knotenZahl, rechts = knotenZahl + 1;
        knotenZahl += 2;
        knoten[2 * k] = links;
        knoten[2 * k + 1] = -rechts - 1;
        baue(links, von, m, lo, hi, mitte, tiefe + 1);
        baue(rechts, m, bis, lo, hi, mitte, tiefe + 1);
    }

    /** Eintritt des Strahls in den Kasten des Knotens k (Kehrwerte der Richtung inv); +∞, wenn er ihn bis tMax nicht trifft. */
    private double eintritt(int k, double[] p, double[] inv, double tMax) {
        int o = 6 * k;
        double t0 = 0, t1 = tMax;
        for (int a = 0; a < 3; a++) {
            double ta = (kasten[o + a] - p[a]) * inv[a], tb = (kasten[o + 3 + a] - p[a]) * inv[a];
            if (ta > tb) {
                double t = ta;
                ta = tb;
                tb = t;
            }
            if (Double.isNaN(ta)) ta = Double.NEGATIVE_INFINITY;
            if (Double.isNaN(tb)) tb = Double.POSITIVE_INFINITY;
            if (ta > t0) t0 = ta;
            if (tb < t1) t1 = tb;
            if (t0 > t1) return Double.POSITIVE_INFINITY;
        }
        return t0;
    }

    /** Kürzester Weg eines Teils bis tMax; +∞ ohne. Der Teil merkt sich selbst, welcher getroffen hat. */
    public double schneide(double[] p, double[] d, double tMax, Teil teil) {
        if (zahl == 0) return Double.POSITIVE_INFINITY;
        double[] inv = {1 / d[0], 1 / d[1], 1 / d[2]};
        int[] stapel = new int[128];
        double[] stapelT = new double[128];
        int sp = 0;
        double best = tMax;
        if (eintritt(0, p, inv, best) == Double.POSITIVE_INFINITY) return Double.POSITIVE_INFINITY;
        stapel[sp] = 0;
        stapelT[sp++] = 0;
        boolean gefunden = false;
        while (sp > 0) {
            sp--;
            int k = stapel[sp];
            if (stapelT[sp] > best) continue;
            int a = knoten[2 * k], b = knoten[2 * k + 1];
            if (b >= 0) {
                for (int q = a; q < a + b; q++) {
                    double t = teil.schneide(teile[q], best);
                    if (t < best) {
                        best = t;
                        gefunden = true;
                    }
                }
            } else {
                int l = a, r = -b - 1;
                double tl = eintritt(l, p, inv, best), tr = eintritt(r, p, inv, best);
                if (tl <= tr) {
                    if (tr != Double.POSITIVE_INFINITY) { stapel[sp] = r; stapelT[sp++] = tr; }
                    if (tl != Double.POSITIVE_INFINITY) { stapel[sp] = l; stapelT[sp++] = tl; }
                } else {
                    if (tl != Double.POSITIVE_INFINITY) { stapel[sp] = l; stapelT[sp++] = tl; }
                    if (tr != Double.POSITIVE_INFINITY) { stapel[sp] = r; stapelT[sp++] = tr; }
                }
            }
        }
        return gefunden ? best : Double.POSITIVE_INFINITY;
    }

    public int knoten() { return knotenZahl; }
}
