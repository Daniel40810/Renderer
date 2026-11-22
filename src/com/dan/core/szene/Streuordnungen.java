package com.dan.core.szene;

import java.util.stream.IntStream;

/**
 * Mehrfachstreuung der Luft nach Richtung (P7e, Werkbuch HalligHooge Teil A, 12.8, F58 A): die Quelle ab der zweiten
 * Streuung J(h, μ, μ_s, φ), gerechnet in Streuordnungen wie bei Bruneton (2017), mit den echten Phasenfunktionen und dem
 * Boden. Ersetzt Ψ(h, μ_s) nach Hillaire, das die Mehrfachstreuung in alle Richtungen gleich nimmt (Befund P7e).
 * <p>
 * Größen je Einheit der Bestrahlung durch die Sonne außerhalb der Luft (senkrecht zur Richtung), je Wellenlänge:
 * <ul>
 * <li>L_n(x, v): Strahldichte, die man von x in Richtung v sieht, aus Licht, das genau n-mal gestreut ist (der Boden
 * zählt als Streuung, Lambert mit der Albedo der Luft);</li>
 * <li>J^n_R(x, v) = ∫ p_R(v·ω) L_{n−1}(x, ω) dω und J^n_M mit p_M (Cornette-Shanks, g = 0,85); die Quelle der n-ten
 * Streuung im Strahlengang ist σ_R J^n_R + σ_M J^n_M;</li>
 * <li>J = Σ_{n ≥ 2} J^n (Tabelle), dazu E_n am Boden = ∫ L_n μ dω über den Himmel.</li>
 * </ul>
 * Die erste Streuung rechnet der Strahlengang ({@link Scattering}) genau; die Tabelle trägt nur den Rest. Gitter:
 * h = 100 km · x³ (24, dicht am Boden: 0, 8 m, 66 m, 0,22, 0,53, 1,0, 1,8, 2,8, 4,2 km …), μ = s |s| (32, dicht an der Waagerechten), μ_s = s |s| ab −0,2 (40, dicht an der Kimm; tiefer 0),
 * φ = π t² (32, dicht zur Sonne hin; Azimut des Blicks gegen die Sonne um die Senkrechte); linear in s (μ) und t,
 * in μ_s und in der Höhe logarithmisch ({@link #logMs});
 * 6 Ordnungen und der Rest als geometrische Reihe, je Knoten 2 × 32 × 16 Richtungen (cos θ = 1 − 2u³ um den Blick
 * und um die Sonne, gewichtet nach Veach). Gerechnet in double auf dem Prozessor je
 * Wellenlänge (die drei Kanäle, die 15 Bänder der Himmelsansicht); die Karte bekommt die Tabellen als Bild.
 */
public final class Streuordnungen {
    public static final int NR = 24, NMU = 32, NMS = 40, NPHI = 32, NE = 64;
    public static final int ORDNUNGEN = 6, SCHRITTE = 40, N_THETA = 32, N_PSI = 16, N_E = 16;
    public static final double MS_MIN = -0.2;
    static final double R = Atmosphere.BOTTOM, RT = Atmosphere.TOP;

    public final Atmosphere atm;
    /** Wellenlängen (nm, nur zur Auskunft) und ihre Beiwerte: Rayleigh-Streuung und Ozon je m am Boden. */
    public final double[] lambda, rayleigh, ozon;
    /** J_R und J_M je Wellenlänge, Index ((i·NMU + j)·NMS + k)·NPHI + m. */
    public final float[][] jR, jM;
    /** Durchlässigkeit bis zur Oberkante je Wellenlänge, Gitter wie {@link Scattering} (TRANS_W × TRANS_H). */
    final float[][] trans;

    public static int index(int i, int j, int k, int m) { return ((i * NMU + j) * NMS + k) * NPHI + m; }

    public static double hVon(int i) { double x = i / (NR - 1.0); return (RT - R) * x * x * x; }
    public static double muVon(int j) { double s = -1 + 2.0 * j / (NMU - 1); return s * Math.abs(s); }
    static final double S_MS0 = -Math.sqrt(-MS_MIN);
    public static double msVon(int k) { double s = S_MS0 + (1 - S_MS0) * k / (NMS - 1); return s * Math.abs(s); }
    public static double phiVon(int m) { double t = m / (NPHI - 1.0); return Math.PI * t * t; }

    static double ih(double h) { return Math.max(0, Math.min(NR - 1, Math.cbrt(Math.max(0, h) / (RT - R)) * (NR - 1))); }
    static double imu(double mu) { double s = Math.signum(mu) * Math.sqrt(Math.abs(mu)); return Math.max(0, Math.min(NMU - 1, (s + 1) / 2 * (NMU - 1))); }
    static double ims(double ms) { double s = Math.signum(ms) * Math.sqrt(Math.abs(ms)); return Math.max(0, Math.min(NMS - 1, (s - S_MS0) / (1 - S_MS0) * (NMS - 1))); }
    static double iphi(double phi) { return Math.max(0, Math.min(NPHI - 1, Math.sqrt(Math.max(0, phi) / Math.PI) * (NPHI - 1))); }

