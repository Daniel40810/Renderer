package com.dan.bear;

import java.util.Arrays;

/**
 * Der Körper eines Braunbären (Phase 18a von LituyaBay1958): ein Netz in Körperkoordinaten und die Biegung, die es in
 * eine Haltung bringt. Java und {@code gl/bear.vert} rechnen dieselbe Biegung; Java braucht sie für die Demo, für die
 * Pfoten auf dem Grund und für den Kopf.
 * <p>
 * <b>Körperkoordinaten</b> (Kopf-Rumpf-Länge 1, das Tier wird mit seiner Länge skaliert): x rechts, y oben, z vorn; s
 * läuft entlang der Wirbelsäule von der Nase (0) bis zum Schwanzstummel (1). Der Anker der Biegung ist die Hüfte
 * ({@link #ANCHOR}): um sie richtet sich der Bär auf. Der Rumpf ist schwer und rund, über den Schultern der Buckel
 * (der Muskel zum Graben), der Kopf breit mit gerader Schnauze und kleinen runden Ohren, der Schwanz ein Stummel
 * [G, nach Fotos und Maßen von Ursus arctos an der Küste Alaskas].
 * <p>
 * <b>Biegung</b> wie bei {@code com.dan.seal}: An jeder Stelle s hat die Wirbelsäule einen Neigungswinkel (Kopf heben,
 * Buckel, ganze Neigung) und einen Gierwinkel (Kopf drehen); die Lage eines Punkts ist das Integral der Richtung vom
 * Anker bis s in {@link #STEPS} Schritten. Querschnitt, Ohren und Beine hängen im Rahmen ihrer Stelle. Der Kiefer öffnet
 * sich um ein Scharnier hinter dem Maul. Danach dreht die Rolle (auf der Seite liegen) den ganzen Körper.
 * <p>
 * <b>Beine</b>: je Bein Oberarm bzw. Oberschenkel, Unterarm bzw. Unterschenkel und Pfote, starre Röhren um die
 * Gelenke. Zwei Winkel je Bein: Schwung (+ nach vorn) und Beugung (hebt die Pfote nach hinten). Vorn tritt der Bär
 * mit den Zehen und Ballen auf, hinten mit der ganzen Sohle (Sohlengänger).
 */
public final class BearBody {
    /** Haltung als Zahlen (Bogenmaß); dieselbe Reihenfolge im Shader. */
    public static final int HEAD_LIFT = 0, HEAD_YAW = 1, ARCH = 2, PITCH = 3, ROLL = 4, JAW = 5,
            SWING = 6, BEND = 10, N = 14;
    /** Beine: vorn links, vorn rechts, hinten links, hinten rechts. */
    public static final int LF = 0, RF = 1, LH = 2, RH = 3;

    /** Anker (Hüfte), Schritte der Integration, Ende des Rumpfs. */
    public static final double ANCHOR = 0.80, END = 1.0;
    public static final int STEPS = 10;
    /** Belegung eines Eckpunkts: Versatz x, y, z im Rahmen, Normale, s, Teil, Winkel um die Achse. */
    public static final int FLOATS = 9;
    /** Teile: 0 Rumpf, 1 Ohr, 10 + 3·Bein + Glied (0 oben, 1 unten, 2 Pfote). */
    public static final int BODY = 0, EAR = 1, LEG0 = 10;

    /** Querschnitt entlang s: s, halbe Breite, Höhe oben, Höhe unten, Mitte (Anteile der Länge) [G]. */
    static final double[][] PROFILE = {
            {0.000, 0.020, 0.018, 0.016, -0.090},
            {0.020, 0.040, 0.034, 0.030, -0.086},
            {0.055, 0.054, 0.048, 0.042, -0.078},
            {0.095, 0.078, 0.070, 0.058, -0.066},
            {0.135, 0.100, 0.088, 0.072, -0.054},
            {0.175, 0.098, 0.092, 0.084, -0.040},
            {0.225, 0.112, 0.114, 0.116, -0.020},
            {0.290, 0.142, 0.174, 0.156, -0.006},
            {0.345, 0.158, 0.190, 0.172, 0.000},
            {0.420, 0.162, 0.160, 0.184, 0.000},
            {0.540, 0.164, 0.132, 0.190, 0.000},
            {0.660, 0.164, 0.122, 0.178, 0.000},
            {0.780, 0.154, 0.128, 0.150, 0.002},
            {0.880, 0.128, 0.120, 0.114, 0.004},
            {0.950, 0.080, 0.078, 0.066, 0.006},
            {1.000, 0.024, 0.026, 0.020, 0.010}};

