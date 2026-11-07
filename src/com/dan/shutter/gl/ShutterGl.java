package com.dan.shutter.gl;

import com.dan.shutter.Shutter;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Die Kino-Stufe auf der Grafikkarte (GL 4.3): Teilbilder sammeln ({@code accumulate.frag}, additiv in eine Summe RGBA32F,
 * je Bildpunkt mit dem Zeitfenster der Umlaufblende und dem Katzenauge gewichtet) und teilen ({@code resolve.frag}). Die
 * App rechnet je Teilbild die Szene zur Zeit des Teilbilds, mit der Kamera am Ort der Pupille und dem Versatz im Bildpunkt
 * ({@link com.dan.shutter.SubFrames}), und gibt das HDR-Bild hierher. Das Ergebnis hat Mip-Stufen. Danach: Mischen aus,
 * Framebuffer und Viewport verändert.
 * <p>
 * Seit LituyaBay1958 W9c Runde 2:
 * <ul>
 * <li><b>Fehler des Objektivs</b>: je Farbe ein Versatz des Teilbilds ({@link View#shiftUv}, aus
 * {@link com.dan.shutter.Aberration}): sphärische Aberration und Längsfarbfehler.</li>
 * <li><b>Lichter</b> ({@code lights.comp}, {@code splat.vert}): vereinzelte helle Bildpunkte geben ihren Überschuss nicht als
 * Punkt in die Summe, sondern als Fleck von der Größe ihrer Zelle der Pupille (Halbmesser des Unschärfekreises · κ / √n). Über
 * n Teilbilder füllen die Flecken den Kreis lückenlos; Form, Katzenauge, Ring der sphärischen Aberration und Farbsäume kommen
 * weiter aus den Orten der Pupille. Dafür braucht der View die Tiefe und die Schärfe.</li>
 * </ul>
 */
public final class ShutterGl {

    /** Ein Teilbild. */
    public static final class View {
        public int hdrTexture, width = 1, height = 1;
        /** Zeit des Teilbilds (s, Uhr von {@link Shutter#openAt}). */
        public double tauS;
        /** Ort in der Pupille (Fläche π, y mit der Stauchung) und die Stauchung. */
        public double pupilU, pupilV, squeeze = 1;
        /** Katzenauge (ρ1, σ1, ρ2, σ2) im Maß der Pupille; null: aus. */
        public double[] catEye;
        /** tan Feldwinkel je Bildpunkt von der Bildmitte (Sensor mm je Bildpunkt / Brennweite, mal Stauchung des Felds). */
        public double tanPerPx;
        /** Blendenzahl (für die Flanke der Umlaufblende). */
        public double fNumber = 2.8;
        /**
         * Seit W9c Runde 2: Versatz des Teilbilds je Farbe (uv: rx, ry, gx, gy, bx, by), etwa
         * {@link com.dan.shutter.Aberration#shiftUv}; null: keiner.
         */
        public double[] shiftUv;
        /**
         * Seit W9c Runde 2, für die Lichter: Leuchtdichte (Einheiten der Szene), ab der ein vereinzelter Bildpunkt als Licht
         * gilt (0: aus; die Linse nimmt etwa 2 / Belichtung), Tiefe des Teilbilds und wie sie zu lesen ist.
         */
        public double lightThreshold;
        public int depthTexture;
        public boolean reversedZ;
        public double near = 0.1, far = 1e5;
        /** Halbmesser des Unschärfekreises (Bildpunkte) je Dioptrie Abstand von der Schärfe: (D/2) · f / Bildpunkt; Schärfe (m). */
        public double cocPxPerDiopter, focusM = 10;
        /** Teilbilder dieses Filmbilds. */
        public int subframes = 1;
        /**
         * Seit W9c Runde 2, für die Lichter als ganze Kreise: die Blende wie {@link com.dan.shutter.Pupil} (Lamellen, Rundung,
         * Drehung, Umkreis → Fläche π als pupilScale), TA_best in Bildpunkten an ρ = k/16 · pupilScale
         * ({@link com.dan.shutter.Aberration#taBestPx}, 17 Werte; null: keine) und der Längsfarbfehler in Bildpunkten je
         * Einheit der Pupille ({@link com.dan.shutter.Aberration#chromaticPx}).
         */
        public int blades;
        public double roundness = 1, rotationDeg, pupilScale = 1;
        public double[] taBestPx;
        public double chromaRPx, chromaBPx;
    }

    /** Seit W9c Runde 2: ein Licht muss so oft heller sein als sein Ring in 3 Bildpunkten Abstand (vereinzelt) [G]. */
    public double lightIsolation = 4;
    /** Seit W9c Runde 2: kleinere Kreise (Halbmesser in Bildpunkten) bleiben im Bild (dort ist auch ein Punkt nicht körnig). */
    public double lightMinRadius = 1.5;
    /** Seit W9c Runde 2: höchstens so viele Lichter je Teilbild (weitere bleiben im Bild als Punkt). */
    public int lightCap = 65536;
    /** Seit W9c Runde 2: Lichter im letzten Teilbild (nach {@link #resolve}; −1: nicht gelesen). */
    public int lastLights = -1;

    private final Shutter shutter;
    private boolean ready;
    private int progAcc, progRes, progLights, progClamp, progSplat, vao, fbo, sumTex, outTex, cleanTex, cmdBuf, lightBuf, lightBufCap, w, h, count;
    private final Map<Integer, Map<String, Integer>> locs = new HashMap<>();

    public ShutterGl(Shutter shutter) { this.shutter = shutter; }

    public boolean ready() { return ready; }

    /** Teilbilder in der Summe. */
    public int count() { return count; }

    public void init(GL4 gl) {
        String vs = read("fullscreen.vert"), win = read("window.glsl");
        progAcc = program(gl, vs, win + read("accumulate.frag"));
        progRes = program(gl, vs, read("resolve.frag"));
        progLights = compute(gl, read("lights.comp"));
        progClamp = compute(gl, read("lights_clamp.comp"));
        progSplat = program(gl, win + read("splat.vert"), win + read("splat.frag"));
        int[] a = new int[2];
        gl.glGenVertexArrays(1, a, 0);
        vao = a[0];
        gl.glGenFramebuffers(1, a, 0);
        fbo = a[0];
        gl.glGenBuffers(2, a, 0);
        cmdBuf = a[0];
        lightBuf = a[1];
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, cmdBuf);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, 16, null, GL4.GL_DYNAMIC_DRAW);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        ready = true;
    }

    /** Eine neue Summe der Größe width × height (leer). */
    public void begin(GL4 gl, int width, int height) {
        if (width != w || height != h || sumTex == 0) {
            if (sumTex != 0) gl.glDeleteTextures(3, new int[]{sumTex, outTex, cleanTex}, 0);
            sumTex = tex(gl, GL4.GL_RGBA32F, width, height);
            outTex = texMip(gl, width, height);
            cleanTex = tex(gl, GL4.GL_RGBA16F, width, height);
            w = width;
            h = height;
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, sumTex, 0);
        gl.glViewport(0, 0, w, h);
        gl.glClearColor(0, 0, 0, 0);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        count = 0;
    }

    /** Ein Teilbild in die Summe. */
    public void add(GL4 gl, View v) {
        if (!ready) return;
        // W9c Runde 2: die Lichter herauslösen (Liste und Teilbild ohne ihren Überschuss)
        boolean lights = v.lightThreshold > 0 && v.depthTexture != 0 && lightCap > 0;
        int src = v.hdrTexture;
        if (lights) {
            if (lightBufCap != lightCap) {
                gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, lightBuf);
                gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, 32L * lightCap, null, GL4.GL_DYNAMIC_COPY);
                gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
                lightBufCap = lightCap;
            }
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, cmdBuf);
            gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 16, com.jogamp.common.nio.Buffers.newDirectIntBuffer(new int[]{4, 0, 0, 0}));
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
            int q = progLights;
            gl.glUseProgram(q);
            bind(gl, q, "uHdr", 0, v.hdrTexture);
            bind(gl, q, "uDepth", 1, v.depthTexture);
            gl.glBindImageTexture(0, cleanTex, 0, false, 0, GL4.GL_WRITE_ONLY, GL4.GL_RGBA16F);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 1, cmdBuf);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 2, lightBuf);
            int l = loc(gl, q, "uSize");
            if (l >= 0) gl.glUniform2i(l, w, h);
            setf(gl, q, "uThresh", (float) v.lightThreshold);
            setf(gl, q, "uIso", (float) lightIsolation);
            seti(gl, q, "uCap", lightCap);
            seti(gl, q, "uReversed", v.reversedZ ? 1 : 0);
            setf(gl, q, "uNear", (float) v.near);
            setf(gl, q, "uFar", (float) v.far);
            setf(gl, q, "uCocK", (float) v.cocPxPerDiopter);
            setf(gl, q, "uFocusD", (float) (1 / Math.max(1e-3, v.focusM)));
            set2(gl, q, "uPupilP", (float) v.pupilU, (float) v.pupilV);
            setf(gl, q, "uMinR", (float) lightMinRadius);
            gl.glDispatchCompute((w + 15) / 16, (h + 15) / 16, 1);
            gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT);
            gl.glUseProgram(progClamp);
            seti(gl, progClamp, "uCap", lightCap);
            gl.glDispatchCompute(1, 1, 1);
            gl.glMemoryBarrier(GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL4.GL_TEXTURE_FETCH_BARRIER_BIT | GL4.GL_SHADER_STORAGE_BARRIER_BIT | GL4.GL_COMMAND_BARRIER_BIT);
            src = cleanTex;
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, sumTex, 0);
        gl.glViewport(0, 0, w, h);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
        gl.glBlendEquation(GL.GL_FUNC_ADD);
        gl.glBindVertexArray(vao);
        int p = progAcc;
        gl.glUseProgram(p);
        bind(gl, p, "uHdr", 0, src);
        fenster(gl, p, v);
        double[] sh = v.shiftUv;
        set2(gl, p, "uShiftR", sh == null ? 0 : (float) sh[0], sh == null ? 0 : (float) sh[1]);
        set2(gl, p, "uShiftG", sh == null ? 0 : (float) sh[2], sh == null ? 0 : (float) sh[3]);
        set2(gl, p, "uShiftB", sh == null ? 0 : (float) sh[4], sh == null ? 0 : (float) sh[5]);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        if (lights) {
            // die Lichter als Flecken (indirekt: so viele, wie die Liste hat; je Licht drei Farben)
            int q = progSplat;
            gl.glUseProgram(q);
            fenster(gl, q, v);
            seti(gl, q, "uCap", lightCap);
            int l = loc(gl, q, "uTA");
            if (l >= 0) {
                float[] f = new float[17];
                for (int k = 0; k < 17; k++) f[k] = v.taBestPx == null ? 0 : (float) v.taBestPx[k];
                gl.glUniform1fv(l, 17, f, 0);
            }
            setf(gl, q, "uRhoTop", (float) v.pupilScale);
            l = loc(gl, q, "uKc");
            if (l >= 0) gl.glUniform3f(l, (float) v.chromaRPx, 0f, (float) v.chromaBPx);
            setf(gl, q, "uSqueezeP", (float) Math.max(1e-3, v.squeeze));
            seti(gl, q, "uBlades", v.blades);
            setf(gl, q, "uRound", (float) v.roundness);
            setf(gl, q, "uRot", (float) Math.toRadians(v.rotationDeg));
            setf(gl, q, "uScaleP", (float) v.pupilScale);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 2, lightBuf);
            gl.glBindBuffer(GL4.GL_DRAW_INDIRECT_BUFFER, cmdBuf);
            gl.glDrawArraysIndirect(GL.GL_TRIANGLE_STRIP, 0);
            gl.glBindBuffer(GL4.GL_DRAW_INDIRECT_BUFFER, 0);
        }
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(0);
        gl.glBindVertexArray(0);
        count++;
        lastWithLights = lights;
    }

    private boolean lastWithLights;

    /** Die Uniforms des Zeitfensters und des Katzenauges (window.glsl). */
    private void fenster(GL4 gl, int p, View v) {
        Shutter s = shutter;
        setf(gl, p, "uTau", (float) v.tauS);
        setf(gl, p, "uOpen", (float) s.openS());
        seti(gl, p, "uRolling", s.rolling ? 1 : 0);
        set2(gl, p, "uGate", (float) s.gateWidthMm, (float) s.gateHeightMm);
        setf(gl, p, "uGateR", (float) s.gateRadiusMm);
        setf(gl, p, "uSweep", (float) Math.toRadians(s.sweepDeg));
        setf(gl, p, "uFps", (float) s.fps);
        setf(gl, p, "uRamp", (float) s.rampS(v.fNumber));
        set2(gl, p, "uPupil", (float) v.pupilU, (float) v.pupilV);
        setf(gl, p, "uSqueeze", (float) Math.max(1e-3, v.squeeze));
        double[] c = v.catEye;
        int l = loc(gl, p, "uCat");
        if (l >= 0) {
            if (c == null) gl.glUniform4f(l, 99, 0, 99, 0);
            else gl.glUniform4f(l, (float) c[0], (float) c[1], (float) c[2], (float) c[3]);
        }
        setf(gl, p, "uTanPerPx", (float) v.tanPerPx);
        set2(gl, p, "uSize", w, h);
    }

    /** Die Summe geteilt (RGBA16F); wo kein Teilbild zählte, lastTexture. Gibt die Textur zurück. */
    public int resolve(GL4 gl, int lastTexture) {
        if (lastWithLights) {   // W9c Runde 2: wie viele Lichter das letzte Teilbild hatte (einmal je Filmbild)
            java.nio.IntBuffer ib = com.jogamp.common.nio.Buffers.newDirectIntBuffer(4);
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, cmdBuf);
            gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 16, ib);
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
            lastLights = ib.get(1) / 3;
        } else lastLights = -1;
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, outTex, 0);
        gl.glViewport(0, 0, w, h);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBindVertexArray(vao);
        gl.glUseProgram(progRes);
        bind(gl, progRes, "uSum", 0, sumTex);
        bind(gl, progRes, "uLast", 1, lastTexture);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glUseProgram(0);
        gl.glBindVertexArray(0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, outTex);   // Mip-Stufen wie das Bild der Kantenglättung (für Glühen und Messungen)
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return outTex;
    }

    public int resultTexture() { return outTex; }

    public void dispose(GL4 gl) {
        if (!ready) return;
        gl.glDeleteProgram(progAcc);
        gl.glDeleteProgram(progRes);
        gl.glDeleteProgram(progLights);
        gl.glDeleteProgram(progClamp);
        gl.glDeleteProgram(progSplat);
        gl.glDeleteBuffers(2, new int[]{cmdBuf, lightBuf}, 0);
        lightBufCap = 0;
        if (sumTex != 0) gl.glDeleteTextures(3, new int[]{sumTex, outTex, cleanTex}, 0);
        gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        locs.clear();
        sumTex = outTex = 0;
        w = h = 0;
        ready = false;
    }

    static int texMip(GL4 gl, int w, int h) {
        int levels = 1 + (int) Math.floor(Math.log(Math.max(w, h)) / Math.log(2));
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, levels, GL4.GL_RGBA16F, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    static int tex(GL4 gl, int fmt, int w, int h) {
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, fmt, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    private int loc(GL4 gl, int p, String n) { return locs.computeIfAbsent(p, k -> new HashMap<>()).computeIfAbsent(n, k -> gl.glGetUniformLocation(p, k)); }

    private void seti(GL4 gl, int p, String n, int v) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform1i(l, v); }

    private void setf(GL4 gl, int p, String n, float v) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform1f(l, v); }

    private void set2(GL4 gl, int p, String n, float a, float b) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform2f(l, a, b); }

    private void bind(GL4 gl, int p, String n, int unit, int t) {
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t);
        seti(gl, p, n, unit);
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    static String read(String file) {
        try (InputStream in = ShutterGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("Shader " + file + " fehlt (src mit auf den Klassenpfad?)");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Shader " + file + ": " + e.getMessage(), e);
        }
    }

    static int compute(GL4 gl, String src) {
        int p = gl.glCreateProgram(), sh = gl.glCreateShader(GL4.GL_COMPUTE_SHADER);
        gl.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + src}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] log = new byte[8192];
            gl.glGetShaderInfoLog(sh, log.length, null, 0, log, 0);
            throw new IllegalStateException("com.dan.shutter: " + new String(log, StandardCharsets.UTF_8).trim());
        }
        gl.glAttachShader(p, sh);
        gl.glDeleteShader(sh);
        gl.glLinkProgram(p);
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] log = new byte[8192];
            gl.glGetProgramInfoLog(p, log.length, null, 0, log, 0);
            throw new IllegalStateException("com.dan.shutter: " + new String(log, StandardCharsets.UTF_8).trim());
        }
        return p;
    }

    static int program(GL4 gl, String vs, String fs) {
        int p = gl.glCreateProgram();
        for (int k = 0; k < 2; k++) {
            int s = gl.glCreateShader(k == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(s, 1, new String[]{"#version 430 core\n" + (k == 0 ? vs : fs)}, null, 0);
            gl.glCompileShader(s);
            int[] ok = new int[1];
            gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] log = new byte[8192];
                gl.glGetShaderInfoLog(s, log.length, null, 0, log, 0);
                throw new IllegalStateException("com.dan.shutter: " + new String(log, StandardCharsets.UTF_8).trim());
            }
            gl.glAttachShader(p, s);
            gl.glDeleteShader(s);
        }
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] log = new byte[8192];
            gl.glGetProgramInfoLog(p, log.length, null, 0, log, 0);
            throw new IllegalStateException("com.dan.shutter: " + new String(log, StandardCharsets.UTF_8).trim());
        }
        return p;
    }
}
