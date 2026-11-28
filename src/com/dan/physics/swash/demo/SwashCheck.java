package com.dan.physics.swash.demo;

import com.dan.core.pruef.Attest;
import com.dan.physics.swash.Boden;
import com.dan.physics.swash.Swash;

import java.nio.file.Path;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfsteine des Lösers für Auflauf und Rücklauf (Seegang Werkbuch, S6a; Grenzen vor der Messung, Datei 64, Abschnitt 3),
 * ohne Grafikkarte:
 * <ol>
 * <li>Ruhender See über welligem Grund mit trockenem Ufer und Insel: |u| ≤ 1e-10 m/s.</li>
 * <li>Volumen im geschlossenen Becken mit Wellen über trockene Hänge: ohne Versickern ≤ 1e-12 (relativ); mit Versickern
 * Wasser im Gitter plus versickertes gleich dem Anfang (≤ 1e-12).</li>
 * <li>Dammbruch auf nassem Grund gegen Stoker (1957): Höhe des Plateaus, Ort des Stoßes und mittlerer Fehler ≤ 1 %.</li>
 * <li>Auflauf einer Einzelwelle auf der Böschung 1:19,85 gegen Synolakis (1987): R/d = 2,831 √(cot β) (H/d)^(5/4), ± 5 %.</li>
 * <li>Nicht brechende periodische Welle auf ebener Böschung (Carrier und Greenspan 1958): höchster Auflauf gegen
 * R = 2 a / √(J₀²(2 k X₀) + J₁²(2 k X₀)) (Keller und Keller 1964; nach Carrier und Greenspan gilt die lineare Höhe auch
 * nichtlinear), ± 5 %; die Welle kommt über den Rand mit Antrieb 2 m vor dem Fuß herein (nach der Messung vom ersten Fall
 * mit 25 m geändert, siehe dort).</li>
 * <li>Zunge auf der Böschung (Shen und Meyer 1963): Eine Schicht gleicher Tiefe h₀ auf der Böschung läuft als Zunge mit
 * u₀ = 2 √(g h₀) los und steigt ballistisch, R = u₀² / 2g (senkrecht; längs der Böschung u₀² / 2g sin β). Für die
 * Kontur h = h_Spitze ist u₀ um 3 √(g h_Spitze) kleiner (Profil nach Ritter im beschleunigten Bezug, genau). ± 10 %.</li>
 * <li>Versickern in einem stehenden Tümpel gegen das Integral nach Darcy (Green und Ampt, h fällt mit):
 * t(F) = [F/a − (c/a²) ln(1 + a F/c)] / K mit a = 1 − Δθ, c = (h₀ + ψ) Δθ, ± 5 %.</li>
 * <li>Seit S6a dazu (Grenze vor der Messung, [G]): Abbremsung einer gleichförmigen Strömung nach Manning gegen
 * u(t) = u₀ / (1 + g n² u₀ t / h^(4/3)), ± 1 %.</li>
 * <li>Auskunft: Kosten eines Schritts des Rechtecks 1024 × 256 zu 0,25 m auf dem Prozessor (für S6b).</li>
 * </ol>
 * Attest {@code docs/status/S6a_Swash.md}.
 */
public final class SwashCheck {
    static final Attest A = new Attest("com.dan.physics.swash", Swash.VERSION);
    static final double G = 9.81;

