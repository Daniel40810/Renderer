package com.dan.gertsch;

import java.awt.image.BufferedImage;
import java.util.stream.IntStream;

/**
 * Holzschnitt nach Gertsch aus einem beliebigen Bild: eine Farbe auf Papier, das Licht allein aus der Dichte
 * gestochener Punkte, dazu Wolken im Farbauftrag und das Korn des Papiers. Er braucht nur die Helligkeit eines Bilds und
 * passt darum auf jede Ausgabe, auch auf Szenen anderer Pakete (ein PNG genügt). {@code gl/StichGl} rechnet dasselbe
 * auf der Grafikkarte, jedes Bild neu und mit Hysterese der Punkte.
 * <p>
 * <b>Ablauf.</b>
 * <ol>
 * <li><b>Helligkeit</b> Y (linear) je Bildpunkt der Quelle, Histogramm über log2 Y wie {@code GertschGl}.</li>
 * <li><b>Ton</b> = Anteil Papier, aus dem Histogramm als Tabelle ({@link #tonKurve}):
 * <ul>
 * <li>{@link Tonwerte#VORLAGE}: nach dem Rang der Helligkeit auf die Perzentile des Tons der Vorlage
 * ({@link Druck#ton}). So bekommt der Druck so viel Papier wie Gertschs Holzschnitt, und hell bleibt hell.</li>
 * <li>{@link Tonwerte#LINEAR}: der Anteil Papier, der dieselbe Helligkeit gibt wie die Szene (Weiß = 99,5 % der
 * Helligkeit = Papier).</li>
 * </ul></li>
 * <li><b>Punkte</b> ({@link Punkte}): Zellen von {@code punktPx / groesse} Bildpunkten; der Punkt einer Zelle wird
 * gestochen, wenn der Ton an seiner Mitte über der Schwelle seines Rangs liegt.</li>
 * <li><b>Farbe:</b> je Bildpunkt Papier, wo ein Punkt ihn deckt (Kante über die Abdeckung), sonst Farbe. Die Farbe
 * ist eine Schicht der Dicke d: Reflexion = Papier · (Farbe / Papier)^d je Kanal, d = 1 + Wolken + Korn. Die Wolken
 * sind ein Rauschen mit senkrecht langen Streifen und breiten Bahnen, wie der Abzug mit dem Reiber (Baren) sie
 * hinterlässt; gemessen in der Vorlage (Autokorrelation senkrecht viel länger als waagerecht).</li>
 * </ol>
 * Größen sind Anteile der Druckbreite. Alle Felder sind offen; die Vorgaben sind gegen {@link Vorlagen#druck()}
 * eingestellt (siehe README, Phase 4).
 */
public final class Stich {
    /** Wie die Helligkeit zum Ton wird. */
    public enum Tonwerte { VORLAGE, LINEAR }

    /** Bereich des Histogramms in log2 Y (wie GertschGl) und Zahl der Fächer. */
    public static final double LO = -24, HI = 4;
    public static final int FAECHER = 256;

    /** Kennwerte des Drucks, nach dem gedruckt wird: Papier, Farbe, Punktgröße, Ton. */
    public Druck druck = Vorlagen.druck();
    public final Punkte punkte = new Punkte();
    {
        punkte.ordnung = 0.75;   // die dünnen Gegenden bleiben zufällig wie bei Gertsch (R wie Zufall), die Mitteltöne werden ruhiger
    }
    public Tonwerte tonwerte = Tonwerte.VORLAGE;
    /** Durchmesser eines Punkts als Anteil der Druckbreite (negativ: aus dem Druck), aber mindestens {@link #punktMin} px. */
    public double punkt = -1, punktMin = 1.5;
    /**
     * Wolken: Streuung der Farbdicke; Breite und Länge der Streifen (Anteile der Breite); Anteile der breiten Bahnen
     * (5-mal so breit, 1,5-mal so lang) und der runden Flecken (Größe {@link #wolkenFleck}) an der Streuung.
     */
    public double wolken = 0.23, wolkenBreite = 0.14, wolkenLaenge = 0.7, wolkenBahnen = 0.45, wolkenFlecken = 0.08, wolkenFleck = 0.03;
    /** Korn: Streuung der Farbdicke je Bildpunkt (gleichverteilt ± korn). */
    public double korn = 0.058;
    /** Farbe und Papier in Lab; null: aus dem Druck. */
    public double[] farbe, papier;
    /** seed der Wolken und des Korns (die Punkte haben ihren eigenen in {@link #punkte}). */
    public int seed = 1;

