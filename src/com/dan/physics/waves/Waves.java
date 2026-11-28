package com.dan.physics.waves;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Der Seegang als Bilanz der spektralen Wirkungsdichte (Werkbuch HalligHooge Teil A, 7.3 und 12.9, P8a): die Referenz
 * in doppelter Genauigkeit, auf einem rechtwinkligen Gitter aus nx × ny Zellen, je Zelle nf Frequenzen (logarithmisch
 * gestuft) × nd Richtungen (gleichmäßig über den Vollkreis, Richtung 0 = +x, gegen den Uhrzeigersinn; es ist die
 * Richtung, <i>in die</i> die Wellen laufen).
 * <p>
 * Gespeichert ist die Varianzdichte E(f, θ) in m²/(Hz·rad) ({@code waves.spec}); transportiert wird die Wirkung
 * N = E/σ (σ = relative Kreisfrequenz), wie in SWAN (Booij, Ris, Holthuijsen 1999):
 * <pre>
 * ∂N/∂t + ∂(c_x N)/∂x + ∂(c_y N)/∂y + ∂(c_θ N)/∂θ + ∂(c_σ N)/∂σ = S/σ
 * c_x = c_g cos θ + U,  c_y = c_g sin θ + V
 * c_θ = σ/sinh(2kd) · (sin θ ∂d/∂x − cos θ ∂d/∂y) + sin θ (cos θ ∂U/∂x + sin θ ∂V/∂x) − cos θ (cos θ ∂U/∂y + sin θ ∂V/∂y)
 * c_σ = kσ/sinh(2kd) · (U ∂d/∂x + V ∂d/∂y) − c_g k (cos²θ ∂U/∂x + sin θ cos θ (∂U/∂y + ∂V/∂x) + sin²θ ∂V/∂y)
 * </pre>
 * <b>Ausbreitung:</b> Flussform, explizit, mit so vielen Teilschritten, wie die Courant-Zahl verlangt; aufwind erster
 * Ordnung in x und y, in θ und σ dazu die Korrektur zweiter Ordnung, begrenzt nach van Leer ({@link #zweiteOrdnung}). Die Flussform hält die Wirkung auf Rundung genau; jeder Fluss über
 * einen Rand (Gebiet, Ufer, Enden der Frequenzachse) ist verbucht ({@link #bilanz}).
 * <p>
 * <b>Quellen</b> (je Zelle; Wind, Schaumkronen und Vierwellen halb implizit mit dem Begrenzer von WAM/SWAN, danach
 * Bodenreibung und Brechen implizit und ohne Begrenzer):
 * <ul>
 * <li>Wind: lineares Wachstum nach Cavaleri und Malanotte-Rizzoli (1981) mit dem Filter von Tolman (1992), dazu
 * exponentielles nach Komen, Hasselmann, Hasselmann (1984); u* aus U10 nach Wu (1982).</li>
 * <li>Schaumkronen: Komen et al. (1984) in der Fassung von WAM Zyklus 3.</li>
 * <li>Vierwellen-Wechselwirkung: DIA nach Hasselmann et al. (1985), mit der Tiefenskalierung von WAM.</li>
 * <li>Bodenreibung: JONSWAP (Hasselmann et al. 1973).</li>
 * <li>Brechen an der Tiefe: Battjes und Janssen (1978).</li>
 * </ul>
 * Dreiwellen-Wechselwirkung fehlt (12.9, Grenzen). Phasengemittelt: keine Beugung, keine einzelne Welle.
 * <p>
 * Kennt nur java.* (Schichtenvertrag, Werkbuch 4); die Anbindung an Gitter und Bus kommt mit P8b. Dieselben Formeln in
 * float auf der Karte: {@code gpu.WavesKarte} (Version 0.2).
 */
public final class Waves {
    public static final String VERSION = "0.3";
    public static final double G = 9.81, RHO_W = 1025, RHO_A = 1.28;

    public final int nx, ny, nf, nd, nc;
    public final double dx, dy;
    /** Frequenzen (Hz), Kreisfrequenzen (rad/s), Breite der Stufen (Hz); Verhältnis benachbarter Frequenzen. */
    public final double[] f, sig, df;
    public final double stufe;
    /** Richtungen (rad) und ihr Abstand. */
    public final double[] theta, cosT, sinT;
    public final double dTheta;

    /** Je Zelle: Wassertiefe (m), Strömung (m/s), Wind in 10 m (m/s). Nach einer Änderung von Tiefe oder Strömung: {@link #vorbereiten()}. */
    public final double[] tiefe, u, v, windU, windV;
    /** E(f, θ) in m²/(Hz·rad): Index ((zelle · nf) + m) · nd + k. */
    public double[] e;
    private double[] e2;

    // ---- Schalter und Größen [G]
    public boolean mitWind = true, mitSchaum = true, mitQuad = true, mitReibung = true, mitBrechen = true;
    public boolean mitRefraktion = true, mitVerschiebung = true;
    /** In y geschlossen im Kreis (für Fälle, die längs y gleich sind). */
    public boolean periodischY;
    /** Unter dieser Tiefe ist die Zelle für den Seegang trocken (m) [G]. */
    public double minTiefe = 0.05;
    /** Einlaufendes Spektrum an einem Rand des Gebiets; null: nichts läuft ein. */
    public interface Rand { double e(int i, int j, int m, int k); }
    public Rand randW, randO, randS, randN;
    /** Seiten des Rands wie {@code core.gitter.Gitter}: Süd, Ost, Nord, West. */
    public static final int SUED = 0, OST = 1, NORD = 2, WEST = 3;
    /** Randspektren je Randzelle ({@link #randAusFeld}), sonst null. */
    public double[] randFeld;
    /** Schubspannungsgeschwindigkeit u* je Zelle (m/s), etwa aus dem Windschub von ERA5 (P8b, W1); null oder ≤ 0: aus U10 nach Wu. */
    public double[] uStern;
    /** {@code waves.diss}: Leistung der Schaumkronen und des Brechens je Zelle im letzten Schritt der Quellen (W/m²). */
    public final double[] diss;
    /** Schaumkronen (Komen 1984, WAM 3). */
    public double cds = 2.36e-5, steilheitPM = Math.sqrt(3.02e-3);
    /** Bodenreibung JONSWAP (m²/s³) [G]: 0,038 nach Zijlema et al. (2012). */
    public double cReibung = 0.038;
    /** Brechen (Battjes–Janssen 1978). */
    public double alphaBJ = 1.0, gammaBJ = 0.73;
    /** Kappung nach dem Schritt der Quellen (P8c, W7): H_rms = √(8 m0) ≤ γ · d, nur mit Brechen. */
    public boolean kappung = true;
    /** DIA. */
    public double lambda = 0.25, cQuad = 3e7;
    /** Begrenzer: Anteil des Pegels von Phillips je Schritt (WAM, SWAN). */
    public double begrenzer = 0.1;
    /** Schranke der Drehgeschwindigkeit gegen die Ausbreitung (wie die Begrenzung der Refraktion in SWAN) [G]. */
    public double drehGrenze = 4;
    /** Abfall des Spektrums über der höchsten Frequenz: E ~ f^(−schweif). */
    public double schweif = 4;
    /** Richtung und Frequenz mit begrenzter zweiter Ordnung (sonst aufwind erster Ordnung) [G]. */
    public boolean zweiteOrdnung = true;
    /** Courant-Zahl der Teilschritte. */
    public double courant = 0.8;

    // ---- Abgeleitetes je Zelle (und Frequenz)
    private final boolean[] nass;
    private final double[] k, cg, rf;                       // rf = σ/sinh(2kd)
    private final double[] dTx, dTy, ux, uy, vx, vy;
    private boolean strom;
    private double rate;                                    // größte Summe |c|/Δ (1/s)

    // ---- Bilanz der Wirkung (m²·s·m²): Summe über E Δf Δθ / σ · dx dy
    /** 0 Rand (Gebiet und Ufer), 1 Enden der Frequenzachse, 2 Wind, 3 Schaumkronen, 4 Vierwellen, 5 Reibung, 6 Brechen, 7 Begrenzer. */
    public final double[] bilanz = new double[8];
    public static final String[] BILANZ = {"Rand", "Frequenzenden", "Wind", "Schaumkronen", "Vierwellen", "Bodenreibung", "Brechen", "Begrenzer"};
    private final double[] buch;                            // je Zelle 8 Posten
    public double zeit;
    /** Teilschritte der Ausbreitung im letzten Schritt. */
    public int teilschritte;

    // ---- DIA: Stützstellen
    private final int[] qmP = new int[2], qmM = new int[2];
    private final double[] qwP = new double[2], qwM = new double[2];
    private final int[][] qkP = new int[2][2], qkM = new int[2][2];
    private final double[][] qvP = new double[2][2], qvM = new double[2][2];
    private final int qOben;

    public Waves(int nx, int ny, double dx, double dy, int nf, double fMin, double fMax, int nd) {
        this.nx = nx; this.ny = ny; this.dx = dx; this.dy = dy; this.nf = nf; this.nd = nd; nc = nx * ny;
        f = new double[nf]; sig = new double[nf]; df = new double[nf];
        stufe = nf > 1 ? Math.pow(fMax / fMin, 1.0 / (nf - 1)) : 1.1;
        for (int m = 0; m < nf; m++) {
            f[m] = fMin * Math.pow(stufe, m);
            sig[m] = 2 * Math.PI * f[m];
            df[m] = f[m] * (Math.sqrt(stufe) - 1 / Math.sqrt(stufe));
        }
        theta = new double[nd]; cosT = new double[nd]; sinT = new double[nd];
        dTheta = 2 * Math.PI / nd;
        for (int q = 0; q < nd; q++) { theta[q] = q * dTheta; cosT[q] = Math.cos(theta[q]); sinT[q] = Math.sin(theta[q]); }
        tiefe = new double[nc]; u = new double[nc]; v = new double[nc]; windU = new double[nc]; windV = new double[nc];
        e = new double[nc * nf * nd]; e2 = new double[nc * nf * nd];
        nass = new boolean[nc]; k = new double[nc * nf]; cg = new double[nc * nf]; rf = new double[nc * nf];
        dTx = new double[nc]; dTy = new double[nc]; ux = new double[nc]; uy = new double[nc]; vx = new double[nc]; vy = new double[nc];
        buch = new double[nc * 8];
        diss = new double[nc];
        // DIA: f(1+λ), f(1−λ) und die Winkel ±11,48°, ∓33,56° als Bruchteile der Stufen
        double ls = Math.log(stufe);
        double aP = Math.log(1 + lambda) / ls, aM = Math.log(1 - lambda) / ls;
        qmP[0] = (int) Math.floor(aP); qwP[1] = aP - qmP[0]; qwP[0] = 1 - qwP[1]; qmP[1] = qmP[0] + 1;
        qmM[0] = (int) Math.floor(aM); qwM[1] = aM - qmM[0]; qwM[0] = 1 - qwM[1]; qmM[1] = qmM[0] + 1;
        qOben = -qmM[0];
        double tP = Math.toRadians(11.48) / dTheta, tM = Math.toRadians(33.56) / dTheta;
        for (int s = 0; s < 2; s++) {
            double p = s == 0 ? tP : -tP, mi = s == 0 ? -tM : tM;
            int p0 = (int) Math.floor(p), m0 = (int) Math.floor(mi);
            qkP[s][0] = p0; qkP[s][1] = p0 + 1; qvP[s][1] = p - p0; qvP[s][0] = 1 - qvP[s][1];
            qkM[s][0] = m0; qkM[s][1] = m0 + 1; qvM[s][1] = mi - m0; qvM[s][0] = 1 - qvM[s][1];
        }
    }

    public int ix(int c, int m, int q) { return (c * nf + m) * nd + q; }

    /** Zahl der Randzellen: 2 · (nx + ny), in der Folge von {@code core.gitter.Gitter#rand}. */
    public int randZellen() { return 2 * (nx + ny); }

    /** Index der Randzelle zur Seite ({@link #SUED} …) und zur Zahl längs der Seite (i bei Süd und Nord, j bei Ost und West), wie {@code Gitter.rand}. */
    public int randIndex(int seite, int k) {
        return switch (seite) {
            case SUED -> k;
            case OST -> nx + k;
            case NORD -> nx + ny + (nx - 1 - k);
            case WEST -> 2 * nx + ny + (ny - 1 - k);
            default -> throw new IllegalArgumentException("Seite " + seite);
        };
    }

    /**
     * Setzt das einlaufende Spektrum je Randzelle (P8b): {@code feld} hat {@link #randZellen()} · nf · nd Werte, Index
     * (r · nf + m) · nd + q. Alle vier Seiten lesen daraus; wo nichts einlaufen soll, steht 0. Das Feld wird nicht kopiert.
     */
    public void randAusFeld(double[] feld) {
        if (feld.length != randZellen() * nf * nd) throw new IllegalArgumentException("Randfeld: " + feld.length + " Werte statt " + randZellen() * nf * nd);
        randFeld = feld;
        randS = (i, j, m, q) -> feld[(randIndex(SUED, i) * nf + m) * nd + q];
        randO = (i, j, m, q) -> feld[(randIndex(OST, j) * nf + m) * nd + q];
        randN = (i, j, m, q) -> feld[(randIndex(NORD, i) * nf + m) * nd + q];
        randW = (i, j, m, q) -> feld[(randIndex(WEST, j) * nf + m) * nd + q];
    }

    /** Wellenzahl (rad/m) zur Kreisfrequenz σ in der Tiefe d (lineare Dispersion, Newton). */
    public static double wellenzahl(double sigma, double d) {
        double k0 = sigma * sigma / G, x = k0 * d;
        if (x > 30) return k0;
        double y = x < 1 ? Math.sqrt(x) : x;                                      // y = kd
        for (int it = 0; it < 50; it++) {
            double t = Math.tanh(y), fy = y * t - x, dy = t + y * (1 - t * t), s = fy / dy;
            y -= s;
            if (Math.abs(s) < 1e-14 * y) break;
        }
        return y / d;
    }

    /** Gruppengeschwindigkeit (m/s). */
    public static double gruppe(double sigma, double kk, double d) {
        double kd = kk * d;
        double n = kd > 30 ? 0.5 : 0.5 * (1 + 2 * kd / Math.sinh(2 * kd));
        return n * sigma / kk;
    }

    /** Rechnet Wellenzahl, Gruppengeschwindigkeit, Gefälle und die Zahl der Teilschritte aus Tiefe und Strömung. */
    public void vorbereiten() {
        strom = false;
        for (int c = 0; c < nc; c++) {
            nass[c] = tiefe[c] >= minTiefe;
            if (u[c] != 0 || v[c] != 0) strom = true;
        }
        IntStream.range(0, nc).parallel().forEach(c -> {
            if (!nass[c]) return;
            double d = tiefe[c];
            for (int m = 0; m < nf; m++) {
                double kk = wellenzahl(sig[m], d);
                k[c * nf + m] = kk;
                cg[c * nf + m] = gruppe(sig[m], kk, d);
                double zkd = 2 * kk * d;
                rf[c * nf + m] = zkd > 60 ? 0 : sig[m] / Math.sinh(zkd);
            }
            int i = c % nx, j = c / nx;
            int w = i > 0 && nass[c - 1] ? c - 1 : c, o = i < nx - 1 && nass[c + 1] ? c + 1 : c;
            int s = j > 0 ? c - nx : periodischY ? c + (ny - 1) * nx : c, n = j < ny - 1 ? c + nx : periodischY ? c - (ny - 1) * nx : c;
            if (!nass[s]) s = c;
            if (!nass[n]) n = c;
            double lx = (o % nx - w % nx) * dx, ly = (n == s ? 0 : (n == c || s == c ? 1 : 2)) * dy;
            dTx[c] = lx > 0 ? (tiefe[o] - tiefe[w]) / lx : 0; dTy[c] = ly > 0 ? (tiefe[n] - tiefe[s]) / ly : 0;
            ux[c] = lx > 0 ? (u[o] - u[w]) / lx : 0; uy[c] = ly > 0 ? (u[n] - u[s]) / ly : 0;
            vx[c] = lx > 0 ? (v[o] - v[w]) / lx : 0; vy[c] = ly > 0 ? (v[n] - v[s]) / ly : 0;
        });
        rate = IntStream.range(0, nc).parallel().mapToDouble(c -> {
            if (!nass[c]) return 0;
            double r = 0;
            double[] ct = new double[nd];
            for (int m = 0; m < nf; m++) {
                dreh(c, m, ct);
                for (int q = 0; q < nd; q++) {
                    double a = Math.abs(cg[c * nf + m] * cosT[q] + u[c]) / dx + (ny > 1 ? Math.abs(cg[c * nf + m] * sinT[q] + v[c]) / dy : 0)
                            + Math.abs(ct[q]) / dTheta + (strom && mitVerschiebung ? Math.abs(schub(c, m, q)) / (2 * Math.PI * df[m]) : 0);
                    r = Math.max(r, a);
                }
            }
            return r;
        }).max().orElse(0);
    }

    /** c_θ für alle Richtungen in Zelle c, Frequenz m (begrenzt). */
    private void dreh(int c, int m, double[] ct) {
        if (!mitRefraktion) { Arrays.fill(ct, 0); return; }
        double r = rf[c * nf + m], g = cg[c * nf + m];
        for (int q = 0; q < nd; q++) {
            double co = cosT[q], si = sinT[q];
            double w = r * (si * dTx[c] - co * dTy[c]);
            if (strom) w += si * (co * ux[c] + si * vx[c]) - co * (co * uy[c] + si * vy[c]);
            double grenze = drehGrenze * dTheta * (Math.abs(g * co + u[c]) / dx + Math.abs(g * si + v[c]) / dy);
            ct[q] = Math.max(-grenze, Math.min(grenze, w));
        }
    }

    /** c_σ in Zelle c, Frequenz m, Richtung q. */
    private double schub(int c, int m, int q) {
        double co = cosT[q], si = sinT[q], kk = k[c * nf + m];
        return kk * (rf[c * nf + m] * (u[c] * dTx[c] + v[c] * dTy[c]) - cg[c * nf + m] * (co * co * ux[c] + si * co * (uy[c] + vx[c]) + si * si * vy[c]));
    }

    /** Ein Schritt: Ausbreitung in Teilschritten, dann die Quellen. */
    public void schritt(double dt) {
        teilschritte = Math.max(1, (int) Math.ceil(dt * rate / courant));
        double h = dt / teilschritte;
        for (int s = 0; s < teilschritte; s++) ausbreitung(h);
        if (mitWind || mitSchaum || mitQuad || mitReibung || mitBrechen) quellen(dt);
        zeit += dt;
    }

    private void buche() {
        for (int c = 0; c < nc; c++) for (int p = 0; p < 8; p++) { bilanz[p] += buch[c * 8 + p] * dx * dy; buch[c * 8 + p] = 0; }
    }

    private void ausbreitung(double h) {
        final double[] a = e, b = e2;
        final boolean sigma = strom && mitVerschiebung;
        IntStream.range(0, nc).parallel().forEach(c -> {
            if (!nass[c]) { Arrays.fill(b, c * nf * nd, (c + 1) * nf * nd, 0); return; }
            int i = c % nx, j = c / nx;
            int cw = i > 0 ? c - 1 : -1, co = i < nx - 1 ? c + 1 : -1;
            int cs = j > 0 ? c - nx : periodischY ? c + (ny - 1) * nx : -1, cn = j < ny - 1 ? c + nx : periodischY ? c - (ny - 1) * nx : -1;
            double[] ct = new double[nd];
            double rand = 0, enden = 0;
            for (int m = 0; m < nf; m++) {
                double g = cg[c * nf + m], wirk = df[m] * dTheta / sig[m];
                dreh(c, m, ct);
                for (int q = 0; q < nd; q++) {
                    int id = ix(c, m, q);
                    double e0 = a[id], de = 0;
                    // ---- x
                    double vo = g * cosT[q] + u[c];
                    for (int s = -1; s <= 1; s += 2) {
                        int nb = s < 0 ? cw : co;
                        double aus;
                        if (nb >= 0 && nass[nb]) {
                            double vf = s * 0.5 * (vo + cg[nb * nf + m] * cosT[q] + u[nb]);
                            aus = vf > 0 ? vf * e0 : vf * a[ix(nb, m, q)];
                        } else {
                            double vf = s * vo;
                            Rand r = nb < 0 ? (s < 0 ? randW : randO) : null;
                            aus = vf > 0 ? vf * e0 : r != null ? vf * r.e(i, j, m, q) : 0;
                            rand -= aus * h / dx * wirk;
                        }
                        de -= aus * h / dx;
                    }
                    // ---- y
                    if (ny > 1) {
                        vo = g * sinT[q] + v[c];
                        for (int s = -1; s <= 1; s += 2) {
                            int nb = s < 0 ? cs : cn;
                            double aus;
                            if (nb >= 0 && nass[nb]) {
                                double vf = s * 0.5 * (vo + cg[nb * nf + m] * sinT[q] + v[nb]);
                                aus = vf > 0 ? vf * e0 : vf * a[ix(nb, m, q)];
                            } else {
                                double vf = s * vo;
                                Rand r = nb < 0 ? (s < 0 ? randS : randN) : null;
                                aus = vf > 0 ? vf * e0 : r != null ? vf * r.e(i, j, m, q) : 0;
                                rand -= aus * h / dy * wirk;
                            }
                            de -= aus * h / dy;
                        }
                    }
                    // ---- θ
                    if (mitRefraktion) {
                        int qp = q + 1 < nd ? q + 1 : 0, qm = q > 0 ? q - 1 : nd - 1, qpp = qp + 1 < nd ? qp + 1 : 0, qmm = qm > 0 ? qm - 1 : nd - 1;
                        double vp = 0.5 * (ct[q] + ct[qp]), vm = 0.5 * (ct[qm] + ct[q]), hd = h / dTheta;
                        double eP = a[ix(c, m, qp)], eM = a[ix(c, m, qm)];
                        double fp = fluss(vp, eM, e0, eP, a[ix(c, m, qpp)], hd), fm = fluss(vm, a[ix(c, m, qmm)], eM, e0, eP, hd);
                        de -= (fp - fm) * h / dTheta;
                    }
                    // ---- σ: Fluss der Wirkung E/σ
                    if (sigma) {
                        double c0 = schub(c, m, q), ds = 2 * Math.PI * df[m];
                        double fp, fm;
                        if (m + 1 < nf) {
                            double vp = 0.5 * (c0 + schub(c, m + 1, q)), nR = a[ix(c, m + 1, q)] / sig[m + 1];
                            double nLL = m > 0 ? a[ix(c, m - 1, q)] / sig[m - 1] : e0 / sig[m];
                            double nRR = m + 2 < nf ? a[ix(c, m + 2, q)] / sig[m + 2] : nR * Math.pow(stufe, -schweif - 1);
                            fp = fluss(vp, nLL, e0 / sig[m], nR, nRR, h / ds);
                        } else {
                            fp = c0 > 0 ? c0 * e0 / sig[m] : c0 * e0 * Math.pow(stufe, -schweif - 1) / sig[m];
                            enden -= fp * h / ds * sig[m] * wirk;
                        }
                        if (m > 0) {
                            double vm = 0.5 * (c0 + schub(c, m - 1, q)), nL = a[ix(c, m - 1, q)] / sig[m - 1];
                            double nLL = m > 1 ? a[ix(c, m - 2, q)] / sig[m - 2] : nL;
                            double nRR = m + 1 < nf ? a[ix(c, m + 1, q)] / sig[m + 1] : e0 / sig[m] * Math.pow(stufe, -schweif - 1);
                            fm = fluss(vm, nLL, nL, e0 / sig[m], nRR, h / (2 * Math.PI * df[m - 1]));
                        } else {
                            fm = c0 < 0 ? c0 * e0 / sig[m] : 0;
                            enden += fm * h / ds * sig[m] * wirk;
                        }
                        de -= (fp - fm) * h / ds * sig[m];
                    }
                    b[id] = e0 + de;
                }
            }
            buch[c * 8] += rand;
            buch[c * 8 + 1] += enden;
        });
        e = b; e2 = a;
        buche();
    }

    /**
     * Fluss über die Fläche zwischen L und R (LL, RR dahinter) bei der Geschwindigkeit v: aufwind, mit der Korrektur
     * zweiter Ordnung nach Lax–Wendroff, begrenzt nach van Leer ({@link #zweiteOrdnung}); hd = Schritt / Weite.
     */
    private double fluss(double v, double ell, double el, double er, double err, double hd) {
        double auf = v > 0 ? el : er;
        if (!zweiteOrdnung) return v * auf;
        double ab = v > 0 ? er : el, dahinter = v > 0 ? ell : err, d = ab - auf;
        if (d == 0) return v * auf;
        double a = auf - dahinter;
        if (a == 0 || a > 0 != d > 0) return v * auf;                             // Extremum: erste Ordnung
        double phi = 2 * a / (a + d);                                            // van Leer, 2r/(1 + r) mit r = a/d; so auch bei winzigem d endlich
        return v * (auf + 0.5 * phi * Math.max(0, 1 - Math.abs(v) * hd) * d);
    }

    /** Anteil brechender Wellen nach Battjes und Janssen (1978): 1 − Q = −β² ln Q, β = H_rms/H_m. */
    public static double brecheranteil(double beta) {
        if (beta >= 1) return 1;
        if (beta < 0.15) return 0;
        double b2 = beta * beta, lo = 0, hi = 1 - Math.min(0.5, (1 - b2) / b2);
        for (int it = 0; it < 80; it++) {
            double q = 0.5 * (lo + hi);
            if (1 - q + b2 * Math.log(q) > 0) hi = q; else lo = q;
        }
        return 0.5 * (lo + hi);
    }

    /** Schubspannungsgeschwindigkeit u* (m/s) aus U10 nach Wu (1982). */
    public static double ustern(double u10) {
        double cd = u10 < 7.5 ? 1.2875e-3 : (0.8 + 0.065 * u10) * 1e-3;
        return Math.sqrt(cd) * u10;
    }

    private void quellen(double dt) {
        final double[] a = e;
        final double l1 = 1 + lambda, l2 = 1 - lambda;
        final double q1 = 1 / Math.pow(l1, 4), q2 = 1 / Math.pow(l2, 4), q3 = 2 / Math.pow(1 - lambda * lambda, 4);
        final double zug = Math.pow(stufe, -schweif);
        final double[] fP = {l1 * Math.pow(stufe, -qmP[0]), l1 * Math.pow(stufe, -qmP[1])}, fM = {l2 * Math.pow(stufe, -qmM[0]), l2 * Math.pow(stufe, -qmM[1])};
        final double[] f11 = new double[nf + qOben];
        for (int m = 0; m < f11.length; m++) f11[m] = Math.pow(f[0] * Math.pow(stufe, m), 11);
        IntStream.range(0, nc).parallel().forEach(c -> {
            if (!nass[c]) { diss[c] = 0; return; }
            double d = tiefe[c];
            int n = nf * nd, basis = c * n;
            // ---- Summen
            double et = 0, eSig = 0, eK = 0, eS1 = 0;
            for (int m = 0; m < nf; m++) {
                double s = 0;
                for (int q = 0; q < nd; q++) s += a[basis + m * nd + q];
                s *= df[m] * dTheta;
                et += s; eSig += s / sig[m]; eK += s / Math.sqrt(k[c * nf + m]); eS1 += s * sig[m];
            }
            if (nf > 1) {                                                           // Schweif über der höchsten Frequenz
                double s = 0;
                for (int q = 0; q < nd; q++) s += a[basis + (nf - 1) * nd + q];
                s *= dTheta * f[nf - 1] * Math.sqrt(stufe);                          // E an der Oberkante × f
                double zz = Math.pow(stufe, -0.5 * schweif);
                s *= zz;
                et += s / (schweif - 1); eSig += s / (schweif * sig[nf - 1]); eK += s / (schweif * Math.sqrt(k[c * nf + nf - 1])); eS1 += s * sig[nf - 1] / (schweif - 2);
            }
            double[] quelle = new double[n];
            double[] posten = new double[8];
            double[] sQuad = mitQuad ? new double[n] : null;
            double sigMittel = et > 0 ? et / eSig : 0, kMittel = et > 0 ? (et / eK) * (et / eK) : 0;
            // ---- Wind
            double uw = Math.hypot(windU[c], windV[c]);
            if (mitWind && uw > 0) {
                double us = uStern != null && uStern[c] > 0 ? uStern[c] : ustern(uw), tw = Math.atan2(windV[c], windU[c]);
                double sPM = 2 * Math.PI * 0.13 * G / (28 * us);
                for (int m = 0; m < nf; m++) {
                    double ph = sig[m] / k[c * nf + m], filter = Math.exp(-Math.pow(sig[m] / sPM, -4));
                    for (int q = 0; q < nd; q++) {
                        double co = Math.cos(theta[q] - tw);
                        if (co <= 0) continue;
                        double lin = 1.5e-3 / (G * G) * Math.pow(us * co, 4) * filter;
                        double exp = Math.max(0, 0.25 * RHO_A / RHO_W * (28 * us / ph * co - 1)) * sig[m];
                        quelle[m * nd + q] += lin + exp * a[basis + m * nd + q];
                    }
                }
            }
            double[] wind = quelle.clone();
            // ---- Schaumkronen
            double[] gSchaum = new double[nf];
            if (mitSchaum && et > 0) {
                double st = kMittel * Math.sqrt(et), gam = cds * Math.pow(st / steilheitPM, 4);
                for (int m = 0; m < nf; m++) gSchaum[m] = gam * sigMittel * k[c * nf + m] / kMittel;
            }
            // ---- Bodenreibung
            double[] gReib = new double[nf];
            if (mitReibung) for (int m = 0; m < nf; m++) {
                double kd = k[c * nf + m] * d;
                if (kd < 20) { double sh = Math.sinh(kd); gReib[m] = cReibung * sig[m] * sig[m] / (G * G * sh * sh); }
            }
            // ---- Brechen
            double gBrech = 0;
            if (mitBrechen && et > 0) {
                double hm = gammaBJ * d, qb = brecheranteil(Math.sqrt(8 * et) / hm);
                if (qb > 0) gBrech = 0.25 * alphaBJ * qb * (eS1 / et) / (2 * Math.PI) * hm * hm / et;
            }
            // ---- Vierwellen (DIA)
            if (mitQuad && et > 0) {
                double kpd = Math.max(0.5, 0.75 * kMittel * d);
                double skal = kpd > 20 ? 1 : 1 + 5.5 / kpd * (1 - 5.0 / 6.0 * kpd) * Math.exp(-1.25 * kpd);
                double vor = cQuad * skal / Math.pow(G, 4);
                for (int m = 0; m < nf + qOben; m++) {
                    double v11 = vor * f11[m];
                    for (int q = 0; q < nd; q++) {
                        double e0 = lies(a, basis, m, q, zug);
                        if (e0 <= 0) continue;
                        for (int s = 0; s < 2; s++) {
                            double ep = 0, em = 0;
                            for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) {
                                ep += qwP[x] * qvP[s][y] * lies(a, basis, m + qmP[x], q + qkP[s][y], zug);
                                em += qwM[x] * qvM[s][y] * lies(a, basis, m + qmM[x], q + qkM[s][y], zug);
                            }
                            double ds = v11 * (e0 * e0 * (ep * q1 + em * q2) - q3 * e0 * ep * em);
                            if (m < nf) sQuad[m * nd + q] -= 2 * ds;
                            for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) {
                                int mp = m + qmP[x], mm = m + qmM[x];
                                if (mp >= 0 && mp < nf) sQuad[mp * nd + Math.floorMod(q + qkP[s][y], nd)] += qwP[x] * qvP[s][y] * ds * fP[x];
                                if (mm >= 0 && mm < nf) sQuad[mm * nd + Math.floorMod(q + qkM[s][y], nd)] += qwM[x] * qvM[s][y] * ds * fM[x];
                            }
                        }
                    }
                }
            }
            // ---- Schritt: halb implizit in den Senken, dann der Begrenzer
            double verlust = 0;                                                     // Varianz je Sekunde aus Schaumkronen und Brechen (m²/s)
            for (int m = 0; m < nf; m++) {
                double kk = k[c * nf + m], wirk = df[m] * dTheta / sig[m];
                double grenze = 2 * Math.PI * begrenzer * 0.0081 / (2 * kk * kk * kk * cg[c * nf + m]);
                for (int q = 0; q < nd; q++) {
                    int o = m * nd + q;
                    double e0 = a[basis + o], n1 = 1 / (1 + dt * gSchaum[m]), n2 = 1 / (1 + dt * (gReib[m] + gBrech));
                    double sq = sQuad != null ? sQuad[o] : 0;
                    // tiefes Wasser (Wind, Schaumkronen, Vierwellen): halb implizit, begrenzt
                    double roh = dt * (quelle[o] + sq - gSchaum[m] * e0) * n1;
                    double e1 = Math.max(0, e0 + Math.max(-grenze, Math.min(grenze, roh)));
                    // Boden und Brandung: implizit, ohne Begrenzer (er hielte die Brandung auf; so auch SWAN)
                    double neu = e1 * n2;
                    a[basis + o] = neu;
                    double z = dt * n1 * wirk, z2 = dt * e1 * n2 * wirk;
                    posten[2] += wind[o] * z; posten[3] -= gSchaum[m] * e0 * z; posten[4] += sq * z;
                    posten[5] -= gReib[m] * z2; posten[6] -= gBrech * z2;
                    verlust += (gSchaum[m] * e0 * n1 + gBrech * e1 * n2) * df[m] * dTheta;
                    posten[7] += (e1 - e0 - roh) * wirk;
                }
            }
            // ---- Kappung (P8c, W7): H_rms = √(8 m0) höchstens γ d; das Spektrum der Zelle gleichmäßig skaliert, das
            // Abgezogene bucht die Bilanz unter „Brechen“ und zählt zur Leistung
            if (kappung && mitBrechen) {
                double m0 = 0;
                for (int m = 0; m < nf; m++) for (int q = 0; q < nd; q++) m0 += a[basis + m * nd + q] * df[m] * dTheta;
                double grenzeM0 = gammaBJ * d * gammaBJ * d / 8;
                if (m0 > grenzeM0) {
                    double fk = grenzeM0 / m0;
                    for (int m = 0; m < nf; m++) {
                        double wirk = df[m] * dTheta / sig[m];
                        for (int q = 0; q < nd; q++) {
                            int o = basis + m * nd + q;
                            posten[6] -= (1 - fk) * a[o] * wirk;
                            a[o] *= fk;
                        }
                    }
                    verlust += (1 - fk) * m0 / dt;
                }
            }
            for (int p = 2; p < 8; p++) buch[c * 8 + p] += posten[p];
            diss[c] = RHO_W * G * verlust;
        });
        buche();
    }

    private double lies(double[] a, int basis, int m, int q, double zug) {
        if (m < 0) return 0;
        q = Math.floorMod(q, nd);
        if (m < nf) return a[basis + m * nd + q];
        return a[basis + (nf - 1) * nd + q] * Math.pow(zug, m - nf + 1);
    }

    // ---------------------------------------------------------------- Kenngrößen

    /** Wirkung im Gebiet (m²·s·m²), die Größe der Bilanz. */
    public double wirkung() {
        double s = 0;
        for (int c = 0; c < nc; c++) for (int m = 0; m < nf; m++) {
            double z = 0;
            for (int q = 0; q < nd; q++) z += e[ix(c, m, q)];
            s += z * df[m] * dTheta / sig[m];
        }
        return s * dx * dy;
    }

    /** Varianz m0 (m²) in Zelle c. */
    public double m0(int c) {
        double s = 0;
        for (int m = 0; m < nf; m++) for (int q = 0; q < nd; q++) s += e[ix(c, m, q)] * df[m];
        return s * dTheta;
    }

    /** {@code waves.hs}: signifikante Wellenhöhe (m). */
    public double hs(int c) { return 4 * Math.sqrt(m0(c)); }

    /** Das Spektrum über der Frequenz (m²/Hz). */
    public double[] e1(int c) {
        double[] s = new double[nf];
        for (int m = 0; m < nf; m++) for (int q = 0; q < nd; q++) s[m] += e[ix(c, m, q)] * dTheta;
        return s;
    }

    /** {@code waves.tp}: Periode am Gipfel (s), Parabel durch die drei Stufen um den Gipfel (über ln f). */
    public double tp(int c) {
        double[] s = e1(c);
        int g = 0;
        for (int m = 1; m < nf; m++) if (s[m] > s[g]) g = m;
        if (s[g] <= 0) return Double.NaN;
        double versatz = 0;
        if (g > 0 && g < nf - 1) {
            double n = s[g - 1] - 2 * s[g] + s[g + 1];
            if (n < 0) versatz = 0.5 * (s[g - 1] - s[g + 1]) / n;
        }
        return 1 / (f[g] * Math.pow(stufe, versatz));
    }

    /** Mittlere Periode T_m01 (s). */
    public double tm01(int c) {
        double a = 0, b = 0;
        for (int m = 0; m < nf; m++) for (int q = 0; q < nd; q++) { double z = e[ix(c, m, q)] * df[m]; a += z; b += z * f[m]; }
        return b > 0 ? a / b : Double.NaN;
    }

    /** {@code waves.dir}: mittlere Richtung (Grad, 0 = +x, gegen den Uhrzeigersinn; wohin die Wellen laufen). */
    public double richtung(int c) {
        double a = 0, b = 0;
        for (int m = 0; m < nf; m++) for (int q = 0; q < nd; q++) { double z = e[ix(c, m, q)] * df[m]; a += z * cosT[q]; b += z * sinT[q]; }
        return a == 0 && b == 0 ? Double.NaN : Math.toDegrees(Math.atan2(b, a));
    }

    /** {@code waves.ubot}: Orbitalgeschwindigkeit am Boden, quadratisches Mittel (m/s). */
    public double ubot(int c) {
        double s = 0, d = tiefe[c];
        for (int m = 0; m < nf; m++) {
            double kd = k[c * nf + m] * d;
            if (kd > 20) continue;
            double sh = Math.sinh(kd), z = 0;
            for (int q = 0; q < nd; q++) z += e[ix(c, m, q)];
            s += z * df[m] * dTheta * sig[m] * sig[m] / (sh * sh);
        }
        return Math.sqrt(s);
    }

    /** {@code waves.mss}: mittlere quadratische Neigung im aufgelösten Bereich (–). */
    public double mss(int c) {
        double s = 0;
        for (int m = 0; m < nf; m++) {
            double z = 0;
            for (int q = 0; q < nd; q++) z += e[ix(c, m, q)];
            s += z * df[m] * dTheta * k[c * nf + m] * k[c * nf + m];
        }
        return s;
    }

    /** Strahlungsspannung {Sxx, Sxy, Syy} in N/m. */
    public double[] spannung(int c) {
        double xx = 0, xy = 0, yy = 0;
        if (!nass[c]) return new double[3];
        for (int m = 0; m < nf; m++) {
            double n = cg[c * nf + m] * k[c * nf + m] / sig[m];
            for (int q = 0; q < nd; q++) {
                double z = e[ix(c, m, q)] * df[m] * dTheta;
                xx += z * (n * cosT[q] * cosT[q] + n - 0.5); xy += z * n * cosT[q] * sinT[q]; yy += z * (n * sinT[q] * sinT[q] + n - 0.5);
            }
        }
        double r = RHO_W * G;
        return new double[] {r * xx, r * xy, r * yy};
    }

    /** {@code waves.force}: Kraft auf das Wasser je Fläche {Fx, Fy} in N/m², aus dem Gefälle der Strahlungsspannung. */
    public double[][] kraft() {
        double[][] s = new double[nc][];
        for (int c = 0; c < nc; c++) s[c] = spannung(c);
        double[][] k2 = new double[2][nc];
        for (int c = 0; c < nc; c++) {
            if (!nass[c]) continue;
            int i = c % nx, j = c / nx;
            int w = i > 0 && nass[c - 1] ? c - 1 : c, o = i < nx - 1 && nass[c + 1] ? c + 1 : c;
            int su = j > 0 ? c - nx : periodischY ? c + (ny - 1) * nx : c, n = j < ny - 1 ? c + nx : periodischY ? c - (ny - 1) * nx : c;
            if (!nass[su]) su = c;
            if (!nass[n]) n = c;
            double lx = (o % nx - w % nx) * dx, ly = (n == su ? 0 : (n == c || su == c ? 1 : 2)) * dy;
            double xxx = lx > 0 ? (s[o][0] - s[w][0]) / lx : 0, xyx = lx > 0 ? (s[o][1] - s[w][1]) / lx : 0;
            double xyy = ly > 0 ? (s[n][1] - s[su][1]) / ly : 0, yyy = ly > 0 ? (s[n][2] - s[su][2]) / ly : 0;
            k2[0][c] = -(xxx + xyy); k2[1][c] = -(xyx + yyy);
        }
        return k2;
    }

    // ---------------------------------------------------------------- Diagnose (seit 0.3, Seegang Werkbuch S1, SF6 A)
    // Nur lesend: Ausbreitung und Quellen bleiben, wie sie sind (die Rechnung ist bitgleich mit 0.2).

    /** Faktor β der Trennung von Windsee und Dünung nach dem Wellenalter, wie in WAM (ECMWF, Bidlot): Windsee, wo β · 28 u* / c · cos(θ − θ_w) > 1. */
    public static final double BETA_WINDSEE = 1.2;

    /**
     * Windsee und Dünung der Zelle c: Ein Anteil E(f, θ) gehört zur Windsee, wo {@link #BETA_WINDSEE} · 28 u* / c ·
     * cos(θ − θ_w) > 1 (c = σ/k die Phasengeschwindigkeit in der Tiefe der Zelle, θ_w die Richtung, in die der Wind weht,
     * u* aus {@link #uStern} oder nach Wu); der Rest ist Dünung. Ohne Wind ist alles Dünung.
     * <p>
     * Rückgabe {Hs, Tp, Richtung, Breite} der Windsee, danach dieselben vier der Dünung: Hs = 4 √m₀ (m), Tp aus der Parabel
     * um den Gipfel wie {@link #tp} (s, NaN ohne Energie), Richtung wie {@link #richtung} (Grad, wohin, 0 = +x, gegen den
     * Uhrzeigersinn; NaN ohne Energie), Richtungsbreite σ_θ = √(2 (1 − r)) in Grad, r die Länge des mittleren
     * Richtungsvektors (Kuik, van Vledder, Holthuijsen 1988). Die Summe der beiden m₀ ist m₀ der Zelle.
     */
    public double[] partitionen(int c) {
        double[] aus = new double[8];
        double uw = Math.hypot(windU[c], windV[c]);
        double us = uw > 0 ? (uStern != null && uStern[c] > 0 ? uStern[c] : ustern(uw)) : 0, tw = Math.atan2(windV[c], windU[c]);
        double[][] s1 = new double[2][nf];
        double[] m0 = new double[2], ex = new double[2], ey = new double[2];
        int basis = c * nf * nd;
        for (int m = 0; m < nf; m++) {
            double kk = k[c * nf + m], ph = nass[c] && kk > 0 ? sig[m] / kk : Double.POSITIVE_INFINITY;
            for (int q = 0; q < nd; q++) {
                double z = e[basis + m * nd + q];
                if (!(z > 0)) continue;
                int p = us > 0 && BETA_WINDSEE * 28 * us / ph * Math.cos(theta[q] - tw) > 1 ? 0 : 1;
                s1[p][m] += z * dTheta;
                double w = z * df[m] * dTheta;
                m0[p] += w;
                ex[p] += w * cosT[q];
                ey[p] += w * sinT[q];
            }
        }
        for (int p = 0; p < 2; p++) {
            aus[4 * p] = 4 * Math.sqrt(m0[p]);
            aus[4 * p + 1] = gipfel(s1[p]);
            if (m0[p] > 0) {
                aus[4 * p + 2] = Math.toDegrees(Math.atan2(ey[p], ex[p]));
                double r = Math.min(1, Math.hypot(ex[p], ey[p]) / m0[p]);
                aus[4 * p + 3] = Math.toDegrees(Math.sqrt(2 * (1 - r)));
            } else {
                aus[4 * p + 2] = Double.NaN;
                aus[4 * p + 3] = Double.NaN;
            }
        }
        return aus;
    }

    /** Periode am Gipfel (s) eines Spektrums über der Frequenz, wie {@link #tp}; NaN ohne Energie. */
    private double gipfel(double[] s) {
        int g = 0;
        for (int m = 1; m < nf; m++) if (s[m] > s[g]) g = m;
        if (!(s[g] > 0)) return Double.NaN;
        double versatz = 0;
        if (g > 0 && g < nf - 1) {
            double n = s[g - 1] - 2 * s[g] + s[g + 1];
            if (n < 0) versatz = 0.5 * (s[g - 1] - s[g + 1]) / n;
        }
        return 1 / (f[g] * Math.pow(stufe, versatz));
    }

    /**
     * Die Leistung der drei Senken in Zelle c, wie sie aus dem jetzigen Spektrum folgt (W/m²): {Schaumkronen, Brechen an
     * der Tiefe, Bodenreibung}. Dieselben Formeln wie im Schritt der Quellen ({@link #schritt}), aber als Rate ohne den
     * halb impliziten Nenner und ohne die Kappung: Für kleine Schritte ist sie gleich dem, was ein Schritt verliert. Die
     * Schalter ({@link #mitSchaum} …) gelten.
     */
    public double[] dissipation(int c) {
        double[] aus = new double[3];
        if (!nass[c]) return aus;
        double d = tiefe[c];
        int basis = c * nf * nd;
        double et = 0, eSig = 0, eK = 0, eS1 = 0;
        double[] s = new double[nf];
        for (int m = 0; m < nf; m++) {
            double z = 0;
            for (int q = 0; q < nd; q++) z += e[basis + m * nd + q];
            s[m] = z * df[m] * dTheta;
            et += s[m]; eSig += s[m] / sig[m]; eK += s[m] / Math.sqrt(k[c * nf + m]); eS1 += s[m] * sig[m];
        }
        double mBins = et;
        if (nf > 1) {                                                           // Schweif wie in den Quellen
            double z = 0;
            for (int q = 0; q < nd; q++) z += e[basis + (nf - 1) * nd + q];
            z *= dTheta * f[nf - 1] * Math.sqrt(stufe);
            z *= Math.pow(stufe, -0.5 * schweif);
            et += z / (schweif - 1); eSig += z / (schweif * sig[nf - 1]); eK += z / (schweif * Math.sqrt(k[c * nf + nf - 1])); eS1 += z * sig[nf - 1] / (schweif - 2);
        }
        if (!(et > 0)) return aus;
        double sigMittel = et / eSig, kMittel = (et / eK) * (et / eK);
        double wc = 0, br = 0, bf = 0;
        if (mitSchaum) {
            double st = kMittel * Math.sqrt(et), gam = cds * Math.pow(st / steilheitPM, 4);
            for (int m = 0; m < nf; m++) wc += gam * sigMittel * k[c * nf + m] / kMittel * s[m];
        }
        if (mitReibung) for (int m = 0; m < nf; m++) {
            double kd = k[c * nf + m] * d;
            if (kd < 20) { double sh = Math.sinh(kd); bf += cReibung * sig[m] * sig[m] / (G * G * sh * sh) * s[m]; }
        }
        if (mitBrechen) {
            double hm = gammaBJ * d, qb = brecheranteil(Math.sqrt(8 * et) / hm);
            if (qb > 0) br = 0.25 * alphaBJ * qb * (eS1 / et) / (2 * Math.PI) * hm * hm / et * mBins;
        }
        double r = RHO_W * G;
        aus[0] = r * wc;
        aus[1] = r * br;
        aus[2] = r * bf;
        return aus;
    }

    /**
     * Setzt das Spektrum der Zelle c auf einen JONSWAP (γ 3,3; σ 0,07 unter, 0,09 über dem Gipfel) mit der Höhe hs (m) und
     * der Periode tp (s), in der Richtung verteilt nach cos^(2s)((θ − θ_m)/2) um {@code wohin} (Grad wie {@link #richtung}),
     * so normiert, dass die Summe über die Stützstellen genau hs²/16 ist. Für den Anfang eines Nachlaufs aus einem Stand
     * des Archivs (seit 0.3). Ohne Höhe, Periode oder Richtung bleibt die Zelle leer. Auf einer Karte danach
     * {@code WavesModul.Rechner#hoch}.
     */
    public void jonswap(int c, double hs, double tp, double wohin, double s) {
        int basis = c * nf * nd;
        Arrays.fill(e, basis, basis + nf * nd, 0);
        if (!(hs > 0) || !(tp > 0) || !Double.isFinite(wohin)) return;
        double fp = 1 / tp, tm = Math.toRadians(wohin), sumF = 0, sumD = 0;
        double[] ef = new double[nf], dq = new double[nd];
        for (int m = 0; m < nf; m++) {
            double x = f[m] / fp, sg = f[m] <= fp ? 0.07 : 0.09;
            double r = Math.exp(-(f[m] - fp) * (f[m] - fp) / (2 * sg * sg * fp * fp));
            ef[m] = Math.pow(f[m], -5) * Math.exp(-1.25 * Math.pow(x, -4)) * Math.pow(3.3, r);
            sumF += ef[m] * df[m];
        }
        for (int q = 0; q < nd; q++) {
            double dl = Math.IEEEremainder(theta[q] - tm, 2 * Math.PI);
            dq[q] = Math.pow(Math.cos(0.5 * dl), 2 * s);
            sumD += dq[q] * dTheta;
        }
        if (!(sumF > 0) || !(sumD > 0)) return;
        double m0 = hs * hs / 16;
        for (int m = 0; m < nf; m++) for (int q = 0; q < nd; q++) e[basis + m * nd + q] = m0 * ef[m] / sumF * dq[q] / sumD;
    }

    /** Richtung wie {@link #richtung} (Grad, wohin, 0 = +x, gegen den Uhrzeigersinn) als woher, von Gitternord im Uhrzeigersinn (wie {@code waves.dir}). */
    public static double woher(double wohin) { return ((270 - wohin) % 360 + 360) % 360; }

    /** Umkehrung von {@link #woher}: aus woher (von Gitternord im Uhrzeigersinn) die Richtung wohin (0 = +x, gegen den Uhrzeigersinn), in [0, 360). */
    public static double wohin(double woher) { return ((270 - woher) % 360 + 360) % 360; }

    public double wellenzahl(int c, int m) { return k[c * nf + m]; }
    /** σ/sinh(2kd): der Faktor der Refraktion an der Tiefe (1/s je Gefälle). */
    public double refraktion(int c, int m) { return rf[c * nf + m]; }
    /** Gefälle der Tiefe in x und y (–), wie die Ausbreitung es nimmt. */
    public double gefaelleX(int c) { return dTx[c]; }
    public double gefaelleY(int c) { return dTy[c]; }
    /** Größte Summe der Courant-Raten (1/s); ein Teilschritt h braucht h · rate ≤ {@link #courant}. */
    public double rate() { return rate; }
    /** Ob eine Strömung gesetzt ist (dann verschiebt die Ausbreitung auch die Frequenz). */
    public boolean strom() { return strom; }
    public double gruppe(int c, int m) { return cg[c * nf + m]; }
    /** Gefälle der Strömung {∂U/∂x, ∂U/∂y, ∂V/∂x, ∂V/∂y} (1/s), wie die Ausbreitung es nimmt. */
    public double[] stromGefaelle(int c) { return new double[] {ux[c], uy[c], vx[c], vy[c]}; }
    /**
     * Die Stützstellen der DIA (für die Karte): {qmP0, qmP1, qmM0, qmM1, qkP[s][y] (s · 2 + y, vier), qkM (vier), qOben}
     * und die Gewichte {qwP0, qwP1, qwM0, qwM1, qvP[s][y] (vier), qvM (vier)}.
     */
    public int[] diaStufen() {
        return new int[] {qmP[0], qmP[1], qmM[0], qmM[1], qkP[0][0], qkP[0][1], qkP[1][0], qkP[1][1], qkM[0][0], qkM[0][1], qkM[1][0], qkM[1][1], qOben};
    }
    public double[] diaGewichte() {
        return new double[] {qwP[0], qwP[1], qwM[0], qwM[1], qvP[0][0], qvP[0][1], qvP[1][0], qvP[1][1], qvM[0][0], qvM[0][1], qvM[1][0], qvM[1][1]};
    }
    public boolean nass(int c) { return nass[c]; }
}
