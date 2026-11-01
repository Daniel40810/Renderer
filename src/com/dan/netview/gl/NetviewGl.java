package com.dan.netview.gl;

import com.dan.netview.NetLayer;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zeichnet {@link NetLayer}-Ebenen mit JOGL (OpenGL 4.3): jede Strecke, Pfeilspitze und jeder Punkt ist eine Instanz
 * eines Vierecks in Bildpunkten (Shader {@code netview.vert}, {@code netview.frag}). Je Stück liegen drei Stufen im
 * Puffer; je Bild wählt {@link #draw} nach dem Abstand zum Auge und zeichnet eine Ebene mit einem
 * {@code glMultiDrawArraysIndirect}. Die Zeit des Blicks lässt Isolinien erscheinen und Strahlen wachsen (Isochronen,
 * die mit der Welle laufen). Mischung an, Tiefentest an (mit kleinem Versatz zum Auge, Vergleich wie beim Aufrufer
 * eingestellt), Tiefe wird nicht geschrieben; {@link #gain} für HDR-Puffer.
 */
public final class NetviewGl {
    static final int STRIDE = 16;

    /** Eine hochgeladene Ebene: Puffer, je Stück und Stufe Anfang und Zahl der Instanzen. */
    private static final class Uploaded {
        int vao, vbo, cmd, version = -1;
        int[][] first, count;
        long instances;
    }

    private final List<NetLayer> layers = new ArrayList<>();
    private final List<Uploaded> up = new ArrayList<>();
    private int program;
    /** Versatz der Tiefe zum Auge (Anteil von w), damit Linien auf dem Gelände nicht flimmern. */
    public double depthBias = 0.0005;
    /** Wie lange die vorderste Isolinie leuchtet (s). */
    public double front = 8;
    /**
     * Faktor auf die Farben (seit LituyaBay1958 Phase 7a): wer in einen HDR-Puffer zeichnet, der danach mit einer
     * Belichtung multipliziert wird, setzt 1/Belichtung, damit die Linien ihre Farbe behalten. Bei umgekehrter Tiefe
     * (reversed Z) ist {@link #depthBias} negativ zu setzen; die Vergleichsfunktion der Tiefe setzt der Aufrufer.
     */
    public float gain = 1;
    private long drawn;

    public void add(NetLayer l) { layers.add(l); up.add(new Uploaded()); }
    public List<NetLayer> layers() { return List.copyOf(layers); }

    public void init(GL4 gl) { program = link(gl); }

    /** Lädt geänderte Ebenen hoch (je Stück alle drei Stufen). */
    public void update(GL4 gl) {
        for (int k = 0; k < layers.size(); k++) {
            NetLayer l = layers.get(k);
            Uploaded u = up.get(k);
            if (u.version == l.version()) continue;
            if (u.vao == 0) {
                int[] a = new int[1], b = new int[2];
                gl.glGenVertexArrays(1, a, 0);
                gl.glGenBuffers(2, b, 0);
                u.vao = a[0]; u.vbo = b[0]; u.cmd = b[1];
            }
            List<float[]> inst = new ArrayList<>();
            u.first = new int[l.items.size()][3];
            u.count = new int[l.items.size()][3];
            for (int i = 0; i < l.items.size(); i++) {
                NetLayer.Item it = l.items.get(i);
                for (int lod = 0; lod < 3; lod++) {
                    u.first[i][lod] = inst.size();
                    pack(it, lod, inst);
                    u.count[i][lod] = inst.size() - u.first[i][lod];
                }
            }
            FloatBuffer fb = Buffers.newDirectFloatBuffer(Math.max(1, inst.size()) * STRIDE);
            for (float[] f : inst) fb.put(f);
            fb.flip();
            gl.glBindVertexArray(u.vao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, u.vbo);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, Math.max(4L, (long) inst.size() * STRIDE * 4), fb, GL.GL_STATIC_DRAW);
            for (int a = 0; a < 4; a++) {
                gl.glEnableVertexAttribArray(a);
                gl.glVertexAttribPointer(a, 4, GL.GL_FLOAT, false, STRIDE * 4, a * 16L);
                gl.glVertexAttribDivisor(a, 1);
            }
            gl.glBindVertexArray(0);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
            u.instances = inst.size();
            u.version = l.version();
        }
    }

    /** Instanzen eines Stücks in Stufe lod (je 16 Werte: Anfang und Art, Ende und Breite, Farbe, Werte). */
    public static List<float[]> instances(NetLayer.Item it, int lod) {
        List<float[]> out = new ArrayList<>();
        pack(it, lod, out);
        return out;
    }

    static void pack(NetLayer.Item it, int lod, List<float[]> out) {
        float r = ((it.rgb >> 16) & 255) / 255f, g = ((it.rgb >> 8) & 255) / 255f, b = (it.rgb & 255) / 255f;
        float ramp = it.rgb < 0 || (it.values != null && it.kind == NetLayer.Kind.BAND) ? 1 : 0;
        float[] p = it.xyz, v = it.values;
        switch (it.kind) {
            case POINTS -> {
                for (int i = 0; i < it.points(); i++) {
                    float val = v == null ? 0 : v[i];
                    out.add(inst(p, i, i, 2, it.width, r, g, b, val, val, v != null && it.rgb < 0 ? 1 : 0));
                }
            }
            case GRAPH -> {
                for (int e = 0; e + 1 < it.edges.length; e += 2) out.add(inst(p, it.edges[e], it.edges[e + 1], 0, it.width, r, g, b, 0, 0, 0));
                for (int i = 0; i < it.points(); i++) out.add(inst(p, i, i, 2, it.width * 2.5f, r, g, b, 0, 0, 0));
            }
            default -> {
                int kind = switch (it.kind) { case ISOLINE -> 3; case RAY -> 4; case BAND -> 5; default -> 0; };
                int[] k = it.lod[lod];
                for (int s = 0; s + 1 < k.length; s++) {
                    float v0 = v == null ? 0 : v[k[s]], v1 = v == null ? 0 : v[k[s + 1]];
                    out.add(inst(p, k[s], k[s + 1], kind, it.width, r, g, b, v0, v1, ramp));
                }
                if (it.kind == NetLayer.Kind.ARROW && k.length >= 2) out.add(inst(p, k[k.length - 2], k[k.length - 1], 1, it.width, r, g, b, 0, 0, 0));
            }
        }
    }

    static float[] inst(float[] p, int a, int b, int kind, float width, float r, float g, float bl, float v0, float v1, float ramp) {
        return new float[]{p[3 * a], p[3 * a + 1], p[3 * a + 2], kind, p[3 * b], p[3 * b + 1], p[3 * b + 2], width, r, g, bl, 1, v0, v1, ramp, 0};
    }

    /** Zeichnet alle sichtbaren Ebenen in den gebundenen Bildspeicher (w × h Bildpunkte), Zeit t (s) für laufende Isolinien. */
    public void draw(GL4 gl, float[] viewProj, double[] eye, int w, int h, double t) {
        update(gl);
        gl.glUseProgram(program);
        gl.glUniformMatrix4fv(loc(gl, "uVP"), 1, false, viewProj, 0);
        gl.glUniform2f(loc(gl, "uViewport"), w, h);
        gl.glUniform1f(loc(gl, "uTime"), (float) t);
        gl.glUniform1f(loc(gl, "uFront"), (float) front);
        gl.glUniform1f(loc(gl, "uDepthBias"), (float) depthBias);
        gl.glUniform1f(loc(gl, "uGain"), gain);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glDisable(GL.GL_CULL_FACE);
        drawn = 0;
        for (int k = 0; k < layers.size(); k++) {
            NetLayer l = layers.get(k);
            Uploaded u = up.get(k);
            if (!l.visible || u.instances == 0) continue;
            float[] ramp = new float[24];
            int n = Math.min(8, l.ramp.stops.length);
            for (int i = 0; i < n; i++) { int c = l.ramp.stops[i]; ramp[3 * i] = ((c >> 16) & 255) / 255f; ramp[3 * i + 1] = ((c >> 8) & 255) / 255f; ramp[3 * i + 2] = (c & 255) / 255f; }
            gl.glUniform3fv(loc(gl, "uRamp"), 8, ramp, 0);
            gl.glUniform1i(loc(gl, "uRampN"), n);
            gl.glUniform2f(loc(gl, "uRampRange"), (float) l.ramp.min, (float) l.ramp.max);
            IntBuffer cmd = Buffers.newDirectIntBuffer(4 * l.items.size());
            int nc = 0;
            for (int i = 0; i < l.items.size(); i++) {
                NetLayer.Item it = l.items.get(i);
                double d = Math.sqrt(sq(it.cx - eye[0]) + sq(it.cy - eye[1]) + sq(it.cz - eye[2]));
                int lod = l.lodFor(it, d);
                int c = u.count[i][lod];
                if (c == 0) continue;
                cmd.put(4).put(c).put(0).put(u.first[i][lod]);
                nc++;
                drawn += c;
            }
            cmd.flip();
            if (nc == 0) continue;
            gl.glBindBuffer(GL4.GL_DRAW_INDIRECT_BUFFER, u.cmd);
            gl.glBufferData(GL4.GL_DRAW_INDIRECT_BUFFER, nc * 16L, cmd, GL4.GL_STREAM_DRAW);
            gl.glBindVertexArray(u.vao);
            gl.glMultiDrawArraysIndirect(GL.GL_TRIANGLE_STRIP, 0, nc, 16);
        }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL4.GL_DRAW_INDIRECT_BUFFER, 0);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(0);
    }

    static double sq(double v) { return v * v; }

    /** Instanzen im letzten Bild. */
    public long drawn() { return drawn; }

    public String info() {
        long all = 0, items = 0;
        for (int k = 0; k < layers.size(); k++) { all += up.get(k).instances; items += layers.get(k).items.size(); }
        return String.format(Locale.GERMAN, "Netzblick: %d Ebenen, %,d Stücke, %,d Instanzen (alle Stufen), %,d gezeichnet", layers.size(), items, all, drawn);
    }

    public void dispose(GL4 gl) {
        for (Uploaded u : up) if (u.vao != 0) { gl.glDeleteVertexArrays(1, new int[]{u.vao}, 0); gl.glDeleteBuffers(2, new int[]{u.vbo, u.cmd}, 0); }
        if (program != 0) gl.glDeleteProgram(program);
        program = 0;
    }

    private int loc(GL4 gl, String n) { return gl.glGetUniformLocation(program, n); }

    public static String source(String file) {
        try (InputStream in = NetviewGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("netview: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("netview: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int link(GL4 gl) {
        int p = gl.glCreateProgram();
        for (String[] s : new String[][]{{"netview.vert", "v"}, {"netview.frag", "f"}}) {
            int sh = gl.glCreateShader(s[1].equals("v") ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + source(s[0])}, null, 0);
            gl.glCompileShader(sh);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] b = new byte[8192];
                int[] len = new int[1];
                gl.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
                throw new IllegalStateException("netview: " + s[0] + " lässt sich nicht übersetzen:\n" + new String(b, 0, len[0], StandardCharsets.UTF_8));
            }
            gl.glAttachShader(p, sh);
            gl.glDeleteShader(sh);
        }
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("netview: Programm lässt sich nicht binden");
        return p;
    }
}
