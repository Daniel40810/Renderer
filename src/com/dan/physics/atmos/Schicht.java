package com.dan.physics.atmos;

/**
 * Die bodennahe Luftschicht nach Monin und Obukhov (Werkbuch HalligHooge Teil A, 7.5 und 12.11, P10a): Flüsse und
 * Profile von Wind, Temperatur und Feuchte aus den Werten in einer Messhöhe und an der Oberfläche.
 * <p>
 * <b>Profile:</b> u(z) = u* / κ [ln(z/z0) − ψ_m(z/L) + ψ_m(z0/L)], θ(z) − θ_0 = θ* / κ [ln(z/z0h) − ψ_h(z/L) + ψ_h(z0h/L)],
 * ebenso q mit q*; Obukhov-Länge L = u*² θ_v / (κ g θ_v*) mit θ_v* = θ* (1 + 0,608 q) + 0,608 θ q*. κ = 0,40.
 * <p>
 * <b>Funktionen</b> nach Businger und Dyer in der Fassung von Dyer und Hicks (1970; Dyer 1974): labil (ζ = z/L &lt; 0)
 * φ_m = (1 − 16 ζ)^−¼, φ_h = (1 − 16 ζ)^−½; stabil φ_m = φ_h = 1 + 5 ζ. Integriert nach Paulson (1970):
 * ψ_m = 2 ln((1 + x)/2) + ln((1 + x²)/2) − 2 arctan x + π/2 mit x = (1 − 16 ζ)^¼, ψ_h = 2 ln((1 + x²)/2); stabil
 * ψ = −5 ζ. (Am 18.11.2026 an Dyer 1974 und an einer zweiten Umsetzung nachgelesen.) Über ζ = 1 hinaus gilt
 * φ = 6 fest, also ψ = −5 (1 + ln ζ) [G]: Die lineare Form ließe die Schichtung sonst beliebig steif werden.
 * <p>
 * <b>Gesetzt [G]:</b> Die Schichtung in der Messhöhe des Winds ist auf ζ ≤ {@link #zetaMax} begrenzt (darüber hat die
 * Gleichung keine Lösung, die Turbulenz bräche ab); der Wind zählt mit mindestens {@link #windMin} (freie Konvektion
 * und Flaute); Rauheit für Wärme und Feuchte über Land z0/10, über Wasser gleich z0; z0 über Wasser nach Charnock mit
 * dem Anteil der glatten Strömung (α u*²/g + 0,11 ν/u*).
 * <p>
 * Ein Objekt trägt das Ergebnis einer Lösung und ist nicht für mehrere Fäden zugleich. Kennt nur java.*.
 */
public final class Schicht {
    /** Größte Schichtung z/L in der Messhöhe des Winds [G]. */
    public double zetaMax = 1.0;
    /** Kleinster wirksamer Wind (m/s) [G]. */
    public double windMin = 0.5;
    /** Charnock-Parameter über Wasser [G]. */
    public double charnock = 0.018;
    /** Verhältnis z0 / z0h über Land [G]. */
    public double z0Verhaeltnis = 10;

    /** Ergebnis: Schubspannungsgeschwindigkeit (m/s), θ* (K), q* (kg/kg), 1/L (1/m), Rauheit für Impuls und für Wärme (m). */
    public double ustern, tstern, qstern, invL, z0, z0h;
    /** Ergebnis: Widerstand der Luft für Wärme und Feuchte zwischen Oberfläche und Messhöhe (s/m). */
    public double ra;
    /** Zahl der Durchläufe der letzten Lösung. */
    public int durchlaeufe;

    /** ψ_m(ζ). */
    public static double psiM(double zeta) {
        if (zeta >= 0) return zeta <= 1 ? -5 * zeta : -5 * (1 + Math.log(zeta));
        double x = Math.sqrt(Math.sqrt(1 - 16 * zeta));
        return 2 * Math.log((1 + x) / 2) + Math.log((1 + x * x) / 2) - 2 * Math.atan(x) + Math.PI / 2;
    }

    /** ψ_h(ζ), auch für die Feuchte. */
    public static double psiH(double zeta) {
        if (zeta >= 0) return zeta <= 1 ? -5 * zeta : -5 * (1 + Math.log(zeta));
        return 2 * Math.log((1 + Math.sqrt(1 - 16 * zeta)) / 2);
    }

