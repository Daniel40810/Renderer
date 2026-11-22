package com.dan.render.reference.gpu.demo;

import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.pruef.Attest;
import com.dan.core.pruef.Bildvergleich;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Lichtquellen;
import com.dan.core.szene.Wasseroptik;
import com.dan.core.szene.spektrum.Spektrum;
import com.dan.render.reference.Ebenen;
import com.dan.render.reference.Licht;
import com.dan.render.reference.Pfadverfolger;
import com.dan.render.reference.Phasen;
import com.dan.render.reference.Referenz;
import com.dan.render.reference.gpu.ReferenzKarte;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung der Referenz Stufe 2 ohne Gebiet (P11a, Werkbuch HalligHooge Teil A, 12.12, Prüfsteine 1–8): die Prüfsteine
 * von Stufe 1 auf der Karte (1 Ofentest, 2 Beer–Lambert, 3 einfacher Rayleigh-Himmel, 4 Tafeln, 5 Grund unter rauem
 * Wasser), dann Stufe 1 = Stufe 2 an fünf Szenen aus {@link Ebenen} und einem kleinen Bild (6), die Geschwindigkeit (7)
 * und Speicher und Validierung (8). Attest {@code docs/status/P11a_ReferenzKarte.md}. {@code --schnell}: ein Zehntel der
 * Pfade (Bau, Validierung), ohne Attest.
 */
public final class ReferenceKarteCheck {
    private static final Attest A = new Attest("com.dan.render.reference.gpu", ReferenzKarte.VERSION);
    static double mass = 1;
    static final int FAEDEN = 65_536;
    /** Pfade und Zeit (s) von Stufe 1 und 2 für Prüfstein 7. */
    static long pfade1, pfade2, nanSumme, abgebrochenSumme;
    static double laengsterAufruf;
    static double zeit1, zeit2;

