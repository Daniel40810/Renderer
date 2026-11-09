package com.dan.aurora.demo;

import com.dan.aurora.Aurora;
import com.dan.aurora.Ort;
import com.dan.aurora.gl.AuroraGl;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Demo von com.dan.aurora, Phase 2: das Polarlicht auf der Grafikkarte, in Bewegung. Das Polarlicht kommt aus
 * {@link AuroraGl} in ein HDR-Bild (cd/m²), die Szene drumherum (Nachthimmel, Sterne, Bergkette, Boden) und die Sicht
 * (Kamera oder Auge) rechnet {@code nachtsicht.frag}. Ziehen mit der Maus dreht den Blick. Unten Ort, Preset, Kp,
 * Qualität, Sicht und „Zeit läuft“ (Drift der Bögen, Falten und Strahlen).
 * <p>
 * Ohne Fenster: {@code --bild datei.png [preset|kp] [ort] [LOW|MEDIUM|HIGH|ULTRA] [seed]} schreibt ein Standbild aus
 * der Grafikkarte (1600 × 900).
 */
public final class AuroraGlDemo implements GLEventListener {
    private AuroraGlDemo() { }

    // ------------------------------------------------------------------ Szene
    final AuroraGl ag = new AuroraGl();
    volatile Aurora aurora = Aurora.aktiv();
    volatile boolean neu = true, laeuft = true;
    volatile double azimut = 0, hoehe = 22, fov = 70;
    volatile AuroraSzene.Sicht sicht = AuroraSzene.Sicht.KAMERA_8;
    volatile String status = "";
    private int prog, vao, hdrTex, hdrFbo, hdrW, hdrH;
    private long t0 = System.nanoTime(), letztes;
    private double zeit, ms;
    private int bilder;
    private long fpsT0;
    volatile double fps;

