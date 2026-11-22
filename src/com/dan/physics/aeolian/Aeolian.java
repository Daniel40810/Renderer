package com.dan.physics.aeolian;

import java.util.Arrays;

/**
 * Flugsand (Werkbuch HalligHooge Teil A, 7.6 und 12.11, P10c): die Rechnung in doppelter Genauigkeit auf einem Gitter aus
 * nx × ny Zellen. Eine Fassung auf der Grafikkarte gibt es nicht: Ein Kopplungsintervall ist eine Formel je Zelle und
 * ein Durchlauf über das Gitter.
 * <p>
 * <b>Eingang</b> (setzt der Aufrufer; am Bus {@link AeolianModul}): je Zelle der Wind in 10 m ({@link #u}, {@link #v}),
 * die Nässe der obersten Bodenschicht ({@link #wet}, Sättigung 0 … 1, {@code surf.wet}) und die Wassertiefe
 * ({@link #h}). Der Boden je Zelle steht in {@link #d50}, {@link #poros}, {@link #schlick}, {@link #fest}.
 * <p>
 * <b>Wind am Boden:</b> u* = κ U / ln(z / z0), neutral, mit einer Rauheit für alle Zellen [G].
 * <p>
 * <b>Schwelle:</b> trocken nach Bagnold, u*t = A √((ρs − ρ) / ρ · g d) ({@link #schwelle}); feucht nach Belly (1964),
 * mal max(1; 1,8 + 0,6 log₁₀ w) mit w in Prozent des Trockengewichts ({@link #feucht}); w folgt aus der Sättigung und
 * dem Porenanteil ({@link #prozent}). Über {@link #wMax} nimmt der Wind nichts auf (Belly hat bis 4 % gemessen).
 * <p>
 * <b>Fluss:</b> gesättigt nach Kawamura (1951), q = C (ρ / g)(u* − u*t)(u* + u*t)² ({@link #fluss}); zum Vergleich
 * nach Lettau und Lettau ({@link #lettau}). Die Sättigungslänge (rund 0,7 m, Andreotti u. a. 2010) liegt weit unter
 * der Zelle: Über losem, trockenem Sand ist der Fluss am Ende der Zelle gesättigt.
 * <p>
 * <b>Was eine Zelle weitergibt</b> (q_ein kommt von den Zellen im Luv, q_trag ist der Fluss mit der trockenen
 * Schwelle, q_auf der mit der feuchten):
 * <ul>
 * <li>Wasser: nichts – alles bleibt liegen.</li>
 * <li>loser Sand, nicht zu nass: max(q_auf; min(q_ein; q_trag)).</li>
 * <li>zu nasser Sand: min(q_ein; q_trag) · exp(−L / {@link #lHaft}) – die Körner springen weiter und bleiben nach und
 * nach haften [G].</li>
 * <li>fester Boden (Hallig, Deich, Warft) und Schlick: min(q_ein; q_trag) · exp(−L / {@link #lFang}) – der Bewuchs fängt
 * den Sand [G]; ab P12 aus {@code veg.cover}.</li>
 * </ul>
 * L ist der Weg des Winds durch die Zelle (Fläche durch die Breite quer zum Wind).
 * <p>
 * <b>Bilanz (Exner):</b> Der Fluss verlässt die Zelle über ihre Kanten im Lee, zerlegt nach Ost und Nord; die Höhe
 * ändert sich um (Zufluss − Abfluss) Δt / (ρs (1 − θ) · Fläche). Die Zellen werden von Luv nach Lee durchlaufen. Wechselt
 * im Gitter das Vorzeichen einer Windkomponente, gilt für diesen Schritt überall die mittlere Richtung [G]
 * ({@link #gemittelt} zählt solche Schritte). Die Masse bleibt bis auf Rundung erhalten: {@link #abtrag},
 * {@link #auftrag}, {@link #ausflug}.
 * <p>
 * <b>Grenzen:</b> Der Wind kennt das Relief nicht (keine Dünen); ein Korn je Zelle, kein Schill, keine Panzerung; der
 * Vorrat an Sand ist unbegrenzt; kein Hangrutsch; A gilt streng für Sand über 0,2 mm. Die Formel sagt gegen
 * Feldmessungen rund das Dreifache voraus (Sherman u. a. 2013). Kennt nur java.* (Schichtenvertrag, Werkbuch 4).
 */
