package com.dan.core.szene;

/**
 * Das Wasser im Bild, Stufe 2 (P8b, Werkbuch HalligHooge Teil A, 12.9, F76 A): Werte und Formeln für die langen Wellen
 * aus dem örtlichen Seegang, dieselben wie {@code render.water/seegang.glsl} (für den Prozessor und die Prüfung).
 * <ul>
 * <li><b>Lange Wellen.</b> Ein fester Satz von {@link #LANG} Wellen in {@link #BAENDER} Bändern der Frequenz von
 * {@link #F_UNTEN} bis {@link #F_OBEN} Hz (Grenzen geometrisch), je Band {@link #JE_BAND} Richtungen im Abstand von
 * 30°, je Band um 5° versetzt, die Wellenzahl je Welle um bis zu ±6 % gestreut [G]; Wellenzahl aus der Mitte des Bands
 * für tiefes Wasser. Phasen fest [G]. Der Satz ist überall derselbe; je Ort ändern sich nur die Höhen: Das Band b
 * bekommt die Varianz V_b = Hs²/16 · (P(ln f_oben − ln f_p) − P(ln f_unten − ln f_p)) mit P dem Anteil eines
 * JONSWAP-Spektrums (γ = 3,3) unter ln(f/f_p) ({@link #cdf}), verteilt auf seine Wellen nach cos²ˢ((θ − θ_m)/2) mit
 * s = {@link #S} [G]; Amplitude a = √(2 V_b w / Σw). So bleibt die Phase eines Orts stetig, und die Wellen laufen um die
 * Sände herum, wohin der Seegang sie trägt.</li>
 * <li><b>Glitzern.</b> Neigungen der langen Wellen, die ein Bildpunkt nicht auflöst, gehen als Varianz ins Glitzern:
 * max(0, mss − aufgelöst), mss aus {@code waves.mss}. Dazu die Kräuselung aus dem Wind wie in Stufe 1.</li>
 * <li><b>Schaum.</b> Bedeckung W = min(1, diss / {@link #D_VOLL}) [G] als Muster aus Rauschen; Albedo {@link #ALBEDO} [G].</li>
 * </ul>
 * Ohne Seegang (Hs = 0, mss = 0, diss = 0) ist das Bild das der Stufe 1 (Prüfstein 8 von P8b).
 * <p>
 * <b>Seit P13c</b> (Werkbuch 12.14, F159 A, F161 A): die langen Wellen auch als Höhe ({@link #amplituden},
 * {@link #hoehe}) mit spitzen Kämmen im Flachen ({@link #kamm}), Brechen nach McCowan ({@link #brechen}), Schaumköpfe
 * auf den Kämmen mit der Bedeckung aus diss / {@link #D_KAL} ({@link #schaum}).
 */
public final class Seegangsoptik {
    public static final int BAENDER = 6, JE_BAND = 12, LANG = BAENDER * JE_BAND, CDF = 64;
    public static final double F_UNTEN = 0.07, F_OBEN = 0.6, S = 8, D_VOLL = 20, ALBEDO = 0.6;
    /** ln(f/f_p) am Anfang und Ende der Tafel {@link #cdf}. */
    public static final double CDF_VON = -1, CDF_BIS = 3;

    private Seegangsoptik() { }

    /** Die langen Wellen: je Welle {Wellenzahl Ost, Nord (rad/m), Phase (rad), Band}. */
    public static double[][] wellen() {
        double[][] w = new double[LANG][];
        java.util.Random r = new java.util.Random(0x5EE6A9L);
        double[] grenze = baender();
        for (int b = 0; b < BAENDER; b++) {
            double fm = Math.sqrt(Math.exp(grenze[2 * b]) * Math.exp(grenze[2 * b + 1]));
            double k0 = Math.pow(2 * Math.PI * fm, 2) / 9.81;
            for (int j = 0; j < JE_BAND; j++) {
                double th = Math.toRadians(j * 360.0 / JE_BAND + b * 5.0), k = k0 * (1 + 0.12 * (r.nextDouble() - 0.5));
                w[b * JE_BAND + j] = new double[] {k * Math.cos(th), k * Math.sin(th), 2 * Math.PI * r.nextDouble(), b};
            }
        }
        return w;
    }

