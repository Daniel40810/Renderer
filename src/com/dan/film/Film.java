package com.dan.film;

import java.util.Locale;

/**
 * Der Film: vom Licht der Szene über das Negativ und die Kopie auf die Leinwand, wie es im Kino war.
 * <ol>
 * <li><b>Belichtung des Negativs</b>: je Schicht H = (Matrix · rgb) · Belichtung / 0,18 (x = log₁₀ H, die Graukarte bei
 * 0); dazu die <b>Halation</b>: Licht, das bis in den Träger kommt, wird an seiner Rückseite gespiegelt, über dem
 * Grenzwinkel θ_c = arcsin(1/n) ganz (Totalreflexion), und kehrt ab dem Halbmesser 2·t·tan θ_c zurück, vor allem in die
 * rotempfindliche Schicht (sie liegt unten) – darum die roten Höfe um Lichter. Die Form des Kerns folgt aus dem Träger
 * ({@link #halationProfile}): Rückschicht aus Ruß (kein Ring, nur ein schwacher Schein), grau eingefärbter Träger
 * (gedämpfter Ring), klarer Träger (voller Ring).</li>
 * <li><b>Entwicklung</b>: Dichte je Schicht aus der Schwärzungskurve, mit <b>Korn</b> in der Dichte: Selwyn (1935)
 * σ_D · √A fest; die RMS-Körnigkeit gilt für 48 µm, ein Bildpunkt deckt A = (Breite/px) · (Höhe/px) des Negativs;
 * σ wächst mit der Dichte etwa wie √D [G]. Je Schicht eigenes Korn (farbiges Korn), jedes Filmbild neues.</li>
 * <li><b>Kopie</b> mit dem Kopierlicht ({@link Grade}): log E der Kopie = −D_Negativ + b + Punkte · 0,025; b so, dass
 * die Graukarte in jeder Farbe auf die Dichte 1,0 der Kopie fällt (Lichtbestimmung „neutral“). Die Kopie hat ihre
 * eigene Kurve und ihre Farbstoffe mit Nebenabsorption (zeilenweise auf 1, so bleibt Grau grau).</li>
 * <li><b>Projektion</b>: Leuchtdichte 10^(−(D − D_min)) (bei einer alten Kopie mit verblassten Farbstoffen,
 * {@link #printAgeYears}), mal {@link #displayGain} auf den Bildschirm, ein weiches Knie,
 * sRGB. Dazu <b>Bildstand</b> (das Bild wackelt im Fenster um Hundertstel Millimeter) und <b>Flackern</b> (die Belichtung
 * schwankt von Bild zu Bild um Promille) [G].</li>
 * </ol>
 * Die Kopiervorlage, ihr Format und die Stärken sind öffentliche Felder; {@link #develop} rechnet auf dem Prozessor
 * dasselbe wie {@code gl/film.frag} (ohne Korn), für Prüfungen und Vorschauen.
 */
public final class Film {
    public static final double GRAY = 0.18, AIM_PRINT_DENSITY = 1.0;
    /** Fläche der Messblende der RMS-Körnigkeit (µm²): Kreis von 48 µm. */
    public static final double A48 = Math.PI * 24 * 24;

    public volatile Stock negative = Stock.vision3_250D(), print = Stock.print2383();
    public volatile Format format = Format.SUPER35_239;
    public volatile Grade grade = Grade.NEUTRAL;
    /** Alter der Kopie (Jahre): ihre Farbstoffe verblassen je Jahrzehnt um {@link Stock#fade} (0: frisch aus dem Labor). */
    public volatile double printAgeYears = 0;
    /** Belichtung gegen die der App (Blenden). */
    public volatile double exposureStops = 0;
    public volatile boolean grain = true, halation = true, weave = true, flicker = true;
    public volatile double grainGain = 1, halationGain = 1, weaveGain = 1, flickerGain = 1;
    /** Bilder je Sekunde (das Korn und der Bildstand wechseln je Filmbild). */
    public volatile double fps = 24;
    /** Leinwand → Bildschirm: die Graukarte (Kopie D = 1,0, 11 % der offenen Leinwand) landet bei rund 20 % (sRGB 0,48) [G]. */
    public volatile double displayGain = 1.7;
    /** Bildstand: Streuung (mm im Fenster) waagerecht und senkrecht, Zeit der Wanderung (s) [G]. */
    public volatile double weaveXmm = 0.010, weaveYmm = 0.016, weaveS = 0.45;
    /** Flackern: Streuung der Belichtung je Bild [G]. */
    public volatile double flickerSigma = 0.006;
    public volatile long seed = 1958;

