package com.dan.surge;

import java.util.Locale;

/**
 * Die Lippe einer brechenden Front als eigenes Netz (aus LituyaBay1958, Werkbuch Welle W4b „Die Röhre“) [G]. Eine
 * Wellenfläche als Höhenfeld kann nicht überhängen; eine Lippe aus ihren Ecken staffelt sich, wo die Front schräg über die
 * Zeilen läuft (Zickzack auf der ganzen Wand). Hier wird je Bild die Linie der Front aus der Flutkarte gezogen (Ankunft =
 * jetzt − {@link #behind} s: die Krone gleich hinter dem Fuß), und an jedem Punkt ein Querschnitt gebaut:
 * <ul>
 * <li>aus dem Wasser hinter der Krone über die Krone, die Lippe als kubische Bézier-Kurve (8 Punkte) bis zur Spitze bei
 * 0,60–0,74 der Höhe, dann vier Punkte der Rolle zurück unter die Lippe; Normalen aus der Tangente;</li>
 * <li>der Wurf zählt vom Fuß der Front an (Tempo aus dem Gefälle der Ankunft, 15–120 m/s, mal 0,35 s), dazu 0,25–0,55
 * der Höhe, in unregelmäßigen Abschnitten aus drei Lagen Rauschen (≈ 37, 83, 190 m), die langsam wandern;</li>
 * <li>Einrollen: früh wirft die Lippe kurz und hoch, später länger, die Spitze sinkt und rollt unter die Lippe zurück;</li>
 * <li>offen oder zu: rund die Hälfte der Abschnitte ist aufgeschlagen; dort fällt der Vorhang (ein eigener Streifen aus
 * fünf Punkten von der Spitze bis unter den Pegel) als Weißwasser ins Wasser;</li>
 * <li>Tropfen von der Spitze (0,35–1,25 s Flug, etwas schneller als die Front) und Spritzer am Fuß des Vorhangs
 * (3–12 m/s nach oben und vorn), feste Plätze alle 0,5 m längs der Front, drei je Platz.</li>
 * </ul>
 * Nur über tiefem Wasser (Grund mehr als 10 m unter dem Pegel) und bei Wellen von 8 bis 80 m, am Rand weich; nur im
 * Umkreis {@link #range} um das Auge. Ergebnis in {@link #mesh} (Dreiecke, je Ecke {@link #FL} Werte) und {@link #drops}
 * (je Tropfen {@link #FT} Werte); gezeichnet von {@code gl/SurgeGl}.
 */
public final class Lip {
    /** Grund unter der Linie (Szene x, z → m; NaN ohne). */
    @FunctionalInterface public interface Bed { double at(double x, double z); }

    /** Punkte der Bézier-Kurve, des Querschnitts (Rücken 2, Krone, Kurve, Rolle 4) und des Vorhangs. */
    public static final int NB = 8, NQ = 2 + 1 + NB + 4, NV = 5;
    /** Werte je Ecke: x y z, nx ny nz, u, H, Tx Tz, Rand, zu, Fuß x z (Punkt der Linie, für das Rauschen). */
    public static final int FL = 14;
    /** Werte je Tropfen: x y z, Größe (m), Deckung, Kennung. */
    public static final int FT = 6;
    /** Ecken je Stück: Hauptband (NQ − 1 Vierecke) und Vorhang (NV − 1), je zwei Dreiecke. */
    public static final int PER_SEGMENT = 6 * (NQ - 1 + NV - 1);

    /** Die Linie so viele Sekunden hinter dem Fuß der Front (Krone); Umkreis um das Auge (m). */
    public double behind = 0.35, range = 4500;
    /** Tropfen: Abstand der Plätze längs der Front (m), Umkreis (m). */
    public double dropSpacing = 0.5, dropRange = 2200;
    /** Höchstens so viele Stücke und Tropfen je Bild (darüber wird verworfen). */
    public int maxSegments = 6000, maxDrops = 40000;
    /** Felder der Flutkarte (Zellen je Kante) für das schnelle Verwerfen. */
    public int blockSize = 32;

