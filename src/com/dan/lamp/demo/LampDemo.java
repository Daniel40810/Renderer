package com.dan.lamp.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.lamp.Lamp;
import com.dan.lamp.Spectra;
import com.dan.lamp.Windows;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Vorführung von {@code lamp} (Paketvertrag Punkt 3 und 10): eine Promenade bei Nacht aus {@link LampPicture} – Laternen,
 * Poller, Fensterlicht nach Uhrzeit, Spiegelung auf nassem Pflaster und Wasser; Regler für Uhrzeit und Nässe. Nur sie
 * darf FStyle nutzen. {@code LampDemo --bild datei.png} schreibt ein Standbild mit vier Feldern.
 */
public final class LampDemo {
    private LampDemo() { }

    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(120, 136, 150);

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            javax.imageio.ImageIO.write(bild(1280, 720), "png", new File(args[1]));
            System.out.println("Bild: " + args[1]);
            return;
        }
        SwingUtilities.invokeLater(LampDemo::open);
    }

    /** Drei Augenblicke der Promenade und die Lichtfarben mit den Tagesgängen der Fenster. */
    static BufferedImage bild(int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int pw = w / 2, ph = h / 2;
        double[][] cases = {{21, 1}, {21, 0}, {3, 1}};
        String[] title = {"21 Uhr nach dem Regen", "21 Uhr trocken", "3 Uhr nach dem Regen"};
        for (int k = 0; k < 3; k++) {
            LampPicture p = new LampPicture(pw, ph);
            p.hour = cases[k][0];
            p.wetness = cases[k][1];
            g.drawImage(p.render(), (k % 2) * pw, (k / 2) * ph, null);
            g.setColor(INK);
            g.drawString(String.format(Locale.GERMAN, "%s · %s", title[k], p.lamps.describe()), (k % 2) * pw + 10, (k / 2) * ph + 18);
        }
        legend(g, pw, ph, pw, ph);
        g.setColor(DIM);
        g.drawString("com.dan.lamp · Timmendorf Phase 11", 10, h - 10);
        g.dispose();
        return im;
    }

    static void legend(Graphics2D g, int x0, int y0, int pw, int ph) {
        g.setColor(BG);
        g.fillRect(x0, y0, pw, ph);
        g.setColor(INK);
        g.drawString("Lichtfarben aus dem Spektrum · Fenster brennend nach Uhrzeit", x0 + 10, y0 + 18);
        Lamp.Type[] t = Lamp.Type.values();
        for (int i = 0; i < t.length; i++) {
            int x = x0 + 14 + (i % 5) * 124, y = y0 + 34 + (i / 5) * 62;
            double[] c = t[i].rgb();
            double m = Math.max(c[0], Math.max(c[1], c[2]));
            g.setColor(new Color(srgb(c[0] / m), srgb(c[1] / m), srgb(c[2] / m)));
            g.fillOval(x, y, 26, 26);
            g.setColor(INK);
            g.drawString(t[i].name(), x + 32, y + 12);
            g.setColor(DIM);
            g.drawString(String.format(Locale.GERMAN, "%.0f K", Spectra.cct(Spectra.xyz(t[i]))), x + 32, y + 27);
        }
        // Tagesgänge
        int cx = x0 + 40, cy = y0 + ph - 30, cw = pw - 70, ch = 170;
        g.setColor(new Color(40, 52, 64));
        for (int k = 0; k <= 24; k += 6) {
            int x = cx + cw * k / 24;
            g.drawLine(x, cy, x, cy - ch);
            g.setColor(DIM);
            g.drawString(k + " h", x - 8, cy + 14);
            g.setColor(new Color(40, 52, 64));
        }
        g.drawLine(cx, cy, cx + cw, cy);
        Color[] cols = {new Color(240, 170, 90), new Color(120, 200, 230), new Color(230, 110, 140), new Color(150, 220, 120)};
        Windows.Use[] us = {Windows.Use.HOME, Windows.Use.HOLIDAY, Windows.Use.SHOP, Windows.Use.GASTRO};
        g.setStroke(new BasicStroke(1.6f));
        for (int u = 0; u < us.length; u++) {
            g.setColor(cols[u]);
            int px = -1, py = -1;
            for (int s = 0; s <= 240; s++) {
                double hr = s / 10.0;
                int x = cx + (int) (cw * hr / 24), y = cy - (int) (ch * Windows.share(us[u], hr));
                if (px >= 0) g.drawLine(px, py, x, y);
                px = x; py = y;
            }
            g.drawString(us[u].name(), cx + 8 + u * 90, cy - ch - 6);
        }
    }

    static int srgb(double v) {
        v = Math.max(0, Math.min(1, v));
        return (int) Math.round(255 * (v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055));
    }

    private static void open() {
        LampPicture pic = new LampPicture(800, 450);
        FFrame f = new FFrame("Leuchten · Demo");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        BufferedImage[] img = {pic.render()};
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.drawImage(img[0], 0, 0, getWidth(), getHeight(), null);
                g.setColor(INK);
                g.drawString(String.format(Locale.GERMAN, "%.1f Uhr · Nässe %.0f %% · %s", pic.hour, 100 * pic.wetness, pic.lamps.describe()), 12, 20);
            }
        };
        view.setPreferredSize(new Dimension(800, 450));
        FSlider hour = new FSlider(0, 240, 210), wet = new FSlider(0, 100, 100);
        JLabel lh = new JLabel("Uhrzeit"), lw = new JLabel("Nässe");
        lh.setForeground(INK);
        lw.setForeground(INK);
        Runnable redo = () -> { pic.hour = hour.getValue() / 10.0; pic.wetness = wet.getValue() / 100.0; img[0] = pic.render(); view.repaint(); };
        hour.addChangeListener(e -> redo.run());
        wet.addChangeListener(e -> redo.run());
        JPanel south = new JPanel(new GridLayout(2, 2, 10, 4));
        south.setBackground(BG);
        south.add(lh); south.add(hour); south.add(lw); south.add(wet);
        root.add(view, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(820, 600);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}
