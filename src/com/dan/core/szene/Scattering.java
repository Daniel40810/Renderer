package com.dan.core.szene;

/**
 * Die Streuung des Lichts in der Atmosphäre auf dem Prozessor, Rechenweg nach Hillaire, „A Scalable and Production
 * Ready Sky and Atmosphere Rendering Technique“ (2020): eine Tabelle der Durchlässigkeit, eine Tabelle der
 * Mehrfachstreuung und je Blickrichtung ein Strahlengang mit 32 Schritten. Die Grafikkarte rechnet in
 * {@code sky_common.glsl} Zeile für Zeile dasselbe; die Prüfung vergleicht beide.
 * <p>
 * Raum: lokal am Auge, y nach oben, das Auge in Höhe {@code r} über dem Erdmittelpunkt auf der y-Achse.
 * Strahldichte und Bestrahlungsstärke in den Einheiten der Szene: in render.air bringt die Sonne außerhalb der
 * Atmosphäre ihre Beleuchtungsstärke in lx je Kanal, das Ergebnis ist dann in cd/m².
 * <p>
 * Kopie von {@code com.dan.sky.Scattering} (P6c, Werkbuch HalligHooge Teil A, 12.7), dazu {@link #strecke} für die
 * Luftperspektive bis zu einem Punkt der Szene.
 */
public final class Scattering {
    public static final int TRANS_W = 256, TRANS_H = 64, MS_N = 32;
    /** VIEW_STEPS: Schritte der Himmelsansicht, seit P7e 64 [G] (32 lagen bei Sonne 0° zur Sonne an der Kimm 1,8 % zu hoch). */
    public static final int TRANS_STEPS = 64, MS_STEPS = 20, MS_DIRS = 8, VIEW_STEPS = 64;
    static final double R = Atmosphere.BOTTOM, RT = Atmosphere.TOP;
    static final double H = Math.sqrt(RT * RT - R * R);

    public final Atmosphere atm;
    /** Tabellen: Durchlässigkeit als optische Tiefe (TRANS_W × TRANS_H × 3, seit P7e) und Mehrfachstreuung Ψ je Einheit Bestrahlung (MS_N² × 3). */
    final float[] trans = new float[TRANS_W * TRANS_H * 3], ms = new float[MS_N * MS_N * 3];

    public Scattering(Atmosphere atm) {
        this.atm = atm;
        buildTransmittance();
        buildMultiScattering();
    }

    // ------------------------------------------------------------------ Geometrie

    /** Abstand bis zur Oberkante der Atmosphäre von Höhe r (vom Mittelpunkt) in Richtung mit Kosinus mu zur Senkrechten. */
    static double distToTop(double r, double mu) {
        double disc = (RT - r) * (RT + r) + r * r * mu * mu;
        return Math.max(0, -r * mu + Math.sqrt(Math.max(0, disc)));
    }

    /** Diskriminante für den Boden; ≥ 0 und mu &lt; 0 heißt: der Strahl trifft die Erde. */
    static double groundDisc(double r, double mu) { return (R - r) * (R + r) + r * r * mu * mu; }

    static boolean hitsGround(double r, double mu) { return mu < 0 && groundDisc(r, mu) >= 0; }

    static double distToGround(double r, double mu) { return Math.max(0, -r * mu - Math.sqrt(Math.max(0, groundDisc(r, mu)))); }

    // ------------------------------------------------------------------ Durchlässigkeit

    /** Tabellenkoordinaten (x_mu, x_r) in 0..1 für Höhe r und Richtung mu (Bruneton 2017). */
    static double[] transUv(double r, double mu) {
        double rho = Math.sqrt(Math.max(0, r * r - R * R));
        double d = distToTop(r, mu), dMin = RT - r, dMax = rho + H;
        return new double[]{(d - dMin) / Math.max(1e-6, dMax - dMin), rho / H};
    }

    /** Umkehrung von {@link #transUv}: (r, mu) zu den Tabellenkoordinaten. */
    static double[] transRMu(double xMu, double xR) {
        double rho = H * xR, r = Math.sqrt(rho * rho + R * R);
        double dMin = RT - r, dMax = rho + H, d = dMin + xMu * (dMax - dMin);
        double mu = d <= 0 ? 1 : (H * H - rho * rho - d * d) / (2 * r * d);
        return new double[]{r, Math.max(-1, Math.min(1, mu))};
    }

