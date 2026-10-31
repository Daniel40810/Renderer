package com.dan.fall.demo;

import com.dan.fall.Fall;
import com.dan.fall.gl.FallGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

/**
 * Die Szene der Vorführung ohne App: eine Wand von 250 m über einem See, darauf ein Fall mit 40 m breiter Kante. Das
 * Auge kreist um einen Punkt ({@link #yaw}, {@link #pitch}, {@link #dist}); die Zeit läuft, außer {@link #fixedTime}
 * ist gesetzt.
 */
public final class FallScene implements GLEventListener {
    public final Fall fall = DemoCliff.fall();
    public final FallGl gl = new FallGl();
    public volatile double yaw = 0, pitch = 4, dist = 700, tx = 0, ty = 130, tz = 10, fovy = 50;
    public volatile double sunAlt = 30, sunAz = 160, exposure = 1.4;
    /** Feste Zeit (s) für Bilder, sonst NaN (Uhr läuft). */
    public volatile double fixedTime = Double.NaN;
    public volatile String check = "";
    /** Eingänge geändert: update() im GL-Faden vor dem nächsten Bild. */
    public volatile boolean dirty;
    /** Boden geändert (Stufenwand ein und aus, seit Phase 7): neues Netz vor dem nächsten Bild. */
    public volatile boolean groundDirty;

    /** Lauf über Fels an der Stufenwand ein oder aus (seit Phase 7). */
    public void course(boolean on) {
        fall.overRock = on;
        fall.ground = on ? DemoCliff::stufen : DemoCliff::height;
        groundDirty = true;
        dirty = true;
    }
    private boolean ready;
    private int w = 1, h = 1, frame;
    private final long t0 = System.nanoTime();

    @Override
    public void init(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.init(g);
        gl.setGround(g, DemoCliff::height, -700, -700, 1400, 281, 0);
        ready = true;
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int ww, int hh) { w = Math.max(1, ww); h = Math.max(1, hh); }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        if (!ready) return;
        if (groundDirty) { groundDirty = false; gl.setGround(g, fall.ground, -700, -700, 1400, 281, 0); check = ""; }
        if (dirty) { dirty = false; fall.update(); }
        w = Math.max(1, d.getSurfaceWidth());
        h = Math.max(1, d.getSurfaceHeight());
        g.glViewport(0, 0, w, h);
        double[] sky = skyColor();
        double k = exposure;
        g.glClearColor((float) disp(sky[0] * k), (float) disp(sky[1] * k), (float) disp(sky[2] * k), 1);
        g.glClearDepth(1);
        g.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        FallGl.View v = view();
        FallGl.Light l = light();
        gl.drawGround(g, v, l, sky);
        double t = Double.isNaN(fixedTime) ? (System.nanoTime() - t0) / 1e9 : fixedTime;
        gl.draw(g, fall, v, l, t);
        if (check.isEmpty() && frame > 3) check = gl.selfCheck(g, fall);
        frame++;
    }

    static double disp(double c) { return Math.pow(1 - Math.exp(-c), 1 / 2.2); }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    double[] sunDir() {
        double a = Math.toRadians(sunAlt), z = Math.toRadians(sunAz);
        return new double[]{Math.cos(a) * Math.sin(z), Math.sin(a), -Math.cos(a) * Math.cos(z)};
    }

    public double[] skyColor() {
        double b = 0.25 + 0.75 * smooth(-4, 20, sunAlt);
        return new double[]{0.45 * b, 0.62 * b, 0.85 * b};
    }

    public FallGl.Light light() {
        FallGl.Light l = new FallGl.Light();
        l.sunDir = sunDir();
        double s = 2.6 * smooth(-1, 8, sunAlt);
        l.sunColor = new double[]{s, s * (0.75 + 0.2 * smooth(0, 25, sunAlt)), s * (0.6 + 0.3 * smooth(0, 25, sunAlt))};
        double[] sk = skyColor();
        l.ambient = new double[]{sk[0] * 0.3, sk[1] * 0.3, sk[2] * 0.3};
        l.fogColor = sk;
        l.fogDistance = 25_000;
        l.display = exposure;
        return l;
    }

    public FallGl.View view() {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double fx = Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr), fz = -Math.cos(yr) * Math.cos(pr);
        double ex = tx - fx * dist, ey = ty - fy * dist, ez = tz - fz * dist;
        FallGl.View v = new FallGl.View();
        v.eye = new double[]{ex, ey, ez};
        v.viewProj = mul(perspective(fovy, w / (double) h, 0.5, 30_000), lookAt(ex, ey, ez, tx, ty, tz));
        return v;
    }

    // ------------------------------------------------------------------ Matrizen (Spalten zuerst)

    public static float[] perspective(double fovy, double aspect, double n, double f) {
        double t = 1 / Math.tan(Math.toRadians(fovy) / 2);
        float[] m = new float[16];
        m[0] = (float) (t / aspect);
        m[5] = (float) t;
        m[10] = (float) ((f + n) / (n - f));
        m[11] = -1;
        m[14] = (float) (2 * f * n / (n - f));
        return m;
    }

    public static float[] lookAt(double ex, double ey, double ez, double cx, double cy, double cz) {
        double fx = cx - ex, fy = cy - ey, fz = cz - ez, fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double sx = -fz, sy = 0, sz = fx, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
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

    public static float[] mul(float[] a, float[] b) {
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
    public void dispose(GLAutoDrawable d) { gl.dispose(d.getGL().getGL4()); }
}
