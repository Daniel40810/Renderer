package com.dan.clipmap.gl;

import com.dan.clipmap.Clipmap;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Die Grafikseite der Clipmap: ein Gitter aus {@code grid}² Punkten (einmal, als Eckpuffer mit (k, l)), die Indizes in
 * 8 × 8 Blöcken, die Höhen aller Stufen in einem Texturfeld (R32F, {@code texSize}² × Stufen). {@link #upload} lädt nur
 * die Rechtecke hoch, die der Ladefaden neu gefüllt hat; {@link #draw} zeichnet jede Stufe mit den Blöcken, die im Bild
 * liegen und nicht ganz von der feineren Stufe bedeckt sind.
 * <p>
 * Die Shader schreibt die App (Licht, Material, Dunst sind ihre Sache); den Baustein {@code clipmap.glsl} setzt sie ein,
 * im Scheitelshader mit {@code #define CLIPMAP_VERTEX} davor. Eingang {@code aClipGrid} auf Stelle {@link #ATTRIB}.
 */
public final class ClipmapGl {
    /** Stelle des Eingangs aClipGrid im Scheitelshader. */
    public static final int ATTRIB = 0;
    /** Blöcke je Kante einer Stufe (Auswahl nach Sichtfeld). */
    public static final int BLOCKS = 8;

    /** Der Baustein als Text. */
    public static String glsl() {
        try (InputStream in = ClipmapGl.class.getResourceAsStream("clipmap.glsl")) {
            if (in == null) throw new IllegalStateException("clipmap.glsl fehlt im Klassenpfad");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private int vao, vbo, ibo, tex, grid, texSize, levels, blockQuads;
    private FloatBuffer up;
    /** Zahlen des letzten Bilds: gezeichnete Dreiecke, Blöcke, verworfene Blöcke, ms für das Hochladen. */
    private long triangles;
    private int drawn, culled, covered;
    private double uploadMs, uploadMb;

    /** Legt Gitter, Indizes und Textur für c an (im GL-Faden, einmal oder nach einem Wechsel der Stufe). */
    public void init(GL4 gl, Clipmap c) {
        dispose(gl);
        grid = c.grid;
        texSize = c.texSize;
        levels = c.levels();
        blockQuads = (grid - 1) / BLOCKS;
        int m = grid;
        FloatBuffer v = Buffers.newDirectFloatBuffer(m * m * 2);
        for (int l = 0; l < m; l++) for (int k = 0; k < m; k++) v.put(k).put(l);
        v.flip();
        int q = grid - 1;
        IntBuffer ix = Buffers.newDirectIntBuffer(q * q * 6);
        for (int by = 0; by < BLOCKS; by++)
            for (int bx = 0; bx < BLOCKS; bx++)
                for (int l = by * blockQuads; l < (by + 1) * blockQuads; l++)
                    for (int k = bx * blockQuads; k < (bx + 1) * blockQuads; k++) {
                        int a = l * m + k, b = a + 1, cc = a + m, d = cc + 1;
                        // Diagonale wechselnd (wie ein Rautenmuster), damit Hänge in keiner Richtung bevorzugt werden
                        if (((k + l) & 1) == 0) ix.put(a).put(cc).put(b).put(b).put(cc).put(d);
                        else ix.put(a).put(cc).put(d).put(a).put(d).put(b);
                    }
        ix.flip();
        int[] t = new int[3];
        gl.glGenVertexArrays(1, t, 0);
        vao = t[0];
        gl.glGenBuffers(2, t, 0);
        vbo = t[0];
        ibo = t[1];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) v.capacity() * 4, v, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(ATTRIB);
        gl.glVertexAttribPointer(ATTRIB, 2, GL.GL_FLOAT, false, 8, 0);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) ix.capacity() * 4, ix, GL.GL_STATIC_DRAW);
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        gl.glGenTextures(1, t, 0);
        tex = t[0];
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, tex);
        gl.glTexStorage3D(GL2ES3.GL_TEXTURE_2D_ARRAY, 1, GL.GL_R32F, texSize, texSize, levels);
        gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, 0);
        up = Buffers.newDirectFloatBuffer(texSize * texSize);
    }

    public boolean ready() { return vao != 0; }

    /** Lädt die neu gefüllten Rechtecke hoch (im GL-Faden, je Bild vor {@link #draw}). */
    public void upload(GL4 gl, Clipmap c) {
        if (vao == 0) init(gl, c);
        long t0 = System.nanoTime();
        long[] bytes = {0};
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, tex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        c.drain((level, i0, j0, w, h, store, t) -> {
            if (w >= t || h >= t) { i0 = 0; j0 = 0; w = t; h = t; }   // ganze Schicht
            // Rechteck ringförmig in bis zu vier Teile zerlegen
            int si = Math.floorMod(i0, t), sj = Math.floorMod(j0, t);
            int w1 = Math.min(w, t - si), h1 = Math.min(h, t - sj);
            int[][] parts = {{si, sj, w1, h1}, {0, sj, w - w1, h1}, {si, 0, w1, h - h1}, {0, 0, w - w1, h - h1}};
            for (int[] p : parts) {
                if (p[2] <= 0 || p[3] <= 0) continue;
                up.clear();
                for (int y = 0; y < p[3]; y++) up.put(store, (p[1] + y) * t + p[0], p[2]);
                up.flip();
                gl.glTexSubImage3D(GL2ES3.GL_TEXTURE_2D_ARRAY, 0, p[0], p[1], level, p[2], p[3], 1, GL2ES2.GL_RED, GL.GL_FLOAT, up);
                bytes[0] += (long) p[2] * p[3] * 4;
            }
        });
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, 0);
        if (bytes[0] > 0) {
            uploadMs = uploadMs * 0.8 + (System.nanoTime() - t0) / 1e6 * 0.2;
            uploadMb = bytes[0] / 1e6;
        }
    }

    /**
     * Zeichnet alle geladenen Stufen mit dem Programm prog (es muss schon benutzt sein, seine übrigen Werte setzt die
     * App). vp: Ansicht mal Projektion (Spalten zuerst, wie für glUniformMatrix4fv); eye: das Auge (x, y, z); unit:
     * Texturstelle für die Höhen.
     */
    public void draw(GL4 gl, int prog, Clipmap c, float[] vp, double[] eye, int unit) {
        if (vao == 0) return;
        Clipmap.Window[] win = c.windows();
        double[][] planes = planes(vp);
        bindCommon(gl, prog, c, eye, unit);
        gl.glBindVertexArray(vao);
        long tri = 0;
        int nd = 0, nc = 0, nv = 0;
        for (int L = 0; L < win.length; L++) {
            Clipmap.Window w = win[L];
            if (!w.loaded()) continue;
            double[] r = w.rect(c);
            double[] in = bindLevel(gl, prog, c, win, L);
            double bs = blockQuads * w.cell();
            for (int by = 0; by < BLOCKS; by++)
                for (int bx = 0; bx < BLOCKS; bx++) {
                    double x0 = r[0] + bx * bs, z0 = r[1] + by * bs, x1 = x0 + bs, z1 = z0 + bs;
                    if (in != null && x0 >= in[0] && x1 <= in[2] && z0 >= in[1] && z1 <= in[3]) { nc++; continue; }
                    // tiefster Punkt mit Erdkrümmung: die fernste Ecke
                    double dx = Math.max(Math.abs(x0 - eye[0]), Math.abs(x1 - eye[0])), dz = Math.max(Math.abs(z0 - eye[2]), Math.abs(z1 - eye[2]));
                    double y0 = Math.min(w.min(), c.noData) - c.drop(Math.hypot(dx, dz)) - 1, y1 = Math.max(w.max(), c.noData) + 1;
                    if (!visible(planes, x0, y0, z0, x1, y1, z1)) { nv++; continue; }
                    int first = (by * BLOCKS + bx) * blockQuads * blockQuads * 6;
                    gl.glDrawElements(GL.GL_TRIANGLES, blockQuads * blockQuads * 6, GL.GL_UNSIGNED_INT, (long) first * 4);
                    tri += (long) blockQuads * blockQuads * 2;
                    nd++;
                }
        }
        gl.glBindVertexArray(0);
        triangles = tri;
        drawn = nd;
        covered = nc;
        culled = nv;
    }

    /** Werte für alle Stufen setzen (Textur auf unit, Größen, Auge, Ersatzwert); das Programm muss benutzt sein. */
    public void bindCommon(GL4 gl, int prog, Clipmap c, double[] eye, int unit) {
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, tex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        u1i(gl, prog, "uClipH", unit);
        u1i(gl, prog, "uClipT", texSize);
        u1i(gl, prog, "uClipM", grid);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uClipCam"), (float) eye[0], (float) eye[1], (float) eye[2]);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uClipNoData"), c.noData);
    }

    /**
     * Werte einer Stufe setzen (Stufe, gröbere da, Index des ersten Scheitels, Ursprung, Zellweite, Krümmung, Rechteck der
     * feineren Stufe). Gibt dieses Rechteck zurück (null ohne feinere Stufe).
     */
    public double[] bindLevel(GL4 gl, int prog, Clipmap c, Clipmap.Window[] win, int L) {
        Clipmap.Window w = win[L];
        int h = c.half();
        double curv = c.curvatureRadius > 0 ? 1 / (2 * c.curvatureRadius) : 0;
        double[] in = null;
        if (L > 0 && win[L - 1].loaded()) {
            double[] f = win[L - 1].rect(c);
            double e = 0.25 * win[L - 1].cell();
            in = new double[]{f[0] + e, f[1] + e, f[2] - e, f[3] - e};
        }
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uClipLevel"), L);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uClipCoarse"), L + 1 < win.length && win[L + 1].loaded() ? 1 : 0);
        gl.glUniform2i(gl.glGetUniformLocation(prog, "uClipIdx"), w.ci() - h, w.cj() - h);
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uClipGeo"), (float) c.originX, (float) c.originZ, (float) w.cell(), (float) curv);
        int li = gl.glGetUniformLocation(prog, "uClipInner");
        if (in == null) gl.glUniform4f(li, 1, 1, -1, -1);
        else gl.glUniform4f(li, (float) in[0], (float) in[1], (float) in[2], (float) in[3]);
        return in;
    }

    static void u1i(GL4 gl, int prog, String n, int v) { gl.glUniform1i(gl.glGetUniformLocation(prog, n), v); }

    /** Die vier Seitenebenen des Sichtfelds aus vp (ohne Nah- und Fernebene, gilt für beide Tiefenbereiche). */
    static double[][] planes(float[] m) {
        double[] r0 = {m[0], m[4], m[8], m[12]}, r1 = {m[1], m[5], m[9], m[13]}, r3 = {m[3], m[7], m[11], m[15]};
        return new double[][]{add(r3, r0, 1), add(r3, r0, -1), add(r3, r1, 1), add(r3, r1, -1)};
    }

    static double[] add(double[] a, double[] b, double s) { return new double[]{a[0] + s * b[0], a[1] + s * b[1], a[2] + s * b[2], a[3] + s * b[3]}; }

    /** Liegt der Quader wenigstens teilweise auf der Innenseite aller Ebenen? */
    static boolean visible(double[][] pl, double x0, double y0, double z0, double x1, double y1, double z1) {
        for (double[] p : pl) {
            // die Ecke, die am weitesten auf der Innenseite liegt
            double x = p[0] >= 0 ? x1 : x0, y = p[1] >= 0 ? y1 : y0, z = p[2] >= 0 ? z1 : z0;
            if (p[0] * x + p[1] * y + p[2] * z + p[3] < 0) return false;
        }
        return true;
    }

    /** „Gelände: 1,2 Mio. Dreiecke in 96 Blöcken (210 außer Sicht, 118 bedeckt) · 0,4 MB in 0,6 ms hochgeladen“ */
    public String info() {
        return String.format(Locale.GERMAN, "%.2f Mio. Dreiecke in %d Blöcken (%d außer Sicht, %d bedeckt) · zuletzt %.2f MB in %.1f ms hochgeladen",
                triangles / 1e6, drawn, culled, covered, uploadMb, uploadMs);
    }

    public long triangles() { return triangles; }

    public void dispose(GL4 gl) {
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (vbo != 0) gl.glDeleteBuffers(2, new int[]{vbo, ibo}, 0);
        if (tex != 0) gl.glDeleteTextures(1, new int[]{tex}, 0);
        vao = vbo = ibo = tex = 0;
    }
}
