package com.dan.wake.gl;

import com.dan.wake.Wake;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Kielwasser auf der Grafikkarte (Paketvertrag Punkt 3; Hülle seit Timmendorf Phase 1, Höhenfeld seit Phase 8, F14 A):
 * ein Compute-Shader ({@code wake.comp}, Ressource in diesem Package) rechnet je Bild zwei Kacheln um die Kamera,
 * nah (256 m) und weit (2 048 m), als Textur-Array RG16F: R Höhe (m), G Schaum (0..1). Die Kacheln liegen auf ihrem
 * Zellraster fest (kein Schwimmen beim Fahren). Kurze Wellen, die das Raster nicht mehr trägt (unter drei Zellen),
 * blendet der Shader aus. Die See ({@code com.dan.ocean}, Eingang Kielwasser) liest die Textur über
 * {@link #texture()}, {@link #rect(int)}; das Package weiß nicht, wer sie liest.
 * <p>
 * Je Quelle gehen 96 Spurpunkte hinauf (nach hinten immer weiter auseinander, bei Fähren gut 20 km) ({@link Wake#uploadTrail}); was der Prozessor mit {@link Wake#tile} rechnet,
 * ist dasselbe.
 */
public final class WakeGl {
    public static final int MAX_SOURCES = 96, TRAIL = 96;
    /** Kantenlänge der beiden Kacheln (m). */
    public double nearSize = 256, farSize = 2048;
    private int program, tex, size, paramsBuf, trailBuf;
    private final double[][] rects = {{0, 0, 1, 0}, {0, 0, 1, 0}};
    private FloatBuffer params = Buffers.newDirectFloatBuffer(MAX_SOURCES * 12), trail = Buffers.newDirectFloatBuffer(MAX_SOURCES * TRAIL * 4);
    private int lastCount;

    public void init(GL4 gl, Wake.Quality q) {
        program = gl.glCreateProgram();
        int cs = compile(gl, GL4.GL_COMPUTE_SHADER, "wake.comp");
        gl.glAttachShader(program, cs);
        gl.glLinkProgram(program);
        int[] ok = new int[1];
        gl.glGetProgramiv(program, GL4.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(cs);
        if (ok[0] == 0) throw new IllegalStateException("wake: Programm lässt sich nicht binden");
        int[] b = new int[2];
        gl.glGenBuffers(2, b, 0);
        paramsBuf = b[0];
        trailBuf = b[1];
        texture(gl, q.tile);
    }

    private void texture(GL4 gl, int n) {
        if (tex != 0) gl.glDeleteTextures(1, new int[]{tex}, 0);
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        tex = t[0];
        size = n;
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, tex);
        gl.glTexStorage3D(GL4.GL_TEXTURE_2D_ARRAY, 1, GL4.GL_RG16F, n, n, 2);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, 0);
    }

    /**
     * Rechnet beide Kacheln um (cx, cz) neu: Quellen und Spuren hinauf, zwei Aufrufe des Compute-Shaders, Sperre für
     * das Lesen als Textur. Ohne Quellen bleiben die Kacheln aus ({@link #rect} meldet aus).
     */
    public void update(GL4 gl, Wake w, double cx, double cz) {
        if (program == 0) return;
        if (w.quality.tile != size) texture(gl, w.quality.tile);
        List<Wake.Source> src = w.sources();
        double nearCell = nearSize / size;
        double ox = Math.floor((cx - nearSize / 2) / nearCell) * nearCell, oz = Math.floor((cz - nearSize / 2) / nearCell) * nearCell;
        params.clear();
        trail.clear();
        int count = 0, first = 0;
        for (Wake.Source s : src) {
            if (count >= MAX_SOURCES) break;
            double[] p = w.uploadParams(s);
            if (p[4] == 0 && p[6] == 0) continue;
            double[][] tr = w.uploadTrail(s, TRAIL);
            params.put((float) (p[0] - ox)).put((float) (p[1] - oz)).put((float) p[2]).put((float) p[3]);
            params.put((float) p[4]).put((float) p[5]).put((float) p[6]).put((float) p[7]);
            params.put((float) p[8]).put((float) Math.min(p[9], 1e7)).put(first).put(tr.length);
            for (double[] q : tr) trail.put((float) (q[0] - ox)).put((float) (q[1] - oz)).put((float) q[2]).put((float) q[3]);
            first += tr.length;
            count++;
        }
        lastCount = count;
        rects[0][3] = rects[1][3] = 0;
        if (count == 0) return;
        params.flip();
        trail.flip();
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, paramsBuf);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) params.limit() * 4, params, GL4.GL_STREAM_DRAW);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, trailBuf);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) Math.max(16, trail.limit() * 4L), trail, GL4.GL_STREAM_DRAW);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        gl.glUseProgram(program);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 1, paramsBuf);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 2, trailBuf);
        gl.glBindImageTexture(0, tex, 0, true, 0, GL4.GL_WRITE_ONLY, GL4.GL_RG16F);
        gl.glUniform1i(gl.glGetUniformLocation(program, "uCount"), count);
        double farCell = farSize / size;
        for (int layer = 0; layer < 2; layer++) {
            double sz = layer == 0 ? nearSize : farSize, cell = layer == 0 ? nearCell : farCell;
            double x0 = Math.floor((cx - sz / 2) / cell) * cell, z0 = Math.floor((cz - sz / 2) / cell) * cell;
            rects[layer] = new double[]{x0, z0, sz, 1};
            gl.glUniform4f(gl.glGetUniformLocation(program, "uRect"), (float) (x0 - ox), (float) (z0 - oz), (float) sz, size);
            gl.glUniform1i(gl.glGetUniformLocation(program, "uLayer"), layer);
            gl.glUniform1f(gl.glGetUniformLocation(program, "uKmax"), (float) (2 * Math.PI / (3 * cell)));
            int groups = (size + 15) / 16;
            gl.glDispatchCompute(groups, groups, 1);
        }
        gl.glMemoryBarrier(GL4.GL_TEXTURE_FETCH_BARRIER_BIT | GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        gl.glUseProgram(0);
    }

    /** Textur-Array (2 Schichten RG16F): 0 nah, 1 weit. */
    public int texture() { return tex; }

    /** Lage einer Schicht: Ecke x, z, Kantenlänge (m), an (1) / aus (0). */
    public double[] rect(int layer) { return rects[layer].clone(); }

    /** „Kielwasser 12 Quellen · 2 × 512² RG16F“ */
    public String info() { return String.format(Locale.GERMAN, "Kielwasser %d Quellen · 2 × %d² RG16F (%.0f m, %.0f m)", lastCount, size, nearSize, farSize); }

    public void dispose(GL4 gl) {
        if (program != 0) gl.glDeleteProgram(program);
        if (tex != 0) gl.glDeleteTextures(1, new int[]{tex}, 0);
        if (paramsBuf != 0) gl.glDeleteBuffers(2, new int[]{paramsBuf, trailBuf}, 0);
        program = tex = paramsBuf = trailBuf = 0;
    }

    /** Liest eine Shader-Datei aus diesem Package (Ressource neben der Klasse). */
    public static String source(String file) {
        try (InputStream in = WakeGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("wake: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("wake: " + file + ": " + e.getMessage(), e);
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
            throw new IllegalStateException("wake: " + file + " lässt sich nicht übersetzen: " + new String(b, StandardCharsets.UTF_8).trim());
        }
        return sh;
    }
}
