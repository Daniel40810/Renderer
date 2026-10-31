package com.dan.lamp.gl;

import com.dan.lamp.Lamp;
import com.dan.lamp.Lamps;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Die Leuchten auf der Grafikkarte (seit Timmendorf Phase 11, F18 A): je Bild die wichtigsten
 * ({@link Lamps#important}, so viele wie die Qualitätsstufe erlaubt) in einen Speicherpuffer (std430, Bindung
 * {@link #BINDING}); fremde Shader lesen ihn mit dem Baustein {@code lamp.glsl} ({@code lampIrradiance},
 * {@code lampSpecular} für nasses Pflaster und Wasser). Dazu {@link #drawHeads}: die leuchtenden Köpfe als Lichtpunkte
 * ({@code lamp_head.vert}/{@code .frag}), additiv, linear, vorbelichtet.
 */
public final class LampGl {
    public static final int BINDING = 7, FLOATS = 12;

    /** Ansicht: Projektion·Blick, Auge, Bildpunkte je Bogenmaß, Vorbelichtung. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public double pxPerRad = 700, preExposure = 1;
    }

    /**
     * Seit Timmendorf Phase 12: wie viele Leuchten höchstens in den Puffer (−1 = nach der Qualitätsstufe). Für die
     * Lichtpunkte aller Leuchten einer Stadt eine zweite {@code LampGl} mit großer Grenze, für das Licht auf den Flächen
     * die Stufe.
     */
    public volatile int limit = -1;
    /** Kleinster Halbmesser eines Lichtpunkts (Bildpunkte). */
    public volatile double minPixels = 0.8;
    private int ssbo, prog, vao, cap;
    private float[] data = new float[0];
    private int count;
    private boolean ready;

    public boolean ready() { return ready; }
    public int count() { return count; }

    public void init(GL4 gl) {
        String[] src = sources();
        prog = program(gl, src[0], src[1]);
        int[] b = new int[1];
        gl.glGenBuffers(1, b, 0);
        ssbo = b[0];
        cap = 64;
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, ssbo);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, cap * FLOATS * 4L, null, GL.GL_DYNAMIC_DRAW);
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        ready = true;
    }

    /**
     * Die beiden Programmtexte der Lichtpunkte, wie sie an den Treiber gehen (seit Phase 12 prüfbar: genau ein
     * {@code #version}, ganz vorn – in Phase 11 stand es im Fragment-Shader doppelt).
     */
    public static String[] sources() { return new String[]{header() + read("lamp_head.vert"), read("lamp_head.frag")}; }

    /** Kopf für fremde Shader: {@code #version}, die Bindung und {@code lamp.glsl}; danach der eigene Text. */
    public static String header() { return "#version 430 core\n#define LAMP_BINDING " + BINDING + "\n" + read("lamp.glsl") + "\n"; }

    /** Füllt das Feld aus den wichtigsten Leuchten zum Auge (CPU, ohne GL; prüfbar). */
    public void prepare(Lamps lamps, double[] eye) {
        List<Lamp> l = lamps.important(eye[0], eye[1], eye[2], limit > 0 ? limit : lamps.quality.gpuLamps);
        if (data.length < Math.max(1, l.size()) * FLOATS) data = new float[Math.max(64, l.size()) * FLOATS];
        count = 0;
        for (Lamp p : l) {
            int o = count++ * FLOATS;
            double k = p.lumen * p.lor * Lamp.norm(p.distribution) * p.output();
            double[] c = p.rgb();
            double fx = Math.sin(Math.toRadians(p.facing)), fz = -Math.cos(Math.toRadians(p.facing));
            data[o] = (float) p.x; data[o + 1] = (float) p.y; data[o + 2] = (float) p.z; data[o + 3] = p.distribution.ordinal();
            data[o + 4] = (float) (k * c[0]); data[o + 5] = (float) (k * c[1]); data[o + 6] = (float) (k * c[2]); data[o + 7] = (float) lamps.reach(p);
            data[o + 8] = (float) fx; data[o + 9] = (float) fz; data[o + 10] = (float) p.area; data[o + 11] = 0;
        }
    }

    /** Schiebt das Feld auf die Karte und bindet den Puffer an {@link #BINDING}. */
    public void upload(GL4 gl) {
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, ssbo);
        if (count > cap) {
            cap = Math.max(count, cap * 2);
            gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, cap * FLOATS * 4L, null, GL.GL_DYNAMIC_DRAW);
        }
        if (count > 0) gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, count * FLOATS * 4L, Buffers.newDirectFloatBuffer(data, 0, count * FLOATS));
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, BINDING, ssbo);
    }

    /** Setzt {@code uLampCount} in einem fremden Programm, das {@link #header()} trägt. */
    public void bind(GL4 gl, int program) {
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, BINDING, ssbo);
        gl.glUniform1i(gl.glGetUniformLocation(program, "uLampCount"), count);
    }

    /** Die Köpfe als Lichtpunkte, nach allem Undurchsichtigen (Tiefentest an, Tiefe nicht schreiben). */
    public void drawHeads(GL4 gl, View v) {
        if (!ready || count == 0) return;
        gl.glUseProgram(prog);
        bind(gl, prog);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uViewProj"), 1, false, v.viewProj, 0);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uEye"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uPxPerRad"), (float) v.pxPerRad);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uMinPx"), (float) minPixels);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uGain"), (float) v.preExposure);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
        gl.glBindVertexArray(vao);
        gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, count);
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
    }

    public void dispose(GL4 gl) {
        if (!ready) return;
        gl.glDeleteProgram(prog);
        gl.glDeleteBuffers(1, new int[]{ssbo}, 0);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        ready = false;
    }

    /** Die Shader-Dateien (für Prüfungen). */
    public static String[] files() { return new String[]{"lamp.glsl", "lamp_head.vert", "lamp_head.frag"}; }

    /** Für Prüfungen. */
    public float[] data() { return data; }

    static String read(String name) {
        try (InputStream in = LampGl.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("lamp: Shader-Datei fehlt: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("lamp: " + name + ": " + e.getMessage(), e);
        }
    }

    static int program(GL4 gl, String vs, String fs) {
        int p = gl.glCreateProgram();
        for (int k = 0; k < 2; k++) {
            int sh = gl.glCreateShader(k == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(sh, 1, new String[]{k == 0 ? vs : fs}, null, 0);
            gl.glCompileShader(sh);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] b = new byte[4096];
                int[] len = new int[1];
                gl.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
                throw new IllegalStateException((k == 0 ? "lamp_head.vert: " : "lamp_head.frag: ") + new String(b, 0, len[0], StandardCharsets.UTF_8));
            }
            gl.glAttachShader(p, sh);
            gl.glDeleteShader(sh);
        }
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("lamp: Programm lässt sich nicht binden");
        return p;
    }
}
