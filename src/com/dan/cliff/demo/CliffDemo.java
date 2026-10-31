package com.dan.cliff.demo;

import com.dan.cliff.Cliff;
import com.dan.cliff.Tor;
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
 * Vorführung des Packages ohne App (Paketvertrag Punkt 3 und 10): eine Wand von 300 m mit Absatz, Überhang unter der
 * Kante, nassem Streifen und Geröll am Fuß. Regler für Relief, Kluftabstand, Flechten, Moos, Nässe, Schnee,
 * Sonnenhöhe und -richtung, Stufe; Ziehen dreht, das Rad ändert den Abstand. Unten die Zeit auf der Grafikkarte.
 * Ohne Fenster: {@code CliffDemo --bild datei.png [breite]} rechnet sechs Ansichten (aus 2 km, aus 50 m, senkrecht von
 * der Seite, Abend, nach Regen, im Mai mit Schnee) und vergleicht dabei Grafikkarte und Prozessor. Mit {@code --granit}
 * (seit Brocken Phase 8) dieselbe Wand aus Granit, dazu zwei Felsburgen auf der Hochfläche und ein Blockmeer am Fuß.
 * Mit {@code --kliff} (seit Sylt Phase 4) das Rote Kliff aus Lockergestein: Schichten, Runsen, Rieselsand, Sickerstreifen,
 * Hohlkehle und die überhängende Grasnarbe, im Fenster wie im Bild.
 * Mit {@code -Draueis=cm} (seit Brocken Phase 10) trägt der Fels Raueis aus 250° ({@code CliffGl.Light.rimeThickness}).
 */
public final class CliffDemo {
    private CliffDemo() { }

