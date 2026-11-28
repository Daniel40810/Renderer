package com.dan.physics.auftrieb;

import com.dan.core.szene.Wasserpunkte;

/**
 * Wasser von der Karte für einen Körper (Seegang Werkbuch, S7b-1, Datei 88): je Säule seiner Proben ({@link Proben#saeule})
 * die rohen Summen der linearen Wellen je Band an einer Stelle ({@link Wasserpunkte}), dazu η der Fläche über dem festen Ort
 * und Pegel, Strom, Wind und Grund, die der Lauf je Bild setzt.
 * <p>
 * <b>Bänder.</b> Die Bänder der Fläche werden zu höchstens {@link #BAENDER} Bändern des Körpers zusammengelegt (die Summen
 * sind additiv) [G]. Je Band: k_b = Σ |Z| k / Σ |Z|, C1 = K1 − k_b C0, und die Druckhöhe in der Tiefe z (unter dem Pegel)
 * ψ(z) = Σ_b A(k_b, z) (Re C0 + D_b(z) Re C1) + (η − Re Z) A(k_p, z), mit A = cosh(k (z + h)) / cosh(k h) und
 * D = (z + h) tanh(k_b (z + h)) − h tanh(k_b h) wie {@link Druckprofil}; das letzte Glied hält die Form der Fläche
 * (zweite Ordnung, Höhe nach Battjes–Groenendijk, Verschiebung), so dass ψ(0) die Fläche des Bilds ist.
 * Gefälle −Im G · A, Geschwindigkeit Re U · A und Im W · sinh(k_b (z + h)) / sinh(k_b h).
 * <p>
 * <b>Ort und Zeit.</b> Die Stelle der Säule und der Ort der Probe liegen auseinander, und die Karte rechnet für die Zeit
 * ihres Bilds. Je Band dreht der Körper die Summen um e^{i (κ_b · δ − ω_b δt)}, κ_b in der Richtung von Re(G C0*) mit dem
 * Betrag k_b (die örtliche Welle des Bands). Je Säule und Zeit gilt der Ort der ersten Probe, die fragt; die übrigen Proben
 * der Säule nehmen dazu das Gefälle in erster Ordnung.
 * <p>
 * Nur für einen Faden (den, der den Körper rechnet).
 */
public final class Bandwasser implements Wasser {
    public static final String VERSION = "0.1";
    /** Bänder des Körpers höchstens [G]. */
    public static final int BAENDER = 6;
    /** Nummer des Körpers in der Anfrage. */
    public final int koerper;

    private Wasserpunkte.Stand stand;
    private int anfang = -1, anzahl;
    /** Je Säule und Band des Körpers: k_b, ω_b, cosh, sinh, tanh(k_b h), dann 7 komplexe Summen (C0, C1, Gx, Gy, Ux, Uy, W). */
    private double[] werte = new double[0];
    private double[] kopf = new double[0];                 // je Punkt: η, Re Z, h, k_p, x, y
    private double[] kielH = new double[0], kielK = new double[0], kielKopf = new double[0];   // seit S7d: Kielwasser je Punkt
    private int nbk;
    private static final int JE = 5 + 14;
    // je Säule der gedrehte Stand für eine Zeit und einen Ort
    private double[] cacheT = new double[0], cacheX = new double[0], cacheY = new double[0], gedreht = new double[0];

    private double pegel, stromU, stromV, windU, windV;
    private double[] grundSaeule;
    private double grundSonst = Double.NEGATIVE_INFINITY;

    public Bandwasser(int koerper) { this.koerper = koerper; }

    // seit S7b-4: je Säule der Punkt der Karte (null: je Säule ein Punkt, wie S7b) und je Punkt seine erste Säule
    private int[] zu, erste;

