package com.dan.cliff.demo;

import com.dan.cliff.Blockfield;
import com.dan.cliff.Cliff;
import com.dan.cliff.CliffMap;
import com.dan.cliff.Overhang;
import com.dan.cliff.Sod;
import com.dan.cliff.StrataMap;
import com.dan.cliff.CNoise;
import com.dan.cliff.Tor;
import com.dan.cliff.gl.CliffGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.util.List;

/**
 * Die Szene der Vorführung ohne App: eine Wand von 300 m über einem Talboden, oben eine Hochfläche, ein Absatz in
 * halber Höhe, ein Überhang unter der Kante, ein nasser Streifen, wo ein Fall herabkäme, Geröll am Fuß. Das Auge
 * kreist um einen Punkt an der Wand ({@link #yaw}, {@link #pitch}, {@link #dist}).
 */
public final class CliffScene implements GLEventListener {
    public final Cliff cliff;
    /** Seit Brocken Phase 8: Granit mit zwei Felsburgen auf der Hochfläche und einem Blockmeer am Fuß. */
    public final boolean granit;
    /** Seit Sylt Phase 4: das Rote Kliff aus Lockergestein ({@link DemoWall#kliff}) mit Schichtkarte, Grasnarbe und Hohlkehle. */
    public final boolean kliff;
    /** Das Höhenfeld der Szene. */
    public final Cliff.Heights ground;
    public final List<Sod> sods;
    public final List<Tor> tors;
    public final Blockfield blocks;
    public volatile int blockCount;
    public final CliffGl gl = new CliffGl();
    public final CliffMap map;
    public final Overhang overhang;
    public volatile double yaw = 10, pitch = 8, dist = 600, tx = 0, ty = 150, tz = -60, fovy = 50;
    public volatile double sunAlt = 30, sunAz = 160, rainWet = 0, snow = 0, exposure = 1.4;
    /** Raueis (m) aus 250°, seit Brocken Phase 10; Voreinstellung aus {@code -Draueis=cm}. */
    public volatile double rime = Double.parseDouble(System.getProperty("raueis", "0")) / 100;
    public volatile String check = "";
    public volatile double gpuMs;
    private boolean ready;
    private int w = 1, h = 1;
    private final int[] query = new int[2];
    private int frame;

    /** Die Wand ({@link DemoWall}). */
    public static double height(double x, double z) { return DemoWall.height(x, z); }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    public CliffScene() { this(false); }

    public CliffScene(boolean granit) { this(granit ? "granit" : "gneis"); }

    /** Art der Wand: „gneis“, „granit“ oder „kliff“ (Lockergestein, seit Sylt Phase 4). */
    public CliffScene(String art) {
        this.granit = art.equals("granit");
        this.kliff = art.equals("kliff");
        ground = kliff ? DemoWall::kliff : DemoWall::height;
        cliff = granit ? Cliff.granit() : kliff ? Cliff.rotesKliff() : new Cliff();
        if (granit) {
            tors = List.of(new Tor(-40, height(-40, -175), -175, 7, 6, 20, 3), new Tor(25, height(25, -190), -190, 4.5, 5, 75, 8));
            blocks = new Blockfield();
            blocks.ground = CliffScene::height;
            blocks.density = (x, z) -> smooth(15, 35, z) * (1 - smooth(110, 160, z)) * (0.55 + 0.45 * smooth(-200, 0, -Math.abs(x)));
        } else {
            tors = List.of();
            blocks = null;
        }
        if (kliff) {
            map = CliffMap.build(cliff, ground, -300, -300, 1, 600, 4, 18);
            // untere Grenze (Kaolinsand) von Westen nach Osten fallend, Flugsand oben 1 bis 3 m
            cliff.strata = StrataMap.build(ground, -300, -300, 1, 600, 600, 30,
                    (x, z) -> 3.5 + 3 * (CNoise.value(x / 220, 0, 2, 11) - 0.5) - x * 0.008, (x, z) -> 1.0 + 2.0 * CNoise.value(x / 70, 0, 6, 12), 1.5);
            // die Brandung hat den Fuß in der Mitte ausgewaschen
            for (double x = -70; x <= 70; x += 1) cliff.strata.paintNotch(x, DemoWall.kliffFoot(x) - 0.5, 3, 1.4 * (1 - smooth(40, 70, Math.abs(x))));
            sods = List.of(new Sod(edge(-250, 250, 1), 5));
            overhang = null;
        } else {
            map = CliffMap.build(cliff, ground, -600, -600, 2.5, 480);
            map.addFall(0, 20, 290, 10, 1);
            double wy = 268;
            overhang = new Overhang(-6, wy, zAt(-6, wy) + 1, 180, 46, 26, 11, 7);
            sods = List.of();
        }
    }

