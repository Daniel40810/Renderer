package com.dan.render.reference.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Lichtquellen;
import com.dan.core.szene.Saeule;
import com.dan.core.szene.Wasseroptik;
import com.dan.render.reference.Ebenen;
import com.dan.render.reference.Licht;
import com.dan.render.reference.Phasen;
import com.dan.render.reference.Referenz;
import com.dan.render.reference.Saeulentabelle;

import java.nio.file.Path;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung der Tabellen der Wassersäule (P7d, Werkbuch HalligHooge Teil A, 12.8, F55 A): 1 Energie, 2 erster Stoß gegen
 * die Formel, 3 Grund nach Lambert, 4 tiefe Säule gegen Lee u. a. (1999, Auskunft), 5 Azimut, 6 Tafel des
 * Echtzeitbilds gegen eine Platte, 7 der Pfadverfolger der Referenz (glattes, trübes Wasser ohne Luft) gegen die
 * Tafel. Die Platte für 3, 5 und 6 ist ein eigener Schätzer: Photonen in drei Dimensionen, gezählt, wo sie die Fläche
 * wirklich erreichen (dann mit der Wahrscheinlichkeit F zurück ins Wasser), der Grund nach Lambert mit seiner Albedo.
 * Attest nach {@code docs/status/P7_Saeule.md} ({@code --ohne-attest}: nicht); {@code --schnell}: ein Zehntel.
 * Seit P10d (Messung e): {@code --leiter [--pfade n]}, siehe {@link #leiter}.
 */
public final class SaeuleCheck {
    private static final Attest A = new Attest("com.dan.render.reference", Referenz.VERSION);
    static double mass = 1;

    public static void main(String[] args) {
        boolean attest = !List.of(args).contains("--ohne-attest");
        if (List.of(args).contains("--schnell")) mass = 0.1;
        System.out.println("SaeuleCheck – Tabellen der Wassersäule (Werkbuch HalligHooge Teil A, P7d)");
        System.out.println("         " + Runtime.getRuntime().availableProcessors() + " Kerne");
        try {
            Saeule s = Saeule.lade();
            if (List.of(args).contains("--leiter")) {
                int pf = 1_000_000;
                for (int i = 0; i < args.length - 1; i++) if (args[i].equals("--pfade")) pf = Integer.parseInt(args[i + 1]);
                leiter(s, pf);
                if (attest) {
                    Path p = A.schreibe(Path.of("docs", "status", "P10_SaeuleLeiter.md"), "P10d – SaeuleCheck --leiter (Messung e)");
                    if (p != null) System.out.println("\nAttest: " + p);
                }
                System.out.println();
                System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
                System.exit(A.verstoesse() == 0 ? 0 : 1);
            }
            if (List.of(args).contains("--nur-referenz")) {
                referenz(s);
                throw new Exception("nur Teil 7");
            }
            energie();
            ersterStoss();
            grund(s);
            lee(s);
            azimut();
            tafel(s);
            referenz(s);
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "P7_Saeule.md"), "P7d – SaeuleCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static int n(double v) { return (int) Math.max(1, Math.round(v * mass)); }

    // ------------------------------------------------------------------ 1 Energie

    static void energie() {
        A.teil("1 Energie (ω = 1)");
        Saeulentabelle b = new Saeulentabelle();
        double xg = 10, fmax = 0;
        int nn = n(400_000);
        Saeulentabelle.Oben o = b.oben(1.0, 0.8, 99, nn, xg);
        StringBuilder w = new StringBuilder();
        for (int j = 1; j < Saeule.NX && Saeule.X[j] <= xg; j++) {
            double e = o.hinaus[j] + o.t[j];
            fmax = Math.max(fmax, Math.abs(e - 1));
            if (j % 3 == 0) w.append(f("x %.2f: %.4f; ", Saeule.X[j], e));
        }
        A.pruefe(fmax <= 5e-3, "Ohne Absorption: Fluss durch die Fläche hinaus und auf den schwarzen Grund, Summe gegen 1 (μ_s 0,8, Platten bis x = 10, " + nn + " Photonen)",
                w + f("größte Abweichung %.1e", fmax), "≤ 5 · 10⁻³ [G]");
    }

    // ------------------------------------------------------------------ 2 Erster Stoß

    /** Einfachstreuung, über den Azimut gemittelt und über das Fach mit μ gewichtet. */
    static double einfach(double u, double x, double mus, int k) {
        double c = Saeulentabelle.cVon(u), om = Saeulentabelle.omegaVon(u);
        double lo = Saeule.MU_MIN + (1 - Saeule.MU_MIN) * k / Saeule.NMU, hi = lo + (1 - Saeule.MU_MIN) / Saeule.NMU, s = 0;
        int nm = 64, nf = 512;
        double ss = Math.sqrt(1 - mus * mus);
        for (int a = 0; a < nm; a++) {
            double mu = lo + (hi - lo) * (a + 0.5) / nm, sv = Math.sqrt(1 - mu * mu), p = 0;
            for (int q = 0; q < nf; q++) p += Phasen.ffDichte(-mus * mu + ss * sv * Math.cos(Math.PI * (q + 0.5) / nf));
            p /= nf;
            double l = om * p * (1 - Math.exp(-c * x * (1 / mus + 1 / mu))) / (mus + mu);
            s += 2 * Math.PI * l * mu * (hi - lo) / nm;
        }
        return s / Saeule.raumFach(k);
    }

    static void ersterStoss() {
        A.teil("2 Erster Stoß gegen die Formel der Einfachstreuung");
        Saeulentabelle b = new Saeulentabelle();
        double fmax = 0;
        StringBuilder w = new StringBuilder();
        int nn = n(20_000_000);
        b.hoechstensStoesse = 1;
        for (double u : new double[] {0.1, 0.4}) {
            Saeulentabelle.Oben o = b.oben(u, 0.85, 7 + (long) (u * 100), nn);
            for (int j : new int[] {14, Saeule.NX - 1})
                for (int k : new int[] {1, 7}) {
                    double a = einfach(u, Saeule.X[j], 0.85, k), m = o.l1[j][k], e = m / a - 1;
                    fmax = Math.max(fmax, Math.abs(e));
                    w.append(f("u %.1f x %.1f μ_v %.2f: %.3e gegen %.3e; ", u, Saeule.X[j], Saeule.MU_V[k], m, a));
                }
        }
        A.pruefe(fmax <= 0.02, "Erster Stoß der Tabelle gegen ω p̄ (1 − e^(−c x (1/μ_s + 1/μ_v))) / (μ_s + μ_v), μ_s 0,85, " + nn + " Photonen",
                w + f("größte Abweichung %.1f %%", 100 * fmax), "≤ 2 % [G]");
    }

    // ------------------------------------------------------------------ Platte (eigener Schätzer)

    /** Platte in 3D: Leuchtdichte je Fach von μ_v; [0] Azimut zur Sonne hin (|φ| < 90°), [1] von ihr weg, [2] alles. */
    static double[][] platte(double u, double x, double mus, double rho, int n, long saat) {
        double c = Saeulentabelle.cVon(u), om = Saeulentabelle.omegaVon(u);
        int nt = Runtime.getRuntime().availableProcessors();
        double[][][] teil = new double[nt][3][Saeule.NMU];
        IntStream.range(0, nt).parallel().forEach(t -> {
            SplittableRandom r = new SplittableRandom(saat * 31 + t);
            double[][] z = teil[t];
            for (int i = t; i < n; i += nt) {
                // Sonne aus +x: das Licht läuft nach −x und hinab
                double ux = -Math.sqrt(1 - mus * mus), uy = 0, uz = -mus, d = 0, w = 1;
                for (int st = 0; st < 1_000_000; st++) {
                    double sl = -Math.log(1 - r.nextDouble()) / c, dn = d - uz * sl;
                    if (dn <= 0) {
                        int k = (uz < Saeule.MU_MIN) ? -1 : Math.min(Saeule.NMU - 1, (int) ((uz - Saeule.MU_MIN) / (1 - Saeule.MU_MIN) * Saeule.NMU));
                        if (k >= 0) {
                            z[ux > 0 ? 0 : 1][k] += w;
                            z[2][k] += w;
                        }
                        // die glatte Fläche spiegelt nach Fresnel zurück (in einem Zug: escape mit 1 − F oder zurück mit F)
                        if (r.nextDouble() >= Saeulentabelle.fresnelUnten(uz)) break;
                        d = 0;
                        uz = -uz;
                        continue;
                    }
                    if (dn >= x) {
                        if (rho <= 0) break;
                        d = x;
                        w *= rho;
                        double ct = Math.sqrt(r.nextDouble()), ph = 2 * Math.PI * r.nextDouble(), stt = Math.sqrt(1 - ct * ct);
                        ux = stt * Math.cos(ph);
                        uy = stt * Math.sin(ph);
                        uz = ct;
                    } else {
                        d = dn;
                        w *= om;
                        double[] q = Phasen.umAchse(new double[] {ux, uy, uz}, Phasen.ffZiehe(r.nextDouble()), r);
                        double l = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
                        ux = q[0] / l;
                        uy = q[1] / l;
                        uz = q[2] / l;
                    }
                    if (w < 1e-3) {
                        if (r.nextDouble() < 0.1) w *= 10; else break;
                    }
                }
            }
        });
        double[][] e = new double[3][Saeule.NMU];
        for (double[][] z : teil) for (int a = 0; a < 3; a++) for (int k = 0; k < Saeule.NMU; k++) e[a][k] += z[a][k];
        for (int k = 0; k < Saeule.NMU; k++) {
            e[0][k] /= n * Saeule.raumFach(k) / 2;
            e[1][k] /= n * Saeule.raumFach(k) / 2;
            e[2][k] /= n * Saeule.raumFach(k);
        }
        return e;
    }

    // ------------------------------------------------------------------ 3 Grund

    static void grund(Saeule s) {
        A.teil("3 Grund nach Lambert");
        Saeulentabelle b = new Saeulentabelle();
        double fmax = 0;
        StringBuilder w = new StringBuilder();
        int nn = n(1_000_000);
        for (double[] fall : new double[][] {{0.4, 11, 0.3}, {0.2, 16, 0.6}}) {
            int j = (int) fall[1];
            double u = fall[0], x = Saeule.X[j], rho = fall[2], mus = 0.85;
            // die Formel aus eigenen Läufen mit so vielen Photonen wie die Tabelle (die Platte endet unter x)
            Saeulentabelle.Oben o = b.oben(u, mus, 11, n(1_200_000), x * 1.0001);
            Saeulentabelle.Grund g = b.grund(u, x, 13, n(400_000));
            double[][] p = platte(u, x, mus, rho, nn, 17);
            for (int k : new int[] {1, 4, 7}) {
                double soll = o.l[j][k] + rho * o.t[j] * g.u[k] / (1 - rho * g.r), e = p[2][k] / soll - 1;
                fmax = Math.max(fmax, Math.abs(e));
                w.append(f("u %.1f x %.2f ρ %.1f μ_v %.2f: Platte %.4f, Formel %.4f; ", u, x, rho, Saeule.MU_V[k], p[2][k], soll));
            }
        }
        A.pruefe(fmax <= 0.03, "Platte mit Grund (Lambert, Albedo ρ) gegen L + ρ T U / (1 − ρ R), μ_s 0,85, " + nn + " Photonen", w + f("größte Abweichung %.1f %%", 100 * fmax), "≤ 3 % [G]");
    }

    // ------------------------------------------------------------------ 4 Lee

    static void lee(Saeule s) {
        A.teil("4 Tiefe Säule gegen Lee u. a. (1999), Auskunft");
        StringBuilder w = new StringBuilder();
        for (double u : new double[] {0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6}) {
            double t = s.l(u, 40, 1.0, Saeule.MU_V[Saeule.NMU - 1]), l = (0.084 + 0.17 * u) * u;
            w.append(f("u %.2f: %.4f gegen %.4f (%+.0f %%); ", u, t, l, 100 * (t / l - 1)));
        }
        A.info("r_rs tief, Sonne und Blick senkrecht, gegen (0,084 + 0,17 u) u (Hydrolight, Petzold)", w.toString());
    }

    // ------------------------------------------------------------------ 5 Azimut

    static void azimut() {
        A.teil("5 Azimut");
        Saeule s = Saeule.lade();
        double[][] p = platte(0.4, 40, 0.70, 0, n(2_000_000), 23);
        StringBuilder w = new StringBuilder();
        double fmax = 0;
        for (int k : new int[] {0, 3, 7}) {
            // Tafel: Mittel über die Hälfte |φ| < 90° (weg von der Sonne) ist L + 4C/π, die andere L − 4C/π
            double l = s.l(0.4, 40, 0.70, Saeule.MU_V[k]), c = s.c(0.4, 40, 0.70, Saeule.MU_V[k]), weg = l + 4 * c / Math.PI, hin = l - 4 * c / Math.PI;
            fmax = Math.max(fmax, Math.max(Math.abs(weg / p[1][k] - 1), Math.abs(hin / p[0][k] - 1)));
            w.append(f("μ_v %.2f: zur Sonne hin %.4f (Tafel %.4f), weg %.4f (Tafel %.4f); ", Saeule.MU_V[k], p[0][k], hin, p[1][k], weg));
        }
        A.pruefe(fmax <= 0.03, "Säule tief, u 0,4, μ_s 0,70: Licht in der Hälfte des Azimuts zur Sonne hin und weg von ihr, Platte gegen L ∓ 4C/π der Tabelle", w + f("größte Abweichung %.1f %%", 100 * fmax), "≤ 3 % [G]");
    }

    // ------------------------------------------------------------------ 6 Tafel

    /**
     * Prüfstein 6 (seit F61 A): Raster zwischen den Stützstellen, dicht im Flachen (x 0,04–1,4 in der Mitte der Stufen),
     * u neben den Stufen, hohe und tiefe Sonne, flacher und steiler Blick; dazu zwei Stellen mit Grund. Je Stelle acht
     * Platten mit eigener Saat; es zählt die Abweichung über 3 σ des Mittels.
     */
    static void tafel(Saeule s) {
        A.teil("6 Tafel des Echtzeitbilds gegen die Platte");
        Wasseroptik wo = new Wasseroptik();
        double[] xs = {0.039, 0.077, 0.136, 0.2245, 0.351, 0.524, 0.755, 1.054, 1.435}, us = {0.12, 0.33, 0.57}, ms = {0.74, 0.96};
        java.util.List<double[]> stellen = new java.util.ArrayList<>();
        for (double x : xs) for (double u : us) for (double m : ms) stellen.add(new double[] {u, x, m, 0});
        stellen.add(new double[] {0.57, 0.365, 0.83, 0.25});
        stellen.add(new double[] {0.33, 3.03, 0.87, 0.25});
        int nn = n(1_000_000), platten = 8;
        double fmax = 0, roh = 0, smax = 0;
        java.util.Map<Double, double[]> jeX = new java.util.TreeMap<>();
        String wo6 = "";
        int q = 0;
        for (double[] st : stellen) {
            float[] tf = s.tafel(new double[] {st[0], st[0], st[0]}, wo.n);
            double[] sum = new double[Saeule.NMU], sq = new double[Saeule.NMU];
            for (int i = 0; i < platten; i++) {
                double[][] p = platte(st[0], st[1], st[2], st[3], nn, 100 + 97L * q + i);
                for (int k = 0; k < Saeule.NMU; k++) {
                    sum[k] += p[2][k];
                    sq[k] += p[2][k] * p[2][k];
                }
            }
            q++;
            for (int k : new int[] {1, 6}) {
                double m = sum[k] / platten, sig = Math.sqrt(Math.max(0, sq[k] / platten - m * m) / (platten - 1)) / m;
                double v = Saeule.wert(tf, 0, 1.0, st[1], st[2], Saeule.MU_V[k], 0, 1, 0, st[3]), ab = Math.abs(v / m - 1), ueber = Math.max(0, ab - 3 * sig);
                double[] jx = jeX.computeIfAbsent(st[1], z -> new double[3]);
                jx[0] = Math.max(jx[0], ueber);
                jx[1] = Math.max(jx[1], ab);
                jx[2] = Math.max(jx[2], sig);
                if (st[1] >= 0.05) roh = Math.max(roh, ab);
                smax = Math.max(smax, sig);
                if (st[1] >= 0.05 && ueber >= fmax) {
                    fmax = ueber;
                    wo6 = f("u %.2f x %.3f μ_s %.2f ρ %.2f μ_v %.2f: Tafel %.4f, Platte %.4f ± %.1f %%", st[0], st[1], st[2], st[3], Saeule.MU_V[k], v, m, 100 * sig);
                }
            }
        }
        StringBuilder je = new StringBuilder();
        jeX.forEach((x, v) -> je.append(f("x %.3f: %.1f %% (ohne Abzug %.1f %%, σ %.1f %%); ", x, 100 * v[0], 100 * v[1], 100 * v[2])));
        A.info("Je Tiefe: größte Abweichung über 3 σ", je.toString());
        A.pruefe(fmax <= 0.03, "Tafel (Java wie wasser.frag) gegen die Platte zwischen den Stützstellen, " + stellen.size() + " Stellen mal zwei Blicke, je " + platten + " Platten zu " + nn + " Photonen",
                f("ab x 0,05 (F64 A; darunter Auskunft je Tiefe): größte Abweichung über 3 σ %.1f %% (%s); ohne Abzug größte %.1f %%, größtes σ %.1f %%", 100 * fmax, wo6, 100 * roh, 100 * smax), "≤ 3 % über 3 σ [G]");
    }

    // ------------------------------------------------------------------ P10d, Messung e: Leiter

    /**
     * Messung e (P10d, Prüfstein 6): die Säule der Referenz gegen die Rechnung des Echtzeitbilds aus der Tafel bei
     * derselben Bestrahlung, Stufe für Stufe näher an die Szene: 1 glatt, ohne Luft, nur die Sonne (wie Teil 7);
     * 2 rau (Wind 8 m/s, Cox-Munk), ohne Luft – das Echtzeitbild lässt das Licht durch die glatte Fläche hinein und durch
     * die wirksame Fläche ({@link Flaeche}) hinaus; 3 mit Luft und Himmel (Sicht 30 km), drei Kanäle – die Säule der
     * Referenz als Ganzes weniger dem Lauf ohne Säule (nur die Spiegelung), das Echtzeitbild mit der Sonne durch die Luft
     * und dem Himmelslicht unter der Fläche (∫ L (1 − F) cos θ dω aus {@link Scattering#radiance}, 32 × 16 Richtungen, wie
     * {@code licht.comp}); 4 spektral – die Referenz 360–830 nm, das Echtzeitbild mit der Tafel über das Spektrum (wie
     * im Bild), der Sonne nach der Luft (P10d) und dem Himmel aus {@link Scattering#radianceSpektral}. Tiefes Wasser
     * (100 m), Blick 45° hinab, von der Sonne weg; Sonne 40° und 15°. Auskunft: welche Stufe trägt die Abweichung?
     */
    static void leiter(Saeule s, int N) {
        A.teil("Messung e: Leiter der Säule (P10d, Prüfstein 6)");
        Wasseroptik w = new Wasseroptik();
        double[] kk = w.daempfung(), bb = w.bb(), uk = new double[3];
        for (int c = 0; c < 3; c++) uk[c] = bb[c] / kk[c];
        float[] t3 = s.tafel(uk, w.n), ts = s.tafel(w), fl = com.dan.core.szene.Flaeche.tafel(w.n);
        // Albedo der Tabellen 0,06 (Wasser); -Dp10d.albedo und -Dp10d.stufen nur für Proben
        Atmosphere luft = Atmosphere.forVisibility(30, Double.parseDouble(System.getProperty("p10d.albedo", "0.06")));
        int stufen = Integer.getInteger("p10d.stufen", 4);
        com.dan.core.szene.Scattering sc = new com.dan.core.szene.Scattering(luft);
        double hv = Math.toRadians(45), eta = w.n, wind = 8, s2 = Wasseroptik.coxMunk(wind);
        double cv = Math.sin(hv), cwv = Math.sqrt(1 - (1 - cv * cv) / (eta * eta)), fv = ReferenceCheck.fresnelFormel(cv, cwv, eta);
        double flDurch = com.dan.core.szene.Flaeche.lies(fl, cv, s2)[3];
        double tiefe = 100;
        double[] v = {0, -Math.sin(hv), Math.cos(hv)};                         // von der Sonne weg (cos φ = −1)
        A.info("Wasser", f("n %.3f, Dämpfung %.3f / %.3f / %.3f 1/m, Wind %.0f m/s (σ² %.4f), Durchlass hinaus glatt %.4f, wirksam %.4f; %d Pfade je Lauf", eta, kk[0], kk[1], kk[2], wind, s2, 1 - fv, flDurch, N));
        for (double hsG : new double[] {40, 15}) {
            double hs = Math.toRadians(hsG);
            double cs = Math.sin(hs), cws = Math.sqrt(1 - (1 - cs * cs) / (eta * eta)), fs = ReferenceCheck.fresnelFormel(cs, cws, eta);
            double[] l = {0, cs, -Math.cos(hs)};
            StringBuilder b = new StringBuilder();
            for (int stufe = 1; stufe <= stufen; stufe++) {
                boolean rau = stufe >= 2, mitLuft = stufe >= 3, spektral = stufe == 4;
                Ebenen sz = new Ebenen(-0.5 - tiefe, -0.5, new double[] {0, 0, 0}, 10);
                Referenz ref = new Referenz(sz, luft, w, rau ? wind : 0);
                ref.spektral = spektral;
                if (!rau) ref.sigma2 = 1e-8;
                ref.lichter.clear();
                ref.lichter.add(new Licht(l, Lichtquellen.SONNE_RADIUS / 1.496e11, 0, 1, true));
                long t0 = System.nanoTime();
                long saat = 5100 + 10L * stufe + (long) hsG;
                double[] ganz = lauf(ref, saat, N, mitLuft, spektral), spiegel = new double[6];
                if (mitLuft) {
                    ref.ohneSaeule = true;
                    spiegel = lauf(ref, saat + 7, N, true, spektral);
                }
                double sek = (System.nanoTime() - t0) / 1e9;
                // Echtzeit: Bestrahlung unter der Fläche aus Sonne (waagerecht, durch die glatte Fläche) und Himmel
                double[] E = new double[3], eh = new double[3];
                double[][] eSt = new double[3][Saeule.NMU];          // seit P10d (F104 A): Himmel je Stufe unter der Fläche, wie licht.comp
                double[] e0 = spektral ? E3() : ref.verfolger(1).wl.sonne;     // wie Teil 7: die Sonne der Bahn außerhalb der Luft
                for (int c = 0; c < 3; c++) E[c] = e0[c];
                if (mitLuft) {
                    double[] t = sc.transmittance(Atmosphere.BOTTOM + 0.5, cs);
                    double[] fN = spektral ? Lichtquellen.farbeNachLuft(luft, cs) : new double[] {1, 1, 1};
                    for (int c = 0; c < 3; c++) E[c] *= t[c] * fN[c];
                    for (int a = 0; a < 64; a++)
                        for (int q = 0; q < 32; q++) {
                            double cz = (q + 0.5) / 32, sz2 = Math.sqrt(1 - cz * cz), ph = 2 * Math.PI * (a + 0.5) / 64;
                            double[] d = {sz2 * Math.sin(ph), cz, -sz2 * Math.cos(ph)};
                            double[] L = spektral ? sc.radianceSpektral(0.5, d, l, 1, com.dan.core.szene.Scattering.VIEW_STEPS) : sc.radiance(0.5, d, l, E3(), null, null);
                            // seit P11d (A27): durch die raue Fläche wie licht.comp (Varianz des Winds), glatt nur auf Stufe 1
                            double gew = cz * (rau ? com.dan.core.szene.Flaeche.lies(fl, cz, s2)[com.dan.core.szene.Flaeche.DURCH] : 1 - Wasseroptik.fresnel(cz, eta)) * 2 * Math.PI / (64 * 32);
                            double st = Saeule.himmelStufe(cz, eta);
                            int k0 = Math.min(Saeule.NMU - 2, (int) st);
                            double fk = st - k0;
                            for (int c = 0; c < 3; c++) {
                                eh[c] += L[c] * gew;
                                eSt[c][k0] += L[c] * gew * (1 - fk);
                                eSt[c][k0 + 1] += L[c] * gew * fk;
                            }
                        }
                }
                double hinaus = (rau ? flDurch : 1 - fv) / (eta * eta);
                // seit P10d (F104 A): das Licht der Sonne durch die wirksame Fläche hinein (rau), wie wasser.frag
                double hinein = rau ? com.dan.core.szene.Flaeche.lies(fl, cs, s2)[3] : 1 - fs;
                b.append(f("Stufe %d (%s, %.0f s): ", stufe, new String[] {"", "glatt, ohne Luft", "rau, ohne Luft", "rau, Luft und Himmel", "rau, Luft und Himmel, spektral"}[stufe], sek));
                double yE = 0, yR = 0, yA = 0;
                for (int c = 0; c < 3; c++) {
                    double soll = hinaus * Saeule.wertStufen(spektral ? ts : t3, c, kk[c], tiefe, cws, cwv, -1, E[c] * cs * hinein, eSt[c], 0);
                    double alt = hinaus * Saeule.wert(spektral ? ts : t3, c, kk[c], tiefe, cws, cwv, -1, E[c] * cs * (1 - fs), eh[c], 0);
                    yA += new double[] {0.2126, 0.7152, 0.0722}[c] * alt;
                    double m = ganz[c] - spiegel[c], sig = Math.sqrt(ganz[3 + c] * ganz[3 + c] + spiegel[3 + c] * spiegel[3 + c]);
                    double[] y = {0.2126, 0.7152, 0.0722};
                    yE += y[c] * soll;
                    yR += y[c] * m;
                    b.append(f("%s Echtzeit / Referenz %.3f (Referenz %.4g ± %.1f %%%s) ", "RGB".charAt(c), soll / m, m, 100 * sig / Math.abs(m),
                            mitLuft ? f(", davon Himmel im Echtzeitbild %.0f %%", 100 * hinaus * Saeule.wert(spektral ? ts : t3, c, kk[c], tiefe, cws, cwv, -1, 0, eh[c], 0) / soll) : ""));
                }
                b.append(f("– Leuchtdichte %.3f (wie bis P10c %.3f); ", yE / yR, yA / yR));
                if (stufe >= 2) leiterMax = Math.max(leiterMax, Math.abs(yE / yR - 1));
            }
            A.info(f("Sonne %.0f°, Blick 45° hinab von der Sonne weg", hsG), b.toString());
        }
        A.pruefe(leiterMax <= 0.05, "Messung e nach F104 A: Säule in tiefem Wasser, Echtzeit gegen Referenz, Stufen 2–4 (rau, mit Luft und Himmel, spektral), Sonne 40° und 15°, Leuchtdichte",
                f("größte Abweichung %.1f %%", 100 * leiterMax), "1 ± 0,05 [G]");
    }

    private static double leiterMax;

    private static double[] E3() { return new double[] {Lichtquellen.SONNE_LX, Lichtquellen.SONNE_LX, Lichtquellen.SONNE_LX}; }

    /** Ein Lauf der Referenz in Richtung v (45° hinab, von der Sonne weg): Mittel und σ je Kanal (spektral nach sRGB). */
    static double[] lauf(Referenz ref, long saat, int N, boolean mitLuft, boolean spektral) {
        double hv = Math.toRadians(45);
        double[] v = {0, -Math.sin(hv), Math.cos(hv)};
        return ReferenceCheck.schaetze(ref, saat, N, (pv, out) -> {
            if (!mitLuft) pv.ohneLuft();
            if (spektral) pv.neueWellen();
            java.util.Arrays.fill(pv.wl.nacht, 0);                              // ohne Nachthimmel (das Echtzeitbild rechnet ihn hier nicht)
            double[] L = pv.verfolge(new double[3], v);
            if (spektral) {
                double[] xyz = new double[3];
                pv.wl.sammle(L, xyz);
                double[] rgb = com.dan.core.szene.spektrum.Spektrum.nachRgb(xyz);
                System.arraycopy(rgb, 0, out, 0, 3);
            } else System.arraycopy(L, 0, out, 0, 3);
        });
    }

    // ------------------------------------------------------------------ 7 Referenz gegen die Tafel

    /**
     * Der Pfadverfolger der Referenz (drei Wellenlängen der Kanäle, ohne Luft, glatte Fläche, nur die Sonne 40° hoch)
     * gegen die Tafel mit Fresnel hinein und hinaus: tiefes Wasser mit dem Blick von der Sonne weg und zu ihr hin,
     * 1 m über einem Grund mit der Albedo 0,3.
     */
    static void referenz(Saeule s) {
        A.teil("7 Referenz (Pfadverfolger) gegen die Tafel");
        Atmosphere luft = Atmosphere.forVisibility(30, 0.08);
        Wasseroptik w = new Wasseroptik();
        double[] kk = w.daempfung(), bb = w.bb(), uk = new double[3];
        for (int c = 0; c < 3; c++) uk[c] = bb[c] / kk[c];
        float[] tf = s.tafel(uk, w.n);
        double hs = Math.toRadians(40), hv = Math.toRadians(45), eta = w.n;
        double cs = Math.sin(hs), cws = Math.sqrt(1 - (1 - cs * cs) / (eta * eta)), cv = Math.sin(hv), cwv = Math.sqrt(1 - (1 - cv * cv) / (eta * eta));
        double fs = ReferenceCheck.fresnelFormel(cs, cws, eta), fv = ReferenceCheck.fresnelFormel(cv, cwv, eta);
        double fmax = 0;
        StringBuilder b = new StringBuilder();
        int N = n(2_000_000);
        // {Tiefe (m), Albedo, Blick nach −z (zur Sonne hin, cos φ = +1) oder +z (weg, −1)}
        for (double[] fall : new double[][] {{100, 0, 1}, {100, 0, -1}, {1, 0.3, -1}}) {
            double tiefe = fall[0], rho = fall[1], cphi = fall[2];
            Ebenen sz = new Ebenen(-0.5 - tiefe, -0.5, new double[] {rho, rho, rho}, 10);
            Referenz ref = new Referenz(sz, luft, w, 0);
            ref.spektral = false;
            ref.sigma2 = 1e-8;
            ref.lichter.clear();
            ref.lichter.add(new Licht(new double[] {0, Math.sin(hs), -Math.cos(hs)}, Lichtquellen.SONNE_RADIUS / 1.496e11, 0, 1, true));
            double[] v = {0, -Math.sin(hv), cphi > 0 ? -Math.cos(hv) : Math.cos(hv)};
            double[] e = ReferenceCheck.schaetze(ref, 4300 + (long) tiefe, N, (pv, out) -> {
                pv.ohneLuft();
                double[] L = pv.verfolge(new double[3], v);
                System.arraycopy(L, 0, out, 0, 3);
            });
            double[] E = ref.verfolger(1).wl.sonne;
            b.append(f("%.0f m, ρ %.1f, %s: ", tiefe, rho, cphi > 0 ? "zur Sonne hin" : "von ihr weg"));
            for (int c = 0; c < 3; c++) {
                double soll = (1 - fv) / (eta * eta) * Saeule.wert(tf, c, kk[c], tiefe, cws, cwv, cphi, E[c] * cs * (1 - fs), 0, rho);
                double m = e[c], sig = e[3 + c], ab = Math.abs(m / soll - 1) - 3 * sig / soll;
                fmax = Math.max(fmax, ab);
                b.append(f("%s %.4g gegen %.4g (σ %.1f %%) ", "RGB".charAt(c), m, soll, 100 * sig / m));
            }
            b.append("; ");
        }
        A.pruefe(fmax <= 0.03, "Pfadverfolger (ohne Luft, glatt, Sonne 40°, Blick 45° hinab, Kanäle 680/550/440 nm) gegen die Tafel mit Fresnel, " + N + " Pfade",
                b + f("größte Abweichung über 3 σ hinaus %.1f %%", 100 * Math.max(0, fmax)), "≤ 3 % über 3 σ [G]");
    }
}
