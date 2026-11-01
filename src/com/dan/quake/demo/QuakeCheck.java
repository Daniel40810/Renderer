package com.dan.quake.demo;

import com.dan.quake.Basin;
import com.dan.quake.Bssa14;
import com.dan.quake.Fault;
import com.dan.quake.Quake;
import com.dan.quake.Rumble;
import com.dan.quake.Shake;
import com.dan.quake.Slope;

import java.util.Locale;

/**
 * Prüfung von {@code com.dan.quake} ohne Fenster (seit LituyaBay1958 Phase 8, Werkbuch 5.1): Laufzeiten gegen
 * t_S − t_P = D (1/v_S − 1/v_P), WELLS94, BSSA14 gegen die Formel von Hand (Fels und weicher Boden), Newmark-Block
 * auf schiefer Ebene gegen die geschlossene Lösung für einen Rechteckimpuls, JIBSON93, Merian, die synthetische
 * Zeitreihe (Ruhe vor der P-Welle, Spitze = BSSA14, gleiche Saat gleiche Reihe), Zittern, Grollen und das Preset
 * {@code fairweather1958()}. Endet mit „Alles in Ordnung.“
 */
public final class QuakeCheck {
    private QuakeCheck() { }

    static int fails;

    static void ok(boolean c, String what) {
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
        if (!c) fails++;
    }

    static String f(String s, Object... a) { return String.format(Locale.GERMAN, s, a); }

