package com.dan.wind.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.wind.WindField;
import com.dan.wind.WindSolution;

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
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Eigene kleine Vorführung (Paketvertrag Punkt 3 und 10): ein Testberg (Kuppe mit 600 m über einer Hochfläche, daneben
 * eine Kante und ein Tal). Links die Karte in 10 m über Grund (Farbe nach der Geschwindigkeit gegen den Wind der Lage,
 * Pfeile, Rauchfäden), rechts ein Schnitt von West nach Ost über die Kuppe mit Pfeilen und Rauchfäden, die mit den Böen
 * ziehen. Seit Brocken Phase 4 aus dem gelösten, massenkonsistenten Feld (Stufe LOW, gut eine Sekunde); Regler für
 * Beaufort, Schichtung und Tag/Nacht. Ohne Fenster schreibt {@code WindDemo --bild datei.png [bft] [schichtung 0..3]
 * [erwärmung −1..1]} ein Standbild.
 */
public final class WindDemo {
    private WindDemo() { }

    static final double X0 = -4000, X1 = 4000, Y1 = 1800;

    /** Der Testberg: Kuppe 600 m über 300 m, im Süden eine Kante (Stufe 120 m), im Norden ein Tal 100 m tief [G]. */
    static double gelaende(double x, double z) {
        double h = 300 + 600 * Math.exp(-Math.pow(Math.hypot(x, z) / 1600, 1.6));
        h += 120 / (1 + Math.exp(-(x - 500) / 80.0)) * Math.exp(-Math.pow((z - 3200) / 900, 2));   // Kante im Süden
        h -= 100 * Math.exp(-Math.pow((z + 3300) / 500, 2)) * Math.exp(-Math.pow(x / 3500, 2));     // Tal im Norden
        return h;
    }

    static WindField feld(int bft, int schicht, double heating) {
        WindField w = WindField.beaufort(bft);
        w.fromDeg = 270;
        w.stability = WindField.Stability.values()[Math.max(0, Math.min(3, schicht))];
        w.heating = heating;
        w.ground = WindDemo::gelaende;
        w.roughness = (x, z) -> w.ground.height(x, z) > 750 ? 0.05 : 0.8;   // oben Heide, unten Wald [G]
        w.quality = WindField.Quality.LOW;
        return w;
    }

