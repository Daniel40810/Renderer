package com.dan.sky;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * Licht an Nebel- und Wolkentröpfchen (seit Brocken Phase 7): die Phasenfunktion eines Tröpfchenfelds nach Mie
 * ({@link Mie}) als Farbtabelle über den Streuwinkel. Darin stehen die <b>Glorie</b> (farbige Ringe um den Gegenpunkt der
 * Sonne, um den Schatten des eigenen Kopfs), der <b>Nebelbogen</b> (ein breiter, fast weißer Bogen rund 38° um den
 * Gegenpunkt) und vorn die Aureole. Ein Nebel, der die Tabelle statt Henyey-Greenstein nimmt, zeigt alle drei von
 * selbst, an der richtigen Stelle und mit den Farben der Tröpfchengröße.
 * <p>
 * Eingänge: der wirksame Halbmesser {@link #radiusUm} (Median einer logarithmischen Normalverteilung) und ihre relative
 * Breite {@link #width}. Schmale Verteilungen (unter 5 %) geben lebhafte Ringe, ab rund 20 % bleibt ein weißlicher Fleck
 * mit rotem Rand (Laven 2005). Gerechnet wird mit 25 Wellenlängen von 400 bis 700 nm und 17 Halbmessern, die Farbe über
 * die Normspektralwerte CIE 1931 (Näherung nach Wyman, Sloan und Shirley 2013) nach linearem sRGB; Weiß bleibt Weiß.
 * Zuletzt wird die Tabelle über die Sonnenscheibe (0,53°) gemittelt.
 * <p>
 * Die Tabelle ({@link Table}) hat {@link #N} Werte je Kanal über 0° bis 180° (gleichabständig), normiert auf 4π:
 * isotrop wäre 1 überall. Gebaut wird einmal je Halbmesser und Breite (rund 0,3 bis 1 s auf allen Kernen), danach aus
 * dem Speicher. Nur Java.
 */
public final class Droplets {
    /** Werte je Kanal über 0° bis 180° (0,088° je Schritt). */
    public static final int N = 2048;
    /** Wellenlängen (nm) und Halbmesser je Verteilung. */
    static final int LAMBDAS = 25, RADII = 17;
    /** Scheinbarer Halbmesser der Sonne (Grad). */
    public static final double SUN_RADIUS_DEG = 0.2665;

    /** Wirksamer Halbmesser der Tröpfchen (µm, Median), 2 bis 30. */
    public volatile double radiusUm = 10;
    /** Relative Breite der Verteilung (σ von ln r), 0,01 bis 0,5. */
    public volatile double width = 0.08;

    public Droplets() { }

    public Droplets(double radiusUm, double width) {
        this.radiusUm = radiusUm;
        this.width = width;
    }

    /** Hochnebel und Wolkenobergrenze mit schmaler Verteilung: lebhafte Glorie [G nach Laven: r 10 µm, σ 8 %]. */
    public static Droplets nebelmeer() { return new Droplets(10, 0.08); }
    /** Strahlungsnebel im Tal: kleine Tröpfchen, breite Verteilung [G]. */
    public static Droplets talnebel() { return new Droplets(5, 0.25); }
    /** Wolke am Berg (Stau): größere Tröpfchen [G]. */
    public static Droplets stau() { return new Droplets(13, 0.15); }

    /** Die Tabelle für die aktuellen Eingänge (aus dem Speicher, sonst gerechnet). */
    public Table table() { return table(radiusUm, width); }

    private static final Map<String, Table> CACHE = new ConcurrentHashMap<>();

    /** Die Tabelle für Halbmesser (µm) und Breite; die letzten acht bleiben im Speicher. */
    public static Table table(double radiusUm, double width) {
        double r = Math.max(2, Math.min(30, radiusUm)), w = Math.max(0.01, Math.min(0.5, width));
        String key = String.format(Locale.ROOT, "%.2f|%.3f", r, w);
        Table t = CACHE.get(key);
        if (t != null) return t;
        t = build(r, w);
        if (CACHE.size() > 8) CACHE.clear();
        CACHE.put(key, t);
        return t;
    }

    /**
     * Die Phasenfunktion als Farbtabelle: {@code rgb} hat {@link #N} × 3 Werte (Winkel i · 180° / (N − 1)), normiert auf
     * 4π (isotrop = 1), gemittelt über die Sonnenscheibe. Dazu die Lage der Erscheinungen.
     */
    public record Table(double radiusUm, double width, float[] rgb, double asymmetry, double gloryRingDeg, double fogbowDeg,
                        double backscatter) {
        /** Wert eines Kanals beim Streuwinkel (Grad), linear zwischen den Stützstellen. */
        public double at(int c, double thetaDeg) {
            double u = Math.max(0, Math.min(N - 1, thetaDeg / 180.0 * (N - 1)));
            int i = Math.min(N - 2, (int) u);
            double f = u - i;
            return rgb[i * 3 + c] * (1 - f) + rgb[(i + 1) * 3 + c] * f;
        }

        /** Helligkeit (Y aus linearem sRGB) beim Streuwinkel. */
        public double luminance(double thetaDeg) {
            return 0.2126 * at(0, thetaDeg) + 0.7152 * at(1, thetaDeg) + 0.0722 * at(2, thetaDeg);
        }

        /** „r 10 µm (σ 8 %): Glorie, erster roter Ring 2,4°, Nebelbogen 38,9°, g 0,86“ */
        public String describe() {
            return String.format(Locale.GERMAN, "r %.0f µm (σ %.0f %%): Glorie, erster roter Ring %.1f°, Nebelbogen %.1f°, g %.2f",
                    radiusUm, width * 100, gloryRingDeg, fogbowDeg, asymmetry);
        }
    }

    // ------------------------------------------------------------------ Rechnung

    static Table build(double r, double w) {
        double[] mu = new double[N];
        for (int i = 0; i < N; i++) mu[i] = Math.cos(Math.toRadians(i * 180.0 / (N - 1)));
        double[] lam = new double[LAMBDAS];
        for (int l = 0; l < LAMBDAS; l++) lam[l] = 0.400 + 0.300 * l / (LAMBDAS - 1);
        // Halbmesser: gleichabständig in ln r über ±2,5 σ, Gewichte nach der Verteilung (Anzahl)
        double[] rad = new double[RADII], wn = new double[RADII];
        for (int k = 0; k < RADII; k++) {
            double z = -2.5 + 5.0 * k / (RADII - 1);
            rad[k] = r * Math.exp(z * w);
            wn[k] = Math.exp(-0.5 * z * z);
        }
        // je Wellenlänge: Σ Anzahl · s11 / k² und Σ Anzahl · C_sca (beides mit π r² gleich gewichtet)
        double[][] spec = new double[LAMBDAS][];
        double[] gl = new double[LAMBDAS];
        IntStream.range(0, LAMBDAS * RADII).parallel().mapToObj(idx -> {
            int l = idx / RADII, k = idx % RADII;
            double x = 2 * Math.PI * rad[k] / lam[l];
            Mie.Result res = Mie.scatter(x, Mie.waterIndex(lam[l]), mu);
            return new Object[]{l, k, res};
        }).forEachOrdered(o -> {
            int l = (Integer) o[0], k = (Integer) o[1];
            Mie.Result res = (Mie.Result) o[2];
            double kk = 2 * Math.PI / lam[l];
            if (spec[l] == null) spec[l] = new double[N + 1];
            double csca = res.qSca() * Math.PI * rad[k] * rad[k];
            for (int i = 0; i < N; i++) spec[l][i] += wn[k] * res.s11()[i] / (kk * kk);
            spec[l][N] += wn[k] * csca;
            gl[l] += wn[k] * csca * res.g();
        });
        // auf 4π normieren (über die Tabelle integriert, damit Abschneiden und Gitter nichts verschieben)
        double dth = Math.PI / (N - 1), gMean = 0;
        for (int l = 0; l < LAMBDAS; l++) {
            gMean += gl[l] / spec[l][N] / LAMBDAS;
            double integ = 0;
            for (int i = 0; i < N; i++) {
                double th = i * dth, wgt = (i == 0 || i == N - 1) ? 0.5 : 1;
                integ += wgt * spec[l][i] * 2 * Math.PI * Math.sin(th) * dth;
            }
            for (int i = 0; i < N; i++) spec[l][i] *= 4 * Math.PI / Math.max(1e-30, integ);
        }
        // Farbe: Normspektralwerte → lineares sRGB, je Kanal so geteilt, dass ein flaches Spektrum (1, 1, 1) gibt
        double[][] wc = new double[LAMBDAS][3];
        double[] sum = new double[3];
        for (int l = 0; l < LAMBDAS; l++) {
            double[] xyz = cie(lam[l] * 1000);
            double[] rgb = {3.2406 * xyz[0] - 1.5372 * xyz[1] - 0.4986 * xyz[2],
                    -0.9689 * xyz[0] + 1.8758 * xyz[1] + 0.0415 * xyz[2],
                    0.0557 * xyz[0] - 0.2040 * xyz[1] + 1.0570 * xyz[2]};
            for (int c = 0; c < 3; c++) { wc[l][c] = rgb[c]; sum[c] += rgb[c]; }
        }
        double[] raw = new double[N * 3];
        for (int i = 0; i < N; i++)
            for (int c = 0; c < 3; c++) {
                double v = 0;
                for (int l = 0; l < LAMBDAS; l++) v += wc[l][c] * spec[l][i];
                raw[i * 3 + c] = v / sum[c];
            }
        // über die Sonnenscheibe mitteln (Profil eines Kreises: √(1 − u²)), am Rand gespiegelt
        double[] sm = sunAverage(raw);
        float[] out = new float[N * 3];
        for (int i = 0; i < N * 3; i++) out[i] = (float) Math.max(0, sm[i]);
        Table pre = new Table(r, w, out, gMean, 0, 0, 0);
        return new Table(r, w, out, gMean, gloryRing(pre), fogbow(pre), pre.luminance(180));
    }

    static double[] sunAverage(double[] raw) {
        double step = 180.0 / (N - 1);
        int h = (int) Math.ceil(SUN_RADIUS_DEG / step);
        double[] k = new double[2 * h + 1];
        double ks = 0;
        for (int j = -h; j <= h; j++) {
            double u = j * step / SUN_RADIUS_DEG;
            k[j + h] = Math.sqrt(Math.max(0, 1 - u * u));
            ks += k[j + h];
        }
        double[] out = new double[raw.length];
        for (int i = 0; i < N; i++)
            for (int c = 0; c < 3; c++) {
                double v = 0;
                for (int j = -h; j <= h; j++) {
                    int q = i + j;
                    if (q < 0) q = -q;
                    if (q > N - 1) q = 2 * (N - 1) - q;
                    v += k[j + h] * raw[q * 3 + c];
                }
                out[i * 3 + c] = v / ks;
            }
        return out;
    }

    /**
     * Erster roter Ring der Glorie (Grad vom Gegenpunkt): das erste Maximum des Rotanteils R / (R + G + B) außerhalb
     * der Mitte (wie Laven 2005 die Ringe zählt; für r = 10 µm rund 2,4°, allgemein ≈ 24° / r).
     */
    static double gloryRing(Table t) {
        double step = 180.0 / (N - 1), prev = -1, prev2 = -1;
        double start = Math.max(0.6, 6.0 / t.radiusUm());
        for (double a = start; a < 25; a += step) {
            double th = 180 - a;
            double rr = t.at(0, th), s = rr + t.at(1, th) + t.at(2, th), red = rr / Math.max(1e-12, s);
            if (prev2 >= 0 && prev > prev2 && prev >= red && prev > 0.36) return a - step;
            prev2 = prev;
            prev = red;
        }
        return Double.NaN;
    }

    /** Nebelbogen: Abstand des hellsten Punkts zwischen 25° und 46° vom Gegenpunkt (Grad). */
    static double fogbow(Table t) {
        double best = -1, at = Double.NaN;
        for (double a = 25; a <= 46; a += 0.05) {
            double v = t.luminance(180 - a);
            if (v > best) { best = v; at = a; }
        }
        return at;
    }

    /** CIE-1931-Normspektralwerte (λ in nm), Mehrfach-Gauß nach Wyman, Sloan und Shirley (2013). */
    static double[] cie(double l) {
        double x = 1.056 * g(l, 599.8, 37.9, 31.0) + 0.362 * g(l, 442.0, 16.0, 26.7) - 0.065 * g(l, 501.1, 20.4, 26.2);
        double y = 0.821 * g(l, 568.8, 46.9, 40.5) + 0.286 * g(l, 530.9, 16.3, 31.1);
        double z = 1.217 * g(l, 437.0, 11.8, 36.0) + 0.681 * g(l, 459.0, 26.0, 13.8);
        return new double[]{x, y, z};
    }

    private static double g(double l, double mu, double s1, double s2) {
        double t = (l - mu) / (l < mu ? s1 : s2);
        return Math.exp(-0.5 * t * t);
    }

    // ------------------------------------------------------------------ Lage am Himmel

    /** Richtung zum Gegenpunkt der Sonne (Mitte von Glorie, Gespenst und Nebelbogen): −Sonne. */
    public static double[] antisolar(double[] sunDir) { return new double[]{-sunDir[0], -sunDir[1], -sunDir[2]}; }

    /**
     * Wie lang der Kernschatten eines Körpers mit dem Halbmesser {@code radiusM} ist (m): dahinter ist er nur noch
     * Halbschatten und im Nebel kaum zu sehen. Ein Mensch (0,25 m) wirft rund 54 m Kernschatten.
     */
    public static double umbraLength(double radiusM) { return radiusM / Math.tan(Math.toRadians(SUN_RADIUS_DEG)); }

    /**
     * Sieht das Auge ein Brockengespenst? Die Sonne muss scheinen (über −0,5°) und im Rücken stehen: der Gegenpunkt
     * liegt unter der Kimm des Auges (sonst fällt der Schatten in den Himmel), und Nebel muss in der Richtung des
     * Gegenpunkts näher als der Kernschatten liegen ({@code fogDistance}, m). Gibt den Winkel des Schattens eines
     * Körpers mit {@code radiusM} am Nebel zurück (Grad), sonst 0.
     */
    public static double spectreDeg(double[] sunDir, double fogDistance, double radiusM) {
        double alt = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sunDir[1]))));
        if (alt < -0.5 || !(fogDistance > 0) || fogDistance > umbraLength(radiusM)) return 0;
        return 2 * Math.toDegrees(Math.atan(radiusM / fogDistance));
    }
}
