package com.dan.render.reference.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Flaeche;
import com.dan.core.szene.Lichtquellen;
import com.dan.core.szene.Scattering;
import com.dan.core.szene.Seegangsoptik;
import com.dan.core.szene.Wasseroptik;
import com.dan.render.reference.Ebenen;
import com.dan.render.reference.Referenz;
import java.nio.file.Path;
import java.util.List;

/**
 * Prüfung der wirksamen Wasserfläche ohne Karte (P7g, F63 A, Werkbuch HalligHooge Teil A, 12.8): die Tafel
 * {@link Flaeche} gegen den Pfadverfolger der Referenz. Im Ofen (die Umgebung leuchtet mit 1, keine Luft) und ohne
 * Wassersäule kommt vom Wasser genau die wirksame Spiegelung zurück. Dazu die Tafel zwischen ihren Stützstellen gegen
 * die Rechnung an der Stelle. Die Grenzen stehen vor der Messung fest; [G] heißt gesetzt. Attest
 * {@code docs/status/P7_Flaeche.md} ({@code --ohne-attest}: nicht). Seit P10d (Messung f): {@code --wellen [--pfade n] [--punkte m]},
 * siehe {@link #wellen}. Seit P11d (A24): {@code --glanz}, siehe {@link #glanzPruefung}.
 */
public final class FlaecheCheck {
    private static final Attest A = new Attest("com.dan.render.reference", Referenz.VERSION);

