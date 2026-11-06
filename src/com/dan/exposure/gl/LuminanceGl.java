package com.dan.exposure.gl;

import com.dan.exposure.EyeAdaptation;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Misst das HDR-Bild für {@link EyeAdaptation} auf der Grafikkarte (seit LituyaBay1958 W6 Runde 6): ein Compute-Shader
 * zählt die log2-Leuchtdichte von jedem {@link #step}-ten Bildpunkt in 64 Fächer. Drei Puffer im Ring, dauerhaft
 * abgebildet (OpenGL 4.4, glBufferStorage), und ein Zaun je Messung: gelesen wird die Messung eines früheren Bilds aus der
 * Abbildung, sobald die Grafikkarte sie fertig hat, ohne zu warten und ohne Aufruf, der den Treiber abgleichen ließe.
 *
 * <pre>
 * LuminanceGl lum = new LuminanceGl();  lum.init(gl);
 * // je Bild, nach der Szene, vor der Nachbearbeitung:
 * long[] h = lum.measure(gl, hdrTex, w, h);   // Histogramm eines früheren Bilds oder null
 * auge.update(h, dt);
 * post.set(gl, "uExposure", belichtung * auge.factor());
 * </pre>
 */
public final class LuminanceGl {
    /** Schritt im Raster (Bildpunkte): bei 1920 × 1080 und 4 rund 130 000 Proben. */
    public int step = 8;
    /**
     * Nur bei jedem every-ten Aufruf neu zählen (die Anpassung braucht Sekunden; gelesen wird bei jedem Aufruf). Seit Runde 6b:
     * Schritt 8 statt 4 und every 2; mit Schritt 4 in jedem Bild kostete das Zählen in Lituya rund 0,9 ms (Radeon, 1080p).
     */
    public int every = 2;
    private long calls;
    private int prog, uSize, uStep, uLog, uHdr;
    /** Runde 6b: drei Puffer im Ring, dauerhaft abgebildet (gelesen wird ohne Aufruf an den Treiber). */
    static final int RING = 3;
    private final int[] buf = new int[RING];
    private final java.nio.IntBuffer[] map = new java.nio.IntBuffer[RING];
    private final long[] fence = new long[RING];
    private final long[] seq = new long[RING];
    private long written;
    private final EyeAdaptation range;

    public LuminanceGl() { this(new EyeAdaptation()); }

    /** Bereich des Histogramms aus a (logMin, logMax; 64 Fächer). */
    public LuminanceGl(EyeAdaptation a) { range = a; }

    public void init(GL4 gl) {
        int s = gl.glCreateShader(GL4.GL_COMPUTE_SHADER);
        gl.glShaderSource(s, 1, new String[]{"#version 430 core\n" + source("luminance.comp")}, null);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("luminance.comp: " + log(gl, s, true));
        prog = gl.glCreateProgram();
        gl.glAttachShader(prog, s);
        gl.glLinkProgram(prog);
        gl.glGetProgramiv(prog, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("luminance.comp: " + log(gl, prog, false));
        gl.glDeleteShader(s);
        uSize = gl.glGetUniformLocation(prog, "uSize");
        uStep = gl.glGetUniformLocation(prog, "uStep");
        uLog = gl.glGetUniformLocation(prog, "uLog");
        uHdr = gl.glGetUniformLocation(prog, "uHdr");
        gl.glGenBuffers(RING, buf, 0);
        int flags = GL4.GL_MAP_READ_BIT | GL4.GL_MAP_PERSISTENT_BIT | GL4.GL_MAP_COHERENT_BIT;
        for (int k = 0; k < RING; k++) {
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, buf[k]);
            gl.glBufferStorage(GL4.GL_SHADER_STORAGE_BUFFER, 64 * 4L, Buffers.newDirectIntBuffer(64), flags | GL4.GL_DYNAMIC_STORAGE_BIT);
            map[k] = gl.glMapBufferRange(GL4.GL_SHADER_STORAGE_BUFFER, 0, 64 * 4L, flags).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer();
        }
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
    }

    /**
     * Zählt das Bild tex (w × h, RGBA16F o. ä.) in den einen Puffer und liest den anderen, wenn seine Messung fertig ist.
     * Rückgabe: 64 Zählungen oder null (noch keine fertige Messung).
     */
    public long[] measure(GL4 gl, int tex, int w, int h) {
        long[] out = null;
        long best = -1;
        // fertige Messungen lesen (die jüngste zählt), ohne auf die Grafikkarte zu warten. Runde 6b: aus der dauerhaften
        // Abbildung statt mit glGetBufferSubData; das hatte in Lituya (Radeon) jedes Bild mit der Grafikkarte abgeglichen und
        // kostete 3 bis 8 ms Bildzeit, obwohl der Zaun schon gefallen war
        int[] st = new int[1];
        for (int k = 0; k < RING; k++) {
            if (fence[k] == 0) continue;
            gl.glGetSynciv(fence[k], GL4.GL_SYNC_STATUS, 1, null, 0, st, 0);
            if (st[0] != GL4.GL_SIGNALED) continue;
            gl.glDeleteSync(fence[k]);
            fence[k] = 0;
            if (seq[k] > best) {
                best = seq[k];
                out = new long[64];
                for (int i = 0; i < 64; i++) out[i] = map[k].get(i) & 0xffffffffL;
            }
        }
        // in einen freien Puffer zählen
        int k = -1;
        if (calls++ % Math.max(1, every) == 0) for (int j = 0; j < RING && k < 0; j++) if (fence[j] == 0) k = j;
        if (k >= 0) {
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, buf[k]);
            gl.glClearBufferData(GL4.GL_SHADER_STORAGE_BUFFER, GL4.GL_R32UI, GL4.GL_RED_INTEGER, GL4.GL_UNSIGNED_INT, null);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 4, buf[k]);
            gl.glUseProgram(prog);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
            gl.glUniform1i(uHdr, 0);
            gl.glUniform2i(uSize, w, h);
            gl.glUniform1i(uStep, Math.max(1, step));
            gl.glUniform2f(uLog, (float) range.logMin, (float) range.logMax);
            int gx = (w / Math.max(1, step) + 15) / 16, gy = (h / Math.max(1, step) + 15) / 16;
            gl.glDispatchCompute(gx, gy, 1);
            // die Ergebnisse sollen über die Abbildung sichtbar werden (kohärent): Sperre für Zugriffe des Prozessors
            gl.glMemoryBarrier(GL4.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT | GL4.GL_SHADER_STORAGE_BARRIER_BIT);
            gl.glUseProgram(0);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 4, 0);
            fence[k] = gl.glFenceSync(GL4.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            seq[k] = ++written;
        }
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        return out;
    }

    public void dispose(GL4 gl) {
        for (int i = 0; i < RING; i++) if (fence[i] != 0) { gl.glDeleteSync(fence[i]); fence[i] = 0; }
        gl.glDeleteBuffers(RING, buf, 0);   // gibt auch die Abbildungen frei
        if (prog != 0) gl.glDeleteProgram(prog);
        prog = 0;
    }

    static String source(String name) {
        try (InputStream in = LuminanceGl.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalArgumentException("Shader fehlt: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static String log(GL4 gl, int id, boolean shader) {
        int[] len = new int[1];
        if (shader) gl.glGetShaderiv(id, GL4.GL_INFO_LOG_LENGTH, len, 0);
        else gl.glGetProgramiv(id, GL4.GL_INFO_LOG_LENGTH, len, 0);
        byte[] b = new byte[Math.max(1, len[0])];
        if (shader) gl.glGetShaderInfoLog(id, b.length, null, 0, b, 0);
        else gl.glGetProgramInfoLog(id, b.length, null, 0, b, 0);
        return new String(b, StandardCharsets.UTF_8).trim();
    }
}
