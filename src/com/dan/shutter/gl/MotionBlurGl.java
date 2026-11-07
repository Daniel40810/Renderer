package com.dan.shutter.gl;

import com.dan.shutter.MotionBlur;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.HashMap;
import java.util.Map;

/**
 * Bewegungsunschärfe auf der Grafikkarte (GL 4.3), McGuire u. a. 2012: {@code mb_velocity} (halbe Bewegung je Bildpunkt aus
 * Tiefe und vorigem Blick, mal T/Δt), {@code mb_tilemax} (je Kachel die längste), {@code mb_neighbor} (3 × 3 Nachbarn),
 * {@code mb_reconstruct} (sammeln längs der Bewegung). Ergebnis: eine eigene Textur RGBA16F gleicher Größe. Danach: Mischen
 * aus, Framebuffer und Viewport verändert.
 */
public final class MotionBlurGl {

    /** Was die App je Bild mitgibt. */
    public static final class View {
        public int colorTexture, depthTexture, width = 1, height = 1;
        /** Bildraum jetzt (NDC, Tiefe wie im Tiefenpuffer) → Clip des vorigen Bilds, Spalten wie OpenGL. */
        public float[] reproj;
        public boolean reversedZ;
        public double near = 0.1, far = 1e5;
        /** Belichtungszeit / Szenenzeit zwischen den Bildern ({@link MotionBlur#scale}). */
        public double scale;
    }

    private final MotionBlur mb;
    private boolean ready;
    private int pVel, pTile, pNb, pRec, vao, fbo, velTex, tileTex, nbTex, outTex, w, h, k, tw, th;
    private final Map<Integer, Map<String, Integer>> locs = new HashMap<>();

    public MotionBlurGl(MotionBlur mb) { this.mb = mb; }

    public boolean ready() { return ready; }

    public void init(GL4 gl) {
        String vs = ShutterGl.read("fullscreen.vert");
        pVel = ShutterGl.program(gl, vs, ShutterGl.read("mb_velocity.frag"));
        pTile = ShutterGl.program(gl, vs, ShutterGl.read("mb_tilemax.frag"));
        pNb = ShutterGl.program(gl, vs, ShutterGl.read("mb_neighbor.frag"));
        pRec = ShutterGl.program(gl, vs, ShutterGl.read("mb_reconstruct.frag"));
        int[] a = new int[1];
        gl.glGenVertexArrays(1, a, 0);
        vao = a[0];
        gl.glGenFramebuffers(1, a, 0);
        fbo = a[0];
        ready = true;
    }

    /** Wendet die Unschärfe an und gibt die neue Textur zurück (ohne Bewegung oder aus: colorTexture). */
    public int apply(GL4 gl, View v) {
        if (!ready || !mb.enabled || v.scale <= 0 || v.reproj == null) return v.colorTexture;
        int kk = mb.tileFor(v.height);
        if (v.width != w || v.height != h || kk != k) {
            free(gl);
            w = v.width;
            h = v.height;
            k = kk;
            tw = (w + k - 1) / k;
            th = (h + k - 1) / k;
            velTex = ShutterGl.tex(gl, GL4.GL_RGBA32F, w, h);
            tileTex = ShutterGl.tex(gl, GL4.GL_RG16F, tw, th);
            nbTex = ShutterGl.tex(gl, GL4.GL_RG16F, tw, th);
            outTex = ShutterGl.tex(gl, GL4.GL_RGBA16F, w, h);
        }
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBindVertexArray(vao);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        // 1 Bewegung je Bildpunkt
        target(gl, velTex, w, h);
        gl.glUseProgram(pVel);
        bind(gl, pVel, "uDepth", 0, v.depthTexture);
        int l = loc(gl, pVel, "uReproj");
        if (l >= 0) gl.glUniformMatrix4fv(l, 1, false, v.reproj, 0);
        seti(gl, pVel, "uReversed", v.reversedZ ? 1 : 0);
        setf(gl, pVel, "uNear", (float) v.near);
        setf(gl, pVel, "uFar", (float) v.far);
        setf(gl, pVel, "uScale", (float) v.scale);
        setf(gl, pVel, "uK", k);
        set2(gl, pVel, "uSize", w, h);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        // 2 Kacheln
        target(gl, tileTex, tw, th);
        gl.glUseProgram(pTile);
        bind(gl, pTile, "uVel", 0, velTex);
        seti(gl, pTile, "uK", k);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        // 3 Nachbarn
        target(gl, nbTex, tw, th);
        gl.glUseProgram(pNb);
        bind(gl, pNb, "uTileMax", 0, tileTex);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        // 4 sammeln
        target(gl, outTex, w, h);
        gl.glUseProgram(pRec);
        bind(gl, pRec, "uColor", 0, v.colorTexture);
        bind(gl, pRec, "uVel", 1, velTex);
        bind(gl, pRec, "uNeighbor", 2, nbTex);
        seti(gl, pRec, "uK", k);
        seti(gl, pRec, "uS", Math.max(3, mb.samples | 1));
        setf(gl, pRec, "uSoftZ", (float) mb.softZ);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glUseProgram(0);
        gl.glBindVertexArray(0);
        return outTex;
    }

    private void target(GL4 gl, int t, int tw, int th) {
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, t, 0);
        gl.glViewport(0, 0, tw, th);
    }

    private void free(GL4 gl) {
        if (velTex != 0) gl.glDeleteTextures(4, new int[]{velTex, tileTex, nbTex, outTex}, 0);
        velTex = tileTex = nbTex = outTex = 0;
    }

    public void dispose(GL4 gl) {
        if (!ready) return;
        for (int p : new int[]{pVel, pTile, pNb, pRec}) gl.glDeleteProgram(p);
        free(gl);
        gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        locs.clear();
        w = h = 0;
        ready = false;
    }

    private int loc(GL4 gl, int p, String n) { return locs.computeIfAbsent(p, q -> new HashMap<>()).computeIfAbsent(n, q -> gl.glGetUniformLocation(p, q)); }

    private void seti(GL4 gl, int p, String n, int v) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform1i(l, v); }

    private void setf(GL4 gl, int p, String n, float v) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform1f(l, v); }

    private void set2(GL4 gl, int p, String n, float a, float b) { int l = loc(gl, p, n); if (l >= 0) gl.glUniform2f(l, a, b); }

    private void bind(GL4 gl, int p, String n, int unit, int t) {
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t);
        seti(gl, p, n, unit);
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }
}
