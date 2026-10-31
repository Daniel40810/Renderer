package com.dan.beacon.demo;

import com.dan.beacon.Beacon;
import com.dan.beacon.Scatter;
import com.dan.beacon.ScatterGrid;
import com.dan.beacon.gl.BeaconGl;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Szene der Vorführung: Nacht über der See, ein Turm mit Laterne und Linse, das Feuer aus {@code com.dan.beacon}.
 * Regen als gleichmäßige Streudichte (Auslöschung 0,25 · R^0,63 je km), Dunst aus der Sicht. Außen kreist die Kamera um
 * einen Zielpunkt, innen steht sie im Laternenraum. Ohne Dreiecke: die Szene ist ein Bilddurchgang, der Turm, See und
 * Raum als Körper schneidet (scene.frag).
 */
public final class BeaconScene implements GLEventListener {
    public final Beacon beacon = Beacon.flash3();
    public final ScatterGrid grid;
    public final BeaconGl gl = new BeaconGl();
    /** Regen in mm/h, Sicht ohne Regen in km. */
    public volatile double rain = 0, visibilityKm = 25;
    public volatile double yaw = 200, pitch = 4, dist = 420, targetY = 18;
    /** Kamera im Laternenraum (Blick auf die Linse). */
    public volatile boolean inside;
    public volatile double insideAz = 200;
    public volatile double pre = 60, moonLux = 0.03, skyL = 0.0012, timeScale = 1;
    public volatile boolean frozen, glare = true, beam = true;
    public volatile double strength = 1;
    public volatile String check = "";
    private int sceneProg, postProg, vao, hdrTex, depthTex, fbo, copyTex, copyFbo, w, h;
    private long last;
    private double t;
    private int gridRain = -1;

    public BeaconScene() {
        Beacon.Quality q = Beacon.Quality.HIGH;
        grid = new ScatterGrid(q.gridAz, q.gridEl, q.gridR);
        beacon.quality = q;
        beacon.y = 24.335;
        beacon.automatic = false;
        beacon.snapOn(true);
    }

    public static double rainSigma(double mmh) { return mmh <= 0 ? 0 : 0.25 * Math.pow(mmh, 0.63) / 1000; }

    /** Szenenzeit um dt weiter (Standbilder). */
    public void advance(double dt) {
        t += dt;
        beacon.step(dt);
    }

    public double[] eye() {
        if (inside) {
            double a = Math.toRadians(insideAz);
            return new double[]{Math.sin(a) * 1.35, 24.6, -Math.cos(a) * 1.35};
        }
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        return new double[]{Math.sin(yr) * Math.cos(pr) * dist, targetY + Math.sin(pr) * dist, -Math.cos(yr) * Math.cos(pr) * dist};
    }

    @Override
    public void init(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        String fs = read("/com/dan/beacon/gl/fullscreen.vert");
        sceneProg = BeaconGl.program(g, fs, BeaconGl.includeSource() + read("scene.frag"));
        postProg = BeaconGl.program(g, fs, read("post.frag"));
        int[] v = new int[1];
        g.glGenVertexArrays(1, v, 0);
        vao = v[0];
        gl.init(g);
        last = System.nanoTime();
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int ww, int hh) { }

