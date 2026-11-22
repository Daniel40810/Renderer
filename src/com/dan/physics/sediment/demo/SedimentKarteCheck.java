package com.dan.physics.sediment.demo;

import com.dan.core.pruef.Attest;
import com.dan.physics.sediment.Sediment;
import com.dan.physics.sediment.gpu.SedimentKarte;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Das Sediment auf der Karte gegen die Referenz (Werkbuch HalligHooge Teil A, 12.10, P9a; Prüfsteine 10 und 11).
 * <p>
 * <b>Prüfstein 10:</b> drei Fälle aus {@link SedimentCheck} (Bilanzfall mit Tide, Trockenfallen, Seegang, Sand und
 * Schlick; Graben nach van Rijn über 2 h; Wolke), je dieselben Teilschritte auf dem Prozessor (double) und auf der Karte
 * (float); verglichen werden {@code sed.c} und die Bodenänderung je Zelle (nur Zellen über 5 % des größten Betrags
 * des Falls). Grenze 1 · 10⁻³ relativ.
 * <p>
 * <b>Prüfstein 11:</b> Zeiten auf der Karte im Fenster von G3 (500 × 400 Zellen, dazu 700 × 400 für P9c) und daraus
 * die Rechenzeit je 20 min Szenenzeit gegen die Vorgabe aus 12.10 (höchstens halb so viel wie hydro).
 * <p>
 * {@code --teil 10} oder {@code --teil 11}: nur dieser Teil (dann ohne Attest).
 */
public final class SedimentKarteCheck {
    static final Attest A = new Attest("com.dan.physics.sediment.gpu", Sediment.VERSION);

    /** hydro je 20 min Szenenzeit auf der 680M (P8c, SeegangCheck: 651 s und 757 s für 72 h über G0 | G1 | G2 G3). */
    static final double HYDRO_JE_20MIN = 0.5 * (651 + 757) / 216;

