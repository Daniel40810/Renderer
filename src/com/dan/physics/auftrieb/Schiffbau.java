package com.dan.physics.auftrieb;

import java.util.ArrayList;
import java.util.List;

/**
 * Netze der Schwimmer aus ihrer {@link Form} (Seegang Werkbuch, S7b-3, Datei 88; SF108 A): der Rumpf aus denselben Spanten
 * wie der Körper ({@link Form.Rumpf#halbeBreite}), Deck, Kiel, Spiegel und Aufbauten wie in der Physik; dazu Zubehör, das
 * nur im Bild steht (Schornstein, Masten, Ruderhaus, Kurrbäume) [G], und der Anstrich nach den Fotos in
 * {@code HalligHooge\docs\img} [G]. Drehkörper (Tonnen) gedreht, Quader, Zylinder und Ellipsoide (Treibgut, Enten).
 * <p>
 * <b>Ablage</b> je Scheitel {@link #FLOATS} Werte: Lage im Körper (x vorn, y Backbord, z oben ab der Unterkante), Material;
 * Normale, Nummer des Körpers (0, der Lauf setzt sie); u, v, w (m, für Muster wie Fensterbänder), 0. Je drei Scheitel ein
 * Dreieck, gegen den Uhrzeigersinn von außen. Materialien je {@link #MAT} Werte: Albedo (linear) R, G, B, Art; Glanz,
 * Breite und Abstand der Fenster (m), 0. Arten: {@link #MATT}, {@link #LACK}, {@link #GLAS}, {@link #FENSTER},
 * {@link #METALL}, {@link #HOLZ}.
 * <p>
 * Das Package kennt kein Bild; das Netz ist nur Zahlen (die Anwendung reicht es an {@code render.schwimmer}).
 */
public final class Schiffbau {
    public static final String VERSION = "0.1";
    public static final int FLOATS = 12, MAT = 8;
    public static final int MATT = 0, LACK = 1, GLAS = 2, FENSTER = 3, METALL = 4, HOLZ = 5;

    /** Ein Netz mit seinen Materialien (Nummern im Netz zählen ab 0 in {@link #material}). */
    public static final class Netz {
        public final String name;
        public final float[] scheitel;
        public final float[] material;

        public Netz(String name, float[] scheitel, float[] material) {
            this.name = name;
            this.scheitel = scheitel;
            this.material = material;
        }

        public int scheitelZahl() { return scheitel.length / FLOATS; }

        public int materialZahl() { return material.length / MAT; }
    }

    // ------------------------------------------------------------------ Baukasten

    private final List<float[]> mat = new ArrayList<>();
    private float[] s = new float[FLOATS * 3 * 1024];
    private int n;

    /** Ein Material: Albedo (linear), Art, Glanz; Nummer zurück. */
    public int material(double r, double g, double b, int art, double glanz) { return material(r, g, b, art, glanz, 0, 0); }

    public int material(double r, double g, double b, int art, double glanz, double fensterBreite, double fensterAbstand) {
        mat.add(new float[] {(float) r, (float) g, (float) b, art, (float) glanz, (float) fensterBreite, (float) fensterAbstand, 0});
        return mat.size() - 1;
    }

