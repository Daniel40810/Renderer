package com.dan.beach.demo;

import com.dan.beach.Beach;
import com.dan.beach.gl.BeachGl;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
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

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Vorführung des Strands ohne Szene (Paketvertrag Punkt 3 und 10): ein ebenes Stück Strand, nass am Wasser, zertreten in
 * einem Streifen landwärts.
 * <ul>
 * <li>{@code BeachDemo}: Fenster; ziehen dreht den Blick, Rad nähert; Tasten 1 Abendsonne, 2 Mittag, 3 Spülsaum,
 * 4 wie das Asset (Düne), 0 Relief aus; Regler für Sonne, Relief und Kachel.</li>
 * <li>{@code BeachDemo --bild datei.png [breite]}: vier Blicke als ein Blatt.</li>
 * </ul>
 */
public final class BeachDemo implements GLEventListener {
    final BeachGl gl = new BeachGl();
    volatile Beach beach = Beach.ostsee();
    volatile boolean on = true;
    double tx, ty, tz, yaw, pitch, dist, fovy = 50;
    volatile double sunAlt = 10, sunAz = 250;
    int w = 1, h = 1;
    static final double SHORE = 20, TRAMPLE_FROM = 6, TRAMPLE_TO = 40;

    void scene(int k) {
        beach = k == 4 ? Beach.duene() : Beach.ostsee();
        beach.quality = Beach.Quality.ULTRA;
        on = k != 0;
        switch (k) {
            case 2 -> { sunAlt = 55; sunAz = 190; tx = 0; ty = 0; tz = 4; yaw = -70; pitch = 28; dist = 6; }
            case 3 -> { sunAlt = 18; sunAz = 300; tx = 0; ty = 0; tz = -SHORE + 2; yaw = -80; pitch = 14; dist = 10; }
            case 4 -> { sunAlt = 14; sunAz = 240; tx = 0; ty = 0; tz = 10; yaw = -60; pitch = 18; dist = 14; }
            default -> { sunAlt = 9; sunAz = 255; tx = 0; ty = 0; tz = 4; yaw = -75; pitch = 20; dist = 7; }
        }
    }

    double[] eye() {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        return new double[]{tx + dist * Math.cos(pr) * Math.cos(yr), Math.max(0.2, ty + dist * Math.sin(pr)), tz - dist * Math.cos(pr) * Math.sin(yr)};
    }

    BeachGl.Light light() {
        BeachGl.Light l = new BeachGl.Light();
        double a = Math.toRadians(sunAz), e = Math.toRadians(sunAlt);
        l.sunDir = new double[]{Math.sin(a) * Math.cos(e), Math.sin(e), -Math.cos(a) * Math.cos(e)};
        double k = Math.max(0, Math.min(1, sunAlt / 30));
        l.sunColor = new double[]{3.1 * (0.6 + 0.4 * k), 2.9 * (0.42 + 0.58 * k), 2.6 * (0.25 + 0.75 * k)};
        l.skyTop = new double[]{0.30 * (0.5 + 0.5 * k), 0.42 * (0.5 + 0.5 * k), 0.64 * (0.55 + 0.45 * k)};
        l.skyHorizon = new double[]{0.68, 0.72 * (0.7 + 0.3 * k), 0.80 * (0.6 + 0.4 * k)};
        l.fogDistance = 4000;
        l.display = 1.1;
        return l;
    }