    /**
     * Die Beine: Stelle s, Seite, Ansatz y, Ruhewinkel oben und unten, Längen oben und unten, Halbmesser oben, am Gelenk
     * und unten, Pfote: Länge, Breite, Dicke, Versatz nach vorn [G].
     */
    static final double[][] LEGS = {
            {0.33, -1, -0.04, 0.00, 0.00, 0.160, 0.160, 0.088, 0.066, 0.058, 0.100, 0.080, 0.034, 0.032},
            {0.33, 1, -0.04, 0.00, 0.00, 0.160, 0.160, 0.088, 0.066, 0.058, 0.100, 0.080, 0.034, 0.032},
            {0.80, -1, -0.04, 0.30, -0.36, 0.170, 0.164, 0.100, 0.064, 0.054, 0.135, 0.070, 0.030, 0.045},
            {0.80, 1, -0.04, 0.30, -0.36, 0.170, 0.164, 0.100, 0.064, 0.054, 0.135, 0.070, 0.030, 0.045}};
    /** Seitlicher Ansatz der Beine (Anteil der halben Breite an der Stelle). */
    static final double LEG_OUT = 0.66;

    private BearBody() { }

    /** Querschnitt an der Stelle s: {halbe Breite, oben, unten, Mitte}. */
    public static double[] section(double s) {
        double[][] p = PROFILE;
        if (s <= p[0][0]) return new double[]{p[0][1], p[0][2], p[0][3], p[0][4]};
        for (int i = 1; i < p.length; i++)
            if (s <= p[i][0]) {
                double t = (s - p[i - 1][0]) / (p[i][0] - p[i - 1][0]);
                t = t * t * (3 - 2 * t);
                double[] r = new double[4];
                for (int k = 0; k < 4; k++) r[k] = p[i - 1][k + 1] + (p[i][k + 1] - p[i - 1][k + 1]) * t;
                return r;
            }
        double[] l = p[p.length - 1];
        return new double[]{l[1], l[2], l[3], l[4]};
    }

    // ------------------------------------------------------------------ Biegung (wie bear.vert)

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Neigung der Wirbelsäule an der Stelle s (rad, + steigt nach vorn). */
    public static double pitchAt(double s, float[] p) {
        double head = 1 - smooth(0.12, 0.34, s);
        return p[PITCH] + p[HEAD_LIFT] * head + p[ARCH] * Math.max(-1, Math.min(1, (s - 0.56) * 2.6));
    }

    /** Gierung der Wirbelsäule an der Stelle s (rad, + nach links). */
    public static double yawAt(double s, float[] p) {
        return p[HEAD_YAW] * (1 - smooth(0.10, 0.36, s));
    }

    /** Punkt der Wirbelsäule an der Stelle s (Länge 1, Hüfte im Ursprung, vor der Rolle). */
    public static double[] spine(double s, float[] p) {
        double ds = (ANCHOR - s) / STEPS, x = 0, y = 0, z = 0;
        for (int k = 0; k < STEPS; k++) {
            double m = s + (k + 0.5) * ds, ph = pitchAt(m, p), yw = yawAt(m, p);
            x += Math.sin(yw) * Math.cos(ph) * ds;
            y += Math.sin(ph) * ds;
            z += Math.cos(yw) * Math.cos(ph) * ds;
        }
        return new double[]{x, y, z};
    }