    /** Die Oberkante des Kliffs als Linie von x0 bis x1 (Schritt m): höchster Punkt nördlich des Fußes, Richtung aus der Wand. */
    double[] edge(double x0, double x1, double step) {
        int n = (int) Math.round((x1 - x0) / step) + 1;
        double[] e = new double[n * 5];
        for (int k = 0; k < n; k++) {
            double x = x0 + k * step, zEdge = DemoWall.kliffEdge(x), best = -1e9, bz = zEdge;
            for (double z = zEdge + 6; z > zEdge - 10; z -= 0.25) {
                // die Kante: wo der Hang von steil nach flach wechselt
                double sl = (ground.height(x, z - 0.5) - ground.height(x, z + 0.5));
                double v = ground.height(x, z) - 3 * Math.max(0, 0.5 - sl);
                if (sl > 0.6 && v > best) { best = v; bz = z; }
            }
            double gx = ground.height(x + 1, bz) - ground.height(x - 1, bz), gz = ground.height(x, bz + 1) - ground.height(x, bz - 1);
            double ox = -gx, oz = -gz, l = Math.hypot(ox, oz);
            e[k * 5] = x;
            e[k * 5 + 1] = ground.height(x, bz);
            e[k * 5 + 2] = bz;
            e[k * 5 + 3] = l > 1e-6 ? ox / l : 0;
            e[k * 5 + 4] = l > 1e-6 ? oz / l : 1;
        }
        return e;
    }

    static double zAt(double x, double y) { return DemoWall.zAt(x, y); }