    /** Azimut des Blicks gegen die Sonne um die Senkrechte aus μ, μ_s, ν. */
    public static double phi(double mu, double ms, double nu) {
        double a = Math.sqrt(Math.max(0, 1 - mu * mu) * Math.max(0, 1 - ms * ms));
        if (a < 1e-9) return 0;
        return Math.acos(Math.max(-1, Math.min(1, (nu - mu * ms) / a)));
    }

    /** Die drei Wellenlängen der Kanäle des Echtzeitbilds (Beiwerte wie {@link Atmosphere}). */
    public static Streuordnungen kanaele(Atmosphere atm) {
        return new Streuordnungen(atm, new double[] {680, 550, 440}, Atmosphere.RAYLEIGH.clone(), Atmosphere.OZONE.clone());
    }

    /** Wellenlängen der Himmelsansicht (seit P7e, F58 A): 15 Bänder zu 22 nm ab 390 nm [G], die Ränder bis 360 und 830 nm. */
    public static final int N_SPEKTRAL = 15;
    public static final double SP_ERSTE = 390, SP_BREITE = 22;

    /** Die 15 Wellenlängen der Himmelsansicht, Beiwerte wie die Referenz (Bodhaine, Ozon nach seinen Querschnitten). */
    public static Streuordnungen spektral(Atmosphere atm) {
        double[] l = new double[N_SPEKTRAL], ra = new double[N_SPEKTRAL], oz = new double[N_SPEKTRAL];
        for (int k = 0; k < N_SPEKTRAL; k++) {
            l[k] = SP_ERSTE + SP_BREITE * k;
            ra[k] = com.dan.core.szene.spektrum.Spektrum.rayleigh(l[k], Atmosphere.RAYLEIGH_H);
            oz[k] = com.dan.core.szene.spektrum.Spektrum.OZON_DICHTE * com.dan.core.szene.spektrum.Spektrum.ozon(l[k]);
        }
        return new Streuordnungen(atm, l, ra, oz);
    }

    /**
     * Gewichte nach linearem sRGB (cd/m²) je Band: K_m ∫ S(λ) (Zeile c von XYZ → sRGB) · (x̄, ȳ, z̄) dλ über das Band, S die
     * Sonne nach ASTM G173 außerhalb der Luft in 1 AE, wie die Referenz abbildet. [c][k]. Das Licht zwischen zwei
     * Wellenlängen linear (Dachfunktionen; ein Band je Wellenlänge, „kasten“, liegt bei tiefer Sonne im Blau um 2 % daneben,
     * weil die Gewichte im Band schief liegen, Befund P7e), außerhalb der ersten und letzten gleich.
     */
    public static double[][] gewichte() { return gewichte(false); }

    /**
     * Tafel für den Weg nach sRGB (seit P7e): je nm von {@code Spektrum.MIN} bis {@code MAX} {Lage zwischen den
     * Wellenlängen (0 … N_SPEKTRAL − 1), Gewichte R, G, B wie {@link #gewichte}}. Das Licht dazwischen quadratisch im
     * Logarithmus durch die drei nächsten Wellenlängen ({@link #nachRgb}): Licht durch lange Wege fällt mit der
     * Wellenlänge fast exponentiell (Abend, Kimm); linear lag es dort bis +8 % im Rot daneben, linear im Logarithmus
     * bis +3 % im Rot und −3 % im Blau, quadratisch im Logarithmus bis +1,5 % (Befund P7e, Einfachstreuung je nm).
     */
    public static float[] spektralTafel() {
        int n = com.dan.core.szene.spektrum.Spektrum.MAX - com.dan.core.szene.spektrum.Spektrum.MIN + 1;
        float[] t = new float[4 * n];
        for (int q = 0; q < n; q++) {
            int lam = com.dan.core.szene.spektrum.Spektrum.MIN + q;
            double[] cmf = com.dan.core.szene.spektrum.Spektrum.xyz(lam);
            double sn = com.dan.core.szene.spektrum.Spektrum.sonne(lam) * com.dan.core.szene.spektrum.Spektrum.KM;
            t[4 * q] = (float) Math.max(0, Math.min(N_SPEKTRAL - 1, (lam - SP_ERSTE) / SP_BREITE));
            for (int c = 0; c < 3; c++) {
                double m = 0;
                for (int k = 0; k < 3; k++) m += com.dan.core.szene.spektrum.Spektrum.XYZ_RGB[c][k] * cmf[k];
                t[4 * q + 1 + c] = (float) (m * sn);
            }
        }
        return t;
    }

