package com.dan.lichen.gl;

import com.dan.lichen.LichenCard;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;

import java.nio.ByteBuffer;

/**
 * Lädt Karten von Bartflechten auf die Grafikkarte (seit LituyaBay1958 W6 Runde 7): ein GL_TEXTURE_2D_ARRAY in
 * SRGB8_ALPHA8 (die Farbe wird beim Lesen linear), je Karte eine Ebene, Alpha = Deckung. Mip-Stufen werden auf dem
 * Prozessor gerechnet: Farbe mit der Deckung gewichtet (kein Saum), Deckung gemittelt (so bleibt die Deckung über die
 * Entfernung gleich, wenn der Leser sie als Wahrscheinlichkeit nimmt, etwa mit gewürfeltem Alpha und Kantenglättung über
 * die Zeit). So erwartet es {@code com.dan.forest.gl.ForestGpu#beardTexture}.
 */
public final class LichenGl {
    private LichenGl() { }

    public static int upload(GL4 gl, LichenCard... cards) {
        int w = cards[0].w, h = cards[0].h;
        for (LichenCard c : cards) if (c.w != w || c.h != h) throw new IllegalArgumentException("Karten brauchen dieselbe Größe");
        int levels = 1 + (int) Math.floor(Math.log(Math.max(w, h)) / Math.log(2));
        int[] id = new int[1];
        gl.glGenTextures(1, id, 0);
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, id[0]);
        gl.glTexStorage3D(GL2ES3.GL_TEXTURE_2D_ARRAY, levels, GL.GL_SRGB8_ALPHA8, w, h, cards.length);
        for (int l = 0; l < cards.length; l++) {
            float[] p = cards[l].rgba;
            int lw = w, lh = h;
            for (int m = 0; m < levels; m++) {
                ByteBuffer bb = Buffers.newDirectByteBuffer(4 * lw * lh);
                for (int i = 0; i < lw * lh; i++) {
                    bb.put((byte) srgb(p[4 * i])).put((byte) srgb(p[4 * i + 1])).put((byte) srgb(p[4 * i + 2]))
                            .put((byte) Math.round(Math.max(0, Math.min(1, p[4 * i + 3])) * 255));
                }
                bb.flip();
                gl.glTexSubImage3D(GL2ES3.GL_TEXTURE_2D_ARRAY, m, 0, 0, l, lw, lh, 1, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
                if (lw == 1 && lh == 1) break;
                int nw = Math.max(1, lw / 2), nh = Math.max(1, lh / 2);
                p = down(p, lw, lh, nw, nh);
                lw = nw; lh = nh;
            }
        }
        gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, 0);
        return id[0];
    }

    /** Eine Mip-Stufe: Farbe mit der Deckung gewichtet, Deckung gemittelt. */
    public static float[] down(float[] p, int w, int h, int nw, int nh) {
        float[] o = new float[4 * nw * nh];
        for (int y = 0; y < nh; y++)
            for (int x = 0; x < nw; x++) {
                float a = 0, r = 0, g = 0, b = 0, ur = 0, ug = 0, ub = 0;
                int n = 0;
                for (int dy = 0; dy < 2; dy++)
                    for (int dx = 0; dx < 2; dx++) {
                        int xx = Math.min(w - 1, 2 * x + dx), yy = Math.min(h - 1, 2 * y + dy), i = 4 * (yy * w + xx);
                        float al = p[i + 3];
                        a += al; r += p[i] * al; g += p[i + 1] * al; b += p[i + 2] * al;
                        ur += p[i]; ug += p[i + 1]; ub += p[i + 2];
                        n++;
                    }
                int q = 4 * (y * nw + x);
                if (a > 1e-6f) { o[q] = r / a; o[q + 1] = g / a; o[q + 2] = b / a; } else { o[q] = ur / n; o[q + 1] = ug / n; o[q + 2] = ub / n; }
                o[q + 3] = a / n;
            }
        return o;
    }

    static int srgb(float v) {
        v = Math.max(0, Math.min(1, v));
        double c = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
        return (int) Math.round(c * 255);
    }

    public static void dispose(GL4 gl, int id) { if (id != 0) gl.glDeleteTextures(1, new int[]{id}, 0); }
}
