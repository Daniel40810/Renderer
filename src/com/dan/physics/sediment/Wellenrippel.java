package com.dan.physics.sediment;

/**
 * Wellenrippel (S6d-1, SF57 A, Werkbuch Seegang 64/84): Gleichgewicht von Länge und Höhe der Rippel unter der Welle nach
 * Wiberg und Harris (1994), Grenzen der Klassen nach Malarkey und Davies (2003), dazu die Shields-Werte von Welle und
 * Strom und die Regel, wann die Welle die Rippel formt. Reine Formeln in double, auch für die Prüfung.
 * <ul>
 * <li><b>Orbitalgeschwindigkeit am Boden</b> aus der signifikanten Welle nach linearer Theorie:
 * U_w = π H_s / (T_p sinh k h), k aus der Dispersion bei T_p. Für ein schmales Spektrum ist das 2 · {@code waves.ubot}
 * (quadratisches Mittel).</li>
 * <li><b>Orbitaldurchmesser</b> d₀ = U_w T_p / π.</li>
 * <li><b>Länge:</b> orbital (d₀/D50 &lt; 1754) λ = 0,62 d₀; anorbital (d₀/D50 &gt; 5587) λ = 535 D50; dazwischen
 * logarithmisch in d₀/D50.</li>
 * <li><b>Höhe:</b> orbital η = 0,17 λ; sonst aus der Steilheit η/λ = exp[−0,095 (ln d₀/η)² + 0,442 ln d₀/η − 2,28],
 * geschlossen gelöst (quadratisch in ln η, die Wurzel mit d(η/λ)/dη &gt; 0). An der Grenze orbital/suborbital gibt sie
 * 0,171 λ: die Höhe springt dort um weniger als 1 %.</li>
 * <li><b>Shields-Werte:</b> Welle θ_w = ½ f_w U_w² / ((s − 1) g D50), f_w nach Soulsby (1997) auf rauem Boden mit
 * z₀ = 2,5 D50 / 30; Strom θ_c = c_D U² / ((s − 1) g D50), c_D nach Soulsby (1997).</li>
 * <li><b>Regel</b> [G]: θ_w ≥ 0,8 (Schichtfluss) wäscht die Rippel weg; bewegt weder Welle noch Strom das Korn
 * (beide ≤ θ_cr), bleiben die alten; herrscht die Welle (θ_w &gt; θ_c), formt sie die Rippel im Gleichgewicht; sonst
 * formt der Strom (seine Rippel zeigt {@code bed.ripple}), die Wellenrippel sind weg.</li>
 * </ul>
 */
public final class Wellenrippel {
    private Wellenrippel() { }

    public static final String VERSION = "0.1";
    public static final double G = 9.81, S = 2.65, NU = 1.36e-6;
    /** Grenzen der Klassen in d₀/D50 (Malarkey und Davies 2003). */
    public static final double ORBITAL_BIS = 1754, ANORBITAL_AB = 5587;
    /** Beiwerte nach Wiberg und Harris (1994). */
    public static final double ORBITAL_LAENGE = 0.62, ORBITAL_STEIL = 0.17, ANORBITAL_LAENGE = 535;
    /** Schichtfluss ab θ_w (Wiberg und Harris 1994: Rippel verschwinden um 0,8 … 1). */
    public static final double SCHICHTFLUSS = 0.8;

    /** Zustand einer Zelle nach der Regel. */
    public static final int BLEIBT = 0, FORMT = 1, WEG_SCHICHT = 2, WEG_STROM = 3;

    /** Wellenzahl k (1/m) zur Kreisfrequenz ω (1/s) in der Tiefe h (m): Startwert nach Guo (2002), dann Newton. */
    public static double wellenzahl(double omega, double h) {
        double x = omega * Math.sqrt(h / G);
        double kh = x * x * Math.pow(1 - Math.exp(-Math.pow(x, 2.4908)), -1 / 2.4908);
        double y = omega * omega * h / G;
        for (int n = 0; n < 6; n++) {
            double t = Math.tanh(kh), f = kh * t - y, df = t + kh * (1 - t * t);
            double d = f / df;
            kh -= d;
            if (Math.abs(d) < 1e-14 * kh) break;
        }
        return kh / h;
    }

