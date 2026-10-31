package com.dan.beachchair.demo;

import com.dan.beachchair.BeachChair;
import com.dan.beachchair.gl.BeachChairGl;
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
import javax.swing.JCheckBox;
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
import java.util.Locale;

/**
 * Vorführung des Strandkorbs ohne Szene (Paketvertrag Punkt 3 und 10): Körbe auf der Bühne aus Sand und See.
 * <ul>
 * <li>{@code BeachChairDemo}: Fenster; ziehen dreht den Blick, Rad nähert; Tasten 1 ein Korb (wie das Foto), 2 Reihen am
 * Strand, 3 abends verschlossen, 4 Ostsee und Nordsee; Regler für Lehne, Fußstützen und Sonne.</li>
 * <li>{@code BeachChairDemo --bild datei.png [breite]}: die vier Blicke als ein Blatt.</li>
 * </ul>
 */
public final class BeachChairDemo implements GLEventListener {
    final BeachChairGl gl = new BeachChairGl();
    final BeachChairGl glNord = new BeachChairGl();
    BeachChair ost = BeachChair.timmendorf(), nord = BeachChair.sylt();
    final List<BeachChair.Chair> chairs = new ArrayList<>(), nordChairs = new ArrayList<>();
    /** Blick: Ziel, Gieren (Grad, Richtung vom Ziel zum Auge, 0 = Ost, 90 = Nord), Neigung, Abstand. */
    double tx, ty = 0.8, tz, yaw = -55, pitch = 8, dist = 3.6, fovy = 50;
    /** Sonne: Höhe und Richtung (Grad von Nord), Lehne und Fußstützen für die Körbe der Vorführung. */
    volatile double sunAlt = 48, sunAz = 150, recline = 0.25, footrest = 0.0;
    volatile boolean closed;
    volatile int scene = 1;
    volatile boolean dirty = true;
    int w = 1, h = 1;
    static final double SHORE = 14;

    void scene(int k) {
        scene = k;
        chairs.clear();
        nordChairs.clear();
        switch (k) {
            case 1 -> {
                BeachChair.Chair c = ost.chair(0, 0, 0, 115, 3);
                c.stripe = new double[]{0.03, 0.12, 0.45};
                c.recline = recline; c.footrest = footrest; c.closed = closed;
                chairs.add(c);
                tx = 0.1; ty = 0.75; tz = 0; yaw = -62; pitch = 6; dist = 3.8; fovy = 46;
            }
            case 2 -> {
                for (int r = 0; r < 3; r++)
                    for (int i = -6; i <= 6; i++) {
                        long id = r * 100 + i + 50;
                        double x = i * 3.2 + (r % 2) * 1.2, z = r * 4.5;
                        BeachChair.Chair c = ost.chair(x, 0, z, BeachChair.facing(0, 250, 5, id), id);
                        ost.pose(c, 0.92, 0.6);
                        chairs.add(c);
                    }
                tx = -2; ty = 0.6; tz = 3; yaw = 28; pitch = 9; dist = 19; fovy = 50;
            }
            case 3 -> {
                for (int i = -4; i <= 4; i++) {
                    BeachChair.Chair c = ost.chair(i * 3.2, 0, 0, 0, i + 20);
                    c.closed = i % 3 != 0;
                    if (!c.closed) { c.recline = 0.5; c.footrest = 1; }
                    chairs.add(c);
                }
                tx = -1; ty = 0.7; tz = 0; yaw = 38; pitch = 8; dist = 12; fovy = 50;
            }
            default -> {
                BeachChair.Chair a = ost.chair(-1.0, 0, 0, 20, 7);
                a.stripe = new double[]{0.03, 0.12, 0.45};
                a.recline = 1; a.footrest = 1; a.closed = false;
                chairs.add(a);
                BeachChair.Chair b = nord.chair(1.2, 0, 0.2, -15, 8);
                b.recline = 0; b.footrest = 0; b.closed = false;
                nordChairs.add(b);
                tx = 0.1; ty = 0.7; tz = 0; yaw = 62; pitch = 12; dist = 5.6; fovy = 48;
            }
        }
        dirty = true;
    }