    public static void main(String[] args) {
        if (List.of(args).contains("--tempo")) {
            System.exit(tempoAllein());
            return;
        }
        boolean schnell = List.of(args).contains("--schnell");
        if (schnell) mass = 0.1;
        System.out.println("ReferenceKarteCheck – com.dan.render.reference.gpu " + ReferenzKarte.VERSION + " (Werkbuch HalligHooge Teil A, P11a)");
        System.out.println("         " + Runtime.getRuntime().availableProcessors() + " Kerne");
        try (Geraet g = new Geraet()) {
            A.info("Gerät", g.name + " (" + g.treiber + "), Validierung " + (g.validierung ? "an" : "aus"));
            Konto konto = g.konto("ReferenceKarteCheck", 256L << 20);
            try (ReferenzKarte rk = new ReferenzKarte(g, konto)) {
                rk.laut = true;
                tafeln(rk);
                ofen(rk);
                beerLambert(rk);
                rayleigh(rk);
                grundRau(rk);
                gleich(rk);
                tempo();
                A.teil("Wache der Pfade");
                A.pruefe(nanSumme == 0 && abgebrochenSumme == 0, "Pfade mit NaN und von der Wache abgebrochene (über 2 Mio. Schritte), alle Rechnungen auf der Karte",
                        f("%d mit NaN, %d abgebrochen; längster Aufruf der Karte %.0f ms", nanSumme, abgebrochenSumme, laengsterAufruf), "0 [G]");
            }
            A.teil("8 Speicher, Validierung (Prüfstein 8)");
            A.pruefe(konto.belegt() == 0, "Speicherkonto nach allen Teilen", konto + ", Spitze " + Geraet.mb(konto.spitze()), "0 belegt");
            if (g.validierung) A.pruefe(g.validierungsFehler == 0, "Validierungsschicht", g.validierungsFehler + " Fehler" + (g.meldungen.isEmpty() ? "" : "; erste: " + g.meldungen.get(0)), "0 Fehler");
            else A.info("Validierungsschicht", "aus (der Lauf mit --schnell schaltet sie ein)");
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (!schnell) {
            Path p = A.schreibe(Path.of("docs", "status", "P11a_ReferenzKarte.md"), "P11a – ReferenceKarteCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Schätzen auf der Karte und die Wache mitzählen. */
    static double[] schaetze(ReferenzKarte rk, int modus, int faeden, int n, long saat) {
        double[] e = rk.schaetze(modus, faeden, n, saat);
        bilanz(rk);
        return e;
    }

    static void bilanz(ReferenzKarte rk) {
        nanSumme += rk.nan;
        abgebrochenSumme += rk.abgebrochen;
        laengsterAufruf = Math.max(laengsterAufruf, rk.groessterAufrufMs);
    }

    /**
     * Nur die Geschwindigkeit (Lauf A3, nach Lauf A2: 477 Pfade/s): Ofentest, Himmel über der Kugel, Säule; je Szene
     * Stufe 2 und Stufe 1 auf allen Kernen. Exit 0 ab dem Zehnfachen (Prüfstein 7), sonst 3: das Skript hält dann an.
     */
    static int tempoAllein() {
        System.out.println("ReferenceKarteCheck --tempo – com.dan.render.reference.gpu " + ReferenzKarte.VERSION + " (P11a, Lauf A3)");
        try (Geraet g = new Geraet()) {
            A.info("Gerät", g.name + " (" + g.treiber + ")");
            Konto konto = g.konto("ReferenceKarteCheck", 256L << 20);
            try (ReferenzKarte rk = new ReferenzKarte(g, konto)) {
                rk.laut = true;
                A.teil("Geschwindigkeit, Stufe 2 gegen Stufe 1");
                Ebenen sz = new Ebenen(-12, -10, new double[] {1, 1, 1}, 0);
                Referenz ofen = new Referenz(sz, Atmosphere.forVisibility(30, 0.08), new Wasseroptik(), 0);
                ofen.sigma2 = 1e-6;
                double[] d = norm(new double[] {0, -0.5, -0.866});
                rk.schalter().uebernimm(ofen).ofen().strahl(new double[3], d);
                double[] e2 = schaetze(rk, ReferenzKarte.MODUS_RICHTUNG, FAEDEN, 4, 11);
                double r2 = rk.pfade / rk.sekundenKarte;
                long t0 = System.nanoTime();
                double[] e1 = IntStream.range(0, 16).parallel().mapToObj(t -> {
                    Pfadverfolger pv = ofen.verfolger(500 + t);
                    pv.ofen();
                    double s = 0;
                    for (int i = 0; i < 16_384; i++) {
                        pv.neueWellen();
                        double[] L = pv.verfolge(new double[3], d);
                        s += (L[0] + L[1] + L[2] + L[3]) / 4;
                    }
                    return new double[] {s};
                }).reduce(new double[] {0}, (x, y) -> new double[] {x[0] + y[0]});
                double r1 = 16 * 16_384 / ((System.nanoTime() - t0) / 1e9);
                A.info("Ofentest spektral, 30° unter der Kimm", f("Stufe 2 %.4f, %.3g Pfade/s (längster Aufruf %.0f ms; %s); Stufe 1 %.4f, %.3g Pfade/s; Verhältnis %.1f",
                        e2[6], r2, rk.groessterAufrufMs, rk.protokoll, e1[0] / (16 * 16_384), r1, r2 / r1));
                List<Fall> fs = faelle();
                for (int k : new int[] {2, 4}) {
                    Fall fa = fs.get(k);
                    double[] s1 = stufe1(fa.ref, fa.d, 400_000, 9000 + 31L * k);
                    rk.schalter().uebernimm(fa.ref).strahl(new double[3], fa.d);
                    double[] s2 = schaetze(rk, ReferenzKarte.MODUS_RICHTUNG, FAEDEN, 8, 7000 + 31L * k);
                    pfade2 += rk.pfade;
                    zeit2 += rk.sekundenKarte;
                    A.info(fa.name, f("Y Stufe 2 / Stufe 1 = %.4f; Stufe 2 %.3g Pfade/s (längster Aufruf %.0f ms)", s2[1] / s1[1], rk.pfade / rk.sekundenKarte, rk.groessterAufrufMs));
                }
                tempo();
                A.pruefe(nanSumme == 0 && abgebrochenSumme == 0, "Pfade mit NaN und von der Wache abgebrochene", nanSumme + " mit NaN, " + abgebrochenSumme + " abgebrochen", "0 [G]");
            }
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "P11a_Tempo.md"), "P11a – ReferenceKarteCheck --tempo");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        return A.verstoesse() == 0 ? 0 : 3;
    }

    static int n(double v) { return (int) Math.max(1, Math.round(v * mass)); }

    /** Pfade je Faden für insgesamt etwa v Pfade. */
    static int jeFaden(double v) { return Math.max(1, (int) Math.round(v * mass / FAEDEN)); }

    public static double[] norm(double[] d) {
        double n = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        return new double[] {d[0] / n, d[1] / n, d[2] / n};
    }

    public static double[] richtung(double hoeheGrad, double azGrad) {
        double h = Math.toRadians(hoeheGrad), a = Math.toRadians(azGrad);
        return new double[] {Math.sin(a) * Math.cos(h), Math.sin(h), -Math.cos(a) * Math.cos(h)};
    }

    static String zahl(double v) { return Math.abs(v) >= 10 ? f("%.2f", v) : f("%.5f", v); }

    static String rate(double pfade, double s) { return f("%.3g Pfade/s", pfade / Math.max(1e-9, s)); }

    // ------------------------------------------------------------------ 4 Tafeln

    static void tafeln(ReferenzKarte rk) {
        A.teil("4 Spektrum und Tafeln auf der Karte (Prüfstein 4)");
        rk.schalter();
        Arrays.fill(rk.w, ReferenzKarte.W_ALB, ReferenzKarte.W_ALB + 3, 0);
        rk.w[ReferenzKarte.W_ALB] = 0.3f;
        rk.w[ReferenzKarte.W_ALB + 1] = 0.5f;
        rk.w[ReferenzKarte.W_ALB + 2] = 0.7f;
        int n = 4096;
        double[] r = rk.rechne(ReferenzKarte.MODUS_TAFELN, n, 1, 1, 1, 1);
        bilanz(rk);
        double[] g = Spektrum.gewichte(new double[] {0.3, 0.5, 0.7});
        String[] namen = {"x̄", "ȳ", "z̄", "Sonne", "reines Wasser", "Ozon", "Rayleigh (Formel)", "Reflexion zu RGB (0,3; 0,5; 0,7)"};
        double[] fmax = new double[8], gross = new double[8];
        for (int i = 0; i < n; i++) {
            double l = r[i * ReferenzKarte.STRIDE + 8];
            double[] soll = {Spektrum.xBar(l), Spektrum.yBar(l), Spektrum.zBar(l), Spektrum.sonne(l), Spektrum.wasser(l), Spektrum.OZON_DICHTE * Spektrum.ozon(l),
                    Spektrum.rayleigh(l, Atmosphere.RAYLEIGH_H), Spektrum.reflexion(g, l)};
            for (int k = 0; k < 8; k++) {
                fmax[k] = Math.max(fmax[k], Math.abs(r[i * ReferenzKarte.STRIDE + k] - soll[k]));
                gross[k] = Math.max(gross[k], Math.abs(soll[k]));
            }
        }
        double schlimmst = 0;
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < 8; k++) {
            double rel = fmax[k] / gross[k];
            schlimmst = Math.max(schlimmst, rel);
            b.append(f("%s %.1e; ", namen[k], rel));
        }
        A.pruefe(schlimmst <= 1e-5, "Tafeln des Spektrums auf der Karte gegen Java an " + n + " Wellenlängen (größte Abweichung durch größten Wert)", b.toString(), "≤ 1 · 10⁻⁵ [G]");
        // Fournier-Forand: Mittel von 1/p über gezogene Richtungen ist 4π, wenn Ziehen und Auswerten dieselbe Verteilung sind
        int k = jeFaden(32_000_000);
        double[] e = schaetze(rk, ReferenzKarte.MODUS_FF, FAEDEN, k, 4711);
        double soll = 4 * Math.PI, sig = e[3];
        A.pruefe(Math.abs(e[0] - soll) <= 3 * sig && sig / soll <= 1e-3,
                "Fournier-Forand auf der Karte: Mittel von 1/p über gezogene Richtungen gegen 4π (Ziehen in 1 − cos θ, F und 1 − F; Dichte je Stufe), " + (long) FAEDEN * k + " Richtungen",
                f("%.5f gegen %.5f, Abweichung %.1e, σ relativ %.1e; Mittel von 1 − cos θ %.5f (Java: b_b/b der Tafel %.4f)", e[0], soll, e[0] / soll - 1, sig / soll, e[1], Phasen.FF_BB),
                "≤ 3 σ, σ ≤ 1 · 10⁻³ relativ [G]");
    }

    // ------------------------------------------------------------------ 1 Ofentest

    static void ofen(ReferenzKarte rk) {
        A.teil("1 Ofentest auf der Karte (Prüfstein 1)");
        Ebenen sz = new Ebenen(-12, -10, new double[] {1, 1, 1}, 0);
        Referenz ref = new Referenz(sz, Atmosphere.forVisibility(30, 0.08), new Wasseroptik(), 0);
        ref.sigma2 = 1e-6;
        double[][] richtungen = {{0, -0.5, -0.866}, {0, -0.866, -0.5}, {0, 0.087, -0.996}, {0, 0.5, -0.866}, {0, -0.999, 0.02}};
        String[] namen = {"30° unter der Kimm", "60° unter", "5° über", "30° über", "senkrecht nach unten"};
        for (boolean sp : new boolean[] {true, false}) {
            ref.spektral = sp;
            for (int q = 0; q < richtungen.length; q++) {
                rk.schalter().uebernimm(ref).ofen().strahl(new double[3], norm(richtungen[q]));
                int k = jeFaden(1_000_000);
                double[] e = schaetze(rk, ReferenzKarte.MODUS_RICHTUNG, FAEDEN, k, 1000L * q + (sp ? 0 : 99));
                double mean = e[6], sig = e[7];
                A.pruefe(Math.abs(mean - 1) <= 3 * sig + 1e-12 || Math.abs(mean - 1) <= 1e-4,
                        "Ofentest " + (sp ? "spektral" : "drei Kanäle") + ", " + namen[q] + " (Luft, glattes Wasser 2 m über dem Boden mit Albedo 1)",
                        f("%.5f ± %.5f (%d Pfade, %s)", mean, sig, (long) FAEDEN * k, rate(rk.pfade, rk.sekundenKarte)), "1 ± 3 σ [G]");
                A.pruefe(3 * sig <= 1e-3, "Ofentest: Streuung des Mittels klein genug für 10⁻³", f("3 σ = %.1e", 3 * sig), "3 σ ≤ 1 · 10⁻³ [G]");
            }
        }
    }

    // ------------------------------------------------------------------ 2 Beer–Lambert

    static void gegen(String groesse, double[] e, double[] soll) {
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

    static void beerLambert(ReferenzKarte rk) {
        A.teil("2 Beer–Lambert auf der Karte (Prüfstein 2)");
        Wasseroptik w = new Wasseroptik();
        w.schwebstoff = 0;
        Ebenen sz = new Ebenen(-1000, Double.NaN, new double[] {0, 0, 0}, 0);
        Referenz ref = new Referenz(sz, Atmosphere.forVisibility(30, 0.08), w, 0);
        ref.spektral = false;
        double s = 2.0;
        double[] soll = new double[3];
        for (int k = 0; k < 3; k++) soll[k] = Math.exp(-Wasseroptik.POPE_FRY[k] * s);
        rk.schalter().uebernimm(ref).weg(s);
        int k = jeFaden(32_000_000);
        double[] e = schaetze(rk, ReferenzKarte.MODUS_WASSERFLUG, FAEDEN, k, 500);
        gegen("Spektrales Tracking durch 2 m reines Wasser auf der Karte (drei Kanäle zugleich), " + (long) FAEDEN * k + " Pfade, " + rate(rk.pfade, rk.sekundenKarte), e, soll);
    }

    // ------------------------------------------------------------------ 3 Rayleigh-Himmel

    static void rayleigh(ReferenzKarte rk) {
        A.teil("3 Einfacher Rayleigh-Himmel auf der Karte (Prüfstein 3)");
        Ebenen sz = new Ebenen(Double.NaN, Double.NaN, new double[] {0, 0, 0}, 0);
        Referenz ref = new Referenz(sz, Atmosphere.forVisibility(30, 0.08), new Wasseroptik(), 0);
        ref.spektral = false;
        double hs = Math.toRadians(30);
        double[] sonne = {0, Math.sin(hs), Math.cos(hs)};
        ref.lichter.clear();
        ref.lichter.add(new Licht(sonne, Math.sin(Math.toRadians(0.2666)), 0, 1, true));
        double[][] richt = {{0, 1, 0}, norm(new double[] {0, Math.sin(Math.toRadians(30)), -Math.cos(Math.toRadians(30))}),
                norm(new double[] {Math.cos(Math.toRadians(10)), Math.sin(Math.toRadians(10)), 0})};
        String[] namen = {"Zenit", "30° hoch nach Norden (gegen die Sonne)", "10° hoch nach Osten"};
        for (int q = 0; q < richt.length; q++) {
            rk.schalter().uebernimm(ref).nurRayleigh().strahl(new double[3], richt[q]);
            int k = jeFaden(4_000_000);
            double[] e = schaetze(rk, ReferenzKarte.MODUS_EINFACH, FAEDEN, k, 3000 + 100L * q);
            gegen("Rayleigh, Einfachstreuung (Stoßschätzer) auf der Karte, " + namen[q] + ", Sonne 30° im Süden, " + (long) FAEDEN * k + " Pfade, " + rate(rk.pfade, rk.sekundenKarte),
                    e, rayleighQuadratur(richt[q], sonne));
        }
    }

    /** Einfachstreuung der Rayleigh-Luft mit Quadratur in double (wie ReferenceCheck von Stufe 1). */
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

    // ------------------------------------------------------------------ 5 Grund unter rauem Wasser

    static void grundRau(ReferenzKarte rk) {
        A.teil("5 Grund unter rauem Wasser auf der Karte (Prüfstein 5)");
        Wasseroptik w = new Wasseroptik();
        w.schwebstoff = 0;
        double tiefe = 5, rho = 0.1, hs = Math.toRadians(40), hv = Math.toRadians(45), s2 = 0.03;
        Ebenen sz = new Ebenen(-0.5 - tiefe, -0.5, new double[] {rho, rho, rho}, 10);
        Referenz ref = new Referenz(sz, Atmosphere.forVisibility(30, 0.08), w, 0);
        ref.spektral = false;
        ref.sigma2 = s2;
        double[] ls = {0, Math.sin(hs), -Math.cos(hs)}, v = {0, -Math.sin(hv), Math.cos(hv)}, oben = {0, 1, 0}, zumAuge = {0, Math.sin(hv), -Math.cos(hv)};
        ref.lichter.clear();
        ref.lichter.add(new Licht(ls, Lichtquellen.SONNE_RADIUS / 1.496e11, 0, 1, true));
        Pfadverfolger p0 = ref.verfolger(1);
        double a = p0.wl.aWasser[0], E = p0.wl.sonne[0], eta = w.n;
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
        rk.schalter().uebernimm(ref).ohneLuft().strahl(new double[3], v);
        int k = jeFaden(24_000_000);
        double[] e = schaetze(rk, ReferenzKarte.MODUS_RICHTUNG, FAEDEN, k, 4200);
        double sig = e[3] / soll;
        A.pruefe(Math.abs(e[0] / soll - 1) <= 3 * sig && sig <= 1e-3,
                "Grund (Albedo 0,1) unter 5 m rauem (σ² 0,03), klarem Wasser ohne Luft, Sonne 40°, Blick 45° hinab, rot: Karte gegen zwei Integrale mit derselben BSDF (3 000 × 3 000), "
                        + (long) FAEDEN * k + " Pfade, " + rate(rk.pfade, rk.sekundenKarte),
                f("%.6g gegen %.6g cd/m², Abweichung %.1e, σ relativ %.1e", e[0], soll, e[0] / soll - 1, sig), "≤ 3 σ, σ ≤ 1 · 10⁻³ relativ [G]");
    }

    // ------------------------------------------------------------------ 6 Stufe 1 = Stufe 2

    /** Eine Szene für Prüfstein 6: Referenz von Stufe 1 und fester Strahl vom Auge. */
    record Fall(String name, Referenz ref, double[] d) { }

    public static Licht sonne(double hoehe, double az) {
        return new Licht(richtung(hoehe, az), Lichtquellen.SONNE_RADIUS / 1.496e11, Lichtquellen.RAND_U, 1, true);
    }

    static List<Fall> faelle() {
        Atmosphere luft = Atmosphere.forVisibility(30, 0.08);
        Ebenen e1 = new Ebenen(-12, -10, new double[] {0.2, 0.2, 0.2}, 10);
        Referenz r1 = new Referenz(e1, luft, new Wasseroptik(), 0);
        r1.lichter.add(sonne(40, 180));
        Ebenen e2 = new Ebenen(-12, -10, new double[] {0.2, 0.2, 0.2}, 10);
        Referenz r2 = new Referenz(e2, luft, new Wasseroptik(), 8);
        r2.lichter.add(sonne(15, 0));
        Ebenen e3 = new Ebenen(-10, Double.NaN, new double[] {0.08, 0.08, 0.08}, 10);
        e3.kugel = true;
        Referenz r3 = new Referenz(e3, luft, new Wasseroptik(), 0);
        r3.lichter.add(sonne(40, 0));
        Ebenen e4 = new Ebenen(-10, Double.NaN, new double[] {0.08, 0.08, 0.08}, 10);
        e4.kugel = true;
        Referenz r4 = new Referenz(e4, luft, new Wasseroptik(), 0);
        r4.lichter.add(sonne(3, 0));
        Ebenen e5 = new Ebenen(-5.5, -0.5, new double[] {0.1, 0.1, 0.1}, 10);
        Referenz r5 = new Referenz(e5, luft, new Wasseroptik(), 3);
        r5.lichter.add(sonne(40, 180));
        return List.of(
                new Fall("Sonne 40° im Rücken, glattes Wasser 2 m über Grund (Albedo 0,2), Blick 20° hinab", r1, richtung(-20, 0)),
                new Fall("Sonne 15°, raues Wasser (8 m/s), Blick 5° hinab, 30° neben der Sonne", r2, richtung(-5, 30)),
                new Fall("Himmel über der Kugel (Albedo 0,08), Sonne 40°, Blick 10° hoch, 90° neben der Sonne", r3, richtung(10, 90)),
                new Fall("Dämmerung: Kugel, Sonne 3°, Blick 5° hoch, 45° neben der Sonne", r4, richtung(5, 45)),
                new Fall("Säule: 30 mg/l, 5 m Wasser über Grund (Albedo 0,1), 3 m/s, Sonne 40° im Rücken, Blick 45° hinab", r5, richtung(-45, 0)));
    }

    /** Stufe 1: Mittel und Streuung des Mittels je Kanal (XYZ) über n Pfade in 16 Teilen. */
    static double[] stufe1(Referenz ref, double[] d, int n, long saat) {
        long t0 = System.nanoTime();
        double[][] r = IntStream.range(0, 16).parallel().mapToObj(t -> {
            Pfadverfolger pv = ref.verfolger(saat + t);
            double[] acc = new double[6], v = new double[3];
            for (int i = 0; i < n / 16; i++) {
                pv.neueWellen();
                double[] L = pv.verfolge(new double[3], d);
                Arrays.fill(v, 0);
                pv.wl.sammle(L, v);
                for (int k = 0; k < 3; k++) {
                    acc[k] += v[k];
                    acc[3 + k] += v[k] * v[k];
                }
            }
            return acc;
        }).toArray(double[][]::new);
        int nn = (n / 16) * 16;
        zeit1 += (System.nanoTime() - t0) / 1e9;
        pfade1 += nn;
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

    static void gleich(ReferenzKarte rk) {
        A.teil("6 Stufe 1 = Stufe 2 an Szenen aus Ebenen (Prüfstein 6)");
        int k = 0;
        for (Fall fa : faelle()) {
            // die Säule unter der Sonne streut in beiden Stufen stark (Variationskoeffizient 18–21, Befund P11a): mehr Pfade
            boolean saeule = k == 0 || k == 4;
            double[] e1 = stufe1(fa.ref, fa.d, n(saeule ? 4_000_000 : 1_000_000), 9000 + 31L * k);
            rk.schalter().uebernimm(fa.ref).strahl(new double[3], fa.d);
            int jf = jeFaden(saeule ? 16_000_000 : 4_000_000);
            double[] e2 = schaetze(rk, ReferenzKarte.MODUS_RICHTUNG, FAEDEN, jf, 7000 + 31L * k);
            pfade2 += rk.pfade;
            zeit2 += rk.sekundenKarte;
            boolean ok = true;
            StringBuilder b = new StringBuilder();
            String[] kn = {"X", "Y", "Z"};
            for (int c = 0; c < 3; c++) {
                double v = e2[c] / e1[c], s = Math.sqrt(e1[3 + c] * e1[3 + c] + e2[3 + c] * e2[3 + c]) / Math.abs(e1[c]);
                double grenze = Math.max(3 * s, 0.005);
                ok &= Math.abs(v - 1) <= grenze;
                b.append(f("%s %s / %s = %.4f (± %.4f); ", kn[c], zahl(e2[c]), zahl(e1[c]), v, grenze));
            }
            A.pruefe(ok, "Stufe 2 durch Stufe 1, spektral: " + fa.name, b.toString(), "1 ± max(3 σ; 0,005) [G]");
            k++;
        }
        bild(rk, faelle().get(1));
    }

    /** Ein kleines Bild der ersten Szene mit beiden Stufen: ΔE₀₀ nach derselben Belichtung. */
    static void bild(ReferenzKarte rk, Fall fa) {
        int bw = 96, bh = 54, n = Math.max(2, n(64));
        Blick blick = new Blick(0, 0, 10, 30, -2, 40);                    // halb Himmel, halb raues Wasser, 30° neben der Sonne
        Referenz ref = fa.ref;
        ref.saat = 77;
        long t0 = System.nanoTime();
        double[][][] h1 = ref.bild(blick, bw, bh, n);
        zeit1 += (System.nanoTime() - t0) / 1e9;
        pfade1 += (long) bw * bh * n;
        rk.schalter().uebernimm(ref);
        double[][][] h2 = rk.bild(blick, bw, bh, n, 77);
        bilanz(rk);
        pfade2 += rk.pfade;
        zeit2 += rk.sekundenKarte;
        double[][] m1 = Referenz.mittel(h1), m2 = Referenz.mittel(h2);
        double y = 0;
        for (int i = 0; i < bw * bh; i++) y += 0.2126 * m1[0][i] + 0.7152 * m1[1][i] + 0.0722 * m1[2][i];
        double k = 1 / (2 * y / (bw * bh));
        double r1 = dE(h1[0], h1[1], k), r2 = dE(h2[0], h2[1], k), d = dE(m1, m2, k), grenze = Math.sqrt(r1 * r1 + r2 * r2) + 0.2;
        double l1 = 0, l2 = 0;
        for (int i = 0; i < bw * bh; i++) {
            l1 += 0.2126 * m1[0][i] + 0.7152 * m1[1][i] + 0.0722 * m1[2][i];
            l2 += 0.2126 * m2[0][i] + 0.7152 * m2[1][i] + 0.0722 * m2[2][i];
        }
        A.pruefe(d <= grenze, "Stufe 1 = Stufe 2 im Bild (" + bw + " × " + bh + ", " + n + " Pfade je Bildpunkt): " + fa.name,
                f("ΔE₀₀ im Mittel %.2f; Rauschen (Hälften) Stufe 1 %.2f, Stufe 2 %.2f; Leuchtdichte Stufe 2 durch Stufe 1 %.4f", d, r1, r2, l2 / l1),
                "ΔE₀₀ ≤ √(R₁² + R₂²) + 0,2 [G]");
    }

    /** Mittlerer ΔE₀₀ zweier linearer Bilder nach der Belichtung k (Wert mal k, über 1 abgeschnitten, sRGB, Lab). */
    static double dE(double[][] a, double[][] b, double k) {
        double s = 0;
        int n = a[0].length;
        double[] la = new double[3], lb = new double[3];
        for (int i = 0; i < n; i++) {
            lab(a[0][i] * k, a[1][i] * k, a[2][i] * k, la);
            lab(b[0][i] * k, b[1][i] * k, b[2][i] * k, lb);
            s += Bildvergleich.deltaE00(la[0], la[1], la[2], lb[0], lb[1], lb[2]);
        }
        return s / n;
    }

    /** Lineares sRGB (0 … 1, abgeschnitten) → L*a*b* (D65), wie {@link Bildvergleich#lab} ohne Rundung auf 8 Bit. */
    static void lab(double r, double g, double b, double[] z) {
        r = Math.max(0, Math.min(1, r));
        g = Math.max(0, Math.min(1, g));
        b = Math.max(0, Math.min(1, b));
        double x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047, y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b,
                zz = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883;
        double fx = fl(x), fy = fl(y), fz = fl(zz);
        z[0] = 116 * fy - 16;
        z[1] = 500 * (fx - fy);
        z[2] = 200 * (fy - fz);
    }

    static double fl(double t) { return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116; }

    // ------------------------------------------------------------------ 7 Geschwindigkeit

    static void tempo() {
        A.teil("7 Geschwindigkeit (Prüfstein 7)");
        double r1 = pfade1 / Math.max(1e-9, zeit1), r2 = pfade2 / Math.max(1e-9, zeit2);
        A.pruefe(r2 >= 10 * r1, "Pfade/s auf der Karte gegen Stufe 1 auf allen Kernen, dieselben Szenen (Prüfstein 6)",
                f("Stufe 1 %.3g Pfade/s (%d Pfade in %.1f s); Stufe 2 %.3g Pfade/s (%d Pfade in %.1f s auf der Karte); Verhältnis %.1f", r1, pfade1, zeit1, r2, pfade2, zeit2, r2 / r1),
                "≥ 10-fach [G]; sonst Halt und Frage");
    }
}
