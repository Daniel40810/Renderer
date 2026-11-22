package com.dan.render.reference;

import com.dan.core.raum.Erde;

/**
 * Gekrümmte Sehstrahlen für die Referenz (P10b, Werkbuch HalligHooge Teil A, 12.11; F93 A, F94 A): Die Luft über dem
 * Wasser ist waagerecht geschichtet (Brechung n − 1 über der Höhe über der Fläche, wie {@code air.n}). Ein Sehstrahl
 * nahe der Waagerechten (± {@link #halb} Grad) läuft nach der {@link Strahlgleichung} über der Kugel; sein Weg wird im
 * gesenkten Raum der {@link Szene} (um d² / 2 R′ wie das Bild) als Streckenzug gegen die Flächen geschnitten.
 * {@link #gerade} gibt die gerade Richtung zu dem Punkt, den er zuerst trifft – dort verfolgt die Referenz ihren Pfad
 * weiter –, oder die Richtung, in die er die Tafel verlässt (Himmel).
 * <p>
 * Die Strahlen hängen nur vom Sehwinkel ab: {@link #NB} Wege werden einmal gerechnet (Runge-Kutta, Schritt 0,25 m unter
 * 1 m Höhe, sonst 1 m bis 2 m [G]), mit einer Ecke, sobald die Strecke seit der letzten um {@link #PFEIL} m vom Bogen
 * abweicht, spätestens nach {@link #STRECKE} m. Je Strahl der Referenz gilt der Weg des nächsten Sehwinkels, um den
 * Unterschied gedreht.
 * <p>
 * Die Fläche lässt Strahlen ab der Höhe {@code sperr} durch (F93 A): Der Streckenzug liegt beim Schneiden um diese
 * Höhe tiefer. Ein Strahl, der die Fläche erreicht, läuft in seiner letzten Richtung noch {@link #NACH} m weiter, damit
 * er das Wasser der Szene auch trifft, wo es tiefer steht als die Fläche.
 * <p>
 * Unabhängig von {@code render.air.Kimmung} (dort Parabeln je Schicht und die Suche im Bild).
 */
public final class Luftstrahl {
    /** Sehwinkel zwischen ± halb, größte Länge einer Strecke (m), größter Pfeil zwischen Strecke und Bogen (m), Nachlauf unter die Fläche (m) [G]. */
    public static final int NB = 2048;
    public static final double STRECKE = 400, PFEIL = 1e-3, NACH = 2000;

    private final Szene szene;
    private final double tanHalb, auge, sperr, weit, hoechste;
    /** Je Sehwinkel die Ecken im gesenkten Raum: Entfernung d (m) und Höhe y relativ zum Auge (m). */
    private final double[][] d, y;
    /** Je Sehwinkel: Tangens der Richtung am Ende im gesenkten Raum; NaN: der Strahl erreicht die Fläche. */
    private final double[] ende;
    /** Gezählt: Strahlen im Streifen, davon mit Treffer, zum Himmel. */
    public final java.util.concurrent.atomic.LongAdder strahlen = new java.util.concurrent.atomic.LongAdder(), treffer = new java.util.concurrent.atomic.LongAdder(),
            himmel = new java.util.concurrent.atomic.LongAdder();
    public final double sekunden;