    private void scheitel(double x, double y, double z, int m, double nx, double ny, double nz, double u, double v, double w) {
        if (n + FLOATS > s.length) s = java.util.Arrays.copyOf(s, 2 * s.length);
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-12) { nx = 0; ny = 0; nz = 1; l = 1; }
        s[n] = (float) x; s[n + 1] = (float) y; s[n + 2] = (float) z; s[n + 3] = m;
        s[n + 4] = (float) (nx / l); s[n + 5] = (float) (ny / l); s[n + 6] = (float) (nz / l); s[n + 7] = 0;
        s[n + 8] = (float) u; s[n + 9] = (float) v; s[n + 10] = (float) w; s[n + 11] = 0;
        n += FLOATS;
    }

    /** Ein Dreieck mit Normalen je Ecke (p, q, r je {x, y, z, nx, ny, nz, u, v}); gedreht, wenn es nach innen zeigt. */
    private void dreieck(double[] p, double[] q, double[] r, int m) {
        double ux = q[0] - p[0], uy = q[1] - p[1], uz = q[2] - p[2], vx = r[0] - p[0], vy = r[1] - p[1], vz = r[2] - p[2];
        double fx = uy * vz - uz * vy, fy = uz * vx - ux * vz, fz = ux * vy - uy * vx;
        if (fx * fx + fy * fy + fz * fz < 1e-14) return;
        double mx = p[3] + q[3] + r[3], my = p[4] + q[4] + r[4], mz = p[5] + q[5] + r[5];
        if (fx * mx + fy * my + fz * mz < 0) { double[] t = q; q = r; r = t; }
        for (double[] a : new double[][] {p, q, r}) scheitel(a[0], a[1], a[2], m, a[3], a[4], a[5], a[6], a[7], a.length > 8 ? a[8] : 0);
    }

    /** Ein Viereck a, b, c, d (Umlauf) mit einer Normale. */
    private void flaeche(double[] a, double[] b, double[] c, double[] d, double nx, double ny, double nz, int m) {
        double[][] e = {a, b, c, d};
        double[][] p = new double[4][];
        for (int i = 0; i < 4; i++) p[i] = new double[] {e[i][0], e[i][1], e[i][2], nx, ny, nz, e[i].length > 3 ? e[i][3] : 0, e[i].length > 4 ? e[i][4] : 0};
        dreieck(p[0], p[1], p[2], m);
        dreieck(p[0], p[2], p[3], m);
    }

    /** Ein Quader (achsparallel im Körper). Material je Seite: vorn/hinten, Seiten, oben, unten. */
    public void quader(double x0, double x1, double y0, double y1, double z0, double z1, int m) { quader(x0, x1, y0, y1, z0, z1, m, m, m); }

    public void quader(double x0, double x1, double y0, double y1, double z0, double z1, int mSeite, int mOben, int mUnten) {
        // u längs x bzw. y, v die Höhe über z0
        flaeche(new double[] {x1, y0, z0, y0, 0}, new double[] {x1, y1, z0, y1, 0}, new double[] {x1, y1, z1, y1, z1 - z0}, new double[] {x1, y0, z1, y0, z1 - z0}, 1, 0, 0, mSeite);
        flaeche(new double[] {x0, y0, z0, y0, 0}, new double[] {x0, y0, z1, y0, z1 - z0}, new double[] {x0, y1, z1, y1, z1 - z0}, new double[] {x0, y1, z0, y1, 0}, -1, 0, 0, mSeite);
        flaeche(new double[] {x0, y1, z0, x0, 0}, new double[] {x0, y1, z1, x0, z1 - z0}, new double[] {x1, y1, z1, x1, z1 - z0}, new double[] {x1, y1, z0, x1, 0}, 0, 1, 0, mSeite);
        flaeche(new double[] {x0, y0, z0, x0, 0}, new double[] {x1, y0, z0, x1, 0}, new double[] {x1, y0, z1, x1, z1 - z0}, new double[] {x0, y0, z1, x0, z1 - z0}, 0, -1, 0, mSeite);
        flaeche(new double[] {x0, y0, z1}, new double[] {x1, y0, z1}, new double[] {x1, y1, z1}, new double[] {x0, y1, z1}, 0, 0, 1, mOben);
        flaeche(new double[] {x0, y0, z0}, new double[] {x0, y1, z0}, new double[] {x1, y1, z0}, new double[] {x1, y0, z0}, 0, 0, -1, mUnten);
    }

    /**
     * Seit L5e1: eine schräge Strebe, im Schnitt x–z ein Viereck (unten von x0a bis x1a bei z0, oben von x0b bis x1b bei
     * z1), quer von y0 bis y1. Material wie {@link #quader}, u längs, v die Höhe über z0.
     */
    public void strebe(double x0a, double x1a, double x0b, double x1b, double y0, double y1, double z0, double z1, int m) {
        double h = z1 - z0;
        flaeche(new double[] {x1a, y0, z0, y0, 0}, new double[] {x1a, y1, z0, y1, 0}, new double[] {x1b, y1, z1, y1, h}, new double[] {x1b, y0, z1, y0, h}, h, 0, -(x1b - x1a), m);
        flaeche(new double[] {x0a, y0, z0, y0, 0}, new double[] {x0b, y0, z1, y0, h}, new double[] {x0b, y1, z1, y1, h}, new double[] {x0a, y1, z0, y1, 0}, -h, 0, x0b - x0a, m);
        flaeche(new double[] {x0a, y1, z0, x0a, 0}, new double[] {x0b, y1, z1, x0b, h}, new double[] {x1b, y1, z1, x1b, h}, new double[] {x1a, y1, z0, x1a, 0}, 0, 1, 0, m);
        flaeche(new double[] {x0a, y0, z0, x0a, 0}, new double[] {x1a, y0, z0, x1a, 0}, new double[] {x1b, y0, z1, x1b, h}, new double[] {x0b, y0, z1, x0b, h}, 0, -1, 0, m);
        flaeche(new double[] {x0b, y0, z1}, new double[] {x1b, y0, z1}, new double[] {x1b, y1, z1}, new double[] {x0b, y1, z1}, 0, 0, 1, m);
        flaeche(new double[] {x0a, y0, z0}, new double[] {x0a, y1, z0}, new double[] {x1a, y1, z0}, new double[] {x1a, y0, z0}, 0, 0, -1, m);
    }

    /**
     * Ein Aufbau mit einem Band Fenster in den Seiten: unten bis unten + hoehe · von Wand, dann das Band bis hoehe · bis,
     * darüber Wand; Dach.
     */
    public void aufbau(double x0, double x1, double hb, double z0, double hoehe, double von, double bis, int wand, int fenster, int dach) {
        double za = z0 + hoehe * von, zb = z0 + hoehe * bis, z1 = z0 + hoehe;
        quader(x0, x1, -hb, hb, z0, za, wand, wand, wand);
        quader(x0, x1, -hb, hb, za, zb, fenster, wand, wand);
        quader(x0, x1, -hb, hb, zb, z1, wand, dach, wand);
    }

    /** Ein Zylinder um eine Achse von a nach b (Halbmesser r, k Teile), mit Deckeln. */
    public void zylinder(double[] a, double[] b, double r, int k, int m) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2], l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-9) return;
        dx /= l; dy /= l; dz /= l;
        double[] e1 = Math.abs(dz) < 0.9 ? kreuz(new double[] {dx, dy, dz}, new double[] {0, 0, 1}) : kreuz(new double[] {dx, dy, dz}, new double[] {1, 0, 0});
        e1 = norm(e1);
        double[] e2 = kreuz(new double[] {dx, dy, dz}, e1);
        for (int i = 0; i < k; i++) {
            double t0 = 2 * Math.PI * i / k, t1 = 2 * Math.PI * (i + 1) / k;
            double[] n0 = {Math.cos(t0) * e1[0] + Math.sin(t0) * e2[0], Math.cos(t0) * e1[1] + Math.sin(t0) * e2[1], Math.cos(t0) * e1[2] + Math.sin(t0) * e2[2]};
            double[] n1 = {Math.cos(t1) * e1[0] + Math.sin(t1) * e2[0], Math.cos(t1) * e1[1] + Math.sin(t1) * e2[1], Math.cos(t1) * e1[2] + Math.sin(t1) * e2[2]};
            double[] p0 = {a[0] + r * n0[0], a[1] + r * n0[1], a[2] + r * n0[2], n0[0], n0[1], n0[2], r * t0, 0};
            double[] p1 = {a[0] + r * n1[0], a[1] + r * n1[1], a[2] + r * n1[2], n1[0], n1[1], n1[2], r * t1, 0};
            double[] q0 = {b[0] + r * n0[0], b[1] + r * n0[1], b[2] + r * n0[2], n0[0], n0[1], n0[2], r * t0, l};
            double[] q1 = {b[0] + r * n1[0], b[1] + r * n1[1], b[2] + r * n1[2], n1[0], n1[1], n1[2], r * t1, l};
            dreieck(p0, p1, q1, m);
            dreieck(p0, q1, q0, m);
            dreieck(new double[] {b[0], b[1], b[2], dx, dy, dz, 0, 0}, new double[] {q0[0], q0[1], q0[2], dx, dy, dz, 0, 0}, new double[] {q1[0], q1[1], q1[2], dx, dy, dz, 0, 0}, m);
            dreieck(new double[] {a[0], a[1], a[2], -dx, -dy, -dz, 0, 0}, new double[] {p1[0], p1[1], p1[2], -dx, -dy, -dz, 0, 0}, new double[] {p0[0], p0[1], p0[2], -dx, -dy, -dz, 0, 0}, m);
        }
    }

    /** Ein Drehkörper um die Hochachse aus Halbmessern rs über Höhen zs, k Teile; Material je Abschnitt (null: m0). */
    public void drehkoerper(double[] zs, double[] rs, int k, int[] mAbschnitt, int m0) {
        for (int j = 0; j + 1 < zs.length; j++) {
            double dz = zs[j + 1] - zs[j], dr = rs[j + 1] - rs[j], l = Math.hypot(dz, dr);
            if (l < 1e-9) continue;
            double nr = dz / l, nz = -dr / l;
            int m = mAbschnitt != null && j < mAbschnitt.length ? mAbschnitt[j] : m0;
            for (int i = 0; i < k; i++) {
                double t0 = 2 * Math.PI * i / k, t1 = 2 * Math.PI * (i + 1) / k, c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
                double[] a = {rs[j] * c0, rs[j] * s0, zs[j], nr * c0, nr * s0, nz, rs[j] * t0, zs[j]};
                double[] b = {rs[j] * c1, rs[j] * s1, zs[j], nr * c1, nr * s1, nz, rs[j] * t1, zs[j]};
                double[] c = {rs[j + 1] * c1, rs[j + 1] * s1, zs[j + 1], nr * c1, nr * s1, nz, rs[j + 1] * t1, zs[j + 1]};
                double[] d = {rs[j + 1] * c0, rs[j + 1] * s0, zs[j + 1], nr * c0, nr * s0, nz, rs[j + 1] * t0, zs[j + 1]};
                if (rs[j] > 1e-9) dreieck(a, b, c, m);
                if (rs[j + 1] > 1e-9) dreieck(a, c, d, m);
            }
        }
        // Deckel oben und unten
        int last = zs.length - 1;
        for (int e = 0; e < 2; e++) {
            int j = e == 0 ? 0 : last;
            double r = rs[j], z = zs[j], sz = e == 0 ? -1 : 1;
            if (r < 1e-9) continue;
            int m = mAbschnitt != null ? mAbschnitt[Math.min(j == 0 ? 0 : last - 1, mAbschnitt.length - 1)] : m0;
            for (int i = 0; i < k; i++) {
                double t0 = 2 * Math.PI * i / k, t1 = 2 * Math.PI * (i + 1) / k;
                dreieck(new double[] {0, 0, z, 0, 0, sz, 0, 0}, new double[] {r * Math.cos(t0), r * Math.sin(t0), z, 0, 0, sz, 0, 0},
                        new double[] {r * Math.cos(t1), r * Math.sin(t1), z, 0, 0, sz, 0, 0}, m);
            }
        }
    }

    /** Ein Ellipsoid mit den Halbachsen a, b, c über z = 0 (wie {@link Form.Ellipsoid}); Material oben und unten. */
    public void ellipsoid(double a, double b, double c, int k, int mOben, int mUnten, double zGrenze) { ellipsoid(0, 0, 0, a, b, c, k, mOben, mUnten, zGrenze); }

    /** Seit S7e2: wie oben, verschoben um (x0, y0, z0) – z0 ist die Unterkante, zGrenze im Ellipsoid gemessen (über z0). */
    public void ellipsoid(double x0, double y0, double z0, double a, double b, double c, int k, int mOben, int mUnten, double zGrenze) {
        int ringe = k / 2;
        for (int j = 0; j < ringe; j++)
            for (int i = 0; i < k; i++) {
                double[][] p = new double[4][];
                int[][] ij = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int q = 0; q < 4; q++) {
                    double th = Math.PI * ij[q][1] / ringe - Math.PI / 2, ph = 2 * Math.PI * ij[q][0] / k;
                    double x = a * Math.cos(th) * Math.cos(ph), y = b * Math.cos(th) * Math.sin(ph), z = c * Math.sin(th) + c;
                    p[q] = new double[] {x + x0, y + y0, z + z0, x / (a * a), y / (b * b), (z - c) / (c * c), 0, 0};
                }
                int m = (p[0][2] + p[2][2]) / 2 - z0 > zGrenze ? mOben : mUnten;
                dreieck(p[0], p[1], p[2], m);
                dreieck(p[0], p[2], p[3], m);
            }
    }

    /**
     * Der Rumpf aus den Spanten der Form: Seiten (Unterwasser bis z1, Wasserpass bis z2, darüber die Seite), Deck,
     * Spiegel, Kiel und die Aufbauten der Form (Wand, Fensterband, Dach).
     */
    public void rumpf(Form.Rumpf r, double z1, double z2, int unter, int pass, int seite, int deck, int kiel, int wand, int fenster, int dach) {
        int ns = 96;
        double L = r.laenge;
        double[] xs = new double[ns + 1];
        for (int i = 0; i <= ns; i++) xs[i] = -0.5 * L * Math.cos(Math.PI * i / ns) * 0.9995;     // dichter an den Enden
        // Höhen je Spant: Boden, Kimm dicht, die Grenzen der Farben, Deck
        int nz = 22;
        double[][] P = new double[ns + 1][];
        int[] zahl = new int[ns + 1];
        double[][][] pts = new double[ns + 1][][];
        for (int i = 0; i <= ns; i++) {
            double x = xs[i], z0 = r.boden(x), zd = r.deck(x);
            List<Double> zl = new ArrayList<>();
            for (int k = 0; k <= nz; k++) {
                double t = (double) k / nz;
                zl.add(z0 + (zd - z0) * (t < 0.5 ? 0.5 * Math.pow(2 * t, 1.6) : t));
            }
            for (double zz : new double[] {z1, z2}) if (zz > z0 && zz < zd) zl.add(zz);
            zl.sort(Double::compare);
            List<double[]> ps = new ArrayList<>();
            ps.add(new double[] {0, z0});
            for (double zz : zl) {
                double hb = r.halbeBreite(x, Math.min(zd - 1e-6, Math.max(z0 + 1e-6, zz)));
                ps.add(new double[] {hb, zz});
            }
            pts[i] = ps.toArray(new double[0][]);
            zahl[i] = pts[i].length;
        }
        // gleiche Zahl der Punkte je Spant: auf die größte auffüllen (doppelte Punkte am Ende)
        int m = 0;
        for (int i = 0; i <= ns; i++) m = Math.max(m, zahl[i]);
        for (int i = 0; i <= ns; i++) if (zahl[i] < m) {
            double[][] q = java.util.Arrays.copyOf(pts[i], m);
            for (int k = zahl[i]; k < m; k++) q[k] = pts[i][zahl[i] - 1].clone();
            pts[i] = q;
        }
        // Normalen je Punkt aus den Nachbarn (glatt über Spanten und Höhen), je Seite gespiegelt
        for (int seiteNr = 0; seiteNr < 2; seiteNr++) {
            double sg = seiteNr == 0 ? 1 : -1;
            for (int i = 0; i < ns; i++)
                for (int k = 0; k + 1 < m; k++) {
                    double[][] q = new double[4][];
                    int[][] ik = {{i, k}, {i + 1, k}, {i + 1, k + 1}, {i, k + 1}};
                    for (int c = 0; c < 4; c++) {
                        int a = ik[c][0], b = ik[c][1];
                        double[] p = pts[a][b];
                        double[] dxv = sub3(punkt(xs, pts, Math.min(ns, a + 1), b, sg), punkt(xs, pts, Math.max(0, a - 1), b, sg));
                        double[] dkv = sub3(punkt(xs, pts, a, Math.min(m - 1, b + 1), sg), punkt(xs, pts, a, Math.max(0, b - 1), sg));
                        double[] nn = kreuz(dxv, dkv);
                        if (sg * nn[1] < 0 && Math.abs(nn[1]) > 1e-9 || nn[1] == 0 && nn[2] > 0) nn = new double[] {-nn[0], -nn[1], -nn[2]};
                        q[c] = new double[] {xs[a], sg * p[0], p[1], nn[0], nn[1], nn[2], xs[a] + 0.5 * L, p[1]};
                    }
                    double zm = 0.25 * (q[0][2] + q[1][2] + q[2][2] + q[3][2]);
                    int mm = zm < z1 ? unter : zm < z2 ? pass : seite;
                    dreieck(q[0], q[1], q[2], mm);
                    dreieck(q[0], q[2], q[3], mm);
                }
        }
        // Deck: zwischen den Kanten der Seiten
        for (int i = 0; i < ns; i++) {
            double[] a = pts[i][m - 1], b = pts[i + 1][m - 1];
            flaeche(new double[] {xs[i], -a[0], a[1], xs[i] + 0.5 * L, -a[0]}, new double[] {xs[i + 1], -b[0], b[1], xs[i + 1] + 0.5 * L, -b[0]},
                    new double[] {xs[i + 1], b[0], b[1], xs[i + 1] + 0.5 * L, b[0]}, new double[] {xs[i], a[0], a[1], xs[i] + 0.5 * L, a[0]}, 0, 0, 1, deck);
        }
        // Spiegel (achtern) und Bug, wenn sie breit sind
        for (int e = 0; e < 2; e++) {
            int i = e == 0 ? 0 : ns;
            double sx = e == 0 ? -1 : 1;
            for (int k = 0; k + 1 < m; k++) {
                double[] a = pts[i][k], b = pts[i][k + 1];
                if (a[0] < 1e-3 && b[0] < 1e-3) continue;
                double zm = 0.5 * (a[1] + b[1]);
                int mm = zm < z1 ? unter : zm < z2 ? pass : seite;
                flaeche(new double[] {xs[i], -a[0], a[1], -a[0], a[1]}, new double[] {xs[i], a[0], a[1], a[0], a[1]}, new double[] {xs[i], b[0], b[1], b[0], b[1]},
                        new double[] {xs[i], -b[0], b[1], -b[0], b[1]}, sx, 0, 0, mm);
            }
        }
        // Kiel
        if (r.kielHoehe > 0) {
            double xa = -0.48 * L, xb = 0.48 * L;
            for (int i = 0; i < ns; i++) {
                double x0 = xs[i], x1 = xs[i + 1];
                if (x1 < xa || x0 > xb || r.riss(0.5 * (x0 + x1)) <= 0) continue;
                quader(x0, x1, -r.kielBreite / 2, r.kielBreite / 2, 0, Math.max(r.boden(x0), r.boden(x1)), kiel, kiel, kiel);
            }
        }
        // Aufbauten der Form
        double d0 = r.deck(0);
        for (double[] a : r.aufbauten) aufbau(a[0], a[1], a[2], d0 + a[3], a[4], 0.35, 0.8, wand, fenster, dach);
    }

    private static double[] punkt(double[] xs, double[][][] pts, int i, int k, double sg) { return new double[] {xs[i], sg * pts[i][k][0], pts[i][k][1]}; }

    /** Das Netz; der Baukasten ist danach leer. */
    public Netz netz(String name) {
        float[] m = new float[MAT * mat.size()];
        for (int i = 0; i < mat.size(); i++) System.arraycopy(mat.get(i), 0, m, MAT * i, MAT);
        Netz r = new Netz(name, java.util.Arrays.copyOf(s, n), m);
        mat.clear();
        n = 0;
        return r;
    }

    // ------------------------------------------------------------------ die Schwimmer von Hooge [G, nach den Fotos]

    /**
     * Das Netz zu einem Körper aus {@link Vorgaben} (nach seinem Namen); für Rümpfe ohne eigenen Anstrich weiß über
     * dunkelrot. Farben linear [G] nach Fotos Fähre1–3 und Kutter1–3.
     */
    public static Netz fuer(Koerper k) { return fuer(k, null); }

    /** Wie {@link #fuer(Koerper)}; farbe: Farbe einer Tonne (rot, gruen, gelb), sonst null. */
    public static Netz fuer(Koerper k, String farbe) {
        Schiffbau b = new Schiffbau();
        Form f = k.form;
        String n = f.name();
        if (f instanceof Form.Rumpf r) {
            double T = r.tiefgang;
            int glas = b.material(0.02, 0.025, 0.03, GLAS, 1);
            switch (n) {
                case "Fähre Hilligenlei" -> {
                    int unter = b.material(0.16, 0.035, 0.03, MATT, 0), pass = b.material(0.16, 0.035, 0.03, LACK, 0.3), seite = b.material(0.018, 0.018, 0.02, LACK, 0.5);
                    int deck = b.material(0.06, 0.07, 0.065, MATT, 0), weiss = b.material(0.72, 0.72, 0.70, LACK, 0.4);
                    int fenster = b.material(0.72, 0.72, 0.70, FENSTER, 0.4, 1.0, 1.6), gelb = b.material(0.62, 0.36, 0.04, LACK, 0.4), rot = b.material(0.45, 0.04, 0.03, LACK, 0.4);
                    int stahl = b.material(0.25, 0.25, 0.25, METALL, 0.3), ocker = b.material(0.55, 0.32, 0.06, LACK, 0.4), schwarz = b.material(0.02, 0.02, 0.02, MATT, 0);
                    int bull = b.material(0.55, 0.56, 0.55, LACK, 0.5), bruecke = b.material(0.72, 0.72, 0.70, FENSTER, 0.4, 0.9, 1.15);
                    b.rumpf(r, T - 0.05, T + 0.05, unter, pass, seite, deck, unter, weiss, fenster, weiss);
                    double d = r.deck(0), L2 = r.laenge / 2, hb = r.breite / 2;
                    // seit S7e1 (Seegang Werkbuch 99, SF151 A) nach Foto Fähre2 [G, Maße aus dem Bild bei 38,3 m Länge]:
                    // Schanzkleid weiß; Portal über dem Autodeck mit drei Öffnungen je Seite, darauf das Fahrgastdeck mit Fensterband,
                    // die Brücke vorn mit Nocken über die ganze Breite, der gelbe Mast über der Brücke, der ockerfarbene Schornstein
                    // achtern mit weißem und rotem Band und schwarzer Kappe; schwarzer Rumpf mit sieben Bullaugen je Seite
                    for (double sg : new double[] {-1, 1}) b.quader(-L2 + 0.6, L2 - 1.6, sg > 0 ? hb - 0.15 : -hb, sg > 0 ? hb : -hb + 0.15, d, d + 1.2, weiss);
                    // seit L5e1 (Befund 2: vier schmale Beine je Seite sahen aus wie ein Tisch): die Stützen wie im Foto – vorn
                    // schräg, eine schräge und eine breite Stütze in der Mitte, achtern eine schräge Strebe vor einer vollen
                    // Endwand; darüber das Schanzkleid des Oberdecks [G, Maße aus dem Bild bei 28,7 Bildpunkten je m]
                    for (double sg : new double[] {-1, 1}) {
                        double ya = sg > 0 ? hb - 0.9 : -hb + 0.15, yb = sg > 0 ? hb - 0.15 : -hb + 0.9;
                        b.strebe(8.35, 8.85, 7.7, 8.2, ya, yb, d, d + 4.5, weiss);                       // vorn
                        b.strebe(2.6, 3.65, 1.75, 2.6, ya, yb, d, d + 4.5, weiss);                        // vordere Mitte
                        b.quader(-4.4, -3.05, ya, yb, d, d + 4.5, weiss);                                  // Mitte
                        b.strebe(-13.1, -12.2, -10.8, -9.95, ya, yb, d, d + 4.5, weiss);                 // achtern schräg
                        b.quader(-13.75, -13.1, ya, yb, d, d + 4.5, weiss);                                // Endwand
                        b.quader(-13.75, 8.0, sg > 0 ? hb - 0.15 : -hb, sg > 0 ? hb : -hb + 0.15, d + 5.2, d + 6.2, weiss);
                    }
                    b.quader(-13.75, 8.5, -hb + 0.15, hb - 0.15, d + 4.5, d + 5.2, weiss);                // Decke des Autodecks
                    b.aufbau(-8, 6.5, hb - 1.0, d + 5.2, 1.9, 0.3, 0.85, weiss, fenster, weiss);          // Fahrgastraum
                    b.aufbau(3.0, 7.0, hb - 0.3, d + 7.1, 1.4, 0.3, 0.9, weiss, bruecke, weiss);         // Brücke mit Nocken
                    b.zylinder(new double[] {1.0, 0, d + 7.1}, new double[] {1.0, 0, d + 12.6}, 0.12, 8, gelb);
                    b.zylinder(new double[] {1.0, -1.2, d + 11.2}, new double[] {1.0, 1.2, d + 11.2}, 0.06, 6, gelb);
                    b.quader(-13.2, -10.5, -1.2, 1.2, d + 5.2, d + 9.2, ocker);                        // Schornstein
                    b.quader(-13.2, -10.5, -1.2, 1.2, d + 9.2, d + 9.5, weiss);
                    b.quader(-13.2, -10.5, -1.2, 1.2, d + 9.5, d + 9.9, rot);
                    b.quader(-13.25, -10.45, -1.25, 1.25, d + 9.9, d + 10.2, schwarz);
                    double zb = 0.5 * (T + d);
                    for (int i = 0; i < 7; i++) {
                        double x = -11 + 2.5 * i, y = r.halbeBreite(x, zb);
                        for (double sg : new double[] {-1, 1}) b.quader(x - 0.2, x + 0.2, sg > 0 ? y - 0.02 : -y - 0.03, sg > 0 ? y + 0.03 : -y + 0.02, zb - 0.2, zb + 0.2, bull);
                    }
                    // Rampe am Bug
                    b.quader(L2 - 1.2, L2 + 0.3, -3.2, 3.2, d - 0.1, d + 0.15, stahl);
                }
                case "Adler-Express" -> {
                    int unter = b.material(0.14, 0.03, 0.03, MATT, 0), pass = b.material(0.62, 0.62, 0.60, LACK, 0.3), seite = b.material(0.015, 0.03, 0.025, LACK, 0.5);
                    int deck = b.material(0.12, 0.10, 0.08, MATT, 0), weiss = b.material(0.72, 0.72, 0.70, LACK, 0.4);
                    int fenster = b.material(0.72, 0.72, 0.70, FENSTER, 0.4, 1.4, 1.7), gold = b.material(0.42, 0.28, 0.10, LACK, 0.4);
                    b.rumpf(r, T - 0.05, T + 0.08, unter, pass, seite, deck, unter, weiss, fenster, weiss);
                    double d = r.deck(0), hb = r.breite / 2 - 0.2;
                    // zwei Decks mit Fenstern, Streifen in Gold am Bug, Brücke oben (Foto Fähre1)
                    b.aufbau(-17, 13, hb, d, 2.4, 0.3, 0.85, weiss, fenster, weiss);
                    b.aufbau(-12, 9, hb - 0.4, d + 2.4, 2.2, 0.3, 0.85, weiss, fenster, weiss);
                    b.aufbau(4, 9, hb - 0.8, d + 4.6, 1.6, 0.35, 0.9, weiss, fenster, weiss);
                    for (double sg : new double[] {-1, 1}) b.quader(-17, 13, sg > 0 ? hb + 0.2 : -hb - 0.22, sg > 0 ? hb + 0.22 : -hb - 0.2, d + 0.9, d + 1.05, gold);
                    b.zylinder(new double[] {6, 0, d + 6.2}, new double[] {6, 0, d + 9.5}, 0.08, 6, weiss);
                }
                case "Hauke Haien" -> {
                    int unter = b.material(0.22, 0.03, 0.03, MATT, 0), pass = b.material(0.22, 0.03, 0.03, LACK, 0.3), seite = b.material(0.012, 0.025, 0.12, LACK, 0.5);
                    int deck = b.material(0.10, 0.09, 0.07, MATT, 0), weiss = b.material(0.72, 0.72, 0.70, LACK, 0.4);
                    int fenster = b.material(0.72, 0.72, 0.70, FENSTER, 0.4, 0.7, 1.3), blau = b.material(0.02, 0.04, 0.18, LACK, 0.4);
                    int gelb = b.material(0.62, 0.40, 0.04, LACK, 0.4);
                    b.rumpf(r, T - 0.1, T + 0.05, unter, pass, seite, deck, unter, weiss, fenster, weiss);
                    double d = r.deck(0), hb = 2.4;
                    // durchgehender Aufbau (Form), darauf das Ruderhaus mit blauem Band, Mast gelb (Foto Fähre3)
                    b.quader(-12, 8, -hb - 0.01, hb + 0.01, d + 1.9, d + 2.1, blau);
                    b.aufbau(-2, 3.5, 1.8, d + 2.3, 1.9, 0.35, 0.85, weiss, fenster, weiss);
                    b.zylinder(new double[] {1, 0, d + 4.2}, new double[] {1, 0, d + 8}, 0.1, 8, gelb);
                    b.zylinder(new double[] {1, -1.3, d + 7.0}, new double[] {1, 1.3, d + 7.0}, 0.05, 6, gelb);
                }
                case "Krabbenkutter" -> {
                    int unter = b.material(0.18, 0.03, 0.03, MATT, 0), pass = b.material(0.65, 0.65, 0.63, LACK, 0.3), seite = b.material(0.015, 0.04, 0.20, LACK, 0.5);
                    int deck = b.material(0.10, 0.085, 0.06, HOLZ, 0), weiss = b.material(0.70, 0.70, 0.68, LACK, 0.4);
                    int fenster = b.material(0.70, 0.70, 0.68, FENSTER, 0.4, 0.6, 1.1), gelb = b.material(0.62, 0.34, 0.03, LACK, 0.4);
                    int netz = b.material(0.06, 0.07, 0.06, MATT, 0);
                    b.rumpf(r, T - 0.05, T + 0.12, unter, pass, seite, deck, unter, weiss, fenster, weiss);
                    double d = r.deck(0);
                    // Mast und Kurrbäume hochgeklappt (vor Anker), Netze gebündelt am Mast (Foto Kutter2/3)
                    b.zylinder(new double[] {3.5, 0, d}, new double[] {3.5, 0, d + 9}, 0.13, 8, gelb);
                    for (double sg : new double[] {-1, 1}) {
                        b.zylinder(new double[] {3.5, sg * 0.4, d + 0.8}, new double[] {3.2, sg * 1.6, d + 8.6}, 0.09, 6, gelb);
                        b.zylinder(new double[] {3.0, sg * 1.2, d + 1.0}, new double[] {3.0, sg * 1.4, d + 3.2}, 0.35, 6, netz);
                    }
                    b.zylinder(new double[] {-5.5, 0, d + 2.2}, new double[] {-5.5, 0, d + 6.5}, 0.07, 6, weiss);
                }
                default -> {
                    int unter = b.material(0.16, 0.04, 0.03, MATT, 0), weiss = b.material(0.7, 0.7, 0.68, LACK, 0.4);
                    int fenster = b.material(0.7, 0.7, 0.68, FENSTER, 0.4, 0.8, 1.4);
                    b.rumpf(r, T - 0.05, T + 0.05, unter, unter, weiss, b.material(0.08, 0.08, 0.07, MATT, 0), unter, weiss, fenster, weiss);
                }
            }
            return b.netz(n);
        }
        if (f instanceof Form.Drehkoerper d) {
            int teile = 24;
            int haupt = switch (farbe == null ? "" : farbe) {
                case "rot" -> b.material(0.40, 0.025, 0.02, LACK, 0.3);
                case "gruen" -> b.material(0.015, 0.16, 0.05, LACK, 0.3);
                default -> b.material(0.62, 0.45, 0.02, LACK, 0.3);
            };
            int dunkel = b.material(0.04, 0.04, 0.04, METALL, 0.2), algen = b.material(0.04, 0.06, 0.02, MATT, 0);
            // Profil an der Grenze des Bewuchses (15 cm unter der Wasserlinie) teilen
            double wl = wasserlinie(k), zg = wl - 0.15;
            List<double[]> pr = new ArrayList<>();
            for (int j = 0; j < d.zs.length; j++) {
                if (j > 0 && d.zs[j - 1] < zg && d.zs[j] > zg)
                    pr.add(new double[] {zg, d.rs[j - 1] + (d.rs[j] - d.rs[j - 1]) * (zg - d.zs[j - 1]) / (d.zs[j] - d.zs[j - 1])});
                pr.add(new double[] {d.zs[j], d.rs[j]});
            }
            double[] zs = new double[pr.size()], rs = new double[pr.size()];
            for (int j = 0; j < zs.length; j++) { zs[j] = pr.get(j)[0]; rs[j] = pr.get(j)[1]; }
            int[] ma = new int[zs.length - 1];
            for (int j = 0; j < ma.length; j++) ma[j] = 0.5 * (zs[j] + zs[j + 1]) < zg ? algen : haupt;
            b.drehkoerper(zs, rs, teile, ma, haupt);
            if (n.startsWith("Leuchttonne")) {
                double top = d.zs[d.zs.length - 1];
                for (int i = 0; i < 4; i++) {
                    double a = Math.PI / 2 * i + Math.PI / 4;
                    b.zylinder(new double[] {0.5 * Math.cos(a), 0.5 * Math.sin(a), top}, new double[] {0.15 * Math.cos(a), 0.15 * Math.sin(a), top + 1.8}, 0.04, 5, haupt);
                }
                b.zylinder(new double[] {0, 0, top + 1.8}, new double[] {0, 0, top + 2.1}, 0.12, 8, dunkel);
            }
            return b.netz(n);
        }
        if (n.equals("Pricke") && f instanceof Form.Quader q) {
            // seit S7e1 (SF149 A): Birkenstamm mit Besen; Backbord aufwärts, Steuerbord abwärts gebunden [G]
            int stamm = b.material(0.30, 0.28, 0.25, HOLZ, 0), reisig = b.material(0.07, 0.05, 0.035, MATT, 0);
            b.zylinder(new double[] {0, 0, 0}, new double[] {0, 0, q.h}, 0.05, 6, stamm);
            boolean auf = !"steuerbord".equals(farbe);
            int zweige = 14;
            for (int i = 0; i < zweige; i++) {
                double a = 2 * Math.PI * i / zweige + 0.3 * Math.sin(3.7 * i), rr = 0.22 + 0.1 * ((i * 7) % 5) / 4.0;
                double z0 = auf ? q.h - 0.7 : q.h, z1 = auf ? q.h + 0.55 + 0.1 * ((i * 3) % 4) / 3.0 : q.h - 0.85;
                b.zylinder(new double[] {0.03 * Math.cos(a), 0.03 * Math.sin(a), z0}, new double[] {rr * Math.cos(a), rr * Math.sin(a), z1}, 0.015, 3, reisig);
            }
            return b.netz(n);
        }
        if (f instanceof Form.Quader q) {
            int holz = b.material(n.contains("Kiste") ? 0.12 : 0.10, n.contains("Kiste") ? 0.18 : 0.085, n.contains("Kiste") ? 0.30 : 0.065, n.contains("Kiste") ? LACK : HOLZ, 0.1);
            b.quader(-q.l / 2, q.l / 2, -q.b / 2, q.b / 2, 0, q.h, holz);
            return b.netz(n);
        }
        if (f instanceof Form.Zylinder z) {
            int holz = b.material(0.09, 0.075, 0.055, HOLZ, 0);
            b.zylinder(new double[] {-z.l / 2, 0, z.r}, new double[] {z.l / 2, 0, z.r}, z.r, 12, holz);
            return b.netz(n);
        }
        if (f instanceof Form.Ellipsoid e) {
            // Eiderente (Erpel): oben weiß, unten schwarz [G]; seit S7e2 farbe „ente“: das Weibchen braun, unten dunkler [G]
            boolean weib = "ente".equals(farbe);
            int oben = weib ? b.material(0.16, 0.09, 0.045, MATT, 0) : b.material(0.55, 0.55, 0.52, MATT, 0);
            int unten = weib ? b.material(0.08, 0.05, 0.03, MATT, 0) : b.material(0.03, 0.03, 0.03, MATT, 0);
            b.ellipsoid(e.a, e.b, e.c, 12, oben, unten, e.c * 1.1);                       // seit S7e2 12 statt 16 Teile (Kosten, L5e2)
            // seit S7e2 (Befund L5e2: ohne Kopf sahen sie aus wie Eier): Hals, Kopf und Schnabel vorn über dem Rumpf, nur im Netz [G]
            if (n.startsWith("Eiderente")) {
                int kopf = weib ? oben : b.material(0.60, 0.60, 0.56, MATT, 0), kappe = weib ? oben : b.material(0.03, 0.03, 0.03, MATT, 0);
                int schnabel = weib ? b.material(0.10, 0.09, 0.06, MATT, 0) : b.material(0.30, 0.32, 0.16, MATT, 0);
                b.ellipsoid(0.82 * e.a, 0, 0.55 * e.c * 2, 0.17 * e.a, 0.30 * e.b, 0.35 * e.c * 2, 8, kopf, kopf, 0);
                b.ellipsoid(0.90 * e.a, 0, 1.00 * e.c * 2, 0.24 * e.a, 0.33 * e.b, 0.22 * e.c * 2, 8, kappe, kopf, 0.30 * e.c * 2);
                b.ellipsoid(1.16 * e.a, 0, 1.10 * e.c * 2, 0.14 * e.a, 0.12 * e.b, 0.06 * e.c * 2, 6, schnabel, schnabel, 0);
            }
            return b.netz(n);
        }
        int grau = b.material(0.2, 0.2, 0.2, MATT, 0);
        double[] k0 = f.kasten();
        b.quader(k0[0], k0[1], k0[2], k0[3], k0[4], k0[5], grau);
        return b.netz(n);
    }

    /** Höhe der ruhigen Wasserlinie über der Unterkante, wenn der Körper aufrecht schwimmt (m; Halbierung über die Proben). */
    static double wasserlinie(Koerper k) {
        double soll = k.masse / 1025, lo = 0, hi = k.form.kasten()[5];
        for (int i = 0; i < 50; i++) {
            double m = 0.5 * (lo + hi);
            if (k.proben.volumenUnter(m) < soll) lo = m; else hi = m;
        }
        return 0.5 * (lo + hi);
    }

    static double[] kreuz(double[] a, double[] b) { return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }

    static double[] sub3(double[] a, double[] b) { return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }

    static double[] norm(double[] a) {
        double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return l < 1e-12 ? new double[] {1, 0, 0} : new double[] {a[0] / l, a[1] / l, a[2] / l};
    }
}
