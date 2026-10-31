package com.dan.cloth;

/**
 * Tuch im Wind: Flaggen, Wimpel, Segel, Markisen, Sonnenschirme und Handtücher (Werkbuch Timmendorf, Abschnitt 6;
 * seit Timmendorf Phase 6 mit XPBD). Ein Netz aus {@link #nu} × {@link #nv} Punkten mit Massen; u läuft längs der
 * Befestigung (Liek am Mast, Stange der Flagge), v quer dazu. Je zwei Dreiecke je Masche.
 * <p>
 * <b>Schritt</b> nach XPBD (Macklin, Müller, Chentanez 2016) in kleinen Schritten (Macklin u. a. 2019): je Unterschritt
 * Kräfte (Schwere, Wind als Druck je Dreieck, Flattern), Vorhersage, dann {@link #iterations} Durchläufe über alle
 * Nebenbedingungen nach Gauß-Seidel in zwölf Farben ({@link #color}: in einer Farbe teilt sich keine Nebenbedingung
 * einen Punkt mit einer anderen), dann Fernhalter und Leinen, zuletzt Geschwindigkeit aus der Verschiebung. Nebenbedingungen sind Abstände zu den 12
 * Nachbarn im Raster: Dehnung (u, v), Scherung (Diagonalen), Biegung (über zwei Maschen), jede mit eigener
 * Nachgiebigkeit α (α̃ = α / Δt²). Dieselbe Rechnung läuft in {@code gl/ClothGpu} als Compute-Shader (F9 A), ein Arbeitsgruppe je Tuch; der
 * Kern hier ist die Vorlage für Prüfung und Demo.
 * <p>
 * <b>Wind</b> wirkt je Dreieck als Druck auf die Normale (½·ρ·c_N·A·(v_rel·n)·|v_rel·n|) und als Reibung längs
 * (½·ρ·c_T·A·|v_t|·v_t). <b>Flattern</b> [G]: eine Welle quer zum Tuch, die von der Befestigung zur freien Kante läuft,
 * mit der Frequenz nach der Strouhal-Zahl f = St·U / A (St 0,2, A die Auslenkung, 0,2 der Länge in v), stärker zur
 * freien Kante hin. Der Wind gilt je Tuch an seinem Bezugspunkt ({@link #reference}); Böen kommen über die Zeit.
 * <p>
 * Raum: x Ost, y oben, z Süd, Meter, Sekunden, Kilogramm (wie die Szenen). Der Kern kennt nur Java 21; Wind und Grund
 * kommen über die eigenen Schnittstellen {@link Wind} und {@link Ground}.
 */
public final class Cloth {
    /** Qualitätsstufen, eigene Kopie im Package (Paketvertrag Punkt 7): Unterschritte je Bild von 1/60 s. */
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    /** Wind am Ort (m/s je Achse), etwa aus {@code wind}; die App reicht ihn durch. */
    @FunctionalInterface
    public interface Wind { void velocity(double x, double y, double z, double t, double[] out); }

    /** Höhe des Grunds am Ort (m), damit ein Handtuch auf dem Sand liegt; null = kein Grund. */
    @FunctionalInterface
    public interface Ground { double height(double x, double z); }

    /** Ereignis nach außen: das Tuch schlägt (für den Klang), mit Stärke 0..1. */
    @FunctionalInterface
    public interface Flap { void flap(double x, double y, double z, double strength); }

    /** Die 12 Nachbarn je Punkt (di, dj): 0–3 Dehnung, 4–7 Scherung, 8–11 Biegung. */
    public static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
    /** Strouhal-Zahl einer Flagge [G: 0,15 bis 0,25 in der Literatur, zu prüfen]. */
    public static final double STROUHAL = 0.2;
    /** Schwere (m/s²). */
    public static final double G = 9.81;

    /** Startwert des Zufalls (Punkt 6): Phase des Flatterns. */
    public long seed = 1;
    public Quality quality = Quality.HIGH;
    /** Flächengewicht des Tuchs (kg/m²): Fahnentuch 0,11, Dacron einer Jolle 0,2, Markisenstoff 0,3 [G]. */
    public double arealDensity = 0.15;
    /** Luftdichte (kg/m³), Beiwerte für Druck auf die Normale und Reibung längs [G nach Platten im Wind]. */
    public double airDensity = 1.225, normalCoefficient = 1.2, tangentCoefficient = 0.03;
    /** Stärke des Flatterns (Anteil des Staudrucks an der freien Kante) [G]; 0 = aus. */
    public double flutter = 0.35;
    /** Nachgiebigkeit (m/N) der Dehnung, Scherung und Biegung [G: Fahnentuch fast undehnbar, schlaff in der Biegung]. */
    public double stretchCompliance = 1e-7, shearCompliance = 1e-5, bendCompliance = 2e-2;
    /** Dämpfung der Geschwindigkeit (1/s) [G]. */
    public double damping = 0.15;
    /** Durchläufe je Unterschritt (Gauß-Seidel in zwölf Farben). */
    public int iterations = 2;
    /** Nässe 0..1: schwerer (bis das Zweieinhalbfache, nasse Baumwolle) und in {@code gl/} dunkler [G]. */
    public double wetness;
    /**
     * Aussehen für {@code gl/}: bis zu drei Farbbänder (linear, je drei Werte), quer zu u ({@code bandsAlongU}: die Bänder
     * folgen aufeinander längs u, also waagrecht bei einer Flagge) oder quer zu v; Durchscheinen 0..1 im Gegenlicht.
     */
    public float[] colors = {0.85f, 0.84f, 0.80f};
    public boolean bandsAlongU = true;
    public double translucency = 0.45;
    public Wind wind = (x, y, z, t, o) -> { o[0] = 5; o[1] = 0; o[2] = 0; };
    public Ground ground;
    public Flap onFlap;

