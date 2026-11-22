package com.dan.render.reference.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Lichtquellen;
import com.dan.core.szene.Wasseroptik;
import com.dan.core.szene.spektrum.Spektrum;
import com.dan.render.reference.Ebenen;
import com.dan.render.reference.Licht;
import com.dan.render.reference.Pfadverfolger;
import com.dan.render.reference.Phasen;
import com.dan.render.reference.Referenz;
import com.dan.render.reference.Strahlgleichung;
import com.dan.render.reference.Wellen;

import java.nio.file.Path;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung der Referenz ohne Gebiet (P7b, Werkbuch HalligHooge Teil A, 12.8, Prüfsteine 1–5): 1 Ofentest, 2 Beer–Lambert
 * und die Durchlässigkeit der Luft, 3 einfacher Rayleigh-Himmel gegen Quadratur, 4 Spektrum, 5 gekrümmte Strahlen. Endet
 * mit „Alles in Ordnung.“ und schreibt das Attest nach {@code docs/status/P7_Referenz_Kern.md} ({@code --ohne-attest}:
 * nicht). {@code --schnell}: ein Zehntel der Pfade (nur zum Bauen).
 */
public final class ReferenceCheck {
    private static final Attest A = new Attest("com.dan.render.reference", Referenz.VERSION);
    static double mass = 1;

