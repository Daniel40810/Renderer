package com.dan.physics.waves.nah.demo;

import com.dan.core.pruef.Attest;
import com.dan.physics.waves.Waves;
import com.dan.physics.waves.nah.Nahfeld;

import java.nio.file.Path;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfsteine des Nahfelds (Seegang Werkbuch, S2), ohne Grafikkarte, gegen Lehrbuch und gegen {@code physics.waves}:
 * <ol>
 * <li>Dispersion mit Strömung: ω = σ(k) + k U_θ, Blockieren bei U = −g/(4ω) im tiefen Wasser.</li>
 * <li>Ebener Grund: ein gleichförmiges Feld bleibt gleich; aus dem Rand gefüllt ist Hs überall die des Rands.</li>
 * <li>Shoaling: E c_g erhalten.</li>
 * <li>Refraktion: Snellius und Hs = K_s K_r.</li>
 * <li>Strömung: N (c_g + U) erhalten, mit und gegen die Wellen.</li>
 * <li>Blockieren: hinter dem Blockierpunkt nichts, der Verlust gebucht.</li>
 * <li>Bodenreibung (JONSWAP): exponentieller Abfall.</li>
 * <li>Pflanzen: Mendez und Losada (2004), H_rms = H₀ / (1 + β̃ x).</li>
 * <li>Brechen an der Tiefe: dasselbe Profil und Spektrum wie {@code physics.waves}.</li>
 * <li>Reflexion: K_r = 0,1 ξ² (Battjes 1974), Wand K_r = 1.</li>
 * <li>Beugung: Halbebene gegen Sommerfeld (Näherung nach Fresnel).</li>
 * <li>Schachtel: das feine Nahfeld im groben.</li>
 * <li>Verschieben: was bleibt, bleibt bitgleich.</li>
 * <li>Bilanz: alles gebucht.</li>
 * </ol>
 * Attest {@code docs/status/S2_Nahfeld.md}.
 */