    /** Durchlässigkeit von (r, mu) bis zur Oberkante, gerechnet mit n Schritten (ohne Erdschatten). */
    public double[] transmittanceExact(double r, double mu, int n) {
        double[] od = opticalDepthExact(r, mu, n);
        return new double[]{Math.exp(-od[0]), Math.exp(-od[1]), Math.exp(-od[2])};
    }

    /** Optische Tiefe von (r, mu) bis zur Oberkante, n Schritte. */
    public double[] opticalDepthExact(double r, double mu, int n) { return opticalDepth(atm, r, mu, n); }

    /**
     * Seit P10d: die Durchlässigkeit der drei Kanäle, wie die Karte sie aus der Tabelle liest (die optische Tiefe an den
     * vier Stützstellen um (r, mu), je mit {@link #TRANS_STEPS} Schritten wie {@code buildTransmittance}, in float wie
     * die Tabelle, bilinear, dann e^−τ) – ohne die ganze Tabelle zu bauen.
     */
    public static double[] durchWieTabelle(Atmosphere atm, double r, double mu) {
        double[] uv = transUv(r, mu);
        double px = Math.max(0, Math.min(1, uv[0])) * (TRANS_W - 1), py = Math.max(0, Math.min(1, uv[1])) * (TRANS_H - 1);
        int i0 = Math.min(TRANS_W - 2, (int) px), j0 = Math.min(TRANS_H - 2, (int) py);
        double fx = px - i0, fy = py - j0;
        double[][] od = new double[4][];
        for (int q = 0; q < 4; q++) {
            double[] rm = transRMu((i0 + (q & 1)) / (TRANS_W - 1.0), (j0 + (q >> 1)) / (TRANS_H - 1.0));
            od[q] = opticalDepth(atm, rm[0], rm[1], TRANS_STEPS);
        }
        double[] t = new double[3];
        for (int c = 0; c < 3; c++) {
            double a = (float) od[0][c], b = (float) od[1][c], d = (float) od[2][c], e = (float) od[3][c];
            t[c] = Math.exp(-((a + (b - a) * fx) * (1 - fy) + (d + (e - d) * fx) * fy));
        }
        return t;
    }

    /** Optische Tiefe von (r, mu) bis zur Oberkante für die Luft atm, n Schritte (quadratisch wachsend). */
    static double[] opticalDepth(Atmosphere atm, double r, double mu, int n) {
        double d = distToTop(r, mu);
        double[] od = new double[3];
        for (int k = 0; k < n; k++) {
            // Schritte wachsen quadratisch: dicht am Anfang, wo Luft und Dunst dicht sind
            double a = (double) k / n, b = (k + 1.0) / n, t0 = d * a * a, t1 = d * b * b;
            double t = 0.5 * (t0 + t1), dt = t1 - t0;
            double h = Math.sqrt(r * r + t * t + 2 * r * mu * t) - R;
            for (int c = 0; c < 3; c++) od[c] += atm.extinction(h, c) * dt;
        }
        return od;
    }

    private void buildTransmittance() {
        for (int j = 0; j < TRANS_H; j++)
            for (int i = 0; i < TRANS_W; i++) {
                double[] rm = transRMu(i / (TRANS_W - 1.0), j / (TRANS_H - 1.0));
                double[] t = opticalDepthExact(rm[0], rm[1], TRANS_STEPS);     // seit P7e (F59 A): optische Tiefe
                int o = (j * TRANS_W + i) * 3;
                for (int c = 0; c < 3; c++) trans[o + c] = (float) t[c];
            }
    }

    /**
     * Durchlässigkeit aus der Tabelle (wie die Grafikkarte): seit P7e (F59 A) liegt die optische Tiefe in der Tabelle,
     * bilinear gelesen, dann e^−τ; T selbst bilinear lag im dichten Dunst an der Kimm +2,2 % zu hoch.
     */
    public double[] transmittance(double r, double mu) {
        double[] uv = transUv(r, mu), od = bilinear(trans, TRANS_W, TRANS_H, uv[0], uv[1]);
        return new double[] {Math.exp(-od[0]), Math.exp(-od[1]), Math.exp(-od[2])};
    }

    // ------------------------------------------------------------------ Mehrfachstreuung

    /** Ψ aus der Tabelle: Beitrag aller Streuungen ab der zweiten je Einheit Bestrahlung, für Höhe r und Sonnen-Kosinus muS. */
    public double[] multiScattering(double r, double muS) {
        return bilinear(ms, MS_N, MS_N, muS * 0.5 + 0.5, (r - R) / (RT - R));
    }

