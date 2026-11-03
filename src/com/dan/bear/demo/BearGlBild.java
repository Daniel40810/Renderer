package com.dan.bear.demo;

import com.dan.bear.Sleuth;
import com.dan.bear.gl.BearGl;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.Locale;

/**
 * Die Haltungen aus {@link BearDemo#pose} mit {@link BearGl} auf der Grafikkarte, je eine Kachel, auf einem ebenen Grund
 * mit Schatten, in der Abendsonne: {@code BearGlBild datei.png}. Für die Abnahme des Shaders auf dem Rechner mit
 * Grafikkarte; die Farben linear gerechnet, beim Schreiben mit Gamma 2,2.
 */
public final class BearGlBild implements GLEventListener {
    final BearGl bearGl = new BearGl();
    int groundProg, gvao, gvbo;
    volatile float[] inst = new float[Sleuth.INST], shade = new float[5];
    volatile double[] eye = {5, 1.6, -5}, at = {0, 0.6, -0.6};
    volatile double wet;
    BufferedImage grabbed;

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        File out = new File(a.length > 0 ? a[0] : "Bear_GL.png");
        int w = 480, h = 320, cols = 5;
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        BearGlBild sc = new BearGlBild();
        d.addGLEventListener(sc);
        String[] names = BearDemo.POSE_NAMES;
        BufferedImage sheet = new BufferedImage(cols * w, 40 + ((names.length + cols - 1) / cols) * h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(12, 18, 24));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        long t0 = System.nanoTime();
        for (int i = 0; i < names.length; i++) {
            float[][] q = BearDemo.pose(i, 0.3);
            double L = 2.3, y = -q[1][0] * L;
            float[] in = new float[Sleuth.INST];
            Sleuth.write(in, 0, 0, y, 0, L, 0, i == 6 ? 0.8 : 0, 0.35 + 0.08 * (i % 3), 0.3, q[0]);
            sc.inst = in;
            sc.shade = new float[]{0, 0, (float) (-0.4 * L), (float) L, 0};
            boolean side = i == 1 || i == 2 || i == 3 || i == 5 || i == 8;
            sc.eye = side ? new double[]{6.2, 1.0, -0.7} : new double[]{4.6, 2.0, -4.6};
            sc.at = side ? new double[]{0, 0.75, -0.7} : new double[]{0, 0.55, -0.6};
            d.display();
            g.drawImage(sc.grabbed, (i % cols) * w, 40 + (i / cols) * h, null);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            g.drawString(names[i], (i % cols) * w + 10, 40 + (i / cols) * h + h - 10);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString(String.format(Locale.GERMAN, "com.dan.bear · gl/BearGl · %s · %.0f ms für %d Kacheln", sc.bearGl.info(), (System.nanoTime() - t0) / 1e6, names.length), 12, 27);
        g.dispose();
        d.destroy();
        ImageIO.write(sheet, "png", out);
        System.out.println("geschrieben: " + out.getAbsolutePath());
        System.exit(0);
    }

    static final String GROUND_VS = "#version 430 core\nlayout(location=0) in vec3 aP; uniform mat4 uVP; out vec3 vP; void main(){ vP=aP; gl_Position=uVP*vec4(aP,1.0); }";
    static final String GROUND_FS = "#version 430 core\nin vec3 vP; out vec4 frag; uniform vec3 uSun, uSunC, uSky;\n"
            + "void main(){ float c = mod(floor(vP.x*1.0)+floor(vP.z*1.0), 2.0); vec3 a = mix(vec3(0.10,0.13,0.08), vec3(0.085,0.11,0.07), c);"
            + " frag = vec4(a * (uSunC * max(uSun.y,0.0) / 3.14159 + uSky * 0.6), 1.0); }";