    static WindSolution loesen(WindField w) { return w.solve(-6000, -5000, 6000, 5000); }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            WindField w = feld(args.length > 2 ? Integer.parseInt(args[2]) : 6, args.length > 3 ? Integer.parseInt(args[3]) : 1,
                    args.length > 4 ? Double.parseDouble(args[4]) : 0);
            loesen(w);
            javax.imageio.ImageIO.write(bild(w, 1500, 560, 0), "png", new File(args[1]));
            System.out.println(w.describe());
            System.out.println("Bild: " + args[1]);
            return;
        }
        SwingUtilities.invokeLater(WindDemo::open);
    }

    /** Karte links (quadratisch, Höhe ht), Schnitt rechts. */
    static BufferedImage bild(WindField w, int wd, int ht, double t) {
        BufferedImage im = new BufferedImage(wd, ht, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.drawImage(karte(w, ht, ht, t), 0, 0, null);
        g.drawImage(render(w, wd - ht, ht, t), ht, 0, null);
        g.dispose();
        return im;
    }

    /** Karte in 10 m über Grund: Farbe = Geschwindigkeit gegen das Profil der Lage (blau langsamer, gelb schneller), Pfeile, Rauchfäden. */
    static BufferedImage karte(WindField w, int wd, int ht, double t) {
        BufferedImage im = new BufferedImage(wd, ht, BufferedImage.TYPE_INT_RGB);
        double xa = -5500, xb = 5500, za = -4500, zb = 4500, sx = wd / (xb - xa), sz = ht / (zb - za);
        double ref = Math.max(0.3, w.profile(10, 0.8));
        for (int py = 0; py < ht; py += 2)
            for (int px = 0; px < wd; px += 2) {
                double x = xa + px / sx, z = za + py / sz, gr = w.ground.height(x, z);
                double[] v = w.windAt(x, gr + 10, z, t);
                double q = Math.hypot(v[0], v[2]) / ref;   // 1 = wie die Lage über Wald
                float k = (float) Math.max(0, Math.min(1, (q - 0.3) / 1.7));
                float shade = (float) (0.55 + 0.45 * Math.max(0, Math.min(1, (gr - 300) / 650)));
                int rgb = new Color(Math.min(1f, (0.15f + 0.85f * k) * shade), Math.min(1f, (0.30f + 0.55f * k) * shade), Math.min(1f, (0.55f - 0.45f * k) * shade)).getRGB();
                im.setRGB(px, py, rgb);
                if (px + 1 < wd) im.setRGB(px + 1, py, rgb);
                if (py + 1 < ht) { im.setRGB(px, py + 1, rgb); if (px + 1 < wd) im.setRGB(px + 1, py + 1, rgb); }
            }
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Höhenlinien alle 100 m (grob über die Bildpunkte)
        g.setColor(new Color(255, 255, 255, 40));
        for (int py = 0; py < ht; py += 3)
            for (int px = 0; px < wd; px += 3) {
                double a = w.ground.height(xa + px / sx, za + py / sz), b = w.ground.height(xa + (px + 3) / sx, za + py / sz), c = w.ground.height(xa + px / sx, za + (py + 3) / sz);
                if (Math.floor(a / 100) != Math.floor(b / 100) || Math.floor(a / 100) != Math.floor(c / 100)) g.fillRect(px, py, 2, 2);
            }
        g.setStroke(new BasicStroke(1.2f));
        for (double z = za + 400; z < zb; z += 600)
            for (double x = xa + 400; x < xb; x += 600) {
                double[] v = w.windAt(x, w.ground.height(x, z) + 10, z, t);
                double u = Math.hypot(v[0], v[2]), px = (x - xa) * sx, py = (z - za) * sz, l = 4 + u * 2.5;
                g.setColor(new Color(240, 240, 235, 220));
                g.drawLine((int) px, (int) py, (int) (px + v[0] / Math.max(1e-6, u) * l), (int) (py + v[2] / Math.max(1e-6, u) * l));
                g.fillOval((int) (px + v[0] / Math.max(1e-6, u) * l) - 2, (int) (py + v[2] / Math.max(1e-6, u) * l) - 2, 4, 4);
            }
        // Rauchfäden in 30 m über Grund vom Westrand
        g.setColor(new Color(242, 237, 227, 170));
        for (int q = 0; q < 14; q++) {
            double x = xa + 100, z = za + 300 + q * 600;
            Path2D p = new Path2D.Double();
            p.moveTo((x - xa) * sx, (z - za) * sz);
            for (int i = 0; i < 900; i++) {
                double[] v = w.windAt(x, w.ground.height(x, z) + 30, z, t);
                double u = Math.hypot(v[0], v[2]);
                if (u < 0.05) break;
                x += v[0] / u * 25;
                z += v[2] / u * 25;
                if (x > xb || z < za || z > zb || x < xa) break;
                p.lineTo((x - xa) * sx, (z - za) * sz);
            }
            g.draw(p);
        }
        g.setColor(new Color(232, 234, 233));
        g.drawString("Karte 10 m über Grund · blau langsamer, gelb schneller als die Lage · Pfeile 10 m, Fäden 30 m", 10, ht - 10);
        g.dispose();
        return im;
    }

    static BufferedImage render(WindField w, int wd, int ht, double t) {
        BufferedImage im = new BufferedImage(wd, ht, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(15, 19, 21));
        g.fillRect(0, 0, wd, ht);
        double sx = wd / (X1 - X0), sy = ht / Y1;
        // Pfeile: Länge nach der Geschwindigkeit, Farbe nach der Böe
        for (double x = X0 + 200; x < X1; x += 320)
            for (double h = 20; h < Y1; h *= 1.55) {
                double gr = w.ground.height(x, 0), y = gr + h;
                if (y > Y1) break;
                double[] v = w.windAt(x, y, 0, t);
                double u = Math.hypot(v[0], v[1]), gu = w.gustAt(x, y, 0, t);
                float k = (float) Math.min(1, gu / 40);
                g.setColor(new Color(0.62f + 0.38f * k, 0.75f - 0.3f * k, 0.81f - 0.45f * k));
                double px = (x - X0) * sx, py = ht - y * sy, l = 6 + u * 2.2;
                double ex = px + v[0] / Math.max(1e-6, u) * l, ey = py - v[1] / Math.max(1e-6, u) * l;
                g.setStroke(new BasicStroke(1.4f));
                g.drawLine((int) px, (int) py, (int) ex, (int) ey);
                g.fillOval((int) ex - 2, (int) ey - 2, 4, 4);
            }
        // Rauchfäden aus drei Quellen am Luvhang
        g.setStroke(new BasicStroke(1.2f));
        for (int q = 0; q < 3; q++) {
            double x = -2600 + q * 600, y = w.ground.height(x, 0) + 15;
            Path2D p = new Path2D.Double();
            p.moveTo((x - X0) * sx, ht - y * sy);
            for (int i = 0; i < 400; i++) {
                double[] v = w.windAt(x, y, 0, t);
                double gu = w.gustAt(x, y, 0, t + i * 0.5), u = Math.max(0.3, Math.hypot(v[0], v[1])), f = gu / u;
                x += v[0] * f * 2;
                y += v[1] * f * 2 + (w.turbulenceAt(x, y, 0) * (Math.sin(i * 0.7 + q) * 1.5));
                y = Math.max(w.ground.height(x, 0) + 2, y);
                if (x > X1) break;
                p.lineTo((x - X0) * sx, ht - y * sy);
            }
            g.setColor(new Color(242, 237, 227, 200));
            g.draw(p);
        }
        // Gelände
        Path2D gp = new Path2D.Double();
        gp.moveTo(0, ht);
        for (int i = 0; i <= wd; i += 3) gp.lineTo(i, ht - w.ground.height(X0 + i / sx, 0) * sy);
        gp.lineTo(wd, ht);
        gp.closePath();
        g.setColor(new Color(45, 74, 55));
        g.fill(gp);
        g.setColor(new Color(232, 234, 233));
        String d = w.describe();
        int cut = d.indexOf(" · gelöst: ");
        g.drawString(cut > 0 ? d.substring(0, cut) : d, 12, 20);
        if (cut > 0) g.drawString(d.substring(cut + 3), 12, 38);
        g.drawString("Schnitt West–Ost über die Kuppe", 12, ht - 10);
        g.dispose();
        return im;
    }

    private static void open() {
        WindField[] w = {feld(6, 1, 0)};
        loesen(w[0]);
        FFrame f = new FFrame("Wind-Package · Demo");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(new Color(15, 19, 21));
        long t0 = System.nanoTime();
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.drawImage(bild(w[0], Math.max(2, getWidth()), Math.max(1, getHeight()), (System.nanoTime() - t0) / 1e9), 0, 0, null);
            }
        };
        view.setPreferredSize(new Dimension(1300, 480));
        FSlider bft = new FSlider(0, 12, 6), sch = new FSlider(0, 3, 1), tag = new FSlider(-10, 10, 0);
        JLabel lbl = new JLabel("Beaufort 6 · Schichtung neutral · Erwärmung 0");
        lbl.setForeground(new Color(232, 234, 233));
        java.util.concurrent.ExecutorService rechner = java.util.concurrent.Executors.newSingleThreadExecutor(r -> { Thread th = new Thread(r, "Windfeld"); th.setDaemon(true); return th; });
        Runnable neu = () -> {
            if (bft.getValueIsAdjusting() || sch.getValueIsAdjusting() || tag.getValueIsAdjusting()) return;
            WindField n = feld(bft.getValue(), sch.getValue(), tag.getValue() / 10.0);
            lbl.setText("Beaufort " + bft.getValue() + " · Schichtung " + n.stability.name().toLowerCase(java.util.Locale.ROOT) + " · Erwärmung " + tag.getValue() / 10.0 + " · rechnet …");
            rechner.submit(() -> {
                loesen(n);
                SwingUtilities.invokeLater(() -> { w[0] = n; lbl.setText(lbl.getText().replace(" · rechnet …", "")); });
            });
        };
        bft.addChangeListener(e -> neu.run());
        sch.addChangeListener(e -> neu.run());
        tag.addChangeListener(e -> neu.run());
        JPanel south = new JPanel(new GridLayout(1, 4, 10, 0));
        south.setBackground(new Color(15, 19, 21));
        south.add(lbl);
        south.add(bft);
        south.add(sch);
        south.add(tag);
        root.add(view, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(1360, 600);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Timer(120, e -> view.repaint()).start();
    }
}
