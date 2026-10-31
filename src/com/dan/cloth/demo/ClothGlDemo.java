package com.dan.cloth.demo;

import com.dan.cloth.Cloth;
import com.dan.cloth.gl.ClothGl;
import com.dan.cloth.gl.ClothGpu;
import com.dan.fcheckbox.FCheckBox;
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

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

/**
 * Grafikteil von {@code com.dan.cloth} (seit Timmendorf Phase 6): eine Reihe Strandflaggen an einer Promenade im
 * böigen Wind, dazu Wimpel und eine Markise. Gerechnet wird mit {@link ClothGpu} auf der Grafikkarte (umschaltbar auf
 * den Prozessor), gezeichnet mit {@link ClothGl}. Nur die Demo nutzt FStyle.
 *
 * <pre>
 * Fenster:  ClothGlDemo [flaggen] [wind m/s]
 * Bild:     ClothGlDemo --bild datei.png [flaggen] [wind m/s] [breite] [höhe]
 * </pre>
 */
public final class ClothGlDemo implements GLEventListener {
    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), MUTED = new Color(120, 136, 150);

    final List<Cloth> cloths = new ArrayList<>();
    final List<double[]> poles = new ArrayList<>();
    final ClothGl gl = new ClothGl();
    final ClothGpu gpu = new ClothGpu();
    final ClothGl.Light light = new ClothGl.Light();
    volatile double windSpeed, windFrom = 250, gustiness = 0.5, wetness, sunAz = 200;
    volatile boolean onGpu = true, running = true;
    volatile double yaw = -0.55, pitch = 0.12, dist = 26;
    double time, frameMs, stepMs;
    long last;
    int width = 1, height = 1;
    boolean gpuOk;
    String check = "";
    volatile String error;
    JLabel info;
    int solidProg, solidVao, solidCount, solidBuf;

    ClothGlDemo(int flags, double wind) {
        windSpeed = wind;
        for (int k = 0; k < flags; k++) {
            double z = (k - (flags - 1) / 2.0) * 4.0;
            Cloth c = Cloth.beachFlag(0, 0, z);
            c.seed = 11 + k * 7;
            cloths.add(c);
            poles.add(new double[]{0, z, 6.25});
        }
        // zwei Wimpel und eine Markise am Ende der Reihe
        double ze = (flags + 1) / 2.0 * 4.0;
        Cloth p1 = Cloth.pennant(0, 4.2, ze, 1.2, 0.3), p2 = Cloth.pennant(0, 4.2, -ze, 1.2, 0.3);
        p1.colors = new float[]{0.86f, 0.86f, 0.84f};
        p2.colors = new float[]{0.05f, 0.18f, 0.55f};
        p1.seed = 5;
        p2.seed = 9;
        cloths.add(p1);
        cloths.add(p2);
        poles.add(new double[]{0, ze, 4.35});
        poles.add(new double[]{0, -ze, 4.35});
        Cloth a = Cloth.awning(3, 1.6, ze + 1.5, 1.4, 0.7);
        cloths.add(a);
        for (Cloth c : cloths) {
            c.ground = (x, zz) -> 0;
            c.wind = (x, y, zz, t, o) -> wind(x, y, zz, t, o);
        }
    }

    /** Wind der Demo: Richtung und Stärke vom Regler, Böen als zwei langsame Wellen längs der Reihe [G]. */
    void wind(double x, double y, double z, double t, double[] o) {
        double g = 1 + gustiness * 0.35 * (Math.sin(0.9 * t + 0.13 * z) + 0.5 * Math.sin(2.3 * t + 0.41 * z + 1.1));
        double to = Math.toRadians(windFrom + 180), u = windSpeed * Math.max(0, g) * Math.log(Math.max(0.5, y) / 0.01) / Math.log(10 / 0.01);
        o[0] = Math.sin(to) * u;
        o[1] = 0;
        o[2] = -Math.cos(to) * u;
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--bild")) {
            image(args);
            return;
        }
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 8;
        double w = args.length > 1 ? Double.parseDouble(args[1]) : 9;
        ClothGlDemo d = new ClothGlDemo(n, w);
        SwingUtilities.invokeLater(d::open);
    }

    static void image(String[] a) throws Exception {
        String file = a.length > 1 ? a[1] : "flaggen.png";
        int n = a.length > 2 ? Integer.parseInt(a[2]) : 8;
        double wv = a.length > 3 ? Double.parseDouble(a[3]) : 9;
        int w = a.length > 4 ? Integer.parseInt(a[4]) : 1280, h = a.length > 5 ? Integer.parseInt(a[5]) : 800;
        ClothGlDemo d = new ClothGlDemo(n, wv);
        d.running = false;
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLOffscreenAutoDrawable drawable = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps(prof), null, w, h);
        drawable.addGLEventListener(d);
        drawable.display();
        drawable.invoke(true, g -> {
            GL4 g4 = g.getGL().getGL4();
            for (int i = 0; i < 240; i++) d.simulate(g4, 1 / 60.0);
            return true;
        });
        d.live = true;
        drawable.display();
        ImageIO.write(d.shown, "png", new File(file));
        System.out.println(file + " geschrieben: " + d.statusLine());
        drawable.destroy();
        System.exit(d.error == null ? 0 : 1);
    }

    // ------------------------------------------------------------------ Fenster

    JPanel view;
    volatile BufferedImage shown;
    private final BufferedImage[] images = new BufferedImage[2];
    private int imageIndex;
    private IntBuffer pixels;
    boolean live;

    void open() {
        FFrame f = new FFrame("Strandflaggen – Grafikteil von com.dan.cloth (XPBD, JOGL)");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        view = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                BufferedImage img = shown;
                if (img != null) g.drawImage(img, 0, 0, getWidth(), getHeight(), null);
            }
        };
        view.setBackground(BG);
        view.setPreferredSize(new Dimension(1100, 760));
        MouseAdapter mouse = new MouseAdapter() {
            Point lastP;
            @Override public void mousePressed(MouseEvent e) { lastP = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) {
                yaw += (e.getX() - lastP.x) * 0.008;
                pitch = Math.max(-0.1, Math.min(1.4, pitch + (e.getY() - lastP.y) * 0.006));
                lastP = e.getPoint();
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) { dist = Math.max(3, Math.min(150, dist * Math.pow(1.12, e.getWheelRotation()))); }
        };
        view.addMouseListener(mouse);
        view.addMouseMotionListener(mouse);
        view.addMouseWheelListener(mouse);
        root.add(view, BorderLayout.CENTER);
        root.add(controls(), BorderLayout.EAST);
        info = new JLabel(" ");
        info.setForeground(MUTED);
        info.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        root.add(info, BorderLayout.SOUTH);
        f.setPreferredFrameSize(new Dimension(1420, 860));
        f.setSize(1420, 860);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        live = true;
        Thread t = new Thread(this::renderLoop, "Tuch-GL");
        t.setDaemon(true);
        t.start();
    }

    JComponent controls() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(BG);
        side.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        side.setPreferredSize(new Dimension(280, 10));
        JLabel head = new JLabel("Strandflaggen im Wind");
        head.setForeground(INK);
        head.setFont(new Font("SansSerif", Font.BOLD, 14));
        side.add(head);
        slider(side, "Wind", 0, 30, (int) windSpeed, v -> { windSpeed = v; return v + " m/s (Bft " + bft(v) + ")"; });
        slider(side, "Böigkeit", 0, 100, (int) (gustiness * 100), v -> { gustiness = v / 100.0; return v + " %"; });
        slider(side, "aus Richtung", 0, 359, (int) windFrom, v -> { windFrom = v; return v + "°"; });
        slider(side, "Nässe", 0, 100, 0, v -> { wetness = v / 100.0; return v + " %"; });
        slider(side, "Sonne (Azimut)", 0, 359, (int) sunAz, v -> { sunAz = v; return v + "° (Gegenlicht: Tuch scheint durch)"; });
        FCheckBox g = new FCheckBox("Rechnen auf der Grafikkarte");
        g.setSelected(true);
        g.addActionListener(e -> onGpu = g.isSelected());
        FCheckBox r = new FCheckBox("Zeit läuft");
        r.setSelected(true);
        r.addActionListener(e -> running = r.isSelected());
        for (JComponent c : new JComponent[]{g, r}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            side.add(Box.createVerticalStrut(10));
            side.add(c);
        }
        JLabel note = new JLabel("<html>Maus ziehen: drehen · Rad: Abstand<br>Flaggen: Schleswig-Holstein, 1,5 × 1 m auf 6 m</html>");
        note.setForeground(MUTED);
        note.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        side.add(note);
        side.add(Box.createVerticalGlue());
        return side;
    }

    static int bft(double v) {
        double[] lim = {0.3, 1.6, 3.4, 5.5, 8.0, 10.8, 13.9, 17.2, 20.8, 24.5, 28.5, 32.7};
        int b = 0;
        while (b < lim.length && v >= lim[b]) b++;
        return b;
    }

    void slider(JPanel side, String name, int min, int max, int value, IntFunction<String> c) {
        JLabel l = new JLabel(name + ": " + c.apply(value));
        l.setForeground(MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(10, 0, 3, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        FSlider s = new FSlider(min, max, value);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.addChangeListener(e -> l.setText(name + ": " + c.apply(s.getValue())));
        side.add(l);
        side.add(s);
    }

    void renderLoop() {
        GLOffscreenAutoDrawable drawable;
        try {
            GLProfile prof = GLProfile.get(GLProfile.GL4);
            drawable = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps(prof), null, Math.max(64, view.getWidth()), Math.max(64, view.getHeight()));
            drawable.addGLEventListener(this);
        } catch (Throwable e) {
            fail(e);
            return;
        }
        while (true) {
            long t0 = System.nanoTime();
            int w = Math.max(64, view.getWidth()), h = Math.max(64, view.getHeight());
            try {
                if (w != drawable.getSurfaceWidth() || h != drawable.getSurfaceHeight()) drawable.setSurfaceSize(w, h);
                drawable.display();
            } catch (Throwable e) {
                fail(e);
            }
            SwingUtilities.invokeLater(view::repaint);
            long sleep = 16 - (System.nanoTime() - t0) / 1_000_000;
            try {
                Thread.sleep(Math.max(1, sleep));
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    static GLCapabilities caps(GLProfile prof) {
        GLCapabilities c = new GLCapabilities(prof);
        c.setOnscreen(false);
        c.setFBO(true);
        c.setDepthBits(24);
        c.setSampleBuffers(true);
        c.setNumSamples(4);
        return c;
    }

    void grabFrame(GL4 g, int w, int h) {
        imageIndex ^= 1;
        BufferedImage img = images[imageIndex];
        if (img == null || img.getWidth() != w || img.getHeight() != h) img = images[imageIndex] = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        if (pixels == null || pixels.capacity() < w * h) pixels = Buffers.newDirectIntBuffer(w * h);
        pixels.clear();
        g.glPixelStorei(GL.GL_PACK_ALIGNMENT, 4);
        g.glReadPixels(0, 0, w, h, GL.GL_BGRA, GL4.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        int[] dst = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < h; y++) {
            pixels.position((h - 1 - y) * w);
            pixels.get(dst, y * w, w);
        }
        shown = img;
    }

    void fail(Throwable e) {
        if (error == null) {
            error = e.toString();
            e.printStackTrace();
        }
        if (info != null) SwingUtilities.invokeLater(() -> { info.setForeground(new Color(240, 120, 110)); info.setText("Fehler: " + error); });
    }

    // ------------------------------------------------------------------ GL

    static final String SOLID_VS = """
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNrm;
            layout(location = 2) in vec3 aCol;
            uniform mat4 uVP;
            out vec3 vW; out vec3 vN; out vec3 vC;
            void main() { vW = aPos; vN = aNrm; vC = aCol; gl_Position = uVP * vec4(aPos, 1.0); }
            """;
    static final String SOLID_FS = """
            in vec3 vW; in vec3 vN; in vec3 vC;
            uniform vec3 uCam, uSunDir, uSunColor, uSkyColor;
            uniform vec4 uFog;
            out vec4 fragColor;
            void main() {
                vec3 n = normalize(vN);
                float v = 0.93 + 0.07 * sin(vW.x * 1.7 + sin(vW.z * 0.9) * 2.0);
                vec3 c = vC * v * (uSunColor * max(dot(n, uSunDir), 0.0) + uSkyColor * (0.5 + 0.5 * n.y));
                c = mix(c, uFog.rgb, 1.0 - exp(-length(uCam - vW) / uFog.w));
                c = c / (1.0 + c * 0.25);
                fragColor = vec4(pow(max(c, vec3(0.0)), vec3(1.0 / 2.2)), 1.0);
            }
            """;

    @Override
    public void init(GLAutoDrawable d) {
        try {
            GL4 g = d.getGL().getGL4();
            gl.init(g);
            gl.setCloths(g, cloths);
            try {
                gpu.init(g);
                check = gpu.selfCheck(g);
                gpu.setCloths(g, cloths);
                gpuOk = true;
            } catch (RuntimeException e) {
                check = "!Grafikkarte: " + e.getMessage();
                gpuOk = false;
                System.err.println("cloth: " + e.getMessage());
            }
            System.out.println("cloth: " + check);
            solidProg = program(g, SOLID_VS, SOLID_FS);
            buildSolids(g);
            last = System.nanoTime();
        } catch (Throwable e) {
            fail(e);
        }
    }

    /** Sand, Promenade und Stangen als einfache Körper (nur die Demo). */
    void buildSolids(GL4 g) {
        List<Float> v = new ArrayList<>();
        float[] sand = {0.62f, 0.54f, 0.38f}, stone = {0.35f, 0.34f, 0.33f}, pole = {0.75f, 0.75f, 0.74f};
        quad(v, -400, -400, 400, 400, -0.01f, sand);
        quad(v, -2.5f, -60, 2.5f, 60, 0.0f, stone);
        for (double[] p : poles) {
            int seg = 10;
            double r = 0.045;
            for (int s = 0; s < seg; s++) {
                double a0 = 2 * Math.PI * s / seg, a1 = 2 * Math.PI * (s + 1) / seg;
                float[] p0 = {(float) (p[0] + r * Math.cos(a0)), 0, (float) (p[1] + r * Math.sin(a0))};
                float[] p1 = {(float) (p[0] + r * Math.cos(a1)), 0, (float) (p[1] + r * Math.sin(a1))};
                float top = (float) p[2];
                float[] n0 = {(float) Math.cos(a0), 0, (float) Math.sin(a0)}, n1 = {(float) Math.cos(a1), 0, (float) Math.sin(a1)};
                vert(v, p0[0], 0, p0[2], n0, pole); vert(v, p1[0], 0, p1[2], n1, pole); vert(v, p1[0], top, p1[2], n1, pole);
                vert(v, p0[0], 0, p0[2], n0, pole); vert(v, p1[0], top, p1[2], n1, pole); vert(v, p0[0], top, p0[2], n0, pole);
            }
        }
        float[] a = new float[v.size()];
        for (int i = 0; i < a.length; i++) a[i] = v.get(i);
        solidCount = a.length / 9;
        int[] ids = new int[1];
        g.glGenVertexArrays(1, ids, 0);
        solidVao = ids[0];
        g.glBindVertexArray(solidVao);
        g.glGenBuffers(1, ids, 0);
        solidBuf = ids[0];
        g.glBindBuffer(GL.GL_ARRAY_BUFFER, solidBuf);
        g.glBufferData(GL.GL_ARRAY_BUFFER, a.length * 4L, Buffers.newDirectFloatBuffer(a), GL.GL_STATIC_DRAW);
        for (int i = 0; i < 3; i++) {
            g.glEnableVertexAttribArray(i);
            g.glVertexAttribPointer(i, 3, GL.GL_FLOAT, false, 36, i * 12L);
        }
        g.glBindVertexArray(0);
    }

    static void quad(List<Float> v, float x0, float z0, float x1, float z1, float y, float[] c) {
        float[] n = {0, 1, 0};
        vert(v, x0, y, z0, n, c); vert(v, x0, y, z1, n, c); vert(v, x1, y, z1, n, c);
        vert(v, x0, y, z0, n, c); vert(v, x1, y, z1, n, c); vert(v, x1, y, z0, n, c);
    }

    static void vert(List<Float> v, float x, float y, float z, float[] n, float[] c) {
        v.add(x); v.add(y); v.add(z); v.add(n[0]); v.add(n[1]); v.add(n[2]); v.add(c[0]); v.add(c[1]); v.add(c[2]);
    }

    /** Rechnet die Tücher dt weiter (Grafikkarte oder Prozessor) und misst die Zeit. */
    void simulate(GL4 g, double dt) {
        long t0 = System.nanoTime();
        for (Cloth c : cloths) c.wetness = wetness;
        if (onGpu && gpuOk) gpu.step(g, dt);
        else {
            for (Cloth c : cloths) c.step(dt);
            gl.upload(g);
        }
        stepMs = stepMs * 0.9 + (System.nanoTime() - t0) / 1e6 * 0.1;
        time += dt;
    }

    private boolean lastOnGpu = true;

    @Override
    public void display(GLAutoDrawable d) {
        if (error != null && solidProg == 0) return;
        try {
            width = d.getSurfaceWidth();
            height = d.getSurfaceHeight();
            GL4 g = d.getGL().getGL4();
            long now = System.nanoTime();
            double dt = Math.min(0.05, (now - last) / 1e9);
            last = now;
            frameMs = frameMs * 0.9 + dt * 1000 * 0.1;
            // Wechsel Grafikkarte ↔ Prozessor: den Stand mitnehmen
            if (gpuOk && onGpu != lastOnGpu) {
                if (onGpu) gpu.setCloths(g, cloths); else gpu.readBack(g);
                lastOnGpu = onGpu;
            }
            if (running) simulate(g, dt);
            frame(g);
            if (live) grabFrame(g, width, height);
        } catch (Throwable e) {
            fail(e);
        }
    }

    void frame(GL4 g) {
        double[] tgt = {0, 3.5, 0};
        double[] eye = {tgt[0] + Math.cos(yaw) * Math.cos(pitch) * dist, tgt[1] + Math.sin(pitch) * dist, tgt[2] + Math.sin(yaw) * Math.cos(pitch) * dist};
        float[] vp = mul(persp(45, width / (double) Math.max(1, height), 0.1, 2000), look(eye, tgt));
        double az = Math.toRadians(sunAz), el = Math.toRadians(28);
        light.sunDir = new double[]{Math.sin(az) * Math.cos(el), Math.sin(el), -Math.cos(az) * Math.cos(el)};
        light.fogDistance = 900;
        g.glViewport(0, 0, width, height);
        float[] sky = {tone(0.55), tone(0.66), tone(0.80)};
        g.glClearColor(sky[0], sky[1], sky[2], 1);
        g.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        g.glEnable(GL.GL_DEPTH_TEST);
        g.glUseProgram(solidProg);
        g.glUniformMatrix4fv(g.glGetUniformLocation(solidProg, "uVP"), 1, false, vp, 0);
        g.glUniform3f(g.glGetUniformLocation(solidProg, "uCam"), (float) eye[0], (float) eye[1], (float) eye[2]);
        double[] sd = light.sunDir;
        g.glUniform3f(g.glGetUniformLocation(solidProg, "uSunDir"), (float) sd[0], (float) sd[1], (float) sd[2]);
        g.glUniform3f(g.glGetUniformLocation(solidProg, "uSunColor"), (float) light.sunColor[0], (float) light.sunColor[1], (float) light.sunColor[2]);
        g.glUniform3f(g.glGetUniformLocation(solidProg, "uSkyColor"), (float) light.skyColor[0], (float) light.skyColor[1], (float) light.skyColor[2]);
        g.glUniform4f(g.glGetUniformLocation(solidProg, "uFog"), (float) light.fogColor[0], (float) light.fogColor[1], (float) light.fogColor[2], (float) light.fogDistance);
        g.glBindVertexArray(solidVao);
        g.glDrawArrays(GL.GL_TRIANGLES, 0, solidCount);
        g.glBindVertexArray(0);
        ClothGl.View v = new ClothGl.View();
        v.viewProj = vp;
        v.eye = eye;
        if (onGpu && gpuOk) gl.draw(g, v, light, gpu); else gl.draw(g, v, light);
        int err = g.glGetError();
        if (err != GL.GL_NO_ERROR && error == null) fail(new IllegalStateException(String.format("OpenGL-Fehler 0x%04X", err)));
        if (info != null && error == null) {
            String s = statusLine();
            SwingUtilities.invokeLater(() -> info.setText(s));
        }
    }

    String statusLine() {
        int pts = 0;
        for (Cloth c : cloths) pts += c.nu * c.nv;
        Cloth f = cloths.get(0);
        return String.format(Locale.GERMAN, "%d Tücher, %,d Punkte, %,d Dreiecke · %s, %d Unterschritte je 1/60 s, %.2f ms je Bild (Rechnen) · %.1f ms je Bild · Flattern %.1f Hz · %s",
                cloths.size(), pts, gl.triangles(), onGpu && gpuOk ? "Grafikkarte" : "Prozessor", f.substeps(), stepMs, frameMs,
                f.flutterFrequency(windSpeed), check);
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { width = w; height = h; }

    @Override
    public void dispose(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.dispose(g);
        gpu.dispose(g);
        g.glDeleteProgram(solidProg);
    }

    static float tone(double c) {
        c = c / (1 + c * 0.25);
        return (float) Math.pow(Math.max(0, c), 1 / 2.2);
    }

    static int program(GL4 g, String vs, String fs) {
        int p = g.glCreateProgram();
        for (int k = 0; k < 2; k++) {
            int s = g.glCreateShader(k == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            g.glShaderSource(s, 1, new String[]{"#version 430 core\n" + (k == 0 ? vs : fs)}, null);
            g.glCompileShader(s);
            int[] ok = new int[1];
            g.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] b = new byte[4096];
                g.glGetShaderInfoLog(s, b.length, null, 0, b, 0);
                throw new IllegalStateException(new String(b).trim());
            }
            g.glAttachShader(p, s);
            g.glDeleteShader(s);
        }
        g.glLinkProgram(p);
        return p;
    }

    static float[] persp(double fovDeg, double aspect, double near, double far) {
        float f = (float) (1 / Math.tan(Math.toRadians(fovDeg) / 2));
        return new float[]{f / (float) aspect, 0, 0, 0, 0, f, 0, 0, 0, 0, (float) ((far + near) / (near - far)), -1,
                0, 0, (float) (2 * far * near / (near - far)), 0};
    }

    static float[] look(double[] e, double[] t) {
        double fx = t[0] - e[0], fy = t[1] - e[1], fz = t[2] - e[2];
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double sx = -fz, sy = 0, sz = fx;
        double sl = Math.sqrt(sx * sx + sz * sz);
        sx /= sl; sz /= sl;
        double ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
        return new float[]{(float) sx, (float) ux, (float) -fx, 0, (float) sy, (float) uy, (float) -fy, 0,
                (float) sz, (float) uz, (float) -fz, 0,
                (float) -(sx * e[0] + sy * e[1] + sz * e[2]), (float) -(ux * e[0] + uy * e[1] + uz * e[2]),
                (float) (fx * e[0] + fy * e[1] + fz * e[2]), 1};
    }

    static float[] mul(float[] a, float[] b) {
        float[] o = new float[16];
        for (int c = 0; c < 4; c++)
            for (int r = 0; r < 4; r++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k];
                o[c * 4 + r] = s;
            }
        return o;
    }
}
