package com.dan.core.szene;

import java.util.stream.IntStream;

/**
 * Die Phase der Wellen über Tiefe und Strömung (Seegang Werkbuch, S3; Plan 6.3, eigene Erweiterung [G]): je Band b und
 * Richtung q die Phase Φ_bq(x) der Mitte des Bands und die Laufzeit der Gruppe T_bq(x) = ∂Φ/∂ω auf einem Gitter.
 * <p>
 * <b>Gewünscht</b> ist für jede Stützstelle die örtliche Wellenzahl in ihrer Richtung: ∇Φ = k(x) e_θ mit
 * ω_b = σ(k, h) + k U_θ, und ∇T = e_θ / (c_g + U_θ). Ein solches Feld hat keine Phase, sobald sich k quer zu e_θ ändert
 * (∇ × (k e_θ) = ∇k × e_θ ≠ 0). Der Plan nahm das Integral längs der Richtung; quer dazu wachsen die Unterschiede dann
 * mit dem Weg, die Kämme drehen sich und werden zu kurz (Befund B25). Eine Lösung der Eikonalgleichung |∇Φ| = k hält die
 * Wellenlänge, dreht die Richtung aber wie ein Strahl – obwohl das Nahfeld die Brechung schon in seinen Richtungen
 * trägt; sie zählte doppelt, und auf schrägen Rändern gibt es keine passenden Randwerte (Befund B26).
 * <p>
 * <b>Hier:</b> Φ ist das Feld, dessen Gradient dem gewünschten am nächsten kommt (kleinste Quadrate über die Kanten der
 * Zellen), also die Lösung der Poisson-Gleichung ∇²Φ = ∇ · (k e_θ) mit dem Fluss des Wunschfelds am Rand. Wo sich k nur
 * längs e_θ ändert (Wellen laufen auf die Küste zu), ist das exakt; sonst bleibt die Richtung nahe θ und die Wellenzahl
 * nahe k. Gerechnet wird die Abweichung von der ebenen Welle im tiefen Wasser, δΦ = Φ − k₀ s mit
 * s = (x − x_Ursprung) · e_θ, direkt mit einer Kosinus-Transformation (Neumann); für ein feines Gitter im groben mit den
 * Werten des groben auf den Randzellen und einer Sinus-Transformation (Dirichlet). Dasselbe für δT = T − s / c_g₀.
 * <p>
 * Trockene Zellen (Tiefe unter {@link #H_MIN}) tragen keinen Wunsch: Ihr k − k₀ wird aus den nassen Nachbarn
 * aufgefüllt [G]. Blockierte Stützstellen nehmen die Wellenzahl am Blockierpunkt, die Laufzeit mit
 * c_g + U_θ ≥ {@link #CG_MIN} c_g₀ [G]; ihre Amplitude ist null.
 * <p>
 * <b>Festwert.</b> Φ ist nur bis auf eine Konstante bestimmt. Ohne Bezug ist δΦ in der Mitte des Gitters 0;
 * {@link #angleichen} setzt die Konstante je Stützstelle so, dass ein anderer Stand im gemeinsamen Gebiet im Mittel
 * gleich ist (zwei Stände der Zeit, ein verschobenes Gitter).
 * <p>
 * Ablage je Zelle: δΦ und δT, Index (b · nd + q) · nc + c; bilinear auslesen ({@link #wert}).
 */
public final class Phasenfeld {
    public static final double H_MIN = 0.05, CG_MIN = 0.2;

    public final int nx, ny, nc, nb, nd;
    public final double dx, x0, y0, ursprungX, ursprungY;
    /** Kreisfrequenz je Band; Richtung je Stützstelle (rad, wohin, 0 = Ost, gegen den Uhrzeigersinn), Index b · nd + q. */
    public final double[] omega, richtung;
    /** Wellenzahl und Gruppengeschwindigkeit im tiefen Wasser je Band. */
    public final double[] k0, cg0;
    public final double[] dPhi, dT;
    /** Zeit des Stands (s), wie die Szene. */
    public final double zeit;

