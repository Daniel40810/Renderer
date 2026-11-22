package com.dan.physics.sediment.demo;

import com.dan.core.pruef.Attest;
import com.dan.physics.sediment.Sediment;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Die Prüfsteine des Sediments für P9a (Werkbuch HalligHooge Teil A, 12.10), soweit sie ohne Grafikkarte laufen:
 * 1 Absinkgeschwindigkeit und Schwelle, 2 Schubspannung aus Strom und Welle, 3 Anpassung des Schwebstoffs (Galappatti) und Advektion
 * einer Wolke, 4 Schlick (Absetzsäule, Erosion), 5 Wanderung eines Hügels nach Exner, 6 Grabenwanderung (van Rijn),
 * 7 Böschungsrutsch, 8 Rippel, 9 Massenbilanz. Die Prüfsteine 10 (Referenz gegen Karte) und 11 (Zeiten auf der 680M)
 * rechnet {@link SedimentKarteCheck}; Teil 11 hier misst die Referenz auf dem Prozessor.
 * <p>
 * Die Vergleichswerte der Prüfsteine 1, 2 und 8 sind am 17.11.2026 unabhängig in Python aus den Formeln der Quellen
 * gerechnet (Seewasser 10 °C: ρ 1025 kg/m³, ν 1,36 · 10⁻⁶ m²/s; Korn 2650 kg/m³); die durchgerechneten Beispiele aus
 * Soulsby (1997) lagen nicht vor. {@code --teil n}: nur Teil n (dann ohne Attest).
 */
public final class SedimentCheck {
    static final Attest A = new Attest("com.dan.physics.sediment", Sediment.VERSION);

