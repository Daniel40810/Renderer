package com.dan.cliff;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Eine Felsburg aus Wollsäcken (seit Brocken Phase 8): Granitblöcke, die die Verwitterung entlang der Klüfte gerundet und
 * übereinander stehen gelassen hat, wie Teufelskanzel und Hexenaltar auf dem Brocken. Ein Höhenfeld kann das nicht; die
 * Burg ist ein eigenes Netz mit demselben Material wie der Fels ({@code gl/cliff.glsl}, als Fels erzwungen).
 * <p>
 * Aufbau: {@link #layers} Lagen, jede aus ein bis drei Blöcken (Superellipsoide mit dem Exponenten {@link #roundness}:
 * 1 = Kugel, 0,2 = fast ein Quader), nach oben kleiner und gegeneinander versetzt; mit {@link #flatTop} liegt oben eine
 * flache, etwas überstehende Platte (Kanzel, Altar). Die unterste Lage steckt {@link #embed} m im Boden. Gleicher
 * {@link #seed}, gleiche Burg.
 */
public final class Tor {
    public final double x, y, z, height, width, headingDeg;
    public final int seed;
    public int layers;
    public double roundness = 0.6, embed = 0.6;
    public boolean flatTop = true;

    /** Fuß der Burg (x, y = Boden, z), Höhe über dem Boden, Breite der untersten Lage, Ausrichtung (Grad von Nord). */
    public Tor(double x, double y, double z, double height, double width, double headingDeg, int seed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.height = height;
        this.width = width;
        this.headingDeg = headingDeg;
        this.seed = seed;
        this.layers = Math.max(2, (int) Math.round(height / 1.3));
    }

    /** Ein Block: Mitte, halbe Ausdehnung (x, y, z), Drehung um die Senkrechte (Grad), Exponent der Rundung. */
    public record Block(double cx, double cy, double cz, double ax, double ay, double az, double yawDeg, double e) { }

    /** Die Blöcke der Burg, von unten nach oben. */
    public List<Block> blocks() {
        Random r = new Random(seed * 7919L + 13);
        List<Block> out = new ArrayList<>();
        double h0 = height + embed, base = y - embed, lh = h0 / (0.94 * (layers - 1) + (flatTop ? 0.7 : 1));
        double ox = 0, oz = 0;
        for (int l = 0; l < layers; l++) {
            boolean top = flatTop && l == layers - 1;
            double w = width * (1 - 0.16 * l) * (0.85 + 0.3 * r.nextDouble());
            double hh = top ? lh * 0.7 : lh * (0.85 + 0.3 * r.nextDouble());
            int n = l == 0 ? 2 + r.nextInt(2) : top ? 1 : 1 + r.nextInt(2);
            ox += (r.nextDouble() - 0.5) * 0.18 * width;
            oz += (r.nextDouble() - 0.5) * 0.18 * width;
            double yaw = headingDeg + (r.nextDouble() - 0.5) * 24;
            for (int k = 0; k < n; k++) {
                double frac = n == 1 ? 0 : (k / (n - 1.0) - 0.5);
                double bw = top ? w * 0.8 : w / n * (1.35 + 0.15 * r.nextDouble());
                double bd = top ? w * 0.6 : w * (0.55 + 0.2 * r.nextDouble());
                double h = Math.toRadians(yaw), along = frac * w * 0.55;
                double cx = x + ox + Math.cos(h) * along, cz = z + oz + Math.sin(h) * along;
                double e = top ? Math.max(0.35, roundness * 0.65) : roundness * (0.85 + 0.3 * r.nextDouble());
                out.add(new Block(cx, base + hh / 2, cz, bw / 2, hh / 2 * 1.05, bd / 2, yaw + (r.nextDouble() - 0.5) * 10, e));
            }
            base += hh * 0.94;   // die Blöcke liegen ein wenig ineinander: keine Spalten, durch die man sieht
        }
        return out;
    }

    /** Oberkante der Burg (m, gleicher Bezug wie y). */
    public double top() {
        double t = -1e9;
        for (Block b : blocks()) t = Math.max(t, b.cy() + b.ay());
        return t;
    }

    /** Das Netz in Weltlage: je Block ein Superellipsoid mit res × res/2 Ecken (Lage, Normale). */
    public Overhang.Mesh mesh(int res) {
        List<Block> bs = blocks();
        int nu = Math.max(4, res / 2), nv = Math.max(6, res);
        int perV = (nu + 1) * (nv + 1);
        float[] v = new float[bs.size() * perV * 6];
        int[] idx = new int[bs.size() * nu * nv * 6];
        int vo = 0, io = 0, base = 0;
        for (Block b : bs) {
            double h = Math.toRadians(b.yawDeg()), ch = Math.cos(h), sh = Math.sin(h);
            for (int i = 0; i <= nu; i++) {
                double u = -Math.PI / 2 + Math.PI * i / nu;
                for (int j = 0; j <= nv; j++) {
                    double w = -Math.PI + 2 * Math.PI * j / nv;
                    double[] pn = superellipsoid(u, w, b.ax(), b.ay(), b.az(), b.e());
                    // um die Senkrechte drehen und verschieben
                    double px = pn[0] * ch - pn[2] * sh, pz = pn[0] * sh + pn[2] * ch;
                    double nx = pn[3] * ch - pn[5] * sh, nz = pn[3] * sh + pn[5] * ch;
                    v[vo++] = (float) (b.cx() + px);
                    v[vo++] = (float) (b.cy() + pn[1]);
                    v[vo++] = (float) (b.cz() + pz);
                    v[vo++] = (float) nx;
                    v[vo++] = (float) pn[4];
                    v[vo++] = (float) nz;
                }
            }
            for (int i = 0; i < nu; i++)
                for (int j = 0; j < nv; j++) {
                    int a = base + i * (nv + 1) + j, c = a + nv + 1;
                    idx[io++] = a; idx[io++] = c; idx[io++] = a + 1;
                    idx[io++] = a + 1; idx[io++] = c; idx[io++] = c + 1;
                }
            base += perV;
        }
        return new Overhang.Mesh(v, idx);
    }

    /**
     * Punkt und Normale eines Superellipsoids mit den halben Achsen ax, ay, az und dem Exponenten e an Breite u
     * (−π/2…π/2) und Länge w (−π…π): {x, y, z, nx, ny, nz}, Normale normiert.
     */
    public static double[] superellipsoid(double u, double w, double ax, double ay, double az, double e) {
        double cu = Math.cos(u), su = Math.sin(u), cw = Math.cos(w), sw = Math.sin(w);
        double x = ax * spow(cu, e) * spow(cw, e), y = ay * spow(su, e), z = az * spow(cu, e) * spow(sw, e);
        double nx = spow(cu, 2 - e) * spow(cw, 2 - e) / ax, ny = spow(su, 2 - e) / ay, nz = spow(cu, 2 - e) * spow(sw, 2 - e) / az;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-12) { nx = 0; ny = Math.signum(su); nz = 0; l = 1; }
        return new double[]{x, y, z, nx / l, ny / l, nz / l};
    }

    static double spow(double v, double p) { return Math.signum(v) * Math.pow(Math.abs(v), p); }
}