    public static void main(String[] args) throws Exception {
        java.util.List<String> a = java.util.Arrays.asList(args);
        String art = a.contains("--kliff") ? "kliff" : a.contains("--granit") ? "granit" : "gneis";
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 && args[2].matches("\\d+") ? Integer.parseInt(args[2]) : 1800, art);
            return;
        }
        SwingUtilities.invokeLater(() -> open(art));
    }

    static void bild(File out, int width) throws Exception { bild(out, width, "gneis"); }

    static void bild(File out, int width, boolean granit) throws Exception { bild(out, width, granit ? "granit" : "gneis"); }

    static void bild(File out, int width, String art) throws Exception {
        boolean granit = art.equals("granit"), kliff = art.equals("kliff");
        int tw = width / 3, th = tw * 9 / 16;
        BufferedImage img = new BufferedImage(tw * 3, th * 2, BufferedImage.TYPE_INT_RGB);
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, tw, th);
        CliffScene s = new CliffScene(art);
        d.addGLEventListener(s);
        String[] title = kliff
                ? new String[]{"Rotes Kliff aus 380 m", "Aus 45 m: Lehm, Runsen, Findlinge", "Abend, Sonne aus Westen", "Nach Regen", "Hohlkehle am Fuß",
                        "Kante mit Grasnarbe"}
                : granit
                ? new String[]{"Granit aus 2 km", "Aus 30 m: Wollsäcke zwischen Klüften", "Felsburg auf der Hochfläche", "Abend, Sonne von Westen",
                        "Blockmeer am Fuß", "Nach Regen: nass und dunkel"}
                : new String[]{"Die Wand aus 2 km", "Aus 50 m: Bänder, Klüfte, Flechten", "Senkrecht von der Seite: keine Streifen", "Abend, Sonne von Westen",
                        "Nach Regen: nass und dunkel", "Mai: Schnee auf den Absätzen"};
        Graphics2D g = img.createGraphics();
        for (int k = 0; k < 6; k++) {
            s.yaw = 10; s.pitch = 8; s.dist = 600; s.tx = 0; s.ty = 150; s.tz = -60; s.fovy = 50;
            s.sunAlt = 30; s.sunAz = 160; s.rainWet = 0; s.snow = 0;
            if (kliff) {
                s.ty = 14; s.tz = 20; s.sunAlt = 25; s.sunAz = 200;
                switch (k) {
                    case 0 -> { s.yaw = 20; s.pitch = 4; s.dist = 380; }
                    case 1 -> { s.yaw = 5; s.pitch = 3; s.dist = 45; s.ty = 13; }
                    case 2 -> { s.yaw = -35; s.pitch = 6; s.dist = 220; s.sunAlt = 7; s.sunAz = 265; s.exposure = 2.2; }
                    case 3 -> { s.yaw = 15; s.pitch = 4; s.dist = 140; s.rainWet = 0.8; }
                    case 4 -> { s.yaw = 10; s.pitch = -4; s.dist = 16; s.ty = 2.5; s.tz = 27; s.fovy = 60; }
                    case 5 -> { s.yaw = 35; s.pitch = 22; s.dist = 14; s.ty = 25; s.tz = 14; s.fovy = 60; }
                    default -> { }
                }
            } else if (granit) switch (k) {
                case 0 -> { s.dist = 2000; s.pitch = 6; }
                case 1 -> { s.dist = 30; s.ty = 120; s.tz = CliffScene.zAt(0, 120) - 2; s.pitch = 4; }
                case 2 -> { Tor t = s.tors.get(0); s.tx = t.x; s.ty = t.y + 3; s.tz = t.z; s.dist = 22; s.pitch = 10; s.yaw = 200; }
                case 3 -> { s.sunAlt = 7; s.sunAz = 260; s.exposure = 2.2; }
                case 4 -> { s.tx = 0; s.ty = 2; s.tz = 70; s.dist = 45; s.pitch = 22; s.yaw = 30; }
                case 5 -> { s.rainWet = 0.85; s.dist = 300; }
                default -> { }
            }
            else switch (k) {
                case 0 -> { s.dist = 2000; s.pitch = 6; }
                case 1 -> { s.dist = 50; s.ty = 120; s.tz = CliffScene.zAt(0, 120) - 2; s.pitch = 4; }
                case 2 -> { s.yaw = 92; s.dist = 220; s.ty = 160; s.tz = CliffScene.zAt(0, 160); s.pitch = 2; }
                case 3 -> { s.sunAlt = 7; s.sunAz = 260; s.exposure = 2.2; }
                case 4 -> { s.rainWet = 0.85; s.dist = 300; }
                case 5 -> { s.snow = 0.7; s.dist = 900; s.pitch = 14; }
                default -> { }
            }
            if (k != (kliff ? 2 : 3)) s.exposure = 1.4;
            for (int i = 0; i < 4; i++) d.display();
            final BufferedImage[] shot = new BufferedImage[1];
            d.invoke(true, a -> { shot[0] = grab(a, tw, th); return true; });
            int x = (k % 3) * tw, y = (k / 3) * th;
            g.drawImage(shot[0], x, y, null);
            label(g, title[k], x, y);
        }
        label(g, String.format(Locale.GERMAN, "%s · %s · Fels %.2f ms%s", s.cliff.describe(), s.check, s.gpuMs,
                granit ? String.format(Locale.GERMAN, " · %d Blöcke", s.blockCount) : ""), 0, th * 2 - 34);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println(s.check);
        System.out.println(s.map.describe());
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

    private static void open(String art) {
        CliffScene scene = new CliffScene(art);
        if (scene.kliff) { scene.ty = 14; scene.tz = 20; scene.dist = 160; scene.pitch = 4; scene.sunAz = 200; }
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(scene);
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                scene.yaw += (e.getX() - lx) * 0.3;
                scene.pitch = Math.max(-30, Math.min(80, scene.pitch + (e.getY() - ly) * 0.2));
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
        JLabel title = new JLabel("com.dan.cliff");
        title.setForeground(new Color(154, 161, 166));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel state = label(" ", ink), check = label(" ", muted), relL = label("", ink), jointL = label("", ink), lichL = label("", ink), mossL = label("", ink),
                wetL = label("", ink), snowL = label("", ink), sunL = label("", ink), azL = label("", ink);
        Cliff c = scene.cliff;
        FSlider rel = new FSlider(0, 120, (int) (c.reliefAmp * 10)), joint = new FSlider(3, 30, (int) c.jointSpacing), lich = new FSlider(0, 100, 50),
                moss = new FSlider(0, 100, 50), wet = new FSlider(0, 100, 0), snow = new FSlider(0, 100, 0), sun = new FSlider(-4, 60, 30), az = new FSlider(60, 300, 160);
        rel.addChangeListener(e -> c.reliefAmp = rel.getValue() / 10.0);
        joint.addChangeListener(e -> c.jointSpacing = joint.getValue());
        lich.addChangeListener(e -> c.lichenAmount = lich.getValue() / 100.0);
        moss.addChangeListener(e -> c.mossAmount = moss.getValue() / 100.0);
        wet.addChangeListener(e -> scene.rainWet = wet.getValue() / 100.0);
        snow.addChangeListener(e -> scene.snow = snow.getValue() / 100.0);
        sun.addChangeListener(e -> scene.sunAlt = sun.getValue());
        az.addChangeListener(e -> scene.sunAz = az.getValue());
        FComboBox quality = new FComboBox(new Object[]{"Niedrig", "Mittel", "Hoch", "Ultra"});
        quality.setSelectedIndex(1);
        quality.addActionListener(e -> c.quality = Cliff.Quality.values()[quality.getSelectedIndex()]);
        FButton far = new FButton("Aus 2 km"), near = new FButton("Aus 50 m");
        far.addActionListener(e -> { scene.dist = 2000; scene.pitch = 6; scene.ty = 150; });
        near.addActionListener(e -> { scene.dist = 50; scene.pitch = 4; scene.ty = 120; });
        Component[] items = {title, state, relL, rel, jointL, joint, lichL, lich, mossL, moss, wetL, wet, snowL, snow, sunL, sun, azL, az, far, near, label("Stufe", ink), quality, check};
        for (Component k : items) {
            if (k instanceof javax.swing.JComponent jc) jc.setAlignmentX(0);
            side.add(k);
            side.add(javax.swing.Box.createVerticalStrut(6));
        }
        side.setPreferredSize(new Dimension(320, 10));
        FFrame f = new FFrame("com.dan.cliff – Steilwand");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(bg);
        root.add(canvas, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        f.setPreferredFrameSize(new Dimension(1500, 860));
        f.setSize(1500, 860);
        f.setMinimumSize(new Dimension(900, 560));
        f.setBackground(bg);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        Animator anim = new Animator(canvas);
        anim.start();
        new Timer(250, e -> {
            relL.setText(String.format(Locale.GERMAN, "Feinform %.1f m", c.reliefAmp));
            jointL.setText(String.format(Locale.GERMAN, "Klüfte alle %.0f m", c.jointSpacing));
            lichL.setText(String.format(Locale.GERMAN, "Flechten %.0f %%", c.lichenAmount * 100));
            mossL.setText(String.format(Locale.GERMAN, "Moos %.0f %%", c.mossAmount * 100));
            wetL.setText(String.format(Locale.GERMAN, "Nässe nach Regen %.0f %%", scene.rainWet * 100));
            snowL.setText(String.format(Locale.GERMAN, "Schnee %.0f %%", scene.snow * 100));
            sunL.setText(String.format(Locale.GERMAN, "Sonnenhöhe %.0f°", scene.sunAlt));
            azL.setText(String.format(Locale.GERMAN, "Sonne aus %03.0f°", scene.sunAz));
            state.setText("<html><body style='width:280px'>" + c.describe() + "<br>" + scene.map.describe()
                    + String.format(Locale.GERMAN, "<br>Abstand %.0f m · Fels auf der Grafikkarte %.2f ms", scene.dist, scene.gpuMs) + "</body></html>");
            check.setText("<html><body style='width:280px'>" + scene.check + "</body></html>");
        }).start();
    }

    private static JLabel label(String t, Color col) {
        JLabel l = new JLabel(t);
        l.setForeground(col);
        return l;
    }
}
