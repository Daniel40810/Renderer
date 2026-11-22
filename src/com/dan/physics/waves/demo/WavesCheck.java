package com.dan.physics.waves.demo;

import com.dan.core.pruef.Attest;
import com.dan.physics.waves.Waves;

import java.nio.file.Path;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Die Prüfsteine des Seegangs für P8a (Werkbuch HalligHooge Teil A, 12.9), soweit sie ohne Grafikkarte laufen:
 * 1 Wachstum über der Anlaufstrecke gegen Kahma und Calkoen (1992), 2 Shoaling und Refraktion gegen die Strahlentheorie
 * (Snellius), 3 Brechen nach Battjes und Janssen (1978) gegen eine unabhängige Integration derselben Bilanz, 4 die Untiefe
 * von Vincent und Briggs (1989) gegen den Rahmen, den das Coastal Engineering Manual nennt, 5 Gegenstrom gegen die
 * Erhaltung der Wirkung, 7 Bilanz der Wirkung. Die Prüfsteine 6 (Referenz gegen Grafikkarte) und 8 (Zeiten auf der 680M)
 * rechnet {@link WavesKarteCheck}; Teil 8 hier misst die Referenz auf dem Prozessor.
 * <p>
 * {@code --teil n}: nur Teil n (zum Nachrechnen; dann ohne Attest). {@code --kurz}: Teil 1 mit halber Strecke.
 * Teil 1 zeigt den Vergleich zusätzlich in der Skalierung mit u*; der Prüfstein bleibt der mit U10 (12.9).
 */
public final class WavesCheck {
    static final Attest A = new Attest("com.dan.physics.waves", Waves.VERSION);

