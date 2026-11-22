package com.dan.physics.hydro.demo;

import static com.dan.physics.hydro.demo.HydroCheck.A;
import static com.dan.physics.hydro.demo.HydroCheck.R;
import static com.dan.core.pruef.Attest.f;

import com.dan.core.gitter.Gitter;
import com.dan.core.raum.Utm;
import com.dan.physics.hydro.Einseitig;
import com.dan.physics.hydro.Hydro;
import java.util.Arrays;

/**
 * Die Fälle 15–18 von {@link HydroCheck} (Werkbuch HalligHooge Teil A, 12.6, Plan P5a): hydro auf der Kugel, die
 * Gleichgewichtstide, die einseitige Schachtel und der Speicher im Zeitschritt. Jeder Fall rechnet über
 * {@link HydroCheck#R}, also auf dem Prozessor oder mit {@code --karte} auf der Karte.
 */
final class HydroP5 {
    private HydroP5() { }

    static final double M2 = 2 * Math.PI / 44714.0;

    // ------------------------------------------------------------------ Fall 15

    /**
     * Ruhender See auf einem geographischen Gitter wie G0 (1/12° × 1/18°, 40 × 30 Zellen ab 6° O, 54° N) mit Subgrid
     * aus Pixeln zu 30″ × 20″: Becken mit Hügeln, Inseln und einer Rinne schmaler als eine Zelle. Dazu das Volumen des
     * Subgrid gegen die Summe über die Pixel mit der Fläche je Zeile.
     */
    static void fall15() {
        Gitter g = Gitter.geografisch("Kugel", 6, 54, 1 / 12.0, 1 / 18.0, 40, 30);
        int fein = 10;
        java.util.function.DoubleBinaryOperator z = (lon, lat) -> {
            double x = (lon - 6) / (40 / 12.0), y = (lat - 54) / (30 / 18.0);
            double h = -25 + 60 * ((x - 0.5) * (x - 0.5) + (y - 0.5) * (y - 0.5)) + 6 * Math.sin(9 * x) * Math.cos(7 * y);
            if (Math.abs(y - 0.3 - 0.2 * x) < 0.004) h = Math.min(h, -18);
            return h;
        };
        Hydro h = HydroCheck.bauen(g, fein, z, 0.025);
        // Volumen bei η = +30 m (alles nass) gegen die Summe über die Pixel
        int w = g.nx * fein;
        double soll = 0, flaeche = 0;
        for (int j = 0; j < g.ny * fein; j++) {
            double pa = g.flaeche(j / fein) / (fein * fein);
            for (int i = 0; i < w; i++) {
                double lon = g.x0 + (i + 0.5) * g.dx / fein, lat = g.y0 + (j + 0.5) * g.dy / fein;
                soll += pa * (30 - z.applyAsDouble(lon, lat));
                flaeche += pa;
            }
        }
        double ist = 0;
        for (int c = 0; c < h.zellen; c++) ist += h.volumen(c, 30);
        double fehler = Math.abs(ist - soll) / flaeche;
        A.pruefe(fehler <= 1e-3, f("Prüfung A, Fall 15: Volumen des Subgrid auf der Kugel (Fläche je Zeile, %.1f … %.1f km²) bei η = +30 m gegen die Summe über die Pixel",
                g.flaeche(0) / 1e6, g.flaeche(g.ny - 1) / 1e6), f("%.2e m mittlere Tiefe", fehler), "≤ 1 mm (wie Prüfstein 8 in 12.4)");
        for (double eta : new double[] {+1, -5}) {
            h.fuellen(eta);
            h.zeit = 0;
            R.start(h);
            double dt = R.dtCfl(h), v0 = h.volumen();
            int nass = 0;
            for (int c = 0; c < h.zellen; c++) if (h.vol[c] > 0) nass++;
            double um = 0;
            for (int n = 0; n < 1000; n++) {
                R.schritt(h, dt);
                for (double x : h.u) um = Math.max(um, Math.abs(x));
            }
            double dv = Math.abs(h.volumen() - v0) / v0;
            A.pruefe(um < HydroCheck.ruhGrenze() && dv < 1e-12, f("Prüfung A, Fall 15: ruhender See auf der Kugel bei η = %+.0f m, 1000 Schritte zu %.1f s; %d von %d Zellen nass", eta, dt, nass, h.zellen),
                    f("|u| bis %.1e m/s, Volumen %.1e", um, dv), R.karte() ? "< 1·10⁻⁶ m/s" : "< 1·10⁻¹⁰ m/s");
        }
    }

