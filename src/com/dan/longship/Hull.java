package com.dan.longship;

/**
 * Der Rumpf aus dem Linienriss eines {@link ShipType}: Halbbreite, Kiellinie und Sprung über die Länge, die Form jedes
 * Spants von Kiel bis Bordkante, daraus Klinkerplanken (jeder Gang greift außen über den darunter), Kiel und Steven,
 * Spanten, Querbalken, Bodenbretter, Mast, Wanten, Steuerruder, Schilde, Ladung. Schiffskoordinaten: s vorn, q Steuerbord,
 * y oben (Konstruktionswasserlinie y = 0); im Netz x = q, y, z = −s.
 * <p>
 * Der Linienriss in Formeln [G nach den Funden]: u = s / (L/2) von −1 (achtern) bis 1 (vorn); Halbbreite
 * B/2 · (1 − |u|^2,4)^0,62; Kiel −T · (1 − |u|^5); Bordkante F + S · |u|^2,6; im Spant von t = 0 (Kiel) bis 1 (Bordkante)
 * q = Halbbreite · sin(t·π/2)^0,55 und y = Kiel + (Bordkante − Kiel) · t^1,35.
 */
public final class Hull {
    public final ShipType type;
    public final double halfLength;
    /** Feste Teile (Rumpf, Kiel, Spanten, Mast, Wanten, Ladung) und Schilde (nur gezeigt, wenn nicht gerudert wird). */
    public final Mesh mesh = new Mesh(), shields = new Mesh();
    /** Säulen für ein Streifenmodell: {s, q, Fläche, Kiel, Oberkante} je Zeile ({@code buoyancy.Floater}). */
    public final double[][] columns;

    public Hull(ShipType type) {
        this.type = type;
        this.halfLength = type.length / 2;
        build();
        columns = buildColumns();
    }

    // ------------------------------------------------------------------ Linienriss

    double u(double s) { return Math.max(-1, Math.min(1, s / halfLength)); }

    /** Größte Halbbreite an der Stelle s (an der Bordkante, m). */
    public double halfBeam(double s) { double a = Math.abs(u(s)); return type.beam / 2 * Math.pow(Math.max(0, 1 - Math.pow(a, 2.4)), 0.62); }

    /** Unterkante des Kiels an der Stelle s. */
    public double keel(double s) { double a = Math.abs(u(s)); return -type.draft * (1 - Math.pow(a, 5)); }

    /** Bordkante an der Stelle s. */
    public double sheerY(double s) { double a = Math.abs(u(s)); return type.freeboard + type.sheer * Math.pow(a, 2.6); }

    /** Punkt der Außenhaut an Stelle s, Spant-Parameter t (0 Kiel … 1 Bordkante), Seite side (+1 Steuerbord): {s, q, y}. */
    public double[] point(double s, double t, int side) {
        double hb = halfBeam(s), k = keel(s), sh = sheerY(s);
        double q = hb * Math.pow(Math.sin(t * Math.PI / 2), 0.55);
        return new double[]{s, side * q, k + (sh - k) * Math.pow(t, 1.35)};
    }

    /** Halbbreite in der Höhe y an der Stelle s (innen an der Haut), für Wasserlinie, Riemenpforten, Decks. */
    public double halfBreadthAt(double s, double y) {
        double k = keel(s), sh = sheerY(s);
        if (y <= k) return 0;
        if (y >= sh) return halfBeam(s);
        double t = Math.pow((y - k) / (sh - k), 1 / 1.35);
        return halfBeam(s) * Math.pow(Math.sin(t * Math.PI / 2), 0.55);
    }

    /** Netzkoordinaten aus s, q, y. */
    static double[] m(double s, double q, double y) { return new double[]{q, y, -s}; }

    static double[] m(double[] p) { return new double[]{p[1], p[2], -p[0]}; }

    // ------------------------------------------------------------------ Netz

