package com.dan.aurora.demo;

import com.dan.aurora.Aurora;
import com.dan.aurora.Emission;
import com.dan.aurora.Spektrum;
import com.dan.aurora.gl.AuroraGl;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.nio.FloatBuffer;
import java.util.Locale;

/**
 * Prüfung von com.dan.aurora.gl auf der Grafikkarte (OpenGL 4.3, ohne Fenster). Endet mit „Alles in Ordnung.“ oder mit
 * der Zahl der Verstöße (Rückgabewert 1):
 * <ul>
 * <li>Grafikkarte = Prozessor: das Rundumbild (256 × 64) gegen {@link Aurora#strahlung} an denselben Richtungen, Summe
 * der Leuchtdichte auf 3 %, mittlerer Fehler je Richtung unter 10 % des Mittels.</li>
 * <li>Ein Blick (1280 × 720) kommt an, additiv, ohne NaN; Zeit je Bild in MEDIUM und HIGH.</li>
 * </ul>
 */
public final class AuroraGlCheck implements GLEventListener {
    private static int bad;
    final AuroraGl ag = new AuroraGl();
    java.util.function.Consumer<GL4> auftrag;

    @Override public void init(GLAutoDrawable d) { ag.init(d.getGL().getGL4()); }
    @Override public void display(GLAutoDrawable d) { if (auftrag != null) { auftrag.accept(d.getGL().getGL4()); auftrag = null; } }
    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }
    @Override public void dispose(GLAutoDrawable d) { ag.dispose(d.getGL().getGL4()); }

    public static void main(String[] args) {
        Locale.setDefault(Locale.GERMAN);
        AuroraGlCheck c = new AuroraGlCheck();
        GLProfile p = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(p);
        caps.setOnscreen(false);
        caps.setFBO(true);
        GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(p).createOffscreenAutoDrawable(null, caps, null, 64, 64);
        dr.addGLEventListener(c);
        dr.display();
        check(c.ag.bereit() || c.ag.status().contains("übersetzt"), c.ag.status());

        Aurora a = Aurora.aktiv();
        a.seed = 3;
        a.quality = Aurora.Quality.HIGH;
        a.vorbereiten(0);
        AuroraGl.Daten daten = AuroraGl.daten(a);
        check(daten.n == a.vorhaenge.size() && daten.tabellen.length == 4 * daten.n, "Daten: " + daten.n + " Vorhänge, " + daten.tabellen.length + " Tabellen");

        // 1 Rundumbild gegen den Prozessor
        int pw = 256, ph = 64;
        float[][] bild = new float[1][];
        int[] glFehler = new int[1];
        c.auftrag = gl -> {
            ag(c).laden(gl, daten);
            int[] fbo = fbo(gl, pw, ph);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo[0]);
            gl.glViewport(0, 0, pw, ph);
            gl.glClearColor(0, 0, 0, 0);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            c.ag.panorama(gl, 1, 1);
            bild[0] = lesen(gl, pw, ph);
            glFehler[0] = gl.glGetError();
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
            gl.glDeleteFramebuffers(1, fbo, 0);
            gl.glDeleteTextures(1, fbo, 1);
        };
        dr.display();
        check(bild[0] != null, "Rundumbild gezeichnet: " + c.ag.status());
        check(glFehler[0] == GL.GL_NO_ERROR, "keine GL-Fehler beim Laden und Zeichnen (0x" + Integer.toHexString(glFehler[0]) + ")");
        if (bild[0] != null) {
            double[] kr = new double[Emission.LINIEN], rgb = new double[3];
            double sumCpu = 0, sumGpu = 0, fehler = 0;
            int n = 0;
            boolean endlich = true;
            for (int iy = ph / 2; iy < ph; iy++)
                for (int ix = 0; ix < pw; ix++) {
                    double az = ((ix + 0.5) / pw - 0.5) * 2 * Math.PI, el = ((iy + 0.5) / ph - 0.5) * Math.PI;
                    double dx = Math.sin(az) * Math.cos(el), dy = Math.sin(el), dz = -Math.cos(az) * Math.cos(el);
                    a.strahlung(dx, -dz, dy, kr);
                    Spektrum.rgb(kr, rgb);
                    double yc = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
                    int o = (iy * pw + ix) * 4;
                    double yg = 0.2126 * bild[0][o] + 0.7152 * bild[0][o + 1] + 0.0722 * bild[0][o + 2];
                    if (Double.isNaN(yg) || Double.isInfinite(yg) || yg < 0) endlich = false;
                    sumCpu += yc;
                    sumGpu += yg;
                    fehler += Math.abs(yg - yc);
                    n++;
                }
            double mittel = sumCpu / n;
            check(endlich, "Rundumbild ohne NaN und nicht negativ");
            check(sumCpu > 0 && Math.abs(sumGpu / sumCpu - 1) < 0.03, String.format("Summe der Leuchtdichte Grafikkarte/Prozessor = %.4f (±3 %%)", sumGpu / Math.max(1e-30, sumCpu)));
            check(fehler / n < 0.10 * mittel, String.format("mittlerer Fehler je Richtung %.1f %% des Mittels (< 10 %%), Mittel %.3e cd/m²", 100 * fehler / n / Math.max(1e-30, mittel), mittel));
        }

        // 2 ein Blick, Zeit je Bild
        for (Aurora.Quality q : new Aurora.Quality[]{Aurora.Quality.MEDIUM, Aurora.Quality.HIGH}) {
            a.quality = q;
            AuroraGl.Daten dq = AuroraGl.daten(a);
            int w = 1280, h = 720;
            double[] ms = new double[1];
            float[][] mitte = new float[1][];
            c.auftrag = gl -> {
                c.ag.laden(gl, dq);
                int[] fbo = fbo(gl, w, h);
                gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo[0]);
                gl.glViewport(0, 0, w, h);
                gl.glClearColor(0, 0, 0, 0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                AuroraGl.View v = new AuroraGl.View();
                v.width = w;
                v.height = h;
                v.invViewProj = Matrizen.invViewProj(60, w / (double) h, new double[]{0, 2, 0}, a.azimutHauptbogen(), 25);
                c.ag.zeichnen(gl, v);
                gl.glFinish();
                long t0 = System.nanoTime();
                int k = 5;
                for (int i = 0; i < k; i++) c.ag.zeichnen(gl, v);
                gl.glFinish();
                ms[0] = (System.nanoTime() - t0) / 1e6 / k;
                mitte[0] = lesen(gl, w, h);
                gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
                gl.glDeleteFramebuffers(1, fbo, 0);
                gl.glDeleteTextures(1, fbo, 1);
            };
            dr.display();
            double sum = 0;
            boolean endlich = true;
            if (mitte[0] != null) for (int i = 0; i < w * h; i++) { float g = mitte[0][4 * i + 1]; if (Float.isNaN(g) || g < 0) endlich = false; sum += g; }
            check(mitte[0] != null && endlich && sum > 0, String.format("Blick %d × %d in %s: Grün gesamt %.3e, %.1f ms je Bild (6 Bilder additiv)", w, h, q, sum, ms[0]));
        }
        dr.destroy();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static AuroraGl ag(AuroraGlCheck c) { return c.ag; }

    /** FBO mit RGBA32F-Textur: {fbo, textur}. */
    static int[] fbo(GL4 gl, int w, int h) {
        int[] t = new int[1], f = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA32F, w, h);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glGenFramebuffers(1, f, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, f[0]);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, t[0], 0);
        return new int[]{f[0], t[0]};
    }

    static float[] lesen(GL4 gl, int w, int h) {
        FloatBuffer b = Buffers.newDirectFloatBuffer(w * h * 4);
        gl.glReadBuffer(GL.GL_COLOR_ATTACHMENT0);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_FLOAT, b);
        float[] f = new float[w * h * 4];
        b.get(f);
        return f;
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }
}