    private static float[] tafel;

    /** Licht der 15 Wellenlängen (je Einheit der Sonne nach ASTM in 1 AE) nach linearem sRGB (cd/m²), wie ansicht.comp. */
    public static double[] nachRgb(double[] L) {
        if (tafel == null) tafel = spektralTafel();
        double[] rgb = new double[3], lg = new double[L.length];
        for (int k = 0; k < L.length; k++) lg[k] = Math.log(Math.max(1e-30, L[k]));
        for (int q = 0; q < tafel.length / 4; q++) {
            double x = tafel[4 * q];
            int c = (int) Math.max(1, Math.min(N_SPEKTRAL - 2, Math.floor(x + 0.5)));
            double u = x - c, v;
            if (Math.min(L[c - 1], Math.min(L[c], L[c + 1])) > 1e-30)
                v = Math.exp(lg[c - 1] * u * (u - 1) / 2 + lg[c] * (1 - u * u) + lg[c + 1] * u * (u + 1) / 2);
            else {
                int k = Math.min(N_SPEKTRAL - 2, (int) x);
                v = L[k] + (L[k + 1] - L[k]) * (x - k);
            }
            for (int k = 0; k < 3; k++) rgb[k] += tafel[4 * q + 1 + k] * v;
        }
        return rgb;
    }

    public static double[][] gewichte(boolean kasten) {
        double[][] g = new double[3][N_SPEKTRAL];
        for (int lam = com.dan.core.szene.spektrum.Spektrum.MIN; lam <= com.dan.core.szene.spektrum.Spektrum.MAX; lam++) {
            double x = Math.max(0, Math.min(N_SPEKTRAL - 1, (lam - SP_ERSTE) / SP_BREITE));
            int k0 = Math.min(N_SPEKTRAL - 2, (int) x);
            double a = x - k0;
            if (kasten) {
                k0 = (int) Math.round(x);
                a = 0;
            }
            double[] cmf = com.dan.core.szene.spektrum.Spektrum.xyz(lam);
            double sn = com.dan.core.szene.spektrum.Spektrum.sonne(lam) * com.dan.core.szene.spektrum.Spektrum.KM;
            for (int c = 0; c < 3; c++) {
                double m = 0;
                for (int q = 0; q < 3; q++) m += com.dan.core.szene.spektrum.Spektrum.XYZ_RGB[c][q] * cmf[q];
                g[c][k0] += (1 - a) * m * sn;
                if (a > 0) g[c][k0 + 1] += a * m * sn;
            }
        }
        return g;
    }

    /**
     * Himmel für Wellenlänge w je Einheit Bestrahlung: Strahlengang vom Auge (Höhe eye) in Richtung v, erste Streuung
     * genau, ab der zweiten aus J, mit dem Boden am Ende (Lambert, nur die Sonne), wie {@link Scattering#radiance}.
     */
    public double himmel(int w, double eye, double[] v, double[] l, int steps) {
        double r = R + Math.max(1, eye), mu = v[1];
        boolean boden = Scattering.hitsGround(r, mu);
        double tMax = boden ? Scattering.distToGround(r, mu) : Scattering.distToTop(r, mu);
        double nu = v[0] * l[0] + v[1] * l[1] + v[2] * l[2];
        double pR = Scattering.phaseRayleigh(nu), pM = Scattering.phaseMie(nu, Atmosphere.MIE_G), L = 0, tp = 1;
        double[] jj = new double[2];
        for (int s = 0; s < steps; s++) {
            double a = (double) s / steps, b = (s + 1.0) / steps, t0 = tMax * a * a, t1 = tMax * b * b, tm = 0.5 * (t0 + t1), dt = t1 - t0;
            double px = v[0] * tm, py = r + v[1] * tm, pz = v[2] * tm, ry = Math.sqrt(px * px + py * py + pz * pz), hy = ry - R;
            double ux = px / ry, uy = py / ry, uz = pz / ry, muy = ux * v[0] + uy * v[1] + uz * v[2], msy = ux * l[0] + uy * l[1] + uz * l[2];
            double sR = rayleigh[w] * Atmosphere.rayleighDensity(hy), sM = atm.mieScattering * Atmosphere.mieDensity(hy);
            double sch = Scattering.hitsGround(ry, msy) ? 0 : 1;
            j(w, hy, muy, msy, nu, jj);
            double src = sch * durch(w, ry, msy) * (sR * pR + sM * pM) + sR * jj[0] + sM * jj[1];
            double e = Math.max(1e-12, ext(hy, w)), sT = Math.exp(-e * dt);
            L += tp * src * (1 - sT) / e;
            tp *= sT;
        }
        if (boden) {
            double px = v[0] * tMax, py = r + v[1] * tMax, pz = v[2] * tMax, rr = Math.sqrt(px * px + py * py + pz * pz);
            double m = (px * l[0] + py * l[1] + pz * l[2]) / rr;
            if (m > 0) L += tp * durch(w, R, m) * m * atm.groundAlbedo / Math.PI;
        }
        return L;
    }