    private Stock calNeg, calPrint;
    private double[] cal;

    /**
     * b je Kanal (Lichtbestimmung 25-25-25): die Graukarte fällt in der Kopie in jeder Farbe auf dieselbe Nettodichte
     * (über D_min), so ist sie auf der Leinwand neutral; in Grün auf D = {@link #AIM_PRINT_DENSITY}, Rot und Blau um ihr
     * D_min daneben (im Labor die Zieldichte des Graufelds, „LAD“).
     */
    public synchronized double[] printerOffsets() {
        Stock n = negative, p = print;
        if (cal == null || n != calNeg || p != calPrint) {
            cal = new double[3];
            for (int c = 0; c < 3; c++) cal[c] = p.curves()[c].inverse(AIM_PRINT_DENSITY - p.curves()[1].dmin() + p.curves()[c].dmin()) + n.curves()[c].density(0);
            calNeg = n;
            calPrint = p;
        }
        return cal.clone();
    }

    /** Belichtung je Schicht (relativ zur Graukarte) für lineares sRGB nach der Belichtung der App. */
    public double[] layerExposure(double r, double g, double b) {
        double[] m = negative.matrix();
        double k = Math.pow(2, exposureStops) / GRAY;
        return new double[]{(m[0] * r + m[1] * g + m[2] * b) * k, (m[3] * r + m[4] * g + m[5] * b) * k, (m[6] * r + m[7] * g + m[8] * b) * k};
    }

    /** Dichten des Negativs je Schicht für Belichtungen H (relativ). */
    public double[] negativeDensity(double[] h) {
        double[] d = new double[3];
        for (int c = 0; c < 3; c++) d[c] = negative.curves()[c].density(Math.log10(Math.max(1e-7, h[c])));
        return d;
    }

    /** Dichten der Kopie aus denen des Negativs, mit dem Kopierlicht. */
    public double[] printDensity(double[] dn) {
        double[] b = printerOffsets(), o = grade.offsets(), d = new double[3];
        for (int c = 0; c < 3; c++) d[c] = print.curves()[c].density(-dn[c] + b[c] + o[c]);
        return d;
    }

    /** Projektion: lineare Werte für den Bildschirm (vor sRGB) aus den Dichten der Kopie. */
    public double[] project(double[] dp) {
        double[] cr = print.dye(), dd = new double[3], out = new double[3];
        double[] keep = dyeKept();
        for (int c = 0; c < 3; c++) dd[c] = (dp[c] - print.curves()[c].dmin()) * keep[c];
        for (int i = 0; i < 3; i++) {
            double s = cr[3 * i] + cr[3 * i + 1] + cr[3 * i + 2];
            double e = (cr[3 * i] * dd[0] + cr[3 * i + 1] * dd[1] + cr[3 * i + 2] * dd[2]) / s;
            out[i] = knee(Math.pow(10, -e) * displayGain);
        }
        return out;
    }

    /** Was von den Farbstoffen der Kopie (Cyan, Purpur, Gelb) nach {@link #printAgeYears} bleibt: (1 − Anteil)^(Jahre / 10). */
    public double[] dyeKept() {
        double[] f = print.fade(), k = new double[3];
        for (int c = 0; c < 3; c++) k[c] = Math.pow(1 - f[c], Math.max(0, printAgeYears) / 10);
        return k;
    }

    /** Weiches Knie oberhalb von 0,8 (der Bildschirm kann nicht heller als 1). */
    public static double knee(double x) { return x <= 0.8 ? x : 0.8 + 0.2 * (1 - Math.exp(-(x - 0.8) / 0.2)); }

    /** Der ganze Weg ohne Korn und Halation: lineares sRGB der Szene → lineare Werte für den Bildschirm. */
    public double[] develop(double r, double g, double b) { return project(printDensity(negativeDensity(layerExposure(r, g, b)))); }

    /** Durchmesser einer Farbstoffwolke im Negativ (µm) [G: Farbkuppler um das entwickelte Silberkorn, rund 10 µm]. */
    public static final double DYE_CLOUD_UM = 10;

    /** Größe einer Farbstoffwolke im entzerrten Bild (Bildpunkte, x und y) für ein Bild, das widthPx breit ist. */
    public double[] grainCloudPx(int widthPx) {
        double x = DYE_CLOUD_UM / (format.widthMm() * 1000 / widthPx);
        return new double[]{x, x / format.squeeze()};
    }