    /**
     * Löst die Schicht aus den Werten in der Messhöhe und an der Oberfläche.
     *
     * @param wind   Betrag des Winds (m/s) in der Höhe zu (m)
     * @param thetaA potentielle Temperatur (K) in der Höhe zt (m): T + (g/c_p) zt
     * @param qA     spezifische Feuchte (kg/kg) in der Höhe zt
     * @param theta0 Temperatur der Oberfläche (K)
     * @param q0     spezifische Feuchte an der Oberfläche (kg/kg)
     * @param z0Land Rauheit (m) über Land; über Wasser ohne Belang
     * @param wasser Rauheit nach Charnock aus der Lösung
     * @param start  Anfangswert für 1/L (1/m), etwa aus dem vorigen Schritt; 0 für neutral
     * @param laeufe größte Zahl der Durchläufe
     * @param genau  Abbruch, wenn sich 1/L um weniger ändert (1/m)
     */
    public void loese(double wind, double zu, double thetaA, double zt, double qA, double theta0, double q0, double z0Land, boolean wasser, double start,
                      int laeufe, double genau) {
        double u = Math.max(wind, windMin), thv = thetaA * (1 + 0.608 * qA), dth = thetaA - theta0, dq = qA - q0;
        double il = Math.max(-1e3, Math.min(start, zetaMax / zu));
        double us = wasser ? 0.035 * u : 0;
        z0 = z0Land;
        int it = 0;
        while (true) {
            if (wasser) z0 = Math.max(1e-6, charnock * us * us / Luft.G + 0.11 * Luft.NU / us);
            z0h = wasser ? z0 : z0 / z0Verhaeltnis;
            double lm = Math.log(zu / z0), lh = Math.log(zt / z0h), usAlt = us;
            // nie unter einem Zehntel des neutralen Werts: bei großer Rauheit und starker Labilität liefe die Form sonst durch null
            double fm = Math.max(0.1 * lm, lm - psiM(zu * il) + psiM(z0 * il)), fh = Math.max(0.1 * lh, lh - psiH(zt * il) + psiH(z0h * il));
            us = Luft.KAPPA * u / fm;
            tstern = Luft.KAPPA * dth / fh;
            qstern = Luft.KAPPA * dq / fh;
            ra = fh / (Luft.KAPPA * us);
            double neu = Luft.KAPPA * Luft.G * (tstern * (1 + 0.608 * qA) + 0.608 * thetaA * qstern) / (thv * us * us);
            neu = Math.max(-1e3, Math.min(neu, zetaMax / zu));
            it++;
            double d = neu - il;
            il += 0.5 * d;                       // gedämpft: die ungedämpfte Folge pendelt bei stabiler Schichtung
            if (it >= laeufe || Math.abs(d) <= genau && Math.abs(us - usAlt) <= 1e-13 * us) break;   // auch u* und z0 (Charnock) müssen stehen
        }
        ustern = us;
        invL = il;
        durchlaeufe = it;
    }

    /**
     * Löst die Schicht aus dem Wind und den Flüssen statt aus den Werten der Oberfläche (für die Profile über einer
     * gemischten Fläche): fühlbare Wärme {@code hf} (W/m², nach oben positiv) und Verdunstung {@code verd} (kg/(m² s)).
     * {@link #ra} und {@link #z0h} sind danach ohne Bedeutung.
     *
     * @param rho Dichte der Luft (kg/m³)
     */
    public void loeseAusFluss(double wind, double zu, double thetaA, double qA, double hf, double verd, double rho, double z0Land, boolean wasser,
                              int laeufe, double genau) {
        double u = Math.max(wind, windMin), thv = thetaA * (1 + 0.608 * qA);
        double il = 0, us = wasser ? 0.035 * u : 0;
        z0 = z0Land;
        int it = 0;
        while (true) {
            if (wasser) z0 = Math.max(1e-6, charnock * us * us / Luft.G + 0.11 * Luft.NU / us);
            double lm = Math.log(zu / z0), fm = Math.max(0.1 * lm, lm - psiM(zu * il) + psiM(z0 * il)), usAlt = us;
            us = Luft.KAPPA * u / fm;
            tstern = -hf / (rho * Luft.CP * us);
            qstern = -verd / (rho * us);
            double neu = Luft.KAPPA * Luft.G * (tstern * (1 + 0.608 * qA) + 0.608 * thetaA * qstern) / (thv * us * us);
            neu = Math.max(-1e3, Math.min(neu, zetaMax / zu));
            it++;
            double d = neu - il;
            il += 0.5 * d;
            if (it >= laeufe || Math.abs(d) <= genau && Math.abs(us - usAlt) <= 1e-13 * us) break;
        }
        ustern = us;
        invL = il;
        z0h = z0;
        ra = Double.NaN;
        durchlaeufe = it;
    }

    /** Zuwachs der potentiellen Temperatur (K) von der Höhe za bis z (m), beide über der Rauheit. */
    public double thetaZwischen(double za, double z) { return tstern / Luft.KAPPA * (Math.log(z / za) - psiH(z * invL) + psiH(za * invL)); }

    /** Zuwachs der spezifischen Feuchte (kg/kg) von der Höhe za bis z (m). */
    public double feuchteZwischen(double za, double z) { return qstern / Luft.KAPPA * (Math.log(z / za) - psiH(z * invL) + psiH(za * invL)); }

    /** Wind (m/s) in der Höhe z (m) aus der letzten Lösung. */
    public double wind(double z) { return z <= z0 ? 0 : ustern / Luft.KAPPA * (Math.log(z / z0) - psiM(z * invL) + psiM(z0 * invL)); }

    /** Zuwachs der potentiellen Temperatur (K) von der Oberfläche bis z (m); unter z0h null. */
    public double thetaUeber(double z) { return z <= z0h ? 0 : tstern / Luft.KAPPA * (Math.log(z / z0h) - psiH(z * invL) + psiH(z0h * invL)); }

    /** Zuwachs der spezifischen Feuchte (kg/kg) von der Oberfläche bis z (m); unter z0h null. */
    public double feuchteUeber(double z) { return z <= z0h ? 0 : qstern / Luft.KAPPA * (Math.log(z / z0h) - psiH(z * invL) + psiH(z0h * invL)); }
}
