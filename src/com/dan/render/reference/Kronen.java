package com.dan.render.reference;

/**
 * Gehölze für die Referenz als einfache Kronen (seit P12c3, Werkbuch HalligHooge Teil A, 12.13, Datei 81; F138 A): je
 * Gehölz ein Ellipsoid mit Laub als Dichte und ein Stamm als Zylinder. Die Maße kommen aus dem Bild
 * ({@code Bewuchsbild.kronen()}): Halbachsen wie die Netze im Bild, Höhe der Krone aus ihren Laubpunkten, Blattfläche je
 * Volumen aus der Fläche des Laubs am Tag (Laubfall), Farben aus den Netzen.
 * <ul>
 * <li><b>Laub:</b> Auslöschung σ = G · Blattfläche je Volumen mit G = 0,5 (Blätter in allen Richtungen); ein Stoß im
 * Ellipsoid ist ein Treffer {@link Szene#BLATT} mit einer Normale gleichverteilt auf der Kugel (zum Strahl gewandt),
 * Durchlass 0,35 wie {@code baum.frag}. Der Weg bis zum Stoß kommt aus einem Zufall, der nur vom Strahl und vom Gehölz
 * abhängt – die Referenz bleibt bei gleicher Saat wiederholbar.</li>
 * <li><b>Stamm:</b> Zylinder vom Boden bis zur Mitte der Krone, Rinde nach Lambert.</li>
 * <li>Lage relativ zum Auge, gesenkt um d² / 2 R_eff an der Mitte des Gehölzes.</li>
 * </ul>
 */
public final class Kronen {
    public static final double G = 0.5, DURCHLASS = 0.35;

    final int n;
    /** je Gehölz: Mitte x, y, z (relativ zum Auge), Halbachsen ax, ay, az, σ; Stamm: Fuß y, Höhe, Halbmesser; Laub, Rinde. */
    final double[] e, stamm;
    final float[] laub, rinde;
    final Huellbaum baum;

    /**
     * @param k    je Gehölz die 15 Werte aus {@code Bewuchsbild.kronen()} (null: fehlt)
     * @param e0   Ursprung der Bauten (Ost), n0 (Nord)
     * @param ost  Auge (UTM), nord, hAuge (m NHN), kr: Kehrwert von 2 R_eff
     */
    public Kronen(float[][] k, double e0, double n0, double ost, double nord, double hAuge, double kr) {
        int z = 0;
        for (float[] q : k) if (q != null) z++;
        n = z;
        e = new double[7 * n];
        stamm = new double[3 * n];
        laub = new float[3 * n];
        rinde = new float[3 * n];
        double[] lo = new double[3 * n], hi = new double[3 * n];
        int i = 0;
        for (float[] q : k) {
            if (q == null) continue;
            double x = q[0] + e0 - ost, zz = q[1] + nord - n0;
            double senk = kr * (x * x + zz * zz);
            double fuss = q[2] - hAuge - senk;
            double y0 = q[5], y1 = Math.max(q[5] + 0.2, q[6]);
            e[7 * i] = x;
            e[7 * i + 1] = fuss + 0.5 * (y0 + y1);
            e[7 * i + 2] = zz;
            e[7 * i + 3] = Math.max(0.1, q[3]);
            e[7 * i + 4] = 0.5 * (y1 - y0);
            e[7 * i + 5] = Math.max(0.1, q[4]);
            e[7 * i + 6] = G * q[7];
            stamm[3 * i] = fuss;
            stamm[3 * i + 1] = 0.5 * (y0 + y1);
            stamm[3 * i + 2] = Math.max(0.02, q[8]);
            for (int c = 0; c < 3; c++) {
                laub[3 * i + c] = q[9 + c];
                rinde[3 * i + c] = q[12 + c];
            }
            for (int a = 0; a < 3; a++) {
                double r = e[7 * i + 3 + a];
                lo[3 * i + a] = e[7 * i + a] - r;
                hi[3 * i + a] = e[7 * i + a] + r;
            }
            lo[3 * i + 1] = Math.min(lo[3 * i + 1], fuss);
            i++;
        }
        baum = new Huellbaum(lo, hi);
    }

    public int zahl() { return n; }

    /** Zufall 0 … 1 aus dem Strahl und dem Gehölz (wiederholbar, unabhängig vom Faden). */
    static double zufall(double[] o, double[] d, int i, int salz) {
        long h = 0x9E3779B97F4A7C15L * (i + 1) + salz;
        for (int a = 0; a < 3; a++) {
            h ^= Double.doubleToLongBits(o[a]);
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 31;
            h ^= Double.doubleToLongBits(d[a]);
            h *= 0x94D049BB133111EBL;
            h ^= h >>> 29;
        }
        return ((h >>> 11) + 0.5) * 0x1.0p-53;
    }

