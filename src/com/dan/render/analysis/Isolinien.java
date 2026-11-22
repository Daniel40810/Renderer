package com.dan.render.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Isolinien eines Felds auf einem regelmäßigen Gitter (seit P11c2, Werkbuch HalligHooge Teil A, 12.12): Marching Squares
 * über den Mitten der Zellen, die Stücke zu Linien verbunden, auf Wunsch nach Douglas und Peucker vereinfacht.
 * <ul>
 * <li><b>Lage:</b> in Zellen; die Mitte der Zelle (i, j) liegt bei (i + 0,5; j + 0,5), j nach Norden wie
 * {@code Gitter.y(j)}.</li>
 * <li><b>Lücken:</b> ein Quadrat mit einer Ecke NaN (trocken, außerhalb) trägt keine Linie; Linien enden dort.</li>
 * <li><b>Kante:</b> der Schnittpunkt linear zwischen den zwei Ecken. Ein Wert genau auf der Stufe zählt als „darüber“.</li>
 * <li><b>Sattel</b> (zwei gegenüberliegende Ecken darüber): entschieden nach dem Mittel der vier Ecken.</li>
 * <li><b>Begleitfeld:</b> je Punkt der Wert eines zweiten Felds, mit demselben Anteil auf der Kante (etwa der
 * Wasserstand, auf dem eine Linie der Tiefe liegt).</li>
 * </ul>
 * Reines Java ohne Karte; die Linien rechnet der Prozessor, wenn sich der Stand ändert.
 */
public final class Isolinien {
    private Isolinien() { }

    /** Eine Linie: Punkte (x, y in Zellen) und das Begleitfeld je Punkt; geschlossen, wenn der letzte Punkt der erste ist. */
    public static final class Linie {
        public final float[] x, y, z;
        public final double stufe;
        public final boolean geschlossen;

        Linie(float[] x, float[] y, float[] z, double stufe, boolean geschlossen) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.stufe = stufe;
            this.geschlossen = geschlossen;
        }