    /** Ergebnis: Ecken (Dreiecke) und Tropfen; Zahl der Stücke, Ecken, Tropfen; Grund des Verwerfens je Art. */
    public float[] mesh, drops;
    public int segments, vertices, dropCount;
    public final int[] rejected = new int[6];   // Stand, Gefälle, Grund, Gewicht, Weite, Puffer
    public String info = "Lippe: aus";

    private int mp, dp;
    private double now, eyeX, eyeZ;

    /**
     * Die Lippe zur Ereigniszeit t: Linie, Querschnitte, Tropfen. Die Flutkarte braucht {@link FloodMap#blocks} mit
     * {@link #blockSize}. Auge (x, z), Pegel, Grund.
     */
    public void build(FloodMap f, double t, double ex, double ez, double level, Bed bed) {
        segments = 0;
        dropCount = 0;
        java.util.Arrays.fill(rejected, 0);
        if (mesh == null || mesh.length != FL * PER_SEGMENT * maxSegments) mesh = new float[FL * PER_SEGMENT * maxSegments];
        if (drops == null || drops.length != FT * maxDrops) drops = new float[FT * maxDrops];
        mp = 0;
        dp = 0;
        vertices = 0;
        if (f == null) { info = "Lippe: keine Flutkarte"; return; }
        if (f.blockMin() == null || f.blockSize() != blockSize) throw new IllegalStateException("surge: FloodMap.blocks(" + blockSize + ", Pegel) fehlt");
        float[] A = f.smoothedArrival(3), M = f.stage;
        int nx = f.nx, nz = f.nz, feld = blockSize;
        double zelle = f.cell;
        float[] fMin = f.blockMin(), fMax = f.blockMax();
        double tc = t - behind;
        now = t;
        int ftx = (nx + feld - 1) / feld, ftz = (nz + feld - 1) / feld;
        double[] o = {f.x0, f.zSouth};
        eyeX = ex;
        eyeZ = ez;
        double[] pa = new double[13], pb = new double[13];
        for (int fj = 0; fj < ftz; fj++)
            for (int fi = 0; fi < ftx; fi++) {
                int fl = fj * ftx + fi;
                if (fMin[fl] > tc + 1 || fMax[fl] < tc - 1) continue;
                double cx = o[0] + (fi + 0.5) * feld * zelle, cz = o[1] - (fj + 0.5) * feld * zelle;
                if (hyp(cx - ex, cz - ez) > range + feld * zelle) continue;
                int i1 = Math.min(nx - 1, (fi + 1) * feld), j1 = Math.min(nz - 1, (fj + 1) * feld);
                for (int j = fj * feld; j < j1; j++)
                    for (int i = fi * feld; i < i1; i++) {
                        float a00 = A[j * nx + i], a10 = A[j * nx + i + 1], a01 = A[(j + 1) * nx + i], a11 = A[(j + 1) * nx + i + 1];
                        if (a00 > 1e5f || a10 > 1e5f || a01 > 1e5f || a11 > 1e5f) continue;
                        // Rand der Zelle gegen den Uhrzeigersinn: unten, rechts, oben, links; je Kante ein Schnitt
                        int k = 0;
                        double[] s = new double[8];
                        k = cut(a00, a10, i, j, i + 1, j, tc, s, k);
                        k = cut(a10, a11, i + 1, j, i + 1, j + 1, tc, s, k);
                        k = cut(a11, a01, i + 1, j + 1, i, j + 1, tc, s, k);
                        k = cut(a01, a00, i, j + 1, i, j, tc, s, k);
                        for (int q = 0; q + 3 < k * 2 && q + 3 < 8; q += 4) {
                            int ra = point(A, M, nx, nz, s[q], s[q + 1], o, zelle, level, bed, t, pa);
                            int rb = point(A, M, nx, nz, s[q + 2], s[q + 3], o, zelle, level, bed, t, pb);
                            if (ra >= 0 || rb >= 0) { rejected[Math.max(ra, rb)]++; continue; }
                            if (hyp(pa[0] - ex, pa[1] - ez) > range) { rejected[4]++; continue; }
                            if (mesh.length - mp < FL * PER_SEGMENT) { rejected[5]++; break; }
                            band(pa, pb, level);
                            segments++;
                        }
                    }
            }
        vertices = mp / FL;
        info = String.format(Locale.GERMAN, "Lippe [G]: %d Stücke, %d Ecken, %d Tropfen (verworfen: Stand %d, Gefälle %d, Grund %d, Gewicht %d, Weite %d, Puffer %d)",
                segments, vertices, dropCount, rejected[0], rejected[1], rejected[2], rejected[3], rejected[4], rejected[5]);
    }

