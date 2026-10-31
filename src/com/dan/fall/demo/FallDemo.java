package com.dan.fall.demo;

import com.dan.fall.Fall;
import com.dan.fbutton.FButton;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Vorführung des Packages ohne App (Paketvertrag Punkt 3 und 10): eine Wand von 250 m über einem See, darauf ein Fall
 * mit 40 m breiter Kante. Regler für Abfluss, Wind, Windrichtung, Temperatur, Sonnenhöhe und -richtung, Stufe; Ziehen
 * dreht, das Rad ändert den Abstand. Unten die Zeit des Falls auf der Grafikkarte.
 * Ohne Fenster: {@code FallDemo --bild datei.png [breite]} rechnet sechs Ansichten (von vorn, am Fuß, von der Seite,
 * mit Wind, im Gegenlicht, bei Frost) und vergleicht dabei Grafikkarte und Prozessor.
 */
public final class FallDemo {
    private FallDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1800);
            return;
        }
        SwingUtilities.invokeLater(FallDemo::open);
    }

    /** Die sechs Ansichten (auch für die Prüfbank ohne JOGL): setzt Szene und Fall für Ansicht k. */
    public static String preset(FallScene s, int k) {
        Fall f = s.fall;
        s.yaw = 0; s.pitch = 4; s.dist = 700; s.tx = 0; s.ty = 130; s.tz = 10; s.fovy = 50;
        s.sunAlt = 30; s.sunAz = 200; s.exposure = 1.4; s.fixedTime = 40;
        f.discharge = 2.0; f.windSpeed = 0; f.windFromDeg = 270; f.temperature = 10;
        String title = switch (k) {
            case 0 -> "Von vorn: oben glatt, unten Schleier";
            case 1 -> { s.dist = 130; s.ty = 35; s.tz = 20; s.pitch = -6; yield "Am Fuß: Tosbecken und Nebel"; }
            case 2 -> { s.yaw = 90; s.dist = 230; s.tz = 20; s.pitch = 2; yield "Von der Seite: die Bahn"; }
            case 3 -> { f.windSpeed = 8; yield "Wind 8 m/s aus Westen"; }
            case 4 -> { s.sunAlt = 9; s.sunAz = 355; s.exposure = 1.8; yield "Gegenlicht: der Schleier leuchtet"; }
            case 5 -> { f.temperature = -12; yield "Frost −12 °C"; }
            default -> "";
        };
        f.update();
        return title;
    }

    static void bild(File out, int width) throws Exception {
        int tw = width / 3, th = tw * 9 / 16;
        BufferedImage img = new BufferedImage(tw * 3, th * 2, BufferedImage.TYPE_INT_RGB);
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, tw, th);
        FallScene s = new FallScene();
        d.addGLEventListener(s);
        Graphics2D g = img.createGraphics();
        String first = "";
        for (int k = 0; k < 6; k++) {
            String title = preset(s, k);
            if (k == 0) first = s.fall.describe();
            for (int i = 0; i < 6; i++) d.display();
            final BufferedImage[] shot = new BufferedImage[1];
            d.invoke(true, a -> { shot[0] = grab(a, tw, th); return true; });
            int x = (k % 3) * tw, y = (k / 3) * th;
            g.drawImage(shot[0], x, y, null);
            label(g, title, x, y);
        }
        label(g, String.format(Locale.GERMAN, "%s · Fall auf der Grafikkarte %.2f ms", s.check, s.gl.gpuMs()), 0, th * 2 - 34);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println(first);
        System.out.println(s.check);
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
        com.jogamp.opengl.GL4 gl = a.getGL().getGL4();
        java.nio.ByteBuffer bb = com.jogamp.common.nio.Buffers.newDirectByteBuffer(w * h * 4);
        int fb = a.getContext().getDefaultDrawFramebuffer();
        gl.glBindFramebuffer(com.jogamp.opengl.GL.GL_READ_FRAMEBUFFER, fb);
        gl.glReadBuffer(fb == 0 ? com.jogamp.opengl.GL.GL_BACK : com.jogamp.opengl.GL.GL_COLOR_ATTACHMENT0);
        gl.glPixelStorei(com.jogamp.opengl.GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, com.jogamp.opengl.GL.GL_RGBA, com.jogamp.opengl.GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                img.setRGB(x, y, ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255));
            }
        return img;
    }

    private static void open() {
        FallScene scene = new FallScene();
        Fall f = scene.fall;
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(scene);
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                scene.yaw += (e.getX() - lx) * 0.3;
                scene.pitch = Math.max(-40, Math.min(80, scene.pitch + (e.getY() - ly) * 0.2));
                lx = e.getX();
                ly = e.getY();
            }
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                scene.dist = Math.max(20, Math.min(4000, scene.dist * Math.pow(1.15, e.getPreciseWheelRotation())));
            }
        };
        canvas.addMouseListener(m);
        canvas.addMouseMotionListener(m);
        canvas.addMouseWheelListener(m);
        Color bg = new Color(13, 19, 22), ink = new Color(231, 236, 236), muted = new Color(143, 160, 164);
        JPanel side = new JPanel();
        side.setBackground(bg);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel title = new JLabel("com.dan.fall");
        title.setForeground(new Color(154, 201, 214));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel state = label(" ", ink), check = label(" ", muted), qL = label("", ink), wL = label("", ink), wdL = label("", ink), tL = label("", ink),
                sunL = label("", ink), azL = label("", ink);
        // Abfluss logarithmisch: 0,05 … 50 m³/s
        FSlider q = new FSlider(0, 300, (int) Math.round(100 * Math.log10(f.discharge / 0.05))), wind = new FSlider(0, 150, 0), wdir = new FSlider(0, 359, 270),
                temp = new FSlider(-25, 25, 10), sun = new FSlider(-4, 60, 30), az = new FSlider(0, 359, 200);
        q.addChangeListener(e -> { f.discharge = 0.05 * Math.pow(10, q.getValue() / 100.0); scene.dirty = true; });
        wind.addChangeListener(e -> { f.windSpeed = wind.getValue() / 10.0; scene.dirty = true; });
        wdir.addChangeListener(e -> { f.windFromDeg = wdir.getValue(); scene.dirty = true; });
        temp.addChangeListener(e -> { f.temperature = temp.getValue(); scene.dirty = true; });
        sun.addChangeListener(e -> scene.sunAlt = sun.getValue());
        az.addChangeListener(e -> scene.sunAz = az.getValue());
        FComboBox quality = new FComboBox(new Object[]{"Niedrig", "Mittel", "Hoch", "Ultra"});
        quality.setSelectedIndex(1);
        quality.addActionListener(e -> { f.quality = Fall.Quality.values()[quality.getSelectedIndex()]; scene.dirty = true; });
        com.dan.fcheckbox.FCheckBox rock = new com.dan.fcheckbox.FCheckBox("Lauf über Fels (Stufenwand)");
        rock.addActionListener(e -> scene.course(rock.isSelected()));
        FButton far = new FButton("Von vorn"), near = new FButton("Am Fuß"), sideV = new FButton("Von der Seite");
        far.addActionListener(e -> { scene.yaw = 0; scene.dist = 700; scene.pitch = 4; scene.ty = 130; scene.tz = 10; });
        near.addActionListener(e -> { scene.yaw = 0; scene.dist = 130; scene.pitch = -6; scene.ty = 35; scene.tz = 20; });
        sideV.addActionListener(e -> { scene.yaw = 90; scene.dist = 230; scene.pitch = 2; scene.ty = 130; scene.tz = 20; });
        Component[] items = {title, state, qL, q, wL, wind, wdL, wdir, tL, temp, sunL, sun, azL, az, rock, far, near, sideV, label("Stufe", ink), quality, check};
        for (Component k : items) {
            if (k instanceof javax.swing.JComponent jc) jc.setAlignmentX(0);
            side.add(k);
            side.add(javax.swing.Box.createVerticalStrut(6));
        }
        side.setPreferredSize(new Dimension(330, 10));
        FFrame fr = new FFrame("com.dan.fall – Wasserfall");
        JPanel root = fr.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(bg);
        root.add(canvas, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        fr.setPreferredFrameSize(new Dimension(1500, 880));
        fr.setSize(1500, 880);
        fr.setMinimumSize(new Dimension(900, 560));
        fr.setBackground(bg);
        fr.setLocationRelativeTo(null);
        fr.setVisible(true);
        Animator anim = new Animator(canvas);
        anim.start();
        new Timer(250, e -> {
            qL.setText(String.format(Locale.GERMAN, "Abfluss %.2f m³/s", f.discharge));
            wL.setText(String.format(Locale.GERMAN, "Wind %.1f m/s", f.windSpeed));
            wdL.setText(String.format(Locale.GERMAN, "Wind aus %03.0f°", f.windFromDeg));
            tL.setText(String.format(Locale.GERMAN, "Temperatur %.0f °C · Eis %.0f %%", f.temperature, f.ice() * 100));
            sunL.setText(String.format(Locale.GERMAN, "Sonnenhöhe %.0f°", scene.sunAlt));
            azL.setText(String.format(Locale.GERMAN, "Sonne aus %03.0f°", scene.sunAz));
            state.setText("<html><body style='width:290px'>" + f.describe()
                    + String.format(Locale.GERMAN, "<br>Abstand %.0f m · %s", scene.dist, scene.gl.describe(f)) + "</body></html>");
            check.setText("<html><body style='width:290px'>" + scene.check + "</body></html>");
        }).start();
    }

    private static JLabel label(String t, Color col) {
        JLabel l = new JLabel(t);
        l.setForeground(col);
        return l;
    }
}