    /**
     * Fläche (µm²), über die das Korn eines Bildpunkts gemittelt ist: die Fläche, die er im Negativ deckt, aber nie
     * weniger als eine Farbstoffwolke (Selwyn gilt nur für Blenden, die größer sind als das Korn; darunter wird das
     * Korn nicht stärker, nur größer als der Bildpunkt).
     */
    public double grainArea(int widthPx) { return Math.max(format.pixelAreaUm2(widthPx), DYE_CLOUD_UM * DYE_CLOUD_UM); }

    /** Streuung der Dichte des Korns in Schicht c bei der Nettodichte dNet für einen Bildpunkt der Fläche aUm2 (Selwyn). */
    public double grainSigma(Stock s, int c, double dNet, double aUm2) {
        return s.rms() / 1000 * s.layerGrain()[c] * Math.sqrt(A48 / Math.max(1e-6, aUm2)) * Math.sqrt(Math.max(0.05, dNet));
    }

    /** Halbmesser des Halationsrings (Bildpunkte, waagerecht) für ein Bild, das widthPx breit ist. */
    public double halationRadiusPx(int widthPx) { return negative.halationRadiusMm() / (format.widthMm() / widthPx); }

    /**
     * Halbmesser des Halationsrings im entzerrten Bild (Bildpunkte, x und y): anamorphotisch wird er mit dem Bild in die
     * Breite gezogen (y = x / Stauchung), der Ring wird eine Ellipse.
     */
    public double[] halationRadiusPx2(int widthPx) {
        double x = halationRadiusPx(widthPx);
        return new double[]{x, x / format.squeeze()};
    }

    /**
     * Reichweite des Halationskerns (Vielfache des Ringhalbmessers); was weiter fällt, ist im Kern nicht mehr enthalten
     * (5248 mit grauem Träger: 2 %, Vision3 mit Rückschicht: rund 10 % seines ohnehin schwachen Scheins). W9c: 3 statt 4
     * (auf der Radeon kostete der Film 2,3 bis 2,9 ms, der größte Teil davon die Halation).
     */
    public static final double HALATION_REACH = 3.0;
    /** Auflösung des Halationspuffers: Bildpunkte je Ringhalbmesser (der Puffer ist so viel kleiner als das Bild). */
    public static final double HALATION_TEXELS = 3.0;
    /** Streuung in den Schichten auf dem Rückweg (Vielfache des Ringhalbmessers) [G: rund 20 µm, die Dicke der Schichten]. */
    public static final double HALATION_EMULSION = 0.1;
    /** Einträge der Tabelle des Kerns (0 … {@link #HALATION_REACH}). */
    public static final int HALATION_TABLE = 64;

    /**
     * Form des Halationskerns (Bestrahlung je Fläche, nicht normiert) im Abstand ρ vom Licht, in Vielfachen des
     * Ringhalbmessers r_c = 2·t·tan θ_c. Die Schichten streuen das Licht, das in den Träger geht, fast gleichmäßig in alle
     * Richtungen (Lambert); unter dem Winkel θ kommt es nach r = 2·t·tan θ zurück. Die Bestrahlung dort ist
     * ∝ R(θ) · cos⁴θ · 10^(−2·D_T / cos θ): cos⁴ aus Lambert (cos θ), dem Weg (d = 2t / cos θ, also 1/d²) und der Neigung
     * (cos θ); R(θ) ist die Reflexion an der Rückseite des Trägers: gegen Luft nach Fresnel bis θ_c, darüber
     * Totalreflexion (das ist der Ring); mit Rückschicht ({@link Stock#remjet}) gibt es keine Totalreflexion, R ist fast
     * fest, es bleibt der breite Schein. Ein grau eingefärbter Träger (Dichte D_T je Durchgang) dämpft die schrägen,
     * langen Wege stärker als die steilen.
     */
    public static double halationProfile(Stock s, double rho) {
        double n = s.baseN(), tanC = Math.tan(Math.asin(1 / n));
        double th = Math.atan(Math.abs(rho) * tanC), c = Math.cos(th), r;
        if (s.remjet()) r = 1;
        else {
            double st = n * Math.sin(th);
            if (st >= 1) r = 1;
            else {
                double ct = Math.sqrt(1 - st * st);
                double rs = (n * c - ct) / (n * c + ct), rp = (n * ct - c) / (n * ct + c);
                r = 0.5 * (rs * rs + rp * rp);
            }
        }
        return r * c * c * c * c * Math.pow(10, -2 * s.baseDensity() / c);
    }