    @Override public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        ag.init(gl);
        prog = programm(gl, "/com/dan/aurora/gl/fullscreen.vert", "nachtsicht.frag");
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        letztes = System.nanoTime();
    }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        long now = System.nanoTime();
        double dt = (now - letztes) / 1e9;
        letztes = now;
        if (laeuft) zeit += dt;
        Aurora a = aurora;
        a.stufen = false;
        if (neu) { a.vorbereiten(zeit); neu = false; } else if (laeuft) a.zeit(zeit);
        ag.laden(gl, AuroraGl.daten(a));
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        hdr(gl, w, h);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glClearColor(0, 0, 0, 0);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        AuroraGl.View v = new AuroraGl.View();
        v.width = w;
        v.height = h;
        v.invViewProj = Matrizen.invViewProj(fov, w / (double) h, new double[]{0, 2, 0}, azimut, hoehe);
        long g0 = System.nanoTime();
        ag.zeichnen(gl, v);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        gl.glViewport(0, 0, w, h);
        gl.glUseProgram(prog);
        gl.glBindVertexArray(vao);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uAurora"), 0);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uInvVP"), 1, false, v.invViewProj, 0);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uZeit"), (float) Math.max(0.01, sicht.sekunden));
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uIso"), 3200);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uBlende"), 2.8f);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uAnpassung"), 0.3f);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uHimmel"), 2.0e-4f);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uAuge"), sicht == AuroraSzene.Sicht.AUGE ? 1 : 0);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        ms = ms * 0.9 + (System.nanoTime() - g0) / 1e6 * 0.1;
        bilder++;
        if (now - fpsT0 > 1e9) { fps = bilder / ((now - fpsT0) / 1e9); bilder = 0; fpsT0 = now; }
        status = String.format(Locale.GERMAN, "%.0f Bilder/s · Prozessor %.1f ms je Bild · t = %.0f s · %s · %s", fps, ms, zeit, a.helligkeit(), ag.status());
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }

    @Override public void dispose(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        ag.dispose(gl);
        if (prog != 0) gl.glDeleteProgram(prog);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (hdrFbo != 0) { gl.glDeleteFramebuffers(1, new int[]{hdrFbo}, 0); gl.glDeleteTextures(1, new int[]{hdrTex}, 0); }
    }

    private void hdr(GL4 gl, int w, int h) {
        if (hdrFbo != 0 && hdrW == w && hdrH == h) return;
        if (hdrFbo != 0) { gl.glDeleteFramebuffers(1, new int[]{hdrFbo}, 0); gl.glDeleteTextures(1, new int[]{hdrTex}, 0); }
        int[] t = new int[1], f = new int[1];
        gl.glGenTextures(1, t, 0);
        hdrTex = t[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glGenFramebuffers(1, f, 0);
        hdrFbo = f[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, hdrTex, 0);
        hdrW = w;
        hdrH = h;
    }

    /** Übersetzt Vertex- und Fragment-Shader (Ressourcen: absolut oder neben dieser Klasse). */
    static int programm(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int[] sh = {gl.glCreateShader(GL4.GL_VERTEX_SHADER), gl.glCreateShader(GL4.GL_FRAGMENT_SHADER)};
        String[] src = {quelle(vert), quelle(frag)};
        for (int i = 0; i < 2; i++) {
            gl.glShaderSource(sh[i], 1, new String[]{"#version 430 core\n" + src[i]}, null, 0);
            gl.glCompileShader(sh[i]);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh[i], GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                int[] len = new int[1];
                gl.glGetShaderiv(sh[i], GL4.GL_INFO_LOG_LENGTH, len, 0);
                byte[] b = new byte[Math.max(1, len[0])];
                gl.glGetShaderInfoLog(sh[i], b.length, len, 0, b, 0);
                throw new IllegalStateException("aurora demo: " + (i == 0 ? vert : frag) + ": " + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
            }
            gl.glAttachShader(p, sh[i]);
        }
        gl.glLinkProgram(p);
        for (int s : sh) { gl.glDetachShader(p, s); gl.glDeleteShader(s); }
        return p;
    }

    static String quelle(String datei) {
        try (InputStream in = AuroraGlDemo.class.getResourceAsStream(datei)) {
            if (in == null) throw new IllegalStateException("aurora demo: Shader fehlt: " + datei);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ Start

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        if (args.length > 1 && args[0].equals("--bild")) {
            Aurora a = args.length > 2 ? AuroraDemo.preset(args[2]) : Aurora.aktiv();
            if (args.length > 3) a.ort = AuroraDemo.ort(args[3]);
            if (args.length > 4) a.quality = Aurora.Quality.valueOf(args[4].toUpperCase(Locale.ROOT));
            if (args.length > 5) a.seed = Long.parseLong(args[5]);
            bild(new File(args[1]), a, 1600, 900);
            return;
        }
        SwingUtilities.invokeLater(AuroraGlDemo::oeffnen);
    }

    /** Standbild aus der Grafikkarte ohne Fenster. */
    static void bild(File datei, Aurora a, int w, int h) throws Exception {
        GLProfile p = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(p);
        caps.setOnscreen(false);
        caps.setFBO(true);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(p).createOffscreenAutoDrawable(null, caps, null, w, h);
        AuroraGlDemo s = new AuroraGlDemo();
        s.aurora = a;
        s.laeuft = false;
        d.addGLEventListener(s);
        a.vorbereiten(0);
        s.azimut = a.azimutHauptbogen();
        s.hoehe = Math.max(12, Math.min(45, a.hoehenwinkel(a.hauptbogen()) * 0.55 + 6));
        d.display();
        d.display();
        BufferedImage[] im = new BufferedImage[1];
        d.invoke(true, gd -> { im[0] = lesen(gd.getGL().getGL4(), gd.getContext().getDefaultDrawFramebuffer(), w, h); return true; });
        ImageIO.write(im[0], "png", datei);
        System.out.println(s.status);
        System.out.println("Bild: " + datei);
        d.destroy();
    }

    static BufferedImage lesen(GL4 gl, int fb, int w, int h) {
        ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, fb);
        gl.glReadBuffer(fb == 0 ? GL.GL_BACK : GL.GL_COLOR_ATTACHMENT0);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                im.setRGB(x, y, ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255));
            }
        return im;
    }

    // ------------------------------------------------------------------ Fenster

    static final Color BG = new Color(11, 17, 24), TEXT = new Color(230, 234, 238), LEISE = new Color(150, 160, 172);

    private static void oeffnen() {
        AuroraGlDemo s = new AuroraGlDemo();
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(s);
        canvas.setPreferredSize(new Dimension(1200, 600));
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                s.azimut += (e.getX() - lx) * 0.15;
                s.hoehe = Math.max(-5, Math.min(85, s.hoehe + (e.getY() - ly) * 0.15));
                lx = e.getX();
                ly = e.getY();
            }
        };
        canvas.addMouseListener(m);
        canvas.addMouseMotionListener(m);

        FFrame f = new FFrame("Aurora · Phase 2 · Polarlicht auf der Grafikkarte");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout(10, 10));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(canvas, BorderLayout.CENTER);

        FComboBox ort = new FComboBox(Ort.alle());
        FComboBox preset = new FComboBox(AuroraDemo.PRESETS);
        preset.setSelectedIndex(1);
        FSlider kp = new FSlider(0, 90, 40);
        FComboBox qual = new FComboBox(new Object[]{"Niedrig (5 km)", "Mittel (2,5 km)", "Hoch (1,25 km)", "Ultra (0,6 km)"});
        qual.setSelectedIndex(2);
        FComboBox sicht = new FComboBox(AuroraSzene.Sicht.values());
        sicht.setSelectedItem(s.sicht);
        FCheckBox laeuft = new FCheckBox("Zeit läuft");
        laeuft.setSelected(true);
        JLabel lKp = label("Kp 4,0"), status = label("");
        status.setForeground(LEISE);
        Runnable neu = () -> {
            int i = preset.getSelectedIndex();
            double k = kp.getValue() / 10.0;
            Aurora a = switch (i) { case 0 -> Aurora.ruhig(); case 1 -> Aurora.aktiv(); case 2 -> Aurora.ausbruch(); case 3 -> Aurora.rot(); default -> Aurora.kp(k); };
            if (i != 4) a.kp = k;
            a.ort = ((Ort) ort.getSelectedItem()).kopie();
            a.quality = Aurora.Quality.values()[qual.getSelectedIndex()];
            a.seed = s.aurora.seed;
            a.stufen = false;
            s.aurora = a;
            s.neu = true;
            a.vorbereiten(0);
            s.azimut = a.azimutHauptbogen();
            s.hoehe = Math.max(12, Math.min(45, a.hoehenwinkel(a.hauptbogen()) * 0.55 + 6));
        };
        ort.addActionListener(e -> neu.run());
        preset.addActionListener(e -> neu.run());
        qual.addActionListener(e -> neu.run());
        sicht.addActionListener(e -> s.sicht = (AuroraSzene.Sicht) sicht.getSelectedItem());
        laeuft.addActionListener(e -> s.laeuft = laeuft.isSelected());
        kp.addChangeListener(e -> {
            lKp.setText(String.format("Kp %.1f", kp.getValue() / 10.0));
            if (!kp.getValueIsAdjusting()) neu.run();
        });
        JPanel zeile1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4)), zeile2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        zeile1.setBackground(BG);
        zeile2.setBackground(BG);
        zeile1.add(label("Ort"));
        zeile1.add(ort);
        zeile1.add(label("Preset"));
        zeile1.add(preset);
        zeile1.add(lKp);
        zeile1.add(kp);
        zeile1.add(label("Qualität"));
        zeile1.add(qual);
        zeile1.add(sicht);
        zeile1.add(laeuft);
        zeile2.add(status);
        JPanel south = new JPanel(new GridLayout(2, 1, 0, 4));
        south.setBackground(BG);
        south.add(zeile1);
        south.add(zeile2);
        root.add(south, BorderLayout.SOUTH);
        // deckend: ein GLCanvas in einem durchsichtigen Fenster bleibt unter Windows leer (wie in den anderen Demos)
        f.setBackground(BG);
        f.setPreferredFrameSize(new Dimension(1400, 760));
        f.setVisible(true);
        neu.run();
        Animator an = new Animator(canvas);
        an.start();
        new Timer(500, e -> status.setText(s.status)).start();
    }

    static JLabel label(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(TEXT);
        return l;
    }
}