    /** Ergebnis von {@link #stechen}. */
    public static final class Ergebnis {
        public BufferedImage bild;
        public int breite, hoehe;
        /** Zellen (mögliche Punkte) und gestochene Punkte. */
        public int punkte, gestochen;
        /** Durchmesser eines Punkts und Kante einer Zelle (px), Sekunden. */
        public double punktPx, zellePx, sekunden;
        /** Die Tabelle des Tons (für Prüfungen und die Grafikkarte). */
        public float[] tonKurve;
    }

    // ------------------------------------------------------------------ Helligkeit und Ton

    /** Lineare Helligkeit Y je Bildpunkt (zeilenweise, oben zuerst). */
    public static float[] helligkeit(BufferedImage bild) {
        int w = bild.getWidth(), h = bild.getHeight();
        int[] p = bild.getRGB(0, 0, w, h, null, 0, w);
        float[] y = new float[w * h];
        IntStream.range(0, h).parallel().forEach(r -> {
            for (int x = 0; x < w; x++) {
                int c = p[r * w + x];
                y[r * w + x] = (float) (0.2126 * Lab.linear(c >> 16) + 0.7152 * Lab.linear(c >> 8) + 0.0722 * Lab.linear(c));
            }
        });
        return y;
    }

    /** Fach von Y wie histogramm.comp: 0 ohne Licht, sonst 1 + 256 gleich breite Fächer in log2 Y. */
    public static int fach(float y) {
        if (y <= 0) return 0;
        return 1 + (int) Math.max(0, Math.min(FAECHER - 1, (Math.log(y) / Math.log(2) - LO) / (HI - LO) * FAECHER));
    }

    public static long[] histogramm(float[] y) {
        long[] f = new long[FAECHER + 1];
        for (float v : y) f[fach(v)]++;
        return f;
    }

    /**
     * Tabelle des Tons aus dem Histogramm: [0] für Y ≤ 0, [1 + k] an der Grenze k der Fächer (k = 0 … 256); dazwischen
     * linear. VORLAGE: Summenverteilung F an den Grenzen (innerhalb eines Fachs gleichverteilt), Ton = Perzentil F des
     * Drucks. LINEAR: Anteil Papier, der die Helligkeit Y / Y(99,5 %) gibt.
     */
    public float[] tonKurve(long[] fach) {
        float[] t = new float[FAECHER + 2];
        long n = 0;
        for (long f : fach) n += f;
        if (n == 0) return t;
        if (tonwerte == Tonwerte.VORLAGE) {
            t[0] = (float) druck.tonBei(0.5 * fach[0] / n);
            long s = fach[0];
            for (int k = 0; k <= FAECHER; k++) {
                t[1 + k] = (float) druck.tonBei(s / (double) n);
                if (k < FAECHER) s += fach[1 + k];
            }
        } else {
            // Y bei 99,5 %, aus den Grenzen linear
            double ziel = 0.995 * n, s = fach[0], weiss = Math.pow(2, HI);
            for (int k = 0; k < FAECHER; k++) {
                if (s + fach[1 + k] >= ziel) {
                    double f = fach[1 + k] == 0 ? 0 : (ziel - s) / fach[1 + k];
                    weiss = Math.pow(2, LO + (k + f) * (HI - LO) / FAECHER);
                    break;
                }
                s += fach[1 + k];
            }
            double ri = yVon(farbeLab()) / yVon(papierLab());
            t[0] = 0;
            for (int k = 0; k <= FAECHER; k++) {
                double y = Math.pow(2, LO + k * (HI - LO) / FAECHER) / weiss;
                t[1 + k] = (float) Math.max(0, Math.min(1, (y - ri) / (1 - ri)));
            }
        }
        return t;
    }