    public static void main(String[] args) {
        boolean attest = !List.of(args).contains("--ohne-attest");
        if (List.of(args).contains("--schnell")) mass = 0.1;
        System.out.println("ReferenceCheck – com.dan.render.reference " + Referenz.VERSION + " (Werkbuch HalligHooge Teil A, P7b)");
        System.out.println("         " + Runtime.getRuntime().availableProcessors() + " Kerne");
        try {
            spektrum();
            ofen();
            ofenRau();
            beerLambert();
            rayleigh();
            kruemmung();
            schaetzer();
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "P7_Referenz_Kern.md"), "P7b – ReferenceCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static int n(double v) { return (int) Math.max(1, Math.round(v * mass)); }

    // ------------------------------------------------------------------ 4 Spektrum

    static void spektrum() {
        A.teil("4 Spektrum (Prüfstein 4)");
        double[] e = new double[Spektrum.N];
        java.util.Arrays.fill(e, 1);
        double[] xyz = Spektrum.nachXyz(e);
        double s = xyz[0] + xyz[1] + xyz[2];
        double fe = Math.max(Math.abs(xyz[0] / s - 1.0 / 3), Math.abs(xyz[1] / s - 1.0 / 3));
        A.pruefe(fe <= 1e-4, "Spektrum gleicher Energie nach XYZ: Normfarbwertanteile gegen Weiß E (1/3, 1/3)", f("x %.6f, y %.6f; Abweichung %.1e", xyz[0] / s, xyz[1] / s, fe), "≤ 1 · 10⁻⁴ [G]");
        double[] d = new double[Spektrum.N];
        for (int i = 0; i < Spektrum.N; i++) d[i] = Spektrum.d65(Spektrum.lambda(i));
        double[] rgb = Spektrum.nachRgb(Spektrum.nachXyz(d));
        double fd = Math.max(Math.abs(rgb[0] - 1), Math.max(Math.abs(rgb[1] - 1), Math.abs(rgb[2] - 1)));
        A.pruefe(fd <= 1e-4, "D65 (Y = 1) nach linearem sRGB gegen (1, 1, 1)", f("%.7f; %.7f; %.7f", rgb[0], rgb[1], rgb[2]), "≤ 1 · 10⁻⁴ [G]");
        SplittableRandom r = new SplittableRandom(7);
        double fr = 0;
        for (int t = 0; t < 2000; t++) {
            double[] c = {r.nextDouble(), r.nextDouble(), r.nextDouble()};
            double[] g = Spektrum.gewichte(c), sp = new double[Spektrum.N];
            for (int i = 0; i < Spektrum.N; i++) sp[i] = Spektrum.reflexion(g, Spektrum.lambda(i)) * Spektrum.d65(Spektrum.lambda(i));
            double[] back = Spektrum.nachRgb(Spektrum.nachXyz(sp));
            for (int k = 0; k < 3; k++) fr = Math.max(fr, Math.abs(back[k] - c[k]));
        }
        A.pruefe(fr <= 1e-6, "RGB → Spektrum (Mallett und Yuksel) → RGB unter D65, 2 000 Farben in [0, 1]³", f("größte Abweichung %.1e", fr), "≤ 1 · 10⁻⁶ [G]");
        // Auskunft: die Szene spektral gegen die drei Kanäle des Echtzeitbilds
        StringBuilder b = new StringBuilder();
        for (int c = 0; c < 3; c++) {
            double l = Spektrum.KANAELE[c];
            b.append(f("%.0f nm: Rayleigh %.3f, Ozon %.4f, Wasser %.4f; ", l, Spektrum.rayleigh(l, Atmosphere.RAYLEIGH_H) / Atmosphere.RAYLEIGH[c],
                    Spektrum.OZON_DICHTE * Spektrum.ozon(l) / Atmosphere.OZONE[c], Spektrum.wasser(l) / Wasseroptik.POPE_FRY[c]));
        }
        A.info("Spektral gegen die Kanäle des Echtzeitbilds (Verhältnis)", b.toString());
        double lux = Spektrum.sonneLux();
        double[] sonne = new double[Spektrum.N];
        for (int i = 0; i < Spektrum.N; i++) sonne[i] = Spektrum.sonne(Spektrum.lambda(i));
        double[] sx = Spektrum.nachXyz(sonne), srgb = Spektrum.nachRgb(sx);
        A.info("Sonne außerhalb der Luft (ASTM G173) gegen das Echtzeitbild", f("%.0f lx gegen %.0f lx je Kanal; Farbe x %.4f, y %.4f; sRGB relativ %.3f / %.3f / %.3f (Echtzeit weiß)",
                lux, Lichtquellen.SONNE_LX, sx[0] / (sx[0] + sx[1] + sx[2]), sx[1] / (sx[0] + sx[1] + sx[2]), srgb[0] / sx[1], srgb[1] / sx[1], srgb[2] / sx[1]));
        A.info("Schwebstoff (F54 A): Fournier-Forand", f("n %.2f, μ_FF %.4f; b_b/b der Tabelle %.4f (Ziel %.3f); b = %.2f 1/m bei %.0f mg/l",
                Phasen.FF_N, Phasen.FF_MU, Phasen.FF_BB, Wellen.BB_ZU_B, new Wellen.Szenenwerte(Atmosphere.forVisibility(30, 0.08), new Wasseroptik()).bWasser, new Wasseroptik().schwebstoff));
    }

    // ------------------------------------------------------------------ 1 Ofentest

    static void ofen() {
        A.teil("1 Ofentest (Prüfstein 1)");
        Atmosphere luft = Atmosphere.forVisibility(30, 0.08);
        Wasseroptik w = new Wasseroptik();
        Ebenen sz = new Ebenen(-12, -10, new double[] {1, 1, 1}, 0);
        Referenz ref = new Referenz(sz, luft, w, 0);
        ref.sigma2 = 1e-6;                                                      // glatt: die Schattung der Mikrofacetten verliert Energie (Auskunft unten)
        double[][] richtungen = {{0, -0.5, -0.866}, {0, -0.866, -0.5}, {0, 0.087, -0.996}, {0, 0.5, -0.866}, {0, -0.999, 0.02}};
        String[] namen = {"30° unter der Kimm", "60° unter", "5° über", "30° über", "senkrecht nach unten"};
        for (boolean sp : new boolean[] {true, false}) {
            ref.spektral = sp;
            int N = n(1_000_000);
            for (int q = 0; q < richtungen.length; q++) {
                final int qq = q;
                double[][] sum = IntStream.range(0, 16).parallel().mapToObj(t -> {
                    Pfadverfolger pv = ref.verfolger(1000L * qq + t + (sp ? 0 : 99));
                    pv.ofen();
                    double s = 0, s2 = 0, tiefe = 0;
                    for (int i = 0; i < N / 16; i++) {
                        pv.neueWellen();
                        double[] L = pv.verfolge(new double[3], norm(richtungen[qq]));
                        double m = 0;
                        for (int k = 0; k < L.length; k++) m += L[k];
                        m /= L.length;
                        s += m;
                        s2 += m * m;
                    }
                    return new double[] {s, s2, tiefe};
                }).toArray(double[][]::new);
                double s = 0, s2 = 0;
                for (double[] x : sum) {
                    s += x[0];
                    s2 += x[1];
                }
                int nn = (N / 16) * 16;
                double mean = s / nn, sig = Math.sqrt(Math.max(0, s2 / nn - mean * mean) / nn);
                A.pruefe(Math.abs(mean - 1) <= Math.max(3 * sig, 0) + 1e-12 || Math.abs(mean - 1) <= 1e-4,
                        "Ofentest " + (sp ? "spektral" : "drei Kanäle") + ", " + namen[q] + " (Luft, glattes Wasser 2 m über dem Boden mit Albedo 1)",
                        f("%.5f ± %.5f (%d Pfade)", mean, sig, nn), "1 ± 3 σ [G]");
                A.pruefe(3 * sig <= 1e-3, "Ofentest: Streuung des Mittels klein genug für 10⁻³", f("3 σ = %.1e", 3 * sig), "3 σ ≤ 1 · 10⁻³ [G]");
            }
        }
    }

    /** Auskunft: Verlust an Energie der rauen Wasseroberfläche (Schattung nach Smith, nur Einfachstreuung an Facetten). */
    static void ofenRau() {
        Atmosphere luft = Atmosphere.forVisibility(30, 0.08);
        Ebenen sz = new Ebenen(-12, -10, new double[] {1, 1, 1}, 0);
        StringBuilder b = new StringBuilder();
        for (double wind : new double[] {0, 5, 12}) {
            Referenz ref = new Referenz(sz, luft, new Wasseroptik(), wind);
            ref.spektral = false;
            int N = n(400_000);
            double[] sum = IntStream.range(0, 16).parallel().mapToObj(t -> {
                Pfadverfolger pv = ref.verfolger(77_000 + t + (long) (wind * 1000));
                pv.ofen();
                double s = 0;
                for (int i = 0; i < N / 16; i++) {
                    double[] L = pv.verfolge(new double[3], norm(new double[] {0, -0.5, -0.866}));
                    s += (L[0] + L[1] + L[2]) / 3;
                }
                return new double[] {s};
            }).reduce(new double[] {0}, (x, y) -> new double[] {x[0] + y[0]});
            b.append(f("Wind %.0f m/s (σ² %.4f): %.4f; ", wind, Wasseroptik.coxMunk(wind), sum[0] / ((N / 16) * 16)));
        }
        A.info("Ofentest mit rauem Wasser (Cox und Munk), 30° unter der Kimm", b + "die Schattung der Facetten nach Smith gibt Energie ab, die Mehrfachstreuung zwischen den Facetten fehlt");
    }

    static double[] norm(double[] d) {
        double n = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        return new double[] {d[0] / n, d[1] / n, d[2] / n};
    }

    // ------------------------------------------------------------------ 2 Beer–Lambert und Luft

    /** Ein Schätzer je Pfad: schreibt drei Werte in aus. */
    interface Schaetzer {
        void pfad(Pfadverfolger pv, double[] aus);
    }

    /** Mittel und Streuung des Mittels je Kanal über n Pfade in 16 Teilen: {m0, m1, m2, σ0, σ1, σ2}. */
    static double[] schaetze(Referenz ref, long saat, int n, Schaetzer s) {
        double[][] r = IntStream.range(0, 16).parallel().mapToObj(t -> {
            Pfadverfolger pv = ref.verfolger(saat + t);
            double[] acc = new double[6], v = new double[3];
            for (int i = 0; i < n / 16; i++) {
                java.util.Arrays.fill(v, 0);
                s.pfad(pv, v);
                for (int k = 0; k < 3; k++) {
                    acc[k] += v[k];
                    acc[3 + k] += v[k] * v[k];
                }
            }
            return acc;
        }).toArray(double[][]::new);
        int nn = (n / 16) * 16;
        double[] e = new double[6];
        for (int k = 0; k < 3; k++) {
            double a = 0, b = 0;
            for (double[] x : r) {
                a += x[k];
                b += x[3 + k];
            }
            e[k] = a / nn;
            e[3 + k] = Math.sqrt(Math.max(0, b / nn - e[k] * e[k]) / nn);
        }
        return e;
    }

    /**
     * Prüft drei Schätzungen gegen das Soll: die Abweichung höchstens 3 σ und σ relativ höchstens 10⁻³ (Grenze von
     * 12.8 für Schätzer mit Rauschen ausgelegt, siehe Werkbuch).
     */
    static void gegen(String groesse, double[] e, double[] soll, String einheit) {
        double fmax = 0, smax = 0;
        boolean ok = true;
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < 3; k++) {
            double dev = Math.abs(e[k] / soll[k] - 1), sr = e[3 + k] / Math.abs(soll[k]);
            fmax = Math.max(fmax, dev);
            smax = Math.max(smax, sr);
            ok &= dev <= 3 * sr && sr <= 1e-3;
            b.append(f("%.0f nm %s gegen %s; ", Spektrum.KANAELE[k], zahl(e[k]), zahl(soll[k])));
        }
        A.pruefe(ok, groesse, b + f("größte relative Abweichung %.1e, σ relativ bis %.1e", fmax, smax), "≤ 3 σ, σ ≤ 1 · 10⁻³ relativ [G]");
    }