    /** Rahmen der Stelle s: {Mitte, vorn, rechts, oben} (je 3 Werte, vor der Rolle). */
    static double[][] frame(double s, float[] p) {
        double[] c = spine(s, p);
        double ph = pitchAt(s, p), yw = yawAt(s, p);
        double[] f = {Math.sin(yw) * Math.cos(ph), Math.sin(ph), Math.cos(yw) * Math.cos(ph)};
        double[] r = {Math.cos(yw), 0, -Math.sin(yw)};
        double[] u = {f[1] * r[2] - f[2] * r[1], f[2] * r[0] - f[0] * r[2], f[0] * r[1] - f[1] * r[0]};
        return new double[][]{c, f, r, u};
    }

    /** Drehung um die x-Achse: + kippt „unten“ nach vorn (und „vorn“ nach oben). {y, z}. */
    static double[] rotX(double y, double z, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[]{y * c + z * s, -y * s + z * c};
    }

    /** Winkel der drei Glieder eines Beins in seinem Rahmen: {oben, unten, Pfote}. */
    public static double[] legAngles(int leg, float[] p) {
        double[] L = LEGS[leg];
        double sw = p[SWING + leg], b = p[BEND + leg];
        double a1 = L[3] + sw, a2 = a1 + L[4] - b;
        boolean fore = leg < 2;
        double ap = fore ? -a2 * 0.75 - b * 0.55 : -a2 * 0.85 - b * 0.35;   // Pfote bleibt flach, beim Heben hängt sie
        return new double[]{a1, a2, a2 + ap};
    }

    /** Das Schulterblatt (vorn) und das Becken (hinten) gleiten mit dem Schwung des Beins mit (Anteil der Länge) [G]. */
    static final double[] SLIDE = {0.10, 0.10, 0.05, 0.05};

    /** Versatz des Ansatzes nach vorn bei diesem Schwung. */
    static double slide(int leg, double swing) { return SLIDE[leg] * Math.sin(swing); }

    /** Gelenke eines Beins im Rahmen der Stelle (y, z; x bleibt): {Ansatz, Knie/Ellbogen, Fuß/Handgelenk}. */
    static double[][] legJoints(int leg, float[] p) {
        double[] L = LEGS[leg], a = legAngles(leg, p);
        double[] j0 = {L[2], slide(leg, p[SWING + leg])};
        double[] d1 = rotX(-1, 0, a[0]), d2 = rotX(-1, 0, a[1]);
        double[] j1 = {j0[0] + d1[0] * L[5], j0[1] + d1[1] * L[5]};
        double[] j2 = {j1[0] + d2[0] * L[6], j1[1] + d2[1] * L[6]};
        return new double[][]{j0, j1, j2};
    }

    /**
     * Ein Eckpunkt in seine Haltung: Versatz (im Rahmen der Stelle s, bei Beinen im Rahmen des Glieds ab seinem oberen
     * Gelenk), Teil, Normale; Ergebnis {x, y, z, nx, ny, nz} in Körperkoordinaten (Länge 1), nach der Rolle.
     */
    public static double[] deform(double ox, double oy, double oz, double nx, double ny, double nz, double s, int part, double ang, float[] p) {
        if (part >= LEG0) {
            int leg = (part - LEG0) / 3, seg = (part - LEG0) % 3;
            double[] L = LEGS[leg];
            s = L[0];
            double[] a = legAngles(leg, p);
            double[][] j = legJoints(leg, p);
            double[] q = rotX(oy, oz, a[seg]), qn = rotX(ny, nz, a[seg]);
            double xo = L[1] * section(s)[0] * LEG_OUT;
            ox = xo + ox;
            oy = j[seg][0] + q[0];
            oz = j[seg][1] + q[1];
            ny = qn[0];
            nz = qn[1];
        } else if (part == BODY && s < 0.125 && Math.cos(ang) < -0.15) {
            // Unterkiefer: um das Scharnier bei s = 0,125 nach unten
            double k = smooth(-0.15, -0.55, Math.cos(ang));
            double[] q = rotX(oy, (0.125 - s), -p[JAW] * k);
            oy = q[0];
            oz += q[1] - (0.125 - s);
        }
        double[][] fr = frame(s, p);
        double[] c = fr[0], f = fr[1], r = fr[2], u = fr[3];
        double px = c[0] + r[0] * ox + u[0] * oy + f[0] * oz, py = c[1] + r[1] * ox + u[1] * oy + f[1] * oz, pz = c[2] + r[2] * ox + u[2] * oy + f[2] * oz;
        double qx = r[0] * nx + u[0] * ny + f[0] * nz, qy = r[1] * nx + u[1] * ny + f[1] * nz, qz = r[2] * nx + u[2] * ny + f[2] * nz;
        double cr = Math.cos(p[ROLL]), sr = Math.sin(p[ROLL]);
        return new double[]{px * cr - py * sr, px * sr + py * cr, pz, qx * cr - qy * sr, qx * sr + qy * cr, qz};
    }