    public static void main(String[] a) {
        System.out.println("Erdbeben (com.dan.quake) – Prüfung");
        Quake q = Quake.magnitude(7.0, 20);
        double d = q.fault.hypoDistance(30_000, 40_000);
        double dtps = q.tS(30_000, 40_000) - q.tP(30_000, 40_000);
        ok(Math.abs(dtps - d * (1 / q.vs - 1 / q.vp)) < 1e-9 && Math.abs(dtps - d / 8400) / dtps < 0.003,
                f("Laufzeiten: D = %.1f km, tS − tP = %.3f s = D (1/vS − 1/vP), etwa D / 8,4 km/s = %.3f s", d / 1000, dtps, d / 8400));
        double l = Fault.wellsLength(7.8), dm = Fault.wellsDisplacement(7.8);
        ok(Math.abs(l - 166_700) < 300 && Math.abs(dm - 10.1) < 0.1, f("WELLS94 für M 7,8: Länge %.1f km (Werkbuch: ≈ 170), größter Versatz %.1f m (≈ 10)", l / 1000, dm));

        // BSSA14 von Hand: PGA, Blattverschiebung, M 7,8, Rjb 10 km
        double fe = 0.4856 + (-0.1662) * (7.8 - 5.5), r = Math.sqrt(10 * 10 + 4.5 * 4.5);
        double fp = (-1.134 + 0.1917 * (7.8 - 4.5)) * Math.log(r) + (-0.008088) * (r - 1);
        double hand760 = Math.exp(fe + fp);
        double flin = -0.6 * Math.log(400 / 760.0), f2 = -0.15 * (Math.exp(-0.00701 * (400 - 360)) - Math.exp(-0.00701 * 400));
        double hand400 = Math.exp(fe + fp + flin + f2 * Math.log((hand760 + 0.1) / 0.1));
        double p760 = Bssa14.median(Bssa14.Imt.PGA, 7.8, 10, 760, Bssa14.Mechanism.STRIKE_SLIP), p400 = Bssa14.median(Bssa14.Imt.PGA, 7.8, 10, 400, Bssa14.Mechanism.STRIKE_SLIP);
        ok(Math.abs(p760 - hand760) < 1e-9 && Math.abs(p400 - hand400) < 1e-9 && Math.abs(hand760 - 0.3077) < 0.001,
                f("BSSA14 PGA, M 7,8 Blattverschiebung, Rjb 10 km: Fels (760 m/s) %.4f g, Boden (400 m/s) %.4f g = von Hand", p760, p400));
        double near = Bssa14.median(Bssa14.Imt.PGA, 7.8, 0, 760, Bssa14.Mechanism.STRIKE_SLIP), far = Bssa14.median(Bssa14.Imt.PGA, 7.8, 100, 760, Bssa14.Mechanism.STRIKE_SLIP);
        double pgv = Bssa14.median(Bssa14.Imt.PGV, 7.8, 10, 760, Bssa14.Mechanism.STRIKE_SLIP), sig = Bssa14.sigma(Bssa14.Imt.PGA, 7.8, 10, 760);
        ok(near > p760 && p760 > far && pgv > 20 && pgv < 60 && Math.abs(sig - Math.hypot(0.348, 0.495)) < 1e-9,
                f("BSSA14 fällt mit dem Abstand: 0 km %.3f g, 10 km %.3f g, 100 km %.3f g; PGV %.1f cm/s; σ %.3f = √(τ² + φ²)", near, p760, far, pgv, sig));

        // Newmark: Rechteckimpuls hangaufwärts (Trägheit hangabwärts) A = 3 m/s², T = 0,5 s
        Slope s = new Slope();
        s.alpha = 30; s.phi = 30; s.cohesion = s.cohesionFor(1.1); s.downslopeAzimuth = 90;   // hangabwärts nach Osten
        double ac = s.ac(), A = 3, T = 0.5, dt = 0.0005;
        int n = (int) (6 / dt);
        float[] e = new float[n], zero = new float[n];
        for (int i = 0; i < (int) Math.round(T / dt); i++) e[i] = (float) -A;   // Boden nach Westen = hangaufwärts
        Slope.Result res = s.newmark(Quake.Record.of(0, 0, 0, dt, e, zero, zero));
        double soll = Slope.rectangularPulse(A, ac, T);
        ok(Math.abs(res.total() - soll) / soll < 0.01, f("Newmark-Block, Rechteckimpuls 3 m/s² über 0,5 s, a_c = %.3f m/s²: %.4f m, geschlossen %.4f m", ac, res.total(), soll));
        ok(Math.abs(s.fs() - 1.1) < 1e-9 && Math.abs(ac - 0.1 * 9.81 * Math.sin(Math.toRadians(30))) < 1e-9 && s.fs(0.1) < s.fs(0.05) && s.fs(0.05) < s.fs(),
                f("Sicherheitsfaktor: FS %.3f, a_c = (FS − 1) g sin α, pseudo-statisch fällt er (k = 0,05: %.3f, 0,1: %.3f)", s.fs(), s.fs(0.05), s.fs(0.1)));
        double j = Slope.jibson93(1, 0.1);
        ok(Math.abs(j - Math.pow(10, 1.993 - 1.546) / 100) < 1e-12, f("JIBSON93: I_A = 1 m/s, a_c = 0,1 g → %.2f cm", j * 100));

        Basin b = new Basin();
        ok(Math.abs(b.period(1) - 2 * 12_000 / Math.sqrt(9.81 * 130)) < 1e-9 && Math.abs(b.period(1) / 60 - 11.2) < 0.1,
                f("Merian: Lituya Bay (12 km, 130 m) T1 = %.0f s = %.1f min (Werkbuch: ≈ 11 min); offen %.1f min", b.period(1), b.period(1) / 60, open(b) / 60));

        // synthetische Zeitreihe
        Quake w = Quake.magnitude(7.0, 20);
        Quake.Record rec = w.record(0, 20_000);
        double tp = w.tP(0, 20_000), ts = w.tS(0, 20_000);
        double before = 0, ps = 0, after = 0;
        for (int i = 0; i < rec.length(); i++) {
            double t = rec.t0 + i * rec.dt, aa = Math.abs(rec.east[i]) + Math.abs(rec.south[i]) + Math.abs(rec.up[i]);
            if (t < tp - 0.5) before = Math.max(before, aa);
            else if (t < ts - 0.5) ps = Math.max(ps, aa);
            else after = Math.max(after, aa);
        }
        ok(before < 1e-3 * after && ps < after && Math.abs(rec.peakHorizontal() / 9.81 - w.pga(0, 20_000)) < 1e-4,
                f("Zeitreihe M 7,0 bei 20 km: still vor der P-Welle (%.1f s), P schwächer als S (%.2f gegen %.2f m/s²), Spitze %.3f g = BSSA14", tp, ps, after, rec.peakHorizontal() / 9.81));
        Quake w2 = Quake.magnitude(7.0, 20), w3 = Quake.magnitude(7.0, 20);
        w3.seed = 99;
        Quake.Record r2 = w2.record(0, 20_000), r3 = w3.record(0, 20_000);
        boolean gleich = r2.length() == rec.length(), anders = false;
        for (int i = 0; i < rec.length() && gleich; i++) gleich = r2.east[i] == rec.east[i];
        for (int i = 0; i < Math.min(rec.length(), r3.length()); i++) anders |= r3.east[i] != rec.east[i];
        double[] sd = rec.significantDuration();
        ok(gleich && anders && rec.arias() > 0 && sd[0] > 3 && sd[1] >= tp, f("gleiche Saat, gleiche Reihe; andere Saat, andere; Arias %.3f m/s, D5–95 %.1f s ab %.1f s", rec.arias(), sd[0], sd[1]));

        Shake sh = new Shake(rec);
        double[] s0 = sh.at(tp - 1), s1 = sh.at(ts + 3);
        boolean still = Math.abs(s0[0]) + Math.abs(s0[1]) + Math.abs(s0[2]) < 1e-6, bewegt = Math.abs(s1[0]) + Math.abs(s1[1]) + Math.abs(s1[2]) > 1e-4;
        double mx = 0;
        for (double t = rec.t0; t < rec.tEnd(); t += 0.05) { double[] v = sh.at(t); mx = Math.max(mx, Math.max(Math.abs(v[0]), Math.abs(v[2]))); }
        ok(still && bewegt && mx < 2, f("Zittern: still vor der P-Welle, bewegt nach der S-Welle, größte Verschiebung %.2f m", mx));
        float[] pcm = new Rumble().pcm(rec, rec.t0, 20, 8000);
        boolean fin = pcm.length == 160_000;
        double pk = 0;
        for (float v : pcm) { fin &= Float.isFinite(v); pk = Math.max(pk, Math.abs(v)); }
        ok(fin && pk <= 1 && pk > 0.1, f("Grollen: 20 s zu 8 kHz, endlich, Spitze %.2f", pk));

        // Preset
        Quake fw = Quake.fairweather1958();
        double gx = 354_903.5 - 352_000, gz = -(6_506_385.5 - 6_503_000);
        Quake.Record g = fw.record(gx, gz);
        double rjb = fw.rjbKm(gx, gz), dt2 = fw.tS(gx, gz) - fw.tP(gx, gz);
        ok(rjb < 2 && fw.pga(gx, gz) > 0.4 && fw.pga(gx, gz) < 0.7 && Math.abs(dt2 - 8.1) < 0.3 && g.significantDuration()[0] > 10,
                f("fairweather1958() am Gilbert Inlet: Rjb %.1f km, PGA %.2f g, tP %.1f s, tS %.1f s, Arias %.2f m/s, D5–95 %.0f s", rjb, fw.pga(gx, gz), fw.tP(gx, gz), fw.tS(gx, gz), g.arias(),
                        g.significantDuration()[0]));
        System.out.println(fails == 0 ? "Alles in Ordnung." : fails + " Fehler.");
        if (fails > 0) System.exit(1);
    }

    static double open(Basin b) { b.open = true; double t = b.period(1); b.open = false; return t; }
}