public final class Aeolian {
    public static final String VERSION = "0.1";

    public final int nx, ny, nc;
    /** Zellgröße (m). */
    public final double dx, dy;

    // ------------------------------------------------------------------ gesetzte Größen
    /** von Kármán, Messhöhe des Winds (m), Rauheit (m) [G, wie atmos über Land]. */
    public double kappa = 0.4, zWind = 10, z0 = 1e-3;
    /** Bagnolds Beiwert der Schwelle: 0,1 für den Beginn aus der Ruhe. */
    public double a = 0.1;
    /** Kawamuras Beiwert (2,78 für seinen Sand von 0,25 mm; gegen Feldmessungen neu geeicht 0,70, Sherman u. a. 2013). */
    public double c = 2.78;
    /** Zum Vergleich: Fluss nach Lettau und Lettau (Beiwert, Bezugskorn in m). */
    public boolean lettau;
    public double cLettau = 6.7, dBezug = 0.25e-3;
    /** Dichte der Luft, des Korns, des Wassers (kg/m³), Schwere (m/s²). */
    public double rho = 1.25, rhoS = 2650, rhoW = 1000, g = 9.81;
    /** Über diesem Wassergehalt (Prozent des Trockengewichts) nimmt der Wind nichts auf [G: Bellys Messbereich]. */
    public double wMax = 4;
    /** Längen (m), über die der Fluss über zu nassem Sand und über festem Boden auf 1/e fällt [G]. */
    public double lHaft = 100, lFang = 20;
    /** Fest ist eine Zelle ab diesem Anteil festen Bodens oder über diesem Schlickanteil [G]. */
    public double festAb = 0.5, schlickMax = 0.10;
    /** Ab dieser Tiefe (m) trägt die Zelle Wasser. */
    public double hNass = 1e-3;
    /** Morphologischer Faktor: Höhe und Massen zählen so oft (F98 A: 1). */
    public double faktor = 1;

    // ------------------------------------------------------------------ Boden
    /** Je Zelle: Korn (m), Porenanteil, Schlickanteil, Anteil festen Bodens. Vorgabe: Sand 0,2 mm, 0,4, 0, 0. */
    public final double[] d50, poros, schlick, fest;

    // ------------------------------------------------------------------ Eingang
    /** Je Zelle: Wind in 10 m nach Ost und nach Nord (m/s), Nässe (0 … 1), Wassertiefe (m). */
    public final double[] u, v, wet, h;
    /**
     * Seit P13b (F153 A): Bedeckung durch Pflanzen je Zelle (0–1) aus {@code veg.cover}; null: ohne. Auf festem Boden
     * mit Bedeckung ist die Fanglänge {@link #lFang} / max(0,25; Bedeckung) [G]: dichter Bewuchs fängt wie bisher,
     * lichter (Pionierzone im Winter) weniger. Ohne Bedeckung bleibt {@link #lFang}.
     */
    public double[] deckung;