    // ------------------------------------------------------------------ Netz

    /** Ringe entlang des Rumpfs, Ecken je Ring; Seiten der Beine. */
    public static final int RINGS = 44, AROUND = 24, LEG_SIDES = 12;

    /** Das Netz als Dreiecke (je Ecke {@link #FLOATS} Werte). */
    public static float[] mesh() {
        FloatList out = new FloatList();
        double[][][] ring = new double[RINGS][AROUND][];
        double[] ss = new double[RINGS];
        for (int i = 0; i < RINGS; i++) {
            double t = i / (double) (RINGS - 1);
            ss[i] = END * (t < 0.35 ? 0.34 * Math.pow(t / 0.35, 1.25) : 0.34 + 0.66 * (t - 0.35) / 0.65);
        }
        for (int i = 0; i < RINGS; i++) {
            double[] q = section(ss[i]);
            for (int k = 0; k < AROUND; k++) {
                double a = 2 * Math.PI * k / AROUND, ca = Math.cos(a), sa = Math.sin(a);
                // oben runder, unten breit und flach (Bauch), an der Schnauze fast eckig
                double y = ca >= 0 ? q[1] * Math.pow(ca, 0.85) : -q[2] * Math.pow(-ca, 0.7);
                double x = q[0] * Math.signum(sa) * Math.pow(Math.abs(sa), 0.85);
                ring[i][k] = new double[]{x, y + q[3], 0, a};
            }
        }
        for (int i = 0; i < RINGS - 1; i++)
            for (int k = 0; k < AROUND; k++) emitQuad(out, ring, ss, i, k, i + 1, (k + 1) % AROUND);
        double[] q0 = section(0);
        for (int k = 0; k < AROUND; k++) {
            int k1 = (k + 1) % AROUND, e = RINGS - 1;
            vert(out, 0, q0[3], 0.006, 0, 0, 1, 0, BODY, 0);
            vert(out, ring[0][k1][0], ring[0][k1][1], 0, ring[0][k1][0] * 0.3, (ring[0][k1][1] - q0[3]) * 0.3, 1, ss[0], BODY, ring[0][k1][3]);
            vert(out, ring[0][k][0], ring[0][k][1], 0, ring[0][k][0] * 0.3, (ring[0][k][1] - q0[3]) * 0.3, 1, ss[0], BODY, ring[0][k][3]);
            double[] qe = section(END);
            vert(out, 0, qe[3], -0.008, 0, 0, -1, END, BODY, 0);
            vert(out, ring[e][k][0], ring[e][k][1], 0, ring[e][k][0], ring[e][k][1] - qe[3], -0.5, END, BODY, ring[e][k][3]);
            vert(out, ring[e][k1][0], ring[e][k1][1], 0, ring[e][k1][0], ring[e][k1][1] - qe[3], -0.5, END, BODY, ring[e][k1][3]);
        }
        // Ohren: kleine runde Kappen oben am Kopf, leicht nach außen
        for (int side = -1; side <= 1; side += 2) ear(out, side);
        // Beine
        for (int leg = 0; leg < 4; leg++) leg(out, leg);
        return out.toArray();
    }

