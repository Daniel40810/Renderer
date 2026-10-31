package com.dan.rime.gl;

import com.dan.rime.Rime;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Darstellung des Eises (Paketvertrag Punkt 3): der Baustein {@code rime.glsl}, den fremde Shader einbinden (Fichten,
 * Masten, Geländer, Schilder, Fels, Gleis), und das Setzen seiner Uniforms aus einem {@link Rime.Ansatz}: Bedeckung,
 * Federn, Farbe und seit Brocken Phase 10 der Versatz der Ecken gegen den Wind ({@code rimeOffset}, die Fahne als Form).
 */
public final class RimeGl {
    /** Name des Bausteins im Klassenpfad, für {@code #include}. */
    public static final String GLSL = "/com/dan/rime/gl/rime.glsl";

    private RimeGl() { }

    /** Setzt uRimeDicke, uRimeArt und uRimeVon im laufenden Programm. */
    public static void bind(GL4 gl, int program, Rime.Ansatz a, double windFromDeg) {
        double r = Math.toRadians(windFromDeg);
        gl.glUniform1f(gl.glGetUniformLocation(program, "uRimeDicke"), a == null ? 0f : (float) a.dicke);
        gl.glUniform1i(gl.glGetUniformLocation(program, "uRimeArt"), a == null ? 0 : a.art.ordinal());
        gl.glUniform3f(gl.glGetUniformLocation(program, "uRimeVon"), (float) Math.sin(r), 0f, (float) -Math.cos(r));
    }

    /** Liest den Baustein (für Prüfungen und für Shader ohne Klassenpfad-Einbindung). */
    public static String source() {
        try (InputStream in = RimeGl.class.getResourceAsStream("rime.glsl")) {
            if (in == null) throw new IllegalStateException("rime: Shader-Datei fehlt: rime.glsl");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("rime: rime.glsl: " + e.getMessage(), e);
        }
    }
}
