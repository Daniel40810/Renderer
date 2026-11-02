package com.dan.succession;

import java.util.Locale;

/**
 * Was die Strömung mit einem Baum macht (seit LituyaBay1958 Phase 14, Werkbuch 9.7). Kraft nach
 * F = ½ ρ<sub>w</sub> C<sub>D</sub> A u², Biegemoment am Stammfuß aus dem Stamm (bis zum Kronenansatz) und der Krone
 * (Fläche über die Kronenhöhe verteilt, wächst mit H²); tragfähig ist der Stamm mit σ<sub>b</sub>·π d³/32, die
 * Wurzelplatte mit einem Anteil davon (auf steilem Fels mit dünnem Boden weniger, dort wird entwurzelt statt gebrochen).
 * <ul>
 * <li>Moment unter der Tragfähigkeit: der Baum steht; bei schneller, tiefer Strömung entrindet ihn das Geröll im Wasser
 * (er stirbt).</li>
 * <li>darüber: gebrochen (Stumpf) oder umgelegt (entwurzelt, in Fließrichtung); ein umgelegter Stamm in tiefem Wasser
 * schwimmt fort.</li>
 * <li>sehr tiefe und schnelle Strömung räumt den Boden bis zum Fels ab, der Baum ist fort.</li>
 * </ul>
 * Die Geschwindigkeit über Land kennt die Vorrechnung nicht; sie kommt aus der Tiefe, u = Fr·√(g·h) [G]. Wo die Krone ins
 * Wasser reicht, bricht jeder Baum (9.7: u<sub>krit</sub> ≈ 1,4 m/s); darunter entscheidet der Stamm. Daher die scharfe
 * Spurlinie.
 */
public final class Wellenwurf {
    public static final double RHO_W = 1000, G = 9.81;
    /** Widerstandsbeiwert (durchströmt), Bruchspannung grünes Holz (Pa, Sitka-Fichte nach WOODHB [zu prüfen]). */
    public static double cd = 1.0, sigma = 40e6;
    /** Wurzelplatte: Anteil der Tragfähigkeit des Stamms; auf tiefem Boden hält sie mehr (der Stamm bricht), auf Hängen über {@link #steil}° mit dünnem Boden weniger (er wird entwurzelt) [G]. */
    public static double wurzel = 1.2, wurzelSteil = 0.6, steil = 30;
    /** Abgeräumt bis zum Fels ab dieser Tiefe (m) und Geschwindigkeit (m/s) [G]. */
    public static double abraumTiefe = 20, abraumU = 20;
    /** Entrindet durch Geröll im Wasser ab dieser Geschwindigkeit (m/s) und Tiefe (m) [G]. */
    public static double rindeU = 4, rindeTiefe = 1;

    public enum Folge {
        UNBERUEHRT("unberührt"), STEHT("steht"), ENTRINDET("entrindet"), GEBROCHEN("gebrochen"), UMGELEGT("umgelegt"), FORT("fort");
        public final String text;
        Folge(String t) { text = t; }
        @Override public String toString() { return text; }
    }

    /** Folge, Boden danach, Verhältnis Moment zu Tragfähigkeit, Geschwindigkeit. */
    public record Schaden(Folge folge, Boden boden, double last, double u) {
        @Override public String toString() { return String.format(Locale.GERMAN, "%s, %s (Last %.2f, %.1f m/s)", folge, boden, last, u); }
    }

    private Wellenwurf() { }

    /** Geschwindigkeit über Land aus der Wassertiefe: Fr·√(g·h). */
    public static double geschwindigkeit(double tiefe, double froude) { return tiefe <= 0 ? 0 : froude * Math.sqrt(G * tiefe); }

    /**
     * Kritische Geschwindigkeit (9.7): bei ihr erreicht das Moment der ganz erfassten Krone (Fläche A, Hebel h<sub>c</sub>)
     * die Tragfähigkeit des Stamms (Durchmesser d).
     */
    public static double uKrit(double d, double a, double hc) { return Math.sqrt(2 * sigma * Math.PI * d * d * d / 32 / (RHO_W * cd * a * hc)); }

    /** Biegemoment am Stammfuß (Nm) bei Tiefe und Geschwindigkeit für einen Baum der Höhe h. */
    public static double moment(double tiefe, double u, double h, Art a) {
        if (tiefe <= 0 || u <= 0) return 0;
        double d = a.stamm * h, cb = a.kroneAb * h, q = 0.5 * RHO_W * cd * u * u;
        double zt = Math.min(tiefe, cb);
        double m = q * d * zt * zt / 2;
        double ak = a.krone * (h / 40) * (h / 40), lk = Math.max(1e-6, h - cb);
        double zc = Math.max(0, Math.min(tiefe, h) - cb);
        m += q * ak / lk * zc * (cb + zc / 2);
        return m;
    }

    /** Tragfähigkeit des Stamms (Nm). */
    public static double tragfaehig(double h, Art a) { double d = a.stamm * h; return sigma * Math.PI * d * d * d / 32; }

    /**
     * Was mit einem Baum (Höhe h, Art a) bei Tiefe tiefe und Geschwindigkeit u geschieht; hang in Grad. Ohne Wasser
     * unberührt, sonst steht er, wird entrindet, gebrochen, umgelegt oder ist fort (siehe Klasse).
     */
    public static Schaden wurf(double tiefe, double u, double h, Art a, double hang) {
        if (!(tiefe > 0)) return new Schaden(Folge.UNBERUEHRT, Boden.WALDBODEN, 0, 0);
        if (tiefe >= abraumTiefe && u >= abraumU) return new Schaden(Folge.FORT, Boden.FELS, Double.POSITIVE_INFINITY, u);
        double mc = tragfaehig(h, a), mr = mc * (hang > steil ? wurzelSteil : wurzel);
        double last = moment(tiefe, u, h, a) / Math.min(mc, mr);
        if (last >= 1) {
            if (mr < mc) return new Schaden(tiefe > 0.5 * h ? Folge.FORT : Folge.UMGELEGT, Boden.MINERAL, last, u);
            return new Schaden(Folge.GEBROCHEN, Boden.MINERAL, last, u);
        }
        if (u >= rindeU && tiefe >= rindeTiefe) return new Schaden(Folge.ENTRINDET, Boden.WALDBODEN, last, u);
        return new Schaden(Folge.STEHT, Boden.WALDBODEN, last, u);
    }
}
