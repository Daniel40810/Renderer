package com.dan.route.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.route.Route;

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
 * Vorführung der Hülle von {@code route} (Paketvertrag Punkt 3 und 10, seit Timmendorf Phase 1): der kürzeste Weg über ein Gitter und der Umkreis von 5 min zu Fuß. Nur sie
 * darf FStyle nutzen. Ohne Fenster schreibt {@code RouteDemo --bild datei.png} ein Standbild (für Statusbilder).
 */
public final class RouteDemo {
    private RouteDemo() { }

    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(120, 136, 150), ACC = new Color(240, 200, 110);

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            State s = new State(7);
            for (int i = 0; i < 240; i++) s.step(1 / 60.0);
            BufferedImage im = new BufferedImage(720, 460, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            paint(g, s, 720, 460);
            g.dispose();
            javax.imageio.ImageIO.write(im, "png", new File(args[1]));
            System.out.println("Bild: " + args[1]);
            return;
        }
        SwingUtilities.invokeLater(RouteDemo::open);
    }

    /** Zustand der Vorführung, mit dem Regler {@code knob}. */
    static final class State {
        double knob, t;
        Route g;
        Route.Path path;
        java.util.Map<Integer, Double> reach;
        State(double knob) { this.knob = knob; reset(); }
        void reset() {
            g = RouteCheck.grid((int) knob);
            int n = (int) knob;
            path = g.shortest(0, n * n - 1, g.walking());
            reach = g.within(n * n / 2, g.walking(), 300);
        }
        void step(double dt) {
            t += dt;

        }
    }

    static void paint(Graphics2D g, State s, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setStroke(new BasicStroke(1.4f));
        int n = (int) s.knob;
        double sc = Math.min(w, h - 60) / (n * 100.0 + 100);
        int ox = 40, oy = 40;
        for (Route.Edge e : s.g.edges()) {
            Route.Node a = s.g.node(e.a()), b = s.g.node(e.b());
            g.setColor(DIM);
            g.drawLine((int) (ox + a.x() * sc), (int) (oy + a.z() * sc), (int) (ox + b.x() * sc), (int) (oy + b.z() * sc));
        }
        for (int i : s.reach.keySet()) {
            Route.Node a = s.g.node(i);
            g.setColor(new Color(127, 182, 198));
            g.fillOval((int) (ox + a.x() * sc) - 4, (int) (oy + a.z() * sc) - 4, 8, 8);
        }
        g.setColor(ACC);
        g.setStroke(new BasicStroke(3f));
        int[] p = s.path.nodes();
        for (int i = 0; i + 1 < p.length; i++) {
            Route.Node a = s.g.node(p[i]), b = s.g.node(p[i + 1]);
            g.drawLine((int) (ox + a.x() * sc), (int) (oy + a.z() * sc), (int) (ox + b.x() * sc), (int) (oy + b.z() * sc));
        }
        g.setColor(INK);
        g.drawString(String.format("Gitter %d × %d, 100 m · Weg %.0f m in %.0f s · in 5 min erreichbar: %d Knoten", n, n, s.path.length(), s.path.cost(), s.reach.size()), 12, 20);
        g.setColor(DIM);
        g.drawString("com.dan.route · Hülle seit Timmendorf Phase 1", 12, h - 12);
    }

    private static void open() {
        State s = new State(7);
        FFrame f = new FFrame("Wegenetz · Demo");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g) { RouteDemo.paint((Graphics2D) g, s, getWidth(), getHeight()); }
        };
        view.setPreferredSize(new Dimension(720, 460));
        FSlider knob = new FSlider(3, 14, (int) Math.round(7));
        JLabel lbl = new JLabel("Gitter " + knob.getValue());
        lbl.setForeground(INK);
        knob.addChangeListener(e -> { s.knob = knob.getValue(); s.reset(); lbl.setText("Gitter " + knob.getValue()); });
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