    private void buildMultiScattering() {
        double[] one = {1, 1, 1};
        for (int j = 0; j < MS_N; j++)
            for (int i = 0; i < MS_N; i++) {
                double muS = -1 + 2.0 * i / (MS_N - 1);
                double r = R + Math.max(1, Math.min(RT - R - 1, j / (MS_N - 1.0) * (RT - R)));
                double[] sun = {Math.sqrt(Math.max(0, 1 - muS * muS)), muS, 0};
                double[] l2 = new double[3], f = new double[3];
                for (int a = 0; a < MS_DIRS; a++)
                    for (int b = 0; b < MS_DIRS; b++) {
                        double th = 2 * Math.PI * (a + 0.5) / MS_DIRS, ph = Math.acos(1 - 2 * (b + 0.5) / MS_DIRS);
                        double[] v = {Math.cos(th) * Math.sin(ph), Math.cos(ph), Math.sin(th) * Math.sin(ph)};
                        double[][] res = integrate(r, v, sun, one, null, null, MS_STEPS, false, false);
                        for (int c = 0; c < 3; c++) { l2[c] += res[0][c]; f[c] += res[1][c]; }
                    }
                int o = (j * MS_N + i) * 3;
                double nd = MS_DIRS * MS_DIRS;
                for (int c = 0; c < 3; c++) ms[o + c] = (float) (l2[c] / nd / (1 - Math.min(0.99, f[c] / nd)));
            }
    }

    // ------------------------------------------------------------------ Strahlengang

    static double phaseRayleigh(double c) { return 3.0 / (16 * Math.PI) * (1 + c * c); }

    static double phaseMie(double c, double g) {
        double g2 = g * g;
        return 3.0 / (8 * Math.PI) * (1 - g2) * (1 + c * c) / ((2 + g2) * Math.pow(Math.max(1e-6, 1 + g2 - 2 * g * c), 1.5));
    }

    /**
     * Ein Strahlengang von Höhe r in Richtung v (Einheitsvektor, y oben) mit einer oder zwei Lichtquellen (Richtung,
     * Bestrahlung außerhalb der Atmosphäre). Ergebnis: [0] gestreutes Licht, [1] Übertragungsfaktor f_ms (nur für die
     * Tabelle der Mehrfachstreuung), [2] Durchlässigkeit bis zum Ende des Wegs.
     * physical = true: echte Phasenfunktionen und Mehrfachstreuung (Himmel); false: isotrop, ohne Ψ (Tabelle Ψ).
     */
    double[][] integrate(double r, double[] v, double[] l1, double[] e1, double[] l2, double[] e2, int steps,
                         boolean quadratic, boolean physical) {
        return integrate(r, v, l1, e1, l2, e2, steps, quadratic, physical, physical);
    }

    /** Wie oben; {@code mehrfach} schaltet Ψ (alle Streuungen ab der zweiten) zu (seit P7e, zur Zerlegung). */
    double[][] integrate(double r, double[] v, double[] l1, double[] e1, double[] l2, double[] e2, int steps,
                         boolean quadratic, boolean physical, boolean mehrfach) {
        double mu = v[1];
        boolean ground = hitsGround(r, mu);
        double tMax = ground ? distToGround(r, mu) : distToTop(r, mu);
        double[] L = new double[3], f = new double[3], tp = {1, 1, 1};
        double g = Atmosphere.MIE_G, iso = 1 / (4 * Math.PI);
        double c1 = dot(v, l1), c2 = l2 == null ? 0 : dot(v, l2);
        double pR1 = physical ? phaseRayleigh(c1) : iso, pM1 = physical ? phaseMie(c1, g) : iso;
        double pR2 = physical ? phaseRayleigh(c2) : iso, pM2 = physical ? phaseMie(c2, g) : iso;
        for (int k = 0; k < steps; k++) {
            double a = (double) k / steps, b = (k + 1.0) / steps;
            double t0 = quadratic ? tMax * a * a : tMax * a, t1 = quadratic ? tMax * b * b : tMax * b;
            double tm = 0.5 * (t0 + t1), dt = t1 - t0;
            double px = v[0] * tm, py = r + v[1] * tm, pz = v[2] * tm;
            double rr = Math.sqrt(px * px + py * py + pz * pz), h = rr - R;
            double dR = Atmosphere.rayleighDensity(h), dM = Atmosphere.mieDensity(h);
            double scM = atm.mieScattering * dM;
            double[] S = new double[3];
            addLight(S, px / rr, py / rr, pz / rr, rr, v, l1, e1, pR1, pM1, dR, scM, physical, mehrfach);
            if (l2 != null) addLight(S, px / rr, py / rr, pz / rr, rr, v, l2, e2, pR2, pM2, dR, scM, physical, mehrfach);
            for (int c = 0; c < 3; c++) {
                double ext = Math.max(1e-12, atm.extinction(h, c));
                double sT = Math.exp(-ext * dt);
                L[c] += tp[c] * (S[c] - S[c] * sT) / ext;
                double sc = Atmosphere.RAYLEIGH[c] * dR + scM;
                f[c] += tp[c] * (sc - sc * sT) / ext;
                tp[c] *= sT;
            }
        }
        if (ground) {
            double px = v[0] * tMax, py = r + v[1] * tMax, pz = v[2] * tMax, rr = Math.sqrt(px * px + py * py + pz * pz);
            groundLight(L, tp, px / rr, py / rr, pz / rr, l1, e1);
            if (l2 != null) groundLight(L, tp, px / rr, py / rr, pz / rr, l2, e2);
        }
        return new double[][]{L, f, tp};
    }

