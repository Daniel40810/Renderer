package com.dan.understory.gl;

import com.dan.understory.Kind;
import com.dan.understory.PlantMesh;
import com.dan.understory.Understory;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Zeichnet einen {@link Understory} mit JOGL (OpenGL 4.3): je Art, Variante und Stufe ein Netz aus {@link PlantMesh},
 * alle Pflanzen eines Eimers als Instanzen in einem Aufruf. Wind im Vertex-Shader (Wedel und Zweige wiegen, Blätter
 * zittern, Bartflechten pendeln), Licht wie im Wald (beidseitige, durchscheinende Blätter, Sonne mit Schattenkarte,
 * Kronendach als Anteil des Sonnenlichts, Himmel, Dunst), Laubwerfer (Teufelskeule, Beeren) im Herbst gelb und kahl.
 * Shader: {@code understory.vert}, {@code understory.frag}, {@code understory_shadow.frag}.
 */
public final class UnderstoryGl {
    /** Blick: Projektion·Blick, Auge; Tonemapping nur für Demos. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean reversedZ, tonemap;
        public double exposure = 1;
    }

    /** Licht (linear, HDR) wie in den Wald-Paketen; canopy: Anteil der Sonne, der durch das Kronendach kommt (0..1). */
    public static final class Light {
        public double[] sunDir = {0.4, 0.7, 0.3}, sunColor = {2.6, 2.5, 2.3}, skyColor = {0.3, 0.38, 0.5}, fogColor = {0.6, 0.65, 0.7};
        public double fogDistance = 25_000, canopy = 1, wetness;
        /**
         * Licht unter dem Kronendach als Textur von oben (r = Anteil der Sonne, der durchkommt, etwa aus Beer-Lambert mit dem
         * Blattflächenindex; 0 = keine, dann gilt {@link #canopy}) und ihr Rechteck x, z, Kante (m).
         */
        public int canopyTexture;
        public double[] canopyRect = {0, 0, 1};
        public int sunShadowTexture;
        public float[] sunShadowMatrix;
        public double sunShadowBias = 0.001;
    }

    private final Understory field;
    private final int buckets;
    private final int[] vao, vbo, ibo, inst, idx, drawn;
    private final int[] meshTris;
    private int program, shadowProgram;
    private FloatBuffer buf = Buffers.newDirectFloatBuffer(Understory.STRIDE * 4096);
    private double windToX = 1, windToZ = 0, windSpeed = 3, gust = 0.5, autumn, bare;
    private long triangles;
    private String info = "Unterwuchs: nicht geladen";

    public UnderstoryGl(Understory field) {
        this.field = field;
        buckets = field.buckets();
        vao = new int[buckets]; vbo = new int[buckets]; ibo = new int[buckets]; inst = new int[buckets]; idx = new int[buckets]; drawn = new int[buckets];
        meshTris = new int[buckets];
    }

    public String info() { return info; }
    public long triangles() { return triangles; }

