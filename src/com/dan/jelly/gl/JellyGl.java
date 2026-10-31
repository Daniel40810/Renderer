package com.dan.jelly.gl;

import com.dan.jelly.Bloom;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Die Ohrenquallen auf der Grafikkarte: je Qualle ein waagerechtes Viereck knapp über dem Spiegel, das der Shader
 * ({@code jelly.frag}) zum Schirm mit Rand, vier Gonaden, Mundarmen und Kanälen macht. Was man sieht, kommt durch die
 * Oberfläche: Durchlass nach Fresnel (flach gesehen spiegelt die See nur), Abschwächung mit der Tiefe nach der Sichttiefe,
 * Farbe des Wassers auf dem Weg. Gestrandete liegen als Gallert auf dem Grund.
 * <p>
 * Aufruf nach dem Wasser, ins gebundene Ziel mit Tiefe, Tiefentest an (Richtung wie die App): das Viereck schreibt keine
 * Tiefe und mischt sich über die See. So decken Pfähle, Boote und Stege die Quallen ab.
 */
public final class JellyGl {
    /** Ansicht: Projektion·Blick, Auge. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
    }

    /** Licht, vorbelichtet: Sonne und Mond als Bestrahlung, Himmel als Strahldichte, Dunst. */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {1, 1, 1}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double[] sky = {0.2, 0.3, 0.5}, fogColor = {0.6, 0.7, 0.8};
        public double fogDistance = 40_000;
        /** Farbe des Wassers als Auslöschung je m (R, G, B), Vorgabe westliche Ostsee: grünlich. */
        public double[] waterExtinction = {0.45, 0.12, 0.22};
    }

    static final int INST = 12;
    private int prog, vao, ivbo, capacity, drawn;
    private float[] inst = new float[256 * INST];
    private boolean ready;
    /** Weiter als so weit (m) zeichnet es nicht. */
    public double maxDistance = 160;

    public boolean ready() { return ready; }
    public int drawn() { return drawn; }

    public String info() { return ready ? String.format(Locale.GERMAN, "Quallen: %d gezeichnet", drawn) : "Quallen: nicht gestartet"; }

    public void init(GL4 gl) {
        prog = program(gl, "#version 430 core\n" + read("jelly.vert"), "#version 430 core\n" + read("jelly.frag"));
        int[] t = new int[1];
        gl.glGenVertexArrays(1, t, 0);
        vao = t[0];
        gl.glGenBuffers(1, t, 0);
        ivbo = t[0];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ivbo);
        capacity = 256;
        gl.glBufferData(GL.GL_ARRAY_BUFFER, capacity * INST * 4L, null, GL.GL_DYNAMIC_DRAW);
        for (int k = 0; k < 3; k++) {
            gl.glEnableVertexAttribArray(k);
            gl.glVertexAttribPointer(k, 4, GL.GL_FLOAT, false, INST * 4, k * 16L);
            gl.glVertexAttribDivisor(k, 1);
        }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        ready = true;
    }

    /** Zeichnet die sichtbaren Quallen in das gebundene Ziel. */
    public void draw(GL4 gl, Bloom b, View v, Light l) {
        drawn = 0;
        if (!ready || b == null || !b.enabled) return;
        Bloom.Jelly[] all = b.all();
        int n = 0;
        double md2 = maxDistance * maxDistance;
        for (Bloom.Jelly j : all) {
            if (!b.visible(j)) continue;
            double ex = j.x - v.eye[0], ey = j.y - v.eye[1], ez = j.z - v.eye[2];
            if (ex * ex + ey * ey + ez * ez > md2) continue;
            double seen = j.stranded() ? 1 : b.seen(j.depth);
            if (seen < 0.03) continue;
            if ((n + 1) * INST > inst.length) inst = java.util.Arrays.copyOf(inst, inst.length * 2);
            int o = n * INST;
            inst[o] = (float) j.x;
            inst[o + 1] = (float) j.y;
            inst[o + 2] = (float) j.z;
            inst[o + 3] = (float) (j.diameterNow() / 2);
            inst[o + 4] = (float) j.heading;
            inst[o + 5] = (float) j.contraction;
            inst[o + 6] = (float) (j.stranded() ? 0 : j.depth);
            inst[o + 7] = j.stranded() ? (float) Math.min(1, j.strandedFor / Math.max(1, b.strandedLife)) + 1 : 0;
            inst[o + 8] = (float) j.hue;
            inst[o + 9] = (float) seen;
            inst[o + 10] = (float) j.size;
            inst[o + 11] = 0;
            n++;
        }
        drawn = n;
        if (n == 0) return;
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ivbo);
        if (n > capacity) {
            capacity = Math.max(n, capacity * 2);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, capacity * INST * 4L, null, GL.GL_DYNAMIC_DRAW);
        }
        FloatBuffer fb = Buffers.newDirectFloatBuffer(inst, 0, n * INST);
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, n * INST * 4L, fb);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);

        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uViewProj"), 1, false, v.viewProj, 0);
        set3(gl, "uEye", v.eye);
        set3(gl, "uSunDir", l.sunDir);
        set3(gl, "uSunColor", l.sunColor);
        set3(gl, "uMoonDir", l.moonDir);
        set3(gl, "uMoonColor", l.moonColor);
        set3(gl, "uSky", l.sky);
        set3(gl, "uFogColor", l.fogColor);
        set3(gl, "uExt", l.waterExtinction);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uFogDist"), (float) l.fogDistance);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFuncSeparate(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA, GL.GL_ZERO, GL.GL_ONE);
        gl.glDepthMask(false);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(vao);
        gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, n);
        gl.glBindVertexArray(0);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(0);
    }

    public void dispose(GL4 gl) {
        if (prog != 0) gl.glDeleteProgram(prog);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (ivbo != 0) gl.glDeleteBuffers(1, new int[]{ivbo}, 0);
        prog = vao = ivbo = 0;
        ready = false;
    }

    private void set3(GL4 gl, String name, double[] v) {
        gl.glUniform3f(gl.glGetUniformLocation(prog, name), (float) v[0], (float) v[1], (float) v[2]);
    }

    /** Liest eine Shader-Datei aus diesem Package. */
    public static String read(String file) {
        try (InputStream in = JellyGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("jelly: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("jelly: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int program(GL4 gl, String vs, String fs) {
        int p = gl.glCreateProgram(), a = compile(gl, GL4.GL_VERTEX_SHADER, vs), b = compile(gl, GL4.GL_FRAGMENT_SHADER, fs);
        gl.glAttachShader(p, a);
        gl.glAttachShader(p, b);
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(a);
        gl.glDeleteShader(b);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetProgramiv(p, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] log = new byte[Math.max(1, len[0])];
            gl.glGetProgramInfoLog(p, log.length, null, 0, log, 0);
            throw new IllegalStateException("jelly: Programm lässt sich nicht binden: " + new String(log, StandardCharsets.UTF_8).trim());
        }
        return p;
    }

    private static int compile(GL4 gl, int type, String src) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{src}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(sh, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] log = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(sh, log.length, null, 0, log, 0);
            throw new IllegalStateException("jelly: Shader lässt sich nicht übersetzen: " + new String(log, StandardCharsets.UTF_8).trim());
        }
        return sh;
    }
}