    /**
     * Bänder mit eigenen Beiwerten: Durchlässigkeit und J je Band genau (J einmal gerechnet und unter
     * {@code ~/.hallighooge/luft/} abgelegt, beim nächsten Start von dort gelesen; auf 16 Fäden rund 6 s je Band [S]).
     * Die Werte von J sind auf float16 gerundet, wie die Karte sie liest.
     */
    public Streuordnungen(Atmosphere atm, double[] lambda, double[] rayleigh, double[] ozon) {
        this(atm, lambda, rayleigh, ozon, false);
        laden();
    }

    private Streuordnungen(Atmosphere atm, double[] lambda, double[] rayleigh, double[] ozon, boolean rechnen) {
        this.atm = atm;
        this.lambda = lambda;
        this.rayleigh = rayleigh;
        this.ozon = ozon;
        int n = lambda.length;
        jR = new float[n][];
        jM = new float[n][];
        trans = new float[n][];
        IntStream.range(0, n).parallel().forEach(w -> trans[w] = baueDurch(w));
        if (rechnen) for (int w = 0; w < n; w++) rechne(w);
    }

    public static final int VERSION = 4;

    /** Schlüssel für den Speicher: Dunst, Albedo, Gitter, Beiwerte der Bänder. */
    String schluessel() {
        return String.format(java.util.Locale.ROOT, "luft_%d_%d_%d_%d_%d_%d_%d_%.6e_%.6e_%.4f_%08x", VERSION, NR, NMU, NMS, NPHI, ORDNUNGEN, N_THETA,
                atm.mieExtinction, atm.mieScattering, atm.groundAlbedo, java.util.Arrays.hashCode(rayleigh) * 31 + java.util.Arrays.hashCode(ozon));
    }

