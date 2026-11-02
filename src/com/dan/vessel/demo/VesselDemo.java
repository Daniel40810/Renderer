package com.dan.vessel.demo;

import com.dan.vessel.Boat;
import com.dan.vessel.Chain;
import com.dan.vessel.Kutter;
import com.dan.vessel.Mesh;
import com.dan.vessel.gl.VesselGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vorführung von {@code com.dan.vessel} (LituyaBay1958 Phase 15): die Edrie vor Anker in der gerechneten {@link Bucht},
 * 5 Faden tief, 40 Faden Kette, der Anker festgehakt. Eine Einzelwelle von 15 m läuft von See heran: das Boot steigt, die
 * Kette spannt sich und reißt, die Welle trägt es zum Ufer, der Rückstrom zurück in die Bucht.
 * <ul>
 * <li>{@code VesselDemo} – Fenster; Leertaste von vorn, 1/2/3 Blick, + und − Zeitraffer, P anhalten;</li>
 * <li>{@code VesselDemo --bild datei.png} – Bogen mit vier Augenblicken und den Kurven (Hub, Wasser am Boot, Trimm,
 * Zug der Kette mit der Bruchlast).</li>
 * </ul>
 */
public final class VesselDemo implements GLEventListener {
    final Bucht w = new Bucht();
    Boat b;
    Chain c;
    final VesselGl vg = new VesselGl();
    final List<double[]> kurve = new ArrayList<>();
    volatile int blick;
    volatile double tempo = 1;
    volatile boolean halt, neu;
    int fbo, fboColor, fboDepth, fboW, fboH;
    static final double GX0 = -420, GX1 = 200, GZ0 = -90, GZ1 = 90, GD = 4;

    public VesselDemo() { anfangen(); }

    void anfangen() {
        w.t = 0;
        b = Kutter.edrie();
        b.settle(w, 0, 0, 270);
        c = Kutter.edrieKette();
        double[] fl = b.hull.fairlead();
        c.lay(w, -66, 0, b.toWorld(fl[0], fl[1], fl[2]));
        b.attach(c);
        kurve.clear();
    }

    void schritt(double dt) {
        w.t += dt;
        b.step(w, dt);
        double[] p = b.position();
        if (kurve.isEmpty() || w.t - kurve.get(kurve.size() - 1)[0] >= 0.1)
            kurve.add(new double[]{w.t, p[1], w.height(p[0], p[2]), b.pitch(), c.fairleadTension() / 1000, p[0], c.broken() ? 1 : 0});
    }

    // ------------------------------------------------------------------ Netze der Bucht

