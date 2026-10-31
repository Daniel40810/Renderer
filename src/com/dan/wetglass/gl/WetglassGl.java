package com.dan.wetglass.gl;

import com.dan.wetglass.Wetglass;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die nasse Fläche auf der Grafikkarte: aus den Tropfen eines {@link Wetglass.Snapshot} wird je Bild eine Karte
 * (RGBA16F mit Mipmaps): Höhe des Wassers in mm, nasser Film, Steigung. Jeder Tropfen ist ein Rechteck, der Shader
 * rechnet die Kappe genau wie {@link Wetglass.Snapshot#heightMm}; überlappen sich zwei, gilt der höhere (GL_MAX).
 * <p>
 * Das Paket zeichnet keine Scheibe. Ein fremder Shader bindet den Baustein {@code wetglass.glsl} ein
 * ({@link #includeSource()}), liest die Karte ({@link #bind}) und bricht damit das Bild dahinter. Für jede Fläche
 * braucht es ein eigenes {@code WetglassGl}.
 */
public final class WetglassGl {
    private int dropProg, normalProg, checkProg, vao, ssbo, capacity;
    private int hTex, hFbo, filmTex, mapTex, mapFbo, mapW, mapH, filmW, filmH;
    private double sizeW = 1, sizeH = 1;
    private int seed;
    private double sheetMm, time, flowX, flowY = -1;
    private boolean ready;
    private String info = "";

    public boolean ready() { return ready; }
    public String info() { return info; }
    /** Dicke der Wasserhaut beim letzten {@link #update} in mm. */
    public double sheetMm() { return sheetMm; }
    /** Die Karte (RGBA16F, Mipmaps): Höhe mm, Film, dh/dx, dh/dy. */
    public int mapTexture() { return mapTex; }
    public double sizeW() { return sizeW; }
    public double sizeH() { return sizeH; }

    public void init(GL4 gl) {
        dropProg = program(gl, "#version 430 core\n" + read("wg_drop.vert"), "#version 430 core\n" + read("wg_height.frag"));
        normalProg = program(gl, "#version 430 core\n" + read("fullscreen.vert"), "#version 430 core\n" + read("wg_normal.frag"));
        checkProg = program(gl, "#version 430 core\n" + read("fullscreen.vert"), "#version 430 core\n" + read("check.frag"));
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        gl.glGenBuffers(1, v, 0);
        ssbo = v[0];
        ready = true;
    }

    /** Der Baustein für fremde Shader (nach #version einsetzen). */
    public static String includeSource() { return read("wetglass.glsl"); }

    private void targets(GL4 gl, int w, int h, int fw, int fh) {
        if (w == mapW && h == mapH && fw == filmW && fh == filmH) return;
        if (mapTex != 0) {
            gl.glDeleteFramebuffers(2, new int[]{hFbo, mapFbo}, 0);
            gl.glDeleteTextures(3, new int[]{hTex, mapTex, filmTex}, 0);
        }
        int[] t = new int[3], f = new int[2];
        gl.glGenTextures(3, t, 0);
        gl.glGenFramebuffers(2, f, 0);
        hTex = t[0];
        mapTex = t[1];
        filmTex = t[2];
        hFbo = f[0];
        mapFbo = f[1];
        gl.glBindTexture(GL.GL_TEXTURE_2D, hTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_R16F, w, h);
        param(gl, GL.GL_NEAREST, GL.GL_NEAREST);
        int levels = 1 + (int) Math.floor(Math.log(Math.max(w, h)) / Math.log(2));
        gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, levels, GL.GL_RGBA16F, w, h);
        param(gl, GL.GL_LINEAR_MIPMAP_LINEAR, GL.GL_LINEAR);
        gl.glBindTexture(GL.GL_TEXTURE_2D, filmTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_RGBA32F, fw, fh);
        param(gl, GL.GL_LINEAR, GL.GL_LINEAR);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        attach(gl, hFbo, hTex);
        attach(gl, mapFbo, mapTex);
        mapW = w;
        mapH = h;
        filmW = fw;
        filmH = fh;
    }

    private static void param(GL4 gl, int min, int mag) {
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, min);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, mag);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    }

    private static void attach(GL4 gl, int fbo, int tex) {
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tex, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
    }

    /** Karte aus dem Zustand rechnen. Danach sind Rahmenpuffer, Ansicht und Mischen wie vorher. */
    public void update(GL4 gl, Wetglass.Snapshot s) {
        int w = s.quality().mapRes, h = Math.max(4, (int) Math.round(w * s.height() / s.width()));
        targets(gl, w, h, s.filmW(), s.filmH());
        int wrap = s.periodic() ? GL.GL_REPEAT : GL.GL_CLAMP_TO_EDGE;
        for (int t : new int[]{mapTex, filmTex}) {
            gl.glBindTexture(GL.GL_TEXTURE_2D, t);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, wrap);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, wrap);
        }
        sizeW = s.width();
        sizeH = s.height();
        int[] prev = new int[1], vp = new int[4], va = new int[1];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prev, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        gl.glGetIntegerv(GL4.GL_VERTEX_ARRAY_BINDING, va, 0);
        boolean depth = gl.glIsEnabled(GL.GL_DEPTH_TEST), blend = gl.glIsEnabled(GL.GL_BLEND), cull = gl.glIsEnabled(GL.GL_CULL_FACE);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_CULL_FACE);
        // Film, Feintropfen, Beschlag, Salz hochladen (RGBA)
        float[] ff = new float[s.film().length * 4];
        for (int i = 0; i < s.film().length; i++) {
            ff[i * 4] = s.film()[i];
            ff[i * 4 + 1] = s.fine()[i];
            ff[i * 4 + 2] = s.fog()[i];
            ff[i * 4 + 3] = s.salt()[i];
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, filmTex);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, s.filmW(), s.filmH(), GL.GL_RGBA, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(ff));
        seed = s.seed();
        sheetMm = s.sheetMm();
        time = s.time();
        flowX = s.flowX();
        flowY = s.flowY();
        // Tropfen hochladen
        int n = s.count();
        float[] d = new float[Math.max(1, n) * 8];
        System.arraycopy(s.drops(), 0, d, 0, n * 8);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, ssbo);
        if (d.length > capacity) {
            capacity = Math.max(d.length, 4096 * 8);
            gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, capacity * 4L, null, GL4.GL_DYNAMIC_DRAW);
        }
        gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, d.length * 4L, Buffers.newDirectFloatBuffer(d));
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        // 1 Höhe: Kappen, der höchste Tropfen gilt
        gl.glBindVertexArray(vao);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hFbo);
        gl.glViewport(0, 0, mapW, mapH);
        gl.glClearColor(0, 0, 0, 0);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        if (n > 0) {
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendEquation(GL4.GL_MAX);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
            gl.glUseProgram(dropProg);
            gl.glUniform2f(gl.glGetUniformLocation(dropProg, "uSize"), (float) s.width(), (float) s.height());
            gl.glUniform1i(gl.glGetUniformLocation(dropProg, "uPeriodic"), s.periodic() ? 1 : 0);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, ssbo);
            gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, n * 4);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, 0);
            gl.glBlendEquation(GL.GL_FUNC_ADD);
            gl.glDisable(GL.GL_BLEND);
        }
        // 2 Karte: Höhe, Film, Steigung; dann Mipmaps
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, mapFbo);
        gl.glUseProgram(normalProg);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hTex);
        gl.glUniform1i(gl.glGetUniformLocation(normalProg, "uHeight"), 0);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, filmTex);
        gl.glUniform1i(gl.glGetUniformLocation(normalProg, "uFilm"), 1);
        gl.glUniform2f(gl.glGetUniformLocation(normalProg, "uTexelMm"), (float) (s.width() / mapW * 1000), (float) (s.height() / mapH * 1000));
        gl.glUniform1i(gl.glGetUniformLocation(normalProg, "uPeriodic"), s.periodic() ? 1 : 0);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glUseProgram(0);
        gl.glBindVertexArray(va[0]);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        if (depth) gl.glEnable(GL.GL_DEPTH_TEST);
        if (blend) gl.glEnable(GL.GL_BLEND);
        if (cull) gl.glEnable(GL.GL_CULL_FACE);
        info = String.format(Locale.GERMAN, "Karte %d × %d (%.2f mm je Texel), %,d Tropfen", mapW, mapH, s.width() / mapW * 1000, n);
    }

    /**
     * Setzt die Uniforms des Bausteins im Programm prog: die Karte an die Einheit unit, Film, Feintropfen, Beschlag und
     * Salz an unit + 1, dazu die Wasserhaut einer Überspülung (Dicke, Zeit, Fließrichtung).
     */
    public void bind(GL4 gl, int prog, int unit) {
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uWgMap"), unit);
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit + 1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, filmTex);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uWgFilm"), unit + 1);
        gl.glUniform2f(gl.glGetUniformLocation(prog, "uWgSize"), (float) sizeW, (float) sizeH);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uWgSeed"), (float) (Math.floorMod(seed, 997) * 0.731));
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uWgSheet"), (float) sheetMm, (float) (time % 1000.0), (float) flowX, (float) flowY);
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    // ------------------------------------------------------------------ Selbsttest

    /**
     * Rechnet die Karte für s, liest 64 × 64 Texel zurück (gleichmäßig verteilt) und vergleicht die Höhe mit
     * {@link Wetglass.Snapshot#heightMm}. „Grafikkarte = Prozessor: Wasserhöhe ± 1 µm (Median), größte 9 µm, 4 096 Proben“
     */
    public String selfCheck(GL4 gl, Wetglass.Snapshot s) {
        if (s.count() == 0) return "";
        update(gl, s);
        int step = Math.max(1, Math.min(mapW, mapH) / 64);
        int[] v = new int[2];
        gl.glGenTextures(1, v, 0);
        int tex = v[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_RGBA32F, 64, 64);
        gl.glGenFramebuffers(1, v, 1);
        int fbo = v[1];
        int[] prev = new int[1], vp = new int[4], va = new int[1];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prev, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        gl.glGetIntegerv(GL4.GL_VERTEX_ARRAY_BINDING, va, 0);
        boolean blend = gl.glIsEnabled(GL.GL_BLEND), depth = gl.glIsEnabled(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tex, 0);
        gl.glViewport(0, 0, 64, 64);
        gl.glUseProgram(checkProg);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hTex);
        gl.glUniform1i(gl.glGetUniformLocation(checkProg, "uMap"), 0);
        gl.glUniform2i(gl.glGetUniformLocation(checkProg, "uOrigin"), step / 2, step / 2);
        gl.glUniform1i(gl.glGetUniformLocation(checkProg, "uStep"), step);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(64 * 64 * 4);
        gl.glReadPixels(0, 0, 64, 64, GL4.GL_RGBA, GL.GL_FLOAT, fb);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        if (blend) gl.glEnable(GL.GL_BLEND);
        if (depth) gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glBindVertexArray(va[0]);
        gl.glUseProgram(0);
        gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        gl.glDeleteTextures(1, new int[]{tex}, 0);
        List<Double> err = new ArrayList<>();
        int wet = 0;
        for (int i = 0; i < 64 * 64; i++) {
            int px = (int) fb.get(i * 4 + 1), py = (int) fb.get(i * 4 + 2);
            if (px >= mapW || py >= mapH) continue;
            double cx = (px + 0.5) / mapW * s.width(), cy = (py + 0.5) / mapH * s.height();
            double cpu = s.heightMm(cx, cy), gpu = fb.get(i * 4);
            if (cpu > 0 || gpu > 0) wet++;
            err.add(Math.abs(cpu - gpu));
        }
        if (err.isEmpty()) return "";
        double[] e = err.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double p99 = e[(int) Math.floor(e.length * 0.99)];
        return String.format(Locale.GERMAN, "Grafikkarte = Prozessor: Wasserhöhe ± %.1f µm (Median), 99 %% unter %.0f µm, %d Proben, %d nass",
                e[e.length / 2] * 1000, p99 * 1000, e.length, wet);
    }

    public void dispose(GL4 gl) {
        for (int p : new int[]{dropProg, normalProg, checkProg}) if (p != 0) gl.glDeleteProgram(p);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (ssbo != 0) gl.glDeleteBuffers(1, new int[]{ssbo}, 0);
        if (mapTex != 0) {
            gl.glDeleteFramebuffers(2, new int[]{hFbo, mapFbo}, 0);
            gl.glDeleteTextures(3, new int[]{hTex, mapTex, filmTex}, 0);
        }
        dropProg = normalProg = checkProg = vao = ssbo = capacity = mapTex = hTex = filmTex = mapW = mapH = filmW = filmH = 0;
        ready = false;
    }

    // ------------------------------------------------------------------ Shader

    static String read(String file) {
        try (InputStream in = WetglassGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("wetglass: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("wetglass: " + file + ": " + e.getMessage(), e);
        }
    }

    /** Übersetzt und bindet zwei Stufen (fertiger Text mit #version); auch für Demos. */
    public static int program(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int vs = stage(gl, GL4.GL_VERTEX_SHADER, vert, "vert"), fs = stage(gl, GL4.GL_FRAGMENT_SHADER, frag, "frag");
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetProgramiv(p, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("wetglass: Programm lässt sich nicht binden:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return p;
    }

    static int stage(GL4 gl, int type, String src, String name) {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{src}, null, 0);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(s, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(s, b.length, len, 0, b, 0);
            gl.glDeleteShader(s);
            throw new IllegalStateException("wetglass: " + name + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return s;
    }
}