    /** Punkte je Richtung und Abstand in Ruhe (m) im Raster (die Ruhelängen selbst stehen in {@link #rest}). */
    public final int nu, nv;
    public final double du, dv;
    /** Lage, Lage vor dem letzten Unterschritt, Geschwindigkeit; Index {@code (j * nu + i) * 3}. */
    public final double[] pos, prev, vel;
    /** Fest (Liek, Stange): diese Punkte bewegt nur die App ({@link #moveTo}). */
    public final boolean[] pinned;
    /** Masse je Punkt (kg, trocken) aus den Dreiecken in Ruhe. */
    public final double[] mass;
    /** Ruhelänge je Punkt und Nachbar ({@link #DIRS}), negativ = kein Nachbar. */
    public final double[] rest;
    /** Leinen (Schot, Fall): Ankerpunkt x, y, z und größte Länge je Punkt; Länge negativ = keine Leine. */
    public final double[] tether;
    /**
     * Fernhalter (long range attachments, Kim, Tsai, Bridson 2012): je Punkt der nächste feste Punkt beim Anlegen und
     * der Abstand in Ruhe dorthin; weiter darf er sich nicht entfernen. Hält lange Tücher bei großen Schritten undehnbar.
     * Index −1 = keiner.
     */
    public final int[] lra;
    public final double[] lraLength;
    /** Bezugspunkt für den Wind (Mitte der festen Punkte beim Anlegen). */
    public final double[] reference = new double[3];
    private final double[] force;
    private double time, phase;
    private final double[] pinForce = new double[3], lastWind = new double[3];
    private double lastFlap;

    /** Ein Netz nu × nv mit Rasterabständen (für eigene Formen; danach {@link #setRest()} und {@link #finish()}). */
    public Cloth(int nu, int nv, double du, double dv) {
        this.nu = Math.max(2, nu);
        this.nv = Math.max(2, nv);
        this.du = du;
        this.dv = dv;
        int n = this.nu * this.nv;
        pos = new double[n * 3];
        prev = new double[n * 3];
        vel = new double[n * 3];
        pinned = new boolean[n];
        mass = new double[n];
        rest = new double[n * DIRS.length];
        tether = new double[n * 4];
        for (int k = 0; k < n; k++) tether[k * 4 + 3] = -1;
        force = new double[n * 3];
        lra = new int[n];
        lraLength = new double[n];
        java.util.Arrays.fill(lra, -1);
    }

    // ------------------------------------------------------------------ Presets (Punkt 5)

    /**
     * Eine Flagge w × h m an einer senkrechten Stange bei (x, y, z) (oberes Ende des Lieks): die Stangenseite (v = 0)
     * fest, das Tuch hängt zunächst nach Osten aus. u läuft die Stange hinab.
     */
    public static Cloth flag(double x, double y, double z, double w, double h) {
        int nu = Math.max(4, (int) Math.round(h / 0.1) + 1), nv = Math.max(4, (int) Math.round(w / 0.1) + 1);
        nu = Math.min(nu, 24);
        nv = Math.min(nv, 32);
        Cloth c = new Cloth(nu, nv, h / (nu - 1), w / (nv - 1));
        for (int j = 0; j < c.nv; j++)
            for (int i = 0; i < c.nu; i++) {
                int k = j * c.nu + i;
                c.place(k, x + j * c.dv, y - i * c.du, z + 0.002 * Math.sin(j * 1.3 + i));
                c.pinned[k] = j == 0;
            }
        c.arealDensity = 0.11;
        return c.finish();
    }

    /** Eine Strandflagge 1,5 × 1 m auf 6 m Stange (Timmendorfer Strand) [G]. */
    public static Cloth beachFlag(double x, double groundY, double z) {
        Cloth c = flag(x, groundY + 6, z, 1.5, 1.0);
        c.colors = SCHLESWIG_HOLSTEIN.clone();
        return c;
    }

    /** Blau, Weiß, Rot (linear, Landesflagge Schleswig-Holstein) [G nach den Farbangaben]. */
    public static final float[] SCHLESWIG_HOLSTEIN = {0.0f, 0.12f, 0.45f, 0.86f, 0.86f, 0.84f, 0.72f, 0.03f, 0.04f};

