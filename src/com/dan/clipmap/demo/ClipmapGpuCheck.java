package com.dan.clipmap.demo;

import com.dan.clipmap.Clipmap;
import com.dan.clipmap.gl.ClipmapGl;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.nio.FloatBuffer;
import java.util.Locale;

/**
 * Der Baustein {@code clipmap.glsl} auf der Grafikkarte gegen Java: jeder Scheitel jeder Stufe wird als Punkt in ein
 * Bild aus Gleitkommazahlen geschrieben (Ort, Höhe mit Übergang, Anteil des Übergangs) und mit
 * {@link Clipmap#vertexHeight} verglichen. So prüft {@code ClipmapCheck --gpu}, dass Shader und Java dasselbe rechnen
 * und die Naht auch auf der Karte dicht ist (Befund Phase 4: {@code %} mit negativen Zahlen ist in GLSL undefiniert).
 */
final class ClipmapGpuCheck {
    private ClipmapGpuCheck() { }

    static final String VS = """
            out vec4 vOut;
            out float vA;
            void main() {
                ClipVertex c = clipmapVertex();
                vOut = vec4(c.world, c.height);
                vA = c.morph;
                gl_Position = vec4((aClipGrid + 0.5) / float(uClipM) * 2.0 - 1.0, 0.0, 1.0);
                gl_PointSize = 1.0;
            }
            """;
    static final String FS = """
            #version 430 core
            in vec4 vOut;
            in float vA;
            layout(location = 0) out vec4 o0;
            layout(location = 1) out vec4 o1;
            void main() { o0 = vOut; o1 = vec4(vA); }
            """;

    /** Prüft alle Stufen von c (schon geladen); gibt {größte Abweichung der Höhe, größte des Übergangs, Zahl der Scheitel} zurück. */
    static double[] run(Clipmap c) {
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        int m = c.grid;
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, m, m);
        double[] res = new double[3];
        d.display();
        d.invoke(true, dr -> {
            GL4 gl = dr.getGL().getGL4();
            String lib = ClipmapGl.glsl();
            int prog = ClipmapDemo.program(gl, "#version 430 core\n#define CLIPMAP_VERTEX\n" + lib + VS, FS);
            gl.glBindAttribLocation(prog, ClipmapGl.ATTRIB, "aClipGrid");
            gl.glLinkProgram(prog);
            ClipmapGl cgl = new ClipmapGl();
            cgl.upload(gl, c);
            int[] t = new int[4];
            gl.glGenVertexArrays(1, t, 0);
            int vao = t[0];
            gl.glGenBuffers(1, t, 1);
            gl.glBindVertexArray(vao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, t[1]);
            FloatBuffer v = Buffers.newDirectFloatBuffer(m * m * 2);
            for (int l = 0; l < m; l++) for (int k = 0; k < m; k++) v.put(k).put(l);
            v.flip();
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) m * m * 8, v, GL.GL_STATIC_DRAW);
            gl.glEnableVertexAttribArray(ClipmapGl.ATTRIB);
            gl.glVertexAttribPointer(ClipmapGl.ATTRIB, 2, GL.GL_FLOAT, false, 8, 0);
            gl.glGenTextures(2, t, 2);
            for (int i = 2; i < 4; i++) {
                gl.glBindTexture(GL.GL_TEXTURE_2D, t[i]);
                gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA32F, m, m);
            }
            int[] fb = new int[1];
            gl.glGenFramebuffers(1, fb, 0);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fb[0]);
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, t[2], 0);
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL4.GL_COLOR_ATTACHMENT1, GL.GL_TEXTURE_2D, t[3], 0);
            gl.glDrawBuffers(2, new int[]{GL.GL_COLOR_ATTACHMENT0, GL4.GL_COLOR_ATTACHMENT1}, 0);
            gl.glViewport(0, 0, m, m);
            gl.glDisable(GL.GL_DEPTH_TEST);
            gl.glUseProgram(prog);
            Clipmap.Window[] win = c.windows();
            cgl.bindCommon(gl, prog, c, new double[]{0, 0, 0}, 0);
            FloatBuffer a = Buffers.newDirectFloatBuffer(m * m * 4), b = Buffers.newDirectFloatBuffer(m * m * 4);
            double worst = 0, worstA = 0;
            long n = 0;
            for (int L = 0; L < win.length; L++) {
                cgl.bindLevel(gl, prog, c, win, L);
                gl.glClearColor(0, 0, 0, 0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                gl.glDrawArrays(GL.GL_POINTS, 0, m * m);
                gl.glReadBuffer(GL.GL_COLOR_ATTACHMENT0);
                a.clear();
                gl.glReadPixels(0, 0, m, m, GL4.GL_RGBA, GL.GL_FLOAT, a);
                gl.glReadBuffer(GL4.GL_COLOR_ATTACHMENT1);
                b.clear();
                gl.glReadPixels(0, 0, m, m, GL4.GL_RGBA, GL.GL_FLOAT, b);
                boolean coarse = L + 1 < win.length;
                for (int l = 0; l < m; l++)
                    for (int k = 0; k < m; k++) {
                        int p = (l * m + k) * 4;
                        worst = Math.max(worst, Math.abs(a.get(p + 3) - c.vertexHeight(L, k, l)));
                        worstA = Math.max(worstA, Math.abs(b.get(p) - (coarse ? c.morph(k, l) : 0)));
                        n++;
                    }
            }
            res[0] = worst;
            res[1] = worstA;
            res[2] = n;
            cgl.dispose(gl);
            return true;
        });
        d.destroy();
        return res;
    }

    static String line(double[] r) {
        return String.format(Locale.GERMAN, "Baustein auf der Grafikkarte gleich Java: %.0f Scheitel, Höhe auf %.1e m, Übergang auf %.1e", r[2], r[0], r[1]);
    }
}
