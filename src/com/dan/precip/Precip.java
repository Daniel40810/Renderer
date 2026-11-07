package com.dan.precip;

import java.util.Locale;

/**
 * Niederschlag um das Auge: Eingänge, Böen, Karte der Schauer und der Zustand für die Grafikkarte. Gerechnet wird mit
 * echten Größen ({@link Drops}): Um das Auge schweben die Tropfen in voller echter Dichte, und zwar die größeren – wie
 * groß der kleinste gezeichnete ist, folgt aus der Zahl der Tropfen der Stufe und der Regenmenge (bei 5 mm/h und der
 * Stufe MEDIUM 1,3 mm). Jeder Tropfen fällt mit seinem Luftwiderstand durch den böigen Wind, schwingt, und schlägt auf
 * dem Wasser oder dem Grund ein; die Grafikkarte führt seinen Zustand ({@code gl/PrecipGl}). Dahinter Schichten bis rund
 * 60 m, dahinter die Vorhänge der Schauer bis 8 km.
 * <p>
 * Die Belichtung ist eine echte Größe: Ein Bildpunkt sieht einen Tropfen nur D / (v · T) der Zeit. Mit 1/30 s ist
 * Regen darum kaum zu sehen (wie auf Filmen), mit 1/250 s klar (das Auge folgt ihm schärfer), mit 1/2000 s sieht man die
 * Form der Tropfen. {@link #shutter} ist in Sekunden; {@link #timeScale} verlangsamt alles (Zeitlupe) – die Belichtung
 * gilt dann in der langsamen Zeit.
 * <p>
 * Nur Java 21. Das Paket kennt weder Wolken noch Meer noch die Szene: Wo es regnet, sagt ein {@link Curtain}, was unten
 * ist, eine Höhenkarte von außen ({@code PrecipGl.Surface}).
 */
public final class Precip {

    /**
     * Stufen: simulierte Tropfen, halbe Breite und Höhe des Kastens um das Auge (m), Spritzteilchen, Wellenfeld
     * (Zellen je Seite, Zweierpotenz, und Zellgröße in m), feines Wellenfeld für die Nähe (0: keins), Schichten,
     * Schritte im Vorhang.
     * <p>
     * Seit W7b Runde 5c ist das Wellenfeld fein statt weit: Die Steigung des Regens sitzt in den kurzen Wellen von 3 bis
     * 16 mm ({@link Ripples#slopeVariance}); ein 8-mm-Raster trug 2 % davon, eines mit 2 mm trägt 66 %. Das Fenster
     * wird dafür kleiner (1,5 bis 3 m); draußen nehmen die Wasser zwei gedrehte Abfragen derselben Fläche (gleiche
     * Statistik), und was die Mip-Stufen wegmitteln, kommt als Facetten zurück.
     */
    public enum Quality {
        LOW(4_000, 2.5, 2.2, 4_096, 256, 0.006, 0, 0, 4, 10),
        MEDIUM(12_000, 3.0, 3.0, 12_288, 512, 0.004, 0, 0, 6, 14),
        HIGH(24_000, 3.5, 3.5, 24_576, 1024, 0.003, 0, 0, 8, 20),
        ULTRA(48_000, 4.5, 4.0, 49_152, 1024, 0.002, 0, 0, 10, 32);
        public final int drops, splashes, rippleRes, fineRes, layers, curtainSteps;
        public final double box, boxY, rippleCell, fineCell;
        Quality(int drops, double box, double boxY, int splashes, int rippleRes, double rippleCell, int fineRes, double fineCell,
                int layers, int curtainSteps) {
            this.drops = drops; this.box = box; this.boxY = boxY; this.splashes = splashes; this.rippleRes = rippleRes;
            this.rippleCell = rippleCell; this.fineRes = fineRes; this.fineCell = fineCell; this.layers = layers; this.curtainSteps = curtainSteps;
        }
        /** Rauminhalt des Kastens (m³). */
        public double volume() { return 2 * box * 2 * box * 2 * boxY; }
        /** Kantenlänge (m) des Wellenfelds. */
        public double rippleSize() { return rippleRes * rippleCell; }
    }

    /** Wo es regnet: 0 (trocken) bis 1 (volle Menge) am Ort x, z. Muss schnell sein (rund 10 000 Fragen je halbe Sekunde). */
    @FunctionalInterface public interface Curtain { double density(double x, double z); }

