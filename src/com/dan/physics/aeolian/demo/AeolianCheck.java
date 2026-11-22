package com.dan.physics.aeolian.demo;

import com.dan.core.pruef.Attest;
import com.dan.physics.aeolian.Aeolian;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.dan.core.pruef.Attest.f;

/**
 * Die Prüfsteine von aeolian für P10c (Werkbuch HalligHooge Teil A, 12.11, Datei 41), ohne Gebiet: 1 Schwelle trocken,
 * 2 Schwelle feucht, 3 Fluss, 4 gleichförmiges Feld, 5 Kanten zu Wasser und zu nassem Sand, 6 Massenbilanz,
 * 7 Richtungstreue, 8 fester Boden und Schlick, 9 Rechenzeit. Prüfstein 10 (Schichtenvertrag) prüft der Bau
 * ({@code check-packages}). Die Sollwerte zu 1–3 sind außerhalb gerechnet (Python, 18.11.2026) und stehen hier als
 * Zahlen. {@code --teil n}: nur Teil n (dann ohne Attest).
 */
public final class AeolianCheck {
    static final Attest A = new Attest("com.dan.physics.aeolian", Aeolian.VERSION);

    public static void main(String[] args) {
        List<String> a = List.of(args);
        int ti = a.indexOf("--teil");
        int nur = ti >= 0 ? Integer.parseInt(a.get(ti + 1)) : 0;
        long t0 = System.nanoTime();
        try {
            if (nur == 0 || nur == 1) schwelle();
            if (nur == 0 || nur == 2) feucht();
            if (nur == 0 || nur == 3) fluss();
            if (nur == 0 || nur == 4) gleichfoermig();
            if (nur == 0 || nur == 5) kanten();
            if (nur == 0 || nur == 6) bilanz();
            if (nur == 0 || nur == 7) richtung();
            if (nur == 0 || nur == 8) festerBoden();
            if (nur == 0 || nur == 9) zeiten();
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (nur == 0) {
            A.teil("Offen");
            A.info("Prüfstein 10 (Schichtenvertrag, Feldnamen)", "check-packages im Bau");
            A.info("Prüfsteine 11–16 (im Gebiet)", "lauf.demo.AeolianLaufCheck, Attest P10c_AeolianLauf.md");
            A.info("Rechenzeit", f("%.1f s", (System.nanoTime() - t0) * 1e-9));
            Path p = A.schreibe(Path.of("docs", "status", "P10c_Aeolian.md"), "P10c – AeolianCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static double rel(double wert, double soll) { return Math.abs(wert - soll) / Math.abs(soll); }

    // ------------------------------------------------------------------ 1 Schwelle trocken

    static void schwelle() {
        A.teil("1 Schwelle für trockenen Sand (Bagnold, A = 0,1)");
        Aeolian ae = new Aeolian(1, 1, 20, 20);
        double[][] soll = {{0.25e-3, 0.22796595140502893}, {0.175e-3, 0.19072999895139728}, {0.44e-3, 0.30243101031474934}};
        double g = 0;
        StringBuilder b = new StringBuilder();
        for (double[] s : soll) {
            g = Math.max(g, rel(ae.schwelle(s[0]), s[1]));
            b.append(f("%.3f mm: %.4f m/s (%.1f m/s in 10 m); ", s[0] * 1e3, ae.schwelle(s[0]), ae.wind(ae.schwelle(s[0]))));
        }
        A.hoechstens("u*t gegen die Handrechnung, größte Abweichung über 0,25, 0,175 und 0,44 mm", g * 100, 0.1, "%");
        A.info("Schwellen (mit z0 = 1 mm)", b.toString());
        A.hoechstens("u* aus dem Wind und zurück", rel(ae.wind(ae.ustern(8.0)), 8.0) * 100, 1e-10, "%");
    }

    // ------------------------------------------------------------------ 2 Schwelle feucht

    static void feucht() {
        A.teil("2 Schwelle für feuchten Sand (Belly 1964)");
        Aeolian ae = new Aeolian(1, 1, 20, 20);
        double[][] soll = {{1, 1.8}, {2, 1.9806179973983888}, {4, 2.1612359947967774}};
        double g = 0;
        for (double[] s : soll) g = Math.max(g, rel(Aeolian.feucht(s[0]), s[1]));
        A.hoechstens("Faktor bei 1, 2, 4 Gewichtsprozent gegen die Handrechnung", g * 100, 0.1, "%");
        double eins = Math.pow(10, -0.8 / 0.6);
        A.pruefe(Aeolian.feucht(0) == 1 && Aeolian.feucht(0.5 * eins) == 1 && Aeolian.feucht(eins * 1.01) > 1, "Kein Einfluss, wo Bellys Formel unter 1 fiele (unter " + f("%.3f", eins) + " %)",
                f("0 %%: %.3f; %.3f %%: %.3f; %.3f %%: %.4f", Aeolian.feucht(0), 0.5 * eins, Aeolian.feucht(0.5 * eins), 1.01 * eins, Aeolian.feucht(1.01 * eins)), "1; 1; > 1");
        A.hoechstens("Gewichtsprozent bei Sättigung 1 und 40 % Poren gegen 25,157", rel(ae.prozent(1, 0.4), 25.157232704402517) * 100, 0.1, "%");
        // über wMax nimmt der Wind nichts auf: die Zelle ist „zu nass“, aus der Ruhe kommt kein Fluss
        ae.u[0] = 12;
        ae.wet[0] = 4.5 / ae.prozent(1, 0.4);
        int art = ae.art(0);
        ae.schritt(300);
        boolean nichts = art == 2 && ae.dz[0] == 0 && ae.qx[0] == 0;
        ae.wet[0] = 3.5 / ae.prozent(1, 0.4);
        ae.schritt(300);
        A.pruefe(nichts && ae.art(0) == 1 && ae.dz[0] < 0, "Bei 4,5 % nimmt der Wind (12 m/s) nichts auf, bei 3,5 % schon", f("Art %d, dann Art %d mit Δz %.2e m", art, ae.art(0), ae.dz[0]), "nichts; Abtrag");
        // Bellys zweite Form für seinen Sand von 0,44 mm: U*t = 17 log10 w + 51 cm/s
        StringBuilder b = new StringBuilder();
        for (double w : new double[] {0.5, 1, 2, 4})
            b.append(f("%.1f %%: %.1f cm/s (Belly %.1f); ", w, 100 * ae.schwelle(0.44e-3) * Aeolian.feucht(w), 17 * Math.log10(w) + 51));
        A.info("Auskunft: Schwelle für 0,44 mm gegen Bellys zweite Form (17 log₁₀ w + 51 cm/s)", b.toString());
    }

    // ------------------------------------------------------------------ 3 Fluss

    static void fluss() {
        A.teil("3 Gesättigter Fluss (Kawamura; zum Vergleich Lettau und Lettau)");
        Aeolian ae = new Aeolian(1, 1, 20, 20);
        double d = 0.25e-3, ut = ae.schwelle(d);
        double[][] soll = {{0.3, 0.007112715822939013, 0.005534726210852134}, {0.4, 0.024031031074393992, 0.02349904435446088}, {0.6, 0.09034277889309886, 0.11434073970579384}};
        double gk = 0, gl = 0;
        for (double[] s : soll) gk = Math.max(gk, rel(ae.fluss(s[0], ut, d), s[1]));
        ae.lettau = true;
        for (double[] s : soll) gl = Math.max(gl, rel(ae.fluss(s[0], ut, d), s[2]));
        ae.lettau = false;
        A.hoechstens("Kawamura bei u* = 0,3, 0,4, 0,6 m/s (0,25 mm) gegen die Handrechnung", gk * 100, 0.1, "%");
        A.hoechstens("Lettau und Lettau ebenso", gl * 100, 0.1, "%");
        A.pruefe(ae.fluss(ut, ut, d) == 0 && ae.fluss(0.9 * ut, ut, d) == 0 && ae.fluss(0, ut, d) == 0, "An und unter der Schwelle", "0", "genau 0");
        double us = 1e4 * ut;
        A.hoechstens("Weit über der Schwelle (u* = 10⁴ u*t) gegen C ρ u*³ / g", rel(ae.fluss(us, ut, d), ae.c * ae.rho / ae.g * us * us * us) * 100, 0.1, "%");
        StringBuilder b = new StringBuilder();
        for (double[] p : new double[][] {{0.31, 0.17e-3}, {0.50, 0.25e-3}, {0.71, 0.44e-3}}) {
            double k = ae.fluss(p[0], ae.schwelle(p[1]), p[1]);
            b.append(f("u* %.2f m/s, %.2f mm: %.1f g/(m s), mit C = 0,70: %.1f; ", p[0], p[1] * 1e3, k * 1e3, k * 1e3 * 0.70 / 2.78));
        }
        A.info("Auskunft: Fluss im Messbereich von Sherman u. a. (2013; dort bis rund 30 g/(m s) gemessen, die Formel rund dreifach zu hoch)", b.toString());
    }

    // ------------------------------------------------------------------ 4 gleichförmiges Feld

    static Aeolian feld(int nx, int ny, double grad, double wind) {
        Aeolian ae = new Aeolian(nx, ny, 20, 20);
        double r = Math.toRadians(grad);                     // wohin der Wind weht, von Nord über Ost
        Arrays.fill(ae.u, wind * Math.sin(r));
        Arrays.fill(ae.v, wind * Math.cos(r));
        if (grad % 90 == 0) {                                // genau längs der Achsen
            if (grad % 180 == 0) Arrays.fill(ae.u, 0); else Arrays.fill(ae.v, 0);
        }
        return ae;
    }

    static void gleichfoermig() {
        A.teil("4 Gleichförmiges trockenes Feld");
        double innen = 0, kante = 0;
        StringBuilder b = new StringBuilder();
        for (double grad : new double[] {90, 0, 270, 45, 200}) {
            Aeolian ae = feld(40, 30, grad, 9);
            for (int n = 0; n < 12; n++) ae.schritt(300);
            double q = ae.fluss(ae.ustern(9), ae.schwelle(2e-4), 2e-4), r = Math.toRadians(grad), cx = Math.sin(r), cy = Math.cos(r);
            if (grad % 180 == 0) cx = 0;
            else if (grad % 90 == 0) cy = 0;
            for (int j = 2; j < 28; j++) for (int i = 2; i < 38; i++) innen = Math.max(innen, Math.abs(ae.dz[i + 40 * j]));
            // die Zellen am Rand im Luv, abseits der Ecken: Zufluss nur über eine Kante weniger
            double rhoB = ae.rhoS * (1 - 0.4), soll, ist;
            if (Math.abs(cx) > 1e-12 && Math.abs(cy) < 1e-12) {
                soll = -q * 3600 / (rhoB * 20);
                ist = ae.dz[(cx > 0 ? 0 : 39) + 40 * 15];
            } else if (Math.abs(cx) < 1e-12) {
                soll = -q * 3600 / (rhoB * 20);
                ist = ae.dz[20 + 40 * (cy > 0 ? 0 : 29)];
            } else {
                soll = -q * Math.abs(cx) * 3600 / (rhoB * 20);            // am Westrand oder Ostrand fehlt der Zufluss über die Kante quer zu x
                ist = ae.dz[(cx > 0 ? 0 : 39) + 40 * 15];
            }
            kante = Math.max(kante, rel(ist, soll));
            b.append(f("nach %.0f°: Rand %.3f mm (Soll %.3f); ", grad, ist * 1e3, soll * 1e3));
        }
        A.hoechstens("Änderung der Höhe im Inneren nach einer Stunde bei 9 m/s, fünf Richtungen", innen, 1e-12, "m");
        A.hoechstens("Abtrag der Zelle am Rand im Luv gegen q Δt / (ρs (1 − θ) Δx)", kante * 100, 0.1, "%");
        A.info("Rand im Luv nach einer Stunde", b.toString());
    }

    // ------------------------------------------------------------------ 5 Kanten

    static void kanten() {
        A.teil("5 Kante zu Wasser und zu nassem Sand");
        // trocken (Spalten 0–19) → Wasser (ab 20), Wind nach Ost
        Aeolian ae = feld(40, 5, 90, 9);
        for (int j = 0; j < 5; j++) for (int i = 20; i < 40; i++) ae.h[i + 40 * j] = 0.5;
        ae.schritt(300);
        double q = ae.fluss(ae.ustern(9), ae.schwelle(2e-4), 2e-4), rhoB = ae.rhoS * 0.6, soll = q * 300 / (rhoB * 20), rest = 0;
        for (int i = 21; i < 40; i++) rest = Math.max(rest, Math.abs(ae.dz[i + 40 * 2]));
        A.hoechstens("Auftrag in der ersten Wasserzelle gegen q Δt / (ρs (1 − θ) Δx) (" + f("%.3f mm in 300 s", soll * 1e3) + ")", rel(ae.dz[20 + 40 * 2], soll) * 100, 0.1, "%");
        A.pruefe(rest == 0 && ae.insWasser > 0 && rel(ae.insWasser, q * 300 * 5 * 20) < 1e-12, "Dahinter nichts; alles liegt im Wasser", f("größte Änderung %.1e m; %.1f kg im Wasser", rest, ae.insWasser), "genau 0");
        // trocken → zu nass
        ae = feld(60, 5, 90, 9);
        for (int j = 0; j < 5; j++) for (int i = 20; i < 60; i++) ae.wet[i + 60 * j] = 1;
        ae.schritt(300);
        double gq = 0, gz = 0;
        for (int n = 1; n <= 30; n++) {
            double qn = q * Math.exp(-n * 20 / ae.lHaft), zn = q * (Math.exp(-(n - 1) * 20 / ae.lHaft) - Math.exp(-n * 20 / ae.lHaft)) * 300 / (rhoB * 20);
            gq = Math.max(gq, rel(ae.qx[19 + n + 60 * 2], qn));
            gz = Math.max(gz, rel(ae.dz[19 + n + 60 * 2], zn));
        }
        A.hoechstens("Fluss über nassem Sand gegen q exp(−x / 100 m), 30 Zellen", gq * 100, 0.1, "%");
        A.hoechstens("Auftrag dort gegen die Rechnung", gz * 100, 0.1, "%");
        A.info("Über nassem Sand", f("nach 100 m noch %.1f %% des Flusses, nach 600 m %.2f %%", 100 * ae.qx[24 + 60 * 2] / q, 100 * ae.qx[49 + 60 * 2] / q));
    }

    // ------------------------------------------------------------------ 6 Massenbilanz

    static Aeolian bunt(int nx, int ny, long seed) {
        Aeolian ae = new Aeolian(nx, ny, 20, 20);
        Random z = new Random(seed);
        for (int k = 0; k < ae.nc; k++) {
            ae.wet[k] = z.nextDouble() < 0.3 ? z.nextDouble() : 0.3 * z.nextDouble();
            ae.h[k] = z.nextDouble() < 0.15 ? 0.2 : 0;
            ae.fest[k] = z.nextDouble() < 0.1 ? 1 : 0;
            ae.schlick[k] = z.nextDouble() < 0.1 ? 0.3 : 0;
            ae.d50[k] = 1.5e-4 + 1.5e-4 * z.nextDouble();
            ae.poros[k] = 0.35 + 0.1 * z.nextDouble();
        }
        return ae;
    }

    static void bilanz() {
        A.teil("6 Massenbilanz");
        double g = 0, g2 = 0, ausflug = 0, bewegt = 0;
        long mittel = 0;
        for (int r = 0; r < 8; r++) {
            Aeolian ae = bunt(64, 48, 11 + r);
            Random z = new Random(5 + r);
            double grad = 45 * r, w = Math.toRadians(grad);
            for (int n = 0; n < 20; n++) {
                for (int k = 0; k < ae.nc; k++) {
                    double wind = 6 + 6 * z.nextDouble();                       // der Wind schwankt je Zelle in der Stärke, nicht in der Richtung
                    ae.u[k] = grad % 180 == 0 ? 0 : wind * Math.sin(w);
                    ae.v[k] = grad % 180 == 90 ? 0 : wind * Math.cos(w);
                }
                ae.schritt(300);
            }
            g = Math.max(g, Math.abs(ae.rest()) / ae.abtrag);
            double m = 0;
            for (int k = 0; k < ae.nc; k++) m += ae.dz[k] * ae.rhoS * (1 - ae.poros[k]) * 400;
            g2 = Math.max(g2, Math.abs(m + ae.ausflug) / ae.abtrag);
            ausflug += ae.ausflug;
            bewegt += ae.abtrag;
            mittel += ae.gemittelt;
        }
        A.hoechstens("Auftrag − Abtrag + Ausflug, bezogen auf den Abtrag: Nässe, Wasser, fester Boden und Korn zufällig, acht Richtungen, je 20 Schritte", g, 1e-12, "");
        A.hoechstens("Dasselbe aus der Höhe: Σ Δz ρs (1 − θ) A + Ausflug", g2, 1e-12, "");
        A.info("Bewegt", f("%.0f t abgetragen, %.0f t über den Rand; Schritte mit gemittelter Richtung %d", bewegt / 1e3, ausflug / 1e3, mittel));
        // wechselnde Vorzeichen im Feld: die mittlere Richtung gilt, die Bilanz schließt weiter
        Aeolian ae = bunt(64, 48, 99);
        Random z = new Random(3);
        for (int n = 0; n < 20; n++) {
            for (int k = 0; k < ae.nc; k++) {
                ae.u[k] = 8 + 2 * z.nextGaussian();
                ae.v[k] = 1.5 * z.nextGaussian();
            }
            ae.schritt(300);
        }
        A.pruefe(ae.gemittelt == 20 && Math.abs(ae.rest()) / ae.abtrag <= 1e-12, "Wechselt das Vorzeichen einer Komponente im Feld, gilt die mittlere Richtung; die Bilanz schließt",
                f("%d von 20 Schritten gemittelt, Rest %.1e", ae.gemittelt, Math.abs(ae.rest()) / ae.abtrag), "20; ≤ 1e-12");
    }

    // ------------------------------------------------------------------ 7 Richtungstreue

    static void richtung() {
        A.teil("7 Richtungstreue");
        int n = 121, m = n / 2;
        double[] masse = new double[8], winkel = new double[8];
        StringBuilder b = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            double grad = 45 * r;
            Aeolian ae = feld(n, n, grad, 9);
            Arrays.fill(ae.wet, 1);                                         // nasse Ebene
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++)
                    if (Math.hypot(i - m, j - m) <= 15.2) ae.wet[i + n * j] = 0;      // runder Fleck trockenen Sands, 600 m
            ae.schritt(300);
            double ax = 0, ay = 0, as = 0, ex = 0, ey = 0, es = 0;
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    double z = ae.dz[i + n * j];
                    if (z > 0) { ax += z * i; ay += z * j; as += z; }
                    else if (z < 0) { ex -= z * i; ey -= z * j; es -= z; }
                }
            masse[r] = ae.abtrag;
            double wx = ax / as - ex / es, wy = ay / as - ey / es;
            winkel[r] = (Math.toDegrees(Math.atan2(wx, wy)) + 360) % 360;
            b.append(f("%.0f°: %.1f t, Auftrag nach %.1f°; ", grad, masse[r] / 1e3, winkel[r]));
        }
        double mittel = Arrays.stream(masse).average().orElse(0), gm = 0, gw = 0;
        for (int r = 0; r < 8; r++) {
            gm = Math.max(gm, rel(masse[r], mittel));
            double dw = Math.abs(winkel[r] - 45 * r);
            gw = Math.max(gw, Math.min(dw, 360 - dw));
        }
        A.hoechstens("Bewegte Masse: größte Abweichung vom Mittel der acht Richtungen (runder Fleck von 600 m auf nasser Ebene)", gm * 100, 1.0, "%");
        A.hoechstens("Richtung vom Schwerpunkt des Abtrags zum Schwerpunkt des Auftrags gegen den Wind", gw, 3.0, "°");
        A.info("Je Richtung", b.toString());
    }

    // ------------------------------------------------------------------ 8 fester Boden und Schlick

    static void festerBoden() {
        A.teil("8 Fester Boden und Schlick");
        Aeolian ae = feld(60, 5, 90, 11);
        for (int j = 0; j < 5; j++)
            for (int i = 20; i < 60; i++) {
                if (i < 40) ae.fest[i + 60 * j] = 1;
                else ae.schlick[i + 60 * j] = 0.3;
            }
        for (int s = 0; s < 12; s++) ae.schritt(300);
        double q = ae.fluss(ae.ustern(11), ae.schwelle(2e-4), 2e-4), kleinst = 0, gq = 0;
        for (int i = 20; i < 60; i++) kleinst = Math.min(kleinst, ae.dz[i + 60 * 2]);
        for (int k = 1; k <= 10; k++) gq = Math.max(gq, rel(ae.qx[19 + k + 60 * 2], q * Math.exp(-k * 20 / ae.lFang)));
        A.pruefe(kleinst == 0, "Abtrag auf festem Boden und auf Schlick (Wind 11 m/s, eine Stunde)", f("%.1e m", kleinst), "genau 0");
        A.hoechstens("Fluss über festem Boden gegen q exp(−x / 20 m), 10 Zellen", gq * 100, 0.1, "%");
        // fester Boden allein: nichts fliegt
        ae = feld(20, 5, 90, 15);
        Arrays.fill(ae.fest, 1);
        ae.schritt(300);
        double g = 0;
        for (int k = 0; k < ae.nc; k++) g = Math.max(g, Math.max(Math.abs(ae.dz[k]), Math.abs(ae.qx[k])));
        A.pruefe(g == 0 && ae.aktiv == 0, "Fester Boden ohne Sand im Luv", f("%.1e", g), "kein Fluss, keine Änderung");
    }

    // ------------------------------------------------------------------ 9 Rechenzeit

    static void zeiten() {
        A.teil("9 Rechenzeit");
        Aeolian ae = bunt(700, 400, 7);
        Arrays.fill(ae.u, 7);
        Arrays.fill(ae.v, -5);
        for (int n = 0; n < 5; n++) ae.schritt(300);
        long t0 = System.nanoTime();
        int schritte = 20;
        for (int n = 0; n < schritte; n++) ae.schritt(300);
        double s = (System.nanoTime() - t0) * 1e-9 / schritte;
        A.hoechstens("Ein Kopplungsintervall für 280 000 Zellen (ein Faden)", s, 0.1, "s");
    }
}