    public void init(GL4 gl) {
        program = link(gl, "understory.vert", "understory.frag", "");
        shadowProgram = link(gl, "understory.vert", "understory_shadow.frag", "#define DEPTH_ONLY\n");
        gl.glGenVertexArrays(buckets, vao, 0);
        gl.glGenBuffers(buckets, vbo, 0);
        gl.glGenBuffers(buckets, ibo, 0);
        gl.glGenBuffers(buckets, inst, 0);
        for (int b = 0; b < buckets; b++) {
            PlantMesh m = PlantMesh.build(field.kindOf(b), Understory.variantOf(b), Understory.lodOf(b));
            gl.glBindVertexArray(vao[b]);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[b]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, Math.max(4L, m.v.length * 4L), Buffers.newDirectFloatBuffer(m.v), GL.GL_STATIC_DRAW);
            int st = PlantMesh.STRIDE * 4;
            gl.glEnableVertexAttribArray(0); gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, st, 0);
            gl.glEnableVertexAttribArray(1); gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, st, 12);
            gl.glEnableVertexAttribArray(2); gl.glVertexAttribPointer(2, 3, GL.GL_FLOAT, false, st, 24);
            gl.glEnableVertexAttribArray(3); gl.glVertexAttribPointer(3, 2, GL.GL_FLOAT, false, st, 36);
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo[b]);
            gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, Math.max(4L, m.tri.length * 4L), Buffers.newDirectIntBuffer(m.tri), GL.GL_STATIC_DRAW);
            idx[b] = m.tri.length;
            meshTris[b] = m.nt;
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst[b]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, Understory.STRIDE * 4L * 64, null, GL4.GL_STREAM_DRAW);
            for (int a = 0; a < 2; a++) {
                gl.glEnableVertexAttribArray(4 + a);
                gl.glVertexAttribPointer(4 + a, 4, GL.GL_FLOAT, false, Understory.STRIDE * 4, a * 16L);
                gl.glVertexAttribDivisor(4 + a, 1);
            }
        }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    /** Wind: Richtung, in die er weht (x, z), Geschwindigkeit (m/s), Böigkeit 0..1. */
    public void setWind(double toX, double toZ, double speed, double gustiness) {
        double l = Math.hypot(toX, toZ);
        windToX = l > 1e-6 ? toX / l : 1;
        windToZ = l > 1e-6 ? toZ / l : 0;
        windSpeed = speed;
        gust = gustiness;
    }

    /** Tag im Jahr (Nordhalbkugel): Teufelskeule und Beerensträucher färben sich ab Ende September und werden kahl [G]. */
    public void setSeason(int day) {
        autumn = smooth(255, 285, day) * (1 - smooth(320, 330, day));
        bare = day < 130 ? 1 - smooth(110, 150, day) : smooth(285, 310, day);
    }

    static double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }

    /** Lädt die Eimer des Felds hoch (nach {@link Understory#update}). */
    public void update(GL4 gl) {
        triangles = 0;
        for (int b = 0; b < buckets; b++) {
            int c = field.count[b];
            drawn[b] = c;
            if (c == 0) continue;
            if (buf.capacity() < c * Understory.STRIDE) buf = Buffers.newDirectFloatBuffer(Math.max(c * Understory.STRIDE, buf.capacity() * 2));
            buf.clear();
            buf.put(field.bucket[b], 0, c * Understory.STRIDE).flip();
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst[b]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) c * Understory.STRIDE * 4, buf, GL4.GL_STREAM_DRAW);
            triangles += (long) c * meshTris[b];
        }
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        int[] kc = field.kindCounts();
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < kc.length; k++) if (kc[k] > 0) sb.append(sb.length() > 0 ? ", " : "").append(String.format(Locale.GERMAN, "%s %,d", field.kinds.get(k).name, kc[k]));
        info = String.format(Locale.GERMAN, "Unterwuchs: %,d Pflanzen (%s), %,d Dreiecke, %,d gepflanzt", field.shown, sb, triangles, field.planted);
    }

    public void draw(GL4 gl, View v, Light l, double t) {
        gl.glUseProgram(program);
        common(gl, program, v.viewProj, t);
        int p = program;
        gl.glUniform3f(loc(gl, p, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        gl.glUniform3f(loc(gl, p, "uSunDir"), (float) l.sunDir[0], (float) l.sunDir[1], (float) l.sunDir[2]);
        gl.glUniform3f(loc(gl, p, "uSunColor"), (float) l.sunColor[0], (float) l.sunColor[1], (float) l.sunColor[2]);
        gl.glUniform3f(loc(gl, p, "uSkyColor"), (float) l.skyColor[0], (float) l.skyColor[1], (float) l.skyColor[2]);
        gl.glUniform3f(loc(gl, p, "uFogColor"), (float) l.fogColor[0], (float) l.fogColor[1], (float) l.fogColor[2]);
        gl.glUniform1f(loc(gl, p, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(loc(gl, p, "uCanopy"), (float) l.canopy);
        gl.glActiveTexture(GL.GL_TEXTURE5);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.canopyTexture);
        gl.glUniform1i(loc(gl, p, "uCanopyTex"), 5);
        gl.glUniform4f(loc(gl, p, "uCanopyRect"), (float) l.canopyRect[0], (float) l.canopyRect[1], (float) Math.max(1, l.canopyRect[2]), l.canopyTexture != 0 ? 1f : 0f);
        gl.glUniform1f(loc(gl, p, "uWet"), (float) l.wetness);
        gl.glUniform2f(loc(gl, p, "uSeason"), (float) autumn, (float) bare);
        gl.glUniform2f(loc(gl, p, "uTone"), v.tonemap ? 1f : 0f, (float) v.exposure);
        gl.glActiveTexture(GL.GL_TEXTURE6);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.sunShadowTexture);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(loc(gl, p, "uSunShadow"), 6);
        boolean ss = l.sunShadowTexture != 0 && l.sunShadowMatrix != null;
        gl.glUniformMatrix4fv(loc(gl, p, "uSunShadowM"), 1, false, ss ? l.sunShadowMatrix : new float[16], 0);
        gl.glUniform2f(loc(gl, p, "uSunShadowP"), ss ? 1f : 0f, (float) l.sunShadowBias);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(v.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDisable(GL.GL_BLEND);
        for (int b = 0; b < buckets; b++) {
            if (drawn[b] == 0 || idx[b] == 0) continue;
            Kind k = field.kindOf(b);
            gl.glUniform1f(loc(gl, p, "uSway"), sway(k));
            gl.glUniform1i(loc(gl, p, "uDeciduous"), k == Kind.DEVILS_CLUB || k == Kind.BERRY ? 1 : 0);
            gl.glBindVertexArray(vao[b]);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, idx[b], GL.GL_UNSIGNED_INT, 0, drawn[b]);
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    /** In eine gebundene Tiefen-Schattenkarte (lightVP: Welt → Licht), nur Stufe 0. */
    public void drawShadow(GL4 gl, float[] lightVP, double t) {
        gl.glUseProgram(shadowProgram);
        common(gl, shadowProgram, lightVP, t);
        for (int b = 0; b < buckets; b++) {
            if (drawn[b] == 0 || idx[b] == 0 || Understory.lodOf(b) != 0) continue;
            gl.glUniform1f(loc(gl, shadowProgram, "uSway"), sway(field.kindOf(b)));
            gl.glBindVertexArray(vao[b]);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, idx[b], GL.GL_UNSIGNED_INT, 0, drawn[b]);
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    /** Biegsamkeit je Art (Höhe im Netz geht quadratisch ein). */
    static float sway(Kind k) {
        return switch (k) {
            case FERN -> 0.9f;
            case BERRY -> 0.7f;
            case DEVILS_CLUB -> 0.5f;
            case LICHEN -> 1.4f;
            default -> 0f;
        };
    }

    private void common(GL4 gl, int p, float[] vp, double t) {
        gl.glUniformMatrix4fv(loc(gl, p, "uVP"), 1, false, vp, 0);
        gl.glUniform1f(loc(gl, p, "uTime"), (float) (t % 3600));
        gl.glUniform4f(loc(gl, p, "uWind"), (float) windToX, (float) windToZ, (float) windSpeed, (float) gust);
    }

    public void dispose(GL4 gl) {
        if (program == 0) return;
        gl.glDeleteProgram(program);
        gl.glDeleteProgram(shadowProgram);
        gl.glDeleteVertexArrays(buckets, vao, 0);
        gl.glDeleteBuffers(buckets, vbo, 0);
        gl.glDeleteBuffers(buckets, ibo, 0);
        gl.glDeleteBuffers(buckets, inst, 0);
        program = 0;
    }

    private static int loc(GL4 gl, int p, String n) { return gl.glGetUniformLocation(p, n); }

    public static String source(String file) {
        try (InputStream in = UnderstoryGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("understory: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("understory: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int link(GL4 gl, String vert, String frag, String defs) {
        int p = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, vert, defs), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, frag, defs);
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[8192];
            int[] len = new int[1];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("understory: Programm lässt sich nicht binden: " + new String(b, 0, len[0], StandardCharsets.UTF_8));
        }
        return p;
    }

    private static int compile(GL4 gl, int type, String file, String defs) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + defs + source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[8192];
            int[] len = new int[1];
            gl.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
            throw new IllegalStateException("understory: " + file + " lässt sich nicht übersetzen:\n" + new String(b, 0, len[0], StandardCharsets.UTF_8));
        }
        return sh;
    }
}
