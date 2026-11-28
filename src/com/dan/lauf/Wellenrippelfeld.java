package com.dan.lauf;

import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Kornfeld;
import com.dan.physics.sediment.Wellenrippel;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Wellenrippel als Gedächtnis (S6d-1, Werkbuch Seegang 84): je Zelle des Gitters des Bodens die Rippel, die die Welle
 * zuletzt geformt hat. Gerechnet aus der Quelle (Archiv), Stand für Stand des Seegangs: H_s, T_p und Richtung bilinear
 * aus dem feinsten Gitter des Seegangs, das die Zelle trägt (nur über nasse Zellen des Seegangs, H_s höchstens
 * {@link #BRECHEN} h), die Tiefe aus dem feinsten Wassergitter (nächste Zelle, der jüngste Stand bis zum Stand des
 * Seegangs), die Strömung aus {@code hydro_<Gitter>} (nächste Zelle; ohne sie keine). Die Regel und die Formeln stehen
 * in {@link Wellenrippel}. Wo es trocken ist ({@code h} &lt; {@link #H_MIN}) oder nichts das Korn bewegt, bleibt der alte
 * Stand – so liegen bei Niedrigwasser die Rippel der letzten Welle. Im Schlick werden sie flacher (Höhe mal
 * 1 − Schlickanteil/0,3 wie die Strömungsrippel in {@code physics.sediment}), auf festem Boden gibt es keine.
 * <p>
 * Neu angefangen wird, wenn die Zeit zurückspringt oder weiter als {@link #VORLAUF} nach vorn: dann von der Zeit minus
 * {@link #VORLAUF} an (ein Tidezyklus und mehr), damit auch am Anfang einer Szene Rippel liegen.
 */
public final class Wellenrippelfeld {
    public static final String VERSION = "0.1";
    /** Vorlauf beim Neuanfang (s); trockener als H_MIN (m) formt nichts; H_s höchstens BRECHEN · h. */
    public static final double VORLAUF = 12 * 3600, H_MIN = 0.05, BRECHEN = 0.78;
    /** Schlickanteil, bei dem die Rippel ganz flach sind; Anteil festen Bodens, ab dem keine liegen. */
    public static final double SCHLICK_GRENZE = 0.3, FEST_GRENZE = 0.5;
    /** Korn ohne Kornfeld (m). */
    public static final double D50_VORGABE = 2e-4;

    public final Gitter gitter;
    public final int n;
    /** Je Zelle: D50 (m), Schlickanteil, θ_cr; fest: keine Rippel. */
    public final double[] d50, schlick, krit;
    public final boolean[] fest;
    /** Das Gedächtnis: Höhe (m), Länge (m), Richtung, in die die formende Welle lief (rad, 0 = Ost, gegen den Uhrzeigersinn). */
    public final float[] eta, laenge, richtung;
    /** Stand (s), der zuletzt formte oder wegnahm (NaN: nie); Klasse 0–2 nach Wiberg und Harris, −1 ohne. */
    public final float[] zeit;
    public final byte[] klasse;
    /** Eingänge der letzten Formung (für die Prüfung): H_s (nach der Grenze), T_p (s), h (m), Strom (m/s), θ_w. */
    public final float[] hs, tp, tiefe, strom, thetaW;
    /** Zustand im letzten Schritt: −1 trocken, sonst {@link Wellenrippel#regel}. */
    public final byte[] zustand;
    /** Stand des Seegangs, bis zu dem gerechnet ist (NaN: keiner). */
    public volatile double stand = Double.NaN;
    /** Schritte gesamt; Dauer des letzten Schritts (s); Zahl je Zustand im letzten Schritt (trocken, bleibt, formt, Schicht, Strom). */
    public long schritte;
    public double letzteDauer;
    public final int[] zahl = new int[5];

    // Abtastung je Zelle
    private final int[] wk, wi, si;
    private final int[] sk;
    private final int[] s4;
    private final float[] w4;
    private final List<Gitter> wasserGitter, seeGitter;
    private final int[] wasserIndex;
    private final boolean[] wasserNoetig;
    private final Gitter stromGitter;
    private final float[][] wEta, wH, wAge;
    private final float[][][] seeFelder;
    private final float[] su, sv;

    /**
     * @param gitter Gitter des Bodens (UTM)
     * @param korn   Kornfeld des Gebiets oder null (dann Sand {@link #D50_VORGABE}, kein Schlick)
     * @param quelle die Quelle (für die Gitter des Wassers, des Seegangs und der Strömung)
     */
    public Wellenrippelfeld(Gitter gitter, Kornfeld korn, Bild.Quelle quelle) {
        if (gitter.lage != Gitter.Lage.UTM) throw new IllegalArgumentException("Wellenrippel: nur Gitter in UTM, nicht " + gitter);
        this.gitter = gitter;
        n = gitter.zellen();
        d50 = korn != null ? korn.auf(gitter, Kornfeld.D50) : filled(n, D50_VORGABE);
        schlick = korn != null ? korn.auf(gitter, Kornfeld.SCHLICK) : new double[n];
        double[] fe = korn != null ? korn.auf(gitter, Kornfeld.FEST) : new double[n];
        fest = new boolean[n];
        krit = new double[n];
        for (int c = 0; c < n; c++) {
            if (!(d50[c] > 0)) d50[c] = D50_VORGABE;
            fest[c] = fe[c] >= FEST_GRENZE;
            krit[c] = Wellenrippel.thetaKrit(d50[c]);
        }
        eta = new float[n];
        laenge = new float[n];
        richtung = new float[n];
        zeit = new float[n];
        klasse = new byte[n];
        hs = new float[n];
        tp = new float[n];
        tiefe = new float[n];
        strom = new float[n];
        thetaW = new float[n];
        zustand = new byte[n];
        leeren();
        // Wasser: das feinste Gitter in UTM, das die Mitte der Zelle trägt; die nächste Zelle
        List<Gitter> alle = quelle.gitter();
        wasserGitter = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> wx = new java.util.ArrayList<>();
        for (int k = 0; k < alle.size(); k++) if (alle.get(k).lage == Gitter.Lage.UTM) { wasserGitter.add(alle.get(k)); wx.add(k); }
        wasserIndex = wx.stream().mapToInt(Integer::intValue).toArray();
        wk = new int[n];
        wi = new int[n];
        for (int c = 0; c < n; c++) {
            double x = gitter.x(c % gitter.nx), y = gitter.y(c / gitter.nx);
            wk[c] = -1;
            double best = Double.MAX_VALUE;
            for (int k = 0; k < wasserGitter.size(); k++) {
                Gitter g = wasserGitter.get(k);
                int i = (int) Math.floor((x - g.x0) / g.dx), j = (int) Math.floor((y - g.y0) / g.dy);
                if (i < 0 || j < 0 || i >= g.nx || j >= g.ny || g.dx >= best) continue;
                best = g.dx;
                wk[c] = k;
                wi[c] = g.index(i, j);
            }
        }
        wasserNoetig = new boolean[wasserGitter.size()];
        for (int c = 0; c < n; c++) if (wk[c] >= 0) wasserNoetig[wk[c]] = true;
        wEta = new float[wasserGitter.size()][];
        wH = new float[wasserGitter.size()][];
        wAge = new float[wasserGitter.size()][];
        for (int k = 0; k < wasserGitter.size(); k++) {
            wEta[k] = new float[wasserGitter.get(k).zellen()];
            wH[k] = new float[wasserGitter.get(k).zellen()];
            wAge[k] = new float[wasserGitter.get(k).zellen()];
        }
        // Strömung: nächste Zelle
        stromGitter = quelle.stroemungGitter();
        si = new int[n];
        Arrays.fill(si, -1);
        if (stromGitter != null && stromGitter.lage == Gitter.Lage.UTM) {
            for (int c = 0; c < n; c++) {
                double x = gitter.x(c % gitter.nx), y = gitter.y(c / gitter.nx);
                int i = (int) Math.floor((x - stromGitter.x0) / stromGitter.dx), j = (int) Math.floor((y - stromGitter.y0) / stromGitter.dy);
                if (i >= 0 && j >= 0 && i < stromGitter.nx && j < stromGitter.ny) si[c] = stromGitter.index(i, j);
            }
            su = new float[stromGitter.zellen()];
            sv = new float[stromGitter.zellen()];
        } else {
            su = sv = null;
        }
        // Seegang: das feinste Gitter, dessen Mitten die Zelle umschließen; bilinear
        seeGitter = quelle.seegangGitter();
        sk = new int[n];
        s4 = new int[4 * n];
        w4 = new float[4 * n];
        for (int c = 0; c < n; c++) {
            double x = gitter.x(c % gitter.nx), y = gitter.y(c / gitter.nx);
            sk[c] = -1;
            double best = Double.MAX_VALUE;
            for (int k = 0; k < seeGitter.size(); k++) {
                Gitter g = seeGitter.get(k);
                if (g.lage != Gitter.Lage.UTM || g.dx >= best) continue;
                double u = (x - g.x0) / g.dx - 0.5, v = (y - g.y0) / g.dy - 0.5;
                if (u < 0 || v < 0 || u > g.nx - 1 || v > g.ny - 1) continue;
                int i = Math.min((int) Math.floor(u), g.nx - 2), j = Math.min((int) Math.floor(v), g.ny - 2);
                double fu = u - i, fv = v - j;
                best = g.dx;
                sk[c] = k;
                s4[4 * c] = g.index(i, j);
                s4[4 * c + 1] = g.index(i + 1, j);
                s4[4 * c + 2] = g.index(i, j + 1);
                s4[4 * c + 3] = g.index(i + 1, j + 1);
                w4[4 * c] = (float) ((1 - fu) * (1 - fv));
                w4[4 * c + 1] = (float) (fu * (1 - fv));
                w4[4 * c + 2] = (float) ((1 - fu) * fv);
                w4[4 * c + 3] = (float) (fu * fv);
            }
        }
        seeFelder = new float[seeGitter.size()][][];
        for (int k = 0; k < seeGitter.size(); k++) seeFelder[k] = new float[5][seeGitter.get(k).zellen()];
    }

    private static double[] filled(int n, double v) {
        double[] a = new double[n];
        Arrays.fill(a, v);
        return a;
    }

    /** Vergisst alle Rippel. */
    public void leeren() {
        Arrays.fill(eta, 0);
        Arrays.fill(laenge, 0);
        Arrays.fill(richtung, 0);
        Arrays.fill(zeit, Float.NaN);
        Arrays.fill(klasse, (byte) -1);
        stand = Double.NaN;
    }

    /** Der Stand des Seegangs, bis zu dem {@link #zu} zur Zeit t rechnen würde (NaN: keiner); gleich {@link #stand}: nichts zu tun. */
    public double ziel(Bild.Quelle quelle, double t) {
        double[] zs = quelle.seegangZeiten();
        double r = Double.NaN;
        for (double z : zs) if (z <= t) r = z;
        return r;
    }

    /**
     * Rechnet bis zur Zeit t (s seit Start): alle Stände des Seegangs seit dem letzten bis zum jüngsten ≤ t. Gibt true,
     * wenn sich das Feld geändert haben kann.
     */
    public boolean zu(Bild.Quelle quelle, double t) {
        double[] zs = quelle.seegangZeiten();
        int it = -1;
        for (int i = 0; i < zs.length; i++) if (zs[i] <= t) it = i;
        if (it < 0) {
            if (Double.isNaN(stand)) return false;
            leeren();
            return true;
        }
        double ziel = zs[it];
        if (ziel == stand) return false;
        int ab;
        if (Double.isNaN(stand) || ziel < stand || ziel - stand > VORLAUF) {
            leeren();
            ab = 0;
            while (ab < it && zs[ab] < ziel - VORLAUF) ab++;
        } else {
            ab = 0;
            while (ab <= it && zs[ab] <= stand) ab++;
        }
        for (int k = ab; k <= it; k++) schritt(quelle, zs[k]);
        stand = ziel;
        return true;
    }

    /** Ein Stand des Seegangs zur Zeit ts; false, wenn die Quelle ihn (oder das Wasser dazu) nicht hat. */
    public boolean schritt(Bild.Quelle quelle, double ts) {
        long n0 = System.nanoTime();
        for (int k = 0; k < seeGitter.size(); k++) if (!quelle.seegang(ts, k, seeFelder[k])) return false;
        double tw = juengster(quelle.zeiten(), ts);
        if (Double.isNaN(tw)) return false;
        for (int k = 0; k < wasserGitter.size(); k++) if (wasserNoetig[k] && !quelle.stand(tw, wasserIndex[k], wEta[k], wH[k], wAge[k])) return false;
        boolean mitStrom = false;
        if (su != null) {
            double tst = juengster(quelle.stroemungZeiten(), ts);
            mitStrom = !Double.isNaN(tst) && ts - tst <= 3600 && quelle.stroemung(tst, su, sv);
        }
        final boolean ms = mitStrom;
        final float zf = (float) ts;
        IntStream.range(0, n).parallel().forEach(c -> zelle(c, zf, ms));
        Arrays.fill(zahl, 0);
        for (int c = 0; c < n; c++) zahl[zustand[c] + 1]++;
        schritte++;
        letzteDauer = (System.nanoTime() - n0) * 1e-9;
        return true;
    }

    private void zelle(int c, float ts, boolean mitStrom) {
        if (fest[c] || wk[c] < 0) {
            zustand[c] = -1;
            return;
        }
        double h = wH[wk[c]][wi[c]];
        if (!(h >= H_MIN)) {
            zustand[c] = -1;
            return;
        }
        double[] s = seegangAm(c);
        double hsc = Math.min(s[0], BRECHEN * h), tpc = s[1];
        double uw = Wellenrippel.uw(hsc, tpc, h);
        double tw = Wellenrippel.thetaWelle(uw, tpc, d50[c]);
        double u = 0;
        if (mitStrom && si[c] >= 0) u = Math.hypot(su[si[c]], sv[si[c]]);
        double tc = Wellenrippel.thetaStrom(u, h, d50[c]);
        int r = Wellenrippel.regel(tw, tc, krit[c]);
        zustand[c] = (byte) r;
        if (r == Wellenrippel.BLEIBT) return;
        zeit[c] = ts;
        if (r == Wellenrippel.FORMT) {
            double[] geo = Wellenrippel.geometrie(Wellenrippel.d0(uw, tpc), d50[c]);
            double m = Math.max(0, 1 - schlick[c] / SCHLICK_GRENZE);
            eta[c] = (float) (geo[0] * m);
            laenge[c] = (float) geo[1];
            richtung[c] = (float) s[2];
            klasse[c] = (byte) geo[2];
            hs[c] = (float) hsc;
            tp[c] = (float) tpc;
            tiefe[c] = (float) h;
            strom[c] = (float) u;
            thetaW[c] = (float) tw;
        } else {
            eta[c] = 0;
            laenge[c] = 0;
            klasse[c] = -1;
        }
    }

    /**
     * H_s (m), T_p (s), Richtung (rad, wohin, 0 = Ost, gegen den Uhrzeigersinn) an der Zelle c: bilinear über die nassen
     * Zellen des Seegangs (H_s &gt; 0, T_p &gt; 0), die Richtung als Vektor; ohne nasse Zelle {0, 0, 0}.
     */
    public double[] seegangAm(int c) {
        int k = sk[c];
        if (k < 0) return new double[] {0, 0, 0};
        float[][] f = seeFelder[k];
        double w = 0, h = 0, t = 0, cx = 0, cy = 0;
        for (int q = 0; q < 4; q++) {
            int z = s4[4 * c + q];
            double wq = w4[4 * c + q];
            if (wq <= 0 || !(f[0][z] > 0) || !(f[1][z] > 0)) continue;
            double th = Math.toRadians(270 - f[2][z]);
            w += wq;
            h += wq * f[0][z];
            t += wq * f[1][z];
            cx += wq * Math.cos(th);
            cy += wq * Math.sin(th);
        }
        if (w <= 0) return new double[] {0, 0, 0};
        return new double[] {h / w, t / w, Math.atan2(cy, cx)};
    }

    /** Der jüngste Wert von z, der ≤ t ist; NaN, wenn keiner. */
    static double juengster(double[] z, double t) {
        double r = Double.NaN;
        for (double v : z) if (v <= t) r = v;
        return r;
    }

    /** Für das Gelände: je Zelle Höhe (m), Länge (m), Richtung (rad), 0. */
    public float[] textur() {
        float[] t = new float[4 * n];
        for (int c = 0; c < n; c++) {
            t[4 * c] = eta[c];
            t[4 * c + 1] = laenge[c];
            t[4 * c + 2] = richtung[c];
        }
        return t;
    }

    /** Zahl der Zellen mit Rippeln. */
    public int mitRippeln() {
        int m = 0;
        for (int c = 0; c < n; c++) if (eta[c] > 0 && laenge[c] > 0) m++;
        return m;
    }
}
