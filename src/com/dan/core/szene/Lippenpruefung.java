package com.dan.core.szene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seit S5b-5b (Seegang Werkbuch 51), für Prüfungen (FusssucheCheck, WalzeCheck; liegt im Kern neben der Brandung, weil
 * demo-Packages anderer Module nicht eingebunden werden dürfen): wertet die Lippen der Brandung im Netz Bild für Bild aus
 * ({@link Brandung#lippenProtokoll}), ohne die Nachbarn der Brandung zu kennen – nur aus dem, was gezeichnet wird.
 * <ul>
 * <li><b>Sichtbare Kante:</b> je sichtbares Ende (f &gt; 0) das Kleinere aus seiner Höhe über Wasser und Grund und dem
 * Abstand (Punkt für Punkt, im Raum) zur Form eines anderen Abschnitts, dessen Ende an derselben Stelle der Wasserlinie
 * liegt (höchstens {@link #GLEICH} m daneben). Ein Ende, das frei sichtbar aufhört, zählt mit seiner Höhe.</li>
 * <li><b>Stücke</b> (seit S5b-5d, Datei 53): Lippen, die an einem gemeinsamen Ende (dieselbe Form) mit f ≥ 0,5
 * aneinanderhängen, bilden ein Stück; seine Länge ist die Summe der Längen, über die f längs der Lippe (linear von A
 * nach B) mindestens 0,5 ist [G]. So zeigt sich, ob die Lippe in kurze Splitter zerfällt (Befund L3e).</li>
 * <li><b>Weg</b> eines Endes, das in beiden Bildern sichtbar ist, von Bild zu Bild (Hausdorff zwischen den Formen
 * derselben Welle). Ein Ende, das gerade auftaucht, liegt fast ganz unter Wasser; sein Weg zählt nicht.</li>
 * </ul>
 */
public final class Lippenpruefung {
    /** Enden an derselben Stelle der Wasserlinie: höchstens so weit auseinander (m) [G]. */
    public static final double GLEICH = 0.5;

    public long bilder, lippen, enden, geteilt, abschnitte;
    public final List<Double> kante = new ArrayList<>(), weg = new ArrayList<>(), stuecke = new ArrayList<>();
    /** f, ab dem ein Teil der Lippe als sichtbar zählt (für die Stücke) [G]. */
    public static final double HALB = 0.5;
    private Map<Integer, List<double[]>> vorher = new HashMap<>();

    /** Ein Bild: das Protokoll der Lippen nach {@link Brandung#rechne}. */
    public void bild(List<double[]> prot) {
        bilder++;
        lippen += prot.size();
        int K = Brandung.LIPPE_KOPF;
        Map<Long, List<double[]>> ort = new HashMap<>();
        Map<Integer, List<double[]>> je = new HashMap<>();
        for (double[] a : prot) {
            je.computeIfAbsent((int) a[0], x -> new ArrayList<>()).add(a);
            for (int e = 0; e < 2; e++) ort.computeIfAbsent(zelle(a[12 + 2 * e], a[13 + 2 * e]), x -> new ArrayList<>()).add(a);
        }
        abschnitte += je.size();
        for (double[] a : prot)
            for (int e = 0; e < 2; e++) {
                if (!(a[6 + e] > 0)) continue;                               // unter Wasser
                enden++;
                if (a[10 + e] > 0) geteilt++;
                double vx = a[12 + 2 * e], vz = a[13 + 2 * e], d = Math.max(0, a[8 + e]);
                long gx = (long) Math.floor(vx), gz = (long) Math.floor(vz);
                for (long x = gx - 1; x <= gx + 1; x++)
                    for (long z = gz - 1; z <= gz + 1; z++) {
                        List<double[]> li = ort.get((x << 32) ^ (z & 0xffffffffL));
                        if (li == null) continue;
                        for (double[] c : li) {
                            if (c[0] == a[0]) continue;
                            for (int ec = 0; ec < 2; ec++)
                                if (Math.hypot(c[12 + 2 * ec] - vx, c[13 + 2 * ec] - vz) <= GLEICH) d = Math.min(d, abstand(a, e, c, ec));
                        }
                    }
                kante.add(d);
            }
        stuecke(prot, ort);
        for (Map.Entry<Integer, List<double[]>> en : je.entrySet())
            for (double[] a : en.getValue())
                for (double[] v : vorher.getOrDefault(en.getKey(), List.of()))
                    if (Math.abs(v[2] - a[2]) < 0.3)
                        for (int e = 0; e < 2; e++)
                            if (a[6 + e] > 0 && v[6 + e] > 0) weg.add(Math.max(hausdorff(a, v, e), hausdorff(v, a, e)));
        vorher = je;
    }

    /** Die Stücke eines Bilds (Union-Find über gemeinsame sichtbare Enden). */
    private void stuecke(List<double[]> prot, Map<Long, List<double[]>> ort) {
        int n = prot.size();
        Map<double[], Integer> ix = new java.util.IdentityHashMap<>();
        for (int i = 0; i < n; i++) ix.put(prot.get(i), i);
        int[] eltern = new int[n];
        double[] sicht = new double[n];
        for (int i = 0; i < n; i++) {
            eltern[i] = i;
            double[] a = prot.get(i);
            double fa = a[6], fb = a[7], mx = Math.max(fa, fb), mn = Math.min(fa, fb);
            double anteil = mn >= HALB ? 1 : mx < HALB ? 0 : (mx - HALB) / (mx - mn);
            sicht[i] = anteil * Math.hypot(a[14] - a[12], a[15] - a[13]);
        }
        for (int i = 0; i < n; i++) {
            double[] a = prot.get(i);
            for (int e = 0; e < 2; e++) {
                if (a[6 + e] < HALB) continue;
                double vx = a[12 + 2 * e], vz = a[13 + 2 * e];
                long gx = (long) Math.floor(vx), gz = (long) Math.floor(vz);
                for (long x = gx - 1; x <= gx + 1; x++)
                    for (long z = gz - 1; z <= gz + 1; z++) {
                        List<double[]> li = ort.get((x << 32) ^ (z & 0xffffffffL));
                        if (li == null) continue;
                        for (double[] c : li) {
                            if (c == a || c[0] == a[0]) continue;
                            for (int ec = 0; ec < 2; ec++)
                                if (c[6 + ec] >= HALB && Math.hypot(c[12 + 2 * ec] - vx, c[13 + 2 * ec] - vz) <= 0.1 && abstand(a, e, c, ec) <= 0.05) {
                                    int r1 = wurzel(eltern, i), r2 = wurzel(eltern, ix.get(c));
                                    if (r1 != r2) eltern[r1] = r2;
                                }
                        }
                    }
            }
        }
        double[] summe = new double[n];
        for (int i = 0; i < n; i++) summe[wurzel(eltern, i)] += sicht[i];
        for (int i = 0; i < n; i++) if (eltern[i] == i && summe[i] > 0) stuecke.add(summe[i]);
    }

    private static int wurzel(int[] e, int i) {
        while (e[i] != i) { e[i] = e[e[i]]; i = e[i]; }
        return i;
    }

    /** Anteil der sichtbaren Länge in Stücken unter l (m). */
    public double anteilKurz(double l) {
        double alle = 0, kurz = 0;
        for (double x : stuecke) { alle += x; if (x < l) kurz += x; }
        return alle > 0 ? kurz / alle : Double.NaN;
    }

    private static long zelle(double x, double z) { return ((long) Math.floor(x) << 32) ^ ((long) Math.floor(z) & 0xffffffffL); }

    /** Größter Abstand Punkt für Punkt zwischen dem Ende ea von a und dem Ende ec von c. */
    static double abstand(double[] a, int ea, double[] c, int ec) {
        int P = (int) a[1], K = Brandung.LIPPE_KOPF;
        double mx = 0;
        for (int k = 0; k < P; k++) {
            int oa = K + 3 * (ea * P + k), oc = K + 3 * (ec * P + k);
            mx = Math.max(mx, Math.sqrt(sq(a[oa] - c[oc]) + sq(a[oa + 1] - c[oc + 1]) + sq(a[oa + 2] - c[oc + 2])));
        }
        return mx;
    }

    /** Größter Abstand eines Punkts des Endes e von a zum Linienzug des Endes e von v. */
    static double hausdorff(double[] a, double[] v, int e) {
        int P = (int) a[1], K = Brandung.LIPPE_KOPF;
        double mx = 0;
        for (int k = 0; k < P; k++) {
            int o = K + 3 * (e * P + k);
            double px = a[o], py = a[o + 1], pz = a[o + 2], mn = Double.MAX_VALUE;
            for (int m = 0; m + 1 < P; m++) {
                int o0 = K + 3 * (e * P + m), o1 = o0 + 3;
                double ux = v[o1] - v[o0], uy = v[o1 + 1] - v[o0 + 1], uz = v[o1 + 2] - v[o0 + 2], l2 = ux * ux + uy * uy + uz * uz;
                double tt = l2 > 0 ? Math.max(0, Math.min(1, ((px - v[o0]) * ux + (py - v[o0 + 1]) * uy + (pz - v[o0 + 2]) * uz) / l2)) : 0;
                mn = Math.min(mn, sq(px - v[o0] - tt * ux) + sq(py - v[o0 + 1] - tt * uy) + sq(pz - v[o0 + 2] - tt * uz));
            }
            mx = Math.max(mx, Math.sqrt(mn));
        }
        return mx;
    }

    private static double sq(double x) { return x * x; }

    /** Quantil p (0 … 1) einer Liste; NaN, wenn leer. */
    public static double q(List<Double> l, double p) {
        if (l.isEmpty()) return Double.NaN;
        double[] a = l.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return a[(int) (p * (a.length - 1))];
    }

    /** Anteil über x. */
    public static double ueber(List<Double> l, double x) {
        return l.isEmpty() ? Double.NaN : (double) l.stream().filter(v -> v > x).count() / l.size();
    }

    /** Eine Zeile für das Attest. */
    public String bericht() {
        return String.format(java.util.Locale.GERMANY, "%d Bilder; je Bild %.1f Lippen an %.1f Abschnitten; %d sichtbare Enden, davon %d mit einem Nachbarn geteilt (%.0f %%); sichtbare Kante Median %.3f m, 95 %% %.3f m, 99 %% %.3f m, höchstens %.3f m, über 0,15 m %.1f %%; Weg der Enden von Bild zu Bild Median %.3f m, 99 %% %.3f m, höchstens %.3f m",
                bilder, (double) lippen / Math.max(1, bilder), (double) abschnitte / Math.max(1, bilder), enden, geteilt, 100.0 * geteilt / Math.max(1, enden),
                q(kante, 0.5), q(kante, 0.95), q(kante, 0.99), q(kante, 1), 100 * ueber(kante, 0.15), q(weg, 0.5), q(weg, 0.99), q(weg, 1))
                + String.format(java.util.Locale.GERMANY, "; sichtbare Stücke (f ≥ %.1f): %d, Länge Median %.1f m, 90 %% %.1f m, sichtbare Länge in Stücken unter 10 m %.0f %%",
                HALB, stuecke.size(), q(stuecke, 0.5), q(stuecke, 0.9), 100 * anteilKurz(10));
    }
}
