package com.dan.physics.waves.demo;

import com.dan.core.pruef.Attest;
import com.dan.physics.waves.Waves;
import com.dan.physics.waves.gpu.WavesKarte;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.dan.core.pruef.Attest.f;

/**
 * Der Seegang auf der Karte gegen die Referenz (Werkbuch HalligHooge Teil A, 12.9, P8a; Prüfsteine 6 und 8).
 * <p>
 * <b>Prüfstein 6:</b> dieselben Fälle wie {@link WavesCheck} (Refraktion, Brechen, Untiefe, Gegenstrom, Bilanzfall mit
 * allen Quellen und Strömung, Wachstum im Wind), dazu der schräge Strand aus dem ersten Teil; je Fall dieselben Schritte
 * auf dem Prozessor (double) und auf der Karte (float), dann Hs je Zelle verglichen (nur Zellen über 5 % der größten
 * Wellenhöhe des Falls). Grenze 1 · 10⁻³ relativ.
 * <p>
 * <b>Prüfstein 8:</b> Zeiten auf der Karte mit Strömung, Wind und allen Quellen in den drei Auflösungen aus 12.9, dazu
 * 28 × 72 (Kosten der Richtung, Abweichung W2); daraus die Rechenzeit für G2 + G3 je 20 min Szenenzeit nach derselben
 * Formel wie {@link WavesCheck} (Courant 0,8 bei 14 m/s, Quellen alle 60 s), und die Vorgabe aus 12.9.
 * <p>
 * {@code --teil 6} oder {@code --teil 8}: nur dieser Teil (dann ohne Attest). {@code --kurz}: Prüfstein 6 mit
 * Untiefe in 0,4 m und halber Zahl der Schritte (für die Arbeitsumgebung).
 */
public final class WavesKarteCheck {
    static final Attest A = new Attest("com.dan.physics.waves.gpu", Waves.VERSION);
    static boolean kurz;

    /**
     * Maßstab hydro [G aus dem Werkbuch]: G2 → G3 auf der 680M im Zeitraffer 25,0 (P4d, Fall 14), also 48 s je 20 min
     * Szenenzeit. Live: Zeitraffer 136 für Physik und Bild zusammen (BildCheck, 16.11.), 8,8 s je 20 min; hydro kostet
     * live höchstens das, ein Fünftel davon ist die Grenze für den Seegang live [S].
     */
    static final double HYDRO_ARCHIV = 1200 / 25.0, LIVE_GESAMT = 1200 / 136.0;