    public static void main(String[] args) {
        List<String> a = List.of(args);
        int ti = a.indexOf("--teil");
        int nur = ti >= 0 ? Integer.parseInt(a.get(ti + 1)) : 0;
        long t0 = System.nanoTime();
        try {
            if (nur == 0 || nur == 10) { bilanzfall(); graben(); wolke(); }
            if (nur == 0 || nur == 11) zeiten();
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (nur == 0) {
            A.info("Rechenzeit", f("%.0f s", (System.nanoTime() - t0) * 1e-9));
            Path p = A.schreibe(Path.of("docs", "status", "P9a_SedimentKarte.md"), "P9a – SedimentKarteCheck (Karte gegen Referenz, Zeiten)");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Größte und mittlere relative Abweichung über die Zellen, deren Betrag in der Referenz über 5 % des größten liegt. */
    static double[] abweichung(double[] ref, double[] karte) {
        double groesst = 0;
        for (double v : ref) groesst = Math.max(groesst, Math.abs(v));
        double max = 0, sum = 0;
        int n = 0;
        for (int c = 0; c < ref.length; c++) {
            if (Math.abs(ref[c]) <= 0.05 * groesst) continue;
            double d = Math.abs(karte[c] - ref[c]) / Math.abs(ref[c]);
            max = Math.max(max, d); sum += d; n++;
        }
        return new double[] {max, n == 0 ? 0 : sum / n, n, groesst};
    }

    /** Zahl der Teilschritte der Referenz für einen Schritt; die Karte rechnet dieselben. */
    static int teile(Sediment r, double dt) { return Math.max(1, (int) Math.ceil(dt * r.rate() / r.courant)); }

    static void vergleiche(String fall, Sediment r, Sediment k) {
        double[] cr = new double[r.nc], ck = new double[r.nc], zr = new double[r.nc], zk = new double[r.nc];
        boolean endlich = true;
        for (int c = 0; c < r.nc; c++) {
            cr[c] = r.schweb(c); ck[c] = k.schweb(c); zr[c] = r.dzS[c] + r.dzM[c]; zk[c] = k.dzS[c] + k.dzM[c];
            endlich &= Double.isFinite(ck[c]) && Double.isFinite(zk[c]);
        }
        double[] ac = abweichung(cr, ck), az = abweichung(zr, zk);
        A.pruefe(endlich, fall + ": Werte der Karte endlich", endlich ? "ja" : "nein", "ja");
        if (ac[2] > 0) A.hoechstens("Prüfstein 10, " + fall + ": sed.c, größte relative Abweichung (" + f("%.0f Zellen, größter Wert %.3g kg/m³, im Mittel %.1e", ac[2], ac[3], ac[1]) + ")", ac[0], 1e-3, "");
        if (az[3] >= 1e-9) A.hoechstens("Prüfstein 10, " + fall + ": Bodenänderung, größte relative Abweichung (" + f("%.0f Zellen, größter Wert %.3g m, im Mittel %.1e", az[2], az[3], az[1]) + ")", az[0], 1e-3, "");
        else {                                                                 // der Fall bewegt den Boden nicht: dann darf es auch die Karte nicht
            double g = 0;
            for (double v : zk) g = Math.max(g, Math.abs(v));
            A.hoechstens("Prüfstein 10, " + fall + ": Bodenänderung auf der Karte (die Referenz bewegt den Boden nicht), größter Betrag", g, 1e-9, "m");
        }
    }

    static void bilanzfall() {
        bilanzfall(false, false);
        bilanzfall(true, false);
        bilanzfall(true, true);
    }

    /** {@code p9c}: mit lockerer Auflage, Faktor 10, Deckel und Spirale (Sediment 0.3). */
    static void bilanzfall(boolean subgrid, boolean p9c) {
        A.teil("10 Karte gegen Referenz: Bilanzfall" + (subgrid ? " mit benetztem Anteil und Porosität je Zelle" : "") + (p9c ? ", dazu lockere Auflage, Faktor 10, Deckel und Spirale (P9c)" : ""));
        Sediment r = SedimentCheck.bilanzfall(60, 40, subgrid), s = SedimentCheck.bilanzfall(60, 40, subgrid);
        if (p9c)
            for (Sediment x : new Sediment[] {r, s}) {
                x.mitAuflage = true;
                x.faktor = 10;
                x.deckel = 0.05;
                x.spiral = new double[x.nc];
                for (int c = 0; c < x.nc; c++) x.spiral[c] = 0.004 * Math.sin(0.2 * (c % x.nx)) * Math.cos(0.15 * (c / x.nx));
            }
        try (SedimentKarte k = SedimentKarte.mitGeraet(s)) {
            for (int n = 0; n < 240; n++) {
                SedimentCheck.bilanzfluss(r, n * 30.0, 30);
                int teile = teile(r, 30);
                for (int t = 0; t < teile; t++) r.teil(30.0 / teile);
                k.runter();
                SedimentCheck.bilanzfluss(s, n * 30.0, 30);
                k.eingang();
                k.teilschritte(teile, 30.0 / teile);
            }
            k.runter();
        }
        A.info("Teilschritte", f("Referenz %d, Karte %d", r.teilschritte, s.teilschritte));
        vergleiche("Bilanzfall (2 h)", r, s);
        double rs = r.aus[0] + r.aus[2], ks = s.aus[0] + s.aus[2];
        A.hoechstens("Fluss über den Rand, Karte gegen Referenz: Sand (" + f("%.1f t", rs / 1e3) + "), relativ", (ks - rs) / rs, 1e-3, "");
        A.hoechstens("Fluss über den Rand, Karte gegen Referenz: Schlick (" + f("%.1f t", r.aus[1] / 1e3) + "), relativ", (s.aus[1] - r.aus[1]) / r.aus[1], 1e-3, "");
        double[] rip = abweichung(r.rH, s.rH);
        A.info("Rippelhöhe, größte relative Abweichung", f("%.1e (%.0f Zellen)", rip[0], rip[2]));
        if (p9c) {
            double[] au = abweichung(r.mAuflage, s.mAuflage), sw = abweichung(r.sandWirklich, s.sandWirklich), wr = r.wirklich(), wk = s.wirklich();
            A.hoechstens("P9c: lockere Auflage, größte relative Abweichung (" + f("%.0f Zellen, größter Wert %.3g kg/m², im Mittel %.1e", au[2], au[3], au[1]) + ")", au[0], 1e-3, "");
            A.hoechstens("P9c: wirklich aufgenommener Sand, größte relative Abweichung (" + f("%.0f Zellen, größter Wert %.3g kg/m², im Mittel %.1e", sw[2], sw[3], sw[1]) + ")", sw[0], 1e-3, "");
            A.info("P9c: Teilschritte am Deckel", f("Referenz %.0f, Karte %.0f", wr[3], wk[3]));
        }
    }

    static Sediment grabenfall() {
        int nx = 300;
        double dx = 0.1, tief = 0.15, anfang = 8, boeschung = 1.5, sohle = 3;
        Sediment s = new Sediment(nx, 1, dx, dx);
        s.mitSchlick = false;
        s.rho = 1000; s.nu = 1.0e-6;
        Arrays.fill(s.d50, 1.6e-4);
        for (int i = 0; i < nx; i++) {
            double x = (i + 0.5) * dx - anfang;
            s.grund[i] = x < 0 || x > 2 * boeschung + sohle ? 0 : x < boeschung ? -tief * x / boeschung : x < boeschung + sohle ? -tief : -tief * (2 * boeschung + sohle - x) / boeschung;
        }
        return s;
    }

    static void graben() {
        A.teil("10 Karte gegen Referenz: Graben");
        double h0 = 0.39, q = h0 * 0.51;
        Sediment r = grabenfall(), s = grabenfall();
        try (SedimentKarte k = SedimentKarte.mitGeraet(s)) {
            for (int n = 0; n < 2 * 3600 / 5; n++) {
                for (int i = 0; i < r.nx; i++) r.h[i] = h0 - r.boden(i);
                Arrays.fill(r.qx, q);
                int teile = teile(r, 5);
                for (int t = 0; t < teile; t++) r.teil(5.0 / teile);
                k.runter();
                for (int i = 0; i < s.nx; i++) s.h[i] = h0 - s.boden(i);
                Arrays.fill(s.qx, q);
                k.eingang();
                k.teilschritte(teile, 5.0 / teile);
            }
            k.runter();
        }
        A.info("Teilschritte", f("Referenz %d, Karte %d", r.teilschritte, s.teilschritte));
        vergleiche("Graben (2 h)", r, s);
    }

    static Sediment wolkenfall() {
        int n = 80;
        Sediment s = new Sediment(n, n, 10, 10);
        SedimentCheck.nurSchalter(s, false, false, true, false, false, true);
        s.tauD = 0;
        Arrays.fill(s.h, 3);
        Arrays.fill(s.qx, 1.5);
        Arrays.fill(s.qy, 0.9);
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            double x = (i + 0.5) * 10 - 250, y = (j + 0.5) * 10 - 250;
            s.cm[s.zelle(i, j)] = Math.exp(-(x * x + y * y) / (2 * 40 * 40));
        }
        return s;
    }

    static void wolke() {
        A.teil("10 Karte gegen Referenz: Wolke");
        Sediment[] paar = {wolkenfall(), wolkenfall()};
        for (int n = 0; n < 40; n++) paar[0].schritt(10);
        try (SedimentKarte k = SedimentKarte.mitGeraet(paar[1])) {
            for (int n = 0; n < 40; n++) k.schritt(10);
            k.runter();
        }
        vergleiche("Wolke (400 s)", paar[0], paar[1]);
        // Auskunft: die Referenz gegen sich selbst, wenn die Konzentration nach jedem Schritt auf float gerundet wird
        Sediment e = wolkenfall();
        for (int n = 0; n < 40; n++) {
            e.schritt(10);
            for (int c = 0; c < e.nc; c++) e.cm[c] = (float) e.cm[c];
        }
        double[] er = abweichung(paar[0].cm, e.cm);
        A.info("Empfindlichkeit: Referenz gegen Referenz mit Rundung auf float je Schritt", f("größte relative Abweichung %.1e, im Mittel %.1e (der Begrenzer zweiter Ordnung verstärkt Rundung an den Flanken der Wolke)", er[0], er[1]));
    }

    static void zeiten() {
        A.teil("11 Zeiten auf der Karte");
        for (int[] gr : new int[][] {{500, 400}, {700, 400}}) {
            Sediment s = SedimentCheck.bilanzfall(gr[0], gr[1]);
            SedimentCheck.bilanzfluss(s, 600, 30);
            try (SedimentKarte k = SedimentKarte.mitGeraet(s)) {
                k.teilschritte(16, 2);
                long t0 = System.nanoTime();
                int n = 400;
                k.teilschritte(n, 2);
                double sek = (System.nanoTime() - t0) * 1e-9, ns = sek * 1e9 / n / s.nc;
                // Teilschritt im Feld [S]: Courant 0,8 bei 2 m/s auf 20 m, also 8 s; 150 Teilschritte je 20 min
                double je20 = ns * 1e-9 * s.nc * 150;
                A.info(gr[0] + " × " + gr[1] + " Zellen", f("%.1f ns je Zelle und Teilschritt (%d Teilschritte in %.2f s, mit Böschungsrutsch); je 20 min Szenenzeit bei 150 Teilschritten [S] %.2f s", ns, n, sek, je20));
                if (gr[0] == 500) {
                    A.info("hydro je 20 min Szenenzeit (P8c)", f("%.1f s", HYDRO_JE_20MIN));
                    A.info("Prüfstein 11, Vorgabe (höchstens halb so viel wie hydro)", je20 <= 0.5 * HYDRO_JE_20MIN ? f("hält: %.2f s ≤ %.2f s", je20, 0.5 * HYDRO_JE_20MIN)
                            : f("hält nicht: %.2f s > %.2f s; das Sediment rechnet dann in größeren Schritten", je20, 0.5 * HYDRO_JE_20MIN));
                }
            }
        }
    }
}