    /**
     * Der Kern als Tabelle über ρ = 0 … {@link #HALATION_REACH} ({@link #HALATION_TABLE} Einträge, größter Wert 1): das Profil,
     * zweidimensional mit der Streuung der Schichten ({@link #HALATION_EMULSION}) gefaltet. Gewichte im Puffer: Tabelle an
     * |(dx / r_x, dy / r_y)|, durch ihre Summe über die Scheibe geteilt ({@link #halationNorm}).
     */
    public static float[] halationTable(Stock s) {
        int n = HALATION_TABLE, q = 6;
        double se = HALATION_EMULSION;
        double[] v = new double[n];
        double max = 0;
        for (int i = 0; i < n; i++) {
            double rho = HALATION_REACH * i / (n - 1), sum = 0, ws = 0;
            for (int a = -q; a <= q; a++)
                for (int b = -q; b <= q; b++) {
                    double ux = 3 * se * a / q, uy = 3 * se * b / q, w = Math.exp(-(ux * ux + uy * uy) / (2 * se * se));
                    sum += w * halationProfile(s, Math.hypot(rho + ux, uy));
                    ws += w;
                }
            v[i] = sum / ws;
            max = Math.max(max, v[i]);
        }
        float[] t = new float[n];
        for (int i = 0; i < n; i++) t[i] = (float) (v[i] / max);
        return t;
    }

    /** Wert der Tabelle bei ρ (linear zwischen den Einträgen, 0 jenseits der Reichweite), wie in halation.frag. */
    public static double halationAt(float[] table, double rho) {
        double u = rho / HALATION_REACH * (table.length - 1);
        if (u >= table.length - 1) return 0;
        int i = (int) u;
        double f = u - i;
        return table[i] * (1 - f) + table[i + 1] * f;
    }

    /** Größe des Halationspuffers für ein Bild w × h mit dem Ringhalbmesser rx, ry (Bildpunkte). */
    public static int[] halationBuffer(int w, int h, double rx, double ry) {
        return new int[]{(int) Math.max(1, Math.min(w, Math.round(w * HALATION_TEXELS / Math.max(1e-6, rx)))),
                (int) Math.max(1, Math.min(h, Math.round(h * HALATION_TEXELS / Math.max(1e-6, ry))))};
    }

    /**
     * Die Gewichte des Kerns im Puffer als Bild (2kx + 1) × (2ky + 1), schon durch ihre Summe geteilt (für halation.frag als
     * Textur: ein Zugriff statt der Rechnung je Punkt). Zeile für Zeile von −ky, Spalte von −kx.
     */
    public static float[] halationWeights(float[] table, double rbx, double rby) {
        double[] nk = halationNorm(table, rbx, rby);
        int kx = (int) nk[1], ky = (int) nk[2], w = 2 * kx + 1;
        float[] out = new float[w * (2 * ky + 1)];
        for (int dy = -ky; dy <= ky; dy++)
            for (int dx = -kx; dx <= kx; dx++) out[(dy + ky) * w + dx + kx] = (float) (halationAt(table, Math.hypot(dx / rbx, dy / rby)) / nk[0]);
        return out;
    }

    /** Summe der Gewichte über die Scheibe im Puffer (Ringhalbmesser rbx, rby in Puffer-Bildpunkten) und ihre Reichweite kx, ky. */
    public static double[] halationNorm(float[] table, double rbx, double rby) {
        int kx = (int) Math.ceil(HALATION_REACH * rbx), ky = (int) Math.ceil(HALATION_REACH * rby);
        double sum = 0;
        for (int dy = -ky; dy <= ky; dy++)
            for (int dx = -kx; dx <= kx; dx++) sum += halationAt(table, Math.hypot(dx / rbx, dy / rby));
        return new double[]{sum, kx, ky};
    }