    static String zahl(double v) { return Math.abs(v) >= 10 ? f("%.2f", v) : f("%.5f", v); }

    static void beerLambert() {
        A.teil("2 Beer–Lambert und Durchlässigkeit (Prüfstein 2)");
        Atmosphere luft = Atmosphere.forVisibility(30, 0.08);
        Wasseroptik w = new Wasseroptik();
        w.schwebstoff = 0;                                                    // nur Absorption
        Ebenen sz = new Ebenen(-1000, Double.NaN, new double[] {0, 0, 0}, 0);
        Referenz ref = new Referenz(sz, luft, w, 0);
        ref.spektral = false;
        double s = 2.0;
        double[] sollW = new double[3];
        for (int k = 0; k < 3; k++) sollW[k] = Math.exp(-Wasseroptik.POPE_FRY[k] * s);
        int N = n(32_000_000);
        gegen("Spektrales Tracking durch 2 m reines Wasser (drei Kanäle zugleich), " + N + " Pfade", schaetze(ref, 500, N, (pv, v) -> {
            double[] wg = {1, 1, 1};
            if (pv.pruefeWasserflug(s, wg) == 0) System.arraycopy(wg, 0, v, 0, 3);
        }), sollW, "");
        double[] d = norm(new double[] {0, Math.sin(Math.toRadians(30)), -Math.cos(Math.toRadians(30))});
        double[] tau = optischeDicke(luft, d, 0), sollL = new double[3];
        for (int k = 0; k < 3; k++) sollL[k] = Math.exp(-tau[k]);
        int K = n(12_000_000);
        gegen("Spektrales Tracking durch die Luft (Anteil, der die Oberkante erreicht), 30° über der Kimm, gegen Quadratur (Rayleigh, Mie, Ozon), " + K + " Pfade",
                schaetze(ref, 1900, K, (pv, v) -> {
                    double[] wg = {1, 1, 1};
                    if (pv.pruefeLuftflug(new double[3], d, wg) == 0) System.arraycopy(wg, 0, v, 0, 3);
                }), sollL, "");
        int M = n(16_000_000);
        gegen("Ratio-Tracking durch die Luft, 30° über der Kimm bis zur Oberkante, gegen Quadratur, " + M + " Pfade", schaetze(ref, 900, M, (pv, v) -> {
            double[] T = {1, 1, 1};
            pv.pruefeLuft(new double[3], d, T);
            System.arraycopy(T, 0, v, 0, 3);
        }), sollL, "");
    }

