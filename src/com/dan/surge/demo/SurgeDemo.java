package com.dan.surge.demo;

import com.dan.surge.FloodMap;
import com.dan.surge.Lip;
import com.dan.surge.Surge;
import com.dan.surge.Veil;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Vorführung von {@code com.dan.surge} ohne Grafikkarte, als Schaubild: links ein Fjord von oben, in dem eine Welle von
 * einem Bergsturz am Kopf des Fjords ausläuft (Isochronen der Ankunft, nach der Höhe gefärbt), dazu die Linie der Lippe
 * und die Tropfen zur gewählten Zeit; rechts oben der Querschnitt der Lippe beim Einrollen (früh, mitten, spät) und mit
 * Vorhang; rechts unten die Schleier am Auflauf, Bahnen mit Luftwiderstand über einem Hang.
 * <p>
 * {@code SurgeDemo --bild datei.png [Zeit s] [Breite]} schreibt ein Standbild (Vorgabe 60 s, 1600).
 */
public final class SurgeDemo {
    static final double C = 45, LEVEL = 0;
    final FloodMap map;
    final Surge surge = Surge.lituya1958();

    SurgeDemo() {
        // Fjord: 3 km breit, 14 km lang, nach Süden offen; die Welle läuft vom Kopf (Norden) aus, 60 m, nimmt ab
        map = FloodMap.of(1000, 1000, 16, -8000, 8000, (x, z) -> {
            double rand = 1500 + 300 * Math.sin(z / 900.0) + 200 * Math.sin(z / 260.0 + 1.3);
            if (Math.abs(x) > rand || z < -7500) return null;
            double r = Math.hypot(x * 0.8, z + 7500);
            double h = 60 * Math.exp(-r / 9000) + 10 * Math.max(0, (Math.abs(x) - rand + 250) / 250);
            return new double[]{r / C + 0.02 * Math.abs(x), LEVEL + h};
        });
        map.fillDry(2);
        map.blocks(32, LEVEL);
    }

