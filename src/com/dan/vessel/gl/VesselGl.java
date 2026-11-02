package com.dan.vessel.gl;

import com.dan.vessel.Boat;
import com.dan.vessel.Chain;
import com.dan.vessel.Mesh;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Zeichnet Boote und Ketten von {@code com.dan.vessel} (LituyaBay1958 Phase 15): Netze aus {@link Mesh} einmal
 * hochgeladen und mit der Modellmatrix des Boots gezeichnet, Ketten als Linienzug, dazu beliebige Netze (Wasser, Gelände
 * der Vorführung), auch jedes Bild neu. Licht: Sonne, Himmel, Dunst; Ausgabe linear (HDR der App) oder mit ACES für
 * Vorführungen. Tiefe: wie die App sie eingestellt hat (umgekehrt oder klassisch, {@link View#reversedZ}).
 */
public final class VesselGl {
    /** Blick: Projektion · Blick (Spalten), Auge, umgekehrte Tiefe, Tonemapping mit Belichtung. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean reversedZ, tonemap;
        public double exposure = 1;
    }

    /** Licht (linear): Richtung zur Sonne, Farben, Dunst. */
    public static final class Light {
        public double[] sunDir = {0.4, 0.7, 0.3}, sunColor = {2.6, 2.5, 2.3}, skyColor = {0.3, 0.38, 0.5}, fogColor = {0.6, 0.65, 0.7};
        public double fogDistance = 25_000;
    }

    static final class Part { int vao, vbo, count, cap; }

    private int prog;
    private final Map<Object, Part> parts = new HashMap<>();
    private Part lines;

    public void init(GL4 gl) {
        prog = program(gl, read("vessel.vert"), read("vessel.frag"));
    }

    /** Lädt ein Netz (9 float je Ecke) unter dem Schlüssel key hoch oder ersetzt es (gleich groß oder kleiner: ohne neu anzulegen). */
    public void upload(GL4 gl, Object key, float[] v) {
        Part p = parts.get(key);
        if (p == null) {
            p = new Part();
            int[] a = new int[1], b = new int[1];
            gl.glGenVertexArrays(1, a, 0);
            gl.glGenBuffers(1, b, 0);
            p.vao = a[0];
            p.vbo = b[0];
            gl.glBindVertexArray(p.vao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, p.vbo);
            for (int i = 0; i < 3; i++) {
                gl.glEnableVertexAttribArray(i);
                gl.glVertexAttribPointer(i, 3, GL.GL_FLOAT, false, Mesh.STRIDE * 4, i * 12L);
            }
            gl.glBindVertexArray(0);
            parts.put(key, p);
        }
        FloatBuffer fb = Buffers.newDirectFloatBuffer(v);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, p.vbo);
        if (v.length > p.cap) {
            gl.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, fb, GL.GL_DYNAMIC_DRAW);
            p.cap = v.length;
        } else gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, v.length * 4L, fb);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        p.count = v.length / Mesh.STRIDE;
    }

    public boolean has(Object key) { return parts.containsKey(key); }

    static final float[] EINS = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    private void begin(GL4 gl, View v, Light l, float[] model, float alpha, float glanz) {
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uVP"), 1, false, v.viewProj, 0);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uModel"), 1, false, model, 0);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        double sl = Math.sqrt(l.sunDir[0] * l.sunDir[0] + l.sunDir[1] * l.sunDir[1] + l.sunDir[2] * l.sunDir[2]);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uSunDir"), (float) (l.sunDir[0] / sl), (float) (l.sunDir[1] / sl), (float) (l.sunDir[2] / sl));
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uSunColor"), (float) l.sunColor[0], (float) l.sunColor[1], (float) l.sunColor[2]);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uSkyColor"), (float) l.skyColor[0], (float) l.skyColor[1], (float) l.skyColor[2]);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uFogColor"), (float) l.fogColor[0], (float) l.fogColor[1], (float) l.fogColor[2]);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uAlpha"), alpha);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uGlanz"), glanz);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uExposure"), (float) v.exposure);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uTonemap"), v.tonemap ? 1 : 0);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(v.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDisable(GL.GL_CULL_FACE);
    }

    /** Zeichnet das Netz key mit der Modellmatrix model (null: Welt). */
    public void draw(GL4 gl, Object key, float[] model, View v, Light l) { draw(gl, key, model, v, l, 1, 0); }

    /** Wie {@link #draw(GL4, Object, float[], View, Light)} durchscheinend (alpha) und mit Glanz wie Wasser (0 … 1). */
    public void draw(GL4 gl, Object key, float[] model, View v, Light l, float alpha, float glanz) {
        Part p = parts.get(key);
        if (p == null || p.count == 0) return;
        begin(gl, v, l, model == null ? EINS : model, alpha, glanz);
        if (alpha < 1) { gl.glEnable(GL.GL_BLEND); gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA); gl.glDepthMask(false); }
        gl.glBindVertexArray(p.vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, p.count);
        gl.glBindVertexArray(0);
        if (alpha < 1) { gl.glDisable(GL.GL_BLEND); gl.glDepthMask(true); }
    }

    /** Zeichnet ein Boot (sein Netz wird beim ersten Mal aus {@link Mesh#kutter} gebaut). */
    public void draw(GL4 gl, Boat b, View v, Light l) {
        if (!has(b.hull)) upload(gl, b.hull, Mesh.kutter(b.hull));
        draw(gl, b.hull, b.model(), v, l);
    }

    /** Zeichnet eine Kette als Linienzug (dunkler Stahl; gerissen bleiben beide Stücke). */
    public void draw(GL4 gl, Chain c, View v, Light l) {
        double[][] nd = new double[c.nodes()][];
        for (int i = 0; i < nd.length; i++) nd[i] = c.node(i);
        draw(gl, nd, c.brokenAt(), v, l);
    }

    /** Zeichnet Kettenglieder aus Knoten (Welt) als Linien; das Glied cut (−1: keins) fehlt. */
    public void draw(GL4 gl, double[][] nodes, int cut, View v, Light l) {
        int n = nodes.length;
        if (n < 2) return;
        float[] pts = new float[(n - 1) * 2 * Mesh.STRIDE];
        int k = 0;
        for (int i = 0; i + 1 < n; i++) {
            if (cut == i) continue;
            for (int e = 0; e < 2; e++) {
                double[] p = nodes[i + e];
                pts[k++] = (float) p[0]; pts[k++] = (float) p[1]; pts[k++] = (float) p[2];
                pts[k++] = 0; pts[k++] = 1; pts[k++] = 0;
                pts[k++] = 0.05f; pts[k++] = 0.045f; pts[k++] = 0.04f;
            }
        }
        if (lines == null) {
            upload(gl, "vessel.lines", java.util.Arrays.copyOf(pts, k));
            lines = parts.get("vessel.lines");
        } else upload(gl, "vessel.lines", java.util.Arrays.copyOf(pts, k));
        begin(gl, v, l, EINS, 1, 0);
        gl.glBindVertexArray(lines.vao);
        gl.glDrawArrays(GL.GL_LINES, 0, k / Mesh.STRIDE);
        gl.glBindVertexArray(0);
    }

    public void dispose(GL4 gl) {
        for (Part p : parts.values()) {
            gl.glDeleteVertexArrays(1, new int[]{p.vao}, 0);
            gl.glDeleteBuffers(1, new int[]{p.vbo}, 0);
        }
        parts.clear();
        if (prog != 0) gl.glDeleteProgram(prog);
        prog = 0;
    }

    // ------------------------------------------------------------------ Shader

    static String read(String file) {
        try (InputStream in = VesselGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("vessel: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("vessel: " + file + ": " + e.getMessage(), e);
        }
    }

    static int program(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int vs = stage(gl, GL4.GL_VERTEX_SHADER, vert, "vessel.vert"), fs = stage(gl, GL4.GL_FRAGMENT_SHADER, frag, "vessel.frag");
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
            throw new IllegalStateException("vessel: Programm lässt sich nicht binden:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
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
            throw new IllegalStateException("vessel: " + name + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return s;
    }
}
