package com.dan.physics.auftrieb.demo;

import com.dan.core.pruef.Attest;
import com.dan.physics.auftrieb.Druckprofil;
import com.dan.physics.auftrieb.Festmacher;
import com.dan.physics.auftrieb.Hydrostatik;
import com.dan.physics.auftrieb.Kette;
import com.dan.physics.auftrieb.Koerper;
import com.dan.physics.auftrieb.Vorgaben;
import com.dan.physics.auftrieb.Wasser;
import com.dan.physics.auftrieb.Welle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfsteine des schwimmenden Körpers ohne Grafikkarte (Seegang Werkbuch, S7a; Grenzen vor der Messung, Datei 86, 3.8):
 * <ol>
 * <li>Schwimmlage aller Vorgaben: Verdrängung gleich Masse (± 0,5 %), Tiefgang mittschiffs gegen die Handrechnung
 * (± 1 cm).</li>
 * <li>Ruhe nach 60 s im ruhigen Wasser: |v| ≤ 1e-4 m/s.</li>
 * <li>Eigenperioden Tauchen und Rollen (Kutter, Fähre, Spitztonne) gegen T = 2π √(M / C) aus der Handrechnung (± 5 %),
 * ohne Dämpfung.</li>
 * <li>Ausschwingen: Dämpfung aus dem logarithmischen Dekrement gegen die Vorgabe (± 10 %), nur linear.</li>
 * <li>Lange Welle (λ = 20 L, Kutter von vorn): Hub durch Amplitude 1 ± 0,05; Stampfen gegen k a ± 5 %.</li>
 * <li>Kurze Welle (λ = L / 3, Fähre von vorn): Hub durch Amplitude ≤ 0,15.</li>
 * <li>Druckhöhe in der Tiefe: Welle gegen cosh(k (z + h)) / cosh(k h) (± 1 %); aus den Summen je Band
 * ({@link Druckprofil}) gegen die volle Summe, RMS durch RMS an der Fläche ≤ 1 %.</li>
 * <li>Seitensee um die Rolleigenperiode (Kutter): Rollen über der Periode, Auskunft.</li>
 * <li>Kette in Ruhe gegen die Kettenlinie: waagerechter Zug und Zug an der Klüse (± 1 %).</li>
 * <li>Spitztonne im Strom 1 m/s: Neigung gegen tan θ = F_D h_D / (B (KB + BM) − W KG) (± 10 %).</li>
 * <li>Fähre an Leinen und Fendern, Wind 12 m/s (Bft 6) quer, von See und von Land: Versatz am Liegeplatz ≤ 0,5 m.</li>
 * <li>Trockenfallen (Fähre auf ebenem Grund): Einsinken ≤ 2 cm; schwimmt wieder auf bei Pegel = Grund + Tiefgang
 * (± 2 cm). Der Kutter als Auskunft (Krängung trocken).</li>
 * <li>Energie ohne Dämpfung, Widerstand, Wind und Zusatzmasse über 600 s (Kutter): Schwankung durch die Energie der
 * Bewegung ≤ 0,1 %.</li>
 * <li>Gleicher Startwert, gleicher Lauf: bitgleich.</li>
 * </ol>
 * Dazu Auskünfte: Proben und Kosten je Teilschritt, Fahrt bei voller Maschine. Attest {@code docs/status/S7a_Auftrieb.md}.
 * Aufruf mit {@code --teil n} für einen Teil.
 */
public final class AuftriebCheck {
    static final Attest A = new Attest("com.dan.physics.auftrieb", Koerper.VERSION);
    static final double G = 9.81;