    private void addLight(double[] S, double ux, double uy, double uz, double rr, double[] v, double[] l, double[] e,
                          double pR, double pM, double dR, double scM, boolean physical, boolean mehrfach) {
        double muL = ux * l[0] + uy * l[1] + uz * l[2];
        double[] tl = transmittance(rr, muL);
        double shadow = hitsGround(rr, muL) ? 0 : 1;
        double[] psi = mehrfach && hillaire ? multiScattering(rr, muL) : null;
        double muV = ux * v[0] + uy * v[1] + uz * v[2], nu = dot(v, l);
        double[] jj = new double[2];
        Streuordnungen so = mehrfach && !hillaire ? streuordnungen() : null;
        for (int c = 0; c < 3; c++) {
            double scR = Atmosphere.RAYLEIGH[c] * dR;
            double s = shadow * tl[c] * (scR * pR + scM * pM);
            if (psi != null) s += psi[c] * (scR + scM);
            if (so != null) {
                so.j(c, rr - R, muV, muL, nu, jj);
                s += scR * jj[0] + scM * jj[1];
            }
            S[c] += e[c] * s;
        }
    }

    private Streuordnungen spektral;

    /** Die Streuordnungen dieser Luft in den 15 Wellenlängen der Himmelsansicht (beim ersten Gebrauch gerechnet). */
    public synchronized Streuordnungen spektral() {
        if (spektral == null) spektral = Streuordnungen.spektral(atm);
        return spektral;
    }

    /**
     * Himmel spektral (seit P7e, F58 A): 15 Wellenlängen, dazwischen quadratisch im Logarithmus, nach linearem sRGB wie die Referenz (cd/m²); Sonne nach ASTM
     * mal {@code faktor} (1 in 1 AE). Nur die Sonne, ohne Scheibe.
     */
    public double[] radianceSpektral(double eye, double[] v, double[] l, double faktor, int steps) {
        Streuordnungen sp = spektral();
        double[] L = new double[Streuordnungen.N_SPEKTRAL];
        for (int k = 0; k < L.length; k++) L[k] = sp.himmel(k, eye, v, l, steps);
        double[] rgb = Streuordnungen.nachRgb(L);
        for (int c = 0; c < 3; c++) rgb[c] *= faktor;
        return rgb;
    }

    /** Mehrfachstreuung nach Hillaire (Ψ, bis P7d) statt nach Richtung ({@link Streuordnungen}, seit P7e). */
    public boolean hillaire;
    private Streuordnungen streuordnungen;

    /** Die Streuordnungen dieser Luft in den drei Kanälen (beim ersten Gebrauch gerechnet). */
    public synchronized Streuordnungen streuordnungen() {
        if (streuordnungen == null) streuordnungen = Streuordnungen.kanaele(atm);
        return streuordnungen;
    }

    private void groundLight(double[] L, double[] tp, double ux, double uy, double uz, double[] l, double[] e) {
        double muL = ux * l[0] + uy * l[1] + uz * l[2];
        if (muL <= 0) return;
        double[] tl = transmittance(R, muL);
        for (int c = 0; c < 3; c++) L[c] += tp[c] * e[c] * tl[c] * muL * atm.groundAlbedo / Math.PI;
    }

