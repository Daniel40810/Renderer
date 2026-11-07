package com.dan.horizon.gl;

import com.dan.horizon.Horizon;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Horizontkarten auf der Grafikkarte (OpenGL 4.3, Compute-Shader). Je ein Gitter der Höhen fein und grob
 * ({@link #heights}), daraus je eine Karte RG32F (r = Horizontwinkel in Richtung der Sonne, g = Boden) über dem feinen und
 * dem groben Gitter ({@link #update}, neu nur, wenn die Sonne um {@link Horizon#azimuthStep} Grad gewandert ist), dazu auf
 * Wunsch die Karte „Sonne“ (R8) über dem feinen Gitter für Dinge, die eine Schattenkarte von oben lesen ({@link #sunMap}).
 * <p>
 * Shader eines Wirts lesen die Karten über {@code /com/dan/horizon/gl/horizon.glsl} ({@code horizonSun}); die Uniforms dafür
 * setzt {@link #bind}. Texturstellen wählt der Wirt.
 * <p>
 * Ein Wirt kann der Karte „Sonne“ einen eigenen Schatten mitgeben (etwa Wolken): {@link #sunMapHost} ersetzt
 * {@code sunmap_host.glsl} (eine Funktion {@code float hzHostShadow(vec3 w, vec3 sunDir)}), {@link #sunMapUniforms} setzt
 * seine Uniforms (Programm gebunden, Kennung des Programms als Argument). Gesetzt vor {@link #init}.
 */
public final class HorizonGl {
    public final Horizon horizon;
    /** Wirt: GLSL für sunmap_host.glsl (null: kein eigener Schatten) und wer dessen Uniforms setzt. */
    public String sunMapHost;
    public IntConsumer sunMapUniforms;
    /** Nach dem Rechnen der Karten auf die Grafikkarte warten (für die Zeitmessung, wie in Lituya). */
    public boolean finish = true;

    private int progHorizon, progSun;
    private int heightF, heightG, mapF, mapG, sunTex;
    private int nF, nG;
    private double[] rectF, rectG;
    private double lastAz = Double.NaN;
    private boolean ready, mapsReady;
    private double lastMs;

    public HorizonGl(Horizon horizon) { this.horizon = horizon; }
    public HorizonGl() { this(new Horizon()); }

    public void init(GL4 gl) {
        progHorizon = compute(gl, "horizon.comp");
        progSun = compute(gl, "sunmap.comp");
        ready = true;
    }

    public boolean ready() { return ready; }
    /** Ob die Karten für ein Azimut stehen. */
    public boolean mapsReady() { return mapsReady; }
    /** Zeit des letzten Rechnens der Karten (ms, mit {@link #finish}). */
    public double lastMillis() { return lastMs; }
    /** Azimut der Karten (Grad), NaN vor dem ersten Rechnen. */
    public double azimuth() { return lastAz; }

    /** Die Höhen beider Gitter hochladen (R32F, linear, Rand fest); die Karten gelten danach als veraltet. */
    public void heights(GL4 gl, Horizon.Grid fine, Horizon.Grid coarse) {
        if (heightF != 0) gl.glDeleteTextures(2, new int[]{heightF, heightG}, 0);
        heightF = upload(gl, fine.h, fine.n);
        heightG = upload(gl, coarse.h, coarse.n);
        if (mapF == 0 || nF != fine.n) { if (mapF != 0) gl.glDeleteTextures(1, new int[]{mapF}, 0); mapF = texture(gl, fine.n, GL4.GL_RG32F); }
        if (mapG == 0 || nG != coarse.n) { if (mapG != 0) gl.glDeleteTextures(1, new int[]{mapG}, 0); mapG = texture(gl, coarse.n, GL4.GL_RG32F); }
        nF = fine.n;
        nG = coarse.n;
        rectF = new double[]{fine.x0, fine.z0, fine.edge};
        rectG = new double[]{coarse.x0, coarse.z0, coarse.edge};
        lastAz = Double.NaN;
        mapsReady = false;
    }

    /** Beide Karten für das Azimut az (Grad von Nord), wenn die Sonne weit genug gewandert ist; true, wenn neu gerechnet. */
    public boolean update(GL4 gl, double azDeg) {
        if (!ready || heightF == 0 || !horizon.moved(lastAz, azDeg)) return false;
        long t0 = System.nanoTime();
        map(gl, mapF, nF, rectF, azDeg);
        map(gl, mapG, nG, rectG, azDeg);
        if (finish) gl.glFinish();
        lastMs = (System.nanoTime() - t0) / 1e6;
        lastAz = azDeg;
        mapsReady = true;
        return true;
    }

    private void map(GL4 gl, int target, int n, double[] rect, double az) {
        gl.glUseProgram(progHorizon);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, heightF);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, heightG);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        int p = progHorizon;
        gl.glUniform1i(loc(gl, p, "uHeightF"), 0);
        gl.glUniform1i(loc(gl, p, "uHeightG"), 1);
        gl.glUniform4f(loc(gl, p, "uFine"), (float) rectF[0], (float) rectF[1], (float) rectF[2], 0f);
        gl.glUniform4f(loc(gl, p, "uCoarse"), (float) rectG[0], (float) rectG[1], (float) rectG[2], 0f);
        gl.glUniform4f(loc(gl, p, "uTarget"), (float) rect[0], (float) rect[1], (float) rect[2], (float) n);
        double a = Math.toRadians(az);
        gl.glUniform2f(loc(gl, p, "uDir"), (float) Math.sin(a), (float) -Math.cos(a));
        gl.glUniform1f(loc(gl, p, "uR"), (float) horizon.effectiveRadius());
        gl.glUniform1f(loc(gl, p, "uSea"), (float) horizon.seaLevel);
        gl.glUniform4f(loc(gl, p, "uSearch"), (float) horizon.firstStep, (float) horizon.growth, (float) horizon.maxDistance, (float) horizon.maxSteps);
        gl.glBindImageTexture(0, target, 0, false, 0, GL4.GL_WRITE_ONLY, GL4.GL_RG32F);
        gl.glDispatchCompute((n + 15) / 16, (n + 15) / 16, 1);
        gl.glMemoryBarrier(GL4.GL_TEXTURE_FETCH_BARRIER_BIT | GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        gl.glUseProgram(0);
    }

    /**
     * Die Karte „Sonne“ (R8, über dem feinen Gitter) für die Sonnenhöhe mit Sinus sinEl und die Richtung sunDir, gemessen
     * in halber Höhe lift über dem Boden (Baumkronen). Kennung der Textur, 0 solange die Karten fehlen.
     */
    public int sunMap(GL4 gl, double sinEl, double[] sunDir, double lift) {
        if (!mapsReady) return 0;
        if (sunTex == 0) sunTex = texture(gl, nF, GL.GL_R8);
        int p = progSun;
        gl.glUseProgram(p);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, mapF);
        gl.glUniform1i(loc(gl, p, "uHorizon"), 0);
        gl.glUniform1f(loc(gl, p, "uSunEl"), (float) Math.asin(Math.max(-1, Math.min(1, sinEl))));
        gl.glUniform1f(loc(gl, p, "uLift"), (float) lift);
        gl.glUniform4f(loc(gl, p, "uGrid"), (float) rectF[0], (float) rectF[1], (float) rectF[2], (float) nF);
        gl.glUniform3f(loc(gl, p, "uSunDir"), (float) sunDir[0], (float) sunDir[1], (float) sunDir[2]);
        if (sunMapUniforms != null) sunMapUniforms.accept(p);
        gl.glBindImageTexture(0, sunTex, 0, false, 0, GL4.GL_WRITE_ONLY, GL.GL_R8);
        gl.glDispatchCompute((nF + 15) / 16, (nF + 15) / 16, 1);
        gl.glMemoryBarrier(GL4.GL_TEXTURE_FETCH_BARRIER_BIT);
        gl.glUseProgram(0);
        return sunTex;
    }

    /**
     * Die Uniforms von {@code horizon.glsl} für das gebundene Programm prog: Karten auf die Texturstellen unitFine und
     * unitCoarse; uHzOn = 0, solange sie fehlen. Danach ist wieder Stelle 0 aktiv.
     */
    public void bind(GL4 gl, int prog, int unitFine, int unitCoarse) {
        gl.glUniform1i(loc(gl, prog, "uHzOn"), mapsReady ? 1 : 0);
        if (!mapsReady) return;
        gl.glActiveTexture(GL.GL_TEXTURE0 + unitFine);
        gl.glBindTexture(GL.GL_TEXTURE_2D, mapF);
        gl.glActiveTexture(GL.GL_TEXTURE0 + unitCoarse);
        gl.glBindTexture(GL.GL_TEXTURE_2D, mapG);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(loc(gl, prog, "uHzFine"), unitFine);
        gl.glUniform1i(loc(gl, prog, "uHzCoarse"), unitCoarse);
        gl.glUniform4f(loc(gl, prog, "uHzFineRect"), (float) rectF[0], (float) rectF[1], (float) rectF[2], 0f);
        gl.glUniform4f(loc(gl, prog, "uHzCoarseRect"), (float) rectG[0], (float) rectG[1], (float) rectG[2], 0f);
    }

    /** Karten (RG32F) und Karte „Sonne“ (R8), 0 solange nicht da. */
    public int fineMap() { return mapF; }
    public int coarseMap() { return mapG; }
    public int sunTexture() { return mapsReady ? sunTex : 0; }
    /** Rechtecke der Gitter (x0, z0, Kante). */
    public double[] fineRect() { return rectF == null ? null : rectF.clone(); }
    public double[] coarseRect() { return rectG == null ? null : rectG.clone(); }

    public void dispose(GL4 gl) {
        for (int p : new int[]{progHorizon, progSun}) if (p != 0) gl.glDeleteProgram(p);
        int[] t = {heightF, heightG, mapF, mapG, sunTex};
        for (int x : t) if (x != 0) gl.glDeleteTextures(1, new int[]{x}, 0);
        progHorizon = progSun = heightF = heightG = mapF = mapG = sunTex = 0;
        ready = mapsReady = false;
    }

    // ------------------------------------------------------------------ Texturen, Shader

    private static int texture(GL4 gl, int n, int internal) {
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, internal, n, n);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    private static int upload(GL4 gl, float[] h, int n) {
        int t = texture(gl, n, GL4.GL_R32F);
        FloatBuffer b = Buffers.newDirectFloatBuffer(h);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, n, n, GL4.GL_RED, GL.GL_FLOAT, b);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t;
    }

    private final java.util.Map<String, Integer> locs = new java.util.HashMap<>();

    private int loc(GL4 gl, int prog, String name) {
        return locs.computeIfAbsent(prog + ":" + name, k -> gl.glGetUniformLocation(prog, name));
    }

    /**
     * Quelltext mit eingesetzten Bausteinen ({@code #include "datei"} aus diesem Package; sunmap_host.glsl vom Wirt). Weichen
     * Halbschatten oder Hub von der Vorgabe ab, kommen sie als {@code HZ_PENUMBRA}, {@code HZ_LIFT} davor (ein Wirt, der
     * horizon.glsl einbindet, setzt dieselben Zeilen vor sein Einbinden: {@link #defines()}).
     */
    public String source(String file) {
        StringBuilder sb = new StringBuilder("#version 430 core\n").append(defines());
        expand(file, sb, new HashSet<>());
        return sb.toString();
    }

    /** Zeilen {@code #define HZ_PENUMBRA …} und {@code HZ_LIFT …}, wenn {@link Horizon#penumbra} oder {@link Horizon#liftDistance} nicht der Vorgabe entsprechen; sonst leer. */
    public String defines() {
        StringBuilder sb = new StringBuilder();
        if (horizon.penumbra != 0.0046) sb.append("#define HZ_PENUMBRA ").append(horizon.penumbra).append('\n');
        if (horizon.liftDistance != 2000) sb.append("#define HZ_LIFT ").append(horizon.liftDistance).append('\n');
        return sb.toString();
    }

    private void expand(String file, StringBuilder sb, Set<String> seen) {
        if (!seen.add(file)) return;
        String text = file.equals("sunmap_host.glsl") && sunMapHost != null ? sunMapHost : read(file);
        for (String l : text.split("\r?\n", -1)) {
            String t = l.trim();
            if (t.startsWith("#include")) expand(t.substring(t.indexOf('"') + 1, t.lastIndexOf('"')), sb, seen);
            else if (!t.startsWith("#version")) sb.append(l).append('\n');
        }
    }

    /** Eine Datei dieses Packages aus dem Klassenpfad. */
    public static String read(String file) {
        try (InputStream in = HorizonGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("horizon: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("horizon: " + file + ": " + e.getMessage(), e);
        }
    }

    private int compute(GL4 gl, String file) {
        int sh = gl.glCreateShader(GL4.GL_COMPUTE_SHADER);
        gl.glShaderSource(sh, 1, new String[]{source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(sh, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] log = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(sh, log.length, null, 0, log, 0);
            gl.glDeleteShader(sh);
            throw new IllegalStateException("horizon: " + file + " lässt sich nicht übersetzen:\n" + new String(log, StandardCharsets.UTF_8));
        }
        int p = gl.glCreateProgram();
        gl.glAttachShader(p, sh);
        gl.glLinkProgram(p);
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(sh);
        if (ok[0] == 0) throw new IllegalStateException("horizon: " + file + " lässt sich nicht binden");
        return p;
    }
}
