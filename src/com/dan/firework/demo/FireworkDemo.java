package com.dan.firework.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.firework.Firework;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Vorführung von {@code firework} (Paketvertrag Punkt 3 und 10): seit Timmendorf Phase 11 der Zündplan der Vorführung
 * über Wasser als Bild aus {@link FireworkPicture} (Sterne, Schweife, Rauch, Spiegelung), der Regler stellt den Wind.
 * Nur sie darf FStyle nutzen. Ohne Fenster schreibt {@code FireworkDemo --bild datei.png} ein Standbild mit vier Feldern.
 */
public final class FireworkDemo {
    private FireworkDemo() { }

    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(120, 136, 150), ACC = new Color(240, 160, 75);

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            BufferedImage im = bild(1280, 720);
            javax.imageio.ImageIO.write(im, "png", new File(args[1]));
            System.out.println("Bild: " + args[1]);
            return;
        }
        SwingUtilities.invokeLater(FireworkDemo::open);
    }

    /**
     * Seit Phase 11 das Standbild: vier Felder – der Zündplan der Vorführung nach 13 s, den Goldregen nach 37 s, eine
     * Langzeitbelichtung von 4 s nach 19 s, die Farben der Salze aus ihrem Spektrum.
     */
    static BufferedImage bild(int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int pw = w / 2, ph = h / 2;
        double[] times = {13, 37, 19};
        String[] title = {"Vorführung nach 13 s · Wind 3 m/s · aus 600 m", "Goldregen nach 37 s", "Langzeitbelichtung 4 s nach 19 s"};
        for (int k = 0; k < 3; k++) {
            Firework f = Firework.demo(0, 0);
            f.trailSeconds = k == 2 ? 4 : 1;
            f.wind = (x, y, z, t, o) -> { o[0] = 3; o[1] = 0; o[2] = 0; };
            for (double t = 0; t < times[k]; t += 1 / 60.0) f.step(1 / 60.0);
            FireworkPicture p = new FireworkPicture(pw, ph);
            p.longExposure = k == 2 ? 4 : 0;
            p.exposure = k == 2 ? 0.006 : 0.004;
            g.drawImage(p.render(f), (k % 2) * pw, (k / 2) * ph, null);
            g.setColor(INK);
            g.drawString(String.format(java.util.Locale.GERMAN, "%s · %,d Sterne · %,d Rauchwolken", title[k], f.stars().size(), f.smoke().size()), (k % 2) * pw + 10, (k / 2) * ph + 18);
        }
        // Farben der Salze aus dem Spektrum
        g.setColor(BG);
        g.fillRect(pw, ph, pw, ph);
        g.setColor(INK);
        g.drawString("Farben aus dem Spektrum (CIE 1931 → lineares sRGB, Leuchtdichte 1)", pw + 10, ph + 18);
        Firework.Salt[] all = Firework.Salt.values();
        for (int i = 0; i < all.length; i++) {
            double[] c = all[i].rgb(), xy = com.dan.firework.Spectrum.chromaticity(all[i]);
            int x = pw + 14 + (i % 4) * (pw / 4), y = ph + 40 + (i / 4) * (ph / 2 - 20);
            double m = Math.max(c[0], Math.max(c[1], c[2]));
            g.setColor(new Color(FireworkPicture.tone(c[0] / m * 0.9), FireworkPicture.tone(c[1] / m * 0.9), FireworkPicture.tone(c[2] / m * 0.9)));
            g.fillOval(x, y, 70, 70);
            g.setColor(INK);
            g.drawString(all[i].name().charAt(0) + all[i].name().substring(1).toLowerCase(), x, y + 88);
            g.setColor(DIM);
            g.drawString(String.format(java.util.Locale.GERMAN, "x %.3f y %.3f", xy[0], xy[1]), x, y + 104);
        }
        g.setColor(DIM);
        g.drawString("com.dan.firework · Timmendorf Phase 11", 10, h - 10);
        g.dispose();
        return im;
    }

    /** Zustand der Vorführung, mit dem Regler {@code knob}. */
    static final class State {
        double knob, t;
        Firework fw;
        State(double knob) { this.knob = knob; reset(); }
        void reset() {
            fw = Firework.demo(0, 0);
            t = 0;
            fw.wind = (x, y, z, tt, o) -> { o[0] = knob; o[1] = 0; o[2] = 0; };
        }
        void step(double dt) {
            t += dt;
            fw.step(dt);
            if (t > 36) reset();
        }
    }

    private static void open() {
        State s = new State(3);
        FFrame f = new FFrame("Feuerwerk · Demo");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        FireworkPicture pic = new FireworkPicture(800, 450);
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.drawImage(pic.render(s.fw), 0, 0, getWidth(), getHeight(), null);
                g.setColor(INK);
                g.drawString(String.format(java.util.Locale.GERMAN, "Zündplan der Vorführung · Wind %.0f m/s · %s", s.knob, s.fw.describe()), 12, 20);
            }
        };
        view.setPreferredSize(new Dimension(800, 450));
        FSlider knob = new FSlider(0, 15, (int) Math.round(3));
        JLabel lbl = new JLabel("Wind m/s " + knob.getValue());
        lbl.setForeground(INK);
        knob.addChangeListener(e -> { s.knob = knob.getValue(); s.reset(); lbl.setText("Wind m/s " + knob.getValue()); });
        JPanel south = new JPanel(new BorderLayout(10, 0));
        south.setBackground(BG);
        south.add(lbl, BorderLayout.WEST);
        south.add(knob, BorderLayout.CENTER);
        root.add(view, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(780, 560);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Timer(16, e -> { s.step(1 / 60.0); view.repaint(); }).start();
    }
}