    double[] eye() {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        // Gieren von Ost gegen den Uhrzeigersinn (z nach Süden: Nord ist −z)
        return new double[]{tx + dist * Math.cos(pr) * Math.cos(yr), Math.max(0.15, ty + dist * Math.sin(pr)), tz - dist * Math.cos(pr) * Math.sin(yr)};
    }

    BeachChairGl.Light light() {
        BeachChairGl.Light l = new BeachChairGl.Light();
        double a = Math.toRadians(sunAz), e = Math.toRadians(sunAlt);
        l.sunDir = new double[]{Math.sin(a) * Math.cos(e), Math.sin(e), -Math.cos(a) * Math.cos(e)};
        // tief stehende Sonne: wärmer und schwächer, der Himmel dunkler [G]
        double k = Math.max(0, Math.min(1, sunAlt / 30));
        l.sunColor = new double[]{3.1 * (0.55 + 0.45 * k), 2.9 * (0.35 + 0.65 * k), 2.6 * (0.18 + 0.82 * k)};
        l.skyTop = new double[]{0.34 * (0.4 + 0.6 * k), 0.47 * (0.4 + 0.6 * k), 0.70 * (0.45 + 0.55 * k)};
        l.skyHorizon = new double[]{0.70 * (0.6 + 0.4 * k), 0.76 * (0.5 + 0.5 * k), 0.86 * (0.45 + 0.55 * k)};
        l.fogColor = l.skyHorizon.clone();
        l.fogDistance = 5000;
        l.display = 1.0;
        return l;
    }

    @Override public void init(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        ost.quality = BeachChair.Quality.ULTRA;
        nord.quality = BeachChair.Quality.ULTRA;
        gl.init(g, ost);
        glNord.init(g, nord);
        gl.setChairs(chairs);
        glNord.setChairs(nordChairs);
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int ww, int hh) { w = Math.max(1, ww); h = Math.max(1, hh); }

    @Override public void display(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        if (dirty) {
            dirty = false;
            if (scene == 1) for (BeachChair.Chair c : chairs) { c.recline = recline; c.footrest = footrest; c.closed = closed; }
            gl.setChairs(chairs);
            glNord.setChairs(nordChairs);
        }
        w = Math.max(1, d.getSurfaceWidth());
        h = Math.max(1, d.getSurfaceHeight());
        g.glViewport(0, 0, w, h);
        BeachChairGl.Light l = light();
        double[] fc = l.fogColor;
        g.glClearColor((float) disp(fc[0]), (float) disp(fc[1]), (float) disp(fc[2]), 1);
        g.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        g.glEnable(GL.GL_DEPTH_TEST);
        g.glDepthFunc(GL.GL_LEQUAL);
        BeachChairGl.View v = new BeachChairGl.View();
        double[] e = eye();
        v.eye = e;
        v.viewProj = mul(perspective(fovy, w / (double) h, 0.05, 40000), lookAt(e[0], e[1], e[2], tx, ty, tz));
        // die Bühne kommt schon mit Anzeige-Kurve; der Schatten multipliziert das fertige Bild (für die Vorführung nah genug)
        gl.drawStage(g, v, l, SHORE);
        gl.drawShadows(g, v, l, null);
        glNord.drawShadows(g, v, l, null);
        gl.draw(g, v, l);
        glNord.draw(g, v, l);
    }

    static double disp(double c) { return Math.pow(1 - Math.exp(-c * 1.0), 1 / 2.2); }

    @Override public void dispose(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.dispose(g);
        glNord.dispose(g);
    }

    String status() {
        return String.format(Locale.GERMAN, "%s<br>%s · %s", ost.describe(), gl.describe(), nord.describe());
    }