    /** Ton zur Helligkeit y aus der Tabelle (wie {@code ton()} in stich.glsl). */
    public static float ton(float[] kurve, float y) {
        if (y <= 0) return kurve[0];
        float x = (float) ((Math.log(y) / Math.log(2) - LO) / (HI - LO) * FAECHER);
        x = Math.max(0, Math.min(FAECHER, x));
        int k = Math.min(FAECHER - 1, (int) x);
        return kurve[1 + k] + (x - k) * (kurve[2 + k] - kurve[1 + k]);
    }

    static double yVon(double[] lab) {
        double[] o = new double[3];
        Lab.zuLinear(lab[0], lab[1], lab[2], o);
        return 0.2126 * o[0] + 0.7152 * o[1] + 0.0722 * o[2];
    }

    public double[] farbeLab() { return farbe != null ? farbe : druck.farbe; }
    public double[] papierLab() { return papier != null ? papier : druck.papier; }

    /** Durchmesser eines Punkts (px) bei der Druckbreite. */
    public double punktPx(int breite) { return Math.max(punktMin, (punkt > 0 ? punkt : druck.punkt) * breite); }

    // ------------------------------------------------------------------ Wolken und Korn (wie stich.glsl)

    /**
     * Gradientenrauschen nach Perlin (2002), etwa in [−1, 1]: je Gitterpunkt eine Richtung aus dem PCG-Hash, quintisch
     * geglättet. Anders als Wertrauschen zeigt es keine Kästchen des Gitters.
     */
    public static float rauschen(float x, float y, int s) {
        float fx = (float) Math.floor(x), fy = (float) Math.floor(y);
        int ix = (int) fx, iy = (int) fy;
        float tx = x - fx, ty = y - fy;
        float a = gitter(ix, iy, s, tx, ty), b = gitter(ix + 1, iy, s, tx - 1, ty), c = gitter(ix, iy + 1, s, tx, ty - 1),
                d = gitter(ix + 1, iy + 1, s, tx - 1, ty - 1);
        tx = tx * tx * tx * (tx * (tx * 6 - 15) + 10);
        ty = ty * ty * ty * (ty * (ty * 6 - 15) + 10);
        float u = a + (b - a) * tx, v = c + (d - c) * tx;
        return 1.4f * (u + (v - u) * ty);
    }

    static float gitter(int ix, int iy, int s, float dx, float dy) {
        float w = 6.2831853f * Zufall.eins(ix + Zufall.pcg(iy + s));
        return (float) Math.cos(w) * dx + (float) Math.sin(w) * dy;
    }

    /**
     * Wolken an (u, v) in Anteilen der Breite (v nach unten), Streuung etwa 1: senkrechte Streifen, breite Bahnen und
     * runde Flecken, unabhängig, nach ihren Anteilen an der Streuung gemischt.
     */
    public float wolken(float u, float v) {
        int s = Zufall.pcg(seed * 0x2545F491 + 17);
        float bw = (float) wolkenBreite, bl = (float) wolkenLaenge, gb = (float) wolkenBahnen, gf = (float) wolkenFlecken, ff = (float) wolkenFleck;
        // Streifen: quer drei Oktaven (dünne Striche neben breiten), längs glatt; Bahnen: breit und lang; Flecken: rund, fein
        float streifen = 0.85f * rauschen(u / bw, v / bl, s) + 0.35f * rauschen(2 * u / bw, 1.3f * v / bl, s ^ 0x7FEB352D)
                + 0.15f * rauschen(4 * u / bw, 1.6f * v / bl, s ^ 0x846CA68B);
        float bahnen = rauschen(u / (5 * bw), v / (1.5f * bl), s ^ 0x68E31DA4), flecken = rauschen(u / ff, v / ff, s ^ 0x1B873593);
        float gs = Math.max(0, 1 - gb - gf);
        return 2.4f * ((float) Math.sqrt(gs) * streifen + (float) Math.sqrt(gb) * bahnen + (float) Math.sqrt(gf) * flecken);
    }

