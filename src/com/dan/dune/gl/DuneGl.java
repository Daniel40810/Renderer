package com.dan.dune.gl;

import com.dan.dune.Dune;
import com.dan.dune.FluxField;
import com.dan.dune.Grains;
import com.dan.dune.Ripples;
import com.dan.dune.SandBed;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Darstellung des Flugsands mit JOGL (Paketvertrag Punkt 3), seit Sylt Phase 8:
 * <ul>
 * <li><b>Boden</b> ({@code dune.vert}, {@code dune.frag}): das Gitter eines {@link FluxField} mit Windrippeln aus
 * {@link Ripples} (Profil, Kämme, Wandern), nassem Sand, Bewuchs und dem Schleier der Sandfahnen, wo Sand fliegt.
 * Wer den Boden selbst zeichnet (Sylt: das Gelände), nimmt nur die Körner.</li>
 * <li><b>Körner</b> ({@code grains.vert}, {@code grains.frag}): {@link Grains} als kurze Striche in Flugrichtung,
 * durchscheinend, ohne Tiefe zu schreiben.</li>
 * </ul>
 * Licht und Blick kommen als Zahlen; die Ausgabe ist linear und vorbelichtet, mit {@link Light#display} > 0 gleich
 * fertig für den Bildschirm (Filmkurve und Gamma, für die Vorführung).
 */
public final class DuneGl {
    /** Licht: Richtung zur Sonne, Farben (linear, vorbelichtet), Dunst. */
    public static final class Light {
        public double[] sunDir = {0.35, 0.55, -0.75}, sunColor = {2.6, 2.45, 2.2}, skyTop = {0.28, 0.42, 0.70}, skyHorizon = {0.62, 0.70, 0.78};
        public double[] fogColor = {0.62, 0.70, 0.78};
        public double fogDistance = 3000, display;
    }

    /** Blick: Matrix (Spalten zuerst), Auge. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = {0, 2, 0};
    }

    /** Fluss, bei dem der Schleier voll ist (kg/(m·s)). */
    public double fluxRef = 0.03;
    /** Länge der Striche: so viele Sekunden Flug (Bewegungsunschärfe) und ab wo die Körner verblassen (m). */
    public double streak = 1 / 60.0, grainNear = 25, grainFar = 60;

    /** Wellenlänge der Rippel im Bild (aus {@link Ripples#drawLambda} beim Hochladen) und Zeit, über die das Wandern überblendet wird (s). */
    public double rippleLambda = 0.1, flowPeriod = 20;

    private int ground, grains, gVao, gVbo, gIbo, gCount, gN = -1, kVao, kVbo, kCap;
    private float[] gData, kData;
    private String info = "";

    public String info() { return info; }

    public void init(GL4 gl) {
        ground = link(gl, "dune.vert", "dune.frag");
        grains = link(gl, "grains.vert", "grains.frag");
        int[] v = new int[2];
        gl.glGenVertexArrays(2, v, 0);
        gVao = v[0];
        kVao = v[1];
        int[] b = new int[3];
        gl.glGenBuffers(3, b, 0);
        gVbo = b[0];
        gIbo = b[1];
        kVbo = b[2];
        gl.glBindVertexArray(gVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, gVbo);
        int stride = 11 * 4;
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, stride, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 4, GL.GL_FLOAT, false, stride, 12);
        gl.glEnableVertexAttribArray(2);
        gl.glVertexAttribPointer(2, 4, GL.GL_FLOAT, false, stride, 28);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, gIbo);
        gl.glBindVertexArray(kVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, kVbo);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 20, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 2, GL.GL_FLOAT, false, 20, 12);
        gl.glBindVertexArray(0);
        info = "Flugsand: Boden mit Rippeln (dune.vert/frag), Körner als Striche (grains.vert/frag)";
    }

    /** Boden aus Feld und Rippeln hochladen (je Bild, die Rippel wandern). */
    public void upload(GL4 gl, FluxField f, Ripples r) {
        int n = f.ns * f.nn;
        if (gData == null || gData.length != n * 11) gData = new float[n * 11];
        float[] a = gData;
        for (int j = 0; j < f.nn; j++)
            for (int i = 0; i < f.ns; i++) {
                int k = j * f.ns + i, o = k * 11;
                double[] p = f.world(i, j);
                boolean shadow = f.h[k] < f.env[k] - 0.02;
                a[o] = (float) p[0]; a[o + 1] = f.h[k]; a[o + 2] = (float) p[1];
                a[o + 3] = (float) (f.q[k] / fluxRef); a[o + 4] = f.moist[k]; a[o + 5] = f.cover[k]; a[o + 6] = shadow ? 1 : 0;
                a[o + 7] = r != null ? r.lambda[k] : (float) f.dune.rippleLambda0;
                a[o + 8] = r != null ? r.height[k] : 0;
                a[o + 9] = r != null ? r.speed[k] : 0;
                a[o + 10] = (float) (f.dep[k] / f.dune.bulkDensity * 3.6e3);
            }
        if (r != null) rippleLambda = r.drawLambda();
        gl.glBindVertexArray(gVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, gVbo);
        if (gN != n) {
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) a.length * 4, FloatBuffer.wrap(a), GL.GL_DYNAMIC_DRAW);
            int[] idx = new int[(f.ns - 1) * (f.nn - 1) * 6];
            int c = 0;
            for (int j = 0; j < f.nn - 1; j++)
                for (int i = 0; i < f.ns - 1; i++) {
                    int v = j * f.ns + i;
                    idx[c++] = v; idx[c++] = v + 1; idx[c++] = v + f.ns;
                    idx[c++] = v + 1; idx[c++] = v + f.ns + 1; idx[c++] = v + f.ns;
                }
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, gIbo);
            gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) c * 4, IntBuffer.wrap(idx), GL.GL_STATIC_DRAW);
            gCount = c;
            gN = n;
        } else gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, (long) a.length * 4, FloatBuffer.wrap(a));
        gl.glBindVertexArray(0);
    }

    /** Boden zeichnen (nach {@link #upload}). */
    public void drawGround(GL4 gl, View v, Light l, FluxField f, double time) {
        if (gCount == 0) return;
        gl.glUseProgram(ground);
        common(gl, ground, v, l);
        gl.glUniform2f(loc(gl, ground, "uWindTo"), (float) f.tx, (float) f.tz);
        gl.glUniform1f(loc(gl, ground, "uTime"), (float) time);
        gl.glUniform1f(loc(gl, ground, "uLambda"), (float) rippleLambda);
        gl.glUniform1f(loc(gl, ground, "uFlowPeriod"), (float) flowPeriod);
        Dune d = f.dune;
        gl.glUniform1f(loc(gl, ground, "uStreamLen"), (float) d.streamerLength);
        gl.glUniform1f(loc(gl, ground, "uStreamWid"), (float) d.streamerWidth);
        gl.glUniform1f(loc(gl, ground, "uStreamSpeed"), (float) (0.8 * d.windAt(d.ustarAt(f.cx, f.cz), 1.0)));
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glBindVertexArray(gVao);
        gl.glDrawElements(GL.GL_TRIANGLES, gCount, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
    }

    /** Körner zeichnen: durchscheinend, mit Tiefentest, ohne Tiefe zu schreiben. */
    public void drawGrains(GL4 gl, View v, Light l, Grains g) {
        int n = g.count;
        if (n == 0) return;
        if (kData == null || kData.length < n * 10) kData = new float[Math.max(n, 1024) * 10 * 3 / 2];
        float[] a = kData;
        double sk = streak;
        for (int k = 0; k < n; k++) {
            int o = k * 10;
            float kind = g.kind[k];
            a[o] = g.x[k]; a[o + 1] = g.y[k]; a[o + 2] = g.z[k]; a[o + 3] = 1; a[o + 4] = kind;
            // Schweif mindestens 4 mm, damit ruhende Körner sichtbar bleiben
            double vx = g.vx[k], vy = g.vy[k], vz = g.vz[k], sp = Math.sqrt(vx * vx + vy * vy + vz * vz), len = Math.max(sk * sp, 0.004);
            double f = sp > 1e-6 ? len / sp : 0;
            a[o + 5] = (float) (g.x[k] - vx * f); a[o + 6] = (float) (g.y[k] - vy * f); a[o + 7] = (float) (g.z[k] - vz * f); a[o + 8] = 0; a[o + 9] = kind;
        }
        gl.glUseProgram(grains);
        common(gl, grains, v, l);
        gl.glUniform1f(loc(gl, grains, "uNear"), (float) grainNear);
        gl.glUniform1f(loc(gl, grains, "uFar"), (float) grainFar);
        gl.glBindVertexArray(kVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, kVbo);
        if (kCap < a.length) {
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) a.length * 4, null, GL4.GL_STREAM_DRAW);
            kCap = a.length;
        }
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, (long) n * 10 * 4, FloatBuffer.wrap(a, 0, n * 10));
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDrawArrays(GL.GL_LINES, 0, n * 2);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glBindVertexArray(0);
    }

    private int fVao, fVbo, fCap;

    /**
     * Seit Phase 9: die Sandfangzäune eines {@link SandBed} als Stecken aus Busch (Striche vom Sand bis zur Oberkante, je
     * 12 cm einer, leicht schräg), so weit sie noch aus dem Sand ragen.
     */
    public void drawFences(GL4 gl, View v, Light l, SandBed b) {
        java.util.List<Float> pts = new java.util.ArrayList<>();
        for (SandBed.Fence fe : b.fences) {
            double len = Math.hypot(fe.x1() - fe.x0(), fe.z1() - fe.z0());
            int n = (int) Math.ceil(len / 0.12);
            for (int s = 0; s <= n; s++) {
                double t = s / (double) Math.max(1, n), x = fe.x0() + (fe.x1() - fe.x0()) * t, z = fe.z0() + (fe.z1() - fe.z0()) * t;
                int k = b.nearest(x, z);
                if (k < 0 || Float.isNaN(b.fenceTop[k])) continue;
                double g = b.height(x, z), top = b.fenceTop[k] - 0.15 * Math.abs(Math.sin(s * 12.9898) * 43758.5453 % 1);
                if (top <= g + 0.02) continue;
                double lean = 0.06 * Math.sin(s * 4.1);
                float[] q = {(float) x, (float) g, (float) z, 0, 2, (float) (x + lean), (float) top, (float) (z + lean * 0.5), 1, 2};
                for (float f : q) pts.add(f);
            }
        }
        if (pts.isEmpty()) return;
        float[] a = new float[pts.size()];
        for (int i = 0; i < a.length; i++) a[i] = pts.get(i);
        if (fVao == 0) {
            int[] o = new int[1];
            gl.glGenVertexArrays(1, o, 0);
            fVao = o[0];
            gl.glGenBuffers(1, o, 0);
            fVbo = o[0];
            gl.glBindVertexArray(fVao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, fVbo);
            gl.glEnableVertexAttribArray(0);
            gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 20, 0);
            gl.glEnableVertexAttribArray(1);
            gl.glVertexAttribPointer(1, 2, GL.GL_FLOAT, false, 20, 12);
        }
        gl.glBindVertexArray(fVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, fVbo);
        if (fCap < a.length) { gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) a.length * 4, null, GL4.GL_DYNAMIC_DRAW); fCap = a.length; }
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, (long) a.length * 4, FloatBuffer.wrap(a));
        gl.glUseProgram(grains);
        common(gl, grains, v, l);
        gl.glUniform1f(loc(gl, grains, "uNear"), (float) grainNear);
        gl.glUniform1f(loc(gl, grains, "uFar"), (float) grainFar);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDrawArrays(GL.GL_LINES, 0, a.length / 5);
        gl.glDisable(GL.GL_BLEND);
        gl.glBindVertexArray(0);
    }

    /** Boden und Körner. */
    public void draw(GL4 gl, View v, Light l, FluxField f, Ripples r, Grains g, double time) {
        upload(gl, f, r);
        drawGround(gl, v, l, f, time);
        if (g != null) drawGrains(gl, v, l, g);
    }

    private void common(GL4 gl, int p, View v, Light l) {
        gl.glUniformMatrix4fv(loc(gl, p, "uViewProj"), 1, false, v.viewProj, 0);
        gl.glUniform3f(loc(gl, p, "uEye"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        double[] s = l.sunDir;
        gl.glUniform3f(loc(gl, p, "uSunDir"), (float) s[0], (float) s[1], (float) s[2]);
        gl.glUniform3f(loc(gl, p, "uSunColor"), (float) l.sunColor[0], (float) l.sunColor[1], (float) l.sunColor[2]);
        gl.glUniform3f(loc(gl, p, "uSkyTop"), (float) l.skyTop[0], (float) l.skyTop[1], (float) l.skyTop[2]);
        gl.glUniform3f(loc(gl, p, "uSkyHorizon"), (float) l.skyHorizon[0], (float) l.skyHorizon[1], (float) l.skyHorizon[2]);
        gl.glUniform3f(loc(gl, p, "uFogColor"), (float) l.fogColor[0], (float) l.fogColor[1], (float) l.fogColor[2]);
        gl.glUniform1f(loc(gl, p, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(loc(gl, p, "uDisplay"), (float) l.display);
    }

    private static int loc(GL4 gl, int p, String name) { return gl.glGetUniformLocation(p, name); }

    public void dispose(GL4 gl) {
        if (ground != 0) gl.glDeleteProgram(ground);
        if (grains != 0) gl.glDeleteProgram(grains);
        gl.glDeleteVertexArrays(2, new int[]{gVao, kVao}, 0);
        gl.glDeleteBuffers(3, new int[]{gVbo, gIbo, kVbo}, 0);
        if (fVao != 0) { gl.glDeleteVertexArrays(1, new int[]{fVao}, 0); gl.glDeleteBuffers(1, new int[]{fVbo}, 0); fVao = fVbo = 0; }
        ground = grains = 0;
    }

    /** „Flugsand: 40 800 Ecken, 18 400 Körner“ */
    public String describe(FluxField f, Grains g) {
        return String.format(Locale.GERMAN, "Flugsand: %d Ecken, %d Körner", f.ns * f.nn, g == null ? 0 : g.count);
    }

    // ------------------------------------------------------------------ Shader

    /** Liest eine Shader-Datei aus diesem Package (Ressource neben der Klasse). */
    public static String source(String file) {
        try (InputStream in = DuneGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("dune: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("dune: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int link(GL4 gl, String vsFile, String fsFile) {
        int p = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, vsFile), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, fsFile);
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        if (ok[0] == 0) throw new IllegalStateException("dune: " + vsFile + "/" + fsFile + " lässt sich nicht binden: " + log(gl, p, true));
        return p;
    }

    private static int compile(GL4 gl, int type, String file) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("dune: " + file + " lässt sich nicht übersetzen: " + log(gl, sh, false));
        return sh;
    }

    private static String log(GL4 gl, int o, boolean program) {
        int[] len = new int[1];
        if (program) gl.glGetProgramiv(o, GL4.GL_INFO_LOG_LENGTH, len, 0); else gl.glGetShaderiv(o, GL4.GL_INFO_LOG_LENGTH, len, 0);
        if (len[0] <= 1) return "";
        byte[] b = new byte[len[0]];
        if (program) gl.glGetProgramInfoLog(o, len[0], null, 0, b, 0); else gl.glGetShaderInfoLog(o, len[0], null, 0, b, 0);
        return new String(b, StandardCharsets.UTF_8).trim();
    }
}
