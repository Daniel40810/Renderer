package com.dan.cloud;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Die Wolken als Zahlen: eine untere Schicht einer {@link CloudType Wolkenart} mit Bedeckung in Achteln, darüber auf
 * Wunsch eine hohe Schicht (Altostratus/Cirrostratus als dünner Schleier), seit Glacier Phase 3 Lentikularis als
 * ortsfeste Linsen einer Leewelle ({@link #lens}), Drift mit dem Wind in Wolkenhöhe und
 * langsamer Formwandel. Die Dichte an jedem Punkt, der Schatten am Boden, die Bedeckung und das Regenfeld lassen sich
 * ohne Grafikkarte fragen; {@code gl/CloudGl} zeichnet mit denselben Formeln und denselben Rauschtexturen.
 * <p>
 * Raum wie in der Szene: x Ost, y oben, z Süd, Meter, Ursprung auf Meereshöhe; die Höhe zählt über der gekrümmten
 * Erde (Radius {@link #EARTH_R}), damit die Wolkendecke zur Kimm hin abtaucht.
 * <p>
 * Die Bedeckung ist geeicht: Für jede Art wird aus der Wetterkarte die Schwelle bestimmt, über der genau
 * {@code oktas/8} der Fläche bewölkt ist (wie die Schaumbedeckung im Meer).
 */
public final class Clouds {
    public static final double EARTH_R = 6_360_000;
    /** Weiche Kante der Bedeckung auf der Wetterkarte. */
    public static final double SOFT = 0.12;
    /** Haufen: Dichte im Kern der Zelle (darunter frisst die Grundform Lücken und Türmchen heraus). */
    static final double CU_CORE = 0.65;
    /** Reichweite des Ambosses über den Turm hinaus, in Kacheln der Wetterkarte (gut eine Drittelzelle). */
    static final double ANVIL_R = 0.35 / 16;
    /** Hohe Schicht: Höhe, Kachelgröße der Karte, Dicke (optisch) bei voller Bedeckung. */
    public static final double HIGH_HEIGHT = 6500, HIGH_TILE = 60_000, HIGH_TAU = 0.6;
    /**
     * Seit Glacier Phase 3: Lentikularis (Altocumulus lenticularis) im Lee eines Gebirges – glatte Linsen auf den Kämmen
     * einer stehenden Leewelle, oft als Stapel. Untere Höhe der Linsen, Abstand der Lagen, Wellenlänge längs des Winds,
     * Länge der Rauschzellen quer dazu (m), optische Dicke im Kern, Zahl der Lagen [G]. Die Linsen ziehen nicht mit dem
     * Wind: sie stehen über dem Gebirge, die Luft strömt durch sie hindurch.
     */
    public static final double LENS_HEIGHT = 4600, LENS_STEP = 320, LENS_WAVE = 9000, LENS_CELL = 12_000, LENS_TAU = 3.0;
    public static final int LENS_SLICES = 3;
    /** Schatten werden durch Mehrfachstreuung weicher: τ wirkt nur zu diesem Anteil. */
    public static final double SHADOW_SOFT = 0.6;

    /** Rechenstufen: Teiler der Auflösung, Schritte im Zenit und an der Kimm, Schritte zum Licht. FLAT = nur 2.5D. */
    public enum Quality {
        FLAT(2, 0, 0, 0), MEDIUM(4, 72, 100, 5), HIGH(2, 96, 70, 6), ULTRA(2, 128, 45, 6);
        /** Teiler der Auflösung, höchste Schrittzahl, Schrittweite nah am Auge in m (wächst mit der Entfernung), Schritte zum Licht. */
        public final int divisor, maxSteps, stepNear, lightSteps;
        Quality(int divisor, int ms, int sn, int ls) { this.divisor = divisor; maxSteps = ms; stepNear = sn; lightSteps = ls; }
    }

    // ------------------------------------------------------------------ Eingänge

    /** Bedeckung der unteren Schicht in Achteln (0..8). */
    public volatile double oktas = 3;
    public volatile CloudType type = CloudType.CUMULUS;
    /** Hohe Schicht 0..1 (0 = keine). */
    public volatile double highCover;
    /** Niederschlag 0..1: Regenvorhänge unter den dichten Stellen (für {@code rain}). */
    public volatile double precipitation;
    /** Wind in Wolkenhöhe (m/s) und Richtung, aus der er kommt (Grad). */
    public volatile double windSpeed = 10, windFromDeg = 250;
    /** Formwandel: 1 = Haufen wachsen und vergehen in rund 10 Minuten. */
    public volatile double evolve = 1;
    public volatile Quality quality = Quality.HIGH;
    /** Seit Glacier Phase 3: Lentikularis 0..1 (0 = keine) und die Richtung der Strömung, aus der die Leewelle kommt (Grad). */
    public volatile double lens, lensFromDeg = 270;
    /** Wie weit gerechnet wird (m); dahinter verschwinden die Wolken im Dunst. */
    public volatile double maxDistance = 60_000;
    /**
     * Seit Brocken Phase 3: Untergrenze der unteren Schicht in m über dem Meer; NaN = die der Art ({@link CloudType#base},
     * Basis über See). Über einem Gebirge hebt die App die Wolken über ihr Gelände oder legt sie an den Berg: Dicke,
     * Türme und Amboss bleiben die der Art.
     */
    public volatile double baseHeight = Double.NaN;
    /**
     * Seit Brocken Phase 3: Höhe der Fläche in m über dem Meer, für die die Schattenkarte rechnet (0 = Meeresspiegel wie
     * bisher). Im Gebirge etwa die mittlere Höhe des Geländes um das Auge; was unter der Wolkenbasis liegt, bekommt
     * so den Schatten an der richtigen Stelle.
     */
    public volatile double shadowHeight;

    // ------------------------------------------------------------------ Wolken am Gelände (seit LituyaBay1958 W7)
    /**
     * Seit LituyaBay1958 W7: das Gelände unter den Wolken (null = keins, alles wie bisher). Daraus rechnet das Paket
     * einmal eine Strömungskarte ({@link Oro}): Gelände geglättet über {@link #terrainSmooth}, seine Steigung und die
     * Gipfel. Mit ihr entstehen Wolken, die es über dem Meer nicht gibt: eine {@link #bank Bank}, die am Hang hängt, eine
     * {@link #cap Kappe} über den höchsten Gipfeln, Haufen bevorzugt über Land ({@link #convection}) und Lücken im Lee
     * ({@link #lee}). Bank und Kappe stehen am Berg (die Luft strömt durch sie hindurch), ihre Form zieht mit dem Wind.
     */
    public volatile Terrain terrain;
    /** Glättung des Geländes für die Strömung (m; die Luft folgt nicht jeder Rinne). */
    public volatile double terrainSmooth = 700;
    /**
     * Seit LituyaBay1958 W7: Grundform und Feinheit mit stetiger Ableitung zwischen den Texeln mischen (siehe
     * {@code Noise.sample3}). Mit langer Brennweite in der Ferne (ein Texel der Feinheit über mehrere Bildpunkte) zerfielen
     * die Wolken sonst in feine senkrechte Streifen und waagrechte Bänder (Probe 711). Vorgabe false: wie bisher.
     */
    public volatile boolean smoothNoise = false;
    /** Wolkenbank am Hang 0..1 (0 = keine), ihre Unter- und Obergrenze (m über dem Meer) und wie weit sie vom Hang in die Luft reicht (m). */
    public volatile double bank, bankBase = 900, bankTop = 2200, bankReach = 450;
    /** Kappe über den Gipfeln 0..1 (0 = keine), Kondensationsniveau der gehobenen Luft (m) und Höhe der Haube über der Hülle der Gipfel (m). */
    public volatile double cap, capBase = 3900, capReach = 700;
    /** Quellwolken über Land 0..1: Haufen über hohem Land dichter, über dem Meer seltener (Bedeckung bleibt im Mittel). */
    public volatile double convection;
    /** Abtrocknen im Lee der Kämme 0..1 (Föhnlücke). */
    public volatile double lee;

    /** Zellen der Strömungskarte je Seite. */
    public static final int ORO_N = 256;
    /** Bank und Kappe: Auslöschung je m im Kern, Kachel der Form (m), Kachel der Lücken in der Bank (m), Versatz der Kappe nach Lee (m) [G]. */
    public static final double ORO_SIGMA = 0.05, ORO_TILE = 2200, BANK_TILE = 26_000, CAP_SHIFT = 400;

    private Terrain oroFor;
    private double oroSmoothFor = Double.NaN;
    private float[] oroMap;
    private double oroPeak;

    private double offX, offZ, time;
    private final Map<CloudType, double[]> quantiles = new EnumMap<>(CloudType.class);
    private double[] highQuantile;

    public Clouds() { }

    /** Wolken einer Art mit einer Bedeckung in Achteln. */
    public static Clouds of(CloudType type, double oktas) {
        Clouds c = new Clouds();
        c.type = type;
        c.oktas = oktas;
        return c;
    }

    /** Schönwetter: 3/8 Cumulus. */
    public static Clouds fair() { return of(CloudType.CUMULUS, 3); }

    /** Grau verhangen: 8/8 Stratocumulus. */
    public static Clouds overcast() { return of(CloudType.STRATOCUMULUS, 8); }

    /** Gewitter: 6/8 Cumulonimbus mit hohem Schirm und Regen. */
    public static Clouds storm() {
        Clouds c = of(CloudType.CUMULONIMBUS, 6);
        c.highCover = 0.5;
        c.precipitation = 0.8;
        c.windSpeed = 22;
        return c;
    }

    /** Zieht die Wolken mit dem Wind weiter und lässt sie wandeln. */
    public synchronized void step(double dt) {
        double a = Math.toRadians(windFromDeg + 180);
        offX -= Math.sin(a) * windSpeed * dt;
        offZ -= -Math.cos(a) * windSpeed * dt;
        time += dt;
    }

    /** Laufzeit setzen (Standbilder), die Drift folgt aus Wind und Zeit. */
    public synchronized void setTime(double t) {
        double a = Math.toRadians(windFromDeg + 180);
        offX = -Math.sin(a) * windSpeed * t;
        offZ = Math.cos(a) * windSpeed * t;
        time = t;
    }

    // ------------------------------------------------------------------ Gelände (seit LituyaBay1958 W7)

    /**
     * Gelände als Höhen in m über dem Meer über ein Quadrat: Ecke (x0, z0), Kantenlänge size (m), n × n Zellen, Zeile j
     * von z0 nach Süden, Spalte i von x0 nach Osten, Wert in der Mitte der Zelle. Meer und Lücken 0.
     */
    public record Terrain(double x0, double z0, double size, int n, float[] height) {
        public Terrain {
            if (n < 2 || height == null || height.length < n * n) throw new IllegalArgumentException("Clouds.Terrain: n × n Höhen erwartet");
        }
    }

    /**
     * Strömungskarte für Bank, Kappe, Quellwolken und Lee: über das Quadrat des Geländes {@link #ORO_N}² Zellen mit je
     * vier Werten (geglättete Höhe, ihre Steigung nach x und z in m/m, höchste Höhe der Umgebung), dazu der höchste Gipfel,
     * die Richtung, in die der Wind weht, und die Stärken. Unveränderlich; {@link Snapshot#oro()} ist null ohne Gelände.
     */
    public record Oro(double x0, double z0, double size, int n, float[] map, double peak, double windToX, double windToZ,
                      double bank, double bankBase, double bankTop, double bankReach,
                      double cap, double capBase, double capReach, double convection, double lee) {
        /** Obergrenze der Bank (m) oder NaN. */
        public double bankHigh() { return bank > 0 ? bankTop + 60 : Double.NaN; }
        /** Oberste Höhe der Kappe (m) oder NaN, wenn kein Gipfel das Kondensationsniveau erreicht. */
        public double capTop() { return cap > 0 && peak > capBase ? peak + capReach : Double.NaN; }
    }

    /**
     * Die Strömungskarte aus dem Gelände: auf m × m Zellen gemittelt (dazu das Größte je Zelle), dreimal kastengeglättet
     * (zusammen etwa Gauß mit {@code smooth}), Steigung aus zentralen Differenzen, Gipfel als größte Höhe im Umkreis, danach geglättet (Hülle, seit Runde 1b).
     * Liefert {@code float[m · m · 4 + 1]}, der letzte Wert ist der höchste Gipfel.
     */
    public static float[] oroMap(Terrain t, int m, double smooth) {
        int n = t.n();
        double cell = t.size() / m;
        double[] avg = new double[m * m], mx = new double[m * m];
        java.util.stream.IntStream.range(0, m).parallel().forEach(j -> {
            for (int i = 0; i < m; i++) {
                int a0 = (int) Math.floor((double) i * n / m), a1 = Math.max(a0 + 1, (int) Math.floor((double) (i + 1) * n / m));
                int b0 = (int) Math.floor((double) j * n / m), b1 = Math.max(b0 + 1, (int) Math.floor((double) (j + 1) * n / m));
                double sum = 0, top = -1e9;
                int k = 0;
                for (int b = b0; b < Math.min(n, b1); b++)
                    for (int a = a0; a < Math.min(n, a1); a++) {
                        double v = t.height()[b * n + a];
                        if (!(v > 0)) v = 0;
                        sum += v;
                        top = Math.max(top, v);
                        k++;
                    }
                avg[j * m + i] = k > 0 ? sum / k : 0;
                mx[j * m + i] = k > 0 ? top : 0;
            }
        });
        int r = Math.max(1, (int) Math.round(smooth / cell / 1.2));
        double[] hs = avg.clone();
        for (int pass = 0; pass < 3; pass++) { hs = box(hs, m, r, true); hs = box(hs, m, r, false); }
        // Hülle der Gipfel: das Höchste in der Nachbarschaft, danach geglättet (ohne Glätten blieben die Kanten der
        // Zellen als senkrechte Wände in der Kappe stehen, Befund W7 Runde 1, Probe 702)
        double[] pk = mx.clone();
        pk = dilate(pk, m, r, true);
        pk = dilate(pk, m, r, false);
        for (int pass = 0; pass < 2; pass++) { pk = box(pk, m, r, true); pk = box(pk, m, r, false); }
        float[] out = new float[m * m * 4 + 1];
        double peak = 0;
        for (int j = 0; j < m; j++)
            for (int i = 0; i < m; i++) {
                int o = (j * m + i) * 4;
                double gx = (hs[j * m + Math.min(m - 1, i + 1)] - hs[j * m + Math.max(0, i - 1)]) / ((Math.min(m - 1, i + 1) - Math.max(0, i - 1)) * cell);
                double gz = (hs[Math.min(m - 1, j + 1) * m + i] - hs[Math.max(0, j - 1) * m + i]) / ((Math.min(m - 1, j + 1) - Math.max(0, j - 1)) * cell);
                out[o] = (float) hs[j * m + i];
                out[o + 1] = (float) gx;
                out[o + 2] = (float) gz;
                out[o + 3] = (float) pk[j * m + i];
                peak = Math.max(peak, mx[j * m + i]);
            }
        out[m * m * 4] = (float) peak;
        return out;
    }

    private static double[] box(double[] v, int m, int r, boolean alongX) {
        double[] o = new double[v.length];
        for (int a = 0; a < m; a++)
            for (int b = 0; b < m; b++) {
                double s = 0;
                int k = 0;
                for (int d = -r; d <= r; d++) {
                    int c = Math.max(0, Math.min(m - 1, b + d));
                    s += alongX ? v[a * m + c] : v[c * m + a];
                    k++;
                }
                if (alongX) o[a * m + b] = s / k; else o[b * m + a] = s / k;
            }
        return o;
    }

    private static double[] dilate(double[] v, int m, int r, boolean alongX) {
        double[] o = new double[v.length];
        for (int a = 0; a < m; a++)
            for (int b = 0; b < m; b++) {
                double s = -1e9;
                for (int d = -r; d <= r; d++) {
                    int c = Math.max(0, Math.min(m - 1, b + d));
                    s = Math.max(s, alongX ? v[a * m + c] : v[c * m + a]);
                }
                if (alongX) o[a * m + b] = s; else o[b * m + a] = s;
            }
        return o;
    }

    /** Die Strömungskarte für die Eingänge jetzt (gemerkt, neu nur bei anderem Gelände oder anderer Glättung). */
    private synchronized Oro oro() {
        Terrain t = terrain;
        double b = clamp01(bank), c = clamp01(cap), cv = clamp01(convection), l = clamp01(lee);
        if (t == null || (b <= 0 && c <= 0 && cv <= 0 && l <= 0)) return null;
        if (t != oroFor || terrainSmooth != oroSmoothFor || oroMap == null) {
            oroMap = oroMap(t, ORO_N, terrainSmooth);
            oroPeak = oroMap[ORO_N * ORO_N * 4];
            oroFor = t;
            oroSmoothFor = terrainSmooth;
        }
        double a = Math.toRadians(windFromDeg);
        return new Oro(t.x0(), t.z0(), t.size(), ORO_N, oroMap, oroPeak, -Math.sin(a), Math.cos(a), b, bankBase, Math.max(bankBase + 100, bankTop),
                Math.max(50, bankReach), c, capBase, Math.max(100, capReach), cv, l);
    }

    static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    /** Werte der Strömungskarte an (x, z), bilinear wie die Grafikkarte (Texelmitten, Rand gehalten); außerhalb 0. */
    public static void oroAt(Oro o, double x, double z, double[] out) {
        double u = (x - o.x0()) / o.size(), v = (z - o.z0()) / o.size();
        out[0] = out[1] = out[2] = out[3] = 0;
        if (u < 0 || v < 0 || u > 1 || v > 1) return;
        int m = o.n();
        double fx = u * m - 0.5, fy = v * m - 0.5;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double ax = fx - x0, ay = fy - y0;
        float[] mp = o.map();
        for (int c = 0; c < 4; c++) {
            int dx = c & 1, dy = (c >> 1) & 1;
            double wgt = (dx == 1 ? ax : 1 - ax) * (dy == 1 ? ay : 1 - ay);
            int ii = Math.max(0, Math.min(m - 1, x0 + dx)), jj = Math.max(0, Math.min(m - 1, y0 + dy));
            int k = (jj * m + ii) * 4;
            for (int q = 0; q < 4; q++) out[q] += wgt * mp[k + q];
        }
    }

    /** Verschiebung des Felds der unteren Schicht durch das Gelände: Haufen über Land, Lücken im Lee (wie oroShift im Shader). */
    static double oroShift(Oro o, double[] g) {
        double up = g[1] * o.windToX() + g[2] * o.windToZ();
        return o.convection() * 0.22 * (smooth(40, 1200, g[0]) - 0.55) - o.lee() * 0.22 * smooth(0.03, 0.22, -up);
    }

    // ------------------------------------------------------------------ Eichung

    /** Feld der Wetterkarte für eine Art (Mischung aus Zellen und Flecken). */
    static double field(CloudType t, double[] w) { return w[0] + (w[1] - w[0]) * t.strat; }

    private synchronized double[] quantile(CloudType t) {
        return quantiles.computeIfAbsent(t, k -> {
            byte[] m = Noise.weather();
            int n = Noise.WEATHER * Noise.WEATHER;
            double[] v = new double[n];
            for (int i = 0; i < n; i++) v[i] = (m[i * 4] & 255) / 255.0 + ((m[i * 4 + 1] & 255) - (m[i * 4] & 255)) / 255.0 * k.strat;
            Arrays.sort(v);
            return v;
        });
    }

    private synchronized double[] highQuantile() {
        if (highQuantile == null) {
            byte[] m = Noise.weather();
            int n = Noise.WEATHER * Noise.WEATHER;
            double[] v = new double[n];
            for (int i = 0; i < n; i++) v[i] = (m[i * 4 + 3] & 255) / 255.0;
            Arrays.sort(v);
            highQuantile = v;
        }
        return highQuantile;
    }

    static double thresholdFor(double[] sorted, double fraction) {
        if (fraction <= 0) return 2;
        if (fraction >= 1) return -1;
        return sorted[(int) Math.min(sorted.length - 1, Math.round((1 - fraction) * sorted.length))] - SOFT / 2;
    }

    /** Schwelle der unteren Schicht für die eingestellte Bedeckung. */
    public double threshold() { return thresholdFor(quantile(type), areaFraction(type, oktas)); }

    /**
     * Welcher Flächenanteil auf der Karte bewölkt sein muss, damit man von unten {@code oktas/8} des Himmels bedeckt
     * sieht. Die Grundform frisst Lücken in die Zellen (lockere Haufen brauchen mehr Fläche), hohe Türme verdecken
     * schräg mehr Himmel, als sie Fläche haben; die Tabelle ist mit
     * {@link #observedCoverage} gemessen (256 Standorte gleichmäßig über die Kachel, Richtungen bis 70° vom Zenit) und wird in der Prüfung
     * nachgemessen.
     */
    public static double areaFraction(CloudType t, double oktas) {
        double[] tab = AREA.get(t);
        double o = Math.max(0, Math.min(8, oktas));
        int i = Math.min(7, (int) o);
        return tab[i] + (tab[i + 1] - tab[i]) * (o - i);
    }

    static final Map<CloudType, double[]> AREA = new EnumMap<>(CloudType.class);
    static {
        AREA.put(CloudType.CUMULUS, new double[]{0, 0.217, 0.412, 0.574, 0.725, 0.795, 0.813, 0.883, 1});
        AREA.put(CloudType.STRATOCUMULUS, new double[]{0, 0.205, 0.377, 0.529, 0.660, 0.762, 0.841, 0.923, 1});
        AREA.put(CloudType.STRATUS, new double[]{0, 0.121, 0.260, 0.396, 0.515, 0.632, 0.757, 0.882, 1});
        AREA.put(CloudType.NIMBOSTRATUS, new double[]{0, 0.123, 0.242, 0.368, 0.504, 0.620, 0.742, 0.863, 1});
        AREA.put(CloudType.CUMULONIMBUS, new double[]{0, 0.026, 0.060, 0.100, 0.149, 0.215, 0.312, 0.469, 1});
    }

    /** Schwelle der hohen Schicht. */
    public double highThreshold() { return thresholdFor(highQuantile(), Math.max(0, Math.min(1, highCover))); }

    // ------------------------------------------------------------------ Zustand

    /** Ein fester Stand für Abfragen und die Grafikkarte. */
    public record Snapshot(CloudType type, double oktas, double threshold, double highCover, double highThreshold, double offX,
                           double offZ, double time, double precipitation, double evolve, Quality quality, double maxDistance,
                           double windSpeed, double windFromDeg, double lens, double lensFromDeg, double base, double shadowHeight,
                           Oro oro, boolean smoothNoise) {
        public double tile() { return type.cell * 16; }
        /** Seit LituyaBay1958 W7: unterste Höhe aller Wolken (Schicht und Bank, m). */
        public double shellLow() { return oro != null && oro.bank() > 0 ? Math.min(base, oro.bankBase() - 60) : base; }
        /** Seit LituyaBay1958 W7: Obergrenze von Schicht und Bank (darüber nur noch Kappe, Linsen und hohe Schicht, m). */
        public double lowHigh() { return oro != null && oro.bank() > 0 ? Math.max(top(), oro.bankHigh()) : top(); }
        /** Seit LituyaBay1958 W7: oberste Höhe der Wolken unter der hohen Schicht (mit der Kappe, m). */
        public double shellHigh() { double c = oro != null ? oro.capTop() : Double.NaN; return Double.isNaN(c) ? lowHigh() : Math.max(lowHigh(), c); }
        /** Seit Brocken Phase 3: höchste Obergrenze der unteren Schicht (m über dem Meer). */
        public double top() { return base + type.maxThickness; }
        /** Kachel der Grundform: Haufen und Türme mit Blumenkohl von etwa 1 km, Schichten mit der Zelle wachsend. */
        public double noiseTile() { return 4000 + (type.cell * 2.2 - 4000) * type.strat * type.strat; }
        public double detailTile() { return noiseTile() * 0.115; }
        /** Dichte im Kern einer Haufenzelle: bei wenig Bedeckung locker, gegen 8/8 geschlossen. */
        public double core() { return CU_CORE + (1 - CU_CORE) * smooth(4, 8, oktas); }
    }

    public synchronized Snapshot snapshot() {
        double b = baseHeight;
        return new Snapshot(type, oktas, threshold(), highCover, highThreshold(), offX, offZ, time, precipitation, evolve, quality,
                maxDistance, windSpeed, windFromDeg, Math.max(0, Math.min(1, lens)), lensFromDeg, Double.isNaN(b) ? type.base : b, shadowHeight, oro(),
                smoothNoise);
    }

    // ------------------------------------------------------------------ Dichte (wie cloud_common.glsl)

    public static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static double remap(double v, double lo, double hi, double a, double b) {
        return a + (v - lo) / Math.max(1e-6, hi - lo) * (b - a);
    }

    /** Höhe über der gekrümmten Erde für einen Szenenpunkt. */
    public static double height(double x, double y, double z) {
        double yy = y + EARTH_R;
        return Math.sqrt(x * x + yy * yy + z * z) - EARTH_R;
    }

    /** Bedeckung der Wetterkarte an (x, z) ohne Höhe, 0..1. */
    public static double coverage(Snapshot s, double x, double z) {
        double[] w = new double[4];
        Noise.sample2(Noise.weather(), Noise.WEATHER, (x + s.offX()) / s.tile(), (z + s.offZ()) / s.tile(), w);
        double f = field(s.type(), w);
        if (s.oro() != null) { double[] g = new double[4]; oroAt(s.oro(), x, z, g); f += oroShift(s.oro(), g); }
        return smooth(s.threshold(), s.threshold() + SOFT, f);
    }

    /**
     * Auslöschung je m am Szenenpunkt (x, y, z). detail = false lässt das feine Rauschen weg (Lichtweg, Schatten).
     */
    public static double density(Snapshot s, double x, double y, double z, boolean detail) {
        Oro o = s.oro();
        if (o == null) return densityMain(s, x, y, z, detail, 0);
        // seit LituyaBay1958 W7: mit Gelände die untere Schicht (Feld verschoben), dazu Bank und Kappe; das Dichtere gilt
        double h = height(x, y, z);
        if (h < s.shellLow() || h > s.shellHigh()) return 0;
        double[] g = new double[4];
        oroAt(o, x, z, g);
        double dm = densityMain(s, x, y, z, detail, oroShift(o, g));
        double[] cs = oroCover(s, o, g, x, z, h);
        if (cs == null || cs[0] * ORO_SIGMA <= dm) return dm;
        double wx = x + s.offX(), wz = z + s.offZ();
        double d = shape(s, cs[0], cs[1], cs[2], wx, wz, h, detail, ORO_TILE);
        return Math.max(dm, d * ORO_SIGMA);
    }

    /**
     * Seit LituyaBay1958 W7: Bedeckung (0..1), Glätte und Lage im Profil von Bank und Kappe am Punkt, ohne die Form, oder
     * null. Die Bank hängt zwischen ihrer Unter- und Obergrenze am Hang (bis {@code bankReach} über dem geglätteten
     * Gelände, nur wo es steigt: Steigung ab 3 %, voll ab 14 %), am Luvhang dichter, mit Lücken aus der Wetterkarte, die langsam am Berg entlangziehen; die Kappe liegt als
     * glatte Haube um die Gipfel, die das Kondensationsniveau erreichen (seit Runde 1b vom Niveau bis {@code capReach} über
     * die geglättete Hülle der Gipfel, zu ihrem Rand hin weich und flacher), etwas nach Lee versetzt. Wie oroCover im Shader.
     */
    public static double[] oroCover(Snapshot s, Oro o, double[] g, double x, double z, double h) {
        double cb = 0, ttb = 0;
        if (o.bank() > 0) {
            double hag = h - g[0];
            double band = smooth(o.bankBase() - 60, o.bankBase() + 60, h) * (1 - smooth(o.bankTop() - 120, o.bankTop() + 60, h));
            double hug = smooth(-250, -60, hag) * (1 - smooth(o.bankReach() * 0.4, o.bankReach(), hag));
            if (band * hug > 0) {
                double up = g[1] * o.windToX() + g[2] * o.windToZ();
                double ww = 0.35 + 0.65 * smooth(-0.02, 0.18, up);
                double[] q = new double[4];
                Noise.sample2(Noise.weather(), Noise.WEATHER, (x + s.offX() * 0.25) / BANK_TILE, (z + s.offZ() * 0.25) / BANK_TILE, q);
                double holes = smooth(0.92 - 0.9 * o.bank(), 1.08 - 0.9 * o.bank(), q[2]);
                // nur am Hang: über flachem Wasser und Ebenen hebt nichts die Luft (dort ist es die Schicht)
                double steil = smooth(0.03, 0.14, Math.sqrt(g[1] * g[1] + g[2] * g[2]));
                cb = band * hug * ww * holes * steil;
                ttb = Math.max(0, Math.min(1, (hag + 60) / o.bankReach()));
            }
        }
        double cc = 0, ttc = 0;
        if (o.cap() > 0 && h > o.capBase() - 50) {
            // seit Runde 1b: die Kappe hüllt die Gipfel ein, die über das Kondensationsniveau ragen – von dort bis eine
            // Reichweite über die Hülle der Gipfel (Kanal A, geglättet), ein Stück mit dem Wind versetzt; zum Rand der
            // Hülle hin weich und flacher (Linsenform) statt an Zellkanten abgeschnitten
            double[] gc = new double[4];
            oroAt(o, x - o.windToX() * CAP_SHIFT, z - o.windToZ() * CAP_SHIFT, gc);
            double over = smooth(o.capBase() - 200, o.capBase() + 250, gc[3]);
            if (over > 0) {
                double inBand = smooth(o.capBase() - 50, o.capBase() + 90, h);
                double hood = 1 - smooth(o.capReach() * 0.45, o.capReach(), h - gc[3]);
                cc = o.cap() * over * inBand * hood;
                ttc = Math.max(0, Math.min(1, (h - o.capBase()) / Math.max(100, gc[3] + o.capReach() - o.capBase())));
            }
        }
        if (cb <= 0 && cc <= 0) return null;
        return cc > cb ? new double[]{cc, 0.95, ttc} : new double[]{cb, 0.55, ttb};
    }

    /** Die untere Schicht wie bisher; df verschiebt das Feld der Wetterkarte (seit LituyaBay1958 W7, sonst 0). */
    static double densityMain(Snapshot s, double x, double y, double z, boolean detail, double df) {
        CloudType t = s.type();
        double h = height(x, y, z), b0 = s.base();
        if (h < b0 || h > b0 + t.maxThickness) return 0;
        double wx = x + s.offX(), wz = z + s.offZ();
        double[] w = new double[4];
        Noise.sample2(Noise.weather(), Noise.WEATHER, wx / s.tile(), wz / s.tile(), w);
        double f = field(t, w) + df, thr = s.threshold();
        double tall = smooth(thr, thr + 0.15 + 0.2 * t.strat - 0.07 * t.anvil, f);
        double top = b0 + t.thickness + (t.maxThickness - t.thickness) * tall * tall;
        double tt = (h - b0) / (top - b0);
        // Haufen werden nach oben schmaler (Kuppe statt Säule)
        double tc = Math.max(0, Math.min(1, tt)), rise = (1 - t.strat) * (0.2 - 0.12 * t.anvil) * tc * tc;
        double cov = smooth(thr + rise, thr + rise + SOFT, f);
        double anv = 0;
        if (t.anvil > 0) {
            // Amboss nur über starken Türmen, ein Drittel einer Zelle über sie hinaus
            double ta = (h - b0) / t.maxThickness;
            double band = smooth(0.8, 0.88, ta) * (1 - smooth(0.94, 0.99, ta));
            if (band > 0) {
                double u = wx / s.tile(), v = wz / s.tile(), fn = f;
                double[] q = new double[4];
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    Noise.sample2(Noise.weather(), Noise.WEATHER, u + ANVIL_R * (float) Math.cos(a), v + ANVIL_R * (float) Math.sin(a), q);
                    fn = Math.max(fn, field(t, q));
                }
                anv = t.anvil * band * smooth(thr + 0.06, thr + 0.14, fn);
            }
        }
        double prof = 0;
        if (tt <= 1) {
            double cu = smooth(0, 0.06, tt) * (1 - smooth(0.45 + 0.35 * t.anvil, 1, tt)), st = smooth(0, 0.18, tt) * (1 - smooth(0.65, 1, tt));
            prof = cu + (st - cu) * t.strat;
        }
        double core = s.core(), fill = Math.max(t.strat, t.anvil), cc = cov * prof * (core + (1 - core) * fill), c = Math.max(cc, anv);
        double sheet = Math.max(t.strat, smooth(0, 0.1, anv - cc));   // Schicht oder Amboss: glatt
        if (c <= 0) return 0;
        return shape(s, c, sheet, tt, wx, wz, h, detail, s.noiseTile()) * t.sigma;
    }

    /** Form aus Grundform und Fransen über einer Bedeckung c (wie im Shader); ohne die Auslöschung. */
    static double shape(Snapshot s, double c, double sheet, double tt, double wx, double wz, double h, boolean detail, double ns) {
        double[] b = new double[4];
        Noise.sample3(Noise.base(), Noise.BASE, wx / ns, (h - s.time() * 0.6 * s.evolve()) / ns, wz / ns, b, s.smoothNoise());
        double low = b[1] * 0.625 + b[2] * 0.25 + b[3] * 0.125;
        double spread = Math.max(0, Math.min(1, (b[0] * 0.7 + low * 0.3 - 0.2) / 0.6)), packed = remap(b[0], low - 1, 1, 0, 1);
        double base = spread + (packed - spread) * sheet;
        base = remap(base, 1 - c, 1, 0, 1) * c;
        if (base <= 0) return 0;
        if (detail) {
            double ds = ns * 0.115;
            double[] d = new double[4];
            Noise.sample3(Noise.detail(), Noise.DETAIL, wx / ds, (h - s.time() * 1.5 * s.evolve()) / ds, wz / ds, d, s.smoothNoise());
            double hf = d[0] * 0.625 + d[1] * 0.25 + d[2] * 0.125;
            double mod = hf + (1 - 2 * hf) * Math.max(0, Math.min(1, tt * 4));
            base = remap(base, mod * (0.55 - 0.25 * sheet), 1, 0, 1);
        }
        return Math.max(0, base);
    }

    /** Optische Dicke der hohen Schicht senkrecht an (x, z). */
    public static double highTau(Snapshot s, double x, double z) {
        if (s.highCover() <= 0) return 0;
        double[] w = new double[4];
        double qx = x + s.offX() * 1.5, qz = z + s.offZ() * 1.5;
        double[] f = new double[4];
        Noise.sample2(Noise.weather(), Noise.WEATHER, qx / (HIGH_TILE * 3), qz / HIGH_TILE, w);
        Noise.sample2(Noise.weather(), Noise.WEATHER, qx / (HIGH_TILE * 2), qz / (HIGH_TILE * 0.12), f);
        return HIGH_TAU * smooth(s.highThreshold(), s.highThreshold() + 0.2, w[3]) * (0.35 + 0.65 * f[2]);
    }

    /**
     * Seit Glacier Phase 3: optische Dicke der Linsenlage {@code slice} (0 unten) senkrecht an (x, z). Längs des Winds
     * die Kämme der stehenden Welle (Kosinus, nur die Spitzen), quer dazu Abschnitte aus einem Rauschen je Kamm; obere Lagen sind kleiner (Stapel). Ortsfest, ohne Drift. Wie lensTau in cloud_common.glsl.
     */
    public static double lensTau(Snapshot s, double x, double z, int slice) {
        if (s.lens() <= 0) return 0;
        double a = Math.toRadians(s.lensFromDeg()), tx = -Math.sin(a), tz = Math.cos(a);
        double u = x * tx + z * tz, v = -x * tz + z * tx;
        double cu = 0.5 + 0.5 * Math.cos(2 * Math.PI * u / LENS_WAVE);
        if (cu < 0.68) return 0;
        int k = (int) Math.floor(u / LENS_WAVE + 0.5);
        double q = v / LENS_CELL;
        int j = (int) Math.floor(q);
        double f = q - j, n = lensHash(k, j) + (lensHash(k, j + 1) - lensHash(k, j)) * f * f * (3 - 2 * f);
        // Mandelform: wo das Rauschen hoch ist, ist die Linse breit und dick, zu den Enden läuft sie spitz aus
        double thr = 1 - 0.55 * s.lens() + 0.07 * slice;
        double b2 = Math.max(0, Math.min(1, (n - thr) / 0.4));
        if (b2 <= 0) return 0;
        double c0 = 1 - (0.30 - 0.06 * slice) * Math.sqrt(b2);
        double bu = Math.max(0, Math.min(1, (cu - c0) / Math.max(1e-6, 1 - c0)));
        return LENS_TAU * Math.sqrt(s.lens()) * Math.pow(bu, 0.6) * Math.sqrt(b2);
    }

    /** Ganzzahliges Rauschen 0..1 je Kamm k und Abschnitt j (bitgleich mit lensHash in cloud_common.glsl). */
    static double lensHash(int k, int j) {
        int h = k * 374761393 + j * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 16777216.0;
    }

    /** Abstände t0, t1 entlang d (Einheitsvektor) vom Punkt o, in denen der Strahl zwischen den Höhen h0 und h1 läuft. */
    public static double[] shell(double[] o, double[] d, double h0, double h1) {
        double ox = o[0], oy = o[1] + EARTH_R, oz = o[2];
        double b = ox * d[0] + oy * d[1] + oz * d[2], oo = ox * ox + oy * oy + oz * oz;
        double r0 = EARTH_R + h0, r1 = EARTH_R + h1, r = Math.sqrt(oo);
        double disc1 = b * b - (oo - r1 * r1);
        if (disc1 < 0) return null;
        double far1 = -b + Math.sqrt(disc1), near1 = -b - Math.sqrt(disc1);
        double disc0 = b * b - (oo - r0 * r0);
        if (r < r0) {
            // unter der Schicht: vom inneren zum äußeren Rand
            return new double[]{-b + Math.sqrt(Math.max(0, disc0)), far1};
        }
        if (r <= r1) {
            // in der Schicht: bis zum inneren Rand (nach unten) oder zum äußeren
            if (disc0 > 0 && -b - Math.sqrt(disc0) > 0) return new double[]{0, -b - Math.sqrt(disc0)};
            return new double[]{0, far1};
        }
        // darüber
        if (near1 < 0) return null;
        if (disc0 > 0 && -b - Math.sqrt(disc0) > 0) return new double[]{near1, -b - Math.sqrt(disc0)};
        return new double[]{near1, far1};
    }

    /**
     * Wie viel direktes Licht (0..1) auf der Fläche der Schattenkarte ({@link Snapshot#shadowHeight}, sonst
     * Meereshöhe) an (x, z) ankommt, aus Richtung l (zum Gestirn). 16 Schritte wie shadow.frag (Kanal R).
     */
    public static double shadow(Snapshot s, double x, double z, double[] l) { return shadow(s, x, s.shadowHeight(), z, l); }

    /**
     * Seit Brocken Phase 3: direktes Licht (0..1) am Punkt (x, y, z) in beliebiger Höhe: liegt er über der unteren
     * Schicht, wirft nur noch die hohe Schicht (und die Lentikularis) Schatten; in der Schicht nur, was über ihm liegt.
     */
    public static double shadow(Snapshot s, double x, double y, double z, double[] l) {
        double[] d = lightRay(l);
        return Math.exp(-(lowTau(s, x, y, z, d) + upperTau(s, x, y, z, d)) * SHADOW_SOFT);
    }

    /** Seit Brocken Phase 3: Licht nur durch die hohe Schicht und die Linsen (Kanal G der Schattenkarte), für Punkte über der unteren Schicht. */
    public static double upperShadow(Snapshot s, double x, double y, double z, double[] l) {
        return Math.exp(-upperTau(s, x, y, z, lightRay(l)) * SHADOW_SOFT);
    }

    static double[] lightRay(double[] l) {
        double ly = Math.max(0.05, l[1]);
        double n = Math.sqrt(l[0] * l[0] + ly * ly + l[2] * l[2]);
        return new double[]{l[0] / n, ly / n, l[2] / n};
    }

    static double lowTau(Snapshot s, double x, double y, double z, double[] d) {
        double[] seg = shell(new double[]{x, y, z}, d, s.shellLow(), s.lowHigh());
        double tau = 0;
        if (seg != null) {
            double t0 = seg[0], t1 = Math.min(seg[1], seg[0] + 40_000), dt = (t1 - t0) / 16;
            for (int k = 0; k < 16; k++) {
                double t = t0 + (k + 0.5) * dt;
                tau += density(s, x + d[0] * t, y + d[1] * t, z + d[2] * t, false) * dt;
            }
        }
        return tau;
    }

    static double upperTau(Snapshot s, double x, double y, double z, double[] d) {
        double tau = 0;
        // seit LituyaBay1958 W7: die Kappe über der Schicht (8 Schritte)
        if (s.shellHigh() > s.lowHigh() + 1) {
            double[] seg = shell(new double[]{x, y, z}, d, s.lowHigh(), s.shellHigh());
            if (seg != null) {
                double t0 = seg[0], t1 = Math.min(seg[1], seg[0] + 20_000), dt = (t1 - t0) / 8;
                for (int k = 0; k < 8; k++) {
                    double t = t0 + (k + 0.5) * dt;
                    tau += density(s, x + d[0] * t, y + d[1] * t, z + d[2] * t, false) * dt;
                }
            }
        }
        double th = Math.max(0, HIGH_HEIGHT - y) / d[1];
        tau += highTau(s, x + d[0] * th, z + d[2] * th) / d[1];
        for (int i = 0; i < LENS_SLICES && s.lens() > 0; i++) {
            double tl = Math.max(0, LENS_HEIGHT + i * LENS_STEP - y) / d[1];
            tau += lensTau(s, x + d[0] * tl, z + d[2] * tl, i) / d[1];
        }
        return tau;
    }

    /** Regenvorhang 0..1 an (x, z) (für {@code rain}, Phase 7): Niederschlag unter dichten Stellen. */
    public static double rain(Snapshot s, double x, double z) {
        if (s.precipitation() <= 0) return 0;
        double[] w = new double[4];
        Noise.sample2(Noise.weather(), Noise.WEATHER, (x + s.offX()) / s.tile(), (z + s.offZ()) / s.tile(), w);
        double f = field(s.type(), w), thr = s.threshold();
        if (s.oro() != null) { double[] g = new double[4]; oroAt(s.oro(), x, z, g); f += oroShift(s.oro(), g); }
        return s.precipitation() * smooth(thr + 0.1, thr + 0.35, f) * (0.6 + 0.4 * w[2]);
    }

    /**
     * Bedeckung, wie man sie von unten sieht: Anteil der Richtungen bis 70° vom Zenit, deren Strahl mehr als
     * optische Dicke 1 durch Wolken läuft. Für Prüfungen (die Eichung gilt für die Fläche).
     */
    public static double observedCoverage(Snapshot s0, double x, double z, int n) {
        // Standort über die Drift verschieben und am Ursprung messen: die Szene ist eine Tangentialebene, weit draußen
        // läge das Auge sonst schon Hunderte Meter über dem Meer
        Snapshot s = new Snapshot(s0.type(), s0.oktas(), s0.threshold(), s0.highCover(), s0.highThreshold(), s0.offX() + x,
                s0.offZ() + z, s0.time(), s0.precipitation(), s0.evolve(), s0.quality(), s0.maxDistance(), s0.windSpeed(), s0.windFromDeg(), s0.lens(), s0.lensFromDeg(),
                s0.base(), s0.shadowHeight(), s0.oro(), s0.smoothNoise());
        int hit = 0, all = 0;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                double u = (i + 0.5) / n, v = (j + 0.5) / n;
                double cz = 1 - u * (1 - Math.cos(Math.toRadians(70))), sz = Math.sqrt(1 - cz * cz), a = v * 2 * Math.PI;
                double[] d = {sz * Math.sin(a), cz, -sz * Math.cos(a)};
                double[] seg = shell(new double[]{0, 10, 0}, d, s.base(), s.top());
                double tau = 0;
                if (seg != null) {
                    double t0 = seg[0], t1 = Math.min(seg[1], seg[0] + 30_000), dt = (t1 - t0) / 48;
                    for (int k = 0; k < 48 && tau < 1; k++) {
                        double t = t0 + (k + 0.5) * dt;
                        tau += density(s, d[0] * t, 10 + d[1] * t, d[2] * t, true) * dt;
                    }
                }
                if (tau >= 1) hit++;
                all++;
            }
        return hit / (double) all;
    }

    // ------------------------------------------------------------------ für die Szene

    /** Faktor für die Anpassungshelligkeit des Auges: ein bedeckter Himmel ist dunkler als der klare. */
    public double brightnessFactor() {
        double low = Math.pow(Math.max(0, Math.min(8, oktas)) / 8, 1.3) * (1 - type.overcastBrightness);
        double high = Math.max(0, Math.min(1, highCover)) * 0.25;
        return Math.max(0.05, (1 - low) * (1 - high));
    }

    /** „Cumulus 3/8 · Basis 900 m · Gipfel bis 2 500 m · Wind 10 m/s aus 250°“ */
    public String describe() {
        CloudType t = type;
        double b = Double.isNaN(baseHeight) ? t.base : baseHeight;
        String s = String.format(Locale.GERMAN, "%s %.0f/8 · Basis %.0f m · Gipfel bis %.0f m · Zug %.0f m/s aus %03.0f°", t.label,
                oktas, b, b + t.maxThickness, windSpeed, windFromDeg);
        if (highCover > 0) s += String.format(Locale.GERMAN, " · hoher Schleier %.0f %%", highCover * 100);
        if (precipitation > 0) s += String.format(Locale.GERMAN, " · Niederschlag %.0f %%", precipitation * 100);
        if (lens > 0.02) s += String.format(Locale.GERMAN, " · Lentikularis %.0f %%", lens * 100);
        if (terrain != null && bank > 0) s += String.format(Locale.GERMAN, " · Bank %.0f %% am Hang %.0f–%.0f m", bank * 100, bankBase, bankTop);
        if (terrain != null && cap > 0) s += String.format(Locale.GERMAN, " · Kappe %.0f %% über %.0f m", cap * 100, capBase);
        if (terrain != null && convection > 0) s += String.format(Locale.GERMAN, " · Quellwolken über Land %.0f %%", convection * 100);
        return s;
    }
}
