package com.dan.render.reference;

import com.dan.core.szene.Atmosphere;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Luft und Wassersäule der Referenz (P7b) und das Tracking durch sie nach Kutz u. a. (2017).
 * <p>
 * <b>Luft</b> in Kugelschalen wie {@link Atmosphere} (Boden 6 360 km, Oberkante 6 460 km): Rayleigh mit Skalenhöhe 8 km,
 * Mie (Dunst) mit 1,2 km, Ozon als Dreieck um 25 km. Die Höhe eines Punkts (Lage relativ zum Auge, y oben) ist
 * |p + (0, R + h_Auge, 0)| − R, wie im Echtzeitbild (das Auge über dem Boden der Kugel in seiner Höhe über NHN).
 * Gestreckt wird in Abschnitten mit eigener Majorante (Auslöschung an der tiefsten Stelle des Abschnitts).
 * <p>
 * <b>Wasser</b> gleichförmig: Absorption je Bahn, Streuung b gleich für alle (F54 A).
 * <p>
 * <b>Spektrales Tracking</b> (Kutz u. a. 2017, Abschnitt 5): an jedem vorläufigen Stoß mit der Majorante σ̄ werden
 * Absorption, Streuung und Nullstoß mit den Wahrscheinlichkeiten Σ|w σ| gewählt (über alle Bahnen gemittelt) und die
 * Gewichte je Bahn mit σ / (σ̄ P) nachgeführt; Absorption beendet den Pfad. Die Durchlässigkeit zu einer Lichtquelle
 * schätzt das Ratio-Tracking.
 */
final class Medien {
    static final double R = Atmosphere.BOTTOM, RT = Atmosphere.TOP;
    /** Größte Länge eines Abschnitts der Luft (m) [G]. */
    static final double ABSCHNITT_MAX = 20_000;

    final Wellen wl;
    final double hAuge;
    final double mieExt, mieSca;
    /** Schalter für die Prüfungen: Mie, Ozon, Rayleigh. */
    boolean mitMie = true, mitOzon = true, mitRayleigh = true;
    /** Streualbedo der Luft und des Wassers erzwingen 1 (Ofentest). */
    boolean ohneAbsorption;

    Medien(Wellen wl, double hAuge) {
        this.wl = wl;
        this.hAuge = hAuge;
        int n = wl.n;
        anteilR = new double[n];
        sR = new double[n];
        sM = new double[n];
        ab = new double[n];
        dR = new double[n];
        dM = new double[n];
        dA = new double[n];
        mieExt = wl.w.luft.mieExtinction;
        mieSca = wl.w.luft.mieScattering;
    }

    /** Höhe über dem Boden der Kugel am Punkt p (relativ zum Auge). */
    double hoehe(double[] p) {
        double y = R + hAuge + p[1];
        return Math.sqrt(p[0] * p[0] + p[2] * p[2] + y * y) - R;
    }

    /** Weg vom Punkt p in Richtung d bis zur Oberkante der Luft (oder 0, wenn schon außerhalb und weg). */
    double bisOben(double[] p, double[] d) {
        double y = R + hAuge + p[1];
        double b = p[0] * d[0] + y * d[1] + p[2] * d[2], c = p[0] * p[0] + p[2] * p[2] + y * y - RT * RT;
        double disk = b * b - c;
        if (disk < 0) return 0;
        double t = -b + Math.sqrt(disk);
        return Math.max(0, t);
    }

    /** Tiefste Höhe auf der Strecke p + t d, t in [a, b]. */
    double tiefste(double[] p, double[] d, double a, double b) {
        double y = R + hAuge + p[1];
        double tc = -(p[0] * d[0] + y * d[1] + p[2] * d[2]);
        double t = Math.max(a, Math.min(b, tc));
        double x = p[0] + t * d[0], yy = y + t * d[1], z = p[2] + t * d[2];
        return Math.sqrt(x * x + yy * yy + z * z) - R;
    }

    // ------------------------------------------------------------------ Luft je Höhe

    /** Streuung (Rayleigh, Mie) und Absorption je Bahn in der Höhe h. */
    void luft(double h, double[] sR, double[] sM, double[] a) {
        double dR = mitRayleigh ? Atmosphere.rayleighDensity(h) : 0, dM = mitMie ? Atmosphere.mieDensity(h) : 0;
        double dO = mitOzon && !ohneAbsorption ? Atmosphere.ozoneDensity(h) : 0;
        double ms = ohneAbsorption ? mieExt : mieSca;
        for (int k = 0; k < wl.n; k++) {
            sR[k] = wl.rayleigh[k] * dR;
            sM[k] = ms * dM;
            a[k] = wl.ozon[k] * dO + (ohneAbsorption ? 0 : (mieExt - mieSca) * dM);
        }
    }