    /**
     * Strahldichte des Himmels in Richtung v vom Auge in Höhe eye (m über dem Meer), nur Streuung (ohne Sonnenscheibe,
     * Sterne, Nachthimmel). Lichtquellen wie bei {@link #integrate}; l2 darf null sein.
     */
    public double[] radiance(double eye, double[] v, double[] l1, double[] e1, double[] l2, double[] e2) {
        return radiance(eye, v, l1, e1, l2, e2, VIEW_STEPS);
    }

    /** Nur Einfachstreuung (ohne Ψ), echte Phasenfunktionen, {@code steps} quadratische Schritte (P7e, Zerlegung). */
    public double[] radianceEinfach(double eye, double[] v, double[] l1, double[] e1, int steps) {
        return integrate(R + Math.max(1, eye), v, l1, e1, null, null, steps, true, true, false)[0];
    }

    /** Wie oben mit eigener Schrittzahl (das Licht der Szene kommt mit 16 Schritten aus, auf 1 % genau). */
    public double[] radiance(double eye, double[] v, double[] l1, double[] e1, double[] l2, double[] e2, int steps) {
        return integrate(R + Math.max(1, eye), v, l1, e1, l2, e2, steps, true, true)[0];
    }

    /**
     * Luftperspektive (seit P6c): Strahlengang von Höhe r in Richtung v bis zum Abstand {@code tEnde} (m) mit
     * {@code steps} gleichen Schritten, echte Phasenfunktionen und Mehrfachstreuung, ohne Boden am Ende.
     * Ergebnis: [0] gestreutes Licht auf dem Weg, [1] Durchlässigkeit des Wegs. Endet der Weg früher an der Oberkante,
     * zählt nur das Stück darin.
     */
    public double[][] strecke(double r, double[] v, double[] l1, double[] e1, double[] l2, double[] e2, double tEnde, int steps) {
        double tMax = Math.min(tEnde, distToTop(r, v[1]));
        double[] L = new double[3], tp = {1, 1, 1};
        double g = Atmosphere.MIE_G, c1 = dot(v, l1), c2 = l2 == null ? 0 : dot(v, l2);
        double pR1 = phaseRayleigh(c1), pM1 = phaseMie(c1, g), pR2 = phaseRayleigh(c2), pM2 = phaseMie(c2, g);
        // seit P7e: J (ab der zweiten Streuung) einmal je Quelle in der Mitte des Wegs [G], wie luft.glsl (jMitte)
        Streuordnungen so = hillaire ? null : streuordnungen();
        double[][] j1 = new double[3][2], j2 = new double[3][2];
        if (so != null) {
            double tm = 0.5 * tMax, px = v[0] * tm, py = r + v[1] * tm, pz = v[2] * tm, rr = Math.sqrt(px * px + py * py + pz * pz);
            double ux = px / rr, uy = py / rr, uz = pz / rr, muV = ux * v[0] + uy * v[1] + uz * v[2];
            for (int c = 0; c < 3; c++) {
                so.j(c, rr - R, muV, ux * l1[0] + uy * l1[1] + uz * l1[2], c1, j1[c]);
                if (l2 != null) so.j(c, rr - R, muV, ux * l2[0] + uy * l2[1] + uz * l2[2], c2, j2[c]);
            }
        }
        for (int k = 0; k < steps; k++) {
            double t0 = tMax * k / steps, t1 = tMax * (k + 1.0) / steps, tm = 0.5 * (t0 + t1), dt = t1 - t0;
            double px = v[0] * tm, py = r + v[1] * tm, pz = v[2] * tm;
            double rr = Math.sqrt(px * px + py * py + pz * pz), h = rr - R;
            double dR = Atmosphere.rayleighDensity(h), dM = Atmosphere.mieDensity(h), scM = atm.mieScattering * dM;
            double[] S = new double[3];
            if (so == null) {
                addLight(S, px / rr, py / rr, pz / rr, rr, v, l1, e1, pR1, pM1, dR, scM, true, true);
                if (l2 != null) addLight(S, px / rr, py / rr, pz / rr, rr, v, l2, e2, pR2, pM2, dR, scM, true, true);
            } else {
                einfachMit(S, px / rr, py / rr, pz / rr, rr, l1, e1, pR1, pM1, dR, scM, j1);
                if (l2 != null) einfachMit(S, px / rr, py / rr, pz / rr, rr, l2, e2, pR2, pM2, dR, scM, j2);
            }
            for (int c = 0; c < 3; c++) {
                double ext = Math.max(1e-12, atm.extinction(h, c)), sT = Math.exp(-ext * dt);
                L[c] += tp[c] * (S[c] - S[c] * sT) / ext;
                tp[c] *= sT;
            }
        }
        return new double[][] {L, tp};
    }