    /** Ein Gehölz: Weg des Stoßes im Laub oder zum Stamm (+∞ ohne); art[0]: 1 Laub, 2 Stamm. */
    double gehoelz(int i, double[] o, double[] d, double tMax, int[] art) {
        double best = Double.POSITIVE_INFINITY;
        // Stamm: senkrechter Zylinder
        double sx = o[0] - e[7 * i], sz = o[2] - e[7 * i + 2], r = stamm[3 * i + 2];
        double a = d[0] * d[0] + d[2] * d[2];
        if (a > 1e-18) {
            double b = sx * d[0] + sz * d[2], c = sx * sx + sz * sz - r * r, D = b * b - a * c;
            if (D >= 0) {
                double sq = Math.sqrt(D);
                for (double t : new double[] {(-b - sq) / a, (-b + sq) / a}) {
                    if (!(t > 1e-9) || !(t < tMax) || t >= best) continue;
                    double y = o[1] + t * d[1];
                    if (y >= stamm[3 * i] && y <= stamm[3 * i] + stamm[3 * i + 1]) {
                        best = t;
                        art[0] = 2;
                        break;
                    }
                }
            }
        }
        // Laub: Ellipsoid, Stoß nach der Auslöschung
        double ax = e[7 * i + 3], ay = e[7 * i + 4], az = e[7 * i + 5], sig = e[7 * i + 6];
        if (sig > 0) {
            double px = (o[0] - e[7 * i]) / ax, py = (o[1] - e[7 * i + 1]) / ay, pz = (o[2] - e[7 * i + 2]) / az;
            double qx = d[0] / ax, qy = d[1] / ay, qz = d[2] / az;
            double A = qx * qx + qy * qy + qz * qz, B = px * qx + py * qy + pz * qz, C = px * px + py * py + pz * pz - 1, D = B * B - A * C;
            if (D > 0) {
                double sq = Math.sqrt(D), t0 = Math.max(0, (-B - sq) / A), t1 = Math.min(Math.min(tMax, best), (-B + sq) / A);
                if (t1 > t0) {
                    double s = -Math.log(zufall(o, d, i, 1)) / sig;
                    if (t0 + s < t1) {
                        best = t0 + s;
                        art[0] = 1;
                    }
                }
            }
        }
        return best;
    }

    /** Nächster Treffer bis tMax; füllt aus und gibt true. */
    public boolean schneide(double[] o, double[] d, double tMax, Szene.Treffer aus) {
        int[] welches = {-1, 0};
        int[] art = new int[1];
        double t = baum.schneide(o, d, tMax, (i, tm) -> {
            double ti = gehoelz(i, o, d, tm, art);
            if (ti < tm) {
                welches[0] = i;
                welches[1] = art[0];
            }
            return ti;
        });
        if (welches[0] < 0 || !(t < tMax)) return false;
        int i = welches[0];
        aus.t = t;
        aus.sigma2 = 0;
        aus.spiegel = 0;
        aus.leuchtet = false;
        aus.aus = Szene.AUS_GEHOELZ;
        double x = o[0] + t * d[0], z = o[2] + t * d[2];
        if (welches[1] == 2) {
            aus.art = Szene.BODEN;
            double nx = x - e[7 * i], nz = z - e[7 * i + 2], l = Math.max(1e-12, Math.hypot(nx, nz));
            aus.n[0] = nx / l;
            aus.n[1] = 0;
            aus.n[2] = nz / l;
            for (int c = 0; c < 3; c++) aus.albedo[c] = rinde[3 * i + c];
        } else {
            aus.art = Szene.BLATT;
            aus.durchlass = DURCHLASS;
            // Normale gleichverteilt auf der Kugel, zum Strahl gewandt
            double u = zufall(o, d, i, 2), v = zufall(o, d, i, 3);
            double cz = 2 * u - 1, sr = Math.sqrt(Math.max(0, 1 - cz * cz)), ph = 2 * Math.PI * v;
            double nx = sr * Math.cos(ph), ny = sr * Math.sin(ph), nz = cz;
            if (nx * d[0] + ny * d[1] + nz * d[2] > 0) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            aus.n[0] = nx;
            aus.n[1] = ny;
            aus.n[2] = nz;
            for (int c = 0; c < 3; c++) aus.albedo[c] = laub[3 * i + c];
        }
        return true;
    }
}
