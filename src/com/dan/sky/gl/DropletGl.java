package com.dan.sky.gl;

import com.dan.sky.Droplets;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Die Phasenfunktion der Tröpfchen ({@link Droplets}) als Textur für einen Nebel oder eine Wolke (seit Brocken
 * Phase 7): {@link Droplets#N} × 1 Texel RGBA16F, u = Streuwinkel / 180°, linear gefiltert, normiert auf 4π (isotrop =
 * 1). Ein Shader liest sie mit {@code texture(uPhase, vec2(acos(dot(blick, sonne)) / PI, 0.5)).rgb} statt
 * Henyey-Greenstein; Glorie, Nebelbogen und Aureole kommen damit von selbst.
 * <p>
 * {@link #update} baut die Tabelle bei neuem Halbmesser oder neuer Breite in einem eigenen Faden (rund 0,5 s) und lädt
 * sie hoch, sobald sie fertig ist; bis dahin gilt die alte. {@link #texture()} ist 0, solange noch keine da ist.
 */
public final class DropletGl {
    private int tex;
    private Droplets.Table shown;
    private Future<Droplets.Table> pending;
    private String pendingKey = "", shownKey = "";
    private final ExecutorService builder = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Tröpfchen");
        t.setDaemon(true);
        return t;
    });

    /** Textur (0, solange keine Tabelle fertig ist). */
    public int texture() { return tex; }

    /** Die hochgeladene Tabelle (null, solange keine da ist). */
    public Droplets.Table table() { return shown; }

    /** Tabelle bei Bedarf bauen (im Hintergrund) und eine fertige hochladen; im GL-Faden je Bild aufrufen. */
    public void update(GL4 gl, Droplets d) {
        double r = d.radiusUm, w = d.width;
        String key = String.format(java.util.Locale.ROOT, "%.2f|%.3f", r, w);
        if (pending != null && pending.isDone()) {
            try {
                upload(gl, pending.get());
                shownKey = pendingKey;
            } catch (Exception e) {
                System.err.println("sky: Tröpfchen: " + e);
            }
            pending = null;
        }
        if (pending == null && !key.equals(shownKey)) {
            pendingKey = key;
            pending = builder.submit(() -> Droplets.table(r, w));
        }
    }

    /** Sofort bauen und hochladen (Standbilder, Prüfungen). */
    public void updateNow(GL4 gl, Droplets d) {
        Droplets.Table t = d.table();
        upload(gl, t);
        shownKey = pendingKey = String.format(java.util.Locale.ROOT, "%.2f|%.3f", d.radiusUm, d.width);
    }

    private void upload(GL4 gl, Droplets.Table t) {
        if (tex == 0) {
            int[] v = new int[1];
            gl.glGenTextures(1, v, 0);
            tex = v[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, Droplets.N, 1);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        }
        float[] px = new float[Droplets.N * 4];
        for (int i = 0; i < Droplets.N; i++) {
            for (int c = 0; c < 3; c++) px[i * 4 + c] = Math.min(60_000f, t.rgb()[i * 3 + c]);
            px[i * 4 + 3] = 1;
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, Droplets.N, 1, GL4.GL_RGBA, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(px));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        shown = t;
    }

    public void dispose(GL4 gl) {
        if (tex != 0) gl.glDeleteTextures(1, new int[]{tex}, 0);
        tex = 0;
        shown = null;
        shownKey = pendingKey = "";
        builder.shutdownNow();
    }
}
