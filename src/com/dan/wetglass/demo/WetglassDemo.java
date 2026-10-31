package com.dan.wetglass.demo;

import com.dan.fbutton.FButton;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.wetglass.Wetglass;
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
 * Vorführung des Packages ohne Leuchtturm (Paketvertrag Punkt 3 und 10): eine Scheibe im Regen vor einem Bild von See
 * und Turm. Regler für Regen, Wind, Neigung der Scheibe, Ausschnitt, Fokus, Zeitlupe und Stufe; mit der Maus wischen.
 * Ohne Fenster: {@code WetglassDemo --bild datei.png [breite]} rechnet zwölf Bilder: die ersten Tropfen, nach einer
 * Minute, nach drei Minuten, nach dem Wischen, eine Nahaufnahme mit dem Turm kopfüber, eine geneigte Scheibe; Beschlag
 * mit Wischspur, eine Überspülung und was sie hinterlässt; einen Handlauf und einen Pfosten im Regen und einen
 * Handlauf im Sprühnebel. Dabei vergleicht es Grafikkarte und Prozessor.
 */
public final class WetglassDemo {
    private WetglassDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1800);
            return;
        }
        SwingUtilities.invokeLater(WetglassDemo::open);
    }

    static void bild(File out, int width) throws Exception {
        int tw = width / 3, th = tw * 3 / 4;
        BufferedImage img = new BufferedImage(tw * 3, th * 4, BufferedImage.TYPE_INT_RGB);
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, tw, th);
        WetglassScene s = new WetglassScene();
        s.frozen = true;
        s.glass.quality = Wetglass.Quality.HIGH;
        s.glass.reset();
        d.addGLEventListener(s);
        Graphics2D g = img.createGraphics();
        String check = "";
        // eine Scheibe, die nach und nach nass wird (Ausschnitt 24 cm hoch)
        double[] at = {8, 60, 180};
        String[] what = {"die ersten Tropfen", "nach einer Minute", "nach drei Minuten: Läufer und Spuren"};
        double t = 0;
        s.half = 0.12;
        s.centerX = 0.17;
        s.centerY = 0.17;
        for (int k = 0; k < 3; k++) {
            s.advance(at[k] - t);
            t = at[k];
            d.display();
            if (k == 2) {
                final String[] c = new String[1];
                d.invoke(true, a -> { c[0] = s.check(a); return true; });
                check = c[0];
            }
            g.drawImage(grab(d, tw, th), k * tw, 0, null);
            label(g, String.format(Locale.GERMAN, "5 mm/h · %s · %s", what[k], s.glass.describe().replaceAll(" · .*", "")), k * tw, 0);
        }
        // wischen, 20 s weiter
        s.glass.wipe(0.17, 0.17, 0.06);
        s.advance(20);
        d.display();
        g.drawImage(grab(d, tw, th), 0, th, null);
        label(g, "gewischt, 20 s später", 0, th);
        // Nahaufnahme: 5 cm Ausschnitt, der Turm steht in den Tropfen kopf
        s.half = 0.025;
        s.centerX = 0.05;
        s.centerY = 0.25;
        s.focusGlass = true;
        d.display();
        g.drawImage(grab(d, tw, th), tw, th, null);
        label(g, "Nahaufnahme 5 cm: Himmel und See stehen in jedem Tropfen kopf", tw, th);
        // geneigte Scheibe (Dachfenster 30°): Tropfen laufen erst später los
        WetglassScene s2 = s;
        s2.glass.reset();
        s2.tiltDeg = 30;
        s2.half = 0.12;
        s2.centerX = 0.17;
        s2.centerY = 0.17;
        s2.advance(180);
        d.display();
        g.drawImage(grab(d, tw, th), tw * 2, th, null);
        label(g, String.format(Locale.GERMAN, "Dachfenster 30°, drei Minuten · %s", s2.glass.describe().replaceAll(" · .*", "")), tw * 2, th);
        // Phase 8b: Beschlag mit Wischspur
        s.tiltDeg = 90;
        s.focusGlass = false;
        s.rate = 3;
        s.glass.reset();
        s.condensation = 1.2e-8;
        s.advance(70);
        for (double x = 0.07; x <= 0.27; x += 0.01) s.glass.wipeInside(x, 0.15 + 0.25 * (x - 0.17), 0.022);
        s.advance(45);
        d.display();
        g.drawImage(grab(d, tw, th), 0, th * 2, null);
        label(g, String.format(Locale.GERMAN, "Beschlag innen, mit der Hand gewischt, 45 s später · Beschlag %.0f %%", s.glass.fogMean() * 100), 0, th * 2);
        // Überspülung: Seewasser läuft als Haut ab, zerreißt in Tropfen und lässt Salz
        s.condensation = 0;
        s.glass.reset();
        s.rate = 4;
        s.wind = 14;
        s.advance(60);
        s.glass.flood(1.2, true);
        s.advance(1.2);
        d.display();
        g.drawImage(grab(d, tw, th), tw, th * 2, null);
        label(g, String.format(Locale.GERMAN, "Überspülung: ein Schwall Seewasser läuft als Haut ab (%.2f mm)", s.glass.sheetMm()), tw, th * 2);
        s.rate = 0;
        while (s.glass.sheetMm() > 0) s.advance(0.5);
        s.advance(240);
        d.display();
        g.drawImage(grab(d, tw, th), tw * 2, th * 2, null);
        label(g, String.format(Locale.GERMAN, "danach getrocknet: Salz %.1f g/m², %,d Tropfen", s.glass.saltMean() * Wetglass.SALT_FULL, s.glass.count()), tw * 2, th * 2);
        // dieselbe Simulation auf Zylindern
        s.rate = 8;
        s.wind = 10;
        s.mode = 1;
        s.advance(150);
        d.display();
        g.drawImage(grab(d, tw, th), 0, th * 3, null);
        label(g, String.format(Locale.GERMAN, "Handlauf 6 cm, 8 mm/h, Wind 10 m/s: oben liegen, unten hängen und fallen (%,d abgefallen)", s.tube.totals()[4]), 0, th * 3);
        if (true) {
            final String[] c = new String[1];
            d.invoke(true, a -> { c[0] = s.check(a); return true; });
            check += "\nZylinder: " + c[0];
        }
        s.mode = 2;
        s.wind = 14;
        s.advance(120);
        d.display();
        g.drawImage(grab(d, tw, th), tw, th * 3, null);
        label(g, "Pfosten 10 cm im Wind 14 m/s: vorn nass, der Wind schiebt die Tropfen um die Flanken", tw, th * 3);
        s.mode = 1;
        s.rate = 0.6;
        s.wind = 16;
        s.saltRate = 0.012;
        s.setup();
        s.tube.reset();
        s.advance(240);
        d.display();
        g.drawImage(grab(d, tw, th), tw * 2, th * 3, null);
        label(g, String.format(Locale.GERMAN, "Handlauf im Sprühnebel: Salz %.1f g/m², der Niesel löst es nur dort, wo er trifft", s.tube.saltMean() * Wetglass.SALT_FULL), tw * 2, th * 3);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println(check);
        System.out.println("Bild: " + out.getPath());
        d.destroy();
        System.exit(0);
    }

    static BufferedImage grab(GLOffscreenAutoDrawable d, int w, int h) {
        final BufferedImage[] g = new BufferedImage[1];
        d.invoke(true, a -> { g[0] = read(a, w, h); return true; });
        return g[0];
    }

    static void label(Graphics2D g, String t, int x, int y) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(x + 6, y + 6, g.getFontMetrics().stringWidth(t) + 12, 22);
        g.setColor(Color.WHITE);
        g.drawString(t, x + 12, y + 22);
    }

    static BufferedImage read(GLAutoDrawable a, int w, int h) {
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
        WetglassScene scene = new WetglassScene();
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(scene);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { wipe(e); }
            @Override public void mouseDragged(MouseEvent e) { wipe(e); }
            void wipe(MouseEvent e) {
                double[] p = scene.paneAt(e.getX(), e.getY(), canvas.getWidth(), canvas.getHeight());
                scene.wipeAt = new double[]{p[0], p[1], Math.max(0.01, scene.half * 0.12)};
            }
        };
        canvas.addMouseListener(m);
        canvas.addMouseMotionListener(m);

        Color bg = new Color(11, 17, 24), ink = new Color(230, 234, 238), muted = new Color(150, 165, 178);
        JPanel side = new JPanel();
        side.setBackground(bg);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel title = new JLabel("com.dan.wetglass");
        title.setForeground(new Color(226, 67, 47));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel state = label(" ", ink), check = label(" ", muted), rateLbl = label("", ink), windLbl = label("", ink), tiltLbl = label("", ink), zoomLbl = label("", ink);
        FSlider rate = new FSlider(0, 100, 62), wind = new FSlider(0, 30, 8), tilt = new FSlider(0, 90, 90), zoom = new FSlider(2, 100, 40);
        FComboBox focus = new FComboBox(new Object[]{"Auge auf der Scheibe", "Auge in der Ferne"});
        FComboBox lapse = new FComboBox(new Object[]{"Echtzeit", "Zeitlupe 1/4", "Zeitlupe 1/10", "Steht", "Zeitraffer 5×"});
        FComboBox quality = new FComboBox(new Object[]{"Niedrig", "Mittel", "Hoch", "Ultra"});
        quality.setSelectedIndex(1);
        rate.addChangeListener(e -> scene.rate = rate.getValue() == 0 ? 0 : 0.1 * Math.pow(1000, (rate.getValue() - 1) / 99.0));
        scene.rate = 0.1 * Math.pow(1000, (rate.getValue() - 1) / 99.0);
        wind.addChangeListener(e -> scene.wind = wind.getValue());
        tilt.addChangeListener(e -> scene.tiltDeg = tilt.getValue());
        zoom.addChangeListener(e -> scene.half = zoom.getValue() / 200.0);
        scene.half = zoom.getValue() / 200.0;
        focus.addActionListener(e -> scene.focusGlass = focus.getSelectedIndex() == 0);
        lapse.addActionListener(e -> scene.lapse = switch (lapse.getSelectedIndex()) { case 1 -> 0.25; case 2 -> 0.1; case 3 -> 0; case 4 -> 5; default -> 1; });
        quality.addActionListener(e -> scene.glass.quality = Wetglass.Quality.values()[quality.getSelectedIndex()]);
        FButton wipeAll = new FButton("Alles wischen");
        wipeAll.addActionListener(e -> scene.glass.wipeAll());
        FButton dry = new FButton("Trocken");
        dry.addActionListener(e -> { scene.glass.reset(); scene.tube.reset(); });
        FComboBox shape = new FComboBox(new Object[]{"Scheibe", "Handlauf (liegender Zylinder)", "Pfosten (stehender Zylinder)"});
        shape.addActionListener(e -> scene.mode = shape.getSelectedIndex());
        JLabel fogLbl = label("", ink), saltLbl = label("", ink);
        FSlider fog = new FSlider(0, 100, 0), salt = new FSlider(0, 100, 0);
        fog.addChangeListener(e -> scene.condensation = fog.getValue() * 3e-10);
        salt.addChangeListener(e -> scene.saltRate = salt.getValue() * 2e-4);
        FButton flood = new FButton("Überspülen (Seewasser)");
        flood.addActionListener(e -> scene.active().flood(1.2, true));
        Component[] items = {title, state, label("Fläche", ink), shape, rateLbl, rate, windLbl, wind, tiltLbl, tilt, zoomLbl, zoom, label("Fokus", ink), focus,
                fogLbl, fog, saltLbl, salt, flood, wipeAll, dry,
                label("Zeit", ink), lapse, label("Stufe", ink), quality, label("Ziehen mit der Maus wischt (außen und innen).", muted), check};
        for (Component c : items) {
            if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(0);
            side.add(c);
            side.add(javax.swing.Box.createVerticalStrut(6));
        }
        side.setPreferredSize(new Dimension(320, 10));
        FFrame f = new FFrame("com.dan.wetglass – Tropfen auf der Scheibe");
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
            rateLbl.setText(String.format(Locale.GERMAN, "Regen %.1f mm/h", scene.rate));
            windLbl.setText(String.format(Locale.GERMAN, "Wind %d m/s gegen die Scheibe", wind.getValue()));
            tiltLbl.setText(String.format(Locale.GERMAN, "Neigung %d° (90° senkrecht)", tilt.getValue()));
            zoomLbl.setText(String.format(Locale.GERMAN, "Ausschnitt %.0f cm hoch", scene.half * 200));
            fogLbl.setText(String.format(Locale.GERMAN, "Beschlag innen: %.1f µg/cm²s", scene.condensation * 1e8));
            saltLbl.setText(String.format(Locale.GERMAN, "Sprühnebel: %.1f mg Salz/m²s", scene.saltRate * Wetglass.SALT_FULL * 1000));
            state.setText("<html><body style='width:280px'>" + scene.status.replace(" · ", "<br>") + "</body></html>");
            if (scene.selfCheck.isEmpty() && scene.active().count() > 200) canvas.invoke(false, a -> { scene.check(a); return true; });
            check.setText("<html><body style='width:280px'>" + scene.selfCheck + "</body></html>");
        }).start();
    }

    private static JLabel label(String t, Color c) {
        JLabel l = new JLabel(t);
        l.setForeground(c);
        return l;
    }
}
