package com.dan.physics.waves.nah.demo;

import com.dan.core.gpu.Stoppuhr;
import com.dan.core.pruef.Attest;
import com.dan.physics.waves.nah.Nahfeld;
import com.dan.physics.waves.gpu.NahfeldKarte;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;

import static com.dan.core.pruef.Attest.f;

/**
 * Das Nahfeld auf der Karte gegen die Referenz (Seegang Werkbuch, S2b): dieselben Fälle wie {@link NahfeldCheck}, je
 * dieselben Schritte auf der Karte (float) und in der Referenz (double), verglichen wird Hs je Zelle: größte Abweichung
 * relativ zum größten Hs ≤ 10⁻³ [G]. Dazu Anlauf, Verschieben und die Zeit je Schritt auf der Karte.
 * Attest {@code docs/status/S2_NahfeldKarte.md}. Einzelne Teile: Nummern als Argumente.
 */
public final class NahfeldKarteCheck {
    static final Attest A = new Attest("com.dan.physics.waves.gpu (NahfeldKarte)", Nahfeld.VERSION);
    static final double GRENZE = 1e-3;

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        java.util.Set<String> nur = new java.util.HashSet<>(Arrays.asList(args));
        Runnable[] teile = {NahfeldKarteCheck::ruhe, NahfeldKarteCheck::refraktion, NahfeldKarteCheck::stroemung, NahfeldKarteCheck::reflexion,
                NahfeldKarteCheck::beugung, NahfeldKarteCheck::quellen, NahfeldKarteCheck::alles, NahfeldKarteCheck::anlauf, NahfeldKarteCheck::verschieben,
                NahfeldKarteCheck::zeit};
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
        Path p = A.schreibe(Path.of("docs", "status", "S2_NahfeldKarte.md"), "S2b – Nahfeld auf der Karte gegen die Referenz (Nahfeld " + Nahfeld.VERSION + ")");
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

    interface Tiefe { double h(int i); }

    static Nahfeld zeile(int nx, double dx, int nb, double fMin, double fMax, int nd, Tiefe t) {
        Nahfeld n = new Nahfeld(nx, 1, dx, nb, fMin, fMax, nd);
        for (int i = 0; i < nx; i++) { n.grund[i] = -t.h(i); n.spiegel[i] = 0; }
        n.vorbereiten();
        return n;
    }

    static Nahfeld.Rand west(Nahfeld n, double[] e) {
        return (i, j, b, q) -> {
            if (i != -1) return 0;
            int c = Math.max(0, Math.min(n.ny - 1, j)) * n.nx;
            double s = n.sigma(c, b, q);
            return s > 0 ? e[b * n.nd + q] / s : 0;
        };
    }

    /**
     * Karte und Referenz mit demselben Anfang; {@code schritt} rechnet einen Schritt der Referenz, die Karte macht
     * {@code schritte} Schritte zu dt. Gibt {größte Abweichung relativ, größtes Hs, Teilschritte Karte, Teilschritte Referenz}.
     */
    static double[] vergleiche(Nahfeld n, int schritte, double dt) {
        n.vorbereiten();
        double[] w = n.n.clone();
        double[] tk = new double[2];
        try (NahfeldKarte k = NahfeldKarte.mitGeraet(n)) {
            k.rand(n.randVarianz());
            for (int s = 0; s < schritte; s++) tk[0] += k.schritt(dt);
            double[] hk = k.hs();
            System.arraycopy(w, 0, n.n, 0, w.length);
            for (int s = 0; s < schritte; s++) { n.schritt(dt); tk[1] += n.teilschritte; }
            double gr = 0, hmax = 0;
            for (int c = 0; c < n.nc; c++) hmax = Math.max(hmax, n.hs(c));
            for (int c = 0; c < n.nc; c++) gr = Math.max(gr, Math.abs(hk[c] - n.hs(c)));
            return new double[] {gr / Math.max(1e-12, hmax), hmax, tk[0], tk[1]};
        }
    }

    static void pruefe(String fall, double[] r) {
        A.pruefe(r[0] <= GRENZE, fall, f("größte Abweichung von Hs %.2e des größten (%.3f m); Teilschritte Karte %.0f, Referenz %.0f", r[0], r[1], r[2], r[3]), "≤ 1e-3");
    }

    // ------------------------------------------------------------------ Fälle

    static void ruhe() {
        A.teil("1 Ebener Grund");
        Nahfeld n = new Nahfeld(40, 30, 10, 8, 0.06, 0.4, 24);
        for (int c = 0; c < n.nc; c++) { n.grund[c] = -10; n.spiegel[c] = 0; }
        n.vorbereiten();
        still(n);
        double[] e = n.jonswap(1.2, 7, 30, 10);
        Nahfeld.Ziel z = (x, y, out) -> System.arraycopy(e, 0, out, 0, e.length);
        n.setze(z);
        n.rand = n.randAus(z);
        pruefe("gleichförmiges Feld, Rand wie Feld, Beugung an (10 Schritte zu 10 s)", vergleiche(n, 10, 10));
    }

