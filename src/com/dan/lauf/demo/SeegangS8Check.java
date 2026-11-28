package com.dan.lauf.demo;

import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Katzenpfoten;
import com.dan.core.szene.Profilpuffer;
import com.dan.core.szene.Wasseroptik;
import com.dan.core.szene.Wasserpunkte;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Bordkamera;
import com.dan.lauf.Buehne;
import com.dan.lauf.Bugschwall;
import com.dan.lauf.Flaechenwart;
import com.dan.lauf.Flotte;
import com.dan.lauf.Linsenglas;
import com.dan.lauf.Schwimmerangabe;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.physics.auftrieb.Bandwasser;
import com.dan.physics.auftrieb.Bugschlag;
import com.dan.physics.auftrieb.Koerper;
import com.dan.physics.auftrieb.Vorgaben;
import com.dan.physics.auftrieb.Welle;
import com.dan.render.frame.Linse;
import com.dan.render.schwimmer.Gischtbild;
import com.dan.render.seegang.Koerpersuche;
import com.dan.render.seegang.SeegangKarte;
import com.dan.render.water.Katzenprobe;
import com.dan.physics.gischt.Buendel;
import com.dan.physics.scheibe.Scheibe;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung S8a und S8b (Seegang Werkbuch, Datei 104): Gischt am Bug, Tropfen auf der Linse, Katzenpfoten, Wellen für
 * Treibgut und Enten, Ausweichen.
 * <ol>
 * <li><b>Ohne Karte</b>: Katzenpfoten (Prüfstein 37: Drift der Flecken, Cox–Munk beim Wind am Ort, volle Rauigkeit über
 * 6 m/s); Gischt (Prüfstein 35: geschlossene Lösung gegen Runge–Kutta, Schwall nur bei Bugschlag); Linse (Prüfstein 36: ein
 * Tropfen läuft ab A_CRIT); Ausweichen (SF163: die Ente taucht vor dem Kutter ab, das Treibgut schiebt der Rumpf weg).</li>
 * <li><b>Karte, Prüfszene</b> (die Szene von {@code SchwimmerCheck}): die Ente mit dem Punkt ihres Trupps gegen die Referenz
 * (Prüfstein 40); Tropfen der Gischt auf der Karte gegen den Prozessor (35); das Bild durch einen Tropfen auf der Karte gegen
 * die Brechung an der Kugelkappe in double (36).</li>
 * <li><b>In der Anwendung</b> ({@code Hooge.szenen}): Katzenpfoten im Bild am ruhigen Tag, Karte gegen Prozessor und die Böe
 * aus ERA5 (37); an Bord des Kutters im Sturm: Bugschläge, Gischt, Tropfen auf der Linse, Kosten (41).</li>
 * </ol>
 * <pre>  SeegangS8Check --szenen ..\HalligHooge\src\szenen\Hooge.szenen [--teil n]</pre>
 */
public final class SeegangS8Check {
    static final Attest A = new Attest("Seegang S8a/S8b: Gischt, Linse, Katzenpfoten, Wellen für Treibgut und Enten", Bugschwall.VERSION);