    public static void main(String[] args) {
        boolean attest = !List.of(args).contains("--ohne-attest");
        System.out.println("FlaecheCheck – wirksame Wasserfläche aus den Neigungen (Werkbuch HalligHooge Teil A, P7g)");
        if (List.of(args).contains("--glanz")) {
            try {
                glanzPruefung();
            } catch (Throwable e) {
                A.fehler(e);
            }
            if (attest) {
                Path p = A.schreibe(Path.of("docs", "status", "P11d_Glanz.md"), "P11d – FlaecheCheck --glanz (A24)");
                if (p != null) System.out.println("\nAttest: " + p);
            }
            System.out.println();
            System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
            System.exit(A.verstoesse() == 0 ? 0 : 1);
        }
        if (List.of(args).contains("--wellen")) {
            int pf = 500, pu = 768;
            gekippt = List.of(args).contains("--gekippt");
            for (int i = 0; i < args.length - 1; i++) {
                if (args[i].equals("--pfade")) pf = Integer.parseInt(args[i + 1]);
                if (args[i].equals("--punkte")) pu = Integer.parseInt(args[i + 1]);
            }
            try {
                wellen(pf, pu);
            } catch (Throwable e) {
                A.fehler(e);
            }
            if (attest) {
                Path p = A.schreibe(Path.of("docs", "status", "P10_FlaecheWellen.md"), "P10d – FlaecheCheck --wellen (Messung f)");
                if (p != null) System.out.println("\nAttest: " + p);
            }
            System.out.println();
            System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
            System.exit(A.verstoesse() == 0 ? 0 : 1);
        }
        try {
            Wasseroptik w = new Wasseroptik();
            float[] t = Flaeche.tafel(w.n);
            A.teil("1 Tafel gegen den Pfadverfolger (Ofen, ohne Säule)");
            Atmosphere luft = Atmosphere.forVisibility(30, 0.08);
            double fmax = 0;
            for (double wind : new double[] {2, 8, 15}) {
                double s2 = Wasseroptik.coxMunk(wind);
                StringBuilder b = new StringBuilder();
                for (double beta : new double[] {2, 5, 10, 20, 45}) {
                    double br = Math.toRadians(beta);
                    Ebenen sz = new Ebenen(-12, -10, new double[] {0, 0, 0}, 0);
                    Referenz ref = new Referenz(sz, luft, w, wind);
                    ref.spektral = false;
                    ref.ohneSaeule = true;
                    double[] v = {0, -Math.sin(br), -Math.cos(br)};
                    double[] e = ReferenceCheck.schaetze(ref, 9100 + (long) (wind * 100 + beta), 800_000, (pv, out) -> {
                        pv.ofen();
                        pv.ohneLuft();
                        double[] L = pv.verfolge(new double[3], v);
                        System.arraycopy(L, 0, out, 0, 3);
                    });
                    double[] fl = Flaeche.lies(t, Math.sin(br), s2);
                    double m = (e[0] + e[1] + e[2]) / 3, sig = (e[3] + e[4] + e[5]) / 3, ab = Math.max(0, Math.abs(fl[0] / m - 1) - 3 * sig / m);
                    fmax = Math.max(fmax, ab);
                    b.append(f("%.0f°: Tafel %.4f, Referenz %.4f ± %.1f %% (Fresnel am Spiegel %.4f, Spiegelbild %.1f° ± %.1f° hoch, Durchlass %.4f); ",
                            beta, fl[0], m, 100 * sig / m, Wasseroptik.fresnel(Math.sin(br), w.n), Math.toDegrees(Math.asin(fl[1])), Math.toDegrees(fl[2]), fl[3]));
                }
                A.info(f("Wind %.0f m/s (σ² %.4f), Blick unter der Waagerechten", wind, s2), b.toString());
            }
            A.pruefe(fmax <= 0.05, "Wirksame Spiegelung der Tafel gegen den Pfadverfolger (Wind 2 / 8 / 15 m/s, Blick 2–45° unter der Waagerechten, 800000 Pfade je Stelle)",
                    f("größte Abweichung über 3 σ %.1f %%", 100 * fmax), "≤ 5 % über 3 σ [G]");
            A.teil("2 Tafel zwischen den Stützstellen");
            double gmax = 0, tmax = 0, hmax = 0;
            String wo = "";
            java.util.SplittableRandom r = new java.util.SplittableRandom(11);
            for (int i = 0; i < 400; i++) {
                double mu = Math.pow(r.nextDouble(), 2), s2 = 0.003 + 0.112 * Math.pow(r.nextDouble(), 2);
                double[] a = Flaeche.rechne(mu, s2, w.n), b = Flaeche.lies(t, mu, s2);
                if (Math.abs(b[0] / a[0] - 1) > gmax) {
                    gmax = Math.abs(b[0] / a[0] - 1);
                    wo = f("μ %.4f, σ² %.4f", mu, s2);
                }
                tmax = Math.max(tmax, Math.abs(b[3] / a[3] - 1));
                hmax = Math.max(hmax, Math.abs(Math.toDegrees(Math.asin(b[1]) - Math.asin(a[1]))));
            }
            A.pruefe(gmax <= 0.02 && tmax <= 0.02, "Tafel bilinear gelesen gegen die Rechnung an der Stelle (400 Stellen, σ² 0,003–0,115)",
                    f("Spiegelung ± %.2f %% (bei %s), Durchlass ± %.2f %%, Höhe des Spiegelbilds ± %.2f°", 100 * gmax, wo, 100 * tmax, hmax), "≤ 2 % [G]");
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "P7_Flaeche.md"), "P7g – FlaecheCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ P10d, Messung f

    /** Referenz wie bis P10c (lange Wellen als gekippte Normale); seit P10d (F103 A) Vorgabe false: als Facetten. */
    static boolean gekippt;

    /** Seegang der Messung f [G]: Sturm im Flachen, wie Ansicht 5 ungefähr (Hs, Tp, Richtung wohin, Wind 10 m). */
    static final double HS = 1.8, TP = 6, THETA = 0, WIND = 18, AUGE = 6;

    /**
     * Messung f (P10d, Prüfstein 7): die Fläche mit langen Wellen nah am Auge. Eine ebene Wasserfläche 6 m unter dem Auge,
     * deren Normale die langen Wellen kippen ({@link Seegangsoptik}, Hs 1,8 m, Tp 6 s, Wind 18 m/s; Restvarianz mss −
     * aufgelöst) – in der Referenz alle Wellen ({@link Ebenen#neigung}, wie {@code Seegangsflaeche}), im Echtzeitbild auf
     * dem Prozessor wie {@code wasser.frag}: lange Wellen und Kräuselung nach der Größe des Bildpunkts gefiltert (1080
     * Bildpunkte auf 40°), der Rest in die wirksame Fläche ({@link Flaeche}). Zwei Fälle ohne Säule: a im Ofen (Umgebung
     * 1, ohne Luft) – nur die Spiegelung als Zahl; b mit Luft und Himmel (Sicht 30 km, drei Kanäle, Sonne 30° im Rücken),
     * das Echtzeitbild mit dem Himmel aus {@link Scattering#radiance} an den drei Proben des Spiegelbilds und (seit Teil 2)
     * der Luft zwischen Auge und Fläche ({@link Scattering#strecke}, 8 Schritte wie das Bild). Blick gegen
     * die Wellen und quer, je Band der Neigung 5–10°, 10–20°, 20–45° nach unten; je Band m Bildpunkte (Vorgabe 768: das
     * Echtzeitbild schwankt von Bildpunkt zu Bildpunkt mit den gezeichneten Wellen) mit je n Pfaden (Vorgabe 500).
     * Seit F103 A (P10d): beide Seiten statistisch – die Echtzeit nimmt die Stärke der Spiegelung aus der Tafel mit der
     * ganzen Varianz um die mittlere Normale, die Referenz die langen Wellen als Facetten; zur Auskunft das Echtzeitbild
     * wie bis P10c. Grenze 1 ± 0,10 je Band (wie Prüfstein 14). {@code --gekippt}: die Referenz wie bis P10c.
     */
    static void wellen(int n, int m) {
        A.teil("Messung f: Fläche mit langen Wellen nah am Auge (P10d, Prüfstein 7)");
        Wasseroptik w = new Wasseroptik();
        float[] t = Flaeche.tafel(w.n);
        double[][] lang = Seegangsoptik.wellen(), kurz = Wasseroptik.wellen(WIND, 0);
        double[] grenzen = Seegangsoptik.baender(), tafel = Seegangsoptik.cdf();
        double s2 = Wasseroptik.coxMunk(WIND), ges = Seegangsoptik.neigung(HS, TP, THETA, 1e-6, lang, grenzen, tafel)[0], mss = ges + 0.005;
        double pix = 2 * Math.tan(Math.toRadians(20)) / 1080;
        Atmosphere luft = Atmosphere.forVisibility(30, 0.06);
        Scattering sc = new Scattering(luft);
        A.info("Seegang", f("Hs %.1f m, Tp %.0f s, Wind %.0f m/s (σ² der Kräuselung %.4f), Neigungsvarianz der langen Wellen %.4f, mss %.4f; Auge %.0f m; %d Bildpunkte je Band, %d Pfade je Bildpunkt", HS, TP, WIND, s2, ges, mss, AUGE, m, n));
        double hs = Math.toRadians(30);
        String[] bn = {"5–10°", "10–20°", "20–45°"};
        double[][] baender = {{5, 10}, {10, 20}, {20, 45}};
        double groesst = 0;
        for (double azB : new double[] {180, 90}) {                          // Blick gegen die Wellen (nach West) und quer (nach Süd)
            double azR = Math.toRadians(azB + 90);                           // Richtung im Gitter von Nord über Ost: 270° = West
            double[] l = {-Math.sin(azR) * Math.cos(hs), Math.sin(hs), Math.cos(azR) * Math.cos(hs)};     // Sonne im Rücken
            for (int fall = 0; fall < 2; fall++) {
                boolean ofen = fall == 0;
                StringBuilder b = new StringBuilder();
                for (int bi = 0; bi < 3; bi++) {
                    double[] sumE = new double[3], sumR = new double[3], sig2 = new double[3], var = new double[3];
                    java.util.SplittableRandom rz = new java.util.SplittableRandom(4242 + bi + (long) azB);
                    long t0 = System.nanoTime();
                    for (int k = 0; k < m; k++) {
                        double ab = Math.toRadians(baender[bi][0] + (baender[bi][1] - baender[bi][0]) * rz.nextDouble()), az = azR + Math.toRadians(40) * (rz.nextDouble() - 0.5);
                        double[] d = {Math.sin(az) * Math.cos(ab), -Math.sin(ab), -Math.cos(az) * Math.cos(ab)};
                        double tt = AUGE / Math.sin(ab), px = d[0] * tt, pz = d[2] * tt;
                        double[] e = echt(t, sc, ofen, d, px, pz, tt * pix / Math.sin(ab), lang, kurz, grenzen, tafel, s2, mss, w.n, l);
                        double[] r = ref(luft, w, ofen, d, l, n, mss, lang, grenzen, tafel, 7000 + 97L * k + bi);
                        for (int c = 0; c < 3; c++) var[c] += e[3 + c];
                        for (int c = 0; c < 3; c++) {
                            sumE[c] += e[c];
                            sumR[c] += r[c];
                            sig2[c] += r[3 + c] * r[3 + c];
                        }
                    }
                    double sek = (System.nanoTime() - t0) / 1e9;
                    double yE = y(sumE), yR = y(sumR);
                    groesst = Math.max(groesst, Math.abs(yE / yR - 1));
                    if (ofen) b.append(f("%s: Echtzeit %.4f, Referenz %.4f ± %.1f %%, Verhältnis %.3f; Echtzeit wie bis P10c %.3f (%.0f s); ", bn[bi], sumE[1] / m, sumR[1] / m,
                            100 * Math.sqrt(sig2[1]) / sumR[1], sumE[1] / sumR[1], var[1] / sumR[1], sek));
                    else b.append(f("%s: Echtzeit / Referenz %.3f (R %.3f G %.3f B %.3f; Referenz G %.4g cd/m² ± %.1f %%), Echtzeit wie bis P10c %.3f (%.0f s); ", bn[bi], yE / yR, sumE[0] / sumR[0], sumE[1] / sumR[1],
                            sumE[2] / sumR[2], sumR[1] / m, 100 * Math.sqrt(sig2[1]) / sumR[1], y(var) / yR, sek));
                }
                A.info(f("%s, Blick %s", ofen ? "a Ofen (Spiegelung als Zahl)" : "b Luft und Himmel, ohne Säule", azB == 180 ? "gegen die Wellen" : "quer zu ihnen"), b.toString());
            }
        }
        A.pruefe(groesst <= 0.10, "Messung f nach F103 A: Fläche mit Wellen nah am Auge, Echtzeit gegen Referenz (beide statistisch: Stärke aus der ganzen Varianz, lange Wellen als Facetten), je Band, Fall und Blick, Leuchtdichte",
                f("größte Abweichung %.1f %%", 100 * groesst), "1 ± 0,10 [G] (wie Prüfstein 14)");
    }

    // ------------------------------------------------------------------ P11d, A24

    /**
     * P11d (A24, Werkbuch HalligHooge Datei 60): das Glitzern der tiefen Sonne. Raue Fläche (Cox und Munk, nichts
     * aufgelöst) ohne Luft und ohne Säule, nur die Sonne; Blick zur Sonne, Fenster im Azimut ±max(3°, 40 α), Bänder
     * 0,5–2°, 2–5°, 5–10° hinab, Wind 3 und 8 m/s, Sonne 3°, 6°, 12°. Das Echtzeitbild wie {@code glanz} in
     * {@code wasser.glsl} (seit P11d mit G1(v) · G1(l) nach Smith) gegen die Referenz; zur Auskunft dasselbe ohne Smith
     * (bis P11d). Grenze 1 ± 0,05 je Band [G].
     */
    static void glanzPruefung() {
        A.teil("P11d: Glitzern der tiefen Sonne (A24)");
        Wasseroptik w = new Wasseroptik();
        Atmosphere luft = Atmosphere.forVisibility(30, 0.06);
        double auge = 7, groesst = 0;
        int m = 200, n = 128;
        double[][] baender = {{0.5, 2}, {2, 5}, {5, 10}};
        for (double wind : new double[] {3, 8}) {
            double s2 = Wasseroptik.coxMunk(wind), alpha = Math.sqrt(s2);
            for (double hsG : new double[] {3, 6, 12}) {
                double hs = Math.toRadians(hsG);
                double[] l = {0, Math.sin(hs), -Math.cos(hs)};
                StringBuilder b = new StringBuilder();
                for (double[] bd : baender) {
                    java.util.SplittableRandom rz = new java.util.SplittableRandom(77 + (long) (10 * bd[0]) + (long) hsG);
                    double sE = 0, sA = 0, sR = 0, v2 = 0, azW = Math.toRadians(Math.max(3, 40 * alpha));
                    for (int k = 0; k < m; k++) {
                        double ab = Math.toRadians(bd[0] + (bd[1] - bd[0]) * rz.nextDouble()), az = azW * (2 * rz.nextDouble() - 1);
                        double[] d = {Math.sin(az) * Math.cos(ab), -Math.sin(ab), -Math.cos(az) * Math.cos(ab)};
                        Ebenen sz = new Ebenen(-auge - 100, -auge, new double[] {0, 0, 0}, auge);
                        Referenz ref = new Referenz(sz, luft, w, wind);
                        ref.spektral = false;
                        ref.ohneSaeule = true;
                        ref.lichter.clear();
                        ref.lichter.add(new com.dan.render.reference.Licht(l, com.dan.core.szene.Lichtquellen.SONNE_RADIUS / 1.496e11, 0, 1, true));
                        double E = ref.verfolger(1).wl.sonne[1];
                        double[] r = ReferenceCheck.schaetze(ref, 3300 + 13L * k, n, (pv, out) -> {
                            pv.ohneLuft();
                            java.util.Arrays.fill(pv.wl.nacht, 0);
                            double[] L = pv.verfolge(new double[3], d);
                            System.arraycopy(L, 0, out, 0, 3);
                        });
                        double[] v = {-d[0], -d[1], -d[2]};
                        sE += E * glanz(v, l, s2, w.n, true);
                        sA += E * glanz(v, l, s2, w.n, false);
                        sR += r[1];
                        v2 += r[4] * r[4];
                    }
                    groesst = Math.max(groesst, Math.abs(sE / sR - 1));
                    b.append(f("%.1f–%.0f°: Echtzeit / Referenz %.3f (Referenz ± %.1f %%), bis P11d ohne Smith %.2f; ", bd[0], bd[1], sE / sR, 100 * Math.sqrt(v2) / sR, sA / sR));
                }
                A.info(f("Wind %.0f m/s (σ² %.4f), Sonne %.0f°, Blick zur Sonne", wind, s2, hsG), b.toString());
            }
        }
        A.pruefe(groesst <= 0.05, "P11d (A24): Glitzern der tiefen Sonne, Echtzeit (Cox und Munk mit Smith G1(v) · G1(l)) gegen Referenz, je Band, Wind und Sonne",
                f("größte Abweichung %.1f %%", 100 * groesst), "1 ± 0,05 [G]");
    }

    /** glanz aus wasser.glsl mit der Normalen nach oben und E = 1; smith: mit G1(v) · G1(l) (seit P11d). */
    static double glanz(double[] v, double[] l, double s2, double eta, boolean smith) {
        if (l[1] <= 0) return 0;
        double[] h = norm(new double[] {l[0] + v[0], l[1] + v[1], l[2] + v[2]});
        double mn = h[1], mv = v[1];
        if (mn <= 0 || mv <= 1e-3) return 0;
        double c2 = mn * mn, t2 = (1 - c2) / c2, p = Math.exp(-t2 / s2) / (Math.PI * s2);
        double r = Wasseroptik.fresnel(dot(l, h), eta) * p / (4 * mv * c2 * c2);
        return smith ? r * Flaeche.g1(mv, Math.sqrt(s2)) * Flaeche.g1(l[1], Math.sqrt(s2)) : r;
    }

    static double y(double[] c) { return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; }

    /** Das Echtzeitbild an einem Bildpunkt wie wasser.frag (nur die Spiegelung; im Ofen der Himmel 1). */
    static double[] echt(float[] t, Scattering sc, boolean ofen, double[] d, double px, double pz, double fuss, double[][] lang, double[][] kurz,
                         double[] grenzen, double[] tafel, double s2, double mss, double nw, double[] l) {
        double[] q = Seegangsoptik.neigungAm(HS, TP, THETA, fuss, px, -pz, 0, lang, grenzen, tafel);
        double langRest = Math.max(0, mss - q[2]);
        double gx = 0, gn = 0, aufg = 0;
        for (double[] wk : kurz) {
            double kk = Math.hypot(wk[0], wk[1]), lam = 2 * Math.PI / kk, g = Math.max(0, Math.min(1, lam / (2 * fuss) - 1));
            if (g <= 0) continue;
            double c = Math.cos(wk[0] * px - wk[1] * pz + wk[2]) * wk[3] * g;
            gx += c * wk[0] / kk;
            gn += c * wk[1] / kk;
            aufg += g * g * 0.5 * wk[3] * wk[3];
        }
        double rest = Math.max(s2 - aufg, 1e-5) + langRest, s2Ganz = rest + aufg + q[2];
        gx += q[0];
        gn += q[1];
        double[] n = norm(new double[] {-gx, 1, gn});                     // wie kraeuselungMit: o − ost gx + süd gn
        double[] v = {-d[0], -d[1], -d[2]};
        double mun = Math.max(0, dot(v, n));
        double[] fl = Flaeche.lies(t, mun, rest);
        // seit P10d (F103 A): die Stärke aus der Tafel mit der ganzen Varianz um die mittlere Normale; bis P10c fl[0]
        double fNeu = Flaeche.lies(t, v[1], s2Ganz)[0], fAlt = fl[0];
        if (ofen) return new double[] {fNeu, fNeu, fNeu, fAlt, fAlt, fAlt};
        double dn = dot(d, n);
        double[] r = {d[0] - 2 * dn * n[0], d[1] - 2 * dn * n[1], d[2] - 2 * dn * n[2]};
        double rn = dot(r, n);
        double[] rt = {r[0] - n[0] * rn, r[1] - n[1] * rn, r[2] - n[2] * rn};
        double lt = Math.sqrt(dot(rt, rt));
        double[] L = new double[3];
        double[] e = {Lichtquellen.SONNE_LX, Lichtquellen.SONNE_LX, Lichtquellen.SONNE_LX};
        if (lt > 1e-5) {
            for (int k = 0; k < 3; k++) rt[k] /= lt;
            // seit P11d (A26): vier Höhen an den Quantilen der Verteilung zu je ¼ wie wasser.frag (bis P11d drei nach Gauß)
            for (int qq = 0; qq < Flaeche.QUANTILE; qq++) {
                double eh = Math.asin(Math.max(0, Math.min(1, fl[Flaeche.QUANTIL + qq])));
                double[] rk = {rt[0] * Math.cos(eh) + n[0] * Math.sin(eh), rt[1] * Math.cos(eh) + n[1] * Math.sin(eh), rt[2] * Math.cos(eh) + n[2] * Math.sin(eh)};
                if (rk[1] < 0.002) rk = norm(new double[] {rk[0], rk[1] + (0.002 - rk[1]), rk[2]});
                double[] Lk = sc.radiance(AUGE, rk, l, e, null, null);
                for (int c = 0; c < 3; c++) L[c] += Lk[c] / Flaeche.QUANTILE;
            }
        } else {
            double[] rr = r[1] < 0.002 ? norm(new double[] {r[0], 0.002, r[2]}) : r;
            L = sc.radiance(AUGE, rr, l, e, null, null);
        }
        // die Luft zwischen Auge und Fläche (in der Referenz enthalten; im Bild legt der Himmel sie darüber)
        double weg = AUGE / Math.max(1e-6, -d[1]);
        double[][] st = sc.strecke(com.dan.core.szene.Atmosphere.BOTTOM + AUGE, d, l, e, null, null, weg, 8);
        double[] o = new double[6];
        for (int c = 0; c < 3; c++) {
            o[c] = st[0][c] + st[1][c] * fNeu * L[c];
            o[3 + c] = st[0][c] + st[1][c] * fAlt * L[c];
        }
        return o;
    }

    /** Die Referenz an einem Bildpunkt: Mittel und σ je Kanal über n Pfade, ohne Säule. */
    static double[] ref(Atmosphere luft, Wasseroptik w, boolean ofen, double[] d, double[] l, int n, double mss, double[][] lang, double[] grenzen, double[] tafel, long saat) {
        Ebenen sz = new Ebenen(Double.NaN, -AUGE, new double[] {0, 0, 0}, AUGE);
        // seit P10d (F103 A) wie Seegangsflaeche.facetten: die langen Wellen als Facetten; vorher als gekippte Normale
        sz.neigung = (x, z) -> {
            double[] q = Seegangsoptik.neigungAm(HS, TP, THETA, 1e-6, x, -z, 0, lang, grenzen, tafel);
            return gekippt ? new double[] {q[0], q[1], Math.max(0, mss - q[2])} : new double[] {0, 0, Math.max(mss, q[2])};
        };
        Referenz ref = new Referenz(sz, luft, w, WIND);
        ref.spektral = false;
        ref.ohneSaeule = true;
        ref.lichter.clear();
        ref.lichter.add(new com.dan.render.reference.Licht(l, com.dan.core.szene.Lichtquellen.SONNE_RADIUS / 1.496e11, 0, 1, true));
        return ReferenceCheck.schaetze(ref, saat, n, (pv, out) -> {
            if (ofen) {
                pv.ofen();
                pv.ohneLuft();
            }
            java.util.Arrays.fill(pv.wl.nacht, 0);
            double[] L = pv.verfolge(new double[3], d);
            System.arraycopy(L, 0, out, 0, 3);
        });
    }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    static double[] norm(double[] a) {
        double s = Math.sqrt(dot(a, a));
        return new double[] {a[0] / s, a[1] / s, a[2] / s};
    }

    static String f(String s, Object... a) { return String.format(java.util.Locale.GERMANY, s, a); }
}
