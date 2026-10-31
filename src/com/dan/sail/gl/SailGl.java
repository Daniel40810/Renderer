package com.dan.sail.gl;

import com.dan.sail.BoatShape;
import com.dan.sail.Sail;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Darstellung von {@code sail} mit JOGL (seit Timmendorf Phase 7): die Boote aus {@link BoatShape} (Rumpf, Kiel, Rigg,
 * Segel mit Bauch, Crew) je Bild neu gebaut und hochgeladen, beidseitig beleuchtet, helles Tuch scheint im Gegenlicht
 * durch, unter der Wasserlinie gedämpft. Ausgabe linear (in einen HDR-Puffer) oder mit Belichtung und sRGB. Die Shader
 * liegen als Ressourcen in diesem Package ({@code sail.vert}, {@code sail.frag}).
 */
public final class SailGl {
    /** Kamera: Matrix, Auge, Belichtung. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean tonemap = true;
        public double exposure = 1;
    }

    /** Licht: zur Sonne, Sonnenfarbe, Himmel und Boden, Dunst; Höhe des Wassers für die Dämpfung. */
    public static final class Light {
        public double[] sunDir = {-0.45, 0.75, 0.35};
        public double[] sunColor = {2.6, 2.45, 2.2};
        public double[] skyColor = {0.45, 0.55, 0.75};
        public double[] groundColor = {0.10, 0.14, 0.18};
        public double[] fogColor = {0.62, 0.70, 0.80};
        public double fogDistance = 3000, water = 0;
    }

    private int program, vao, vbo, count;
    private final BoatShape shape = new BoatShape();
    private FloatBuffer data = Buffers.newDirectFloatBuffer(9 * 8192);

    public void init(GL4 gl) {
        program = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, "sail.vert"), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, "sail.frag");
        gl.glAttachShader(program, vs);
        gl.glAttachShader(program, fs);
        gl.glLinkProgram(program);
        int[] ok = new int[1];
        gl.glGetProgramiv(program, GL4.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        if (ok[0] == 0) throw new IllegalStateException("sail: Programm lässt sich nicht binden");
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        gl.glGenBuffers(1, v, 0);
        vbo = v[0];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        for (int i = 0; i < 3; i++) {
            gl.glEnableVertexAttribArray(i);
            gl.glVertexAttribPointer(i, 3, GL.GL_FLOAT, false, 36, i * 12L);
        }
        gl.glBindVertexArray(0);
    }

    /** Zeit (s) für schlagendes Tuch und fliegende Kites (seit Phase 8). */
    public double time;

    /** Die Boote neu bauen und hochladen (je Bild), Wasserhöhe y0 je Boot aus {@code water}. */
    public void update(GL4 gl, List<Sail.Boat> boats, java.util.function.ToDoubleFunction<Sail.Boat> water) { update(gl, boats, water, null); }

    /**
     * Seit Phase 8: wie oben, dazu fremde Dreiecke im selben Format (9 Werte je Ecke: Lage, Normale, Farbe), etwa Schiffe
     * ohne Segel, die die App selbst baut.
     */
    public void update(GL4 gl, List<Sail.Boat> boats, java.util.function.ToDoubleFunction<Sail.Boat> water, float[] extra) {
        data.clear();
        count = 0;
        shape.time = time;
        for (Sail.Boat b : boats) put(shape.build(b, water.applyAsDouble(b)));
        if (extra != null) put(extra);
        data.flip();
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) data.limit() * 4, data, GL4.GL_STREAM_DRAW);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    private void put(float[] t) {
        if (data.remaining() < t.length) {
            FloatBuffer nb = Buffers.newDirectFloatBuffer(Math.max(data.capacity() * 2, data.position() + t.length));
            data.flip();
            nb.put(data);
            data = nb;
        }
        data.put(t);
        count += t.length / 9;
    }

    public void draw(GL4 gl, View v, Light l) {
        if (count == 0) return;
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
        gl.glUniform1f(loc(gl, "uWater"), (float) l.water);
        gl.glUniform1f(loc(gl, "uExposure"), (float) v.exposure);
        gl.glUniform1i(loc(gl, "uTonemap"), v.tonemap ? 1 : 0);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, count);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    /** Dreiecke im letzten Bild. */
    public int triangles() { return count / 3; }

    private int loc(GL4 gl, String n) { return gl.glGetUniformLocation(program, n); }

    private void vec(GL4 gl, String n, double[] c) { gl.glUniform3f(loc(gl, n), (float) c[0], (float) c[1], (float) c[2]); }

    public void dispose(GL4 gl) {
        if (program != 0) gl.glDeleteProgram(program);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (vbo != 0) gl.glDeleteBuffers(1, new int[]{vbo}, 0);
        program = vao = vbo = 0;
    }

    /** Liest eine Shader-Datei aus diesem Package (Ressource neben der Klasse). */
    public static String source(String file) {
        try (InputStream in = SailGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("sail: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("sail: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int compile(GL4 gl, int type, String file) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[4096];
            gl.glGetShaderInfoLog(sh, b.length, null, 0, b, 0);
            throw new IllegalStateException("sail: " + file + " lässt sich nicht übersetzen: " + new String(b, StandardCharsets.UTF_8).trim());
        }
        return sh;
    }
}