    public static void main(String[] args) {
        String nur = args.length >= 2 && args[0].equals("--teil") ? args[1] : null;
        long t0 = System.nanoTime();
        try {
            if (nur == null || nur.equals("1")) ruhe();
            if (nur == null || nur.equals("2")) volumen();
            if (nur == null || nur.equals("3")) stoker();
            if (nur == null || nur.equals("4")) synolakis();
            if (nur == null || nur.equals("5")) carrierGreenspan();
            if (nur == null || nur.equals("6")) zunge();
            if (nur == null || nur.equals("7")) darcy();
            if (nur == null || nur.equals("8")) manning();
            if (nur == null || nur.equals("9")) kosten();
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer der Prüfung", f("%.1f s", (System.nanoTime() - t0) / 1e9));
        if (nur == null) {
            Path p = A.schreibe(Path.of("docs", "status", "S6a_Swash.md"), "S6a – Auflauf und Rücklauf, Löser auf dem Prozessor (Swash " + Swash.VERSION + ")");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ 1 und 2: Becken

    /** Becken 40 × 20 m zu 0,25 m: Hang nach Osten bis 0,8 m über dem Spiegel, Wellen im Grund, eine Insel; Wände ringsum. */
    static Swash becken() {
        Swash s = new Swash(160, 80, 0.25, 0, 0, 0);
        s.grund((x, y) -> -0.8 + 0.04 * x + 0.25 * Math.sin(2 * Math.PI * x / 7.3) * Math.cos(2 * Math.PI * y / 5.1)
                + 0.9 * Math.exp(-((x - 12.3) * (x - 12.3) + (y - 10.2) * (y - 10.2)) / 8));
        return s;
    }

    static void ruhe() {
        A.teil("1 Ruhender See über welligem Grund mit trockenem Ufer");
        Swash s = becken().fuellen(0.013, null);
        int trocken = 0, nass = 0;
        for (int j = 0; j < s.ny; j++) for (int i = 0; i < s.nx; i++) if (s.tiefe(i, j) > 0) nass++; else trocken++;
        s.laufe(30);
        double umax = s.hoechsteGeschwindigkeit(0), emax = 0;
        for (int j = 0; j < s.ny; j++)
            for (int i = 0; i < s.nx; i++) if (s.tiefe(i, j) > s.hTrocken) emax = Math.max(emax, Math.abs(s.spiegel(i, j) - 0.013));   // seit 0.3 der Spiegel (an der Front im Keil w)
        A.info("Becken", f("%s; %d Zellen nass, %d trocken; 30 s in %d Schritten", s.beschreibung(), nass, trocken, s.schritte()));
        A.hoechstens("größte Geschwindigkeit nach 30 s (nasse Zellen)", umax, 1e-10, "m/s");
        A.info("größte Abweichung des Spiegels", f("%.2e m", emax));
    }

    static void volumen() {
        A.teil("2 Volumen im geschlossenen Becken mit Wellen über trockene Hänge");
        Swash.Anfang buckel = (x, y) -> 0.013 + 0.35 * Math.exp(-((x - 5) * (x - 5) + (y - 6) * (y - 6)) / 4);
        Swash s = becken().fuellen(0.013, buckel);
        double v0 = s.volumen();
        s.laufe(60);
        double r = (s.volumen() - v0) / v0;
        A.info("ohne Versickern", f("V₀ %.6f m³, nach 60 s (%d Schritte) %.6f m³; abgeschnitten %.2e m³; höchster Auflauf %.3f m", v0, s.schritte(), s.volumen(), s.abgeschnitten(), s.auflauf()));
        A.hoechstens("Volumen nach 60 s, relativ", r, 1e-12, "");
        A.hoechstens("abgeschnittene negative Tiefen, relativ", s.abgeschnitten() / v0, 1e-12, "");
        Swash t = becken();
        t.durchlaessigkeit((x, y) -> Boden.durchlaessigkeit(0.2e-3)).grundwasser((x, y) -> 0.0);
        t.fuellen(0.013, buckel);
        double w0 = t.volumen();
        t.laufe(60);
        double rv = (t.volumen() + t.versickert() - w0) / w0;
        A.info("mit Versickern (K nach Hazen für d50 0,2 mm, Grundwasser im Spiegel)", f("K %.2e m/s; versickert %.4f m³ (%.2f %%), im Gitter %.6f m³", Boden.durchlaessigkeit(0.2e-3), t.versickert(), 100 * t.versickert() / w0, t.volumen()));
        A.hoechstens("Wasser im Gitter plus versickertes nach 60 s gegen den Anfang, relativ", rv, 1e-12, "");
    }

    // ------------------------------------------------------------------ 3 Stoker

    static void stoker() {
        A.teil("3 Dammbruch auf nassem Grund gegen Stoker (1957)");
        double hl = 1.0, hr = 0.1, xd = 50, t = 5, dx = 0.05;
        Swash s = new Swash(2000, 1, dx, 0, 0, 0);
        s.grund((x, y) -> 0.0);
        s.fuellen(0, (x, y) -> x < xd ? hl : hr);
        s.laufe(t);
        // Lösung: Verdünnung links, Plateau h_m, Stoß mit S nach rechts
        double lo = hr, hi = hl;
        for (int k = 0; k < 200; k++) {
            double m = 0.5 * (lo + hi);
            if (stokerF(m, hl, hr) > 0) lo = m; else hi = m;
        }
        double hm = 0.5 * (lo + hi), sS = Math.sqrt(G * hm * (hm + hr) / (2 * hr)), um = sS * (1 - hr / hm), cl = Math.sqrt(G * hl), cm = Math.sqrt(G * hm);
        double fehler = 0, plat = 0, xs = Double.NaN, halb = 0.5 * (hm + hr);
        int np = 0;
        double pa = xd + (um - cm) * t, pb = xd + sS * t, pl = pb - pa;
        for (int i = 0; i < s.nx; i++) {
            double x = s.x(i), xi = (x - xd) / t, he;
            if (xi < -cl) he = hl;
            else if (xi <= um - cm) he = (2 * cl - xi) * (2 * cl - xi) / (9 * G);
            else if (xi < sS) he = hm;
            else he = hr;
            fehler += Math.abs(s.tiefe(i, 0) - he) * dx;
            if (x > pa + 0.2 * pl && x < pb - 0.2 * pl) { plat += s.tiefe(i, 0); np++; }
            if (i > 0 && Double.isNaN(xs) && x > pa && s.tiefe(i - 1, 0) >= halb && s.tiefe(i, 0) < halb)
                xs = s.x(i - 1) + (s.tiefe(i - 1, 0) - halb) / (s.tiefe(i - 1, 0) - s.tiefe(i, 0)) * dx;
        }
        plat /= np;
        double l1 = fehler / ((hl - hr) * (sS + cl) * t);
        A.info("Fall", f("h_L %.1f m, h_R %.1f m, %d Zellen zu %.2f m, t = %.0f s; Stoker: h_m %.4f m, u_m %.3f m/s, Stoß %.3f m/s", hl, hr, s.nx, dx, t, hm, um, sS));
        A.hoechstens(f("Plateau gegen h_m (Mittel über %d Zellen), relativ", np), plat / hm - 1, 0.01, "");
        A.hoechstens(f("Ort des Stoßes (halbe Höhe) gegen x_d + S t, bezogen auf S t (gerechnet %.3f m, Stoker %.3f m)", xs, xd + sS * t), (xs - xd - sS * t) / (sS * t), 0.01, "");
        A.hoechstens("mittlerer Fehler der Tiefe, Σ|h − h_Stoker| Δx / ((h_L − h_R)(S + c_L) t)", l1, 0.01, "");
    }

    /** Null bei der Höhe h_m des Plateaus: 2 (c_L − c_m) gegen die Geschwindigkeit hinter dem Stoß. */
    static double stokerF(double hm, double hl, double hr) {
        double sS = Math.sqrt(G * hm * (hm + hr) / (2 * hr));
        return 2 * (Math.sqrt(G * hl) - Math.sqrt(G * hm)) - sS * (1 - hr / hm);
    }

    // ------------------------------------------------------------------ 4 Synolakis

    static void synolakis() {
        A.teil("4 Auflauf einer Einzelwelle gegen Synolakis (1987)");
        double d = 1, cot = 19.85, hd = 0.0185, H = hd * d, xs = 4, dx = 0.05;
        double gam = Math.sqrt(3 * hd / 4), x0 = d * cot, lh = acosh(Math.sqrt(20)) / gam * d, x1 = xs + x0 + lh;
        int nx = (int) Math.ceil((x1 + 30) / dx);
        Swash s = new Swash(nx, 1, dx, 0, 0, 0);
        s.hSpitze = 1e-4;
        s.grund((x, y) -> Math.max(-d, (xs - x) / cot));
        s.rand(Swash.E, Swash.Rand.OFFEN);
        s.fuellen(0, new Swash.Anfang() {
            public double stand(double x, double y) { double c = 1 / Math.cosh(gam * (x - x1) / d); return H * c * c; }

            public double[] u(double x, double y) {
                double c = 1 / Math.cosh(gam * (x - x1) / d), e = H * c * c;
                return new double[] {-2 * (Math.sqrt(G * (d + e)) - Math.sqrt(G * d)), 0};
            }
        });
        s.laufe(30);
        double r = s.auflauf() / d, ref = 2.831 * Math.sqrt(cot) * Math.pow(hd, 1.25);
        A.info("Fall", f("H/d %.4f, cot β %.2f, d %.0f m; Welle bei X₁ = X₀ + L = %.2f m vom Ufer (L = arcosh √20 / γ = %.2f m); %d Zellen zu %.2f m, 30 s in %d Schritten; Spitze ab %.0e m",
                hd, cot, d, x1 - xs, lh, nx, dx, s.schritte(), s.hSpitze));
        A.info("Auflauf", f("R/d %.4f gerechnet, %.4f nach Synolakis", r, ref));
        A.hoechstens("Auflauf gegen Synolakis, relativ", r / ref - 1, 0.05, "");
    }

    static double acosh(double x) { return Math.log(x + Math.sqrt(x * x - 1)); }

    // ------------------------------------------------------------------ 5 Carrier und Greenspan

    static void carrierGreenspan() {
        A.teil("5 Nicht brechende periodische Welle auf ebener Böschung (Carrier und Greenspan 1958)");
        // Fall nach der Messung geändert (Grenze nicht): Der Rand stand zuerst 25 m vor dem Fuß. Im Flachwasser ohne
        // Dispersion steilt sich die Welle (a/d = 0,04) auf diesem Weg auf, und R/a wuchs mit a (5 mm +1,8 %, 2 cm +6,2 %);
        // der Satz von Carrier und Greenspan gilt für die Welle am Fuß. Jetzt 2 m vor dem Fuß; der erste Fall als Auskunft.
        double[] r = cg(2, 0.02, 0.025, true), alt = cg(25, 0.02, 0.025, false);
        A.hoechstens("höchster Auflauf gegen Carrier und Greenspan, relativ (Rand 2 m vor dem Fuß)", r[0] / r[1] - 1, 0.05, "");
        A.info("erster Fall (Rand 25 m vor dem Fuß, Auskunft)", f("R %.4f m gegen %.4f m linear: %+.1f %%; die Welle steilt sich über den flachen Grund auf (R/a wächst mit a)", alt[0], alt[1], 100 * (alt[0] / alt[1] - 1)));
    }

    /** Auflauf {gerechnet, linear} mit dem Rand {@code flach} m vor dem Fuß, Amplitude a, Zellen dx. */
    static double[] cg(double flach, double a, double dx, boolean bericht) {
        double d = 0.5, cot = 20, tanb = 1 / cot, per = 20, xs = 3;
        double om = 2 * Math.PI / per, k = om / Math.sqrt(G * d), x0 = d * cot, z = 2 * k * x0;
        double rLin = 2 * a / Math.sqrt(sq(bessel(0, z)) + sq(bessel(1, z)));
        double xb = xs + x0 + flach;
        int nx = (int) Math.round(xb / dx);
        Swash s = new Swash(nx, 1, dx, 0, 0, 0);
        s.hSpitze = 1e-4;
        s.grund((x, y) -> Math.max(-d, (xs - x) * tanb));
        double rampe = 2 * per;
        s.antrieb(Swash.E, (t, x, y, aus) -> {
            double w = t < rampe ? 0.5 * (1 - Math.cos(Math.PI * t / rampe)) : 1, e = a * w * Math.sin(om * t);
            aus[0] = e;
            aus[1] = 2 * (Math.sqrt(G * (d + e)) - Math.sqrt(G * d));
            aus[2] = 0;
        });
        s.fuellen(0, null);
        double tMess = rampe + 3 * per, tEnde = tMess + 4 * per;
        s.laufe(tMess);
        s.spurenLoeschen();
        s.laufe(tEnde);
        double r = s.auflauf(), eps = om * om * r / (G * tanb * tanb);
        if (bericht) {
            A.info("Fall", f("d %.1f m, 1:%.0f (X₀ = %.0f m), T %.0f s, a %.3f m, 2kX₀ = %.3f; Rand mit Antrieb %.0f m vor dem Fuß; %d Zellen zu %.3f m; gemessen über %.0f Perioden ab %.0f s (%d Schritte)",
                    d, cot, x0, per, a, z, flach, nx, dx, (tEnde - tMess) / per, tMess, s.schritte()));
            A.info("Auflauf", f("R %.4f m gerechnet, %.4f m linear (R/a %.2f); Brechzahl ε = ω² R / (g tan²β) %.2f (bricht nicht unter 1)", r, rLin, rLin / a, eps));
        }
        return new double[] {r, rLin};
    }

    static double sq(double v) { return v * v; }

    /** Bessel J_n(x) aus dem Integral (1/π) ∫₀^π cos(n τ − x sin τ) dτ (Simpson, 4000 Teile). */
    static double bessel(int n, double x) {
        int m = 4000;
        double hh = Math.PI / m, s = 0;
        for (int i = 0; i <= m; i++) {
            double tau = i * hh, w = i == 0 || i == m ? 1 : i % 2 == 1 ? 4 : 2;
            s += w * Math.cos(n * tau - x * Math.sin(tau));
        }
        return s * hh / 3 / Math.PI;
    }

    // ------------------------------------------------------------------ 6 Zunge

    static void zunge() {
        A.teil("6 Zunge auf der Böschung (Shen und Meyer 1963)");
        double tanb = 0.05, h0 = 0.5, xd = 55, dx = 0.05, c0 = Math.sqrt(G * h0);
        Swash s = new Swash(1600, 1, dx, 0, 0, 0);
        s.hSpitze = 1e-4;
        s.grund((x, y) -> (x - xd) * tanb);
        s.rand(Swash.W, Swash.Rand.OFFEN);
        s.fuellen(0, (x, y) -> x < xd ? (x - xd) * tanb + h0 : -1e9);
        double tStern = 2 * c0 / (G * tanb);
        s.laufe(tStern + 3);
        double u0 = 2 * c0, uth = u0 - 3 * Math.sqrt(G * s.hSpitze), r = s.auflauf();
        double ref = uth * uth / (2 * G) + s.hSpitze, refSpitze = u0 * u0 / (2 * G);
        A.info("Fall", f("Schicht h₀ %.2f m auf 1:%.0f, %d Zellen zu %.2f m, %.1f s (höchster Stand nach u₀/(g tan β) = %.2f s), %d Schritte", h0, 1 / tanb, s.nx, dx, s.zeit(), tStern, s.schritte()));
        A.info("Auflauf", f("R %.4f m gerechnet (Kontur h ≥ %.0e m); Shen und Meyer %.4f m für die Kontur (u₀ %.3f m/s), %.4f m für die Spitze (u₀ = 2√(g h₀) = %.3f m/s); längs der Böschung %.2f m",
                r, s.hSpitze, ref, uth, refSpitze, u0, r / Math.sin(Math.atan(tanb))));
        A.hoechstens("Auflauf der Zunge gegen u₀² / 2g, relativ", r / ref - 1, 0.10, "");
        // Auskunft (seit der Messung): Konvergenz und Dicke der Front; die Spitze nach Ritter wird mit dem Quadrat des
        // Abstands dünn, die letzten Meter sind dünner als 1 mm
        StringBuilder b = new StringBuilder();
        for (double z : new double[] {0.25, 0.1, 0.025}) {
            double[] q = zungeBei(z, tanb, h0, xd, new double[] {1e-4, 1e-2});
            b.append(f("%s%.3f m: %+.1f %% / %+.1f %%", b.length() == 0 ? "" : "; ", z, 100 * q[0], 100 * q[1]));
        }
        A.info("Konvergenz (Auskunft): Abweichung für die Kontur 0,1 mm / 1 cm je Zellgröße", b + f("; bei %.2f m %+.1f %% / –", dx, 100 * (r / ref - 1)));
    }

    /** Abweichung des Auflaufs der Zunge für die Konturen hs bei Zellgröße dx. */
    static double[] zungeBei(double dx, double tanb, double h0, double xd, double[] hs) {
        double c0 = Math.sqrt(G * h0);
        double[] aus = new double[hs.length];
        for (int q = 0; q < hs.length; q++) {
            Swash s = new Swash((int) Math.round(80 / dx), 1, dx, 0, 0, 0);
            s.hSpitze = hs[q];
            s.grund((x, y) -> (x - xd) * tanb);
            s.rand(Swash.W, Swash.Rand.OFFEN);
            s.fuellen(0, (x, y) -> x < xd ? (x - xd) * tanb + h0 : -1e9);
            s.laufe(2 * c0 / (G * tanb) + 3);
            double uth = 2 * c0 - 3 * Math.sqrt(G * hs[q]);
            aus[q] = s.auflauf() / (uth * uth / (2 * G) + hs[q]) - 1;
        }
        return aus;
    }

    // ------------------------------------------------------------------ 7 Darcy

    static void darcy() {
        A.teil("7 Versickern in einem Tümpel gegen das Integral nach Darcy (Green und Ampt)");
        double h0 = 0.05, k = 1e-4;
        Swash s = new Swash(20, 4, 0.25, 0, 0, 0);
        s.grund((x, y) -> 0.0).durchlaessigkeit((x, y) -> k).grundwasser((x, y) -> -10.0);
        s.fuellen(h0, null);
        double fl = s.nx * s.ny * s.dx * s.dx, a = 1 - s.dTheta, c = (h0 + s.psi) * s.dTheta, v0 = s.volumen();
        double[] anteil = {0.25, 0.5, 0.75, 1.0}, tMod = new double[anteil.length];
        java.util.Arrays.fill(tMod, Double.NaN);
        double fAlt = 0, tAlt = 0;
        while (s.zeit() < 2000 && Double.isNaN(tMod[anteil.length - 1])) {
            s.schritt();
            double fNeu = s.versickert() / fl;
            for (int q = 0; q < anteil.length; q++)
                if (Double.isNaN(tMod[q]) && fNeu >= anteil[q] * h0 * (1 - 1e-9))
                    tMod[q] = fNeu > fAlt ? tAlt + (Math.min(anteil[q] * h0, fNeu) - fAlt) / (fNeu - fAlt) * (s.zeit() - tAlt) : s.zeit();
            fAlt = fNeu;
            tAlt = s.zeit();
        }
        A.info("Fall", f("Tümpel %.0f cm über Sand, K %.0e m/s, ψ %.2f m, Δθ %.2f; Grundwasser tief; Schritte %d", 100 * h0, k, s.psi, s.dTheta, s.schritte()));
        double schlimmst = 0;
        StringBuilder b = new StringBuilder();
        for (int q = 0; q < anteil.length; q++) {
            double fq = anteil[q] * h0, tRef = (fq / a - c / (a * a) * Math.log1p(a * fq / c)) / k;
            schlimmst = Math.max(schlimmst, Math.abs(tMod[q] / tRef - 1));
            b.append(f("%s%.0f %%: %.1f s (Darcy %.1f s)", q == 0 ? "" : "; ", 100 * anteil[q], tMod[q], tRef));
        }
        A.info("Zeit, bis der Anteil versickert ist", b.toString());
        A.hoechstens("größte Abweichung der Zeiten gegen das Integral, relativ", schlimmst, 0.05, "");
        A.info("Wasser im Gitter plus versickertes gegen den Anfang", f("%.1e (relativ)", (s.volumen() + s.versickert() - v0) / v0));
        Swash m = new Swash(20, 4, 0.25, 0, 0, 0);
        double km = Boden.durchlaessigkeit(0.03e-3);
        m.grund((x, y) -> 0.0).durchlaessigkeit((x, y) -> km).grundwasser((x, y) -> -10.0);
        m.fuellen(h0, null);
        m.laufe(tMod[anteil.length - 1]);
        A.info("Schlick (d50 0,03 mm) in derselben Zeit", f("K %.0e m/s; versickert %.2e m (%.3f %% des Films)", km, m.versickert() / fl, 100 * m.versickert() / (fl * h0)));
    }

    // ------------------------------------------------------------------ 8 Manning

    static void manning() {
        A.teil("8 Abbremsung einer gleichförmigen Strömung nach Manning");
        double hh = 0.1, u0 = 1, nn = 0.025, t = 10;
        Swash s = new Swash(20, 1, 0.25, 0, 0, 0);
        s.grund((x, y) -> 0.0).rauheit((x, y) -> nn);
        for (int sd = 0; sd < 4; sd++) s.rand(sd, Swash.Rand.OFFEN);
        s.fuellen(hh, new Swash.Anfang() {
            public double stand(double x, double y) { return hh; }

            public double[] u(double x, double y) { return new double[] {u0, 0}; }
        });
        s.laufe(t);
        double u = s.geschwindigkeit(10, 0)[0], ref = u0 / (1 + G * nn * nn * u0 * t / Math.pow(hh, 4.0 / 3.0));
        A.info("Fall", f("h %.2f m, u₀ %.1f m/s, n %.3f, %.0f s in %d Schritten; u %.4f m/s, Lösung %.4f m/s; Manning für Sand d50 0,2 mm: n %.4f", hh, u0, nn, t, s.schritte(), u, ref, Boden.manningSand(0.2e-3)));
        A.hoechstens("Geschwindigkeit nach 10 s gegen die Lösung, relativ", u / ref - 1, 0.01, "");
    }

    // ------------------------------------------------------------------ 9 Kosten

    static void kosten() {
        A.teil("9 Kosten auf dem Prozessor (Auskunft für S6b)");
        Swash s = new Swash(1024, 256, 0.25, 0, 0, 0);
        s.grund((x, y) -> -2 + x / 30 + 0.1 * Math.sin(y / 7)).rauheit((x, y) -> Boden.manningSand(0.2e-3))
                .durchlaessigkeit((x, y) -> Boden.durchlaessigkeit(0.2e-3)).grundwasser((x, y) -> 0.0);
        s.antrieb(Swash.W, (t, x, y, aus) -> {
            double e = 0.4 * Math.sin(2 * Math.PI * t / 6);
            aus[0] = e;
            aus[1] = 2 * (Math.sqrt(G * Math.max(0, 2 + e)) - Math.sqrt(G * 2));
            aus[2] = 0;
        });
        s.fuellen(0, null);
        for (int k = 0; k < 5; k++) s.schritt();
        double ms0 = s.rechenzeit();
        long n0 = s.schritte();
        for (int k = 0; k < 40; k++) s.schritt();
        double je = (s.rechenzeit() - ms0) / (s.schritte() - n0), dt = s.zeit() / s.schritte();
        A.info("Rechteck 1024 × 256 zu 0,25 m (262 144 Zellen)", f("%.1f ms je Schritt auf %d Kernen, Δt im Mittel %.4f s: %.0f Schritte je Sekunde Wasser; der Prozessor schafft %.2f s Wasser je s",
                je, Runtime.getRuntime().availableProcessors(), dt, 1 / dt, dt / (je / 1000)));
    }
}
