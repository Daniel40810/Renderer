package com.dan.herd.demo;

import com.dan.fframe.FFrame;
import com.dan.herd.AnimalMesh;
import com.dan.herd.Herd;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Vorführung der Haustiere von oben: Hühnerhof, Ziegenweide mit Tümpel, Schweinepferch und der Hund am Steg (der Hof
 * aus {@code HerdCheck}). Die Maus ist ein Mensch, der stört: Hühner und Ziegen laufen weg, der Hund kommt und setzt
 * sich. Rechtsklick wechselt Tag und Nacht.
 * <pre>
 * HerdDemo                      Fenster
 * HerdDemo --bild datei.png     der Hof nach fünf Minuten als Bild (1400 × 900)
 * </pre>
 */
public final class HerdDemo {
    private HerdDemo() { }

    static final Color BG = new Color(30, 38, 24), INK = new Color(230, 234, 226), MUTED = new Color(150, 160, 140);

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("--bild")) {
            Herd h = HerdCheck.farm();
            h.step(0.01);
            for (int i = 0; i < 3000; i++) h.step(0.1);
            BufferedImage img = new BufferedImage(1400, 900, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            draw(g, 1400, 900, h, null);
            g.dispose();
            File f = new File(a[1]);
            if (f.getAbsoluteFile().getParentFile() != null) f.getAbsoluteFile().getParentFile().mkdirs();
            ImageIO.write(img, "png", f);
            System.out.println("Bild: " + f.getPath());
            return;
        }
        SwingUtilities.invokeLater(HerdDemo::window);
    }

    static void window() {
        Herd h = HerdCheck.farm();
        double[] mouse = {1e9, 1.7, 1e9};
        h.disturbers.add(mouse);
        JComponent view = new JComponent() {
            @Override protected void paintComponent(Graphics g0) { draw((Graphics2D) g0, getWidth(), getHeight(), h, mouse); }
        };
        view.setPreferredSize(new Dimension(1300, 820));
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                double[] w = toWorld(e.getX(), e.getY(), view.getWidth(), view.getHeight());
                mouse[0] = w[0]; mouse[2] = w[1];
            }
            @Override public void mouseExited(MouseEvent e) { mouse[0] = mouse[2] = 1e9; }
            @Override public void mousePressed(MouseEvent e) { if (SwingUtilities.isRightMouseButton(e)) h.sunAltDeg = h.sunAltDeg > 0 ? -10 : 30; }
        };
        view.addMouseListener(ma);
        view.addMouseMotionListener(ma);
        long[] last = {System.nanoTime()};
        new Timer(30, e -> {
            long now = System.nanoTime();
            h.step(Math.min(0.1, (now - last[0]) / 1e9));
            last[0] = now;
            view.repaint();
        }).start();
        FFrame fr = new FFrame("com.dan.herd – Haustiere");
        JPanel root = fr.getComponentPane();
        root.setLayout(new BorderLayout());
        root.add(view, BorderLayout.CENTER);
        fr.setPreferredFrameSize(new Dimension(1400, 900));
        fr.setSize(1400, 900);
        fr.setLocationRelativeTo(null);
        fr.setVisible(true);
    }

    static final double CX = 0, CZ = 5, SPAN = 120;

    static double[] toWorld(int x, int y, int w, int h) {
        double s = Math.min(w, h - 60) / SPAN;
        return new double[]{CX + (x - w / 2.0) / s, CZ + (y - 30 - (h - 60) / 2.0) / s};
    }

    /** Der Hof von oben. */
    static void draw(Graphics2D g, int w, int h, Herd herd, double[] mouse) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(herd.night() ? new Color(10, 14, 20) : BG);
        g.fillRect(0, 0, w, h);
        double s = Math.min(w, h - 60) / SPAN;
        double ox = w / 2.0 - CX * s, oz = 30 + (h - 60) / 2.0 - CZ * s;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.setColor(INK);
        g.drawString("com.dan.herd · " + herd.describe() + (herd.night() ? " · Nacht" : " · Tag") + " · Maus: ein Mensch, Rechtsklick: Tag/Nacht", 14, 22);
        // Gehege und Tümpel
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{6, 5}, 0));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (Herd.Pen p : herd.pens) {
            g.setColor(MUTED);
            g.draw(new Ellipse2D.Double(ox + (p.x() - p.r()) * s, oz + (p.z() - p.r()) * s, 2 * p.r() * s, 2 * p.r() * s));
            g.drawString(p.name(), (float) (ox + p.x() * s - 25), (float) (oz + (p.z() - p.r()) * s - 6));
            for (double[] wp : p.waypoints()) g.fill(new Ellipse2D.Double(ox + wp[0] * s - 3, oz + wp[1] * s - 3, 6, 6));
        }
        g.setStroke(new BasicStroke(1));
        g.setColor(new Color(40, 70, 90));
        g.fill(new Ellipse2D.Double(ox + 36 * s, oz - 4 * s, 8 * s, 8 * s));
        // Tiere als Körper mit Kopf, gedreht nach ihrer Richtung
        for (Herd.Animal a : herd.animals()) {
            if (a.state == Herd.State.AWAY) continue;
            double[] c = AnimalMesh.coat(a.kind, a.tone);
            Color col = new Color(cl(c[0] * 2.2), cl(c[1] * 2.2), cl(c[2] * 2.2));
            AffineTransform at = g.getTransform();
            g.translate(ox + a.pos[0] * s, oz + a.pos[2] * s);
            g.rotate(Math.toRadians(a.heading));
            double len = a.kind.length * a.size * s, wid = len * (a.kind == Herd.Kind.HUHN ? 0.6 : 0.38);
            g.setColor(col);
            g.fill(new Ellipse2D.Double(-wid / 2, -len / 2, wid, len));
            double hr = wid * 0.5, hy = -len / 2 - hr * (a.head > 0.5 ? 0.2 : 0.6);
            g.fill(new Ellipse2D.Double(-hr / 2, hy - hr / 2, hr, hr));
            if (a.state == Herd.State.RUN) { g.setColor(new Color(226, 120, 70)); g.draw(new Ellipse2D.Double(-wid / 2 - 2, -len / 2 - 2, wid + 4, len + 4)); }
            g.setTransform(at);
        }
        if (mouse != null && mouse[0] < 1e8) {
            g.setColor(new Color(226, 67, 47));
            g.fill(new Ellipse2D.Double(ox + mouse[0] * s - 5, oz + mouse[2] * s - 5, 10, 10));
        }
        g.setColor(MUTED);
        g.drawString(String.format(Locale.GERMAN, "Raster: %.0f m im Bild", SPAN), 14, h - 12);
    }

    static int cl(double v) { return (int) Math.max(0, Math.min(255, v * 255)); }
}
