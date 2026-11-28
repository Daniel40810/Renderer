package com.dan.physics.auftrieb.demo;

import com.dan.core.pruef.Attest;
import com.dan.physics.auftrieb.Koerper;
import com.dan.physics.auftrieb.Vorgaben;
import com.dan.physics.auftrieb.Wasser;

import java.nio.file.Path;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung des Treibguts (Seegang Werkbuch, S7e2, Datei 101; SF151 A, Prüfstein 32). Balken, Fischkiste und Stamm, wie die
 * Szene sie baut ({@link Vorgaben#treibgut}, Proben {@link Vorgaben#TREIB_D} m), treiben 600 s in glattem Wasser mit
 * gleichmäßigem Strom und Wind. Gemessen wird die Drift über die letzten 200 s; erwartet ist das Gleichgewicht aus dem
 * Widerstand des Wassers auf die Flächen unter Wasser und dem Schub des Winds auf die Flächen darüber, beide senkrecht
 * projiziert in der Lage am Ende ({@link Vorgaben#flaechen}), mit den c_D des Körpers:
 * <pre>
 *   ½ ρ_W |u − v| (u − v) (c_längs A_W,vorn |ĥ·ê_W| + c_quer A_W,Seite |q̂·ê_W|)
 * + ½ ρ_L |w − v| (w − v) c_Wind (A_L,vorn |ĥ·ê_L| + A_L,Seite |q̂·ê_L|) = 0
 * </pre>
 * (u Strom, w Wind, v Drift, ĥ und q̂ waagrecht längs und quer zum Körper). Grenze ± 10 % [G] auf die Drift; der Anteil
 * des Winds (Abdrift durch den Wind über die Windgeschwindigkeit) als Auskunft.
 */
public final class TreibCheck {
    static final Attest A = new Attest("Treibgut (S7e2)", Koerper.VERSION);

    public static void main(String[] args) {
        System.out.println("TreibCheck – S7e2 (Seegang Werkbuch 101)");
        long t0 = System.nanoTime();
        try {
            A.teil("1 Flächen aus der Ruhelage");
            for (String art : new String[] {"balken", "fischkiste", "stamm"}) {
                Koerper k = Vorgaben.treibgut(art, Vorgaben.TREIB_D);
                A.info(k.form.name(), f("%d Proben; über Wasser von vorn %.3f m², von der Seite %.3f m²; Krängung in Ruhe %.0f°", k.proben.n, k.windVorn, k.windSeite,
                        Vorgaben.ruheKraengung(k.form.name())));
            }
            A.teil("2 Drift gegen das Gleichgewicht (Prüfstein 32)");
            double[][] faelle = {{0.5, 0, 0, 10}, {0, -0.4, 12, 0}};
            for (String art : new String[] {"balken", "fischkiste", "stamm"})
                for (double[] fa : faelle) drift(art, fa[0], fa[1], fa[2], fa[3]);
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer der Prüfung", f("%.1f s", (System.nanoTime() - t0) * 1e-9));
        Path pa = A.schreibe(Path.of("docs", "status", "S7e_Treibgut.md"), "S7e2 – Treibgut (Koerper " + Koerper.VERSION + ")");
        if (pa != null) System.out.println("\nAttest: " + pa);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static void drift(String art, double su, double sv, double wu, double wv) {
        Koerper k = Vorgaben.treibgut(art, Vorgaben.TREIB_D);
        Wasser wa = new Wasser() {
            @Override public double pegel(double x, double y, double t) { return 0; }
            @Override public void welle(double x, double y, double z, double t, double[] out) { java.util.Arrays.fill(out, 0); }
            @Override public void strom(double x, double y, double t, double[] out) { out[0] = su; out[1] = sv; }
            @Override public void wind(double x, double y, double t, double[] out) { out[0] = wu; out[1] = wv; }
        };
        k.einschwimmen(wa, 0, 0, 30, Vorgaben.ruheKraengung(k.form.name()), 0, 0);
        k.geschwindigkeit(su, sv, 0);
        double dt = 0.05, x0 = 0, y0 = 0;
        for (int i = 1; i <= 12000; i++) {
            k.schritt(wa, dt);
            if (i == 8000) { double[] o = k.lage(); x0 = o[0]; y0 = o[1]; }
        }
        double[] o = k.lage();
        double vx = (o[0] - x0) / 200, vy = (o[1] - y0) / 200;
        double[] fl = Vorgaben.flaechen(k, 0.01, 0);
        double[] e = gleichgewicht(k, fl, su, sv, wu, wv);
        double fehler = Math.hypot(vx - e[0], vy - e[1]) / Math.hypot(e[0], e[1]);
        double w = Math.hypot(wu, wv), am = Math.hypot(vx - su, vy - sv) / w, ae = Math.hypot(e[0] - su, e[1] - sv) / w;
        String fall = f("%s, Strom %.1f/%.1f m/s, Wind %.0f/%.0f m/s", k.form.name(), su, sv, wu, wv);
        A.pruefe(fehler <= 0.10, fall, f("Drift %.3f/%.3f m/s gegen %.3f/%.3f m/s (%.1f %%); Anteil des Winds %.2f %% gegen %.2f %%; Kurs %.0f°, Krängung %.0f°; unter Wasser vorn %.3f, Seite %.3f m²",
                vx, vy, e[0], e[1], 100 * fehler, 100 * am, 100 * ae, k.kurs(), k.kraengung(), fl[2], fl[3]), "± 10 % [G]");
    }

    /** Drift im Gleichgewicht (Newton in der Ebene, Jacobi-Matrix aus Differenzen). */
    static double[] gleichgewicht(Koerper k, double[] fl, double su, double sv, double wu, double wv) {
        double[] h = k.richtungWelt(1, 0, 0);
        double hl = Math.hypot(h[0], h[1]), hx = h[0] / hl, hy = h[1] / hl;
        double vx = su, vy = sv;
        for (int it = 0; it < 60; it++) {
            double[] f0 = kraft(k, fl, hx, hy, vx, vy, su, sv, wu, wv);
            double e = 1e-5;
            double[] fa = kraft(k, fl, hx, hy, vx + e, vy, su, sv, wu, wv), fb = kraft(k, fl, hx, hy, vx, vy + e, su, sv, wu, wv);
            double a = (fa[0] - f0[0]) / e, b = (fb[0] - f0[0]) / e, c = (fa[1] - f0[1]) / e, d = (fb[1] - f0[1]) / e, det = a * d - b * c;
            double dx = (d * f0[0] - b * f0[1]) / det, dy = (-c * f0[0] + a * f0[1]) / det;
            vx -= dx; vy -= dy;
            if (Math.hypot(dx, dy) < 1e-9) break;
        }
        return new double[] {vx, vy};
    }

    static double[] kraft(Koerper k, double[] fl, double hx, double hy, double vx, double vy, double su, double sv, double wu, double wv) {
        double rx = su - vx, ry = sv - vy, r = Math.hypot(rx, ry);
        double cw = r < 1e-12 ? 0 : k.cdLaengs * fl[2] * Math.abs(rx * hx + ry * hy) / r + k.cdQuer * fl[3] * Math.abs(-rx * hy + ry * hx) / r;
        double ax = wu - vx, ay = wv - vy, s = Math.hypot(ax, ay);
        double ca = s < 1e-12 ? 0 : k.cdWind * (fl[0] * Math.abs(ax * hx + ay * hy) / s + fl[1] * Math.abs(-ax * hy + ay * hx) / s);
        return new double[] {0.5 * 1025 * cw * r * rx + 0.5 * 1.25 * ca * s * ax, 0.5 * 1025 * cw * r * ry + 0.5 * 1.25 * ca * s * ay};
    }
}