    private Phasenfeld(int nx, int ny, double dx, double x0, double y0, double ox, double oy, double[] omega, double[] richtung, int nd, double zeit) {
        this.nx = nx; this.ny = ny; this.nc = nx * ny; this.dx = dx; this.x0 = x0; this.y0 = y0; this.ursprungX = ox; this.ursprungY = oy;
        this.omega = omega.clone(); this.richtung = richtung.clone(); this.nb = omega.length; this.nd = nd; this.zeit = zeit;
        k0 = new double[nb]; cg0 = new double[nb];
        for (int b = 0; b < nb; b++) { k0[b] = omega[b] * omega[b] / Dispersion.G; cg0[b] = 0.5 * omega[b] / k0[b]; }
        dPhi = new double[nb * nd * nc];
        dT = new double[nb * nd * nc];
    }

    /**
     * Richtungen je Stützstelle [G]: die Richtung q des Nahfelds, je Band und Richtung um bis zu ±0,4 Δθ versetzt
     * (Folge geringer Diskrepanz), damit die Bänder nicht dieselben Kammlinien zeichnen.
     */
    public static double[] richtungen(int nb, int nd) {
        double[] r = new double[nb * nd];
        double dth = 2 * Math.PI / nd;
        for (int b = 0; b < nb; b++)
            for (int q = 0; q < nd; q++) {
                double u = (0.7548776662466927 * (b + 1) + 0.5698402909980532 * (q + 7)) % 1.0;
                r[b * nd + q] = q * dth + 0.8 * (u - 0.5) * dth;
            }
        return r;
    }

    /**
     * Rechnet das Phasenfeld. Gitter: nx × ny Zellen zu dx, Ecke (x0, y0); je Zelle Tiefe h, Strömung (u, v) (null:
     * keine); Ursprung der Phase (ox, oy); grob: ein gröberes Phasenfeld, aus dem die Randzellen kommen (null: Neumann).
     */
    public static Phasenfeld rechne(int nx, int ny, double dx, double x0, double y0, double[] h, double[] u, double[] v,
                                    double[] omega, double[] richtung, int nd, double ox, double oy, Phasenfeld grob, double zeit) {
        return rechne(nx, ny, dx, x0, y0, h, u, v, omega, richtung, nd, ox, oy, grob, zeit, null);
    }

    /** Untere Schranke des Gewichts einer Kante, relativ zur größten Energie der Stützstelle [G]. */
    public static final double GEWICHT_MIN = 0.01;
    /** Grenze der Iteration (relativer Rest) und höchste Zahl der Schritte. */
    public static final double TOLERANZ = 1e-7;
    public static final int SCHRITTE = 4000;
    /** Schritte des letzten gewichteten Lösens (größte über die Stützstellen), zur Auskunft. */
    public int schritte;
    /**
     * Je Band die kleinste Wellenzahl der Dispersion k(h, U) über die nassen Zellen und alle Richtungen (rad/m): Keine
     * Welle des Bands ist im Feld länger als 2π / kMin. Die Synthese misst die Auflösung mit max(|∇Φ|, kMin) (Befund
     * B34): Wo das Feld nachgibt (fast trocken, wenig Energie), kann |∇Φ| kleiner sein, als die Physik erlaubt.
     */
    public double[] kMin;

