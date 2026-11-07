package com.dan.lens.demo;

import com.dan.fframe.FFrame;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fslider.FSlider;
import com.dan.lens.Lens;
import com.dan.lens.gl.LensGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.util.FPSAnimator;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Demo von com.dan.lens: ein Kai am Abend mit Lichterkette, einem Kasten im Fokus, einem Pfahl ganz vorn und der Sonne,
 * durch das Objektiv. Fenster mit Reglern (Brennweite, Blende, Fokus, Lamellen, Rundung, Schalter), oder ohne Fenster
 * {@code --bild datei.png}: vier Einstellungen als Kontaktbogen (Lochkamera; 85 mm f/2 auf 4 m: Bokeh mit Katzenauge;
 * 50 mm f/11 mit sieben geraden Lamellen: Stern und Geisterbilder; 50 mm f/2 auf 8 m: der Pfahl weich von beiden Seiten).
 */
public final class LensDemo {
    static final double NEAR = 0.3;

    /** Eine Einstellung der Demo. */
    record Shot(String title, double focal, double fNumber, double focusM, int blades, double round, boolean lens) { }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        String bild = null;
        for (int i = 0; i < a.length - 1; i++) if (a[i].equals("--bild")) bild = a[i + 1];
        if (bild != null) { sheet(new File(bild)); return; }
        SwingUtilities.invokeLater(LensDemo::window);
    }

    // ------------------------------------------------------------------ Szene und Objektiv auf der Grafikkarte

    static final class Stage implements GLEventListener {
        final Lens lens = new Lens();
        final LensGl lensGl = new LensGl(lens);
        volatile boolean useLens = true;
        volatile double exposure = 1.0, sunE = 2.5;
        int sceneProg, postProg, vao, fbo, outFbo, color, depth, out, w, h;
        BufferedImage grabbed;
        volatile boolean grab;
        long last = System.nanoTime();

        @Override public void init(GLAutoDrawable d) {
            GL4 gl = d.getGL().getGL4();
            sceneProg = program(gl, "scene.frag");
            postProg = program(gl, "post.frag");
            int[] t = new int[1];
            gl.glGenVertexArrays(1, t, 0);
            vao = t[0];
            gl.glGenFramebuffers(1, t, 0);
            fbo = t[0];
            gl.glGenFramebuffers(1, t, 0);
            outFbo = t[0];
            lens.quality = Lens.Quality.HIGH;
            lensGl.init(gl);
        }

        void size(GL4 gl, int nw, int nh) {
            if (nw == w && nh == h) return;
            if (color != 0) gl.glDeleteTextures(3, new int[]{color, depth, out}, 0);
            w = nw;
            h = nh;
            color = tex(gl, GL.GL_RGBA16F, w, h);
            depth = tex(gl, GL4.GL_DEPTH_COMPONENT32F, w, h);
            out = tex(gl, GL.GL_RGBA8, w, h);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, color, 0);
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, depth, 0);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, outFbo);
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, out, 0);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        }

        @Override public void display(GLAutoDrawable d) {
            GL4 gl = d.getGL().getGL4();
            size(gl, d.getSurfaceWidth(), d.getSurfaceHeight());
            long now = System.nanoTime();
            double dt = Math.min(0.1, (now - last) / 1e9);
            last = now;
            double sx = w * 0.766, sy = h * 0.764;
            // Szene
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
            gl.glViewport(0, 0, w, h);
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(GL.GL_ALWAYS);
            gl.glUseProgram(sceneProg);
            u2(gl, sceneProg, "uSize", w, h);
            u1(gl, sceneProg, "uFocal", (float) lens.focalMm);
            u2(gl, sceneProg, "uSunPx", (float) sx, (float) sy);
            u1(gl, sceneProg, "uSunE", (float) sunE);
            u1(gl, sceneProg, "uNearS", (float) NEAR);
            gl.glBindVertexArray(vao);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            gl.glDisable(GL.GL_DEPTH_TEST);
            int img = color;
            if (useLens) {
                LensGl.View v = new LensGl.View();
                v.colorTexture = color;
                v.depthTexture = depth;
                v.width = w;
                v.height = h;
                v.reversedZ = true;
                v.near = NEAR;
                v.far = 1e6;
                // Sonne als Richtung: über eine Projektion, deren Bild die Lage oben trifft
                double f = 1 / Math.tan(Math.atan(12 / lens.focalMm));
                float asp = w / (float) h;
                v.viewProj = new float[]{(float) (f / asp), 0, 0, 0, 0, (float) f, 0, 0, 0, 0, 0, -1, 0, 0, (float) NEAR, 0};
                double nx = sx / w * 2 - 1, ny = sy / h * 2 - 1;
                double[] dir = {nx * asp / f, ny / f, -1};
                double l = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + 1);
                v.sunDir = new double[]{dir[0] / l, dir[1] / l, dir[2] / l};
                v.sunIrradiance = new double[]{sunE, sunE * 0.93, sunE * 0.85};
                v.exposure = exposure;
                v.dt = dt;
                img = lensGl.apply(gl, v);
            }
            // Belichtung ins Bild
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, grab ? outFbo : d.getContext().getDefaultDrawFramebuffer());
            gl.glViewport(0, 0, w, h);
            gl.glUseProgram(postProg);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            gl.glBindTexture(GL.GL_TEXTURE_2D, img);
            u1i(gl, postProg, "uHdr", 0);
            u1(gl, postProg, "uExposure", (float) exposure);
            gl.glBindVertexArray(vao);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            if (grab) {
                ByteBuffer b = ByteBuffer.allocateDirect(w * h * 4);
                gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, b);
                BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        int i = ((h - 1 - y) * w + x) * 4;
                        im.setRGB(x, y, (b.get(i) & 255) << 16 | (b.get(i + 1) & 255) << 8 | (b.get(i + 2) & 255));
                    }
                grabbed = im;
                grab = false;
            }
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        }

        @Override public void reshape(GLAutoDrawable d, int x, int y, int ww, int hh) { }

        @Override public void dispose(GLAutoDrawable d) { lensGl.dispose(d.getGL().getGL4()); }
    }

    // ------------------------------------------------------------------ ohne Fenster: Kontaktbogen

    static void sheet(File file) throws Exception {
        Shot[] shots = {
                new Shot("Lochkamera (alles scharf)", 50, 2, 8, 8, 0.35, false),
                new Shot("85 mm f/2, Fokus 4 m: Bokeh, Katzenauge, helle Ränder", 85, 2, 4, 9, 0.6, true),
                new Shot("50 mm f/11, 7 gerade Lamellen: Stern und Geisterbilder", 50, 11, 1e4, 7, 0, true),
                new Shot("50 mm f/2, Fokus 8 m: der Pfahl vorn weich", 50, 2, 8, 8, 0.35, true)};
        int w = 1280, h = 720;
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        Stage st = new Stage();
        d.addGLEventListener(st);
        d.display();
        BufferedImage out = new BufferedImage(2 * w / 2, 2 * h / 2 + 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(12, 18, 24));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        for (int k = 0; k < shots.length; k++) {
            Shot s = shots[k];
            st.useLens = s.lens();
            st.lens.focalMm = s.focal();
            st.lens.fNumber = s.fNumber();
            st.lens.autoFocus = false;
            st.lens.focusM = s.focusM();
            st.lens.aperture.blades = s.blades();
            st.lens.aperture.roundness = s.round();
            for (int i = 0; i < 4; i++) d.display();   // Fokus nachführen und lesen
            st.grab = true;
            d.display();
            int cx = (k % 2) * w / 2, cy = (k / 2) * (h / 2 + 30);
            g.drawImage(st.grabbed, cx, cy + 30, w / 2, h / 2, null);
            g.setColor(new Color(230, 236, 240));
            g.drawString(s.title() + (s.lens() ? " · " + st.lens.describe(s.focusM()) : ""), cx + 8, cy + 20);
        }
        g.dispose();
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        ImageIO.write(out, "png", file);
        Lens.Optics o = st.lensGl.optics();
        System.out.println("Bild: " + file.getPath() + (o != null ? String.format(Locale.GERMAN, " · %d Geisterbilder, %d Strahlen", o.ghostCount(), o.spikeAngles().length) : ""));
        d.destroy();
        System.exit(0);
    }

    // ------------------------------------------------------------------ Fenster

    static void window() {
        Stage st = new Stage();
        GLJPanel panel = new GLJPanel(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        panel.addGLEventListener(st);
        panel.setPreferredSize(new Dimension(1280, 720));
        FFrame f = new FFrame("com.dan.lens · Objektiv");
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(new Color(18, 20, 30));
        side.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JLabel info = new JLabel(" ");
        info.setForeground(new Color(200, 210, 220));
        Runnable upd = () -> info.setText("<html><body style='width:200px'>" + st.lens.describe(Double.isNaN(st.lensGl.focusM()) ? st.lens.focusM : st.lensGl.focusM()) + "</body></html>");
        slider(side, "Brennweite (mm)", 18, 200, 50, v -> st.lens.focalMm = v, upd);
        slider(side, "Blende ×10 (f/2 … f/22)", 20, 220, 28, v -> st.lens.fNumber = v / 10.0, upd);
        slider(side, "Fokus (dm)", 5, 400, 80, v -> { st.lens.autoFocus = false; st.lens.focusM = v / 10.0; }, upd);
        slider(side, "Lamellen", 0, 16, 8, v -> st.lens.aperture.blades = v, upd);
        slider(side, "Rundung (%)", 0, 100, 35, v -> st.lens.aperture.roundness = v / 100.0, upd);
        check(side, "Objektiv", true, v -> st.useLens = v);
        check(side, "Autofokus (Mitte)", true, v -> st.lens.autoFocus = v);
        check(side, "Geisterbilder", true, v -> st.lens.ghosts = v);
        check(side, "Stern", true, v -> st.lens.star = v);
        check(side, "Verzeichnung, Farbsäume", true, v -> { st.lens.distortion = v; st.lens.chromatic = v; });
        check(side, "Randabfall", true, v -> st.lens.vignetting = v);
        side.add(info);
        st.lens.autoFocus = true;
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.add(panel, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        f.setSize(1600, 800);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new FPSAnimator(panel, 30).start();
        new javax.swing.Timer(500, e -> upd.run()).start();
    }

    interface IntSet { void set(int v); }

    interface BoolSet { void set(boolean v); }

    static void slider(JPanel p, String name, int min, int max, int val, IntSet s, Runnable upd) {
        JLabel l = new JLabel(name);
        l.setForeground(new Color(200, 210, 220));
        FSlider sl = new FSlider(min, max, val);
        sl.addChangeListener(e -> { s.set(sl.getValue()); upd.run(); });
        s.set(val);
        p.add(l);
        p.add(sl);
    }

    static void check(JPanel p, String name, boolean val, BoolSet s) {
        FCheckBox c = new FCheckBox(name);
        c.setSelected(val);
        c.addActionListener(e -> s.set(c.isSelected()));
        s.set(val);
        p.add(c);
    }

    // ------------------------------------------------------------------ GL-Hilfen

    static int tex(GL4 gl, int fmt, int w, int h) {
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, fmt, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    static void u1(GL4 gl, int p, String n, float v) { gl.glUniform1f(gl.glGetUniformLocation(p, n), v); }

    static void u1i(GL4 gl, int p, String n, int v) { gl.glUniform1i(gl.glGetUniformLocation(p, n), v); }

    static void u2(GL4 gl, int p, String n, float a, float b) { gl.glUniform2f(gl.glGetUniformLocation(p, n), a, b); }

    static int program(GL4 gl, String frag) {
        String vs = "#version 430 core\n" + vertex(), fs = "#version 430 core\n" + res(frag);
        int p = gl.glCreateProgram();
        int[] ok = new int[1];
        for (int k = 0; k < 2; k++) {
            int s = gl.glCreateShader(k == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(s, 1, new String[]{k == 0 ? vs : fs}, null, 0);
            gl.glCompileShader(s);
            gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] log = new byte[4096];
                gl.glGetShaderInfoLog(s, log.length, null, 0, log, 0);
                throw new IllegalStateException(frag + ": " + new String(log, StandardCharsets.UTF_8).trim());
            }
            gl.glAttachShader(p, s);
        }
        gl.glLinkProgram(p);
        return p;
    }

    static String vertex() {
        try (InputStream in = LensGl.class.getResourceAsStream("fullscreen.vert")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException | NullPointerException e) {
            throw new IllegalStateException("fullscreen.vert fehlt (src auf den Klassenpfad)", e);
        }
    }

    static String res(String n) {
        try (InputStream in = LensDemo.class.getResourceAsStream(n)) {
            if (in == null) throw new IllegalStateException(n + " fehlt (src auf den Klassenpfad)");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private LensDemo() { }
}
