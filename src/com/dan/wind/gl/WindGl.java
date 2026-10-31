package com.dan.wind.gl;

import com.dan.wind.WindField;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Das Windfeld auf der Grafikkarte (Paketvertrag Punkt 3): eine 3D-Textur RGBA16F mit Wind und Böe je Zelle des
 * Gitters und der Baustein {@code wind.glsl}, den fremde Shader einbinden. Hochgeladen wird nur bei Wechsel der Lage
 * ({@link #upload}); je Bild setzt {@link #bind} die Uniforms. Ziel im Werkbuch: unter 0,3 ms je Bild [G].
 */
public final class WindGl {
    /** Name des Bausteins im Klassenpfad, für {@code #include}. */
    public static final String GLSL = "/com/dan/wind/gl/wind.glsl";
    private int tex;
    private WindField.Grid grid;

    /** Lädt das Feld (aus {@link WindField#sample}) als 3D-Textur hoch; legt sie beim ersten Mal an. */
    public void upload(GL4 gl, WindField.Grid g, float[] data) {
        if (data.length != g.cells() * 4) throw new IllegalArgumentException("wind: " + data.length + " Werte, erwartet " + g.cells() * 4);
        if (tex == 0) {
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            tex = t[0];
        }
        gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, tex);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_WRAP_R, GL.GL_CLAMP_TO_EDGE);
        gl.glTexImage3D(GL2ES2.GL_TEXTURE_3D, 0, GL.GL_RGBA16F, g.nx(), g.nz(), g.layers(), 0, GL.GL_RGBA, GL.GL_FLOAT, FloatBuffer.wrap(data));
        gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, 0);
        grid = g;
    }

    /** Setzt die Uniforms von {@code wind.glsl} im laufenden Programm; ohne Feld uWindOn = 0. */
    public void bind(GL4 gl, int program, int unit) {
        boolean on = tex != 0 && grid != null;
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, on ? tex : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(gl.glGetUniformLocation(program, "uWindTex"), unit);
        gl.glUniform1i(gl.glGetUniformLocation(program, "uWindOn"), on ? 1 : 0);
        if (!on) return;
        gl.glUniform4f(gl.glGetUniformLocation(program, "uWindBox"), (float) grid.x0(), (float) grid.z0(), (float) (grid.nx() * grid.cell()), (float) (grid.nz() * grid.cell()));
        gl.glUniform4f(gl.glGetUniformLocation(program, "uWindLay"), (float) grid.first(), (float) grid.stretch(), grid.layers(), (float) grid.top());
    }

    /** Größe der Textur auf der Grafikkarte (Byte). */
    public long bytes() { return grid == null ? 0 : (long) grid.cells() * 8; }

    public void dispose(GL4 gl) {
        if (tex != 0) gl.glDeleteTextures(1, new int[]{tex}, 0);
        tex = 0;
        grid = null;
    }

    /** Liest den Baustein (für Prüfungen und für Shader ohne Klassenpfad-Einbindung). */
    public static String source() {
        try (InputStream in = WindGl.class.getResourceAsStream("wind.glsl")) {
            if (in == null) throw new IllegalStateException("wind: Shader-Datei fehlt: wind.glsl");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("wind: wind.glsl: " + e.getMessage(), e);
        }
    }
}
