package com.dan.lauf;

import com.dan.core.gitter.Gitter;
import com.dan.render.analysis.Messblick;

import com.dan.render.analysis.Querschnitt;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

/**
 * Die Felder des Messblicks aus einer {@link Bild.Quelle} (seit P11c2, Werkbuch HalligHooge Teil A, 12.12): auf dem
 * Gitter der Strömung (aus dem Nachlauf, {@code hydro_G3}) oder, ohne Strömung, auf dem feinsten Wassergitter; je Stand
 * u, v, η, h und, wenn das Sediment auf demselben Gitter liegt, die Trübung (g/m³, wie {@code Bodennetz.truebung}:
 * 1 000 · (Schlick + 0,05 · Sand)). Zwei Stände um die Zeit wie in {@link Bild#zu}.
 */
public final class Messquelle {
    private final Bild.Quelle q;
    /** Das Gitter des Messblicks. */
    public final Gitter gitter;
    private final int k;
    /** Hat die Quelle Strömung (u, v)? Sonst ruht das Wasser im Messblick (nur Isolinien). */
    public final boolean stroemung;
    /** Liegt die Trübung auf demselben Gitter? */
    public final boolean truebung;
    private double paarA = Double.NaN, paarB = Double.NaN;
    private volatile float[][] stA, stB;
    private volatile float[] trA, trB;
    private volatile double wJetzt;
    /** Seit P11c3: Hinweise aus {@code hydro_<Gitter>/hinweis.txt} (Stunden nach dem ersten Stand von, bis; Text). */
    private final List<Object[]> hinweise = new ArrayList<>();
    /** Trocken unter dieser Tiefe (m), wie im Messblick. */
    public double hMin = 0.05;

    public Messquelle(Bild.Quelle q) { this(q, null); }

    /** Mit dem Ordner des Archivs (darf null sein): dann gelten die Hinweise aus {@code hydro_<Gitter>/hinweis.txt}. */
    public Messquelle(Bild.Quelle q, File archiv) {
        this.q = q;
        Gitter s = q.stroemungGitter();
        List<Gitter> wg = q.gitter();
        int kk = -1;
        if (s != null) for (int i = 0; i < wg.size(); i++) if (gleich(wg.get(i), s)) kk = i;
        if (kk < 0 && !wg.isEmpty()) kk = wg.size() - 1;               // grob zuerst: das letzte ist das feinste
        k = kk;
        gitter = kk >= 0 ? wg.get(kk) : null;
        stroemung = s != null && kk >= 0 && gleich(wg.get(kk), s);
        Gitter sg = q.sedimentGitter();
        truebung = gitter != null && sg != null && gleich(sg, gitter);
        if (archiv != null && s != null) {
            File h = new File(new File(archiv, Stroemung.ORDNER + s.name), "hinweis.txt");
            if (h.isFile())
                try {
                    for (String z : Files.readAllLines(h.toPath(), StandardCharsets.UTF_8)) {
                        z = z.strip();
                        if (z.isEmpty() || z.startsWith("#")) continue;
                        String[] t = z.split("\\s+", 3);
                        hinweise.add(new Object[] {Double.parseDouble(t[0]), Double.parseDouble(t[1]), t[2]});
                    }
                } catch (Exception e) {
                    hinweise.add(new Object[] {0.0, 1e9, "hinweis.txt nicht lesbar: " + e.getMessage()});
                }
        }
    }

    static boolean gleich(Gitter a, Gitter b) {
        return a.nx == b.nx && a.ny == b.ny && Math.abs(a.x0 - b.x0) < 1e-6 && Math.abs(a.y0 - b.y0) < 1e-6 && Math.abs(a.dx - b.dx) < 1e-9 && Math.abs(a.dy - b.dy) < 1e-9;
    }

    /** Setzt die Stände um t in den Messblick (nur wenn sich das Paar ändert) und die Zeit; false ohne Stand. */
    public boolean zu(double t, Messblick m) {
        if (gitter == null) return false;
        double[] z = stroemung ? q.stroemungZeiten() : q.zeiten();
        if (z.length == 0) return false;
        int i = 0;
        while (i + 1 < z.length && z[i + 1] <= t) i++;
        double a = z[i], b = i + 1 < z.length && t > a ? z[i + 1] : a;
        if (t < z[0]) a = b = z[0];
        if (a != paarA || b != paarB) {
            float[][] na;
            float[] ta;
            if (a == paarB && stB != null) {
                na = stB;
                ta = trB;
            } else {
                na = lies(a);
                if (na == null) return false;
                ta = trueb(a);
            }
            float[][] nb = b == a ? na : lies(b);
            if (nb == null) return false;
            float[] tb = b == a ? ta : trueb(b);
            stA = na;
            stB = nb;
            trA = ta;
            trB = tb;
            paarA = a;
            paarB = b;
            m.staende(gitter, na, nb, ta, tb, a, b);
        }
        m.zeit(t);
        wJetzt = b > a ? Math.max(0, Math.min(1, (t - a) / (b - a))) : 0;
        return true;
    }

