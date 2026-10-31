package com.dan.wake.demo;

import com.dan.wake.Wake;

import java.util.Locale;

/**
 * Prüfung ohne Fenster (Paketvertrag Punkt 10) für {@code wake} (Hülle seit Timmendorf Phase 1, Physik seit Phase 8):
 * Grundgrößen, Keilwinkel eines Druckpunkts (19,47°) und des Musters aus Bug und Heck, Wellenlänge der Querwellen
 * gegen 2πV²/g, Abnahme mit dem Abstand (1/√r quer laufend, r^(−1/3) am Rand des Keils), engerer Keil bei hoher
 * Froude-Zahl (Rabaud und Moisy 2013), Höhen gegen Messungen [G], Spur in der Kurve, Schaum, Schwell am Ufer,
 * Kachel gleich Punkt, Wiederholbarkeit. Endet mit „Alles in Ordnung.“ oder mit den Verstößen (Rückgabewert 1).
 */
public final class WakeCheck {
    private static int bad, good;

    public static void main(String[] args) {
        System.out.println("com.dan.wake – Prüfung");
        grund();
        keil();
        muster();
        hoehen();
        spur();
        schwell();
        System.out.println();
        System.out.println(good + " Prüfungen bestanden, " + bad + " fehlgeschlagen.");
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void grund() {
        System.out.println("Grundgrößen");
        check(Math.abs(Wake.KELVIN_DEG - 19.4712) < 1e-3, f("Kelvin-Keil %.4f° (arcsin 1/3)", Wake.KELVIN_DEG));
        check(Math.abs(Wake.transverseWavelength(5) - 2 * Math.PI * 25 / 9.81) < 1e-9, f("Querwellen bei 5 m/s: λ = %.2f m", Wake.transverseWavelength(5)));
        check(Math.abs(Wake.wakeAngleDeg(0.3) - Wake.KELVIN_DEG) < 1e-12 && Wake.wakeAngleDeg(3) < Wake.wakeAngleDeg(1.5), "Rabaud und Moisy: langsam der volle Keil, schnell enger");
        Wake w = Wake.luebeckerBucht();
        Wake.Source s = w.add("Fähre", 0, 0, 90, 10, 200);
        check(Wake.depthFroude(10, 12) < 1, "Fähre mit 10 m/s in 12 m Wasser: unterkritisch");
        check(Wake.inWedge(s, -500, 0) && !Wake.inWedge(s, 500, 0), "hinter der Fähre im Keil, voraus nicht");
        w.step(60);
        check(Math.abs(s.x - 600) < 1e-9 && Math.abs(s.z) < 1e-9, "Autopilot (Hülle): Kurs Ost, 60 s mit 10 m/s sind 600 m nach +x");
    }

    /** Ein Druckpunkt allein: die stärksten Wellen am Rand des Keils, bei 19,47°. */
    static void keil() {
        System.out.println("Keil");
        double k0 = 9.81 / 9, r = 400, best = 0, ang = 0;
        double[] o = new double[4];
        for (double d = 3; d <= 30; d += 0.05) {
            Wake.branches(r * Math.cos(Math.toRadians(d)), r * Math.sin(Math.toRadians(d)), k0, 1, 1e9, o);
            double e = o[0] + o[2];
            if (e > best) { best = e; ang = d; }
        }
        check(Math.abs(ang - Wake.KELVIN_DEG) < 0.6, f("Druckpunkt: abseits der Achse die stärkste Welle unter %.2f° (Kelvin 19,47°)", ang));
        Wake.branches(r * Math.cos(Math.toRadians(25)), r * Math.sin(Math.toRadians(25)), k0, 0, 1e9, o);
        check(o[0] + o[2] < 0.05 * best, f("außerhalb des Keils (25°) klingt es ab: %.1f %% der Spitze", (o[0] + o[2]) / best * 100));
        // Abnahme: am Rand r^(−1/3), auf der Achse r^(−1/2)
        double c1 = edge(k0, 400), c2 = edge(k0, 3200);
        Wake.branches(400, 0, k0, 0, 1e9, o);
        double a1 = o[0];
        Wake.branches(1600, 0, k0, 0, 1e9, o);
        double a2 = o[0];
        check(Math.abs(c2 / c1 - Math.pow(8, -1.0 / 3)) < 0.06 && Math.abs(a2 / a1 - 0.5) < 0.02,
                f("Abnahme: am Rand des Keils ×%.2f auf achtfachen Abstand (r^−⅓: %.2f), auf der Achse ×%.2f auf vierfachen (r^−½: 0,50)", c2 / c1, Math.pow(8, -1.0 / 3), a2 / a1));
    }

    static double edge(double k0, double r) {
        double best = 0;
        double[] o = new double[4];
        for (double d = 17; d <= 21; d += 0.02) {
            Wake.branches(r * Math.cos(Math.toRadians(d)), r * Math.sin(Math.toRadians(d)), k0, 1, 1e9, o);
            best = Math.max(best, o[0] + o[2]);
        }
        return best;
    }

    /** Das Muster einer Quelle (Bug und Heck) hinter einem Boot auf geradem Kurs. */
    static void muster() {
        System.out.println("Muster aus Bug und Heck");
        double[] fr = {0.4, 1.0, 2.0};
        double[] maxAng = new double[3], edgeAng = new double[3];
        for (int c = 0; c < 3; c++) {
            double L = 10, V = fr[c] * Math.sqrt(9.81 * L);
            Wake w = new Wake();
            w.depth = 1000;
            Wake.Source s = w.add("Boot", 0, 0, 90, V, L, 3, 0.5, false);
            for (int i = 0; i < (int) (30 * L / V / 0.05) + 50; i++) w.step(0.05);
            double[] b = s.bow();
            double r = 10 * L, best = 0;
            double[] env = new double[121];
            for (int k = 0; k <= 120; k++) {
                double d = k * 0.25;
                env[k] = w.envelope(s, b[0] - r * Math.cos(Math.toRadians(d)), b[1] + r * Math.sin(Math.toRadians(d)));
                if (env[k] > best) { best = env[k]; maxAng[c] = d; }
            }
            for (int k = 0; k <= 120; k++) if (env[k] >= 0.3 * best) edgeAng[c] = k * 0.25;
        }
        check(Math.abs(edgeAng[0] - Wake.KELVIN_DEG) < 1.5, f("J/70-ähnlich (Fr 0,4): der Keil endet bei %.1f° (Kelvin 19,47°)", edgeAng[0]));
        check(maxAng[1] < 12.5 && maxAng[2] < 7.5 && maxAng[2] < maxAng[1],
                f("schnell (Fr 1 und 2): die stärkste Welle unter %.1f° und %.1f° – enger, wie Rabaud und Moisy (%.1f° und %.1f°)", maxAng[1], maxAng[2], Wake.wakeAngleDeg(1), Wake.wakeAngleDeg(2)));
        // Wellenlänge der Querwellen auf der Achse
        double L = 6.2, V = 3;
        Wake w = new Wake();
        w.depth = 1000;
        Wake.Source s = w.add("J/70", 0, 0, 90, V, L, 2.25, 0.35, false);
        for (int i = 0; i < 400; i++) w.step(0.1);
        double[] b = s.bow();
        int zc = 0;
        double first = -1, last = -1, prev = w.height(s, b[0] - 3 * L, b[1]);
        for (double x = 3 * L; x <= 9 * L; x += L / 400) {
            double h = w.height(s, b[0] - x, b[1]);
            if (prev < 0 && h >= 0) { zc++; if (first < 0) first = x; last = x; }
            prev = h;
        }
        double lam = (last - first) / Math.max(1, zc - 1);
        check(Math.abs(lam / Wake.transverseWavelength(V) - 1) < 0.02, f("Querwellen hinter der J/70 bei 3 m/s: %.2f m (2πV²/g = %.2f m)", lam, Wake.transverseWavelength(V)));
        check(w.height(s, b[0] + 20, b[1]) == 0 && w.height(s, b[0] - 30, b[1] + 60) == 0, "vor dem Bug und weit neben der Spur: still");
    }

    /** Höhen gegen Beobachtungen [G]. */
    static void hoehen() {
        System.out.println("Höhen");
        double[][] c = {{200, 9.3, 30, 6.5, 100}, {6.2, 3.0, 2.25, 0.35, 6.2}, {2.0, 1.4, 1.13, 0.12, 2}};
        String[] n = {"Fähre 200 m bei 18 kn", "J/70 bei 6 kn", "Optimist bei 2,7 kn"};
        double[][] want = {{0.15, 0.6}, {0.01, 0.08}, {0.002, 0.02}};
        for (int k = 0; k < 3; k++) {
            Wake w = new Wake();
            w.depth = 100;
            Wake.Source s = w.add(n[k], 0, 0, 90, c[k][1], c[k][0], c[k][2], c[k][3], k == 0);
            for (int i = 0; i < (int) (20 * c[k][0] / c[k][1] / 0.1) + 30; i++) w.step(0.1);
            double[] b = s.bow();
            double mx = 0;
            for (double x = 0.1 * c[k][4]; x < 6 * c[k][4]; x += c[k][0] / 300) mx = Math.max(mx, Math.abs(w.height(s, b[0] - x, b[1] + c[k][4])));
            check(mx > want[k][0] && mx < want[k][1], f("%s: bis %.3f m in %.0f m Abstand [G %.3f bis %.2f]", n[k], mx, c[k][4], want[k][0], want[k][1]));
        }
        Wake w = new Wake();
        w.depth = 4;
        Wake.Source s = w.add("Fähre", 0, 0, 90, 6, 200, 30, 6.5, true);
        for (int i = 0; i < 2000; i++) w.step(0.1);
        double[] b = s.bow();
        double deep = 0, shallow;
        w.depth = 100;
        for (double x = 50; x < 600; x += 1) deep = Math.max(deep, Math.abs(w.height(s, b[0] - x, b[1] + 200)));
        w.depth = 4;
        shallow = 0;
        for (double x = 50; x < 600; x += 1) shallow = Math.max(shallow, Math.abs(w.height(s, b[0] - x, b[1] + 200)));
        check(shallow < deep, f("in 4 m Wasser (Fr_h %.2f) bricht der Keil zusammen: %.2f m statt %.2f m", Wake.depthFroude(6, 4), shallow, deep));
    }

    /** Spur in der Kurve, Schaum, Kachel. */
    static void spur() {
        System.out.println("Spur, Schaum, Kachel");
        Wake w = new Wake();
        w.depth = 100;
        Wake.Source s = w.add("Optimist", 0, 0, 90, 1.5, 2.3, 1.13, 0.12, false);
        s.autopilot = false;
        double x = 0, z = 0, h = 90;
        for (int i = 0; i < 600; i++) {
            double dt = 0.1;
            if (i > 300 && i < 360) h -= 1.5;           // Wende um 90° nach Nord
            x += Math.sin(Math.toRadians(h)) * 1.5 * dt;
            z -= Math.cos(Math.toRadians(h)) * 1.5 * dt;
            w.moveTo(s, x, z, h, 1.5);
            w.step(dt);
        }
        // auf der alten Strecke (vor der Wende) liegt noch Kielwasser
        double old = 0;
        for (double q = 0; q < 2; q += 0.05) old = Math.max(old, Math.abs(w.height(s, 25 + q, 0.3)));
        boolean finite = true;
        for (int i = 0; i < 400; i++) finite &= Double.isFinite(w.height(s, -10 + i * 0.2, -40 + i * 0.25));
        check(old > 1e-4 && finite, f("die Spur folgt der Wende: auf der alten Strecke noch %.1f mm, überall endlich (%d Spurpunkte)", old * 1000, s.trailPoints()));
        Wake f = new Wake();
        f.depth = 30;
        Wake.Source fe = f.add("Fähre", 0, 0, 90, 9, 200, 30, 6.5, true);
        for (int i = 0; i < 1200; i++) f.step(0.1);
        double[] b = fe.bow();
        double f300 = f.foamAt(b[0] - 300, b[1]), side = f.foamAt(b[0] - 300, b[1] + 80);
        for (int i = 0; i < 9000; i++) f.step(0.1);
        double gone = f.foamAt(b[0] - 300, b[1]);
        check(f300 > 0.3 && side < 0.01 && gone < 0.05, f("Schraubenwasser der Fähre: 300 m hinter dem Bug %.2f, 80 m daneben %.2f, 15 min später %.2f", f300, side, gone));
        // Kachel gleich Punkt (mit der ausgedünnten Spur)
        Wake j = new Wake();
        j.depth = 100;
        Wake.Source js = j.add("J/70", 0, 0, 60, 3, 6.2, 2.25, 0.35, false);
        for (int i = 0; i < 400; i++) j.step(0.1);
        int n = 32;
        float[] t = new float[2 * n * n];
        double[] jb = js.bow();
        double x0 = jb[0] - 60, z0 = jb[1] - 20, size = 64;
        j.tile(x0, z0, size, n, t);
        double[][] tr = j.uploadTrail(js, Wake.UPLOAD);
        double[] p = j.uploadParams(js), o = new double[3];
        double err = 0, cell = size / n, kMax = 2 * Math.PI / (3 * cell);
        int used = 0;
        for (int jj = 0; jj < n; jj++)
            for (int ii = 0; ii < n; ii++) {
                double px = x0 + (ii + 0.5) * cell, pz = z0 + (jj + 0.5) * cell;
                double ref = Wake.trailCoordsUpload(tr, px, pz, o) ? p[4] * Wake.shape(o[0], o[1], p[2], p[3], p[5], kMax) : 0;
                err = Math.max(err, Math.abs(ref - t[2 * (jj * n + ii)]));
                if (ref != 0) used++;
            }
        check(err < 1e-6 && used > 100 && tr.length <= Wake.UPLOAD, f("Kachel 32² gleich Punkt: größter Unterschied %.1e m (%d Zellen im Kielwasser, Spur %d Punkte)", err, used, tr.length));
        Wake a = new Wake(), c = new Wake();
        for (Wake q : new Wake[]{a, c}) { Wake.Source qs = q.add("x", 0, 0, 30, 2, 4); for (int i = 0; i < 200; i++) q.step(0.1); }
        check(a.heightAt(-10, 15, 0) == c.heightAt(-10, 15, 0), "gleiche Eingänge, gleiche Wellen");
    }

    /** Der Schwell der Fähre erreicht das Ufer: Ort und Zeit nach dem Keil. */
    static void schwell() {
        System.out.println("Schwell am Ufer");
        Wake w = new Wake();
        w.depth = 30;
        Wake.Source s = w.add("Fähre", 0, 0, 0, 9, 200, 30, 6.5, true);   // fährt nach Nord (−z)
        double[][] shore = new double[41][];
        for (int i = 0; i <= 40; i++) shore[i] = new double[]{3000, -10000 + i * 500};
        w.watch(shore);
        double[] got = {Double.NaN, 0, 0};
        w.onArrival = (src, x, z, h, t) -> { if (Double.isNaN(got[0])) { got[0] = t; got[1] = h; got[2] = z; } };
        for (int i = 0; i < 2400 * 10 && Double.isNaN(got[0]); i++) w.step(0.1);
        // geradeaus: der Rand des Keils erreicht einen Punkt im Abstand d quer, wenn das Schiff d/tan 19,47° weiter ist
        double d = 3000, along = d / Math.tan(Math.toRadians(Wake.KELVIN_DEG));
        double tEst = (along + (got[2] - 0) * 0 + 100) / 9;    // grob: der Strandpunkt neben dem Start
        double zHit = got[2], tGeo = (-zHit + along + 100) / 9;
        check(!Double.isNaN(got[0]) && Math.abs(got[0] / tGeo - 1) < 0.15 && got[1] > 0.01 && got[1] < 0.3,
                f("Fähre 18 kn, Ufer 3 km quer: der Schwell kommt nach %.0f s (Keil: %.0f s) mit %.0f mm an", got[0], tGeo, got[1] * 1000));
        check(tEst > 0, f("… das sind %.0f min, nachdem die Fähre am Strandpunkt vorbei war", (got[0] - (-zHit) / 9) / 60));
    }

    static String f(String s, Object... a) { return String.format(Locale.GERMAN, s, a); }

    static void check(boolean c, String what) {
        if (c) good++; else bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private WakeCheck() { }
}