    private void laden() {
        String key = schluessel();
        int n = lambda.length, N = NR * NMU * NMS * NPHI;
        java.nio.file.Path datei = java.nio.file.Path.of(System.getProperty("user.home"), ".hallighooge", "luft", key + ".bin");
        if (java.nio.file.Files.isRegularFile(datei)) {
            try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.BufferedInputStream(java.nio.file.Files.newInputStream(datei), 1 << 20))) {
                for (int w = 0; w < n; w++) {
                    jR[w] = new float[N];
                    jM[w] = new float[N];
                    for (int i = 0; i < N; i++) jR[w][i] = in.readFloat();
                    for (int i = 0; i < N; i++) jM[w][i] = in.readFloat();
                }
                return;
            } catch (java.io.IOException e) {
                // neu rechnen
            }
        }
        long t0 = System.nanoTime();
        for (int w = 0; w < n; w++) {
            rechne(w);
            for (float[] f : new float[][] {jR[w], jM[w]}) for (int i = 0; i < N; i++) f[i] = Float.float16ToFloat(Float.floatToFloat16(f[i]));
        }
        System.out.printf(java.util.Locale.GERMANY, "Streuordnungen: %d Wellenlängen in %.1f s gerechnet (%s)%n", n, (System.nanoTime() - t0) / 1e9, datei);
        try {
            java.nio.file.Files.createDirectories(datei.getParent());
            java.nio.file.Path tmp = datei.resolveSibling(key + ".tmp");
            try (java.io.DataOutputStream out = new java.io.DataOutputStream(new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(tmp), 1 << 20))) {
                for (int w = 0; w < n; w++) {
                    for (float v : jR[w]) out.writeFloat(v);
                    for (float v : jM[w]) out.writeFloat(v);
                }
            }
            java.nio.file.Files.move(tmp, datei, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) {
            System.out.println("Streuordnungen: nicht abgelegt: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ Luft

    double ext(double h, int w) {
        return rayleigh[w] * Atmosphere.rayleighDensity(h) + atm.mieExtinction * Atmosphere.mieDensity(h) + ozon[w] * Atmosphere.ozoneDensity(h);
    }

    private float[] baueDurch(int w) {
        float[] t = new float[Scattering.TRANS_W * Scattering.TRANS_H];
        for (int j = 0; j < Scattering.TRANS_H; j++)
            for (int i = 0; i < Scattering.TRANS_W; i++) {
                double[] rm = Scattering.transRMu(i / (Scattering.TRANS_W - 1.0), j / (Scattering.TRANS_H - 1.0));
                double r = rm[0], mu = rm[1], d = Scattering.distToTop(r, mu), od = 0;
                int n = Scattering.TRANS_STEPS;
                for (int q = 0; q < n; q++) {
                    double a = (double) q / n, b = (q + 1.0) / n, t0 = d * a * a, t1 = d * b * b, tm = 0.5 * (t0 + t1);
                    double h = Math.sqrt(r * r + tm * tm + 2 * r * mu * tm) - R;
                    od += ext(h, w) * (t1 - t0);
                }
                t[j * Scattering.TRANS_W + i] = (float) od;           // seit P7e (F59 A): optische Tiefe
            }
        return t;
    }

    /** Durchlässigkeit von (r, μ) bis zur Oberkante für Wellenlänge w (optische Tiefe bilinear wie {@link Scattering}). */
    public double durch(int w, double r, double mu) {
        double[] uv = Scattering.transUv(r, mu);
        double px = Math.max(0, Math.min(1, uv[0])) * (Scattering.TRANS_W - 1), py = Math.max(0, Math.min(1, uv[1])) * (Scattering.TRANS_H - 1);
        int i0 = Math.min(Scattering.TRANS_W - 2, (int) px), j0 = Math.min(Scattering.TRANS_H - 2, (int) py);
        double fx = px - i0, fy = py - j0;
        float[] t = trans[w];
        int W = Scattering.TRANS_W;
        double a = t[j0 * W + i0], b = t[j0 * W + i0 + 1], c = t[(j0 + 1) * W + i0], d = t[(j0 + 1) * W + i0 + 1];
        return Math.exp(-((a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy));
    }

    // ------------------------------------------------------------------ Tabelle lesen

    /** J_R, J_M für Wellenlänge w an (h, μ, μ_s, ν), vierfach linear. */
    public void j(int w, double h, double mu, double ms, double nu, double[] aus) {
        if (ms < MS_MIN) {                                                  // Sonne tiefer als das Gitter: Nacht
            aus[0] = aus[1] = 0;
            return;
        }
        vierfach(jR[w], jM[w], ih(h), imu(mu), ims(ms), iphi(phi(mu, ms, nu)), aus);
    }

    /**
     * J über μ_s logarithmisch zwischen den Knoten (seit P7e, Befund Dämmerung: unter der Kimm fällt J mit der Tiefe der
     * Sonne fast exponentiell, linear lag es bei −3° bis +30 % zu hell); wo ein Knoten 0 ist, linear. Abschalten nur zum Vergleich.
     */
    public static boolean logMs = true;

    static void vierfach(float[] a, float[] b, double fi, double fj, double fk, double fm, double[] aus) {
        int i = Math.min(NR - 2, (int) fi), j = Math.min(NMU - 2, (int) fj), k = Math.min(NMS - 2, (int) fk), m = Math.min(NPHI - 2, (int) fm);
        double wi = fi - i, wj = fj - j, wk = fk - k, wm = fm - m, sa = 0, sb = 0;
        if (logMs) {
            // je j: in μ_s und dann in der Höhe logarithmisch (seit P7e, F59 A), φ zuerst linear
            for (int dj = 0; dj < 2; dj++) {
                double gj = dj == 0 ? 1 - wj : wj;
                double a0 = 0, a1 = 0, b0 = 0, b1 = 0;
                for (int di = 0; di < 2; di++) {
                    int o0 = index(i + di, j + dj, k, m), o1 = index(i + di, j + dj, k + 1, m);
                    double va = geo((1 - wm) * a[o0] + wm * a[o0 + 1], (1 - wm) * a[o1] + wm * a[o1 + 1], wk);
                    double vb = b == null ? 0 : geo((1 - wm) * b[o0] + wm * b[o0 + 1], (1 - wm) * b[o1] + wm * b[o1 + 1], wk);
                    if (di == 0) { a0 = va; b0 = vb; } else { a1 = va; b1 = vb; }
                }
                sa += gj * geo(a0, a1, wi);
                sb += gj * geo(b0, b1, wi);
            }
            aus[0] = sa;
            aus[1] = sb;
            return;
        }
        for (int di = 0; di < 2; di++) for (int dj = 0; dj < 2; dj++) for (int dk = 0; dk < 2; dk++) {
            double g = (di == 0 ? 1 - wi : wi) * (dj == 0 ? 1 - wj : wj) * (dk == 0 ? 1 - wk : wk);
            int o = index(i + di, j + dj, k + dk, m);
            sa += g * ((1 - wm) * a[o] + wm * a[o + 1]);
            if (b != null) sb += g * ((1 - wm) * b[o] + wm * b[o + 1]);
        }
        aus[0] = sa;
        aus[1] = sb;
    }

    static double geo(double x, double y, double w) {
        double e = 1e-30;
        if (x <= e || y <= e) return (1 - w) * x + w * y;
        return Math.exp((1 - w) * Math.log(x) + w * Math.log(y));
    }

    // ------------------------------------------------------------------ Streuordnungen

    private void rechne(int w) {
        int N = NR * NMU * NMS * NPHI;
        double[] eBoden = new double[NE];
        // Ordnung 1: Licht, einmal gestreut (Luft) oder am Boden reflektiert
        float[] L1 = new float[N];
        IntStream.range(0, NR * NMU * NMS).parallel().forEach(q -> {
            int i = q / (NMU * NMS), j = (q / NMS) % NMU, k = q % NMS;
            for (int m = 0; m < NPHI; m++) L1[index(i, j, k, m)] = (float) strahl(w, i, j, k, m, null, null, null, true);
        });
        float[] L = L1;
        double[] eNeu = bodenLicht(L);
        float[] sumR = new float[N], sumM = new float[N];
        float[] vR = null, vM = null;
        for (int n = 2; n <= ORDNUNGEN; n++) {
            float[] dR = new float[N], dM = new float[N];
            final float[] Lv = L;
            IntStream.range(0, NR * NMS).parallel().forEach(q -> dichte(Lv, q / NMS, q % NMS, dR, dM));
            for (int x = 0; x < N; x++) {
                sumR[x] += dR[x];
                sumM[x] += dM[x];
            }
            if (n == ORDNUNGEN) {
                // die Ordnungen danach als geometrische Reihe je Knoten (q = J^n / J^(n−1), höchstens 0,95): wie 12
                // Ordnungen auf 0,2 % (Befund P7e; sechs allein fehlten an der Kimm im Blau bis 3,4 %)
                if (vR != null) for (int x = 0; x < N; x++) {
                    double qR = vR[x] > 0 ? Math.min(0.95, Math.max(0, dR[x] / vR[x])) : 0, qM = vM[x] > 0 ? Math.min(0.95, Math.max(0, dM[x] / vM[x])) : 0;
                    sumR[x] += (float) (dR[x] * qR / (1 - qR));
                    sumM[x] += (float) (dM[x] * qM / (1 - qM));
                }
                break;
            }
            vR = dR;
            vM = dM;
            final double[] eAlt = eNeu;
            float[] Ln = new float[N];
            IntStream.range(0, NR * NMU * NMS).parallel().forEach(q -> {
                int i = q / (NMU * NMS), j = (q / NMS) % NMU, k = q % NMS;
                for (int m = 0; m < NPHI; m++) Ln[index(i, j, k, m)] = (float) strahl(w, i, j, k, m, dR, dM, eAlt, false);
            });
            L = Ln;
            eNeu = bodenLicht(L);
        }
        jR[w] = sumR;
        jM[w] = sumM;
    }

    /**
     * L am Knoten (i, j, k, m): Strahlengang von dort in Richtung v; Quelle der Ordnung 1 (Sonne, Phasenfunktion) oder
     * σ_R J_R + σ_M J_M aus der Dichte der Ordnung; am Boden Lambert mit E der Ordnung davor (Ordnung 1: Sonne).
     */
    private double strahl(int w, int i, int j, int k, int m, float[] dR, float[] dM, double[] eAlt, boolean erste) {
        double h = Math.max(1, Math.min(RT - R - 1, hVon(i))), r = R + h, mu = muVon(j), ms = msVon(k), ph = phiVon(m);
        double sm = Math.sqrt(Math.max(0, 1 - mu * mu)), ss = Math.sqrt(Math.max(0, 1 - ms * ms));
        double nu = mu * ms + sm * ss * Math.cos(ph);
        boolean boden = Scattering.hitsGround(r, mu);
        double tMax = boden ? Scattering.distToGround(r, mu) : Scattering.distToTop(r, mu);
        double pR = Scattering.phaseRayleigh(nu), pM = Scattering.phaseMie(nu, Atmosphere.MIE_G);
        double L = 0, tp = 1;
        double[] jj = new double[2];
        for (int s = 0; s < SCHRITTE; s++) {
            double a = (double) s / SCHRITTE, b = (s + 1.0) / SCHRITTE, t0 = tMax * a * a, t1 = tMax * b * b, tm = 0.5 * (t0 + t1), dt = t1 - t0;
            double ry = Math.sqrt(r * r + tm * tm + 2 * r * mu * tm), hy = ry - R;
            double muy = (r * mu + tm) / ry, msy = (r * ms + tm * nu) / ry;
            double dRy = Atmosphere.rayleighDensity(hy), dMy = Atmosphere.mieDensity(hy);
            double sR = rayleigh[w] * dRy, sM = atm.mieScattering * dMy, src;
            if (erste) {
                double sch = Scattering.hitsGround(ry, msy) ? 0 : 1;
                src = sch * durch(w, ry, msy) * (sR * pR + sM * pM);
            } else {
                if (msy < MS_MIN) src = 0;
                else {
                    vierfach(dR, dM, ih(hy), imu(muy), ims(msy), iphi(phi(muy, msy, nu)), jj);
                    src = sR * jj[0] + sM * jj[1];
                }
            }
            double e = Math.max(1e-12, ext(hy, w)), sT = Math.exp(-e * dt);
            L += tp * src * (1 - sT) / e;
            tp *= sT;
        }
        if (boden) {
            double msg = (r * ms + tMax * nu) / R;
            double eg = erste ? (msg > 0 ? msg * durch(w, R, msg) : 0) : bodenWert(eAlt, msg);
            L += tp * atm.groundAlbedo / Math.PI * eg;
        }
        return L;
    }

    static double bodenWert(double[] e, double ms) {
        double f = (ms - MS_MIN) / (1 - MS_MIN) * (NE - 1);
        f = Math.max(0, Math.min(NE - 1, f));
        int i = Math.min(NE - 2, (int) f);
        return e[i] + (f - i) * (e[i + 1] - e[i]);
    }

    /** E am Boden aus L (Knoten i = 0): ∫ L μ dω über den Himmel, je μ_s auf NE Stufen. */
    private double[] bodenLicht(float[] L) {
        double[] e = new double[NE];
        double[] jj = new double[2];
        for (int q = 0; q < NE; q++) {
            double ms = MS_MIN + (1 - MS_MIN) * q / (NE - 1), s = 0;
            for (int a = 0; a < N_E; a++) {
                double mu = (a + 0.5) / N_E;
                for (int b = 0; b < 2 * N_E; b++) {
                    double ph = Math.PI * (b + 0.5) / (2 * N_E);
                    vierfach(L, null, 0, imu(mu), ims(ms), iphi(ph), jj);
                    s += jj[0] * mu;
                }
            }
            // dω = dμ dφ über φ ∈ [0, 2π] (symmetrisch: zweimal [0, π])
            e[q] = s * (1.0 / N_E) * (Math.PI / (2 * N_E)) * 2;
        }
        return e;
    }

    /** Knoten der Richtungen um v: cos θ = 1 − 2 u³ (dicht nach vorn), Gauß-Legendre in u; ψ gleichmäßig. */
    static final double[] GL_X, GL_W;
    static {
        double[][] g = gaussLegendre(N_THETA);
        GL_X = g[0];
        GL_W = g[1];
    }

    /**
     * J der nächsten Ordnung an allen Knoten (i, ·, k, ·) aus L der Ordnung davor (an derselben Höhe und Sonne). Zwei
     * Quadraturen je N_THETA × N_PSI, um den Blick (Spitze der Phasenfunktion) und um die Sonne (Kranz in L), nach
     * Veach (1997) gewichtet (Dichte der Knoten ∝ 1/u², u = ((1 − cos θ)/2)^⅓): Um den Blick allein fehlten dem Kranz
     * bei Sonne 5° quer zu ihr an der Kimm im Rot 6 % (Befund P7e). Die Richtungen um die Sonne und ihr L liegen je
     * Scheibe (i, k) einmal fest.
     */
    private void dichte(float[] L, int i, int k, float[] dR, float[] dM) {
        double ms = msVon(k), ss = Math.sqrt(Math.max(0, 1 - ms * ms));
        double[] l = {ss, ms, 0};
        int nB = N_THETA * N_PSI;
        double[] bx = new double[nB], by = new double[nB], bz = new double[nB], bL = new double[nB], bW = new double[nB], bU = new double[nB];
        double[] f1 = Math.abs(l[1]) < 0.9 ? kreuz(l, new double[] {0, 1, 0}) : kreuz(l, new double[] {1, 0, 0});
        norm(f1);
        double[] f2 = kreuz(l, f1);
        double dps = 2 * Math.PI / N_PSI;
        double[] cps = new double[N_PSI], sps = new double[N_PSI];
        for (int b = 0; b < N_PSI; b++) {
            cps[b] = Math.cos(dps * (b + 0.5));
            sps[b] = Math.sin(dps * (b + 0.5));
        }
        double[] pRa = new double[N_THETA], pMa = new double[N_THETA];
        for (int a = 0; a < N_THETA; a++) {
            double u = GL_X[a], ct = 1 - 2 * u * u * u, wct = GL_W[a] * 6 * u * u, st = Math.sqrt(Math.max(0, 1 - ct * ct));
            pRa[a] = Scattering.phaseRayleigh(ct);
            pMa[a] = Scattering.phaseMie(ct, Atmosphere.MIE_G);
            for (int b = 0; b < N_PSI; b++) {
                int q = a * N_PSI + b;
                bx[q] = ct * l[0] + st * (cps[b] * f1[0] + sps[b] * f2[0]);
                by[q] = ct * l[1] + st * (cps[b] * f1[1] + sps[b] * f2[1]);
                bz[q] = ct * l[2] + st * (cps[b] * f1[2] + sps[b] * f2[2]);
                bL[q] = zweifach(L, i, k, imu(Math.max(-1, Math.min(1, by[q]))), iphi(Math.abs(Math.atan2(bz[q], bx[q]))));
                bW[q] = dps * wct;
                bU[q] = u * u;
            }
        }
        for (int j = 0; j < NMU; j++)
            for (int m = 0; m < NPHI; m++) {
                double mu = muVon(j), ph = phiVon(m), sm = Math.sqrt(Math.max(0, 1 - mu * mu));
                double[] v = {sm * Math.cos(ph), mu, sm * Math.sin(ph)};
                // Basis senkrecht zu v
                double[] e1 = Math.abs(v[1]) < 0.9 ? kreuz(v, new double[] {0, 1, 0}) : kreuz(v, new double[] {1, 0, 0});
                norm(e1);
                double[] e2 = kreuz(v, e1);
                double sRsum = 0, sMsum = 0;
                // um den Blick
                for (int a = 0; a < N_THETA; a++) {
                    double u = GL_X[a], uv = u * u, ct = 1 - 2 * u * u * u, wct = GL_W[a] * 6 * u * u, st = Math.sqrt(Math.max(0, 1 - ct * ct)), sl = 0;
                    for (int b = 0; b < N_PSI; b++) {
                        double ox = ct * v[0] + st * (cps[b] * e1[0] + sps[b] * e2[0]), oy = ct * v[1] + st * (cps[b] * e1[1] + sps[b] * e2[1]), oz = ct * v[2] + st * (cps[b] * e1[2] + sps[b] * e2[2]);
                        double cl = ox * l[0] + oy * l[1] + oz * l[2], ul = Math.cbrt(Math.max(0, (1 - cl) / 2));
                        ul *= ul;
                        double wg = uv + ul < 1e-30 ? 0.5 : ul / (uv + ul);
                        sl += wg * zweifach(L, i, k, imu(Math.max(-1, Math.min(1, oy))), iphi(Math.abs(Math.atan2(oz, ox))));
                    }
                    sl *= dps * wct;
                    sRsum += pRa[a] * sl;
                    sMsum += pMa[a] * sl;
                }
                // um die Sonne
                for (int q = 0; q < nB; q++) {
                    double cv = bx[q] * v[0] + by[q] * v[1] + bz[q] * v[2], uv = Math.cbrt(Math.max(0, (1 - cv) / 2));
                    uv *= uv;
                    double wg = uv + bU[q] < 1e-30 ? 0.5 : uv / (uv + bU[q]), sl = wg * bW[q] * bL[q];
                    sRsum += Scattering.phaseRayleigh(cv) * sl;
                    sMsum += Scattering.phaseMie(cv, Atmosphere.MIE_G) * sl;
                }
                int o = index(i, j, k, m);
                dR[o] = (float) sRsum;
                dM[o] = (float) sMsum;
            }
    }

    /** L an (i, ·, k, ·) bilinear in (j, m). */
    static double zweifach(float[] L, int i, int k, double fj, double fm) {
        int j = Math.min(NMU - 2, (int) fj), m = Math.min(NPHI - 2, (int) fm);
        double wj = fj - j, wm = fm - m;
        int o0 = index(i, j, k, m), o1 = index(i, j + 1, k, m);
        return (1 - wj) * ((1 - wm) * L[o0] + wm * L[o0 + 1]) + wj * ((1 - wm) * L[o1] + wm * L[o1 + 1]);
    }

    static double[] kreuz(double[] a, double[] b) { return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }

    static void norm(double[] a) {
        double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        a[0] /= l;
        a[1] /= l;
        a[2] /= l;
    }

    /** Knoten und Gewichte nach Gauß-Legendre auf [0, 1]. */
    static double[][] gaussLegendre(int n) {
        double[] x = new double[n], w = new double[n];
        for (int i = 0; i < n; i++) {
            double z = Math.cos(Math.PI * (i + 0.75) / (n + 0.5)), pp = 0;
            for (int it = 0; it < 100; it++) {
                double p1 = 1, p2 = 0;
                for (int j = 1; j <= n; j++) {
                    double p3 = p2;
                    p2 = p1;
                    p1 = ((2 * j - 1) * z * p2 - (j - 1) * p3) / j;
                }
                pp = n * (z * p1 - p2) / (z * z - 1);
                double z1 = z;
                z = z1 - p1 / pp;
                if (Math.abs(z - z1) < 1e-15) break;
            }
            x[i] = 0.5 * (1 - z);
            w[i] = 1.0 / ((1 - z * z) * pp * pp);
        }
        return new double[][] {x, w};
    }
}
