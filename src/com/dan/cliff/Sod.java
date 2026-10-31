package com.dan.cliff;

/**
 * Die Grasnarbe als Überhang (seit Sylt Phase 4): an der Oberkante eines Kliffs aus Lockergestein halten die Wurzeln
 * von Strandhafer und Heide eine Matte aus Gras, Wurzeln und Sand, die über die Kante hinausragt, wenn der Lehm
 * darunter abbröckelt; sie hängt durch, reißt stellenweise ab und fehlt dort. Ein Höhenfeld kann das nicht; die Matte
 * wird als Schlauch entlang der Kante gezeichnet, mit demselben Material ({@code gl/cliff.glsl}, Lockergestein: oben
 * Gras, darunter Narbe und Sand nach der Tiefe unter der Oberkante).
 * <p>
 * Eingang ist die Kante als Linie: je Punkt Lage auf der Oberkante (x, y, z) und die Richtung aus der Wand heraus
 * (waagerecht, ox, oz). Die Rückseite steckt {@link #embed} m im Gelände.
 */
public final class Sod {
    /** Kante: je Punkt x, y, z, ox, oz (Richtung aus der Wand, Einheitsvektor in der Waagerechten). */
    public final double[] edge;
    /** Mittlere Ausladung über die Kante (m), Dicke der Matte (m), wie weit die Spitze durchhängt (m). */
    public double reach = 0.6, thickness = 0.3, droop = 0.25;
    /** Wie viel der Kante ohne Überhang ist (abgerissen), 0..1. */
    public double gaps = 0.3;
    public double embed = 0.8;
    public final int seed;

    public Sod(double[] edge, int seed) {
        if (edge.length < 10 || edge.length % 5 != 0) throw new IllegalArgumentException("Kante: mindestens zwei Punkte zu je x, y, z, ox, oz");
        this.edge = edge.clone();
        this.seed = seed;
    }

    public int points() { return edge.length / 5; }

    /** Ausladung (m) am Punkt k der Kante: mit Rauschen entlang der Kante, in Lücken 0. */
    public double reachAt(int k) {
        double x = edge[k * 5], z = edge[k * 5 + 2];
        double n = CNoise.value(x / 6.0, z / 6.0, seed * 0.37, seed + 70), f = CNoise.value(x / 1.7, z / 1.7, seed * 0.11, seed + 71);
        double open = Cliff.smooth(gaps - 0.08, gaps + 0.08, n);
        return reach * open * (0.5 + 1.0 * f);
    }

    /** Querschnitt (aus der Wand heraus, nach oben) in m, gegen den Uhrzeigersinn von hinten oben, für Ausladung r. */
    double[][] section(double r) {
        double t = thickness, dr = droop * Math.min(1, r / Math.max(0.05, reach));
        return new double[][]{
                {-embed, 0.02}, {r * 0.45, 0.06}, {r, -dr}, {r * 0.92, -dr - t * 0.8}, {r * 0.35, -t * 1.05}, {-embed, -t * 1.3}
        };
    }

    /** Das Netz in Weltlage: je Kantenpunkt ein Querschnitt mit sechs Ecken, geschlossen. */
    public Overhang.Mesh mesh() {
        int np = points(), ns = 6;
        float[] p = new float[np * ns * 3];
        for (int k = 0; k < np; k++) {
            double x = edge[k * 5], y = edge[k * 5 + 1], z = edge[k * 5 + 2], ox = edge[k * 5 + 3], oz = edge[k * 5 + 4];
            double[][] s = section(reachAt(k));
            for (int c = 0; c < ns; c++) {
                int q = (k * ns + c) * 3;
                p[q] = (float) (x + ox * s[c][0]);
                p[q + 1] = (float) (y + s[c][1]);
                p[q + 2] = (float) (z + oz * s[c][0]);
            }
        }
        float[] v = new float[np * ns * 6];
        for (int k = 0; k < np; k++)
            for (int c = 0; c < ns; c++) {
                int ka = Math.max(0, k - 1), kb = Math.min(np - 1, k + 1), ca = (c + ns - 1) % ns, cb = (c + 1) % ns;
                double[] along = sub(p, (kb * ns + c) * 3, (ka * ns + c) * 3), round = sub(p, (k * ns + cb) * 3, (k * ns + ca) * 3);
                // Normale nach außen: quer zum Umlauf des Querschnitts und zur Kante
                double nx = round[1] * along[2] - round[2] * along[1], ny = round[2] * along[0] - round[0] * along[2], nz = round[0] * along[1] - round[1] * along[0];
                double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
                // Lage relativ zur Mitte des Querschnitts: die Normale zeigt von ihr weg
                double mx = 0, my = 0, mz = 0;
                for (int cc = 0; cc < ns; cc++) { int q = (k * ns + cc) * 3; mx += p[q]; my += p[q + 1]; mz += p[q + 2]; }
                int q = (k * ns + c) * 3;
                double dx = p[q] - mx / ns, dy = p[q + 1] - my / ns, dz = p[q + 2] - mz / ns;
                if (nx * dx + ny * dy + nz * dz < 0) l = -l;
                if (Math.abs(l) < 1e-9) l = 1;
                int o = (k * ns + c) * 6;
                v[o] = p[q]; v[o + 1] = p[q + 1]; v[o + 2] = p[q + 2];
                v[o + 3] = (float) (nx / l); v[o + 4] = (float) (ny / l); v[o + 5] = (float) (nz / l);
            }
        int[] idx = new int[(np - 1) * ns * 6];
        int t = 0;
        for (int k = 0; k < np - 1; k++)
            for (int c = 0; c < ns; c++) {
                int a = k * ns + c, b = k * ns + (c + 1) % ns, d = (k + 1) * ns + c, e = (k + 1) * ns + (c + 1) % ns;
                idx[t++] = a; idx[t++] = b; idx[t++] = e;
                idx[t++] = a; idx[t++] = e; idx[t++] = d;
            }
        return new Overhang.Mesh(v, idx);
    }

    private static double[] sub(float[] p, int a, int b) { return new double[]{p[a] - p[b], p[a + 1] - p[b + 1], p[a + 2] - p[b + 2]}; }

    /** Größte Ausladung über die Kante (m) und Anteil der Kante mit Überhang, für Prüfung und HUD. */
    public double[] stats() {
        double m = 0, on = 0;
        for (int k = 0; k < points(); k++) {
            double r = reachAt(k);
            m = Math.max(m, r);
            if (r > 0.1) on++;
        }
        return new double[]{m, on / points()};
    }
}