    // ------------------------------------------------------------------ Zustand und Ausgabe
    /** Änderung der Höhe seit dem Beginn (m), {@code bed.dz.aeolian}. */
    public final double[] dz;
    /** Fluss, den die Zelle im letzten Schritt weitergab (kg/(m s)), nach Ost und nach Nord: {@code aeol.q}. */
    public final double[] qx, qy;
    /** Aufgelaufen (kg, mit {@link #faktor}): abgetragen, aufgetragen, über den Rand des Gitters geflogen. */
    public double abtrag, auftrag, ausflug;
    /** Aufgelaufen (kg): was in Wasser fiel (Teil von {@link #auftrag}). */
    public double insWasser;
    /** Zellen mal Schritte mit Fluss; Schritte mit gemittelter Richtung; Schritte. */
    public long aktiv, gemittelt, schritte;
    /** Je Zelle die Zeit mit Fluss (s) und der weitergegebene Sand (kg/m), aufgelaufen. */
    public final double[] dauer, menge;
    /** Je Zelle die Zeit, in der der Wind aus ihr aufnehmen konnte (s), und was er dabei höchstens aufnahm (kg/m: Σ q_auf Δt), aufgelaufen. */
    public final double[] dauerAuf, aufnahme;

    private final double[] ein;                    // Zufluss je Zelle im laufenden Schritt (kg/s)

    public Aeolian(int nx, int ny, double dx, double dy) {
        this.nx = nx;
        this.ny = ny;
        this.nc = nx * ny;
        this.dx = dx;
        this.dy = dy;
        d50 = new double[nc]; poros = new double[nc]; schlick = new double[nc]; fest = new double[nc];
        Arrays.fill(d50, 2e-4);
        Arrays.fill(poros, 0.4);
        u = new double[nc]; v = new double[nc]; wet = new double[nc]; h = new double[nc];
        dz = new double[nc]; qx = new double[nc]; qy = new double[nc]; dauer = new double[nc]; menge = new double[nc]; dauerAuf = new double[nc]; aufnahme = new double[nc];
        ein = new double[nc];
    }

    /** Auf null: Höhe, Flüsse und Summen. */
    public void anfang() {
        Arrays.fill(dz, 0); Arrays.fill(qx, 0); Arrays.fill(qy, 0); Arrays.fill(dauer, 0); Arrays.fill(menge, 0); Arrays.fill(dauerAuf, 0); Arrays.fill(aufnahme, 0);
        abtrag = auftrag = ausflug = insWasser = 0;
        aktiv = gemittelt = schritte = 0;
    }

    /** u* (m/s) zum Wind U (m/s) in der Höhe {@link #zWind}. */
    public double ustern(double wind) { return kappa * wind / Math.log(zWind / z0); }

    /** Der Wind in der Höhe {@link #zWind} (m/s), bei dem u* den Wert us hat. */
    public double wind(double us) { return us * Math.log(zWind / z0) / kappa; }

    /** Schwelle u*t (m/s) für trockenen Sand vom Korn d (m): Bagnold. */
    public double schwelle(double d) { return a * Math.sqrt((rhoS - rho) / rho * g * d); }

    /** Faktor der Schwelle bei w Prozent Wasser (vom Trockengewicht): Belly (1964), nie unter 1. */
    public static double feucht(double w) { return w > 0 ? Math.max(1, 1.8 + 0.6 * Math.log10(w)) : 1; }

    /** Wassergehalt in Prozent des Trockengewichts bei der Sättigung s (0 … 1) und dem Porenanteil theta. */
    public double prozent(double s, double theta) { return 100 * s * theta * rhoW / (rhoS * (1 - theta)); }

    /** Gesättigter Fluss (kg/(m s)) bei u*, der Schwelle ut (beide m/s) und dem Korn d (m): Kawamura oder, mit {@link #lettau}, Lettau und Lettau. */
    public double fluss(double us, double ut, double d) {
        if (!(us > ut)) return 0;
        return lettau ? cLettau * Math.sqrt(d / dBezug) * rho / g * us * us * (us - ut) : c * rho / g * (us - ut) * (us + ut) * (us + ut);
    }

    /** Die Art der Zelle c: 0 Wasser, 1 loser Sand, 2 zu nasser Sand, 3 fest. */
    public int art(int c) {
        if (h[c] >= hNass) return 0;
        if (fest[c] >= festAb || schlick[c] > schlickMax) return 3;
        return prozent(wet[c], poros[c]) > wMax ? 2 : 1;
    }