    /**
     * Seit P11c3: ein Hinweis zur Zeit t (null: keiner). Ohne Strömung: nur Isolinien. Der Nachlauf beginnt in Ruhe: in den
     * ersten 2 h [G] schwingt die Strömung ein. Dazu die Hinweise des Archivs (F118 A: M1b).
     */
    public String hinweis(double t) {
        if (gitter == null) return "kein Gitter für den Messblick";
        if (!stroemung) return "keine Strömung (live oder ohne Nachlauf): nur Isolinien";
        double[] z = q.stroemungZeiten();
        if (z.length == 0) return null;
        double h = (t - z[0]) / 3600;
        List<String> l = new ArrayList<>();
        for (Object[] x : hinweise) if (h >= (double) x[0] && h <= (double) x[1]) l.add((String) x[2]);
        if (l.isEmpty() && h < 2) l.add("Nachlauf beginnt in Ruhe: die Strömung schwingt in den ersten 2 h ein");
        return l.isEmpty() ? null : String.join(" · ", l);
    }

    /** Seit P11c3: Wasserstand am Ort (m NHN) aus dem letzten Paar, NaN trocken oder außerhalb. */
    public double wasser(double ost, double nord) {
        float[][] a = stA, b = stB;
        if (a == null || gitter == null) return Double.NaN;
        double x = (ost - gitter.x0) / gitter.dx, y = (nord - gitter.y0) / gitter.dy;
        if (x < 0 || y < 0 || x >= gitter.nx || y >= gitter.ny) return Double.NaN;
        int c = (int) x + gitter.nx * (int) y;
        double w = wJetzt, h = a[3][c] + w * (b[3][c] - a[3][c]);
        return h > hMin ? a[2][c] + w * (b[2][c] - a[2][c]) : Double.NaN;
    }

    /** Seit P11c3: das Profil des Schnitts zum letzten Paar (linear zwischen den Ständen); null ohne Stand. */
    public Querschnitt.Profil profil(Querschnitt s, DoubleBinaryOperator grund) {
        float[][] a = stA, b = stB;
        float[] ta = trA, tb = trB;
        if (a == null) return null;
        double w = wJetzt;
        Querschnitt.Profil pa = s.profil(a[0], a[1], a[2], a[3], ta, hMin, grund);
        if (b == a || w == 0) return pa;
        Querschnitt.Profil pb = s.profil(b[0], b[1], b[2], b[3], tb == null ? ta : tb, hMin, (o, n) -> Double.NaN);
        int n = pa.punkte();
        double[] eta = new double[n], qu = new double[n], tr = new double[n];
        for (int k = 0; k < n; k++) {
            eta[k] = mix(pa.eta()[k], pb.eta()[k], w);
            qu[k] = mix(pa.quer()[k], pb.quer()[k], w);
            tr[k] = mix(pa.truebung()[k], pb.truebung()[k], w);
        }
        return new Querschnitt.Profil(pa.s(), pa.grund(), eta, qu, tr);
    }

    /** Linear, wo beide gelten; sonst der nähere Stand. */
    private static double mix(double a, double b, double w) {
        if (!Double.isNaN(a) && !Double.isNaN(b)) return a + w * (b - a);
        return w < 0.5 ? a : b;
    }

    /** Seit P11c3: die Reihe Q(t) durch den Schnitt über alle Stände der Strömung: {Zeiten, Q (m³/s)}; ohne Strömung leer. */
    public double[][] durchfluss(Querschnitt s) {
        if (!stroemung) return new double[][] {new double[0], new double[0]};
        double[] z = q.stroemungZeiten();
        double[] r = new double[z.length];
        float[] qq = new float[(gitter.nx + 1) * gitter.ny + gitter.nx * (gitter.ny + 1)];
        for (int k = 0; k < z.length; k++) r[k] = q.fluss(z[k], qq) ? s.durchfluss(qq) : Double.NaN;
        return new double[][] {z, r};
    }

    private float[][] lies(double ts) {
        int n = gitter.zellen();
        float[] u = null, v = null, eta = new float[n], h = new float[n], alter = new float[n];
        if (stroemung) {
            u = new float[n];
            v = new float[n];
            if (!q.stroemung(ts, u, v)) u = v = null;
        }
        if (!q.stand(ts, k, eta, h, alter)) return null;
        return new float[][] {u, v, eta, h};
    }

    private float[] trueb(double ts) {
        if (!truebung) return null;
        int n = gitter.zellen();
        float[] c = new float[2 * n];
        double[] sz = q.sedimentZeiten();
        if (sz.length == 0) return null;
        // der Stand des Sediments, der der Zeit am nächsten liegt (es legt seltener ab als hydro)
        double best = sz[0];
        for (double x : sz) if (Math.abs(x - ts) < Math.abs(best - ts)) best = x;
        if (!q.sediment(best, c)) return null;
        float[] r = new float[n];
        for (int i = 0; i < n; i++) r[i] = (float) (1000 * (c[2 * i + 1] + com.dan.core.szene.Bodennetz.SAND * c[2 * i]));
        return r;
    }
}
