package com.dan.beacon.demo;

import com.dan.beacon.Beacon;
import com.dan.beacon.Characteristic;
import com.dan.beacon.Lens;
import com.dan.beacon.LightColour;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.ftextfield.FTextfield;
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
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Vorführung des Packages ohne Leuchtturm-App (Paketvertrag Punkt 3 und 10): ein Turm auf See bei Nacht, das Feuer mit
 * umschaltbarer Kennung (Textfeld mit Rückmeldung des Parsers, Voreinstellungen), Regen, Sicht, Blick von außen oder
 * aus dem Laternenraum. Ziehen dreht, das Rad zoomt.
 * Ohne Fenster: {@code BeaconDemo --bild datei.png [breite]} rechnet Nacht klar gegen Nacht mit Regen, die Sektoren
 * des Roten Sands, die Sturmnacht und die Lichtflecken im Laternenraum, dazu den Schnitt durch die Linse mit verfolgten
 * Strahlen und die Kennungen als Lichtkurven. Dabei vergleicht es Grafikkarte und Prozessor.
 */
public final class BeaconDemo {
    private BeaconDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1800);
            return;
        }
        SwingUtilities.invokeLater(BeaconDemo::open);
    }

    static void bild(File out, int width) throws Exception {
        int w2 = width / 2, h2 = w2 * 9 / 16, w3 = width / 3, h3 = w3 * 3 / 4, h4 = w2 * 11 / 20;
        BufferedImage img = new BufferedImage(width, h2 + h3 + h4, BufferedImage.TYPE_INT_RGB);
        Graphics2D gg = img.createGraphics();
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w2, h2);
        BeaconScene s = new BeaconScene();
        s.frozen = true;
        d.addGLEventListener(s);
        d.display();
        // 1 Fl(3) W 15s: klar gegen Regen, gleicher Blick, gleicher Augenblick (ein Strahl streicht nah am Auge vorbei)
        String check = "";
        for (int k = 0; k < 2; k++) {
            s.beacon.copyLight(Beacon.flash3());
            s.beacon.snapOn(true);
            s.rain = k == 0 ? 0 : 5;
            s.visibilityKm = k == 0 ? 30 : 15;
            s.yaw = 205;
            s.pitch = 3;
            s.dist = 380;
            s.targetY = 22;
            s.beacon.setTime(0, 205 + 26);
            s.advance(0);
            d.display();
            if (k == 1) {
                final String[] c = new String[1];
                d.invoke(true, a -> { c[0] = s.check(a); return true; });
                check = c[0];
            }
            gg.drawImage(grab(d, w2, h2), k * w2, 0, null);
            label(gg, k == 0 ? "Fl(3) W 15s · klare Nacht, Sicht 30 km" : "dieselbe Nacht mit Regen 5 mm/h, Sicht 3,8 km", k * w2, 0);
        }
        // 2 Roter Sand im Regen an der Grenze Weiß/Rot · Sturmnacht im Wolkenbruch · Laternenraum
        d.setSurfaceSize(w3, h3);
        s.beacon.copyLight(Beacon.roterSand1985());
        s.beacon.snapOn(true);
        s.rain = 3;
        s.visibilityKm = 12;
        // Peilung 185° (Grenze Weiß/Rot): Beobachter im Azimut 5°
        s.yaw = 5;
        s.pitch = 2;
        s.dist = 900;
        s.targetY = 24;
        s.advance(0);
        d.display();
        gg.drawImage(grab(d, w3, h3), 0, h2, null);
        label(gg, "Roter Sand 1985 im Regen, Peilung 185°: Weiß | Rot", 0, h2);
        s.beacon.copyLight(Beacon.stormNight());
        s.beacon.snapOn(true);
        s.rain = 30;
        s.visibilityKm = 8;
        s.yaw = 150;
        s.pitch = 4;
        s.dist = 260;
        s.targetY = 20;
        s.beacon.setTime(0, 150 + 34);
        s.pre = 25;
        s.advance(0);
        d.display();
        gg.drawImage(grab(d, w3, h3), w3, h2, null);
        label(gg, "Sturmnacht Fl W 5s · Wolkenbruch 30 mm/h", w3, h2);
        s.beacon.copyLight(Beacon.stormNight());
        s.beacon.snapOn(true);
        s.pre = 0.12;
        s.rain = 0;
        s.inside = true;
        s.insideAz = 250;
        s.beacon.setTime(0, 40);
        s.advance(0);
        d.display();
        gg.drawImage(grab(d, w3, h3), 2 * w3, h2, null);
        label(gg, "Laternenraum: Lichtflecken der Drehlinse", 2 * w3, h2);
        s.inside = false;
        // 3 Schnitt durch die Linse und Lichtkurven
        lensPlot(gg, s.beacon, 0, h2 + h3, w2, h4);
        curves(gg, w2, h2 + h3, width - w2, h4);
        gg.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println(check);
        System.out.println(s.beacon.describe());
        System.out.println("Bild: " + out.getPath());
        d.destroy();
        System.exit(0);
    }

    /** Der Schnitt durch die Trommellinse: Prismen, Sockel und 60 verfolgte Strahlen aus der Lampe. */
    static void lensPlot(Graphics2D g, Beacon b, int x0, int y0, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(14, 20, 28));
        g.fillRect(x0, y0, w, h);
        Lens lens = b.lens();
        double sc = h * 0.85 / 1.2, cx = x0 + w * 0.12, cy = y0 + h * 0.5;
        // Strahlen
        java.util.Random rnd = new java.util.Random(3);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < 70; i++) {
            double phi = Math.toRadians(-62 + 124.0 * i / 69), off = (rnd.nextDouble() * 2 - 1) * 0.004;
            List<double[]> path = Lens.path(lens, new double[]{-Math.sin(phi) * off, Math.cos(phi) * off}, new double[]{Math.cos(phi), Math.sin(phi)}, 1.9);
            Path2D p = new Path2D.Double();
            for (int k = 0; k < path.size(); k++) {
                double px = cx + path.get(k)[0] * sc, py = cy - path.get(k)[1] * sc;
                if (k == 0) p.moveTo(px, py); else p.lineTo(px, py);
            }
            boolean through = path.size() > 3;
            g.setColor(through ? new Color(255, 214, 140, 150) : new Color(150, 160, 175, 90));
            g.draw(p);
        }
        // Prismen
        for (double[] q : lens.prisms()) {
            Path2D p = new Path2D.Double();
            for (int k = 0; k < q.length / 2; k++) {
                double px = cx + q[2 * k] * sc, py = cy - q[2 * k + 1] * sc;
                if (k == 0) p.moveTo(px, py); else p.lineTo(px, py);
            }
            p.closePath();
            g.setColor(new Color(120, 200, 210, 90));
            g.fill(p);
            g.setColor(new Color(180, 230, 235));
            g.draw(p);
        }
        g.setColor(new Color(217, 180, 90));
        g.fillOval((int) (cx - 0.016 * sc), (int) (cy - 0.016 * sc), (int) Math.max(3, 0.032 * sc), (int) Math.max(3, 0.032 * sc));
        Lens.Field f = b.field();
        label(g, String.format(Locale.GERMAN, "Gürtellinse: %d Prismenringe, f = %.2f m, ±%.0f°, Strahl %.1f° breit (Lampe %.0f mm), %.0f %% gebündelt",
                lens.rings, lens.focal, lens.acceptanceDeg(), f.fwhmDeg(), f.lampRadius() * 2000, f.collimated() * 100), x0, y0);
    }

    /** Lichtkurven der Voreinstellungen über 30 s. */
    static void curves(Graphics2D g, int x0, int y0, int w, int h) {
        g.setColor(new Color(18, 24, 32));
        g.fillRect(x0, y0, w, h);
        String[] texts = {"F WRG", "Fl(3) W 15s", "Fl W 5s", "Oc(3) WRG 12s", "Q(6)+LFl W 15s", "Mo(U) W 15s", "Al.Fl WR 5s"};
        String[] names = {"Roter Sand 1985", "Fl(3) W 15s", "Sturmnacht", "Tegeler Plate", "Kardinal Süd", "Morse U", "Wechselfeuer"};
        int rows = texts.length, top = 38, left = 150, rh = (h - top - 26) / rows;
        double span = 30;
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int r = 0; r < rows; r++) {
            Characteristic c = Characteristic.tryParse(texts[r]);
            int yb = y0 + top + r * rh + rh - 4, hh = rh - 10;
            g.setColor(new Color(185, 200, 210));
            g.drawString(names[r] + "  " + texts[r], x0 + 8, yb - hh / 2 + 4);
            for (int i = 0; i < w - left - 12; i++) {
                double t = span * i / (w - left - 12);
                double lv = c.level(t);
                LightColour lc = c.colourAt(t);
                double[] rgb = lc.rgb();
                double m = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
                Color col = new Color((int) (255 * Math.pow(rgb[0] / m, 1 / 2.2)), (int) (255 * Math.pow(rgb[1] / m, 1 / 2.2)), (int) (255 * Math.pow(rgb[2] / m, 1 / 2.2)));
                int hgt = (int) Math.round(lv * hh);
                if (hgt > 0) {
                    g.setColor(col);
                    g.fillRect(x0 + left + i, yb - hgt, 1, hgt);
                }
            }
            g.setColor(new Color(60, 72, 86));
            g.drawLine(x0 + left, yb, x0 + w - 12, yb);
        }
        g.setColor(new Color(150, 165, 178));
        for (int s = 0; s <= 30; s += 5) {
            int x = x0 + left + (int) ((w - left - 12) * s / span);
            g.drawString(s + " s", x - 8, y0 + h - 8);
        }
        label(g, "Kennungen als Lichtkurven (Parser, Standardtakt)", x0, y0);
    }

    static BufferedImage grab(GLOffscreenAutoDrawable d, int w, int h) {
        final BufferedImage[] g = new BufferedImage[1];
        d.invoke(true, a -> { g[0] = read(a, w, h); return true; });
        return g[0];
    }

    static void label(Graphics2D g, String t, int x, int y) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(new Color(0, 0, 0, 130));
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

    // ------------------------------------------------------------------ Fenster

    private static void open() {
        BeaconScene scene = new BeaconScene();
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(scene);
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                if (scene.inside) scene.insideAz += (e.getX() - lx) * 0.3;
                else {
                    scene.yaw += (e.getX() - lx) * 0.3;
                    scene.pitch = Math.max(-2, Math.min(80, scene.pitch + (e.getY() - ly) * 0.2));
                }
                lx = e.getX();
                ly = e.getY();
            }
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                scene.dist = Math.max(30, Math.min(8000, scene.dist * Math.pow(1.1, e.getPreciseWheelRotation())));
            }
        };
        canvas.addMouseListener(m);
        canvas.addMouseMotionListener(m);
        canvas.addMouseWheelListener(m);

        Color bg = new Color(11, 17, 24), ink = new Color(230, 234, 238), muted = new Color(150, 165, 178), red = new Color(226, 67, 47);
        JPanel side = new JPanel();
        side.setBackground(bg);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel title = new JLabel("com.dan.beacon");
        title.setForeground(red);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel state = label(" ", ink), feedback = label(" ", muted), rainLbl = label("", ink), visLbl = label("", ink), strLbl = label("", ink), check = label(" ", muted);
        FComboBox preset = new FComboBox(Beacon.PRESETS);
        preset.setSelectedIndex(2);
        FTextfield text = new FTextfield();
        text.setText(scene.beacon.characteristic().text);
        FSlider rain = new FSlider(0, 100, 0), vis = new FSlider(2, 60, 25), str = new FSlider(0, 300, 100);
        FCheckBox inside = new FCheckBox("Im Laternenraum"), turn = new FCheckBox("Linse dreht"), glare = new FCheckBox("Lichtpunkt");
        turn.setSelected(true);
        glare.setSelected(true);
        preset.addActionListener(e -> {
            scene.beacon.copyLight(Beacon.preset(preset.getSelectedIndex()));
            text.setText(scene.beacon.characteristic().text);
            feedback.setText(scene.beacon.characteristic().describe());
        });
        text.addActionListener(e -> {
            String err = scene.beacon.setCharacteristic(text.getText());
            feedback.setForeground(err == null ? muted : new Color(240, 170, 80));
            feedback.setText("<html><body style='width:230px'>" + (err == null ? scene.beacon.characteristic().describe() + "<br>" + scene.beacon.characteristic().timesText() : err) + "</body></html>");
        });
        rain.addChangeListener(e -> scene.rain = rain.getValue() == 0 ? 0 : 0.1 * Math.pow(1000, rain.getValue() / 100.0));
        vis.addChangeListener(e -> scene.visibilityKm = vis.getValue());
        str.addChangeListener(e -> scene.strength = str.getValue() / 100.0);
        inside.addActionListener(e -> scene.inside = inside.isSelected());
        turn.addActionListener(e -> scene.beacon.turning = turn.isSelected());
        glare.addActionListener(e -> scene.glare = glare.isSelected());
        for (Component c : new Component[]{title, state, label("Voreinstellung", muted), preset, label("Kennung (Enter übernimmt)", muted), text, feedback,
                rainLbl, rain, visLbl, vis, strLbl, str, inside, turn, glare, check}) {
            if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            side.add(c);
            side.add(javax.swing.Box.createVerticalStrut(6));
        }
        side.setPreferredSize(new Dimension(300, 700));
        new Timer(300, e -> {
            rainLbl.setText(String.format(Locale.GERMAN, "Regen %.1f mm/h", scene.rain));
            visLbl.setText(String.format(Locale.GERMAN, "Sicht ohne Regen %.0f km", scene.visibilityKm));
            strLbl.setText(String.format(Locale.GERMAN, "Strahl ×%.1f", scene.strength));
            state.setText("<html><body style='width:240px'>" + scene.status() + "</body></html>");
            if (!scene.check.isEmpty()) check.setText("<html><body style='width:240px'>" + scene.check + "</body></html>");
        }).start();
        // FFrame zeichnet Titel und Rahmen selbst: der Inhalt gehört in getComponentPane()
        FFrame f = new FFrame("com.dan.beacon – Leuchtfeuer");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.add(canvas, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        f.setPreferredFrameSize(new Dimension(1400, 820));
        f.setSize(1400, 820);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        Animator an = new Animator(canvas);
        an.start();
        new Timer(3000, e -> canvas.invoke(false, a -> { scene.check(a); return true; })) {{ setRepeats(false); }}.start();
    }

    static JLabel label(String t, Color c) {
        JLabel l = new JLabel(t);
        l.setForeground(c);
        l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        return l;
    }
}