    /**
     * Worauf der Regen um das Auge fällt (rund 4 000 Fragen, wenn das Auge sich bewegt hat oder jede Sekunde). Ohne
     * Grund ({@code ground == null}) liegt überall Wasser auf {@link #waterLevel}.
     */
    public interface Ground {
        /** Höhe (m) der Fläche, auf die ein Tropfen fällt: Gelände oder Wasserspiegel, das Höhere. */
        double height(double x, double z);
        /** Liegt dort Wasser (tief genug für Ringe)? */
        boolean water(double x, double z);
        /** Oberkante des Kronendachs (m), NaN ohne Wald. */
        default double canopy(double x, double z) { return Double.NaN; }
    }

    /** Raster des Grunds um das Auge: Ecke, Kante (m), Zellen je Seite, je Zelle Höhe, Art (0 Wasser, 1 Grund), Kronendach (−1e9 ohne), frei. */
    public record Grid(double x0, double z0, double size, int n, float[] cells, int version) {
        public double cell() { return size / n; }
        /** Werte der Zelle am Ort (am Rand die äußerste). */
        public int index(double x, double z) {
            int i = (int) Math.floor((x - x0) / size * n), j = (int) Math.floor((z - z0) / size * n);
            return (Math.max(0, Math.min(n - 1, j)) * n + Math.max(0, Math.min(n - 1, i))) * 4;
        }
        public double height(double x, double z) { return cells[index(x, z)]; }
        public boolean water(double x, double z) { return cells[index(x, z) + 1] < 0.5; }
        public double canopy(double x, double z) { return cells[index(x, z) + 2]; }
    }

    /** Zellen je Seite im Raster des Grunds. */
    public static final int GROUND_N = 64;

    // ------------------------------------------------------------------ Eingänge

    /** Regen in mm/h unter dem dichtesten Schauer. */
    public volatile double rate = 2;
    /** Art der Verteilung (Landregen, Niesel, Schauer). */
    public volatile Drops.Dsd dsd = Drops.Dsd.LANDREGEN;
    /** Wind 10 m über Grund (m/s), woher (Grad von Nord), Böigkeit 0..1 (Standardabweichung der Böen / Mittel). */
    public volatile double windSpeed = 5, windFromDeg = 240, gust = 0.25;
    /** Stärke der kleinen Wirbel um das Auge, Anteil des Winds [G]. */
    public volatile double turbulence = 0.15;
    /** Belichtungszeit (s) und Zeitmaß (1 = Echtzeit, 0,05 = Zeitlupe). */
    public volatile double shutter = 1 / 250.0, timeScale = 1;
    /**
     * Blende (Durchmesser in m) und Schärfe (Abstand in m) von Auge oder Kamera: Ein Tropfen im Abstand z wird auf
     * einen Kreis von aperture · |1 − z / focus| verschmiert (Strahlenoptik der dünnen Linse). Vorgabe wie das Auge am
     * trüben Tag: Pupille 3 mm, scharf auf 4 m [G]. Ganz nahe Tropfen sind so große, blasse Scheiben statt weißer Flecken.
     */
    public volatile double aperture = 0.003, focus = 4;
    /** Wolkenuntergrenze (m): bis dahin reichen die Vorhänge; darüber kein Regen. */
    public volatile double cloudBase = 800;
    /** Wasserspiegel (m), wo die Höhenkarte nichts sagt. */
    public volatile double waterLevel;
    public volatile Quality quality = Quality.MEDIUM;
    public volatile int seed = 1958;
    /** null: überall dieselbe Menge (Landregen). */
    public volatile Curtain curtain;
    /** Auge. */
    public volatile double eyeX, eyeY = 10, eyeZ;
    /** Unterstände (je x, z, Halbmesser, Oberkante; höchstens vier). */
    public volatile double[] shelter = new double[0];
    /** Worauf es fällt (null: Wasser auf {@link #waterLevel}). */
    public volatile Ground ground;
    /**
     * Im Wald: Anteil des Regens, der zwischen den Kronen frei durchfällt, und Größe der Tropfen, die von Nadeln und
     * Zweigen abtropfen (mm) [G]. Der Rest des Regens (abzüglich rund 25 % Verdunstung vom nassen Dach) kommt als
     * diese großen Tropfen an.
     */
    public volatile double throughfall = 0.3, interception = 0.25, dripMin = 3.0, dripMax = 5.0;

    /** Karte der Schauer: Zellen je Seite und Kantenlänge (m). */
    public static final int MAP_N = 96;
    public static final double MAP_SIZE = 16_000;
    /** Kleinste und größte gezeichnete Größe (mm); darüber zerplatzen Tropfen im Fall. */
    public static final double DMIN_FLOOR = 0.3, DMAX = 6.0;