    public static void main(String[] args) {
        List<String> a = List.of(args);
        int ti = a.indexOf("--teil");
        int nur = ti >= 0 ? Integer.parseInt(a.get(ti + 1)) : 0;
        long t0 = System.nanoTime();
        try {
            if (nur == 0 || nur == 1) absinken();
            if (nur == 0 || nur == 2) schubspannung();
            if (nur == 0 || nur == 3) { anpassung(); wolke(); }
            if (nur == 0 || nur == 4) schlick();
            if (nur == 0 || nur == 5) huegel();
            if (nur == 0 || nur == 6) graben();
            if (nur == 0 || nur == 7) rutsch();
            if (nur == 0 || nur == 8) rippel();
            if (nur == 0 || nur == 9) bilanz();
            if (nur == 0 || nur == 11) zeiten();
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (nur == 0) {
            A.teil("Offen");
            A.info("Prüfsteine 1, 2, 8", "gegen unabhängig gerechnete Werte der Formeln (Python); die Beispiele aus Soulsby (1997) lagen nicht vor");
            A.info("Prüfstein 6 (Grabenwanderung)", "Auskunft: das gemessene Sohlprofil nach 15 h liegt nur als Abbildung vor");
            A.info("Prüfsteine 10 und 11 (Karte gegen Referenz, Zeiten auf der 680M)", "SedimentKarteCheck, Attest P9a_SedimentKarte.md");
            A.info("Rechenzeit", f("%.0f s", (System.nanoTime() - t0) * 1e-9));
            Path p = A.schreibe(Path.of("docs", "status", "P9a_Sediment.md"), "P9a – SedimentCheck (Referenz)");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Hilfen

    static void nurSchalter(Sediment s, boolean geschiebe, boolean schweb, boolean schlick, boolean rutsch, boolean rippel, boolean diffusion) {
        s.mitGeschiebe = geschiebe; s.mitSchweb = schweb; s.mitSchlick = schlick; s.mitRutsch = rutsch; s.mitRippel = rippel; s.mitDiffusion = diffusion;
    }

    /** Gleichförmiger Strom in +x: Tiefe h, Geschwindigkeit u. */
    static void strom(Sediment s, double h, double u) {
        Arrays.fill(s.h, h);
        Arrays.fill(s.qx, u * h);
        Arrays.fill(s.qy, 0);
    }

    static double rel(double wert, double soll) { return Math.abs(wert - soll) / Math.abs(soll); }

    // ------------------------------------------------------------------ 1 Absinken und Schwelle

    /** d (m), D*, w_s (m/s), θ_cr, τ_cr (N/m²). */
    static final double[][] KORN = {
            {6.3e-05, 1.281093358, 0.002286189974, 0.1196266622, 0.1201409073},
            {0.0001, 2.033481521, 0.005675257993, 0.08939674898, 0.1425095925},
            {0.0002, 4.066963042, 0.02024831972, 0.05531387397, 0.1763544587},
            {0.0005, 10.16740761, 0.06643222416, 0.03284606636, 0.2618036777},
            {0.001, 20.33481521, 0.1144134169, 0.0301886462, 0.4812447563},
            {0.002, 40.66963042, 0.1737275604, 0.03663933952, 1.168153742}};

    static void absinken() {
        A.teil("1 Absinkgeschwindigkeit und Schwelle (Soulsby 1997)");
        double rho = 1025, rhoS = 2650, nu = 1.36e-6, groesst = 0, groesstT = 0;
        StringBuilder b = new StringBuilder();
        for (double[] k : KORN) {
            double ws = Sediment.sink(k[0], rhoS / rho, nu), ds = Sediment.dStern(k[0], rhoS / rho, nu);
            double tcr = Sediment.thetaKrit(ds) * Sediment.G * (rhoS - rho) * k[0];
            groesst = Math.max(groesst, rel(ws, k[2]));
            groesstT = Math.max(groesstT, Math.max(rel(ds, k[1]), rel(tcr, k[4])));
            b.append(f("%.3f mm: %.2f mm/s, τ_cr %.3f N/m²; ", k[0] * 1e3, ws * 1e3, tcr));
        }
        A.hoechstens("Prüfstein 1: Absinkgeschwindigkeit, größte Abweichung über d = 0,063–2 mm", groesst * 100, 1.0, "%");
        A.hoechstens("Prüfstein 1: D* und kritische Schubspannung, größte Abweichung", groesstT * 100, 1.0, "%");
        A.info("Werte", b.toString());
    }

    // ------------------------------------------------------------------ 2 Schubspannung

    /** U, h, d, u_bot (quadratisches Mittel), T, φ (Grad) → τ_c, τ_w, τ_m, τ_max. */
    static final double[][] SCHUB = {
            {1, 5, 0.0002, 0, 0, 0, 1.216366276, 0, 1.216366276, 1.216366276},
            {0.5, 2, 0.0002, 0.3, 6, 0, 0.3584282381, 0.6720267414, 0.467956846, 1.139983587},
            {0.5, 2, 0.0002, 0.3, 6, 45, 0.3584282381, 0.6720267414, 0.467956846, 1.056098833},
            {0.5, 2, 0.0002, 0.3, 6, 90, 0.3584282381, 0.6720267414, 0.467956846, 0.8189038716},
            {0.2, 10, 0.0005, 0.5, 8, 30, 0.05058000364, 1.984647429, 0.1065796701, 2.077631667},
            {0, 3, 0.0003, 0.4, 7, 0, 0, 1.172333051, 0, 1.172333051}};

    static void schubspannung() {
        A.teil("2 Schubspannung aus Strom und Welle (Soulsby 1997)");
        double groesst = 0;
        StringBuilder b = new StringBuilder();
        for (double[] k : SCHUB) {
            Sediment s = new Sediment(3, 1, 10, 10);
            nurSchalter(s, false, false, false, false, false, false);
            strom(s, k[1], k[0]);
            Arrays.fill(s.d50, k[2]);
            Arrays.fill(s.ubot, k[3]);
            Arrays.fill(s.tp, k[4]);
            Arrays.fill(s.wcos, Math.cos(Math.toRadians(k[5])));
            Arrays.fill(s.wsin, Math.sin(Math.toRadians(k[5])));
            s.teil(1e-3);
            double tm = s.tauM[1], tx = s.tauMax[1];
            groesst = Math.max(groesst, Math.max(k[8] > 0 ? rel(tm, k[8]) : Math.abs(tm), rel(tx, k[9])));
            b.append(f("U %.1f, h %.0f, u_bot %.1f, φ %.0f°: τ_m %.3f, τ_max %.3f; ", k[0], k[1], k[3], k[5], tm, tx));
        }
        A.hoechstens("Prüfstein 2: τ_m und τ_max im Schritt des Modells gegen die Formeln, größte Abweichung über 6 Fälle", groesst * 100, 2.0, "%");
        A.info("Werte (N/m²)", b.toString());
    }

    // ------------------------------------------------------------------ 3 Schwebstoff

    static void anpassung() {
        A.teil("3 Schwebstoff: Anpassung an das Gleichgewicht");
        int nx = 300;
        double dx = 10, h = 5, u = 1.0;
        Sediment s = new Sediment(nx, 1, dx, dx);
        nurSchalter(s, false, true, false, false, false, false);
        Arrays.fill(s.d50, 1e-4);
        s.randSand = 0;
        strom(s, h, u);
        for (int n = 0; n < 200; n++) { strom(s, h, u); s.schritt(60); }
        int m = nx / 2;
        double ceq = s.gleichgewicht(m), laenge = u / s.austausch(m), groesst = 0;
        StringBuilder b = new StringBuilder();
        for (double x : new double[] {0.5, 1, 2, 3}) {
            int i = (int) Math.round(x * laenge / dx - 0.5);
            double soll = ceq * (1 - Math.exp(-(i + 0.5) * dx / laenge)), ist = s.cs[i];
            groesst = Math.max(groesst, rel(ist, soll));
            b.append(f("x = %.1f L: %.4f statt %.4f kg/m³; ", (i + 0.5) * dx / laenge, ist, soll));
        }
        A.info("Fall", f("d 0,1 mm, h %.0f m, U %.1f m/s: c_gl %.4f kg/m³, Anpassungslänge L = U T_s = %.0f m (%.0f Zellen)", h, u, ceq, laenge, laenge / dx));
        A.hoechstens("Prüfstein 3: Konzentration längs des Gerinnes gegen c_gl (1 − e^(−x/L)), größte Abweichung", groesst * 100, 5.0, "%");
        A.info("Werte", b.toString());
    }

    static void wolke() {
        A.teil("3 Schwebstoff: Advektion einer Wolke");
        int n = 80;
        double dx = 10, h = 3, u = 0.5, v = 0.3;
        Sediment s = new Sediment(n, n, dx, dx);
        nurSchalter(s, false, false, true, false, false, true);
        s.tauD = 0;                                                 // keine Ablagerung, kein Schlick im Boden: nur Transport
        Arrays.fill(s.h, h);
        Arrays.fill(s.qx, u * h);
        Arrays.fill(s.qy, v * h);
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            double x = (i + 0.5) * dx - 250, y = (j + 0.5) * dx - 250;
            s.cm[s.zelle(i, j)] = Math.exp(-(x * x + y * y) / (2 * 40 * 40));
        }
        double m0 = s.masse()[2], zeit = 400;
        double[] s0 = schwerpunkt(s);
        for (int k = 0; k < 40; k++) s.schritt(zeit / 40);
        double m1 = s.masse()[2], kleinst = Double.MAX_VALUE;
        for (double c : s.cm) kleinst = Math.min(kleinst, c);
        double[] s1 = schwerpunkt(s);
        A.hoechstens("Prüfstein 3: Masse der Wolke (im Gebiet und über den Rand) gegen den Anfang, relativ", (m1 + s.aus[1] - m0) / m0, 1e-6, "");
        A.pruefe(kleinst >= 0, "Kleinste Konzentration", f("%.3g kg/m³", kleinst), "≥ 0");
        A.hoechstens("Weg des Schwerpunkts gegen U · t (in Zellen)", Math.hypot(s1[0] - s0[0] - u * zeit, s1[1] - s0[1] - v * zeit) / dx, 1.0, "");
        A.info("Über den Rand", f("%.2g der Masse; %d Teilschritte", s.aus[1] / m0, s.teilschritte));
    }

    static double[] schwerpunkt(Sediment s) {
        double m = 0, x = 0, y = 0;
        for (int j = 0; j < s.ny; j++) for (int i = 0; i < s.nx; i++) {
            double w = s.cm[s.zelle(i, j)] * s.h[s.zelle(i, j)];
            m += w; x += w * (i + 0.5) * s.dx; y += w * (j + 0.5) * s.dy;
        }
        return new double[] {x / m, y / m};
    }

    // ------------------------------------------------------------------ 4 Schlick

    static void schlick() {
        A.teil("4 Schlick: Absetzsäule (Krone) und Erosion (Partheniades)");
        Sediment s = new Sediment(3, 1, 10, 10);
        nurSchalter(s, false, false, true, false, false, false);
        double h = 2, c0 = 0.1;
        Arrays.fill(s.h, h);
        Arrays.fill(s.cm, c0);
        double groesst = 0;
        for (int n = 1; n <= 400; n++) {
            s.schritt(20);
            if (n % 100 == 0) groesst = Math.max(groesst, rel(s.cm[1], c0 * Math.exp(-s.wsSchlick * s.zeit / h)));
        }
        A.hoechstens("Prüfstein 4: Absetzsäule über 8 000 s (2 h/w_s) gegen c0 e^(−w_s t/h), größte Abweichung", groesst * 100, 2.0, "%");
        A.hoechstens("Abgesetzt gegen den Verlust im Wasser, relativ", (s.mSchlick[1] - h * (c0 - s.cm[1])) / (h * c0), 1e-9, "");

        s = new Sediment(3, 1, 10, 10);
        nurSchalter(s, false, false, true, false, false, false);
        s.schlick(1);
        s.tauE1 = s.tauE0;
        strom(s, 3, 1.0);
        s.teil(1e-3);
        double tau = s.tauMax[1], rate = s.mErosion * (tau / s.tauE0 - 1) / 3, t0 = s.zeit, cAnf = s.cm[1];
        for (int n = 0; n < 60; n++) { strom(s, 3, 1.0); s.schritt(10); }
        A.hoechstens("Prüfstein 4: Erosion bei τ = " + f("%.2f", tau) + " N/m² gegen M (τ/τ_e − 1) t / h, Abweichung", rel(s.cm[1] - cAnf, rate * (s.zeit - t0)) * 100, 2.0, "%");
        A.info("Konzentration nach 600 s", f("%.4f kg/m³", s.cm[1]));
    }

    // ------------------------------------------------------------------ 5 Hügel nach Exner

    /** Geschiebe (kg/(s m)) bei festem Fluss q über der Tiefe h, wie das Modell es rechnet (nur Strom). */
    static double geschiebeBei(Sediment s, double q, double h, double d) {
        double u = q / h, tau = s.rho * Sediment.cdStrom(h, d / 12) * u * u, ds = Sediment.dStern(d, s.rhoS / s.rho, s.nu);
        double tcr = Sediment.thetaKrit(ds) * Sediment.G * (s.rhoS - s.rho) * d;
        return Sediment.geschiebe(d, ds, s.rho, s.rhoS, tau, tau, tcr);
    }

    static void huegel() {
        A.teil("5 Wanderung eines flachen Hügels nach Exner");
        int nx = 300;
        double dx = 2, tiefe = 2, q = 2.0, d = 3e-4, hoch = 0.02, breit = 20, x0 = 150;
        Sediment s = new Sediment(nx, 1, dx, dx);
        nurSchalter(s, true, false, false, false, false, false);
        s.epsHang = 0;
        Arrays.fill(s.d50, d);
        for (int i = 0; i < nx; i++) { double x = (i + 0.5) * dx - x0; s.grund[i] = hoch * Math.exp(-x * x / (2 * breit * breit)); }
        double eps = 1e-4, c = -(geschiebeBei(s, q, tiefe + eps, d) - geschiebeBei(s, q, tiefe - eps, d)) / (2 * eps) / (s.rhoS * (1 - s.poros));
        double zeit = 40 / c, dt = 400;
        int schritte = (int) Math.ceil(zeit / dt);
        double v0 = 0;
        for (int i = 0; i < nx; i++) v0 += s.grund[i];
        for (int n = 0; n < schritte; n++) {
            for (int i = 0; i < nx; i++) s.h[i] = tiefe - s.boden(i);
            Arrays.fill(s.qx, q);
            s.schritt(dt);
        }
        double m = 0, mx = 0, spitze = 0;
        for (int i = 0; i < nx; i++) { double z = s.boden(i); m += z; mx += z * (i + 0.5) * dx; spitze = Math.max(spitze, z); }
        double weg = mx / m - x0, soll = c * s.zeit;
        A.info("Fall", f("Tiefe %.0f m, U %.1f m/s, d 0,3 mm, Hügel %.0f cm hoch: Geschiebe %.4f kg/(s m), Geschwindigkeit der Sohlform %.2f m/Tag", tiefe, q / tiefe, hoch * 100,
                geschiebeBei(s, q, tiefe, d), c * 86400));
        A.hoechstens("Prüfstein 5: Weg des Schwerpunkts nach " + f("%.1f", s.zeit / 86400) + " Tagen gegen c · t (" + f("%.1f statt %.1f m", weg, soll) + "), Abweichung", rel(weg, soll) * 100, 5.0, "%");
        A.hoechstens("Volumen des Hügels gegen den Anfang (mit dem Fluss über den Rand), relativ", (m * dx * dx * s.rhoS * (1 - s.poros) + s.aus[2] - v0 * dx * dx * s.rhoS * (1 - s.poros)) / (v0 * dx * dx * s.rhoS * (1 - s.poros)), 1e-6, "");
        A.info("Höhe am Ende", f("%.1f von %.1f cm (aufwind erster Ordnung verbreitert den Hügel)", spitze * 100, hoch * 100));
    }

    // ------------------------------------------------------------------ 6 Graben nach van Rijn

    static void graben() {
        A.teil("6 Grabenwanderung (Laborfall von van Rijn 1987): Auskunft");
        int nx = 300;
        double dx = 0.1, h0 = 0.39, u0 = 0.51, q = h0 * u0, tief = 0.15, anfang = 8, boeschung = 1.5, sohle = 3;
        Sediment s = new Sediment(nx, 1, dx, dx);
        nurSchalter(s, true, true, false, true, true, true);
        s.rho = 1000; s.nu = 1.0e-6;
        Arrays.fill(s.d50, 1.6e-4);
        for (int i = 0; i < nx; i++) {
            double x = (i + 0.5) * dx - anfang;
            s.grund[i] = x < 0 || x > 2 * boeschung + sohle ? 0 : x < boeschung ? -tief * x / boeschung : x < boeschung + sohle ? -tief : -tief * (2 * boeschung + sohle - x) / boeschung;
        }
        double[] anf = s.grund.clone();
        // Einlauf: Rippel und Gleichgewicht stellen sich ein, der Boden bleibt
        for (int n = 0; n < 2400; n++) { Arrays.fill(s.h, h0); Arrays.fill(s.qx, q); s.schritt(1); }
        double cEin = s.cs[10], qbEin = s.geschiebe(10), rh = s.rH[10], rl = s.rL[10];
        Arrays.fill(s.dzS, 0);
        Arrays.fill(s.aus, 0);
        s.zeit = 0;
        for (int i = 0; i < nx; i++) s.cs[i] = cEin;
        for (int n = 0; n < 15 * 3600 / 5; n++) {
            for (int i = 0; i < nx; i++) s.h[i] = h0 - s.boden(i);
            Arrays.fill(s.qx, q);
            s.schritt(5);
        }
        double v0 = 0, v1 = 0, m0 = 0, m1 = 0, tiefst = 0;
        int wo = 0;
        for (int i = 0; i < nx; i++) {
            double a = -Math.min(0, anf[i]), b = -Math.min(0, s.boden(i));
            v0 += a; v1 += b; m0 += a * (i + 0.5) * dx; m1 += b * (i + 0.5) * dx;
            if (-s.boden(i) > tiefst) { tiefst = -s.boden(i); wo = i; }
        }
        A.info("Versuch", f("h %.2f m, U %.2f m/s, d50 0,16 mm, Graben %.2f m tief, Böschungen 1:10, 15 h; Süßwasser, ν 1,0 · 10⁻⁶ m²/s", h0, u0, tief));
        A.info("Zulauf im Gleichgewicht", f("Schwebstoff %.3f kg/m³ (im Versuch nachgerechnet mit 0,14 kg/m³ Zugabe), %.4f kg/(s m) in Schwebe, Geschiebe %.4f kg/(s m); Rippel %.1f cm hoch, %.0f cm lang",
                cEin, cEin * q, qbEin, rh * 100, rl * 100));
        A.info("Nach 15 h", f("Querschnitt des Grabens %.0f %% des Anfangs; Schwerpunkt %.2f m stromab gewandert; tiefste Stelle %.3f m bei x = %.1f m (Anfang: Sohle %.1f–%.1f m)",
                100 * v1 / v0, m1 / v1 - m0 / v0, tiefst, (wo + 0.5) * dx, anfang + boeschung, anfang + boeschung + sohle));
        A.info("Einordnung", "im Versuch blieb der Graben nach 15 h deutlich erhalten (veröffentlichte Abbildungen, hier nicht nachgemessen); das Modell verfüllt und verschmiert ihn schneller");
        A.info("Teilschritte", String.valueOf(s.teilschritte));
    }

    // ------------------------------------------------------------------ 7 Böschungsrutsch

    static void rutsch() {
        A.teil("7 Böschungsrutsch");
        int n = 41;
        double dx = 0.5;
        Sediment s = new Sediment(n, n, dx, dx);
        nurSchalter(s, false, false, false, true, false, false);
        double v0 = 0;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            double r = Math.hypot((i - 20) * dx, (j - 20) * dx);
            s.grund[s.zelle(i, j)] = Math.max(0, 5 - Math.tan(Math.toRadians(60)) * r);
            v0 += s.grund[s.zelle(i, j)];
        }
        double vor = steilste(s);
        for (int k = 0; k < 3000; k++) s.teil(1);
        double nach = steilste(s), v1 = 0;
        for (int c = 0; c < s.nc; c++) v1 += s.boden(c);
        A.pruefe(Math.toDegrees(Math.atan(nach)) <= 30.5, "Prüfstein 7: steilste Kante nach 3 000 Teilschritten (vorher " + f("%.1f", Math.toDegrees(Math.atan(vor))) + "°)",
                f("%.2f°", Math.toDegrees(Math.atan(nach))), "≤ 30,5° [G]");
        A.hoechstens("Prüfstein 7: Volumen gegen den Anfang, relativ", (v1 - v0) / v0, 1e-12, "");
    }

    static double steilste(Sediment s) {
        double m = 0;
        for (int j = 0; j < s.ny; j++) for (int i = 0; i < s.nx; i++) {
            int c = s.zelle(i, j);
            if (i + 1 < s.nx) m = Math.max(m, Math.abs(s.boden(c + 1) - s.boden(c)) / s.dx);
            if (j + 1 < s.ny) m = Math.max(m, Math.abs(s.boden(c + s.nx) - s.boden(c)) / s.dy);
        }
        return m;
    }

    // ------------------------------------------------------------------ 8 Rippel

    /** d (m), η_max (m), λ_max (m), θ_wo, θ_sf. */
    static final double[][] RIPPEL = {
            {0.0001, 0.01363283825, 0.1148676926, 0.6597748989, 1.625589962},
            {0.0002, 0.01857144344, 0.1458683853, 0.2679518713, 0.6601946709},
            {0.0004, 0.02529909804, 0.2324338463, 0.1088222748, 0.2681223518}};

    static void rippel() {
        A.teil("8 Rippel (Soulsby und Whitehouse 2005)");
        double groesst = 0;
        StringBuilder b = new StringBuilder();
        for (double[] k : RIPPEL) {
            Sediment s = new Sediment(3, 1, 10, 10);
            nurSchalter(s, true, false, false, false, true, false);
            Arrays.fill(s.d50, k[0]);
            double u = 0.5;
            for (int n = 0; n < 4000; n++) { strom(s, 2, u); s.teil(10); }
            groesst = Math.max(groesst, Math.max(rel(s.rH[1], k[1]), rel(s.rL[1], k[2])));
            b.append(f("d %.1f mm: %.1f mm hoch, %.0f mm lang; ", k[0] * 1e3, s.rH[1] * 1e3, s.rL[1] * 1e3));
        }
        A.hoechstens("Prüfstein 8: Höhe und Länge im Gleichgewicht gegen die Formeln, größte Abweichung", groesst * 100, 2.0, "%");
        A.info("Werte", b.toString());

        Sediment s = new Sediment(3, 3, 10, 10);
        nurSchalter(s, true, false, false, false, true, false);
        for (int n = 0; n < 2000; n++) { strom(s, 2, 0.5); s.teil(10); }
        double vorher = Math.toDegrees(s.rippelRichtung(4));
        for (int n = 0; n < 4000; n++) { Arrays.fill(s.h, 2); Arrays.fill(s.qx, 0); Arrays.fill(s.qy, 1.0); s.teil(10); }
        double nachher = Math.toDegrees(s.rippelRichtung(4));
        A.pruefe(Math.abs(vorher) <= 2 && Math.abs(nachher - 90) <= 2, "Prüfstein 8: Richtung folgt dem Strom (erst +x, dann +y)", f("%.1f°, dann %.1f°", vorher, nachher), "0° und 90° ± 2°");
        double hoch = s.rH[4];
        for (int n = 0; n < 500; n++) { Arrays.fill(s.h, 2); Arrays.fill(s.qx, 0); Arrays.fill(s.qy, 0.2); s.teil(10); }
        A.pruefe(s.rH[4] == hoch, "Unter der Schwelle (U 0,1 m/s) bleiben die Rippel stehen", f("%.2f mm", s.rH[4] * 1e3), "unverändert");
        for (int n = 0; n < 2000; n++) { Arrays.fill(s.h, 2); Arrays.fill(s.qx, 0); Arrays.fill(s.qy, 3.0); s.teil(10); }
        A.pruefe(s.rH[4] < 0.01 * hoch, "Über der Grenze zum Schichtfluss (U 1,5 m/s) verschwinden sie", f("%.3f mm", s.rH[4] * 1e3), "< 1 % der Höhe");
    }

    // ------------------------------------------------------------------ 9 Massenbilanz

    /** Der Bilanzfall: Rinne und Sandbank mit Trockenfallen, schnelle Tide, Wirbel, Seegang, Sand und Schlick; auch für die Karte. */
    public static Sediment bilanzfall(int nx, int ny) { return bilanzfall(nx, ny, false); }

    /** {@code subgrid}: dazu Tiefe und Geschwindigkeit des Stroms getrennt vom Volumen (benetzter Anteil 0,55–1) und Porosität je Zelle, wie am Bus (seit 0.2). */
    public static Sediment bilanzfall(int nx, int ny, boolean subgrid) {
        Sediment s = new Sediment(nx, ny, 20, 20);
        if (subgrid) {
            s.tiefe = new double[s.nc]; s.ug = new double[s.nc]; s.vg = new double[s.nc]; s.porosZ = new double[s.nc];
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) s.porosZ[s.zelle(i, j)] = 0.34 + 0.1 * (i + 0.5) / nx;
        }
        Arrays.fill(s.d50, 1.5e-4);
        Arrays.fill(s.ubot, 0.2);
        Arrays.fill(s.tp, 5);
        Arrays.fill(s.wcos, Math.cos(Math.toRadians(30)));
        Arrays.fill(s.wsin, Math.sin(Math.toRadians(30)));
        s.schlick(0.2);
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            double x = (i + 0.5) / nx, y = (j + 0.5) / ny;
            s.grund[s.zelle(i, j)] = -3 + 2.2 * x + 1.4 * Math.exp(-Math.pow((y - 0.5) / 0.18, 2)) * Math.exp(-Math.pow((x - 0.55) / 0.2, 2)) - 1.2 * Math.exp(-Math.pow((y - 0.2) / 0.08, 2));
            s.h[s.zelle(i, j)] = Math.max(0, pegel(0) - s.grund[s.zelle(i, j)]);
        }
        return s;
    }

    static double pegel(double t) { return 0.2 + 0.9 * Math.sin(2 * Math.PI * t / 3600); }

    /** Flüsse des Bilanzfalls für den Schritt von t bis t + dt: zeilenweise aus der Kontinuität zum Zielpegel (Ostwand zu), dazu ein Wirbel ohne Divergenz. */
    public static void bilanzfluss(Sediment s, double t, double dt) {
        int nx = s.nx, ny = s.ny;
        for (int j = 0; j < ny; j++) {
            s.qx[s.kx(nx, j)] = 0;
            for (int i = nx - 1; i >= 0; i--) {
                int c = s.zelle(i, j);
                double ziel = Math.max(0, pegel(t + dt) - s.grund[c]);
                s.qx[s.kx(i, j)] = s.qx[s.kx(i + 1, j)] + (ziel - s.h[c]) * s.dx / dt;
            }
        }
        Arrays.fill(s.qy, 0);
        double staerke = 0.15 * Math.sin(2 * Math.PI * t / 3600 + 1);
        double[] psi = new double[(nx + 1) * (ny + 1)];
        for (int j = 1; j < ny; j++) for (int i = 1; i < nx; i++) {
            double hm = Math.min(Math.min(s.h[s.zelle(i - 1, j - 1)], s.h[s.zelle(i, j - 1)]), Math.min(s.h[s.zelle(i - 1, j)], s.h[s.zelle(i, j)]));
            psi[i + (nx + 1) * j] = staerke * s.dx * Math.min(hm, 2) * Math.sin(Math.PI * i / nx) * Math.sin(Math.PI * j / ny);
        }
        for (int j = 0; j < ny; j++) for (int i = 0; i <= nx; i++) s.qx[s.kx(i, j)] += (psi[i + (nx + 1) * (j + 1)] - psi[i + (nx + 1) * j]) / s.dy;
        for (int j = 0; j <= ny; j++) for (int i = 0; i < nx; i++) s.qy[s.ky(i, j)] -= (psi[i + 1 + (nx + 1) * j] - psi[i + (nx + 1) * j]) / s.dx;
        if (s.tiefe != null) for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            int c = s.zelle(i, j);
            double anteil = 0.55 + 0.45 * Math.pow(Math.sin(0.37 * i + 0.23 * j), 2);
            s.tiefe[c] = s.h[c] / anteil;
            s.ug[c] = s.tiefe[c] > 0 ? 0.5 * (s.qx[s.kx(i, j)] + s.qx[s.kx(i + 1, j)]) / s.tiefe[c] : 0;
            s.vg[c] = s.tiefe[c] > 0 ? 0.5 * (s.qy[s.ky(i, j)] + s.qy[s.ky(i, j + 1)]) / s.tiefe[c] : 0;
        }
    }

    static void bilanz() {
        bilanz(false);
        bilanz(true);
    }

    static void bilanz(boolean subgrid) {
        A.teil(subgrid ? "9 Massenbilanz mit benetztem Anteil und Porosität je Zelle (seit 0.2)" : "9 Massenbilanz");
        Sediment s = bilanzfall(60, 40, subgrid);
        double[] m0 = s.masse();
        double umsatzS = 0, umsatzM = 0, trocken = 0;
        double[] dzVor = new double[s.nc], mVor = new double[s.nc];
        int schritte = 240;
        for (int n = 0; n < schritte; n++) {
            System.arraycopy(s.dzS, 0, dzVor, 0, s.nc);
            System.arraycopy(s.mSchlick, 0, mVor, 0, s.nc);
            bilanzfluss(s, n * 30.0, 30);
            s.schritt(30);
            for (int c = 0; c < s.nc; c++) {
                umsatzS += Math.abs(s.dzS[c] - dzVor[c]) * s.rhoS * (1 - s.porositaet(c)) * s.dx * s.dy;
                umsatzM += Math.abs(s.mSchlick[c] - mVor[c]) * s.dx * s.dy;
                if (s.h[c] < s.hMin) trocken++;
            }
        }
        double[] m1 = s.masse();
        double fehlS = m1[0] + m1[1] + s.aus[0] + s.aus[2] - m0[0] - m0[1], fehlM = m1[2] + m1[3] + s.aus[1] - m0[2] - m0[3];
        double groesst = 0, cMax = 0;
        for (int c = 0; c < s.nc; c++) { groesst = Math.max(groesst, Math.abs(s.dzS[c] + s.dzM[c])); cMax = Math.max(cMax, s.schweb(c)); }
        A.info("Fall", f("60 × 40 Zellen zu 20 m, Tide 0,9 m in 1 h über 2 h, Seegang u_bot 0,2 m/s; im Mittel %.0f %% der Zellen trocken; %d Teilschritte", 100 * trocken / (schritte * s.nc), s.teilschritte));
        A.info("Umsatz", f("Sand %.0f t am Boden bewegt, %.1f t über den Rand; Schlick %.0f t, %.1f t über den Rand; größte Bodenänderung %.1f mm, Schwebstoff bis %.2f kg/m³",
                umsatzS / 1e3, (s.aus[0] + s.aus[2]) / 1e3, umsatzM / 1e3, s.aus[1] / 1e3, groesst * 1e3, cMax));
        A.hoechstens("Prüfstein 9: Sand (Wasser, Boden, Rand) gegen den Anfang, relativ zum Umsatz", fehlS / umsatzS, 1e-6, "");
        A.hoechstens("Prüfstein 9: Schlick (Wasser, Boden, Rand) gegen den Anfang, relativ zum Umsatz", fehlM / umsatzM, 1e-6, "");
        boolean endlich = true;
        for (int c = 0; c < s.nc; c++) endlich &= Double.isFinite(s.cs[c]) && Double.isFinite(s.cm[c]) && Double.isFinite(s.dzS[c]) && s.cs[c] >= 0 && s.cm[c] >= 0;
        A.pruefe(endlich, "Alle Werte endlich, Konzentrationen nicht negativ", endlich ? "ja" : "nein", "ja");
    }

    // ------------------------------------------------------------------ 11 Zeiten der Referenz

    static void zeiten() {
        A.teil("11 Zeiten der Referenz auf dem Prozessor (Auskunft)");
        Sediment s = bilanzfall(250, 200);
        bilanzfluss(s, 0, 30);
        for (int k = 0; k < 5; k++) s.teil(1);
        long t0 = System.nanoTime();
        int n = 40;
        for (int k = 0; k < n; k++) s.teil(1);
        double ns = (System.nanoTime() - t0) / (double) n / s.nc;
        A.info("Referenz, ein Faden", f("%.0f ns je Zelle und Teilschritt (250 × 200 Zellen)", ns));
    }
}
