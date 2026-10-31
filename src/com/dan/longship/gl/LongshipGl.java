package com.dan.longship.gl;

import com.dan.longship.Longship;
import com.dan.longship.Mesh;
import com.dan.longship.ShipType;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zeichnet Schiffe aus {@link Longship} mit JOGL (OpenGL 4.3): die festen Teile je Typ einmal im Grafikspeicher, die
 * beweglichen (Riemen, Rah, Segel) je Schiff und Bild neu; jedes mit der Modellmatrix des Schiffs. Licht in den
 * Einheiten des Benutzers (linear, HDR); {@link View#tonemap} rechnet für Vorführungen gleich das fertige Bild.
 */
public final class LongshipGl {
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean tonemap;
        public double exposure = 1;
    }

    public static final class Light {
        public double[] sunDir = {0.4, 0.7, 0.3}, sunColor = {2.6, 2.5, 2.3}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double[] skyColor = {0.32, 0.38, 0.48}, fogColor = {0.6, 0.65, 0.7};
        public double skyWeight = 1, fogDistance = 25_000, wetness;
        /** Wasserspiegel in der Welt (m): darunter ist der Rumpf dunkel und nass. */
        public double waterY;
        /** Himmel als Rundumbild für den Dunst (0 = fogColor), Abbildung wie {@code com.dan.timber}. */
        public int skyTexture;
    }

    private int program;
    private final Map<ShipType, int[]> statics = new EnumMap<>(ShipType.class), shields = new EnumMap<>(ShipType.class);
    private final Map<Longship, int[]> dynamics = new IdentityHashMap<>();
    private final Mesh scratch = new Mesh();
    private int drawn, triangles;

    public void init(GL4 gl) { program = link(gl, "longship.vert", "longship.frag"); }

    /** Zeichnet alle Schiffe (nach dem Undurchsichtigen, vor dem Wasser oder danach, wie die Szene es will). */
    public void draw(GL4 gl, List<Longship> ships, View v, Light l) {
        gl.glUseProgram(program);
        gl.glUniformMatrix4fv(loc(gl, "uVP"), 1, false, v.viewProj, 0);
        gl.glUniform3f(loc(gl, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        set3(gl, "uSunDir", l.sunDir); set3(gl, "uSunColor", l.sunColor); set3(gl, "uMoonDir", l.moonDir); set3(gl, "uMoonColor", l.moonColor);
        set3(gl, "uSkyColor", l.skyColor); set3(gl, "uFogColor", l.fogColor);
        gl.glUniform1f(loc(gl, "uSkyWeight"), (float) l.skyWeight);
        gl.glUniform1f(loc(gl, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(loc(gl, "uExposure"), (float) v.exposure);
        gl.glUniform1f(loc(gl, "uWaterY"), (float) l.waterY);
        gl.glUniform1f(loc(gl, "uWet"), (float) l.wetness);
        gl.glUniform1i(loc(gl, "uTonemap"), v.tonemap ? 1 : 0);
        gl.glActiveTexture(GL.GL_TEXTURE7);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.skyTexture);
        gl.glUniform1i(loc(gl, "uSky"), 7);
        gl.glUniform1i(loc(gl, "uSkyOn"), l.skyTexture != 0 ? 1 : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glEnable(GL.GL_DEPTH_TEST);
        drawn = 0;
        triangles = 0;
        for (Longship s : ships) {
            double[] m = s.model();
            float[] mf = new float[16];
            for (int i = 0; i < 16; i++) mf[i] = (float) m[i];
            gl.glUniformMatrix4fv(loc(gl, "uModel"), 1, false, mf, 0);
            int[] st = statics.computeIfAbsent(s.type, t -> upload(gl, s.hull.mesh, null));
            drawMesh(gl, st);
            if (s.type.shields && !s.rowing()) drawMesh(gl, shields.computeIfAbsent(s.type, t -> upload(gl, s.hull.shields, null)));
            s.dynamicMesh(scratch);
            int[] dy = dynamics.get(s);
            dy = upload(gl, scratch, dy);
            dynamics.put(s, dy);
            drawMesh(gl, dy);
            drawn++;
        }
        gl.glBindVertexArray(0);
    }

    private void drawMesh(GL4 gl, int[] b) {
        if (b[3] == 0) return;
        gl.glBindVertexArray(b[0]);
        gl.glDrawElements(GL.GL_TRIANGLES, b[3], GL.GL_UNSIGNED_INT, 0);
        triangles += b[3] / 3;
    }

    /** Lädt ein Netz in einen VAO {vao, vbo, ibo, Zahl der Indizes}; vorhandene Puffer werden neu gefüllt. */
    private static int[] upload(GL4 gl, Mesh m, int[] b) {
        if (b == null) {
            int[] t = new int[3];
            gl.glGenVertexArrays(1, t, 0);
            gl.glGenBuffers(2, t, 1);
            b = new int[]{t[0], t[1], t[2], 0};
            gl.glBindVertexArray(b[0]);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, b[1]);
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, b[2]);
            int st = Mesh.STRIDE * 4;
            gl.glEnableVertexAttribArray(0); gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, st, 0);
            gl.glEnableVertexAttribArray(1); gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, st, 12);
            gl.glEnableVertexAttribArray(2); gl.glVertexAttribPointer(2, 2, GL.GL_FLOAT, false, st, 24);
            gl.glEnableVertexAttribArray(3); gl.glVertexAttribPointer(3, 1, GL.GL_FLOAT, false, st, 32);
        } else {
            gl.glBindVertexArray(b[0]);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, b[1]);
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, b[2]);
        }
        float[] v = m.vertexData();
        int[] ix = m.indexData();
        FloatBuffer fb = Buffers.newDirectFloatBuffer(v);
        IntBuffer ib = Buffers.newDirectIntBuffer(ix);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) v.length * 4, fb, GL.GL_DYNAMIC_DRAW);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) ix.length * 4, ib, GL.GL_DYNAMIC_DRAW);
        b[3] = ix.length;
        gl.glBindVertexArray(0);
        return b;
    }

    /** „3 Schiffe · 41 200 Dreiecke“ */
    public String info() { return String.format(Locale.GERMAN, "%d Schiffe · %,d Dreiecke", drawn, triangles); }

    public void dispose(GL4 gl) {
        for (Map<?, int[]> mp : List.<Map<?, int[]>>of(statics, shields, dynamics))
            for (int[] b : mp.values()) { gl.glDeleteVertexArrays(1, b, 0); gl.glDeleteBuffers(2, b, 1); }
        statics.clear(); shields.clear(); dynamics.clear();
        if (program != 0) gl.glDeleteProgram(program);
        program = 0;
    }

    // ------------------------------------------------------------------ Shader

    private int loc(GL4 gl, String n) { return gl.glGetUniformLocation(program, n); }
    private void set3(GL4 gl, String n, double[] c) { gl.glUniform3f(loc(gl, n), (float) c[0], (float) c[1], (float) c[2]); }

    public static String source(String file) {
        try (InputStream in = LongshipGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("longship: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("longship: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int link(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, vert), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, frag);
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        if (ok[0] == 0) throw new IllegalStateException("longship: Programm lässt sich nicht binden: " + log(gl, p, true));
        return p;
    }

    private static int compile(GL4 gl, int type, String file) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("longship: " + file + " lässt sich nicht übersetzen:\n" + log(gl, sh, false));
        return sh;
    }

    private static String log(GL4 gl, int obj, boolean program) {
        int[] len = new int[1];
        if (program) gl.glGetProgramiv(obj, GL4.GL_INFO_LOG_LENGTH, len, 0); else gl.glGetShaderiv(obj, GL4.GL_INFO_LOG_LENGTH, len, 0);
        byte[] b = new byte[Math.max(1, len[0])];
        if (program) gl.glGetProgramInfoLog(obj, b.length, len, 0, b, 0); else gl.glGetShaderInfoLog(obj, b.length, len, 0, b, 0);
        return new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8);
    }
}
