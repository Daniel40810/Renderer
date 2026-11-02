package com.dan.vessel;

import java.util.Arrays;

/**
 * Netze aus Code für einen Kutter ({@link Hull}): Rumpf aus den Spanten der Form, Deck, Spiegel, Steuerhaus mit
 * Fensterband, Mast mit Topplicht, zwei Trollstangen (aufgeholt, wie vor Anker [G]). Dreiecke im Achsenkreuz des Rumpfs
 * (s vorn, y oben, q Steuerbord), je Ecke Lage 3, Normale 3, Farbe 3 (linear) – 9 float. Farben [G]: Unterwasser
 * rotbraun (Kupferfarbe), schwarzer Wasserpass, weißes Freibord mit grünem Schergang, Deck Holz, Steuerhaus weiß mit
 * grauem Dach.
 */
public final class Mesh {
    private Mesh() { }

    public static final int STRIDE = 9;

    private static final class B {
        float[] v = new float[9 * 3 * 4096];
        int n;

        void tri(double[] a, double[] b, double[] c, double[] col) {
            double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
            double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx, l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-12) return;
            nx /= l; ny /= l; nz /= l;
            for (double[] p : new double[][]{a, b, c}) {
                if (n + 9 > v.length) v = Arrays.copyOf(v, v.length * 2);
                v[n++] = (float) p[0]; v[n++] = (float) p[1]; v[n++] = (float) p[2];
                v[n++] = (float) nx; v[n++] = (float) ny; v[n++] = (float) nz;
                v[n++] = (float) col[0]; v[n++] = (float) col[1]; v[n++] = (float) col[2];
            }
        }

        void quad(double[] a, double[] b, double[] c, double[] d, double[] col) { tri(a, b, c, col); tri(a, c, d, col); }

        /** Quader von (s0, y0, q0) bis (s1, y1, q1), Außenseiten. */
        void box(double s0, double y0, double q0, double s1, double y1, double q1, double[] col, double[] top) {
            double[] p000 = {s0, y0, q0}, p100 = {s1, y0, q0}, p010 = {s0, y1, q0}, p110 = {s1, y1, q0},
                    p001 = {s0, y0, q1}, p101 = {s1, y0, q1}, p011 = {s0, y1, q1}, p111 = {s1, y1, q1};
            quad(p000, p010, p110, p100, col);   // Backbord (q0)
            quad(p001, p101, p111, p011, col);   // Steuerbord
            quad(p100, p110, p111, p101, col);   // vorn
            quad(p000, p001, p011, p010, col);   // achtern
            quad(p010, p011, p111, p110, top);   // oben
            quad(p000, p100, p101, p001, col);   // unten
        }