    /** Ein Schritt der Länge dt (s) mit dem Eingang, wie er steht. */
    public void schritt(double dt) {
        // Richtung: je Zelle, solange die Vorzeichen der Komponenten im Gitter einheitlich sind; sonst die mittlere
        boolean xp = false, xm = false, yp = false, ym = false;
        double su = 0, sv = 0;
        for (int k = 0; k < nc; k++) {
            if (u[k] > 0) xp = true; else if (u[k] < 0) xm = true;
            if (v[k] > 0) yp = true; else if (v[k] < 0) ym = true;
            su += u[k];
            sv += v[k];
        }
        boolean mittel = xp && xm || yp && ym;
        double mx = 0, my = 0;
        if (mittel) {
            double m = Math.hypot(su, sv);
            if (m > 0) { mx = su / m; my = sv / m; }
            gemittelt++;
        }
        boolean nachOst = mittel ? mx >= 0 : !xm, nachNord = mittel ? my >= 0 : !ym;
        Arrays.fill(ein, 0);
        double flaeche = dx * dy;
        for (int jj = 0; jj < ny; jj++) {
            int j = nachNord ? jj : ny - 1 - jj;
            for (int ii = 0; ii < nx; ii++) {
                int i = nachOst ? ii : nx - 1 - ii, k = i + nx * j;
                double wind = Math.hypot(u[k], v[k]), cx = 0, cy = 0;
                if (mittel) { cx = mx; cy = my; } else if (wind > 0) { cx = u[k] / wind; cy = v[k] / wind; }
                double breite = Math.abs(cx) * dy + Math.abs(cy) * dx, zu = ein[k], qAus = 0;
                if (breite > 0) {
                    int art = art(k);
                    if (art != 0) {
                        double us = ustern(wind), ut = schwelle(d50[k]), qEin = zu / breite, durch = Math.min(qEin, fluss(us, ut, d50[k]));
                        if (art == 1) {
                            double qAuf = fluss(us, ut * feucht(prozent(wet[k], poros[k])), d50[k]);
                            if (qAuf > 0) {
                                dauerAuf[k] += dt;
                                aufnahme[k] += qAuf * dt;
                            }
                            qAus = Math.max(qAuf, durch);
                        }
                        else qAus = durch * Math.exp(-flaeche / breite / (art == 2 ? lHaft : deckung != null && deckung[k] > 0 ? lFang / Math.max(0.25, deckung[k]) : lFang));
                    }
                }
                double ab = qAus * breite, dm = (zu - ab) * dt * faktor;                 // kg
                dz[k] += dm / (rhoS * (1 - poros[k]) * flaeche);
                if (dm >= 0) {
                    auftrag += dm;
                    if (h[k] >= hNass) insWasser += dm;
                } else abtrag -= dm;
                qx[k] = qAus * cx;
                qy[k] = qAus * cy;
                if (qAus > 0) {
                    aktiv++;
                    dauer[k] += dt;
                    menge[k] += qAus * dt;
                    double ox = qAus * Math.abs(cx) * dy, oy = qAus * Math.abs(cy) * dx;   // kg/s über die Kante im Lee nach Ost oder West, nach Nord oder Süd
                    int in = cx > 0 ? i + 1 : i - 1, jn = cy > 0 ? j + 1 : j - 1;
                    if (ox > 0) {
                        if (in >= 0 && in < nx) ein[in + nx * j] += ox;
                        else ausflug += ox * dt * faktor;
                    }
                    if (oy > 0) {
                        if (jn >= 0 && jn < ny) ein[i + nx * jn] += oy;
                        else ausflug += oy * dt * faktor;
                    }
                }
            }
        }
        schritte++;
    }

    /** Was der Bilanz fehlt (kg): Auftrag − Abtrag + Ausflug; bis auf Rundung null. */
    public double rest() { return auftrag - abtrag + ausflug; }
}
