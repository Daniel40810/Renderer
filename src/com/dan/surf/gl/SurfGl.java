package com.dan.surf.gl;

import com.dan.surf.Surf;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Darstellung der Brandung mit JOGL (Paketvertrag Punkt 3). <b>Hülle aus Sylt Phase 1:</b> ein Vollbild-Dreieck zeigt
 * einen Querschnitt durch ebenen Hang und Welle (Shader {@code surf.vert}, {@code surf.frag} als Ressourcen hier im
 * Package). Die echten Brecher als Netz mit Lippe und Hohlraum kommen in Sylt Phase 6 (F9 A).
 */
public final class SurfGl {
    private int program, vao;

    public void init(GL4 gl) {
        program = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, "surf.vert"), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, "surf.frag");
        gl.glAttachShader(program, vs);
        gl.glAttachShader(program, fs);
        gl.glLinkProgram(program);
        int[] ok = new int[1];
        gl.glGetProgramiv(program, GL4.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        if (ok[0] == 0) throw new IllegalStateException("surf: Programm lässt sich nicht binden");
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
    }

    /** Zeichnet den Querschnitt in den gebundenen Bildspeicher; Hang 1:{@code 1/slope}, Zeit t in s. */
    public void draw(GL4 gl, Surf s, double slope, float t) {
        gl.glUseProgram(program);
        gl.glUniform1f(gl.glGetUniformLocation(program, "uHs"), (float) s.hs());
        gl.glUniform1f(gl.glGetUniformLocation(program, "uTp"), (float) s.tp());
        gl.glUniform1f(gl.glGetUniformLocation(program, "uSlope"), (float) slope);
        gl.glUniform1f(gl.glGetUniformLocation(program, "uGamma"), (float) s.gamma);
        gl.glUniform1f(gl.glGetUniformLocation(program, "uTime"), t);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
    }

    public void dispose(GL4 gl) {
        if (program != 0) gl.glDeleteProgram(program);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        program = vao = 0;
    }

    /** Liest eine Shader-Datei aus diesem Package (Ressource neben der Klasse). */
    public static String source(String file) {
        try (InputStream in = SurfGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("surf: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("surf: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int compile(GL4 gl, int type, String file) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("surf: " + file + " lässt sich nicht übersetzen");
        return sh;
    }
}