    @Override public void init(GLAutoDrawable d) { gl.init(d.getGL().getGL4()); }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int ww, int hh) { w = Math.max(1, ww); h = Math.max(1, hh); }

    @Override public void display(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        w = Math.max(1, d.getSurfaceWidth());
        h = Math.max(1, d.getSurfaceHeight());
        g.glViewport(0, 0, w, h);
        BeachGl.Light l = light();
        double[] c = l.skyHorizon;
        g.glClearColor((float) disp(c[0], l.display), (float) disp(c[1], l.display), (float) disp(c[2], l.display), 1);
        g.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        g.glEnable(GL.GL_DEPTH_TEST);
        g.glDepthFunc(GL.GL_LEQUAL);
        BeachGl.View v = new BeachGl.View();
        double[] e = eye();
        v.eye = e;
        v.viewProj = mul(perspective(fovy, w / (double) h, 0.05, 40000), lookAt(e[0], e[1], e[2], tx, ty, tz));
        Beach b = beach;
        if (!on) { b = b.copy(); b.relief = 0; b.grainContrast = 0; b.cavity = 0; }
        gl.drawPatch(g, v, l, b, SHORE, TRAMPLE_FROM, TRAMPLE_TO);
    }

    static double disp(double c, double k) { return Math.pow(1 - Math.exp(-c * k), 1 / 2.2); }

    @Override public void dispose(GLAutoDrawable d) { gl.dispose(d.getGL().getGL4()); }

    // ------------------------------------------------------------------ Bild

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1800);
            return;
        }
        if (relaunch(args)) return;
        SwingUtilities.invokeLater(BeachDemo::open);
    }

    static void bild(File out, int width) throws Exception {
        int tw = width / 2, th = tw * 9 / 16;
        BufferedImage img = new BufferedImage(tw * 2, th * 2, BufferedImage.TYPE_INT_RGB);
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        caps.setSampleBuffers(true);
        caps.setNumSamples(4);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, tw, th);
        BeachDemo s = new BeachDemo();
        d.addGLEventListener(s);
        Graphics2D g = img.createGraphics();
        String[] name = {"Ostseesand zertreten, Abendsonne 9° (Streiflicht)", "Mittags, Sonne 55°", "Spülsaum: nass und glatt, landwärts zertreten",
                "Wie das Asset: die Düne in ihrer Größe"};
        for (int i = 0; i < 4; i++) {
            s.scene(i + 1);
            for (int f = 0; f < 2; f++) d.display();
            final BufferedImage[] shot = new BufferedImage[1];
            d.invoke(true, a -> { shot[0] = grab(a, tw, th); return true; });
            int x = (i % 2) * tw, y = (i / 2) * th;
            g.drawImage(shot[0], x, y, null);
            label(g, name[i], x, y);
        }
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println(Beach.ostsee().describe());
        System.out.println(Beach.duene().describe());
        System.out.println("Bild: " + out.getPath());
        d.destroy();
        System.exit(0);
    }

    static void label(Graphics2D g, String t, int x, int y) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(new Color(0, 0, 0, 130));
        g.fillRect(x + 6, y + 6, g.getFontMetrics().stringWidth(t) + 12, 22);
        g.setColor(Color.WHITE);
        g.drawString(t, x + 12, y + 22);
    }

    static BufferedImage grab(GLAutoDrawable a, int w, int h) {
        GL4 gl = a.getGL().getGL4();
        java.nio.ByteBuffer bb = com.jogamp.common.nio.Buffers.newDirectByteBuffer(w * h * 4);
        int fb = a.getContext().getDefaultReadFramebuffer();
        gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, fb);
        gl.glReadBuffer(fb == 0 ? GL.GL_BACK : GL.GL_COLOR_ATTACHMENT0);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                img.setRGB(x, y, ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255));
            }
        return img;
    }

    // ------------------------------------------------------------------ Fenster

    /** Wie DuneDemo: unter Windows braucht die GLCanvas {@code --add-opens} für sun.awt; fehlt es, Neustart mit den Schaltern. */
    static boolean relaunch(String[] args) throws Exception {
        Module desk = ModuleLayer.boot().findModule("java.desktop").orElse(null);
        if (desk == null || desk.isOpen("sun.awt", BeachDemo.class.getModule()) || Boolean.getBoolean("beach.relaunched")) return false;
        String exe = ProcessHandle.current().info().command().orElse("java");
        List<String> cmd = new ArrayList<>(java.util.Arrays.asList(exe, "-Dbeach.relaunched=true", "--enable-native-access=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED", "--add-opens", "java.desktop/sun.awt.windows=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.java2d=ALL-UNNAMED", "-cp", System.getProperty("java.class.path"), BeachDemo.class.getName()));
        cmd.addAll(java.util.Arrays.asList(args));
        Process p = new ProcessBuilder(cmd).inheritIO().start();
        System.exit(p.waitFor());
        return true;
    }

    private static void open() {
        BeachDemo s = new BeachDemo();
        s.scene(1);
        GLCapabilities caps = new GLCapabilities(GLProfile.get(GLProfile.GL4));
        caps.setDoubleBuffered(true);
        caps.setDepthBits(24);
        GLCanvas canvas = new GLCanvas(caps);
        canvas.addGLEventListener(s);
        FFrame f = new FFrame("Strand · beach");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        Color bg = new Color(14, 20, 22), ink = new Color(233, 236, 234);
        f.setBackground(bg);
        root.setBackground(bg);
        JLabel state = new JLabel(" ");
        state.setForeground(ink);
        FSlider alt = new FSlider(2, 70, 9), az = new FSlider(0, 360, 255), rel = new FSlider(0, 80, 22), tile = new FSlider(5, 80, 24);
        alt.addChangeListener(e -> s.sunAlt = alt.getValue());
        az.addChangeListener(e -> s.sunAz = az.getValue());
        rel.addChangeListener(e -> { Beach b = s.beach.copy(); b.relief = rel.getValue() / 1000.0; s.beach = b; });
        tile.addChangeListener(e -> { Beach b = s.beach.copy(); b.tile = tile.getValue() / 10.0; s.beach = b; });
        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        String[] names = {"Sonnenhöhe (Grad)", "Sonne aus (Grad von Nord)", "Relief (mm)", "Kachel (dm)"};
        FSlider[] sl = {alt, az, rel, tile};
        for (int i = 0; i < sl.length; i++) {
            JLabel l = new JLabel(names[i]);
            l.setForeground(ink);
            side.add(l);
            side.add(sl[i]);
        }
        root.add(canvas, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        root.add(state, BorderLayout.SOUTH);
        final int[] drag = new int[2];
        canvas.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) { drag[0] = e.getX(); drag[1] = e.getY(); }
        });
        canvas.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                s.yaw -= (e.getX() - drag[0]) * 0.3;
                s.pitch = Math.max(2, Math.min(85, s.pitch + (e.getY() - drag[1]) * 0.3));
                drag[0] = e.getX(); drag[1] = e.getY();
            }
        });
        canvas.addMouseWheelListener(e -> s.dist = Math.max(1, Math.min(300, s.dist * Math.pow(1.1, e.getPreciseWheelRotation()))));
        canvas.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyTyped(java.awt.event.KeyEvent e) {
                char c = e.getKeyChar();
                if (c >= '0' && c <= '4') s.scene(c - '0');
            }
        });
        f.setSize(1400, 860);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Animator(canvas).start();
        new Timer(300, e -> state.setText("<html>" + s.beach.describe() + "<br>Ziehen: Blick · Rad: Abstand · 1 Abendsonne · 2 Mittag · 3 Spülsaum · 4 Düne · 0 ohne Relief</html>")).start();
    }

    // ------------------------------------------------------------------ Matrizen (Spalten zuerst)

    static float[] perspective(double fovy, double aspect, double n, double f) {
        double t = 1 / Math.tan(Math.toRadians(fovy) / 2);
        float[] m = new float[16];
        m[0] = (float) (t / aspect);
        m[5] = (float) t;
        m[10] = (float) ((f + n) / (n - f));
        m[11] = -1;
        m[14] = (float) (2 * f * n / (n - f));
        return m;
    }

    static float[] lookAt(double ex, double ey, double ez, double cx, double cy, double cz) {
        double fx = cx - ex, fy = cy - ey, fz = cz - ez, fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double sx = -fz, sy = 0, sz = fx, sl = Math.sqrt(sx * sx + sz * sz);
        sx /= sl; sz /= sl;
        double ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
        float[] m = new float[16];
        m[0] = (float) sx; m[4] = (float) sy; m[8] = (float) sz;
        m[1] = (float) ux; m[5] = (float) uy; m[9] = (float) uz;
        m[2] = (float) -fx; m[6] = (float) -fy; m[10] = (float) -fz;
        m[12] = (float) -(sx * ex + sy * ey + sz * ez);
        m[13] = (float) -(ux * ex + uy * ey + uz * ez);
        m[14] = (float) (fx * ex + fy * ey + fz * ez);
        m[15] = 1;
        return m;
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + rr] * b[c * 4 + k];
                r[c * 4 + rr] = s;
            }
        return r;
    }
}