    /** Ein Wimpel (Dreieck) 1,2 m lang, 0,3 m hoch an der Stange: die Höhe nimmt zur Spitze ab (Punkte fallen zusammen). */
    public static Cloth pennant(double x, double y, double z, double len, double h) {
        int nu = 5, nv = 13;
        Cloth c = new Cloth(nu, nv, h / (nu - 1), len / (nv - 1));
        for (int j = 0; j < nv; j++) {
            double s = 1 - j / (double) (nv - 1) * 0.92;
            for (int i = 0; i < nu; i++) {
                int k = j * nu + i;
                c.place(k, x + j * c.dv, y - h / 2 + (h / 2 - i * c.du) * s, z + 0.002 * Math.sin(j * 1.7 + i));
                c.pinned[k] = j == 0;
            }
        }
        c.arealDensity = 0.11;
        return c.setRest().finish();
    }

    /** Eine Markise (Strandkorb) w × d m, an der Vorderkante fest, hinten an zwei Ecken an Leinen [G]. */
    public static Cloth awning(double x, double y, double z, double w, double d) {
        Cloth c = new Cloth(11, 7, w / 10, d / 6);
        for (int j = 0; j < c.nv; j++)
            for (int i = 0; i < c.nu; i++) {
                int k = j * c.nu + i;
                c.place(k, x + i * c.du, y - 0.15 * j / (c.nv - 1.0), z + j * c.dv);
                c.pinned[k] = j == 0;
            }
        int a = (c.nv - 1) * c.nu, b = a + c.nu - 1;
        c.tie(a, x, y - 0.4, z + d, 0.42);
        c.tie(b, x + w, y - 0.4, z + d, 0.42);
        c.arealDensity = 0.3;
        c.bendCompliance = 2e-3;
        c.colors = new float[]{0.08f, 0.25f, 0.55f, 0.86f, 0.86f, 0.84f, 0.08f, 0.25f, 0.55f};
        c.bandsAlongU = false;
        c.translucency = 0.25;
        return c.finish();
    }

    /** Ein Sonnenschirm (Achteck, Halbmesser r m) in Höhe y: die Mitte und der Kranz der Speichen fest, das Tuch dazwischen. */
    public static Cloth parasol(double x, double y, double z, double r) {
        int nu = 17, nv = 6;
        Cloth c = new Cloth(nu, nv, 2 * Math.PI * r / (nu - 1), r / (nv - 1));
        for (int j = 0; j < nv; j++)
            for (int i = 0; i < nu; i++) {
                double a = 2 * Math.PI * i / (nu - 1), rr = r * j / (nv - 1.0);
                int k = j * nu + i;
                c.place(k, x + rr * Math.cos(a), y - 0.35 * (rr / r) * (rr / r), z + rr * Math.sin(a));
                c.pinned[k] = j == 0 || (j == nv - 1 && i % 2 == 0);
            }
        c.arealDensity = 0.25;
        return c.setRest().finish();
    }

    /** Ein Handtuch 1,8 × 0,9 m, lose auf dem Grund (die Ecken nicht fest). */
    public static Cloth towel(double x, double y, double z) {
        Cloth c = new Cloth(13, 7, 1.8 / 12, 0.9 / 6);
        for (int j = 0; j < c.nv; j++)
            for (int i = 0; i < c.nu; i++) c.place(j * c.nu + i, x + i * c.du, y + 0.01, z + j * c.dv);
        c.arealDensity = 0.4;
        c.bendCompliance = 5e-2;
        return c.finish();
    }

    /**
     * Ein Band nu × 2 zwischen zwei festen Enden im Abstand {@code span} (m), mit der Länge {@code length} ≥ span längs u,
     * waagrecht gespannt und dann losgelassen (für die Kettenlinie in der Prüfung).
     */
    public static Cloth strip(double span, double length, double width, int nu) {
        Cloth c = new Cloth(nu, 2, length / (nu - 1), width);
        for (int j = 0; j < 2; j++)
            for (int i = 0; i < nu; i++) {
                int k = j * nu + i;
                c.place(k, -span / 2 + span * i / (nu - 1.0), 0, j * width);
                c.pinned[k] = i == 0 || i == nu - 1;
            }
        c.arealDensity = 0.2;
        c.bendCompliance = 1;
        return c.finish();
    }

    void place(int k, double x, double y, double z) {
        pos[k * 3] = prev[k * 3] = x;
        pos[k * 3 + 1] = prev[k * 3 + 1] = y;
        pos[k * 3 + 2] = prev[k * 3 + 2] = z;
    }

