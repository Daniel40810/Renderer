package com.dan.aurora.gl;

import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Lädt und übersetzt die Shader dieses Packages (Ressourcen neben der Klasse). Vor den Text kommen
 * {@code #version 430 core} und die Defines. Fehler kommen mit Dateiname zurück.
 */
final class Glsl {
    private Glsl() { }

    static String read(String file) {
        try (InputStream in = Glsl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("aurora: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("aurora: " + file + ": " + e.getMessage(), e);
        }
    }

    static String source(String file, String... defines) {
        StringBuilder sb = new StringBuilder("#version 430 core\n");
        for (String d : defines) sb.append("#define ").append(d).append('\n');
        sb.append(read(file));
        return sb.toString();
    }

    /** Übersetzt die Stufen (Endung bestimmt die Art) und bindet ein Programm. */
    static int program(GL4 gl, String name, String[] defines, String... files) {
        int prog = gl.glCreateProgram();
        int[] sh = new int[files.length];
        for (int i = 0; i < files.length; i++) {
            String f = files[i];
            int type = f.endsWith(".vert") ? GL4.GL_VERTEX_SHADER : f.endsWith(".frag") ? GL4.GL_FRAGMENT_SHADER : GL4.GL_COMPUTE_SHADER;
            sh[i] = gl.glCreateShader(type);
            gl.glShaderSource(sh[i], 1, new String[]{source(f, defines)}, null, 0);
            gl.glCompileShader(sh[i]);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh[i], GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                String log = log(gl, sh[i], true);
                for (int s : sh) if (s != 0) gl.glDeleteShader(s);
                gl.glDeleteProgram(prog);
                throw new IllegalStateException("aurora: " + f + " lässt sich nicht übersetzen:\n" + log);
            }
            gl.glAttachShader(prog, sh[i]);
        }
        gl.glLinkProgram(prog);
        int[] ok = new int[1];
        gl.glGetProgramiv(prog, GL4.GL_LINK_STATUS, ok, 0);
        for (int s : sh) { gl.glDetachShader(prog, s); gl.glDeleteShader(s); }
        if (ok[0] == 0) {
            String log = log(gl, prog, false);
            gl.glDeleteProgram(prog);
            throw new IllegalStateException("aurora: Programm " + name + " lässt sich nicht binden:\n" + log);
        }
        return prog;
    }

    static String log(GL4 gl, int obj, boolean shader) {
        int[] len = new int[1];
        if (shader) gl.glGetShaderiv(obj, GL4.GL_INFO_LOG_LENGTH, len, 0);
        else gl.glGetProgramiv(obj, GL4.GL_INFO_LOG_LENGTH, len, 0);
        if (len[0] <= 1) return "";
        byte[] b = new byte[len[0]];
        if (shader) gl.glGetShaderInfoLog(obj, len[0], len, 0, b, 0);
        else gl.glGetProgramInfoLog(obj, len[0], len, 0, b, 0);
        return new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8);
    }
}