    /** Korn des Bildpunkts (x, y) des Drucks, gleichverteilt in [−1, 1). */
    public float korn(int x, int y) { return 2 * Zufall.eins(x + Zufall.pcg(y + Zufall.pcg(seed + 0x3C6EF372))) - 1; }

    /**
     * Ausgleich der Farbe je Kanal (ln), damit die Farbe im Mittel über den Druck die des Drucks bleibt: Die Schicht
     * Papier · (Farbe / Papier)^d wird mit schwankender Dicke d im Mittel heller (e^x ist gewölbt). c = ln E[e^((d − 1) L)]
     * mit L = ln(Farbe / Papier); E über die Wolken (64 × 64 Proben auf dem Druck) mal E über das Korn (gleichverteilt:
     * sinh(kL) / kL).
     */
    public float[] ausgleich(double hoeheZuBreite) {
        double[] pap = new double[3], far = new double[3];
        double[] pl = papierLab(), fl = farbeLab();
        Lab.zuLinear(pl[0], pl[1], pl[2], pap);
        Lab.zuLinear(fl[0], fl[1], fl[2], far);
        float[] w = new float[64 * 64];
        for (int j = 0; j < 64; j++)
            for (int i = 0; i < 64; i++) w[j * 64 + i] = wolken((i + 0.5f) / 64, (float) ((j + 0.5) / 64 * hoeheZuBreite));
        float[] c = new float[3];
        for (int k = 0; k < 3; k++) {
            double l = Math.log(Math.max(1e-6, far[k]) / Math.max(1e-6, pap[k])), ew = 0, kl = korn * l;
            for (float v : w) ew += Math.exp(wolken * v * l);
            ew /= w.length;
            double ek = Math.abs(kl) < 1e-9 ? 1 : Math.sinh(kl) / kl;
            c[k] = (float) Math.log(ew * ek);
        }
        return c;
    }

    // ------------------------------------------------------------------ Stechen

    /** Druckt ein Bild als Holzschnitt, {@code breite} Bildpunkte breit (Seitenverhältnis bleibt). */
    public Ergebnis stechen(BufferedImage quelle, int breite) {
        return stechen(helligkeit(quelle), quelle.getWidth(), quelle.getHeight(), breite);
    }

    /** Druckt aus linearer Helligkeit (zeilenweise, oben zuerst, w × h), etwa dem Licht von {@link Malweise}. */
    public Ergebnis stechen(float[] y, int w, int h, int breite) {
        return stechen(y, w, h, breite, tonKurve(histogramm(y)));
    }