    /**
     * Wie oben, mit der Varianz je Zelle und Stützstelle (Index (c · nb + b) · nd + q, wie im Nahfeld; null: ohne
     * Gewicht). Dann zählt jede Kante mit w = {@link #GEWICHT_MIN} + Ê, Ê das Mittel der beiden Zellen relativ zur größten
     * Energie der Stützstelle (Befund B33): Die Phase stimmt, wo die Stützstelle Energie hat; dort, wo sie keine hat
     * (Watt fast trocken, abgewandte Richtungen), gibt sie nach. Gelöst mit vorkonditionierten konjugierten Gradienten
     * (Jacobi), Start aus der Lösung ohne Gewicht.
     */
    public static Phasenfeld rechne(int nx, int ny, double dx, double x0, double y0, double[] h, double[] u, double[] v,
                                    double[] omega, double[] richtung, int nd, double ox, double oy, Phasenfeld grob, double zeit, double[] e) {
        Phasenfeld p = new Phasenfeld(nx, ny, dx, x0, y0, ox, oy, omega, richtung, nd, zeit);
        int nb = omega.length;
        p.h_ = h; p.u_ = u; p.v_ = v; p.e_ = e; p.grob_ = grob;
        p.kMin = new double[nb];
        java.util.Arrays.fill(p.kMin, Double.POSITIVE_INFINITY);
        p.loeser = new Loeser(nx, ny, grob != null);
        IntStream.range(0, nb * nd).parallel().forEach(bq -> {
            int b = bq / nd;
            double th = richtung[bq], ex = Math.cos(th), ey = Math.sin(th);
            int n = nx * ny;
            Wunsch wu = p.wunsch(bq);
            double km = Double.POSITIVE_INFINITY;
            for (int c = 0; c < n; c++) if (h[c] >= H_MIN) km = Math.min(km, wu.kmin[c]);
            synchronized (p) { p.kMin[b] = Math.min(p.kMin[b], km); }
            double[] fp = p.loeser.loese(wu.kp, ex * dx, ey * dx, wu.rp), ft = p.loeser.loese(wu.kt, ex * dx, ey * dx, wu.rt);
            if (wu.eh != null) {
                int s1 = gewichtet(nx, ny, wu.eh, wu.kp, ex * dx, ey * dx, wu.rp, fp), s2 = gewichtet(nx, ny, wu.eh, wu.kt, ex * dx, ey * dx, wu.rt, ft);
                synchronized (p) { p.schritte = Math.max(p.schritte, Math.max(s1, s2)); }
            }
            if (grob == null) {                                                   // Festwert: in der Mitte 0
                int cm = (ny / 2) * nx + nx / 2;
                double a = fp[cm], bt = ft[cm];
                for (int c = 0; c < n; c++) { fp[c] -= a; ft[c] -= bt; }
            }
            System.arraycopy(fp, 0, p.dPhi, bq * n, n);
            System.arraycopy(ft, 0, p.dT, bq * n, n);
        });
        return p;
    }

    /** Seit S5b-6b: die Eingaben von {@link #rechne} (für {@link #gradient}): Tiefe, Strömung, Varianz, grobes Feld, Löser. */
    double[] h_, u_, v_, e_;
    Phasenfeld grob_;
    Loeser loeser;

    /** Das gewünschte Feld einer Stützstelle: κ für Φ und T (trocken aufgefüllt), Randwerte, Gewicht Ê (null: ohne). */
    static final class Wunsch {
        double[] kp, kt, rp, rt, eh, kmin;
    }

