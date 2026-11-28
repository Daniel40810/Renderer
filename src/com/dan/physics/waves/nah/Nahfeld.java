package com.dan.physics.waves.nah;

import com.dan.physics.waves.Waves;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Das Nahfeld des Seegangs (Seegang Werkbuch, S2; SF1 A): die Wirkungsbilanz auf einem feinen Gitter um einen Ort, als
 * Referenz in doppelter Genauigkeit. Je Zelle {@code nb} Bänder der <b>absoluten</b> Frequenz ω (logarithmisch gestuft)
 * × {@code nd} Richtungen (gleichmäßig, 0 = +x, gegen den Uhrzeigersinn, wohin die Wellen laufen). Gespeichert ist die
 * Wirkung N je Stützstelle, über die Breite des Bands und Δθ genommen (m²·s): die Varianz der Stützstelle ist E = σ N.
 * <p>
 * <b>Warum absolute Frequenz:</b> Die Oberfläche (S3) setzt je Band eine Phase Φ − ω t an; ω muss dafür überall dieselbe
 * sein. Bei ruhender Strömung bleibt ω längs eines Strahls erhalten: Es gibt keinen Fluss in der Frequenz. Die
 * Wellenzahl folgt je Zelle, Band und Richtung aus ω = σ(k, h) + k U_θ mit σ² = g k tanh(k h) und U_θ = U · e_θ. Hat die
 * Gleichung keine Lösung (Gegenstrom über der Gruppengeschwindigkeit), ist die Stützstelle <b>blockiert</b>: Ihre Wirkung
 * geht verloren und wird gebucht.
 * <pre>
 * ∂N/∂t + ∂(C_x N)/∂x + ∂(C_y N)/∂y + ∂(C_θ N)/∂θ = S/σ
 * C_x = s c_g cos θ + U,  C_y = s c_g sin θ + V
 * C_θ = s · c_θ,Refraktion + c_g (−sin θ ∂s/∂x + cos θ ∂s/∂y)          (Beugung nach Holthuijsen u. a. 2003)
 * c_θ,Refraktion = σ/sinh(2kh) (sin θ ∂h/∂x − cos θ ∂h/∂y) + sin θ (cos θ ∂U/∂x + sin θ ∂V/∂x) − cos θ (cos θ ∂U/∂y + sin θ ∂V/∂y)
 * s = √(1 + δ),  δ = ∇·(c c_g ∇√E_b) / (k² c c_g √E_b)      (E_b die Energie des Bands, geglättet; ohne Beugung s = 1)
 * </pre>
 * <b>Kanten:</b> Was über eine Fläche zu einer trockenen Zelle ausfließt, wird mit dem Reflexionsbeiwert
 * K_r² (Energie) in die gespiegelte Richtung derselben Zelle zurückgegeben: K_r = min(1, 0,1 ξ²) nach Battjes (1974),
 * ξ = tan α / √(H/L₀), tan α aus dem Höhenunterschied zur trockenen Zelle; an Bauwerken ({@link #wand}) K_r = 1. Der
 * Rest ist an der Kante verloren (gebucht).
 * <p>
 * <b>Quellen</b> (je Zelle, wie {@code physics.waves.Waves}, mit σ und k der Stützstelle): Wind (Cavaleri/Malanotte-Rizzoli
 * mit Filter nach Tolman, exponentiell nach Komen), Schaumkronen (Komen, WAM 3), Brechen an der Tiefe (Battjes–Janssen
 * 1978) mit der Kappung H_rms ≤ γ h, Bodenreibung (JONSWAP), Pflanzen (Mendez und Losada 2004). Senken halb implizit.
 * Keine Vierwellen-Wechselwirkung [G]: Über einige Kilometer hat sie wenig Zeit; das Spektrum am Rand kommt aus
 * {@code physics.waves}, das sie rechnet.
 * <p>
 * <b>Ausbreitung:</b> Flussform, explizit, Teilschritte nach Courant; im Raum aufwind erster Ordnung, in θ mit der
 * begrenzten zweiten Ordnung wie {@code Waves}. Ränder des Gebiets: einlaufend aus {@link #rand}, auslaufend frei;
 * {@link #periodischY} für Fälle, die längs y gleich sind. Jeder Fluss ist gebucht ({@link #bilanz}).
 * <p>
 * Kennt nur java.* und {@code physics.waves.Waves} (dasselbe Modul).
 */
public final class Nahfeld {
    public static final String VERSION = "0.2";
    public static final double G = Waves.G, RHO = Waves.RHO_W, RHO_A = Waves.RHO_A;

    public final int nx, ny, nb, nd, nc, nn;
    /** Kantenlänge der Zellen (m); Ecke unten links des Gitters (m, etwa UTM), sie wandert mit {@link #verschieben}. */
    public final double dx;
    public double x0, y0;
    /** Mitten der Bänder (Hz), ihre Kreisfrequenz ω (rad/s) und Breite (Hz); Verhältnis benachbarter Bänder. */
    public final double[] f, omega, df;
    public final double stufe;
    public final double[] theta, cosT, sinT;
    public final double dTheta;

    // ---- Eingaben je Zelle; nach einer Änderung vorbereiten()
    /** Höhe des Grunds (m, etwa NHN) und des Wasserspiegels (m); Tiefe = Spiegel − Grund. */
    public final double[] grund, spiegel;
    /** Strömung (m/s), Wind in 10 m (m/s) in den Achsen des Gitters; u* (m/s, null oder ≤ 0: nach Wu). */
    public final double[] u, v, windU, windV;
    public double[] uStern;
    /** Pflanzen (Mendez und Losada 2004): C_D · b_v · N_v (1/m) und Höhe der Halme (m); null: keine. */
    public double[] pflanzen, halm;
    /** Bauwerk (Wand, Spundwand): Eine trockene Zelle mit wand reflektiert ganz. Null: keine. */
    public boolean[] wand;

    // ---- Zustand
    /** Wirkung je Stützstelle (m²·s), Index ((c · nb) + b) · nd + q. */
    public double[] n;
    private double[] n2;

    // ---- Schalter und Größen [G]
    public boolean mitWind = true, mitSchaum = true, mitBrechen = true, mitReibung = true, mitPflanzen = true, mitRefraktion = true, mitBeugung = true,
            mitReflexion = true, kappung = true;
    public boolean periodischY;
    /** Quellen nach jedem Teilschritt der Ausbreitung (wahr) oder einmal je Schritt wie physics.waves. */
    public boolean quellenJeTeilschritt = true;
    public double minTiefe = 0.05;
    public double cds = 2.36e-5, steilheitPM = Math.sqrt(3.02e-3), cReibung = 0.038, alphaBJ = 1.0, gammaBJ = 0.73;
    /** Begrenzung von c_θ wie in Waves [G]; Courant-Zahl der Teilschritte. */
    public double drehGrenze = 4, courant = 0.8;
    /** Beugung: Glätten von √E (Durchgänge eines 3 × 3-Filters), Abstand der Krümmung in Wellenlängen (höchstens abstandMax Zellen) und Grenzen von δ [G]. */
    public int glaetten = 2, abstandMax = 16;
    public double stencil = 0.25, deltaMin = -0.8, deltaMax = 3;

    /**
     * Bindung an das grobe Feld (Seegang Werkbuch, Datei 1, 6.2): m₀ des groben Felds (G3w) je Zelle (m²); null: keine.
     * Je Block von {@link #bindungBlock} × {@link #bindungBlock} Zellen (100 m) wird m₀ des Nahfelds mit der Zeit
     * {@link #bindungZeit} auf das Mittel des Ziels geführt: alle Stützstellen der Zellen des Blocks mal
     * 1 + (Δt/τ)(m₀,Ziel / m₀ − 1), begrenzt auf [0,5; 2]. Die Form des Spektrums bleibt, gebucht unter „Bindung“.
     * <p>
     * Frei bleiben Blöcke, die selbst oder deren acht Nachbarblöcke eine trockene Zelle oder Wand enthalten: Dort zeigt
     * das Nahfeld, was das grobe Feld nicht auflöst (Lee hinter Warften, Lahnungen, Anlegern; Reflexion an Kanten). Frei
     * bleiben auch Blöcke, die im Mittel höchstens {@link #bindungTiefe} tief sind oder deren Tiefe um mehr als
     * {@link #bindungStreuung} des Mittels streut (Watt, Priele, Kanten; SF17 A): Dort rechnet der feine Grund Shoaling,
     * Reibung und Brechen, die das grobe Feld über 100 m gemittelt nicht sieht. Mit {@link #ohneBindung} legt der
     * Aufrufer die freien Zellen selbst fest.
     */
    public double[] bindungM0;
    /**
     * Statt m₀: das Spektrum des Ziels je Stützstelle (Varianz, m², Länge nn; null: nur m₀). Dann führt die Bindung jede
     * Stützstelle einer gebundenen Zelle mit der Zeit τ zum Ziel, N ← (N + Δt/τ · N_Ziel) / (1 + Δt/τ): Form und Höhe des
     * Spektrums kommen im freien Wasser aus G3w (mit der Vierwellen-Wechselwirkung, die das Nahfeld nicht rechnet; B20).
     */
    public double[] bindungSpektrum;
    public int bindungBlock = 1;
    public double bindungZeit = 900, bindungTiefe = 2, bindungStreuung = 0.25;
    public boolean[] ohneBindung;

    /** Einlaufende Wirkung je Stützstelle in einer Geisterzelle (i ∈ [−1, nx], j ∈ [−1, ny]); null: nichts läuft ein. */
    public interface Rand { double n(int i, int j, int b, int q); }
    public Rand rand;

    // ---- Abgeleitetes
    private final boolean[] nass;
    /** Je Stützstelle: Wellenzahl (NaN: blockiert), σ, c_g (relativ, Betrag), c_θ der Refraktion. */
    private final double[] k, sig, cg, cth;
    /** Je Zelle und Band: s = √(1 + δ) der Beugung (1 ohne). */
    private final double[] s;
    /** Je Stützstelle die Geschwindigkeiten des laufenden Schritts {C_x, C_y, C_θ} (mit Beugung). */
    private final double[] cxA, cyA, ctA;
    /** Je Zelle die Summe der Courant-Raten (1/s), für die örtlichen Schritte des Anlaufs. */
    private final double[] rateZ;
    private final double[] dhx, dhy, ux, uy, vx, vy;
    private double rate;

    // ---- Bilanz der Wirkung (m²·s·m²)
    /**
     * 0 Rand (netto ein), 1 Wind, 2 Schaumkronen, 3 Brechen (mit Kappung), 4 Reibung, 5 Pflanzen, 6 Kanten (nicht
     * reflektiert), 7 blockiert, 8 trocken, 9 Begrenzung (negative Werte auf 0 gesetzt; gewonnen), 10 Bindung an das
     * grobe Feld (beide Vorzeichen).
     */
    public final double[] bilanz = new double[11];
    public static final String[] BILANZ = {"Rand netto", "Wind", "Schaumkronen", "Brechen", "Bodenreibung", "Pflanzen", "Kanten", "blockiert", "trocken", "Begrenzung", "Bindung"};
    /** Im Anlauf ({@link #anlauf}) wird nicht gebucht: Er rechnet in Pseudozeit. */
    private boolean buchen = true;
    private final double[] buch;
    /** Leistung der Senken je Zelle im letzten Schritt der Quellen (W/m²): Schaumkronen, Brechen mit Kappung, Reibung, Pflanzen. */
    public final double[] dwc, dbr, dbf, dveg;
    public double zeit;
    public int teilschritte;

    public Nahfeld(int nx, int ny, double dx, int nb, double fMin, double fMax, int nd) {
        this.nx = nx; this.ny = ny; this.dx = dx; this.nb = nb; this.nd = nd;
        nc = nx * ny;
        nn = nc * nb * nd;
        f = new double[nb]; omega = new double[nb]; df = new double[nb];
        stufe = Math.pow(fMax / fMin, 1.0 / nb);
        for (int b = 0; b < nb; b++) {
            double lo = fMin * Math.pow(stufe, b), hi = lo * stufe;
            f[b] = Math.sqrt(lo * hi);
            omega[b] = 2 * Math.PI * f[b];
            df[b] = hi - lo;
        }
        theta = new double[nd]; cosT = new double[nd]; sinT = new double[nd];
        dTheta = 2 * Math.PI / nd;
        for (int q = 0; q < nd; q++) { theta[q] = q * dTheta; cosT[q] = Math.cos(theta[q]); sinT[q] = Math.sin(theta[q]); }
        grund = new double[nc]; spiegel = new double[nc]; u = new double[nc]; v = new double[nc]; windU = new double[nc]; windV = new double[nc];
        n = new double[nn]; n2 = new double[nn];
        nass = new boolean[nc];
        k = new double[nn]; sig = new double[nn]; cg = new double[nn]; cth = new double[nn];
        s = new double[nc * nb];
        Arrays.fill(s, 1);
        cxA = new double[nn]; cyA = new double[nn]; ctA = new double[nn];
        rateZ = new double[nx * ny];
        dhx = new double[nc]; dhy = new double[nc]; ux = new double[nc]; uy = new double[nc]; vx = new double[nc]; vy = new double[nc];
        buch = new double[nc * bilanz.length];
        dwc = new double[nc]; dbr = new double[nc]; dbf = new double[nc]; dveg = new double[nc];
    }

    public int ix(int c, int b, int q) { return (c * nb + b) * nd + q; }

    public double tiefe(int c) { return Math.max(0, spiegel[c] - grund[c]); }

    public boolean nass(int c) { return nass[c]; }

    /** Mitte der Zelle (i, j) in den Koordinaten des Gitters (m). */
    public double x(int i) { return x0 + (i + 0.5) * dx; }

    public double y(int j) { return y0 + (j + 0.5) * dx; }

    // ------------------------------------------------------------------ Dispersion mit Strömung

    /**
     * Wellenzahl (rad/m) zur absoluten Kreisfrequenz ω in der Tiefe h bei der Strömung U_θ in Laufrichtung (m/s):
     * ω = √(g k tanh(k h)) + k U_θ, der Zweig mit c_g + U_θ > 0; NaN, wenn keine Lösung besteht (blockiert).
     */
    public static double wellenzahl(double omega, double h, double ut) {
        if (ut == 0) return Waves.wellenzahl(omega, h);
        if (ut > 0) {                                                          // mitlaufend: f(k) = σ(k) + k U − ω steigt
            double lo = 0, hi = Waves.wellenzahl(omega, h);
            for (int it = 0; it < 200; it++) {
                double m = 0.5 * (lo + hi);
                if (sigma(m, h) + m * ut > omega) hi = m; else lo = m;
                if (hi - lo < 1e-13 * hi) break;
            }
            return 0.5 * (lo + hi);
        }
        // Gegenstrom: σ(k) + k U steigt, solange c_g(k) > −U (c_g fällt mit k), dann fällt es; gesucht ist der Zweig davor
        double kmax = Waves.wellenzahl(omega, h) * 4;
        for (int t = 0; t < 60 && Waves.gruppe(sigma(kmax, h), kmax, h) + ut >= 0 && sigma(kmax, h) + kmax * ut < omega; t++) kmax *= 2;
        double kb = kmax;
        if (Waves.gruppe(sigma(kmax, h), kmax, h) + ut < 0) {                // Scheitel c_g = −U suchen
            double lo = 0, hi = kmax;
            for (int it = 0; it < 200; it++) {
                double m = 0.5 * (lo + hi);
                if (Waves.gruppe(sigma(m, h), m, h) + ut > 0) lo = m; else hi = m;
                if (hi - lo < 1e-13 * hi) break;
            }
            kb = lo;
        }
        if (sigma(kb, h) + kb * ut < omega) return Double.NaN;                // auch am Scheitel zu klein: blockiert
        double lo = 0, hi = kb;
        for (int it = 0; it < 200; it++) {
            double m = 0.5 * (lo + hi);
            if (sigma(m, h) + m * ut > omega) hi = m; else lo = m;
            if (hi - lo < 1e-13 * hi) break;
        }
        return 0.5 * (lo + hi);
    }

    static double sigma(double kk, double h) { return Math.sqrt(G * kk * Math.tanh(kk * h)); }

    // ------------------------------------------------------------------ Vorbereiten

    /** Rechnet Tiefe, Wellenzahlen, Gruppengeschwindigkeiten, Gefälle und Refraktion aus Spiegel, Grund und Strömung. Zellen, die trocken fallen, verlieren ihre Wirkung (gebucht). */
    public void vorbereiten() {
        zellen();
        IntStream.range(0, nc).parallel().forEach(c -> {
            double h = tiefe(c);
            for (int b = 0; b < nb; b++)
                for (int q = 0; q < nd; q++) {
                    int id = ix(c, b, q);
                    if (!nass[c]) { k[id] = sig[id] = cg[id] = cth[id] = 0; continue; }
                    double ut = u[c] * cosT[q] + v[c] * sinT[q];
                    double kk = wellenzahl(omega[b], h, ut);
                    k[id] = kk;
                    if (Double.isNaN(kk)) { sig[id] = cg[id] = cth[id] = 0; continue; }
                    double sg = sigma(kk, h);
                    sig[id] = sg;
                    cg[id] = Waves.gruppe(sg, kk, h);
                    double zkh = 2 * kk * h, r = zkh > 60 ? 0 : sg / Math.sinh(zkh);
                    double co = cosT[q], si = sinT[q];
                    cth[id] = mitRefraktion ? r * (si * dhx[c] - co * dhy[c]) + si * (co * ux[c] + si * vx[c]) - co * (co * uy[c] + si * vy[c]) : 0;
                }
        });
        // Blockierte Stützstellen verlieren ihre Wirkung (gebucht)
        for (int c = 0; c < nc; c++) {
            if (!nass[c]) continue;
            double verlust = 0;
            for (int i = c * nb * nd; i < (c + 1) * nb * nd; i++) if (Double.isNaN(k[i]) && n[i] > 0) { verlust += n[i]; n[i] = 0; }
            buch[c * bilanz.length + 7] -= verlust;
        }
        buche();
    }

    /**
     * Der Teil von {@link #vorbereiten()} je Zelle: nass oder trocken (Zellen, die trocken fallen, verlieren ihre Wirkung,
     * gebucht) und die Gefälle von Tiefe und Strömung. Die Karte ({@code gpu.NahfeldKarte}) rechnet die Stützstellen
     * selbst und braucht nur diesen Teil.
     */
    public void zellen() {
        for (int c = 0; c < nc; c++) {
            boolean w = tiefe(c) >= minTiefe;
            if (nass[c] && !w) {
                double verlust = 0;
                for (int i = c * nb * nd; i < (c + 1) * nb * nd; i++) { verlust += n[i]; n[i] = 0; }
                buch[c * bilanz.length + 8] -= verlust;
            }
            nass[c] = w;
        }
        IntStream.range(0, nc).parallel().forEach(c -> {
            int i = c % nx, j = c / nx;
            int w = i > 0 && nass[c - 1] ? c - 1 : c, o = i < nx - 1 && nass[c + 1] ? c + 1 : c;
            int sd = nachbarS(c), nd2 = nachbarN(c);
            int su = sd >= 0 && nass[sd] ? sd : c, no = nd2 >= 0 && nass[nd2] ? nd2 : c;
            double lx = (o == c ? 0 : 1) + (w == c ? 0 : 1), ly = (no == c ? 0 : 1) + (su == c ? 0 : 1);
            dhx[c] = lx > 0 ? (tiefe(o) - tiefe(w)) / (lx * dx) : 0;
            dhy[c] = ly > 0 ? (tiefe(no) - tiefe(su)) / (ly * dx) : 0;
            ux[c] = lx > 0 ? (u[o] - u[w]) / (lx * dx) : 0; uy[c] = ly > 0 ? (u[no] - u[su]) / (ly * dx) : 0;
            vx[c] = lx > 0 ? (v[o] - v[w]) / (lx * dx) : 0; vy[c] = ly > 0 ? (v[no] - v[su]) / (ly * dx) : 0;
        });
        buche();
    }

    /** Gefälle je Zelle aus {@link #zellen()}: ∂h/∂x, ∂h/∂y, ∂U/∂x, ∂U/∂y, ∂V/∂x, ∂V/∂y. */
    public double[] gefaelle(int c) { return new double[] {dhx[c], dhy[c], ux[c], uy[c], vx[c], vy[c]}; }

    /** Welche Zellen frei von der Bindung sind ({@link #ohneBindung} oder die eigene Regel); nach {@link #zellen()}. */
    public boolean[] freiVonBindung() { return ohneBindung != null ? ohneBindung : freiNachRegel(); }

    /** Zahl der Geisterzellen: 2 (nx + ny), Folge Süd, Nord, West, Ost. */
    public int randZellen() { return 2 * (nx + ny); }

    /** Lage (i, j) der Geisterzelle r. */
    public int[] randLage(int r) {
        if (r < nx) return new int[] {r, -1};
        if (r < 2 * nx) return new int[] {r - nx, ny};
        if (r < 2 * nx + ny) return new int[] {-1, r - 2 * nx};
        return new int[] {nx, r - 2 * nx - ny};
    }

    /** Varianz je Geisterzelle, Band und Richtung (m²) aus einem Ziel, Index (r · nb + b) · nd + q; für die Karte. */
    public double[] randVarianz(Ziel z) {
        int m = nb * nd;
        double[] v = new double[randZellen() * m], e = new double[m];
        for (int r = 0; r < randZellen(); r++) {
            int[] p = randLage(r);
            z.varianz(x0 + (p[0] + 0.5) * dx, y0 + (p[1] + 0.5) * dx, e);
            System.arraycopy(e, 0, v, r * m, m);
        }
        return v;
    }

    /** Wie {@link #randVarianz(Ziel)} aus dem jetzigen {@link #rand}: Wirkung mal σ der Nachbarzelle (ohne σ: ω), wie die Karte zurückrechnet; nach vorbereiten(). */
    public double[] randVarianz() {
        int m = nb * nd;
        double[] v = new double[randZellen() * m];
        if (rand == null) return v;
        for (int r = 0; r < randZellen(); r++) {
            int[] p = randLage(r);
            int c = Math.max(0, Math.min(nx - 1, p[0])) + nx * Math.max(0, Math.min(ny - 1, p[1]));
            for (int b = 0; b < nb; b++)
                for (int q = 0; q < nd; q++) {
                    double sg = sig[ix(c, b, q)];
                    if (!(sg > 0)) sg = omega[b];
                    v[(r * nb + b) * nd + q] = rand.n(p[0], p[1], b, q) * sg;
                }
        }
        return v;
    }

    private int nachbarS(int c) { int j = c / nx; return j > 0 ? c - nx : periodischY ? c + (ny - 1) * nx : -1; }

    private int nachbarN(int c) { int j = c / nx; return j < ny - 1 ? c + nx : periodischY ? c - (ny - 1) * nx : -1; }

    // ------------------------------------------------------------------ Beugung

    /** s = √(1 + δ) je Zelle und Band aus der geglätteten Energie (Holthuijsen, Herman, Booij 2003). */
    private void beugung() {
        if (!mitBeugung) { Arrays.fill(s, 1); return; }
        final double[] a0 = new double[nc * nb], kq = new double[nc * nb], ccg = new double[nc * nb];
        IntStream.range(0, nc).parallel().forEach(c -> {
            if (!nass[c]) return;
            for (int b = 0; b < nb; b++) {
                double e = 0, kw = 0, cw = 0, w = 0;
                for (int q = 0; q < nd; q++) {
                    int id = ix(c, b, q);
                    if (Double.isNaN(k[id]) || sig[id] == 0) continue;
                    double ee = sig[id] * n[id];
                    e += ee;
                    double gew = ee + 1e-30;
                    kw += gew * k[id]; cw += gew * (sig[id] / k[id]) * cg[id]; w += gew;
                }
                a0[c * nb + b] = Math.sqrt(Math.max(0, e));
                kq[c * nb + b] = w > 0 ? kw / w : 0;
                ccg[c * nb + b] = w > 0 ? cw / w : 0;
            }
        });
        double[] a = a0;
        for (int p = 0; p < glaetten; p++) a = glaette(a);
        final double[] aa = a;
        IntStream.range(0, nc).parallel().forEach(c -> {
            int i = c % nx, j = c / nx;
            for (int b = 0; b < nb; b++) {
                int o = c * nb + b;
                if (!nass[c] || aa[o] <= 1e-12 || kq[o] <= 0) { s[o] = 1; continue; }
                // ∇·(c c_g ∇a) mit dem Abstand m Δx ≈ L/4 ({@link #stencil}): Was sich auf kürzeren Wegen ändert, ist
                // für die phasengemittelte Amplitude Rauschen; mit Δx gerechnet wüchse es mit (L/Δx)² und schaukelte
                // sich längs des Strahls auf (eine Delle in a macht δ > 0, schnellere Wellen, tiefere Delle).
                // Fehlt der Nachbar (Rand, trocken), steht dort a linear aus dem gegenüberliegenden fortgesetzt:
                // Die Krümmung längs dieser Achse ist dann null.
                int m = Math.max(1, Math.min(abstandMax, (int) Math.round(stencil * 2 * Math.PI / kq[o] / dx)));
                int[] nb4 = {i - m >= 0 ? c - m : -1, i + m < nx ? c + m : -1, zeileVon(j - m, i), zeileVon(j + m, i)};
                double sum = 0;
                for (int r = 0; r < 4; r++) {
                    if (r >= 2 && ny == 1) continue;
                    int z = nb4[r], g = nb4[r ^ 1];
                    boolean da = z >= 0 && nass[z], gegen = g >= 0 && nass[g];
                    if (da) sum += 0.5 * (ccg[o] + ccg[z * nb + b]) * (aa[z * nb + b] - aa[o]);
                    else if (gegen) sum += ccg[o] * (aa[o] - aa[g * nb + b]);
                }
                double d = sum / (m * dx * m * dx) / (kq[o] * kq[o] * ccg[o] * aa[o]);
                d = Math.max(deltaMin, Math.min(deltaMax, d));
                s[o] = Math.sqrt(1 + d);
            }
        });
    }

    /** Zelle (i, j) mit der Zeile j, periodisch oder −1 außerhalb. */
    private int zeileVon(int j, int i) {
        if (j < 0 || j >= ny) { if (!periodischY) return -1; j = Math.floorMod(j, ny); }
        return j * nx + i;
    }

    private double[] glaette(double[] a) {
        double[] r = new double[a.length];
        IntStream.range(0, nc).parallel().forEach(c -> {
            if (!nass[c]) return;
            int i = c % nx, j = c / nx;
            for (int b = 0; b < nb; b++) {
                double sum = 0, w = 0;
                for (int dj = -1; dj <= 1; dj++)
                    for (int di = -1; di <= 1; di++) {
                        int ii = i + di, jj = j + dj;
                        if (ii < 0 || ii >= nx) continue;
                        if (jj < 0 || jj >= ny) { if (!periodischY) continue; jj = Math.floorMod(jj, ny); }
                        int z = jj * nx + ii;
                        if (!nass[z]) continue;
                        double g = (di == 0 ? 2 : 1) * (dj == 0 ? 2 : 1);
                        sum += g * a[z * nb + b];
                        w += g;
                    }
                r[c * nb + b] = w > 0 ? sum / w : a[c * nb + b];
            }
        });
        return r;
    }

    /** Gefälle von s je Zelle und Band in x und y (für C_θ der Beugung). */
    private double[] sGefaelle(int c, int b) {
        int i = c % nx;
        int w = i > 0 && nass[c - 1] ? c - 1 : c, o = i < nx - 1 && nass[c + 1] ? c + 1 : c;
        int sd = nachbarS(c), nd2 = nachbarN(c);
        int su = sd >= 0 && nass[sd] ? sd : c, no = nd2 >= 0 && nass[nd2] ? nd2 : c;
        double lx = (o == c ? 0 : 1) + (w == c ? 0 : 1), ly = (no == c ? 0 : 1) + (su == c ? 0 : 1);
        return new double[] {lx > 0 ? (s[o * nb + b] - s[w * nb + b]) / (lx * dx) : 0, ly > 0 ? (s[no * nb + b] - s[su * nb + b]) / (ly * dx) : 0};
    }

    // ------------------------------------------------------------------ Schritt

    /** Geschwindigkeiten einer Stützstelle mit Beugung: {C_x, C_y, C_θ}. */
    private void tempo(int c, int b, int q, double[] gs, double[] out) {
        int id = ix(c, b, q);
        double sb = s[c * nb + b], g = cg[id], co = cosT[q], si = sinT[q];
        out[0] = sb * g * co + u[c];
        out[1] = sb * g * si + v[c];
        double ct = sb * cth[id] + (mitBeugung ? g * (-si * gs[0] + co * gs[1]) : 0);
        double grenze = drehGrenze * dTheta * (Math.abs(out[0]) / dx + Math.abs(out[1]) / dx);
        out[2] = Math.max(-grenze, Math.min(grenze, ct));
    }

    /** Füllt die Geschwindigkeiten des Schritts mit der jetzigen Beugung und gibt die größte Summe der Courant-Raten (1/s). */
    private double geschwindigkeiten() {
        return IntStream.range(0, nc).parallel().mapToDouble(c -> {
            double r = 0;
            double[] t = new double[3];
            for (int b = 0; b < nb; b++) {
                double[] gs = mitBeugung && nass[c] ? sGefaelle(c, b) : new double[2];
                for (int q = 0; q < nd; q++) {
                    int id = ix(c, b, q);
                    if (!nass[c] || Double.isNaN(k[id])) { cxA[id] = cyA[id] = ctA[id] = 0; continue; }
                    tempo(c, b, q, gs, t);
                    cxA[id] = t[0]; cyA[id] = t[1]; ctA[id] = t[2];
                    r = Math.max(r, Math.abs(t[0]) / dx + (ny > 1 ? Math.abs(t[1]) / dx : 0) + Math.abs(t[2]) / dTheta);
                }
            }
            rateZ[c] = r;
            return r;
        }).max().orElse(0);
    }

    /**
     * Ein Schritt: Beugung, dann Ausbreitung in Teilschritten, die Quellen nach jedem Teilschritt ({@link
     * #quellenJeTeilschritt}, sonst einmal am Ende). Je Teilschritt ist das stationäre Gleichgewicht einer Zelle vom
     * Zeitschritt unabhängig (N_i = N_(i−1) / (1 + γ Δx / C)); einmal am Ende mit dem ganzen Schritt hinge es von ihm ab,
     * sobald γ Δt nicht mehr klein ist (Pflanzen, Brandung).
     */
    public void schritt(double dt) {
        beugung();
        rate = geschwindigkeiten();
        teilschritte = Math.max(1, (int) Math.ceil(dt * rate / courant));
        double h = dt / teilschritte;
        boolean q = mitWind || mitSchaum || mitBrechen || mitReibung || mitPflanzen;
        for (int t = 0; t < teilschritte; t++) {
            ausbreitung(h, null);
            if (q && quellenJeTeilschritt) quellen(h, null, n2, null);        // n2: der Zustand vor der Ausbreitung
            if (bindungM0 != null && quellenJeTeilschritt) binden(h, null);
        }
        if (q && !quellenJeTeilschritt) quellen(dt, null, n, null);
        if (bindungM0 != null && !quellenJeTeilschritt) binden(dt, null);
        zeit += dt;
    }

    /**
     * Anlauf (Datei 1, 6.2: „Ein Sprung der Kamera zeigt sofort die eingeschwungene See“): rechnet zum stationären
     * Zustand in Pseudozeit mit örtlichen Schritten, je Stützstelle Δt = courant / ihre Rate (|C_x| + |C_y|)/Δx +
     * |C_θ|/Δθ, höchstens {@code dtMax}. Jede Änderung einer Stützstelle (Ausbreitung, Reflexion in sie, Quellen,
     * Bindung) geht mit ihrem eigenen Δt ein; im Gleichgewicht ist die Summe der Raten null, der Zustand also derselbe
     * wie mit {@link #schritt}. Jede Stützstelle läuft so rund eine Zelle je Runde, auch die kurzen, langsamen Wellen. Bucht nicht. Gibt die Runden zurück; endet, wenn sich Hs in keiner
     * Zelle um mehr als {@code toleranz} (relativ zum größten Hs) ändert. Statt der vier Durchgänge des stationären SWAN
     * (Gauß–Seidel), weil jede Runde auf der Karte ganz parallel läuft [G].
     */
    public int anlauf(int hoechstens, double toleranz, double dtMax) {
        boolean q = mitWind || mitSchaum || mitBrechen || mitReibung || mitPflanzen;
        double[] lokal = new double[nn], alt = new double[nc];
        double[] nachQ = n.clone();                                            // Zustand nach den Quellen der Runde davor (B22)
        for (int c = 0; c < nc; c++) alt[c] = hs(c);
        buchen = false;
        try {
            for (int it = 1; it <= hoechstens; it++) {
                beugung();
                rate = geschwindigkeiten();
                for (int id = 0; id < nn; id++) {
                    double r = Math.abs(cxA[id]) / dx + (ny > 1 ? Math.abs(cyA[id]) / dx : 0) + Math.abs(ctA[id]) / dTheta;
                    lokal[id] = r > 0 ? Math.min(dtMax, courant / r) : dtMax;
                }
                ausbreitung(0, lokal);
                if (q) quellen(0, lokal, n2, nachQ);
                if (bindungM0 != null) binden(0, lokal);
                double groesste = 0, hmax = 0;
                for (int c = 0; c < nc; c++) {
                    double h = hs(c);
                    groesste = Math.max(groesste, Math.abs(h - alt[c]));
                    hmax = Math.max(hmax, h);
                    alt[c] = h;
                }
                if (it > 2 && groesste <= toleranz * Math.max(1e-9, hmax)) return it;
            }
            return hoechstens;
        } finally {
            buchen = true;
            Arrays.fill(buch, 0);
        }
    }

    // ------------------------------------------------------------------ Bindung

    /** Ist die Zelle nach der eigenen Regel frei (ein Block mit einer trockenen Zelle oder Wand in ihm oder um ihn)? */
    private boolean[] freiNachRegel() {
        int bs = Math.max(1, bindungBlock), bx = (nx + bs - 1) / bs, by = (ny + bs - 1) / bs;
        boolean[] hart = new boolean[bx * by];
        double[] hs = new double[bx * by], hq = new double[bx * by];
        int[] z = new int[bx * by];
        for (int c = 0; c < nc; c++) {
            int blk = (c / nx / bs) * bx + (c % nx) / bs;
            boolean w = !nass[c] || (wand != null && wand[c]);
            if (w) hart[blk] = true;
            double t = tiefe(c);
            hs[blk] += t; hq[blk] += t * t; z[blk]++;
        }
        boolean[] flach = new boolean[bx * by];
        for (int blk = 0; blk < bx * by; blk++) {
            double m = hs[blk] / Math.max(1, z[blk]), sd = Math.sqrt(Math.max(0, hq[blk] / Math.max(1, z[blk]) - m * m));
            flach[blk] = m <= bindungTiefe || sd > bindungStreuung * m;
        }
        boolean[] frei = new boolean[nc];
        for (int c = 0; c < nc; c++) {
            int ib = (c % nx) / bs, jb = (c / nx) / bs;
            boolean f = flach[jb * bx + ib];
            for (int dj = -1; dj <= 1 && !f; dj++)
                for (int di = -1; di <= 1 && !f; di++) {
                    int ii = ib + di, jj = jb + dj;
                    if (ii >= 0 && ii < bx && jj >= 0 && jj < by && hart[jj * bx + ii]) f = true;
                }
            frei[c] = f;
        }
        return frei;
    }

    private void binden(double dt0, double[] lokal) {
        int bs = Math.max(1, bindungBlock), bx = (nx + bs - 1) / bs, by = (ny + bs - 1) / bs, m = nb * nd, nBil = bilanz.length;
        boolean[] frei = ohneBindung != null ? ohneBindung : freiNachRegel();
        if (bindungSpektrum != null) {                                         // je Stützstelle zum Spektrum des Ziels, implizit
            IntStream.range(0, nc).parallel().forEach(c -> {
                if (!nass[c] || frei[c]) return;
                double vor = 0;
                for (int x = c * m; x < (c + 1) * m; x++) {
                    if (!(sig[x] > 0)) continue;
                    double a = (lokal != null ? lokal[x] : dt0) / bindungZeit, z = bindungSpektrum[x] / sig[x];
                    double neu = (n[x] + a * z) / (1 + a);
                    vor += neu - n[x];
                    n[x] = neu;
                }
                buch[c * nBil + 10] += vor;
            });
            buche();
            return;
        }
        IntStream.range(0, bx * by).parallel().forEach(blk -> {
            int ib = blk % bx, jb = blk / bx;
            double m0 = 0, m0z = 0;
            int z = 0;
            for (int j = jb * bs; j < Math.min(ny, jb * bs + bs); j++)
                for (int i = ib * bs; i < Math.min(nx, ib * bs + bs); i++) {
                    int c = j * nx + i;
                    if (!nass[c] || frei[c]) continue;
                    m0 += m0(c);
                    m0z += bindungM0[c];
                    z++;
                }
            if (z == 0 || m0 <= 1e-12 || !(m0z >= 0)) return;
            double rel = m0z / m0 - 1;                                          // je Δt/τ
            for (int j = jb * bs; j < Math.min(ny, jb * bs + bs); j++)
                for (int i = ib * bs; i < Math.min(nx, ib * bs + bs); i++) {
                    int c = j * nx + i;
                    if (!nass[c] || frei[c]) continue;
                    double vor = 0;
                    for (int x = c * m; x < (c + 1) * m; x++) {
                        double fak = Math.max(0.5, Math.min(2, 1 + (lokal != null ? lokal[x] : dt0) / bindungZeit * rel));
                        vor += (fak - 1) * n[x];
                        n[x] *= fak;
                    }
                    buch[c * nBil + 10] += vor;
                }
        });
        buche();
    }

    /** Rechnet bis zum Gleichgewicht: Schritte von dt, bis sich Hs in keiner Zelle um mehr als {@code toleranz} (relativ) ändert; gibt die Schritte zurück. */
    public int stationaer(double dt, int hoechstens, double toleranz) {
        double[] alt = new double[nc];
        for (int c = 0; c < nc; c++) alt[c] = hs(c);
        for (int it = 1; it <= hoechstens; it++) {
            schritt(dt);
            double groesste = 0, hmax = 0;
            for (int c = 0; c < nc; c++) {
                double h = hs(c);
                groesste = Math.max(groesste, Math.abs(h - alt[c]));
                hmax = Math.max(hmax, h);
                alt[c] = h;
            }
            if (it > 2 && groesste <= toleranz * Math.max(1e-9, hmax)) return it;
        }
        return hoechstens;
    }

    private static int spiegelX(int q, int nd) { return Math.floorMod(nd / 2 - q, nd); }

    private static int spiegelY(int q, int nd) { return Math.floorMod(-q, nd); }

    /**
     * Reflexionsbeiwert K_r (Amplitude) an der Fläche zwischen der nassen Zelle c und der trockenen Zelle z für Band b;
     * (ex, ey) die Richtung von c zu z. Die Höhe H ist die der einlaufenden Anteile (Laufrichtung zur Kante hin), wie bei
     * Battjes (1974); tan α aus dem Höhenunterschied des Grunds.
     */
    double reflexion(int c, int z, int b, double ex, double ey) {
        if (!mitReflexion) return 0;
        if (wand != null && wand[z]) return 1;
        double tana = Math.max(1e-6, (grund[z] - grund[c]) / dx);
        double m = 0;
        for (int bb = 0; bb < nb; bb++) for (int q = 0; q < nd; q++) if (cosT[q] * ex + sinT[q] * ey > 1e-9) m += energie(c, bb, q);
        double hs = 4 * Math.sqrt(m);
        if (hs <= 1e-9) return 1;
        double l0 = G / (2 * Math.PI * f[b] * f[b]);
        double xi = tana / Math.sqrt(hs / l0);
        return Math.min(1, 0.1 * xi * xi);
    }

    private void ausbreitung(double h0, double[] lokal) {
        final double[] a = n, out = n2;
        final int nBil = bilanz.length;
        IntStream.range(0, nc).parallel().forEach(c -> {
            int base = c * nb * nd;
            if (!nass[c]) { Arrays.fill(out, base, base + nb * nd, 0); return; }
            int i = c % nx, j = c / nx;
            int cw = i > 0 ? c - 1 : -1, co = i < nx - 1 ? c + 1 : -1, cs = nachbarS(c), cn = nachbarN(c);
            int[] nbx = {cw, co}, nby = {cs, cn};
            double ein0 = 0, kante = 0, block = 0, begrenzt = 0;
            double[] neu = new double[nb * nd];
            for (int b = 0; b < nb; b++) {
                double[] krx = new double[2], kry = new double[2];                // K_r² an West/Ost, Süd/Nord, wenn trocken
                for (int sd = 0; sd < 2; sd++) {
                    krx[sd] = nbx[sd] >= 0 && !nass[nbx[sd]] ? Math.pow(reflexion(c, nbx[sd], b, sd == 0 ? -1 : 1, 0), 2) : 0;
                    kry[sd] = ny > 1 && nby[sd] >= 0 && !nass[nby[sd]] ? Math.pow(reflexion(c, nby[sd], b, 0, sd == 0 ? -1 : 1), 2) : 0;
                }
                for (int q = 0; q < nd; q++) {
                    int id = ix(c, b, q);
                    if (Double.isNaN(k[id])) continue;
                    double h = lokal != null ? lokal[id] : h0;
                    double e0 = a[id], de = 0;
                    for (int achse = 0; achse < (ny > 1 ? 2 : 1); achse++) {
                        double[] cA = achse == 0 ? cxA : cyA;
                        int[] nbs = achse == 0 ? nbx : nby;
                        double[] kr = achse == 0 ? krx : kry;
                        double vo = cA[id];
                        for (int sd = 0; sd < 2; sd++) {
                            int z = nbs[sd], sg = sd == 0 ? -1 : 1;
                            double aus;
                            if (z >= 0 && nass[z]) {
                                int iz = ix(z, b, q);
                                if (Double.isNaN(k[iz])) {                           // der Nachbar ist für diese Stützstelle blockiert
                                    aus = sg * vo > 0 ? sg * vo * e0 : 0;
                                    block -= aus * h / dx;
                                } else {
                                    double vf = sg * 0.5 * (vo + cA[iz]);
                                    aus = vf > 0 ? vf * e0 : vf * a[iz];
                                }
                            } else if (z >= 0) {                                    // trockene Zelle: Kante, Reflexion in dieselbe Zelle
                                double vf = sg * vo;
                                aus = vf > 0 ? vf * e0 : 0;
                                if (aus > 0) {
                                    int qs = achse == 0 ? spiegelX(q, nd) : spiegelY(q, nd);
                                    neu[b * nd + qs] += kr[sd] * aus * (lokal != null ? lokal[ix(c, b, qs)] : h0) / dx;
                                    kante -= (1 - kr[sd]) * aus * h / dx;
                                }
                            } else {                                                // Rand des Gebiets
                                double vf = sg * vo;
                                double ein = this.rand != null && vf < 0 ? this.rand.n(achse == 0 ? (sd == 0 ? -1 : nx) : i, achse == 0 ? j : (sd == 0 ? -1 : ny), b, q) : 0;
                                aus = vf > 0 ? vf * e0 : vf * ein;
                                ein0 -= aus * h / dx;
                            }
                            de -= aus * h / dx;
                        }
                    }
                    neu[b * nd + q] += e0 + de;
                }
                // ---- θ (begrenzte zweite Ordnung wie Waves)
                if (mitRefraktion || mitBeugung) {
                    double[] fl = new double[nd];                               // Fluss über die Fläche zwischen q und q+1
                    for (int q = 0; q < nd; q++) {
                        int qp = (q + 1) % nd, qm = (q + nd - 1) % nd, qpp = (q + 2) % nd;
                        double vq = 0.5 * (ctA[ix(c, b, q)] + ctA[ix(c, b, qp)]);
                        double hf = lokal != null ? Math.min(lokal[ix(c, b, q)], lokal[ix(c, b, qp)]) : h0;
                        fl[q] = fluss(vq, a[ix(c, b, qm)], a[ix(c, b, q)], a[ix(c, b, qp)], a[ix(c, b, qpp)], hf / dTheta);
                    }
                    for (int q = 0; q < nd; q++) {
                        int qm = (q + nd - 1) % nd;
                        neu[b * nd + q] -= (fl[q] - fl[qm]) * (lokal != null ? lokal[ix(c, b, q)] : h0) / dTheta;
                    }
                }
                // blockierte Stützstellen dieser Zelle halten nichts (etwa aus der Reflexion)
                for (int q = 0; q < nd; q++)
                    if (Double.isNaN(k[ix(c, b, q)]) && neu[b * nd + q] != 0) { block -= neu[b * nd + q]; neu[b * nd + q] = 0; }
            }
            for (int x = 0; x < nb * nd; x++) {
                if (neu[x] < 0) { begrenzt -= neu[x]; neu[x] = 0; }
                out[base + x] = neu[x];
            }
            buch[c * nBil] += ein0;
            buch[c * nBil + 6] += kante;
            buch[c * nBil + 7] += block;
            buch[c * nBil + 9] += begrenzt;
        });
        n = out;
        n2 = a;
        buche();
    }

    /** Fluss wie in Waves: aufwind, mit der Korrektur zweiter Ordnung nach Lax–Wendroff, begrenzt nach van Leer. */
    private static double fluss(double v, double ell, double el, double er, double err, double hd) {
        double auf = v > 0 ? el : er;
        double ab = v > 0 ? er : el, dahinter = v > 0 ? ell : err, d = ab - auf;
        if (d == 0) return v * auf;
        double a = auf - dahinter;
        if (a == 0 || a > 0 != d > 0) return v * auf;
        double phi = 2 * a / (a + d);
        return v * (auf + 0.5 * phi * Math.max(0, 1 - Math.abs(v) * hd) * d);
    }

    private void buche() {
        int m = bilanz.length;
        if (!buchen) { Arrays.fill(buch, 0); return; }
        for (int c = 0; c < nc; c++) for (int p = 0; p < m; p++) { bilanz[p] += buch[c * m + p] * dx * dx; buch[c * m + p] = 0; }
    }

    // ------------------------------------------------------------------ Quellen

    /**
     * Die Quellen, halb implizit: e₁ = (e* + Δt q) / (1 + Δt γ). Die Raten γ (und der exponentielle Teil des Winds)
     * kommen aus {@code basis}: Nach jedem Teilschritt ist das der Zustand vor der Ausbreitung, dann ist das
     * Gleichgewicht γ(e) e = T + q vom Schritt unabhängig (B6; sonst stiege die Senke mit e* = e + Δt T). Einmal je Schritt
     * (wie physics.waves) ist es der Zustand nach der Ausbreitung.
     */
    /**
     * Die Quellen, halb implizit e₁ = (e* + Δt q) / (1 + Δt γ) mit den Raten aus {@code basis}. Im Anlauf ({@code nachQ}
     * nicht null, B22) sind die Senken zudem um den Zustand nach den Quellen der Runde davor linearisiert:
     * e₁ = (e* + Δt q + Δt β e₁,vor) / (1 + Δt (γ + β)), β = Σ max(0, d ln γ / d ln λ) γ unter E → λ E (Schaumkronen 2,
     * Brechen aus Q_b, Pflanzen ½). Am Gleichgewicht ist e₁,vor = e₁, der Zustand also derselbe; mit großem Δt der
     * Pseudozeit schwingt das Brechen so nicht mehr von Runde zu Runde.
     */
    private void quellen(double dt0, double[] lokal, double[] basis, double[] nachQ) {
        final double[] a = n, v = basis;
        final int nBil = bilanz.length;
        IntStream.range(0, nc).parallel().forEach(c -> {
            dwc[c] = dbr[c] = dbf[c] = dveg[c] = 0;
            if (!nass[c]) return;
            double dtZelle = dt0;
            double h = tiefe(c);
            if (!(mitWind || mitSchaum || mitBrechen || mitReibung || mitPflanzen)) return;
            // Summen über die Stützstellen (Varianz E = σ N)
            double et = 0, eSig = 0, eK = 0, eS1 = 0;
            for (int b = 0; b < nb; b++)
                for (int q = 0; q < nd; q++) {
                    int id = ix(c, b, q);
                    if (Double.isNaN(k[id]) || sig[id] == 0) continue;
                    double e = sig[id] * v[id];
                    et += e; eSig += e / sig[id]; eK += e / Math.sqrt(k[id]); eS1 += e * sig[id];
                }
            double sigMittel = et > 0 ? et / eSig : 0, kMittel = et > 0 ? (et / eK) * (et / eK) : 0;
            double uw = Math.hypot(windU[c], windV[c]);
            double us = uw > 0 ? (uStern != null && uStern[c] > 0 ? uStern[c] : Waves.ustern(uw)) : 0, tw = Math.atan2(windV[c], windU[c]);
            double sPM = us > 0 ? 2 * Math.PI * 0.13 * G / (28 * us) : 0;
            double gam = mitSchaum && et > 0 ? cds * Math.pow(kMittel * Math.sqrt(et) / steilheitPM, 4) : 0;
            double gBrech = 0, mBrech = 0;
            if (mitBrechen && et > 0) {
                double hm = gammaBJ * h, bt = Math.sqrt(8 * et) / hm, qb = Waves.brecheranteil(bt);
                if (qb > 0) gBrech = 0.25 * alphaBJ * qb * (eS1 / et) / (2 * Math.PI) * hm * hm / et;
                mBrech = steigungBrechen(bt, qb);
            }
            double gVeg = 0;
            if (mitPflanzen && pflanzen != null && pflanzen[c] > 0 && et > 0 && kMittel > 0) {
                double hv = Math.min(h, halm != null ? halm[c] : h), kh = kMittel * h, kav = kMittel * hv;
                double hrms = Math.sqrt(8 * et);
                double sh = Math.sinh(kav), ch = Math.cosh(kh);
                double eps = 1 / (2 * Math.sqrt(Math.PI)) * RHO * pflanzen[c] * Math.pow(kMittel * G / (2 * sigMittel), 3)
                        * (sh * sh * sh + 3 * sh) / (3 * kMittel * ch * ch * ch) * hrms * hrms * hrms;        // W/m²
                gVeg = eps / (RHO * G * et);
            }
            double wind = 0, wc = 0, br = 0, bf = 0, vg = 0;
            for (int b = 0; b < nb; b++)
                for (int q = 0; q < nd; q++) {
                    int id = ix(c, b, q);
                    if (Double.isNaN(k[id]) || sig[id] == 0) continue;
                    double sg = sig[id], kk = k[id], e0 = sg * a[id];
                    // Wind (Leistung auf die Varianz der Stützstelle, m²/s)
                    double qw = 0;
                    if (mitWind && us > 0) {
                        double co = Math.cos(theta[q] - tw);
                        if (co > 0) {
                            double ph = sg / kk, filter = Math.exp(-Math.pow(sg / sPM, -4));
                            double dSigma = 2 * Math.PI * df[b];
                            double lin = 1.5e-3 / (G * G) * Math.pow(us * co, 4) * filter / (2 * Math.PI) * dSigma * dTheta;
                            double exp = Math.max(0, 0.25 * RHO_A / RHO * (28 * us / ph * co - 1)) * sg;
                            qw = lin + exp * sg * v[id];
                        }
                    }
                    double gs = gam > 0 ? gam * sigMittel * kk / kMittel : 0;
                    double gr = 0;
                    if (mitReibung) {
                        double kd = kk * h;
                        if (kd < 20) { double shh = Math.sinh(kd); gr = cReibung * sg * sg / (G * G * shh * shh); }
                    }
                    double summe = gs + gBrech + gr + gVeg;
                    double dt = lokal != null ? lokal[id] : dt0;
                    double e1;
                    if (nachQ != null) {
                        double beta = 2 * gs + mBrech * gBrech + 0.5 * gVeg;
                        e1 = (e0 + dt * qw + dt * beta * sg * nachQ[id]) / (1 + dt * (summe + beta));
                        nachQ[id] = e1 / sg;
                    } else e1 = (e0 + dt * qw) / (1 + dt * summe);
                    a[id] = e1 / sg;
                    double z = dt * e1;                                           // Verlust je Senke = Rate · e1 · dt (implizit)
                    wind += dt * qw / sg;
                    wc += gs * z; br += gBrech * z; bf += gr * z; vg += gVeg * z;
                    buch[c * nBil + 1] += dt * qw / sg;
                    buch[c * nBil + 2] -= gs * z / sg;
                    buch[c * nBil + 3] -= gBrech * z / sg;
                    buch[c * nBil + 4] -= gr * z / sg;
                    buch[c * nBil + 5] -= gVeg * z / sg;
                }
            // Kappung wie Waves (P8c, W7): H_rms ≤ γ h
            if (kappung && mitBrechen) {
                double m0 = 0;
                for (int b = 0; b < nb; b++) for (int q = 0; q < nd; q++) { int id = ix(c, b, q); if (sig[id] > 0) m0 += sig[id] * a[id]; }
                double grenze = gammaBJ * h * gammaBJ * h / 8;
                if (m0 > grenze) {
                    double fk = grenze / m0;
                    for (int b = 0; b < nb; b++)
                        for (int q = 0; q < nd; q++) {
                            int id = ix(c, b, q);
                            buch[c * nBil + 3] -= (1 - fk) * a[id];
                            a[id] *= fk;
                        }
                    br += (1 - fk) * m0;
                }
            }
            double r = lokal != null ? 0 : RHO * G / dtZelle;                   // im Anlauf keine Leistung
            dwc[c] = r * wc; dbr[c] = r * br; dbf[c] = r * bf; dveg[c] = r * vg;
        });
        buche();
    }

    /**
     * max(0, d ln γ_BJ / d ln λ) unter E → λ E: γ_BJ ∝ Q_b / E, H_rms ∝ √λ; aus 1 − Q = −b² ln Q folgt
     * d ln Q / d ln b = 2 (1 − Q) / (b² − Q). Über b = 1 ist Q = 1 und γ ∝ 1/λ, dann 0.
     */
    static double steigungBrechen(double b, double qb) {
        if (!(qb > 0) || qb >= 1 || b >= 1) return 0;
        double nen = b * b - qb;
        if (!(nen > 1e-12)) return 0;
        return Math.max(0, (1 - qb) / nen - 1);
    }

    // ------------------------------------------------------------------ Kennwerte

    /** Varianz der Stützstelle (m²). */
    public double energie(int c, int b, int q) { int id = ix(c, b, q); return sig[id] * n[id]; }

    public double m0(int c) {
        double s0 = 0;
        for (int b = 0; b < nb; b++) for (int q = 0; q < nd; q++) s0 += energie(c, b, q);
        return s0;
    }

    public double hs(int c) { return 4 * Math.sqrt(Math.max(0, m0(c))); }

    /** Mittlere Richtung (Grad, wohin, wie Waves.richtung); NaN ohne Energie. */
    public double richtung(int c) {
        double a = 0, b2 = 0;
        for (int b = 0; b < nb; b++) for (int q = 0; q < nd; q++) { double e = energie(c, b, q); a += e * cosT[q]; b2 += e * sinT[q]; }
        return a == 0 && b2 == 0 ? Double.NaN : Math.toDegrees(Math.atan2(b2, a));
    }

    /** Hs der Anteile, die in eine Halbebene laufen: cos(θ − richtung) > 0 (für die Reflexion). */
    public double hsNach(int c, double richtungGrad) {
        double t = Math.toRadians(richtungGrad), m = 0;
        for (int b = 0; b < nb; b++) for (int q = 0; q < nd; q++) if (Math.cos(theta[q] - t) > 1e-9) m += energie(c, b, q);
        return 4 * Math.sqrt(m);
    }

    /** Wirkung im Gebiet (m²·s·m²), die Größe der Bilanz. */
    public double wirkung() {
        double s0 = 0;
        for (double x : n) s0 += x;
        return s0 * dx * dx;
    }

    public double wellenzahl(int c, int b, int q) { return k[ix(c, b, q)]; }

    public double sigma(int c, int b, int q) { return sig[ix(c, b, q)]; }

    public double gruppe(int c, int b, int q) { return cg[ix(c, b, q)]; }

    /** s = √(1 + δ) der Beugung (1 ohne). */
    public double beugung(int c, int b) { return s[c * nb + b]; }

    public double rateZuletzt() { return rate; }

    // ------------------------------------------------------------------ Spektrum setzen, Ziel, Schachtel, Verschieben

    /** Ein Spektrum je Ort: die Varianz je Stützstelle (Band b, Richtung q) in {@code e} (Länge nb · nd), m². */
    public interface Ziel { void varianz(double x, double y, double[] e); }

    /**
     * Ein JONSWAP (γ 3,3) mit Hs, Tp und cos^(2s)((θ − θ_m)/2) um {@code wohin} (Grad), über jedes Band und jede Richtung
     * integriert, so dass die Summe hs²/16 ist (Anteile unter und über den Bändern fallen weg und sind nicht mitgezählt:
     * die Summe ist hs²/16 nur, was in die Bänder fällt). Für einen Ort ohne Gefälle des Spektrums.
     */
    public double[] jonswap(double hs, double tp, double wohin, double sBreite) {
        double[] e = new double[nb * nd];
        if (!(hs > 0) || !(tp > 0)) return e;
        double fp = 1 / tp, gesamt = 0;
        double[] ef = new double[nb];
        // die ganze Kurve, damit der Anteil in den Bändern richtig ist
        int teil = 2000;
        double fa = 0.2 * fp, fe = 6 * fp, lfa = Math.log(fa), lfe = Math.log(fe);
        for (int i = 0; i < teil; i++) {
            double l = lfa + (i + 0.5) * (lfe - lfa) / teil, ff = Math.exp(l), dff = ff * (lfe - lfa) / teil;
            double x = ff / fp, sg = ff <= fp ? 0.07 : 0.09;
            double sp = Math.pow(ff, -5) * Math.exp(-1.25 * Math.pow(x, -4)) * Math.pow(3.3, Math.exp(-(ff - fp) * (ff - fp) / (2 * sg * sg * fp * fp)));
            gesamt += sp * dff;
            for (int b = 0; b < nb; b++) {
                double lo = f[b] / Math.sqrt(stufe), hi = f[b] * Math.sqrt(stufe);
                if (ff >= lo && ff < hi) ef[b] += sp * dff;
            }
        }
        double[] dq = new double[nd];
        double sumD = 0, tm = Math.toRadians(wohin);
        for (int q = 0; q < nd; q++) {
            double dl = Math.IEEEremainder(theta[q] - tm, 2 * Math.PI);
            dq[q] = Math.pow(Math.cos(0.5 * dl), 2 * sBreite);
            sumD += dq[q];
        }
        double m0 = hs * hs / 16;
        for (int b = 0; b < nb; b++) for (int q = 0; q < nd; q++) e[b * nd + q] = m0 * ef[b] / gesamt * dq[q] / sumD;
        return e;
    }

    /**
     * Das Spektrum aus den Partitionen von {@code physics.waves} (`waves.sea`, `waves.swell`, Seegang Werkbuch S1): je
     * Teil {Hs (m), Tp (s), woher (Grad von Gitternord im Uhrzeigersinn), Richtungsbreite σ_θ (Grad, Kuik)} ein JONSWAP
     * mit cos^(2s)((θ − θ_m)/2), s = 2/σ_θ² − 1 (σ_θ in rad; begrenzt auf 1 bis 400). Teile ohne Höhe fallen weg.
     */
    public double[] partitionen(double[]... teile) {
        double[] e = new double[nb * nd];
        for (double[] t : teile) {
            if (t == null || !(t[0] > 0) || !(t[1] > 0) || !Double.isFinite(t[2])) continue;
            double sr = Math.toRadians(Double.isFinite(t[3]) && t[3] > 0 ? t[3] : 30);
            double sb = Math.max(1, Math.min(400, 2 / (sr * sr) - 1));
            double[] x = jonswap(t[0], t[1], Waves.wohin(t[2]), sb);
            for (int i = 0; i < e.length; i++) e[i] += x[i];
        }
        return e;
    }

    /** Ein Band und eine Richtung allein (für Prüfungen): Varianz hs²/16 in (b, q). */
    public double[] einzeln(double hs, int b, int q) {
        double[] e = new double[nb * nd];
        e[b * nd + q] = hs * hs / 16;
        return e;
    }

    /** Setzt jede nasse Zelle auf das Ziel (Wirkung = Varianz / σ der Stützstelle); nach vorbereiten(). */
    public void setze(Ziel z) {
        IntStream.range(0, nc).parallel().forEach(c -> {
            if (!nass[c]) return;
            double[] e = new double[nb * nd];
            z.varianz(x(c % nx), y(c / nx), e);
            for (int b = 0; b < nb; b++)
                for (int q = 0; q < nd; q++) {
                    int id = ix(c, b, q);
                    n[id] = sig[id] > 0 ? e[b * nd + q] / sig[id] : 0;
                }
        });
    }

    /**
     * Ein Rand aus einem Ziel: die Geisterzelle bekommt die Varianz des Ziels an ihrem Ort, geteilt durch σ der
     * benachbarten Zelle im Gitter. Die Varianzen werden je Geisterzelle einmal gerechnet und gehalten.
     */
    public Rand randAus(Ziel z) {
        int m = nb * nd;
        double[][] w = new double[2 * (nx + ny) + 4][];
        return (i, j, b, q) -> {
            int r = randNummer(i, j);
            double[] e = w[r];
            if (e == null) {
                e = new double[m];
                z.varianz(x0 + (i + 0.5) * dx, y0 + (j + 0.5) * dx, e);
                w[r] = e;
            }
            int c = Math.max(0, Math.min(nx - 1, i)) + nx * Math.max(0, Math.min(ny - 1, j));
            double sg = sig[ix(c, b, q)];
            if (!(sg > 0)) sg = omega[b];
            return e[b * nd + q] / sg;
        };
    }

    private int randNummer(int i, int j) {
        if (j < 0) return Math.max(0, Math.min(nx - 1, i));
        if (j >= ny) return nx + Math.max(0, Math.min(nx - 1, i));
        if (i < 0) return 2 * nx + j;
        return 2 * nx + ny + j;
    }

    /**
     * Schachtel: Rand des feinen Nahfelds aus dem groben, die Varianz je Stützstelle bilinear über die nassen Zellen des
     * groben an der Mitte jeder Geisterzelle, geteilt durch σ der benachbarten feinen Zelle. Beide mit denselben Bändern
     * und Richtungen.
     */
    public static Rand schachtel(Nahfeld grob, Nahfeld fein) {
        if (grob.nb != fein.nb || grob.nd != fein.nd) throw new IllegalArgumentException("Schachtel: verschiedene Stützstellen");
        int m = fein.nb * fein.nd;
        double[][] w = new double[2 * (fein.nx + fein.ny) + 4][];
        return (i, j, b, q) -> {
            int r = fein.randNummer(i, j);
            double[] e = w[r];
            if (e == null) {
                e = grob.varianzBei(fein.x0 + (i + 0.5) * fein.dx, fein.y0 + (j + 0.5) * fein.dx);
                w[r] = e;
            }
            int c = Math.max(0, Math.min(fein.nx - 1, i)) + fein.nx * Math.max(0, Math.min(fein.ny - 1, j));
            double sg = fein.sig[fein.ix(c, b, q)];
            if (!(sg > 0)) sg = fein.omega[b];
            return e[b * fein.nd + q] / sg;
        };
    }

    /** Varianz je Stützstelle an einem Ort, bilinear über die nassen Zellen (0 ohne nasse Zelle). */
    public double[] varianzBei(double x, double y) {
        int m = nb * nd;
        double[] e = new double[m];
        double fi = (x - x0) / dx - 0.5, fj = (y - y0) / dx - 0.5;
        int i0 = (int) Math.floor(fi), j0 = (int) Math.floor(fj);
        double a = fi - i0, bb = fj - j0, summe = 0;
        for (int dj = 0; dj < 2; dj++)
            for (int di = 0; di < 2; di++) {
                int i = Math.max(0, Math.min(nx - 1, i0 + di)), j = Math.max(0, Math.min(ny - 1, j0 + dj));
                int c = j * nx + i;
                if (!nass[c]) continue;
                double g = (di == 0 ? 1 - a : a) * (dj == 0 ? 1 - bb : bb);
                if (g <= 0) continue;
                summe += g;
                for (int x2 = 0; x2 < m; x2++) e[x2] += g * sig[c * m + x2] * n[c * m + x2];
            }
        if (summe > 0) for (int x2 = 0; x2 < m; x2++) e[x2] /= summe;
        return e;
    }

    /**
     * Verschiebt das Gitter um (di, dj) Zellen (das Auge ist weitergezogen): Die Wirkung der Zellen, die im Gitter bleiben,
     * zieht mit, die neuen Zellen bekommen das Ziel. Die Eingaben (Grund, Spiegel, Strömung, Wind …) setzt der Aufrufer für
     * das neue Gitter neu und ruft vorbereiten(); die Wirkung der neuen Zellen setzt diese Methode erst danach über
     * {@link #nachVerschieben}.
     */
    public void verschieben(int di, int dj) {
        double[] neu = new double[nn];
        int m = nb * nd;
        boolean[] alt = new boolean[nc];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int is = i + di, js = j + dj;
                if (is < 0 || is >= nx || js < 0 || js >= ny) continue;
                System.arraycopy(n, (js * nx + is) * m, neu, (j * nx + i) * m, m);
                alt[j * nx + i] = true;
            }
        n = neu;
        n2 = new double[nn];
        x0 += di * dx;
        y0 += dj * dx;
        neuGitter = alt;
    }

    private boolean[] neuGitter;

    /** Nach {@link #verschieben} und vorbereiten(): die Zellen, die neu ins Gitter kamen, bekommen das Ziel. */
    public void nachVerschieben(Ziel z) {
        if (neuGitter == null) return;
        boolean[] alt = neuGitter;
        IntStream.range(0, nc).parallel().forEach(c -> {
            if (alt[c] || !nass[c]) return;
            double[] e = new double[nb * nd];
            z.varianz(x(c % nx), y(c / nx), e);
            for (int b = 0; b < nb; b++) for (int q = 0; q < nd; q++) { int id = ix(c, b, q); n[id] = sig[id] > 0 ? e[b * nd + q] / sig[id] : 0; }
        });
        neuGitter = null;
    }
}