    /** Majorante der Luft zwischen den Höhen hMin und hMax. */
    double majoranteLuft(double hMin, double hMax) {
        double dR = mitRayleigh ? Atmosphere.rayleighDensity(hMin) : 0, dM = mitMie ? Atmosphere.mieDensity(hMin) : 0;
        double hz = Math.max(hMin, Math.min(hMax, Atmosphere.OZONE_CENTER));
        double dO = mitOzon && !ohneAbsorption ? Atmosphere.ozoneDensity(hz) : 0;
        double m = 0;
        for (int k = 0; k < wl.n; k++) m = Math.max(m, wl.rayleigh[k] * dR + wl.ozon[k] * dO);
        return (m + mieExt * dM) * 1.000001 + 1e-30;
    }

    // ------------------------------------------------------------------ Ergebnis eines Flugs

    /** Ergebnis von {@link #fliege}: 0 bis zum Ende, 1 Streuung bei t (Rayleigh-Anteil je Bahn in {@code anteilR}), 2 absorbiert. */
    static final int ENDE = 0, STREUUNG = 1, ABSORBIERT = 2;
    /** Faktor auf die Majorante der Luft beim Flug mit Ereignissen an jedem Stoß (≥ 1). */
    double dichte = 1;

    /** Ort des Ereignisses und Rayleigh-Anteil der Streuung je Bahn (Luft). */
    double tEreignis;
    final double[] anteilR;
    private final double[] sR, sM, ab;
    /** Eigene Felder des Ratio-Trackings: es läuft auch mitten in einem Flug (Ereignis an einem Stoß). */
    private final double[] dR, dM, dA;

    /**
     * Freier Flug in der Luft von p in Richtung d bis tMax (höchstens bis zur Oberkante) mit spektralem Tracking.
     * w: Gewichte je Bahn (werden nachgeführt).
     */
    int fliegeLuft(double[] p, double[] d, double tMax, double[] w, SplittableRandom r) { return fliegeLuft(p, d, tMax, w, r, null); }

    /** An jedem vorläufigen Stoß (vor der Wahl des Ereignisses): Weg t, Streuung Rayleigh und Mie je Bahn, Majorante. */
    interface Stoss {
        void bei(double t, double[] sR, double[] sM, double mj);
    }

    /**
     * Wie oben, mit {@code stoss} an jedem vorläufigen Stoß (seit P7c: das Ereignis zur Lichtquelle an jedem Stoß statt
     * nur an der Streuung). Die Majorante ist mit {@link #dichte} vervielfacht: mehr Stöße, jeder mit kleinerem Gewicht.
     */
    int fliegeLuft(double[] p, double[] d, double tMax, double[] w, SplittableRandom r, Stoss stoss) {
        double tEnde = Math.min(tMax, bisOben(p, d));
        double t = 0;
        while (t < tEnde) {
            double h0 = hoehe(Vek.plus(p, t, d));
            double len = Math.min(ABSCHNITT_MAX, Math.max(200, 2 * (h0 + 500)));
            double b = Math.min(tEnde, t + len);
            double hMin = tiefste(p, d, t, b), hMax = Math.max(h0, hoehe(Vek.plus(p, b, d)));
            double mj = majoranteLuft(hMin, hMax) * (stoss != null ? dichte : 1);
            while (true) {
                t -= Math.log(1 - r.nextDouble()) / mj;
                if (t >= b) {
                    t = b;
                    break;
                }
                luft(hoehe(Vek.plus(p, t, d)), sR, sM, ab);
                if (stoss != null) stoss.bei(t, sR, sM, mj);
                int e = waehle(w, mj, r);
                if (e == 1) {
                    tEreignis = t;
                    for (int k = 0; k < wl.n; k++) {
                        double s = sR[k] + sM[k];
                        anteilR[k] = s > 0 ? sR[k] / s : 0;
                    }
                    return STREUUNG;
                }
                if (e == 2) return ABSORBIERT;
            }
        }
        tEreignis = tEnde;
        return ENDE;
    }

