package com.dan.render.reference;

import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Lichtquellen;
import com.dan.core.szene.Wasseroptik;
import com.dan.core.szene.spektrum.Spektrum;

/**
 * Die Wellenlängen eines Pfads (P7b, F53 A) und alle Größen der Szene, die von ihnen abhängen. Zwei Arten:
 * <ul>
 * <li><b>spektral:</b> vier Wellenlängen nach Wilkie u. a. (2014), die Heldenwellenlänge gleichverteilt in 360–830 nm,
 * die drei anderen um je ein Viertel des Bereichs verschoben (zyklisch). Jede Bahn hat dieselbe Dichte; das Mittel über
 * die Bahnen ist unverzerrt.</li>
 * <li><b>drei Kanäle:</b> die Zahlen des Echtzeitbilds (680, 550, 440 nm [G]): Sonne weiß, Rayleigh und Ozon aus
 * {@link Atmosphere}, Wasser aus {@link Wasseroptik}, Albedo RGB. Der Schalter aus F53 A.</li>
 * </ul>
 * Alle Felder je Bahn; die Werte werden bei jedem neuen Pfad gesetzt ({@link #setze}).
 */
public final class Wellen {
    public final boolean spektral;
    /** Zahl der Bahnen: 4 spektral, 3 Kanäle. */
    public final int n;
    /** Wellenlänge je Bahn (nm). */
    public final double[] lambda;
    /** Luft: Rayleigh am Boden, Ozon größte Auslöschung (1/m). */
    public final double[] rayleigh, ozon;
    /** Wasser: Absorption (reines Wasser und Schwebstoff), 1/m. */
    public final double[] aWasser;
    /** Sonne außerhalb der Luft in 1 AE, Mond (Beleuchtungsstärke außerhalb der Luft), Nachthimmel (Leuchtdichte). */
    public final double[] sonne, mond, nacht;
    /**
     * Seit P10d (S8): Absorption des Wassers je Bahn bei der Trübung c (g/m³), dieselbe Formel wie {@link #aWasser}
     * (bei c gleich dem Schwebstoff der Szene bitgleich).
     */
    public double aWasserBei(int k, double c) {
        if (!spektral) return Wasseroptik.POPE_FRY[k] + Wasseroptik.A_STERN_443 * c * Math.exp(-Wasseroptik.S_NAP * (Wasseroptik.LAMBDA[k] - 443));
        double l = lambda[k];
        return Spektrum.wasser(l) + Wasseroptik.A_STERN_443 * c * Math.exp(-Wasseroptik.S_NAP * (l - 443));
    }

    /** Streuung des Schwebstoffs (1/m) bei der Trübung c, wie {@link Szenenwerte#bWasser}. */
    public static double bWasserBei(double c) { return Wasseroptik.BB_STERN * c / BB_ZU_B; }

    /** Gewichte nach Mallett und Yuksel für die Mondfarbe. */
    private final double[] mondGewichte;

    final Szenenwerte w;

    /** Feste Werte der Szene, die nicht von der Wellenlänge abhängen. */
    public static final class Szenenwerte {
        public final Atmosphere luft;
        public final Wasseroptik wasser;
        /** Streuung des Schwebstoffs (1/m), F54 A: b = b_b / {@link #BB_ZU_B}. */
        public final double bWasser;

        public Szenenwerte(Atmosphere luft, Wasseroptik wasser) {
            this.luft = luft;
            this.wasser = wasser;
            this.bWasser = Wasseroptik.BB_STERN * wasser.schwebstoff / BB_ZU_B;
        }
    }

    /** Rückstreuanteil des Schwebstoffs b_b / b [S] (F54 A, mineralische Teilchen nach Mobley 1994). */
    public static final double BB_ZU_B = 0.019;

    public Wellen(boolean spektral, Szenenwerte w) { this(spektral, w, 4); }

    /** Spektral mit {@code bahnen} Wellenlängen je Pfad (seit P7c; mehr Bahnen: weniger Farbrauschen bei gleicher Geometrie). */
    public Wellen(boolean spektral, Szenenwerte w, int bahnen) {
        this.spektral = spektral;
        this.w = w;
        n = spektral ? bahnen : 3;
        lambda = new double[n];
        rayleigh = new double[n];
        ozon = new double[n];
        aWasser = new double[n];
        sonne = new double[n];
        mond = new double[n];
        nacht = new double[n];
        mondGewichte = Spektrum.gewichte(Lichtquellen.MONDFARBE);
        if (!spektral) setze(0);
    }