    // ------------------------------------------------------------------ Fall 16

    /**
     * Gleichgewichtstide in einem tiefen geschlossenen Becken (300 × 30 km, 4 000 m, ohne Reibung und Coriolis;
     * Eigenperiode 2L/√(gh) = 50 min): η_eq = A r(t) cos(ω_M2 t) (x/L − ½), A = 0,1 m, Anlauf 3 h. Die Antwort ist fast
     * statisch (Faktor 1/(1 − (T₀/T)²) = 1,005): η folgt η_eq. Verglichen wird die Amplitude von η_Ost − η_West mit der
     * von η_eq über die zweite Periode.
     */
    static void fall16() {
        int nx = 100, ny = 10;
        double dx = 3000, L = nx * dx, amp = 0.1, anlauf = 3 * 3600, T = 2 * Math.PI / M2;
        Gitter g = Gitter.utm("Becken", 32, 400000, 6000000, dx, nx, ny);
        Hydro h = HydroCheck.bauen(g, 2, (x, y) -> -4000, HydroCheck.OHNE_REIBUNG);
        Arrays.fill(h.f, 0);
        h.smagorinsky = 0;
        h.fuellen(0);
        h.gleich = new double[h.zellen];
        R.start(h);
        double dt = R.dtCfl(h);
        double[] ts = new double[20000], d = new double[20000], q = new double[20000];
        int m = 0;
        while (h.zeit < 2 * T - 1e-9) {
            double t1 = h.zeit + Math.min(dt, 2 * T - h.zeit);
            double r = t1 >= anlauf ? 1 : 0.5 * (1 - Math.cos(Math.PI * t1 / anlauf));
            for (int c = 0; c < h.zellen; c++) h.gleich[c] = amp * r * Math.cos(M2 * t1) * ((c % nx + 0.5) * dx / L - 0.5);
            R.schritt(h, t1 - h.zeit);
            if (h.zeit >= T) {
                double ow = 0, oo = 0, gw = 0, go = 0;
                for (int j = 0; j < ny; j++) {
                    ow += h.eta[nx * j];
                    oo += h.eta[nx - 1 + nx * j];
                    gw += h.gleich[nx * j];
                    go += h.gleich[nx - 1 + nx * j];
                }
                ts[m] = h.zeit;
                d[m] = (oo - ow) / ny;
                q[m] = (go - gw) / ny;
                m++;
            }
        }
        double ai = HydroCheck.harmonisch(ts, d, m, M2)[0], as = HydroCheck.harmonisch(ts, q, m, M2)[0], dyn = 1 / (1 - Math.pow(2 * L / Math.sqrt(9.81 * 4000) / T, 2));
        A.pruefe(Math.abs(ai / as - 1) <= 0.02, "Prüfung A, Fall 16: Gleichgewichtstide im tiefen geschlossenen Becken (300 × 30 km, 4 000 m), η_Ost − η_West gegen η_eq über die zweite Periode",
                f("Verhältnis %.4f (%+.2f %%; dynamisch erwartet %.4f), Amplitude %.4f m gegen %.4f m", ai / as, 100 * (ai / as - 1), dyn, ai, as), "±2 % [G]");
    }

    // ------------------------------------------------------------------ Fall 17