    @Override
    public void init(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.init(g);
        gl.upload(g, map);
        if (kliff) gl.setWall(g, ground, -300, -300, 600, 601);
        else gl.setWall(g, ground, -600, -600, 1200, 481);
        gl.setOverhangs(overhang == null ? List.of() : List.of(overhang));
        gl.setSods(sods);
        gl.setTors(tors);
        g.glGenQueries(2, query, 0);
        ready = true;
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int ww, int hh) { w = Math.max(1, ww); h = Math.max(1, hh); }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        if (!ready) return;
        w = Math.max(1, d.getSurfaceWidth());
        h = Math.max(1, d.getSurfaceHeight());
        g.glViewport(0, 0, w, h);
        double[] sky = skyColor();
        double k = exposure;
        g.glClearColor((float) disp(sky[0] * k), (float) disp(sky[1] * k), (float) disp(sky[2] * k), 1);
        g.glClearDepth(1);
        g.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        CliffGl.View v = view();
        CliffGl.Light l = light();
        if (frame > 2) {
            long[] t = new long[2];
            g.glGetQueryObjecti64v(query[0], GL4.GL_QUERY_RESULT, t, 0);
            g.glGetQueryObjecti64v(query[1], GL4.GL_QUERY_RESULT, t, 1);
            gpuMs = gpuMs * 0.9 + (t[1] - t[0]) / 1e6 * 0.1;
        }
        g.glQueryCounter(query[0], GL4.GL_TIMESTAMP);
        gl.drawWall(g, cliff, v, l);
        gl.drawOverhangs(g, cliff, v, l);
        gl.drawSods(g, cliff, v, l);
        gl.drawTors(g, cliff, v, l);
        if (blocks != null) blockCount = gl.drawBlocks(g, cliff, blocks, v, l);
        g.glQueryCounter(query[1], GL4.GL_TIMESTAMP);
        if (check.isEmpty() && frame > 3) check = gl.selfCheck(g, cliff);
        frame++;
    }

    static double disp(double c) { return Math.pow(1 - Math.exp(-c), 1 / 2.2); }

    double[] sunDir() {
        double a = Math.toRadians(sunAlt), z = Math.toRadians(sunAz);
        return new double[]{Math.cos(a) * Math.sin(z), Math.sin(a), -Math.cos(a) * Math.cos(z)};
    }

    double[] skyColor() {
        double b = 0.25 + 0.75 * smooth(-4, 20, sunAlt);
        return new double[]{0.45 * b, 0.62 * b, 0.85 * b};
    }

    CliffGl.Light light() {
        CliffGl.Light l = new CliffGl.Light();
        l.sunDir = sunDir();
        double s = 2.6 * smooth(-1, 8, sunAlt);
        l.sunColor = new double[]{s, s * (0.75 + 0.2 * smooth(0, 25, sunAlt)), s * (0.6 + 0.3 * smooth(0, 25, sunAlt))};
        double[] sk = skyColor();
        l.ambient = new double[]{sk[0] * 0.9, sk[1] * 0.9, sk[2] * 0.9};
        l.fogColor = sk;
        l.fogDistance = 25_000;
        l.rainWet = rainWet;
        l.snow = snow;
        l.rimeThickness = rime;
        l.rimeKind = rime > 0 ? 1 : 0;
        l.rimeFromDeg = 250;
        l.display = exposure;
        return l;
    }

    CliffGl.View view() {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double fx = Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr), fz = -Math.cos(yr) * Math.cos(pr);
        double ex = tx - fx * dist, ey = ty - fy * dist, ez = tz - fz * dist;
        CliffGl.View v = new CliffGl.View();
        v.eye = new double[]{ex, ey, ez};
        v.viewProj = mul(perspective(fovy, w / (double) h, 0.5, 30_000), lookAt(ex, ey, ez, tx, ty, tz));
        v.pixelAngle = Math.toRadians(fovy) / h;
        return v;
    }

    // ------------------------------------------------------------------ Matrizen (Spalten zuerst)

    static float[] perspective(double fovy, double aspect, double n, double f) {
        double t = 1 / Math.tan(Math.toRadians(fovy) / 2);
        float[] m = new float[16];
        m[0] = (float) (t / aspect);
        m[5] = (float) t;
        m[10] = (float) ((f + n) / (n - f));
        m[11] = -1;
        m[14] = (float) (2 * f * n / (n - f));
        return m;
    }

    static float[] lookAt(double ex, double ey, double ez, double cx, double cy, double cz) {
        double fx = cx - ex, fy = cy - ey, fz = cz - ez, fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double sx = fy * 0 - fz * 1, sy = fz * 0 - fx * 0, sz = fx * 1 - fy * 0, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= sl; sy /= sl; sz /= sl;
        double ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
        float[] m = new float[16];
        m[0] = (float) sx; m[4] = (float) sy; m[8] = (float) sz;
        m[1] = (float) ux; m[5] = (float) uy; m[9] = (float) uz;
        m[2] = (float) -fx; m[6] = (float) -fy; m[10] = (float) -fz;
        m[12] = (float) -(sx * ex + sy * ey + sz * ez);
        m[13] = (float) -(ux * ex + uy * ey + uz * ez);
        m[14] = (float) (fx * ex + fy * ey + fz * ez);
        m[15] = 1;
        return m;
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + rr] * b[c * 4 + k];
                r[c * 4 + rr] = s;
            }
        return r;
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.dispose(g);
        g.glDeleteQueries(2, query, 0);
    }
}