        /** Stange von a nach b mit quadratischem Querschnitt der Kante w. */
        void beam(double[] a, double[] b, double w, double[] col) {
            double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2], l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= l; dy /= l; dz /= l;
            double[] u = Math.abs(dy) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
            double px = dy * u[2] - dz * u[1], py = dz * u[0] - dx * u[2], pz = dx * u[1] - dy * u[0], pl = Math.sqrt(px * px + py * py + pz * pz);
            px /= pl; py /= pl; pz /= pl;
            double qx = dy * pz - dz * py, qy = dz * px - dx * pz, qz = dx * py - dy * px;
            double h = w / 2;
            double[][] c = new double[8][];
            for (int k = 0; k < 4; k++) {
                double sa = (k == 0 || k == 3) ? -h : h, sb = (k < 2) ? -h : h;
                c[k] = new double[]{a[0] + px * sa + qx * sb, a[1] + py * sa + qy * sb, a[2] + pz * sa + qz * sb};
                c[k + 4] = new double[]{b[0] + px * sa + qx * sb, b[1] + py * sa + qy * sb, b[2] + pz * sa + qz * sb};
            }
            for (int k = 0; k < 4; k++) { int j = (k + 1) % 4; quad(c[k], c[j], c[j + 4], c[k + 4], col); }
            quad(c[0], c[3], c[2], c[1], col);
            quad(c[4], c[5], c[6], c[7], col);
        }

        float[] done() { return Arrays.copyOf(v, n); }
    }

    static final double[] ROT = {0.30, 0.07, 0.04}, SCHWARZ = {0.02, 0.02, 0.02}, WEISS = {0.78, 0.78, 0.74}, GRUEN = {0.05, 0.22, 0.12},
            HOLZ = {0.36, 0.26, 0.15}, GRAU = {0.30, 0.31, 0.32}, FENSTER = {0.03, 0.04, 0.05}, MAST = {0.42, 0.34, 0.22}, LICHT = {3, 3, 2.6};

    static double[] farbe(Hull h, double y, double deck) {
        if (y < h.draft + 0.08) return ROT;
        if (y < h.draft + 0.22) return SCHWARZ;
        if (y > deck - 0.16) return GRUEN;
        return WEISS;
    }

    /** Das Netz eines Kutters (9 float je Ecke, Dreiecke). */
    public static float[] kutter(Hull h) {
        B b = new B();
        int ns = 56, ny = 14;
        double[][][] pt = new double[ns + 1][ny + 1][];
        for (int i = 0; i <= ns; i++) {
            double s = -h.loa / 2 + h.loa * i / ns;
            if (i == ns) s -= 1e-3;
            double k = h.keel(s), d = h.deck(s) - 1e-3;
            for (int j = 0; j <= ny; j++) {
                double t = j / (double) ny, y = k + (d - k) * (t * t * 0.35 + 0.65 * t);
                pt[i][j] = new double[]{s, y, h.halfBreadth(s, Math.max(k + 1e-4, y))};
            }
        }
        for (int i = 0; i < ns; i++)
            for (int j = 0; j < ny; j++) {
                double[] a = pt[i][j], c = pt[i + 1][j], e = pt[i + 1][j + 1], f = pt[i][j + 1];
                double[] col = farbe(h, 0.25 * (a[1] + c[1] + e[1] + f[1]), h.deck(0.5 * (a[0] + c[0])));
                // Steuerbord (+q), Backbord (−q)
                b.quad(new double[]{a[0], a[1], a[2]}, new double[]{f[0], f[1], f[2]}, new double[]{e[0], e[1], e[2]}, new double[]{c[0], c[1], c[2]}, col);
                b.quad(new double[]{a[0], a[1], -a[2]}, new double[]{c[0], c[1], -c[2]}, new double[]{e[0], e[1], -e[2]}, new double[]{f[0], f[1], -f[2]}, col);
            }
        // Boden (Kiel) und Deck
        for (int i = 0; i < ns; i++) {
            double[] a = pt[i][0], c = pt[i + 1][0];
            b.quad(new double[]{a[0], a[1], -a[2]}, new double[]{a[0], a[1], a[2]}, new double[]{c[0], c[1], c[2]}, new double[]{c[0], c[1], -c[2]}, ROT);
            double[] d0 = pt[i][ny], d1 = pt[i + 1][ny];
            b.quad(new double[]{d0[0], d0[1], -d0[2]}, new double[]{d1[0], d1[1], -d1[2]}, new double[]{d1[0], d1[1], d1[2]}, new double[]{d0[0], d0[1], d0[2]}, HOLZ);
        }
        // Spiegel
        for (int j = 0; j < ny; j++) {
            double[] a = pt[0][j], f = pt[0][j + 1];
            b.quad(new double[]{a[0], a[1], -a[2]}, new double[]{f[0], f[1], -f[2]}, new double[]{f[0], f[1], f[2]}, new double[]{a[0], a[1], a[2]},
                    farbe(h, 0.5 * (a[1] + f[1]), h.deck(a[0])));
        }
        // Schanzkleid (niedrig, 0,25 m)
        for (int i = 0; i < ns; i++) {
            double[] d0 = pt[i][ny], d1 = pt[i + 1][ny];
            for (int sg = -1; sg <= 1; sg += 2) {
                double[] a = {d0[0], d0[1], sg * d0[2]}, c = {d1[0], d1[1], sg * d1[2]}, e = {d1[0], d1[1] + 0.25, sg * d1[2]}, f = {d0[0], d0[1] + 0.25, sg * d0[2]};
                if (sg > 0) b.quad(a, f, e, c, GRUEN); else b.quad(a, c, e, f, GRUEN);
                if (sg > 0) b.quad(a, c, e, f, WEISS); else b.quad(a, f, e, c, WEISS);
            }
        }
        // Steuerhaus mit Fensterband
        double dh = h.deck(0.5 * (h.houseS0 + h.houseS1)), hw = h.houseWidth / 2, hh = h.houseHeight;
        b.box(h.houseS0, dh, -hw, h.houseS1, dh + hh * 0.62, hw, WEISS, WEISS);
        b.box(h.houseS0 + 0.02, dh + hh * 0.62, -hw + 0.02, h.houseS1 - 0.02, dh + hh * 0.9, hw - 0.02, FENSTER, FENSTER);
        b.box(h.houseS0 - 0.15, dh + hh * 0.9, -hw - 0.12, h.houseS1 + 0.12, dh + hh, hw + 0.12, GRAU, GRAU);
        // Mast, Topplicht, Trollstangen (aufgeholt, leicht nach außen)
        double dm = h.deck(h.mastS);
        b.beam(new double[]{h.mastS, dm, 0}, new double[]{h.mastS, dm + h.mastHeight, 0}, 0.16, MAST);
        b.box(h.mastS - 0.08, dm + h.mastHeight - 0.4, -0.08, h.mastS + 0.08, dm + h.mastHeight - 0.22, 0.08, LICHT, LICHT);
        double base = dm + 1.2;
        for (int sg = -1; sg <= 1; sg += 2) {
            double ang = Math.toRadians(14);
            b.beam(new double[]{h.mastS - 0.3, base, sg * 0.25}, new double[]{h.mastS - 0.3 - 0.8, base + h.poleLength * Math.cos(ang), sg * (0.25 + h.poleLength * Math.sin(ang))}, 0.09, MAST);
        }
        // Ankerwinde am Bug
        double sb = 0.36 * h.loa;
        b.box(sb - 0.3, h.deck(sb), -0.35, sb + 0.3, h.deck(sb) + 0.45, 0.35, GRAU, GRAU);
        return b.done();
    }

    /** Anzahl der Ecken eines Netzes. */
    public static int vertices(float[] m) { return m.length / STRIDE; }
}
