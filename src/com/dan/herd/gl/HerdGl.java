package com.dan.herd.gl;

import com.dan.herd.AnimalMesh;
import com.dan.herd.Herd;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Die Tiere auf der Grafikkarte: ein Netz je Art ({@link AnimalMesh}), je Tier eine Instanz mit Ort, Richtung, Gang,
 * Kopf, Liegen, Sitzen, Fellfarbe und Schwanz; Beine, Kopf und Schwanz bewegt der Vertex-Shader. Ein Aufruf je Art.
 */
public final class HerdGl {
    /** Ansicht: Projektion·Blick, Auge, Tiefenvergleich, Reichweite (m). */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean reversedZ;
        public double range = 600;
    }

    /** Licht, vorbelichtet: Sonne und Mond als Bestrahlung, Himmel als Bestrahlung/π, Dunst; Wolkenschatten optional. */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {1, 1, 1}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double[] sky = {0.2, 0.25, 0.3}, fogColor = {0.5, 0.55, 0.6};
        public double fogDistance = 25_000;
        public int cloudShadow;
        public double[] cloudRect = {0, 0, 1};
    }

    static final int INST = 16;
    private int prog, ivbo, capacity;
    private final int[] vao = new int[Herd.Kind.values().length], vbo = new int[vao.length], count = new int[vao.length];
    private final AnimalMesh[] meshes = new AnimalMesh[vao.length];
    private boolean ready;
    private String info = "";
    private int drawn;

    public boolean ready() { return ready; }
    public String info() { return info; }
    public int drawn() { return drawn; }

    public void init(GL4 gl) {
        prog = program(gl, "#version 430 core\n" + read("herd.vert"), "#version 430 core\n" + read("herd.frag"));
        int[] t = new int[1];
        gl.glGenBuffers(1, t, 0);
        ivbo = t[0];
        capacity = 128;
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ivbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, capacity * INST * 4L, null, GL.GL_DYNAMIC_DRAW);
        int tris = 0;
        for (Herd.Kind k : Herd.Kind.values()) {
            AnimalMesh m = AnimalMesh.build(k);
            meshes[k.ordinal()] = m;
            float[] v = m.vertices();
            gl.glGenVertexArrays(1, t, 0);
            vao[k.ordinal()] = t[0];
            gl.glGenBuffers(1, t, 0);
            vbo[k.ordinal()] = t[0];
            count[k.ordinal()] = m.count();
            tris += m.count() / 3;
            gl.glBindVertexArray(vao[k.ordinal()]);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[k.ordinal()]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
            int st = AnimalMesh.FLOATS * 4;
            attr(gl, 0, 3, st, 0, false);
            attr(gl, 1, 3, st, 12, false);
            attr(gl, 2, 3, st, 24, false);
            attr(gl, 3, 1, st, 36, false);
            attr(gl, 4, 1, st, 40, false);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ivbo);
            int is = INST * 4;
            for (int i = 0; i < 4; i++) attr(gl, 5 + i, 4, is, i * 16, true);
        }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        ready = true;
        info = String.format(Locale.GERMAN, "4 Arten, %d Dreiecke, je Tier eine Instanz", tris);
    }

    private static void attr(GL4 gl, int loc, int n, int stride, int off, boolean inst) {
        gl.glEnableVertexAttribArray(loc);
        gl.glVertexAttribPointer(loc, n, GL.GL_FLOAT, false, stride, off);
        gl.glVertexAttribDivisor(loc, inst ? 1 : 0);
    }

    /** Instanzdaten eines Tiers (auch für die Prüfung). */
    public static float[] instance(Herd.Animal a) {
        double amp = Math.min(0.55, a.speed / Math.max(0.1, a.kind.walk) * 0.35);
        double[] coat = AnimalMesh.coat(a.kind, a.tone);
        double sit = a.state == Herd.State.SIT ? 1 : 0;
        double down = a.kind == Herd.Kind.HUHN ? a.head : a.head;
        return new float[]{(float) a.pos[0], (float) a.pos[1], (float) a.pos[2], (float) Math.toRadians(a.heading),
                (float) a.gait, (float) amp, (float) down, (float) a.lie,
                (float) coat[0], (float) coat[1], (float) coat[2], (float) sit,
                (float) a.wag, (float) a.size, 0f, 0f};
    }

    /** Wie weit sich der Kopf zum Fressen senkt (rad). */
    static double headDown(Herd.Kind k) {
        return switch (k) { case HUHN -> 1.1; case ZIEGE -> 1.0; case SCHWEIN -> 0.5; case HUND -> 0.8; };
    }

    /** Alle Tiere draußen und in Reichweite zeichnen. */
    public void draw(GL4 gl, Herd herd, View view, Light light) {
        List<Herd.Animal> all = herd.animals();
        int nk = vao.length;
        int[] per = new int[nk];
        java.util.List<Herd.Animal> vis = new java.util.ArrayList<>();
        double r2 = view.range * view.range;
        for (Herd.Animal a : all) {
            if (a.state == Herd.State.AWAY) continue;
            double dx = a.pos[0] - view.eye[0], dz = a.pos[2] - view.eye[2];
            if (dx * dx + dz * dz > r2) continue;
            vis.add(a);
            per[a.kind.ordinal()]++;
        }
        drawn = vis.size();
        if (drawn == 0) return;
        int[] start = new int[nk];
        for (int i = 1; i < nk; i++) start[i] = start[i - 1] + per[i - 1];
        int[] fill = start.clone();
        float[] d = new float[drawn * INST];
        for (Herd.Animal a : vis) System.arraycopy(instance(a), 0, d, fill[a.kind.ordinal()]++ * INST, INST);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ivbo);
        if (drawn > capacity) {
            capacity = drawn * 2;
            gl.glBufferData(GL.GL_ARRAY_BUFFER, capacity * INST * 4L, null, GL.GL_DYNAMIC_DRAW);
        }
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, drawn * INST * 4L, Buffers.newDirectFloatBuffer(d));
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(loc(gl, "uVP"), 1, false, view.viewProj, 0);
        set3(gl, "uCam", view.eye);
        set3(gl, "uSunDir", light.sunDir);
        set3(gl, "uSunCol", light.sunColor);
        set3(gl, "uMoonDir", light.moonDir);
        set3(gl, "uMoonCol", light.moonColor);
        set3(gl, "uSky", light.sky);
        set3(gl, "uFogColor", light.fogColor);
        gl.glUniform1f(loc(gl, "uFogDist"), (float) light.fogDistance);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, light.cloudShadow);
        gl.glUniform1i(loc(gl, "uCloudShadow"), 0);
        gl.glUniform4f(loc(gl, "uCloudRect"), (float) light.cloudRect[0], (float) light.cloudRect[1], (float) Math.max(1, light.cloudRect[2]), light.cloudShadow != 0 ? 1f : 0f);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(view.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDisable(GL.GL_BLEND);
        for (int k = 0; k < nk; k++) {
            if (per[k] == 0) continue;
            AnimalMesh m = meshes[k];
            gl.glUniform2f(loc(gl, "uNeck"), (float) m.neck[0], (float) m.neck[1]);
            gl.glUniform2f(loc(gl, "uHipF"), (float) m.hipFront[0], (float) m.hipFront[1]);
            gl.glUniform2f(loc(gl, "uHipB"), (float) m.hipBack[0], (float) m.hipBack[1]);
            gl.glUniform2f(loc(gl, "uTail"), (float) m.tail[0], (float) m.tail[1]);
            gl.glUniform1f(loc(gl, "uLeg"), (float) m.legLength);
            gl.glUniform1f(loc(gl, "uHeadDown"), (float) headDown(Herd.Kind.values()[k]));
            gl.glBindVertexArray(vao[k]);
            gl.glDrawArraysInstancedBaseInstance(GL.GL_TRIANGLES, 0, count[k], per[k], start[k]);
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        info = String.format(Locale.GERMAN, "%d Tiere gezeichnet", drawn);
    }

    private int loc(GL4 gl, String n) { return gl.glGetUniformLocation(prog, n); }
    private void set3(GL4 gl, String n, double[] v) { gl.glUniform3f(loc(gl, n), (float) v[0], (float) v[1], (float) v[2]); }

    public void dispose(GL4 gl) {
        if (!ready) return;
        gl.glDeleteProgram(prog);
        gl.glDeleteBuffers(1, new int[]{ivbo}, 0);
        gl.glDeleteBuffers(vbo.length, vbo, 0);
        gl.glDeleteVertexArrays(vao.length, vao, 0);
        ready = false;
    }

    static String read(String file) {
        try (InputStream in = HerdGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("herd: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("herd: " + file + ": " + e.getMessage(), e);
        }
    }

    static int program(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int vs = stage(gl, GL4.GL_VERTEX_SHADER, vert, "herd.vert"), fs = stage(gl, GL4.GL_FRAGMENT_SHADER, frag, "herd.frag");
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[4096];
            int[] len = new int[1];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("herd: Programm lässt sich nicht binden: " + new String(b, 0, len[0], StandardCharsets.UTF_8));
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
            byte[] b = new byte[4096];
            int[] len = new int[1];
            gl.glGetShaderInfoLog(s, b.length, len, 0, b, 0);
            throw new IllegalStateException("herd: " + name + " lässt sich nicht übersetzen:\n" + new String(b, 0, len[0], StandardCharsets.UTF_8));
        }
        return s;
    }
}
