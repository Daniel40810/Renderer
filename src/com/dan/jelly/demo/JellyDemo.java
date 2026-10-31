package com.dan.jelly.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.jelly.Bloom;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Vorführung ohne Grafikkarte: Blick von oben auf 3,2 × 2 m Wasser über Sand, wie von der Seebrücke, mit dem Muster des
 * Shaders auf der CPU nachgerechnet (Schirm, Gonaden, Mundarme, Saum, Abschwächung mit der Tiefe). Regler: Tag im Jahr.
 * Ohne Fenster schreibt {@code JellyDemo --bild datei.png [tag]} ein Standbild.
 */
public final class JellyDemo {
    private JellyDemo() { }

    static final double W = 3.2, H = 2.0;

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            Bloom b = Bloom.summer();
            b.dayOfYear = args.length > 2 ? Integer.parseInt(args[2]) : 235;
            b.density = 2.5;
            for (int i = 0; i < 200; i++) b.step(0.05, 0, 0);
            javax.imageio.ImageIO.write(render(b, 800, 500), "png", new File(args[1]));
            System.out.println("Bild: " + args[1] + " · " + b.describe());
            return;
        }
        SwingUtilities.invokeLater(JellyDemo::open);
    }

    /** Wasser von oben mit den Quallen im Ausschnitt W × H um den Ursprung. */
    static BufferedImage render(Bloom b, int w, int h) {
        double[] ext = {0.45, 0.12, 0.22};
        double bedDepth = 3.5;
        float[] r = new float[w * h], g = new float[w * h], bl = new float[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double wx = (x / (double) w - 0.5) * W, wz = (y / (double) h - 0.5) * H;
                double sand = 0.55 + 0.08 * Math.sin(wx * 9 + Math.sin(wz * 3) * 2) * Math.sin(wz * 7);
                int i = y * w + x;
                r[i] = (float) (sand * 0.92 * Math.exp(-ext[0] * 2 * bedDepth) + 0.02);
                g[i] = (float) (sand * 0.85 * Math.exp(-ext[1] * 2 * bedDepth) + 0.05);
                bl[i] = (float) (sand * 0.66 * Math.exp(-ext[2] * 2 * bedDepth) + 0.07);
            }
        for (Bloom.Jelly j : b.all()) {
            if (!b.visible(j)) continue;
            double rad = j.diameterNow() / 2, seen = b.seen(j.depth);
            int px = (int) ((j.x / W + 0.5) * w), py = (int) ((j.z / H + 0.5) * h), pr = (int) Math.ceil(rad * 1.12 / W * w);
            for (int y = Math.max(0, py - pr); y < Math.min(h, py + pr + 1); y++)
                for (int x = Math.max(0, px - pr); x < Math.min(w, px + pr + 1); x++) {
                    double ux = ((x / (double) w - 0.5) * W - j.x) / rad, uz = -((y / (double) h - 0.5) * H - j.z) / rad;
                    double c = Math.cos(-j.heading), s = Math.sin(-j.heading);
                    double u = ux * c - uz * s, v = ux * s + uz * c;
                    double[] p = pattern(u, v, j.contraction, j.hue);
                    if (p == null) continue;
                    double a = p[3] * seen;
                    int i = y * w + x;
                    double ar = Math.exp(-ext[0] * 2 * j.depth), ag = Math.exp(-ext[1] * 2 * j.depth), ab = Math.exp(-ext[2] * 2 * j.depth);
                    r[i] = (float) (r[i] * (1 - a) + p[0] * 0.8 * ar * a);
                    g[i] = (float) (g[i] * (1 - a) + p[1] * 0.8 * ag * a);
                    bl[i] = (float) (bl[i] * (1 - a) + p[2] * 0.8 * ab * a);
                }
        }
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < w * h; i++) im.setRGB(i % w, i / w, (srgb(r[i]) << 16) | (srgb(g[i]) << 8) | srgb(bl[i]));
        return im;
    }

    /** Das Muster des Shaders auf der CPU: Albedo RGB und Deckung, null außerhalb. */
    static double[] pattern(double u, double v, double contr, double hue) {
        double r = Math.hypot(u, v), ang = Math.atan2(v, u);
        double fringe = 0;
        if (r > 1) {
            double k = frac(ang * 96 / (2 * Math.PI));
            fringe = sm(0.35, 0, Math.abs(k - 0.5) - 0.12) * sm(1.1 - 0.05 * contr, 1, r) * 0.5;
            if (fringe < 0.01) return null;
        }
        double body = (0.16 + 0.24 * sm(0.55, 0.98, r) + 0.10 * contr * sm(0.6, 1, r)) * sm(1, 0.97, r);
        double canal = 0;
        if (r > 0.42 && r < 0.97) {
            double k = frac(ang * 8 / (2 * Math.PI) + 0.0625), k2 = frac(ang * 32 / (2 * Math.PI));
            canal = sm(0.03, 0, Math.abs(k - 0.5) - 0.005) * 0.10 + sm(0.08, 0, Math.abs(k2 - 0.5)) * 0.04 * sm(0.7, 0.95, r);
        }
        double gon = 0;
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 0.25 + i * Math.PI * 0.5, cx = 0.36 * Math.cos(a), cy = 0.36 * Math.sin(a);
            double dx = u - cx, dy = v - cy, rr = Math.hypot(dx, dy) + 1e-9;
            double ring = sm(0.055, 0, Math.abs(rr - 0.15) - 0.018);
            double open = sm(0.55, 0.9, (dx * -cx + dy * -cy) / (rr * 0.36));
            gon = Math.max(gon, ring * (1 - 0.85 * open));
        }
        double arms = 0;
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI * 0.5, ux = Math.cos(a), uy = Math.sin(a);
            double along = u * ux + v * uy, across = Math.abs(-u * uy + v * ux + 0.02 * Math.sin(along * 60));
            arms = Math.max(arms, sm(0.045, 0, across - 0.01) * sm(0.32, 0, along) * (along >= 0 ? 1 : 0));
        }
        double a = Math.max(body, fringe) + canal + 0.8 * gon + 0.3 * arms;
        double t = Math.max(0, Math.min(1, 0.8 * gon / Math.max(a, 1e-3)));
        double gr = 0.55 + (0.95 - 0.55) * hue, gg = 0.25 + (0.38 - 0.25) * hue, gb = 0.88 + (0.62 - 0.88) * hue;
        return new double[]{0.86 + (gr - 0.86) * t, 0.89 + (gg - 0.89) * t, 0.95 + (gb - 0.95) * t, Math.min(0.85, a)};
    }

    static double sm(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    static double frac(double v) { return v - Math.floor(v); }

    static int srgb(double v) {
        v = Math.max(0, Math.min(1, v));
        return (int) Math.round(255 * (v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055));
    }

    private static void open() {
        Bloom b = Bloom.summer();
        b.density = 2.5;
        FFrame f = new FFrame("Ohrenquallen · com.dan.jelly");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(new Color(11, 17, 24));
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.drawImage(render(b, Math.max(1, getWidth() / 2), Math.max(1, getHeight() / 2)), 0, 0, getWidth(), getHeight(), null);
            }
        };
        view.setPreferredSize(new Dimension(800, 500));
        FSlider tag = new FSlider(120, 330, 235);
        JLabel lbl = new JLabel();
        lbl.setForeground(new Color(230, 234, 238));
        Runnable text = () -> lbl.setText("Tag " + b.dayOfYear + " · " + b.describe());
        b.dayOfYear = 235;
        tag.addChangeListener(e -> b.dayOfYear = tag.getValue());
        JPanel south = new JPanel(new BorderLayout(10, 0));
        south.setBackground(new Color(11, 17, 24));
        south.add(lbl, BorderLayout.NORTH);
        south.add(tag, BorderLayout.CENTER);
        root.add(view, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(860, 620);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Timer(40, e -> {
            b.step(0.04, 0, 0);
            text.run();
            view.repaint();
        }).start();
    }
}