    static double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }

    float[] gitter(boolean wasser) {
        int nx = (int) ((GX1 - GX0) / GD), nz = (int) ((GZ1 - GZ0) / GD);
        float[] v = new float[nx * nz * 6 * Mesh.STRIDE];
        int k = 0;
        double[][] h = new double[nx + 1][nz + 1];
        for (int i = 0; i <= nx; i++)
            for (int j = 0; j <= nz; j++) {
                double x = GX0 + i * GD, z = GZ0 + j * GD, g = w.bed(x, z) + (wasser ? 0 : 0.8 * Math.sin(x / 13 + z / 9) * smooth(80, 120, x));
                h[i][j] = wasser ? w.height(x, z) : g;
            }
        for (int i = 0; i < nx; i++)
            for (int j = 0; j < nz; j++) {
                double x0 = GX0 + i * GD, z0 = GZ0 + j * GD;
                if (wasser) {
                    double d = 0;
                    for (int a = 0; a < 2; a++) for (int e = 0; e < 2; e++) d = Math.max(d, h[i + a][j + e] - w.bed(x0 + a * GD, z0 + e * GD));
                    if (d < 0.05) continue;
                }
                double[][] q = {{x0, h[i][j], z0}, {x0 + GD, h[i + 1][j], z0}, {x0 + GD, h[i + 1][j + 1], z0 + GD}, {x0, h[i][j + 1], z0 + GD}};
                double hm = 0.25 * (q[0][1] + q[1][1] + q[2][1] + q[3][1]);
                double[] col;
                if (wasser) {
                    double sch = smooth(1.5, 6, Math.abs(q[0][1] - q[2][1]) + Math.abs(q[1][1] - q[3][1]));   // Gischt an steiler Front
                    col = new double[]{0.03 + 0.6 * sch, 0.07 + 0.6 * sch, 0.08 + 0.6 * sch};
                } else {
                    double t = smooth(-1, 3, hm);
                    col = new double[]{0.30 * (1 - t) + 0.06 * t, 0.28 * (1 - t) + 0.12 * t, 0.24 * (1 - t) + 0.05 * t};
                }
                for (int[] tri : new int[][]{{0, 3, 2}, {0, 2, 1}}) {
                    double[] a = q[tri[0]], bb = q[tri[1]], cc = q[tri[2]];
                    double ux = bb[0] - a[0], uy = bb[1] - a[1], uz = bb[2] - a[2], vx = cc[0] - a[0], vy = cc[1] - a[1], vz = cc[2] - a[2];
                    double nx2 = uy * vz - uz * vy, ny2 = uz * vx - ux * vz, nz2 = ux * vy - uy * vx, l = Math.sqrt(nx2 * nx2 + ny2 * ny2 + nz2 * nz2) + 1e-9;
                    for (double[] pnt : new double[][]{a, bb, cc}) {
                        v[k++] = (float) pnt[0]; v[k++] = (float) pnt[1]; v[k++] = (float) pnt[2];
                        v[k++] = (float) (nx2 / l); v[k++] = (float) Math.abs(ny2 / l); v[k++] = (float) (nz2 / l);
                        v[k++] = (float) col[0]; v[k++] = (float) col[1]; v[k++] = (float) col[2];
                    }
                }
            }
        return java.util.Arrays.copyOf(v, k);
    }

    // ------------------------------------------------------------------ GL

    static float[] perspective(double fovy, double aspect, double near, double far) {
        double f = 1 / Math.tan(Math.toRadians(fovy) / 2);
        return new float[]{(float) (f / aspect), 0, 0, 0, 0, (float) f, 0, 0, 0, 0, (float) ((far + near) / (near - far)), -1, 0, 0, (float) (2 * far * near / (near - far)), 0};
    }

    static float[] lookAt(double[] e, double[] t) {
        double fx = t[0] - e[0], fy = t[1] - e[1], fz = t[2] - e[2], fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double sx = -fz, sy = 0, sz = fx, sl = Math.sqrt(sx * sx + sz * sz);
        sx /= sl; sz /= sl;
        double ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
        return new float[]{(float) sx, (float) ux, (float) -fx, 0, (float) sy, (float) uy, (float) -fy, 0, (float) sz, (float) uz, (float) -fz, 0,
                (float) -(sx * e[0] + sy * e[1] + sz * e[2]), (float) -(ux * e[0] + uy * e[1] + uz * e[2]), (float) (fx * e[0] + fy * e[1] + fz * e[2]), 1};
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) { float s = 0; for (int k = 0; k < 4; k++) s += a[k * 4 + i] * b[j * 4 + k]; r[j * 4 + i] = s; }
        return r;
    }

    VesselGl.View kamera(int bl, int wd, int ht) {
        double[] p = b.position();
        double[] e, t;
        switch (bl) {
            case 1 -> { e = new double[]{-180, 60, 140}; t = new double[]{20, 5, 0}; }
            case 2 -> { e = new double[]{p[0] + 35, p[1] + 9, p[2] + 28}; t = new double[]{p[0], p[1] + 1, p[2]}; }
            default -> { e = new double[]{p[0] - 25, Math.max(p[1], 0) + 12, 110}; t = new double[]{p[0] - 25, Math.max(p[1], 0) + 4, 0}; }
        }
        VesselGl.View v = new VesselGl.View();
        v.eye = e;
        v.viewProj = mul(perspective(50, wd / (double) ht, 0.5, 5000), lookAt(e, t));
        v.tonemap = true;
        v.exposure = 1.0;
        return v;
    }

    static VesselGl.Light licht() {
        VesselGl.Light l = new VesselGl.Light();
        l.sunDir = new double[]{-0.5, 0.25, -0.6};      // tief im Nordwesten, ein Juliabend [G]
        l.sunColor = new double[]{2.4, 1.9, 1.4};
        l.skyColor = new double[]{0.30, 0.36, 0.46};
        l.fogColor = new double[]{0.55, 0.58, 0.62};
        l.fogDistance = 3000;
        return l;
    }

    void zeichnen(GL4 gl, int wd, int ht) {
        gl.glViewport(0, 0, wd, ht);
        gl.glClearColor(0.45f, 0.52f, 0.60f, 1);
        gl.glClearDepth(1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        VesselGl.View v = kamera(blick, wd, ht);
        VesselGl.Light l = licht();
        if (!vg.has("grund")) vg.upload(gl, "grund", gitter(false));
        vg.upload(gl, "wasser", gitter(true));
        vg.draw(gl, "grund", null, v, l);
        vg.draw(gl, b, v, l);
        gl.glLineWidth(2);
        vg.draw(gl, c, v, l);
        vg.draw(gl, "wasser", null, v, l, 0.82f, 0.7f);
    }

    void ziel(GL4 gl, int wd, int ht) {
        if (fbo != 0 && wd == fboW && ht == fboH) { gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo); return; }
        int[] t = new int[2];
        gl.glGenTextures(2, t, 0);
        fboColor = t[0];
        fboDepth = t[1];
        gl.glBindTexture(GL.GL_TEXTURE_2D, fboColor);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, wd, ht, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, null);
        gl.glBindTexture(GL.GL_TEXTURE_2D, fboDepth);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_DEPTH_COMPONENT32F, wd, ht, 0, GL4.GL_DEPTH_COMPONENT, GL.GL_FLOAT, null);
        int[] f = new int[1];
        gl.glGenFramebuffers(1, f, 0);
        fbo = f[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, fboColor, 0);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, fboDepth, 0);
        fboW = wd;
        fboH = ht;
    }

    static BufferedImage lesen(GL4 gl, int wd, int ht) {
        ByteBuffer bb = ByteBuffer.allocateDirect(wd * ht * 4);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, wd, ht, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage img = new BufferedImage(wd, ht, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < ht; y++)
            for (int x = 0; x < wd; x++) {
                int i = ((ht - 1 - y) * wd + x) * 4;
                img.setRGB(x, y, ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255));
            }
        return img;
    }

    /** Der Bogen: vier Augenblicke (je wd × ht) und die Kurven; als PNG. */
    public void bogen(GL4 gl, File png, int wd, int ht) throws Exception {
        vg.init(gl);
        anfangen();
        double tf = w.ankunft(0);
        double[] zeiten = {tf - 12, tf - 2.5, tf + 4, tf + 22};
        String[] namen = {"vor Anker, die Front kommt", "die Kette spannt sich", "auf der Welle, Kette gerissen", "Rückstrom in die Bucht"};
        int[] blicke = {1, 0, 0, 1};
        BufferedImage out = new BufferedImage(2 * wd, 2 * ht + 320, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double dt = 1 / 30.0;
        long t0 = System.nanoTime();
        for (int k = 0; k < 4; k++) {
            while (w.t < zeiten[k]) schritt(dt);
            blick = blicke[k];
            ziel(gl, wd, ht);
            zeichnen(gl, wd, ht);
            BufferedImage im = lesen(gl, wd, ht);
            g.drawImage(im, (k % 2) * wd, (k / 2) * ht, null);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            g.setColor(Color.WHITE);
            double[] p = b.position();
            g.drawString(String.format(Locale.GERMAN, "t = %.1f s · %s · Hub %.1f m, Trimm %.0f°, Kette %.1f kN%s", w.t, namen[k], p[1], b.pitch(), c.fairleadTension() / 1000,
                    c.broken() ? " (gerissen)" : ""), (k % 2) * wd + 10, (k / 2) * ht + 22);
        }
        while (w.t < tf + 60) schritt(dt);
        double rechen = (System.nanoTime() - t0) / 1e9;
        kurven(g, 0, 2 * ht, 2 * wd, 320);
        g.dispose();
        png.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(out, "png", png);
        System.out.println("Bild: " + png);
        System.out.println(b);
        System.out.println(c);
        System.out.printf(Locale.GERMAN, "%.0f s Welle in %.1f s gerechnet und gezeichnet%n", w.t, rechen);
    }

    void kurven(Graphics2D g, int x0, int y0, int wd, int ht) {
        g.setColor(new Color(18, 20, 24));
        g.fillRect(x0, y0, wd, ht);
        double t0 = kurve.get(0)[0], t1 = kurve.get(kurve.size() - 1)[0];
        int l = x0 + 60, r = x0 + wd - 60, top = y0 + 48, bot = y0 + ht - 40;
        double yMax = 0;
        for (double[] k : kurve) yMax = Math.max(yMax, Math.max(k[1], k[2]));
        yMax = Math.ceil((yMax + 2) / 5) * 5;
        double tMax = Math.ceil(c.breakingLoad / 1000 / 10 + 1) * 10;
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(new Color(70, 70, 80));
        g.drawLine(l, bot, r, bot);
        g.drawLine(l, top, l, bot);
        g.drawLine(r, top, r, bot);
        for (int s = 0; s <= (int) (t1 - t0); s += 10) {
            int x = (int) (l + (r - l) * s / (t1 - t0));
            g.setColor(new Color(50, 50, 60));
            g.drawLine(x, top, x, bot);
            g.setColor(Color.LIGHT_GRAY);
            g.drawString(String.format(Locale.GERMAN, "%.0f s", t0 + s), x - 10, bot + 15);
        }
        g.drawString(String.format(Locale.GERMAN, "Hub (m, 0 … %.0f)", yMax), x0 + 4, top - 10);
        g.drawString(String.format(Locale.GERMAN, "Zug (kN, 0 … %.0f)", tMax), r - 90, top - 10);
        // Bruchlast
        int yb = (int) (bot - (bot - top) * c.breakingLoad / 1000 / tMax);
        g.setColor(new Color(160, 60, 60));
        g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{6, 4}, 0));
        g.drawLine(l, yb, r, yb);
        g.drawString(String.format(Locale.GERMAN, "Bruchlast %.0f kN", c.breakingLoad / 1000), r - 120, yb - 4);
        g.setStroke(new BasicStroke(2));
        String[] name = {"Boot (Schwerpunkt)", "Wasser am Boot", "Trimm (°, ±45)", "Zug an der Klüse"};
        Color[] col = {new Color(240, 220, 120), new Color(90, 170, 230), new Color(170, 170, 170), new Color(230, 90, 80)};
        for (int s = 0; s < 4; s++) {
            g.setColor(col[s]);
            int px = -1, py = -1;
            for (double[] k : kurve) {
                int x = (int) (l + (r - l) * (k[0] - t0) / (t1 - t0));
                double val = s == 0 ? k[1] / yMax : s == 1 ? k[2] / yMax : s == 2 ? 0.5 + k[3] / 90 : k[4] / tMax;
                int y = (int) (bot - (bot - top) * Math.max(0, Math.min(1, val)));
                if (px >= 0) g.drawLine(px, py, x, y);
                px = x;
                py = y;
            }
            g.fillRect(l + 10 + s * 190, bot + 22, 14, 4);
            g.drawString(name[s], l + 28 + s * 190, bot + 28);
        }
        g.setColor(Color.WHITE);
        g.drawString(String.format(Locale.GERMAN, "%s · %s", b, c), x0 + 8, y0 + 16);
    }

    // ------------------------------------------------------------------ Fenster

    @Override public void init(GLAutoDrawable d) { vg.init(d.getGL().getGL4()); }

    long last;

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        long now = System.nanoTime();
        double dt = last == 0 ? 0 : Math.min(0.1, (now - last) / 1e9);
        last = now;
        if (neu) { neu = false; anfangen(); }
        if (!halt) {
            double rest = dt * tempo;
            while (rest > 1e-6) { double h = Math.min(1 / 30.0, rest); schritt(h); rest -= h; }
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        zeichnen(gl, d.getSurfaceWidth(), d.getSurfaceHeight());
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int wd, int ht) { }

    @Override public void dispose(GLAutoDrawable d) { vg.dispose(d.getGL().getGL4()); }

    public static void main(String[] a) throws Exception {
        VesselDemo v = new VesselDemo();
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setDepthBits(24);
        caps.setSampleBuffers(true);
        caps.setNumSamples(4);
        if (a.length > 0 && a[0].equals("--bild")) {
            caps.setOnscreen(false);
            caps.setFBO(true);
            GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, 64, 64);
            d.display();
            d.getContext().makeCurrent();
            v.bogen(d.getGL().getGL4(), new File(a.length > 1 ? a[1] : "Vessel_Demo.png"), 800, 450);
            d.getContext().release();
            d.destroy();
            System.exit(0);
        }
        javax.swing.SwingUtilities.invokeLater(() -> {
            com.jogamp.opengl.awt.GLCanvas cv = new com.jogamp.opengl.awt.GLCanvas(caps);
            cv.addGLEventListener(v);
            javax.swing.JFrame f = new javax.swing.JFrame("com.dan.vessel – die Edrie vor Anker in der Welle (Leertaste von vorn, 1/2/3 Blick, +/− Tempo, P anhalten)");
            f.add(cv);
            f.setSize(1280, 760);
            f.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
            cv.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override public void keyPressed(java.awt.event.KeyEvent e) {
                    switch (e.getKeyChar()) {
                        case ' ' -> v.neu = true;
                        case '1' -> v.blick = 0;
                        case '2' -> v.blick = 1;
                        case '3' -> v.blick = 2;
                        case '+' -> v.tempo = Math.min(8, v.tempo * 2);
                        case '-' -> v.tempo = Math.max(0.125, v.tempo / 2);
                        case 'p', 'P' -> v.halt = !v.halt;
                        default -> { }
                    }
                }
            });
            f.setVisible(true);
            cv.requestFocusInWindow();
            new com.jogamp.opengl.util.FPSAnimator(cv, 60).start();
        });
    }
}