    /** Zahl der Werte in {@link #mesh} und {@link #drops}. */
    public int meshFloats() { return mp; }
    public int dropFloats() { return dp; }

    /** Schnitt der Linie a = tc auf der Kante (i0, j0)–(i1, j1); gibt den Zähler der Schnitte zurück. */
    private static int cut(float a0, float a1, int i0, int j0, int i1, int j1, double tc, double[] s, int k) {
        if ((a0 < tc) == (a1 < tc) || k >= 4) return k;
        double f = (tc - a0) / (a1 - a0);
        s[k * 2] = i0 + (i1 - i0) * f;
        s[k * 2 + 1] = j0 + (j1 - j0) * f;
        return k + 1;
    }

    /** Wert-Rauschen 2D (0 … 1). */
    private static double noise2(double x, double z) {
        double i = Math.floor(x), j = Math.floor(z), f = x - i, g = z - j;
        f = f * f * (3 - 2 * f);
        g = g * g * (3 - 2 * g);
        long a = (long) i, b = (long) j;
        double v00 = hashI(a * 73856093L ^ b * 19349663L, 9), v10 = hashI((a + 1) * 73856093L ^ b * 19349663L, 9);
        double v01 = hashI(a * 73856093L ^ (b + 1) * 19349663L, 9), v11 = hashI((a + 1) * 73856093L ^ (b + 1) * 19349663L, 9);
        return (1 - g) * ((1 - f) * v00 + f * v10) + g * ((1 - f) * v01 + f * v11);
    }

    /** Drei Lagen 2D-Rauschen in der Fläche (≈ 37, 83, 190 m), die verschieden schnell wandern; 0 … 1. */
    private static double layers2(double x, double z, double t, double o) {
        return noise2(x / 37.0 + t * 0.21 + o, z / 37.0 + o * 0.7) * 0.30 + noise2(x / 83.0 - t * 0.09 + o * 1.7, z / 83.0 + o * 1.3) * 0.40
                + noise2(x / 190.0 + t * 0.04 + o * 2.3, z / 190.0 + o * 0.4) * 0.30;
    }

