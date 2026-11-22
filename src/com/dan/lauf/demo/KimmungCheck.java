package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Erde;
import com.dan.physics.atmos.Atmos;
import com.dan.physics.atmos.Luft;
import com.dan.physics.atmos.Profil;
import com.dan.render.reference.Strahlgleichung;

import java.nio.file.Path;

import static com.dan.core.pruef.Attest.f;

/**
 * Lichtstrahlen durch das Profil von atmos (P10a, Werkbuch HalligHooge Teil A, 12.11, Prüfsteine 9–11): Die
 * Strahlgleichung der Referenz ({@link Strahlgleichung}, Runge-Kutta 4) läuft durch die Brechzahl aus
 * {@link Profil}. Das steht hier und nicht bei atmos, weil ein Physikmodul die Grafik nicht kennt.
 * <ul>
 * <li><b>Prüfstein 9:</b> Im linearen Gefälle der Brechzahl ist der Strahl ein Kreisbogen mit dem Radius n / |dn/dz|
 * (waagerecht gestartet, Radius aus dem Durchhang über 2 km); dazu über 20 km gegen die genaue Lösung, die
 * Kettenlinie.</li>
 * <li><b>Prüfstein 10:</b> Fällt die Temperatur um 3,42 K je 100 m, ist die Dichte mit der Höhe gleich und der Strahl
 * gerade: Krümmung höchstens 1 % der Erdkrümmung [G]. Profil aus Temperatur, hydrostatischem Druck und Ciddor.</li>
 * <li><b>Prüfstein 11:</b> Untere Spiegelung im gerechneten Profil (Wasser 5 K wärmer als die Luft, Wind 3 m/s) über
 * der Kugel: Es gibt einen Sehwinkel unter der Waagerechten, bis zu dem Strahlen vor der Fläche nach oben umkehren.
 * Seine Lage aus der Strahlgleichung (Halbieren) gegen die Erhaltungsgröße n (R + z) cos β der geschichteten Kugel;
 * Grenze ±5 % [G]. Augenhöhe 4 m [G].</li>
 * </ul>
 * Attest {@code docs/status/P10a_Kimmung.md}.
 */
public final class KimmungCheck {
    private static final Attest A = new Attest("com.dan.lauf (Kimmung)", Atmos.VERSION);
    static final double R = Erde.R;

