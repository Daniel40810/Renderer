package com.dan.vessel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Der Bootsbauer (seit LituyaBay1958 W8a): baut aus einem {@link Troller} das Netz eines hölzernen Trollers mit allem,
 * was man aus der Nähe sieht – Kraweel-Rumpf aus denselben Spanten wie der Starrkörper ({@link Hull}), Verschanzung mit
 * Schandeck und Speigatten, Scheuerleiste, gewölbtes Deck aus Planken, Steuerhaus mit gerahmten Scheiben, Tür und
 * Steuerrad, Logis mit Bullaugen, Fischraumluke, Trollwinden, Mast mit Saling, Ladebaum und Lichtern, zwei Trollstangen
 * mit Schlingerdämpfern, Besan mit Stützsegel, Ankerwinde und Bugrolle, Rettungsring, Schornstein, Scheinwerfer,
 * Tauwerk und Fischkisten, Name an Bug und Spiegel.
 * <p>
 * Ausgabe ({@link Model}): Dreiecke mit 14 float je Ecke ({@link #STRIDE}: Ort, Normale, Grundfarbe, Flächenkoordinaten,
 * Material, Verdeckung, Kante), getrennt in deckende und gläserne; dazu Drähte und Leinen als Strecken (für die
 * Darstellung mit Mindestbreite und Deckung, {@code gl/CraftGl}), Lampen und Schriftzüge. Achsenkreuz wie {@link Hull}:
 * s nach vorn, y nach oben ab Kielunterkante, q nach Steuerbord.
 * <ul>
 * <li><b>Verdeckung</b> (Umgebungslicht, „AO“): je Ecke 24 Strahlen in die Halbkugel über der Fläche gegen alle
 * Dreiecke (Hüllkörperbaum); was in Ecken, unter Leisten und im Steuerhaus liegt, bekommt weniger Himmel.</li>
 * <li><b>Kante:</b> wie scharf die Flächen an dieser Ecke zusammenstoßen (0 glatt … 1 rechtwinklig); dort platzt
 * die Farbe zuerst ab.</li>
 * </ul>
 * Reproduzierbar: gleicher Bauplan, gleiches Netz. Nur Java 21.
 */
public final class Shipwright {
    private Shipwright() { }

    /** float je Ecke: x y z, nx ny nz, r g b, u v, Material, Verdeckung, Kante. */
    public static final int STRIDE = 14;
    /** float je Draht: x0 y0 z0, x1 y1 z1, Halbmesser, Material, r g b. */
    public static final int WIRE = 11;

    public static final int M_HULL = 0, M_DECK = 1, M_VARNISH = 2, M_PAINT = 3, M_METAL = 4, M_IRON = 5, M_GLASS = 6,
            M_ROPE = 7, M_CANVAS = 8, M_LAMP = 9, M_DECAL = 10, M_ROOF = 11, M_BRASS = 12, M_WOOD = 13, M_INTERIOR = 14;
    /** Namen der Materialien (für Prüfung und Legende). */
    public static final String[] MATERIAL = {"Rumpf", "Deck", "Lack", "Farbe", "Stahl", "Eisen", "Glas", "Tauwerk",
            "Segeltuch", "Lampe", "Schrift", "Dach", "Messing", "Holz", "innen"};

    /** Das fertige Boot. */
    public static final class Model {
        /** Deckende Dreiecke, Glas (durchscheinend, nach dem Deckenden zu zeichnen). */
        public final float[] opaque, glass;
        /** Drähte und Leinen ({@link #WIRE} float je Strecke). */
        public final float[] wires;
        /** Lampen: x y z, r g b (Strahldichte), Halbmesser – 7 float je Lampe. */
        public final float[] lamps;
        /** Schriftzüge für die Schrifttextur: Zeile 0 Name, Zeile 1 Heimathafen. */
        public final String[] labels;
        /** Hülle im Achsenkreuz des Rumpfs: smin, ymin, qmin, smax, ymax, qmax. */
        public final double[] bounds;
        /** Spitzen der Trollstangen (für Leinen, die die App selbst hängt): s, y, q je Seite (Backbord, Steuerbord). */
        public final double[][] poleTips;

        Model(float[] o, float[] g, float[] w, float[] l, String[] labels, double[] b, double[][] tips) {
            opaque = o; glass = g; wires = w; lamps = l; this.labels = labels; bounds = b; poleTips = tips;
        }

        public int triangles() { return (opaque.length + glass.length) / STRIDE / 3; }
    }

    // ================================================================== Bau

    public static Model build(Troller t) {
        B b = new B(t);
        b.hull();
        b.deckAndBulwark();
        b.wheelhouse();
        b.trunk();
        b.deckGear();
        b.mastAndRig();
        b.poles();
        b.mizzen();
        b.anchorGear();
        b.names();
        float[] o = b.o.trim(), g = b.g.trim();
        Occlusion.bake(o, g, 24, t.seed);
        Occlusion.edges(o);
        Occlusion.edges(g);
        double[] bd = {1e9, 1e9, 1e9, -1e9, -1e9, -1e9};
        for (float[] a : new float[][]{o, g})
            for (int i = 0; i < a.length; i += STRIDE)
                for (int k = 0; k < 3; k++) { bd[k] = Math.min(bd[k], a[i + k]); bd[3 + k] = Math.max(bd[3 + k], a[i + k]); }
        return new Model(o, g, b.w.trim(), b.lamps.trim(), new String[]{t.name, t.homeport}, bd, b.tips);
    }

    // ------------------------------------------------------------------ Puffer

    static final class FB {
        float[] a = new float[1 << 16];
        int n;
        void add(float x) { if (n == a.length) a = Arrays.copyOf(a, a.length * 2); a[n++] = x; }
        float[] trim() { return Arrays.copyOf(a, n); }
    }

    private static final class B {
        final Troller t;
        final Hull h;
        final FB o = new FB(), g = new FB(), w = new FB(), lamps = new FB();
        final Random rnd;
        double[][] tips = new double[2][];
        final double L, wl;

        B(Troller t) {
            this.t = t;
            this.h = t.hull;
            L = h.loa;
            wl = t.waterline();
            rnd = new Random(t.seed * 7919L + 17);
        }

        // ---------------------------------------------------------- Grundformen

        void vert(FB f, double[] p, double[] n, double[] c, double u, double v, int m) {
            f.add((float) p[0]); f.add((float) p[1]); f.add((float) p[2]);
            f.add((float) n[0]); f.add((float) n[1]); f.add((float) n[2]);
            f.add((float) c[0]); f.add((float) c[1]); f.add((float) c[2]);
            f.add((float) u); f.add((float) v); f.add(m); f.add(1f); f.add(0f);
        }

        static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
        static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
        static double[] mul(double[] a, double k) { return new double[]{a[0] * k, a[1] * k, a[2] * k}; }
        static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
        static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
        static double[] norm(double[] a) { double l = Math.sqrt(dot(a, a)); return l < 1e-12 ? new double[]{0, 1, 0} : mul(a, 1 / l); }
        static double[] lerp(double[] a, double[] b, double k) { return new double[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k}; }
        static double[] P(double s, double y, double q) { return new double[]{s, y, q}; }

        /** Flächenkoordinaten einer ebenen Fläche aus ihrer Lage: entlang der größten Ausdehnung. */
        static double[] uvOf(double[] p, double[] n) {
            double ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
            if (ay >= ax && ay >= az) return new double[]{p[0], p[2]};
            if (ax >= az) return new double[]{p[2], p[1]};
            return new double[]{p[0], p[1]};
        }

        void tri(FB f, double[] a, double[] b, double[] c, double[] col, int m) {
            double[] n = cross(sub(b, a), sub(c, a));
            if (dot(n, n) < 1e-16) return;
            n = norm(n);
            for (double[] p : new double[][]{a, b, c}) { double[] uv = uvOf(p, n); vert(f, p, n, col, uv[0], uv[1], m); }
        }

        void quad(FB f, double[] a, double[] b, double[] c, double[] d, double[] col, int m) { tri(f, a, b, c, col, m); tri(f, a, c, d, col, m); }

        /** Quader in Rumpfachsen. */
        void box(double s0, double y0, double q0, double s1, double y1, double q1, double[] col, int m) {
            double[] a = P(s0, y0, q0), b = P(s1, y0, q0), c = P(s1, y1, q0), d = P(s0, y1, q0);
            double[] e = P(s0, y0, q1), f = P(s1, y0, q1), gg = P(s1, y1, q1), hh = P(s0, y1, q1);
            quad(o, a, d, c, b, col, m); quad(o, e, f, gg, hh, col, m);
            quad(o, a, b, f, e, col, m); quad(o, d, hh, gg, c, col, m);
            quad(o, a, e, hh, d, col, m); quad(o, b, c, gg, f, col, m);
        }

        /** Gedrehter Quader: Mitte, Achsen u, v, w (normiert) und halbe Längen. */
        void obox(double[] c, double[] u, double[] v, double[] ww, double hu, double hv, double hw, double[] col, int m) {
            double[][] p = new double[8][];
            for (int i = 0; i < 8; i++)
                p[i] = add(add(add(c, mul(u, (i & 1) == 0 ? -hu : hu)), mul(v, (i & 2) == 0 ? -hv : hv)), mul(ww, (i & 4) == 0 ? -hw : hw));
            int[][] fc = {{0, 2, 3, 1}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 4, 6, 2}, {1, 3, 7, 5}};
            for (int[] q : fc) quad(o, p[q[0]], p[q[1]], p[q[2]], p[q[3]], col, m);
        }

        /** Zylinder (Kegelstumpf) von a nach b, Halbmesser r0 … r1, glatte Normalen, Deckel wahlweise. */
        void cyl(FB f, double[] a, double[] b, double r0, double r1, int seg, double[] col, int m, boolean caps) {
            double[] ax = sub(b, a);
            double len = Math.sqrt(dot(ax, ax));
            if (len < 1e-9) return;
            double[] k = mul(ax, 1 / len);
            double[] u = norm(cross(k, Math.abs(k[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0}));
            double[] v = cross(k, u);
            double slope = (r0 - r1) / len;
            for (int i = 0; i < seg; i++) {
                double a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
                double[] d0 = add(mul(u, Math.cos(a0)), mul(v, Math.sin(a0))), d1 = add(mul(u, Math.cos(a1)), mul(v, Math.sin(a1)));
                double[] n0 = norm(add(d0, mul(k, slope))), n1 = norm(add(d1, mul(k, slope)));
                double[] p00 = add(a, mul(d0, r0)), p01 = add(a, mul(d1, r0)), p10 = add(b, mul(d0, r1)), p11 = add(b, mul(d1, r1));
                double uu0 = (double) i / seg, uu1 = (double) (i + 1) / seg;
                vert(f, p00, n0, col, uu0, 0, m); vert(f, p01, n1, col, uu1, 0, m); vert(f, p11, n1, col, uu1, len, m);
                vert(f, p00, n0, col, uu0, 0, m); vert(f, p11, n1, col, uu1, len, m); vert(f, p10, n0, col, uu0, len, m);
                if (caps) {
                    double[] mk = mul(k, -1);
                    vert(f, a, mk, col, 0.5, 0.5, m); vert(f, p01, mk, col, 0.5 + 0.5 * Math.cos(a1), 0.5 + 0.5 * Math.sin(a1), m); vert(f, p00, mk, col, 0.5 + 0.5 * Math.cos(a0), 0.5 + 0.5 * Math.sin(a0), m);
                    vert(f, b, k, col, 0.5, 0.5, m); vert(f, p10, k, col, 0.5 + 0.5 * Math.cos(a0), 0.5 + 0.5 * Math.sin(a0), m); vert(f, p11, k, col, 0.5 + 0.5 * Math.cos(a1), 0.5 + 0.5 * Math.sin(a1), m);
                }
            }
        }

        /** Ring (Torus) um die Achse ax durch c; Farbe je Viertel wechselnd, wenn col2 nicht null (Rettungsring). */
        void torus(double[] c, double[] ax, double R, double r, int segR, int segr, double[] col, double[] col2, int m) {
            double[] k = norm(ax);
            double[] u = norm(cross(k, Math.abs(k[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0}));
            double[] v = cross(k, u);
            for (int i = 0; i < segR; i++)
                for (int j = 0; j < segr; j++) {
                    double[][] p = new double[4][], n = new double[4][];
                    for (int q = 0; q < 4; q++) {
                        double a = 2 * Math.PI * (i + (q == 1 || q == 2 ? 1 : 0)) / segR, bb = 2 * Math.PI * (j + (q >= 2 ? 1 : 0)) / segr;
                        double[] d = add(mul(u, Math.cos(a)), mul(v, Math.sin(a)));
                        n[q] = norm(add(mul(d, Math.cos(bb)), mul(k, Math.sin(bb))));
                        p[q] = add(add(c, mul(d, R)), mul(n[q], r));
                    }
                    double[] cc = col2 != null && (i * 4 / segR) % 2 == 1 ? col2 : col;
                    int[] ord = {0, 1, 2, 0, 2, 3};
                    for (int q : ord) vert(o, p[q], n[q], cc, (double) i / segR, (double) j / segr, m);
                }
        }

        void wire(double[] a, double[] b, double r, int m, double[] col) {
            for (double x : a) w.add((float) x);
            for (double x : b) w.add((float) x);
            w.add((float) r); w.add(m);
            for (double x : col) w.add((float) x);
        }

        void lamp(double[] p, double[] rad, double r) {
            for (double x : p) lamps.add((float) x);
            for (double x : rad) lamps.add((float) x);
            lamps.add((float) r);
        }

        double deckAt(double s, double q) {
            double hb = Math.max(0.05, h.halfBreadth(s, h.deck(s) - 1e-3));
            double x = Math.min(1, Math.abs(q) / hb);
            return h.deck(s) + 0.07 * hb / 1.85 * (1 - x * x);   // Bucht des Decks (rund 7 cm mittschiffs) [G]
        }

        double sAt(double f) { return f * L; }

        // ---------------------------------------------------------- Rumpf

        void hull() {
            int ns = 140, ny = 44;
            double[][][] pos = new double[ns + 1][ny + 1][];
            double[][] girth = new double[ns + 1][ny + 1];
            double[] ss = new double[ns + 1];
            for (int i = 0; i <= ns; i++) {
                double v = (double) i / ns;
                double x = Math.sin(Math.PI * (v - 0.5));            // dichter an Steven und Spiegel
                double s = 0.5 * L * x;
                s = Math.max(-0.5 * L, Math.min(0.5 * L * 0.9995, s));
                ss[i] = s;
                double k = h.keel(s), top = h.deck(s);
                double kd = h.keelDepth * h.draft * Math.max(0, 1 - Math.max(0, 2 * s / L - h.forefoot) / (1 - h.forefoot));
                double y0 = k + kd;
                double keelHalf = Math.abs(2 * s / L) < 0.97 ? h.keelWidth / 2 : 0.01;
                for (int j = 0; j <= ny; j++) {
                    double y, q;
                    if (j == 0) { y = k; q = 0; }
                    else if (j == 1) { y = k; q = keelHalf; }
                    else if (j == 2) { y = Math.min(y0, top); q = keelHalf; }
                    else {
                        double tt = (double) (j - 2) / (ny - 2);
                        y = y0 + (top - y0) * (tt < 0.5 ? 0.5 * Math.pow(2 * tt, 1.35) : 1 - 0.5 * Math.pow(2 * (1 - tt), 1.2));
                        q = Math.max(keelHalf, h.halfBreadth(s, Math.min(top - 1e-4, Math.max(y, y0 + 1e-4))));
                    }
                    pos[i][j] = P(s, y, q);
                    girth[i][j] = j == 0 ? 0 : girth[i][j - 1] + Math.hypot(pos[i][j][1] - pos[i][j - 1][1], pos[i][j][2] - pos[i][j - 1][2]);
                }
            }
            // Normalen aus dem Gitter (Steuerbord)
            double[][][] nor = new double[ns + 1][ny + 1][];
            for (int i = 0; i <= ns; i++)
                for (int j = 0; j <= ny; j++) {
                    double[] ds = sub(pos[Math.min(ns, i + 1)][j], pos[Math.max(0, i - 1)][j]);
                    double[] dy = sub(pos[i][Math.min(ny, j + 1)], pos[i][Math.max(0, j - 1)]);
                    double[] n = norm(cross(ds, dy));
                    if (n[2] < 0 && j > 1) n = mul(n, -1);
                    if (j == 0) n = new double[]{0, -1, 0};
                    nor[i][j] = n;
                }
            for (int side = -1; side <= 1; side += 2)
                for (int i = 0; i < ns; i++)
                    for (int j = 0; j < ny; j++) {
                        int[][] c = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                        int[] ord = side > 0 ? new int[]{0, 1, 2, 0, 2, 3} : new int[]{0, 2, 1, 0, 3, 2};
                        for (int q : ord) {
                            int a = c[q][0], bq = c[q][1];
                            double[] p = pos[a][bq], n = nor[a][bq];
                            double[] pp = P(p[0], p[1], side * p[2]), nn = new double[]{n[0], n[1], side * n[2]};
                            vert(o, pp, nn, t.topsides, ss[a], h.deck(ss[a]) - p[1], M_HULL);   // u: s, v: Abstand unter der Deckskante
                        }
                    }
            // Spiegel (achtern), leicht nach hinten geneigt durch den Sprung [G]
            double[] mid = P(ss[0] - 0.02, 0.5 * (h.keel(ss[0]) + h.deck(ss[0])), 0);
            for (int side = -1; side <= 1; side += 2)
                for (int j = 0; j < ny; j++) {
                    double[] a = pos[0][j], bq = pos[0][j + 1];
                    double[] pa = P(a[0] - 0.015, a[1], side * a[2]), pb = P(bq[0] - 0.015, bq[1], side * bq[2]);
                    double[] n = {-1, 0, 0};
                    double dk = h.deck(ss[0]);
                    double[] uvA = {side * a[2], dk - a[1]}, uvB = {side * bq[2], dk - bq[1]}, uvM = {0, dk - mid[1]};
                    if (side > 0) { vert(o, mid, n, t.topsides, uvM[0], uvM[1], M_HULL); vert(o, pb, n, t.topsides, uvB[0], uvB[1], M_HULL); vert(o, pa, n, t.topsides, uvA[0], uvA[1], M_HULL); }
                    else { vert(o, mid, n, t.topsides, uvM[0], uvM[1], M_HULL); vert(o, pa, n, t.topsides, uvA[0], uvA[1], M_HULL); vert(o, pb, n, t.topsides, uvB[0], uvB[1], M_HULL); }
                }
            {   // oberes Dreieck des Spiegels (zwischen den beiden Deckskanten)
                double[] tp = pos[0][ny];
                double dk = h.deck(ss[0]);
                double[] pa = P(tp[0] - 0.015, tp[1], -tp[2]), pb = P(tp[0] - 0.015, tp[1], tp[2]);
                double[] n = {-1, 0, 0};
                vert(o, mid, n, t.topsides, 0, dk - mid[1], M_HULL); vert(o, pa, n, t.topsides, -tp[2], dk - tp[1], M_HULL); vert(o, pb, n, t.topsides, tp[2], dk - tp[1], M_HULL);
            }
            // Scheuerleiste (halbrund, Farbe des Schergangs) knapp unter der Deckskante, zweite schmale Leiste darunter
            for (int side = -1; side <= 1; side += 2) {
                rail(side, -0.03, 0.055, 0.045, t.sheerStrake, M_PAINT, 0.02 * L, 0.985);
                rail(side, -t.sheerStrakeHeight - 0.02, 0.025, 0.02, t.sheerStrake, M_PAINT, 0.12 * L, 0.80);
            }
            // Stevenbeschlag (Messing, abgegriffen) vorn am Steven
            double s1 = 0.5 * L * 0.9995;
            cyl(o, P(s1, h.keel(s1), 0), P(s1 + 0.01, h.deck(s1) + 0.05, 0), 0.035, 0.035, 6, new double[]{0.35, 0.22, 0.08}, M_BRASS, false);
            // Ruder und Schraube (unter Wasser, im klaren Wasser sichtbar)
                box(-0.5 * L - 0.55, h.keel(-0.5 * L) + 0.08, -0.04, -0.5 * L - 0.05, h.keelDepth * h.draft + 0.05, 0.04, t.bottom, M_PAINT);
            cyl(o, P(-0.5 * L + 0.25, 0.38 * h.draft, 0), P(-0.5 * L + 0.55, 0.38 * h.draft, 0), 0.06, 0.06, 8, new double[]{0.36, 0.24, 0.10}, M_BRASS, true);
            for (int k = 0; k < 3; k++) {
                double a = 2 * Math.PI * k / 3;
                double[] c = P(-0.5 * L + 0.32, 0.38 * h.draft, 0), d = P(0, Math.cos(a), Math.sin(a));
                obox(add(c, mul(d, 0.22)), new double[]{1, 0, 0}, d, norm(cross(new double[]{1, 0, 0}, d)), 0.05, 0.22, 0.012, new double[]{0.36, 0.24, 0.10}, M_BRASS);
            }
        }

        /** Leiste längs der Bordwand: Abstand dy unter der Deckskante, Vorstand, halbe Höhe, von s = -L/2 + achtern bis vorn (Anteil). */
        void rail(int side, double dy, double out, double halfH, double[] col, int m, double aftInset, double foreFrac) {
            int n = 90, seg = 6;
            double s0 = -0.5 * L + aftInset, s1 = 0.5 * L * foreFrac;
            double[][][] p = new double[n + 1][seg + 1][], nn = new double[n + 1][seg + 1][];
            for (int i = 0; i <= n; i++) {
                double s = s0 + (s1 - s0) * i / n;
                double yc = h.deck(s) + dy;
                double q = h.halfBreadth(s, Math.max(h.keel(s) + 0.01, Math.min(h.deck(s) - 1e-3, yc)));
                for (int j = 0; j <= seg; j++) {
                    double a = Math.PI * j / seg - Math.PI / 2;   // −90° unten … +90° oben
                    double dq = Math.cos(a) * out, dyy = Math.sin(a) * halfH;
                    p[i][j] = P(s, yc + dyy, side * (q + dq));
                    nn[i][j] = norm(new double[]{0, Math.sin(a) / halfH, side * Math.cos(a) / out});
                }
            }
            for (int i = 0; i < n; i++)
                for (int j = 0; j < seg; j++) {
                    int[][] c = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                    int[] ord = side > 0 ? new int[]{0, 2, 1, 0, 3, 2} : new int[]{0, 1, 2, 0, 2, 3};
                    for (int q : ord) vert(o, p[c[q][0]][c[q][1]], nn[c[q][0]][c[q][1]], col, p[c[q][0]][c[q][1]][0], j, m);
                }
        }

        // ---------------------------------------------------------- Deck und Verschanzung

        void deckAndBulwark() {
            int n = 120, m = 14;
            double s0 = -0.5 * L, s1 = 0.5 * L * 0.985;
            double[][][] p = new double[n + 1][m + 1][];
            for (int i = 0; i <= n; i++) {
                double s = s0 + (s1 - s0) * i / n;
                double hb = Math.max(0.02, h.halfBreadth(s, h.deck(s) - 1e-3) - 0.07);
                for (int j = 0; j <= m; j++) {
                    double q = hb * (2.0 * j / m - 1);
                    p[i][j] = P(s, deckAt(s, q), q);
                }
            }
            for (int i = 0; i < n; i++)
                for (int j = 0; j < m; j++) {
                    double[] a = p[i][j], bq = p[i + 1][j], c = p[i + 1][j + 1], d = p[i][j + 1];
                    double[] nn = norm(cross(sub(d, a), sub(bq, a)));
                    if (nn[1] < 0) nn = mul(nn, -1);
                    for (double[] q : new double[][]{a, c, bq, a, d, c}) vert(o, q, nn, t.deck, q[0], q[2], M_DECK);
                }
            // Verschanzung: außen bündig mit der Bordwand, innen 6 cm, oben das Schandeck (lackiert, etwas breiter)
            int nb = 110;
            double sa = -0.5 * L + 0.01, se = 0.5 * L * 0.975;
            for (int side = -1; side <= 1; side += 2)
                for (int i = 0; i < nb; i++) {
                    double sA = sa + (se - sa) * i / nb, sB = sa + (se - sa) * (i + 1) / nb;
                    double qA = h.halfBreadth(sA, h.deck(sA) - 1e-3) * 1.006, qB = h.halfBreadth(sB, h.deck(sB) - 1e-3) * 1.006;
                    double dA = h.deck(sA), dB = h.deck(sB);
                    double bwA = t.bulwark * (1 + 0.35 * Math.max(0, 2 * sA / L - 0.5)), bwB = t.bulwark * (1 + 0.35 * Math.max(0, 2 * sB / L - 0.5));
                    double[] oA0 = P(sA, dA - 0.01, side * qA), oB0 = P(sB, dB - 0.01, side * qB), oA1 = P(sA, dA + bwA, side * qA), oB1 = P(sB, dB + bwB, side * qB);
                    double[] iA0 = P(sA, dA, side * (qA - 0.06)), iB0 = P(sB, dB, side * (qB - 0.06)), iA1 = P(sA, dA + bwA, side * (qA - 0.06)), iB1 = P(sB, dB + bwB, side * (qB - 0.06));
                    double[] nOut = {0, 0, side}, nIn = {0, 0, -side};
                    for (double[] q : side > 0 ? new double[][]{oA0, oB1, oB0, oA0, oA1, oB1} : new double[][]{oA0, oB0, oB1, oA0, oB1, oA1})
                        vert(o, q, nOut, t.topsides, q[0], h.deck(q[0]) - q[1], M_HULL);
                    // Speigatten: alle rund 0,9 m ein dunkler Schlitz am Fuß der Innenseite und außen [G]
                    boolean scupper = ((int) Math.floor((sA + 50) / 0.9)) != ((int) Math.floor((sB + 50) / 0.9)) && Math.abs(sA) < 0.42 * L;
                    double[] colIn = t.topsides;
                    for (double[] q : side > 0 ? new double[][]{iA0, iB0, iB1, iA0, iB1, iA1} : new double[][]{iA0, iB1, iB0, iA0, iA1, iB1})
                        vert(o, q, nIn, colIn, q[0], q[1] - h.deck(q[0]) + 10, M_PAINT);
                    if (scupper) {
                        double sm = 0.5 * (sA + sB), dm = h.deck(sm), qm = h.halfBreadth(sm, dm - 1e-3) * 1.006;
                        box(sm - 0.09, dm + 0.005, side > 0 ? qm - 0.068 : -qm - 0.004, sm + 0.09, dm + 0.06, side > 0 ? qm + 0.004 : -qm + 0.068, new double[]{0.02, 0.02, 0.02}, M_IRON);
                    }
                    // Schandeck
                    double[] cA0 = P(sA, dA + bwA, side * (qA + 0.035)), cB0 = P(sB, dB + bwB, side * (qB + 0.035));
                    double[] cA1 = P(sA, dA + bwA, side * (qA - 0.095)), cB1 = P(sB, dB + bwB, side * (qB - 0.095));
                    double[] cA2 = P(sA, dA + bwA + 0.045, side * (qA + 0.035)), cB2 = P(sB, dB + bwB + 0.045, side * (qB + 0.035));
                    double[] cA3 = P(sA, dA + bwA + 0.045, side * (qA - 0.095)), cB3 = P(sB, dB + bwB + 0.045, side * (qB - 0.095));
                    quad(o, cA2, cB2, cB3, cA3, t.varnish, M_VARNISH);
                    quad(o, cA0, cB0, cB2, cA2, t.varnish, M_VARNISH);
                    quad(o, cA1, cA3, cB3, cB1, t.varnish, M_VARNISH);
                    quad(o, cA0, cA1, cB1, cB0, t.varnish, M_VARNISH);
                }
            // Heckreling: Schandeck über den Spiegel
            double sT = -0.5 * L + 0.01, dT = h.deck(sT), qT = h.halfBreadth(sT, dT - 1e-3);
            box(sT - 0.03, dT - 0.01, -qT, sT + 0.06, dT + t.bulwark, qT, t.topsides, M_PAINT);
            box(sT - 0.05, dT + t.bulwark, -qT - 0.035, sT + 0.09, dT + t.bulwark + 0.045, qT + 0.035, t.varnish, M_VARNISH);
            // Relingsstützen innen (alle 0,6 m) [G]
            for (int side = -1; side <= 1; side += 2)
                for (double s = -0.46 * L; s < 0.44 * L; s += 0.6) {
                    double d = h.deck(s), q = h.halfBreadth(s, d - 1e-3) - 0.07;
                    box(s - 0.03, d, side > 0 ? q - 0.04 : -q, s + 0.03, d + t.bulwark, side > 0 ? q : -q + 0.04, t.topsides, M_PAINT);
                }
        }

        // ---------------------------------------------------------- Steuerhaus

        /** Bilinear gespannte Wand P(u, v) = Ecken a (u0 v0), b (u1 v0), c (u1 v1), d (u0 v1). */
        static double[] at(double[] a, double[] b, double[] c, double[] d, double u, double v) {
            return lerp(lerp(a, b, u), lerp(d, c, u), v);
        }

        /**
         * Wand mit Fensteröffnungen (u-Bereiche, v von v0 bis v1, alle in 0 … 1) in einer bilinear gespannten Fläche;
         * die Scheiben sitzen 3 cm innen, mit Rahmen. inside: Richtung ins Innere (für Laibung und Glas).
         */
        void wall(double[] a, double[] b, double[] c, double[] d, double[][] win, double v0, double v1, double[] col, double[] frame, double[] inside, int mat) {
            List<double[]> cuts = new ArrayList<>();
            for (double[] wi : win) cuts.add(wi);
            double prev = 0;
            // Brüstung und Sturz über die ganze Breite
            quadUV(at(a, b, c, d, 0, 0), at(a, b, c, d, 1, 0), at(a, b, c, d, 1, v0), at(a, b, c, d, 0, v0), col, mat);
            quadUV(at(a, b, c, d, 0, v1), at(a, b, c, d, 1, v1), at(a, b, c, d, 1, 1), at(a, b, c, d, 0, 1), col, mat);
            for (double[] wi : cuts) {
                if (wi[0] > prev) quadUV(at(a, b, c, d, prev, v0), at(a, b, c, d, wi[0], v0), at(a, b, c, d, wi[0], v1), at(a, b, c, d, prev, v1), col, mat);
                prev = wi[1];
                double[] g00 = add(at(a, b, c, d, wi[0], v0), mul(inside, 0.03)), g10 = add(at(a, b, c, d, wi[1], v0), mul(inside, 0.03));
                double[] g11 = add(at(a, b, c, d, wi[1], v1), mul(inside, 0.03)), g01 = add(at(a, b, c, d, wi[0], v1), mul(inside, 0.03));
                // Laibung
                quadUV(at(a, b, c, d, wi[0], v0), at(a, b, c, d, wi[1], v0), g10, g00, frame, M_PAINT);
                quadUV(at(a, b, c, d, wi[1], v1), at(a, b, c, d, wi[0], v1), g01, g11, frame, M_PAINT);
                quadUV(at(a, b, c, d, wi[0], v1), at(a, b, c, d, wi[0], v0), g00, g01, frame, M_PAINT);
                quadUV(at(a, b, c, d, wi[1], v0), at(a, b, c, d, wi[1], v1), g11, g10, frame, M_PAINT);
                // Scheibe
                double[] n = norm(mul(inside, -1));
                for (double[] q : new double[][]{g00, g10, g11, g00, g11, g01}) vert(g, q, n, new double[]{0.04, 0.05, 0.05}, 0, 0, M_GLASS);
                // Rahmen (Leiste, 2 cm vor der Wand)
                double fu = 0.018 / Math.max(1e-3, dist(at(a, b, c, d, 0, 0.5), at(a, b, c, d, 1, 0.5)));
                double fv = 0.018 / Math.max(1e-3, dist(at(a, b, c, d, 0.5, 0), at(a, b, c, d, 0.5, 1)));
                double[] out = mul(inside, -0.012);
                frameStrip(a, b, c, d, wi[0] - fu, wi[1] + fu, v0 - fv, v0 + fv * 0.5, out, frame);
                frameStrip(a, b, c, d, wi[0] - fu, wi[1] + fu, v1 - fv * 0.5, v1 + fv, out, frame);
                frameStrip(a, b, c, d, wi[0] - fu, wi[0] + fu * 0.5, v0, v1, out, frame);
                frameStrip(a, b, c, d, wi[1] - fu * 0.5, wi[1] + fu, v0, v1, out, frame);
            }
            if (prev < 1) quadUV(at(a, b, c, d, prev, v0), at(a, b, c, d, 1, v0), at(a, b, c, d, 1, v1), at(a, b, c, d, prev, v1), col, mat);
        }

        void frameStrip(double[] a, double[] b, double[] c, double[] d, double u0, double u1, double v0, double v1, double[] out, double[] col) {
            double[] p0 = add(at(a, b, c, d, u0, v0), out), p1 = add(at(a, b, c, d, u1, v0), out), p2 = add(at(a, b, c, d, u1, v1), out), p3 = add(at(a, b, c, d, u0, v1), out);
            quadUV(p0, p1, p2, p3, col, M_PAINT);
            double[] b0 = at(a, b, c, d, u0, v0), b1 = at(a, b, c, d, u1, v0), b2 = at(a, b, c, d, u1, v1), b3 = at(a, b, c, d, u0, v1);
            quadUV(b0, b1, p1, p0, col, M_PAINT); quadUV(b1, b2, p2, p1, col, M_PAINT); quadUV(b2, b3, p3, p2, col, M_PAINT); quadUV(b3, b0, p0, p3, col, M_PAINT);
        }

        static double dist(double[] a, double[] b) { double[] d = sub(a, b); return Math.sqrt(dot(d, d)); }

        void quadUV(double[] a, double[] b, double[] c, double[] d, double[] col, int m) { quad(o, a, b, c, d, col, m); }

        void wheelhouse() {
            double s0 = h.houseS0, s1 = h.houseS1, W = h.houseWidth, H = h.houseHeight;
            double d0 = deckAt(s0, 0) - 0.02, d1 = deckAt(s1, 0) - 0.02;
            double tumble = H * Math.tan(Math.toRadians(t.houseTumblehome)), rake = H * Math.tan(Math.toRadians(t.windshieldRake));
            double hw = W / 2, tw = hw - tumble;
            // Ecken: unten (achtern Bb, achtern Stb, vorn Stb, vorn Bb), oben
            double[] ab = P(s0, d0, -hw), as = P(s0, d0, hw), fs = P(s1, d1, hw), fb = P(s1, d1, -hw);
            double[] abT = P(s0, d0 + H, -tw), asT = P(s0, d0 + H, tw), fsT = P(s1 - rake, d1 + H, tw), fbT = P(s1 - rake, d1 + H, -tw);
            double[] trim = t.houseTrim;
            // Front: drei Scheiben (Mitte breiter), Brüstung 0,95 m
            double v0 = 0.50, v1 = 0.90;
            wall(fb, fs, fsT, fbT, new double[][]{{0.07, 0.32}, {0.36, 0.64}, {0.68, 0.93}}, v0, v1, t.house, trim, new double[]{-1, 0, 0}, M_PAINT);
            // Seiten: zwei Scheiben, auf Steuerbord die Tür (achtern) mit Fenster
            wall(as, fs, fsT, asT, new double[][]{{0.40, 0.62}, {0.68, 0.92}}, v0, v1, t.house, trim, new double[]{0, 0, -1}, M_PAINT);
            wall(fb, ab, abT, fbT, new double[][]{{0.08, 0.32}, {0.38, 0.60}}, v0, v1, t.house, trim, new double[]{0, 0, 1}, M_PAINT);
            // Rückwand mit Fenster
            wall(ab, as, asT, abT, new double[][]{{0.30, 0.70}}, 0.55, 0.88, t.house, trim, new double[]{1, 0, 0}, M_PAINT);
            // Tür auf Steuerbord: Rahmen und Griff (zwischen u 0.06 und 0.34)
            double[] dA = at(as, fs, fsT, asT, 0.06, 0.02), dB = at(as, fs, fsT, asT, 0.34, 0.02), dC = at(as, fs, fsT, asT, 0.34, 0.93), dD = at(as, fs, fsT, asT, 0.06, 0.93);
            double[] outS = {0, 0, 0.012};
            quadUV(add(dA, outS), add(dB, outS), add(dC, outS), add(dD, outS), mix(t.house, trim, 0.25), M_PAINT);
            cyl(o, add(at(as, fs, fsT, asT, 0.30, 0.48), new double[]{0, 0, 0.02}), add(at(as, fs, fsT, asT, 0.30, 0.48), new double[]{0, 0, 0.07}), 0.012, 0.012, 6, new double[]{0.35, 0.24, 0.10}, M_BRASS, true);
            // Ecken: senkrechte Eckleisten
            for (double[][] e : new double[][][]{{ab, abT}, {as, asT}, {fs, fsT}, {fb, fbT}})
                cyl(o, e[0], e[1], 0.03, 0.03, 4, trim, M_PAINT, false);
            // Dach: gewölbt, Überstand vorn 0,14, seitlich 0,10, achtern 0,28; Stirnleiste
            roof(s0 - 0.28, s1 - rake + 0.14, tw + 0.10, d0 + H, d1 + H, 0.09, t.roof, trim);
            // Innen: Konsole und Steuerrad hinter der Mittelscheibe, Boden dunkel
            double sc = s1 - 0.45;
            box(sc - 0.25, d1, -0.6, sc + 0.05, d1 + 0.9, 0.6, new double[]{0.20, 0.13, 0.07}, M_INTERIOR);
            double[] wc = P(sc - 0.32, d1 + 1.12, -0.15);
            torus(wc, new double[]{1, 0.25, 0}, 0.30, 0.022, 20, 6, t.varnish, null, M_VARNISH);
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                double[] dir = norm(cross(norm(new double[]{1, 0.25, 0}), new double[]{0, Math.cos(a), Math.sin(a)}));
                double[] u = norm(new double[]{0, Math.cos(a), Math.sin(a)});
                double[] tip = add(wc, mul(u, 0.40));
                cyl(o, wc, tip, 0.014, 0.010, 5, t.varnish, M_VARNISH, false);
            }
            // Positionslichter an den Seiten (vorne): Kasten mit Blende, Linse
            double sl = s1 - 0.35, yl = d1 + H * 0.82;
            for (int side = -1; side <= 1; side += 2) {
                double q = side * (hw - tumble * 0.82 + 0.08);
                box(sl - 0.20, yl - 0.10, q - 0.06, sl + 0.08, yl + 0.10, q + 0.06, trim, M_PAINT);
                double[] lc = P(sl + 0.04, yl, q + side * 0.07);
                double[] col = side < 0 ? new double[]{0.9, 0.05, 0.03} : new double[]{0.05, 0.8, 0.25};
                box(sl - 0.02, yl - 0.07, q + side * 0.06 - 0.012, sl + 0.07, yl + 0.07, q + side * 0.06 + 0.012, col, M_LAMP);
                if (t.runningLights) lamp(lc, mul(col, t.lampRadiance), 0.05);
            }
            // Rettungsring an der Backbordwand
            double[] rc = at(fb, ab, abT, fbT, 0.68, 0.30);
            torus(add(rc, new double[]{0, 0, -0.07}), new double[]{0, 0, 1}, 0.30, 0.055, 24, 8, new double[]{0.78, 0.76, 0.70}, new double[]{0.55, 0.05, 0.03}, M_PAINT);
            // Schornstein (schwarz) achtern durchs Dach, mit Regenkappe
            double[] st0 = P(s0 + 0.35, d0 + H - 0.1, 0.55), st1 = P(s0 + 0.35, d0 + H + 1.25, 0.55);
            cyl(o, st0, st1, 0.085, 0.075, 12, new double[]{0.025, 0.022, 0.02}, M_IRON, false);
            cyl(o, add(st1, new double[]{0, 0.08, 0}), add(st1, new double[]{0, 0.13, 0}), 0.16, 0.02, 12, new double[]{0.03, 0.028, 0.025}, M_IRON, true);
            for (int k = 0; k < 3; k++) {
                double a = 2 * Math.PI * k / 3;
                double[] off = {0.06 * Math.cos(a), 0, 0.06 * Math.sin(a)};
                cyl(o, add(st1, off), add(add(st1, off), new double[]{0, 0.09, 0}), 0.008, 0.008, 4, new double[]{0.03, 0.028, 0.025}, M_IRON, false);
            }
            // Scheinwerfer, Horn und Peitschenantenne auf dem Dach
            double[] sp = P(s1 - rake - 0.15, d1 + H + 0.12, 0);
            cyl(o, sp, add(sp, new double[]{0, 0.18, 0}), 0.02, 0.02, 6, new double[]{0.3, 0.3, 0.3}, M_METAL, false);
            cyl(o, add(sp, new double[]{-0.12, 0.28, 0}), add(sp, new double[]{0.12, 0.28, 0}), 0.10, 0.11, 14, new double[]{0.30, 0.30, 0.29}, M_METAL, true);
            box(sp[0] + 0.119, sp[1] + 0.19, -0.095, sp[0] + 0.125, sp[1] + 0.37, 0.095, new double[]{0.6, 0.6, 0.55}, M_GLASS);
            cyl(o, P(s0 + 0.2, d0 + H + 0.1, -0.6), P(s0 + 0.2, d0 + H + 0.1 + 3.2, -0.62), 0.012, 0.004, 6, new double[]{0.05, 0.05, 0.05}, M_IRON, false);
            // Haltegriffe am Dachrand
            for (int side = -1; side <= 1; side += 2) {
                double q = side * (tw - 0.05);
                for (double s = s0 + 0.15; s < s1 - rake - 0.2; s += 0.55)
                    cyl(o, P(s, d0 + H + 0.08, q), P(s, d0 + H + 0.16, q), 0.012, 0.012, 5, t.varnish, M_VARNISH, false);
                cyl(o, P(s0 + 0.15, d0 + H + 0.16, q), P(s1 - rake - 0.25, d1 + H + 0.16, q), 0.018, 0.018, 6, t.varnish, M_VARNISH, false);
            }
        }

        static double[] mix(double[] a, double[] b, double k) { return lerp(a, b, k); }

        /** Gewölbtes Dach von s0 bis s1 (Überstand eingerechnet), halbe Breite hw, Höhe hinten/vorn, Wölbung, mit Stirnleiste. */
        void roof(double s0, double s1, double hw, double y0, double y1, double camber, double[] col, double[] trim) {
            int n = 12, m = 10;
            double[][][] p = new double[n + 1][m + 1][];
            for (int i = 0; i <= n; i++)
                for (int j = 0; j <= m; j++) {
                    double s = s0 + (s1 - s0) * i / n, q = hw * (2.0 * j / m - 1);
                    double y = y0 + (y1 - y0) * i / n + camber * (1 - (q / hw) * (q / hw));
                    p[i][j] = P(s, y + 0.04, q);
                }
            for (int i = 0; i < n; i++)
                for (int j = 0; j < m; j++) quadUV(p[i][j], p[i][j + 1], p[i + 1][j + 1], p[i + 1][j], col, M_ROOF);
            // Unterseite und Stirnleiste (6 cm)
            for (int i = 0; i < n; i++) {
                for (int side = 0; side <= 1; side++) {
                    int j = side * m;
                    double[] a = p[i][j], bq = p[i + 1][j];
                    quadUV(a, bq, add(bq, new double[]{0, -0.07, 0}), add(a, new double[]{0, -0.07, 0}), trim, M_PAINT);
                }
            }
            for (int end = 0; end <= 1; end++) {
                int i = end * n;
                for (int j = 0; j < m; j++) {
                    double[] a = p[i][j], bq = p[i][j + 1];
                    quadUV(a, bq, add(bq, new double[]{0, -0.07, 0}), add(a, new double[]{0, -0.07, 0}), trim, M_PAINT);
                }
            }
            for (int i = 0; i < n; i++)
                for (int j = 0; j < m; j++) {
                    double[] dn = {0, -0.07, 0};
                    quadUV(add(p[i][j], dn), add(p[i + 1][j], dn), add(p[i + 1][j + 1], dn), add(p[i][j + 1], dn), mix(col, new double[]{0.5, 0.5, 0.48}, 0.5), M_PAINT);
                }
        }

        // ---------------------------------------------------------- Logis vor dem Steuerhaus

        void trunk() {
            double s0 = h.houseS1 - 0.05, s1 = t.trunkFore * L, H = t.trunkHeight;
            double hw0 = 0.36 * h.beam, hw1 = Math.min(0.30 * h.beam, h.halfBreadth(s1, h.deck(s1) - 1e-3) - 0.35);
            double d0 = deckAt(s0, 0) - 0.06, d1 = deckAt(s1, 0) - 0.06;
            double[] ab = P(s0, d0, -hw0), as = P(s0, d0, hw0), fs = P(s1, d1, hw1), fb = P(s1, d1, -hw1);
            double in = 0.06;
            double[] abT = P(s0, d0 + H, -hw0 + in), asT = P(s0, d0 + H, hw0 - in), fsT = P(s1 - 0.12, d1 + H, hw1 - in), fbT = P(s1 - 0.12, d1 + H, -hw1 + in);
            quadUV(as, fs, fsT, asT, t.house, M_PAINT);
            quadUV(fb, ab, abT, fbT, t.house, M_PAINT);
            quadUV(fb, fs, fsT, fbT, t.house, M_PAINT);
            // Dach (Segeltuch, gestrichen) mit Wölbung
            roof(s0, s1 - 0.08, hw0 - in + 0.05, d0 + H - 0.04, d1 + H - 0.04, 0.06, t.roof, t.houseTrim);
            // Bullaugen: Messingringe mit Glas, drei je Seite
            for (int side = -1; side <= 1; side += 2)
                for (int k = 0; k < 3; k++) {
                    double u = 0.22 + 0.27 * k;
                    double[] c = side > 0 ? at(as, fs, fsT, asT, u, 0.52) : at(ab, fb, fbT, abT, u, 0.52);
                    double[] nrm = norm(side > 0 ? cross(sub(fs, as), sub(asT, as)) : cross(sub(abT, ab), sub(fb, ab)));
                    if (nrm[2] * side < 0) nrm = mul(nrm, -1);
                    torus(add(c, mul(nrm, 0.01)), nrm, 0.10, 0.018, 18, 6, new double[]{0.42, 0.28, 0.10}, null, M_BRASS);
                    double[] u1 = norm(cross(nrm, new double[]{0, 1, 0})), v1 = new double[]{0, 1, 0};
                    for (int s = 0; s < 14; s++) {
                        double a0 = 2 * Math.PI * s / 14, a1 = 2 * Math.PI * (s + 1) / 14;
                        double[] c0 = sub(c, mul(nrm, 0.01));
                        double[] p0 = add(c0, add(mul(u1, 0.09 * Math.cos(a0)), mul(v1, 0.09 * Math.sin(a0)))), p1 = add(c0, add(mul(u1, 0.09 * Math.cos(a1)), mul(v1, 0.09 * Math.sin(a1))));
                        vert(g, c0, nrm, new double[]{0.04, 0.05, 0.05}, 0, 0, M_GLASS); vert(g, p0, nrm, new double[]{0.04, 0.05, 0.05}, 0, 0, M_GLASS); vert(g, p1, nrm, new double[]{0.04, 0.05, 0.05}, 0, 0, M_GLASS);
                    }
                }
            // Lüfter (Pilzlüfter) und Kombüsenrohr („Charlie Noble“) auf dem Logis
            double[] cn = P(0.5 * (s0 + s1), d0 + H + 0.02, 0.45);
            cyl(o, cn, add(cn, new double[]{0, 0.45, 0}), 0.05, 0.05, 10, new double[]{0.35, 0.24, 0.10}, M_BRASS, false);
            cyl(o, add(cn, new double[]{-0.1, 0.45, 0}), add(cn, new double[]{0.1, 0.45, 0}), 0.05, 0.05, 10, new double[]{0.35, 0.24, 0.10}, M_BRASS, true);
            double[] mv = P(0.5 * (s0 + s1) + 0.4, d1 + H + 0.02, -0.4);
            cyl(o, mv, add(mv, new double[]{0, 0.12, 0}), 0.06, 0.06, 10, t.house, M_PAINT, false);
            cyl(o, add(mv, new double[]{0, 0.12, 0}), add(mv, new double[]{0, 0.18, 0}), 0.12, 0.10, 12, t.house, M_PAINT, true);
        }

        // ---------------------------------------------------------- Luke, Trollwinden, Kram

        void deckGear() {
            double sh = t.hatchS * L, hs = t.hatchSize / 2;
            double d = deckAt(sh, 0);
            box(sh - hs, d - 0.02, -hs, sh + hs, d + 0.28, hs, t.house, M_PAINT);
            box(sh - hs - 0.04, d + 0.28, -hs - 0.04, sh + hs + 0.04, d + 0.34, hs + 0.04, t.varnish, M_VARNISH);
            for (int k = -1; k <= 1; k++) box(sh - hs, d + 0.34, k * 0.36 - 0.01, sh + hs, d + 0.355, k * 0.36 + 0.01, new double[]{0.1, 0.08, 0.06}, M_WOOD);
            // Trollgrube achtern: Süll und vier Trollwinden (Gurdies) an der Reling, Draht auf den Trommeln
            double sp = -0.40 * L, dp = deckAt(sp, 0);
            box(sp - 0.7, dp - 0.02, -0.55, sp + 0.7, dp + 0.32, -0.50, t.house, M_PAINT);
            box(sp - 0.7, dp - 0.02, 0.50, sp + 0.7, dp + 0.32, 0.55, t.house, M_PAINT);
            box(sp + 0.65, dp - 0.02, -0.55, sp + 0.7, dp + 0.32, 0.55, t.house, M_PAINT);
            for (int side = -1; side <= 1; side += 2)
                for (int k = 0; k < 2; k++) {
                    double s = sp - 0.35 + 0.7 * k, dd = h.deck(s), q = side * (h.halfBreadth(s, dd - 1e-3) - 0.32);
                    double y = dd + t.bulwark + 0.28;
                    box(s - 0.05, dd, q - 0.05, s + 0.05, y, q + 0.05, new double[]{0.25, 0.25, 0.24}, M_METAL);
                    cyl(o, P(s - 0.09, y + 0.12, q), P(s + 0.09, y + 0.12, q), 0.17, 0.17, 18, new double[]{0.30, 0.30, 0.29}, M_METAL, true);
                    cyl(o, P(s - 0.07, y + 0.12, q), P(s + 0.07, y + 0.12, q), 0.12, 0.12, 18, new double[]{0.07, 0.07, 0.07}, M_IRON, false);
                    // Bremshebel
                    cyl(o, P(s, y + 0.12, q + side * 0.1), P(s + 0.12, y + 0.45, q + side * 0.12), 0.012, 0.012, 5, new double[]{0.04, 0.04, 0.04}, M_IRON, true);
                }
            // Tauwerk: zwei Buchten (flache Spiralen) und Fischkisten
            coil(P(-0.30 * L, deckAt(-0.30 * L, 0.6) + 0.02, 0.6), 0.32, 7, 0.015);
            coil(P(0.42 * L, deckAt(0.42 * L, -0.3) + 0.02, -0.35), 0.26, 6, 0.013);
            for (int k = 0; k < 3; k++) {
                double s = -0.25 * L + 0.05 * k, q = -0.75 + 0.02 * k, y = deckAt(s, q) + 0.22 * (k == 2 ? 1 : 0);
                double s2 = k == 2 ? -0.24 * L : s;
                box(s2 - 0.32, k == 2 ? deckAt(-0.25 * L, -0.75) + 0.22 : deckAt(s, q), q - 0.22, s2 + 0.32, (k == 2 ? deckAt(-0.25 * L, -0.75) + 0.22 : deckAt(s, q)) + 0.22, q + 0.22, new double[]{0.30 - 0.03 * k, 0.24, 0.16}, M_WOOD);
            }
            // Eimer
            double[] bu = P(-0.35 * L, deckAt(-0.35 * L, -0.4), -0.4);
            cyl(o, bu, add(bu, new double[]{0, 0.28, 0}), 0.12, 0.15, 14, new double[]{0.25, 0.25, 0.24}, M_METAL, false);
        }

        void coil(double[] c, double r, int turns, double rr) {
            int seg = turns * 16;
            double[] prev = null;
            for (int i = 0; i <= seg; i++) {
                double a = 2 * Math.PI * i / 16, rad = r - (r * 0.5) * i / seg;
                double[] p = add(c, new double[]{rad * Math.cos(a), rr + 0.004 * Math.sin(i * 0.7), rad * Math.sin(a)});
                if (prev != null) cyl(o, prev, p, rr, rr, 5, new double[]{0.30, 0.25, 0.16}, M_ROPE, false);
                prev = p;
            }
        }

        // ---------------------------------------------------------- Mast, Saling, Baum, Stagen, Lichter

        double mastTop() { return h.deck(h.mastS) + h.mastHeight; }

        void mastAndRig() {
            double sm = h.mastS, d = deckAt(sm, 0) + t.trunkHeight * 0.9;
            double top = mastTop();
            double[] foot = P(sm, deckAt(sm, 0), 0), head = P(sm - 0.05, top, 0);
            cyl(o, foot, head, 0.09, 0.055, 14, t.mast, M_PAINT, true);
            // Saling
            double ys = deckAt(sm, 0) + 0.78 * h.mastHeight;
            double[] sl = P(sm - 0.04, ys, -0.75), sr = P(sm - 0.04, ys, 0.75);
            obox(P(sm - 0.04, ys, 0), new double[]{1, 0, 0}, new double[]{0, 1, 0}, new double[]{0, 0, 1}, 0.04, 0.03, 0.75, t.mast, M_PAINT);
            // Topp: Ankerlicht (Rundumlicht) und Kappe
            double[] al = P(sm - 0.05, top + 0.16, 0);
            cyl(o, P(sm - 0.05, top, 0), P(sm - 0.05, top + 0.06, 0), 0.07, 0.07, 12, new double[]{0.08, 0.08, 0.08}, M_IRON, true);
            cyl(o, P(sm - 0.05, top + 0.06, 0), P(sm - 0.05, top + 0.26, 0), 0.06, 0.06, 12, new double[]{0.9, 0.88, 0.8}, M_LAMP, false);
            cyl(o, P(sm - 0.05, top + 0.26, 0), P(sm - 0.05, top + 0.32, 0), 0.075, 0.03, 12, new double[]{0.08, 0.08, 0.08}, M_IRON, true);
            if (t.anchorLight) lamp(al, new double[]{t.lampRadiance, t.lampRadiance * 0.92, t.lampRadiance * 0.75}, 0.06);
            // Dampferlicht (Topplicht) nach vorn
            double yl = deckAt(sm, 0) + 0.55 * h.mastHeight;
            box(sm + 0.06, yl - 0.10, -0.07, sm + 0.20, yl + 0.10, 0.07, t.mast, M_PAINT);
            box(sm + 0.19, yl - 0.07, -0.05, sm + 0.215, yl + 0.07, 0.05, new double[]{0.9, 0.88, 0.8}, M_LAMP);
            if (t.runningLights) lamp(P(sm + 0.22, yl, 0), new double[]{t.lampRadiance, t.lampRadiance, t.lampRadiance * 0.85}, 0.05);
            // Ladebaum nach achtern über die Luke, mit Hanger
            double yb = deckAt(sm, 0) + 1.9;
            double[] b0 = P(sm - 0.1, yb, 0), b1 = P(t.hatchS * L, yb + 1.0, 0.15);
            cyl(o, b0, b1, 0.06, 0.045, 10, t.mast, M_PAINT, true);
            wire(b1, P(sm - 0.05, top - 0.3, 0), 0.006, M_METAL, new double[]{0.25, 0.25, 0.24});
            wire(b1, add(b1, new double[]{0, -1.2, 0}), 0.008, M_ROPE, new double[]{0.30, 0.25, 0.16});
            // Vorstag zum Steven, Wanten über die Saling zu den Püttingen
            double sf = 0.5 * L * 0.985;
            double[] stem = P(sf, h.deck(sf) + 0.25, 0);
            wire(P(sm - 0.02, top - 0.12, 0), stem, 0.006, M_METAL, new double[]{0.25, 0.25, 0.24});
            for (int side = -1; side <= 1; side += 2) {
                double ds = h.deck(sm), q = side * (h.halfBreadth(sm, ds - 1e-3) + 0.03);
                double[] chain = P(sm, ds + 0.1, q);
                double[] tip = side < 0 ? sl : sr;
                wire(P(sm - 0.04, top - 0.12, 0), tip, 0.006, M_METAL, new double[]{0.25, 0.25, 0.24});
                wire(tip, chain, 0.006, M_METAL, new double[]{0.25, 0.25, 0.24});
                wire(P(sm - 0.04, ys - 0.05, 0), chain, 0.006, M_METAL, new double[]{0.25, 0.25, 0.24});
                // Püttingeisen und Rostfahne
                box(sm - 0.03, ds - 0.25, q - 0.012, sm + 0.03, ds + 0.18, q + 0.012, new double[]{0.06, 0.05, 0.045}, M_IRON);
                // Webeleinen zum Aufentern (nur Stb, drei)
            }
        }

        // ---------------------------------------------------------- Trollstangen und Schlingerdämpfer

        void poles() {
            double sm = h.mastS;
            double pivotY = deckAt(sm, 0) + 0.9;
            double a = Math.toRadians(t.poleAngle);
            for (int side = -1; side <= 1; side += 2) {
                double[] pv = P(sm - 0.1, pivotY, side * 0.12);
                double[] dir = norm(new double[]{-Math.sin(Math.toRadians(6)), Math.cos(a), side * Math.sin(a)});
                double[] tip = add(pv, mul(dir, h.poleLength));
                cyl(o, pv, tip, 0.065, 0.028, 10, new double[]{0.36, 0.26, 0.14}, M_WOOD, true);
                // Beschlag am Fuß (Gelenk)
                cyl(o, add(pv, mul(dir, -0.05)), add(pv, mul(dir, 0.25)), 0.08, 0.075, 10, new double[]{0.22, 0.22, 0.21}, M_METAL, true);
                tips[side < 0 ? 0 : 1] = tip.clone();
                // Hanger zum Masttopp, Achterholer zum Heck, Vorholer zum Bug
                wire(tip, P(sm - 0.05, mastTop() - 0.2, 0), 0.007, M_ROPE, new double[]{0.30, 0.26, 0.18});
                double ss = -0.44 * L, ds = h.deck(ss);
                wire(tip, P(ss, ds + t.bulwark, side * (h.halfBreadth(ss, ds - 1e-3))), 0.006, M_ROPE, new double[]{0.30, 0.26, 0.18});
                double sb = 0.40 * L, db = h.deck(sb);
                wire(add(pv, mul(dir, 0.55 * h.poleLength)), P(sb, db + t.bulwark, side * h.halfBreadth(sb, db - 1e-3)), 0.006, M_ROPE, new double[]{0.30, 0.26, 0.18});
                // Schlingerdämpfer: Leine senkrecht ins Wasser, Platte (dreieckig) in der Tiefe
                if (t.flopperDepth > 0) {
                    double[] wtr = P(tip[0], wl - t.flopperDepth, tip[2]);
                    wire(tip, wtr, 0.008, M_ROPE, new double[]{0.25, 0.22, 0.16});
                    double[] pl0 = add(wtr, new double[]{0.35, -0.05, 0}), pl1 = add(wtr, new double[]{-0.3, -0.05, 0.25}), pl2 = add(wtr, new double[]{-0.3, -0.05, -0.25});
                    tri(o, pl0, pl1, pl2, new double[]{0.12, 0.10, 0.08}, M_IRON);
                    tri(o, pl0, pl2, pl1, new double[]{0.12, 0.10, 0.08}, M_IRON);
                    // Stander am Topp: kleiner Wimpel? – stattdessen der Glockenring am Topp (Fischglocke) [G]
                    cyl(o, add(tip, new double[]{0, -0.25, 0}), add(tip, new double[]{0, -0.12, 0}), 0.035, 0.02, 8, new double[]{0.40, 0.27, 0.10}, M_BRASS, true);
                }
            }
        }

        // ---------------------------------------------------------- Besan mit Stützsegel

        void mizzen() {
            if (!t.ridingSail) return;
            double sz = -0.44 * L, d = deckAt(sz, 0);
            double hgt = 4.6;
            double[] foot = P(sz, d, 0), head = P(sz - 0.06, d + hgt, 0);
            cyl(o, foot, head, 0.06, 0.04, 10, t.mast, M_PAINT, true);
            double[] bEnd = P(sz - 1.35, d + 1.05, 0);
            double[] bStart = P(sz - 0.04, d + 0.95, 0);
            cyl(o, bStart, bEnd, 0.04, 0.03, 8, t.mast, M_PAINT, true);
            // Segel: Dreieck Kopf – Hals – Schothorn, gewölbt (4 %) nach Backbord [G]
            double[] tack = P(sz - 0.06, d + 1.02, 0), clew = add(bEnd, new double[]{0.05, 0.06, 0}), peak = add(head, new double[]{-0.06, -0.12, 0});
            int n = 10;
            double[][][] p = new double[n + 1][][];
            for (int i = 0; i <= n; i++) {
                p[i] = new double[i + 1][];
                for (int j = 0; j <= i; j++) {
                    double u = (double) i / n, v = i == 0 ? 0 : (double) j / i;
                    double[] a = lerp(peak, tack, u), b = lerp(peak, clew, u);
                    double[] q = lerp(a, b, v);
                    double belly = 0.04 * 2.3 * Math.sin(Math.PI * v) * Math.sin(Math.PI * Math.min(1, u * 1.05));
                    p[i][j] = add(q, new double[]{0, 0, -belly});
                }
            }
            double[] cv = {0.62, 0.55, 0.42};
            for (int i = 0; i < n; i++)
                for (int j = 0; j <= i; j++) {
                    double[] a = p[i][j], b = p[i + 1][j], c = p[i + 1][j + 1];
                    double[] nn = norm(cross(sub(b, a), sub(c, a)));
                    for (double[] q : new double[][]{a, b, c}) vert(o, q, nn, cv, q[0], q[1], M_CANVAS);
                    if (j < i) {
                        double[] e = p[i][j + 1];
                        double[] n2 = norm(cross(sub(c, a), sub(e, a)));
                        for (double[] q : new double[][]{a, c, e}) vert(o, q, n2, cv, q[0], q[1], M_CANVAS);
                    }
                }
            // Liek als Leine, Schot zum Heck
            wire(peak, clew, 0.008, M_ROPE, new double[]{0.45, 0.40, 0.30});
            double st = -0.5 * L + 0.05;
            wire(bEnd, P(st, h.deck(st) + t.bulwark + 0.05, 0.2), 0.007, M_ROPE, new double[]{0.30, 0.26, 0.18});
            wire(P(sz - 0.06, d + hgt - 0.1, 0), P(0.5 * L * 0.95, h.deck(0.5 * L * 0.95) + 0.3, 0), 0.005, M_METAL, new double[]{0.25, 0.25, 0.24});
        }

        // ---------------------------------------------------------- Anker

        void anchorGear() {
            double sw = 0.40 * L, d = deckAt(sw, 0);
            // Ankerwinde: Grundplatte, Trommel quer, Kettennuss, Kurbel
            box(sw - 0.28, d - 0.01, -0.36, sw + 0.28, d + 0.08, 0.36, new double[]{0.20, 0.20, 0.19}, M_METAL);
            box(sw - 0.12, d + 0.08, -0.40, sw + 0.12, d + 0.42, -0.30, new double[]{0.18, 0.18, 0.17}, M_METAL);
            box(sw - 0.12, d + 0.08, 0.30, sw + 0.12, d + 0.42, 0.40, new double[]{0.18, 0.18, 0.17}, M_METAL);
            cyl(o, P(sw, d + 0.30, -0.30), P(sw, d + 0.30, 0.30), 0.13, 0.13, 16, new double[]{0.10, 0.09, 0.08}, M_IRON, true);
            cyl(o, P(sw, d + 0.30, -0.48), P(sw, d + 0.30, -0.40), 0.10, 0.10, 12, new double[]{0.14, 0.12, 0.10}, M_IRON, true);
            // Bugrolle zwischen zwei Wangen am Steven
            double sf = 0.5 * L * 0.985, df = h.deck(sf);
            box(sf - 0.45, df + 0.05, -0.09, sf + 0.20, df + 0.30, -0.07, new double[]{0.12, 0.11, 0.10}, M_IRON);
            box(sf - 0.45, df + 0.05, 0.07, sf + 0.20, df + 0.30, 0.09, new double[]{0.12, 0.11, 0.10}, M_IRON);
            cyl(o, P(sf + 0.08, df + 0.22, -0.07), P(sf + 0.08, df + 0.22, 0.07), 0.07, 0.07, 12, new double[]{0.16, 0.14, 0.12}, M_IRON, true);
            // Kette von der Nuss über die Rolle (die freie Kette zeichnet die App aus der Kettenrechnung)
            wire(P(sw, d + 0.40, -0.44), P(sf + 0.08, df + 0.30, 0), 0.018, M_IRON, new double[]{0.07, 0.05, 0.04});
            // Klampen und Poller vorn
            for (int side = -1; side <= 1; side += 2) {
                double s = 0.30 * L, dd = h.deck(s), q = side * (h.halfBreadth(s, dd - 1e-3) - 0.22);
                cyl(o, P(s, dd, q), P(s, dd + 0.22, q), 0.05, 0.05, 10, new double[]{0.12, 0.11, 0.10}, M_IRON, true);
                cyl(o, P(s - 0.12, dd + 0.18, q), P(s + 0.12, dd + 0.18, q), 0.025, 0.025, 6, new double[]{0.12, 0.11, 0.10}, M_IRON, true);
            }
        }

        // ---------------------------------------------------------- Name

        /** Schriftfelder: Name an beiden Seiten des Bugs, Name und Heimathafen am Spiegel (Zeilen der Schrifttextur). */
        void names() {
            double s0 = 0.30 * L, s1 = 0.43 * L;
            for (int side = -1; side <= 1; side += 2) {
                int n = 10;
                for (int i = 0; i < n; i++) {
                    double sa = s0 + (s1 - s0) * i / n, sb = s0 + (s1 - s0) * (i + 1) / n;
                    double ya = h.deck(sa) - t.sheerStrakeHeight - 0.08, yb = h.deck(sb) - t.sheerStrakeHeight - 0.08;
                    double hgt = 0.26;
                    double qa0 = h.halfBreadth(sa, ya - hgt) + 0.006, qa1 = h.halfBreadth(sa, ya) + 0.006, qb0 = h.halfBreadth(sb, yb - hgt) + 0.006, qb1 = h.halfBreadth(sb, yb) + 0.006;
                    double[] a0 = P(sa, ya - hgt, side * qa0), a1 = P(sa, ya, side * qa1), b0 = P(sb, yb - hgt, side * qb0), b1 = P(sb, yb, side * qb1);
                    double ua = side > 0 ? (double) i / n : 1 - (double) i / n, ub = side > 0 ? (double) (i + 1) / n : 1 - (double) (i + 1) / n;   // von außen lesbar
                    double[] nn = {0, 0, side};
                    // Zeile 0 (v 0 … 0.5)
                    double[][] pts = {a0, b0, b1, a0, b1, a1};
                    double[][] uvs = {{ua, 0.5}, {ub, 0.5}, {ub, 0.0}, {ua, 0.5}, {ub, 0.0}, {ua, 0.0}};
                    int[] ord = side > 0 ? new int[]{0, 2, 1, 3, 5, 4} : new int[]{0, 1, 2, 3, 4, 5};
                    for (int k : ord) vert(o, pts[k], nn, new double[]{0.02, 0.02, 0.02}, uvs[k][0], uvs[k][1], M_DECAL);
                }
            }
            // Spiegel: Name (Zeile 0) und Heimathafen (Zeile 1)
            double st = -0.5 * L - 0.02, dt = h.deck(-0.5 * L);
            double[][] rows = {{dt - 0.40, dt - 0.12, 1.15, 0.0, 0.5}, {dt - 0.58, dt - 0.43, 0.95, 0.5, 1.0}};
            for (double[] r : rows) {
                double hw = r[2];
                double[] a = P(st, r[0], -hw), b = P(st, r[0], hw), c = P(st, r[1], hw), d = P(st, r[1], -hw);   // von achtern gelesen: Backbord links
                double[] nn = {-1, 0, 0};
                double[][] pts = {a, b, c, a, c, d};
                double[][] uvs = {{0, r[4]}, {1, r[4]}, {1, r[3]}, {0, r[4]}, {1, r[3]}, {0, r[3]}};
                for (int k = 0; k < 6; k++) vert(o, pts[k], nn, new double[]{0.02, 0.02, 0.02}, uvs[k][0], uvs[k][1], M_DECAL);
            }
        }
    }

    // ================================================================== Verdeckung und Kanten

    /** Verdeckung je Ecke (Strahlen in die Halbkugel gegen einen Hüllkörperbaum) und Schärfe der Kanten. */
    static final class Occlusion {
        float[] tri;   // je Dreieck 9 float
        int[] idx;
        float[] bmin, bmax;
        int[] left, right, start, count;
        int nodes;

        static void bake(float[] o, float[] g, int rays, long seed) {
            int nt = o.length / STRIDE / 3 + g.length / STRIDE / 3;
            Occlusion oc = new Occlusion();
            oc.tri = new float[nt * 9];
            int k = 0;
            for (float[] a : new float[][]{o, g})
                for (int i = 0; i < a.length; i += STRIDE * 3) {
                    if (a[i + 11] == M_DECAL || a[i + 11] == M_LAMP) { for (int c = 0; c < 9; c++) oc.tri[k * 9 + c] = Float.NaN; k++; continue; }
                    for (int c = 0; c < 3; c++) for (int d = 0; d < 3; d++) oc.tri[k * 9 + c * 3 + d] = a[i + c * STRIDE + d];
                    k++;
                }
            oc.build(nt);
            // je Ort und Normale einmal rechnen
            Map<Long, Float> cache = new HashMap<>();
            Random r = new Random(seed);
            double[][] dirs = new double[rays][];
            for (int i = 0; i < rays; i++) {
                double u = (i + r.nextDouble()) / rays, v = r.nextDouble();
                double rr = Math.sqrt(u), ph = 2 * Math.PI * v;
                dirs[i] = new double[]{rr * Math.cos(ph), Math.sqrt(Math.max(0, 1 - u)), rr * Math.sin(ph)};
            }
            // je Ort und Normale einmal, parallel
            Map<Long, int[]> first = new HashMap<>();
            List<long[]> jobs = new ArrayList<>();
            float[][] arr = {o, g};
            for (int w = 0; w < 2; w++)
                for (int i = 0; i < arr[w].length; i += STRIDE) {
                    long key = key(arr[w], i);
                    if (!first.containsKey(key)) { first.put(key, new int[]{w, i}); jobs.add(new long[]{key, w, i}); }
                }
            float[] res = new float[jobs.size()];
            java.util.stream.IntStream.range(0, jobs.size()).parallel().forEach(j -> res[j] = oc.ao(arr[(int) jobs.get(j)[1]], (int) jobs.get(j)[2], dirs));
            for (int j = 0; j < res.length; j++) cache.put(jobs.get(j)[0], res[j]);
            for (float[] a : arr)
                for (int i = 0; i < a.length; i += STRIDE) a[i + 12] = cache.get(key(a, i));
        }

        static long key(float[] a, int i) {
            return (Math.round(a[i] * 200.0) * 73856093L) ^ (Math.round(a[i + 1] * 200.0) * 19349663L) ^ (Math.round(a[i + 2] * 200.0) * 83492791L)
                    ^ (Math.round(a[i + 3] * 8.0) * 4256249L) ^ (Math.round(a[i + 4] * 8.0) * 15485863L) ^ (Math.round(a[i + 5] * 8.0) * 32452843L);
        }

        float ao(float[] a, int i, double[][] dirs) {
            double nx = a[i + 3], ny = a[i + 4], nz = a[i + 5];
            int mat = (int) a[i + 11];
            // Netze sind zweiseitig: die Seite, die mehr Himmel sieht, zählt (Innenwände sehen nach innen)
            double best = 0;
            boolean both = mat == M_PAINT || mat == M_INTERIOR || mat == M_CANVAS || mat == M_GLASS;
            for (int sgn = 1; sgn >= (both ? -1 : 1); sgn -= 2) {
                double ox = a[i] + nx * sgn * 0.012, oy = a[i + 1] + ny * sgn * 0.012, oz = a[i + 2] + nz * sgn * 0.012;
                double[] n = {nx * sgn, ny * sgn, nz * sgn};
                double[] u = B.norm(B.cross(n, Math.abs(n[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0}));
                double[] v = B.cross(n, u);
                int open = 0;
                for (double[] d : dirs) {
                    double dx = u[0] * d[0] + n[0] * d[1] + v[0] * d[2], dy = u[1] * d[0] + n[1] * d[1] + v[1] * d[2], dz = u[2] * d[0] + n[2] * d[1] + v[2] * d[2];
                    if (!hit(ox, oy, oz, dx, dy, dz, 6.0)) open++;
                }
                double f = (double) open / dirs.length;
                best = Math.max(best, f);
            }
            return (float) best;
        }

        void build(int nt) {
            idx = new int[nt];
            for (int i = 0; i < nt; i++) idx[i] = i;
            int cap = 2 * nt + 1;
            bmin = new float[cap * 3]; bmax = new float[cap * 3];
            left = new int[cap]; right = new int[cap]; start = new int[cap]; count = new int[cap];
            nodes = 0;
            node(0, nt);
        }

        int node(int s, int n) {
            int id = nodes++;
            float[] mn = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, mx = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (int i = s; i < s + n; i++) {
                int t = idx[i];
                if (Float.isNaN(tri[t * 9])) continue;
                for (int c = 0; c < 3; c++)
                    for (int d = 0; d < 3; d++) { float x = tri[t * 9 + c * 3 + d]; mn[d] = Math.min(mn[d], x); mx[d] = Math.max(mx[d], x); }
            }
            for (int d = 0; d < 3; d++) { bmin[id * 3 + d] = mn[d]; bmax[id * 3 + d] = mx[d]; }
            if (n <= 6) { start[id] = s; count[id] = n; left[id] = right[id] = -1; return id; }
            int ax = 0;
            float ext = -1;
            for (int d = 0; d < 3; d++) if (mx[d] - mn[d] > ext) { ext = mx[d] - mn[d]; ax = d; }
            final int axis = ax;
            Integer[] sub = new Integer[n];
            for (int i = 0; i < n; i++) sub[i] = idx[s + i];
            Arrays.sort(sub, (p, q) -> Float.compare(cent(p, axis), cent(q, axis)));
            for (int i = 0; i < n; i++) idx[s + i] = sub[i];
            int h = n / 2;
            count[id] = 0;
            left[id] = node(s, h);
            right[id] = node(s + h, n - h);
            return id;
        }

        float cent(int t, int ax) {
            float a = tri[t * 9 + ax];
            if (Float.isNaN(a)) return 0;
            return a + tri[t * 9 + 3 + ax] + tri[t * 9 + 6 + ax];
        }

        boolean hit(double ox, double oy, double oz, double dx, double dy, double dz, double tmax) {
            int[] stack = new int[128];
            int sp = 0;
            stack[sp++] = 0;
            double ix = 1 / (Math.abs(dx) < 1e-12 ? 1e-12 : dx), iy = 1 / (Math.abs(dy) < 1e-12 ? 1e-12 : dy), iz = 1 / (Math.abs(dz) < 1e-12 ? 1e-12 : dz);
            while (sp > 0) {
                int id = stack[--sp];
                double t0 = 0, t1 = tmax;
                double a = (bmin[id * 3] - ox) * ix, b = (bmax[id * 3] - ox) * ix;
                t0 = Math.max(t0, Math.min(a, b)); t1 = Math.min(t1, Math.max(a, b));
                a = (bmin[id * 3 + 1] - oy) * iy; b = (bmax[id * 3 + 1] - oy) * iy;
                t0 = Math.max(t0, Math.min(a, b)); t1 = Math.min(t1, Math.max(a, b));
                a = (bmin[id * 3 + 2] - oz) * iz; b = (bmax[id * 3 + 2] - oz) * iz;
                t0 = Math.max(t0, Math.min(a, b)); t1 = Math.min(t1, Math.max(a, b));
                if (t0 > t1) continue;
                if (left[id] < 0) {
                    for (int i = start[id]; i < start[id] + count[id]; i++) if (tri(idx[i], ox, oy, oz, dx, dy, dz, tmax)) return true;
                } else {
                    if (sp + 2 >= stack.length) stack = Arrays.copyOf(stack, stack.length * 2);
                    stack[sp++] = left[id];
                    stack[sp++] = right[id];
                }
            }
            return false;
        }

        boolean tri(int t, double ox, double oy, double oz, double dx, double dy, double dz, double tmax) {
            int b = t * 9;
            if (Float.isNaN(tri[b])) return false;
            double e1x = tri[b + 3] - tri[b], e1y = tri[b + 4] - tri[b + 1], e1z = tri[b + 5] - tri[b + 2];
            double e2x = tri[b + 6] - tri[b], e2y = tri[b + 7] - tri[b + 1], e2z = tri[b + 8] - tri[b + 2];
            double px = dy * e2z - dz * e2y, py = dz * e2x - dx * e2z, pz = dx * e2y - dy * e2x;
            double det = e1x * px + e1y * py + e1z * pz;
            if (Math.abs(det) < 1e-12) return false;
            double inv = 1 / det;
            double tx = ox - tri[b], ty = oy - tri[b + 1], tz = oz - tri[b + 2];
            double u = (tx * px + ty * py + tz * pz) * inv;
            if (u < 0 || u > 1) return false;
            double qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
            double v = (dx * qx + dy * qy + dz * qz) * inv;
            if (v < 0 || u + v > 1) return false;
            double tt = (e2x * qx + e2y * qy + e2z * qz) * inv;
            return tt > 1e-3 && tt < tmax;
        }

        /** Kante je Ecke: 1 − kleinstes Skalarprodukt mit den Normalen anderer Flächen am selben Ort (0 glatt … 1 rechtwinklig). */
        static void edges(float[] a) {
            Map<Long, List<Integer>> at = new HashMap<>();
            for (int i = 0; i < a.length; i += STRIDE) {
                long key = (Math.round(a[i] * 500.0) * 73856093L) ^ (Math.round(a[i + 1] * 500.0) * 19349663L) ^ (Math.round(a[i + 2] * 500.0) * 83492791L);
                at.computeIfAbsent(key, x -> new ArrayList<>()).add(i);
            }
            for (List<Integer> l : at.values()) {
                double mn = 1;
                for (int p : l)
                    for (int q : l) mn = Math.min(mn, Math.abs(a[p + 3] * a[q + 3] + a[p + 4] * a[q + 4] + a[p + 5] * a[q + 5]));
                float e = (float) Math.min(1, 1 - mn);
                for (int p : l) a[p + 13] = e;
            }
        }
    }
}