    /**
     * Seit S7b-4 (Seegang Werkbuch 89): je Säule der Nummer ihres Punkts in der Anfrage ({@link Koerper#kartenpunkt}).
     * Die Summen eines Punkts gelten dann für alle seine Säulen; je Säule dreht der Körper sie mit κ_b auf die Stelle der
     * ersten Probe, die fragt (wie für den Verzug). null: je Säule ein Punkt.
     */
    public void zuordnung(int[] punktJeSaeule) {
        if (punktJeSaeule == null) { zu = null; erste = null; return; }
        boolean gleich = true;
        int mx = -1;
        for (int c = 0; c < punktJeSaeule.length; c++) { if (punktJeSaeule[c] != c) gleich = false; mx = Math.max(mx, punktJeSaeule[c]); }
        if (gleich) { zu = null; erste = null; return; }
        zu = punktJeSaeule.clone();
        erste = new int[mx + 1];
        java.util.Arrays.fill(erste, -1);
        for (int c = 0; c < zu.length; c++) if (erste[zu[c]] < 0) erste[zu[c]] = c;
        stand(stand);
    }

    /** Setzt den Stand der Karte (null: ruhiges Wasser). */
    public void stand(Wasserpunkte.Stand s) {
        stand = s;
        anfang = -1;
        anzahl = 0;
        if (s == null || koerper >= s.anfrage.anfang.length || s.anfrage.anfang[koerper] < 0) return;
        anfang = s.anfrage.anfang[koerper];
        anzahl = s.anfrage.anzahl[koerper];
        int nb = s.nb;
        nbk = Math.min(BAENDER, nb);
        if (werte.length < anzahl * nbk * JE) werte = new double[anzahl * nbk * JE];
        if (kopf.length < anzahl * 6) kopf = new double[anzahl * 6];
        if (kpKopf.length < anzahl) kpKopf = new double[anzahl];
        if (kielH.length < anzahl) { kielH = new double[anzahl]; kielK = new double[anzahl]; kielKopf = new double[anzahl]; }
        int ns = zu == null ? anzahl : zu.length;                            // Säulen (je eine im Zwischenspeicher)
        if (cacheT.length < ns) {
            cacheT = new double[ns]; cacheX = new double[ns]; cacheY = new double[ns];
            gedreht = new double[ns * nbk * 14];
        } else if (gedreht.length < ns * nbk * 14) gedreht = new double[ns * nbk * 14];
        java.util.Arrays.fill(cacheT, 0, ns, Double.NaN);
        double[] roh = new double[16];
        for (int c = 0; c < anzahl; c++) {
            int i = anfang + c, o = i * s.je;
            float[] w = s.werte;
            double h = Math.max(0.05, w[o + 4]);
            kopf[6 * c] = w[o];
            kopf[6 * c + 1] = w[o + 1];
            kopf[6 * c + 2] = h;
            kopf[6 * c + 3] = w[o + 5];
            kopf[6 * c + 4] = s.anfrage.ost[i];
            kopf[6 * c + 5] = s.anfrage.nord[i];
            for (int g = 0; g < nbk; g++) {
                int b0 = g * nb / nbk, b1 = (g + 1) * nb / nbk;
                java.util.Arrays.fill(roh, 0);
                double om = 0;
                for (int b = b0; b < b1; b++) {
                    int ob = o + Wasserpunkte.KOPF + Wasserpunkte.JE_BAND * b;
                    for (int j = 0; j < 16; j++) roh[j] += w[ob + j];
                    om += w[ob] * s.omega[b];
                }
                double sa = roh[0], kb = sa > 0 ? roh[1] / sa : 0;
                om = sa > 0 ? om / sa : 0.5 * (s.omega[b0] + s.omega[b1 - 1]);
                int q = (c * nbk + g) * JE;
                werte[q] = kb;
                werte[q + 1] = om;
                double kh = Math.min(30, kb * h), sh = Math.sinh(kh);
                werte[q + 2] = 1 / Math.cosh(kh);
                werte[q + 3] = sh > 1e-12 ? 1 / sh : 0;
                werte[q + 4] = Math.tanh(kh);
                // C0, C1 = K1 − k_b C0, Gx, Gy, Ux, Uy, W
                werte[q + 5] = roh[2]; werte[q + 6] = roh[3];
                werte[q + 7] = roh[4] - kb * roh[2]; werte[q + 8] = roh[5] - kb * roh[3];
                for (int j = 0; j < 10; j++) werte[q + 9 + j] = roh[6 + j];
                saBand[g] = sa;
            }
            // Bänder mit sehr wenig Welle lässt der Körper weg (unter 1e-3 der Summe) [G]
            double sum = 0;
            for (int g = 0; g < nbk; g++) sum += saBand[g];
            for (int g = 0; g < nbk; g++) if (saBand[g] < 1e-3 * sum) werte[(c * nbk + g) * JE] = 0;
            double kp = kopf[6 * c + 3];
            kpKopf[c] = kp > 0 ? 1 / Math.cosh(Math.min(30, kp * h)) : 0;
            // seit S7d: das Kielwasser am Punkt, mit seiner eigenen Wellenzahl abklingend (Wasserpunkte.KOPF ≥ 10)
            if (Wasserpunkte.KOPF >= 10) {
                kielH[c] = w[o + 8];
                kielK[c] = w[o + 9];
                kielKopf[c] = kielK[c] > 0 ? 1 / Math.cosh(Math.min(30, kielK[c] * h)) : 0;
            }
        }
    }