    static void refraktion() {
        A.teil("2 Shoaling und Refraktion");
        int nd = 72;
        Nahfeld n = zeile(150, 20, 1, 0.095, 0.105, nd, i -> 30 - 27 * (i + 0.5) / 150);
        n.periodischY = true;
        n.vorbereiten();
        still(n);
        double[] e = new double[nd];
        double sum = 0;
        for (int q = 0; q < nd; q++) { double d = Math.IEEEremainder(n.theta[q] - Math.toRadians(30), 2 * Math.PI); e[q] = Math.pow(Math.cos(0.5 * d), 200); sum += e[q]; }
        for (int q = 0; q < nd; q++) e[q] /= 16 * sum;
        n.rand = west(n, e);
        pruefe("30° auf 30 m bis 3 m, 0,1 Hz, 72 Richtungen (60 Schritte zu 10 s)", vergleiche(n, 60, 10));
    }

    static void stroemung() {
        A.teil("3 Strömung und Blockieren");
        for (double ue : new double[] {0.8, -0.8, -2.5}) {
            double f = ue == -2.5 ? 0.2 : 0.12;
            Nahfeld n = zeile(200, 10, 1, f - 0.005, f + 0.005, 36, i -> ue == -2.5 ? 200 : 50);
            for (int i = 0; i < n.nx; i++) n.u[i] = i < 40 ? 0 : i > 160 ? ue : ue * (i - 40) / 120.0;
            n.vorbereiten();
            still(n);
            if (ue == -2.5) n.mitSchaum = true;
            n.rand = west(n, n.einzeln(1.0, 0, 0));
            pruefe(f("U %+.1f m/s%s (60 Schritte zu 5 s)", ue, ue == -2.5 ? ", blockiert, mit Schaumkronen" : ""), vergleiche(n, 60, 5));
        }
    }

    static void reflexion() {
        A.teil("4 Reflexion");
        for (boolean wand : new boolean[] {false, true}) {
            Nahfeld n = zeile(100, 20, 1, 0.095, 0.105, 36, i -> 2);
            n.grund[99] = 0.5;
            if (wand) { n.wand = new boolean[n.nc]; n.wand[99] = true; }
            n.vorbereiten();
            still(n);
            n.mitBeugung = false;
            n.rand = west(n, n.einzeln(0.5, 0, 0));
            pruefe(wand ? "Wand" : "Böschung, K_r = 0,1 ξ² (60 Schritte zu 10 s)", vergleiche(n, 60, 10));
        }
    }

    static void beugung() {
        A.teil("5 Beugung an einer Halbebene");
        Nahfeld n = new Nahfeld(80, 100, 5, 1, 0.12, 0.13, 36);
        for (int c = 0; c < n.nc; c++) { n.grund[c] = -10; n.spiegel[c] = 0; }
        for (int i = 0; i < 40; i++) n.grund[20 * n.nx + i] = 2;
        n.vorbereiten();
        still(n);
        n.mitReflexion = false;
        int q = 9;
        n.rand = (i, j, b, qq) -> j == -1 && qq == q ? (1.0 / 16) / n.sigma(i, 0, q) : 0;
        pruefe("Halbebene, 80 × 100 Zellen à 5 m (40 Schritte zu 2 s)", vergleiche(n, 40, 2));
    }

    static void quellen() {
        A.teil("6 Quellen");
        int nx = 150;
        Nahfeld n = zeile(nx, 10, 8, 0.06, 0.45, 32, i -> 8 - 7.7 * (i + 0.5) / nx);
        n.pflanzen = new double[n.nc];
        n.halm = new double[n.nc];
        for (int i = 120; i < nx; i++) { n.pflanzen[i] = 2; n.halm[i] = 0.3; }
        Arrays.fill(n.windU, 14);
        Arrays.fill(n.windV, 3);
        n.vorbereiten();
        n.rand = west(n, n.jonswap(1.5, 8, 0, 20));
        double[] r = vergleiche(n, 60, 5);
        pruefe("Wind, Schaumkronen, Brechen mit Kappung, Reibung, Pflanzen; Profil 8 m bis 0,3 m (60 Schritte zu 5 s)", r);
        n.quellenJeTeilschritt = false;
        n.setze((x, y, out) -> Arrays.fill(out, 0));
        pruefe("dasselbe, Quellen einmal je Schritt (wie physics.waves)", vergleiche(n, 60, 5));
    }