    public static void main(String[] args) {
        try {
            kreisbogen();
            gerade();
            spiegelung();
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "P10a_Kimmung.md"), "P10a – KimmungCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Die Luft über einer Ebene: Höhe ist y. */
    static Strahlgleichung eben(Profil pr) {
        return new Strahlgleichung(new Strahlgleichung.Brechzahl() {
            @Override public double n(double[] r) { return pr.n(r[1]); }

            @Override public double[] gradient(double[] r) { return new double[] {0, pr.gefaelle(r[1]), 0}; }
        });
    }

    /** Die Luft über der Kugel um den Ursprung: Höhe ist der Abstand vom Mittelpunkt weniger R. */
    static Strahlgleichung kugel(Profil pr) {
        return new Strahlgleichung(new Strahlgleichung.Brechzahl() {
            @Override public double n(double[] r) { return pr.n(Math.hypot(r[0], r[1]) - R); }

            @Override public double[] gradient(double[] r) {
                double rr = Math.hypot(r[0], r[1]), g = pr.gefaelle(rr - R);
                return new double[] {g * r[0] / rr, g * r[1] / rr, 0};
            }
        });
    }

    static double asinh(double x) { return Math.log(x + Math.sqrt(x * x + 1)); }

    // ------------------------------------------------------------------ 9 Kreisbogen

    static void kreisbogen() {
        A.teil("9 Strahl im linearen Gefälle der Brechzahl");
        double n0 = 1.000280, g = -Erde.REFRAKTION * n0 / R;
        Profil pr = Profil.linear(n0, g);
        Strahlgleichung sg = eben(pr);
        // (a) waagerecht in 50 m Höhe: Radius aus dem Durchhang
        double[] z = sg.start(new double[] {0, 50, 0}, new double[] {1, 0, 0});
        for (int i = 0; i < 200; i++) z = sg.schritt(z, 10);
        double hang = 50 - z[1], radius = (z[0] * z[0] + hang * hang) / (2 * hang), soll = pr.n(50) / Math.abs(pr.gefaelle(50));
        A.hoechstens("Prüfstein 9: Radius des Strahls (waagerecht, Durchhang über 2 km) gegen n / |dn/dz|, relativ", radius / soll - 1, 1e-6, "");
        A.info("Werte", f("Radius %.1f km gegen %.1f km (dn/dz = %.3e je m, k = 0,13); Durchhang %.6f m", radius * 1e-3, soll * 1e-3, g, hang));
        // (b) 0,1° steigend aus 2 m über 20 km gegen die Kettenlinie n0 + g y = c cosh(g (x − x0) / c)
        double th0 = Math.toRadians(0.1), y0 = 2, nA = n0 + g * y0;
        z = sg.start(new double[] {0, y0, 0}, new double[] {Math.cos(th0), Math.sin(th0), 0});
        for (int i = 0; i < 2000; i++) z = sg.schritt(z, 10);
        double c = nA * Math.cos(th0), x0 = -(c / g) * asinh(Math.tan(th0));
        double ySoll = (c * Math.cosh(g * (z[0] - x0) / c) - n0) / g;
        A.hoechstens("Prüfstein 9: Höhe nach 20 km (0,1° steigend aus 2 m) gegen die Kettenlinie, relativ zur Länge", (z[1] - ySoll) / 20_000, 1e-6, "");
        A.info("Werte", f("%.6f m gegen %.6f m", z[1], ySoll));
    }

    // ------------------------------------------------------------------ 10 gerader Strahl

    /** Krümmung eines waagerecht in 10 m Höhe gestarteten Strahls über 5 km (1/m, nach unten positiv). */
    static double kruemmung(Profil pr) {
        Strahlgleichung sg = eben(pr);
        double[] z = sg.start(new double[] {0, 10, 0}, new double[] {1, 0, 0});
        for (int i = 0; i < 500; i++) z = sg.schritt(z, 10);
        return 2 * (10 - z[1]) / (z[0] * z[0]);
    }

    static void gerade() {
        A.teil("10 Gleiche Dichte mit der Höhe");
        Profil pr = Profil.ausGefaelle(283.15, -0.0342, 101325, 0.55, 430);
        double k = kruemmung(pr) * R, groesst = 0;
        for (int i = 1; i < Profil.STUFEN; i++) groesst = Math.max(groesst, Math.abs(pr.gefaelle(0.5 * (Profil.hoehe(i - 1) + Profil.hoehe(i)))));
        A.hoechstens("Prüfstein 10: Krümmung des Strahls bei −3,42 K je 100 m, in Teilen der Erdkrümmung", k, 0.01, "");
        A.hoechstens("Prüfstein 10: größtes Gefälle der Brechzahl in der Tabelle (0–100 m), ebenso", groesst * R, 0.01, "");
        A.info("Zur Auskunft", f("genau −g/R_d = %.3f K je 100 m: %.1e; Normalatmosphäre (−0,65 K je 100 m, 10 °C): k = %.3f; isotherm: k = %.3f; Inversion +3 K je 100 m: k = %.3f",
                -Luft.G / Luft.RD * 100, kruemmung(Profil.ausGefaelle(283.15, -Luft.G / Luft.RD, 101325, 0.55, 430)) * R,
                kruemmung(Profil.ausGefaelle(283.15, -0.0065, 101325, 0.55, 430)) * R, kruemmung(Profil.ausGefaelle(283.15, 0, 101325, 0.55, 430)) * R,
                kruemmung(Profil.ausGefaelle(283.15, 0.03, 101325, 0.55, 430)) * R));
    }

    // ------------------------------------------------------------------ 11 untere Spiegelung

    /** Ein Strahl vom Auge: kleinste Höhe über der Fläche (m; ≤ 0: er trifft sie) und der Bogen auf der Erde bis dorthin (m). */
    static double[] strahl(Strahlgleichung sg, double auge, double beta) {
        double[] z = sg.start(new double[] {0, R + auge, 0}, new double[] {Math.cos(beta), -Math.sin(beta), 0});
        double hoehe = auge;
        for (int i = 0; i < 2_000_000; i++) {
            double[] nz = sg.schritt(z, hoehe < 1 ? 0.25 : 1.0);
            double rr = Math.hypot(nz[0], nz[1]), h = rr - R;
            if (h <= 0) return new double[] {h, R * Math.atan2(nz[0], nz[1])};
            if (nz[3] * nz[0] + nz[4] * nz[1] > 0) return new double[] {Math.min(h, hoehe), R * Math.atan2(nz[0], nz[1])};       // er steigt wieder
            z = nz;
            hoehe = h;
        }
        throw new IllegalStateException("Strahl endet nicht");
    }

    /** Grenzwinkel unter der Waagerechten (rad): flacher kehrt der Strahl vor der Fläche um, steiler trifft er sie. */
    static double grenzwinkel(Strahlgleichung sg, double auge) {
        double lo = 0, hi = Math.toRadians(1);
        for (int i = 0; i < 40; i++) {
            double m = 0.5 * (lo + hi);
            if (strahl(sg, auge, m)[0] <= 0) hi = m;
            else lo = m;
        }
        return 0.5 * (lo + hi);
    }

    /** Dasselbe aus der Erhaltungsgröße der geschichteten Kugel: cos β = min n (R + z) / (n_A (R + z_A)) über unten ≤ z ≤ z_A. */
    static double grenzwinkelFrei(Profil pr, double auge, double unten) {
        double mA = pr.n(auge) * (R + auge), klein = Double.MAX_VALUE;
        for (int i = 0; i <= 400_000; i++) {
            double z = unten + (auge - unten) * i / 400_000.0;
            klein = Math.min(klein, pr.n(z) * (R + z));
        }
        // 1 − cos β = (m_A − m_min) / m_A, ohne die Auslöschung von acos nahe 1
        return 2 * Math.asin(Math.sqrt(Math.max(0, (mA - klein) / mA) / 2));
    }

    static Profil profil(double wind, double tLuft, double tWasser, double rf) {
        Atmos at = new Atmos(10, 10, 10);
        double p = 101500;
        java.util.Arrays.fill(at.h, 5);
        at.wetter(wind, tLuft, Luft.feuchte(rf * Luft.saettigung(tLuft), p), p, 0, 300, tWasser);
        at.anfang(tWasser);
        for (int i = 0; i < 8; i++) at.schritt(300);
        return at.profile[0];
    }

    static void spiegelung() {
        A.teil("11 Untere Spiegelung über warmem Wasser");
        double auge = 4, min = 60 * 180 / Math.PI;
        Profil pr = profil(3, 280.15, 285.15, 0.8);
        double bs = grenzwinkel(kugel(pr), auge), bf = grenzwinkelFrei(pr, auge, 0);
        A.hoechstens("Prüfstein 11: Grenzwinkel aus der Strahlgleichung gegen die Erhaltungsgröße (Wasser 12 °C, Luft 7 °C, 3 m/s, Auge 4 m)", (bs / bf - 1) * 100, 5.0, "%");
        Profil neutral = profil(3, 280.15, 280.15, 0.8);
        double kimm = grenzwinkelFrei(neutral, auge, 0), geo = Math.sqrt(2 * auge / R);
        A.pruefe(bs > 1.2 * kimm, "Prüfstein 11: Der Grenzwinkel liegt deutlich unter der Kimm derselben Luft ohne Temperaturunterschied", f("%.2f′ gegen %.2f′", bs * min, kimm * min),
                "über dem 1,2-Fachen [G]");
        A.info("Winkel unter der Waagerechten", f("Strahlgleichung %.3f′, Erhaltungsgröße %.3f′; Kimm ohne Temperaturunterschied %.2f′ (Strahlgleichung %.2f′), ohne Luft %.2f′", bs * min,
                bf * min, kimm * min, grenzwinkel(kugel(neutral), auge) * min, geo * min));
        StringBuilder b = new StringBuilder();
        for (double an : new double[] {0.999, 0.9, 0.7, 0.5}) {
            double[] s = strahl(kugel(pr), auge, an * bs);
            b.append(f("%.0f %% des Grenzwinkels: kehrt in %.3f m Höhe um, %.2f km vom Auge; ", an * 100, s[0], s[1] * 1e-3));
        }
        A.info("Strahlen flacher als der Grenzwinkel", b.toString());
        StringBuilder bh = new StringBuilder();
        for (double u : new double[] {0.01, 0.05, 0.2, 0.5}) bh.append(f("ab %.2f m: %.2f′; ", u, grenzwinkelFrei(pr, auge, u) * min));
        A.info("Grenzwinkel, wenn die Fläche Strahlen schon höher abfängt (Wellen)", bh.toString());
        StringBuilder bt = new StringBuilder();
        for (double dT : new double[] {1, 2, 5, 8}) bt.append(f("%+.0f K: %.2f′; ", dT, grenzwinkelFrei(profil(3, 280.15, 280.15 + dT, 0.8), auge, 0) * min));
        for (double dT : new double[] {-2, -5}) bt.append(f("%+.0f K: %.2f′; ", dT, grenzwinkelFrei(profil(3, 280.15, 280.15 + dT, 0.8), auge, 0) * min));
        A.info("Grenzwinkel nach dem Unterschied Wasser − Luft (3 m/s; 0 heißt: auch der waagerechte Strahl krümmt sich zur Fläche, die Kimm liegt nicht mehr unter der Waagerechten)",
                bt.toString());
        A.befund("Der Grenzstrahl streift die Fläche: Die Hälfte des Temperaturunterschieds liegt im untersten Zentimeter. Über welcher Höhe das Bild die Fläche als undurchsichtig nimmt "
                + "(Wellen), entscheidet P10b; die Zahlen dazu stehen oben.");
    }
}
