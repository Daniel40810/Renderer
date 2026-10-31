package com.dan.wind.demo;

import com.dan.wind.WindField;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen
 * (Rückgabewert 1). Seit Brocken Phase 1: Profil, Schichtung, Turbulenz, Böen, Gitter. Seit Phase 4 der Kern: Masse,
 * Beschleunigung über einer Kuppe gegen die Faustformel (Walmsley, Taylor und Salmon 1989: ΔS = 1,6 H/L · e^(−4 z/L)),
 * Umströmen bei stabiler Luft, Lee hinter einer Kante, Kaltluft und Hangwind, gleiche Eingänge gleiches Feld.
 */
public final class WindCheck {
    private static int bad;

    public static void main(String[] args) {
        WindField a = WindField.westlage(), b = WindField.westlage();
        a.seed = b.seed = 42;
        check(a.gustAt(120, 30, -40, 5) == b.gustAt(120, 30, -40, 5), "gleicher seed, gleiche Böe");
        b.seed = 43;
        check(a.gustAt(120, 30, -40, 5) != b.gustAt(120, 30, -40, 5), "anderer seed, andere Böe");
        check(WindField.calm().speed < WindField.breeze().speed && WindField.breeze().speed < WindField.westlage().speed
                && WindField.westlage().speed < WindField.sturm().speed, "Presets steigen von calm bis sturm");
        check(WindField.beaufort(-3).speed == WindField.beaufort(0).speed && WindField.beaufort(99).speed == WindField.beaufort(12).speed,
                "beaufort(n) begrenzt auf 0..12");
        for (int n = 0; n <= 12; n++) if (WindField.beaufortOf(WindField.beaufort(n).speed) != n) { check(false, "beaufortOf(beaufort(" + n + "))"); break; }
        check(true, "beaufortOf ist die Umkehrung von beaufort");
        // Profil: in refHeight genau speed, darunter weniger, darüber mehr, am Boden 0
        WindField w = WindField.westlage();
        check(Math.abs(w.profile(w.refHeight, w.z0) - w.speed) < 1e-9, "Profil trifft speed in 10 m");
        check(w.profile(2, w.z0) < w.speed && w.profile(80, w.z0) > w.speed && w.profile(0, w.z0) < 1e-9, "Profil steigt mit der Höhe, am Grund 0");
        check(w.profile(5000, w.z0) == w.profile(w.top, w.z0), "über der Grenzschicht bleibt der Wind gleich");
        // logarithmisch: u(100)/u(10) bei z0 = 0,3 m neutral
        double ln = Math.log(100.3 / 0.3) / Math.log(10.3 / 0.3);
        check(Math.abs(w.profile(100, 0.3) / w.speed - ln) < 1e-9, "neutral: logarithmisches Profil");
        WindField st = WindField.westlage(), la = WindField.westlage();
        st.stability = WindField.Stability.STABIL;
        la.stability = WindField.Stability.LABIL;
        check(st.profile(200, 0.3) > w.profile(200, 0.3) && la.profile(200, 0.3) < w.profile(200, 0.3), "stabil steiler, labil flacher als neutral");
        // Richtung: aus West weht es nach Osten (+x)
        WindField west = WindField.beaufort(5);
        west.fromDeg = 270;
        double[] v = west.windAt(0, 50, 0, 0);
        check(v[0] > 0 && Math.abs(v[2]) < 1e-9, "Westwind weht nach Osten");
        west.fromDeg = 0;
        v = west.windAt(0, 50, 0, 0);
        check(v[2] > 0 && Math.abs(v[0]) < 1e-9, "Nordwind weht nach Süden (+z)");
        // Hang: Luft steigt den Luvhang hinauf
        WindField hang = WindField.westlage();
        hang.fromDeg = 270;
        hang.ground = (x, z) -> 500 * Math.exp(-(x * x + z * z) / (1500.0 * 1500));
        double[] luv = hang.windAt(-1200, hang.ground.height(-1200, 0) + 20, 0, 0), lee = hang.windAt(1200, hang.ground.height(1200, 0) + 20, 0, 0);
        check(luv[1] > 0 && lee[1] < 0, "am Luvhang steigt, im Lee sinkt die Luft");
        check(Math.abs(hang.windAt(0, 400, 0, 0)[0]) < 1e-12, "unter dem Grund kein Wind");
        // Turbulenz: rauer und tiefer mehr
        check(w.turbulenceAt(0, 5, 0) > w.turbulenceAt(0, 200, 0), "Turbulenz nimmt mit der Höhe ab");
        WindField wald = WindField.westlage(), moor = WindField.westlage();
        wald.z0 = 1.0;
        moor.z0 = 0.03;
        check(wald.turbulenceAt(0, 20, 0) > moor.turbulenceAt(0, 20, 0), "über Wald mehr Turbulenz als über Moor");
        // Böen: im Mittel wie der Wind, Spitzen darüber
        double sum = 0, max = 0, u = w.profile(30, w.z0);
        int n = 20_000;
        for (int i = 0; i < n; i++) { double g = w.gustAt(i * 7.3, 30, i * -3.1, i * 0.05); sum += g; max = Math.max(max, g); }
        check(Math.abs(sum / n / u - 1) < 0.08, String.format(java.util.Locale.GERMAN, "Böen im Mittel wie der Wind (%.2f)", sum / n / u));
        check(max > u * 1.3, "Böen mit Spitzen über dem Mittel");
        // Gitter (F7 A): 50 m über 12,7 × 11,9 km, 30 Schichten bis 1 500 m
        WindField.Grid g = w.grid(-6700, -5500, 6000, 6400);
        check(g.nx() == 254 && g.nz() == 238 && g.layers() == 30, "Gitter 254 × 238 × 30 über dem Hauptraster");
        double topOf = g.first() * (Math.pow(g.stretch(), g.layers()) - 1) / (g.stretch() - 1);
        check(Math.abs(topOf - 1500) < 0.5, String.format(java.util.Locale.GERMAN, "Schichten reichen bis 1 500 m (Streckung %.4f)", g.stretch()));
        check(Math.abs(g.layerOf(g.layerHeight(7)) - 7.5) < 1e-6, "layerOf ist die Umkehrung von layerHeight");
        WindField.Grid kl = WindField.breeze().grid(0, 0, 500, 300);
        float[] s = WindField.breeze().sample(kl, 0);
        boolean finite = s.length == kl.cells() * 4;
        for (float f : s) if (Float.isNaN(f) || Float.isInfinite(f)) finite = false;
        check(finite, "sample liefert vier endliche Werte je Zelle");
        kern();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Seit Brocken Phase 4: der massenkonsistente Kern. */
    static void kern() {
        System.out.println("Kern (seit Brocken Phase 4)");
        java.util.Locale de = java.util.Locale.GERMAN;
        double H = 200, L = 1000, s = L / 1.1774;
        WindField f = WindField.beaufort(5);
        f.fromDeg = 270;
        f.z0 = 0.03;
        f.quality = WindField.Quality.LOW;
        f.ground = (x, z) -> H * Math.exp(-(x * x + z * z) / (2 * s * s));
        com.dan.wind.WindSolution so = f.solve(-6000, -5000, 6000, 5000);
        check(so.massMax < 0.01 && so.residual < 1e-4, String.format(de, "Masse: in keiner Zelle mehr als 1 %% Fehler (höchstens %.4f %%, im Mittel %.5f %%, %d Schritte, %.1f s)",
                so.massMax * 100, so.massMean * 100, so.iterations, so.millis / 1000));
        StringBuilder b = new StringBuilder();
        boolean faust = true;
        for (double h : new double[]{10, 30}) {
            double ds = so.speedAt(0, h, 0) / so.speedAt(-5000, h, 0) - 1, tl = 1.6 * H / L * Math.exp(-4 * h / L);
            faust &= Math.abs(ds / tl - 1) < 0.35;
            b.append(String.format(de, " %.0f m: %.2f (Faustformel %.2f)", h, ds, tl));
        }
        check(faust, "Kuppe H/L = 0,2, neutral, Beschleunigung am Gipfel auf ± 35 % der Faustformel:" + b);
        double hoch = so.speedAt(0, 250, 0) / so.speedAt(-5000, 250, 0) - 1, tief = so.speedAt(0, 10, 0) / so.speedAt(-5000, 10, 0) - 1;
        check(hoch < tief && hoch > 0, String.format(de, "die Beschleunigung nimmt mit der Höhe ab (250 m: %.2f)", hoch));
        double[] luv = so.at(-900, f.ground.height(-900, 0) + 30, 0), lee = so.at(900, f.ground.height(900, 0) + 30, 0);
        check(luv[1] > 0.3 && lee[1] < -0.3, String.format(de, "am Luvhang steigt die Luft (%.2f m/s), im Lee sinkt sie (%.2f m/s)", luv[1], lee[1]));
        WindField st = f.copy();
        st.stability = WindField.Stability.INVERSION;
        com.dan.wind.WindSolution ss = st.solve(-6000, -5000, 6000, 5000);
        double sideN = so.speedAt(0, 10, 2 * L) / so.speedAt(-5000, 10, 0), sideS = ss.speedAt(0, 10, 2 * L) / ss.speedAt(-5000, 10, 0);
        check(sideS > sideN + 0.05, String.format(de, "bei Inversion geht mehr Luft um die Kuppe herum: seitlich %.2f gegen neutral %.2f der Lage", sideS, sideN));
        // Lee hinter einer Kante (Stufe 150 m, steil)
        WindField k = WindField.beaufort(6);
        k.fromDeg = 270;
        k.quality = WindField.Quality.LOW;
        k.ground = (x, z) -> 150 / (1 + Math.exp((x - 0) / 40.0));   // hoch im Westen, Abbruch bei x = 0
        com.dan.wind.WindSolution sk = k.solve(-4000, -2000, 4000, 2000);
        double hinter = sk.speedAt(200, 10, 0), weit = sk.speedAt(3000, 10, 0);
        check(sk.shelterAt(200, 0) > 0.3 && hinter < 0.8 * weit, String.format(de, "Lee: hinter einer Kante (Winkel %.0f°) %.1f m/s, weiter weg %.1f m/s", Math.toDegrees(sk.shelterAt(200, 0)), hinter, weit));
        // Kaltluft und Hangwind
        WindField v = WindField.beaufort(1);
        v.stability = WindField.Stability.STABIL;
        v.heating = -1;
        v.quality = WindField.Quality.LOW;
        v.ground = (x, z) -> 300 - 150 * Math.exp(-(x * x) / (2 * 800.0 * 800));
        com.dan.wind.WindSolution sv = v.solve(-5000, -3000, 5000, 3000);
        double nacht = sv.at(900, v.ground.height(900, 0) + 20, 0)[0];
        check(sv.coldAt(0, 0) > 10 && sv.coldAt(4500, 0) < 1 && v.coldPoolAt(0, 0, 0) == sv.coldAt(0, 0),
                String.format(de, "Kaltluft nachts: im Tal %.0f m tief, auf der Höhe 0", sv.coldAt(0, 0)));
        v.heating = 1;
        v.stability = WindField.Stability.LABIL;
        double tag = v.solve(-5000, -3000, 5000, 3000).at(900, v.ground.height(900, 0) + 20, 0)[0];
        check(nacht < 0 && tag > 1, String.format(de, "Hangwind am Osthang des Tals in 20 m: nachts %.2f m/s (hinab), am Tag %.2f m/s (hinauf)", nacht, tag));
        // gleiche Eingänge, gleiches Feld; ohne Lösung die Näherung
        com.dan.wind.WindSolution s2 = f.copy().solve(-6000, -5000, 6000, 5000);
        double[] a1 = so.at(321, 150, -77), a2 = s2.at(321, 150, -77);
        check(a1[0] == a2[0] && a1[1] == a2[1] && a1[2] == a2[2], "gleiche Eingänge, gleiches Feld");
        double[] aus = f.windAt(9000, 40, 0, 0), hull = f.hullAt(9000, 40, 0);
        check(aus[0] == hull[0] && f.describe().contains("gelöst"), "außerhalb des Gitters die Näherung der Hülle; describe nennt das gelöste Feld");
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private WindCheck() { }
}