    // ------------------------------------------------------------------ Bild

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1800);
            return;
        }
        if (relaunch(args)) return;
        SwingUtilities.invokeLater(BeachChairDemo::open);
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
        BeachChairDemo s = new BeachChairDemo();
        d.addGLEventListener(s);
        Graphics2D g = img.createGraphics();
        String[] name = {"Ein Ostseekorb wie auf dem Foto, mittags", "Reihen am Timmendorfer Strand, nachmittags",
                "Abends: die meisten verschlossen", "Ostsee (Lehne hinten, Fußstützen heraus) und Nordsee"};
        double[][] sun = {{52, 165}, {38, 225}, {9, 285}, {45, 200}};
        for (int i = 0; i < 4; i++) {
            s.sunAlt = sun[i][0];
            s.sunAz = sun[i][1];
            s.recline = 0.15;
            s.footrest = 0;
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
        System.out.println(s.ost.describe());
        System.out.println(s.nord.describe());
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
        if (desk == null || desk.isOpen("sun.awt", BeachChairDemo.class.getModule()) || Boolean.getBoolean("beachchair.relaunched")) return false;
        String exe = ProcessHandle.current().info().command().orElse("java");
        List<String> cmd = new ArrayList<>(java.util.Arrays.asList(exe, "-Dbeachchair.relaunched=true",
                "--enable-native-access=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.awt.windows=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.java2d=ALL-UNNAMED",
                "-cp", System.getProperty("java.class.path"), BeachChairDemo.class.getName()));
        cmd.addAll(java.util.Arrays.asList(args));
        Process p = new ProcessBuilder(cmd).inheritIO().start();
        System.exit(p.waitFor());
        return true;
    }

    private static void open() {
        BeachChairDemo s = new BeachChairDemo();
        s.scene(1);
        GLCapabilities caps = new GLCapabilities(GLProfile.get(GLProfile.GL4));
        caps.setDoubleBuffered(true);
        caps.setDepthBits(24);
        caps.setSampleBuffers(true);
        caps.setNumSamples(4);
        GLCanvas canvas = new GLCanvas(caps);
        canvas.addGLEventListener(s);
        FFrame f = new FFrame("Strandkorb · beachchair");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        Color bg = new Color(14, 20, 22), ink = new Color(233, 236, 234);
        f.setBackground(bg);
        root.setBackground(bg);
        JLabel state = new JLabel(" ");
        state.setForeground(ink);
        FSlider rec = new FSlider(0, 100, 25), foot = new FSlider(0, 100, 0), alt = new FSlider(-5, 70, 48), az = new FSlider(60, 300, 150);
        rec.addChangeListener(e -> { s.recline = rec.getValue() / 100.0; s.dirty = true; });
        foot.addChangeListener(e -> { s.footrest = foot.getValue() / 100.0; s.dirty = true; });
        alt.addChangeListener(e -> s.sunAlt = alt.getValue());
        az.addChangeListener(e -> s.sunAz = az.getValue());
        JCheckBox zu = new JCheckBox("Verschlossen");
        zu.setOpaque(false);
        zu.setForeground(ink);
        zu.addActionListener(e -> { s.closed = zu.isSelected(); s.dirty = true; canvas.requestFocusInWindow(); });
        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        String[] names = {"Lehne", "Fußstützen", "Sonnenhöhe (Grad)", "Sonne aus (Grad von Nord)"};
        FSlider[] sl = {rec, foot, alt, az};
        for (int i = 0; i < sl.length; i++) {
            JLabel l = new JLabel(names[i]);
            l.setForeground(ink);
            side.add(l);
            side.add(sl[i]);
        }
        side.add(zu);
        root.add(canvas, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        root.add(state, BorderLayout.SOUTH);
        final int[] drag = new int[3];
        canvas.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) { drag[0] = e.getX(); drag[1] = e.getY(); }
        });
        canvas.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                s.yaw -= (e.getX() - drag[0]) * 0.3;
                s.pitch = Math.max(-5, Math.min(80, s.pitch + (e.getY() - drag[1]) * 0.3));
                drag[0] = e.getX(); drag[1] = e.getY();
            }
        });
        canvas.addMouseWheelListener(e -> s.dist = Math.max(1.2, Math.min(200, s.dist * Math.pow(1.1, e.getPreciseWheelRotation()))));
        canvas.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyTyped(java.awt.event.KeyEvent e) {
                char c = e.getKeyChar();
                if (c >= '1' && c <= '4') s.scene(c - '0');
            }
        });
        f.setSize(1400, 860);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Animator(canvas).start();
        new Timer(300, e -> state.setText("<html>" + s.status() + "<br>Ziehen: Blick · Rad: Abstand · 1 ein Korb · 2 Reihen · 3 abends · 4 Ostsee und Nordsee</html>")).start();
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