    /**
     * Einseitige Schachtel über zwei Lagen: grob geographisch (1/12° × 1/18°, 30 × 12 Zellen ab 6° O, 54° N, 30 m tief,
     * ohne Reibung, mit Coriolis), am Westrand eine Welle mit T = 3 h und 0,3 m, sonst Wände: im Becken eine stehende
     * Welle. Fein in UTM, 1 km, 40 × 30 Zellen in der Mitte, Rand einseitig aus dem groben ({@link Einseitig}),
     * Kopplung alle 300 s. Verglichen wird η im feinen mit η des groben am selben Ort über die letzten zwei Perioden.
     */
    static void fall17() {
        double T = 3 * 3600, om = 2 * Math.PI / T, amp = 0.3, ende = 6 * T, takt = 300;
        Gitter gg = Gitter.geografisch("Grob", 6, 54, 1 / 12.0, 1 / 18.0, 30, 12);
        Hydro grob = HydroCheck.bauen(gg, 2, (x, y) -> -30, HydroCheck.OHNE_REIBUNG);
        double[] sw = Utm.vorwaerts(6.9, 54.12, 32);
        Gitter gf = Gitter.utm("Fein", 32, Math.rint(sw[0] / 1000) * 1000, Math.rint(sw[1] / 1000) * 1000, 1000, 40, 30);
        Hydro fein = HydroCheck.bauen(gf, 2, (x, y) -> -30, HydroCheck.OHNE_REIBUNG);
        grob.fuellen(0);
        fein.fuellen(0);
        grob.rand[Hydro.WEST] = Hydro.Rand.WASSERSTAND;
        grob.randwert[Hydro.WEST] = (t, x, y) -> amp * Math.sin(om * t) * (t >= T ? 1 : 0.5 * (1 - Math.cos(Math.PI * t / T)));
        Einseitig e = new Einseitig(grob, fein);
        e.stand(0, Einseitig.Stand.von(grob));
        R.start(grob);
        R.start(fein);
        int n = gf.nx * gf.ny, m = 0;
        int mitte = gf.nx / 2 + gf.nx * (gf.ny / 2);
        double[] ts = new double[20000], ef = new double[20000], eg = new double[20000];
        double[] sq = new double[n], sg = new double[n];
        // Ort jeder feinen Zelle im groben (bilinear)
        int[][] zz = new int[n][4];
        double[][] ww = new double[n][4];
        for (int c = 0; c < n; c++) {
            double[] ll = gf.grad(gf.x(c % gf.nx), gf.y(c / gf.nx));
            double fi = (ll[0] - gg.x0) / gg.dx - 0.5, fj = (ll[1] - gg.y0) / gg.dy - 0.5;
            int i0 = (int) Math.floor(fi), j0 = (int) Math.floor(fj);
            double a = fi - i0, b = fj - j0;
            zz[c] = new int[] {i0 + gg.nx * j0, i0 + 1 + gg.nx * j0, i0 + gg.nx * (j0 + 1), i0 + 1 + gg.nx * (j0 + 1)};
            ww[c] = new double[] {(1 - a) * (1 - b), a * (1 - b), (1 - a) * b, a * b};
        }
        double t = 0, amax = 0;
        while (t < ende - 1e-9) {
            double t1 = t + takt;
            gleichmaessig(grob, t1);
            e.stand(t1, Einseitig.Stand.von(grob));
            gleichmaessig(fein, t1);
            t = t1;
            if (t > ende - 2 * T) {
                for (int c = 0; c < n; c++) {
                    double g2 = 0;
                    for (int k = 0; k < 4; k++) g2 += ww[c][k] * grob.eta[zz[c][k]];
                    double dd = fein.eta[c] - g2;
                    sq[c] += dd * dd;
                    sg[c] += g2 * g2;
                    if (c == mitte) {
                        ts[m] = t;
                        ef[m] = fein.eta[c];
                        eg[m] = g2;
                        m++;
                    }
                }
            }
        }
        double af = HydroCheck.harmonisch(ts, ef, m, om)[0], ag = HydroCheck.harmonisch(ts, eg, m, om)[0];
        double schlimmst = 0, mittel = 0, bezug = 0;
        for (int c = 0; c < n; c++) bezug += sg[c] / n;
        bezug = Math.sqrt(bezug);
        int wo = 0;
        for (int c = 0; c < n; c++) {
            double r = Math.sqrt(sq[c]) / bezug;
            mittel += r / n;
            if (r > schlimmst) { schlimmst = r; wo = c; }
        }
        A.pruefe(Math.abs(af / ag - 1) <= 0.02, f("Prüfung A, Fall 17: einseitige Schachtel, grob geographisch (5,5 × 6,2 km), fein UTM (1 km, %s), stehende Welle T = 3 h; Amplitude in der Mitte des feinen gegen das grobe am selben Ort (Periode 5–6)", gf),
                f("%.4f m gegen %.4f m (%+.2f %%)", af, ag, 100 * (af / ag - 1)), "±2 % [G]");
        A.pruefe(schlimmst <= 0.05, "Prüfung A, Fall 17: Abweichung fein gegen grob je Zelle des feinen, RMS über die Perioden 5–6 bezogen auf das RMS des groben über das feine Gitter",
                f("höchstens %.2f %% (Zelle %d, %d), im Mittel %.2f %%; %d Randpunkte außerhalb des groben", 100 * schlimmst, wo % gf.nx, wo / gf.nx, 100 * mittel, e.ausserhalb), "≤ 5 % [G]");
    }

