package com.dan.wetglass.demo;

import com.dan.wetglass.Wetglass;
import com.dan.wetglass.gl.WetglassGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Die Demo-Szene ohne Leuchtturm: eine Scheibe vor einem Bild von See, Himmel und einem fernen geringelten Turm. Das
 * Auge sitzt dicht hinter der Scheibe und sieht einen Ausschnitt; der Hintergrund wird je nach Fokus unscharf. Statt
 * der Scheibe zeigt sie auch dieselbe Simulation auf einem Zylinder: einem liegenden Handlauf oder einem stehenden
 * Pfosten (F10).
 */
final class WetglassScene implements GLEventListener {
    final Wetglass glass = new Wetglass();
    final WetglassGl wgl = new WetglassGl(), tgl = new WetglassGl();
    /** 0 Scheibe, 1 liegender Zylinder (Handlauf, 3 cm), 2 stehender Zylinder (Pfosten, 5 cm). */
    volatile int mode;
    final Wetglass tube = new Wetglass();
    final Wetglass.Cylinder rail = new Wetglass.Cylinder(0.03, true), post = new Wetglass.Cylinder(0.05, false);
    private int tubeMode = -1;
    /** Beschlag (m/s Kondensation) und Salz aus dem Sprühnebel (Bedeckung je s). */
    volatile double condensation, saltRate;
    volatile double rate = 5, wind = 8, facing = 1, tiltDeg = 90, half = 0.12, centerX = 0.17, centerY = 0.17, lapse = 1, sunAlt = 18, sunAz = 200, dark = 0.3;
    volatile boolean frozen, focusGlass = true;
    volatile String status = "", selfCheck = "";
    volatile double[] wipeAt;
    private int bgProg, paneProg, tubeProg, post2, vao, bgTex, bgFbo, colTex, colFbo, w, h;
    private long last;
    double gpuMs;
    private final int[] query = new int[4];
    private int frame;

    WetglassScene() { setup(); }

    /** Eingänge der Scheibe aus den Reglern. */
    void setup() {
        double t = Math.toRadians(tiltDeg);
        glass.slopeX = 0;
        glass.slopeY = -Math.sin(t);
        glass.windX = wind * 0.35 * Math.sin(t);
        glass.rain(rate, wind, facing);
        glass.condensation = condensation;
        glass.saltRate = saltRate;
        // Zylinder: abgewickelt, x um ihn herum, y entlang der Achse
        Wetglass.Cylinder c = mode == 2 ? post : rail;
        c.wind = wind;
        if (mode != tubeMode && mode > 0) {
            tube.surface = c;
            tube.width = c.circumference();
            tube.height = 0.2;
            tube.drainLength = mode == 2 ? 1.2 : 1e9;
            tube.quality = glass.quality;
            tube.reset();
            tubeMode = mode;
        }
        if (c.horizontal) tube.rainAcross(rate, wind);
        else tube.rain(rate, wind, 1);
        tube.saltRate = saltRate;
    }

    /** Die Simulation, die gerade zu sehen ist. */
    Wetglass active() { return mode == 0 ? glass : tube; }

    void advance(double sec) {
        setup();
        double dt = 1 / 30.0;
        for (double s = 0; s < sec; s += dt) active().step(dt);
    }

