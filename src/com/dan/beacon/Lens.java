package com.dan.beacon;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Eine dioptrische Gürtellinse (Fresnel) im Schnitt: Prismenringe übereinander, jeder mit senkrechter Innenfläche und
 * schräger Außenfläche, die das Licht aus dem Brennpunkt waagerecht machen; dazwischen Messingrahmen, die nichts
 * durchlassen. Darunter der Linsensockel, darüber die Deckplatte mit einer Öffnung in der Mitte.
 * <p>
 * {@link #trace} schickt Strahlen von einer leuchtenden Kugel (Glühfaden oder Lichtbogen) in der Linsenmitte durch das
 * Glas, mit Brechung nach Snellius, Reflexionsverlust nach Fresnel und Totalreflexion. Heraus kommt die
 * Lichtverteilung in der Ferne (Lichtstärke über dem Höhenwinkel) und in der Nähe (wie hell jeder Ort im Laternenraum
 * angestrahlt wird, mit den Streifen der Prismenringe, dem Lichtring auf den Dielen und dem Fleck an der Kuppel).
 * Gerechnet wird im Meridianschnitt: Für eine Trommellinse um eine senkrechte Achse ist das genau; eine Ringlinse
 * (Bullauge) einer Drehlinse ist um ihre waagerechte Achse genauso gebaut, darum gilt derselbe Schnitt für beide
 * Richtungen.
 * <p>
 * Lage: x = Abstand von der Achse (m), y = Höhe über der Lampenmitte (m).
 */
public final class Lens {
    /** Brechzahl von Kronglas. */
    public static final double N_GLASS = 1.52;

    public final double focal, halfHeight, outer;
    /**
     * Die Laterne um die Linse: Halbmesser des Glases und seine Unter- und Oberkante über der Lampenmitte (m). Ins
     * Fernfeld kommt nur, was durchs Glas geht; was darüber oder darunter auftrifft, fängt Kuppel oder Brüstung (es
     * bleibt im Nahfeld). Vorgabe wie im Roten Sand: r 1,85 m, −0,835 bis +1,265 m.
     */
    public volatile double glassRadius = 1.85, glassBottom = -0.835, glassTop = 1.265;
    public final int rings;
    /** Prismen als Vielecke {x, y, …}: innen unten, innen oben, dann die gekrümmte Außenfläche von oben nach unten. */
    final List<double[]> prisms = new ArrayList<>();
    /** Undurchsichtige Teile (Sockel, Deckplatte) als Strecken {x0, y0, x1, y1}. */
    final List<double[]> stops = new ArrayList<>();

    private Lens(double focal, double halfHeight, int rings) {
        this.focal = focal;
        this.halfHeight = halfHeight;
        this.rings = rings;
        double maxX = focal;
        double h = 2 * halfHeight / rings;
        int m = 8;
        for (int k = 0; k < rings; k++) {
            double ya = -halfHeight + k * h, yb = ya + h, yc = (ya + yb) / 2;
            // Außenfläche als Kurve x(y): An jeder Stelle steht ihre Normale so, dass der Strahl, der dort aus dem
            // Brennpunkt ankommt, waagerecht austritt (n·d1 − x̂ ∥ Normale). Von der Mitte aus nach oben und unten integriert.
            double t0 = 0.02;
            double[] xs = new double[m + 1];
            for (int pass = 0; pass < 2; pass++) {
                int steps = 64;
                double[] xf = new double[steps + 1];
                int mid = steps / 2;
                xf[mid] = focal + t0;
                for (int dir = -1; dir <= 1; dir += 2) {
                    for (int i = mid; i != (dir > 0 ? steps : 0); i += dir) {
                        double y = ya + (yb - ya) * i / steps, x = xf[i];
                        double s1 = faceSlope(focal, x, y), yn = ya + (yb - ya) * (i + dir) / steps;
                        double s2 = faceSlope(focal, x + s1 * (yn - y), yn);
                        xf[i + dir] = x + 0.5 * (s1 + s2) * (yn - y);
                    }
                }
                double mn = Double.MAX_VALUE;
                for (double v : xf) mn = Math.min(mn, v);
                if (pass == 0) { t0 += focal + 0.008 - mn; continue; }
                for (int j = 0; j <= m; j++) xs[j] = xf[j * steps / m];
            }
            // Viereck: innen unten, innen oben, dann die Außenkurve von oben nach unten
            double[] poly = new double[4 + 2 * (m + 1)];
            poly[0] = focal; poly[1] = ya; poly[2] = focal; poly[3] = yb;
            for (int j = 0; j <= m; j++) {
                int src = m - j;
                poly[4 + 2 * j] = xs[src];
                poly[5 + 2 * j] = ya + (yb - ya) * src / m;
                maxX = Math.max(maxX, xs[src]);
            }
            prisms.add(poly);
        }
        this.outer = maxX;
        // Was nicht durchlässt, wie im Modell der App: der Sockel (Platte r 0,40 m unter der Linse), die Messingringe
        // unten und oben (r 0,44–0,50 m) und die Deckplatte, die zur Mitte hin eine Öffnung von 0,12 m lässt
        double h0 = halfHeight;
        stops.add(new double[]{0, -h0 - 0.08, 0.40, -h0 - 0.08});
        stops.add(new double[]{0.44, -h0 - 0.06, 0.50, -h0 - 0.06});
        stops.add(new double[]{0.50, -h0 - 0.06, 0.50, -h0 - 0.01});
        stops.add(new double[]{0.50, -h0 - 0.01, 0.44, -h0 - 0.01});
        stops.add(new double[]{0.44, h0 + 0.01, 0.50, h0 + 0.01});
        stops.add(new double[]{0.50, h0 + 0.01, 0.50, h0 + 0.06});
        stops.add(new double[]{0.50, h0 + 0.06, 0.12, h0 + 0.08});
    }

    /**
     * Trommellinse 4. Ordnung wie im Laternenraum des Roten Sands: Brennweite 0,39 m, ±0,415 m hoch (±47° um die
     * Lampe), 25 Prismenringe.
     */
    public static Lens drum() { return new Lens(0.39, 0.415, 25); }

    public static Lens of(double focal, double halfHeight, int rings) { return new Lens(focal, halfHeight, rings); }

    /** Die Prismen (Kopien) für Zeichnung und Modell. */
    public List<double[]> prisms() {
        List<double[]> l = new ArrayList<>();
        for (double[] p : prisms) l.add(p.clone());
        return l;
    }

    /** Höchster Einfallswinkel, den die Linse noch fasst (Grad). */
    public double acceptanceDeg() { return Math.toDegrees(Math.atan(halfHeight / focal)); }

    // ------------------------------------------------------------------ Strahlverfolgung

    /**
     * Ergebnis einer Verfolgung. Fernfeld: Lichtstärke je Lumen der Lampe (cd/lm) über dem Höhenwinkel ε von
     * {@code farMin} in Schritten {@code farStep} (Grad). Nahfeld: Lichtstrom je Meter Höhe und je Radiant Azimut
     * (lm/(m·rad) je Lumen), der den Zylinder mit Halbmesser r in der Höhe y kreuzt ({@code nearF}), dazu die mittlere
     * Steigung dy/dx der Strahlen dort ({@code nearS}); Gitter nr × ny über r0..r1 und y0..y1.
     */
    public record Field(double[] far, double farMin, double farStep, float[] nearF, float[] nearS, int nr, int ny,
                        double r0, double r1, double y0, double y1, double lampRadius, double collimated, double stray,
                        double absorbed, double fwhmDeg, double peakCdPerLm, double meanExitErrorDeg) {

        /** Lichtstärke je Lumen beim Höhenwinkel ε (Grad), linear zwischen den Stufen. */
        public double cdPerLm(double epsDeg) {
            double u = (epsDeg - farMin) / farStep;
            if (u < 0 || u > far.length - 1) return 0;
            int i = (int) Math.floor(u);
            double f = u - i;
            return i + 1 < far.length ? far[i] * (1 - f) + far[i + 1] * f : far[i];
        }

        /** Verteilung über ε, auf 1 im Höchstwert bezogen. */
        public double relative(double epsDeg) { return cdPerLm(epsDeg) / peakCdPerLm; }

        /** Nahfeld-Fluss F (lm/(m·rad) je lm) und Steigung bei (r, y), bilinear. */
        public double[] near(double r, double y) {
            double u = (r - r0) / (r1 - r0) * (nr - 1), v = (y - y0) / (y1 - y0) * (ny - 1);
            if (u < 0 || v < 0 || u > nr - 1 || v > ny - 1) return new double[]{0, 0};
            int i = Math.min(nr - 2, (int) u), j = Math.min(ny - 2, (int) v);
            double fu = u - i, fv = v - j;
            double f = lerp(lerp(nearF[j * nr + i], nearF[j * nr + i + 1], fu), lerp(nearF[(j + 1) * nr + i], nearF[(j + 1) * nr + i + 1], fu), fv);
            double s = lerp(lerp(nearS[j * nr + i], nearS[j * nr + i + 1], fu), lerp(nearS[(j + 1) * nr + i], nearS[(j + 1) * nr + i + 1], fu), fv);
            return new double[]{f, s};
        }
    }

    static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    /**
     * Verfolgt n Strahlen einer leuchtenden Kugel (Halbmesser lampRadius in m) in die vordere Hälfte (±90°); jeder trägt
     * (1 lm / 4π) · cos φ · Δφ je Radiant Azimut. Gleicher seed, gleiches Ergebnis.
     */
    public Field trace(double lampRadius, int n, long seed) {
        String key = focal + "|" + halfHeight + "|" + rings + "|" + glassRadius + "|" + glassBottom + "|" + glassTop + "|" + lampRadius + "|" + n + "|" + seed;
        synchronized (TRACED) {
            Field f = TRACED.get(key);
            if (f != null) return f;
        }
        Field f = traceUncached(lampRadius, n, seed);
        synchronized (TRACED) { TRACED.put(key, f); }
        return f;
    }

    /**
     * Seit Timmendorf Phase 9: gleiche Linse, gleicher Glühfaden, gleiche Strahlen ergeben dasselbe Feld; die letzten 16
     * Felder bleiben im Speicher, damit eine Szene mit vielen Feuern nicht für jedes neu verfolgt (rund 0,7 s je Feld).
     */
    private static final java.util.Map<String, Field> TRACED = new java.util.LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Field> e) { return size() > 16; }
    };

    private Field traceUncached(double lampRadius, int n, long seed) {
        Random rnd = new Random(seed);
        double farMin = -90, farStep = 0.05;
        int farN = (int) Math.round(180 / farStep) + 1;
        double[] far = new double[farN];
        int nr = 72, ny = 220;
        double r0 = 0.30, r1 = 4.6, y0 = -1.6, y1 = 3.9;
        double dy = (y1 - y0) / (ny - 1), dr = (r1 - r0) / (nr - 1);
        double[] nf = new double[nr * ny], ns = new double[nr * ny];
        double total = 0, coll = 0, stray = 0, absorbed = 0, errSum = 0, errW = 0;
        double dPhi = Math.PI / n;
        for (int s = 0; s < n; s++) {
            double phi = -Math.PI / 2 + (s + rnd.nextDouble()) * dPhi;
            double off = (rnd.nextDouble() * 2 - 1) * lampRadius;
            double w = Math.cos(phi) * dPhi / (4 * Math.PI);
            total += w;
            double[] d = {Math.cos(phi), Math.sin(phi)};
            double[] o = {-Math.sin(phi) * off, Math.cos(phi) * off};
            double[] res = run(o, d);
            if (res == null) { absorbed += w; continue; }
            w *= res[4];
            double ox = res[0], oy = res[1], dx = res[2], dyy = res[3];
            if (res[5] > 0) {
                coll += Math.abs(Math.toDegrees(Math.atan2(dyy, dx))) < 5 ? w : 0;
                double e = Math.toDegrees(Math.atan2(dyy, dx));
                if (lampRadius == 0) { errSum += Math.abs(e) * w; errW += w; }
            } else stray += w;
            // Fernfeld (nur Strahlen nach außen)
            if (dx > 1e-6) {
                double eps = Math.toDegrees(Math.atan2(dyy, dx));
                int b = (int) Math.round((eps - farMin) / farStep);
                double yg = oy + (glassRadius - ox) * dyy / dx;
                if (b >= 0 && b < farN && yg >= glassBottom && yg <= glassTop) far[b] += w;
                // Nahfeld: wo der Strahl die Zylinder kreuzt
                double sl = dyy / dx;
                for (int i = 0; i < nr; i++) {
                    double r = r0 + i * dr;
                    if (r < ox - 1e-9) continue;
                    double y = oy + (r - ox) * sl;
                    double v = (y - y0) / dy;
                    int j = (int) Math.floor(v);
                    if (j < 0 || j >= ny - 1) continue;
                    double fv = v - j;
                    nf[j * nr + i] += w * (1 - fv);
                    nf[(j + 1) * nr + i] += w * fv;
                    ns[j * nr + i] += w * (1 - fv) * sl;
                    ns[(j + 1) * nr + i] += w * fv * sl;
                }
            }
        }
        // Fernfeld: Fluss je Bin → Lichtstärke je lm (je sr: durch Δε in rad und cos ε), leicht geglättet
        double[] cd = new double[farN];
        double dEps = Math.toRadians(farStep);
        for (int i = 0; i < farN; i++) {
            double eps = Math.toRadians(farMin + i * farStep);
            cd[i] = far[i] / (dEps * Math.max(0.02, Math.cos(eps)));
        }
        cd = smooth(cd, 5);
        // Höchstwert aus der stärker geglätteten Kurve (das Rauschen der Stichprobe hebt sonst einzelne Stufen heraus)
        double[] wide = smooth(cd, 10);
        double peak = 0;
        int pi = 0;
        for (int i = 0; i < farN; i++) if (wide[i] > peak) { peak = wide[i]; pi = i; }
        int a = pi, b = pi;
        while (a > 0 && cd[a] > peak / 2) a--;
        while (b < farN - 1 && cd[b] > peak / 2) b++;
        double fwhm = (b - a) * farStep;
        float[] F = new float[nr * ny], S = new float[nr * ny];
        for (int k = 0; k < nr * ny; k++) {
            F[k] = (float) (nf[k] / dy);
            S[k] = nf[k] > 1e-12 ? (float) (ns[k] / nf[k]) : 0f;
        }
        return new Field(cd, farMin, farStep, F, S, nr, ny, r0, r1, y0, y1, lampRadius, coll / total, stray / total,
                Math.max(0, 1 - (coll + stray) / total), fwhm, peak, errW > 0 ? errSum / errW : Double.NaN);
    }

    static double sumAll(double[] a) { double s = 0; for (double v : a) s += v; return s; }

    static double[] smooth(double[] a, int r) {
        double[] o = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            double s = 0, w = 0;
            for (int k = -r; k <= r; k++) {
                int j = i + k;
                if (j < 0 || j >= a.length) continue;
                double g = Math.exp(-k * k / (0.5 * r * r + 1e-9));
                s += a[j] * g;
                w += g;
            }
            o[i] = s / w;
        }
        return o;
    }

    /**
     * Ein Strahl durch die Linse. Gibt {x, y, dx, dy, Gewicht, durchs Glas (1/0)} des austretenden Strahls zurück,
     * oder null, wenn er an Messing oder Sockel endet.
     */
    double[] run(double[] o, double[] d) { return run(o, d, null); }

    /** Der Weg eines Strahls als Punktfolge bis zum Halbmesser maxR (zum Zeichnen). Mehr als zwei Punkte: durchs Glas. */
    public static List<double[]> path(Lens lens, double[] o, double[] d, double maxR) {
        List<double[]> pts = new ArrayList<>();
        pts.add(o.clone());
        double[] r = lens.run(o, d, pts);
        if (r != null) {
            double t = r[2] > 1e-6 ? (maxR - r[0]) / r[2] : 2;
            pts.add(new double[]{r[0] + r[2] * t, r[1] + r[3] * t});
        }
        return pts;
    }

    double[] run(double[] o, double[] d, List<double[]> pts) {
        double ox = o[0], oy = o[1], dx = d[0], dy = d[1], w = 1;
        int inside = -1;
        boolean glass = false;
        for (int bounce = 0; bounce < 12; bounce++) {
            double best = Double.MAX_VALUE;
            int bp = -1, be = -1;
            boolean stop = false;
            for (int p = 0; p < prisms.size(); p++) {
                if (inside >= 0 && p != inside) continue;
                double[] q = prisms.get(p);
                int ne = q.length / 2;
                for (int e = 0; e < ne; e++) {
                    double ax = q[e * 2], ay = q[e * 2 + 1], bx = q[((e + 1) % ne) * 2], by = q[((e + 1) % ne) * 2 + 1];
                    double t = hit(ox, oy, dx, dy, ax, ay, bx, by);
                    if (t > 1e-9 && t < best) { best = t; bp = p; be = e; stop = false; }
                }
            }
            if (inside < 0) {
                for (double[] sgm : stops) {
                    double t = hit(ox, oy, dx, dy, sgm[0], sgm[1], sgm[2], sgm[3]);
                    if (t > 1e-9 && t < best) { best = t; stop = true; bp = -1; }
                }
            }
            if (best == Double.MAX_VALUE) return inside >= 0 ? null : new double[]{ox, oy, dx, dy, w, glass ? 1 : 0};
            if (stop) { if (pts != null) pts.add(new double[]{ox + dx * best, oy + dy * best}); return null; }
            ox += dx * best;
            oy += dy * best;
            if (pts != null) pts.add(new double[]{ox, oy});
            double[] q = prisms.get(bp);
            int ne = q.length / 2;
            // Ober- und Unterkante (Messingrahmen) schlucken
            if (be == 1 || be == ne - 1) return null;
            double ax = q[be * 2], ay = q[be * 2 + 1], bx = q[((be + 1) % ne) * 2], by = q[((be + 1) % ne) * 2 + 1];
            // Normale der Kante, zum Strahl hin gerichtet
            double nx = by - ay, ny = -(bx - ax), nl = Math.hypot(nx, ny);
            nx /= nl;
            ny /= nl;
            if (nx * dx + ny * dy > 0) { nx = -nx; ny = -ny; }
            double eta = inside < 0 ? 1 / N_GLASS : N_GLASS;
            double cosi = -(nx * dx + ny * dy);
            double k = 1 - eta * eta * (1 - cosi * cosi);
            if (k < 0) {
                // Totalreflexion
                dx += 2 * cosi * nx;
                dy += 2 * cosi * ny;
                continue;
            }
            double cost = Math.sqrt(k);
            double n1 = inside < 0 ? 1 : N_GLASS, n2 = inside < 0 ? N_GLASS : 1;
            double rs = (n1 * cosi - n2 * cost) / (n1 * cosi + n2 * cost), rp = (n2 * cosi - n1 * cost) / (n2 * cosi + n1 * cost);
            w *= 1 - 0.5 * (rs * rs + rp * rp);
            double tx = eta * dx + (eta * cosi - cost) * nx, ty = eta * dy + (eta * cosi - cost) * ny;
            double tl = Math.hypot(tx, ty);
            dx = tx / tl;
            dy = ty / tl;
            inside = inside < 0 ? bp : -1;
            glass = true;
        }
        return null;
    }

    /** Schnitt Strahl o + t·d mit der Strecke a–b; t oder −1. */
    static double hit(double ox, double oy, double dx, double dy, double ax, double ay, double bx, double by) {
        double ex = bx - ax, ey = by - ay;
        double den = dx * ey - dy * ex;
        if (Math.abs(den) < 1e-14) return -1;
        double t = ((ax - ox) * ey - (ay - oy) * ex) / den;
        double u = ((ax - ox) * dy - (ay - oy) * dx) / den;
        return u >= 0 && u <= 1 ? t : -1;
    }

    /**
     * Steigung dx/dy der Außenfläche am Ort (x, y): Der Strahl aus dem Brennpunkt trat bei y_in in die senkrechte
     * Innenfläche ein und läuft gebrochen bis hierher; die Fläche steht senkrecht zu n·d1 − x̂.
     */
    static double faceSlope(double focal, double x, double y) {
        double yin = y;
        double[] d1 = {1, 0};
        for (int it = 0; it < 6; it++) {
            d1 = refract(norm(focal, yin), new double[]{-1, 0}, 1 / N_GLASS);
            yin = y - (x - focal) * d1[1] / d1[0];
        }
        double nx = N_GLASS * d1[0] - 1, ny = N_GLASS * d1[1];
        return -ny / nx;
    }

    static double[] norm(double x, double y) { double l = Math.hypot(x, y); return new double[]{x / l, y / l}; }

    /** Brechung von d an der Fläche mit Normale n (gegen d gerichtet), eta = n1/n2. */
    static double[] refract(double[] d, double[] n, double eta) {
        double cosi = -(n[0] * d[0] + n[1] * d[1]);
        double k = 1 - eta * eta * (1 - cosi * cosi);
        double c = eta * cosi - Math.sqrt(Math.max(0, k));
        double x = eta * d[0] + c * n[0], y = eta * d[1] + c * n[1], l = Math.hypot(x, y);
        return new double[]{x / l, y / l};
    }
}