    private double time, mapTimer = 1e9, mapX0, mapZ0, gustX, gustZ, lastDt;
    private long frame;
    private float[] map = new float[0];
    private int mapVersion;
    private Grid grid = new Grid(0, 0, 1, 1, new float[]{0, 0, -1e9f, 0}, 0);
    private double gridTimer = 1e9;
    private Ground gridFor;
    private double gridLevel = Double.NaN;
    private final java.util.SplittableRandom rng;
    private volatile Snapshot snapshot;

    public Precip() {
        rng = new java.util.SplittableRandom(seed);
        snapshot = build();
    }

    // ------------------------------------------------------------------ Voreinstellungen

    public static Precip of(double rate, Drops.Dsd dsd) { Precip p = new Precip(); p.rate = rate; p.dsd = dsd; p.step(0); return p; }
    public static Precip drizzle() { return of(0.4, Drops.Dsd.NIESEL); }
    public static Precip moderate() { return of(5, Drops.Dsd.LANDREGEN); }
    public static Precip heavy() { return of(15, Drops.Dsd.LANDREGEN); }
    public static Precip shower() { return of(25, Drops.Dsd.SCHAUER); }

    // ------------------------------------------------------------------ Lauf

    /** Wind als Vektor (wohin er weht) in m/s: {x, z}, ohne Böen. */
    public double[] windTo() {
        double a = Math.toRadians(windFromDeg + 180);
        return new double[]{Math.sin(a) * windSpeed, -Math.cos(a) * windSpeed};
    }

    /**
     * Einen Schritt weiter: dt in Sekunden Szenenzeit (wird mit {@link #timeScale} nicht noch einmal multipliziert;
     * die App gibt die Zeit, die vergehen soll). Böen als Ornstein-Uhlenbeck-Vorgang (Korrelationszeit 6 s), alle halbe
     * Sekunde die Karte der Schauer.
     */
    public synchronized void step(double dt) {
        if (dt < 0) dt = 0;
        time += dt;
        lastDt = dt;
        frame++;
        double sd = gust * windSpeed, tau = 6, a = Math.exp(-dt / tau), b = Math.sqrt(Math.max(0, 1 - a * a)) * sd;
        gustX = gustX * a + b * gauss();
        gustZ = gustZ * a + b * gauss() * 0.6;   // quer zum Wind schwächer
        mapTimer += dt;
        double cell = MAP_SIZE / MAP_N;
        double cx = Math.floor(eyeX / cell) * cell - MAP_SIZE / 2, cz = Math.floor(eyeZ / cell) * cell - MAP_SIZE / 2;
        if (mapTimer >= 0.5 || cx != mapX0 || cz != mapZ0 || (map.length == 0) != (curtain == null)) rebuildMap(cx, cz);
        gridTimer += dt;
        double half = groundHalf(quality), gc = 2 * half / GROUND_N;
        double gx = eyeX - grid.x0() - grid.size() / 2, gz = eyeZ - grid.z0() - grid.size() / 2;
        if (gridTimer >= 1 || Math.abs(gx) > gc || Math.abs(gz) > gc || Math.abs(grid.size() - 2 * half) > 1e-9 || gridFor != ground
                || gridLevel != waterLevel) rebuildGrid(half);
        snapshot = build();
    }

    private double gauss() { return Math.sqrt(-2 * Math.log(1 - rng.nextDouble())) * Math.cos(2 * Math.PI * rng.nextDouble()); }

    private void rebuildMap(double x0, double z0) {
        mapTimer = 0;
        mapX0 = x0;
        mapZ0 = z0;
        Curtain c = curtain;
        if (c == null) {
            if (map.length != 0) mapVersion++;
            map = new float[0];
            return;
        }
        double cell = MAP_SIZE / MAP_N;
        float[] m = new float[MAP_N * MAP_N];
        for (int j = 0; j < MAP_N; j++)
            for (int i = 0; i < MAP_N; i++)
                m[j * MAP_N + i] = (float) Math.max(0, Math.min(1, c.density(x0 + (i + 0.5) * cell, z0 + (j + 0.5) * cell)));
        map = m;
        mapVersion++;
    }

    /** Halbe Kante (m) des Rasters des Grunds: Kasten und Wellenfeld (das bis ein Viertel seiner Kante vor dem Auge liegen kann). */
    public static double groundHalf(Quality q) { return Math.max(q.box + 0.5, 0.75 * q.rippleSize() + 1); }