    /** Optische Dicke je Kanal von der Höhe h0 längs d bis zur Oberkante (Quadratur in 200 000 Schritten). */
    static double[] optischeDicke(Atmosphere luft, double[] d, double h0) { return optischeDicke(luft, d, h0, 200_000); }

    static double[] optischeDicke(Atmosphere luft, double[] d, double h0, int n) {
        double R = Atmosphere.BOTTOM, RT = Atmosphere.TOP, y = R + h0;
        double bq = y * d[1], c = y * y - RT * RT, tEnd = -bq + Math.sqrt(bq * bq - c);
        double[] tau = new double[3];
        for (int i = 0; i < n; i++) {
            double t = (i + 0.5) * tEnd / n;
            double x = t * d[0], yy = y + t * d[1], z = t * d[2];
            double h = Math.sqrt(x * x + yy * yy + z * z) - R;
            for (int k = 0; k < 3; k++) tau[k] += luft.extinction(h, k) * tEnd / n;
        }
        return tau;
    }

    // ------------------------------------------------------------------ 6 Schätzer seit P7c

    /**
     * Prüfungen zu den Schätzern, die P7c für die Szenen aus dem Gebiet eingeführt hat (Werkbuch 12.8, Stand P7c): die
     * Durchlässigkeit der Luft durch Quadratur statt Ratio-Tracking und der Weg Sonne → raue Fläche → unter Wasser.
     */
    static void schaetzer() {
        A.teil("6 Schätzer seit P7c (zu den Prüfsteinen 1–3)");
        Atmosphere luft = Atmosphere.forVisibility(30, 0.08);
        double fmax = 0;
        StringBuilder b = new StringBuilder();
        for (double[] fall : new double[][] {{0, 30}, {0, 2}, {1000, -0.3}, {5, 0.05}}) {
            double h0 = fall[0], e = Math.toRadians(fall[1]);
            double[] d = norm(new double[] {0, Math.sin(e), -Math.cos(e)});
            Ebenen sz = new Ebenen(-1e5, Double.NaN, new double[] {0, 0, 0}, h0);
            Referenz ref = new Referenz(sz, luft, new Wasseroptik(), 0);
            ref.spektral = false;
            double[] T = {1, 1, 1};
            ref.verfolger(1).pruefeLuftQuadratur(new double[3], d, T);
            double[] tau = optischeDicke(luft, d, h0, 4_000_000);
            for (int k = 0; k < 3; k++) fmax = Math.max(fmax, Math.abs(T[k] / Math.exp(-tau[k]) - 1));
            b.append(f("aus %.0f m, %.2f°: T %.6f / %.6f / %.6f; ", h0, fall[1], T[0], T[1], T[2]));
        }
        A.pruefe(fmax <= 1e-6, "Durchlässigkeit der Luft durch Quadratur (Gauß-Legendre in Abschnitten) gegen Mittelpunktregel in 4 Mio. Schritten",
                b + f("größte relative Abweichung %.1e", fmax), "≤ 1 · 10⁻⁶ [G]");
        // Grund unter glattem, klarem Wasser, ohne Luft: Sonne → Brechung → Grund (Lambert) → Brechung → Auge
        Wasseroptik w = new Wasseroptik();
        w.schwebstoff = 0;
        double tiefe = 5, rho = 0.1, hs = Math.toRadians(40), hv = Math.toRadians(45);
        Ebenen sz = new Ebenen(-0.5 - tiefe, -0.5, new double[] {rho, rho, rho}, 10);
        Referenz ref = new Referenz(sz, luft, w, 0);
        ref.spektral = false;
        ref.sigma2 = 1e-8;
        ref.lichter.clear();
        ref.lichter.add(new Licht(new double[] {0, Math.sin(hs), -Math.cos(hs)}, Lichtquellen.SONNE_RADIUS / 1.496e11, 0, 1, true));
        double[] v = {0, -Math.sin(hv), Math.cos(hv)};
        Pfadverfolger p0 = ref.verfolger(1);
        double a = p0.wl.aWasser[0], E = p0.wl.sonne[0], eta = w.n;
        double cs = Math.sin(hs), cws = Math.sqrt(1 - (1 - cs * cs) / (eta * eta)), cv = Math.sin(hv), cwv = Math.sqrt(1 - (1 - cv * cv) / (eta * eta));
        double soll = rho / Math.PI * E * cs * (1 - fresnelFormel(cs, cws, eta)) * Math.exp(-a * tiefe / cws) * Math.exp(-a * tiefe / cwv) * (1 - fresnelFormel(cv, cwv, eta)) / (eta * eta);
        int N = n(24_000_000);
        double[] e = schaetze(ref, 4100, N, (pv, out) -> {
            pv.ohneLuft();
            double[] L = pv.verfolge(new double[3], v);
            System.arraycopy(L, 0, out, 0, 3);
        });
        double sig = e[3] / soll, ab = Math.abs(e[0] / soll - 1);
        A.pruefe(ab <= 3 * sig && sig <= 1e-3, "Grund (Albedo 0,1) unter 5 m glattem, klarem Wasser ohne Luft, Sonne 40°, Blick 45° hinab, rot (a = " + f("%.3f", a) + "/m): Pfadverfolger gegen die Formel, " + N + " Pfade",
                f("%.6g gegen %.6g cd/m², Abweichung %.1e, σ relativ %.1e (Rest der Spiegelung zwischen Grund und Fläche unter 10⁻⁴)", e[0], soll, e[0] / soll - 1, sig), "≤ 3 σ, σ ≤ 1 · 10⁻³ relativ [G]");
        grundRau();
    }