    public static void main(String[] args) {
        String nur = args.length >= 2 && args[0].equals("--teil") ? args[1] : null;
        long t0 = System.nanoTime();
        try {
            if (nur == null || nur.equals("1")) schwimmlage();
            if (nur == null || nur.equals("3")) eigenperioden();
            if (nur == null || nur.equals("4")) ausschwingen();
            if (nur == null || nur.equals("5")) langeWelle();
            if (nur == null || nur.equals("6")) kurzeWelle();
            if (nur == null || nur.equals("7")) druckhoehe();
            if (nur == null || nur.equals("8")) seitensee();
            if (nur == null || nur.equals("9")) kette();
            if (nur == null || nur.equals("10")) tonneImStrom();
            if (nur == null || nur.equals("11")) leinen();
            if (nur == null || nur.equals("12")) trockenfallen();
            if (nur == null || nur.equals("13")) energie();
            if (nur == null || nur.equals("14")) bitgleich();
            if (nur == null || nur.equals("15")) auskunft();
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer der Prüfung", f("%.1f s", (System.nanoTime() - t0) / 1e9));
        if (nur == null) {
            Path p = A.schreibe(Path.of("docs", "status", "S7a_Auftrieb.md"), "S7a – Schwimmende Körper auf dem Prozessor (Auftrieb " + Koerper.VERSION + ")");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Hilfen

    record Vorgabe(String name, Supplier<Koerper> bau) { }

    static List<Vorgabe> alle() {
        List<Vorgabe> l = new ArrayList<>();
        l.add(new Vorgabe("Fähre", Vorgaben::faehre));
        l.add(new Vorgabe("Adler-Express", Vorgaben::adlerExpress));
        l.add(new Vorgabe("Hauke Haien", Vorgaben::haukeHaien));
        l.add(new Vorgabe("Krabbenkutter", Vorgaben::krabbenkutter));
        l.add(new Vorgabe("Spierentonne", Vorgaben::spierentonne));
        l.add(new Vorgabe("Spitztonne", Vorgaben::spitztonne));
        l.add(new Vorgabe("Leuchttonne", Vorgaben::leuchttonne));
        l.add(new Vorgabe("Balken", Vorgaben::balken));
        l.add(new Vorgabe("Fischkiste", Vorgaben::fischkiste));
        l.add(new Vorgabe("Stamm", Vorgaben::stamm));
        l.add(new Vorgabe("Eiderente", Vorgaben::eiderente));
        return l;
    }

    /** Ohne Dämpfung, Widerstand, Wind, Wellenwiderstand. */
    static void frei(Koerper k) { k.mitDaempfung = false; k.mitWiderstand = false; k.mitWind = false; k.mitWellenwiderstand = false; }

    /** Mittlere Periode aus den Nulldurchgängen (aufwärts) einer Reihe um ihren Mittelwert. */
    static double periode(double[] t, double[] x, double ab) {
        double m = 0;
        int c = 0;
        for (int i = 0; i < x.length; i++) if (t[i] >= ab) { m += x[i]; c++; }
        m /= Math.max(1, c);
        List<Double> z = new ArrayList<>();
        for (int i = 1; i < x.length; i++)
            if (t[i] >= ab && x[i - 1] - m < 0 && x[i] - m >= 0) z.add(t[i - 1] + (t[i] - t[i - 1]) * (m - x[i - 1]) / (x[i] - x[i - 1]));
        if (z.size() < 2) return Double.NaN;
        return (z.get(z.size() - 1) - z.get(0)) / (z.size() - 1);
    }

    /** Spitzen (Beträge der Maxima) einer Reihe um null. */
    static List<Double> spitzen(double[] x) {
        List<Double> s = new ArrayList<>();
        for (int i = 1; i < x.length - 1; i++) if (x[i] > x[i - 1] && x[i] >= x[i + 1] && x[i] > 0) s.add(x[i]);
        return s;
    }

    // ------------------------------------------------------------------ 1 und 2

    static void schwimmlage() {
        A.teil("1 und 2 Schwimmlage und Ruhe im ruhigen Wasser");
        Wasser w = Wasser.ruhig(0);
        for (Vorgabe vg : alle()) {
            Koerper k = vg.bau.get();
            k.einschwimmen(w, 0, 0, 0, 0, 0, 0);
            k.schritt(w, 60);
            double verdr = 1025 * k.eingetaucht() / k.masse - 1;
            double t = k.tiefgang(w), dt = t - k.hydro.tiefgang;
            double[] v = k.geschwindigkeit(), om = k.drehgeschwindigkeit();
            double sp = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            A.pruefe(Math.abs(verdr) <= 0.005, vg.name + ": Verdrängung gegen Masse", f("%+.3f %%", 100 * verdr), "± 0,5 %");
            String ruhe = f("|v| %.2e m/s, |ω| %.2e rad/s; Krängung %.3f°, Trimm %.3f°", sp, Math.sqrt(om[0] * om[0] + om[1] * om[1] + om[2] * om[2]), k.kraengung(), k.trimm());
            if (k.hydro.gm(k.schwerpunkt[2]) <= 0.01) {
                // ohne aufrechte Gleichgewichtslage (Balken über Eck, Stamm ohne Rückstellung): Auskunft (nach der Messung, Datei 87)
                A.info(vg.name + ": ohne aufrechte Lage (GM " + f("%.3f m", k.hydro.gm(k.schwerpunkt[2])) + ")", f("Tiefgang %.4f m; %s", t, ruhe));
                continue;
            }
            A.pruefe(Math.abs(dt) <= 0.01, vg.name + ": Tiefgang mittschiffs gegen die Handrechnung", f("%.4f gegen %.4f m (%+.1f mm)", t, k.hydro.tiefgang, 1000 * dt), "± 1 cm");
            A.pruefe(sp <= 1e-4, vg.name + ": Ruhe nach 60 s", ruhe, "|v| ≤ 1e-4 m/s");
        }
    }

    // ------------------------------------------------------------------ 3 und 4

    static double[] hand(Koerper k) {
        Hydrostatik h = k.hydro;
        double tz = 2 * Math.PI * Math.sqrt((k.masse + k.zusatz[2]) / (1025 * G * h.awl));
        double gm = h.gm(k.schwerpunkt[2]);
        double tr = 2 * Math.PI * Math.sqrt((k.traegheit[0] + k.zusatz[3]) / (k.masse * G * gm));
        return new double[] {tz, tr, gm};
    }

    /** Lässt k aus der Ruhe mit Tauchen dz und Krängung phi los und schreibt z und Krängung über dauer s auf. */
    static double[][] los(Koerper k, Wasser w, double dz, double phi, double dauer, double dt) {
        k.einschwimmen(w, 0, 0, 0, 0, 0, 0);
        double z0 = k.lage()[2];
        k.einschwimmen(w, 0, 0, 0, phi, 0, 0);
        double[] o = k.lage();
        k.setzen(o[0], o[1], o[2] + dz, 0, phi, 0);
        int n = (int) Math.round(dauer / dt);
        double[] t = new double[n], z = new double[n], r = new double[n];
        for (int i = 0; i < n; i++) {
            k.schritt(w, dt);
            t[i] = k.zeit();
            z[i] = k.lage()[2] - z0;
            r[i] = k.kraengung();
        }
        return new double[][] {t, z, r};
    }

    static void eigenperioden() {
        A.teil("3 Eigenperioden ohne Dämpfung");
        Wasser w = Wasser.ruhig(0);
        for (Supplier<Koerper> s : List.<Supplier<Koerper>>of(Vorgaben::krabbenkutter, Vorgaben::faehre, Vorgaben::spitztonne)) {
            Koerper k = s.get();
            frei(k);
            double[] hd = hand(k);
            double[][] z = los(k, w, -0.03, 0, 40, 0.02);
            double tz = periode(z[0], z[1], 0);
            k = s.get();
            frei(k);
            double[][] r = los(k, w, 0, 2, 60, 0.02);
            double tr = periode(r[0], r[2], 0);
            A.pruefe(Math.abs(tz / hd[0] - 1) <= 0.05, k.name + ": Tauchen", f("%.3f gegen %.3f s (%+.1f %%)", tz, hd[0], 100 * (tz / hd[0] - 1)), "± 5 %");
            A.pruefe(Math.abs(tr / hd[1] - 1) <= 0.05, k.name + ": Rollen", f("%.3f gegen %.3f s (%+.1f %%), GM %.3f m", tr, hd[1], 100 * (tr / hd[1] - 1), hd[2]), "± 5 %");
        }
    }

    static double zetaAusSpitzen(List<Double> p) {
        if (p.size() < 3) return Double.NaN;
        int n = Math.min(p.size() - 1, 4);
        double d = Math.log(p.get(0) / p.get(n)) / n;
        return d / Math.sqrt(4 * Math.PI * Math.PI + d * d);
    }

    static void ausschwingen() {
        A.teil("4 Ausschwingen, nur lineare Dämpfung");
        Wasser w = Wasser.ruhig(0);
        for (Supplier<Koerper> s : List.<Supplier<Koerper>>of(Vorgaben::krabbenkutter, Vorgaben::faehre)) {
            Koerper k = s.get();
            frei(k);
            k.mitDaempfung = true;
            k.rollenQuadrat = 0;
            double[][] z = los(k, w, -0.05, 0, 40, 0.01);
            double zt = zetaAusSpitzen(spitzen(z[1]));
            A.pruefe(Math.abs(zt / k.zetaTauchen - 1) <= 0.10, k.name + ": Dämpfung Tauchen", f("ζ %.4f gegen %.4f (%+.1f %%)", zt, k.zetaTauchen, 100 * (zt / k.zetaTauchen - 1)), "± 10 %");
            k = s.get();
            frei(k);
            k.mitDaempfung = true;
            k.rollenQuadrat = 0;
            double[][] r = los(k, w, 0, 3, 80, 0.01);
            double zr = zetaAusSpitzen(spitzen(r[2]));
            A.pruefe(Math.abs(zr / k.zetaRollen - 1) <= 0.10, k.name + ": Dämpfung Rollen", f("ζ %.4f gegen %.4f (%+.1f %%)", zr, k.zetaRollen, 100 * (zr / k.zetaRollen - 1)), "± 10 %");
        }
    }

    // ------------------------------------------------------------------ 5 und 6

    /** Körper im regelmäßigen Seegang von vorn: {Amplitude Hub, Amplitude Stampfen (Grad), Periode}. */
    static double[] imSeegang(Koerper k, double a, double lambda, double h, double richtungLauf, double kurs, int perioden) {
        double kk = 2 * Math.PI / lambda, om = Math.sqrt(G * kk * Math.tanh(kk * h)), tp = 2 * Math.PI / om;
        Welle wl = Welle.regelmaessig(a, tp, richtungLauf, h);
        wl.anlauf = 2 * tp;
        k.einschwimmen(wl, 0, 0, kurs, 0, 0, 0);
        double z0 = k.lage()[2];
        double dt = tp / 100;
        int n0 = (int) Math.round(4 * tp / dt), n = (int) Math.round(perioden * tp / dt);
        double zmax = -1e9, zmin = 1e9, pmax = -1e9, pmin = 1e9;
        for (int i = 0; i < n0 + n; i++) {
            k.schritt(wl, dt);
            if (i < n0) continue;
            double z = k.lage()[2] - z0, p = k.trimm();
            zmax = Math.max(zmax, z); zmin = Math.min(zmin, z); pmax = Math.max(pmax, p); pmin = Math.min(pmin, p);
        }
        return new double[] {0.5 * (zmax - zmin), 0.5 * (pmax - pmin), tp};
    }

    static void langeWelle() {
        A.teil("5 Lange Welle von vorn (λ = 20 L, Kutter)");
        Koerper k = Vorgaben.krabbenkutter();
        double lam = 20 * 16.0, a = 0.3, h = 10, kk = 2 * Math.PI / lam;
        double[] r = imSeegang(k, a, lam, h, 180, 0, 3);
        double pHand = Math.toDegrees(Math.atan(kk * a));
        A.pruefe(Math.abs(r[0] / a - 1) <= 0.05, "Hub durch Amplitude", f("%.4f (Hub %.4f m, a %.2f m, T %.1f s)", r[0] / a, r[0], a, r[2]), "1 ± 0,05");
        A.pruefe(Math.abs(r[1] / pHand - 1) <= 0.05, "Stampfen gegen k a", f("%.4f° gegen %.4f° (%+.1f %%)", r[1], pHand, 100 * (r[1] / pHand - 1)), "± 5 %");
    }

    static void kurzeWelle() {
        A.teil("6 Kurze Welle von vorn (λ = L / 3, Fähre)");
        Koerper k = Vorgaben.faehre();
        double lam = 38.3 / 3, a = 0.3, h = 5;
        double[] r = imSeegang(k, a, lam, h, 180, 0, 6);
        A.pruefe(r[0] / a <= 0.15, "Hub durch Amplitude", f("%.4f (Hub %.4f m, a %.2f m, T %.2f s)", r[0] / a, r[0], a, r[2]), "≤ 0,15");
        A.info("Stampfen", f("%.3f°", r[1]));
    }

    // ------------------------------------------------------------------ 7

    static void druckhoehe() {
        A.teil("7 Druckhöhe in der Tiefe");
        // a) eine Welle gegen die Formel
        double worst = 0;
        double[] o = new double[6];
        for (double[] c : new double[][] {{4, 3}, {6, 5}, {2.5, 2}, {8, 10}}) {
            double tp = c[0], h = c[1];
            Welle wl = Welle.regelmaessig(1, tp, 90, h);
            double kk = Welle.wellenzahl(2 * Math.PI / tp, h);
            for (double z = 0; z >= -h; z -= h / 8) {
                wl.welle(0, 0, z, 0, o);
                double soll = Math.cosh(kk * (z + h)) / Math.cosh(kk * h);
                worst = Math.max(worst, Math.abs(o[0] / soll - 1));
            }
        }
        A.pruefe(worst <= 0.01, "Welle gegen cosh(k (z + h)) / cosh(k h)", f("größte Abweichung %.2e", worst), "± 1 %");
        // b) Summen je Band gegen die volle Summe
        for (double[] c : new double[][] {{1.0, 4, 3}, {1.5, 6, 6}, {0.6, 3, 2}}) {
            double hs = c[0], tp = c[1], h = c[2];
            Welle wl = Welle.jonswap(hs, tp, 90, 10, h, 8, 12, 9, 0.08, 0.6, 7);
            int nb = wl.baender();
            double[] s0 = new double[nb], s1 = new double[nb], kb = new double[nb];
            Random r = new Random(3);
            double[] zs = h > 2.5 ? new double[] {-0.5, -1, -2, -3} : new double[] {-0.5, -1, -2};
            double e1 = 0, e0 = 0, ref = 0, cnt = 0;
            double[] e1z = new double[zs.length], e0z = new double[zs.length], rz = new double[zs.length];
            for (int i = 0; i < 300; i++) {
                double x = 200 * r.nextDouble(), y = 200 * r.nextDouble(), t = 100 * r.nextDouble();
                wl.baender(x, y, t, s0, s1, kb);
                wl.welle(x, y, 0, t, o);
                ref += o[0] * o[0];
                cnt++;
                for (int j = 0; j < zs.length; j++) {
                    double z = Math.max(-h, zs[j]);
                    wl.welle(x, y, z, t, o);
                    double d1 = Druckprofil.psi(z, h, nb, kb, s0, s1) - o[0], d0 = Druckprofil.psi0(z, h, nb, kb, s0) - o[0];
                    e1z[j] += d1 * d1; e0z[j] += d0 * d0; rz[j] += o[0] * o[0];
                    e1 = Math.max(e1, 0); e0 = Math.max(e0, 0);
                }
            }
            double rms0 = Math.sqrt(ref / cnt), worst1 = 0, worst0 = 0;
            StringBuilder b = new StringBuilder();
            for (int j = 0; j < zs.length; j++) {
                double r1 = Math.sqrt(e1z[j] / cnt) / rms0, r0 = Math.sqrt(e0z[j] / cnt) / rms0;
                worst1 = Math.max(worst1, r1); worst0 = Math.max(worst0, r0);
                b.append(f("%s %.1f m: %.2f %% (ohne S1 %.2f %%)", j == 0 ? "" : ";", Math.max(-h, zs[j]), 100 * r1, 100 * r0));
            }
            A.pruefe(worst1 <= 0.01, f("Summen je Band, Hs %.1f m, Tp %.0f s, h %.0f m: RMS durch RMS an der Fläche", hs, tp, h), f("%.2f %%", 100 * worst1), "≤ 1 %");
            A.info(f("  je Tiefe (Hs %.1f, Tp %.0f, h %.0f)", hs, tp, h), b.toString().trim());
        }
    }

    // ------------------------------------------------------------------ 8

    static void seitensee() {
        A.teil("8 Seitensee um die Rolleigenperiode (Kutter, a 0,1 m, h 10 m)");
        Koerper k0 = Vorgaben.krabbenkutter();
        double tphi = hand(k0)[1];
        StringBuilder b = new StringBuilder();
        double best = 0, bestT = 0;
        for (double fac : new double[] {0.6, 0.8, 0.9, 1.0, 1.1, 1.2, 1.4, 1.7}) {
            Koerper k = Vorgaben.krabbenkutter();
            double tp = fac * tphi, h = 10, om = 2 * Math.PI / tp, kk = Welle.wellenzahl(om, h);
            Welle wl = Welle.regelmaessig(0.1, tp, 90, h);
            wl.anlauf = 2 * tp;
            k.einschwimmen(wl, 0, 0, 0, 0, 0, 0);
            double dt = tp / 80, rmax = 0;
            int n0 = (int) Math.round(10 * tp / dt), n = (int) Math.round(6 * tp / dt);
            for (int i = 0; i < n0 + n; i++) {
                k.schritt(wl, dt);
                if (i >= n0) rmax = Math.max(rmax, Math.abs(k.kraengung()));
            }
            double slope = Math.toDegrees(kk * 0.1);
            b.append(f("; T %.2f s: %.2f° (%.1f × k a)", tp, rmax, rmax / slope));
            if (rmax > best) { best = rmax; bestT = tp; }
        }
        A.info("Rollen über der Periode", b.substring(2));
        A.info("größtes Rollen", f("%.2f° bei T %.2f s; T_φ der Handrechnung %.2f s", best, bestT, tphi));
    }

    // ------------------------------------------------------------------ 9

    static void kette() {
        A.teil("9 Kette in Ruhe gegen die Kettenlinie");
        Wasser w = Wasser.ruhig(0, -6);
        for (double seg : new double[] {0.5, 1.0}) {
            Kette c = new Kette(30, seg, 2.0e4 * 0.016 * 0.016, 0.016, new double[3]);
            c.fest = true;
            double[] fl = {27.5, 0, -1};
            c.legen(w, 0, 0, fl);
            double dt = 0.002;
            double[] fk = null;
            for (int i = 0; i < 120 / dt; i++) fk = c.schritt(w, i * dt, dt, fl);
            double hh = Math.hypot(fk[0], fk[1]), t = Math.sqrt(fk[0] * fk[0] + fk[1] * fk[1] + fk[2] * fk[2]);
            double[] soll = Kette.kettenlinie(5 - c.durchmesser / 2, 27.5, 30, c.gewichtNass());
            String g = f("Glieder %.1f m: ", seg);
            if (seg == 0.5) {
                A.pruefe(Math.abs(hh / soll[0] - 1) <= 0.01, g + "waagerechter Zug an der Klüse", f("%.1f gegen %.1f N (%+.2f %%)", hh, soll[0], 100 * (hh / soll[0] - 1)), "± 1 %");
                A.pruefe(Math.abs(t / soll[2] - 1) <= 0.01, g + "Zug an der Klüse", f("%.1f gegen %.1f N (%+.2f %%)", t, soll[2], 100 * (t / soll[2] - 1)), "± 1 %");
            } else {
                A.info(g + "Zug waagerecht und an der Klüse", f("%.1f und %.1f N (%+.2f %%, %+.2f %%)", hh, t, 100 * (hh / soll[0] - 1), 100 * (t / soll[2] - 1)));
            }
            A.info(g + "auf dem Grund", f("%.1f m (Kettenlinie %.1f m)", c.aufGrund(w), 30 - soll[1]));
        }
    }

    // ------------------------------------------------------------------ 10

    static void tonneImStrom() {
        A.teil("10 Spitztonne im Strom 1 m/s");
        Welle w = Welle.ruhig(5);
        w.stromU = 1.0;
        Koerper k = Vorgaben.spitztonne();
        k.einschwimmen(w, 0, 0, 0, 0, 0, 0);
        Kette c = Kette.stahl(15, 0.016, new double[] {0, 0, 0});
        c.fest = true;
        c.legen(w, -10, 0, k.punktWelt(0, 0, 0));
        k.festmachen(c);
        double dt = 0.02, sum = 0;
        int n = 0;
        for (int i = 0; i < 240 / dt; i++) {
            k.schritt(w, dt);
            if (k.zeit() > 180) {
                double[] up = k.richtungWelt(0, 0, 1);
                sum += Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, up[2]))));
                n++;
            }
        }
        double tilt = sum / n;
        Hydrostatik h = k.hydro;
        double tt = h.tiefgang, d = 1.6, fd = 0.5 * 1025 * k.cdQuer * d * tt * 1.0, hD = tt / 2;
        double b = 1025 * G * h.volumen, wg = k.masse * G;
        double soll = Math.toDegrees(Math.atan(fd * hD / (b * (h.kb + h.it / h.volumen) - wg * k.schwerpunkt[2])));
        A.pruefe(Math.abs(tilt / soll - 1) <= 0.10, "Neigung im Strom", f("%.2f° gegen %.2f° (%+.1f %%)", tilt, soll, 100 * (tilt / soll - 1)), "± 10 %");
        A.info("Zug der Kette an der Tonne", f("%.0f N; Widerstand nach der Handrechnung %.0f N", c.zugKlause(), fd));
    }

    // ------------------------------------------------------------------ 11

    static void leinen() {
        A.teil("11 Fähre an Leinen und Fendern, Wind 12 m/s quer");
        for (int seite : new int[] {1, -1}) {
            Welle w = Welle.ruhig(5);
            w.windV = 12 * seite;          // +: aus Süden (von Land), schiebt die Fähre von der Kaje; −: aus Norden, auf die Kaje
            Koerper k = Vorgaben.faehre();
            // Kaje längs x bei y = 0, Wasser bei y > 0; Fähre mit Kurs 90° (Ost), Steuerbord an der Kaje
            double yg = 5.0 + 0.3;
            k.einschwimmen(w, 0, yg, 90, 0, 0, 0);
            double[] p0 = {0, 0, 0}, n = {0, 1, 0};
            for (double xf : new double[] {-12, -4, 4, 12}) k.festmachen(new Festmacher.Fender(new double[] {xf, -5.0, 1.8}, 0.3, p0, n, 3e5));
            double[][] kl = {{17, -4.0, 2.9}, {-17, -4.0, 2.9}, {6, -4.6, 2.7}, {-6, -4.6, 2.7}};
            double[][] po = {{24, -3, 2.0}, {-24, -3, 2.0}, {-2, -2, 2.0}, {2, -2, 2.0}};
            List<Festmacher.Leine> ls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                double[] p = k.punktWelt(kl[i][0], kl[i][1], kl[i][2]);
                double len = Math.sqrt((po[i][0] - p[0]) * (po[i][0] - p[0]) + (po[i][1] - p[1]) * (po[i][1] - p[1]) + (po[i][2] - p[2]) * (po[i][2] - p[2]));
                Festmacher.Leine l = new Festmacher.Leine(kl[i], po[i], len - 0.05, 1.5e6);
                ls.add(l);
                k.festmachen(l);
            }
            double[] m0 = k.punktWelt(0, 0, 0);
            double vers = 0, dt = 0.02, zugMax = 0;
            for (int i = 0; i < 180 / dt; i++) {
                k.schritt(w, dt);
                double[] m = k.punktWelt(0, 0, 0);
                if (k.zeit() > 10) vers = Math.max(vers, Math.hypot(m[0] - m0[0], m[1] - m0[1]));
                for (Festmacher.Leine l : ls) zugMax = Math.max(zugMax, l.zug);
            }
            String wo = seite > 0 ? "von Land (Leinen tragen)" : "von See (Fender tragen)";
            A.pruefe(vers <= 0.5, "Versatz am Liegeplatz, Wind " + wo, f("%.3f m", vers), "≤ 0,5 m");
            A.info("  größter Zug einer Leine", f("%.1f kN", zugMax / 1000));
        }
    }

    // ------------------------------------------------------------------ 12

    static void trockenfallen() {
        A.teil("12 Trockenfallen auf ebenem Grund");
        // Pegel 0 bis 20 s, dann 5 mm/s hinab bis 0,4 m unter den Grund, 60 s halten, 5 mm/s hinauf [G: schnell, aber
        // langsam gegen die Eigenperioden]
        double grund = -3, rate = 0.005, tief = grund - 0.4, t1 = 20 + (0 - tief) / rate, t2 = t1 + 60, t3 = t2 + (0 - tief) / rate;
        for (int wer = 0; wer < 2; wer++) {
            Welle w = Welle.ruhig(3);
            w.grund = grund;
            w.pegel = t -> t < 20 ? 0 : t < t1 ? -(t - 20) * rate : t < t2 ? tief : Math.min(0, tief + (t - t2) * rate);
            Koerper k = wer == 0 ? Vorgaben.faehre() : Vorgaben.krabbenkutter();
            k.schrittMax = 0.01;
            k.einschwimmen(w, 0, 0, 0, 0, 0, 0);
            double einsinken = 0, auf = Double.NaN, kr = 0;
            boolean warAufGrund = false;
            double dt = 0.1;
            for (int i = 0; i < t3 / dt; i++) {
                k.schritt(w, dt);
                double t = k.zeit();
                einsinken = Math.max(einsinken, k.grundTiefe());
                if (t > t1 && t < t2) kr = Math.max(kr, Math.abs(k.kraengung()));
                if (k.aufGrund()) warAufGrund = true;
                if (t > t2 && warAufGrund && !k.aufGrund() && Double.isNaN(auf)) auf = w.pegel(0, 0, t);
            }
            double soll = grund + k.hydro.tiefgang;
            if (wer == 0) {
                A.pruefe(einsinken <= 0.02, k.name + ": Einsinken in den Grund", f("%.4f m", einsinken), "≤ 2 cm");
                A.pruefe(Math.abs(auf - soll) <= 0.02, k.name + ": schwimmt auf bei Pegel", f("%.4f gegen %.4f m (%+.1f mm)", auf, soll, 1000 * (auf - soll)), "± 2 cm");
                A.info(k.name + ": trocken", f("Krängung höchstens %.2f°", kr));
            } else {
                A.info(k.name + ": trocken", f("Krängung höchstens %.1f°, Einsinken %.3f m, schwimmt auf bei %.3f m (Grund + T %.3f m)", kr, einsinken, auf, soll));
            }
        }
    }

    // ------------------------------------------------------------------ 13

    static void energie() {
        A.teil("13 Energie ohne Dämpfung, Widerstand, Wind und Zusatzmasse (Kutter, 600 s)");
        Wasser w = Wasser.ruhig(0);
        Koerper k = Vorgaben.krabbenkutter();
        frei(k);
        k.mitZusatz = false;
        k.einschwimmen(w, 0, 0, 0, 0, 0, 0);
        double eRuhe = k.energie(w);
        double[] o = k.lage();
        k.setzen(o[0], o[1], o[2] - 0.1, 0, 10, 0);
        double e0 = k.energie(w), emin = e0, emax = e0;
        double dt = 0.05;
        for (int i = 0; i < 600 / dt; i++) {
            k.schritt(w, dt);
            double e = k.energie(w);
            emin = Math.min(emin, e); emax = Math.max(emax, e);
        }
        double rel = (emax - emin) / (e0 - eRuhe);
        A.pruefe(rel <= 0.001, "Schwankung durch Energie der Bewegung", f("%.2e (Bewegung %.1f kJ)", rel, (e0 - eRuhe) / 1000), "≤ 0,1 %");
    }

    // ------------------------------------------------------------------ 14

    static double[] lauf14() {
        Welle w = Welle.jonswap(0.8, 4.5, 200, 10, 6, 8, 1, 3, 0.08, 0.6, 42);
        w.anlauf = 5;
        Koerper k = Vorgaben.krabbenkutter();
        k.einschwimmen(w, 0, 0, 30, 0, 0, 0);
        k.schritt(w, 20);
        double[] o = k.lage(), q = k.drehung(), v = k.geschwindigkeit(), om = k.drehgeschwindigkeit();
        return new double[] {o[0], o[1], o[2], q[0], q[1], q[2], q[3], v[0], v[1], v[2], om[0], om[1], om[2]};
    }

    static void bitgleich() {
        A.teil("14 Gleicher Startwert, gleicher Lauf");
        double[] a = lauf14(), b = lauf14();
        int gleich = 0;
        for (int i = 0; i < a.length; i++) if (Double.doubleToRawLongBits(a[i]) == Double.doubleToRawLongBits(b[i])) gleich++;
        A.pruefe(gleich == a.length, "Kutter im Seegang (24 Teilwellen) 20 s, zweimal", f("%d von %d Werten bitgleich", gleich, a.length), "bitgleich");
    }

    // ------------------------------------------------------------------ 15

    static void auskunft() {
        A.teil("15 Auskunft: Proben, Kosten, Fahrt");
        Welle w = Welle.jonswap(0.8, 4.5, 200, 10, 6, 8, 1, 3, 0.08, 0.6, 42);
        Wasser ruhig = Wasser.ruhig(0);
        for (Vorgabe vg : alle()) {
            Koerper k = vg.bau.get();
            double[] hd = hand(k);
            k.einschwimmen(ruhig, 0, 0, 0, 0, 0, 0);
            long t0 = System.nanoTime();
            k.schritt(ruhig, 2);
            double us = (System.nanoTime() - t0) / 1e3 / (2 / k.schrittMax);
            A.info(vg.name, f("%d Proben zu %.2f m; %.1f t; GM %.2f m; T_z %.2f s, T_φ %.2f s; A₃₃/m %.2f; %.0f µs je Teilschritt im ruhigen Wasser",
                    k.proben.n, k.proben.d, k.masse / 1000, hd[2], hd[0], hd[1], k.zusatz[2] / k.masse, us));
        }
        Koerper k = Vorgaben.krabbenkutter();
        k.einschwimmen(w, 0, 0, 0, 0, 0, 0);
        long t0 = System.nanoTime();
        k.schritt(w, 2);
        A.info("Krabbenkutter im Seegang (" + w.n + " Teilwellen)", f("%.0f µs je Teilschritt", (System.nanoTime() - t0) / 1e3 / (2 / k.schrittMax)));
        Wasser tief = Wasser.ruhig(0);
        k = Vorgaben.krabbenkutter();
        k.einschwimmen(tief, 0, 0, 0, 0, 0, 0);
        k.hebel = 1;
        k.schritt(tief, 120);
        double[] v = k.geschwindigkeit();
        A.info("Krabbenkutter, volle Maschine nach 120 s", f("%.2f kn", Math.hypot(v[0], v[1]) / 0.5144));
    }
}