    Wunsch wunsch(int bq) {
        int b = bq / nd, n = nc;
        double th = richtung[bq], ex = Math.cos(th), ey = Math.sin(th);
        double w = omega[b];
        Wunsch wu = new Wunsch();
        double[] kp = new double[n], kt = new double[n], km = new double[n];
        boolean[] nass = new boolean[n];
        for (int c = 0; c < n; c++) {
            nass[c] = h_[c] >= H_MIN;
            double[] g = gradient(bq, c);
            km[c] = g[0];
            kp[c] = g[0] - k0[b];
            kt[c] = g[1] - 1 / cg0[b];
        }
        fuellen(nx, ny, nass, kp);
        fuellen(nx, ny, nass, kt);
        wu.kp = kp; wu.kt = kt; wu.kmin = km;
        if (grob_ != null) {
            double[] rp = new double[n], rt = new double[n];
            double[] o = new double[6];
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    if (!(i == 0 || j == 0 || i == nx - 1 || j == ny - 1)) continue;
                    grob_.wert(bq, x(i), y(j), o);
                    double s = (x(i) - ursprungX) * ex + (y(j) - ursprungY) * ey;
                    rp[j * nx + i] = o[0] - k0[b] * s;
                    rt[j * nx + i] = o[1] - s / cg0[b];
                }
            wu.rp = rp; wu.rt = rt;
        }
        if (e_ != null) {
            double[] eh = new double[n];
            double em = 0;
            for (int c = 0; c < n; c++) { eh[c] = e_[c * nb * nd + bq]; em = Math.max(em, eh[c]); }
            if (em > 0) { for (int c = 0; c < n; c++) eh[c] /= em; wu.eh = eh; }
        }
        return wu;
    }

    /**
     * Seit S5b-6b: der gewünschte Gradient der Stützstelle bq in der Zelle c, {k, 1 / (c_g + U_θ)}: k aus der Dispersion
     * mit Tiefe (mindestens {@link #H_MIN}) und Strömung längs der Richtung, blockiert am Blockierpunkt, c_g + U_θ
     * mindestens {@link #CG_MIN} c_g₀ (wie {@link #rechne}).
     */
    double[] gradient(int bq, int c) {
        int b = bq / nd;
        double th = richtung[bq], ex = Math.cos(th), ey = Math.sin(th);
        double ut = u_ == null ? 0 : u_[c] * ex + v_[c] * ey;
        double hh = Math.max(H_MIN, h_[c]);
        double k = Dispersion.wellenzahl(omega[b], hh, ut), cga;
        if (Double.isNaN(k)) { k = Dispersion.blockiert(omega[b], hh, ut); cga = 0; }
        else cga = Dispersion.gruppe(Dispersion.sigma(k, hh), k, hh) + ut;
        return new double[] {k, 1 / Math.max(cga, CG_MIN * cg0[b])};
    }

    /**
     * Gewichtete kleinste Quadrate Σ w_f (φ_n − φ_c − d_f)², w_f = GEWICHT_MIN + (Ê_c + Ê_n)/2, mit Jacobi-PCG an Ort
     * und Stelle von phi (Start). Randwerte fest, wenn rand ≠ null. Gibt die Zahl der Schritte zurück.
     */
    static int gewichtet(int nx, int ny, double[] ehat, double[] kap, double ax, double ay, double[] rand, double[] phi) {
        int n = nx * ny;
        double[] wx = new double[n], wy = new double[n], dxf = new double[n], dyf = new double[n];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i + 1 < nx; i++) {
                int c = j * nx + i;
                wx[c] = GEWICHT_MIN + 0.5 * (ehat[c] + ehat[c + 1]);
                dxf[c] = 0.5 * (kap[c] + kap[c + 1]) * ax;
            }
        for (int j = 0; j + 1 < ny; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                wy[c] = GEWICHT_MIN + 0.5 * (ehat[c] + ehat[c + nx]);
                dyf[c] = 0.5 * (kap[c] + kap[c + nx]) * ay;
            }
        boolean[] fest = new boolean[n];
        if (rand != null)
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) if (i == 0 || j == 0 || i == nx - 1 || j == ny - 1) { fest[j * nx + i] = true; phi[j * nx + i] = rand[j * nx + i]; }
        // b = −Σ w d (Kanten), Diagonale
        double[] b = new double[n], diag = new double[n];
        for (int c = 0; c < n; c++) {
            int i = c % nx, j = c / nx;
            if (i + 1 < nx) { b[c] -= wx[c] * dxf[c]; b[c + 1] += wx[c] * dxf[c]; diag[c] += wx[c]; diag[c + 1] += wx[c]; }
            if (j + 1 < ny) { b[c] -= wy[c] * dyf[c]; b[c + nx] += wy[c] * dyf[c]; diag[c] += wy[c]; diag[c + nx] += wy[c]; }
        }
        double[] r = new double[n], z = new double[n], pp = new double[n], ap = new double[n];
        anwenden(nx, ny, wx, wy, phi, ap);
        double bn = 0;
        for (int c = 0; c < n; c++) { r[c] = fest[c] ? 0 : b[c] - ap[c]; if (!fest[c]) bn += b[c] * b[c]; }
        if (rand == null) { double m = 0; for (double x : r) m += x; m /= n; for (int c = 0; c < n; c++) r[c] -= m; }
        bn = Math.sqrt(Math.max(bn, 1e-300));
        double rz = 0;
        for (int c = 0; c < n; c++) { z[c] = fest[c] ? 0 : r[c] / diag[c]; pp[c] = z[c]; rz += r[c] * z[c]; }
        int it = 0;
        for (; it < SCHRITTE; it++) {
            double rn = 0;
            for (int c = 0; c < n; c++) rn += r[c] * r[c];
            if (Math.sqrt(rn) <= TOLERANZ * bn) break;
            anwenden(nx, ny, wx, wy, pp, ap);
            double pap = 0;
            for (int c = 0; c < n; c++) if (!fest[c]) pap += pp[c] * ap[c];
            if (!(pap > 0)) break;
            double al = rz / pap, rz2 = 0;
            for (int c = 0; c < n; c++) {
                if (fest[c]) continue;
                phi[c] += al * pp[c];
                r[c] -= al * ap[c];
            }
            if (rand == null) { double m = 0; for (double x : r) m += x; m /= n; for (int c = 0; c < n; c++) r[c] -= m; }
            for (int c = 0; c < n; c++) { z[c] = fest[c] ? 0 : r[c] / diag[c]; rz2 += r[c] * z[c]; }
            double be = rz2 / rz;
            rz = rz2;
            for (int c = 0; c < n; c++) pp[c] = fest[c] ? 0 : z[c] + be * pp[c];
        }
        return it;
    }

    /** (A φ)_c = Σ w_f (φ_c − φ_n) über die Kanten. */
    static void anwenden(int nx, int ny, double[] wx, double[] wy, double[] phi, double[] out) {
        java.util.Arrays.fill(out, 0);
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                if (i + 1 < nx) { double f = wx[c] * (phi[c] - phi[c + 1]); out[c] += f; out[c + 1] -= f; }
                if (j + 1 < ny) { double f = wy[c] * (phi[c] - phi[c + nx]); out[c] += f; out[c + nx] -= f; }
            }
    }

    /** Füllt Zellen ohne Wunsch (trocken) aus den Nachbarn (Mittel der gefüllten, bis alles gefüllt ist). */
    static void fuellen(int nx, int ny, boolean[] gut, double[] f) {
        int n = nx * ny;
        boolean[] g = gut.clone();
        int offen = 0;
        for (boolean x : g) if (!x) offen++;
        if (offen == 0) return;
        if (offen == n) { java.util.Arrays.fill(f, 0); return; }
        while (offen > 0) {
            boolean[] neu = g.clone();
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int c = j * nx + i;
                    if (g[c]) continue;
                    double s = 0;
                    int m = 0;
                    if (i > 0 && g[c - 1]) { s += f[c - 1]; m++; }
                    if (i < nx - 1 && g[c + 1]) { s += f[c + 1]; m++; }
                    if (j > 0 && g[c - nx]) { s += f[c - nx]; m++; }
                    if (j < ny - 1 && g[c + nx]) { s += f[c + nx]; m++; }
                    if (m > 0) { f[c] = s / m; neu[c] = true; offen--; }
                }
            g = neu;
        }
    }

    /**
     * Kleinste Quadrate über die Kanten: Σ (φ_n − φ_c − d_cn)² mit d = κ̄ (e · Δ) auf jeder Kante (κ̄ das Mittel der beiden
     * Zellen). Neumann: Kosinus-Transformation (DCT-II) über alle Zellen. Dirichlet: Randzellen fest, Sinus-Transformation
     * (DST-I) über das Innere; Kanten zwischen zwei Randzellen zählen nicht.
     */
    static final class Loeser {
        final int nx, ny, mx, my, o;
        final boolean dirichlet;
        final double[][] cx, cy;
        final double[] lx, ly, nx2, ny2;

        Loeser(int nx, int ny, boolean dirichlet) {
            this.nx = nx; this.ny = ny; this.dirichlet = dirichlet;
            mx = dirichlet ? nx - 2 : nx; my = dirichlet ? ny - 2 : ny; o = dirichlet ? 1 : 0;
            cx = basis(mx, dirichlet); cy = basis(my, dirichlet);
            lx = new double[mx]; ly = new double[my]; nx2 = new double[mx]; ny2 = new double[my];
            for (int p = 0; p < mx; p++) {
                lx[p] = dirichlet ? 2 * (1 - Math.cos(Math.PI * (p + 1) / (mx + 1))) : 2 * (1 - Math.cos(Math.PI * p / mx));
                nx2[p] = dirichlet ? (mx + 1) / 2.0 : (p == 0 ? mx : mx / 2.0);
            }
            for (int q = 0; q < my; q++) {
                ly[q] = dirichlet ? 2 * (1 - Math.cos(Math.PI * (q + 1) / (my + 1))) : 2 * (1 - Math.cos(Math.PI * q / my));
                ny2[q] = dirichlet ? (my + 1) / 2.0 : (q == 0 ? my : my / 2.0);
            }
        }

        static double[][] basis(int m, boolean sinus) {
            double[][] c = new double[m][m];
            for (int p = 0; p < m; p++)
                for (int i = 0; i < m; i++)
                    c[p][i] = sinus ? Math.sin(Math.PI * (p + 1) * (i + 1) / (m + 1)) : Math.cos(Math.PI * p * (i + 0.5) / m);
            return c;
        }

        /** κ je Zelle, (ex, ey) · dx, Randwerte (Dirichlet) oder null. Zurück φ je Zelle. */
        double[] loese(double[] kap, double ax, double ay, double[] rand) {
            int n = nx * ny;
            double[] b = new double[n];
            for (int j = 0; j < ny; j++)
                for (int i = 0; i + 1 < nx; i++) {
                    int c = j * nx + i;
                    if (dirichlet && rand(i, j) && rand(i + 1, j)) continue;
                    double d = 0.5 * (kap[c] + kap[c + 1]) * ax;
                    b[c] -= d; b[c + 1] += d;
                }
            for (int j = 0; j + 1 < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int c = j * nx + i;
                    if (dirichlet && rand(i, j) && rand(i, j + 1)) continue;
                    double d = 0.5 * (kap[c] + kap[c + nx]) * ay;
                    b[c] -= d; b[c + nx] += d;
                }
            double[] phi = new double[n];
            if (dirichlet) {
                for (int j = 0; j < ny; j++)
                    for (int i = 0; i < nx; i++) {
                        int c = j * nx + i;
                        if (rand(i, j)) { phi[c] = rand[c]; continue; }
                        if (i == 1) b[c] += rand[c - 1];
                        if (i == nx - 2) b[c] += rand[c + 1];
                        if (j == 1) b[c] += rand[c - nx];
                        if (j == ny - 2) b[c] += rand[c + nx];
                    }
            }
            double[][] t = transformiere(b);
            for (int q = 0; q < my; q++)
                for (int p = 0; p < mx; p++) {
                    double lam = lx[p] + ly[q];
                    t[q][p] = lam > 1e-14 ? t[q][p] / lam / (nx2[p] * ny2[q]) : 0;
                }
            zurueck(t, phi);
            return phi;
        }

        boolean rand(int i, int j) { return i == 0 || j == 0 || i == nx - 1 || j == ny - 1; }

        /** t[q][p] = Σ_ij cy[q][j] cx[p][i] b über das Innere (Dirichlet) oder alles (Neumann). */
        double[][] transformiere(double[] b) {
            double[][] r = new double[my][mx], z = new double[my][mx];
            for (int j = 0; j < my; j++) {
                int base = (j + o) * nx + o;
                for (int p = 0; p < mx; p++) {
                    double s = 0;
                    double[] c = cx[p];
                    for (int i = 0; i < mx; i++) s += c[i] * b[base + i];
                    z[j][p] = s;
                }
            }
            for (int q = 0; q < my; q++) {
                double[] c = cy[q];
                for (int p = 0; p < mx; p++) {
                    double s = 0;
                    for (int j = 0; j < my; j++) s += c[j] * z[j][p];
                    r[q][p] = s;
                }
            }
            return r;
        }

        void zurueck(double[][] t, double[] phi) {
            double[][] z = new double[my][mx];
            for (int j = 0; j < my; j++)
                for (int p = 0; p < mx; p++) {
                    double s = 0;
                    for (int q = 0; q < my; q++) s += cy[q][j] * t[q][p];
                    z[j][p] = s;
                }
            for (int j = 0; j < my; j++)
                for (int i = 0; i < mx; i++) {
                    double s = 0;
                    for (int p = 0; p < mx; p++) s += cx[p][i] * z[j][p];
                    phi[(j + o) * nx + o + i] = s;
                }
        }
    }

    /**
     * Setzt die Konstante je Stützstelle so, dass dieses Feld im gemeinsamen Gebiet mit {@code alt} im Mittel
     * übereinstimmt (für zwei Stände oder ein verschobenes Gitter). Ohne gemeinsames Gebiet unverändert.
     */
    public void angleichen(Phasenfeld alt) {
        double[] o = new double[6];
        for (int bq = 0; bq < nb * nd; bq++) {
            int b = bq / nd;
            double th = richtung[bq], ex = Math.cos(th), ey = Math.sin(th);
            double sp = 0, st = 0;
            int m = 0;
            for (int j = 0; j < ny; j += 2)
                for (int i = 0; i < nx; i += 2) {
                    double x = x(i), y = y(j);
                    if (x < alt.x0 + 0.5 * alt.dx || x > alt.x0 + (alt.nx - 0.5) * alt.dx || y < alt.y0 + 0.5 * alt.dx || y > alt.y0 + (alt.ny - 0.5) * alt.dx) continue;
                    alt.wert(bq, x, y, o);
                    double s = (x - ursprungX) * ex + (y - ursprungY) * ey;
                    sp += o[0] - k0[b] * s - dPhi[bq * nc + j * nx + i];
                    st += o[1] - s / cg0[b] - dT[bq * nc + j * nx + i];
                    m++;
                }
            if (m == 0) continue;
            sp /= m; st /= m;
            for (int c = 0; c < nc; c++) { dPhi[bq * nc + c] += sp; dT[bq * nc + c] += st; }
        }
    }

    public double x(int i) { return x0 + (i + 0.5) * dx; }

    public double y(int j) { return y0 + (j + 0.5) * dx; }

    /**
     * Bilinear am Ort (x, y): {Φ, T, ∂Φ/∂x, ∂Φ/∂y, ∂T/∂x, ∂T/∂y} der Stützstelle bq = b · nd + q, mit der ebenen Welle
     * des tiefen Wassers zurück addiert. Außerhalb der Zellmitten am Rand festgehalten.
     */
    public void wert(int bq, double x, double y, double[] o) {
        int b = bq / nd;
        double th = richtung[bq], ex = Math.cos(th), ey = Math.sin(th);
        double s = (x - ursprungX) * ex + (y - ursprungY) * ey;
        double fx = (x - x0) / dx - 0.5, fy = (y - y0) / dx - 0.5;
        fx = Math.max(0, Math.min(nx - 1.000001, fx));
        fy = Math.max(0, Math.min(ny - 1.000001, fy));
        int i = (int) fx, j = (int) fy;
        int i1 = Math.min(nx - 1, i + 1), j1 = Math.min(ny - 1, j + 1);
        double ax = fx - i, ay = fy - j;
        int o0 = bq * nc;
        int c00 = o0 + j * nx + i, c10 = o0 + j * nx + i1, c01 = o0 + j1 * nx + i, c11 = o0 + j1 * nx + i1;
        double p00 = dPhi[c00], p10 = dPhi[c10], p01 = dPhi[c01], p11 = dPhi[c11];
        double t00 = dT[c00], t10 = dT[c10], t01 = dT[c01], t11 = dT[c11];
        double p = (1 - ay) * ((1 - ax) * p00 + ax * p10) + ay * ((1 - ax) * p01 + ax * p11);
        double t = (1 - ay) * ((1 - ax) * t00 + ax * t10) + ay * ((1 - ax) * t01 + ax * t11);
        double px = ((1 - ay) * (p10 - p00) + ay * (p11 - p01)) / dx, py = ((1 - ax) * (p01 - p00) + ax * (p11 - p10)) / dx;
        double tx = ((1 - ay) * (t10 - t00) + ay * (t11 - t01)) / dx, ty = ((1 - ax) * (t01 - t00) + ax * (t11 - t10)) / dx;
        o[0] = p + k0[b] * s;
        o[1] = t + s / cg0[b];
        o[2] = px + k0[b] * ex;
        o[3] = py + k0[b] * ey;
        o[4] = tx + ex / cg0[b];
        o[5] = ty + ey / cg0[b];
    }

    /** Speicher der Ablage in float (Byte), für die Karte. */
    public long bytes() { return 2L * 4 * dPhi.length; }
}