    /**
     * Grund unter rauem, klarem Wasser ohne Luft: der Pfadverfolger gegen zwei Integrale über die Halbkugel unter der
     * Fläche (Mittelpunktregel in θ und φ) mit derselben BSDF: Sonne → raue Fläche → Grund (Lambert) → raue Fläche → Auge.
     * Prüft, dass der Weg durch die Fläche genau einmal zählt.
     */
    static void grundRau() {
        Atmosphere luft = Atmosphere.forVisibility(30, 0.08);
        Wasseroptik w = new Wasseroptik();
        w.schwebstoff = 0;
        double tiefe = 5, rho = 0.1, hs = Math.toRadians(40), hv = Math.toRadians(45), s2 = 0.03;
        Ebenen sz = new Ebenen(-0.5 - tiefe, -0.5, new double[] {rho, rho, rho}, 10);
        Referenz ref = new Referenz(sz, luft, w, 0);
        ref.spektral = false;
        ref.sigma2 = s2;
        double[] ls = {0, Math.sin(hs), -Math.cos(hs)}, v = {0, -Math.sin(hv), Math.cos(hv)}, oben = {0, 1, 0}, zumAuge = {0, Math.sin(hv), -Math.cos(hv)};
        ref.lichter.clear();
        ref.lichter.add(new Licht(ls, Lichtquellen.SONNE_RADIUS / 1.496e11, 0, 1, true));
        Pfadverfolger p0 = ref.verfolger(1);
        double a = p0.wl.aWasser[0], E = p0.wl.sonne[0], eta = w.n;
        // Halbkugel unter der Fläche: Richtungen o (abwärts) mit θ zur Senkrechten nach unten
        int nt = 3000, np = 3000;
        double ein = 0, aus = 0;
        for (int it = 0; it < nt; it++) {
            double th = (it + 0.5) * (Math.PI / 2) / nt, st = Math.sin(th), ct = Math.cos(th), dw = st * (Math.PI / 2) / nt * (2 * Math.PI) / np;
            double att = Math.exp(-a * tiefe / ct);
            for (int ip = 0; ip < np; ip++) {
                double ph = (ip + 0.5) * 2 * Math.PI / np;
                double[] o = {st * Math.cos(ph), -ct, st * Math.sin(ph)};
                ein += Pfadverfolger.flaeche(s2, eta, ls, o, oben, eta, 1) * ct * att * dw;
                aus += Pfadverfolger.flaeche(s2, eta, o, zumAuge, oben, 1, eta) * att * dw;
            }
        }
        double soll = rho / Math.PI * E * ein * aus;
        int N = n(24_000_000);
        double[] e = schaetze(ref, 4200, N, (pv, out) -> {
            pv.ohneLuft();
            double[] L = pv.verfolge(new double[3], v);
            System.arraycopy(L, 0, out, 0, 3);
        });
        double sig = e[3] / soll;
        A.pruefe(Math.abs(e[0] / soll - 1) <= 3 * sig && sig <= 1e-3, "Grund (Albedo 0,1) unter 5 m rauem (σ² 0,03), klarem Wasser ohne Luft, sonst wie oben: Pfadverfolger gegen zwei Integrale mit derselben BSDF (3 000 × 3 000), " + N + " Pfade",
                f("%.6g gegen %.6g cd/m², Abweichung %.1e, σ relativ %.1e", e[0], soll, e[0] / soll - 1, sig), "≤ 3 σ, σ ≤ 1 · 10⁻³ relativ [G]");
    }

