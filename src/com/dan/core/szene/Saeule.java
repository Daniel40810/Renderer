package com.dan.core.szene;

import com.dan.core.szene.spektrum.Spektrum;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Die Wassersäule als Tabelle (P7d, Werkbuch HalligHooge Teil A, 12.8, F55 A), gebacken von
 * {@code render.reference.Saeulentabelle} (Monte Carlo an einer Platte, Fournier-Forand mit b_b/b = 0,019) und hier
 * abgelegt als {@code saeule.bin}, wie das Spektrum. Gelesen vom Echtzeitbild ({@code render.water}) über
 * {@link #tafel}.
 * <p>
 * Ähnlichkeit: Mit fester Phasenfunktion hängt die Säule nur von u = b_b / (a + b_b), der Tiefe x = (a + b_b) D und den
 * Winkeln unter der Fläche ab. Schwebstoff und Wellenlänge gehen über u ein.
 * <ul>
 * <li><b>L(u, x, μ_s, μ_v):</b> Leuchtdichte unter der Fläche nach oben (Richtung μ_v, über den Azimut gemittelt) je
 * Einheit der waagerechten Bestrahlung unter der Fläche von oben unter μ_s, über schwarzem Grund in der Tiefe x (1/sr).</li>
 * <li><b>C(u, x, μ_s, μ_v):</b> Mittel von L cos φ über den Azimut, φ der Azimut der Richtung des Lichts gegen den
 * Lauf der Sonnenstrahlen; L(φ) ≈ L + 2 C cos φ (das Licht, das von der Sonne weg läuft, ist heller).</li>
 * <li><b>T(u, x, μ_s):</b> Bestrahlung des Grunds in der Tiefe x je Einheit derselben Bestrahlung (schwarzer Grund).</li>
 * <li><b>U(u, x, μ_v):</b> Leuchtdichte unter der Fläche von einem Grund in der Tiefe x, der nach Lambert mit der
 * Ausstrahlung 1 strahlt (1/sr); <b>R(u, x):</b> der Teil seines Flusses, der zum Grund zurückkehrt.</li>
 * </ul>
 * Ein Grund mit der Albedo ρ (Lambert) gibt dann genau L_g = ρ T U / (1 − ρ R). Stützstellen: u 0 … 0,8 in 0,05
 * ({@link #U}); x = 40 (j/15)² ({@link #X}, 40 steht für tief); μ_s von {@link #MU_MIN} bis 1 in 8 Stufen (Punkte),
 * μ_v in 8 Fächern gleicher Breite (der Wert gilt in der Mitte des Fachs). Unter der Fläche liegt jedes Licht von oben
 * im Kegel bis cos 0,665 (n = 1,34).
 */
public final class Saeule {
    public static final int MAGIE = 0x53_61_65_31, NU = 17, NX = 32, NMU = 8;
    public static final double MU_MIN = 0.665, X_MAX = 40;
    public static final double[] U = new double[NU], X = new double[NX], MU_S = new double[NMU], MU_V = new double[NMU];
    static {
        for (int i = 0; i < NU; i++) U[i] = 0.05 * i;
        for (int j = 0; j < NX; j++) X[j] = X_MAX * Math.pow(j / (NX - 1.0), 4);
        for (int k = 0; k < NMU; k++) {
            MU_S[k] = MU_MIN + (1 - MU_MIN) * k / (NMU - 1.0);
            MU_V[k] = MU_MIN + (1 - MU_MIN) * (k + 0.5) / NMU;
        }
    }

    /** Raumwinkel mal mittleres μ eines Fachs: ∫ μ dω = π (μ_o² − μ_u²). */
    public static double raumFach(int k) {
        double lo = MU_MIN + (1 - MU_MIN) * k / NMU, hi = MU_MIN + (1 - MU_MIN) * (k + 1) / NMU;
        return Math.PI * (hi * hi - lo * lo);
    }

    /** Die Tabellen, Reihenfolge [u][x][μ_s][μ_v] (L und C), [u][x][μ_s], [u][x][μ_v], [u][x]. */
    public final float[] l, c, t, u, r;

    public Saeule(float[] l, float[] c, float[] t, float[] u, float[] r) {
        this.l = l;
        this.c = c;
        this.t = t;
        this.u = u;
        this.r = r;
    }

    private static Saeule geladen;

    /** Die abgelegte Tabelle ({@code saeule.bin}). */
    public static synchronized Saeule lade() {
        if (geladen != null) return geladen;
        try (InputStream in = Saeule.class.getResourceAsStream("saeule.bin")) {
            if (in == null) throw new IOException("saeule.bin fehlt (gebacken von render.reference.Saeulentabelle)");
            DataInputStream d = new DataInputStream(new BufferedInputStream(in));
            if (d.readInt() != MAGIE || d.readInt() != NU || d.readInt() != NX || d.readInt() != NMU) throw new IOException("saeule.bin: falscher Kopf");
            float[][] a = {new float[NU * NX * NMU * NMU], new float[NU * NX * NMU * NMU], new float[NU * NX * NMU], new float[NU * NX * NMU], new float[NU * NX]};
            for (float[] f : a) for (int i = 0; i < f.length; i++) f[i] = d.readFloat();
            geladen = new Saeule(a[0], a[1], a[2], a[3], a[4]);
            return geladen;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ Lage in den Stützstellen

    /** Lage in u (Index mit Bruch). */
    public static double iu(double u) { return Math.max(0, Math.min(NU - 1, u / 0.05)); }

    /** Lage in x: j = 31 (x / 40)^¼ (seit F61 A 32 Stufen, x_j = 40 (j/31)⁴: 12 unter x = 1), über 40 tief. */
    public static double ix(double x) { return (NX - 1) * Math.sqrt(Math.sqrt(Math.max(0, Math.min(X_MAX, x)) / X_MAX)); }

    public static double ims(double mu) { return Math.max(0, Math.min(NMU - 1, (mu - MU_MIN) / (1 - MU_MIN) * (NMU - 1))); }

    public static double imv(double mu) { return Math.max(0, Math.min(NMU - 1, (mu - MU_MIN) / (1 - MU_MIN) * NMU - 0.5)); }

    static int lo(double f, int n) { return Math.min(n - 2, (int) Math.floor(f)); }

    /** L(u, x, μ_s, μ_v), multilinear. */
    public double l(double uu, double x, double mus, double muv) { return vierfach(l, uu, x, mus, muv); }

    /** C(u, x, μ_s, μ_v), multilinear. */
    public double c(double uu, double x, double mus, double muv) { return vierfach(c, uu, x, mus, muv); }

    static double vierfach(float[] l, double uu, double x, double mus, double muv) {
        double a = iu(uu), b = ix(x), c = ims(mus), e = imv(muv);
        int i = lo(a, NU), j = lo(b, NX), k = lo(c, NMU), m = lo(e, NMU);
        double fa = a - i, fb = b - j, fc = c - k, fe = e - m, s = 0;
        for (int di = 0; di < 2; di++) for (int dj = 0; dj < 2; dj++) for (int dk = 0; dk < 2; dk++) for (int dm = 0; dm < 2; dm++) {
            double w = (di == 0 ? 1 - fa : fa) * (dj == 0 ? 1 - fb : fb) * (dk == 0 ? 1 - fc : fc) * (dm == 0 ? 1 - fe : fe);
            s += w * l[(((i + di) * NX + j + dj) * NMU + k + dk) * NMU + m + dm];
        }
        return s;
    }

    /** T(u, x, μ_s), logarithmisch interpoliert (T und U fallen mit x etwa exponentiell). */
    public double t(double uu, double x, double mus) { return Math.exp(dreifachLog(t, iu(uu), ix(x), ims(mus))); }

    /** U(u, x, μ_v), logarithmisch interpoliert. */
    public double u(double uu, double x, double muv) { return Math.exp(dreifachLog(u, iu(uu), ix(x), imv(muv))); }

    /** Kleinster Wert vor dem Logarithmus [G]. */
    public static final double KLEIN = 1e-30;

    static double dreifachLog(float[] f, double a, double b, double c) {
        int i = lo(a, NU), j = lo(b, NX), k = lo(c, NMU);
        double fa = a - i, fb = b - j, fc = c - k, s = 0;
        for (int di = 0; di < 2; di++) for (int dj = 0; dj < 2; dj++) for (int dk = 0; dk < 2; dk++)
            s += (di == 0 ? 1 - fa : fa) * (dj == 0 ? 1 - fb : fb) * (dk == 0 ? 1 - fc : fc) * Math.log(Math.max(KLEIN, f[((i + di) * NX + j + dj) * NMU + k + dk]));
        return s;
    }

    /** R(u, x). */
    public double r(double uu, double x) {
        double a = iu(uu), b = ix(x);
        int i = lo(a, NU), j = lo(b, NX);
        double fa = a - i, fb = b - j;
        return (1 - fa) * ((1 - fb) * r[i * NX + j] + fb * r[i * NX + j + 1]) + fa * ((1 - fb) * r[(i + 1) * NX + j] + fb * r[(i + 1) * NX + j + 1]);
    }

    static double dreifach(float[] f, double a, double b, double c) {
        int i = lo(a, NU), j = lo(b, NX), k = lo(c, NMU);
        double fa = a - i, fb = b - j, fc = c - k, s = 0;
        for (int di = 0; di < 2; di++) for (int dj = 0; dj < 2; dj++) for (int dk = 0; dk < 2; dk++)
            s += (di == 0 ? 1 - fa : fa) * (dj == 0 ? 1 - fb : fb) * (dk == 0 ? 1 - fc : fc) * f[((i + di) * NX + j + dj) * NMU + k + dk];
        return s;
    }

    // ------------------------------------------------------------------ Himmel

    /** Stufen über cos des Einfalls in der Luft für das Licht des Himmels [G]. */
    static final int HIMMEL_STUFEN = 32;

    /**
     * Gewichte für das Licht des Himmels: Leuchtdichte in der Luft gleich in allen Richtungen [G], durch die Fläche nach
     * Fresnel; je Stufe {μ_s unter der Fläche, Anteil an der waagerechten Bestrahlung unter der Fläche}.
     */
    public static double[][] himmelGewichte(double n) {
        double[][] g = new double[HIMMEL_STUFEN][2];
        double s = 0;
        for (int q = 0; q < HIMMEL_STUFEN; q++) {
            double ma = (q + 0.5) / HIMMEL_STUFEN, ct = Math.sqrt(Math.max(0, 1 - (1 - ma * ma) / (n * n)));
            g[q][0] = ct;
            g[q][1] = (1 - Wasseroptik.fresnel(ma, n)) * ma;
            s += g[q][1];
        }
        for (double[] q : g) q[1] /= s;
        return g;
    }

    // ------------------------------------------------------------------ Tafel für das Echtzeitbild

    /**
     * Werte je Kanal in der Tafel: L [x][μ_s][μ_v], C [x][μ_s][μ_v], ln T [x][μ_s], ln U [x][μ_v], R [x], L des Himmels
     * [x][μ_v], ln T des Himmels [x]. Zwischen den Stufen in x: L, C und L des Himmels linear in q = 1 − e^(−{@link #KQ} x)
     * (die Säule sättigt mit der Tiefe), ln T, ln U und R linear in x.
     */
    public static final int JE_KANAL = 2 * NX * NMU * NMU + NX * NMU + NX * NMU + NX + NX * NMU + NX;
    public static final int O_C = NX * NMU * NMU, O_T = 2 * NX * NMU * NMU, O_U = O_T + NX * NMU, O_R = O_U + NX * NMU, O_LH = O_R + NX, O_TH = O_LH + NX * NMU;

    /**
     * Die Tafel für ein Bild: für jeden der drei Kanäle mit seinem u die Tabellen über (x, μ_s, μ_v) und die über μ_s
     * gewichteten für den Himmel ({@link #himmelGewichte}). Das Echtzeitbild interpoliert in ihr wie {@link #wert}.
     */
    public float[] tafel(double[] uk, double n) {
        float[] f = new float[3 * JE_KANAL];
        double[][] hg = himmelGewichte(n);
        for (int c = 0; c < 3; c++) {
            int o = c * JE_KANAL;
            double uu = uk[c];
            for (int j = 0; j < NX; j++) {
                for (int k = 0; k < NMU; k++)
                    for (int m = 0; m < NMU; m++) {
                        f[o + (j * NMU + k) * NMU + m] = (float) l(uu, X[j], MU_S[k], MU_V[m]);
                        f[o + O_C + (j * NMU + k) * NMU + m] = (float) c(uu, X[j], MU_S[k], MU_V[m]);
                    }
                for (int k = 0; k < NMU; k++) {
                    f[o + O_T + j * NMU + k] = (float) Math.log(Math.max(KLEIN, t(uu, X[j], MU_S[k])));
                    f[o + O_U + j * NMU + k] = (float) Math.log(Math.max(KLEIN, u(uu, X[j], MU_V[k])));
                }
                f[o + O_R + j] = (float) r(uu, X[j]);
                double th = 0;
                for (double[] q : hg) th += q[1] * t(uu, X[j], q[0]);
                f[o + O_TH + j] = (float) Math.log(Math.max(KLEIN, th));
                for (int m = 0; m < NMU; m++) {
                    double lh = 0;
                    for (double[] q : hg) lh += q[1] * l(uu, X[j], q[0], MU_V[m]);
                    f[o + O_LH + j * NMU + m] = (float) lh;
                }
            }
        }
        return f;
    }

    /** Schritt (nm) und Luftmasse des Sonnenlichts für die Gewichte der Kanäle [G]. */
    public static final double SCHRITT = 5, LUFTMASSE = 1.5;

    /**
     * Die Tafel für ein Bild, über das Spektrum gemittelt (F55 A „je Wellenlänge“): Die Referenz rechnet 360–830 nm und
     * bildet nach linearem sRGB ab; das Echtzeitbild rechnet drei Kanäle. Je Kanal c ist ein Wert der Tafel das Mittel
     * Σ w_c(λ) L(u(λ), x K(λ)/K_c, …) / Σ w_c(λ) mit w_c = (Zeile c von XYZ → sRGB)·(x̄, ȳ, z̄) mal Sonne durch die Luft
     * (Rayleigh, Luftmasse {@link #LUFTMASSE}) [G], 380–780 nm. Die Tiefe des Kanals ist x = K_c D mit K_c aus
     * {@link Wasseroptik#daempfung()}, wie im Shader. ln T, ln U und ln T des Himmels werden nach dem Mitteln genommen.
     */
    public float[] tafel(Wasseroptik w) {
        int nl = (int) Math.round((780 - 380) / SCHRITT) + 1;
        double[] lam = new double[nl], ul = new double[nl], kl = new double[nl];
        double[][] wc = new double[3][nl];
        double bb = Wasseroptik.BB_STERN * w.schwebstoff;
        for (int i = 0; i < nl; i++) {
            double l = 380 + SCHRITT * i;
            lam[i] = l;
            double a = Spektrum.wasser(l) + Wasseroptik.A_STERN_443 * w.schwebstoff * Math.exp(-Wasseroptik.S_NAP * (l - 443));
            kl[i] = a + bb;
            ul[i] = kl[i] > 0 ? bb / kl[i] : 0;
            double e = Spektrum.sonne(l) * Math.exp(-LUFTMASSE * Spektrum.rayleighTau(l));
            double[] cmf = Spektrum.xyz(l);
            for (int c = 0; c < 3; c++) {
                double m = 0;
                for (int q = 0; q < 3; q++) m += Spektrum.XYZ_RGB[c][q] * cmf[q];
                wc[c][i] = m * e;
            }
        }
        double[] kk = w.daempfung();
        float[] f = new float[3 * JE_KANAL];
        double[][] hg = himmelGewichte(w.n);
        for (int c = 0; c < 3; c++) {
            int o = c * JE_KANAL;
            double sw = 0;
            for (int i = 0; i < nl; i++) sw += wc[c][i];
            for (int j = 0; j < NX; j++) {
                double[] xl = new double[nl];
                for (int i = 0; i < nl; i++) xl[i] = X[j] * kl[i] / kk[c];
                for (int k = 0; k < NMU; k++)
                    for (int m = 0; m < NMU; m++) {
                        double sl = 0, sc = 0;
                        for (int i = 0; i < nl; i++) {
                            sl += wc[c][i] * l(ul[i], xl[i], MU_S[k], MU_V[m]);
                            sc += wc[c][i] * c(ul[i], xl[i], MU_S[k], MU_V[m]);
                        }
                        f[o + (j * NMU + k) * NMU + m] = (float) (sl / sw);
                        f[o + O_C + (j * NMU + k) * NMU + m] = (float) (sc / sw);
                    }
                for (int k = 0; k < NMU; k++) {
                    double st = 0, su = 0;
                    for (int i = 0; i < nl; i++) {
                        st += wc[c][i] * t(ul[i], xl[i], MU_S[k]);
                        su += wc[c][i] * u(ul[i], xl[i], MU_V[k]);
                    }
                    f[o + O_T + j * NMU + k] = (float) Math.log(Math.max(KLEIN, st / sw));
                    f[o + O_U + j * NMU + k] = (float) Math.log(Math.max(KLEIN, su / sw));
                }
                double sr = 0, th = 0;
                for (int i = 0; i < nl; i++) {
                    sr += wc[c][i] * r(ul[i], xl[i]);
                    for (double[] q : hg) th += wc[c][i] * q[1] * t(ul[i], xl[i], q[0]);
                }
                f[o + O_R + j] = (float) (sr / sw);
                f[o + O_TH + j] = (float) Math.log(Math.max(KLEIN, th / sw));
                for (int m = 0; m < NMU; m++) {
                    double lh = 0;
                    for (int i = 0; i < nl; i++) for (double[] q : hg) lh += wc[c][i] * q[1] * l(ul[i], xl[i], q[0], MU_V[m]);
                    f[o + O_LH + j * NMU + m] = (float) (lh / sw);
                }
            }
        }
        return f;
    }

    /**
     * Was {@code wasser.frag} aus der Tafel rechnet (in double), Leuchtdichte unter der Fläche nach oben je Kanal:
     * Säule aus Sonne (oder Mond) mit der waagerechten Bestrahlung unter der Fläche es und μ_s, aus dem Himmel mit eh,
     * dazu der Grund mit der Albedo rho in der Tiefe D.
     *
     * @param k Dämpfung a + b_b des Kanals (1/m)
     * @param cphi cos des Azimuts zwischen dem Licht zum Auge und dem Lauf der Sonnenstrahlen
     */
    public static double wert(float[] f, int c, double k, double d, double mus, double muv, double cphi, double es, double eh, double rho) {
        int o = c * JE_KANAL;
        double x = Math.min(X_MAX, k * d), cs = ims(mus), e = imv(muv);
        int j = lo(ix(x), NX);
        double fx = (x - X[j]) / (X[j + 1] - X[j]), dq = q(X[j + 1]) - q(X[j]);
        // in der Tiefe ist q in float 1: dann linear in x (die Säule ist dort gesättigt), wie im Shader
        double fq = dq > DQ_MIN ? (q(x) - q(X[j])) / dq : fx;
        double lS = drei(f, o, j, fq, cs, e) + 2 * cphi * drei(f, o + O_C, j, fq, cs, e), lH = zwei(f, o + O_LH, j, fq, e);
        double tS = Math.exp(zwei(f, o + O_T, j, fx, cs)), tH = Math.exp(eins(f, o + O_TH, j, fx));
        double uG = Math.exp(zwei(f, o + O_U, j, fx, e)), rG = eins(f, o + O_R, j, fx);
        return es * lS + eh * lH + rho * (es * tS + eh * tH) * uG / (1 - rho * rG);
    }

    /**
     * Seit P10d (F104 A): wie {@link #wert}, aber das Himmelslicht nicht gleich im Kegel verteilt, sondern nach den Stufen
     * der Sonne: {@code eStufen[q]} ist die waagerechte Bestrahlung unter der Fläche aus Richtungen, die unter der Fläche
     * bei μ_s = {@link #MU_S}[q] liegen (je Richtung linear auf die zwei nächsten Stufen geteilt, wie {@code licht.comp}).
     * Die Säule aus dem Himmel ist dann Σ_q E_q L(x, μ_q, μ_v) (der Teil mit cos φ fällt im Mittel über den Azimut weg), der
     * Grund bekommt Σ_q E_q T(x, μ_q); in x wie die Karte: L linear in q, ln Σ E T linear in x zwischen den Stufen.
     */
    public static double wertStufen(float[] f, int c, double k, double d, double mus, double muv, double cphi, double es, double[] eStufen, double rho) {
        int o = c * JE_KANAL;
        double x = Math.min(X_MAX, k * d), cs = ims(mus), e = imv(muv);
        int j = lo(ix(x), NX);
        double fx = (x - X[j]) / (X[j + 1] - X[j]), dq = q(X[j + 1]) - q(X[j]);
        double fq = dq > DQ_MIN ? (q(x) - q(X[j])) / dq : fx;
        double lS = drei(f, o, j, fq, cs, e) + 2 * cphi * drei(f, o + O_C, j, fq, cs, e), tS = Math.exp(zwei(f, o + O_T, j, fx, cs));
        double lH = 0, t0 = 0, t1 = 0;
        for (int qq = 0; qq < NMU; qq++) {
            if (eStufen[qq] == 0) continue;
            lH += eStufen[qq] * drei(f, o, j, fq, qq, e);
            t0 += eStufen[qq] * Math.exp(f[o + O_T + j * NMU + qq]);
            t1 += eStufen[qq] * Math.exp(f[o + O_T + (j + 1) * NMU + qq]);
        }
        double eh = Math.exp((1 - fx) * Math.log(Math.max(1e-30, t0)) + fx * Math.log(Math.max(1e-30, t1)));
        double uG = Math.exp(zwei(f, o + O_U, j, fx, e)), rG = eins(f, o + O_R, j, fx);
        return es * lS + lH + rho * (es * tS + eh) * uG / (1 - rho * rG);
    }

    /** Seit P10d: die Lage einer Richtung des Himmels in der Luft (cos zur Senkrechten cz) unter der Fläche, in Stufen von {@link #MU_S}. */
    public static double himmelStufe(double cz, double n) { return ims(Math.sqrt(Math.max(0, 1 - (1 - cz * cz) / (n * n)))); }

    /** Sättigung der Säule mit der Tiefe für die Interpolation in x [G]: L erreicht bei x = 0,71 rund 91 % des Tiefen (mit der Fläche). */
    public static final double KQ = 3.5;

    static double q(double x) { return 1 - Math.exp(-KQ * x); }

    /** Kleinster Abstand in q, unter dem linear in x interpoliert wird [G]. */
    public static final double DQ_MIN = 1e-4;

    static double drei(float[] f, int o, int j, double fb, double c, double e) {
        int k = lo(c, NMU), m = lo(e, NMU);
        double fc = c - k, fe = e - m, s = 0;
        for (int dj = 0; dj < 2; dj++) for (int dk = 0; dk < 2; dk++) for (int dm = 0; dm < 2; dm++)
            s += (dj == 0 ? 1 - fb : fb) * (dk == 0 ? 1 - fc : fc) * (dm == 0 ? 1 - fe : fe) * f[o + ((j + dj) * NMU + k + dk) * NMU + m + dm];
        return s;
    }

    static double zwei(float[] f, int o, int j, double fb, double c) {
        int k = lo(c, NMU);
        double fc = c - k;
        return (1 - fb) * ((1 - fc) * f[o + j * NMU + k] + fc * f[o + j * NMU + k + 1]) + fb * ((1 - fc) * f[o + (j + 1) * NMU + k] + fc * f[o + (j + 1) * NMU + k + 1]);
    }

    static double eins(float[] f, int o, int j, double fb) {
        return (1 - fb) * f[o + j] + fb * f[o + j + 1];
    }
}
