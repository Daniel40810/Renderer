package com.dan.moss.gl;

import com.dan.moss.MossTexture;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;

/**
 * Lädt Moostexturen auf die Grafikkarte (seit LituyaBay1958 W6 Runde 7): ein GL_TEXTURE_2D_ARRAY in RGBA16F, je
 * Textur eine Ebene; rgb = Albedo · Verdeckung (linear, die gesehene Farbe ohne Licht), a = Höhe 0..1. Mit Mip-Stufen,
 * wiederholt, anisotrop gefiltert (wenn da). So erwarten es {@code com.dan.forest.gl.ForestGl.Light#mossTexture} und
 * {@code com.dan.tree.gl.TreeGl.Light#mossTexture}.
 */
public final class MossGl {
    private MossGl() { }

    /** Alle Texturen (gleiche Kante) als Ebenen 0, 1, … eines Arrays; gibt die Kennung zurück. */
    public static int upload(GL4 gl, MossTexture... ts) {
        int n = ts[0].n;
        for (MossTexture t : ts) if (t.n != n) throw new IllegalArgumentException("Moostexturen brauchen dieselbe Kante");
        int[] id = new int[1];
        gl.glGenTextures(1, id, 0);
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, id[0]);
        int levels = 1 + (int) Math.floor(Math.log(n) / Math.log(2));
        gl.glTexStorage3D(GL2ES3.GL_TEXTURE_2D_ARRAY, levels, GL.GL_RGBA16F, n, n, ts.length);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(4 * n * n);
        for (int l = 0; l < ts.length; l++) {
            fb.clear();
            fb.put(rgba(ts[l]));
            fb.flip();
            gl.glTexSubImage3D(GL2ES3.GL_TEXTURE_2D_ARRAY, 0, 0, 0, l, n, n, 1, GL.GL_RGBA, GL.GL_FLOAT, fb);
        }
        gl.glGenerateMipmap(GL2ES3.GL_TEXTURE_2D_ARRAY);
        gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
        if (gl.isExtensionAvailable("GL_EXT_texture_filter_anisotropic") || gl.isExtensionAvailable("GL_ARB_texture_filter_anisotropic"))
            gl.glTexParameterf(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MAX_ANISOTROPY_EXT, 8f);
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, 0);
        return id[0];
    }

    /** rgb = Albedo · Verdeckung, a = Höhe, je Bildpunkt (Zeile für Zeile). */
    public static float[] rgba(MossTexture t) {
        int n = t.n;
        float[] o = new float[4 * n * n];
        for (int i = 0; i < n * n; i++) {
            o[4 * i] = t.albedo[3 * i] * t.ao[i];
            o[4 * i + 1] = t.albedo[3 * i + 1] * t.ao[i];
            o[4 * i + 2] = t.albedo[3 * i + 2] * t.ao[i];
            o[4 * i + 3] = t.height[i];
        }
        return o;
    }

    public static void dispose(GL4 gl, int id) { if (id != 0) gl.glDeleteTextures(1, new int[]{id}, 0); }
}
