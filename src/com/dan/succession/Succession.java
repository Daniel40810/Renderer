package com.dan.succession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Störung und Wiederbewaldung (seit LituyaBay1958 Phase 14, Werkbuch 5.6, 9.7) als Zellautomat auf dem Pflanzgitter eines
 * Walds: ein Platz je {@code spacing} m (wie {@code forest.TreeStates}, Platz (i, j) liegt bei
 * [i·Abstand, (i+1)·Abstand) × [j·Abstand, (j+1)·Abstand)), je Platz ein Hauptbaum und ein Baum im Unterstand.
 * <ol>
 * <li><b>Störung</b> ({@link #stoeren}): wo der höchste Wasserstand über dem Gelände lag, ist das Band. Jeder Platz bekommt
 * seinen Altbaum (Art aus dem Bestand) und dessen Schicksal aus {@link Wellenwurf} mit der Tiefe und u = Fr·√(g·h);
 * Fallrichtung hangab (der Rückfluss), Boden danach Fels, Mineralboden oder Waldboden. Tote Reste stehen oder liegen
 * noch Jahre (entrindet 20–45, Stumpf 10–30, liegender Stamm 30–60 Jahre [G]).</li>
 * <li><b>Jahre</b> ({@link #rechnen}): Samen vom Altbestand am Rand und von reifen Bäumen im Band, ausgebreitet mit der
 * Flugweite der Art (auf einem groben Gitter zu 4 × 4 Plätzen, drei Kastenfilter ≈ Gauß, dazu 15 % fünfmal so weit); Anwuchs auf freien Plätzen nach
 * Samen, Boden und Licht (Lichtbäume brauchen Licht, Schattenbäume keimen auch im Unterstand); Wuchs nach Chapman-Richards,
 * im Schatten gebremst; Sterben nach Lebensdauer und, für Lichtbäume, im Schatten; überwächst der Unterstand den
 * Hauptbaum, tauschen sie. Fels verwittert zu Mineralboden, Mineralboden unter Kronen wird Waldboden.</li>
 * <li><b>Ausgabe</b>: je Jahr Art und Höhe des Hauptbaums (Verlauf im Speicher), {@link #zustand} schreibt einen Jahrgang in
 * eine {@link Ausgabe} (die App setzt daraus {@code forest.TreeStates} mit Nachwuchs), {@link #statistik} für Prüfung und
 * Abgleich (Kronenhöhe im Band gegen außerhalb).</li>
 * </ol>
 * Alles ist mit einem festen Startwert zufällig (PCG je Platz und Jahr): zwei Läufe geben dasselbe. Das Package kennt
 * keine App und kein anderes Package; Gelände, Wasser und Bestand kommen über kleine Schnittstellen.
 */
public final class Succession {
    /** Höhe des Geländes (m). */
    public interface Gelaende { double height(double x, double z); }

    /** Höchster Wasserstand der Welle (m, NaN: trocken). */
    public interface Wasser { double level(double x, double z); }

    /** Altbestand: Anteil der Art am Platz (Summe ≤ 1, der Rest ist offen). */
    public interface Bestand { double share(double x, double z, int art); }

    /**
     * Ein Platz eines Jahrgangs: Zustand wie {@code forest.TreeStates} (0 stehend, 1 gebrochen, 2 entrindet, 3 umgelegt,
     * 4 fort), Fallrichtung (Kompass), Art des lebenden Baums (−1: keiner) und seine Höhe (m).
     */
    public interface Ausgabe { void platz(int i, int j, int zustand, double fallDeg, int art, double hoehe); }

    public static final int STEHEND = 0, GEBROCHEN = 1, ENTRINDET = 2, UMGELEGT = 3, FORT = 4;
    /** Grobes Gitter für Samen und Kronendach: so viele Plätze je Kante. */
    public static final int GROB = 4;

    public final double spacing;
    public final int i0, j0, w, h;
    public final List<Art> arten;
    public final long seed;
    /** Froude-Zahl der Strömung über Land [G]. */
    public double froude = 1.5;
    /** Meeresspiegel (m): Plätze darunter sind Wasser. */
    public double pegel = 0;
    /** Höhe des Altbestands (m) [G]. */
    public double altHoehe = 40;
    /** Fels verwittert zu Mineralboden (je Jahr), Mineralboden unter Kronen wird Waldboden (je Jahr) [G]. */
    public double verwittern = 0.04, humus = 0.03;
    /** Sterben im Schatten für Lichtbäume (je Jahr, mal 1 − Toleranz) [G]. */
    public double schattenTod = 0.06;
    /** Fernausbreitung: Anteil und Faktor der Weite [G]. */
    public double fernAnteil = 0.15, fernWeit = 5;
    /** Ab dieser Höhe (m) zählt ein Baum zum Kronendach. */
    public double dachAb = 2;

    // ------------------------------------------------------------------ Band
    private int n;
    private int[] lage;
    private float[] tiefe, fall, hang;
    private byte[] folge, altArt, boden0;
    private float[] altH;
    private short[] restBis;
    private boolean[] land;

    // ------------------------------------------------------------------ Verlauf
    private final List<byte[]> histArt = new ArrayList<>(), histH = new ArrayList<>(), histBoden = new ArrayList<>();
    private int jahre = -1;

    // ------------------------------------------------------------------ grob
    private int gw, gh;
    private float[][] altSaat;
    private float[] altDach, altDecke;
    private long nsStoerung, nsJahre;

    public Succession(double spacing, int i0, int j0, int w, int h, List<Art> arten, long seed) {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("leeres Gitter");
        if (arten.isEmpty() || arten.size() > 15) throw new IllegalArgumentException("1 bis 15 Arten");
        this.spacing = spacing;
        this.i0 = i0;
        this.j0 = j0;
        this.w = w;
        this.h = h;
        this.arten = List.copyOf(arten);
        this.seed = seed;
    }

    /** Ein Gitter über das Rechteck (x0, z0) … (x1, z1) in Metern. */
    public static Succession covering(double x0, double z0, double x1, double z1, double spacing, List<Art> arten, long seed) {
        int a = (int) Math.floor(Math.min(x0, x1) / spacing), b = (int) Math.floor(Math.min(z0, z1) / spacing);
        int c = (int) Math.floor(Math.max(x0, x1) / spacing), d = (int) Math.floor(Math.max(z0, z1) / spacing);
        return new Succession(spacing, a, b, c - a + 1, d - b + 1, arten, seed);
    }

    // ------------------------------------------------------------------ Zufall

    static int pcg(int v) {
        int s = v * 747796405 + (int) 2891336453L;
        int x = ((s >>> ((s >>> 28) + 4)) ^ s) * 277803737;
        return (x >>> 22) ^ x;
    }

    private int seed32() { return (int) (seed ^ (seed >>> 32)); }

    /** Gleichverteilt in [0, 1) für Platz (i, j), Jahr und Zweck. */
    double u01(int i, int j, int jahr, int k) {
        int hsh = pcg(i * 1597334677 ^ j * (int) 3812015801L ^ seed32());
        hsh = pcg(hsh + jahr * (int) 0x9E3779B9L);
        return (pcg(hsh + k * 0x632BE5AB) >>> 8) * 0x1.0p-24;
    }

    // ------------------------------------------------------------------ Störung

    /**
     * Das Band aus dem höchsten Wasserstand: Altbäume aus dem Bestand, ihr Schicksal aus der Strömung, Reste und Boden.
     * Danach Jahr 0. Plätze unter dem Pegel sind Wasser, außerhalb des Bands steht der Altbestand.
     */
    public void stoeren(Gelaende g, Wasser wa, Bestand b) {
        long t0 = System.nanoTime();
        int S = arten.size();
        gw = (w + GROB - 1) / GROB;
        gh = (h + GROB - 1) / GROB;
        altSaat = new float[S][gw * gh];
        altDach = new float[gw * gh];
        altDecke = new float[gw * gh];
        land = new boolean[gw * gh];
        int cap = 1024;
        int[] lg = new int[cap];
        float[] tf = new float[cap], fl = new float[cap], hg = new float[cap], ah = new float[cap];
        byte[] fo = new byte[cap], aa = new byte[cap], bo = new byte[cap];
        short[] rb = new short[cap];
        int k = 0;
        double sp = spacing;
        double[] sh = new double[S];
        for (int jj = 0; jj < h; jj++)
            for (int ii = 0; ii < w; ii++) {
                int i = i0 + ii, j = j0 + jj;
                double x = (i + 0.5) * sp, z = (j + 0.5) * sp, gz = g.height(x, z);
                if (!(gz > pegel)) continue;
                int c = (jj / GROB) * gw + ii / GROB;
                land[c] = true;
                double cov = 0;
                for (int s = 0; s < S; s++) { sh[s] = Math.max(0, b.share(x, z, s)); cov += sh[s]; }
                if (cov > 1) { for (int s = 0; s < S; s++) sh[s] /= cov; cov = 1; }
                double lev = wa.level(x, z), tf0 = lev - gz;
                if (!(tf0 > 0)) {
                    // Altbestand außerhalb: Saat und Dach
                    for (int s = 0; s < S; s++) altSaat[s][c] += (float) sh[s];
                    altDach[c] += (float) (altHoehe * cov);
                    altDecke[c] += (float) cov;
                    continue;
                }
                if (k == cap) {
                    cap *= 2;
                    lg = Arrays.copyOf(lg, cap); tf = Arrays.copyOf(tf, cap); fl = Arrays.copyOf(fl, cap); hg = Arrays.copyOf(hg, cap); ah = Arrays.copyOf(ah, cap);
                    fo = Arrays.copyOf(fo, cap); aa = Arrays.copyOf(aa, cap); bo = Arrays.copyOf(bo, cap); rb = Arrays.copyOf(rb, cap);
                }
                // Hang und Fallrichtung (hangab = Rückfluss)
                double gx = (g.height(x + sp, z) - g.height(x - sp, z)) / (2 * sp), gzz = (g.height(x, z + sp) - g.height(x, z - sp)) / (2 * sp);
                double hangDeg = Math.toDegrees(Math.atan(Math.hypot(gx, gzz)));
                double fallDeg = (Math.toDegrees(Math.atan2(-gx, gzz)) + 360) % 360;   // Kompass: Ost = +x, Nord = −z
                double u = Wellenwurf.geschwindigkeit(tf0, froude);
                double roll = u01(i, j, 0, 1);
                int art = -1;
                double ht = 0;
                if (roll < cov) {
                    double pick = roll / cov * cov;
                    art = 0;
                    while (art < S - 1 && pick > sh[art]) { pick -= sh[art]; art++; }
                    ht = Math.min(arten.get(art).hMax, altHoehe * (0.7 + 0.45 * u01(i, j, 0, 2)));
                }
                Wellenwurf.Schaden sd = art >= 0 ? Wellenwurf.wurf(tf0, u, ht, arten.get(art), hangDeg)
                        : new Wellenwurf.Schaden(tf0 >= Wellenwurf.abraumTiefe && u >= Wellenwurf.abraumU ? Wellenwurf.Folge.FORT : Wellenwurf.Folge.UNBERUEHRT,
                        tf0 >= Wellenwurf.abraumTiefe && u >= Wellenwurf.abraumU ? Boden.FELS : Boden.MINERAL, 0, u);
                double r2 = u01(i, j, 0, 3);
                int rest = switch (sd.folge()) {
                    case ENTRINDET -> 20 + (int) (r2 * 25);
                    case GEBROCHEN -> 10 + (int) (r2 * 20);
                    case UMGELEGT -> 30 + (int) (r2 * 30);
                    default -> 0;
                };
                lg[k] = jj * w + ii;
                tf[k] = (float) tf0;
                fl[k] = (float) fallDeg;
                hg[k] = (float) hangDeg;
                fo[k] = (byte) sd.folge().ordinal();
                aa[k] = (byte) art;
                ah[k] = (float) ht;
                bo[k] = (byte) sd.boden().ordinal();
                rb[k] = (short) rest;
                k++;
            }
        n = k;
        lage = Arrays.copyOf(lg, k); tiefe = Arrays.copyOf(tf, k); fall = Arrays.copyOf(fl, k); hang = Arrays.copyOf(hg, k);
        folge = Arrays.copyOf(fo, k); altArt = Arrays.copyOf(aa, k); altH = Arrays.copyOf(ah, k); boden0 = Arrays.copyOf(bo, k); restBis = Arrays.copyOf(rb, k);
        histArt.clear();
        histH.clear();
        histBoden.clear();
        jahre = -1;
        nsStoerung = System.nanoTime() - t0;
    }

    /** Plätze im Band. */
    public int band() { return n; }

    /** Gerechnete Jahre (−1: noch keins). */
    public int jahre() { return jahre; }

    // ------------------------------------------------------------------ Jahre

    /** Rechnet die Jahre 0 … jahre (0 ist der Zustand gleich nach der Welle). */
    public void rechnen(int bis) {
        if (folge == null) throw new IllegalStateException("erst stoeren()");
        long t0 = System.nanoTime();
        int S = arten.size();
        byte[] art = new byte[n], unter = new byte[n], bod = boden0.clone();
        float[] ho = new float[n], uh = new float[n];
        short[] geb = new short[n], ugeb = new short[n];
        Arrays.fill(unter, (byte) -1);
        for (int k = 0; k < n; k++) {
            boolean lebt = folge[k] == Wellenwurf.Folge.STEHT.ordinal();
            art[k] = lebt ? altArt[k] : -1;
            ho[k] = lebt ? altH[k] : 0;
            geb[k] = (short) (lebt ? -(int) Math.round(arten.get(altArt[k]).alterBei(altH[k])) : 0);
        }
        histArt.clear();
        histH.clear();
        histBoden.clear();
        merken(art, ho, bod);
        float[][] saat = new float[S][gw * gh];
        float[] dach = new float[gw * gh], decke = new float[gw * gh];
        float[] tmp = new float[gw * gh];
        double[] rate = new double[S];
        for (int y = 1; y <= bis; y++) {
            // Saat (aus dem Vorjahr) und Kronendach auf dem groben Gitter
            for (int s = 0; s < S; s++) {
                float[] q = saat[s];
                System.arraycopy(altSaat[s], 0, q, 0, q.length);
            }
            System.arraycopy(altDach, 0, dach, 0, dach.length);
            System.arraycopy(altDecke, 0, decke, 0, decke.length);
            for (int k = 0; k < n; k++) {
                int c = grob(k);
                if (art[k] >= 0) {
                    Art a = arten.get(art[k]);
                    if (y - geb[k] >= a.reife) saat[art[k]][c] += 1;
                    if (ho[k] >= dachAb) { dach[c] += ho[k]; decke[c] += 1; }
                }
            }
            for (int s = 0; s < S; s++) {
                // Kern der Ausbreitung: 85 % nah (Gauß mit der Flugweite), 15 % weit (fünffach) – Sturm und Vögel [G]
                double sigma = arten.get(s).weite / (GROB * spacing);
                float[] q = saat[s], fern = q.clone();
                blur(q, tmp, sigma);
                blur(fern, tmp, sigma * fernWeit);
                for (int c = 0; c < q.length; c++) q[c] = (float) ((1 - fernAnteil) * q[c] + fernAnteil * fern[c]);
            }
            float norm = 1f / (GROB * GROB);
            for (int k = 0; k < n; k++) {
                int c = grob(k), lk = lage[k], ii = lk % w, jj = lk / w, i = i0 + ii, j = j0 + jj;
                double fc = Math.min(1, decke[c] * norm), hc = decke[c] > 0 ? dach[c] / decke[c] : 0;
                // Boden
                if (bod[k] == Boden.FELS.ordinal() && u01(i, j, y, 3) < verwittern) bod[k] = (byte) Boden.MINERAL.ordinal();
                else if (bod[k] == Boden.MINERAL.ordinal() && fc > 0.5 && u01(i, j, y, 4) < humus) bod[k] = (byte) Boden.WALDBODEN.ordinal();
                // Hauptbaum
                if (art[k] >= 0) {
                    Art a = arten.get(art[k]);
                    double l = licht(fc, hc, ho[k]);
                    ho[k] = (float) wachsen(a, ho[k], l);
                    if (u01(i, j, y, 5) < tod(a, y - geb[k], l)) { art[k] = unter[k]; ho[k] = uh[k]; geb[k] = ugeb[k]; unter[k] = -1; uh[k] = 0; }
                }
                // Unterstand
                if (unter[k] >= 0) {
                    Art a = arten.get(unter[k]);
                    double l = licht(fc, hc, uh[k]) * (ho[k] > uh[k] + 2 ? 0.4 : 1);
                    uh[k] = (float) wachsen(a, uh[k], l);
                    if (u01(i, j, y, 6) < tod(a, y - ugeb[k], l)) { unter[k] = -1; uh[k] = 0; }
                    else if (uh[k] > ho[k]) {
                        byte ta = art[k]; float th = ho[k]; short tg = geb[k];
                        art[k] = unter[k]; ho[k] = uh[k]; geb[k] = ugeb[k]; unter[k] = ta; uh[k] = th; ugeb[k] = tg;
                    }
                }
                // Anwuchs: auf freiem Platz oder (Schattenbäume) im Unterstand
                boolean frei = art[k] < 0, unterFrei = !frei && unter[k] < 0;
                if (frei || unterFrei) {
                    double lg = licht(fc, hc, 0) * (frei ? 1 : 0.4);
                    double sum = 0;
                    for (int s = 0; s < S; s++) {
                        Art a = arten.get(s);
                        double sd = Math.min(1.5, saat[s][c] * norm) * a.saat;
                        rate[s] = sd * a.anwuchs[bod[k]] * Math.pow(lg, 2 * (1 - a.schatten));
                        sum += rate[s];
                    }
                    if (sum > 0 && u01(i, j, y, 7) < 1 - Math.exp(-sum)) {
                        double pick = u01(i, j, y, 8) * sum;
                        int s = 0;
                        while (s < S - 1 && pick > rate[s]) { pick -= rate[s]; s++; }
                        if (frei) { art[k] = (byte) s; ho[k] = 0.3f; geb[k] = (short) y; }
                        else { unter[k] = (byte) s; uh[k] = 0.3f; ugeb[k] = (short) y; }
                    }
                }
            }
            merken(art, ho, bod);
        }
        jahre = bis;
        nsJahre = System.nanoTime() - t0;
    }

    private int grob(int k) { int lk = lage[k]; return (lk / w / GROB) * gw + (lk % w) / GROB; }

    /** Licht für einen Baum der Höhe hh unter einem Dach (Deckung fc, mittlere Höhe hc): 1 frei, 0,15 unter dichtem höherem Dach. */
    static double licht(double fc, double hc, double hh) {
        double ueber = Math.max(0, Math.min(1, (hc - hh) / 10));
        return 1 - 0.85 * fc * ueber;
    }

    /** Ein Jahr Wuchs: von der Höhe zurück aufs Alter, ein Jahr weiter, im Schatten gebremst (Toleranz + (1 − Toleranz)·Licht). */
    static double wachsen(Art a, double hh, double licht) {
        double t = a.alterBei(hh);
        double dh = a.hoehe(t + 1) - hh;
        return hh + Math.max(0, dh) * (a.schatten + (1 - a.schatten) * licht);
    }

    /** Sterben je Jahr: Lebensdauer, im Schatten für Lichtbäume mehr, alte Bäume stark. */
    double tod(Art a, int alter, double licht) {
        double p = 1.0 / a.alter;
        if (licht < 0.5) p += (1 - a.schatten) * schattenTod;
        if (alter > a.alter) p += 0.1;
        return p;
    }

    private void merken(byte[] art, float[] ho, byte[] bod) {
        byte[] a = new byte[n], hh = new byte[n];
        for (int k = 0; k < n; k++) {
            a[k] = (byte) (art[k] + 1);
            hh[k] = (byte) Math.min(255, Math.round(ho[k] * 4));
        }
        histArt.add(a);
        histH.add(hh);
        histBoden.add(bod.clone());
    }

    /** Drei Kastenfilter (≈ Gauß mit σ in Zellen), Rand fortgesetzt. */
    public static void blurTest(float[] v, int gw, int gh, double sigma) { blur(v, new float[v.length], gw, gh, sigma); }

    private void blur(float[] v, float[] tmp, double sigma) { blur(v, tmp, gw, gh, sigma); }

    static void blur(float[] v, float[] tmp, int gw, int gh, double sigma) {
        int r = (int) Math.max(0, Math.round((Math.sqrt(4 * sigma * sigma + 1) - 1) / 2));
        if (r == 0) return;
        for (int pass = 0; pass < 3; pass++) {
            // waagerecht
            for (int j = 0; j < gh; j++) {
                int o = j * gw;
                double s = 0;
                for (int i = -r; i <= r; i++) s += v[o + Math.max(0, Math.min(gw - 1, i))];
                for (int i = 0; i < gw; i++) {
                    tmp[o + i] = (float) (s / (2 * r + 1));
                    s += v[o + Math.min(gw - 1, i + r + 1)] - v[o + Math.max(0, i - r)];
                }
            }
            // senkrecht
            for (int i = 0; i < gw; i++) {
                double s = 0;
                for (int j = -r; j <= r; j++) s += tmp[Math.max(0, Math.min(gh - 1, j)) * gw + i];
                for (int j = 0; j < gh; j++) {
                    v[j * gw + i] = (float) (s / (2 * r + 1));
                    s += tmp[Math.min(gh - 1, j + r + 1) * gw + i] - tmp[Math.max(0, j - r) * gw + i];
                }
            }
        }
    }

    // ------------------------------------------------------------------ Ausgabe

    /**
     * Schreibt den Jahrgang jahr (0 … {@link #jahre()}) Platz für Platz des Bands in a: ein lebender Baum ab 1,5 m steht
     * (mit Art und Höhe), sonst der Rest der Welle, solange er steht oder liegt, sonst fort. Außerhalb des Bands wird nichts
     * geschrieben (dort steht der Altbestand).
     */
    public void zustand(int jahr, Ausgabe a) {
        int y = Math.max(0, Math.min(jahre, jahr));
        byte[] ar = histArt.get(y), hh = histH.get(y);
        for (int k = 0; k < n; k++) {
            int lk = lage[k], i = i0 + lk % w, j = j0 + lk / w;
            int s = ar[k] - 1;
            double ht = (hh[k] & 0xff) / 4.0;
            if (s >= 0 && ht >= 1.5) { a.platz(i, j, STEHEND, 0, s, ht); continue; }
            int f = folge[k];
            if (y < restBis[k]) {
                int z = f == Wellenwurf.Folge.GEBROCHEN.ordinal() ? GEBROCHEN : f == Wellenwurf.Folge.ENTRINDET.ordinal() ? ENTRINDET : UMGELEGT;
                a.platz(i, j, z, fall[k], -1, 0);
            } else a.platz(i, j, FORT, 0, -1, 0);
        }
    }

    /** Art des Altbaums am Band-Platz k vor der Welle (−1: keiner). */
    public int altArt(int k) { return altArt[k]; }

    /** Höhe des Altbaums am Band-Platz k vor der Welle (m). */
    public double altHoehe(int k) { return altH[k]; }

    /** Art (−1 keine) und Höhe des Hauptbaums am Platz (i, j) im Jahr, NaN außerhalb des Bands. */
    public double[] platz(int i, int j, int jahr) {
        int ii = i - i0, jj = j - j0;
        if (ii < 0 || jj < 0 || ii >= w || jj >= h || jahre < 0) return null;
        int k = Arrays.binarySearch(lage, jj * w + ii);
        if (k < 0) return null;
        int y = Math.max(0, Math.min(jahre, jahr));
        return new double[]{histArt.get(y)[k] - 1, (histH.get(y)[k] & 0xff) / 4.0, boden(k, y), folge[k], tiefe[k]};
    }

    private int boden(int k, int y) { return histBoden.isEmpty() ? boden0[k] : histBoden.get(Math.max(0, Math.min(jahre, y)))[k]; }

    /** Folge der Welle an Platz k des Bands (0 … {@link #band()}). */
    public Wellenwurf.Folge folge(int k) { return Wellenwurf.Folge.values()[folge[k]]; }

    /** Lage (i, j), Wassertiefe, Hang und Fallrichtung von Platz k des Bands. */
    public double[] lage(int k) { int lk = lage[k]; return new double[]{i0 + lk % w, j0 + lk / w, tiefe[k], hang[k], fall[k]}; }

    /** Boden von Platz k im Jahr. */
    public Boden bodenArt(int k, int jahr) { return Boden.values()[boden(k, jahr)]; }

    /** Höhe (m) des Hauptbaums an Platz k im Jahr (0: keiner) und seine Art (−1). */
    public double hoehe(int k, int jahr) { return (histH.get(Math.max(0, Math.min(jahre, jahr)))[k] & 0xff) / 4.0; }

    public int art(int k, int jahr) { return histArt.get(Math.max(0, Math.min(jahre, jahr)))[k] - 1; }

    /** Zahlen eines Jahrgangs: Band, Folgen, Deckung, Höhen, Anteile der Arten. */
    public record Statistik(int jahr, int band, int[] folgen, int[] boden, double deckung, double hoeheMittel, double hoeheBand, double hoeheAussen,
                            double[] anteil, long nsStoerung, long nsJahre) {
        /** Kronenhöhe im Band (mit Lücken) gegen außerhalb. */
        public double verhaeltnis() { return hoeheAussen > 0 ? hoeheBand / hoeheAussen : Double.NaN; }
    }

    public Statistik statistik(int jahr) {
        int y = Math.max(0, Math.min(jahre, jahr));
        int S = arten.size();
        int[] fo = new int[Wellenwurf.Folge.values().length], bo = new int[3];
        double[] an = new double[S];
        int dicht = 0;
        double hs = 0, hall = 0;
        for (int k = 0; k < n; k++) {
            fo[folge[k]]++;
            bo[boden(k, y)]++;
            int s = jahre >= 0 ? histArt.get(y)[k] - 1 : -1;
            double ht = jahre >= 0 ? (histH.get(y)[k] & 0xff) / 4.0 : 0;
            hall += ht;
            if (s >= 0 && ht >= dachAb) { dicht++; hs += ht; an[s]++; }
        }
        for (int s = 0; s < S; s++) an[s] = dicht > 0 ? an[s] / dicht : 0;
        double da = 0, dd = 0;
        for (int c = 0; c < altDach.length; c++) { da += altDach[c]; dd += altDecke[c]; }
        return new Statistik(y, n, fo, bo, n > 0 ? dicht / (double) n : 0, dicht > 0 ? hs / dicht : 0, n > 0 ? hall / n : 0,
                dd > 0 ? da / dd : altHoehe, an, nsStoerung, nsJahre);
    }

    /** „Band 12 345 Plätze · Jahr 54: Deckung 92 %, Höhe 24,1 m (im Band 22,0 m, außerhalb 40,0 m, 0,55) · Roterle 31 %, …“ */
    public String zeile(int jahr) {
        Statistik st = statistik(jahr);
        StringBuilder sb = new StringBuilder(String.format(Locale.GERMAN, "Band %,d Plätze · Jahr %d: Deckung %.0f %%, Höhe %.1f m (im Band %.1f m, außerhalb %.1f m, %.2f)",
                st.band(), st.jahr(), 100 * st.deckung(), st.hoeheMittel(), st.hoeheBand(), st.hoeheAussen(), st.verhaeltnis()));
        for (int s = 0; s < arten.size(); s++) sb.append(s == 0 ? " · " : ", ").append(arten.get(s).name).append(String.format(Locale.GERMAN, " %.0f %%", 100 * st.anteil()[s]));
        return sb.toString();
    }

    /** Prüfsumme des ganzen Verlaufs (für „zwei Läufe gleich“). */
    public long pruefsumme() {
        long s = 1125899906842597L;
        for (int y = 0; y < histArt.size(); y++) {
            for (byte b : histArt.get(y)) s = 31 * s + b;
            for (byte b : histH.get(y)) s = 31 * s + b;
        }
        return s;
    }
}