    private static void emitQuad(FloatList out, double[][][] ring, double[] ss, int i0, int k0, int i1, int k1) {
        int[][] c = {{i0, k0}, {i1, k0}, {i1, k1}, {i0, k0}, {i1, k1}, {i0, k1}};
        for (int[] ik : c) {
            int i = ik[0], k = ik[1];
            double[] v = ring[i][k], n = ringNormal(ring, ss, i, k);
            vert(out, v[0], v[1], 0, n[0], n[1], n[2], ss[i], BODY, v[3]);
        }
    }

    private static double[] ringNormal(double[][][] ring, double[] ss, int i, int k) {
        int n = ring.length, m = ring[0].length;
        double[] a = ring[i][(k + 1) % m], b = ring[i][(k - 1 + m) % m];
        double tx = a[0] - b[0], ty = a[1] - b[1];
        double nx = -ty, ny = tx, nl = Math.hypot(nx, ny);
        nx /= Math.max(1e-9, nl);
        ny /= Math.max(1e-9, nl);
        int ip = Math.min(n - 1, i + 1), im = Math.max(0, i - 1);
        double[] cp = section(ss[ip]), cm = section(ss[im]);
        double rp = Math.hypot(ring[ip][k][0], ring[ip][k][1] - cp[3]), rm = Math.hypot(ring[im][k][0], ring[im][k][1] - cm[3]);
        double dz = -(ss[ip] - ss[im]);
        double nz = Math.abs(dz) < 1e-9 ? 0 : -(rp - rm) / dz;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return new double[]{nx / l, ny / l, nz / l};
    }