    /** Fresnel unpolarisiert aus cos des Winkels in Luft und im Wasser. */
    static double fresnelFormel(double ci, double ct, double n) {
        double rs = (ci - n * ct) / (ci + n * ct), rp = (n * ci - ct) / (n * ci + ct);
        return 0.5 * (rs * rs + rp * rp);
    }

    // ------------------------------------------------------------------ 3 Rayleigh-Himmel

    static void rayleigh() {
        A.teil("3 Einfacher Rayleigh-Himmel (Prüfstein 3)");
        Atmosphere luft = Atmosphere.forVisibility(30, 0.08);
        Wasseroptik w = new Wasseroptik();
        Ebenen sz = new Ebenen(Double.NaN, Double.NaN, new double[] {0, 0, 0}, 0);
        Referenz ref = new Referenz(sz, luft, w, 0);
        ref.spektral = false;
        double hs = Math.toRadians(30);
        double[] sonne = {0, Math.sin(hs), Math.cos(hs)};                        // aus Süden, 30° hoch
        ref.lichter.clear();
        ref.lichter.add(new Licht(sonne, Math.sin(Math.toRadians(0.2666)), 0, 1, true));
        double[][] richt = {{0, 1, 0}, norm(new double[] {0, Math.sin(Math.toRadians(30)), -Math.cos(Math.toRadians(30))}),
                norm(new double[] {Math.cos(Math.toRadians(10)), Math.sin(Math.toRadians(10)), 0})};
        String[] namen = {"Zenit", "30° hoch nach Norden (gegen die Sonne)", "10° hoch nach Osten"};
        for (int q = 0; q < richt.length; q++) {
            final int qq = q;
            int N = n(4_000_000);
            gegen("Rayleigh, Einfachstreuung (Stoßschätzer), " + namen[q] + ", Sonne 30° im Süden, " + N + " Pfade", schaetze(ref, 3000 + 100L * q, N, (pv, v) -> {
                pv.nurRayleigh();
                double[] L = pv.einfachStoesse(new double[3], richt[qq]);
                System.arraycopy(L, 0, v, 0, 3);
            }), rayleighQuadratur(richt[q], sonne), "cd/m²");
        }
    }