    public static void main(String[] args) {
        File szenen = null;
        int breite = 1280, hoehe = 720;
        java.util.Set<String> nur = new java.util.HashSet<>();
        for (int i = 0; i < args.length; i++)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[++i]);
                case "--teil" -> nur.add(args[++i]);
                default -> { }
            }
        System.out.println("SeegangS8Check – S8a, S8b (Seegang Werkbuch 104)");
        long t0 = System.nanoTime();
        try {
            if (nur.isEmpty() || nur.contains("1")) SchwimmerCheck.zeit("1", SeegangS8Check::ohneKarte);
            if (nur.isEmpty() || nur.contains("2")) SchwimmerCheck.zeit("2", SeegangS8Check::pruefszene);
            if (nur.isEmpty() || nur.contains("3")) {
                if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
                File sz = szenen;
                SchwimmerCheck.zeit("3", () -> anwendung(sz, breite, hoehe));
            }
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer der Prüfung", f("%.1f s", (System.nanoTime() - t0) * 1e-9));
        String datei = nur.isEmpty() ? "S8ab_Seegang.md" : "S8ab_Seegang_teil" + String.join("", new java.util.TreeSet<>(nur)) + ".md";
        Path pa = A.schreibe(Path.of("docs", "status", datei), "S8a/S8b – Gischt, Linse, Katzenpfoten, Wellen für Treibgut und Enten (Bugschwall " + Bugschwall.VERSION
                + ", Linsenglas " + Linsenglas.VERSION + ", Katzenpfoten " + Katzenpfoten.VERSION + ", Flotte " + Flotte.VERSION + ")");
        if (pa != null) System.out.println("\nAttest: " + pa);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ 1

    static void ohneKarte() {
        A.teil("1 Ohne Karte: Katzenpfoten (37), Gischt (35), Linse (36), Ausweichen (SF163)");
        katzenpfoten();
        gischtFormel();
        schwallNurBeiSchlag();
        linseLaeuft();
        ausweichen();
    }

    static void katzenpfoten() {
        A.info("Böenfaktor → Streuung (ln)", f("G 1,2: %.3f; 1,5: %.3f; 2,0: %.3f; ohne Böe: %.3f", Katzenpfoten.sigma(1.2), Katzenpfoten.sigma(1.5), Katzenpfoten.sigma(2.0), Katzenpfoten.sigma(Double.NaN)));
        // Drift: das Feld zu zwei Zeiten, Verschiebung mit der größten Ähnlichkeit
        double u = 2.5, v = 1.0, dtS = 10, dx = 4;
        int n = 64;
        Katzenpfoten.Feld fe = new Katzenpfoten.Feld();
        fe.schritt(1000, u, v);
        double[][] z0 = feld(fe, n, dx);
        fe.schritt(1000 + dtS, u, v);
        double[][] z1 = feld(fe, n, dx);
        double best = -2, bx = 0, by = 0;
        for (double sx = -10; sx <= 40; sx += 0.5)
            for (double sy = -10; sy <= 30; sy += 0.5) {
                double c = korrelation(z0, z1, n, dx, sx, sy);
                if (c > best) { best = c; bx = sx; by = sy; }
            }
        double soll = Katzenpfoten.DRIFT * Math.hypot(u, v) * dtS, ist = Math.hypot(bx, by);
        A.pruefe(Math.abs(ist / soll - 1) <= 0.10, "Katzenpfoten: Flecken treiben mit 0,8 des Winds (Prüfstein 37)",
                f("in %.0f s %.1f / %.1f m (Soll %.1f / %.1f m), Betrag %.1f %% des Solls; Ähnlichkeit %.3f", dtS, bx, by, Katzenpfoten.DRIFT * u * dtS,
                        Katzenpfoten.DRIFT * v * dtS, 100 * ist / soll, best), "± 10 %");
        // Kräuselung im Fleck gegen Cox–Munk beim Wind am Ort; Mittel des Winds; glatt unter dem Einsetzen
        double sig = Katzenpfoten.sigma(1.6), U = 3.2, dMax = 0, mw = 0, glatt = 0, voll = 0;
        java.util.Random r = new java.util.Random(7);
        int m = 200_000;
        for (int i = 0; i < m; i++) {
            double qx = 256 * r.nextDouble(), qy = 256 * r.nextDouble(), qt = 256 * r.nextDouble();
            double uo = Katzenpfoten.windOrt(qx, qy, qt, U, sig);
            mw += uo / m;
            double s2 = Katzenpfoten.sigma2(U, uo);
            if (uo >= Katzenpfoten.U_VOLL) { dMax = Math.max(dMax, Math.abs(s2 / Wasseroptik.coxMunk(uo) - 1)); voll++; }
            if (uo <= Katzenpfoten.U_EIN) glatt++;
        }
        A.pruefe(dMax <= 0.05 && voll > 0, "Katzenpfoten: Kräuselung im Fleck gegen Cox–Munk beim Wind am Ort (Prüfstein 37)",
                f("U %.1f m/s, σ %.3f (G 1,6): in %.1f %% der Fläche Wind am Ort ≥ 4 m/s (voll), größte Abweichung %.2f %%; glatt (≤ 1,5 m/s) %.1f %%; Mittel des Winds am Ort %.3f m/s",
                        U, sig, 100 * voll / m, 100 * dMax, 100 * glatt / m, mw), "± 5 %");
        double d6 = 0;
        for (int i = 0; i < 20_000; i++) {
            double uo = Katzenpfoten.windOrt(256 * r.nextDouble(), 256 * r.nextDouble(), 256 * r.nextDouble(), 6.0, Katzenpfoten.sigma(2.0));
            d6 = Math.max(d6, Math.abs(Katzenpfoten.sigma2(6.0, uo) / Wasseroptik.coxMunk(6.0) - 1));
        }
        A.pruefe(d6 < 1e-12, "Katzenpfoten: über 6 m/s volle Rauigkeit (Prüfstein 37)", f("U 6 m/s: größte Abweichung von Cox–Munk %.1e", d6), "keine");
        A.info("Katzenpfoten: Mittel des Winds am Ort", f("%.3f m/s bei U %.1f m/s (log-normal mit −σ²/2)", mw, U));
    }

    static double[][] feld(Katzenpfoten.Feld fe, int n, double dx) {
        double[][] z = new double[n][n];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double[] q = fe.ursprung(i * dx, j * dx);
                z[j][i] = Katzenpfoten.z(q[0], q[1], q[2]);
            }
        return z;
    }

    /** Ähnlichkeit von z1 mit z0, um (sx, sy) m verschoben (z1(x) gegen z0(x − s)), bilinear in z0. */
    static double korrelation(double[][] z0, double[][] z1, int n, double dx, double sx, double sy) {
        double s = 0, a = 0, b = 0;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double x = i - sx / dx, y = j - sy / dx;
                int i0 = (int) Math.floor(x), j0 = (int) Math.floor(y);
                if (i0 < 0 || j0 < 0 || i0 + 1 >= n || j0 + 1 >= n) continue;
                double fx = x - i0, fy = y - j0;
                double w = (1 - fx) * (1 - fy) * z0[j0][i0] + fx * (1 - fy) * z0[j0][i0 + 1] + (1 - fx) * fy * z0[j0 + 1][i0] + fx * fy * z0[j0 + 1][i0 + 1];
                s += w * z1[j][i];
                a += w * w;
                b += z1[j][i] * z1[j][i];
            }
        return a > 0 && b > 0 ? s / Math.sqrt(a * b) : -1;
    }

    static void gischtFormel() {
        // die geschlossene Lösung (Buendel, gischt.glsl) gegen Runge–Kutta mit 0,1 ms
        double dMax = 0, wegMax = 0;
        double[][] faelle = {{0.15, 3, 8, 1, 2, -1}, {0.3, 1.5, 6, -2, 9, 3}, {1.2, -4, 2, 0.5, 14, -6}, {0.6, 0, 12, 0, 0, 0}};
        for (double[] fa : faelle) {
            double k = fa[0];
            double[] v0 = {fa[1], fa[2], fa[3]}, w = {fa[4], fa[5]};
            double ux = w[0], uy = -Buendel.G / k, uz = w[1];
            double[] x = {0, 0, 0}, v = v0.clone();
            double h = 1e-4, t = 0;
            for (int s = 1; s <= 30; s++) {
                while (t < s * 0.1 - 1e-12) {
                    double[][] kk = new double[4][6];
                    double[] y = {x[0], x[1], x[2], v[0], v[1], v[2]};
                    for (int q = 0; q < 4; q++) {
                        double c = q == 0 ? 0 : q == 3 ? 1 : 0.5;
                        double[] yy = new double[6];
                        for (int e = 0; e < 6; e++) yy[e] = y[e] + (q == 0 ? 0 : c * h * kk[q - 1][e]);
                        kk[q] = new double[] {yy[3], yy[4], yy[5], -k * (yy[3] - ux), -k * (yy[4] - uy), -k * (yy[5] - uz)};
                    }
                    for (int e = 0; e < 3; e++) {
                        x[e] += h / 6 * (kk[0][e] + 2 * kk[1][e] + 2 * kk[2][e] + kk[3][e]);
                        v[e] += h / 6 * (kk[0][e + 3] + 2 * kk[1][e + 3] + 2 * kk[2][e + 3] + kk[3][e + 3]);
                    }
                    t += h;
                }
                double e1 = (1 - Math.exp(-k * t)) / k;
                double[] g = {ux * t + (v0[0] - ux) * e1, uy * t + (v0[1] - uy) * e1, uz * t + (v0[2] - uz) * e1};
                double weg = Math.sqrt(x[0] * x[0] + x[1] * x[1] + x[2] * x[2]);
                double d = Math.sqrt(Math.pow(g[0] - x[0], 2) + Math.pow(g[1] - x[1], 2) + Math.pow(g[2] - x[2], 2));
                if (weg > 0.05) dMax = Math.max(dMax, d / weg);
                wegMax = Math.max(wegMax, weg);
            }
        }
        A.pruefe(dMax <= 0.01, "Gischt: Flugbahn, geschlossene Lösung gegen Runge–Kutta (Prüfstein 35)",
                f("vier Würfe zu 3 s (k 0,15–1,2 1/s, mit Wind): größte Abweichung %.2e des Wegs (bis %.1f m)", dMax, wegMax), "± 1 %");
    }

    /** Der Kutter 60 s gegen eine Welle mit Bugschwall: Schläge, Bündel. */
    static int[] schwall(double a, double periode) {
        Welle w = a > 0 ? Welle.regelmaessig(a, periode, 180, 20) : Welle.ruhig(20);
        w.anlauf = 10;
        Koerper k = Vorgaben.krabbenkutter();
        k.einschwimmen(w, 0, 0, 0, 0, 0, 0);
        Bugschlag b = new Bugschlag(k);
        Bugschwall bs = new Bugschwall();
        double t = 0, dt = 1.0 / 30;
        int bu = 0;
        for (int i = 0; i < 1800; i++) {
            k.schritt(w, dt);
            t += dt;
            int vor = bs.liste().size();
            if (b.pruefe(k, w, t)) bs.schlag(k, b, w, t, 5, 0);
            bu += Math.max(0, bs.liste().size() - vor);
        }
        int spritz = 0;
        for (Buendel p : bs.liste()) if (p.kind() == Buendel.SPLASH) spritz++;
        return new int[] {b.zahl, bs.schlaege(), bu, spritz};
    }

    static void schwallNurBeiSchlag() {
        int[] r0 = schwall(0, 3.5), r1 = schwall(0.05, 3.5), r2 = schwall(0.8, 3.5);
        A.info("Gischt: Kutter gegen die See (60 s)", f("ruhig %d Schläge, %d Bündel; a 0,05 m %d, %d; a 0,8 m %d Schläge, %d Würfe, %d Bündel (%d Spritzwasser)",
                r0[0], r0[2], r1[0], r1[2], r2[0], r2[1], r2[2], r2[3]));
        A.pruefe(r0[2] == 0 && r1[2] == 0 && r2[0] >= 1 && r2[1] == r2[0] && r2[3] == 2 * Math.min(r2[0], Bugschwall.MAX / 2),
                "Gischt: Schwall nur bei Bugschlag, je Schlag zwei Bündel Spritzwasser (Prüfstein 35)", f("ruhig %d, klein %d Bündel; steil %d Schläge, %d Würfe, %d Spritzwasser",
                        r0[2], r1[2], r2[0], r2[1], r2[3]), "0; 0; je Schlag ein Wurf mit 2");
    }

    static void linseLaeuft() {
        double[] faktoren = {0.8, 1.5};
        double[] weg = new double[2];
        for (int f0 = 0; f0 < 2; f0++) {
            Linsenglas l = new Linsenglas(5);
            l.glas.evaporation = 0;
            double a = faktoren[f0] * Scheibe.A_CRIT;
            l.glas.addDrop(Linsenglas.BREITE / 2, Linsenglas.HOEHE * 0.8, a);
            Scheibe.Snapshot s0 = l.glas.snapshot();
            for (int i = 0; i < 120; i++) l.schritt(1.0 / 60, i / 60.0, new double[] {0, 0, 0}, 0, null);
            Scheibe.Snapshot s = l.glas.snapshot();
            double y0 = Linsenglas.HOEHE * 0.8;
            double ymin = y0;
            for (int i = 0; i < s.count(); i++) ymin = Math.min(ymin, s.drops()[i * Scheibe.Snapshot.FLOATS + 1]);
            weg[f0] = s.count() == 0 ? y0 : y0 - ymin;
        }
        A.pruefe(weg[0] < 1e-4 && weg[1] > 1e-3, "Linse: ein Tropfen läuft ab A_CRIT (Prüfstein 36)",
                f("a = 0,8 A_CRIT: %.2f mm in 2 s; a = 1,5 A_CRIT: %.1f mm (A_CRIT %.1f mm, Scheibe senkrecht)", 1000 * weg[0], 1000 * weg[1], 1000 * Scheibe.A_CRIT), "haftet; läuft (> 1 mm)");
    }

    /** SF163: ein Kutter in Fahrt auf eine Ente und ein Stück Treibgut zu, ruhiges Wasser, ohne Karte. */
    static void ausweichen() {
        List<Schwimmerangabe> l = new ArrayList<>();
        double x0 = 466000, y0 = 6046000;
        l.add(new Schwimmerangabe("kutter", "krabbenkutter", x0, y0, 0, "frei", new double[0]));
        l.add(new Schwimmerangabe("enten.E9.1", "eiderente", x0 + 1.0, y0 + 70, 180, "trupp", new double[] {x0 + 1.0, y0 + 70}));
        l.add(new Schwimmerangabe("balken", "balken", x0 - 0.8, y0 + 45, 90, "frei", new double[0]));
        Flotte fl = new Flotte(l, (x, y) -> -12);
        try {
            Flotte.Schwimmer ku = fl.schwimmer.get(0), en = fl.schwimmer.get(1), ba = fl.schwimmer.get(2);
            Flotte.Umwelt um = new Flotte.Umwelt() {
                @Override public double pegel(double o, double n) { return 0; }

                @Override public double[] wind() { return new double[] {0, 0}; }
            };
            double t = 1000, dt = 1.0 / 30, eindringMax = 0, abstandBeimTauchen = Double.NaN, vMax = 0;
            fl.vorBild(t, 1, dt, um);
            fl.warte();
            ku.koerper.hebel = 1.0;
            ku.koerper.kursSoll = 0;
            for (int i = 0; i < 60 * 30; i++) {
                t += dt;
                fl.vorBild(t, 1, dt, um);
                try { fl.warte(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                double[] ok = ku.koerper.lage(), ka = ku.koerper.form.kasten();
                double[] vk = ku.koerper.geschwindigkeit();
                vMax = Math.max(vMax, Math.hypot(vk[0], vk[1]));
                if (fl.abgetaucht > 0 && Double.isNaN(abstandBeimTauchen)) {
                    double[] bug = ku.koerper.punktWelt(ka[1], 0, 0), oe = en.koerper.lage();
                    abstandBeimTauchen = oe[1] - bug[1];
                }
                // Treibgut im Rumpf? (vorn, Backbord im Kutter)
                double[] ob = ba.koerper.lage(), mi = ku.koerper.punktWelt(0, 0, 0);
                double kr = Math.toRadians(ku.koerper.kurs()), fo = Math.sin(kr), fn = Math.cos(kr);
                double xb = (ob[0] - mi[0]) * fo + (ob[1] - mi[1]) * fn, yb = -(ob[0] - mi[0]) * fn + (ob[1] - mi[1]) * fo;
                if (xb > ka[0] && xb < ka[1] && ku.koerper.form instanceof com.dan.physics.auftrieb.Form.Rumpf ru) {
                    double pen = ru.halbeBreite(xb, ru.tiefgang) - Math.abs(yb);
                    eindringMax = Math.max(eindringMax, pen);
                }
                if (ok[1] > y0 + 120) break;
            }
            double[] ob = ba.koerper.lage();
            A.info("Ausweichen: Kutter in Fahrt", f("bis %.1f m/s; Ente taucht %s; Treibgut am Ende %.1f m quer zum Kurs; Stöße des Rumpfs %d",
                    vMax, fl.abgetaucht > 0 ? f("%.1f m vor dem Bug ab", abstandBeimTauchen) : "NICHT ab", ob[0] - x0, fl.geschoben));
            A.pruefe(fl.abgetaucht >= 1 && abstandBeimTauchen > 0 && abstandBeimTauchen <= Flotte.AUSWEICH_VOR + 1, "Ausweichen: die Ente taucht vor dem Schiff ab (SF163)",
                    f("%d mal, zuerst %.1f m vor dem Bug", fl.abgetaucht, abstandBeimTauchen), "≥ 1, bis 30 m vor dem Bug [G]");
            A.pruefe(fl.geschoben >= 1 && eindringMax <= Flotte.SCHUB_RAND + 0.5, "Ausweichen: der Rumpf schiebt das Treibgut weg (SF163)",
                    f("%d Stöße; größte Tiefe im Rumpf %.2f m (Mitte des Balkens gegen die Bordwand)", fl.geschoben, eindringMax), "≥ 1; Mitte höchstens 0,8 m in der Bordwand [G]");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            fl.close();
        }
    }

    // ------------------------------------------------------------------ 2

    static void pruefszene() {
        A.teil("2 Karte in der Prüfszene: Ente mit Wellen (40), Gischt (35) und Linse (36) auf der Karte");
        Profilpuffer p = new Profilpuffer(SchwimmerCheck.OM, SchwimmerCheck.UNTEN, SchwimmerCheck.OBEN, SchwimmerCheck.ND, 0x5EE6A3L, false);
        SchwimmerCheck.ob = SchwimmerCheck.szene(p);
        SchwimmerCheck.karte = SeegangKarte.mitGeraet(SchwimmerCheck.ob, SchwimmerCheck.STUFEN, SchwimmerCheck.N, SchwimmerCheck.D0);
        SeegangKarte karte = SchwimmerCheck.karte;
        A.info("Gerät", karte.geraet().name + " (" + karte.geraet().treiber + ")");
        karte.eingaben();
        karte.bezug(SchwimmerCheck.BX, SchwimmerCheck.BY);
        SchwimmerCheck.suche = new Koerpersuche(karte.geraet(), karte.geraet().konto("Körpersuche", 256L << 20), SchwimmerCheck.NB);
        try {
            for (double ab : ABSTAENDE) enteMitWellen(ab, false);
            enteMitWellen(5, true);
            gischtAufDerKarte(karte.geraet());
            linseAufDerKarte(karte.geraet());
        } finally {
            SchwimmerCheck.suche.close();
            karte.close();
        }
    }

    /** Prüfstein 40: die Ente nimmt Höhe und Neigung vom Punkt ihres Trupps (5 m weg), gegen die Referenz an ihren Säulen. */
    static double[] ABSTAENDE = {0, 2, 5};

    static void enteMitWellen(double ab, boolean eigen) {
        double dtBild = 1.0 / 30;
        Koerper ka = Flotte.vorgabe("eiderente"), kr = Flotte.vorgabe("eiderente");
        double[] rast = Flotte.raster("eiderente");
        ka.kartenRaster(rast[0], rast[1]);
        double mx = 466000, my = 6046000, x0 = mx + 0.8 * ab, y0 = my - 0.6 * ab, kurs = 60;
        Bandwasser wa = new Bandwasser(0), wr = new Bandwasser(0);
        wa.zuordnung(ka.kartenpunkt);
        for (Bandwasser w : new Bandwasser[] {wa, wr}) { w.setze(0, 0, 0, 0, 0); w.grundSonst(-SchwimmerCheck.tiefe(x0, y0)); }
        ka.einschwimmen(wa, x0, y0, kurs, 0, 0, SchwimmerCheck.T0);
        kr.einschwimmen(wr, x0, y0, kurs, 0, 0, SchwimmerCheck.T0);
        int ms = kr.proben.saeulen;
        Wasserpunkte wp = new Wasserpunkte();
        Geraet g = SchwimmerCheck.karte.geraet();
        double t = SchwimmerCheck.T0, dz = 0, sz = 0, zz = 0;
        SchwimmerCheck.suche.leeren();
        int bilder = (int) Math.round(SchwimmerCheck.PRUEF_S / dtBild);
        for (int i = -Koerpersuche.RING; i < bilder; i++) {
            double tb = t + dtBild;
            SchwimmerCheck.karte.zeit(tb);
            com.dan.core.gpu.Ablauf a = new com.dan.core.gpu.Ablauf(g);
            SchwimmerCheck.suche.lies(wp);
            double[] px = {mx}, py = {my};
            if (eigen) ka.kartenWelt(0, px, py, 0);                             // je Ente ein eigener Punkt (SF161 B)
            SchwimmerCheck.suche.suche(a, SchwimmerCheck.karte, tb, SchwimmerCheck.anfrage(px, py, 1));
            g.rechnen.einmal(c -> a.aufnehmen(c, null));
            if (i < 0) continue;
            double[] rx = new double[ms], ry = new double[ms];
            kr.saeulenWelt(0, rx, ry, 0);
            wr.stand(SchwimmerCheck.referenz(SchwimmerCheck.anfrage(rx, ry, ms), t));
            if (wa.stand() != wp.stand()) wa.stand(wp.stand());
            ka.schritt(wa, dtBild);
            kr.schritt(wr, dtBild);
            t += dtBild;
            double ha = ka.lage()[2], hr = kr.lage()[2];
            dz = Math.max(dz, Math.abs(ha - hr));
            sz += hr;
            zz += hr * hr;
        }
        double mz = sz / bilder, hub = Math.sqrt(Math.max(0, zz / bilder - mz * mz));
        String text = f("Ente %.0f m von der Mitte des Trupps, %.0f s, Hs %.1f m: größte Abweichung %.1f mm, Hub RMS (Referenz) %.3f m, %d Säulen, %d Punkt",
                        ab, SchwimmerCheck.PRUEF_S, SchwimmerCheck.HS, 1000 * dz, hub, ms, ka.kartenpunkte);
        // seit S8b (Befund): der Punkt des Trupps ist Auskunft (er hält die Grenze nicht); Prüfstein ist der Punkt je Ente
        if (eigen) A.pruefe(dz <= 0.03, "Ente mit Wellen: Hub mit ihrem eigenen Punkt gegen die Referenz an ihren Säulen (Prüfstein 40)", text, "± 3 cm");
        else A.info("Ente mit dem Punkt des Trupps (SF161 A, Auskunft)", text);
    }

    /** Prüfstein 35 auf der Karte: Tropfen aus gischtprobe.comp gegen Buendel.particle in double. */
    static void gischtAufDerKarte(Geraet g) {
        Welle w = Welle.regelmaessig(0.8, 3.5, 180, 20);
        w.anlauf = 10;
        Koerper k = Vorgaben.krabbenkutter();
        k.einschwimmen(w, 471380, 6048345, 0, 0, 0, 0);
        Bugschlag b = new Bugschlag(k);
        Bugschwall bs = new Bugschwall();
        double t = 0;
        for (int i = 0; i < 1800 && bs.liste().size() < 4; i++) {
            k.schritt(w, 1.0 / 30);
            t += 1.0 / 30;
            if (b.pruefe(k, w, t)) bs.schlag(k, b, w, t, 6, -2);
        }
        if (bs.liste().isEmpty()) { A.pruefe(false, "Gischt auf der Karte (Prüfstein 35)", "kein Schlag in 60 s", "Bündel"); return; }
        double[] auge = {471370, 6048340, 4};
        Konto konto = g.konto("Prüfung S8", 64L << 20);
        double dMax = 0, wegMax = 0;
        int lebtAnders = 0, n = 0;
        for (double alter : new double[] {0.2, 0.6, 1.2}) {
            double ts = bs.liste().get(0).t0() + alter;
            double[] l = bs.buendel(ts);
            List<Buendel> pl = bs.liste();
            int[] proben = new int[2 * 64 * pl.size()];
            int q = 0;
            for (int k2 = 0; k2 < pl.size(); k2++) for (int i = 0; i < 64; i++) { proben[q++] = k2; proben[q++] = i * 6; }
            float[] r = Gischtbild.probe(g, konto, l, auge[0], auge[2], auge[1], proben);
            for (int s = 0; s < proben.length / 2; s++) {
                Buendel p = pl.get(proben[2 * s]);
                double[] c = p.particle(proben[2 * s + 1], ts);
                double cx = c[0] - auge[0], cy = c[1] - auge[2], cz = c[2] + auge[1];
                double d = Math.sqrt(Math.pow(r[8 * s] - cx, 2) + Math.pow(r[8 * s + 1] - cy, 2) + Math.pow(r[8 * s + 2] - cz, 2));
                double weg = Math.sqrt(Math.pow(c[0] - p.x(), 2) + Math.pow(c[1] - p.y(), 2) + Math.pow(c[2] - p.z(), 2));
                if ((r[8 * s + 4] > 0.5) != (c[4] > 0.5)) lebtAnders++;
                else if (c[4] > 0.5) { dMax = Math.max(dMax, d / Math.max(0.1, weg)); wegMax = Math.max(wegMax, weg); n++; }
            }
        }
        A.pruefe(dMax <= 0.01 && lebtAnders == 0, "Gischt: Tropfen auf der Karte gegen den Prozessor (Prüfstein 35)",
                f("%d Tropfen aus %d Bündeln zu drei Zeiten: größte Abweichung %.2e des Wegs (bis %.1f m), lebt anders: %d", n, bs.liste().size(), dMax, wegMax, lebtAnders), "± 1 %; gleich");
    }

    /** Prüfstein 36 auf der Karte: das Bild durch einen Tropfen gegen die Brechung an der Kugelkappe in double. */
    static void linseAufDerKarte(Geraet g) {
        Konto konto = g.konto("Prüfung S8, Linse", 64L << 20);
        int bw = 1280, bh = 720;
        double bild = 60;
        try (Linse li = new Linse(g, konto)) {
            li.breite = Linsenglas.BREITE;
            li.hoehe = Linsenglas.HOEHE;
            double a = 2.0e-3, cx = Linsenglas.BREITE * 0.5, cy = Linsenglas.HOEHE * 0.5;
            float[] tr = {(float) cx, (float) cy, (float) a, 0, 0, -1, 0, 0};
            List<float[]> uvl = new ArrayList<>();
            for (double r = 0.02; r < 0.98; r += 0.04)
                for (double w = 0; w < 2 * Math.PI; w += Math.PI / 8) {
                    double px = cx + r * a * Math.cos(w), py = cy + r * a * Math.sin(w);
                    uvl.add(new float[] {(float) (px / Linsenglas.BREITE), (float) (1 - py / Linsenglas.HOEHE)});
                }
            float[] uv = new float[2 * uvl.size()];
            for (int i = 0; i < uvl.size(); i++) { uv[2 * i] = uvl.get(i)[0]; uv[2 * i + 1] = uvl.get(i)[1]; }
            float[] r = li.probe(tr, 1, bild, bw, bh, uv);
            double dIn = 0, versatz = 0;
            int innen = 0, tir = 0, tirAnders = 0, rand = 0;
            for (int i = 0; i < uvl.size(); i++) {
                double[] ref = Linse.referenz(Linsenglas.BREITE, Linsenglas.HOEHE, bild, bw, bh, cx, cy, a, uv[2 * i], uv[2 * i + 1]);
                double qx = (uv[2 * i] * Linsenglas.BREITE - cx) / a, qy = ((1 - uv[2 * i + 1]) * Linsenglas.HOEHE - cy) / a, q = Math.hypot(qx, qy);
                boolean tirR = ref[2] == 0, tirK = r[4 * i + 2] == 0;
                if (tirR) tir++;
                if (q > 0.97) { rand++; continue; }                            // ganz am Rand trifft der Strahl die Nachbarzelle (Wechsel der Kappe)
                if (tirR != tirK) { tirAnders++; continue; }
                if (tirR) continue;
                double d = Math.hypot((r[4 * i] - ref[0]) * bw, (r[4 * i + 1] - ref[1]) * bh);
                dIn = Math.max(dIn, d);
                versatz = Math.max(versatz, Math.hypot((ref[0] - uv[2 * i]) * bw, (ref[1] - uv[2 * i + 1]) * bh));
                innen++;
            }
            A.pruefe(dIn <= 1.0 && tirAnders == 0, "Linse: Bild durch einen Tropfen gegen die Brechung einer Kugelkappe, Strahl in double (Prüfstein 36)",
                    f("Tropfen a = %.1f mm, %d Proben innen (bis 0,97 a): größte Abweichung %.2f Bildpunkte bei Versatz bis %.0f Bildpunkten; Totalreflexion %d (Karte anders: %d); am Rand %d nicht geprüft",
                            1000 * a, innen, dIn, versatz, tir, tirAnders, rand), "± 1 Bildpunkt");
        }
    }

    // ------------------------------------------------------------------ 3

    static void anwendung(File szenen, int breite, int hoehe) {
        A.teil("3 In der Anwendung: Katzenpfoten (37), Böe aus ERA5, Gischt und Linse an Bord (35, 36, 41)");
        try {
            Szenen sz = Szenen.lies(szenen);
            Buehne b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
            try {
                katzenImBild(b, sz, breite, hoehe);
                entenImBild(b, sz, breite, hoehe);
                anBord(b, sz, breite, hoehe);
            } finally {
                b.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void katzenImBild(Buehne b, Szenen sz, int breite, int hoehe) throws Exception {
        Szenario s = sz.szenario("ruhigsee");
        SchwimmerCheck.pegel(b, sz);
        b.oeffne(s);
        b.wasserbild().seegang = true;
        double t = (Instant.parse("2026-10-30T14:30:00Z").toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
        double dkMax = 0, duMax = 0;
        int nProben = 0;
        for (String an : new String[] {"anleger_see", "rinne_hoch"}) {
            Ansicht ans = sz.ansicht("anleger_see");
            Blick bl = new Blick();
            ans.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
            bl.groesse(breite, hoehe);
            double[] wa = b.wasserUndSeegang(bl.ost, bl.nord);
            Ansicht.hebe(bl, wa[0], wa[1], ans.ueberWasser);
            if (an.equals("rinne_hoch")) {                                  // seit L6a: 12 m über dem Wasser, schräg gegen die Sonne
                bl.hoehe = (Double.isNaN(wa[0]) ? 1 : wa[0]) + 12;
                bl.neigung = -14;
                bl.richtung = 215;
                bl.bildwinkel = 55;
            }
            for (boolean k : new boolean[] {false, true}) {
                b.wasserbild().katzen = k;
                float[] px = b.standbild(bl, t);
                javax.imageio.ImageIO.write(SeegangBilder.bild(px, breite, hoehe), "png", Path.of("docs", "status", "S8b_katzen_" + an + "_" + (k ? "an" : "aus") + ".png").toFile());
            }
            double[] kk = b.wasserbild().katzenKopf;
            A.info("Katzenpfoten, " + an, f("Bilder docs/status/S8b_katzen_%s_an.png und _aus.png; Wind der Szene %.2f m/s, Böe aus ERA5 %s, Böenfaktor %s, σ %.3f, Gewicht %.2f",
                    an, kk[4], Double.isNaN(b.bild().boee) ? "KEINE" : f("%.1f m/s", b.bild().boee), Double.isNaN(b.wasserbild().boeFaktor) ? "–" : f("%.2f", b.wasserbild().boeFaktor), kk[3], kk[6]));
            // Karte gegen Prozessor an Orten vor dem Auge
            List<float[]> orte = new ArrayList<>();
            java.util.Random r = new java.util.Random(3);
            for (int i = 0; i < 2000; i++) {
                double d = 5 + 800 * r.nextDouble(), w = Math.toRadians(bl.richtung + 60 * (r.nextDouble() - 0.5));
                orte.add(new float[] {(float) (d * Math.sin(w)), (float) (-d * Math.cos(w))});
            }
            float[] o = new float[2 * orte.size()];
            for (int i = 0; i < orte.size(); i++) { o[2 * i] = orte.get(i)[0]; o[2 * i + 1] = orte.get(i)[1]; }
            Katzenprobe.Ergebnis e = Katzenprobe.rechne(b.geraet(), b.geraet().konto("Prüfung S8, Katzen", 16L << 20), b.wasserbild(), o);
            for (int i = 0; i < orte.size(); i++) {
                duMax = Math.max(duMax, Math.abs(e.karte()[2 * i] - e.prozessor()[2 * i]) / Math.max(0.1, e.prozessor()[2 * i]));
                dkMax = Math.max(dkMax, Math.abs(e.karte()[2 * i + 1] - e.prozessor()[2 * i + 1]) / Math.max(1e-4, e.prozessor()[2 * i + 1]));
            }
            nProben += orte.size();
        }
        A.pruefe(!Double.isNaN(b.bild().boee), "Katzenpfoten: die Böe kommt aus ERA5 (SF156 A)", Double.isNaN(b.bild().boee) ? "keine Böe im Gebiet (Schritt boee?)"
                : f("fg10 %.1f m/s am Auge, G %.2f", b.bild().boee, b.wasserbild().boeFaktor), "da");
        A.pruefe(duMax <= 0.01 && dkMax <= 0.01, "Katzenpfoten: Karte gegen Prozessor (Prüfstein 37)",
                f("%d Orte in zwei Ansichten: Wind am Ort bis %.2e, Neigungsvarianz bis %.2e relativ", nProben, duMax, dkMax), "± 1 % [G]");
    }

    /** Prüfstein 41 mit den Enten: das Auge 130 m vor dem Trupp E1 am ruhigen Tag; Punkte der Karte, Kosten, Bild. */
    static void entenImBild(Buehne b, Szenen sz, int breite, int hoehe) throws Exception {
        Szenario s = sz.szenario("ruhigsee");
        SchwimmerCheck.pegel(b, sz);
        b.oeffne(s);
        b.wasserbild().seegang = true;
        Flaechenwart w = b.wart();
        Flotte fl = b.flotte();
        double t = (Instant.parse("2026-10-30T14:30:00Z").toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
        Blick bl = new Blick();
        bl.ost = 472211 - 115;
        bl.nord = 6048031 - 60;
        double[] wa = b.wasserUndSeegang(bl.ost, bl.nord);
        bl.hoehe = (Double.isNaN(wa[0]) ? 1.0 : wa[0]) + 2.5;
        bl.richtung = 62;
        bl.neigung = -6;
        bl.bildwinkel = 40;
        bl.groesse(breite, hoehe);
        b.standbild(bl, t);
        fl.neuSetzen();
        double dt = 1.0 / 30;
        long uhrNs = 0, w0 = System.nanoTime();
        int bereit = -1, n = 0, punkte = 0;
        Map<String, Double> summe = new java.util.TreeMap<>();
        double faden = 0;
        for (int i = 0; i < 3000; i++) {
            w.zeitraffer(1);
            w.vorBild(bl, t, uhrNs);
            b.flotteBild(t, 1, dt);
            Buehne.Bildzeiten z = b.bildzeiten(bl, t, 1, true);
            w.nachBild();
            if (bereit < 0 && w.bereit() && !w.wechselt() && (w.koerpermitte() == null || w.koerperBereit())) bereit = i;
            if (bereit >= 0 && i > bereit + 60) {
                for (Map.Entry<String, Double> e : z.abschnitte().entrySet()) summe.merge(e.getKey(), e.getValue(), Double::sum);
                n++;
                fl.warte();
                faden += fl.fadenMs;
                if (w.koerpersuche() != null) punkte = Math.max(punkte, w.koerpersuche().zuletzt);
                if (n >= 300) break;
            }
            t += dt;
            uhrNs += Math.round(dt * 1e9);
            if ((System.nanoTime() - w0) / 1e9 > 300) break;
        }
        fl.warte();
        float[] px = b.standbild(bl, t);
        javax.imageio.ImageIO.write(SeegangBilder.bild(px, breite, hoehe), "png", Path.of("docs", "status", "S8b_enten.png").toFile());
        int enten = 0, da = 0, mitPunkt = 0, treib = 0;
        Wasserpunkte.Stand st = fl.wasserpunkte.stand();
        for (Flotte.Schwimmer x : fl.schwimmer) {
            boolean ente = "eiderente".equals(x.angabe.art), tg = java.util.Set.of("balken", "fischkiste", "stamm").contains(x.angabe.art);
            if (!ente && !tg) continue;
            int k = fl.schwimmer.indexOf(x);
            boolean p = st != null && k < st.anfrage.anfang.length && st.anfrage.anfang[k] >= 0;
            if (ente) { enten++; if (!x.weg) da++; if (p) mitPunkt++; }
            if (tg && p) treib++;
        }
        double sk = summe.getOrDefault("see_koerper", 0.0) / Math.max(1, n), sb = 0;
        for (Map.Entry<String, Double> e : summe.entrySet()) if (e.getKey().startsWith("Schwimmer")) sb += e.getValue() / Math.max(1, n);
        double teil = faden / Math.max(1, n) / (1.0 / 30 / 0.004);
        A.info("Enten im Bild (ruhigsee 14:30 UTC, 130 m vor dem Trupp E1)", f("Bild docs/status/S8b_enten.png; %d Enten, %d da, %d mit eigenem Punkt; Treibgut mit Punkt %d; Punkte der Karte %d",
                enten, da, mitPunkt, treib, punkte));
        A.pruefe(n > 0 && sk <= 0.75 && sb <= 1.0 && teil <= 2.5, "Kosten mit den Enten nah (Prüfstein 41)",
                f("see_koerper %.3f ms, Bild der Schwimmer %.3f ms, Faden %.2f ms je Teilschritt (%d Bilder)", sk, sb, teil, n), "≤ 0,75; ≤ 1,0; ≤ 2,5 ms");
    }

    static void anBord(Buehne b, Szenen sz, int breite, int hoehe) throws Exception {
        Szenario s = sz.szenario("seegang");
        SchwimmerCheck.pegel(b, sz);
        b.oeffne(s);
        b.wasserbild().seegang = true;
        Flaechenwart w = b.wart();
        Flotte fl = b.flotte();
        Ansicht an = sz.ansicht("bord_kutter");
        Bordkamera bk = b.bord(an);
        if (bk == null) throw new IllegalStateException("keine Bordkamera auf „" + an.bord + "“");
        double t = (Instant.parse("2026-10-25T13:30:00Z").toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
        Blick bl0 = new Blick();
        an.setze(bl0, b.gelaende, b.utm(), b.uhr().bei(t));
        bl0.groesse(breite, hoehe);
        b.standbild(bl0, t);
        fl.neuSetzen();
        double dt = 1.0 / 30, gischtMs = 0, linseMs = 0;
        long uhrNs = 0, w0 = System.nanoTime();
        int bereit = -1, gemessen = 0, buendelMax = 0, mitGischt = 0, mitLinse = 0;
        boolean probe = false, bildGeschrieben = false;
        int bildAb = -1;
        Flotte.Schwimmer ku = fl.schwimmer.get(bk.nr);
        int schlag0 = 0;
        for (int i = 0; i < 4000; i++) {
            w.zeitraffer(1);
            b.flotteBild(t, 1, dt);
            Blick bl = bk.weiter(dt, t, bl0);
            if (bl == null) bl = bl0;
            bl.groesse(breite, hoehe);
            w.vorBild(bl, t, uhrNs);
            Buehne.Bildzeiten z = b.bildzeiten(bl, t, 1, true);
            w.nachBild();
            if (bereit < 0 && w.bereit() && !w.wechselt()) { bereit = i; schlag0 = ku.bug.zahl; }
            if (bereit >= 0 && i > bereit + 30) {
                gemessen++;
                double gs = 0, ls = 0;
                for (Map.Entry<String, Double> e : z.abschnitte().entrySet()) {
                    if (e.getKey().startsWith("Gischt")) gs += e.getValue();
                    if (e.getKey().startsWith("Linse")) ls += e.getValue();
                }
                int bu = b.gischtbild() != null ? b.gischtbild().gezeichnet : 0;
                if (bu > 0) { gischtMs = Math.max(gischtMs, gs); mitGischt++; }
                if (b.linsenbild() != null && b.linsenbild().gezeichnet > 0) { linseMs = Math.max(linseMs, ls); mitLinse++; }
                // seit L6a (Befund 3: das Bild im ersten Bild des Schwalls zeigte noch keine Tropfen): 0,4 s nach dem Wurf
                if (bu > buendelMax) { buendelMax = bu; bildAb = gemessen + 12; }
                if (gemessen == bildAb) {
                    javax.imageio.ImageIO.write(SeegangBilder.bild(b.letztesBild(), breite, hoehe), "png", Path.of("docs", "status", "S8a_bord_gischt.png").toFile());
                    bildGeschrieben = true;
                }
                // nach 50 s ohne Schlag: ein Schlag von Hand für Bild und Kosten (Probe, klar benannt)
                if (gemessen == 1500 && ku.bug.zahl == schlag0) {
                    fl.warte();
                    Bugschlag bs = ku.bug;
                    bs.letzteV = 3.5;
                    fl.gischt.schlag(ku.koerper, bs, ku.wasser, t, b.bild().windU, b.bild().windV);
                    probe = true;
                }
                if (gemessen >= 1800) break;
            }
            t += dt;
            uhrNs += Math.round(dt * 1e9);
            if ((System.nanoTime() - w0) / 1e9 > 420) break;
        }
        fl.warte();
        javax.imageio.ImageIO.write(SeegangBilder.bild(b.letztesBild(), breite, hoehe), "png", Path.of("docs", "status", "S8a_bord_ende.png").toFile());
        int schlaege = ku.bug.zahl - schlag0;
        A.info("An Bord des Kutters (seegang, ab 13:30 UTC)", f("%d Bilder; Bugschläge %d, Würfe %d%s; größte Zahl Bündel im Bild %d; Treffer auf der Linse %d, Tropfen jetzt %d; Bilder docs/status/S8a_bord_gischt.png%s, S8a_bord_ende.png",
                gemessen, schlaege, fl.gischt.schlaege(), probe ? " (dazu ein Schlag von Hand nach 50 s: Probe)" : "", buendelMax, bk.linse.treffer(), bk.linse.glas.snapshot().count(),
                bildGeschrieben ? "" : " (keins: keine Gischt im Bild)"));
        A.pruefe(mitGischt == 0 || gischtMs <= 0.3, "Kosten: Gischt (Prüfstein 41)", mitGischt == 0 ? "keine Gischt im Bild" : f("höchstens %.3f ms in %d Bildern mit Gischt", gischtMs, mitGischt), "≤ 0,3 ms [G]");
        A.pruefe(mitLinse == 0 || linseMs <= 0.3, "Kosten: Linse (Prüfstein 41)", mitLinse == 0 ? "keine Tropfen auf der Linse" : f("höchstens %.3f ms in %d Bildern mit Tropfen", linseMs, mitLinse), "≤ 0,3 ms [G]");
        A.pruefe(ku.kentern == null, "seegang: Kutter nicht gekentert", f("größte Krängung %.1f°", ku.kraengungMax), "≤ " + (int) Flotte.KENTERN + "° [G]");
    }
}