    /** Neue Wellenlängen für einen Pfad: u gleichverteilt in [0, 1) (nur spektral). */
    public void setze(double u) {
        if (!spektral) {
            double[] aS = w.wasser.aSchweb();
            for (int c = 0; c < 3; c++) {
                lambda[c] = Spektrum.KANAELE[c];
                rayleigh[c] = Atmosphere.RAYLEIGH[c];
                ozon[c] = Atmosphere.OZONE[c];
                aWasser[c] = Wasseroptik.POPE_FRY[c] + aS[c];
                sonne[c] = Lichtquellen.SONNE_LX;
                mond[c] = Lichtquellen.SONNE_LX * Lichtquellen.MONDFARBE[c];
                nacht[c] = Lichtquellen.NACHT;
            }
            return;
        }
        double breite = Spektrum.MAX - Spektrum.MIN, l0 = u * breite;
        double nachtFaktor = Lichtquellen.NACHT / Spektrum.sonneLux();
        for (int k = 0; k < n; k++) {
            double l = Spektrum.MIN + (l0 + k * breite / n) % breite;
            lambda[k] = l;
            rayleigh[k] = Spektrum.rayleigh(l, Atmosphere.RAYLEIGH_H);
            ozon[k] = Spektrum.OZON_DICHTE * Spektrum.ozon(l);
            aWasser[k] = Spektrum.wasser(l) + Wasseroptik.A_STERN_443 * w.wasser.schwebstoff * Math.exp(-Wasseroptik.S_NAP * (l - 443));
            double s = Spektrum.sonne(l);
            sonne[k] = s;
            mond[k] = s * Spektrum.reflexion(mondGewichte, l);
            nacht[k] = s * nachtFaktor;
        }
    }

    /** Albedo je Bahn aus linearem RGB: spektral nach Mallett und Yuksel, sonst der Kanal. */
    public void albedo(double[] rgb, double[] aus) {
        if (!spektral) {
            System.arraycopy(rgb, 0, aus, 0, 3);
            return;
        }
        double[] g = Spektrum.gewichte(rgb);
        for (int k = 0; k < n; k++) aus[k] = Spektrum.reflexion(g, lambda[k]);
    }

    /**
     * Seit P12c3: Strahldichte je Bahn einer leuchtenden Fläche aus linearem RGB (cd/m²): spektral die Form des
     * Sonnenspektrums mal der Reflexion nach Mallett und Yuksel der Farbe (wie das Licht der Nacht, {@link #nacht}), sonst
     * der Kanal.
     */
    public void leuchten(double[] rgb, double[] aus) {
        if (!spektral) {
            System.arraycopy(rgb, 0, aus, 0, 3);
            return;
        }
        double m = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
        if (!(m > 0)) {
            java.util.Arrays.fill(aus, 0, n, 0);
            return;
        }
        double[] g = Spektrum.gewichte(new double[] {rgb[0] / m, rgb[1] / m, rgb[2] / m});
        double f = m / Spektrum.sonneLux();
        for (int k = 0; k < n; k++) aus[k] = sonne[k] * f * Spektrum.reflexion(g, lambda[k]);
    }

    /**
     * Beitrag der Bahnen zu XYZ (cd/m²) bzw. RGB: spektral K_m · Bereich / n · Σ L ȳ …, sonst L je Kanal. {@code aus}: drei
     * Werte, zu denen addiert wird.
     */
    public void sammle(double[] L, double[] aus) {
        if (!spektral) {
            for (int c = 0; c < 3; c++) aus[c] += L[c];
            return;
        }
        double f = Spektrum.KM * (Spektrum.MAX - Spektrum.MIN) / n;
        for (int k = 0; k < n; k++) {
            if (L[k] == 0) continue;
            double l = lambda[k];
            aus[0] += f * L[k] * Spektrum.xBar(l);
            aus[1] += f * L[k] * Spektrum.yBar(l);
            aus[2] += f * L[k] * Spektrum.zBar(l);
        }
    }
}