    private final double[] saBand = new double[BAENDER];
    private double[] kpKopf = new double[0];

    /** Der Stand, mit dem der Körper rechnet (null: keiner). */
    public Wasserpunkte.Stand stand() { return stand; }

    /** Pegel (Ruhewasser, m NHN), Strom und Wind (m/s, nach Ost und Nord) für die nächsten Schritte. */
    public void setze(double pegel, double stromU, double stromV, double windU, double windV) {
        this.pegel = pegel;
        this.stromU = stromU; this.stromV = stromV;
        this.windU = windU; this.windV = windV;
    }

    /** Grund je Säule (m NHN; null: {@link #grundSonst(double)} überall). */
    public void grund(double[] jeSaeule) { grundSaeule = jeSaeule; }

    /** Grund, wo keine Säule ihn kennt (m NHN). */
    public void grundSonst(double g) { grundSonst = g; }

    /** Grund an jedem Ort (m NHN), etwa für die Kette; null: {@link #grundSonst(double)}. Nur im Faden des Körpers rufen. */
    public void grundFunktion(java.util.function.DoubleBinaryOperator f) { grundF = f; }

    private java.util.function.DoubleBinaryOperator grundF;

    @Override public double pegel(double x, double y, double t) { return pegel; }

    @Override public void strom(double x, double y, double t, double[] out) { out[0] = stromU; out[1] = stromV; }

    @Override public void wind(double x, double y, double t, double[] out) { out[0] = windU; out[1] = windV; }

    @Override public double grund(double x, double y) {
        if (grundF != null) {
            double g = grundF.applyAsDouble(x, y);
            return Double.isNaN(g) ? grundSonst : g;
        }
        if (grundSaeule == null) return grundSonst;
        int c = naechste(x, y);
        return c < 0 || c >= grundSaeule.length ? grundSonst : grundSaeule[c];
    }

    @Override public double grund(int saeule, double x, double y) {
        if (grundSaeule == null || saeule < 0 || saeule >= grundSaeule.length) return grund(x, y);
        return grundSaeule[saeule];
    }

    @Override public void welle(double x, double y, double z, double t, double[] out) { welle(naechste(x, y), x, y, z, t, out); }

    /** Die Säule, deren Stelle (x, y) am nächsten liegt (−1 ohne Stand); mit {@link #zuordnung} die erste des nächsten Punkts. */
    int naechste(double x, double y) {
        int best = -1;
        double bd = Double.POSITIVE_INFINITY;
        for (int c = 0; c < anzahl; c++) {
            double d = (kopf[6 * c + 4] - x) * (kopf[6 * c + 4] - x) + (kopf[6 * c + 5] - y) * (kopf[6 * c + 5] - y);
            if (d < bd) { bd = d; best = c; }
        }
        if (zu != null && best >= 0) return best < erste.length ? erste[best] : -1;
        return best;
    }

