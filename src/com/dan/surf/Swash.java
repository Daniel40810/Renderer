package com.dan.surf;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Auflauf und Rücklauf je Querprofil (seit Sylt Phase 7, F8 A): die Flachwassergleichungen in einer Dimension auf dem
 * Strand, je Reihe eines {@link WaveTrain} ein eigenes Profil,
 * <pre>
 *   ∂d/∂t + ∂(d·u)/∂x = −i
 *   ∂(d·u)/∂t + ∂(d·u² + ½·g·d²)/∂x = −g·d·∂z/∂x − c_f·u·|u|
 * </pre>
 * gelöst mit finiten Volumen: HLL-Flüsse, hydrostatische Rekonstruktion nach Audusse u. a. (2004) für das Trockenfallen
 * (ein ruhender See bleibt auf jedem Grund ruhig), MUSCL mit minmod und Heun in der Zeit, Reibung halbimplizit. Brecher
 * kommen als Walzen (Stöße) herein, laufen den Strand hinauf, kehren um und fließen als dünne Schicht zurück, bis die
 * nächste Walze sie trifft.
 * <p>
 * <b>Rand zur See:</b> dort, wo das Wasser {@link #boundaryDepth} tief ist (wandert mit dem Pegel). Hereinlaufend
 * die Welle aus dem Wellenzug ({@link WaveTrain}): je Reihe die Welle, die den Rand zuletzt passiert hat, als Walze
 * (Sägezahn, vorn steil) oder ungebrochen; hinauslaufend der Rücklauf, der den Rand ohne Spiegelung verlässt
 * (Riemann-Invarianten). Dazu der Wasserstand aus Ruhewasser, Brandungsschwebung und dem Set-up seewärts des Randes
 * (nach Bowen aus der mittleren Brechtiefe).
 * <p>
 * <b>Sand:</b> je Zelle ein Wasserfilm, der glänzt und in Sekunden versickert, die Nässe der obersten Schicht, die mit
 * dem Wetter trocknet (Minuten bei Sonne und Wind, Stunden im Schatten) und über dem Grundwasser nie ganz auf trocken
 * fällt, Schaum, der mit dem Wasser fährt und als Blasen liegen bleibt, und der <b>Spülsaum</b>: Tang und Schill, die der
 * Auflauf an seinem höchsten Punkt ablegt und die nächste kräftige Welle wieder aufnimmt.
 * <p>
 * <b>Ereignisse</b> ({@link #onSwash}): {@link Kind#RUNUP} am höchsten Punkt jedes Auflaufs (Ort, Höhe über dem
 * Ruhewasser) – für Sanderlinge, Spülsaum, Klang; {@link Kind#COLLISION}, wo der Rücklauf auf die nächste Walze trifft
 * (der kleine Stoß, Gischt); {@link Kind#BORE}, wenn eine Walze den Rand passiert. Kein anderes Package wird gebunden:
 * die Szene macht daraus Gischt ({@code spray}) und nassen Sand ({@code mudflat}). Gemeldet wird am Ende jedes Teilschritts
 * auf dem Faden, der {@link #step} ruft, Reihe für Reihe – nie gleichzeitig aus mehreren Fäden.
 */
public final class Swash {
    /** Was am Strand geschieht. */
    public enum Kind { RUNUP, COLLISION, BORE }

    /** Ein Ereignis: Ort (x längs des Profils, y längs des Kamms, z Höhe), Art, Stärke (RUNUP: Höhe über dem Ruhewasser in m; COLLISION: Stoß in m²/s; BORE: Höhe der Walze in m). */
    @FunctionalInterface
    public interface Event { void swash(double x, double y, double z, Kind kind, double strength); }

    /** Wetter für die Trocknung: Sonne 0..1, Wind (m/s), Luft (°C), Regen (mm/h) – gleiche Bedeutung wie in {@code mudflat}. */
    public record Weather(double sun, double wind, double airC, double rainMmH) {
        public static final Weather CALM_SHADE = new Weather(0, 0, 10, 0), SUNNY_WINDY = new Weather(1, 10, 20, 0), OVERCAST = new Weather(0.3, 4, 12, 0);
    }

    public static final double G = Surf.G;
    /** Unter dieser Tiefe gilt eine Zelle als trocken (m). */
    public static final double DRY = 1e-4;

    public final int rows, nx;
    /** Profil von xa (seewärts) in Zellen der Breite cell; Breite längs des Kamms (m). */
    public final double xa, cell, width;
    /** Zustand je Reihe und Zelle (Index r·nx + i): Tiefe d (m), Fluss q = d·u (m²/s). */
    public final double[] d, q;
    /** Grund in der Mitte jeder Zelle (m, gleiches Bezugssystem wie der Wellenzug); seewärts zuerst. */
    public final double[] bed;
    /** Sand je Zelle: Wasserfilm (glänzt, 0..1), Nässe der obersten Schicht (0..1), Schaum auf dem Wasser, Blasen auf dem Sand, Spülsaum (Tang und Schill, 0..1). */
    public final float[] film, moist, foam, bubbles, wrack;
    /** Im Sand über dem Grundwasser gespeichertes Wasser (m), sickert mit {@link #tauDrain} nach unten ab. */
    public final float[] store;

    /** Tiefe am Rand zur See (m); NaN: aus H_s, 0,75·H_s zwischen 0,35 und 1,2 m. */
    public double boundaryDepth = Double.NaN;
    /** Reibung c_f [G: an Stockdon (2006) abgestimmt]. */
    public double cf = 0.012;
    /** Versickern in trockenen Sand (m/s) [G: Mittelsand 1e-4 … 1e-3], Fassung der obersten Schicht als Wasser (m). */
    public double infiltration = 3e-4, layer = 0.015;
    /** Was der ungesättigte Sand aufnimmt, bevor er nichts mehr schluckt (m Wasser), und wie schnell es nach unten absickert (s) [G: Porenraum 0,35 von 10 cm]. */
    public double capacity = 0.035, tauDrain = 90;
    /** Halbwertszeit der Nässe: im Schatten bei Windstille (h), in Sonne mit frischem Wind (min) [G: Sand trocknet schneller als Schlick]. */
    public double halfDryShadeHours = 4, halfDrySunWindMinutes = 12;
    /** Steighöhe des Kapillarsaums über dem Grundwasser (m) [G: Mittelsand 0,3 … 0,5]. */
    public double capillary = 0.4;
    /** Grundwasser im Strand über dem Ruhewasser als Anteil des mittleren Auflaufs [G: Nielsen 1990, Überhöhung]. */
    public double groundwaterRise = 0.4;
    /** Abbau: Schaum auf dem Wasser (s), Blasen auf dem Sand (s). */
    public double tauFoam = 8, tauBubbles = 25;
    /** Umkehr der Spitze: so weit (m senkrecht) muss sie zurückweichen bzw. wieder steigen, damit ein Auflauf zählt. */
    public double turn = 0.012;
    /** Ab dieser Tiefe zählt Wasser als Spitze des Auflaufs (m) [G: ein Laufdraht misst 1–2 cm über dem Sand]. */
    public double tipDepth = 0.01;
    /** Höhe der Walzen am Rand höchstens dieser Anteil der Tiefe [G: im Inneren der Brandung 0,5…0,7]. */
    public double gammaBoundary = 0.78;
    /** Verstärkung der Brandungsschwebung am Rand [G: an Stockdon (2006) abgestimmt, dort ist die langperiodische Schwankung auf flachen Stränden der größte Teil des Auflaufs]. */
    public double infragravity = 4;
    /** Courant-Zahl. */
    public double courant = 0.45;
    public Weather weather = Weather.OVERCAST;
    public Event onSwash;

    private double time;
    /** Rechenzeit des letzten Schritts (ms), Teilschritte. */
    public double ms;
    public int substeps;
    /** Läufe: Zählung der Auflaufspitzen, ihre Höhen (m über Ruhewasser), Stöße, Walzen. */
    public int runups, collisions, bores;
    private double[] runupHeights = new double[256];
    /** Gemittelte Höhe der Brecher, der Auflauf und der Rand jetzt. */
    private double hbMean = Double.NaN;
    /** Je Reihe: gleitendes Mittel und Summe der Spitzen (in fester Reihenfolge gemittelt, damit das Ergebnis nicht von den Fäden abhängt). */
    private final double[] rowMean, rowSum;
    /** Ereignisse je Reihe, gesammelt im parallelen Teilschritt und danach in der Reihenfolge der Reihen gemeldet. */
    private final java.util.ArrayList<Object[]>[] pending;
    private int ib = 1;
    public double levelB, setupB;

    // Spitze je Reihe
    private final int[] tip, tipMax, tipMin;
    private final boolean[] rising;
    private final double[] lastCollision, lastBoreX;
    private final int[] lastBoreId;
    // Arbeitsfelder
    private final double[] d1, q1, fd, fq, gw;

    /**
     * Ein Strand für alle Reihen des Wellenzugs von xa bis xe (seewärts bis landwärts), Zellen zu cell m. xa muss so
     * weit draußen liegen, dass dort bei niedrigstem Pegel noch {@link #boundaryDepth} Wasser steht.
     */
    public Swash(WaveTrain t, double xa, double xe, double cell) {
        this(t.rows, xa, xe, cell, t.width, t.profile, t.level);
    }

    /**
     * Ein Strand ohne Wellenzug: rows Profile über dem Grund p, Ruhewasser level. Ohne Wellenzug ({@code step(dt, null)})
     * ist der Rand zur See eine Wand – für Versuche (Dammbruch, ruhender See, Masse) und für eigene Antriebe über
     * {@link #d} und {@link #q}.
     */
    public Swash(int rows, double xa, double xe, double cell, double width, WaveTrain.Profile p, double level) {
        this.rows = rows;
        this.xa = xa;
        this.cell = cell;
        this.width = width;
        this.nx = Math.max(8, (int) Math.ceil((xe - xa) / cell));
        int n = rows * nx;
        d = new double[n];
        q = new double[n];
        d1 = new double[n];
        q1 = new double[n];
        fd = new double[n];
        fq = new double[n];
        bed = new double[nx];
        film = new float[n];
        moist = new float[n];
        foam = new float[n];
        bubbles = new float[n];
        wrack = new float[n];
        store = new float[n];
        tip = new int[rows];
        tipMax = new int[rows];
        tipMin = new int[rows];
        rising = new boolean[rows];
        lastCollision = new double[rows];
        lastBoreX = new double[rows];
        lastBoreId = new int[rows];
        gw = new double[1];
        inflowRow = new double[rows];
        rowMean = new double[rows];
        rowSum = new double[rows];
        Arrays.fill(rowMean, Double.NaN);
        @SuppressWarnings("unchecked")
        java.util.ArrayList<Object[]>[] pe = new java.util.ArrayList[rows];
        for (int r = 0; r < rows; r++) pe[r] = new java.util.ArrayList<>();
        pending = pe;
        infilRow = new double[rows];
        Arrays.fill(lastCollision, -1e9);
        Arrays.fill(lastBoreId, -1);
        this.level = level;
        setProfile(p, level);
    }

    /** Ruhewasser für den Betrieb ohne Wellenzug (m). */
    public double level;

    /** Grund neu (etwa nach einem Abbruch); Wasser bis zum Ruhewasser, darüber trocken und mäßig feucht. */
    public void setProfile(WaveTrain.Profile p, double level) {
        for (int i = 0; i < nx; i++) bed[i] = p.bed(x(i));
        for (int r = 0; r < rows; r++)
            for (int i = 0; i < nx; i++) {
                int k = r * nx + i;
                d[k] = Math.max(0, level - bed[i]);
                q[k] = 0;
                moist[k] = d[k] > DRY ? 1 : (float) equilibrium(bed[i] - level);
                film[k] = d[k] > DRY ? 1 : 0;
            }
        gw[0] = level;
    }

    public double time() { return time; }

    /** Mitte der Zelle i (m). */
    public double x(int i) { return xa + (i + 0.5) * cell; }

    /** Zelle zum Ort x (geklemmt). */
    public int cellAt(double x) { return Math.max(0, Math.min(nx - 1, (int) Math.floor((x - xa) / cell))); }

    /** Lage des Randes zur See jetzt (m). */
    public double boundaryX() { return xa + ib * cell; }

    /** Grundwasser im Strand (m, gleiches Bezugssystem). */
    public double groundwater() { return gw[0]; }

    // ------------------------------------------------------------------ Abfragen

    /** Wasserstand am Ort x in Reihe r (Grund + Tiefe; trocken: NaN), linear zwischen den Zellmitten. */
    public double surface(int r, double x) {
        double u = (x - xa) / cell - 0.5;
        int i = (int) Math.floor(u);
        if (i < ib - 1 || i + 1 >= nx) return Double.NaN;
        double f = u - i;
        int k = r * nx + i;
        double da = d[k], db = d[k + 1];
        if (da <= 1e-3 && db <= 1e-3) return Double.NaN;
        // an der Spitze nicht über die trockene Zelle hinaus strecken
        if (db <= 1e-3) return f < 0.5 ? bed[i] + da : Double.NaN;
        if (da <= 1e-3) return f > 0.5 ? bed[i + 1] + db : Double.NaN;
        return (bed[i] + da) * (1 - f) + (bed[i + 1] + db) * f;
    }

    /** Tiefe am Ort (m, 0 trocken). */
    public double depth(int r, double x) { return sample(d, r, x); }

    /** Geschwindigkeit am Ort (m/s, zur Küste positiv). */
    public double velocity(int r, double x) {
        int k = r * nx + cellAt(x);
        return d[k] > 1e-3 ? q[k] / d[k] : 0;
    }

    /** Glänzender Wasserfilm 0..1, Nässe 0..1, Schaum, Blasen, Spülsaum am Ort. */
    public double filmAt(int r, double x) { return sample(film, r, x); }
    public double moistAt(int r, double x) { return sample(moist, r, x); }
    public double foamAt(int r, double x) { return sample(foam, r, x); }
    public double bubblesAt(int r, double x) { return sample(bubbles, r, x); }
    public double wrackAt(int r, double x) { return sample(wrack, r, x); }

    /** Nässe für die Szene: 1 unter Wasser und im Film, sonst die Nässe der Schicht. */
    public double wetnessAt(int r, double x) {
        int k = r * nx + cellAt(x);
        return d[k] > 1e-3 ? 1 : Math.max(film[k], moist[k]);
    }

    /** Höchste Wasserlinie jetzt in Reihe r: Ort der Spitze (m). */
    public double tipX(int r) { return x(tip[r]); }

    private double sample(double[] a, int r, double x) {
        double u = (x - xa) / cell - 0.5;
        int i = Math.max(0, Math.min(nx - 2, (int) Math.floor(u)));
        double f = Math.max(0, Math.min(1, u - i));
        int k = r * nx + i;
        return a[k] * (1 - f) + a[k + 1] * f;
    }

    private double sample(float[] a, int r, double x) {
        double u = (x - xa) / cell - 0.5;
        int i = Math.max(0, Math.min(nx - 2, (int) Math.floor(u)));
        double f = Math.max(0, Math.min(1, u - i));
        int k = r * nx + i;
        return a[k] * (1 - f) + a[k + 1] * f;
    }

    // ------------------------------------------------------------------ Statistik

    /** Höhen der bisherigen Auflaufspitzen über dem Ruhewasser (m), ungeordnet (die Reihen rechnen parallel); für Verteilungen sortieren. */
    public double[] runupHeights() { return Arrays.copyOf(runupHeights, runups); }

    /** R_2%: von 2 % der Auflaufspitzen erreicht oder übertroffen (m über Ruhewasser); NaN ohne Spitzen. */
    public double r2() { return exceed(0.02); }

    /** Höhe, die der Anteil p der Spitzen erreicht oder übertrifft. */
    public double exceed(double p) {
        if (runups == 0) return Double.NaN;
        double[] s = runupHeights();
        Arrays.sort(s);
        int k = (int) Math.floor((1 - p) * (s.length - 1) + 1e-9);
        return s[Math.max(0, Math.min(s.length - 1, k))];
    }

    /** Mittlere Auflaufhöhe (m über Ruhewasser). */
    public double meanRunup() {
        if (runups == 0) return Double.NaN;
        double sum = 0;
        for (double v : rowSum) sum += v;
        return sum / runups;
    }

    /** Statistik zurücksetzen (nach dem Einschwingen). */
    public void resetStatistics() { runups = 0; Arrays.fill(rowSum, 0); collisions = 0; bores = 0; }

    /**
     * Faustformel für den Auflauf nach Stockdon u. a. (2006): R_2% = 1,1·(η̄ + S/2) mit η̄ = 0,35·β·√(H₀L₀) und
     * S = √(H₀L₀·(0,563·β² + 0,004)); auf sehr flachen Stränden (ξ₀ &lt; 0,3) R_2% = 0,043·√(H₀L₀).
     * H₀ die Höhe draußen (zurückgerechnet aufs Tiefe), T_p, β der Hang am Strand.
     */
    public static double stockdon(double h0, double tp, double beta) {
        double l0 = Surf.deepLength(tp), hl = Math.sqrt(h0 * l0);
        if (beta / Math.sqrt(h0 / l0) < 0.3) return 0.043 * hl;
        double setup = 0.35 * beta * hl, s = Math.sqrt(h0 * l0 * (0.563 * beta * beta + 0.004));
        return 1.1 * (setup + s / 2);
    }

    // ------------------------------------------------------------------ Schritt

    /** Halbwertszeit der Nässe (s) bei diesem Wetter [G]. */
    public double halfLife(Weather w) {
        double sun = clamp01(w.sun()), wind = Math.max(0, Math.min(1, w.wind() / 10)), warm = clamp01((w.airC() - 5) / 20);
        double k = clamp01(0.6 * sun + 0.4 * wind + 0.1 * warm * sun);
        double hours = halfDryShadeHours * (1 - k) + halfDrySunWindMinutes / 60.0 * k;
        if (w.airC() < 0) hours *= 3;
        if (w.rainMmH() > 0.1) hours *= 20;
        return hours * 3600;
    }

    /** Nässe im Gleichgewicht mit dem Grundwasser in der Höhe z darüber (m) [G: Kapillarsaum, darüber trocken]. */
    public double equilibrium(double above) {
        if (above <= 0) return 0.92;
        return 0.04 + 0.88 * Math.exp(-above / capillary);
    }

    /** Ein Schritt der Szenenzeit, getrieben vom Wellenzug t (der vorher seinen Schritt gemacht hat). */
    public void step(double dt, WaveTrain t) {
        long t0 = System.nanoTime();
        if (!(dt > 0)) return;
        if (t == null) { stepClosed(dt, t0); return; }
        level = t.level;
        // Rand: Tiefe boundaryDepth beim Pegel mit Schwebung
        double hB = Double.isNaN(boundaryDepth) ? Math.max(0.35, Math.min(3.0, 1.25 * t.hs)) : boundaryDepth;
        // mittlere Brechhöhe der letzten Wellen (für den Set-up seewärts des Randes)
        for (WaveTrain.Wave w : t.waves)
            if (w.type != Surf.Breaker.NONE && !Double.isNaN(w.xBreak) && w.id != lastHb) {
                lastHb = w.id;
                hbMean = Double.isNaN(hbMean) ? w.hBreak : hbMean + 0.05 * (w.hBreak - hbMean);
            }
        double lev = t.level;
        levelRef = lev;
        int nib = 1;
        while (nib < nx - 4 && lev - bed[nib] > hB) nib++;
        ib = Math.max(1, nib);
        double xb = boundaryX();
        double h0 = Math.max(0.05, lev - bed[ib]);
        double hBr = Double.isNaN(hbMean) ? h0 : hbMean / t.gamma;
        // Set-up seewärts des Randes nach Bowen u. a. (1968): am Brechpunkt der Set-down −γ²·h_b/16, danach steigt der
        // Wasserstand mit K = 1/(1 + 8/(3γ²)) je Meter, den die Tiefe abnimmt
        double gm = t.gamma, kB = 1 / (1 + 8 / (3 * gm * gm));
        setupB = hBr > h0 ? -gm * gm * hBr / 16 + kB * (hBr - h0) : 0;
        levelB = t.level + infragravity * t.surfBeat(xb) + setupB;
        // Grundwasser: Ruhewasser + Anteil des mittleren Auflaufs, träge
        double rm = 0;
        int nm = 0;
        for (int r = 0; r < rows; r++) if (!Double.isNaN(rowMean[r])) { rm += rowMean[r]; nm++; }
        double target = lev + groundwaterRise * (nm == 0 ? 0 : Math.max(0, rm / nm));
        gw[0] += (target - gw[0]) * Math.min(1, dt / 120);
        // Teilschritte nach Courant
        double rest = dt;
        int steps = 0;
        while (rest > 1e-9 && steps < 400) {
            double vmax = 1e-6;
            for (int k = 0; k < d.length; k++) {
                double dk = d[k];
                if (dk > DRY) vmax = Math.max(vmax, Math.abs(q[k] / dk) + Math.sqrt(G * dk));
            }
            vmax = Math.max(vmax, Math.sqrt(G * (h0 + 1.5 * t.hs)) * 1.5);
            double h = Math.min(rest, courant * cell / vmax);
            sub(h, t, xb, h0);
            rest -= h;
            steps++;
        }
        substeps = steps;
        sand(dt, t);
        ms = (System.nanoTime() - t0) / 1e6;
    }

    private int lastHb = -1;

    /** Ohne Wellenzug: Wand bei ib = 1, nur Teilschritte und Sand. */
    private void stepClosed(double dt, long t0) {
        ib = 1;
        levelRef = level;
        levelB = level;
        setupB = 0;
        double rest = dt;
        int steps = 0;
        while (rest > 1e-12 && steps < 4000) {
            double vmax = 1e-6;
            for (int k = 0; k < d.length; k++) {
                double dk = d[k];
                if (dk > DRY) vmax = Math.max(vmax, Math.abs(q[k] / dk) + Math.sqrt(G * dk));
            }
            double h = Math.min(rest, courant * cell / vmax);
            sub(h, null, xa, 0);
            rest -= h;
            steps++;
        }
        substeps = steps;
        sand(dt, null);
        ms = (System.nanoTime() - t0) / 1e6;
    }

    /** Ein Teilschritt (Heun) für alle Reihen. */
    private void sub(double h, WaveTrain t, double xb, double h0) {
        final double tNow = time;
        IntStream.range(0, rows).parallel().forEach(r -> {
            int o = r * nx;
            ghost(r, t, xb, h0);
            // Stufe 1: U1 = U + h·L(U)
            double f1 = flux(o, d, q, fd, fq);
            for (int i = ib; i < nx; i++) {
                int k = o + i;
                d1[k] = d[k] + h * fd[k];
                q1[k] = q[k] + h * fq[k];
                fix(d1, q1, k, h);
            }
            for (int i = 0; i < ib; i++) { d1[o + i] = d[o + i]; q1[o + i] = q[o + i]; }
            // Stufe 2: U = ½(U + U1 + h·L(U1))
            double f2 = flux(o, d1, q1, fd, fq);
            inflowRow[r] += 0.5 * h * (f1 + f2) * cell / cell;
            double infil = 0;
            for (int i = ib; i < nx; i++) {
                int k = o + i;
                d[k] = 0.5 * (d[k] + d1[k] + h * fd[k]);
                q[k] = 0.5 * (q[k] + q1[k] + h * fq[k]);
                fix(d, q, k, h);
                // Versickern in trockenen Sand über dem Grundwasser
                if (d[k] > DRY && bed[i] > gw[0] && infiltration > 0) {
                    double inf = Math.min(d[k], infiltration * Math.max(0, 1 - store[k] / capacity) * h);
                    store[k] += (float) inf;
                    d[k] -= inf;
                    if (d[k] <= DRY) q[k] = 0;
                    infil += inf;
                    moist[k] = (float) Math.min(1, moist[k] + inf / layer);
                }
            }
            infilRow[r] += infil * cell;
            track(r, tNow + h);
        });
        time += h;
        // Ereignisse auf dem rufenden Faden, Reihe für Reihe (gleiche Reihenfolge bei jedem Lauf)
        for (int r = 0; r < rows; r++) {
            java.util.ArrayList<Object[]> pe = pending[r];
            if (pe.isEmpty()) continue;
            if (onSwash != null)
                for (Object[] e : pe) onSwash.swash((Double) e[0], (Double) e[1], (Double) e[2], (Kind) e[3], (Double) e[4]);
            pe.clear();
        }
    }

    /** Volumen in der Reihe r zwischen dem Rand und dem Ende (m² je m Kamm). */
    public double volume(int r) {
        double v = 0;
        for (int i = ib; i < nx; i++) v += d[r * nx + i];
        return v * cell;
    }

    /** Durch den Rand hereingeflossen (m²) und versickert (m²) je Reihe, seit dem Start. */
    public double inflow(int r) { return inflowRow[r]; }
    public double infiltrated(int r) { return infilRow[r]; }

    private final double[] inflowRow, infilRow;

    /** Geisterzellen 0..ib−1: der Zustand am Rand aus der hereinlaufenden Welle und dem hinauslaufenden Rücklauf. */
    private void ghost(int r, WaveTrain t, double xb, double h0) {
        int o = r * nx;
        if (t == null) {
            // Wand: gespiegelt
            d[o] = Math.max(0, bed[1] + d[o + 1] - bed[0]);
            q[o] = -q[o + 1] * (d[o] > DRY ? d[o] / Math.max(DRY, d[o + 1]) : 0);
            return;
        }
        double eta = incident(r, t, xb, h0);
        double hIn = Math.max(0.02, levelB + eta - bed[ib - 1]);
        double c0 = Math.sqrt(G * Math.max(0.02, levelB - bed[ib - 1]));
        double uIn = eta * c0 / Math.max(0.05, levelB - bed[ib - 1]);
        double rPlus = uIn + 2 * Math.sqrt(G * hIn);
        int k = o + ib;
        double dk = Math.max(0, d[k]), uk = dk > 1e-3 ? q[k] / dk : 0;
        double rMinus = uk - 2 * Math.sqrt(G * dk);
        double c = Math.max(0, (rPlus - rMinus) / 4), u = (rPlus + rMinus) / 2;
        double dg = c * c / G;
        for (int i = 0; i < ib; i++) {
            // Geister gleich tief über ihrem Grund wie am Rand (Wasserspiegel gleich)
            double eg = bed[ib - 1] + dg;
            double di = Math.max(0, eg - bed[i]);
            d[o + i] = di;
            q[o + i] = di * u;
        }
    }

    /** Hereinlaufende Auslenkung am Rand in Reihe r (m, um den mittleren Wasserstand). */
    private double incident(int r, WaveTrain t, double xb, double h0) {
        WaveTrain.Wave passed = null, next = null;
        for (WaveTrain.Wave w : t.waves) {
            if (w.x >= xb) { if (passed == null || w.x < passed.x) passed = w; }
            else if (next == null || w.x > next.x) next = w;
        }
        if (passed == null && next == null) return 0;
        if (passed == null) {
            // noch keine Welle am Rand: Trog der kommenden
            double Hn = t.heightRow(next, r);
            return -0.5 * Hn * Math.min(1, Math.max(0, 1 - (xb - next.x) / Math.max(1, next.c * next.period)) + 0.0);
        }
        double H = t.heightRow(passed, r);
        if (passed.broken(r)) H = Math.min(H, gammaBoundary * h0);
                double c = Math.max(0.5, Math.sqrt(G * h0));
        double spacing = next != null ? Math.max(2, passed.x - next.x) : c * passed.period;
        double phi = Math.min(1, (passed.x - xb) / spacing);
        if (passed.id != lastBoreId[r]) {
            lastBoreId[r] = passed.id;
            boolean broken = passed.broken(r);
            if (broken) emit(r, xb, t.rowY(r), levelB, Kind.BORE, H);
            synchronized (this) { if (broken) bores++; }
        }
        if (passed.broken(r)) return H * (0.5 - phi);             // Walze: vorn steil, dahinter fallend
        // ungebrochen: Kamm schmal, Trog breit (genähert)
        double s = Math.cos(2 * Math.PI * phi);
        return H * (0.5 * s + 0.12 * (s * s - 0.5));
    }

    /** Rechte Seite L(U) = −(F_{i+½} − F_{i−½})/dx + S für die Zellen ib..nx−1 einer Reihe (in outD, outQ); gibt den Fluss durch den Rand (m²/s). */
    private double flux(int o, double[] dd, double[] qq, double[] outD, double[] outQ) {
        int n = nx;
        // Rekonstruktion je Zelle (MUSCL, minmod auf Tiefe, Oberfläche, Geschwindigkeit): West- und Ostrand
        double[] dW = buf(0), dE = buf(1), uW = buf(2), uE = buf(3), zW = buf(4), zE = buf(5);
        for (int i = ib - 1; i < n; i++) {
            int k = o + i;
            double di = dd[k], ui = di > DRY ? qq[k] / di : 0;
            boolean inner = i > ib - 1 && i < n - 1 && dd[k - 1] > 0.01 && di > 0.01 && dd[k + 1] > 0.01;
            if (inner) {
                double em = bed[i - 1] + dd[k - 1], ep = bed[i + 1] + dd[k + 1], ei = bed[i] + di;
                double um = qq[k - 1] / dd[k - 1], up = qq[k + 1] / dd[k + 1];
                double sd = minmod(di - dd[k - 1], dd[k + 1] - di), se = minmod(ei - em, ep - ei), su = minmod(ui - um, up - ui);
                dW[i] = di - 0.5 * sd; dE[i] = di + 0.5 * sd;
                zW[i] = ei - 0.5 * se - dW[i]; zE[i] = ei + 0.5 * se - dE[i];
                uW[i] = ui - 0.5 * su; uE[i] = ui + 0.5 * su;
            } else {
                dW[i] = dE[i] = di;
                zW[i] = zE[i] = bed[i];
                uW[i] = uE[i] = ui;
            }
        }
        // Flüsse an den Flächen i+½ für i = ib−1 .. n−2
        double[] fD = buf(6), fQL = buf(7), fQR = buf(8), f = buf(9);
        for (int i = ib - 1; i < n - 1; i++) {
            double dl = dE[i], zl = zE[i], dr = dW[i + 1], zr = zW[i + 1];
            double zs = Math.max(zl, zr);
            double dls = Math.max(0, dl + zl - zs), drs = Math.max(0, dr + zr - zs);
            hll(dls, uE[i], drs, uW[i + 1], f);
            fD[i] = f[0];
            fQL[i] = f[1] + 0.5 * G * (dl * dl - dls * dls);   // für die Zelle links (i)
            fQR[i] = f[1] + 0.5 * G * (dr * dr - drs * drs);   // für die Zelle rechts (i+1)
        }
        // Landende: Wand
        fD[n - 1] = 0;
        fQL[n - 1] = 0.5 * G * dE[n - 1] * dE[n - 1];
        for (int i = ib; i < n; i++) {
            int k = o + i;
            double src = 0.5 * G * (dW[i] + dE[i]) * (zW[i] - zE[i]);
            outD[k] = -(fD[i] - fD[i - 1]) / cell;
            outQ[k] = -(fQL[i] - fQR[i - 1] - src) / cell;
        }
        return fD[ib - 1];
    }

    private final ThreadLocal<double[][]> work = ThreadLocal.withInitial(() -> new double[10][]);

    private double[] buf(int j) {
        double[][] w = work.get();
        if (w[j] == null || w[j].length < nx) w[j] = new double[Math.max(nx, 2)];
        return w[j];
    }

    /** HLL-Fluss für (d, d·u) zwischen links und rechts, trockene Seiten nach Toro; Ergebnis in out[0], out[1]. */
    static void hll(double dl, double ul, double dr, double ur, double[] out) {
        if (dl <= DRY && dr <= DRY) { out[0] = 0; out[1] = 0; return; }
        double cl = Math.sqrt(G * dl), cr = Math.sqrt(G * dr);
        if (dl <= DRY) ul = 0;
        if (dr <= DRY) ur = 0;
        double sl, sr;
        if (dl <= DRY) { sl = ur - 2 * cr; sr = ur + cr; }
        else if (dr <= DRY) { sl = ul - cl; sr = ul + 2 * cl; }
        else { sl = Math.min(ul - cl, ur - cr); sr = Math.max(ul + cl, ur + cr); }
        double fl0 = dl * ul, fl1 = dl * ul * ul + 0.5 * G * dl * dl;
        double fr0 = dr * ur, fr1 = dr * ur * ur + 0.5 * G * dr * dr;
        if (sl >= 0) { out[0] = fl0; out[1] = fl1; return; }
        if (sr <= 0) { out[0] = fr0; out[1] = fr1; return; }
        double inv = 1 / (sr - sl);
        out[0] = (sr * fl0 - sl * fr0 + sl * sr * (dr - dl)) * inv;
        out[1] = (sr * fl1 - sl * fr1 + sl * sr * (dr * ur - dl * ul)) * inv;
    }

    /** Positiv halten, Reibung halbimplizit, Geschwindigkeit in sehr dünnen Schichten entschärft (Kurganov und Petrova 2007). */
    private void fix(double[] dd, double[] qq, int k, double h) {
        double dk = dd[k];
        if (dk < DRY) { dd[k] = Math.max(0, dk); qq[k] = 0; return; }
        double d4 = dk * dk * dk * dk, e4 = EPS * EPS * EPS * EPS;
        double u = Math.sqrt(2) * dk * qq[k] / Math.sqrt(d4 + Math.max(d4, e4));
        if (Math.abs(u) > 15) u = Math.signum(u) * 15;
        double fr = 1 + h * cf * Math.abs(u) / Math.max(dk, 0.005);
        qq[k] = dk * u / fr;
    }

    /** Tiefe, unter der die Geschwindigkeit entschärft wird (m). */
    static final double EPS = 2e-3;

    static double minmod(double a, double b) { return a * b <= 0 ? 0 : Math.abs(a) < Math.abs(b) ? a : b; }

    /** Spitze, Auflauf und Stoß in Reihe r verfolgen. */
    private void track(int r, double tNow) {
        int o = r * nx;
        int i = ib;
        while (i + 1 < nx && d[o + i + 1] > tipDepth) i++;
        tip[r] = i;
        double zTip = bed[i] - 0; // Höhe der Spitze
        if (rising[r]) {
            if (i > tipMax[r]) tipMax[r] = i;
            // umgekehrt: zwei Zellen und 2 cm unter dem Höchsten
            if (i < tipMax[r] && bed[tipMax[r]] - bed[i] > turn) {
                rising[r] = false;
                tipMin[r] = i;
                int m = tipMax[r];
                double R = bed[m] - levelRef;
                recordRunup(r, m, R);
            }
        } else {
            if (i < tipMin[r]) tipMin[r] = i;
            if (i > tipMin[r] && bed[i] - bed[tipMin[r]] > turn) { rising[r] = true; tipMax[r] = i; }
        }
        // Stoß: Rücklauf (seewärts) trifft die hereinlaufende Walze
        if (tNow - lastCollision[r] > 1.5) {
            for (int j = ib + 1; j < i; j++) {
                int k = o + j;
                double da = d[k], db = d[k + 1];
                if (da < 0.02 || db < 0.02) continue;
                double ua = q[k] / da, ub = q[k + 1] / db;
                if (ua > 0.4 && ub < -0.4) {
                    double s = (ua - ub) * 0.5 * (da + db);
                    lastCollision[r] = tNow;
                    synchronized (this) { collisions++; }
                    emit(r, x(j) + 0.5 * cell, (r + 0.5) * width / rows, bed[j] + da, Kind.COLLISION, s);
                    break;
                }
            }
        }
    }

    /** Bezug der Auflaufhöhe: das Ruhewasser (ohne Schwebung und Set-up, wie bei Stockdon). */
    private double levelRef;

    private void emit(int r, double x, double y, double z, Kind k, double s) { pending[r].add(new Object[]{x, y, z, k, s}); }

    private void recordRunup(int r, int m, double R) {
        synchronized (this) {
            if (runups == runupHeights.length) runupHeights = Arrays.copyOf(runupHeights, runups * 2);
            runupHeights[runups++] = R;
        }
        rowSum[r] += R;
        rowMean[r] = Double.isNaN(rowMean[r]) ? R : rowMean[r] + 0.02 * (R - rowMean[r]);
        // Spülsaum: Tang und Schill am höchsten Punkt abgelegt
        int k = r * nx + m;
        wrack[k] = (float) Math.min(1, wrack[k] + 0.25);
        if (m > 0) wrack[k - 1] = (float) Math.min(1, wrack[k - 1] + 0.1);
        emit(r, x(m), (r + 0.5) * width / rows, bed[m], Kind.RUNUP, R);
    }

    /** Sand: Film, Nässe, Schaum, Blasen, Spülsaum (einmal je Schritt). */
    private void sand(double dt, WaveTrain t) {
        final double xb = boundaryX();
        double half = halfLife(weather);
        double decay = Math.pow(0.5, dt / half);
        double ff = Math.exp(-dt / tauFoam), fb = Math.exp(-dt / tauBubbles), fs = Math.exp(-dt / tauDrain);
        double rainWet = Math.min(1, weather.rainMmH() / 2.0);
        double g = gw[0];
        IntStream.range(0, rows).parallel().forEach(r -> {
            int o = r * nx;
            for (int i = ib; i < nx; i++) {
                int k = o + i;
                double dk = d[k];
                double above = bed[i] - g;
                double eq = equilibrium(above);
                if (dk > 0.002) {
                    film[k] = 1;
                    if (above <= 0) moist[k] = 1;
                    double u = Math.abs(q[k] / Math.max(dk, 1e-3));
                    // kräftige Strömung nimmt Tang und Schill wieder auf
                    if (dk > 0.03 && u > 0.6) wrack[k] = (float) (wrack[k] * Math.exp(-dt * 2));
                    // Schaum auf dem Wasser: verdriftet unten, hier nur Abbau
                    foam[k] = (float) (foam[k] * ff);
                } else {
                    // Film versickert: hoch über dem Grundwasser in Sekunden, nahe an ihm hält er sich eine Minute und länger [G]
                    double tauF = 3 + 40 * Math.exp(-Math.max(0, above) / 0.1) + (above <= 0 ? 30 : 0);
                    film[k] = (float) (film[k] * Math.exp(-dt / tauF));
                    // Schaum, der liegen bleibt, wird zu Blasen
                    if (foam[k] > bubbles[k]) bubbles[k] = foam[k] * 0.8f;
                    foam[k] = 0;
                    // Nässe: über dem Gleichgewicht trocknet sie mit dem Wetter, darunter steigt sie langsam aus dem Grundwasser
                    double m = moist[k];
                    if (m > eq) m = eq + (m - eq) * decay;
                    else m = m + (eq - m) * Math.min(1, dt / 1800);
                    if (rainWet > m) m = rainWet;
                    moist[k] = (float) m;
                }
                bubbles[k] = (float) (bubbles[k] * fb);
                store[k] = (float) (store[k] * fs);
            }
            // Schaum fährt mit dem Wasser (halb Lagrange), frisch an der Front der Walzen
            for (int i = nx - 1; i > ib; i--) {
                int k = o + i;
                if (d[k] <= 0.002) continue;
                double u = q[k] / Math.max(d[k], 1e-3);
                double src = i - u * dt / cell;
                int s0 = (int) Math.floor(src);
                double f = src - s0;
                if (s0 < ib || s0 + 1 >= nx) continue;
                foam[k] = (float) (foam[o + s0] * (1 - f) + foam[o + s0 + 1] * f);
            }
            // Schaum der Walzen kommt über den Rand herein
            if (t != null) for (int i = ib; i < Math.min(nx, ib + 3); i++) foam[o + i] = (float) Math.max(foam[o + i], t.foam.at(xb, r));
            // Fronten: wo die Tiefe zur Küste hin springt und das Wasser läuft, schäumt es
            for (int i = ib; i < nx - 1; i++) {
                int k = o + i;
                double jump = d[k] - d[k + 1];
                double u = d[k] > 1e-3 ? q[k] / d[k] : 0;
                if (u > 0.3 && jump > 0.04) foam[k] = (float) Math.min(1, Math.max(foam[k], jump * 6));
                if (u > 0.3 && d[k] > 0.002 && d[k + 1] <= 0.002) foam[k] = (float) Math.max(foam[k], 0.7);
            }
            // Spülsaum altert langsam (Tang trocknet, bleibt aber liegen)
        });
    }

    static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    /** „Auflauf: 37 Spitzen, R_2% 0,62 m (Stockdon 0,58 m) · 12 Stöße · Rand bei 82,0 m“ */
    public String describe(double stockdon) {
        return String.format(Locale.GERMAN, "Auflauf: %d Spitzen, R_2%% %.2f m%s · %d Stöße · Rand %.1f m, Teilschritte %d, %.1f ms",
                runups, r2(), Double.isNaN(stockdon) ? "" : String.format(Locale.GERMAN, " (Stockdon %.2f m)", stockdon), collisions, boundaryX(), substeps, ms);
    }
}