    static void alles() {
        A.teil("7 Alles zusammen: Insel, Wand, Gegenstrom, Wind, Pflanzen, Bindung");
        Nahfeld n = new Nahfeld(50, 40, 20, 8, 0.06, 0.45, 32);
        for (int j = 0; j < n.ny; j++)
            for (int i = 0; i < n.nx; i++) {
                int c = j * n.nx + i;
                double x = n.x(i) - n.x0, y = n.y(j) - n.y0;
                n.grund[c] = -10 + 11 * x / 1000;
                if (Math.hypot(x - 600, y - 400) < 80) n.grund[c] = 1.5;
                n.spiegel[c] = 0.4;
                n.u[c] = y > 600 ? -3.0 : 0.3;
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
        Arrays.fill(n.bindungM0, 0.8 * m0z);
        n.bindungBlock = 5;
        n.bindungZeit = 120;
        double[] w0 = n.n.clone();
        pruefe("50 × 40 Zellen à 20 m, 8 Bänder × 32 Richtungen, Bindung an m₀ (20 Schritte zu 5 s)", vergleiche(n, 20, 5));
        System.arraycopy(w0, 0, n.n, 0, w0.length);
        n.bindungSpektrum = new double[n.nn];
        int m = n.nb * n.nd;
        for (int c = 0; c < n.nc; c++) for (int x = 0; x < m; x++) n.bindungSpektrum[c * m + x] = 1.2 * e[x];
        pruefe("dasselbe, Bindung an das Spektrum (B20)", vergleiche(n, 20, 5));
    }

    static void anlauf() {
        A.teil("8 Anlauf");
        Consumer<Nahfeld> aufbau = n -> {
            for (int j = 0; j < n.ny; j++)
                for (int i = 0; i < n.nx; i++) {
                    int c = j * n.nx + i;
                    n.grund[c] = -12 + 11.5 * i / n.nx;
                    if (Math.hypot(i - 40, j - 20) < 4) n.grund[c] = 1;
                    n.spiegel[c] = 0;
                    n.u[c] = 0.3 * Math.sin(j / 9.0);
                }
            n.vorbereiten();
            n.mitWind = false;
        };
        Nahfeld n = new Nahfeld(60, 40, 10, 6, 0.06, 0.4, 32);
        aufbau.accept(n);
        double[] e = n.jonswap(1.2, 7, 15, 10);
        n.rand = (i, j, b, q) -> i == -1 ? e[b * n.nd + q] / Math.max(1e-9, n.sigma(Math.max(0, Math.min(n.ny - 1, j)) * n.nx, b, q)) : 0;
        int rk, rr;
        double[] hk;
        long t0 = System.nanoTime();
        try (NahfeldKarte k = NahfeldKarte.mitGeraet(n)) {
            k.rand(n.randVarianz());
            rk = k.anlauf(2000, 1e-7, 600);
            hk = k.hs();
        }
        long t1 = System.nanoTime();
        // seit S3d (Seegang Werkbuch 28): derselbe Anlauf in Stücken wechselnder Größe ist bitgleich
        int rs, stuecke = 0;
        double[] hs;
        try (NahfeldKarte k = NahfeldKarte.mitGeraet(n)) {
            k.rand(n.randVarianz());
            NahfeldKarte.Anlauf a = k.anlaufInStuecken(2000, 1e-7, 600);
            int[] groessen = {1, 3, 5, 16, 2, 7};
            while (!a.weiter(groessen[stuecke % groessen.length])) stuecke++;
            stuecke++;
            rs = a.runden();
            hs = k.hs();
        }
        long anders = 0;
        for (int c = 0; c < n.nc; c++) if (Double.doubleToRawLongBits(hs[c]) != Double.doubleToRawLongBits(hk[c])) anders++;
        A.pruefe(rs == rk && anders == 0, "Anlauf in Stücken (1, 3, 5, 16, 2, 7 Runden) gegen den Anlauf in einem Zug (S3d)",
                f("Runden %d gegen %d in %d Stücken; Zellen mit anderem Hs %d", rs, rk, stuecke, anders), "gleich viele, bitgleich");
        rr = n.anlauf(2000, 1e-7, 600);
        double gr = 0, hmax = 0;
        for (int c = 0; c < n.nc; c++) { gr = Math.max(gr, Math.abs(hk[c] - n.hs(c))); hmax = Math.max(hmax, n.hs(c)); }
        A.pruefe(gr <= 2e-3 * hmax, "Anlauf der Karte gegen den Anlauf der Referenz (60 × 40, Insel, Strömung, Brechen, Reibung, Beugung)",
                f("größte Abweichung von Hs %.2e des größten (%.3f m); Runden Karte %d, Referenz %d; Karte %.1f s", gr / hmax, hmax, rk, rr, (t1 - t0) * 1e-9), "≤ 2e-3");
    }

    static void verschieben() {
        A.teil("9 Verschieben");
        Nahfeld n = new Nahfeld(30, 20, 10, 6, 0.06, 0.4, 24);
        Consumer<Integer> grund = d -> { for (int c = 0; c < n.nc; c++) { n.grund[c] = -8 - 0.05 * ((c % 30) + d); n.spiegel[c] = 0; n.u[c] = 0.2; } };
        grund.accept(0);
        n.vorbereiten();
        double[] e = n.jonswap(1.0, 7, 45, 8);
        Nahfeld.Ziel z = (x, y, out) -> { for (int i = 0; i < out.length; i++) out[i] = e[i] * (1 + 0.001 * x - 0.0005 * y); };
        n.setze(z);
        n.rand = n.randAus(z);
        double[] w = n.n.clone();
        double x0 = n.x0, y0 = n.y0;
        double[] hk;
        try (NahfeldKarte k = NahfeldKarte.mitGeraet(n)) {
            k.rand(n.randVarianz());
            for (int s = 0; s < 3; s++) k.schritt(5);
            k.verschieben(3, -2);
            grund.accept(3);
            n.zellen();
            k.felder();
            k.einsetzen(z);
            hk = k.hs();
        }
        n.x0 = x0; n.y0 = y0;
        grund.accept(0);
        n.vorbereiten();
        System.arraycopy(w, 0, n.n, 0, w.length);
        for (int s = 0; s < 3; s++) n.schritt(5);
        n.verschieben(3, -2);
        grund.accept(3);
        n.vorbereiten();
        n.nachVerschieben(z);
        double gr = 0, hmax = 0;
        for (int c = 0; c < n.nc; c++) { gr = Math.max(gr, Math.abs(hk[c] - n.hs(c))); hmax = Math.max(hmax, n.hs(c)); }
        A.pruefe(gr <= GRENZE * hmax, "drei Schritte, verschoben um (3, −2), neue Zellen aus dem Ziel", f("größte Abweichung von Hs %.2e des größten (%.3f m)", gr / hmax, hmax), "≤ 1e-3");
    }

    static void zeit() {
        A.teil("10 Zeit auf der Karte");
        zeit(128, 2, "128 × 128 Zellen à 2 m, 8 Bänder × 32 Richtungen, Schritt 0,5 s");
        zeit(100, 10, "100 × 100 Zellen à 10 m (wie das feine Nahfeld), Schritt 0,5 s");
    }

    static void zeit(int nz, double dz, String name) {
        Nahfeld n = new Nahfeld(nz, nz, dz, 8, 0.07, 0.6, 32);
        for (int c = 0; c < n.nc; c++) { n.grund[c] = -6 + 0.01 * (c % nz); n.spiegel[c] = 0; n.u[c] = 0.3; n.windU[c] = 12; }
        n.vorbereiten();
        double[] e = n.jonswap(1.2, 6, 0, 8);
        Nahfeld.Ziel z = (x, y, out) -> System.arraycopy(e, 0, out, 0, e.length);
        n.setze(z);
        try (NahfeldKarte k = NahfeldKarte.mitGeraet(n)) {
            k.rand(n.randVarianz(z));
            k.schritt(0.5);
            k.sTempo = k.sAusbreitung = k.sQuellen = 0;
            int s = 5, ts = 0;
            long t0 = System.nanoTime();
            for (int i = 0; i < s; i++) ts = k.schritt(0.5);
            double ms = (System.nanoTime() - t0) * 1e-6 / s;
            A.info(name, f("%.1f ms je Schritt (%d Teilschritte; Beugung und Tempo %.1f, Ausbreitung mit Quellen %.1f ms)",
                    ms, ts, k.sTempo * 1e3 / s, k.sAusbreitung * 1e3 / s));
            // je Kernel auf der Karte (Befund B39)
            try (Stoppuhr uhr = new Stoppuhr(k.geraet(), k.geraet().rechnen, 256)) {
                if (uhr.verfuegbar) {
                    k.uhr = uhr;
                    for (int i = 0; i < s; i++) k.schritt(0.5);
                    k.uhr = null;
                    StringBuilder sb = new StringBuilder();
                    double summe = 0;
                    for (java.util.Map.Entry<String, Double> kz : uhr.summen().entrySet()) { sb.append(f("%s %.2f; ", kz.getKey(), kz.getValue() / s)); summe += kz.getValue() / s; }
                    A.info(name + ": je Kernel auf der Karte, ms je Schritt (Summe " + f("%.1f", summe) + " ms)", sb.toString());
                } else A.info("je Kernel auf der Karte", "keine Zeitstempel");
            }
        }
    }
}