    /** Kutz: wählt 0 Null, 1 Streuung, 2 Absorption und führt w nach; sR, sM, ab sind gesetzt. */
    private int waehle(double[] w, double mj, SplittableRandom r) {
        double pa = 0, ps = 0, pn = 0, sc = 0;
        for (int k = 0; k < wl.n; k++) sc = Math.max(sc, Math.abs(w[k]));
        if (!(sc > 1e-250)) return 2;                                         // Gewicht ohne Belang: wie absorbiert
        for (int k = 0; k < wl.n; k++) {
            double s = sR[k] + sM[k], aw = Math.abs(w[k]) / sc;
            pa += aw * ab[k];
            ps += aw * s;
            pn += aw * Math.abs(mj - s - ab[k]);
        }
        double c = pa + ps + pn;
        if (c <= 0) return 0;
        double u = r.nextDouble() * c;
        if (u < pa) return 2;
        if (u < pa + ps) {
            double P = ps / c;
            for (int k = 0; k < wl.n; k++) w[k] *= (sR[k] + sM[k]) / (mj * P);
            return 1;
        }
        double P = pn / c;
        for (int k = 0; k < wl.n; k++) w[k] *= (mj - sR[k] - sM[k] - ab[k]) / (mj * P);
        return 0;
    }

    /**
     * Durchlässigkeit der Luft von p bis tMax (höchstens Oberkante), multipliziert auf T je Bahn. Seit P7c durch Quadratur
     * ({@link #quadratur}, ohne Rauschen; das Ratio-Tracking streut stark, wo die Majorante an der Auslöschung liegt, am
     * Boden), mit {@code quadratur = false} durch Ratio-Tracking (Prüfstein 2).
     */
    void durchLuft(double[] p, double[] d, double tMax, double[] T, SplittableRandom r) {
        if (!quadratur) {
            durchLuftRatio(p, d, tMax, T, r);
            return;
        }
        integrale(p, d, tMax, iq);
        for (int k = 0; k < wl.n; k++)
            T[k] *= Math.exp(-(wl.rayleigh[k] * iq[0] + mieExt * iq[1] + (ohneAbsorption ? 0 : wl.ozon[k] * iq[2])));
    }

    /** Durchlässigkeit durch Quadratur (Vorgabe) oder Ratio-Tracking. */
    boolean quadratur = true;
    private final double[] iq = new double[3];
    /** Gauß-Legendre mit 6 Punkten auf [−1, 1]. */
    private static final double[] GL_X = {-0.9324695142031521, -0.6612093864662645, -0.2386191860831969, 0.2386191860831969, 0.6612093864662645, 0.9324695142031521},
            GL_W = {0.1713244923791704, 0.3607615730481386, 0.4679139345726910, 0.4679139345726910, 0.3607615730481386, 0.1713244923791704};
    /** Knicke der Dichten über der Höhe (m): Boden, Ozon unten, Mitte, oben. */
    private static final double[] KNICKE = {0, Atmosphere.OZONE_CENTER - Atmosphere.OZONE_HALF_WIDTH, Atmosphere.OZONE_CENTER, Atmosphere.OZONE_CENTER + Atmosphere.OZONE_HALF_WIDTH};

    /**
     * ∫ Dichte ds für Rayleigh, Mie und Ozon längs p + t d, t in [0, min(tMax, Oberkante)]: Gauß-Legendre mit 6 Punkten
     * in Abschnitten wie beim Tracking (Länge 2 (h + 500 m), 200 m bis {@link #ABSCHNITT_MAX}), geteilt an den Knicken der
     * Dichten. Relativ ≤ 10⁻⁷ (geprüft gegen feinere Teilung, ReferenceCheck).
     */
    void integrale(double[] p, double[] d, double tMax, double[] aus) {
        double tEnde = Math.min(tMax, bisOben(p, d));
        aus[0] = aus[1] = aus[2] = 0;
        if (!(tEnde > 0)) return;
        double qy = R + hAuge + p[1], b = p[0] * d[0] + qy * d[1] + p[2] * d[2], qq = p[0] * p[0] + qy * qy + p[2] * p[2];
        double[] br = new double[9];
        int nb = 0;
        for (double hk : KNICKE) {
            double c = qq - (R + hk) * (R + hk), D = b * b - c;
            if (D <= 0) continue;
            double s = Math.sqrt(D);
            for (double t : new double[] {-b - s, -b + s}) if (t > 0 && t < tEnde) br[nb++] = t;
        }
        Arrays.sort(br, 0, nb);
        br[nb++] = tEnde;
        double t = 0;
        for (int i = 0; i < nb; i++) {
            double ende = br[i];
            while (t < ende) {
                double h0 = Math.sqrt(qq + 2 * b * t + t * t) - R;
                double len = Math.min(ABSCHNITT_MAX, Math.max(200, 2 * (Math.max(0, h0) + 500)));
                double e = Math.min(ende, t + len), m = 0.5 * (t + e), hw = 0.5 * (e - t);
                for (int g = 0; g < 6; g++) {
                    double s = m + hw * GL_X[g], h = Math.sqrt(qq + 2 * b * s + s * s) - R, w = GL_W[g] * hw;
                    aus[0] += w * (mitRayleigh ? Atmosphere.rayleighDensity(h) : 0);
                    aus[1] += w * (mitMie ? Atmosphere.mieDensity(h) : 0);
                    aus[2] += w * (mitOzon ? Atmosphere.ozoneDensity(h) : 0);
                }
                t = e;
            }
        }
    }