    private void build() {
        int ns = Math.max(16, (int) Math.round(type.length / 0.45));
        int nt = type.strakes;
        double land = 0.022 + type.beam * 0.002, thick = 0.035;
        // Planken: je Gang ein Streifen außen (Unterkante nach außen versetzt: Klinker), innen glatt
        for (int side = -1; side <= 1; side += 2)
            for (int k = 0; k < nt; k++) {
                double t0 = k / (double) nt, t1 = (k + 1) / (double) nt;
                for (int i = 0; i < ns; i++) {
                    double s0 = -halfLength + type.length * i / ns, s1 = -halfLength + type.length * (i + 1) / ns;
                    double[] a = point(s0, t0, side), b = point(s1, t0, side), c = point(s1, t1, side), d = point(s0, t1, side);
                    double[] na = outward(s0, t0, side), nb = outward(s1, t0, side);
                    double[] a2 = add(a, na, land), b2 = add(b, nb, land);
                    double w = Math.hypot(d[1] - a[1], d[2] - a[2]);
                    double[] uv = {s0, 0, s1, 0, s1, w, s0, w};
                    // außen: Normale nach außen (Steuerbord gegen den Uhrzeigersinn von außen gesehen)
                    if (side > 0) mesh.quad(m(a2), m(b2), m(c), m(d), Material.HULL, uv);
                    else mesh.quad(m(b2), m(a2), m(d), m(c), Material.HULL, new double[]{s1, 0, s0, 0, s0, w, s1, w});
                    // Lippe der überlappenden Planke (Kante nach unten)
                    if (k > 0) {
                        if (side > 0) mesh.quad(m(a), m(b), m(b2), m(a2), Material.HULL, new double[]{s0, 0, s1, 0, s1, 0.02, s0, 0.02});
                        else mesh.quad(m(b), m(a), m(a2), m(b2), Material.HULL, new double[]{s1, 0, s0, 0, s0, 0.02, s1, 0.02});
                    }
                    // innen
                    double[] ia = add(a, na, -thick), ib = add(b, nb, -thick), ic = add(c, outward(s1, t1, side), -thick), id = add(d, outward(s0, t1, side), -thick);
                    if (side > 0) mesh.quad(m(ib), m(ia), m(id), m(ic), Material.INNER, new double[]{s1, 0, s0, 0, s0, w, s1, w});
                    else mesh.quad(m(ia), m(ib), m(ic), m(id), Material.INNER, uv);
                }
            }
        // Schandeck (Bordkante) und Kiel
        double[][] railP = new double[ns + 1][], railS = new double[ns + 1][], keelL = new double[ns + 1][];
        for (int i = 0; i <= ns; i++) {
            double s = -halfLength + type.length * i / ns;
            railP[i] = m(point(s, 1, -1));
            railS[i] = m(point(s, 1, 1));
            keelL[i] = m(s, 0, keel(s) - 0.04);
        }
        double rr = 0.045 + type.beam * 0.006;
        for (int i = 0; i < ns; i++) {
            mesh.beam(railP[i], railP[i + 1], rr, 4, Material.WOOD);
            mesh.beam(railS[i], railS[i + 1], rr, 4, Material.WOOD);
            mesh.beam(keelL[i], keelL[i + 1], 0.07 + type.beam * 0.01, 4, Material.WOOD);
        }
        steven(1);
        steven(-1);
        // Spanten und Querbalken
        double frameStep = type.length > 12 ? 1.0 : 0.6;
        for (double s = -halfLength + frameStep; s < halfLength - frameStep * 0.6; s += frameStep) {
            if (Math.abs(u(s)) > 0.9) continue;
            for (int side = -1; side <= 1; side += 2) {
                double[] prev = null;
                for (int j = 0; j <= 6; j++) {
                    double t = 0.03 + 0.92 * j / 6.0;
                    double[] p = add(point(s, t, side), outward(s, t, side), -thick - 0.05);
                    if (prev != null) mesh.beam(m(prev), m(p), 0.045 + type.beam * 0.004, 4, Material.WOOD);
                    prev = p;
                }
            }
            double yb = Math.min(type.freeboard * 0.35, sheerY(s) - 0.35);
            double hb = halfBreadthAt(s, yb) - thick - 0.06;
            if (hb > 0.2) mesh.beam(m(s, -hb, yb), m(s, hb, yb), 0.05 + type.beam * 0.004, 4, Material.WOOD);
        }
        // Bodenbretter und Decks
        if (type.hold) {
            deck(-halfLength * 0.82, -halfLength * 0.38, type.freeboard * 0.35);
            deck(halfLength * 0.38, halfLength * 0.82, type.freeboard * 0.35);
            cargo();
        } else deck(-halfLength * 0.84, halfLength * 0.84, keel(0) + type.draft * 0.45);
        if (type.hasSail()) mast();
        rudder();
        if (type.shields) shields();
    }

