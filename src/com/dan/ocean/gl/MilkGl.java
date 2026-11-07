package com.dan.ocean.gl;

import com.dan.ocean.GlacialMilk;
import com.jogamp.opengl.GL4;

/** Uniforms von {@code milk.glsl} (Gletschermilch) für ein gebundenes Programm. */
public final class MilkGl {
    private MilkGl() { }

    public static void bind(GL4 gl, int prog, GlacialMilk m) {
        int n = Math.min(GlacialMilk.MAX_SOURCES, m.sources.size());
        float[] v = new float[4 * GlacialMilk.MAX_SOURCES];
        for (int i = 0; i < n; i++) {
            GlacialMilk.Source s = m.sources.get(i);
            v[4 * i] = (float) s.x(); v[4 * i + 1] = (float) s.z(); v[4 * i + 2] = (float) s.reach(); v[4 * i + 3] = (float) s.strength();
        }
        gl.glUniform4fv(gl.glGetUniformLocation(prog, "uMilkSrc[0]"), GlacialMilk.MAX_SOURCES, v, 0);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uMilkN"), n);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uMilkBase"), (float) m.base);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uMilkK"), (float) m.k);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uMilkColor"), (float) m.color[0], (float) m.color[1], (float) m.color[2]);
    }
}
