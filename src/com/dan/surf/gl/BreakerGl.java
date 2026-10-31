package com.dan.surf.gl;

import com.dan.surf.BreakerMesh;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Die Brecher mit JOGL (seit Sylt Phase 6, F9 A): das Netz aus {@link BreakerMesh} je Bild hochgeladen und mit
 * {@code breaker.vert}/{@code breaker.frag} gezeichnet, dazu der Sand darunter. Mehrere Becken über Plätze
 * ({@code slot}); jeder Platz hat einen Puffer für das Wasser (je Bild neu) und einen für den Sand (einmal).
 * Licht und Blick kommen als Zahlen; die Ausgabe ist linear und vorbelichtet, mit {@link Light#display} > 0 gleich fertig
 * fürs Bild (Vorführung).
 */
public final class BreakerGl {
    /** Blick: Matrix (Spalten zuerst), Auge, umgekehrte Tiefe. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = {0, 0, 0};
        public boolean reversedZ;
    }

    /** Licht und Farben, vorbelichtet. */
    public static final class Light {
        public double[] sunDir = {0.3, 0.5, -0.8}, sunColor = {2.6, 2.45, 2.2}, skyTop = {0.28, 0.42, 0.70}, skyHorizon = {0.62, 0.70, 0.78};
        public double[] fogColor = {0.62, 0.70, 0.78}, waterDeep = {0.02, 0.10, 0.12}, waterShallow = {0.06, 0.24, 0.22}, sand = {0.52, 0.45, 0.33};
        public double fogDistance = 6000, display;
        /**
         * Seit Sylt Phase 13: Meeresleuchten ({@link com.dan.surf.Bioluminescence}), vorbelichtet: die Strahldichte frisch
         * aufgewühlten Wassers (Schaum, Kamm, Blasen im Schwall). 0 = aus.
         */
        public double[] glow = {0, 0, 0};
        /**
         * Seit Sylt Phase 13: Spuren im Sand (etwa ein {@code com.dan.mudflat.TrackMap}: R8, Tiefe 0..1) über dem Rechteck
         * {@code trackRect} {x0, z0, Kantenlänge, an} in der Szene, und wie die Koordinaten des Netzes in der Szene liegen
         * ({@code trackFrame}: Ursprung x, z, Einheitsvektor des Netz-x, Einheitsvektor des Netz-z). 0 = keine.
         */
        public int trackTexture;
        public double[] trackRect = {0, 0, 1, 0}, trackFrame = {0, 0, 1, 0, 0, 1};
    }

    static final int MAX_SLOTS = 8;
    private int program;
    private final int[] vao = new int[MAX_SLOTS], vbo = new int[MAX_SLOTS], ibo = new int[MAX_SLOTS], count = new int[MAX_SLOTS], capV = new int[MAX_SLOTS], capI = new int[MAX_SLOTS];
    private final int[] bedVao = new int[MAX_SLOTS], bedVbo = new int[MAX_SLOTS], bedIbo = new int[MAX_SLOTS], bedCount = new int[MAX_SLOTS];
    private String info = "";

    public String info() { return info; }

    public void init(GL4 gl) {
        program = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, "breaker.vert"), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, "breaker.frag");
        gl.glAttachShader(program, vs);
        gl.glAttachShader(program, fs);
        gl.glLinkProgram(program);
        int[] ok = new int[1];
        gl.glGetProgramiv(program, GL4.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        if (ok[0] == 0) throw new IllegalStateException("surf: Brecher-Programm lässt sich nicht binden: " + log(gl, program, true));
        info = "Brecher-Netz (breaker.vert/frag)";
    }

    /** Das Wasser eines Beckens hochladen (je Bild). */
    public void upload(GL4 gl, int slot, BreakerMesh m) {
        int[] ids = {vao[slot], vbo[slot], ibo[slot]};
        int nv = m.vertexCount * BreakerMesh.STRIDE, ni = m.indexCount;
        boolean fresh = ids[0] == 0;
        if (fresh) {
            int[] t = new int[1];
            gl.glGenVertexArrays(1, t, 0);
            vao[slot] = t[0];
            int[] b = new int[2];
            gl.glGenBuffers(2, b, 0);
            vbo[slot] = b[0];
            ibo[slot] = b[1];
        }
        gl.glBindVertexArray(vao[slot]);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[slot]);
        if (nv > capV[slot]) { capV[slot] = nv + nv / 4; gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) capV[slot] * 4, null, GL4.GL_STREAM_DRAW); }
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, (long) nv * 4, FloatBuffer.wrap(m.vertices, 0, nv));
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo[slot]);
        if (ni > capI[slot]) { capI[slot] = ni + ni / 4; gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) capI[slot] * 4, null, GL4.GL_STREAM_DRAW); }
        gl.glBufferSubData(GL.GL_ELEMENT_ARRAY_BUFFER, 0, (long) ni * 4, IntBuffer.wrap(m.indices, 0, ni));
        attribs(gl);
        gl.glBindVertexArray(0);
        count[slot] = ni;
    }

    /** Den Sand eines Beckens hochladen (einmal). */
    public void uploadBed(GL4 gl, int slot, BreakerMesh m) {
        if (bedVao[slot] == 0) {
            int[] t = new int[1];
            gl.glGenVertexArrays(1, t, 0);
            bedVao[slot] = t[0];
            int[] b = new int[2];
            gl.glGenBuffers(2, b, 0);
            bedVbo[slot] = b[0];
            bedIbo[slot] = b[1];
        }
        gl.glBindVertexArray(bedVao[slot]);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, bedVbo[slot]);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) m.vertexCount * BreakerMesh.STRIDE * 4, FloatBuffer.wrap(m.vertices, 0, m.vertexCount * BreakerMesh.STRIDE), m.swashColumn >= 0 ? GL4.GL_DYNAMIC_DRAW : GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, bedIbo[slot]);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) m.indexCount * 4, IntBuffer.wrap(m.indices, 0, m.indexCount), GL.GL_STATIC_DRAW);
        attribs(gl);
        gl.glBindVertexArray(0);
        bedCount[slot] = m.indexCount;
    }

    /** Nässe des Sandes neu hochladen (seit Sylt Phase 7; nach {@link BreakerMesh#updateBed}), Netz unverändert. */
    public void refreshBed(GL4 gl, int slot, BreakerMesh m) {
        if (bedVbo[slot] == 0) { uploadBed(gl, slot, m); return; }
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, bedVbo[slot]);
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, (long) m.vertexCount * BreakerMesh.STRIDE * 4, FloatBuffer.wrap(m.vertices, 0, m.vertexCount * BreakerMesh.STRIDE));
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    private static void attribs(GL4 gl) {
        int st = BreakerMesh.STRIDE * 4;
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, st, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, st, 12);
        gl.glEnableVertexAttribArray(2);
        gl.glVertexAttribPointer(2, 4, GL.GL_FLOAT, false, st, 24);
    }

    /**
     * Sand und Wasser eines Beckens zeichnen (Tiefentest wie der Aufrufer); wetLine: bis wohin der Sand nass ist (m), NaN:
     * die Nässe kommt je Ecke aus dem Schwall ({@link BreakerMesh#bed(com.dan.surf.WaveTrain, com.dan.surf.Swash, int)}).
     */
    public void draw(GL4 gl, int slot, View v, Light l, double time, double wetLine) {
        gl.glUseProgram(program);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(program, "uVP"), 1, false, v.viewProj, 0);
        set3(gl, "uEye", v.eye);
        set3(gl, "uSunDir", l.sunDir);
        set3(gl, "uSunColor", l.sunColor);
        set3(gl, "uSkyTop", l.skyTop);
        set3(gl, "uSkyHorizon", l.skyHorizon);
        set3(gl, "uFogColor", l.fogColor);
        set3(gl, "uWaterDeep", l.waterDeep);
        set3(gl, "uWaterShallow", l.waterShallow);
        set3(gl, "uSand", l.sand);
        gl.glUniform1f(gl.glGetUniformLocation(program, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(gl.glGetUniformLocation(program, "uTime"), (float) (time % 3600));
        gl.glUniform1f(gl.glGetUniformLocation(program, "uDisplay"), (float) l.display);
        gl.glUniform1f(gl.glGetUniformLocation(program, "uWetLine"), (float) wetLine);
        gl.glUniform3f(gl.glGetUniformLocation(program, "uGlow"), (float) l.glow[0], (float) l.glow[1], (float) l.glow[2]);
        gl.glActiveTexture(GL.GL_TEXTURE0 + 9);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.trackTexture);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(gl.glGetUniformLocation(program, "uTracks"), 9);
        gl.glUniform4f(gl.glGetUniformLocation(program, "uTrackRect"), (float) l.trackRect[0], (float) l.trackRect[1], (float) l.trackRect[2],
                l.trackTexture == 0 ? 0f : (float) l.trackRect[3]);
        double[] tf = l.trackFrame;
        gl.glUniform2f(gl.glGetUniformLocation(program, "uTrackO"), (float) tf[0], (float) tf[1]);
        gl.glUniform4f(gl.glGetUniformLocation(program, "uTrackAxes"), (float) tf[2], (float) tf[3], (float) tf[4], (float) tf[5]);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(v.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDisable(GL.GL_CULL_FACE);
        if (bedCount[slot] > 0) {
            gl.glUniform1i(gl.glGetUniformLocation(program, "uMode"), Double.isNaN(wetLine) ? 2 : 1);
            gl.glBindVertexArray(bedVao[slot]);
            gl.glDrawElements(GL.GL_TRIANGLES, bedCount[slot], GL.GL_UNSIGNED_INT, 0);
        }
        if (count[slot] > 0) {
            gl.glUniform1i(gl.glGetUniformLocation(program, "uMode"), 0);
            gl.glBindVertexArray(vao[slot]);
            gl.glDrawElements(GL.GL_TRIANGLES, count[slot], GL.GL_UNSIGNED_INT, 0);
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    private void set3(GL4 gl, String u, double[] v) {
        gl.glUniform3f(gl.glGetUniformLocation(program, u), (float) v[0], (float) v[1], (float) v[2]);
    }

    public void dispose(GL4 gl) {
        if (program != 0) gl.glDeleteProgram(program);
        for (int s = 0; s < MAX_SLOTS; s++) {
            for (int a : new int[]{vao[s], bedVao[s]}) if (a != 0) gl.glDeleteVertexArrays(1, new int[]{a}, 0);
            for (int b : new int[]{vbo[s], ibo[s], bedVbo[s], bedIbo[s]}) if (b != 0) gl.glDeleteBuffers(1, new int[]{b}, 0);
            vao[s] = bedVao[s] = vbo[s] = ibo[s] = bedVbo[s] = bedIbo[s] = 0;
            capV[s] = capI[s] = count[s] = bedCount[s] = 0;
        }
        program = 0;
    }

    private static int compile(GL4 gl, int type, String file) {
        int sh = gl.glCreateShader(type);
        String src = SurfGl.source(file);
        if (!src.startsWith("#version")) src = "#version 430 core\n" + src;
        gl.glShaderSource(sh, 1, new String[]{src}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("surf: " + file + ": " + log(gl, sh, false));
        return sh;
    }

    private static String log(GL4 gl, int id, boolean prog) {
        int[] len = new int[1];
        if (prog) gl.glGetProgramiv(id, GL4.GL_INFO_LOG_LENGTH, len, 0); else gl.glGetShaderiv(id, GL4.GL_INFO_LOG_LENGTH, len, 0);
        byte[] b = new byte[Math.max(1, len[0])];
        if (prog) gl.glGetProgramInfoLog(id, b.length, null, 0, b, 0); else gl.glGetShaderInfoLog(id, b.length, null, 0, b, 0);
        return new String(b, java.nio.charset.StandardCharsets.UTF_8).trim();
    }
}