    /**
     * @param hoehen  Höhen über der Fläche (m), steigend, die erste 0
     * @param nMinus1 Brechung n − 1 in diesen Höhen; darüber das Gefälle der Normalatmosphäre (k = 0,13)
     * @param auge    Höhe des Auges über der Fläche (m)
     * @param sperr   Höhe über der Fläche, ab der sie Strahlen durchlässt (m)
     * @param halb    halbe Höhe des Streifens (Grad)
     * @param weit    größte Entfernung (m)
     * @param hoechste höchste Fläche der Szene über der Fläche der Luft (m); Strecken darüber werden nicht geschnitten
     */
    public Luftstrahl(Szene szene, double[] hoehen, double[] nMinus1, double auge, double sperr, double halb, double weit, double hoechste) {
        long n0 = System.nanoTime();
        this.szene = szene;
        this.auge = auge;
        this.sperr = sperr;
        this.weit = weit;
        this.hoechste = hoechste;
        tanHalb = Math.tan(Math.toRadians(halb));
        d = new double[NB][];
        y = new double[NB][];
        ende = new double[NB];
        double R = Erde.R, oben = hoehen[hoehen.length - 1];
        Strahlgleichung sg = new Strahlgleichung(new Strahlgleichung.Brechzahl() {
            double n1(double h) {
                if (h <= 0) return nMinus1[0];
                if (h >= oben) return nMinus1[nMinus1.length - 1] - Erde.REFRAKTION * (h - oben) / R;
                int k = stufe(h);
                return nMinus1[k] + (nMinus1[k + 1] - nMinus1[k]) * (h - hoehen[k]) / (hoehen[k + 1] - hoehen[k]);
            }

            int stufe(double h) {
                int lo = 0, hi = hoehen.length - 2;
                while (lo < hi) {
                    int m = (lo + hi + 1) >>> 1;
                    if (hoehen[m] <= h) lo = m;
                    else hi = m - 1;
                }
                return lo;
            }

            @Override public double n(double[] r) { return 1 + n1(Math.hypot(r[0], r[1]) - R); }

            @Override public double[] gradient(double[] r) {
                double rr = Math.hypot(r[0], r[1]), h = rr - R, g;
                if (h < 0) g = 0;
                else if (h >= oben) g = -Erde.REFRAKTION / R;
                else {
                    int k = stufe(h);
                    g = (nMinus1[k + 1] - nMinus1[k]) / (hoehen[k + 1] - hoehen[k]);
                }
                return new double[] {g * r[0] / rr, g * r[1] / rr, 0};
            }
        });
        double rOpt2 = 2 * Erde.R_OPTISCH;
        java.util.stream.IntStream.range(0, NB).parallel().forEach(i -> {
            double tan = tan(i), beta = Math.atan(tan);
            double[] z = sg.start(new double[] {0, R + auge, 0}, new double[] {Math.cos(beta), Math.sin(beta), 0});
            java.util.ArrayList<double[]> ecken = new java.util.ArrayList<>();
            ecken.add(new double[] {0, 0});
            double h = auge, letzteD = 0, letzteS = tan, end = Double.NaN;
            for (long schritt = 0; schritt < 2_000_000; schritt++) {
                double[] nz = sg.schritt(z, h < 1 ? 0.25 : h < 10 ? 1 : 2);
                double rr = Math.hypot(nz[0], nz[1]);
                h = rr - R;
                double bogen = R * Math.atan2(nz[0], nz[1]), yf = h - auge - bogen * bogen / rOpt2;
                // Steigung im gesenkten Raum: Richtung gegen die örtliche Waagerechte, weniger d / R′
                double radial = (nz[3] * nz[0] + nz[4] * nz[1]) / rr, quer = (nz[3] * nz[1] - nz[4] * nz[0]) / rr, s = radial / quer - 2 * bogen / rOpt2;
                z = nz;
                boolean unten = h <= sperr, fern = bogen >= weit, hoch = h > oben && radial > 0;
                if (unten || fern || hoch || bogen - letzteD >= STRECKE || Math.abs(s - letzteS) * (bogen - letzteD) / 8 >= PFEIL) {
                    ecken.add(new double[] {bogen, yf});
                    letzteD = bogen;
                    letzteS = s;
                }
                if (unten) {
                    ecken.add(new double[] {bogen + NACH, yf + s * NACH});
                    break;
                }
                if (fern || hoch) {
                    end = s;
                    break;
                }
            }
            d[i] = new double[ecken.size()];
            y[i] = new double[ecken.size()];
            for (int k = 0; k < d[i].length; k++) {
                d[i][k] = ecken.get(k)[0];
                y[i][k] = ecken.get(k)[1];
            }
            ende[i] = end;
        });
        sekunden = (System.nanoTime() - n0) * 1e-9;
    }

    /** Tangens des Sehwinkels der Zeile i. */
    public double tan(int i) { return -tanHalb + 2 * tanHalb * i / (NB - 1); }

    /** Zahl der Ecken des Wegs i und ob er die Fläche erreicht. */
    public int ecken(int i) { return d[i].length; }

    public boolean erreichtFlaeche(int i) { return Double.isNaN(ende[i]); }

    /** Entfernung, in der der Weg i endet (m). */
    public double weite(int i) { return d[i][d[i].length - 1]; }

    /**
     * Die gerade Richtung (Einheitsvektor, relativ zum Auge: x Ost, y oben, z Süd) zu dem, was der gekrümmte Sehstrahl in
     * der Richtung {@code r} zuerst trifft; außerhalb des Streifens {@code r} selbst.
     */
    public double[] gerade(double[] r) {
        double wag = Math.hypot(r[0], r[2]);
        if (!(wag > 0)) return r;
        double tan = r[1] / wag;
        if (Math.abs(tan) >= tanHalb) return r;
        strahlen.increment();
        int i = (int) Math.max(0, Math.min(NB - 1, Math.rint((tan + tanHalb) / (2 * tanHalb) * (NB - 1))));
        double dt = tan - tan(i), ex = r[0] / wag, ez = r[2] / wag;
        double[] di = d[i], yi = y[i], p = new double[3], q = new double[3];
        Szene.Treffer t = new Szene.Treffer();
        for (int k = 0; k + 1 < di.length; k++) {
            // Strecke k im gesenkten Raum, um den Unterschied des Sehwinkels gedreht und um die Sperrhöhe gesenkt
            double d0 = di[k], d1 = di[k + 1], y0 = yi[k] + dt * d0 - (k == 0 ? 0 : sperr), y1 = yi[k + 1] + dt * d1 - sperr;
            // über der höchsten Fläche der Szene (im gesenkten Raum sinkt sie mit d²) gibt es nichts zu schneiden
            if (Math.min(y0, y1) > hoechste - auge - d0 * d0 / (2 * Erde.R_OPTISCH) + 0.5) continue;
            p[0] = ex * d0;
            p[1] = y0;
            p[2] = ez * d0;
            q[0] = ex * (d1 - d0);
            q[1] = y1 - y0;
            q[2] = ez * (d1 - d0);
            double l = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
            if (!(l > 0)) continue;
            for (int c = 0; c < 3; c++) q[c] /= l;
            if (szene.schneide(p, q, l, t)) {
                treffer.increment();
                // die Gerade zum getroffenen Punkt: Sie trifft dieselbe Fläche dort
                return Vek.norm(new double[] {p[0] + q[0] * t.t, p[1] + q[1] * t.t, p[2] + q[2] * t.t});
            }
        }
        himmel.increment();
        // die Richtung am Ende; erreicht der Strahl die Fläche, ohne dass die Szene dort etwas zeigt: zum Ende des Nachlaufs
        int n = di.length;
        if (Double.isNaN(ende[i])) return Vek.norm(new double[] {ex * di[n - 1], yi[n - 1] + dt * di[n - 1] - sperr, ez * di[n - 1]});
        return Vek.norm(new double[] {ex, ende[i] + dt, ez});
    }
}
