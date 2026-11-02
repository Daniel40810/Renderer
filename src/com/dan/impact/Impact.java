package com.dan.impact;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Das Nahfeld des Eintauchens im Schnitt (2D, je Meter Breite): schwach kompressibles SPH mit zwei Phasen, Wasser und
 * Granulat, Luft als Hohlraum (LituyaBay1958 Phase 10, Werkbuch 5.3, 9.3).
 * <ul>
 * <li><b>Kern:</b> Wendland-C2, h = √2·dx; Dichte aus der Kontinuitätsgleichung in Volumenform (beide Phasen), Druck nach
 * Tait (γ 7, je Phase eigenes ρ₀, gemeinsame Schallgeschwindigkeit c₀), Zug abgeschnitten (Hohlraum statt Kleben);
 * Druckkraft nach Hu und Adams (Volumen, gemittelter Druck: Dichtesprung 1 : 2,7 ohne Zittern); künstliche Zähigkeit
 * nach Monaghan; Dichtediffusion (δ-SPH) nur innerhalb einer Phase, ohne den hydrostatischen Anteil (Fourtakis).</li>
 * <li><b>Granulat</b> nach Mohr-Coulomb ({@link Granulate}): Zähigkeit η = (c + p′ tan φ)/(2|D| + ε), am Grund mit tan δ,
 * implizit (Jacobi), damit ein ruhender Körper steht und ein gleitender genau die Grenzspannung trägt; p′ unter dem
 * Ruhespiegel ohne den Wasserdruck (Auftrieb, [G]).</li>
 * <li><b>Grund</b> als Linienzug mit drei Lagen fester Wandpartikel; ihr Druck aus den Nachbarn mit dem Schweregradienten
 * (Adami), das Wasser gleitet frei, das Granulat reibt.</li>
 * <li><b>Zulauf</b> ({@link #feed}): ein Pufferstreifen am Hang über der Uferlinie, in dem Reihen mit der Mächtigkeit s(t)
 * des {@link Inflow} entstehen und mit v(t) hangab geführt werden; an der Uferlinie werden sie frei.</li>
 * <li><b>Messen:</b> Pegel ({@link #gauge}) als oberer Rand der zusammenhängenden Wassersäule, Rand für die Welle
 * ({@link #far}: Wasserstand und Durchfluss an einem Schnitt, {@link Far}), Kaverne (Leere unter dem Ruhespiegel),
 * Energie der führenden Welle ρ g ∫ η² dx gegen die Gleitenergie ½ m v², Auflauf am Gegenhang.</li>
 * </ul>
 * Rechnung parallel über die Partikel, jedes sammelt nur von seinen Nachbarn: Zwei Läufe sind gleich. Der Kern braucht
 * nur Java 21. Der Würfel in 3D und die Rechnung auf der Grafikkarte kommen mit der Welle (Phase 11 und 13).
 */
public final class Impact {
    /** Partikelabstand: LOW 8 m, MEDIUM 5 m, HIGH 4 m, ULTRA 2 m. */
    public enum Quality {
        LOW(8), MEDIUM(5), HIGH(4), ULTRA(2);
        public final double dx;

        Quality(double dx) { this.dx = dx; }
    }

    public static final int STAGE = 10;

    /**
     * Übergabe an das Fernfeld am Rand des Nahfelds: je Randzelle Wasserstand über dem Ruhespiegel (m) und Durchfluss je
     * Meter (m²/s). Im Schnitt gibt es eine Zelle (0) bei z = 0 mit qz = 0.
     */
    @FunctionalInterface public interface Far { void at(double t, int cell, double x, double z, double eta, double qx, double qz); }

    /** Ein Gebiet im Schnitt, etwa für eine Schüttung. */
    @FunctionalInterface public interface Region { boolean inside(double x, double y); }

    /** Anfangszustand des Wassers: Auslenkung η(x) über dem Ruhespiegel und Geschwindigkeit u(x), über die Tiefe gleich. */
    public interface Init {
        double eta(double x);

        default double u(double x) { return 0; }
    }

    public static final byte WATER = 0, GRAIN = 1, WALL = 2, FEED = 3, GONE = 4;
    static final int LAYERS = 3;

    public final double dx, h;
    public String name = "Schnitt", source = "";
    public double gravity = 9.81;
    /** Schallgeschwindigkeit (m/s); mindestens zehnmal √(g·Tiefe) und das Sechsfache des Aufpralls. */
    public double c0 = 700;
    public double gamma = 7, alpha = 0.02, delta = 0.1, cfl = 0.2, maxDt = 0.01;
    /**
     * Unelastischer Stoß im Granulat: Zähigkeit nach Monaghan nur bei Annäherung, zwischen Körnern und am Grund. Ein
     * Korngerüst prallt nicht zurück; der Impuls normal zum Grund geht beim Aufschlag verloren wie im tiefengemittelten
     * Strom an einem Knick der Bahn [G].
     */
    public double grainAlpha = 0.02;
    public double waterDensity = 1000, level = 0;
    public Granulate grain = Granulate.rock();
    /** Reibung unter dem Ruhespiegel mit p′ = p − ρ_w g (Spiegel − y) [G]. */
    public boolean buoyantFriction = true;
    /**
     * Reibung am Grund mit dem Druck der Wand (Normalspannung samt Staudruck, ohne Porenwasser), nicht mit der Auflast:
     * Wo der Strom am Hangfuß umgelenkt wird, drückt er mit ρ v²/R auf den Grund und wird dort gebremst (Krümmungsterm
     * bei Savage-Hutter) [G].
     */
    public boolean dynamicBed = false;
    /** Druck im Gerüst höchstens die Auflast (gegen den Staudruck beim Aufprall). */
    public boolean capLoad = false;
    /**
     * Zeit (s), in der sich die Poren eines Korns unter Wasser füllen; 0: sofort bei Berührung. Das Wasser geht dabei
     * ganz in die Körner über (Masse und Impuls), deren Volumen bleibt: Unter Wasser verdrängt der Körper nur noch sein
     * Kornvolumen (FRITZ01: 39 % Poren) [G].
     */
    public double poreTime = 0;
    double poreMass;
    /** Jacobi-Schritte der impliziten Reibung je Zeitschritt. */
    public int frictionSweeps = 16;
    public double frictionEps = 1e-5;
    /** Abstand der Messungen (s). */
    public double sampleDt = 0.05;
    /** Wer das verlässt, ist fort (Spritzer weit hinaus). */
    public double boxX0 = -1e9, boxX1 = 1e9, boxY0 = -1e9, boxY1 = 1e9;
    /** Kaverne: gezählt zwischen diesen x (m); Energie der führenden Welle ab xEnergy. */
    public double cavernX0 = 0, cavernX1 = 700, energyFrom = 450;

    // Partikel
    int n;
    double[] px = new double[0], py = new double[0], vx = new double[0], vy = new double[0], rho = new double[0], prs = new double[0], mass = new double[0];
    byte[] kind = new byte[0];
    boolean[] ramp = new boolean[0];
    /** Ruhedichte je Teilchen (ein Korn mit gefüllten Poren ist schwerer); freier Porenraum je Korn (m² je Meter). */
    double[] rest = new double[0], pore = new double[0], open = new double[0];
    double slopeSin;
    // Arbeit
    double[] ax = new double[0], ay = new double[0], dr = new double[0], shear = new double[0], eta = new double[0], ux = new double[0], uy = new double[0],
            wx = new double[0], wy = new double[0], wrho = new double[0], u0x = new double[0], u0y = new double[0], load = new double[0];
    // Gitter der Nachbarn
    int[] cellStart = new int[0], order = new int[0], cellOf = new int[0];
    int gnx, gny;
    double gx0, gy0, gcell;

    // Grund
    double[] bedX = new double[0], bedY = new double[0];

    // Zulauf
    Inflow inflow;
    double gateX, gateY, tX, tY, nX, nY, feedLast, feedCarry, feedStart;
    int feedRows;
    double releasedMass, releasedVolume, releasedEnergy, releasedMomentum;
    double lostVolume;

    double time, amax = 0, lastDt;
    long steps;
    double nextSample = 0;
    double waterMass0 = Double.NaN;

    // Messungen
    public static final class Gauge {
        public final String name;
        public final double x;
        final List<double[]> series = new ArrayList<>();
        double max = Double.NEGATIVE_INFINITY, tMax = Double.NaN, min = Double.POSITIVE_INFINITY;

        Gauge(String name, double x) { this.name = name; this.x = x; }

        void add(double t, double eta) {
            series.add(new double[]{t, eta});
            if (!Double.isNaN(eta) && eta > max) { max = eta; tMax = t; }
            if (!Double.isNaN(eta) && eta < min) min = eta;
        }

        public synchronized double max() { return max; }

        public synchronized double timeOfMax() { return tMax; }

        public synchronized double min() { return min; }

        /** Zeitreihe {t, η} (Kopie). */
        public synchronized List<double[]> series() { return new ArrayList<>(series); }

        /** Erster Zeitpunkt, an dem η über {@code a} steigt (NaN: nie). */
        public synchronized double firstAbove(double a) { for (double[] s : series) if (s[1] > a) return s[0]; return Double.NaN; }
    }

    final List<Gauge> gauges = new ArrayList<>();
    double farX = Double.NaN;
    Far far;
    final List<double[]> farSeries = new ArrayList<>(), cavern = new ArrayList<>(), energy = new ArrayList<>();
    double runup = Double.NEGATIVE_INFINITY, runupX0 = Double.NaN;

    public Impact(double dx) {
        this.dx = dx;
        this.h = Math.sqrt(2) * dx;
    }

    // ------------------------------------------------------------------ Aufbau

    /** Grund als Linienzug (x steigend), Paare x0, y0, x1, y1, …; legt drei Lagen Wandpartikel darunter. */
    public synchronized Impact bed(double... xy) {
        int m = xy.length / 2;
        bedX = new double[m];
        bedY = new double[m];
        for (int i = 0; i < m; i++) { bedX[i] = xy[2 * i]; bedY[i] = xy[2 * i + 1]; }
        for (int i = 1; i < m; i++) if (!(bedX[i] > bedX[i - 1])) throw new IllegalArgumentException("x des Grundes steigt nicht");
        double total = 0;
        double[] acc = new double[m];
        for (int i = 1; i < m; i++) acc[i] = total += Math.hypot(bedX[i] - bedX[i - 1], bedY[i] - bedY[i - 1]);
        int k = 1;
        for (double s = 0.5 * dx; s < total; s += dx) {
            while (k < m - 1 && acc[k] < s) k++;
            double sx = bedX[k] - bedX[k - 1], sy = bedY[k] - bedY[k - 1], len = Math.hypot(sx, sy), f = (s - acc[k - 1]) / len;
            double x = bedX[k - 1] + f * sx, y = bedY[k - 1] + f * sy, tx = sx / len, ty = sy / len, nx = -ty, ny = tx;
            for (int l = 0; l < LAYERS; l++) {
                int i = add(WALL, x - nx * (l + 0.5) * dx, y - ny * (l + 0.5) * dx, 0, 0, waterDensity, waterDensity * dx * dx);
                prs[i] = 0;
            }
        }
        return this;
    }

    /** Höhe des Grundes bei x (NaN außerhalb). */
    public double bedAt(double x) {
        if (bedX.length < 2 || x < bedX[0] || x > bedX[bedX.length - 1]) return Double.NaN;
        int k = Arrays.binarySearch(bedX, x);
        if (k >= 0) return bedY[k];
        k = -k - 2;
        return bedY[k] + (x - bedX[k]) / (bedX[k + 1] - bedX[k]) * (bedY[k + 1] - bedY[k]);
    }

    /** Abstand zum Linienzug des Grundes. */
    public double bedDistance(double x, double y) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 1; i < bedX.length; i++) {
            double sx = bedX[i] - bedX[i - 1], sy = bedY[i] - bedY[i - 1], l2 = sx * sx + sy * sy;
            double f = Math.max(0, Math.min(1, ((x - bedX[i - 1]) * sx + (y - bedY[i - 1]) * sy) / l2));
            best = Math.min(best, Math.hypot(x - bedX[i - 1] - f * sx, y - bedY[i - 1] - f * sy));
        }
        return best;
    }

    /** Füllt Wasser bis zum Ruhespiegel {@code level} über dem Grund (hydrostatisch). */
    public Impact fill(double level) { return fill(level, x -> 0); }

    /** Füllt Wasser bis {@code level + init.eta(x)}, mit der Geschwindigkeit {@code init.u(x)}. */
    public synchronized Impact fill(double level, Init init) {
        this.level = level;
        double b = bValue(waterDensity);
        double ymin = Double.POSITIVE_INFINITY;
        for (double y : bedY) ymin = Math.min(ymin, y);
        int nxl = (int) Math.ceil((bedX[bedX.length - 1] - bedX[0]) / dx);
        for (int i = 0; i < nxl; i++) {
            double x = bedX[0] + (i + 0.5) * dx, top = level + init.eta(x), u = init.u(x);
            for (int j = 0; ; j++) {
                double y = top - (j + 0.5) * dx;
                if (y < ymin - dx) break;
                double bz = bedAt(x);
                if (Double.isNaN(bz) || y < bz || bedDistance(x, y) < 0.5 * dx * 0.999) continue;
                double p = waterDensity * gravity * (top - y), r = waterDensity * Math.pow(1 + p / b, 1 / gamma);
                add(WATER, x, y, u, 0, r, waterDensity * dx * dx);
            }
        }
        waterMass0 = waterMass();
        return this;
    }

    /** Schüttet Granulat in ein Gebiet über dem Grund (Druck aus der Auflast je Spalte). */
    public synchronized Impact grains(Region r) {
        double b = bValue(grain.density());
        double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, y0 = Double.POSITIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < bedX.length; i++) { x0 = Math.min(x0, bedX[i]); x1 = Math.max(x1, bedX[i]); y0 = Math.min(y0, bedY[i]); }
        y1 = y0 + 4000;
        int nxl = (int) Math.ceil((x1 - x0) / dx);
        for (int i = 0; i < nxl; i++) {
            double x = x0 + (i + 0.5) * dx, top = Double.NaN;
            List<Double> col = new ArrayList<>();
            for (double y = y0 + 0.5 * dx; y < y1; y += dx) {
                double bz = bedAt(x);
                if (Double.isNaN(bz) || y < bz || bedDistance(x, y) < 0.5 * dx * 0.999 || !r.inside(x, y)) continue;
                col.add(y);
                top = y + 0.5 * dx;
            }
            for (double y : col) {
                double p = grain.density() * gravity * (top - y), rr = grain.density() * Math.pow(1 + p / b, 1 / gamma);
                add(GRAIN, x, y, 0, 0, rr, grain.density() * dx * dx);
            }
        }
        return this;
    }

    /**
     * Zulauf: Der ganze Gleitkörper liegt zu Beginn als freies Granulat auf einer Rampe hangauf von der Uferlinie (Tor)
     * bei (gx, gy); der Hang fällt mit {@code slopeDeg} nach rechts ab. Er gleitet mit der mittleren Geschwindigkeit
     * V̄ des {@link Inflow} (nach Rate); seine Mächtigkeit an der Stelle σ hangauf ist s(t)·v(t)/V̄ mit t = σ/V̄, so geht
     * durch die Uferlinie genau das Volumen des Zuflusses. Auf der Rampe ist der Hangabtrieb aufgehoben und die Reibung
     * am Grund aus (wie der Schlitten im Versuch, der mit gleichbleibender Geschwindigkeit ankommt); ab der Uferlinie
     * gilt alles. Anders als ein geführter Puffer schiebt die Rampe nicht nach: Der Körper hat nur seinen eigenen Impuls.
     * Der Grund muss die Rampe tragen ({@link #rampLength()}).
     */
    public synchronized Impact feed(Inflow in, double gx, double gy, double slopeDeg) {
        double a = Math.toRadians(slopeDeg);
        inflow = in;
        gateX = gx;
        gateY = gy;
        tX = Math.cos(a);
        tY = -Math.sin(a);
        nX = Math.sin(a);
        nY = Math.cos(a);
        slopeSin = Math.sin(a);
        double vb = in.meanSpeed(), b = bValue(grain.density()), carry = 0;
        for (int j = 0; ; j++) {
            double sig = (j + 0.5) * dx, t = in.start() + sig / vb;
            if (t > in.end()) break;
            double sb = in.thickness(t) * in.speed(t) / vb, want = sb / dx + carry;
            int rows = (int) Math.floor(want + 0.5);
            carry = want - rows;
            for (int k = 0; k < rows; k++) {
                double d = (k + 0.5) * dx, x = gx - tX * sig + nX * d, y = gy - tY * sig + nY * d;
                double p = grain.density() * gravity * nY * (rows * dx - d), r = grain.density() * Math.pow(1 + p / b, 1 / gamma);
                int i = add(GRAIN, x, y, vb * tX, vb * tY, r, grain.density() * dx * dx);
                ramp[i] = true;
            }
        }
        return this;
    }

    /** Länge der Rampe hangauf, die {@link #feed} braucht (m). */
    public static double rampLength(Inflow in) { return in.meanSpeed() * (in.end() - in.start()); }

    /** Pegel am Ort x (Wasserstand über dem Ruhespiegel alle {@link #sampleDt}). */
    public synchronized Gauge gauge(String name, double x) { Gauge g = new Gauge(name, x); gauges.add(g); return g; }

    /** Rand für das Fernfeld am Schnitt x. */
    public synchronized Impact far(double x, Far f) { farX = x; far = f; return this; }

    /** Auflauf am Gegenhang: höchster Wasserstand am Grund rechts von x0. */
    public synchronized Impact runupFrom(double x0) { runupX0 = x0; return this; }

    int add(byte k, double x, double y, double u, double v, double r, double m) {
        if (n == px.length) grow(Math.max(1024, n * 2));
        px[n] = x; py[n] = y; vx[n] = u; vy[n] = v; rho[n] = r; mass[n] = m; kind[n] = k; prs[n] = 0;
        rest[n] = k == GRAIN || k == FEED ? m / (dx * dx) : waterDensity;
        pore[n] = k == GRAIN || k == FEED ? grain.porosity() * dx * dx : 0;
        open[n] = 0;
        return n++;
    }

    void grow(int cap) {
        px = Arrays.copyOf(px, cap); py = Arrays.copyOf(py, cap); vx = Arrays.copyOf(vx, cap); vy = Arrays.copyOf(vy, cap);
        rho = Arrays.copyOf(rho, cap); prs = Arrays.copyOf(prs, cap); mass = Arrays.copyOf(mass, cap); kind = Arrays.copyOf(kind, cap); ramp = Arrays.copyOf(ramp, cap); rest = Arrays.copyOf(rest, cap); pore = Arrays.copyOf(pore, cap); open = Arrays.copyOf(open, cap);
        ax = new double[cap]; ay = new double[cap]; dr = new double[cap]; shear = new double[cap]; eta = new double[cap];
        ux = new double[cap]; uy = new double[cap]; wx = new double[cap]; wy = new double[cap]; wrho = new double[cap]; u0x = new double[cap]; u0y = new double[cap]; load = new double[cap]; cellOf = new int[cap]; order = new int[cap];
    }

    // ------------------------------------------------------------------ Stoff

    double bValue(double r0) { return c0 * c0 * r0 / gamma; }

    double rho0(byte k) { return k == WATER ? waterDensity : grain.density(); }

    double pressureOf(byte k, double r) {
        double r0 = rho0(k);
        return Math.max(0, bValue(r0) * (Math.pow(r / r0, gamma) - 1));
    }

    /** Druck des Teilchens i aus seiner Dichte und seiner Ruhedichte. */
    double pressureAt(int i) {
        double r0 = rest[i];
        return Math.max(0, bValue(r0) * (Math.pow(rho[i] / r0, gamma) - 1));
    }

    // ------------------------------------------------------------------ Kern

    double kernel(double q) {
        if (q >= 2) return 0;
        double a = 1 - 0.5 * q;
        return 7 / (4 * Math.PI * h * h) * a * a * a * a * (2 * q + 1);
    }

    /** Kern W(r) (1/m²). */
    public double kernelAt(double r) { return kernel(r / h); }

    /** ∂W/∂r. */
    double dkernel(double q) {
        if (q >= 2) return 0;
        double a = 1 - 0.5 * q;
        return 7 / (4 * Math.PI * h * h) * (-5 * q * a * a * a) / h;
    }

    void grid() {
        double x0 = Double.POSITIVE_INFINITY, y0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            if (kind[i] == GONE) continue;
            x0 = Math.min(x0, px[i]); x1 = Math.max(x1, px[i]); y0 = Math.min(y0, py[i]); y1 = Math.max(y1, py[i]);
        }
        gcell = 2 * h;
        gx0 = x0 - gcell;
        gy0 = y0 - gcell;
        gnx = (int) ((x1 - gx0) / gcell) + 2;
        gny = (int) ((y1 - gy0) / gcell) + 2;
        int cells = gnx * gny;
        if (cellStart.length < cells + 1) cellStart = new int[cells + 1];
        else Arrays.fill(cellStart, 0, cells + 1, 0);
        for (int i = 0; i < n; i++) {
            if (kind[i] == GONE) { cellOf[i] = -1; continue; }
            int c = (int) ((py[i] - gy0) / gcell) * gnx + (int) ((px[i] - gx0) / gcell);
            cellOf[i] = c;
            cellStart[c + 1]++;
        }
        for (int c = 0; c < cells; c++) cellStart[c + 1] += cellStart[c];
        int[] fillp = Arrays.copyOf(cellStart, cells);
        for (int i = 0; i < n; i++) if (cellOf[i] >= 0) order[fillp[cellOf[i]]++] = i;
    }

    boolean fluid(byte k) { return k == WATER || k == GRAIN; }

    /** Ein Zeitschritt (s), der Schritt wird nach CFL gewählt. */
    public synchronized double step() {
        if (n == 0) return 0;
        double vmax = 0;
        for (int i = 0; i < n; i++) if (kind[i] != WALL && kind[i] != GONE) vmax = Math.max(vmax, Math.hypot(vx[i], vy[i]));
        double dt = Math.min(maxDt, cfl * h / (c0 + vmax));
        if (amax > 0) dt = Math.min(dt, 0.25 * Math.sqrt(h / amax));
        lastDt = dt;
        grid();
        // 1. Dichte mit den Geschwindigkeiten jetzt, 2. Druck aus der neuen Dichte, 3. Kräfte, Geschwindigkeit, Reibung,
        // Ort. So bleibt das Paar Dichte/Geschwindigkeit (Schall) stabil; umgekehrt wüchse jede Schwingung.
        rates(true);
        for (int i = 0; i < n; i++) {
            if (!fluid(kind[i])) continue;
            double r0 = rest[i];
            rho[i] = Math.max(0.8 * r0, Math.min(1.3 * r0, rho[i] + dr[i] * dt));
        }
        for (int i = 0; i < n; i++) if (kind[i] != WALL && kind[i] != GONE) prs[i] = pressureAt(i);
        wallPressure();
        rates(false);
        // auf der Rampe: Hangabtrieb nur aufgehoben, solange das Korn mindestens mit der Zuflussgeschwindigkeit fährt;
        // langsamer (gestaut) zieht ihn die Schwerkraft wieder hangab, damit nichts auf der Rampe liegen bleibt
        double vb = inflow == null ? 0 : inflow.meanSpeed();
        for (int i = 0; i < n; i++) if (ramp[i] && vx[i] * tX + vy[i] * tY >= vb) { ax[i] -= gravity * slopeSin * tX; ay[i] -= gravity * slopeSin * tY; }
        final double fdt = dt;
        IntStream.range(0, n).parallel().forEach(i -> {
            if (fluid(kind[i])) { ux[i] = vx[i] + ax[i] * fdt; uy[i] = vy[i] + ay[i] * fdt; } else { ux[i] = vx[i]; uy[i] = vy[i]; }
        });
        friction(dt);
        double mx = 0;
        for (int i = 0; i < n; i++) {
            if (!fluid(kind[i])) continue;
            vx[i] = ux[i];
            vy[i] = uy[i];
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            guard(i);
            mx = Math.max(mx, Math.hypot(ax[i], ay[i]));
            if (px[i] < boxX0 || px[i] > boxX1 || py[i] < boxY0 || py[i] > boxY1) {
                if (kind[i] == WATER) lostVolume += mass[i] / waterDensity;
                kind[i] = GONE;
            }
        }
        amax = mx;
        feedStep(dt);
        if (grain != null && grain.porosity() > 0) pores(dt);
        time += dt;
        steps++;
        if (time + 1e-12 >= nextSample) { sample(); nextSample += sampleDt; }
        return dt;
    }


    /**
     * Harter Schutz am Linienzug des Grundes: Ein Partikel näher als 0,3 dx (oder darunter) kommt auf diesen Abstand
     * zurück und verliert die Geschwindigkeit in den Grund hinein. Die Wandlagen allein lassen an einspringenden Ecken
     * Lücken.
     */
    void guard(int i) {
        double best = Double.POSITIVE_INFINITY, fx = 0, fy = 0, nx = 0, ny = 1, sd = 0;
        for (int k = 1; k < bedX.length; k++) {
            double sx = bedX[k] - bedX[k - 1], sy = bedY[k] - bedY[k - 1], l2 = sx * sx + sy * sy;
            double f = Math.max(0, Math.min(1, ((px[i] - bedX[k - 1]) * sx + (py[i] - bedY[k - 1]) * sy) / l2));
            double qx = bedX[k - 1] + f * sx, qy = bedY[k - 1] + f * sy, d = Math.hypot(px[i] - qx, py[i] - qy);
            if (d < best) {
                double l = Math.sqrt(l2), mx = -sy / l, my = sx / l;
                best = d; fx = qx; fy = qy; nx = mx; ny = my;
                sd = (px[i] - qx) * mx + (py[i] - qy) * my;
            }
        }
        if (best > 0.3 * dx && sd > 0) return;
        double bz = bedAt(px[i]);
        if (Double.isNaN(bz)) return;
        px[i] = fx + nx * 0.3 * dx;
        py[i] = fy + ny * 0.3 * dx;
        double vn = vx[i] * nx + vy[i] * ny;
        if (vn < 0) { vx[i] -= vn * nx; vy[i] -= vn * ny; }
    }

    /** Rechnet bis tEnd. */
    public void run(double tEnd) { while (time < tEnd) step(); }

    void wallPressure() {
        IntStream.range(0, n).parallel().forEach(w -> {
            if (kind[w] != WALL) return;
            double sw = 0, sp = 0, sr = 0, sh = 0;
            int cx = (int) ((px[w] - gx0) / gcell), cy = (int) ((py[w] - gy0) / gcell);
            for (int jy = Math.max(0, cy - 1); jy <= Math.min(gny - 1, cy + 1); jy++)
                for (int jx = Math.max(0, cx - 1); jx <= Math.min(gnx - 1, cx + 1); jx++) {
                    int c = jy * gnx + jx;
                    for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
                        int j = order[k];
                        byte kj = kind[j];
                        if (kj == WALL || kj == GONE) continue;
                        double ddx = px[w] - px[j], ddy = py[w] - py[j], r2 = ddx * ddx + ddy * ddy;
                        if (r2 >= 4 * h * h) continue;
                        double wk = kernel(Math.sqrt(r2) / h);
                        sw += wk;
                        sp += prs[j] * wk;
                        sh += rho[j] * gravity * (py[j] - py[w]) * wk;
                        sr += rho[j] * wk;
                    }
                }
            if (sw > 0) { prs[w] = Math.max(0, (sp + sh) / sw); wrho[w] = sr / sw; } else { prs[w] = 0; wrho[w] = waterDensity; }
        });
    }

    /** density: Kontinuität, δ und Scherrate (mit den Geschwindigkeiten); sonst Kräfte (mit dem Druck). */
    void rates(boolean density) {
        final double v0 = dx * dx, h2 = 4 * h * h, eps = 0.01 * h * h;
        IntStream.range(0, n).parallel().forEach(i -> {
            byte ki = kind[i];
            if (!fluid(ki)) { if (density) dr[i] = shear[i] = 0; else ax[i] = ay[i] = 0; return; }
            double ri = rho[i], pi = prs[i], mi = mass[i], vi = mi / ri, r0 = rest[i], bi = bValue(r0);
            double sax = 0, say = 0, sdr = 0, lxx = 0, lxy = 0, lyx = 0, lyy = 0;
            int cx = (int) ((px[i] - gx0) / gcell), cy = (int) ((py[i] - gy0) / gcell);
            for (int jy = Math.max(0, cy - 1); jy <= Math.min(gny - 1, cy + 1); jy++)
                for (int jx = Math.max(0, cx - 1); jx <= Math.min(gnx - 1, cx + 1); jx++) {
                    int c = jy * gnx + jx;
                    for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
                        int j = order[k];
                        if (j == i) continue;
                        byte kj = kind[j];
                        double ddx = px[i] - px[j], ddy = py[i] - py[j], r2 = ddx * ddx + ddy * ddy;
                        if (r2 >= h2 || r2 < 1e-12) continue;
                        double r = Math.sqrt(r2), dw = dkernel(r / h), ex = ddx / r, ey = ddy / r;
                        double rj, pj, vj, mj, uxj, uyj;
                        if (kj == WALL) { rj = wrho[j]; pj = prs[j]; vj = v0; mj = ri * v0; uxj = 0; uyj = 0; }
                        else { rj = rho[j]; pj = prs[j]; mj = mass[j]; vj = mj / rj; uxj = vx[j]; uyj = vy[j]; }
                        double dvx = vx[i] - uxj, dvy = vy[i] - uyj;
                        if (density) {
                        // Kontinuität
                        sdr += ri * vj * (dvx * ex + dvy * ey) * dw;
                        // δ-SPH in einer Phase
                        boolean same = kj == ki || (ki == GRAIN && kj == FEED);
                        if (same) {
                            double rh = r0 * (Math.pow(Math.max(1e-6, 1 + r0 * gravity * (py[i] - py[j]) / bi), 1 / gamma) - 1);
                            sdr += -2 * delta * h * c0 * ((rj / rest[j] - ri / r0) * r0 - rh) * dw * r / (r2 + eps) * vj;
                        }
                        if (ki == GRAIN) {
                            double gxw = dw * ex, gyw = dw * ey;
                            lxx += vj * (-dvx) * gxw; lxy += vj * (-dvx) * gyw; lyx += vj * (-dvy) * gxw; lyy += vj * (-dvy) * gyw;
                        }
                        continue;
                        }
                        // Druck (Hu & Adams)
                        double pt = (rj * pi + ri * pj) / (ri + rj), f = -(vi * vi + vj * vj) * pt * dw / mi;
                        sax += f * ex;
                        say += f * ey;
                        // künstliche Zähigkeit; zwischen Körnern und am Grund als unelastischer Stoß (grainAlpha)
                        boolean granular = ki == GRAIN && (kj == GRAIN || kj == FEED || kj == WALL);
                        if (kj != WALL || granular) {
                            double vr = dvx * ddx + dvy * ddy;
                            if (vr < 0) {
                                double al = granular ? grainAlpha : alpha;
                                double mu = h * vr / (r2 + eps), pij = -al * c0 * mu / (0.5 * (ri + rj));
                                sax += -mj * pij * dw * ex;
                                say += -mj * pij * dw * ey;
                            }
                        }
                    }
                }
            if (density) {
                dr[i] = sdr;
                if (ki == GRAIN) {
                    double dxy = 0.5 * (lxy + lyx);
                    shear[i] = Math.sqrt(0.5 * (lxx * lxx + lyy * lyy + 2 * dxy * dxy));
                } else shear[i] = 0;
            } else {
                ax[i] = sax;
                ay[i] = say - gravity;
            }
        });
    }

    /** Mohr-Coulomb im Granulat, implizit nach Jacobi auf ux, uy. */
    void friction(double dt) {
        if (grain == null) return;
        final double v0 = dx * dx, h2 = 4 * h * h, eps = 0.01 * h * h, tf = grain.tanPhi(), tb = grain.tanBed(), c = grain.cohesion(), emax = 1e13;
        boolean any = false;
        for (int i = 0; i < n && !any; i++) any = kind[i] == GRAIN;
        if (!any) return;
        overburden();
        IntStream.range(0, n).parallel().forEach(i -> {
            if (kind[i] != GRAIN && kind[i] != FEED) { eta[i] = 0; return; }
            eta[i] = Math.min(emax, (c + load[i] * tf) / (2 * shear[i] + frictionEps));
        });
        System.arraycopy(ux, 0, u0x, 0, n);
        System.arraycopy(uy, 0, u0y, 0, n);
        double[] srcx = u0x, srcy = u0y;
        for (int sweep = 0; sweep < frictionSweeps; sweep++) {
            final double[] sx = srcx, sy = srcy, ox = sweep % 2 == 0 ? wx : ux, oy = sweep % 2 == 0 ? wy : uy;
            IntStream.range(0, n).parallel().forEach(i -> {
                if (kind[i] != GRAIN) { ox[i] = sx[i]; oy[i] = sy[i]; return; }
                double ri = rho[i], ei = eta[i], eb = ei * (tf > 0 ? tb / tf : 1), sk = 0, su = 0, sv = 0;
                int cx = (int) ((px[i] - gx0) / gcell), cy = (int) ((py[i] - gy0) / gcell);
                for (int jy = Math.max(0, cy - 1); jy <= Math.min(gny - 1, cy + 1); jy++)
                    for (int jx = Math.max(0, cx - 1); jx <= Math.min(gnx - 1, cx + 1); jx++) {
                        int cc = jy * gnx + jx;
                        for (int k = cellStart[cc]; k < cellStart[cc + 1]; k++) {
                            int j = order[k];
                            if (j == i) continue;
                            byte kj = kind[j];
                            if (kj != GRAIN && kj != FEED && kj != WALL) continue;
                            double ddx = px[i] - px[j], ddy = py[i] - py[j], r2 = ddx * ddx + ddy * ddy;
                            if (r2 >= h2 || r2 < 1e-12) continue;
                            double r = Math.sqrt(r2), f = -dkernel(r / h) * r / (r2 + eps), kk;
                            if (kj == WALL && ramp[i]) continue;
                            if (kj == WALL) {
                                double ebj = eb;
                                if (dynamicBed) {
                                    double pw = prs[j];
                                    if (buoyantFriction && py[j] < level) pw = Math.max(0, pw - waterDensity * gravity * (level - py[j]));
                                    ebj = Math.min(emax, (c + pw * tb) / (2 * shear[i] + frictionEps));
                                }
                                kk = v0 * 2 * ebj / ri * f;
                            }
                            else {
                                double ej = kj == FEED ? ei : eta[j];
                                kk = mass[j] * (ei + ej) / (ri * rho[j]) * f;
                                su += kk * sx[j];
                                sv += kk * sy[j];
                            }
                            sk += kk;
                        }
                    }
                ox[i] = (u0x[i] + dt * su) / (1 + dt * sk);
                oy[i] = (u0y[i] + dt * sv) / (1 + dt * sk);
            });
            srcx = ox;
            srcy = oy;
        }
        if (srcx != ux) { System.arraycopy(srcx, 0, ux, 0, n); System.arraycopy(srcy, 0, uy, 0, n); }
    }


    /**
     * Druck im Korngerüst für die Reibung (Pa): der mittlere Druck aus dem SPH ohne den Wasserdruck, aber höchstens die
     * Auflast der zusammenhängenden Kornsäule darüber (Spalten zu 2 dx, unter dem Ruhespiegel mit Auftrieb). Der
     * Staudruck beim Aufprall trägt im gesättigten Korn das Porenwasser, nicht das Gerüst [G]; ohne die Grenze würde der
     * Körper beim Aufprall starr.
     */
    void overburden() {
        double cell = 2 * dx, x0 = gx0;
        int m = gnx * (int) Math.ceil(gcell / cell) + 2;
        int[] cnt = new int[m + 1];
        for (int i = 0; i < n; i++) if (kind[i] == GRAIN || kind[i] == FEED) { int c = Math.max(0, Math.min(m - 1, (int) ((px[i] - x0) / cell))); cnt[c + 1]++; }
        for (int c = 0; c < m; c++) cnt[c + 1] += cnt[c];
        int[] idx = new int[cnt[m]], fillp = Arrays.copyOf(cnt, m);
        for (int i = 0; i < n; i++) if (kind[i] == GRAIN || kind[i] == FEED) { int c = Math.max(0, Math.min(m - 1, (int) ((px[i] - x0) / cell))); idx[fillp[c]++] = i; }
        double rg = grain.density(), rw = buoyantFriction ? waterDensity : 0;
        IntStream.range(0, m).parallel().forEach(c -> {
            int a = cnt[c], b = cnt[c + 1];
            if (b <= a) return;
            Integer[] o = new Integer[b - a];
            for (int k = a; k < b; k++) o[k - a] = idx[k];
            Arrays.sort(o, (u, v) -> Double.compare(py[v], py[u]));
            double top = py[o[0]] + 0.5 * dx;
            double prev = py[o[0]];
            for (Integer ii : o) {
                int i = ii;
                if (prev - py[i] > 2.5 * dx) top = py[i] + 0.5 * dx;
                prev = py[i];
                double y = py[i], dry = Math.max(0, top - Math.max(y, level)), wet = Math.max(0, Math.min(top, level) - y);
                double lith = gravity * (rg * dry + (rg - rw) * wet), pe = prs[i];
                if (buoyantFriction && y < level) pe -= waterDensity * gravity * (level - y);
                load[i] = capLoad ? Math.min(lith, Math.max(0, pe)) : Math.max(0, pe);
            }
        });
    }

    // ------------------------------------------------------------------ Zulauf

    /** Wer auf der Rampe die Uferlinie überquert, ist frei; Masse, Volumen, Energie und Impuls werden gezählt. */
    void feedStep(double dt) {
        if (inflow == null) return;
        for (int i = 0; i < n; i++) {
            if (!ramp[i]) continue;
            if (kind[i] != GRAIN) { ramp[i] = false; continue; }
            double sigma = (px[i] - gateX) * tX + (py[i] - gateY) * tY;
            if (sigma >= 0) {
                ramp[i] = false;
                double m = mass[i], vv = vx[i] * vx[i] + vy[i] * vy[i];
                releasedMass += m;
                releasedVolume += m / grain.density();
                releasedEnergy += 0.5 * m * vv;
                releasedMomentum += m * Math.sqrt(vv);
            }
        }
    }


    /**
     * Poren füllen: Ein Wasserteilchen, das Körnern mit freiem Porenraum näher als 1,5 dx kommt, geht ganz in sie
     * über, verteilt nach freiem Raum, mit seinem Impuls; Dichte und Ruhedichte der Körner steigen um die Masse, ihr
     * Volumen bleibt. Der Raum, der je Schritt frei wird, ist mit {@link #poreTime} begrenzt. Der Reihe nach, damit zwei
     * Läufe gleich bleiben.
     */
    void pores(double dt) {
        grid();
        double r2max = 2.25 * dx * dx, fill = poreTime > 0 ? dt / poreTime : 1;
        // Porenraum öffnet sich mit der Zeit unter Wasser (unter dem Ruhespiegel oder neben Wasser)
        for (int j = 0; j < n; j++) if (kind[j] == GRAIN && !ramp[j] && py[j] < level) open[j] = Math.min(pore[j], open[j] + fill * grain.porosity() * dx * dx);
        int[] nb = new int[64];
        double[] cap = new double[64];
        for (int w = 0; w < n; w++) {
            if (kind[w] != WATER) continue;
            double vw = mass[w] / waterDensity, tot = 0;
            int m = 0;
            int cx = (int) ((px[w] - gx0) / gcell), cy = (int) ((py[w] - gy0) / gcell);
            for (int jy = Math.max(0, cy - 1); jy <= Math.min(gny - 1, cy + 1); jy++)
                for (int jx = Math.max(0, cx - 1); jx <= Math.min(gnx - 1, cx + 1); jx++) {
                    int c = jy * gnx + jx;
                    for (int k = cellStart[c]; k < cellStart[c + 1] && m < nb.length; k++) {
                        int j = order[k];
                        if (kind[j] != GRAIN || ramp[j] || open[j] <= 1e-12) continue;
                        double ddx = px[w] - px[j], ddy = py[w] - py[j];
                        if (ddx * ddx + ddy * ddy >= r2max) continue;
                        double cp = open[j];
                        nb[m] = j; cap[m] = cp; tot += cp; m++;
                    }
                }
            if (tot < vw) continue;
            double mw = mass[w];
            for (int k = 0; k < m; k++) {
                int j = nb[k];
                double share = cap[k] / tot, dm = mw * share, dv = vw * share, m0 = mass[j], m1 = m0 + dm;
                vx[j] = (m0 * vx[j] + dm * vx[w]) / m1;
                vy[j] = (m0 * vy[j] + dm * vy[w]) / m1;
                rho[j] *= m1 / m0;
                rest[j] *= m1 / m0;
                mass[j] = m1;
                pore[j] -= dv;
                open[j] -= dv;
            }
            poreMass += mw;
            kind[w] = GONE;
        }
    }

    /** Wasser, das in die Poren gegangen ist (kg je Meter). */
    public double poreMass() { return poreMass; }

    // ------------------------------------------------------------------ Messen

    void sample() {
        for (Gauge g : gauges) synchronized (g) { g.add(time, surface(g.x)); }
        if (far != null) {
            double e = surface(farX), q = 0;
            for (int i = 0; i < n; i++) if (kind[i] == WATER && Math.abs(px[i] - farX) < dx) q += vx[i] * mass[i] / rho[i];
            q /= 2 * dx;
            farSeries.add(new double[]{time, e, q});
            far.at(time, 0, farX, 0, Double.isNaN(e) ? 0 : e, q, 0);
        }
        cavern.add(new double[]{time, cavernArea()});
        double[] le = leadEnergy();
        energy.add(new double[]{time, le[0], le[1]});
        if (!Double.isNaN(runupX0))
            for (int i = 0; i < n; i++)
                if (kind[i] == WATER && px[i] > runupX0) {
                    double bz = bedAt(px[i]);
                    if (!Double.isNaN(bz) && py[i] - bz < 3 * dx) runup = Math.max(runup, py[i]);
                }
    }

    /** Oberer Rand der zusammenhängenden Säule aus Wasser und Granulat bei x über dem Ruhespiegel (NaN: leer); eingeschlossenes Wasser unter dem Schutt bricht die Säule so nicht ab. */
    public synchronized double surface(double x) {
        double[] ys = new double[64];
        int m = 0;
        for (int i = 0; i < n; i++)
            if ((kind[i] == WATER || kind[i] == GRAIN) && Math.abs(px[i] - x) < 0.6 * dx) { if (m == ys.length) ys = Arrays.copyOf(ys, m * 2); ys[m++] = py[i]; }
        if (m == 0) return Double.NaN;
        Arrays.sort(ys, 0, m);
        double top = ys[0];
        for (int k = 1; k < m && ys[k] - top <= 2.5 * dx; k++) top = ys[k];
        return top + 0.5 * dx - level;
    }

    /** Oberfläche über alle Spalten zu dx: {x0, Zellen …} (NaN: trocken). */
    public synchronized double[] profile(double x0, double x1) {
        int m = Math.max(0, (int) Math.ceil((x1 - x0) / dx));
        List<List<Double>> cols = new ArrayList<>();
        for (int c = 0; c < m; c++) cols.add(new ArrayList<>());
        for (int i = 0; i < n; i++) {
            if (kind[i] != WATER && kind[i] != GRAIN) continue;
            int c = (int) Math.floor((px[i] - x0) / dx);
            if (c >= 0 && c < m) cols.get(c).add(py[i]);
        }
        double[] out = new double[m];
        for (int c = 0; c < m; c++) {
            List<Double> l = cols.get(c);
            if (l.isEmpty()) { out[c] = Double.NaN; continue; }
            Collections.sort(l);
            double top = l.get(0);
            for (int k = 1; k < l.size() && l.get(k) - top <= 2.5 * dx; k++) top = l.get(k);
            out[c] = top + 0.5 * dx - level;
        }
        return out;
    }

    /**
     * Fläche der Kaverne (m² je Meter): Leere unter dem Ruhespiegel und über dem Grund zwischen cavernX0 und cavernX1,
     * über der in derselben Spalte zusammenhängendes Wasser oder Granulat steht (mindestens zwei volle Zellen zu 2 dx):
     * die Luft unter der Wasserwand und hinter dem Gleitkörper. Ein offenes Wellental zählt nicht.
     */
    public synchronized double cavernArea() {
        double cell = 2 * dx, top = level + 800;
        int mx = (int) Math.ceil((cavernX1 - cavernX0) / cell), my = (int) Math.ceil((top - level + 400) / cell);
        if (mx <= 0) return 0;
        boolean[] full = new boolean[mx * my];
        for (int i = 0; i < n; i++) {
            byte k = kind[i];
            if (k != WATER && k != GRAIN && k != FEED) continue;
            int cx = (int) Math.floor((px[i] - cavernX0) / cell), cy = (int) Math.floor((top - py[i]) / cell);
            if (cx >= 0 && cx < mx && cy >= 0 && cy < my) full[cy * mx + cx] = true;
        }
        int empty = 0, swl = (int) Math.floor((top - level) / cell);
        for (int cx = 0; cx < mx; cx++) {
            double xa = cavernX0 + cx * cell, xb = xa + cell, ba = bedAt(xa), bb = bedAt(xb);
            if (Double.isNaN(ba) || Double.isNaN(bb)) continue;
            boolean lid = false;
            for (int cy = 0; cy < my; cy++) {
                boolean f = full[cy * mx + cx];
                if (f && cy + 1 < my && full[(cy + 1) * mx + cx]) lid = true;
                if (cy < swl) continue;
                double yb = top - (cy + 1) * cell;
                if (yb < Math.max(ba, bb) + dx) break;
                if (!f && lid) empty++;
            }
        }
        return empty * cell * cell;
    }

    /**
     * Energie der führenden Welle ρ g ∫ η² dx (Lage und Bewegung zu gleichen Teilen) über den Bauch um den höchsten Punkt
     * rechts von energyFrom; einzelne leere Spalten (bis zwei) werden überbrückt: {E (J/m), x des Kamms}.
     */
    public synchronized double[] leadEnergy() {
        double x0 = energyFrom, x1 = bedX.length > 0 ? bedX[bedX.length - 1] : x0;
        double[] p = profile(x0, x1);
        for (int c = 1; c < p.length - 1; c++) {
            if (!Double.isNaN(p[c])) continue;
            int a = c - 1, b = c + 1;
            while (b < p.length && b - a <= 7 && Double.isNaN(p[b])) b++;
            if (b < p.length && b - a <= 7 && !Double.isNaN(p[a])) for (int k = a + 1; k < b; k++) p[k] = p[a] + (p[b] - p[a]) * (k - a) / (b - a);
        }
        int best = -1;
        double be = 0;
        for (int c = 2; c < p.length - 2; c++) {
            double sum = 0;
            int m = 0;
            for (int k = c - 2; k <= c + 2; k++) if (!Double.isNaN(p[k])) { sum += p[k]; m++; }
            if (m >= 3 && sum / m > be) { be = sum / m; best = c; }
        }
        if (best < 0) return new double[]{0, Double.NaN};
        double s = 0;
        for (int c = best; c < p.length && !Double.isNaN(p[c]) && p[c] > 0; c++) s += p[c] * p[c];
        for (int c = best - 1; c >= 0 && !Double.isNaN(p[c]) && p[c] > 0; c--) s += p[c] * p[c];
        return new double[]{waterDensity * gravity * s * dx, x0 + (best + 0.5) * dx};
    }

    // ------------------------------------------------------------------ Abfragen

    public double time() { return time; }

    public long steps() { return steps; }

    public double lastDt() { return lastDt; }

    public int count(byte k) { int c = 0; for (int i = 0; i < n; i++) if (kind[i] == k) c++; return c; }

    public synchronized double waterMass() { double s = 0; for (int i = 0; i < n; i++) if (kind[i] == WATER) s += mass[i]; return s; }

    /** Volumen des Wassers Σ m/ρ (m² je Meter). */
    public synchronized double waterVolume() { double s = 0; for (int i = 0; i < n; i++) if (kind[i] == WATER) s += mass[i] / rho[i]; return s; }

    public synchronized double grainMass() { double s = 0; for (int i = 0; i < n; i++) if (kind[i] == GRAIN) s += mass[i]; return s; }

    /** Masse, Volumen (bei Schüttdichte), Bewegungsenergie und Impuls dessen, was durch die Uferlinie kam. */
    public double releasedMass() { return releasedMass; }

    public double releasedVolume() { return releasedVolume; }

    public double releasedEnergy() { return releasedEnergy; }

    public double releasedMomentum() { return releasedMomentum; }

    public double lostVolume() { return lostVolume; }

    public double initialWaterMass() { return waterMass0; }

    public List<Gauge> gauges() { return Collections.unmodifiableList(gauges); }

    /** Am Rand gemessen: {t, η, q}. */
    public synchronized List<double[]> farSeries() { return new ArrayList<>(farSeries); }

    /** Kamm zwischen x0 und x1 im über fünf Spalten geglätteten Profil: {x, η}. */
    public synchronized double[] crest(double x0, double x1) {
        double[] p = profile(x0, x1);
        double bx = Double.NaN, be = Double.NEGATIVE_INFINITY;
        for (int c = 2; c < p.length - 2; c++) {
            double sum = 0;
            int m = 0;
            for (int k = c - 2; k <= c + 2; k++) if (!Double.isNaN(p[k])) { sum += p[k]; m++; }
            if (m >= 3 && sum / m > be) { be = sum / m; bx = x0 + (c + 0.5) * dx; }
        }
        return new double[]{bx, be};
    }

    /**
     * Fläche der Kaverne: {t, m²}. */
    public synchronized List<double[]> cavernSeries() { return new ArrayList<>(cavern); }

    /** Energie der führenden Welle: {t, J/m, x des Kamms}. */
    public synchronized List<double[]> energySeries() { return new ArrayList<>(energy); }

    /** Höchster Auflauf am Gegenhang (m über dem Ruhespiegel). */
    public double runup() { return runup - level; }

    /** Größte Geschwindigkeit des Granulats und des Wassers jetzt (m/s). */
    public synchronized double[] maxSpeeds() {
        double g = 0, w = 0;
        for (int i = 0; i < n; i++) {
            double s = Math.hypot(vx[i], vy[i]);
            if (kind[i] == GRAIN) g = Math.max(g, s);
            else if (kind[i] == WATER) w = Math.max(w, s);
        }
        return new double[]{g, w};
    }

    /** Geschwindigkeit, unter der der Anteil q der Partikel einer Art liegt (m/s). */
    public synchronized double speedQuantile(byte k, double q) {
        double[] v = new double[n];
        int m = 0;
        for (int i = 0; i < n; i++) if (kind[i] == k) v[m++] = Math.hypot(vx[i], vy[i]);
        if (m == 0) return 0;
        Arrays.sort(v, 0, m);
        return v[Math.min(m - 1, (int) (q * m))];
    }

    /** Druck in der obersten Wandlage am Grund bei x (Pa): Mittel der Wandpartikel innerhalb dx um (x, Grund − dx/2). */
    public synchronized double bedPressure(double x) {
        double yb = bedAt(x) - 0.5 * dx, sum = 0;
        int c = 0;
        for (int i = 0; i < n; i++) if (kind[i] == WALL && Math.hypot(px[i] - x, py[i] - yb) < dx) { sum += prs[i]; c++; }
        return c == 0 ? Double.NaN : sum / c;
    }

    /** Ein Augenblick für die Grafik. */
    public record Frame(double t, float[] x, float[] y, float[] speed, byte[] kind, double[] profileX, double[] profile) { }

    public synchronized Frame frame() {
        int m = 0;
        for (int i = 0; i < n; i++) if (kind[i] != WALL && kind[i] != GONE) m++;
        float[] x = new float[m], y = new float[m], s = new float[m];
        byte[] k = new byte[m];
        int c = 0;
        for (int i = 0; i < n; i++) {
            if (kind[i] == WALL || kind[i] == GONE) continue;
            x[c] = (float) px[i]; y[c] = (float) py[i]; s[c] = (float) Math.hypot(vx[i], vy[i]); k[c] = kind[i]; c++;
        }
        double x0 = bedX.length > 0 ? bedX[0] : 0, x1 = bedX.length > 0 ? bedX[bedX.length - 1] : 0;
        return new Frame(time, x, y, s, k, new double[]{x0, x1}, profile(x0, x1));
    }

    /** Linienzug des Grundes (Kopie). */
    public double[][] bedLine() { return new double[][]{bedX.clone(), bedY.clone()}; }

    public Inflow inflow() { return inflow; }

    public String status() {
        double[] ms = maxSpeeds();
        return String.format(Locale.GERMAN, "t = %.2f s · %d Schritte (Δt %.2f ms) · Wasser %d, Granulat %d, Puffer %d, Wand %d · Granulat bis %.0f m/s, Wasser bis %.0f m/s",
                time, steps, lastDt * 1000, count(WATER), count(GRAIN), count(FEED), count(WALL), ms[0], ms[1]);
    }
}
