package com.dan.cloth.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.cloth.Cloth;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Vorführung von {@code cloth} auf dem Prozessor (Paketvertrag Punkt 3 und 10, seit Timmendorf Phase 1, XPBD seit Phase 6): eine Reihe Strandflaggen im Wind, von der Seite gesehen. Nur sie
 * darf FStyle nutzen. Ohne Fenster schreibt {@code ClothDemo --bild datei.png} ein Standbild (für Statusbilder).
 */
public final class ClothDemo {
    private ClothDemo() { }

    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(120, 136, 150), ACC = new Color(238, 240, 234);

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            State s = new State(8);
            for (int i = 0; i < 240; i++) s.step(1 / 60.0);
            BufferedImage im = new BufferedImage(720, 460, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            paint(g, s, 720, 460);
            g.dispose();
            javax.imageio.ImageIO.write(im, "png", new File(args[1]));
            System.out.println("Bild: " + args[1]);
            return;
        }
        SwingUtilities.invokeLater(ClothDemo::open);
    }

    /** Zustand der Vorführung, mit dem Regler {@code knob}. */
    static final class State {
        double knob, t;
        Cloth[] flags = new Cloth[4];
        State(double knob) { this.knob = knob; reset(); }
        void reset() {
            for (int k = 0; k < flags.length; k++) {
                Cloth c = Cloth.beachFlag(k * 3.0, 0, 0);
                final double gust = 0.25 * k;
                c.wind = (x, y, z, tt, o) -> { o[0] = knob * (1 + 0.3 * Math.sin(tt * 1.7 + gust)); o[1] = 0; o[2] = 0.6 * Math.sin(tt * 2.3 + gust); };
                flags[k] = c;
            }
        }
        void step(double dt) {
            t += dt;
            for (Cloth c : flags) c.step(dt);
        }
    }

    static void paint(Graphics2D g, State s, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setStroke(new BasicStroke(1.4f));
        double sc = Math.min(w / 14.0, h / 9.0), ox = w * 0.12, oy = h * 0.85;
        for (Cloth c : s.flags) {
            double[] base = c.point(0, 0);
            g.setColor(DIM);
            g.drawLine((int) (ox + base[0] * sc), (int) oy, (int) (ox + base[0] * sc), (int) (oy - (base[1] + 0.2) * sc));
            g.setColor(ACC);
            for (int j = 0; j < c.nv; j++)
                for (int i = 0; i < c.nu; i++) {
                    double[] p = c.point(i, j);
                    if (i + 1 < c.nu) { double[] q = c.point(i + 1, j); g.drawLine((int) (ox + p[0] * sc), (int) (oy - p[1] * sc), (int) (ox + q[0] * sc), (int) (oy - q[1] * sc)); }
                    if (j + 1 < c.nv) { double[] q = c.point(i, j + 1); g.drawLine((int) (ox + p[0] * sc), (int) (oy - p[1] * sc), (int) (ox + q[0] * sc), (int) (oy - q[1] * sc)); }
                }
        }
        g.setColor(INK);
        g.drawString(String.format("Wind %.0f m/s · XPBD, Gauß-Seidel in zwölf Farben, Flattern nach Strouhal", s.knob), 12, 20);
        g.setColor(DIM);
        g.drawString("com.dan.cloth · seit Timmendorf Phase 6 (mit JOGL: ClothGlDemo)", 12, h - 12);
    }

    private static void open() {
        State s = new State(8);
        FFrame f = new FFrame("Tuch · Demo");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g) { ClothDemo.paint((Graphics2D) g, s, getWidth(), getHeight()); }
        };
        view.setPreferredSize(new Dimension(720, 460));
        FSlider knob = new FSlider(0, 20, (int) Math.round(8));
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