    @Override public void welle(int c, double x, double y, double z, double t, double[] out) {
        for (int i = 0; i < 6; i++) out[i] = 0;
        if (anfang < 0 || c < 0) return;
        int m = c;                                                           // der Punkt der Karte zur Säule c
        if (zu != null) { if (c >= zu.length) return; m = zu[c]; }
        if (m >= anzahl) return;
        if (z > 0) z = 0;
        int nb = nbk;
        if (!(cacheT[c] == t)) drehen(c, m, x, y, t);
        double h = kopf[6 * m + 2];
        double zh = Math.max(0, z + h);
        double psi = 0, gx = 0, gy = 0, u = 0, v = 0, w = 0;
        for (int g = 0; g < nb; g++) {
            int q = (m * nb + g) * JE, r = (c * nb + g) * 14;
            double kb = werte[q];
            if (!(kb > 0)) continue;
            double a, sh, d;
            if (z >= 0) { a = 1; sh = 1; d = 0; }
            else {
                double e = Math.exp(Math.min(30, kb * zh)), ei = 1 / e;
                double ch = 0.5 * (e + ei), sn = 0.5 * (e - ei);
                a = ch * werte[q + 2];
                sh = sn * werte[q + 3];
                d = zh * (sn / ch) - h * werte[q + 4];
            }
            psi += a * (gedreht[r] + d * gedreht[r + 2]);
            gx -= a * gedreht[r + 5];
            gy -= a * gedreht[r + 7];
            u += a * gedreht[r + 8];
            v += a * gedreht[r + 10];
            w += sh * gedreht[r + 13];
        }
        // seit S7d: das Kielwasser, mit seiner Wellenzahl abklingend
        double kh = kielH[m];
        if (kh != 0) {
            double ak = 1, kk = kielK[m];
            if (z < 0 && kk > 0) {
                double e = Math.exp(Math.min(30, kk * zh));
                ak = 0.5 * (e + 1 / e) * kielKopf[m];
            }
            psi += kh * ak;
        }
        // die Form der Fläche über dem linearen Anteil (ohne das Kielwasser), mit k_p abklingend
        double kor = kopf[6 * m] - kopf[6 * m + 1] - kh, kp = kopf[6 * m + 3];
        if (kor != 0) {
            double ak = 1;
            if (z < 0 && kp > 0) {
                double e = Math.exp(Math.min(30, kp * zh));
                ak = 0.5 * (e + 1 / e) * kpKopf[m];
            }
            psi += kor * ak;
        }
        // die übrigen Proben der Säule: Gefälle in erster Ordnung
        double dx = x - cacheX[c], dy = y - cacheY[c];
        psi += dx * gx + dy * gy;
        out[0] = psi; out[1] = gx; out[2] = gy; out[3] = u; out[4] = v; out[5] = w;
    }

    /** Dreht die Summen des Punkts m auf den Ort (x, y) und die Zeit t, für die Säule c. */
    private void drehen(int c, int m, double x, double y, double t) {
        cacheT[c] = t;
        cacheX[c] = x;
        cacheY[c] = y;
        double dx = x - kopf[6 * m + 4], dy = y - kopf[6 * m + 5], dt = t - stand.t;
        for (int g = 0; g < nbk; g++) {
            int q = (m * nbk + g) * JE, r = (c * nbk + g) * 14;
            double kb = werte[q], om = werte[q + 1];
            double c0r = werte[q + 5], c0i = werte[q + 6], gxr = werte[q + 9], gxi = werte[q + 10], gyr = werte[q + 11], gyi = werte[q + 12];
            // örtliche Welle des Bands: Richtung von Re(G C0*), Betrag k_b
            double kx = gxr * c0r + gxi * c0i, ky = gyr * c0r + gyi * c0i, kl = Math.hypot(kx, ky);
            double ph = (kl > 0 ? kb * (kx * dx + ky * dy) / kl : 0) - om * dt;
            double co = Math.cos(ph), si = Math.sin(ph);
            for (int j = 0; j < 7; j++) {
                double re = werte[q + 5 + 2 * j], im = werte[q + 6 + 2 * j];
                gedreht[r + 2 * j] = re * co - im * si;
                gedreht[r + 2 * j + 1] = re * si + im * co;
            }
        }
    }
}