    public static void main(String[] args) {
        List<String> a = List.of(args);
        int ti = a.indexOf("--teil");
        int nur = ti >= 0 ? Integer.parseInt(a.get(ti + 1)) : 0;
        boolean kurz = a.contains("--kurz");
        long t0 = System.nanoTime();
        try {
            if (nur == 0 || nur == 2) refraktion();
            if (nur == 0 || nur == 3) brechen();
            if (nur == 0 || nur == 5) gegenstrom();
            if (nur == 0 || nur == 7) bilanz();
            if (nur == 0 || nur == 4) untiefe(kurz);
            if (nur == 0 || nur == 1) { wachstum(10, kurz); wachstum(20, kurz); }
            if (nur == 0 || nur == 8) zeiten();
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (nur == 0) {
            A.teil("Offen");
            A.info("Prüfstein 4 (Vincent–Briggs)", "gerechnet gegen den Rahmen aus dem Coastal Engineering Manual; die Messwerte der Transekte liegen nicht vor");
            A.info("Prüfsteine 6 und 8 (Karte gegen Referenz, Zeiten auf der 680M)", "WavesKarteCheck, Attest P8a_WavesKarte.md; oben die Zeiten der Referenz auf dem Prozessor");
            A.info("Rechenzeit", f("%.0f s", (System.nanoTime() - t0) * 1e-9));
            Path p = A.schreibe(Path.of("docs", "status", "P8a_Waves.md"), "P8a – WavesCheck (Referenz)");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Hilfen

    /** JONSWAP über der Frequenz (m²/Hz), auf dem Gitter auf Hs gebracht. */
    static double[] jonswap(Waves w, double hs, double tp, double gamma) {
        double fp = 1 / tp, s0 = 0;
        double[] e = new double[w.nf];
        for (int m = 0; m < w.nf; m++) {
            double x = w.f[m] / fp, sg = x <= 1 ? 0.07 : 0.09;
            e[m] = Math.pow(w.f[m], -5) * Math.exp(-1.25 * Math.pow(x, -4)) * Math.pow(gamma, Math.exp(-(x - 1) * (x - 1) / (2 * sg * sg)));
            s0 += e[m] * w.df[m];
        }
        double z = hs * hs / 16 / s0;
        for (int m = 0; m < w.nf; m++) e[m] *= z;
        return e;
    }

    /** cos^p um die Richtung t0 (rad), auf dem Gitter normiert (Summe · Δθ = 1). */
    static double[] verteilung(Waves w, double t0, double p) {
        double[] d = new double[w.nd];
        double s = 0;
        for (int q = 0; q < w.nd; q++) {
            double c = Math.cos(w.theta[q] - t0);
            d[q] = c > 0 ? Math.pow(c, p) : 0;
            s += d[q] * w.dTheta;
        }
        for (int q = 0; q < w.nd; q++) d[q] /= s;
        return d;
    }

    static void laufe(Waves w, double dt, double dauer) {
        int n = (int) Math.ceil(dauer / dt);
        for (int s = 0; s < n; s++) w.schritt(dt);
    }

    static void alleQuellenAus(Waves w) { w.mitWind = w.mitSchaum = w.mitQuad = w.mitReibung = w.mitBrechen = false; }

    // ------------------------------------------------------------------ 2 Shoaling und Refraktion

    /** Ebener Strand, längs y gleich; ohne Quellen. Vergleich mit der Strahlentheorie je Frequenz und Richtung. */
    static void refraktion() {
        A.teil("2 Shoaling und Refraktion");
        for (double grad : new double[] {0, 30}) for (int nd : new int[] {36, 72}) {
            int nx = 100;
            Waves w = new Waves(nx, 1, 50, 50, 5, 0.08, 0.125, nd);
            alleQuellenAus(w);
            w.periodischY = true;
            for (int i = 0; i < nx; i++) w.tiefe[i] = 20 - 19.0 * (i + 0.5) / nx;
            w.vorbereiten();
            double[] ef = jonswap(w, 1.0, 10, 3.3), dv = verteilung(w, Math.toRadians(grad), 30);
            w.randW = (i, j, m, q) -> ef[m] * dv[q];
            laufe(w, 10, 4000);
            double maxH = 0, maxR = 0;
            StringBuilder sb = new StringBuilder();
            for (double tiefe : new double[] {15, 10, 5, 3, 2}) {
                int c = (int) Math.round((20 - tiefe) / 19.0 * nx - 0.5);
                double d = w.tiefe[c], m0 = 0, sx = 0, sy = 0;
                for (int m = 0; m < w.nf; m++) {
                    double k0 = w.wellenzahl(0, m), g0 = w.gruppe(0, m), k1 = w.wellenzahl(c, m), g1 = w.gruppe(c, m);
                    for (int q = 0; q < nd; q++) {
                        if (w.cosT[q] <= 1e-9 || dv[q] == 0) continue;
                        double si = k0 * w.sinT[q] / k1, co = Math.sqrt(1 - si * si);
                        double z = ef[m] * dv[q] * w.df[m] * w.dTheta * g0 * w.cosT[q] / (g1 * co);
                        m0 += z; sx += z * co; sy += z * si;
                    }
                }
                double hs = 4 * Math.sqrt(m0), ri = Math.toDegrees(Math.atan2(sy, sx));
                double aH = w.hs(c) / hs - 1, aR = w.richtung(c) - ri;
                maxH = Math.max(maxH, Math.abs(aH)); maxR = Math.max(maxR, Math.abs(aR));
                sb.append(f("%.0f m: %.3f/%.3f m, %.1f/%.1f°; ", d, w.hs(c), hs, w.richtung(c), ri));
            }
            String fall = f("%.0f°, %d Richtungen", grad, nd);
            A.info("Fall " + fall + " (Modell/Strahl)", sb.toString());
            if (nd == 36) {
                A.hoechstens("Hs gegen Strahlentheorie, " + fall, maxH * 100, 3, "%");
                A.hoechstens("Richtung gegen Snellius, " + fall, maxR, 1, "°");
            } else {
                A.info("Hs und Richtung, " + fall, f("%.2f %%, %.2f°", maxH * 100, maxR));
            }
        }
    }

    // ------------------------------------------------------------------ 3 Brechen

    /** Strand 1:100, eine Richtung, nur Brechen. Gegen eine unabhängige Integration (RK4, 1 m) derselben Bilanz. */
    static void brechen() {
        A.teil("3 Brechen an der Tiefe");
        int nx = 150;
        double dx = 10, d0 = 15.5, gefaelle = 0.01;
        Waves w = new Waves(nx, 1, dx, dx, 14, 0.06, 0.35, 36);
        alleQuellenAus(w);
        w.mitBrechen = true;
        w.periodischY = true;
        for (int i = 0; i < nx; i++) w.tiefe[i] = d0 - gefaelle * (i + 0.5) * dx;
        w.vorbereiten();
        double[] ef = jonswap(w, 2.0, 8, 3.3);
        w.randW = (i, j, m, q) -> q == 0 ? ef[m] / w.dTheta : 0;
        laufe(w, 5, 3000);
        // ---- unabhängig: Fluss F_m = cg_m E_m Δf_m, dF_m/dx = −Γ(E) E_m Δf_m, kontinuierliche Tiefe
        double[] fl = new double[w.nf];
        double x = 0.5 * dx;                                                    // ab der Mitte der ersten Zelle
        for (int m = 0; m < w.nf; m++) fl[m] = w.gruppe(0, m) * w.e[w.ix(0, m, 0)] * w.dTheta * w.df[m];
        double summe = 0, max = 0, hsd = 0;
        int zahl = 0;
        StringBuilder sb = new StringBuilder();
        double h = 0.5;
        for (int c = 1; c < nx && w.nass(c); c++) {
            double ziel = (c + 0.5) * dx;
            while (x < ziel - 1e-9) {
                double[] k1 = rate(w, fl, d0 - gefaelle * x), k2 = rate(w, plus(fl, k1, h / 2), d0 - gefaelle * (x + h / 2));
                double[] k3 = rate(w, plus(fl, k2, h / 2), d0 - gefaelle * (x + h / 2)), k4 = rate(w, plus(fl, k3, h), d0 - gefaelle * (x + h));
                for (int m = 0; m < w.nf; m++) fl[m] = Math.max(0, fl[m] + h / 6 * (k1[m] + 2 * k2[m] + 2 * k3[m] + k4[m]));
                x += h;
            }
            double d = w.tiefe[c], m0 = 0;
            if (d < 0.5) break;
            for (int m = 0; m < w.nf; m++) m0 += fl[m] / Waves.gruppe(w.sig[m], Waves.wellenzahl(w.sig[m], d), d);
            double ref = 4 * Math.sqrt(m0), ab = w.hs(c) / ref - 1;
            summe += Math.abs(ab); zahl++; max = Math.max(max, Math.abs(ab));
            if (c % 25 == 0 || d < 1.05 && d > 0.95) sb.append(f("%.1f m: %.3f/%.3f m; ", d, w.hs(c), ref));
            if (d < 2) hsd = Math.max(hsd, w.hs(c) / d);
        }
        A.info("Hs über der Tiefe (Modell/Integration)", sb.toString());
        A.hoechstens("Hs gegen unabhängige Integration, Mittel des Betrags", summe / zahl * 100, 10, "%");
        A.info("größte Abweichung; Hs/Tiefe im Flachen (höchstens)", f("%.1f %%; %.2f", max * 100, hsd));
        A.pruefe(hsd > 0.3 && hsd < 0.8, "Sättigung Hs/Tiefe unter 2 m", f("%.2f", hsd), "0,3 … 0,8");
        A.info("Messwerte aus dem Kanal (Battjes–Janssen 1978)", "liegen nicht vor – offen");
    }

    static double[] plus(double[] a, double[] b, double h) {
        double[] r = new double[a.length];
        for (int i = 0; i < a.length; i++) r[i] = Math.max(0, a[i] + h * b[i]);
        return r;
    }

    /** dF_m/dx für die Brecherbilanz in der Tiefe d. */
    static double[] rate(Waves w, double[] fl, double d) {
        double[] em = new double[w.nf], r = new double[w.nf];
        double et = 0, es = 0;
        for (int m = 0; m < w.nf; m++) {
            em[m] = fl[m] / Waves.gruppe(w.sig[m], Waves.wellenzahl(w.sig[m], d), d);
            et += em[m]; es += em[m] * w.sig[m];
        }
        if (et <= 0 || d <= 0) return r;
        double hm = w.gammaBJ * d, qb = Waves.brecheranteil(Math.sqrt(8 * et) / hm);
        double gam = 0.25 * w.alphaBJ * qb * (es / et) / (2 * Math.PI) * hm * hm / et;
        for (int m = 0; m < w.nf; m++) r[m] = -gam * em[m];
        return r;
    }

    // ------------------------------------------------------------------ 4 Untiefe

    /**
     * Die elliptische Untiefe von Vincent und Briggs (1989) im Maßstab des Beckens: Tiefe 0,4572 m, über der Untiefe
     * 0,1524 m, Halbachsen 3,05 m (längs) und 3,96 m (quer); TMA-Spektrum (γ = 2, Tp = 1,3 s, Hs = 7,75 cm), Richtungen
     * als gewickelte Normalverteilung mit σ = 30° (Fall B1, breit) und 10° (N1, schmal). Transekt 4 liegt 6,1 m hinter
     * der Mitte der Untiefe [S]. Ohne Quellen. Maß: der Rahmen aus dem Coastal Engineering Manual (II-3) für Spektren
     * mit breiter Richtungsverteilung: 110–140 % hinter der Untiefe; die Messwerte der Transekte liegen nicht vor.
     */
    static void untiefe(boolean kurz) {
        A.teil("4 Untiefe nach Vincent und Briggs");
        double dx = kurz ? 0.4 : 0.2, tief = 0.4572, x0 = 6.1, y0 = 13.7, hs0 = 0.0775;
        int nx = (int) Math.round(16 / dx), ny = (int) Math.round(27.4 / dx);
        double[] hoch = new double[2];
        for (int fall = 0; fall < 2; fall++) {
            double sm = Math.toRadians(fall == 0 ? 30 : 10);
            Waves w = new Waves(nx, ny, dx, dx, 16, 0.45, 2.0, 36);
            alleQuellenAus(w);
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                double x = (i + 0.5) * dx - x0, y = (j + 0.5) * dx - y0;
                boolean innen = x * x / (3.05 * 3.05) + y * y / (3.96 * 3.96) < 1;
                w.tiefe[j * nx + i] = innen ? Math.min(tief, 0.9144 - 0.7620 * Math.sqrt(1 - x * x / (3.81 * 3.81) - y * y / (4.95 * 4.95))) : tief;
            }
            w.vorbereiten();
            // TMA: JONSWAP mal der Tiefenfunktion von Kitaigorodskii; auf Hs gebracht
            double[] ef = jonswap(w, 1, 1.3, 2);
            double s0 = 0;
            for (int m = 0; m < w.nf; m++) {
                double wh = w.sig[m] * Math.sqrt(tief / Waves.G);
                ef[m] *= wh <= 1 ? 0.5 * wh * wh : wh < 2 ? 1 - 0.5 * (2 - wh) * (2 - wh) : 1;
                s0 += ef[m] * w.df[m];
            }
            double[] dv = new double[w.nd];
            double sd = 0;
            for (int q = 0; q < w.nd; q++) {
                double d = 1 / (2 * Math.PI);
                for (int n = 1; n <= 20; n++) d += Math.exp(-0.5 * n * n * sm * sm) * Math.cos(n * w.theta[q]) / Math.PI;
                dv[q] = Math.max(0, d); sd += dv[q] * w.dTheta;
            }
            double z = hs0 * hs0 / 16 / s0 / sd;
            Waves.Rand rand = (i, j, m, q) -> ef[m] * dv[q] * z;
            w.randW = rand; w.randS = rand; w.randN = rand;
            laufe(w, 0.5, 45);
            int it = (int) Math.round(12.2 / dx - 0.5);
            double max = 0, ymax = 0, min = 9, maxAlle = 0, xAlle = 0;
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < ny; j++) {
                double y = (j + 0.5) * dx - y0, v = w.hs(j * nx + it) / hs0;
                if (Math.abs(y) > 7) continue;
                if (v > max) { max = v; ymax = y; }
                min = Math.min(min, v);
                if (Math.abs(y / 1.0 - Math.rint(y / 1.0)) < 0.5 * dx - 1e-9 && y > -0.5 * dx) sb.append(f("%.0f m: %.2f; ", Math.abs(y), v));
            }
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                double v = w.hs(j * nx + i) / hs0;
                if ((i + 0.5) * dx > x0 && v > maxAlle) { maxAlle = v; xAlle = (i + 0.5) * dx - x0; }
            }
            hoch[fall] = max;
            String name = fall == 0 ? "B1 (σ = 30°)" : "N1 (σ = 10°)";
            A.info(name + ": Hs/Hs0 auf Transekt 4 über dem Abstand von der Achse", sb.toString());
            A.info(name + ": höchster Wert auf Transekt 4; kleinster; höchster überhaupt", f("%.2f bei y = %+.1f m; %.2f; %.2f, %.1f m hinter der Mitte", max, ymax, min, maxAlle, xAlle));
            if (fall == 0) {
                A.pruefe(max >= 1.10 && max <= 1.40, "B1: Erhöhung hinter der Untiefe (Transekt 4)", f("%.2f", max), "1,10 … 1,40 (Rahmen CEM)");
                A.pruefe(Math.abs(ymax) <= 1.0, "B1: Ort des höchsten Werts", f("%+.1f m von der Achse", ymax), "≤ 1 m");
            }
        }
        A.pruefe(hoch[1] > hoch[0], "schmale Richtungsverteilung bündelt stärker als breite", f("%.2f gegen %.2f", hoch[1], hoch[0]), "N1 > B1");
    }

