package com.dan.precip.demo;

import com.dan.precip.Drops;
import com.dan.precip.Precip;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Vorführung des Pakets ohne Szene (Paketvertrag Punkt 3 und 10): Regen über Wasser mit Pfählen, einem Steg und einem
 * dunklen Waldrand. Regler für Regenmenge, Wind, Belichtung und Zeitlupe; Ziehen dreht, das Rad zoomt.
 * Ohne Fenster: {@code PrecipDemo --bild datei.png [breite]} rechnet acht Proben (Regen vor dem Wald, Gegenlicht, auf
 * dem Wasser, Tropfen nah mit 1/2000 s, 1/30 s wie Film, Schauer, Regenbogen, Zeitlupe) und den Selbsttest
 * Grafikkarte = Prozessor.
 */
public final class PrecipDemo {
    private PrecipDemo() {}

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 3840);
            return;
        }
        SwingUtilities.invokeLater(PrecipDemo::open);
    }

    /** Eine Probe: Titel und was an der Szene zu stellen ist. */
    private record Probe(String title, java.util.function.Consumer<PrecipScene> set) {}

    static void bild(File out, int width) throws Exception {
        int cols = 4, tw = width / cols, th = tw * 9 / 16;
        Probe[] probes = {
                new Probe("Regen vor dem Wald · 5 mm/h · 1/250 s", s -> { onPier(s); s.precip.rate = 5; }),
                new Probe("Gegenlicht · Sonne 9° voraus", s -> { onPier(s); s.precip.rate = 5; s.sunAlt = 9; s.sunAz = 262; s.sunInRain = true; s.lookAt = new double[]{-40, 4.5, 12}; }),
                new Probe("Auf dem Wasser · 1,2 m · 5 mm/h", s -> {
                    s.eyeAt = new double[]{6, 1.2, -6};
                    s.lookAt = new double[]{6, -0.3, -2};
                    s.precip.rate = 5;
                    s.fovy = 50;
                }),
                new Probe("Tropfen nah · 1/2000 s · 15 mm/h", s -> {
                    onPier(s);
                    s.precip.rate = 15;
                    s.precip.shutter = 1 / 2000.0;
                    s.precip.focus = 3;
                    s.precip.aperture = 0.0015;
                    s.fovy = 9;
                    s.lookAt = new double[]{-2, 2.4, 10.5};
                }),
                new Probe("Wie Film · 1/30 s · 5 mm/h", s -> { onPier(s); s.precip.rate = 5; s.precip.shutter = 1 / 30.0; }),
                new Probe("Schauer · 25 mm/h · Wind 9 m/s", s -> { onPier(s); s.precip.rate = 25; s.precip.dsd = Drops.Dsd.SCHAUER; s.precip.windSpeed = 9; }),
                new Probe("Regenbogen · Schauer in 1,5 km, Sonne im Rücken", s -> {
                    s.eyeAt = new double[]{-2, 2.9, 8};
                    s.sunAlt = 24;
                    s.sunAz = 200;
                    s.precip.rate = 8;
                    // Schauer gegenüber der Sonne, 1,5 km weg, 900 m Halbmesser
                    double a = Math.toRadians(200 + 180), cx = Math.sin(a) * 1500, cz = -Math.cos(a) * 1500;
                    s.precip.curtain = (x, z) -> Math.max(0, 1 - Math.hypot(x - cx, z - cz) / 900);
                    s.lookAt = new double[]{-2 + Math.sin(a) * 100, 2.9 + 18, 8 - Math.cos(a) * 100};
                    s.fovy = 70;
                }),
                new Probe("Zeitlupe 1/20 · 1/250 s · 8 mm/h", s -> { onPier(s); s.precip.rate = 8; s.precip.timeScale = 0.05; s.fovy = 30; s.lookAt = new double[]{-2, 2.2, 14}; }),
        };
        int rows = (probes.length + cols - 1) / cols;
        BufferedImage img = new BufferedImage(width, th * rows, BufferedImage.TYPE_INT_RGB);
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, tw, th);
        PrecipScene s = new PrecipScene();
        s.frozen = true;
        d.addGLEventListener(s);
        d.display();
        final String[] check = {""};
        d.invoke(true, a -> { check[0] = s.check(a); return true; });
        Graphics2D gg = img.createGraphics();
        StringBuilder log = new StringBuilder();
        for (int k = 0; k < probes.length; k++) {
            reset(s);
            probes[k].set().accept(s);
            s.precip.quality = Precip.Quality.HIGH;
            s.pgl.reseed = true;
            s.advance(2.5);
            d.display();
            d.display();
            gg.drawImage(grab(d, tw, th), (k % cols) * tw, (k / cols) * th, null);
            label(gg, probes[k].title(), (k % cols) * tw, (k / cols) * th);
            log.append(probes[k].title()).append(": ").append(s.status).append('\n');
        }
        gg.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println(check[0]);
        System.out.print(log);
        System.out.println("Bild: " + out.getPath());
        d.destroy();
        System.exit(0);
    }

    static void onPier(PrecipScene s) {
        s.eyeAt = new double[]{-2, 2.9, 7.5};
        s.lookAt = new double[]{-2, 2.4, 30};
        s.fovy = 55;
    }

    static void reset(PrecipScene s) {
        s.eyeAt = null;
        s.lookAt = null;
        s.fovy = 55;
        s.sunAlt = 20;
        s.sunAz = 220;
        s.precip.rate = 5;
        s.precip.dsd = Drops.Dsd.LANDREGEN;
        s.precip.windSpeed = 5;
        s.precip.windFromDeg = 240;
        s.precip.shutter = 1 / 250.0;
        s.precip.timeScale = 1;
        s.precip.curtain = null;
        s.precip.focus = 4;
        s.precip.aperture = 0.003;
        s.sunInRain = false;
    }

    static BufferedImage grab(GLOffscreenAutoDrawable d, int w, int h) {
        final BufferedImage[] g = new BufferedImage[1];
        d.invoke(true, a -> { g[0] = read(a, w, h); return true; });
        return g[0];
    }

    static BufferedImage read(GLAutoDrawable a, int w, int h) {
        com.jogamp.opengl.GL4 gl = a.getGL().getGL4();
        ByteBuffer b = ByteBuffer.allocateDirect(w * h * 4);
        gl.glBindFramebuffer(com.jogamp.opengl.GL.GL_READ_FRAMEBUFFER, a.getContext().getDefaultReadFramebuffer());
        gl.glPixelStorei(com.jogamp.opengl.GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, com.jogamp.opengl.GL.GL_RGBA, com.jogamp.opengl.GL.GL_UNSIGNED_BYTE, b);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int o = ((h - 1 - y) * w + x) * 4;
                img.setRGB(x, y, (b.get(o) & 255) << 16 | (b.get(o + 1) & 255) << 8 | (b.get(o + 2) & 255));
            }
        return img;
    }

    static void label(Graphics2D g, String t, int x, int y) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(x + 6, y + 6, g.getFontMetrics().stringWidth(t) + 12, 22);
        g.setColor(Color.WHITE);
        g.drawString(t, x + 12, y + 22);
    }

    static void open() {
        PrecipScene s = new PrecipScene();
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(s);
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                s.yaw += (e.getX() - lx) * 0.3;
                s.pitch = Math.max(-5, Math.min(80, s.pitch + (e.getY() - ly) * 0.3));
                lx = e.getX();
                ly = e.getY();
            }
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) { s.dist = Math.max(2, Math.min(200, s.dist * Math.pow(1.1, e.getWheelRotation()))); }
        };
        canvas.addMouseListener(m);
        canvas.addMouseMotionListener(m);
        canvas.addMouseWheelListener(m);
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        JLabel info = new JLabel();
        side.add(slider("Regen (mm/h, Wurzel)", 0, 100, 22, v -> s.precip.rate = Math.pow(v / 10.0, 2)));
        side.add(slider("Wind (m/s)", 0, 25, 5, v -> s.precip.windSpeed = v));
        side.add(slider("Belichtung 1/x s", 30, 2000, 250, v -> s.precip.shutter = 1.0 / v));
        side.add(slider("Zeitlupe (%)", 1, 100, 100, v -> s.precip.timeScale = v / 100.0));
        side.add(slider("Sonne (°)", -5, 70, 20, v -> s.sunAlt = v));
        side.add(info);
        JFrame f = new JFrame("com.dan.precip");
        f.setLayout(new BorderLayout());
        f.add(canvas, BorderLayout.CENTER);
        f.add(side, BorderLayout.EAST);
        f.setSize(1500, 860);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setVisible(true);
        new Animator(canvas).start();
        new Timer(500, e -> info.setText("<html><body style='width:260px'>" + s.status + "</body></html>")).start();
    }

    private static JPanel slider(String name, int lo, int hi, int v, java.util.function.IntConsumer set) {
        JPanel p = new JPanel(new BorderLayout());
        JLabel l = new JLabel(name + ": " + v);
        JSlider sl = new JSlider(lo, hi, v);
        sl.addChangeListener(e -> { set.accept(sl.getValue()); l.setText(name + ": " + sl.getValue()); });
        set.accept(v);
        p.add(l, BorderLayout.NORTH);
        p.add(sl, BorderLayout.CENTER);
        return p;
    }

    static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }
}