    /** Je Band die längste seiner Wellen (m); ist sie kürzer als zwei Bildpunkte, überspringt der Shader das Band. */
    public static double[] laengste() {
        double[][] w = wellen();
        double[] l = new double[BAENDER];
        for (double[] q : w) l[(int) q[3]] = Math.max(l[(int) q[3]], 2 * Math.PI / Math.hypot(q[0], q[1]));
        return l;
    }

    /** Je Band {ln f_unten, ln f_oben} (Hz), hintereinander. */
    public static double[] baender() {
        double[] g = new double[2 * BAENDER];
        double q = Math.log(F_OBEN / F_UNTEN) / BAENDER;
        for (int b = 0; b < BAENDER; b++) { g[2 * b] = Math.log(F_UNTEN) + b * q; g[2 * b + 1] = Math.log(F_UNTEN) + (b + 1) * q; }
        return g;
    }

    /** Anteil der Energie eines JONSWAP (γ = 3,3) unter ln(f/f_p) = x, an {@link #CDF} Stellen von {@link #CDF_VON} bis {@link #CDF_BIS}. */
    public static double[] cdf() {
        int n = 20000;
        double von = -2.0, bis = 4.0, h = (bis - von) / n, s = 0, vor = 0;
        double[] kum = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double f = Math.exp(von + i * h), sg = f <= 1 ? 0.07 : 0.09;
            double e = Math.pow(f, -5) * Math.exp(-1.25 * Math.pow(f, -4)) * Math.pow(3.3, Math.exp(-(f - 1) * (f - 1) / (2 * sg * sg))) * f;   // je d(ln f)
            if (i > 0) s += 0.5 * h * (e + vor);
            vor = e;
            kum[i] = s;
        }
        double[] t = new double[CDF];
        for (int k = 0; k < CDF; k++) {
            double x = CDF_VON + k * (CDF_BIS - CDF_VON) / (CDF - 1);
            t[k] = kum[(int) Math.round((x - von) / h)] / s;
        }
        return t;
    }

    /** Der Anteil darüber, 1 − {@link #cdf}, an denselben Stellen (für die Bänder über der Spitze, ohne Auslöschung in float). */
    public static double[] cdq() {
        double[] p = cdf(), q = new double[p.length];
        for (int k = 0; k < p.length; k++) q[k] = 1 - p[k];
        return q;
    }

    /** P(x) aus der Tafel, linear; unter dem Anfang 0, über dem Ende 1 (wie der Shader). */
    public static double p(double[] tafel, double x) {
        double u = (x - CDF_VON) / (CDF_BIS - CDF_VON) * (CDF - 1);
        if (u <= 0) return 0;
        if (u >= CDF - 1) return 1;
        int i = (int) Math.floor(u);
        return tafel[i] + (u - i) * (tafel[i + 1] - tafel[i]);
    }

    /**
     * Die sechs Werte am Ort (UTM, m) im feinsten Netz, das ihn hat, bilinear zwischen den Zellmitten (am Rand der
     * Randwert), linear in der Zeit – dieselbe Regel wie {@code seeWerte} im Shader; die Netze fein zuerst. null ohne Netz.
     * Seit S3c-6 (SF31 A) mit dem Übergang am Rand eines feineren Netzes, seit S3c-7 nach
     * {@link Seegangsnetz#RAND_ART} ({@link Seegangsnetz#gemischt}).
     */
    public static double[] werte(java.util.List<Seegangsnetz> netze, double zeit, double ost, double nord) {
        return Seegangsnetz.gemischt(netze, ost, nord, (s, o, n, rand) -> werteNetz(s, zeit, o, n, rand));
    }

    /** Die sechs Werte eines Netzes am Ort (bilinear, linear in der Zeit); null, wenn es den Ort nicht hat oder nicht gesetzt ist. */
    static double[] werteNetz(Seegangsnetz s, double zeit, double ost, double nord) { return werteNetz(s, zeit, ost, nord, false); }

    /** Wie oben; mit {@code rand} außerhalb des Netzes die Randwerte (seit S3c-7, SF33 A). */
    static double[] werteNetz(Seegangsnetz s, double zeit, double ost, double nord, boolean rand) {
        if (!s.gesetzt()) return null;
        var gi = s.gitter;
        double ux = (ost - gi.x0) / gi.dx, uy = (nord - gi.y0) / gi.dy;
        if (rand) {
            ux = Math.max(0, Math.min(gi.nx, ux));
            uy = Math.max(0, Math.min(gi.ny, uy));
        } else if (ux < 0 || uy < 0 || ux >= gi.nx || uy >= gi.ny) return null;
        double bx = Math.max(0, Math.min(gi.nx - 1, ux - 0.5)), by = Math.max(0, Math.min(gi.ny - 1, uy - 0.5));
        int i0 = Math.max(0, Math.min((int) bx, gi.nx - 2)), j0 = Math.max(0, Math.min((int) by, gi.ny - 2));
        int i1 = Math.min(i0 + 1, gi.nx - 1), j1 = Math.min(j0 + 1, gi.ny - 1);
        double fx = bx - i0, fy = by - j0;
        double[] gw = s.gewicht(zeit);
        int pa = (int) gw[1], pb = 1 - pa, nz = s.zellen;
        double[] r = new double[6];
        int[] zz = {i0 + gi.nx * j0, i1 + gi.nx * j0, i0 + gi.nx * j1, i1 + gi.nx * j1};
        for (int f = 0; f < 6; f++) {
            double[] c = new double[4];
            for (int q = 0; q < 4; q++) {
                double a = s.werte[pa][f * nz + zz[q]], b = s.werte[pb][f * nz + zz[q]];
                c[q] = a + gw[0] * (b - a);
            }
            r[f] = (c[0] + (c[1] - c[0]) * fx) * (1 - fy) + (c[2] + (c[3] - c[2]) * fx) * fy;
        }
        return r;
    }

    /**
     * Die Neigung der langen Wellen am Ort (UTM) zur Modellzeit t, wie der Shader ({@code seegangLang}), aber in double:
     * {∂η/∂Ost, ∂η/∂Nord, aufgelöste Varianz}. Wellen kürzer als zwei Bildpunkte ({@code fuss}) zählen nicht (g = 0), mit
     * fuss → 0 alle (die Referenz, P8c). Phase je Welle: k · (Ost, Nord) + φ − ω(k) t.
     */
    public static double[] neigungAm(double hs, double tp, double thetaM, double fuss, double ost, double nord, double t, double[][] wellen, double[] grenzen, double[] tafel) {
        if (!(hs > 0) || !(tp > 0)) return new double[3];
        double m0 = hs * hs / 16, lnfp = -Math.log(tp), gx = 0, gn = 0, auf = 0;
        for (int b = 0; b < BAENDER; b++) {
            double v = m0 * (p(tafel, grenzen[2 * b + 1] - lnfp) - p(tafel, grenzen[2 * b] - lnfp));
            if (v <= 0) continue;
            double sw = 0;
            double[] w = new double[JE_BAND];
            for (int j = 0; j < JE_BAND; j++) {
                double[] q = wellen[b * JE_BAND + j];
                double c = Math.abs(Math.cos(0.5 * (Math.atan2(q[1], q[0]) - thetaM)));
                w[j] = c > 0 ? Math.exp(2 * S * Math.log(c)) : 0;
                sw += w[j];
            }
            if (sw <= 0) continue;
            for (int j = 0; j < JE_BAND; j++) {
                double[] q = wellen[b * JE_BAND + j];
                double k = Math.hypot(q[0], q[1]), a2 = 2 * v * w[j] / sw, lam = 2 * Math.PI / k;
                double g = Math.max(0, Math.min(1, lam / (2 * fuss) - 1));
                if (g <= 0) continue;
                double ph = q[0] * ost + q[1] * nord + q[2] - Wasseroptik.omega(k) * t;
                double sl = Math.sqrt(a2) * k * g * Math.cos(ph);
                gx += sl * q[0] / k;
                gn += sl * q[1] / k;
                auf += g * g * 0.5 * a2 * k * k;
            }
        }
        return new double[] {gx, gn, auf};
    }

    /**
     * Die langen Wellen an einem Ort, wie der Shader: Amplituden je Welle (m) aus Hs, Tp und der Richtung θ_m (rad, wohin,
     * 0 = Ost); gibt {Neigungsvarianz gesamt, davon aufgelöst} für die Größe eines Bildpunkts {@code fuss} (m).
     */
    public static double[] neigung(double hs, double tp, double thetaM, double fuss, double[][] wellen, double[] grenzen, double[] tafel) {
        if (!(hs > 0) || !(tp > 0)) return new double[] {0, 0};
        double m0 = hs * hs / 16, lnfp = -Math.log(tp), ges = 0, auf = 0;
        for (int b = 0; b < BAENDER; b++) {
            double v = m0 * (p(tafel, grenzen[2 * b + 1] - lnfp) - p(tafel, grenzen[2 * b] - lnfp));
            if (v <= 0) continue;
            double sw = 0;
            double[] w = new double[JE_BAND];
            for (int j = 0; j < JE_BAND; j++) {
                double[] q = wellen[b * JE_BAND + j];
                double c = Math.abs(Math.cos(0.5 * (Math.atan2(q[1], q[0]) - thetaM)));
                w[j] = c > 0 ? Math.exp(2 * S * Math.log(c)) : 0;
                sw += w[j];
            }
            if (sw <= 0) continue;
            for (int j = 0; j < JE_BAND; j++) {
                double[] q = wellen[b * JE_BAND + j];
                double k = Math.hypot(q[0], q[1]), a2 = 2 * v * w[j] / sw, lam = 2 * Math.PI / k;
                double g = Math.max(0, Math.min(1, lam / (2 * fuss) - 1));
                ges += 0.5 * a2 * k * k;
                auf += g * g * 0.5 * a2 * k * k;
            }
        }
        return new double[] {ges, auf};
    }

    // ------------------------------------------------------------------ seit P13c (Brandung, Werkbuch 12.14, F159 A, F161 A)

    /**
     * diss (W/m²) für volle Schaumdecke, kalibriert (F161 A, 20.11.2026): im Xaver-Archiv über die Zellen von G3w mit
     * Hs &gt; 1,2 m, 19 Zeitpunkte, Median von diss / W_Ziel mit W_Ziel = √(W_Callaghan · W_Monahan) bei U₁₀ aus ERA5
     * (Callaghan u. a. 2008: 4,82 · 10⁻⁶ (U + 1,98)³; Monahan und O'Muircheartaigh 1980: 3,84 · 10⁻⁶ U^3,41). Werte je
     * Zeitpunkt 18,7–26,2 W/m², Median 22,7. Bis P13b galt {@link #D_VOLL} = 20 [G].
     */
    public static final double D_KAL = 22.7;
    /** Brechen nach McCowan: H &gt; γ h [Literatur]. */
    public static final double GAMMA = 0.78;
    /** Weißwasser hinter dem Kamm: Proben eine halbe und anderthalb Perioden zurück, Abklingen 1/e nach zwei Perioden [G]. */
    public static final double TAU1 = 0.5, TAU2 = 1.5, ABKLING = 2.0;
    /** Muster der Schaumköpfe [G]: Flecken 3 m und 0,8 m, Streifen längs des Winds 20 m × 1,5 m; Streuung je Teil (in σ der Höhe). */
    /**
     * Gewicht der Höhe in X [G, Lauf C2]: Die Summe weniger gerichteter Wellen hat dünnere Ränder als eine Normalverteilung;
     * mit Gewicht 1 blieben bei W = 2 % rund 4 % zu wenig Köpfe, mit 0,7 rund 2,5 %.
     */
    public static final double C0 = 0.7;
    /**
     * Feinstruktur des Schaums unter 0,3 m (Blasen, Fetzen, Lücken), die das Modell nicht kennt: eine Streuung, die nie als
     * aufgelöst gilt (F164 A, 20.11.2026) [G]. Ohne sie sprang der Schaum nah am Auge von 0 auf 1 (harte Flecken, Lauf C5).
     */
    public static final double C4 = 0.35;
    public static final double FLECK1 = 3.0, FLECK2 = 0.8, STREIF_LANG = 20.0, STREIF_QUER = 1.5, C1 = 0.5, C2 = 0.3, C3 = 0.8;
    /** Streifen längs des Winds ab Bft 8 (17 m/s), voll ab Bft 10 (25 m/s) [Beaufort-Skala, G]. */
    public static final double STREIF_AB = 17.0, STREIF_VOLL = 25.0;
    /** Wellen, deren Amplitude unter dieser Grenze liegt, zählen in der Höhe nicht (m) [G]. */
    public static final double AMIN = 1e-3;

    /**
     * Die Amplituden der langen Wellen am Ort für die Größe eines Bildpunkts {@code fuss}: je Welle a_j · g_j (m), dazu
     * hinter den {@link #LANG} Werten die ganze Varianz der Höhe m0 (Σ a²/2 über die Bänder), die aufgelöste
     * m0r (Σ g² a²/2) und die aufgelöste Neigungsvarianz (wie {@link #neigung}). Wie {@code seeAmplituden} im Shader.
     */
    public static double[] amplituden(double hs, double tp, double thetaM, double fuss, double[][] wellen, double[] grenzen, double[] tafel) {
        double[] r = new double[LANG + 3];
        if (!(hs > 0) || !(tp > 0)) return r;
        double m0 = hs * hs / 16, lnfp = -Math.log(tp), cm = Math.cos(thetaM), sm = Math.sin(thetaM);
        for (int b = 0; b < BAENDER; b++) {
            double v = m0 * (p(tafel, grenzen[2 * b + 1] - lnfp) - p(tafel, grenzen[2 * b] - lnfp));
            if (v <= 0) continue;
            double sw = 0;
            double[] w = new double[JE_BAND];
            for (int j = 0; j < JE_BAND; j++) {
                double[] q = wellen[b * JE_BAND + j];
                double k = Math.hypot(q[0], q[1]), h = 0.5 * (1 + (q[0] * cm + q[1] * sm) / k);
                w[j] = h > 0 ? Math.exp(S * Math.log(h)) : 0;
                sw += w[j];
            }
            if (sw <= 0) continue;
            for (int j = 0; j < JE_BAND; j++) {
                double[] q = wellen[b * JE_BAND + j];
                double k = Math.hypot(q[0], q[1]), a2 = 2 * v * w[j] / sw, lam = 2 * Math.PI / k;
                double g = Math.max(0, Math.min(1, lam / (2 * fuss) - 1));
                double a = Math.sqrt(a2) * g;
                r[LANG] += 0.5 * a2;
                if (a < AMIN) continue;
                r[b * JE_BAND + j] = a;
                r[LANG + 1] += 0.5 * a * a;
                r[LANG + 2] += 0.5 * a * a * k * k;
            }
        }
        return r;
    }

    /**
     * Höhe der linearen langen Wellen am Ort (UTM) zur Zeit t − τ: {ζ, ∂ζ/∂Ost, ∂ζ/∂Nord}; η = Σ a sin φ (passend zur
     * Neigung in {@link #neigungAm}), φ = k · (Ost, Nord) + φ₀ − ω (t − τ).
     */
    public static double[] hoehe(double[] amp, double[][] wellen, double ost, double nord, double t) {
        double z = 0, gx = 0, gn = 0;
        for (int j = 0; j < LANG; j++) {
            double a = amp[j];
            if (a <= 0) continue;
            double[] q = wellen[j];
            double k = Math.hypot(q[0], q[1]);
            double ph = q[0] * ost + q[1] * nord + q[2] - Wasseroptik.omega(k) * t;
            double s = Math.sin(ph), c = Math.cos(ph);
            z += a * s;
            gx += a * c * q[0];
            gn += a * c * q[1];
        }
        return new double[] {z, gx, gn};
    }

    /** Anteil des Kamms an der Wellenhöhe im Flachen, wie {@code com.dan.surf.BreakerShape.crestShare} (0,5 tief … 0,8 flach) [G]. */
    public static double kammAnteil(double hs, double h) {
        double nl = Math.max(0, Math.min(1, (hs / Math.max(0.05, h) - 0.1) / 0.6));
        return 0.5 + 0.3 * nl;
    }

    /** β der Kammform aus dem Kammanteil: ein Kamm bei 2σ steht (2 + 1,5 β)/4 der Höhe über dem Ruhewasser. */
    public static double beta(double hs, double h) { return (kammAnteil(hs, h) - 0.5) / 0.375; }

    /**
     * Spitze Kämme, flache Täler (P13c, F159 A): ζ_s = (ζ + β (ζ² − σ²) / 2σ) / √(1 + β²/2), unter ζ = −σ/β festgehalten
     * (das Tal); Mittel 0 und Varianz σ² bleiben. {ζ_s, Faktor der Neigung dζ_s/dζ}. σ: aufgelöste Streuung der Höhe.
     */
    public static double[] kamm(double z, double sigma, double beta) {
        if (!(sigma > 0) || !(beta > 0)) return new double[] {z, 1};
        double n = Math.sqrt(1 + 0.5 * beta * beta), zt = -sigma / beta, f = 1;
        if (z < zt) { z = zt; f = 0; }
        else f = (1 + beta * z / sigma) / n;
        return new double[] {(z + beta * (z * z - sigma * sigma) / (2 * sigma)) / n, f};
    }

    /** Verteilungsfunktion der Normalverteilung über erf nach Abramowitz und Stegun 7.1.26 (|Fehler| &lt; 1,5 · 10⁻⁷), wie der Shader. */
    public static double phi(double x) {
        double z = Math.abs(x) / Math.sqrt(2), t = 1 / (1 + 0.3275911 * z);
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-z * z);
        return x >= 0 ? 0.5 * (1 + y) : 0.5 * (1 - y);
    }

    /** Umkehrung von {@link #phi} nach Acklam (rationale Näherung, relativer Fehler &lt; 1,2 · 10⁻⁹), wie der Shader. */
    public static double phiInv(double p) {
        p = Math.max(1e-7, Math.min(1 - 1e-7, p));
        double[] a = {-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00};
        double[] b = {-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01};
        double[] c = {-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00};
        double[] d = {7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00};
        double pl = 0.02425;
        if (p < pl) {
            double q = Math.sqrt(-2 * Math.log(p));
            return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }
        if (p > 1 - pl) {
            double q = Math.sqrt(-2 * Math.log(1 - p));
            return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }
        double q = p - 0.5, r = q * q;
        return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1);
    }

    /**
     * Schaumköpfe (P13c, F161 A): Anteil Schaum im Bildpunkt bei der Bedeckung W. Die Köpfe sitzen, wo die Summe
     * X = C0 · ζ/σ + Σ c_i n_i (Höhe der langen Wellen in ihrer Streuung, Flecken, Streifen) über der Schwelle
     * z_q = √V · Φ⁻¹(1 − W) liegt, V = C0² + Σ c_i² + C4² (C4 nie aufgelöst). Was der Bildpunkt auflöst (X_auf, Varianz V_auf), steht im Bild; der Rest
     * geht als Wahrscheinlichkeit ein: Schaum = 1 − Φ((z_q − X_auf) / √(V − V_auf)). Im Mittel über die Fläche ist das W,
     * ob aufgelöst oder nicht (A35: kein hartes Muster in der Ferne).
     */
    public static double schaum(double W, double xAuf, double vGanz, double vAuf) {
        if (W <= 0) return 0;
        if (W >= 1) return 1;
        double zq = Math.sqrt(vGanz) * phiInv(1 - W), su = Math.sqrt(Math.max(vGanz - vAuf, 1e-4));
        return 1 - phi((zq - xAuf) / su);
    }

    /** Brechen an einem Kamm (P13c): Anteil 0 … 1, wenn die örtliche Höhe ζ_s / Kammanteil die Grenze γ h überschreitet (± 10 %) [G]. */
    public static double brechen(double zs, double kammAnteil, double h) {
        if (!(h > 0) || zs <= 0) return 0;
        double x = zs / kammAnteil / (GAMMA * h);
        double u = Math.max(0, Math.min(1, (x - 0.9) / 0.2));
        return u * u * (3 - 2 * u);
    }
}
