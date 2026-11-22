package com.dan.render.reference;

import java.util.SplittableRandom;

/**
 * Die Wasseroberfläche als raues Dielektrikum nach Walter u. a. (2007) mit der Verteilung der Neigungen nach Cox und
 * Munk (P7b, F53 A): Beckmann mit α² = σ² (σ² die mittlere quadratische Neigung beider Richtungen, wie
 * {@code Wasseroptik.coxMunk}), Schattung nach Smith (Näherung von Walter, Gl. 27), Fresnel unpolarisiert. Brechzahl
 * gleich für alle Wellenlängen ({@code Wasseroptik.n}, 1,34 [G]).
 * <p>
 * Richtungen zeigen von der Fläche weg (o: zum vorigen Punkt des Pfads, i: zum nächsten). Die Gewichte sind die des
 * Pfads vom Auge her: Beim Übergang in das dichtere Wasser nimmt die Strahldichte um (n_o / n_i)² ab, also sieht das Auge
 * die n²-fache Strahldichte im Wasser durch 1/n² (Walter, Gl. 21).
 */
final class Facette {
    final double alpha2, eta;

    /** σ² (Cox und Munk) und Brechzahl des Wassers. */
    Facette(double sigma2, double eta) {
        this.alpha2 = Math.max(1e-8, sigma2);
        this.eta = eta;
    }

    /** Beckmann D(m) zur Achse n. */
    double d(double[] m, double[] n) {
        double c = Vek.dot(m, n);
        if (c <= 0) return 0;
        double c2 = c * c, t2 = (1 - c2) / c2;
        return Math.exp(-t2 / alpha2) / (Math.PI * alpha2 * c2 * c2);
    }

    /** Smith G1 (Walter, Gl. 27). */
    double g1(double[] v, double[] m, double[] n) {
        double vn = Vek.dot(v, n);
        if (Vek.dot(v, m) * vn <= 0) return 0;
        double c = Math.abs(vn), t = Math.sqrt(Math.max(0, 1 - c * c)) / c;
        if (t == 0) return 1;
        double a = 1 / (Math.sqrt(alpha2) * t);
        return a < 1.6 ? (3.535 * a + 2.181 * a * a) / (1 + 2.276 * a + 2.577 * a * a) : 1;
    }

    /** Eine Mikronormale nach D(m) |m·n| zur Achse n. */
    double[] ziehe(double[] n, SplittableRandom r) {
        double t2 = -alpha2 * Math.log(1 - r.nextDouble()), c = 1 / Math.sqrt(1 + t2);
        return Phasen.umAchse(n, c, r);
    }

    /** Dichte von {@link #ziehe}: D(m) |m·n|. */
    double dichte(double[] m, double[] n) { return d(m, n) * Math.abs(Vek.dot(m, n)); }

    /** Fresnel unpolarisiert für cos des Einfalls ci (≥ 0) und eta = n_einfall / n_durch; 1 bei Totalreflexion. */
    static double fresnel(double ci, double eta) {
        double s2 = eta * eta * (1 - ci * ci);
        if (s2 >= 1) return 1;
        double ct = Math.sqrt(1 - s2);
        double rs = (eta * ci - ct) / (eta * ci + ct), rp = (ci - eta * ct) / (ci + eta * ct);
        return 0.5 * (rs * rs + rp * rp);
    }

    /**
     * BSDF · |i·n| für die Strahldichte, die von i nach o läuft: f(i, o) |i·n|. nO: Brechzahl auf der Seite von o, nA auf
     * der anderen Seite; liegen i und o auf derselben Seite, ist es Spiegelung, sonst Brechung. n: Normale (Seite egal).
     */
    double wert(double[] i, double[] o, double[] n, double nO, double nA) {
        double in = Vek.dot(i, n), on = Vek.dot(o, n);
        double[] nn = on > 0 ? n : Vek.neg(n);
        if (in * on > 0) {                                                    // Spiegelung
            double[] h = Vek.norm(new double[] {i[0] + o[0], i[1] + o[1], i[2] + o[2]});
            if (Vek.dot(h, nn) < 0) h = Vek.neg(h);
            double f = fresnel(Math.abs(Vek.dot(o, h)), nO / nA);
            double g = g1(i, h, nn) * g1(o, h, nn);
            return f * g * d(h, nn) / (4 * Math.abs(on));
        }
        double ni = nA, no = nO;
        double[] h = Vek.norm(new double[] {-(ni * i[0] + no * o[0]), -(ni * i[1] + no * o[1]), -(ni * i[2] + no * o[2])});
        if (Vek.dot(h, nn) < 0) h = Vek.neg(h);
        double ih = Vek.dot(i, h), oh = Vek.dot(o, h);
        if (ih * oh >= 0) return 0;
        double f = fresnel(Math.abs(oh), no / ni);
        if (f >= 1) return 0;
        double g = g1(i, h, nn) * g1(o, h, nn), den = ni * ih + no * oh;
        return Math.abs(ih) * Math.abs(oh) / Math.abs(on) * no * no * (1 - f) * g * d(h, nn) / (den * den);
    }