    /** Steven: aus dem Kielende hinauf über die Bordkante, vorn mit Tierkopf, achtern mit eingerollter Spitze [G]. */
    private void steven(int dir) {
        double top = sheerY(halfLength * dir), rise = type.length > 12 ? 1.0 : 0.25;
        int n = 14;
        double[] prev = null;
        for (int i = 0; i <= n; i++) {
            double f = i / (double) n;
            double y = keel(halfLength * dir * 0.999) - 0.04 + (top + rise - keel(halfLength * dir * 0.999)) * f;
            double out = 0.6 * Math.pow(f, 1.8) * (type.length > 12 ? 1 : 0.35);
            double[] p = m(dir * (halfLength + out), 0, y);
            if (prev != null) mesh.beam(prev, p, 0.09 - 0.03 * f, 0.09 - 0.03 * (f + 1.0 / n), 4, Material.WOOD);
            prev = p;
        }
        if (type.dragonHead && dir > 0) {
            // Hals nach vorn gebogen, Kopf mit Maul und zwei Ohren
            double s0 = halfLength + 0.6, y0 = top + rise;
            double[][] neck = {{s0, y0}, {s0 + 0.25, y0 + 0.45}, {s0 + 0.55, y0 + 0.75}, {s0 + 0.95, y0 + 0.82}};
            for (int i = 0; i + 1 < neck.length; i++) mesh.beam(m(neck[i][0], 0, neck[i][1]), m(neck[i + 1][0], 0, neck[i + 1][1]), 0.08 - 0.01 * i, 0.07 - 0.01 * i, 6, Material.WOOD);
            mesh.beam(m(s0 + 0.95, 0, y0 + 0.82), m(s0 + 1.35, 0, y0 + 0.7), 0.07, 0.02, 6, Material.WOOD);
            for (int side = -1; side <= 1; side += 2) mesh.beam(m(s0 + 0.85, side * 0.05, y0 + 0.85), m(s0 + 0.75, side * 0.12, y0 + 1.1), 0.025, 0.005, 4, Material.WOOD);
        } else if (type.length > 12 && dir < 0) {
            double s0 = -halfLength - 0.6, y0 = top + rise;
            mesh.beam(m(s0, 0, y0), m(s0 - 0.2, 0, y0 + 0.4), 0.06, 0.04, 6, Material.WOOD);
            mesh.beam(m(s0 - 0.2, 0, y0 + 0.4), m(s0 + 0.05, 0, y0 + 0.62), 0.04, 0.02, 6, Material.WOOD);
        }
    }

    private void deck(double s0, double s1, double y) {
        int n = Math.max(2, (int) Math.round((s1 - s0) / 0.5));
        for (int i = 0; i < n; i++) {
            double a = s0 + (s1 - s0) * i / n, b = s0 + (s1 - s0) * (i + 1) / n;
            double ha = halfBreadthAt(a, y) - 0.06, hb = halfBreadthAt(b, y) - 0.06;
            if (ha < 0.1 || hb < 0.1) continue;
            mesh.quad(m(a, -ha, y), m(a, ha, y), m(b, hb, y), m(b, -hb, y), Material.DECK, new double[]{-ha, a, ha, a, hb, b, -hb, b});
        }
    }

    /** Laderaum der Knorr: Ballen und Fässer auf dem Boden [G]. */
    private void cargo() {
        java.util.SplittableRandom r = new java.util.SplittableRandom(950);
        double y0 = keel(0) + 0.35;
        mesh.quad(m(-halfLength * 0.38, -1.6, y0), m(-halfLength * 0.38, 1.6, y0), m(halfLength * 0.38, 1.6, y0), m(halfLength * 0.38, -1.6, y0), Material.DECK);
        for (double s = -halfLength * 0.34; s < halfLength * 0.34; s += 1.25)
            for (double q = -1.5; q <= 1.5; q += 1.0) {
                if (r.nextDouble() < 0.25) continue;
                double h = 0.5 + r.nextDouble() * 0.35, y = y0 + h / 2;
                if (halfBreadthAt(s, y + h / 2) < Math.abs(q) + 0.6) continue;
                box(s + (r.nextDouble() - 0.5) * 0.2, q, y, 0.55, 0.42, h / 2);
                if (r.nextDouble() < 0.4) box(s, q, y + h, 0.45, 0.35, 0.22);
            }
    }

