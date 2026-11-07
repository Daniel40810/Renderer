package com.dan.dustcloud.gl;

import com.dan.dustcloud.DustCloud;
import com.dan.dustcloud.Plume;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Darstellung von {@link DustCloud} und {@link Plume} auf der Grafikkarte (aus LituyaBay1958, Werkbuch Welle W3, W3c,
 * W4; OpenGL 4.3): ein Strahlengang durch die Hüllen der Staubschicht und der Fahne, in halber Auflösung und mit der Tiefe
 * hochgerechnet ({@code upsample.frag}; in Lituya sparte das 17–28 ms je Bild ohne Saum an Kanten). Über ein gebundenes
 * HDR-Ziel gelegt: Ziel · T + L.
 * <p>
 * Ein Wirt mit eigenem Strahlengang (mehr Medien im selben Strahl, eigenes Licht) nimmt nur die Bausteine
 * {@code dust.glsl} ({@code dustLayerDensity}, {@code dustPlumeDensity}, {@code dustPhaseHG}), {@link #noiseTexture},
 * {@link #bindPlume} und {@code upsample.frag}, wie LituyaBay1958.
 */
public final class DustGl {
    /** Blick: Matrizen (Spalten zuerst), Auge, Blickrichtung, Nah- und Fernebene, umgekehrte Tiefe, Bildgröße. */
    public static final class View {
        public float[] invViewProj;
        public double[] eye = {0, 0, 0}, forward = {0, 0, -1};
        public double near = 0.5, far = 100_000;
        public boolean reversedZ = true;
        public int width = 1280, height = 720;
    }

    /** Licht, vorbelichtet: Sonne (Richtung, Farbe am Ort), Himmel im Zenit, Dunst (Farbe, Entfernung). */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {2.6, 2.5, 2.3}, skyColor = {0.25, 0.32, 0.45}, hazeColor = {0.55, 0.6, 0.68};
        public double fogDistance = 40_000;
    }

    /** Schritte des Strahls, halbe Auflösung, Pegel (Fuß der Fahne). */
    public int steps = 32;
    public boolean half = true;
    public double level = 0;

    private int progDust, progUp, vao, noiseTex, topTex, groundTex, halfTex, halfFbo, halfW, halfH, frame;
    private double[] geo;
    private double yLo, yHi;
    private boolean ready;
    private final Map<String, Integer> locs = new HashMap<>();

    public void init(GL4 gl) {
        progDust = program(gl, "fullscreen.vert", "dust.frag");
        progUp = program(gl, "fullscreen.vert", "upsample.frag");
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        noiseTex = noiseTexture(gl);
        ready = true;
    }

    public boolean ready() { return ready; }

    /** Den Stand der Wolke hochladen (Oberkante und optische Dicke je Zelle, Grund); einmal je Bild der Rechnung. */
    public void upload(GL4 gl, DustCloud c) {
        int nx = c.nx, nz = c.nz;
        FloatBuffer to = Buffers.newDirectFloatBuffer(nx * nz * 2), gr = Buffers.newDirectFloatBuffer(nx * nz);
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                double x = c.cx(i), z = c.cz(j);
                double g = c.ground(x, z), t = c.top(x, z), od = c.opticalDepth(x, z);
                if (Double.isNaN(g)) g = 0;
                if (Double.isNaN(t)) { t = g; od = 0; }
                to.put((float) t).put((float) od);
                gr.put((float) g);
                lo = Math.min(lo, g);
                if (od > 1e-3) hi = Math.max(hi, t + 10);
            }
        to.flip();
        gr.flip();
        if (topTex == 0) { topTex = tex2d(gl); groundTex = tex2d(gl); }
        gl.glBindTexture(GL.GL_TEXTURE_2D, topTex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_RG32F, nx, nz, 0, GL4.GL_RG, GL.GL_FLOAT, to);
        gl.glBindTexture(GL.GL_TEXTURE_2D, groundTex);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R32F, nx, nz, 0, GL4.GL_RED, GL.GL_FLOAT, gr);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        geo = new double[]{c.x0, c.z0, c.dx, nx, nz};
        yLo = Math.max(-200, lo);
        yHi = hi > lo ? hi : lo;
    }

    /**
     * Wolke (nach {@link #upload}) und Fahne (null: keine; age Sekunden seit dem Abbruch) über das gebundene HDR-Ziel legen.
     * depthTex: Tiefe der Szene (wird gelesen, nicht geschrieben); time: Laufzeit (s) für das Rauschen.
     */
    public void draw(GL4 gl, View v, Light l, int depthTex, boolean layer, Plume plume, double age, double time) {
        if (!ready) return;
        boolean lay = layer && topTex != 0 && yHi > yLo;
        boolean pl = plume != null && plume.active(age);
        if (!lay && !pl) return;
        int[] prevFbo = new int[1], vp = new int[4];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prevFbo, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        int p = progDust;
        gl.glUseProgram(p);
        gl.glUniformMatrix4fv(loc(gl, p, "uInvVP"), 1, false, v.invViewProj, 0);
        u3(gl, p, "uCam", v.eye);
        u3(gl, p, "uFwd", v.forward);
        u3(gl, p, "uSun", l.sunDir);
        u3(gl, p, "uSunColor", l.sunColor);
        u3(gl, p, "uSkyColor", l.skyColor);
        u3(gl, p, "uHazeColor", l.hazeColor);
        gl.glUniform1f(loc(gl, p, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(loc(gl, p, "uLevel"), (float) level);
        gl.glUniform1f(loc(gl, p, "uTime"), (float) (time % 10_000));
        gl.glUniform1f(loc(gl, p, "uNear"), (float) v.near);
        gl.glUniform1f(loc(gl, p, "uFar"), (float) v.far);
        gl.glUniform1i(loc(gl, p, "uRev"), v.reversedZ ? 1 : 0);
        gl.glUniform1i(loc(gl, p, "uSteps"), steps);
        gl.glUniform1f(loc(gl, p, "uFrame"), (float) (frame++ & 63));
        gl.glUniform1i(loc(gl, p, "uLayerOn"), lay ? 1 : 0);
        gl.glUniform1i(loc(gl, p, "uPlumeOn"), pl ? 1 : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        gl.glUniform1i(loc(gl, p, "uDepth"), 0);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, topTex);
        gl.glUniform1i(loc(gl, p, "uDcTop"), 1);
        gl.glActiveTexture(GL.GL_TEXTURE2);
        gl.glBindTexture(GL.GL_TEXTURE_2D, groundTex);
        gl.glUniform1i(loc(gl, p, "uDcGround"), 2);
        gl.glActiveTexture(GL.GL_TEXTURE3);
        gl.glBindTexture(GL4.GL_TEXTURE_3D, noiseTex);
        gl.glUniform1i(loc(gl, p, "uDcNoise"), 3);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        if (lay) {
            gl.glUniform4f(loc(gl, p, "uDcGeo"), (float) geo[0], (float) geo[1], (float) geo[2], 0f);
            gl.glUniform2f(loc(gl, p, "uDcSize"), (float) geo[3], (float) geo[4]);
            gl.glUniform2f(loc(gl, p, "uDcY"), (float) yLo, (float) yHi);
        }
        if (pl) bindPlume(gl, p, plume, age);
        gl.glBindVertexArray(vao);
        gl.glDisable(GL.GL_DEPTH_TEST);
        if (!half) {
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_SRC_ALPHA);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            gl.glDisable(GL.GL_BLEND);
        } else {
            int hw = Math.max(16, (vp[2] + 1) / 2), hh = Math.max(16, (vp[3] + 1) / 2);
            if (hw != halfW || hh != halfH || halfFbo == 0) resizeHalf(gl, hw, hh);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, halfFbo);
            gl.glViewport(0, 0, hw, hh);
            gl.glDisable(GL.GL_BLEND);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prevFbo[0]);
            gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
            upsample(gl, halfTex, hw, hh, depthTex, v.near, v.far, v.reversedZ);
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    /** Ein Bild (L, T) halber Größe mit der Tiefe über das gebundene Ziel legen (Mischung ONE, SRC_ALPHA). */
    public void upsample(GL4 gl, int halfTexture, int hw, int hh, int depthTex, double near, double far, boolean reversed) {
        int p = progUp;
        gl.glUseProgram(p);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, halfTexture);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(loc(gl, p, "uHalb"), 0);
        gl.glUniform1i(loc(gl, p, "uDepth"), 1);
        gl.glUniform2f(loc(gl, p, "uHalbPx"), hw, hh);
        gl.glUniform1f(loc(gl, p, "uNear"), (float) near);
        gl.glUniform1f(loc(gl, p, "uFar"), (float) far);
        gl.glUniform1i(loc(gl, p, "uRev"), reversed ? 1 : 0);
        gl.glBindVertexArray(vao);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_SRC_ALPHA);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glDisable(GL.GL_BLEND);
    }

    private void resizeHalf(GL4 gl, int w, int h) {
        if (halfFbo != 0) gl.glDeleteFramebuffers(1, new int[]{halfFbo}, 0);
        if (halfTex != 0) gl.glDeleteTextures(1, new int[]{halfTex}, 0);
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        halfTex = t[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, halfTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glGenFramebuffers(1, t, 0);
        halfFbo = t[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, halfFbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, halfTex, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        halfW = w;
        halfH = h;
    }

    /** Die Uniforms der Fahne ({@code uDcPlume}, {@code uDcPlumeR} aus dust.glsl) für das gebundene Programm. */
    public static void bindPlume(GL4 gl, int prog, Plume plume, double age) {
        float[][] u = plume.uniforms(age);
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uDcPlume"), u[0][0], u[0][1], u[0][2], u[0][3]);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uDcPlumeR"), u[1][0], u[1][1], u[1][2]);
    }

    /**
     * Kachelbares Wertrauschen 64³ (R8, trilinear mit Mip-Stufen, wiederholt) für {@code uDcNoise}: Zufall mit Samen 1958,
     * einmal mit einem 3er-Kasten geglättet, auf 0..255 gestreckt (wie in LituyaBay1958 seit W3).
     */
    public static int noiseTexture(GL4 gl) {
        byte[] v = com.dan.dustcloud.Noise.tile(64, 1958);
        ByteBuffer b = Buffers.newDirectByteBuffer(v.length);
        b.put(v).flip();
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        int n = 64;
        gl.glBindTexture(GL4.GL_TEXTURE_3D, t[0]);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage3D(GL4.GL_TEXTURE_3D, 0, GL.GL_R8, n, n, n, 0, GL4.GL_RED, GL.GL_UNSIGNED_BYTE, b);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glGenerateMipmap(GL4.GL_TEXTURE_3D);
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        for (int pn : new int[]{GL.GL_TEXTURE_WRAP_S, GL.GL_TEXTURE_WRAP_T, GL4.GL_TEXTURE_WRAP_R}) gl.glTexParameteri(GL4.GL_TEXTURE_3D, pn, GL.GL_REPEAT);
        gl.glBindTexture(GL4.GL_TEXTURE_3D, 0);
        return t[0];
    }

    public void dispose(GL4 gl) {
        for (int p : new int[]{progDust, progUp}) if (p != 0) gl.glDeleteProgram(p);
        for (int t : new int[]{noiseTex, topTex, groundTex, halfTex}) if (t != 0) gl.glDeleteTextures(1, new int[]{t}, 0);
        if (halfFbo != 0) gl.glDeleteFramebuffers(1, new int[]{halfFbo}, 0);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        progDust = progUp = noiseTex = topTex = groundTex = halfTex = halfFbo = vao = 0;
        locs.clear();
        ready = false;
    }

    // ------------------------------------------------------------------ Helfer

    private static int tex2d(GL4 gl) {
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        return t[0];
    }

    private void u3(GL4 gl, int p, String n, double[] v) { gl.glUniform3f(loc(gl, p, n), (float) v[0], (float) v[1], (float) v[2]); }

    private int loc(GL4 gl, int prog, String name) { return locs.computeIfAbsent(prog + ":" + name, k -> gl.glGetUniformLocation(prog, name)); }

    /** Quelltext mit eingesetzten Bausteinen ({@code #include "datei"} aus diesem Package). */
    public static String source(String file) {
        StringBuilder sb = new StringBuilder("#version 430 core\n");
        expand(file, sb, new HashSet<>());
        return sb.toString();
    }

    private static void expand(String file, StringBuilder sb, Set<String> seen) {
        if (!seen.add(file)) return;
        for (String l : read(file).split("\r?\n", -1)) {
            String t = l.trim();
            if (t.startsWith("#include")) expand(t.substring(t.indexOf('"') + 1, t.lastIndexOf('"')), sb, seen);
            else sb.append(l).append('\n');
        }
    }

    static String read(String file) {
        try (InputStream in = DustGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("dustcloud: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("dustcloud: " + file + ": " + e.getMessage(), e);
        }
    }

    static int program(GL4 gl, String vert, String frag) {
        int prog = gl.glCreateProgram();
        int[] sh = new int[2];
        String[] f = {vert, frag};
        for (int i = 0; i < 2; i++) {
            sh[i] = gl.glCreateShader(i == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(sh[i], 1, new String[]{source(f[i])}, null, 0);
            gl.glCompileShader(sh[i]);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh[i], GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                int[] len = new int[1];
                gl.glGetShaderiv(sh[i], GL4.GL_INFO_LOG_LENGTH, len, 0);
                byte[] log = new byte[Math.max(1, len[0])];
                gl.glGetShaderInfoLog(sh[i], log.length, null, 0, log, 0);
                throw new IllegalStateException("dustcloud: " + f[i] + " lässt sich nicht übersetzen:\n" + new String(log, StandardCharsets.UTF_8));
            }
            gl.glAttachShader(prog, sh[i]);
        }
        gl.glLinkProgram(prog);
        int[] ok = new int[1];
        gl.glGetProgramiv(prog, GL4.GL_LINK_STATUS, ok, 0);
        for (int s : sh) gl.glDeleteShader(s);
        if (ok[0] == 0) throw new IllegalStateException("dustcloud: " + vert + " + " + frag + " lässt sich nicht binden");
        return prog;
    }
}