    public static void main(String[] args) {
        List<String> a = List.of(args);
        int ti = a.indexOf("--teil");
        int nur = ti >= 0 ? Integer.parseInt(a.get(ti + 1)) : 0;
        kurz = a.contains("--kurz");
        long t0 = System.nanoTime();
        try {
            if (nur == 0 || nur == 6) vergleich();
            if (nur == 0 || nur == 8) zeiten();
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (nur == 0) {
            A.info("Rechenzeit", f("%.0f s", (System.nanoTime() - t0) * 1e-9));
            Path p = A.schreibe(Path.of("docs", "status", "P8a_WavesKarte.md"), "P8a – WavesKarteCheck (Karte gegen Referenz, Zeiten)");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Fälle (wie WavesCheck)

    /** {@code empfindlich}: dazu die Referenz gegen sich selbst, einmal nach dem zehnten Schritt um 1 · 10⁻⁷ gestört (Auskunft). */
    record Fall(String name, Supplier<Waves> bau, double dt, int schritte, boolean empfindlich) {
        Fall(String name, Supplier<Waves> bau, double dt, int schritte) { this(name, bau, dt, schritte, false); }
    }

    static Waves strand(int nx, int ny, int nf, int nd) {
        Waves w = new Waves(nx, ny, 50, 50, nf, 0.04, 0.6, nd);
        WavesCheck.alleQuellenAus(w);
        w.periodischY = true;
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) w.tiefe[j * nx + i] = 20 - 19.0 * (i + 0.5) / nx + 0.5 * Math.sin(2 * Math.PI * j / ny);
        w.vorbereiten();
        double[] ef = WavesCheck.jonswap(w, 1.0, 8, 3.3), dv = WavesCheck.verteilung(w, Math.toRadians(30), 10);
        w.randW = (i, j, m, q) -> ef[m] * dv[q];
        return w;
    }

    static Waves refraktion() {
        int nx = 100;
        Waves w = new Waves(nx, 1, 50, 50, 5, 0.08, 0.125, 36);
        WavesCheck.alleQuellenAus(w);
        w.periodischY = true;
        for (int i = 0; i < nx; i++) w.tiefe[i] = 20 - 19.0 * (i + 0.5) / nx;
        w.vorbereiten();
        double[] ef = WavesCheck.jonswap(w, 1.0, 10, 3.3), dv = WavesCheck.verteilung(w, Math.toRadians(30), 30);
        w.randW = (i, j, m, q) -> ef[m] * dv[q];
        return w;
    }

    static Waves brechen() {
        int nx = 150;
        double dx = 10, d0 = 15.5, gefaelle = 0.01;
        Waves w = new Waves(nx, 1, dx, dx, 14, 0.06, 0.35, 36);
        WavesCheck.alleQuellenAus(w);
        w.mitBrechen = true;
        w.periodischY = true;
        for (int i = 0; i < nx; i++) w.tiefe[i] = d0 - gefaelle * (i + 0.5) * dx;
        w.vorbereiten();
        double[] ef = WavesCheck.jonswap(w, 2.0, 8, 3.3);
        w.randW = (i, j, m, q) -> q == 0 ? ef[m] / w.dTheta : 0;
        return w;
    }

    /** Fall B1 von Vincent und Briggs (σ = 30°), wie WavesCheck.untiefe. */
    static Waves untiefe() {
        double dx = kurz ? 0.4 : 0.2, tief = 0.4572, x0 = 6.1, y0 = 13.7, hs0 = 0.0775, sm = Math.toRadians(30);
        int nx = (int) Math.round(16 / dx), ny = (int) Math.round(27.4 / dx);
        Waves w = new Waves(nx, ny, dx, dx, 16, 0.45, 2.0, 36);
        WavesCheck.alleQuellenAus(w);
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            double x = (i + 0.5) * dx - x0, y = (j + 0.5) * dx - y0;
            boolean innen = x * x / (3.05 * 3.05) + y * y / (3.96 * 3.96) < 1;
            w.tiefe[j * nx + i] = innen ? Math.min(tief, 0.9144 - 0.7620 * Math.sqrt(1 - x * x / (3.81 * 3.81) - y * y / (4.95 * 4.95))) : tief;
        }
        w.vorbereiten();
        double[] ef = WavesCheck.jonswap(w, 1, 1.3, 2);
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
        return w;
    }

    static Waves gegenstrom() {
        int nx = 100;
        double dx = 50, d = 30;
        Waves w = new Waves(nx, 1, dx, dx, 28, 0.04, 0.6, 36);
        WavesCheck.alleQuellenAus(w);
        w.periodischY = true;
        for (int i = 0; i < nx; i++) {
            w.tiefe[i] = d;
            w.u[i] = -1.0 * Math.max(0, Math.min(1, ((i + 0.5) * dx - 1000) / 2000));
        }
        w.vorbereiten();
        double[] ef = new double[w.nf];
        double s0 = 0;
        for (int m = 0; m < w.nf; m++) { ef[m] = Math.exp(-0.5 * Math.pow((w.f[m] - 0.15) / 0.02, 2)); s0 += ef[m] * w.df[m]; }
        for (int m = 0; m < w.nf; m++) ef[m] *= 1.0 / 16 / s0;
        w.randW = (i, j, m, q) -> q == 0 ? ef[m] / w.dTheta : 0;
        return w;
    }

    /** Untiefe, die trockenfällt, Strömung in x und y, Wind 15 m/s, alle Quellen (wie WavesCheck.bilanz). */
    static Waves alles() {
        int nx = 30, ny = 20;
        Waves w = new Waves(nx, ny, 200, 200, 28, 0.04, 0.6, 36);
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            int c = j * nx + i;
            double x = (i + 0.5 - 18) * 200, y = (j + 0.5 - 10) * 200, r2 = (x * x + y * y) / (1200.0 * 1200.0);
            w.tiefe[c] = 10 - 9.98 * Math.exp(-r2 * 4) - 0.2 * i;
            w.u[c] = 0.8 * Math.sin(Math.PI * (j + 0.5) / ny); w.v[c] = 0.3 * Math.cos(Math.PI * (i + 0.5) / nx);
            w.windU[c] = 15 * Math.cos(Math.toRadians(30)); w.windV[c] = 15 * Math.sin(Math.toRadians(30));
        }
        w.vorbereiten();
        double[] ef = WavesCheck.jonswap(w, 1.5, 7, 3.3), dv = WavesCheck.verteilung(w, Math.toRadians(10), 4);
        w.randW = (i, j, m, q) -> ef[m] * dv[q];
        return w;
    }

