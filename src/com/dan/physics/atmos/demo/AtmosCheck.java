package com.dan.physics.atmos.demo;

import com.dan.core.pruef.Attest;
import com.dan.physics.atmos.Atmos;
import com.dan.physics.atmos.Luft;
import com.dan.physics.atmos.Profil;
import com.dan.physics.atmos.Schicht;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Die Prüfsteine von atmos für P10a (Werkbuch HalligHooge Teil A, 12.11), ohne Gebiet: 1 Strahlungsgleichgewicht,
 * 2 Bodenwärme gegen die analytische Lösung, 3 logarithmisches Windprofil, 4 stabile und labile Schichtung gegen eine
 * unabhängige Rechnung, 5 Schließung der Energiebilanz über einen Tag, 6 Trocknung, 7 flaches Wasser, 8 Brechzahl nach
 * Ciddor gegen die Werte des NIST, 13 Rechenzeit; seit P10b dazu 14 Entwässerung (Prüfstein 8 von P10b, der Teil ohne Gebiet); seit P10c 15 Regen (F97 A). Die Prüfsteine 9–11 (Strahlen durch das Profil) rechnet
 * {@code com.dan.lauf.demo.KimmungCheck}: Sie brauchen {@code render.reference.Strahlgleichung}, und ein Physikmodul
 * kennt die Grafik nicht. Prüfstein 12 (Schichtenvertrag) prüft der Bau ({@code check-packages}).
 * <p>
 * Die unabhängige Rechnung zu 4 und 6 steht hier ({@link #frei}): die Funktionen φ von Businger und Dyer selbst, über
 * ln z nach Simpson integriert statt in der geschlossenen Form von Paulson, und die Obukhov-Länge durch Halbieren
 * statt durch die gedämpfte Folge von {@link Schicht}. {@code --teil n}: nur Teil n (dann ohne Attest).
 */
public final class AtmosCheck {
    static final Attest A = new Attest("com.dan.physics.atmos", Atmos.VERSION);

    public static void main(String[] args) {
        List<String> a = List.of(args);
        int ti = a.indexOf("--teil");
        int nur = ti >= 0 ? Integer.parseInt(a.get(ti + 1)) : 0;
        long t0 = System.nanoTime();
        try {
            if (nur == 0 || nur == 1) strahlung();
            if (nur == 0 || nur == 2) boden();
            if (nur == 0 || nur == 3) neutral();
            if (nur == 0 || nur == 4) schichtung();
            if (nur == 0 || nur == 5) bilanz();
            if (nur == 0 || nur == 6) trocknung();
            if (nur == 0 || nur == 7) wasser();
            if (nur == 0 || nur == 8) ciddor();
            if (nur == 0 || nur == 13) zeiten();
            if (nur == 0 || nur == 14) entwaesserung();
            if (nur == 0 || nur == 15) regen();
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (nur == 0) {
            A.teil("Offen");
            A.info("Prüfsteine 9–11 (Kreisbogen, gerader Strahl, untere Spiegelung)", "KimmungCheck, Attest P10a_Kimmung.md");
            A.info("Prüfstein 12 (Schichtenvertrag, Feldnamen)", "check-packages im Bau");
            A.info("Prüfstein 6", "ein Literaturwert für die Trocknungszeit ist nicht nachgeschlagen; die Handrechnung steht oben");
            A.info("Rechenzeit", f("%.0f s", (System.nanoTime() - t0) * 1e-9));
            Path p = A.schreibe(Path.of("docs", "status", "P10a_Atmos.md"), "P10a – AtmosCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static void regen() {
        A.teil("15 Regen (P10c, F97 A)");
        double p = 101300;
        // (a) ohne Verdunstung und ohne Entwässerung: 1 mm in einer Stunde hebt die Sättigung des obersten Zentimeters um 1 / (0,4 · 10 mm)
        Atmos at = sand(false, p);
        at.mitEntwaesserung = false;
        at.wet[0] = 0.10;
        at.regen[0] = 1.0 / 3600;
        for (int i = 0; i < 12; i++) at.schritt(300);
        double soll = 0.10 + 1.0 / (at.poros * at.dicke[0] * Luft.RHO_W);
        A.hoechstens("Nässe nach 1 mm Regen in einer Stunde auf Sand mit Sättigung 0,10 (Soll " + f("%.4f", soll) + ")", Math.abs(at.wet[0] - soll), 1e-9, "");
        // (b) mehr als die Schicht fasst: Sättigung 1, nicht darüber
        for (int i = 0; i < 48; i++) at.schritt(300);
        A.pruefe(at.wet[0] == 1, "Nach weiteren 4 mm (die Schicht fasst 4 mm)", f("%.6f", at.wet[0]), "genau 1");
        // Dass atmos ohne Regen rechnet wie 0.2, prüft der Nachlauf im Gebiet (AeolianLaufCheck, Prüfstein 11): bitgleich gegen das Archiv
        // (c) Regen mit Verdunstung: nach dem Schauer nasser, danach trocknet es wieder
        Atmos v = sand(true, p), o = sand(true, p);
        v.ueber[0] = o.ueber[0] = 1.2;
        v.grundwasser[0] = o.grundwasser[0] = 1.2;
        for (int i = 0; i < 288; i++) { v.schritt(300); o.schritt(300); }
        double vor = v.wet[0];
        v.regen[0] = 2.0 / 3600;
        for (int i = 0; i < 12; i++) { v.schritt(300); o.schritt(300); }
        double nach = v.wet[0];
        v.regen[0] = 0;
        int stunden = 0;
        while (v.wet[0] > o.wet[0] + 0.02 && stunden < 240) {
            for (int i = 0; i < 12; i++) { v.schritt(300); o.schritt(300); }
            stunden++;
        }
        A.pruefe(nach > vor + 0.3 && stunden > 0 && stunden < 240, "Schauer von 2 mm auf trockenem Sand (1,2 m über dem Grundwasser, 15 °C, 5 m/s, 400 W/m²)",
                f("Nässe %.2f → %.2f; nach %d h wieder auf 0,02 an der Rechnung ohne Schauer", vor, nach, stunden), "steigt um > 0,3; trocknet wieder [G]");
    }

    static double rel(double wert, double soll) { return Math.abs(wert - soll) / Math.abs(soll); }

    static double feuchte(double rf, double T, double p) { return Luft.feuchte(rf * Luft.saettigung(T), p); }

    // ------------------------------------------------------------------ 1 Strahlungsgleichgewicht

    static void strahlung() {
        A.teil("1 Strahlungsgleichgewicht");
        double[][] faelle = {{0, 300}, {300, 320}, {600, 350}};
        double groesst = 0;
        StringBuilder b = new StringBuilder();
        for (double[] fa : faelle) {
            Atmos at = new Atmos(1, 1, 1);
            at.mitTurbulenz = false;
            at.mitBoden = false;
            at.wetter(0, 283.15, 0.005, 101325, fa[0], fa[1], 283.15);
            at.anfang(283.15);
            for (int i = 0; i < 5; i++) at.schritt(300);
            double soll = Math.pow(((1 - at.albedoNass) * fa[0] + at.emission * fa[1]) / (at.emission * Luft.SIGMA), 0.25);
            groesst = Math.max(groesst, Math.abs(at.ts[0] - soll));
            b.append(f("S %.0f, L %.0f W/m²: %.3f K (Soll %.3f); ", fa[0], fa[1], at.ts[0], soll));
        }
        A.hoechstens("Prüfstein 1: Fläche ohne Wind und ohne Bodenwärme gegen Einstrahlung = ε σ T⁴, größte Abweichung", groesst, 0.1, "K");
        A.info("Werte", b.toString());
    }

    // ------------------------------------------------------------------ 2 Bodenwärme

    static void boden() {
        A.teil("2 Bodenwärme");
        Atmos at = new Atmos(1, 1, 1);
        at.mitTurbulenz = false;
        double t0 = 283.15, amp = 5, periode = 86400, om = 2 * Math.PI / periode, dt = 300;
        at.h[0] = 1.0;                                  // tiefes Wasser: Die Oberfläche hat die Temperatur, die das Wetter vorgibt
        at.wetter(0, t0, 0.005, 101325, 0, 300, t0);
        at.anfang(t0);
        int tage = 20, je = (int) (periode / dt), n = Atmos.SCHICHTEN;
        double[] s = new double[n + 1], c = new double[n + 1];
        for (int i = 1; i <= tage * je; i++) {
            double t = i * dt;
            Arrays.fill(at.tMeer, t0 + amp * Math.sin(om * t));
            at.schritt(dt);
            if (i > (tage - 1) * je) {
                for (int k = 0; k < n; k++) {
                    s[k] += (at.tb[k] - t0) * Math.sin(om * t);
                    c[k] += (at.tb[k] - t0) * Math.cos(om * t);
                }
                s[n] += at.gb[0] * Math.sin(om * t);
                c[n] += at.gb[0] * Math.cos(om * t);
            }
        }
        double kappa = at.leit / at.kapaz, d = Math.sqrt(2 * kappa / om), tiefe = 0, gesamt = 0;
        for (double x : at.dicke) gesamt += x;
        double groesstA = 0, groesstP = 0;
        StringBuilder b = new StringBuilder(), bs = new StringBuilder();
        for (int k = 0; k < n; k++) {
            double z = tiefe + at.dicke[k] / 2;
            tiefe += at.dicke[k];
            double a = 2.0 / je * Math.hypot(s[k], c[k]), ph = Math.atan2(c[k], s[k]);
            double aSoll = amp * Math.exp(-z / d), phSoll = -z / d;
            double fa = a / aSoll - 1, fp = winkel(ph - phSoll) / (2 * Math.PI);
            if (k == 1 || k == 2) {
                groesstA = Math.max(groesstA, Math.abs(fa));
                groesstP = Math.max(groesstP, Math.abs(fp));
            }
            b.append(f("%.3f m: %.3f K (Soll %.3f, %+.1f %%), Phase %+.2f %% der Periode; ", z, a, aSoll, fa * 100, fp * 100));
            // Platte der Dicke 0,5 m ohne Fluss unten: T = A cosh(k (L − z)) / cosh(k L), k = (1 + i) / d
            double[] zae = cosh((gesamt - z) / d, (gesamt - z) / d), nen = cosh(gesamt / d, gesamt / d);
            double re = (zae[0] * nen[0] + zae[1] * nen[1]) / (nen[0] * nen[0] + nen[1] * nen[1]), im = (zae[1] * nen[0] - zae[0] * nen[1]) / (nen[0] * nen[0] + nen[1] * nen[1]);
            bs.append(f("%.3f m: %+.1f %%, %+.2f %%; ", z, (a / (amp * Math.hypot(re, im)) - 1) * 100, winkel(ph - Math.atan2(im, re)) / (2 * Math.PI) * 100));
        }
        A.hoechstens("Prüfstein 2: Tagesgang im Boden, Amplitude in 2,5 und 7 cm Tiefe gegen den Halbraum", groesstA * 100, 5.0, "%");
        A.hoechstens("Prüfstein 2: Phase dort, in Teilen der Periode", groesstP * 100, 5.0, "%");
        A.info("Dämpfungstiefe", f("%.3f m (λ %.1f W/(m K), C %.2f MJ/(m³ K))", d, at.leit, at.kapaz * 1e-6));
        A.info("Alle Schichten gegen den Halbraum", b.toString());
        A.info("Alle Schichten gegen die Platte von 0,5 m ohne Fluss unten (Amplitude, Phase)", bs.toString());
        double g = 2.0 / je * Math.hypot(s[n], c[n]), gSoll = at.leit * amp * Math.sqrt(2) / d, gp = winkel(Math.atan2(c[n], s[n]) - Math.PI / 4) / (2 * Math.PI);
        A.info("Fluss in den Boden", f("Amplitude %.1f W/m² (Halbraum %.1f, %+.1f %%), Phase %+.2f %% der Periode gegen 45° Vorlauf", g, gSoll, (g / gSoll - 1) * 100, gp * 100));
    }

    static double winkel(double w) {
        while (w > Math.PI) w -= 2 * Math.PI;
        while (w < -Math.PI) w += 2 * Math.PI;
        return w;
    }

    /** cosh(a + i b). */
    static double[] cosh(double a, double b) { return new double[] {Math.cosh(a) * Math.cos(b), Math.sinh(a) * Math.sin(b)}; }

    // ------------------------------------------------------------------ unabhängige Rechnung der Schicht

    static double phiM(double z) { return z < 0 ? Math.pow(1 - 16 * z, -0.25) : z <= 1 ? 1 + 5 * z : 6; }

    static double phiH(double z) { return z < 0 ? Math.pow(1 - 16 * z, -0.5) : z <= 1 ? 1 + 5 * z : 6; }

    /** ∫ φ(z/L) dz/z von za bis zb, nach Simpson über ln z mit n Abschnitten (n gerade). */
    static double integral(boolean impuls, double za, double zb, double invL, int n) {
        double x0 = Math.log(za), dx = (Math.log(zb) - x0) / n, sum = 0;
        for (int i = 0; i <= n; i++) {
            double z = Math.exp(x0 + i * dx), w = i == 0 || i == n ? 1 : i % 2 == 1 ? 4 : 2;
            sum += w * (impuls ? phiM(z * invL) : phiH(z * invL));
        }
        return sum * dx / 3;
    }

    /** Ergebnis der unabhängigen Rechnung: u*, θ*, q*, 1/L, z0, z0h. */
    record Frei(double us, double ts, double qs, double invL, double z0, double z0h) {
        double wind(double z) { return us / Luft.KAPPA * integral(true, z0, z, invL, 400); }

        double theta(double z) { return ts / Luft.KAPPA * integral(false, z0h, z, invL, 400); }
    }

    /** Die Größen der Schicht zu einem festen 1/L. */
    static Frei bei(double il, double u, double zu, double dth, double zt, double dq, double z0Land, boolean wasser, int n) {
        double z0 = z0Land, us = 0.03 * u;
        for (int i = 0; i < (wasser ? 60 : 1); i++) {
            if (wasser) z0 = Math.max(1e-6, 0.018 * us * us / Luft.G + 0.11 * Luft.NU / us);
            us = Luft.KAPPA * u / integral(true, z0, zu, il, n);
        }
        double z0h = wasser ? z0 : z0 / 10, fh = integral(false, z0h, zt, il, n);
        return new Frei(us, Luft.KAPPA * dth / fh, Luft.KAPPA * dq / fh, il, z0, z0h);
    }

    /** Unabhängige Lösung: 1/L durch Halbieren der Gleichung 1/L = κ g θ_v* / (θ_v u*²). */
    static Frei frei(double u, double zu, double thA, double zt, double qA, double th0, double q0, double z0Land, boolean wasser, int n) {
        double thv = thA * (1 + 0.608 * qA), dth = thA - th0, dq = qA - q0;
        double lo = -50, hi = 1.0 / zu;
        java.util.function.DoubleUnaryOperator rest = il -> {
            Frei fr = bei(il, u, zu, dth, zt, dq, z0Land, wasser, n);
            return il - Luft.KAPPA * Luft.G * (fr.ts * (1 + 0.608 * qA) + 0.608 * thA * fr.qs) / (thv * fr.us * fr.us);
        };
        double rl = rest.applyAsDouble(lo), rh = rest.applyAsDouble(hi);
        if (rh < 0) return bei(hi, u, zu, dth, zt, dq, z0Land, wasser, n);       // über der Grenze ζ = 1: dort bleibt auch Schicht stehen
        if (rl > 0) throw new IllegalStateException("keine Lösung über −50 je m");
        for (int i = 0; i < 70; i++) {
            double m = 0.5 * (lo + hi), rm = rest.applyAsDouble(m);
            if (rm > 0) hi = m;
            else lo = m;
        }
        return bei(0.5 * (lo + hi), u, zu, dth, zt, dq, z0Land, wasser, n);
    }

    // ------------------------------------------------------------------ 3 neutral

    static void neutral() {
        A.teil("3 Neutrale Schichtung");
        Schicht s = new Schicht();
        double u = 8, z0 = 1e-3, th = 283.15, q = 0.005, groesst = 0;
        s.loese(u, 10, th, 2, q, th, q, z0, false, 0, 200, 1e-15);
        double us = Luft.KAPPA * u / Math.log(10 / z0);
        for (double z : new double[] {0.1, 0.5, 2, 10, 50, 100}) groesst = Math.max(groesst, rel(s.wind(z), us / Luft.KAPPA * Math.log(z / z0)));
        A.hoechstens("Prüfstein 3: logarithmisches Windprofil über Land (8 m/s in 10 m, z0 1 mm), 0,1–100 m", groesst * 100, 0.1, "%");
        A.info("u*, 1/L", f("%.4f m/s (Soll %.4f), %.1e je m", s.ustern, us, s.invL));
        // über Wasser: u* und z0 nach Charnock müssen sich gegenseitig erfüllen
        s.loese(u, 10, th, 2, q, th, q, z0, true, 0, 200, 1e-15);
        double zc = s.charnock * s.ustern * s.ustern / Luft.G + 0.11 * Luft.NU / s.ustern;
        A.hoechstens("Prüfstein 3: über Wasser erfüllen u* und z0 nach Charnock das Profil zugleich", Math.max(rel(s.z0, zc), rel(s.wind(10), u)) * 100, 0.1, "%");
        A.info("über Wasser", f("u* %.4f m/s, z0 %.3f mm, C_d(10 m) %.2f · 10⁻³", s.ustern, s.z0 * 1e3, s.ustern * s.ustern / (u * u) * 1e3));
    }

    // ------------------------------------------------------------------ 4 Schichtung

    /** Wind (m/s), Temperatur der Luft in 2 m (°C), Temperatur der Oberfläche (°C), relative Feuchte, Wasser (1) oder Land (0). */
    static final double[][] SCHICHT = {
            {3, 7, 12, 0.8, 1},          // labil über warmem Wasser: der Fall der unteren Spiegelung
            {4, 12, 20, 0.6, 0},         // labil über sonnigem Watt
            {1.5, 15, 27, 0.5, 0},       // schwacher Wind, stark labil
            {5, 12, 9, 0.8, 0},          // stabil über kühlem Watt
            {8, 14, 9, 0.9, 1},          // stabil: warme Luft über kaltem Wasser
            {6, 10, 9.5, 0.7, 1}};       // fast neutral

    static void schichtung() {
        A.teil("4 Stabile und labile Schichtung (Businger–Dyer)");
        double p = 101325, groesstF = 0, groesstP = 0;
        StringBuilder b = new StringBuilder();
        Schicht s = new Schicht();
        for (double[] fa : SCHICHT) {
            double ta = fa[1] + 273.15, t0 = fa[2] + 273.15, thA = ta + Luft.GAMMA * 2, qa = feuchte(fa[3], ta, p), q0 = Luft.feuchteSatt(t0, p);
            boolean wasser = fa[4] > 0;
            s.loese(fa[0], 10, thA, 2, qa, t0, q0, 1e-3, wasser, 0, 2000, 1e-15);
            Frei fr = frei(fa[0], 10, thA, 2, qa, t0, q0, 1e-3, wasser, 2000);
            double rho = Luft.dichte(ta, qa, p), lv = Luft.verdampfung(ta);
            double hf = -rho * Luft.CP * s.ustern * s.tstern, le = -rho * lv * s.ustern * s.qstern, hfF = -rho * Luft.CP * fr.us * fr.ts, leF = -rho * lv * fr.us * fr.qs;
            groesstF = Math.max(groesstF, Math.max(rel(s.ustern, fr.us), Math.max(rel(hf, hfF), rel(le, leF))));
            for (double z : new double[] {0.1, 0.5, 1, 2, 5, 10, 30}) {
                groesstP = Math.max(groesstP, rel(s.wind(z), fr.wind(z)));
                groesstP = Math.max(groesstP, Math.abs(s.thetaUeber(z) - fr.theta(z)) / Math.abs(thA - t0));
            }
            b.append(f("%s %.1f m/s, Luft %.0f °C, Fläche %.1f °C: ζ(10 m) %+.3f, u* %.3f m/s, H %+.1f W/m², LE %+.1f W/m² (%d Durchläufe); ", wasser ? "Wasser" : "Land",
                    fa[0], fa[1], fa[2], 10 * s.invL, s.ustern, hf, le, s.durchlaeufe));
        }
        A.hoechstens("Prüfstein 4: u*, fühlbare und latente Wärme gegen die unabhängige Rechnung, größte Abweichung über 6 Fälle", groesstF * 100, 1.0, "%");
        A.hoechstens("Prüfstein 4: Profile von Wind und Temperatur in 0,1–30 m, größte Abweichung", groesstP * 100, 1.0, "%");
        A.info("Fälle", b.toString());
        // die Zelle rechnet je Schritt nur wenige Durchläufe und nimmt 1/L aus dem vorigen Schritt mit: nach einigen Schritten steht sie
        Atmos at = new Atmos(1, 1, 1);
        at.mitBoden = false;
        at.h[0] = 1.0;
        double ta = 280.15, qa = feuchte(0.8, ta, p);
        at.wetter(3, ta, qa, p, 0, 300, 285.15);
        at.anfang(285.15);
        StringBuilder bz = new StringBuilder();
        for (int i = 1; i <= 6; i++) {
            at.schritt(300);
            if (i <= 3 || i == 6) bz.append(f("Schritt %d: H %.2f, LE %.2f W/m²; ", i, at.hf[0], at.le[0]));
        }
        Frei fr = frei(3, 10, ta + Luft.GAMMA * 2, 2, qa, 285.15, Luft.feuchteSatt(285.15, p), 1e-3, true, 2000);
        double rho = Luft.dichte(ta, qa, p), hfF = -rho * Luft.CP * fr.us * fr.ts, leF = -rho * Luft.verdampfung(ta) * fr.us * fr.qs;
        A.hoechstens("Prüfstein 4: Flüsse der Zelle nach 6 Schritten (Wasser 5 K wärmer als die Luft, 3 m/s) gegen die unabhängige Rechnung",
                Math.max(rel(at.hf[0], hfF), rel(at.le[0], leF)) * 100, 1.0, "%");
        A.info("Zelle", bz + f("unabhängig H %.2f, LE %.2f W/m²", hfF, leF));
        Profil pr = at.profile[0];
        StringBuilder bp = new StringBuilder();
        for (int k : new int[] {0, 1, 8, 16, 24, 32, 36, 41, 47, 55, 63})
            bp.append(f("%.2f m: %.2f °C, (n − 1) · 10⁶ = %.3f; ", Profil.hoehe(k), pr.t[k] - 273.15, pr.r[k] * 1e6));
        A.info("Profil dazu", bp.toString());
    }

    // ------------------------------------------------------------------ 5 Energiebilanz

    /** Wärme im Boden und im Wasser (J/m², Summe über die Zellen), hier noch einmal aus dem Zustand gerechnet. */
    static double bestand(Atmos at) {
        double e = 0;
        for (int c = 0; c < at.nc; c++) {
            for (int k = 0; k < Atmos.SCHICHTEN; k++) e += at.kapaz * at.dicke[k] * at.tb[Atmos.SCHICHTEN * c + k];
            if (at.h[c] >= at.hNass) e += Luft.RHO_W * Luft.C_W * at.h[c] * at.tw[c];
        }
        return e;
    }

    /** Ein Tag mit Sonne, Wind und zwei Tiden über sechs Zellen; gibt {Rest, Bezug, Austausch mit dem Meer, begrenzt} zurück. */
    static double[] tag(boolean mitTief, StringBuilder b) {
        Atmos at = new Atmos(6, 1, 1);
        double p = 101500, dt = 300;
        at.z0[1] = 1e-2;
        at.wetter(4, 283.15, feuchte(0.7, 283.15, p), p, 0, 310, 285.15);
        at.anfang(283.15);
        at.wet[1] = 0.0005;                                // fast trocken: Die Verdunstung endet am Vorrat
        double e0 = bestand(at), bezug = 0;
        for (int i = 1; i <= 288; i++) {
            double t = i * dt, tag = 2 * Math.PI * t / 86400, tide = Math.sin(2 * Math.PI * t / 44714);
            double ta = 283.15 + 4 * Math.sin(tag - 2.4);
            Arrays.fill(at.t2, ta);
            Arrays.fill(at.q2, feuchte(0.7, ta, p));
            Arrays.fill(at.wind, 5 + 3 * Math.sin(tag + 1));
            Arrays.fill(at.sonne, Math.max(0, 500 * Math.sin(tag - Math.PI / 2)));
            Arrays.fill(at.gegen, 310 + 20 * Math.sin(tag - 2));
            at.h[2] = Math.max(0, 0.25 * tide);                 // flach überflutet, fällt trocken
            at.h[3] = mitTief ? Math.max(0, 1.2 * tide) : Math.max(0, 0.1 * tide + 0.05);
            at.h[4] = mitTief ? 2.0 + tide : 0.02;              // immer tief; sonst ein dünner Film
            at.h[5] = 0.05;                                     // stehendes flaches Wasser
            at.schritt(dt);
            for (int c = 0; c < at.nc; c++) bezug += Math.abs(at.rn[c]) * dt;
        }
        double rest = bestand(at) - e0 - (at.eStrahlung - at.eFuehlbar - at.eLatent + at.eZufluss + at.eMeer);
        if (b != null)
            b.append(f("Strahlung %+.2f, fühlbar %+.2f, latent %+.2f, Zufluss %+.1f, Meer %+.1f MJ/m² (Summe über 6 Zellen); Oberfläche am Ende %.1f/%.1f/%.1f/%.1f/%.1f/%.1f °C; "
                            + "Nässe der beiden Landzellen %.2f, %.2f", at.eStrahlung * 1e-6, at.eFuehlbar * 1e-6, at.eLatent * 1e-6, at.eZufluss * 1e-6, at.eMeer * 1e-6,
                    at.ts[0] - 273.15, at.ts[1] - 273.15, at.ts[2] - 273.15, at.ts[3] - 273.15, at.ts[4] - 273.15, at.ts[5] - 273.15, at.wet[0], at.wet[1]));
        return new double[] {rest, bezug, at.eMeer, at.begrenzt};
    }

    static void bilanz() {
        A.teil("5 Schließung der Energiebilanz über einen Tag");
        StringBuilder b = new StringBuilder(), bt = new StringBuilder();
        double[] r = tag(false, b);
        A.hoechstens("Prüfstein 5: Bestand − (Strahlung − fühlbar − latent + Zufluss) über einen Tag, Land und flaches Wasser, relativ zum Umsatz der Strahlung", r[0] / r[1], 1e-6,
                "");
        A.pruefe(r[2] == 0 && r[3] > 0, "Prüfstein 5: ohne tiefes Wasser kein Austausch mit dem Meer; die Verdunstung endete am Vorrat", f("Meer %.1f J/m², begrenzt in %.0f Schritten", r[2], r[3]),
                "0; > 0");
        A.info("Posten", b.toString());
        double[] rt = tag(true, bt);
        A.hoechstens("Prüfstein 5: dasselbe mit tiefem Wasser (dort schließt der Austausch mit dem Meer die Zelle)", rt[0] / rt[1], 1e-6, "");
        A.info("Posten mit tiefem Wasser", bt.toString());
    }

    // ------------------------------------------------------------------ 6 Trocknung

    /** Verdunstung (kg/(m² s)) im Gleichgewicht der Oberfläche bei der Nässe w, unabhängig gerechnet. */
    static double verdunstung(Atmos at, double w, double u, double ta, double qa, double p, double sonne, double gegen) {
        double alb = at.albedoNass + (at.albedoTrocken - at.albedoNass) * (1 - w), rs = at.widerstand(w), thA = ta + Luft.GAMMA * 2;
        double rho = Luft.dichte(ta, qa, p), lv = Luft.verdampfung(ta), lo = ta - 30, hi = ta + 60, verd = 0;
        for (int i = 0; i < 50; i++) {
            double t = 0.5 * (lo + hi), qs = Luft.feuchteSatt(t, p), q0 = qs, ra = 50;
            Frei fr = null;
            for (int j = 0; j < 12; j++) {                       // die Feuchte an der Oberfläche liegt hinter dem Widerstand r_s
                q0 = qa + (qs - qa) * ra / (ra + rs);
                fr = frei(u, 10, thA, 2, qa, t, q0, at.z0[0], false, 200);
                ra = (thA - t) / (fr.us * fr.ts);
            }
            double hf = -rho * Luft.CP * fr.us * fr.ts;
            verd = -rho * fr.us * fr.qs;
            double rest = (1 - alb) * sonne + at.emission * (gegen - Luft.SIGMA * t * t * t * t) - hf - lv * verd;
            if (rest > 0) lo = t;
            else hi = t;
        }
        return verd;
    }

    static void trocknung() {
        A.teil("6 Trocknung");
        Atmos at = new Atmos(1, 1, 1);
        at.mitBoden = false;                                  // festes Wetter, Gleichgewicht der Oberfläche
        double p = 101300, ta = 288.15, qa = feuchte(0.6, ta, p), u = 5, sonne = 400, gegen = 330, dt = 300;
        at.wetter(u, ta, qa, p, sonne, gegen, ta);
        at.anfang(ta);
        double tHalb = Double.NaN, vor = 1;
        int i = 0;
        while (i < 100_000) {
            at.schritt(dt);
            i++;
            if (at.wet[0] < 0.5) {
                tHalb = (i - 1 + (vor - 0.5) / (vor - at.wet[0])) * dt;
                break;
            }
            vor = at.wet[0];
        }
        // Handrechnung: t = W_max ∫ dw / E(w) von ½ bis 1, nach Simpson über 5 Stellen
        double[] w = {0.5, 0.625, 0.75, 0.875, 1}, g = {1, 4, 2, 4, 1};
        double sum = 0;
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < 5; k++) {
            double e = verdunstung(at, w[k], u, ta, qa, p, sonne, gegen);
            sum += g[k] / e;
            b.append(f("w %.3f: %.3f mm/h; ", w[k], e * 3600));
        }
        double soll = at.vorrat() * sum * 0.125 / 3;
        A.hoechstens("Prüfstein 6: Zeit, bis die Nässe unter ½ fällt (15 °C, 60 %, 5 m/s, 400 W/m² Sonne), gegen die Handrechnung aus dem Verdunstungsstrom", (tHalb / soll - 1) * 100,
                5.0, "%");
        A.info("Zeit", f("%.2f h im Modell, %.2f h von Hand; Vorrat der obersten Schicht %.1f kg/m²", tHalb / 3600, soll / 3600, at.vorrat()));
        A.info("Verdunstung im Gleichgewicht", b.toString());
        // Oktober zur Auskunft: 10 °C, 80 %, 4 m/s, 100 W/m² Sonne
        Atmos ok = new Atmos(1, 1, 1);
        ok.mitBoden = false;
        ok.wetter(4, 283.15, feuchte(0.8, 283.15, p), p, 100, 310, 283.15);
        ok.anfang(283.15);
        int j = 0;
        while (j < 100_000 && ok.wet[0] >= 0.5) {
            ok.schritt(dt);
            j++;
        }
        A.info("Zur Auskunft, Oktobertag (10 °C, 80 %, 4 m/s, 100 W/m² Sonne)", f("unter ½ nach %.1f h", j * dt / 3600));
    }

    // ------------------------------------------------------------------ 7 flaches Wasser

    static void wasser() {
        A.teil("7 Flaches Wasser auf dem Watt");
        Atmos at = new Atmos(1, 1, 1);
        at.mitTurbulenz = false;
        at.mitBoden = false;
        double t0 = 283.15, sonne = 400, gegen = 320, tiefe = 0.05, dt = 300;
        at.h[0] = tiefe;
        at.wetter(0, t0, 0.005, 101325, sonne, gegen, t0);
        at.anfang(t0);
        for (int i = 0; i < 24; i++) at.schritt(dt);
        // unabhängig: C dT/dt = (1 − α) S + ε (L − σ T⁴), Runge-Kutta 4 mit 1 s
        double c = Luft.RHO_W * Luft.C_W * tiefe, t = t0, a = (1 - at.albedoWasser) * sonne + at.emission * gegen, e = at.emission * Luft.SIGMA;
        for (int i = 0; i < 7200; i++) {
            double k1 = (a - e * Math.pow(t, 4)) / c, k2 = (a - e * Math.pow(t + 0.5 * k1, 4)) / c, k3 = (a - e * Math.pow(t + 0.5 * k2, 4)) / c, k4 = (a - e * Math.pow(t + k3, 4)) / c;
            t += (k1 + 2 * k2 + 2 * k3 + k4) / 6;
        }
        A.hoechstens("Prüfstein 7: Erwärmung von 5 cm Wasser in 2 h mit der Nettostrahlung, gegen die unabhängige Rechnung", ((at.tw[0] - t0) / (t - t0) - 1) * 100, 2.0, "%");
        A.info("Werte", f("%.3f K im Modell, %.3f K unabhängig (400 W/m² Sonne, 320 W/m² Gegenstrahlung, Beginn 10 °C)", at.tw[0] - t0, t - t0));
    }

    // ------------------------------------------------------------------ 8 Brechzahl

    /** NIST, Engineering Metrology Toolbox, Tabelle 1 (Ciddor, 450 µmol/mol CO₂): t (°C), relative Feuchte (%), p (Pa), λ (nm), n. */
    static final double[][] NIST = {
            {20, 0, 101325, 633, 1.000271800}, {20, 0, 60000, 633, 1.000160924}, {20, 0, 120000, 633, 1.000321916}, {50, 0, 100000, 633, 1.000243285},
            {5, 0, 100000, 633, 1.000282756}, {-40, 0, 100000, 633, 1.000337580}, {50, 100, 120000, 633, 1.000287924}, {40, 75, 120000, 633, 1.000299418},
            {20, 100, 100000, 633, 1.000267394}, {40, 100, 110000, 1700, 1.000270247}, {20, 0, 101325, 1700, 1.000268479}, {40, 100, 110000, 300, 1.000289000},
            {20, 0, 101325, 300, 1.000286581}, {-40, 0, 120000, 300, 1.000427233}};

    static void ciddor() {
        A.teil("8 Brechzahl nach Ciddor (1996)");
        double groesst = 0;
        for (double[] r : NIST) {
            double T = r[0] + 273.15;
            groesst = Math.max(groesst, Math.abs(Luft.brechzahl(r[3] / 1000, T, r[2], Luft.molanteil(r[1] / 100, T, r[2]), 450) - r[4]));
        }
        A.hoechstens("Prüfstein 8: Brechzahl gegen die 14 Prüfwerte des NIST (−40 bis 50 °C, 60–120 kPa, 0–100 %, 300–1700 nm), größte Abweichung", groesst, 1e-8, "");
        double[][] satt = {{100, 101418}, {50, 12351}, {40, 7384}, {20, 2339}};
        double gs = 0;
        for (double[] s : satt) gs = Math.max(gs, rel(Luft.saettigung(s[0] + 273.15), s[1]));
        A.hoechstens("Sättigungsdampfdruck über Wasser gegen Tabelle 2 dort (20–100 °C)", gs * 100, 0.01, "%");
        double n10 = Luft.brechung(0.55, 283.15, 101325, 0, 430);
        A.info("Für das Bild", f("(n − 1) · 10⁶ bei 10 °C, 1013,25 hPa, trocken, 550 nm: %.3f; je Kelvin %.3f, je 10 %% Feuchte %.3f", n10 * 1e6,
                (Luft.brechung(0.55, 284.15, 101325, 0, 430) - n10) * 1e6, (Luft.brechung(0.55, 283.15, 101325, Luft.molanteil(0.1, 283.15, 101325), 430) - n10) * 1e6));
    }

    // ------------------------------------------------------------------ 14 Entwässerung (P10b, F92 A)

    /** Sättigung im Gleichgewicht über Grundwasser in der Tiefe t (m), hier noch einmal: van Genuchten mit den Werten von {@link Atmos}. */
    static double gleichgewicht(Atmos at, double t) {
        double h = t + 0.5 * at.dicke[0], m = 1 - 1 / at.vgN;
        return at.sRest + (1 - at.sRest) / Math.pow(1 + Math.pow(at.vgAlpha * h, at.vgN), m);
    }

    static Atmos sand(boolean verdunstung, double p) {
        Atmos at = new Atmos(1, 1, 1);
        at.mitEntwaesserung = true;
        at.mitVerdunstung = verdunstung;
        at.wetter(5, 288.15, feuchte(0.6, 288.15, p), p, 400, 330, 288.15);
        at.anfang(288.15);
        return at;
    }

    static void entwaesserung() {
        A.teil("14 Entwässerung (P10b, Prüfstein 8 ohne Gebiet)");
        double p = 101300, groesst = 0;
        StringBuilder b = new StringBuilder();
        // (a) Grundwasser fest in 5 cm bis 2 m Tiefe: Die oberste Schicht stellt sich auf das Gleichgewicht ein
        for (double tiefe : new double[] {0.05, 0.2, 0.5, 1.0, 2.0}) {
            Atmos at = sand(false, p);
            at.ueber[0] = tiefe;
            at.grundwasser[0] = tiefe;
            for (int i = 0; i < 288 * 30; i++) at.schritt(300);
            double soll = gleichgewicht(at, tiefe);
            if (tiefe <= 1.0) groesst = Math.max(groesst, rel(at.wet[0], soll));
            b.append(f("%.2f m: %.3f (Soll %.3f); ", tiefe, at.wet[0], soll));
        }
        A.hoechstens("Sättigung der obersten Schicht über festem Grundwasser nach 30 Tagen gegen die Haltekurve (van Genuchten), größte Abweichung über die Tiefen bis 1 m", groesst * 100, 1.0, "%");
        A.info("Gleichgewicht (über 2 m Grundwasser ist der Sand nach 30 Tagen noch nicht so trocken wie die Haltekurve: so trocken leitet er kaum noch)", b.toString());
        // (b) nach dem Trockenfallen sinkt das Grundwasser mit 2 cm je Stunde
        Atmos at = sand(false, p);
        StringBuilder bz = new StringBuilder();
        double gw = 0;
        for (int i = 1; i <= 72; i++) {
            at.schritt(300);
            if (i % 12 == 0) bz.append(f("%d h: Grundwasser %.3f m, Nässe %.3f; ", i / 12, at.grundwasser[0], at.wet[0]));
            gw = at.grundwasser[0];
        }
        A.hoechstens("Grundwasser 6 h nach dem Trockenfallen gegen 2 cm je Stunde", (gw / 0.12 - 1) * 100, 0.01, "%");
        A.hoechstens("Nässe dann gegen das Gleichgewicht über 12 cm", (at.wet[0] / gleichgewicht(at, 0.12) - 1) * 100, 1.0, "%");
        A.info("Verlauf ohne Verdunstung", bz.toString());
        // (c) mit Verdunstung (Sommerfall aus Prüfstein 6): nah am Grundwasser hält der Nachschub die Oberfläche nass, hoher Sand trocknet
        StringBuilder bv = new StringBuilder();
        double nah = 0, hoch = 0;
        for (double tiefe : new double[] {0.1, 0.3, 0.6, 1.2}) {
            Atmos v = sand(true, p);
            v.mitBoden = false;
            v.ueber[0] = tiefe;
            v.grundwasser[0] = tiefe;
            for (int i = 0; i < 288; i++) v.schritt(300);
            bv.append(f("Grundwasser in %.1f m: Nässe %.2f, Verdunstung %.3f mm/h; ", tiefe, v.wet[0], v.le[0] / Luft.verdampfung(288.15) * 3600));
            if (tiefe == 0.1) nah = v.wet[0];
            if (tiefe == 1.2) hoch = v.wet[0];
        }
        A.pruefe(nah > 0.9 && hoch < 0.2, "Nach einem Tag mit Verdunstung (15 °C, 5 m/s, 400 W/m²): 10 cm über dem Grundwasser nass, 1,2 m darüber trocken", f("%.2f und %.2f", nah, hoch), "> 0,9; < 0,2 [G]");
        A.info("Mit Verdunstung nach einem Tag", bv.toString());
    }

    // ------------------------------------------------------------------ 13 Rechenzeit

    static void zeiten() {
        A.teil("13 Rechenzeit");
        int nx = 700, ny = 400;
        Atmos at = new Atmos(nx, ny, 10);
        double p = 101500;
        java.util.Random zufall = new java.util.Random(4711);
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                double x = Math.sin(i * 0.013) + Math.cos(j * 0.021) + 0.3 * zufall.nextDouble();        // Rinnen, Watt und Sände
                at.h[i + nx * j] = x > 0.9 ? 3 * (x - 0.9) : x > 0.6 ? 0.2 * (x - 0.6) : 0;
                at.z0[i + nx * j] = 5e-4 + 2e-3 * zufall.nextDouble();
            }
        at.wetter(5, 283.15, feuchte(0.75, 283.15, p), p, 250, 320, 285.15);
        for (int s = 0; s < at.ns; s++) at.wind[s] = 2 + 6 * zufall.nextDouble();
        at.anfang(283.15);
        for (int i = 0; i < 4; i++) at.schritt(300);
        int n = 10;
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) at.schritt(300);
        double je = (System.nanoTime() - t0) * 1e-9 / n;
        at.parallel = false;
        long t1 = System.nanoTime();
        for (int i = 0; i < 3; i++) at.schritt(300);
        double einzeln = (System.nanoTime() - t1) * 1e-9 / 3;
        int nass = 0;
        for (double x : at.h) if (x >= at.hNass) nass++;
        A.hoechstens(f("Prüfstein 13: ein Kopplungsintervall für %d Zellen und %d Säulen auf %d Kernen", at.nc, at.ns, Runtime.getRuntime().availableProcessors()), je, 0.5, "s");
        A.info("Zeiten", f("%.3f s je Schritt (%.0f ns je Zelle); auf einem Faden %.3f s; %d %% der Zellen unter Wasser", je, je / at.nc * 1e9, einzeln, 100 * nass / at.nc));
    }
}