    @Override
    public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        bgProg = WetglassGl.program(gl, "#version 430 core\n" + read("post.vert").replace("void main() {", "out vec2 vUv;\nvoid main() {\n    vUv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);"),
                "#version 430 core\n" + read("bg.frag"));
        paneProg = WetglassGl.program(gl, "#version 430 core\n" + read("post.vert").replace("void main() {", "out vec2 vUv;\nvoid main() {\n    vUv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);"),
                "#version 430 core\n" + WetglassGl.includeSource() + "\n" + read("pane.frag").replace("#include \"wetglass.glsl\"", ""));
        post2 = WetglassGl.program(gl, "#version 430 core\n" + read("post.vert"), "#version 430 core\n" + read("post.frag"));
        tubeProg = WetglassGl.program(gl, "#version 430 core\n" + read("post.vert").replace("void main() {", "out vec2 vUv;\nvoid main() {\n    vUv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);"),
                "#version 430 core\n" + WetglassGl.includeSource() + "\n" + read("tube.frag").replace("#include \"wetglass.glsl\"", ""));
        wgl.init(gl);
        tgl.init(gl);
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        gl.glGenQueries(4, query, 0);
        last = System.nanoTime();
    }

    private void targets(GL4 gl, int W, int H) {
        if (W == w && H == h) return;
        if (bgTex != 0) {
            gl.glDeleteFramebuffers(2, new int[]{bgFbo, colFbo}, 0);
            gl.glDeleteTextures(2, new int[]{bgTex, colTex}, 0);
        }
        int[] t = new int[2];
        gl.glGenTextures(2, t, 0);
        bgTex = t[0];
        colTex = t[1];
        int levels = 1 + (int) Math.floor(Math.log(Math.max(W, H)) / Math.log(2));
        gl.glBindTexture(GL.GL_TEXTURE_2D, bgTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, levels, GL.GL_RGBA16F, W, H);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, colTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, W, H);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glGenFramebuffers(2, t, 0);
        bgFbo = t[0];
        colFbo = t[1];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, bgFbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, bgTex, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, colFbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, colTex, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        w = W;
        h = H;
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int width, int height) { }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        drawable = d;
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9);
        last = now;
        setup();
        double[] wp = wipeAt;
        if (wp != null) { wipeAt = null; glass.wipe(wp[0], wp[1], wp[2]); glass.wipeInside(wp[0], wp[1], wp[2]); }
        if (!frozen) active().step(dt * lapse);
        if (frame > 1) {
            int o = ((frame - 1) & 1) * 2;
            long[] r = new long[2];
            gl.glGetQueryObjecti64v(query[o], GL4.GL_QUERY_RESULT, r, 0);
            gl.glGetQueryObjecti64v(query[o + 1], GL4.GL_QUERY_RESULT, r, 1);
            gpuMs = gpuMs * 0.9 + Math.max(0, r[1] - r[0]) / 1e6 * 0.1;
        }
        int W = d.getSurfaceWidth(), H = d.getSurfaceHeight();
        targets(gl, W, H);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBindVertexArray(vao);
        // 1 Hintergrund
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, bgFbo);
        gl.glViewport(0, 0, W, H);
        gl.glUseProgram(bgProg);
        gl.glUniform1f(gl.glGetUniformLocation(bgProg, "uAspect"), W / (float) H);
        gl.glUniform1f(gl.glGetUniformLocation(bgProg, "uFovy"), 50f);
        gl.glUniform1f(gl.glGetUniformLocation(bgProg, "uTime"), (float) (glass.time() % 3600));
        gl.glUniform1f(gl.glGetUniformLocation(bgProg, "uSunAlt"), (float) sunAlt);
        gl.glUniform1f(gl.glGetUniformLocation(bgProg, "uSunAz"), (float) sunAz);
        gl.glUniform1f(gl.glGetUniformLocation(bgProg, "uDark"), (float) dark);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, bgTex);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        // 2 Karte der Scheibe
        gl.glQueryCounter(query[(frame & 1) * 2], GL4.GL_TIMESTAMP);
        if (mode > 0) { drawTube(gl, W, H); return; }
        wgl.update(gl, glass.snapshot());
        // 3 Scheibe vor dem Hintergrund
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, colFbo);
        gl.glViewport(0, 0, W, H);
        gl.glUseProgram(paneProg);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, bgTex);
        gl.glUniform1i(gl.glGetUniformLocation(paneProg, "uBg"), 0);
        wgl.bind(gl, paneProg, 1);
        gl.glUniform2f(gl.glGetUniformLocation(paneProg, "uViewport"), W, H);
        gl.glUniform2f(gl.glGetUniformLocation(paneProg, "uCenter"), (float) centerX, (float) centerY);
        gl.glUniform1f(gl.glGetUniformLocation(paneProg, "uHalf"), (float) half);
        gl.glUniform1f(gl.glGetUniformLocation(paneProg, "uFocusLod"), focusGlass ? (float) Math.max(0, 3.2 - half * 2) : 0f);
        gl.glUniform1f(gl.glGetUniformLocation(paneProg, "uLens"), 0.3f);
        double alt = Math.toRadians(sunAlt), az = Math.toRadians(sunAz - 180);
        gl.glUniform3f(gl.glGetUniformLocation(paneProg, "uSunDir"), (float) (Math.cos(alt) * Math.sin(az)), (float) Math.sin(alt), (float) (Math.cos(alt) * Math.cos(az)));
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        finish(d, gl, W, H);
        status = String.format(Locale.GERMAN, "Regen %.1f mm/h · Wind %.0f m/s · Neigung %.0f° · %s · %s · Scheibe %.2f ms", rate, wind, tiltDeg, glass.describe(), wgl.info(), gpuMs);
    }

    /** Stattdessen der Zylinder vor dem Hintergrund. */
    private void drawTube(GL4 gl, int W, int H) {
        tgl.update(gl, tube.snapshot());
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, colFbo);
        gl.glViewport(0, 0, W, H);
        gl.glUseProgram(tubeProg);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, bgTex);
        gl.glUniform1i(gl.glGetUniformLocation(tubeProg, "uBg"), 0);
        tgl.bind(gl, tubeProg, 1);
        Wetglass.Cylinder c = mode == 2 ? post : rail;
        gl.glUniform2f(gl.glGetUniformLocation(tubeProg, "uViewport"), W, H);
        gl.glUniform1f(gl.glGetUniformLocation(tubeProg, "uFovy"), (float) tubeFovy);
        gl.glUniform1f(gl.glGetUniformLocation(tubeProg, "uRadius"), (float) c.radius);
        gl.glUniform1f(gl.glGetUniformLocation(tubeProg, "uDist"), (float) (c.radius * tubeDist));
        gl.glUniform1f(gl.glGetUniformLocation(tubeProg, "uLift"), (float) (c.radius * tubeLift));
        gl.glUniform1i(gl.glGetUniformLocation(tubeProg, "uHorizontal"), c.horizontal ? 1 : 0);
        gl.glUniform1f(gl.glGetUniformLocation(tubeProg, "uAxial"), 0.05f);
        gl.glUniform3f(gl.glGetUniformLocation(tubeProg, "uPaint"), 0.10f, 0.16f, 0.15f);
        double alt = Math.toRadians(sunAlt), az = Math.toRadians(sunAz - 180);
        gl.glUniform3f(gl.glGetUniformLocation(tubeProg, "uSunDir"), (float) (Math.cos(alt) * Math.sin(az)), (float) Math.sin(alt), (float) (Math.cos(alt) * Math.cos(az)));
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        finish(null, gl, W, H);
        status = String.format(Locale.GERMAN, "%s %.0f cm · Regen %.1f mm/h · Wind %.0f m/s · %s · %s", c.horizontal ? "Handlauf" : "Pfosten", c.radius * 200, rate, wind, tube.describe(), tgl.info());
    }

    /** Bildwinkel und Lage des Zylinders: Abstand und Versatz der Achse in Halbmessern. */
    volatile double tubeFovy = 40, tubeDist = 4.2, tubeLift = 0.35;
    private GLAutoDrawable drawable;

    private void finish(GLAutoDrawable dd, GL4 gl, int W, int H) {
        GLAutoDrawable d = dd != null ? dd : drawable;
        gl.glQueryCounter(query[(frame & 1) * 2 + 1], GL4.GL_TIMESTAMP);
        frame++;
        // 4 ins Fenster
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        gl.glViewport(0, 0, W, H);
        gl.glUseProgram(post2);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, colTex);
        gl.glUniform1i(gl.glGetUniformLocation(post2, "uImg"), 0);
        gl.glUniform1f(gl.glGetUniformLocation(post2, "uExposure"), 1.4f);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    /** Bildpunkt (Fensterkoordinaten, y nach unten) → Ort auf der Scheibe in m. */
    double[] paneAt(int px, int py, int W, int H) {
        double nx = px / (double) W * 2 - 1, ny = 1 - py / (double) H * 2;
        return new double[]{centerX + nx * W / (double) H * half, centerY + ny * half};
    }

    String check(GLAutoDrawable d) {
        selfCheck = mode == 0 ? wgl.selfCheck(d.getGL().getGL4(), glass.snapshot()) : tgl.selfCheck(d.getGL().getGL4(), tube.snapshot());
        return selfCheck;
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        wgl.dispose(gl);
        gl.glDeleteProgram(bgProg);
        gl.glDeleteProgram(paneProg);
        gl.glDeleteProgram(post2);
        gl.glDeleteProgram(tubeProg);
        tgl.dispose(gl);
    }

    static String read(String f) {
        try (InputStream in = WetglassScene.class.getResourceAsStream(f)) {
            if (in == null) throw new IllegalStateException("wetglass demo: " + f + " fehlt");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