    /** Tiefes Wasser, Wind längs x, Quellen des tiefen Wassers, aus der Ruhe (wie WavesCheck.wachstum, Gitter 250 m). */
    static Waves wachstum(double u10) {
        int nx = 101;
        Waves w = new Waves(nx, 1, 250, 250, 28, 0.04, 0.6, 36);
        w.mitReibung = false; w.mitBrechen = false; w.mitRefraktion = false;
        w.periodischY = true;
        for (int i = 0; i < nx; i++) { w.tiefe[i] = 2000; w.windU[i] = u10; }
        w.vorbereiten();
        return w;
    }

    static List<Fall> faelle() {
        int h = kurz ? 2 : 1;
        List<Fall> l = new ArrayList<>();
        l.add(new Fall("Strand schräg (100 × 24 Zellen, nur Ausbreitung)", () -> strand(100, 24, 28, 36), 10, 60 / h));
        l.add(new Fall("2 Refraktion, 30°", WavesKarteCheck::refraktion, 10, 400 / h));
        l.add(new Fall("3 Brechen", WavesKarteCheck::brechen, 5, 600 / h));
        l.add(new Fall("4 Untiefe B1", WavesKarteCheck::untiefe, 0.5, 90 / h));
        l.add(new Fall("5 Gegenstrom", WavesKarteCheck::gegenstrom, 5, 600 / h));
        l.add(new Fall("7 Untiefe, Strömung, Wind, alle Quellen", WavesKarteCheck::alles, 30, 120 / h, true));
        l.add(new Fall("1 Wachstum 10 m/s", () -> wachstum(10), 30, 240 / h));
        l.add(new Fall("1 Wachstum 20 m/s", () -> wachstum(20), 30, 240 / h));
        return l;
    }

    /** Das einlaufende Spektrum (in allen Fällen an jedem Rand gleich) und die Bits der Ränder. */
    static double[] rand(Waves w, int[] bits) {
        Waves.Rand r = null;
        bits[0] = 0;
        if (w.randW != null) { r = w.randW; bits[0] |= 1; }
        if (w.randO != null) { r = w.randO; bits[0] |= 2; }
        if (w.randS != null) { r = w.randS; bits[0] |= 4; }
        if (w.randN != null) { r = w.randN; bits[0] |= 8; }
        if (r == null) return null;
        double[] s = new double[w.nf * w.nd];
        for (int m = 0; m < w.nf; m++) for (int q = 0; q < w.nd; q++) s[m * w.nd + q] = r.e(0, 0, m, q);
        return s;
    }

    // ------------------------------------------------------------------ 6 Referenz gegen Karte