    /**
     * Halation auf dem Prozessor, genau wie FilmGl auf der Grafikkarte: das Bild src (w × h, ein Wert je Bildpunkt, die
     * Belichtung der roten Schicht) auf den Puffer gemittelt (r_c ≈ {@link #HALATION_TEXELS} Bildpunkte), dort mit dem Kern
     * gefaltet (außerhalb des Bildfensters kein Licht), bilinear zurück auf w × h. Ergebnis: K ∗ H.
     */
    public static float[] halate(float[] src, int w, int h, double rx, double ry, float[] table) {
        int[] bs = halationBuffer(w, h, rx, ry);
        int bw = bs[0], bh = bs[1];
        double sx = (double) w / bw, sy = (double) h / bh;
        double[] down = new double[bw * bh];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int bx = Math.min(bw - 1, (int) ((x + 0.5) / sx)), by = Math.min(bh - 1, (int) ((y + 0.5) / sy));
                down[by * bw + bx] += src[y * w + x];
            }
        // durch die Fläche, die ein Bildpunkt des Puffers deckt (nicht durch die Zahl der Bildpunkte, die auf ihn fallen; die
        // schwankt um eins, wenn die Größen nicht aufgehen): so bleibt die Energie eines Lichts genau erhalten
        for (int i = 0; i < down.length; i++) down[i] /= sx * sy;
        double rbx = rx / sx, rby = ry / sy;
        double[] nk = halationNorm(table, rbx, rby);
        int kx = (int) nk[1], ky = (int) nk[2];
        double[] conv = new double[bw * bh];
        for (int y = 0; y < bh; y++)
            for (int x = 0; x < bw; x++) {
                double acc = 0;
                for (int dy = -ky; dy <= ky; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= bh) continue;
                    for (int dx = -kx; dx <= kx; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= bw) continue;
                        double wgt = halationAt(table, Math.hypot(dx / rbx, dy / rby));
                        if (wgt > 0) acc += wgt * down[yy * bw + xx];
                    }
                }
                conv[y * bw + x] = acc / nk[0];
            }
        float[] out = new float[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double u = (x + 0.5) / sx - 0.5, v = (y + 0.5) / sy - 0.5;
                int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
                double fx = u - x0, fy = v - y0;
                int xa = Math.max(0, Math.min(bw - 1, x0)), xb = Math.max(0, Math.min(bw - 1, x0 + 1));
                int ya = Math.max(0, Math.min(bh - 1, y0)), yb = Math.max(0, Math.min(bh - 1, y0 + 1));
                out[y * w + x] = (float) ((conv[ya * bw + xa] * (1 - fx) + conv[ya * bw + xb] * fx) * (1 - fy)
                        + (conv[yb * bw + xa] * (1 - fx) + conv[yb * bw + xb] * fx) * fy);
            }
        return out;
    }

    /** Bildstand: Lage des Bilds im Fenster (mm, x und y) zur Zeit t (s), glatt und wiederholbar. */
    public double[] weaveMm(double t) {
        if (!weave) return new double[]{0, 0};
        double u = t / Math.max(0.05, weaveS);
        return new double[]{weaveXmm * weaveGain * noise(u, seed), weaveYmm * weaveGain * noise(u, seed + 7)};
    }

    /** Flackern: Faktor der Belichtung für das Filmbild Nummer frame. */
    public double flicker(long frame) {
        if (!flicker) return 1;
        return 1 + flickerSigma * flickerGain * gauss(frame, seed + 13);
    }

    /** Glattes Rauschen mit Streuung rund 1 (Wert an ganzen Zahlen gaußverteilt, kubisch verbunden). */
    static double noise(double u, long s) {
        long i = (long) Math.floor(u);
        double f = u - i, a = gauss(i, s), b = gauss(i + 1, s);
        double w = f * f * (3 - 2 * f);
        // Mischen senkt die Streuung; ausgeglichen im Mittel über f (√(∫(1−w)² + w²) ≈ 0,77)
        return (a * (1 - w) + b * w) / 0.77;
    }

    static double gauss(long i, long s) {
        long h = mix(i * 0x9E3779B97F4A7C15L + s * 0xC2B2AE3D27D4EB4FL);
        double u1 = ((h >>> 11) + 0.5) / (1L << 53), u2 = ((mix(h) >>> 11) + 0.5) / (1L << 53);
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
    }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** sRGB (IEC 61966-2-1) aus linear. */
    public static double srgb(double c) { c = Math.max(0, Math.min(1, c)); return c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055; }

    /** „Eastman Color Negative 5248 → Eastman Color Print 5382 · CinemaScope · Abend 1958“ */
    public String describe() {
        return String.format(Locale.GERMAN, "%s → %s · %s · %s · %.0f B/s", negative.name(), print.name(), format.name(), grade.name(), fps);
    }
}
