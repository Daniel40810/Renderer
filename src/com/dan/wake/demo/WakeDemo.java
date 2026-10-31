package com.dan.wake.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.wake.Wake;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Vorführung von {@code wake} auf dem Prozessor (Paketvertrag Punkt 3 und 10; Hülle seit Timmendorf Phase 1, Bild seit
 * Phase 8): von oben das Kielwasser zweier Quellen aus {@link Wake#tile}, wie es {@code gl/} rechnet, schattiert nach
 * der Neigung, Schaum weiß. Links ein Boot, das alle 40 s um 80° wendet (Länge und Fahrt am Regler), rechts eine Fähre
 * mit 18 kn und ihr Schwell über 1,6 km. Nur die Demo nutzt FStyle.
 *
 * <pre>
 * Fenster: WakeDemo
 * Bild:    WakeDemo --bild datei.png
 * </pre>
 */
public final class WakeDemo {
    private WakeDemo() { }

    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(150, 166, 180);

    /** Zustand: ein wendendes Boot und eine Fähre. */
    static final class State {
        double length = 6.2, speed = 3, t, heading = 60, turnLeft;
        final Wake boatWake = new Wake(), ferryWake = new Wake();
        Wake.Source boat, ferry;
        double bx, bz;

        State() {
            boatWake.depth = 100;
            ferryWake.depth = 25;
            ferry = ferryWake.add("Fähre", 0, 0, 30, 9.3, 200, 30, 6.5, true);
            reset();
        }

        void reset() {
            boatWake.clear();
            boat = boatWake.add("Boot", 0, 0, heading, speed, length, Math.max(0.6, length / 3), Math.max(0.12, 0.05 * length), false);
            boat.autopilot = false;
            bx = bz = 0;
        }

        void step(double dt) {
            t += dt;
            if (t % 40 < dt) turnLeft = 80;
            if (turnLeft > 0) { double d = Math.min(turnLeft, 12 * dt); heading -= d; turnLeft -= d; }
            bx += Math.sin(Math.toRadians(heading)) * speed * dt;
            bz -= Math.cos(Math.toRadians(heading)) * speed * dt;
            boatWake.moveTo(boat, bx, bz, heading, speed);
            boatWake.step(dt);
            ferryWake.step(dt);
        }
    }

    public static void main(String[] args) throws Exception {
        State s = new State();
        if (args.length > 1 && args[0].equals("--bild")) {
            for (int i = 0; i < 1500; i++) s.step(1 / 15.0);
            for (int i = 0; i < 90 * 15; i++) s.ferryWake.step(1 / 15.0);
            BufferedImage im = new BufferedImage(1280, 660, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            paint(g, s, 1280, 660);
            g.dispose();
            javax.imageio.ImageIO.write(im, "png", new File(args[1]));
            System.out.println("Bild: " + args[1]);
            return;
        }
        SwingUtilities.invokeLater(() -> open(s));
    }

    static void paint(Graphics2D g, State s, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        int size = Math.min(w / 2 - 30, h - 90);
        double[] b = s.boat.bow();
        double span = Math.max(60, s.length * 16);
        field(g, s.boatWake, b[0] - span / 2, b[1] - span / 2, span, 20, 50, size, 0.6 * Math.max(0.01, Wake.targetAmplitude(s.boat)));
        double[] f = s.ferry.bow();
        field(g, s.ferryWake, f[0] - 1500, f[1] + 100 - 800, 1600, w / 2 + 10, 50, size, 0.25);
        g.setColor(INK);
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.drawString(String.format(Locale.GERMAN, "Boot %.1f m, %.1f kn, Fr %.2f · wendet alle 40 s · %.0f × %.0f m", s.length, s.speed / 0.5144, s.boat.froude(), span, span), 20, 30);
        g.drawString(String.format(Locale.GERMAN, "Fähre 200 m, 18 kn, Fr %.2f · %.1f km × %.1f km", s.ferry.froude(), 1.6, 1.6), w / 2 + 10, 30);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(DIM);
        g.drawString(String.format(Locale.GERMAN, "Keil nach Kelvin ±%.2f°, nach Rabaud und Moisy bei Fr %.2f ±%.1f° · Querwellen λ = 2πV²/g = %.1f m", Wake.KELVIN_DEG,
                s.boat.froude(), Wake.wakeAngleDeg(s.boat.froude()), Wake.transverseWavelength(s.speed)), 20, h - 14);
        g.drawString(String.format(Locale.GERMAN, "Schaum der Schraube vergeht in rund 2,5 min · Wellen in einer Länge Abstand bis %.2f m", Wake.targetAmplitude(s.ferry)), w / 2 + 10, h - 14);
    }

    /** Ein Feld von oben: Kachel aus Wake.tile, schattiert nach der Neigung in Blickrichtung, Schaum weiß. */
    static void field(Graphics2D g, Wake wk, double x0, double z0, double span, int px, int py, int size, double scale) {
        int n = Math.min(320, size);
        float[] t = new float[2 * n * n];
        wk.tile(x0, z0, span, n, t);
        BufferedImage im = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        double cell = span / n;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double hh = t[2 * (j * n + i)], dx = (i + 1 < n ? t[2 * (j * n + i + 1)] : hh) - hh, dz = (j + 1 < n ? t[2 * ((j + 1) * n + i)] : hh) - hh;
                double slope = (dx * 0.8 + dz * 0.6) / cell;
                double shade = Math.max(-1, Math.min(1, slope / (scale * 0.8 / Math.max(1, span / 200))));
                double foam = t[2 * (j * n + i) + 1];
                double r = 0.10 + 0.08 * shade, gg = 0.24 + 0.10 * shade, bb = 0.33 + 0.12 * shade;
                r = r + (0.92 - r) * foam;
                gg = gg + (0.95 - gg) * foam;
                bb = bb + (0.97 - bb) * foam;
                im.setRGB(i, j, (c(r) << 16) | (c(gg) << 8) | c(bb));
            }
        g.drawImage(im, px, py, size, size, null);
    }

    static int c(double v) { return (int) Math.max(0, Math.min(255, v * 255)); }

    private static void open(State s) {
        FFrame f = new FFrame("Kielwasser (com.dan.wake)");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g) { WakeDemo.paint((Graphics2D) g, s, getWidth(), getHeight()); }
        };
        view.setPreferredSize(new Dimension(1280, 660));
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(BG);
        side.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JLabel l1 = new JLabel("Länge: 6,2 m"), l2 = new JLabel("Fahrt: 5,8 kn");
        l1.setForeground(DIM);
        l2.setForeground(DIM);
        FSlider len = new FSlider(2, 30, 6), sp = new FSlider(1, 20, 6);
        len.addChangeListener(e -> { s.length = len.getValue(); l1.setText("Länge: " + len.getValue() + " m"); s.reset(); });
        sp.addChangeListener(e -> { s.speed = sp.getValue() * 0.5144; l2.setText("Fahrt: " + sp.getValue() + " kn"); });
        side.add(l1);
        side.add(len);
        side.add(l2);
        side.add(sp);
        root.add(view, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        f.setSize(1500, 740);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Timer(66, e -> { s.step(1 / 15.0); view.repaint(); }).start();
    }
}