    /** Einfachstreuung der Rayleigh-Luft (ohne Mie und Ozon) mit Quadratur in double. */
    static double[] rayleighQuadratur(double[] v, double[] l) {
        double R = Atmosphere.BOTTOM, RT = Atmosphere.TOP;
        double y = R, bq = y * v[1], c = y * y - RT * RT, tEnd = -bq + Math.sqrt(bq * bq - c);
        int n = 20_000;
        double[] L = new double[3], tauV = new double[3];
        double mu = v[0] * l[0] + v[1] * l[1] + v[2] * l[2], pR = Phasen.rayleigh(mu);
        double dt = tEnd / n;
        for (int i = 0; i < n; i++) {
            double t = (i + 0.5) * dt;
            double[] p = {t * v[0], y + t * v[1], t * v[2]};
            double h = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]) - R;
            double dR = Atmosphere.rayleighDensity(h);
            // optische Dicke zur Sonne von p
            double b2 = p[0] * l[0] + p[1] * l[1] + p[2] * l[2], c2 = p[0] * p[0] + p[1] * p[1] + p[2] * p[2] - RT * RT, tS = -b2 + Math.sqrt(b2 * b2 - c2);
            int m = 2000;
            double tauS = 0;
            for (int j = 0; j < m; j++) {
                double s = (j + 0.5) * tS / m;
                double qx = p[0] + s * l[0], qy = p[1] + s * l[1], qz = p[2] + s * l[2];
                tauS += Atmosphere.rayleighDensity(Math.sqrt(qx * qx + qy * qy + qz * qz) - R) * tS / m;
            }
            for (int k = 0; k < 3; k++) {
                double tv = tauV[k] + Atmosphere.RAYLEIGH[k] * dR * dt / 2;
                L[k] += Math.exp(-tv - Atmosphere.RAYLEIGH[k] * tauS) * Atmosphere.RAYLEIGH[k] * dR * pR * Lichtquellen.SONNE_LX * dt;
                tauV[k] += Atmosphere.RAYLEIGH[k] * dR * dt;
            }
        }
        return L;
    }

    // ------------------------------------------------------------------ 5 gekrümmte Strahlen

    static void kruemmung() {
        A.teil("5 Gekrümmte Strahlen (Prüfstein 5)");
        // (a) ebenes, lineares Gefälle n(y) = n0 + g y: genaue Lösung die Kettenlinie n0 + g y = c cosh(g (x − x0) / c)
        double n0 = 1.000293, k = 0.13, Rer = 6_371_000, g = -k * n0 / Rer;
        Strahlgleichung sg = new Strahlgleichung(new Strahlgleichung.Brechzahl() {
            @Override public double n(double[] r) { return n0 + g * r[1]; }

            @Override public double[] gradient(double[] r) { return new double[] {0, g, 0}; }
        });
        double th0 = Math.toRadians(0.3);
        double[] z = sg.start(new double[] {0, 0, 0}, new double[] {Math.cos(th0), Math.sin(th0), 0});
        double h = 10, s = 0;
        while (s < 30_000 - 1e-9) {
            z = sg.schritt(z, h);
            s += h;
        }
        double c = n0 * Math.cos(th0), x0 = -(c / g) * asinh(Math.tan(th0));
        double ySoll = (c * Math.cosh(g * (z[0] - x0) / c) - n0) / g;
        double fehler = Math.abs(z[1] - ySoll) / 30_000;
        // Kreisbogen zur Auskunft: Krümmung |g| cos θ / n
        double kr = Math.abs(g) / n0, yKreis = z[0] * Math.tan(th0) - kr * z[0] * z[0] / 2 / Math.pow(Math.cos(th0), 3);
        A.pruefe(fehler <= 1e-6, "Strahlgleichung (RK4, Schritt 10 m) über 30 km im linearen Gefälle von n (k = 0,13, 0,3° steigend) gegen die genaue Kettenlinie",
                f("Höhe am Ende %.6f m gegen %.6f m, relativ zur Länge %.1e; Kreisbogen als Näherung %.4f m", z[1], ySoll, fehler, yKreis), "relativ ≤ 1 · 10⁻⁶ [G]");
        // (b) Kugel mit dn/dr = −k n0 / R gegen das gesenkte Gelände des Echtzeitbilds (gerader Strahl, Fläche − d² / 2 R_eff)
        double hAuge = 3, d = 5_000, Reff = Rer / (1 - k);                // vor der Kimm (6,6 km): der erste Schnitt
        double tanD = (hAuge + d * d / (2 * Reff)) / d;
        Strahlgleichung kg = new Strahlgleichung(new Strahlgleichung.Brechzahl() {
            @Override public double n(double[] r) { return n0 * (1 - k * (Math.sqrt(r[0] * r[0] + r[1] * r[1]) - Rer) / Rer); }

            @Override public double[] gradient(double[] r) {
                double rr = Math.sqrt(r[0] * r[0] + r[1] * r[1]), gg = -k * n0 / Rer;
                return new double[] {gg * r[0] / rr, gg * r[1] / rr, 0};
            }
        });
        double dl = Math.atan(tanD);
        double[] zz = kg.start(new double[] {0, Rer + hAuge, 0}, new double[] {Math.cos(dl), -Math.sin(dl), 0});
        double schritt = 1, rPrev = Rer + hAuge;
        double[] prev = zz;
        int it = 0;
        while (it++ < 100_000) {
            double[] nz = kg.schritt(zz, schritt);
            double r = Math.hypot(nz[0], nz[1]);
            if (r <= Rer) {
                double f = (rPrev - Rer) / (rPrev - r);
                double xh = zz[0] + f * (nz[0] - zz[0]), yh = zz[1] + f * (nz[1] - zz[1]);
                prev = new double[] {xh, yh};
                break;
            }
            zz = nz;
            rPrev = r;
        }
        double bogen = Rer * Math.atan2(prev[0], prev[1]);
        A.pruefe(Math.abs(bogen - d) <= 0.5, "Kugel mit dn/dr = −k n / R (k = 0,13) gegen das gesenkte Gelände des Echtzeitbilds: Treffer auf NHN aus 3 m Augenhöhe in 5 km",
                f("%.3f m gegen %.3f m (Bogen auf der Erde), Abweichung %.3f m", bogen, d, bogen - d), "≤ 0,5 m [G]");
    }

    static double asinh(double x) { return Math.log(x + Math.sqrt(x * x + 1)); }

}