    /** Ganzzahliges Rauschen (splitmix64), 0 … 1; viel billiger als sin für die vielen Plätze der Tropfen. */
    static double hashI(long k, int salt) {
        long z = k * 0x9E3779B97F4A7C15L + salt * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    /**
     * Ein Punkt der Linie in Gitterzellen (fi, fj): p = {x, z, H, Tx, Tz, Wurf, Gewicht, Abschnitt, zu, Höhe der Spitze,
     * Tempo, Platz der Tropfen, Einrollen}. −1: in Ordnung, sonst der Grund (0 Stand, 1 Gefälle, 2 Grund, 3 Gewicht).
     */
    private int point(float[] A, float[] M, int nx, int nz, double fi, double fj, double[] o, double zelle, double pegel,
                             Bed grund, double t, double[] p) {
        double m = FloodMap.bilinear(M, nx, nz, fi, fj);
        if (Double.isNaN(m)) return 0;
        double H = m - pegel;
        if (H < 8 || H > 80) return 0;
        double ax = FloodMap.bilinear(A, nx, nz, fi + 1, fj) - FloodMap.bilinear(A, nx, nz, fi - 1, fj), an = FloodMap.bilinear(A, nx, nz, fi, fj + 1) - FloodMap.bilinear(A, nx, nz, fi, fj - 1);
        if (Double.isNaN(ax + an) || Math.abs(ax) > 1e4 || Math.abs(an) > 1e4) return 1;
        double l = hyp(ax, an);
        if (l < 1e-6) return 1;
        // Szene: x nach Ost, z nach Süd; Zeilen laufen nach Norden
        double tx = ax / l, tz = -an / l;
        double x = o[0] + (fi + 0.5) * zelle, z = o[1] - (fj + 0.5) * zelle;
        double g = grund.at(x, z);
        // nur über tiefem Wasser (kein Auflauf am Hang); ohne Grund (noch nicht geladen) gilt das Wasser als tief
        if (g >= pegel - 10) return 2;
        double w = smooth(8, 20, H) * (1 - smooth(45, 80, H)) * (Double.isNaN(g) ? 1 : smooth(pegel - 10, pegel - 25, g));
        // Rauschen in der Fläche (x, z), langsam wandernd; die Front läuft hindurch (Lituya W4b Runde 4: eine Koordinate
        // längs der Front sprang bei Positionen von Kilometern, wo die Richtung um ein Grad drehte, um zig Meter)
        double wx = Math.abs(tz) + 1e-3, wz = Math.abs(tx) + 1e-3;
        double ab = 0.55 + 0.45 * smooth(0.3, 0.65, layers2(x, z, t, 3.1));
        // der Wurf zählt vom Fuß der Front an; die Linie liegt behind s hinter dem Fuß
        double v = Math.max(15, Math.min(120, 2 * zelle / l));   // Tempo der Front (m/s) aus dem Gefälle der Ankunft
        double fuss = v * behind;
        // Einrollen [G]: eine Phase aus den beiden langen Lagen derselben wandernden Lagen wie offen/zu
        double phase = layers2(x, z, -t, 17.3);
        double lang = (noise2(x / 83.0 + t * 0.09 + 17.3 * 1.7, z / 83.0 + 17.3 * 1.3) * 0.40
                + noise2(x / 190.0 - t * 0.04 + 17.3 * 2.3, z / 190.0 + 17.3 * 0.4) * 0.30) / 0.70;
        double roll = smooth(0.15, 0.75, lang);
        double L = (fuss + (0.25 + 0.30 * ab) * H * (0.6 + 0.6 * roll)) * (0.35 + 0.65 * w);
        // offen oder zu: rund die Hälfte geschlossen, breit, damit der Vorhang über einige zehn Meter aus Strängen wächst
        double zu = smooth(0.28, 0.68, phase);
        p[0] = x; p[1] = z; p[2] = H * (0.75 + 0.25 * w); p[3] = tx; p[4] = tz; p[5] = L; p[6] = w; p[7] = ab; p[8] = zu;
        p[9] = 0.74 + (0.60 - 0.74) * roll;   // Höhe der Spitze (Anteil der Höhe)
        p[10] = v; p[11] = wx >= wz ? x : z + 1e6; p[12] = roll;   // Plätze der Tropfen längs der Achse, in der die Front mehr läuft
        return w > 0.05 ? -1 : 3;
    }

    static double smooth(double a, double b, double x) { double u = Math.max(0, Math.min(1, (x - a) / (b - a))); return u * u * (3 - 2 * u); }

    /** Ein Stück Band zwischen zwei Punkten der Linie: Lippe, Vorhang, Tropfen. */
    private void band(double[] a, double[] b, double pegel) {
        float[][] va = section(a, pegel), vb = section(b, pegel);
        for (int k = 0; k + 1 < NQ + NV; k++) {
            if (k == NQ - 1) continue;   // zwischen dem Ende der Rolle und dem Kopf des Vorhangs
            put(va[k]); put(vb[k]); put(vb[k + 1]);
            put(va[k]); put(vb[k + 1]); put(va[k + 1]);
        }
        drops(a, b, pegel);
    }

    /**
     * Tropfen und Spritzer [G]. Feste Plätze längs der Front (alle {@link #dropSpacing} m, nach der Koordinate längs der
     * Front, damit ein Tropfen von Bild zu Bild derselbe bleibt); jeder Platz wirft immer wieder einen Tropfen, der
     * ballistisch fällt. Meist an offenen Abschnitten; am Vorhang springen Spritzer vom Fuß (Flugzeit 2·vy/g).
     */
    private void drops(double[] a, double[] b, double pegel) {
        double la = a[11], lb = b[11];
        if (Math.abs(lb - la) < 1e-6 || Math.abs(lb - la) > 30) return;
        if (hyp(a[0] - eyeX, a[1] - eyeZ) > dropRange) return;
        double lo = Math.min(la, lb), hi = Math.max(la, lb);
        for (double k = Math.ceil(lo / dropSpacing); k * dropSpacing < hi; k++) {
            if (drops.length - dp < FT) return;
            double f = (k * dropSpacing - la) / (lb - la);
            for (int j = 0; j < 3; j++) {
                if (drops.length - dp < FT) return;
                long kk = (long) k * 4 + j;
                double h3 = hashI(kk, 3);
                double h1 = hashI(kk, 1);
                double zu = a[8] + (b[8] - a[8]) * f, w = a[6] + (b[6] - a[6]) * f;
                if (h3 > w * (0.45 + 0.55 * (1 - zu))) continue;
                double H = a[2] + (b[2] - a[2]) * f, L = a[5] + (b[5] - a[5]) * f, ty = a[9] + (b[9] - a[9]) * f;
                double tx = a[3] + (b[3] - a[3]) * f, tz = a[4] + (b[4] - a[4]) * f, tl = hyp(tx, tz);
                if (tl < 1e-6) continue;
                tx /= tl; tz /= tl;
                double v = a[10] + (b[10] - a[10]) * f;
                double x0 = a[0] + (b[0] - a[0]) * f, z0 = a[1] + (b[1] - a[1]) * f;
                double x, y, z, gr, deck, tau;
                if (hashI(kk, 7) < 0.7 * zu) {
                    // Spritzer am Fuß des Vorhangs: wo er aufschlägt, springt Wasser nach oben und vorn
                    double vy = 3.0 + 9.0 * hashI(kk, 6), life = 2 * vy / 9.81;
                    tau = (hashI(kk, 2) + now / life) % 1.0 * life;
                    double dv = v * (0.05 + 0.35 * hashI(kk, 4)), seit = (hashI(kk, 5) - 0.5) * 6.0;
                    x = x0 + tx * 1.09 * L + (tx * dv - tz * seit) * tau;
                    z = z0 + tz * 1.09 * L + (tz * dv + tx * seit) * tau;
                    y = pegel + vy * tau - 4.905 * tau * tau;
                    if (y < pegel - 0.3) continue;
                    gr = (0.10 + 0.40 * h1 * h1) * (1 + 0.6 * tau);
                    deck = Math.min(1, tau / 0.08) * (1 - 0.5 * tau / life);
                } else {
                    // Tropfen von der Spitze der Lippe
                    double life = 0.35 + 0.9 * h1 * h1;   // mit bis zu 2,2 s Flug standen sie wie Schnee in der ganzen Röhre
                    tau = (hashI(kk, 2) + now / life) % 1.0 * life;
                    double dv = v * (0.03 + 0.20 * hashI(kk, 4)), seit = (hashI(kk, 5) - 0.5) * 3.0, vy = 0.3 + 3.2 * hashI(kk, 6);
                    x = x0 + tx * L + (tx * dv - tz * seit) * tau;
                    z = z0 + tz * L + (tz * dv + tx * seit) * tau;
                    y = pegel + ty * H + vy * tau - 4.905 * tau * tau;
                    if (y < pegel - 0.3) continue;   // ins Wasser gefallen
                    gr = (0.08 + 0.27 * h1 * h1) * (1 + 0.8 * tau);
                    deck = Math.min(1, tau / 0.10) * Math.min(1, (y - pegel + 0.3) / 1.5) * (1 - 0.55 * tau / life);
                }
                drops[dp++] = (float) x; drops[dp++] = (float) y; drops[dp++] = (float) z;
                drops[dp++] = (float) gr; drops[dp++] = (float) deck; drops[dp++] = (float) (kk % 997);
                dropCount++;
            }
        }
    }

    /**
     * Der runde Querschnitt am Punkt p: zwei Punkte des Rückens, die Krone, die Lippe als kubische Bézier-Kurve von der
     * Krone (Tangente leicht steigend) zur Spitze (Tangente senkrecht nach unten), dann vier Punkte der Rolle zurück unter
     * die Lippe; danach als eigener Streifen der Vorhang von der Spitze bis unter den Pegel. u für die Schattierung
     * (0 Rücken, 0,12 Krone, 0,62 Spitze, bis 0,72 Rolle; Vorhang 2,62 … 3,0). p wie in point: {x, z, H, Tx, Tz, Wurf,
     * Gewicht, Abschnitt, zu, Höhe der Spitze (Anteil), Tempo, Platz, Einrollen}; Ergebnis je Ecke {@link #FL} Werte.
     */
    public static float[][] section(double[] p, double pegel) {
        double H = p[2], L = p[5], ty = p[9], roll = p[12];
        double[] ss = new double[NQ], yy = new double[NQ], uu = new double[NQ];
        int k = 0;
        ss[k] = -0.60 * H; yy[k] = 0.93 * H - 1.0; uu[k++] = 0.0;
        ss[k] = -0.25 * H; yy[k] = 0.99 * H; uu[k++] = 0.06;
        ss[k] = 0.0; yy[k] = 1.02 * H; uu[k++] = 0.12;
        double x0 = 0, y0 = 1.02 * H, x1 = 0.55 * L, y1 = 1.08 * H, x2 = 1.00 * L, y2 = 1.00 * H, x3 = L, y3 = ty * H;
        for (int b = 1; b <= NB; b++) {
            double t = b / (double) NB, mt = 1 - t;
            ss[k] = mt * mt * mt * x0 + 3 * mt * mt * t * x1 + 3 * mt * t * t * x2 + t * t * t * x3;
            yy[k] = mt * mt * mt * y0 + 3 * mt * mt * t * y1 + 3 * mt * t * t * y2 + t * t * t * y3;
            uu[k++] = 0.12 + 0.50 * t;
        }
        // die Rolle von der Spitze aus: früh ein kleiner Haken, eingerollt zurück unter die Lippe
        double[][] r0 = {{0.00, -0.04}, {-0.01, -0.07}, {-0.03, -0.09}, {-0.05, -0.10}};
        double[][] r1 = {{-0.03, -0.09}, {-0.10, -0.14}, {-0.17, -0.16}, {-0.22, -0.16}};
        double[] ur = {0.68, 0.70, 0.71, 0.72};
        for (int q = 0; q < 4; q++) {
            ss[k] = (1 + r0[q][0] + (r1[q][0] - r0[q][0]) * roll) * L;
            yy[k] = (ty + r0[q][1] + (r1[q][1] - r0[q][1]) * roll) * H;
            uu[k++] = ur[q];
        }
        float[][] r = new float[NQ + NV][];
        corners(ss, yy, uu, NQ, p, pegel, r, 0);
        double[] vs = {1.00, 1.03, 1.06, 1.08, 1.09}, vy = {1.0, 0.68, 0.35, 0.10, -0.05 / Math.max(ty, 0.3)}, vu = {2.62, 2.72, 2.82, 2.92, 3.0};
        double[] cs = new double[NV], cy = new double[NV];
        for (int q = 0; q < NV; q++) { cs[q] = vs[q] * L; cy[q] = vy[q] * ty * H; }
        corners(cs, cy, vu, NV, p, pegel, r, NQ);
        return r;
    }

    private static void corners(double[] ss, double[] yy, double[] uu, int n, double[] p, double pegel, float[][] r, int ab) {
        double H = p[2];
        for (int i = 0; i < n; i++) {
            int i0 = Math.max(0, i - 1), i1 = Math.min(n - 1, i + 1);
            double ds = ss[i1] - ss[i0], dy = yy[i1] - yy[i0];
            // nach außen: die Tangente um 90° gedreht (oben auf der Krone nach oben, an Spitze und Vorhang nach vorn)
            double nh = -dy, nv = ds, nl = hyp(nh, nv);
            if (nl < 1e-9) { nh = 0; nv = 1; nl = 1; }
            nh /= nl; nv /= nl;
            double x = p[0] + p[3] * ss[i], z = p[1] + p[4] * ss[i];
            r[ab + i] = new float[]{(float) x, (float) (pegel + yy[i]), (float) z, (float) (p[3] * nh), (float) nv, (float) (p[4] * nh), (float) uu[i],
                    (float) H, (float) p[3], (float) p[4], (float) p[6], (float) p[8], (float) p[0], (float) p[1]};
        }
    }

    private void put(float[] v) { System.arraycopy(v, 0, mesh, mp, FL); mp += FL; }

    /** Wie Math.hypot, aber ohne dessen Schutz gegen Überlauf (im Profil von Lituya H13 der größte Posten). */
    static double hyp(double a, double b) { return Math.sqrt(a * a + b * b); }
}