        public int punkte() { return x.length; }
    }

    /** Die Linien von {@code f} (nx × ny Zellen, zeilenweise, j nach Norden) zur Stufe; {@code begleit} darf null sein. */
    public static List<Linie> linien(float[] f, float[] begleit, int nx, int ny, double stufe) {
        // Stücke: je Stück zwei Schlüssel der Kanten; Schlüssel 2 (i + nx j) für die Kante nach Osten, + 1 nach Norden
        Map<Long, float[]> punkt = new HashMap<>();
        List<long[]> stuecke = new ArrayList<>();
        long[] k = new long[4];
        boolean[] schnitt = new boolean[4];
        for (int j = 0; j + 1 < ny; j++)
            for (int i = 0; i + 1 < nx; i++) {
                int c00 = i + nx * j, c10 = c00 + 1, c01 = c00 + nx, c11 = c01 + 1;
                float a = f[c00], b = f[c10], c = f[c11], d = f[c01];
                if (Float.isNaN(a) || Float.isNaN(b) || Float.isNaN(c) || Float.isNaN(d)) continue;
                int fall = (a >= stufe ? 1 : 0) | (b >= stufe ? 2 : 0) | (c >= stufe ? 4 : 0) | (d >= stufe ? 8 : 0);
                if (fall == 0 || fall == 15) continue;
                // Kanten im Kreis: 0 unten (00–10), 1 rechts (10–11), 2 oben (01–11), 3 links (00–01)
                k[0] = 2L * c00;
                k[1] = 2L * c10 + 1;
                k[2] = 2L * c01;
                k[3] = 2L * c00 + 1;
                schnitt[0] = (a >= stufe) != (b >= stufe);
                schnitt[1] = (b >= stufe) != (c >= stufe);
                schnitt[2] = (d >= stufe) != (c >= stufe);
                schnitt[3] = (a >= stufe) != (d >= stufe);
                for (int e = 0; e < 4; e++) if (schnitt[e] && !punkt.containsKey(k[e])) punkt.put(k[e], kante(f, begleit, nx, i, j, e, stufe));
                if (fall == 5 || fall == 10) {
                    boolean mitte = (a + b + c + d) / 4 >= stufe;
                    // 5: 00 und 11 darüber; 10: 10 und 01 darüber
                    boolean hochVerbunden = mitte;
                    if ((fall == 5) == hochVerbunden) {               // die Ecken 10 und 01 abtrennen
                        stuecke.add(new long[] {k[0], k[1]});
                        stuecke.add(new long[] {k[2], k[3]});
                    } else {                                          // die Ecken 00 und 11 abtrennen
                        stuecke.add(new long[] {k[3], k[0]});
                        stuecke.add(new long[] {k[1], k[2]});
                    }
                } else {
                    long p = -1, q = -1;
                    for (int e = 0; e < 4; e++)
                        if (schnitt[e]) {
                            if (p < 0) p = k[e];
                            else q = k[e];
                        }
                    stuecke.add(new long[] {p, q});
                }
            }
        return verbinde(stuecke, punkt, stufe);
    }

    /** Schnittpunkt auf Kante e des Quadrats (i, j): x, y in Zellen und das Begleitfeld. */
    private static float[] kante(float[] f, float[] begleit, int nx, int i, int j, int e, double stufe) {
        int c00 = i + nx * j;
        int p, q;
        double xp, yp, xq, yq;
        switch (e) {
            case 0 -> { p = c00; q = c00 + 1; xp = i; yp = j; xq = i + 1; yq = j; }
            case 1 -> { p = c00 + 1; q = c00 + 1 + nx; xp = i + 1; yp = j; xq = i + 1; yq = j + 1; }
            case 2 -> { p = c00 + nx; q = c00 + nx + 1; xp = i; yp = j + 1; xq = i + 1; yq = j + 1; }
            default -> { p = c00; q = c00 + nx; xp = i; yp = j; xq = i; yq = j + 1; }
        }
        double fp = f[p], fq = f[q], t = fq == fp ? 0.5 : (stufe - fp) / (fq - fp);
        t = Math.max(0, Math.min(1, t));
        float z = Float.NaN;
        if (begleit != null) z = (float) (begleit[p] + t * (begleit[q] - begleit[p]));
        return new float[] {(float) (xp + t * (xq - xp) + 0.5), (float) (yp + t * (yq - yp) + 0.5), z};
    }

    private static List<Linie> verbinde(List<long[]> stuecke, Map<Long, float[]> punkt, double stufe) {
        Map<Long, List<Integer>> an = new HashMap<>();
        for (int s = 0; s < stuecke.size(); s++)
            for (long key : stuecke.get(s)) an.computeIfAbsent(key, x -> new ArrayList<>(2)).add(s);
        boolean[] benutzt = new boolean[stuecke.size()];
        List<Linie> linien = new ArrayList<>();
        // erst die offenen Linien (ein Ende mit nur einem Stück), dann die geschlossenen
        for (int runde = 0; runde < 2; runde++)
            for (int s0 = 0; s0 < stuecke.size(); s0++) {
                if (benutzt[s0]) continue;
                long[] st = stuecke.get(s0);
                long anfang;
                if (runde == 0) {
                    if (an.get(st[0]).size() == 1) anfang = st[0];
                    else if (an.get(st[1]).size() == 1) anfang = st[1];
                    else continue;
                } else anfang = st[0];
                List<Long> folge = new ArrayList<>();
                folge.add(anfang);
                long jetzt = anfang;
                int s = s0;
                while (s >= 0 && !benutzt[s]) {
                    benutzt[s] = true;
                    long[] x = stuecke.get(s);
                    long weiter = x[0] == jetzt ? x[1] : x[0];
                    folge.add(weiter);
                    jetzt = weiter;
                    s = -1;
                    for (int n : an.get(jetzt)) if (!benutzt[n]) s = n;
                }
                boolean zu = folge.size() > 2 && folge.get(0).equals(folge.get(folge.size() - 1));
                int n = folge.size();
                float[] lx = new float[n], ly = new float[n], lz = new float[n];
                for (int i = 0; i < n; i++) {
                    float[] p = punkt.get(folge.get(i));
                    lx[i] = p[0];
                    ly[i] = p[1];
                    lz[i] = p[2];
                }
                linien.add(new Linie(lx, ly, lz, stufe, zu));
            }
        return linien;
    }

    /** Douglas und Peucker: lässt Punkte weg, die höchstens {@code toleranz} (Zellen) von der Sehne liegen. Enden bleiben. */
    public static Linie vereinfache(Linie l, double toleranz) {
        int n = l.punkte();
        if (n <= 2 || toleranz <= 0) return l;
        boolean[] bleibt = new boolean[n];
        bleibt[0] = bleibt[n - 1] = true;
        int[] stapel = new int[2 * n];
        int sp = 0;
        stapel[sp++] = 0;
        stapel[sp++] = n - 1;
        while (sp > 0) {
            int b = stapel[--sp], a = stapel[--sp];
            if (b - a < 2) continue;
            double ax = l.x[a], ay = l.y[a], bx = l.x[b], by = l.y[b], dx = bx - ax, dy = by - ay, len = Math.hypot(dx, dy);
            double best = -1;
            int bi = -1;
            for (int i = a + 1; i < b; i++) {
                double d = len < 1e-12 ? Math.hypot(l.x[i] - ax, l.y[i] - ay) : Math.abs(dy * (l.x[i] - ax) - dx * (l.y[i] - ay)) / len;
                if (d > best) {
                    best = d;
                    bi = i;
                }
            }
            if (best > toleranz) {
                bleibt[bi] = true;
                stapel[sp++] = a;
                stapel[sp++] = bi;
                stapel[sp++] = bi;
                stapel[sp++] = b;
            }
        }
        int m = 0;
        for (boolean x : bleibt) if (x) m++;
        float[] x = new float[m], y = new float[m], z = new float[m];
        int k = 0;
        for (int i = 0; i < n; i++)
            if (bleibt[i]) {
                x[k] = l.x[i];
                y[k] = l.y[i];
                z[k++] = l.z[i];
            }
        return new Linie(x, y, z, l.stufe, l.geschlossen);
    }
}
