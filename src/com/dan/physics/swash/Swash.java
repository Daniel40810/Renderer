package com.dan.physics.swash;

import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * Auflauf und Rücklauf als dünner Wasserfilm (Seegang Werkbuch, S6a; SF52 A): die Flachwassergleichungen auf einem
 * Rechteck am Ufer, als Referenz in doppelter Genauigkeit (die Karte folgt mit S6b),
 * <pre>
 *   ∂h/∂t + ∂(hu)/∂x + ∂(hv)/∂y = −i
 *   ∂(hu)/∂t + ∂(hu² + ½ g h²)/∂x + ∂(huv)/∂y = −g h ∂b/∂x − g n² u |u| / h^(1/3)
 *   ∂(hv)/∂t + ∂(huv)/∂x + ∂(hv² + ½ g h²)/∂y = −g h ∂b/∂y − g n² v |u| / h^(1/3)
 * </pre>
 * Der Kern ist aus {@code wave.BayGrid} übernommen (SF3 A, Kopie; das Element-Package bleibt unverändert): finite Volumen
 * mit den Werten in der Zellmitte, Flüsse nach HLL (stoßerfassend), zweite Ordnung mit MUSCL (minmod) und dem Zeitschritt
 * nach Heun. Der Grund geht über die hydrostatische Rekonstruktion nach Audusse u. a. (2004) ein: Ein ruhender See über
 * welligem Grund bleibt in Ruhe, Ufer werden ohne Sonderfälle nass und trocken, die Tiefe bleibt nicht negativ.
 * <p>
 * <b>Neu gegenüber Bay:</b>
 * <ul>
 * <li><b>Reibung</b> nach Manning je Zelle ({@link #n}, aus der Rauheit des Geländes, {@link Boden#manning}), halb
 * implizit.</li>
 * <li><b>Versickern</b> nach Darcy in der Form von Green und Ampt (1911): Unter einem Wasserfilm der Tiefe h dringt eine
 * gesättigte Front in den Sand, i = K (1 + (h + ψ) Δθ / F), F das versickerte Wasser ({@link #sick}, m), K die
 * Durchlässigkeit je Zelle ({@link #kf}, aus d50, {@link Boden#durchlaessigkeit}; im Schlick fast null), ψ die Saugspannung
 * an der Front und Δθ der freie Porenanteil [G, Rawls u. a. 1983 für Sand]. Je Schritt wird das Integral bei festem h
 * genau gelöst. Erreicht die Front das Grundwasser ({@link #gw}), ist der Sand gesättigt und nimmt nichts mehr; unter dem
 * Grundwasser versickert nichts. Ist die Zelle trocken, zieht das Wasser mit der Rate K unter der Schwere nach unten ab
 * [G]: der Sand oben trocknet, die nächste Welle versickert wieder schneller.</li>
 * <li><b>Antrieb</b> an einer Seite ({@link Rand#ANTRIEB}): die einlaufende Welle (Stand und Geschwindigkeit, etwa aus der
 * Synthese, SF54 A) über die einlaufende Riemann-Invariante u + 2√(g h), die auslaufende aus dem Innern: Was
 * zurückläuft, verlässt das Gitter ohne Spiegelung (für lange, flache Wellen genau).</li>
 * <li><b>Lage:</b> lokale Achsen x, y, gegen die Welt um {@link #winkel} gedreht (das Rechteck längs der Uferlinie, SF53 A).</li>
 * <li><b>Spuren</b> für das Bild (S6c): höchster Stand je Zelle ({@link #etaMax}, Spülsaum) und wann sie zuletzt nass war
 * ({@link #nassZeit}).</li>
 * </ul>
 * Parallel über die Zeilen; jede Zelle und jede Kante wird für sich gerechnet: zwei Läufe sind gleich.
 * <p>
 * Kennt nur {@code java.*} (Schichtenvertrag, Werkbuch HalligHooge Teil A, Abschnitt 4).
 */
public final class Swash {
    public static final String VERSION = "0.3";

    /** Art einer Seite. */
    public enum Rand { WAND, OFFEN, ANTRIEB }

    /** Seiten: W bei x = 0, E bei x = nx·dx, S bei y = 0, N bei y = ny·dx. */
    public static final int W = 0, E = 1, S = 2, N = 3;
    /** Lagen der Randzellen je Seite. */
    public static final int G = 2;

    /** Ein Feld über den lokalen Koordinaten (Zellmitten), etwa der Grund (m über dem Bezug; NaN: Fels). */
    @FunctionalInterface public interface Feld { double wert(double x, double y); }

    /** Anfangszustand: Stand (m über dem Bezug, unter dem Grund: trocken) und Geschwindigkeit {u, v} (m/s, null: Ruhe). */
    public interface Anfang {
        double stand(double x, double y);

        default double[] u(double x, double y) { return null; }
    }

    /**
     * Die einlaufende Welle an einer Seite mit {@link Rand#ANTRIEB}: am Ort (x, y, lokal) zur Zeit t nach {@code aus}
     * {Stand über dem Bezug (m), Geschwindigkeit senkrecht zur Seite ins Gitter (m/s), Geschwindigkeit längs der Seite in
     * Richtung von +x bzw. +y (m/s)}. Gerufen für die Randzellen, je Stufe des Schritts.
     */
    @FunctionalInterface public interface Antrieb { void welle(double t, double x, double y, double[] aus); }

    public final int nx, ny, px;
    /** Zellgröße (m), Ursprung der lokalen Achsen in der Welt (m) und Drehung der lokalen x-Achse gegen die Welt (rad, gegen den Uhrzeigersinn). */
    public final double dx, x0, y0, winkel;
    final double cw, sw;

    /**
     * Schwere (m/s²); Zahl nach Courant für den Zeitschritt: seit 0.2 (Seegang Werkbuch 69/70, SF65 A) 0,4 statt 0,3. Die
     * Prüfsteine aus S6a halten damit dieselben Werte bis auf die vierte Stelle. Gewählt war 0,45; damit lief die Karte mit
     * dem Zeitschritt der Referenz bei Carrier und Greenspan nach 66 s über (eine dünne Zelle am zurückweichenden Ufer,
     * h 4e-5 m knapp über {@link #hGeschw}, bekam in float 50 m/s, in der Referenz mit h 1e-5 m keine Geschwindigkeit).
     */
    public double schwere = 9.81, cfl = 0.4;
    /**
     * Unter hTrocken gilt eine Zelle als trocken, unter hGeschw hat sie keine Geschwindigkeit (m) [G]. Bay nimmt 1 mm (für
     * float auf der Karte); am Ufer bliebe damit nach jedem Rücklauf ein unbeweglicher Film knapp unter 1 mm auf dem Hang
     * liegen, und die nächste Zunge liefe auf nassen Grund (S6a, Teil 5: der Auflauf sank von Periode zu Periode um 13 %).
     * In double darum 10 µm.
     */
    public double hTrocken = 1e-6, hGeschw = 1e-5;
    /** Höchste Geschwindigkeit (m/s) [G: weit über allem am Ufer, schützt dünne Filme] und kleinste für den Zeitschritt. */
    public double vMax = 50, vMin = 1;
    /** Ab dieser Tiefe zählt eine Zelle für die Spuren als nass (m) [G: ein Laufdraht misst 1–2 cm über dem Sand, die Spur des Films ist feiner]. */
    public double hSpitze = 1e-3;
    /** Seit 0.3 (SF74 A): Front im Teilgitter ({@link #kanten}); aus nur zum Vergleich in Prüfungen. */
    public boolean teilgitter = true;
    /** Saugspannung an der Front ψ (m) und freier Porenanteil Δθ [G: Sand nach Rawls, Brakensiek und Miller 1983: ψ 4,95 cm, θ_e 0,417; Sand am Ufer zu rund 30 % gefüllt]. */
    public double psi = Boden.PSI_SAND, dTheta = Boden.DTHETA_SAND;
    /** Ruhewasser (m über dem Bezug): Bezug für {@link #auflauf} und den Antrieb. */
    public double bezug;

    final Rand[] rand = {Rand.WAND, Rand.WAND, Rand.WAND, Rand.WAND};
    final Antrieb[] antrieb = new Antrieb[4];

    // Zustand (mit Randzellen)
    /** Grund (m über dem Bezug; NaN: Fels), Tiefe (m), Durchfluss (m²/s) je Zelle samt Rand; Index {@link #id}. */
    public final double[] b, h, hu, hv;
    /** Manning-Beiwert (s/m^(1/3)), Durchlässigkeit (m/s), Höhe des Grundwassers (m über dem Bezug), versickertes Wasser oberhalb der Front (m). */
    public final double[] n, kf, gw, sick;
    /** Höchster Stand je Zelle, seit sie nass war (m über dem Bezug; NaN: nie nass), und wann sie zuletzt nass war (s; −∞: nie). */
    public final double[] etaMax, nassZeit;
    final boolean[] trockenAnfang;
    // Arbeit
    final double[] h0, hu0, hv0, dh, dhu, dhv;
    final double[] sH, sE, sU, sV, tH, tE, tU, tV;
    /** Seit 0.3 (SF74 A): Tiefe und Stand an den Kanten je Richtung (W, E bzw. S, N) und die Tiefe für den Quellterm. */
    final double[] xHw, xHe, xEw, xEe, yHs, yHn, yEs, yEn, xQ, yQ;
    /** Seit 0.3: Steigung des Grunds je Richtung (minmod), Spiegel je Zelle (nass: η, an der Front im Keil w, trocken: Grund), Front. */
    final double[] gX, gY, spiegel;
    final boolean[] front;
    final double[] fx, fy, pxL, pxR, pyL, pyR, mfx, mfy;
    final double[] zeileA, zeileB, theta;

    double zeit, zufluss, versickert, abgeschnitten;
    long schritte, ns;
    boolean gefuellt;

    /** Ein Rechteck aus nx × ny Zellen zu dx m, Ursprung (x0, y0) in der Welt, um winkel gedreht. */
    public Swash(int nx, int ny, double dx, double x0, double y0, double winkel) {
        if (nx < 2 || ny < 1 || !(dx > 0)) throw new IllegalArgumentException("Gitter");
        this.nx = nx;
        this.ny = ny;
        this.dx = dx;
        this.x0 = x0;
        this.y0 = y0;
        this.winkel = winkel;
        cw = Math.cos(winkel);
        sw = Math.sin(winkel);
        px = nx + 2 * G;
        int m = px * (ny + 2 * G);
        b = new double[m]; h = new double[m]; hu = new double[m]; hv = new double[m];
        n = new double[m]; kf = new double[m]; gw = new double[m]; sick = new double[m];
        Arrays.fill(n, 0.0);
        Arrays.fill(gw, Double.NEGATIVE_INFINITY);
        etaMax = new double[m];
        nassZeit = new double[m];
        Arrays.fill(etaMax, Double.NaN);
        Arrays.fill(nassZeit, Double.NEGATIVE_INFINITY);
        trockenAnfang = new boolean[m];
        h0 = new double[m]; hu0 = new double[m]; hv0 = new double[m];
        dh = new double[m]; dhu = new double[m]; dhv = new double[m];
        sH = new double[m]; sE = new double[m]; sU = new double[m]; sV = new double[m];
        tH = new double[m]; tE = new double[m]; tU = new double[m]; tV = new double[m];
        xHw = new double[m]; xHe = new double[m]; xEw = new double[m]; xEe = new double[m];
        yHs = new double[m]; yHn = new double[m]; yEs = new double[m]; yEn = new double[m];
        xQ = new double[m]; yQ = new double[m];
        gX = new double[m]; gY = new double[m]; spiegel = new double[m]; front = new boolean[m];
        fx = new double[3 * (nx + 1) * ny]; fy = new double[3 * nx * (ny + 1)];
        pxL = new double[(nx + 1) * ny]; pxR = new double[(nx + 1) * ny]; pyL = new double[nx * (ny + 1)]; pyR = new double[nx * (ny + 1)];
        mfx = new double[(nx + 1) * ny]; mfy = new double[nx * (ny + 1)];
        theta = new double[m];
        zeileA = new double[ny];
        zeileB = new double[ny];
    }

    // ------------------------------------------------------------------ Lage

    /** Index der Zelle (i, j), Randzellen ab −{@value #G}. */
    public int id(int i, int j) { return (j + G) * px + i + G; }

    /** Lokale Mitte der Zelle. */
    public double x(int i) { return (i + 0.5) * dx; }

    public double y(int j) { return (j + 0.5) * dx; }

    /** Lokal nach Welt. */
    public double weltX(double x, double y) { return x0 + cw * x - sw * y; }

    public double weltY(double x, double y) { return y0 + sw * x + cw * y; }

    /** Welt nach lokal. */
    public double lokalX(double wx, double wy) { return cw * (wx - x0) + sw * (wy - y0); }

    public double lokalY(double wx, double wy) { return -sw * (wx - x0) + cw * (wy - y0); }

    public boolean fels(int c) { return Double.isNaN(b[c]); }

    // ------------------------------------------------------------------ Aufbau

    /** Grund für alle Zellen samt Rand (m über dem Bezug; NaN: Fels, Wand). */
    public Swash grund(Feld f) { setze(b, f); return this; }

    /** Manning-Beiwert je Zelle (s/m^(1/3)); 0 ohne Reibung (Vorgabe). */
    public Swash rauheit(Feld f) { setze(n, f); return this; }

    /** Durchlässigkeit je Zelle (m/s); 0 ohne Versickern (Vorgabe). */
    public Swash durchlaessigkeit(Feld f) { setze(kf, f); return this; }

    /** Höhe des Grundwassers je Zelle (m über dem Bezug); darunter versickert nichts. Vorgabe −∞. */
    public Swash grundwasser(Feld f) { setze(gw, f); return this; }

    void setze(double[] a, Feld f) {
        for (int j = -G; j < ny + G; j++) for (int i = -G; i < nx + G; i++) a[id(i, j)] = f.wert(x(i), y(j));
    }

    public Swash rand(int seite, Rand r) { rand[seite] = r; return this; }

    public Rand rand(int seite) { return rand[seite]; }

    /** Der Antrieb einer Seite (null: keiner). */
    public Antrieb antrieb(int seite) { return antrieb[seite]; }

    /** Antrieb an einer Seite (setzt sie auf {@link Rand#ANTRIEB}). */
    public Swash antrieb(int seite, Antrieb a) { rand[seite] = Rand.ANTRIEB; antrieb[seite] = a; return this; }

    /** Wasser nach dem Anfangszustand (null: bis zum Ruhewasser), Ruhewasser {@code bezug}; setzt Zeit und Spuren zurück. */
    /**
     * Seit 0.3 (SF74 A): Tiefe der Zelle (i, j) für den Spiegel e in Ruhe. Mit {@link #teilgitter} das Volumen über der
     * Ebene des Grunds (Umfang S = |gX| + |gY|, wie {@link #spiegelZelle}): ganz bedeckt e − b, im Keil (e − b + S/2)² / (2S);
     * sonst e − b.
     */
    double tiefeInRuhe(int i, int j, double e) {
        int c = id(i, j);
        double bc = b[c];
        if (!teilgitter) return Math.max(0, e - bc);
        double sg = Math.abs(steigungGrund(i, j, 1, 0)) + Math.abs(steigungGrund(i, j, 0, 1));
        if (!(sg > 0) || e >= bc + 0.5 * sg) return Math.max(0, e - bc);
        double t = e - (bc - 0.5 * sg);
        return t > 0 ? t * t / (2 * sg) : 0;
    }

    public Swash fuellen(double bezug, Anfang a) {
        this.bezug = bezug;
        grundRand();
        for (int j = -G; j < ny + G; j++)
            for (int i = -G; i < nx + G; i++) {
                int c = id(i, j);
                sick[c] = 0;
                etaMax[c] = Double.NaN;
                nassZeit[c] = Double.NEGATIVE_INFINITY;
                if (fels(c)) { h[c] = hu[c] = hv[c] = 0; trockenAnfang[c] = false; continue; }
                double e = a == null ? bezug : a.stand(x(i), y(j));
                h[c] = tiefeInRuhe(i, j, e);
                trockenAnfang[c] = bezug - b[c] <= 0;
                double[] u = a == null ? null : a.u(x(i), y(j));
                hu[c] = h[c] > hGeschw && u != null ? h[c] * u[0] : 0;
                hv[c] = h[c] > hGeschw && u != null ? h[c] * u[1] : 0;
            }
        zeit = zufluss = versickert = abgeschnitten = 0;
        schritte = ns = 0;
        gefuellt = true;
        spuren();
        return this;
    }

    // ------------------------------------------------------------------ Ränder

    /**
     * Seit 0.3: der Grund der Randzellen nach der Regel von {@link #raender} (Wand und offen: die gespiegelte Zelle, Antrieb:
     * die erste Zelle innen; die Ecken nach S und N), schon vor dem Füllen: Die Tiefe in Ruhe hängt im Teilgitter von der
     * Steigung des Grunds ab, auch an der letzten Zelle vor dem Rand.
     */
    void grundRand() {
        for (int seite = 0; seite < 4; seite++) {
            boolean xs = seite == W || seite == E, an = rand[seite] == Rand.ANTRIEB && antrieb[seite] != null;
            int m = xs ? ny : nx;
            for (int k = xs ? 0 : -G; k < (xs ? m : m + G); k++)
                for (int l = 1; l <= G; l++) {
                    int gi, gj, ii, jj, ai, aj;
                    if (seite == W) { gi = -l; gj = k; ii = l - 1; jj = k; ai = 0; aj = k; }
                    else if (seite == E) { gi = nx - 1 + l; gj = k; ii = nx - l; jj = k; ai = nx - 1; aj = k; }
                    else if (seite == S) { gi = k; gj = -l; ii = k; jj = l - 1; ai = k; aj = 0; }
                    else { gi = k; gj = ny - 1 + l; ii = k; jj = ny - l; ai = k; aj = ny - 1; }
                    ii = Math.max(0, Math.min(nx - 1, ii)); jj = Math.max(0, Math.min(ny - 1, jj));
                    ai = Math.max(0, Math.min(nx - 1, ai)); aj = Math.max(0, Math.min(ny - 1, aj));
                    b[id(gi, gj)] = an ? b[id(ai, aj)] : b[id(ii, jj)];
                }
        }
    }

    /** Randzellen aller Seiten zur Zeit t. */
    void raender(double t) {
        double[] w = new double[3];
        for (int seite = 0; seite < 4; seite++) {
            Rand r = rand[seite];
            boolean xs = seite == W || seite == E;
            int m = xs ? ny : nx;
            for (int k = -G; k < m + G; k++) {
                for (int l = 1; l <= G; l++) {
                    int gi, gj, ii, jj, ai, aj;   // Randzelle, gespiegelte Zelle innen, erste Zelle innen
                    if (seite == W) { gi = -l; gj = k; ii = l - 1; jj = k; ai = 0; aj = k; }
                    else if (seite == E) { gi = nx - 1 + l; gj = k; ii = nx - l; jj = k; ai = nx - 1; aj = k; }
                    else if (seite == S) { gi = k; gj = -l; ii = k; jj = l - 1; ai = k; aj = 0; }
                    else { gi = k; gj = ny - 1 + l; ii = k; jj = ny - l; ai = k; aj = ny - 1; }
                    if (xs && (k < 0 || k >= ny) || !xs && (k < 0 || k >= nx)) {
                        ii = Math.max(0, Math.min(nx - 1, ii));
                        jj = Math.max(0, Math.min(ny - 1, jj));
                        ai = Math.max(0, Math.min(nx - 1, ai));
                        aj = Math.max(0, Math.min(ny - 1, aj));
                    }
                    int g = id(gi, gj), c = id(ii, jj);
                    if (r == Rand.ANTRIEB && antrieb[seite] != null) { antrieb(seite, t, g, id(ai, aj), gi, gj, w); continue; }
                    // Wand und offen: Grund und Tiefe der gespiegelten Zelle, so bleibt ein ruhender See in Ruhe
                    b[g] = b[c];
                    if (fels(g)) { h[g] = hu[g] = hv[g] = 0; continue; }
                    h[g] = h[c];
                    hu[g] = hu[c];
                    hv[g] = hv[c];
                    if (r == Rand.WAND) { if (xs) hu[g] = -hu[g]; else hv[g] = -hv[g]; }
                    if (h[g] <= hTrocken) { hu[g] = hv[g] = 0; }
                }
            }
        }
    }

    /**
     * Randzelle g einer Seite mit Antrieb: Grund wie die erste Zelle innen a (ein ruhender See bleibt in Ruhe), einlaufende
     * Invariante R⁺ = u_e + 2√(g h_e) aus der Welle, auslaufende R⁻ = u_a − 2√(g h_a) aus dem Innern; daraus c = (R⁺ − R⁻)/4,
     * u = (R⁺ + R⁻)/2. Längs der Seite: einströmend die Welle, ausströmend das Innere. Ist das Innere trocken, gilt die Welle.
     */
    void antrieb(int seite, double t, int g, int a, int gi, int gj, double[] w) {
        b[g] = b[a];
        if (fels(g)) { h[g] = hu[g] = hv[g] = 0; return; }
        antrieb[seite].welle(t, x(gi), y(gj), w);
        double he = Math.max(0, w[0] - b[g]), ue = he > hGeschw ? w[1] : 0, te = he > hGeschw ? w[2] : 0;
        double sign = seite == W || seite == S ? 1 : -1;      // ins Gitter: +x an W, −x an E, +y an S, −y an N
        boolean xs = seite == W || seite == E;
        double ha = h[a], qa = xs ? hu[a] : hv[a], ta = xs ? hv[a] : hu[a];
        double ua = vel(qa, ha) * sign, va = vel(ta, ha);
        double hg, ug, tg;
        if (ha <= hTrocken) { hg = he; ug = ue; tg = te; }
        else {
            double rp = ue + 2 * Math.sqrt(schwere * he), rm = ua - 2 * Math.sqrt(schwere * ha);
            double c = 0.25 * (rp - rm);
            ug = 0.5 * (rp + rm);
            hg = c > 0 ? c * c / schwere : 0;
            tg = ug > 0 ? te : va;
        }
        h[g] = hg;
        double qn = hg > hGeschw ? hg * ug * sign : 0, qt = hg > hGeschw ? hg * tg : 0;
        if (xs) { hu[g] = qn; hv[g] = qt; } else { hv[g] = qn; hu[g] = qt; }
    }

    // ------------------------------------------------------------------ Rechnung

    static double minmod(double a, double b) { return a * b <= 0 ? 0 : (Math.abs(a) < Math.abs(b) ? a : b); }

    double vel(double q, double hh) { return hh > hGeschw ? q / hh : 0; }

    void zeilen(int von, int bis, IntConsumer f) {
        if (bis - von >= 32) IntStream.range(von, bis).parallel().forEach(f);
        else for (int j = von; j < bis; j++) f.accept(j);
    }

    /** Steigungen (minmod) für die Zellen −1 … n in x (s) und y (t). */
    void steigungen() {
        zeilen(-G, ny + G, j -> { for (int i = -G; i < nx + G; i++) spiegelZelle(i, j); });
        zeilen(-1, ny + 1, j -> {
            for (int i = -1; i <= nx; i++) {
                int c = id(i, j);
                if (fels(c)) { sH[c] = sE[c] = sU[c] = sV[c] = tH[c] = tE[c] = tU[c] = tV[c] = 0; continue; }
                int l = c - 1, r = c + 1, d = c - px, u = c + px;
                double hc = h[c], ec = hc + b[c], uc = vel(hu[c], hc), vc = vel(hv[c], hc);
                if (fels(l) || fels(r)) { sH[c] = sE[c] = sU[c] = sV[c] = 0; }
                else {
                    sH[c] = minmod(hc - h[l], h[r] - hc);
                    sE[c] = minmod(ec - h[l] - b[l], h[r] + b[r] - ec);
                    sU[c] = minmod(uc - vel(hu[l], h[l]), vel(hu[r], h[r]) - uc);
                    sV[c] = minmod(vc - vel(hv[l], h[l]), vel(hv[r], h[r]) - vc);
                }
                if (fels(d) || fels(u)) { tH[c] = tE[c] = tU[c] = tV[c] = 0; }
                else {
                    tH[c] = minmod(hc - h[d], h[u] - hc);
                    tE[c] = minmod(ec - h[d] - b[d], h[u] + b[u] - ec);
                    tU[c] = minmod(uc - vel(hu[d], h[d]), vel(hu[u], h[u]) - uc);
                    tV[c] = minmod(vc - vel(hv[d], h[d]), vel(hv[u], h[u]) - vc);
                }
                if (hc <= hGeschw) { sU[c] = sV[c] = tU[c] = tV[c] = 0; }
                kanten(c, sH[c], sE[c], gX[c], xHw, xHe, xEw, xEe, xQ);
                kanten(c, tH[c], tE[c], gY[c], yHs, yHn, yEs, yEn, yQ);
            }
        });
    }

    /** Seit 0.3: ob die Zelle (i, j) im Gitter samt Rand liegt. */
    boolean imGitter(int i, int j) { return i >= -G && i < nx + G && j >= -G && j < ny + G; }

    /**
     * Seit 0.3 (SF74 A, Seegang Werkbuch 74): Steigung des Grunds je Richtung, Front und Spiegel der Zelle (i, j). An der
     * Front (nass, ein Nachbar trocken, oder die Zelle nicht ganz bedeckt) steht das Wasser im Teilgitter eben (Bollermann, Chen, Kurganov und Noelle 2013):
     * Der Grund ist in der Zelle eine Ebene mit den Steigungen gX, gY; sein Umfang über die Zelle ist S = |gX| + |gY|.
     * Reicht das Wasser nicht über die Zelle (h &lt; S/2), bedeckt es einen Keil mit dem Spiegel w = b − S/2 + √(2 h S),
     * sonst w = η. {@link #fuellen} füllt in Ruhe nach derselben Regel, so bleibt der See in Ruhe.
     */
    void spiegelZelle(int i, int j) {
        int c = id(i, j);
        if (fels(c)) { gX[c] = gY[c] = 0; front[c] = false; spiegel[c] = Double.NaN; return; }
        double bc = b[c], hc = h[c];
        gX[c] = steigungGrund(i, j, 1, 0);
        gY[c] = steigungGrund(i, j, 0, 1);
        boolean nass = hc > hTrocken, f = false;
        if (teilgitter && nass)
            for (int d = 0; d < 4 && !f; d++) {
                int a = i + (d == 0 ? -1 : d == 1 ? 1 : 0), e = j + (d == 2 ? -1 : d == 3 ? 1 : 0);
                if (!imGitter(a, e)) continue;
                int n = id(a, e);
                if (!fels(n) && !(h[n] > hTrocken)) f = true;
            }
        if (!nass) { front[c] = false; spiegel[c] = bc; return; }
        double sg = Math.abs(gX[c]) + Math.abs(gY[c]);
        boolean keil = teilgitter && sg > 0 && hc < 0.5 * sg;                     // bedeckt die Zelle nicht ganz
        front[c] = f || keil;
        spiegel[c] = keil ? bc - 0.5 * sg + Math.sqrt(2 * hc * sg) : hc + bc;
    }

    /** Seit 0.3: Steigung des Grunds der Zelle (i, j) in Richtung (di, dj) nach minmod; 0 neben Fels und am Rand des Gitters. */
    double steigungGrund(int i, int j, int di, int dj) {
        if (!imGitter(i - di, j - dj) || !imGitter(i + di, j + dj)) return 0;
        int c = id(i, j), a = id(i - di, j - dj), z = id(i + di, j + dj);
        if (fels(a) || fels(z)) return 0;
        return minmod(b[c] - b[a], b[z] - b[c]);
    }

    /**
     * Seit 0.3 (SF74 A): Tiefe für den Quellterm −g q (b₊ − b₋) / Δx aus den Tiefen an den Kanten h₋, h₊ und dem Unterschied
     * des Grunds db = b₊ − b₋: beide nass ½(h₋ + h₊) wie bisher; eine trocken (Keil an der Front) ½(h₋² − h₊²)/db, so hält
     * der Druck an den Kanten dem Gefälle die Waage; sonst 0.
     */
    static double quellTiefe(double hm, double hp, double db) {
        if (hm > 0 && hp > 0) return 0.5 * (hm + hp);
        return db != 0 ? 0.5 * (hm * hm - hp * hp) / db : 0;
    }

    /**
     * Seit 0.3 (SF74 A): Tiefe und Stand an den beiden Kanten der Zelle c in einer Richtung (Steigungen sh, se; Grund gb)
     * und die Tiefe für den Quellterm. Gewöhnlich Mitte ± halbe Steigung. Mit {@link #teilgitter}: trocken der lineare
     * Grund; an der Front der ebene Spiegel w über dem linearen Grund (Kante trocken, wo w darunter liegt) und als Tiefe
     * des Quellterms ½(h₋² − h₊²)/(b₊ − b₋): So hält der Druck an den Kanten dem Gefälle im Teilgitter die Waage.
     */
    void kanten(int c, double sh, double se, double gb, double[] hm, double[] hp, double[] em, double[] ep, double[] q) {
        double hc = h[c], ec = hc + b[c];
        if (teilgitter && !(hc > hTrocken)) {                                       // trocken: linearer Grund
            hm[c] = hp[c] = 0; em[c] = b[c] - 0.5 * gb; ep[c] = b[c] + 0.5 * gb; q[c] = 0;
            return;
        }
        if (!front[c]) {
            hm[c] = hc - 0.5 * sh; hp[c] = hc + 0.5 * sh; em[c] = ec - 0.5 * se; ep[c] = ec + 0.5 * se;
            q[c] = quellTiefe(hm[c], hp[c], (ep[c] - hp[c]) - (em[c] - hm[c]));
            return;
        }
        double w = spiegel[c], bm = b[c] - 0.5 * gb, bp = b[c] + 0.5 * gb;
        double eM = Math.max(w, bm), eP = Math.max(w, bp), hM = eM - bm, hP = eP - bp;
        double qq = quellTiefe(hM, hP, gb);
        hm[c] = hM; hp[c] = hP; em[c] = eM; ep[c] = eP; q[c] = qq;
    }

    /**
     * HLL über eine Kante: Zustand links (hL, ηL, unL, utL) und rechts; schreibt Masse, Normal-, Tangentialimpuls nach
     * o[0..2] und die Druckkorrekturen nach Audusse nach o[3] (links) und o[4] (rechts).
     */
    void hll(double hL, double eL, double unL, double utL, double hR, double eR, double unR, double utR, double[] o) {
        double bL = eL - hL, bR = eR - hR, bf = Math.max(bL, bR);
        double hLs = Math.max(0, eL - bf), hRs = Math.max(0, eR - bf);
        double g = schwere;
        if (hLs <= hTrocken && hRs <= hTrocken) { o[0] = o[1] = o[2] = 0; }
        else {
            double cL = Math.sqrt(g * hLs), cR = Math.sqrt(g * hRs);
            double sl, sr;
            if (hLs <= hTrocken) { sl = unR - 2 * cR; sr = unR + cR; }
            else if (hRs <= hTrocken) { sl = unL - cL; sr = unL + 2 * cL; }
            else { sl = Math.min(unL - cL, unR - cR); sr = Math.max(unL + cL, unR + cR); }
            double qL = hLs * unL, qR = hRs * unR;
            double fL0 = qL, fL1 = qL * unL + 0.5 * g * hLs * hLs, fL2 = qL * utL;
            double fR0 = qR, fR1 = qR * unR + 0.5 * g * hRs * hRs, fR2 = qR * utR;
            if (sl >= 0) { o[0] = fL0; o[1] = fL1; o[2] = fL2; }
            else if (sr <= 0) { o[0] = fR0; o[1] = fR1; o[2] = fR2; }
            else {
                double inv = 1 / (sr - sl);
                o[0] = (sr * fL0 - sl * fR0 + sl * sr * (hRs - hLs)) * inv;
                o[1] = (sr * fL1 - sl * fR1 + sl * sr * (qR - qL)) * inv;
                o[2] = (sr * fL2 - sl * fR2 + sl * sr * (hRs * utR - hLs * utL)) * inv;
            }
        }
        o[3] = 0.5 * g * (hL * hL - hLs * hLs);
        o[4] = 0.5 * g * (hR * hR - hRs * hRs);
    }

    /**
     * Änderungsraten dh, dhu, dhv aus dem jetzigen Zustand (Randzellen gefüllt). Die Flüsse der Masse sind nach
     * {@link #abfluss} begrenzt: Keine Zelle gibt in einem Schritt dt mehr ab, als sie hat.
     */
    void raten(double dt) {
        steigungen();
        final double g = schwere;
        zeilen(0, ny, j -> {
            double[] o = new double[5];
            for (int i = -1; i < nx; i++) {
                int a = id(i, j), c = a + 1, f = j * (nx + 1) + i + 1;
                boolean sa = fels(a), sc = fels(c);
                if (sa && sc) { fx[3 * f] = fx[3 * f + 1] = fx[3 * f + 2] = 0; pxL[f] = pxR[f] = 0; continue; }
                double hL = xHe[a], eL = xEe[a], uL = vel(hu[a], h[a]) + 0.5 * sU[a], vL = vel(hv[a], h[a]) + 0.5 * sV[a];
                double hR = xHw[c], eR = xEw[c], uR = vel(hu[c], h[c]) - 0.5 * sU[c], vR = vel(hv[c], h[c]) - 0.5 * sV[c];
                if (sc) { fx[3 * f] = fx[3 * f + 1] = fx[3 * f + 2] = 0; pxL[f] = 0.5 * g * hL * hL; pxR[f] = 0; continue; }
                if (sa) { fx[3 * f] = fx[3 * f + 1] = fx[3 * f + 2] = 0; pxL[f] = 0; pxR[f] = 0.5 * g * hR * hR; continue; }
                hll(hL, eL, uL, vL, hR, eR, uR, vR, o);
                fx[3 * f] = o[0]; fx[3 * f + 1] = o[1]; fx[3 * f + 2] = o[2];
                pxL[f] = o[1] + o[3];
                pxR[f] = o[1] + o[4];
            }
        });
        zeilen(-1, ny, j -> {
            double[] o = new double[5];
            for (int i = 0; i < nx; i++) {
                int a = id(i, j), c = a + px, f = (j + 1) * nx + i;
                boolean sa = fels(a), sc = fels(c);
                if (sa && sc) { fy[3 * f] = fy[3 * f + 1] = fy[3 * f + 2] = 0; pyL[f] = pyR[f] = 0; continue; }
                double hL = yHn[a], eL = yEn[a], vL = vel(hv[a], h[a]) + 0.5 * tV[a], uL = vel(hu[a], h[a]) + 0.5 * tU[a];
                double hR = yHs[c], eR = yEs[c], vR = vel(hv[c], h[c]) - 0.5 * tV[c], uR = vel(hu[c], h[c]) - 0.5 * tU[c];
                if (sc) { fy[3 * f] = fy[3 * f + 1] = fy[3 * f + 2] = 0; pyL[f] = 0.5 * g * hL * hL; pyR[f] = 0; continue; }
                if (sa) { fy[3 * f] = fy[3 * f + 1] = fy[3 * f + 2] = 0; pyL[f] = 0; pyR[f] = 0.5 * g * hR * hR; continue; }
                hll(hL, eL, vL, uL, hR, eR, vR, uR, o);
                fy[3 * f] = o[0]; fy[3 * f + 1] = o[1]; fy[3 * f + 2] = o[2];
                pyL[f] = o[1] + o[3];
                pyR[f] = o[1] + o[4];
            }
        });
        abfluss(dt);
        final double inv = 1 / dx;
        zeilen(0, ny, j -> {
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                if (fels(c)) { dh[c] = dhu[c] = dhv[c] = 0; continue; }
                int fl = j * (nx + 1) + i, fr = fl + 1, fd = j * nx + i, fu = fd + nx;
                double masse = -(fx[3 * fr] - fx[3 * fl] + fy[3 * fu] - fy[3 * fd]) * inv;
                double mx = -(pxL[fr] - pxR[fl] + fy[3 * fu + 2] - fy[3 * fd + 2]) * inv;
                double my = -(pyL[fu] - pyR[fd] + fx[3 * fr + 2] - fx[3 * fl + 2]) * inv;
                // zentrierter Quellterm aus den eigenen Randwerten (Audusse)
                mx += g * xQ[c] * ((xEw[c] - xHw[c]) - (xEe[c] - xHe[c])) * inv;
                my += g * yQ[c] * ((yEs[c] - yHs[c]) - (yEn[c] - yHn[c])) * inv;
                dh[c] = masse;
                dhu[c] = mx;
                dhv[c] = my;
            }
        });
    }

    /**
     * Begrenzung des Abflusses (Abflusszeit nach Bollermann, Chen, Kurganov und Noelle 2013): Gibt eine Zelle über ihre vier
     * Kanten in dt mehr Wasser ab, als sie hat, werden ihre ausgehenden Flüsse (Masse und Mitführung längs der Kante) mit
     * θ = h / Abfluss gekürzt. An einer trockenen Front läuft die Welle mit 2c statt |u| + c, und in 2D addieren sich die
     * Richtungen: Der Zeitschritt nach Courant allein hält die Tiefe dort nicht positiv (S6a, Teil 2). Das Volumen bleibt
     * genau, denn jede Kante gibt ab, was die Nachbarzelle bekommt; im Ruhezustand fließt nichts, θ = 1.
     */
    void abfluss(double dt) {
        final double k = dt / dx;
        zeilen(0, ny, j -> {
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                if (fels(c)) { theta[c] = 1; continue; }
                int fl = j * (nx + 1) + i, fr = fl + 1, fd = j * nx + i, fu = fd + nx;
                double aus = k * (Math.max(0, fx[3 * fr]) + Math.max(0, -fx[3 * fl]) + Math.max(0, fy[3 * fu]) + Math.max(0, -fy[3 * fd]));
                theta[c] = aus > h[c] ? h[c] / aus : 1;
            }
        });
        zeilen(0, ny, j -> {
            for (int i = 0; i <= nx; i++) {
                int f = j * (nx + 1) + i;
                double m = fx[3 * f];
                if (m == 0) continue;
                int geber = m > 0 ? i - 1 : i;
                if (geber < 0 || geber >= nx) continue;                       // aus der Randzelle: ungekürzt
                double th = theta[id(geber, j)];
                if (th < 1) { fx[3 * f] *= th; fx[3 * f + 2] *= th; }
            }
        });
        zeilen(0, ny + 1, j -> {
            for (int i = 0; i < nx; i++) {
                int f = j * nx + i;
                double m = fy[3 * f];
                if (m == 0) continue;
                int geber = m > 0 ? j - 1 : j;
                if (geber < 0 || geber >= ny) continue;
                double th = theta[id(i, geber)];
                if (th < 1) { fy[3 * f] *= th; fy[3 * f + 2] *= th; }
            }
        });
    }

    /** Größte Wellengeschwindigkeit |u| + √(g h) im Innern (m/s). */
    double hoechstesTempo() {
        zeilen(0, ny, j -> {
            double m = 0;
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                double hh = h[c];
                if (hh <= hTrocken) continue;
                double u = Math.abs(vel(hu[c], hh)), v = Math.abs(vel(hv[c], hh));
                m = Math.max(m, Math.max(u, v) + Math.sqrt(schwere * hh));
            }
            zeileA[j] = m;
        });
        double m = 0;
        for (int j = 0; j < ny; j++) m = Math.max(m, zeileA[j]);
        return m;
    }

    /** Stufe 1: U = U0 + dt L; Stufe 2: U = (U0 + U + dt L)/2. */
    void anwenden(double dt, int stufe) {
        zeilen(0, ny, j -> {
            double cut = 0;
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                if (fels(c)) continue;
                double nh, nu, nv;
                if (stufe == 1) { nh = h[c] + dt * dh[c]; nu = hu[c] + dt * dhu[c]; nv = hv[c] + dt * dhv[c]; }
                else { nh = 0.5 * (h0[c] + h[c] + dt * dh[c]); nu = 0.5 * (hu0[c] + hu[c] + dt * dhu[c]); nv = 0.5 * (hv0[c] + hv[c] + dt * dhv[c]); }
                if (!(nh >= 0)) { cut -= Double.isNaN(nh) ? 0 : nh; nh = 0; }
                if (nh <= hTrocken || !(Math.abs(nu) < Double.MAX_VALUE) || !(Math.abs(nv) < Double.MAX_VALUE)) { nu = nv = 0; }
                double sp = Math.hypot(nu, nv), lim = vMax * nh;
                if (sp > lim) { nu *= lim / sp; nv *= lim / sp; }
                h[c] = nh; hu[c] = nu; hv[c] = nv;
            }
            zeileB[j] = cut;
        });
        double s = 0;
        for (int j = 0; j < ny; j++) s += zeileB[j];
        abgeschnitten += s * dx * dx;
    }

    /** Reibung nach Manning je Zelle, halb implizit: (hu)ⁿ⁺¹ = (hu)* / (1 + Δt g n² |u| / h^(4/3)). */
    void reibung(double dt) {
        final double g = schwere;
        zeilen(0, ny, j -> {
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                double hh = h[c], nn = n[c];
                if (hh <= hGeschw || !(nn > 0)) continue;
                double u = hu[c] / hh, v = hv[c] / hh, s = Math.sqrt(u * u + v * v);
                double k = 1 + dt * g * nn * nn * s / Math.pow(hh, 4.0 / 3.0);
                hu[c] /= k;
                hv[c] /= k;
            }
        });
    }

    /**
     * Versickern nach Green und Ampt je Zelle über dem Grundwasser: nass, genau gelöst bei festem h (höchstens das Wasser
     * der Zelle, höchstens bis die Front das Grundwasser erreicht); trocken zieht das versickerte Wasser mit K ab [G]. Die
     * Geschwindigkeit des Films bleibt (das Wasser geht senkrecht).
     */
    void versickern(double dt) {
        zeilen(0, ny, j -> {
            double vs = 0;
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                double k = kf[c];
                if (fels(c) || !(k > 0)) continue;
                double frei = b[c] - gw[c];
                if (!(frei > 0)) continue;
                double fmax = dTheta * frei, f = sick[c], hh = h[c];
                if (hh > hTrocken) {
                    if (f >= fmax) continue;                                  // gesättigt bis zum Grundwasser
                    double fn = greenAmpt(f, (hh + psi) * dTheta, k * dt);
                    double d = Math.min(fn - f, Math.min(hh, fmax - f));
                    if (!(d > 0)) continue;
                    double nh = hh - d;
                    if (nh <= hTrocken) { hu[c] = hv[c] = 0; }
                    else { double r = nh / hh; hu[c] *= r; hv[c] *= r; }
                    h[c] = nh;
                    sick[c] = f + d;
                    vs += d;
                } else if (f > 0) sick[c] = Math.max(0, f - k * dt);
            }
            zeileB[j] = vs;
        });
        double s = 0;
        for (int j = 0; j < ny; j++) s += zeileB[j];
        versickert += s * dx * dx;
    }

    /**
     * Das versickerte Wasser nach einem Schritt bei fester Tiefe: X aus (X − F) − A ln((A + X)/(A + F)) = K Δt, mit
     * A = (h + ψ) Δθ (Integral von dF/dt = K (1 + A/F)). Newton mit Klammer.
     */
    public static double greenAmpt(double f, double a, double kdt) {
        if (!(kdt > 0)) return f;
        if (!(a > 0)) return f + kdt;
        double lo = f, hi = f + kdt + Math.sqrt(2 * a * kdt);
        while (ga(hi, f, a, kdt) < 0) hi = f + 2 * (hi - f);
        double x = hi;
        for (int it = 0; it < 80; it++) {
            double gx = ga(x, f, a, kdt);
            if (gx > 0) hi = x; else lo = x;
            double d = x / (a + x);
            double xn = d > 0 ? x - gx / d : 0.5 * (lo + hi);
            if (!(xn > lo && xn < hi)) xn = 0.5 * (lo + hi);
            if (Math.abs(xn - x) <= 1e-15 * Math.max(x, 1e-30)) return xn;
            x = xn;
        }
        return x;
    }

    static double ga(double x, double f, double a, double kdt) { return (x - f) - a * Math.log1p((x - f) / (a + f)) - kdt; }

    void spuren() {
        final double t = zeit;
        zeilen(0, ny, j -> {
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                if (h[c] > hSpitze) {
                    double e = h[c] + b[c];
                    if (!(etaMax[c] >= e)) etaMax[c] = e;
                    nassZeit[c] = t;
                }
            }
        });
    }

    /** Massenfluss (m³/s) durch eine Seite ins Gitter, aus den gemittelten Flüssen des letzten Schritts. */
    double randfluss(int seite) {
        double s = 0;
        if (seite == W) for (int j = 0; j < ny; j++) s += mfx[j * (nx + 1)];
        else if (seite == E) for (int j = 0; j < ny; j++) s -= mfx[j * (nx + 1) + nx];
        else if (seite == S) for (int i = 0; i < nx; i++) s += mfy[i];
        else for (int i = 0; i < nx; i++) s -= mfy[ny * nx + i];
        return s * dx;
    }

    /** Ein Schritt (s) nach Courant; gibt den Zeitschritt zurück. */
    public double schritt() { return schritt(Double.POSITIVE_INFINITY); }

    /** Ein Schritt, höchstens dtMax (s). */
    public double schritt(double dtMax) {
        if (!gefuellt) throw new IllegalStateException("fuellen() fehlt");
        long t0 = System.nanoTime();
        double dt = Math.min(dtMax, cfl * dx / Math.max(vMin, hoechstesTempo()));
        int m = h.length;
        System.arraycopy(h, 0, h0, 0, m);
        System.arraycopy(hu, 0, hu0, 0, m);
        System.arraycopy(hv, 0, hv0, 0, m);
        raender(zeit);
        raten(dt);
        for (int f = 0; f < mfx.length; f++) mfx[f] = 0.5 * fx[3 * f];
        for (int f = 0; f < mfy.length; f++) mfy[f] = 0.5 * fy[3 * f];
        anwenden(dt, 1);
        raender(zeit + dt);
        raten(dt);
        for (int f = 0; f < mfx.length; f++) mfx[f] += 0.5 * fx[3 * f];
        for (int f = 0; f < mfy.length; f++) mfy[f] += 0.5 * fy[3 * f];
        anwenden(dt, 2);
        for (int s = 0; s < 4; s++) if (rand[s] != Rand.WAND) zufluss += randfluss(s) * dt;
        reibung(dt);
        versickern(dt);
        zeit += dt;
        spuren();
        schritte++;
        ns += System.nanoTime() - t0;
        return dt;
    }

    /** Rechnet bis zur Zeit tEnde (der letzte Schritt endet genau dort). */
    public void laufe(double tEnde) { while (zeit < tEnde - 1e-12) schritt(tEnde - zeit); }

    // ------------------------------------------------------------------ Abfragen

    public double zeit() { return zeit; }

    public long schritte() { return schritte; }

    /** Rechenzeit bisher (ms). */
    public double rechenzeit() { return ns / 1e6; }

    /** Wasser im Gitter (m³). */
    public double volumen() {
        double s = 0;
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) { int c = id(i, j); if (!fels(c)) s += h[c]; }
        return s * dx * dx;
    }

    /** Netto über die Seiten hereingeflossen (m³). */
    public double zufluss() { return zufluss; }

    /** Seit dem Füllen versickert (m³). */
    public double versickert() { return versickert; }

    /** Abgeschnittene negative Tiefen (m³), sollte null sein. */
    public double abgeschnitten() { return abgeschnitten; }

    public double tiefe(int i, int j) { return h[id(i, j)]; }

    /** Stand über dem Bezug (NaN: Fels). */
    public double stand(int i, int j) { int c = id(i, j); return fels(c) ? Double.NaN : h[c] + b[c]; }

    /** Seit 0.3: Spiegel der Zelle (i, j) nach dem letzten Schritt: nass η, an der Front im Keil der ebene Spiegel w (m über dem Bezug); trocken NaN. */
    public double spiegel(int i, int j) { int c = id(i, j); if (fels(c) || !(h[c] > hTrocken)) return Double.NaN; spiegelZelle(i, j); return spiegel[c]; }

    public double[] geschwindigkeit(int i, int j) { int c = id(i, j); return new double[] {vel(hu[c], h[c]), vel(hv[c], h[c])}; }

    public boolean trockenAmAnfang(int i, int j) { return trockenAnfang[id(i, j)]; }

    /** Höchste Geschwindigkeit in Zellen tiefer als hMin (m/s). */
    public double hoechsteGeschwindigkeit(double hMin) {
        double m = 0;
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                if (h[c] > hMin && h[c] > hGeschw) m = Math.max(m, Math.hypot(hu[c], hv[c]) / h[c]);
            }
        return m;
    }

    /** Höchster Auflauf über dem Ruhewasser in Zellen, die am Anfang trocken waren (m; −∞: keiner), seit dem Füllen oder {@link #spurenLoeschen}. */
    public double auflauf() {
        double best = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int c = id(i, j);
                if (trockenAnfang[c] && !Double.isNaN(etaMax[c])) best = Math.max(best, etaMax[c] - bezug);
            }
        return best;
    }

    /** Höchster Auflauf in einer Zeile j (m über dem Ruhewasser). */
    public double auflauf(int j) {
        double best = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < nx; i++) {
            int c = id(i, j);
            if (trockenAnfang[c] && !Double.isNaN(etaMax[c])) best = Math.max(best, etaMax[c] - bezug);
        }
        return best;
    }

    /**
     * Übernimmt den Stand nach einer Rechnung auf der Karte ({@code gpu.SwashKarte}, die Felder sind schon geschrieben):
     * Zeit, Schritte und die Bilanz seit dem Füllen.
     */
    public void uebernehmen(double zeit, long schritte, double zufluss, double versickert, double abgeschnitten) {
        this.zeit = zeit;
        this.schritte = schritte;
        this.zufluss = zufluss;
        this.versickert = versickert;
        this.abgeschnitten = abgeschnitten;
    }

    /** Löscht die höchsten Stände (der Spülsaum beginnt neu). */
    public void spurenLoeschen() { Arrays.fill(etaMax, Double.NaN); spuren(); }

    public String beschreibung() {
        return String.format(java.util.Locale.GERMAN, "Swash %s: %d × %d zu %.3f m (%.0f × %.0f m), Drehung %.1f°", VERSION, nx, ny, dx, nx * dx, ny * dx, Math.toDegrees(winkel));
    }
}