    /** Ruhelängen aus der jetzigen Lage (für Formen, die nicht im Raster liegen: Wimpel, Schirm). */
    public Cloth setRest() {
        for (int j = 0; j < nv; j++)
            for (int i = 0; i < nu; i++) {
                int k = j * nu + i;
                for (int d = 0; d < DIRS.length; d++) {
                    int b = neighbor(i, j, d);
                    rest[k * DIRS.length + d] = b < 0 ? -1 : dist(k, b);
                }
            }
        restSet = true;
        return this;
    }

    private boolean restSet;

    /** Ruhelängen (aus dem Raster, wenn nicht gesetzt), Massen aus den Dreiecken, Bezugspunkt des Winds. */
    public Cloth finish() {
        if (!restSet)
            for (int j = 0; j < nv; j++)
                for (int i = 0; i < nu; i++) {
                    int k = j * nu + i;
                    for (int d = 0; d < DIRS.length; d++) {
                        int b = neighbor(i, j, d);
                        rest[k * DIRS.length + d] = b < 0 ? -1 : Math.hypot(DIRS[d][0] * du, DIRS[d][1] * dv);
                    }
                }
        java.util.Arrays.fill(mass, 0);
        for (int j = 0; j + 1 < nv; j++)
            for (int i = 0; i + 1 < nu; i++) {
                int a = j * nu + i, b = a + 1, c = a + nu, d = c + 1;
                double a1 = area(a, b, c), a2 = area(b, d, c);
                mass[a] += a1 / 3;
                mass[b] += (a1 + a2) / 3;
                mass[c] += (a1 + a2) / 3;
                mass[d] += a2 / 3;
            }
        double total = 0;
        for (double m : mass) total += m;
        for (int k = 0; k < mass.length; k++) mass[k] = Math.max(mass[k], total / mass.length * 0.05);
        int np = 0;
        double[] s = new double[3];
        for (int k = 0; k < pinned.length; k++)
            if (pinned[k]) { np++; for (int a = 0; a < 3; a++) s[a] += pos[k * 3 + a]; }
        if (np == 0) { np = pinned.length; for (int k = 0; k < np; k++) for (int a = 0; a < 3; a++) s[a] += pos[k * 3 + a]; }
        for (int a = 0; a < 3; a++) reference[a] = s[a] / np;
        // Fernhalter: nächster fester Punkt in Ruhe (im Raster über die Rasterabstände, sonst über die Lage beim Anlegen)
        java.util.Arrays.fill(lra, -1);
        for (int k = 0; k < pinned.length; k++) {
            if (pinned[k]) continue;
            int ki = k % nu, kj = k / nu;
            double best = Double.MAX_VALUE;
            for (int p = 0; p < pinned.length; p++) {
                if (!pinned[p]) continue;
                double dd = restSet ? dist(k, p) : Math.hypot((p % nu - ki) * du, (p / nu - kj) * dv);
                if (dd < best) { best = dd; lra[k] = p; }
            }
            lraLength[k] = best;
        }
        return this;
    }