    /** Erste Streuung genau, ab der zweiten J aus j[c] = {J_R, J_M}. */
    private void einfachMit(double[] S, double ux, double uy, double uz, double rr, double[] l, double[] e, double pR, double pM, double dR, double scM, double[][] j) {
        double muL = ux * l[0] + uy * l[1] + uz * l[2];
        double[] tl = transmittance(rr, muL);
        double shadow = hitsGround(rr, muL) ? 0 : 1;
        for (int c = 0; c < 3; c++) {
            double scR = Atmosphere.RAYLEIGH[c] * dR;
            S[c] += e[c] * (shadow * tl[c] * (scR * pR + scM * pM) + scR * j[c][0] + scM * j[c][1]);
        }
    }

    /**
     * J als Bild für die Karte (seit P7e): (2 NPHI NMS) × (NMU NR) RGBA, x = m + NPHI k (J_R) bzw. + NPHI NMS (J_M),
     * y = j + NMU i, rgb die Wellenlängen w0..w0+2 (fehlende 0).
     */
    public static float[] jBild(Streuordnungen so, int w0) {
        int W = 2 * Streuordnungen.NPHI * Streuordnungen.NMS, H = Streuordnungen.NMU * Streuordnungen.NR;
        float[] f = new float[W * H * 4];
        for (int i = 0; i < Streuordnungen.NR; i++) for (int j = 0; j < Streuordnungen.NMU; j++) for (int k = 0; k < Streuordnungen.NMS; k++)
            for (int m = 0; m < Streuordnungen.NPHI; m++) {
                int o = Streuordnungen.index(i, j, k, m), y = j + Streuordnungen.NMU * i, x = m + Streuordnungen.NPHI * k;
                for (int c = 0; c < 3; c++) {
                    int w = w0 + c;
                    if (w >= so.lambda.length) continue;
                    f[(y * W + x) * 4 + c] = so.jR[w][o];
                    f[(y * W + x + Streuordnungen.NPHI * Streuordnungen.NMS) * 4 + c] = so.jM[w][o];
                }
                f[(y * W + x) * 4 + 3] = 1;
                f[(y * W + x + Streuordnungen.NPHI * Streuordnungen.NMS) * 4 + 3] = 1;
            }
        return f;
    }

    /** Optische Tiefe der Wellenlängen w0..w0+2 als Bild (TRANS_W × TRANS_H RGBA, wie {@link #transmittanceTable}). */
    public static float[] durchBild(Streuordnungen so, int w0) {
        float[] f = new float[TRANS_W * TRANS_H * 4];
        for (int i = 0; i < TRANS_W * TRANS_H; i++) {
            for (int c = 0; c < 3; c++) if (w0 + c < so.lambda.length) f[i * 4 + c] = so.trans[w0 + c][i];
            f[i * 4 + 3] = 1;
        }
        return f;
    }

    // ------------------------------------------------------------------ Hilfen

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    /** Bilinear in einer Tabelle mit Stützstellen an i/(w−1), j/(h−1) – wie die Grafikkarte mit (x·(n−1)+0,5)/n. */
    static double[] bilinear(float[] lut, int w, int h, double x, double y) {
        double px = Math.max(0, Math.min(1, x)) * (w - 1), py = Math.max(0, Math.min(1, y)) * (h - 1);
        int i0 = Math.min(w - 2, (int) px), j0 = Math.min(h - 2, (int) py);
        double fx = px - i0, fy = py - j0;
        double[] out = new double[3];
        for (int c = 0; c < 3; c++) {
            double a = lut[(j0 * w + i0) * 3 + c], b = lut[(j0 * w + i0 + 1) * 3 + c];
            double d = lut[((j0 + 1) * w + i0) * 3 + c], e = lut[((j0 + 1) * w + i0 + 1) * 3 + c];
            out[c] = (a + (b - a) * fx) * (1 - fy) + (d + (e - d) * fx) * fy;
        }
        return out;
    }

    /** Die Tabellen als Kopie (für die Prüfung gegen die Grafikkarte). */
    /** Die Tabelle der Durchlässigkeit als optische Tiefe τ (seit P7e; T = e^−τ). */
    public float[] transmittanceTable() { return trans.clone(); }
    public float[] multiScatteringTable() { return ms.clone(); }
}