    /**
     * Dichte (je sr), mit der {@link #ziehe(double[], double[], double, double, SplittableRandom)} die gespiegelte
     * Richtung i zu o gibt: F(o·h) D(h) |h·n| / (4 |o·h|) (seit P7c, für die Mischung mit der Keule zur Sonne).
     */
    double dichteSpiegel(double[] o, double[] i, double[] n, double no, double nd) {
        double[] nn = Vek.dot(o, n) >= 0 ? n : Vek.neg(n);
        double[] h = Vek.norm(new double[] {i[0] + o[0], i[1] + o[1], i[2] + o[2]});
        if (Vek.dot(h, nn) < 0) h = Vek.neg(h);
        double oh = Math.abs(Vek.dot(o, h));
        if (oh == 0) return 0;
        return fresnel(oh, no / nd) * d(h, nn) * Math.abs(Vek.dot(h, nn)) / (4 * oh);
    }

    /**
     * Dichte (je sr), mit der {@link #ziehe(double[], double[], double, double, SplittableRandom)} die Richtung i zu o
     * gibt, gespiegelt oder gebrochen (seit P7c). Gebrochen: (1 − F) D(h) |h·n| n_d² |i·h| / (n_o o·h + n_d i·h)².
     */
    double dichte(double[] o, double[] i, double[] n, double no, double nd) {
        if (Vek.dot(o, n) * Vek.dot(i, n) > 0) return dichteSpiegel(o, i, n, no, nd);
        double[] nn = Vek.dot(o, n) >= 0 ? n : Vek.neg(n);
        double[] h = Vek.norm(new double[] {-(nd * i[0] + no * o[0]), -(nd * i[1] + no * o[1]), -(nd * i[2] + no * o[2])});
        if (Vek.dot(h, nn) < 0) h = Vek.neg(h);
        double ih = Vek.dot(i, h), oh = Vek.dot(o, h);
        if (ih * oh >= 0) return 0;
        double f = fresnel(Math.abs(oh), no / nd);
        if (f >= 1) return 0;
        double den = nd * ih + no * oh;
        return (1 - f) * d(h, nn) * Math.abs(Vek.dot(h, nn)) * nd * nd * Math.abs(ih) / (den * den);
    }

    /**
     * Neue Richtung i zu o (Pfad vom Auge her) mit der Normalen n (zur Seite von o gedreht). Gibt {i, Gewicht, gebrochen
     * (1/0)} zurück oder null. no, ni_durch: Brechzahl auf der Seite von o und auf der anderen Seite.
     */
    double[][] ziehe(double[] o, double[] n, double no, double nd, SplittableRandom r) {
        double[] nn = Vek.dot(o, n) >= 0 ? n : Vek.neg(n);
        double[] m = ziehe(nn, r);
        double om = Vek.dot(o, m);
        if (om <= 0) return null;
        double f = fresnel(om, no / nd);
        double on = Math.abs(Vek.dot(o, nn)), mn = Vek.dot(m, nn);
        if (r.nextDouble() < f) {
            double[] i = Vek.spiegle(Vek.neg(o), m);
            if (Vek.dot(i, nn) <= 0) return null;
            double w = g1(i, m, nn) * g1(o, m, nn) * om / (on * mn);
            return new double[][] {i, {w}, {0}};
        }
        double[] i = Vek.breche(Vek.neg(o), m, no / nd);
        if (i == null || Vek.dot(i, nn) >= 0) return null;
        double w = g1(i, m, nn) * g1(o, m, nn) * om / (on * mn) * (no / nd) * (no / nd);
        return new double[][] {i, {w}, {1}};
    }
}
