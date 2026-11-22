package com.dan.render.reference;

/** Kleine Vektorrechnung in double für die Referenz. */
final class Vek {
    private Vek() { }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    static double[] norm(double[] a) {
        double n = Math.sqrt(dot(a, a));
        return new double[] {a[0] / n, a[1] / n, a[2] / n};
    }

    static double[] neg(double[] a) { return new double[] {-a[0], -a[1], -a[2]}; }

    static double[] plus(double[] p, double t, double[] d) { return new double[] {p[0] + t * d[0], p[1] + t * d[1], p[2] + t * d[2]}; }

    static double[] kreuz(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** Zwei Einheitsvektoren senkrecht zu n (Einheitsvektor) und zueinander (Duff u. a. 2017). */
    static double[][] basis(double[] n) {
        double s = Math.copySign(1.0, n[2]), a = -1 / (s + n[2]), b = n[0] * n[1] * a;
        return new double[][] {{1 + s * n[0] * n[0] * a, s * b, -s * n[0]}, {b, s + n[1] * n[1] * a, -n[1]}};
    }

    /** Spiegelung von d (Richtung, die auf die Fläche zuläuft) an m. */
    static double[] spiegle(double[] d, double[] m) {
        double c = 2 * dot(d, m);
        return new double[] {d[0] - c * m[0], d[1] - c * m[1], d[2] - c * m[2]};
    }

    /**
     * Brechung von d (Einheitsvektor, auf die Fläche zu) an m mit eta = n_vorher / n_nachher; null bei Totalreflexion.
     * m darf zu beiden Seiten zeigen.
     */
    static double[] breche(double[] d, double[] m, double eta) {
        double c = -dot(d, m);
        double[] mm = m;
        if (c < 0) {
            c = -c;
            mm = neg(m);
        }
        double k = 1 - eta * eta * (1 - c * c);
        if (k < 0) return null;
        double f = eta * c - Math.sqrt(k);
        return new double[] {eta * d[0] + f * mm[0], eta * d[1] + f * mm[1], eta * d[2] + f * mm[2]};
    }
}