    /** Ein Ohr: Halbkugel, flach gedrückt, an der Stelle 0,15 oben seitlich. */
    private static void ear(FloatList out, int side) {
        double s = 0.15;
        double[] q = section(s);
        double cx = side * q[0] * 0.62, cy = q[3] + q[1] * 0.80, r = 0.030;
        int lat = 5, lon = 10;
        for (int i = 0; i < lat; i++)
            for (int j = 0; j < lon; j++) {
                double[][] v = new double[4][];
                int[][] ij = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int c = 0; c < 4; c++) {
                    double th = Math.PI / 2 * ij[c][0] / lat, ph = 2 * Math.PI * ij[c][1] / lon;
                    double nx = Math.sin(th) * Math.cos(ph), nz = Math.sin(th) * Math.sin(ph) * 0.35, ny = Math.cos(th);
                    // nach außen geneigt
                    double tilt = side * 0.45, ex = nx * Math.cos(tilt) + ny * Math.sin(tilt), ey = -nx * Math.sin(tilt) + ny * Math.cos(tilt);
                    v[c] = new double[]{cx + ex * r, cy + ey * r, nz * r, ex, ey, nz / 0.35};
                }
                for (int t : new int[]{0, 1, 2, 0, 2, 3}) vert(out, v[t][0], v[t][1], v[t][2], v[t][3], v[t][4], v[t][5], s, EAR, 0);
            }
    }

    /** Ein Bein: zwei Röhren und eine Pfote, je im Rahmen ihres Glieds ab dem oberen Gelenk. */
    private static void leg(FloatList out, int leg) {
        double[] L = LEGS[leg];
        tube(out, LEG0 + 3 * leg, L[0], L[5], L[7], L[8], 0.06);
        tube(out, LEG0 + 3 * leg + 1, L[0], L[6], L[8], L[9], 0.0);
        paw(out, LEG0 + 3 * leg + 2, L[0], L[10], L[11], L[12], L[13], leg < 2);
    }

    /** Röhre von y = +über bis y = −len, Halbmesser r0 oben, r1 unten (etwas oval: vorn-hinten breiter). */
    private static void tube(FloatList out, int part, double s, double len, double r0, double r1, double over) {
        int rings = 5;
        double[][][] v = new double[rings + 1][LEG_SIDES + 1][];
        for (int i = 0; i <= rings; i++) {
            double t = i / (double) rings, y = over - (len + over) * t;
            double r = r0 + (r1 - r0) * t;
            // Gelenk dicker: am Ende abrunden
            double cap = i == 0 || i == rings ? 0.85 : 1;
            for (int k = 0; k <= LEG_SIDES; k++) {
                double a = 2 * Math.PI * k / LEG_SIDES, ca = Math.cos(a), sa = Math.sin(a);
                v[i][k] = new double[]{sa * r * 0.82 * cap, y, ca * r * 1.08 * cap, sa, 0, ca, a};
            }
        }
        for (int i = 0; i < rings; i++)
            for (int k = 0; k < LEG_SIDES; k++) {
                double[][] q = {v[i][k], v[i + 1][k], v[i + 1][k + 1], v[i][k + 1]};
                for (int t : new int[]{0, 1, 2, 0, 2, 3}) vert(out, q[t][0], q[t][1], q[t][2], q[t][3], q[t][4], q[t][5], s, part, q[t][6]);
            }
        // unterer Deckel (rund)
        for (int k = 0; k < LEG_SIDES; k++) {
            double[] a = v[rings][k], b = v[rings][k + 1];
            vert(out, 0, -len - r1 * 0.4, 0, 0, -1, 0, s, part, 0);
            vert(out, b[0], b[1], b[2], b[3], -0.5, b[5], s, part, b[6]);
            vert(out, a[0], a[1], a[2], a[3], -0.5, a[5], s, part, a[6]);
        }
    }

    /** Pfote: ein gerundeter Kasten unter dem Gelenk, vorn länger (Zehen), mit dem Winkel 1 (Krallen) vorn. */
    private static void paw(FloatList out, int part, double s, double len, double wid, double thick, double fwd, boolean fore) {
        int nl = 6, nw = 6;
        // Ellipsoid-artige Hülle: Länge z von fwd-len·0,35 bis fwd+len·0,65, Breite x, Dicke y unter dem Gelenk
        double z0 = fwd - len * (fore ? 0.45 : 0.55), z1 = fwd + len * (fore ? 0.55 : 0.45), cy = -thick * 0.5;
        double[][][] v = new double[nl + 1][nw + 1][];
        for (int i = 0; i <= nl; i++) {
            double t = i / (double) nl, th = Math.PI * t, z = (z0 + z1) / 2 - Math.cos(th) * (z1 - z0) / 2;
            double rr = Math.sin(th);
            for (int k = 0; k <= nw; k++) {
                double a = 2 * Math.PI * k / nw, ca = Math.cos(a), sa = Math.sin(a);
                double x = sa * wid * 0.5 * Math.pow(rr, 0.6), y = cy + (ca >= 0 ? ca * thick * 0.6 : ca * thick * 0.5) * Math.pow(rr, 0.6);
                double nx = sa * Math.pow(rr, 0.6), ny = ca * Math.pow(rr, 0.6), nz = -Math.cos(th);
                double nn = Math.sqrt(nx * nx + ny * ny + nz * nz);
                // Krallen: vorderste Ringe, unten (ang 1 = Krallen, 0 = Fell)
                v[i][k] = new double[]{x, y, z, nx / nn, ny / nn, nz / nn, t > 0.8 && ca < 0.3 ? 1 : 0};
            }
        }
        for (int i = 0; i < nl; i++)
            for (int k = 0; k < nw; k++) {
                double[][] q = {v[i][k], v[i + 1][k], v[i + 1][k + 1], v[i][k + 1]};
                for (int t : new int[]{0, 1, 2, 0, 2, 3}) vert(out, q[t][0], q[t][1], q[t][2], q[t][3], q[t][4], q[t][5], s, part, q[t][6]);
            }
    }

    private static void vert(FloatList o, double x, double y, double z, double nx, double ny, double nz, double s, int part, double ang) {
        o.add(x); o.add(y); o.add(z); o.add(nx); o.add(ny); o.add(nz); o.add(s); o.add(part); o.add(ang);
    }

    /** Wachsende Liste von float. */
    static final class FloatList {
        float[] a = new float[8192];
        int n;
        void add(double v) { if (n == a.length) a = Arrays.copyOf(a, n * 2); a[n++] = (float) v; }
        float[] toArray() { return Arrays.copyOf(a, n); }
    }

    // ------------------------------------------------------------------ Abfragen für das Verhalten

    /** Tiefster Punkt der Pfote eines Beins (Länge 1, relativ zur Hüfte, nach der Rolle): {x, y, z}. */
    public static double[] foot(int leg, float[] p) {
        double[] L = LEGS[leg];
        double best = Double.MAX_VALUE;
        double[] r = null;
        double z0 = L[13] - L[10] * (leg < 2 ? 0.45 : 0.55), z1 = L[13] + L[10] * (leg < 2 ? 0.55 : 0.45);
        for (int i = 0; i <= 4; i++) {
            double z = z0 + (z1 - z0) * (0.15 + 0.7 * i / 4.0);
            double[] q = deform(0, -L[12] * 1.1, z, 0, -1, 0, L[0], LEG0 + 3 * leg + 2, 0, p);
            if (q[1] < best) { best = q[1]; r = q; }
        }
        return new double[]{r[0], r[1], r[2]};
    }

    /** Kleinste Beugung je Bein (rad): vorn gestreckt bis 0, hinten etwas über die Ruhe hinaus [G]. */
    static double minBend(int leg) { return leg < 2 ? 0 : -0.30; }

    /** Größte Reichweite eines Beins vom Ansatz bis zum Fußgelenk (Länge 1). */
    public static double maxReach(int leg) {
        double[] L = LEGS[leg];
        double th = Math.max(0, -L[4] + minBend(leg));   // kleinste Knickung im Gelenk
        return Math.sqrt(L[5] * L[5] + L[6] * L[6] + 2 * L[5] * L[6] * Math.cos(th));
    }

    /**
     * Setzt Schwung und Beugung eines Beins so, dass die Mitte seiner Sohle bei (y, z) in Körperkoordinaten steht
     * (Länge 1, relativ zur Hüfte, Rolle 0); die Wirbelsäule muss in p schon stehen. Zwei Glieder, das Gelenk nach vorn
     * gebeugt, die Pfote nach {@link #legAngles}. Gibt false, wenn das Ziel zu weit ist (dann so weit wie möglich).
     */
    public static boolean solveLeg(int leg, double y, double z, float[] p) {
        double[] L = LEGS[leg];
        double[][] fr = frame(L[0], p);
        double[] c = fr[0], f = fr[1], u = fr[3];
        double ty = (0 - c[0]) * 0 + (y - c[1]) * u[1] + (z - c[2]) * u[2] + (0 - c[0]) * u[0];
        double tz = (y - c[1]) * f[1] + (z - c[2]) * f[2] + (0 - c[0]) * f[0];
        ty -= L[2];
        double L1 = L[5], L2 = L[6], dmax = maxReach(leg) * 0.9999;
        boolean ok = true;
        double a1 = L[3] + p[SWING + leg], a2 = a1 + L[4] - p[BEND + leg];
        double tz0 = tz;
        for (int it = 0; it < 6; it++) {
            boolean fore = leg < 2;
            tz = tz0 - slide(leg, a1 - L[3]);
            double b = a1 + L[4] - a2;
            double ap = fore ? -a2 * 0.75 - b * 0.55 : -a2 * 0.85 - b * 0.35;
            double[] off = rotX(-L[12], L[13], a2 + ap);
            double ay = ty - off[0], az = tz - off[1];
            double d = Math.hypot(ay, az);
            if (d > dmax) { ok = false; ay *= dmax / d; az *= dmax / d; d = dmax; }
            double dmin = Math.abs(L1 - L2) + 1e-4;
            if (d < dmin) { ay *= dmin / d; az *= dmin / d; d = dmin; }
            double phi = Math.atan2(az, -ay);
            double al = Math.acos(Math.max(-1, Math.min(1, (L1 * L1 + d * d - L2 * L2) / (2 * L1 * d))));
            double be = Math.acos(Math.max(-1, Math.min(1, (L2 * L2 + d * d - L1 * L1) / (2 * L2 * d))));
            a1 = phi + al;
            a2 = phi - be;
        }
        double b = a1 + L[4] - a2;
        if (b < minBend(leg)) { b = minBend(leg); ok = false; }
        p[SWING + leg] = (float) (a1 - L[3]);
        p[BEND + leg] = (float) b;
        return ok;
    }

    /** Mitte der Sohle eines Beins (Länge 1, relativ zur Hüfte, nach der Rolle): der Punkt, den der Gang führt. */
    public static double[] sole(int leg, float[] p) {
        double[] L = LEGS[leg];
        double[] q = deform(0, -L[12], L[13], 0, -1, 0, L[0], LEG0 + 3 * leg + 2, 0, p);
        return new double[]{q[0], q[1], q[2]};
    }

    /** Tiefste Sohle der vier Beine (Länge 1): auf diese Höhe stellt das Verhalten den Grund. */
    public static double soleLevel(float[] p) {
        double m = Double.MAX_VALUE;
        for (int l = 0; l < 4; l++) m = Math.min(m, sole(l, p)[1]);
        return m;
    }

    /** Tiefster Punkt der vier Pfoten (Länge 1): so tief liegt der Grund unter der Hüfte, wenn der Bär steht. */
    public static double lowestFoot(float[] p) {
        double m = Double.MAX_VALUE;
        for (int l = 0; l < 4; l++) m = Math.min(m, foot(l, p)[1]);
        return m;
    }

    /** Ein grobes Abbild des Rumpfs (Unterseite) für {@link #lowest}. */
    private static final double[][] PROBES;
    static {
        java.util.List<double[]> l = new java.util.ArrayList<>();
        for (int i = 0; i <= 16; i++) {
            double s = END * i / 16.0;
            double[] q = section(s);
            for (int k = 0; k < 8; k++) {
                double a = 2 * Math.PI * k / 8, ca = Math.cos(a), sa = Math.sin(a);
                double y = ca >= 0 ? q[1] * ca : -q[2] * Math.pow(-ca, 0.7);
                l.add(new double[]{q[0] * sa, y + q[3], s, a});
            }
        }
        PROBES = l.toArray(new double[0][]);
    }

    /** Tiefster Punkt des Rumpfs (ohne Beine; Länge 1, relativ zur Hüfte): für das Liegen. */
    public static double lowest(float[] p) {
        double m = Double.MAX_VALUE;
        for (double[] q : PROBES) m = Math.min(m, deform(q[0], q[1], 0, 0, 1, 0, q[2], BODY, q[3], p)[1]);
        return m;
    }

    /** Spitze der Nase (Länge 1, relativ zur Hüfte). */
    public static double[] nose(float[] p) {
        double[] q = section(0);
        double[] d = deform(0, q[3], 0.006, 0, 0, 1, 0, BODY, 0, p);
        return new double[]{d[0], d[1], d[2]};
    }

    /** Höchster Punkt (Schulter oder Kopf, Länge 1, relativ zur Hüfte). */
    public static double top(float[] p) {
        double m = -Double.MAX_VALUE;
        for (int i = 0; i <= 20; i++) {
            double s = i / 20.0;
            double[] q = section(s);
            m = Math.max(m, deform(0, q[3] + q[1], 0, 0, 1, 0, s, BODY, 0, p)[1]);
        }
        return m;
    }

    /** Länge der Wirbelsäule von der Nase bis zum Schwanz in dieser Haltung (bleibt bei jeder Biegung 1). */
    public static double spineLength(float[] p) {
        double len = 0;
        double[] a = spine(0, p);
        for (int i = 1; i <= 60; i++) {
            double[] b = spine(END * i / 60.0, p);
            len += Math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]));
            a = b;
        }
        return len;
    }

    /** Zahl der Dreiecke des Netzes. */
    public static int triangles() { return mesh().length / FLOATS / 3; }
}