    /** U_w (m/s): Amplitude der Orbitalgeschwindigkeit am Boden aus H_s (m), T_p (s), Tiefe h (m); 0 ohne Welle. */
    public static double uw(double hs, double tp, double h) {
        if (!(hs > 0) || !(tp > 0) || !(h > 0)) return 0;
        double om = 2 * Math.PI / tp, kh = wellenzahl(om, h) * h;
        if (kh > 20) return 0;
        return Math.PI * hs / (tp * Math.sinh(kh));
    }

    /** d₀ = U_w T / π (m). */
    public static double d0(double uw, double t) { return uw * t / Math.PI; }

    /**
     * Gleichgewicht {Höhe η, Länge λ, Klasse} (m, m, 0 orbital / 1 suborbital / 2 anorbital) beim Orbitaldurchmesser d₀ (m)
     * und Korn d50 (m). Ohne Lösung der Steilheit (Diskriminante &lt; 0) ist η = 0.
     */
    public static double[] geometrie(double d0, double d50) {
        if (!(d0 > 0) || !(d50 > 0)) return new double[] {0, 0, 0};
        double r = d0 / d50, lo = ORBITAL_LAENGE * d0, la = ANORBITAL_LAENGE * d50;
        if (r < ORBITAL_BIS) return new double[] {ORBITAL_STEIL * lo, lo, 0};
        double l;
        int klasse;
        if (r > ANORBITAL_AB) {
            l = la;
            klasse = 2;
        } else {
            double s = (Math.log(r) - Math.log(ORBITAL_BIS)) / (Math.log(ANORBITAL_AB) - Math.log(ORBITAL_BIS));
            l = Math.exp((1 - s) * Math.log(lo) + s * Math.log(la));
            klasse = 1;
        }
        return new double[] {hoeheAusSteilheit(d0, l), l, klasse};
    }

    /**
     * η aus η/λ = exp[−0,095 x² + 0,442 x − 2,28], x = ln(d₀/η): mit y = ln η quadratisch,
     * 0,095 y² + (1,442 − 0,19 L) y + (0,095 L² − 0,442 L + 2,28 − ln λ) = 0, L = ln d₀; die größere Wurzel.
     */
    public static double hoeheAusSteilheit(double d0, double l) {
        double L = Math.log(d0), a = 0.095, b = 1.442 - 0.19 * L, c = 0.095 * L * L - 0.442 * L + 2.28 - Math.log(l);
        double D = b * b - 4 * a * c;
        if (D < 0) return 0;
        return Math.exp((-b + Math.sqrt(D)) / (2 * a));
    }

    /** Steilheit η/λ nach Wiberg und Harris bei d₀/η (für die Prüfung). */
    public static double steilheit(double d0DurchEta) {
        double x = Math.log(d0DurchEta);
        return Math.exp(-0.095 * x * x + 0.442 * x - 2.28);
    }

    /** z₀ = k_s/30 mit k_s = 2,5 D50 (m). */
    public static double z0(double d50) { return 2.5 * d50 / 30; }

    /** θ_w der Welle (U_w, T, D50). */
    public static double thetaWelle(double uw, double t, double d50) {
        if (!(uw > 0)) return 0;
        double a = uw * t / (2 * Math.PI), fw = Sediment.fwRau(a, z0(d50));
        return 0.5 * fw * uw * uw / ((S - 1) * G * d50);
    }

    /** θ_c des Stroms (Betrag des Tiefenmittels U, Tiefe h, D50). */
    public static double thetaStrom(double u, double h, double d50) {
        if (!(u > 0) || !(h > 0)) return 0;
        return Sediment.cdStrom(h, z0(d50)) * u * u / ((S - 1) * G * d50);
    }

    /** θ_cr nach Soulsby und Whitehouse (1997). */
    public static double thetaKrit(double d50) { return Sediment.thetaKrit(Sediment.dStern(d50, S, NU)); }

    /** Die Regel [G]: {@link #BLEIBT}, {@link #FORMT}, {@link #WEG_SCHICHT}, {@link #WEG_STROM}. */
    public static int regel(double thetaW, double thetaC, double thetaCr) {
        if (thetaW >= SCHICHTFLUSS) return WEG_SCHICHT;
        if (Math.max(thetaW, thetaC) <= thetaCr) return BLEIBT;
        return thetaW > thetaC ? FORMT : WEG_STROM;
    }
}