    private void box(double s, double q, double y, double hs, double hq, double hy) {
        double[][] p = new double[8][];
        int i = 0;
        for (int dy = -1; dy <= 1; dy += 2) for (int[] c : new int[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) p[i++] = m(s + c[0] * hs, q + c[1] * hq, y + dy * hy);
        int[][] f = {{4, 5, 6, 7}, {3, 2, 1, 0}, {0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 0, 4, 7}};
        double[] ctr = m(s, q, y);
        for (int[] ff : f) {
            double[] a = p[ff[0]], b = p[ff[1]], c = p[ff[2]], d = p[ff[3]];
            double[] mid = {(a[0] + c[0]) / 2 - ctr[0], (a[1] + c[1]) / 2 - ctr[1], (a[2] + c[2]) / 2 - ctr[2]};
            if (Mesh.dot(Mesh.normal(a, b, d), mid) >= 0) mesh.quad(a, b, c, d, Material.CARGO); else mesh.quad(a, d, c, b, Material.CARGO);
        }
    }

    /** Mast mit Wanten und Vorstag; die Rah und das Segel sind beweglich ({@link Longship#dynamicMesh}). */
    private void mast() {
        double s = type.mastStation(), top = type.yardHeight + 0.7;
        mesh.beam(m(s, 0, keel(s) + 0.1), m(s, 0, top), 0.16 * Math.sqrt(type.beam / 4), 0.09 * Math.sqrt(type.beam / 4), 8, Material.WOOD);
        double[] t = m(s, 0, top - 0.4);
        for (int side = -1; side <= 1; side += 2)
            for (double ds : new double[]{-1.2, 0.0}) {
                double sp = s + ds;
                mesh.beam(t, m(sp, side * (halfBeam(sp) - 0.05), sheerY(sp)), 0.015, 3, Material.ROPE);
            }
        double sb = halfLength * 0.86;
        mesh.beam(t, m(sb, 0, sheerY(sb) + 0.1), 0.015, 3, Material.ROPE);
        mesh.beam(t, m(-sb, 0, sheerY(-sb) + 0.1), 0.015, 3, Material.ROPE);
    }

    /** Steuerruder an Steuerbord achtern (das Blatt bewegt sich nicht, die Pinne zeigt nach innen). */
    private void rudder() {
        double s = -halfLength * 0.8, q = halfBeam(s) + 0.12, top = sheerY(s) + 0.5, bot = keel(s) - type.draft * 0.35;
        double[] a = m(s + 0.15, q, top), b = m(s - 0.1, q + 0.05, bot);
        mesh.beam(a, b, 0.09 * Math.sqrt(type.beam / 4) + 0.02, 6, Material.WOOD);
        double bl = Math.min(1.6, type.draft * 1.4 + 0.3), bw = 0.22 + type.beam * 0.03;
        double[] p0 = m(s - 0.1, q + 0.05, bot), p1 = m(s - 0.1 - bw, q + 0.05, bot + 0.1), p2 = m(s - 0.05 - bw, q + 0.05, bot + bl), p3 = m(s + 0.05, q + 0.05, bot + bl);
        mesh.quad2(p0, p1, p2, p3, Material.WOOD);
        mesh.beam(m(s + 0.12, q, top - 0.15), m(s + 0.12, q - 1.0, top - 0.05), 0.03, 4, Material.WOOD);
    }

    /** Schilde am Bord: Linde bemalt (Farbe nach der Nummer in v), Buckel aus Eisen. */
    private void shields() {
        double[] st = type.oarStations();
        int idx = 0;
        for (int side = -1; side <= 1; side += 2)
            for (double s0 : st) {
                double s = s0 + type.oarSpacing / 2;
                if (Math.abs(u(s)) > 0.85) continue;
                double y = sheerY(s) - 0.18, q = side * (halfBeam(s) + 0.07), r = 0.42;
                double[] c = m(s, q, y), n = m(0, side, 0.12);
                double[] e1 = m(1, 0, 0), e2 = Mesh.norm(Mesh.cross(n, e1));
                int nseg = 12, color = idx++ % 5;
                for (int i = 0; i < nseg; i++) {
                    double a0 = 2 * Math.PI * i / nseg, a1 = 2 * Math.PI * (i + 1) / nseg;
                    double[] p0 = {c[0] + (e1[0] * Math.cos(a0) + e2[0] * Math.sin(a0)) * r, c[1] + (e1[1] * Math.cos(a0) + e2[1] * Math.sin(a0)) * r, c[2] + (e1[2] * Math.cos(a0) + e2[2] * Math.sin(a0)) * r};
                    double[] p1 = {c[0] + (e1[0] * Math.cos(a1) + e2[0] * Math.sin(a1)) * r, c[1] + (e1[1] * Math.cos(a1) + e2[1] * Math.sin(a1)) * r, c[2] + (e1[2] * Math.cos(a1) + e2[2] * Math.sin(a1)) * r};
                    double[] tip = {c[0] + n[0] * 0.05, c[1] + n[1] * 0.05, c[2] + n[2] * 0.05};
                    int ia = shields.vertex(tip[0], tip[1], tip[2], n[0], n[1], n[2], 0.5, color + 0.5, Material.SHIELD);
                    int ib = shields.vertex(p0[0], p0[1], p0[2], n[0], n[1], n[2], 0.5 + 0.5 * Math.cos(a0), color + 0.5 + 0.4 * Math.sin(a0), Material.SHIELD);
                    int ic = shields.vertex(p1[0], p1[1], p1[2], n[0], n[1], n[2], 0.5 + 0.5 * Math.cos(a1), color + 0.5 + 0.4 * Math.sin(a1), Material.SHIELD);
                    if (Mesh.dot(Mesh.normal(tip, p0, p1), n) >= 0) shields.tri(ia, ib, ic); else shields.tri(ia, ic, ib);
                }
                shields.beam(c, new double[]{c[0] + n[0] * 0.12, c[1] + n[1] * 0.12, c[2] + n[2] * 0.12}, 0.09, 0.03, 6, Material.IRON);
            }
    }

    // ------------------------------------------------------------------ Hilfen

    /** Außennormale der Haut (aus den Nachbarpunkten), in s, q, y. */
    double[] outward(double s, double t, int side) {
        double ds = 0.05, dt = 0.01;
        double[] a = point(s - ds, t, side), b = point(s + ds, t, side), c = point(s, Math.max(0, t - dt), side), d = point(s, Math.min(1, t + dt), side);
        double[] e1 = {b[0] - a[0], b[1] - a[1], b[2] - a[2]}, e2 = {d[0] - c[0], d[1] - c[1], d[2] - c[2]};
        double[] n = Mesh.norm(Mesh.cross(e1, e2));
        // nach außen: q in Richtung der Seite, sonst nach unten
        if (n[1] * side < 0 || (Math.abs(n[1]) < 1e-6 && n[2] > 0)) n = new double[]{-n[0], -n[1], -n[2]};
        return n;
    }

    static double[] add(double[] p, double[] n, double f) { return new double[]{p[0] + n[0] * f, p[1] + n[1] * f, p[2] + n[2] * f}; }

    /** Säulen für das Streifenmodell: je Spant zwei Säulen (Backbord, Steuerbord) über die Wasserlinie verteilt. */
    private double[][] buildColumns() {
        int n = 12;
        java.util.List<double[]> cols = new java.util.ArrayList<>();
        double ds = type.length * 0.92 / n;
        for (int i = 0; i < n; i++) {
            double s = -type.length * 0.46 + ds * (i + 0.5);
            double hw = halfBreadthAt(s, 0);
            if (hw < 0.05) continue;
            double keelY = keel(s) * 0.75;               // mittlere Tiefe des Spants unter der Wasserlinie
            for (int side = -1; side <= 1; side += 2) cols.add(new double[]{s, side * hw / 2, ds * hw, keelY, sheerY(s)});
        }
        return cols.toArray(new double[0][]);
    }
}