    BufferedImage render(int w, double t) {
        int h = w * 9 / 16;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(16, 22, 30));
        g.fillRect(0, 0, w, h);
        double s = w / 1600.0;
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, (int) (15 * s)));
        // --- links: der Fjord von oben
        int L = (int) (20 * s), T = (int) (40 * s), W = (int) (820 * s), H = h - T - (int) (20 * s);
        double sc = Math.min(W / 16000.0, H / 16000.0);
        int cx = L + W / 2, cz = T + H / 2;
        for (int j = 0; j < map.nz; j += 2)
            for (int i = 0; i < map.nx; i += 2) {
                float a = map.arrival[j * map.nx + i], m = map.stage[j * map.nx + i];
                double x = map.x(i), z = map.z(j);
                int px = (int) (cx + x * sc), pz = (int) (cz + z * sc);
                Color col;
                if (Float.isNaN(m)) col = new Color(52, 50, 44);
                else {
                    double hh = Math.min(1, (m - LEVEL) / 60);
                    boolean iso = Math.abs(a / 10 - Math.round(a / 10)) < 0.06;
                    col = a <= t ? new Color((int) (30 + 70 * hh), (int) (95 + 80 * hh), (int) (120 + 70 * hh)) : new Color(24, 52, 70);
                    if (iso) col = col.brighter();
                }
                g.setColor(col);
                g.fillRect(px, pz, Math.max(1, (int) Math.ceil(32 * sc)), Math.max(1, (int) Math.ceil(32 * sc)));
            }
        Lip lip = surge.lip;
        lip.range = 20000;
        lip.dropRange = 20000;
        lip.build(map, t, 0, 0, LEVEL, (x, z) -> LEVEL - 150);
        g.setColor(new Color(255, 255, 255, 230));
        for (int v = 0; v < lip.vertices; v += 6) {
            int o = v * Lip.FL;
            g.fillRect((int) (cx + lip.mesh[o + 12] * sc), (int) (cz + lip.mesh[o + 13] * sc), 2, 2);
        }
        g.setColor(new Color(200, 235, 255, 120));
        for (int k = 0; k < lip.dropCount; k += 3) {
            int o = k * Lip.FT;
            g.fillRect((int) (cx + lip.drops[o] * sc), (int) (cz + lip.drops[o + 2] * sc), 1, 1);
        }
        g.setColor(Color.WHITE);
        g.drawString(String.format(Locale.GERMANY, "Fjord von oben, %.0f s: Isochronen alle 10 s, Lippe %d Stücke, %d Tropfen", t, lip.segments, lip.dropCount), L, (int) (26 * s));
        // --- rechts oben: der Querschnitt beim Einrollen
        int RL = (int) (870 * s), RT = (int) (60 * s), RW = (int) (700 * s), RH = (int) (380 * s);
        g.drawString("Querschnitt der Lippe: früh (hell), mitten, spät eingerollt; mit Vorhang (zu)", RL, (int) (40 * s));
        double Hh = 40, Lw = 0.35 * C + 0.4 * Hh;
        double ps = RH / 75.0;
        int ox = RL + (int) (RW * 0.45), oy = RT + RH - (int) (15 * ps);
        g.setColor(new Color(40, 90, 120));
        g.fillRect(RL, oy, RW, (int) (15 * ps));
        // der Körper der Welle hinter der Krone bis zum Pegel, die Wand unter der Lippe (die Rückwand der Röhre)
        Path2D body = new Path2D.Double();
        body.moveTo(ox - 0.9 * Hh * ps, oy);
        body.lineTo(ox - 0.6 * Hh * ps, oy - (0.93 * Hh - 1) * ps);
        body.lineTo(ox - 0.25 * Hh * ps, oy - 0.99 * Hh * ps);
        body.lineTo(ox, oy - 1.02 * Hh * ps);
        body.lineTo(ox + 0.35 * C * ps, oy);   // der Fuß der Front liegt 0,35 s vor der Krone
        body.closePath();
        g.setColor(new Color(30, 70, 80));
        g.fill(body);
        for (int q = 0; q < 3; q++) {
            double roll = q / 2.0;
            double[] p = {0, 0, Hh, 1, 0, Lw * (0.6 + 0.6 * roll), 1, 0.7, q == 2 ? 1 : 0, 0.74 + (0.60 - 0.74) * roll, C, 0, roll};
            float[][] sec = Lip.section(p, 0);
            Path2D path = new Path2D.Double();
            for (int k = 0; k < Lip.NQ; k++) {
                double xx = ox + sec[k][0] * ps, yy = oy - sec[k][1] * ps;
                if (k == 0) path.moveTo(xx, yy); else path.lineTo(xx, yy);
            }
            g.setStroke(new BasicStroke((float) (3 * s)));
            g.setColor(new Color(255, 255, 255, 255 - q * 70));
            g.draw(path);
            if (q == 2) {
                Path2D cur = new Path2D.Double();
                for (int k = Lip.NQ; k < Lip.NQ + Lip.NV; k++) {
                    double xx = ox + sec[k][0] * ps, yy = oy - sec[k][1] * ps;
                    if (k == Lip.NQ) cur.moveTo(xx, yy); else cur.lineTo(xx, yy);
                }
                g.setColor(new Color(170, 230, 210));
                g.draw(cur);
            }
        }
        // --- rechts unten: Schleier am Auflauf
        int VT = (int) (500 * s), VH = h - VT - (int) (30 * s);
        g.setColor(Color.WHITE);
        g.drawString("Schleier am Rand der Zunge: Fäden mit Luftwiderstand (k = 0,55/s), Hang 35°, Ausschnitt 160 × 80 m", RL, VT - (int) (10 * s));
        // Ausschnitt 160 m × 80 m um den Rand der Zunge bei x = 350 m
        double vs = VH / 80.0, x0v = 280, y0v = (280 - 80) * 0.7;
        int vx = RL + (int) (20 * s), vy = VT + VH;
        g.setClip(RL, VT, w - RL - (int) (20 * s), VH);
        Path2D hang = new Path2D.Double();
        hang.moveTo(vx, vy);
        for (int i = 280; i <= 440; i += 2) hang.lineTo(vx + (i - x0v) * vs, vy - ((Math.max(0, i - 80) * 0.7) - y0v) * vs);
        hang.lineTo(vx + 160 * vs, vy);
        hang.closePath();
        // die Zunge (Wasser bis zum Rand bei 350 m)
        Path2D zunge = new Path2D.Double();
        zunge.moveTo(vx, vy - 6 * vs);
        zunge.lineTo(vx + (350 - x0v) * vs, vy - ((350 - 80) * 0.7 - y0v) * vs);
        zunge.lineTo(vx, vy);
        zunge.closePath();
        g.setColor(new Color(70, 60, 48));
        g.fill(hang);
        g.setColor(new Color(215, 222, 220));
        g.fill(zunge);
        Veil veil = surge.veil;
        double[][] starts = {{344, 0}, {348, 0}, {352, 0}};
        java.util.Random r = new java.util.Random(524);
        g.setStroke(new BasicStroke((float) (1.2 * s)));
        for (double[] st : starts)
            for (int f = 0; f < 14; f++) {
                double v = (0.35 + 0.45 * r.nextDouble()) * 30, steil = 0.7 + 0.8 * r.nextDouble();
                double[] p0 = {st[0], (st[0] - 80) * 0.7 + 3, 0}, v0 = {v * 0.8, v * steil * 0.8 + 4, 0};
                Path2D b = new Path2D.Double();
                for (int k = 0; k <= 60; k++) {
                    double tau = k * 0.1;
                    double[] q = Veil.position(p0, v0, tau);
                    double xx = vx + (q[0] - x0v) * vs, yy = vy - (q[1] - y0v) * vs;
                    if (k == 0) b.moveTo(xx, yy); else b.lineTo(xx, yy);
                    if (q[1] < (Math.max(0, q[0] - 80) * 0.7)) break;
                }
                g.setColor(new Color(230, 236, 240, 140));
                g.draw(b);
            }
        g.setClip(null);
        g.dispose();
        return img;
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.GERMANY);
        SurgeDemo d = new SurgeDemo();
        if (args.length >= 2 && args[0].equals("--bild")) {
            double t = args.length >= 3 ? Double.parseDouble(args[2].replace(',', '.')) : 60;
            int w = args.length >= 4 ? Integer.parseInt(args[3]) : 1600;
            ImageIO.write(d.render(w, t), "png", new File(args[1]));
            System.out.println("SurgeDemo: " + args[1]);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("com.dan.surge – die Front aus der Flutkarte");
            JSlider sl = new JSlider(0, 400, 60);
            JPanel p = new JPanel() {
                { setPreferredSize(new java.awt.Dimension(1600, 900)); }
                @Override protected void paintComponent(Graphics g) { g.drawImage(d.render(getWidth(), sl.getValue()), 0, 0, null); }
            };
            sl.addChangeListener(e -> p.repaint());
            f.add(p, BorderLayout.CENTER);
            f.add(sl, BorderLayout.SOUTH);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.pack();
            f.setVisible(true);
        });
    }
}