    /**
     * Rechnet bis t1 in gleichen Schritten (so viele, wie die CFL-Grenze am Anfang verlangt). Ein kurzer Rest am Ende
     * jedes Intervalls ließe den Schritt springen; das verträgt das Verfahren am Rand seiner Stabilität nicht (12.6, Stand P5a).
     */
    static void gleichmaessig(Hydro h, double t1) {
        int n = (int) Math.ceil((t1 - h.zeit) / R.dtCfl(h) - 1e-9);
        double dt = (t1 - h.zeit) / Math.max(1, n);
        for (int k = 0; k < n; k++) R.schritt(h, k == n - 1 ? t1 - h.zeit : dt);
    }

    // ------------------------------------------------------------------ Fall 18

    /**
     * Speicher im Zeitschritt (Werkbuch 12.6, Befund 9): Becken 30 × 10 Zellen zu 200 m, Pixel 10 m. Westlich offenes
     * Wasser (−9 m), östlich geschlossenes Land (+10 m); in der Zelle (15, 5) eine Rinne aus 8 Pixeln (−9,5 m) längs
     * ihres Westrands, die einzige Verbindung ins Land. Am Westrand steigt das Wasser in 1 h um 2 m, dazu 0,1 m mit 10 min.
     * Die Rinne darf nicht überschwingen: η dort höchstens 0,5 m [G] über dem höchsten Wasserstand am Rand.
     */
    static void fall18() {
        int nx = 30, ny = 10, fein = 20;
        double d = 200, px = d / fein;
        Gitter g = Gitter.utm("Rinne", 32, 400000, 6000000, d, nx, ny);
        Hydro h = HydroCheck.bauen(g, fein, (x, y) -> {
            int ci = (int) Math.floor((x - 400000) / d), cj = (int) Math.floor((y - 6000000) / d);
            int pi = (int) Math.floor((x - 400000 - ci * d) / px), pj = (int) Math.floor((y - 6000000 - cj * d) / px);
            if (ci < 15) return -9;
            if (ci == 15 && cj == 5 && pi == 0 && pj >= 6 && pj <= 13) return -9.5;
            return 10;
        }, 0.025);
        h.fuellen(0);
        h.rand[Hydro.WEST] = Hydro.Rand.WASSERSTAND;
        double anlauf = 3600;
        Hydro.Randwert rw = (t, x, y) -> 2 * (t >= anlauf ? 1 : 0.5 * (1 - Math.cos(Math.PI * t / anlauf))) + 0.1 * Math.sin(2 * Math.PI * t / 600);
        h.randwert[Hydro.WEST] = rw;
        R.start(h);
        int tasche = 15 + nx * 5;
        double hoechst = Double.NEGATIVE_INFINITY, rand = Double.NEGATIVE_INFINITY, dtMin = Double.POSITIVE_INFINITY;
        while (h.zeit < 7200 - 1e-9) {
            double dt = Math.min(R.dtCfl(h), 7200 - h.zeit);
            dtMin = Math.min(dtMin, dt);
            R.schritt(h, dt);
            hoechst = Math.max(hoechst, h.eta[tasche]);
            rand = Math.max(rand, rw.wert(h.zeit, 0, 0));
        }
        A.pruefe(hoechst <= rand + 0.5, "Prüfung A, Fall 18: Speicher – Rinne aus 8 Pixeln zwischen geschlossenem Land, Wasser am Rand steigt in 1 h um 2 m (dazu 0,1 m mit 10 min), 2 h",
                f("η in der Rinne höchstens %.3f m, am Rand höchstens %.3f m; kleinster Schritt %.2f s", hoechst, rand, dtMin), "≤ Rand + 0,5 m [G]");
    }
}
