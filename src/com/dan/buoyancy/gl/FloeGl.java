package com.dan.buoyancy.gl;

import com.dan.buoyancy.Floes;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Eisschollen aus {@link Floes} auf der Grafikkarte (seit LituyaBay1958 W6 Runde 3): je Form ein Netz (Deckel,
 * gebrochene Kante, Seiten bis unter Wasser), je Stück eine Instanz mit Durchmesser, Höhe, Drehung und Neigung.
 * Oben Schnee und altes Eis, an den Seiten blaues Gletschereis, an der Wasserlinie nass; die Seiten scheinen gegen die
 * Sonne blau durch. Der Schatten fällt als Abbild von Deckel und Kante entlang der Sonne auf den Wasserspiegel
 * (abgedunkelt durch Multiplizieren, ohne Tiefe zu schreiben); die Seiten unter Wasser verdeckt das Wasser.
 * <p>
 * Aufruf nach dem Wasser (mit Tiefe) ins gebundene HDR-Ziel, Tiefentest mit {@link View#reversedZ}.
 */
public final class FloeGl {
    /** Ansicht: Projektion·Blick (Spalten), Auge, umgekehrte Tiefe. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean reversedZ;
    }

    /** Licht linear (HDR): Sonne, Himmel, Dunst; Stärke des Schattens auf dem Wasser (0..1). */
    public static final class Light {
        public double[] sunDir = {0.4, 0.6, 0.3}, sunColor = {2.6, 2.5, 2.3}, skyColor = {0.3, 0.38, 0.5}, fogColor = {0.6, 0.65, 0.7};
        public double fogDistance = 25_000, shadow = 0.45;
    }

    /** Ecken des Umrisses je Form. */
    public static final int CORNERS = 11;
    private int prog, vao, vbo, ibo, inst;
    private final int[] first = new int[Floes.VARIANTS], cnt = new int[Floes.VARIANTS];
    private int capFirst[] = new int[Floes.VARIANTS], capCount[] = new int[Floes.VARIANTS];
    private int instCap;
    private FloatBuffer direct;
    private final int[] start = new int[Floes.VARIANTS], count = new int[Floes.VARIANTS];
    private boolean ready;
    private int drawn, triangles;

    public boolean ready() { return ready; }
    public int drawn() { return drawn; }
    public int triangles() { return triangles; }

    public void init(GL4 gl) {
        prog = BuoyancyGl.program(gl, "#version 430 core\n" + BuoyancyGl.read("floe.vert"), "#version 430 core\n" + BuoyancyGl.read("floe.frag"));
        List<Float> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        // je Form: erst Deckel und Kante (für den Schatten), dann die Seiten
        int base = 0;
        for (int f = 0; f < Floes.VARIANTS; f++) {
            float[] o = Floes.outline(f, CORNERS);
            capFirst[f] = idx.size();
            first[f] = idx.size();
            int k = CORNERS;
            // Deckel: Mitte und eingerückter Rand (0,86) auf 1, leicht gewölbt
            int c = base;
            put(v, 0, 1.06f, 0, 0, 1, 0, 0);
            base++;
            int top = base;
            // Rand des Deckels uneben (0,82 bis 1) [G]
            float[] yj = new float[k];
            for (int i = 0; i < k; i++) yj[i] = 0.82f + 0.18f * (float) (Math.abs(Math.sin(i * 12.9898 + f * 78.233) * 43758.5453) % 1.0);
            for (int i = 0; i < k; i++) { put(v, o[2 * i] * 0.86f, yj[i], o[2 * i + 1] * 0.86f, 0, 1, 0, 0); base++; }
            for (int i = 0; i < k; i++) { idx.add(c); idx.add(top + i); idx.add(top + (i + 1) % k); }
            // Kante: vom eingerückten Rand auf 1 zum vollen Umriss auf 0,8 (gebrochen, je Fläche eigene Normale)
            for (int i = 0; i < k; i++) {
                int i1 = (i + 1) % k;
                float ax = o[2 * i], az = o[2 * i + 1], bx = o[2 * i1], bz = o[2 * i1 + 1];
                float mx = (ax + bx) / 2, mz = (az + bz) / 2, ml = (float) Math.hypot(mx, mz) + 1e-6f;
                // Normale der Schräge: nach außen und oben (Kante 0,14 breit, 0,2 hoch, in Einheiten von Durchmesser und Höhe)
                float nx = mx / ml * 0.2f, ny = 0.14f, nz = mz / ml * 0.2f;
                int q = base;
                put(v, ax * 0.86f, yj[i], az * 0.86f, nx, ny, nz, 1);
                put(v, bx * 0.86f, yj[i1], bz * 0.86f, nx, ny, nz, 1);
                put(v, bx, 0.62f * yj[i1], bz, nx, ny, nz, 1);
                put(v, ax, 0.62f * yj[i], az, nx, ny, nz, 1);
                base += 4;
                idx.add(q); idx.add(q + 2); idx.add(q + 1);
                idx.add(q); idx.add(q + 3); idx.add(q + 2);
            }
            capCount[f] = idx.size() - capFirst[f];
            // Seiten: von 0,8 bis −1,4 (unter Wasser), je Fläche eine Normale
            for (int i = 0; i < k; i++) {
                int i1 = (i + 1) % k;
                float ax = o[2 * i], az = o[2 * i + 1], bx = o[2 * i1], bz = o[2 * i1 + 1];
                float nx = bz - az, nz = -(bx - ax);
                float mx = (ax + bx) / 2, mz = (az + bz) / 2;
                if (nx * mx + nz * mz < 0) { nx = -nx; nz = -nz; }
                int q = base;
                put(v, ax, 0.62f * yj[i], az, nx, 0, nz, 2);
                put(v, bx, 0.62f * yj[i1], bz, nx, 0, nz, 2);
                put(v, bx * 0.92f, -1.4f, bz * 0.92f, nx, -0.05f, nz, 2);
                put(v, ax * 0.92f, -1.4f, az * 0.92f, nx, -0.05f, nz, 2);
                base += 4;
                idx.add(q); idx.add(q + 2); idx.add(q + 1);
                idx.add(q); idx.add(q + 3); idx.add(q + 2);
            }
            cnt[f] = idx.size() - first[f];
        }
        float[] va = new float[v.size()];
        for (int i = 0; i < va.length; i++) va[i] = v.get(i);
        int[] ia = idx.stream().mapToInt(Integer::intValue).toArray();
        int[] b = new int[3];
        gl.glGenVertexArrays(1, b, 0);
        vao = b[0];
        gl.glGenBuffers(3, b, 0);
        vbo = b[0];
        ibo = b[1];
        inst = b[2];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, va.length * 4L, Buffers.newDirectFloatBuffer(va), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 28, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 28, 12);
        gl.glEnableVertexAttribArray(2);
        gl.glVertexAttribPointer(2, 1, GL.GL_FLOAT, false, 28, 24);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        IntBuffer ib = Buffers.newDirectIntBuffer(ia);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, ia.length * 4L, ib, GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst);
        for (int a = 0; a < 3; a++) {
            gl.glEnableVertexAttribArray(3 + a);
            gl.glVertexAttribPointer(3 + a, 4, GL.GL_FLOAT, false, Floes.STRIDE * 4, a * 16L);
            gl.glVertexAttribDivisor(3 + a, 1);
        }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        ready = true;
    }

    private static void put(List<Float> v, float x, float y, float z, float nx, float ny, float nz, float part) {
        v.add(x); v.add(y); v.add(z); v.add(nx); v.add(ny); v.add(nz); v.add(part);
    }

    /** Stücke hochladen (nach {@link Floes#fill}). */
    public void update(GL4 gl, Floes f) {
        System.arraycopy(f.start, 0, start, 0, Floes.VARIANTS);
        System.arraycopy(f.count, 0, count, 0, Floes.VARIANTS);
        int n = f.total * Floes.STRIDE;
        if (direct == null || direct.capacity() < n) direct = Buffers.newDirectFloatBuffer(Math.max(n, 1024) * 3 / 2);
        direct.clear();
        direct.put(f.data, 0, n).flip();
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst);
        if (n * 4L > instCap) { instCap = (int) Math.max(n * 4L * 3 / 2, 4096); gl.glBufferData(GL.GL_ARRAY_BUFFER, instCap, null, GL4.GL_DYNAMIC_DRAW); }
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, n * 4L, direct);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    /** Schatten auf das Wasser, dann die Stücke. Lässt Tiefentest und Mischen aus. */
    public void draw(GL4 gl, View view, Light light, double time) {
        if (!ready) return;
        int tot = 0;
        for (int c : count) tot += c;
        drawn = tot;
        triangles = 0;
        if (tot == 0) return;
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(loc(gl, "uVP"), 1, false, view.viewProj, 0);
        gl.glUniform3f(loc(gl, "uCam"), (float) view.eye[0], (float) view.eye[1], (float) view.eye[2]);
        double[] s = light.sunDir;
        double sl = Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]) + 1e-12;
        gl.glUniform3f(loc(gl, "uSunDir"), (float) (s[0] / sl), (float) (s[1] / sl), (float) (s[2] / sl));
        gl.glUniform3f(loc(gl, "uSunColor"), (float) light.sunColor[0], (float) light.sunColor[1], (float) light.sunColor[2]);
        gl.glUniform3f(loc(gl, "uSkyColor"), (float) light.skyColor[0], (float) light.skyColor[1], (float) light.skyColor[2]);
        gl.glUniform4f(loc(gl, "uFog"), (float) light.fogColor[0], (float) light.fogColor[1], (float) light.fogColor[2], (float) light.fogDistance);
        gl.glUniform1f(loc(gl, "uTime"), (float) time);
        gl.glBindVertexArray(vao);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(view.reversedZ ? GL.GL_GEQUAL : GL.GL_LEQUAL);
        gl.glDisable(GL.GL_CULL_FACE);
        // Schatten: nur bei Sonne über 4°, ohne Tiefe zu schreiben, multiplizierend
        double el = s[1] / sl;
        if (light.shadow > 0 && el > 0.07) {
            double k = light.shadow * Math.min(1, (el - 0.07) / 0.1);
            gl.glUniform1i(loc(gl, "uShadow"), 1);
            gl.glUniform1f(loc(gl, "uShadowK"), (float) k);
            gl.glDepthMask(false);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ZERO, GL.GL_SRC_COLOR);
            for (int f = 0; f < Floes.VARIANTS; f++) {
                if (count[f] == 0) continue;
                gl.glDrawElementsInstancedBaseInstance(GL.GL_TRIANGLES, capCount[f], GL.GL_UNSIGNED_INT, capFirst[f] * 4L, count[f], start[f]);
            }
            gl.glDisable(GL.GL_BLEND);
        }
        gl.glUniform1i(loc(gl, "uShadow"), 0);
        gl.glDepthMask(true);
        for (int f = 0; f < Floes.VARIANTS; f++) {
            if (count[f] == 0) continue;
            gl.glDrawElementsInstancedBaseInstance(GL.GL_TRIANGLES, cnt[f], GL.GL_UNSIGNED_INT, first[f] * 4L, count[f], start[f]);
            triangles += cnt[f] / 3 * count[f];
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    private int loc(GL4 gl, String n) { return gl.glGetUniformLocation(prog, n); }

    public void dispose(GL4 gl) {
        if (!ready) return;
        gl.glDeleteProgram(prog);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        gl.glDeleteBuffers(3, new int[]{vbo, ibo, inst}, 0);
        ready = false;
    }
}
