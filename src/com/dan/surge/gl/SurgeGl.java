package com.dan.surge.gl;

import com.dan.surge.FloodMap;
import com.dan.surge.Lip;
import com.dan.surge.Noise;
import com.dan.surge.Veil;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Die Front auf der Grafikkarte (OpenGL 4.3): die Lippe als Netz mit ihren Tropfen ({@link #drawLip}) und die Schleier am
 * Auflauf ({@link #setVeil}, {@link #drawVeil}); dazu die Flutkarte als Textur ({@link #floodTexture}, {@link #bindFlood})
 * und das 3D-Rauschen für die Gischt im Strahlengang des Wirts ({@link #noiseTexture}).
 * <p>
 * <b>Licht.</b> Die Shader rufen nur den Lichtvertrag aus {@code light.glsl} (lightSun, lightSky, lightSkyDir, lightHaze,
 * lightAerial, lightIrradiance; uVP, uCam, uSun, uTime, uLevel, uFrame). Ohne Wirt gilt die Vorgabe mit festen Farben
 * ({@link Light}). Ein Wirt mit eigenem Licht setzt vor {@link #init} {@link #hostLight} (GLSL, ersetzt light.glsl) und
 * {@link #hostUniforms} (setzt je Programm seine Uniforms, das Programm ist gebunden); dann setzt dieses Package nur
 * uCurvature, uPxPerM und uSgEvent. Texturstellen 0–3 gehören dem Package nicht: es bindet keine Texturen.
 */
public final class SurgeGl {
    /** Blick: Matrix (Spalten zuerst), Auge, umgekehrte Tiefe, Bildpunkte je Meter in 1 m Abstand, Erdkrümmung. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = {0, 0, 0};
        public boolean reversedZ = true;
        public double pxPerM = 1000;
        /** Absenkung r² · curvature (etwa 1/(2R)); 0 ohne. */
        public double curvature;
    }

    /** Licht der Vorgabe (vorbelichtet): Sonne, Himmel im Zenit und an der Kimm, Dunst. */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {2.6, 2.5, 2.3}, zenith = {0.18, 0.28, 0.45}, horizon = {0.45, 0.5, 0.58}, haze = {0.5, 0.56, 0.64};
        public double fogDistance = 40_000, time, level, frame;
    }

    /** Wirt: GLSL für light.glsl (null: Vorgabe) und wer dessen Uniforms setzt. */
    public String hostLight;
    public IntConsumer hostUniforms;

    private int progLip, progDrops, progVeil;
    private int vao, vbo, cap, tvao, tvbo, tcap, veilVao, veilVbo, veilN;
    private double veilT0 = Double.MAX_VALUE, veilT1 = -Double.MAX_VALUE;
    private boolean ready;
    private final Map<String, Integer> locs = new HashMap<>();
    public String info = "Front: –";

    public void init(GL4 gl) {
        progLip = program(gl, "lip.vert", "lip.frag");
        progDrops = program(gl, "drops.vert", "drops.frag");
        progVeil = program(gl, "veil.vert", "veil.frag");
        ready = true;
    }

    public boolean ready() { return ready; }

    /** Kennungen der Programme (für den Wirt, etwa Prüfungen). */
    public int lipProgram() { return progLip; }
    public int dropsProgram() { return progDrops; }
    public int veilProgram() { return progVeil; }

    /** Licht für ein gebundenes Programm: vom Wirt oder die Vorgabe. */
    private void light(GL4 gl, int p, View v, Light l) {
        if (hostUniforms != null) { hostUniforms.accept(p); return; }
        if (l == null) l = new Light();
        gl.glUniformMatrix4fv(loc(gl, p, "uVP"), 1, false, v.viewProj, 0);
        u3(gl, p, "uCam", v.eye);
        u3(gl, p, "uSun", l.sunDir);
        gl.glUniform1f(loc(gl, p, "uTime"), (float) (l.time % 10_000));
        gl.glUniform1f(loc(gl, p, "uLevel"), (float) l.level);
        gl.glUniform1f(loc(gl, p, "uFrame"), (float) l.frame);
        u3(gl, p, "uLightSun", l.sunColor);
        u3(gl, p, "uLightZenith", l.zenith);
        u3(gl, p, "uLightHorizon", l.horizon);
        u3(gl, p, "uLightHaze", l.haze);
        gl.glUniform1f(loc(gl, p, "uLightFog"), (float) l.fogDistance);
    }

    /**
     * Die Lippe (nach {@link Lip#build}) und ihre Tropfen in das gebundene Ziel (HDR mit Tiefe; Tiefentest und Mischung
     * wie eingestellt): Netz als Dreiecke, Tropfen als Punkte mit Größe.
     */
    public void drawLip(GL4 gl, Lip lip, View v, Light l) {
        int n = lip.vertices;
        if (!ready || n == 0) return;
        if (vao == 0) {
            int[] b = new int[1];
            gl.glGenVertexArrays(1, b, 0);
            vao = b[0];
            gl.glGenBuffers(1, b, 0);
            vbo = b[0];
            gl.glBindVertexArray(vao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
            int[] size = {3, 3, 1, 1, 2, 1, 1, 2};
            long off = 0;
            for (int a = 0; a < size.length; a++) {
                gl.glEnableVertexAttribArray(a);
                gl.glVertexAttribPointer(a, size[a], GL.GL_FLOAT, false, Lip.FL * 4, off);
                off += size[a] * 4L;
            }
        }
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        long bytes = (long) lip.meshFloats() * 4;
        if (bytes > cap) { gl.glBufferData(GL.GL_ARRAY_BUFFER, lip.mesh.length * 4L, null, GL.GL_DYNAMIC_DRAW); cap = lip.mesh.length * 4; }
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, bytes, direct(0, lip.mesh, lip.meshFloats()));
        gl.glUseProgram(progLip);
        light(gl, progLip, v, l);
        gl.glUniform1f(loc(gl, progLip, "uCurvature"), (float) v.curvature);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, n);
        gl.glBindVertexArray(0);
        // die Tropfen als weiche Punkte (deckend mit gestreuter Durchsicht, die Kantenglättung mittelt sie; so schreiben
        // sie Tiefe, und Staub und Gischt eines Strahlengangs liegen richtig davor)
        int m = lip.dropCount;
        if (m == 0) return;
        if (tvao == 0) {
            int[] b = new int[1];
            gl.glGenVertexArrays(1, b, 0);
            tvao = b[0];
            gl.glGenBuffers(1, b, 0);
            tvbo = b[0];
            gl.glBindVertexArray(tvao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, tvbo);
            int[] size = {3, 1, 1, 1};
            long off = 0;
            for (int a = 0; a < size.length; a++) {
                gl.glEnableVertexAttribArray(a);
                gl.glVertexAttribPointer(a, size[a], GL.GL_FLOAT, false, Lip.FT * 4, off);
                off += size[a] * 4L;
            }
        }
        gl.glBindVertexArray(tvao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, tvbo);
        long tb = (long) lip.dropFloats() * 4;
        if (tb > tcap) { gl.glBufferData(GL.GL_ARRAY_BUFFER, lip.drops.length * 4L, null, GL.GL_DYNAMIC_DRAW); tcap = lip.drops.length * 4; }
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, tb, direct(1, lip.drops, lip.dropFloats()));
        gl.glUseProgram(progDrops);
        light(gl, progDrops, v, l);
        gl.glUniform1f(loc(gl, progDrops, "uCurvature"), (float) v.curvature);
        gl.glUniform1f(loc(gl, progDrops, "uPxPerM"), (float) v.pxPerM);
        gl.glEnable(GL4.GL_PROGRAM_POINT_SIZE);
        gl.glDrawArrays(GL.GL_POINTS, 0, m);
        gl.glDisable(GL4.GL_PROGRAM_POINT_SIZE);
        gl.glBindVertexArray(0);
    }

    private final FloatBuffer[] direct = new FloatBuffer[2];

    /** Die ersten n Werte von a in einem direkten Puffer (JOGL liest direkte Puffer ohne Umweg). */
    private FloatBuffer direct(int k, float[] a, int n) {
        if (direct[k] == null || direct[k].capacity() < a.length) direct[k] = Buffers.newDirectFloatBuffer(a.length);
        FloatBuffer b = direct[k];
        b.clear();
        b.put(a, 0, n).flip();
        return b;
    }

    /** Die Fäden der Schleier hochladen (aus {@link Veil#threads}; einmal je Lauf). */
    public void setVeil(GL4 gl, float[] f) {
        veilN = f.length / Veil.FL;
        veilT0 = Double.MAX_VALUE;
        veilT1 = -Double.MAX_VALUE;
        for (int k = 0; k < veilN; k++) { veilT0 = Math.min(veilT0, f[k * Veil.FL + 3]); veilT1 = Math.max(veilT1, f[k * Veil.FL + 3]); }
        if (veilVao == 0) {
            int[] b = new int[1];
            gl.glGenVertexArrays(1, b, 0); veilVao = b[0];
            gl.glGenBuffers(1, b, 0); veilVbo = b[0];
        }
        gl.glBindVertexArray(veilVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, veilVbo);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(Math.max(1, f.length));
        fb.put(f).flip();
        gl.glBufferData(GL.GL_ARRAY_BUFFER, Math.max(4L, f.length * 4L), fb, GL.GL_STATIC_DRAW);
        for (int a = 0; a < 2; a++) {
            gl.glEnableVertexAttribArray(a);
            gl.glVertexAttribPointer(a, 4, GL.GL_FLOAT, false, Veil.FL * 4, a * 16L);
            gl.glVertexAttribDivisor(a, 1);
        }
        gl.glBindVertexArray(0);
        info = String.format(Locale.GERMAN, "Schleier: %,d Fäden, Abwurf %.0f–%.0f s", veilN, veilT0, veilT1);
    }

    /** Zahl der Fäden und ihre Zeiten des Abwurfs (s). */
    public int veilThreads() { return veilN; }
    public double veilFirst() { return veilT0; }
    public double veilLast() { return veilT1; }

    /**
     * Die Schleier zur Ereigniszeit t in das gebundene HDR-Ziel mit Tiefe (Tiefe nur lesen, weich gemischt, vormultipliziert).
     */
    public void drawVeil(GL4 gl, View v, Light l, double t) {
        if (!ready || veilN == 0 || t < veilT0 - 1 || t > veilT1 + 14) return;
        gl.glUseProgram(progVeil);
        light(gl, progVeil, v, l);
        gl.glUniform1f(loc(gl, progVeil, "uSgEvent"), (float) t);
        gl.glUniform1f(loc(gl, progVeil, "uCurvature"), (float) v.curvature);
        gl.glUniform1f(loc(gl, progVeil, "uPxPerM"), (float) v.pxPerM);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(v.reversedZ ? GL.GL_GEQUAL : GL.GL_LEQUAL);
        gl.glDepthMask(false);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(veilVao);
        gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, veilN);
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_DEPTH_TEST);
    }

    // ------------------------------------------------------------------ Flutkarte und Rauschen für den Wirt

    /** Die Flutkarte als Textur RG32F (linear, Rand fest); tex 0: neu anlegen, sonst diese Kennung neu füllen. */
    public static int floodTexture(GL4 gl, FloodMap f, int tex) {
        FloatBuffer b = Buffers.newDirectFloatBuffer(f.textureData());
        if (tex == 0) {
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            tex = t[0];
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_RG32F, f.nx, f.nz, 0, GL4.GL_RG, GL.GL_FLOAT, b);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return tex;
    }

    /**
     * Die Uniforms von surge_flood.glsl für das gebundene Programm prog: an, Textur auf die Stelle unit (danach ist Stelle 0
     * aktiv), Rechteck ({@link FloodMap#rect}), Ereigniszeit.
     */
    public static void bindFlood(GL4 gl, int prog, boolean on, int unit, int tex, double[] rect, double eventTime) {
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uSgFloodOn"), on ? 1 : 0);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uSgFlood"), unit);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uSgEvent"), (float) eventTime);
        if (!on) return;
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uSgFloodRect"), (float) rect[0], (float) rect[1], (float) rect[2], (float) rect[3]);
    }

    /** Kachelbares Wertrauschen 64³ (R8, trilinear mit Mip-Stufen, wiederholt) für uSgNoise. */
    public static int noiseTexture(GL4 gl) {
        byte[] v = Noise.tile(64, 1958);
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
        for (int p : new int[]{progLip, progDrops, progVeil}) if (p != 0) gl.glDeleteProgram(p);
        for (int b : new int[]{vbo, tvbo, veilVbo}) if (b != 0) gl.glDeleteBuffers(1, new int[]{b}, 0);
        for (int a : new int[]{vao, tvao, veilVao}) if (a != 0) gl.glDeleteVertexArrays(1, new int[]{a}, 0);
        progLip = progDrops = progVeil = vao = vbo = tvao = tvbo = veilVao = veilVbo = cap = tcap = veilN = 0;
        locs.clear();
        ready = false;
    }

    // ------------------------------------------------------------------ Shader

    private void u3(GL4 gl, int p, String n, double[] v) { gl.glUniform3f(loc(gl, p, n), (float) v[0], (float) v[1], (float) v[2]); }

    private int loc(GL4 gl, int prog, String name) { return locs.computeIfAbsent(prog + ":" + name, k -> gl.glGetUniformLocation(prog, name)); }

    /** Quelltext mit eingesetzten Bausteinen (aus diesem Package; light.glsl vom Wirt, wenn gesetzt). */
    public String source(String file) {
        StringBuilder sb = new StringBuilder("#version 430 core\n");
        expand(file, sb, new HashSet<>());
        return sb.toString();
    }

    private void expand(String file, StringBuilder sb, Set<String> seen) {
        if (!seen.add(file)) return;
        String text = file.equals("light.glsl") && hostLight != null ? hostLight : read(file);
        for (String l : text.split("\r?\n", -1)) {
            String t = l.trim();
            if (t.startsWith("#include")) expand(t.substring(t.indexOf('"') + 1, t.lastIndexOf('"')), sb, seen);
            else if (!t.startsWith("#version")) sb.append(l).append('\n');
        }
    }

    /** Eine Datei dieses Packages (Name relativ zu gl/ oder absolut im Klassenpfad). */
    public static String read(String file) {
        try (InputStream in = SurgeGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("surge: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("surge: " + file + ": " + e.getMessage(), e);
        }
    }

    private int program(GL4 gl, String vert, String frag) {
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
                throw new IllegalStateException("surge: " + f[i] + " lässt sich nicht übersetzen:\n" + new String(log, StandardCharsets.UTF_8));
            }
            gl.glAttachShader(prog, sh[i]);
        }
        gl.glLinkProgram(prog);
        int[] ok = new int[1];
        gl.glGetProgramiv(prog, GL4.GL_LINK_STATUS, ok, 0);
        for (int s : sh) gl.glDeleteShader(s);
        if (ok[0] == 0) throw new IllegalStateException("surge: " + vert + " + " + frag + " lässt sich nicht binden");
        return prog;
    }
}
