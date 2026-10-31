package com.dan.beach.gl;

import com.dan.beach.Beach;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Darstellung des Strands mit JOGL (Paketvertrag Punkt 3), seit Timmendorf:
 * <ul>
 * <li><b>Für Szenen</b>: {@link #init} lädt die Karten als eine Textur (RGBA16F, Umlauf, Mipmaps: Neigung, Relief, Korn),
 * {@link #bind} setzt sie und die Werte eines {@link Beach} in ein fremdes Programm, das {@code beach.glsl} einsetzt
 * (der Geländeshader der Szene ruft dort {@code beachSurface} auf, wo Sand liegt).</li>
 * <li><b>Für die Vorführung</b>: {@link #drawPatch} zeichnet ein Stück Strand (trocken und zertreten oben, nass am Wasser,
 * dahinter die See) mit {@code beach_demo.vert/frag}.</li>
 * </ul>
 * Licht kommt als Zahlen; die Ausgabe ist linear, mit {@link Light#display} &gt; 0 fertig für den Bildschirm.
 */
public final class BeachGl {
    /** Licht: Richtung zur Sonne, Farben (linear, vorbelichtet), Dunst. */
    public static final class Light {
        public double[] sunDir = {0.35, 0.4, -0.85}, sunColor = {3.0, 2.85, 2.6}, skyTop = {0.30, 0.42, 0.62}, skyHorizon = {0.55, 0.65, 0.78};
        public double fogDistance = 6000, display;
    }

    /** Blick: Matrix (Spalten zuerst), Auge. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = {0, 1.7, 5};
    }

    private int tex, demo, vao, vbo;
    private String info = "";

    public String info() { return info; }

    /** Die Textur (für eigene Shader). */
    public int texture() { return tex; }

    /** Karten hochladen (einmal). */
    public void init(GL4 gl) {
        Beach.Maps m = Beach.maps();
        int n = m.n;
        float[] d = new float[n * n * 4];
        for (int k = 0; k < n * n; k++) {
            d[k * 4] = m.du[k];
            d[k * 4 + 1] = m.dv[k];
            d[k * 4 + 2] = m.relief[k];
            d[k * 4 + 3] = m.grain[k];
        }
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        tex = t[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA16F, n, n, 0, GL.GL_RGBA, GL.GL_FLOAT, FloatBuffer.wrap(d));
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        info = String.format(Locale.GERMAN, "Strand: %d² RGBA16F aus %s (beach.glsl)", n, m.source);
    }

    public boolean ready() { return tex != 0; }

    /**
     * Textur und Werte in ein Programm setzen, das {@code beach.glsl} einsetzt (Programm ist in Gebrauch). {@code on} falsch
     * schaltet den Strand im Shader ab; ein Programm ohne die Uniforms bleibt unberührt.
     */
    public void bind(GL4 gl, int prog, Beach b, int unit, boolean on) {
        int k = gl.glGetUniformLocation(prog, "uBeachOn");
        if (k < 0) return;
        gl.glUniform1i(k, on && tex != 0 ? 1 : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uBeachTex"), unit);
        double a = Math.toRadians(b.octave2Deg);
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uBeachA"), (float) b.tile, (float) b.relief, (float) b.grainTile, (float) b.grainContrast);
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uBeachB"), (float) b.octave2, (float) Math.cos(a), (float) Math.sin(a), (float) b.octave2Weight);
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uBeachC"), (float) b.wetRelief, (float) b.calmRelief, (float) b.cavity, (float) b.quality.range);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uBeachSlope"), (float) Beach.maps().slopeRms);
        double[] o = b.offsets();
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uBeachOff"), (float) o[0], (float) o[1], (float) o[2], (float) o[3]);
    }

    // ------------------------------------------------------------------ Vorführung

    /**
     * Ein Stück Strand (Vorführung): eben, die Wasserlinie bei z = −shore, nass bis 4 m davor, zertreten zwischen
     * {@code trampleFrom} und {@code trampleTo} m landwärts; dahinter die See.
     */
    public void drawPatch(GL4 gl, View v, Light l, Beach b, double shore, double trampleFrom, double trampleTo) {
        if (demo == 0) {
            demo = link(gl, "beach_demo.vert", "beach_demo.frag");
            int[] a = new int[1];
            gl.glGenVertexArrays(1, a, 0);
            vao = a[0];
            gl.glGenBuffers(1, a, 0);
            vbo = a[0];
            float far = 30000;
            float[] q = {-far, 0, -far, far, 0, -far, far, 0, far, -far, 0, -far, far, 0, far, -far, 0, far};
            gl.glBindVertexArray(vao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) q.length * 4, FloatBuffer.wrap(q), GL.GL_STATIC_DRAW);
            gl.glEnableVertexAttribArray(0);
            gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 12, 0);
            gl.glBindVertexArray(0);
        }
        gl.glUseProgram(demo);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(demo, "uViewProj"), 1, false, v.viewProj, 0);
        gl.glUniform3f(gl.glGetUniformLocation(demo, "uEye"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        double[] s = l.sunDir;
        double sl = Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
        gl.glUniform3f(gl.glGetUniformLocation(demo, "uSunDir"), (float) (s[0] / sl), (float) (s[1] / sl), (float) (s[2] / sl));
        gl.glUniform3f(gl.glGetUniformLocation(demo, "uSunColor"), (float) l.sunColor[0], (float) l.sunColor[1], (float) l.sunColor[2]);
        gl.glUniform3f(gl.glGetUniformLocation(demo, "uSkyTop"), (float) l.skyTop[0], (float) l.skyTop[1], (float) l.skyTop[2]);
        gl.glUniform3f(gl.glGetUniformLocation(demo, "uSkyHorizon"), (float) l.skyHorizon[0], (float) l.skyHorizon[1], (float) l.skyHorizon[2]);
        gl.glUniform1f(gl.glGetUniformLocation(demo, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(gl.glGetUniformLocation(demo, "uDisplay"), (float) l.display);
        gl.glUniform3f(gl.glGetUniformLocation(demo, "uZones"), (float) shore, (float) trampleFrom, (float) trampleTo);
        bind(gl, demo, b, 0, true);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 6);
        gl.glBindVertexArray(0);
    }

    public void dispose(GL4 gl) {
        if (tex != 0) gl.glDeleteTextures(1, new int[]{tex}, 0);
        if (demo != 0) {
            gl.glDeleteProgram(demo);
            gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
            gl.glDeleteBuffers(1, new int[]{vbo}, 0);
        }
        tex = demo = 0;
    }

    // ------------------------------------------------------------------ Shader

    /** Liest eine Shader-Datei aus diesem Package; {@code #include "beach.glsl"} wird eingesetzt. */
    public static String source(String file) {
        try (InputStream in = BeachGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("beach: Shader-Datei fehlt: " + file);
            String s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            StringBuilder b = new StringBuilder();
            for (String line : s.split("\r?\n", -1)) {
                if (line.trim().startsWith("#include")) {
                    String f = line.substring(line.indexOf('"') + 1, line.lastIndexOf('"'));
                    b.append(source(f.substring(f.lastIndexOf('/') + 1))).append('\n');
                } else b.append(line).append('\n');
            }
            return b.toString();
        } catch (IOException e) {
            throw new IllegalStateException("beach: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int link(GL4 gl, String vsFile, String fsFile) {
        int p = gl.glCreateProgram();
        int vs = compile(gl, GL2ES2.GL_VERTEX_SHADER, vsFile), fs = compile(gl, GL2ES2.GL_FRAGMENT_SHADER, fsFile);
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL2ES2.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        if (ok[0] == 0) throw new IllegalStateException("beach: " + vsFile + "/" + fsFile + " lässt sich nicht binden: " + log(gl, p, true));
        return p;
    }

    private static int compile(GL4 gl, int type, String file) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL2ES2.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("beach: " + file + " lässt sich nicht übersetzen: " + log(gl, sh, false));
        return sh;
    }

    private static String log(GL4 gl, int o, boolean program) {
        int[] len = new int[1];
        if (program) gl.glGetProgramiv(o, GL2ES2.GL_INFO_LOG_LENGTH, len, 0); else gl.glGetShaderiv(o, GL2ES2.GL_INFO_LOG_LENGTH, len, 0);
        if (len[0] <= 1) return "";
        byte[] b = new byte[len[0]];
        if (program) gl.glGetProgramInfoLog(o, len[0], null, 0, b, 0); else gl.glGetShaderInfoLog(o, len[0], null, 0, b, 0);
        return new String(b, StandardCharsets.UTF_8).trim();
    }
}