    private double area(int a, int b, int c) {
        double ux = pos[b * 3] - pos[a * 3], uy = pos[b * 3 + 1] - pos[a * 3 + 1], uz = pos[b * 3 + 2] - pos[a * 3 + 2];
        double vx = pos[c * 3] - pos[a * 3], vy = pos[c * 3 + 1] - pos[a * 3 + 1], vz = pos[c * 3 + 2] - pos[a * 3 + 2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        return 0.5 * Math.sqrt(nx * nx + ny * ny + nz * nz);
    }

    /** Index des Nachbarn d von (i, j), oder −1. */
    public int neighbor(int i, int j, int d) {
        int a = i + DIRS[d][0], b = j + DIRS[d][1];
        return a < 0 || b < 0 || a >= nu || b >= nv ? -1 : b * nu + a;
    }

    // ------------------------------------------------------------------ Ränder

    /** Punkt k fest oder lose. */
    public void pin(int k, boolean fest) { pinned[k] = fest; }

    /** Einen festen Punkt an einen Ort setzen (Mast neigt sich, Boot fährt). */
    public void moveTo(int k, double x, double y, double z) {
        pos[k * 3] = x;
        pos[k * 3 + 1] = y;
        pos[k * 3 + 2] = z;
    }

    /** Alle festen Punkte verschieben (die Stange mit dem Boot). */
    public void translatePinned(double dx, double dy, double dz) {
        for (int k = 0; k < pinned.length; k++)
            if (pinned[k]) { pos[k * 3] += dx; pos[k * 3 + 1] += dy; pos[k * 3 + 2] += dz; }
    }

    /** Leine von Punkt k zu einem Anker mit größter Länge (zieht nur, wenn sie straff ist); Länge < 0 löst sie. */
    public void tie(int k, double x, double y, double z, double length) {
        tether[k * 4] = x;
        tether[k * 4 + 1] = y;
        tether[k * 4 + 2] = z;
        tether[k * 4 + 3] = length;
    }

    /**
     * Reffen und Bergen: der Anteil {@code share} (0..1) des Tuchs von der Befestigung (v = 0) her wird an das Liek geholt
     * und fest; 0 löst alles wieder (bis auf das Liek), 1 birgt das Tuch ganz.
     */
    public void reef(double share) {
        int rows = (int) Math.round(Math.max(0, Math.min(1, share)) * (nv - 1));
        for (int j = 1; j < nv; j++)
            for (int i = 0; i < nu; i++) {
                int k = j * nu + i, base = i;
                if (j <= rows) {
                    pinned[k] = true;
                    // zusammengerollt am Liek, etwas versetzt
                    double off = 0.01 * j;
                    moveTo(k, pos[base * 3] + off, pos[base * 3 + 1], pos[base * 3 + 2] + off);
                } else if (pinned[k] && reefed) pinned[k] = false;
            }
        reefed = rows > 0;
    }

    private boolean reefed;

    // ------------------------------------------------------------------ Schritt

    /** Unterschritte je 1/60 s nach der Stufe. */
    public int substeps() {
        return switch (quality) { case LOW -> 8; case MEDIUM -> 12; case HIGH -> 20; case ULTRA -> 32; };
    }

    /** Wind am Bezugspunkt zur Zeit t (m/s), so wie ihn der Schritt nimmt. */
    public double[] windAt(double t) {
        double[] w = new double[3];
        wind.velocity(reference[0], reference[1], reference[2], t, w);
        return w;
    }

    /** Frequenz des Flatterns (Hz) bei Windgeschwindigkeit U: St·U / (0,2 · Länge in v) [G]. */
    public double flutterFrequency(double u) { return STROUHAL * u / (0.2 * (nv - 1) * dv); }

    /** Rechnet dt Sekunden weiter: Unterschritte nach der Stufe (je 1/60 s), höchstens 0,1 s am Stück. */
    public void step(double dt) {
        double d = Math.max(0, Math.min(0.1, dt));
        int n = steps(d);
        if (n == 0) return;
        double h = d / n;
        // Wind einmal je Schritt, in seiner Mitte (so auch auf der Grafikkarte)
        double[] w = windAt(time + d / 2);
        System.arraycopy(w, 0, lastWind, 0, 3);
        double u = Math.sqrt(w[0] * w[0] + w[1] * w[1] + w[2] * w[2]), fr = flutterFrequency(u);
        double[] acc = new double[3];
        for (int s = 0; s < n; s++) substep(h, w, phase + 2 * Math.PI * fr * (s + 1) * h, acc);
        phase = (phase + 2 * Math.PI * fr * d) % (2 * Math.PI * 1e3);
        time += d;
        // Kraft des Tuchs an den festen Punkten: Impuls, den die Nebenbedingungen den freien Punkten nehmen, je Zeit
        for (int a = 0; a < 3; a++) pinForce[a] = -acc[a] / d;
        if (onFlap != null) {
            int k = pinned.length - 1;
            double v = Math.sqrt(vel[k * 3] * vel[k * 3] + vel[k * 3 + 1] * vel[k * 3 + 1] + vel[k * 3 + 2] * vel[k * 3 + 2]);
            if (v > 3 && time - lastFlap > 0.08) {
                lastFlap = time;
                onFlap.flap(pos[k * 3], pos[k * 3 + 1], pos[k * 3 + 2], Math.min(1, v / 10));
            }
        }
    }

    /**
     * Für {@code gl/ClothGpu}: die Werte eines Schritts von dt Sekunden – {Unterschritte, Δt je Unterschritt, Wind x, y,
     * z, Frequenz des Flatterns, Phase am Anfang} – und die Uhr rückt vor wie bei {@link #step}, ohne das Netz zu rechnen.
     */
    public double[] advance(double dt) {
        double d = Math.max(0, Math.min(0.1, dt));
        int n = steps(d);
        double[] w = windAt(time + d / 2);
        System.arraycopy(w, 0, lastWind, 0, 3);
        double u = Math.sqrt(w[0] * w[0] + w[1] * w[1] + w[2] * w[2]), fr = flutterFrequency(u);
        double[] r = {n, n == 0 ? 0 : d / n, w[0], w[1], w[2], fr, phase};
        phase = (phase + 2 * Math.PI * fr * d) % (2 * Math.PI * 1e3);
        time += d;
        return r;
    }

    /** Zahl der Unterschritte für dt Sekunden. */
    public int steps(double dt) { return dt <= 0 ? 0 : Math.max(1, (int) Math.ceil(substeps() * dt * 60 - 1e-9)); }

    /** Phase des Flatterns (rad) am Anfang des nächsten Schritts. */
    public double phase() { return phase; }

    /** Masse eines Punkts mit Nässe. */
    public double massAt(int k) { return mass[k] * arealDensity * (1 + 1.5 * Math.max(0, Math.min(1, wetness))); }

    private void substep(double h, double[] w, double ph, double[] impulse) {
        int n = pinned.length;
        double[] f = force;
        java.util.Arrays.fill(f, 0);
        forces(w, ph, f);
        double damp = Math.exp(-damping * h), px = 0, py = 0, pz = 0;
        for (int k = 0; k < n; k++) {
            int o = k * 3;
            prev[o] = pos[o];
            prev[o + 1] = pos[o + 1];
            prev[o + 2] = pos[o + 2];
            if (pinned[k]) continue;
            double m = massAt(k);
            vel[o] = (vel[o] + h * f[o] / m) * damp;
            vel[o + 1] = (vel[o + 1] + h * (f[o + 1] / m - G)) * damp;
            vel[o + 2] = (vel[o + 2] + h * f[o + 2] / m) * damp;
            px -= m * vel[o];
            py -= m * vel[o + 1];
            pz -= m * vel[o + 2];
            pos[o] += h * vel[o];
            pos[o + 1] += h * vel[o + 1];
            pos[o + 2] += h * vel[o + 2];
        }
        for (int it = 0; it < iterations; it++) solve(h);
        for (int k = 0; k < n; k++) {
            int o = k * 3;
            if (pinned[k]) { vel[o] = vel[o + 1] = vel[o + 2] = 0; continue; }
            if (ground != null) {
                double g = ground.height(pos[o], pos[o + 2]) + 0.005;
                if (pos[o + 1] < g) {
                    pos[o + 1] = g;
                    // Reibung auf dem Grund [G]
                    pos[o] -= 0.6 * (pos[o] - prev[o]);
                    pos[o + 2] -= 0.6 * (pos[o + 2] - prev[o + 2]);
                }
            }
            vel[o] = (pos[o] - prev[o]) / h;
            vel[o + 1] = (pos[o + 1] - prev[o + 1]) / h;
            vel[o + 2] = (pos[o + 2] - prev[o + 2]) / h;
            double m = massAt(k);
            px += m * vel[o];
            py += m * vel[o + 1];
            pz += m * vel[o + 2];
        }
        impulse[0] += px;
        impulse[1] += py;
        impulse[2] += pz;
    }

    /**
     * Kräfte je Punkt: Wind aus den sechs Dreiecken um den Punkt (je ein Drittel) und das Flattern mit der Phase ph.
     * Jeder Punkt sammelt für sich (wie auf der Grafikkarte).
     */
    void forces(double[] w, double ph, double[] f) {
        double half = 0.5 * airDensity, u = Math.sqrt(w[0] * w[0] + w[1] * w[1] + w[2] * w[2]);
        double fr = flutterFrequency(u), len = (nv - 1) * dv, q = 0.5 * airDensity * u * u * flutter, ph0 = (seed % 997) * 0.37;
        boolean flat = flutter > 0 && u >= 0.5;
        double[] t = new double[3], nrm = new double[3];
        for (int j = 0; j < nv; j++)
            for (int i = 0; i < nu; i++) {
                int k = j * nu + i;
                if (pinned[k]) continue;
                double fx = 0, fy = 0, fz = 0;
                for (int c = 0; c < 6; c++) {
                    if (!triAround(i, j, c, w, half, t)) continue;
                    fx += t[0];
                    fy += t[1];
                    fz += t[2];
                }
                if (flat && j > 0) {
                    double s = j / (nv - 1.0);
                    // Welle läuft mit 0,6·U zur freien Kante [G]; mass[k] ist hier die Fläche je Punkt (m²)
                    double sn = Math.sin(ph - 2 * Math.PI * fr * (s * len) / (0.6 * u) + ph0), fa = q * s * s * sn * mass[k];
                    normal(i, j, nrm);
                    fx += fa * nrm[0];
                    fy += fa * nrm[1];
                    fz += fa * nrm[2];
                }
                f[k * 3] = fx;
                f[k * 3 + 1] = fy;
                f[k * 3 + 2] = fz;
            }
    }

    /** Das c-te der sechs Dreiecke um (i, j): Drittel der Windkraft in out; false, wenn es das Dreieck nicht gibt. */
    private boolean triAround(int i, int j, int c, double[] w, double half, double[] out) {
        int qi, qj;
        boolean second;
        switch (c) {
            case 0 -> { qi = i; qj = j; second = false; }          // als a
            case 1 -> { qi = i - 1; qj = j; second = false; }      // als b
            case 2 -> { qi = i - 1; qj = j; second = true; }       // als b
            case 3 -> { qi = i; qj = j - 1; second = false; }      // als c
            case 4 -> { qi = i; qj = j - 1; second = true; }       // als c
            default -> { qi = i - 1; qj = j - 1; second = true; }  // als d
        }
        if (qi < 0 || qj < 0 || qi + 1 >= nu || qj + 1 >= nv) return false;
        int a = qj * nu + qi, b = a + 1, cc = a + nu, d = cc + 1;
        return second ? tri(b, d, cc, w, half, out) : tri(a, b, cc, w, half, out);
    }

    private boolean tri(int a, int b, int c, double[] w, double half, double[] out) {
        double ux = pos[b * 3] - pos[a * 3], uy = pos[b * 3 + 1] - pos[a * 3 + 1], uz = pos[b * 3 + 2] - pos[a * 3 + 2];
        double vx = pos[c * 3] - pos[a * 3], vy = pos[c * 3 + 1] - pos[a * 3 + 1], vz = pos[c * 3 + 2] - pos[a * 3 + 2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx, l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-12) return false;
        double area = 0.5 * l;
        nx /= l;
        ny /= l;
        nz /= l;
        double rx = w[0] - (vel[a * 3] + vel[b * 3] + vel[c * 3]) / 3, ry = w[1] - (vel[a * 3 + 1] + vel[b * 3 + 1] + vel[c * 3 + 1]) / 3,
                rz = w[2] - (vel[a * 3 + 2] + vel[b * 3 + 2] + vel[c * 3 + 2]) / 3;
        double rn = rx * nx + ry * ny + rz * nz;
        double tx = rx - rn * nx, ty = ry - rn * ny, tz = rz - rn * nz, tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
        double pn = half * normalCoefficient * area * rn * Math.abs(rn), pt = half * tangentCoefficient * area * tl;
        out[0] = (pn * nx + pt * tx) / 3;
        out[1] = (pn * ny + pt * ty) / 3;
        out[2] = (pn * nz + pt * tz) / 3;
        return true;
    }

    /** Normale am Punkt (i, j) aus den Nachbarn (Kreuzprodukt der Mittendifferenzen), Länge 1. */
    public void normal(int i, int j, double[] out) {
        int il = Math.max(0, i - 1), ir = Math.min(nu - 1, i + 1), jl = Math.max(0, j - 1), jr = Math.min(nv - 1, j + 1);
        int a = j * nu + il, b = j * nu + ir, c = jl * nu + i, d = jr * nu + i;
        double ux = pos[b * 3] - pos[a * 3], uy = pos[b * 3 + 1] - pos[a * 3 + 1], uz = pos[b * 3 + 2] - pos[a * 3 + 2];
        double vx = pos[d * 3] - pos[c * 3], vy = pos[d * 3 + 1] - pos[c * 3 + 1], vz = pos[d * 3 + 2] - pos[c * 3 + 2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx, l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-12) { out[0] = 0; out[1] = 1; out[2] = 0; return; }
        out[0] = nx / l;
        out[1] = ny / l;
        out[2] = nz / l;
    }

    /** Farbe der Nebenbedingung (i, j) in Richtung d (0, 2, … 10): je zwei Farben, die sich keinen Punkt teilen. */
    public static int color(int d, int i, int j) {
        return switch (d) {
            case 2 -> j & 1;
            case 8 -> (i >> 1) & 1;
            case 10 -> (j >> 1) & 1;
            default -> i & 1;
        };
    }

    /**
     * Ein Durchlauf nach Gauß-Seidel in zwölf Farben (Richtung 0, 2, … 10, je Farbe 0 und 1): in einer Farbe teilen
     * sich die Nebenbedingungen keinen Punkt, die Grafikkarte rechnet sie gleichzeitig. Danach Fernhalter und Leinen.
     */
    private void solve(double h) {
        double h2 = h * h;
        for (int d = 0; d < DIRS.length; d += 2) {
            double alpha = (d < 4 ? stretchCompliance : d < 8 ? shearCompliance : bendCompliance) / h2;
            for (int col = 0; col < 2; col++)
                for (int j = 0; j < nv; j++)
                    for (int i = 0; i < nu; i++) {
                        if (color(d, i, j) != col) continue;
                        int a = j * nu + i;
                        double r = rest[a * DIRS.length + d];
                        if (r < 0) continue;
                        int b = neighbor(i, j, d);
                        double wa = pinned[a] ? 0 : 1 / massAt(a), wb = pinned[b] ? 0 : 1 / massAt(b);
                        if (wa + wb == 0) continue;
                        double dx = pos[a * 3] - pos[b * 3], dy = pos[a * 3 + 1] - pos[b * 3 + 1], dz = pos[a * 3 + 2] - pos[b * 3 + 2];
                        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (l < 1e-12) continue;
                        double dl = -(l - r) / (wa + wb + alpha) / l;
                        pos[a * 3] += wa * dl * dx;
                        pos[a * 3 + 1] += wa * dl * dy;
                        pos[a * 3 + 2] += wa * dl * dz;
                        pos[b * 3] -= wb * dl * dx;
                        pos[b * 3 + 1] -= wb * dl * dy;
                        pos[b * 3 + 2] -= wb * dl * dz;
                    }
        }
        for (int k = 0; k < pinned.length; k++) {
            if (pinned[k]) continue;
            int p = lra[k];
            if (p >= 0 && pinned[p]) clamp(k, pos[p * 3], pos[p * 3 + 1], pos[p * 3 + 2], lraLength[k]);
            if (tether[k * 4 + 3] >= 0) clamp(k, tether[k * 4], tether[k * 4 + 1], tether[k * 4 + 2], tether[k * 4 + 3]);
        }
    }

    private void clamp(int k, double ax, double ay, double az, double max) {
        double dx = pos[k * 3] - ax, dy = pos[k * 3 + 1] - ay, dz = pos[k * 3 + 2] - az;
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l <= max || l < 1e-12) return;
        double s = max / l;
        pos[k * 3] = ax + dx * s;
        pos[k * 3 + 1] = ay + dy * s;
        pos[k * 3 + 2] = az + dz * s;
    }