    /** Wie oben, mit einer gegebenen Tabelle des Tons (etwa der, die die Grafikkarte gerade nimmt). */
    public Ergebnis stechen(float[] y, int w, int h, int breite, float[] tonKurve) {
        long t0 = System.nanoTime();
        int pw = breite, ph = (int) Math.round(breite * (double) h / w);
        double punktPx = punktPx(pw);
        float s = (float) (punktPx / punkte.groesse);
        int reich = punkte.reichweite(), cw = (int) Math.ceil(pw / s) + 2 * reich + 1, ch = (int) Math.ceil(ph / s) + 2 * reich + 1;
        float[] kurve = punkte.kurve();
        // Zellen von −reich bis …: Form und ob gestochen
        float[][] fo = new float[cw * ch][];
        boolean[] gest = new boolean[cw * ch];
        int[] zahl = new int[ch];
        IntStream.range(0, ch).parallel().forEach(r -> {
            float[] f = new float[6];
            int j = r - reich, z = 0;
            for (int c = 0; c < cw; c++) {
                int i = c - reich;
                punkte.form(i, j, f);
                float ton = ton(tonKurve, bilinear(y, w, h, f[0] * s / pw * w - 0.5f, f[1] * s / ph * h - 0.5f));
                if (ton > Punkte.schwelle(kurve, punkte.rang(i, j))) {
                    gest[r * cw + c] = true;
                    float[] g = java.util.Arrays.copyOf(f, 7);
                    g[6] = Punkte.einzug(f, s, 1);
                    fo[r * cw + c] = g;
                    z++;
                }
            }
            zahl[r] = z;
        });
        double[] pap = new double[3], far = new double[3];
        double[] pl = papierLab(), fl = farbeLab();
        Lab.zuLinear(pl[0], pl[1], pl[2], pap);
        Lab.zuLinear(fl[0], fl[1], fl[2], far);
        float[] lnVerh = new float[3], papF = new float[3], aus = ausgleich(ph / (double) pw);
        for (int c = 0; c < 3; c++) {
            papF[c] = (float) Math.max(1e-6, pap[c]);
            lnVerh[c] = (float) Math.log(Math.max(1e-6, far[c]) / papF[c]);
        }
        int[] px = new int[pw * ph];
        float kw = (float) wolken, kk = (float) korn;
        IntStream.range(0, ph).parallel().forEach(yy -> {
            for (int xx = 0; xx < pw; xx++) {
                float qx = (xx + 0.5f) / s, qy = (yy + 0.5f) / s;
                int ci = (int) Math.floor(qx), cj = (int) Math.floor(qy);
                float deckt = 0;
                for (int dj = -reich; dj <= reich; dj++)
                    for (int di = -reich; di <= reich; di++) {
                        int c = (cj + dj + reich) * cw + ci + di + reich;
                        if (!gest[c]) continue;
                        float d = Punkte.abstand(qx, qy, fo[c]) * s + fo[c][6];
                        deckt += Math.max(0, Math.min(1, 0.5f - d));   // Summe: an Kanten, wo Punkte sich berühren, fast genau die Vereinigung
                    }
                deckt = Math.min(1, deckt);
                float k = korn(xx, yy);
                float dicke = 1 + kw * wolken((xx + 0.5f) / pw, (yy + 0.5f) / pw) + kk * k;
                int rgb = 0;
                for (int c = 0; c < 3; c++) {
                    float papier = papF[c] * (1 + 0.5f * kk * k);
                    float farbe = papF[c] * (float) Math.exp(dicke * lnVerh[c] - aus[c]);
                    rgb = (rgb << 8) | Lab.srgb8(deckt * papier + (1 - deckt) * farbe);
                }
                px[yy * pw + xx] = rgb;
            }
        });
        Ergebnis e = new Ergebnis();
        e.bild = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
        e.bild.setRGB(0, 0, pw, ph, px, 0, pw);
        e.breite = pw;
        e.hoehe = ph;
        e.punkte = (int) Math.ceil(pw / s) * (int) Math.ceil(ph / s);
        for (int z : zahl) e.gestochen += z;
        e.punktPx = punktPx;
        e.zellePx = s;
        e.tonKurve = tonKurve;
        e.sekunden = (System.nanoTime() - t0) / 1e9;
        return e;
    }

    /** Bilinear in der Helligkeit (Bildpunktmitten bei ganzen Zahlen), am Rand begrenzt; wie stich_punkte.comp. */
    public static float bilinear(float[] y, int w, int h, float x, float yy) {
        x = Math.max(0, Math.min(w - 1, x));
        yy = Math.max(0, Math.min(h - 1, yy));
        int x0 = Math.min(w - 2, (int) x), y0 = Math.min(h - 2, (int) yy);
        if (w == 1) x0 = 0;
        if (h == 1) y0 = 0;
        float fx = x - x0, fy = yy - y0;
        int x1 = Math.min(w - 1, x0 + 1), y1 = Math.min(h - 1, y0 + 1);
        float a = y[y0 * w + x0], b = y[y0 * w + x1], c = y[y1 * w + x0], d = y[y1 * w + x1];
        float u = a + (b - a) * fx, v = c + (d - c) * fx;
        return u + (v - u) * fy;
    }
}