    // ------------------------------------------------------------------ 5 Gegenstrom

    /** Wellen laufen in einen Gegenstrom; je Stufe bleibt ω = σ + kU, der Fluss der Wirkung (cg + U) E/σ bleibt. */
    static void gegenstrom() {
        A.teil("5 Gegenstrom");
        int nx = 100;
        double dx = 50, d = 30;
        Waves w = new Waves(nx, 1, dx, dx, 28, 0.04, 0.6, 36);
        alleQuellenAus(w);
        w.periodischY = true;
        for (int i = 0; i < nx; i++) {
            w.tiefe[i] = d;
            double x = (i + 0.5) * dx;
            w.u[i] = -1.0 * Math.max(0, Math.min(1, (x - 1000) / 2000));
        }
        w.vorbereiten();
        double[] ef = new double[w.nf];
        double s0 = 0;
        for (int m = 0; m < w.nf; m++) { ef[m] = Math.exp(-0.5 * Math.pow((w.f[m] - 0.15) / 0.02, 2)); s0 += ef[m] * w.df[m]; }
        for (int m = 0; m < w.nf; m++) ef[m] *= 1.0 / 16 / s0;
        w.randW = (i, j, m, q) -> q == 0 ? ef[m] / w.dTheta : 0;
        laufe(w, 5, 3000);
        double max = 0;
        StringBuilder sb = new StringBuilder();
        for (int c : new int[] {10, 30, 40, 50, 60, 80, 95}) {
            double uu = w.u[c], m0 = 0, t1 = 0;
            for (int m = 0; m < w.nf; m++) {
                double om = w.sig[m], kk = w.wellenzahl(0, m);
                for (int it = 0; it < 100; it++) {                                // σ(k) + kU = ω
                    double sg = Math.sqrt(Waves.G * kk * Math.tanh(kk * d)), cgk = Waves.gruppe(sg, kk, d);
                    double st = (sg + kk * uu - om) / (cgk + uu);
                    kk -= st;
                    if (Math.abs(st) < 1e-13 * kk) break;
                }
                double sg = Math.sqrt(Waves.G * kk * Math.tanh(kk * d)), cgk = Waves.gruppe(sg, kk, d);
                double z = w.gruppe(0, m) * ef[m] * w.df[m] / w.sig[m] * sg / (cgk + uu);
                m0 += z; t1 += z * sg;
            }
            double ref = 4 * Math.sqrt(m0), ab = w.hs(c) / ref - 1;
            max = Math.max(max, Math.abs(ab));
            sb.append(f("U %.2f: %.3f/%.3f m, Tm01 %.2f/%.2f s; ", uu, w.hs(c), ref, w.tm01(c), 2 * Math.PI * m0 / t1));
        }
        A.info("Hs und Tm01 (Modell/Erhaltung der Wirkung)", sb.toString());
        A.hoechstens("Hs gegen Erhaltung der Wirkung", max * 100, 5, "%");
    }