    private void targets(GL4 g, int ww, int hh) {
        if (ww == w && hh == h) return;
        if (fbo != 0) {
            g.glDeleteFramebuffers(2, new int[]{fbo, copyFbo}, 0);
            g.glDeleteTextures(3, new int[]{hdrTex, depthTex, copyTex}, 0);
        }
        int[] t3 = new int[3], f2 = new int[2];
        g.glGenTextures(3, t3, 0);
        g.glGenFramebuffers(2, f2, 0);
        hdrTex = t3[0];
        depthTex = t3[1];
        copyTex = t3[2];
        fbo = f2[0];
        copyFbo = f2[1];
        g.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        g.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, ww, hh);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        for (int tx : new int[]{depthTex, copyTex}) {
            g.glBindTexture(GL.GL_TEXTURE_2D, tx);
            g.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_DEPTH_COMPONENT32F, ww, hh);
            g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
            g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        }
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        g.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, hdrTex, 0);
        g.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, depthTex, 0);
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, copyFbo);
        g.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, copyTex, 0);
        g.glBindTexture(GL.GL_TEXTURE_2D, 0);
        w = ww;
        h = hh;
    }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9);
        last = now;
        if (!frozen) advance(dt * timeScale);
        int ww = d.getSurfaceWidth(), hh = d.getSurfaceHeight();
        targets(g, ww, hh);
        double sigma = rainSigma(rain);
        beacon.hazePerM = 2.996 / (visibilityKm * 1000);
        // Regen: Streugitter neu, wenn sich die Menge ändert
        int key = (int) Math.round(rain * 100);
        if (key != gridRain) {
            grid.fill(Scatter.uniform(sigma), beacon.x, beacon.y, beacon.z);
            gridRain = key;
        }
        Beacon.Snapshot s = beacon.snapshot();
        gl.update(g, s, grid);
        double[] e = eye();
        double[] target = inside ? new double[]{0, 24.4, 0} : new double[]{0, targetY, 0};
        double fovy = inside ? 70 : 38;
        float[] proj = perspective(fovy, ww / (double) hh, 0.1, 60_000);
        float[] view = lookAt(e, target);
        float[] vp = mul(proj, view), inv = invert(vp);
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        g.glViewport(0, 0, ww, hh);
        g.glEnable(GL.GL_DEPTH_TEST);
        g.glDepthFunc(GL.GL_ALWAYS);
        g.glDisable(GL.GL_BLEND);
        g.glUseProgram(sceneProg);
        gl.bind(g, sceneProg, 4, pre);
        g.glUniformMatrix4fv(g.glGetUniformLocation(sceneProg, "uInvVP"), 1, false, inv, 0);
        g.glUniformMatrix4fv(g.glGetUniformLocation(sceneProg, "uVP"), 1, false, vp, 0);
        g.glUniform3f(g.glGetUniformLocation(sceneProg, "uCam"), (float) e[0], (float) e[1], (float) e[2]);
        double ma = Math.toRadians(35), mz = Math.toRadians(150);
        g.glUniform3f(g.glGetUniformLocation(sceneProg, "uMoonDir"), (float) (Math.sin(mz) * Math.cos(ma)), (float) Math.sin(ma), (float) (-Math.cos(mz) * Math.cos(ma)));
        double dim = 1 / (1 + rain * 0.5);
        g.glUniform1f(g.glGetUniformLocation(sceneProg, "uMoon"), (float) (moonLux * pre * dim));
        g.glUniform1f(g.glGetUniformLocation(sceneProg, "uSkyL"), (float) (skyL * pre * (rain > 0 ? 0.6 : 1)));
        g.glUniform1f(g.glGetUniformLocation(sceneProg, "uTime"), (float) t);
        g.glUniform1f(g.glGetUniformLocation(sceneProg, "uWet"), rain > 0 ? 1f : 0f);
        g.glUniform1f(g.glGetUniformLocation(sceneProg, "uSigma"), (float) (sigma + beacon.hazePerM));
        g.glBindVertexArray(vao);
        g.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        g.glDepthFunc(GL.GL_LESS);
        // Tiefe kopieren, dann Strahl und Lichtpunkte
        g.glBindFramebuffer(GL2ES3.GL_READ_FRAMEBUFFER, fbo);
        g.glBindFramebuffer(GL2ES3.GL_DRAW_FRAMEBUFFER, copyFbo);
        g.glBlitFramebuffer(0, 0, ww, hh, 0, 0, ww, hh, GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
        BeaconGl.View v = new BeaconGl.View();
        v.viewProj = vp;
        v.invViewProj = inv;
        v.eye = e;
        v.reversedZ = false;
        v.width = ww;
        v.height = hh;
        v.fovyDeg = fovy;
        v.depthTexture = copyTex;
        v.targetFbo = fbo;
        v.preExposure = pre;
        v.strength = strength;
        v.jitter = !frozen;
        if (beam && !inside) gl.drawBeam(g, v);
        if (glare && !inside) {
            List<BeaconGl.Glare> l = new ArrayList<>();
            double[] lux = s.illuminanceAt(e[0], e[1], e[2], sigma + beacon.hazePerM);
            double dl = Math.sqrt(e[0] * e[0] + (e[1] - s.y()) * (e[1] - s.y()) + e[2] * e[2]);
            // ab etwa zwei Bildpunkten Linsengröße zeigt die Fläche selbst das Licht, der Punkt behält nur den Hof
            double px = 2 * Math.tan(Math.toRadians(fovy) / 2) / hh * dl;
            l.add(new BeaconGl.Glare(s.x(), s.y(), s.z(), lux, Math.max(0, Math.min(1, (2 * px - 0.9) / 0.9)), 2.5));
            gl.drawGlare(g, v, l);
        }
        // ins Fenster
        g.glDisable(GL.GL_DEPTH_TEST);
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        g.glViewport(0, 0, ww, hh);
        g.glUseProgram(postProg);
        g.glActiveTexture(GL.GL_TEXTURE0);
        g.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        g.glUniform1i(g.glGetUniformLocation(postProg, "uHdr"), 0);
        g.glBindVertexArray(vao);
        g.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        g.glUseProgram(0);
    }

    /** Selbsttest der Grafikkarte (im GL-Faden). */
    public String check(GLAutoDrawable d) {
        check = gl.selfCheck(d.getGL().getGL4(), beacon.snapshot(), grid);
        return check;
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.dispose(g);
        g.glDeleteProgram(sceneProg);
        g.glDeleteProgram(postProg);
    }

    // ------------------------------------------------------------------ Matrizen (spaltenweise, wie OpenGL)

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

    static float[] lookAt(double[] e, double[] c) {
        double fx = c[0] - e[0], fy = c[1] - e[1], fz = c[2] - e[2], fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double sx = fy * 0 - fz * 1, sy = fz * 0 - fx * 0, sz = fx * 1 - fy * 0, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= sl; sy /= sl; sz /= sl;
        double ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
        return new float[]{(float) sx, (float) ux, (float) -fx, 0, (float) sy, (float) uy, (float) -fy, 0, (float) sz, (float) uz, (float) -fz, 0,
                (float) -(sx * e[0] + sy * e[1] + sz * e[2]), (float) -(ux * e[0] + uy * e[1] + uz * e[2]), (float) (fx * e[0] + fy * e[1] + fz * e[2]), 1};
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + rr] * b[c * 4 + k];
                r[c * 4 + rr] = (float) s;
            }
        return r;
    }

    static float[] invert(float[] m) {
        double[] a = new double[16];
        for (int i = 0; i < 16; i++) a[i] = m[i];
        double[] inv = new double[16];
        inv[0] = a[5] * a[10] * a[15] - a[5] * a[11] * a[14] - a[9] * a[6] * a[15] + a[9] * a[7] * a[14] + a[13] * a[6] * a[11] - a[13] * a[7] * a[10];
        inv[4] = -a[4] * a[10] * a[15] + a[4] * a[11] * a[14] + a[8] * a[6] * a[15] - a[8] * a[7] * a[14] - a[12] * a[6] * a[11] + a[12] * a[7] * a[10];
        inv[8] = a[4] * a[9] * a[15] - a[4] * a[11] * a[13] - a[8] * a[5] * a[15] + a[8] * a[7] * a[13] + a[12] * a[5] * a[11] - a[12] * a[7] * a[9];
        inv[12] = -a[4] * a[9] * a[14] + a[4] * a[10] * a[13] + a[8] * a[5] * a[14] - a[8] * a[6] * a[13] - a[12] * a[5] * a[10] + a[12] * a[6] * a[9];
        inv[1] = -a[1] * a[10] * a[15] + a[1] * a[11] * a[14] + a[9] * a[2] * a[15] - a[9] * a[3] * a[14] - a[13] * a[2] * a[11] + a[13] * a[3] * a[10];
        inv[5] = a[0] * a[10] * a[15] - a[0] * a[11] * a[14] - a[8] * a[2] * a[15] + a[8] * a[3] * a[14] + a[12] * a[2] * a[11] - a[12] * a[3] * a[10];
        inv[9] = -a[0] * a[9] * a[15] + a[0] * a[11] * a[13] + a[8] * a[1] * a[15] - a[8] * a[3] * a[13] - a[12] * a[1] * a[11] + a[12] * a[3] * a[9];
        inv[13] = a[0] * a[9] * a[14] - a[0] * a[10] * a[13] - a[8] * a[1] * a[14] + a[8] * a[2] * a[13] + a[12] * a[1] * a[10] - a[12] * a[2] * a[9];
        inv[2] = a[1] * a[6] * a[15] - a[1] * a[7] * a[14] - a[5] * a[2] * a[15] + a[5] * a[3] * a[14] + a[13] * a[2] * a[7] - a[13] * a[3] * a[6];
        inv[6] = -a[0] * a[6] * a[15] + a[0] * a[7] * a[14] + a[4] * a[2] * a[15] - a[4] * a[3] * a[14] - a[12] * a[2] * a[7] + a[12] * a[3] * a[6];
        inv[10] = a[0] * a[5] * a[15] - a[0] * a[7] * a[13] - a[4] * a[1] * a[15] + a[4] * a[3] * a[13] + a[12] * a[1] * a[7] - a[12] * a[3] * a[5];
        inv[14] = -a[0] * a[5] * a[14] + a[0] * a[6] * a[13] + a[4] * a[1] * a[14] - a[4] * a[2] * a[13] - a[12] * a[1] * a[6] + a[12] * a[2] * a[5];
        inv[3] = -a[1] * a[6] * a[11] + a[1] * a[7] * a[10] + a[5] * a[2] * a[11] - a[5] * a[3] * a[10] - a[9] * a[2] * a[7] + a[9] * a[3] * a[6];
        inv[7] = a[0] * a[6] * a[11] - a[0] * a[7] * a[10] - a[4] * a[2] * a[11] + a[4] * a[3] * a[10] + a[8] * a[2] * a[7] - a[8] * a[3] * a[6];
        inv[11] = -a[0] * a[5] * a[11] + a[0] * a[7] * a[9] + a[4] * a[1] * a[11] - a[4] * a[3] * a[9] - a[8] * a[1] * a[7] + a[8] * a[3] * a[5];
        inv[15] = a[0] * a[5] * a[10] - a[0] * a[6] * a[9] - a[4] * a[1] * a[10] + a[4] * a[2] * a[9] + a[8] * a[1] * a[6] - a[8] * a[2] * a[5];
        double det = a[0] * inv[0] + a[1] * inv[4] + a[2] * inv[8] + a[3] * inv[12];
        float[] r = new float[16];
        for (int i = 0; i < 16; i++) r[i] = (float) (inv[i] / det);
        return r;
    }

    static String read(String file) {
        String path = file.startsWith("/") ? file : file;
        try (InputStream in = BeaconScene.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("beacon demo: Shader fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** „Fl(3) W 15s · Drehlinse … · Regen 5 mm/h“ */
    public String status() {
        return String.format(Locale.GERMAN, "%s · Regen %s mm/h · Sicht %.0f km", beacon.describe(), rain < 1 ? String.format(Locale.GERMAN, "%.1f", rain) : String.format(Locale.GERMAN, "%.0f", rain), visibilityKm);
    }
}