    // ------------------------------------------------------------------ Abfragen

    /** Lage eines Punkts. */
    public double[] point(int i, int j) {
        int o = (j * nu + i) * 3;
        return new double[]{pos[o], pos[o + 1], pos[o + 2]};
    }

    /** Größte Abweichung einer Kante (Dehnung, u und v) von ihrer Ruhelänge (Anteil). */
    public double maxStretch() {
        double m = 0;
        for (int j = 0; j < nv; j++)
            for (int i = 0; i < nu; i++) {
                int a = j * nu + i;
                for (int d = 0; d < 4; d += 2) {
                    double r = rest[a * DIRS.length + d];
                    if (r <= 1e-9) continue;
                    m = Math.max(m, Math.abs(dist(a, neighbor(i, j, d)) / r - 1));
                }
            }
        return m;
    }

    double dist(int a, int b) {
        double dx = pos[b * 3] - pos[a * 3], dy = pos[b * 3 + 1] - pos[a * 3 + 1], dz = pos[b * 3 + 2] - pos[a * 3 + 2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Mittlere Geschwindigkeit der freien Punkte (m/s). */
    public double meanSpeed(double dt) {
        double s = 0;
        int n = 0;
        for (int k = 0; k < pinned.length; k++) {
            if (pinned[k]) continue;
            int o = k * 3;
            s += Math.sqrt(vel[o] * vel[o] + vel[o + 1] * vel[o + 1] + vel[o + 2] * vel[o + 2]);
            n++;
        }
        return n == 0 ? 0 : s / n;
    }

    /** Bewegungs- und Lageenergie der freien Punkte (J, Lage über y = 0). */
    public double energy() {
        double e = 0;
        for (int k = 0; k < pinned.length; k++) {
            if (pinned[k]) continue;
            int o = k * 3;
            double m = massAt(k);
            e += 0.5 * m * (vel[o] * vel[o] + vel[o + 1] * vel[o + 1] + vel[o + 2] * vel[o + 2]) + m * G * pos[o + 1];
        }
        return e;
    }

    /** Gewicht der freien Punkte (N). */
    public double freeWeight() {
        double m = 0;
        for (int k = 0; k < pinned.length; k++) if (!pinned[k]) m += massAt(k);
        return m * G;
    }

    /** Summe der Kräfte, die das Tuch im letzten Unterschritt an den festen Punkten zieht (N, {x, y, z}). */
    public double[] pinForce() { return pinForce.clone(); }

    /** Wind am Bezugspunkt im letzten Unterschritt (m/s). */
    public double[] lastWind() { return lastWind.clone(); }

    /** Normalen aller Punkte (Länge 1), Index wie {@link #pos}. */
    public float[] normals() {
        float[] out = new float[pos.length];
        double[] n = new double[3];
        for (int j = 0; j < nv; j++)
            for (int i = 0; i < nu; i++) {
                normal(i, j, n);
                int o = (j * nu + i) * 3;
                out[o] = (float) n[0];
                out[o + 1] = (float) n[1];
                out[o + 2] = (float) n[2];
            }
        return out;
    }

    /** Dreiecke (Indizes, je Masche zwei). */
    public int[] triangles() {
        int[] t = new int[(nu - 1) * (nv - 1) * 6];
        int p = 0;
        for (int j = 0; j + 1 < nv; j++)
            for (int i = 0; i + 1 < nu; i++) {
                int a = j * nu + i, b = a + 1, c = a + nu, d = c + 1;
                t[p++] = a; t[p++] = b; t[p++] = c;
                t[p++] = b; t[p++] = d; t[p++] = c;
            }
        return t;
    }

    /** Wie viel los ist (0..1): Wind am Bezugspunkt durch 15 m/s. */
    public double strength() {
        double[] w = windAt(time);
        return Math.min(1, Math.sqrt(w[0] * w[0] + w[1] * w[1] + w[2] * w[2]) / 15);
    }

    /** Laufzeit seit dem Anlegen (s). */
    public double time() { return time; }

    /** „Flagge 16 × 11 Punkte, 0,14 kg, 20 Unterschritte, Dehnung 0,4 %“ */
    public String describe() {
        double m = 0;
        for (int k = 0; k < mass.length; k++) m += massAt(k);
        return String.format(java.util.Locale.GERMAN, "Tuch %d × %d Punkte, %.2f kg, %d Unterschritte je 1/60 s, Dehnung %.1f %%", nu, nv, m, substeps(), maxStretch() * 100);
    }
}