public final class NahfeldCheck {
    static final Attest A = new Attest("com.dan.physics.waves.nah (Nahfeld)", Nahfeld.VERSION);
    static final double G = Nahfeld.G;

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        java.util.Set<String> nur = new java.util.HashSet<>(java.util.Arrays.asList(args));
        Runnable[] teile = {NahfeldCheck::dispersion, NahfeldCheck::ruhe, NahfeldCheck::shoaling, NahfeldCheck::refraktion, NahfeldCheck::stroemung,
                NahfeldCheck::blockieren, NahfeldCheck::reibung, NahfeldCheck::pflanzen, NahfeldCheck::brechen, NahfeldCheck::reflexion, NahfeldCheck::beugung,
                NahfeldCheck::schachtel, NahfeldCheck::verschieben, NahfeldCheck::bilanz, NahfeldCheck::zeit, NahfeldCheck::bindung, NahfeldCheck::anlauf,
                NahfeldCheck::partitionen};
        try {
            for (int i = 0; i < teile.length; i++) {
                if (!nur.isEmpty() && !nur.contains(String.valueOf(i + 1))) continue;
                long ta = System.nanoTime();
                teile[i].run();
                System.out.printf("  [Teil %d: %.1f s]%n", i + 1, (System.nanoTime() - ta) * 1e-9);
            }
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Rechenzeit der Prüfung", f("%.0f s", (System.nanoTime() - t0) * 1e-9));
        Path p = A.schreibe(Path.of("docs", "status", "S2_Nahfeld.md"), "S2 – Nahfeld des Seegangs, Referenz (Nahfeld " + Nahfeld.VERSION + ")");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Hilfen

    static void still(Nahfeld n) {
        n.mitWind = n.mitSchaum = n.mitBrechen = n.mitReibung = n.mitPflanzen = false;
        n.kappung = false;
    }

    /** Ein Gitter in x (eine Zeile), Tiefe je Zelle aus der Funktion. */
    interface Tiefe { double h(int i); }

    static Nahfeld zeile(int nx, double dx, int nb, double fMin, double fMax, int nd, Tiefe t) {
        Nahfeld n = new Nahfeld(nx, 1, dx, nb, fMin, fMax, nd);
        for (int i = 0; i < nx; i++) { n.grund[i] = -t.h(i); n.spiegel[i] = 0; }
        n.vorbereiten();
        return n;
    }

    /** Nur vom westlichen Rand läuft etwas ein: das Spektrum e (Varianz je Stützstelle). */
    static Nahfeld.Rand west(Nahfeld n, double[] e) {
        return (i, j, b, q) -> {
            if (i != -1) return 0;
            int c = Math.max(0, Math.min(n.ny - 1, j)) * n.nx;
            double s = n.sigma(c, b, q);
            return s > 0 ? e[b * n.nd + q] / s : 0;
        };
    }

    static double sigma(double k, double h) { return Math.sqrt(G * k * Math.tanh(k * h)); }

    static double gruppe(double omega, double h, double ut) {
        double k = Nahfeld.wellenzahl(omega, h, ut);
        return Waves.gruppe(sigma(k, h), k, h);
    }

    static double winkel(double a, double b) { return Math.abs(Math.IEEEremainder(a - b, 360)); }

    // ------------------------------------------------------------------ 1 Dispersion

    static void dispersion() {
        A.teil("1 Dispersion mit Strömung");
        double rest = 0, cgu = Double.POSITIVE_INFINITY;
        int n = 0;
        for (double h : new double[] {1.5, 4, 20, 1000})
            for (double fr : new double[] {0.06, 0.12, 0.25, 0.5})
                for (double ut : new double[] {0, 0.6, -0.6, 1.5, -1.2}) {
                    double om = 2 * Math.PI * fr, k = Nahfeld.wellenzahl(om, h, ut);
                    if (Double.isNaN(k)) continue;
                    n++;
                    rest = Math.max(rest, Math.abs(sigma(k, h) + k * ut - om) / om);
                    cgu = Math.min(cgu, Waves.gruppe(sigma(k, h), k, h) + ut);
                }
        A.pruefe(rest <= 1e-10, "ω = σ(k) + k U_θ (Rest, relativ)", f("%.2e über %d Fälle", rest, n), "≤ 1e-10");
        A.pruefe(cgu > 0, "gewählter Zweig läuft vorwärts: c_g + U_θ > 0", f("kleinster Wert %.3f m/s", cgu), "> 0");
        double om = 2 * Math.PI * 0.2, ub = G / (4 * om);
        double vor = Nahfeld.wellenzahl(om, 1000, -0.99 * ub), nach = Nahfeld.wellenzahl(om, 1000, -1.01 * ub);
        A.pruefe(!Double.isNaN(vor) && Double.isNaN(nach), "Blockieren im tiefen Wasser bei U = −g/(4ω)",
                f("f 0,2 Hz: g/(4ω) = %.3f m/s; bei 0,99 k = %.4f rad/m, bei 1,01 blockiert: %s", ub, vor, Double.isNaN(nach) ? "ja" : "nein"), "Grenze ± 1 %");
        double flach = Nahfeld.wellenzahl(2 * Math.PI * 0.1, 2, -5);
        A.pruefe(Double.isNaN(flach), "Gegenstrom über √(g h) blockiert jede Frequenz", f("h 2 m, U −5 m/s (√(g h) = %.2f m/s): %s", Math.sqrt(G * 2), Double.isNaN(flach) ? "blockiert" : "nicht blockiert"), "blockiert");
    }

    // ------------------------------------------------------------------ 2 Ebener Grund

    static void ruhe() {
        A.teil("2 Ebener Grund");
        Nahfeld n = new Nahfeld(40, 30, 10, 8, 0.06, 0.4, 24);
        for (int c = 0; c < n.nc; c++) { n.grund[c] = -10; n.spiegel[c] = 0; }
        n.vorbereiten();
        still(n);
        double[] e = n.jonswap(1.2, 7, 30, 10);
        Nahfeld.Ziel z = (x, y, out) -> System.arraycopy(e, 0, out, 0, e.length);
        n.setze(z);
        n.rand = n.randAus(z);
        double h0 = n.hs(0), w0 = n.wirkung();
        for (int t = 0; t < 20; t++) n.schritt(10);
        double ab = 0;
        for (int c = 0; c < n.nc; c++) ab = Math.max(ab, Math.abs(n.hs(c) / h0 - 1));
        A.pruefe(ab <= 1e-9, "gleichförmiges Feld bleibt gleich (Rand wie Feld, 200 s, Beugung an)", f("größte Abweichung von Hs %.2e (Hs %.3f m)", ab, h0), "≤ 1e-9");
        A.pruefe(Math.abs(n.wirkung() / w0 - 1) <= 1e-9, "Wirkung im Gebiet bleibt", f("%.2e relativ", Math.abs(n.wirkung() / w0 - 1)), "≤ 1e-9");

        Nahfeld m = zeile(200, 10, 8, 0.06, 0.4, 24, i -> 10);
        still(m);
        double[] e2 = m.jonswap(1.0, 8, 0, 10);
        m.rand = west(m, e2);
        int it = m.stationaer(10, 400, 1e-7);
        double soll = 0;
        for (int b = 0; b < m.nb; b++) for (int q = 0; q < m.nd; q++) if (m.cosT[q] > 1e-9) soll += e2[b * m.nd + q];
        soll = 4 * Math.sqrt(soll);
        double gr = 0;
        for (int c = 0; c < m.nc; c++) gr = Math.max(gr, Math.abs(m.hs(c) / soll - 1));
        A.pruefe(gr <= 0.01, "aus dem Rand gefüllt: Hs überall die des einlaufenden Spektrums", f("größte Abweichung %.2e (%d Schritte)", gr, it), "± 1 %");
    }

    // ------------------------------------------------------------------ 3 Shoaling

    static void shoaling() {
        A.teil("3 Shoaling");
        Nahfeld n = zeile(200, 10, 1, 0.095, 0.105, 36, i -> 20 - 18 * (i + 0.5) / 200);
        still(n);
        n.rand = west(n, n.einzeln(1.0, 0, 0));
        int it = n.stationaer(10, 600, 1e-8);
        double om = n.omega[0], cg0 = gruppe(om, n.tiefe(0), 0), gr = 0;
        for (int c = 0; c < n.nc; c++) {
            double soll = Math.sqrt(cg0 / gruppe(om, n.tiefe(c), 0));
            gr = Math.max(gr, Math.abs(n.hs(c) / soll - 1));
        }
        A.pruefe(gr <= 0.02, "Hs / Hs₀ = √(c_g₀ / c_g), 20 m bis 2 m, 0,1 Hz", f("größte Abweichung %.2e; Hs am flachen Ende %.4f m (%d Schritte)", gr, n.hs(n.nc - 1), it), "± 2 %");
    }

    // ------------------------------------------------------------------ 4 Refraktion

    /** Refraktion an geraden Tiefenlinien (30 m bis 3 m auf 3 km, 0,1 Hz, Einfall 30°): {größte Abweichung der Richtung (°), von Hs (relativ), Schritte}. */
    static double[] snellius(int nd, double breite, StringBuilder sb) {
        Nahfeld n = zeile(300, 10, 1, 0.095, 0.105, nd, i -> 30 - 27 * (i + 0.5) / 300);
        n.periodischY = true;
        n.vorbereiten();
        still(n);
        double[] e = new double[nd];
        double sum = 0, th0 = Math.toRadians(30);
        for (int q = 0; q < nd; q++) {
            double d = Math.IEEEremainder(n.theta[q] - th0, 2 * Math.PI);
            e[q] = breite == 0 ? (Math.abs(d) <= 0.5 * n.dTheta + 1e-12 ? 1 : 0) : Math.pow(Math.cos(0.5 * d), 2 * breite);
            sum += e[q];
        }
        for (int q = 0; q < nd; q++) e[q] /= 16 * sum;                         // Hs 1 m
        if (breite == 0) for (int q = 0; q < nd; q++) if (e[q] > 0) th0 = n.theta[q];   // die nächste Richtung
        n.rand = west(n, e);
        int it = n.stationaer(10, 800, 1e-8);
        double om = n.omega[0], h0 = n.tiefe(0), k0 = Nahfeld.wellenzahl(om, h0, 0), c0 = om / k0;
        double cg0 = Waves.gruppe(om, k0, h0);
        double grW = 0, grH = 0;
        for (double hz : new double[] {15, 8, 5}) {
            int c = 0;
            while (c < n.nc - 1 && n.tiefe(c) > hz) c++;
            double h = n.tiefe(c), k = Nahfeld.wellenzahl(om, h, 0), th = Math.asin(Math.sin(th0) * (om / k) / c0);
            double hs = Math.sqrt(cg0 * Math.cos(th0) / (Waves.gruppe(om, k, h) * Math.cos(th)));
            grW = Math.max(grW, winkel(n.richtung(c), Math.toDegrees(th)));
            grH = Math.max(grH, Math.abs(n.hs(c) / hs - 1));
            if (sb != null) sb.append(f("%s%.1f m: %.2f° (Snellius %.2f°), Hs %.3f (%.3f)", sb.length() > 0 ? "; " : "", h, n.richtung(c), Math.toDegrees(th), n.hs(c), hs));
        }
        return new double[] {grW, grH, it};
    }

    static void refraktion() {
        A.teil("4 Refraktion an geraden Tiefenlinien");
        StringBuilder sb = new StringBuilder();
        double[] r = snellius(72, 100, sb);
        A.pruefe(r[0] <= 0.5, "mittlere Richtung nach Snellius, 30° auf 30 m bis 3 m, 0,1 Hz, cos^200 (σ_θ 8°), 72 Richtungen", sb + f(" (%.0f Schritte)", r[2]), "± 0,5°");
        A.pruefe(r[1] <= 0.03, "Hs = K_s K_r = √(c_g₀ cos θ₀ / (c_g cos θ))", f("größte Abweichung %.2e", r[1]), "± 3 %");
        double[] a = snellius(72, 0, null), b = snellius(32, 100, null), c = snellius(32, 0, null);
        A.info("Auflösung der Richtungen (Abweichung der Richtung)", f("eine Richtung allein: %.2f° bei 72, %.2f° bei 32 Richtungen (Einfall 33,75°); cos^200: %.2f° bei 32 Richtungen", a[0], c[0], b[0]));
    }

    // ------------------------------------------------------------------ 5 Strömung

    static void stroemung() {
        A.teil("5 Strömung: Wirkung erhalten");
        for (double ue : new double[] {-0.8, 0.8}) {
            Nahfeld n = zeile(200, 10, 1, 0.115, 0.125, 36, i -> 50);
            for (int i = 0; i < n.nx; i++) n.u[i] = i < 40 ? 0 : i > 160 ? ue : ue * (i - 40) / 120.0;
            n.vorbereiten();
            still(n);
            n.rand = west(n, n.einzeln(1.0, 0, 0));
            int it = n.stationaer(10, 600, 1e-8);
            double om = n.omega[0], h = 50;
            double k0 = Nahfeld.wellenzahl(om, h, 0), s0 = sigma(k0, h), cg0 = Waves.gruppe(s0, k0, h);
            double k = Nahfeld.wellenzahl(om, h, ue), s = sigma(k, h), cg = Waves.gruppe(s, k, h);
            double soll = Math.sqrt(s * cg0 / (s0 * (cg + ue))) * n.hs(20);
            int c = 180;
            A.pruefe(Math.abs(n.hs(c) / soll - 1) <= 0.02, f("N (c_g + U) erhalten, U %+.1f m/s (%s), 0,12 Hz, 50 m", ue, ue < 0 ? "gegen die Wellen" : "mit den Wellen"),
                    f("Hs %.4f m gegen %.4f m; σ/ω %.3f (%d Schritte)", n.hs(c), soll, s / om, it), "± 2 %");
        }
    }

    // ------------------------------------------------------------------ 6 Blockieren

    static void blockieren() {
        A.teil("6 Blockieren");
        Nahfeld n = zeile(200, 10, 1, 0.195, 0.205, 36, i -> 200);
        for (int i = 0; i < n.nx; i++) n.u[i] = i < 40 ? 0 : i > 160 ? -2.5 : -2.5 * (i - 40) / 120.0;
        n.vorbereiten();
        still(n);
        n.mitSchaum = true;                                                    // vor dem Blockierpunkt steilen die Wellen auf
        n.rand = west(n, n.einzeln(1.0, 0, 0));
        double w0 = n.wirkung();
        for (int t = 0; t < 300; t++) n.schritt(5);
        int erste = -1;
        for (int c = 0; c < n.nc; c++) if (Double.isNaN(n.wellenzahl(c, 0, 0))) { erste = c; break; }
        double ub = G / (4 * n.omega[0]), dahinter = 0, davor = 0;
        for (int c = Math.max(0, erste); c < n.nc; c++) dahinter = Math.max(dahinter, n.hs(c));
        for (int c = 0; c < Math.max(0, erste); c++) davor = Math.max(davor, n.hs(c));
        A.pruefe(erste > 0 && Math.abs(-n.u[erste - 1] - ub) <= 0.05 && dahinter <= 0.01 * davor, "ab dem Blockierpunkt keine Energie in der blockierten Richtung",
                f("blockiert ab Zelle %d bei U %.3f m/s (−g/(4ω) = −%.3f); Hs davor höchstens %.3f m, dahinter %.1g m (schräge Anteile, aus der Beugung)", erste, erste >= 0 ? n.u[erste] : Double.NaN, ub, davor, dahinter),
                "Hs dahinter ≤ 1 % davor; Grenze ± 0,05 m/s");
        double ein = n.bilanz[0], weg = n.bilanz[7];
        A.pruefe(weg < 0, "der Verlust ist als blockiert gebucht", f("Rand ein %.4g, blockiert %.4g, Schaumkronen %.4g m²·s·m²", ein, weg, n.bilanz[2]), "< 0");
        double sum = 0;
        for (double x : n.bilanz) sum += x;
        A.pruefe(Math.abs(n.wirkung() - w0 - sum) <= 1e-9 * Math.max(Math.abs(ein), n.wirkung()), "Bilanz geschlossen", f("Rest %.2e", Math.abs(n.wirkung() - w0 - sum) / Math.max(Math.abs(ein), n.wirkung())), "≤ 1e-9");
    }

    // ------------------------------------------------------------------ 7 Bodenreibung

    static void reibung() {
        A.teil("7 Bodenreibung (JONSWAP)");
        Nahfeld n = zeile(200, 10, 1, 0.095, 0.105, 36, i -> 5);
        still(n);
        n.mitReibung = true;
        n.rand = west(n, n.einzeln(1.0, 0, 0));
        int it = n.stationaer(10, 600, 1e-8);
        double om = n.omega[0], h = 5, k = Nahfeld.wellenzahl(om, h, 0), cg = Waves.gruppe(om, k, h), sh = Math.sinh(k * h);
        double gam = n.cReibung * om * om / (G * G * sh * sh), gr = 0;
        for (int c = 1; c < n.nc; c++) {
            double soll = n.hs(0) * Math.exp(-0.5 * gam * (c * n.dx) / cg);
            gr = Math.max(gr, Math.abs(n.hs(c) / soll - 1));
        }
        A.pruefe(gr <= 0.02, "Hs = Hs₀ exp(−C_f σ² x / (2 g² sinh²(kh) c_g)), 5 m, 0,1 Hz", f("größte Abweichung %.2e; Hs nach 2 km %.4f m (%d Schritte)", gr, n.hs(n.nc - 1), it), "± 2 %");
    }

    // ------------------------------------------------------------------ 8 Pflanzen

    static void pflanzen() {
        A.teil("8 Pflanzen (Mendez und Losada 2004)");
        double h = 1.0, cd = 1.0, bv = 0.004, nv = 500, hv = 0.3, hrms0 = 0.3;
        Nahfeld n = zeile(300, 0.5, 1, 0.12, 0.13, 36, i -> h);
        still(n);
        n.mitPflanzen = true;
        n.pflanzen = new double[n.nc];
        n.halm = new double[n.nc];
        java.util.Arrays.fill(n.pflanzen, cd * bv * nv);
        java.util.Arrays.fill(n.halm, hv);
        n.rand = west(n, n.einzeln(Math.sqrt(2) * hrms0, 0, 0));             // Hs = √2 H_rms
        int it = n.stationaer(2, 1500, 1e-10);
        double om = n.omega[0], k = Nahfeld.wellenzahl(om, h, 0), kh = k * h, sa = Math.sinh(k * hv), s = Math.sinh(kh);
        double h0 = n.hs(0) / Math.sqrt(2);
        double beta = 1 / (3 * Math.sqrt(Math.PI)) * cd * bv * nv * k * h0 * (sa * sa * sa + 3 * sa) / ((Math.sinh(2 * kh) + 2 * kh) * s);
        double gr = 0;
        for (int c = 1; c < n.nc; c++) {
            double soll = h0 / (1 + beta * c * n.dx);
            gr = Math.max(gr, Math.abs(n.hs(c) / Math.sqrt(2) / soll - 1));
        }
        A.pruefe(gr <= 0.02, "H_rms = H₀ / (1 + β̃ x) über 150 m Salzwiese (h 1 m, Halme 0,3 m, C_D b_v N_v 2/m, Δx 0,5 m)",
                f("größte Abweichung %.2e; H_rms %.3f → %.3f m (%d Schritte)", gr, h0, n.hs(n.nc - 1) / Math.sqrt(2), it), "± 2 %");
    }

    // ------------------------------------------------------------------ 9 Brechen gegen physics.waves

    static Waves wavesProfil(int nx, double dx, int nf, int nd, Tiefe t, double[] ref, double dt, double bis) {
        Waves w = new Waves(nx, 1, dx, dx, nf, 0.04, 0.6, nd);
        w.periodischY = true;
        for (int i = 0; i < nx; i++) w.tiefe[i] = t.h(i);
        w.mitWind = w.mitSchaum = w.mitQuad = w.mitReibung = false;
        w.vorbereiten();
        if (ref != null) {
            w.randW = (i, j, m, q) -> ref[m * nd + q];
            for (int s = 0; s < Math.round(bis / dt); s++) w.schritt(dt);
        }
        return w;
    }

    static void brechen() {
        A.teil("9 Brechen an der Tiefe, gegen physics.waves");
        int nx = 150, nf = 28, nd = 36;
        double dx = 10;
        Tiefe t = i -> 8 - 7.7 * (i + 0.5) / nx;
        Waves w0 = wavesProfil(nx, dx, nf, nd, t, null, 0, 0);
        w0.jonswap(0, 1.5, 8, 0, 20);
        double[] ref = new double[nf * nd];
        System.arraycopy(w0.e, 0, ref, 0, ref.length);
        Waves w5 = wavesProfil(nx, dx, nf, nd, t, ref, 5, 1500), w05 = wavesProfil(nx, dx, nf, nd, t, ref, 0.5, 1500);

        double st = Math.pow(0.6 / 0.04, 1.0 / (nf - 1));
        double[] e = new double[nf * nd];
        for (int m = 0; m < nf; m++) for (int q = 0; q < nd; q++) e[m * nd + q] = ref[m * nd + q] * w0.df[m] * w0.dTheta;
        Nahfeld[] n = new Nahfeld[2];
        for (int v = 0; v < 2; v++) {
            n[v] = zeile(nx, dx, nf, 0.04 / Math.sqrt(st), 0.6 * Math.sqrt(st), nd, t);
            still(n[v]);
            n[v].mitBrechen = true;
            n[v].kappung = true;
            n[v].mitBeugung = false;                                           // wie physics.waves
            n[v].quellenJeTeilschritt = v == 1;
            n[v].rand = west(n[v], e);
            for (int s = 0; s < 300; s++) n[v].schritt(5);
        }
        double[] gr = new double[3];
        StringBuilder sb = new StringBuilder();
        for (double hz : new double[] {6, 4, 2, 1, 0.5}) {
            int c = 0;
            while (c < nx - 1 && t.h(c) > hz) c++;
            gr[0] = Math.max(gr[0], Math.abs(n[0].hs(c) / w5.hs(c) - 1));
            gr[1] = Math.max(gr[1], Math.abs(n[1].hs(c) / w05.hs(c) - 1));
            gr[2] = Math.max(gr[2], Math.abs(w5.hs(c) / w05.hs(c) - 1));
            sb.append(f("%s%.2f m: %.3f / %.3f · %.3f / %.3f", sb.length() > 0 ? "; " : "", t.h(c), n[0].hs(c), w5.hs(c), n[1].hs(c), w05.hs(c)));
        }
        A.pruefe(gr[0] <= 0.01, "gleiche Rechnung: Quellen einmal je Schritt von 5 s wie physics.waves (Battjes–Janssen mit Kappung, JONSWAP 1,5 m / 8 s, 8 m bis 0,3 m)",
                f("größte Abweichung %.2e", gr[0]), "± 1 %");
        A.pruefe(gr[1] <= 0.05, "Quellen je Teilschritt (Nahfeld, 5 s) gegen physics.waves mit kleinem Schritt (0,5 s)", f("größte Abweichung %.2e", gr[1]), "± 5 %");
        A.info("Hs je Tiefe: Nahfeld je Schritt / Waves 5 s · Nahfeld je Teilschritt / Waves 0,5 s", sb.toString());
        A.info("Aufteilung der Schritte in physics.waves (5 s gegen 0,5 s, 10-m-Zellen)", f("größte Abweichung von Hs %.1f %%", 100 * gr[2]));
    }

    // ------------------------------------------------------------------ 10 Reflexion

    static void reflexion() {
        A.teil("10 Reflexion an der Kante");
        for (boolean wand : new boolean[] {false, true}) {
            Nahfeld n = zeile(100, 20, 1, 0.095, 0.105, 36, i -> 2);
            n.grund[99] = 0.5;
            if (wand) { n.wand = new boolean[n.nc]; n.wand[99] = true; }
            n.vorbereiten();
            still(n);
            n.mitBeugung = false;
            n.rand = west(n, n.einzeln(0.5, 0, 0));
            int it = n.stationaer(10, 800, 1e-9);
            int c = 50;
            double kr = n.hsNach(c, 180) / n.hsNach(c, 0);
            double soll;
            if (wand) soll = 1;
            else {
                double tana = (n.grund[99] - n.grund[98]) / n.dx, l0 = G / (2 * Math.PI * n.f[0] * n.f[0]);
                double xi = tana / Math.sqrt(n.hsNach(98, 0) / l0);
                soll = Math.min(1, 0.1 * xi * xi);
            }
            A.pruefe(Math.abs(kr / soll - 1) <= (wand ? 0.01 : 0.05), wand ? "Wand: K_r = 1" : "Böschung: K_r = 0,1 ξ² (Battjes 1974), tan α 0,125, Hs 0,5 m, 0,1 Hz",
                    f("K_r %.4f gegen %.4f (%d Schritte)", kr, soll, it), wand ? "± 1 %" : "± 5 %");
        }
    }

    // ------------------------------------------------------------------ 11 Beugung

    /** Fresnel-Integrale C(w), S(w) (Simpson). */
    static double[] fresnel(double w) {
        int m = 2000;
        double h = w / m, c = 0, s = 0;
        for (int i = 0; i <= m; i++) {
            double t = i * h, g = i == 0 || i == m ? 1 : i % 2 == 1 ? 4 : 2;
            c += g * Math.cos(0.5 * Math.PI * t * t);
            s += g * Math.sin(0.5 * Math.PI * t * t);
        }
        return new double[] {c * h / 3, s * h / 3};
    }

    /** Sommerfeld, Näherung nach Fresnel: Amplitude hinter einer Halbebene, w = x √(2/(L r)), x > 0 im Licht. */
    static double sommerfeld(double w) {
        double[] cs = fresnel(w);
        return Math.sqrt(0.5 * ((cs[0] + 0.5) * (cs[0] + 0.5) + (cs[1] + 0.5) * (cs[1] + 0.5)));
    }

    static void beugung() {
        A.teil("11 Beugung an einer Halbebene");
        double[] rms = new double[2], linie = new double[2];
        double l = 0;
        int jb = 40, ib = 80;
        for (int mit = 0; mit < 2; mit++) {
            Nahfeld n = new Nahfeld(160, 200, 5, 1, 0.12, 0.13, 36);
            for (int c = 0; c < n.nc; c++) { n.grund[c] = -10; n.spiegel[c] = 0; }
            for (int i = 0; i < ib; i++) n.grund[jb * n.nx + i] = 2;            // Wellenbrecher bis x = 400 m
            n.vorbereiten();
            still(n);
            n.mitReflexion = false;
            n.mitBeugung = mit == 1;
            int q = 9;                                                         // nach +y
            n.rand = (i, j, b, qq) -> j == -1 && qq == q ? (1.0 / 16) / n.sigma(i, 0, q) : 0;
            int it = n.stationaer(2, 1500, 1e-6);
            double k = n.wellenzahl(0, 0, q);
            l = 2 * Math.PI / k;
            int jr = jb + (int) Math.round(3 * l / n.dx);                        // drei Wellenlängen hinter dem Brecher
            double r = (jr - jb) * n.dx, s2 = 0;
            int z = 0;
            for (int i = 0; i < n.nx; i++) {
                double x = n.x(i) - ib * n.dx, w = x * Math.sqrt(2 / (l * r));
                if (w < -2.5 || w > 1.0) continue;
                double d = n.hs(jr * n.nx + i) - sommerfeld(w);
                s2 += d * d;
                z++;
            }
            rms[mit] = Math.sqrt(s2 / z);
            double e1 = n.m0(jr * n.nx + ib - 1), e2 = n.m0(jr * n.nx + ib);
            linie[mit] = Math.sqrt(0.5 * (e1 + e2)) * 4;
            A.info(mit == 1 ? "mit Beugung" : "ohne Beugung", f("Kd an der Schattenlinie %.3f; Abweichung von Sommerfeld (RMS, −2,5 ≤ w ≤ 1) %.3f; %d Schritte", linie[mit], rms[mit], it));
        }
        A.pruefe(Math.abs(linie[1] / 0.5 - 1) <= 0.15, "Kd an der Schattenlinie gegen Sommerfeld 0,5 (drei Wellenlängen hinter dem Kopf, L " + f("%.0f m", l) + ")",
                f("%.3f (ohne Beugung %.3f)", linie[1], linie[0]), "± 15 %");
        A.pruefe(rms[1] < rms[0] && rms[1] <= 0.1, "Verlauf quer zur Schattenlinie gegen Sommerfeld (Fresnel), RMS von Kd", f("%.3f mit, %.3f ohne Beugung", rms[1], rms[0]), "≤ 0,1 und besser als ohne");
    }

    // ------------------------------------------------------------------ 12 Schachtel

    static Nahfeld grobFeld(double[] e) {
        Nahfeld g = new Nahfeld(30, 30, 100, 8, 0.06, 0.4, 24);
        for (int c = 0; c < g.nc; c++) { g.grund[c] = -15; g.spiegel[c] = 0; }
        g.vorbereiten();
        still(g);
        Nahfeld.Ziel z = (x, y, out) -> System.arraycopy(e, 0, out, 0, e.length);
        g.setze(z);
        g.rand = g.randAus(z);
        return g;
    }

    static void schachtel() {
        A.teil("12 Schachtel");
        Nahfeld probe = new Nahfeld(1, 1, 1, 8, 0.06, 0.4, 24);
        double[] e = probe.jonswap(1.5, 8, 20, 8);
        Nahfeld g = grobFeld(e);
        for (int t = 0; t < 5; t++) g.schritt(20);
        Nahfeld fe = new Nahfeld(40, 40, 25, 8, 0.06, 0.4, 24);
        fe.x0 = 1000; fe.y0 = 1000;
        for (int c = 0; c < fe.nc; c++) { fe.grund[c] = -15; fe.spiegel[c] = 0; }
        fe.vorbereiten();
        still(fe);
        fe.rand = Nahfeld.schachtel(g, fe);
        int it = fe.stationaer(5, 800, 1e-8);
        double soll = g.hs(15 * 30 + 15), gr = 0;
        for (int c = 0; c < fe.nc; c++) gr = Math.max(gr, Math.abs(fe.hs(c) / soll - 1));
        A.pruefe(gr <= 0.01, "feines Nahfeld (25 m) aus dem groben (100 m) gefüllt: Hs wie im groben", f("größte Abweichung %.2e (Hs %.3f m, %d Schritte)", gr, soll, it), "± 1 %");
    }

    // ------------------------------------------------------------------ 13 Verschieben

    static void verschieben() {
        A.teil("13 Verschieben");
        Nahfeld n = new Nahfeld(30, 20, 10, 6, 0.06, 0.4, 24);
        for (int c = 0; c < n.nc; c++) { n.grund[c] = -8 - 0.05 * (c % 30); n.spiegel[c] = 0; n.u[c] = 0.2; }
        n.vorbereiten();
        double[] e = n.jonswap(1.0, 7, 45, 8);
        Nahfeld.Ziel z = (x, y, out) -> { for (int i = 0; i < out.length; i++) out[i] = e[i] * (1 + 0.001 * x - 0.0005 * y); };
        n.setze(z);
        n.rand = n.randAus(z);
        for (int t = 0; t < 5; t++) n.schritt(5);
        double[] alt = n.n.clone();
        double x0 = n.x0, y0 = n.y0;
        int di = 3, dj = -2, m = n.nb * n.nd;
        n.verschieben(di, dj);
        boolean gleich = true;
        int zellen = 0;
        for (int j = 0; j < n.ny; j++)
            for (int i = 0; i < n.nx; i++) {
                int is = i + di, js = j + dj;
                if (is < 0 || is >= n.nx || js < 0 || js >= n.ny) continue;
                zellen++;
                for (int x = 0; x < m; x++) if (n.n[(j * n.nx + i) * m + x] != alt[(js * n.nx + is) * m + x]) gleich = false;
            }
        A.pruefe(gleich && n.x0 == x0 + di * n.dx && n.y0 == y0 + dj * n.dx, "Wirkung der bleibenden Zellen bitgleich, Ecke gewandert", f("%d Zellen, Ecke (%.0f, %.0f) m", zellen, n.x0, n.y0), "bitgleich");
        for (int c = 0; c < n.nc; c++) n.grund[c] = -8 - 0.05 * ((c % 30) + di);
        n.vorbereiten();
        n.nachVerschieben(z);
        double gr = 0;
        double[] ez = new double[m];
        for (int j = 0; j < n.ny; j++)
            for (int i = 0; i < n.nx; i++) {
                int is = i + di, js = j + dj;
                if (is >= 0 && is < n.nx && js >= 0 && js < n.ny) continue;
                z.varianz(n.x(i), n.y(j), ez);
                double s = 0;
                for (double v : ez) s += v;
                gr = Math.max(gr, Math.abs(n.hs(j * n.nx + i) / (4 * Math.sqrt(s)) - 1));
            }
        A.pruefe(gr <= 1e-12, "neue Zellen bekommen das Ziel", f("größte Abweichung von Hs %.1e", gr), "≤ 1e-12");
    }

    // ------------------------------------------------------------------ 14 Bilanz

    static void bilanz() {
        A.teil("14 Bilanz");
        Nahfeld n = new Nahfeld(50, 40, 20, 8, 0.06, 0.45, 32);
        for (int j = 0; j < n.ny; j++)
            for (int i = 0; i < n.nx; i++) {
                int c = j * n.nx + i;
                double x = n.x(i) - n.x0, y = n.y(j) - n.y0;
                n.grund[c] = -10 + 11 * x / 1000;                               // Böschung nach Osten, am Ende trocken
                double r = Math.hypot(x - 600, y - 400);
                if (r < 80) n.grund[c] = 1.5;                                   // Insel
                n.spiegel[c] = 0.4;
                n.u[c] = y > 600 ? -3.0 : 0.3;                                  // im Norden blockierender Gegenstrom
                n.v[c] = 0.1;
                n.windU[c] = 14; n.windV[c] = 4;
            }
        n.pflanzen = new double[n.nc];
        n.halm = new double[n.nc];
        for (int c = 0; c < n.nc; c++) if (n.grund[c] > -1.5 && n.grund[c] < 0.3) { n.pflanzen[c] = 3; n.halm[c] = 0.3; }
        n.wand = new boolean[n.nc];
        n.wand[20 * n.nx + 30] = true;
        n.vorbereiten();
        double[] e = n.jonswap(1.4, 7, 10, 8);
        Nahfeld.Ziel z = (x, y, out) -> System.arraycopy(e, 0, out, 0, e.length);
        n.setze(z);
        n.rand = n.randAus(z);
        double m0z = 0;
        for (double x : e) m0z += x;
        n.bindungM0 = new double[n.nc];
        java.util.Arrays.fill(n.bindungM0, 0.8 * m0z);
        n.bindungBlock = 5;
        n.bindungZeit = 120;
        java.util.Arrays.fill(n.bilanz, 0);
        double w0 = n.wirkung();
        for (int t = 0; t < 30; t++) {
            if (t == 15) {                                                     // Ebbe: 0,5 m tiefer, Zellen fallen trocken
                for (int c = 0; c < n.nc; c++) n.spiegel[c] = -0.1;
                n.vorbereiten();
            }
            n.schritt(5);
        }
        double sum = 0, betrag = 0;
        StringBuilder sb = new StringBuilder();
        for (int p = 0; p < n.bilanz.length; p++) {
            sum += n.bilanz[p];
            betrag += Math.abs(n.bilanz[p]);
            sb.append(f("%s%s %.3g", p > 0 ? ", " : "", Nahfeld.BILANZ[p], n.bilanz[p]));
        }
        double rest = Math.abs(n.wirkung() - w0 - sum) / Math.max(betrag, n.wirkung());
        A.pruefe(rest <= 1e-9, "Änderung der Wirkung = Summe der Posten (Wind, alle Senken, Kante, Wand, Insel, Blockieren, Trockenfallen, Beugung, Bindung)", f("Rest %.2e", rest), "≤ 1e-9");
        A.info("Posten (m²·s·m²)", sb.toString());
        boolean alle = n.bilanz[1] > 0 && n.bilanz[2] < 0 && n.bilanz[3] < 0 && n.bilanz[4] < 0 && n.bilanz[5] < 0 && n.bilanz[6] < 0 && n.bilanz[7] < 0 && n.bilanz[8] < 0
                && n.bilanz[10] != 0;
        A.pruefe(alle, "jeder Posten hat gearbeitet und das richtige Vorzeichen", alle ? "Wind > 0, Senken, Kante, blockiert, trocken < 0, Bindung ≠ 0" : sb.toString(), "alle");
    }

    // ------------------------------------------------------------------ Rechenzeit

    static void zeit() {
        A.teil("15 Rechenzeit der Referenz");
        Nahfeld n = new Nahfeld(128, 128, 2, 8, 0.07, 0.6, 32);
        for (int c = 0; c < n.nc; c++) { n.grund[c] = -6 + 0.01 * (c % 128); n.spiegel[c] = 0; n.u[c] = 0.3; n.windU[c] = 12; }
        n.vorbereiten();
        double[] e = n.jonswap(1.2, 6, 0, 8);
        Nahfeld.Ziel z = (x, y, out) -> System.arraycopy(e, 0, out, 0, e.length);
        n.setze(z);
        n.rand = n.randAus(z);
        n.schritt(0.5);
        long t0 = System.nanoTime();
        int s = 5;
        for (int t = 0; t < s; t++) n.schritt(0.5);
        double ms = (System.nanoTime() - t0) * 1e-6 / s;
        A.info("128 × 128 Zellen à 2 m, 8 Bänder × 32 Richtungen (524 288 Stützstellen), Schritt 0,5 s", f("%.0f ms je Schritt (%d Teilschritte), CPU in double, %d Kerne", ms, n.teilschritte, Runtime.getRuntime().availableProcessors()));
    }

    // ------------------------------------------------------------------ 16 Bindung

    static void bindung() {
        A.teil("16 Bindung an das grobe Feld");
        double h = 3, tau = 1000;
        Nahfeld n = zeile(300, 20, 1, 0.095, 0.105, 36, i -> h);
        still(n);
        n.mitReibung = true;
        n.mitBeugung = false;
        double[] e = n.einzeln(1.0, 0, 0);
        n.rand = west(n, e);
        n.bindungM0 = new double[n.nc];
        java.util.Arrays.fill(n.bindungM0, 1.0 / 16);
        n.bindungBlock = 1;
        n.bindungZeit = tau;
        int it = n.stationaer(20, 1500, 1e-10);
        double om = n.omega[0], k = Nahfeld.wellenzahl(om, h, 0), cg = Waves.gruppe(om, k, h), sh = Math.sinh(k * h);
        double gam = n.cReibung * om * om / (G * G * sh * sh), mz = 1.0 / 16, e0 = n.m0(0);
        double eq = mz / (1 + gam * tau), lam = (gam + 1 / tau) / cg, gr = 0;
        for (int c = 1; c < n.nc; c++) {
            double soll = eq + (e0 - eq) * Math.exp(-lam * c * n.dx);
            gr = Math.max(gr, Math.abs(Math.sqrt(n.m0(c) / soll) - 1));
        }
        A.pruefe(gr <= 0.02, "Bindung gegen Reibung: m₀ = m₀,eq + (m₀(0) − m₀,eq) exp(−(γ + 1/τ) x / c_g), m₀,eq = m₀,Ziel / (1 + γ τ)",
                f("größte Abweichung von Hs %.2e; Hs am Ende %.3f m (Gleichgewicht %.3f m, ohne Bindung gegen 0); τ %.0f s, γ τ %.2f (%d Schritte)", gr, n.hs(n.nc - 1), 4 * Math.sqrt(eq), tau, gam * tau, it), "± 2 %");
        // dasselbe mit der Bindung an das Spektrum (B20): bei einer Stützstelle dieselbe Gleichung
        double[] alt = n.n.clone();
        n.bindungSpektrum = new double[n.nn];
        for (int c = 0; c < n.nc; c++) n.bindungSpektrum[n.ix(c, 0, 0)] = 1.0 / 16;
        java.util.Arrays.fill(n.n, 0);
        int it2 = n.stationaer(20, 1500, 1e-10);
        double gr2 = 0;
        for (int c = 0; c < n.nc; c++) gr2 = Math.max(gr2, Math.abs(n.n[c * n.nb * n.nd] / alt[c * n.nb * n.nd] - 1));
        A.pruefe(gr2 <= 0.02, "Bindung an das Spektrum (B20): bei einer Stützstelle dieselbe Lösung", f("größte Abweichung der Wirkung %.2e (%d Schritte)", gr2, it2), "± 2 %");
        // Watt und Rinne bleiben frei (SF17 A): ein Block über 2 m und gleichmäßig ist gebunden, einer mit 1,5 m und einer mit Rinne frei
        Nahfeld w = new Nahfeld(30, 10, 10, 2, 0.08, 0.2, 12);
        for (int c = 0; c < w.nc; c++) {
            int i = c % 30;
            w.grund[c] = i < 10 ? -5 : i < 20 ? -1.5 : (c / 30) % 2 == 0 ? -8 : -2;   // tief, Watt, Rinne
        }
        w.bindungBlock = 10;
        w.vorbereiten();
        boolean[] fr = w.freiVonBindung();
        A.pruefe(!fr[5 * 30 + 4] && fr[5 * 30 + 14] && fr[5 * 30 + 24], "gebunden nur tief und gleichmäßig (über 2 m, Streuung unter 25 %)",
                f("tief 5 m: %s; Watt 1,5 m: %s; Rinne 2 m / 8 m: %s", fr[5 * 30 + 4] ? "frei" : "gebunden", fr[5 * 30 + 14] ? "frei" : "gebunden", fr[5 * 30 + 24] ? "frei" : "gebunden"),
                "gebunden, frei, frei");
        // Frei bleiben Blöcke an Land und um sie: eine Insel im gleichförmigen Feld, das Ziel doppelt so hoch, ein Schritt von 10 s
        Nahfeld m = new Nahfeld(40, 40, 10, 4, 0.08, 0.3, 24);
        for (int c = 0; c < m.nc; c++) { m.grund[c] = -6; m.spiegel[c] = 0; }
        for (int j = 18; j < 22; j++) for (int i = 18; i < 22; i++) m.grund[j * 40 + i] = 2;
        m.vorbereiten();
        still(m);
        m.mitReflexion = false;
        double[] ez = m.jonswap(1.0, 6, 0, 10);
        Nahfeld.Ziel zz = (x, y, out) -> System.arraycopy(ez, 0, out, 0, ez.length);
        m.setze(zz);
        m.rand = m.randAus(zz);
        double mz2 = 0;
        for (double x : ez) mz2 += x;
        m.bindungM0 = new double[m.nc];
        java.util.Arrays.fill(m.bindungM0, 4 * mz2);
        m.bindungBlock = 5;
        m.bindungZeit = 60;
        int frei = 20 * 40 + 16, fest = 8 * 40 + 30;                       // zwei Zellen westlich der Insel; weit von Insel und Rand
        double f0 = m.m0(frei), g0 = m.m0(fest);
        m.schritt(10);
        double ff = m.m0(frei) / f0, fg = m.m0(fest) / g0;
        A.pruefe(Math.abs(ff - 1) <= 0.01 && fg > 1.4 && fg < 1.7, "Blöcke an der Insel und um sie bleiben frei, die übrigen folgen dem Ziel (Ziel Hs 2 m, Feld 1 m, τ 60 s, ein Schritt von 10 s)",
                f("m₀ wächst neben der Insel um den Faktor %.4f, im freien Wasser um %.3f (erwartet rund 1 + 10/60 · 3)", ff, fg), "frei 1 ± 1 %, gebunden 1,4–1,7");
    }

    // ------------------------------------------------------------------ 17 Anlauf

    static void anlauf() {
        A.teil("17 Anlauf in Pseudozeit");
        // Shoaling mit Brechen und Reibung, 2 D mit Insel: Schritte gegen Anlauf
        Nahfeld[] v = new Nahfeld[2];
        int[] it = new int[2];
        long[] ms = new long[2];
        for (int a = 0; a < 2; a++) {
            Nahfeld n = new Nahfeld(80, 60, 10, 6, 0.06, 0.4, 32);
            for (int j = 0; j < n.ny; j++)
                for (int i = 0; i < n.nx; i++) {
                    int c = j * n.nx + i;
                    n.grund[c] = -12 + 11.5 * i / n.nx;
                    if (Math.hypot(i - 50, j - 30) < 5) n.grund[c] = 1;
                    n.spiegel[c] = 0;
                    n.u[c] = 0.3 * Math.sin(j / 9.0);
                }
            n.vorbereiten();
            n.mitWind = false;
            double[] e = n.jonswap(1.2, 7, 15, 10);
            Nahfeld.Ziel z = (x, y, out) -> System.arraycopy(e, 0, out, 0, e.length);
            n.rand = (i, j, b, q) -> i == -1 ? e[b * n.nd + q] / Math.max(1e-9, n.sigma(Math.max(0, Math.min(n.ny - 1, j)) * n.nx, b, q)) : 0;
            long t0 = System.nanoTime();
            it[a] = a == 0 ? n.stationaer(5, 3000, 1e-7) : n.anlauf(3000, 1e-7, 600);
            ms[a] = (System.nanoTime() - t0) / 1_000_000;
            v[a] = n;
        }
        double gr = 0, hmax = 0;
        for (int c = 0; c < v[0].nc; c++) { gr = Math.max(gr, Math.abs(v[1].hs(c) - v[0].hs(c))); hmax = Math.max(hmax, v[0].hs(c)); }
        A.pruefe(gr <= 0.01 * hmax, "Anlauf gegen Schritte bis zum Gleichgewicht (Böschung, Insel, Strömung, Brechen, Reibung, Beugung)",
                f("größte Abweichung von Hs %.2e m (%.2f %% des größten Hs %.3f m)", gr, 100 * gr / hmax, hmax), "± 1 % des größten Hs");
        A.info("Aufwand", f("Schritte: %d Schritte (%d ms); Anlauf: %d Runden (%d ms)", it[0], ms[0], it[1], ms[1]));
        A.pruefe(it[1] < 400, "Anlauf in wenigen Runden (80 × 60 Zellen)", f("%d Runden", it[1]), "< 400");
    }

    // ------------------------------------------------------------------ 18 Partitionen

    static void partitionen() {
        A.teil("18 Spektrum aus den Partitionen");
        Nahfeld n = new Nahfeld(1, 1, 10, 8, 0.07, 0.6, 32);
        n.grund[0] = -20;
        n.vorbereiten();
        double[] sea = {1.2, 5.0, 250, 28}, swell = {0.8, 11.0, 300, 15};   // woher (von Gitternord, Uhrzeiger), Breite (Grad)
        double[] e = n.partitionen(sea, swell);
        double m0 = 0;
        for (double x : e) m0 += x;
        double soll = (1.2 * 1.2 + 0.8 * 0.8) / 16;
        A.pruefe(Math.abs(m0 / soll - 1) <= 0.03, "m₀ der Bänder gegen (Hs₁² + Hs₂²)/16 (Bänder 0,07–0,6 Hz)", f("%.5f gegen %.5f m² (%.1f %%)", m0, soll, 100 * (m0 / soll - 1)), "± 3 % (Anteil außerhalb der Bänder)");
        // Windsee allein: Richtung und Breite zurück
        double[] e1 = n.partitionen(sea);
        double a = 0, b = 0, s = 0;
        for (int bb = 0; bb < n.nb; bb++) for (int q = 0; q < n.nd; q++) { double x = e1[bb * n.nd + q]; a += x * n.cosT[q]; b += x * n.sinT[q]; s += x; }
        double r = Math.hypot(a, b) / s, wohin = Math.toDegrees(Math.atan2(b, a)), breite = Math.toDegrees(Math.sqrt(2 * (1 - r)));
        A.pruefe(winkel(Waves.woher(wohin), 250) <= 0.5 && Math.abs(breite - 28) <= 1.5, "Richtung (woher) und Breite (Kuik) der Windsee zurück",
                f("woher %.2f° (250°), Breite %.2f° (28°, 32 Richtungen)", Waves.woher(wohin), breite), "± 0,5°, ± 1,5°");
    }
}