    @Override public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        bearGl.init(gl);
        int vs = gl.glCreateShader(GL4.GL_VERTEX_SHADER), fs = gl.glCreateShader(GL4.GL_FRAGMENT_SHADER);
        gl.glShaderSource(vs, 1, new String[]{GROUND_VS}, null);
        gl.glCompileShader(vs);
        gl.glShaderSource(fs, 1, new String[]{GROUND_FS}, null);
        gl.glCompileShader(fs);
        groundProg = gl.glCreateProgram();
        gl.glAttachShader(groundProg, vs);
        gl.glAttachShader(groundProg, fs);
        gl.glLinkProgram(groundProg);
        float s = 60;
        float[] v = {-s, 0, -s, s, 0, -s, s, 0, s, -s, 0, -s, s, 0, s, -s, 0, s};
        int[] t = new int[1];
        gl.glGenVertexArrays(1, t, 0);
        gvao = t[0];
        gl.glGenBuffers(1, t, 0);
        gvbo = t[0];
        gl.glBindVertexArray(gvao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, gvbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 12, 0);
        gl.glBindVertexArray(0);
    }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        double alt = Math.toRadians(24), az = Math.toRadians(250);
        double[] sun = {Math.cos(alt) * Math.sin(az), Math.sin(alt), -Math.cos(alt) * Math.cos(az)};
        double[] sunC = {3.0, 2.55, 2.0}, top = {0.18, 0.28, 0.48}, hor = {0.50, 0.56, 0.62};
        float[] vp = viewProj(eye, at, 32, w / (double) h);
        gl.glViewport(0, 0, w, h);
        gl.glClearColor((float) hor[0], (float) hor[1], (float) hor[2], 1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glUseProgram(groundProg);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(groundProg, "uVP"), 1, false, vp, 0);
        gl.glUniform3f(gl.glGetUniformLocation(groundProg, "uSun"), (float) sun[0], (float) sun[1], (float) sun[2]);
        gl.glUniform3f(gl.glGetUniformLocation(groundProg, "uSunC"), (float) sunC[0], (float) sunC[1], (float) sunC[2]);
        gl.glUniform3f(gl.glGetUniformLocation(groundProg, "uSky"), (float) top[0], (float) top[1], (float) top[2]);
        gl.glBindVertexArray(gvao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 6);
        bearGl.update(gl, inst, 1, shade, 1);
        BearGl.View v = new BearGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        BearGl.Light l = new BearGl.Light();
        l.sunDir = sun;
        l.sunColor = sunC;
        l.skyTop = top;
        l.skyHorizon = hor;
        l.wind = new double[]{4, 1};
        l.time = 3;
        bearGl.draw(gl, v, l);
        // zurücklesen, mit Gamma
        FloatBuffer fb = FloatBuffer.allocate(w * h * 4);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_FLOAT, fb);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int o = ((h - 1 - y) * w + x) * 4;
                int r = gam(fb.get(o)), gg = gam(fb.get(o + 1)), bb = gam(fb.get(o + 2));
                im.setRGB(x, y, (r << 16) | (gg << 8) | bb);
            }
        grabbed = im;
    }

    static int gam(float v) { return (int) Math.round(255 * Math.pow(Math.max(0, Math.min(1, v)), 1 / 2.2)); }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }

    @Override public void dispose(GLAutoDrawable d) { bearGl.dispose(d.getGL().getGL4()); }

    /** Projektion · Blick (Spalten zuerst, wie OpenGL). */
    static float[] viewProj(double[] e, double[] a, double fovy, double aspect) {
        double[] f = norm(new double[]{a[0] - e[0], a[1] - e[1], a[2] - e[2]});
        double[] s = norm(cross(f, new double[]{0, 1, 0})), u = cross(s, f);
        double[] view = {s[0], u[0], -f[0], 0, s[1], u[1], -f[1], 0, s[2], u[2], -f[2], 0,
                -dot(s, e), -dot(u, e), dot(f, e), 1};
        double n = 0.1, fa = 500, t = 1 / Math.tan(Math.toRadians(fovy) / 2);
        double[] proj = {t / aspect, 0, 0, 0, 0, t, 0, 0, 0, 0, (fa + n) / (n - fa), -1, 0, 0, 2 * fa * n / (n - fa), 0};
        float[] r = new float[16];
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                double v = 0;
                for (int k = 0; k < 4; k++) v += proj[k * 4 + rr] * view[c * 4 + k];
                r[c * 4 + rr] = (float) v;
            }
        return r;
    }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }

    static double[] norm(double[] a) { double l = Math.sqrt(dot(a, a)); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
}