    static void vergleich() {
        A.teil("6 Referenz (double) gegen Karte (float)");
        double gesamt = 0;
        String schlimmster = "";
        for (Fall fall : faelle()) {
            Waves ref = fall.bau.get(), kar = fall.bau.get();
            int[] bits = new int[1];
            double[] sp = rand(kar, bits);
            long t0 = System.nanoTime();
            for (int s = 0; s < fall.schritte; s++) ref.schritt(fall.dt);
            double tRef = (System.nanoTime() - t0) * 1e-9;
            int teil = 0;
            double tKar;
            try (WavesKarte k = WavesKarte.mitGeraet(kar, sp, bits[0])) {
                t0 = System.nanoTime();
                for (int s = 0; s < fall.schritte; s++) teil += k.schritt(fall.dt);
                k.runter();
                tKar = (System.nanoTime() - t0) * 1e-9;
            }
            double hmax = 0;
            for (int c = 0; c < ref.nc; c++) hmax = Math.max(hmax, ref.hs(c));
            double max = 0, tm = 0, ri = 0;
            int wo = 0, zellen = 0;
            boolean heil = true;
            for (double v : kar.e) if (!Double.isFinite(v) || v < 0) { heil = false; break; }
            for (int c = 0; c < ref.nc; c++) {
                double a = ref.hs(c), b = kar.hs(c);
                if (!(a > 0.05 * hmax)) continue;
                zellen++;
                double ab = Math.abs(b / a - 1);
                if (!(ab <= max)) { max = ab; wo = c; }
                tm = Math.max(tm, Math.abs(kar.tm01(c) / ref.tm01(c) - 1));
                double dr = Math.abs(kar.richtung(c) - ref.richtung(c));
                ri = Math.max(ri, Math.min(dr, 360 - dr));
            }
            A.info(fall.name, f("%d × %d Zellen, %d × %d Stützstellen, %d Schritte zu %.1f s (%d Teilschritte), Hs bis %.3f m, %d Zellen verglichen; Prozessor %.1f s, Karte %.1f s",
                    ref.nx, ref.ny, ref.nf, ref.nd, fall.schritte, fall.dt, teil, hmax, zellen, tRef, tKar));
            A.hoechstens(fall.name + ": Hs, größte relative Abweichung", max, 1e-3, "");
            A.info(fall.name + ": Ort; Tm01; Richtung", f("Zelle %d, %d: %.5f gegen %.5f m; Tm01 bis %.1e relativ; Richtung bis %.3f°",
                    wo % ref.nx, wo / ref.nx, kar.hs(wo), ref.hs(wo), tm, ri));
            if (!heil) A.pruefe(false, fall.name + ": Spektrum der Karte endlich und nicht negativ", "nein", "ja");
            if (fall.empfindlich) {
                Waves st = fall.bau.get();
                java.util.Random rnd = new java.util.Random(1);
                for (int s = 0; s < fall.schritte; s++) {
                    st.schritt(fall.dt);
                    if (s == 9) for (int n = 0; n < st.e.length; n++) st.e[n] *= 1 + 1e-7 * rnd.nextGaussian();
                }
                double se = 0;
                for (int c = 0; c < ref.nc; c++) if (ref.hs(c) > 0.05 * hmax) se = Math.max(se, Math.abs(st.hs(c) / ref.hs(c) - 1));
                A.info(fall.name + ": Empfindlichkeit der Referenz", f("einmal um 1 · 10⁻⁷ gestört (nach Schritt 10), am Ende in Hs bis %.2e: so weit trägt das Verfahren selbst eine Störung in der Größe der Rundung von float", se));
            }
            if (max > gesamt) { gesamt = max; schlimmster = fall.name; }
        }
        A.info("Alle Fälle: größte relative Abweichung in Hs", f("%.2e (%s)", gesamt, schlimmster));
    }

    // ------------------------------------------------------------------ 8 Zeiten

