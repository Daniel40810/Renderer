package com.dan.shutter.gl;

import com.dan.shutter.Shutter;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Die Kino-Stufe auf der Grafikkarte (GL 4.3): Teilbilder sammeln ({@code accumulate.frag}, additiv in eine Summe RGBA32F,
 * je Bildpunkt mit dem Zeitfenster der Umlaufblende und dem Katzenauge gewichtet) und teilen ({@code resolve.frag}). Die
 * App rechnet je Teilbild die Szene zur Zeit des Teilbilds, mit der Kamera am Ort der Pupille und dem Versatz im Bildpunkt
 * ({@link com.dan.shutter.SubFrames}), und gibt das HDR-Bild hierher. Das Ergebnis hat Mip-Stufen. Danach: Mischen aus,
 * Framebuffer und Viewport verändert.
 */
public final class ShutterGl {

    /** Ein Teilbild. */
    public static final class View {
        public int hdrTexture, width = 1, height = 1;
        /** Zeit des Teilbilds (s, Uhr von {@link Shutter#openAt}). */
        public double tauS;
        /** Ort in der Pupille (Fläche π, y mit der Stauchung) und die Stauchung. */
        public double pupilU, pupilV, squeeze = 1;
        /** Katzenauge (ρ1, σ1, ρ2, σ2) im Maß der Pupille; null: aus. */
        public double[] catEye;
        /** tan Feldwinkel je Bildpunkt von der Bildmitte (Sensor mm je Bildpunkt / Brennweite, mal Stauchung des Felds). */
        public double tanPerPx;
        /** Blendenzahl (für die Flanke der Umlaufblende). */
        public double fNumber = 2.8;
    }

    private final Shutter shutter;
    private boolean ready;
    private int progAcc, progRes, vao, fbo, sumTex, outTex, w, h, count;
    private final Map<Integer, Map<String, Integer>> locs = new HashMap<>();

    public ShutterGl(Shutter shutter) { this.shutter = shutter; }

    public boolean ready() { return ready; }

    /** Teilbilder in der Summe. */
    public int count() { return count; }

    public void init(GL4 gl) {
        String vs = read("fullscreen.vert");
        progAcc = program(gl, vs, read("accumulate.frag"));
        progRes = program(gl, vs, read("resolve.frag"));
        int[] a = new int[1];
        gl.glGenVertexArrays(1, a, 0);
        vao = a[0];
        gl.glGenFramebuffers(1, a, 0);
        fbo = a[0];
        ready = true;
    }

    /** Eine neue Summe der Größe width × height (leer). */
    public void begin(GL4 gl, int width, int height) {
        if (width != w || height != h || sumTex == 0) {
            if (sumTex != 0) gl.glDeleteTextures(2, new int[]{sumTex, outTex}, 0);
            sumTex = tex(gl, GL4.GL_RGBA32F, width, height);
            outTex = texMip(gl, width, height);
            w = width;
            h = height;
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, sumTex, 0);
        gl.glViewport(0, 0, w, h);
        gl.glClearColor(0, 0, 0, 0);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        count = 0;
    }