    private void rebuildGrid(double half) {
        gridTimer = 0;
        gridFor = ground;
        gridLevel = waterLevel;
        int n = GROUND_N;
        double cell = 2 * half / n, x0 = Math.floor((eyeX - half) / cell) * cell, z0 = Math.floor((eyeZ - half) / cell) * cell;
        float[] c = new float[n * n * 4];
        Ground g = ground;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double x = x0 + (i + 0.5) * cell, z = z0 + (j + 0.5) * cell;
                int o = (j * n + i) * 4;
                if (g == null) {
                    c[o] = (float) waterLevel;
                    c[o + 2] = -1e9f;
                } else {
                    c[o] = (float) g.height(x, z);
                    c[o + 1] = g.water(x, z) ? 0 : 1;
                    double cp = g.canopy(x, z);
                    c[o + 2] = Double.isNaN(cp) ? -1e9f : (float) cp;
                }
            }
        grid = new Grid(x0, z0, 2 * half, n, c, grid.version() + 1);
    }

    /** Neu anfangen: Zeit, Böen, Karte. */
    public synchronized void reset() {
        time = gustX = gustZ = 0;
        mapTimer = 1e9;
        gridTimer = 1e9;
        map = new float[0];
        step(0);
    }

    private Snapshot build() {
        Quality q = quality;
        double r = Math.max(0, rate), lam = dsd.lambda(Math.max(0.01, r)), n0 = dsd.n0(Math.max(0.01, r));
        // die größeren Tropfen in voller Dichte: über dmin schweben genau so viele je m³, wie der Kasten fasst
        double dmin = r <= 0 ? DMAX : Math.max(DMIN_FLOOR, Math.min(3.0, Drops.dminFor(n0, lam, q.drops / q.volume(), DMAX)));
        double[] w = windTo();
        double rho = Drops.airDensity(eyeY);
        double[] sh = shelter;
        float[] shf = new float[Math.min(16, sh.length - sh.length % 4)];
        for (int i = 0; i < shf.length; i++) shf[i] = (float) sh[i];
        return new Snapshot(time, lastDt, frame, eyeX, eyeY, eyeZ, q, r, dsd, lam, n0, dmin, DMAX,
                w[0] * 0.9, w[1] * 0.9, gustX * 0.9, gustZ * 0.9, turbulence * windSpeed, rho, shutter * Math.max(1e-4, timeScale), timeScale,
                cloudBase, waterLevel, mapX0, mapZ0, MAP_SIZE / MAP_N, map.length == 0 ? 0 : MAP_N, map, mapVersion, seed, shf, grid,
                throughfall, interception, dripMin, dripMax, Math.max(0, aperture), Math.max(0.05, focus));
    }

    /** Der Zustand für Shader und Prüfungen (unveränderlich). */
    public Snapshot snapshot() { return snapshot; }

    /**
     * Unveränderlicher Zustand. {@code shutterSim} ist die Belichtung in der Zeit der Tropfen (Belichtung · Zeitmaß),
     * Wind und Böen in 10 m über Grund mal 0,9 (die Reibung am Boden, [G]), {@code turb} die Stärke der Wirbel in m/s.
     */
    public record Snapshot(double time, double dt, long frame, double eyeX, double eyeY, double eyeZ, Quality quality,
                           double rate, Drops.Dsd dsd, double lambda, double n0, double dmin, double dmax,
                           double windX, double windZ, double gustX, double gustZ, double turb, double airDensity,
                           double shutterSim, double timeScale, double cloudBase, double waterLevel,
                           double mapX0, double mapZ0, double mapCell, int mapN, float[] map, int mapVersion, int seed, float[] shelter,
                           Grid ground, double throughfall, double interception, double dripMin, double dripMax, double aperture, double focus) {

        /** Schauer 0..1 am Ort (bilinear zwischen den Zellmitten, wie im Shader). */
        public double curtainAt(double x, double z) {
            if (mapN == 0) return 1;
            double gx = (x - mapX0) / mapCell - 0.5, gz = (z - mapZ0) / mapCell - 0.5;
            double fx = Math.floor(gx), fz = Math.floor(gz);
            int ix = (int) fx, iz = (int) fz;
            double tx = gx - fx, tz = gz - fz;
            double a = at(ix, iz), b = at(ix + 1, iz), c = at(ix, iz + 1), d = at(ix + 1, iz + 1);
            return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
        }

        private double at(int i, int j) {
            i = Math.max(0, Math.min(mapN - 1, i));
            j = Math.max(0, Math.min(mapN - 1, j));
            return map[j * mapN + i];
        }

        /** Regen in mm/h am Ort. */
        public double rateAt(double x, double z) { return rate * curtainAt(x, z); }

        /**
         * Anteil der simulierten Tropfen, die am Ort leben: bei weniger Regen schweben weniger Tropfen über dmin,
         * N(&gt;dmin; R_Ort) / N(&gt;dmin; R) (N0 mit dem Abgleich der vollen Menge, wie im Shader).
         */
        public double liveFraction(double localRate) {
            if (localRate <= 0 || rate <= 0) return 0;
            double lf = dsd.lambda(localRate), nf = dsd.n0(localRate), full = n0 / lambda * Math.exp(-lambda * dmin);
            return Math.min(1, nf / lf * Math.exp(-lf * dmin) / Math.max(1e-12, full));
        }

        /** Liegt der Ort in einem Unterstand? */
        public boolean sheltered(double x, double y, double z) {
            for (int i = 0; i + 3 < shelter.length; i += 4) {
                double dx = x - shelter[i], dz = z - shelter[i + 1];
                if (dx * dx + dz * dz < (double) shelter[i + 2] * shelter[i + 2] && y < shelter[i + 3]) return true;
            }
            return false;
        }

        /** Exponent e in N0(R) ≈ N0(R_voll) · (R / R_voll)^e (für den Shader; fast 0). */
        public double n0Exponent() {
            if (rate <= 0) return 0;
            return Math.log(dsd.n0(rate) / dsd.n0(rate / 2)) / Math.log(2);
        }

        /**
         * Anteil der simulierten Tropfen, die unter dem Kronendach leben: die abgetropften großen Tropfen tragen
         * (1 − Durchfall − Verdunstung) des Regens; Dichte n = R / (π/6 D³ v) mit D in der Mitte der Tropfgröße.
         */
        public double dripFraction(double localRate) {
            if (localRate <= 0) return 0;
            double d = 0.5 * (dripMin + dripMax), flux = localRate / 3.6e6 * Math.max(0, 1 - throughfall - interception);
            double n = flux / (Math.PI / 6 * Math.pow(d * 1e-3, 3) * Drops.terminal(d));
            return Math.min(1, n / Math.max(1e-9, quality.drops / quality.volume()));
        }

        /** Tropfen je m³ über dmin bei voller Menge (die Dichte im Kasten). */
        public double densityAboveDmin() { return Drops.numberDensity(n0, lambda, dmin, dmax); }

        /** Auslöschung (1/m) im Regen am Ort, aus der Verteilung. */
        public double extinctionAt(double x, double z) {
            double r = rateAt(x, z);
            return r <= 0 ? 0 : Drops.extinction(dsd.n0(r), dsd.lambda(r));
        }
    }

    // ------------------------------------------------------------------ Ausgänge

    /** Regen in mm/h am Ort. */
    public double rateAt(double x, double z) { return snapshot.rateAt(x, z); }

    /** Sichtweite (km) im Regen allein am Auge (Kontrast 2 %). */
    public double visibilityKm() {
        double s = snapshot.extinctionAt(eyeX, eyeZ);
        return s <= 0 ? Double.POSITIVE_INFINITY : 3.912 / s / 1000;
    }

    /** Lautstärke 0..1 des Regens am Auge (für den Ton). */
    public double loudness() { return Math.min(1, Math.sqrt(snapshot.rateAt(eyeX, eyeZ) / 40)); }

    public String describe() {
        Snapshot s = snapshot;
        double here = s.rateAt(eyeX, eyeZ);
        String kind = here < 0.05 ? "trocken" : here < 0.5 ? "Niesel" : here < 2 ? "leichter Regen" : here < 8 ? "mäßiger Regen"
                : here < 30 ? "starker Regen" : "Wolkenbruch";
        return String.format(Locale.GERMAN, "%s (%s) · %.1f mm/h hier · Tropfen ab %.1f mm in voller Dichte (%.0f/m³) · Sicht %s · Belichtung 1/%.0f s",
                kind, s.dsd().label, here, s.dmin(), s.densityAboveDmin(),
                here > 0.05 ? String.format(Locale.GERMAN, "%.1f km", visibilityKm()) : "frei", 1 / Math.max(1e-6, shutter));
    }
}