    static void zeiten() {
        A.teil("8 Zeiten auf der Karte mit Strömung und allen Quellen");
        int[][] aufl = {{28, 36}, {24, 24}, {20, 18}, {28, 72}};
        double[] g2g3 = new double[aufl.length];
        for (int r = 0; r < aufl.length; r++) g2g3[r] = zeit(aufl[r][0], aufl[r][1], r == 3);
        A.teil("8 Vorgabe für die Auflösung (12.9)");
        A.info("Maßstab", f("hydro G2 → G3 im Archiv %.0f s je 20 min Szenenzeit (P4d, Fall 14, Zeitraffer 25); live für Physik und Bild zusammen %.1f s (Zeitraffer 136), ein Fünftel davon %.1f s",
                HYDRO_ARCHIV, LIVE_GESAMT, LIVE_GESAMT / 5));
        String archiv = "keine", live = "keine";
        for (int r = 0; r < 3; r++) {
            if (archiv.equals("keine") && g2g3[r] <= HYDRO_ARCHIV) archiv = f("%d × %d (%.0f s)", aufl[r][0], aufl[r][1], g2g3[r]);
            if (live.equals("keine") && g2g3[r] <= LIVE_GESAMT / 5) live = f("%d × %d (%.1f s)", aufl[r][0], aufl[r][1], g2g3[r]);
        }
        A.info("Archiv: feinste Auflösung, die höchstens so viel kostet wie hydro", archiv);
        A.info("Live: feinste Auflösung, die höchstens ein Fünftel kostet", live.equals("keine") ? "keine – live bleibt der Seegang aus dem Wind (wie bisher)" : live);
        A.info("72 statt 36 Richtungen (W2)", f("%.1f-fache Rechenzeit (28 × 72: %.0f s gegen 28 × 36: %.0f s)", g2g3[3] / g2g3[0], g2g3[3], g2g3[0]));
    }

    /** ns je Wert für einen Teilschritt der Ausbreitung und einen Schritt der Quellen; gibt die Zeit für G2 + G3 je 20 min (s). */
    static double zeit(int nf, int nd, boolean auskunft) {
        int nx = kurz ? 60 : 240, ny = kurz ? 40 : 160;
        Waves w = new Waves(nx, ny, 100, 100, nf, 0.04, 0.6, nd);
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            int c = j * nx + i;
            w.tiefe[c] = 12 - 10.0 * i / nx + 2 * Math.sin(0.3 * j);
            w.u[c] = 0.5 * Math.sin(Math.PI * (j + 0.5) / ny); w.v[c] = 0.2;
            w.windU[c] = 15;
        }
        w.vorbereiten();
        double[] ef = WavesCheck.jonswap(w, 1.5, 7, 3.3), dv = WavesCheck.verteilung(w, 0, 4), sp = new double[nf * nd];
        for (int m = 0; m < nf; m++) for (int q = 0; q < nd; q++) sp[m * nd + q] = ef[m] * dv[q];
        for (int c = 0; c < w.nc; c++) System.arraycopy(sp, 0, w.e, c * nf * nd, nf * nd);
        double sekA, sekQ;
        int nA = 96, nQ = 24;
        long werte;
        try (WavesKarte k = WavesKarte.mitGeraet(w, sp, 1)) {
            werte = k.anzahl;
            k.teilschritte(16, 1.0);
            k.quellen(60);                                                          // warm
            long t0 = System.nanoTime();
            k.teilschritte(nA, 1.0);
            sekA = (System.nanoTime() - t0) * 1e-9;
            t0 = System.nanoTime();
            for (int s = 0; s < nQ; s++) k.quellen(60);
            sekQ = (System.nanoTime() - t0) * 1e-9;
        }
        double nsA = sekA / nA / werte * 1e9, nsQ = sekQ / nQ / werte * 1e9;
        double g2 = 148.0 * 172 * nf * nd, g3 = 240.0 * 320 * nf * nd;
        double zeit = (g2 * (1200 / (0.8 * 500 / 14.0) * nsA + 20 * nsQ) + g3 * (1200 / (0.8 * 100 / 14.0) * nsA + 20 * nsQ)) * 1e-9;
        A.info(f("%d × %d Stützstellen%s", nf, nd, auskunft ? " (Auskunft, W2)" : ""), f("Ausbreitung %.2f ns je Wert und Teilschritt, Quellen %.2f ns je Wert und Schritt (%.1f Mio. Werte); G2 + G3 je 20 min Szenenzeit: %.1f s",
                nsA, nsQ, werte * 1e-6, zeit));
        return zeit;
    }
}