    /** Ein Teilbild in die Summe. */
    public void add(GL4 gl, View v) {
        if (!ready) return;
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, sumTex, 0);
        gl.glViewport(0, 0, w, h);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
        gl.glBlendEquation(GL.GL_FUNC_ADD);
        gl.glBindVertexArray(vao);
        int p = progAcc;
        gl.glUseProgram(p);
        bind(gl, p, "uHdr", 0, v.hdrTexture);
        Shutter s = shutter;
        setf(gl, p, "uTau", (float) v.tauS);
        setf(gl, p, "uOpen", (float) s.openS());
        seti(gl, p, "uRolling", s.rolling ? 1 : 0);
        set2(gl, p, "uGate", (float) s.gateWidthMm, (float) s.gateHeightMm);
        setf(gl, p, "uGateR", (float) s.gateRadiusMm);
        setf(gl, p, "uSweep", (float) Math.toRadians(s.sweepDeg));
        setf(gl, p, "uFps", (float) s.fps);
        setf(gl, p, "uRamp", (float) s.rampS(v.fNumber));
        set2(gl, p, "uPupil", (float) v.pupilU, (float) v.pupilV);
        setf(gl, p, "uSqueeze", (float) Math.max(1e-3, v.squeeze));
        double[] c = v.catEye;
        int l = loc(gl, p, "uCat");
        if (l >= 0) {
            if (c == null) gl.glUniform4f(l, 99, 0, 99, 0);
            else gl.glUniform4f(l, (float) c[0], (float) c[1], (float) c[2], (float) c[3]);
        }
        setf(gl, p, "uTanPerPx", (float) v.tanPerPx);
        set2(gl, p, "uSize", w, h);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(0);
        gl.glBindVertexArray(0);
        count++;
    }

    /** Die Summe geteilt (RGBA16F); wo kein Teilbild zählte, lastTexture. Gibt die Textur zurück. */
    public int resolve(GL4 gl, int lastTexture) {
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, outTex, 0);
        gl.glViewport(0, 0, w, h);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBindVertexArray(vao);
        gl.glUseProgram(progRes);
        bind(gl, progRes, "uSum", 0, sumTex);
        bind(gl, progRes, "uLast", 1, lastTexture);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glUseProgram(0);
        gl.glBindVertexArray(0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, outTex);   // Mip-Stufen wie das Bild der Kantenglättung (für Glühen und Messungen)
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return outTex;
    }

    public int resultTexture() { return outTex; }

    public void dispose(GL4 gl) {
        if (!ready) return;
        gl.glDeleteProgram(progAcc);
        gl.glDeleteProgram(progRes);
        if (sumTex != 0) gl.glDeleteTextures(2, new int[]{sumTex, outTex}, 0);
        gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        locs.clear();
        sumTex = outTex = 0;
        w = h = 0;
        ready = false;
    }

    static int texMip(GL4 gl, int w, int h) {
        int levels = 1 + (int) Math.floor(Math.log(Math.max(w, h)) / Math.log(2));
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, levels, GL4.GL_RGBA16F, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    static int tex(GL4 gl, int fmt, int w, int h) {
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, fmt, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    private int loc(GL4 gl, int p, String n) { return locs.computeIfAbsent(p, k -> new HashMap<>()).computeIfAbsent(n, k -> gl.glGetUniformLocation(p, k)); }

    private void seti(GL4 gl, int p, String n, int v) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform1i(l, v); }

    private void setf(GL4 gl, int p, String n, float v) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform1f(l, v); }

    private void set2(GL4 gl, int p, String n, float a, float b) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform2f(l, a, b); }

    private void bind(GL4 gl, int p, String n, int unit, int t) {
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t);
        seti(gl, p, n, unit);
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    static String read(String file) {
        try (InputStream in = ShutterGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("Shader " + file + " fehlt (src mit auf den Klassenpfad?)");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Shader " + file + ": " + e.getMessage(), e);
        }
    }

    static int program(GL4 gl, String vs, String fs) {
        int p = gl.glCreateProgram();
        for (int k = 0; k < 2; k++) {
            int s = gl.glCreateShader(k == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(s, 1, new String[]{"#version 430 core\n" + (k == 0 ? vs : fs)}, null, 0);
            gl.glCompileShader(s);
            int[] ok = new int[1];
            gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] log = new byte[8192];
                gl.glGetShaderInfoLog(s, log.length, null, 0, log, 0);
                throw new IllegalStateException("com.dan.shutter: " + new String(log, StandardCharsets.UTF_8).trim());
            }
            gl.glAttachShader(p, s);
            gl.glDeleteShader(s);
        }
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] log = new byte[8192];
            gl.glGetProgramInfoLog(p, log.length, null, 0, log, 0);
            throw new IllegalStateException("com.dan.shutter: " + new String(log, StandardCharsets.UTF_8).trim());
        }
        return p;
    }
}