    // ------------------------------------------------------------------ 7 Bilanz

    /** Untiefe, Strömung, Wind, alle Quellen: Die Wirkung im Gebiet ändert sich genau um die verbuchten Posten. */
    static void bilanz() {
        A.teil("7 Bilanz der Wirkung");
        int nx = 30, ny = 20;
        Waves w = new Waves(nx, ny, 200, 200, 28, 0.04, 0.6, 36);
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            int c = j * nx + i;
            double x = (i + 0.5 - 18) * 200, y = (j + 0.5 - 10) * 200, r2 = (x * x + y * y) / (1200.0 * 1200.0);
            w.tiefe[c] = 10 - 9.98 * Math.exp(-r2 * 4) - 0.2 * i;                 // Untiefe, die trockenfällt; nach Osten flacher
            w.u[c] = 0.8 * Math.sin(Math.PI * (j + 0.5) / ny); w.v[c] = 0.3 * Math.cos(Math.PI * (i + 0.5) / nx);
            w.windU[c] = 15 * Math.cos(Math.toRadians(30)); w.windV[c] = 15 * Math.sin(Math.toRadians(30));
        }
        w.vorbereiten();
        double[] ef = jonswap(w, 1.5, 7, 3.3), dv = verteilung(w, Math.toRadians(10), 4);
        w.randW = (i, j, m, q) -> ef[m] * dv[q];
        double a0 = w.wirkung();
        int trocken = 0, sub = 0;
        for (int c = 0; c < w.nc; c++) if (!w.nass(c)) trocken++;
        for (int s = 0; s < 120; s++) { w.schritt(30); sub = Math.max(sub, w.teilschritte); }
        double a1 = w.wirkung(), summe = 0, groesse = Math.abs(a1);
        StringBuilder sb = new StringBuilder();
        for (int p = 0; p < 8; p++) { summe += w.bilanz[p]; groesse = Math.max(groesse, Math.abs(w.bilanz[p])); sb.append(Waves.BILANZ[p]).append(f(" %.3e; ", w.bilanz[p])); }
        boolean heil = true;
        double min = 0, hmax = 0;
        for (double v : w.e) { if (!Double.isFinite(v)) heil = false; min = Math.min(min, v); }
        for (int c = 0; c < w.nc; c++) hmax = Math.max(hmax, w.hs(c));
        A.info("Fall", f("%d × %d Zellen zu 200 m, %d trocken, 28 × 36 Stufen, 1 h in Schritten von 30 s (bis %d Teilschritte), Hs bis %.2f m", nx, ny, trocken, sub, hmax));
        A.info("Posten (m²·s·m²)", sb.toString());
        A.hoechstens("Bilanz: |Änderung − Posten| / größter Posten", Math.abs(a1 - a0 - summe) / groesse, 1e-3, "");
        A.pruefe(heil && min >= -1e-12, "Spektrum endlich und nicht negativ", f("kleinster Wert %.2e", min), "≥ 0");
        double[][] kr = w.kraft();
        double kmax = 0;
        for (int c = 0; c < w.nc; c++) kmax = Math.max(kmax, Math.hypot(kr[0][c], kr[1][c]));
        A.info("größte Kraft aus der Strahlungsspannung", f("%.2f N/m²", kmax));
    }

    // ------------------------------------------------------------------ 1 Wachstum über der Anlaufstrecke

    /** Tiefes Wasser, Wind ablandig längs x, alle Quellen des tiefen Wassers. Gegen Kahma und Calkoen (1992), zusammengesetzter Datensatz [S]. */
    static void wachstum(double u10, boolean kurz) {
        A.teil(f("1 Wachstum über der Anlaufstrecke, %.0f m/s", u10));
        // zwei Gitter: 0–25 km zu 250 m und 0–200 km zu 2 km
        double[] strecke = {5, 10, 20, 50, 100, 200};
        double[] hs = new double[6], tp = new double[6];
        for (int g = 0; g < 2; g++) {
            double dx = g == 0 ? 250 : 2000;
            int nx = kurz && g == 1 ? 51 : 101;
            Waves w = new Waves(nx, 1, dx, dx, 28, 0.04, 0.6, 36);
            w.mitReibung = false; w.mitBrechen = false; w.mitRefraktion = false;
            w.periodischY = true;
            for (int i = 0; i < nx; i++) { w.tiefe[i] = 2000; w.windU[i] = u10; }
            w.vorbereiten();
            double dt = g == 0 ? 30 : 120, dauer = (g == 0 ? 8 : kurz ? 18 : 72) * 3600, letzter = 0;
            int n = (int) (dauer / dt);
            for (int s = 0; s < n; s++) {
                w.schritt(dt);
                if ((s + 1) % (int) (3600 / dt) == 0) {
                    double jetzt = w.hs(nx - 1);
                    if (Math.abs(jetzt - letzter) < 5e-5 * jetzt) break;       // stationär
                    letzter = jetzt;
                }
            }
            for (int p = 0; p < 6; p++) {
                if (g == 0 ? strecke[p] > 20 : strecke[p] <= 20) continue;
                double xi = strecke[p] * 1000 / dx - 0.5;
                int i0 = (int) Math.floor(xi);
                if (i0 + 1 >= nx) { hs[p] = Double.NaN; continue; }
                double t = xi - i0;
                hs[p] = (1 - t) * w.hs(i0) + t * w.hs(i0 + 1); tp[p] = (1 - t) * w.tp(i0) + t * w.tp(i0 + 1);
            }
            A.info(f("Gitter %.0f m: gerechnet", dx), f("%.1f h, Hs am Ende %.2f m", w.zeit / 3600, w.hs(nx - 1)));
        }
        double maxH = 0, maxT = 0, us = Waves.ustern(u10), maxHs = 0;
        StringBuilder sb = new StringBuilder(), su = new StringBuilder();
        for (int p = 0; p < 6; p++) {
            if (Double.isNaN(hs[p])) continue;
            // dieselben Daten in der Skalierung mit u* (Kahma und Calkoen 1992) [S]; Sättigung nach WAM 3
            double xu = Waves.G * strecke[p] * 1000 / (us * us), eu = Math.min(1.1e3, 6.5e-4 * Math.pow(xu, 0.9)), nuu = Math.max(0.13 / 28, 3.08 / (2 * Math.PI) * Math.pow(xu, -0.27));
            double hu = 4 * Math.sqrt(eu) * us * us / Waves.G, tu = us / (nuu * Waves.G);
            maxHs = Math.max(maxHs, Math.abs(hs[p] * hs[p] / (hu * hu) - 1));
            su.append(f("%.0f km: Hs %.2f/%.2f m (E %+.0f %%), fp %.3f/%.3f Hz (%+.0f %%); ", strecke[p], hs[p], hu, (hs[p] * hs[p] / (hu * hu) - 1) * 100, 1 / tp[p], 1 / tu, (tu / tp[p] - 1) * 100));
            double xs = Waves.G * strecke[p] * 1000 / (u10 * u10);
            double eps = Math.min(3.64e-3, 5.2e-7 * Math.pow(xs, 0.9)), nu = Math.max(0.13, 2.18 * Math.pow(xs, -0.27));
            double hk = 4 * Math.sqrt(eps) * u10 * u10 / Waves.G, tk = u10 / (nu * Waves.G);
            maxH = Math.max(maxH, Math.abs(hs[p] * hs[p] / (hk * hk) - 1)); maxT = Math.max(maxT, Math.abs(tk / tp[p] - 1));
            sb.append(f("%.0f km: Hs %.2f/%.2f m (E %+.0f %%), fp %.3f/%.3f Hz (%+.0f %%); ", strecke[p], hs[p], hk, (hs[p] * hs[p] / (hk * hk) - 1) * 100, 1 / tp[p], 1 / tk, (tk / tp[p] - 1) * 100));
        }
        A.info("Modell/Kahma–Calkoen (mit U10 skaliert)", sb.toString());
        A.info(f("Modell/Kahma–Calkoen (mit u* = %.3f m/s skaliert)", us), su.toString());
        A.info("größte Abweichung der Energie in der Skalierung mit u*", f("%.1f %%", maxHs * 100));
        A.hoechstens(f("Energie gegen Kahma–Calkoen, %.0f m/s", u10), maxH * 100, 15, "%");
        A.hoechstens(f("Spitzenfrequenz gegen Kahma–Calkoen, %.0f m/s", u10), maxT * 100, 15, "%");
    }

    // ------------------------------------------------------------------ 8 Zeiten (Auskunft)

    /**
     * Zeiten der Referenz auf dem Prozessor in den drei Auflösungen aus 12.9: ns je Wert für einen Teilschritt der
     * Ausbreitung und für einen Schritt der Quellen; daraus die Rechenzeit je 20 min Szenenzeit für G2 (500 m,
     * 148 × 172 Zellen) und G3 (100 m, 240 × 320 Zellen) bei √(g · 20 m) = 14 m/s und Quellen alle 60 s.
     */
    static void zeiten() {
        A.teil("8 Zeiten der Referenz auf dem Prozessor (Auskunft; die Karte misst WavesKarteCheck)");
        for (int[] r : new int[][] {{28, 36}, {24, 24}, {20, 18}}) {
            int nx = 80, ny = 60;
            Waves w = new Waves(nx, ny, 100, 100, r[0], 0.04, 0.6, r[1]);
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                w.tiefe[c] = 12 - 0.1 * i + 2 * Math.sin(0.3 * j);
                w.u[c] = 0.5 * Math.sin(Math.PI * (j + 0.5) / ny); w.v[c] = 0.2;
                w.windU[c] = 15;
            }
            w.vorbereiten();
            double[] ef = jonswap(w, 1.5, 7, 3.3), dv = verteilung(w, 0, 4);
            w.randW = (i, j, m, q) -> ef[m] * dv[q];
            for (int s = 0; s < 5; s++) w.schritt(5);                             // warm
            long werte = (long) w.nc * w.nf * w.nd;
            int n = 10, teil = 0;
            long t0 = System.nanoTime();
            for (int s = 0; s < n; s++) { w.schritt(5); teil += w.teilschritte; }
            double mit = (System.nanoTime() - t0) * 1e-9;
            alleQuellenAus(w);
            t0 = System.nanoTime();
            int teil2 = 0;
            for (int s = 0; s < n; s++) { w.schritt(5); teil2 += w.teilschritte; }
            double ohne = (System.nanoTime() - t0) * 1e-9;
            double nsA = ohne / teil2 / werte * 1e9, nsQ = Math.max(0, mit - ohne * teil / teil2) / n / werte * 1e9;
            double g2 = 148.0 * 172 * r[0] * r[1], g3 = 240.0 * 320 * r[0] * r[1];
            double zeit = (g2 * (1200 / (0.8 * 500 / 14.0) * nsA + 20 * nsQ) + g3 * (1200 / (0.8 * 100 / 14.0) * nsA + 20 * nsQ)) * 1e-9;
            A.info(f("%d × %d Stützstellen", r[0], r[1]), f("Ausbreitung %.1f ns je Wert und Teilschritt, Quellen %.1f ns je Wert und Schritt; G2 + G3 je 20 min Szenenzeit: %.0f s (%d Fäden)",
                    nsA, nsQ, zeit, Runtime.getRuntime().availableProcessors()));
        }
    }
}