    /** Ratio-Tracking der Luft: multipliziert T je Bahn mit der Durchlässigkeit von p bis tMax (höchstens Oberkante). */
    void durchLuftRatio(double[] p, double[] d, double tMax, double[] T, SplittableRandom r) {
        double tEnde = Math.min(tMax, bisOben(p, d));
        double t = 0;
        while (t < tEnde) {
            double h0 = hoehe(Vek.plus(p, t, d));
            double len = Math.min(ABSCHNITT_MAX, Math.max(200, 2 * (h0 + 500)));
            double b = Math.min(tEnde, t + len);
            double hMin = tiefste(p, d, t, b), hMax = Math.max(h0, hoehe(Vek.plus(p, b, d)));
            double mj = majoranteLuft(hMin, hMax);
            while (true) {
                t -= Math.log(1 - r.nextDouble()) / mj;
                if (t >= b) {
                    t = b;
                    break;
                }
                luft(hoehe(Vek.plus(p, t, d)), dR, dM, dA);
                double mx = 0;
                for (int k = 0; k < wl.n; k++) {
                    T[k] *= 1 - (dR[k] + dM[k] + dA[k]) / mj;
                    mx = Math.max(mx, T[k]);
                }
                if (mx < 0.05) {                                                // Russisches Roulette
                    if (r.nextDouble() < 0.5) {
                        java.util.Arrays.fill(T, 0, wl.n, 0);
                        return;
                    }
                    for (int k = 0; k < wl.n; k++) T[k] *= 2;
                }
            }
        }
    }

    // ------------------------------------------------------------------ Wasser

    /**
     * Seit P10d (S8): Trübung des Wassers für den Pfad (g/m³), gesetzt beim Eintritt ins Wasser aus der Szene; NaN: der
     * Schwebstoff der Szene. Mit {@link #truebung(double)} setzen (rechnet die Beiwerte je Bahn einmal).
     */
    double truebung = Double.NaN;
    private final double[] aOrt = new double[16];
    private double bOrt;

    void truebung(double c) {
        truebung = c;
        if (Double.isNaN(c)) return;
        for (int k = 0; k < wl.n; k++) aOrt[k] = wl.aWasserBei(k, c);
        bOrt = Wellen.bWasserBei(c);
    }

    /** Absorption je Bahn und Streuung (gleich) des Wassers. */
    double aWasser(int k) { return ohneAbsorption ? 0 : Double.isNaN(truebung) ? wl.aWasser[k] : aOrt[k]; }

    double bWasser() { return Double.isNaN(truebung) ? wl.w.bWasser : bOrt; }

    double majoranteWasser() {
        double m = 0;
        for (int k = 0; k < wl.n; k++) m = Math.max(m, aWasser(k));
        return (m + bWasser()) * 1.000001;
    }

    /** Freier Flug im Wasser bis tMax mit spektralem Tracking. */
    int fliegeWasser(double tMax, double[] w, SplittableRandom r) {
        double mj = majoranteWasser(), b = bWasser(), t = 0;
        for (int k = 0; k < wl.n; k++) {
            sR[k] = 0;
            sM[k] = b;
            ab[k] = aWasser(k);
        }
        while (true) {
            t -= Math.log(1 - r.nextDouble()) / mj;
            if (t >= tMax) {
                tEreignis = tMax;
                return ENDE;
            }
            int e = waehle(w, mj, r);
            if (e == 1) {
                tEreignis = t;
                return STREUUNG;
            }
            if (e == 2) return ABSORBIERT;
        }
    }

    /** Durchlässigkeit des Wassers über die Länge s je Bahn (gleichförmig: genau). */
    void durchWasser(double s, double[] T) {
        double b = bWasser();
        for (int k = 0; k < wl.n; k++) T[k] *= Math.exp(-(aWasser(k) + b) * s);
    }
}
