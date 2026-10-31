package com.dan.cloth.gl;

import com.dan.cloth.Cloth;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Darstellung von {@code cloth} mit JOGL (seit Timmendorf Phase 6): die Tücher als Dreiecke, beidseitig beleuchtet,
 * mit Durchscheinen im Gegenlicht, Nässe und Farbbändern ({@link Cloth#colors}). Lage und Normalen kommen aus
 * Speicherpuffern: entweder von {@link ClothGpu} (die Tücher bleiben auf der Grafikkarte) oder vom Prozessor
 * ({@link #upload}). Die Shader liegen als Ressourcen in diesem Package ({@code cloth.vert}, {@code cloth.frag},
 * {@code cloth.comp}). Ausgabe linear (in einen HDR-Puffer) oder mit Belichtung und sRGB ({@link View#tonemap}).
 */
public final class ClothGl {
    /** Kamera: Matrix, Auge, Belichtung. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean tonemap = true;
        public double exposure = 1;
    }

    /** Licht: zur Sonne, Sonnenfarbe (wie Bestrahlung/π), Himmel und Boden, Dunst. */
    public static final class Light {
        public double[] sunDir = {-0.45, 0.75, 0.35};
        public double[] sunColor = {2.6, 2.45, 2.2};
        public double[] skyColor = {0.45, 0.55, 0.75};
        public double[] groundColor = {0.12, 0.11, 0.08};
        public double[] fogColor = {0.62, 0.70, 0.80};
        public double fogDistance = 1500;
    }

    private int program, vao, uvBuf, idxBuf, posBuf, nrmBuf, lookBuf, indices, total;
    private final List<Cloth> cloths = new ArrayList<>();
    private FloatBuffer cpuPos, cpuNrm;

    public void init(GL4 gl) {
        program = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, "cloth.vert"), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, "cloth.frag");
        gl.glAttachShader(program, vs);
        gl.glAttachShader(program, fs);
        gl.glLinkProgram(program);
        int[] ok = new int[1];
        gl.glGetProgramiv(program, GL4.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        if (ok[0] == 0) throw new IllegalStateException("cloth: Programm lässt sich nicht binden: " + log(gl, program, true));
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        int[] b = new int[5];
        gl.glGenBuffers(5, b, 0);
        uvBuf = b[0];
        idxBuf = b[1];
        posBuf = b[2];
        nrmBuf = b[3];
        lookBuf = b[4];
    }

    /** Die Tücher, die gezeichnet werden (gleiche Reihenfolge wie in {@link ClothGpu#setCloths}). */
    public void setCloths(GL4 gl, List<Cloth> cs) {
        cloths.clear();
        cloths.addAll(cs);
        total = 0;
        int tris = 0;
        for (Cloth c : cs) { total += c.nu * c.nv; tris += (c.nu - 1) * (c.nv - 1) * 2; }
        FloatBuffer uv = Buffers.newDirectFloatBuffer(Math.max(1, total) * 4);
        IntBuffer idx = Buffers.newDirectIntBuffer(Math.max(1, tris * 3));
        int off = 0;
        for (int ci = 0; ci < cs.size(); ci++) {
            Cloth c = cs.get(ci);
            for (int j = 0; j < c.nv; j++)
                for (int i = 0; i < c.nu; i++) uv.put(i / (c.nu - 1f)).put(j / (c.nv - 1f)).put(ci).put(0);
            for (int t : c.triangles()) idx.put(off + t);
            off += c.nu * c.nv;
        }
        uv.flip();
        idx.flip();
        indices = tris * 3;
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, uvBuf);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) uv.limit() * 4, uv, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 4, GL.GL_FLOAT, false, 16, 0);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, idxBuf);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) idx.limit() * 4, idx, GL.GL_STATIC_DRAW);
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        cpuPos = Buffers.newDirectFloatBuffer(Math.max(1, total) * 4);
        cpuNrm = Buffers.newDirectFloatBuffer(Math.max(1, total) * 4);
        ssbo(gl, posBuf, cpuPos, GL4.GL_DYNAMIC_DRAW);
        ssbo(gl, nrmBuf, cpuNrm, GL4.GL_DYNAMIC_DRAW);
    }

    /** Lage und Normalen der Tücher vom Prozessor hochladen (wenn sie mit {@link Cloth#step} rechnen). */
    public void upload(GL4 gl) {
        cpuPos.clear();
        cpuNrm.clear();
        for (Cloth c : cloths) {
            float[] n = c.normals();
            for (int k = 0; k < c.nu * c.nv; k++) {
                cpuPos.put((float) c.pos[k * 3]).put((float) c.pos[k * 3 + 1]).put((float) c.pos[k * 3 + 2]).put(1);
                cpuNrm.put(n[k * 3]).put(n[k * 3 + 1]).put(n[k * 3 + 2]).put(0);
            }
        }
        cpuPos.flip();
        cpuNrm.flip();
        ssbo(gl, posBuf, cpuPos, GL4.GL_DYNAMIC_DRAW);
        ssbo(gl, nrmBuf, cpuNrm, GL4.GL_DYNAMIC_DRAW);
    }

    private static void ssbo(GL4 gl, int b, FloatBuffer data, int usage) {
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, b);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) Math.max(4, data.limit()) * 4, data.limit() == 0 ? null : data, usage);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
    }

    /** Zeichnet aus den eigenen Puffern (nach {@link #upload}). */
    public void draw(GL4 gl, View v, Light l) { draw(gl, v, l, posBuf, nrmBuf); }

    /** Zeichnet aus den Puffern der Grafikkarten-Rechnung. */
    public void draw(GL4 gl, View v, Light l, ClothGpu gpu) { draw(gl, v, l, gpu.positions(), gpu.normals()); }

    void draw(GL4 gl, View v, Light l, int pos, int nrm) {
        if (indices == 0) return;
        FloatBuffer looks = Buffers.newDirectFloatBuffer(cloths.size() * 16);
        for (Cloth c : cloths) {
            float[] col = c.colors;
            int bands = Math.max(1, Math.min(3, col.length / 3));
            for (int b = 0; b < 3; b++) {
                int o = Math.min(b, bands - 1) * 3;
                looks.put(col[o]).put(col[o + 1]).put(col[o + 2]).put(0);
            }
            looks.put(bands).put(c.bandsAlongU ? 1 : 0).put((float) c.translucency).put((float) Math.max(0, Math.min(1, c.wetness)));
        }
        looks.flip();
        ssbo(gl, lookBuf, looks, GL4.GL_STREAM_DRAW);
        gl.glUseProgram(program);
        gl.glUniformMatrix4fv(loc(gl, "uVP"), 1, false, v.viewProj, 0);
        gl.glUniform3f(loc(gl, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        double[] sd = l.sunDir;
        double sl = Math.sqrt(sd[0] * sd[0] + sd[1] * sd[1] + sd[2] * sd[2]);
        gl.glUniform3f(loc(gl, "uSunDir"), (float) (sd[0] / sl), (float) (sd[1] / sl), (float) (sd[2] / sl));
        vec(gl, "uSunColor", l.sunColor);
        vec(gl, "uSkyColor", l.skyColor);
        vec(gl, "uGroundColor", l.groundColor);
        gl.glUniform4f(loc(gl, "uFog"), (float) l.fogColor[0], (float) l.fogColor[1], (float) l.fogColor[2], (float) l.fogDistance);
        gl.glUniform1f(loc(gl, "uExposure"), (float) v.exposure);
        gl.glUniform1i(loc(gl, "uTonemap"), v.tonemap ? 1 : 0);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, pos);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 1, nrm);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 2, lookBuf);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(vao);
        gl.glDrawElements(GL.GL_TRIANGLES, indices, GL.GL_UNSIGNED_INT, 0L);
        gl.glBindVertexArray(0);
        for (int i = 0; i < 3; i++) gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, i, 0);
        gl.glUseProgram(0);
    }

    private int loc(GL4 gl, String n) { return gl.glGetUniformLocation(program, n); }

    private void vec(GL4 gl, String n, double[] c) { gl.glUniform3f(loc(gl, n), (float) c[0], (float) c[1], (float) c[2]); }

    /** Dreiecke je Bild. */
    public int triangles() { return indices / 3; }

    public void dispose(GL4 gl) {
        if (program != 0) gl.glDeleteProgram(program);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        gl.glDeleteBuffers(5, new int[]{uvBuf, idxBuf, posBuf, nrmBuf, lookBuf}, 0);
        program = vao = 0;
    }

    /** Liest eine Shader-Datei aus diesem Package (Ressource neben der Klasse). */
    public static String source(String file) {
        try (InputStream in = ClothGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("cloth: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cloth: " + file + ": " + e.getMessage(), e);
        }
    }

    static String log(GL4 gl, int id, boolean prog) {
        int[] len = new int[1];
        if (prog) gl.glGetProgramiv(id, GL4.GL_INFO_LOG_LENGTH, len, 0); else gl.glGetShaderiv(id, GL4.GL_INFO_LOG_LENGTH, len, 0);
        if (len[0] <= 1) return "";
        byte[] b = new byte[len[0]];
        if (prog) gl.glGetProgramInfoLog(id, len[0], null, 0, b, 0); else gl.glGetShaderInfoLog(id, len[0], null, 0, b, 0);
        return new String(b, StandardCharsets.UTF_8).trim();
    }

    private static int compile(GL4 gl, int type, String file) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("cloth: " + file + " lässt sich nicht übersetzen: " + log(gl, sh, false));
        return sh;
    }
}
